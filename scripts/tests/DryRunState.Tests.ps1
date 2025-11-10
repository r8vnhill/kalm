<#
Pester tests for DryRunState module (DryRunState.psm1)

Validates the Set-KalmDryRun / Get-KalmDryRun singleton behavior using
data-driven tests (Pester v5+).
#>

Describe 'DryRunState singleton' {
    BeforeAll {
        # Resolve repo root relative to this test file and dot-source the lib script.
        $repoRoot = Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')
        $libPath  = Join-Path $repoRoot 'scripts\lib'
        # Import the DryRun module (strict: fail if missing)
        $modulePath = Join-Path $libPath 'DryRunState.psm1'
        if (-not (Test-Path $modulePath)) { Throw "Missing module: $modulePath - import the module before running tests." }
        Import-Module $modulePath -Force

        # Load the unmanaged helper once so we can stress the backing field from multiple CLR threads.
        try { [Kalm.Tests.DryRunStateThreadHarness] | Out-Null }
        catch {
            $typeDefinition = @"
namespace Kalm.Tests
{
    using System;
    using System.Management.Automation;
    using System.Management.Automation.Runspaces;
    using System.Threading;
    using System.Threading.Tasks;

    public static unsafe class DryRunStateThreadHarness
    {
        public static int[] FireHose(IntPtr address, int[] values)
        {
            if (address == IntPtr.Zero) { throw new ArgumentNullException(nameof(address)); }
            if (values is null) { throw new ArgumentNullException(nameof(values)); }

            var snapshot = new int[values.Length];
            var location = (int*)address;
            Parallel.For(0, values.Length, i =>
            {
                snapshot[i] = Interlocked.Exchange(ref *location, values[i]);
            });
            return snapshot;
        }

        public static Task LaunchToggleStorm(IntPtr address, int workerCount, int togglesPerWorker, int spinWait)
        {
            if (address == IntPtr.Zero) { throw new ArgumentNullException(nameof(address)); }
            if (workerCount <= 0) { throw new ArgumentOutOfRangeException(nameof(workerCount)); }
            if (togglesPerWorker <= 0) { throw new ArgumentOutOfRangeException(nameof(togglesPerWorker)); }
            if (spinWait < 0) { spinWait = 0; }

            var location = (int*)address;
            var workers = new Task[workerCount];
            for (var i = 0; i < workerCount; i++)
            {
                var seed = i;
                workers[i] = Task.Run(() =>
                {
                    var value = seed & 1;
                    for (var j = 0; j < togglesPerWorker; j++)
                    {
                        value ^= 1;
                        Interlocked.Exchange(ref *location, value);
                        if (spinWait > 0)
                        {
                            Thread.SpinWait(spinWait);
                        }
                    }
                });
            }

            return Task.WhenAll(workers);
        }

    }
}
"@
            $assemblies = @(
                'System.Management.Automation'
                'System.Threading'
                'System.Threading.Thread'
                'System.Threading.Tasks'
                'System.Threading.Tasks.Parallel'
            )
            Add-Type -TypeDefinition $typeDefinition -Language CSharp -CompilerOptions '/unsafe' -ReferencedAssemblies $assemblies
        }
    }

    BeforeEach {
        # Ensure a clean baseline before each test vector
        Set-KalmDryRun $false
    }

    Context 'data-driven state transitions' {
        $cases = @(
            @{
                Name     = 'defaults-to-false'
                Steps    = @()                    # no calls
                Expected = $false
            }
            @{
                Name     = 'set-true'
                Steps    = @($true)
                Expected = $true
            }
            @{
                Name     = 'set-false'
                Steps    = @($false)
                Expected = $false
            }
            @{
                Name     = 'true-then-false'
                Steps    = @($true, $false)
                Expected = $false
            }
            @{
                Name     = 'false-then-true'
                Steps    = @($false, $true)
                Expected = $true
            }
            @{
                Name     = 'idempotent-true'
                Steps    = @($true, $true, $true)
                Expected = $true
            }
            @{
                Name     = 'idempotent-false'
                Steps    = @($false, $false, $false)
                Expected = $false
            }
            @{
                Name     = 'flip-multiple'
                Steps    = @($true, $false, $true, $false, $true)
                Expected = $true
            }
        )

        It 'applies <Name> → expected: <Expected>' -ForEach $cases {
            foreach ($step in $Steps) { Set-KalmDryRun $step }
            (Get-KalmDryRun) | Should -Be $Expected
        }
    }

    Context 'persists across independent calls' {
        It 'persists most-recent value' {
            Set-KalmDryRun $true
            Set-KalmDryRun $true
            (Get-KalmDryRun) | Should -BeTrue

            Set-KalmDryRun $false
            (Get-KalmDryRun) | Should -BeFalse
        }
    }

    Context 'thread safety' {
        It 'keeps the backing field binary during a concurrent write storm' {
            InModuleScope DryRunState {
                Reset-KalmDryRun
                $handle = [System.Runtime.InteropServices.GCHandle]::Alloc(
                    $script:_KalmDryRun,
                    [System.Runtime.InteropServices.GCHandleType]::Pinned
                )
                try {
                    $address = $handle.AddrOfPinnedObject()
                    $pattern = foreach ($i in 0..511) {
                        if (($i % 2) -eq 0) { 1 } else { 0 }
                    }
                    $history = [Kalm.Tests.DryRunStateThreadHarness]::FireHose($address, $pattern)
                }
                finally {
                    if ($handle.IsAllocated) { $handle.Free() }
                }

                $uniqueStates = $history | Sort-Object -Unique | ForEach-Object { [bool]$_ }
                $uniqueStates | Should -BeExactly @($false, $true)
                (Get-KalmDryRun) | Should -BeOfType [bool]
            }
        }

        It 'surfaces concurrent writer toggles to observers' {
            InModuleScope DryRunState {
                Reset-KalmDryRun
                $handle = [System.Runtime.InteropServices.GCHandle]::Alloc(
                    $script:_KalmDryRun,
                    [System.Runtime.InteropServices.GCHandleType]::Pinned
                )
                try {
                    $address = $handle.AddrOfPinnedObject()
                    $writerTask = [Kalm.Tests.DryRunStateThreadHarness]::LaunchToggleStorm(
                        $address,
                        4,
                        2000,
                        200
                    )
                    $observed = [System.Collections.Generic.List[bool]]::new()
                    while (-not $writerTask.IsCompleted) {
                        $observed.Add((Get-KalmDryRun))
                        Start-Sleep -Milliseconds 0
                    }
                    $writerTask.GetAwaiter().GetResult()
                    $observed.Add((Get-KalmDryRun))
                    ($observed.ToArray() | Sort-Object -Unique) | Should -BeExactly @($false, $true)
                }
                finally {
                    if ($handle.IsAllocated) { $handle.Free() }
                }
            }
        }

    }
}

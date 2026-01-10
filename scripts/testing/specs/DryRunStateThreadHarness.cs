using System;
using System.Management.Automation;
using System.Management.Automation.Runspaces;
using System.Threading;
using System.Threading.Tasks;

namespace Kalm.Automation.Tests
{
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

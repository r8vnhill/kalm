$ErrorActionPreference = "Stop"

java -version
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$psVersion = $PSVersionTable.PSVersion.ToString()
Write-Host ("PowerShell " + $psVersion)

git --version
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Import-Module Pester -ErrorAction Stop
$pesterVersion = (Get-Module Pester -ListAvailable | Select-Object -First 1).Version.ToString()
Write-Host ("Pester " + $pesterVersion)

Write-Host "Container smoke test OK."

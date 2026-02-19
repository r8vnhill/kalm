$ErrorActionPreference = "Stop"

java -version
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$psVersion = $PSVersionTable.PSVersion.ToString()
Write-Information ("PowerShell " + $psVersion) -InformationAction Continue

git --version
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Import-Module Pester -ErrorAction Stop
$pesterVersion = (Get-Module Pester -ListAvailable | Select-Object -First 1).Version.ToString()
Write-Information ("Pester " + $pesterVersion) -InformationAction Continue

Write-Information "Container smoke test OK." -InformationAction Continue

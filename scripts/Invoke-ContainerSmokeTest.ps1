$ErrorActionPreference = "Stop"

java -version
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$psVersion = $PSVersionTable.PSVersion.ToString()
Write-Information ("PowerShell " + $psVersion) -InformationAction Continue

git --version
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Information "Container smoke test OK." -InformationAction Continue

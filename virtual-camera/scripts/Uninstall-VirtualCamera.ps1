param(
    [ValidateSet('Debug', 'Release')]
    [string]$Configuration = 'Debug',

    [string]$Name = 'Static Image Camera',

    [string]$DeployRoot = "$env:ProgramData\StaticImageVirtualCamera"
)

$ErrorActionPreference = 'Stop'

$exePath = Join-Path $DeployRoot 'bin\VirtualCameraControl.exe'
$logPath = Join-Path $DeployRoot 'uninstall.log'
$runnerPath = Join-Path $DeployRoot 'run-uninstall.ps1'

if (-not (Test-Path $exePath)) {
    throw "Built control executable not found: $exePath"
}

$runner = @"
`$ErrorActionPreference = 'Stop'
Start-Transcript -Path '$logPath' -Force
& '$exePath' uninstall --name '$Name'
`$exitCode = `$LASTEXITCODE
Write-Host "VirtualCameraControlExitCode=`$exitCode"
Stop-Transcript
exit `$exitCode
"@
Set-Content -Path $runnerPath -Value $runner -Encoding UTF8

$process = Start-Process -FilePath 'powershell.exe' `
    -Verb RunAs `
    -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $runnerPath) `
    -Wait `
    -PassThru

if (Test-Path $logPath) {
    Get-Content $logPath -Tail 200
}

if ($process.ExitCode -ne 0) {
    throw "Virtual camera removal failed."
}

Remove-Item -Path $DeployRoot -Recurse -Force -ErrorAction SilentlyContinue

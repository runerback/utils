param(
    [ValidateSet('Debug', 'Release')]
    [string]$Configuration = 'Debug',

    [string]$Name = 'Static Image Camera',

    [string]$ImagePath,

    [string]$DeployRoot = "$env:ProgramData\StaticImageVirtualCamera"
)

$ErrorActionPreference = 'Stop'

function Get-WorkspaceRoot {
    $workspaceRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
    $solutionPath = Join-Path $workspaceRoot 'native\VirtualCameraNative.sln'

    if (-not (Test-Path $solutionPath)) {
        throw "Workspace layout not found under $workspaceRoot. Keep this script under a workspace that contains native\VirtualCameraNative.sln."
    }

    return $workspaceRoot
}

$workspaceRoot = Get-WorkspaceRoot
$outputDir = Join-Path $workspaceRoot "native\x64\$Configuration"
$exePath = Join-Path $outputDir 'VirtualCameraControl.exe'
$dllPath = Join-Path $outputDir 'VirtualCameraMediaSource.dll'
$deployBin = Join-Path $DeployRoot 'bin'
$deployAssets = Join-Path $DeployRoot 'assets'
$deployedExePath = Join-Path $deployBin 'VirtualCameraControl.exe'
$deployedDllPath = Join-Path $deployBin 'VirtualCameraMediaSource.dll'
$logPath = Join-Path $DeployRoot 'install.log'
$runnerPath = Join-Path $DeployRoot 'run-install.ps1'

if ([string]::IsNullOrWhiteSpace($ImagePath)) {
    $ImagePath = Join-Path $workspaceRoot 'assets\default-camera.png'
}
elseif (-not [System.IO.Path]::IsPathRooted($ImagePath)) {
    $ImagePath = Join-Path (Get-Location) $ImagePath
}

$ImagePath = [System.IO.Path]::GetFullPath($ImagePath)
$deployedImagePath = Join-Path $deployAssets ([System.IO.Path]::GetFileName($ImagePath))

if (-not (Test-Path $exePath) -or -not (Test-Path $dllPath)) {
    & (Join-Path $PSScriptRoot 'Build-Native.ps1') -Configuration $Configuration -Platform x64
}

if (-not (Test-Path $ImagePath)) {
    throw "Image file not found: $ImagePath"
}

New-Item -ItemType Directory -Path $deployBin -Force | Out-Null
New-Item -ItemType Directory -Path $deployAssets -Force | Out-Null

Copy-Item -Path (Join-Path $outputDir '*') -Destination $deployBin -Recurse -Force
Copy-Item -Path $ImagePath -Destination $deployedImagePath -Force

$runner = @"
`$ErrorActionPreference = 'Stop'
Start-Transcript -Path '$logPath' -Force
& '$deployedExePath' install --name '$Name' --image '$deployedImagePath' --dll '$deployedDllPath'
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
    throw "Virtual camera installation failed."
}

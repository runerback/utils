param(
    [ValidateSet('Debug', 'Release')]
    [string]$Configuration = 'Release',

    [ValidateSet('x64')]
    [string]$Platform = 'x64'
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
$solutionPath = Join-Path $workspaceRoot 'native\VirtualCameraNative.sln'
$packageRoot = Join-Path (Split-Path $solutionPath -Parent) 'packages'
$requiredPackages = @(
    'Microsoft.VCRTForwarders.140.1.0.7',
    'Microsoft.Windows.CppWinRT.2.0.220608.4',
    'Microsoft.Windows.ImplementationLibrary.1.0.220201.1'
)

$vswherePath = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
if (-not (Test-Path $vswherePath)) {
    throw "vswhere.exe was not found. Install Visual Studio with native desktop tooling."
}

$installationPath = & $vswherePath -latest -products * -requires Microsoft.Component.MSBuild -property installationPath
if (-not $installationPath) {
    throw "Visual Studio with MSBuild was not found."
}

$msbuildPath = Join-Path $installationPath 'MSBuild\Current\Bin\MSBuild.exe'
$vcToolsPath = Join-Path $installationPath 'VC\Tools\MSVC'
$sdkIncludeRoot = 'C:\Program Files (x86)\Windows Kits\10\Include'

if (-not (Test-Path $msbuildPath)) {
    throw "MSBuild.exe was not found under $installationPath."
}

if (-not (Test-Path $vcToolsPath)) {
    throw "The Visual C++ toolchain is missing. Install the 'Desktop development with C++' workload before building."
}

if (-not (Test-Path $sdkIncludeRoot)) {
    throw "The Windows 10/11 SDK is missing. Install a Windows SDK 10.0.22000.0 or newer before building."
}

if (-not ($requiredPackages | ForEach-Object { Test-Path (Join-Path $packageRoot $_) } | Where-Object { $_ -eq $false } | Select-Object -First 1)) {
    Write-Host "NuGet packages already present. Skipping restore."
}
else {
    & $msbuildPath $solutionPath /t:Restore /p:RestorePackagesConfig=true
    if ($LASTEXITCODE -ne 0) {
        throw "Package restore failed."
    }
}

& $msbuildPath $solutionPath /m /p:Configuration=$Configuration /p:Platform=$Platform
if ($LASTEXITCODE -ne 0) {
    throw "Build failed."
}

<#
.SYNOPSIS
Builds the ScreenRecorder APK with the Gradle wrapper.

.DESCRIPTION
Uses the Android Studio installation at D:\android\studio for Java and builds
either the debug APK or the unsigned release APK for the app module.

The Android SDK must already be configured through one of these mechanisms:
- ANDROID_SDK_ROOT
- ANDROID_HOME
- local.properties with sdk.dir=...

.PARAMETER Configuration
Selects which APK to build. Allowed values are debug and release.

.PARAMETER Clean
Runs the Gradle clean task before assembling the APK.

.EXAMPLE
.\build-apk.ps1

.EXAMPLE
.\build-apk.ps1 -Configuration release

.EXAMPLE
.\build-apk.ps1 -Configuration debug -Clean
#>
[CmdletBinding()]
param(
    [Parameter()]
    [ValidateSet("debug", "release")]
    [string]$Configuration = "debug",

    [Parameter()]
    [switch]$Clean
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Resolve-JavaHome {
    param(
        [Parameter(Mandatory = $true)]
        [string]$StudioRoot
    )

    $candidates = @(
        (Join-Path $StudioRoot "jbr"),
        (Join-Path $StudioRoot "jbr\Contents\Home")
    )

    foreach ($candidate in $candidates) {
        $javaExe = Join-Path $candidate "bin\java.exe"
        if (Test-Path $javaExe) {
            return $candidate
        }
    }

    return $null
}

function Resolve-AndroidSdkPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ProjectRoot
    )

    if ($env:ANDROID_SDK_ROOT) {
        return $env:ANDROID_SDK_ROOT
    }

    if ($env:ANDROID_HOME) {
        return $env:ANDROID_HOME
    }

    $localPropertiesPath = Join-Path $ProjectRoot "local.properties"
    if (-not (Test-Path $localPropertiesPath)) {
        return $null
    }

    $sdkLine = Get-Content -Path $localPropertiesPath | Where-Object { $_ -match '^\s*sdk\.dir\s*=' } | Select-Object -First 1
    if (-not $sdkLine) {
        return $null
    }

    $sdkValue = $sdkLine -replace '^\s*sdk\.dir\s*=\s*', ""
    $sdkPath = $sdkValue.Trim()
    $sdkPath = $sdkPath.Replace("\\", "\")
    $sdkPath = $sdkPath.Replace("\:", ":")
    $sdkPath = $sdkPath.Replace("\ ", " ")
    return $sdkPath
}

$projectRoot = $PSScriptRoot
$gradleWrapper = Join-Path $projectRoot "gradlew.bat"
$studioRoot = "D:\android\studio"
$javaHome = Resolve-JavaHome -StudioRoot $studioRoot

if (-not (Test-Path $gradleWrapper)) {
    throw "Gradle wrapper not found at '$gradleWrapper'. Run this script from the repository root."
}

if (-not (Test-Path $studioRoot)) {
    throw "Android Studio path '$studioRoot' was not found."
}

if (-not $javaHome) {
    throw "No Java runtime was found under '$studioRoot\jbr'. Expected '$studioRoot\jbr\bin\java.exe'."
}

$sdkRoot = Resolve-AndroidSdkPath -ProjectRoot $projectRoot
if (-not $sdkRoot) {
    throw "Android SDK is not configured. Set ANDROID_SDK_ROOT or ANDROID_HOME, or create local.properties with sdk.dir=..."
}

if (-not (Test-Path $sdkRoot)) {
    throw "Android SDK path '$sdkRoot' does not exist."
}

$env:JAVA_HOME = $javaHome
$env:ANDROID_SDK_ROOT = $sdkRoot
if (-not $env:ANDROID_HOME) {
    $env:ANDROID_HOME = $sdkRoot
}

$javaBin = Join-Path $javaHome "bin"
if (-not (($env:Path -split ";") -contains $javaBin)) {
    $env:Path = "$javaBin;$env:Path"
}

$gradleTask = switch ($Configuration) {
    "debug" { ":app:assembleDebug" }
    "release" { ":app:assembleRelease" }
}

$outputDirectory = switch ($Configuration) {
    "debug" { Join-Path $projectRoot "app\build\outputs\apk\debug" }
    "release" { Join-Path $projectRoot "app\build\outputs\apk\release" }
}

$expectedApkPath = switch ($Configuration) {
    "debug" { Join-Path $outputDirectory "app-debug.apk" }
    "release" { Join-Path $outputDirectory "app-release-unsigned.apk" }
}

$gradleArgs = @("--console=plain")
if ($Clean) {
    $gradleArgs += "clean"
}
$gradleArgs += $gradleTask

Write-Host "Using JAVA_HOME: $javaHome"
Write-Host "Using Android SDK: $sdkRoot"
Write-Host "Running Gradle task: $gradleTask"

Set-Location $projectRoot
& $gradleWrapper @gradleArgs
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

$apkPath = $expectedApkPath
if (-not (Test-Path $apkPath)) {
    $latestApk = Get-ChildItem -Path $outputDirectory -Filter "*.apk" -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if ($latestApk) {
        $apkPath = $latestApk.FullName
    }
    else {
        throw "Build completed, but no APK was found in '$outputDirectory'."
    }
}

Write-Host ""
Write-Host "APK created: $apkPath"
Write-Host "Configuration: $Configuration"

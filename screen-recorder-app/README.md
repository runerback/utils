# Screen Recorder App

An Android screen recorder built with Kotlin and Jetpack Compose. It records the device display through `MediaProjection`, can optionally capture system audio, and uses a floating toolbox so recording can be started and stopped while other apps are in the foreground.

## Features

- Floating recording toolbox with **Start**, **Stop**, and **Exit**
- Toolbox auto-hides during active capture so it does not appear in the saved video
- Live elapsed recording time shown in the toolbox
- Screen capture foreground service
- Optional system audio capture
- Resolution and frame-rate presets
- Saved recordings list with thumbnail preview
- Open, share, and delete actions for saved videos

## Requirements

- Android Studio / Android SDK
- Java 17 compatible runtime
- Android 14+ device or emulator (`minSdk = 34`)

## Local build setup

This repository includes a PowerShell build helper for the current Windows setup.

### Toolchain paths used on this machine

- Android Studio: `D:\android\studio`
- Android SDK: `D:\android\sdk`

`build-apk.ps1` uses the Java runtime from:

- `D:\android\studio\jbr`

Before building, configure the Android SDK with one of these options:

1. Set `ANDROID_SDK_ROOT=D:\android\sdk`
2. Set `ANDROID_HOME=D:\android\sdk`
3. Create `local.properties` in the repo root:

```properties
sdk.dir=D:\\android\\sdk
```

## Build

Build the debug APK:

```powershell
.\build-apk.ps1 -Configuration debug
```

Build the release APK:

```powershell
.\build-apk.ps1 -Configuration release
```

Clean and rebuild:

```powershell
.\build-apk.ps1 -Configuration debug -Clean
```

The generated APKs are written under:

- `app\build\outputs\apk\debug\`
- `app\build\outputs\apk\release\`

## App permissions

The app requests or relies on these Android permissions:

- `SYSTEM_ALERT_WINDOW` for the floating toolbox
- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PROJECTION` for active capture
- `POST_NOTIFICATIONS` for the recording notification
- `RECORD_AUDIO` for system audio capture support

## Usage

1. Install and launch the app.
2. Enable overlay permission when prompted.
3. Tap **Enter Recording**.
4. Grant screen capture permission.
5. Use the floating toolbox to start recording.
6. Stop the recording from the ongoing recording notification.
7. Review saved videos from the in-app recordings list.

## Project structure

- `app\src\main\java\com\runerback\screenrecorder\MainActivity.kt` - app entry point and permission flow
- `app\src\main\java\com\runerback\screenrecorder\ui\` - Compose UI and view model
- `app\src\main\java\com\runerback\screenrecorder\service\` - floating toolbox and recording services
- `app\src\main\java\com\runerback\screenrecorder\recorder\` - media encoding and muxing pipeline
- `app\src\main\java\com\runerback\screenrecorder\data\` - settings, state, and MediaStore access
- `build-apk.ps1` - Windows build helper

## Notes

- The app saves recordings into `Movies/Screen Recorder` through `MediaStore`.
- The floating toolbox timer uses `mm:ss` under one hour and `hh:mm:ss` at one hour or above.

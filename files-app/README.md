# Files App (Android)

Android file manager built with **Kotlin + Jetpack Compose**.  
It supports browsing local storage, connecting to SMB shares, basic file operations, and optional LAN file sharing for selected files.

## Features

1. Browse local files and folders (Storage Access Framework and direct file-path access when available).
2. Connect to SMB/CIFS shares (SMB 2.0.2–3.1.1 via `jcifs`).
3. Create folders and text files.
4. Delete files/folders.
5. Multi-tab sources (local + multiple SMB tabs) with persisted tab config.
6. Optional LAN sharing: start a temporary local HTTP server and share selected files by URL.
7. Built-in runtime log viewer and crash report export.

## Tech Stack

- **Language:** Kotlin (JVM target 17)
- **UI:** Jetpack Compose (Material 3)
- **DI:** Hilt
- **Networking:** Ktor client/server + OkHttp
- **Persistence:** DataStore Preferences + Kotlin Serialization
- **Build:** Gradle Kotlin DSL, Android Gradle Plugin 8.7.3

## Project Structure

```text
app/src/main/java/com/runerback/files/
  data/
    datasource/      # Settings and file utility data sources
    model/           # File nodes, metadata, tab/source models
    repository/      # Local + SMB repository implementations
    settings/        # App-level DataStore-backed settings
  di/                # Hilt module providers
  share/             # LAN sharing manager/service/server
  ui/                # Compose screens, tab UIs, dialogs, components
  FilesApplication.kt
  MainActivity.kt
```

## Requirements

- Android Studio (recent stable)
- Android SDK (configured in `local.properties`)
- JDK 17
- Min SDK: 26
- Target/Compile SDK: 35

## Getting Started

1. Clone the repository and open `files-app` in Android Studio.
2. Ensure `local.properties` points to a valid Android SDK path.
3. Sync Gradle.
4. Run the `app` configuration on a device/emulator.

## Build

### PowerShell helper (Windows)

```powershell
.\build.ps1
```

Default task is `assembleDebug`.

Run a specific task:

```powershell
.\build.ps1 -Task assembleRelease
```

### Gradle wrapper

```powershell
.\gradlew.bat assembleDebug
```

## Signing Configuration (Release)

The app expects release signing properties in `local.properties`:

- `RELEASE_STORE_FILE`
- `RELEASE_KEY_ALIAS`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_PASSWORD`

Keep signing keys and passwords local/private, and do not commit real credentials.

## Notes

- LAN sharing is served over local network HTTP and is intended for trusted networks.
- App settings include SMB timeout and LAN sharing enable/disable toggles.

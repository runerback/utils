# ComfyUI API App

An Android client for [ComfyUI](https://github.com/comfyanonymous/ComfyUI) workflows, plus a companion ComfyUI custom-node extension for building and running API test suites.

The app lets you load a workflow JSON, edit its exposed parameters, upload images, queue prompts, and monitor execution over HTTP/WebSocket. It also includes a built-in schema generator for marking which workflow fields should be editable in the mobile UI.

---

## Repository Layout

```
.
├── app/                          # Android application (Kotlin + Jetpack Compose)
│   ├── build.gradle.kts
│   └── src/main/java/com/runerback/comfyuiapi/
│       ├── MainActivity.kt
│       ├── ComfyUIApplication.kt
│       ├── di/AppModule.kt       # Ktor HttpClient, JSON, Hilt providers
│       ├── data/                 # Data sources, repository, models
│       ├── domain/               # Business logic (schema parsing, patching, builder)
│       └── ui/                   # Compose screens, components, ViewModels
├── custom_nodes/
│   └── ComfyUI-ApiTestSuits/     # ComfyUI custom-node extension (Python)
│       ├── __init__.py           # Image-processing test nodes
│       └── tests/
├── examples/                     # Standalone ComfyUI API examples
│   ├── basic_api_example.py
│   ├── websockets_api_example.py
│   ├── websocket-messages.ts
│   └── upload_image_api.md
├── workflows/
│   └── apitest.schema.json       # Example schema for editable workflow fields
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── build.ps1                     # PowerShell build helper
```

---

## Android App

### Tech Stack

- **Language:** Kotlin 2.1.0
- **UI:** Jetpack Compose (BOM 2024.12.01), Material 3
- **Architecture:** MVVM + Repository pattern
- **DI:** Dagger Hilt 2.55
- **Networking:** Ktor 2.3.13 (OkHttp engine, JSON, WebSockets, logging)
- **Persistence:** DataStore Preferences
- **Serialization:** Kotlinx Serialization JSON 1.7.3

### Features

- Load a ComfyUI workflow JSON from device storage.
- Automatically expose editable parameters based on a sidecar `.schema.json` file.
- Edit text prompts, seeds, integers, options/combos, and upload local images.
- Queue prompts to a configurable ComfyUI server.
- Stream execution progress and latent previews via WebSocket.
- View generated images in an in-app gallery.
- Built-in schema generator screen for creating `.schema.json` files without hand-editing JSON.
- Capture fatal exceptions to an in-memory log buffer for easier field debugging.

### Build Requirements

- Android Studio Ladybug or newer
- JDK 17
- Android SDK 35 (compileSdk), minimum SDK 26

### Build

```bash
# Debug APK
./gradlew assembleDebug

# Release APK (requires signing config in local.properties)
./gradlew assembleRelease
```

On Windows you can also use the provided helper:

```powershell
.\build.ps1 -Task assembleDebug
```

### Signing Configuration

Create `local.properties` in the project root with your release keystore:

```properties
RELEASE_STORE_FILE=/path/to/keystore.jks
RELEASE_KEY_ALIAS=your_alias
RELEASE_STORE_PASSWORD=your_store_password
RELEASE_KEY_PASSWORD=your_key_password
```

### Runtime Permissions

The app requests internet access and uses `android:usesCleartextTraffic="true"` so it can talk to local ComfyUI instances over plain HTTP.

---

## ComfyUI Custom Nodes: ApiTestSuits

A small ComfyUI extension that provides deterministic image-processing nodes useful for testing API pipelines. All nodes support an optional `interval` input to simulate processing latency.

### Nodes

| Node | Description |
|------|-------------|
| `ATS-Resize` | Resize by percent or absolute width/height. |
| `ATS-GrayScale` | Convert RGB(A) images to grayscale. |
| `ATS-Rotate` | Rotate clockwise or anti-clockwise by an arbitrary degree. |
| `ATS-Crop` | Crop to a rectangular region. |
| `ATS-Invert` | Invert image colors. |

### Installation

Copy or symlink the `custom_nodes/ComfyUI-ApiTestSuits` folder into your ComfyUI `custom_nodes/` directory and restart ComfyUI.

### Example Workflow

See `custom_nodes/ComfyUI-ApiTestSuits/tests/workflow.json` and `tests/test_workflow.py` for a complete pipeline:

```
Load Image → ATS-Resize → ATS-GrayScale → ATS-Rotate → ATS-Crop → ATS-Invert → Save Image
```

---

## API Examples

The `examples/` folder contains standalone scripts for learning the ComfyUI REST and WebSocket APIs:

- `basic_api_example.py` — queue a simple prompt over HTTP.
- `websockets_api_example.py` — queue a prompt and wait for completion via WebSocket.
- `upload_image_api.md` — how to upload an input image before referencing it in a workflow.
- `websocket-messages.ts` — TypeScript types for ComfyUI WebSocket messages.

---

## Schema Files

The Android app uses an optional `.schema.json` sidecar to decide which workflow fields are editable. See `workflows/apitest.schema.json` for the supported format.

You can also generate schemas inside the app from the **Schema Generator** screen.

---

## Development Notes

- The app output APK is renamed to `comfyui-api-debug.apk` / `comfyui-api-release.apk` via `applicationVariants` configuration.
- Uncaught exceptions are logged to `LogBuffer` before the default crash handler takes over; view the log from the main screen.
- Logging is disabled by default in the Ktor client (`LogLevel.NONE`); change it in `di/AppModule.kt` when debugging.

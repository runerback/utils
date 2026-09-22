# Queue Helper

Queue Helper is an Android app for preparing generation queues from reusable presets. It manages task queues, edits per-task prompts and media, and packages the result as a `queue.zip` file in Downloads.

## Current task support

MiniMax H3 Ref2VA is the only supported task type for now. The app may support additional Wan2GP-based task types in the future as their queue formats and media requirements are added.

## Features

- Create, edit, duplicate, delete, import, and export presets.
- Manage preset-scoped task queues and a global task queue.
- Batch-create tasks from one or more presets.
- Edit task prompts with subject, picture, and audio tokens.
- Attach up to six images to each task.
- Attach one audio file to each task and configure its trim range.
- Use checkbox mode in preset or global task lists to apply media to multiple tasks:
  - **Add Image** appends one or more selected images to every checked task.
  - **Add Audio** replaces or adds one selected audio file on every checked task.
- Sync task prompts and settings from their presets while preserving media selections.
- Pack one task or an entire queue into `queue.zip`.
- Store presets, tasks, and imported media locally on the device.

## Tech stack

- Kotlin
- Jetpack Compose and Material 3
- Navigation Compose
- Android ViewModel
- Preferences DataStore and file-backed JSON persistence
- Kotlinx Serialization
- Coil for image thumbnails
- Media3 ExoPlayer for audio preview
- Robolectric and JUnit for unit tests

## Project layout

```text
app/src/main/java/com/runerback/queuehelper/
├── data/
│   ├── local/       # Preset, task, and media repositories
│   ├── model/       # Prompt, preset, task, token, and media models
│   └── template/    # Template loading and model-specific limits
├── domain/          # Packing, audio trimming, and batch media use cases
└── ui/
    ├── common/      # Shared Compose controls
    ├── components/  # Loading and logging components
    ├── edit/        # Preset editor
    ├── icons/       # Custom image-vector icons
    ├── navigation/  # Type-safe navigation routes
    ├── pack/        # Task lists and task editor
    └── presets/     # Preset list and preset batch actions
```

Key implementation files:

- `ui/pack/PackScreen.kt` — shared preset/global task list and single-task editor UI.
- `ui/pack/PackViewModel.kt` — task-list state, selection mode, and batch operations.
- `domain/BatchTaskMediaUseCase.kt` — multi-task image append and audio replacement.
- `domain/PackAllUseCase.kt` — queue-wide `queue.zip` generation.
- `data/local/TaskRepository.kt` — task persistence.
- `data/local/MediaRepository.kt` — imported media storage and deduplication.

## Requirements

- JDK 17
- Android SDK Platform 35
- Android Studio or another environment capable of running the Gradle wrapper

The project uses Gradle 8.9, Android Gradle Plugin 8.7.3, and Kotlin 2.1.0.

## Build

From the project root on Windows:

```powershell
.\gradlew.bat assembleDebug
```

The debug APK is written under:

```text
app/build/outputs/apk/debug/
```

A helper script is also available:

```powershell
.\build.ps1
```

It stops older local build processes, runs `assembleDebug`, and prints the generated APK path.

## Test

Run the unit tests with:

```powershell
.\gradlew.bat testDebugUnitTest
```

Run tests and build the debug APK together with:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

## Release builds

Release signing reads these properties from `local.properties`:

```properties
RELEASE_STORE_FILE=...
RELEASE_KEY_ALIAS=...
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_PASSWORD=...
```

`local.properties` is machine-specific and should not be committed.

## Data and output behavior

- Picked images and audio files are copied into app-private storage, so queue tasks do not depend on temporary picker URI grants.
- Imported media is deduplicated by the SHA-256 hash of its source URI.
- Each task can reference up to six images and one audio file.
- Queue packaging writes image files once even when multiple tasks reference them.
- Audio is trimmed to WAV during packaging according to each task's trim settings.
- On Android 10 and newer, `queue.zip` is written through MediaStore Downloads; on Android 9 and older, the app requests legacy write-storage permission before saving.

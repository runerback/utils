# Who Is Calling (Android)

Android 14+ app that identifies incoming calls. When a call comes in it reads the caller's number
(via the **call screening role** — no `READ_CALL_LOG` needed), queries a configurable HTTP API,
and shows the result as a heads-up notification while the phone rings.

## How it works

1. `CallerIdScreeningService` (a `CallScreeningService`) is invoked by the system before the phone
   rings and receives the caller's number.
2. The number is substituted into a user-configured URL template (`{number}` placeholder) and
   fetched with `GET`, under a configurable timeout (0.5–4 s, default 3 s — screening apps must
   respond to the system within ~5 s).
3. The API returns JSON like:
   ```json
   { "isSpam": false, "sourceArea": "Shanghai", "sourceName": "Delivery" }
   ```
4. A high-priority heads-up notification shows the caller name, number, and area. Spam results are
   flagged `⚠ SPAM`; if the "Block spam calls" toggle is on, spam calls are automatically rejected.

## Features

- Call screening role onboarding with status banner.
- Master enable switch and recent-lookups list on the home screen.
- Configurable API URL template, timeout, spam blocking, and failure notifications.
- Built-in "Test lookup" to verify the API configuration.

## Tech Stack

- **Language:** Kotlin (JVM target 17)
- **UI:** Jetpack Compose (Material 3)
- **DI:** Hilt
- **Networking:** Ktor client (OkHttp) + kotlinx.serialization
- **Persistence:** DataStore Preferences
- **Build:** Gradle Kotlin DSL, Android Gradle Plugin 8.7.3

## Requirements

- Android Studio (recent stable)
- Android SDK (configured in `local.properties`)
- JDK 17
- Min SDK: 34 (Android 14)
- Target/Compile SDK: 35

## Build

```powershell
.\build.ps1
```

Default task is `assembleDebug`. Run a specific task with `.\build.ps1 -Task assembleRelease`.

Release signing properties are read from `local.properties` (`RELEASE_STORE_FILE`,
`RELEASE_KEY_ALIAS`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_PASSWORD`).

## Testing on an emulator

1. Grant the **call screening role** from the home screen banner and allow notifications.
2. Set the API URL to a mock server, e.g. `http://10.0.2.2:8080/lookup?num={number}`
   (`10.0.2.2` reaches the host machine from the emulator). Cleartext HTTP is allowed by the app.
3. Simulate an incoming call from Android Studio's emulator extended controls (Phone → Call device)
   and watch for the heads-up notification.

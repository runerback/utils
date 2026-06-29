# Real Keyboard Modifier State Feedback Plan

## Context

The user wants the Android keyboard screen to reflect the real (physical) keyboard's modifier state. When they press **Caps Lock** or **Shift** on the real keyboard connected to the Windows PC, the phone UI should update accordingly:
- Caps Lock indicator, letter case, and real keyboard LED stay in sync.
- Shift indicator and shifted symbols reflect the real Shift key state.

Currently the system is one-way: Android sends key events to Windows, and Windows injects them via Interception. There is no path for state to flow back from Windows to Android.

## Approach

Add bidirectional communication:
1. The Windows server polls the real keyboard/system modifier state.
2. When Caps Lock or Shift state changes, the server sends a state update to the Android client.
3. The Android client receives the state and drives the UI.

This keeps the real keyboard input path unblocked (no Interception filter on real keyboards) and avoids intercepting physical keystrokes.

### 1. Windows: monitor modifier state

**File:** `keyboard-windows/src/InterceptionInput.cpp` / `InterceptionInput.h`

Add a background thread that polls Windows API state every ~50 ms:

```cpp
bool caps = (GetKeyState(VK_CAPITAL) & 0x0001) != 0;
bool shift = (GetAsyncKeyState(VK_SHIFT) & 0x8000) != 0;
```

- `GetKeyState(VK_CAPITAL)` returns the toggle/LED state, so it reflects both real and injected Caps Lock changes.
- `GetAsyncKeyState(VK_SHIFT)` returns whether any Shift key is currently held, so it reflects both real and injected Shift changes.

Add a callback setter:

```cpp
using ModifierStateCallback = std::function<void(bool caps, bool shift)>;
void setModifierStateCallback(ModifierStateCallback callback);
```

The thread compares current state to the previous state and invokes the callback only on change. The thread starts in `initialize()` and stops in `shutdown()`.

### 2. Windows: make the server send state to the client

**File:** `keyboard-windows/src/KeyboardServer.cpp` / `KeyboardServer.h`

The server currently accepts one client and blocks in `handleClient()` reading. To send outbound messages while the read loop runs, refactor it:

- Store the active client socket under a mutex.
- Run `handleClient()` in a detached thread so `run()` can continue accepting or the main thread can send.
- Add a public `send(const std::string& line)` method that sends a line to the active client (with `\n` terminator), guarded by the same mutex.
- On disconnect, clear the active socket.

New header outline:

```cpp
class KeyboardServer {
public:
    using KeyHandler = std::function<void(int vk, bool down)>;

    explicit KeyboardServer(int port, KeyHandler handler);
    ~KeyboardServer();

    bool start();
    void run();
    void stop();
    bool send(const std::string& line);

private:
    void handleClient(SOCKET clientSocket);

    int port_;
    KeyHandler handler_;
    std::atomic<bool> running_{false};
    SOCKET listenSocket_ = INVALID_SOCKET;
    std::mutex clientMutex_;
    SOCKET activeClient_ = INVALID_SOCKET;
};
```

### 3. Windows: serialize and send state updates

**File:** `keyboard-windows/src/JsonProtocol.cpp` / `JsonProtocol.h`

Add an outgoing state message builder:

```cpp
std::string buildStateMessage(bool caps, bool shift);
```

Returns:

```json
{"type":"state","caps":true,"shift":false}
```

In `main.cpp`, wire the InterceptionInput modifier-state callback to the server:

```cpp
input.setModifierStateCallback([&server](bool caps, bool shift) {
    server.send(buildStateMessage(caps, shift));
});
```

### 4. Android: receive server state

**File:** `keyboard-android/app/src/main/java/com/runerback/keyboard/network/KeyboardClient.kt`

Add a data class and a StateFlow:

```kotlin
data class ModifierState(val capsLock: Boolean = false, val shift: Boolean = false)

private val _modifierState = MutableStateFlow(ModifierState())
val modifierState: StateFlow<ModifierState> = _modifierState.asStateFlow()
```

Inside `connect()`, after the socket and writer are created, also create a `BufferedReader` and launch a read coroutine in the same `scope`:

```kotlin
val reader = newSocket.getInputStream().bufferedReader()
scope.launch {
    runCatching {
        while (isActive) {
            val line = reader.readLine() ?: break
            parseModifierState(line)?.let { _modifierState.value = it }
        }
    }.onFailure { ... }
}
```

Add a parser for incoming state messages:

```kotlin
private fun parseModifierState(line: String): ModifierState? {
    return runCatching {
        val json = JSONObject(line)
        if (json.optString("type") != "state") return null
        ModifierState(
            capsLock = json.optBoolean("caps"),
            shift = json.optBoolean("shift")
        )
    }.getOrNull()
}
```

Reset `_modifierState` to `ModifierState()` on disconnect.

### 5. Android: drive the UI from server state

**File:** `keyboard-android/app/src/main/java/com/runerback/keyboard/ui/screens/KeyboardScreen.kt`

Change `KeyboardScreen` to accept server-driven modifier state:

```kotlin
fun KeyboardScreen(
    onKeyEvent: (vk: Int, action: String) -> Unit,
    modifier: Modifier = Modifier,
    connectionState: KeyboardClient.State? = null,
    serverModifierState: KeyboardClient.ModifierState = KeyboardClient.ModifierState(),
    onOpenSettings: (() -> Unit)? = null
)
```

Inside the composable, replace the local `capsActive`/`shiftActive` toggling with a merged state:

```kotlin
var capsActive by remember { mutableStateOf(serverModifierState.capsLock) }
var shiftActive by remember { mutableStateOf(serverModifierState.shift) }

LaunchedEffect(serverModifierState) {
    capsActive = serverModifierState.capsLock
    shiftActive = serverModifierState.shift
}
```

Keep `handleKeyEvent` sending the events, but do not let local phone taps override the server state optimistically for Caps Lock. For Caps, rely on the round-trip state update (it is fast on LAN and keeps the UI authoritative). For Shift, the phone sends down/up and the server state updates immediately; the UI follows.

This means remove:

```kotlin
if (vk == Vk.CAPITAL && action == "down") {
    capsActive = !capsActive
}
if (vk == Vk.SHIFT) {
    shiftActive = action == "down"
}
```

and instead let `LaunchedEffect` sync from `serverModifierState`.

The visual effects (Caps border, Shift primary color, uppercase letters, shifted symbols) already react to `capsActive`/`shiftActive`, so no visual changes are needed.

### 6. Android: pass state into KeyboardScreen

**Files:**
- `keyboard-android/app/src/main/java/com/runerback/keyboard/MainActivity.kt`
- `keyboard-android/app/src/main/java/com/runerback/keyboard/ime/KeyboardInputMethodService.kt`

In both entry points, collect `KeyboardClient.modifierState` and pass it to `KeyboardScreen`:

```kotlin
val connectionState by KeyboardClient.state.collectAsState()
val modifierState by KeyboardClient.modifierState.collectAsState()

KeyboardScreen(
    onKeyEvent = { vk, action -> KeyboardClient.sendKey(vk, action) },
    connectionState = connectionState,
    serverModifierState = modifierState,
    onOpenSettings = { ... }
)
```

## Files to Modify

- `keyboard-windows/src/InterceptionInput.h`
- `keyboard-windows/src/InterceptionInput.cpp`
- `keyboard-windows/src/KeyboardServer.h`
- `keyboard-windows/src/KeyboardServer.cpp`
- `keyboard-windows/src/JsonProtocol.h`
- `keyboard-windows/src/JsonProtocol.cpp`
- `keyboard-windows/src/main.cpp`
- `keyboard-android/app/src/main/java/com/runerback/keyboard/network/KeyboardClient.kt`
- `keyboard-android/app/src/main/java/com/runerback/keyboard/ui/screens/KeyboardScreen.kt`
- `keyboard-android/app/src/main/java/com/runerback/keyboard/MainActivity.kt`
- `keyboard-android/app/src/main/java/com/runerback/keyboard/ime/KeyboardInputMethodService.kt`

No manifest or theme changes are needed.

## Verification

1. Build the Windows server:
   ```bash
   cmake --build keyboard-windows/build
   ```
2. Build the Android app:
   ```bash
   ./keyboard-android/gradlew -p keyboard-android assembleDebug
   ```
3. Run the Windows server and connect the Android app.
4. Press **Caps Lock** on the real keyboard:
   - Phone Caps key shows the strong indicator and letters switch to uppercase.
5. Press **Caps Lock** again on the real keyboard:
   - Phone Caps indicator turns off and letters return to lowercase.
6. Hold **Shift** on the real keyboard:
   - Phone Shift key highlights, shifted symbols appear.
7. Release **Shift** on the real keyboard:
   - Phone Shift highlight and symbols revert.
8. Press **Caps Lock** on the phone:
   - Real keyboard LED toggles, then phone UI updates to match.
9. Press **Shift** on the phone:
   - Works as before; UI updates from the server's state feedback.
10. Confirm real keyboard typing is not blocked.

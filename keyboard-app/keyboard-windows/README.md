# Keyboard Windows Receiver

Receives JSON key events over TCP from the Android keyboard app and injects them as low-level keyboard input through the [Interception](https://github.com/oblitum/Interception) driver.

## Prerequisites

- Windows 10/11
- Interception driver installed (see below)
- CMake + Visual Studio 2022 Build Tools (or MinGW-w64)

## Install the Interception driver

The driver is required for the receiver to inject input. Download the latest release from the [Interception releases page](https://github.com/oblitum/Interception/releases/latest) and run the command-line installer as Administrator:

```cmd
install-interception.exe /install
```

On Windows 11 you may need to disable driver signature enforcement or enable test signing before the driver will load.

## Build

```cmd
cd keyboard-windows
cmake -B build -S .
cmake --build build --config Release
```

## Run

```cmd
build\Release\keyboard-windows.exe --port 50051
```

The app runs as a Windows GUI application. There is no console window; instead:

- A tray icon appears in the system tray.
- Right-click the tray icon and choose **Show / hide logs** (or double-click the icon) to open the built-in log window.
- Choose **Exit** to close the app.

The server listens on all interfaces. Enter the PC's LAN IP and port in the Android app and connect.

## Log window

All output that previously went to the console is now captured in the log window. It uses a dark background and monospace font to look like a terminal. Closing the log window hides it; the app keeps running in the tray.

## Protocol

Each key event is one newline-terminated JSON object:

```json
{"type":"key","vk":65,"action":"down"}
{"type":"key","vk":65,"action":"up"}
```

- `vk` is a Windows virtual-key code.
- `action` is `"down"` or `"up"`.

## Note

The Interception driver must be present. If you only see "Failed to create Interception context" at startup, the driver is not installed or is being blocked by Windows.

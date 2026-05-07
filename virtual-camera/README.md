# Static Image Virtual Camera

This workspace builds a **native Windows 11 virtual camera** that behaves like a hardware webcam and can stream a **static image** as its video source.

The workspace is **portable** as long as these top-level paths stay together:

- `assets\`
- `native\`
- `scripts\`
- `README.md`
- `THIRD_PARTY_NOTICES.txt`

That means you can keep it in `D:\Projects\virt_cam` or copy the same layout to another folder such as `D:\Projects\utils\virtual-camera`.

## Layout

- `native\VirtualCameraMediaSource` - local working copy of Microsoft's Media Foundation virtual-camera media source sample
- `native\VirtualCamera_Installer` - stripped-down control CLI for creating, removing, and listing the virtual camera
- `native\VirtualCameraNative.sln` - slim native solution that only builds the DLL and the control app
- `vendor\Windows-Camera` - untouched upstream Microsoft sample used as reference in the source workspace; not required in a copied portable layout
- `scripts\Build-Native.ps1` - build helper for the native solution
- `scripts\Install-VirtualCamera.ps1` - copies the built binaries into a stable machine-visible deployment folder, registers the media-source DLL, and installs the current-user virtual camera
- `scripts\Uninstall-VirtualCamera.ps1` - removes the virtual camera, unregisters the media source, and deletes the deployment folder
- `assets\default-camera.png` - default still image used for installs and smoke tests

## Current state

- The native solution now builds successfully on this machine.
- The helper CLI supports `install`, `uninstall`, `register-source`, `unregister-source`, `create`, `remove`, and `list`.
- The synthetic media source now accepts an image path attribute, decodes the still image once with WIC, scales it to the active frame size, and reuses it for RGB32 or NV12 output.

## Prerequisites

This workspace currently expects:

- Visual Studio with the Visual C++ toolchain
- Windows 10/11 SDK headers and libraries

`scripts\Build-Native.ps1` checks for those prerequisites before building.

## Build

```powershell
.\scripts\Build-Native.ps1 -Configuration Debug
```

## Install the virtual camera

```powershell
.\scripts\Install-VirtualCamera.ps1 -Configuration Debug -Name "Static Image Camera"
```

This installs a **current-user** virtual camera, copies the runtime into `%ProgramData%\StaticImageVirtualCamera`, stores the deployed still-image path as an activation attribute on the virtual camera, and prompts for elevation so the media-source DLL can be registered machine-wide.

By default the install script uses `assets\default-camera.png` from the current workspace root. You can still override that with `-ImagePath <path>`.

## Remove the virtual camera

```powershell
.\scripts\Uninstall-VirtualCamera.ps1 -Configuration Debug -Name "Static Image Camera"
```

## Notes

- The current registration flow uses **machine-wide COM registration** for the media-source DLL, while the virtual camera itself is still created with **current-user** access.
- The install script deploys the DLL, its app-local runtime dependencies, and the still image into a machine-visible directory before registration; do not register the DLL directly from the transient build output directory.
- The still image is loaded when the media source activates, then reused as repeated video frames.
- If you copy this workspace to another folder, keep the same top-level `assets`, `native`, and `scripts` layout so the scripts can resolve the solution and default image.
- The remaining work is compatibility validation in real camera consumers and any packaging cleanup needed after that validation.

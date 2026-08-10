# OpenPose Editor

A 3D OpenPose editor for Android. Pose a fixed 3D skeleton from any angle, then export a standard 2D OpenPose image plus JSON keypoints.

## Overview

The editor separates the interactive 3D viewport from the final render camera:

- **Viewport camera** — orbit, pan, and zoom around the character to place joints.
- **Render camera** — independent camera that produces the final 2D OpenPose output.
- **Pose mode** — lock the viewport and drag joints directly; bone lengths are preserved.
- **Body-part groups** — toggle hands, feet, and face; frame the camera on any group. Only the body is visible on first launch.

## Features

- 3D skeleton preview with OpenPose-style colors
- Orbit / pinch-zoom viewport gestures; default front view of the character
- Pose mode for screen-space joint manipulation with selected-joint highlight; joints move on a view-normal circle so bone length is preserved
- Toggle visibility per body-part group: left hand, right hand, left foot, right foot, face; face includes eyes and ears
- Submenu checkbox labels are clickable, not just the checkbox itself
- Camera framing actions: full body, left hand, right hand, feet, face
- Lock viewport camera to render camera with optional sync
- Reset viewport camera without affecting render camera
- Workzone toolbar shortcut to enter/leave pose mode
- Export PNG image + OpenPose-compatible JSON
- 2D pose preview dialog: drag the rendered pose, then export PNG or JSON separately; reset to origin when moved
- Save / load projects as JSON
- System menu with in-memory logs viewer (copy / clear)

## Tech Stack

| Layer | Technology |
|-------|------------|
| UI | Jetpack Compose |
| 3D rendering | OpenGL ES 3.0 in `GLSurfaceView` |
| Math | JOML |
| Serialization | Kotlinx Serialization |
| Language | Kotlin 2.1.0 |
| Build | Gradle with Android Gradle Plugin 8.7.3 |

## Project Structure

```
app/src/main/java/com/runerback/openposeeditor/
├── MainActivity.kt                  # Entry point
├── ui/
│   ├── EditorScreen.kt              # Compose UI, side menu, gesture handling
│   ├── EditorViewModel.kt           # Camera logic, pose editing, framing
│   ├── EditorState.kt               # Global observable state (singleton)
│   ├── ProjectState.kt              # Save / load serialization
│   ├── MenuCategory.kt              # Side-menu categories
│   ├── LogViewDialog.kt             # Standalone log viewer dialog
│   ├── LogBuffer.kt                 # In-memory log cache with timestamps
│   └── PreviewDialog.kt             # Standalone 2D pose preview / export dialog
├── render/
│   ├── PoseRenderer.kt              # OpenGL joint/bone drawing
│   ├── EditorGLRenderer.kt          # GLSurfaceView renderer bridge
│   ├── Camera.kt                    # View/projection math
│   ├── PrimitiveMeshes.kt           # Sphere / cylinder generators
│   ├── Mesh.kt                      # OpenGL mesh wrapper
│   └── ShaderProgram.kt             # Shader compile / link helper
├── skeleton/
│   ├── Skeleton.kt                  # Skeleton interface
│   ├── ProceduralSkeleton.kt        # Built-in stick-figure skeleton
│   ├── Keypoint.kt                  # Joint data
│   ├── Bone.kt                      # Bone data
│   ├── KeypointGroup.kt             # BODY, LEFT_HAND, RIGHT_HAND, LEFT_FOOT, RIGHT_FOOT, FACE
│   └── SkeletonPose.kt              # Pose capture / apply
└── export/
    ├── KeypointExporter.kt          # Exporter interface
    └── OpenPoseExporter.kt          # OpenPose JSON exporter

app/src/main/assets/shaders/
├── skeleton.vert
└── skeleton.frag
```

## Building

Requires Android SDK with API 35 installed.

```bash
./gradlew :app:assembleDebug
```

The debug APK is written to:

```
app/build/outputs/apk/debug/openpose-editor-debug.apk
```

## Usage

### Viewport gestures

- **One finger drag** — rotate around the character
- **Two finger pinch** — zoom in / out

### Side menu

Tap an icon to open its submenu. Tap the same icon again or tap the 3D view to close it.

| Icon | Actions |
|------|---------|
| Show | Toggle left hand, right hand, left foot, right foot, face (labels are clickable) |
| Pose | Enable pose mode for joint editing |
| Zoom | Frame full body, hands, feet, face; return to previous camera |
| Camera | Lock view, sync render camera to viewport, reset viewport |
| File | Preview, save project, load project |
| System | Show Logs |

### Workzone toolbar

A shortcut chip in the top-right of the 3D view toggles pose mode without opening the side menu.

### Pose mode

1. Open the **Pose** submenu or tap the workzone toolbar shortcut to enable **Pose mode**.
2. Tap a joint to select it.
3. Drag to move the joint. Movement stays on a plane perpendicular to the current view and preserves bone length, so dragging up/down/left/right maps predictably to the screen.
4. Tap another joint to switch selection; tap empty space to deselect.

### Export

1. Open **File → Preview** to see the render-camera output.
2. Drag the rendered pose to reposition it in the 2D frame. A **Reset** button appears once the pose is moved.
3. Tap **Export png** or **Export json** to save only that file.
4. Files are saved to `Android/data/com.runerback.openposeeditor/files/OpenPoseEditor/`.

## Output Format

The exported JSON follows the OpenPose single-person format:

```json
{
  "version": 1.3,
  "people": [
    {
      "pose_keypoints_2d": [x0, y0, c0, x1, y1, c1, ...],
      "face_keypoints_2d": [...],
      "hand_left_keypoints_2d": [...],
      "hand_right_keypoints_2d": [...]
    }
  ]
}
```

Each keypoint is a triplet: `x`, `y`, and confidence (`1.0` for enabled joints, `0.0` for disabled/missing joints).

## Project Save Format

Saved projects contain:

- Skeleton pose (joint rotations)
- Viewport camera state
- Render camera state
- Visible body-part groups

## Notes

- The skeleton is procedural and fixed in place; only joint rotations change, so bone lengths stay consistent.
- The render camera is shown in the viewport as a semi-transparent yellow wireframe frustum.
- The default viewport is a front view of the character.
- On first launch, only the body group is visible; hands, feet, and face are hidden and can be enabled from the **Show** menu.
- Pose-mode movement uses a view-normal plane: the dragged joint travels on a circle that faces the current camera, so screen-space dragging is predictable and bone length is preserved.
- Google-specific frameworks are avoided where possible; rendering uses Khronos OpenGL ES directly.

## Future Work

- Replace the procedural skeleton with a PMX (MMD) model loader
- Add more keypoint export formats (COCO-WholeBody, etc.)
- Add rotation constraints per joint for more anatomically correct posing

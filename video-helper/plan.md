# Video editor web app plan (Python + FFmpeg + FastAPI)

## Problem and approach
Build a non-destructive web-based video editor using Python and FFmpeg where users can load a source video, preview original and current modified result, and export a new file without altering the original.  
Use **FastAPI + HTML/JS** for UI/API, keep edit state in a server-side session/project model, and use FFmpeg for metadata probing, preview/proxy generation, and final export render.

## Scope delivered
- Load one source video per project/session.
- Play **original video** and **current modified preview** in the web UI.
- Edit operations:
  - **Split (trim)**: single kept A-B segment.
  - **Crop**: drag selector on preview or numeric inputs.
  - **Frame rate**: whole-video-only setting.
- Split controls:
  - A/B via frame-step controls, duration/time input, and draggable range sliders.
  - Timeline zoom control from 0.04s window to 10s window.
- Crop presets: 4:3, 3:4, 16:9, 9:16.
- Non-destructive workflow: source video remains unchanged.
- Modified preview strategy: FFmpeg-generated proxy clip for browser playback.

## Architecture implemented
1. Backend (FastAPI)
   - Upload endpoint, project read/update state endpoints, preview render endpoint, export endpoint.
   - Metadata probing via `ffprobe`.
   - FFmpeg command builder for trim/crop/fps filtergraph.
   - File storage layout in `uploads/`, `work/`, `exports/`, `projects/`.

2. Frontend (HTML/JS)
   - Dual-player layout: Original | Modified Preview.
   - Trim controls (ranges, frame-step buttons, numeric inputs, zoom control).
   - Crop overlay drag selector + numeric crop inputs.
   - Crop preset buttons.
   - Whole-video FPS input.
   - Save state, refresh preview, and export actions.

3. Non-destructive state model
   - Immutable original file path per project.
   - Server-side JSON project state for trim/crop/fps with validation.

4. Processing strategy
   - Deterministic filter order: trim -> crop -> fps.
   - Preview render optimized for quick playback.
   - Export render produces final downloadable output.

## Progress
- [x] Scaffold FastAPI app and static UI.
- [x] Implement upload/probe API and immutable storage.
- [x] Define project edit-state schema and validation.
- [x] Build FFmpeg command builder.
- [x] Implement preview proxy generation endpoint.
- [x] Implement export endpoint.
- [x] Build dual-player UI and API wiring.
- [x] Implement trim timeline controls and zoom.
- [x] Implement crop interactions and presets.
- [x] Implement whole-video frame-rate setting.
- [x] Add explicit backend error surfacing.
- [x] Add core FFmpeg/validation tests.

## Next implementation (active)
- Add a **Resize** operation with one input: `max longer edge`.
- Keep aspect ratio automatically; no free width/height mode.
- Apply resize on the currently modified frame (after crop), then fps.
- Show dimensions beside both player headers:
  - `Original`: source dimensions.
  - `Preview`: computed modified dimensions after crop/resize.

## Resize implementation checklist
- Extend edit schema with optional `resize_max`.
- Update FFmpeg filters to `trim -> crop -> resize -> fps`.
- Add resize validation and command tests.
- Add UI controls for resize and wire load/save state.
- Add header dimension labels and refresh logic.
- Run tests after implementation.

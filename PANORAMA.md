# PANORAMA.md

## Goal

Add a new **“Capture Panorama”** button to the **pause screen** in MattMC (Minecraft 1.21.10 fork).  
When pressed, it should capture a **6-face cubemap panorama** of the player’s current view location and save the images to disk for later use (e.g., title-screen panoramas, screenshots, etc.).

This should work in **singleplayer** and should not require external mods.

---

## Output Format

The capture produces **6 square PNG images**, corresponding to the 6 directions of a cubemap:

- `panorama_0.png`
- `panorama_1.png`
- `panorama_2.png`
- `panorama_3.png`
- `panorama_4.png`
- `panorama_5.png`

All images must be:
- Square (same width/height)
- Same resolution (recommend `1024x1024` by default)
- Captured from the same world position and same moment in time

---

## Save Location (Updated)

Save to a **new timestamped folder inside the normal screenshots folder**, so the screenshots directory stays clean and it’s obvious what belongs together.

Folder layout:

`<gameDir>/screenshots/<timestamp>/`

Example:

`./screenshots/2025-12-28_17.34.12/panorama_0.png`  
`./screenshots/2025-12-28_17.34.12/panorama_1.png`  
...  
`./screenshots/2025-12-28_17.34.12/panorama_5.png`

Notes:
- Timestamp format should be filesystem-friendly (avoid `:` on Windows).
- If folder creation fails, fall back to saving directly under `./screenshots/`.

---

## Capture Requirements

### Camera / Projection
- Use **FOV = 90** during capture (restore user FOV afterward).
- Capture six views by rotating the camera:

Suggested rotation set (match whatever CubeMap/Panorama renderer expects):
- Four horizontal directions (yaw offsets 0/90/180/270, pitch 0)
- Up (pitch -90)
- Down (pitch +90)

**Important:** The exact face order must match the order expected by Minecraft’s panorama cubemap renderer (the class that renders the title panorama). Do not guess—mirror the engine’s face order.

### Rendering
- Render the **world only** (no HUD, no UI widgets).
- The capture must be done on the **render thread**.
- Render into an **offscreen framebuffer / render target** sized `NxN`.
- After rendering each face, read pixels and write a PNG.

### Restore State
After capture completes (or fails), restore:
- Player camera yaw/pitch
- FOV
- Any temporary render options changed (hide GUI, view bobbing, hand rendering, etc.)
- Main framebuffer binding / viewport

---

## User Experience

### Pause Screen Button
Add a button labeled:
- `"Capture Panorama"`

Placement:
- In the pause menu button list, near other utility buttons (e.g., above “Options” or near “Save and Quit”).

Click behavior:
- Starts a panorama capture job.
- Shows a short toast/chat message:
    - `Capturing panorama... (1/6)`
    - `Saved panorama to: <path>`

### Performance / Stutter Control
Capturing 6 full renders can hitch. Prefer capturing **one face per frame** over 6 frames:

- Create a `PanoramaJob` state machine that captures one face each render tick.
- The job completes after face 5 is written.

This keeps the UI responsive and makes the hitch smaller.

---

## Implementation Plan (High Level)

### 1) UI Hook
In the pause screen class (`PauseScreen` / `InGameMenuScreen` depending on mappings):
- Add a new button in `init()`
- On click: call `PanoramaCapture.start()`

### 2) Capture Manager
Implement a manager class (example name):
- `net.minecraft.client.screenshots.PanoramaCapture` (or `mattmc.client.PanoramaCapture`)

Responsibilities:
- Maintain a single active `PanoramaJob`
- Provide `start(Minecraft mc)` and `onFrame(Minecraft mc)` methods
- Ensure capture runs on the render thread

### 3) Rendering + Saving
For each face:
- Bind offscreen framebuffer
- Set viewport to `size x size`
- Apply camera rotation for that face
- Render world scene
- Read pixels into `NativeImage`
- Create the timestamp folder under `./screenshots/` (once per capture)
- Write PNG to disk as `panorama_<i>.png`
- Restore main framebuffer binding

### 4) Face Order
Locate the vanilla panorama/cubemap renderer class used by the title screen:
- often named something like `CubeMap`, `PanoramaRenderer`, or similar

Copy its face ordering convention so the six files match the expected `panorama_0..5` mapping.

---

## API / Class Notes

- Use Minecraft’s existing screenshot utilities if available:
    - `Screenshot.takeScreenshot(RenderTarget)`
    - `NativeImage.writeToFile(Path)`
- Use a `TextureTarget` / `RenderTarget` for offscreen rendering
- Avoid allocating large buffers repeatedly:
    - reuse the render target
    - write per-face images and immediately close the `NativeImage`

---

## Edge Cases

- If the player is not in a world (no level loaded), disable/hide the button.
- If a capture is already running, disable the button or show `Capture already in progress`.
- If disk write fails:
    - log the exception
    - show a toast/chat error
    - restore state and abort cleanly
- If shaders are enabled:
    - capture should reflect what the player sees (shader output), unless explicitly disabled.

---

## Success Criteria

- Button appears on pause screen.
- Clicking it creates a **new timestamped folder** under `./screenshots/`.
- The folder contains exactly 6 PNG files: `panorama_0.png` through `panorama_5.png`.
- Face order is correct for use as a Minecraft panorama.
- After capture, the player’s settings/camera state are restored.
- No crashes; capture failure is handled gracefully.
- Minimal hitching (prefer one face per frame).

---

## Optional Enhancements

- Configurable resolution (512/1024/2048)
- Option to also generate a “preview” stitched image
- Automatically copy the 6 files into a resourcepack-friendly folder layout
- Add a “Copy path to clipboard” button after completion

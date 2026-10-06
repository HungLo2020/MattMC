# Retained render scene

**Status: phase 1 in progress (October 2026).** Sections marked *current*
describe the code as it is today; the phase list is the target. Update this
page as phases land.

## Why

On the moving shader benchmark the renderer runs at ~119 FPS against Frozen's
~316. The GPU (~3.4 ms) is close to Frozen; the CPU is not. Both the Java render
thread (~5.2 ms of work) and the Rust frame worker (~6.0 ms busy) individually
exceed Frozen's whole 3.2 ms frame, and neither has a dominant hotspot.

*Current:* every frame Java re-sends one record per visible section layer
(~6,800, ~1 MB), each carrying a camera-relative transform. Rust then
re-validates every record, rebuilds batch plans keyed by the full instance list
and the camera-dependent facing masks, re-selects shadow casters, rebuilds a
~1,600-op command list and re-validates it. Section geometry is already
persistent on the GPU; the description of what to draw is not. Caches miss
whenever the camera moves because their keys contain camera-dependent data.

Frozen (Sodium + Iris) keeps draw lists per region and per frame only walks
visibility and issues a few multi-draws. Parity needs the same shape: the
per-frame cost must scale with *visible sections*, through one tight loop, not
with every record through several generic layers.

## Target architecture

```
ingestion API  ──►  RenderScene (Rust)  ──►  per-frame draw generation  ──►  passes
(Java today,        persistent objects,      visible set + camera →          vanilla, shader
 Rust later)        validated on entry       indirect command runs           camera, shadow
```

- **`RenderScene`** owns persistent render objects. Terrain first: one
  `TerrainSection` per section position, with per-layer residency (geometry
  page, index base), per-facing draw commands for each material run, and its
  integer world origin. Entities and block entities follow the same pattern
  later.
- **The ingestion API is the renderer's permanent interface**, not a migration
  shim: add or replace a section mesh, remove a section, set the visible set,
  set the camera. Java is its first caller; Rust world, meshing and visibility
  code call the same API as they migrate. Validation, range derivation and
  material classification happen here, once per change.
- **Per-frame draw generation** walks only the visible set: pick facings from
  the camera cell, append the precomputed commands into per-pass runs grouped
  by (material mode, page), and write each visible section's camera-relative
  instance record. No generic batch plans, no per-record validation.
- **Passes consume scene runs.** Vanilla opaque/cutout/translucent, the shader
  camera passes and the shadow pass all read the same section records; the
  shadow pass uses its own visible set.
- **Rust owns everything on the GPU side**; callers only describe the scene.

### Rules

- Visibility and culling stay identical to Frozen (Sodium portal search for
  the camera; Iris' shadow traversal for the shadow pass) until the user
  decides otherwise. Faster culling is a later, separate, measured change.
- Validate at ingestion, never per frame. Per-frame code may assume scene
  invariants.
- Memory stays bounded and reported; persistent GPU buffers are allowed.
- Vulkan only (newest stable, RTX 2070 minimum). The OpenGL backend is out of
  scope for new scene features.
- Replace system by system. Each phase deletes the code it replaces; there is
  no parallel renderer.

## Current: scene terrain on the shader route

Static chunk sections whose meshes are resident are drawn by
[`scene_terrain.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/worldrender/source/frames/scene_terrain.rs):

- When the general path first draws a resident mesh, its retained record gets
  `SceneTerrainGroup`s: one per facing group, with its absolute page index
  range, material kind, cull and winding (`scene_terrain_groups`).
- Each frame, `partition_scene_terrain_instances` takes the described camera
  sections and shadow-only casters out of batching. `prepare_scene_terrain_draws`
  writes one instance block for them and appends each section's
  facing-selected groups to runs keyed by (kind, cull, winding, page); a run
  is one indirect draw (`TerrainIndexedIndirect::draw_count`).
- Camera runs carry shadow twins; the camera sections' unselected faces and
  the light-frustum casters become shadow-only runs. Translucent runs keep
  frame order and split on any state change.
- Shadow casters follow Frozen: Java offers only sections the camera
  traversal has built (no shadow-only builds), and every section, camera
  visible or not, casts only if it passes Sodium's shadow-tree leaf test
  (`source_shadow_origin_intersects`: centre ±8, no distance cylinder).
- Undescribed sections (first frame after upload, changed material ids, a
  pending upload) and camera-sorted translucent sections keep the batch path.
- Terrain coverage receipts subtract the scene's indices
  (`SourceTerrainDrawCoverage::without_scene`); the scene covers its own by
  construction (`scene_terrain` and `indexed_indirect_runs_*` tests).

Since ABI 70, Java sends visible sections and casters as compact entries (see
[Java Bridge](JAVA-BRIDGE.md)). When the shader route is armed, admission
(`frame/static_terrain.rs`) keeps them compact: `take_scene_terrain` turns
described sections and casters into scene entries and expands only the rest
into instances. A frame that leaves the shader route, and every other route,
calls `expand_static_terrain` first, so they still see ordinary instances.
Coverage validation and voxel occupancy read the compact and scene terrain
directly. A retained record is dropped whenever its key acknowledges another
generation, so a record's presence proves it is current.

## Phases

Each phase ends with Rust/Java tests passing and the full parity matrix
(below) recorded in `PROGRESS.md`, then a local commit.

1. **Retained terrain sections.** `RenderScene` terrain records built when a
   section mesh is accepted. Java sends a compact visible-section list instead
   of per-section instance records; Rust generates terrain indirect runs for
   both routes directly from the records. Removes per-frame terrain
   validation, batch plans and range memos.
2. **Shadow casters from the scene**, with Frozen-identical shadow section
   selection (Iris shadow traversal) instead of the current
   "every built section in range" approximation.
3. **Visibility in Rust.** Port Frozen's Sodium occlusion search exactly onto
   the scene's section graph; Java sends only camera and frustum. Removes the
   Java terrain enqueue (~1.2 ms).
4. **Static instance data.** World origins become persistent per-section data
   and the camera a per-frame uniform, so nothing per-section is written while
   only the camera moves.
5. **Retained entities and block entities**: persistent model instances, with
   per-frame transform and pose data only.
6. **Beyond Frozen:** GPU-driven culling into indirect-count buffers, and
   parallel command recording per pass.

## Parity matrix

Equal performance and matching visuals across vanilla, shaders, DH and
shaders + DH, each in settled and moving scenes. Image differences must stay
within the existing thresholds with zero validation events; see
[Render Verification](RENDER-VERIFICATION.md) for the harness commands.

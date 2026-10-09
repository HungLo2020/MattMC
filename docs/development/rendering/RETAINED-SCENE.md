# Retained render scene

**Status: shader-route retained terrain and the Rust visibility search are
implemented in part; the full scene target remains open (October 2026).**
ABI 70 carries compact terrain, and ordinary frames now select those compact
camera layers and shadow candidates in the Rust section graph. Native
publication rows and vertex staging, plus the DH ledger/ABI 72 generic groups, retain CPU input data.
Reduced-color DH geometry also uses shared GPU pages, with the separate
ownership limits below. Diagnostic and
other routes retain their documented producer and expansion paths. The phase list separates remaining targets
from the current implementation; it is not a declaration of parity completion.

## Why

At the initial retained-scene checkpoint, the moving shader benchmark ran at ~119 FPS against Frozen's
~316. The GPU (~3.4 ms) is close to Frozen; the CPU is not. Both the Java render
thread (~5.2 ms of work) and the Rust frame worker (~6.0 ms busy) individually
exceed Frozen's whole 3.2 ms frame, and neither has a dominant hotspot.

*Historical starting point, before ABI 70:* every frame Java re-sent one record per visible section layer
(~6,800, ~1 MB), each carrying a camera-relative transform. Rust then
re-validates every record, rebuilds batch plans keyed by the full instance list
and the camera-dependent facing masks, re-selects shadow casters, rebuilds a
~1,600-op command list and re-validates it. Section geometry is already
persistent on the GPU; the description of what to draw is not. Caches miss
whenever the camera moves because their keys contain camera-dependent data.
The [current compact/scene route](#current-scene-terrain-on-the-shader-route)
below supersedes this per-section-record description; the original workload
figures are retained as motivation, not current performance acceptance.

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
- Shadow candidates come from sections the camera traversal has built, with
  no shadow-only builds. Ordinary frames select them from graph mesh rows
  synchronized directly from Rust's publication registry; diagnostic and other
  ineligible frames retain the Java producer.
  Every section, camera visible or not, casts only if it passes Sodium's
  shadow-tree leaf test (`source_shadow_origin_intersects`: centre ±8, no
  distance cylinder).
- Undescribed sections (first frame after upload, changed material ids, a
  pending upload) and camera-sorted translucent sections keep the batch path.
- Terrain coverage receipts subtract the scene's indices
  (`SourceTerrainDrawCoverage::without_scene`); the scene covers its own by
  construction (`scene_terrain` and `indexed_indirect_runs_*` tests).

Since ABI 70, visible section layers and casters cross the Java bridge as compact
entries (see [Java Bridge](JAVA-BRIDGE.md)). Ordinary frames now form them in
[`chunk/terrain_selection.rs`](https://github.com/HungLo2020/MattMC/blob/313e7a8a82a34dc915c4924a78da77c720af2f7e/src/main/rust/render/chunk/terrain_selection.rs);
Java copies the native records into its frame/request storage. At the
[`20e157ca` checkpoint](https://github.com/HungLo2020/MattMC/blob/20e157cab7962140b30b83f40374cdeb1e6a8b19/src/main/rust/render/chunk/terrain_selection.rs),
solid/cutout layers preserve graph visit order while translucent layers remain
back to front; this supersedes the earlier key-order equality report. The section
graph owns readiness/build bookkeeping, visible-slot stamps and selected animated
sprite IDs; Java marks the corresponding sprite objects. At `4740f8fa`, native
[intake](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/worldrender/terrain/intake.rs) also assembles section layers: sorter-order normalization, unsupported-fluid
omission, water/material classification, range splitting and mesh identity
([`terrain/assembly.rs`](https://github.com/HungLo2020/MattMC/blob/4740f8fabffd878286850083e2d86ff733c9121e/src/main/rust/render/worldrender/terrain/assembly.rs)).
Java supplies build/sort/atlas inputs, dispatches meshing workers, publishes the
returned assets and runs the ineligible-frame producer. The native
[publication registry](JAVA-BRIDGE.md#terrain-publication-registry) now owns
section-layer identities and their graph-sync queue; Java still registers those
rows and coordinates upload acknowledgement and resource-reload swaps. The
prior Java water/index assembly boundary is historical. Since `1d609f12`, ordinary assembly keeps
vertices in a native staging map until asset acceptance or discard; Java gets
identity/count metadata plus copied indices, ranges and receipts. Asset decode
clones the exact staged generation for retry safety. Diagnostic vertex readers
and staging-capacity misses use copied vertex output. See
[terrain intake](JAVA-BRIDGE.md#terrain-layer-intake) for bounds and fallback.
Native record selection and retained GPU scene drawing
are separate steps. When the shader route is armed, admission
(`frame/static_terrain.rs`) keeps them compact: `take_scene_terrain` turns
described sections and casters into scene entries and expands only the rest
into instances. A frame that leaves the shader route, and every other route,
calls `expand_static_terrain` first, so they still see ordinary instances.
Coverage validation and voxel occupancy read the compact and scene terrain
directly. A retained record is dropped whenever its key acknowledges another
generation, so a record's presence proves it is current.

## Current: retained DH inputs

The [DH column ledger](RENDER-ARCHITECTURE.md#resource-ownership-and-retries)
retains immutable packed payloads and publication/lease/route state in Rust.
Java still walks the quadtree, supplies provenance and render parameters, and
copies selected records into the frame. Ordinary publication builds frontend
assets from the ledger; exact-material provenance retains the Java publication
path. Batched collection checks each walked container's generation before
publication/visibility admission. Container close retires only its lease, and
a build finishing after section close releases its new container instead of
installing it. These changes preserve CPU lifetime without moving the DH tree
or all frame preparation into Rust.

Reduced-color GPU columns now suballocate shared device vertex/index pages;
packed-uniform pass owners also share geometry bindings per vertex page.
Exact-atlas residency keeps separate buffers, and DH source draw recording
still emits individual indexed draws. See [page upload and retirement](RENDER-ARCHITECTURE.md#resource-ownership-and-retries)
for submission-gated reuse and idle binding retention. Shared storage is
implemented; DH multi-draw and all-route retained scene ownership remain targets.

ABI 72 similarly retains DH generic-group boxes in a CPU registry, with one
per-frame group instance carrying origin, light, shading and SSAO. Native decode
still expands these into ordinary camera-relative box requests. Java owns the
callbacks and change notifications; missing generations request resending. This
is separate from retained GPU terrain and phase 5's entity/block-entity target.
Neither change establishes all-route scene ownership or Frozen parity.

### Next proposed ownership slice

Remove the visible-segment round trip: Rust currently selects the list, exports
it into Java records, and decodes those records back into another native list.
The next candidate is a bounded native CPU frame transaction consumed directly
by the renderer, with Java carrying its identity and scalar counts. Move the
coordinator's layer counting and diagnostic-copy consumers with this boundary;
otherwise they would recreate the same list every frame. Preserve exact order,
column generations, lifecycle rejection, queued-frame ownership and capture
readback. This slice is not implemented yet. Profile the moving DH workload
against Frozen first, then verify real transitions and images alongside timing.

## Phases

Each phase ends with Rust/Java tests passing and the full parity matrix
(below) recorded in `PROGRESS.md`, then a local commit.

1. **Retained terrain sections.** `RenderScene` terrain records built when a
   section mesh is accepted. Java sends a compact visible-section list instead
   of per-section instance records; Rust generates terrain indirect runs for
   both routes directly from the records. Removes per-frame terrain
   validation, batch plans and range memos.
2. **Shadow casters from the scene**, with Frozen-identical shadow section
   selection (Iris shadow traversal). Current candidates use built geometry
   without shadow-only builds and the shared leaf test described above;
   complete scene-owned visibility remains part of the target.
3. **Visibility in Rust.** Port Frozen's Sodium occlusion search exactly onto
   the scene's section graph; Java sends only camera and frustum. Removes the
   Java terrain enqueue (~1.2 ms).
   *Status:* the search runs in Rust (`chunk/section_graph.rs`), and ordinary
   frames also select compact camera layers, shadow candidates and animated
   sections in `chunk/terrain_selection.rs`. Java skips its visible-list and
   per-section record construction on that route. Rust now owns build request
   bookkeeping and entity-culling visit stamps; Java dispatches the listed
   requests, invokes native publication-to-graph sync and marks selected sprites.
   Shader-disabled frames also extract block entities from visited built sections plus global
   entries when a current search exists; Java still performs their semantic
   extraction, so this is not the retained-entity phase. Diagnostic/fault/
   reload/readiness-receipt frames keep the Java producer. Render-list region
   order, Iris's non-culling frustum and complete scene-owned visibility remain;
   this does not complete every phase or remove Java.
4. **Static instance data.** World origins become persistent per-section data
   and the camera a per-frame uniform, so nothing per-section is written while
   only the camera moves.
5. **Retained entities and block entities**: persistent model instances, with
   per-frame transform and pose data only. ABI 71 rigs are partial progress:
   cacheable models reuse local-space assets and send raw poses, but Rust still
   expands ordinary per-frame instances and Java retains animation/semantic
   extraction. First-person and other ineligible models retain Java posing.
   [#803](https://github.com/HungLo2020/MattMC/issues/803) and
   [#819](https://github.com/HungLo2020/MattMC/issues/819) remain bounded gaps;
   this is not completion of the retained-entity phase.
6. **Beyond Frozen:** GPU-driven culling into indirect-count buffers, and
   parallel command recording per pass.

## Parity matrix

Equal performance and matching visuals across vanilla, shaders, DH and
shaders + DH, each in settled and moving scenes. Image differences must stay
within the existing thresholds with zero validation events; see
[Render Verification](RENDER-VERIFICATION.md) for the harness commands.

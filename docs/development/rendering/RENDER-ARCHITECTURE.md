# Render architecture

The native renderer is split into layers with one-way dependencies. Java hands
semantic frames to the bridge; renderers turn them into GAL command lists; the
GAL validates and executes them on a private backend.

```text
render/
├── bridge/       Java C ABI: wire records, decoding, context registry (composition root)
├── worldrender/  world renderer; composes the GUI on the whole-frame route
├── guirender/    GUI renderer: sprites, quads, item meshes, post effects
├── shaderpack/   shader-pack parsing, planning and runtime
├── shared/       helpers used by both renderers
├── scene/        wire and data vocabulary (constants, data types)
├── vulkanic/     VulkanicGAL: the graphics abstraction layer and its backends
└── chunk/        native chunk meshing, render lists and sorting used by Java's chunk renderer
```

## Dependency rules

Each layer may use the public GAL modules and the layers listed for it. The
rules are enforced by
[`architecture_boundary.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/vulkanic/architecture_boundary.rs),
which runs with the Rust tests.

| Layer | May depend on |
| --- | --- |
| `vulkanic` (GAL and backends) | nothing above it |
| `scene` | GAL value types only (`vulkanic::resources`) |
| `shared` | the public GAL |
| `shaderpack` | `scene` |
| `guirender` | `scene`, `shared`, `shaderpack` |
| `worldrender` | `scene`, `shared`, `shaderpack`, `guirender` |
| `bridge` | everything above, as the composition root |

Further rules:

- Only `vulkanic/backends/` names a backend (Vulkan or OpenGL). Everyone else
  branches on `BackendCapabilities` (features, limits, shader conventions),
  never on which backend is running.
- Only the bridge creates GALs (`VulkanicGal::create*` with `BackendChoice`).
- Code outside `vulkanic` uses the public GAL modules only; tests build GALs
  through `vulkanic::test_support`.
- `guirender` never names `worldrender`. World-owned atlases reach the GUI
  through the `GuiAtlasOwner` trait, which the world renderer implements.
- The GAL carries no game vocabulary and never branches on resource labels.
  Renderer-specific profiling lives in the renderers (`WholeFrameProfile` in
  `worldrender` embeds the GAL's `SubmitProfile`).

## Where new code goes

| You are adding | Put it in |
| --- | --- |
| A new kind of draw, pass or effect for the world | `worldrender/` (see its README) |
| GUI drawing, item rendering or a GUI post effect | `guirender/` |
| A wire constant or data type shared with Java | `scene/` |
| Shader-pack parsing or pass planning | `shaderpack/` |
| A new Java entry point or wire record | `bridge/` (see [Java Bridge](JAVA-BRIDGE.md)) |
| A new GPU capability, resource type or command | `vulkanic/` (see [VulkanicGAL](VULKANIC-GAL.md)) |

A new GPU feature belongs in the GAL only if it is game-agnostic; anything
that knows about blocks, entities, the GUI or shader packs belongs in a
renderer. If a renderer needs a backend difference, add a capability rather
than checking the backend.

## Resource ownership and retries

Source shadow terrain applies its existing dimension, distance and light-frustum
policy before constructing batches. Validate every shadow candidate's asset
generation, section and sorted topology even when it is culled. Selected batches
retain original frame indices; their four-entry cache includes those positions
and identities. Repeated mesh keys use the existing build-then-filter ordering
and bypass the selected cache, since culled draws can affect grouping order.
The source plan retains its final frustum check. See
[`geometry/batching.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/worldrender/geometry/batching.rs)
and
[`source/frames/terrain.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/worldrender/source/frames/terrain.rs).

Source terrain multi-draw binds the instance storage stream at offset zero;
`firstInstance` addresses 80-byte records within that stream. Pack these records
on their stride, while aligning each absolute uniform descriptor offset and
direct-draw instance descriptor offset to 256 bytes. Equal same-frame uniforms
share immutable packed CPU blocks and their existing upload offsets. The packed
block memo retains at most 64 entries and resets at the next frame; changed
uniform values or texture transforms get separate blocks. Keep each draw’s
instance payload independent and preserve owned bytes at GAL upload boundaries. Keep the conservative descriptor-aligned
per-batch capacity reservation separate from the packed upload size. The
frame-stream allocator in
[`geometry/arenas.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/worldrender/geometry/arenas.rs)
retains bounded capacity, frame epochs and completion-gated reuse. Changing
record packing must preserve descriptor alignment in mixed direct/multi-draw
allocations as well as exact staged bytes and indirect instance indexing.

Keep upload data and its owner retryable until submission succeeds. Mesh and
sorted-index replacements must validate and submit before publishing the new
CPU state or retiring old assets. Atlas recovery replays accepted updates in
order; rejected uploads must not advance animation clocks or lose pending work.

DH asset preflight runs after the real quadtree selects visible generations.
Keep those selected assets resident through submission and presentation. The CPU
collector excludes their keys from replacement updates while the pending visible
list exists; the coordinator flushes again after presentation. Unrelated columns
still use the bounded publication budget, and repeated rebuilds retain only the
latest pending snapshot. Changing a generation on old segment indices is unsafe:
a replacement can change opaque, transparent and water stream topology. See
[`DistantHorizonsSemanticCollector`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/vulkanic/world/DistantHorizonsSemanticCollector.java)
and
[`RustGalFrameCoordinator`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/vulkanic/gui/RustGalFrameCoordinator.java).

Release descriptor sets and cached pass bindings before their textures,
samplers or residency buffers. Cache eviction must account for prepared
commands as well as submitted work. GUI stream reservations remain owned
through command preparation and submission; frame-local reservations must be
released when preparation fails. Shader reloads retire bindings and pipelines
only after the replacement source generation is accepted.

DH containers acquire a CPU lifetime lease atomically with semantic publication.
Identical replacements can share a generation: closing one container retires it
only when the last owner closes. Changed generations and world/resource resets
invalidate old lease groups, so late closes cannot erase replacement data.
The collector's snapshot cache targets must not retire a live container merely
because its column is absent from the current visible list; transition siblings
and parents still need readiness. Their working set follows the DH quadtree
lifetime. Check memory during large-radius streaming and repeated transitions;
the cache targets are not a hard cap on live geometry. These leases own CPU
lifetime only; Rust and the GAL retain all native resource ownership.

GUI item-target eviction uses every item identity and extent in the ordered
frame, before recording individual items. Evicting against one item at a time
discards cached pixels needed by later items and causes repeated rasterization.
Keep accepted static rasters across frames; identity, extent and asset-generation
changes invalidate them. Pending command uses still prevent eviction.

Direct DH composition tracks snapshot image usage separately from copied
contents. Its early resolver establishes `ShaderRead` even when far fade is
off and no vanilla pixels are copied. Later vanilla fade snapshots must
transition from that same-frame state. Confirm persistent usage only after
accepted submission; resource replacement and reset discard it. See
[direct recording](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/worldrender/vanilla/recording.rs).

## Shader controls at startup

Shader selection, pack options and key bindings remain Java-owned configuration.
`Minecraft` initializes that configuration before constructing `Options`, which
must include registered keys before loading saved mappings. Client ticks process
the shader controls; changes request a fresh source snapshot through
`RustGalFrameCoordinator`. Rust still owns shader execution and GPU resources.
Do not remove these configuration hooks when removing Java renderer lifecycle
code. The integrated distribution also needs a version-label fallback when no
separate Iris mod container exists.

World entry and extent changes prepare source prerequisites through a temporary
Rust-owned offscreen GAL target. Its normal graph submission initializes depth
history, lightmaps and voxel resources without writing or presenting the acquired
image. Run that graph even when terrain and LOD streams are still empty: depth
snapshots and initialized shadow attachments are admission prerequisites for
sky/hand-only entry frames too. An empty draw list still executes the graph's
attachment clears, depth copies and forward/composite work.
If source admission rejects contracts or assets and produces no resource
snapshot, keep the source unarmed and retain that rejection. A missing snapshot
cannot participate in depth merging or final-target correlation; treating it
as an admitted transaction can turn an unsupported pack into a world-entry crash.
Admission then rebuilds the exact-frame snapshot for the acquired target;
the selected source graph writes the visible world and GUI through the existing
frame owner. Preparation retires its cached bindings and attachments on success
or failure. Source discovery also covers world frames before terrain appears.
The eager Java atlas publisher stages available normal/specular maps alongside
the base atlas, rather than waiting for a terrain section build. Admission must
include the complete source graph's declared color outputs: their bootstrap and
writers provide them within the selected submission. Live entry validation is
still required; this preparation does not make unsupported frame families
admissible. See
[`source/submit.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/worldrender/source/submit.rs).

The selected `gbuffers_textured` material writer applies the resolved Iris
alpha test to its primary output after the pack fragment runs. The default
threshold is 0.1; `alphaTest.gbuffers_textured` can override or disable it.
Opaque particle blending still requires this discard: writing alpha zero does
not preserve the scene or its depth. Keep this source policy in
[`contracts/material.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/shaderpack/contracts/material.rs)
and the prepared shader, outside the game-neutral GAL.

Single-color opaque/translucent terrain, textured material, entity and hand sources retain their
declared pack color slot and original GLSL lighting. Discovery must not require
Complementary helper names or invent auxiliary targets for those programs.
Ordinary terrain resolves `alphaTest.gbuffers_terrain` with its selected stage's
effective defines. Without an override, opaque terrain has no alpha test and
cutout terrain requires primary output alpha greater than 0.1, matching Frozen's
Sodium terrain passes. An explicit disabled or `GREATER x` override applies to
both material classes. The prepared fragment wraps the actual `main` definition,
so early returns still reach the test. Definition parsing preserves signature
comments and spacing and skips prototypes and commented functions; DH depth
insertion uses the same brace-aware parser.
See [`terrain/alpha_test.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/shaderpack/contracts/terrain/alpha_test.rs)
and [`lowering/text.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/shaderpack/lowering/text.rs).
Translucent terrain remains a separate source pass with source-alpha-over
blending. Its selected property branches resolve with that stage's effective
defines. The default alpha test is disabled, matching Frozen's ordinary
translucent terrain; `GREATER x` applies to output zero after the pack runs.
Keep explicit disabled alpha state distinct from a missing raster contract.
The paired lowerer links legacy `varying` declarations to explicit locations,
ignoring declaration comments and reserving one location per matrix column;
scalar uniforms, samplers and the native compiler still gate preparation.
Uniform/sampler discovery and rewriting use the same comment-free lexical view,
so annotations cannot hide an active binding or make a declaration count as a use.
The compact textured stream can remove unused, known terrain-attribute
declarations from shared headers. Actual reads of those attributes still need
an explicit semantic input; substituting zero would change the pack's behavior.
The compact material contract supplies `mc_Entity=(0,0,0,1)`, matching the
disabled generic attribute observed on Frozen's particle draws. This is a fixed
source input, with no new vertex lane or borrowed GL state. Mid-UV, tangent and
mid-block reads remain unsupported; entity/hand/terrain streams keep their own
per-vertex entity semantics.
See [source lowering](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/render/shaderpack/lowering).

The selected frame orders optional `begin` writers before shadows, then
`prepare` before sky and terrain. These are ordinary Rust-owned fullscreen
passes in the same named-color transaction. Their enabled declarations,
resource bindings, feedback and mip requirements participate in admission;
missing paired sources or lowering failures reject the complete route.
DH frames use separately expanded pre-terrain programs. Before begin, the color
transaction clears every new target and both sides of each clear-enabled warm
target, matching Frozen. Targets declared `clear=false` retain their confirmed
history. The generation owns cached GAL clear passes; warm frames allocate no
clear resources. Fog values remain per-frame inputs and explicit source colors
take precedence. Clears invalidate mip descendants on the affected side, so
sampling them still requires explicit mip generation. Geometry and fullscreen
writers load colors initialized or written earlier this frame. Main depth still
clears. Preserving only slot zero loses packs that write sky elsewhere.
Record opaque outputs immediately
after their pass, before deferred stages snapshot same-frame feedback.
See [fullscreen discovery](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/shaderpack/contracts/fullscreen.rs)
and [frame sequencing](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/worldrender/source/plans/terrain.rs).
See [color clears](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/shaderpack/resources/color_targets/clears.rs)
for generation ownership and base-mip transitions. Rejected replacements cannot
advance confirmed history; clear passes retire before their attachment views.

Begin/prepare/deferred/composite/final stages expose unit-quad local positions through the
owned procedural triangle. Their legacy model-view is identity; the projection
maps XY from `[0,1]` to `[-1,1]` and has a zero Z column, matching Frozen's
composite transformer and captured native vertex shader. Local positions do
not inherit the Vulkan sampler-UV row conversion. Normal, color, texture
matrices and unused texture-coordinate sets have explicit composite values;
these constants neither consume world-camera uniforms nor borrow GL state.
The Overworld sky source runs first on an owned octagonal horizon with tiled
Y=±16 planes, then on the expanded vanilla Y=16 disc. The horizon radius is
effective render distance in blocks capped at 256; its vertex color is copied
fog color with alpha 1. The disc receives sky color. Keep these as separate
consumers so self-sampling packs snapshot feedback between draws, and retain
distinct cached pipeline identities for their geometry and uniform contracts.
The horizon remains active when fog hides the disc; the pack's `sky=false`
or `sky=0` directive disables both while leaving celestial discovery separate.
See [horizon geometry](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/shaderpack/lowering/horizon.rs)
for the owned prism and plane construction.
Sun/Moon `gl_Vertex` retains the local unit XZ quad; translation, scale and
Y/X/Z rotations belong to its owned model-view, including fragment-stage
matrix reads. Rotate with the copied vanilla sky clock, independently of
Iris's quarter-shifted `sunAngle`. End sky faces retain their baked local
rotations and camera-only model-view. These inputs use the existing semantic
frame and std140 writer, without Java GPU state. See
[captured input checks](RENDERDOC-INPUTS.md).

Unlit sky and celestial vertex formats expose `(240,240,0,1)` for both legacy
lightmap coordinate sets 1 and 2, matching Frozen's Iris transformer. Both
texture-matrix slots use the canonical lightmap transform; slot 0 retains the
primary texture transform. Lower slot 2 as an alias of the existing owned
lightmap matrix, keeping the two-matrix uniform block and ABI unchanged.

The vanilla lower sky disc carries the `sky_dark_disc` material role and local
bottom-fan vertices. Its Rust shader applies the camera-relative draw transform
and sky fog; a generic black textured material omits that fog and leaves black
regions between cave surfaces. The role has its own fog header and pipeline key;
ordinary material and compact DH-box record offsets stay unchanged. Rebuild
Java and native Rust together when changing this semantic role. See
[the sky material shader](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/worldrender/vanilla/glsl/sky_material_fragment.glsl).

Sky and celestial fragments derive lower-left screen coordinates from
`gl_FragCoord`; their color/depth target samples use the world source row
conversion, while copied PNGs retain their authored addressing. Composite
varyings already address native target rows. Convert those varyings back to
source screen coordinates only at inverse projection; use the declared `vec2`
interface rather than assuming a spelling such as `texCoord`. Reconstruction
requires this conversion even when the fragment never reads `gl_FragCoord`.
See [coordinate lowering](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/shaderpack/lowering/fullscreen_coordinates.rs).

Each writer selects its own color sampler bindings from the owned targets.
Program-local descriptor ordinals can give opaque, translucent, entity, hand,
textured-material and DH writers different combined handles for the same role.
Replace only the local writer's color subset in its cloned admission table;
retain the original snapshot and all non-color ownership checks.
Fullscreen stages additionally choose feedback and mip policy. A geometry
snapshot cannot choose those stages' sampled images. Validate snapshot
generations before excluding the stage-owned color
subset. All other duplicate roles still require exact binding equality.
An absent `DRAWBUFFERS` on `final.fsh` selects its single displayed color via
the owned primary target and normal final-copy path. This default does not
apply to deferred/geometry stages, malformed directives or extra outputs.

Custom `uniform.*` and `variable.*` properties travel with the preprocessed
stage's selected options and source fingerprint. Rust links the active GLSL
uniforms and their dependency closure into a typed expression program, reads
the copied frame semantics and writes the existing std140 block. This supports
scalar arithmetic, comparisons, lazy `if`, selected numeric functions and
`vec2`/`vec3`/`vec4` construction; unsupported active functions remain an admission
failure. It does not borrow Iris evaluator objects or accept an untyped payload.
Keep Frozen's overload order: exact signatures, a final int-to-float cast, then
casts inside arguments. Float-to-int requires `toInt`; `floor`/`ceil` can return
either type. Bound property bytes, parsing, resolution work and graph depth;
reject cycles, missing frame inputs and non-finite results. See
[expression evaluation](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/render/shaderpack/uniforms/source/expressions).

Built-in celestial positions and `eyeBrightnessSmooth` are derived in Rust from
copied view, sky-time, End-flash and packed-light semantics. Resolve
`sunPathRotation`, `eyeBrightnessHalflife` and `endFlashShadows` from the selected
dimension program set; when an override directory exists, Frozen does not merge
missing base programs into its directives. Cache only the four immutable scope
policies per source generation. Expanded stages remain temporary and bounded.
See [frame directives](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/shaderpack/properties/frame_uniforms.rs).

Update brightness smoothing once per frame, reset it on world/pack generation
changes, and retain both packed-light channels. The derived frame memo must
include pack generation, sky scope, viewport and projection as well as world,
frame, time and view; same-frame reloads or resize preparation must recompute it.
Activation of the shader environment also invalidates that memo. Entity and
hand writers use the derived frame whenever gameplay shader semantics are
enabled, even if the source omits `atlasSize` or render-stage inputs. Keep the
raw-frame path only for disabled fixtures that need no owned derived values.
These values use the existing typed std140 writer and expression inputs, without
borrowing Iris temporal objects. See [frame derivation](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/worldrender/source/uniforms.rs).

Conventional packs without a MattMC binding manifest use a bounded Rust table of
supported Iris sampler aliases. Geometry samples color targets from slot four;
fullscreen `tex` refers to the first scene color. Resolve conditional custom PNGs
from the selected stage's final defines, keeping sampled assets separate from
color output identities. An explicit manifest remains authoritative. See
[protocol bindings](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/shaderpack/resources/bindings/legacy.rs).
The normal pre- and post-terrain chains participate in the asset/admission requirement
closure, alongside terrain and shadow plans. Copied PNG residency uses semantic
role/path identities: the same sampler name in different stages may select
different images. Resolve property branches before checking shared declaration
duplicates; pass-local PNG paths remain authoritative in their lowered bindings.
Raw primary and opaque-only shadow depth remain distinct roles. A late writer
adding raw-depth requirements must rebuild cached wrappers; failure retains the
accepted resources and retires partial creations. Shadow programs may declare
only primary color; do not invent a second output. Legacy combined matrices use
the current draw family's transforms, and fog coordinates retain pack writes
through an explicit interpolated float with Frozen's zero initialization.

Color targets and shadow camera directives use the same bounded, selected
fragment-program traversal as built-in uniforms. Apply declarations in Frozen
ProgramSet order, retaining the last accepted value and protocol defaults for
absent directives. An empty dimension override must not import base libraries.
Compact transported snapshots retain their explicit metadata path; missing
executable includes still prevent admission. See
[directive traversal](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/shaderpack/properties/directives.rs).

Keep each dimension's shadow resolution, matrices, caster properties and cutout
rule together. Cache four immutable shadow policies per source generation;
attachment compatibility includes the selected resolution, and cutout pipeline
identity includes the threshold. Invalid policies reject before attachment
replacement. Perspective shadows remain a camera-policy rejection. The bridge copies
the user's shadow distance in chunks alongside the normal render distance in
blocks (`far_plane`). Rust derives distance-only, advanced and safe-zone terrain
culling from those inputs and the selected pack multipliers, preserving Frozen's
different equality rules and negative sentinels. Camera uniforms remain independent
of caster selection. ABI 68 adds immutable renderer culling bounds, camera origin,
leash-holder bounds, eligibility and player-only extraction roles. Rust selects
entity casters with the pack's entity multiplier, while block-entity directives
remain independent. Java copies bounded off-camera candidates without reading pack
caster flags. Entity distance boxes use Frozen's absolute float casts; advanced
planes subtract the double camera first. Safe-zone entity visibility retains
Frozen's world-AABB behavior, separately from terrain's Sodium entry point.
Model geometry bounds cannot replace gameplay culling bounds. Shadow-only orbs
retain their role through Rust geometry creation; other unported shadow streams
are removed from camera queues and reported as missing ownership during admission.
This transport does not establish broad entity/shadow gameplay parity.
See [scoped shadows](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/shaderpack/properties/shadow/scoped.rs)
and [color declarations](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/shaderpack/resources/color_targets/scoped.rs).
The user-zero shadow-pass lifecycle, default culling with geometry shaders, and
DH-enabled shadow projection selection still need their own admission and runtime
verification; the distance calculator alone does not establish those behaviors.

## Troubleshooting

- **Shader Packs is missing from Video Settings.** Check that the CPU shader
  configuration initialized before `Options`; the menu hides that page when
  `Iris.getIrisConfig()` is null.

- **A boundary test fails after a change.** Its message names the file, line
  and rule. Move the code to the layer allowed to know about it, or expose what
  you need through that layer's public API. Don't add exceptions to the test.
- **"backend identity must not be exposed".** You compared against a backend
  name or added a backend-identifying capability field; add a feature flag or
  limit instead.

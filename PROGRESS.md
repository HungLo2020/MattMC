# Rendering migration handoff

## Active objective

**Goal 3 — Iris shader packs on Rust Vulkan.** Run the selected non-DH pack
through one Rust-owned Vulkan frame and compare against Frozen Java OpenGL.
Goal 4 (DH+Iris) has not begun. Do not edit `docs/RUST-MIGRATION-PROMPTS.md`,
commit, push, or change Frozen source/behavior. Keep this document under 200
lines. Java supplies bounded immutable render semantics and copied pack/config
source; Rust owns preprocessing, lowering, resources, passes, synchronization,
submission, and presentation. Preserve shader-off vanilla and DH.

## Current gate (2026-09-25)

The canonical pair uses `Origin`, 1280x720, camera
`150.5,100,530.5,105,10`, render/simulation distance 4, DH disabled, and one
two-mode `DevUtils/Audit/Capture.py` invocation with Current Rust Vulkan
shader-on, Frozen OpenGL shader-on, and `--rust-selected-source-execution`.
The selected ZIP is `run/shaderpacks/ComplementaryHungLoIfied.zip`, SHA-256
`cb4343913a0d...`. Mask transient chat at x<1000,y=570..609 for RGB MAE.
The harness now stages a byte-identical pack after each client's Gradle
`runClient` ZIP copy and verifies its SHA-256 receipt before treating the pair
as valid. Previous pairs without that receipt are not renderer parity evidence.
No Frozen source was edited. For temporary controls, set
`MATTMC_CAPTURE_SHADER_PACK_SOURCE` to a diagnostic ZIP outside the repo.

- Frozen disables face culling for shadow terrain; Rust source shadow
  pipelines use no culling. Raw shadow maps and ray-projected depth agree; do
  not add a Rust shadow-sampling/depth offset.

Compact paired measurements and retained images:
`artifacts/graphics-captures/goal3-source-2026-09-25/` (`paired-diagnostics.json`,
`mipfix-and-night/summary.json`). Heavy isolated runs are pruned.

## Validation and history

Shader-off vanilla and DH `SINGLE_PASS` captures completed with loaded
terrain/LOD. Rain lightmap residency crash fixed (the source frame's
confirm/discard boundary owns the lightmap transaction). Cloud route: celestial
quads draw only via the pre-terrain sky writer; color targets are staged before
route selection; a successful submission keeps the route armed.
**Mip semantics (2026-09-25).** Iris applies a mipmapped min filter only for
programs declaring `colortexNMipmapEnabled`; everyone else sees mip 0. Rust
bound non-declaring readers to the full chain, so composite7 FXAA read stale
mips (cloud speckle). Non-mipmapped bindings now sample the base-mip view. The
harness passes `advanceFrameCounter=true` so both clients advance Iris's
counter. Overhead clouds 7.765 -> **1.547/1.607/2.284**; day **2.792/2.564/2.877**.
**Night/rain gap: deferred by the user (2026-09-25) — noted, ignore for now,
keep working; not reclassified as a Frozen bug.** Night 5.465/6.781/10.829,
rain 8.137/9.337/8.826: Current's night volumetric fog is brighter/bluer. With
composite's ray dither pinned to any constant both clients pass (~0.8/1.0/1.6);
with the pack's per-pixel spatial dither Frozen's upper-frame mean B drops to
41.7, outside every constant-dither result (Current 54.4), so Frozen's value
depends on neighboring pixels. Ruled out on both: noise input, globals,
vlFactor, TAA, loop exits, explicit-LOD lookups. Frozen: NVIDIA RTX 3080 Ti,
driver 595.91.07. Do not add a Current workaround.
**Iris hand order (2026-09-25).** Non-DH Depth32 hands: depthtex2 snapshot ->
hand pass -> hand depth into main depth -> depthtex1 -> deferred ->
translucents (DH/D24S8 keep legacy order); validation **0 errors**.
Entities: Iris disables vanilla entity-shadow decals when the pack owns a
shadow pass; selected-source textured batches now omit them (cow pair 5.37 ->
4.82 MAE). Shadow caster directives (`shadowEntities/Player/BlockEntities`,
Iris defaults true/false/true) now resolve through option-aware
`shaders.properties` preprocessing (shared `resolved_source_properties`);
Complementary resolves entities=false, player=true, blockEntities=false.
Player shadow caster: see the 2026-09-26 entry below.
**Shadow map orientation + casters (2026-09-25).** Source shadow pipelines use
`RasterYDirection::Down` (shadow consumers are matrix-addressed; a `final`
`texCoord` readback hides this flip, use `gl_FragCoord` in a world stage).
Java sweeps the whole render window for off-camera casters (bounded 384
builds/256 columns per frame). Layered overlays fold VIEW_OFFSET_Z_LAYERING into
the instance transform (`view_layering::apply_to_model`). Look-down residual
is timing confounds: composite dither=0.5 plus `WAVING_SPEED 0` passes
(**4.955/4.519/3.449**).
Other gaps: glint unblended/rejected; energy swirl unadmittable; crack drawn
post-`final`; no Iris program fallback chains;
`texture2D(sampler2DShadow, vec2)` accepted by NVIDIA GL but rejected here;
the translucent contract requires exact source expressions (e.g. the colortex3
`gl_FragData[1]` line), so edited water sources are unadmitted.
Always stage the canonical pack via `MATTMC_CAPTURE_SHADER_PACK_SOURCE`.

**Entity shadow casters (2026-09-26).** Iris's shadow pass draws the local
player even in first person. Java now extracts the player (and vehicle) only
while a Rust shader-pack source is ready, submits it semantically inside a
shadow-only capture scope, and re-tags the records as stratum 69
(`WORLD_STRATUM_ENTITY_SHADOW_CASTER`: no flags/foil/outline/ordering). Rust
admits them only to the shadow pass per the pack's caster directives, lowers
the pack `shadow` stage for the entity stream
(`lower_entity_shadow_source_pair`, cull none, raster Down, depth write), and
records them after terrain casters. Camera writers, coverage and model
diagnostics ignore stratum 69. `shadow2DLod` is lowered. Fixes found on the
way: frame coverage rejected stratum 69 (route never armed); per-batch render-
stage lookups re-parsed every pack file (`runtime_semantic_defines` is now
memoized per immutable source) and each terrain mesh was converted 4x per
frame (now a per-plan memo cleared after preparation) — plan prep 330 -> 20 ms.
Evidence: paired shadow-map readback shows the player caster on both sides;
day **2.405/2.129/2.508**, look-down **9.062/9.383/8.574** (known confounds),
cow scene **2.352/2.015/2.544**; validation **0 errors**; Rust suite **1851**.
The cow scenario's `missing_semantic_entity_identity` evidence failure predates
this (also shader-off) and is a harness identity-matching issue.
**Block outline via `gbuffers_line` (2026-09-26).** Frozen draws the selection
box with the pack's `gbuffers_line` into colortex0/6 (after the opaque flush,
before `beginTranslucents`; translucent-layer targets after translucent
terrain); Current drew it after `final`, vertically mirrored. New
`line_contract` (DRAWBUFFERS [0]/[0,6]; pack must expand segments itself)
lowers Iris-injected `vaPosition`/`vaNormal`/`modelViewMatrix`/
`projectionMatrix` onto the material stream and exposes the stored vertex id
for `gl_VertexID` parity. Segments are staged as vanilla `Mode.LINES` quads
(s,s,e,e; direction normal; `VIEW_OFFSET_Z_LAYERING` folded in), vanilla
TRANSLUCENT blend, per-segment depth, own `Lines` pass phase; Java tags
translucent-layer targets (style flag 0x100). Frames with outlines and no
lowerable line program stay unadmitted; the post-final overlay only runs when
no source line writer consumed the segments. Ground pose 146.5,66,530.5,105,60
(Frozen cannot aim): outline crop MAE 1.11/2.12/1.36 -> **0.42/0.70/0.51**,
full frame 2.53/3.66/1.45; receipt `source_line_execution` = `line_source_gen2`,
12 segments; validation **0 errors**; suite **1853**. Translucent-target path
untested e2e: posing at the stained glass (151.5,78,553.5,270,0) aborts the
route on a Sodium DYNAMIC-sort translucent section (fixed below); with that
fix the translucent-phase outline draws after translucent terrain (receipt
`translucent_draws: 1`) and is occluded by the glass like Frozen's.
**Translucent/water fixes (2026-09-26).** (1) Camera-sorted (DYNAMIC) runs
map to one translucent section sub-range (`index_subrange`) instead of
aborting. (2) Source translucent terrain now writes depth (vanilla
TRANSLUCENT does; red-line diagnostic showed Frozen's glass occluding back
outline edges), so depthtex0 carries water/glass. (3) Discarded source frames
re-queue first-use geometry uploads (`geometry_uploads` on the transaction).
(4) Sorted-index updates retire cached source geometry. (5) Source terrain
uses Iris XHFP semantics: diagonal face normal for all 4 vertices, tangent
from triangle (0,1,2) retrying (2,3,0) — a flipped tangent frame drew a large
dark triangle over water. (6) World-target lowering: world stages flip every
source-space target access once (texture, textureLod, integer texelFetch, and
`sampler2D` function parameters whose call sites all pass targets), skip
pack-bound images (`texture.gbuffers.gaux4` is a PNG, was sampled mirrored),
now also for textured/weather/cloud/line fragments; `viewHeight` is added to
the contract when a world fragment reads gl_FragCoord. Complementary's SSR
passes `depthtex1` as a parameter: Current reflected mirrored underwater kelp.
Water pose 152.5,66,499.5,180,25: 16.6 -> 9.74/10.78/9.20; shader-off 0.60.
**Colored light + shadowtex1 (2026-09-26).** `IRIS_FEATURE_<X>` is defined
for pack-requested `iris.features.optional` flags Rust supports
(`CUSTOM_IMAGES`), so the floodfill block light compiles in. `shadowtex1` is
now Iris's pre-translucent shadow depth copy (split shadow pass, own texture),
not an alias of shadowtex0: underwater receivers take the caustic path.
No-water control 5.82/9.54/9.98 -> **4.65/6.40/7.18**; day
**2.357/2.097/2.502**, glass 7.38/6.47/4.85, look-down 9.06 -> **7.18/7.71/7.11**;
suite **1855**. Water pair now 10.79/13.10/10.62: Current's water surface is
brighter. **Suspected Frozen behavior — awaiting user decision:** isolated
pre-color, SSR/sky reflection and fresnel all match at 1/4 scale, but on Frozen
the `color` read in gbuffers_water `main` just before the reflection mix is
0.77x its value one statement earlier. That happens only when GetReflection's
arithmetic-only Step 3 (GetSky/GGX, no texture reads/discard/writes to `main`
locals) is compiled in; renaming parameters or using a temporary for the
swizzled `inout` changes nothing. Rust keeps `color` unchanged. Deterministic
across repeats. Diagnostic ZIPs in the session scratchpad.
**Shader menu on Vulkan (2026-09-26).** The Sodium "Shader Packs" page was
gated off for Rust Vulkan (Goal 1 leftover); now shown. On Vulkan `Iris.reload`
parses a CPU-only menu pack (`Iris.getMenuPack`, engine-only env defines,
FeatureFlags report Rust features, no GPU queries), persists `<pack>.txt`, and
asks the coordinator to restage (also on disable/option-only changes). Rust
applies saved options like Iris: boolean false suppresses the pack's `#define`,
`const` options rewrite constants, env/profile defaults yield to saved options.
Shader-on day 2.394/2.108/2.504, shader-off pair 0.041/0.047/0.039; suite 1856.
Follow-ups: `IrisDefines` skips GL macros on Vulkan (Apply crashed); GUI
scissors clip to the frame at the `ScissorStack` root (DH settings crashed with
`tile clip must be frame-local`); verified by scripted Vulkan smoke, crash
reproduced without the clamp, video menu pixel-identical. **Shader execution follows config (2026-09-26):** Rust/Java run the
selected pack whenever Iris config enables one; `MATTMC_RUST_SELECTED_SOURCE_EXECUTION`
is only an override (1 on, else off). Frame mesh-instance bound 4096 -> 65536
(FFI bound; per-batch GPU stream stays 4096). stderr `[MattMC shaders] shader
route active|vanilla fallback: <reason>` on change. **Perf at RD 10 (user
config):** was 0-1 fps (per-frame source mesh re-conversion + per-draw index
validation); fixed with validated-mesh set, 256 MB LRU conversion cache (slim,
byte-free once GPU geometry resident), validate-once, per-frame uniform memo.
Now 3-4 fps vs ~60 shader-off: ~4,000 per-section draws/19,600 ops per frame;
needs region batching/persistent draw plans (open). F2 screenshot throws on
Vulkan (Goal 1 gap). No crash/RSS runaway in 150 s. Resize: armed route now
disarms on viewport change and re-arms (5 resizes x3 runs clean); built-in
G-buffer draws without a shadow pipeline no longer demand one, and standard
item foil (1-target pipeline) draws in the G-buffer translucent phase. OPEN
(Goal 1): Vulkan clouds crash at cloudRange >~16 chunks (worst-case face
preflight vs 65,536 material quads) — vanilla default 128 crashes.

Frozen `IrisRenderingPipeline.beginTranslucents()` runs deferred fullscreen
stages after opaque depth copy and before translucent world writers. Rust's
shader-only source plan now inserts deferred stages at that matching boundary.
The combined DH route retains its old ordering until Goal 4 can split its
opaque/translucent writers. Recheck shader-off vanilla/DH after any shared
resource or scheduling change. Do not claim Goal 3 complete until its remaining
source coverage and clean Vulkan run are verified.

## Retained architecture

Rust separates shadow-only terrain candidates from camera-color draws. The
bounded one-section CPU halo and source-derived advanced shadow frustum admit
off-camera casters only to the shadow pass. Candidate admission applies
Sodium's 64-block cylinder. The shadow target uses the pack's 2048-square
extent and Iris's opaque-white shadow-color clear. Keep one Rust-owned
frame/presenter, immutable asset validation, indirect terrain submission,
source queue settling, and static fragment specialization intact.
Goal 1 vanilla and Goal 2 DH remain regression baselines. No commit or push.

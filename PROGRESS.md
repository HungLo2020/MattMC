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
Retained pairs: `artifacts/graphics-captures/goal3-source-2026-09-25/`.
The harness stages a byte-identical pack per client and verifies its SHA-256
receipt (pairs without it are not evidence). Diagnostic ZIPs go through
`MATTMC_CAPTURE_SHADER_PACK_SOURCE` (outside the repo).

- Shadow terrain draws unculled on both; shadow maps and ray-projected depth
  agree, so add no Rust shadow-sampling/depth offset.

## Validation and history

Rain lightmap residency owned by the source frame's confirm/discard boundary;
celestial quads draw only via the pre-terrain sky writer.
**Mip semantics (2026-09-25).** Only programs declaring
`colortexNMipmapEnabled` sample mips (others see the base-mip view); harness
passes `advanceFrameCounter=true`. Overhead clouds **1.547/1.607/2.284**.
**Night/rain gap: deferred by the user (2026-09-25) — noted, ignore for now,
keep working; not reclassified as a Frozen bug.** Night 5.465/6.781/10.829,
rain 8.137/9.337/8.826: night volumetric fog brighter/bluer; passes with any
constant ray dither, Frozen's spatial-dither result depends on neighbouring
pixels. Do not add a Current workaround.
**Iris hand order (2026-09-25).** Non-DH Depth32 hands: depthtex2 snapshot ->
hand pass -> hand depth into main depth -> depthtex1 -> deferred ->
translucents (DH/D24S8 keep legacy order); validation **0 errors**.
Vanilla entity-shadow decals omitted when the pack owns a shadow pass; caster
directives resolve option-aware (Complementary: entities=false, player=true).
**Shadow map orientation + casters (2026-09-25).** Source shadow pipelines use
`RasterYDirection::Down` (shadow consumers are matrix-addressed; a `final`
`texCoord` readback hides this flip, use `gl_FragCoord` in a world stage).
Java sweeps the whole render window for off-camera casters (bounded 384
builds/256 columns per frame). Layered overlays fold VIEW_OFFSET_Z_LAYERING into
the instance transform (`view_layering::apply_to_model`). Look-down residual
is timing confounds: composite dither=0.5 plus `WAVING_SPEED 0` passes
(**4.955/4.519/3.449**).
Other gaps: energy swirl unadmittable; no Iris program fallback chains;
`texture2D(sampler2DShadow, vec2)` accepted by NVIDIA GL but rejected here;
the translucent contract requires exact source expressions (e.g. the colortex3
`gl_FragData[1]` line), so edited water sources are unadmitted.
Always stage the canonical pack via `MATTMC_CAPTURE_SHADER_PACK_SOURCE`.

**Glint via `gbuffers_armor_glint` (2026-09-27).** Iris draws every glint
(item/entity/armor, world and hand) with `ShaderKey.GLINT`: position+UV only,
`gl_Color=(1,1,1,glintStrength)`, `gl_TextureMatrix[0]`=vanilla glint matrix,
GLINT blend, EQUAL depth, no write/cull, alpha>0.0001. Glint sections now leave
the entity/hand writers (were drawn opaque: purple trident) for entity- and
hand-stream lowerings of armor_glint (`EntityGlint`/`HandGlint` load-only
passes on the entity / private hand depth; frames prepared before the stream
reservation). `invariant gl_Position` in the source vertex preamble fixed EQUAL
streaks. Held trident pair 7.25/10.49/6.45 -> **2.336/2.033/2.434**, 0 VUIDs. Also fixed:
trident in hand crashed (model-part hand identity vs item identity); colour-pass
target cache evicted other phases by depth texture (hand/entity ping-pong
recreating targets every frame). Gap: world item entities get
`currentRenderedItemId=-1` (Iris sets the item's ID; Complementary shades a
dropped diamond as gem) — dropped-foil pair 2.95/2.82/3.13. Decal (special)
foil still uses mesh UVs (vanilla projects them).
**Camera-motion smear (2026-09-27).** `camera_history` returned previous =
current for every call after the first in a frame, so composite TAA and the
deferred1 reflection filter saw no motion and blended stale history at the wrong
pixels (smear while turning; static captures hide it). Same-frame calls now
return the stored history. The `...Reprojection(vec3 pos)` helpers also convert
image-UV input once. Motion pair (`MATTMC_CAPTURE_STATIC_POSE_SEQUENCE=4,2,18`)
sharpness 135 vs 256 -> 173 vs 183; left-pose arm residual is harness timing
(Frozen captures 2 frames after a turn, Current ~10: mid-sway vs settled arm).
**Entity shadow casters (2026-09-26).** Iris shadows the first-person player:
Java extracts player/vehicle (stratum 69) while a source is ready; Rust admits
them only to the lowered entity `shadow` stage per caster directives. Cow
**2.352/2.015/2.544**; its `missing_semantic_entity_identity` is a harness issue.
**Block outline via `gbuffers_line` (2026-09-26).** Iris draws the selection
box with the pack's `gbuffers_line` into colortex0/6 after the opaque flush
(translucent-layer targets after translucent terrain). `line_contract` lowers
Iris-injected `vaPosition`/`vaNormal`/`modelViewMatrix`/`projectionMatrix` onto
the material stream (segments as vanilla `Mode.LINES` quads s,s,e,e; stored
vertex id for `gl_VertexID` parity), own `Lines` phase; the post-final overlay
runs only when no source line writer consumed the segments. Outline crop MAE
**0.42/0.70/0.51** (pose 146.5,66,530.5,105,60); translucent-phase outline
occluded by glass like Frozen's.
**Block breaking via `gbuffers_damagedblock` (2026-09-27).** Iris draws all
crumbling (block-entity crumbling buffer and `renderBlockDestroyAnimation`)
with the pack's damagedblock program after the outline, before
`beginTranslucents`, with vanilla CRUMBLING state (DST_COLOR/SRC_COLOR blend,
depth test no write, bias -1/-10, back cull) and `alphaTest.gbuffers_damagedblock`
(default 0.1). New `damaged_block_contract` + material-stream writer
(`DamagedBlock` phase); crumbling entity meshes leave the entity writer; terrain
cracks in the shader route copy vanilla's model quads with SheetedDecal UVs (Java
`extractCrumblingBlockModelMesh`), shader-off keeps the crack-quad writer. Fixed:
block-entity crumbling crashed on Vulkan (destroy-stage path read as a sprite
name; shader-off too); an armed route meeting an uncoverable frame crashed the
game — it now disarms and draws that frame with the vanilla Rust route
(`admit_armed_source_frame`). Evidence: oak-sign-breaking stage 6 pair
2.542/2.228/2.779 (plain sign 2.263/1.896/2.421; crack darkening 0.850 vs Frozen
0.858), 0 VUIDs; shader-off pair 0.18. Terrain crack has no Frozen fixture
(Frozen harness only breaks block entities; cannot edit Frozen): Current-only
check draws the multiply crack in the G-buffer. Skull-breaking fixture fails in
Current's harness (reads a skull property from stone), not rendering.
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
across repeats. Diagnostic ZIPs in the session scratchpad. **User: water gap deferred (2026-09-26).**
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
route active|vanilla fallback: <reason>` on change. **Perf at RD 10
(2026-09-26), measured on the release Rust profile `RunDev.py` uses** (dev
profile is opt-level 0 and ~10x slower; do not profile it): 13 fps -> 21 fps,
frontend 70 -> 38 ms. Fixes: one staged frame-stream write per frame instead
of 5 ops/draw (36k -> 18k GAL ops); equal per-program uniform blocks share one
stream copy; per-frame batch scope validates programs once and memoizes pack
keys; prepared source programs memoized by candidate epoch (translucent one
was rebuilt per translucent mesh); no per-frame `WorldPrimitiveFrame` clone
(audit-only); no duplicate coverage check; fast hasher in GAL hazard tracker;
geometric stream-slot growth (was ~1,500 set creations/frame). **Crash fixed:**
source geometry is ~0.9 GiB at RD 10 and hit the 512 MiB cap (release reaches
full load); now device-local with a one-shot staging copy (was host-visible),
cap 2 GiB. Still ~7,100 per-section draws/frame (shadow-only 2,200); next:
shadow-only prep, GAL submit (~11 ms), compact 128 B source vertex. Pairs
unchanged (2026-09-27, after camera-history fix): day 2.395/2.111/2.507, glass
7.50/6.45/4.57, down 7.21/7.74/7.12, shader-off 0.14/0.23/0.24; suite 1861. F2 screenshot throws (Goal 1 gap).
Resize disarms/re-arms the route; foil draws in the G-buffer translucent phase. Vulkan clouds at range 128:
crash fixed (bounds 262,144 quads, Fabulous slot growth); shader packs that
draw their own clouds drop vanilla cloud quads before validation.

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

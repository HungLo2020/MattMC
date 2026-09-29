# Rendering migration handoff

## Active objective

**Goal 4 — Iris + Distant Horizons together (started 2026-09-28).** Goal 3
(non-DH pack) notes below remain the regression baseline. Frozen repo:
`../MattMC_JavaPerfTesting/MattMC`. DH pair: `Capture.py --mode current/frozen
-shaders-on --rust-selected-source-execution --world-distant-horizons-real-world
--world-distant-horizons-opaque`, `MATTMC_CAPTURE_DH_RADIUS_OVERRIDE` (default 8).
**Fixture:** Frozen's DH reads only DB formats 1-2 (Origin's is Current's 4), so
use a shared Frozen-generated DB: Frozen `runClient -x test -PmattmcRunGameDir=
<copy> --args=--quickPlaySingleplayer=Origin` (OpenGL options, DB removed) until
it stops growing, then `MATTMC_CAPTURE_RUN_SOURCE=<dir>` (scratch `g4src`).
Iris parity fixes: `dhRenderDistance` = DH radius*16 while DH renders, else vanilla
chunks (was max); `DISTANT_HORIZONS` in the copied environment for every program
while DH renders (recollected on toggle); selected-source flag no longer forces DH
exact-material topology/provenance (opt-in `...rustGalDistantHorizons.
exactMaterialTopology`); DH programs get the vanilla `far` (was DH distance: the
pack's smoothstep(far*.5, far*.7) fade discarded all DH); DH depth cleared each
frame (loaded last frame's depth: only edges passed); fullscreen `dhProjection`
= Iris DHCompat (gbuffer fov, DH near, far (blocks+512)*sqrt2); DH voxels only via
a pack `dh_shadow` with `dhShadow.enabled`. Frame order (Frozen LevelRenderer):
sky -> DH opaque -> vanilla opaque -> ... -> deferred (beginTranslucents) -> DH
translucent -> vanilla translucent -> composites (was DH after all terrain and
deferred after water: VL-cloud discard removed all water). Pairs: r4 day
1.63/1.61/2.28; r32 land (150.5,100,530.5,0,5, shared DB) 19 -> 4.38/4.46/2.89;
0 VUIDs. Open: DH hill band ~9 levels brighter on Current (not vlFactor: forcing 1
is far brighter). Goal 3 regressions unchanged after the toggle fix. DH-off frames (0 LODs,
define removed) admit normally.
**Suspected Frozen bug (09-29; user: note it, ignore, move on).** Look-down r32 pair
(150.5,100,530.5,105,80) MAE 15/18/33: Frozen hazes near terrain. Probe pack
(scratch `diag-vl`) shows Frozen `composite` sees `textureSize(shadowcolor1)=0`
with DH (reads (0,0,0,1) -> SALS heights 15 -> vlFactor pinned 1 -> DH VL not
suppressed); `final` sees the real 2048^2 map. Without DH, Frozen composite
instead sees `shadowtex0` 0x0. Current binds both correctly (vlFactor 0).
**DH toggle crash fixed (09-29):** pack recollection replaced the voxel volume
while cached set-ones bound it; set-ones keyed on ColoredVoxel*/Puddle roles are
released first. **Memory:** r32 pair Frozen 8.7 vs Current 8.8 GB at 98 s.
**r32 land pairs:** night 0.66/0.83/0.61, rain 1.99/2.00/2.05, 0 VUIDs.
**DH generic objects (09-29, user crash):** harness disables them unless
`MATTMC_CAPTURE_DH_GENERIC=true`. Iris: `dh_generic`->`dh_terrain`, alpha blend.
Rust: boxes (+DH material in flags bits 8-15) expand to the DH source stream,
grouped by sub-block fraction, drawn with `dh_terrain` (blend variant sharing base
layouts). Unadmitted: generic material quads / no LODs; warmup frame omits them.
Pairs generic on: land 4.88/4.95/3.09, up 2.71/2.86/2.76 (2241 boxes), 0 VUIDs. Real
config (`run/` copy at `~/.cache/mattmc-claude/realrun`): route active, no crash.
No prompt-doc edits, commits, pushes, or Frozen changes; <=200 lines. Java: semantics.

## Current gate (2026-09-25)

The canonical pair uses `Origin`, 1280x720, camera
`150.5,100,530.5,105,10`, render/simulation distance 4, DH disabled, and one
two-mode `DevUtils/Audit/Capture.py` invocation with Current Rust Vulkan
shader-on, Frozen OpenGL shader-on, and `--rust-selected-source-execution`.
The selected ZIP is `run/shaderpacks/ComplementaryHungLoIfied.zip`, SHA-256
`cb4343913a0d...`. Mask transient chat at x<1000,y=570..609 for RGB MAE.
Retained pairs: `artifacts/graphics-captures/goal3-source-2026-09-25/`.
The harness stages a byte-identical pack per client and verifies its SHA-256
receipt (pairs without it are not evidence). Always stage packs (canonical or
diagnostic) via `MATTMC_CAPTURE_SHADER_PACK_SOURCE` (outside the repo).

- Shadow terrain unculled on both; shadow/ray depth agree: add no depth offset.

## Validation and history

Celestial quads: pre-terrain sky writer only. Only programs declaring
`colortexNMipmapEnabled` sample mips.
**Night/rain gap: deferred by the user (2026-09-25); not a Frozen bug.** Night
5.47/6.78/10.83, rain 8.14/9.34/8.83 (night fog brighter/bluer; dither).
**Iris hand order (09-25).** Non-DH Depth32 hands: depthtex2 snapshot -> hand
pass -> hand depth into main depth -> depthtex1 -> deferred -> translucents.
Entity-shadow decals omitted when the pack owns a shadow pass.
**Shadow map orientation + casters (2026-09-25).** Source shadow pipelines use
`RasterYDirection::Down` (use `gl_FragCoord` in a world stage to check it). Java
sweeps the render window for off-camera casters (384 builds/256 columns per
frame). Layered overlays fold VIEW_OFFSET_Z_LAYERING into the instance transform.
Look-down residual is timing (dither 0.5 + `WAVING_SPEED 0`: 4.96/4.52/3.45). Gaps:
energy swirl unadmitted (Iris: gbuffers_entities, ENTITIES_CUTOUT, pipeline's
additive blend + texture matrix); no Iris program fallback chains;
`texture2D(sampler2DShadow, vec2)` rejected; edited water sources unadmitted.

**Glint (09-27):** Iris draws every glint with `ShaderKey.GLINT` (position+UV,
`gl_Color=(1,1,1,glintStrength)`, glint texture matrix, GLINT blend, EQUAL depth,
no write/cull, alpha>0.0001) via `EntityGlint`/`HandGlint` load-only passes;
`invariant gl_Position` fixed EQUAL streaks (trident 7.25 -> 2.34). Decal foil
glint uses per-pose SheetedDecal UVs. Entity/hand alpha tests run on output 0
after `main`, as Iris appends them (clock 3.50 -> 2.95). Harness: chicken/horse
differ on Frozen (mob pose); the spawner evidence rule never matches.
**Camera-motion smear (09-27):** same-frame `camera_history` calls returned
previous = current, so TAA/reflection filters saw no motion; fixed (motion pair
sharpness 135 vs 256 -> 173 vs 183; arm residual is harness timing).
**Held items / hand passes (2026-09-27).** Crash on hotbar switch fixed:
`currentRenderedItemId` now resolves from the drawn mesh (vanilla draws the old
stack during the equip animation), as Iris does. Block items resolve the pack's
block material of the default state (`mattmc/runtime-block-items.properties`,
0 when unmatched); `heldItemId` keeps item.properties. Iris `isHandTranslucent`
(ABI 66 `translucent_hand_mask`): that whole hand draws in a late pass after
weather, before composite, into main depth. Degenerate/sub-mm quads (TaCZ gun
faces) no longer reject the frame (scale-independent normals, second-triangle
fallback); producer-authored TaCZ hand normals were worse (2.0 -> 3.6 MAE).
Source local materials honour copied `.mcmeta` sampling (glint blur/clamp).
Gaps: gun muzzle/stock-edge shading; `gbuffers_hand_water` packs unadmitted. Gun hotbar icon fixed: it was composited under the hotbar
sprite (raw phase order, not `dynamicLayerOrder/Id`); the raster now uses
Frozen's PIP pose scale(f,f,-f)*scale(1,-1,-1) and new GUI lighting modes
6/7 (OversizedItemRenderer ITEMS_3D / ITEMS_FLAT by `usesBlockLight`).
Shader-off gun pair 0.41/0.70/0.80 -> 0.14/0.25/0.24 (one barrel-top px row
differs). TACZ GUI capture cache is skipped while its animation is active; a
selected-slot icon offset in shader-on pairs is wall-clock TACZ animation timing
(first-person poses match). World item layers carry `minecraft:item_entity/ground/<ns>/<path>`
(`ItemStackRenderState.submitSemantic`); other entity draws get 0 (Iris resets).
Gap: dropped flat foil item under clamp-glint fixture: washed on Frozen, opaque here.
**Crash robustness (2026-09-27).** A failed armed submission within 60 frames
of (re)arming disarms and redraws that frame on the vanilla Rust route; later
failures disarm and return `retryable selected-source failure` (Java resubmits
once); a missing DH depth snapshot after a world change disarms in admission.
GUI item layers are compacted, not rejected. Dimension tour: 0 crashes/VUIDs
(program identities carry a dimension tag, shadow identity a content hash;
writer-declared shadow roles are staged even if terrain samples none). Vanilla's below-horizon dark disc is sky geometry (Iris:
skybasic), skipped by the textured writer. Deferred water gap: underwater pair
152.5,60,499.5,180,10 is 131.8/152.9/168.7 (Frozen near-black water fog).
Sweeps (hotbar, block-entity/mob models): 0 fallbacks; fixed XP orbs, End portals.
**Block edits (09-28):** placed/broken blocks could stay invisible (both routes;
more often with shaders): an edited section invalidated while its build was in
flight was re-dispatched into the in-flight gate and dropped, and edits waited
behind the whole streaming backlog. Invalidations now stay parked until the
worker completes and enter the pending queue first. Place/remove stress (8
alternations right after a teleport, shaders on/off): all correct. **Iris
screen button labels:** `SmoothedFloat` fades now use wall-clock deltas (the
Iris shader timer never advances on the Rust route). Pre-existing Java failures:
`CloudSemanticAdmissionTest`/`NativeParticleCollectionTest` (stale source-text asserts).
**Perf (09-27/28).** Matrix `--profile performance --workload-profile settled-static|
moving-camera --world Origin`. Static on 31->48 fps (Frozen 205), off 166->176
(Frozen 355); moving off 296 vs 422, on 34 vs 286. Done: geometry pages+multidraw
(`MATTMC_RUST_DISABLE_SOURCE_TERRAIN_MULTIDRAW`/`_PAGES`), identity-keyed terrain
batch plans, translucent-order cell reuse, coverage memo keyed on own meshes,
memoized programs (epoch), `FullscreenPipelineCache`, primitive Java residency
maps/section asset rows. Left: entities ~4.5 ms, terrain+shadow ~3.6, GAL 2.2,
Java ~5; 100 fps needs Java/Rust pipelining or persistent recorded plans.
**Teleport/validation/voxels (2026-09-28).** Tour (6 teleports, F2, 40 s walk,
validation on): 0 VUIDs, no post-arm fallback. Fixed: F2 restores PRESENT_SRC;
colored-light voxels update every frame (sampled volumes in ShaderRead); an
uninitialized volume uploads a cleared field like Iris; full stream slots wait
for the oldest; stale pending lightmaps are discarded at frame entry and never
promoted unuploaded (injected failures 1/5 -> 0/35). Occupancy is exact-
incremental (randomized equivalence test): per-box patches, in-place shift on
cell crossings; 230 ms/frame while loading -> 0.5 ms static, walking p50 3.9/p95 20 ms. Gap: the per-asset voxel vertex cache keeps
pack material ids until the asset reloads. Pairs after the perf pass: day 2.26/1.82/2.12,
glass 6.67/5.61/4.42, down 6.83/7.54/6.60, off 0.13/0.22/0.22, gun 2.07/1.63/2.07,
pane 2.32/1.81/2.10, 0 VUIDs; Rust suite 1877. Entity/hand source normals are
Iris's in-level BufferBuilder face normal (diagonal cross, authored side; the
vertex stage flips it under a mirroring pose): cow 2.09/1.64/2.06, gun
2.11/1.66/2.09, banner 2.22/1.87/2.27. Gun gap: muzzle cap/front sight px. TaCZ
fire/reload/aim: 0 fallbacks/VUIDs; 50 ms muzzle flash shading unverified.
**Casters / outline (09-26).** Player/vehicle casters: entity `shadow` stage only.
Selection box: pack `gbuffers_line` into colortex0/6 after the opaque flush
(`line_contract` lowers Iris-injected va*/matrix inputs; LINES quads s,s,e,e;
own `Lines` phase). Outline crop MAE 0.42/0.70/0.51 (146.5,66,530.5,105,60).
**Block breaking via `gbuffers_damagedblock` (2026-09-27).** Crumbling draws
with the pack's damagedblock program after the outline, before
`beginTranslucents` (vanilla CRUMBLING state, bias -1/-10, alphaTest default
0.1); terrain cracks copy vanilla model quads with SheetedDecal UVs. An armed
route meeting an uncoverable frame disarms (`admit_armed_source_frame`).
Sign-breaking pair 2.542/2.228/2.779, 0 VUIDs (terrain cracks Current-only).
**Translucent/water fixes (2026-09-26).** DYNAMIC sorted runs map to one
`index_subrange`; source translucent terrain writes depth; discarded frames
re-queue first-use uploads; sorted-index updates retire cached geometry; Iris
XHFP normals/tangents (flipped tangent drew a dark triangle over water); world
stages flip every target access once (incl. `sampler2D` params; pack PNGs not
flipped). Water pose 152.5,66,499.5,180,25: 16.6 -> 9.74/10.78/9.20.
**Colored light + shadowtex1 (2026-09-26).** `IRIS_FEATURE_<X>` is defined
for pack-requested `iris.features.optional` flags Rust supports
(`CUSTOM_IMAGES`), so the floodfill block light compiles in. `shadowtex1` is
now Iris's pre-translucent shadow depth copy (split shadow pass, own texture),
not an alias of shadowtex0: underwater receivers take the caustic path. Water
pair 10.79/13.10/10.62 (Current's water brighter). **Suspected Frozen behavior:** isolated
pre-color, SSR/sky reflection and fresnel all match at 1/4 scale, but on Frozen
the `color` read in gbuffers_water `main` just before the reflection mix is
0.77x its value one statement earlier. That happens only when GetReflection's
arithmetic-only Step 3 (GetSky/GGX, no texture reads/discard/writes to `main`
locals) is compiled in; renaming parameters or using a temporary for the
swizzled `inout` changes nothing. Rust keeps `color` unchanged. Deterministic
across repeats. Diagnostic ZIPs in the session scratchpad. **User: water gap deferred (2026-09-26).**
**Shader menu (09-26):** works on Vulkan (`Iris.reload` parses a CPU-only menu
pack, persists `<pack>.txt`; Rust applies saved options). The selected pack runs
whenever Iris config enables one (`MATTMC_RUST_SELECTED_SOURCE_EXECUTION` only
overrides); stderr reports `[MattMC shaders] shader route active|vanilla fallback`.
Source geometry (~0.9 GiB at RD 10) is device-local with staged uploads, cap 2 GiB
(profile only the release Rust profile). F2 screenshots: Rust copies the completed
frame target before present; Java only encodes the PNG. Resize re-arms the route.

## Retained architecture

Rust separates shadow-only terrain candidates from camera-color draws; the CPU
halo and source-derived shadow frustum admit off-camera casters only to the
shadow pass (Sodium's 64-block cylinder). The shadow target uses the pack's 2048-square
extent and Iris's opaque-white shadow-color clear. Keep one Rust-owned
frame/presenter, immutable asset validation, indirect terrain submission,
source queue settling, and static fragment specialization intact.
Goal 1 vanilla and Goal 2 DH remain regression baselines (recheck shader-off
vanilla/DH after shared resource/scheduling changes). No commit or push.

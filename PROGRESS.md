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
Pairs: gun 2.38/1.96/2.53, held pane 2.35/1.82/2.11, day+stairs 2.28/1.83/2.15. Gaps: gun muzzle/stock-edge shading; `gbuffers_hand_water`
packs unadmitted. Gun hotbar icon fixed: it was composited under the hotbar
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
screen button labels:** `SmoothedFloat` GUI fades used the Iris shader timer,
which Java does not advance on the Rust route (alpha stuck at 0); the shader
screen's transitions now use wall-clock deltas. Pre-existing Java failures:
`CloudSemanticAdmissionTest`/`NativeParticleCollectionTest` (stale source-text asserts).
**Perf (RD10, 2026-09-27/28):** ~33 -> ~50 fps, frontend 23.9 -> 12.9 ms (static
camera). Source route reuses identity-keyed static/shadow batch plans (batching
4.0 -> 0.3 ms); terrain coverage validation memoized (3.3 -> 0.4 ms); source
terrain geometry lives in shared 128 MiB pages (indices rebased at upload,
completion-gated release) and is multi-drawn (1280-byte instance blocks via
`firstInstance`, per-slot indirect buffers; GAL 15k -> 4k ops). A/B:
`MATTMC_RUST_DISABLE_SOURCE_TERRAIN_MULTIDRAW` / `_PAGES`. Left: plan ~8.5 ms,
Java ~7 ms, fullscreen plan re-creation 1.3 ms. **Walking** (tour, 6 blocks/s):
frontend 45 -> 35 ms. Camera-sorted translucency reuses its topological order
while the camera stays in one cell of the sort's plane arrangement (per-axis
extent/separator breakpoints + slanted-plane sides; exact, property-tested;
Sodium re-sorts on plane crossings) plus cached batches: 5.4 -> 0.65 ms; batch
keys use the GAL's Fx-style hasher (shadow plan 4.6 -> 3.6 ms). Left while
moving: shadow-only plan misses every frame, source plan ~14 ms, voxels ~7 ms.
**Teleport/validation/voxels (2026-09-28).** Temporary tour (6 teleports, F2
shots, 40 s walk; validation on): 0 VUIDs, no fallback after arming. Fixed: F2
capture after frame finalization restores PRESENT_SRC; the named route updates
colored-light voxels every frame (were frozen after arming; sampled volumes
bracketed in ShaderRead); an uninitialized volume uploads a cleared field like
Iris (was a post-teleport vanilla flash); all stream slots in flight waits for
the oldest; a stale pending lightmap is discarded at frame entry, and the armed
route's provisional assembly (ops dropped) no longer promotes a never-uploaded
lightmap (UNDEFINED on the next vanilla fallback; injected failures 1/5 ->
0/35); lightmap changes release only pack sets keyed on `Lightmap`. Occupancy is
exact-incremental (randomized equivalence test vs full rebuild): reused mesh
snapshots, volume culling, memoized mesh list, per-box patches for same-cell
changes (one transfer scope each: GAL texture hazards are per subresource),
in-place shift on cell crossings. 230 ms/frame while chunks load -> 0.5 ms
static, walking p50 3.9/p95 20 ms. Gap: the per-asset voxel vertex cache keeps
pack material ids until the asset reloads. Final pairs: day 2.24/1.81/2.14,
glass 6.65/5.64/4.42, down 6.95/7.70/6.74, off 0.19/0.36/0.35, gun 2.13/1.68/2.10,
pane 2.34/1.82/2.10, 0 VUIDs; Rust suite 1877. Entity/hand source normals are
Iris's in-level BufferBuilder face normal (diagonal cross, authored side; the
vertex stage flips it under a mirroring pose): cow 2.09/1.64/2.06, gun
2.11/1.66/2.09, banner 2.22/1.87/2.27. Gun gap left: muzzle cap/front sight px.
TaCZ fire/reload/aim harness (validation on): 0 fallbacks/VUIDs; the 50 ms muzzle
flash (translucent textured quad) is not tick-capturable, so its shading parity
(Iris: inside the hand pass) is unverified. Springfield display JSON has `//`
comments; a strict Gson parse drops its `reload_empty` sound (data, not render).
**Casters / outline (2026-09-26).** Player/vehicle casters: entity `shadow` stage only. Iris draws the selection
box with the pack's `gbuffers_line` into colortex0/6 after the opaque flush
(translucent-layer targets after translucent terrain). `line_contract` lowers
Iris-injected `vaPosition`/`vaNormal`/`modelViewMatrix`/`projectionMatrix` onto
the material stream (segments as vanilla `Mode.LINES` quads s,s,e,e; stored
vertex id for `gl_VertexID` parity), own `Lines` phase; the post-final overlay
runs only when no source line writer consumed the segments. Outline crop MAE
**0.42/0.70/0.51** (pose 146.5,66,530.5,105,60); translucent-phase outline
occluded by glass like Frozen's.
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

Deferred stages run at Iris's `beginTranslucents` boundary (DH route keeps its
old order until Goal 4). Recheck shader-off vanilla/DH after any shared
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

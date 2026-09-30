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
0 VUIDs. **Suspected Frozen bug #2 (awaiting user):** DH hill band ~7 brighter on Current.
Diag packs (dh_terrain outputs): color/lmCoord/normal/shadow/playerPos match; Frozen's DH
`noisetex` samples ~black (not pack noise.png) so pow(noise+.5) darkens. No prompt-doc
edits, commits, pushes or Frozen changes; <=200 lines.
**Suspected Frozen bug (user: note, ignore).** Look-down r32 (150.5,100,530.5,105,80)
MAE 15/18/33: Frozen `composite` sees `textureSize(shadowcolor1)=0` with DH (no DH:
`shadowtex0` 0x0) -> vlFactor pinned 1 -> haze; `final` binds the real maps.
**DH toggle crash fixed:** voxel volume replacement releases its set-ones first.
Memory r32 pair Frozen 8.7 vs Current 8.8 GB; night 0.66/0.83/0.61, rain 1.99/2.00/2.05.
**DH generic objects:** harness disables them unless `MATTMC_CAPTURE_DH_GENERIC=
true`. Boxes (+material, flags 8-15) expand to the DH source stream, drawn by
`dh_terrain` with alpha blend like Iris. Unadmitted: generic quads / no LODs.
Generic-on pairs: land 4.88/4.95/3.09, up 2.71/2.86/2.76 (2241 boxes), 0 VUIDs.
**Shader toggle:** DH forward owners rebuild on format change; transparent/water
packed slots reset+flush in both modes; meshes cached with shaders off stay
unadmitted; Java calls allChanged + resends entity meshes once Rust accepts the
source (re-arms in 1-2 frames). Real-config copy `~/.cache/mattmc-claude/realrun`
via scratch `realtest.sh` (kills its client; now `-PmattmcRustProfile=release` like
RunDev — the old ms figures were a dev build). **Perf (RD10, DH r128, shaders):**
material wrapper keyed on mesh_asset_generation (now view+sampler) retired every
pack/DH set per mesh update; lightmap/voxel changes release DH set-one only; shared
DH scalar block + column-frame ring; generic boxes grouped by DH group ordinal
(flags 16-31); encoder env switches cached + pass-kind memo; terrain validation
cached per (stratum,key,generation) (cleared on pack/material/texture change);
generic validation without geometry. Release: frontend 17.5 ms median, admit 2.1.
**Memory (real config, RSS 14.5 GB = java heap memfd 7.9 + anon 5.3 + nvidia 1.1):**
Rust heap 4.25 -> 2.34 GB. Sodium section builders pre-sized 32K quads x 7 facings
x 3 passes per worker (1.15 GB) -> 2048 growing. DH columns kept CPU payload until
first visible (0.83 GB) -> off-screen columns upload 8 MB/frame, then release.
Found by that change: shadow-caster sweep skipped columns whose chunk arrived after
the cursor (caster set timing-dependent, 2776 vs 4714 sections) -> deferred retry.
Left: mesh vertex 756 MB + semantic 529 MB CPU copies. RSS peaks now land 9.1 vs
Frozen 9.5, water 10.8 vs 9.7 (were 12.0/12.8, guard failures).
**Screen effects (Rust GUI):** in-wall used alpha .1 white (vanilla: opaque .1 grey,
mirrored, central window of the z=-.5 quad under itemFov) -> in-wall pair 84-119 ->
1.6 MAE; underwater overlay now honors copied `underwaterOverlay`; fire = vanilla's two
yawed quads projected (8 affine strips); all in new stratum GUI_SCREEN_EFFECT (50,
below post effect/HUD; dynamic layers >=10000 were drawn over the Rust hotbar).
**Tour (real config, DH r128, shaders; nether/end/walk/F5):** walk crashed "shadow
candidate section bound exceeded" (full sweep > 4096) -> own 12288 bound, nearest kept.
End fell back all visit (End sky quads: depth disabled) -> skytextured box (selector 2,
36-vertex primitive, CUSTOM_SKY). MC_RENDER_STAGE_* now Iris ordinals (were stale:
translucent 15/rain 19/entities 23/HAND); hand = HAND_SOLID/_TRANSLUCENT. 1-frame
fallbacks remain on dimension change (DH depth snapshot / shadowtex0).
**Robustness (09-30):** a failed recording (GUI validation) consumed the plan without
discarding DH targets -> every later frame "already awaiting" (1309 fallbacks); now
discarded on failure and stale ones at entry; stale occupancy submissions are dropped
before append (vanilla fallback crashed "colored-light ... awaiting"). Spectator/empty
GUI emitted an empty load/store pass -> rejected every frame; such passes are stripped.
Real-config tours (shaders+DH r128): F1/F3/inventory/pause/chat/F5 and RD 16->6->10
mid-game: 0 crashes, only startup fallbacks. Regressions 09-30: day 2.38, glass 7.42,
down 7.16, gun 2.20, pane 2.38, DH land 5.04 (Frozen run-to-run ~0.6), up 2.55; off
0.26-0.43 = water animation phase only (non-water 0.03).
**DH water:** dh_water now writes depth like DH TRANSPARENT (dhDepthTex0 vs 1): streaks
gone, w-north 4.41 -> 4.13. Rings also appear without DH (RD8): deferred 09-26 water gap.
**Scenarios:** r128 needs a pregenerated DB; water poses flake on DH payload churn.
**Perf (bench.sh, g4src, RD10, DH r32, moving):** 39.8 fps vs Frozen 9.5; RSS 12.3 vs 12.6.

## Current gate (2026-09-25)

Canonical pair: `Origin`, 1280x720, `150.5,100,530.5,105,10`, RD 4, DH off, one two-mode
`Capture.py` run (`--rust-selected-source-execution`), pack ComplementaryHungLoIfied.zip
(SHA-256 `cb4343913a0d...`) via `MATTMC_CAPTURE_SHADER_PACK_SOURCE`. Mask chat x<1000,y=570..609.

## Validation and history

Celestial/End-sky quads: pre-terrain sky writer only; mips only if declared.
**Night/rain gap: deferred by the user (2026-09-25); not a Frozen bug.** Night
5.47/6.78/10.83, rain 8.14/9.34/8.83 (night fog brighter/bluer; dither).
**Iris hand order (09-25).** depthtex2 snapshot -> hand -> hand depth into main depth
-> depthtex1 -> deferred -> translucents. Entity-shadow decals omitted with a shadow pass.
**Shadow map (09-25).** Source shadow pipelines use `RasterYDirection::Down`. Java
sweeps the window for off-camera casters (384/256 per frame; unloaded columns retried).
Look-down residual is timing (dither 0.5 + `WAVING_SPEED 0`: 4.96/4.52/3.45). Gaps:
energy swirl unadmitted (Iris: gbuffers_entities, ENTITIES_CUTOUT, pipeline's
additive blend + texture matrix); no Iris program fallback chains;
`texture2D(sampler2DShadow, vec2)` rejected; edited water sources unadmitted.

**Glint (09-27):** Iris `ShaderKey.GLINT` (GLINT blend, EQUAL depth, no write/cull) in
load-only passes; `invariant gl_Position` fixed EQUAL streaks; alpha tests after main.
**Camera-motion smear (09-27):** same-frame `camera_history` returned previous =
current (TAA saw no motion); fixed (sharpness 135 vs 256 -> 173 vs 183).
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
Shader-off gun pair 0.41/0.70/0.80 -> 0.14/0.25/0.24; TACZ icon offset = animation timing. World item layers carry `minecraft:item_entity/ground/<ns>/<path>`
(`submitSemantic`); others 0. Gap: dropped flat foil item (clamp-glint) opaque here.
**Crash robustness (2026-09-27).** A failed armed submission within 60 frames
of (re)arming disarms and redraws that frame on the vanilla Rust route; later
failures disarm and return `retryable selected-source failure` (Java resubmits
once); a missing DH depth snapshot after a world change disarms in admission.
GUI item layers are compacted, not rejected. Dimension tour: 0 crashes/VUIDs
(program identities carry a dimension tag, shadow identity a content hash;
writer-declared shadow roles are staged even if terrain samples none). Below-horizon
dark disc is sky geometry (skybasic). The old "underwater" 131/152/168 gap was the
camera inside the seabed: missing in-wall overlay (fixed 09-29, see above).
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
cell crossings (walking p50 3.9/p95 20 ms). Gap: voxel vertex cache keeps pack material
ids until the asset reloads. Entity/hand normals = Iris BufferBuilder face normal
(cow 2.09, banner 2.22). Gun gap: muzzle cap/front sight px. TaCZ: 0 fallbacks.
**Casters / outline (09-26).** Player casters: entity `shadow` stage only. Selection
box: pack `gbuffers_line` after the opaque flush (LINES quads s,s,e,e); MAE .42/.70/.51.
**Block breaking via `gbuffers_damagedblock` (2026-09-27).** Crumbling draws
with the pack's damagedblock program after the outline, before
`beginTranslucents` (vanilla CRUMBLING state, bias -1/-10, alphaTest default
0.1); terrain cracks copy vanilla model quads with SheetedDecal UVs. An armed
route meeting an uncoverable frame disarms (`admit_armed_source_frame`). Sign pair 2.54.
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

Shadow-only casters (halo + source frustum) feed only the shadow pass; 2048^2 shadow
target, Iris white shadow-color clear. Keep one Rust frame/presenter, immutable asset
validation, indirect terrain submission, static fragment specialization. Goals 1-2
remain regression baselines (recheck after shared changes). No commit or push.

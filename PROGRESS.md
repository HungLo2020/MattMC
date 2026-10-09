# Rust migration working record

## Goal and constraints

- ACTIVE: remove Java/JVM entirely; one Rust library and one executable for client/server.
- Use untouched Frozen Java OpenGL for behavior, images and performance. Preserve Rust/GAL resource, submission and presentation ownership.
- Work in this checkout on master; publish tested larger milestones. Assumptions: `ASSUMPTIONS.md`.
- Preserve/exclude the user's `docs/STANDARD-COPILOT-PROMPTS.md` edit (SHA c5695e9b). SUMMARY≤10 lines; this record≤100.
- Put developer guides beside their subsystem and verification drivers under `DevUtils/tests/`; check Wiki after documentation changes.

## Current ownership and gaps

- Rust owns rendering/GAL/presentation, terrain visibility/assembly/publication, retained DH inputs, state graphs and many storage/world kernels.
- Canonical live block sections, counters, rebuild captures, generation handoffs and light layers now have Rust owners with CPU compatibility views.
- Java still owns general gameplay/simulation, chunk orchestration, contextual callbacks, assets/platform/startup and remaining frame extraction.
- Cargo builds the native library; `app/` is empty. The native executable and complete Java removal are unfinished.
- Performance gaps guide further ownership migration; they do not justify abandoning it. Full acceptance still requires realistic Frozen-equivalent workloads and bounded resources.

## Published light ownership — `8dab8e575`

- Rust owns canonical lazy/allocated generations, mutations/copies, propagation result installation, sky seeding/repetition and independent packet imports.
- Original map COW, raw defaults, invalid-write materialization, mutable-array escape/constructor aliases and subclass callbacks remain constraints.
- Rust2436 pass/3 ignored; fullJava1800 pass/2 skipped. Focused lighting18 Rust/29 Java pass; 108 actual Frozen representations match every4096 cells.
- Release31c8c8cc passes all7 lifecycle cases and reviewed vanilla/Iris+DH coast pairs/DH coverage. All16 ABAB/exact6000 runs are clean; VUID/exception/orphan0.
- Performance FAILS vanilla FPS/p99 and DH p99. MedianFPS Current/Frozen: vanilla1096/1153, DH703/669, shaders352/317, shaderDH253/229. SUMMARY retains both repeats.
- Medianp99 ms Current/Frozen: vanilla3.781/3.439, DH5.524/5.519, shaders5.370/6.693, shaderDH7.377/7.542. No isolated or overall gain claimed.
- Historical driver/integrity: `build/native-light-migration/runtime-final.log` and `production-verification.json`;original runtime invocation retired by existing retention.25 generated copies retired;source/native/Frozen/protected-edit integrity passed.
- Paired8s flight profiles pass identity/movement/cleanup and inspected F3 positions;4 copies retired. Weighted Java allocation Current0.934GB/Frozen2.186GB is diagnostic only.
- Current computeLightWord samples41.94MB, DataLayer84.93MB, native binding8.39MB; categories overlap. CPU is JIT-heavy. Comparison: `goal5/native-live-light-flight-profile-20261009/profile-comparison.json`.
- Incomingba8d4a937 was documentation-only;two colliding guides reconciled.21 reviewed paths published via normalpush,remote verified;owned documentation stash dropped,older stashes/user prompt untouched.
- Guide: `docs/development/world/lighting/RUST-LIVE-LAYERS.md`.

## Current local batch — direct terrain-light consumer

- Rust reads54 retained light leases and native state columns directly into18³ mesher words. Java supplies8-byte contextual predicates/shade and one reusable position, without scalar light projections or final word packing.
- Native state/platform/light admission precedes contextual callbacks. Mutable arrays, custom layers/states/platforms and appearance diagnostics retain the scalar compatibility path.
- FullRust2440 pass/3 ignored;fullJava1803 pass/2 skipped. Focused27 Java checks include actual producer/native boundary for all254472 Frozen outputs, GC/fill leases, expired scopes and rejection without partial output.
- Four native checks cover Frozen outputs, halo faces/edges/corners, missing light types and rejection. The13.6KiB fixture losslessly retains31809 states×8 light/AO variants;actual Frozen regeneration matches byte for byte.
- Pinned symbol releasea25a1281 passes all7 lifecycle cases and reviewed settled compatibility pairs/DH coverage. All16 ABAB/exact6000 runs clean,VUID/exception/orphan0;25 generated copies retired.
- Performance FAILS vanilla and DH FPS/p99. MedianFPS Current/Frozen:vanilla1111/1185,DH682/706,shaders337/315,shaderDH254/227. Medianp99ms:3.884/3.028,6.592/5.242,5.712/6.386,7.188/7.761. No isolated gain claimed.
- Final source freeze covers src, Common/rendering/tooling verification code and build.gradle. Full Java executor mapping matchesa25a1281. Driver: `build/native-terrain-light-migration/final/production-driver.log`.
- Gradle now tracks release debug/strip environment policy. Isolated actual-task staging/reuse/invalidation/default-restoration checks pass. Initial stripped preflight was interrupted/excluded before runtime.
- Initial test compile referenced a removed Iris setter;corrected CPU fixture supplies equivalent AO through test-only reflection/restoration. Original failure retained.
- Guide: `docs/development/rendering/RUST-TERRAIN-LIGHTING.md`. Paired streaming profiles start only after clean runtime timing completes.
- Source/native/Frozen/protected-edit integrity passes;runtime driver exit1 is performance-only. Settled pairs use diagnostic scalar lighting;compatibility only. Ordinary-input profiles prove bulk execution;flight images are not registered pixel parity.
- All4 flights pass identity/timing/source/cleanup and actual F3 review;4 copies retired. CPU pair identical→266.166/95/561.485;allocation pair identical→266.692/95/561.626,from150.5/95/530.5,yaw−75/pitch25.17 native prepare samples prove bulk execution;Current JIT PhaseCoalesce4642/Frozen87 limits attribution.
- Weighted8s Java allocation Current1.091GB/Frozen2.448GB;computeLightWord samples42MB→0 versus preceding31c. Total Current allocation rose from0.934GB;no isolated/overall gain. Categories overlap;short RSS only. Comparison:`goal5/native-terrain-light-flight-profile-20261009/profile-comparison.json`.
- Integrated upstream4246f4e7b(36 paths):incremental GUI image retention/ABI78,packed DH admission and ordinary-gameplay performance harness.3 overlapping guides restored cleanly from owned stash53159c1;user edit/older stashes untouched.
- Combined release d9d1a9d6:Rust2443 pass/3 ignored;Java1807 tests/2 skipped/no failures;all7 lifecycle and reviewed vanilla/Iris+DH diagnostic compatibility pairs/DH coverage pass. All16 ABAB/exact6000 clean,VUID/exception/orphan0;25 copies retired;all source/native/Frozen/protected guards pass.
- MedianFPS Current/Frozen vanilla1263/1158,DH657/643,shaders336/317,shaderDH257/228. Medianp99ms3.216/3.072,6.839/5.749,6.137/5.979,6.212/7.446. Performance gate FAILS vanilla,shader andDH p99;large repeat variance/no isolated gain. SUMMARY retains both repeats.
- Combined receipts:`build/native-terrain-light-migration/integrated/{verification,runtime-summary,visual-review}.json`;actual fullJava executor mapsd9d1a9d6. Images use scalar diagnostics;bulk pixels/flicker absence remain unproved. Retain owned stashes through publication.
- Incoming f7d5f7dd2 changes documentation only; two guide conflicts combine native-light ownership/publication caveats with the direct terrain consumer. Renderer/native identity remains unchanged; repeat Wiki after reconciliation.
- First ordinary run failed before game startup:--jdk controlled observer only,old inherited JVM rejected UseCompactObjectHeaders. Failed fixture retained,no usable timing/native mapping. Harness now sets child JAVA_HOME/PATH;6 JDK25 checks pass,including actual executable/VM flags and frame-agent return/DH instrumentation. Initial regression fixture omission corrected;failure log retained.
- Ordinary Current/Frozen/Frozen/Current completes:30s entry/standing/travel,visible minimap,DH draws,same copied world. MedianloopFPS C/F535/555,619/601,634/623;p99ms6.740/5.904,2.625/2.911,3.000/2.904. Entry/travel performance remains open;17 Current vs2 Frozen frames>50ms across both repeats.
- All source/native/Frozen/original-world/protected guards pass,exceptions0/ownedclients0;12 HUD snapshots reviewed. Both entries have incompletely built terrain;first Current is less complete,images not time registered. Standing/travel broadly match;minimap initialization/details differ. Movement is only16.45 blocks before a terrain barrier,not sustained streaming or pop-in/flicker proof.4 verified copies retired;failed first fixture retained. Receipt:`goal5/native-terrain-light-ordinary-integrated-jdk-fixed-20261009/comparison.json`.


## Next performance migration

- Profile-backed priorities:pre-integration8s background/sky47MiB,model rig30MiB,block-entity scope19MiB;item restamp1MiB. Migrate live world/biome state with direct background/model consumers;figures are diagnostic,not isolated gains.
- Move retained CPU item/entity mesh payloads and direct native consumption together. Cached item generation restamps currently clone Java indices twice;preserve texture admission,reload/retirement and mutable API compatibility.
- Ignored Rust producer/owner candidate passes8 checks,including99999 shared restamps and576 actual Frozen fast-encoder cases. Frozen disables baked-color multiplication and merges embedded light;correctness reference is its actual recorded stream. Native wiring/reload/foil/real runtime still pending;not production or speedup proof.
- Earlier world-item profile samples~102MB/15s beneath enqueue;only part is index cloning. Guide: `docs/development/rendering/RUST-ITEM-LAYERS.md`.
- Ignored native biome owner/direct sky consumer now passes12 checks:1600 actual Frozen import/growth/padding/copy/shared-single operations and256 exact sky-sampler cases,alias-safe captures,generation/compatibility invalidation,world unload,view-center hiding,height/epoch/capacity bounds,window reuse and CPU owner/view lifetime. Production FFI/packet/mutation/sky wiring and runtime proof remain unfinished. Workbench:`build/native-terrain-light-migration/next-world/` [not a published feature].
- Continue authoritative world-state producers/consumers and simulation ownership ahead of more static catalogs;then assets/platform/network/native application and Java/bridge/build removal.
- Broader gameplay, long-session memory and temporal rendering proof remain incomplete. Settled coast pairs cannot certify unseen scenarios or flicker absence.

## Earlier verified foundations

- `642943247`: Rust launch policy and world/hand poses;12000 Frozen CPU cases exact. Full held-clock native admission/pixels/reload/Vulkan/memory passes;normal-ground strict probes remain unaccepted because both live versions reject the older fixture. Frozen behavior is unchanged.
- Its16 clean ABAB runs passed medianFPS floors but failed vanilla p99(3.506/3.019ms),with substantial repeat variance. `validation/native-world-item-final-20261009/summary.json`;25 copies retired.
- `d9c92dedf`: authored GUI/item pose ownership and lazy mutable meshes;full CPU/lifecycle/reviewed pairs passed,vanilla performance floors remained open. Item guide retains detailed checkpoints.
- `b3c8dbe1`: native DH cloud preparation;`59171b74`/`e62309898`: live section counters. Proper cloud/counter guides retain verification scope and performance failures.
- `a908f78cd`: native NOISE/SURFACE/CARVERS handoffs;diagnostic stage transfer allocation107.9→1.0KB/chunk,not isolated throughput evidence. Stage handoff guide retains runtime results.
- `9afdb7d2c`: retained rebuild captures;live-section alias checkpoint526af413 passed full suites/lifecycle/pairs but missed vanilla/DH floors. Chunk guides retain palette/alias/publication constraints.
- `6324cd1dd`: retained DH visibility frames;`c9e2a71d4`: shared world-color fields;`84016f210`: map/state policy. These scoped improvements do not establish complete game parity or performance.
- Block/state/fluid/physical/intrinsic/sound/offset/family foundations are documented under `docs/development/game-model/`;remaining producers/behavior are still migration work.

## Evidence and storage

- Preserve failed/incomplete/crashed/symlink/unproven fixtures. Retire generated copies only after terminal receipts and owned-process identity checks;retain compact evidence.
- Bulk pruning recovered~350GiB;routine retirement continues. About287GiB free before the current runtime. Guide: `docs/development/rendering/ARTIFACT-STORAGE.md`.
- Clean FPS windows exclude builds/profilers/other games;require2 repeats per side/mode,exact6000 frames,VUID0,averageFPS≥Frozen,p99≤Frozen for full performance acceptance.

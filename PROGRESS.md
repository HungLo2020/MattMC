# Rust migration working record
## Goal and rules
- Remove Java/JVM completely: one Rust library and one executable for client/server. Goal remains ACTIVE.
- Maintain/improve correctness and performance against untouched Frozen Java OpenGL; no Java Vulkan baseline or fallback.
- Publish tested larger milestones on master in this checkout. No separate migration checkout. Assumptions: `ASSUMPTIONS.md`.
- Preserve/exclude the user's `docs/STANDARD-COPILOT-PROMPTS.md` edit (SHA c5695e9b). Keep SUMMARY≤10 lines and this record≤100.
- Developer documentation belongs in relevant `docs/development/` directories; run Wiki checks. Test drivers belong in `DevUtils/tests/`.
## Current ownership
- Rust owns Vulkan/GAL rendering and presentation, terrain visibility/assembly/publication, DH ledger/visible payloads, state graphs and many world/storage kernels.
- Registered block/fluid facts and policies originate in Rust with Java compatibility projections. Canonical live block palettes/storage now have a Rust owner. General gameplay, chunk orchestration, platform/assets/startup remain Java.
- Cargo builds a cdylib only; `app/` is empty. Native executable/lifecycle and complete Java removal remain unfinished.
- Rust/GAL retains all GPU resources, passes, synchronization, submission, completion and presentation. Transitional Java exposes bounded CPU semantics only.
## Current batch — authoritative live block sections
- Rust owns canonical packed storage, palette admission/growth and fused mutations. Java retains CPU views without a palette/word mirror or per-read downcalls. Copies and valid saved/network/generated imports adopt native ownership.
- Rebuild snapshots decode directly from the live owner. Light payloads, ordered counting and heightmap/skylight exports avoid Java storage reconstruction. Generic enumeration/save/network/stage exports still use temporary compatibility projections; bulk producers remain work.
- Atomic publication and separately leased generations keep retained readers valid through growth. Custom policies/aliases/malformed imports retain compatibility. Invalid writes and callback mutations preserve original ordering.
- Original zero-width copies share their palette: successful single-value network reads update all copies; growth detaches only its owner. Failed other-width reads retain the old generation. Native immutable captures sample that value once.
- Guide: `docs/development/world/chunk/RUST-LIVE-SECTIONS.md`; affected palette, lighting, heightmap and stage guides describe current ownership separately from historical helper evidence.
## Final verification — 2026-10-09
- Release SHA526af413: Rust2415 pass/3 ignored;focused Java113 pass;full Java1746 pass/2 skipped;Wiki2484 pages/43 indexes passes.
- All seven lifecycle cases pass: world unload/reload,different-world reload,resource reload,resize,swapchain recreation,and both view-distance changes. Exceptions/panics/dependency/GAL violations0.
- Reviewed actual vanilla/Iris+DH coast pairs pass. RGB0.401/0.666/0.784 and3.765/4.336/4.028;DH coverage passes,VUID0. Static pairs do not certify unseen flicker or broad gameplay.
- `validation/native-live-block-sections-alias-final-20261009/summary.json` FAILS performance floors. All16 ABAB/exact6000 runs clean,exceptions0,VUID0,owned orphans0. Final source/native/protected-user-doc/Frozen integrity passes. Twenty-five generated copies retired.
- SUMMARY has final repeats and p99:vanilla−5.1%,DH−24.3%,shaders+7.5%,shader+DH+11.5%. Vanilla/DH miss average-FPS and p99 floors;shader modes pass both. No isolated storage speedup or root cause for DH degradation proven.
- Earlier pre-alias release4a8f also passes full suites,lifecycle and reviewed pairs,with vanilla/DH floors failing. Separate receipt: `validation/native-live-block-sections-20261009/summary.json`;25 copies retired. Do not substitute it for final-source proof.
## Diagnostic profiles and performance investigation
- Earlier release4a8f paired ordinary-flight profiles: `goal5/live-block-sections-flight-profile-20261009/`. Timing/source/native/Frozen/cleanup checks pass. Reviewed identical start150.5/95/530.5 and forward266.166/95/561.485,seven X columns. Two generated copies retired.
- One8s window per side: capture+slice prep24→13 Current samples versus previous snapshot checkpoint;NativeSectionSnapshot334→273;whole meshing470→348. Small samples,scene scheduling and observer overhead prevent throughput attribution. Some inline labels contradict eligible paths;do not infer getter copies from them. Compact `profile-comparison.json` records limits.
- Paired steady-DH CPU/allocation profiles: `goal5/live-sections-dh-benchmark-profile-20261009/`;both timing/source/native/cleanup receipts pass,2 copies retired. Pre-alias scope only;profiler FPS is not acceptance evidence.
- Current20s CPU25003 samples: native frame worker15278(61.1%),NVIDIA5922 inclusive,memcpy1376;live-owner labels60(0.24%). Allocation samples estimate Current3.16GB/Frozen6.66GB per15s;live-owner2.1MB. Different workload phase/profiler overhead prevent isolated allocation/FPS claims.
- Java item mesh construction/cloud culling/string/vector preparation and native submission/encoding remain significant. Retained six-run DH phase comparison shows higher native/GPU time despite fewer draw ops than the snapshot checkpoint;does not isolate the cause.
## Previous published foundations
- `9afdb7d2c`: Rust immutable4096-state captures and bulk18³ halo reads;ordinary rebuilds avoid Java palette cloning/object expansion. Each capture8KiB;existing512-entry/5s cache;all27 reset slots clear retained references. See snapshot guide for compatibility and prior verification scope.
- `a0f5abeb2`: evidence/retention guards;missing/failed evidence cannot pass through historical comparisons.
- `6ccbfdf41`,`df6c6dc77`,`2f2158cc4`: state graphs,fluid/property definitions,1235 blocks/31809 states. Java objects remain views.
- `1b1837931`,`d0141162d`,`059c95621`,`48a6e051e`: physical/intrinsic/sound/offset/family ownership. World callbacks/scheduling/shapes remain unfinished.
- `84016f210`: native map colors/image processing/state policy and framed-map shader ordering. Full performance acceptance remained open;subsystem guides retain scoped evidence.
- `6324cd1dd`: native DH visibility frames avoid ordinary Java export/list/re-encoding. Target DH encoding allocation432→34MB/15s,diagnostic;not all JVM allocation. See retained-scene guide.
- `c9e2a71d4`: native shared world-color fields avoid per-block64-sample reconstruction. Full checks passed;vanilla performance floors remained unmet. See biome section-color guide.
- Prior documentation sync preserved user work;recovery: `build/map-palette-migration-draft/docs-upstream-sync/`. Earlier stash/transfer receipts remain preserved.
## Next work — user's performance priority
- Continue moving authoritative world state and hot producers/consumers together. Avoid further static catalogs as the main migration batch.
- Remove Rust→Java arrays/palettes→Rust reconstruction at NOISE/SURFACE/CARVERS stage installation and imports. Then move the section update/counter transaction and remaining bulk consumers;preserve exact palette/save/callback/lifecycle rules.
- Move per-frame item/cloud/scene preparation into Rust and investigate native frame-worker costs using the retained profiles. Performance gaps guide continuing migration;they do not excuse abandoning ownership work. Full acceptance still requires realistic Frozen-equivalent workloads and bounded resources.
- Broader gameplay/transition coverage remains incomplete,including bed/banner/zombie/held-item cases. Settled coast pairs/source tests cannot certify unseen scenarios or temporal flicker absence.
- Later:world simulation/behavior/components,assets/platform/network ownership,native application lifecycle,then Java/bridge/build removal.
## Storage and verification discipline
- Preserve live/crashed/symlink/unproven fixtures and sources. Retire generated copies only after process/start-identity and terminal-receipt checks;retain compact evidence.
- Bulk pruning recovered~350GiB;routine workflow/profile retirement continues. About303GiB free after this batch. Storage guide: `docs/development/rendering/ARTIFACT-STORAGE.md`.
- Clean FPS windows exclude builds/profilers/games. Require≥2 repeats per side/mode,exact6000 frames,clean receipts,VUID0;average FPS≥Frozen and p99≤Frozen for full performance acceptance.

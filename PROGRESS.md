# Rust migration working record
## Goal and rules
- Remove Java/JVM completely: one Rust library and one executable for client/server. Goal remains ACTIVE.
- Maintain/improve correctness and performance against untouched Frozen Java OpenGL; no Java Vulkan baseline or fallback.
- Publish tested larger milestones on master in this checkout. No separate migration checkout. Assumptions:`ASSUMPTIONS.md`.
- Preserve/exclude the user's `docs/STANDARD-COPILOT-PROMPTS.md` edit (SHAc5695e9b). Keep SUMMARY≤10 lines and this record≤100.
- Developer documentation belongs in relevant `docs/development/` directories; run Wiki checks. Test drivers belong in `DevUtils/tests/`.
## Current ownership
- Rust owns Vulkan/GAL rendering and presentation, terrain visibility/assembly/publication, DH ledger/visible payloads, native state graphs and many world/storage kernels.
- Native registered block/fluid facts and policies support Java compatibility projections. General gameplay, live world storage/mutation, orchestration, platform/assets/startup remain Java.
- Cargo still builds a cdylib only; `app/` is empty. Native executable/lifecycle and complete Java removal remain unfinished.
- Rust/GAL must retain all GPU resources, passes, synchronization, submission, completion and presentation. Transitional Java exposes bounded immutable CPU semantics only.
## Latest implementation — loaded-section snapshots
- Rust owns immutable 4096-state rebuild captures, decoding and bulk18³ halo reads. Ordinary sections avoid Java palette cloning/4096-object expansion. Java callbacks use read-only CPU views without per-block FFI.
- Existing512-entry/5-second cache and outstanding jobs retain owners; each capture8KiB. Automatic arenas release Rust storage after readers disappear; no native global cache. All27 reset slots now clear state/model/light/entity references.
- Custom palettes/registries/storage/strategies and debug-world substitutes preserve their CPU compatibility path. Canonical state IDs, model-generation admission and compact header4/whole-frame ABI74 remain unchanged.
- Live loaded-world mutation is still Java. Practical constraints/commands:`docs/development/world/chunk/RUST-SECTION-SNAPSHOTS.md`.
## Verification and performance — 2026-10-09
- Native2409 pass/3 ignored;full Java1739 pass/2 skipped;38 affected Java checks pass, including7 new snapshot tests. Wiki2483 pages/43 indexes passes.
- Release SHA15c7ba5e: all7 lifecycle cases and reviewed vanilla/Iris+DH pairs pass. RGB0.203/0.348/0.382 and3.717/4.266/3.963;DH coverage passes,VUID0.
- Full workflow`validation/native-chunk-state-snapshots-20261009/summary.json` FAILS performance floors. All16 ABAB/exact6000 runs clean,exceptions0,VUID0,owned orphans0;game source/library/Frozen unchanged. Twenty-five generated copies retired.
- SUMMARY.md records current measurements:vanilla−9.8% versus Frozen;DH+3.0%;shaders+7.6%;shader+DH+9.4%. Vanilla FPS/p99 and both shader p99 floors fail. DH repeats vary;no isolated ownership speedup claim.
- Paired normal-flight profiles`goal5/chunk-state-snapshots-flight-profile-v3-20261009/` pass timing/source/library/Frozen/cleanup checks. Reviewed identical start150.5/95/530.5 and terminal block266/95/561,seven X columns;about half-block terminal drift. Diagnostic only.
- CPU samples/8s:capture+slice preparation35→24;NativeSectionSnapshot313→334;whole meshing task475→470. Small samples/scene scheduling/observer overhead prevent a speedup claim. Comparison:`build/map-policy-performance-profile/chunk-state-profile-comparison.json`.
- Two Current profile attempts rejected video timestamp collisions and remain preserved. Reproduced Matroska400µs→duplicatePTS;FFV1/NUT preservesµs without resampling or relaxed validation. Seven observer tests pass. Historical profiles used MKV;container change is a comparison limitation. Two accepted profile copies retired.
- Frozen remains clean at7a4d18171. Benchmark/full Java precede final Java view-adoption ordering hardening;38 affected checks pass again. Read-only wrapper now precedes cleanup registration;closed-arena/read-only tests pass. Native library unchanged;reviewed final Iris+DH proof passes RGB3.746/4.309/3.992,VUID0/integrity/owned orphans0. One final proof copy retired. Video-observer correction is separate.
## Published foundations and retained evidence
- `a0f5abeb2`: verification/retention guards;missing/failed evidence cannot pass through historical comparisons.
- `6ccbfdf41`,`df6c6dc77`,`2f2158cc4`:state graphs,fluid/property definitions,1235 blocks/31809 states. Java objects remain views.
- `1b1837931`,`d0141162d`,`059c95621`,`48a6e051e`:physical/intrinsic/sound/offset/family ownership. World callbacks/scheduling/shapes remain unfinished.
- `84016f210`:native map colors/image processing/state policy and framed-map shader ordering. Full acceptance remained open;focused game-model pages retain evidence.
- `6324cd1dd`:native DH visibility frames avoid ordinary Java export/list/re-encoding. Target allocation432→34MB/15s,diagnostic;whole-renderer performance remained open. See retained-scene guide.
- `c9e2a71d4`:native shared world-color fields avoid per-block64-sample reconstruction. Full native2405/Java1732 plus77 affected/final admission proof passed;world-color throughput comparison failed vanilla floors. See biome section-color guide.
- Scoped upstream documentation sync preserved user work;recovery:`build/map-palette-migration-draft/docs-upstream-sync/`. Prior batch/stash/transfer receipts remain preserved.
## Next work — user's performance priority
- Prioritize live world-state ownership and hot producer/consumer paths over further static catalogs. Move authoritative chunk storage with lighting,heightmap,save and rebuild consumers;reuse the existing native stage/storage kernels carefully.
- Preserve palette identity/history,IDs,state/property/save bytes,callback ordering and generation/lifecycle rules. Avoid a Java mirror or per-block native downcalls. Profile complete callers and real workloads.
- Investigate remaining per-frame preparation and native frame-worker costs alongside migration. Current performance gaps guide work;they do not halt tested ownership milestones. Full acceptance still requires realistic Frozen-equivalent workloads and bounded resources.
- Broad gameplay/transition coverage remains incomplete,including bed/banner/zombie/held-item cases. Settled coast captures and source tests cannot certify unseen cases or temporal flicker absence.
- Later:world simulation/behavior/components,assets/platform/network ownership,native application lifecycle,then Java/bridge/build removal.
## Storage and verification discipline
- Preserve live/crashed/symlink/unproven fixtures and sources. Retire generated copies only after process/start-identity and terminal-receipt checks;retain compact evidence.
- Bulk pruning recovered~350GiB;routine workflow/profile retirement continues. Storage guide:`docs/development/rendering/ARTIFACT-STORAGE.md`;receipts:`goal5/disk-cleanup-20261008/`.
- Clean FPS windows exclude builds/profilers/games. Require≥2 repeats per side/mode,exact6000 frames,clean receipts,VUID0;average FPS≥Frozen and p99≤Frozen for full performance acceptance.

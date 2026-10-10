# Rust migration working record

## Goal and constraints

- ACTIVE: remove Java/JVM entirely; one Rust library and one executable for client/server.
- Work on master in this checkout; publish tested larger milestones normally.
- Untouched Frozen Java OpenGL is the sole behavior/performance baseline: `../MattMC_JavaPerfTesting/MattMC`, HEAD `7a4d181717dc0c7da9086f1a17687dfd522ce417`.
- Preserve Rust/GAL ownership of resources, synchronization, submission and presentation. Java compatibility bridges expose bounded CPU semantics only.
- Preserve/exclude the protected prompt at `docs/STANDARD-COPILOT-PROMPTS.md` (SHA c5695e9b). SUMMARY≤10 lines; this record≤100; assumptions in `ASSUMPTIONS.md`.
- Developer guides belong with their subsystem; verification tooling under `DevUtils/tests/`; run Wiki after doc changes.

## Current ownership and remaining work

- Rust owns rendering/GAL/presentation, terrain assembly/publication/visibility, retained DH inputs, state graphs and many world kernels.
- Canonical block/biome sections, rebuild captures, counters, live light generations and loaded-biome residency have Rust owners.
- Java still owns general gameplay/simulation, chunk orchestration, contextual callbacks, assets/platform/network/startup and remaining frame extraction.
- Cargo builds the native library; `app/` is empty. The executable and complete Java removal are unfinished.
- Prioritize authoritative world-state producers and direct consumers over more static catalogs. Performance gaps guide migration; they do not justify abandoning it.

## Published sky/fog and color reuse

- `1b9b10339`: native live biome palettes/index and direct sky consumer. Mutable-section escape, shared-single aliases, packet/range/unload and original callback admission remain constraints.
- `3adbe6d5d`: direct fog consumer and generation-validated Rust result reuse. Java camera/tick-only outer memos were removed after reproducing a stale same-camera/tick hook result.
- Exact-position/field reuse validates the world revision and every captured source identity/generation/revision. Java hooks, weather and brightness still run each call; dynamic biome effects and custom sources retain Java.
- Actual Frozen fixtures cover 1,600 palette operations, 256 double-bit color samples and 12,288 range cases. The range oracle exposed widened arithmetic; wrapping Java-int subtraction/abs fixed it.
- Release f449557e: full Rust2,456 pass/3 ignored; full Java1,818 tests/2 skipped/0 failures; all six actual JNI executors map the exact canonical library.
- All seven lifecycle cases and both manually reviewed vanilla/Iris+DH settled pairs/DH coverage pass. VUID/exception/orphan counts0. Diagnostic scalar pairs cannot certify ordinary bulk-input pixels or temporal defects.
- All16 ABAB/exact6,000 runs clean. MedianFPS C/F: vanilla1224.7/1159.1, DH720.2/661.45, shaders345.2/317.7, shadersDH255.45/226.85. Both raw repeats remain in SUMMARY.
- Medianp99 ms C/F: vanilla3.394/3.322 FAIL; DH5.235/6.405, shaders4.773/6.196, shadersDH6.810/8.602 pass. Large repeat variance; no isolated or overall performance acceptance.
- All current/Frozen/native/protected-prompt integrity guards and eight actual Current FPS-library fingerprints pass.25 generated runtime copies retired.
- Four ordinary8s CPU/allocation profiles pass identity/movement/cleanup; all12 F3 captures reviewed. Forward/return endpoints differ≤0.545 blocks; both cross seven X chunk columns. Four additional copies retired.
- Weighted Java allocation0.951/2.437 GB C/F excludes Rust. Current Java sky/fog grids sample0 versus Frozen208.67/12.58 MB; native color CPU sampling0 is not absence proof. Current/Frozen JIT PhaseCoalesce5,054/100 limits attribution.
- Current Java lighting-map copy allocation89.13 MB motivates the current migration. No isolated gain or long-session memory proof from these profiles.
- Guide: `docs/development/world/biome/RUST-LIVE-BIOMES.md`. Receipts: `build/native-biome-fog-cache-migration/` and `goal5/native-live-biome-fog-cache-flight-profile-20261009/profile-comparison.json`.

## Current local light-map migration

- Canonical Block/Sky maps now live in the existing Rust library. Snapshots share64-shard roots; updates detach only the root and changed shard. Sky tops/defaults migrate too.
- Bounded CPU identity pins preserve original Java object identity; native map/cache/snapshot references retain typed Arc light owners. Slots are reused only after Java clears retired references and acknowledges them.
- Preserve Frozen sentinel/stale-cache/null membership, shallow identity, copy-reset and lowest/default behavior. Caller-supplied Java-map constructors retain aliases/custom behavior. Virtual layer copies remain callbacks.
- Mutable array escape invalidates the typed owner, preserving existing generation leases. Refilling may re-adopt a new owner; old map entries decline direct reads until replaced.
- Canonical block/sky scalar reads also consume typed maps directly; visible maps use shared read locks. Custom storages/layers/arrays retain callbacks. Original queued-light precedence and first-write publication COW remain intact.
- Terrain captures retain original DataLayer references; the bulk consumer borrows generations at preparation time. Capturing light bytes earlier would change Frozen fill semantics.
- Actual Frozen map fixture4,096 operations and scalar-consumer fixture1,024 cases cover identities, defaults, missing layers, sky enablement and extreme packed coordinates. Map fixture regeneration matches both production fixtures exactly.
- Actual Frozen fixtures/focused Rust29 and Java26 pass. A20,000-cycle concurrent-snapshot test reproduced premature CPU-slot retirement; removal now locks through result resolution. External `super(null)` compatibility probe failed Current/passed Frozen; native constructor now package-private, permanent external regression added.
- Full Rust2,467 pass/3 ignored and final Java1,827 tests/2 skipped/0 failures; six actual JNI workers exact7580a46e. Initial validation was intentionally interrupted for the constructor fix; preserved full Rust result reused only after verifying unchanged Rust source/library.
- Final runtime: all seven lifecycle cases and both manually reviewed settled vanilla/Iris+DH diagnostic pairs/DH coverage pass; VUID/exception/orphan0. All16 ABAB/exact6,000 runs clean, source/library/Frozen/prompt guards and eight Current native fingerprints pass;25 completed runtime copies retired. Initial interrupted fixture retained.
- MedianFPS C/F: vanilla1,099.8/1,158.0 FAIL; DH737.2/661.9, shaders348.3/318.0, shaderDH253.8/228.25 pass. Medianp99ms4.090/3.114 FAIL vanilla; DH5.311/5.851, shaders5.694/6.296, shaderDH6.412/7.714 pass. No overall/isolated performance acceptance. Four ordinary8s CPU/allocation flights andall12F3 reviews pass; final source/library/Frozen/prompt guards pass. Four completed copies retired. Source freeze lifted after guards.
- Retirement uses reusable CPU output and64-slot stack scratch; Java handoffs reuse their reservation output.100,000 retirement cycles retain bounded slot tables. Candidate10,000-snapshot zero-table-allocation check is not runtime speedup proof.
- Ordinary weighted Java allocation0.757/2.369GB C/F; lighting-map copy path1.05/74.45MB versus preceding Current89.13MB. Remaining sampled copy-path allocation is an FFM lease object. Excludes native allocations and does not isolate total FPS benefit. Native scalar-light46samples, map-related71; dynamic translucent sorting1,020samples; diagnostic validation affects attribution. Forward/return endpoints differ≤1.089blocks; both cross seven X chunk columns. Short RSS windows are not long-memory proof.
- Pre-sync terrain snapshot preparation samples55.57MB Java allocation, including direction arrays already fixed upstream; legacy packet exports14.68MB. These world-state consumers guide the next migration; overlapping categories are not additive.
- Next terrain face-policy candidate under `build/next-terrain-culling/` matches787,992 actual Frozen production decisions across31,809states andall21,316 unique-shape pairs.21,007 shape identities deduplicate to146 immutable descriptions, preserving identity flags. Retained-table candidate also passes:756,437 decisions native,31,555 tag/hook callbacks preserved;21,316 bounded shape-pair entries, invalid IDs/directions decline. Candidate only: production ownership/wiring and runtime pending.
- Guide: `docs/development/world/lighting/RUST-LIGHT-MAPS.md`; evidence under `build/native-light-map-migration/` and `goal5/native-light-map-flight-profile-20261009/profile-comparison.json`. Performance acceptance remains open.

## Sync and publication

- Requested600s work then sync completed: integrated f86206767 testing speedup, preserving39 local paths/modes without stash or conflicts. Driver now runs test+parityTest.
- Later upstream documentation through b9319b7a9 integrated; scoped guide conflicts reconciled with native ownership. External prompt-only442574c44 preserved. No incoming source/runtime changes in these documentation merges.
- Published18 reviewed fog/cache paths as3adbe6d5d; normal master push and remote verified. Protected prompt/older stashes untouched. Verified change-only recovery removed; no whole-repository backups.
- Latest sync fast-forwarded five commits to81440bf19: recorder/scripted harness, allocation reductions, lazy Rust mesh resource sets.28 local paths preserved; scoped SUMMARY stash resolved with latest pre-sync7580 results plus incoming historical qualifier, staging restored empty.27 other paths/modes byte-identical; protected prompt unchanged. Combined verification and affected transition now pass; only scoped SUMMARY stash/change-only recovery removed, older two stashes preserved. Compact restoration/cleanup receipts retained.
- Fresh integrated fullRust2,467pass/3ignored; releaseb7297d06 builds; fullJava1,829tests/2skip/0fail, sixJNIworkers exactlibrary; recorder Python2 and scripted-harness Python4 tests pass. Both new manually reviewed pairs/DH coverage pass; all16 ABAB/exact6000 runs clean; source/library/Frozen/prompt guards pass;25 completed runtime copies retired.
- Integrated medianFPS C/F: vanilla1327.95/1171.55, DH769.6/645.15, shaders347.4/315, shaderDH258.95/227.15. Medianp99ms vanilla3.277/3.097 FAIL; DH3.868/6.245, shaders4.590/6.411, shaderDH6.004/8.191 pass. Upstream resource/allocation changes plus map migration; no isolated gain or overall acceptance. Prior7580 timing/profiles remain historical.
- Four fresh b729ordinary8s profiles pass all terminal/integrity/movement/cleanup guards; all12F3 images reviewed, CPU endpoints identical and maximum allocation endpoint drift1.089blocks, both cross seven X chunk columns. Four copies retired. Initial PID/focus-guard rejection retained with no logged exception/panic; no validator relaxation.
- Integrated weightedJava allocation0.823/2.398GB C/F; map-copy path0sampled/94.37MB, no fullJava map cloning; native allocations excluded. Current section preparation25.17MB, including8.39MB occlusion-cache tables; Direction.values0sampled and legacy packet export0sampled after upstream. ResourceLocation strings5.24MB. Sparse samples/combined changes prevent isolated gains. CPU Current native sort962, Java section prep319, culling40, scalar light56leaf samples; diagnostics remain enabled.
- Original integrated gate6/7: decrease counted two mentions on one INFO ClosedChannelException after Stopping!. Shared ordered classifier fixes the discrepancy with existing FPS rules; all7 retained logs replay clean. New regression reproduced old failure,4 classifier +13 validation tests pass, preserving earlier/errors/panics/dependency rejection. Original failed report retained; fresh affected transition passes with exactb729native and source/Frozen/prompt/cleanup guards. One additional completed copy retired. No light-map publication yet.

## Earlier foundations and open risks

- `8dab8e575`: live light owners; `ee34f2ad9`:54-lease bulk terrain-light consumer. Subsystem guides retain historical suites/lifecycle/pairs/performance failures; those results do not verify this new map code.
- Integrated d9d1a9d6 ordinary gameplay: C/F median loopFPS entry535/555, standing619/601, travel634/623; p99ms6.740/5.904,2.625/2.911,3.000/2.904.17 Current vs2 Frozen frames>50ms; entry/travel performance open.
- That travel covered only16.45 blocks before a barrier. Entry terrain/minimap incompleteness differs; images not time registered. It does not prove sustained streaming, pop-in or flicker absence.
- Item/entity retained payload candidate under build passes8 tests/576 Frozen encoder cases; native wiring/reload/foil/runtime pending. Prioritize world-state work first.
- Remaining Java light orchestration includes section status, queued entries, retained columns, checkNode, inconsistency processing and publication scheduling.
- Broad gameplay, terrain pop-in/flicker and first shader world-frame proof, long-session host/GPU memory and realistic performance remain open. Settled coast pairs alone cannot establish completion.

## Evidence and storage

- Preserve failed/incomplete/crashed/symlink/unproven fixtures. Retire generated copies only after terminal receipts and owned-process identity checks; keep compact evidence.
- Bulk pruning recovered~350 GiB; routine retirement continues. About243 GiB free before final map validation. See `docs/development/rendering/ARTIFACT-STORAGE.md`.
- Clean FPS windows exclude builds/profilers/other games; require two repeats per side/mode, exact6,000 frames, VUID0, averageFPS≥Frozen and p99≤Frozen for full performance acceptance.

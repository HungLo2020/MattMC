# Rust migration working record

## Goal and constraints

- ACTIVE: remove all Java/JVM; one Rust library and executable with client/server modes. Correctness/performance must match or improve on unchanged Frozen OpenGL.
- Work on master in this checkout; publish verified vertical slices incrementally. New crates require approval. Performance gaps guide further Rust ownership work.
- Frozen: ../MattMC_JavaPerfTesting/MattMC, HEAD7a4d181717dc0c7da9086f1a17687dfd522ce417. Never change its behavior or use Java Vulkan as a reference.
- Rust/GAL owns GPU resources, ordering and presentation; one presenter. Transitional bridges carry bounded semantic CPU inputs.
- Protect docs/STANDARD-COPILOT-PROMPTS.md (SHA c5695e9b). SUMMARY<=10 lines; PROGRESS<=100; ASSUMPTIONS stays concise.
- Practical developer guides belong beside subsystems; verification drivers in DevUtils/tests/. Run Wiki check after doc changes.

## Current ownership and remaining migration

- Rust owns renderer/GAL/presentation, terrain assembly/visibility, canonical block/biome sections, light fields and biome color windows.
- Rust owns DH conversion, materials/topology/packets, visibility, full/render points and full-data source columns/metadata/footprints/updates. Java retains material wrappers, flags/timestamps, compression/DTOs and generation/retrieval orchestration; closed-source hashes still fold escaped readers in Java.
- Rust client residency/ranges own loaded-chunk slots, coordinates and selection; Java binds behavior objects and delivers events.
- Rust entity geometry/removal/camera membership, ticking order, visible ID/UUID tracking, section membership/index/range scans and manager residency serve production client/server consumers.
- Java retains entity simulation, behavior callbacks, visibility-transition orchestration, persistence I/O and the loading inbox.
- Rust owns numerical fog policy, water transitions and rain history. Java retains ordered getters, environment dispatch/hooks and final scalar projections.
- Rust owns atlases/model baking, ordinary GUI item templates and the star-field producer. Java retains font/layout and remaining frame-input extraction/orchestration.
- General simulation, networking and startup/platform remain Java. src/main/rust/app/mod.rs is empty; Java removal is far from complete.

## Current implementation batch
- Native entity collection (verified checkpoint): Rust batches stock NO_SPECTATORS queries across sections; custom bounds/spectator callbacks retain their original iterator boundary. Guide: [entity collection](docs/development/world/entities/RUST-ENTITY-COLLECTION.md).
- Native6ba548a6 passes full Rust2783/3ignored, Java2126/2skip and exact Frozen6886 cases. The new range-failure case reproduced and fixed a lost result prefix; failure evidence retained.
- Final component all-hit CPU41.84/67.65/26.31ms Current/previous consumer/Frozen per4096 queries; Java heap6.52/18.78/18.87MB. Current CPU improves38.2% against its previous consumer, but Frozen remains faster. Dense-section CPU1% slower than control despite74% lower Java allocation.
- All four6000-frame FPS/p99 pairs pass scoped floors (SUMMARY); two settled pairs/three lifecycles pass,VUID0. Source/class/native/Frozen/input/JVM guards pass; seven originals/five presenter/nine native identities reviewed. Hazy gates remain structural only. Thirteen completed copies retired; lossless model-profile JSON archival reclaimed110536497B, original JFRs retained. Evidence: build/native-entity-collection/; broader migration remains unfinished.
- Model frames (verified checkpoint): Rust now owns pose storage, instance construction and immutable variants; Java pose ring/full records removed. Rust 2779 and Java 2121 tests passed, followed by 24 focused tests on final Java changes. Java allocation falls 68%/73%; component CPU remains 8.8%/25.1% slower than the prior Java control. Four runtime modes completed; vanilla FPS remains 4.2% below Frozen. See [implementation, measurements and limits](docs/development/rendering/RUST-MODEL-FRAMES.md) and SUMMARY.md.

## Earlier DH checkpoints

- Published retained DH sources/codecs/conversion policy remove Java point reconstruction and callback-heavy conversion. Detailed results, failures and limits remain in rendering/RUST-DH-RENDER-SOURCE.md and RUST-DH-CONVERSION.md.
- Final conversion policy CPU50.29/64.69ms Current/Frozen on24 saved sources; Java heap5.85/29.85MB. Rust2736/Java2058 pass; seven lifecycle/three settled pairs pass. These historical results do not verify later candidates.
- Historical full gameplay matrix: Current/Frozen FPS1322.30/1231.60,796.85/873.55,347.40/314.85,264.00/226.95. DH FPS misses8.8%; other FPS/all p99 floors pass. Failed DH startup peaked13.25GiB RSS with no measured frames; retain that failure and profiles.

## Live biome query checkpoint

- Rust owns fiddled-distance selection and retained client biome reads. Historical CPU76.07/216.02ms Current/Frozen over1048576 queries; Java heap0 on both sides. Frozen162701 cases, Rust2737/Java2060 and scoped runtime checks passed on native487cebcc. Full evidence and limits: [live biome guide](docs/development/world/biome/RUST-LIVE-BIOMES.md).

## Entity range-query checkpoint

- Rust scans retained membership/primary bounds for ordinary untyped and stock-class queries; Java receives bounded match batches. Geometry/membership revisions preserve mutations between callbacks; custom bounds/types retain ordered callbacks.
- Integrated upstream5f7b28a97 without conflict; native066c6679 passes Rust2743/3ignored, Java2068/2skip and Frozen6403 cases with source/class/native guards. Default-hash class-map order failure retained; matched class-hash oracle controls that input.
- CPU32768 queries over256 members: no-hit15.57/46.37/6.70ms and all-hit57.44/58.46/14.68ms Current/Java-scan-control/Frozen. No-hit CPU improves66.4% versus control; small sections regress and Frozen stays faster. Warmed Java allocation0; not a total-memory bound.
- Two vanilla6000-frame pairs:1280.8/1326.8 and1357.1/1350.8FPS. Medians1318.95/1338.80FPS,p993.335/3.605ms Current/Frozen: FPS misses1.5%,p99 passes. Equal saved inputs/settings/heap/GC/JIT; slightly different live entity counts. No all-mode acceptance or isolated gameplay gain.
- Two settled pairs/three lifecycles pass,VUID0; seven originals/five presenter identities reviewed. Hazy lifecycle views prove structural behavior only. Retired nine completed copied worlds; originals/failures retained. Guide: world/entities/RUST-ENTITY-SECTIONS.md; receipts: build/native-entity-range/.

## Combined world-query checkpoint

- Rust owns section visibility and joins untyped coordinate/member scans, skipping empty/hidden/nonmatching sections without a Java round trip. Retained dense slots and bounded first batches preserve callbacks, generations and lease lifetime; no new crate.
- Nativea554bfbc passes full Rust2746/3ignored and Java2070/2skip, plus Frozen6663 membership/world and6721 index cases. Initial null-consumer text and failed-removal exception mismatches fixed; failures retained.
- CPU4096queries,256 one-member sections: no-hit34.94/57.20/21.88ms and all-hit60.29/60.31/22.78ms Current/Java-traversal-control/Frozen. No-hit38.9% better control, all-hit equal; Frozen CPU gap stays open. Warmed Current Java allocation0. Initial all-hit regression/profile and subsequent candidates retained in build/native-world-entity-query/.
- Four clean6000-frame vanilla windows give Current/Frozen medians1235.60/1206.95FPS,p993.606/3.487ms: FPS passes,p99 misses3.4%. Inputs/JVM controls match; live entity counts188/186 vs180/175. Two settled pairs/three lifecycles pass,VUID0; seven originals/five presenter/seven native identities reviewed. Hazy gate views prove structural behavior only. Retired nine copied worlds. Guide: world/entities/RUST-ENTITY-SECTION-INDEX.md#world-queries.

## Camera environment checkpoint

- Fresh paired vanilla JFRs at04cde9ff5 complete with guards; camera fluid checks account for248MB sampled allocation weight, not a rate or retained memory. Evidence: build/native-world-state-profile/paired/.
- Rust now queries retained client block owners/counters and computes near-plane immersion. Java supplies pose/projection/tag semantics; no per-query cell arrays or copied block data. Palette replacement and mutable array escape retire old query bindings.
- Pre-integration natived885e723 passes49152 actual Frozen camera/world cases and4 lazy/reset controls; native admission and source/class/native guards pass. Full Rust2750/3ignored and Java2080/2skip pass, including ten camera boundary tests. Final Java-source oracle/class/native guards pass; four vanilla6000-frame runs clean; two settled pairs/three lifecycles pass, VUID0.
- CPU1048576queries:121.10/124.87ms Current/Frozen (3.0% less); Java heap0/319.29MB. Initial187.57ms candidate failed; tag/cell reuse, one native query and immutable projection inputs removed repeated work. Superseded two-call API removed; all attempts retained.
- Vanilla Current/Frozen medians1298.60/1264.80FPS,p993.543/3.679ms; both scoped floors pass. Two pairs are not an isolated gain or all-mode acceptance. Inputs/JVM controls/source/native guards pass; seven originals/five presenter/seven native identities reviewed. Hazy lifecycle views prove structural behavior only. Nine completed copied worlds retired.
- Separate30s gameplay JFR samples new camera/native path; no camera allocation samples after first-thread events excluded (not a zero-allocation proof). Profile timings excluded; originals retained.
- Integrated upstreamde7d3572f, preserving scoped/batched release and stale-binding retirement. Combined native971d56d8 passes Rust2750/3ignored, Java2087/2skip, five harness checks and Frozen49152+4. Fresh CPU121.58/127.65ms (4.8% less), Java heap0/319.29MB; one fresh vanilla pair1273.0/1330.2FPS,p993.395/3.298ms misses4.3%/2.9%. No isolated gameplay gain; shader+DH world-reload passes,VUID0; integrated guards/original/presenter checks pass, three copied worlds retired.
- Documentation: rendering/RUST-CAMERA-ENVIRONMENT.md; evidence: build/native-camera-environment/. No gameplay gain claimed. Hash-verified profile JSON compression reclaimed640429745bytes; two completed copied worlds retired, JFR originals retained.

## Collision traversal checkpoint

- Published camera056582a23 and collisionefe94ff71; live origin/master and clean worktree verified after publication.
- Rust now owns retained-section traversal, static grid intersections and contextual face/edge exclusion. Java retains required contextual calls/result projection; no cell export or result batch across callbacks.
- Frozen231212 cases pass on native2669a647 with current Java lazy query shapes, Java11 and Rust world325. Original-grid retention fixes the top-slab epsilon mismatch; failed versions/JIT timings remain recorded.
- Latest CPU80.41/80.11ms Current/Frozen per65536 queries is comparable (+0.4%); Java heap34.25/75.23MB (54.5% less). Native border exclusion, fixed-width reads and lazy Java query shapes remove most scan-only overhead. No gameplay gain claimed.
- Full check passes Rust2757/3ignored and Java2098/2skip; Oracle10 source/class/native guards and compiler-quiet samples pass. Four gameplay windows give1274.4/1347.7FPS,p993.310/3.498ms: FPS misses5.4%,p99 passes. Inputs/JVM controls match; live entities190/189/185/188.
- Two settled pairs/three lifecycle cases pass,VUID0; seven originals/five presenter/seven native identities reviewed. Hazy gates are structural only. Nine copied worlds retired; originals/failures retained.
- Integrated upstream8e7c5e496 (Rust-owned DH full-data sources/updates), preserving all22 local paths byte-for-byte without conflicts/stash. Combined native2ad8899a passes Rust2759/3ignored, Java2095/2skip and Frozen231212; CPU80.61/79.93ms, Java heap34.25/75.23MB. Fresh vanilla/DH pairs pass:1331.2/1262.8 and794.2/663.6FPS,p993.347/4.006 and4.334/7.003ms. Single pairs, entities186/192 and167/166; no isolated gain. Shader+DH pair/three lifecycles pass,VUID0; five originals/four presenter/six native identities reviewed; eight copies retired. native2669 receipts above stay pre-integration.
- Upstream documents deliberate DH downsample alias/null differences from Frozen; preserve them and do not count those cases as exact Frozen parity.
- Guide: world/physics/RUST-BLOCK-COLLISIONS.md. Drivers: DevUtils/tests/world/chunk/VerifyBlockCollisionScan.*; receipts: build/native-collision-scan/.

## Next work and open acceptance

- Continue migrating performance-relevant world-state producers with their consumers, removing Java packing followed by Rust reconstruction. Finish and publish each verified batch before starting another implementation batch.
- Published typed queries2701a9725: Rust selects retained stock class groups within coordinate traversal; Java retains admission and callbacks. Nativea9928d75 passes Rust2764/Java2102/Frozen6735. Empty groups23.37/25.16ms Current/Frozen; misses35.00/34.22, all-hit63.71/35.88; Java heap0, remaining CPU gaps explicit. Guide: world/entities/RUST-ENTITY-SECTION-INDEX.md.
- Random ticks nativee6a65319: Rust owns primary position sequence and callback-bounded chunk selection over live primary leases; Java retains weather/behavior. Read-only packet/light/save consumers stop escaping arrays. Full Rust2769/3ignored, Java2110/2skip and Frozen600 pass. Sparse/1-in-16/dense CPU1.31/1.56/5.41ms vs Frozen1.73/1.82/3.08; prior-Current control2.39/2.42/3.92. Sparse Java heap1716B/7.08MB; dense38% slower prior Current/76% slower Frozen remains open. Guide: world/chunk/RUST-RANDOM-TICKS.md; build/native-random-ticks/ retains failed candidates and final oracle-5.
- Random-tick runtime: four6000-frame mode pairs pass scoped FPS/p99 floors (RUST-RANDOM-TICKS.md); two settled pairs/three lifecycles pass,VUID0. Source/class/native/Frozen/input/JVM guards pass; entities186/180,181/181,167/167,165/166. Seven originals/five presenter/nine native identities reviewed;13 copied worlds retired. Hazy gates prove structural behavior only. Fresh paired30s JFRs at2701a9725 identified56MB sampled server random-position allocation/41 palette-get leaves, not rates; build/native-frame-state-profile/paired/ retains originals,2 world copies retired and599145590B reclaimed by lossless JSON compression.
- Asset registry native de0fabcc: Rust owns mesh/texture numeric membership, slots, ordering and journals; stable rig admission reads dependency revisions natively. Java retains immutable payload references and upload/instance producers. Removed Java MembershipJournaledMap. Native6/Java18 and replay200000 pass on de0fabcc; production admission included. CPU stable1.44/3.01ms, aliases1.43/14.98, churn4.24/4.20 vs previous Current; stable/aliases Java heap0, churn5.32/23.22MB. Full Rust2775/3ignored and Java2115/2skip pass; observer initialization failure retained in oracle-2, fixed oracle-3 passes guards. Profile evidence: native-frame-state-profile/targets.json. Guide: rendering/RUST-ASSET-REGISTRY.md; logs: build/native-asset-registry-*.log.
- Asset-registry runtime: four6000-frame Current/Frozen pairs (RUST-ASSET-REGISTRY.md); vanilla FPS misses3.9%, other FPS/all p99 floors pass, not an isolated gain. Two settled pairs/three lifecycles pass,VUID0; source/class/native/Frozen/input/JVM guards pass. Entities191/184,183/180,167/165,166/165. Seven originals/five presenter/nine native identities reviewed;13 copies retired. Hazy gates prove structural behavior only; diagnostic captures disable rigs, ordinary gameplay enables them. Receipts: build/native-asset-registry/.
- Published mesh lifetime e0cf94984 moves usage, ordered retirement and rollback into Rust; rigs touch retained identities directly. Stable-rig CPU21% lower vs prior Java, changing rigs22% slower and scalar paths slower; Java asset wrappers/deliveries and entity simulation remain. Guide: rendering/RUST-MESH-LIFETIME.md.
- First shader world frame, first-turn nearby terrain pop-in/flicker, sustained streaming and long host/GPU memory remain unproved. Settled images do not resolve transient gaps.
- Broad gameplay remains open: transparency, particles/weather, entities/items/hands, GUI/HUD, resource packs, representative shaders and DH combinations. The latest rotating workload hides the minimap.
- Rain foreground visibility and Current-only waypoint chat differ despite initial-image tolerance; whole weather/HUD parity is not established.
- Strict leash width differs; predecessor reproduces it. Some historical reload/cow fixtures omit Current crosshair; the latest vanilla coast shows it. That pose does not close all HUD defects.
- Strict height-world teardown/closed-channel race and parallel Rust GLX BadAccess/poison remain unresolved; serial success does not fix them. The historical driver mismatch is resolved.

## Preservation and retention

- Protected stashes:12345bcea835a6969b164f1a358ecd44a5bb22ab andc979e5de03a6682327a293fec0fc5150eaacf0bb. No workflow stash needed.
- Explicitly stage reviewed paths; never force-push/reset/blanket-stage. Preserve incoming and local work together on upstream master.
- Prune only recognized completed copies after clients exit; preserve originals/profiles/failures/unknown or active scopes and open-defect worlds. Never clean during timing. Fresh paired model JFRs retained: two completed worlds retired; verified lossless JSON compression reclaimed641680011bytes.
- Prior manifests record large cache/archive/video retirement. Latest point-codec cleanup retired21 completed copies/2.29GiB while preserving1550 selected evidence hashes and all originals/profiles/failures.
- Metadata/IO cleanup retired29 completed copies, reclaiming3.11GiB;1387 selected evidence hashes unchanged. Interrupted final DH scope, all originals/profiles/per-frame diagnostics and protected inputs remain.
- Light-input cleanup retired21 completed copies/2.29GiB with1548 selected evidence hashes unchanged; all originals/profiles/per-frame diagnostics and failed RSS/legacy-shutdown scopes remain.

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

## Recent published checkpoints

- Integrated upstream5f7b28a97 (DH sky light, saved contributor batches, fresh chunk slabs) preserving all19 entity-query paths; combined Rust2743/Java2068 pass. Earlier: f1de16e11 (sky/id-map/GUI) and7c49a0a21 (batched native section reclamation, pressure collections, DH creation-site reductions). Also integrated2b979072d test tiers/affected runner, preserving all27 pending files; no stash needed.

## Earlier DH checkpoints

- Published retained DH sources/codecs/conversion policy remove Java point reconstruction and callback-heavy conversion. Detailed results, failures and limits remain in rendering/RUST-DH-RENDER-SOURCE.md and RUST-DH-CONVERSION.md.
- Final conversion policy CPU50.29/64.69ms Current/Frozen on24 saved sources; Java heap5.85/29.85MB. Rust2736/Java2058 pass; seven lifecycle/three settled pairs pass. These historical results do not verify later candidates.
- Historical full gameplay matrix: Current/Frozen FPS1322.30/1231.60,796.85/873.55,347.40/314.85,264.00/226.95. DH FPS misses8.8%; other FPS/all p99 floors pass. Failed DH startup peaked13.25GiB RSS with no measured frames; retain that failure and profiles.

## Live biome query checkpoint

- Native487cebcc moves ordinary fiddled-distance selection and client biome identity reads into Rust's retained sections; Java resolves holders and dispatches unsupported/custom sources. No Java selection loop or section export remains on the admitted path.
- Frozen162701 coordinate cases, Java8/native9 boundary tests, full Rust2737/3ignored and Java2060/2skip pass. Scalar selection1048576queries:76.07/216.02ms Current/Frozen,64.8% faster, zero Java heap on both sides; excludes world lookup/GPU.
- One clean6000-frame vanilla pair:1320.6/1068.7FPS and p993.736/4.428ms Current/Frozen. Two settled image pairs and three lifecycle cases pass,VUID0; seven originals/five presenter identities/six native images reviewed. Gate haze establishes structural evidence only; no new all-mode acceptance.
- Sources/native/Frozen guards pass. Retired nine completed world copies; lossless profile JSON archival reclaimed625594176bytes, originals retained. Guide: world/biome/RUST-LIVE-BIOMES.md; receipts: build/native-biome-query/.

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
- Typed world-query slice: Rust selects retained stock class groups and scans primary bounds during coordinate traversal; Java retains lazy class admission, descriptors and custom/consumer callbacks. No per-query candidate list or new crate.
- Nativea9928d75 passes Rust2764/3ignored, Java2102/2skip, Frozen6735 cases and all source/class/native guards. Compiler-quiet oracle-3:256-section empty23.37/45.04/25.16ms, misses35.00/73.83/34.22ms, all-hit63.71/79.01/35.88ms Current/prior Java/Frozen; Current Java heap0. Empty-group lock skip improves30% over candidate96109; all-hit/small-section Frozen gaps remain.
- Four fresh6000-frame pairs give Current/Frozen FPS1241.4/1095.0 vanilla,354.9/300.0 shaders,801.0/693.5 DH,278.9/227.5 both; all scoped FPS/p99 floors pass. Inputs/JVM controls match; live entities190/190,180/178,165/166,164/166. Single pairs, no isolated/sustained gain.
- Two settled pairs/three lifecycles pass,VUID0; seven originals/five presenter/nine native identities reviewed; hazy gates are structural only. Thirteen completed copies retired with originals unchanged. Guide: world/entities/RUST-ENTITY-SECTION-INDEX.md; receipts: build/native-typed-section/. Initial JIT-contaminated timing retained.
- Published mesh lifetime e0cf94984 moves usage, ordered retirement and rollback into Rust; rigs touch retained identities directly. Stable-rig CPU21% lower vs prior Java, changing rigs22% slower and scalar paths slower; Java asset wrappers/deliveries and entity simulation remain. Guide: rendering/RUST-MESH-LIFETIME.md.
- Historical nativeec10875b passed Rust2763/Java2100/replay200000, four gameplay pairs, two settled pairs and three lifecycles;13 copies retired. Captures disable rigs; ordinary gameplay enables them. Profile73MB is sampled allocation weight. Receipts: build/native-mesh-lifetime/.
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

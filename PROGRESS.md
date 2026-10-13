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
- Rust owns DH conversion policy/temporary points/color reuse, materials/topology/packets, retained visibility, primary full/render points and dense metadata, and saved-point/metadata payload policy. Java retains compression, DTO/material/contributor orchestration and generation/retrieval scheduling.
- Rust client residency/ranges own loaded-chunk slots, coordinates and selection; Java binds behavior objects and delivers events.
- Rust entity geometry/removal/camera membership, ticking order, visible ID/UUID tracking, section membership/index/range scans and manager residency serve production client/server consumers.
- Java retains entity simulation, behavior callbacks, visibility transitions, persistence I/O and the loading inbox.
- Rust owns numerical fog policy, water transitions and rain history. Java retains ordered getters, environment dispatch/hooks and final scalar projections.
- Rust owns atlases/model baking, ordinary GUI item templates and the star-field producer. Java retains font/layout and remaining frame-input extraction/orchestration.
- General simulation, networking and startup/platform remain Java. src/main/rust/app/mod.rs is empty; Java removal is far from complete.

## Recent published checkpoints

- ae7f499b3: entity UUID/chunk residency and persistence admission. 1a977b9db: fog numerical policy.
- 61805d6dc: retained light queues/sky seeds. 8e16a569c: direct block-light inputs and bulk DH column resizing. 7acbefd0f: matched performance/memory reports.
- b6a0f89ca: primary Rust DH point codecs and lazy closed-source readers. Real24-DTO hydration improved29.4%; historical matrix still missed the DH FPS floor5.8%.
- Integrated upstream5f7b28a97 (DH sky light, saved contributor batches, fresh chunk slabs) preserving all19 entity-query paths; combined Rust2743/Java2068 pass. Earlier: f1de16e11 (sky/id-map/GUI) and7c49a0a21 (batched native section reclamation, pressure collections, DH creation-site reductions). Also integrated2b979072d test tiers/affected runner, preserving all27 pending files; no stash needed.
- Historical matrices and limitations remain in rendering/GAMEPLAY-PERFORMANCE.md and the relevant world/lighting/chunk guides; they do not verify newer sources.

## Earlier evidence

- Historical metadata/IO results and retained failures: world/chunk/RUST-DH-METADATA-CODEC.md. Direct light-section inputs and their CPU/runtime results: world/lighting/RUST-LIGHT-SECTION-INPUTS.md and rendering/GAMEPLAY-PERFORMANCE.md. Those older binaries do not verify current sources.

## Render-source storage checkpoint

- Rust owns primary DH render points and lazy dense semantic planes; reducer/box consumers borrow those spans directly. Java retains contextual callbacks, sparse bindings and traversal.
- Conversion failures close partially built owners; scoped views invalidate safely on close. Frozen packed-height edge behavior is preserved.
- Final full suites pass Rust2728/3ignored and Java2046/2skip. Complete saved-world point/geometry oracles match Frozen and predecessor; all guards pass. Seven lifecycle and three settled pairs pass,VUID0;13 originals reviewed.
- Interior boxes now pass one scoped CPU source owner; Rust resolves/validates four windows, removing Java span construction and repeated source-scope acquisition.
- CPU build Current/predecessor/Frozen321.42/313.36/657.63ms. Current is51.1% faster Frozen but2.6% slower predecessor. Heap allocation37.6/315.4/156.2MB per24-source batch;88.1% lower predecessor, not a total-memory bound.
- Retired unused Rust caches after checking no live references:36.45GiB reclaimed; lossless archival of19 completed historical audit JSONs saved2.14GiB. Controls, originals and failures retained.
- Current DH attempt2 failed during startup:RSS13.25GiB,0 measured frames, DH still generating. Earlier measured DH run821FPS,p99 2.718ms,peak9.00GiB. Failure retained; continuation completed the missing measured pair with unchanged guard.
- Sixteen completed6000-frame windows plus one failed startup retained. Current/Frozen FPS1322.30/1231.60,796.85/873.55,347.40/314.85,264.00/226.95. DH FPS misses8.8%; other FPS/all p99 floors pass. See SUMMARY/table guide.
- Game-only JFR/NMT diagnostic completes9.52GiB, not a peak reproduction.6,858 allocation samples identify empty/mutable DH column wrappers and render views; allocation weights do not prove retention. Preserve failure and profile.

## Conversion-reader checkpoint

- Replaced ordinary per-column Java identity projection during render conversion with one scoped Rust column lease; custom/escaped inputs retain their public path. Production callbacks/arithmetic still Java.
- Two new Rust and23 focused Java tests pass, including nested readers, source replacement/closure and no persistent Java bindings.
- First24-saved-source hydration/read/close comparison: Current57.89ms, public-reader control56.24ms, Frozen44.04ms; exact point checksums and source/class/native/corpus guards pass. Java allocation7.14/33.85/4.21MB. CPU floor fails; original attempt retained.
- Off-heap scalar result candidate56.59/57.20/46.14ms (Current/public/Frozen), heap7.14/33.85/4.21MB. CPU still22.6% slower Frozen;78.9% less Java allocation than control. Full Java/parity2049/2skip and51 Rust DH tests pass. Three settled pairs pass,VUID0;6 originals reviewed.
- Removed direct-span experiment:55.84ms but15.00MB heap and broader invalidation machinery. Source hashes exactly match simpler candidate. Receipts/predecessor: build/native-dh-conversion-reader/. SUMMARY describes published a9286e05, not this candidate.
- Ordinary paired gameplay failed: Current lost focus before travel; Frozen observer failed reflection before timing. Preserved both, added full error stacks; short Frozen diagnostic passes but does not reproduce/explain failure. No accepted new FPS. World unload/reload and resource reload pass with zero failures.

## Conversion-policy checkpoint

- 0a7d1f4af published the scoped reader; this batch migrates the actual top-down conversion loop into Rust. No Java loop fallback; contextual callbacks and semantic publication remain transitional Java.
- Initial prototype matches896 Frozen color-only and896 preceding semantic output/callback/failure-prefix cases. Saved-geometry CPU174.70/38.61/33.12ms(candidate/preceding/Frozen) rejects its callback-heavy boundary; no performance acceptance or publication.
- Native1fcb8c8c reads retained columns/writes render windows directly and derives intrinsic facts in Rust. Callback-frame reuse removes allocation churn. Final10 oracle runs pass896cases each; guarded CPU66.53/39.85/22.00ms(candidate/preceding/Frozen), heap63.39/48.45/42.93MB still FAIL. Java/parity2053/2skip,61 focused Rust tests and Wiki pass. No runtime acceptance. Guide: rendering/RUST-DH-CONVERSION.md.
- Native561b048e: Rust policy/untinted-color reuse/reload invalidation/native overflow scratch. Full-source CPU50.29/63.57/64.69ms(Current/preceding/Frozen), heap5.85/34.23/29.85MB;22.3% faster Frozen, outputs/10oracles/guards pass. Rust2736/3ignored, Java2058/2skip pass. Seven lifecycle and3settled Frozen pairs pass,VUID0;13 originals/10 Current identities reviewed. No new FPS acceptance. Integrated test/parity/slow tiers pass1863/184/11,2skip; Wiki passes.
- Retired3 older copied worlds/308773838bytes and the runtime harness retired10 new completed copies; originals, failures and receipts retained. Receipt: build/native-dh-conversion-policy/retention.json.

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

## Next work and open acceptance

- Continue migrating performance-relevant world-state producers with their consumers, removing Java packing followed by Rust reconstruction. Finish and publish each verified batch before starting another implementation batch.
- Next entity step: join native coordinate traversal, visibility and member scans to amortize small-section boundary cost; preserve live callbacks and cursor lifetimes.
- First shader world frame, first-turn nearby terrain pop-in/flicker, sustained streaming and long host/GPU memory remain unproved. Settled images do not resolve transient gaps.
- Broad gameplay remains open: transparency, particles/weather, entities/items/hands, GUI/HUD, resource packs, representative shaders and DH combinations. The latest rotating workload hides the minimap.
- Rain foreground visibility and Current-only waypoint chat differ despite initial-image tolerance; whole weather/HUD parity is not established.
- Strict leash width differs; predecessor reproduces it. Some historical reload/cow fixtures omit Current crosshair; the latest vanilla coast shows it. That pose does not close all HUD defects.
- Strict height-world teardown/closed-channel race and parallel Rust GLX BadAccess/poison remain unresolved; serial success does not fix them. The historical driver mismatch is resolved.

## Preservation and retention

- Protected stashes:12345bcea835a6969b164f1a358ecd44a5bb22ab andc979e5de03a6682327a293fec0fc5150eaacf0bb. No workflow stash needed.
- Explicitly stage reviewed paths; never force-push/reset/blanket-stage. Preserve incoming and local work together on upstream master.
- Prune only recognized completed generated copies after clients exit. Preserve measurements/originals/profiles, failures, unknown/active/symlinked scopes, prepared inputs and open-defect worlds. Never clean during timing.
- Prior manifests record large cache/archive/video retirement. Latest point-codec cleanup retired21 completed copies/2.29GiB while preserving1550 selected evidence hashes and all originals/profiles/failures.
- Metadata/IO cleanup retired29 completed copies, reclaiming3.11GiB;1387 selected evidence hashes unchanged. Interrupted final DH scope, all originals/profiles/per-frame diagnostics and protected inputs remain.
- Light-input cleanup retired21 completed copies/2.29GiB with1548 selected evidence hashes unchanged; all originals/profiles/per-frame diagnostics and failed RSS/legacy-shutdown scopes remain.

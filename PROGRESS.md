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
- Rust owns DH materials/topology/packets, retained visibility, primary points/metadata and saved-point/metadata payload policy. Java retains compression, DTO/material/contributor orchestration and generation/retrieval scheduling.
- Rust client residency/ranges own loaded-chunk slots, coordinates and selection; Java binds behavior objects and delivers events.
- Rust entity geometry/removal/camera membership, ticking order, visible ID/UUID tracking, section membership/index and manager residency serve production client/server consumers.
- Java retains entity simulation, behavior callbacks, visibility transitions, persistence I/O and the loading inbox.
- Rust owns numerical fog policy, water transitions and rain history. Java retains ordered getters, environment dispatch/hooks and final scalar projections.
- Rust owns atlases/model baking, ordinary GUI item templates and the star-field producer. Java retains font/layout and remaining frame-input extraction/orchestration.
- General simulation, networking and startup/platform remain Java. src/main/rust/app/mod.rs is empty; Java removal is far from complete.

## Recent published checkpoints

- ae7f499b3: entity UUID/chunk residency and persistence admission. 1a977b9db: fog numerical policy.
- 61805d6dc: retained light queues/sky seeds. 8e16a569c: direct block-light inputs and bulk DH column resizing. 7acbefd0f: matched performance/memory reports.
- b6a0f89ca: primary Rust DH point codecs and lazy closed-source readers. Real24-DTO hydration improved29.4%; historical matrix still missed the DH FPS floor5.8%.
- Integrated upstream f1de16e11 without conflicts: sky transforms/native stars, identity-map critical calls and GUI allocation changes.
- Historical matrices and limitations remain in rendering/GAMEPLAY-PERFORMANCE.md and the relevant world/lighting/chunk guides; they do not verify newer sources.

## Verified metadata and bulk-IO checkpoint

- Native691c0b95 owns generation/compression/uniformity payload policy against primary planes. Private byte-array streams use bulk input; streaming decompressors preserve error prefixes.
- Initial JFR found162/285 hydration CPU samples in single-byte ByteArrayInputStream.read. The later28-sample profile is too small to quantify a new bottleneck or normalized allocation improvement.
- Actual Frozen exposed short-list backing-capacity/EOF-prefix mismatch. Fixed-payload native consumption and compatibility controls now preserve logical size and partial mutations.
- Full Rust2723/3ignored and Java/parity2043/2skip pass. Actual Frozen606 metadata rows/303 cases and696830 point rows/170 cases match, including48 saved metadata and120 saved point blobs; all guards pass.
- Real24-DTO predecessor/current/Frozen hydration78.73/43.62/41.51ms:44.6% faster predecessor, still5.1% slower Frozen. Checksums/source/native/class/predecessor/corpus guards pass; contextual material resolution omitted.
- Seven lifecycle scenarios and vanilla/DH/Iris+DH settled pairs pass,VUID0. All13 originals/10 Current identities reviewed. Seven heavily hazy gate views establish structural evidence only.
- Controller exited143 after both final DH clients completed clean captures; signal origin unconfirmed. Existing harness recovered the comparison from guarded originals without client rerun. Failure/recovery receipts retained.
- All20 exact6000-frame runs clean; every live JVM control matches before measurement. Current/Frozen FPS1317.80/1264.50,753.75/701.45,348.45/313.65,264.55/225.95; p99ms3.563/3.710,3.992/6.264,4.679/7.220,6.270/8.842.
- All FPS/p99 floors PASS in this measured scope. Integrated upstream sky/id-map/GUI changes are included; no isolated metadata whole-game gain claimed. DH RSS8.83–9.40GiB,combined9.00/9.15GiB below guard; long memory remains open.
- Final source/native/class/Frozen/prompt, three prepared-input and saved-corpus guards pass; no clients remain. Guide: world/chunk/RUST-DH-METADATA-CODEC.md. Receipts: build/native-dh-metadata-codec/.

## Next work and open acceptance

- Continue migrating performance-relevant world-state producers with their consumers, removing Java packing followed by Rust reconstruction. Finish and publish each verified batch before starting another implementation batch.
- Profile remaining IO/scratch ownership and node/world-state producer costs with adequate samples. Keep CPU hydration gains separate from whole-game measurements.
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

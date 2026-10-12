# Rust migration working record

## Goal and constraints

- ACTIVE: remove all Java/JVM; one Rust library and executable with client/server modes. Final correctness/performance must match or improve on Frozen.
- Work on master in this checkout; publish verified vertical slices incrementally. New crates require approval. Performance gaps guide further Rust migration.
- Sole reference: unchanged Frozen OpenGL at ../MattMC_JavaPerfTesting/MattMC, HEAD7a4d181717dc0c7da9086f1a17687dfd522ce417.
- Rust/GAL owns GPU resources, ordering and presentation; one presenter. Transitional bridges carry bounded semantic CPU inputs.
- Protect docs/STANDARD-COPILOT-PROMPTS.md (SHA c5695e9b). SUMMARY<=10 lines; PROGRESS<=100; ASSUMPTIONS stays concise.
- Developer guides belong beside subsystems; drivers in DevUtils/tests/. Run Wiki check after documentation changes.

## Current ownership and remaining migration

- Rust owns renderer/GAL/presentation, terrain assembly/visibility, canonical block/biome sections, light fields and biome color windows.
- Rust owns DH materials/topology/packets and retained visibility; Java retains generation/retrieval scheduling and its generation tree.
- Rust client residency/ranges own loaded-chunk slots, coordinates and selection; Java binds behavior objects and delivers events.
- Rust entity geometry/removal/camera membership, ticking order, visible ID/UUID tracking, section membership/index and manager residency serve production client/server consumers.
- Java retains entity simulation, behavior callbacks, section visibility transitions, persistence I/O and the loading inbox.
- The verified fog slice owns built-in numerical color/range policy, shared water transitions and per-environment rain history. Java retains ordered getters, environment priority/virtual dispatch, mutable hooks and final scalar projections.
- Rust owns atlases/model baking and ordinary GUI item templates; Java retains font/layout and remaining frame-input extraction/orchestration.
- General simulation, networking and startup/platform remain Java. src/main/rust/app/mod.rs is empty; Java removal is far from complete.

## Published checkpoints

- c1428dbc7: removal/camera retirement and bounded alpha masks. d8e6b8f47: visible tracking and GUI text copies.
- 0cea5de1d: root/typed entity-section membership and retained DH admitted identities. f54c1d754: section-coordinate map/index and spatial queries.
- ae7f499b3: known UUID/chunk residency and persistence admission. Remote master and clean worktree were verified after each publication.
- Historical matrices and qualifications remain in rendering/GAMEPLAY-PERFORMANCE.md; earlier unmatched JVM results do not verify later sources.

## Verified light-work checkpoint

- Nativeca8bf37a owns pending propagation queues, journals inputs without copying and seeds canonical sky work directly. Java retains node checks, section orchestration and callbacks.
- Frozen492 passes/437089 rows, full Rust2711/3ignored and Java2029/2skip pass. Predecessor pairs: terrain1.0%/nether7.5% faster, edits3.1% slower; the edit cost needs further producer/consumer work.
- Seven lifecycle/two settled pairs and matching six-column flights pass,VUID0;17 originals reviewed/9 identities match. Hazy lifecycle views do not establish world-entry parity; endpoints do not resolve transient gaps.
- All20 matched6000-frame timings clean; final source/native/class/Frozen/prompt/3input guards pass; no clients remain. Current/Frozen FPS:1286.70/1222.20,784.95/836.05,341.00/305.65,246.70/217.60. P99ms:3.597/3.564,3.251/4.907,4.986/8.140,7.258/10.453.
- Aggregate FAIL: vanilla p99 misses0.93%, DH FPS6.11%; other floors pass. No isolated whole-game gain claimed. Guide: world/lighting/RUST-LIGHT-WORK.md; receipts: build/native-light-work/.
- Release full-test debug-only cache assertion and capture-terminated Java parity attempt retained; supported Rust suite and sequential Java parity pass. Run Java suites separately from capture cleanup.

## Published block-light and DH resize checkpoint (8e16a569c)

- Remote master and clean worktree verified after publication. Native3c8e2d3d enumerates retained section owners directly into native work; shared adapter renamed NativeChunkInputs. Rust bulk resize removes per-point DH zero append/view construction.
- Rust2714/3ignored, Java2032/2skip and actual Frozen492/437089/54 native seeds pass. Seven lifecycle/two settled pairs pass,VUID0;11 originals/9 native identities reviewed. Hazy lifecycle views establish structural checks only. Six-column flights reach matching F3 poses, including shared return overshoot;6 originals reviewed.
- Source-only8aaa5520 predecessor pairs: terrain12.6%/nether18.2% faster, edits unchanged. First DH matrix failed RSS~12.4GiB/0 measured frames. Game-only JFR identifies DH hydration/zero-appends; weighted add/view-refresh allocation922->158MB after resize. Variable-work profiles do not isolate whole-game gains or retained-memory bounds. Failed attempts retained.
- All20 exact6000-frame matched timings clean; every live JVM control identical; source/native/class/Frozen/prompt/3input guards pass; no clients remain. Current/Frozen FPS:1312.65/1208.70,742.00/808.40,351.20/315.50,260.80/217.35. P99ms:3.362/3.540,4.310/5.175,4.834/6.930,6.573/10.419.
- Aggregate FAIL: DH FPS misses8.2%; other FPS/allp99 floors pass. Current DH RSS8.52–9.12GiB,combined9.53/9.92GiB below guard; sustained memory remains open. SUMMARY records this full matrix. Guides: world/lighting/RUST-BLOCK-LIGHT-SOURCES.md and world/chunk/RUST-DH-FULL-DATA.md; receipts: build/native-light-seeds/combined/.

## Next work and open acceptance

- Prioritize DH saved-source point decoding/encoding directly against primary native columns, moving its Java loops and per-point accesses together. Preserve formats, list/alias identity and corruption/exception prefixes; compression wrappers and contextual material resolution remain later work. The allocation profile proves hydration churn, not that this explains the entire FPS gap.
- Continue migrating node/world-state producers with consumers; avoid Java packing followed by Rust reconstruction. Native work queues now replace that transfer in light propagation. Close and publish verified increments before starting another implementation batch.
- Investigate measured native expansion/batching and remaining producer costs; small fog preparation times do not by themselves explain the DH gap.
- First shader world frame, first-turn nearby regular-terrain pop-in/flicker, sustained streaming and long host/GPU memory remain unproved. Settled images do not resolve transient gaps.
- Broad gameplay remains open: transparency, particles/weather, entities/items/hands, GUI/HUD, resource packs, representative shaders and DH combinations.
- Rain foreground coverage/visibility and Current-only waypoint chat differ despite passing initial-image tolerance. Whole weather/HUD parity is not established.
- Strict leash width differs and predecessor control reproduces it. Some static reload/cow fixtures omit Current crosshair; moving/latest vanilla views show it. Body/shadow checks do not accept those defects.
- Strict height-world teardown/closed-channel race and parallel Rust GLX BadAccess/poison remain unresolved; serial success does not fix them. The historical driver mismatch is resolved.

## Preservation and retention

- Protected stashes:12345bcea835a6969b164f1a358ecd44a5bb22ab andc979e5de03a6682327a293fec0fc5150eaacf0bb. No new workflow stash was needed.
- Stage reviewed paths explicitly; never force-push/reset/blanket-stage. Preserve incoming and local work together on upstream master.
- Fog cleanup retired22 completed generated workspaces, preserving184 selected evidence hashes; rejected/failed weather scopes remain. Residency cleanup retired21/preserved151.
- Prior manifests record~350GiB reclaimed caches plus21.08GiB archives,17.04GiB unused profiles and17.83GiB superseded videos with447 PNG/profile receipts retained.
- Retire only recognized completed workspaces after clients exit. Preserve measurements/originals/profiles, failed/unknown/active/symlinked scopes, prepared inputs and open-defect worlds. Never clean during timing.
- Combined light-source cleanup retires24 completed generated workspaces and reclaims2.60GiB. Original images/profiles and the failed source-only RSS recording remain. Selected1585 evidence hashes and the byte-verified full receipt are retained in build/native-light-seeds/combined/; all final guards pass again after cleanup.

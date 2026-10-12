# Rust migration working record

## Goal and constraints

- ACTIVE: remove all Java/JVM; one Rust library and executable with client/server modes. Correctness and performance must match or improve on Frozen before final acceptance.
- Work on master in this checkout; publish verified migration checkpoints incrementally. New crates require approval. Performance gaps guide further migration, not a Java fallback.
- Frozen OpenGL is the sole reference: ../MattMC_JavaPerfTesting/MattMC at 7a4d181717dc0c7da9086f1a17687dfd522ce417. Never modify Frozen.
- Rust/GAL owns GPU resources, ordering and presentation; one presenter. Transitional bridges carry bounded semantic CPU inputs.
- Protect docs/STANDARD-COPILOT-PROMPTS.md (SHA c5695e9b). SUMMARY<=10 lines; PROGRESS<=100; ASSUMPTIONS stays concise.
- Developer guides belong beside subsystems; verification drivers in DevUtils/tests/. Run Wiki check after documentation edits.

## Current ownership and remaining migration

- Rust owns renderer/GAL/presentation, terrain assembly/visibility, block/biome sections, light fields and biome color windows.
- Rust owns DH material/point/metadata policy, topology, packet encoding and retained visibility. Java retains generation/retrieval scheduling and its generation tree.
- Rust client residency/ranges own logical slots, coordinates and ordinary loaded-chunk selection; Java binds behavior objects and callbacks.
- Rust entity geometry/removal/camera membership, ticking order/snapshots and visible ID/UUID tracking serve production client/server consumers.
- Current section slice moves root/typed membership arrays into Rust; Java retains class descriptors/object bindings and callbacks. The coordinate map/index is published as f54c1d754. The subsequent residency slice owns primary UUID/chunk state and admission in Rust with scoped verification; behavior callbacks, section visibility transitions and persistence I/O remain Java.
- Rust owns texture atlases/model baking and ordinary GUI item templates. Java retains font/layout, behavior objects and remaining extraction/orchestration.
- General simulation, networking, startup/platform and remaining frame-input producers are Java. src/main/rust/app/mod.rs is empty; Java removal is far from complete.

## Published predecessors

- c1428dbc7 moves removal/camera retirement into Rust and bounds item alpha masks. d8e6b8f47 moves visible tracking into Rust and streamlines GUI text copies. Both were pushed and remotely verified.
- Prior native9c9f085c reports all20 timing floors passing, Rust2685/3ignored and Java/parity2010/2skip. Its matrix uses older unmatched JVM controls and does not verify current6076262e. Details remain in rendering/GAMEPLAY-PERFORMANCE.md#local-visible-tracking-checkpoint.
- Integrated doc-only6b858d35b with targeted workflow stash4ed93c27; reconciled2 overlapping docs without changing source/native identities. Protected stashes remain untouched.

## Published membership/DH predecessor

- Native6076262e: Rust owns production root/typed arrays, mutation counters and generational binding retirement. Quiet traversal reads native CPU views without rebuilding a Java primary list. Six Rust controls include10k retired-slot reuse cycles.
- Final review repaired a genuine typed-toArray VM-error/destination-prefix mismatch. Expanded actual Frozen membership6123 and manager lookup4417 cases match with source/class/native guards. Earlier failed/pre-repair recordings remain in build/native-entity-sections/.
- Matched15s DH profiles selected accepted-parent intersection reuse, fixed9-root order and count-only admitted frame output. Full diagnostic/exact-atlas APIs preserve keys; admission and ordering remain Rust-owned. Focused Rust25/Java73 and actual Frozen1280 walk/counter replay pass.
- Full suites pass Rust2691/3ignored and Java/parity2018 executed/2skip. Seven lifecycle checks, two world comparisons and cow body/shadow pair pass,VUID0. All14 source originals were individually reviewed; all10 Current correctness native identities match.
- All20 exact6000-frame alternating timings finish cleanly. All10 live heap/GC/JIT controls match; final source/native/Frozen/prompt/3prepared-input guards pass; no clients remain. DH4 repeats per side, other modes2;360settle/240warmup.
- Latest full matrix is in SUMMARY: DH and both shader modes pass both floors; vanilla FPSFAIL0.38% and p99FAIL6.18%. Aggregate performance remains unaccepted. Frozen DH moved substantially; no isolated gain/regression cause is claimed.
- Preceding matched d4348edd matrix fails vanilla p99 by4.25% and DH FPS by18.2%. Older G1/1GiB versus ZGC/4GiB matrix is confounded. Harness now matches effective controls without editing Frozen; real HotSpot/override tests and live observations pass.
- Current vanilla phase receipts show native mesh expansion/grouping around0.1ms median; first repeat is substantially slower than second. Investigate real producer/batching/streaming costs; do not replace failed repeats or change baseline behavior.
- Guides: world/entities/RUST-ENTITY-SECTIONS.md, rendering/RUST-DH-VISIBILITY.md and rendering/GAMEPLAY-PERFORMANCE.md. Current receipts: build/native-entity-sections/dh-summary-candidate/; historical candidates/profile receipts retained separately.
- Publish this correctness-verified migration checkpoint with the measured vanilla miss explicitly open. Next vertical slice: section-coordinate/index/visibility ownership and live frame-input costs; move producers/consumers together, removing duplicate Java packing/Rust reconstruction.

## Published section-coordinate checkpoint

- Production EntitySectionStorage is wired to a native physical hash map/threaded AVL index and coordinate queries. Java retains section behavior bindings and visibility/member callbacks; visibility/persistence orchestration still remains. Scoped runtime checks pass; broader and aggregate performance acceptance remain open.
- Native168dd924 release and13 focused controls pass, including20k randomized AVL mutations/bounded retired-node reuse. Prior75156954 matches actual Frozen6693 with guards and15 Java integration tests pass after adding omitted fixture bootstrap. Spatial key/binding now returned together; numeric/null controls expanded. Full Rust2698/3ignored and Java/parity2022/2skip pass; expanded actual Frozen6721 and manager4417 match with source/class/native guards. Controller49926 completed its7lifecycles/2worldpairs/cow-body-shadow and20 timings; f54c1d754 was pushed/remote verified with a clean worktree. Seven lifecycles/2worldpairs/cow-body-shadow pass,VUID0;14 originals reviewed/10 Current identities match. All20 matched timings finish cleanly/all10 effective controls match/final source+native+Frozen+prompt+3input guards pass/no clients remain. Vanilla FPSFAIL0.75%, DH FPSFAIL6.41%; all p99/shader FPS floors pass. Aggregate performance remains unaccepted; no isolated cause/gain claimed.
- Guide: docs/development/world/entities/RUST-ENTITY-SECTION-INDEX.md; receipts: build/native-section-index/. Its complete168dd924 matrix and open FPS misses remain in rendering/GAMEPLAY-PERFORMANCE.md.

## Verified entity-residency checkpoint

- Production client/server managers now use Rust known-UUID, chunk visibility/load status, unload membership and ticking membership. Initial client visibility and load/store/unload decisions move with their primary state; Java retains behavior callbacks, persistence I/O and the loading inbox.
- Native35b68379 release and18 focused controls pass; actual Frozen residency5264 matches with guards and13 selected Java integration tests pass. Full Rust2703/3ignored and Java/parity2026/2skip pass; refreshed Frozen index6721/lookup4417 also match with guards. Source is fixed; controller74344 has completed7lifecycles/2worldpairs/cow-body-shadow successfully,VUID0. All14 originals reviewed/10 Current identities match. All20 matched timings finish cleanly/every FPS+p99 floor passes/all10 effective VM controls match/final source+native+Frozen+prompt+3input guards pass/no clients remain. No isolated speedup is claimed; broader acceptance remains open.
- Guide: world/entities/RUST-ENTITY-RESIDENCY.md. Receipts: build/native-entity-residency/. SUMMARY now records the complete35b68379 matrix. Next prioritize live frame producers/consumers and remaining world-state orchestration.

## Open acceptance scope

- The historical graphics-driver mismatch was resolved by reboot; hardware tests execute. It is not an active blocker.
- Strict leash width still differs; published-registry control reproduces it. Cow body/shadow comparison passes separately. Some static reload fixtures omit Current crosshair; predecessor9f also does, while moving/latest vanilla views show it.
- First shader world frame, first-turn nearby regular-terrain pop-in/flicker, sustained streaming and long host/GPU memory remain unproved. Settled captures do not resolve transient gaps; preserve observations of reduced flicker and possible haze separately.
- Broad gameplay/transition acceptance remains open: entities/items/hands, particles/weather, transparency, GUI/HUD, resource packs, representative shaders and DH combinations.
- Strict height-world teardown/closed-channel race and parallel Rust GLX BadAccess/poison failures remain unresolved; serial success does not fix them.

## Preservation and retention

- Protected stashes:12345bcea835a6969b164f1a358ecd44a5bb22ab andc979e5de03a6682327a293fec0fc5150eaacf0bb. Workflow recovery/publication receipts remain under build/native-entity-sections/.
- Stage explicit reviewed paths; never force-push/reset/blanket-stage. Preserve incoming docs and local implementation together on upstream master.
- Prior pruning recovered~350GiB plus21.08GiB archives/17.04GiB unused Rust profiles; retired56 superseded videos(17.83GiB) with447 PNG/profile receipts retained. Historical deletion manifests record hashes.
- Residency workflow retired21 completed generated workspaces after client exit;151 selected evidence hashes remained unchanged.
- Retire only recognized, completed generated world/profile workspaces after their clients exit. Preserve measurements, originals, profiles, failed/unknown/active/symlinked scopes, prepared inputs and open-defect worlds. Never clean during timed workloads.

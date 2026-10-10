# Rust migration working record

## Goal and constraints

- ACTIVE: remove Java/JVM system by system; one Rust library and one executable for client/server.
- Work on master in this checkout; publish tested larger milestones normally.
- Frozen OpenGL alone is the baseline: ../MattMC_JavaPerfTesting/MattMC, HEAD7a4d181717dc0c7da9086f1a17687dfd522ce417; never modify Frozen.
- Preserve Rust/GAL GPU ownership and one presenter. Transitional Java exposes bounded CPU semantics.
- Protect/exclude docs/STANDARD-COPILOT-PROMPTS.md, SHAc5695e9b. SUMMARY≤10 lines; this record≤100; concise ASSUMPTIONS.md.
- Practical guides belong beside their subsystem; tools in DevUtils/tests/; Wiki after documentation changes.

## Ownership and priorities

- Rust owns rendering/GAL/presentation, native terrain assembly/publication/visibility, retained DH inputs and many content/world kernels.
- Canonical block/biome sections, rebuild captures, counters, light layers/maps, biome residency and sky/fog/scalar light consumers have native owners.
- Java still owns general gameplay/simulation, chunk/light orchestration, contextual callbacks, platform/network/startup and remaining extraction. app/ remains empty.
- Prioritize whole authoritative world-state producers with direct consumers; avoid Java packing followed by Rust reconstruction. Performance guides migration rather than stopping it.
- Published sky1b9b10339, fog/cache3adbe6d5d, light maps971e0226b, face policy/shared cacheaf6921cf5 and DH height fieldsdb49d8816. Their subsystem guides retain scoped verification and historical timings.

## Published DH height fields

- Native collision catalog/DH height owners read live blocks/counters directly; Java retains CPU leases. Custom/dynamic shapes and overridden wrappers keep original callbacks.
- Actual Frozen production oracle:31,809 states,31,532 cached states,323 geometries and16 saved chunks; preserve opacity16, exclusive top, omitted minimum row and stale counters.
- Release574b68f0: Rust2479pass/3ignored; Java1838tests/2skip/0fail across329 suites; five JNI checks and three observed workers map exact release. Reviewed settled vanilla/Iris+DH pairs pass, DH28.81%, VUID0.
- Six original lifecycle cases pass. Different-world teardown had one closed-channel INFO line/two classifier matches; original strict failure retained. Correct same-source rerun passes; race unresolved and classifier unchanged.
- All16 production ABAB/exact6000 clean; medianFPS C/F1372.4/1179.4 vanilla,720.55/602.7 DH,340.55/317.45 shaders,253.3/224.5 combined. Medianp99ms3.125/3.176,5.471/6.706,4.417/6.141,5.795/7.754 pass. No isolated gain claimed.
- Paired8s DH allocation profiles: all6 F3 endpoints reviewed, native height producer observed; positive opaque/translucent/water submissions and all integrity/process guards pass. WeightedJava heightmaps7.34/74.45MB C/F, total1.50/4.31GB; native allocation excluded. ShortCurrentRSS4.36GiB only.
- Guide: docs/development/world/chunk/RUST-DH-HEIGHTMAPS.md; receipts: build/native-dh-heightmap-migration/. Publisheddb49d8816 normally, master/upstream matched and worktree clean before lighting work.

## DH lighting/emitter milestone under final review

- Rust owns section snapshots, first emitter enumeration, both priority queues and immutable compact lighting fields. Java passes bounded existing-owner references; light getters read CPU leases directly.
- Hashing/beacons request emitters before lighting. The native first-cache owner serves that normal path; public mutable lists must still match its positions/order. Edited/custom caches, custom wrappers and injected mutable storage keep original callbacks; mutation detaches authority once.
- Preserve Frozen neighbor order/deduplication, center clear after seeding, LIFO priority queues, direction order, propagation bounds, stale emitter caches and independent retained-field lifetimes. No GPU/frame ABI or presenter change.
- Actual pinned Frozen production corpus:20 cases/60 phases over all16 saved chunks, nine neighbors, omitted center seeds, null/duplicates, stale cache and empty chunks; compare every cell/flag/work count. Two additional cold neighborhoods exercise the complete JNI handoff.
- Final release3f00217d: four focused Rust checks and full2483pass/3ignored; five JNI checks and fullJava1843tests/2skip/0fail/0errors across330 suites. Two observed JNI workers map exact release.
- All7 replacement lifecycle scenarios and both manually reviewed settled vanilla/Iris+DH pairs pass, VUID0 and DH28.806%. This does not resolve the earlier teardown race or prove transient/first-frame behavior.
- Paired8s DH allocation profiles pass: all6 original-resolution F3 endpoints reviewed/exactly match, crossing seven X columns; actual native lighting-field publication observed on DH worker stacks; opaque/translucent/water submissions positive. Source/native/Frozen/prompt/save/movement/terminal/process guards pass.
- WeightedJava total1.108/4.448GB C/F; legacyqueues36.70/34.60MB remain (previousCurrent128.97MB). Sampling excludes native allocation and subsets overlap; no isolated gain. ShortCurrent peakRSS4.05GiB only.
- All20 production timing runs clean, all integrity guards and10 Current native fingerprints pass. Vanilla/shaders/combined have two repeats per side; DH has four with no discarded runs.
- MedianFPS C/F1406.0/1163.85 vanilla,749.6/744.65 DH,345.65/314.2 shaders,254.85/219.9 combined. Medianp99ms3.117/3.454,4.142/5.161,4.856/6.320,6.127/9.685 meet measured floors.
- Initial DH two-repeat FPS miss743.0/744.65 (0.22%) is retained. Two additional repeats per side were declared before running; all four are included. DH margin is narrow and repeats vary; no isolated throughput gain or broad acceptance.
- Initial compact-only b22 variant declined normal pre-hash caches. Runtime intentionally stopped143 with owned clients cleaned; superseded/incomplete evidence and source receipts remain pinned, excluded from final acceptance.
- Guide: docs/development/world/lighting/RUST-DH-LIGHTING.md; exact receipts: build/native-dh-lighting-migration/. Short Current CPU profile passes all integrity/process guards; all3 F3 endpoints reviewed with positive DH submissions and0.545block return drift. Native lighting394/source44 CPU samples; Java voxel-builder1566, biome694/block221 out of13011 total, overlapping subsets. No paired Frozen CPU comparison. Final source/publication review underway; one stale Rust module doc sentence corrected after verification with executable code/native artifact unchanged, separately recorded.

## Evidence driving the next migration

- Earlier accepted ordinary/DH travel profiles establish actual matching F3 travel and native DH submissions, not transient parity. Interrupted originals remain rejected; later accepted replacements are explicitly separate.
- Native terrain cache wait:62/238 meshing samples across8 workers before shared reads;0/193 after, with all3 F3 endpoints/integrity guards reviewed. This does not prove the original DH timing outlier's cause.
- Historical305 timings passed FPS but missed vanilla p99; original2f DH/p99 regression and severe repeat variance remain in their receipts. Newer passing batches do not erase those records or establish broad gameplay performance.
- Latest DH allocation still samples event dependency lookup238MB Current/472MB Frozen. Next: whole DH voxel-column/full-data producers and direct native consumers, with actual Frozen output/mapping oracles and public-mutation/custom-hook compatibility.
- Remaining contextual terrain preparation/lighting/tint work also merits ownership migration. Do not replace whole-producer work with isolated native helpers over Java-packed data.

## Sync, storage and test integrity

- Requested600s work then sync completed earlier. Latest incoming docs-only1e060cb74 was fast-forwarded; scoped SUMMARY conflict preserved newer metrics and incoming qualifiers. All local hashes/modes/symlinks restored, staging empty; scoped recovery removed, two older stashes untouched.
- Fresh fetch before and during this lighting verification finds master/upstream matched atdb49d8816; no new incoming changes. Fetch/review again before normal publication; never stage the protected prompt.
- Bulk pruning recovered~350GiB earlier;~200GiB currently free. Preserve prepared DH source, unique failures/crashes/incomplete scopes; retire only proven completed inactive generated copies.
- Earlier parent-wide cleanup mistakenly deleted an interrupted allocation world copy. Logs/images/raw profile/interruption receipt remain; no recovery claimed. Helper now requires recognized completed enclosing flight.json plus driver exit0; incomplete/malformed/unknown/symlinked receipts retain worlds. Retention13tests and combined43 retention/flight tests pass.
- Height-field cleanup retained original failed reload logs/captures/receipts and prepared source. Validation driver had already retired that completed copied world despite strict log-health rejection; no world recovery claimed. Latest cleanup retires23 completed timing/profile copies; all unique evidence, prepared source and superseded lighting runtime worlds remain preserved.
- Clean timing excludes builds/profilers/other games; require exact6000frames, VUID0, no orphans, and measured FPS≥Frozen/p99≤Frozen. Distinguish short profiles/settled pairs from realistic sustained streaming and long-memory acceptance.

## Open work

- Broad gameplay, first shader world frame, temporal pop-in/flicker and long-session host/GPU memory remain unproved. Settled pairs do not certify all ordinary bulk-light pixels.
- Earlier ordinary-gameplay receipt d9d1a9d6 has entry/travel outliers and only16.45blocks travel before a barrier; not sustained-streaming proof.
- General simulation, Java removal and the Rust executable remain substantial work. No concrete user-decision blocker; continue implementation/debugging.

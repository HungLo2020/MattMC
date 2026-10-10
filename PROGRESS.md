# Rust migration working record

## Goal and constraints

- ACTIVE: remove Java/JVM system by system; one Rust library and executable for client/server.
- Work on master in this checkout. Latest instruction authorizes incremental commits/pushes; supersedes the earlier no-publication restriction.
- Frozen OpenGL alone is the baseline: ../MattMC_JavaPerfTesting/MattMC, HEAD7a4d181717dc0c7da9086f1a17687dfd522ce417; never modify Frozen.
- Preserve Rust/GAL GPU ownership and one presenter. Bridges carry bounded CPU semantics.
- Protect docs/STANDARD-COPILOT-PROMPTS.md, SHAc5695e9b. SUMMARY≤10 lines; this record≤100; concise ASSUMPTIONS.md.
- Practical guides belong beside subsystems; drivers in DevUtils/tests/; run Wiki check after documentation changes.

## Current ownership and next target

- Rust owns rendering/GAL/presentation, native terrain assembly/visibility, block/biome sections, counters, light fields, biome color windows, DH points/material maps/reduction and retained chunk inputs.
- Client residency now owns logical slot publication, count, center, position fingerprint and empty membership. Normal empty scans directly read retained native counters and stop at each callback.
- Java still binds atomic chunk references, supplies reference-CAS facts, projects ring reads, orchestrates relocation and delivers world/biome/hook callbacks. Malformed radii/custom inputs retain compatibility behavior.
- General gameplay/simulation, platform/network/startup and remaining extraction still belong to Java; app/ is empty. This is not Java-removal completion.
- Loaded-world sky/fog and DH columns now share one retained native biome binding; unchanged publication avoids Java pointer arrays and Rust owner-row reconstruction while checking primary generations.
- Prioritize authoritative world-state producers and direct consumers together. Next: contextual DH render-column color conversion or neighborhood orchestration, guided by streaming samples; preserve callbacks, cached wrapper fields and compatibility escape.

## Preceding shared-biome CPU checkpoint

- Release70cbb053 adds shared biome input/publication to retained chunk inputs/client residency and upstreamfc1d529db optimizations. Private semantic CPU exports only; renderer ABI79/layout58 and Rust/GAL presentation ownership are unchanged.
- Actual untouched Frozen Storage/outer-radius CRS1 records600 operations: every physical slot/count, exact fastutil empty iteration, range/index, callback order and exception prefix match the actual Java adapter and Rust core.
- Six focused Java client controls cover bulk counters, callback mutation, exception prefixes, escaped arrays/sets, exact table grow/shrink and custom-world height getters. Retained owners/snapshots survive growth/release; callbacks run outside native locks.
- Final250 Rust world checks and29 focused biome/column Java controls pass. FullJava:1755 ordinary reported/two skips and150 parity reported, zero failures/errors;1905 reported/1903 executed across341 suites. Six final workers map exact70cbb053. Prior scripted-look5 Python controls pass.
- All39474 Current/38176 Frozen source pins, native library and protected prompt match; Frozen HEAD unchanged. Latest receipts: build/native-biome-inputs/; earlier residency: build/native-client-residency/. Proper guides sit beside biome/chunk/chunk-loading subsystems.
- Both color consumers replay256 actual Frozen color cases through shared bindings. Same-root publication reuses windows; mutation/growth/replacement, escape, independent block admission, empty/oversized heights and custom getter order pass controls.
- Initial combined parity rejected an old always-preserve semantic-footprint assumption. Original log/XML retained. Reconciled test explicitly proves disabled preservation skips reads/publication and enabled preservation retains readers without point-array export; production behavior preserved.
- Native admission now declines custom world/height bindings before metadata reads. Added regression preserves stateful per-row getter calls/keys. Rust/native stayed unchanged; final Java suites include this revision.
- Additional strong native leases and obsolete cached bindings need sustained memory checks. CPU contract evidence is not runtime, FPS or full-parity acceptance. Twenty interleaved/exact6000 timing runs are predeclared for this image, not started.

## Previous combined world-producer CPU checkpoint

- Release628cfd97 adds DH change-hash sampling, cached-surface policy, UTF16 identity hashes and emitter folding to Rust over retained inputs. Java admits identities and delivers the original emitter boundary; no sampled voxel arrays cross it. Renderer ABI79/layout58 and Rust/GAL ownership remain.
- Actual Frozen recorder pins15 classes and records96 cases:16 saved block chunks/two declared biome patterns/three cached-field-edit phases. Rust and the ordinary Java consumer match every hash, including stale-empty masking and public emitter reorder/duplicates.
- Final253 Rust world,132 bridge,155 chunk and2 terrain-expansion checks pass. FocusedJava31 pass; fullJava1755 ordinary/two skips plus154 parity,1909 reported/1907 executed across342 suites, zero failures/errors. Six final workers map exact628cfd97.
- All39481 Current/38176 Frozen source pins, native library and protected prompt match; Frozen HEAD unchanged. Receipts: build/native-dh-chunk-hash/; guide: docs/development/world/chunk/RUST-DH-CHANGE-HASH.md. Initial Java fixture rejection retained: raw palette edit was correctly masked by the empty counter; corrected test uses the section setter. Production hash unchanged.
- Custom callback/exception prefixes, escaped biome/public wrapper replacement and mutable emitter subclasses keep compatibility behavior. Native identity readers share locks; scratch grows geometrically within fixed bounds. Hardware/FPS/long-memory remain unverified; twenty ABAB/exact6000 runs predeclared, not started.

## Current render-column CPU checkpoint

- Complete compact render-column reduction now belongs to render/dh_columns: exact unstable sort ties, small/adjacent/erase/forced merge passes, water restoration, highest-to-lowest output and semantic mixed identities.
- Canonical Java views borrow existing heap spans and receive direct destination writes, without point-array packing or Java reducer graph/sort construction. Custom inputs preserve callbacks/failures; native decline leaves output untouched. Contextual colors, faces/greedy merges and final packets remain Java.
- Actual untouched Frozen recorder pins6 classes and executes3616 synthetic CPU cases. All outputs/exceptions/sentinels match Rust; alias/material and four independent scratch-worker controls pass. Reused per-thread capacities are bounded8192nodes/8192indices/4096output pairs, with no source pointer/world retained.
- Releaseee1676ec passes4 focused Rust/27 architecture checks and5 focused JNI controls. FullJava1755 ordinary/two skips plus159 parity,1914 reported/1912 executed across343 suites, zero failures/errors; six final workers map exact image. All39492 Current/38176 Frozen/native/prompt/inventory guards pass; Frozen HEAD unchanged.
- No new runtime/FPS/long-memory claim;20 ABAB/exact6000 runs predeclared, not started. Receipts: build/native-dh-render-reduction/; guide: docs/development/rendering/RUST-DH-RENDER-COLUMNS.md. Initial fixture-include compilation error retained and corrected. Native scratch has no source/world pointers; Apache2.0 sort attribution/license bundled.

## Prior accepted hardware evidence

- Last measured image839ff128:2485 Rust pass/3ignored, Java1852reported/2skips; all7 lifecycle cases and2 reviewed original-resolution settled pairs pass, VUID0/DH28.84%; source/native/Frozen/prompt guards match.
- All20 predeclared ABAB/exact6000 runs clean: medianFPS C/F1331.80/1153.55 vanilla,725.85/733.70 DH,347.30/316.35 shaders,254.15/225.05 combined. Medianp99ms3.421/3.110,4.865/5.321,4.967/5.979,6.811/8.034. Vanilla p99 and DH FPS fail acceptance; both shader modes pass. DH includes4 repeats/side, others2. SUMMARY retains exact runs.
- Paired allocation/Current CPU travel profiles pass guards;9 original F3 positions reviewed, actual native publication observed. WeightedJava0.828/4.226GB C/F excludes native allocation; shortCurrentRSS4.684GiB is not long-memory evidence.
- Profile evidence drove columns, lighting and retained inputs: native height/preparation288, columns102, lighting541 of11881 CPU ticks. Inclusive tracked runnable1460/processQueue1314 overlap; neighborhood297/constructor302/hash144 identify producer work. They do not isolate priority-queue cost or prove a speedup.
- Later CPU milestones remain scoped history in subsystem guides/receipts: native terrain handoff, mutable full-data/occlusion/merges, contributor semantics, height reuse, UTF16 material identity and direct intrinsic materials, retained chunk inputs97737bac/client residencya1561418. Current CPU checks cover their contracts; older timings do not accept70cbb053.

## Hardware and remaining verification

- NVIDIA mismatch persists: loaded kernel595.91.07 versus installed595.99.02/NVML595.99. Unattended update installed newer user space; no reboot/driver/display/package change made here.
- Vulkan validation launches previously enumerated llvmpipe and failed X11 DRI3 BadDrawable before entry. Preserve those failures; do not repeat known software launches as hardware proof. CPU migration continues; goal remains active.
- After driver restoration, test both sides on the same restored driver: fullRust,7 lifecycle cases, equivalent original captures,20 clean timing runs, real streaming profiles and sustained host/GPU memory.
- Earlier software fullRust attempt2353pass/138fail/3ignored included GUI green210/209 and graphics-lock cascades; retained, not claimed passed. Earlier strict height-world teardown rejection/closed-channel race remains unresolved.
- Broad gameplay, first shader world frame, temporal pop-in/flicker, sustained streaming and long-memory remain unproved. Prior16.45-block travel/outlier receipt is not sustained-streaming acceptance.

## Sync and artifact integrity

- Next renderer batch uses sync-and-push:21 intended paths; upstream already matchesd3c427a79, empty index, no conflict/stash needed. Final CPU gates pass; prepared for incremental publication.
- Latest publication instruction supersedes the earlier no-commit restriction. Combined batchd3c427a79 published and remote master verified with sync-and-push. Worktree clean; workflow stash retired and both old stashes preserved. No hardware performance acceptance implied.
- Integrated368c10a87 documentation ontofc1d529db. Preserved150 pending paths:146 byte/mode/symlink exact, four overlapping docs reconciled; lighting conflict preserves historical #826 finding plus executable fix. Two additional rendering docs reflect that newer CPU scope. Original empty index restored; recovery: build/native-dh-chunk-hash/publish/.
- Earlier requested600s work interval/sync preserved134 paths across5323a216f→fc1d529db, reconciled packed-word decoding/mutation stamps and DH retained LongList/preservation-policy tests. Earlier temporary stash retired; original two stashes retained. Failures/receipts: build/native-client-residency/sync/.
- Bulk pruning recovered~350GiB earlier;~179GiB free. No new world/capture copies. Preserve prepared DH source and five open first-view/startup/closed-pipe investigation worlds; completed inactive timing copies are absent.
- Earlier cleanup removed an interrupted allocation-world copy; other evidence remains, no recovery claimed. Retention requires recognized completed flight plus driver exit0; malformed/incomplete/unknown/symlinked scopes retain worlds. No broad deletion.

# Rust migration working record

## Goal and constraints

- ACTIVE: remove Java/JVM system by system; one Rust library and executable with client/server modes.
- Work on master in this checkout; publish validated batches incrementally. Latest authorization supersedes the earlier no-publication restriction.
- Frozen OpenGL alone is the baseline: ../MattMC_JavaPerfTesting/MattMC, HEAD7a4d181717dc0c7da9086f1a17687dfd522ce417; never modify Frozen.
- Preserve Rust/GAL GPU ownership and one presenter; transitional bridges carry bounded semantic CPU inputs.
- Protect docs/STANDARD-COPILOT-PROMPTS.md, SHAc5695e9b. SUMMARY≤10 lines; this record≤100; ASSUMPTIONS stays concise.
- Developer guides belong beside subsystems; test drivers in DevUtils/tests/. Run Wiki check after doc edits.

## Current ownership

- Rust owns rendering/GAL/presentation, terrain assembly/visibility, block/biome sections, counters, light fields and biome color windows.
- Rust owns DH points/material tables, source metadata/occlusion, tint/reduction, whole box construction, greedy topology, packet encoding and direct ledger handoff.
- Client residency owns logical publication, counts, centers, fingerprints and empty membership; Java still binds chunk references and supplies callbacks.
- Loaded-world sky/fog and DH columns share retained native biome bindings; unchanged publication avoids rebuilding owner rows.
- General simulation, platform/network/startup, file orchestration and remaining extraction are Java. src/main/rust/app/mod.rs is empty; Java removal is incomplete.

## Current batch: saved palettes and decoded sections (453df088)

- Rust constructs canonical local/global block and biome owners directly; retains padding, palette order/unused entries, global remapping and validation policy.
- Current-version NBT loads hydrate primary block/biome/light owners inside Rust. Java palette/word/light arrays and list/stream reconstruction are removed from admitted loads.
- Confined decode scopes release temporary vectors/root tape; independent returned owners feed native counters and light maps. Custom factories retain original codec callbacks.
- d7107c13:33 focused Java controls pass, including40 actual Frozen saved chunks and primary/scope/copy ownership. Decode retains its originating factory; final Java scope controls also pass.
- Separate82-case actual Frozen palette oracle covers widths, padding, aliases and copy/edit/save consumers. Earlier e5c83a3a checkpoint:fullRust2621pass/3ignored, Java1967reported/2skips/0fail.
- Initial expanded Java run failed eager DH wrapper capture after pure helper initialization. Lazy actual-consumer binding plus early-init regression passes; initial failure and compile correction retained.
- Full suites pass on d7107c13:Rust2623pass/3ignored; Java1969reported/2skips/0fail (1967executed,360suites). Three quiet guarded40-chunk pairs cost13.39–13.46ms Current vs8.29–8.32ms Frozen (~1.61–1.62×). Scoped floor FAIL; profile/predecessor next; a15c0555 runtime historical.
- Earlier quiet saved-owner timings:blocks1~3.17–3.20×Frozen;blocks2~8.55–8.64×. Matrix terminated on measured JIT at17entries; all rounds retained, predecessor attribution absent.
- Warmed guarded JFR pair:40,000 loads/side; native FFM signature buckets place~30% main samples in hydration/~48% region reads. Java allocation estimates7.01/18.23GB C/F, diagnostic only.
- Word-wise native validation release453df088:fullRust2623pass/3ignored, Java1969reported/2skips/0fail;40 Frozen chunks and ownership controls pass. Compile visibility correction retained.
- Fresh453df088 three-pair quiet load timing:Current10.22–10.30ms vsFrozen8.31ms (~1.23–1.24×, floor FAIL);~23–24% below earlier d710 observations. All3 quiet control pairs pass:22–23% lower than hydration checkpoint,26–27% lower than published baseline. Seven lifecycle cases and two settled Frozen pairs pass, VUID0;9 exact native artifacts and four original deterministic1280×720 captures reviewed.
- Guides:docs/development/world/chunk/RUST-SAVED-PALETTES.md and RUST-DECODED-SECTIONS.md. Receipts/checkpoint controls:build/native-saved-palettes/.

## Previous published batch: saved DH lighting

- Saved-light batch a15c0555 on integrated base abbd16d3d moves saved nibble decoding, compact bytes, allocation/counter policy and ownership into Rust.
- Generation carries one field instead of two Java light-storage maps; admitted light/column consumers use the field directly.
- Import defers emitter enumeration; unknown caches remain distinct from recorded empty caches. Public mutable/custom inputs retain compatibility behavior.
- Preserve Frozen list order, skipped pairs, carried skylight, ignored extra bytes and mutable clear/edit behavior. No Java input pointers remain in Rust.
- Actual64-case Frozen replay,52 native imports,256 native columns and deferred-emitter controls pass.
- FullRust2618pass/3ignored; fullJava1962reported/2skips/0fail (1960executed,359 suite executions). Seven native consumers map the exact image.
- Guards cover39570 Current/38176 Frozen inputs, compiled consumers/driver/tests, fixture, native library and protected prompt. Frozen HEAD unchanged.
- Six alternating pairs per reference, five equal warmups,100 imports: C/F5.794/289.178ms; C/prior5.767/290.383ms. Checksums match; scoped cost ~98% lower.
- Initial annotation/fixture-name/raw-pointer and benchmark logger-parser failures retained; corrected suites/comparisons pass.
- Real DH CPU profile clean/exact, native boxes223/14341 samples; no importer calls sampled. Live importer admission remains unproved.
- Hardware:7 lifecycle cases and2 settled Frozen pairs pass, VUID0;9 exact native artifacts and4 original1280×720 images reviewed.
- Guide: docs/development/world/lighting/RUST-DH-SAVED-LIGHT.md; receipts: build/native-dh-saved-light/.

## Current performance evidence

- NVIDIA kernel/userspace595.99.02 match after reboot; NVML and Vulkan enumerate RTX3080Ti.
- Fresh20 predeclared alternating runs:360 settle/240 warmup/exact6000 measured frames; four DH repeats/side, two in other modes.
- All runs clean; source/native/Frozen/prompt guards and10 Current fingerprints pass. Every repeat retained; no profiler/other client during timing.
- MedianFPS C/F: vanilla1362.55/1148.50, DH877.20/709.40, shaders344.65/315.80, combined261.65/226.85.
- Medianp99ms C/F:3.365/3.337,2.299/5.322,4.748/6.737,5.494/8.743, same mode order.
- Aggregate performance gate FAIL: vanilla p99 misses by0.029ms. All FPS floors and other p99 floors pass; investigate rather than discard the miss.
- Hidden-minimap settled rotating workload only; readiness restarts permitted. No isolated importer FPS gain or broad acceptance claim.
- SUMMARY tracks every run; protocol/results: build/native-dh-saved-light/gameplay/performance.json.
- Older0133e073 whole-box matrix passed all eight floors; historical evidence remains in docs/development/rendering/GAMEPLAY-PERFORMANCE.md and build/native-dh-box-gameplay/.

## Next migration targets

- Continue authoritative world-state producers and direct consumers together; avoid Java packing followed by Rust reconstruction.
- Move the region-produced tape directly to Rust decode; remove Java output/input copies, repeated capacity retries and unconditional validation fingerprints.
- Continue DH source traversal/material resolution, collector candidates and contextual producers; move complete policy/ownership rather than isolated helper loops.
- Use actual workload profiles to prioritize; sampled stacks overlap and startup/JIT work does not establish steady-state cost or an isolated speedup.
- Treat measured performance gaps as continuing implementation work. Do not weaken GAL ownership or use Frozen Java Vulkan as a baseline.

## Remaining correctness and acceptance

- First shader-enabled world frame, temporal pop-in/flicker, sustained streaming and long host/GPU memory behavior remain unproved.
- Settled captures occur after terrain admission; they do not resolve the user's first-turn nearby terrain gaps with shaders+DH.
- Broad gameplay/state transitions still need coverage: entities/items/hands, particles/weather, transparency, GUI/HUD, resource packs, representative shaders and DH combinations.
- Earlier16.45-block travel and short RSS/allocation profiles are diagnostic; they do not establish sustained-streaming or memory acceptance.
- Earlier strict height-world teardown/closed-channel race remains unresolved. Preserve those failures.
- Earlier parallel Rust GLX BadAccess/poisoned-lock cascade remains unresolved; serial full-suite success does not fix that race.
- No JVM-free executable exists; remaining game behavior, networking and platform systems require migration before goal completion.

## History and evidence locations

- World storage/palette/counters, heights/lighting, full-data/contributors and source ownership: docs/development/world/ indexes and build/native-*/ receipts.
- DH reduction/boxes/quads/packets/handoff: docs/development/rendering/ subsystem guides; retain failed candidates and exact image/protocol identities.
- Previous handoff111c5922, source-owner and source-metadata regressions remain historical; their scoped results do not accept the new candidate.
- Previous whole-box batch: abbd16d3d, exact remote proof and clean status recorded in build/native-dh-faces/publish/.

## Preservation and cleanup

- Original stashes12345bcea835a6969b164f1a358ecd44a5bb22ab andc979e5de03a6682327a293fec0fc5150eaacf0bb remain protected.
- Sync-and-push integrates upstream before one batch commit; preserve pending paths/modes/staging. No force-push/reset/blanket staging.
- Prior pruning recovered~350GiB plus21.08GiB verified archival and17.04GiB unused Rust profiles; receipts remain in prior build/native-*/ directories.
- This batch pruned30 prior and9 completed runtime work copies; source worlds, captures and receipts remain. JFR JSON gzip plus2 completed profiling worlds saved255.5MiB; completed-profile-archive.json records restoration/hashes.
- Verified36 lossless JSON archives save3.31GiB. Manifest: build/native-dh-saved-light/completed-json-archive.json; restore with gzip -dk PATH.json.gz.
- Retired56 superseded October9 profiling videos (17.83GiB);447 PNGs, profiles and receipts retained. build/native-saved-palettes/retired-profile-videos.json records exact hashes; videos are not restorable.
- Active/unresolved/unknown/symlinked scopes retain evidence. Earlier interrupted allocation-world cleanup did not establish recovery.
- Preserve prepared DH source and open first-view/startup/closed-pipe worlds. Retention requires recognized completion, driver exit0 and no live process.

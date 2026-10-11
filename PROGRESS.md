# Rust migration working record

## Goal and constraints

- ACTIVE: remove Java/JVM system by system; one Rust library and executable with client/server modes.
- Work on master in this checkout; publish validated batches incrementally. Latest authorization supersedes earlier no-publication restrictions.
- Frozen OpenGL alone is the baseline:../MattMC_JavaPerfTesting/MattMC,HEAD7a4d181717dc0c7da9086f1a17687dfd522ce417; never modify Frozen.
- Preserve Rust/GAL GPU ownership and one presenter; transitional bridges carry bounded semantic CPU inputs.
- Protect docs/STANDARD-COPILOT-PROMPTS.md,SHAc5695e9b. SUMMARY≤10 lines; this record≤100; ASSUMPTIONS stays concise.
- Developer guides belong beside subsystems; test drivers in DevUtils/tests/. Run Wiki check after doc edits.

## Current ownership

- Rust owns rendering/GAL/presentation, terrain assembly/visibility, block/biome sections, counters, light fields and biome color windows.
- Rust owns DH points/material tables, metadata/occlusion, tint/reduction, box construction, greedy topology, packet encoding and direct ledger handoff.
- Local DH visibility also retains topology/section identities/container epochs and traverses and admits directly; Java still owns generation/retrieval scheduling and its generation tree.
- Client residency owns logical publication, counts, centers, fingerprints and empty membership; Java binds chunk references/callbacks. Sky/fog and DH columns share retained biome bindings.
- General simulation, platform/network/startup, file orchestration and remaining extraction are Java. src/main/rust/app/mod.rs is empty; Java removal is far from complete.

## DH visibility checkpoint

- Releaseebd3c9a3 moves ordinary render-list traversal into a retained Rust CPU owner. Stock frames send24 frustum floats/heights, without Java node traversal/candidate pairs or repeated generation imports.
- Incremental mutations, identity tokens and container epochs fence late workers, detached nodes and older closes. Closing drops native bulk storage; custom CPU callbacks keep their original route.
- Review found parent close-then-null could overwrite a concurrent worker install. Retirement now detaches under the section lock before close; two lifecycle regressions pass.
- FullRust2626pass/3ignored; fullJava1978reported/2skips/0fail (1976executed,361suites). Repeated actual Frozen1280-walk oracle and source/class/native/driver guards pass after the retirement repair.
- Hardware:7 lifecycle cases and2 settled Frozen pairs pass,VUID0;9 artifacts pin exactnative. All4 original1280×720 images reviewed: coast/trees/water/distant land, vanilla HUD/hand agree at tested poses.
- Warmed Current/Frozen profiles pass/guarded:50000 warmup+60000 measured frames,15s itimer/dwarf. Current nativecollector256samples,Javaiterator0 (prior64); renderlist272vsprior278 inclusive. Compiler activity remains; no isolated speedup claim.
- Fresh20-run comparison PASS:all clean;medianFPS C/F1431.4/1165.9,795.2/734.0,342.4/313.55,262.2/226.0;medianp99ms2.963/3.293,3.734/5.110,4.697/7.004,6.037/8.647 (vanilla/DH/shaders/combined). All eight floors pass; settled rotating/hidden-minimap scope only.
- Controller65089 terminal exit0;all six stages and source/native/Frozen/protected-prompt/prepared-fixture guards pass. Receipts:build/native-dh-visibility/; guide:docs/development/rendering/RUST-DH-VISIBILITY.md.

## Published ownership checkpoints

- Previous publishedee809bbab retains region-to-section tapes in Rust; full Java tape round trip/retries removed, fingerprints opt-in. Release06529556:Rust2623pass/3ignored,Java1974reported/2skips/0fail; actual40-chunk Frozen replay/guards pass.
- Three quiet40-chunk pairs on i7-12700K CPU19:Current12.20–12.31ms/Frozen13.14–13.53ms,ratios0.909–0.928 (7–9% lower scoped cost). All3 fresh45a074369 controls pass,26–28% lower. Seven lifecycle cases/two reviewed settled pairs pass,VUID0. Receipts:build/native-owned-tape/.
- Earlier45a074369/453df088 construct primary block/biome/light owners directly from current-version saved NBT. Canonical format, originating custom factories and scope/copy lifetimes preserved. Actual82 palette cases/40 chunks pass; full suites pass.
- Hydration's earlier~1.61–1.62× Frozen scoped cost fell to~1.23–1.24× with word-wise validation, then tape ownership passed the Frozen floor. Original failed timings/controls remain in build/native-saved-palettes/; world/chunk/RUST-SAVED-PALETTES.md and RUST-DECODED-SECTIONS.md document scope.
- Saved-DH-lighta15c0555 owns nibble decoding/compact bytes and carries one native field into consumers. Actual64 Frozen cases/52 imports/256 columns pass; Rust2618pass/3ignored,Java1962reported/2skips/0fail; matched scoped import costs~98% lower. Guide:world/lighting/RUST-DH-SAVED-LIGHT.md; receipts:build/native-dh-saved-light/.
- Historical saved-light20-run matrix:medianFPS C/F1362.55/1148.50,877.20/709.40,344.65/315.80,261.65/226.85 (vanilla/DH/shaders/combined). Aggregate FAIL:vanilla p99+0.029ms; other floors pass. All repeats retained. The new SUMMARY tracks the fresh local matrix rather than mixing images.

## Next work and acceptance

- Use actual workload profiles to prioritize live world/per-frame producers and consumers. Performance gaps guide more ownership migration; do not weaken GAL or treat them as migration blockers.
- Ordinary DH currently exports keys back to Java although its admitted caller only needs their count. Output-copy samples are small; assess this alongside remaining generation and entity/world extraction rather than declaring a gain from source inspection.
- First shader-enabled world frame, temporal pop-in/flicker, sustained streaming and long host/GPU memory behavior remain unproved.
- Settled captures occur after terrain admission; they do not resolve the user's first-turn nearby regular-chunk gaps with shaders+DH.
- Broad gameplay/transitions still need coverage:entities/items/hands, particles/weather, transparency, GUI/HUD, resource packs, representative shaders and DH combinations.
- Earlier16.45-block travel and short RSS/allocation profiles are diagnostic, not sustained-streaming or memory acceptance.
- Earlier strict height-world teardown/closed-channel race and parallel Rust GLX BadAccess/poisoned-lock cascade remain unresolved; serial suite success does not fix them. Preserve failures.
- No JVM-free executable exists; remaining game behavior, networking and platform systems require migration before goal completion.

## Preservation and cleanup

- Original stashes12345bcea835a6969b164f1a358ecd44a5bb22ab andc979e5de03a6682327a293fec0fc5150eaacf0bb remain protected. Sync-and-push integrated documentation-onlybe0499204 (batch397); all23 pending paths restored, architecture notes combine both changes. Temporary integration stash61e418a3 retained through publication verification.
- Stage explicit reviewed paths only; no force-push/reset/blanket staging. Fresh performance matrix and guards pass; publication proof belongs in build/native-dh-visibility/publish/.
- Prior pruning recovered~350GiB plus21.08GiB verified archival and17.04GiB unused Rust profiles. Lossless JSON archives retain original/restoration hashes; restore with gzip -dk PATH.json.gz.
- Earlier56 superseded profiling videos retired (17.83GiB);447 PNGs/profiles/receipts remain. Native-saved-palettes/retired-profile-videos.json records hashes; those videos are not restorable.
- Current batch archived162 terminal predecessor JSON files losslessly (12.05GiB saved), including unchanged failed FPS aggregates; images/logs/receipts retained. Hash manifests:build/native-dh-visibility/{completed-json-archive,historical-completed-json-archive}.json.
- Prior18 completed generated chunk-test worlds and2 completed tape-profile workspaces retired; corpus/logs/results remain. Both completed visibility-profile workspaces and20 completed timing workspaces retired after terminal controller/equivalence review; completed-workspaces.json records decisions.
- Preserve prepared DH sources and open first-view/startup/closed-pipe worlds. Retention requires recognized completion, driver exit0 and no live process; unknown/active/symlinked scopes remain.
- Developer guides and build/native-*/ receipts retain detailed subsystem histories, rejected candidates and publication identities. Earlier interrupted allocation-world cleanup did not establish recovery.

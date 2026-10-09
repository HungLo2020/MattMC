# Java-to-Rust migration — working record
## Objective and authority
- Complete only when Java/JVM is gone and one Rust library plus one Rust executable provide client/server modes.
- Preserve or improve behavior, visuals and performance against Frozen Java OpenGL; never alter Frozen.
- User authorizes commits/pushes to master and routine pruning. Assumptions: `ASSUMPTIONS.md`.
- Plan and acceptance: `docs/development/RUST-MIGRATION.md`; content details: `docs/development/game-model/`.
## Working state — 2026-10-08
- Work takes place in `/home/matt/Documents/Repos/MattMC` on `master`; synced documentation-only upstream `3e1b2a94c` after validation. Reconciled four docs while preserving native/source hashes and the user prompt edit.
- Previous `batch` branch preserved at `d7ee0335d`; local documentation edits saved in stash `12345bcea835` and `build/migration-transfer-20261008/`. User prompt restored locally.
- Master already owns Vulkan rendering, terrain visibility/assembly/publication, DH ledger/payloads and several world/storage kernels in Rust. Guard/test milestone published at `a0f5abeb2`; native state graphs/fluid definitions published at `6ccbfdf41`, property/fluid integration at `df6c6dc77` (remotes verified).
- Native block registry owns shared immutable tables; fluid/property definitions and registered block identities/domains/defaults now originate in Rust. Physical settings and intrinsic map-color/emission/fluid rules now originate in Rust; shapes, blocked light and contextual predicates still come from Java. General gameplay, world orchestration, assets/platform startup and application lifecycle remain Java.
- Cargo currently builds a cdylib only; `app/` is empty. Rust-only application and Java removal remain outstanding.
## Milestone 0 — trustworthy verification and storage (current)
- Feature fixtures must pass Frozen; missing scenarios, manifests, RGB data or failed captures cannot pass via a historical non-regression comparison.
- Background exceptions become explicit failed steps; aggregate checks every requested step and the full lifecycle scenario set.
- FPS artifacts require complete/publishable/crash-free receipts, exact frame counts, valid timings and explicit zero VUIDs.
- `--perf` requires >=2 paired repeats across all four modes; paired average-FPS medians must reach Frozen and p99 medians must not worsen.
- Client cleanup is scoped to this invocation and checks process start identity; killed owned orphans reject acceptance.
- Completed fixture copies retire while receipts/images stay; latest successful/failed driver runs are retained, with explicit `.keep` and comparison-baseline pins.
- Bulk cleanup/follow-ups recovered ~350 GiB across passes; disk free352.49 GiB after recent builds/tests. Final sweep recovered10.90 GiB: 2,065 exact duplicates hardlinked, 5 inactive generated fixtures retired, unused Cargo profiles/incremental removed; source/save/settings hashes matched.
- Detailed receipts: original checkout `artifacts/graphics-captures/goal5/disk-cleanup-20261008/`; recovery instructions: `ARTIFACT-STORAGE.md`.
- Verification: 21 focused Python regressions pass; wiki check passes (2,470 pages/43 indexes); runtime unchanged by this milestone.
- Fixed artifact-parent marker search that skipped capture-runner game cleanup; preserve live/crashed/symlink/unproven work and fixtures whose source is missing. Nine focused retention tests pass, including a real live-process check; wider harness regressions also pass. Receipts in cleanup `final-dedup/`.
## Performance and parity evidence limits
- Historical `batch2/summary.json` at runtime `d7ee0335d` lacks raw receipts; its measurements are not independently re-established acceptance. Fresh master measurements appear in Milestone1a and `SUMMARY.md`.
- Feature candidate: chest/chest-shaders/sign/trident passed; bed/banner/zombie/held-item coverage is incomplete or failed.
- Prior shield/hand attempts hit disk preflight; zombie equipment-reference processing raised an exception. Separate harness failures from renderer defects.
- Settled coast comparisons do not prove all gameplay, transitions, resource bounds, shader packs or moving terrain.
## Next ownership milestone
- Move content definitions and state construction into Rust, following Phase 2 of the existing game-model plan; shared typed IDs/tables precede their consumers.
- Keep definitions independent of rendering; render-owned model/material/tint columns retain their separate lifecycle.
- Verify all IDs/states/properties/save bytes and realistic full-client workloads against Frozen before publication.
- Subsequent milestones: world storage/simulation, behavior/components by family, remaining rendering/assets/platform/network owners, native application lifecycle, then Java/bridge/build removal.
- Renderer `batch` and ABI77 worktrees need measured integration before their runtime changes are promoted.

## Published foundations
- `6ccbfdf41`: native Cartesian state graphs plus all5 fluid definitions/37 states; Java objects are compatibility views. See `docs/development/game-model/STATE-GRAPHS.md` and `FLUID-DEFINITIONS.md`. Graph SHA989fd061714809653c436cae3e4931044af50fa0f4c4ade82c28b82140540e23.
- `df6c6dc77`: all134 native property definitions plus typed canonical block/fluid associations; shared immutable schemas. See `PROPERTY-DEFINITIONS.md`. Frozen/state/codec/runtime correctness passed; performance remained below the full target.
- Earlier full workflows: `validation/state-graphs-master-20261008/`, `native-fluid-definitions-master-20261008/`, `native-properties-master-20261008/`. Their figures are historical; current measurements are in SUMMARY.md. Each retired25 generated fixtures.
- Bootstrap allocation improved~10% for combined content migration; no isolated startup/FPS gain established. Five-pair receipts remain in `build/state-graph-master-verification-v2/`, `fluid-definitions-master-verification-20261008/`, `property-definitions-master-verification-20261008/`.

## Performance investigation retained
- Valid vanilla profile:20.25s inside measurement,10126 render-thread samples; coordinator28.1% inclusive,terrain enqueue12.4%,selectVisible6.9%. Evidence:`goal5/state-graph-performance-profile-v3/`; costs:`build/state-graph-migration/phase-costs.json`.
- Rejected/removed dense mesh-slot cache: candidate/control1045.3/1066.1 FPS,p994.028/3.595ms;Frozen1138.7/3.206. A1.6µs semantic-submit saving was not an overall win. Evidence:`goal5/dense-mesh-cache-measurement-v3/`; reuse/reload regression tests retained.
- User steering: move more ownership into Rust using compact sound data paths; measure/diagnose performance while migration continues. An unmet whole-renderer performance floor does not halt tested ownership milestones.

## Historical ownership milestones
- `2f2158cc4` owns1235 block identities/domains/defaults and31809 ranges;131 sets reuse63 graphs. Registered constructors bypass Java domain/default selection; unregistered codec objects retain compatibility. Full correctness passed; accepted16 performance runs had FPS−9.4%,+3.7%,+8.8%,+11.5% and all p99 comparisons failed. Receipts:`native-block-definitions-master-20261008/` plus clean vanilla repeat;33 fixtures retired. See `BLOCK-DEFINITIONS.md`.
- `1b1837931` owns181 physical profiles (18 fields) and removes2229 Java calls.24 Java/18 native ownership tests and five Frozen pairs pass; full Java1701/Rust2357,7 lifecycle cases and two reviewed images pass.16 clean FPS runs had−7.9%,−8.0%,+6.4%,+10.9%;only vanilla p99 passed. Receipts:`native-block-physics-master-20261008/`;25 fixtures retired. See `BLOCK-PHYSICS.md`.
- Both milestones were published with remote tips verified. Their staging sources/classes/binaries retired after hash checks; reference/provenance receipts remain in their draft directories. Current measurements are in SUMMARY.md; historical slices establish no isolated speedup.

## Milestone 1g — intrinsic state data (published `d0141162d`; performance target unmet)
- Rust computes map-color identity, emission and canonical fluid association for all31809 states using205 shared executable rule sets. Native evaluator matches untouched Frozen exactly; copper grates retain their distinct falling source-water state.
- Applied17 source/test files after exact base hashes against1b1837931. Six Java classes project indexed native state views and bounded property-function tables;849 Java declaration calls and two obsolete helpers removed. Format7 no longer imports emission/fluid IDs. Shapes, blocked light and contextual behavior still await migration.
- Standalone20 native tests, six Java production classes and three new regression tests compiled/passed before integration. Tests cover copied Properties and manually constructed state admission. Expanded Frozen observer to v6 for cached/default map colors and copied color/emission functions.
- Integrated release build,20 native content tests and27 Java ownership/registry/graph/meshing/save tests pass. Five v6 Frozen pairs match all six digests; intrinsic SHAfd35da448e3f2ab075d0639a3f4f5d7459355d9895c0a7aff787e26a158ac539. Sources/native match,Frozen unchanged;`build/block-intrinsics-master-verification-20261008/results.json`.
- Bootstrap2.288/2.314 s,allocation956.74/1069.62 MB(−10.6% combined);no isolated speedup claimed. Full `validation/native-block-intrinsics-master-20261008/` completed:Java1704/Rust2359 passed,2 skips/3 ignores,wiki2476/43,all7 lifecycle cases and both reviewed image pairs pass. RGB0.312/0.515/0.600 and3.763/4.359/3.997;DH subset pass,VUID0.
- All16 ABAB/6000 runs clean;FPS medians−5.8%,−8.6%,+5.9%,+10.5% (vanilla,DH,shaders,shaders+DH). Vanilla/DH average and p99 fail;both shader modes pass this run. SUMMARY.md has repeats/tails. Sources/native match observer,Frozen unchanged;`build/block-intrinsic-migration-draft/runtime-integrity.json`.25 fixtures retired.
- Draft/provenance:`build/block-intrinsic-migration-draft/`; exact integration manifest/reference receipts retained; applied staging sources/classes/binaries retired after hash checks. Do not rerun preparation scripts. Documentation:`docs/development/game-model/BLOCK-INTRINSICS.md`.

## Milestone 1h — sound content and block offsets (published `059c95621`; performance target unmet)
- Native draft owns2011 sound events,125 sound profiles,23 instruments and159 block sound/instrument/offset configurations. Preserves decorated-pot and pewen-branch sound rules;31,809 state bindings match Frozen. Eight offset configurations match9,250 exact Frozen samples, including all finite indices and extreme coordinate seeds.
- Staged11 Java production classes compile: native event registration/order, profile/enum views, cached state sounds and shared immutable offset vectors. Removes1071 additional Java configuration calls; format8 derives offset kinds/bounds directly. Native buffers are bounded CPU data; playback resources stay audio-owned.
- Initial23 standalone content tests and full native evaluator comparison pass. Shared core seed/world/render adapters, one new core test, four Java regressions and the v7 observer were staged afterward; their compilation/execution resumed after1g became terminal. No runtime-source changes during validation; no measured speedup claimed.
- Draft/provenance:`build/block-material-migration-draft/`; exact original hashes and read-only Frozen extracts retained. Do not rerun preparation scripts over drafts. Focused sound/offset developer pages and their indexes are integrated.
- Published1g as`d0141162d`; remote verified. Then applied38 sound/offset source/test files after exact base hashes. Release build,32 Java and24 native content/core tests pass. Shared coordinate seeding compiles in the full renderer/world build.
- Final standalone24 content/core and167 content/world tests pass; all native rows and9,250 offset samples still match Frozen. Eleven Java classes, four new regressions and v7 observer compile; three v7 control pairs match all seven digests (material SHAd386e583b266babbf00d9610730f2c8422c206fd5e13e821f40a48a4999b56ba). Sound IDs use a shared u16 column; instrument property names reuse sound declarations.
- Fixed pre-freeze sound access: canonical event objects are retained separately from unbound holders; registry lifecycle unchanged. Initial failure/recheck logs retained. Five integrated v7 Frozen pairs match all seven digests; sources/native match,Frozen unchanged. Receipt:`build/block-materials-master-verification-20261008/results.json`.
- Bootstrap2.284/2.269 s(+0.6%),allocation959.95/1066.61 MB(−10.0% combined migration);no isolated speedup claimed. Full `validation/native-block-materials-master-20261008/` completed:Java1708/Rust2363 pass,2 skips/3 ignores,all7 lifecycle cases and both reviewed images pass;RGB0.252/0.401/0.463 and3.695/4.222/3.867,DH subset pass,VUID0.

- Sound/offset:16 clean ABAB/6000 runs;FPS medians−3.0%,−3.8%,+8.6%,+9.6%;vanilla/DH average+p99 and shaders+DH p99 fail. Sources/native match observer,Frozen unchanged;`build/block-material-migration-draft/runtime-integrity.json`.25 generated fixtures retired; applied staging sources/classes/binaries removed after hashes checked.

## Milestone 1i — block-family configuration (verified; performance target unmet)
- Rust draft owns17 block sets,12 wood types and141 per-block family bindings, including button durations and weighted-plate limits; removes141 explicit Java factory parameter lists. Family enum carries only applicable typed configuration; gameplay/world scheduling remains separate.
- Read-only Frozen/current extracts agree across all1235 blocks; native draft matches every definition/binding.27 native content/core tests pass;17 Java production classes,3 new regressions and v8 observer compile. Draft/provenance:`build/block-family-migration-draft/`. No timing claim.
- Published sound/offset milestone as`059c95621`; remote verified. Applied28 family source/test/observer files after exact hashes; release build,26 native content tests and35 Java ownership/codec/meshing/save tests pass.
- Three v8 observer control pairs match all eight semantic digests; family SHA476e875d92a9be84463f7810ec20f5a27388ab62e2d56ffc0bfbea5d70f55229. All28 staged source/test/observer files still match recorded base/draft hashes; full runtime is unchanged.
- Five integrated v8 Frozen pairs match all eight digests; source/native hashes match,Frozen unchanged. Receipt:`build/block-families-master-verification-20261008/results.json`. Fixed the upstream-reported two-phase retention gap: parent retirement rechecks workspace/source eligibility.11 focused retention tests pass, including preserving a missing-source copy until its external source returns.
- Full `validation/native-block-families-master-20261008/` finished:Java1711/Rust2366 pass,2 skips/3 ignores,all7 lifecycle cases and both reviewed images pass;RGB0.304/0.542/0.642 and3.664/4.182/3.826,DH subset pass,VUID0. All16 ABAB/6000 runs clean;FPS−6.9%,−28.9%,+8.1%,+9.2%,vanilla/DH average+p99 fail,both shader modes pass. Investigate larger DH gap;no isolated family attribution. Sources/native match,Frozen unchanged;runtime-integrity receipt retained;25 generated fixtures retired.

## Next slice — map palette and image processing (staged only)
- Rust draft owns62 RGB definitions,4 shades,256 packed colors and whole-image RGBA/PNG processing. Actual Current staging swaps R/B for208/256 packed colors versus Frozen CPU image bytes; the draft corrects this and sends16 KiB indexed maps instead of64 KiB Java-expanded images.
- Six standalone native tests,nine Java production classes and both Java/native initialization probes pass. Renderer integration,regressions,real held/framed-map captures and timing remain pending. Main runtime unchanged during family workflow;draft:`build/map-palette-migration-draft/`.

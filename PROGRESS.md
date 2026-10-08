# Java-to-Rust migration — working record
## Objective and authority
- Complete only when Java/JVM is gone and one Rust library plus one Rust executable provide client/server modes.
- Preserve or improve behavior, visuals and performance against Frozen Java OpenGL; never alter Frozen.
- User authorizes commits/pushes to master and routine pruning. Assumptions: `ASSUMPTIONS.md`.
- Plan and acceptance: `docs/development/RUST-MIGRATION.md`; content details: `docs/development/game-model/`.
## Working state — 2026-10-08
- Work takes place in `/home/matt/Documents/Repos/MattMC` on `master`; synced upstream documentation commit `7fdf1ebce` while preserving all native work and the user prompt edit.
- Previous `batch` branch preserved at `d7ee0335d`; local documentation edits saved in stash `12345bcea835` and `build/migration-transfer-20261008/`. User prompt restored locally.
- Master already owns Vulkan rendering, terrain visibility/assembly/publication, DH ledger/payloads and several world/storage kernels in Rust. Guard/test milestone published at `a0f5abeb2`; native state graphs/fluid definitions published at `6ccbfdf41`, property/fluid integration at `df6c6dc77` (remotes verified).
- Native block registry owns shared immutable tables; fluid/property definitions and registered block identities/domains/defaults now originate in Rust. Intrinsic physical settings now originate in Rust locally; shape/light functions and contextual predicates still come from Java. General gameplay, world orchestration, assets/platform startup and application lifecycle remain Java.
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

## Milestone 1e — native block state definitions (published `2f2158cc4`; performance target unmet)
- Rust now declares1235 names/registration IDs, ordered domains/defaults and31809 state ranges;131 property/default sets reuse63 immutable transition graphs. Java behavior factories, physical settings and world-dependent callbacks remain.
- Registered constructors project native state definitions and bypass Java property/default selection; unregistered codec/test objects retain their existing constructor path. Export format5 sends remaining state facts only, removing names/defaults/property lists/value-index export.
- Prepared separately during the previous fixed-source validation; applied183 files after base-hash checks. Release build,17 integrated native content tests and22 Java registry/graph/codec/save/meshing tests pass.
- Extended Frozen observer to all31809 block-state flags/lights/fluid associations/offset samples/light-face boxes. Three pre-integration control pairs match Frozen at df6c6dc77; `build/block-definitions-control-verification-20261008/results.json`, block digest `24e7560d845c18d8d7fa4f5d31c2669c6b94ee03aee38ef7892920924fdc4732`.
- Five integrated Frozen pairs match all four digests (graph,fluid,property,block facts); `build/block-definitions-master-verification-20261008/results.json`. Bootstrap2.274/2.227 s (+2.1%), allocation958.36/1068.40 MB (−10.3%, combined migration). No isolated speedup claimed.
- Full `validation/native-block-definitions-master-20261008/` finished: Java1699 passed/2 skipped,Rust2356 passed/3 ignored,wiki2474/43,all7 lifecycle scenarios pass. Reviewed vanilla RGB0.346/0.585/0.697 and shaders+DH3.648/4.165/3.785 pass;DH subset pass,VUID0.
- Accepted16 ABAB/6000 runs use the full workflow’s three non-vanilla modes plus `validation/native-block-definitions-vanilla-clean-v2-20261008/`. Vanilla−9.4%,DH+3.7%,shaders+8.8%,shaders+DH+11.5%;all p99 comparisons fail. SUMMARY.md has both repeats/tails; no isolated migration speedup claimed.
- Original vanilla set conservatively excluded after draft compilation overlapped invocation startup; first repeat rejected after Frozen shutdown ClosedChannelException. Final repeat clean. Sources/native hashes match the Frozen observer,Frozen unchanged;`build/block-definition-migration-draft/runtime-integrity.json`.33 generated fixture copies retired.
- Draft/provenance: `build/block-definition-migration-draft/`; Frozen remained unchanged. Published as `2f2158cc4` on master; remote verified. User prompt remains uncommitted. Applied staging sources/classes/test binaries retired after hash checks; receipts/reference data retained.

## Milestone 1f — physical settings (verified; performance target unmet)
- Prepared native physical definitions for1235 blocks using181 immutable profiles: five floats,twelve flags,push reaction; removes2229 registered Java scalar configuration calls. AIR/CAN_OCCLUDE columns derive directly from native definitions.
- Untouched Frozen/current control agree on all18 physical fields; staged native values match every float bit/flag/reaction.18 standalone Rust tests pass; four Java production classes and two ownership/compatibility tests compile. Initial v5 control matched before adding cached-state coverage; final expanded digest below.
- Applied14 source/test files after exact base hashes against2f2158cc4. Release build and18 integrated native content tests pass; native facts format6 derives AIR/CAN_OCCLUDE directly. Documentation: `docs/development/game-model/BLOCK-PHYSICS.md`; draft/provenance: `build/block-settings-migration-draft/`.
- Integrated24 Java tests pass after correcting two test fixtures to exercise BlockBehaviour without allocating frozen-registry holders. Five v5 Frozen pairs match all five digests; physical/state-cache SHA95e1eb2e06a414acf14e401e975acec34c2efa82f34b1cedf11f3af07d0c6f50. Receipt:`build/block-physics-master-verification-20261008/results.json`.
- Bootstrap2.279/2.263 s (+0.7%),allocation959.19/1066.06 MB (−10.0% combined migration);no isolated speedup claimed. Applied staging sources/classes/binaries retired after hashes checked; provenance retained.
- Full `validation/native-block-physics-master-20261008/` finished:Java1701/Rust2357 passed,2 Java skips/3 Rust ignores;all7 lifecycle cases,wiki2475/43 and reviewed vanilla/Iris+DH pairs pass. RGB0.210/0.355/0.390 and3.726/4.268/3.871;DH subset pass,VUID0.
- All16 ABAB/6000 runs clean. FPS medians−7.9%,−8.0%,+6.4%,+10.9% (vanilla,DH,shaders,shaders+DH);vanilla p99 passes,other3 fail. SUMMARY.md has both repeats/tails. Sources/native match observer,Frozen unchanged;`build/block-settings-migration-draft/runtime-integrity.json`.25 fixtures retired. Next: publish this ownership slice, then finish/integrate intrinsic rules.

## Milestone 1g — intrinsic state data (isolated draft, not integrated)
- Native rules compute map color,emission andfluid association for all31809 states using205 shared rule sets; initial evaluator matches untouched Frozen exactly. Preserve copper grates’ distinct falling source-water state.
- Staged six Java classes andthree new regressions: indexed native state views, explicit legacy-state admission, native lookup-table projections for copied Properties;849 Java map/light declaration calls removed in draft. Format7 stops importing emission/fluid IDs. Native format7 draft now passes20 content tests; Java projection compilation checked after1f FPS became terminal. Integration and runtime checks remain pending.
- Draft:`build/block-intrinsic-migration-draft/`; original content base hashes and Java manifests retained. Do not rerun preparation scripts over modified drafts. Next: compile/review draft after1f workflow is terminal, update Frozen observer coverage, then integrate after physics milestone publication.

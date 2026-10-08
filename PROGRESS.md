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
- Native block registry owns shared immutable tables; fluid/property definitions and registered block identities/domains/defaults now originate in Rust. Remaining physical settings still come from Java. General gameplay, world orchestration, assets/platform startup and application lifecycle remain Java.
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

## Milestone 1a — native state graph construction (published `6ccbfdf41`)
- Rust `content/state` owns Cartesian enumeration and transition IDs for all block/fluid definitions; shared registry uses the same state-slot arithmetic.
- Java state objects/transition-reference arrays remain temporary views; Java graph expansion and neighbour-map searches are removed. Block declarations and codecs still remain Java.
- Native graph owners follow automatic-arena lifetimes; buffers immutable, 65,535-state ceiling, bounded materialized graph sizes.
- Master release build and full suites passed: Java1,695 passed/2 skipped; Rust2,347 passed/3 ignored; 0 failed. Wiki passed; completed client results below.
- Frozen/before/candidate observers agree: 1,235 blocks, 5 fluids, 31,846 states, 491,395 transitions; SHA256 `989fd061714809653c436cae3e4931044af50fa0f4c4ade82c28b82140540e23`.
- Five unprofiled fresh-JVM pairs: bootstrap thread allocation Current958.90/Frozen1068.09 MB (−10.2%); median time2.322/2.271 s (+2.2%). No startup-speed improvement or gameplay-performance acceptance established.
- Evidence moved to `build/state-graph-migration/evidence-before-transfer/`; transfer manifests preserve exact source hashes and original provenance.
- Repeatable observer driver: `DevUtils/tests/content/VerifyStateGraphs.py`; all five pairs match Frozen, source/native hashes checked, reference unchanged. Receipt: `build/state-graph-master-verification-v2/results.json`.
- Full gate `validation/state-graphs-master-20261008/` finished exit1: tests/wiki/lifecycle/parity pass; FPS floor fails. All 16 FPS receipts clean; 25 generated fixture copies retired automatically, investigation pinned. Remaining performance gap guides further native migration; it does not stop implementation.
- All 7 lifecycle scenarios passed; visually reviewed coast pairs pass: vanilla RGB0.227/0.355/0.394, Iris+DH3.604/4.113/3.764 (DH subset3.692/3.985/3.870), VUID0, both client exits0.
- Fresh ABAB/6,000: vanilla/DH FPS medians−3.7%/−14.0%; shader modes+7.0%/+9.5%. p99 Current/Frozen: vanilla3.606/3.580, DH7.186/5.431, shaders5.355/6.325, shaders+DH14.620/10.182 ms; `SUMMARY.md` has per-run FPS.
- Profile receipts point to vanilla native mesh expansion/grouping0.144 ms/frame, DH command generation~0.30 ms, shader+DH resource preparation1.87–1.99 ms; inclusive scopes, not additive. Compact costs: `build/state-graph-migration/phase-costs.json`. Next: sampling/alloc diagnostics, actual fixes, repeat full floor.
- Valid vanilla itimer/DWARF profile: 20.25 s wholly inside measured window, 10,126 render-thread samples; coordinator28.1% inclusive, terrain enqueue12.4%, selectVisible6.9%; mesh selection uses coordinate hashing despite dense graph slots. Evidence `goal5/state-graph-performance-profile-v3/`; older late-attachment profiles explicitly rejected. Next: inspect dense mesh lookup optimization, preserve ordering/lifecycle, measure against Frozen.
- Rejected and removed slot-indexed mesh cache after 6 clean, mapped-library-verified runs: median candidate/control1045.3/1066.1 FPS, p994.028/3.595 ms; Frozen1138.7 FPS/p993.206 ms. Semantic-submit0.03750→0.03590 ms did not establish an overall win. Retained two publication/reuse/reload regressions; release rebuilt with exact measured control SHA, 138 chunk tests pass. Evidence `goal5/dense-mesh-cache-measurement-v3/`; 6 fixture copies and diagnostic binary copies retired.

## Milestone 1b — native fluid definitions (published `6ccbfdf41`; performance target unmet)
- Rust `content/fluid` owns all five names/IDs, property declarations, defaults, resistance and all 37 intrinsic state rows; no Java definition export/input. Typed IDs and compact shared layouts are available to future native gameplay.
- Java registry/state objects project native rows; removed Java property declarations and amount/source/height/legacy-level calculations. Hot getters use immutable cached views, with no native crossing. World-dependent flow/ticks/interactions still remain Java.
- Preserve true-first falling domains and existing contiguous global IDs. Release build, 2 Rust fluid tests and 10 focused Java registry/graph/meshing tests pass; wiki2472/43 pass.
- Five fresh-JVM pairs agree with untouched Frozen for every graph plus all fluid IDs/traits/legacy block IDs/codec outputs/canonical round trips; fluid digest `88ec5a63f0f0f999c863525582a4616c0772edf7fa41707e285ca1bfef7c05b6`. Receipt `build/fluid-definitions-master-verification-20261008/results.json`; median bootstrap2.267/2.233 s (+1.5%), main-thread allocation958.99/1066.39 MB (−10.1%, combined graph+fluid work).
- Full workflow `validation/native-fluid-definitions-master-20261008/` completed exit1 solely for performance. Java1,697 passed/2 skipped; Rust2,351 passed/3 ignored; wiki2472/43 and all7 lifecycle scenarios pass. Reviewed coast pairs pass (vanilla RGB0.230/0.395/0.442, shaders+DH3.713/4.255/3.877; VUID0).
- All16 FPS receipts clean/exact6000/exit0, no owned orphans; 25 generated fixture copies retired. Latest average-FPS medians vs Frozen: vanilla−6.0%, DH−18.6%, shaders+8.3%, shaders+DH+9.5%; all p99 comparisons fail. Shader-only p99 worsened versus earlier measurements; cause not established. `SUMMARY.md` preserves both repeats and the full performance gap.
- User steering: move more ownership into Rust with sound compact data paths; continue migration while measuring/diagnosing performance. Do not stall the migration waiting for the entire renderer floor to pass.

## Milestone 1c — native block/fluid integration (verified property milestone)
- Block registry now stores a typed fluid-state ID per block state, replacing separate kind/height columns. Rust derives has-fluid/falling flags and resolves intrinsic facts from its fluid owner; export format4 sends associations and native property references, without Java fluid-fact reconstruction.
- Java fluid views expose their native ID directly; registry registration checks it against Java's temporary ID mapper. Meshing registration prepares only render-owned metadata before trying the native view; compatibility facts are deferred until rejection.
- Native graph automatic arena is allocated before acquiring its Rust owner, avoiding an allocation-failure leak at that step. Release build, 14 content +28 affected-consumer Rust tests, 20 Java projection/meshing/save tests and wiki2472/43 pass. Temporary staging copies/binary removed after exact worktree checks.
- Five Frozen pairs still match both graph/fluid digests; `build/fluid-associations-master-verification-20261008/results.json`. Bootstrap medians2.286/2.260 s (+1.1%); allocation959.08/1066.31 MB (−10.1% for combined content work, not an isolated integration speedup).
- New scoped runtime check `validation/native-fluid-associations-smoke-20261008/` passes, with reviewed vanilla RGB0.300/0.538/0.638 and shaders+DH3.709/4.259/3.858; VUID0. Resource reload with shaders+DH also passes (`lifecycle-gate/fluid-association-reload-20261008/`); 3 generated fixture copies retired. Tested source/native hashes still match.
- Combined runtime/performance verification for this integration and shared properties appears below; the earlier association-only checks remain scoped historical evidence.

## Milestone 1d — native property definitions (verified; performance gap remains)
- Rust `content/property` declares all134 properties (57 boolean,36 integer,41 enum):123 shared plus11 for integrated content, including serialized names, ordered domains and range bounds. Java's shared fields only project native definitions and bind existing enum objects; block definitions/gameplay remain Java.
- Native block registries share immutable schemas by reference; property names/values no longer export from Java. Per-block property IDs retain first-use identity. Fluids derive falling/level values from the same owner.
- Release build and17 native content tests pass. Five fresh Frozen pairs match all134 property definitions/codecs plus unchanged graph/fluid digests; source/native identity checks pass, Frozen untouched. Receipt `build/property-definitions-master-verification-20261008/results.json`; property SHA256 `6b70ad38ddae4bb4e31a44132b04db7ced98d3d10dcc1898e3ef8804b7109287`.
- Bootstrap median Current2.308/Frozen2.314 s (~equal); main-thread allocation959.39/1068.05 MB (−10.2%, combined graph/fluid/property work, not isolated property performance).
- Full `validation/native-properties-master-20261008/` completed exit1 solely for performance: Java1699 passed/2 skipped, Rust2356 passed/3 ignored, wiki2473/43, all7 lifecycle scenarios pass. Both coast images reviewed: vanilla RGB0.209/0.355/0.389, shaders+DH3.666/4.188/3.837; DH subset pass,VUID0.
- All16 ABAB/6000 FPS receipts clean, exit0, no exceptions/orphans;25 generated fixtures retired. Medians vs Frozen: vanilla−6.3%, DH−0.5%, shaders+10.3%, shaders+DH+9.1%; vanilla/DH p99 fail, both shader modes pass this run. Both repeats in SUMMARY.md; no isolated speedup attributed to property definitions.
- Source/native hashes still match the five-pair observer after full runtime checks (`build/state-graph-migration/property-runtime-integrity.json`).
- Published as `df6c6dc77` on master; remote verified. User prompt edit remains uncommitted. Latest full performance table is this milestone.

## Milestone 1e — native block state definitions (verified; performance target unmet)
- Rust now declares1235 names/registration IDs, ordered domains/defaults and31809 state ranges;131 property/default sets reuse63 immutable transition graphs. Java behavior factories, physical settings and world-dependent callbacks remain.
- Registered constructors project native state definitions and bypass Java property/default selection; unregistered codec/test objects retain their existing constructor path. Export format5 sends remaining state facts only, removing names/defaults/property lists/value-index export.
- Prepared separately during the previous fixed-source validation; applied183 files after base-hash checks. Release build,17 integrated native content tests and22 Java registry/graph/codec/save/meshing tests pass.
- Extended Frozen observer to all31809 block-state flags/lights/fluid associations/offset samples/light-face boxes. Three pre-integration control pairs match Frozen at df6c6dc77; `build/block-definitions-control-verification-20261008/results.json`, block digest `24e7560d845c18d8d7fa4f5d31c2669c6b94ee03aee38ef7892920924fdc4732`.
- Five integrated Frozen pairs match all four digests (graph,fluid,property,block facts); `build/block-definitions-master-verification-20261008/results.json`. Bootstrap2.274/2.227 s (+2.1%), allocation958.36/1068.40 MB (−10.3%, combined migration). No isolated speedup claimed.
- Full `validation/native-block-definitions-master-20261008/` finished: Java1699 passed/2 skipped,Rust2356 passed/3 ignored,wiki2474/43,all7 lifecycle scenarios pass. Reviewed vanilla RGB0.346/0.585/0.697 and shaders+DH3.648/4.165/3.785 pass;DH subset pass,VUID0.
- Accepted16 ABAB/6000 runs use the full workflow’s three non-vanilla modes plus `validation/native-block-definitions-vanilla-clean-v2-20261008/`. Vanilla−9.4%,DH+3.7%,shaders+8.8%,shaders+DH+11.5%;all p99 comparisons fail. SUMMARY.md has both repeats/tails; no isolated migration speedup claimed.
- Original vanilla set conservatively excluded after draft compilation overlapped invocation startup; first repeat rejected after Frozen shutdown ClosedChannelException. Final repeat clean. Sources/native hashes match the Frozen observer,Frozen unchanged;`build/block-definition-migration-draft/runtime-integrity.json`.33 generated fixture copies retired.
- Draft/provenance: `build/block-definition-migration-draft/`; Frozen remained unchanged. Next: publish this verified ownership slice, then integrate the prepared physical-settings owner. Applied staging sources/classes/test binaries retired after hash checks; receipts/reference data retained.

## Milestone 1f — physical settings (isolated draft, not integrated)
- Prepared native physical definitions for1235 blocks using181 immutable profiles: five floats,twelve flags,push reaction; removes2229 registered Java scalar configuration calls. AIR/CAN_OCCLUDE columns derive directly from native definitions.
- Untouched Frozen/current control agree on all18 physical fields; staged native values match every float bit/flag/reaction.18 standalone Rust tests pass; four Java production classes and two ownership/compatibility tests compile. V5 observer control adds physical digest78cdb304d7050022aab46e56904b18c8e5cd9061250fc3912027bb240f9e2a1a.
- Draft in `build/block-settings-migration-draft/`, with base hashes and proper developer-document draft. Do not apply until1e runtime validation/publication completes. All1e runtime checks are terminal; approved clean replacement recorded above. Continue native migration with performance gaps tracked.

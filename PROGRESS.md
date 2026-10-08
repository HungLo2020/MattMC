# Java-to-Rust migration — working record
## Objective and authority
- Complete only when Java/JVM is gone and one Rust library plus one Rust executable provide client/server modes.
- Preserve or improve behavior, visuals and performance against Frozen Java OpenGL; never alter Frozen.
- User authorizes commits/pushes to master and routine pruning. Assumptions: `ASSUMPTIONS.md`.
- Plan and acceptance: `docs/development/RUST-MIGRATION.md`; content details: `docs/development/game-model/`.
## Working state — 2026-10-08
- Work takes place in `/home/matt/Documents/Repos/MattMC` on `master`; synced upstream documentation commit `7fdf1ebce` while preserving all native work and the user prompt edit.
- Previous `batch` branch preserved at `d7ee0335d`; local documentation edits saved in stash `12345bcea835` and `build/migration-transfer-20261008/`. User prompt restored locally.
- Master already owns Vulkan rendering, terrain visibility/assembly/publication, DH ledger/payloads and several world/storage kernels in Rust.
- Native block registry owns shared immutable tables; Java still defines and exports content. General gameplay, world orchestration, assets/platform startup and application lifecycle remain Java.
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

## Milestone 1a — native state graph construction (uncommitted)
- Rust `content/state` owns Cartesian enumeration and transition IDs for all block/fluid definitions; shared registry uses the same state-slot arithmetic.
- Java state objects/transition-reference arrays remain temporary views; Java graph expansion and neighbour-map searches are removed. Block declarations and codecs still remain Java.
- Native graph owners follow automatic-arena lifetimes; buffers immutable, 65,535-state ceiling, bounded materialized graph sizes.
- Master release build and full suites passed: Java1,695 passed/2 skipped; Rust2,347 passed/3 ignored; 0 failed. Wiki passed; completed client results below.
- Frozen/before/candidate observers agree: 1,235 blocks, 5 fluids, 31,846 states, 491,395 transitions; SHA256 `989fd061714809653c436cae3e4931044af50fa0f4c4ade82c28b82140540e23`.
- Five unprofiled fresh-JVM pairs: bootstrap thread allocation Current958.90/Frozen1068.09 MB (−10.2%); median time2.322/2.271 s (+2.2%). No startup-speed improvement or gameplay-performance acceptance established.
- Evidence moved to `build/state-graph-migration/evidence-before-transfer/`; transfer manifests preserve exact source hashes and original provenance.
- Repeatable observer driver: `DevUtils/tests/content/VerifyStateGraphs.py`; all five pairs match Frozen, source/native hashes checked, reference unchanged. Receipt: `build/state-graph-master-verification-v2/results.json`.
- Full gate `validation/state-graphs-master-20261008/` finished exit1: tests/wiki/lifecycle/parity pass; FPS floor fails. All 16 FPS receipts clean; 25 generated fixture copies retired automatically, investigation pinned. Publish only after acceptance.
- All 7 lifecycle scenarios passed; visually reviewed coast pairs pass: vanilla RGB0.227/0.355/0.394, Iris+DH3.604/4.113/3.764 (DH subset3.692/3.985/3.870), VUID0, both client exits0.
- Fresh ABAB/6,000: vanilla/DH FPS medians−3.7%/−14.0%; shader modes+7.0%/+9.5%. p99 Current/Frozen: vanilla3.606/3.580, DH7.186/5.431, shaders5.355/6.325, shaders+DH14.620/10.182 ms; `SUMMARY.md` has per-run FPS.
- Profile receipts point to vanilla native mesh expansion/grouping0.144 ms/frame, DH command generation~0.30 ms, shader+DH resource preparation1.87–1.99 ms; inclusive scopes, not additive. Compact costs: `build/state-graph-migration/phase-costs.json`. Next: sampling/alloc diagnostics, actual fixes, repeat full floor.
- Valid vanilla itimer/DWARF profile: 20.25 s wholly inside measured window, 10,126 render-thread samples; coordinator28.1% inclusive, terrain enqueue12.4%, selectVisible6.9%; mesh selection uses coordinate hashing despite dense graph slots. Evidence `goal5/state-graph-performance-profile-v3/`; older late-attachment profiles explicitly rejected. Next: inspect dense mesh lookup optimization, preserve ordering/lifecycle, measure against Frozen.
- Rejected and removed slot-indexed mesh cache after 6 clean, mapped-library-verified runs: median candidate/control1045.3/1066.1 FPS, p994.028/3.595 ms; Frozen1138.7 FPS/p993.206 ms. Semantic-submit0.03750→0.03590 ms did not establish an overall win. Retained two publication/reuse/reload regressions; release rebuilt with exact measured control SHA, 138 chunk tests pass. Evidence `goal5/dense-mesh-cache-measurement-v3/`; 6 fixture copies and diagnostic binary copies retired.

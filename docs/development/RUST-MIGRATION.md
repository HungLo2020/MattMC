# Completing the Rust migration

The final runtime is **one Rust executable**, with client and dedicated-server
modes, **at most one separately loaded Rust library**, and no Java/JVM. This is
ongoing work. Current native kernels and Java bridges do not establish
completion. Keep the root `PROGRESS.md` within 100 lines, `SUMMARY.md` within 10,
and `ASSUMPTIONS.md` concise; put subsystem documentation beside its owner.

## Milestones

| Stage | Ownership to establish | Acceptance evidence |
| --- | --- | --- |
| 0: verification | Reliable acceptance results and bounded generated artifacts | Failed/missing evidence rejects acceptance; owned-process and retention regressions pass |
| 1: content | Rust definitions, typed registries, state construction and common metadata | Every Frozen ID/state/property agrees; existing worlds serialize identically |
| 2: world | Authoritative chunks, load/save/upgrade scheduling, lighting, generation and ticking | Equivalent saved worlds and deterministic simulation traces; real loading/rebuild/transition workloads |
| 3: gameplay | Block behaviors by family, components, inventories, entities, physics and AI | Interaction/tick/save traces and realistic gameplay for each migrated family |
| 4: presentation and services | Remaining semantic extraction, GUI, assets, configuration, audio, networking and platform services | Full vanilla/DH/shader coverage, reloads, transitions, resource and latency checks |
| 5: application | Native startup, client/server orchestration and executable; remove Java, bridges and JVM build/runtime requirements | Clean Rust-only build and realistic client/server/save-compatibility verification |

Stages overlap where dependencies permit. Ship coherent ownership slices;
move their callers and lifetime management with their data. Follow the
[content migration plan](game-model/MIGRATION-PLAN.md) for definitions and
behavior, and the [project architecture](PROJECT-ARCHITECTURE.md) for module
boundaries. `content` does not depend on its consumers; `compat` is temporary.
Avoid a Java launcher disguised as the native application.

## Publishing a milestone

Frozen **Java OpenGL** is the sole correctness and performance reference.
Leave Frozen unchanged. Match worlds, cameras, effective settings, resource
and shader packs, DH configuration and timing. Native fixtures and unit tests
supplement live verification; they cannot replace it.

Use the [rendering verification workflow](rendering/RENDER-VERIFICATION.md),
including `RunValidation.py --label <new> --perf`, plus the subsystem's own
behavior/save tests. Isolated checkouts need explicit `--run-source`,
`--vanilla-run-source` and `--shader-pack` pointing at retained inputs.
`RunFeatureParity.py` adds held-item, equipment and block-entity fixtures.
Preserve actual receipts and inspect images; reject incomplete, crashed,
non-equivalent or pathological runs. Average FPS and p99 must meet Frozen
across the four renderer modes. Desktop noise calls for more paired evidence,
not an automatic acceptance waiver.

For runtime changes, measure before and after with equivalent realistic
workloads and monitor memory/resources through repeated transitions. Tooling
and documentation-only milestones make no new runtime performance claim.
Update affected docs, run `python3 DevUtils/RunWiki.py check`, then commit and
push tested milestones to master. Prune superseded evidence using the
[storage guidance](rendering/ARTIFACT-STORAGE.md); pin only current acceptance
and unresolved diagnostic evidence.

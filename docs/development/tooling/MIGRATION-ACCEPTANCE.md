# Migration acceptance

The owner's [October 16 delivery policy](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18984771)
uses focused correctness and short regression/resource screens for intermediate
migration increments. Full performance acceptance belongs at completed subsystem
milestones and final goal acceptance. This supersedes conflicting per-increment
performance and exception-turn requirements in older assignments and policy text.
Correctness, save integrity and memory safety remain mandatory. Preserve original
baselines, failed runs and the rules used for historical acceptance; a policy
change does not turn missing evidence into a pass.

## System ownership and checkpoints

Assign a complete production subsystem outcome to one worker, with explicit
cross-owner interfaces and reservations. The worker chooses its connected
implementation order and internal milestones, and continues until that system
is complete without asking for another small assignment. Move producers, owned
state, actual consumers and lifetime together. A wrapper, counter, pooled frame
or native helper around retained Java policy is not system completion.

Publish coherent, tested checkpoints while retaining responsibility for the
remaining system. Remove replaced Java implementation where correctness permits;
keep the original baseline separately. Java may temporarily call a shared Rust
runtime; the final Rust executable must call that same runtime. Do not create
duplicate application implementations or a JVM sidecar and call it migration.
External service dependencies must remain explicit. Subsystem completion does
not establish the final application-wide no-JVM claim.

Existing ownership fences remain binding until a verified release/handoff.
Whole-system responsibility does not authorize concurrent writes to reserved
interfaces, unrelated infrastructure changes or new account/security access.

## Performance comparisons

### Intermediate increments

Use a short, trustworthy representative regression/resource screen with actual
workload and output identity. Record source/classes/native origins, configuration,
warmup/work, measured latency and noise, memory/retention limits and temporary
performance debt. Include real boundary/conversion and ownership costs. A noisy
or inconclusive screen is not a pass; investigate the affected observation.

Exhaustive per-increment multi-rotation matrices, per-row causal attribution and
a coordinator exception turn for ordinary temporary migration overhead are not
publication prerequisites. Reuse valid unchanged screens with an impact record;
do not rerun them to fill a new form. Stop remaining optional performance-only
jobs at safe boundaries once the required screen is satisfied. Preserve completed
and negative receipts, and do not interrupt valid correctness jobs.

Investigate material unexpected regressions, hangs, runaway allocation/retention
or unusable behavior. Report the worker's judgement and supporting evidence;
do not hide costs or label every regression unavoidable bridge overhead. Record
the responsible owner and a connected ownership-based exit, then continue that
work within the assigned system instead of polishing individual bridge getters.

### Completed subsystem milestones and final goals

Use full matched **Frozen** performance acceptance at completed subsystem
milestones and final goal acceptance. Preserve the original Frozen pin and
hardware targets. Up to **10% slower** is the general workload allowance;
up to **25% slower** applies when attributable to the temporary Java/Rust
boundary. These are alternatives, never additive. State the applicable allowance
and support boundary attribution instead of assuming it.

Apply those thresholds to the specific changed workload, not an aggregate
whole-application budget. Record matched workload, metric, source/build identities,
configuration, repetitions, noise and relevant resource costs. A small aggregate
effect does not establish a workload pass; an unrelated aggregate regression
is not an extra gate on an in-budget workload. Pre-change Current remains a
useful diagnostic comparison, not a universal speedup requirement.

The [October 16 delegation](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18978065)
retains dot's authority to review documented, bounded temporary migration-cost
exceptions where milestone acceptance requires it. Workers cannot self-approve
such exceptions. Record exact candidate/baseline identities, matched deltas,
affected scope, retained Frozen conversion/ownership debt, responsible owner and
verifiable ownership exit. Poor native implementations and unexplained material
regressions still require investigation. An intermediate screen or exception
does not waive correctness or the final native distribution/hardware targets in
the [accepted migration prompts](https://github.com/HungLo2020/MattMC/blob/master/NEW-MIGRATION-PROMPTS.md).

## Correctness and local publication

Exercise dangerous producer/consumer contracts before freezing for broad final
tiers: immediate and pending consumers, aliases, callbacks/reentry, error prefixes,
failure/cancellation, save/reopen and retirement where applicable. A production
witness must exercise real ownership, not an activity counter alone. Do not
launch broad campaigns merely because a prototype compiles.

Each worker runs applicable local correctness/subsystem checks, the required
stable [test tiers](TESTING.md), and affected validation after reconciliation.
No correctness, save-integrity or memory-safety gate is waived. Record actual
passes, failures, skips, missing coverage and exact tested inputs. Reuse evidence
only when scope, dependencies, configuration and fixtures are verified unchanged;
never silently relabel an older runtime or erase its failed observations.

Use the normal authorized fresh sync-and-push workflow: review the scoped
checkpoint on current master, reconcile dependencies, run affected checks, make
one reviewed commit, push without force, and verify the exact remote and required
CI to a terminal result. No separate coordinator publication turn or unrelated
device wait is required. Report the completed capability and concrete remaining
work; continue the owned system after its checkpoint lands.

## Local publication and rolling client checks

The owner's desktop performs accumulated client-render/FPS validation during its
own normal development tests; it is not a dedicated per-increment signoff queue.
Locally verified scoped checkpoints may publish with pending client coverage
explicitly identified. Investigate regressions found by those rolling checks.
A CPU fixture or documentation build is not a rendered client/FPS pass.

Follow the [render verification protocols](../rendering/RENDER-VERIFICATION.md)
when executing those checks. Do not weaken their health/image assertions or
silently waive required hardware checks. Keep pending client, visual, streaming
and long-session validation explicit. Original hardware targets remain part of
completed subsystem/final acceptance.

## Decision record

- [October 16 intermediate screens and milestone performance policy](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18984771)
- [Whole-system world-generation ownership and sole policy-document publisher](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18984776)
- [Shared Rust runtime, thin temporary Java caller and final no-JVM application](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18985361)
- [October 16 bounded-exception delegation](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18978065)
- [Historical workload-specific acceptance without an aggregate application gate](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18933981)
- [Historical interim Frozen allowances](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18933044)
- [Desktop client-render/FPS ownership](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18933581)
- [Rolling desktop development checks](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18933599)

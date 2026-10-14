# Interim migration acceptance

The owner's October 14, 2026 decisions update the migration workflow: move
production ownership into Rust without letting temporary boundary costs or a
separate client-validation queue stall independent work. Correctness remains
mandatory. This policy applies prospectively; keep historical measurements,
failed runs and the acceptance rules used for them intact. For interim work,
this dated policy supersedes older blanket no-slowdown or per-slice client-signoff
language; it does not retrospectively turn missing evidence into a pass.

## Performance comparisons

Use matched **Frozen** comparisons for interim acceptance. Up to **10% slower**
is acceptable for the specific migrated workload; up to **25% slower** is acceptable
when the cost is attributable to the temporary Java/Rust boundary. These are
alternative allowances, not additive budgets. State which applies and provide
evidence for a boundary attribution rather than assuming every regression is
bridge overhead. Record the metric, workload, direction of regression, matched
configuration and source/native identities so the comparison is reproducible.

Apply these limits to matched measurements of the **specific workload being
changed**, not an aggregate whole-application budget. An application already
40% slower than Frozen does not make a workload measured 10% slower than its
Frozen equivalent a blocker. Conversely, a small whole-application effect does
not prove that a workload meets its applicable limit. Track broader application
regressions separately; do not impose them as an extra gate on an in-budget slice.

Pre-change Current comparisons remain useful diagnostics for locating new
costs; they are not an additional universal acceptance floor. Preserve and
investigate demonstrated regressions beyond the applicable allowance. Do not
change workloads, discard inconvenient failures or combine unlike measurements
to manufacture a pass. Missing or noisy comparisons remain unverified.

Resolve boundary debt by moving the actual producer, storage, consumers and
lifetime into production Rust. Avoid spending the migration optimizing a
transitional bridge while its Java owner remains. The allowances do not excuse
behavior changes, lost content, broken saves or incorrect synchronization;
retain documented intentional differences and exclusions. They also do not
replace the separate final native distribution and hardware targets in the
[accepted migration prompts](https://github.com/HungLo2020/MattMC/blob/master/NEW-MIGRATION-PROMPTS.md).

## Local publication and rolling client checks

Each worker performs reasonable local correctness and subsystem validation,
using the [test tiers](TESTING.md) and relevant owner guides. Record the exact
scope, failed or skipped checks and pending coverage. Source inspection, a
passing CPU fixture or a documentation build must not be reported as a passing
rendered client or FPS run.

The owner's desktop is the actual client-render/FPS validation location. It
checks accumulated upstream changes during its **own normal development tests**;
it is not a dedicated QA queue. Locally verified, scoped slices may publish
without a separate desktop client/FPS signoff for each slice. Keep independent
migration work moving while client coverage is pending, and investigate
regressions when those accumulated checks reveal them. Lack of a historical
client fixture is a stated coverage limit, not an automatic block on every
unrelated migration slice.

This does not waive local correctness checks or known over-limit regressions.
Keep pending client, visual, streaming and long-session checks explicitly
pending. Follow the [render verification protocols](../rendering/RENDER-VERIFICATION.md)
when executing those checks; do not silently weaken their health or image
assertions. A historical script's strict performance verdict can be reported
alongside the applicable interim policy, with the original result preserved.

## Decision record

- [Workload-specific acceptance, without an aggregate application gate](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18933981)

- [Interim Frozen comparison allowances](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18933044)
- [Desktop client-render/FPS ownership](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18933581)
- [Rolling checks during the desktop's own development](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18933599)

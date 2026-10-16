# Connected item runtime cost review

**S0024/r6, October 16, 2026: temporary cost exception and bounded integrated resource evidence accepted for publication.**
The frozen candidate uses native `56f6aad82ef92e53144b5a486063730303524fbac9c9e183800030039d1c2360`
and 189 item-family classes. It was measured over `7fd10319`; the later
`e2242d18` skill/policy update changes no application/native inputs.
See [ownership and correctness](ITEM-STATE.md) for the actual native payloads,
physical legacy backing, lifetime controls, preserved allocation-failure defect
and final test tiers.
Under [decision 277](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18978586),
the disclosed 25 complete-operation and five generic-body latency/Java-allocation
results are accepted as temporary migration debt, with every confidence limit
retained. They remain outside the normal budgets.
[Decision 279](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18978903)
accepts the integrated native resource envelope below and carries this temporary
cost disposition into the combined image based on unchanged item sources/classes
and affected dependency validation. This is impact-based acceptance; image
`1eb51c6f…` was not timed identically to `56f6aad8…`. Historical full test tiers
remain evidence for their prior image. No broad resource or normal-budget pass
is implied.

Frozen remains `7a4d181717dc0c7da9086f1a17687dfd522ce417`. The original pre fixture
is `build/item-state-pre-646986043`, native `ae6ed8f6…`, with the preserved menu,
inventory, r5, upstream-lighting and enchantment class overlays recorded in each
receipt. References and original workload sources are unchanged.

## Complete operations

One fresh JVM per side, five dependent samples per workload. Every one of the
125 timed output checksums and 125 serialized/save checks per side matches.
These are diagnostic point estimates: 23 of 25 quiet medians exceed Frozen by
more than 25%; two are numerically within 10%. They are not a combined pass.
Every Current/pre median is worse in this run. Under the
[current policy](../tooling/MIGRATION-ACCEPTANCE.md), that comparison is diagnostic;
only dot can approve a specific temporary-overhead exception. Decision 277 records
that exception for this exact measured image; it is not a general budget pass.

All times below are ns/op. `q` is the number of compiler/GC-quiet samples in
Frozen/pre/Current order. Java bytes are operation-thread allocations; native
allocations and deferred retirement are not included. Variable byte samples
remain in the raw receipt. No whole-game, FPS or workload-frequency result was
measured.

| Operation | Frozen | Pre | Current | q F/P/C | Java B/op F/C |
|---|---:|---:|---:|---|---|
| quick_move_zero | 19.663 | 25.740 | 74.664 | 2/3/1 | 0.00 / 0.00 |
| quick_move_full | 288.921 | 374.017 | 2266.663 | 2/2/1 | 0.00 / 308.01 |
| quick_move_one | 419.527 | 448.699 | 1690.710 | 5/3/4 | 56.00 / 308.01 |
| quick_move_late | 788.637 | 356.194 | 1785.277 | 4/2/2 | 56.00 / 308.01 |
| quick_move_many | 1030.206 | 821.279 | 5862.554 | 5/4/5 | 56.00 / 308.01 |
| quick_move_empty | 303.865 | 274.170 | 5037.096 | 4/4/4 | 112.00 / 472.02 |
| reverse_zero | 28.112 | 26.528 | 87.682 | 1/2/2 | 0.00 / 0.00 |
| reverse_full | 639.877 | 923.280 | 2508.031 | 3/3/2 | 56.00 / 300.01 |
| reverse_one | 817.851 | 1245.911 | 2027.039 | 2/2/4 | 56.00 / 300.01 |
| reverse_late | 1271.083 | 793.921 | 1713.173 | 2/5/3 | 56.00 / 300.01 |
| reverse_many | 2536.583 | 791.275 | 2604.144 | 3/3/4 | 56.00 / 300.01 |
| reverse_empty | 1100.657 | 452.294 | 2414.202 | 5/5/5 | 112.00 / 464.02 |
| add_zero | 10.951 | 11.611 | 21.516 | 5/3/4 | 0.00 / 0.00 |
| add_full | 5575.346 | 4707.919 | 5717.479 | 3/3/3 | 1832.00–1832.08 / 1944.00 |
| add_partial | 3993.872 | 4610.921 | 7191.394 | 5/5/3 | 2240.00 / 1944.00–1944.08 |
| add_late | 4289.485 | 4337.594 | 5752.711 | 4/3/3 | 2240.00 / 1944.00 |
| add_many | 10817.202 | 12709.800 | 14224.931 | 5/4/4 | 0.00 / 64.00 |
| add_empty | 553.783 | 241.438 | 1529.852 | 4/5/4 | 56.00 / 224.00–228.01 |
| add_alias | 37.487 | 42.596 | 204.878 | 5/4/5 | 0.00 / 64.00 |
| add_damaged | 86.141 | 55.135 | 996.280 | 5/3/3 | 56.00 / 228.01 |
| pick_empty | 17.641 | 22.025 | 244.147 | 4/4/4 | 0.00 / 64.00 |
| pick_displace | 65.559 | 68.760 | 740.638 | 4/5/4 | 0.00 / 64.00 |
| pick_full | 87.025 | 99.787 | 619.978 | 4/4/3 | 0.00 / 64.00 |
| clicked_forward | 9188.622 | 5527.658 | 11954.617 | 5/4/4 | 2224.00 / 3144.06 |
| clicked_reverse | 8554.665 | 5769.836 | 12686.505 | 2/4/5 | 2224.00–2224.08 / 3088.06 |

Raw source/configuration/class/native identities, all samples and save checks are
in `build/item-state-r6-connected-allocation-cost1`. Quiet counts range from one
to five; compiler/GC flags do not prove stable independent trials. External
memory PSI avg10 was zero; CPU `some` reached 7.27 and IO 0.14. The monitor observed
61,793 swap-in pages and 5 swap-out pages. Keep those limits alongside the raw
series. Bootstrap was separately 6.533 s Frozen, 6.440 s pre and 6.575 s Current.

## Generic access debt

The unchanged original probe failed Current's admission check before timing:
it checked only direct interfaces, while `PatchedDataComponentMap` inherits
`Stack` through `Overlay`. Preserve `build/item-state-r6-connected-generic-cost1`;
it is not a passing Current result.

The companion `ItemStateGenericDebtReference.java` retains the five timed public
operation bodies, warmup and timing loop. Only its class references and inherited
interface recognition differ; the native-admission assertion remains. Its
Current receipt confirms both native stack and slot ownership. This is a focused
measurement compatibility correction, with no production change or broad rerun.
Original source remains untouched; new controls run against all three sides.

| Operation | Frozen ns | Pre ns | Current ns | q F/P/C |
|---|---:|---:|---:|---|
| count_read | 1.272 | 1.091 | 2.538 | 5/5/4 |
| count_mutation_read | 1.199 | 1.270 | 4.457 | 4/3/3 |
| component_read | 4.706 | 3.957 | 78.742 | 4/4/2 |
| component_mutation | 16.252 | 13.288 | 627.351 | 4/3/3 |
| stock_chest_create_mutate_swap_save_load_save_consume | 75408.250 | 156248.125 | 398440.000 | 5/5/4 |

All 25 sample checksums per side match in
`build/item-state-r6-connected-generic-cost2`; complete chest save/load/save
comparison is inside that original round-trip body. All five point estimates
exceed 25% Frozen. This remains one JVM per side, with sparse quiet samples and
no native allocation accounting.

## Bounded attribution and retained ownership debt

`build/item-state-r6-bounded-profile1` contains one unchanged-image JFR diagnostic
of five original complete operations, 60,000 calls each. Source/class/native
checks hold. Recordings include reset/consumption; analysis selects stacks with
the actual production operation in their ancestry.

| Operation | Operation CPU samples | Operation allocation samples |
|---|---:|---:|
| Forward zero | 0 | 0 |
| Forward many | 31 | 34 |
| Forward empty | 25 | 54 |
| Add many | 114 | 3 |
| Pick empty | 2 | 6 |

Observed allocation origins include `NativeMenuEffects`, `StockTransferEffects`,
inventory effects, thread-local entries, required `ItemStack`/component facades,
cleaner records and release `AtomicLong` objects. Source still calls public
`ItemStack.copy()` from the menu effect; its component facade makes a nested
native copy/acquisition. Java retains genuine opaque identity anchors and
construction/callback effects while Rust owns payloads and operation control.
The component root bag is already shared across copied native backings; these
observations do not justify claiming a second parallel payload store.

Sampling weights contain large apparent carry-over/outlier values relative to
the direct thread-byte measurements. Use the recordings to identify allocation
origins, not class allocation fractions. CPU samples are sparse and include
foreign-call stubs; they do not split native work, synchronization and transition
cost or prove all latency is FFI. Native heap/peak, other-thread allocations and
deferred retirement costs remain unmeasured. Keep those unknowns explicit.

**Responsible owner: matt-alienwarem15r3 / S0024.** A proposed next ownership slice
would join actual copied-stack preparation/adoption and its VM identity-resource
publication to the existing native menu/inventory operation, preserving every
required public facade, `asItem`/custom callback, alias, failure prefix and GC
lease. Verify any reduction against these same complete workloads and native
resource evidence. This is a future proposal, not current implementation or
permission to reopen the frozen candidate. Generic identity lookup/mutation
and native lock/hash/table work also remain measured debt or attribution
unknowns; no optimal-native-implementation claim follows.

## Integrated native ownership observation

After ticket/generation publication `006afac17`, normal sync retained all pending
item work and all 189 item-family class bytes. The rebuilt combined native image
is `1eb51c6fcd3c215bd867385121a0628b63b1ce9d03e8f7f9d6482a97a644c401`;
do not relabel earlier latency measurements as testing this binary. Focused
item/server-level/chunk-publication integration checks pass 304 tests, with
4 skipped and no failures. Current-only replay matches all 10,079 immutable
original Frozen/pre rows. The integrated server separately passes the same 16
component transfer/save/reload controls.

The bounded resource probe reuses S27's reviewed Linux/glibc observer unchanged,
with known malloc/calloc/realloc and delayed cross-thread-free controls. It tags
only the original complete empty-destination chest transfer, after fixture
creation/reset. All 32 calls request **320 Rust bytes**, with median/max
incremental scoped live requested peak **320/320 bytes**. The actual returned
stack is intentionally retained: **160 tagged Rust bytes** remain while other
scoped native owners retire. Releasing that facade and observing normal
GC/Cleaner/reclaimer processing leaves **zero tagged Rust bytes in every scope**.
Tagged other-caller bytes also reach zero in these scopes. Original transfer
results and actual native handle/VM weak-reference lifetime are checked.

This is requested libc heap, not Java heap, usable allocation size, mmap/code
cache, all-thread allocation or process-native RSS. Untagged fixture/world/global
allocations are outside the scopes. Instrumented process peak RSS is separate
and includes Java and profiler bookkeeping. Forced GC observations are not
natural cleanup-latency measurements. Native resource scope beyond this
representative operation remains unverified; no all-process-retired claim is
made. Evidence is in `build/item-state-r6-integration-native-resources1`.

```sh
python3 DevUtils/tests/content/item-state/resource/VerifyItemResources.py \
  --java-home /path/to/jdk --output build/item-native-resources
```

## Reproduce the cost diagnostics

Compile and verify the selected image first; use new output directories.

```sh
python3 DevUtils/tests/content/item-state/MeasureItemState.py \
  --java-home /path/to/jdk --pre build/item-state-pre-646986043 \
  --pre-overlay build/item-state-pre-upstream-3f1d \
  --pre-overlay build/item-state-pre-r6 \
  --workload inventory --output build/item-complete-cost
```

Use `--workload generic-debt` for the companion control. These commands emit
raw evidence and explicit limits, never an acceptance verdict. Preserve earlier
failed and noisy results; the [owner's policy](../tooling/MIGRATION-ACCEPTANCE.md)
requires a documented decision for out-of-budget work.

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

## S0034 pending contents and native broadcast screen

**October 16–17, 2026: short regression screen under
[policy 317](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18984771).**
These results record temporary debt. They are not a Frozen acceptance, which
moves to the completed items/inventories milestone.

- **Pre-change Current:** published `11346d05`, sealed as
  `build/item-state-pre-s34-11346` (all 13,892 classes byte-identical to that
  build, release native `17be1be3…`, jar first in `classpath.txt`).
- **Origins:** every run uses `--complete-pre --origin-guard`. The guard caught
  one contaminated attempt (`build/s34-broadcast2`): the workload probed a
  Current-only class on the pre side. That run is not used.
- **Broadcast workload:** `--workload broadcast` (`BroadcastTimingReference`) times one
  `broadcastChanges` of an open stock chest menu with a read-only recording
  synchronizer/listener (`tick_idle`), or a quick move plus the broadcast that
  ships it. One JVM per side, five samples each.

| Workload (`build/s34-broadcast3`) | Frozen ns | Pre ns | Current ns | C/pre | Java B/op F/P/C |
|---|---:|---:|---:|---:|---|
| tick_idle | 1004 | 6429 | 3261 | 0.51 | 2520/3256/520 |
| tick_quick_move_empty | 1509 | 7906 | 8647 | 1.09 | 2808/3512/1240 |
| tick_quick_move_one | 2208 | 84476 | 14035 | 0.17 | 2864/23145/1560 |

The first screen (`build/s34-broadcast1`) measured `tick_quick_move_empty` at
1.54× pre. Each processed slot re-read every slot snapshot, cloning two Arcs
per row. Snapshots are now computed in place, and only the remaining slots
are refreshed, only after an observer callback ran.

The unchanged 25-row inventory workload (`build/s34-cost1`, one run) puts
`quick_move_empty`/`reverse_empty` at 0.84/0.82× pre, with Java bytes
468→336/464→320. The other rows are within single-run noise. Its consumption step reads every chest slot
outside the timer, so it excludes materialization.

Debt: every row remains above Frozen (idle broadcast 3.25×, changed tick
5.73×). Native allocation and process memory are not measured.

### Second S34 checkpoint: hoppers, containers, pickups

**October 17, 2026.** The candidate adds native hoppers, barrels, shulker boxes,
dispensers and droppers. It also adds comparator signals, saves without
materializing, native no-op inventory ticks and pending pickups. Same fixtures
and flags as above, one JVM per side, five samples. Medians use all samples
because some rows had fewer than three quiet samples.

| Broadcast (`build/s34-v3/broadcast`) | Frozen ns | Pre ns | Current ns | C/pre |
|---|---:|---:|---:|---:|
| tick_idle | 1456 | 6672 | 3433 | 0.51 |
| tick_quick_move_empty | 1579 | 10473 | 8042 | 0.77 |
| tick_quick_move_one | 9650 | 88087 | 10394 | 0.12 |

In the inventory workload (`build/s34-v3/inventory`), pre itself moved by up to
±30% per row between this run and `build/s34-cost1`. Only one row changed
consistently: **`add_damaged`**, with pre at 828/829 ns and Current going from 881 to
1076 ns (1.30× pre, Java bytes unchanged at 228). `add_empty` went from 1126 to
718 ns (0.71× pre, Java bytes 228→88).

The published 8200f037 text first blamed the `add_damaged` cost on pending damaged
publication (action 36). That was wrong. A counter probe showed the workload adds
into an occupied slot, so it never publishes pending contents (prepared +1,
pending +0). `publish_pending_add` copied the stack before checking that the row
was empty, then fell back to the prepared path, which copied it again. The row is
now checked before copying. After that fix (`build/s34-v5/inventory`),
`add_damaged` is 883 ns (1.07× pre), and every row is 0.67–1.07× pre.

Debt: the broadcast rows remain 2.4×/5.1×/1.08× Frozen, and the inventory rows
remain above Frozen (for example, `add_empty` 3.3×, `clicked_forward` 2.1×).

### Double-chest slot links

**October 17, 2026.** Six-row menus over an exact double chest link their slots to
the halves' native rows (see the double-chest menus note in [ITEM-STATE](ITEM-STATE.md)).
The broadcast workload gains two modes: `double_idle`, and `double_quick_move_one`
(the inventory source at menu index 54). Both halves are emptied in the
untimed reset. A first attempt (`build/s34-v6/broadcast-nonstationary`) left
overflow from earlier runs in place, so its checksum depended on how many
warmup rounds ran, and the driver correctly refused it.

| Broadcast (`build/s34-v6/broadcast`) | Frozen ns | Pre ns | Current ns | C/pre |
|---|---:|---:|---:|---:|
| tick_idle | 984 | 6141 | 3267 | 0.53 |
| tick_quick_move_empty | 2187 | 7429 | 8822 | 1.19 |
| tick_quick_move_one | 2116 | 74783 | 9151 | 0.12 |
| double_idle | 2131 | 9890 | 5275 | 0.53 |
| double_quick_move_one | 2752 | 21741 | 20240 | 0.93 |

The idle double-chest broadcast now skips unchanged linked rows. A six-row
quick move still transfers through Java `moveItemStackTo` (7.4× Frozen), which
is the next ownership gap. `tick_quick_move_empty` is an unchanged single-chest
path. Its Frozen and pre values also moved between runs (Frozen 1579→2187 ns),
so treat it as noise until it repeats.

Double-chest to inventory quick moves then became native too (`build/s34-v7/broadcast`):

| Broadcast | Frozen ns | Pre ns | Current ns | C/pre |
|---|---:|---:|---:|---:|
| tick_idle | 1053 | 6398 | 3298 | 0.52 |
| tick_quick_move_empty | 2108 | 7770 | 10563 | 1.36 |
| tick_quick_move_one | 2745 | 93764 | 14988 | 0.16 |
| double_idle | 1718 | 9331 | 5064 | 0.54 |
| double_quick_move_one | 2485 | 19885 | 18246 | 0.92 |
| double_reverse_one | 4213 | 180697 | 11830 | 0.07 |

`double_reverse_one` moves a stack from the second half (menu 31) into an empty
inventory. Unattributed variance: `tick_quick_move_empty` takes an unchanged
single-chest path. Across three screens Current read 8042, 8822 and 10563 ns,
while Frozen read 1579, 2187 and 2108 ns. As a ratio to Frozen it reads
5.1×, 4.0× and 5.0×, so it does not rise steadily, but it has not been attributed
and stays on watch.

### Pending-content resources

`VerifyItemResources.py --workload pending` (`build/s34-v5/resource-pending2`)
reuses the S27 scoped libc-heap observer. It covers each pending producer:
quick move, quick move then materialized, empty-slot pickup and damaged pickup.

- Each publication allocates 320 B (quick move) or 160 B (pickup) of tagged
  Rust heap. That memory stays live while the row exists and returns to 0 B
  when the row is emptied with its owner still reachable. The pending recipe
  map is empty after retirement.
- A held pending row keeps 336 B until it is replaced, then 0 B. This shows
  the accounting can see retention.
- A churn of 12 rounds × 4000 cycles of all four producers (192,000 pending
  publications) levels off: RSS after forced GC is about 816 MiB in rounds 7–11
  (−Xmx1g, observer preloaded), and the Java heap stays flat at about 133 MiB.

These are process samples, not allocator attribution. Untagged threads,
mmap and the allocator's usable size are excluded.

Correctness for this checkpoint:

- All 14 contracts are exact against the complete pre image
  (`build/s34-v3/contracts`). This run caught an `inventory_add` regression
  first (see the empty main slot note in [ITEM-STATE](ITEM-STATE.md)).
- `VerifyHopperRuntime.py` passes all 16 checks on Current (native mapped)
  and on Frozen. That includes a real item entity collected by a hopper and
  a save/reload.

Impact record for the reused broad tiers: Rust full 3118/0, Java ordinary
2610/0, parity 193 and slow 11 ran on `c5a9de42` plus the pre-fix diff
(`build/s34-v2-final`, fresh XML only). Since then there have been two kinds of change:

- **Item changes:** two edits inside `publish_pending_add` (move one item, and
  check the row before copying). The Rust item suite (68), the focused Java
  packages (175) and the 14 contracts were rerun on each image.
- **Upstream syncs:** `43f3c309`, `4c27ffce` and `c8ab7fb7`, covering lighting,
  decoration and noise, with no path overlap. Focused tests, contracts and the
  hopper witness were rerun on `c8ab`.

The broad tiers were not rerun on the published images.

## S0030 natively prepared copies

**S0030/r2, October 16, 2026: temporary cost exception accepted for publication.**
[Decision 310](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18984260)
accepts these costs as bounded temporary migration debt for this exact candidate.
It is not a Frozen-budget pass, and not a claim that all added cost is explained.
The debt stays with the item-state owner. The exit is native-owned container slot contents and
transfer publication, which needs a separate assignment.

- **Candidate:** published master `4199640e33c54a1b8408b25f7bb695446dbbabae`
  plus the S0030 diff. Release native
  `17be1be34bd5950c40fd6876d63c16dffc1e201866c4e38a3a83082e3b89f2cb`, compiled
  classes aggregate `80a837e8…`.
- **Pre-change Current:** the complete published `2b1835578c` image in
  `build/item-state-pre-s30-2b18-complete`. Classes jar
  `9abe40b5edc7f089bbcc43eab6afeffa95d8f4144cae53974fef5248f83def75`, native
  `d49b361d5fd0971a148ad0e493ac91bcb2e0ba2988aec6ecb97ff47750bb44a3`. It runs with
  `--complete-pre`, so no historical overlays are applied. Master moved from
  `2b18` to `4199640e` through S28/S32 chunk generation and docs only, and no
  item, inventory or menu path changed.
- **Frozen:** unchanged `7a4d181717dc0c7da9086f1a17687dfd522ce417`.

Each side ran three separate JVM processes, each with five nested dependent
samples per workload. The 15 samples are not independent trials. Every output
checksum matches across sides and runs.

`--origin-guard` reran each side's exact timed command once, untimed, with class-load and library
logging:

- Frozen loaded all 4,364 game classes from its own checkout and no MattMC native.
- Pre loaded all 4,489 game classes from the fixture `classes.jar` and its native
  from the fixture, with none from the live build.
- Current loaded 4,489 from `build/classes/java/main` and `build/rust/native`.

The timed processes separately verify the mapped native path, inode and SHA-256.

Under these measurements, the targeted latencies are approximately at pre-change
level: the `*_empty` rows and `add_damaged` are 0.95–1.02× pre, with unchanged
operation-thread Java bytes. This does not show that S0030 adds no debt:

- Java bytes exclude native allocation and retention.
- Facade/Cleaner allocation alone does not explain the latency.

Every row remains far above Frozen, as pre-change Current already was (the
S0024 debt above). The proposed exit is native-owned container slot contents,
so that transfers publish without a Java facade per copy. That needs a later,
non-overlapping assignment.

Times are ns/op: per-run medians of the five samples, then C/F and C/pre from
the median of all samples. `q` totals the compiler/GC-quiet samples.

| Operation | Frozen r1/r2/r3 | Pre r1/r2/r3 | Current r1/r2/r3 | C/F | C/pre | q F/P/C | Java B/op F/P/C |
|---|---|---|---|---:|---:|---|---|
| quick_move_zero | 19/19/23 | 67/61/75 | 61/75/61 | 3.02 | 0.83 | 6/8/6 | 0/0/0 |
| quick_move_full | 290/854/305 | 1332/1257/1312 | 1365/1291/1373 | 4.13 | 1.04 | 4/4/6 | 0/308/308 |
| quick_move_one | 386/365/394 | 1766/1412/1379 | 1519/1673/1546 | 3.99 | 1.09 | 12/13/13 | 56/308/308 |
| quick_move_late | 355/1109/371 | 1522/1597/1575 | 1519/3113/3861 | 7.97 | 1.90 | 11/10/3 | 56/308/304–308 |
| quick_move_many | 496/518/676 | 4486/4526/4670 | 4773/4672/5282 | 9.16 | 1.06 | 15/15/13 | 56/308/308 |
| quick_move_empty | 285/287/1375 | 1942/6834/1974 | 1863/2032/2082 | 6.80 | 0.99 | 13/10/13 | 112/464–468–472/472 |
| reverse_zero | 27/25/25 | 82/81/78 | 114/83/83 | 3.32 | 1.06 | 7/6/6 | 0/0/0 |
| reverse_full | 614/627/757 | 1613/1671/1631 | 1936/1832/1732 | 2.92 | 1.12 | 13/7/6 | 56/300/296–300 |
| reverse_one | 630/655/742 | 1650/1765/1682 | 1874/1950/1713 | 2.78 | 1.12 | 9/8/7 | 56/300/300 |
| reverse_late | 663/1141/727 | 1622/1844/1611 | 2039/1931/1675 | 2.66 | 1.16 | 12/10/9 | 56/296–300/300 |
| reverse_many | 600/567/833 | 2674/2966/2463 | 2893/2803/2394 | 4.56 | 1.05 | 12/11/10 | 56/300/300 |
| reverse_empty | 420/471/454 | 2679/2705/2146 | 2406/2483/2223 | 5.31 | 0.95 | 15/13/13 | 112/464/464 |
| add_zero | 7/7/8 | 20/18/17 | 20/22/21 | 2.97 | 1.19 | 12/12/11 | 0/0/0 |
| add_full | 4472/4633/4104 | 4955/4511/5058 | 5076/5349/5117 | 1.20 | 1.08 | 9/9/8 | 1832–1848/1912–2296/1912 |
| add_partial | 4338/3848/4062 | 5895/5236/5153 | 5460/5906/5102 | 1.37 | 1.00 | 13/12/11 | 1832–1848–2240/1912–2296/1912 |
| add_late | 4458/4092/4782 | 5940/4823/4929 | 5020/5448/5208 | 1.19 | 1.07 | 11/11/13 | 1832–1848–2240/1912–2296/1912 |
| add_many | 9988/10902/11739 | 15530/16198/13471 | 13635/13857/12826 | 1.28 | 0.89 | 14/11/11 | 0/64/64 |
| add_empty | 198/206/209 | 1202/1046/1085 | 1274/1035/1083 | 5.36 | 1.00 | 14/14/13 | 56/228/228 |
| add_alias | 30/35/34 | 234/229/229 | 219/207/213 | 6.49 | 0.92 | 15/14/13 | 0/64/64 |
| add_damaged | 65/58/61 | 914/860/977 | 920/1011/827 | 15.18 | 1.02 | 12/11/10 | 56/228/228 |
| pick_empty | 17/92/18 | 230/236/234 | 260/265/230 | 14.17 | 1.09 | 12/14/14 | 0/64/64 |
| pick_displace | 303/125/62 | 579/512/524 | 601/626/559 | 4.81 | 1.14 | 14/15/14 | 0/64/64 |
| pick_full | 196/262/101 | 601/525/515 | 693/542/650 | 3.40 | 1.23 | 12/12/11 | 0/64/64 |
| clicked_forward | 5604/4900/5295 | 11187/9753/11530 | 11609/10066/10907 | 2.04 | 1.00 | 13/12/14 | 2184–2224/3128–3152–3536/3152 |
| clicked_reverse | 5909/5012/6218 | 12344/10938/11543 | 14496/12046/13725 | 2.25 | 1.13 | 11/13/11 | 2184–2224/3104–3116–3512/3088–3128 |

Short rows are noisy. Per-run Current/pre ratios on sub-100 ns rows vary by
up to ±40%. `quick_move_late` (1.00/1.95/2.45, only three quiet Current
samples) and `pick_full` (1.15/1.03/1.26) are not explained, and those rows are
not targeted by S0030. Host load averaged 1.4–2.7 with zram swap active.

Follow-up for the two unexplained rows (dot review 307):

- **Work counts.** `PreparedPathWorkCounts` instruments the four item callback
  entry points on both images. Per operation, `quick_move_late` makes the same
  native transfer steps, slot visits and callbacks as `quick_move_one`,
  `quick_move_full` and `quick_move_many`. Current adds exactly one prepared
  snapshot (callback 17) to each. The pick rows prepare nothing and make one
  identical inventory callback, or two for `pick_displace`. Their only change is
  the wider five-argument inventory callback. Receipt: `build/s30-workcounts1`.
- **Narrow retiming.** `SelectedInventoryTiming` reran only `quick_move_one`,
  `quick_move_late` and the three pick rows. It ran five alternating pre/Current
  process pairs on the same release image, with unchanged fixtures and the
  mapped native recorded. Current/pre: `pick_full` 0.99 (per pair 0.87–1.08),
  `pick_empty` 1.05, `pick_displace` 1.02, `quick_move_one` 0.97.
  `quick_move_late` is 1.16 (per pair 0.92–1.34). Receipt: `build/s30-narrow1`.

The earlier `pick_full` increase did not reproduce. About +16% on
`quick_move_late` remains unexplained: the control row doing identical counted
work measures 0.97.

Raw receipts and origin-guard logs are in `build/s30-recon-cost/cost-{1,2,3}`.
The equivalent un-guarded runs on base `2b1835578c` are in `build/s30-final3`.
Two guard attempts that failed on the guard itself are kept: Frozen has no
`NativeItemState` class and no MattMC native. Their timings are not reused.

The first fixture, `build/item-state-pre-s30-2b18`, omitted its own
`classes.jar` from `classpath.txt`. Its pre side therefore ran
`build/classes/java/main` against the old native, plus the automatic historical
overlays. Those receipts (`build/s30-baseline-inventory1`, `build/s30-final1`,
crashed `build/s30-final2`) are invalid as pre-change Current. They are kept
as failed evidence, together with the earlier GC/warmup explanation, which is
withdrawn.

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

Add `--workload pending` (with optional `--rounds`, `--cycles` and
`--timeout`) for the S0034 pending producers, the held control and the RSS
churn (see [pending-content resources](#pending-content-resources)). Only the
measured operations take observer tags, because the observer has 16,384.

## Reproduce the cost diagnostics

Compile and verify the selected image first; use new output directories.

```sh
python3 DevUtils/tests/content/item-state/MeasureItemState.py \
  --java-home /path/to/jdk --pre build/item-state-pre-646986043 \
  --pre-overlay build/item-state-pre-upstream-3f1d \
  --pre-overlay build/item-state-pre-r6 \
  --workload inventory --output build/item-complete-cost
```

Use `--workload generic-debt` for the companion control. When `--pre` is a
complete compiled image whose `classpath.txt` starts with its own `classes.jar`,
add `--complete-pre`. This skips the automatic historical menu, inventory and r5
overlays. Add `--origin-guard` to record where each side loaded its classes and
native library from, using untimed reruns of the exact timed commands. With a
complete pre image, any game class loaded from the live build fails the run. These commands emit
raw evidence and explicit limits, never an acceptance verdict. Preserve earlier
failed and noisy results; the [owner's policy](../tooling/MIGRATION-ACCEPTANCE.md)
requires a documented decision for out-of-budget work.

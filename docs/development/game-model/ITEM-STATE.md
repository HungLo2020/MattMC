# Retained item runtime state

**S0024/r6: bounded migration accepted for publication, October 16, 2026.**
Rust owns runtime patch/default structure, stack item/count/pop time, exact chest
slots, exact player main inventory state and equipment membership. Integer, null
and native enchantment payloads live in the actual component backings. Java
retains facade identities, foreign opaque payloads and external effects.
[Persistent component codecs](ITEM-COMPONENTS.md) and
[item definitions](ITEM-DEFINITIONS.md) are separate boundaries.

Mutable enchantment builders and codec/stream decoding use native payloads and
hash membership, with live entry/iterator/spliterator facades. Immutable views
share their builder's map; another builder copies it. The constructor taking an
existing Java map retains that physical backing and its alias behavior. Stack,
menu and inventory operations consume the connected native payloads.

The [cost review](ITEM-STATE-COSTS.md) records all 25 complete-operation costs,
generic access debt, bounded attribution and native lifetime controls.
[Decision 277](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18978586)
accepts the disclosed latency/Java-allocation envelope as temporary debt.
[Decision 279](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18978903)
accepts the integrated image and bounded native resources, carrying that cost
exception through unchanged item sources/classes and affected validation.
The normal performance budgets are exceeded; broader migration and the proposed
ownership exit remain future work. Historical image-specific checkpoints below
retain their original limits and do not describe current approval status.

## Working on this boundary

Start in
[`NativeItemState`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/item/NativeItemState.java)
and the
[Rust runtime](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/content/item/runtime).
`NativeItemSlots` binds public list facades to actual native membership. Registry
definitions are immutable metadata, not mutable stack storage. Call-local codec
slots must never become retained owner identities.

An exact `Inventory` has one native owner for its main 36 slots, selection and
wrapping change counter, linked to the exact native equipment store. Its returned
`NonNullList` is the same live facade on
every call; iterator/sublist writes address that owner. Inventory subclasses keep
the original separate storage and virtual behavior. Equipment membership uses a
native enum-ordered store that distinguishes absent, present-null and actual stack
entries; Java retains key/facade identity and external entity effects.
`EQUIPMENT_SLOT_MAPPING` is public and mutable: a main-slot write can also route
to equipment, after the main write has already committed.

Inventory-add now keeps its loop, destination search, merge arithmetic and
count/pop-time writes in those actual owners. Hotbar selection, remaining-space
search, item matching, crafting scans and add-and-pick orchestration also run in
Rust. Newer source reads actual typed damage and enchantment payloads for controlled
backings; foreign prototypes, malformed types and user equality still take the
original Java effects. Equipment access, facade construction, creative behavior
and crash reporting also remain Java effects.

The inventory visitor reads each current slot in Rust and
resolves its captured facade from the owner's existing binding bag before Java
effects; it does not create another slot array. Save, tick and drop skip actual
empty stacks. Predicate/accounting visitors still see EMPTY. Contains captures
container size once; replacement checks the live size at every iteration. Load
clears main storage before obtaining its original lazy iterator. No native gate
or component borrow crosses any effect.

Exact chest and inventory menus now use a native roster and carried-stack owner.
The roster retains actual slot/container endpoints; Java bindings resolve facade
identity and GC reachability. Native code owns direct quick-move routing, repeated
QUICK_MOVE and complete destination-transfer continuation. The current source
continues an admitted destination transfer inside the menu's native operation;
Java supplies its existing effect context rather than starting a second native
run. Required source and
snapshot facades exist before mutations. Initial-slot capture remains distinct
from the current roster selection in each repeated action. Specialized equipment,
crafting, take/drop and world dirty methods remain original Java effects.

## Contracts to preserve

- Detach at the original COW point before prototype lookup/equality. Callbacks can
  expose a patch, copy or reenter. Never replay them or keep a native guard across
  Java effects. Restore the previous effect frame after nested native calls.
- Patch builders and escaped patches share their original backing until explicit
  COW. Retain cached key-view identity, live removal, hash/array iteration and
  source-specific iterator/spliterator behavior, including negative `skip` counts.
- Opaque payload roots belong to the exact backing version. Publish Java binding
  rows with release/acquire on the same slot and validate the complete incarnation.
  A native fence cannot publish an unrelated Java heap object. Do not globally
  root facade cycles or retain every payload ever assigned.
- A stack can occupy several slots or be its own transfer destination. Preserve
  sequential wrapping count writes, EMPTY/AIR/null-item masks, negative limits
  and original copy/constructor/`asItem` effects. Materialize required snapshots
  and inserted facades before shrinking a source or publishing membership.
- Preserve error prefixes: selection can change before a bad pick fails; damaged
  add clears its source before a bad destination write fails. Inventory-add's
  return depends on its last loop iteration, not total progress.
- Equipment/player/world dispatch stays live. Preserve every world dirty call;
  native inventory counter increments do not authorize combining world effects.
- Save/load/reporters are live and lazy. Main inventory clears before load visits
  entries. Save emits main slots only. Preserve iterator progress and reporter
  order rather than serializing a detached container snapshot.
- Enchantment `Mutable.toImmutable()` shares its map; constructing another
  `Mutable` copies it. Constructor levels allow 0–255, while persistent levels
  allow 1–255. Outer unmodifiable entry sets still contain mutable live entries.
  Preserve actual holder hash/equality, null keys, entry positions and callback
  error prefixes. A query captures its old key array before hashing, but reads
  the mask and value array after callbacks. No native guard crosses these effects.

## Allocation and callback lifetime

A stack promotes its existing component owner without creating another Java
stack lease or native registry handle. Unique unretained component storage can
move into the unpublished native stack; real aliases keep their original shared
allocation. Copied stacks embed their overlay. Compatibility aliases resolve the
actual containing owner without an ancestry chain.

The public `PatchedDataComponentMap` is the Java lease object shared with its
stack, including native stack copies. Its constructors register handle-only
rollback before acquiring native ownership; an unbound component-only copy can
later promote that same owner. Do not reintroduce a composed private overlay or
adopt a copied stack after publishing its membership/count mutations.

Stock chest backing selection lives in a replaceable native container cell.
Existing slots follow that cell through replacement and content swaps; escaped
old lists keep their own owners. Java holds one current facade identity anchor.
Foreign lists, null backing and pending loot execute original Java getter/effect
paths. Private main/chest slot indices live in retained native slot bindings;
public mutable UI indices keep their original Java behavior.

Private scalar/row views require real native allocation leases. Derived slices
retain the same implicit arena scope; cleanup owns only an idempotent handle
release action. Allocate rollback before acquisition, and use the same action
for adoption failure and eventual cleanup. No raw pointer or generation number
alone proves lifetime. Fence the actual owner through the last memory access.

The complete production transfer retains actual source/destination allocations
through one native run. Forward and reverse scans consume their correct owners;
every row/default guard ends before Java effects. Resume foreign behavior at the
original phase/index without repeating committed work. The bounded 27-slot/128-row
helper remains a focused lower-level control, not complete-operation evidence.
Default semantic indexes resolve canonical rows, not duplicate scalars.

Component COW publication stages the actual next native backing and matching VM
root bag before a version-checked commit. Component writes stage optional/type
roots and retirement capacity before native membership changes. The native result
decides which opaque bindings survive; retirement must not allocate or retain
historical payloads. Keep all foreign prototype/equality callbacks at their
original positions, outside owner guards.

Complete fallible payload-table reservations before relocating an entry buffer
addressed by a published read view. Pinning the backing/header does not keep a
freed `Vec` buffer alive. No recoverable failure may leave the header pointing
at that old buffer; the final buffer move must be followed by non-failing
publication. The standalone model driver injects real first/second allocator
failures and checks staged native enchantment lease retirement.

Opaque facade bags use primitive native tokens, with growth before publication
and no shrinking allocation during retirement. Slot writes and insertions ask
the canonical native rows whether the previous facade still has another alias;
Java keeps no slot-reference counter. Component bags retain their original
Optional wrappers directly. An exact empty-source check reads the current native
menu endpoint before constructing an effect frame; unknown endpoints and chests
without current loot-clear admission keep their original getter/effect path.
The connected GC-binding image `03c3bcae…` passes 76 focused Java tests,
77 native models and all 10,010 current comparison rows. The exact pair is
preserved in `build/item-state-r5-gc-binding-sealed1`; it has no current
cost-acceptance claim.

The newer source reads roster/slot facade tokens through bounded private entries
that borrow the actual retained allocation. Busy gates resume the regular read;
no pointer or generation number substitutes for the owning lease. Exact-slot
has/get/copy effects execute in their original order within one callback, with
both required facades strongly anchored before Rust consumes them. Unknown or
foreign endpoints keep their original separate callbacks. Image `13d34ed4…`
passes 76 focused Java tests, 80 native models and all 10,010 current comparison
rows; its exact pair is preserved in `build/item-state-r5-bounded-facade-sealed1`.
Its complete diagnostic cost is recorded below and remains unaccepted.

The next source change consumes actual allocation leases directly in menu
callbacks. Java effect fields keep the owning slot/source/snapshot/destination
facades alive until Rust acquires its own Arc; Rust retains that Arc before a
later effect can clear the Java anchor. Inventory destinations use the outer
inventory allocation, not an Arc reconstructed from its inline slot address.
Ordinary stack-copy downcalls borrow the fenced source allocation and keep the
existing preregistered rollback/adoption path for the new copy. These are private
contracts, not public raw-pointer constructors. A pointer without the matching
live owner is invalid. Cleanup policy remains unchanged. Image `c854ed89…`
passes 77 focused Java tests, including GC during a nested dirty effect followed
by detached-source retirement, 84 native models and all 10,010 current comparison
rows. Its exact pair is preserved in `build/item-state-r5-menu-lease-sealed1`.
It has not been cost-measured.

The current source extends this same allocation-lease contract to slot
publication and inventory add/scan/visit. Publication borrows the real row owner
while the VM binding-bag monitor and owning lease remain held; it retains the
inserted allocation before changing membership. Inventory operations acquire
their own native Arcs before effects. Identity scan mode 5 still consumes a
facade token, because two different facades can share one actual allocation.
Image `69974542…` passes 77 focused Java tests, 87 native models and all 10,010
current comparison rows. Its exact pair is preserved in
`build/item-state-r5-operation-lease-sealed1`. At that historical checkpoint, broader affected checks and complete cost were
pending; later r6 results and disposition supersede that status.

## Complete stock-chest ownership

Both stock 27-slot chest directions now use native destination owners. Reverse
order is hotbar 8..0 followed by main 35..9. Each original inventory change-counter
increment occurs on its actual native counter. Empty insertion still constructs
its Java facade before publication and executes equipment/world effects in order.
Source cleanup uses the captured source stack's live emptiness after effects.

Admission checks the actual current slot mapping and backing. Reverse native
admission requires exactly 63 UI slots; a modified menu uses the original captured
end index. Custom slots/lists, pending loot, foreign component/prototype behavior
and effectful item copy dispatch resume the original remaining Java phase.
The continuation preserves phase, next index and accumulated change without
repeating committed work.

## Correctness checks

Use JDK 25, existing compiled Frozen/current classpaths and immutable baseline
fixtures. Preserve pinned Frozen `7a4d181717dc0c7da9086f1a17687dfd522ce417`, the
pre-646986043 class/JNI fixture, original menu overlay and original inventory
family (`build/item-state-pre-inventory`). Never regenerate a baseline from
modified source. The driver pins observer sources, compiled class families and
actual mapped native images. The added original Slot/Container/EntityEquipment/
InventoryMenu families are sealed in `build/item-state-pre-r5`; reference reuse
checks every supplemental overlay and refuses older receipts without it.
After the lighting sync, also supply the immutable original lighting overlay
`build/item-state-pre-upstream-3f1d` and original enchantment overlay
`build/item-state-pre-r6`. These preserve reference behavior when current classes
are rebuilt; they do not advance the pinned reference sources.

```bash
python3 DevUtils/tests/content/item-state/VerifyItemState.py \
  --java-home /path/to/jdk-25 --pre build/item-state-pre-646986043 \
  --pre-overlay build/item-state-pre-upstream-3f1d \
  --pre-overlay build/item-state-pre-r6 \
  --observers inventory inventory_add inventory_actor \
  --output build/item-state-inventory-check
```

`inventory_actor` uses actual `AbstractContainerMenu.clicked` repeated QUICK_MOVE
as well as direct calls, both directions, aliases and modified menu sizes.
`inventory_add` exercises actual public add, callbacks, creative fallback and
error prefixes. They need installed Mockito/Byte Buddy/Objenesis test dependencies;
Byte Buddy's experimental JDK parser is enabled only in these observer JVMs.
Mocked external effects provide correctness evidence, not gameplay cost evidence.

Other observer names include `contracts`, `default_cursor`, `default_skip`,
`default_entry`, `stock_signed`, `copy_effects`, `scalar_presence` and
`default_bootstrap`. Use `inventory_visit` for lazy input/output, tick, accounting,
live routing changes, nested callbacks and error prefixes. Focused Java tests are
`NativeItemStateTest`, `NativeInventoryTest` and `NativeEquipmentTest`; standalone Rust model drivers
live in `DevUtils/tests/content/item-state/`. Run checks appropriate to changed
ownership and effects before the broader [acceptance procedure](../tooling/MIGRATION-ACCEPTANCE.md).

Use `enchantment_payload` for the original shared/copy, null, validation,
live-entry, hash/equality reentry, predicate and tooltip contracts. Its first
36 rows match Frozen/pre/current. `NativeEnchantmentRootsTest` separately
executes the new native store through FFM, including actual anchor allocation
failure, rehash, reentrant equality and GC retirement. The first partial backend
image `23e229ce…` passes 83 focused Java tests and 96 imported-runtime models;
these checks do not certify the unfinished consumer integration. The unchanged
combined pre-backend image `bc7bfd60…` passes 10,010 three-way composition rows
and 95 focused item/lighting tests. All 48 Frozen/pre output dumps remain
byte-identical to the preserved pre-sync references.

The removal image `f1d3c4b…` passes 88 Java tests and 98 imported-runtime models;
the cursor image `3bbdf736…` passes 93 and 104 respectively. Exact paired artifacts
are retained under `build/item-state-r6-remove-sealed1` and
`build/item-state-r6-cursor-sealed1`. The original enchantment observer now has
48 exact rows: the earlier 42 remain byte-identical on every side. Added controls
cover live entry hash/equality order, wrapped iterator removal, consumer failure
resumption and independent split ranges. These original-backend observations
remain the oracle for subsequent comparisons. The complete native-map image
`8401bb33…` passes all 48 enchantment rows, the unchanged 10,010 composition rows,
96 focused Java tests and 107 imported-runtime models. Its paired source, classes,
library and receipts are retained in
`build/item-state-r6-enchantment-composition-sealed1`.

Use `component_payload` for 21 additional original controls covering shared versus
copied enchantments, scalar COW, callback replacement/reentry/error prefixes,
directional opaque equality and malformed component short circuits. Their original
Frozen/pre outputs are retained in
`build/item-state-r6-component-consumer-original1`. These references do not certify
the newer typed producer/storage/consumer source on their own. The connected image
`6bd35b9f…` passes all 10,079 composition rows, 97 focused Java tests, 117
imported-runtime models, 60 actual-crate item tests and the full Rust suite
(3,010 passed, 9 ignored). Its exact source/classes/library and comparison receipts
are in `build/item-state-r6-connected-composition-sealed1`. The first ordinary run
exposed Mockito maker conflicts and a registry-bootstrap timeout. Removing the
second maker from the inventory JUnit setup retained every behavior assertion;
the fresh run passes 2,308 ordinary tests (6 skipped), 193 parity and 11 slow,
with all source/class/native guards intact. Keep the failed run in
`build/item-state-r6-connected-java-gates1` and the corrected run in
`build/item-state-r6-connected-java-gates2`.

That image remains held: a later real allocator-failure control reproduces a
freed published entry-buffer pointer when the second native reservation fails.
Exact failing source/binary/logs are retained in
`build/item-state-r6-failed-reservation1`. The corrected reservation order passes
119 imported-runtime models, including both failure positions and actual native
enchantment lease retirement. Replacement image `56f6aad8…` also passes 97 focused
Java tests, all 10,079 composition rows, 60 actual-crate item tests and full Rust
(3,010 passed, 9 ignored). Its exact pair is retained in
`build/item-state-r6-reservation-composition-sealed1`. Final Java tiers separately
pass 2,308 ordinary tests (6 skipped), 193 parity and 11 slow with source, class
and native guards intact, retained in
`build/item-state-r6-connected-java-gates3`. These correctness results alone do
not establish performance acceptance; the later cost and integrated-image
dispositions are recorded in the [cost review](ITEM-STATE-COSTS.md).

Use the default Mockito maker throughout JUnit tests. Registering the separate
subclass maker can make Mockito 5.8 return another maker's abstract-mock handler
in later tests. Standalone preserved observers retain their original settings
in their own JVMs.

For real component decoding, hopper copies and save/reload, compile the selected
image first, then run:

```sh
python3 DevUtils/tests/content/item-state/VerifyComponentRuntime.py \
  --java /path/to/jdk/bin/java \
  --classpath-file build/item-state-current-classpath.txt \
  --native-dir build/rust/native \
  --output build/item-component-runtime
```

The driver uses a new loopback-only dedicated server world, checks exact item and
component membership before/after transfer and after restart, and retains logs,
binary hashes and native mappings. It never opens an existing save. Omit
`--native-dir` only for the Java-only Frozen reference. Frozen passes all 16
controls. The preserved pre-change native fixture cannot start a full world:
it lacks the unrelated `mattmc_light_tasks_create` symbol. Keep that failed
attempt and the original fixture intact; its item-only composition receipts do
not certify whole-server startup. Current also passes all 16 controls on
`6bd35b9f…`, with its intended native image mapped and both server exits zero.
Both Frozen and Current log the same 11 startup data/tag/recipe errors on each
run; those matching diagnostics are retained, not described as clean startup.
These runtime receipts describe `6bd35b9f…`; they do not certify the later
allocation-failure correction or provide cost acceptance.
The replacement `56f6aad8…` separately passes the same 16 runtime controls, with
both server exits zero and the intended native image mapped. Its receipt is in
`build/item-state-r6-component-runtime-current2`; this remains runtime/save
coverage rather than a cost or client result.

Use `equipment` for absent/null/EMPTY membership, live enum iteration, self-copy,
clear/drop/tick failure prefixes and codec snapshots. `container_slot` preserves
captured-size/live-getter iteration, custom iterator dispatch, slot callbacks,
reentry, aliases and delayed null-container errors. `inventory_menu` exercises
actual armor/offhand equip hooks and crafting-result quick-craft/take/drop order.
Use `menu_state` for list/carried identity and error prefixes, `menu_source` for
captured getters/nulls/EMPTY/reentry, and `equipment_inventory` for live routing,
nullable equipment and foreign getter behavior.
Specialized slot methods remain Java effect boundaries.

Default-map construction must work before registry bootstrap. Bind semantic role
identities at the existing post-registration binding point. Explicit null prototype
values differ from key presence in patched-map `has` checks. Preserve hash-entry
live values/`setValue`, array-entry snapshots and fast-entry reuse. Escaped entries
must release replaced or unrelated values as their original contracts require.

The final R4 image `c9c12ae1…` passed 60 focused Java tests, 58 native models and
240 current observer rows. The connected R5 equipment/menu/component image
`0de307e0…` passed 73 Java tests, 73 native models and 10,010 current rows,
including the 9,600-row component trace. Its exact pair is preserved in
`build/item-state-r5-equipment-link-sealed1`. This certifies that image.
None of these checks establishes performance acceptance.

Two later private VM-binding failure probes on the sealed `ac90aee9…` image
exposed premature publication: COW could detach before its VM root clone failed,
and a type-binding failure could leave a native member without its type root.
Both witnesses retain source/classes/native/commands/results. They inject actual
private binding-map failures; they do not claim an actual OOM or Frozen failure.
The staged-publication image `a39da124…` now passes 76 focused Java tests,
74 native models and 10,010 exact current observer rows. Its corrected Java/native
pair is preserved in `build/item-state-r5-root-publication-sealed1`.
The first broader trace failed because an equal present patch entry reached the
native removal branch after its unused binding allocation was eliminated. That
failed pair remains in `build/item-state-r5-root-publication-failed-sealed1`;
the corrected call retains the present/removal distinction without publishing
an unused leaf. Preserve these
controls alongside original null-message, bootstrap, presence, entry and
modified-menu witnesses; never replace expected output to conceal a failure.

## Historical cost failures and remaining work

Eight successive complete-transfer diagnostics failed the applicable
budget. Preserve their per-workload receipts, including noisy samples and earlier
correctness failures. These are one-JVM diagnostics with dependent samples;
they establish no acceptance or final regression estimate.

| Native image | Local receipt directory | Many merges, Current/Frozen µs | Empty insertion, Current/Frozen µs |
| --- | --- | --- | --- |
| `be22b184…` | `build/item-state-stock-cost1` | 11.72 / 0.71 | 2.48 / 0.41 |
| `cf7f5a35…` | `build/item-state-owned-transfer-cost1` | 4.96 / 0.68 | 1.67 / 0.29 |
| `1261e2d5…` | `build/item-state-stack-lease-cost1` | 5.25 / 0.66 | 1.42 / 0.28 |
| `eea5487f…` | `build/item-state-admission-cost1` | 3.50 / 0.68 | 1.62 / 0.28 |
| `36f5876d…` | `build/item-state-bounded-cost1` | 2.72 / 1.20 | 1.40 / 0.30 |
| `170b733f…` | `build/item-state-combined-cost1` | 2.43 / 0.58 | 1.32 / 0.30 |
| `e15829ca…` | `build/item-state-embedded-cost1` | 2.37 / 0.51 | 2.40 / 0.29 |

Earlier isolated owner/canonical-cell diagnostics also failed generic
get/set/count/copy costs. Native allocation reduction did not resolve that debt.
The final R4 diagnostic (`build/item-state-r4-inventory-cost1`, native
`c9c12ae1…`) retained all six original forward workloads and added 19 reverse,
add/pick and actual repeated QUICK_MOVE shapes. All 25 workloads exceeded the
25% Frozen allowance and none improved pre-change Current. Every checksum and
save check matched, which does not make the cost acceptable. Preserve each row
and its allocation count; see the
[recorded per-workload results and R5 scope](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18966503).

The first connected R5 diagnostic (`build/item-state-r5-connected-cost1`, native
`03c3bcae…`) completed with identical checksums and all 125 save checks per side.
Twenty-three of its 25 workload medians exceeded 25% Frozen; none improved
pre-change Current. `add_partial` and `add_many` were numerically 18.6% and 21.2%
above Frozen, without a new attribution or acceptance claim. Several workloads
had only one or two quiet samples. Allocation reduction did not make this image
feasible: forward/reverse empty-source calls allocate zero bytes but still cost
173/223 ns. Keep all raw samples and the
[individual results](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18971680).
The joined menu/transfer image `0cca2eb2…` uses bounded actual-owner empty-source
access and passes 76 focused Java tests, 79 native models and all 10,010 current
comparison rows. Its exact pair is retained in
`build/item-state-r5-unified-menu-sealed1`. It has not been cost-measured.

The complete joined-operation diagnostic (`build/item-state-r5-operation-cost1`,
image `13d34ed4…`) again matched all checksums and 125 save checks per side.
Twenty-three workloads exceeded 25% Frozen. `add_late` was numerically 24.4%
above Frozen without new attribution; `add_many` was faster than both references
in this run, with substantial baseline variation from the preceding run.
`reverse_zero` had no quiet pre-change sample. Empty-source calls still cost
71/80 ns, with zero allocation. Copied forward actions retain 308–472 bytes
versus 0–112 Frozen bytes; repeated actions retain about 3.1 KB versus 2.184 KB.
Preserve the [per-workload results and limitations](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18972385);
one improving workload does not establish overall feasibility or an exception.

The final pre-sync allocation-lease image `69974542…`
(`build/item-state-r5-allocation-cost1`) also fails overall: 22 of 25 workloads
exceed 25% Frozen, with identical checksums and 125 save checks per side.
Forward/reverse empty-source medians are 81/152 ns versus 18/32 ns Frozen,
with zero Java allocation. `add_many` alone is 0.870 times Frozen and 0.808
times pre-change; that result does not offset the other workloads. Retain the
missing quiet pre-change reverse-zero sample and the measured facade/ownership
debt. R6 addresses actual component payload production and consumption; no new
accessor or representation timing series is an accepted remedy.

One bounded JFR attribution identifies boxed facade tokens, slot publication
bindings, copy/cleanup facades and native scan work. Native downcall samples do
not distinguish transition overhead from Rust algorithm cost. Discard the first
allocation sample per thread when interpreting these recordings: disabled-recording
gaps inflated its weight. Exact allocation totals remain in the timing receipt.

R5 connects replaceable container owners, private slot bindings, menu actions and
stack/component facade lifetime. Complete that ownership slice and affected
correctness before another cost campaign. Keep the unchanged 25 workloads and
all facade/copy/adoption/GC costs. The general 10% or attributable-boundary 25%
Frozen allowances are alternatives; native algorithm overhead is not automatically
boundary overhead. Ordinary/parity/slow/native checks and remaining gameplay gates
were still required at that historical checkpoint. The later r6 exception and
publication disposition are recorded in the [cost review](ITEM-STATE-COSTS.md).

For a later connected cost checkpoint, after all builds/tests stop:

```bash
python3 DevUtils/tests/content/item-state/MeasureItemState.py \
  --java-home /path/to/jdk-25 --pre build/item-state-pre-646986043 \
  --pre-overlay build/item-state-pre-upstream-3f1d \
  --pre-overlay build/item-state-pre-r6 \
  --workload inventory --output build/item-state-inventory-cost
```

This keeps the six original forward workloads unchanged, then adds 19 reverse,
add/pick and actual repeated-caller shapes. All required facades/copies are timed;
fixture reset, output consumption and serialized save checks are outside the
timer. External Player behavior uses the same stub-only mock on every side.
Thread allocation excludes Rust. The driver verifies original inventory/menu
class overlays and records their manifests. This diagnostic still uses one JVM
per side and dependent samples; it cannot establish final acceptance.

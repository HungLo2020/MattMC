# Fallen-tree generation

S0027/r3 implements native invocation control for the complete fallen-tree
feature and its attached-log mushroom decoration. The verified candidate runs their complete
invocation control in Rust, including the native random-count handoff. Java
retains actual world/provider/list/context effects. Compiled fallen programs now
consume the same Rust cursor against existing native region writes. The attached
and vine region consumers pass the focused real-server controls; custom or
mutable effect resources retain their genuine VM operations.

The five configured variants are oak, birch, super birch, jungle and spruce.
Their biome selectors, providers and decorators come from
[TreeFeatures](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/data/worldgen/features/TreeFeatures.java)
and [VegetationFeatures](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/data/worldgen/features/VegetationFeatures.java).
The current placement algorithm is
[FallenTreeFeature](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/levelgen/feature/FallenTreeFeature.java);
[AttachedToLogsDecorator](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/levelgen/feature/treedecorators/AttachedToLogsDecorator.java)
now enters the shared native invocation. This page records the verified scope, temporary migration-cost acceptance and
remaining ownership debt.

## Preserve the observable boundaries

- Place and decorate the stump before drawing direction, length and offset.
  Rejecting the fallen log keeps the stump and still returns true. Ignore the
  original `setBlock` boolean, and preserve flags 3 for logs and 19 for decorations.
- Preserve the six ground probes, support-gap reset and direct-constructor values
  outside the codec's range. The support state is read below the log but
  `isFaceSturdy` receives the original log position.
- Preserve Java HashSet order before stable Y sorting and shuffling. An attached
  decoration draws direction before the `<=` probability test, then tests air,
  samples its provider and writes.
- Configuration and direction lists can be mutable. Keep their original live
  reads, iteration, callbacks and failure prefixes; a tag or identity cache cannot
  freeze them. Retained mutable positions and nested callbacks also remain live.
- Reuse actual native region owners and the existing
  [FEATURES transaction](../RUST-FULL-GENERATION.md#the-features-transaction).
  Do not substitute a standing-tree configuration or add standing-tree leaf work.

## Capture and compare

The real-server observer lives in
[DevUtils/tests/worldgen/fallen](https://github.com/HungLo2020/MattMC/tree/master/DevUtils/tests/worldgen/fallen).
It calls actual public feature methods in isolated real `WorldGenRegion`s and
proto chunks, recording ordered effects, random state and chunk metadata.
A candidate needs an explicit classpath file and native directory:

```sh
python3 DevUtils/tests/worldgen/fallen/VerifyFallen.py \
  --output build/fallen-tree/<candidate> \
  --current-classpath <complete-classpath-file> --current-native <native-directory> \
  --native-parent --extended --logical-parent --expected-fixtures 85
```

`--capture-reference` deliberately uses the sealed pre-change image as the
untouched current reference; it does not test later production edits.
`--native-parent` additionally exercises existing native selector programs on
pre-change/current, with the original Java selector on Frozen. `--marker` makes
entry into the original Java fallen-tree/attached-log body fail; use it only for
intended native-admission controls, with matching `--fixtures` and
`--expected-fixtures`. The five stock parent variants also support
`--program-marker`, which fails entry through the old configured-feature
adapter. `--native-decorations` requires zero Java flags-19 writes for those
stock parents and a nonzero Frozen counterpart. The prior compiled fallen
program still makes 11 such VM writes and fails that consumer criterion.

`--logical-parent` removes Java block-method entry probes and the first
intercepted cursor from parent comparisons. Native region code owns those
operations directly. Genuine callback events, physical block/metadata/entity
images and RNG remain exact. The same observer retains full ordered traces for
direct calls. Its original 72 Frozen rows were audited against the earlier
64+7+1 receipts; the later decoration-write counter leaves all fixture JSON
bytes unchanged. Preserve both observer versions and their input guards.

## Current evidence and remaining gates

The original sealed development candidate is native
`993d1635c7658e9fcfe376373577b77fa15b5a44d0fc600e0d9db235ecb09951`
with the Java7 overlay at `build/fallen-tree/s0027-edge-verified-inputs-7`.
It matches all 85 original Frozen fixtures, passes 15 stock native body/adapter/
write markers, and passes 15 original-Java controls with
`-Dmattmc.worldgen.javaDecoration=true`. The original controls remain the
expectations, including inherited pre-change failures. Native selector counter,
checked-error and mutable-origin regressions are covered by the same controls.

Two lifetime/read-boundary regressions were found and corrected before these
results. A retained decorator context must not retain its completed invocation;
its setter captures only the actual world. Reads can also reach farther than
native region writes: attached/vine air tests use the actual level read cache.
A fallen frame that reaches outside its bound write region continues the same
native control frame through genuine VM world effects, preserving refused-write
booleans, position identities and postprocessing. It does not replay the feature.
Preserve the failed controls in `s0027-retained-context-original-controls-1`,
`s0027-retained-context-consumer-5`, and `s0027-edge-read-original-controls-3`.
The earlier long-log setup in `s0027-edge-post-mark-references-2` was rejected
because its outer terrain writes were refused; it is not parity evidence.

The pre-integration candidate passes the complete Java tiers: 2,211 ordinary tests
(6 skipped), 193 parity tests and 11 slow tests. The complete Rust run is
**not green**: 2,902 passed, 57 failed and 9 ignored. Every failure name and body
matches the previously recorded host rendering failures. This does not certify
rendering or gameplay on this host.

Use the existing serialized FULL audit through the family-specific wrapper:

```sh
python3 DevUtils/tests/worldgen/fallen/full/VerifyFallenFull.py \
  --output build/fallen-tree/<full-biomes> --stage biomes --radius 2 --reopen \
  --current-classpath <complete-classpath-file> --current-native <native-directory>
```

The integrated image over published S25 (`006afac17`) passes all 85 original
fixtures, 15 native admission controls, 15 Java-switch controls and 20 family
lifecycle cases. Its full Java tiers report 2,382 ordinary tests with 10 skips,
193 parity and 11 slow passes. The four additional skips are the documented
upstream failed-create and three slot-publication cases; the other six are
existing fixture/performance exclusions. Rust remains non-green: 2,965 passed,
57 unchanged host failures and 9 ignored. Names and bodies match the prior run
apart from printed OS thread IDs. These full tiers certify native `36b91a53…`.

The final S25+S24 image over `c85a8c016` is native
`5b51df2438a275ed29c4b15c3f0d30ad78cb7d14b05ff5ea405b3b8652cbd120`,
with 13,881 selected classes and unchanged 21 owned Java7 class bytes. Its
focused 85/15/15 controls and 20 family lifecycle cases pass, as do 355 affected
Java tests with four documented upstream skips. The final 150-chunk biome
fresh/reopen comparison is exact against original Frozen/pre references. Do not
relabel the earlier complete tiers as freshly running on this later image.

The biome run compares 150 FULL chunks on Frozen, pre-change and Current, then
reopens each saved world. Every serialized field and generation entity matches.
A separate `--resume` run saves/unloads at CARVERS and finishes all 150 chunks at
FULL; that comparison also passes. Resume observes four stock fallen variants;
Current makes zero calls into the old Java fallen adapter. Focused controls
cover all five variants.

The dimension run compares 125 chunks across four dimensions and an additional
End-origin region. Current exactly matches pre-change, and every side matches
its own reopen. Frozen comparisons retain the inherited Primordial differences
in 119 block-state section fields and 11 postprocessing fields across 25 chunks;
this is an explicit failed Frozen comparison, not whole-world parity acceptance.
Other 100 chunks match Frozen.

The FULL observer waits for existing entity storage before reading entities and
sorts only the audit copy of the top-level block-entity list by complete NBT.
It preserves every compound, duplicate and nested list order. Original failed
reopen receipts are retained. `VerifyBlockEntityObserver.py` checks all 150
original Frozen chunk dumps against the corrected observer, including field and
duplicate negative controls. Disable correctness instrumentation for timing.

The cancellation audit passes the existing nonpublication criterion for all
three routes: 121 saved positions share a status in each comparison, with zero
differences below FEATURES away from decorated neighbours. Scheduling-dependent
neighbour and FEATURES-or-later differences remain explicitly reported; this
is not whole-chunk equality. Run `full/VerifyFallenCancel.py --inputs
<successful-full-receipt> --output build/fallen-tree/<cancel>` to reuse sealed
inputs. `full/VerifyFallenFailure.py` runs the original rollback/persisted-status
audit with an explicit injection point; its default one-write threshold must
actually fire on both routes. Preserve failures and check nonvacuity.

The explicit one-write failure run passes both native and Java routes: nine
rollback chunks match their pre-step forms, the failed chunk remains CARVERS in
memory, and no FEATURES result is published. All inspected persisted positions
are absent in this failure run; it does not establish successful-save durability.
`full/VerifyFallenLifecycle.py` additionally passes 20 actual family transaction
cases: all five variants fail at matched first/third logical write entries,
restore exact physical images, drop deferred effects and successfully retry.
Native and original-Java RNG prefixes and retry images match. Disabling rollback
is an intended failing control. Its native hook counts both `set_block` and
`set_section_state`; countdown `2*N-1` matches Java's Nth world-write entry.
The earlier unmatched-counter comparison remains an explicit failed receipt.

## Cost disposition and resource evidence

Normal latency budgets fail. Dot accepted 19 verified latency/Java-allocation
rows as temporary ownership debt in
[decision 273](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18978193),
with the exact manifest corrected in
[274](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18978276).
[Decision 276](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18978579)
accepts the remaining original oak row and the scoped native resource envelope
with its limits. [Decision 283](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18979761)
explicitly carries that disposition to the final integrated `5b51df24` image
using its recorded impact checks and changed resource envelope below. These
decisions do not establish a normal-budget or whole-process-memory pass.

The original complete-call pilot preserves all 20 Frozen work/RNG results.
Nineteen rows have enough quiet groups: direct calls are 2.34–4.97 times Frozen,
bound native calls 9.81–14.47 times, and selectors 10.68–12.79 times. Preserve
all rows at `build/fallen-tree/s0027-complete-cost-pilot-1`. The original noisy
oak/configured row remains unverified there. Its separate oak-only repeat reuses
the exact original timed body: 4.028/5.221/12.291 microseconds for Frozen/pre/
Current, with 24/27/26 quiet groups and exact work/RNG. The other 19 original Frozen/pre rows were
not repeated. Decision 276 records its temporary cost disposition.

The pilot in `fallen/cost/VerifyFallenCost.py` includes actual calls, boundary
work and adoption inside each interval; setup/reset/seeding stay outside.
Java thread allocation, first call, binding and explicit region close are
separate. A bounded JFR profile finds callback-root allocations in fallen
effects, region adoption, context construction and per-call descriptor encoding.
Its 54 owned Java execution samples cannot establish native CPU costs or
application CPU shares. FULL correctness wall times and global entry counters
including startup/dependencies are not performance or per-chunk frequency data.

`fallen/cost/VerifyFallenResources.py` runs a separate Linux/glibc allocation
observer; never use its instrumented times as latency evidence. It returns
unmodified libc pointers and tracks requested malloc/calloc/realloc/aligned
heap bytes with per-call tags, including later frees on other threads. Empty,
known-size/realloc and delayed cross-thread-free controls must pass. The fixed
observer bookkeeping, Java heap, mmap/code cache and OS/native RSS are excluded.
Its reported peak is incremental live requested heap within the tagged scope,
not total process or whole-world native peak.

The first resource run measures all 20 cases on Frozen/pre/Current, with 256
warmups and 64 fixed measured seeds per case. Current matches all Frozen work
and RNG; native mappings/input guards and allocator controls pass. Median
Current requested native bytes range 10,700–22,476 per call; maximum incremental
live requested peak is 11,260 bytes. Cold/new-program and binding scopes are
reported separately, including all raw per-call series.

The actual retained-program control keeps 8,348 tagged Rust bytes live. After
release, all program/chunk weak references clear and every tagged Rust allocation
retires while the real server continues ticking. No production reclaimer drain
is forced. The forced-GC observation includes polling/GC/snapshot overhead and
is not a natural Cleaner latency measurement. Other-caller native requests
still retain 57,976 bytes on Current (31,303 pre; 3,906 Frozen) after this window;
they remain unattributed and are not a proven candidate leak or fully retired
native memory. Decision 276 accepts that original bounded resource evidence. On the final
integrated image, all 20 Rust requested-byte medians remain identical, the held
program still retains 8,348 bytes, and all tagged Rust allocations retire.
Other-caller retained bytes are 70,568, still explicitly unattributed. The final
integration-impact cost/resource disposition is accepted in decision 283.
The final current-only integration replay uses the original compiled timing
body, with 256 warmups and 12 groups of 32 calls. All 20 work/RNG results match
the original Current. Nineteen quiet rows range from 0.782 to 1.141 times its
latency; maximum absolute latency is 103.675 microseconds for oak/native and the
largest Java allocation increase is 389 bytes per call. The low-warmup
configured oak row remains noisy. Its separate 4,096-warmup, 48-by-128 repeat
measures 11.578 microseconds and 7,501.563 Java bytes, with 28 quiet groups.
Only the two previously noisy oak placed/native rows received matched
higher-warmup old/new repeats: 12.301 to 11.819 and 65.422 to 62.681
microseconds. These are integration-impact observations from one process per
side, not replacement Frozen-budget rows or stable speedup claims. Preserve
both original and retry receipts.

On the final image, scoped all-native requested-byte medians range from 10,752
to 22,476 and maximum incremental peak remains 11,260. Cold maxima are
203,765 requested and 72,267 peak bytes; binding maxima are 32,512 and 32,128.
Each maximum may come from a different row. These retain the observer's
requested-heap and ownership limits above.

The native configuration/context ownership redesign is reserved for the next
assignment; keep this stable production candidate unchanged.

An observer mismatch or incomplete output fails. Inspect each fixture and the
source/class/native guards; stock Java fallback must not pass native admission.
For a stable candidate also run the original broader Java/Rust tiers, complete
configured/placed/selector costs, and
[FULL-chunk, resume and failure verification](../RUST-FULL-GENERATION.md).
Preserve known reference differences and explicit host limitations. The earlier
lighting exception does not apply to this family.

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

The published S27 evidence above remains historical. S0029 now implements the
next compiled decorator-chain ownership slice described below; its development
checks do not inherit S27's cost exception.

An observer mismatch or incomplete output fails. Inspect each fixture and the
source/class/native guards; stock Java fallback must not pass native admission.
For a stable candidate also run the original broader Java/Rust tiers, complete
configured/placed/selector costs, and
[FULL-chunk, resume and failure verification](../RUST-FULL-GENERATION.md).
Preserve known reference differences and explicit host limitations. The earlier
lighting exception does not apply to this family.


## Decorator-chain ownership development (S0029)

The S0029 development candidate retains admitted stump/log chains in the compiled
fallen program. Exact immutable JDK/Guava lists containing supported stock
attached-log and trunk-vine decorators use the existing native decision kernels,
placement RNG and region. One stable Y-sorted position context feeds every
successive decorator. Warm admitted chains create no Java Context, descriptor,
child arena or nested decorator downcall.

Mutable/custom chain lists stay behind their actual VM traversal and context;
compilation must not query or iterate them. An immutable list containing a custom
decorator, provider or direction resource also keeps that genuine context path.
Exposed aliases remain authoritative. Sticky dynamic-shape/out-of-region VM
continuation and the original RNG/error prefixes still apply.

The encoder now returns its words and retained effect tables together to the
program consumer. An inherited thread-local table kept the previous configuration
alive after compilation; the original escaped-context GC failure and isolated
root diagnostic are preserved. A separate original adoption failure leaves
8,348 requested Rust bytes live. The corrected consumer releases a raw program
when Java adoption fails, while successfully adopted programs keep their existing
Cleaner ownership. Cache publication failure must preserve the old map and retry.
No test-only root clearing is used for candidate acceptance.

Focused controls live beside the existing observer in `FallenChainRisks.java`.
The original twelve mutation/alias/reentry/error controls match Frozen and
published S27. The corrected thirteen-case candidate also passes escaped-context
GC and the ordinary multi-chain bridge marker. The marker deliberately fails on
published S27. Native adoption/publication and in-flight replacement controls use
`chain/VerifyFallenChainAllocation.py` with explicit `--classpath`, `--native`,
`--reference` and `--output`; `--expect-adoption-leak` labels the original failing
negative, not a successful lifetime check. These allocation probes make no
latency or whole-process-memory claim.

The distinct pre-change reference is published commit `2b18ec0d8`, native
`5b51df24` and its immutable 13,881 selected classes. Frozen remains `7a4d1817`.
The new native candidate is `925a27bf` with the Java2 overlay, sealed as 13,882
selected classes. All 98 Frozen fixture comparisons, 15 native markers and 15
original-Java-switch controls pass. Java tiers pass: 2,469 ordinary with ten
existing skips, 193 parity and 11 slow. The actual Rust suite has 3,022 passes,
57 failures and nine ignored; the 57 failure names/bodies match the historical
headless rendering failures after normalizing only printed OS thread IDs. It
remains non-green.

Actual 150-biome FULL generation and own reopen match Frozen and the guarded
published S27 run. Fresh pre/current CARVERS resume also matches all 150 Frozen
outputs. On 125 dimension chunks, each side's own reopen and pre/current are
exact; the inherited Primordial 119 state/11 postprocessing differences remain
non-green. Both actual world write-failure routes restore nine chunks and keep
the failed chunk CARVERS/absent on disk. Twenty family transaction cases and the
missed-rollback negative pass. Cancellation matches 121 statuses with no
below-FEATURES differences away from decorated neighbours; later and neighbouring
differences remain explicit, not whole-chunk equality.

The two-case oak/native+selector resource control matches Frozen/pre work and
observes 8,588 held Rust bytes (published S27: 8,348). Releasing the real Program
root clears all tagged Rust bytes and program/chunk weak owners. The three
allocation/publication/replacement controls also retire all their tagged Rust
bytes. These bounded scopes do not measure every variant or process memory;
other-caller live requests remain unattributed. Median Rust requests in these
two cases are 4,972 bytes per call, versus 14,060/14,004 pre. Maximum Rust
incremental peak is 4,808; the all-caller oak/native peak of 19,696 includes an
unattributed 15,048-byte other-caller peak. The two-case retirement window retains
13,546 other-caller bytes on Current and 58,808 on pre. These are different
scopes from S27's twenty-case values; do not infer a process-memory reduction.

One matched 28-row campaign uses 1,024 warmups and 32 groups of 64 complete
calls, one JVM per side. Every row preserves Frozen/pre/Current work and RNG,
with 12–32 GC/compiler-quiet groups. Measured Current native/selector calls are
15.802–18.099 microseconds for stock variants and 18.295–18.569 for the ordinary
multi-chains. Their ratios to Frozen are 2.813–4.367 and 2.357–2.920 respectively;
ratios to published S27 are 0.251–0.343 and 0.112–0.117. Configured/placed calls
retain their VM effects and measure 2.175–5.245 times Frozen. These observed
single-process results do not establish stable speedups or whole-generation
performance. All normal Frozen budgets fail; the new case-specific
[cost/resource request](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18981179)
was accepted by [decision 292](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18981235)
for the exact `925a27bf`/Java2 image and these measured scopes. This is a new
temporary migration-overhead exception, not a normal-budget pass; S27's earlier
exceptions remain historical.

The single bounded Current multi-chain JFR diagnostic has only five selected
Java execution samples and 59 allocation samples. It observes outer region
refresh/adoption and FFM allocations; its sample weights are not actual bytes
per operation, and it cannot apportion native CPU or establish a hard cost floor.
Retiring the remaining outer placement/region adoption boundary needs a
subsequent assigned ownership slice. No further bridge microtuning is proposed.
`cost/VerifyFallenCost.py --chains` adds complete
JDK/Guava four-decorator stump/log workloads to the original twenty route cases;
compile/binding/close scopes stay separate from warm complete calls.

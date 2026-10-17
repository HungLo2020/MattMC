# Native Perlin graph construction

## Perlin graph and genuine VM identities

`NativePerlin` projects real `DoubleList`, `RandomSource` and positional-factory
operations in the original order. Rust owns the graph, decisions, metadata,
shared native leaf references and reader lifetimes. Java keeps the original
`ImprovedNoise` facade identities strongly reachable through their public
Perlin parent; replacing that ledger with weak facades changes observable
`getOctaveNoise` identity and collection behavior. The ledger is not the native
graph's lifetime owner or a second permutation store.

Graph readers own actual native references across amplitude callbacks, reentry
and facade cleanup. An evaluation destination is borrowed only for one
synchronous octave write, never retained across a VM callback. Mutable
amplitude observations and comparisons preserve order and raw bits; existing
snapshots stay immutable. Legacy validation still performs the actual list
stream operation, with its nonzero predicate supplied by the native policy.

For this increment, use `*CompositeConstructorContractTest`, `*NativePerlinTest`
and `*NativeNoiseStateTest`, then
`DevUtils/tests/worldgen/noise_composite/VerifyComposite.py` with a complete
sealed pre-change runtime and a new output directory. It compares constructor
callbacks/reentry, public facade custody and the independent composite corpus.
The constructor and reader failure/retirement checks must pass before broad
publication tiers. The octave-set factory also runs native span/index/validation policy through
the original set, iterator and mutable-list operations. Physical graph, reader
and factory counters are tied to their actual drop paths; callback failures
retire the affected invocation. The new Perlin implementation has no final
acceptance yet.

## Focused commands

Use JDK25 and a coherent release Java/native build:

```sh
./gradlew -PmattmcRustProfile=release test \
  --tests '*CompositeConstructorContractTest' --tests '*NativePerlinTest' \
  --tests '*NativeNoiseStateTest'
python3 DevUtils/tests/worldgen/noise_composite/VerifyComposite.py \
  --pre-runtime build/worldgen/noise-composite/pre-c5a-runtime \
  --output build/worldgen/noise-composite/portable-review
python3 DevUtils/tests/worldgen/noise_composite/VerifyCompositeScreen.py \
  --pre-runtime build/worldgen/noise-composite/pre-c5a-runtime \
  --output build/worldgen/noise-composite/screen-review
```

The pre-change runtime must be complete and immutable, as described in the
[chunk-generation guide](../chunk-loading/RUST-CHUNK-GENERATION.md).
Leaf construction and sampling constraints are in [native noise](RUST-NOISE.md).
The [Normal producer/pair correction](RUST-NORMAL.md) continues this ownership
work. The next [Blended/Perlin-Simplex increment](RUST-COMPOSITE-PRODUCERS.md)
continues construction ownership; random-source state remains;
this checkpoint does not complete noise.

## Historical Perlin-only screen — October 16, 2026

On master `c5a9de42…` plus this increment, release `259f42d6…`, the coherent
candidate passed92 focused checks, thirteen portable contracts per runtime,
and the same50,368-observation composite corpus on original Frozen, sealed
publishedc5a/31a2 and Current. Eleven actual Rust checks and four Java owner
checks passed; four Rust state/index/raw-bit policy controls and four Java
graph/builder/reader/factory cleanup controls failed their intended assertions.
Actual class/native origins and full runtime/reference guards passed. Its later
tiers passed2603 ordinary checks plus six expected skips,193 parity,11 slow and
3119 Rust checks plus nine ignored; fresh648FULL/runtime also completed. The
known Normal-tail defect prevented publication; the unstarted remaining world
queue was held. These results keep the Perlin-only259f42d6 identity.

The first shorter screen remains explicitly INCONCLUSIVE. A single bounded
repeat used CPU4,1–4GiB heap, the same observer,24 warm batches and six
JIT/GC-quiet accepted samples per row. Construction plus public bound queries
used8,192 calls per batch; cached public sampling used262,144. All accepted
outputs/RNG words and actual native work matched. Extra warm/retry batches
remain recorded rather than assumed equal across runtimes.

| Public workload | Frozen ns/call | Pre-change ns/call | Current ns/call | Current/pre |
| --- | ---: | ---: | ---: | ---: |
| Modern Perlin construction + bound / Legacy RNG | 7512 | 10943 | 12089 | 1.105× |
| Modern Perlin construction + bound / Xoroshiro | 3496 | 8354 | 9292 | 1.112× |
| Legacy Perlin construction + bound / Legacy RNG | 9385 | 13403 | 13514 | 1.008× |
| Legacy Perlin construction + bound / Xoroshiro | 3866 | 9237 | 9138 | 0.989× |
| Cached Perlin public sampling | 82.0 | 88.2 | 191.7 | 2.172× |
| Cached Normal public sampling | 170.6 | 173.2 | 270.0 | 1.559× |

Current/Frozen median ratios are1.609×,2.658×,1.440×,2.364×,2.336× and1.583×.
Full observed ranges are retained. Every Current accepted sample was slower
than every Frozen sample in its corresponding row; constructor/pre ranges
overlap, so do not claim causal speedups from those medians. These are explicit
intermediate RNG/amplitude boundary debts, not final budget passes. Remaining
exit work is native composite/configuration/RNG producers and direct native
consumers while preserving genuine custom VM facts. S33 owns that continuation.

Cached calls allocated zero Java bytes. Constructor Java bytes/call were about
2958/3798/1590/1590 Current,2658/4194/2218/2218 pre, and2040/3576/1600/1600
Frozen; exact values are retained. This does not attribute native allocation.
All actual native graph, reader, factory and leaf owners retired after every
row. Sampled process peak RSS was143.82/149.70/152.61MB for Frozen/pre/Current,
all with zero swap; whole-process RSS is not causal allocator evidence. No
unbounded retention was observed in this finite workload.

Receipts: `build/worldgen/noise-composite/{second-focused,portable-second,native-controls,java-controls,screen-first,screen-longer}/`.
The first smaller native compile harness needed the real module hierarchy
for private visibility; that harness correction did not change production.
The earlier Perlin-only image `b2de1dac…` passed28 focused checks and its own
three-runtime corpus before adding the factory and stronger retirement checks;
its receipts remain identified as that earlier image.

The subsequent combined Normal/`43f3c309…` candidate requires its own affected
verification; these earlier receipts do not certify that changed runtime.

## Subnormal constructor frequency correction

Before publication, an additional real public-sampler check found that Rust's
`powi` loses subnormal `2^exponent` results below exponent -1023. With actual
source42, first octave -1024 and amplitudes `[1]`, Frozen and sealed pristine
published43f produce input-factor bits1125899906842624 and sample
bits4585530132662145614 at `(1e308,0,0)`. The unpublished `35aa9a6e…`
candidate produced factor0 and sample bits13826052343356134875. The negative
three-runtime logs and exact loaded origins are retained; earlier ordinary
corpus/screens do not cover this case and do not certify it.

Constructor powers now encode the exact IEEE binary result, preserving Java's
subnormal, underflow and overflow boundaries. The actual Java constructor
contract checks every exponent from -1076 through1025 and both integer extremes,
plus the independent preserved public sample. Frozen and pristine43f passed
both contracts; defective35aa failed both. Corrected release `813bb2d7…` passed82 focused Java checks,29 portable
Frozen/Current contracts, the ordinary50,368 corpus and15 actual Rust module
checks with nine safe controls. Its loaded origins/reference guards and short
resource screen pass their stated checks. The later combined4c27 validation
is recorded in [Normal verification](RUST-NORMAL.md#reconciliation-with-published-lighting-requests). The old running native aggregate finished naturally; the unstarted
world queue was held before any world job began.

Use `*PerlinBinaryFrequencyContractTest` alongside the other focused checks;
`VerifyNormal.py` now includes both contracts. Receipts:
`build/worldgen/noise-composite/{exponent-value-investigation,power-contract-baseline}/`.

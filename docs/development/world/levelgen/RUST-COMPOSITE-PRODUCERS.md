# Native Blended and Perlin-Simplex production

## Ownership and VM effects

Blended construction now uses a native producer owning three actual Perlin
graphs. Rust chooses child creation order and octave ranges, computes multiplier
metadata and requests the original bound query. Java retains codec fields and
debug facade identities. A transferred native reader owns all three children;
its native group/local index selects the original VM amplitude list, and its
snapshot index spans all40 octaves. The destination is borrowed only for each
synchronous write. Prepare VM projections before allocating a reader so a VM
allocation failure cannot strand the reader.

Perlin-Simplex construction now uses a native producer owning actual leaf
references, octave membership/skip policy, derived-source seed, frequency
metadata and snapshot packing. Java performs the real sorted-set operations,
original source/derived-source construction, leaf calls and typed projection
array allocation. It keeps the original projection array with its actual leaf
facades; native state never borrows that array or a Java permutation.

The typed VM array is allocated after the initial leaf and before native octave
storage. Its original array-limit failure must keep the completed RNG prefix
and release the bound initial leaf. Positive octaves force the initial child's
existing lazy validation before deriving their seed; the same native validator
is reused. Parent metadata and leaf data retire independently of detached
immutable evaluation snapshots. No borrowed VM address crosses a callback.

The existing [Perlin graph](RUST-PERLIN.md), [Normal producer](RUST-NORMAL.md)
and [noise sampling ABI](RUST-NOISE.md) remain shared. The extended reader keeps
single and paired behavior, raw amplitude bits, callback order and physical
retirement. Default source state and leaf consumption now share the actual
[random-source owners](RUST-RANDOM-SOURCES.md). Derived-source VM factories and
remaining worldgen orchestration/compatibility still need connected migration;
this increment does not complete noise or world generation.

Any future fused source path must preserve both source and private generator
subclass dispatch. Xoroshiro's Java `long * float` calculation rounds before
widening to double; checking only successor RNG words does not verify offsets.

## Focused verification

Use JDK25 and a coherent release library/classes:

```sh
./gradlew -PmattmcRustProfile=release test \
  --tests '*BlendedConstructorContractTest' --tests '*NativeBlendedTest' \
  --tests '*SimplexCompositeConstructorContractTest' \
  --tests '*NativeSimplexOctavesTest' --tests '*NativePerlinTest' \
  --tests '*NativeNormalTest' --tests '*PerlinBinaryFrequencyContractTest'
```

The public contracts exercise actual source failure/reentry and state prefixes,
configuration reuse, positive/negative octave handling and the VM array-limit
failure. Native-owner tests use actual graph/reader retirement, facade GC and
later snapshot use. Preserve complete immutable reference runtimes, original
Frozen and exact loaded origins as described in the
[chunk-generation guide](../chunk-loading/RUST-CHUNK-GENERATION.md).

The publishedc8 pre-change runtime has the Normal-tail correction; do not use
an older verifier's expected pre-tail failure against that fixed image. Earlier
build-only checks are preparation, with their own source hashes. The reconciled870 release passed126 focused Java checks; the native module
harness passed26 checks and seven safe policy/omission controls. The combinedc24
release passed46 portable contracts on each complete runtime,50,368 exact corpus
observations and mandatory tiers:2662 ordinary Java passes plus six skips,
193 parity,11 slow and3149 Rust passes plus nine ignored. After870,17 incoming
slot/item class files changed;13,905 other classes, all1289 Rust sources and the
release library remained byte-exact. Affected Java checks reran; the mandatory
aggregate retains its actualc24 scope. Fresh FULL and CARVERS-resume campaigns on870 each generated648 chunks per
runtime and saved successfully: Current exactly matched publishedc8 in all
four dimensions/two rounds; the three standard dimensions matched Frozen.
The original162 Primordial differences remain. Failure/cancellation, stock
binding fresh/reopen and player pending-shutdown/own-save/reopen also passed
on that image. Injected failure keeps CARVERS in memory and no failed chunk
on disk, with identical rollback. Cancellation releases660 holders to zero
and matches169 statuses; retain its permitted data differences.

The laterb993 menu-only sync changes three Java families and two Rust item
files. Its fresh paired release passed181 focused Java checks,68 Rust item checks,
46 portable noise contracts per runtime and the exact50,368-observation corpus.
The870 world and resource receipts retain their original image identities.
A source/class/interface impact record verifies all642 generation/RNG/loader
sources and830 corresponding classes byte-exact; only the incoming menu/item
families changed. All14 Current item observer groups and16 real hopper
transfer/entity/save-reload checks passed on the new image. Frozen/pre
reference rows retain their original identities with complete input guards.
The new library was not retimed; the representative870 screen is reused only
for unchanged noise/source work. Client/FPS and long-session coverage remain
pending for the whole migration.
Apply the
[migration acceptance policy](../../tooling/MIGRATION-ACCEPTANCE.md) and record
results on the actual coherent image rather than relabeling prototypes.


Run the portable contracts and the bounded real producer screen after building
one coherent release. Both commands preserve complete reference images and
verify actual loaded origins and current inputs:

```sh
python3 DevUtils/tests/worldgen/noise_composite/VerifyProducers.py \
  --pre-runtime build/worldgen/noise-composite/pre-c8ab-runtime \
  --output build/worldgen/<new-contract-directory>
python3 DevUtils/tests/worldgen/noise_composite/VerifyProducerScreen.py \
  --pre-runtime build/worldgen/noise-composite/pre-c8ab-runtime \
  --output build/worldgen/<new-screen-directory>
```

The screen includes construction, first snapshot/sample and the next source
word for both source families. It checks actual native creation/reader work
and bounded retirement after each row. `--smoke` runs a small functional pilot
and must not be reported as a performance measurement.

## Intermediate producer cost

The short870 screen uses CPU4, JDK25, a1–4GiB heap,24 warmup batches and six
JIT/GC-quiet samples per row. Caller sources are prepared before timing;
construction, derived-source creation, first snapshot/sample and the next source
word are timed. Blended uses512 calls per batch; Perlin-Simplex uses4096.
Actual source/class/library origins, work counts, output and complete immutable
reference inventories are checked.

| Complete producer | Frozen µs | Publishedc8 µs | Current µs |
| --- | ---: | ---: | ---: |
| Blended, Legacy source |96.46|169.14|166.72|
| Blended, Xoroshiro source |25.82|132.61|129.98|
| Perlin-Simplex, Legacy source |12.03|18.29|16.61|
| Perlin-Simplex, Xoroshiro source |6.56|15.69|14.02|

The Legacy Perlin-Simplex row is variable: one Current sample reached22.94µs
with matching CPU time and no observed JIT/GC. A separate affected-row check
measured Current15.92–18.03µs and pre15.67–17.98µs; its medians were17.83 and
17.39µs. Preserve both runs. Relative change against pre is **inconclusive**;
claim no improvement from the first median. Every Current accepted sample in
both runs remains slower than every corresponding Frozen sample.

All checked native-owner live counts returned to each row's starting levels or
lower. The first full screen sampled peak process RSS at563.1/607.7/601.1MiB
for Frozen/pre/Current, with no swap observed. This is a bounded process and
ownership observation, not native allocator attribution. Current Java allocation
was about20,696B per Blended call and2240B per Perlin-Simplex call in that run.

The observed1.38–5.03× median Frozen cost remains migration debt, not a final
performance pass. Remaining actual source ownership and fused native RNG consumers
are the connected exit; per-draw VM callbacks remain in this checkpoint. Use
`--kind 2` to investigate only the Legacy Perlin-Simplex row; retain the original
full screen and its source identities. Local receipts are under
`build/worldgen/noise-final/producer-screen` and `producer-screen-row2`.

The connected [random-source owner](RUST-RANDOM-SOURCES.md) consumes canonical
Xoroshiro state directly in the existing native leaf producer. Its guide records
the later integrated RNG checks and resource screen. The measurements above
remain the original published `0f2b4976` checkpoint evidence.

## Construction lifetime observations

`NativeNoiseSeed.statistics()[3]` is a process-wide live-leaf count, not a
constructor identity. Exact before/after equality can reject correct retirement
when unrelated automatic-arena owners finish during a test. Original server
1541→1539 and local deterministic two-unrelated-leaf failures are retained; the
control does not establish the cause of that historical aggregate failure.
The Simplex tests instead observe the actual constructor's leaves and
graph through scoped native weak references, plus its actual vector-allocation
and graph-drop events. The observation owns no leaf, graph or VM facade; normal
constructors have no observation. The original real VM array failure,259-draw
prefix and GC deadlines remain. The combined `5c25f65a` image passes the corrected test with the same two
unrelated leaves retiring concurrently. Safe actual-native builder leaks and
early vector allocation both compile and fail the targeted assertions. Receipts
are under `build/worldgen/random-gaussian/leaf364-{original-control,scoped-control,safe-controls}`;
219 focused checks and56 native checks pass. Both corrected tests also pass
inside the full2,701-pass ordinary tier;193 parity and11 slow checks pass.
The full native tier also passes3,181 tests with nine ignored. Fresh FULL and
CARVERS-resume, failure/cancellation and stock/player save-reopen gates pass on
this same image; their exact scope is recorded in the random-source guide.
No global-counter relaxation replaces the ownership proof, and the controlled
reproduction does not retroactively attribute the historical aggregate failure.

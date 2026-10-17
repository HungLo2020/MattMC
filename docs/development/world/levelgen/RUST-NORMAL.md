# Native Normal noise production

## Current ownership and callback contract

The Normal constructor now uses a native producer that owns both actual Perlin
graphs, computes the original deviation/bound metadata, and emits genuine VM
child and list-iterator operations in order. Java keeps the original parameters
object, public API and debug facade projections. Perlin/leaf details are in
[native Perlin](RUST-PERLIN.md) and [native noise](RUST-NOISE.md).

The range scan obtains the real iterator after both children finish, and calls
`hasNext`, `nextIndex`, then `nextDouble`. Reentry and exceptions keep their
original prefix and identity. Native frames retain the actual child data
through callbacks; they retain no raw JVM pointers. Unused parent metadata can
retire after its facade is collected while a paired reader keeps both children
alive for its remaining work. Evaluation destinations are borrowed only for
individual synchronous writes. Snapshots stay immutable.

## Mutable-tail correction

A real factory callback can grow the shared amplitude list during first-child
construction. The first child keeps its original octave count; the second
captures the later size. Frozen observes a later mutation to that second
child's extra tail. The previously published native wrapper validated only the
first child and silently reused stale sampling state.

The paired native reader checks each child's own captured shape and live
amplitudes, mapping the second child's local indices to its actual snapshot
offset. It also writes both child ranges through the same producer state.
This preserves parameter identity and later mutation while removing the
remaining Java constructor range/factor policy.

The issue was reproduced on original Frozen, sealed publishedc5a and the
Perlin-only candidate, then independently on pristine published43f. The
original result bits changed 4599426361975877692→4599037845078277601 in Frozen;
all pre-fix Current images stayed stale. Those are retained negative receipts,
not three-runtime parity passes.

## Focused verification

Use JDK25 and one coherent release Java/native build:

```sh
./gradlew -PmattmcRustProfile=release test \
  --tests '*NormalConstructorContractTest' --tests '*NativeNormalTest' \
  --tests '*NativePerlinTest' --tests '*PerlinBinaryFrequencyContractTest' \
  --tests '*NativeNoiseStateTest'
```

`NormalConstructorContractTest` keeps the four public iterator/failure/reentry
and asymmetric-tail contracts. The immutable pre-change runtime is expected
to fail only the retained known-tail regression; Frozen and the corrected
candidate must pass it. Native-owner tests exercise actual constructor and
reader retirement, facade collection and later use of both children.

On published `43f3c309…` plus the combined Perlin/Normal increment, the
coherent release image `35aa9a6e…` passed 80 focused Java checks. Frozen and
Current passed all 27 portable contracts; sealed pristine published43f passed
26 and failed only the retained tail regression. All three produced the same
50,368-observation ordinary composite corpus. The corrected tail result matches
Frozen, including the original parameter identity and asymmetric child shapes.
Actual class/native origins and complete runtime/reference guards passed.

Fourteen actual Rust module tests passed. Eight safe ownership, raw-bit, index,
iterator and arithmetic controls failed their assertions; compilation failures
and unsafe lifetime mutants are excluded. Three Java Normal cleanup omissions
and the four Perlin cleanup omissions also failed their intended assertions.
A parallel test-only counter interference failure is retained: sibling tests
now share the existing counter lock. All 14 pass with eight test threads; the
production prefixes and rebuilt Java/native image are byte-identical to the
verified release. This does not relabel the original source inventories.

The `35aa9a6e…` Java tiers passed 2617 ordinary checks plus six expected skips,
193 parity and11 slow checks; its native aggregate also passed. A later
[Perlin subnormal regression](RUST-PERLIN.md#subnormal-constructor-frequency-correction)
prevented publication. The original world queue was held before any world job
started. These receipts retain that superseded image identity; the corrected combined
verification is recorded below.
Temporary resource/boundary costs follow the
[migration policy](../../tooling/MIGRATION-ACCEPTANCE.md).
Whole noise/world generation, Blended/Perlin-Simplex constructors and native
random-source ownership remain unfinished.

Receipts: `build/worldgen/noise-composite/{next-normal-baseline,next-normal-contracts,published43-build,published43-tail,pre-43f-runtime}/`.

Combined receipts: `build/worldgen/noise-normal/{first-focused,portable-first,native-parallel-first,native-parallel-fixed,test-lock-impact,test-lock-rebuild,native-controls,java-controls,perlin-java-controls}/`.

## Corrected combined candidate — October 16, 2026

The corrected release `813bb2d7…` on published43f passed82 focused Java
checks. Frozen and Current passed all29 portable contracts, including every
binary frequency from -1076 through1025, integer extremes and the preserved
public subnormal sample. Sealed pristine43f passed28 with exactly the original
Normal-tail failure. All three ordinary50,368-observation corpora still match.
Actual loaded class/native origins and complete source/reference guards pass.
Fifteen actual Rust module checks pass; nine safe policy/ownership controls
fail as intended. The three Normal and four Perlin Java cleanup omissions
still fail their intended assertions on this coherent image.

Its new intermediate screen uses CPU4,1–4GiB heap,24 warm batches and six
JIT/GC-quiet accepted batches. Construction/bound queries use8,192 calls per
batch; cached sampling uses262,144. Actual output/RNG sums, native work and
final graph/reader/factory/leaf retirement match. Extra warm/retry work and all
sample ranges remain recorded. Reference and runtime guards pass.

| Public workload | Frozen ns/call | Published43f ns/call | Current ns/call | Current/pre |
| --- | ---: | ---: | ---: | ---: |
| Modern Perlin + bound / Legacy RNG | 7406 | 11776 | 11925 | 1.013× |
| Modern Perlin + bound / Xoroshiro | 3498 | 9236 | 9059 | 0.981× |
| Legacy Perlin + bound / Legacy RNG | 9501 | 13425 | 13306 | 0.991× |
| Legacy Perlin + bound / Xoroshiro | 3308 | 9163 | 8170 | 0.892× |
| Cached Perlin public sampling | 81.9 | 88.5 | 193.1 | 2.181× |
| Cached Normal public sampling | 170.9 | 173.4 | 304.4 | 1.756× |

The fourth constructor row is variable (Current7,829–11,918ns, pre8,473–9,318ns);
its apparent median improvement is inconclusive. No causal constructor speedup
is established. Every accepted Current sample is slower than every Frozen
sample in its corresponding row. These observations identify intermediate
cost debt; they do not establish a final performance-budget pass. Cached
Perlin remains consistent with the earlier screens; Normal's required second
child validation retains the live amplitude/VM boundary cost. Do not remove
those genuine observations to improve a measurement.

Cached calls allocate zero Java bytes. Constructor bytes/call are about
2958/3798/1590/1590 Current,2658/4194/2218/2218 pre and
2040/3576/1600/1600 Frozen. Sampled process peak RSS is
147.47/149.64/151.36MB for Frozen/pre/Current, with zero swap. These finite
process samples do not attribute native allocation or prove an unbounded
workload safe; no growing retention was observed here.

S33 owns the connected exit through native composite/configuration and random
state producers and direct native consumers, preserving custom VM facts.
The final Frozen budget remains outstanding. This813b image later passed
2619 ordinary Java checks plus six expected skips,193 parity,11 slow and
3131 Rust checks plus nine ignored. Published4c27 arrived before its world
queue started; the reconciled image has separate world receipts below.

Corrected receipts: `build/worldgen/noise-normal/{power-focused,power-portable,power-native-controls,power-java-controls,power-perlin-java-controls,power-screen}/`.
The earlier `35aa9a6e…` screen at `screen-first` remains identified with that
superseded image: cached Perlin192.6ns, Normal331.0ns, all outputs/native work
and row retirement verified. Its successful ordinary corpus and tiers did not
cover the later subnormal finding and do not certify the corrected image.

## Reconciliation with published lighting requests

Published `4c27ffce…` adds the lighting request/common VM-effect loop. The
combined native image `29b50e29…` passed82 focused checks and all29 portable
noise contracts; the ordinary50,368 corpus and corrected tail remain exact.
All four actual upstream request/failure/lifetime/unload observer suites match
Frozen and the unchanged published43f reference (12 runs). Actual mapped images
and complete class/source/reference guards pass.

All27 relevant noise/RNG/loader classes,35 noise sources and the screen observer
are byte-identical to the screened `813bb2d7…` version. The verified impact
record reuses that intermediate noise screen under the migration policy; its
raw timings/RSS retain the original image and historical pre43f identities.
It is not a new timing or final budget claim for the changed whole library.
The variable constructor row remains inconclusive relative to pre.

The exact combined image passed2619 ordinary Java checks plus six expected
skips,193 parity,11 slow and3133 Rust checks plus nine ignored. New fresh and
CARVERS-resume campaigns each completed648 FULL chunks per runtime, across
four dimensions and two rounds; all six server saves/exits succeeded. Current
is exact against the preserved published43f reference; all three standard
Frozen dimensions are exact, with the original162 Primordial differences
retained separately in each campaign.

Failure injection after50 writes at chunk30 preserved CARVERS status and
restored all nine affected chunks in both native and Java failure paths.
Cancellation released660 retained holders to zero and matched169 stage
statuses; its allowed neighboring/decorated differences remain in the raw
receipt. Stock probes required real native stage/binding use and reopened
saved chunks. Player shutdown with pending work and each runtime's own-save
reopen passed. Complete source/image/reference guards passed throughout.
Stock loaded-holder timings remain diagnostic, with large pre-existing/noisy
ratios; this is not performance acceptance for that separate workload.

Six additional portable checked-Throwable contracts pass on Frozen, pre and
Current: real RNG/factory/iterator/reader IOExceptions retain identity and
later sampler use. These are build-only verification fixtures; production
code and the tested image were unchanged.

The first request-observer compile failed because the supplied main-only
classpaths omitted the existing test-only Objenesis jar; no tests ran. The
retry appends the same installed jar to all three runtime classpaths, with no
production/observer/assertion changes. The failed receipt remains separate.
Receipts: `build/worldgen/noise-normal/{integration-impact,integrated-focused,integrated-portable,integrated-light-requests,integrated-light-requests2,integrated-request-classpaths}/`.

Final combined receipts: `build/worldgen/noise-normal/{integrated-stable-tiers,integrated-stable-worlds,integrated-full-straight,integrated-full-resume,integrated-failure-cancel,integrated-stock-binding,integrated-player-shutdown}/`.
Checked-failure receipts: `build/worldgen/noise-composite/checked-contracts/`.
This is an intermediate whole-worldgen checkpoint; native Blended/Perlin-Simplex
construction, RNG ownership and remaining Java orchestration/compatibility
producers and consumers continue under S33.

The next [Blended/Perlin-Simplex producers](RUST-COMPOSITE-PRODUCERS.md) continue
this work; their verification remains separately identified.

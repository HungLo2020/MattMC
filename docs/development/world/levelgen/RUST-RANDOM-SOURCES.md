# Native random-source ownership

## Current production boundary

The Xoroshiro path puts default source state directly in a native
source owner. Its canonical state has no Java seed mirror. Exposing an actual
generator promotes that state to a shared native owner; the Java generator is
an identity projection of the same data. Codecs use private projections without
calling overridable public accessors. Explicit raw state import keeps `(0, 0)`;
public construction retains the original fallback seeds.

Default reseeding updates native source state without creating a Java generator
or arena anchor. A generator already handed to a caller keeps its old actual
state and independent lifetime. A later generator projection shares the new
source state. Promotion transfers authority once; it is not a copied-state/commit
loop. Custom or reflected VM generator identities bind their actual owner before
original virtual dispatch.

Default leaves use that source's real state inside Rust through the existing
arithmetic and leaf builder. Native source borrows span only native arithmetic,
never a Java callback. The producer retains the actual source until its last
draw; the completed leaf keeps only leaf data. Source and escaped-generator
retirement are independent.

Only an exact `XoroshiroRandomSource` with an exact `Xoroshiro128PlusPlus` backing
may use that fused path. Either subclass must retain its original virtual
callbacks. Real source exceptions, reentry and partial RNG prefixes continue
through the existing callback builder. Preserve Java's `long * float` rounding
before conversion to double when porting random doubles.

The source also owns single-seed construction, default bounded draws,
float/double generation and count-based skipping. Seeding shares the existing
decoration seed mixer. The later Gaussian ownership described below keeps custom
reset dispatch as a genuine VM effect. Source and
generator subclasses retain original dispatch. The integrated `5e933129` image
passed 194 focused Java checks and 36 actual native checks. Three safe fault
controls detect raw-state normalization, escaped-state mutation and a leaked
source owner.

The default source holds its inline state under a native mutex. Projected roots
use separate atomic cells without promising an atomic pair or linearized behavior
for the original non-thread-safe generator. Source calls use ordinary FFM
downcalls because native locks and variable draw loops may block. No native
mutable borrow or VM address spans a Java callback. See the actual
[source owner](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/levelgen/owned_source.rs)
and [leaf builder](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/levelgen/synth/construction.rs).

## Remaining ownership and verification

This is a working slice, not a completed random subsystem. Scalar VM-facing
orchestration, wrapper count/delegation, positional/fork policy and decoration
state handoffs still need connected ownership work. The thin
Java facade and final Rust caller must share the same runtime.

Original Frozen state/codec/sequence and leaf-data baselines are preserved.
The published pre-change image is `build/worldgen/noise-composite/pre-0f2b-runtime`:
use its complete classpath and matching library. Never pair new classes with an
older native library. The integrated source-owner release passed 12 original
Frozen portable contracts and 15 each on published pre/Current. Three contracts
use native bridge APIs absent from Frozen. The 12,324 sequence/state
and 12,480 mixed source-operation observations and complete 12-leaf raw-data
oracle match all three runtimes exactly. The 46 noise contracts and 50,368 corpus
observations also match. These receipts are under
`build/worldgen/rng-ownership/light-integrated-{portable,noise}`. Incoming lighting
coordinator, threaded, live-source and checked-throwable traces and all four
request lifecycle observers also match all three runtimes. Three additional
callback-reseed regressions preserve fork, positional fork and bounded rejection
when a real backing-generator callback replaces the source state between draws.

The integrated `5e933129` plus RNG image (`0ac8787d` library) passed the uncached
mandatory tiers: 2,676 ordinary Java tests plus six skips, 193 parity, 11 slow,
and 3,161 Rust tests plus nine ignored. The later three callback-reseed tests
have their own original Frozen/pre/Current portable run; they are not relabeled
as part of that earlier ordinary aggregate. Fresh FULL and CARVERS-resume each
generated and saved 648 chunks per runtime. Current matches published pre in all
eight regions; the three standard dimensions match Frozen, with the original
162 Primordial differences retained. Failure rollback, cancellation, stock
binding fresh/reopen and player pending-shutdown/own-save/reopen also pass.
Cancellation retains its permitted data differences. Receipts remain under
`build/worldgen/rng-ownership/`; the later Gaussian receipts below retain their own image identities.

With JDK25, run the state, subclass, raw-import and actual lifetime contracts
alongside the existing noise suites:

```sh
./gradlew -PmattmcRustProfile=release test \
  --tests '*XoroshiroStateContractTest' \
  --tests '*XoroshiroLeafDispatchContractTest' \
  --tests '*XoroshiroRawStateContractTest' \
  --tests '*XoroshiroOperationsContractTest' --tests '*NativeRandomBitsTest' \
  --tests 'net.minecraft.world.level.levelgen.synth.*'
```

Before publication, verify actual native mappings, complete source/class/reference
inventories, corpus identity and resource/retirement costs. Follow the
[migration acceptance policy](../../tooling/MIGRATION-ACCEPTANCE.md); source
ownership does not waive earlier constructor or cached-sampling debt. Keep the
[noise producer constraints](RUST-COMPOSITE-PRODUCERS.md) and
[noise ABI](RUST-NOISE.md) in view when connecting further consumers.

The intermediate generator-per-reseed image passed 193 focused checks and all
state/leaf/mixed/noise corpora, but its source screen exposed real reseeding
churn: 260 Java bytes per operation versus 32 in pre, and measured Current
91–97ns versus pre 8–12ns in the longer run. All owners retired. Preserve that
negative performance evidence at `build/worldgen/rng-ownership/source-screen-long`;
other short-source rows remain variable. It motivated actual native source
ownership, not a root-reuse pool that would change escaped-generator aliases.
The integrated source-owner screen at
`build/worldgen/rng-ownership/source-owner-screen` verifies the same outputs,
zero timed Java bytes per reseed, no owners created during the timed operations,
and source/root retirement after every row. Current reseed+draw costs 36–43ns
versus Frozen 7–12ns/pre 7–13ns. Individual float/double groups still cost
1133–1225ns for 32 pairs plus a successor word versus Frozen 143–164ns/pre 142–269ns.
This is recorded migration debt, not a performance pass. The real source mutex
and ordinary FFM calls remain on each scalar call. Connect Gaussian, positional
and later worldgen consumers to native source ownership to reduce those
boundaries; do not reintroduce a Java state mirror or mark locking calls critical.

The same integrated image screened complete Blended/Simplex construction, first
snapshot/sample and RNG successor. Xoroshiro Blended measured 55–60µs versus
pre 121–134µs/Frozen 26–29µs; Xoroshiro Simplex measured 9.7–11.0µs versus
pre 14.1–14.4µs/Frozen 6.4–6.7µs. Legacy Current/pre ranges overlap; neither
is an improvement claim. All actual outputs and graph/source/root retirement
checks pass. Current/Frozen medians remain 1.37–2.21× across the four rows.
Raw samples and assessment are under
`build/worldgen/rng-ownership/source-owner-producer-screen`; these are
intermediate measurements, not final performance acceptance.

## Gaussian ownership

The default Gaussian cache is embedded in the existing native Xoroshiro source,
with no additional per-source arena or cache owner. Standalone and other-source
instances materialize a separate native cache only when needed. Native atomic
cells preserve cached raw bits across pops and resets; they do not promise an
atomic pair or a linearized concurrent stream. Fixed cache operations use
critical downcalls; allocating and variable draw operations use ordinary calls.
Rust owns rejection, draw order and result/cache commits. Java performs genuine
custom `nextDouble` dispatch and `Math.log` effects. No native borrow crosses a
VM callback. The Java cache fields and rejection algorithm are removed.

The actual cache backend remains independent of the public draw target and a
replaced private Gaussian field. Reseeding resets the embedded cache only when
it is still the actual source field; replacement/null/custom fields perform
their original VM reset after seed commit. An independent draw source retires
after its last draw; an embedded cache retains its source through result commit.
Real pending-frame tests observe native weak ownership after facade collection,
continue the actual operation and verify final data retirement.

Use a coherent JDK25 release image and run the Gaussian contracts alongside
source/noise tests. `VerifyGaussians.py` compares original Frozen, a complete
published pre-runtime and Current; four raw bridge contracts are absent from
Frozen and explicitly not run there:

```sh
python3 DevUtils/tests/worldgen/random_source/VerifyGaussians.py \
  --pre-runtime build/worldgen/random-gaussian/pre-f5b8-runtime \
  --output build/worldgen/<new-gaussian-contract-directory>
python3 DevUtils/tests/worldgen/random_source/VerifyGaussianScreen.py \
  --pre-runtime build/worldgen/random-gaussian/pre-f5b8-runtime \
  --current-runtime build/worldgen/random-gaussian/screen-atomic-runtime \
  --output build/worldgen/<new-gaussian-screen-directory>
```

The combined `cf7c0009` working release (`5c25f65a` native library) passed219
focused Java checks,56 actual native module checks and three safe Gaussian fault
controls. Portable Gaussian checks passed15 Frozen/19 pre/19 Current; RNG
sequence/state/codec, raw leaves,46 noise contracts per runtime and50,368 corpus
observations match. Incoming15 item contract groups also pass on all runtimes.
Receipts are under `build/worldgen/random-gaussian/leaf364-*`,
`atomic-safe-controls` and `cf7-item15`. The uncached Java tiers passed2,701 ordinary tests plus six skips,193 parity
and11 slow tests. The full Rust tier passed3,181 tests with nine ignored;
fresh FULL and CARVERS-resume checks each generated and saved648 chunks per
runtime. All eight regions match published pre exactly; standard dimensions
match Frozen, retaining162 original Primordial differences. Injected failure
keeps nine chunks exactly rolled back, the failed chunk at CARVERS in memory
and absent on disk. Cancellation releases660 holders to zero and matches169
statuses, retaining its permitted data differences. Stock binding fresh/reopen
and player pending-shutdown/own-save/reopen pass on all three runtimes.
Receipts remain under `build/worldgen/random-gaussian/stable-{tiers,worlds}`
and their linked workload directories. These gates certify this checkpoint,
not completion of world generation or lighting. The separately owned
[canonical skylight callback defect](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18997323)
and lighting lifecycle correction remain tracked by the lighting worker.

Preserve the original failures: `field-current-negative` contains three actual
private-field replacement failures, and `draw-target-baseline` contains two
public target/cache alias failures. Original resource receipts under `screen`
retain the earlier cache mutex/retention cost (cached65–69ns). The corrected
`screen-atomic` run measured cached28–32ns, pre22–25ns and Frozen5.3–6.4ns,
with zero Java bytes or pending frames for that row. This is an intermediate
screen, not final performance acceptance. Default32-Gaussian-plus-word groups
measured1.61–2.75µs versus pre0.73–1.19µs; standalone and Legacy rows retain
substantial latency and264–268 Java bytes for first native-cache materialization.
Source creation plus three Gaussian draws remains variable and slower. Outputs,
actual owner/frame creation and retirement match; sampled RSS was136/271/263MB
Frozen/pre/Current with no swap. Process RSS is not native allocator attribution.
The owner retains this debt. The connected Legacy ownership below removes
its default lazy cache owner; wrapper/derived-source production and further
native consumer fusion remain the exit for bridge/frame and factory costs.
Repeated scalar getter tuning is not the system exit.

## Legacy source ownership

Legacy's actual 48-bit atomic state and default Gaussian cache live in a native
source with native Gaussian and leaf consumers. The Java AtomicLong state and
LCG arithmetic are removed.
Keep Java's single-attempt CAS failure and genuine threading-exception effect;
a retry loop or mutex would change that observable contract. Construction starts
with raw state zero before the real virtual `setSeed` hook, including the
`WorldgenRandom` superclass call. Custom bit/double/bounded draws and reset
callbacks retain their original order, reentry and committed error prefixes.
Java masks long shifts even for unusual public `next(int)` counts.

Ten public contracts first passed on original Frozen, complete published
`ce394a9a` and Current before implementation. Expanded public contracts pass15
Frozen/18 pre/18 Current; three raw bridge cases are absent from Frozen. The
paired working release (`522e52d1`) passes245 focused Java and63 actual native
checks, plus three safe raw-import/CAS/base-owner leak controls. Real pending
Legacy cache/source consumers survive facade and actual arena-anchor GC, then
retire at their final native use. RNG, raw leaf, Gaussian and full noise
contracts/corpora match their original references. The complete pre-runtime is
`build/worldgen/legacy-source/pre-ce394-runtime`; use `VerifyLegacySources.py`
with `--pre-runtime` and a fresh `--output` below `build/`.

A real creation probe found an unnecessary native superclass owner in delegating
`WorldgenRandom` instances. Their separate raw-zero/cache state now materializes
only on a genuine inherited use; ordinary Legacy construction still initializes
its canonical owner through the real seed hook. The handle publishes one owner
with automatic cleanup, without Java seed/cache mirrors. Wrapper Gaussian cache
independence across delegated reseeds remains an original-baseline contract.

Short resource screens preserve material debt. Legacy32-Gaussian groups have
zero timed Java allocation, but timing overlaps pre-change and is inconclusive.
Source creation plus three Gaussian draws measured494–1042ns versus pre330–343ns
in the corrected run: a latency regression, not a pass. Java allocation is296B
versus312B pre and80B Frozen. The actual Simplex producer's allocation fell from
2652B to2432B after removing the unused owner; pre is2192B. Its16.72–18.92µs
range overlaps pre15.93–18.36µs, so it is also inconclusive. Frozen latency debt
remains. Actual work, creation and retirement checks pass; process RSS/swap does
not attribute native allocator memory. Preserve original negative screens and
`resource-assessment.json` under `build/worldgen/legacy-source/`. The connected
exit is native wrapper count/delegation and default derived-source production,
removing Java factory/arena/FFM costs. This is no final performance acceptance.
The safe `ce394a9a` plus Legacy image passed2,722 ordinary Java tests with six
skips,193 parity,11 slow and3,188 native tests with nine ignored. Fresh FULL
and CARVERS-resume each generated and saved648 chunks per runtime. All eight
regions match published pre; standard dimensions match Frozen, retaining162
original Primordial differences. Failure/cancellation, stock fresh/reopen and
player pending-shutdown/own-save/reopen also pass, with their earlier rollback,
status and permitted cancellation-difference constraints preserved.

Reconciliation with corrected item-comparison commit `35308d18` changes only
five item sources and24 NativeItemState-family classes; all13,907 other Main
classes and all generation/RNG sources remain byte-exact. The paired release
(`cc5f25f6`) passes261 focused Java checks,73 item-native checks including real
held-lock and shape-budget controls, and a fresh full native suite of3,192
passes with nine ignored. Legacy, RNG, Gaussian and noise contracts/corpora
match all references on this combined image. All15 item observer groups,16 real hopper transfer/save checks per runtime,
stock fresh/reopen and player pending-shutdown/own-save/reopen also pass on the
combined image. Earlier broad Java, world and resource receipts retain their actual
safe-image scope; they are reused by the recorded unchanged-source/class and
dependency impact, not relabeled as new combined aggregates. See
`build/worldgen/legacy-source/35308-impact/after.json` and the `combined-*`
receipts. The separately owned skylight callback/lifecycle work remains outside
this checkpoint; no whole-worldgen or final performance completion is claimed.

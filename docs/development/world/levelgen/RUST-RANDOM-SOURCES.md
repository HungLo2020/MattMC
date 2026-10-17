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
decoration seed mixer; Gaussian reset remains the real VM effect. Source and
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

This is a working slice, not a completed random subsystem. Gaussian state,
Legacy sources, positional/fork orchestration and decoration state handoffs still need connected ownership work. The thin
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
`build/worldgen/rng-ownership/`; build-only Gaussian preparation is separate
and does not change the production boundary above.

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

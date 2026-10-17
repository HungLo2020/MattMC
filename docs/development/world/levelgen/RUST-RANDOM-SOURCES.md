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
orchestration, wrapper integration, positional/fork policy and decoration
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

## Wrapper count, seed and leaf production

`WorldgenRandom` count and seed arithmetic now live in Rust. Default leaves
consume the actual backing source through a native kernel owning both count
and source data until its last draw; a completed leaf retains neither owner.
Java supplies real target identities and requested virtual effects. No native
borrow crosses a callback. These calls use ordinary FFM.

Count commits precede custom draw failures. Preserve a count created by a
constructor hook before the final target field is assigned, read a target
replaced by a callback again for the next bit request, and keep the wrapper's
Gaussian cache independent of delegated reseeds. Two bit requests still consume
two backing Xoroshiro words. These constraints also apply to private generator
callbacks and feature/salt seed effects that reenter and throw after seed commit.

Original Frozen, complete published `ac11e032` and Current first matched ten
public dangerous contracts and 18,456 count/seed/fork/backing-state observations
(SHA256 `68461e6556b768e74e8a011b2807882f1975753a2b00327a73aad2350e052427`).
Expanded contracts pass 15 per runtime, with 2,280 additional leaf/count-overflow/
slime observations matching all three references. The corrected working release
(`475a8e16`) passes 276 focused checks and 70 native module checks. Four safe
production fault controls detect lost count commits, signed word shifts,
widened feature-seed overflow and leaked base ownership. Actual pending kernels
continue after facade/arena collection, then retire both count and source.
The two later single-effect prefix cases have portable and ordinary receipts;
they are not relabeled into the earlier 276 focused aggregate.

Run compatibility and the bounded production screen with complete reference
classpaths and matching libraries:

```sh
python3 DevUtils/tests/worldgen/random_source/VerifyWorldgenSources.py \
  --pre-runtime build/worldgen/worldgen-random/pre-ac11-runtime \
  --output build/worldgen/<new-wrapper-contract-directory>
python3 DevUtils/tests/worldgen/random_source/VerifyWorldgenScreen.py \
  --pre-runtime build/worldgen/worldgen-random/pre-ac11-runtime \
  --output build/worldgen/<new-wrapper-screen-directory>
```

The screen measures actual leaf construction, four seed operations and wrapper
creation plus those operations. It checks source successors, counts, native
frame/owner work and retirement. `--smoke` is functional preparation, not a
performance measurement. The original four-frame screen remains at
`build/worldgen/worldgen-random/combined-screen`, with exact source snapshots.
Two unnecessary single-effect feature/salt frames were removed: shared native
arithmetic now supplies the original virtual VM seed call directly. Decoration
and large-feature operations retain their real pending frames.

The corrected `two-frame-screen` verifies two frames per seed group and complete
retirement. Seed groups remain slower at 702–800ns versus 250–374ns pre, with 244
additional Java bytes. Creation plus seeds costs 902–1060ns versus 537–588ns pre,
also 244 additional bytes. Old/new timing ranges overlap: actual frame removal
is proven, an isolated timing improvement is not. Legacy leaf construction
measured 3.89–4.70µs versus 6.08–6.72µs pre; Xoroshiro measured 5.33–6.34µs versus
6.88–8.00µs pre, but its original overlapping screen remains inconclusive.
Frozen latency debt remains. Actual work/retirement and zero swap are verified;
process RSS does not attribute native allocation. See `resource-assessment.json`.
The connected exit is native derived-source factories and whole consumers,
removing temporary VM owner factories and per-bit bridges. Final budgets,
hardware and whole random/worldgen completion remain open.

The `34279686` plus wrapper image passed fresh mandatory tiers: 2,739 ordinary
Java tests with six skips, 193 parity, 11 slow and 3,199 Rust tests with nine
ignored. Fresh FULL and CARVERS-resume each generated and saved 648 chunks per
runtime. All eight regions match published pre exactly; standard dimensions
match Frozen, retaining 162 original Primordial differences. Failure injection
preserves nine rollback chunks, CARVERS in memory and absence on disk;
cancellation releases 660 holders and matches 169 statuses with its permitted
data differences. Stock fresh/reopen and player pending-shutdown/own-save/reopen
also pass. Receipts under `build/worldgen/worldgen-random/stable-*` retain this
actual 342-based scope. Reconciliation with the later lighting retirement
checkpoint requires affected combined-image checks; earlier aggregates are not
relabeled. This checkpoint does not complete world generation.

The later `7619d2db` lighting reconciliation changes five Java sources and 13
Main class files; 13,923 other classes, all 1,295 Rust sources, release `475a8e16`,
build configuration and 117 dependencies remain exact. Fresh combined checks
pass 312 focused cases plus original checked lighting effects, idle 1/128/cycle
retirement and modeled/real release-prefix cases. A short-process external
native-mapping sampler missed one completed modeled test; its failure is kept.
An in-process mapped-file/hash observer verified all three unchanged release
tests without a delay, manual drain or changed assertion.

Fresh Current FULL and CARVERS-resume runs on 7619 each generated and saved 648
chunks, compared against explicitly preserved reference rows with byte-exact
audit classes and matching settings. All eight pre regions match; standard
Frozen dimensions match with the same 162 Primordial differences. Fresh stock
and player shutdown/own-save/reopen checks also pass. See `7619-impact`,
`7619-focused`, `7619-light-release`, `7619-full-*` and `7619-saves` under the
same evidence root. Earlier broad/resource receipts retain their actual 342
scope; unchanged inputs justify reuse, not relabeled aggregate results.

The subsequent crafting-row update (`2bcdbe2c`) changes three item/menu Java
families, with 16 changed Main files and 13,920 others exact; Rust, native
library, source/noise classes and build/dependency inputs remain unchanged.
Its fresh item 15 and real hopper 16/save checks pass on all three runtimes.
Incoming review separately reproduced three negative/wrapped-negative grid
constructor failures against the actual built facade: its structural exception
differed from original Frozen/pre `NegativeArraySizeException`. Keep that
negative receipt; the normal crafting results do not certify invalid sizes.

The standalone correction (`3d04cb0d`) changes only one Main class. The final
paired image passes 315 focused checks, including the new constructor-boundary
contract. The exact original observer bytecode now matches Frozen/pre for all
three reproduced cases and exception messages; all other 13,935 Main classes,
Rust/configuration/dependencies and the release library remain byte-exact.
Receipts are `3d04-focused-final`, `3d04-impact` and
`3d04-crafting-ctor-corrected`. An earlier input snapshot overlapped its sync;
its passing XML is retained with a failed source guard and does not count as
acceptance. Capture inputs only after Git operations settle. Earlier mandatory,
world, resource and affected item receipts retain their actual image scopes,
with the explicit unchanged-input impact records above.


## Worldgen Gaussian consumer in progress

The next working slice connects the actual worldgen kernel to native Gaussian
production. The cache miss must commit before any target inspection or count
owner creation. If the wrapper has no actual count owner yet, native control
requests that genuine VM resource before its first draw. The native pending
producer owns backing data while waiting, then owns count and backing together;
both retire after the last draw, with the actual cache retained through commit.
Custom wrapper/source/generator callbacks keep original VM dispatch.

Before implementation, seven dangerous contracts passed on original Frozen,
complete published `c298904f` and Current. The 36,504 Gaussian/cache/count/backing
observations match at SHA256
`cc7a23aea7c64679339036f9eb1aa67ed79821442f380753d4ae4a73b21f5b72`.
Use `VerifyWorldgenGaussians.py` with complete
`build/worldgen/worldgen-gaussian/pre-c298-runtime`. The paired release passes
325 focused Java checks, including three actual JVM collection proofs. Native
count/backing data survive facade and arena-anchor collection, retire before
`Math.log`, and leave the cache alive through commit. Abandoning a deferred
count-resource request releases its backing and cache without drawing. The
seven contracts and all 36,504 observations match all three runtimes after the
port. The 74 native module checks and three safe controls detect the wrong
count-resource effect, skipped backing draws and leaked producer ownership.

Run the bounded resource screen with:

```sh
python3 DevUtils/tests/worldgen/random_source/VerifyWorldgenGaussianScreen.py \
  --pre-runtime build/worldgen/worldgen-gaussian/pre-c298-runtime \
  --output build/worldgen/worldgen-gaussian/new-resource-screen
```

The first screen records exact Gaussian/count/successor outputs and actual
class/native origins, with all native owners and frames retiring. Prepared
Legacy/Xoroshiro Gaussian32-plus-word groups take 2.60–2.63/2.92–3.64 µs versus
3.60–3.63/4.00–4.14 µs pre-change. Construction plus three Gaussian draws takes
0.64–0.87/0.80–0.86 µs versus 0.93–1.09/0.81–0.90 µs; the Xoroshiro creation
comparison overlaps and is inconclusive. All four remain substantially slower
than Frozen. Java allocation increases from 488 to 976 bytes per prepared group
and by 40 bytes per creation group. Genuine VM target descriptors cross the
ordinary FFM boundary on cache misses; bounded descriptor traffic remains debt
for encompassing native consumer orchestration. Native owner/frame counts do
not increase relative to pre-change. RSS is process memory, not a native
allocator measurement. See `resource-screen/resource-assessment.json` under the
same evidence root. This intermediate screen grants no final budget or hardware
pass.

On the `c298904f` plus Gaussian working image (release `f8e3bf2b`), mandatory
tiers pass 2,752 ordinary Java checks with six skips, 193 parity checks, 11 slow
checks and 3,203 native checks with nine ignored. Fresh FULL and CARVERS-resume
runs generate/save 648 chunks per runtime each: all eight pre-change regions
match, standard Frozen dimensions match, and the known 162 Primordial
differences remain. Both injected failure paths restore all nine chunks;
cancellation retires 660 holders to zero with all 169 statuses matching. Stock
fresh/reopen and pending player shutdown/own-save/reopen checks pass on all
three runtimes. See `stable-tiers`, `full-*`, `failure-cancel`, `stock-binding`,
`player-shutdown` and `saves` under the Gaussian evidence root. These receipts
identify that tested working image; later reconciliation requires affected
integration checks and does not relabel these aggregates.


The reconciled `a438a03c` plus Gaussian image (release `2add5acd`) passes 328
focused Java checks and a fresh complete native tier: 3,203 passed, nine
ignored. The incoming changes affect six item production sources and 33 item
Main classes; all 840 worldgen/noise classes, owned Gaussian/worldgen Rust,
resources and 117 dependencies remain exact. The original public equipment
observer verifies null-equipment construction/read order and live mapping
remap/removal broadcasts on the actual rebuilt pair. Fresh Current Gaussian
contracts and corpus match the preserved original reference observations;
item 15 and hopper 16/save checks pass on all three runtimes. Fresh Current FULL
and CARVERS-resume each save 648 chunks and match the explicit reference rows
with identical audit bytecode and settings. Stock and player shutdown/own-save/
reopen pass; stock fingerprints and future aliases also match the preserved
references. See `a438-{focused,impact,observers,native,full-*,saves}` and
`a438-stock-binding/original-reference-comparison.json`. Earlier full Java and
resource receipts retain their actual `f8e3bf2b` scope; affected item/runtime
checks above verify integration, without relabeling those aggregates.


## Positional factory ownership in progress

Formatting must read canonical private factory data directly. A same-package
subclass can declare `seed()`, `seedLo()` or `seedHi()`; original
`parityConfigString` never invokes those methods. The published `2c3bbc299`
formatting path accidentally called them, changing text and allowing checked
exceptions after a partial builder prefix. The bounded correction calls
`nativeFactory.seed(index)` directly in the two formatting bodies, preserving
the original append order and leaving package getters overridable.
`PositionalFormattingContractTest` covers four value/checked-getter cases;
`PositionalFormattingEffects.java` runs the same observer bytes compiled against
original Frozen. The original four-row failure is retained under
`build/worldgen/positional-formatting/original-baseline`. This correction changes
no RNG arithmetic, factory API or native ownership.
The correction passes 13 focused checks (four new formatting cases, seven
original factory contracts and two physical lifetime checks). The same
Frozen-compiled four-row observer matches original output; restoring the two
virtual-getter bodies in an actual VM overlay reproduces all four published
failures. Factory 10,073 and Gaussian 36,504 observations remain exact. Only the
two nested factory classes differ from the complete published2c runtime;
13,944 other Main classes and every Rust source/native byte remain unchanged.
See `positional-formatting/{focused,corrected-effects,impact.json}` and
`positional-factories/formatting-observers`. Prior broad gates retain their
actual checkpoint identities; this is a bounded dispatch correction.

Before changing production, seven public factory contracts and 10,073 actual
source/cache/successor observations match original Frozen, complete published
`58c319791` and Current at SHA256
`51c4cb5484cf39f8de8400f86e7a6ae7034ed569de3846efa4565102544a7712`.
Run `VerifyPositionalFactories.py` with
`build/worldgen/positional-factories/pre-58-runtime`. This complete fixture
shares only the individually guarded immutable resources from pre-c298; Main
and native are the actual matched published58 pair. Two observer preparation
failures are retained separately; neither is acceptance evidence.

Preserve the original distinction: Legacy `fromSeed` ignores the factory seed,
while Xoroshiro XORs both stored words. Legacy hashes Java UTF-16 code units;
Xoroshiro hashes UTF-8 through MD5, including the JVM's question-mark replacement
of malformed surrogate units. Position arithmetic keeps original integer
overflow and new Xoroshiro sources keep the original zero-pair fallback.
Default interface calls must still reach real subclass overrides; fork calls
must preserve virtual callbacks, target identity, source prefix and failure
ordering.

The native factory/source producer remains an unpublished working slice.
Its facade validation and physical lifetime proofs are now complete below.
Enclosing aquifer and noise-fill consumers still copy factory seeds through VM
getters; retaining the actual factory in those native kernels remains open.


The first actual shared release passes 335 focused Java checks, and all seven
contracts plus the 10,073-observation corpus still match all three runtimes.
The native modules pass 79 checks, including hash vectors, malformed UTF-16,
coordinate overflow and actual child-source independence after factory retirement.
These are initial integration receipts, not completed ownership or resource gates.
Those initial results preceded the physical JVM facade/anchor proofs below;
the source return lease uses the already-initialized factory
bridge for cleanup if source projection or class initialization fails.


The first resource screen is retained at `positional-factories/resource-screen`.
Prepared factory/source groups overlap the pre-change ranges and are inconclusive.
Factory construction plus hashing regresses, especially Xoroshiro: 1.76–1.83 µs
versus pre-change 0.55–1.17 µs for construction, hash, three Gaussians and a
successor word. Java allocation is 628 versus 752 bytes for that row; Legacy
hash construction increases to 628 from 296 bytes. All actual owners/frames
retire and no swap occurs; process RSS is not native allocator attribution.
The regression is not accepted as a final performance result.

Review identified an avoidable source handoff: retaining a second native source
reference then releasing the pending return required two extra downcalls.
The working replacement transfers the same reference to its genuine VM anchor
through one idempotent cleanup action, including projection/class-init failure.
The paired lifetime proofs and Current-only resource continuation below assess
it against unchanged original rows. Further enclosing native
consumer work must remove repeated VM source projections and hash-buffer bridges;
factory/source resource traffic remains explicit migration debt.


The direct-transfer release passes 337 focused checks, including both real
JVM facade/arena collection proofs. Its anchor-release leak control makes
both unchanged proofs reject a retained real SourceArc at the original
five-second deadline; the in-process mapped-library hash is recorded.
The Current-only resource continuation keeps exact original outputs and
owner retirement, with the same observer and preserved reference rows.
Legacy prepared groups improve to 0.36–0.37 µs from 0.90–1.24; several other
spreads overlap and remain inconclusive. Xoroshiro hash construction still
costs 1.59–1.76 µs versus pre-change 0.55–1.17. Java hash allocations remain
628 versus 296/752 bytes (Legacy/Xoroshiro). Two source downcalls were removed;
the remaining hash/projection regression stays explicit, with no final pass.
See `adopt-screen/original-reference-comparison.json` and
`vm-anchor-lease-control`. Earlier screens and controls retain their original
runtime identities.

The published source producer `542dfd165` was reconciled after these guards
closed, preserving all 16 pending files. The actual rebuilt combined pair
passes 353 focused cases before worldgen consumer changes. The five added
sky-transport caller contracts then pass on the original callers; a naive
bulk restore fails the public snapshot-entry callback case. Consumer adoption
and the required compatibility branch are described in the
[decoration guide](feature/RUST-DECORATION.md#sky-source-transport).


The next reconciliation includes published direct-section Source consumption and
original reference-fixture repair at `29c205f35`. All 22 pending files, their
modes and staging, the protected prototype and existing stashes are preserved.
The rebuilt matched native/Main pair (`e460e66b`) passes 358 focused checks,
including the five actual decoration caller contracts and physical factory/source
retirement. This does not relabel earlier native or resource receipts.

The actual two-consumer screen is retained at
`positional-factories/sky-consumer-screen`, with source review and limits in
`resource-assessment.json`. On its earlier `542dfd165`/`190d8831` combined image,
copy/projection takes 316–401 ns versus published58 239–317 ns, at 40 Java bytes
on both. Escaped snapshot/restore takes 242–326 ns versus 103–137 ns, at 536
bytes on both. Outputs agree, actual nine native Source stores and their VM
facades/anchors retire each batch, independent snapshots survive, and swap is
zero. Both slowdowns remain intermediate debt; process RSS is not native
allocator measurement, and original Frozen has no equivalent native-region API.
The direct-section update leaves these transport implementations unchanged;
reuse is limited to that recorded impact, without claiming a new timing run.

Published Source transport currently reads metadata, transfers native words and
copies through VM scratch memory. Escaped arrays additionally import/export their
real live aliases around snapshots and restores. These are inspected operations,
not measured individual causal contributions. Enclosing native consumers should
retain actual owners and avoid repeated VM projection; genuine escaped aliases
must retain compatibility. S33 owns the worldgen consumers, while changes to the
Source producer API remain coordinated with its lighting owner. Neither this
screen nor the factory screen establishes final performance or FPS acceptance.


Mandatory checks closed on the matched `29c205f35` plus working factory/consumer
image (`e460e66b`): 2,769 ordinary Java passes with six skips, 193 parity, 11 slow,
and 3,219 full native passes with nine explicit ignores. Actual test-executable
and mapped-library identities are recorded in `stable-tiers/verified-totals.json`.
The published Source driver passes 248 rows plus its expected real base-release
leak rejection. All seven now-tracked original reference traces match published
Git bytes and provenance hashes; the earlier missing-fixture failure is retained.
Fresh Current factory and Gaussian observers again match their original references.

Fresh Frozen, published58 and Current each save 648 chunks in straight FULL
and CARVERS-resume runs. All eight pre-change regions match; the standard Frozen
dimensions match and the historical 162 Primordial chunk differences remain
explicit. Injected failure rollback and cancellation pass, including 660 holders
retiring to zero and 169 matching statuses under the existing data-difference
rules. Stock and player shutdown, own-save and reopen checks pass on all three.
See `positional-factories/{gate-chain,saves,full-straight,full-resume,failure-cancel}`.
These are correctness receipts, without a timing or final hardware claim.

The subsequent Java-only result-container checkpoint `a9e2ae14` was reconciled
with all 22 pending files and protected state preserved. Its affected integration
checks pass: 359 focused, unchanged original factory/Gaussian corpora, all three
runtimes item 15 and hopper 16, plus fresh Current stock/player save and reopen.
All 1,309 Rust sources, the native library and 843 worldgen classes remain exact;
only 16 classes within its three item file families change. See `a9e2-impact.json`
and `a9e2-affected`. Earlier broad aggregates retain their actual `29c205f35`
image identities.


Published lighting Source-frame checkpoint `172ef018` is now reconciled, preserving
all 22 pending files and protected state. It leaves the agreed six Source methods
and both S33 consumers unchanged but changes private native frame exports. Build
the complete matched Root/native and Main pair before running its producer driver
(now 252 rows plus two expected actual base-release leak rejections). The matched
working release `5cfa73bd` passes 359 focused checks, all 252 producer rows and
both expected negatives. The same 53 effect cases, compiled once against Frozen,
match original Frozen, published58 and Current exactly. The first observer
launcher omitted its real Objenesis test dependency; that compilation failure and
a refused existing-output-directory continuation are retained. The fresh launcher
adds only the guarded dependency jar; observer code and expectations are unchanged.

Fresh Current factory/Gaussian corpora still match the original references.
The full native tier passes 3,224 checks with nine explicit ignores; its actual
executable hash and matched native mapping are recorded. Fresh Current FULL and
CARVERS-resume each save 648 chunks matching the preserved reference rows, with
byte-exact audit observers and the same Primordial qualification. Fresh rollback,
cancellation and stock/player shutdown, own-save and reopen checks pass. See
`172-{consumer-focused,source-producer,source-effects-2,observers,native,saves}`
and `172-gates-3/result.json`. The integration changes 29 lighting classes and
eight lighting Rust files; all 843 worldgen classes remain exact. Earlier broad
Java tiers and resource screens retain their original image identities with
these impact and affected-check records, rather than being relabeled.

That historical checkpoint left factory retention in native aquifer and
noise-fill kernels open. The subsequent consumer integration passes the actual
canonical factory to ore kernels, aquifer bindings, column caches and carver
evaluators. Each pending native consumer retains its `Factory` Arc through its
last use; local coordinate draws avoid allocating derived Source facades.
Custom factory subclasses retain Java dispatch, and aquifer adapters propagate
the original checked callback object and effect prefix.

After building a matched whole Root native/Main runtime, verify the actual ore
kernel's pending ownership, VM facade/segment/arena retirement, output and final
release against a complete immutable pre-change runtime:

```sh
python3 DevUtils/tests/worldgen/random_source/VerifyFactoryKernelOwnership.py \
  --pre-runtime build/worldgen/factory-consumers/pre-2c-runtime \
  --output build/worldgen/<new-factory-kernel-directory> --controls
```

The observer compiles once against the pre-change image and runs those same
bytes on both images. Four scenes cover both random families, including zero
and extreme seeds. Safe ABI controls delegate computation to the exact native
library but admit an independent factory or leak the real kernel; the actual
canonical weak-reference assertions must reject them. These controls do not
free memory early. The [aquifer effects driver](RUST-AQUIFER.md#verification)
separately checks original Frozen callback order, reentry and checked failures.

The October 17 bounded consumer batch was reconciled with lighting checkpoint
`66d9690ea`. Its matching Root native `85e61b60` and Main runtime pass 85 focused
Java checks, all four real kernel lifetime/output scenes and both safe controls,
and all 22 original Frozen aquifer effect rows with both wrapper controls.
The actual combined native suite passes 3,227 checks with nine explicit ignores.
Fresh FULL and CARVERS-resume runs each save 648 chunks: every retained Current
reference region and the standard Frozen dimensions match, with the historical
Primordial qualification retained. Receipts are under
`build/worldgen/factory-consumers/combined-*`; earlier `batch-*` results retain
their actual pre-reconciliation image identities.

Failure rollback, cancellation, stock shutdown/reopen and player pending-save/
reopen also pass on that combined image. The subsequent item-only checkpoint
`8341a0b66` leaves all worldgen and lighting sources and classes unchanged. The
fresh matching native `03a33455` passes 97 affected Java checks, the same kernel
and original-effect controls, all 15 original item contracts and 16 real hopper
save checks on each of Frozen, pre-change and Current. The larger earlier gates
remain scoped to `85e61b60`, supported by `final-impact.json`; they are not
relabeled as runs on the final library. The known custom-height lighting callback
differences reported by S31 remain outside this batch's completion claim.

A short diagnostic NOISE screen on the earlier matching `2926f7ef` image measures
26.9 → 24.6 ms per eight Overworld fills and 22.4 → 22.9 ms for Amplified, with
similar peak process RSS. It uses one pre-change/candidate pair per setting,
12 samples after at least five seconds of warmup. The unchanged NOISE source
and classes support reusing that screen after the lighting reconciliation;
it is not relabeled as a measurement of `85e61b60`, a native allocator result,
or a final Frozen performance pass. See `batch-screen-3` and `combined-impact.json`.

Final performance, rendered-client/FPS and whole native worldgen acceptance remain
open; the measured factory-hash and Source transport slowdowns are not waived.

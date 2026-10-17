# Native biome decoration

`ChunkGenerator.applyBiomeDecoration` hands its whole step loop to
[`NativeDecoration.decorate`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/levelgen/NativeDecoration.java).
[`decoration/sequence.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/levelgen/decoration/sequence.rs)
then runs every generation step in one call per chunk:

1. the step's structures, through a Java upcall (`Sequence.structures`);
2. the union of the present biomes' placed features, in index order, each
   after `setFeatureSeed(decoration, index, step)`;
3. each feature's compiled program in
   [`levelgen/decoration/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/levelgen/decoration),
   or Java's `placeWithBiomeCheck` through an upcall (`Sequence.feature`).

Rust also gathers the present biome set: the distinct values stored in the
bound 3×3's native biome owners, mapped through a per-plan table to plan biome
IDs (values outside `possibleBiomes()` drop out). When a value is not in the
plan, Java gathers the set from section palettes as before
(`javaBiomeSets=` counts those loops). Java owns the structure-step dispatch
(each start's pieces then place natively, see
[native structure pieces](../structure/RUST-STRUCTURE-PIECES.md)) and every
feature without a program. When the loop declines before running, Java's own
loop runs, offering each placed feature to `NativeDecoration.place` one at a
time.

## What runs in Rust

| Piece | Supported now | Rust file |
| --- | --- | --- |
| Loop | steps, structure slots, biome union, feature seeds, Java fallbacks | `sequence.rs` |
| Random | `WorldgenRandom` over Xoroshiro, through `BitRandomSource`'s derivations | `random.rs` |
| Modifiers | `count`, `in_square`, `height_range` (uniform, trapezoid, biased, very biased), `biome`, `rarity_filter`, `block_predicate_filter`, `heightmap`, `surface_water_depth_filter`, `count_on_every_layer`, `environment_scan`, `random_offset`, `noise_threshold_count`, `noise_based_count`, `surface_relative_threshold_filter` | `placement.rs` |
| Int providers | constant, uniform, biased-to-bottom, weighted list, clamped, clamped normal | `program.rs`, `simple.rs` |
| Float providers | constant, uniform, clamped normal, trapezoid | `program.rs`, `simple.rs` |
| State providers | simple, weighted, rule-based, randomized int | `tree/mod.rs`, `vegetation.rs` |
| Predicates | true/all/any/not, matching blocks/tags/fluids, solid, replaceable, inside world bounds, has sturdy face, would-survive (saplings); anything else asks Java | `program.rs`, `placement.rs` |
| Features | ore, replace blobs, glowstone, springs, the three selectors, trees (supported trunk, foliage, root placer and decorator variants, pale moss), random patch and flower patches, simple block, block column, seagrass, sea pickle, kelp, disk, vines, forest rock, underwater magma, multiface growth (glow lichen, sculk vein), geode, monster room, delta, basalt columns, coral claw/tree/mushroom, lake, sculk patch, pointed and large dripstone, dripstone cluster, blue ice, ice spike, iceberg, `freeze_top_layer`, bamboo, huge mushrooms, huge fungi, weeping and twisting vines, basalt pillar, scattered ore, chorus plant, End island, End spikes/gateway/platform, vegetation patches (moss, pale moss, clay pools), desert well | `ore.rs`, `simple.rs`, `tree/`, `vegetation.rs`, `geode.rs`, `rooms.rs`, `nether.rs`, `coral.rs`, `lakes.rs`, `sculk.rs`, `dripstone.rs`, `ice.rs`, `freeze.rs`, `plants.rs`, `end.rs`, `patch.rs`, `rooms.rs` |
| Templates | `StructureTemplate.placeInWorld` for structure pieces ([guide](../structure/RUST-STRUCTURE-TEMPLATES.md)) | `template.rs` |
| Region | 3×3 proto chunks: live block/counter/biome owners, WG and final heightmaps, sky-light sources | `region.rs` |
| `setBlock` | `WorldGenRegion.setBlock` → `ProtoChunk.setBlockState` | `write.rs`, `facts.rs` |

Any other configured feature inside a supported selector, patch or placement
runs as a **Java leaf**: Rust hands Java the random's state through an upcall,
Java places it and returns the state. Top-level programs use Java leaves only
under `count_on_every_layer` or `environment_scan`, where the eager scans pay
for the upcall. **Java steps** run small pieces that need block entities or
registries (monster-room loot and spawner mob, desert-well suspicious sand,
pale moss patches, a water lake's freezing pass, gateway exits and crystal effects) the same way.

End spikes, gateways and the platform already have native geometry. Their
[shared End executor](RUST-END-FEATURES.md) also serves direct feature calls;
Java retains the descriptor cache/iterator, crystal effects and gateway exit
mutation. Spike programs resolve the retained mutable descriptor list on each
invocation through the configured-feature adapter. [Fallen trees and
AttachedToLogs decorators](RUST-FALLEN-TREES.md) now have native invocation
control and compiled fallen region writes; attached/vine region consumers are
verified by focused real-server and FULL save/reopen controls. Their measured
migration overhead requires the scoped exception documented there. Still Java: fossils (their
two template placements run natively), planted huge fungi, and features
without an encoder. Java retains structure-step dispatch in the original
order and the piece objects, but supported starts and legacy pieces, including
mineshafts, now use [native structure planning and placement](../structure/RUST-STRUCTURE-PIECES.md).
Unsupported paths retain Java fallbacks. The earlier mineshaft estimate, roughly 3% of the Overworld feature step,
describes the preceding Java route, not its current Java-only cost. This boundary
was source-reviewed at `e1c35961991261469c934bf07f724b99a418ba55` on 2026-10-15;
no generation or performance test was rerun. [Start dispatch][current-start-dispatch] ·
[Piece dispatch and fallback][current-piece-dispatch]

`freeze_top_layer` reads block light through Java and calls the
[shared native biome temperature service](../../biome/RUST-TEMPERATURE.md) through
the existing Java adapter. Its **per-Biome, per-thread cache is keyed only by
packed position**, although a miss uses the supplied **sea level**, so cache
history is part of the result. The feature calls
`Biome.warmEnoughToRain(pos, level.getSeaLevel())` (`OP_WARM`) on the calling
thread, at the points `shouldFreeze` and `shouldSnow` call it (a synchronous callback that updates the cache, never memoized or reordered): the position
below for ice first in every column, then the surface position for snow only
when the biome has precipitation. [Current call order][current-freeze-calls] ·
[Expected per-column count][current-freeze-count]. The shared native cache sees the same sequence as Java's feature would make.
With
`-Dmattmc.worldgen.verifyDecoration=true` each native freeze placement checks
that the caches (entries and FIFO order) and call counts equal Java's replay
from the same history (`freezeHistoryVerified=`/`freezeHistoryMismatched=`).
The native count is the placement's own (`Call.warmCalls`), not a delta of the
global `freezeWarmCalls=` aggregate, which other worker threads also advance;
`-Dmattmc.worldgen.verifyFreezeSeed=true` additionally seeds conflicting
sea-level entries and fills each cache to its eviction boundary (not a parity
run). The verifier copies and restores the actual native FIFO without invoking
temperature policy. Historical Java contract: [cache scope][temperature-cache] ·
[calculation, lookup and sea-level callers][temperature-lookup] ·
[Native freeze path][native-temperature]

## Contracts to preserve

- **Draw order.** Java's placement streams are lazy: each modifier's
  `getPositions` runs as its `flatMap` reaches it, so draws interleave depth
  first with the feature's own draws. `placement.rs` walks modifiers
  recursively in that order. `count_on_every_layer` is eager and re-samples
  its count at every loop check; Rust memoises each column's ground layers
  because nothing writes while positions are collected.
- **JVM arithmetic.** HotSpot's x86_64 `sin`/`cos` stubs are transcribed in
  [`core/java_trig.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/core/java_trig.rs)
  and checked over every ore and fancy-branch angle. Rust builds `Mth.SIN`
  from that port; Java compares it at startup and any difference disables
  native decoration. Float expressions keep Java's `float`/`double` widening.
- **Hash-set order.** Trees record logs and leaves, and vegetation patches
  their ground blocks, in `java.util.HashSet` order;
  [`java_set.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/levelgen/decoration/java_set.rs)
  emulates it, including `HashMap`'s tree bins: a bucket of nine keys in a
  table of 64 or more becomes a red-black tree whose new keys follow their
  tree parent and whose root moves to the front. That order outlives a later
  split, and a cave moss patch reaches it.
- **Survival rules.** `canSurvive` checks compile in `NativeDecorationEncoder.survival`
  to predicates over state sets: always, `mayPlaceOn` for vegetation whose
  rule reads only the state below, seagrass, tall seagrass, sea pickles,
  growing plants (kelp), corals and double plants. Shape-dependent sets
  (sturdy faces, support shapes) are built without a world; a dynamic-shape
  state found at run time asks Java's exact test instead. Any other block
  asks Java at the position.
- **Gaussian cache.** `WorldgenRandom.setSeed` never resets its
  `MarsagliaPolarGaussian`, so a cached gaussian carries from one placed feature
  to the next. Rust keeps it with the random and hands it across every upcall
  and between programs. `Math.log` in the draw comes from Java (a pure upcall):
  HotSpot's intrinsic need not match libm bit for bit. So do `Math.pow` with
  non-integer exponents and `sin`/`cos` off the transcribed stub's main path;
  HotSpot computes `Math.pow(x, 2.0)` as `x * x` (checked on the bundled JDK).
- **Region semantics.** Rust binds the 3×3 chunks it may write. Reads beyond
  them (sculk cursors wander that far) go to `WorldGenRegion` through pure
  upcalls (block state, height, noise biome), so they see and fail exactly as
  Java's reads do. Far writes are refused by Java's own `ensureCanWrite`. Ore
  writes use `LevelChunkSection.setBlockState(…, false)`.
- **`setBlock`.** `write.rs` follows `WorldGenRegion.setBlock` exactly: far
  writes refused, void air outside the world, section and counter writes,
  light notifications for lit chunks with `ChunkSkyLightSources.update` in
  place, final heightmaps primed and updated. POI, block-entity stubs,
  post-processing marks, light-engine calls and ticks are recorded in call
  order and replayed by Java after the call or before any upcall.
- **Upcalls.** A sync upcall first gives Java everything Rust holds (views,
  heightmaps, sky sources, primed maps, events), runs the Java step, then
  refreshes the binding Rust reads. Operations: `updateShapeAtEdge`, beehive
  checks and bees, Java leaves, Java predicates, a step's structures and a
  Java-route feature. Pure upcalls (no sync): `Math` functions, block light,
  and reads beyond the bound 3×3.
- **Exposure boundaries.** Every public Java return also projects metadata and
  replays effects. Callers can observe and mutate live arrays between public
  calls; retaining a native owner never permits skipping this synchronization.
- **No partial fallback.** A program declines (`JAVA_ROUTE`) only before its
  first draw; inside the loop that feature is placed by Java instead. Program
  creation requires counter policies for every state it can write. A binding
  failure after writes throws the same "Feature placement" crash Java raises.
  If the binding no longer holds after a Java feature, the loop stops and
  Java's loop continues from the next feature.

## Gates and switches

A region binds only when the level is a plain `WorldGenRegion`, all nine
chunks are plain `ProtoChunk`s with the region's height and canonical native
owners, and the native block registry is ready. The native loop also needs
the write path (every chunk at CARVERS or later with the final heightmap set,
and each lit chunk's light engine and sky sources) and a plan for the region
context. Programs and the plan are cached per generator, keyed by the tag
generation, biome binding and `minGenY`/`genDepth`.

Binding is per-chunk work over every section, so two shortcuts keep sparse
chunks (the End's islands) cheap:

- Each `ProtoChunk` keeps its last capture (section, block, counter and biome
  owners and their words) while its section array is unchanged; the next
  region reuses it, and `refresh` re-checks every owner before native work.
  Canonical replacements are recaptured after validating block/counter owners
  and the complete ordered biome-holder table. A different factory binding is
  compatible only when every biome ID denotes the identical holder. Unsupported
  replacements drop the capture and follow the existing binding-failure rules.
- A structure step calls back only when the chunk references one of the
  step's structures: `startsForStructure` finds nothing otherwise, and each
  feature reseeds the random the step would have set.

Unprimed WG heightmaps (chunks restored from disk at CARVERS) are primed by
Rust at the first read, exactly as `ChunkAccess.getHeight` primes them,
unless a custom `BlockState` subclass is registered.

### Current ownership boundary

Published `43f3c309…` and its verified FEATURES-only handoff transfer native
region metadata, section adoption and pending effects to the whole-worldgen
owner S33/r3. The published source/CI identities and receiving ACK are
[recorded in the swarm handoff](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18987892).
Lighting, its coordinator/layers and the narrow `SerializableChunkData`
light-save selection remain S31-owned. Preserve the following region contracts
when continuing native producers and consumers; this transfer does not imply
whole-worldgen or final performance completion.

## Retained region ownership

[`NativeDecorationRegion`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativeDecorationRegion.java)
holds a confined handle to Rust-owned WG/final heightmaps, sky sources, dirty
flags, section-generation leases and pending effects. Placement producers and
successive programs in the existing native sequence use that same state.
Java's binding segments are borrowed projections, and its section-owner arrays
keep the live chunk storage reachable. The FEATURES transaction remains the
rollback and deferred-effect authority.

At each actual VM boundary, project metadata into the existing live arrays,
then claim an ordered effect batch. A nested placement produces into its own
pending batch. Replay acknowledges only effects whose VM operation completed;
failed adoption or replay retains the unfinished suffix. Before importing VM
state for a subsequent call, retry unfinished projections/effects. Canonical
heightmap projection copies directly into the existing raw array; custom
heightmaps retain virtual `setRawData` dispatch.

When a callback replaces canonical sections, reconcile the complete capture
before native work resumes and retire old generation leases. New owners join
the same transaction without overwriting its original snapshot; rollback puts
the original sections and counters back and discards deferred effects.

Close the binding through the existing decoration scope. An active downcall's
borrow prevents premature confined-scope close; failed close leaves the native
owner live. A Cleaner tied to the projection scope handles abandonment, so a
retained projection segment also retains its native allocation. Active native
calls and claimed effect batches hold independent strong native references.
Rust stores no raw JVM object pointers. Release and replay acknowledgement use
primitive handles, so cleanup does not allocate a Java segment after taking
responsibility for a native allocation. The allocation-failure controls retain
the original failed release/ack argument cases as negatives.

#### Recorded S31 measurements and remaining work

**Historical correctness hold, October 16:** the combined S31/S32 production
image on `4199640e` (native `b7502e37`, sealed Main `d3d4f7d6`) matches the
125 fresh dimension chunks, but its own reopen lost saved End light fields.
Separate runs lost nine SkyLight fields in three far End chunks and three
BlockLight fields near the origin. The actual final unload-save snapshots
already lacked the fields before encoding; encoded tapes, region writes and
final disk agreed. These failures remain preserved, separate from the known
Primordial differences and any performance exception.

A single buffered native diagnostic used a distinct image (`ceac5f5f`) with the
same production classes. It observed actual section removals and task snapshot
adoption without dropped events, but saving preceded removal and the loss did
not recur. Direct resumes ran; all observed action5 publications used task mode.
The original driver's duplicate-binding event-count assertion failed after the
probe; read-only validation identified two actual owners across fifty returns,
without rerunning the server. Keep both receipts. No production fix, new
acceptance timing, or correctness waiver follows from this instrumented capture.

A later controlled-order test reproduced the loss on published `4199640e`, the
combined S31 image, and exact parent `41d441c8`. Each image retained the sampled
light data when saved with tickets held; each lost nonzero fields when original
ticket release led to the genuine last unload save. All six runs began with
identical sampled light bytes, and the parent used identical observer bytecode.
Thus S31 and the changes published in `4199640e` are unnecessary for this
controlled failure. A later portable observer compiled against immutable Frozen
`7a4d1817` retains all sampled fields under both schedules, while the earlier Current image lost
them on unload with identical initial data and observer bytecode. The current lighting correction on `c5a9de42` (native `5f56832a`) retains the
actual active-column retirement source, including COW changes. Controlled saves
match all sampled Frozen bytes; the original 125 fresh/own-reopen chunks match
pre-change Current and their own reopened world. The inherited 25 Primordial
Frozen differences remain separate. Both earlier prototypes are rejected:
`a529fcf4` restored stale visible light after COW, and `c3842c23` resurrected a
layer retired while disabled. Preserve their original positive/negative receipts.
Fresh reserved FEATURES owner and all ten callback/alias cases pass on the
combined correction. See the
[lighting continuation](../../lighting/RUST-LIGHT-COORDINATOR.md#lighting-and-save-continuation-under-development)
for exact current correctness, capability-impact and resource evidence. This
checkpoint does not complete either whole lighting or whole world generation.

**Current October 17 intermediate screen:** `c5a9de42`/`5f56832a` matches
actual output, callbacks, native work and the original observer bytecode.
The same 4,096 warmups and 32 groups of 64 complete operations yield 13 normal
and 23 callback compilation/GC-quiet groups. Normal measures 74.654 us and
callbacks 77.294 us, with 5,664/8,352 Java bytes respectively. Relative to the
original guarded Frozen/pre observations, Current/Frozen is 1.183/1.153 and
Current/pre-change is 1.047/1.000. The complete quiet ranges are
67.947–84.148 us and
75.534–115.895 us.
This is retained temporary cost under policy317, not a final allowance pass
or native allocator/CPU attribution. The historical image comparison below
remains relevant evidence of image-sensitive costs, not a causal percentage.
Latest receipts: `build/lighting/s0031-reserved-feature-screen-8`.

**Historical October 17 pre-noise screen:** the combined `929fb693`/`f4010bdd`
image matches both triplets' actual output/callback/native-work identity, with
13 normal and 23 callback compilation/GC-quiet groups. Each group has 64
complete operations after 4,096 warmups. Same observer bytecode and guarded
original Frozen/pre observations are retained. Normal measures 75.514 us
versus 63.128 Frozen/71.301 pre-change; callbacks measure 87.955 us versus
67.018/77.307. Java allocation is 5,664/8,352 bytes per operation respectively.
Current/Frozen is 1.196/1.312; Current/pre-change is 1.059/1.138.
These are explicit temporary costs, not final performance allowance passes.

The material increase was investigated with one process on each of two
preserved images under the same observer/configuration. Rejected `c3842c23`
measures 54.427/60.153 us and corrected pre-integration `f3cc80c1` measures
65.223/64.441 us. Both match work and guards; neither is relabeled as the
current candidate. Current quiet ranges remain 71.098–88.481 us normal and
80.175–104.985 us callback. The image-sensitive cost spans integrations;
these bounded observations do not identify a causal function or justify
attributing all overhead to the Java/Rust boundary. Save/reopen, callback,
owner and release checks pass; no runaway ownership is observed. Keep the
whole cost charged and continue direct native caller/world-service ownership.
The generation reservation remains with S31 until explicit verified handoff;
whole lighting continues independently. Receipts are under
`build/lighting/s0031-reserved-feature-screen-5` and its two labeled diagnostic
siblings. Two earlier wrapper guard failures occurred before any JVM/benchmark
and remain preserved; Frozen-only jar hashes now come from the original
guarded runtime, with every selected dependency checked.

The October 16 S31 measurement on documentation base `41d441c8` used the
cleanup-safe native image `ea4c79c3` and sealed Main manifest `1b0367db`.
Frozen remains `7a4d1817`; pre-change Current is published S29 `2b183557`
(native `925a27bf`). These are historical measurements of that exact image;
subsequent chunk-generation integration requires its own affected checks.
All 28 original complete public-call observations and both production
three-feature sequences matched their Frozen observations. JDK 25, release
Rust, ZGC, CPU affinity 2–7, 1,024 warmups and 32 groups of 64 operations were
used with other local builds and server drivers stopped.

| Complete workload | Current | Current / Frozen | Current / pre-change |
| --- | --- | --- | --- |
| Native public fallen call, including four-decorator chains | 23.95–30.74 μs | 4.032–6.859 | 1.333–1.655 |
| Native selector public call | 23.91–26.07 μs | 3.245–6.344 | 1.376–1.500 |
| Production three-feature sequence | 61.29 μs | 0.976 | 0.828 |
| Same sequence with a mutating VM callback | 65.75 μs | 0.837 | 0.775 |

Each public-call route had at least 14 GC/compiler-quiet groups; the sequence
rows had 7–14 across the three runtimes. These single-process comparisons do
not establish stable causal percentages. Public calls exceed the normal
Frozen allowance. Under the current
[intermediate migration policy](../../../tooling/MIGRATION-ACCEPTANCE.md), this
remains recorded temporary debt with an owned exit; an intermediate checkpoint
does not require a new performance-exception turn. Full matched performance
acceptance still applies at subsystem completion, and historical exceptions do
not automatically cover changed workloads.

Separate requested-heap controls measured 40,712/41,440 native bytes per
three-feature operation, of which 39,776/40,456 were attributed to Rust.
Pre-change totals were 40,544/41,224 bytes. Moving the metadata allocation
category is not a process-memory reduction. Tagged allocations retired at
return; held-owner, projection/opaque-handle GC and final-release controls
also reached zero Rust bytes. This instrumentation excludes Java heap,
`mmap`, code cache, RSS and work on other threads, and supplies no latency
acceptance result.

One paired JFR diagnostic of complete `chain_jdk/native` calls selected only
owned-operation stacks: pre-change had four Java execution samples (three
in refresh), Current eight (five in refresh). Allocation samples moved from
`afterPlacement` toward the public call and replay boundary. These sparse
samples identify retained synchronization and lifetime work; their weights
are neither exact bytes per operation nor native CPU shares, and do not
explain every nanosecond of the public-call regression. Raw recordings stay
local. Preserve the raw cost rows, selected stacks and source/image guards
when reviewing this result.

The remaining ownership debt belongs to the decoration migration owner.
A proposed next slice should connect canonical chunk metadata production,
VM alias custody and native consumers through the transaction, retaining
projection only at genuine VM exposure. Its exit criteria are exact callback,
replacement, rollback and saved-world observations; no stale live arrays or
premature retirement; and matched complete public and multi-feature costs
within the applicable Frozen allowance (or a separately reviewed bounded
exception). The existing native step loop and fallen-chain algorithms need
no second port. This proposal is not authorization to change chunk-loading,
saving or another worker's interfaces.

| Property | Effect |
| --- | --- |
| `mattmc.worldgen.javaDecoration` | every feature on Java's path (the pre-change code) |
| `mattmc.worldgen.javaSequence` | Java's step loop, one native call per placed feature |
| `mattmc.worldgen.decorationJavaKinds=10,11` | keep these outermost program kinds in Java (bisecting) |
| `mattmc.worldgen.verifyDecoration` | shadow-verify every native placement (Java's loop) |
| `mattmc.worldgen.decorationProfile` | per-feature and per-structure time by route (Java's loop) |
| `mattmc.worldgen.decorationDebug` | log encoder declines, rejected programs and loop declines |
| `mattmc.worldgen.decorationTest` | verification: `fail:N` fails the Nth native write, `javafail:N` the Nth Java `setBlock` |

`NativeDecoration.statistics()` (the audit's `native_decoration`) reports
bound regions, native/Java placements, sequenced and declined loops, native
placements by kind, replayed events by kind, native templates and pieces with
Java's remaining structure work (`javaTemplates=`, `javaPieces=`), carver
counters and `javaBiomeSets=`, upcalls by operation (`upcalls={op=count}`)
and one-time setup times by phase (`setupMs=`: facts, piece tables, plan and
program encoding per biome source, template compiles).

## Verify

```sh
CARGO_TARGET_DIR=build/rust/target cargo test --locked --manifest-path src/main/rust/Cargo.toml \
  --profile suite --lib -j 8 -- world::level::levelgen::decoration core::java_trig
python3 DevUtils/tests/worldgen/VerifyFullGeneration.py --output build/full-generation/<label> \
  --rounds 2 --sides current --compare-to build/full-generation/<pre-change>/reference0-0
python3 DevUtils/tests/worldgen/VerifyFullGeneration.py --output build/full-generation/<label> --timing \
  --radius 4 --rounds 2 --sides current --dimensions minecraft:overworld,minecraft:primordial_caves,minecraft:the_nether \
  --jvm-arg=-Dmattmc.worldgen.verifyDecoration=true
```

The [FULL-chunk driver](../RUST-FULL-GENERATION.md) is the acceptance check.
Compare against pre-change Current in every dimension and against Frozen
where Frozen is reproducible. Check `native_decoration` in `regions.json` to
confirm the native route ran.

The retained-region controls use sealed classpaths/native directories and
preserve Frozen and pre-change observations:

```sh
python3 DevUtils/tests/worldgen/region/VerifyRegion.py \
  --output build/decoration-region/<callback-controls> --reference <sealed-pre>
python3 DevUtils/tests/worldgen/region/ownership/VerifyRegionOwnership.py \
  --output build/decoration-region/<ownership-controls> --reference <sealed-pre> \
  --classpath <current-classpath-file> --native <current-native-directory>
python3 DevUtils/tests/worldgen/region/sequence/VerifyRegionSequence.py \
  --output build/decoration-region/<sequence-controls> --reference <sealed-pre> \
  --current-classpath <current-classpath-file> --current-native <current-native-directory>
```

The first command establishes ten callback/alias baselines, including a real
native biome consumer after VM mutation. The original pre-change section
replacement failure is preserved as a negative control. Candidate runs
add `--sides current`, explicit current inputs and `--frozen-observations` from
the identical archived observer. Ownership controls exercise actual adoption,
projection, replay, replacement rollback, close and last-projection GC. Run
`--kill-release` and `--kill-ack` separately to require their intended failures.
Their libc requested-heap instrumentation is excluded from latency measurements.
Use the sequence driver's `--resources` switch for a separate matched allocation
and last-use retirement run; its instrumented timings are not acceptance data.
The sequence observer calls production `applyBiomeDecoration` with three native
features, including a callback between native consumers. Its short defaults
establish work parity; matched cost runs need idle hosts, explicit warmup/batch
settings and sufficient GC/compiler-quiet groups. These focused controls do not
replace FULL/save/reopen, failure/cancellation or the mandatory test tiers.

For a save/reopen discrepancy, preserve the failing world's final disk state
and its earlier live fingerprints separately. The opt-in save discriminator
replays the archived commands into new worlds:

```sh
python3 DevUtils/tests/worldgen/region/diagnostics/VerifyRegionSave.py \
  --output build/decoration-region/<save-diagnostic> \
  --inputs <sealed-current-main-directory> --template <preserved-dimension-run>
```

It traces eight selected End chunks (the positions are explicit in
`RegionSaveAgent.java`): actual save snapshots versus harness snapshots,
encoded tape contents, and region-write entry/return. Snapshot identity is
weakly tracked. Light bytes are reconstructed with public nibble reads,
without exporting a mutable array or retaining another light owner. Those reads
can acquire the layer's normal cached read projection. The boot agent installs
its transformer before resolving the snapshot helper's classes. Java events use
a fixed buffer and flush after capture; its summary reports capacity and dropped
events. Any dropped event makes the capture inconclusive. No lighting barrier
is inserted, but observation allocations and calls can change save scheduling.
`completed` means the probes ran successfully;
`fresh_reopen.identical` remains a separate, strict comparison. Keep failed
or incomplete traces, actual loaded native paths and all input guards; these
instrumented runs provide no acceptance latency or automatic correctness waiver.

Add `--live-light --fresh-only` for the bounded writer trace of original public
reads, weak engine/storage/map identities and existing lifecycle callbacks.
Canonical stock commands can bypass Java per-section coordinator wrappers;
absence of those wrapper events does not establish absence of native removal.
For an explicitly authorized native diagnostic, `--native-diag <artifact-dir>`
requires a separately built image, its exact source-selection receipt, diagnostic
patch, source hashes and build result. It keeps the selected production classes
and original native image pinned, verifies any original source selection after
upstream edits, and never calls that old runtime a build of the newer tip.
Native buffers record owner incarnations, command/removal/retention decisions,
publication membership and sequence identities. The matching opt-in Java hooks
observe both direct-frame and task action5 adoption. Native and Java drop counts
must both be zero. Keep diagnostic changes excluded from production verification;
if observation masks the loss, report the observed save ordering and stop the
bounded experiment rather than treating the quiet run as a fix.

The pinned S31 investigation also has a test-only controlled-order driver:

```sh
python3 DevUtils/tests/worldgen/region/diagnostics/VerifyRegionSaveOrder.py \
  --output build/decoration-region/<controlled-order> \
  --published <sealed-published-419-image> --failing <sealed-combined-main> \
  --template <preserved-dimension-run>
```

Use the preserved review artifacts for these exact images. The controller runs
FEATURES/FULL requests and ticket release in one actual server-executor job,
using the normal managed request loop. Its unload budget permits real queued
continuations and becomes false before eager saving. It requires naturally dirty
targets, no earlier successful target save, actual `scheduleUnload` save callers,
and that this unload's cleanup enqueue follows its save. It changes no dirty or
status flag and never invokes cleanup ahead of its own save. Missing legitimate
preconditions stop the test as unsupported. Native diagnostic hooks are excluded.
Only after both original images reproduce can `--pre-s32` and `--prerequisite`
select the exact parent; source and compiled Java observer equality are checked.
Compare the captured live light, final save snapshots, encoded tapes, writes and
saved world separately. A successful fixture precondition is not a correctness
pass or permission to erase the original failing worlds.

Shadow verification undoes each native placement, replays it on Java's route
with the same seeded random and compares blocks, heightmaps, sky sources,
ticks and post-processing. It needs Java's loop, so it never exercises
`sequence.rs`; FULL parity does. Its worlds keep extra post-processing marks
and block-entity stubs from the undone placement: never use them as parity
output. Profile with `decorationProfile` to choose the next feature by cost.
See [recorded results](RUST-DECORATION-RESULTS.md).

## Adding a feature family

1. Read the Java feature and every helper it calls; list each draw, read and write.
2. Add it to `vegetation.rs` (or its own file) with a decoder that reads
   states it writes through `Reader::write_state` and sets through `Reader::set`,
   and its encoder in `NativeDecorationEncoder` matching the exact feature
   instance and configuration class. Compile `canSurvive` with `survival`.
3. Read through `level_block_state` (void air outside the world) and write
   through `set_block`; predicates through `Placement::test`.
4. Run FULL parity, shadow verification and a profile, then time it.

[current-start-dispatch]: https://github.com/HungLo2020/MattMC/blob/e1c35961991261469c934bf07f724b99a418ba55/src/main/java/net/minecraft/world/level/levelgen/structure/Structure.java#L97-L115
[current-piece-dispatch]: https://github.com/HungLo2020/MattMC/blob/e1c35961991261469c934bf07f724b99a418ba55/src/main/java/net/minecraft/world/level/levelgen/structure/StructureStart.java#L89-L108
[temperature-cache]: https://github.com/HungLo2020/MattMC/blob/e1c35961991261469c934bf07f724b99a418ba55/src/main/java/net/minecraft/world/level/biome/Biome.java#L64-L72
[temperature-lookup]: https://github.com/HungLo2020/MattMC/blob/e1c35961991261469c934bf07f724b99a418ba55/src/main/java/net/minecraft/world/level/biome/Biome.java#L113-L185
[native-temperature]: https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/levelgen/decoration/freeze.rs
[current-freeze-calls]: https://github.com/HungLo2020/MattMC/blob/729f781906b5545735eee043ae2a4d085f637f18/src/main/rust/world/level/levelgen/decoration/freeze.rs#L54-L72
[current-freeze-count]: https://github.com/HungLo2020/MattMC/blob/729f781906b5545735eee043ae2a4d085f637f18/src/main/java/net/minecraft/world/level/levelgen/NativeFreezeVerification.java#L125-L130


## Sky-source transport

The source producer published in
[`542dfd165`](https://github.com/HungLo2020/MattMC/commit/542dfd165888793e68a304d7185afba8f6a73800)
provides bulk word count/copy/snapshot/restore/match operations.
`NativeDecorationRegion` uses those operations at its existing synchronization
boundaries; `DecorationTransaction` uses snapshot/restore for rollback.
Normal sources keep canonical native storage without exposing a Java raw array.
Already escaped arrays still retain their identity and outside-write visibility.
The producer's subclass compatibility paths invoke the actual virtual
`nativeRaw()` once and do not add a `nativeBits()` call.

One verification restore path intentionally keeps its original custom-source
arraycopy expression: a raw callback can replace the public `Maps.sky[index]`
entry, and the original caller rereads that entry's length after the callback.
Passing only the old snapshot to a bulk restore loses that behavior. The
canonical source uses bulk restore; custom sources retain original argument
and callback order. A real naive substitution fails the regression test and
its receipt is retained at
`build/worldgen/positional-factories/sky-consumer-naive-negative`.

Use `NativeDecorationSkyTransportTest` for escaped identity, short restore,
custom callback entry replacement, copy/projection/comparison and original
throwable/prefix checks. The fixture uses real source storage, native region
owners/buffers and chunk transactions; only the world's height is mocked for
isolated region metadata. Before consumer edits all five cases passed on
the actual combined producer. Integration, saved-world and publication gates
for the current consumer changes remain pending.


The combined `29c205f35` plus positional-factory/consumer working image passes
358 focused checks on matched native/Main `e460e66b`. The earlier actual
consumer screen and its performance debt are documented in the
[random-source guide](../RUST-RANDOM-SOURCES.md#positional-factory-ownership-in-progress).
Run its bounded observer with the complete immutable reference:

```bash
python3 DevUtils/tests/worldgen/random_source/VerifySkyConsumerScreen.py \
  --pre-runtime build/worldgen/positional-factories/pre-58-runtime \
  --output build/worldgen/<new-sky-consumer-screen>
```

This measures the real region copy/projection and escaped snapshot/restore paths,
with setup outside the timer and scoped actual Source-store retirement. It is an
intermediate screen, with no original-Frozen equivalent or final hardware claim.

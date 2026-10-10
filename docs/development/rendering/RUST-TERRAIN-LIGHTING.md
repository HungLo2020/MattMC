# Bulk Rust terrain lighting

At [`ee34f2ad`](https://github.com/HungLo2020/MattMC/commit/ee34f2ad99921848d8fc5d63da93eb6c583786c4),
admitted canonical chunk rebuilds prepare their 18³ mesher light words in Rust.
The consumer borrows [live light generations](../world/lighting/RUST-LIVE-LAYERS.md)
and reads native block-registry columns directly. Java supplies state IDs,
retained CPU references and contextual emissive/view/collision predicates and
shade. It no longer projects scalar light values or packs the final words on
this path. Author-recorded lifecycle and settled compatibility checks pass;
complete bulk-path visual parity and performance acceptance remain open.

## Working on the boundary

- [`meshing/preparation/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/render/chunk/meshing/preparation)
  owns intrinsic lookup, halo light reads and exact packed-word preparation.
- [`NativeSectionSnapshot`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/sodium/client/render/chunk/compile/tasks/NativeSectionSnapshot.java)
  checks bulk eligibility before contextual extraction and passes 54 optional
  CPU lease slots (27 sections, block then sky) to Rust, which writes directly
  into the existing mesher output span. Java still admits each distinct state
  to model metadata; model reload guards remain.
- Contextual staging is eight bytes per cell, scoped to one rebuild (46,656 bytes).
  Rust borrows the flat span directly; it creates no second context collection.
  One mutable position follows Frozen's synchronous light-cache convention.
- The immutable native registry owns emission, light blocking and solid-render
  facts. Java predicate and shade callbacks remain migration work. World light
  publication still follows its original map copy-on-write transaction.

## Compatibility and lifetime

The [production gate](https://github.com/HungLo2020/MattMC/blob/ee34f2ad99921848d8fc5d63da93eb6c583786c4/src/main/java/net/sodium/client/render/chunk/compile/tasks/NativeSectionSnapshot.java#L357-L430)
requires a native state-ID halo, appearance diagnostics disabled and the exact
built-in `FabricBlockAccess` class. [Slice admission](https://github.com/HungLo2020/MattMC/blob/ee34f2ad99921848d8fc5d63da93eb6c583786c4/src/main/java/net/sodium/client/world/LevelSlice.java#L283-L309)
requires the expected central section origin and both padded corners inside the
slice. Every present light layer must be exactly `DataLayer` with a native view;
a missing block/sky layer occupies a null slot and reads zero. Native state
admission rejects missing registry data, out-of-range IDs and custom states.
Mutable Java arrays, escaped arrays, custom layers/states/platforms and
appearance diagnostics retain the original scalar path when these gates decline.

This eligibility decision precedes contextual callbacks. Once bulk extraction
starts, model-ID mismatches, callback errors, invalid/expired leases and rejected
preparation throw; they do not restart the scalar path or replay callbacks.
The [Java bridge](https://github.com/HungLo2020/MattMC/blob/ee34f2ad99921848d8fc5d63da93eb6c583786c4/src/main/java/net/sodium/client/render/chunk/compile/tasks/NativeTerrainLighting.java#L24-L61)
checks span sizes and live 16-byte lease metadata. Rust validates the whole
state/context span before writing output, including reserved context bits.
Native pointer validity, alignment and disjoint writable output remain caller
contracts; shape checks do not authenticate arbitrary pointers as CPU leases.

A returned read-only CPU segment retains its Rust generation independently of
its `DataLayer` owner through an automatic arena. The synchronous call fences
all leases; Rust retains no input pointer. Fill, owner reclamation and map
replacement cannot invalidate an already retained generation. A retained view
is not an immutable light snapshot: allocated-generation writes remain visible,
and existing caller exclusion and map copy-on-write still govern publication.
Automatic reclamation does not establish long-session resource bounds. This
is CPU storage only; GAL and presentation ownership are unchanged.

Preserve raw lazy defaults before lightmap packing, including negative and
out-of-range values. Packing can carry bits between light lanes. Keep the second
emissive predicate's original conditional invocation, emission brightness
boosting, opaque read suppression and Java float-to-integer AO behavior.

## Verification

The commands below describe focused checks. Results in this section are the
implementation author's [committed terrain-light record](https://github.com/HungLo2020/MattMC/blob/ee34f2ad99921848d8fc5d63da93eb6c583786c4/PROGRESS.md#L33-L55)
and [release summary](https://github.com/HungLo2020/MattMC/blob/ee34f2ad99921848d8fc5d63da93eb6c583786c4/SUMMARY.md), not independent runtime verification
by this documentation review. This review inspected pinned source, fixture
coverage and those records; it did not run clients, Java/Rust suites, captures
or profiles, or inspect the unbundled runtime receipts. Earlier release
`a25a1281`, integrated release `d9d1a9d6` and the JDK-corrected ordinary run
are separate evidence windows; preserve their individual limits below.

```sh
CARGO_TARGET_DIR=build/rust/target-tests cargo test --manifest-path src/main/rust/Cargo.toml render::chunk::meshing::preparation
./gradlew -PmattmcRustProfile=release test -x testRustNative --tests '*NativeTerrainLightingTest' --tests '*NativeLightLayerTest' --tests '*NativeBlockSectionSnapshotTest' --tests '*NativeSectionSnapshotTintTest' --tests '*NativeSectionColorsTest'
python3 DevUtils/RunWiki.py check
```

Four native checks pass: actual Frozen word outputs, halo faces/edges/corners
and missing dimensions, rejection without partial output, and Java-compatible
float conversion with signed light defaults. The 13,644-byte fixture
losslessly retains all 254,472 Frozen production outputs across 31,809 states and
eight light/AO variants. Its oracle uses air neighbours and does not establish
all contextual-world parity. The focused Java suite passes 27 checks, including the actual contextual producer
and native boundary for every recorded output, retained views through GC/fill,
expired leases, mutable-array compatibility and rejection. These CPU fixtures
do not exercise every production slice/platform gate or all contextual worlds;
requesting GC is not proof of deterministic reclamation. The initial test
compile used a removed Iris setter; the corrected CPU fixture supplies equivalent
AO inputs through test-only reflection and restores them. The original failure
is retained. Full Rust passes 2,440 cases (3 ignored). Full Java passes 1,803
cases (2 skipped). Symbol-enabled release `a25a1281` passes all seven lifecycle cases.
Both reviewed settled vanilla/Iris+DH compatibility pairs and DH coverage pass.
All 16 ABAB runs are clean with exactly 6,000 frames and no concrete Vulkan
validation errors or orphan clients. The overall performance gate **fails**
vanilla and vanilla+DH FPS/p99 floors; both shader modes pass. Median p99
Current/Frozen is 3.884/3.028 ms vanilla and 6.592/5.242 ms with DH. This is
not an isolated gain or full performance acceptance. `SUMMARY.md` retains both
FPS repeats; receipt: `validation/native-terrain-light-final-20261009/summary.json`.
Source/native/Frozen/protected-edit hashes remain unchanged and 25 generated
copies are retired. The preceding light-storage benchmark is a separate build.

The settled-image harness enables appearance diagnostics and therefore uses the
scalar compatibility path. Those pairs check integration and compatibility;
they cannot demonstrate bulk-path pixels. The ordinary-input flight
profiles exercise gameplay without deterministic appearance diagnostics. Their
reviewed native stacks and actual camera positions establish execution of the
migrated consumer. Unregistered flight images do not
establish exact pixel parity or flicker absence.

Both CPU flights pass identity/source/timing/cleanup checks and inspected F3
movement from chunk 9 to 16, with identical start/end positions. Current's stacks contain 17 samples in
`mattmc_terrain_light_prepare`, confirming ordinary bulk-path execution. This
short profile is JIT-heavy (4,642 `PhaseCoalesce` samples versus Frozen's 87);
it is not a throughput improvement. All four profiles now pass terminal identity,
source, timing, cleanup and inspected positions; four game copies are retired.
Weighted eight-second Java allocation is 1.091 GB Current and 2.448 GB Frozen.
There are no sampled Java `computeLightWord` allocations, versus 41.94 MB in
the preceding release. Total Current allocation rose from 0.934 GB in that
earlier sample; categories overlap and neither isolated nor total gain is proven.
Evidence: `goal5/native-terrain-light-flight-profile-20261009/profile-comparison.json`.

Upstream `4246f4e7b` has since been integrated: GUI image retention, packed DH
admission and the ordinary-gameplay harness bring the bridge to ABI 78.
Combined release `d9d1a9d6` passes 2,443 Rust tests (three ignored), Java's
1,807 tests (two skipped, no failures), all seven lifecycle cases and reviewed
vanilla/Iris+DH diagnostic compatibility pairs/DH coverage. All sixteen ABAB
runs contain exactly 6,000 frames with no validation errors or orphan clients.
Source/library/Frozen/user-edit integrity checks pass; 25 generated copies are
retired. Receipt: `validation/native-terrain-light-integrated-20261009/summary.json`.

Average FPS exceeds Frozen in every mode, but the performance gate fails
vanilla, shaders and vanilla+DH p99. Median p99 Current/Frozen is respectively
3.216/3.072, 6.137/5.979 and 6.839/5.749 ms; shader+DH is 6.212/7.446 ms.
Large repeat variance prevents an isolated migration gain claim. `SUMMARY.md`
retains both FPS repeats. These settled diagnostic images still use scalar
lighting and do not prove bulk-path pixels or absence of flicker.

The first visible-map gameplay run failed before startup because `--jdk` selected
observer tools while the game inherited an older JVM. The harness now sets both
child `JAVA_HOME` and `PATH`; all six checks pass under JDK25, including actual
executable resolution with the game VM flags and frame-agent return/DH hooks.
The failed fixture and original test failure remain. The corrected ordinary
comparison below uses 30-second entry, standing and travel windows with a
shared Frozen-readable copied world. See
[ordinary gameplay performance](GAMEPLAY-PERFORMANCE.md).

Regenerate using JDK25 and the untouched Frozen build's full runtime classpath:
compile `DevUtils/tests/rendering/meshing/GenerateFrozenTerrainLightOracle.java`,
then run `GenerateFrozenTerrainLightOracle OUTPUT` with that classpath and
Frozen's native-library directory. The generator refuses another `LightDataAccess`
class. Compress the recorded output with:

```sh
python3 DevUtils/tests/rendering/meshing/CompressFrozenTerrainLightOracle.py OUTPUT build/frozen-terrain-light.bin
cmp build/frozen-terrain-light.bin src/main/rust/render/chunk/meshing/preparation/frozen-terrain-light.bin
```

The compressor checks the original oracle SHA256 before retaining its dictionary
and state-pattern runs. Never generate expectations from the migrated consumer.


The JDK-corrected DH-on, shaders-off, visible-map comparison completes two
observations per side, ordered Current/Frozen/Frozen/Current. Median client-loop
FPS Current/Frozen is 535/555 on playable entry, 619/601 standing and 634/623
holding forward movement. Median p99 is 6.740/5.904, 2.625/2.911 and
3.000/2.904 ms respectively. Current has seventeen frames above 50 ms across
entry/travel repeats versus two on Frozen across all phases. Entry and travel
performance remain open. The all-frame observer and three screenshots per
client add diagnostic overhead; these are not uninstrumented RunDev results.

All source, library, Frozen and original-world guards pass; no client exceptions
or remaining owned clients occur. Twelve HUD snapshots were reviewed. Both
versions show partially built terrain at entry; the first Current snapshot is
less complete, but captures are not frame/time registered. Standing/travel
terrain broadly agrees; minimap initialization/details differ. Movement covers
only about 16.45 blocks before meeting terrain, so this is not sustained chunk
streaming or proof of a pop-in/flicker fix. Four verified copies were retired;
the initial failed JVM fixture remains. The compact receipt is
`goal5/native-terrain-light-ordinary-integrated-jdk-fixed-20261009/comparison.json`.

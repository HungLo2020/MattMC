# Rust DH quad builds

The prior quad-owner checkpoint was reviewed at [`f8feb3ff`](https://github.com/HungLo2020/MattMC/commit/f8feb3ff553242127e1e9533f7af89b4fd5e1a8d).
Suite totals, measured timings and untracked receipt descriptions below are
implementation-author reports. This documentation review inspected source and
fixture assertions; it ran no Java/Rust suites, client, GPU or benchmark checks.
The later packet implementation and its new verification are documented in
[Rust DH semantic packets](RUST-DH-PACKETS.md).

Ordinary `LodQuadBuilder` instances retain their twelve directional quad lists
in `render/dh_quads/`. Rust owns opacity/water admission, adjacent premerging,
stable sort order, both greedy passes, overlap flags and semantic merge
outcomes. This is build-local CPU policy within the existing Rust library.
VulkanicGAL retains all GPU ownership.

Java still supplies faces and contextual color callbacks and interns source
identities. [Rust packet plans](RUST-DH-PACKETS.md) now capture each selected
layer under the native lease lock before callbacks and encode the final bytes.
The four packet methods remain separate captures, not one atomic snapshot of
all builder lists. Ordinary encoding no longer reconstructs Java `BufferQuad`
projections. The native path requires `getClass() == LodQuadBuilder.class`;
subclassed builders retain the Java compatibility implementation.

Implementation: [Rust owner](https://github.com/HungLo2020/MattMC/tree/f8feb3ff553242127e1e9533f7af89b4fd5e1a8d/src/main/rust/render/dh_quads),
[CPU lease](https://github.com/HungLo2020/MattMC/blob/f8feb3ff553242127e1e9533f7af89b4fd5e1a8d/src/main/java/com/seibel/distanthorizons/core/dataObjects/render/bufferBuilding/NativeDhQuadBuilder.java)
and [builder](https://github.com/HungLo2020/MattMC/blob/f8feb3ff553242127e1e9533f7af89b4fd5e1a8d/src/main/java/com/seibel/distanthorizons/core/dataObjects/render/bufferBuilding/LodQuadBuilder.java).

## Constraints

- Preserve signed-short coordinates and Java's sign extension in sort keys.
  Equal keys retain input order. Each build reuses at most65,536 primitive
  keys; larger lists use stable comparison sorting. Preserve compound short width addition,
  including overflow after valid positive-width admission.
- Curvature uses width16; ordinary builds use2048. Negative coordinates use
  floor division. Adjacent-face insertion tries both merge directions;
  horizontal insertion does not premerge. Only horizontal faces get a second
  greedy pass. Overlap flags affect both participating rows.
- Water stays transparent at alpha255 when transparency is enabled. Emission
  counts describe input faces before merging. The explicit exact-material
  diagnostic prevents merging different identities/variants; ordinary visual
  topology preserves Frozen's greedy behavior.
- Configuration is sampled for each operation that attempts merging. Keep contextual
  dirt/shade callbacks and water diagnostics in their existing vertex order.
  Store no Java objects or GPU runtime state in native lists.
- Close builders after packet construction. `LodRenderSection` releases its
  builder after the synchronous semantic build; the collector owns independent
  packet bytes. Earlier failed or skipped asynchronous stages can instead
  leave cleanup to the Cleaner; no collection deadline is established. Native operations and
  close exclude each other. No global quad pool or world cache is added.

## Verify

Use JDK25 and the same native release for all Java runs:

```sh
CARGO_TARGET_DIR=build/rust/target-tests cargo test --manifest-path src/main/rust/Cargo.toml --locked --profile suite --lib render::dh_quads::
CARGO_PROFILE_RELEASE_DEBUG=line-tables-only CARGO_PROFILE_RELEASE_STRIP=none ./gradlew -PmattmcRustProfile=release buildRustNative compileTestJava
CARGO_PROFILE_RELEASE_DEBUG=line-tables-only CARGO_PROFILE_RELEASE_STRIP=none ./gradlew -PmattmcRustProfile=release test -x testRustNative --tests '*NativeDhQuadBuilderTest' --tests '*NativeDhPacketPlanTest' --tests '*LodQuadBuilderLayerTest'
python3 DevUtils/RunWiki.py check
```

[VerifyDhQuads.java](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/rendering/VerifyDhQuads.java)
records actual untouched Frozen add/premerge/greedy output and vertex bytes.
The fixture embeds compiled-class hashes and512 synthetic CPU cases covering
signed coordinates, ties, curvature, overlaps, transparency, large widths
and repeated merges. Its size generator never produces an empty input; the
separate rejected-admission/close test checks a still-empty builder. Expected algorithms are not copied.
Supplemental controls compare current semantic companions with the actual Java
compatibility builder, check lease closure and invalid input prefixes.

The driver accepts `bench current|frozen SEED QUADS ROUNDS`, with five warmup
rounds per JVM. Time creation, face admission, merging, count consumption and
release together in isolated alternating pairs. Add `packets` after `ROUNDS`
to include final packet production and byte consumption. Seeds0/1/2 select smooth,
overlapping and scattered faces. `grass-packets` also substitutes grass for
non-water faces and runs ordered dirt/shade callbacks with fading enabled.
Receipts belong in
`build/native-dh-quad-owner/`. CPU fixtures and timings do not establish gameplay
FPS, visual parity or long-session memory acceptance.

The [Python runner](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/rendering/VerifyDhQuads.py)
accepts `--mode topology|packets|grass-packets`, explicit JDK, compiled-driver, classpath-file,
native-directory and output paths; it pins driver/native bytes, checks result
checksums, and rejects a Current median above Frozen. Do not overlap timed
runs with compilation, tests, profiling or input hashing.

## Recorded CPU checkpoint

The author reports image `9ac7111b`, six alternating pairs per scenario, five
2,000-build warmups and 2,000 measured builds/JVM, with 2,048 faces per build:

| Workload | Current ms | Frozen ms | Current/Frozen |
|---|---:|---:|---:|
| Smooth topology | 118.391 | 120.152 | 0.985 |
| Overlapping topology | 132.954 | 179.989 | 0.739 |
| Scattered topology | 133.108 | 465.470 | 0.286 |
| Smooth with packets | 341.128 | 426.570 | 0.800 |
| Overlapping with packets | 442.196 | 629.144 | 0.703 |
| Scattered with packets | 442.743 | 913.023 | 0.485 |

All six author-reported scoped median gates pass, with matching checksums
and driver/native pins. Broader source/Frozen/compiled-class guards are
reported to match before and after measurement; those untracked receipts
were not independently inspected here. Documentation-only upstream `517aae517` leaves these inputs
unchanged. These are synthetic CPU results, not gameplay acceptance.

Initial smooth topology failed at139.764/119.126ms (1.173×). Forced inlining
and policy-read changes alone did not improve that gate. Inlining was removed;
profiles led to cached primitive keys and in-place permutation. Failed timings,
profiles and source guards are retained; initial receipts are losslessly
compressed. Exact-match Frozen replay and seven native controls cover the new
sort, including65,536/65,537-row threshold behavior. Receipts:
`build/native-dh-quad-owner/`.

The author reports final full suites on upstream `517aae517`: 2,604 Rust
passes/three ignored, and 1,949 Java cases reported/two skipped (1,947
executed), zero failures/errors across 352 Java suites. Six Java workers load the exact native image. All
39,544 Current/38,176 Frozen source pins, native/compiled-class/fixture/driver
guards and protected prompt match; Frozen is unchanged. Wiki validation passes.


## Evidence limits

The following review describes the prior `f8feb3ff` checkpoint. Additional
packet replay and reentry controls are in the [packet guide](RUST-DH-PACKETS.md).

The [Java controls](https://github.com/HungLo2020/MattMC/blob/f8feb3ff553242127e1e9533f7af89b4fd5e1a8d/src/test/java/com/seibel/distanthorizons/core/dataObjects/render/bufferBuilding/NativeDhQuadBuilderTest.java)
contain three test methods: the recorded 512-case Frozen corpus, 64 semantic
companion seeds against an actual subclass compatibility builder, and invalid
input prefix/idempotent close checks. The seven [Rust controls](https://github.com/HungLo2020/MattMC/blob/f8feb3ff553242127e1e9533f7af89b4fd5e1a8d/src/main/rust/render/dh_quads/tests.rs)
cover layout, policy, signed/curvature boundaries and stable sorting, including
the 65,536/65,537 transition. Source assertions do not establish Cleaner release
timing, all callback/reentrant mutation cases, threaded build teardown or
long-session allocation bounds.

The benchmark constructs synthetic faces before timing and uses a proxy render
wrapper (constant shade and no live world). It measures builder creation,
admission, merging, count consumption and release; packet mode additionally
encodes and consumes bytes. It does not measure chunk/world traversal, real
contextual face production, GPU submission or visual output. The smooth seed
uses width 2,048 and alpha 128, whereas overlapping/scattered seeds use alpha
255 with water still transparent; curvature and overlap diagnostics are off.
These are distinct synthetic input scenarios. Topology checksums consume only opaque/transparent counts; packet checksums
add packed vertex-byte sums, not semantic companion arrays or exact per-byte
equality. Recorded corpus and semantic companion equality controls are
separate evidence. The corpus uses stone/water and a constant-shade proxy; it
does not exercise dirt/shade callback reentry or live contextual face inputs.
The replay checks that four embedded class-hash strings have length 64; it
does not rehash and compare those Frozen compiled classes during replay.

Unlike the older source benchmark, this Python runner exits unsuccessfully if
the Current median exceeds Frozen. It rechecks its selected native/driver files
before each JVM and afterward, but does not itself hash every classpath entry,
source file or Frozen inventory. Keep the broader author receipt guards
separate from the checks enforced by this command.

The [#777 quad ownership review](https://github.com/HungLo2020/MattMC/issues/777#issuecomment-6106127269) and [#709 CPU evidence review](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-6106127723) retain these migration and acceptance boundaries. Both issues remain open.

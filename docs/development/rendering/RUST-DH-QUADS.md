# Rust DH quad builds

Ordinary `LodQuadBuilder` instances retain their twelve directional quad lists
in `render/dh_quads/`. Rust owns opacity/water admission, adjacent premerging,
stable sort order, both greedy passes, overlap flags and semantic merge
outcomes. This is build-local CPU policy within the existing Rust library.
VulkanicGAL retains all GPU ownership.

Java still supplies faces and contextual color callbacks, interns source
identities, and encodes final semantic packets. Packet construction copies
immutable native rows before invoking Java callbacks. Temporary `BufferQuad`
projections exist during encoding; no persistent Java quad mirror is kept.
Subclassed builders retain the existing Java compatibility implementation.

Implementation: [Rust owner](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/render/dh_quads),
[CPU lease](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/com/seibel/distanthorizons/core/dataObjects/render/bufferBuilding/NativeDhQuadBuilder.java)
and [builder](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/com/seibel/distanthorizons/core/dataObjects/render/bufferBuilding/LodQuadBuilder.java).

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
  packet bytes. The cleaner releases abandoned builders. Native operations and
  close exclude each other. No global quad pool or world cache is added.

## Verify

Use JDK25 and the same native release for all Java runs:

```sh
CARGO_TARGET_DIR=build/rust/target-tests cargo test --manifest-path src/main/rust/Cargo.toml --locked --profile suite --lib render::dh_quads::
CARGO_PROFILE_RELEASE_DEBUG=line-tables-only CARGO_PROFILE_RELEASE_STRIP=none ./gradlew -PmattmcRustProfile=release buildRustNative compileTestJava
CARGO_PROFILE_RELEASE_DEBUG=line-tables-only CARGO_PROFILE_RELEASE_STRIP=none ./gradlew -PmattmcRustProfile=release test -x testRustNative --tests '*NativeDhQuadBuilderTest' --tests '*LodQuadBuilderLayerTest'
python3 DevUtils/RunWiki.py check
```

[VerifyDhQuads.java](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/rendering/VerifyDhQuads.java)
records actual untouched Frozen add/premerge/greedy output and vertex bytes.
The fixture embeds compiled-class hashes and512 synthetic CPU cases covering
signed coordinates, ties, curvature, overlaps, transparency, empty builds,
large widths and repeated merges. Expected algorithms are not copied.
Supplemental controls compare current semantic companions with the actual Java
compatibility builder, check lease closure and invalid input prefixes.

The driver accepts `bench current|frozen SEED QUADS ROUNDS`, with five warmup
rounds per JVM. Time creation, face admission, merging, count consumption and
release together in isolated alternating pairs. Add `packets` after `ROUNDS`
to include final packet production and byte consumption. Seeds0/1/2 select smooth,
overlapping and scattered faces. Receipts belong in
`build/native-dh-quad-owner/`. CPU fixtures and timings do not establish gameplay
FPS, visual parity or long-session memory acceptance.

The [Python runner](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/rendering/VerifyDhQuads.py)
accepts `--mode topology|packets`, explicit JDK, compiled-driver, classpath-file,
native-directory and output paths; it pins driver/native bytes, checks result
checksums, and rejects a Current median above Frozen. Do not overlap timed
runs with compilation, tests, profiling or input hashing.

## Recorded CPU checkpoint

Image `9ac7111b`, six alternating pairs per scenario, five2,000-build warmups
and2,000 measured builds/JVM,2,048 faces per build:

| Workload | Current ms | Frozen ms | Current/Frozen |
|---|---:|---:|---:|
| Smooth topology | 118.391 | 120.152 | 0.985 |
| Overlapping topology | 132.954 | 179.989 | 0.739 |
| Scattered topology | 133.108 | 465.470 | 0.286 |
| Smooth with packets | 341.128 | 426.570 | 0.800 |
| Overlapping with packets | 442.196 | 629.144 | 0.703 |
| Scattered with packets | 442.743 | 913.023 | 0.485 |

All six scoped median gates pass, with matching checksums and driver/native
pins. The full source/Frozen/compiled-class guards match before and after
measurement. Documentation-only upstream `517aae517` leaves these inputs
unchanged. These are synthetic CPU results, not gameplay acceptance.

Initial smooth topology failed at139.764/119.126ms (1.173×). Forced inlining
and policy-read changes alone did not improve that gate. Inlining was removed;
profiles led to cached primitive keys and in-place permutation. Failed timings,
profiles and source guards are retained; initial receipts are losslessly
compressed. Exact-match Frozen replay and seven native controls cover the new
sort, including65,536/65,537-row threshold behavior. Receipts:
`build/native-dh-quad-owner/`.

Final full suites on upstream `517aae517` report2,604 Rust passes/three ignored,
and1,949 Java cases reported/two skipped (1,947 executed), zero failures/errors
across352 Java suites. Six Java workers load the exact native image. All
39,544 Current/38,176 Frozen source pins, native/compiled-class/fixture/driver
guards and protected prompt match; Frozen is unchanged. Wiki validation passes.

# Retained Rust region tapes

Current disk-backed `IOWorker.loadForParse` reads a tape into a Rust CPU owner.
The section decoder consumes that owner directly. The normal admitted path has
no full-tape Java array or native input recopy, no output-capacity retry, and no
validation fingerprint unless region validation requests one.

Rust still parses the region NBT document before producing the tape. Java owns
file-cache scheduling, registry bindings, the remaining root-tag model and chunk
wrappers. Pending encoded writes still use their existing Java tape; pending tags
keep their copy semantics. This is intermediate migration, not a JVM-free loader.

## Lifetime and compatibility

- `NativeChunkTape` owns one CPU lease. Its automatic FFM scope releases the
  wrapper; explicit close drops the bulk tape immediately and is idempotent.
- Copy and native section decode serialize against close inside Rust. Closing a
  region file does not invalidate a returned tape, and the tape can cross from
  the IO executor to the parser executor.
- `SerializableChunkData.parseLoaded` consumes and closes its `Loaded` result on
  success, decline, older-version upgrade and exceptions. Returned block, biome
  and light owners have independent lifetimes.
- `IOWorker` closes a produced load result when its future has already been
  canceled or otherwise completed. A direct caller of `loadForParse` must parse
  or close its returned `Loaded`; the automatic scope is a final discard guard.
- Custom factories, malformed/noncanonical sections and older data versions use
  an explicit full-tape copy and their original tag/codec/upgrade behavior.
- The old `readTape` API retains its byte-array and metadata contract. Both APIs
  share the Rust reader, default compression/NBT limits, error classification and
  Java storage diagnostics. Validation-enabled owned reads retain fingerprints.

Implementation: [region reader](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/storage/region/ffi.rs),
[CPU owner](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/storage/nbt/owned.rs),
and [decode ownership](RUST-DECODED-SECTIONS.md).

## Verify

Use JDK25 and a release native library with matching Java classes:

```sh
CARGO_PROFILE_RELEASE_DEBUG=line-tables-only CARGO_PROFILE_RELEASE_STRIP=none ./gradlew -PmattmcRustProfile=release test -x testRustNative --tests '*NativeChunkTapeTest' --tests '*NativeRegionFilePayloadTest' --tests '*NativeRegionFileWriterTest'
CARGO_PROFILE_RELEASE_DEBUG=line-tables-only CARGO_PROFILE_RELEASE_STRIP=none ./gradlew -PmattmcRustProfile=release parityTest -x testRustNative --tests '*NativeChunkSectionsTest'
python3 DevUtils/tests/chunk/VerifyDecodedSections.py --java-home "$JAVA_HOME" --output build/native-owned-tape/chunk-loader
python3 DevUtils/tests/chunk/VerifyDecodedSections.py --java-home "$JAVA_HOME" --output build/native-owned-tape/chunk-loader --benchmark --pairs 3
python3 DevUtils/RunWiki.py check
```

The actual Frozen 40-chunk oracle covers all block/biome/light values, palette
representation and re-serialized data. Owner tests cover region closure, thread
transfer, explicit closure, optional validation fingerprints, cancellation and
upgrade failure. Timing includes region production and parsing; separate output
paths retain older candidates. Release `06529556` passes the full Rust suite
(2,623 passed, three ignored), both Java suites (1,974 reported, two skipped,
zero failures), and actual Frozen replay with source/class/native guards.
The initial array-only test assumption and test bootstrap ordering were corrected;
a concurrent Gradle report-write failure is retained and superseded by the
completed clean full run.

Three quiet guarded pairs on the i7-12700K's CPU19 measure12.20–12.31ms Current
against13.14–13.53ms Frozen per40 chunks. Ratios0.917/0.909/0.928 pass the scoped
Frozen floor (7–9% lower cost). Each measured round has zero compilation; input,
compiled-class, native-image and untouched Frozen guards pass. These fresh CPU19
comparisons are distinct from the preceding CPU11 timings. All three fresh controls against published `45a074369` pass on the same CPU
(ratios0.729/0.722/0.735;26–28% lower cost). Actual Frozen replay and all image
guards pass in both comparisons. Seven fresh lifecycle cases and two settled
Frozen OpenGL comparisons pass with zero Vulkan VUIDs; nine Current artifacts
identify the exact native image. Four original1280×720 captures were reviewed.
These checks do not establish first-frame/first-turn, sustained streaming, memory
or whole-game FPS acceptance. Runtime/source/image receipt:
`build/native-owned-tape/gameplay/runtime-review.json`. CPU receipts:
`build/native-owned-tape/chunk-loader/frozen/results-frozen.json`. The preceding `453df088` timings and runtime
captures belong to the published saved-palette batch, not this candidate.

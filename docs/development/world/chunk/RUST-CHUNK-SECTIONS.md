# Rust chunk section serialization

Current canonical storage uses the [Rust live section owner](RUST-LIVE-SECTIONS.md).
The helper path and historical acceptance below are narrower verification;
historical pinned-body drivers reject the new container ownership changes.
Use the live-section guide's current Gradle checks; the older driver commands
below describe the historical helper acceptance, not current-owner verification.


Eligible chunk saves and current-version loads handle the `sections` list in Rust. In
[`storage/chunk/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/storage/chunk),
each section is written straight as NBT tape:

- block-state and biome containers packed as `PalettedContainer`'s codec does
- palette lists
- `BlockLight` and `SkyLight` layers
- `Y`

Before, Java encoded every container into a tag tree on the background
executor. The IO thread then flattened that tree into tape byte by byte, and
that flattening was the largest Java cost of saving. The Java bridge is
[`NativeChunkSections`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativeChunkSections.java).

## How a save flows

1. `ChunkMap.save` copies the chunk (`SerializableChunkData.copyOf`, unchanged).
   It then runs `SerializableChunkData.encode()` on the background executor.
2. `encode()` passes each section's raw containers to Rust: storage words, bits
   and palette state IDs. It also passes light bytes and the chunk's biome names.
   Rust returns the tape of the complete `sections` record.
3. Java builds the rest of the root compound as before, with an empty
   placeholder list at `sections`. The tape writer splices Rust's record in
   where the placeholder is.
4. `IOWorker` keeps the tape as the pending write. It builds the
   `CompoundTag` (`write()`) only if a pending read (`loadAsync`, `scanChunk`)
   asks for it. The region file writes the tape directly. An encoded
   tape-writer failure is retained instead of tape; `runStore` reports it and
   completes the store exceptionally before any region write.

Rust owns the section layout: which keys exist, `CompoundTag` key order,
packing and tape records. Its block-state vocabulary is built once from the
[block registry](../../game-model/RUST-BLOCK-REGISTRY.md):

- every block state's `BlockState.CODEC` compound as tape: `Name`, and
  `Properties` unless it is the default state, in `HashMap` order (the codec
  inserts `Properties` before `Name`, and the last property first)
- the storage bits of each block palette size, from `Strategy.createForBlockStates`

Java checks that the containers' strategies give the same storage bits, and
sends biome names as `holderByNameCodec` writes them with each chunk.

## How a load flows

1. `ChunkMap.scheduleChunkLoad` reads the chunk as tape
   (`IOWorker.loadForParse`): the [retained Rust region tape](RUST-REGION-TAPE.md), or a pending encoded
   write's tape. A pending tag is copied as before.
2. On the background executor, `SerializableChunkData.parseLoaded` passes the
   tape to Rust ([`load.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/storage/chunk/load.rs)).
   Rust finds the root `sections` list and decodes it:
   - Palette states are identified by matching each entry's tape byte for byte
     against the save vocabulary's encoded compounds.
   - Biome names are looked up in the registry's names.
   - Storage words, light layers and `Y` remain in a scoped Rust decode owner;
     Java receives the root tape without `sections`.
3. Java reads that smaller root as a tag. If its `DataVersion` is current
   (`upgradeChunkTag` would return it unchanged), Rust hydrates primary block,
   biome and light owners directly. Java wraps independent leases in section
   objects, without palette/word/light arrays or list/stream reconstruction.
   [Decode scopes and primary hydration](RUST-DECODED-SECTIONS.md) describe the
   ownership handoff and custom-factory admission rule.
   `parse` reads everything else from the root.
4. Java falls back to its original route for tag-only input, a non-current data
   version, a decode/hydration decline, or a custom factory. It
   upgrades the full tag and parses it, so logging, partial results and
   exceptions follow that compatibility route. `parseLoaded` closes its loaded
   tape on success, fallback and failure; direct `loadForParse` callers must
   consume or close their result. The region-tape owner guide describes the
   explicit bulk release and automatic wrapper lifetime.

Rust declines, so Java decodes the chunk, for:
- a palette entry that is not the canonical encoding of a registered state
  (for example a name without a namespace)
- a biome name not exactly as the registry writes it
- a `Y`, light layer, palette or `data` of an unexpected type or length

Unknown keys, out-of-height sections (their containers are never read) and
missing containers are supported by the native route when the other eligibility
checks pass. Native decode declines (`-2`) retain the original path; invalid
status, result-transfer and downcall failures throw instead. A decline is not a
promise of fallback after every exception.

## Constraints when changing this code

- The tape must equal `NativeNbtRegionAccess.writeTape(write())` byte for byte.
  Key order is `java.util.HashMap` iteration order at default capacity:
  `String.hashCode` bucket first, then insertion order. Insertion order follows
  `write()` and the codec's `palette`, `data` order. `data` is omitted when
  the palette has one entry. An empty list has element type 0.
- Packing preserves object identity. Equal labels mean the same state object
  (registry identity) or the same biome holder (identity map). Values appear
  in first-occurrence order, and words are packed with zero padding. Storage
  bits come from the factory's strategies: a block strategy whose bits differ
  from the vocabulary's keeps Java.
- Rust declines (Java encodes the chunk) for:
  - container, storage or palette classes it doesn't model
  - unregistered or non-`BlockState` states, and non-reference biome holders
  - missing palette entries, even unused ones
  - strategies with other entry counts

  Fallback invokes the retained Java encoder. A Rust rejection partway through
  packing now clears the lookup slots touched before its unsupported-input
  return; successful compaction clears them too. The new regressions cover
  rejected-then-valid use on the same thread. Reading the containers takes each container's lock and calls
  `DataLayer.getData()` on the snapshot's light layers before a decline, as
  `write()` does; neither is undone.
- Status handling: a decline (`-2`) or a `RuntimeException` caught inside
  section-input gathering makes the bridge decline. Its strategy checks and
  scratch setup are outside that inner catch; the caller now catches native
  encoder `RuntimeException`s and uses Java encoding. A
  too-small output buffer (`1`) retries
  with the reported size. Invalid ABI input (`-1`) and downcall failures throw
  from the
  [bridge](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativeChunkSections.java);
  `SerializableChunkData.encode()` catches the resulting `RuntimeException`
  for Java fallback. `Error` subclasses are not covered by that catch; load
  decoding keeps its separate throwing contract above.
- The root `write(placeholder)` runs outside the tape-writer catch. Its failures
  can still escape, and a throwing supplier passed through `IOWorker.submitTask`
  can leave the save future pending. Tape-writer `IOException` and
  `RuntimeException` instead travel in `Encoded.failure`; `runStore` reports
  them and completes the store exceptionally without writing. This repairs
  that tape-failure path, not every save-future failure.
- `-Dmattmc.storage.javaChunkSections=true` keeps Java section encoding and
  decoding. `NativeChunkSections.setEnabled` toggles both in tests. This
  migration changes chunk-section saves and loads; other storage owners keep
  their existing paths.
  In particular, [POI already has a separate native tape route](https://github.com/HungLo2020/MattMC/blob/121ad13c84e45555c34814d54a8199194b37f39c/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiManager.java#L80-L119).

## Verify a change

```sh
python3 DevUtils/tests/storage/VerifyRustChunkSections.py --parity-only
python3 DevUtils/tests/storage/VerifyRustChunkSections.py --forks 3
```

`NativeChunkSectionsTest` compares both routes' tapes byte for byte on:

- 40 saved chunks from a server world: 24 FULL, plus 4 each at
  `structure_starts`, `biomes`, `carvers` and `initialize_light`.
  The corpus is `src/test/resources/storage/chunks.bin`, extracted by
  `DevUtils/tests/storage/ExtractChunkNbtCorpus.py`.
- 64 synthetic chunks: single, linear, hash-map and global palettes for states
  and biomes; sections without containers; absent, lazy and explicit light.
- an all-empty section list and a palette that names one state twice

Loading compares a fingerprint of re-encoded data and selected section state:
- the tape it re-encodes to
- every section's palette class and entry identity hashes, storage class,
  bits and raw words, air/random-ticking flags, light layers and `Y`

Identity hashes are not direct reference-equality assertions or a proof that
all object identities match.

The checks:
- The 40 saved and 64 synthetic chunks are written to a region file and
  loaded on both routes. Every chunk must use Rust.
- 24 edited chunks cover eight edits across three inputs. When the original
  throws (a short light layer), both routes throw the same exception. The test
  asserts at least twelve aggregate native loads, not each edit's exact route.
- An older `DataVersion` must call an upgrade callback. That fixture uses an
  identity callback with a counter, not an actual data-fixer migration.

It also stores eight encoded chunks through `IOWorker`, queues `scanChunk`,
`loadAsync` and `loadForParse`, then synchronizes and reads back through the
same worker against
`write()` (tag equality, not region-file bytes; region headers carry write
timestamps). That eight-chunk round-trip fixture does not force or observe the
pending-write branch, reopen storage, or exercise same-chunk coalescing and
injected failures.

These tests start from `SerializableChunkData` built by `parse` or directly.
They do not run `ChunkMap.save`'s `copyOf` from live chunks, a full server, or
concurrent saves and pending reads of the same chunk.

The benchmark (`NativeChunkSectionsVerification`) times the 40 saved chunks in
three cases:

- `encode`: chunk data to tape
- `save`: `encode` plus direct region file writes with `sync=false`
- `load`: reading every chunk back from its region file and `parseLoaded`,
  including the unchanged Rust region read and decompression

Initial corpus parsing, live snapshots, `IOWorker` scheduling and final
close/flush are outside the measured operations. The `load` case does include
`parseLoaded`, while its file creation is setup. Its checksum samples section
count, chunk position and one block rather than the parity test's detailed
fingerprint. The `save` and `load` results are not durable end-to-end save/load
throughput.

Three independent JVM pairs alternate the route order on the same CPUs. Each
case must save at least 5% in every pair and at the upper 95% bootstrap
bound. Results are written to `build/chunk-sections-migration/acceptance/`.
Inspect `results.json` → `performance` → each case's `passes` value: the
driver records a failed performance gate without failing its process, so exit
success alone is not performance acceptance.

## Status

These results are historical: the vocabulary was then built in Java. Its move
to the block registry has a separate
[author-recorded comparison](../../game-model/BLOCK-REGISTRY-VERIFICATION.md),
not a new run of these original acceptance results.

The implementation author recorded the following release acceptance on
2026-10-06, before the later `8db0fd82` recovery changes: Ryzen 5
5600G, Linux x86_64, OpenJDK 25, with `passes: true` for all three cases.

Parity: seven Java tests and three Rust tests passed.
- Saving: byte-identical tapes for 40 saved chunks (960 sections, 1.8 MB of
  tape) and 64 synthetic chunks.
- Loading: identical fingerprints for all 40 saved and 64 synthetic chunks,
  every one through Rust. The author reports twelve edited cases staying native
  and twelve falling back with equal results or exceptions; the checked-in test
  only enforces the weaker aggregate route condition described above.

Mutation checks:
- Saving: eleven semantic mutations were caught. They covered hash tie
  order, hash spreading, key insertion order, identity labels, palette
  element type, storage-bits lookup, empty-list type, signed `Y`, biome names
  and pending scans.
- Loading: seven were caught: signed `Y`, the height check, the root's child
  count, light order, palette labels, the data-version gate and biome IDs.
- Proven equivalents:
  - Saving: moving `BlockLight` earlier in insertion order. It shares its
    bucket only with the later `Y`, so order cannot change.
  - Loading: passing absent `data` as an empty array. `unpack` ignores storage
    for a single-entry palette, and for larger palettes both forms fail
    `unpack`, which sends the chunk to Java.

Median time per round of 40 chunks over three JVM pairs. The last column is
the conservative saving at the upper 95% bound of the time ratio:

| Case | Java | Rust including boundary | Time saved | Conservative saving |
|---|---:|---:|---:|---:|
| `encode` | 32.8 ms | 9.2 ms | 71.9% | 70.6% |
| `save` | 61.0 ms | 38.4 ms | 37.1% | 33.2% |
| `load` | 18.4 ms | 14.3 ms | 22.7% | 20.5% |

Every pair passed. The smallest individual-pair savings were 70.9%
(`encode`), 33.6% (`save`) and 21.3% (`load`). The unchanged Rust NBT
encoding, compression and file I/O make up most of `save`'s and `load`'s
remaining time. `load`'s pairs varied widely, from 21% to 54% saved.

A few measured rounds overlapped JIT compilation (2, 4 and 4 of 180 samples
per case). These are warmed subsystem timings on this machine and corpus, not
whole-server claims. Benchmark guards require nonzero native encodes or loads
in the relevant native mode and zero in Java mode. The encode and saved/synthetic
load parity fixtures assert route use per fixture; edited-load cases use the
aggregate condition above. These checks have different strength.

## Current review and recovery limits

[The earlier save-only source review for #774](https://github.com/HungLo2020/MattMC/issues/774#issuecomment-6027620399)
matched nineteen declared rewrites across eight production Java files against
`5c02fd82`. That checks the edit boundary; this maintenance review did not rerun
Java/Rust tests, mutation checks, benchmarks or live saves. Thread-local scratch
and output buffers and the process-lifetime vocabulary still need lifecycle and
memory evidence.

[#818](https://github.com/HungLo2020/MattMC/issues/818) tracks two source-derived
recovery problems at `121ad13c`: malformed palette rejection after touching valid
labels can leave native lookup entries that affect a later encode on the same
thread, and an encode supplier that throws through `IOWorker.submitTask` can
leave the returned save future pending. Ordinary setter-produced palettes do
not create that malformed-input trigger. No runtime reproduction or world damage
was observed in this review. That earlier review did not cover loading.

[The earlier load review](https://github.com/HungLo2020/MattMC/issues/774#issuecomment-6029878781)
at `313e7a8a` reconstructed eight existing Java files from twenty-eight declared
rewrites against `5c02fd82`. It inspected loading and confirmed both #818 save
paths remained unchanged at that commit; `loadForParse` uses a separate
throwing-task wrapper. No Java/Rust suite, benchmark, live save/load or recovery
regression was rerun. Decode scratch, weak registry-name handles, pending native
decoded results and the process-lifetime vocabulary still require
lifecycle/memory evidence.

The later [recovery commit `8db0fd82`](https://github.com/HungLo2020/MattMC/commit/8db0fd82c52acd01cd6b787e5775d64fca8c3d25)
repairs rejected-compaction cleanup and the encoded tape-failure path. The commit
author reports that its regressions fail without their fixes; this maintenance
review inspected their definitions but did not rerun them:

- `rejectedSectionThenValidChunkOnTheSameThread` injects invalid storage IDs
  into a block-state container, compares Java/native failure classes, then
  compares tapes for six ordinary chunks on the same thread. The Rust unit
  test checks touched lookup entries are cleared and reuses the same labels
  in reversed order.
- `unwritableTapeFailsTheStore` directly constructs `Encoded.failure` with an
  `IOException`, asserts that exact cause within a thirty-second timeout, then
  synchronizes and confirms no stored chunk. It does not induce an actual
  tape-writer exception or test a supplier that throws before returning
  `Encoded`.

## Save-failure completion and shutdown

`IOWorker` uses the exception-completing task wrapper for ordinary and encoded
save suppliers. An exception before a supplier returns data completes its save
future exceptionally, so attached reporting/accounting handlers run; it does not
replace an earlier pending value. `Encoded.failure` still fails at `runStore`.
Supplier failures do not poison subsequent saves, reads, scans or synchronization.

Task admission is serialized with shutdown. Tasks admitted before `close()`
drain before its lower-priority shutdown barrier; requests made after shutdown
starts return failed futures with `RejectedExecutionException`, including reads
and synchronization. `synchronize()` is multi-stage: when close races its pending
write wait, the later flush/barrier task may be rejected, so its returned future
fails explicitly even though the earlier accepted writes drain. Callers requiring
a successful synchronization must await it before closing. This prevents silently
abandoned foreground futures.
The exception wrapper catches `Exception`, not `Error` subclasses.

Run the focused headless integration and existing native recovery cases with a
matching native library and JDK25:

```sh
./gradlew test -x testRustNative --tests '*IOWorkerFailureRecoveryTest' --tests '*NativeChunkSectionsTest.malformedSnapshotCompletesSaveAccountingAndRecovers' --tests '*NativeChunkSectionsTest.chunkMapSaveReportsFailureAndReleasesActualAccounting' --tests '*NativeChunkSectionsTest.recoveryFfiFixturesMatchCleanJavaAndNativeSections' --tests '*NativeChunkSectionsTest.rejectedSectionThenValidChunkOnTheSameThread' --tests '*NativeChunkSectionsTest.unwritableTapeFailsTheStore' -PmattmcTestForks=1
cargo test --manifest-path src/main/rust/Cargo.toml storage::chunk::tests:: -- --test-threads=1
```

The integration tests use the actual worker, priority executor, native region
storage and temporary local-filesystem files (which may reside on tmpfs). Latches force pending reads/coalescing before a
background write can run; a blocking region-file directory induces a real write
failure. Coverage includes throwing normal/encoded suppliers, save-completion
accounting callbacks, later successful saves, disk and pending reads/scans,
concurrent writes, synchronization, close admission races and reopening.
The malformed-snapshot case calls real `write()` and background `encode()` with
`future::join`, matching the supplier shape used by `ChunkMap`. Its accounting
check attaches a caller callback to the actual save future; it does not start a
server or instantiate `ChunkMap`.

The separate `chunkMapSaveReportsFailureAndReleasesActualAccounting` case invokes
the actual private `ChunkMap.save`, background encode, inherited `writeEncoded`
and production completion callback with real snapshots, worker and native files.
It checks the actual `activeChunkWrites` field, reporting arguments, and healthy
follow-up/reopen. The server, POI and DH boundaries and snapshot acquisition are
stubbed; this is a partially mocked boundary integration, not a live server or a
verification of `MinecraftServer`'s crash-report file output.

For a scoped pre/post scheduling-overhead comparison, run the opt-in benchmark
against both revisions with the same JDK/native profile and otherwise idle host:

```sh
MATTMC_IOWORKER_WARMUPS=5 MATTMC_IOWORKER_TRIALS=9 ./gradlew performanceTest -x testRustNative --tests '*IOWorkerThroughputBenchmark'
```

It emits `IOWORKER_THROUGHPUT` records (defaults: five measured trials after two
warmups; the command above increases both), using identical ordered 256-chunk datasets (seed 818, 4 KiB payloads) on normal
and pre-encoded routes. Timing includes worker creation, admission, writes,
flush and close; fixture encoding and reopened-content checks are outside the
interval. Records include process CPU time, GC count/time and JIT compilation time
deltas as noise diagnostics. These are process-wide counters; background JVM work
can contribute. Normal precedes encoded on each trial, so route order and continued
warmup also matter. It has no timing assertions. Compare repeated matched JVM runs
with identical configuration and retain all trials, including outliers; this is a small local-filesystem/storage workload, not physical-disk or live-game acceptance,
nor a throughput promise
for live snapshots, full worlds or the renderer.

The Rust FFI recovery regression reuses the actual thread-local encoder across
twelve malformed block/biome rejections, intervening zero-bit containers and
forced insufficient-output-buffer retries. Successful tapes must match a fresh
encoder byte for byte and equal shared clean-Java oracle resources; rejected and
too-small calls must leave output guards untouched. The two files in
`src/test/resources/storage/`, `chunk-recovery-valid-sections.bin` and
`chunk-recovery-zero-sections.bin`, are named `sections` records serialized by
`SerializableChunkData.write()` with native sections disabled and test-only
block/biome `PalettedContainer` subclasses that bypass the exact-class native live,
resize and packing admission guards, then the retained Java
`NativeNbt.TapeWriter.writeTag`. Merely disabling native sections is insufficient
for an independent oracle because ordinary block palettes can still use native
packing. The native bridge comparison uses separate ordinary containers. The valid section at Y=0 is stone/plains
except element zero is dirt/desert; the zero-bit fixture is all stone/plains.
The Java regression regenerates these bytes in memory and compares both the
checked-in resources and actual native bridge output. Rust compares its fresh
encoder with the same resources before every recovered tape is checked against
that oracle. These are test artifacts, not full NBT documents or saved worlds.
The direct compaction cleanup regression remains alongside them.

These cases address the remaining save-future boundary tracked in
[#818](https://github.com/HungLo2020/MattMC/issues/818). They are bounded headless
storage checks, not a live-world durability, shutdown-under-process-crash or
whole-game performance claim. Earlier review notes above describe their original
commits, not current verification.

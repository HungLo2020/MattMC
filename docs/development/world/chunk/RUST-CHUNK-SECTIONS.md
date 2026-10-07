# Rust chunk section serialization

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
   and palette labels. It also passes light bytes and the chunk's biome names.
   Rust returns the tape of the complete `sections` record.
3. Java builds the rest of the root compound as before, with an empty
   placeholder list at `sections`. The tape writer splices Rust's record in
   where the placeholder is.
4. `IOWorker` keeps the tape as the pending write. It builds the
   `CompoundTag` (`write()`) only if a pending read (`loadAsync`, `scanChunk`)
   asks for it. The region file writes the tape directly.

Rust owns the section layout: which keys exist, `CompoundTag` key order,
packing and tape records. Java supplies vocabulary built once:

- every block state's `BlockState.CODEC` compound as tape
- the storage bits of each palette size, taken from the strategies themselves
- biome names as `holderByNameCodec` writes them, sent with each chunk

## How a load flows

1. `ChunkMap.scheduleChunkLoad` reads the chunk as tape
   (`IOWorker.loadForParse`): the region file's tape, or a pending encoded
   write's tape. A pending tag is copied as before.
2. On the background executor, `SerializableChunkData.parseLoaded` passes the
   tape to Rust ([`load.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/storage/chunk/load.rs)).
   Rust finds the root `sections` list and decodes it:
   - Palette states are identified by matching each entry's tape byte for byte
     against the save vocabulary's encoded compounds.
   - Biome names are looked up in the registry's names.
   - It also returns storage words, light layers and `Y`, plus the root tape
     without `sections`.
3. Java reads that smaller root as a tag. If its `DataVersion` is current
   (`upgradeChunkTag` would return it unchanged), Java builds each section the
   way the codecs do: `PalettedContainer.unpack` with the decoded palette and
   words, `new DataLayer(bytes)`, and default containers for missing keys.
   `parse` reads everything else from the root.
4. Java falls back to its original route for tag-only input, a non-current data
   version, a Rust decline, or an `unpack` that does not succeed cleanly. It
   upgrades the full tag and parses it, so logging, partial results and
   exceptions are the original ones.

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

  Fallback invokes the retained Java encoder; this does not guarantee recovery
  of native scratch or completion of the enclosing save future (see the tracked
  limits below). Reading the containers takes each container's lock and calls
  `DataLayer.getData()` on the snapshot's light layers before a decline, as
  `write()` does; neither is undone.
- Status handling: a decline (`-2`) or a `RuntimeException` caught inside
  section-input gathering falls back to Java encoding. Strategy checks and
  scratch setup outside that catch do not share this recovery rule. A
  too-small output buffer (`1`) retries
  with the reported size. Invalid ABI input (`-1`) and downcall failures throw
  instead of falling back; see the
  [bridge](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativeChunkSections.java).
  An `IOException` from the Java tape writer now surfaces from `encode()` on the
  background executor (as `UncheckedIOException`), not from the IO thread's
  region write.
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
timestamps). The test does not force or observe the pending-write branch, reopen
storage, or exercise same-chunk coalescing and injected failures.

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

The implementation author recorded release acceptance on 2026-10-06: Ryzen 5
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
was observed in this review. Acceptance needs deterministic rejection-then-valid
encoding and exceptional-completion regressions, alongside pending/coalescing,
reopen and concurrent-save coverage; the successful fixtures above do not close
those requirements. That earlier review did not cover loading.

[The current load review](https://github.com/HungLo2020/MattMC/issues/774#issuecomment-6029878781)
at `313e7a8a` reconstructed eight existing Java files from twenty-eight declared
rewrites against `5c02fd82`. It inspected loading and confirmed both #818 save
paths remain unchanged; `loadForParse` uses a separate throwing-task wrapper.
No Java/Rust suite, benchmark, live save/load or recovery regression was rerun.
Decode scratch, weak registry-name handles, pending native decoded results and
the process-lifetime vocabulary still require lifecycle/memory evidence.

# Rust chunk section serialization

Saving a chunk now builds its `sections` list in Rust. In
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

  Java then fails exactly as before where the original would. Reading the
  containers takes each container's lock and calls `DataLayer.getData()` on the
  snapshot's light layers before a decline, as `write()` does; neither is
  undone.
- Status handling: a decline (`-2`) or an exception while gathering section
  input falls back to Java encoding. A too-small output buffer (`1`) retries
  with the reported size. Invalid ABI input (`-1`) and downcall failures throw
  instead of falling back; see the
  [bridge](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativeChunkSections.java).
  An `IOException` from the Java tape writer now surfaces from `encode()` on the
  background executor (as `UncheckedIOException`), not from the IO thread's
  region write.
- `-Dmattmc.storage.javaChunkSections=true` keeps Java encoding.
  `NativeChunkSections.setEnabled` toggles this in tests. Entity, POI and other
  region storage still use the generic tape writer.

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

It also stores encoded chunks through `IOWorker` and checks pending
`loadAsync` and `scanChunk` results and the read-back from disk against
`write()` (tag equality, not region-file bytes; region headers carry write
timestamps).

These tests start from `SerializableChunkData` built by `parse` or directly.
They do not run `ChunkMap.save`'s `copyOf` from live chunks, a full server, or
concurrent saves and pending reads of the same chunk.

The benchmark (`NativeChunkSectionsVerification`) times the 40 saved chunks in
two cases:

- `encode`: chunk data to tape
- `save`: `encode` plus the region file write

Three independent JVM pairs alternate the route order on the same CPUs. Each
case must save at least 5% in every pair and at the upper 95% bootstrap
bound. Results are written to `build/chunk-sections-migration/acceptance/`.
Inspect `results.json` → `performance` → each case's `passes` value: the
driver records a failed performance gate without failing its process, so exit
success alone is not performance acceptance.

## Status

The implementation author recorded release acceptance on 2026-10-06: Ryzen 5
5600G, Linux x86_64, OpenJDK 25, with `passes: true` for both cases.

Parity: four Java tests and three Rust tests passed with byte-identical tapes.
That covers 40 saved chunks (960 sections, 1.8 MB of tape) and 64 synthetic
chunks.

Mutation checks:
- Eleven semantic mutations were all caught. They covered:
  - hash tie order, hash spreading and key insertion order
  - identity labels, palette element type and storage-bits lookup
  - empty-list type, signed `Y` and biome names
  - pending scans of lazily built tags
- One further mutation was a proven equivalent and was replaced. Moving
  `BlockLight` earlier cannot change order, because it shares its bucket only
  with the later `Y`.

Median time per round of 40 chunks over three JVM pairs. The last column is
the conservative saving at the upper 95% bound of the time ratio:

| Case | Java | Rust including boundary | Time saved | Conservative saving |
|---|---:|---:|---:|---:|
| `encode` | 32.7 ms | 8.7 ms | 73.3% | 69.9% |
| `save` | 57.5 ms | 37.5 ms | 34.7% | 33.7% |

Every pair passed. The smallest individual-pair savings were 70.1% (`encode`)
and 34.0% (`save`). The unchanged Rust NBT encoding, compression and file
write make up most of `save`'s remaining time.

A few measured rounds overlapped JIT compilation (1 and 4 of 180 samples per
case). These are warmed subsystem timings on this machine and corpus, not
whole-server claims.

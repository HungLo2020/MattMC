# Pack access and repository

The directory, ZIP and vanilla pack sources described here use Rust for
lookups, listings and byte reads. Java's
[`NativePackResources`](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/src/main/java/net/minecraft/server/packs/NativePackResources.java)
holds a native handle and implements `PackResources`; metadata sections still
decode through their Java codecs, from bytes Rust reads.

Source-reviewed at
[`398a0078`](https://github.com/HungLo2020/MattMC/commit/398a00782007587c779264aaa62222036b8a9361).
Keeping the Java interface does not make arbitrary custom `PackResources`
implementations compatible: the [resource manager](RESOURCE-MANAGER.md)
requires `NativePackResources` instances, including its vanilla subclass.
See [verification](VERIFICATION.md) for the recorded fixtures and runtime limits.

## Pack kinds

| Kind | Opened by | Paths |
| --- | --- | --- |
| Directory | `NativePackResources.DirectorySupplier` | Validated like `PathPackResources` (`decomposePath`; Java's messages) |
| ZIP | `NativePackResources.ZipSupplier` | Looked up literally, like `FilePackResources` |
| Directory inside an archive | `DirectorySupplier` given a supported `jar:` path (the jar's built-in packs) | Validated |
| Vanilla | [`VanillaPackResourcesBuilder`](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/src/main/java/net/minecraft/server/packs/VanillaPackResourcesBuilder.java) | Validated; separate root-file and per-type search orders |

A pack is a table of roots in [`pack.rs`](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/src/main/rust/assets/packs/pack.rs).
For layered packs, resource lookups search overlays in reverse declaration
order, then the primary layer; root files such as `pack.mcmeta` use the primary
root. Vanilla's builder reverses each root list's insertion order independently;
`pushUniversalPath` adds the path's `assets` and `data` children to the respective
type lists. Listings keep the first file found per location across roots.
Directory walks list regular files, do not follow symlink entries, and skip
`.DS_Store` when running in the IDE. This traversal rule is separate from direct
lookups and [repository validation](#discovery-and-selection).

The ZIP reader ([`zip.rs`](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/src/main/rust/assets/packs/zip.rs))
supports ZIP64, stored and deflated entries. It rejects names that are not
UTF-8; direct name lookup selects the last duplicate entry and, if `name` is
absent, also checks for the directory entry `name/`. These rules do not establish
compatibility with every third-party archive or pack.

`close()` drops the pack's cached open archives; a later access can reopen them.
It does not release the native handle or reset a remembered archive-open failure.
The Java pack's `Cleaner` releases its handle's `Arc<Pack>` ownership, while
native managers retain their own `Arc` references (see
[`packs/ffi.rs`](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/src/main/rust/assets/packs/ffi.rs)
and [manager lifetimes](RESOURCE-MANAGER.md#lifetimes-and-reloads)). A supplier
captures its Java pack owner. Retain the supplier or pack rather than caching a
raw handle past its owner; do not manually release a handle Java can still use.

## `pack.mcmeta`

[`metadata.rs`](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/src/main/rust/assets/packs/metadata.rs)
ports `PackFormat` decoding (`min_format`, `max_format`, `pack_format`,
`supported_formats`), overlay entries, their validation messages and
`PackCompatibility.forVersion`. [`NativePackFormats`](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/src/main/java/net/minecraft/server/packs/metadata/pack/NativePackFormats.java)
wraps them as the Java codecs `PackMetadataSection` and `OverlayMetadataSection` use.

DataFixerUpper's JSON coercions are part of the format and must stay exact:
`Codec.INT` accepts only numbers (and truncates them, so `1e10` becomes
`1410065408`), booleans are not numbers, and a non-array reports
`Not a json array: …`. Check new cases against Frozen before changing them.

## Discovery and selection

[`repository.rs`](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/src/main/rust/assets/repository.rs)
lists and classifies folder entries.
[`FolderRepositorySource`](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/src/main/java/net/minecraft/server/packs/repository/FolderRepositorySource.java)
applies Java's `DirectoryValidator` to symlinks and known directories before
accepting their packs; a validated symlink target is classified again by Rust.
Non-default filesystems go through `discoverArchivePacks`, which supports
`jar:` archive folders such as the game's `/data/minecraft/datapacks`, not
arbitrary filesystem providers or nested ZIP packs.
`PackSelectionScreen` uses `FolderRepositorySource.detectPackPath` for dropped files.

`Pack.Position.insert` and `PackRepository.rebuildSelected` call Rust for the
insertion index and the selected order. A pinned pack compares against its
own position while scanning from the opposite end; keep that asymmetry.

## Troubleshooting

- *A pack is missing from the list*: look for `Found non-pack entry` or
  `Ignoring potential pack entry` (a symlink the validator rejected) in the log.
- *`Invalid path` or `Invalid segment` errors*: a directory or vanilla pack was
  asked for a path that `decomposePath` rejects. ZIP packs do not validate
  paths, as in vanilla.
- *A ZIP pack reads as empty*: the first access logs `Failed to open pack …`;
  that archive access object remembers the failure and does not retry, even
  after `close()`. Correct the archive and reopen the pack through its supplier.
- *A custom pack fails during manager construction*: implementing
  `PackResources` alone is insufficient. Use the supported native suppliers or
  explicitly extend the native integration; there is no generic Java fallback.

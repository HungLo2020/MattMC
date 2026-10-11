# Pack access and repository

Rust opens and reads every resource and data pack. Java's
[`NativePackResources`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/server/packs/NativePackResources.java)
holds a native handle and keeps the `PackResources` API; metadata sections still
decode with their Java codecs, from bytes Rust reads.

## Pack kinds

| Kind | Opened by | Paths |
| --- | --- | --- |
| Directory | `NativePackResources.DirectorySupplier` | Validated like `PathPackResources` (`decomposePath`; Java's messages) |
| ZIP | `NativePackResources.ZipSupplier` | Looked up literally, like `FilePackResources` |
| Directory inside an archive | `DirectorySupplier` given a zipfs path (the jar's built-in packs) | Validated |
| Vanilla | [`VanillaPackResourcesBuilder`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/server/packs/VanillaPackResourcesBuilder.java) | Validated; per-type roots, then universal roots |

A pack is a table of roots in [`pack.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/assets/packs/pack.rs):
overlays are searched newest first, then the primary root. Listings visit roots
in that order and keep the first file found per location. Directory walks list
regular files only, do not follow symlinks, and skip `.DS_Store` when running
in the IDE.

The ZIP reader ([`zip.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/assets/packs/zip.rs))
supports ZIP64, stored and deflated entries. Like `java.util.zip.ZipFile` it
rejects names that are not UTF-8, and the last duplicate name wins. A lookup of
`name` also matches a directory entry `name/`.

`close()` keeps vanilla's semantics: it releases open archive files, and the
next read reopens them. The native pack itself is freed when its Java object is
collected (`Cleaner`). Do not cache handles past their Java owner.

## `pack.mcmeta`

[`metadata.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/assets/packs/metadata.rs)
ports `PackFormat` decoding (`min_format`, `max_format`, `pack_format`,
`supported_formats`), overlay entries, their validation messages and
`PackCompatibility.forVersion`. [`NativePackFormats`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/server/packs/metadata/pack/NativePackFormats.java)
wraps them as the Java codecs `PackMetadataSection` and `OverlayMetadataSection` use.

DataFixerUpper's JSON coercions are part of the format and must stay exact:
`Codec.INT` accepts only numbers (and truncates them, so `1e10` becomes
`1410065408`), booleans are not numbers, and a non-array reports
`Not a json array: …`. Check new cases against Frozen before changing them.

## Discovery and selection

[`repository.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/assets/repository.rs)
lists a packs folder and classifies each entry as a directory pack, a ZIP pack,
a symlink, or not a pack. Java still applies the `DirectoryValidator` to symlinks
(it is a configurable allow list) and then asks Rust to classify the target.
Folders on another filesystem (the game jar's `/data/minecraft/datapacks`)
go through `discoverArchivePacks`.
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
  after that the pack behaves as empty without retrying, as
  `SharedZipFileAccess` did.

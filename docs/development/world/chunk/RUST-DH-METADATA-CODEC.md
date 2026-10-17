# Rust DH saved-metadata codecs

Generation, compression and horizontal-uniformity payloads now decode directly
into the primary [Rust metadata planes](RUST-DH-SOURCE-METADATA.md). Rust owns
payload extent, byte projection and uniformity normalization. Java supplies
bounded byte IO, compression wrappers and DTO orchestration; ordinary imports
create no Java metadata snapshot and perform no per-byte native calls.

Implementation: [native codec](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/chunk/dh_columns/metadata_codec.rs)
and [temporary IO bridge](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativeDhMetadataCodec.java).
Contributor payloads and contextual material resolution remain Java migration work.

## Ownership and compatibility

- Keep DH mutation exclusion and pin the independent metadata lease throughout
  IO. A retained list remains valid after its source table closes. No input
  buffer pointer or plane borrow crosses a Java IO call.
- Generation/compression imports consume exactly 4,096 payload bytes, preserving
  signed values, logical list size, suffixes and the successful mutation prefix
  on failure. Frozen retains backing capacity after list shrink; a short logical
  list still consumes the full payload. Newly grown elements remain zero-filled.
- Uniformity first skips 4,096 compression bytes, then maps each nonzero byte to
  true. Its existing V3/V4 behavior is verified separately: Frozen predates these
  sidecars and does not establish their policy.
- Encoding follows each metadata list's logical size. Compression output appends
  the fixed normalized uniformity plane. Custom lists/sources retain compatibility
  traversal before any native mutation.
- Exact private uncompressed and already decompressed Zstd-block byte-array
  streams are read in place: their whole remaining decompressed array is fed to
  Rust in one critical call, and the stream position moves to its end, as the
  bulk reads did. Other output uses at most 16 KiB per call. Streaming LZ4, XZ
  and Zstd retain byte reads so decompressor failures preserve their exact prefix.
  Read-ahead stays inside private DTO streams; completion ignores unused bytes
  and pending errors beyond the completed payload.
- For V3+ rows in those two modes, the compression blob is decompressed once:
  the compression plane is read, then the stream rewinds so the uniformity
  reader rereads the same bytes. Each plane keeps its own failure (a short
  compression plane is `DataCorruptedException`, a short uniformity plane a raw
  `EOFException`).
- `-Dmattmc.dh.javaCodec=true` selects transitional Java codec controls. These
  controls retain Frozen's backing-capacity read behavior; they are verification
  support, not the final runtime architecture.

## Verification

Use JDK25 with a matching release native library:

```sh
./gradlew -PmattmcRustProfile=release buildRustNative testClasses -x testRustNative
./gradlew -PmattmcRustProfile=release test -x buildRustNative -x testRustNative --tests '*NativeDhMetadataCodecTest' --tests '*NativeDhCodecTest' --tests '*NativeDhMetadataListTest' --tests '*FullDataSourceV2SemanticUniformityTest'
JAVA_HOME=/path/to/jdk25 python3 DevUtils/tests/world/chunk/VerifyDhMetadataCodec.py --output build/dh-metadata-oracle
JAVA_HOME=/path/to/jdk25 python3 DevUtils/tests/world/chunk/VerifyDhPointCodec.py --output build/dh-point-oracle
python3 DevUtils/RunWiki.py check
```

The independent observer compiles against actual unchanged Frozen and Current,
compares complete signed metadata state, all compression modes, deprecated write
rejection, shrunk/extended lists and EOF prefixes, and requires native execution.
An optional `--saved-corpus` accepts a read-only full-DTO manifest with database
and blob hashes. The point observer remains required when shared byte IO changes.
These CPU checks supplement gameplay; they do not establish broad rendering parity.

## Current work

The initial observer exposed a pre-existing short-list mismatch: Current rejected
reads before IO while Frozen retained partial values and reported corruption at
EOF. The corrected native route consumes the fixed payload and preserves logical
extent. Receipts, including rejected attempts, live in
`build/native-dh-metadata-codec/`. The corrected candidate matches actual Frozen across 606 metadata rows and
696,830 point rows, including real saved metadata and 120 point blobs; all
source/native/class/corpus guards pass. The full integrated Rust suite passes
2,723 tests with three ignored. Full Java/parity passes 2,043 tests with two skipped. Two serial JVM runs per
side over 24 real saved DTOs measure predecessor/current/Frozen medians of
78.73/43.62/41.51 ms: Current improves 44.6%, remaining 5.1% slower than Frozen.
Checksums, source/native/class/predecessor/corpus guards match. Initialization,
metadata, point decoding and close are included; material resolution is omitted.
This scoped benchmark does not establish a whole-game gain. Matched frame
timings now pass every FPS/p99 floor across all20 exact6000-frame runs;
see the [integrated matrix](../../rendering/GAMEPLAY-PERFORMANCE.md#dh-metadata-and-bulk-io-checkpoint).
This matrix includes the upstream sky/id-map/GUI changes and does not isolate
the metadata change’s whole-game effect. All seven lifecycle scenarios pass
with zero exceptions, Rust panics and GAL failures. Their seven original captures
were reviewed; heavy haze limits them to structural transition evidence.

Vanilla, no-shader DH and shader+DH settled pairs pass, VUID zero. All 13
originals and ten Current native identities were reviewed. The validation
controller terminated with signal 143 after both final DH captures completed;
the signal origin is unconfirmed. Existing harness post-processing recovered
the comparison from byte-guarded original artifacts without rerunning clients.
The interrupted controller and recovery receipt remain recorded. First-frame,
transient terrain, broad gameplay and sustained-memory acceptance remain open.

After all clients exited, retention retired 29 completed generated world copies
and reclaimed 3.11 GiB, preserving 1,387 selected evidence hashes. Originals,
profiles, per-frame diagnostics and the interrupted final DH scope remain.

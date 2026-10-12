# Rust DH saved-point codecs

Ordinary DH saved-source point payloads now stream through Rust directly against
the [primary column table](RUST-DH-SOURCE-TABLE.md). Java retains compression
wrappers, database/network DTO orchestration, material resolution and the
separate generation/compression/uniformity/contributor payloads. There is no
Java point array or per-point native get/set loop on the admitted route.

Implementation: [codec](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/chunk/dh_columns/codec.rs),
[table mutations and closed leases](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/chunk/dh_columns/source.rs)
and [IO bridge](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativeDhCodec.java).

## Ownership and compatibility

- The legacy reader consumes signed big-endian column lengths and 64-bit points.
  The current codec follows counts, IDs/flags, heights, predicted bottoms and
  packed lights in the existing x/z order. Prediction spans column boundaries.
  Interior and four border payloads keep their existing formats.
- Preserve Java integer wrapping, fifth-varint-byte truncation, signed ID shifts
  and unmasked height/bottom setters. Validation runs at the original stage and
  obeys the caller's mode; zero sentinel points remain legal when it is disabled.
- Counts clear before resizing. Truncation or malformed data preserves the
  successful mutation prefix. A malformed negative count preserves Frozen’s
  negative logical list size separately from all actual Rust slice lengths;
  native consumers decline the invalid extent. The stream bridge delivers
  already-read bytes
  before raising a decompressor error. It ignores a pending read-ahead error
  when the parser has already completed or diagnosed an earlier format error.
  No native callback holds a table or point borrow across Java IO.
- Java transfers at most 16 KiB of bytes per feed. Rust retains only parser state
  between feeds, never the byte-buffer pointer. Output likewise streams bounded
  byte spans. These byte bounds do not cap total source point storage.
- Keep DH's original mutation exclusion and pin the source throughout decoding,
  encoding and escaped-reader projection. Reader extents refresh on completion
  or failure. Unescaped columns stay in Rust; list identities survive writes.
- Custom sources/lists, closed sources and writable alias arrangements retain
  the original Java traversal before any native mutation. Read-only native
  serialization also supports missing columns. The bridge does not borrow GPU
  handles or change rendering ownership.

Source close now retains native column leases in one lazy compatibility owner,
then releases the live table. It constructs no Java readers for unescaped
columns. Later closed-source reads create independent CPU leases on demand;
existing readers remain writable. Closed-source replacement keeps null/empty
facts and list identity. Grouped reader arenas still have their separate GC
lifetime; closing a source does not guarantee that all point storage is freed.

## Verify a change

Use JDK25 and a matching release native library:

```sh
./gradlew -PmattmcRustProfile=release buildRustNative testClasses -x testRustNative
./gradlew -PmattmcRustProfile=release test -x buildRustNative -x testRustNative --tests '*NativeDhCodecTest' --tests '*NativeDhColumnGroupTest' --tests '*FullDataSourceV2SemanticUniformityTest' --tests '*FullDataSourceV2ContributorReplacementTest'
JAVA_HOME=/path/to/jdk25 python3 DevUtils/tests/world/chunk/VerifyDhPointCodec.py --output build/dh-point-codec-oracle
./gradlew testRustNative
python3 DevUtils/RunWiki.py check
```

The observer compiles the same source separately against actual unchanged
Frozen and Current. It compares compressed bytes, complete column/point state,
legacy inputs and corruption prefixes, and requires Current's native route.
Integrated V3/V4 sidecar regressions remain separate from this point-payload
comparison. `-Dmattmc.dh.javaCodec=true` keeps the Java codec for controlled
comparisons; it is transitional verification support.

## Current verification

The first candidate incorrectly ran final validation when disabled; the sentinel
regression exposed it. The corrected candidate passes 12 focused Java and 47
Rust DH tests. Production compatibility tests cover interior/borders, all
compression reads, permitted writes, deprecated Zstd-stream write rejection,
truncation, malformed varints, aliases, escaped readers and lazy source close.
The independent Frozen observer exposed a pre-existing negative-count mismatch
(after 204,950 compared rows): Frozen retains size -1 and later reports EOF,
while the former native list rejected it immediately. The current correction
keeps that invalid logical size separate from Rust storage.

The corrected `94d74a8b` candidate passes full Rust (2,718 tests, three ignored)
and Java/parity (2,036 passed, two skipped). The actual Frozen observer matches
696,830 rows across 170 cases, including 120 real saved point blobs, with
source/native/class/corpus guards intact.

Two JVM runs per side over 24 real saved DTOs per sample measure old
Current/current/Frozen medians of 110.88/78.29/41.72 ms. Initialization, metadata,
point decoding and close are included; contextual material resolution is
omitted. Current improves 29.4% over its published predecessor but still takes
1.88 times Frozen. Checksums, source/native/class inputs and saved-source hashes
match; these scoped results do not establish a whole-game gain.

Seven lifecycle checks and settled DH/Iris+DH pairs pass with VUID zero.
Eleven accepted originals and nine Current native identities were reviewed.
Hazy lifecycle views provide structural evidence only. The first no-shader DH
pair omitted required depth attachments and failed coverage acceptance; the
corrected pair passes. Its two rejected originals and failure receipts remain.

All twenty matched 6,000-frame timings complete cleanly with identical live JVM
controls and source/native/class/Frozen/prompt/prepared-input/saved-corpus guards.
DH FPS remains 5.8% below Frozen; other FPS and all p99 floors pass. Current DH
RSS peaks at 8.77–9.23 GiB and combined peaks at 9.89/10.40 GiB below the guard.
See the [complete matrix](../../rendering/GAMEPLAY-PERFORMANCE.md#dh-saved-point-codec-checkpoint).

The game-only allocation recording completes cleanly at 9.54 GiB RSS. Sampled
weighted native-segment allocation totals 971 MB versus 916 MB in the earlier
recording; variable background work prevents normalized comparison. Sampled stacks end at refresh and limit caller attribution. No overall allocation decrease
or retained-memory bound is claimed. Codec decode/encode stacks account for
134/27 MB of weighted allocation, identifying bounded IO scratch as further
profile work rather than proving the cause of the FPS gap.

Receipts: `build/native-dh-codec/`. Broad rendering, first-frame/temporal defects,
sustained streaming and long-memory acceptance remain open.

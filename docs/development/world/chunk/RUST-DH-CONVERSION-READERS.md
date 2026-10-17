# Rust DH conversion readers

> **Current state (2026-10-13):** the Java `NativeDhConversionReader` and its
> exports were removed. Rust's conversion loop now selects columns itself
> (`Reader::select`), including escaped columns, which are native-backed views.
> `VerifyDhConversionReader` was retired; the content below is historical. See
> [Rust-only DH render build](../../rendering/RUST-DH-RENDER-BUILD.md).

Full-data to render-data conversion uses a scoped Rust reader over the primary
column table. A conversion creates one reader and selects each column in the
existing x/z order. Rust retains the selected allocation until the next
selection or scope close. Ordinary conversion does not create persistent Java
column-list identities, mutable-column adapters or grouped arena registrations
for these reads. It does not copy points into a second array.

Implementation: [Rust reader](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/chunk/dh_columns/reader.rs),
[temporary adapter](https://github.com/HungLo2020/MattMC/blob/1490391f94850b7afd3335d1c9448ca0b79ea9ff/src/main/java/net/minecraft/world/level/chunk/NativeDhConversionReader.java)
and [consumer](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/com/seibel/distanthorizons/core/dataObjects/transformers/FullDataToRenderDataTransformer.java).

## Lifetime and compatibility

- Consume a selected list before selecting another column. This internal,
  reusable read cursor must never escape to a source, contributor map or caller.
- Native reads resolve the selected allocation's current extent. No Rust slice
  or mutable borrow survives a downcall or Java callback. There is no cached
  point pointer to become stale after vector growth.
- The cursor owns one independent native column lease. Source replacement or
  closure cannot free it prematurely. A confined Java arena invalidates the
  cursor before releasing its lease at scope close.
- Keep DH's existing source mutation exclusion during conversion. Ownership
  safety does not establish concurrent mutation or a transactional snapshot.
- Already escaped lists, custom lists and closed sources retain their public
  getter path and identity. Source subclasses retain the original traversal.
  Missing/empty facts and corrupt negative logical sizes remain distinct.
- Contextual material/color callbacks, sparse semantic sidecars and source
  traversal remain Java. The subsequent [conversion-policy migration](../../rendering/RUST-DH-CONVERSION.md)
  moves arithmetic into Rust and consumes this reader directly. Its full-source
  CPU comparison, lifecycle checks and settled pairs pass; broader runtime
  acceptance remains open. This published reader checkpoint alone does not
  complete conversion migration.

## Verification

Use JDK 25 and the ordinary release native build:

```sh
./gradlew -PmattmcRustProfile=release test -x testRustNative --tests '*NativeDhConversionReaderTest' --tests '*NativeDhCodecTest'
CARGO_TARGET_DIR=build/rust/target cargo test --profile suite --manifest-path src/main/rust/Cargo.toml --lib world::level::chunk::dh_columns::reader:: -- --test-threads=1
```

Regressions cover unescaped primary points without persistent Java bindings,
custom/escaped/missing inputs, nested readers, growth, replacement and closure.
Release `bbd8fc84` passes all 51 Rust DH-column tests and 2,049 Java/parity
tests (two skipped). Three settled Frozen/Current visual pairs pass with zero
Vulkan validation findings; all six originals were reviewed. World unload/reload and resource reload also pass with no exceptions, Rust
panics, dependency violations or GAL validation failures. Settled images do not prove temporal parity.

The [saved-source observer](https://github.com/HungLo2020/MattMC/blob/1490391f94850b7afd3335d1c9448ca0b79ea9ff/DevUtils/tests/world/chunk/VerifyDhConversionReader.java)
compiles separately against Current and unchanged Frozen. Arguments are a saved
DTO manifest and a boolean selecting the native reader. It measures hydration,
ordered consumption of every point and closure. The public-reader control uses
the same Current classes/native image. Contextual colors and GPU work are
excluded; lower Java allocation does not prove bounded retained memory.
Generated controls and receipts: `build/native-dh-conversion-reader/`.

## CPU candidate measurements

Two interleaved JVMs per side consume all points from 24 saved sources after
hydration. The scoped reader/public-reader/Frozen medians are
56.59/57.20/46.14 ms, with 7.14/33.85/4.21 MB Java allocation per batch. Complete
point checksums and source/class/native/corpus guards pass. The candidate cuts
Java allocation by 78.9% against the public-reader control, but still takes
22.6% longer than Frozen; this is not a CPU parity pass or an FPS result.

The first scalar interface measured 57.89 ms. A later generation-checked direct
span experiment measured 55.84 ms but doubled candidate allocation to 15.00 MB
and required broad point-storage invalidation changes. That experiment was
removed; production source hashes match the simpler 56.59 ms candidate.
Receipts retain all three attempts. An ordinary-gameplay pair failed before
completion: Current lost input focus before travel, while Frozen hit a
reflection error before measurement. Neither side establishes a paired timing.
A separate five-second-per-phase Frozen diagnostic completed without
reproducing the error; it is not combined with the failed Current observation.
The observer now writes complete exception stacks. See the
[gameplay verification record](../../rendering/GAMEPLAY-PERFORMANCE.md#conversion-reader-checkpoint).

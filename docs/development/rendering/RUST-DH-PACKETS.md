# Rust DH semantic packets

Ordinary DH builders use `render/dh_quads/packets.rs` to select each packet
layer, plan contextual callbacks, shade colors and encode vertices and semantic
companions. Java supplies contextual dirt colors and directional shade scalars
in the original order. [Native column handoff](RUST-DH-COLUMN-HANDOFF.md) now
retains final packets in Rust through ledger publication. Java transport arrays
are requested for explicit packet APIs and diagnostics; ordinary production
does not reconstruct temporary `BufferQuad` objects or Java vertex packets.
Subclassed builders retain the existing compatibility implementation.

The packet plan is an independent immutable CPU snapshot. Close it with
try-with-resources, including when a callback throws. Closing or mutating the
source builder during a callback cannot invalidate the selected rows. The
collector receives independent packet bytes. This changes no GPU ownership;
VulkanicGAL still owns resources, submission and presentation.

## Preserve these contracts

- Keep layer and vertex order, including the separate upward water route.
- Preserve wrapped short coordinates and signed byte lighting remainders.
  Keep unused vertex padding zero and opacity policy consistent with the builder.
- Rust decides which grass vertices need dirt. Java calls the dirt getter and
  then the shade getter for every selected vertex, including error-marked grass.
  Preserve the callback failure prefix and float shading for NaN and infinity.
- Water diagnostics occur in vertex order and exclude error-marked water.
  Their admission uses the immutable launch configuration.
- Split transport at the collector's quad limit; vertex bytes and all three
  semantic companion arrays must have matching extents. No native pointers or
  GPU handles enter these arrays.

Implementation: [packet owner](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/dh_quads/packets.rs),
[scoped callback bridge](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/com/seibel/distanthorizons/core/dataObjects/render/bufferBuilding/NativeDhPacketPlan.java).

## Verification

Use the build and focused test commands in [Rust DH quad builds](RUST-DH-QUADS.md).
`VerifyDhPackets.java` under `DevUtils/tests/rendering/` must be compiled against
untouched Frozen, then run with that tree's classpath to `record PATH`. Run the
identical compiled driver with Current's classpath and exact native release to
`verify PATH`. Bind the render wrapper before the builder class initializes.

The saved `src/test/resources/rendering/frozen-dh-packets.bin.gz` fixture embeds
seven Frozen class hashes and 96 synthetic cases. It records four public packet
routes with five callback failure settings: 1,920 output/failure-prefix checks.
Cases cover all grass modes, nullable mode, debug suppression, transparency,
overlap errors, signed light values, wrapped coordinates and unusual shade
values. The earlier 512-case quad fixture supplies additional topology/byte
replay. Expected encoding is executed by Frozen, not copied into the recorder.

These controls and isolated CPU builder timings do not establish gameplay FPS,
visual parity, streaming or sustained memory acceptance. Current packet test
and benchmark receipts belong in `build/native-dh-packets/`. Face emission,
contextual getter implementations and collector lifecycle orchestration remain
Java; their migration is subsequent work. The later native handoff supersedes
the transport-array path measured below.

## Recorded CPU checkpoint

Release `6ae38d7a` on `f8feb3ff5`, six alternating pairs per workload,
five 2,000-build warmups and 2,000 measured builds per JVM, 2,048 faces per build:

| Workload | Current ms | Frozen ms | Current/Frozen |
|---|---:|---:|---:|
| Smooth packets | 234.272 | 430.817 | 0.544 |
| Overlapping packets | 300.963 | 616.584 | 0.488 |
| Scattered packets | 302.844 | 898.005 | 0.337 |
| Smooth with grass callbacks | 262.227 | 456.136 | 0.575 |
| Overlapping with grass callbacks | 324.842 | 662.464 | 0.490 |
| Scattered with grass callbacks | 328.676 | 934.345 | 0.352 |

All six scoped median gates pass with matching checksums and exact driver/native
pins. Full guards match 39,550 Current and 38,176 untouched Frozen source files,
compiled classes, fixtures and protected prompt before and after measurement.
The final full suites pass 2,607 Rust tests with three ignored and 1,953 Java
cases reported with two skipped, zero failures/errors (1,951 executed; 354 suite
executions). Six Java workers map the exact release. Public Frozen replay passes
with and without graphics auditing. Wiki validation passes.

An early transport control incorrectly expected positions for non-exact variants;
those variants intentionally use position zero. Correcting the control required
no encoder change. An opaque-grass native fixture also needed an opaque alpha.
Both failed runs are retained; final suites pass. These checks do not accept
whole-game performance or visual parity for this later release.
Documentation-only upstream `2b7311757` was integrated afterward; exact guards
confirm unchanged runtime source, native image, classes, fixtures and driver.

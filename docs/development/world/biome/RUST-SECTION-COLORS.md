# Rust-owned section color snapshots

Chunk rebuilding shares built-in biome lattice samples by resolver and world
coordinate within each section capture. Rust plans those coordinates, owns the
completed fields, and supplies vertex samples to the direct terrain mesher.
Java still evaluates resolvers, biome blending and contextual inputs against its
immutable `LevelSlice`. Canonical block storage now has a separate
[Rust live owner](../chunk/RUST-LIVE-SECTIONS.md); this color owner is not a
cross-section cache or a native biome-blending implementation.

## Ownership and compatibility

- A section needs coordinates `-1..17` in each axis for the existing `4×4×4`
  per-block domain. Rust requests only coordinates used by active blocks,
  separately for grass, foliage and dry foliage. Adjacent blocks in the same
  capture share lattice samples for the same resolver. The existing origin
  `blockTint` evaluation still runs for every active block, and enabled tint
  diagnostics may resample a lattice.
- Other provider paths, including remaining built-in callbacks and custom
  providers, retain their original per-block, Y/Z/X callback order and
  all 64 samples, including providers returning `-1` at the origin. Their
  temporary literal rows are captured separately; they are never deduplicated.
- The compact meshing header is version **4**, 136 bytes. Its final `u64` is a
  CPU color-owner identity. Production owners contain both shared fields and
  any copied literal-provider rows. A zero identity uses the old literal tensor
  only for replay/ABI fixtures. This does not change whole-frame render ABI 74.
- Construction is exclusive. Java fills native requested colors, then seals
  the owner before rendering. Decode validates completion, origin and active
  block order and holds an `Arc` until scanning finishes. Closing a lease
  rejects future decode but cannot invalidate an already decoded owner.
- At most 128 construction/ready leases remain in the registry. Decoded `Arc`
  owners can outlive lease removal, so this is not a cap on all live color memory.
  Each request is bounded by three `19³` fields and 4096 literal rows. Identities
  are never reused.
  Model generation is checked both before capture and again immediately before
  native mesh admission.
- Frozen's vertex domain and fixed-point interpolation are unchanged. Dry
  foliage remains a per-block tint. All GPU work still belongs to Rust/GAL.

Snapshots consisting of built-in biome sources and untinted states avoid
Java's one-MiB tensor. Literal
provider staging is allocated only when required and freed after Rust captures
it, before scanning. The direct native mesher
reads four field samples per vertex instead of reconstructing a 64-word row
per model record. Explicit diagnostics may read back or resample a lattice;
those records are not production rendering inputs.

## Work and verification

Source: [world color fields](https://github.com/HungLo2020/MattMC/tree/c9e2a71d415a7f6022e772ff0b8a2e01dd51d916/src/main/rust/world/level/biome/color_fields),
[Java semantic capture](https://github.com/HungLo2020/MattMC/blob/c9e2a71d415a7f6022e772ff0b8a2e01dd51d916/src/main/java/net/sodium/client/render/chunk/compile/tasks/NativeSectionColors.java),
[origin tint and diagnostic resampling](https://github.com/HungLo2020/MattMC/blob/a908f78cd909200f5f4f4424b124072cef0a17f6/src/main/java/net/sodium/client/render/chunk/compile/tasks/NativeSectionSnapshot.java),
[compact ingestion](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/chunk/meshing/section.rs).

```sh
CARGO_TARGET_DIR=build/rust/target-tests cargo test --manifest-path src/main/rust/Cargo.toml color_fields
CARGO_TARGET_DIR=build/rust/target-tests cargo test --manifest-path src/main/rust/Cargo.toml render::chunk::meshing
./gradlew -PmattmcRustProfile=release test -x testRustNative --tests '*NativeSectionColorsTest' --tests '*NativeSectionSnapshotTintTest'
```

Before this change, paired ordinary-flight profiles crossed seven chunk
columns with shaders/DH off. Java lattice extraction accounted for 159/383
snapshot CPU samples in Current (8-second diagnostic interval). Evidence:
`goal5/chunk-snapshot-flight-profile-20261009/`. Both profiler windows were
inside actual forward-key intervals; source/library/Frozen identity and
cleanup checks passed. These samples identify a target, not a proven FPS gain.

**Author-reported color checkpoint (`c9e2a71d`):** four core checks, 65 meshing checks and the full
native suite (2402 passed, three ignored) pass. Full Java passes 1732 tests
(two skips); release build and wiki check (2482 pages/43 indexes) pass.
`validation/native-world-color-fields-20261009/` passed all seven lifecycle
cases and reviewed vanilla/Iris+DH image pairs, with zero VUIDs. All 16
6,000-frame ABAB rows are clean. Vanilla still fails average FPS/p99 floors;
other modes pass this comparison, but DH repeats vary substantially.
Source/library/Frozen identity and owned-client cleanup passed; 25 generated
copies were retired. Measured SHA `6d637127` predates final input/lifetime
hardening: reject old headers before expanded reads, prevent negative tint-index
overflow, and retire literal staging before meshing. Final SHA `953ac5b4` passes
the full native suite (2405/three ignored) and 77 affected Java checks;
a fresh reviewed Iris+DH proof passes (mean RGB 3.746/4.307/3.993, zero VUIDs).
The Java generation recheck was added after profiling; 77 affected Java checks
pass again. The final reviewed admission proof passes (mean RGB
3.736/4.298/3.984, zero VUIDs, exact captured native identity, unchanged sources
and Frozen, no owned orphans). The [pinned color summary](https://github.com/HungLo2020/MattMC/blob/c9e2a71d415a7f6022e772ff0b8a2e01dd51d916/SUMMARY.md)
records that checkpoint; the root summary now describes a later workload.
This documentation review did not rerun these checks or inspect the unbundled
runtime receipts. An isolated throughput gain is not established.

If capture fails, check provider exceptions, stale/released identities and
model reload generation first. A failed construction must close its lease;
never render an unsealed field or silently substitute another world snapshot.

The author reports the repeated ordinary-flight profile passing in
`goal5/world-color-fields-flight-profile-20261009/` (Current) and
`goal5/world-color-fields-frozen-profile-v2-20261009/` (Frozen). Both eight-second
windows lie inside actual forward input; reviewed cameras reached the same
266/95/561 block and crossed seven chunk columns. Source/native/Frozen identity
and cleanup pass. Current tint preparation decreased from 159/13989 to
101/13892 samples, and snapshot preparation from 383 to 313 samples. These
single paired diagnostic windows do not establish exact per-frame cost or an
isolated FPS gain. The first Frozen repeat is retained as failed because its
later return-trip video failed timestamp validation.

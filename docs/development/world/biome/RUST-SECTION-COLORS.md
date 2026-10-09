# Rust-owned section color snapshots

Chunk rebuilding shares built-in biome color inputs by world coordinate. Rust
plans the coordinates, owns the completed fields, and supplies vertex samples
to the direct terrain mesher. Java still evaluates biome resolvers against its
immutable `LevelSlice`; loaded-world storage and biome blending are not yet
fully native.

## Ownership and compatibility

- A section needs coordinates `-1..17` in each axis for the existing `4×4×4`
  per-block domain. Rust requests only coordinates used by active blocks,
  separately for grass, foliage and dry foliage. Adjacent blocks share samples.
- Other provider paths, including remaining built-in callbacks and custom
  providers, retain their original per-block, Y/Z/X callback order and
  all 64 samples, including providers returning `-1` at the origin. Their
  temporary literal rows are captured separately; they are never deduplicated.
- The compact meshing header is version **4**, 136 bytes. Its final `u64` is a
  CPU color-owner identity. A zero identity uses the existing literal tensor
  for replay/ABI fixtures. This does not change whole-frame render ABI 74.
- Construction is exclusive. Java fills native requested colors, then seals
  the owner before rendering. Decode validates completion, origin and active
  block order and holds an `Arc` until scanning finishes. Closing a lease
  rejects future decode but cannot invalidate an already decoded owner.
- At most 128 construction/ready leases exist. Requests are bounded by three
  `19³` fields; literal rows by 4096 blocks. Identities are never reused.
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

Source: [world color fields](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/biome/color_fields),
[Java semantic capture](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/sodium/client/render/chunk/compile/tasks/NativeSectionColors.java),
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

**Current verification:** four core checks, 65 meshing checks and the full
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
and Frozen, no owned orphans). `SUMMARY.md` records those
scoped timings; an isolated throughput gain is not established.

If capture fails, check provider exceptions, stale/released identities and
model reload generation first. A failed construction must close its lease;
never render an unsealed field or silently substitute another world snapshot.

The repeated ordinary-flight profile passes in
`goal5/world-color-fields-flight-profile-20261009/` (Current) and
`goal5/world-color-fields-frozen-profile-v2-20261009/` (Frozen). Both eight-second
windows lie inside actual forward input; reviewed cameras reached the same
266/95/561 block and crossed seven chunk columns. Source/native/Frozen identity
and cleanup pass. Current tint preparation decreased from 159/13989 to
101/13892 samples, and snapshot preparation from 383 to 313 samples. These
single paired diagnostic windows do not establish exact per-frame cost or an
isolated FPS gain. The first Frozen repeat is retained as failed because its
later return-trip video failed timestamp validation.

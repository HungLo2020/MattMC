# Rust DH data-source sky light

> **Current state:** [Rust-only DH generation](RUST-DH-GENERATION.md) supersedes this page's
> descriptions of Java fallback, compatibility and decline routes, Java light
> storage and wrapper-replacement checks; those no longer exist. Details and
> historical results below remain as recorded.

`DhLightingEngine.bakeDataSourceSkyLight` lights a DH full-data source's
points before each save of generated data (`GeneratedFullDataSourceProvider`)
and after live chunk updates (`AbstractDhLevel`). It now runs as one Rust pass
over the source's primary column table:

- Rust: [`dh_columns/sky.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/chunk/dh_columns/sky.rs) (`mattmc_dh_source_bake_sky`)
- Java: [`NativeDhSkyLight`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativeDhSkyLight.java)

The Java bake used to write each lit point through a list `set` (a downcall
each), and created a Java reader for every column it visited. The native bake
writes the table directly. Java only refreshes the views of columns whose
readers had already escaped, as after native occlusion culling.

## Rules to preserve

The pass reproduces the Java bake exactly; the original stays as
`bakeDataSourceSkyLightInJava` for compatibility and as the test reference.

- Pass 1 walks z outer, x inner, each column top-down. An "air" point is one
  whose material opacity is 0 (not `isAir`). Values are re-read after every
  propagation, which may already have raised later points.
- Propagation follows Java's recursion exactly: neighbours in the order
  `-x, +x, -z, +z`, overlap tests with their `continue`/`break` rules, and
  `light - 1 > adjacent`. The depth is at most 15, because light drops by 1 at
  each step.
- Pass 2 walks columns in index order. Index 0 always takes the maximum. Each
  opaque point copies the light above it, less `opacity * height` when the
  point above is partially translucent.
- IDs use `ID_MASK` (`Integer.MAX_VALUE`), and the sky setter sign-extends like
  Java's `(long) light << 56`.
- Every non-empty column detaches before the first write, because Java's second
  pass writes index 0 of every non-empty column.

## When it declines

The bake declines before any change, and the Java bake runs, when:
- the source is a subclass, or `-Dmattmc.dh.javaSkyBake=true` is set;
- the mapping has no native material policy;
- a column is a plain Java list or aliased;
- any point's material is outside the mapping, which Java's wrapper lookup
  would reject.

A failed detach allocation reports the detached readers and throws
`OutOfMemoryError`, as occlusion culling does.

## Tests

```bash
cd src/main/rust && cargo test --locked --profile suite --lib dh_columns::sky
./gradlew -PmattmcRustProfile=release test --tests '*NativeDhSkyLightTest'
```

`NativeDhSkyLightTest` bakes identical copies of saved native chunk sources,
filled with random stacks across the whole grid, with both bakes at maximum
sky light 15, 0 and 9. It compares every point, and the escaped readers. It
compares against Current's Java bake, not an actual-Frozen recording.

# Shared property definitions

> Current implementation: Rust owns all 134 property
> declarations: 123 shared and 11 for integrated content. Registered block
> definitions, [physical settings](BLOCK-PHYSICS.md) and
> [intrinsic state rules](BLOCK-INTRINSICS.md) are native too; Java enum objects,
> codecs, behavior and world-dependent gameplay remain.

## Ownership

[`content/property/builtin.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/content/property/builtin.rs)
declares names and ordered boolean, integer and enum domains in native code.
The catalog builds once, independently of Java, rendering and world state.
`Builtin` identifies a declaration; a compact value index identifies its value.
Schemas are immutable and shared with the [block registry](RUST-BLOCK-REGISTRY.md).
Fluid construction uses the same boolean/integer domains directly.

Java's `BlockStateProperties` fields are temporary views loaded from bounded,
versioned, process-lifetime CPU tables. Java enum classes bind serialized native
values to existing gameplay objects; they do not select the domain or its order.
Hot property getters/codecs use projected data without native calls. A missing
definition or enum binding fails startup instead of substituting a Java domain.
Generic factories remain for synthetic/test definitions. All properties used
by the registered blocks have native declarations.

The [native block definition registry](BLOCK-DEFINITIONS.md) references schemas
by `Builtin` ID and the installed registry shares them by `Arc`. Neither
schemas nor block property lists return through Java. Declaration IDs are
bridge-local; saved block/state IDs and first-use `PropertyId`s are unchanged.
Generic Rust builders still accept explicit schemas for synthetic definitions.

## Changing or adding a property

1. Declare the native domain in `builtin.rs`; keep serialized names compatible.
2. Bind its Java compatibility field only while Java callers remain. Add enum
   bindings when needed; do not redeclare the domain in Java.
3. Test ordering, codecs, state transitions and actual world loading against
   Frozen. Equal property names do not imply equal identity or equal domains.

Boolean order is **true, false**. `FACING`, horizontal facing, hopper facing and
vertical direction have different explicit orders. Integer domains are
inclusive. Never normalize these orders: they determine default states and IDs.
Java numeric gameplay constants remain until their consumers migrate; they do
not construct shared property domains.

## Verification

```sh
CARGO_TARGET_DIR="$PWD/build/rust/target-tests" cargo test \
  --manifest-path src/main/rust/Cargo.toml --locked --lib content::
./gradlew -PmattmcRustProfile=release test -x testRustNative \
  --tests 'net.minecraft.world.level.block.state.properties.NativePropertyDefinitionsTest' \
  --tests 'net.minecraft.world.level.block.NativeBlockRegistryTest' \
  --tests 'net.minecraft.world.level.material.NativeFluidDefinitionsTest'
python3 DevUtils/tests/content/VerifyStateGraphs.py \
  --java-home /path/to/jdk-25 --output build/property-definitions-verification
```

Version 3 added comparisons of all 134 public property fields, serialized
domains, internal indices, parsing and codecs against Frozen's existing
classes. Current version 6 retains these and the earlier graph/fluid checks;
see [observer scope and integrity limits](STATE-GRAPHS.md#verification).
Bootstrap time and JVM main-thread allocation are separate measurements;
they do not establish full-client FPS or gameplay parity. Use the
[render verification workflows](../rendering/RENDER-VERIFICATION.md) for that
scope and retain open performance gaps while continuing native migration.

The following author-recorded results cover published property/fluid milestone
`df6c6dc77`, before the [block-definition follow-up](BLOCK-DEFINITIONS.md).
This documentation review did not rerun the suites or inspect the local receipts.

Five fresh-JVM pairs on 2026-10-08 match untouched Frozen for all 134
properties, 31,846 combined block/fluid states and 491,395 transitions,
with unchanged fluid/graph digests. Receipt: `build/property-definitions-master-verification-20261008/results.json`.
The release build and 17 native content tests pass. Bootstrap medians were
Current/Frozen 2.308/2.314 s; main-thread allocation 959.39/1068.05 MB. The
allocation reduction includes earlier graph/fluid migrations; this does not
isolate a property speedup. Full validation completed at
`artifacts/graphics-captures/validation/native-properties-master-20261008/`:
Java 1,699 passed/two skipped, Rust 2,356 passed/three ignored, seven lifecycle
scenarios and both reviewed vanilla/shaders+DH coast pairs passed, with zero
VUIDs. All 16 ABAB/6,000-frame FPS receipts were clean; 25 fixture copies retired.

The overall performance target remains unmet: vanilla/DH average FPS medians
were 6.3%/0.5% below Frozen, with worse p99 medians. Shaders/shaders+DH were
10.3%/9.1% above Frozen and their p99 medians passed in this run. These are
combined renderer measurements, not an isolated property optimization claim.
See the root `SUMMARY.md` for both repeats and continue native ownership work.

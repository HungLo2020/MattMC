# Java bridge

In the current migration, Java drives the native renderer through a C ABI: the exported
`mattmc_vulkanic_gal_*` functions, which
[`VulkanicGalBridge.java`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/vulkanic/bridge/VulkanicGalBridge.java)
binds by name with FFM downcalls. The Rust side is
[`render/bridge/`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/bridge) (see its README for the file map). It decodes
and copies what Java sends, calls the GAL or a renderer, and writes a status
back. It makes no rendering decisions.

This is a current compatibility boundary. The [completed runtime target](../PROJECT-ARCHITECTURE.md) has no Java dependency; [Goal 5 status](GOAL-5-STATUS.md) keeps current ownership and verified progress distinct.

## How the ABI stays in sync

- **Records are `#[repr(C)]` structs** in [`bridge/abi/`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/bridge/abi),
  grouped by family. Every request starts with an `FfiHeader` carrying the ABI
  version and the record's byte size; the bridge rejects unknown versions and
  any size that differs from the Rust layout.
- **Java does not hard-code layouts.** It asks Rust for each record's size,
  alignment and field offsets (`mattmc_vulkanic_gal_abi_struct_layout`, backed
  by the table in [`bridge/layout.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/bridge/layout.rs)) by struct id,
  then writes fields by index.
- **Versions:** Java's `ABI_VERSION` must equal Rust's `FFI_ABI_VERSION`
  ([`abi/version.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/bridge/abi/version.rs)), which also records what
  each version changed.

## Changing the ABI

1. Add or extend the record in the right `bridge/abi/` family file. Append
   fields; reordering or removing fields breaks every Java writer.
2. Add the record (or new fields) to the layout table in `bridge/layout.rs`,
   with a new struct id for a new record.
3. Mirror it in `VulkanicGalBridge.java`: the `Struct` enum entry (same id) and
   the code writing its fields.
4. Bump `FFI_ABI_VERSION` and Java's `ABI_VERSION` together, with a one-line
   note in `abi/version.rs`.
5. Bound the new input: payload limits live in `abi/limits.rs`, and per-request
   input-byte bounds in the checked readers in `bridge/memory.rs`.
6. Decode into renderer types in the matching module (`gui/`, `world/`, ...)
   and keep it decode-and-copy only; rendering decisions belong to renderers.

Never rename an exported function or change its signature without the matching
Java change: Java binds by name and fails at load if a symbol is missing.

ABI 67 appends `configured_shadow_distance_chunks` to the shader environment
record (struct 73, field 64). Java copies the CPU user setting without clamping
or interpreting negative values; Rust combines it with copied normal render
distance and pack policy. Rebuild the native library before using the new Java
writer. `WorldShaderEnvironmentEncodingTest` verifies the appended field and
adjacent fog range against the exported native layout, including dirty storage.
The layout-query result has a fixed 72-field capacity (312 bytes), mirrored by
Java's query allocation. Each table entry checks its field count at compile time;
the bridge regression queries all exported records, including the 65-field shader
environment. Update the native capacity and Java allocation together when growing it.

ABI 68 appends entity culling mode, flags, absolute double bounds, optional
leash-holder bounds and double camera origin to mesh instances (struct 69,
fields 31–35). Orb instances (struct 110, 248 bytes, fields 8–13) carry the same facts plus
an explicit shadow-only role. Absent metadata actively zeroes every appended
field. The decoder rejects unknown flags, malformed bounds, inconsistent holder
roles and metadata in first-person, terrain or block-entity domains. Nested Java
CPU extraction scopes retain immutable records; an inner null scope masks its
parent and cleanup restores it. Rust owns pack selection and geometry creation.
Rebuild before running `WorldEntityCullingEncodingTest`; its native checks cover
large camera origins, exact double values and dirty storage.

Typed orb placements name a boundary in the collected mesh stream. When the
shadow-only CPU capture removes foil or outline meshes, map those boundaries
through its kept-mesh prefix before the later source-admission mapping.
Preserve equal-boundary order, camera placements, culling facts and shadow roles;
appearance residency and published immutable records must remain unchanged.
See [the semantic collector](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/vulkanic/world/ExperienceOrbSemanticCollector.java).

## Rules the boundary tests enforce

- The bridge uses only the public GAL modules and is the only code that creates
  GALs (`VulkanicGal::create*`).
- `gui/` and `world/` only decode and copy records; they never build GAL
  resources or commands themselves.
- Record names extend shared families (GUI, world, material) rather than naming
  individual producers (no `Hotbar`, `BossBar`, ...).

## Verifying an ABI change

Run from the repository root; the subshell leaves the Gradle command there.

```sh
(cd src/main/rust && cargo test --release render::bridge)
./gradlew test --tests net.vulkanic.bridge.VulkanicGalBridgeAbiTest
```

For a refactor that should not change the ABI, compare the exported symbols of
the release library before and after:

```sh
nm -D --defined-only src/main/rust/target/release/libmattmc_rust.so | awk '{print $3}' | sort > symbols.txt
```

## Held-item light input

`RustGalWorldPrimitiveRenderer` applies each held stack's `BLOCK_STATE`
component to its block's default state before copying light emission into the
semantic frame. This matters for stateful items such as Light blocks and lit
Redstone Lamps. Empty and non-block stacks supply zero; each native-bound value
is clamped to 0–15. The retained `IrisItemLightProvider` applies the same component
without changing its existing return-value contract.

Java copies the two hands independently on each frame. Rust's active shader-pack
policy owns `oldHandLight`: its default/true value raises the main-hand value to
the stronger hand, while false preserves separate values. Do not apply this
composition in Java or cache emission by item type. Hand changes, component
changes, and shader-pack reloads must use current inputs.

Focused regression checks:

```sh
./gradlew test --tests net.vulkanic.world.HeldItemBlockStateLightTest
cd src/main/rust && cargo test --release held_light
```

These checks cover semantic values and native uniform preparation. They do not
replace the live shader-pack and image checks in [Render Verification](RENDER-VERIFICATION.md).

## Request memory budget

Checked FFI readers charge all nested reads against a 512 MiB budget per bridge
request. Counts and byte limits are checked before constructing foreign slices;
profiling does not dereference nested pointers ahead of decoding. The byte
counter measures validated reads (including repeated reads), not unique Java
allocation size. Java must still supply live memory for the duration of the call.
Failed resource creation batches release successful creates before reporting
failure, and result alignment/capacity are checked before execution.

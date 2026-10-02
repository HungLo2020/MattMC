# Java bridge

Java drives the native renderer through a C ABI: the exported
`mattmc_vulkanic_gal_*` functions, which
[`VulkanicGalBridge.java`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/vulkanic/bridge/VulkanicGalBridge.java)
binds by name with FFM downcalls. The Rust side is
[`render/bridge/`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/bridge) (see its README for the file map). It decodes
and copies what Java sends, calls the GAL or a renderer, and writes a status
back. It makes no rendering decisions.

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

## Rules the boundary tests enforce

- The bridge uses only the public GAL modules and is the only code that creates
  GALs (`VulkanicGal::create*`).
- `gui/` and `world/` only decode and copy records; they never build GAL
  resources or commands themselves.
- Record names extend shared families (GUI, world, material) rather than naming
  individual producers (no `Hotbar`, `BossBar`, ...).

## Verifying an ABI change

```sh
cd src/main/rust && cargo test --release render::bridge
./gradlew test --tests net.vulkanic.bridge.VulkanicGalBridgeAbiTest
```

For a refactor that should not change the ABI, compare the exported symbols of
the release library before and after:

```sh
nm -D --defined-only src/main/rust/target/release/libmattmc_rust.so | awk '{print $3}' | sort > symbols.txt
```

## Request memory budget

Checked FFI readers charge all nested reads against a 512 MiB budget per bridge
request. Counts and byte limits are checked before constructing foreign slices;
profiling does not dereference nested pointers ahead of decoding. The byte
counter measures validated reads (including repeated reads), not unique Java
allocation size. Java must still supply live memory for the duration of the call.
Failed resource creation batches release successful creates before reporting
failure, and result alignment/capacity are checked before execution.

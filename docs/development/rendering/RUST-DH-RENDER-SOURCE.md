# Rust DH render-source storage

Ordinary DH conversion writes render points into a Rust CPU owner. Reduction
and neighbor-box production borrow its stable point spans directly. Java does
not keep a mirrored point array or repack native points for these consumers.
Interior box builds pass one scoped source owner and four numeric windows;
Rust resolves and validates the neighbors without four Java span adapters.
Cross-source and heap neighbors retain the bounded span entry.
Material, variant and horizontal-uniformity planes allocate on their first
nonzero write; clearing a color-only source does not allocate semantic planes.

The owner lives in
[render/dh_columns/source.rs](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/dh_columns/source.rs).
[NativeDhRenderSource](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/vulkanic/world/NativeDhRenderSource.java)
is its scoped CPU adapter, exposed through `LongList` by `ColumnRenderSource`.
Recompile callers after changing this internal field type. This is CPU source
ownership; VulkanicGAL continues to own all GPU resources and presentation.

## Working on the boundary

- Point extents are fixed for a source. Conversion owns mutation; a synchronous
  build borrows points after conversion. Existing caller exclusion remains
  required for mutation and close; this adds no concurrent transaction.
- Closing a source invalidates every borrowed FFM segment before releasing its
  Rust storage. Escaped column reads fail safely. Close is idempotent, and a
  cleaner releases abandoned owners. Do not retain a raw address after the
  synchronous native call, or read a source after close.
- Conversion failures close their partially built source. Ordinary center and
  adjacent build sources close after synchronous packet production; packets
  retain their own storage. Asynchronous scheduling remains Java.
- Exact heap lists remain admitted for temporary columns and compatibility.
  Native and heap inputs/outputs may alias: reduction consumes the old input
  before writing, and equal-height bulk copies preserve overlap semantics.
  Custom lists/views retain their existing compatibility paths.
- Preserve Frozen's combined packed-height predicate in the nonvoid query.
  Equal nonzero height fields still pass that predicate. Zero or externally
  mutated compatibility dimensions retain the original traversal.
- Contextual color callbacks, sparse material/contributor bindings and source
  traversal remain Java. Moving storage does not complete their migration or
  establish bounded whole-session memory.

## Verification commands

Use JDK25 and the ordinary release native library:

```sh
./gradlew -PmattmcRustProfile=release test parityTest -x testRustNative
CARGO_TARGET_DIR=build/rust/target cargo test --profile suite --manifest-path src/main/rust/Cargo.toml --lib render::dh_columns:: -- --test-threads=1
python3 DevUtils/tests/rendering/VerifyDhRenderSource.py --saved-manifest /path/to/saved-dto-manifest.json --output build/dh-render-source-check --height 128
python3 DevUtils/RunWiki.py check
```

The manifest is the saved DTO corpus produced by the
[DH point-codec verifier](../world/chunk/RUST-DH-POINT-CODEC.md).
The render-source driver hydrates it outside timing, extracts actual column
geometry/light values, and supplies identical fixed colors on both sides.
Its oracle checks production column builds and merged geometry against
unchanged Frozen. Timing covers source construction, fill/reduction,
column builds/merging and close, with a minimal checksum. Full point hashing and
merged-geometry checks run separately outside timing. Contextual material/color resolution and GPU
rendering require separate runtime verification.

`--storage-only` isolates construction/fill/reduction/close. An optional
`--predecessor` directory contains pinned `classes/`, `native/` and `pins.json`
from the previous Current image. The driver verifies source, compiled-class,
native-image, corpus and protected-prompt hashes around the comparisons.
Thread allocation measurements cover Java heap allocation only; they do not
measure Rust allocation or retained host/GPU memory.

## CPU checkpoint: October12

Release `a9286e05` passes 2,728 Rust tests (3 ignored) and 2,046 Java/parity tests (2 skipped).
The 3,616 actual Frozen reducer cases replay with heap/native/mixed storage;
3,072 whole-box cases replay with heap and primary-source neighbors. Alias,
lazy-plane clearing, packed-height and close/lifetime controls pass.

Complete point and merged-geometry oracles match unchanged Frozen and preceding
Current for 24 saved geometry/light sources at height 128. Six interleaved JVMs
measure Current/predecessor/Frozen at 321.42/313.36/657.63 ms per full batch:
51.1% faster Frozen, but 2.6% slower predecessor. Java thread allocation is
37.6/315.4/156.2MB per batch, 88.1% lower predecessor. Native storage is excluded
from that allocation figure; this is not a retained-memory bound or FPS result.
Source/class/native/corpus/prompt guards pass.

The first storage prototype measured 365.46 ms. A 450-sample post-warmup JFR
located most sampled execution around box production, including shared-session
handling. Direct source/window consumption removes four per-box Java spans and
repeated source-scope acquisition; the revised measurements above retain the
remaining predecessor regression explicitly. Temporary profiler/controllers
remain generated artifacts, not runtime instrumentation.

Seven lifecycle scenarios and three settled Frozen/Current pairs pass on the
same release, with zero Vulkan validation messages. Thirteen original images
were reviewed; the seven hazy lifecycle views establish structural behavior
only. Settled pairs do not prove first-frame or first-turn temporal parity.
Matched gameplay timings and the retained DH startup RSS failure are recorded
in [the gameplay checkpoint](GAMEPLAY-PERFORMANCE.md#render-source-storage-checkpoint).
Receipts: `build/native-dh-render-source/`; runtime roots:
`artifacts/graphics-captures/validation/native-dh-render-source-{gate,pairs,perf}-20261012/`.

# Rust flat GUI item frames

**Implemented 2026-10-13. Scoped checks complete; shader p99 parity remains open.**

Cached flat GUI items use the existing Rust-owned immutable
item templates. Rust retains layer geometry, transforms, material membership
and ordinary/special foil selection. Each frame supplies one placement and foil
clock instead of reconstructing a Java batch for every layer. Successful
registration discards the Java layer geometry; Java keeps resource-publication
bindings and model/cache admission. Animated or uncached source geometry still
uses live semantic extraction. This does not migrate the complete GUI.

The owners are [GUI templates](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/guirender/mesh/template.rs)
and their [flat-item producer](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/vulkanic/gui/GuiFlatItemMeshCollector.java).
The existing template ABI and Rust/GAL resource and presenter ownership remain
unchanged. See [3D item templates](RUST-GUI-ITEM-TEMPLATES.md) for the shared
registry, memory budget and submission lifetime.

## Constraints

- One template contains a complete item, with consistent native raster layout
  and ordered material layers. Registration validates geometry before retaining it.
- Source geometry is copied once. Placement, GUI extent, clip, scheduler sequence,
  cache identity and foil clock remain frame facts. Special foil keeps its native
  decal-layout semantics and uses the current frame's clock.
- The bounded topology cache retains a native lease. Frame decoding acquires
  shared ownership; eviction/reload cannot invalidate an already decoded frame.
  Java model/texture callbacks retain their existing order.
- Retention-capacity rejection keeps that entry on the existing inline semantic
  path. Invalid inputs throw. No renderer or Java GPU fallback is introduced.
- Atlas publication must visit all retained template assets, including foil.
  Capture diagnostics must observe the template's foil facts.

## Verification

Fresh paired 30-second gameplay JFRs at `f505bbbd1` completed with
source/native/Frozen guards. The Current flat-item producer carries about
111 MB of sampled allocation weight, including 66 MB in instantiation and
26 MB in batch construction. These weights overlap and are not per-frame
rates or measured migration savings. The profiles are diagnostic runs, not
performance acceptance: `build/native-post-particle-profile/`. Two completed
world copies were retired; verified event-JSON compression reclaimed 630 MB,
with original JFRs retained.

The release build and 12 focused Java checks pass, including geometry retirement
and immutable frame facts. Four Rust ABI controls pass; the new one compares
576 complete expanded frames against the inline path across scales, standard
and special foil, placement and clipping. The initial Rust fixture incorrectly
carried block-only vertex metadata; its failed receipt remains, and the corrected
fixture passes without validator changes.

The full `check` task passes 2,791 Rust tests (three ignored) and 2,142 Java
tests (two skipped) on native `c10ca3c2`; receipt:
`build/native-flat-item-frame/full-check.json`. Documentation checks pass.

The actual cached producer benchmark passes source/class/native guards,
placement/foil/ownership checks and measured-JIT quietness. Three interleaved
JVMs per side/case use 256 warmup samples and eight measured samples each.
Each sample instantiates 128 frames of nine items:

| Case | Current / prior Java CPU ms | Current / prior Java heap bytes |
| --- | --- | --- |
| Plain, two layers | 0.0565 / 0.3553 | 488,448 / 2,386,944 |
| Standard foil, four layers | 0.0582 / 0.7271 | 534,528 / 4,626,432 |
| Special foil, four layers | 0.0582 / 0.8950 | 534,528 / 5,935,104 |

This isolates cached instantiation and placement consumption. Model resolution,
encoding, native expansion and GPU work are excluded. The prior Java producer
is an internal control, not Frozen acceptance or an isolated gameplay gain.
Receipt: `build/native-flat-item-frame/measurement-1/results.json`.

The first GUI capture was rejected because the driver selected slot 2; the
layered fixture requires the static slot 1. Its source/native guards passed,
and the rejected receipt and original images remain. The corrected driver
starts with GUI captures. Its second moving-foil report initially received a
manifest filename where a directory was required. Rechecking the same captured
inputs with the correct parent directory passes all eight predicted temporal
changes and matching deltas; original failed manifests are unchanged. Receipt:
`build/native-flat-item-frame/moving-foil-recheck.json`.

Settled vanilla and shader+DH pairs and two explicit
frame-completion modes passed before integration; they are scoped evidence.

Upstream `c357b63cd` (Rust-only DH section building) is integrated, preserving
the local implementation. Combined `check` passes 2,793 Rust tests (three ignored) and 2,138 Java
tests (two skipped) on native `e491d123`. Eight GUI cases pass: layers, alpha,
asymmetric transforms, patterned foil, two moving-foil phases, special foil
and resize. Two settled pairs, two completion modes and three lifecycle cases
(world unload/reload, resource reload and view-distance reduction) pass with
no reported GAL validation errors. Source/native guards pass; 19 Current native
identities and 23 originals were reviewed. The hazy lifecycle images support
structural presentation only. First-turn terrain, shader-entry and broad
visual/memory acceptance remain open. Receipts: `integrated-runtime.json`,
`integrated-remainder.json`, `moving-foil-recheck.json` and
`integrated-final-evidence.json` under `build/native-flat-item-frame/`.
Keep failures and superseded attempts distinct from accepted results.

```sh
./gradlew -PmattmcRustProfile=release buildRustNative classes
./gradlew -PmattmcRustProfile=release test --tests '*GuiFlatItemMeshCollectorTest' \
  --tests '*NativeGuiItemTemplateTest'
CARGO_TARGET_DIR=build/rust/target cargo test --locked \
  --manifest-path src/main/rust/Cargo.toml --profile suite --lib \
  gui_item_templates -- --test-threads=1
```

Reproduce the producer measurement after rebuilding Java and native code:

```sh
python3 DevUtils/tests/rendering/VerifyFlatItemFrames.py \
  --java-home /absolute/path/to/jdk-25 --output build/flat-item-new-attempt
```

## Initial performance finding

The pre-integration `c10ca3c2` matrix missed vanilla FPS and both shader p99
floors. Shader GUI preparation was close to the preceding particle checkpoint;
world/frame-coordination tails were elevated. This motivated the controlled
comparison below; aggregate phase timings alone do not establish causality.
Original receipts remain in `first-performance-comparison.json` and
`first-performance-diagnostics.json` under `build/native-flat-item-frame/`.

## Controlled gameplay diagnosis after integration

Eight clean 6,000-frame windows compare retained templates with the previous
inline CPU input path on the same integrated renderer. Two runs per side/mode
use retained/inline/inline/retained order; medians:

| Mode | Retained / inline FPS | Retained / inline p99 ms |
| --- | --- | --- |
| Vanilla | 1328.4 / 1287.3 | 3.220 / 3.526 |
| Shaders | 351.2 / 347.25 | 5.329 / 5.305 |

The earlier large slowdown does not reproduce in this bounded comparison;
shader p99 differs by 0.5%. This is an internal diagnostic, not Frozen
acceptance or proof of a gameplay gain. Source/native/class guards pass.
The temporary admission switch was removed, and original source and compiled
class hashes were restored before final captures. Saved workload inputs match;
live entity counts vary from 180 to 191. Receipt:
`build/native-flat-item-frame/ab-gameplay.json`.


## Integrated Frozen gameplay checkpoint

One clean 6,000-frame pair per mode on native `e491d123`, after restoring the
production source/classes and integrating the DH section-build migration:

| Mode | Current / Frozen FPS | Current / Frozen p99 ms |
| --- | --- | --- |
| Vanilla | 1273.1 / 1205.4 | 3.508 / 3.910 |
| Shaders | 350.2 / 307.7 | 10.358 / 7.998 |
| DH | 786.1 / 622.0 | 5.314 / 6.564 |
| Shaders + DH | 276.4 / 223.2 | 13.331 / 9.910 |

All FPS floors pass, but both shader p99 floors miss. Saved inputs and recorded
JVM options match; live entity counts are 185/190, 188/184, 165/167 and 166/165.
The controlled experiment above does not reproduce a large regression from
flat-item retention; it does not close the renderer's shader tail-latency gap.
Receipt: `build/native-flat-item-frame/integrated-performance-comparison.json`.

Completed runtime/control copies were retired after clients exited: 43 copied
worlds, about 4.6 GiB reclaimed. Original images, logs, failed receipts and
source worlds remain. Receipt: `build/native-flat-item-frame/retention.json`.

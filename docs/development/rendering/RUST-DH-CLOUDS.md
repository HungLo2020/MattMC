# Rust DH cloud preparation

> Cloud-owner implementation was source-inspected at [`64294324`](https://github.com/HungLo2020/MattMC/commit/642943247003d7d8d756a65180f0872b088c13f0).
> Runtime results below are author-recorded checkpoints; this documentation
> review did not rerun suites or inspect the unbundled receipts, profiles or images.

Built-in DH clouds now keep their motion, placement, culling and color-change
history in a native CPU owner. This removes the per-frame corner/vector graph
and keeps native rendering from receiving coordinates that Rust just produced.
Java still parses the texture into shared API boxes, queries world color and
preserves ordered pre-render, event and post-render callbacks. Performance
floors remain unmet and broad visual coverage is unfinished.

## Changing the producer

Start with [cloud CPU sources](https://github.com/HungLo2020/MattMC/tree/642943247003d7d8d756a65180f0872b088c13f0/src/main/rust/render/clouds)
and [CloudRenderHandler](https://github.com/HungLo2020/MattMC/blob/642943247003d7d8d756a65180f0872b088c13f0/src/main/java/com/seibel/distanthorizons/core/render/renderer/generic/CloudRenderHandler.java).
Preserve Frozen's millisecond clock, disabled intervals, wrapping integers,
float operation order, negative tile adjustment and approximate normalizer.
Corner coordinates add the tile width in float before widening to double.
The center nine tiles remain admitted without distance/look culling.

Only exact built-in groups and the built-in camera wrapper use this owner.
Custom factories/wrappers keep their original getter timing and Java CPU policy.
Each group calls the world-color provider only after active admission, retaining
shared-box updates and change notifications. Replaced pre-render callbacks
clear the native publication; API events that change the origin select the
ordinary semantic-origin path. Shading, light and cancellation remain ordered.

## Transfer and lifetime

ABI 75 introduced the 88-byte retained DH group instance layout; the current
whole-frame ABI is 79. Flag bit 1 selects a
CPU cloud owner address and an immutable pose generation; the three ordinary
origin lanes must be zero. The native decoder resolves that generation and
copies coordinates before constructing owned frame data. Ordinary API groups
carry their double origin and zero native fields. This changes neither GPU
ownership nor the GAL's handle model.

Each owner has a [fixed 16-pose ring](https://github.com/HungLo2020/MattMC/blob/642943247003d7d8d756a65180f0872b088c13f0/src/main/rust/render/clouds/ffi.rs#L6-L30). Missing/expired generations are rejected,
never replaced by a newer pose. Pinning the owner prevents release, but does
not stop later preparation from overwriting an older ring entry. Frame packing
captures the epoch when appending
an instance, keeps strong owner references across packed-list copies, and drops
them on clear. Ordinary queued decoding completes inside the FFI call; the older
pipelined route pins owners until join/context destruction. Rebuild Java and
the native library together after changing this layout.

Related tracking: [#777](https://github.com/HungLo2020/MattMC/issues/777#issuecomment-6089017758).

## Shader-pack cloud directives

The [current policy at `2003b118`](https://github.com/HungLo2020/MattMC/blob/2003b118dfac30ca501dc135b9de0e2d7e2567fb/src/main/rust/render/shaderpack/properties/clouds.rs)
reads the active source generation's preprocessed `shaders.properties` with its
option and environment defines. `dhClouds=off` suppresses DH clouds; when no
recognized DH directive is set, `clouds=off` also suppresses them. Explicit
`dhClouds=on` or `fancy` overrides that fallback and allows them. Recognized
`clouds=fast`/`fancy` allow clouds only when no recognized `dhClouds` directive
is set. Missing or unresolvable properties retain the
default of allowing DH clouds; this is a bounded parser, not a guarantee of
all Java Properties syntax or unknown-value behavior.

The previous special suppression of vanilla clouds merely because DH cloud
groups exist is removed; each producer uses its own effective setting. After each accepted source update, `RustGalFrameCoordinator` reads
the policy through `mattmc_vulkanic_gal_shader_pack_cloud_settings`:

- `clouds=off|fast|fancy` overrides the vanilla cloud option in
  `Options.getCloudsType` (render distance 4 or more), as Iris does.
- `GenericObjectRenderer` skips the `Clouds` group before submission when the
  accepted policy suppresses DH clouds, after the existing pre-render/event
  callbacks and before origin/box submission. This replaces
  the `c00b11c8` instance flag bit and admission filter.
- `LevelRenderer` no longer hides vanilla clouds while DH cloud groups exist.

Four Rust policy fixtures now inspect supported directives, explicit override,
recognized duplicate ordering, conditional `DISTANT_HORIZONS` and missing
properties, plus vanilla override encoding and packed settings. They do not
execute a live shader toggle, Java option/event integration, cloud image comparison or
asynchronous teardown. This documentation review inspected their source and
reran no Rust/Java or client workloads; the preceding hardware blocker remains
in the [current evidence checkpoint](GOAL-5-STATUS.md). The
[#745 source review](https://github.com/HungLo2020/MattMC/issues/745#issuecomment-6102233578)
records the earlier flag/filter design; the current getter/producer path
supersedes that implementation while broader DH visual/transition acceptance
remains open. Cached settings are read after source acceptance; source staging
and settings refresh are not claimed to be an atomic transition. The
[current policy review](https://github.com/HungLo2020/MattMC/issues/745#issuecomment-6102688591)
records the replacement implementation. The separate [group-ID admission gap](https://github.com/HungLo2020/MattMC/issues/820)
remains open; this policy change does not repair it.

## Verification

With JDK 25 selected:

```sh
./gradlew -PmattmcRustProfile=release test -x testRustNative \
  --tests net.vulkanic.world.NativeDhCloudGroupStateTest \
  --tests com.seibel.distanthorizons.core.render.renderer.generic.NativeCloudApiProjectionTest \
  --tests net.vulkanic.bridge.PackedDhGenericBoxesTest
CARGO_TARGET_DIR=build/rust/target-tests cargo test \
  --manifest-path src/main/rust/Cargo.toml -- --test-threads=4
python3 DevUtils/tests/rendering/RunValidation.py --label <new-label> --perf
```

The Java oracle retains Frozen's allocating vector calculation and exercises
histories, large/negative positions and numeric boundaries. Native consumer
tests exercise pose selection, stale rejection and independence after owner
release. These supplement real DH/cloud sessions; they do not prove an FPS gain
or unseen temporal parity. Prior profile allocation estimates are diagnostic
and belong to their recorded source versions.

The author reports that local release `b3c8dbe1` passes Rust 2,423 tests, the
eight focused Java cases,
seven lifecycle transitions and reviewed vanilla/Iris+DH settled coast pairs.
The full Java run found three stale ABI74 assertions; after correcting only
those expectations, all seven tests in the affected classes pass (1,755 cases
pass across the full run and rerun; two skipped). The original failed workflow
and correction receipt are retained under
`validation/native-dh-cloud-owner-final-20261009/`.
All 16 clean FPS runs complete but performance floors fail. A separate actual
DH profile observes native preparation and no legacy culling allocations in its
15-second allocation window; this is diagnostic, not an isolated speedup claim.

A supplemental high-altitude cloud view passes on Current with queuing disabled
(nine private cloud groups), but Frozen never reaches that fixture's readiness
gate. A lower look-up pair captures both and has RGB differences 2.275/1.749/1.013
with VUID 0; it fails the DH visible-extension coverage requirement. Natural
cloud motion is also not phase-matched. Neither supplemental pair establishes
DH/cloud visual acceptance; Frozen behavior remains unchanged.

The following [section-counter milestone](../world/chunk/RUST-SECTION-COUNTERS.md),
release `59171b74`, includes this cloud owner and passes the full Java suite,
lifecycle checks and reviewed coast pairs. Its 16 clean FPS runs still fail
vanilla/DH performance floors. Consult the [source-pinned summary](https://github.com/HungLo2020/MattMC/blob/642943247003d7d8d756a65180f0872b088c13f0/SUMMARY.md) for this checkpoint;
this does not turn the supplemental cloud views above into accepted parity.

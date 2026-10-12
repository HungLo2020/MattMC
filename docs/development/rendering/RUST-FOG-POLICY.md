# Rust fog policy and transition state

The verified checkpoint routes production fog color blending, void/effect
darkness, environment ranges, render-distance ranges, underwater biome-color
transitions and atmospheric rain smoothing through the existing Rust library.
Water transition history keeps its original process-wide lifetime; each
atmospheric environment owns an independent native rain history.

Implementation: [CPU policy](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/fog/policy.rs),
[transition state](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/fog/state.rs),
[scalar boundary](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/fog/ffi.rs)
and [Java semantic adapter](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/client/renderer/fog/NativeFog.java).
All seven built-in environment producers and `FogRenderer` consume the policy.
No new crate, GPU handle, framebuffer or presenter is introduced.

## Boundaries and compatibility

- Java retains environment priority/virtual dispatch, world/entity getters,
  custom biome/dimension providers, effect callbacks and mutable mod hooks.
  This checkpoint moves numerical policy and transition ownership, not complete
  environment extraction or world simulation.
- Keep callback order and committed field prefixes. Water initializes its
  environment range before querying player vision, applies vision before the
  biome callback, then publishes sky/cloud ranges. A thrown callback must leave
  the same partial `FogData` state. Atmospheric smoothing commits before cloud
  options are queried. Native calls contain no Java callbacks.
- Preserve float operation order, Java NaN/signed-zero min/max semantics,
  wrapping clocks and integer color conversion. Do not replace channel floor
  with rounding, clamp unusual color inputs, reset rain when another environment
  applies, or scope the shared water history to a world.
- Stateless calls borrow reusable thread-local CPU scratch during a critical
  downcall; Rust retains no pointer to that scratch. Stateful calls use ordinary
  downcalls. Automatic arenas retain/release each rain owner, and native locks
  end before Java callbacks resume.
- `FogData` and vector results remain compatibility projections for public
  callers and mod hooks. The whole-frame bridge still copies the final fog
  scalars; eliminating that last projection is separate migration work.

## Verification

Native `c17ef718` passes three focused Rust controls, eight Java checks and
full suites (2,706 Rust passed/three ignored; 2,028 executed Java/parity/two
skipped). The actual Frozen 11,778-case observer found 989 air-color packing
mismatches for negative or oversized channels; other recorded groups match.
The missing per-channel masks are corrected with a regression control.
Refreshed release `d64eb03a` matches all 11,778 actual Frozen cases with
source/class/native guards. Refreshed full suites pass 2,707 Rust tests
(three ignored) and 2,028 executed Java/parity tests (two skipped). A fixed-source
pipeline passes all seven lifecycles and both world comparisons, with zero
VUIDs. Four world originals were individually reviewed and nine Current
artifacts identify this exact native image. Cow body/shadow also passes; all
fourteen source originals were individually reviewed and ten Current artifacts
identify this image. The corrected rain invocation passes with four matching camera poses and a
bounded 6,000-frame readiness allowance on both clients. All twenty-two accepted
source originals were individually reviewed; eleven Current artifacts identify
this native image. Background rain/fog agree closely, but foreground rain coverage
and a Current-only chat overlay differ. The automated initial-image tolerance
passes; it does not establish whole HUD/foreground/weather parity. Earlier
camera-schedule, readiness and local argument-prefix failures are retained.
All twenty matched timings finish cleanly with identical effective JVM controls
and final source/native/Frozen/prompt/prepared-input guards. Vanilla p99 misses
3.15% and DH FPS misses 9.23%; remaining floors pass. Aggregate performance
remains failed, with no isolated cause/gain attributed. See the [complete matrix](GAMEPLAY-PERFORMANCE.md#local-fog-policy-checkpoint).

The observer uses controlled world/entity/hook getters and the public clock
provider. It records actual built-in producer results, float bits and callback/
failure prefixes; it does not establish gameplay or weather-transition parity.
Observer compile and JDK25/Byte Buddy setup failures are retained separately.
With a release library, JDK25 and unchanged sibling Frozen:

```sh
CARGO_TARGET_DIR=build/rust/target-tests cargo test --profile suite \
  --manifest-path src/main/rust/Cargo.toml render::fog -- --test-threads=1
./gradlew -PmattmcRustProfile=release buildRustNative classes
python3 DevUtils/tests/rendering/fog/VerifyFogPolicy.py \
  --java-home /path/to/jdk25 --output build/fog-reference
```

For real rain comparisons, use the same four-pose sweep and a readiness allowance
long enough for normal client rain interpolation on fast clients. The wait is
capture instrumentation; it does not change weather or fog behavior:

```sh
MATTMC_CAPTURE_RUN_SOURCE=/absolute/path/retained/vanilla-run \
python3 DevUtils/Audit/Capture.py --profile extended \
  --mode frozen-opengl-shaders-off --mode current-rust-vulkan-shaders-off \
  --workload-profile correctness --world-weather-scenario rain \
  --jvm-arg=-Dmattmc.dev.deterministicCameraCapture.settledReadyMaxWaitFrames=6000 \
  --artifact-root build/fog-rain-reference
```

Retain candidate receipts in `build/native-fog/`. CPU controls supplement
real camera/media/weather transitions and do not establish an FPS gain, a
first-turn terrain fix or long-session memory bounds.

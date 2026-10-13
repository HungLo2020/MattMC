# Rust shadow candidate storage

**Implemented and verified with upstream `1490391f9` on 2026-10-13.
This is scoped verification, not whole-renderer or whole-migration acceptance.**

Off-camera shader shadow candidates are captured directly into bounded Rust
CPU storage. Java supplies ordered gameplay callbacks and immutable bounds;
Rust owns the backing allocation, growth and admission output. Selection reads
the captured slots directly, without intermediate Java culling records or
Java-managed candidate arenas. The ordinary visible-entity render-state records
still use their existing bridge representation.

The producer is `EntityRenderer.captureEntityShadowCandidate`, called from
`LevelRenderer.extractRustShadowCandidates`. The confined Java view is
[`EntityShadowCandidates`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/vulkanic/bridge/EntityShadowCandidates.java);
its owner and bridge consumer are under
[`bridge/world/entity_shadow_query`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/render/bridge/world/entity_shadow_query).
This is CPU data ownership. Shader policy, GPU resources and presentation retain
their existing Rust/GAL ownership.

## Capture and lifetime

- Capture preserves passenger, renderer-bounds, readiness, hook and leash-holder
  callback ordering. It does not batch callbacks or reuse results across them.
- Each slot contains flags, prepared entity bounds and uninflated holder bounds.
  Camera state belongs to the selection query, not to each candidate.
- Native growth preserves earlier captures and caps storage at 65,536 entries.
  Java refreshes its borrowed views after growth, including allocation failure.
- Capture validates before advancing the producer count. A failed candidate
  leaves the completed prefix intact. Clear reuses allocation; absent holder
  fields are zeroed so a previous frame cannot leak into the new one.
- Query and producer run serially on the render thread. Closing the query
  invalidates its Java views and releases native storage. This storage never
  travels with an asynchronous GPU frame.
- Undecided admission keeps every candidate. A partially written result is not
  consumed. Full frame planning still applies shadow policy independently.

## Verification

```sh
# JDK 25; build before timing, and do not overlap timings with other workloads.
./gradlew -PmattmcRustProfile=release test \
  --tests '*EntityShadowCandidatesTest' --tests '*EntityShadowCaptureTest' \
  --tests '*WorldEntityCullingEncodingTest'
CARGO_TARGET_DIR=build/rust/target cargo test --locked \
  --manifest-path src/main/rust/Cargo.toml --profile suite --lib \
  entity_shadow_query -- --test-threads=1
python3 DevUtils/tests/rendering/VerifyShadowCandidates.py \
  --java-home /path/to/jdk25 --output build/shadow-candidates/measurement
```

The component driver compares all captured fields before timing production
against the prior Java record/buffer sequence. It measures 128 frames with
64 or 1,024 candidates, with and without leash holders, and retains source,
compiled-class and native-image guards. This internal transport control is
supplemental; unchanged Frozen OpenGL remains the gameplay and correctness
baseline. It does not measure total native memory or establish runtime parity.

Full checks pass 2,788 Rust tests (three ignored) and 2,137 Java tests (two
skipped), including callback mutation/failure order and the native entry point
against all 108 Frozen fixture decisions. A fresh actual Frozen bounds probe
reproduces those decisions with source/class guards. Scoped runtime results are below.
Preliminary measurements are retained under
`build/native-shadow-candidates/measurement-1/`; they include 5,120 bytes of
benchmark-only view creation per 128 captures. The final driver retains that
observation view instead of recreating it each frame.

## Integrated checkpoint (native `f29c8316`)

Upstream `1490391f9` removes DH Java generation fallbacks. Integration preserved
all 18 local paths without conflicts. The combined full checks pass the Rust
and Java counts above. Fresh DH generation matches actual unchanged Frozen
checksums over 16 recorded chunks in four JVMs; those short timings are diagnostic
only. Saved-chunk heightmap differences documented in
[Rust DH generation](../world/chunk/RUST-DH-GENERATION.md) are not exact parity.

`build/native-shadow-candidates/integrated/measurement/` passes captured-field
checks, source/class/native guards and 192 compiler-quiet samples across 24 JVMs.
Medians per 128 captures, compared with the previous Java transport:

| Candidates | CPU ms, Current / previous | Java heap bytes, Current / previous |
| --- | --- | --- |
| 64, no holders | 0.0804 / 0.1048 | 0 / 0 |
| 64, mixed holders | 0.1356 / 0.1463 | 524,288 / 704,512 |
| 1,024, no holders | 1.3674 / 2.4276 | 0 / 0 |
| 1,024, mixed holders | 2.1520 / 2.7689 | 8,388,608 / 11,190,272 |

CPU is 7–44% lower; mixed-holder Java allocation is 25–26% lower. These measure
capture/transport, not an isolated gameplay gain or a Frozen speed comparison.
Both paths still perform Java bounds inflation; gameplay callbacks and ordinary
render-state extraction remain unmigrated.

One fresh 6,000-frame Current/Frozen OpenGL pair per affected mode, with ordinary
model rigs enabled and no overlapping build or documentation checks:

| Mode | FPS, Current / Frozen | p99 ms, Current / Frozen |
| --- | --- | --- |
| Shaders | 357.0 / 312.6 | 5.098 / 7.935 |
| Shaders + DH | 282.8 / 225.8 | 5.889 / 9.223 |

Both scoped FPS/p99 floors pass. Saved inputs, configuration and live JVM flags
match; live entity counts differ: 181/183 with shaders and 165/167 with shaders+DH
(Current/Frozen). One pair does not isolate this migration's gameplay effect or
prove sustained performance. Vanilla modes retain their preceding checkpoint;
this candidate changes shader-only capture.

One settled shader+DH image pair and world unload/reload, resource reload and
view-distance decrease pass with zero validation errors, exceptions and panics.
All five originals, four Current presenter correlations and six native identities
were checked. The settled coast, underwater vegetation, distant islands and sky
align closely, with small foliage/water variation and HUD hidden. The lifecycle
images are heavily hazed and provide structural evidence only. This does not
close first-turn terrain gaps, the first shader world frame, broad entity-shadow
visuals or HUD acceptance.

Peak client RSS is 5.92/7.31 GiB with shaders and 7.73/7.51 GiB with shaders+DH
(Current/Frozen). Observations complete normally, but no GPU-memory metric or
long-session memory bound is established. Eight completed copied worlds were
retired after clients exited; original images and diagnostic receipts remain.

Evidence: `build/native-shadow-candidates/integrated/` contains full-check,
measurement, fresh-generation, runtime, gameplay-comparison, image-review,
memory-observations, final-integrity and retention receipts. Original captures:
`artifacts/graphics-captures/validation/native-shadow-candidates-integrated-20261013/`.

## Historical pre-integration checkpoint

Native `cd299da9` passed the same full-test counts and 108 Frozen bounds cases.
`measurement-2/` showed 12–43% less CPU and 25–26% less mixed-holder Java allocation
than prior transport. Two clean 6,000-frame pairs/mode gave Current/Frozen medians
348.75/314.65 FPS, p99 4.910/7.608 ms with shaders, and 281.30/226.40 FPS,
p99 5.867/9.394 ms with shaders+DH. These results predate upstream integration.

The initial shader pair is retained as diagnostic evidence and excluded from
those medians: a documentation check overlapped about two seconds of its Current
measurement. An additional matched pair replaced it. All ten timing runs and the
exclusion remain in `build/native-shadow-candidates/timing-exclusion.json` and
its neighboring runtime receipts. The settled pair and three lifecycles passed;
five originals/four presenter/nine native identities were checked. Fourteen
completed copied worlds were retired. The integrated checkpoint above replaces
these historical results as verification of the combined implementation.

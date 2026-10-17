# Underground shader lighting checks

As of October 4, 2026, the original Complementary archive still fails the buried
spectator comparison against Frozen Java OpenGL. Keep production fog and light
shafts enabled. The prior decision to ignore Frozen's look-down haze does not
automatically cover this underground case; that extension awaits the user.

## Prepare equivalent inputs

Use an owned copied world and save spectator mode **and the starting pose**
before either launch. Frozen's deterministic mode override updates local mode
only; a creative server save can leave its inside-block overlay active while
Current exposes the cave. Follow [the fixture procedure](TERRAIN-MOVEMENT-CHECKS.md)
and use `--pose 167.5,38,553.5,0,0` for this scene. Preserve the original save.
Do not alter a source world or Frozen to obtain matching screenshots.

Use the same immutable archive, camera, DH setting, frame cap and stationary
dwell through [the paired harness](RENDER-VERIFICATION.md#2-frozen-image-comparison).
This investigation used original pack SHA-256
`4420b62e09cb12cdb5dc62bf25aec8ed8748431f431b143eb00265b469ef9c27`,
DH off, cap 60 and 512 stationary frames. Verify the final staged archive hash:
the ordinary launcher can replace a manually supplied archive with its built-in
copy. Local-only spectator/creative comparisons and changed-archive attempts
are excluded from exact-input evidence.

Frozen smooths with elapsed wall time; Current's deterministic fixture can step
by 1/60 second. Matching frame dwell alone does not equalize their history. Use
[completed-frame uniform receipts and actual Frozen SDK inputs](RENDERDOC-INPUTS.md)
before attributing a lighting difference to fog policy. Latest receipt files
may already contain unload matrices. Inspect shadow extents and depth storage;
the complete map is 2048×2048 even when the screen is 1280×720.

## Check capture ownership

From the repository root, run:

```sh
CARGO_TARGET_DIR="$PWD/build/rust/target" cargo test \
  --manifest-path src/main/rust/Cargo.toml source_empty_shadows -- --test-threads=1
CARGO_TARGET_DIR="$PWD/build/rust/target" cargo test \
  --manifest-path src/main/rust/Cargo.toml capture -- --test-threads=1
./gradlew buildRustNative -PmattmcRustProfile=release
```

The first group checks empty shadow initialization, complete pack-map capture
and immutable scalar retention. The second checks capture admission, restore,
resource ownership and publication. These supplemental tests do not establish
image parity. Retained fullscreen blocks belong to the selected native capture;
later frames cannot overwrite them, and completed publication stamps the frame,
correlation and GAL submission. Rejected captures release their CPU data.

## Recorded results and limits

Receipts below live under ignored
`artifacts/graphics-captures/goal5/underground-light-shafts/`; keep those artifacts
when reproducing a result. Whole-image RGB errors use the unchanged threshold 6.

| Observation | Result | Receipt |
| --- | --- | --- |
| CPU camera-facing masks omitted source shadow faces | Two regressions, 76 shadow checks and full Rust 2,153/3 ignored pass; Frozen-only covered depth falls 251,375→22 | `shadow-facing-original-runtime-verification.json` |
| Original archive, equal cap/dwell | RGB 16.160/11.614/7.510 fails | `fog-clock-60fps-runtime-verification.json` |
| Original archive, saved underground start | RGB 20.566/15.041/10.029 fails; observer stops before captured frame 1395 | `buried-start-original-runtime-verification.json` |
| Volumetric light disabled in archived diagnostic copies; atmospheric fog enabled | RGB 1.924/1.710/2.157 passes; diagnostic only | `shadow-facing-no-volumetric-runtime-verification.json` |
| Immutable capture-owned uniform blocks | Five source checks, 33 capture checks and release pass; real Current frame 1067/submission 2056 retains eight stages with clean validation | `captured-uniform-runtime-verification.json` |
| Fresh original Frozen SDK frame 684, shader counter 583 | Settled fog/camera/sun/near/far agree; deferred 2260 and volumetric 2501 shadow depths unbound, shadow color bound | `buried-start-fog-input-triage.json` |
| Only volumetric depth reads/queried extent replaced by zero in archived diagnostic copies | RGB 4.354/4.105/4.341 passes; no production change | `null-volumetric-depth-runtime-verification.json` |

The shadow-facing fix preserves off-camera faces and supplements visible terrain
with only its omitted shadow ranges. It corrects missing geometry before GPU
rasterization; cull-off alone cannot restore those faces. The complete-map capture
fix retains the screen outputs' own extents. Neither correction establishes
renderer-wide flicker acceptance. Shadow coverage triage uses separate equivalent
frames, and Current still covers additional terrain; it is not map-exact or
final-image acceptance.

The fresh saved-cave observations have bit-equal fog color, `inDry=1`,
`inRainy=0`, wetness, camera, near/far and sun. Eye brightness differs by 6.4e-7
and smoothed delta by 0.000261 s; shadow projection matches exactly and model-view
differs by at most 1.2e-7. The saved start removes teleport as the sole cause.
Earlier forest-start smoothing differences remain historical observations.

Across separate runs, the zero-depth diagnostic changes Frozen from its
original by only 0.477/0.257/0.137; Current diagnostic versus Frozen original
is 4.546/4.168/4.328. Current original still differs by 20.162/14.770/9.853.
This supports unbound Frozen shadow sampling as a major cause of the haze
difference. Cross-run metrics are triage, and incomplete GL sampling is not a
portable contract. Preserve the failed original-pack verdict pending the user's
exception decision. Do not turn the diagnostic constants or a volumetric bypass
into production behavior.

Actual paired clients exit 0 with clean applicable validation and no orphans.
The fresh Frozen observer and SDK inspection succeed, but automatic harness
RenderDoc replay fails and its aggregate verdict remains failed. The first
attempt stopped at the disk reserve before launch and is excluded; one verified
duplicate asset cache was retired to restore the reserve while preserving
worlds, source and capture evidence. These captures establish no performance,
motion, broad parity or long-run resource acceptance.

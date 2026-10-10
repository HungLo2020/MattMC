# Render verification

What to run before calling a rendering change done. Frozen, the unmodified
Java reference checkout, is the correctness baseline: compare against it,
don't change it.

Large historical capture inputs may be stored as lossless `.gz` archives.
The lifecycle gate reads matching plain and gzip client logs directly; restore
the original file before running other tools that require its plain path;
see [capture storage and recovery](ARTIFACT-STORAGE.md).

Serialize experiments that share native/class outputs. Native builds now publish
libraries atomically to preserve existing mappings; that does not establish
equivalent binary identity for concurrent benchmarks. See
[native builds and running clients](../tooling/NATIVE-BUILDS.md).

On Linux, `cleanup_client_core_dumping=true` is native crash evidence, even if
the wrapper exits 143 and no `hs_err` is retained. The capture runner observes
the same process start identity during its existing termination grace; flight
and normalized artifacts reject this flag. For older runs, match system core
records to the isolated PID, executable/main class, copied working directory
and launch interval. A completed recording can still belong to a failed session.
Private Vulkan diagnostics use [best-effort console writes](VULKANIC-GAL.md)
so a closed launcher pipe cannot turn an otherwise valid submission into a
logging panic. Keep the overlap trace enabled when checking that failure;
turning it off would remove the triggering write.

Foil capture observers use one Rust whole-frame boundary for GUI, hand and
world samples, including reload-overlay frames that skip item collection.
Require matching observed frame IDs; do not relabel stale samples. Process
memory observation accepts both in-tree game copies and the Python runner's
exact marked-root `.tmp/<run-id>/game_dir_<run-id>` path. Fresh metadata,
isolated cwd and stable Java process/start identity remain mandatory;
external paths, wrong-run siblings and symlink escapes are rejected.

## 1. Tests

Run from the repository root; the subshell preserves the Rust directory configuration without changing the next command's working directory.

The [validation driver](#one-command-validation) combines the common rendering
checks with the limits below. The individual test commands are:

```sh
(cd src/main/rust && cargo test --locked --profile suite --lib -- --test-threads=4)  # Rust, including boundary tests
./gradlew -PmattmcRustProfile=release test -x testRustNative       # ordinary Java (excludes tagged parity)
./gradlew -PmattmcRustProfile=release parityTest -x testRustNative # large Java parity workloads
```

See [Running tests quickly](../tooling/TESTING.md) for the `suite` profile,
the `test`/`parityTest` split and fork settings. The validation driver
includes both Java tasks when `--all-java-tests` is selected; a plain `test`
result alone is no longer the full Java suite.
Plain `./gradlew test` first runs the whole Rust suite serially
(`testRustNative`, `--test-threads=1`, `suite` profile) in Gradle's own target
directory, then can reuse its passing stamp while declared inputs/output are
unchanged. A reused stamp is not a fresh native test run; force a rerun after
relevant driver/environment changes. An unfiltered `test` excludes large
`@Tag("parity")` classes; run `parityTest` as well, or `check`, for both Java suites.
Use `-x testRustNative` after separately completing the intended Rust checks;
the driver's `--lib` command is narrower than Gradle's unfiltered Cargo command. `-PmattmcRustProfile=release` makes the Java tests load the release
library the clients use, instead of building a debug one. Parallel Rust runs
can race on native driver state (OpenAL/EGL/Vulkan); preserve the initial
failure and investigate it with serial repeats. A serial pass is useful evidence
but does not establish that the original failure was harmless.

The architecture boundary tests run with the Rust tests; see
[Render Architecture](RENDER-ARCHITECTURE.md). The Java test task sets
`net.bytebuddy.experimental` so Mockito 5.8's bundled Byte Buddy can mock
game classes on JDK 25. The author reports the former 11 mocking failures
resolved and two stale atlas/shield expectations corrected at `7f256b53`.
Do not classify a new mocking failure as an accepted baseline automatically;
check the effective JVM, Byte Buddy configuration and actual failure.

### One-command validation

The [fog/cache milestone](../world/biome/RUST-LIVE-BIOMES.md)
adds canonical native sky/fog sampling and generation-validated result reuse.
The author reports release `f449557e` passing both Java tasks (1,818 tests/two
skips), Rust (2,456 passes/three ignored), seven lifecycle cases and manually
reviewed vanilla/Iris+DH settled diagnostic pairs with DH coverage. All sixteen
ABAB/6,000-frame runs are clean, but vanilla p99 **fails** at 3.394 ms Current
versus 3.322 ms Frozen. See [the full recorded matrix](GOAL-5-STATUS.md#october-9-live-fog-and-validated-color-reuse-summary).
These results predate the later recorder, allocation and lazy-mesh source
changes; they are not full current-head acceptance. Broad gameplay, temporal
rendering and long-session memory acceptance remain open.

The preceding sky-only milestone retains its own evidence: range-corrected
`c3aa5fed` passed both Java tasks (1,812 tests/two skips), Rust (2,455 passes/three
ignored), seven lifecycle cases and reviewed settled pairs, without an FPS
repeat. Its earlier `bc2207fb` comparison failed vanilla p99. Keep these build
identities separate from the later fog/cache results.

For hand-played diagnosis, [session recording](SESSION-RECORDING.md) captures
Current only, with observer overhead. Neither a recording nor the later
allocation/mesh commit's component measurements replace a matched Frozen
performance comparison. Two independently run synthetic recorder-summary tests
cover tooling only; they do not validate client recording or live JFR data.

The preceding author-recorded
[terrain-light matrix](GOAL-5-STATUS.md#october-9-bulk-terrain-light-and-ordinary-gameplay-summary),
release `d9d1a9d6`, reports Java/Rust 1,807/2,443 tests (two Java skips and three
Rust ignored), seven lifecycle cases and reviewed diagnostic compatibility
pairs, but an overall **performance FAIL** on vanilla, shaders and DH p99.
Those settled images use scalar diagnostic lighting; they do not prove
bulk-path pixels. The separate ordinary comparison leaves entry/travel
performance open and stops after about 16.45 blocks at terrain.
The later [test/tooling commit](https://github.com/HungLo2020/MattMC/commit/f86206767dadde696adfed4e04c5ee97cd0d0885)
changes suite selection, profiles and fixture work, with combined Java checks
still in progress at publication. Its source changes and author-recorded
wiki/Rust checks do not provide a newer complete runtime acceptance matrix.
The earlier [live-light matrix](GOAL-5-STATUS.md#october-9-live-light-and-ordinary-gameplay-summary)
retains its own source and workload scope.
Earlier [item-input](GOAL-5-STATUS.md#october-9-native-world-and-hand-input-summary)
and [generation-handoff](GOAL-5-STATUS.md#october-9-native-generation-handoff-summary)
workflows retain their own historical results.
Keep author-recorded runtime checks separate from source inspection and focused
Python tooling verification; this documentation review did not rerun those
clients or Java/Rust suites or inspect their unbundled runtime artifacts.

```sh
python3 DevUtils/tests/rendering/RunValidation.py --label <new-label> [--perf]
```

The [current driver](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/rendering/RunValidation.py)
orchestrates a bounded set of tests and workloads:

1. Java tests in `net.vulkanic.*`, `net.sodium.*`, `com.seibel.*` and
   `net.minecraft.client.dev.*` use `-PmattmcRustProfile=release` and skip
   Gradle's serial `testRustNative` dependency. Repeat `--java-test <pattern>`
   to change the filters; explicit filters can select matching parity-tagged
   classes. `--all-java-tests` removes the filters and invokes both `test` and
   `parityTest`, retaining their performance/benchmark class exclusions.
   An unfiltered `test` alone excludes `@Tag("parity")` classes.
   `--skip java-tests` still runs
   `buildRustNative` and `classes` with the release profile.
2. `cargo test --locked --profile suite --lib` runs with four threads by default and
   `ALSOFT_DRIVERS=null`; this is not a `--release` test command. Named failures
   parsed from libtest output are rerun serially and rerun successes are labeled
   flaky. The Rust checks, followed by the wiki check, run in a background
   thread while the [lifecycle gate](#lifecycle-gate) and two settled Frozen
   parity pairs run sequentially. Iris+DH enables generic DH rendering; the
   other pair is vanilla. Native-driver tests can overlap those correctness
   captures, so this is not a claim of exclusive GPU use.
3. The driver joins its background thread before moving-camera FPS. By default
   it requests one 1,800-frame current-client run in each of four modes.
   `--perf` instead requests current/Frozen/current/Frozen (ABAB), two runs per
   side and mode with 6,000 measured frames each. `--perf-repeats` changes the
   repetitions (at least two per side). Both protocols request 360 settle and
   240 warm-up frames. `--perf` requires all four modes to meet Frozen's median
   average FPS across equal repeat counts and not worsen its median of run p99
   frame times. These are medians of per-side runs, not median paired deltas or
   pooled-frame tails. There is no noise allowance in this numeric gate; a
   passing gate still needs the workload and variability checks below. Without
   `--perf`, the FPS step checks only current-client health.

The Java native-library build and current-client capture/FPS commands request
the release profile, with line-table debug information and stripping disabled
in the environment. Preserve effective hashes and settings; these do not make
the separately compiled Rust test binary a release binary. The four modes are vanilla, shaders, vanilla+DH and shaders+DH.
`--skip` accepts `java-tests`, `rust-tests`, `wiki`, `gate`, `parity` or `fps`
and can be repeated. A subset run verifies only its executed steps.

Output goes to `artifacts/graphics-captures/validation/<label>/` (existing
labels are refused): `summary.md`, `summary.json`, step logs and timings.
The validation and feature-parity drivers also retire completed generated
fixtures and superseded marked invocations. The repair for
[#823 is closed after focused verification](https://github.com/HungLo2020/MattMC/issues/823#issuecomment-6073221178):
parent retirement now rechecks workspace eligibility and preserves a parent
with any retained workspace. This does not replace backups or establish live
process race freedom. Keep required sources outside managed outputs, and pin
acceptance evidence or unresolved investigations with a root `.keep`; see
[the separate cleanup passes and pin limits](ARTIFACT-STORAGE.md#verification-driver-retention).

All four input paths must exist even when associated steps are skipped. The
defaults, relative to this checkout, are:

- `--run-source`: `artifacts/graphics-captures/goal5/terrain-look-direction/cold-source/run`
- `--vanilla-run-source`: `artifacts/graphics-captures/goal5/shadow-orb-ordering-fix/copied-source/run`
- `--shader-pack`: `artifacts/graphics-captures/goal5/rejected-source-shadow-depth/original-pack/ComplementaryHungLoIfied.zip`
- Frozen: the fixed sibling checkout `../MattMC_JavaPerfTesting/MattMC`

The first and third also accept `MATTMC_CAPTURE_RUN_SOURCE` and
`MATTMC_CAPTURE_SHADER_PACK_SOURCE`. Override missing archived inputs with
retained equivalent fixtures; existence checks do not prove equivalence.
Run from the repository root with Java, Rust and capture prerequisites ready.
The default save name is `Origin`. For another copied save, set
`MATTMC_CAPTURE_WORLD` to its folder name under the input run's `saves/`, for
example `MATTMC_CAPTURE_WORLD='New World' python3 DevUtils/tests/rendering/RunValidation.py ...`.
Both clients use that selected save; generic loading and lifecycle readiness
do not require the historical `Origin` world. Inspect the parity images when
changing the source save or camera pose.
Repeat `--jvm-arg=<option>` for gate, parity and current-client FPS options;
these are not forwarded into Frozen's gameplay benchmark.

The driver refuses client JVMs found at startup through Linux `/proc` whose
process name is `java` and command contains `KnotClient` or `devlaunchinjector`.
After each capture it sends `SIGKILL` only to those matches whose working
directory lies inside this invocation's output tree, rechecking process start
identity and using pidfds where available. A successful signal increments
`owned_orphans` and fails that step; this count does not prove all possible
orphans were discovered or that their exit was observed. Missing `/proc` or
unreadable process records limit discovery. Keep runs isolated and inspect the
actual client exit/core receipts as well as the summary.

Treat the summary as an index into evidence, not an all-checks certificate:

- Every requested step must be present and explicitly pass; an empty aggregate
  fails. Rust/wiki background exceptions become failed results, and missing
  requested results also fail. The combined driver requires all seven lifecycle
  scenario names and each scenario's `passed: true`. This repairs the prior
  background-`OSError` false PASS described in
  [#822](https://github.com/HungLo2020/MattMC/issues/822); it does not strengthen
  every underlying lifecycle receipt. A foreground launch or malformed-input
  exception can still abort before a final summary is written.
- FPS health requires explicit zero VUIDs, successful capture exit, complete
  publishable/crash-free/device-loss-free sampler receipts, exact measured-frame
  count, valid positive timings and no orphan/RSS guard. Missing health fields
  fail for these required fields. The separate log scan still accepts absent
  matching logs and absent terrain-failure counters; inspect retained logs and
  actual receipts alongside the [performance A/B](#4-performance-ab) equivalence
  checks. Health fields do not prove workload equivalence.
  The log scan records raw exception mentions separately. It classifies only
  the exact server INFO `ClosedChannelException` disconnect after the client's
  INFO `Stopping!` marker as an orderly shutdown disconnect. Earlier disconnects,
  stack traces, other exception types and error-level messages still fail; all
  completion and timing requirements remain in force. Preserve failed historical
  receipts and run a fresh comparison after fixing a classification error.
- The parity reader takes the first pair from the lexically last matching
  report, requiring report success, three finite nonnegative RGB errors and
  nonempty current-client VUID records containing explicit integer zeroes.
  Iris+DH also requires a passing DH extension. It neither recomputes the
  image threshold nor visually inspects PNGs. Review the listed side-by-side
  images (water, DH, sky and clouds), effective mode, frame correlation and
  requested extension. Gate limits remain below.

The implementation author reports a default run taking 14m20 (about 14½
minutes) on an RTX 2070 desktop: Java tests about 17 s, gate 7 min, parity
4½ min, FPS 2½ min, with Rust (about 3 min) and wiki work overlapping the gate.
The earlier manual workflow reportedly took about 35 minutes. These are dated
workflow observations, not independently reproduced timings or guarantees.
The Python fixtures inspect command generation, ABAB ordering, named-failure
parsing, artifact readers, mocked orchestration and owned-process cleanup.
At `97e30922`, an independent isolated review passed all 11 supplied driver
tests and nine additional mocked orchestration cases, including Rust/wiki
launch errors, artifact parsing failure, nonzero Rust exit, wiki timeout,
interrupted missing results, and skip combinations. These checks did not run
Java/Rust suites, clients, live benchmarks or production cleanup. See the
[integration records](#october-7-integration-batch-checks) for separate historical
author reports.

### Native frame, map and world-input evidence

Use the [item-input workload record](GOAL-5-STATUS.md#october-9-native-world-and-hand-input-summary)
for that measured release and subsequent observer corrections. The
focused guides keep earlier evidence scopes:

- [Map checks](../game-model/MAP-COLORS.md) cover indexed conversion, explicit map origins/materials and framed-map crops. The [Java staging test](https://github.com/HungLo2020/MattMC/blob/84016f210afdf7d5a8c6a61f9440e8f304f6aa74/src/test/java/net/vulkanic/gui/NativeMapImagesTest.java) checks GUI staging removal on reset and closes the manager in cleanup; it does not verify all world PNG cache closure. The failed original workflow and fresh shader+DH health repeat remain distinct author reports
- [DH frame ownership](RETAINED-SCENE.md#native-dh-visibility-frame-ownership) checks exact identity/lifecycle/count/decision rejection and immutable-owner survival. The ledger's queued-owner fixture does not execute the actual queue. The full sixteen-row ABAB comparison predates native late-input hardening; final-release Iris+DH and 60,000-frame diagnostic profiles have separate scope
- [Color fields](../world/biome/RUST-SECTION-COLORS.md#work-and-verification) retain the earlier full-suite/performance window, later native input/lifetime hardening, and final Java generation recheck with affected tests and fresh admission proof. Do not attribute every earlier benchmark to the final hardened source
- [Rebuild snapshots](../world/chunk/RUST-SECTION-SNAPSHOTS.md#verification), [live storage](../world/chunk/RUST-LIVE-SECTIONS.md#verification) and [stage handoff](../world/levelgen/RUST-STAGE-HANDOFF.md#verification-and-profiling) have separate alias, callback, view-lifetime, halo and transfer tests. Historical palette/worldgen helper drivers pin earlier owners and need integration updates before they can validate this boundary

All results above are author-recorded unless explicitly stated otherwise. This
review inspected committed source/text, not unbundled receipts, images or
profiles, and ran no runtime suites. Sampled allocation and CPU stacks identify
components; they do not establish isolated FPS improvements or long-run bounds.

### Native counters, cloud and item-pose evidence

Follow [section counters](../world/chunk/RUST-SECTION-COUNTERS.md),
[DH clouds](RUST-DH-CLOUDS.md) and [item layers](RUST-ITEM-LAYERS.md) for their
admission, callback and CPU-owner contracts. Per-section transactions do not
establish whole-chunk rollback. CPU pose equality and retained-owner tests do
not establish every world/hand/GUI image or queued failure path. Keep source
inspection, reported suites, ordinary captures and performance as separate gates.

At [`64294324`](https://github.com/HungLo2020/MattMC/blob/642943247003d7d8d756a65180f0872b088c13f0/PROGRESS.md),
the author records 12,000 exact Frozen/current CPU pose cases, full suites,
seven lifecycle cases and reviewed coast comparisons. The full ABAB matrix
belongs to release `0d54a098`; later GUI/hand reload observers and managed
memory-path corrections have 184 affected Java checks and a fresh strict
held-clock foil receipt. The latter reports short-session memory only. Ground
pairs still reject fixed fixture probes despite close live-Frozen agreement;
retain that failure rather than calling the fixture accepted.

The coordinated maintenance review independently ran eight exact-source
[Python memory-observer tests](https://github.com/HungLo2020/MattMC/blob/642943247003d7d8d756a65180f0872b088c13f0/DevUtils/Audit/test_client_memory.py).
They use synthetic process and temporary-directory fixtures, including exact
managed-run recognition and wrong-run/external/symlink rejection. No client,
Java/Rust suite, capture or benchmark was run by that review. These tests do
not convert author-recorded runtime or cleanup results into independent passes.

### Sound, offset and family evidence

The [content observer at `48a6e051`](https://github.com/HungLo2020/MattMC/blob/48a6e051ecd8bc322fb41118004edb3f13b41932/DevUtils/tests/content/VerifyStateGraphs.py)
uses schema v8 and compares eight semantic digests, adding sound/material and
family declarations to its earlier graph, fluid, property, block, physical and
intrinsic checks. It requires a separate Frozen checkout with compiled main
classes and at least three alternating fresh-JVM pairs. Sound coverage includes
event identities/ranges, profiles, instruments, state bindings and finite offset
samples; family coverage includes block-set/wood definitions, aliases, codecs
and per-block bindings. Source/native hashes, observer identity and Frozen Git
identity are checked around the run. Frozen Git identity does not authenticate
its prebuilt classes, and the bootstrap timing/allocation report has no
performance acceptance threshold. Main-thread Java allocation is not total
process or native memory.

These are definition and fresh-JVM bootstrap checks. They do not establish
sound playback, interactions or tick scheduling, save lifecycle, moving-frame
parity or full-client FPS. The author's five-pair v7/v8 reports and full
workflows are recorded separately in the [sound/offset](GOAL-5-STATUS.md#october-8-native-sound-and-offsets-summary)
and [family](GOAL-5-STATUS.md#october-8-native-block-families-summary) summaries;
matching digests do not turn their failed performance verdicts into passes.

### Feature fixture parity

Feature fixtures beyond the coast poses:

```sh
python3 DevUtils/tests/rendering/RunFeatureParity.py --label <new-label>
```

The [feature driver at `97e30922`](https://github.com/HungLo2020/MattMC/blob/97e3092269ed29854c8175a480a819fb1896c311/DevUtils/tests/rendering/RunFeatureParity.py)
defaults to ten Current/Frozen scenarios: `chest`, `chest-shaders`, `bed`,
`oak-sign`, `banner`, `zombie-armor-foil`, `held-trident-foil`, `held-shield`,
`held-shield-foil` and `empty-hand`. Only `chest-shaders` enables shaders;
this is not a shader-on/off matrix for every feature. Repeat
`--scenario <name>` to request a subset.

The current driver also includes `framed-map`, `framed-map-rotated`,
`framed-map-decorated` and `framed-map-shaders` (four additional scenarios).
They exercise the real map asset/render paths with Frozen's existing fixture;
its green/gray checkerboard is not a red/blue channel-order test. Keep the
[complete map palette and GPU regressions](../game-model/MAP-COLORS.md) alongside
these captures. Held-map coverage remains separate.
Framed-map source verification must exercise the native glyph writer and its
local map texture, with canonical ordinary/glow-frame identities. A generic
particle/textured pass cannot prove glyph-family parity. Keep crop comparisons
even when a whole-image average passes: a small incorrectly lit map can be
hidden by otherwise similar terrain pixels.

Shader-enabled feature rows request `--rust-selected-source-execution`, which
waits for and correlates Rust's final shader output at every pose. External
window samples without that requirement can capture a provisional image and
do not establish final shader-path parity. The option is scoped to Current;
Frozen keeps its OpenGL shader path.
Source mesh samples prioritize model/moving producers within the existing
16-record bound; aggregate counts still cover every source mesh. This prevents
terrain volume from hiding a required producer identity without weakening the
matching or completed-execution checks.
The attachment's entity-mesh-only requirement excludes item-frame fixtures:
their backing is opaque geometry. Exact captured-frame producer matching still
requires the backing identity and map/item content; terrain alone cannot pass.
Selected-source item frames retain five separate captures at the fixture camera
pose. Each must have its own completed producer submission; the single-pose
entity-fixture shortcut must not remove this temporal coverage.

Item-frame crop evidence must include saved pixels and the exact projected
viewport rectangle, correlated with completed backing/content submissions for
every captured frame. Structural execution alone cannot satisfy the crop gate.
The general model-workload gate accepts this completed typed item-frame proof:
the backing uses block-model receipts and map/item content has its own receipts.
Requiring unrelated ModelPart records can reject a correctly captured source
frame; accepting a player's hand instead can conceal missing fixture work.
Other model families keep their existing ModelPart requirements.
`test_item_frame_capture_crops.py` checks that missing images, stale or duplicate
map execution, wrong generations/submissions/map identities, unreadable PNGs
and offscreen bounds reject that evidence.
Frozen producer admission also recognizes rotated/decorated item-frame variants
through their typed emission checks; missing map content, wrong rotation or
incorrect decoration identity must still reject the pair.

To run just map fixtures:

```sh
python3 DevUtils/tests/rendering/RunFeatureParity.py --label <new-map-label> \
  --scenario framed-map --scenario framed-map-rotated \
  --scenario framed-map-decorated --scenario framed-map-shaders
```

`--repo-root`, `--run-source`, `--shader-pack` and `--frozen-repo` must all exist,
including the shader pack for shaders-off subsets. They default to the script's
checkout, its `run/`, `run/shaderpacks/ComplementaryHungLoIfied.zip`, and the
same Frozen sibling respectively; the source/pack environment overrides above
also apply. The driver builds release native code and Java classes in the
selected checkout before captures. Outputs remain in the **script's checkout**
under `artifacts/graphics-captures/feature-parity/<label>/`, even with
`--repo-root` pointing elsewhere. Use a fresh label. Results are `summary.json`,
`build.log`, scenario logs and nested capture evidence; an early build failure
returns without the final summary.

Every requested scenario must be present, exit zero, report manifest success,
have three finite nonnegative RGB errors and a passing
`cross_repository_visual_parity` status. The driver relies on the capture
manifest's parity decision; it does not independently recompute the threshold
or require every optional crop, foil or equipment subreport. Inspect those
reports and images for the feature being changed. `--compare <prior-label>`
additionally requires requested scenarios in the baseline, rejects lost report
coverage or previously passing status, and flags any RGB error channel more
than 1.0 above baseline. Improvements do not fail, but unchanged historical
failures still fail absolute parity. A comparison baseline must remain available
beside the new output, with its `summary.json` intact.

Feature capture timeouts set exit 124, but manifest parsing occurs before owned
client cleanup and final summary writing. An independent mocked timeout with a
truncated manifest raised `JSONDecodeError` before either occurred; treat a
missing summary as incomplete evidence, not a pass, and check for owned clients
after such an abort. The startup client check and post-capture cleanup have the
same `/proc` limits described above; a signaled owned client rejects its
scenario. All six Python feature fixtures passed independently, covering
synthetic summary/status and comparison behavior, not live feature captures.
Incomplete feature coverage is not full rendering acceptance. Both drivers
retire finished fixture copies and superseded marked invocations; see
[capture storage](ARTIFACT-STORAGE.md) for pins, destructive retirement and
recovery limits before running either driver.

### Lifecycle gate

World unload/reload (save and quit), resource reload, resize and view-distance
changes cross the pipelined Java/Rust frame boundary: a queued frame can
complete after the state it was built from is gone, and runtime-owned
resources can still be bound by cached sets. Run the gate with every rendering
batch, next to Frozen parity:

```sh
python3 DevUtils/tests/rendering/RunLifecycleGate.py --label <label>
```

It runs each transition scenario on the current client with Iris + DH
(`--no-shaders`, `--no-dh`, `--scenario <name>` narrow it) and fails a
scenario on a crashed audit row or a recognized exception, Rust panic or GAL
`DependencyViolation` in matching client logs. The GAL logs every validation failure
(`rust_gal_validation_failure`, rate-limited), including teardown errors that
callers discard. A logged dependency violation fails the gate; other
validation failures are reported, not failed. Results go to
`artifacts/graphics-captures/lifecycle-gate/<label>/summary.json`
(`--artifact-root` overrides it). Lifecycle and FPS checks share the exact
ordered shutdown rule: only the server INFO `ClosedChannelException` disconnect
after the render-thread INFO `Stopping!` marker is counted separately, as
`shutdown-disconnects` in the lifecycle report. Earlier disconnects, stack
traces, other exceptions, panics and dependency failures still reject the run.
Test the classifier with
`python3 -m unittest discover -s DevUtils/tests/rendering -p 'test_runtime_log_health.py'`;
preserve historical failed receipts and use
a fresh label to rerun the affected transition. The combined validation driver's broader
client cleanup is described [above](#one-command-validation); do not confuse
it with ownership-scoped termination of one capture process group.

The [gate source](https://github.com/HungLo2020/MattMC/blob/697b0a3c6200151830a565c73aaee88d323eb484/DevUtils/tests/rendering/RunLifecycleGate.py)
defaults to seven scenarios: same-world unload/reload, different-world reload,
resource reload, resize, swapchain recreation, and view-distance decrease and
increase. It requires a successful capture command and audit rows marked
`crash_free`, and scans the selected `.log` and `.log.gz` client paths for `Exception`,
`panicked at` and `DependencyViolation`. Other suffixes are ignored; a retained
plain/gzip duplicate is scanned twice. Generic GAL validation markers are
reported without automatically failing the gate. Inspect retained logs and the
underlying scenario receipts as well as `summary.json`: the driver does not
require each row's `passed` field or prove that a matching client log exists.
Use a fresh unique label, because existing directories are reused and client
logs are scanned recursively. This is a bounded transition/log gate, not an
image-parity, leak-bound or all-error certificate.

Extra client options are repeatable and forwarded as `--jvm-arg=<option>`.
To exercise native terrain staging in detailed capture scenarios:

```sh
python3 DevUtils/tests/rendering/RunLifecycleGate.py --label <unique-label> \
  --jvm-arg=-Dmattmc.dev.forceTerrainVertexStaging=true
```

This overrides the detailed-diagnostics copy preference; faults, texture probes
and the appearance trace still require copied vertices. Verify the staging
announcement and retained receipts. The flag is not a guarantee that every
layer used staging, especially when its count cap triggers copied fallback.

The author reports all seven scenarios clean at `7f256b53`; `4740f8fa` reports
clean world-unload/reload and resource-reload repeats after native assembly.
The latter also guards VoxelMap's neighbor query when the player disappears
during unload. This documentation review ran neither the gate nor the runtime
checks and did not inspect their unbundled artifacts. The old independent
SIGSEGV remains separate unless its specific cause and regression are established.

### October 7 integration batch checks

Source reviewed through `697b0a3c`; the following fixture definitions are
separate from the author's runtime reports below:

- **Publication rows:** six [registry fixtures](https://github.com/HungLo2020/MattMC/blob/697b0a3c6200151830a565c73aaee88d323eb484/src/main/rust/render/worldrender/terrain/publication.rs) cover initial/reset/dirty rows, unique-key collisions, removal, transactional replacement, clear and coordinate packing. One [C-export fixture](https://github.com/HungLo2020/MattMC/blob/697b0a3c6200151830a565c73aaee88d323eb484/src/main/rust/render/bridge/world/terrain_publication.rs) actually applies rows to the section graph, checks bulk reads and rejects a null graph/invalid slot. These do not exercise Java's full reload/acknowledgement path or inject failures between Java and native mutations.
- **DH lifecycle batching:** [native fixtures](https://github.com/HungLo2020/MattMC/blob/697b0a3c6200151830a565c73aaee88d323eb484/src/main/rust/render/dh_collector/tests.rs) cover stale-generation rejection, stable ties, walk-ordered publication demand and LRU touches. Updated [Java comparisons](https://github.com/HungLo2020/MattMC/blob/697b0a3c6200151830a565c73aaee88d323eb484/src/test/java/net/vulkanic/world/DistantHorizonsSemanticCollectorTest.java) compare per-column and bulk calls into the same migrated ledger in ordinary and exact-atlas modes; they are not old-Java versus new-Rust runtime parity.
- **Lease and late-build repairs:** the collector fixture closes an unbuilt container without erasing another generation at the same position. Two [section lifecycle fixtures](https://github.com/HungLo2020/MattMC/blob/697b0a3c6200151830a565c73aaee88d323eb484/src/test/java/com/seibel/distanthorizons/core/render/LodRenderSectionLifecycleTest.java) exercise completion before and after close, replacement and lease release. They drive those orderings explicitly; no exhaustive concurrent interleaving or live unload stability result follows from their definitions.
- **Shared DH pages:** the [LOD fixture](https://github.com/HungLo2020/MattMC/blob/697b0a3c6200151830a565c73aaee88d323eb484/src/main/rust/render/worldrender/lod/tests.rs) checks two columns sharing vertex/index buffers at different offsets, one staging host write, generation replacement and range release after a later completed mock submission. Existing upload/rejection/range fixtures are adapted, and packed transparent bindings assert shared page sets. These are mock-GAL assertions, not cross-device Vulkan or long-run page-retention measurements. Test unpacked/deferred and exact-atlas paths separately.
- **Reload cleanup:** the new commit releases Java CPU payloads of uploaded reload replacements. It adds no dedicated reload-payload regression in this interval and does not repair the [#821](https://github.com/HungLo2020/MattMC/issues/821) fully omitted-layer native staging gap. Verify both paths separately.
- **Vulkan feature negotiation:** `drawIndirectFirstInstance` is enabled if supported; the device diff adds no dedicated feature-disabled regression. Check that configuration independently before claiming cross-device coverage or DH multi-draw acceptance.

The [integration report at `72b8cea2`](https://github.com/HungLo2020/MattMC/commit/72b8cea2e63a7186830e6c647a657745aa771969)
attributes these desktop results to the implementation author (RTX 2070,
Frozen `JavaPerfTesting` at `7a4d18171`):

- Tests: `cargo test --lib` 2,344 passed, three ignored; Java rendering suites
  `net.vulkanic.*`, `net.sodium.*`, `com.seibel.*`, `net.minecraft.client.dev.*`
  and the wiki check passed
- Lifecycle gate: seven scenarios passed, with zero reported exceptions,
  panics, dependency violations and GAL validation failures
- Frozen parity with `MATTMC_CAPTURE_DH_GENERIC=true`: Iris+DH RGB MAE
  3.646/4.160/3.849 with DH extension pass; vanilla 0.257/0.452/0.530;
  zero VUIDs, with side-by-side water/DH/sky/cloud images inspected by the author
- 20:32 moving-camera single runs, 6,000 frames: Rust/Frozen FPS vanilla
  1,100/1,151; vanilla+DH 617/736; shaders 341/318; shaders+DH 253/229.
  Corresponding median frames were 0.67/0.73, 1.31/1.14, 2.83/3.02 and
  3.87/4.23 ms. The author recorded all clean and about ±25% vanilla+DH noise

The [later driver report](https://github.com/HungLo2020/MattMC/blob/697b0a3c6200151830a565c73aaee88d323eb484/docs/development/rendering/RENDER-VERIFICATION.md)
records a default run against `72b8cea2e` in 14m20 (`validation/drv1`):
Rust 2,344, Java 792 passed plus one skipped, seven gate scenarios and parity
Iris+DH 3.663/4.177/3.853 with DH pass, vanilla 0.230/0.394/0.442. Its separate
`validation/drv1-perf` ABAB protocol used two 6,000-frame runs per side/mode:

| Mode | Rust FPS, run 1 / run 2 | Frozen FPS, run 1 / run 2 |
| --- | --- | --- |
| Vanilla | 1,171 / 1,130 | 1,226 / 1,123 |
| Vanilla + DH | 757 / 770 | 736 / 599 |
| Shaders | 343 / 351 | 318 / 316 |
| Shaders + DH | 250 / 252 | 228 / 227 |

The [22:36 summary](https://github.com/HungLo2020/MattMC/blob/697b0a3c6200151830a565c73aaee88d323eb484/SUMMARY.md) reports all runs clean and up to ±20%
noise. It describes the earlier single-run vanilla+DH deficit as within that
variability. Preserve both results; neither isolates the gain of one change,
establishes tails or proves lasting all-mode parity. The standalone and driver
captures are settled-pose evidence, distinct from moving performance workloads.
This documentation reconciliation did not rerun Java/Rust runtime suites,
the lifecycle gate, captures or benchmarks and did not inspect the unbundled
artifacts. The isolated Python tool checks [above](#one-command-validation)
have a narrower, independently reproduced scope.

### October 7 evening staging and DH checks

Source reviewed at `f13239e1`; the following tests are verification targets,
not runs performed by this documentation review. Rebuild Java and the native
library together for ABI 72 before runtime checks:

```sh
(cd src/main/rust && cargo test --release staged_vertices_serve_only_their_generation_until_discarded)
(cd src/main/rust && cargo test --release dh_collector)
(cd src/main/rust && cargo test --release dh_generic_groups)
./gradlew -PmattmcRustProfile=release test --tests net.vulkanic.world.DistantHorizonsSemanticCollectorTest
```

- **Terrain staging:** the [generation fixture](https://github.com/HungLo2020/MattMC/blob/f13239e10d0f66d244c4311c091d0d60819fb391/src/main/rust/render/worldrender/terrain/staging.rs) checks replacement, repeated reads and generation-specific/wildcard discard. It does not exercise the 32,768-layer fallback, the actual mesh-update rejection/retry, Java acknowledgement/removal/rollback wiring or concurrent publication. Inspect those separately; the staged upload clones vertices inside Rust and still copies index/range inputs.
- **DH ledger:** [ledger fixtures](https://github.com/HungLo2020/MattMC/blob/f13239e10d0f66d244c4311c091d0d60819fb391/src/main/rust/render/dh_collector/tests.rs) cover identical rebuild/owner leases, late acknowledgement after reset, protected eviction and access order, selected-route segment handover, stale execution receipts, payload differences and bridge-like asset validation. The `the_rust_flush_leaves_provenance_updates_to_java_and_acknowledges_what_it_sent` fixture calls ledger selection/acknowledgement; it does not execute the native flush export's context lookup, frontend apply, failure release or retry. Retention targets can remain exceeded by protected columns and are not a total-memory bound.
- **Bulk visibility:** two [Java comparisons](https://github.com/HungLo2020/MattMC/blob/f13239e10d0f66d244c4311c091d0d60819fb391/src/test/java/net/vulkanic/world/DistantHorizonsSemanticCollectorTest.java) compare per-column and bulk call sequences against the same migrated ledger on matching fixtures, in ordinary and exact-atlas modes. They are not a pre-migration Java-versus-Rust implementation comparison. They check tied-distance order, unpublished columns, counts, route receipts, handed-over segments and publication order. Rust fixtures also cover section centers and stable sorting. These do not independently rerun the live quadtree/frustum walk or prove temporal parity.
- **Generic groups:** two [native fixtures](https://github.com/HungLo2020/MattMC/blob/f13239e10d0f66d244c4311c091d0d60819fb391/src/main/rust/render/bridge/world/dh_generic_groups.rs) check representative camera-relative expansion, preserved instance ordinals, stale-generation skipping/resend and inverted bounds. ABI layout assertions cover the appended frame fields and 56/72-byte records. The fixtures do not prove the complete Java dirty-group/re-registration lifecycle, queued-frame overlap, registry capacity behavior or visual continuity during resend.

Add integration regressions for the source-qualified boundaries in
[#820](https://github.com/HungLo2020/MattMC/issues/820) and
[#821](https://github.com/HungLo2020/MattMC/issues/821): use the actual first
allocated group through active collection, and stage a fully omitted translucent
layer with no previous asset, verifying exact-generation cleanup. Hand-picked
nonzero group IDs and assembly-only empty-result fixtures miss those handoffs.
The issues report source inspection, not runtime reproductions.

Follow with moving vanilla/DH and Iris+DH, visible generic objects, translucent
terrain sorting, upload rejection/retry and real unload/reload/resource-reload
checks. Preserve material-provenance coverage beside the ordinary native flush
route. The author reports scoped tests/captures at the staging and generic-group
commits in the [checkpoint](GOAL-5-STATUS.md#evening-terrain-staging-and-dh-ownership-follow-up);
those reports are not fresh verification of the later ledger/payload/visibility
commits. The gzip/log and JVM-argument changes add no new checked-in lifecycle
driver regression in this interval. Runtime suites and unbundled artifacts were
not rerun or inspected by this review.

### October 7 rig and terrain assembly checks

Source reviewed through `4740f8fa`; these are verification targets, not runs
performed by this documentation review:

```sh
(cd src/main/rust && cargo test --release model_rigs)
(cd src/main/rust && cargo test --release terrain::)
(cd src/main/rust && cargo test --release retire_defers_a_referenced_resource_until_its_last_dependent_goes)
./gradlew test --tests net.vulkanic.world.ModelRigTransformParityTest
```

- **Rigs:** the [Java parity fixture](https://github.com/HungLo2020/MattMC/blob/7a6009f84d966263293f864933f4da06b1823dfa/src/test/java/net/vulkanic/world/ModelRigTransformParityTest.java) compares cow, wolf and humanoid transforms over 20 seeded pose/visibility/skipDraw trials each; Rust fixtures cover hierarchy, bounds, flags and registration validation. They do not cover Citadel extraction, glint pixels, whole-frame orb ordering or upload/withdrawal races. Keep [#803](https://github.com/HungLo2020/MattMC/issues/803) open and add a real decode-path regression for [#819](https://github.com/HungLo2020/MattMC/issues/819), including zero/many-part expansion, consecutive orbs and shadow-only streams.
- **Terrain:** [thirteen assembly fixtures](https://github.com/HungLo2020/MattMC/blob/4740f8fabffd878286850083e2d86ff733c9121e/src/main/rust/render/worldrender/terrain/assembly/tests.rs) cover opaque ranges, identity, sorted translucent ranges, facing-local rebasing, water atlas/separate-sheet behavior, generic and unsupported fluids, empty omission and malformed references. Native decoding has separate fixtures. The temporary 1,500+ layer comparison is author-reported, not a retained dual-path regression or independent run. Follow with normal sorting, water, reload and publication checks.
- **GUI sharing:** cache fixtures were adapted to `SharedVec`; this interval adds no dedicated sharing/copy-on-write regression. Check cache hits/misses, shared mutation, preparation, context recreation and address reuse separately from GPU residency.
- **Retirement:** the [new GAL fixture](https://github.com/HungLo2020/MattMC/blob/4740f8fabffd878286850083e2d86ff733c9121e/src/main/rust/render/vulkanic/tests/handles.rs#L50-L80) checks one texture with two dependent views plus immediate unreferenced retirement. It does not exercise a full set/view/texture cascade, recording/submission interleavings or all consumer-cache teardown routes. Keep those cases distinct from a passing unit fixture or the rig's three-frame delay.

### October 7 residency and selection checks

For the `20e157ca` changes, target the following regressions before fresh
runtime comparisons (these definitions were inspected, not executed, by this
source review):

```sh
(cd src/main/rust && cargo test --release gui_mesh_persistent)
(cd src/main/rust && cargo test --release accepted_mesh_geometry_stays_resident_across_frames_until_idle)
(cd src/main/rust && cargo test --release prepared_geometry_memo_reuses_unchanged_meshes_and_refreshes_placement)
(cd src/main/rust && cargo test --release semantic_item_foil_animation_is_a_uniform_and_strength_invalidates_geometry)
(cd src/main/rust && cargo test --release camera_layers_follow_visit_order_then_translucent_back_to_front)
./gradlew test --tests net.vulkanic.bridge.PackedDhGenericBoxesTest --tests net.vulkanic.gui.GuiItemMeshSemanticCollectorTest --tests net.minecraft.client.dev.GraphicsFrameBenchmarkReadinessTest
```

The [persistent-decode fixture](https://github.com/HungLo2020/MattMC/blob/20e157cab7962140b30b83f40374cdeb1e6a8b19/src/main/rust/render/bridge/tests/mod.rs#L1383-L1430)
checks repeated geometry equality, repeated invalid-geometry rejection and
address/generation transport. Despite its name, it does not change the
block-raster state of an already valid cached batch. The GUI residency test uses
mock GAL to verify accepted-write reuse and idle reclamation; foil tests keep
geometry stable while its draw transform changes. The [terrain fixture](https://github.com/HungLo2020/MattMC/blob/20e157cab7962140b30b83f40374cdeb1e6a8b19/src/main/rust/render/chunk/terrain_selection/tests.rs#L45-L74)
preserves deliberately far-first input visits, not strict distance order.
[Packed-box tests](https://github.com/HungLo2020/MattMC/blob/20e157cab7962140b30b83f40374cdeb1e6a8b19/src/test/java/net/vulkanic/bridge/PackedDhGenericBoxesTest.java)
compare encoded bytes and reject invalid values; they do not exercise the
three-buffer ring wrapping with queued work.

No dedicated tests were added here for block-entity selection, emptied-page
binding retirement or role-filtered DH teardown. The modified LOD test still
uses the existing opaque identical-draw case, not a new late-water-order test.
Exercise same-thread bridge recreation/address reuse, queue overlap, idle GUI
paths, resource reload and both shader/DH transitions in fresh runtime checks.
Use moving pistons and global/offscreen block entities for the selection change,
and observe foil animation as well as still GUI pixels. These open verification
cases are not assertions that failures have been reproduced.

For GUI target declarations, [commit `78e8e04`](https://github.com/HungLo2020/MattMC/commit/78e8e0423084f010bb47e36132550619b37644c2)
adds two GUI unit tests and extends one existing world-frame test:

- [Blur scratch declarations](https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/rust/render/guirender/frontend/tests.rs#L2565-L2606)
  check that recorded scratch passes are admitted with their owned-target list
  and rejected with an empty list.
- [Custom post-effect declarations](https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/rust/render/guirender/frontend/tests.rs#L2608-L2659)
  check a main → private intermediate → main chain and the same empty-list
  rejection. This fixture uses no external bindings or depth inputs; their
  ownership handling is source-inspected implementation, not new test coverage.
- The added [prebuilt-blur case](https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/rust/render/worldrender/tests.rs#L2617-L2669)
  in `source_candidate_prepares_matching_png_assets_without_admitting_execution`
  checks that missing stats reject without increasing the submission count,
  then supplying the recorded stats submits that frame exactly once.

The two GUI tests use [mock GAL](https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/rust/render/guirender/frontend/tests.rs#L2661-L2665).
The extended world case uses the
[test-only selected-source coordinator](https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/rust/render/worldrender/submit.rs#L200-L212);
actual mid-frame [source preparation/arming](https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/rust/render/worldrender/submit.rs#L152-L158)
is compiled under `cfg(not(test))`. These assertions cover stats propagation
and zero-versus-one submission, not execution of that production transition.
No dedicated item-raster entry case was added.

To exercise those cases from the repository root:

```sh
(cd src/main/rust && cargo test --release source_frame_gui_validation)
(cd src/main/rust && cargo test --release source_candidate_prepares_matching_png_assets_without_admitting_execution)
```

This documentation review inspected their source only; it did not execute the
tests or reproduce the original menu/world-entry failure in a live client.
After rebuilding native release, repeat the affected blurred-menu/world-entry
and custom post-effect flows, checking source-route continuity and validation
output under [real-config checks](#3-real-config-session). This bounded fix does
not establish the absence of other GUI crashes or broad rendering parity;
[Goal 5 remains incomplete](GOAL-5-STATUS.md#remaining-work).

For DH container lifetime and snapshot-pressure changes, run the real-container
regressions before a normal-overlap gameplay pair:

```sh
./gradlew -PmattmcRustProfile=release test -x testRustNative -x buildRustNative \
  --tests net.vulkanic.world.DistantHorizonsSemanticCollectorTest \
  --tests com.seibel.distanthorizons.core.render.LodRenderSectionLifecycleTest
(cd src/main/rust && cargo test --lib dh_collector)   # the Rust column ledger
```

Two lifetime rules these tests pin: a container retires only through its own
lease (closing one that never recorded a column changes nothing), and a build
that finishes after its `LodRenderSection` closed is closed, not installed.

Rebuild the native release library first if Rust changed. Keep the gameplay
readiness requirement intact; repeated DH builds in a stationary scene can
indicate premature retirement rather than ordinary streaming. Unit tests alone
do not prove temporal parity or memory stability.

For DH quad-layer changes, run the real builder regression:

```sh
./gradlew -PmattmcRustProfile=release test -x testRustNative -x buildRustNative \
  --tests com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodQuadBuilderLayerTest
```

Use the already verified release native library for these Java-only changes.
The checks cover all six faces, opaque and translucent leaf colors, water at
alpha 255, disabled transparency, and preserved semantic vertex/material bytes.
Then compare real distant foliage using the original pack and shared DH source
database. A low whole-image error can hide missing tree crowns; inspect the
distant regions and the exact screenshot/depth-mask correlation too.

Run `cargo test ... direct_dh_composition` for vanilla/DH snapshot dependencies:
first and repeated frames, no-fade to double-pass changes, resize, rejected
submission, and the opaque/translucent fade boundaries. Use actual terrain
meshes in the fixture so recording reaches the compositor; empty frames can
skip that code. Retry the matching shaders-off DH gameplay workload after a
release rebuild and check both the GAL error stream and Vulkan validation.
Passing these regressions does not establish terrain-flicker or image parity.

For actual gameplay window transitions on X11, run
`python3 DevUtils/tests/rendering/ObserveGameplayResize.py --artifact-root ROOT
--mode current-rust-vulkan-shaders-on --mode frozen-opengl-shaders-on` beside
a fresh paired gameplay run with enough frames to keep both clients alive.
The driver verifies each isolated KnotClient PID and window, requests
960×540 → 1600×900 → 1280×720, checks the benchmark viewport and captured
window extent, and records subsequent frame progress. Use a separate diagnostic
run: these external observations do not prove first-resized-frame correctness,
registered image parity, flicker or performance. Inspect native route/validation
logs and memory along with `MODE-resize/resize.json`.

For source terrain stream packing, run `cargo test ... source_stream_packing`
and `cargo test ... source_terrain_frame_stream`. They check exact staged
records addressed by `firstInstance`, shared uniforms, mixed direct and
multi-draw descriptor alignment, overflow rejection, bounded capacity and
completion-gated reuse. After rebuilding release, repeat actual shader gameplay
against Frozen with validation enabled for correctness and disabled in a separate
performance pair. Compare host-write bytes and source preparation time; packing
tests alone establish neither image parity nor a performance improvement.

On a Linux display, also run the native source-chain conformance explicitly:

```sh
MATTMC_RUN_WINDOWED_CONFORMANCE=1 CARGO_TARGET_DIR=build/rust/target cargo test \
  --manifest-path src/main/rust/Cargo.toml \
  complete_source_chain_executes_once_on_a_native_acquired_vulkan_frame \
  -- --test-threads=1 --nocapture
```

The ordinary suite leaves this windowed fixture disabled. Its native warmup
must use source-entry preparation, then refresh the next frame's exact resource
snapshot before selected execution. Its DH input needs a perspective projection
and matching inverse; an identity placeholder cannot provide valid clip planes.
This test covers source-chain submission/presentation, not Frozen image parity.

Run `cargo test --release source_empty_shadows -- --test-threads=1` after changing
empty-frame or source rollback ordering. It exercises the real preparation and
named terrain-plan builders, plus native shader sampling of both owned shadow
depths. Empty geometry must still produce a full-size opaque snapshot, and both
sampled depths must equal the clear value 1. Repeated unsubmitted plan discards
must reclaim their color targets without advancing confirmed shadow state;
replacing the runtime must retire its pending generation without losing images.
These checks supplement real world entry/exit and rejected-frame validation;
they do not establish gameplay parity or long-run memory bounds.

For single-color source changes, `cargo test ... single_color` covers paired
discovery, output-slot preservation, particle alpha testing, explicit legacy
varyings and native Vulkan shader-module creation/cleanup. From the repo root:

```sh
CARGO_TARGET_DIR=build/rust/target cargo test \
  --manifest-path src/main/rust/Cargo.toml single_color -- --test-threads=1
```

After changing translucent discovery, also run `cargo test ... translucent`.
For ordinary terrain alpha policy, run `cargo test ... normal_terrain_alpha`;
it checks opaque/cutout defaults, disabled and explicit overrides, selected
property branches, output-slot preservation and native shader-module creation.
Run `cargo test ... source_main_function_parser` after changing alpha wrappers
or DH depth insertion. These fixtures cover signature trivia, prototypes,
commented braces and malformed definitions. Rebuild release and compare foliage
and terrain visible through leaf holes in a fresh equivalent Frozen gameplay
pair; native compilation alone does not establish cutout pixel parity.
Check shader temporal inputs separately from world time and camera equivalence.
Current's deterministic temporal capture overrides do not freeze Frozen's Iris
`SystemTimeUniforms` timer. A fixture-equivalent static pair can therefore contain
different animated cloud/water states. Keep the unchanged image tolerance and
record that limitation; establish matching source-clock inputs before accepting
animated shader parity. Do not change Frozen's renderer to make a pair pass.
Single-color water tests cover disabled/default and explicit `GREATER` alpha
tests and reject undeclared auxiliary writes. The real-pack probe below includes
the separate translucent program. Retry the original gameplay workload after a
release rebuild; source compilation does not prove water pixels or route continuity.

For pre-terrain source changes, run `cargo test ... pre_terrain_fullscreen`.
The regressions cover enabled begin/prepare ordering, native modules, resource
closure, feedback/mip requirements and missing-pair rejection. Warm-frame
checks preserve earlier writers, clear both sides of clear-enabled warm targets
before begin, and record opaque outputs before deferred feedback. A command-recording fixture checks the
begin/shadow/prepare/terrain boundaries; it does not submit its geometry. The
ordinary source-pass regression checks color preservation and depth clearing. Probe the real pack, rebuild release, and capture a fresh
Frozen OpenGL pair: compilation alone cannot prove sky or fog pixels.
For a cold radius-10 static scene, the existing `--workload-profile settled-static
--profile extended` capture allows 900 readiness frames on both clients and
retains the required quiet window. Keep any shorter-deadline failure recorded;
this profile is correctness evidence, not a startup-performance pass.

Run `cargo test ... frame_start_clears` for color lifecycle changes. The native
readback checks current/feedback clears with changing fog, explicit pack colors,
retained `clear=false` pixels and regenerated mip descendants. The lifecycle
regression checks cached-pass reuse, duplicate recording, actual GAL submission
rejection, resize/world replacement and resource retirement. Rebuild release and
retry a real pack with clear-enabled feedback targets; these fixtures do not
establish gameplay parity or the terrain-flicker root cause.

For custom property expressions, run `cargo test ... custom_expression` and
`cargo test ... bounds_custom_property_storage`. These cover type resolution,
dependency cycles, bounded work, option branches, source identity, std140 values
and native shader-module compilation. To check numerical behavior against
Frozen's actual compiled evaluator, use Java 25 with its existing compiled
classes and the fastutil/JOML dependency jars on the classpath:

```sh
java --class-path "$FROZEN_CLASSES:$FASTUTIL_JAR:$JOML_JAR" \
  DevUtils/tests/rendering/FrozenCustomExpressionProbe.java
```

The probe prints typed results and raw float bits, including rejected expressions.
Optional arguments are `type expression` pairs. It only reads Frozen's compiled
classes; it creates no renderer and establishes no gameplay parity. Recorded
Goal 5 cases and class hashes are in
`artifacts/graphics-captures/goal5/custom-uniform-expressions/`.

For built-in celestial/light uniforms, entity/hand propagation and same-frame
activation/reload/resize invalidation:

```sh
CARGO_TARGET_DIR=build/rust/target cargo test \
  --manifest-path src/main/rust/Cargo.toml builtin_ -- --test-threads=1
java --class-path "$FROZEN_CLASSES:$JOML_JAR" \
  DevUtils/tests/rendering/FrozenBuiltinUniformProbe.java
```

The Java 25 probe reads Frozen's compiled classes. It exercises the actual
`SmoothedVec2f` notifier/timer behavior and reproduces the celestial matrix
recipe with Frozen's `Axis` and JOML. It does not invoke a live celestial-uniform
provider or establish gameplay parity. Recorded inputs, outputs and class hashes
are in `artifacts/graphics-captures/goal5/builtin-frame-uniforms/`.
The entity/hand regression uses programs that read smoothed light and celestial
positions without `atlasSize` or a render-stage uniform. The activation case
starts with a valid lightmap-only frame and enables source semantics without
changing the existing frame/world/time memo fields. Rebuild release and retry
the real pack workload after these tests; packing checks do not establish
gameplay admission or visible parity.

Run `cargo test ... legacy_sampler` for protocol alias/phase selection, custom
PNG identity, raw secondary shadow ownership, late-writer cache replacement,
legacy fog/matrix lowering and a single-output shadow program. These tests
include native module compilation and failure cleanup. Supplemental MakeUp
module results are in `artifacts/graphics-captures/goal5/legacy-source-inputs/`;
they do not prove asset admission, shadow policy or particle semantics in gameplay.

For missing legacy particle inputs, use the [RenderDoc observation procedure](RENDERDOC-INPUTS.md)
to inspect the actual Frozen draw before adding a source default. Run
`cargo test ... material` after changing the compact input contract.

For scoped color/shadow directives, run `cargo test ... scoped_directive`.
These regressions cover dimension selection, declaration order, aliases,
conditional properties, empty overrides, bounded memo identity, attachment
replacement and cutout pipeline compatibility. The source probe also reports
the selected pack's shadow and color policies. Re-run built-in uniform and full
Rust tests when changing their shared traversal. Supplemental discovery and
native compilation do not establish live shadow, transition or gameplay parity.

Run `cargo test ... shadow_distance_selection` for copied-distance prerequisites,
multiplier/cap rules and 9,180 visibility decisions captured from Frozen's compiled
advanced, safe-zone, box and non-culling frustums. The numerical driver is
`DevUtils/tests/rendering/FrozenShadowCullingProbe.java`; run it as a Java 25 source
file with Frozen's `build/classes/java/main` and JOML 1.10.5 on the classpath. Its
output is the fixture `DevUtils/tests/rendering/fixtures/frozen_shadow_culling.tsv`.
It reproduces the private selection recipe with explicit inputs and invokes the
actual frustum classes, using an identity camera and upward light. Class hashes
and results are recorded under `artifacts/graphics-captures/goal5/shadow-caster-distances/`.
This is supplemental numerical evidence, not gameplay entity/shadow parity.

Run `cargo test ... entity_shadow_culling` for entity distances, eligibility,
leash unions, player-only roles, independent block-entity flags and strict bridge
transport. `DevUtils/tests/rendering/FrozenEntityBoundsProbe.java` runs with the
same read-only Frozen classpath and produces `fixtures/frozen_entity_bounds.tsv`:
108 actual world-AABB calls at zero, million-block and world-border origins.
Keep these separate from terrain's camera-relative entry point: distance-box
casts and safe-zone results differ. These identity-camera numeric cases remain
supplemental; real moving entity/shadow captures are required for parity.
For typed orb ordering and the copied-world enchanted-item/orb fixture, see
[entity shadow ordering checks](ENTITY-SHADOW-CHECKS.md). CPU boundary and ABI
regressions supplement gameplay captures; they cannot prove shadow pixels.
`ShadowOnlyEntityCaptureTest` checks that unported off-camera streams preserve
camera data and retain an admission receipt rather than disappearing silently.

The 2026-10-02 ABI 68 Current Complementary startup check completed with clean
validation and a settled screenshot. Its first renderable world frame was
91/submission 109, already on `rust-native-selected-source`; no later world
fallback diagnostics appeared. Evidence: `goal5/entity-shadow-bounds/live-complementary`.
This Current-only diagnostic run establishes startup continuity for that workload,
not Frozen image parity, entity-shadow acceptance, flicker or performance.

After an ABI change, rebuild `./gradlew -PmattmcRustProfile=release buildRustNative`
before Java or live checks. Run `WorldShaderEnvironmentEncodingTest` with
`VulkanicGalBridgeAbiTest` to check exported layouts, dirty-storage encoding and
the exact signed configuration value; the Rust bridge tests check decoding.

To diagnose an additional pack before gameplay, extract its `shaders/` contents
into a separate artifact directory, or use an exported semantic source snapshot.
The optional native probe requires a Vulkan device. It prepares the geometry
families, declared sky/celestial writers and the complete post-terrain
deferred/composite/final chain, then
compiles each retained vertex/fragment module. It reports unresolved scalar
semantics and rejects failed preparation or native compilation:

```sh
MATTMC_SHADER_PROBE_SOURCE=/absolute/path/to/extracted/shaders \
CARGO_TARGET_DIR=build/rust/target cargo test \
  --manifest-path src/main/rust/Cargo.toml configured_pack_source_diagnostic \
  -- --ignored --nocapture --test-threads=1
```

The probe does not load binary textures, admit gameplay resources or present a
frame. Without a snapshot's runtime environment it uses the explicit MC 1.21.10
Iris/Linux Overworld test environment. A passing probe is supplemental compiler
evidence only; compare equivalent real gameplay with Frozen before claiming parity.

Run `cargo test ... fullscreen_legacy_transform -- --test-threads=1` for native
16×16 readback checks of combined/separate matrices and `ftransform()`. This
checks viewport coverage, unit local positions, sampler row conversion and
fixed composite inputs without adding world-camera fields. The pre-fix
rejection and Frozen input/native-shader observations are retained under
`artifacts/graphics-captures/goal5/makeup-particle-renderdoc-v9/`.
Run `cargo test ... sky_legacy_transform -- --test-threads=1` for captured
sky-disc and celestial local inputs, clip positions and draw matrix checks.
See [RenderDoc inputs](RENDERDOC-INPUTS.md) for their capture provenance and
matrix layout. These tests cannot replace day/night gameplay comparisons.
Run `cargo test ... sky_lightmap_legacy -- --test-threads=1` for native readback
of both full-bright compatibility coordinates and their aliased lightmap
matrices across the sky disc, horizon and celestial quad. This regression also
checks that the primary matrix remains unchanged. Its baseline is Frozen's
`VanillaCoreTransformer`, including inputs optimized out of a RenderDoc shader;
it proves this input contract, not whole-pack gameplay parity.
Run `cargo test ... horizon_legacy_transform -- --test-threads=1` for native
coverage below the disc, copied horizon color, and capped render distance.
Its disc-only control leaves the lower region at the clear color. Frozen
frame 353 event 888 inputs are retained in `goal5/horizon-input-replay/`;
this supplemental check does not establish shader-pack gameplay parity.
`source_horizon_staging` checks draw order, fog-hidden disc behavior, standard
dimension gates and rejection before allocation when required inputs are absent.
The scoped Complementary result is recorded in
[`PROGRESS.md`](https://github.com/HungLo2020/MattMC/blob/master/PROGRESS.md), with
captured inputs/check logs in `goal5/horizon-input-replay/evidence.json` and
images in `goal5/complementary-horizon-fixed-pair/`. A passing static image
tolerance does not establish matching animated source clocks, terrain-flicker
repair, transitions or long-running memory bounds.
Run `cargo test ... fullscreen_coordinate_domains -- --test-threads=1` for
sky target sampling and inline inverse-projection UV conversion. The native
16×16 regression renders a prepared gradient, samples it through the world
sky writer, and checks upward view rays through a composite using `texcoord`.
It supplements real-pack images; it does not establish clouds or sky parity.
To read one real pre-terrain output, set
`MATTMC_RUST_SELECTED_SOURCE_CAPTURE_STAGE=shader-pack-stage:world0/prepare.fsh:auxiliary_h`
for a fresh Capture run with `--rust-selected-source-execution`. Keep that
diagnostic separate from the paired visual acceptance run.
Run `cargo test ... fullscreen_stage_color_binding` to check that a geometry
snapshot cannot replace a fullscreen stage's feedback image. The regression
also checks foreign pack/world rejection and cleanup after partial preparation.
Run `cargo test ... stage_color_binding` for geometry's program-local tables as
well. The opaque-to-translucent regression stages actual cached samplers with
different descriptor ordinals; scoped replacement must retain non-color inputs,
reject foreign generations, and leave the admission snapshot unchanged.

## 2. Frozen image comparison

After changing source shadow batching, run `cargo test ... shadow_batch_selection
-- --test-threads=1`. These regressions compare early selection with the existing
late frustum cull, including repeated opaque/translucent mesh ordering, original
frame positions, bounded cache eviction and invalid references outside the
frustum. Follow with a real shader-pack moving-camera benchmark and a paired
static image check; CPU batch equality alone does not establish runtime parity
or a performance gain.

[`DevUtils/Audit/Capture.py`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/Audit/Capture.py)
captures the same world pose in Current and in Frozen. Frozen's checkout is
found through `java_perf_repo` in `DevUtils/Common/platform/directory/directories.json`
(or `--frozen-repo`). To prepare a separate full clone, run:

```sh
python3 DevUtils/ProvisionFrozenBaseline.py --dest /path/to/Frozen
```

The [provisioner](https://github.com/HungLo2020/MattMC/blob/20e157cab7962140b30b83f40374cdeb1e6a8b19/DevUtils/ProvisionFrozenBaseline.py#L123-L154)
clones the latest `JavaPerfTesting` or fast-forwards a matching clean checkout.
It refuses worktrees, another branch/origin, ahead commits or dirty state
without discarding them. Default preparation runs `copyJdkToRun jar
shaderPackZip testClasses` while skipping tests; `--no-build` skips it.
`--copy-run-inputs` copies absent `run/options.txt`,
`run/shaderpacks/ComplementaryHungLoIfied.zip` and selected `--world` directories;
existing destinations win. Pass `--frozen-repo` to comparison tools
when `--dest` differs from their configured location, and record/pin the exact
reference commit because the latest branch moves.

[`--check`](https://github.com/HungLo2020/MattMC/blob/20e157cab7962140b30b83f40374cdeb1e6a8b19/DevUtils/ProvisionFrozenBaseline.py#L217-L274)
fetches remote-tracking refs but does not merge, build or copy inputs; this
fetch still happens with `--check --dry-run`. Plain `--dry-run` suppresses
state-changing steps while platform/Git inspection still runs; it checks cached
upstream refs, not fresh remote state. This review
did not execute the provisioner.

For ordinary interactive launch, `RunDev.py --frozen` selects the configured
checkout and skips its `test` task. It does not prepare missing inputs, force
OpenGL, check the reference branch or protect its game directory from normal
settings/save writes. See [the launcher contract](../tooling/NATIVE-BUILDS.md#launch-current-or-frozen).
An interactive launch is not a parity or integrity receipt.

A shader-pack pair at a fixed pose:

```sh
MATTMC_CAPTURE_SHADER_PACK_SOURCE=$PWD/run/shaderpacks/ComplementaryHungLoIfied.zip \
python3 DevUtils/Audit/Capture.py --profile standard \
  --mode current-rust-vulkan-shaders-on --mode frozen-opengl-shaders-on \
  --world Origin --rust-selected-source-execution --diagnostic \
  --capture-world-time 6000 --capture-camera-pose=150.5,100,530.5,105,10 \
  --artifact-dir artifacts/graphics-captures/my-check
```

Use `--mode current-rust-vulkan-shaders-off --mode frozen-opengl-shaders-off`
without the pack for vanilla. The pair lands in
`paired_visual_static_terrain/pair-01/` (both images, a side-by-side and an
amplified diff). A per-channel mean absolute error, masking the chat area:

```python
import numpy as np; from PIL import Image
d = "artifacts/graphics-captures/my-check/paired_visual_static_terrain/pair-01/"
a, b = (np.asarray(Image.open(d + n).convert("RGB")).astype(float)
        for n in ("current_rust_vulkan_01_initial.png", "frozen_java_opengl_01_initial.png"))
m = np.ones(a.shape[:2], bool); m[570:610, :1000] = False
print(np.abs(a - b)[m].mean(0))
```

- Compare **before and after in the same session.** The error moves between
  sessions with no render change (for example day 2.38 vs 2.64), so a
  stored baseline from another day doesn't prove a regression.
- Check Vulkan validation output: `grep -rho 'VUID-[A-Za-z0-9_-]*'` over the
  Current capture directory should find nothing.
- Distant Horizons pairs vary by about 0.5 between identical runs; repeat them.
- A Current capture can fail in the `measurement` phase with `timed out
  waiting for settled submitted work ... rust-terrain-settled=N/8` and no crash
  report. This also happens on unchanged code; rerun the pair before
  suspecting your change, and compare against a baseline run the same way.

Selected-source readbacks require a receipt for the exact gameplay frame. The
bounded `selected-source-execution-latest.json` covers steady-state submissions;
per-frame files record activations and may not exist for a later screenshot.
Stage copied producer observations when claiming a readback and retain them
only after its promotion. Deferred retries replace one pending snapshot rather
than consuming the bounded history of acknowledged captures. A missing or stale
receipt must still defer capture; do not relax the frame/producer checks. See
[`GraphicsAuditModelCaptureHistory`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/client/dev/GraphicsAuditModelCaptureHistory.java).

Real-world Iris+DH captures automatically retain the main and DH opaque depth
snapshots from the acknowledged selected-source submission. Their scope is
`source-dh-depth-coverage`: the pack writes shared color targets, so ordinary DH
private color attachments do not describe that route. The visible-extension
check compares pixels where DH opaque depth is below clear 1 and main opaque
depth is exactly clear 1. It verifies float readback hashes, frame/submission
identity, extent and row origin against the screenshot acknowledgement.
An 8-bit depth preview cannot distinguish distant geometry from clear depth;
missing, stale or resized attachments must fail proof rather than be guessed.
Ordinary DH full captures use their private alpha and float main depth. Request
`--rust-full-gameplay-attachments` for shaders-off DH comparisons: the default
final-output-only dump cannot prove DH coverage, even if both clients and the
whole-image comparison succeed.
Run the evidence regression checks with:

```sh
python3 -m unittest discover -s DevUtils/tests/rendering -p 'test_dh_depth_coverage.py'
```

The two source depth buffers exist only for a claimed diagnostic capture and
are released after completion or rejection before submission. Raw files use
fixed names and are overwritten on retries to bound disk use; the manifest
hashes reject files belonging to a different retry. This proves an opaque DH
extension at that pose, not translucent/water parity or temporal stability.

For particle cutouts, run `--cutout-terrain-particle hidden` first, then
`--cutout-terrain-particle visible --cutout-terrain-reference <hidden-artifact>`
with the same pack and camera. The existing harness checks source equality,
the full particle region including transparent holes, and the visible effect
against the hidden control. An opaque `particle-atlas-static-a` comparison
cannot establish transparency: all its source texels have alpha 255.

For falling-leaf particles, `-Dmattmc.dev.graphicsAuditLeafParticles=true`
holds six leaves (tinted `leaf_*` with known tints, cherry, pale oak) in front
of the camera through the ordinary `FallingLeavesParticle` extraction
([`GraphicsAuditLeafParticleFixture`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/client/particle/GraphicsAuditLeafParticleFixture.java)).
Add `.distance=<blocks>` (1–64) and `.spin=true` to exercise coarse mips and
rotation. Deterministic captures force GPU retirement and do not enable DH by
default, so observe live gameplay with the window-video observer instead, with
and without DH (`--world-distant-horizons-real-world
--world-distant-horizons-opaque`, shaders off): every leaf must keep its tint
from frame to frame. `MATTMC_TRACE_PARTICLE_QUADS=N` logs the first N decoded
particle quads per frame (texture, surface, UVs, ARGB, light, centre) to stderr;
stable values there with changing pixels point at a GPU pass, not the producer.

The new ordering regression is limited to direct, non-G-buffer particle material
quads. Run from the repository root:

```sh
(cd src/main/rust && cargo test --lib direct_dh_fade_composites_run_before_particle_draws)
./gradlew test --tests net.minecraft.client.particle.GraphicsAuditLeafParticleFixtureTest
```

The Rust test checks a particle draw after the last DH fade snapshot for both
single- and double-pass modes; it does not compare output pixels. The three Java
fixture tests check positions and invalid look direction, not tint rendering or
live frame stability. Retain live DH-on/off, shader-disabled evidence for the
leaf fix; model particles, Fabulous/G-buffer and selected shader-source behavior
need separate checks. The earlier selected-shader hidden/visible cutout evidence
also remains separate. Source/test inspection here is not a rerun of either test
command or the author's before/after window video.

## 3. Real-config session

Set diagnostic environment options before launching. Production renderer
options are snapshotted on first access through `core/environment.rs`; changing
the environment later does not reconfigure that process. Unit fixtures can
use thread-local scoped overrides to exercise different diagnostic modes.

Run the game with your own settings (`python3 DevUtils/RunDev.py` uses the
release native profile) with shaders on and off, join a world, and watch the
log for `panicked`, `Game crashed` or `VUID`. The shader route should report
`shader route active` on the first renderable shader-enabled world frame.
Check private preparation and actual presentation correlation when testing entry.
Capture both stdout and stderr: native
shader diagnostics are not necessarily copied into `run/logs/latest.log`.
An `active` message immediately followed by `vanilla fallback` is a failed
shader frame, not successful shader rendering. Verify that Shader Packs appears
in Video Settings and that toggling the configured pack changes the rendered
world; unit tests alone do not exercise the complete startup and live pass graph.

For comparisons across vanilla, shaders and DH, set `MATTMC_CAPTURE_RUN_SOURCE`
to the same source run directory (containing `saves/<world>` and `options.txt`)
for every route. The harness copies that source for both clients, including when
DH is disabled. It fails if the requested world is missing. Without the override,
it uses Current's `run/`. Use fresh artifact roots and check `source_run` and
`source_save_hash` in each canonical `fixture_manifest.json` before comparing
results across routes. World-source regressions run with:

```sh
python3 -m unittest discover -s DevUtils/tests/rendering -p test_canonical_world_source.py
```

For temporal observations on Linux/X11, launch the client through the existing
harness and pass its live Java PID to the bounded window capture driver:

```sh
python3 DevUtils/tests/rendering/CaptureWindowTimeline.py \
  --pid 12345 --output artifacts/graphics-captures/terrain-timeline \
  --frames 120 --interval 0.05 --region 0,420,480,200
```

Use a new output directory each time and choose a region inside the client
window. The driver verifies the window belongs to that PID, captures at most
240 samples, and writes timestamps, adjacent-frame pixel deltas and a temporal
range image. Capture the equivalent scene in Frozen OpenGL. Animation, camera
motion and sampling aliasing can change pixels; this report alone does not
prove flicker or its absence. A stopped client, failed capture or resize fails
the observation rather than silently switching windows.

For shorter frame changes, use the lossless FFV1 video observer (requires
`ffmpeg` and `ffprobe`) on the same live PID and region:

```sh
python3 DevUtils/tests/rendering/CaptureWindowVideo.py \
  --pid 12345 --output artifacts/graphics-captures/terrain-video \
  --fps 60 --seconds 8 --region 0,420,480,200
```

It records up to ten seconds at 30, 60 or 120 samples per second, keeps actual
video timestamps without duplicating frames, and streams decoded pixel deltas
with bounded memory. New recordings use FFV1 in `window-crop.nut` with a
microsecond encoder clock. Matroska's millisecond clock can merge distinct X11
sample times and trigger the strict timestamp validator; existing MKV evidence
remains readable. No timestamps are rewritten and the validator stays strict.
Run `python3 -m unittest discover -s DevUtils/tests/rendering -p 'test_window_video_*.py'`
to check ownership and timestamp precision. Inspect the timestamps for gaps: sampling still cannot
prove that every presented frame was observed. The video's overhead makes it
unsuitable for performance acceptance.

For movement across chunk boundaries, use [Terrain movement checks](TERRAIN-MOVEMENT-CHECKS.md).
The benchmark yaw path stays at a fixed position and does not test travel.

For the vanilla lower sky disc, run `cargo test ... sky_dark` and the Java
`SkyProceduralAdmissionTest` after rebuilding native release. The native tests
read actual pixels for local fog distances, camera translation, fog alpha and
disabled fog; the Java check verifies Frozen's bottom fan and copied draw facts.
Repeat the ordinary `--pose buried` comparison against Frozen OpenGL to inspect
uncovered sky between cave surfaces. These checks do not establish shader-pack
sky parity or solve the general terrain-flickering defect.

`ObserveGameplayVideo.py` can trigger that observer alongside an existing
`Gameplay.py` job. Use a fresh artifact root and `--artifact-preserve-current-run`
on the gameplay command so retention cannot remove a video still being written.
The observer waits for a settled camera path, checks the exact live client working
directory and retains the benchmark context before and after recording:

```sh
python3 DevUtils/tests/rendering/ObserveGameplayVideo.py \
  --artifact-root artifacts/graphics-captures/moving-terrain \
  --mode current-rust-vulkan-shaders-off --mode frozen-opengl-shaders-off \
  --minimum-path-frames 1200 --fps 120 --seconds 10
python3 DevUtils/tests/rendering/AnalyzeTerrainVideo.py \
  artifacts/graphics-captures/moving-terrain/current-rust-vulkan-shaders-off-video
```

Keep both clients alive long enough for observation; a frame-count benchmark can
finish quickly on Frozen even when `Options` records a low FPS limit. Check actual
samples and timestamps. The analyzer streams a bounded window, finds pixels
that change then return, and retains three candidate strips. If lossless recording
misses its sample-count requirement, reject it and retry both clients with the
same smaller `--region`; keep the sampling checks intact. Inspect the strips for
motion, water animation and texture aliasing; its thresholds are triage settings,
not parity tolerances. Run its checks with `python3 -m unittest discover -s
DevUtils/tests/rendering -p test_terrain_video_analysis.py`.

Repeat analysis with `--sample-lag 2` and `--sample-lag 4` on both videos:
one rendered frame may appear in several recorder samples, which adjacent-only
triads miss. These runs keep at most nine samples and write separate
`transient-analysis-lag-2/4` directories. Wider intervals also increase motion
false positives; inspect the candidate strips and retain the same thresholds.
Check the tile counts across all rows too. `--rank-by tiles` saves a separate
candidate set so a small coherent patch is not hidden by texture-edge pixels.

Moving-camera performance rows still require a drained terrain queue before
measurement starts, but the harness passes
`-Dmattmc.dev.graphicsFrameBenchmark.terrainQueueDrainDuringMeasurement=false`:
a rotating camera keeps exposing sections (cave networks advance one portal hop
per rotation in both Sodium implementations), Frozen builds them inside its own
window, and restarting on every build left vanilla rows unable to settle.
`measurementFramesWithTerrainStreaming` in the benchmark JSON counts those frames;
compare it across runs. Settled-static rows keep restarting on any build.

The capture runner's timed diagnostic dump (thread dump, heap and native-memory
summaries) stops the JVM at a safepoint, so it waits until a frame benchmark
completes or fails; its class histogram uses `-all` so it never forces a full GC.

When readiness restarts timing, the benchmark camera path resets.
Reject such video as an uninterrupted motion observation.
A deliberately labeled streaming diagnostic can pass
`--jvm-arg=-Dmattmc.dev.graphicsFrameBenchmark.requireTerrainQueueDrain=false`;
retain asynchronous build/pop-in evidence and do not use it for steady-state
performance or settled parity acceptance. Keep the normal readiness gate for
those checks. Compare the actual camera path and restart count on both clients.

Producer readiness timeouts cover one continuous blocked interval. A successful
gate or measurement restart clears the previous deadline; cumulative wait and
restart counters remain available. A short late queue change must not inherit
an expired startup deadline. The harness's overall run limit still applies.
Run `GraphicsFrameBenchmarkReadinessTest` after changes to this lifecycle.

To see where in the camera path time goes, pass
`--jvm-arg=-Dmattmc.dev.benchmark.phaseSegments=6`: entries with at least six
samples in `graphics_frame_benchmark_*.json` then also list `segmentMeans`.
These divide each entry's samples into six nearly equal segments in recording
order, independently of the sorted percentile copy. Phase values are
nanoseconds; `counterSamples`
uses each counter's own units. A segment is a slice of that entry's samples,
not necessarily the same frame interval as another entry. Compare matching
windows with Frozen's `frameNanosSamples`; early windows can include JIT warm-up
and terrain streaming. A matching final 600-frame mean does not establish a
matching full 1,800-frame result.

Per-item producer phases (GUI sprite `*.java-producer`, `world.model.java-extraction`
and `world.model.rust-enqueue`) run many times per frame, so they are recorded
only with `--jvm-arg=-Dmattmc.dev.benchmark.detailedPhases=true`. Leave the flag
off for timing comparisons; enable it to attribute Java producer time.
Whole-frame/aggregate phases remain; absent per-item entries do not mean zero
producer cost. Preserve this setting with warm-up length and measured-frame
count, and compare equivalent modes. The [October 7 summary](GOAL-5-STATUS.md#october-7-mode-summary)
keeps short and long vanilla windows distinct.

Release gameplay skips the GAL's per-frame op, handle and hazard checks unless
`MATTMC_GAL_VALIDATION=1` is set before launch. The capture harness sets it for
`--validation standard`; debug/test builds and watched buffer-upload captures
also retain the checks. See [GAL validation](VULKANIC-GAL.md) for what remains
active. Use separate validation-on correctness and validation-off timing runs.

Deterministic correctness captures drain queued frames, run synchronously and
force GPU retirement after presentation in `RustGalFrameCoordinator`. They can
hide defects caused by overlapping frames.
Also observe normal RunDev sessions or the gameplay frame benchmark, which
polls completion without forcing retirement. Run synchronization validation on
that workload; a clean static capture alone does not establish temporal stability.
Set `MATTMC_TRACE_GPU_OVERLAP=1` before a bounded gameplay launch to retain
`vulkan.submission.ownership` samples from a non-blocking native timeline query.
`gpu_incomplete_images >= 2` establishes unfinished work for distinct present
images at that instant. Offscreen/update submissions are counted separately;
failed samples report `unknown`. `gpu_sample_wall_start_ns` and
`gpu_sample_wall_end_ns` bracket the query in Unix nanoseconds for correlation
with the video observer's wall-clock interval; clock failures report `unknown`.
These intervals do not identify an exact displayed video frame.
This sampling adds diagnostic overhead and
does not establish flicker absence. The profile's `last_images_in_flight` counts
acquired images only; old observations of one acquired image cannot establish
whether GPU submissions overlapped.
Two unfinished present images are sufficient evidence, but a single unfinished
image does not exclude new offscreen work queued behind it. After the run,
`AnalyzeGpuOverlap.py LOG --video VIDEO.json --output RESULT.json` reconstructs
which submissions present from consecutive IDs, sampled completion and the
incomplete-present count. Its `offscreen_queued_while_prior_present_incomplete`
counter identifies that narrower overlap condition; timestamped witnesses are
bounded. Missing/failed samples remain unknown until completion catches up,
and inconsistent counters reject analysis. This does not prove that a shared
resource was reused, that commands execute concurrently on one queue, or that
overlap caused flicker. Run `test_gpu_overlap_analysis.py` and inspect the native
trace and video before interpreting candidates.

For a GPU-heavy real-world diagnostic, set `MATTMC_CAPTURE_WORLD_WIDTH=3840`
and `MATTMC_CAPTURE_WORLD_HEIGHT=2160` before a paired `Gameplay.py` launch.
Both launchers consume these bounded dimensions (320×240 through 3840×2160);
defaults remain 1280×720. Check each benchmark's actual window dimensions and
the fingerprint's resolution before comparing results. Menu fixtures retain
their separate `MATTMC_CAPTURE_MENU_WIDTH/HEIGHT` settings. Higher resolution
alone does not establish GPU overlap: retain the native timeline samples.
The window video observer requests only its crop from X11 using
[FFmpeg's X11 crop options](https://github.com/FFmpeg/FFmpeg/blob/n6.1/libavdevice/xcbgrab.c),
so a larger client does not require copying the entire window for every sample.
Verify the crop fits the actual client and inspect its contents and provenance;
sampling failures and invisible/offscreen regions remain failed observations.
If the window manager clamps a requested extent, retain the failed attempt.
For a separate X11 overlap diagnostic, `ObserveGameplayVideo.py
--unmanaged-extent 3840,2160` temporarily reparents only the verified isolated
game window to the desktop root and bypasses the manager's size clamp. It
requires benchmark acknowledgment of the size before recording, retains
`window-transition.json`, and requests restoration after observation. No display
mode or game context is replaced. Verify the captured crop is visible, both
receipts acknowledge the same extent, restoration succeeds and gameplay
continues. This controlled resize is not steady-state performance, exact-frame
resize correctness or fullscreen acceptance.
The observer reads `client_extent` from X11 geometry and separately records
`screenshot_extent`: a desktop-clipped screenshot cannot establish the full
framebuffer dimensions. The crop must fit both. A valid partial-window crop
does not prove correctness of the unobserved part of a larger framebuffer.

## 4. Performance A/B

For visible-HUD gameplay, initial playable entry and actual terrain travel, use
the [ordinary gameplay comparison](GAMEPLAY-PERFORMANCE.md). Keep its continuous
frame recording alongside the settled measurements below. The capture launcher
hides the minimap in settled rows, and readiness resets can exclude publication
stalls; those rows alone must not be reported as ordinary gameplay parity.
`RunValidation.py` records this scope in its FPS receipt as
`ordinary_gameplay_verified: false`. The ordinary driver is invoked separately;
its `complete` status confirms protocol completion and health checks, without
an automatic Current/Frozen performance-floor or pixel-parity verdict.

For the current original-pack moving workload, batching probes and rejected
optimization evidence, see [shader terrain profiling](SHADER-TERRAIN-PROFILING.md).

Measure with the moving-camera frame benchmark, enabled through JVM properties,
and always with the release native profile:

```sh
JAVA_TOOL_OPTIONS="-Dmattmc.dev.graphicsFrameBenchmark=true \
 -Dmattmc.dev.graphicsFrameBenchmark.status=/tmp/bench-status.json \
 -Dmattmc.dev.graphicsFrameBenchmark.workloadProfile=moving-camera \
 -Dmattmc.dev.graphicsFrameBenchmark.cameraPathType=moving-camera \
 -Dmattmc.dev.graphicsFrameBenchmark.stopAfterComplete=false" \
./gradlew -PmattmcRustProfile=release runClient -x test \
  "--args=--quickPlaySingleplayer=Origin --width 1280 --height 720"
```

Add `cameraX`, `cameraY`, `cameraZ`, `cameraYaw`, `cameraPitch` and `yawDelta`
(same prefix) to pin the camera path so runs are comparable.

The status file reports `validity.measuredAverageFps`, per-phase CPU times
under `exclusivePhaseNanos` (the native ones are `rust-gal.native-profile.*`:
GAL validation, hazard analysis, backend encode and submit, renderer phases)
and `submittedWorkCounts`.

Inspect `producerWorkloadWaitFrames`, `lastProducerWorkloadBlocker`,
`measurementRestartsAfterReadinessLoss` and `lastMeasurementRestartCause` even
when a run completes. Current's DH producer gate checks visible columns and
pending publication on every frame. A failed check discards the entire partial
measurement window and starts settling again. Moving-camera mode permits
terrain streaming during measurement, but does not disable those DH checks;
the final retained window can omit earlier active publication work. A rendered
world waiting at this gate is a benchmark readiness failure, not evidence that
the selected save failed to load.

A separate live-world diagnostic can set
`mattmc.dev.graphicsFrameBenchmark.requireTerrainQueueDrain=false` and
`mattmc.dev.graphicsFrameBenchmark.requireDistantHorizonsExecution=false`.
Record these overrides, match the effective JVM settings on both clients, and
retain positive measured DH work: native DH GPU phase samples for Current and
LOD render phase samples for Frozen. This probe does not pass the regular
readiness gate or establish visual parity. Report it separately from validation.

Run `cargo test ... whole_frame_resource_profile` after changing whole-frame
resource accounting. The world/GUI counters include frontend preparation and
recording retirement; standalone GAL submit counters have a narrower scope.
Compare creation/destruction alongside deferred destroys when diagnosing churn.
Older submit-only artifacts may report zero despite per-frame preparation.

Short and long runs revisit the same rotating view path (0.35° per frame),
but differ in warm-up, sample weighting and streaming state. The author reports
early frames costing several times more in the measured vanilla workload: Java
phases sum to the whole frame interval and each runs 3–4× slower than in steady
state, because once-per-frame methods need thousands of calls before C2
compiles them. The client therefore lowers HotSpot's tier thresholds
(`clientJvmArgs` in `build.gradle`, mirrored in `packaging/run-mattmc.*`):
the author reports 1,800-frame vanilla 588→679–749 FPS, with shader timing
unchanged in that GPU-bound workload. This was not independently reproduced.
Same-build
short vanilla runs still vary by roughly ±10% (545–676 FPS seen), with every
phase moving together; compare phase means, repeat runs, or use a long run
(`--measure-frames 60000`) before crediting a change with a few percent.

Check the recorded runtime FPS limit before comparing throughput. For the
capture runner, `MATTMC_CAPTURE_MAX_FPS=260` selects the unlimited slider value;
out-of-range values such as 1000 decode to the default 120. Validation and
graphics diagnostic runs are overhead probes, not performance acceptance.

For named-source terrain uniform changes, run `cargo test ... packed_source_uniforms
-- --test-threads=1`. These checks compare packed bytes with semantic preparation,
reject another frame or program, preserve earlier snapshots when matrices change,
and retain missing-value/non-finite-instance validation. Follow with real moving
gameplay and static image comparisons; CPU checks alone establish neither parity
nor a speedup.

Pin `MATTMC_CAPTURE_SHADER_PACK_SOURCE=/absolute/path/pack.zip` for a paired
shader run and inspect both canonical pack hashes. A single Current row and a
Current/Frozen pair can choose different default pack sources. The Oct 3 source
preparation diagnostic used `cb434…`; the first unpinned pair selected `4420…`,
which lacks the transported resource manifest and crashed Current on unresolved
flood-fill sampler roles. That failure remains separate from performance evidence
under `goal5/source-draw-preparation-profile/`. The corrected shared-pack control
completed 1,800 measured frames per side (Current/Frozen 40.2/302.1 FPS).
Two clean packed-uniform candidates reduced preparation from 5.87 to 4.83/4.78 ms
(about 18%). Current FPS 38.4/41.6 and Frozen 313.5/302.0 do not establish an
overall FPS gain. Native workload samples cover only a 128-frame prefix; readiness
restarts and measurement onset differ. The fresh static pair passes RGB MAE
2.335/1.908/2.196 at the unchanged tolerance 6, with exercised standard Vulkan
validation clean and both clients reaped. This is one shader-only pose; terrain
flicker, other packs, transitions and long-run bounds remain separate checks.

For manifest-free image discovery, run `cargo test ... legacy_owned_image_aliases
-- --test-threads=1`. The checks cover selected property branches, distinct light
history, incompatible shapes/flags, alias conflicts, final duplicate values and
explicit-manifest precedence. Real bundled source checks also prepare weather,
clouds, the full composite chain, late draw families and DH depth consumers
without the optional manifest. The entity-shadow and outline helpers reproduced
the later live failure even after composite preparation passed. Follow with the
original archive in the paired runtime command above;
source preparation alone does not establish gameplay or pixel parity.
Check the resolved roles too: a second live retry reached program preparation
but incorrectly treated shadow `gaux4` as scene color. Two before-failing checks
now require the shared gbuffers/shadow PNG override and actual shadow defines.

The corrected Oct 3 original-archive (`4420…`) shader-only pair completed both
clients with Current's standard Vulkan validation exercised and clean. RGB MAE
2.356/1.924/2.212 passes the unchanged tolerance 6; both images were reviewed.
Evidence: `goal5/original-pack-image-bindings/`, including failed runs and their
exact source/native snapshots. Source activation was frame 101, while the image
was frame 385; this is a settled pose, not exact first-world-frame proof. The
original-archive Iris+DH follow-up also completed both clients cleanly, exercising
opaque, transparent and water LOD source draws. Its whole-image RGB MAE
13.459/11.926/13.921 fails tolerance 6: Current's hillside/sky are paler and
distant silhouettes differ. Recorded client peak RSS was about 8.78/9.05 GiB
(Current/Frozen); diagnostic duration and different readiness intervals are not
FPS measurements or long-run resource bounds. Animation and flicker remain
unverified in these pairs. Failed runs also exposed undefined opaque shadow depth
during rejected-frame cleanup. Both empty-frame producers now retain the opaque
snapshot and initialize it even without caster draws; the native regression
sampled depth 0 before and 1 after. Repeated plan discards also exposed four lost
color resources, fixed by retiring frontend consumers before runtime images.
Evidence: `goal5/rejected-source-shadow-depth/`. The fresh shader-only pair passes
RGB MAE 2.356/1.924/2.214 with clean standard validation, including normal empty
world exit. This does not reproduce the original rejected-first-frame sequence
or establish repeated-transition and long-run resource bounds.
The latest original-pack Iris+DH regression also passes whole-image RGB MAE
3.701/4.194/3.889 and DH-region 4.929/5.236/4.849, with clean standard validation.
Its screenshot and depth mask join gameplay frame 666/submission 1244;
normal empty world-exit frame 672 also completed cleanly. Distant tree silhouettes
differed in that revision, despite the passing average errors. A subsequent
quad-layer correction restored the opaque leaf colors to `dh_terrain`, matching
Frozen; six real-builder cases failed before, then all 138 DH checks passed.
The fresh original-pack pair restores the distant crowns, with RGB MAE
3.721/4.249/3.882 and DH-region 4.955/5.390/4.733. Inspect the region images:
the whole-image average did not improve and had hidden the missing crowns.
Evidence: `goal5/dh-tree-silhouette-routing/`. This is still one settled coast
pose, not broad parity, motion or memory acceptance.
The shaders-off follow-up completed both clients with clean API checks. Its
full attachment readbacks join screenshot frame 185/submission 361, but the
DH-region RGB MAE 8.249/7.553/6.378 fails unchanged tolerance 6; far water is more
transparent in Current. The whole-image error 3.319/3.493/3.151 hides this too.
The pre-leaf-change control reproduces the same regional error and the same
163,576-pixel depth mask. Frozen's masked pixels are identical; Current differs
by one red-channel unit at one pixel. This water mismatch predates the leaf fix.
The fixed source and compiled class were restored, and all 138 DH Java checks
passed again. Both control clients completed with clean API checks; Frozen's
full recorded worktree state stayed unchanged. Current's private color already
shows the pale seabed before fog resolve, but a Frozen intermediate comparison
is needed to distinguish material, blending and coverage causes. This shaders-off
result does not establish the cause of shader-enabled haze. Evidence:
`goal5/dh-tree-silhouette-routing/water-control-verification.json`.
The earlier final-only attempt had no DH depth proof and is not an accepted
comparison.

When investigating haze, compare camera/projection and copied fog inputs before
changing pack settings. Matching vanilla fog does not establish matching
shader-pack atmospheric fog: the pack may reconstruct distance from main or DH
depth and use separate altitude, weather and lighting uniforms. A diagnostic
pack copy that bypasses one fog function, used identically in both isolated
clients, can separate that contribution from bloom or volumetric lighting.
Retain the original and diagnostic archive hashes and the one changed entry;
keep the production pack intact and report this as a causal experiment, not a
parity fix. Fullscreen uniform receipts named `latest` or overwritten per stage
can contain menu-frame identity matrices after world exit. Retain live receipts
and join their gameplay frame to the screenshot acknowledgement before using
them to explain captured pixels.

For a shader-disabled DH fog experiment, set
`MATTMC_CAPTURE_DH_DISABLE_FOG=true` on the paired capture command. Dedicated DH
rows must also emit `MATTMC_CAPTURE_DH_KEEP_FOG=false`: Frozen's shell launcher
applies the keep setting last, so contradictory flags invalidate the pair.
Check `enableDhFog=false` in both captured configurations before interpreting
the images. Run `python3 -m unittest discover -s DevUtils/tests/rendering -p
test_dh_capture_fog.py` for the launcher-environment regression. Disabling fog is
a diagnostic input, not a rendering fix or production parity result.
The Oct 3 shader-disabled coast experiment verified fog disabled in both
captured configs and clean API checks. The pale water/seabed difference remains;
DH-region RGB MAE rises to 12.311/11.190/9.548, so DH fog alone does not explain
it. Evidence: `goal5/dh-tree-silhouette-routing/fog-isolation-verification.json`.
The initial experiment was stopped after discovering contradictory fog flags
and is excluded. Four before-failing launcher subcases pass after the override
correction; the broader seven capture tests pass. Production fog stays enabled.

For built-in DH lighting changes, run
`cargo test --release ordinary_dh_native_skylight -- --test-threads=1` from
`src/main/rust`. The GAL readback checks low and high skylight rows in opaque
and inherited-transparent water draws. It failed before the correction: sky
zero selected the bright green row instead of the intended dark red row.
Preserving the original coordinate passes all four combinations. Frozen's
actual low-skylight post-vertex output is retained under
`goal5/dh-water-source-observation/`; see [RenderDoc observations](RENDERDOC-INPUTS.md).
The exact-atlas built-in vertex uses the same coordinate rule. The reduced-color
GPU fixture does not establish exact-atlas runtime parity. Rebuild release before
the real Current/Frozen water comparison; the fixture alone is not acceptance.

The corrected release passed the full Rust suite (2,150 tests, three ignored)
and the normal-fog, shader-disabled coast comparison. Whole-image RGB MAE is
0.943/1.205/1.420; DH-region error fell from 8.249/7.553/6.378 to
2.043/1.587/1.473, within unchanged tolerance 6. The same 163,576-pixel DH mask
was used. The pale submerged patches disappeared in visual review. Both clients
completed with clean API checks; the shared DB, camera, fog and full Frozen
source state were preserved. The Current process mapped the corrected release
library. Retained evidence: `goal5/dh-water-source-observation/runtime-verification.json`.
This proves the lighting correction at one settled coast pose, not motion,
terrain-flicker, broad DH parity, performance or long-run memory stability.

The Oct 3 16:30 haze observation was followed by an isolated atmospheric-fog
bypass pair (`goal5/original-pack-fog-isolation`). Both clients completed cleanly
and the harness found equivalent inputs, but RGB MAE 15.402/14.300/17.609 still
fails tolerance 6; the nearby hillside remains washed out. This does not identify
the cause. Live deferred/composite uniforms were retained for the actual captured
gameplay frame 657, avoiding the later identity-matrix overwrite. The production
native and Frozen tracked state stayed unchanged.
A second isolated pair bypassed only `GetVolumetricLight`, preserving atmospheric
fog (`goal5/original-pack-volumetric-isolation`). RGB MAE 3.706/4.211/3.890 and
the DH-covered region 4.910/5.220/4.874 pass tolerance 6, with clean API checks.
This implicates the light-shaft path in the washout; disabling it is not a fix.
The sky-check loop used an authored lower-left integer depth address without
converting it to Vulkan target rows. A native regression failed before the fix:
the intended five sky samples instead returned zero, driving excess volumetric
light. The lowering now converts source-derived integer addresses at reads of
bound targets and preserves native fragment/history addresses and PNG overrides.

From `src/main/rust`, run
`cargo test --release fullscreen_authored_texels -- --test-threads=1` for the
integer-address regression: a real GAL render writes a known top/bottom gradient
and samples the pack's viewport-relative sky-check pattern while retaining native
per-fragment addressing. Before the correction, its sky count was zero instead
of five. Source checks cover the actual bundled loop, aliases, comments, PNG
overrides, vertex reads, sampled mip dimensions and ambiguous reassignment.
Reprojection returns already converted into image UVs remain native, including
integer history reads without a direct varying dependency; the GPU test checks
this independently of the sky-count channel.
Follow with the original archive and normal light-shaft path in the real paired
command; the diagnostic bypass and gradient test do not establish live parity.

The corrected original-pack Iris+DH pair at the same coast pose passes whole-image
RGB MAE 3.712/4.221/3.895 and DH-covered-region 4.915/5.225/4.842, within unchanged
tolerance 6. Both clients completed; Current's standard Vulkan validation was
exercised and clean. The screenshot's gameplay frame 645 admitted 150 opaque,
163 transparent and 111 water LOD instances, with live deferred/composite uniforms
retained for that frame. A separate shader-only regression passes RGB MAE
2.342/1.918/2.197. Both comparisons were visually reviewed. Original pack, light
shafts, fog, shared source database and Frozen state stayed intact. Evidence:
`goal5/fullscreen-authored-texel-rows/`, including failing regressions, exact
source/native snapshots, full 2,146-test result and the two runtime comparisons.
These settled poses establish the scoped haze correction, not broad parity,
motion/flicker, first-world-frame, performance or long-run resource acceptance.
Underground original-pack lighting remains a separate failed comparison. See
[Underground shader lighting checks](UNDERGROUND-SHADER-CHECKS.md) for saved-pose
controls, exact submission-linked uniforms, full shadow-map observations and
archived fog/volumetric isolation. The settled fog inputs now agree; Frozen's
volumetric shadow-depth samplers remain unbound. Diagnostic agreement does not
establish original-pack acceptance or extend the user's existing haze exception.

The fresh original Iris+DH coast check after the shadow-facing fix has equivalent
inputs and RGB 3.698/4.237/3.873 within threshold 6, but its aggregate fails
validation. At capture frame 660/submission 1243, the full dump reads compatibility
translucent snapshots that the selected source route never produced, causing
`VUID-vkCmdDraw-None-09600` for their undefined layouts. The before-failing
availability regression now excludes those readbacks and records explicit
unavailability, while admitting prior submitted and same-submission producers.
Six source checks, 34 capture checks and the release build pass. The fresh
original-pack pair then passes with clean applicable validation, VUID/GL error
counts zero and both actual clients exiting 0. Whole RGB is
3.671/4.177/3.852; the 178,766-pixel DH extension is 4.951/5.389/4.726, both
within threshold 6. Frame 657/submission 1234 has eight owner-linked uniform
blocks and explicit unavailability for the two unused snapshots, with no
PNG/raw files for them. The pair was visually reviewed. Peak diagnostic RSS is
8.49 GiB Current/9.10 GiB Frozen; this is no performance or long-run bound claim.
Both failing and fixed runs are retained in
`goal5/shadow-facing-coast-regression/{before,after}-runtime-verification.json`.

Profile ordinary gameplay separately when investigating work the benchmark
suppresses. Attach JDK 25's `jcmd CLIENT_PID JFR.start name=render settings=profile
duration=60s filename=/absolute/path/render.jfr` to the verified game process.
Use `jfr view hot-methods`, `allocation-by-site` and `native-methods` on that
recording; separate render-thread stacks from chunk-worker costs. Native samples
include both execution and waiting. Keep the recording and compact aggregates;
expanded JSON repeats class-loader metadata and can be much larger.

For selected render-thread methods, stream compact sample counts and allocation
weights directly from the recording:

```sh
java DevUtils/tests/rendering/SummarizeRenderJfr.java /absolute/path/render.jfr \
  net.vulkanic.world.RustGalWorldPrimitiveRenderer.copyStandardItemFoilTexture \
  net.vulkanic.world.StandardFoilTextureCache.copy
```

The tool reads allocation `eventThread` and execution/native `sampledThread`
fields. Weights estimate sampled allocations; they are not exact byte counts or
an FPS measurement. Compare equivalent workloads and recording settings.

The frame coordinator formats its automatic audit line only when
`mattmc.dev.graphicsAuditSliceMetrics=true`, outside measured benchmark frames.
Explicit `currentAuditMetricsLine()` requests still work. Check the logging flag
before building diagnostic strings; testing it inside the sink still allocates
the message during ordinary gameplay.

- Run 5 times per side, with and without Distant Horizons, in the same session.
  Build the baseline by stashing your change, run it, then restore and verify
  the tree.
- Entity and terrain workloads differ between runs and affect FPS.
  `submittedWorkCounts` accumulates during settling, warmup and discarded
  measurement attempts too; it is not a count for the final timing window.
  Compare readiness restarts and frame-correlated workload evidence before
  interpreting a small FPS change. Do not divide that cumulative map by the
  final measured frame count.
- Treat differences within about one standard error as noise. Large
  improvements you didn't design (code-layout effects) aren't wins to claim.

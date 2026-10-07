# Goal 5 rendering checkpoint

**Goal 5 remains incomplete.** The current review covers retained-scene,
queued-frame, compact-terrain, visibility and rendering-cost work through
[`20e157ca`](https://github.com/HungLo2020/MattMC/commit/20e157cab7962140b30b83f40374cdeb1e6a8b19),
including persistent GUI geometry, graph-visit terrain order, vanilla block-entity
selection and DH transport/batching. It adds native ownership and author-recorded workload improvements;
it does not establish broad visual/temporal parity, full scene migration,
long-run resource bounds or resolution of the earlier independent native crash.

Java still supplies world/entity semantics, graph updates and build scheduling.
Rust now performs camera section-graph search, ordinary compact terrain/caster
selection and an off-camera entity-shadow prefilter, alongside native frame
execution and GPU resource ownership. Java still reads visits for pending build
work and lazy entity-culling checks, marks animated sprites and retains the
ineligible-frame terrain producer. The final target remains one Rust
executable supporting client and server, at most one separately loaded Rust
library, and no Java. See [Project Architecture](../PROJECT-ARCHITECTURE.md),
[Render Architecture](RENDER-ARCHITECTURE.md) and [Retained Scene](RETAINED-SCENE.md).

The earlier [October 4 tracker checkpoint](https://github.com/HungLo2020/MattMC/issues/747#issuecomment-5982772544)
and [October 3 checkpoint](https://github.com/HungLo2020/MattMC/issues/747#issuecomment-5972378509)
retain their historical scope and acceptance limits. The older evidence below
is preserved; it is not a measurement of the current implementation.
Those source checkpoints are
[`37817e1`](https://github.com/HungLo2020/MattMC/commit/37817e128b99b07456c0ee22a5d34eaf05c72150)
and [`2fff1ef`](https://github.com/HungLo2020/MattMC/commit/2fff1ef19106350f806ddedd4fb3c3b4fbc44716).

## What changed

### October 7 source review

#### GUI residency and mode-cost follow-up

The `20e157ca` source checkpoint extends the earlier review below:

- **GUI reuse:** flat and standard-foil items keep topology/raster identities; foil pixels remain animated through a per-draw UV transform. Cached TACZ captures can use persistent bridge storage. Native decode, prepared-geometry and accepted GPU-range reuse avoid repeated work, with separate bounds and lifetimes. Geometry idle age counts mesh transactions, not every displayed frame. Same-thread context recreation/address reuse remains a verification gap, not a demonstrated failure. [Bridge](JAVA-BRIDGE.md) · [Architecture](RENDER-ARCHITECTURE.md#resource-ownership-and-retries)
- **Terrain and block entities:** solid/cutout compact selection preserves graph visit order; translucent selection stays back to front. This is not a strict Euclidean near-to-far sort. Shader-disabled block-entity extraction consumes visited built sections plus global block entities when a current search exists; shader frames and unavailable-search cases keep the range-scan path. Java still owns the semantic extraction. [Selection boundary](RENDER-ARCHITECTURE.md#resource-ownership-and-retries)
- **DH and resources:** generic boxes use bounded primitive buffers and a three-slot pending ring; source opaque and late-water ranges each use an ordered pass. Ordinary DH pack-set release filters changed resource roles; exact-atlas teardown remains broader. Empty world geometry pages retire only their dependent bindings. These changes do not establish long-run bounds or cross-route temporal parity. [Architecture](RENDER-ARCHITECTURE.md#resource-ownership-and-retries)
- **Java and diagnostics:** four exact-key sky samples, throttled disk-state checks, single-pass chunk vertex construction, bulk native-profile reads and optional per-item phases reduce collection/instrumentation work. Disk-state reuse is unconditional within a nonzero frame epoch and may outlast 250 ms; the interval applies between epochs. [Profiling](SHADER-TERRAIN-PROFILING.md) · [Verification](RENDER-VERIFICATION.md)

This review inspected pinned source and test definitions only. It did not rerun
Java/native tests, live captures or benchmarks, and did not inspect the author's
unbundled artifacts. The [verification guidance](RENDER-VERIFICATION.md#october-7-residency-and-selection-checks)
distinguishes new assertions from remaining lifecycle and visual checks.

#### Earlier native-selection checkpoint

The following review retains its [`313e7a8a`](https://github.com/HungLo2020/MattMC/commit/313e7a8a82a34dc915c4924a78da77c720af2f7e) scope; later ordering and residency
changes above are not covered by its byte-identical-record report.

- **Ordinary terrain selection:** Rust now forms compact camera layers, optional off-camera shadow candidates and animated-section positions from graph visits and mirrored published mesh rows. Java copies native-layout records without its ordinary visible-list/per-section record construction. It still owns readiness/build scheduling, mesh publication, animated-sprite marking and semantic producers; diagnostics, faults, reloads, explicit per-record mode and identity-receipt frames retain the Java producer. This advances the visibility phase without completing all retained-scene phases. [Selection boundary](RENDER-ARCHITECTURE.md#resource-ownership-and-retries) · [Bridge lifetime](JAVA-BRIDGE.md#standalone-query-handles)
- **Camera entity culling:** the Sodium option gates an additional visited-section rejection before the ordinary frustum check. Glowing/name-visible entities and other documented bypass cases bypass the added rejection; the hook does not affect shadow-pass admission. This avoids some Java extraction work without moving entity semantics or adding Citadel model transport. [Architecture](RENDER-ARCHITECTURE.md#resource-ownership-and-retries)
- **Direct DH particle order:** particle-source material quads draw after the direct non-G-buffer route's DH vanilla-fade composites, translucent terrain and receiver shadows. The regression checks command order for both fade modes; the fixture supports live tint/mip/rotation inspection. Model-mesh particles, Fabulous/G-buffer and selected shader-source paths remain separate. The earlier [#748 selected-shader alpha review](https://github.com/HungLo2020/MattMC/issues/748#issuecomment-5972310449) is not replaced by this ordering repair. [Particle scope](RENDER-ARCHITECTURE.md#shader-controls-at-startup) · [Verification](RENDER-VERIFICATION.md)

This review inspected source and test definitions at `313e7a8a`; it did not rerun
native/Java tests, capture live gameplay or independently inspect the author's
unbundled comparison/video artifacts. The three terrain-selection tests cover
ordering/flags, empty or unbuilt sections, animation selection, bounded shadow
candidates and duplicate/cleared mesh rows. The entity-culling commit adds no
dedicated camera-hook regression. Neither those definitions nor the particle
command-order check establish broad visual or temporal acceptance.

### October 6 source review

The following bullets preserve the earlier
[`121ad13c` checkpoint](https://github.com/HungLo2020/MattMC/commit/121ad13c84e45555c34814d54a8199194b37f39c). In particular,
its Java-visible-list and camera-limit descriptions predate ordinary native
record selection; use the October 7 scope above for current ownership.

- **Queued whole frames:** the calling thread decodes and copies the queued request before Java releases its arena. The native FIFO then acquires, executes and presents frames, ordered with queued mesh updates and atlas ticks. Java keeps at most one frame queued ahead; non-queued context-registry access joins pending work and standalone queries remain separate. Captures drain to the synchronous route. The earlier worker-decode/borrowed-arena contract remains only for the single in-flight fallback when queuing is disabled. This does not establish all cancellation/failure recovery. [Bridge lifetime](JAVA-BRIDGE.md#pipelined-frames)
- **Compact and retained terrain:** ABI 69/70 carries compact shadow casters and camera sections. Admitted shader frames use current-generation retained records and per-facing scene groups; pending, undescribed or camera-sorted entries retain ordinary expansion. Vanilla, Fabulous and frames leaving that route expand compact terrain. Diagnostic/fault/reload paths still support per-record data. These are partial scene capabilities, not completion of every planned phase. [Current scene route](RETAINED-SCENE.md#current-scene-terrain-on-the-shader-route)
- **Camera and shadow selection:** the Rust section graph follows Frozen-derived search rules, while Java mirrors readiness/build facts, consumes visited sections and schedules work. Shadow candidates use already-built geometry, with the leaf test also applied to camera-visible twins and supplement faces; no shadow-only build sweep is retained. Java's current limits remain 4,096 camera sections and 12,288 shadow-candidate sections, with different overflow handling. Region draw order, Iris's non-culling frustum and a scene-owned visible list remain outstanding. [Selection boundary](RENDER-ARCHITECTURE.md#resource-ownership-and-retries)
- **Entity shadow prefilter:** a standalone Rust query uses the active pack's copied shadow policy before Java extracts off-camera entities; unresolved policy keeps candidates. The frame plan applies admission again. Java still extracts poses/geometry for retained candidates, and this does not repair the Citadel geometry limitation in [#803](https://github.com/HungLo2020/MattMC/issues/803). [Query contract](JAVA-BRIDGE.md#standalone-query-handles)
- **Submission and reuse:** host-buffer writes with safe local ordering move to each command list's start; staging reuses best-fit chunks and keeps smaller idle chunks first. Fullscreen plans park and reuse complete matching inputs with up to four variants per stage path. Source-role, voxel-selection and shared entity-uniform memos avoid repeated work. [GAL](VULKANIC-GAL.md) · [Profiling](SHADER-TERRAIN-PROFILING.md)
- **Source data and color history:** terrain/entity/hand source vertices now pack into 64 bytes and decode to the same eight semantic lanes. Initialized clear-enabled feedback targets skip dead end-of-frame copies, and valid mip chains survive until level-zero changes. Java raw biome sky/fog samples reuse exact position/partial-tick/game-time keys; Java still owns those semantic producers. [Architecture](RENDER-ARCHITECTURE.md)
- **Validation and evidence:** normal release play skips per-frame GAL op/handle/hazard checks; debug builds, tests, watched uploads and explicit GAL-validation runs keep them. Standard validation captures enable the checks. Optional benchmark segment means describe sample-order slices, not broad parity. Source presence and local benchmark changes do not certify acceptance. [Verification](RENDER-VERIFICATION.md) · [Native builds](../tooling/NATIVE-BUILDS.md) · [Artifact storage](ARTIFACT-STORAGE.md)


The earlier `2fff1ef` checkpoint established this scope:

- **Shader sources and inputs:** broader single-color programs, terrain and particle alpha policy, selected custom textures and sampler aliases, typed bounded custom expressions, built-in celestial/light inputs, scoped color/shadow directives, exact R16F, begin/prepare passes, and legacy fullscreen/sky/horizon/celestial transforms. Supported contracts still gate admission; this is not compatibility with every Iris pack. [Implementation](RENDER-ARCHITECTURE.md#shader-controls-at-startup)
- **Entity shadows:** ABI 68 carries immutable entity/leash bounds, camera origin, eligibility and shadow roles for Rust-owned selection. Typed orb ordering survives shadow-only mesh filtering. Rebuild Java and native code together. [Bridge](JAVA-BRIDGE.md) · [Ordering checks](ENTITY-SHADOW-CHECKS.md)
- **DH and terrain:** CPU lifetime leases and selected-column replacement protection, correct snapshot usage through DOUBLE_PASS fading, spectator-in-solid visibility, and fogged vanilla lower-sky geometry. These address separate bounded failure cases. [Resource ownership](RENDER-ARCHITECTURE.md#resource-ownership-and-retries) · [Movement checks](TERRAIN-MOVEMENT-CHECKS.md)
- **Cost and diagnosis:** whole-frame GUI item-cache eviction, packed terrain instances/shared uniforms, earlier shadow selection, reduced hazard bookkeeping allocations, guarded audit formatting, improved timing/resource counters and bounded capture/movement/resize/overlap observers. [GAL](VULKANIC-GAL.md) · [Verification](RENDER-VERIFICATION.md)

Admission remains bounded. The source supports [at most eight color targets](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/rust/render/shaderpack/resources/color_targets/declarations.rs#L5-L7), and custom properties use the [supported typed expression grammar and functions](RENDER-ARCHITECTURE.md#shader-controls-at-startup), including scalar operations and vector construction. Unsupported active functions remain an admission failure. Selected POM, generated-normal and anisotropic-filter settings retain their [unsupported-feature gates](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/rust/render/shaderpack/contracts/terrain.rs#L1562-L1583). These boundaries prevent a blanket shader-pack compatibility claim.

### October 4 follow-up

- **Shader inputs and shadows:** source-derived integer texel addresses now use the sampled target's row convention; manifest-free aliases resolve supported Rust-owned images under the actual stage's properties. Empty selected-source frames initialize and retain owned shadow snapshots, rollback retires frontend consumers before runtime images, and terrain shadow casters retain faces omitted from camera color draws. Admission remains bounded. [Shader architecture](RENDER-ARCHITECTURE.md#shader-controls-at-startup) · [Capture inputs](RENDERDOC-INPUTS.md)
- **DH and terrain:** fully opaque leaf colors stay in the opaque DH stream; built-in DH lighting preserves dark skylight rows. The CPU terrain source combines urgent edits, one nearest-section dispatch in four, current portal-frontier work and immediate loaded-air connectivity. These are separate repairs; nearby geometry can still arrive late in cold first-turn observations. [Ownership and dispatch](RENDER-ARCHITECTURE.md#resource-ownership-and-retries) · [Movement checks](TERRAIN-MOVEMENT-CHECKS.md)
- **Costs:** per-pass immutable terrain uniforms, cached complete repeated-mesh plans, bounded foil copies and reduced snapshot/diagnostic allocations avoid measured work. Java retains the CPU producers described in the architecture. The recorded component reductions do not establish an overall FPS gain. [Shader terrain profiling](SHADER-TERRAIN-PROFILING.md)
- **Diagnosis and termination:** completed capture receipts retain exact frame/submission uniforms and attachment extents; absent optional snapshots are marked unavailable. The harness rejects an observed owned-client core dump even when the wrapper exits 143. Best-effort renderer console writes repair a reproduced closed-pipe abort; a separate disconnect SIGSEGV remains unresolved. [Verification](RENDER-VERIFICATION.md) · [GAL console contract](VULKANIC-GAL.md)

## What the recorded evidence establishes

The following paragraphs retain the earlier October 3–4 evidence. Later retained-scene reports and the October 6–7 speed summaries are separated below; none is a universal runtime certificate.

The [earlier progress log](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/PROGRESS.md) and [follow-up progress log](https://github.com/HungLo2020/MattMC/blob/37817e128b99b07456c0ee22a5d34eaf05c72150/PROGRESS.md) contain the implementation author's test and capture results. Those Java, Rust, native and gameplay runs were not rerun by this documentation review; their ignored capture artifacts are not bundled with the wiki. The earlier independent review passed 60 Python rendering-tool tests; the follow-up tracker review passed 79 isolated Python rendering-tool tests at `37817e1`. These verify tooling scope only.

The earlier log records passing scoped Complementary static comparisons, hidden/visible leaf-particle cutout comparisons, first selected-source frames for specific Complementary and MakeUp/DH-off workloads, and bounded DH movement repeats after repairing selected-column asset-ack pruning. It also records before/after observations for spectator terrain and dark-sky-disc fog. These results apply to the documented workloads and keep their original tolerances.

MakeUp's latest listed image pair still exceeds tolerance 6. The older Iris+DH V6 far-extension check remains a failed historical result: its per-channel error is 9.686/15.968/11.238 even though the whole-image average is lower. The later coast results below apply to their own recorded workloads and do not erase that verdict. A clean validation log, completed run, compiled shader or low whole-image average cannot replace the required scoped image comparison.

The follow-up's [recorded coast comparisons](RENDER-VERIFICATION.md#4-performance-ab) pass the unchanged tolerance 6 after the individual fixes: shader-disabled DH has whole-image RGB MAE 0.943/1.205/1.420 and DH-region 2.043/1.587/1.473; the later original-pack Iris+DH capture, after correcting optional-snapshot readback, has whole-image 3.671/4.177/3.852 and DH-region 4.951/5.389/4.726. Opaque leaf crowns and the integer-depth light-shaft path have their own retained before/after evidence. These are settled coast poses, not motion, first-frame, broad parity or resource-bound acceptance.

[Cold first-turn observations](TERRAIN-MOVEMENT-CHECKS.md) still show delayed nearby terrain/water. Correlated diagnostics establish missing regular geometry in one cold frame; diagnostic timing does not measure ordinary presentation latency. Historical sessions rejected by later core-record audits remain rejected. Bounded clean console-fix repeats address the closed-pipe abort only; they do not resolve the independent disconnect SIGSEGV.

The [original-pack underground comparison](UNDERGROUND-SHADER-CHECKS.md) still fails (RGB MAE 20.566/15.041/10.029). Null-depth and disabled-volumetric diagnostic comparisons are causal evidence only. Production fog and light shafts remain enabled, and the underground exception decision remains pending.

[Per-pass preparation measurements](RENDER-VERIFICATION.md#4-performance-ab) record reductions of about 18%, while [repeated-mesh batching measurements](SHADER-TERRAIN-PROFILING.md#repeated-mesh-plans) record reductions of 13–15%. Those historical repeated-mesh Current runs were about 34–35 FPS against Frozen about 304–308 FPS; varying readiness and live populations limit comparisons. No overall FPS improvement or broad performance acceptance is established.

### Latest author-recorded workloads

#### October 7 mode summary

The [source-pinned summary](https://github.com/HungLo2020/MattMC/blob/20e157cab7962140b30b83f40374cdeb1e6a8b19/SUMMARY.md) reports moving-camera runs with
matching settings. FPS and median frame time are distinct aggregate measures:

| Reported workload | Rust/Vulkan FPS / median frame | Frozen Java/OpenGL FPS / median frame |
| --- | --- | --- |
| Shaders, 1,800 frames | 314 / 2.98 ms | 307–309 / 2.99 ms |
| Shaders + DH, 1,800 frames | 223 / 4.21 ms | 228 / 4.19 ms |
| Vanilla + DH, 1,800 frames | 417 / 2.01 ms | 415 / 2.08 ms |
| Vanilla, 1,800 frames after 240 warm-up | 595 / 1.37 ms | 937 / 0.84 ms |
| Vanilla, 1,800 frames after 6,000 warm-up (earlier that day) | 912 / 0.94 ms | 1395 / 0.66 ms |
| Vanilla, 30,000 frames after 6,000 warm-up (earlier that day) | 1334 / 0.64 ms | 1462 / 0.52 ms |

These are implementation-author reports, not independent reruns or broad
performance acceptance. The [progress log](https://github.com/HungLo2020/MattMC/blob/20e157cab7962140b30b83f40374cdeb1e6a8b19/PROGRESS.md#L13)
records latest short-run ranges of shaders 305–314 and shaders+DH 215–223 FPS,
with shaders+DH p99 about 14 ms against Frozen 9.3 ms. Its earlier unequal-warm-up
vanilla parity claim is explicitly withdrawn; the equal-warm-up rows above
still show a gap. Do not combine these windows, component timings or successive
local optimizations into additive gains. Recorded scoped image passes keep
their original workloads and tolerances; failed underground comparisons,
terrain readiness/flicker, long-run resource bounds and the independent native
crash remain open.

#### Updated October 6 summary reviewed October 7

The [updated source-pinned summary](https://github.com/HungLo2020/MattMC/blob/313e7a8a82a34dc915c4924a78da77c720af2f7e/SUMMARY.md)
still describes moving-camera workloads of 1,800 frames:

| Reported workload | Rust/Vulkan | Frozen Java/OpenGL |
| --- | --- | --- |
| Complementary shader FPS / median frame | 280–294 FPS (latest 294) / 3.28 ms | 307 FPS / 2.99 ms |
| Shader last 600 frames, mean frame time | 3.10–3.24 ms | 3.20 ms |
| Shader GPU frame time | 3.18–3.23 ms | About 3.0 ms |
| Vanilla FPS / median frame | 469–492 FPS / 1.73 ms | 937 FPS / 0.84 ms |

The [same revision's progress log](https://github.com/HungLo2020/MattMC/blob/313e7a8a82a34dc915c4924a78da77c720af2f7e/PROGRESS.md#L13)
reports the native terrain-selection comparison as one pair per mode:
398→469 FPS vanilla and 280→294 FPS shaders, plus byte-identical compact records
over 1,800 frames in each mode. These local pairs and the summary's ranges are
different reports, not repeated independent confirmation or additive gains.
Its entity-culling record explicitly rejects the unusually low vanilla error
0.045 as entity evidence because that capture contains no entities; the later
RGB error 0.202/0.347/0.382 is attributed to the usual water-animation variance.

The falling-leaf fix has an author-reported fixture/window-video before/after
and a source command-order regression. None was rerun by this documentation
review. The last-600-frame slice does not establish full-run parity, and vanilla
worker/GPU timings (1.4/0.69 ms) are component measurements, not independent
costs to add to the frame median. Broad parity, repeated performance acceptance,
long-run resource bounds and the independent crash remain open.

#### October 6 speed summary

The [source-pinned `SUMMARY.md`](https://github.com/HungLo2020/MattMC/blob/121ad13c84e45555c34814d54a8199194b37f39c/SUMMARY.md)
reports moving-camera workloads of 1,800 frames:

| Reported workload | Rust/Vulkan | Frozen Java/OpenGL |
| --- | --- | --- |
| Complementary shader FPS / median frame | 271–288 FPS (latest 283–284) / 3.32–3.38 ms | 307 FPS / 2.99 ms |
| Shader last 600 frames, mean frame time | 3.10–3.24 ms | 3.20 ms |
| Shader GPU frame time | 3.18–3.23 ms | About 3.0 ms |
| Vanilla FPS / median frame | 451–472 FPS / 1.80 ms | 937 FPS / 0.84 ms |

These are implementation-author reports, not reruns by this documentation
review. The last-600 row is a different slice from the full 1,800-frame FPS
and median rows; it does not establish full-run performance parity. The summary
attributes the shader gap to early warm-up/streaming and heavier terrain/shadow
views, and the vanilla gap to CPU work. Its vanilla worker/GPU times (1.40/0.68 ms)
are component timings, not additional independent frame costs.

The [same revision's progress log](https://github.com/HungLo2020/MattMC/blob/121ad13c84e45555c34814d54a8199194b37f39c/PROGRESS.md#L12-L13)
retains author-reported scoped image comparisons and intermediate measurements,
including no measurable gain from mip-chain reuse. Its earlier double-buffered
Java staging description predates the current caller-decode
implementation. Neither these notes nor the speed summary supply new
independently inspected image/profile artifacts, long-run bounds or broad
Goal 5 acceptance.

#### Earlier retained-scene checkpoint

The [source-pinned progress log](https://github.com/HungLo2020/MattMC/blob/54611cfc25dbdf60ae4b11dc17557d2bec77469d/PROGRESS.md#L4-L11)
records successive profiled moving-shader workloads, including 133 to 188 FPS
after shadow-selection changes. The section-graph step then records a regression
from 187.8 to 175.7 FPS while more translucent sections become visible; the
entity-shadow prefilter records 175.7 to 195.7 FPS. These are separate local
comparisons with changing admitted work, not additive gains or a final
Current-versus-Frozen parity result.

The latest query checkpoint reports Iris+DH RGB error 3.683/4.188/3.866 with its
DH check passing, vanilla 0.176/0.297/0.316, zero validation events, and 2,215
native tests passing with three ignored. Earlier Java renderer checkpoints
record known AtlasAnimation/ShieldAtlas failures and separately retried
instrumentation flakes; they are not a clean independent Java-suite pass.
These are implementation-author reports. This maintenance review did not rerun
the native/Java suites, live comparisons or performance workloads, or inspect
the unbundled image/profile artifacts. The coordinated review independently
ran six focused Python tooling tests; that result verifies tooling only.

Earlier failing image comparisons, cold-readiness observations and the
independent disconnect SIGSEGV remain historical evidence with unresolved
acceptance unless a specific later result addresses them. Atomic native-library
staging prevents an overwritten mapping hazard; it is not demonstrated closure
of that separate crash. A panic/waiter test establishes waiter release, not all
frame-outcome or cancellation semantics. Follow the current
[verification rules](RENDER-VERIFICATION.md) and retain failures alongside passes.

## Remaining work

- Broad vanilla, DH, Iris and Iris+DH visual and temporal parity, including water, foliage, day/night/weather, entities/layers, hands, GUI and resource packs
- General terrain flicker investigation across all four routes, cold terrain/water readiness and the failed original-pack underground comparison; clean bounded videos do not establish acceptance
- The independent disconnect SIGSEGV, repeated entry/exit and broad native stability; the closed-pipe fix does not close these cases
- First selected-source frames for other packs and real transitions, plus reloads, dimensions, resize/fullscreen and repeated entry/exit
- Matching animated source clocks, repeated performance measurements and long-run/reload/large-radius CPU/GPU resource bounds

Offscreen source preparation helps initialize the selected graph before presentation. The current [admission code](https://github.com/HungLo2020/MattMC/blob/54611cfc25dbdf60ae4b11dc17557d2bec77469d/src/main/rust/render/worldrender/source/admission.rs#L1612-L1667) still disarms incomplete frame coverage or unavailable DH depth and reports Rust-vanilla fallback. A fallback in a required shader frame is a failed Goal 5 workload, not evidence of selected-pack success. Follow [real-config checks](RENDER-VERIFICATION.md#3-real-config-session), capture stdout and stderr, and check actual presentation correlation.

Entity culling does not add Citadel model geometry transport. The conditional empty-model limitation tracked in [#803](https://github.com/HungLo2020/MattMC/issues/803) remains applicable; avoid converting source warnings into either universal crash claims or a declaration that imported mobs now render correctly.

For source-input investigations use [RenderDoc observations](RENDERDOC-INPUTS.md). For acceptance use equivalent Frozen **Java OpenGL** workloads and the unchanged [verification rules](RENDER-VERIFICATION.md); source and numerical tests remain supplemental.

## Tracked follow-up

The later October 7 [GUI residency review](https://github.com/HungLo2020/MattMC/issues/772#issuecomment-6043636919),
[terrain/block-entity review](https://github.com/HungLo2020/MattMC/issues/520#issuecomment-6043638924),
[DH transport/batching review](https://github.com/HungLo2020/MattMC/issues/745#issuecomment-6043640406)
and [performance review](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-6043642312)
cover the `20e157ca` checkpoint without closing those issues. They preserve
lifecycle/test-coverage gaps and author-only runtime provenance; no new
native/Java run or benchmark is established by these source reviews.

The October 7 [terrain-selection review](https://github.com/HungLo2020/MattMC/issues/520#issuecomment-6029873623),
[performance review](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-6029874156)
and [DH particle-order review](https://github.com/HungLo2020/MattMC/issues/745#issuecomment-6029874814)
cover the `313e7a8a` source snapshot without closing those issues. They retain
Java/retained-scene limits, the single-pair performance provenance and the
direct-route particle scope. Source and test-definition inspection does not
establish new runtime, image or temporal acceptance.

The earlier [ownership review](https://github.com/HungLo2020/MattMC/issues/747#issuecomment-6027569797)
and [performance review](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-6027581285)
cover `121ad13c` without closing either issue. They distinguish source/test
inspection from the implementation author's measurements and captures; no
runtime suite or benchmark was rerun during this maintenance review.

The previously verified source-review comments record bounded progress without
closing these issues. They keep their original checkpoint scope; the October 6
source and speed-summary reconciliation and the October 7 source review above
do not themselves update tracker state:

| Topic | Verified comment link |
| --- | --- |
| Current rendering ownership, evidence and remaining Goal 5 acceptance | [#747](https://github.com/HungLo2020/MattMC/issues/747#issuecomment-6010252617) |
| Pipelined/retained-scene performance scope and recorded regressions | [#709](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-6010243435) |
| Compact terrain, native section graph and shadow-query boundaries | [#520](https://github.com/HungLo2020/MattMC/issues/520#issuecomment-6010247010) |
| DH iterator repair and continuing integration limits | [#745](https://github.com/HungLo2020/MattMC/issues/745#issuecomment-6010229884) |

Earlier verified tracker updates (preserved for provenance):

| Topic | Verified comment link |
| --- | --- |
| October 4 renderer follow-up and remaining Goal 5 acceptance | [#747](https://github.com/HungLo2020/MattMC/issues/747#issuecomment-5982772544) |
| October 4 performance progress and remaining measurements | [#709](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-5982769674) |
| October 4 DH integration and backport progress | [#745](https://github.com/HungLo2020/MattMC/issues/745#issuecomment-5982770594) |
| October 4 terrain/shadow ownership progress | [#520](https://github.com/HungLo2020/MattMC/issues/520#issuecomment-5982771278) |
| October 4 resource/capture evidence | [#758](https://github.com/HungLo2020/MattMC/issues/758#issuecomment-5982771898) |
| Separate player-distance ownership migration; no renderer acceptance claim | [#776](https://github.com/HungLo2020/MattMC/issues/776#issuecomment-5982773396), [scope](../world/chunk-loading/RUST-PLAYER-DISTANCE.md) |

The verified progress comments below preserve the earlier `2fff1ef` checkpoint. They are historical scoped evidence, not acceptance of the October 4 follow-up. No issue closure or completed milestone is implied by this documentation reconciliation.

| Topic | Verified comment link |
| --- | --- |
| Canonical rendering reconciliation | [#747](https://github.com/HungLo2020/MattMC/issues/747#issuecomment-5972378509) |
| ABI 68; Rust shadow distance/frustum/caster selection with Java semantic producers retained | [#520](https://github.com/HungLo2020/MattMC/issues/520#issuecomment-5972365639) |
| Selected-shader particle alpha and author-recorded hidden/visible leaf comparison | [#748](https://github.com/HungLo2020/MattMC/issues/748#issuecomment-5972310449) |
| Scoped color/format lifecycle | [#758](https://github.com/HungLo2020/MattMC/issues/758#issuecomment-5972343949) |
| Single-color output routing | [#761](https://github.com/HungLo2020/MattMC/issues/761#issuecomment-5972344807) |
| Typed custom-uniform expressions | [#762](https://github.com/HungLo2020/MattMC/issues/762#issuecomment-5972364997) |
| Hazard ranges, source packing, shadow selection and GUI cache costs | [#709](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-5972324451) |
| GUI cache/first-frame integration and remaining UI/scene producer ownership | [#772](https://github.com/HungLo2020/MattMC/issues/772#issuecomment-5972325295) |
| DH semantic leases/selected-generation lifetime, migration scope | [#777](https://github.com/HungLo2020/MattMC/issues/777#issuecomment-5972366397) |
| Native DH DOUBLE_PASS/entry integration, continuing backport scope | [#745](https://github.com/HungLo2020/MattMC/issues/745#issuecomment-5972367046) |

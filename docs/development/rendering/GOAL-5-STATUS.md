# Goal 5 rendering checkpoint

**Goal 5 remains incomplete.** [Commit `2fff1ef`](https://github.com/HungLo2020/MattMC/commit/2fff1ef19106350f806ddedd4fb3c3b4fbc44716), recorded on 2026-10-03, changes 254 paths across shader-pack handling, entity shadows, Distant Horizons (DH), terrain visibility, resource lifetime, performance work and verification tools. It adds implementation and scoped evidence; it does not establish broad rendering parity, eliminate terrain flicker or meet the overall performance objective.

The current migration retains Java configuration, CPU collection and terrain visibility work. Rust consumes copied semantics, and Rust/VulkanicGAL owns native rendering resources, command submission and presentation. The precise current boundaries are documented below; this checkpoint does not claim that every policy has migrated. The final project target is one Rust executable supporting both client and server, at most one separately loaded Rust library, and no Java. See [Project Architecture](../PROJECT-ARCHITECTURE.md) and [Render Architecture](RENDER-ARCHITECTURE.md).

The [rendering tracker checkpoint](https://github.com/HungLo2020/MattMC/issues/747#issuecomment-5972378509) maps this commit to the affected open issues. These progress comments do not close the remaining acceptance work.

## What changed

- **Shader sources and inputs:** broader single-color programs, terrain and particle alpha policy, selected custom textures and sampler aliases, typed bounded custom expressions, built-in celestial/light inputs, scoped color/shadow directives, exact R16F, begin/prepare passes, and legacy fullscreen/sky/horizon/celestial transforms. Supported contracts still gate admission; this is not compatibility with every Iris pack. [Implementation](RENDER-ARCHITECTURE.md#shader-controls-at-startup)
- **Entity shadows:** ABI 68 carries immutable entity/leash bounds, camera origin, eligibility and shadow roles for Rust-owned selection. Typed orb ordering survives shadow-only mesh filtering. Rebuild Java and native code together. [Bridge](JAVA-BRIDGE.md) · [Ordering checks](ENTITY-SHADOW-CHECKS.md)
- **DH and terrain:** CPU lifetime leases and selected-column replacement protection, correct snapshot usage through DOUBLE_PASS fading, spectator-in-solid visibility, and fogged vanilla lower-sky geometry. These address separate bounded failure cases. [Resource ownership](RENDER-ARCHITECTURE.md#resource-ownership-and-retries) · [Movement checks](TERRAIN-MOVEMENT-CHECKS.md)
- **Cost and diagnosis:** whole-frame GUI item-cache eviction, packed terrain instances/shared uniforms, earlier shadow selection, reduced hazard bookkeeping allocations, guarded audit formatting, improved timing/resource counters and bounded capture/movement/resize/overlap observers. [GAL](VULKANIC-GAL.md) · [Verification](RENDER-VERIFICATION.md)

Admission remains bounded. The source supports [at most eight color targets](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/rust/render/shaderpack/resources/color_targets/declarations.rs#L5-L7), and custom properties use the [supported typed expression grammar and functions](RENDER-ARCHITECTURE.md#shader-controls-at-startup), including scalar operations and vector construction. Unsupported active functions remain an admission failure. Selected POM, generated-normal and anisotropic-filter settings retain their [unsupported-feature gates](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/rust/render/shaderpack/contracts/terrain.rs#L1562-L1583). These boundaries prevent a blanket shader-pack compatibility claim.

## What the recorded evidence establishes

The [commit's progress log](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/PROGRESS.md) contains the implementation author's test and capture results. Those historical Java, Rust and gameplay runs were not rerun by this documentation review; their ignored capture artifacts are not bundled with the wiki. The independent review passed 60 Python rendering-tool tests, which verifies that tooling scope only.

The log records passing scoped Complementary static comparisons, hidden/visible leaf-particle cutout comparisons, first selected-source frames for specific Complementary and MakeUp/DH-off workloads, and bounded DH movement repeats after repairing selected-column asset-ack pruning. It also records before/after observations for spectator terrain and dark-sky-disc fog. These results apply to the documented workloads and keep their original tolerances.

MakeUp's latest listed image pair still exceeds tolerance 6. Iris+DH's recorded V6 far-extension check also fails: its per-channel error is 9.686/15.968/11.238 even though the whole-image average is lower. A clean validation log, completed run, compiled shader or low whole-image average cannot replace the required scoped image comparison.

## Remaining work

- Broad vanilla, DH, Iris and Iris+DH visual and temporal parity, including water, foliage, day/night/weather, entities/layers, hands, GUI and resource packs
- General terrain flicker investigation across all four routes; clean bounded videos do not establish its absence
- First selected-source frames for other packs and real transitions, plus reloads, dimensions, resize/fullscreen and repeated entry/exit
- Matching animated source clocks, repeated performance measurements and long-run/reload/large-radius CPU/GPU resource bounds

Offscreen source preparation helps initialize the selected graph before presentation. The current [admission code](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/rust/render/worldrender/source/admission.rs#L1537-L1592) still disarms incomplete frame coverage or unavailable DH depth and reports Rust-vanilla fallback. A fallback in a required shader frame is a failed Goal 5 workload, not evidence of selected-pack success. Follow [real-config checks](RENDER-VERIFICATION.md#3-real-config-session), capture stdout and stderr, and check actual presentation correlation.

Entity culling does not add Citadel model geometry transport. The conditional empty-model limitation tracked in [#803](https://github.com/HungLo2020/MattMC/issues/803) remains applicable; avoid converting source warnings into either universal crash claims or a declaration that imported mobs now render correctly.

For source-input investigations use [RenderDoc observations](RENDERDOC-INPUTS.md). For acceptance use equivalent Frozen **Java OpenGL** workloads and the unchanged [verification rules](RENDER-VERIFICATION.md); source and numerical tests remain supplemental.

## Tracked follow-up

The verified progress comments below remain scoped to this source checkpoint. No issue was closed or moved to a completed milestone by this reconciliation.

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

# Retained Rust DH visibility

Rust owns the ordinary DH render-list topology, section identity,
container-generation state and frustum traversal in a retained CPU owner.
The traversal supplies candidates directly to the existing Rust collector for
publication, stable near-to-far ordering and segment admission. On the native
route, per-frame culling input is six normalized frustum planes and world-height
bounds, or no planes for `NeverCullFrustum`/disabled culling. Java does not walk nodes,
construct candidate pairs or import generations again on that route. The
collector still returns frame metadata and sorted column keys to Java.

Java still owns DH generation/retrieval scheduling, its generation tree and
section wrappers. Mutation notifications connect that tree to the visibility
owner. This is a visibility subsystem migration, not a JVM-free DH runtime.
VulkanicGAL remains the sole GPU resource/submission/presentation owner.

## Integration and lifetime

- Stock `LodQuadTree` installs an observer on root and descendant construction,
  value replacement, branch deletion and root recentering. Public detached node
  references remain usable, but stop publishing to their former visibility owner.
- Stock sections publish enablement and completed container swaps as changes.
  Section identity tokens reject late worker updates after replacement. Container
  epochs distinguish successive swaps even when the collector reuses a generation;
  closing an older container cannot invalidate its replacement.
- Parent retirement detaches its container under the section lock before closing
  it. A worker completing during the old close keeps its newly installed build;
  retirement cannot overwrite it with a late null assignment.
- Each selection reads one coherent topology snapshot under the Rust owner lock.
  The lock is released before collector admission; no Java callback runs under it.
  The [collector rechecks candidate generations](https://github.com/HungLo2020/MattMC/blob/65457ef80471487cbdb4ad566e23e9eca07912bc/src/main/rust/render/dh_collector/visibility.rs#L56-L64)
  against its current ledger.
  Java's existing collector lock still protects the collector/provenance handoff.
- Tree close drops topology storage immediately. Late callbacks become no-ops;
  the small wrapper survives until its automatic FFM scope has no remaining users.
  No Java pointer or GPU handle is retained by the topology.
- Canonical mutation methods feed the owner. Do not write public node/value or
  container fields directly in a native-integrated producer. Existing generic
  tree users retain their original fields and behavior; generation orchestration
  still needs migration.
- The native route requires the Rust whole-frame semantic build, an exact stock
  `LodQuadTree`, a compatible owner and a non-shadow pass. With culling enabled,
  only exact `DhFrustumBounds` and `NeverCullFrustum` classes qualify; disabled
  culling also qualifies and supplies no planes, even with a custom frustum
  registered. Custom frustum callbacks keep the Java CPU traversal when culling
  is enabled; shadow passes always keep it. A custom section or container
  permanently switches that owner's compatibility flag off and closes its
  retained topology; a custom tree never creates the owner. These CPU paths do
  not introduce a Java Vulkan renderer or another presenter.

Source checked at `65457ef80471487cbdb4ad566e23e9eca07912bc`:
[retained topology](https://github.com/HungLo2020/MattMC/blob/65457ef80471487cbdb4ad566e23e9eca07912bc/src/main/rust/render/dh_collector/tree.rs),
[frame routing](https://github.com/HungLo2020/MattMC/blob/65457ef80471487cbdb4ad566e23e9eca07912bc/src/main/java/com/seibel/distanthorizons/core/render/RenderBufferHandler.java#L159-L170),
[direct collector boundary](https://github.com/HungLo2020/MattMC/blob/65457ef80471487cbdb4ad566e23e9eca07912bc/src/main/rust/render/bridge/dh_collector.rs#L840-L874),
[generation-tree integration](https://github.com/HungLo2020/MattMC/blob/65457ef80471487cbdb4ad566e23e9eca07912bc/src/main/java/com/seibel/distanthorizons/core/render/LodQuadTree.java),
[section lifecycle](https://github.com/HungLo2020/MattMC/blob/65457ef80471487cbdb4ad566e23e9eca07912bc/src/main/java/com/seibel/distanthorizons/core/render/LodRenderSection.java)
and [FFM owner lifetime](https://github.com/HungLo2020/MattMC/blob/65457ef80471487cbdb4ad566e23e9eca07912bc/src/main/java/net/vulkanic/world/NativeDhVisibility.java).

## Verify

Build Current with JDK25 and a release native library. The
[oracle driver](https://github.com/HungLo2020/MattMC/blob/65457ef80471487cbdb4ad566e23e9eca07912bc/DevUtils/tests/rendering/VerifyDhVisibility.py)
currently requires Linux x64 (`mattmc_rust-linux-x64.so` and `/proc/self/maps`)
and Frozen at `../MattMC_JavaPerfTesting/MattMC`. It asserts Frozen HEAD
`7a4d181717dc0c7da9086f1a17687dfd522ce417` and the protected prompt's hard-coded
SHA-256. Frozen's compiled classes must also be available; building them changes
outputs only, never Frozen source. The driver asks each repository's Gradle
runtime classpath rather than requiring another verification driver's cached
classpath files.

```sh
CARGO_PROFILE_RELEASE_DEBUG=line-tables-only CARGO_PROFILE_RELEASE_STRIP=none ./gradlew -PmattmcRustProfile=release buildRustNative classes
python3 DevUtils/tests/rendering/VerifyDhVisibility.py --java-home "$JAVA_HOME"
./gradlew -PmattmcRustProfile=release test -x testRustNative --tests '*LodRenderSectionLifecycleTest' --tests '*DistantHorizonsSemanticCollectorTest' --tests '*NativeDhColumnHandoffTest'
python3 DevUtils/RunWiki.py check
```

The [Frozen recorder](https://github.com/HungLo2020/MattMC/blob/65457ef80471487cbdb4ad566e23e9eca07912bc/DevUtils/tests/rendering/VerifyDhVisibility.java)
executes its actual generic `QuadTree`, node iterator and stock `DhFrustumBounds`
with synthetic `Item` values on 32 scenarios with 40 complete walks each.
[Native replay](https://github.com/HungLo2020/MattMC/blob/65457ef80471487cbdb4ad566e23e9eca07912bc/DevUtils/tests/rendering/VerifyNativeDhVisibility.java)
uses the same mutation hooks as the real generation tree, then compares
`NativeDhVisibility.inspect()` output. Controls cover null parents,
coarse/leaf values, enabled/empty/missing generations, branch deletion, negative
and world-border coordinates, recentering and retained detached references.
The current driver checks complete ordered pairs and traversal counters;
source/class/native/driver/Frozen/protected-prompt guards pin its inputs,
including five class images per side.
This replay does not construct real `LodQuadTree` sections/containers or call
the production `collectVisibleTree` publication/admission path.

Separate [section lifecycle tests](https://github.com/HungLo2020/MattMC/blob/65457ef80471487cbdb4ad566e23e9eca07912bc/src/test/java/com/seibel/distanthorizons/core/render/LodRenderSectionLifecycleTest.java)
exercise real semantic container swaps, closes and visibility bindings with
mocked tree/level dependencies. The retirement regression installs a replacement
during a mocked old-container `close()`; it is a deterministic interleaving,
not a threaded stress test. Rust controls cover late identities, empty results
and generation reuse. These checks are supplemental to real gameplay.

The runtime, performance and storage results below are the implementation
author's historical reports; this source reconciliation did not rerun or
independently verify them. For release `ebd3c9a3`, the author reports passing
the full Rust suite (2,626 passed, three ignored) and both Java suites
(1,978 reported, 1,976 executed, two skipped, zero failures). The author reports
that the post-retirement-repair oracle passed all 1,280 complete Frozen walks
with source/class/native/driver/Frozen/protected-prompt guards passing.
Initial test-task selection and Gradle init-script failures remain in the receipts;
the author reports that the corrected runs passed.

The author reports seven hardware lifecycle cases and two settled Frozen image
pairs passing with zero GAL validation failures. All four original 1280×720
images were reviewed; nine Current artifacts mapped the exact native image.
Both warmed CPU profiles also passed their guards: the Current profile samples 256 native collector calls
and zero Java node-iterator calls, versus 64 iterator samples in the predecessor.
Render-list inclusive samples are 272 versus predecessor 278; compiler activity
remains, so those counts do not establish an isolated speedup.

The author's predeclared comparison includes all 20 alternating runs, each with
360 settle, 240 warmup and exactly 6,000 measured frames. Vanilla + DH has four repeats
per side; other modes have two. Source, native image, Frozen, protected prompt,
prepared worlds and shader-pack guards passed; all ten Current fingerprints matched.

| Mode | Median FPS, Current / Frozen | Median p99 ms, Current / Frozen |
| --- | --- | --- |
| Vanilla | 1,431.4 / 1,165.9 | 2.963 / 3.293 |
| Vanilla + DH | 795.2 / 734.0 | 3.734 / 5.110 |
| Shaders | 342.4 / 313.55 | 4.697 / 7.004 |
| Shaders + DH | 262.2 / 226.0 | 6.037 / 8.647 |

The author reports all eight floors passing. This is a settled rotating workload
with the minimap hidden; it does not establish startup/first-turn behavior, sustained streaming,
long-session memory bounds, broad parity or an isolated migration gain.

Receipts: `build/native-dh-visibility/`, including `gameplay/performance.json`,
`gameplay/runtime-review.json`, `warmed-profile-comparison.json` and the
post-repair oracle under `replay-retirement/`. The predecessor profile that
selected this boundary belongs to `ee809bbab`; its receipt is
`build/native-owned-tape/warmed-profile-comparison.json`.
The author reports that, after terminal validation and input-equivalence checks,
two completed profile workspaces and twenty timing workspaces were retired; images, logs and results
remain. Lossless archival of 162 older terminal JSON files recovered 12.05 GiB,
with exact restoration hashes in the two `*completed-json-archive.json` manifests.

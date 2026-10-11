# Retained Rust DH visibility

Rust owns the ordinary DH render-list topology, section identity,
container-generation state and frustum traversal in a retained CPU owner.
The traversal supplies candidates directly to the existing Rust collector for
publication, stable near-to-far ordering and segment admission. Normal frames
supply only the six normalized frustum planes and world-height bounds; they do
not walk Java nodes, construct candidate pairs or import generations again.

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
  Java's existing collector lock still protects the collector/provenance handoff.
- Tree close drops topology storage immediately. Late callbacks become no-ops;
  the small wrapper survives until its automatic FFM scope has no remaining users.
  No Java pointer or GPU handle is retained by the topology.
- Canonical mutation methods feed the owner. Do not write public node/value or
  container fields directly in a native-integrated producer. Existing generic
  tree users retain their original fields and behavior; generation orchestration
  still needs migration.
- Custom tree/section/container implementations and custom frustum callbacks keep
  their original Java CPU traversal. Stock camera frustums and disabled culling
  use Rust. Shadow-pass callbacks keep their existing path. These CPU paths do
  not introduce a Java Vulkan renderer or another presenter.

Implementation: [retained topology](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/dh_collector/tree.rs),
[direct collector boundary](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/bridge/dh_collector.rs)
and [generation-tree integration](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/com/seibel/distanthorizons/core/render/LodQuadTree.java).

## Verify

Build Current with JDK25 and a release native library. Frozen's compiled classes
must also be available; building them changes outputs only, never Frozen source.
The oracle driver asks each repository's Gradle runtime classpath rather than
requiring another verification driver's cached classpath files.

```sh
CARGO_PROFILE_RELEASE_DEBUG=line-tables-only CARGO_PROFILE_RELEASE_STRIP=none ./gradlew -PmattmcRustProfile=release buildRustNative classes
python3 DevUtils/tests/rendering/VerifyDhVisibility.py --java-home "$JAVA_HOME"
./gradlew -PmattmcRustProfile=release test -x testRustNative --tests '*LodRenderSectionLifecycleTest' --tests '*DistantHorizonsSemanticCollectorTest' --tests '*NativeDhColumnHandoffTest'
python3 DevUtils/RunWiki.py check
```

The Frozen recorder executes its actual `QuadTree`, node iterator and stock
`DhFrustumBounds` on 32 scenarios with 40 complete walks each. Replay uses the
same mutation hooks as the real generation tree. Controls cover null parents,
coarse/leaf values, enabled/empty/missing generations, branch deletion, negative
and world-border coordinates, recentering and retained detached references.
The current driver checks complete ordered pairs and traversal counters;
source/class/native/driver/Frozen/protected-prompt guards pin its inputs.

Separate section tests exercise real semantic container swaps and closes.
Rust controls cover late identities, empty results and generation reuse.
These checks are supplemental to real gameplay. The validated release
`ebd3c9a3` passes the full Rust suite (2,626 passed, three ignored) and both
Java suites (1,978 reported, 1,976 executed, two skipped, zero failures).
The post-retirement-repair oracle passes all 1,280 complete Frozen walks with
source/class/native/driver/Frozen/protected-prompt guards passing.
Initial test-task selection and Gradle init-script failures remain in the receipts;
the corrected runs pass.

Seven hardware lifecycle cases and two settled Frozen image pairs pass with
zero GAL validation failures. All four original 1280×720 images were reviewed;
nine Current artifacts map the exact native image. Both warmed CPU profiles
also pass their guards: the Current profile samples 256 native collector calls
and zero Java node-iterator calls, versus 64 iterator samples in the predecessor.
Render-list inclusive samples are 272 versus predecessor 278; compiler activity
remains, so those counts do not establish an isolated speedup.

The fresh predeclared comparison includes all 20 alternating runs, each with
360 settle, 240 warmup and exactly 6,000 measured frames. DH has four repeats
per side; other modes have two. Source, native image, Frozen, protected prompt,
prepared worlds and shader-pack guards pass; all ten Current fingerprints match.

| Mode | Median FPS, Current / Frozen | Median p99 ms, Current / Frozen |
| --- | --- | --- |
| Vanilla | 1,431.4 / 1,165.9 | 2.963 / 3.293 |
| Vanilla + DH | 795.2 / 734.0 | 3.734 / 5.110 |
| Shaders | 342.4 / 313.55 | 4.697 / 7.004 |
| Shaders + DH | 262.2 / 226.0 | 6.037 / 8.647 |

All eight floors pass. This is a settled rotating workload with the minimap
hidden; it does not establish startup/first-turn behavior, sustained streaming,
long-session memory bounds, broad parity or an isolated migration gain.

Receipts: `build/native-dh-visibility/`, including `gameplay/performance.json`,
`gameplay/runtime-review.json`, `warmed-profile-comparison.json` and the
post-repair oracle under `replay-retirement/`. The predecessor profile that
selected this boundary belongs to `ee809bbab`; its receipt is
`build/native-owned-tape/warmed-profile-comparison.json`.
After terminal validation and input-equivalence checks, two completed profile
workspaces and twenty timing workspaces were retired; images, logs and results
remain. Lossless archival of 162 older terminal JSON files recovered 12.05 GiB,
with exact restoration hashes in the two `*completed-json-archive.json` manifests.

# Native terrain face policy

Canonical model faces now use Rust policy over the retained world state IDs in
[section snapshots](../world/chunk/RUST-SECTION-SNAPSHOTS.md). Java exports cached
face geometry and the original block-method implementation identities once.
Rust retains the geometry, derives admission from its existing block registry,
and evaluates faces inside the mesher. Java no longer visits six neighbours to
construct canonical culling masks for Rust to read back.

Implementation:
[`face_policy/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/render/chunk/meshing/face_policy),
[`NativeTerrainCulling`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/sodium/client/render/chunk/compile/tasks/NativeTerrainCulling.java)
and [`NativeSectionSnapshot`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/sodium/client/render/chunk/compile/tasks/NativeSectionSnapshot.java).
This is the current implementation. Scoped lifecycle and settled checks pass;
broad gameplay and performance acceptance remain open.

## Ownership and admission

Rust owns one immutable process-wide table for the canonical block registry.
It stores source implementation IDs, six face IDs per state, shared geometry and
bounded shape comparisons. Block identity, occlusion and fluid facts come from
the existing native block owner. Java reads a readonly CPU admission byte per
state; it does not receive native culling results or rebuild a native decision.
There are no GPU handles, alternate presenters or backend resources here.

A bulk check admits the existing 18³ world-ID grid once per section. Canonical
model records then carry a native-policy flag; the Rust mesher reads their
semantic state IDs directly. Compatibility records retain their Java masks.
The Java occlusion cache is allocated only when those callbacks are needed.
Private compact snapshot version **5** adds this flag; whole-frame ABI remains
unchanged. A stale header or invalid controlled native record is rejected.

Preserve these constraints:

- Full-neighbour **object identity** precedes skip/platform callbacks. A different
  shape with identical cube geometry does not authorize that shortcut.
- Half-transparent blocks and powder snow skip their own block identity;
  mangrove roots and canonical liquids preserve their original directional/fluid
  rules. Equivalent shape lists may share comparison storage.
- Bars retain reloadable tag queries. Leaves retain registered hooks. Unknown
  implementations, custom states and incompatible platform providers retain
  their original callback path and invocation order, including empty neighbours.
- Admission requires the canonical immutable slice. Do not apply retained native
  IDs to custom/aliased Java sources or replace their callbacks with cached results.
- Resource-pack model reload guards remain in the snapshot. Cached block face
  geometry is intrinsic world data, independent of those model generations.

The owner is bounded to 65,534 states, 1,024 unique shapes, 8,192 boxes and a
1,024² comparison table; geometry construction also bounds comparison work.
There is one table, not a cache per world, frame or section. Unsupported geometry
retains compatibility callbacks. Automatic Java staging reclamation and these
limits do not establish total host/GPU memory acceptance.

## Concurrent model-cache reads

Model quads, selectors and rendering state now share one Rust cache owner.
Compact, replay and static-model scans borrow a read guard for their entire
scan; independent builders can read concurrently. Registration and cache clear
take the exclusive write guard. Reload clears all three tables together, and
borrowed references never outlive their guard. The C ABI and Java lifecycle
admission are unchanged. Read-only builds no longer hold the three former
exclusive mutexes. The prior DH travel profile found 62 contended cache samples
among 238 native meshing samples across eight workers. This supports the change;
it does not identify the cause of the earlier DH timing outlier. The accepted
replacement profile on `30586383` observes zero contended-lock points among 193
native meshing samples across all eight workers. All three F3 positions were
reviewed (forward drift 0.545 blocks), actual opaque/translucent/water DH
submissions are positive, and source/library/Frozen/prompt/prepared-source
guards match. Sampling is not exact accumulated time or an isolated FPS gain.
The first new profile was interrupted and remains rejected; only its replacement
is accepted. A signal trace on the replacement Python wrapper exited normally;
the earlier interruptions remain unexplained. Receipt: `shared-cache/dh-wall-comparison.json`
under the migration verification directory below.

## Verification

The actual unchanged Frozen corpus records 787,992 production decisions across
31,809 states, 21,007 shape identities and all 21,316 unique geometry pairs.
The candidate matches every admitted decision and preserves tag/hook callbacks.
The real Java export/C ABI tests also compare every admitted recorded query.
A compact-builder regression compares glass/glass with glass/air without Java
masks or solid/skip hints, then rejects the stale snapshot after cache reload.
These CPU fixtures supplement runtime checks; they do not establish gameplay
or performance acceptance.

Release `30586383` passes the full Rust suite (2,473 tests; three ignored),
including eight simultaneous readers protected from reload and eight actual
C ABI builders producing identical bytes. Full Java passes 1,833 tests (two
skipped); all six JNI workers loaded that exact library. All seven lifecycle
cases and both manually inspected settled vanilla/Iris+DH comparisons pass,
including distant LOD coverage and zero Vulkan validation messages.
The encompassing validation driver was interrupted with exit 143 during FPS
measurement, before writing a final summary. Its source, library, Frozen and
protected-prompt checks still match; the owned orphan was stopped and its
incomplete evidence preserved. A first timing retry omitted the production capture environment and is rejected
too. All 16 corrected bounded ABAB runs now have exactly 6,000 frames, clean
runtime/cleanup receipts and matching sources/native identities. Shader and DH
modes pass their median floors; vanilla p99 fails (3.407/3.284 ms Current/Frozen).
DH repeats vary substantially; no isolated cache speedup is established.
Do not count either rejected batch as a completed performance gate.

Receipts for this release: `build/native-terrain-culling-migration/shared-cache/`;
runtime checks: `validation/native-terrain-culling-shared-cache-20261010/`;
corrected timings: `validation/native-terrain-culling-shared-cache-perf-retry-v2-20261010/`.
The integrated `shared-cache/runtime-verification.json` identifies the completed
stages and preserves the interruption/rejected-retry qualifications.
Settled views do not prove transient pop-in/flicker, shader first-frame behavior,
ordinary bulk-light pixels or long-running host/GPU memory bounds.

The preceding face-policy-only release `2f8255f4` passed the full source/runtime
checks and all 16 clean ABAB runs, but failed whole-renderer performance:
vanilla p99 and DH average FPS/p99. Its median DH FPS was 630.5/795.85
Current/Frozen; the slow repeat also had more GC and higher RSS. No isolated
face-policy throughput gain or causal explanation is claimed.
Historical receipt: `validation/native-terrain-culling-20261009/summary.json`.

Four accepted ordinary moving-world CPU/allocation profiles and three accepted
DH allocation/wait profiles on that prior release have manually reviewed F3
positions and source/library/Frozen/prompt/process guards. Weighted Java
allocation was 0.818/2.401 GB ordinary and 1.707/4.453 GB with DH, Current/Frozen.
The profiles identify remaining section preparation and DH builder event lookup,
lighting scratch, full-data hashing and heightmap construction as migration
work. Sparse, overlapping weights exclude native allocations; those comparisons
are not speed measurements of the shared cache. Their interruption, replacement,
position and cleanup receipts are under `build/native-terrain-culling-migration/`.

```sh
CARGO_TARGET_DIR=build/rust/target-tests cargo test --locked --manifest-path src/main/rust/Cargo.toml --profile suite --lib render::chunk::meshing
./gradlew -PmattmcRustProfile=release test -x testRustNative --tests '*NativeTerrainCullingTest' --tests '*NativeChunkMeshEncoderTest' --tests '*NativeStaticBlockModelRegistryCullingTest' --tests '*NativeMeshingStateViewTest' --tests '*NativeSectionSnapshotTintTest'
python3 DevUtils/RunWiki.py check
```

Use
[`GenerateFrozenFacePolicyOracle.java`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/meshing/GenerateFrozenFacePolicyOracle.java)
with Frozen's compiled runtime classpath. It guards the actual reference class
hashes. Losslessly gzip the recorded stream with `mtime=0`; never regenerate
expectations from Current. The bootstrap corpus does not certify loaded-world
hooks/tags, pixels, streaming or temporal terrain correctness.

See [light-map verification](../world/lighting/RUST-LIGHT-MAPS.md) for the
preceding ownership milestone and its separately scoped measurements.

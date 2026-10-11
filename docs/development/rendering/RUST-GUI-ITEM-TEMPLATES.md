# Rust GUI item templates

Local implementation in progress. ABI 80 connects the ordinary cached 3D GUI
item producer to immutable Rust CPU templates and the existing mesh consumer.
The integrated native `a99b6f8d` passes full CPU checks and plain/static-foil
Frozen GUI comparisons. Moving-foil capture admission was incorrect; the repaired route passes a fresh
Frozen comparison at matching observed natural phases. All four GUI pairs,
eight original reviews and source/native guards now pass. Combined runtime passes
seven lifecycles and two settled world pairs. The completed 20-run performance
matrix fails only vanilla p99; no isolated GUI speed improvement is claimed.

## Ownership direction

Ordinary 3D GUI items previously rebuilt one Java `GuiMeshBatchRecord` per quad
each frame. The preceding entity-consumer allocation profile attributes 134 MiB
of 184 MiB of sampled GUI batch allocation to that producer over 15 seconds.
These weighted samples are not per-frame rates or a vanilla p99 explanation.

The [template core](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/guirender/mesh/template.rs)
retains validated material layers, transforms and shared immutable geometry.
Frame instantiation supplies placement, GUI extent/pose, clip, order, cache
identity and source-layer foil facts. Rust creates the frontend requests directly;
cached items send one instance instead of per-quad records and repeated encoding.
GPU resources, passes, submission and presentation remain owned by Rust/GAL.

## Constraints

- Templates contain one complete block item with contiguous layer indices.
  Source-layer selectors preserve distinct foil clocks without per-quad inputs.
- Conservative retained storage charges cover vector capacities and referenced
  geometry, even when shared. The global template budget is 64 MiB. This bounds
  template retention, not all renderer allocations or in-flight references.
- Each frame still obeys the existing aggregate geometry-byte and batch limits.
  Java admission and native preflight count the expanded geometry before allocating.
  Invalid dynamic inputs leave the destination and its payload counter unchanged.
- Owned frontend requests keep geometry alive after template release. Copy-on-write
  storage prevents later source mutation from changing captured geometry.
- Native geometry identities include a non-reused generation; caller cache
  eviction or address reuse must not alias an older geometry generation.
- Integration must preserve model/tint callbacks, animated and custom geometry,
  independent foil inputs, overlays, sequence ordering, resize and reload behavior.
  Registration copies geometry and resolves authored CPU poses synchronously.
  Positive registry identities are never reused and are not addresses or GPU handles.
  Caller-side queued decode acquires ownership before returning; worker-decode
  fallback separately pins Java leases through join, failure or context destruction.
- The existing 256-entry topology cache retains leases. Reload/eviction removes
  those references; automatic cleanup releases unreachable native owners. The
  native registry also admits at most 1,024 owners. Capacity rejection uses status
  -14 and retains the existing inline semantic route until topology invalidation.
  Malformed inputs throw; they are not treated as capacity rejection. Animated or
  uncached geometry continues its existing live extraction route.

## Work and verification

Batch struct 97 appends template identity at field 42 and a source-layer foil
slice at field 43. Struct 118 describes each foil fact. A template instance must
zero all inline material, geometry and authored-transform fields. Inline records
must omit both template fields. Rebuild Java and the native library together.

The four core controls exercise immutable geometry/lifetime, source-layer foil
selection, transactional rejection and budget release/generation separation:

```sh
CARGO_TARGET_DIR=build/rust/target-tests CARGO_BUILD_JOBS=2 \
  ALSOFT_DRIVERS=null cargo test --profile suite \
  --manifest-path src/main/rust/Cargo.toml retained_item_ -- --test-threads=1
```

All four controls pass. Initial fixtures incorrectly mixed explicit-mesh lighting
with native block-item layout and supplied foil speed outside its allowed range;
the corrected fixtures and rejected logs are retained. Validators are unchanged.
Receipts: `build/native-gui-item-templates/core-validation.json` and
`build/native-gui-item-templates/core-tests.log`.
Three new Rust ABI controls and 30 focused JDK tests pass on release `59e65c7b`.
They cover mixed frames, independent clocks, stale identities,
ambiguous inputs, transactional registration and worker lifetime pins. The combined serial Rust suite passes 2,638 tests (three ignored); Java executes
1,999 tests with two skips. Refreshed Frozen entity/range replays and source/native
guards pass. Matched Frozen runtime, allocation and performance checks remain required. The preceding
[entity-geometry matrix](../world/entities/RUST-ENTITY-GEOMETRY.md) predates this
core and fails vanilla p99; it does not verify or measure the new owner.

The initial hardware run passed seven lifecycle cases and the reviewed shader/DH
pair (zero VUIDs), but vanilla HUD entry failed before capture: atlas retirement
looked only at the outer instance's zero asset ID and dropped its retained source
declarations. Publication now visits the template's immutable source identities
without copying an array per frame. The new rebinding/retirement regression passes alongside ten focused controls.
Refreshed Java suites execute 2,000 tests with two skips and no failures. All
1,159 Rust source paths and the native library are unchanged, allowing guarded
reuse of the serial Rust result. Frozen replays and runtime acceptance are still
being refreshed under `build/native-gui-item-templates-atlas-fix/`; the initial
failed world/logs remain.

The repaired ABI 80/native `59e65c7b` run passes seven lifecycle cases and both
settled Frozen pairs with zero VUIDs. All four original images were reviewed,
including vanilla HUD and the held log; nine Current artifacts and all source,
Frozen, native, prompt and prepared-input guards pass. This establishes the
scoped atlas-retirement repair, not foil/layout, allocation, performance or
startup acceptance. The outer controller stopped before those later stages
to integrate the newly published resource owners; the combined source needs
fresh checks. Receipts: `build/native-gui-item-templates-atlas-fix/gameplay/`.

Upstream resource ownership is integrated at `6673fac82`; all local source
changes were restored exactly and the four documentation merges were reviewed.
The combined release `e81fc916` builds successfully. Its fresh serial Rust/Java
suites and Frozen entity/range/resource replays run under
`build/native-gui-item-templates-integrated/`. Resource performance uses a strict
Current/Frozen ratio of at most 1.0. Earlier `59e65c7b` evidence and the preceding
entity timing matrix do not establish this combined revision's acceptance.

The combined CPU checkpoint passes Rust 2,649/Java 2,001 (three ignored/two
skipped), Frozen entity/range replays and all source/native guards. All ten
resource records match, but the strict scoped gate fails retagging by 1.3%;
see [resource profiling](../resources/VERIFICATION.md#local-integrated-checkpoint-and-profiling).
The first integrated GUI controller passes the plain-item face-lighting probes;
both original images were reviewed. It then stops in the foil verifier because
the local launch sequence omitted `--world-resource-reload`, leaving the
required observed reload receipt absent. The verifier now returns a failed
report for that condition instead of crashing. This does not relax acceptance;
all four fresh pairs must request actual world reloads. Failed captures remain
under `native-gui-item-templates-integrated-gui-20261011`. Moving foil and
oversized placement did not run. Subsequent native tag-view changes require a
new source/native pin under `build/native-registry-tag-views/`; combined world
runtime, GUI, allocation and gameplay timing acceptance remains pending.

## Current integrated GUI capture boundary

Native `a99b6f8d` passes 2,651 serial Rust tests (three ignored), 2,002
executed Java/parity tests (two skipped), and Frozen entity/range replays.
These full suites precede the capture-only repair below. Plain items and static
foil pass actual world reload and face-lighting/foil comparisons; all four
original images were reviewed. Moving foil failed: Current observed scaled
ticks 126,736 while Frozen observed 340,000, which matches the requested
10,000 phase modulo 330,000. This mismatched-phase image does not establish
a renderer foil defect. Oversized placement did not run.

The external screenshot route omitted the GUI/equipment/wolf readiness checks
already required by Frozen and Current's attachment route. The local repair
checks the normally rendered frame before requesting its screenshot; it changes
neither the animation clock nor the phase window. Ten focused Java controls
pass, including rejection of the failed phase, incomplete item sets and stale
previous-frame observations. The fresh ordinary screenshot route passes moving foil at Current/Frozen
scaled ticks 340,020/340,004 (phases 10,020/10,004); all eight items match
the unchanged phase window and foil pixel checks. Both original images were
reviewed. The oversized transformed/clipped slab placement also passes; its
two originals were reviewed. All four fresh pairs and four Current native
identities pass, with source/Frozen/native/prompt guards and no leftover clients.
Receipts: `build/native-registry-tag-views-phase-fix/gameplay/`.

All 1,178 Rust inputs and the library match the full-suite checkpoint, allowing
guarded reuse of its Rust result. The refreshed full Java/parity suite passes
2,003 executed cases (two skipped, zero failures) for the changed capture class.
All seven world lifecycle cases and both settled Frozen pairs pass with zero
VUIDs; all four originals were reviewed, nine Current artifacts pin this native
image, and source/Frozen/native/prompt/prepared-input guards pass. These settled
checks do not prove first-turn/first-frame or long-session behavior.

The guarded allocation diagnostic passes. With identical inclusive counting of
15-second weighted samples, GUI batch allocation is 184/85.5 MiB before/after,
and the ordinary 3D producer's batch allocation is 134/28 MiB. Source revisions
and frame counts differ: these are not per-frame rates, an isolated speedup
or a p99 cause. All 20 fresh predeclared timing runs are clean and all final
source/native/Frozen/prompt/prepared-input guards pass. All four median FPS
floors pass; only vanilla p99 misses its floor: Current/Frozen 3.357/3.123 ms
despite median average FPS 1,334.3/1,152.5. See the
[complete integrated matrix](GAMEPLAY-PERFORMANCE.md#current-hardware-evidence-boundary).
Allocation and completed timing receipts are under the same phase-fix prefix;
keep every repeat. Preserve the failed pair under
`native-registry-tag-views-gui-20261011`; matching clocks and acknowledged
images are required before accepting moving foil.

# Rust DH box production

Source reviewed at [`b32fd91c`](https://github.com/HungLo2020/MattMC/commit/b32fd91c847f3660bebec4dcc8d268efcdd589c8). Suite results, timings, profiles and hardware/capture checks below are
implementation-author reports. This documentation review inspected source and
fixture assertions; it did not rerun Java/Rust suites, clients, captures or
benchmarks, or independently inspect the untracked runtime receipts.

Ordinary DH builds now produce complete boxes in Rust: top/bottom visibility,
fake-ocean geometry, stock directional shading, neighbor light intervals and
semantic material splits. One synchronous call borrows existing compact
columns and inserts faces directly into the quad owner, including premerging
and coverage accounting. Java no longer constructs interval lists or submits
each resulting face separately.

Java still traverses sources, resolves contextual materials and supplies world
facts. Custom shade implementations retain their ordered callbacks. This is a
step toward moving the complete render-source producer and consumer into Rust;
GPU resources and presentation remain owned by VulkanicGAL.

Implementation: [complete box policy](https://github.com/HungLo2020/MattMC/blob/b32fd91c847f3660bebec4dcc8d268efcdd589c8/src/main/rust/render/dh_quads/boxes.rs),
[vertical policy](https://github.com/HungLo2020/MattMC/blob/b32fd91c847f3660bebec4dcc8d268efcdd589c8/src/main/rust/render/dh_quads/faces.rs),
[borrowed storage bridge](https://github.com/HungLo2020/MattMC/blob/b32fd91c847f3660bebec4dcc8d268efcdd589c8/src/main/java/com/seibel/distanthorizons/core/dataObjects/render/bufferBuilding/NativeDhQuadBuilder.java)
and [box consumer](https://github.com/HungLo2020/MattMC/blob/b32fd91c847f3660bebec4dcc8d268efcdd589c8/src/main/java/com/seibel/distanthorizons/core/dataObjects/render/bufferBuilding/ColumnBox.java).

## Constraints

- Whole-box admission requires explicit `NativeShadeFacts` opt-in. The stock
  provider admits its exact render wrapper, an exact client world (or no world)
  and built-in dimension effects. Other providers must honor that immutable,
  callback-free CPU contract. Sample mode/world/ambient/flat once as
  CPU facts. Unsupported contexts retain their callbacks; do not batch them or query new
  methods on existing custom wrappers.
- Preserve the original shade callback before reading neighbors on that route. A callback
  may mutate adjacent storage; retain the original read/emission order.
  Null neighbors retain their existing unshaded face behavior.
- Preserve point order, covered-light suppression, transparent walls, overhang
  light and different-detail section-edge cave exemptions. The packed
  zero-height predicate tests both height fields together; do not replace it
  with a different geometric predicate.
- Ordered semantic spans retain fallback gaps and their exact material,
  variant and position. Boxes without spans allocate no Java face/interval packet. Semantic gameplay
  can supply spans: its existing Java material-resolution lists remain, and
  the bridge allocates a bounded numeric projection once per box. This is not
  allocation-free construction.
- Canonical exact `LongArrayList` windows and immutable JDK span lists admit.
  Custom getters, malformed extents, more than 4,096 points per neighbor or
  4,096 semantic spans, and subclasses
  retain the compatibility path. Bounds are checked
  before the native call. Native pointers never outlive that call.
- Each build retains two numeric scratch vectors. Neighbor processing swaps
  their packed, aligned interval words instead of copying every interval after
  each split. Ranges entirely outside the original face do no work. They are
  empty after emission and freed with the quad owner; they hold no world,
  Java object, retained source pointer or GPU handle. They can retain capacity
  proportional to the bounded input; this is not long-session memory proof.
- Invalid quad sizes preserve exception classes and already-emitted prefixes.
  Native face insertion shares the existing quad admission, semantic coverage
  and premerge policy. Do not add a second face ledger or transport route.

## Verify

Use the release settings in [quad verification](RUST-DH-QUADS.md#verify).
Run `*NativeDhFacesTest`, `*ColumnBoxSemanticMaterialSpanTest` and
`*NativeDhQuadBuilderTest`. The first starts an isolated JVM so the static
shade wrapper cannot inherit another suite's initialization.

`VerifyDhFaces.java record PATH` must run with untouched Frozen's classpath.
Its fixture records 3,072 actual whole-box results: 384 input scenes, four
shade-failure settings and pre/post greedy output. The recorded neighbors
include null/empty columns, packed empty heights, mixed transparency/materials,
overhangs, repeated intervals and padded array offsets. Six compiled Frozen
class hashes accompany every fixture. Replay checks actual face fields and
callback/exception prefixes; expected policy is not copied.

`VerifyDhFaces.java record-shades PATH` additionally records 48 values from
Frozen's actual client shade getter, with six compiled class pins. It allocates
an unconstructed CPU level for the getter and restores the flat-shading setting;
Frozen source and rendering behavior stay untouched.

The identical compiled driver accepts `bench PATTERN ROUNDS COPIES`.
`--production-facts` selects stock directional shade values on every side and
admits the new whole-box route on Current. This changes the earlier asymmetric
shade workload, so compare all sides again and retain previous results rather
than combining their timings. The proxy represents immutable CPU facts; real
world wrapper costs still need gameplay profiling. It times
whole-box face production, quad merging, count consumption and owner release,
after five warmup rounds. Inputs and views are prepared outside timing. Use
isolated alternating Current/Frozen runs; pin the driver, classpaths and native
images. Compare the predecessor Current image separately to isolate this
change. Count checksums supplement exact replay; timings are CPU component
evidence, not gameplay FPS or pixel parity.

`VerifyDhFaces.py --help` lists the required Java, driver, classpath and native
paths. Use `--reference-kind frozen` for the baseline and a separate
`--reference-kind previous-current --reference-native-dir PATH` comparison.
The optional `--semantic-spans 8` or `32` workload includes packing and splitting
against previous Current only; Frozen predates that material extension.

## Verification checkpoint (October 11, 2026)

The pre-change real-world vanilla+DH CPU profile includes `ColumnBox` in
977 of 15,445 samples and the Java column builder in 1,155 samples. It sampled
active terrain settling, not just the settled view. Receipt:
`artifacts/graphics-captures/goal5/native-dh-handoff-active-dh-cpu-20261011/current/receipt.json`.
These inclusive samples overlap and do not establish an isolated speedup.

The author reports cleaned candidate `0133e073` passing the Frozen callback corpus, 768 complete
native-box replays, 192 current semantic boxes, the actual 48-value shade corpus
and reentrant/custom/closed-owner controls. Full Rust passes 2,616 tests with
three ignored; full Java reports 1,959 with two skips and zero failures/errors
(1,957 executed). All seven native consumers map that exact image. All six
standard-shade CPU comparisons pass: C/F .747/.479/.478 and versus previous
Current .485/.445/.454 for simple/mixed/dense workloads. The empty-span timings retain their exact compiled driver under `empty-span-driver/`.
Six additional previous-Current comparisons include semantic packing and
splitting: eight spans give .889/.905/.684 ratios; 32 spans give .702/.536/.459.
Frozen predates that semantic extension, so these are explicitly predecessor
comparisons. All counts match. The same real DH workload also observes
`mattmc_dh_box_add` in 213 of 14,894 samples and no Java `applyLightToRange`;
it maps this exact image, exits cleanly and passes guards on the host with the
restored NVIDIA driver.
That active profile is diagnostic, not an isolated speedup or FPS comparison. Fresh
hardware verification passes seven lifecycle cases and both settled image pairs
(vanilla and Iris+DH), with zero reported validation errors. Nine Current artifacts
fingerprint this exact image, and all four original 1280×720 images were reviewed.
Mean RGB differences are .259/.445/.499 (vanilla) and 3.668/4.169/3.856 (combined);
existing color/lighting differences remain. Receipts:
`artifacts/graphics-captures/validation/native-dh-box-correctness-20261011/summary.json`.
All twenty fresh alternating 6,000-frame timings pass health/input guards and
every measured FPS/p99 floor. The [box-specific gameplay matrix](GAMEPLAY-PERFORMANCE.md#october-11-complete-dh-box-matrix)
records all medians and protocol limits; it does not isolate this component's
FPS contribution. The earlier handoff matrix remains historical. Receipts:
`build/native-dh-faces/`. Initial recorder setup, invalid test-span and unsupported test-subclass failures
are retained alongside corrected checks. `ColumnArrayView` is final; custom
backing lists instead exercise admission decline before getter calls/mutation.

The first six-pair CPU matrix regresses two workloads versus Frozen and the
preceding Current image; the dense case improves. Those failed timings and
their exact native image remain under `initial-timings/`. Profiles led to
packed interval storage, rejection of external ranges and a scalar native
specialization for faces with no borrowed inputs. These per-face bridge
experiments were superseded by whole-box production and removed; retained
custom callbacks use the preceding producer path. The packed revision improves the dense case by 26% versus its predecessor, but
its simpler cases remain 2.8% and 3.6% slower; Frozen ratios are 1.541/1.099/.771.
All six comparisons and the packed native image remain under `packed-timings/`.

The retained implementation owns the entire box producer in one native
operation. Stock worlds supply mode/world/ambient/flat scalar facts; no Iris GPU
state is borrowed. Custom wrappers and unsupported neighbor windows retain the
existing path. CPU component gains and settled captures do not establish
first-frame, temporal, sustained-streaming or long-session memory acceptance.


## Source review limits

The [Java verifier](https://github.com/HungLo2020/MattMC/blob/b32fd91c847f3660bebec4dcc8d268efcdd589c8/src/test/java/com/seibel/distanthorizons/core/dataObjects/render/bufferBuilding/NativeDhFacesTest.java)
separates 3,072 Frozen callback-route records from 768 no-injected-failure
complete native-box comparisons and 192 Current semantic controls. The 48-value
shade corpus exercises the actual getter on an unconstructed CPU level, not
48 rendered worlds. Replay checks embedded class-hash string lengths; it does
not recompute the Frozen class hashes. Retained custom callback failure/reentry
controls therefore must not be described as failure injection into every
admitted callback-free whole-box route.

Canonical storage windows and immutable semantic spans are synchronously
borrowed; Java traversal/context resolution still prepares them. Scratch may
retain capacity until the quad owner closes. CPU fixture equality and sampled
native calls do not establish concurrent mutation, cancellation/teardown timing,
first-frame geometry or a process-wide memory bound.

The [timing runner](https://github.com/HungLo2020/MattMC/blob/b32fd91c847f3660bebec4dcc8d268efcdd589c8/DevUtils/tests/rendering/VerifyDhFaces.py)
enforces a median-at-most-reference exit gate, compares count checksums and
rehashes selected driver/native files. It does not itself hash every source,
classpath entry or Frozen inventory, and its count checksum is weaker than
exact face/semantic equality fixtures. Keep full author guard receipts and
actual gameplay/profile evidence separate from this synthetic command.

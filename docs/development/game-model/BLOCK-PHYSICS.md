# Native block physical settings

> Current implementation; the author's recorded focused correctness checks
> passed. The full performance target remains unmet.

## Ownership and use

Rust's [physical definition owner](https://github.com/HungLo2020/MattMC/blob/1b1837931a9b25fa12b655e440c6ff77cdd5887d/src/main/rust/content/block/definitions/physics.rs)
declares five physical floats
(hardness, resistance, friction, speed factor and jump factor), twelve flags
and piston push reaction for every registered block. The ordered catalog
selects one of 181 immutable configurations for each of its 1,235 entries.
Sharing physical settings is independent of sharing state domains: air and
stone have the same empty state domain but different physical settings.

Native consumers read `definitions::registry().definition(block_id).physics`.
The data has process lifetime, no world references and no rendering resources.
Hot consumers can read their existing compact state columns; air and occlusion
flags now derive from native physical definitions when those columns freeze.
Java's remaining-fact packet rejects attempts to supply either flag. Other
state flags still pass through Java's per-state evaluation, including the
random-tick result; owning a physical setting does not migrate every derived
or overridden state query.

`NativeBlockDefinitions` borrows bounded metadata buffers and creates one
temporary Java view per configuration. `BlockBehaviour` applies the native
settings before block and state constructors cache values. Ordinary gameplay
getters keep local reads and do not make FFM calls. Registered definitions
override constructor-supplied physical values; unregistered codec/test objects
retain their explicit Java settings while those APIs migrate.

Java's registered catalog no longer supplies 2,229 scalar configuration calls.
Factories and family constructors remain transitional; their physical values
cannot override native registered definitions. [Intrinsic state rules](BLOCK-INTRINSICS.md)
now also own semantic map colors, emission and fluid associations. Sounds,
instruments, shapes, contextual predicates, offsets and gameplay hooks remain
separate migration work. This slice does not establish complete block
behavior migration or an isolated frame-rate improvement.

## Editing and verification

Edit the physical configuration selected in `catalog.rs`, or define a new
configuration in `physics.rs`. Shared changes affect every selecting block.
Keep exact float values, negative unbreakable hardness and independent force-
solid flags; do not normalize these into guessed material categories.
The profile list is finite and static, with no runtime-input cache.

Run the content tests and `NativeBlockPhysicsTest` plus the registry/state,
fluid, meshing and chunk-save tests described in [block definitions](BLOCK-DEFINITIONS.md).
The Java regressions exercise the `BlockBehaviour` constructor directly,
proving native settings win before its caches are assigned and preserving the
unregistered compatibility path. They do not create intrusive registry holders
after bootstrap has frozen block registration.

Version 5 of `DevUtils/tests/content/VerifyStateGraphs.py` compares all 18
physical fields against untouched Frozen, including raw float bits,
constructor-cached values and seven additional cached physical facts for every
block state. It retains the graph, fluid, property and per-state
fact digests. Run it with a new output directory and no competing workload;
follow with real client lifecycle, image and performance verification. The
[observer limits](STATE-GRAPHS.md#verification) distinguish semantic checks,
JVM main-thread allocation, reference-build provenance and performance gates.

## Current verification

These author-recorded results describe the physical-settings milestone present
at milestone commit
[`1b183793`](https://github.com/HungLo2020/MattMC/commit/1b1837931a9b25fa12b655e440c6ff77cdd5887d).
This documentation review did not rerun the suites or inspect local receipts.
They predate the [intrinsic state-rule milestone](BLOCK-INTRINSICS.md), whose
separate results cover map colors, emitted light and fluid associations.

The integrated release build, 18 native content tests and 24 Java
ownership/registry/graph/codec/save/meshing tests pass. Five fresh-JVM pairs
against untouched Frozen match all five semantic digests, including every
physical setting and the cached state facts. Receipt:
`build/block-physics-master-verification-20261008/results.json`.
Bootstrap medians were Current 2.279/Frozen 2.263 s (+0.7%); JVM main-thread
allocation 959.19/1066.06 MB (−10.0% for the combined content migration). This does not
establish an isolated physics or gameplay speedup.

Full `validation/native-block-physics-master-20261008/` verification passed
1,701 Java tests (2 skipped), 2,357 Rust tests (3 ignored), all seven lifecycle
scenarios and both visually reviewed coast pairs. Vanilla mean RGB differences
were 0.210/0.355/0.390; Iris+DH 3.726/4.268/3.871, with the DH subset passing
and no Vulkan validation errors. Static captures do not prove broad movement
parity or every gameplay behavior.

All 16 ABAB/6,000-frame performance runs were clean. Average FPS versus Frozen
was −7.9% vanilla, −8.0% DH, +6.4% shaders and +10.9% shaders+DH. Vanilla's p99
passed; the other three modes failed their p99 comparisons. `SUMMARY.md`
records both repeats and tail values. No isolated physics performance gain is
claimed; these gaps guide further ownership migration and remain open.

Source/native hashes match the five-pair observer, Frozen is unchanged, and
25 generated fixture copies were retired. Receipt:
`build/block-settings-migration-draft/runtime-integrity.json`.

Pre-integration/reference receipts and source hashes remain under
`build/block-settings-migration-draft/`; applied staging sources/classes/binaries
were retired after verification. Extraction files are historical evidence only,
never production inputs.

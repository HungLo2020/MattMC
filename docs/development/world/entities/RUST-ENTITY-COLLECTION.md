# Native world entity collection

**Implemented; scoped verification completed on 2026-10-13. Frozen component
CPU gaps and broader migration acceptance remain open.** This extends the retained [section index](RUST-ENTITY-SECTION-INDEX.md)
and [member storage](RUST-ENTITY-SECTIONS.md); entity simulation remains Java.

The existing gameplay profile in `build/native-frame-state-profile/targets.json`
records 63 sampled selector stacks through `Level.getEntities`. It identifies a
migration target, not a measured gain from this implementation.

## Ownership and callback boundaries

`Level.getEntities` delegates result collection to its entity getter. For the
stock `NO_SPECTATORS` selector and ordinary storage, Rust scans retained section
and member state and returns bounded batches across multiple sections. Java
projects those identities into the caller's result list and excludes the
requested entity. No entity array is reconstructed and sent back to Rust.
Dragon-part enumeration retains its existing order after ordinary entities.

- Membership registration admits only classes inheriting `Entity.isSpectator`
  unchanged. Their result is the stock constant false. Player game modes and
  other overrides remain ordered Java callbacks; there is no mirrored flag
  updated each frame or tick.
- Native collection stops before the first custom bounds or spectator callback,
  returning the original member cursor and modification count. Java finishes
  that section with the existing mutation-aware iterator before Rust opens
  future sections. Do not prefetch across that boundary.
- Native results are projected before any callback or deferred traversal error.
  Preserve the collected prefix when a later coordinate range fails. Only an exact Java
  `ArrayList` is admitted: an overridden `add` could mutate later entities.
  Custom getters, storage, sections and predicates retain their dispatch.
- Each pooled spatial cursor lazily owns a 128-pair output buffer. Reopening
  clears its continuation position; nested queries use separate cursors.
  Native bindings remain generational, and no native lock spans Java callbacks.

Implementation: [native collector](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/entity_sections/collection.rs),
[Java cursor](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/entity/NativeEntitySectionIndex.java)
and [production caller](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/Level.java).

## Screen-shake source selection

Screen-shake selection now filters immutable capability inside the retained
Rust membership query. `GameRenderer` requests source identities through
`Level.getScreenShakeSources`; it no longer projects all ordinary nearby entities
into a temporary Java list before rejecting their capabilities. The numeric
shake reducer, effect callbacks, random offsets and pose application remain Java.
This is source-selection ownership, not completion of camera-effect migration.

The latest diagnostic gameplay profile at `b48b9eb7d`'s source contains 64
render-thread collector samples; truncated stacks do not establish the exact
caller distribution. Inspection identifies screen shake as a per-frame consumer
that needs only a small capability subset. Profiling identifies avoidable work;
it does not establish a speedup for this candidate.

- Registration publishes immutable capability bits alongside the retained
  geometry binding; there is no per-frame class scan or duplicated member list.
  Bit 0 admits the stock non-spectator result; bit 1 identifies shake sources.
- Check original bounds and spectator behavior before filtering capabilities.
  Rejected entities can still mutate later members, throw or insert future
  sections. Native traversal must yield before those callbacks.
- Complete collection before evaluating any shake distance/intensity callback.
  Preserve source order, duplicates, camera exclusion and dragon-part getter
  ordering. Subclassed worlds, getters and storage retain original dispatch.
- Capability bits belong to a generational membership binding and are cleared
  on slot reuse. They do not change entity simulation or GPU ownership.

Eight focused Java integration tests and 30 native membership tests pass, including
production world queries, rejected-entity callbacks and custom getter dispatch.
The actual-Frozen corpus matches all 7,186 cases, including 300 new source-selection
cases with ordered callback and mutation traces. Source/class/native guards pass.
Native `5e09c034` also passes the nine-JVM component comparison with source,
class, native identity and output guards. Six measured-JIT samples were excluded
and retained. The full check passes 2,785 Rust tests (three ignored) and 2,133
Java tests (two skipped), including parity and slow suites. The scoped runtime
checkpoint below also passes.
Each sample includes 4,096 result allocations, collections and the original
consumer's capability loop. There are 64 warmups and eight compiler-quiet samples
per workload in three interleaved JVMs per side. The control uses the previous
Current non-spectator collector on candidate storage; Frozen is unchanged.

| Sources / layout | CPU ms, Current / previous consumer / Frozen | Java heap MB, Current / previous consumer / Frozen |
| --- | --- | --- |
| None, 256 sections | 34.80 / 43.28 / 28.51 | 0.098 / 6.521 / 18.874 |
| Every 16th, 256 sections | 35.61 / 43.78 / 29.32 | 0.426 / 6.521 / 18.874 |
| Every entity, 256 sections | 42.18 / 43.17 / 37.75 | 6.521 / 6.521 / 18.874 |
| None, one section / 128 entities | 2.138 / 5.562 / 2.419 | 0.098 / 2.261 / 8.749 |
| Every 16th, one section / 128 entities | 2.373 / 5.836 / 3.577 | 0.328 / 2.261 / 8.749 |

Scattered no-source CPU improves 19.6% against the previous consumer, with 98.5%
less Java allocation. Dense no-source CPU is 11.6% below Frozen. Scattered and
one-entity query CPU still lag Frozen; the one-entity no-source case measures
0.468/0.466/0.071 ms Current/control/Frozen. These scoped improvements do not
establish a gameplay gain or native/total memory bound. Callback-heavy cases,
all raw samples and identity receipts are in `build/native-screen-shake/measurement-1/`.

```sh
python3 DevUtils/tests/world/entity/VerifyEntityCollection.py \
  --java-home /path/to/jdk25 --output build/screen-shake/measurement --screen-shake
```

### Screen-shake runtime checkpoint

One fresh 6,000-frame pair per mode on native `5e09c034`:

| Mode | FPS, Current / Frozen | p99 ms, Current / Frozen | Peak RSS GiB, Current / Frozen |
| --- | --- | --- | --- |
| Vanilla | 1289.3 / 1190.4 | 3.332 / 3.787 | 5.03 / 4.37 |
| Shaders | 359.3 / 318.5 | 4.714 / 7.190 | 5.82 / 6.16 |
| DH | 814.9 / 684.8 | 3.826 / 6.050 | 6.04 / 5.99 |
| Shaders + DH | 275.9 / 227.9 | 5.363 / 8.484 | 8.48 / 7.64 |

All scoped FPS/p99 floors pass. Saved inputs and live JVM controls match;
entity counts are 190/181, 184/182, 167/166 and 165/167 in table order.
Single pairs do not establish an isolated gain or sustained acceptance. RSS
covers short client sessions; no GPU-memory metric or long-session bound is proven.

Vanilla and shader+DH settled pairs and three lifecycle checks (world reload,
resource reload, view-distance reduction) pass with zero Vulkan validation
errors. All seven original images were viewed: coast pairs closely align;
heavy blue haze limits the three lifecycle initial images to structural evidence.
These checks do not close first-turn pop-in/flicker, first shader-world
presentation, broad entity/rig visuals or visible active-dinosaur shake coverage.
The numerical shake behavior is unchanged; source-selection oracle results
must not be reported as complete camera-effect or gameplay parity.

Final source/class/native/Frozen/input/JVM guards pass. Five presenter
correlations and nine native identities match. Thirteen completed test-world
copies were retired, preserving originals, logs, samples and identity receipts
under `build/native-screen-shake/` and
`artifacts/graphics-captures/validation/native-screen-shake-20261013/`.
Subsequent measurements below are historical collector checkpoints.

## Verification commands

Use JDK 25 and build the current native library before the Frozen observer:

```sh
./gradlew -PmattmcRustProfile=release test \
  --tests '*NativeEntityCollectionTest' --tests '*EntitySectionStorageNativeTest'
python3 DevUtils/tests/world/entity/VerifySectionMembers.py \
  --java-home /path/to/jdk25 --output build/entity-collection/oracle
```

Native `6ba548a6` passes the full check: 2,783 Rust tests (3 ignored) and
2,126 Java tests (2 skipped), including five production collection integration
tests. The final Frozen replay (`oracle-3`) matches all 6,886 cases with
source/class/native guards. Scoped gameplay and lifecycle evidence is recorded below.

The observer compares actual Current/Frozen getter collection, including
multiple native batches, spectator overrides, bounds changes, nested queries,
future-column insertion, null visibility and failed mutation prefixes. Source
and component checks supplement real gameplay; they do not establish broad
rendering parity or long-session memory bounds.

The component driver is `DevUtils/tests/world/entity/VerifyEntityCollection.py`
with the same JDK/output arguments. It compares unchanged Frozen, Current, and
the previous Current getter/callback sequence on candidate storage. Each case
uses 64 warmups and eight compiler-quiet samples in three interleaved JVMs per
side; measured-JIT samples are retained separately, with at most 16 attempts
per case. Initial whole-run rejections remain in `measurement-1` and
`measurement-2` under `build/native-entity-collection/`.

The initial collector lost a successful prefix before a later section-coordinate
range failure; actual Frozen case `collection-range-failure-prefix` reproduces
that difference in `oracle-2`. The corrected protocol defers the error until
after projection. Native batch counts also reserve result capacity once, only
after the first non-excluded match; all-excluded batches leave the list untouched.


## Component measurements

Final `build/native-entity-collection/measurement-4/` passes source/class/native
identity, output identity/order and compiler-quiet checks. Four measured-JIT
samples were excluded from medians and retained separately. Each sample runs
4,096 collections, including result-list allocation and projection. The control
uses the previous Current getter/callback sequence on candidate native storage;
it is supplemental evidence, not a substitute for Frozen.

| Workload | CPU ms, Current / previous consumer / Frozen | Java heap MB, Current / previous consumer / Frozen |
| --- | --- | --- |
| 256 sections, no hits | 33.59 / 35.81 / 21.08 | 0.098 / 0.262 / 0.360 |
| 256 sections, all hits | 41.84 / 67.65 / 26.31 | 6.52 / 18.78 / 18.87 |
| 256 sections, callback every 16th entity | 44.01 / 67.86 / 25.98 | 13.86 / 18.78 / 18.87 |
| One section, 128 matches before exclusion | 5.18 / 5.13 / 2.34 | 2.26 / 8.65 / 8.75 |

Large ordinary all-hit CPU improves 38.2% versus the previous Current consumer,
with 65.3% less Java allocation. The dense single-section case remains about
1% slower than that control, despite 73.9% less Java allocation. Frozen CPU
remains faster, including small queries: the one-entity no-hit case takes
0.474/0.476/0.071 ms. These gaps guide further ownership work. Java allocation
does not measure native/total memory, and no isolated gameplay gain is claimed.

`measurement-3` records the collector before reserving Java projection capacity:
large all-hit CPU42.27 ms and Java allocation18.61 MB. `oracle-2` retains the
failure-prefix mismatch. Keep these rejected/intermediate receipts distinct
from the final candidate.

## Current gameplay checkpoint

One fresh 6,000-frame Current/Frozen pair per mode on native `6ba548a6`:

| Mode | FPS, Current / Frozen | p99 ms, Current / Frozen |
| --- | --- | --- |
| Vanilla | 1246.7 / 1213.6 | 3.474 / 3.935 |
| Shaders | 360.1 / 319.1 | 4.225 / 7.146 |
| DH | 802.9 / 661.1 | 3.877 / 6.265 |
| Shaders + DH | 280.3 / 227.9 | 5.742 / 8.583 |

All scoped FPS/p99 floors pass. Saved inputs and live JVM controls match;
live entity counts differ: 187/184, 190/179, 166/167 and 165/166 respectively.
Single pairs do not establish an isolated gain or sustained performance
acceptance. Vanilla and shaders+DH settled comparisons, world unload/reload,
resource reload and view-distance decrease pass with zero Vulkan validation
errors. Seven original images were viewed; five Current presenter correlations
and nine native identities match. The paired coast views closely align, while
heavy blue haze limits all three lifecycle images to structural evidence.
These checks do not establish broad entity visuals, first-turn continuity,
first shader world presentation or long-running memory behavior.

Final source/class/native/Frozen/input/JVM guards pass. Thirteen completed
copied worlds were retired, preserving original captures, logs and failed
attempts under `artifacts/graphics-captures/validation/native-entity-collection-20261013/`.
Receipts: `build/native-entity-collection/{runtime,gameplay-comparison,
image-review,final-integrity,retention}.json`.

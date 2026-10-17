# Retained client chunk ranges

The client residency owner now indexes immutable stock chunk coordinates in
Rust as slots are published or dropped. Block-entity extraction and the moving
piston scan select loaded chunks directly from this index instead of looking
up every coordinate in a Java render-distance square. Java still owns chunk
objects, block-entity maps, renderer callbacks and radius relocation.

## Callback and lifetime constraints

- Preserve z-major/x-major order and the existing ring-slot, coordinate and
  active-window checks. Moving the center does not evict physical slots.
- Publish coordinates with the slot before unload callbacks. Failed unload
  retains the same published prefix as ordinary `getChunk`.
- Selection borrows at most 64 loaded-slot entries per native call. It does not
  reconstruct a chunk array in either language or retain Java/GPU addresses.
- A mutation revision invalidates unread entries between callbacks. A radius
  change rebinds to the current storage before selecting the next coordinate.
  Insertions behind the cursor remain behind it, as in the original scan.
- Any custom/unknown chunk binding makes the owner decline indexed selection
  until that binding is removed. Custom chunks keep live coordinate getters;
  custom cache classes keep virtual `getChunk` calls in the original scan.
- Nested readers use independent scratch pages. Exceptions return scratch in
  `finally`; each thread retains at most four 1,552-byte page buffers.
- No native lock or borrowed output crosses a renderer/world callback.
- Use the existing client thread for selection and chunk publication. The
  Rust lock does not turn Java reference publication and native notification
  into a transaction for arbitrary concurrent callers.

Implementation at source `f92ea29d`: [range index](https://github.com/HungLo2020/MattMC/blob/f92ea29dde0199a9ea65006e216ac92570b3b02b/src/main/rust/world/level/client_residency/range.rs),
[client consumer](https://github.com/HungLo2020/MattMC/blob/f92ea29dde0199a9ea65006e216ac92570b3b02b/src/main/java/net/minecraft/client/multiplayer/ClientChunkCache.java)
and [render extraction](https://github.com/HungLo2020/MattMC/blob/f92ea29dde0199a9ea65006e216ac92570b3b02b/src/main/java/net/minecraft/client/renderer/LevelRenderer.java).
The [residency guide](RUST-CLIENT-RESIDENCY.md) covers remaining object bindings,
empty-set escape and callback ordering.

## Verify

This documentation review inspected source and verification drivers at
`f92ea29d`; it did not rerun the suites, replays or hardware captures. Results
below are author-recorded checkpoints with their own native identities.

Use Linux x64, JDK25 and a release native library. The driver checks
`/proc/self/maps`, expects Frozen at `../MattMC_JavaPerfTesting/MattMC`, and
asserts its recorded HEAD and the protected prompt hash. The recorder compiles separately against
Current and untouched Frozen, and executes Frozen's actual `getChunk` and
storage transitions. It compares complete ordered selections and callback
prefixes over 645 cases: dense/sparse windows, negative/world-border positions,
stale physical slots, replacements/drops, center/radius changes, nested readers
and exceptions. Source, class, native image and protected-prompt guards pin the
comparison. Java controls also cover dynamic custom getters and page retirement.
The [range driver](https://github.com/HungLo2020/MattMC/blob/f92ea29dde0199a9ea65006e216ac92570b3b02b/DevUtils/tests/world/chunk/VerifyClientChunkRanges.java)
allocates stock chunk fixtures with empty section arrays and a custom world's
no-op unload callback. It exercises the actual selection/storage methods, not
normal chunk construction, retained empty-counter admission or the renderer's
block-entity callbacks. Its throwing case stops the range consumer; it does
not verify an unload-callback failure.

```sh
CARGO_PROFILE_RELEASE_DEBUG=line-tables-only CARGO_PROFILE_RELEASE_STRIP=none ./gradlew -PmattmcRustProfile=release buildRustNative classes
python3 DevUtils/tests/world/chunk/VerifyClientChunkRanges.py --java-home "$JAVA_HOME"
./gradlew -PmattmcRustProfile=release test parityTest -x testRustNative
python3 DevUtils/RunWiki.py check
```

The first range candidate (`77126827`) passes 2,627 Rust tests (three ignored),
1,982 reported Java tests (two skipped), all 645 Frozen cases and four new Java
range controls. Seven hardware lifecycle cases and two settled image pairs
pass with clean validation; all four original images were reviewed. Paired
shader+DH CPU profiles pass their guards and sample 51 native range calls in
Current. Compiler activity remains; this is admission evidence, not an isolated gain.

The initial 20-run comparison is clean but fails its vanilla p99 floor
(Current 3.338 ms / Frozen 3.074 ms). Other FPS and p99 floors pass. That failed
aggregate and every repeat remain in `build/native-client-ranges/gameplay/`.
The subsequent change removes unused ordinary entity-readiness queries from
the semantic extraction stream. Falling-block observers and custom renderer/world
callbacks still query readiness; loading and shadow eligibility retain their
meaningful checks. Four functional extraction controls and the full Java suite
pass (1,984 executed, two skipped); a fresh 645-case Frozen range replay passes.
The revised hardware comparison passes all seven lifecycle cases and two
reviewed settled image pairs with clean validation. Its 20 declared timing runs
are clean, but vanilla p99 still misses (Current 3.217 ms / Frozen 3.124 ms);
all other FPS/p99 floors pass. Paired warmed vanilla allocation profiles pass
their source/native/process guards (20,000 warmup, 60,000 measured frames;
15-second weighted samples at 512 KiB). Current samples include repeated GUI
batch records and inflated entity bounds. Absolute sampled bytes and different
profiled frame counts do not establish an isolated regression or p99 cause.
These historical matrices do not isolate this migration's FPS contribution.
The [current combined checkpoint](../../rendering/GAMEPLAY-PERFORMANCE.md#current-hardware-evidence-boundary)
records newer verification. Initial receipts remain in `build/native-client-ranges/`; revised
receipts go in `build/native-client-ranges-readiness/`. Broad gameplay, temporal/streaming and long-memory
acceptance remain open. The predecessor vanilla+DH profile's 20 inclusive
block-entity extraction samples do not identify this as the largest frame cost.

Completed generated timing/profile worlds are retired after receipt review.
Every failed performance repeat and both allocation profiles remain. The
validation runner retired the superseded first range correctness capture tree;
its small review receipt retains image hashes, while the revised originals remain.

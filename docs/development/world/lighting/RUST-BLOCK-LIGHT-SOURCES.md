# Rust block-light sources

Canonical `BlockLightEngine.propagateLightSources()` now enumerates block
emitters and appends their entries entirely in Rust. Java keeps the initial
`setLightEnabled` and contextual chunk lookup in their original order. It then
passes the existing retained CPU section binding and pending queue owner;
it constructs no block-state array, emitter list or per-emitter callback.

Implementation: [source policy](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/lighting/propagation/sources.rs),
[packed-word reader](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/chunk/live/mod.rs)
and [Java adapter](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativeLightSources.java).
The general `NativeChunkInputs` adapter reuses the
[existing shared bindings](../chunk/RUST-DH-CHUNK-INPUTS.md). Its rename changes
neither admission nor cache lifetime; there is no new mirror or registry.

## Contracts

- Preserve section/Y/Z/X order, all entry bits, empty-shape flags and append
  order relative to existing work. The decrease queue is untouched.
- Ignore section emptiness counters when enumerating emitters. Raw palette
  writes can leave a counter stale-empty; the original `findBlocks` still
  sees those states. An unused emitter palette entry can cause a scan but
  must not emit a source for an air cell.
- Admit every section's palette before changing pending work. Custom states
  and unknown IDs retain contextual Java enumeration without a native prefix.
  Unused custom palette entries also decline: the original `maybeHas` visits
  palette entries before scanning cells.
- Read current primary owners after palette growth or section replacement.
  The word visitor decodes each packed word once, ignores padding, and emits
  no intermediate state array. Zero-width single values are captured once.
- Keep original caller exclusion. Section mutation locks do not create an
  atomic whole-chunk snapshot. No Java callback runs while native locks are
  held; the stateful producer uses an ordinary downcall and fenced CPU leases.
- Generic/custom readers, debug chunks and unsupported bindings keep their
  original virtual callbacks. Node checks and publication scheduling remain
  Java. This is world lighting and has no GPU/GAL or presenter ownership.

## Verify changes

Use JDK25 and one release library for Java/runtime measurements:

```sh
./gradlew -PmattmcRustProfile=release testClasses -x testRustNative
./gradlew test -x buildRustNative -x testRustNative --tests '*NativeBlockLightSourcesTest' --tests '*NativeLightPropagationTest' --tests '*NativeBiomeWorldTest'
JAVA_HOME=/path/to/jdk25 python3 DevUtils/tests/lighting/VerifyLightWork.py --output build/block-light-source-oracle
./gradlew testRustNative
python3 DevUtils/RunWiki.py check
```

Focused Rust controls check order, entry bits, rejection before partial enqueue
and word visits across single/local/global widths and padding. Java compares
the producer directly with `findBlockLightSources`, including raw stale-empty
counter writes, palette growth, section replacement and pending queue contents.
The propagation suite covers saved/generated terrain and contextual wrappers.
The actual unchanged Frozen observer compares full light state after every
pass and requires the native block producer to be exercised on Current.
Run Java suites separately from capture cleanup, which can terminate unrelated
Gradle wrappers; see [work-queue verification](RUST-LIGHT-WORK.md).

## Current verification

Candidate `8aaa5520` passes 42 Rust lighting checks and the packed-word visitor
control, plus 16 focused Java tests. Actual unchanged Frozen matches all 492
passes and 437,089 state rows with source/native/class guards intact. Current
seeds all 54 ordinary chunks directly in Rust. Two alternating JVM pairs against
the published predecessor measure terrain 22.042/19.258 ms, edits
10.737/10.695 ms and nether 8.406/6.879 ms (predecessor/candidate).
Terrain improves 12.6% and nether 18.2%; edits are effectively unchanged.
Checksums and compiled/native identity guards match. These are warmed lighting
workloads, not whole-game or Frozen throughput claims.
The source-only candidate's supported Rust suite passes 2,714 tests (three ignored), and Java unit
and parity suites pass 2,031 (two skipped). Seven lifecycle cases and settled
vanilla/Iris+DH pairs pass with zero validation errors and nine exact native
identities. Eleven originals were reviewed; hazy lifecycle views establish
structural checks only. Both ordinary flights cross six chunk columns and
return to matching start positions, but forward endpoints differ by 0.54 blocks.
All six endpoint originals were reviewed; this does not establish matching-pose
pixels, absence of transient gaps or long-session memory bounds.
The matched whole-game matrix stopped on its first DH run: the RSS guard
triggered around 12.4 GiB before any measured frames. Diagnostic runs completed
near 8.9 GiB, and the game-only allocation profile identifies per-point DH
saved-column resizing/view construction as major FFM churn. A
[native bulk-resize prerequisite](../chunk/RUST-DH-FULL-DATA.md#constraints-when-changing-this-subsystem)
is now included in combined candidate `3c8e2d3d`. Its supported Rust suite
passes 2,714 tests (three ignored), and Java unit/parity suites pass 2,032
(two skipped). The actual Frozen observer still matches all 492 passes and
437,089 state rows, with 54 native block-source admissions.
Two completed DH diagnostic sessions stay below the RSS guard at 8.51/9.07 GiB.
Game-only JFR weighted allocation below mutable-column add/view refresh drops
from 922 to 158 MB. Background DH work varies between recordings; this is
directional allocation evidence, not a retained-memory or normalized speedup
claim. Earlier CPU/runtime results verify `8aaa5520`, not the combined memory
fix. Combined runtime verification passes seven lifecycle cases and settled
vanilla/Iris+DH comparisons with zero validation errors. All eleven originals
were reviewed and nine Current artifacts identify `3c8e2d3d`. The lifecycle
views remain heavily hazy and provide structural evidence only; the settled
coastal pairs align with unchanged Frozen. Moving-camera and repeated whole-game
performance checks are separate, ongoing acceptance work.
Receipts:
`build/native-light-seeds/`. These CPU results do not establish whole-game
speedup, broad rendering parity or long-session memory acceptance.

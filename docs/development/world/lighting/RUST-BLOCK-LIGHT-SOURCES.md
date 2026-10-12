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

Combined native `3c8e2d3d`, published in `8e16a569c`, passes the supported
Rust suite: 2,714 tests (three ignored). Java unit/parity suites pass 2,032
(two skipped). Actual unchanged Frozen matches all 492 lighting passes and
437,089 state rows with source/native/class guards intact; Current seeds 54
ordinary chunks directly in Rust.

Two alternating JVM pairs for source-only `8aaa5520` against its published
predecessor measure terrain 22.042/19.258 ms, edits 10.737/10.695 ms and nether
8.406/6.879 ms (predecessor/candidate). Terrain improves 12.6% and nether 18.2%;
edits are effectively unchanged. Checksums and compiled/native identity guards
match. These warmed lighting results do not isolate a whole-game gain from
the combined candidate.

The source-only whole-game matrix stopped on its first DH run at about
12.4 GiB RSS before measuring frames. The failed recording remains retained.
Game-only JFR identified per-point DH hydration/view creation as major FFM
churn. After the [bulk-resize fix](../chunk/RUST-DH-FULL-DATA.md#constraints-when-changing-this-subsystem),
weighted allocation under add/view refresh drops from 922 to 158 MB.
Background DH work varies; this is directional allocation evidence, not a
normalized speedup or retained-memory bound.

Combined runtime verification passes seven lifecycle cases and settled
vanilla/Iris+DH comparisons with zero validation errors. Eleven originals were
reviewed and nine Current artifacts identify `3c8e2d3d`. Hazy lifecycle views
provide structural evidence only; the settled coastal pairs align with Frozen.
Both ordinary flights cross six X chunk columns and reach matching recorded
poses, including a shared small return overshoot. All six endpoint originals
were reviewed; endpoints do not resolve transient gaps or long-session memory.

All twenty matched 6,000-frame timing runs complete cleanly with identical
live JVM controls. All p99 floors and vanilla/shader/combined FPS floors pass;
DH FPS remains 8.2% below Frozen, so aggregate performance acceptance fails.
Four Current DH sessions peak at 8.52–9.12 GiB RSS; two shader+DH sessions peak
at 9.53/9.92 GiB, all below the guard. Final source/native/class/Frozen/prompt
and prepared-input guards pass, with no clients remaining. See the
[complete matrix](../../rendering/GAMEPLAY-PERFORMANCE.md#block-light-sources-and-bulk-dh-resize).
Receipts: `build/native-light-seeds/combined/`; source-only diagnostics and
predecessor timings remain in `build/native-light-seeds/`. Broad rendering,
first-world-frame, transient-gap and sustained-memory acceptance remain open.

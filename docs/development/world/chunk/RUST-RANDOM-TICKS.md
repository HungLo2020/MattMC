# World random tick selection

**2026-10-13 checkpoint: scoped correctness/runtime checks pass; dense CPU gap remains open.**

Rust owns each world's random-position sequence and random-tick candidate
selection in
[`world/level/random_ticks.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/random_ticks.rs).
`Level.getBlockRandomPos` consumes that same sequence for precipitation and
thunder. `ServerLevel.tickChunk` selects directly from the
[primary live section](RUST-LIVE-SECTIONS.md), without exporting block arrays.
Rust also traverses retained chunk sections and their ticking counters. Java
retains weather, behavior callbacks and general simulation. Read-only chunk
packet, light-initialization and save consumers borrow section arrays without
marking them as escaped; actual mutable-array callers still invalidate that
authority.

## Callback and lifetime rules

- Preserve signed Java integer overflow, arithmetic right shifts, coordinate
  masks and the exact number/order of random draws. The sequence is primary
  Rust state; there is no Java seed mirror.
- A selection skips only states whose installed policy proves that neither
  block nor fluid has a random-tick callback. Unknown/custom policies return
  to Java. Native work is bounded to 4,096 attempts per call.
- Return before each behavior callback. No palette lock survives that return;
  subsequent selection reads current storage again. Callbacks may
  grow/replace palettes, use the shared position sequence, throw, or recursively
  tick another section. Never precompute candidates across those calls.
- The original section reference stays captured for its inner loop, even if
  a callback replaces its chunk-array slot. Later sections read the live array.
  Section tick counters are checked once when entering that section.
- Run the selected block callback before obtaining its fluid. Obtain the fluid
  from that selected state, not from a new world lookup after the callback.
- Custom level/section methods and active profilers retain the original Java
  lookup/callback order. In particular, a profiler's `push` can run before the
  block lookup; it must not be moved after native selection.
- Each admitted chunk input caches one native tick view, retaining its primary
  generations through strong leases. Ordinary atomic writes stay visible; a
  storage-replacement epoch or owner retirement rejects the lease. Rebinding
  takes place before another selection. Epoch saturation permanently declines
  reuse; it cannot wrap into an old lease. Zero-width palette aliases stay live.
  These leases contain no copied block values and retain at most 256 primary
  generations per view. Superseded views release after their Java arenas become
  unreachable; sustained memory acceptance remains open.
- A bounded native cursor stays in a reusable thread-local CPU buffer across
  callbacks. Nested chunk ticks borrow separate buffers; Java receives one
  scalar candidate, rather than copying cursor fields in both directions.
- The automatic Java arena releases its one CPU sequence owner. Native calls
  fence both sequence and section owners. No GPU state or new crate is involved.

## Evidence and commands

A fresh paired 30-second gameplay JFR at `2701a9725` found 56 MB of sampled
server allocation weight in `Level.getBlockRandomPos` and 41 leaf samples in
`PalettedContainer.get`. These are diagnostic samples, not rates, retained
memory or isolated migration gains. Profiles and guards are in
`build/native-frame-state-profile/paired/`.

Native `e6a65319` passes the full check: 2,769 Rust tests (three ignored) and
2,110 Java tests (two skipped), including eight random-tick integration tests.
All 600 Frozen replay cases match, with source/class/native identity guards.
Compiler-quiet component medians, milliseconds per 4,096 chunk ticks:

| Callback density | Current | Previous Current loop | Frozen |
| --- | ---: | ---: | ---: |
| 1 in 4,096 | 1.310 | 2.386 | 1.728 |
| 1 in 16 | 1.564 | 2.423 | 1.816 |
| Every block | 5.415 | 3.925 | 3.076 |

Sparse cases are 24% and 14% faster than Frozen. **The dense case remains 38%
slower than the previous Current loop and 76% slower than Frozen.** It is an
open performance gap; Java behavior callbacks still cross the ownership
boundary. Previous Current is a causal control using its `Level`/`ServerLevel`
classes over the candidate storage, not a correctness baseline. Java allocation
per sample is 1,716 B / 442,368 B / 7,077,888 B across the three densities,
versus 7,077,888 B for both reference loops in each density. This measures Java
allocation, not total retained/native memory or whole-game speed.

Receipts: `build/native-random-ticks/oracle-5/` and
`build/native-random-ticks-check.log`. Earlier rejected or slower candidates
remain under `oracle-{1,2,3,4}/`: section-only selection took 5.93 ms sparse,
chunk traversal 2.85 ms, and primary leases/native cursors removed further
lookup and boundary work. The first timing attempt rejected measured JIT work.

Focused native tests cover sequence wrapping, bounded skipping, mutations and
retired owners. Java controls cover signed masks, callbacks consuming the same
sequence, palette replacement, profiler ordering and exception prefixes.
The Frozen observer invokes actual `ServerLevel.tickChunk` on both sides,
using disconnected world shells with real palettes, counters and Nether Wart
callbacks. Its controlled random source avoids block placement, records callback
order, mutates future inputs and consumes nested position draws. It is
supplemental CPU evidence, not whole-game verification.

```sh
# JDK 25; build before timing, with no other clients/builds/profilers active.
./gradlew -PmattmcRustProfile=release test --tests '*NativeRandomTicksTest'
CARGO_TARGET_DIR=build/rust/target cargo test --locked \
  --manifest-path src/main/rust/Cargo.toml --profile suite --lib \
  world::level::random_ticks -- --test-threads=1
python3 DevUtils/tests/world/chunk/VerifyRandomTicks.py \
  --java-home /path/to/jdk25 --output build/random-ticks/replay --benchmark \
  --java-control-ref 2701a9725
```

The component benchmark runs 4,096 production chunk ticks per sample with 24
sections and random tick speed 3. Three densities distinguish mostly skipped
attempts from callback-heavy work. Three alternating rounds of Current, Frozen
and the optional previous-Current control use matching heap, GC and synchronous
compilation settings; measured compilation rejects a sample set.

## Runtime checkpoint

One fresh 6,000-frame Current/Frozen pair per vanilla, shaders, DH and shaders+DH
mode passes the scoped FPS and p99 floors. Full numbers are in
[SUMMARY.md](https://github.com/HungLo2020/MattMC/blob/master/SUMMARY.md).
Saved inputs, settings and JVM controls match; live entity counts vary slightly.
Single pairs do not establish an isolated migration gain or sustained acceptance.

Two settled image pairs and world reload, resource reload and view-distance
decrease checks pass, with zero recorded VUIDs. Seven original images, five Rust
presenter correlations and nine native image identities were checked. Lifecycle
images are heavily hazed: they establish structural/presentation evidence only.
First shader presentation, first-turn pop-in/flicker and long-session memory
remain open, as does the dense CPU regression above.

Receipts: `build/native-random-ticks/{runtime,gameplay-comparison,image-review,final-integrity}.json`
and `artifacts/graphics-captures/validation/native-random-ticks-20261013/`.
All source/class/native/Frozen guards pass; 13 completed copied worlds were
retired after clients exited, preserving original images and failure evidence.
Use the existing [render verification tools](../../rendering/RENDER-VERIFICATION.md)
for subsequent runtime comparisons on a newly pinned build.

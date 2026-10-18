# Rust light work ownership

`LightEngine` retains both pending propagation queues in Rust. Java producers
append a complete position/entry pair in one ordinary downcall; the canonical
sky-source producer appends directly in Rust. The propagation consumer takes
the pending deques without copying them into Java buffers or rebuilding them
in a second native deque. The effectful block-pass correction instead consumes
those same live queues one pair at a time, so callback additions and consumed
failure prefixes follow the original order. This extends the existing
[propagation policy](RUST-LIGHT-PROPAGATION.md); it does not move the whole light
engine or world simulation out of Java.

## Ownership and admission

- [`work.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/lighting/propagation/work.rs)
  owns pending inputs, published counts and an input journal. Decreases precede
  increases; each queue preserves FIFO order and all 64 entry bits.
- The independent queue lease lets section callbacks enqueue without aliasing
  the mutable propagation engine. A mutex covers only queue operations; it is
  released before any Java callback. Atomic word counts supply Java's empty
  checks without a downcall.
- The bulk snapshot route clears and reuses input capacity on success. Its
  rejected section input restores the journal. The
  [effectful block correction](RUST-LIGHT-PROPAGATION.md#effectful-block-propagation-under-development)
  uses the actual queue and canonical storage owners directly; it preserves
  immediate writes, live additions and the consumed prefix without replaying a
  failed VM effect. Its pending primary-state extension captures a real section
  owner per selected state, reads it after the callback and drops it before
  consuming the canonical registry's properties. Custom state/shape effects
  retain their VM boundary. This correction is still under verification.
- Canonical sky seeding updates native light bytes and enqueues native work
  directly. Escaped arrays retain the bounded compatibility export. Stateful
  queue calls use ordinary FFM downcalls and native scratch spans.
- Java owner bindings lease CPU memory and fence synchronous borrows. The bulk
  snapshot handle excludes nested use. Effectful invocations retain independent
  native inputs and release queue/state guards before VM callbacks. The original
  single-thread caller contract remains; this does not certify arbitrary
  concurrent use.

The connected [light coordinator](RUST-LIGHT-COORDINATOR.md) now owns stock node
membership, section admission, columns and publication. Java retains genuine
world/object callbacks and compatibility paths. Stock block/sky source seeding
uses Rust; custom readers and mixed or escaped layers retain their original
Java leaf calls while Rust selects the sky-source traversal. The effectful block
correction likewise runs its live queue policy in Rust. The pending effectful
sky correction extends that consumer with the original empty-section traversal;
its snapshot/replay failures and validation status are recorded in
[propagation development](RUST-LIGHT-PROPAGATION.md#effectful-sky-propagation-under-development).
Rust owns those pending queues as well. The server's outer batch/scheduling and request policy
has its separate [threaded coordinator](RUST-THREADED-LIGHTING.md). No GPU
resource, presenter or rendering boundary is involved.

## Verify changes

Build the native release and Java classes with JDK25, then run:

```sh
./gradlew -PmattmcRustProfile=release testClasses -x testRustNative
./gradlew test -x buildRustNative -x testRustNative --tests '*NativeLightQueuesTest' --tests '*NativeLightPropagationTest'
./gradlew testRustNative
JAVA_HOME=/path/to/jdk25 python3 DevUtils/tests/lighting/VerifyLightWork.py --output build/light-work-oracle
python3 DevUtils/RunWiki.py check
```

The oracle compiles one observer separately against actual unchanged Frozen and
Current classes. It compares full layer-content hashes, lazy allocation, map
sharing, publication immutability, section states, notifications and work counts
after every pass. It refuses different Frozen engine class images, pins sources,
native libraries and compiled classes, and requires actual native passes without
rejections. Its six sparse worlds cover initial lighting, edits, queued imports
and light-enable toggles. Existing propagation tests add saved FULL terrain,
generated dimensions, empty-section crossings, missing neighbours and rejection.
These checks supplement live chunk loading and gameplay; they cannot establish
whole-game parity or performance alone.

Run Gradle Java suites before or after capture harnesses: their repository
cleanup currently matches unrelated Gradle wrappers and can terminate a
concurrent parity run. The full Rust gate uses the `suite` profile with debug
assertions; the full release-profile suite contains a lightmap-cache test that
expects its debug-only same-generation check.

## Current verification

The figures below describe the historical `ca8bf37a` queue-owner checkpoint.
See [effectful block propagation](RUST-LIGHT-PROPAGATION.md#effectful-block-propagation-under-development)
for the current correction, verification and outstanding performance debt.

Candidate `ca8bf37a` matches Frozen for 492 passes and 437,089 state rows, with
source/native/class guards intact. Queue growth/wrapping and the existing Java
propagation tests pass (nine Java tests and 40 Rust lighting tests). Two
alternating JVM pairs against the published predecessor measure terrain
21.998/21.769 ms, edits 10.306/10.621 ms and nether 8.966/8.295 ms
(predecessor/candidate). Checksums and compiled/native guards match. Terrain
improves 1.0%, nether improves 7.5%, and edits regress 3.1%; this is a warmed
subsystem comparison, not a Frozen whole-game claim.

The supported full Rust suite passes 2,711 tests (three ignored), and full Java
suites pass 2,029 (two skipped). The earlier release-profile test failure and
terminated concurrent Java parity attempt remain in the receipts. All seven
lifecycle cases and settled vanilla/Iris+DH pairs pass, with zero validation
errors and nine matching native identities. Eleven originals were reviewed;
the lifecycle images are heavily hazy and prove no visual world-entry parity.
Both ordinary flights cross six chunk columns at matching positions; six
endpoint originals agree closely. These endpoints do not accept transient
terrain gaps or long-session memory. All 20 matched whole-game runs are clean; vanilla p99 misses 0.93% and DH FPS
misses 6.11%, while the other floors pass. Final source/native/class/Frozen and
prepared-input guards pass; no clients remain. See the complete
[performance record](../../rendering/GAMEPLAY-PERFORMANCE.md#local-light-work-checkpoint).
Receipts are under `build/native-light-work/`.

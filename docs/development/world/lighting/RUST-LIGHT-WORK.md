# Rust light work ownership

`LightEngine` retains both pending propagation queues in Rust. Java producers
append a complete position/entry pair in one ordinary downcall; the canonical
sky-source producer appends directly in Rust. The propagation consumer takes
the pending deques without copying them into Java buffers or rebuilding them
in a second native deque. This extends the existing
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
- Successful admission clears and reuses input capacity. A rejected section
  callback restores original inputs after callback additions, matching the
  previous bridge's refill order. Generated pass-local work is discarded.
  Earlier callback effects and later result-install failures are not rolled back.
- Canonical sky seeding updates native light bytes and enqueues native work
  directly. Escaped arrays retain the bounded compatibility export. Stateful
  queue calls use ordinary FFM downcalls and native scratch spans.
- Java owner bindings lease CPU memory and fence synchronous borrows. Same-handle
  nested native propagation is excluded. The original single-thread caller
  contract remains; this does not certify arbitrary concurrent or reentrant use.

Java still owns `blockNodesToCheck`, section admission, contextual callbacks,
column extraction, per-engine publication and compatibility propagation loops.
The server's outer batch/scheduling policy has its separate
[threaded coordinator](RUST-THREADED-LIGHTING.md).
Rust owns pending queues even when those compatibility loops execute. No GPU
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

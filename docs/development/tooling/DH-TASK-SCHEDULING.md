# DH task admission and cancellation

[`PriorityTaskPicker`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/com/seibel/distanthorizons/core/util/threading/PriorityTaskPicker.java)
coordinates logical DH queues over backing executors. A task remains owned until
it runs or is explicitly discarded; dequeuing it is not proof of admission.
This is the recovery contract for [#753](https://github.com/HungLo2020/MattMC/issues/753).

## Admission and retry

The picker reserves running counts before calling the backing executor, so an
inline or fast worker cannot finish before its admission is accounted for.
A live executor's rejection returns the unstarted task to the front of its
logical queue. A shared daemon timer retries with a per-executor delay that
increases from 25 ms to a maximum of 250 ms; new submissions do not bypass that
cooldown. Queue order describes admission, not completion order among parallel
workers. A permanently rejecting executor that still reports itself live keeps
its work queued until capacity returns or its owner explicitly cancels or closes
it. There is no retry limit that silently drops work.

Shutdown marks ownership closed before notifying discard callbacks. Logical
queued work is discarded even for graceful logical-executor shutdown; already
admitted work may finish. Immediate shutdown also releases unstarted admissions.
Retired backing pools remain part of termination accounting after replacement.
Callbacks and backing-executor calls run outside the picker's bookkeeping lock.

## Returning a future or holding a resource

Use the picker's owned `runAsync`/`supplyAsync` helpers, or
`execute(command, onDiscard)` with an explicit terminal callback. An opaque
`CompletableFuture.runAsync(..., executor)` command does not expose its returned
future to the executor, so discarding that command alone cannot settle the
caller's future. Ordinary submitted `Future` commands are cancelled on discard.

A resource-producing supplier must also close a result if cancellation wins the
race with completion. The provider and render paths enforce this for native DH
sources. Render builds retain an outer completion owner for their upload slot,
busy state and neighbor-source cleanup. Request owners release permits and group entries exactly once on their
relevant terminal path, and queued decode discard returns its pooled DTO. Client
success waits for decoding and the consumer handoff; cancellation does not prove
already-running work has stopped.

Generation queues publish their completion owner before calling a generator
that may complete synchronously. Closing during submission forwards the selected
interrupt policy when the worker becomes available. Synchronous submission
failures settle the owner and remove in-progress bookkeeping, including fatal
errors before they are rethrown. Updater discard paths release their in-memory
counts and positions without clearing persisted retry work. Legacy migration
closes its converted native source on terminal update paths and deletes the old
row only after a successful update stage. Explicit cancellation stops the current
migration run, retaining old rows for a later reopen rather than immediately
selecting them again. A successful stage is not a new guarantee that every
underlying storage error propagates: existing synchronous update error logging
remains a separate boundary.

## Verification

Run `./gradlew test --tests '*PriorityTaskPickerRecoveryTest'` for scheduler
rejection, bounded retry, inline completion, reentrancy and shutdown controls.
The adjacent `*LifecycleTest`, `*DiscardLifecycleTest`,
`LodRenderSectionAdmissionTest` and `LodRenderSectionResourceTest` classes exercise
actual DH owners, pooled objects and native-source cleanup with controlled world,
transport and storage-read boundaries. Use the [testing guide](TESTING.md) for the
full `check` gate and native-library prerequisites.

These headless ownership tests do not validate GPU output, terrain-generation
parity or whole-game frame time. Keep accepted-task throughput comparisons
separate from rejection/backoff controls and from gameplay benchmarks.

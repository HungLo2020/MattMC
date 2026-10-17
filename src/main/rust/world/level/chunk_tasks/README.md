# Server chunk-task queue

`mod.rs` owns priority levels, linked FIFO chunk membership, task-token lists,
reprioritization, release and pop. Reused task/chunk arenas keep primary links
indexed and avoid repeated neighbor hash lookups. `ffi.rs` exposes a private semantic CPU owner
and an atomic read-only top/pending projection. `tests.rs` replays actual Frozen
operations and checks ownership after failures. Tokens are callable identities,
never addresses or GPU handles. The primary task arena also assigns reusable callable rows.
Each result projects the next callable row and bounded call mode to the excluded
writer; it avoids separate hot-path header reads. Java temporarily pins callable references only;
it owns no priority/chunk map and does not reconstruct queue state each frame.

See [the developer guide](../../../../../../docs/development/world/chunk-loading/RUST-CHUNK-TASKS.md)
for dispatch constraints, replay and verification scope.

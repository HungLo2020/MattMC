//! Rust-owned Anvil region-file implementation.
//!
//! Production Java opens one persistent native region handle per `RegionFile`.
//! Rust owns the `.mca`/`.mcc` file handle, header tables, timestamps, sector
//! allocation, external chunk transitions, compression, whole-buffer NBT
//! parsing/writing, and flush/close ordering. Java retains cache and scheduling
//! policy in `RegionFileStorage`/`IOWorker` plus the gameplay `CompoundTag`
//! object model.

pub mod decompress;
pub mod error;
pub mod external;
pub mod ffi;
pub mod format;
pub mod header;
pub mod open;
pub mod payload;
pub mod semantic;
pub mod writer;

#[cfg(test)]
mod tests;

pub use error::{RegionError, RegionErrorKind, RegionResult};
pub use payload::{read_chunk_payload, ChunkPayload};
pub use semantic::{read_chunk_nbt_fingerprint, ChunkNbtFingerprint};
pub use writer::{delete_chunk_payload, flush_region, write_chunk_payload, ChunkWriteResult};

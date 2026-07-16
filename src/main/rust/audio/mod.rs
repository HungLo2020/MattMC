//! Native OpenAL backend used by the Java sound policy layer.
//!
//! Java keeps resource lookup, Ogg decoding, `SoundInstance` policy, subtitles,
//! music scheduling, and option handling. This module owns the low-level OpenAL
//! resources behind opaque handles: devices, contexts, sources, buffers, stream
//! queues, listener state, pool limits, and native error translation.

mod buffer;
mod context;
mod device;
mod errors;
pub mod ffi;
mod handles;
mod listener;
mod source;

#[cfg(test)]
mod tests;

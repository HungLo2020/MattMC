//! Native OpenAL backend used by the Java sound policy layer.
//!
//! Java keeps resource lookup, streaming Ogg decoding, `SoundInstance` policy,
//! subtitles, music scheduling, and option handling. This module owns encoded
//! static audio assets, static Ogg Vorbis decoding, device-local static buffer
//! caching, OpenAL resources, stream queues, listener state, pool limits, and
//! native error translation behind opaque handles.

mod asset;
mod backend;
mod buffer;
mod commands;
mod context;
mod decoder;
mod device;
mod errors;
pub mod ffi;
mod format;
mod handles;
mod listener;
mod source;
mod stream;

#[cfg(test)]
mod tests;

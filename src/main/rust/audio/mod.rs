//! Native audio: the OpenAL backend and the sound policy above it.
//!
//! The backend owns encoded audio assets, Ogg Vorbis decoding, device-local
//! static buffer caching, OpenAL resources, stream queues, listener state,
//! pool limits and native error translation behind opaque handles.
//! [`policy`] owns `sounds.json` event resolution, the asset cache and preload
//! set, and the playback engine with its device-owner thread. Java keeps pack
//! IO, `SoundInstance` objects, subtitles, music selection and options.

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
pub mod policy;
mod refill;
mod source;
mod stream;
mod stream_decoder;

#[cfg(test)]
mod tests;

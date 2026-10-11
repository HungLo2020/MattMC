//! Sound policy owned by Rust: `sounds.json` event resolution, the asset cache
//! and preload set, and the playback engine with its device-owner thread.
//!
//! Java keeps pack IO, `SoundInstance` objects (their per-tick behavior,
//! subtitles listeners and music selection) and calls in through [`ffi`]. The
//! OpenAL/Vorbis backend below (`super::backend`) is unchanged.

pub(crate) mod assets;
pub(crate) mod engine;
pub(crate) mod events;
pub mod ffi;
pub(crate) mod thread;

#[cfg(test)]
mod tests;

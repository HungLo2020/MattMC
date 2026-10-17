//! The client sound system owned by Rust: `sounds.json` event resolution,
//! the asset cache and preload set, the playback engine with its
//! device-owner thread, per-tick sound rules, subtitles, music, the local
//! player's ambience and the volume preview.
//!
//! Java keeps pack IO, the `SoundInstance` objects gameplay code creates, the
//! entity facts their rules sample and the situation music follows, and
//! calls in through [`ffi`]. The OpenAL/Vorbis backend below
//! (`super::backend`) is unchanged.

pub(crate) mod ambient;
pub(crate) mod assets;
pub(crate) mod client;
pub(crate) mod engine;
pub(crate) mod events;
pub mod ffi;
pub(crate) mod music;
pub(crate) mod preview;
pub(crate) mod rules;
pub(crate) mod subtitles;
pub(crate) mod thread;

#[cfg(test)]
mod client_tests;
#[cfg(test)]
mod tests;

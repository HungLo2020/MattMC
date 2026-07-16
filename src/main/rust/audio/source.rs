use std::sync::Arc;

use alto::{Buffer, DistanceModel, Source, SourceState, StaticSource, StreamingSource};

use super::context::alto_call;
use super::device::ChannelPool;
use super::errors::{AudioError, AudioResult};
use super::stream;

pub(crate) const AL_INITIAL: i32 = 0x1011;
pub(crate) const AL_PLAYING: i32 = 0x1012;
pub(crate) const AL_PAUSED: i32 = 0x1013;
pub(crate) const AL_STOPPED: i32 = 0x1014;

/// Native OpenAL source owned by the Rust backend.
///
/// Java keeps `SoundInstance` and scheduling state, but the source handle is the
/// only authority for OpenAL source lifetime. Destroying the source stops it,
/// drops any queued stream buffers, and releases exactly one channel-pool slot.
pub(crate) struct NativeSource {
    pub(crate) device: u64,
    pub(crate) pool: ChannelPool,
    pub(crate) kind: SourceKind,
}

pub(crate) enum SourceKind {
    Static(StaticSource),
    Streaming(StreamingSource),
}

impl NativeSource {
    pub(crate) fn play(&mut self) {
        match &mut self.kind {
            SourceKind::Static(source) => source.play(),
            SourceKind::Streaming(source) => source.play(),
        }
    }

    pub(crate) fn pause(&mut self) {
        match &mut self.kind {
            SourceKind::Static(source) => source.pause(),
            SourceKind::Streaming(source) => source.pause(),
        }
    }

    pub(crate) fn stop(&mut self) {
        match &mut self.kind {
            SourceKind::Static(source) => source.stop(),
            SourceKind::Streaming(source) => source.stop(),
        }
    }

    pub(crate) fn state(&self) -> i32 {
        let state = match &self.kind {
            SourceKind::Static(source) => source.state(),
            SourceKind::Streaming(source) => source.state(),
        };
        match state {
            SourceState::Initial => AL_INITIAL,
            SourceState::Playing => AL_PLAYING,
            SourceState::Paused => AL_PAUSED,
            SourceState::Stopped => AL_STOPPED,
            SourceState::Unknown(value) => value,
        }
    }

    pub(crate) fn set_position(&mut self, x: f32, y: f32, z: f32) -> AudioResult<()> {
        match &mut self.kind {
            SourceKind::Static(source) => {
                alto_call("Set source position", source.set_position([x, y, z]))
            }
            SourceKind::Streaming(source) => {
                alto_call("Set source position", source.set_position([x, y, z]))
            }
        }
    }

    pub(crate) fn set_pitch(&mut self, pitch: f32) -> AudioResult<()> {
        match &mut self.kind {
            SourceKind::Static(source) => alto_call("Set source pitch", source.set_pitch(pitch)),
            SourceKind::Streaming(source) => alto_call("Set source pitch", source.set_pitch(pitch)),
        }
    }

    pub(crate) fn set_volume(&mut self, gain: f32) -> AudioResult<()> {
        match &mut self.kind {
            SourceKind::Static(source) => alto_call("Set source gain", source.set_gain(gain)),
            SourceKind::Streaming(source) => alto_call("Set source gain", source.set_gain(gain)),
        }
    }

    pub(crate) fn set_relative(&mut self, relative: bool) -> AudioResult<()> {
        match &mut self.kind {
            SourceKind::Static(source) => source.set_relative(relative),
            SourceKind::Streaming(source) => source.set_relative(relative),
        }
        Ok(())
    }

    pub(crate) fn disable_attenuation(&mut self) -> AudioResult<()> {
        match &mut self.kind {
            SourceKind::Static(source) => alto_call(
                "Disable source attenuation",
                source.set_distance_model(DistanceModel::None),
            ),
            SourceKind::Streaming(source) => alto_call(
                "Disable source attenuation",
                source.set_distance_model(DistanceModel::None),
            ),
        }
    }

    pub(crate) fn linear_attenuation(&mut self, distance: f32) -> AudioResult<()> {
        match &mut self.kind {
            SourceKind::Static(source) => {
                alto_call(
                    "Set linear distance model",
                    source.set_distance_model(DistanceModel::Linear),
                )?;
                alto_call("Set max distance", source.set_max_distance(distance))?;
                alto_call("Set reference distance", source.set_reference_distance(1.0))?;
                alto_call("Set rolloff factor", source.set_rolloff_factor(0.0))
            }
            SourceKind::Streaming(source) => {
                alto_call(
                    "Set linear distance model",
                    source.set_distance_model(DistanceModel::Linear),
                )?;
                alto_call("Set max distance", source.set_max_distance(distance))?;
                alto_call("Set reference distance", source.set_reference_distance(1.0))?;
                alto_call("Set rolloff factor", source.set_rolloff_factor(0.0))
            }
        }
    }

    pub(crate) fn set_looping(&mut self, looping: bool) {
        if let SourceKind::Static(source) = &mut self.kind {
            source.set_looping(looping);
        }
    }

    pub(crate) fn attach_static_buffer(&mut self, buffer: Arc<Buffer>) -> AudioResult<()> {
        match &mut self.kind {
            SourceKind::Static(source) => {
                alto_call("Attach static buffer", source.set_buffer(buffer))
            }
            SourceKind::Streaming(_) => Err(AudioError::InvalidArgument),
        }
    }

    pub(crate) fn queue_stream_buffer(&mut self, buffer: Buffer) -> AudioResult<()> {
        match &mut self.kind {
            SourceKind::Streaming(source) => stream::queue_buffer(source, buffer),
            SourceKind::Static(_) => Err(AudioError::InvalidArgument),
        }
    }

    pub(crate) fn remove_processed_buffers(&mut self) -> AudioResult<i32> {
        match &mut self.kind {
            SourceKind::Streaming(source) => stream::remove_processed_buffers(source),
            SourceKind::Static(_) => Ok(0),
        }
    }
}

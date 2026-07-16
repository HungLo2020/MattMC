use std::sync::{Arc, Mutex, OnceLock};

use super::buffer::{create_buffer, NativeBuffer};
use super::commands::{
    ListenerStateRecord, SoundConfigRecord, SOUND_FLAG_DISABLE_ATTENUATION,
    SOUND_FLAG_LINEAR_ATTENUATION, SOUND_FLAG_LOOPING, SOUND_FLAG_RELATIVE,
    SOUND_UPDATE_ATTENUATION, SOUND_UPDATE_GAIN, SOUND_UPDATE_LOOPING, SOUND_UPDATE_PITCH,
    SOUND_UPDATE_POSITION, SOUND_UPDATE_RELATIVE,
};
use super::context::{cstring_to_string, load_openal, NativeAlto};
use super::device::{ChannelPool, NativeDevice};
use super::errors::*;
use super::handles::{HandleTable, ResourceKind};
use super::listener::{self, ListenerTransform};
use super::source::{NativeSource, SourceKind};

/// Owns all OpenAL objects reachable through the Java native audio API.
///
/// Java may hold opaque numeric handles, but Rust is the lifetime authority for
/// devices, contexts, sources, buffers, stream queues, and listener state. A
/// device destroy is a reload/shutdown boundary: all sources and buffers tied to
/// that device are removed first, then the context/device drops. Calls using old
/// handles after that point fail with `ERR_INVALID_HANDLE`.
struct AudioBackend {
    openal: Option<NativeAlto>,
    devices: HandleTable<NativeDevice>,
    sources: HandleTable<NativeSource>,
    buffers: HandleTable<NativeBuffer>,
}

impl AudioBackend {
    fn new() -> Self {
        Self {
            openal: None,
            devices: HandleTable::new(ResourceKind::Device),
            sources: HandleTable::new(ResourceKind::Source),
            buffers: HandleTable::new(ResourceKind::Buffer),
        }
    }

    fn openal(&mut self) -> AudioResult<&NativeAlto> {
        if self.openal.is_none() {
            self.openal = Some(load_openal()?);
        }
        Ok(self
            .openal
            .as_ref()
            .expect("OpenAL loader was just initialized"))
    }
}

static BACKEND: OnceLock<Mutex<AudioBackend>> = OnceLock::new();

fn backend() -> &'static Mutex<AudioBackend> {
    BACKEND.get_or_init(|| Mutex::new(AudioBackend::new()))
}

fn with_backend<T>(f: impl FnOnce(&mut AudioBackend) -> AudioResult<T>) -> AudioResult<T> {
    let mut guard = backend()
        .lock()
        .map_err(|_| AudioError::OpenAlCall("Lock audio backend", "mutex poisoned".to_string()))?;
    f(&mut guard)
}

pub(crate) fn create_device(preferred: Option<String>, hrtf: bool) -> AudioResult<u64> {
    with_backend(|backend| {
        let device = NativeDevice::open(backend.openal()?, preferred, hrtf)?;
        Ok(backend.devices.insert(device))
    })
}

pub(crate) fn destroy_device(device_handle: u64) -> AudioResult<()> {
    with_backend(|backend| {
        backend.ensure_device_owner(device_handle)?;

        let source_handles = backend
            .sources
            .handles_for(|source| source.device == device_handle);
        for handle in source_handles {
            if let Some(mut source) = backend.sources.remove(handle) {
                source.stop();
            }
        }

        let buffer_handles = backend
            .buffers
            .handles_for(|buffer| buffer.device == device_handle);
        for handle in buffer_handles {
            backend.buffers.remove(handle);
        }

        backend
            .devices
            .remove(device_handle)
            .ok_or(AudioError::InvalidHandle)?;
        Ok(())
    })
}

pub(crate) fn create_source(device_handle: u64, pool: ChannelPool) -> AudioResult<u64> {
    with_backend(|backend| {
        let context = {
            let device = backend
                .devices
                .get_mut(device_handle)
                .ok_or(AudioError::InvalidHandle)?;
            device.ensure_owner_thread()?;
            if !device.acquire_pool(pool) {
                return Err(AudioError::PoolExhausted);
            }
            device.context.clone()
        };

        let kind = match pool {
            ChannelPool::Static => {
                super::context::alto_call("Create static source", context.new_static_source())
                    .map(SourceKind::Static)
            }
            ChannelPool::Streaming => {
                super::context::alto_call("Create streaming source", context.new_streaming_source())
                    .map(SourceKind::Streaming)
            }
        };

        match kind {
            Ok(kind) => Ok(backend.sources.insert(NativeSource {
                device: device_handle,
                pool,
                kind,
                queued_stream_buffers: 0,
            })),
            Err(error) => {
                if let Some(device) = backend.devices.get_mut(device_handle) {
                    device.release_pool(pool);
                }
                Err(error)
            }
        }
    })
}

pub(crate) fn destroy_source(source_handle: u64) -> AudioResult<()> {
    with_backend(|backend| {
        let device_handle = backend
            .sources
            .get(source_handle)
            .ok_or(AudioError::InvalidHandle)?
            .device;
        backend.ensure_device_owner(device_handle)?;

        let mut source = backend
            .sources
            .remove(source_handle)
            .ok_or(AudioError::InvalidHandle)?;
        source.stop();
        if let Some(device) = backend.devices.get_mut(source.device) {
            device.release_pool(source.pool);
        }
        Ok(())
    })
}

pub(crate) fn create_static_sound(
    device_handle: u64,
    config: SoundConfigRecord,
    buffer_handle: u64,
) -> AudioResult<u64> {
    let source = create_source(device_handle, ChannelPool::Static)?;
    if let Err(error) = update_sound(source, super::commands::SOUND_UPDATE_ALL, config)
        .and_then(|_| attach_static_buffer(source, buffer_handle))
    {
        let _ = destroy_source(source);
        return Err(error);
    }
    Ok(source)
}

pub(crate) fn create_streaming_sound(
    device_handle: u64,
    config: SoundConfigRecord,
) -> AudioResult<u64> {
    let source = create_source(device_handle, ChannelPool::Streaming)?;
    if let Err(error) = update_sound(source, super::commands::SOUND_UPDATE_ALL, config) {
        let _ = destroy_source(source);
        return Err(error);
    }
    Ok(source)
}

pub(crate) fn update_sound(
    source_handle: u64,
    update_mask: u32,
    config: SoundConfigRecord,
) -> AudioResult<()> {
    with_source_mut(source_handle, |source| {
        apply_sound_config(source, update_mask, config)
    })
}

pub(crate) fn submit_stream_chunks(
    source_handle: u64,
    chunks: &[&[u8]],
    channels: i32,
    bits: i32,
    pcm: bool,
    sample_rate: i32,
) -> AudioResult<i32> {
    with_backend(|backend| {
        let device_handle = backend
            .sources
            .get(source_handle)
            .ok_or(AudioError::InvalidHandle)?
            .device;
        backend.ensure_device_owner(device_handle)?;
        let context = backend
            .devices
            .get(device_handle)
            .ok_or(AudioError::InvalidHandle)?
            .context
            .clone();
        let mut accepted = 0_i32;
        for chunk in chunks {
            let buffer = create_buffer(&context, chunk, channels, bits, pcm, sample_rate)?;
            backend
                .sources
                .get_mut(source_handle)
                .ok_or(AudioError::InvalidHandle)?
                .queue_stream_buffer(buffer)?;
            accepted += 1;
        }
        Ok(accepted)
    })
}

pub(crate) fn update_listener(
    device_handle: u64,
    listener_state: ListenerStateRecord,
) -> AudioResult<()> {
    listener_set_transform(
        device_handle,
        ListenerTransform {
            position: listener_state.position,
            forward: listener_state.forward,
            up: listener_state.up,
        },
    )?;
    listener_set_gain(device_handle, listener_state.gain)
}

pub(crate) fn source_play(source_handle: u64) -> AudioResult<()> {
    with_source_mut(source_handle, |source| {
        source.play();
        Ok(())
    })
}

pub(crate) fn source_pause(source_handle: u64) -> AudioResult<()> {
    with_source_mut(source_handle, |source| {
        source.pause();
        Ok(())
    })
}

pub(crate) fn source_stop(source_handle: u64) -> AudioResult<()> {
    with_source_mut(source_handle, |source| {
        source.stop();
        Ok(())
    })
}

pub(crate) fn source_state(source_handle: u64) -> AudioResult<i32> {
    with_backend(|backend| {
        let source = backend
            .sources
            .get(source_handle)
            .ok_or(AudioError::InvalidHandle)?;
        backend.ensure_device_owner(source.device)?;
        Ok(source.state())
    })
}

pub(crate) fn source_set_position(source_handle: u64, x: f32, y: f32, z: f32) -> AudioResult<()> {
    with_source_mut(source_handle, |source| source.set_position(x, y, z))
}

pub(crate) fn source_set_pitch(source_handle: u64, pitch: f32) -> AudioResult<()> {
    with_source_mut(source_handle, |source| source.set_pitch(pitch))
}

pub(crate) fn source_set_volume(source_handle: u64, gain: f32) -> AudioResult<()> {
    with_source_mut(source_handle, |source| source.set_volume(gain))
}

pub(crate) fn source_set_looping(source_handle: u64, looping: bool) -> AudioResult<()> {
    with_source_mut(source_handle, |source| {
        source.set_looping(looping);
        Ok(())
    })
}

pub(crate) fn source_set_relative(source_handle: u64, relative: bool) -> AudioResult<()> {
    with_source_mut(source_handle, |source| source.set_relative(relative))
}

pub(crate) fn source_disable_attenuation(source_handle: u64) -> AudioResult<()> {
    with_source_mut(source_handle, |source| source.disable_attenuation())
}

pub(crate) fn source_linear_attenuation(source_handle: u64, distance: f32) -> AudioResult<()> {
    with_source_mut(source_handle, |source| source.linear_attenuation(distance))
}

pub(crate) fn create_buffer_handle(
    device_handle: u64,
    data: &[u8],
    channels: i32,
    bits: i32,
    pcm: bool,
    sample_rate: i32,
) -> AudioResult<u64> {
    with_backend(|backend| {
        let context = backend
            .devices
            .get(device_handle)
            .ok_or(AudioError::InvalidHandle)
            .and_then(|device| {
                device.ensure_owner_thread()?;
                Ok(device.context.clone())
            })?;
        let buffer = create_buffer(&context, data, channels, bits, pcm, sample_rate)?;
        Ok(backend.buffers.insert(NativeBuffer {
            device: device_handle,
            buffer: Arc::new(buffer),
        }))
    })
}

pub(crate) fn destroy_buffer(buffer_handle: u64) -> AudioResult<()> {
    with_backend(|backend| {
        let device_handle = backend
            .buffers
            .get(buffer_handle)
            .ok_or(AudioError::InvalidHandle)?
            .device;
        backend.ensure_device_owner(device_handle)?;
        backend
            .buffers
            .remove(buffer_handle)
            .ok_or(AudioError::InvalidHandle)?;
        Ok(())
    })
}

pub(crate) fn attach_static_buffer(source_handle: u64, buffer_handle: u64) -> AudioResult<()> {
    with_backend(|backend| {
        let source_device = backend
            .sources
            .get(source_handle)
            .ok_or(AudioError::InvalidHandle)?
            .device;
        backend.ensure_device_owner(source_device)?;
        let buffer = backend
            .buffers
            .get(buffer_handle)
            .ok_or(AudioError::InvalidHandle)?
            .clone();
        let source = backend
            .sources
            .get_mut(source_handle)
            .ok_or(AudioError::InvalidHandle)?;
        if source.device != buffer.device {
            return Err(AudioError::InvalidHandle);
        }
        source.attach_static_buffer(buffer.buffer)
    })
}

pub(crate) fn queue_stream_buffer(
    source_handle: u64,
    data: &[u8],
    channels: i32,
    bits: i32,
    pcm: bool,
    sample_rate: i32,
) -> AudioResult<()> {
    with_backend(|backend| {
        let device_handle = backend
            .sources
            .get(source_handle)
            .ok_or(AudioError::InvalidHandle)?
            .device;
        backend.ensure_device_owner(device_handle)?;
        let context = backend
            .devices
            .get(device_handle)
            .ok_or(AudioError::InvalidHandle)?
            .context
            .clone();
        let buffer = create_buffer(&context, data, channels, bits, pcm, sample_rate)?;
        backend
            .sources
            .get_mut(source_handle)
            .ok_or(AudioError::InvalidHandle)?
            .queue_stream_buffer(buffer)
    })
}

pub(crate) fn remove_processed_stream_buffers(source_handle: u64) -> AudioResult<i32> {
    with_source_mut_value(source_handle, |source| source.remove_processed_buffers())
}

pub(crate) fn listener_set_transform(
    device_handle: u64,
    transform: ListenerTransform,
) -> AudioResult<()> {
    with_backend(|backend| {
        let context = &backend
            .devices
            .get(device_handle)
            .ok_or(AudioError::InvalidHandle)
            .and_then(|device| {
                device.ensure_owner_thread()?;
                Ok(device)
            })?
            .context;
        listener::set_transform(context, transform)
    })
}

pub(crate) fn listener_reset(device_handle: u64) -> AudioResult<()> {
    with_backend(|backend| {
        let context = &backend
            .devices
            .get(device_handle)
            .ok_or(AudioError::InvalidHandle)
            .and_then(|device| {
                device.ensure_owner_thread()?;
                Ok(device)
            })?
            .context;
        listener::reset(context)
    })
}

pub(crate) fn listener_set_gain(device_handle: u64, gain: f32) -> AudioResult<()> {
    with_backend(|backend| {
        let context = &backend
            .devices
            .get(device_handle)
            .ok_or(AudioError::InvalidHandle)
            .and_then(|device| {
                device.ensure_owner_thread()?;
                Ok(device)
            })?
            .context;
        listener::set_gain(context, gain)
    })
}

pub(crate) fn device_is_disconnected(device_handle: u64) -> AudioResult<bool> {
    with_backend(|backend| {
        Ok(backend
            .devices
            .get(device_handle)
            .ok_or(AudioError::InvalidHandle)?
            .is_disconnected())
    })
}

pub(crate) fn device_has_default_changed(device_handle: u64) -> AudioResult<bool> {
    with_backend(|backend| {
        let current = backend.openal()?.0.default_output().map(cstring_to_string);
        let device = backend
            .devices
            .get_mut(device_handle)
            .ok_or(AudioError::InvalidHandle)?;
        let changed = device.default_device_name != current;
        if changed {
            device.default_device_name = current;
        }
        Ok(changed)
    })
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct PoolCounts {
    pub(crate) static_used: i32,
    pub(crate) static_limit: i32,
    pub(crate) streaming_used: i32,
    pub(crate) streaming_limit: i32,
}

pub(crate) fn device_pool_counts(device_handle: u64) -> AudioResult<PoolCounts> {
    with_backend(|backend| {
        let device = backend
            .devices
            .get(device_handle)
            .ok_or(AudioError::InvalidHandle)?;
        Ok(PoolCounts {
            static_used: device.static_used as i32,
            static_limit: device.static_limit as i32,
            streaming_used: device.streaming_used as i32,
            streaming_limit: device.streaming_limit as i32,
        })
    })
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
#[cfg(test)]
pub(crate) struct LiveCounts {
    pub(crate) devices: usize,
    pub(crate) sources: usize,
    pub(crate) buffers: usize,
    pub(crate) queued_stream_buffers: i32,
}

#[cfg(test)]
pub(crate) fn live_counts() -> AudioResult<LiveCounts> {
    with_backend(|backend| {
        Ok(LiveCounts {
            devices: backend.devices.len(),
            sources: backend.sources.len(),
            buffers: backend.buffers.len(),
            queued_stream_buffers: backend
                .sources
                .values()
                .map(NativeSource::queued_stream_buffers)
                .sum(),
        })
    })
}

pub(crate) fn default_device_name() -> AudioResult<String> {
    with_backend(|backend| {
        Ok(backend
            .openal()?
            .0
            .default_output()
            .map(cstring_to_string)
            .unwrap_or_default())
    })
}

pub(crate) fn current_device_name(device_handle: u64) -> AudioResult<String> {
    with_backend(|backend| {
        Ok(backend
            .devices
            .get(device_handle)
            .ok_or(AudioError::InvalidHandle)?
            .current_device_name
            .clone())
    })
}

pub(crate) fn available_devices() -> AudioResult<String> {
    with_backend(|backend| {
        Ok(backend
            .openal()?
            .0
            .enumerate_outputs()
            .into_iter()
            .map(cstring_to_string)
            .collect::<Vec<_>>()
            .join("\n"))
    })
}

fn with_source_mut(
    source_handle: u64,
    f: impl FnOnce(&mut NativeSource) -> AudioResult<()>,
) -> AudioResult<()> {
    with_source_mut_value(source_handle, f)
}

fn with_source_mut_value<T>(
    source_handle: u64,
    f: impl FnOnce(&mut NativeSource) -> AudioResult<T>,
) -> AudioResult<T> {
    with_backend(|backend| {
        let device_handle = backend
            .sources
            .get(source_handle)
            .ok_or(AudioError::InvalidHandle)?
            .device;
        backend.ensure_device_owner(device_handle)?;
        let source = backend
            .sources
            .get_mut(source_handle)
            .ok_or(AudioError::InvalidHandle)?;
        f(source)
    })
}

fn apply_sound_config(
    source: &mut NativeSource,
    update_mask: u32,
    config: SoundConfigRecord,
) -> AudioResult<()> {
    if update_mask & SOUND_UPDATE_POSITION != 0 {
        source.set_position(config.x, config.y, config.z)?;
    }
    if update_mask & SOUND_UPDATE_PITCH != 0 {
        source.set_pitch(config.pitch)?;
    }
    if update_mask & SOUND_UPDATE_GAIN != 0 {
        source.set_volume(config.gain)?;
    }
    if update_mask & SOUND_UPDATE_LOOPING != 0 {
        source.set_looping(config.flags & SOUND_FLAG_LOOPING != 0);
    }
    if update_mask & SOUND_UPDATE_RELATIVE != 0 {
        source.set_relative(config.flags & SOUND_FLAG_RELATIVE != 0)?;
    }
    if update_mask & SOUND_UPDATE_ATTENUATION != 0 {
        if config.flags & SOUND_FLAG_DISABLE_ATTENUATION != 0 {
            source.disable_attenuation()?;
        } else if config.flags & SOUND_FLAG_LINEAR_ATTENUATION != 0 {
            source.linear_attenuation(config.attenuation_distance)?;
        }
    }
    Ok(())
}

impl AudioBackend {
    fn ensure_device_owner(&self, device_handle: u64) -> AudioResult<()> {
        self.devices
            .get(device_handle)
            .ok_or(AudioError::InvalidHandle)?
            .ensure_owner_thread()
    }
}

#[cfg(test)]
pub(crate) mod test_support {
    use super::*;

    pub(crate) fn reset_backend_for_tests() {
        if let Some(mutex) = BACKEND.get() {
            if let Ok(mut guard) = mutex.lock() {
                *guard = AudioBackend::new();
            }
        }
    }

    pub(crate) fn counts_for_tests() -> LiveCounts {
        live_counts().expect("audio backend counts should be readable in tests")
    }
}

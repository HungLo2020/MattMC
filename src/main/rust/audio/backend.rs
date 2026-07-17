use std::collections::HashMap;
use std::sync::{Arc, Mutex, OnceLock};

use super::asset::AudioAsset;
#[cfg(test)]
use super::asset::AudioAssetSnapshot;
use super::buffer::create_buffer;
use super::commands::{
    ListenerStateRecord, SoundConfigRecord, StaticDecodeParityRecord,
    SOUND_FLAG_DISABLE_ATTENUATION, SOUND_FLAG_LINEAR_ATTENUATION, SOUND_FLAG_LOOPING,
    SOUND_FLAG_RELATIVE, SOUND_UPDATE_ATTENUATION, SOUND_UPDATE_GAIN, SOUND_UPDATE_LOOPING,
    SOUND_UPDATE_PITCH, SOUND_UPDATE_POSITION, SOUND_UPDATE_RELATIVE,
};
use super::context::{cstring_to_string, load_openal, NativeAlto};
use super::decoder;
use super::device::{ChannelPool, NativeDevice};
use super::errors::*;
use super::handles::{HandleTable, ResourceKind};
use super::listener::{self, ListenerTransform};
use super::refill::StreamingPlayback;
use super::source::{NativeSource, SourceKind};
use super::stream_decoder::StreamDecoder;
use alto::Buffer;

/// Owns all OpenAL objects reachable through the Java native audio API.
///
/// Java may hold opaque numeric handles, but Rust is the lifetime authority for
/// devices, contexts, sources, encoded static assets, decoded static cache
/// entries, stream queues, and listener state. A device destroy is a
/// reload/shutdown boundary: all sources and device-local static cache entries
/// tied to that device are removed first, then the context/device drops. Calls
/// using old handles after that point fail with `ERR_INVALID_HANDLE`.
struct AudioBackend {
    openal: Option<NativeAlto>,
    devices: HandleTable<NativeDevice>,
    sources: HandleTable<NativeSource>,
    assets: HandleTable<AudioAsset>,
    static_cache: HashMap<StaticCacheKey, StaticCacheEntry>,
    static_cache_hits: u64,
    static_cache_misses: u64,
    minimum_asset_generation: u64,
}

#[derive(Clone, Copy, Debug, Hash, PartialEq, Eq)]
struct StaticCacheKey {
    device: u64,
    asset: u64,
}

#[derive(Clone)]
enum StaticCacheEntry {
    Ready {
        buffer: Arc<Buffer>,
        sample_rate: u32,
        channels: u16,
        frames: u64,
    },
    Failed(AudioError),
}

impl AudioBackend {
    fn new() -> Self {
        Self {
            openal: None,
            devices: HandleTable::new(ResourceKind::Device),
            sources: HandleTable::new(ResourceKind::Source),
            assets: HandleTable::new(ResourceKind::Asset),
            static_cache: HashMap::new(),
            static_cache_hits: 0,
            static_cache_misses: 0,
            minimum_asset_generation: 1,
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

pub(crate) fn create_asset(
    encoded: &[u8],
    debug_name: Option<String>,
    reload_generation: u64,
) -> AudioResult<u64> {
    if encoded.is_empty() || reload_generation == 0 {
        return Err(AudioError::InvalidArgument);
    }
    with_backend(|backend| {
        if reload_generation < backend.minimum_asset_generation {
            return Err(AudioError::InvalidHandle);
        }
        Ok(backend
            .assets
            .insert(AudioAsset::new(encoded, debug_name, reload_generation)))
    })
}

pub(crate) fn destroy_asset(asset_handle: u64) -> AudioResult<()> {
    with_backend(|backend| {
        backend
            .assets
            .remove(asset_handle)
            .ok_or(AudioError::InvalidHandle)?;
        backend
            .static_cache
            .retain(|key, _| key.asset != asset_handle);
        backend.destroy_streams_for_asset(asset_handle);
        Ok(())
    })
}

pub(crate) fn destroy_asset_generation(reload_generation: u64) -> AudioResult<()> {
    if reload_generation == 0 {
        return Err(AudioError::InvalidArgument);
    }
    with_backend(|backend| {
        let handles = backend
            .assets
            .handles_for(|asset| asset.reload_generation() <= reload_generation);
        for handle in handles {
            backend.assets.remove(handle);
            backend.static_cache.retain(|key, _| key.asset != handle);
            backend.destroy_streams_for_asset(handle);
        }
        backend.minimum_asset_generation =
            backend.minimum_asset_generation.max(reload_generation + 1);
        Ok(())
    })
}

pub(crate) fn compare_asset_static_pcm(
    asset_handle: u64,
    java_pcm: &[u8],
    java_channels: i32,
    java_bits: i32,
    java_pcm_flag: bool,
    java_sample_rate: i32,
) -> AudioResult<StaticDecodeParityRecord> {
    with_backend(|backend| {
        let asset = backend
            .assets
            .get(asset_handle)
            .ok_or(AudioError::InvalidHandle)?;
        decoder::compare_static_pcm(
            asset.encoded(),
            java_pcm,
            java_channels,
            java_bits,
            java_pcm_flag,
            java_sample_rate,
        )
    })
}

#[cfg(test)]
pub(crate) fn validate_asset(asset_handle: u64) -> AudioResult<()> {
    with_backend(|backend| {
        backend
            .assets
            .get(asset_handle)
            .ok_or(AudioError::InvalidHandle)?;
        Ok(())
    })
}

#[cfg(test)]
pub(crate) fn clear_assets_for_shutdown() -> AudioResult<()> {
    with_backend(|backend| {
        backend.assets.clear();
        backend.static_cache.clear();
        backend.minimum_asset_generation = 1;
        Ok(())
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

        backend
            .static_cache
            .retain(|key, _| key.device != device_handle);

        backend
            .devices
            .remove(device_handle)
            .ok_or(AudioError::InvalidHandle)?;
        Ok(())
    })
}

#[cfg(test)]
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
                stream_playback: None,
                stop_requested: false,
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
    asset_handle: u64,
) -> AudioResult<u64> {
    with_backend(|backend| {
        let static_buffer = backend.static_buffer_for_asset(device_handle, asset_handle)?;
        let context = {
            let device = backend
                .devices
                .get_mut(device_handle)
                .ok_or(AudioError::InvalidHandle)?;
            device.ensure_owner_thread()?;
            if !device.acquire_pool(ChannelPool::Static) {
                return Err(AudioError::PoolExhausted);
            }
            device.context.clone()
        };

        let kind =
            match super::context::alto_call("Create static source", context.new_static_source())
                .map(SourceKind::Static)
            {
                Ok(kind) => kind,
                Err(error) => {
                    if let Some(device) = backend.devices.get_mut(device_handle) {
                        device.release_pool(ChannelPool::Static);
                    }
                    return Err(error);
                }
            };

        let source_handle = backend.sources.insert(NativeSource {
            device: device_handle,
            pool: ChannelPool::Static,
            kind,
            queued_stream_buffers: 0,
            stream_playback: None,
            stop_requested: false,
        });

        let result = backend
            .sources
            .get_mut(source_handle)
            .ok_or(AudioError::InvalidHandle)
            .and_then(|source| {
                apply_sound_config(source, super::commands::SOUND_UPDATE_ALL, config)?;
                source.attach_static_buffer(static_buffer)
            });

        if let Err(error) = result {
            if let Some(mut source) = backend.sources.remove(source_handle) {
                source.stop();
                if let Some(device) = backend.devices.get_mut(source.device) {
                    device.release_pool(source.pool);
                }
            }
            return Err(error);
        }

        Ok(source_handle)
    })
}

pub(crate) fn create_streaming_sound_from_asset(
    device_handle: u64,
    config: SoundConfigRecord,
    asset_handle: u64,
    looping: bool,
) -> AudioResult<u64> {
    with_backend(|backend| {
        let encoded = backend
            .assets
            .get(asset_handle)
            .ok_or(AudioError::InvalidHandle)?
            .encoded_arc();
        let context = {
            let device = backend
                .devices
                .get_mut(device_handle)
                .ok_or(AudioError::InvalidHandle)?;
            device.ensure_owner_thread()?;
            if !device.acquire_pool(ChannelPool::Streaming) {
                return Err(AudioError::PoolExhausted);
            }
            device.context.clone()
        };

        let kind = match super::context::alto_call(
            "Create asset streaming source",
            context.new_streaming_source(),
        )
        .map(SourceKind::Streaming)
        {
            Ok(kind) => kind,
            Err(error) => {
                if let Some(device) = backend.devices.get_mut(device_handle) {
                    device.release_pool(ChannelPool::Streaming);
                }
                return Err(error);
            }
        };

        let source_handle = backend.sources.insert(NativeSource {
            device: device_handle,
            pool: ChannelPool::Streaming,
            kind,
            queued_stream_buffers: 0,
            stream_playback: None,
            stop_requested: false,
        });

        let result = StreamDecoder::new(encoded)
            .map(|decoder| StreamingPlayback::new(asset_handle, decoder, looping))
            .and_then(|playback| {
                let source = backend
                    .sources
                    .get_mut(source_handle)
                    .ok_or(AudioError::InvalidHandle)?;
                apply_sound_config(source, super::commands::SOUND_UPDATE_ALL, config)?;
                source.attach_stream_playback(&context, playback)
            });

        if let Err(error) = result {
            if let Some(mut source) = backend.sources.remove(source_handle) {
                source.stop();
                if let Some(device) = backend.devices.get_mut(source.device) {
                    device.release_pool(source.pool);
                }
            }
            return Err(error);
        }

        Ok(source_handle)
    })
}

pub(crate) fn tick_device(device_handle: u64) -> AudioResult<()> {
    with_backend(|backend| {
        backend.ensure_device_owner(device_handle)?;
        let context = backend
            .devices
            .get(device_handle)
            .ok_or(AudioError::InvalidHandle)?
            .context
            .clone();
        let source_handles = backend
            .sources
            .handles_for(|source| source.device == device_handle);
        for handle in source_handles {
            if let Some(source) = backend.sources.get_mut(handle) {
                source.tick_stream(&context)?;
            }
        }
        Ok(())
    })
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
pub(crate) struct LiveCounts {
    pub(crate) devices: usize,
    pub(crate) sources: usize,
    pub(crate) buffers: usize,
    pub(crate) queued_stream_buffers: i32,
    pub(crate) assets: usize,
    pub(crate) static_cache_entries: usize,
    pub(crate) stream_decoders: usize,
    pub(crate) stream_decoded_chunks: u64,
}

pub(crate) fn live_counts() -> AudioResult<LiveCounts> {
    with_backend(|backend| {
        let _static_cache_metadata =
            backend
                .static_cache
                .values()
                .fold(0_u64, |accumulator, entry| match entry {
                    StaticCacheEntry::Ready {
                        sample_rate,
                        channels,
                        frames,
                        ..
                    } => accumulator ^ *frames ^ u64::from(*sample_rate) ^ u64::from(*channels),
                    StaticCacheEntry::Failed(_) => accumulator,
                });
        Ok(LiveCounts {
            devices: backend.devices.len(),
            sources: backend.sources.len(),
            buffers: 0,
            queued_stream_buffers: backend
                .sources
                .values()
                .map(NativeSource::queued_stream_buffers)
                .sum(),
            assets: backend.assets.len(),
            static_cache_entries: backend.static_cache.len(),
            stream_decoders: backend
                .sources
                .values()
                .map(NativeSource::stream_decoder_count)
                .sum(),
            stream_decoded_chunks: backend
                .sources
                .values()
                .map(NativeSource::stream_decoded_chunks)
                .sum(),
        })
    })
}

#[cfg(test)]
pub(crate) fn asset_count() -> AudioResult<usize> {
    with_backend(|backend| Ok(backend.assets.len()))
}

#[cfg(test)]
pub(crate) fn asset_snapshot_for_tests(asset_handle: u64) -> AudioResult<AudioAssetSnapshot> {
    with_backend(|backend| {
        backend
            .assets
            .get(asset_handle)
            .map(AudioAsset::snapshot)
            .ok_or(AudioError::InvalidHandle)
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

    fn static_buffer_for_asset(
        &mut self,
        device_handle: u64,
        asset_handle: u64,
    ) -> AudioResult<Arc<Buffer>> {
        let key = StaticCacheKey {
            device: device_handle,
            asset: asset_handle,
        };

        if let Some(entry) = self.static_cache.get(&key).cloned() {
            self.static_cache_hits += 1;
            return match entry {
                StaticCacheEntry::Ready { buffer, .. } => Ok(buffer),
                StaticCacheEntry::Failed(error) => Err(error),
            };
        }

        self.static_cache_misses += 1;
        let asset = self
            .assets
            .get(asset_handle)
            .ok_or(AudioError::InvalidHandle)?
            .clone();
        let context = self
            .devices
            .get(device_handle)
            .ok_or(AudioError::InvalidHandle)
            .and_then(|device| {
                device.ensure_owner_thread()?;
                Ok(device.context.clone())
            })?;

        let decoded = match decoder::decode_vorbis(asset.encoded()) {
            Ok(decoded) => decoded,
            Err(error) => {
                self.static_cache
                    .insert(key, StaticCacheEntry::Failed(error.clone()));
                return Err(error);
            }
        };
        let bytes = samples_to_le_bytes(&decoded.samples);
        let buffer = match create_buffer(
            &context,
            &bytes,
            decoded.channels as i32,
            16,
            true,
            decoded.sample_rate as i32,
        ) {
            Ok(buffer) => Arc::new(buffer),
            Err(error) => {
                self.static_cache
                    .insert(key, StaticCacheEntry::Failed(error.clone()));
                return Err(error);
            }
        };
        self.static_cache.insert(
            key,
            StaticCacheEntry::Ready {
                buffer: buffer.clone(),
                sample_rate: decoded.sample_rate,
                channels: decoded.channels,
                frames: decoded.frames,
            },
        );
        Ok(buffer)
    }

    fn destroy_streams_for_asset(&mut self, asset_handle: u64) {
        let handles = self
            .sources
            .handles_for(|source| source.stream_asset_handle() == Some(asset_handle));
        for handle in handles {
            if let Some(mut source) = self.sources.remove(handle) {
                source.stop();
                if let Some(device) = self.devices.get_mut(source.device) {
                    device.release_pool(source.pool);
                }
            }
        }
    }
}

fn samples_to_le_bytes(samples: &[i16]) -> Vec<u8> {
    let mut bytes = Vec::with_capacity(samples.len() * 2);
    for sample in samples {
        bytes.extend_from_slice(&sample.to_le_bytes());
    }
    bytes
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

    pub(crate) fn asset_count_for_tests() -> usize {
        asset_count().expect("audio asset count should be readable in tests")
    }

    #[derive(Clone, Copy, Debug, PartialEq, Eq)]
    pub(crate) struct StreamStats {
        pub(crate) decoded_chunks: u64,
        pub(crate) loop_restarts: u64,
        pub(crate) decode_failed: bool,
    }

    pub(crate) fn stream_stats_for_tests(source_handle: u64) -> AudioResult<StreamStats> {
        with_backend(|backend| {
            let source = backend
                .sources
                .get(source_handle)
                .ok_or(AudioError::InvalidHandle)?;
            Ok(StreamStats {
                decoded_chunks: source.stream_decoded_chunks(),
                loop_restarts: source.stream_loop_restarts(),
                decode_failed: source.stream_decode_failed(),
            })
        })
    }

    #[derive(Clone, Copy, Debug, PartialEq, Eq)]
    pub(crate) struct StaticCacheStats {
        pub(crate) entries: usize,
        pub(crate) ready_entries: usize,
        pub(crate) failed_entries: usize,
        pub(crate) total_frames: u64,
        pub(crate) first_sample_rate: u32,
        pub(crate) first_channels: u16,
        pub(crate) hits: u64,
        pub(crate) misses: u64,
    }

    pub(crate) fn static_cache_stats_for_tests() -> StaticCacheStats {
        with_backend(|backend| {
            let mut ready_entries = 0_usize;
            let mut failed_entries = 0_usize;
            let mut total_frames = 0_u64;
            let mut first_sample_rate = 0_u32;
            let mut first_channels = 0_u16;
            for entry in backend.static_cache.values() {
                match entry {
                    StaticCacheEntry::Ready {
                        sample_rate,
                        channels,
                        frames,
                        ..
                    } => {
                        ready_entries += 1;
                        total_frames += *frames;
                        if first_sample_rate == 0 {
                            first_sample_rate = *sample_rate;
                            first_channels = *channels;
                        }
                    }
                    StaticCacheEntry::Failed(_) => failed_entries += 1,
                }
            }
            Ok(StaticCacheStats {
                entries: backend.static_cache.len(),
                ready_entries,
                failed_entries,
                total_frames,
                first_sample_rate,
                first_channels,
                hits: backend.static_cache_hits,
                misses: backend.static_cache_misses,
            })
        })
        .expect("audio static cache stats should be readable in tests")
    }
}

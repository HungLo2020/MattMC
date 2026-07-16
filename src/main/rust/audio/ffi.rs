use std::ptr;
use std::slice;
use std::sync::{Arc, Mutex, OnceLock};

use super::buffer::{audio_format_to_openal, create_buffer, NativeBuffer};
use super::context::{cstring_to_string, load_openal, NativeAlto};
use super::device::{ChannelPool, NativeDevice};
use super::errors::*;
use super::handles::HandleTable;
use super::listener::{INITIAL_FORWARD, INITIAL_POSITION, INITIAL_UP};
use super::source::{NativeSource, SourceKind, AL_STOPPED};

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
            devices: HandleTable::default(),
            sources: HandleTable::default(),
            buffers: HandleTable::default(),
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

fn status(result: AudioResult<()>) -> i32 {
    match result {
        Ok(()) => OK,
        Err(error) => error.status(),
    }
}

fn with_backend<T>(f: impl FnOnce(&mut AudioBackend) -> AudioResult<T>) -> AudioResult<T> {
    let mut guard = backend()
        .lock()
        .map_err(|_| AudioError::OpenAlCall("Lock audio backend", "mutex poisoned".to_string()))?;
    f(&mut guard)
}

/// # Safety
///
/// `preferred_ptr` must be null with length zero or point to `preferred_len`
/// readable UTF-8 bytes. `output_handle` must be a valid writable pointer.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_device_create(
    preferred_ptr: *const u8,
    preferred_len: u64,
    hrtf: i32,
    output_handle: *mut u64,
) -> i32 {
    if output_handle.is_null() {
        return ERR_INVALID_ARGUMENT;
    }
    match with_backend(|backend| {
        let preferred = ptr_to_string(preferred_ptr, preferred_len)?;
        let device = NativeDevice::open(backend.openal()?, preferred, hrtf != 0)?;
        let handle = backend.devices.insert(device);
        unsafe {
            *output_handle = handle;
        }
        Ok(())
    }) {
        Ok(()) => OK,
        Err(error) => error.status(),
    }
}

/// # Safety
///
/// `device_handle` must be a handle returned by `mattmc_audio_device_create`.
/// Invalid or stale handles are ignored safely.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_device_destroy(device_handle: u64) -> i32 {
    status(with_backend(|backend| {
        let source_handles = backend
            .sources
            .handles_for(|source| source.device == device_handle);
        for handle in source_handles {
            backend.sources.remove(handle);
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
    }))
}

/// # Safety
///
/// `output_handle` must be writable. `device_handle` must name a live native
/// device. `pool_id` is 0 for static channels and 1 for streaming channels.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_source_create(
    device_handle: u64,
    pool_id: i32,
    output_handle: *mut u64,
) -> i32 {
    if output_handle.is_null() {
        return ERR_INVALID_ARGUMENT;
    }
    match with_backend(|backend| {
        let pool = ChannelPool::from_id(pool_id).ok_or(AudioError::InvalidArgument)?;
        let (context, kind) = {
            let device = backend
                .devices
                .get_mut(device_handle)
                .ok_or(AudioError::InvalidHandle)?;
            if !device.acquire_pool(pool) {
                return Err(AudioError::PoolExhausted);
            }
            let context = device.context.clone();
            let kind = match pool {
                ChannelPool::Static => SourceKind::Static(super::context::alto_call(
                    "Create static source",
                    context.new_static_source(),
                )?),
                ChannelPool::Streaming => SourceKind::Streaming(super::context::alto_call(
                    "Create streaming source",
                    context.new_streaming_source(),
                )?),
            };
            (context, kind)
        };
        drop(context);
        let handle = backend.sources.insert(NativeSource {
            device: device_handle,
            pool,
            kind,
        });
        unsafe {
            *output_handle = handle;
        }
        Ok(())
    }) {
        Ok(()) => OK,
        Err(error) => error.status(),
    }
}

/// # Safety
///
/// `source_handle` may be any value. Live handles are destroyed exactly once;
/// invalid or stale handles return `ERR_INVALID_HANDLE`.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_source_destroy(source_handle: u64) -> i32 {
    status(with_backend(|backend| {
        let mut source = backend
            .sources
            .remove(source_handle)
            .ok_or(AudioError::InvalidHandle)?;
        source.stop();
        if let Some(device) = backend.devices.get_mut(source.device) {
            device.release_pool(source.pool);
        }
        Ok(())
    }))
}

/// # Safety
///
/// `source_handle` must name a live source.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_source_play(source_handle: u64) -> i32 {
    status(with_source_mut(source_handle, |source| {
        source.play();
        Ok(())
    }))
}

/// # Safety
///
/// `source_handle` must name a live source.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_source_pause(source_handle: u64) -> i32 {
    status(with_source_mut(source_handle, |source| {
        source.pause();
        Ok(())
    }))
}

/// # Safety
///
/// `source_handle` must name a live source.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_source_stop(source_handle: u64) -> i32 {
    status(with_source_mut(source_handle, |source| {
        source.stop();
        Ok(())
    }))
}

/// # Safety
///
/// `output_state` must be writable. Invalid handles return stopped state plus
/// an error status so Java can fail safely.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_source_state(
    source_handle: u64,
    output_state: *mut i32,
) -> i32 {
    if output_state.is_null() {
        return ERR_INVALID_ARGUMENT;
    }
    match with_backend(|backend| {
        let state = backend
            .sources
            .get(source_handle)
            .ok_or(AudioError::InvalidHandle)?
            .state();
        unsafe {
            *output_state = state;
        }
        Ok(())
    }) {
        Ok(()) => OK,
        Err(error) => {
            unsafe {
                *output_state = AL_STOPPED;
            }
            error.status()
        }
    }
}

/// # Safety
///
/// `source_handle` must name a live source.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_source_set_position(
    source_handle: u64,
    x: f32,
    y: f32,
    z: f32,
) -> i32 {
    status(with_source_mut(source_handle, |source| {
        source.set_position(x, y, z)
    }))
}

/// # Safety
///
/// `source_handle` must name a live source.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_source_set_pitch(source_handle: u64, pitch: f32) -> i32 {
    status(with_source_mut(source_handle, |source| {
        source.set_pitch(pitch)
    }))
}

/// # Safety
///
/// `source_handle` must name a live source.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_source_set_volume(source_handle: u64, gain: f32) -> i32 {
    status(with_source_mut(source_handle, |source| {
        source.set_volume(gain)
    }))
}

/// # Safety
///
/// `source_handle` must name a live source.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_source_set_looping(source_handle: u64, looping: i32) -> i32 {
    status(with_source_mut(source_handle, |source| {
        source.set_looping(looping != 0);
        Ok(())
    }))
}

/// # Safety
///
/// `source_handle` must name a live source.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_source_set_relative(
    source_handle: u64,
    relative: i32,
) -> i32 {
    status(with_source_mut(source_handle, |source| {
        source.set_relative(relative != 0)
    }))
}

/// # Safety
///
/// `source_handle` must name a live source.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_source_disable_attenuation(source_handle: u64) -> i32 {
    status(with_source_mut(source_handle, |source| {
        source.disable_attenuation()
    }))
}

/// # Safety
///
/// `source_handle` must name a live source.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_source_linear_attenuation(
    source_handle: u64,
    distance: f32,
) -> i32 {
    status(with_source_mut(source_handle, |source| {
        source.linear_attenuation(distance)
    }))
}

/// # Safety
///
/// `data_ptr` must point to `data_len` readable PCM bytes. `output_handle`
/// must be writable. Java retains ownership of the input bytes; Rust copies
/// them into an OpenAL buffer before the call returns.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_buffer_create(
    device_handle: u64,
    data_ptr: *const u8,
    data_len: u64,
    channels: i32,
    bits: i32,
    pcm: i32,
    sample_rate: i32,
    output_handle: *mut u64,
) -> i32 {
    if output_handle.is_null() {
        return ERR_INVALID_ARGUMENT;
    }
    match with_backend(|backend| {
        let data = bytes_from_ptr(data_ptr, data_len)?;
        let context = backend
            .devices
            .get(device_handle)
            .ok_or(AudioError::InvalidHandle)?
            .context
            .clone();
        let buffer = create_buffer(&context, data, channels, bits, pcm != 0, sample_rate)?;
        let handle = backend.buffers.insert(NativeBuffer {
            device: device_handle,
            buffer: Arc::new(buffer),
        });
        unsafe {
            *output_handle = handle;
        }
        Ok(())
    }) {
        Ok(()) => OK,
        Err(error) => error.status(),
    }
}

/// # Safety
///
/// `buffer_handle` may be any value. Live handles are removed exactly once.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_buffer_destroy(buffer_handle: u64) -> i32 {
    status(with_backend(|backend| {
        backend
            .buffers
            .remove(buffer_handle)
            .ok_or(AudioError::InvalidHandle)?;
        Ok(())
    }))
}

/// # Safety
///
/// `source_handle` must name a live static source. `buffer_handle` must name a
/// live buffer from the same device.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_source_attach_static_buffer(
    source_handle: u64,
    buffer_handle: u64,
) -> i32 {
    status(with_backend(|backend| {
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
    }))
}

/// # Safety
///
/// `data_ptr` must point to `data_len` readable PCM bytes. Java retains the
/// input buffer; Rust copies the bytes into an OpenAL queue buffer.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_source_queue_stream_buffer(
    source_handle: u64,
    data_ptr: *const u8,
    data_len: u64,
    channels: i32,
    bits: i32,
    pcm: i32,
    sample_rate: i32,
) -> i32 {
    status(with_backend(|backend| {
        let data = bytes_from_ptr(data_ptr, data_len)?;
        let device_handle = backend
            .sources
            .get(source_handle)
            .ok_or(AudioError::InvalidHandle)?
            .device;
        let context = backend
            .devices
            .get(device_handle)
            .ok_or(AudioError::InvalidHandle)?
            .context
            .clone();
        let buffer = create_buffer(&context, data, channels, bits, pcm != 0, sample_rate)?;
        backend
            .sources
            .get_mut(source_handle)
            .ok_or(AudioError::InvalidHandle)?
            .queue_stream_buffer(buffer)
    }))
}

/// # Safety
///
/// `output_processed` must be writable.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_source_remove_processed_buffers(
    source_handle: u64,
    output_processed: *mut i32,
) -> i32 {
    if output_processed.is_null() {
        return ERR_INVALID_ARGUMENT;
    }
    match with_source_mut(source_handle, |source| {
        let processed = source.remove_processed_buffers()?;
        unsafe {
            *output_processed = processed;
        }
        Ok(())
    }) {
        Ok(()) => OK,
        Err(error) => error.status(),
    }
}

/// # Safety
///
/// `device_handle` must name a live device.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_listener_set_transform(
    device_handle: u64,
    px: f32,
    py: f32,
    pz: f32,
    fx: f32,
    fy: f32,
    fz: f32,
    ux: f32,
    uy: f32,
    uz: f32,
) -> i32 {
    status(with_backend(|backend| {
        let context = &backend
            .devices
            .get(device_handle)
            .ok_or(AudioError::InvalidHandle)?
            .context;
        super::context::alto_call("Set listener position", context.set_position([px, py, pz]))?;
        super::context::alto_call(
            "Set listener orientation",
            context.set_orientation(([fx, fy, fz], [ux, uy, uz])),
        )
    }))
}

/// # Safety
///
/// `device_handle` must name a live device.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_listener_reset(device_handle: u64) -> i32 {
    unsafe {
        mattmc_audio_listener_set_transform(
            device_handle,
            INITIAL_POSITION[0],
            INITIAL_POSITION[1],
            INITIAL_POSITION[2],
            INITIAL_FORWARD[0],
            INITIAL_FORWARD[1],
            INITIAL_FORWARD[2],
            INITIAL_UP[0],
            INITIAL_UP[1],
            INITIAL_UP[2],
        )
    }
}

/// # Safety
///
/// `device_handle` must name a live device.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_listener_set_gain(device_handle: u64, gain: f32) -> i32 {
    status(with_backend(|backend| {
        let context = &backend
            .devices
            .get(device_handle)
            .ok_or(AudioError::InvalidHandle)?
            .context;
        super::context::alto_call("Set listener gain", context.set_gain(gain))
    }))
}

/// # Safety
///
/// `output_connected` must be writable.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_device_is_disconnected(
    device_handle: u64,
    output_disconnected: *mut i32,
) -> i32 {
    if output_disconnected.is_null() {
        return ERR_INVALID_ARGUMENT;
    }
    match with_backend(|backend| {
        let disconnected = backend
            .devices
            .get(device_handle)
            .ok_or(AudioError::InvalidHandle)?
            .is_disconnected();
        unsafe {
            *output_disconnected = if disconnected { 1 } else { 0 };
        }
        Ok(())
    }) {
        Ok(()) => OK,
        Err(error) => error.status(),
    }
}

/// # Safety
///
/// `output_changed` must be writable.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_device_has_default_changed(
    device_handle: u64,
    output_changed: *mut i32,
) -> i32 {
    if output_changed.is_null() {
        return ERR_INVALID_ARGUMENT;
    }
    match with_backend(|backend| {
        let current = backend.openal()?.0.default_output().map(cstring_to_string);
        let device = backend
            .devices
            .get_mut(device_handle)
            .ok_or(AudioError::InvalidHandle)?;
        let changed = device.default_device_name != current;
        if changed {
            device.default_device_name = current;
        }
        unsafe {
            *output_changed = if changed { 1 } else { 0 };
        }
        Ok(())
    }) {
        Ok(()) => OK,
        Err(error) => error.status(),
    }
}

/// # Safety
///
/// `output_counts` must point to four writable `i32` values. The values are
/// static-used, static-limit, streaming-used, streaming-limit.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_device_pool_counts(
    device_handle: u64,
    output_counts: *mut i32,
) -> i32 {
    if output_counts.is_null() {
        return ERR_INVALID_ARGUMENT;
    }
    match with_backend(|backend| {
        let device = backend
            .devices
            .get(device_handle)
            .ok_or(AudioError::InvalidHandle)?;
        unsafe {
            *output_counts.add(0) = device.static_used as i32;
            *output_counts.add(1) = device.static_limit as i32;
            *output_counts.add(2) = device.streaming_used as i32;
            *output_counts.add(3) = device.streaming_limit as i32;
        }
        Ok(())
    }) {
        Ok(()) => OK,
        Err(error) => error.status(),
    }
}

/// # Safety
///
/// `out_len` must be writable. When `out_ptr` is non-null, it must point to
/// `out_capacity` writable bytes. The function writes the required UTF-8 byte
/// length to `out_len`, excluding any terminator.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_default_device_name(
    out_ptr: *mut u8,
    out_capacity: u64,
    out_len: *mut u64,
) -> i32 {
    write_string(out_ptr, out_capacity, out_len, |backend| {
        Ok(backend
            .openal()?
            .0
            .default_output()
            .map(cstring_to_string)
            .unwrap_or_default())
    })
}

/// # Safety
///
/// Same output-buffer contract as `mattmc_audio_default_device_name`.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_current_device_name(
    device_handle: u64,
    out_ptr: *mut u8,
    out_capacity: u64,
    out_len: *mut u64,
) -> i32 {
    write_string(out_ptr, out_capacity, out_len, |backend| {
        Ok(backend
            .devices
            .get(device_handle)
            .ok_or(AudioError::InvalidHandle)?
            .current_device_name
            .clone())
    })
}

/// # Safety
///
/// Same output-buffer contract as `mattmc_audio_default_device_name`. Device
/// names are separated by newline characters.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_available_devices(
    out_ptr: *mut u8,
    out_capacity: u64,
    out_len: *mut u64,
) -> i32 {
    write_string(out_ptr, out_capacity, out_len, |backend| {
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

/// # Safety
///
/// `output_format` must be writable.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_format_to_openal(
    channels: i32,
    bits: i32,
    pcm: i32,
    output_format: *mut i32,
) -> i32 {
    if output_format.is_null() {
        return ERR_INVALID_ARGUMENT;
    }
    match audio_format_to_openal(channels, bits, pcm != 0) {
        Ok(format) => {
            unsafe {
                *output_format = format;
            }
            OK
        }
        Err(error) => error.status(),
    }
}

fn with_source_mut(
    source_handle: u64,
    f: impl FnOnce(&mut NativeSource) -> AudioResult<()>,
) -> AudioResult<()> {
    with_backend(|backend| {
        let source = backend
            .sources
            .get_mut(source_handle)
            .ok_or(AudioError::InvalidHandle)?;
        f(source)
    })
}

unsafe fn ptr_to_string(ptr: *const u8, len: u64) -> AudioResult<Option<String>> {
    if ptr.is_null() {
        return if len == 0 {
            Ok(None)
        } else {
            Err(AudioError::InvalidArgument)
        };
    }
    let bytes = bytes_from_ptr(ptr, len)?;
    std::str::from_utf8(bytes)
        .map(|value| Some(value.to_string()))
        .map_err(|_| AudioError::InvalidArgument)
}

unsafe fn bytes_from_ptr<'a>(ptr: *const u8, len: u64) -> AudioResult<&'a [u8]> {
    let len = usize::try_from(len).map_err(|_| AudioError::InvalidArgument)?;
    if ptr.is_null() {
        return if len == 0 {
            Ok(&[])
        } else {
            Err(AudioError::InvalidArgument)
        };
    }
    if len > isize::MAX as usize {
        return Err(AudioError::InvalidArgument);
    }
    Ok(unsafe { slice::from_raw_parts(ptr, len) })
}

fn write_string(
    out_ptr: *mut u8,
    out_capacity: u64,
    out_len: *mut u64,
    build: impl FnOnce(&mut AudioBackend) -> AudioResult<String>,
) -> i32 {
    if out_len.is_null() {
        return ERR_INVALID_ARGUMENT;
    }
    match with_backend(build).and_then(|value| unsafe {
        let bytes = value.as_bytes();
        *out_len = bytes.len() as u64;
        if out_ptr.is_null() {
            return Ok(());
        }
        let capacity = usize::try_from(out_capacity).map_err(|_| AudioError::InvalidArgument)?;
        if capacity < bytes.len() {
            return Err(AudioError::InvalidArgument);
        }
        ptr::copy_nonoverlapping(bytes.as_ptr(), out_ptr, bytes.len());
        Ok(())
    }) {
        Ok(()) => OK,
        Err(error) => error.status(),
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
}

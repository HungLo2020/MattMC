use std::ptr;
use std::slice;

use super::backend;
use super::commands::{ListenerStateRecord, SoundConfigRecord, StreamChunkRecord};
use super::device::ChannelPool;
use super::errors::*;
use super::format::audio_format_to_openal;
use super::listener::ListenerTransform;
use super::source::AL_STOPPED;

fn status(result: AudioResult<()>) -> i32 {
    match result {
        Ok(()) => OK,
        Err(error) => error.status(),
    }
}

fn status_with_output<T>(result: AudioResult<T>, output: *mut T, fallback: Option<T>) -> i32
where
    T: Copy,
{
    if output.is_null() {
        return ERR_INVALID_ARGUMENT;
    }
    match result {
        Ok(value) => {
            unsafe {
                *output = value;
            }
            OK
        }
        Err(error) => {
            if let Some(value) = fallback {
                unsafe {
                    *output = value;
                }
            }
            error.status()
        }
    }
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
    let preferred = match unsafe { ptr_to_string(preferred_ptr, preferred_len) } {
        Ok(preferred) => preferred,
        Err(error) => return error.status(),
    };
    status_with_output(
        backend::create_device(preferred, hrtf != 0),
        output_handle,
        None,
    )
}

/// # Safety
///
/// `device_handle` may be any value. Live devices are destroyed exactly once;
/// stale or invalid handles return `ERR_INVALID_HANDLE` without touching other
/// live devices.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_device_destroy(device_handle: u64) -> i32 {
    status(backend::destroy_device(device_handle))
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
    let pool = match ChannelPool::from_id(pool_id) {
        Some(pool) => pool,
        None => return ERR_INVALID_ARGUMENT,
    };
    status_with_output(
        backend::create_source(device_handle, pool),
        output_handle,
        None,
    )
}

/// # Safety
///
/// `source_handle` may be any value. Live handles are destroyed exactly once;
/// invalid or stale handles return `ERR_INVALID_HANDLE`.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_source_destroy(source_handle: u64) -> i32 {
    status(backend::destroy_source(source_handle))
}

/// # Safety
///
/// `source_handle` must name a live source.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_source_play(source_handle: u64) -> i32 {
    status(backend::source_play(source_handle))
}

/// # Safety
///
/// `source_handle` must name a live source.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_source_pause(source_handle: u64) -> i32 {
    status(backend::source_pause(source_handle))
}

/// # Safety
///
/// `source_handle` must name a live source.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_source_stop(source_handle: u64) -> i32 {
    status(backend::source_stop(source_handle))
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
    status_with_output(
        backend::source_state(source_handle),
        output_state,
        Some(AL_STOPPED),
    )
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
    status(backend::source_set_position(source_handle, x, y, z))
}

/// # Safety
///
/// `source_handle` must name a live source.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_source_set_pitch(source_handle: u64, pitch: f32) -> i32 {
    status(backend::source_set_pitch(source_handle, pitch))
}

/// # Safety
///
/// `source_handle` must name a live source.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_source_set_volume(source_handle: u64, gain: f32) -> i32 {
    status(backend::source_set_volume(source_handle, gain))
}

/// # Safety
///
/// `source_handle` must name a live source.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_source_set_looping(source_handle: u64, looping: i32) -> i32 {
    status(backend::source_set_looping(source_handle, looping != 0))
}

/// # Safety
///
/// `source_handle` must name a live source.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_source_set_relative(
    source_handle: u64,
    relative: i32,
) -> i32 {
    status(backend::source_set_relative(source_handle, relative != 0))
}

/// # Safety
///
/// `source_handle` must name a live source.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_source_disable_attenuation(source_handle: u64) -> i32 {
    status(backend::source_disable_attenuation(source_handle))
}

/// # Safety
///
/// `source_handle` must name a live source.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_source_linear_attenuation(
    source_handle: u64,
    distance: f32,
) -> i32 {
    status(backend::source_linear_attenuation(source_handle, distance))
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
    let data = match unsafe { bytes_from_ptr(data_ptr, data_len) } {
        Ok(data) => data,
        Err(error) => return error.status(),
    };
    status_with_output(
        backend::create_buffer_handle(device_handle, data, channels, bits, pcm != 0, sample_rate),
        output_handle,
        None,
    )
}

/// # Safety
///
/// `buffer_handle` may be any value. Live handles are removed exactly once.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_buffer_destroy(buffer_handle: u64) -> i32 {
    status(backend::destroy_buffer(buffer_handle))
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
    status(backend::attach_static_buffer(source_handle, buffer_handle))
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
    let data = match unsafe { bytes_from_ptr(data_ptr, data_len) } {
        Ok(data) => data,
        Err(error) => return error.status(),
    };
    status(backend::queue_stream_buffer(
        source_handle,
        data,
        channels,
        bits,
        pcm != 0,
        sample_rate,
    ))
}

/// # Safety
///
/// `output_processed` must be writable.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_source_remove_processed_buffers(
    source_handle: u64,
    output_processed: *mut i32,
) -> i32 {
    status_with_output(
        backend::remove_processed_stream_buffers(source_handle),
        output_processed,
        None,
    )
}

/// # Safety
///
/// `config_ptr` must point to one readable `SoundConfigRecord`, and
/// `output_handle` must be writable. `buffer_handle` must name a live static
/// PCM buffer owned by the same device. Rust creates and configures the OpenAL
/// source, attaches the buffer, and returns the owned native sound handle.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_sound_create_static(
    device_handle: u64,
    config_ptr: *const SoundConfigRecord,
    buffer_handle: u64,
    output_handle: *mut u64,
) -> i32 {
    if output_handle.is_null() {
        return ERR_INVALID_ARGUMENT;
    }
    let config = match unsafe { value_from_ptr(config_ptr) } {
        Ok(config) => config,
        Err(error) => return error.status(),
    };
    status_with_output(
        backend::create_static_sound(device_handle, config, buffer_handle),
        output_handle,
        None,
    )
}

/// # Safety
///
/// `config_ptr` must point to one readable `SoundConfigRecord`, and
/// `output_handle` must be writable. Rust creates the streaming OpenAL source
/// and owns all queued stream buffers submitted later for this handle.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_sound_create_streaming(
    device_handle: u64,
    config_ptr: *const SoundConfigRecord,
    output_handle: *mut u64,
) -> i32 {
    if output_handle.is_null() {
        return ERR_INVALID_ARGUMENT;
    }
    let config = match unsafe { value_from_ptr(config_ptr) } {
        Ok(config) => config,
        Err(error) => return error.status(),
    };
    status_with_output(
        backend::create_streaming_sound(device_handle, config),
        output_handle,
        None,
    )
}

/// # Safety
///
/// `config_ptr` must point to one readable `SoundConfigRecord`. `update_mask`
/// selects which fields Rust applies to the owned native source.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_sound_update(
    sound_handle: u64,
    update_mask: u32,
    config_ptr: *const SoundConfigRecord,
) -> i32 {
    let config = match unsafe { value_from_ptr(config_ptr) } {
        Ok(config) => config,
        Err(error) => return error.status(),
    };
    status(backend::update_sound(sound_handle, update_mask, config))
}

/// # Safety
///
/// `chunks_ptr` must point to `chunk_count` readable `StreamChunkRecord`
/// entries. Each record must point to readable PCM bytes for the duration of
/// the call. Rust copies every accepted chunk into an OpenAL queue buffer.
/// `output_accepted` must be writable.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_sound_submit_stream_chunks(
    sound_handle: u64,
    chunks_ptr: *const StreamChunkRecord,
    chunk_count: u64,
    channels: i32,
    bits: i32,
    pcm: i32,
    sample_rate: i32,
    output_accepted: *mut i32,
) -> i32 {
    if output_accepted.is_null() {
        return ERR_INVALID_ARGUMENT;
    }
    let chunks = match unsafe { stream_chunks_from_ptr(chunks_ptr, chunk_count) } {
        Ok(chunks) => chunks,
        Err(error) => return error.status(),
    };
    status_with_output(
        backend::submit_stream_chunks(sound_handle, &chunks, channels, bits, pcm != 0, sample_rate),
        output_accepted,
        Some(0),
    )
}

/// # Safety
///
/// `sound_handle` must name a live native sound.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_sound_play(sound_handle: u64) -> i32 {
    status(backend::source_play(sound_handle))
}

/// # Safety
///
/// `sound_handle` must name a live native sound.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_sound_pause(sound_handle: u64) -> i32 {
    status(backend::source_pause(sound_handle))
}

/// # Safety
///
/// `sound_handle` must name a live native sound.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_sound_stop(sound_handle: u64) -> i32 {
    status(backend::source_stop(sound_handle))
}

/// # Safety
///
/// `output_state` must be writable. Invalid handles return stopped state plus
/// an error status so Java can fail safely.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_sound_state(
    sound_handle: u64,
    output_state: *mut i32,
) -> i32 {
    status_with_output(
        backend::source_state(sound_handle),
        output_state,
        Some(AL_STOPPED),
    )
}

/// # Safety
///
/// `sound_handle` may be any value. Live sounds are stopped, their queued
/// stream buffers are dropped, and their pool slot is released exactly once.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_sound_stop_and_destroy(sound_handle: u64) -> i32 {
    status(backend::destroy_source(sound_handle))
}

/// # Safety
///
/// `output_processed` must be writable.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_sound_remove_processed_stream_buffers(
    sound_handle: u64,
    output_processed: *mut i32,
) -> i32 {
    status_with_output(
        backend::remove_processed_stream_buffers(sound_handle),
        output_processed,
        None,
    )
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
    status(backend::listener_set_transform(
        device_handle,
        ListenerTransform {
            position: [px, py, pz],
            forward: [fx, fy, fz],
            up: [ux, uy, uz],
        },
    ))
}

/// # Safety
///
/// `listener_state` must point to one readable `ListenerStateRecord`.
/// Java supplies policy-computed transform/gain values; Rust applies them to
/// the listener for the live device/context.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_listener_update(
    device_handle: u64,
    listener_state: *const ListenerStateRecord,
) -> i32 {
    let listener_state = match unsafe { value_from_ptr(listener_state) } {
        Ok(listener_state) => listener_state,
        Err(error) => return error.status(),
    };
    status(backend::update_listener(device_handle, listener_state))
}

/// # Safety
///
/// `device_handle` must name a live device.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_listener_reset(device_handle: u64) -> i32 {
    status(backend::listener_reset(device_handle))
}

/// # Safety
///
/// `device_handle` must name a live device.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_listener_set_gain(device_handle: u64, gain: f32) -> i32 {
    status(backend::listener_set_gain(device_handle, gain))
}

/// # Safety
///
/// `output_disconnected` must be writable.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_device_is_disconnected(
    device_handle: u64,
    output_disconnected: *mut i32,
) -> i32 {
    status_with_output(
        backend::device_is_disconnected(device_handle).map(|value| if value { 1 } else { 0 }),
        output_disconnected,
        None,
    )
}

/// # Safety
///
/// `output_changed` must be writable.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_device_has_default_changed(
    device_handle: u64,
    output_changed: *mut i32,
) -> i32 {
    status_with_output(
        backend::device_has_default_changed(device_handle).map(|value| if value { 1 } else { 0 }),
        output_changed,
        None,
    )
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
    match backend::device_pool_counts(device_handle) {
        Ok(counts) => {
            unsafe {
                *output_counts.add(0) = counts.static_used;
                *output_counts.add(1) = counts.static_limit;
                *output_counts.add(2) = counts.streaming_used;
                *output_counts.add(3) = counts.streaming_limit;
            }
            OK
        }
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
    write_string(out_ptr, out_capacity, out_len, backend::default_device_name)
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
    write_string(out_ptr, out_capacity, out_len, || {
        backend::current_device_name(device_handle)
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
    write_string(out_ptr, out_capacity, out_len, backend::available_devices)
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
    status_with_output(
        audio_format_to_openal(channels, bits, pcm != 0),
        output_format,
        None,
    )
}

unsafe fn ptr_to_string(ptr: *const u8, len: u64) -> AudioResult<Option<String>> {
    if ptr.is_null() {
        return if len == 0 {
            Ok(None)
        } else {
            Err(AudioError::InvalidArgument)
        };
    }
    let bytes = unsafe { bytes_from_ptr(ptr, len) }?;
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

unsafe fn value_from_ptr<T: Copy>(ptr: *const T) -> AudioResult<T> {
    if ptr.is_null() {
        return Err(AudioError::InvalidArgument);
    }
    Ok(unsafe { *ptr })
}

unsafe fn stream_chunks_from_ptr<'a>(
    ptr: *const StreamChunkRecord,
    len: u64,
) -> AudioResult<Vec<&'a [u8]>> {
    let len = usize::try_from(len).map_err(|_| AudioError::InvalidArgument)?;
    if ptr.is_null() {
        return if len == 0 {
            Ok(Vec::new())
        } else {
            Err(AudioError::InvalidArgument)
        };
    }
    if len > isize::MAX as usize / std::mem::size_of::<StreamChunkRecord>() {
        return Err(AudioError::InvalidArgument);
    }
    let records = unsafe { slice::from_raw_parts(ptr, len) };
    let mut chunks = Vec::with_capacity(records.len());
    for record in records {
        chunks.push(unsafe { bytes_from_ptr(record.data_ptr, record.data_len) }?);
    }
    Ok(chunks)
}

fn write_string(
    out_ptr: *mut u8,
    out_capacity: u64,
    out_len: *mut u64,
    build: impl FnOnce() -> AudioResult<String>,
) -> i32 {
    if out_len.is_null() {
        return ERR_INVALID_ARGUMENT;
    }
    match build().and_then(|value| unsafe {
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

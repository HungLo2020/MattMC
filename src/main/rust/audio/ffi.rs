use std::ptr;
use std::slice;

use super::backend;
use super::commands::{ListenerStateRecord, SoundConfigRecord, StaticDecodeParityRecord};
use super::decoder::MAX_DECODED_PCM_BYTES;
use super::errors::*;
use super::format::audio_format_to_openal;
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
/// `data_ptr` must point to `data_len` readable encoded audio bytes for the
/// duration of the call. `debug_name_ptr` must be null with length zero or
/// point to `debug_name_len` readable UTF-8 bytes. `output_handle` must be
/// writable. Rust copies both byte ranges before returning and never retains
/// Java-owned pointers.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_asset_create(
    data_ptr: *const u8,
    data_len: u64,
    debug_name_ptr: *const u8,
    debug_name_len: u64,
    reload_generation: u64,
    output_handle: *mut u64,
) -> i32 {
    if output_handle.is_null() {
        return ERR_INVALID_ARGUMENT;
    }
    let data = match unsafe { bytes_from_ptr(data_ptr, data_len) } {
        Ok(data) => data,
        Err(error) => return error.status(),
    };
    let debug_name = match unsafe { ptr_to_string(debug_name_ptr, debug_name_len) } {
        Ok(value) => value,
        Err(error) => return error.status(),
    };
    status_with_output(
        backend::create_asset(data, debug_name, reload_generation),
        output_handle,
        None,
    )
}

/// # Safety
///
/// `asset_handle` may be any value. Live encoded audio assets are removed
/// exactly once; stale, wrong-kind, or invalid handles return
/// `ERR_INVALID_HANDLE` without touching unrelated resources.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_asset_destroy(asset_handle: u64) -> i32 {
    status(backend::destroy_asset(asset_handle))
}

/// # Safety
///
/// Destroys all encoded assets whose reload generation is at or before
/// `reload_generation`, then rejects future registrations for those stale
/// generations. This function does not touch OpenAL device/source/buffer
/// handles.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_asset_destroy_generation(reload_generation: u64) -> i32 {
    status(backend::destroy_asset_generation(reload_generation))
}

/// # Safety
///
/// `java_pcm_ptr` must point to `java_pcm_len` readable Java-decoded PCM bytes
/// for the duration of the call. `output_record` must point to one writable
/// `StaticDecodeParityRecord`. Rust decodes the encoded asset behind
/// `asset_handle`, compares internally, and writes only compact mismatch
/// metadata back to Java.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_asset_compare_static_pcm(
    asset_handle: u64,
    java_pcm_ptr: *const u8,
    java_pcm_len: u64,
    channels: i32,
    bits: i32,
    pcm: i32,
    sample_rate: i32,
    output_record: *mut StaticDecodeParityRecord,
) -> i32 {
    if output_record.is_null() {
        return ERR_INVALID_ARGUMENT;
    }
    if java_pcm_len > MAX_DECODED_PCM_BYTES as u64 {
        return ERR_INVALID_ARGUMENT;
    }
    let java_pcm = match unsafe { bytes_from_ptr(java_pcm_ptr, java_pcm_len) } {
        Ok(data) => data,
        Err(error) => return error.status(),
    };
    status_with_output(
        backend::compare_asset_static_pcm(
            asset_handle,
            java_pcm,
            channels,
            bits,
            pcm != 0,
            sample_rate,
        ),
        output_record,
        Some(StaticDecodeParityRecord::default()),
    )
}

/// # Safety
///
/// `config_ptr` must point to one readable `SoundConfigRecord`, and
/// `output_handle` must be writable. `asset_handle` must name a live encoded
/// Ogg Vorbis audio asset. Rust lazily decodes the asset, owns the device-local
/// OpenAL buffer cache, creates/configures the source, and returns the owned
/// native sound handle.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_sound_create_static_from_asset(
    device_handle: u64,
    config_ptr: *const SoundConfigRecord,
    asset_handle: u64,
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
        backend::create_static_sound(device_handle, config, asset_handle),
        output_handle,
        None,
    )
}

/// # Safety
///
/// `config_ptr` must point to one readable `SoundConfigRecord`, and
/// `output_handle` must be writable. `asset_handle` must name a live encoded
/// Ogg Vorbis asset. Rust creates a streaming OpenAL source, owns an
/// independent decoder cursor, and pre-fills/refills the queue on device ticks.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_sound_create_streaming_from_asset(
    device_handle: u64,
    config_ptr: *const SoundConfigRecord,
    asset_handle: u64,
    looping: i32,
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
        backend::create_streaming_sound_from_asset(
            device_handle,
            config,
            asset_handle,
            looping != 0,
        ),
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
/// `device_handle` may be any value. Live devices must be ticked from their
/// owning sound thread. Rust removes processed streaming buffers and refills all
/// active asset-backed streams for that device.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_device_tick(device_handle: u64) -> i32 {
    status(backend::tick_device(device_handle))
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
/// `output_counts` must point to eight writable `i32` values. The values are
/// live devices, live sounds/sources, Java-visible static buffers, queued
/// streaming buffers, encoded assets, decoded static-cache entries, and stream
/// decoders, plus live-stream decoded chunk count. This diagnostic API is used
/// by dev-only validation hooks and does not require a live device handle.
#[no_mangle]
pub unsafe extern "C" fn mattmc_audio_debug_live_counts(output_counts: *mut i32) -> i32 {
    if output_counts.is_null() {
        return ERR_INVALID_ARGUMENT;
    }
    match backend::live_counts() {
        Ok(counts) => {
            unsafe {
                *output_counts.add(0) = counts.devices as i32;
                *output_counts.add(1) = counts.sources as i32;
                *output_counts.add(2) = counts.buffers as i32;
                *output_counts.add(3) = counts.queued_stream_buffers;
                *output_counts.add(4) = counts.assets as i32;
                *output_counts.add(5) = counts.static_cache_entries as i32;
                *output_counts.add(6) = counts.stream_decoders as i32;
                *output_counts.add(7) = counts.stream_decoded_chunks.min(i32::MAX as u64) as i32;
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

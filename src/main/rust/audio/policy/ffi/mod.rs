//! C ABI for the Java sound layer (`NativeSoundPolicy`).
//!
//! Strings are UTF-8 (pointer, length). Variable-length results are written
//! into caller buffers: a call returns the needed length and copies only when
//! the buffer is large enough. Java instance IDs are positive and below
//! [`NATIVE_INSTANCE`](super::client::NATIVE_INSTANCE).
//!
//! [`engine`] covers the device, Java instance playback and ticks;
//! [`client`] the Rust-owned sounds (events, music, ambience, preview),
//! subtitles and log lines. This module holds the registry and resolution.

mod client;
mod engine;

use std::collections::HashSet;
use std::ptr;
use std::slice;
use std::sync::{Arc, LazyLock, RwLock};

use super::super::errors::{ERR_INVALID_ARGUMENT, OK};
use super::events::{RegistryBuilder, Resolution, SoundRegistry};
use super::thread::ENGINE;

/// The installed registry of the latest resource reload, for lock-free
/// reads by Java resolution; the client holds the same `Arc`.
static ACTIVE: LazyLock<RwLock<Arc<SoundRegistry>>> =
    LazyLock::new(|| RwLock::new(Arc::new(SoundRegistry::default())));

/// Result bit: asset load requests are waiting to be taken.
const ASSET_REQUESTS: i32 = 0x100;

const RESOLVE_UNKNOWN: i32 = 0;
const RESOLVE_NEED_DRAW: i32 = 1;
const RESOLVE_EMPTY: i32 = 2;
const RESOLVE_SOUND: i32 = 3;
const RESOLVED_STREAM: u32 = 1 << 0;
const RESOLVED_PRELOAD: u32 = 1 << 1;

fn active() -> Arc<SoundRegistry> {
    ACTIVE.read().unwrap_or_else(|poisoned| poisoned.into_inner()).clone()
}

unsafe fn str_arg<'a>(ptr: *const u8, len: u64) -> Option<&'a str> {
    if ptr.is_null() {
        return if len == 0 { Some("") } else { None };
    }
    std::str::from_utf8(unsafe { slice::from_raw_parts(ptr, usize::try_from(len).ok()?) }).ok()
}

/// An optional string: null is absent.
unsafe fn optional_str<'a>(ptr: *const u8, len: u64) -> Option<&'a str> {
    if ptr.is_null() {
        None
    } else {
        unsafe { str_arg(ptr, len) }
    }
}

/// Copies `text` when it fits; returns its length either way.
unsafe fn write_text(text: &str, out: *mut u8, capacity: u64) -> i64 {
    if !out.is_null() && text.len() as u64 <= capacity {
        unsafe { ptr::copy_nonoverlapping(text.as_ptr(), out, text.len()) };
    }
    text.len() as i64
}

/// One `sounds.json` resource in Java's stack order.
#[repr(C)]
pub struct FfiSoundResource {
    pub namespace: *const u8,
    pub namespace_len: u64,
    pub pack: *const u8,
    pub pack_len: u64,
    pub json: *const u8,
    pub json_len: u64,
}

/// Builds a registry from `count` resources and the `\n`-separated list of
/// existing `ns:sounds/....ogg` files. Returns an owned handle (0 on bad input).
///
/// # Safety
/// `resources` must point to `count` records whose pointers are valid for
/// their lengths; `files` must be valid for `files_len` bytes.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sound_registry_build(
    generation: u64,
    resources: *const FfiSoundResource,
    count: u64,
    files: *const u8,
    files_len: u64,
) -> u64 {
    let Some(files) = (unsafe { str_arg(files, files_len) }) else { return 0 };
    let available: HashSet<String> = files.split('\n').filter(|f| !f.is_empty()).map(str::to_owned).collect();
    let mut builder = RegistryBuilder::new(generation, available);
    if count > 0 {
        if resources.is_null() {
            return 0;
        }
        for resource in unsafe { slice::from_raw_parts(resources, count as usize) } {
            let (Some(namespace), Some(pack), Some(json)) = (unsafe {
                (
                    str_arg(resource.namespace, resource.namespace_len),
                    str_arg(resource.pack, resource.pack_len),
                    str_arg(resource.json, resource.json_len),
                )
            }) else {
                return 0;
            };
            builder.add_resource(namespace, pack, json);
        }
    }
    Box::into_raw(Box::new(builder.build())) as u64
}

/// The build warnings, `\n`-separated. Returns the needed length.
///
/// # Safety
/// `handle` must come from `mattmc_sound_registry_build` and not be consumed;
/// `out` must be writable for `capacity` bytes.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sound_registry_warnings(handle: u64, out: *mut u8, capacity: u64) -> i64 {
    if handle == 0 {
        return -1;
    }
    let registry = unsafe { &*(handle as *const SoundRegistry) };
    unsafe { write_text(&registry.warnings.join("\n"), out, capacity) }
}

/// Installs (and consumes) a built registry.
///
/// # Safety
/// `handle` must come from `mattmc_sound_registry_build` and not be consumed.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sound_registry_install(handle: u64) -> i32 {
    if handle == 0 {
        return ERR_INVALID_ARGUMENT;
    }
    let registry: Arc<SoundRegistry> = Arc::from(unsafe { Box::from_raw(handle as *mut SoundRegistry) });
    *ACTIVE.write().unwrap_or_else(|poisoned| poisoned.into_inner()) = registry.clone();
    ENGINE.with_client(|client| client.registry = registry);
    OK
}

/// Frees a built registry that will not be installed.
///
/// # Safety
/// `handle` must come from `mattmc_sound_registry_build` and not be consumed.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sound_registry_discard(handle: u64) {
    if handle != 0 {
        drop(unsafe { Box::from_raw(handle as *mut SoundRegistry) });
    }
}

/// Every registered event name, `\n`-separated (`getAvailableSounds`).
///
/// # Safety
/// `out` must be writable for `capacity` bytes.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sound_event_names(out: *mut u8, capacity: u64) -> i64 {
    let registry = active();
    let names: Vec<&str> = registry.names.iter().map(|name| &**name).collect();
    unsafe { write_text(&names.join("\n"), out, capacity) }
}

/// Looks up an event: returns -2 when unknown, -1 when it has no subtitle,
/// else the subtitle key's length (copied when it fits).
///
/// # Safety
/// The name must be valid for `name_len` bytes; `out` writable for `capacity`.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sound_event_lookup(
    name: *const u8,
    name_len: u64,
    out: *mut u8,
    capacity: u64,
) -> i64 {
    let Some(name) = (unsafe { str_arg(name, name_len) }) else { return -2 };
    let registry = active();
    let Some(event) = registry.event_index(name) else { return -2 };
    match &registry.events[event as usize].subtitle {
        Some(subtitle) => unsafe { write_text(subtitle, out, capacity) },
        None => -1,
    }
}

/// `WeighedSoundEvents.getWeight()` (0 for an unknown event).
///
/// # Safety
/// The name must be valid for `name_len` bytes.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sound_event_weight(name: *const u8, name_len: u64) -> i32 {
    let Some(name) = (unsafe { str_arg(name, name_len) }) else { return 0 };
    let registry = active();
    registry.event_index(name).map_or(0, |event| registry.event_weight(event))
}

#[repr(C)]
#[derive(Default)]
pub struct FfiResolvedSound {
    pub bound: i32,
    pub weight: i32,
    pub volume: f32,
    pub pitch: f32,
    pub attenuation_distance: i32,
    pub flags: u32,
    /// Length of the location text (copied when it fits the buffer).
    pub location_len: u32,
}

/// Resolves `name` with `draws` (the instance random's `nextInt` results so
/// far). Returns `RESOLVE_*`; for `RESOLVE_NEED_DRAW` the caller draws
/// `nextInt(out.bound)` and calls again with the extra draw.
///
/// # Safety
/// The name must be valid for `name_len` bytes, `draws` for `draw_count`
/// values, `out` writable and `location` writable for `capacity` bytes.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sound_resolve(
    name: *const u8,
    name_len: u64,
    draws: *const i32,
    draw_count: u64,
    out: *mut FfiResolvedSound,
    location: *mut u8,
    capacity: u64,
) -> i32 {
    let (Some(name), false) = (unsafe { str_arg(name, name_len) }, out.is_null()) else {
        return ERR_INVALID_ARGUMENT;
    };
    let draws = if draw_count == 0 || draws.is_null() {
        &[][..]
    } else {
        unsafe { slice::from_raw_parts(draws, draw_count as usize) }
    };
    let out = unsafe { &mut *out };
    *out = FfiResolvedSound::default();
    match active().resolve(name, draws) {
        Resolution::UnknownEvent => RESOLVE_UNKNOWN,
        Resolution::NeedDraw(bound) => {
            out.bound = bound;
            RESOLVE_NEED_DRAW
        }
        Resolution::Empty => RESOLVE_EMPTY,
        Resolution::Sound(sound) => {
            out.weight = sound.weight;
            out.volume = sound.volume;
            out.pitch = sound.pitch;
            out.attenuation_distance = sound.attenuation_distance;
            out.flags = (if sound.stream { RESOLVED_STREAM } else { 0 })
                | (if sound.preload { RESOLVED_PRELOAD } else { 0 });
            out.location_len = unsafe { write_text(&sound.location, location, capacity) } as u32;
            RESOLVE_SOUND
        }
    }
}

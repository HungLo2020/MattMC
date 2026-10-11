//! C ABI for the Java sound policy layer (`NativeSoundPolicy`).
//!
//! Strings are UTF-8 (pointer, length). Variable-length results are written
//! into caller buffers: a call returns the needed length and copies only when
//! the buffer is large enough. Instance IDs are Java-assigned and positive.

use std::collections::HashSet;
use std::ptr;
use std::slice;
use std::sync::{Arc, LazyLock, Mutex, MutexGuard, RwLock};

use super::super::backend;
use super::super::errors::{ERR_INVALID_ARGUMENT, OK};
use super::super::listener::ListenerTransform;
use super::engine::{PlayRequest, PlayResult, Released, CATEGORY_COUNT};
use super::events::{RegistryBuilder, Resolution, SoundRegistry};
use super::thread::ENGINE;

/// The installed registry of the latest resource reload.
static ACTIVE: LazyLock<RwLock<Arc<SoundRegistry>>> =
    LazyLock::new(|| RwLock::new(Arc::new(SoundRegistry::default())));
/// Tick results not yet taken by Java.
static RELEASED: Mutex<Vec<Released>> = Mutex::new(Vec::new());
static DUE: Mutex<Vec<u64>> = Mutex::new(Vec::new());

/// Bit 63 of a released instance ID: requeued for a manual loop.
const RELEASED_REQUEUED: u64 = 1 << 63;
/// `play` result bit: asset load requests are waiting to be taken.
const PLAY_ASSET_REQUESTS: i32 = 0x100;

const PLAY_FLAG_LINEAR: u32 = 1 << 0;
const PLAY_FLAG_RELATIVE: u32 = 1 << 1;
const PLAY_FLAG_LOOPING: u32 = 1 << 2;
const PLAY_FLAG_STREAM: u32 = 1 << 3;
const PLAY_FLAG_CAN_START_SILENT: u32 = 1 << 4;

const RESOLVE_UNKNOWN: i32 = 0;
const RESOLVE_NEED_DRAW: i32 = 1;
const RESOLVE_EMPTY: i32 = 2;
const RESOLVE_SOUND: i32 = 3;
const RESOLVED_STREAM: u32 = 1 << 0;
const RESOLVED_PRELOAD: u32 = 1 << 1;

fn lock<T>(mutex: &Mutex<T>) -> MutexGuard<'_, T> {
    mutex.lock().unwrap_or_else(|poisoned| poisoned.into_inner())
}

fn active() -> Arc<SoundRegistry> {
    ACTIVE.read().unwrap_or_else(|poisoned| poisoned.into_inner()).clone()
}

unsafe fn str_arg<'a>(ptr: *const u8, len: u64) -> Option<&'a str> {
    if ptr.is_null() {
        return if len == 0 { Some("") } else { None };
    }
    std::str::from_utf8(unsafe { slice::from_raw_parts(ptr, usize::try_from(len).ok()?) }).ok()
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
    let registry = unsafe { Box::from_raw(handle as *mut SoundRegistry) };
    *ACTIVE.write().unwrap_or_else(|poisoned| poisoned.into_inner()) = Arc::from(registry);
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

/// Opens the output device on the audio thread (`Library.init`) and requests
/// the registry's preloads. Returns 0, or the error text's length (copied
/// when it fits) negated.
///
/// # Safety
/// `preferred` must be valid for `preferred_len` bytes (null and 0 for the
/// default device); `error` writable for `capacity` bytes.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sound_engine_init(
    preferred: *const u8,
    preferred_len: u64,
    hrtf: i32,
    error: *mut u8,
    capacity: u64,
) -> i64 {
    let preferred = if preferred.is_null() {
        None
    } else {
        match unsafe { str_arg(preferred, preferred_len) } {
            Some(name) => Some(name.to_owned()),
            None => return -1,
        }
    };
    match ENGINE.init(preferred, hrtf != 0) {
        Ok(()) => {
            let preloads = active().preload_paths();
            ENGINE.with_policy(|policy| {
                policy.loaded = true;
                for path in &preloads {
                    policy.assets.preload(path);
                }
            });
            0
        }
        Err(message) => -(unsafe { write_text(&message, error, capacity) }).max(1),
    }
}

/// `SoundEngine.destroy`: stops everything, forgets the asset generation and
/// closes the device.
#[no_mangle]
pub extern "C" fn mattmc_sound_engine_destroy() {
    let loaded = ENGINE.with_policy(|policy| policy.loaded);
    if !loaded {
        return;
    }
    ENGINE.stop_all();
    let previous = ENGINE.with_policy(|policy| {
        policy.loaded = false;
        policy.assets.clear()
    });
    let _ = backend::destroy_asset_generation(previous);
    ENGINE.cleanup();
}

/// `SoundEngine.emergencyShutdown`: closes the device only.
#[no_mangle]
pub extern "C" fn mattmc_sound_engine_emergency_shutdown() {
    if ENGINE.with_policy(|policy| policy.loaded) {
        ENGINE.cleanup();
    }
}

/// Sets every category's final volume (`Options.getFinalSoundSourceVolume`)
/// and, when `refresh` is set, recomputes active gains.
///
/// # Safety
/// `volumes` must point to 11 floats in `SoundSource` order.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sound_engine_set_category_volumes(volumes: *const f32, refresh: i32) -> i32 {
    if volumes.is_null() {
        return ERR_INVALID_ARGUMENT;
    }
    let mut table = [0.0f32; CATEGORY_COUNT];
    table.copy_from_slice(unsafe { slice::from_raw_parts(volumes, CATEGORY_COUNT) });
    ENGINE.with_policy(|policy| {
        policy.set_category_volumes(table);
        if refresh != 0 {
            policy.refresh_volumes();
        }
    });
    OK
}

#[repr(C)]
pub struct FfiPlayRequest {
    pub instance: u64,
    pub x: f32,
    pub y: f32,
    pub z: f32,
    pub volume: f32,
    pub pitch: f32,
    pub delay: i32,
    pub attenuation_distance: i32,
    pub category: u32,
    pub flags: u32,
}

/// `SoundEngine.play` after resolution and listener notification. Returns the
/// `PlayResult` ordinal, with `0x100` set when asset requests are waiting.
///
/// # Safety
/// `request` must be readable; the strings valid for their lengths.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sound_engine_play(
    request: *const FfiPlayRequest,
    event: *const u8,
    event_len: u64,
    path: *const u8,
    path_len: u64,
) -> i32 {
    let (false, Some(event), Some(path)) =
        (request.is_null(), unsafe { str_arg(event, event_len) }, unsafe { str_arg(path, path_len) })
    else {
        return ERR_INVALID_ARGUMENT;
    };
    let request = unsafe { &*request };
    let play = PlayRequest {
        instance: request.instance,
        event: Arc::from(event),
        path: path.to_owned(),
        category: request.category.min(CATEGORY_COUNT as u32 - 1) as u8,
        volume: request.volume,
        pitch: request.pitch,
        position: [request.x, request.y, request.z],
        linear_attenuation: request.flags & PLAY_FLAG_LINEAR != 0,
        relative: request.flags & PLAY_FLAG_RELATIVE != 0,
        looping: request.flags & PLAY_FLAG_LOOPING != 0,
        delay: request.delay,
        stream: request.flags & PLAY_FLAG_STREAM != 0,
        attenuation_distance: request.attenuation_distance,
        can_start_silent: request.flags & PLAY_FLAG_CAN_START_SILENT != 0,
    };
    ENGINE.with_policy(|policy| {
        let result: PlayResult = policy.play(play);
        result as i32 | if policy.assets.has_requests() { PLAY_ASSET_REQUESTS } else { 0 }
    })
}

#[no_mangle]
pub extern "C" fn mattmc_sound_engine_play_delayed(instance: u64, delay: i32) {
    ENGINE.with_policy(|policy| policy.play_delayed(instance, delay));
}

/// Whether the engine still references `instance` (active or queued).
#[no_mangle]
pub extern "C" fn mattmc_sound_engine_knows(instance: u64) -> i32 {
    ENGINE.with_policy(|policy| policy.knows(instance)) as i32
}

#[no_mangle]
pub extern "C" fn mattmc_sound_engine_stop(instance: u64) {
    ENGINE.with_policy(|policy| policy.stop(instance));
}

/// `stop(location, source)`; a null `event` or negative `category` is absent.
/// Both absent stops everything.
///
/// # Safety
/// `event` must be null or valid for `event_len` bytes.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sound_engine_stop_matching(event: *const u8, event_len: u64, category: i32) {
    let event = if event.is_null() { None } else { unsafe { str_arg(event, event_len) } };
    let category = u8::try_from(category).ok();
    if event.is_none() && category.is_none() {
        mattmc_sound_engine_stop_all();
    } else {
        ENGINE.with_policy(|policy| policy.stop_matching(event, category));
    }
}

#[no_mangle]
pub extern "C" fn mattmc_sound_engine_stop_all() {
    if ENGINE.with_policy(|policy| policy.loaded) {
        ENGINE.stop_all();
        lock(&RELEASED).clear();
        lock(&DUE).clear();
    }
}

#[no_mangle]
pub extern "C" fn mattmc_sound_engine_set_volume(instance: u64, factor: f32, volume: f32) {
    ENGINE.with_policy(|policy| policy.set_volume(instance, factor, volume));
}

#[repr(C)]
pub struct FfiTickingUpdate {
    pub instance: u64,
    pub volume: f32,
    pub pitch: f32,
    pub x: f32,
    pub y: f32,
    pub z: f32,
    pub reserved: u32,
}

/// The ticking instances' per-tick volume, pitch and position.
///
/// # Safety
/// `updates` must point to `count` records.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sound_engine_update_ticking(updates: *const FfiTickingUpdate, count: u64) -> i32 {
    if count == 0 {
        return OK;
    }
    if updates.is_null() {
        return ERR_INVALID_ARGUMENT;
    }
    let updates = unsafe { slice::from_raw_parts(updates, count as usize) };
    ENGINE.with_policy(|policy| {
        for update in updates {
            policy.update_ticking(update.instance, update.volume, update.pitch, [update.x, update.y, update.z]);
        }
    });
    OK
}

/// Pauses every instance whose category bit is not set in `kept_categories`.
#[no_mangle]
pub extern "C" fn mattmc_sound_engine_pause_all_except(kept_categories: u32) {
    ENGINE.with_policy(|policy| policy.pause_all_except(kept_categories));
}

#[no_mangle]
pub extern "C" fn mattmc_sound_engine_resume() {
    ENGINE.with_policy(|policy| policy.resume());
}

/// # Safety
/// `transform` must point to 9 floats: position, forward, up.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sound_engine_listener(transform: *const f32) -> i32 {
    if transform.is_null() {
        return ERR_INVALID_ARGUMENT;
    }
    let values = unsafe { slice::from_raw_parts(transform, 9) };
    let transform = ListenerTransform {
        position: [values[0], values[1], values[2]],
        forward: [values[3], values[4], values[5]],
        up: [values[6], values[7], values[8]],
    };
    ENGINE.with_policy(|policy| policy.listener(transform));
    OK
}

/// Start of a client tick; returns 1 when the device changed and the engine
/// must reload.
#[no_mangle]
pub extern "C" fn mattmc_sound_engine_tick_begin(paused: i32) -> i32 {
    ENGINE.with_policy(|policy| policy.begin_tick(paused != 0));
    ENGINE.take_reload_needed() as i32
}

/// End of a client tick: cleanup and due instances (taken separately).
/// Returns the number of released plus due results.
#[no_mangle]
pub extern "C" fn mattmc_sound_engine_tick_end(paused: i32) -> i64 {
    let (released, due) = ENGINE.with_policy(|policy| policy.end_tick(paused != 0));
    let total = released.len() + due.len();
    lock(&RELEASED).extend(released);
    lock(&DUE).extend(due);
    total as i64
}

/// Queues the device tick (streaming refill, stopped-voice scan, device check)
/// after Java played the due instances.
#[no_mangle]
pub extern "C" fn mattmc_sound_engine_device_tick() {
    ENGINE.with_policy(|policy| policy.schedule_tick());
}

/// Takes released instances (bit 63: requeued); returns how many were written.
///
/// # Safety
/// `out` must be writable for `capacity` values.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sound_engine_take_released(out: *mut u64, capacity: u64) -> u64 {
    let mut released = lock(&RELEASED);
    let count = released.len().min(capacity as usize);
    for (index, entry) in released.drain(..count).enumerate() {
        let value = entry.instance | if entry.requeued { RELEASED_REQUEUED } else { 0 };
        unsafe { *out.add(index) = value };
    }
    count as u64
}

/// Takes due delayed instances; returns how many were written.
///
/// # Safety
/// `out` must be writable for `capacity` values.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sound_engine_take_due(out: *mut u64, capacity: u64) -> u64 {
    let mut due = lock(&DUE);
    let count = due.len().min(capacity as usize);
    for (index, instance) in due.drain(..count).enumerate() {
        unsafe { *out.add(index) = instance };
    }
    count as u64
}

/// Takes waiting asset load requests (`\n`-separated paths) and writes their
/// generation. Returns the needed length; requests stay queued until a call
/// whose buffer fits them.
///
/// # Safety
/// `out` must be writable for `capacity` bytes and `generation` writable.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sound_engine_take_asset_requests(out: *mut u8, capacity: u64, generation: *mut u64) -> i64 {
    if generation.is_null() {
        return -1;
    }
    ENGINE.with_policy(|policy| {
        if !policy.assets.has_requests() {
            return 0;
        }
        unsafe { *generation = policy.assets.generation() };
        let requests = policy.assets.take_requests();
        let text = requests.join("\n");
        if out.is_null() || (text.len() as u64) > capacity {
            // Too small: put them back for a larger buffer.
            for path in &requests {
                policy.assets.requeue(path);
            }
        } else {
            unsafe { ptr::copy_nonoverlapping(text.as_ptr(), out, text.len()) };
        }
        text.len() as i64
    })
}

/// Publishes a loaded asset's encoded bytes for (`path`, `generation`).
/// Returns 1 when used, 0 when stale, negative on error.
///
/// # Safety
/// The path must be valid for `path_len` bytes and `bytes` for `len` bytes.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sound_engine_provide_asset(
    path: *const u8,
    path_len: u64,
    generation: u64,
    bytes: *const u8,
    len: u64,
) -> i32 {
    let (Some(path), false) = (unsafe { str_arg(path, path_len) }, bytes.is_null()) else {
        return ERR_INVALID_ARGUMENT;
    };
    if !ENGINE.with_policy(|policy| policy.assets.awaits(path, generation)) {
        return 0;
    }
    let encoded = unsafe { slice::from_raw_parts(bytes, len as usize) };
    let Ok(handle) = backend::create_asset(encoded, Some(path.to_owned()), generation) else {
        ENGINE.with_policy(|policy| {
            let waiting = policy.assets.fail(path, generation);
            policy.asset_failed(waiting);
        });
        return -1;
    };
    let used = ENGINE.with_policy(|policy| match policy.assets.provide(path, generation, handle) {
        Some(waiting) => {
            policy.asset_ready(waiting, handle);
            true
        }
        None => false,
    });
    if !used {
        let _ = backend::destroy_asset(handle);
    }
    used as i32
}

/// Records a failed read for (`path`, `generation`); waiting voices fail.
///
/// # Safety
/// The path must be valid for `path_len` bytes.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sound_engine_fail_asset(path: *const u8, path_len: u64, generation: u64) {
    if let Some(path) = unsafe { str_arg(path, path_len) } {
        ENGINE.with_policy(|policy| {
            let waiting = policy.assets.fail(path, generation);
            policy.asset_failed(waiting);
        });
    }
}

/// Static used/limit and streaming used/limit of the current device.
///
/// # Safety
/// `out` must be writable for 4 values.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sound_engine_pool_counts(out: *mut i32) -> i32 {
    if out.is_null() {
        return ERR_INVALID_ARGUMENT;
    }
    let counts = ENGINE.pool_counts();
    unsafe { ptr::copy_nonoverlapping(counts.as_ptr(), out, 4) };
    OK
}

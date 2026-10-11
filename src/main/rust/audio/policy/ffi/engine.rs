//! Device lifecycle, Java instance playback, ticks, assets and log lines.

use std::ptr;
use std::slice;
use std::sync::{Arc, Mutex, MutexGuard};

use super::super::super::backend;
use super::super::super::errors::{ERR_INVALID_ARGUMENT, OK};
use super::super::super::listener::ListenerTransform;
use super::super::client::{JavaRule, TickUpdate};
use super::super::engine::{PlayRequest, PlayResult, Released, CATEGORY_COUNT};
use super::super::rules::{RuleKind, Sample};
use super::super::thread::ENGINE;
use super::{str_arg, write_text, ASSET_REQUESTS};

/// Tick results not yet taken by Java.
static RELEASED: Mutex<Vec<Released>> = Mutex::new(Vec::new());
static DUE: Mutex<Vec<u64>> = Mutex::new(Vec::new());

/// Bit 63 of a released instance ID: requeued for a manual loop.
const RELEASED_REQUEUED: u64 = 1 << 63;
/// Rule outcome bits on an instance ID.
const OUTCOME_STOPPED: u64 = 1 << 63;
const OUTCOME_SWITCHED: u64 = 1 << 62;

const PLAY_FLAG_LINEAR: u32 = 1 << 0;
const PLAY_FLAG_RELATIVE: u32 = 1 << 1;
const PLAY_FLAG_LOOPING: u32 = 1 << 2;
const PLAY_FLAG_STREAM: u32 = 1 << 3;
const PLAY_FLAG_CAN_START_SILENT: u32 = 1 << 4;
/// A ticking update carrying a plain tickable's volume and pitch.
const TICK_PLAIN: u32 = 1 << 31;
/// `tick_begin` flags: the device was reopened (Java forgets its instances);
/// reopening failed and the engine stopped.
const TICK_REOPENED: i32 = 1 << 0;
const TICK_STOPPED: i32 = 1 << 1;

fn lock<T>(mutex: &Mutex<T>) -> MutexGuard<'_, T> {
    mutex.lock().unwrap_or_else(|poisoned| poisoned.into_inner())
}

fn asset_flag(client: &super::super::client::Client) -> i32 {
    if client.policy.assets.has_requests() {
        ASSET_REQUESTS
    } else {
        0
    }
}

fn clear_tick_results() {
    lock(&RELEASED).clear();
    lock(&DUE).clear();
}

/// Opens the output device on the audio thread (`SoundEngine.loadLibrary`)
/// and requests the registry's preloads. Returns 0, or the error text's
/// length (copied when it fits) negated.
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
    match ENGINE.open(preferred, hrtf != 0) {
        Ok(()) => 0,
        Err(message) => -(unsafe { write_text(&message, error, capacity) }).max(1),
    }
}

/// `SoundEngine.destroy`.
#[no_mangle]
pub extern "C" fn mattmc_sound_engine_destroy() {
    ENGINE.close();
    clear_tick_results();
}

/// `SoundEngine.emergencyShutdown`: closes the device only.
#[no_mangle]
pub extern "C" fn mattmc_sound_engine_emergency_shutdown() {
    if ENGINE.with_client(|client| client.policy.loaded) {
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
    ENGINE.with_client(|client| {
        client.policy.set_category_volumes(table);
        if refresh != 0 {
            client.policy.refresh_volumes();
        }
    });
    OK
}

/// One Java `SoundEngine.play` after resolution.
#[repr(C)]
pub struct FfiPlayRequest {
    pub instance: u64,
    pub x: f64,
    pub y: f64,
    pub z: f64,
    /// `getVolume()` / `getPitch()`.
    pub volume: f32,
    pub pitch: f32,
    pub delay: i32,
    pub attenuation_distance: i32,
    pub category: u32,
    pub flags: u32,
    /// The instance's rule kind (0: none) and its parameter.
    pub rule: i32,
    pub rule_parameter: i32,
    /// For a ruled instance: its own volume and pitch fields and the
    /// resolved sound's factors.
    pub base_volume: f32,
    pub base_pitch: f32,
    pub sound_volume: f32,
    pub sound_pitch: f32,
}

/// `SoundEngine.play` for a Java instance after resolution. Returns the
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
    let position = [request.x, request.y, request.z];
    let play = PlayRequest {
        instance: request.instance,
        event: Arc::from(event),
        path: path.to_owned(),
        category: request.category.min(CATEGORY_COUNT as u32 - 1) as u8,
        volume: request.volume,
        pitch: request.pitch,
        position: position.map(|axis| axis as f32),
        linear_attenuation: request.flags & PLAY_FLAG_LINEAR != 0,
        relative: request.flags & PLAY_FLAG_RELATIVE != 0,
        looping: request.flags & PLAY_FLAG_LOOPING != 0,
        delay: request.delay,
        stream: request.flags & PLAY_FLAG_STREAM != 0,
        attenuation_distance: request.attenuation_distance,
        can_start_silent: request.flags & PLAY_FLAG_CAN_START_SILENT != 0,
    };
    let rule = RuleKind::from_abi(request.rule).map(|kind| JavaRule {
        kind,
        parameter: request.rule_parameter,
        base_volume: request.base_volume,
        base_pitch: request.base_pitch,
        sound_volume: request.sound_volume,
        sound_pitch: request.sound_pitch,
    });
    ENGINE.with_client(|client| {
        let result: PlayResult = client.play_java(play, position, rule);
        result as i32 | asset_flag(client)
    })
}

#[no_mangle]
pub extern "C" fn mattmc_sound_engine_play_delayed(instance: u64, delay: i32) {
    ENGINE.with_client(|client| client.policy.play_delayed(instance, delay));
}

/// Whether the engine still references `instance` (active or queued).
#[no_mangle]
pub extern "C" fn mattmc_sound_engine_knows(instance: u64) -> i32 {
    ENGINE.with_client(|client| client.policy.knows(instance)) as i32
}

#[no_mangle]
pub extern "C" fn mattmc_sound_engine_stop(instance: u64) {
    ENGINE.with_client(|client| client.policy.stop(instance));
}

/// `stop(location, source)`; a null `event` or negative `category` is absent.
/// Both absent stops everything.
///
/// # Safety
/// `event` must be null or valid for `event_len` bytes.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sound_engine_stop_matching(event: *const u8, event_len: u64, category: i32) {
    let event = unsafe { super::optional_str(event, event_len) };
    let category = u8::try_from(category).ok();
    if event.is_none() && category.is_none() {
        mattmc_sound_engine_stop_all();
    } else {
        ENGINE.with_client(|client| client.policy.stop_matching(event, category));
    }
}

#[no_mangle]
pub extern "C" fn mattmc_sound_engine_stop_all() {
    if ENGINE.with_client(|client| client.policy.loaded) {
        ENGINE.stop_all();
        clear_tick_results();
    }
}

#[no_mangle]
pub extern "C" fn mattmc_sound_engine_set_volume(instance: u64, factor: f32, volume: f32) {
    ENGINE.with_client(|client| client.policy.set_volume(instance, factor, volume));
}

/// One ticking instance's update: a plain tickable's volume and pitch
/// ([`TICK_PLAIN`]), or a ruled instance's sample.
#[repr(C)]
pub struct FfiTickingUpdate {
    pub instance: u64,
    pub flags: u32,
    /// Plain: volume. Sampled: the rule's measured value.
    pub value: f32,
    /// Plain: pitch.
    pub pitch: f32,
    pub reserved: u32,
    pub x: f64,
    pub y: f64,
    pub z: f64,
}

/// The ticking instances' updates. Ruled instances that stopped or switched
/// are written to `outcomes` (ID with bit 63 stopped, bit 62 switched);
/// returns how many.
///
/// # Safety
/// `updates` must point to `count` records and `outcomes` be writable for
/// `capacity` values (at least `count`).
#[no_mangle]
pub unsafe extern "C" fn mattmc_sound_engine_update_ticking(
    updates: *const FfiTickingUpdate,
    count: u64,
    outcomes: *mut u64,
    capacity: u64,
) -> i64 {
    if count == 0 {
        return 0;
    }
    if updates.is_null() || outcomes.is_null() || capacity < count {
        return ERR_INVALID_ARGUMENT as i64;
    }
    let updates: Vec<TickUpdate> = unsafe { slice::from_raw_parts(updates, count as usize) }
        .iter()
        .map(|update| {
            let position = [update.x, update.y, update.z];
            if update.flags & TICK_PLAIN != 0 {
                TickUpdate::Plain { instance: update.instance, volume: update.value, pitch: update.pitch, position }
            } else {
                TickUpdate::Sampled {
                    instance: update.instance,
                    sample: Sample { flags: update.flags, position, value: update.value },
                }
            }
        })
        .collect();
    let results = ENGINE.with_client(|client| client.update_ticking(&updates));
    for (index, outcome) in results.iter().enumerate() {
        let value = outcome.instance
            | if outcome.stopped { OUTCOME_STOPPED } else { 0 }
            | if outcome.switched { OUTCOME_SWITCHED } else { 0 };
        unsafe { *outcomes.add(index) = value };
    }
    results.len() as i64
}

/// Pauses every instance whose category bit is not set in `kept_categories`.
#[no_mangle]
pub extern "C" fn mattmc_sound_engine_pause_all_except(kept_categories: u32) {
    ENGINE.with_client(|client| client.policy.pause_all_except(kept_categories));
}

#[no_mangle]
pub extern "C" fn mattmc_sound_engine_resume() {
    ENGINE.with_client(|client| client.policy.resume());
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
    ENGINE.with_client(|client| client.policy.listener(transform));
    OK
}

/// Start of a client tick. A changed or disconnected output device is
/// reopened here (`TICK_REOPENED`, plus `TICK_STOPPED` if that failed);
/// `0x100` means asset requests are waiting.
#[no_mangle]
pub extern "C" fn mattmc_sound_engine_tick_begin(paused: i32) -> i32 {
    let mut flags = 0;
    if ENGINE.take_reload_needed() && ENGINE.with_client(|client| client.policy.loaded) {
        flags |= TICK_REOPENED;
        let reopened = ENGINE.reopen();
        clear_tick_results();
        ENGINE.with_client(|client| match reopened {
            Ok(()) => client.log("I Sound engine reopened after an output device change".into()),
            Err(message) => {
                flags |= TICK_STOPPED;
                client.log(format!("E Error starting SoundSystem. Turning off sounds & music: {message}"));
            }
        });
    }
    ENGINE.with_client(|client| {
        client.begin_tick(paused != 0);
        flags | asset_flag(client)
    })
}

/// End of a client tick: Rust-owned tickables, cleanup and due instances
/// (the released and due Java instances are taken separately). Returns
/// `0x100` when asset requests are waiting.
#[no_mangle]
pub extern "C" fn mattmc_sound_engine_tick_end(paused: i32) -> i32 {
    let (released, due, flags) = ENGINE.with_client(|client| {
        let (released, due) = client.end_tick(paused != 0);
        (released, due, asset_flag(client))
    });
    lock(&RELEASED).extend(released);
    lock(&DUE).extend(due);
    flags
}

/// Queues the device tick (streaming refill, stopped-voice scan, device check)
/// after Java played the due instances.
#[no_mangle]
pub extern "C" fn mattmc_sound_engine_device_tick() {
    ENGINE.with_client(|client| client.policy.schedule_tick());
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
    ENGINE.with_client(|client| {
        let assets = &mut client.policy.assets;
        if !assets.has_requests() {
            return 0;
        }
        unsafe { *generation = assets.generation() };
        let requests = assets.take_requests();
        let text = requests.join("\n");
        if out.is_null() || (text.len() as u64) > capacity {
            // Too small: put them back for a larger buffer.
            for path in &requests {
                assets.requeue(path);
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
    if !ENGINE.with_client(|client| client.policy.assets.awaits(path, generation)) {
        return 0;
    }
    let encoded = unsafe { slice::from_raw_parts(bytes, len as usize) };
    let Ok(handle) = backend::create_asset(encoded, Some(path.to_owned()), generation) else {
        ENGINE.with_client(|client| {
            let waiting = client.policy.assets.fail(path, generation);
            client.policy.asset_failed(waiting);
        });
        return -1;
    };
    let used = ENGINE.with_client(|client| match client.policy.assets.provide(path, generation, handle) {
        Some(waiting) => {
            client.policy.asset_ready(waiting, handle);
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
        ENGINE.with_client(|client| {
            let waiting = client.policy.assets.fail(path, generation);
            client.policy.asset_failed(waiting);
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

/// Takes queued log lines (`\n`-separated, each prefixed `I `, `W ` or
/// `E `). Returns the needed length; lines stay queued until they fit.
///
/// # Safety
/// `out` must be writable for `capacity` bytes.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sound_take_logs(out: *mut u8, capacity: u64) -> i64 {
    ENGINE.with_client(|client| {
        let lines = client.take_logs();
        if lines.is_empty() {
            return 0;
        }
        let text = lines.join("\n");
        if out.is_null() || text.len() as u64 > capacity {
            client.requeue_logs(lines);
        } else {
            unsafe { ptr::copy_nonoverlapping(text.as_ptr(), out, text.len()) };
        }
        text.len() as i64
    })
}

/// `ONLY_WARN_ONCE.add(event)`: 1 the first time.
///
/// # Safety
/// The name must be valid for `name_len` bytes.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sound_warn_once(name: *const u8, name_len: u64) -> i32 {
    let Some(name) = (unsafe { str_arg(name, name_len) }) else { return 0 };
    ENGINE.with_client(|client| client.warn_once(name)) as i32
}

/// `SoundEngine.reload`: resets the warnings to the `\n`-separated events
/// found missing.
///
/// # Safety
/// `names` must be valid for `len` bytes.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sound_reset_warnings(names: *const u8, len: u64) {
    let names = unsafe { str_arg(names, len) }.unwrap_or("");
    ENGINE.with_client(|client| client.reset_warnings(names.split('\n').filter(|name| !name.is_empty())));
}

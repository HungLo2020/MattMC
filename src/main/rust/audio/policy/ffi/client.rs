//! Rust-owned sounds: world sound events, music, the local player's ambience,
//! the volume preview, and the subtitle overlay's frames.

use std::ptr;
use std::sync::{Arc, Mutex, MutexGuard};

use crate::world::level::levelgen::random::Legacy;

use super::super::ambient::{
    AdditionsSettings, AmbientInput, AmbientOutput, BiomeChange, BubbleColumn, MoodSettings,
};
use super::super::client::{Client, NativeSound};
use super::super::engine::{PlayResult, CATEGORY_COUNT};
use super::super::music::{Frequency, Situation, Track};
use super::super::subtitles::Visible;
use super::super::thread::ENGINE;
use super::{optional_str, str_arg, write_text, ASSET_REQUESTS};

/// The last subtitle frame, copied out by `mattmc_sound_subtitles_take`.
static FRAME: Mutex<Vec<Visible>> = Mutex::new(Vec::new());

const EVENT_FLAG_LINEAR: u32 = 1 << 0;
const EVENT_FLAG_RELATIVE: u32 = 1 << 1;

const AMBIENT_UNDERWATER: u32 = 1 << 0;
const AMBIENT_SPECTATOR: u32 = 1 << 1;
const AMBIENT_BUBBLE_UP: u32 = 1 << 2;
const AMBIENT_BUBBLE_DOWN: u32 = 1 << 3;
const AMBIENT_BIOME_CHANGED: u32 = 1 << 4;
const AMBIENT_HAS_LOOP: u32 = 1 << 5;
const AMBIENT_HAS_MOOD: u32 = 1 << 6;
const AMBIENT_HAS_ADDITIONS: u32 = 1 << 7;

fn lock<T>(mutex: &Mutex<T>) -> MutexGuard<'_, T> {
    mutex.lock().unwrap_or_else(|poisoned| poisoned.into_inner())
}

/// Runs `f` and adds the waiting-asset bit to its actions.
fn with_actions(f: impl FnOnce(&mut Client) -> i32) -> i32 {
    ENGINE.with_client(|client| {
        let actions = f(client);
        actions | if client.policy.assets.has_requests() { ASSET_REQUESTS } else { 0 }
    })
}

/// `ClientLevel.playSound`'s `SimpleSoundInstance(event, source, volume,
/// pitch, RandomSource.create(seed), x, y, z)`.
#[repr(C)]
pub struct FfiEventRequest {
    pub seed: i64,
    pub x: f64,
    pub y: f64,
    pub z: f64,
    pub volume: f32,
    pub pitch: f32,
    /// Ticks to wait (`playDelayed`), or 0.
    pub delay: i32,
    pub category: u32,
    pub flags: u32,
    pub reserved: u32,
}

/// Plays (or queues) a seeded world sound event. Returns the `PlayResult`
/// ordinal (`NOT_STARTED` when queued), with `0x100` for asset requests.
///
/// # Safety
/// `request` must be readable and the event valid for `event_len` bytes.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sound_play_event(event: *const u8, event_len: u64, request: *const FfiEventRequest) -> i32 {
    let (Some(event), false) = (unsafe { str_arg(event, event_len) }, request.is_null()) else {
        return -1;
    };
    let request = unsafe { &*request };
    let sound = NativeSound {
        linear: request.flags & EVENT_FLAG_LINEAR != 0,
        relative: request.flags & EVENT_FLAG_RELATIVE != 0,
        ..NativeSound::at(
            Arc::from(event),
            request.category.min(CATEGORY_COUNT as u32 - 1) as u8,
            request.volume,
            request.pitch,
            [request.x, request.y, request.z],
        )
    };
    let random = Legacy::new(request.seed);
    with_actions(|client| {
        if request.delay > 0 {
            client.play_native_delayed(sound, random, request.delay);
            PlayResult::NotStarted as i32
        } else {
            let mut random = random;
            client.play_native(sound, &mut random, false).0 as i32
        }
    })
}

unsafe fn track(event: *const u8, event_len: u64, min_delay: i32, max_delay: i32, replace: i32) -> Option<Track> {
    unsafe { optional_str(event, event_len) }.map(|event| Track {
        event: Arc::from(event),
        min_delay,
        max_delay,
        replace_current: replace != 0,
    })
}

/// `new MusicManager`: a fresh music state with the frequency option ordinal.
#[no_mangle]
pub extern "C" fn mattmc_sound_music_reset(frequency: i32) {
    ENGINE.with_client(|client| client.music_reset(Frequency::from_ordinal(frequency)));
}

/// `MusicManager.tick` with the situational music (null event: none).
/// Returns toast actions (1 show, 2 hide) and `0x100` for asset requests.
///
/// # Safety
/// `event` must be null or valid for `event_len` bytes.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sound_music_tick(
    event: *const u8,
    event_len: u64,
    min_delay: i32,
    max_delay: i32,
    replace: i32,
    volume: f32,
) -> i32 {
    let situation = Situation { track: unsafe { track(event, event_len, min_delay, max_delay, replace) }, volume };
    with_actions(|client| client.music_tick(&situation))
}

/// `MusicManager.startPlaying`.
///
/// # Safety
/// `event` must be valid for `event_len` bytes.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sound_music_start(event: *const u8, event_len: u64, volume: f32) -> i32 {
    let Some(event) = (unsafe { str_arg(event, event_len) }) else { return 0 };
    let event: Arc<str> = Arc::from(event);
    with_actions(|client| client.music_start(&event, volume))
}

/// `MusicManager.stopPlaying()` (null event) or `stopPlaying(music)`.
///
/// # Safety
/// `event` must be null or valid for `event_len` bytes.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sound_music_stop(event: *const u8, event_len: u64) -> i32 {
    let event = unsafe { optional_str(event, event_len) };
    ENGINE.with_client(|client| match event {
        Some(event) => client.music_stop_track(event),
        None => client.music_stop(),
    })
}

#[no_mangle]
pub extern "C" fn mattmc_sound_music_toast_if_needed() -> i32 {
    ENGINE.with_client(|client| client.music_toast_if_needed())
}

/// # Safety
/// `event` must be valid for `event_len` bytes.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sound_music_is_playing(event: *const u8, event_len: u64) -> i32 {
    let Some(event) = (unsafe { str_arg(event, event_len) }) else { return 0 };
    ENGINE.with_client(|client| client.music_is_playing(event)) as i32
}

/// The current track's resolved sound location: its length (copied when it
/// fits), or -1 when there is none.
///
/// # Safety
/// `out` must be writable for `capacity` bytes.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sound_music_current_sound(out: *mut u8, capacity: u64) -> i64 {
    match ENGINE.with_client(|client| client.music_current_sound()) {
        Some(location) => unsafe { write_text(&location, out, capacity) },
        None => -1,
    }
}

/// `MusicManager.setMinutesBetweenSongs` with the situational track.
///
/// # Safety
/// `event` must be null or valid for `event_len` bytes.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sound_music_set_frequency(
    frequency: i32,
    event: *const u8,
    event_len: u64,
    min_delay: i32,
    max_delay: i32,
) {
    let track = unsafe { track(event, event_len, min_delay, max_delay, 0) };
    ENGINE.with_client(|client| client.music_set_frequency(Frequency::from_ordinal(frequency), track.as_ref()));
}

/// The local player's facts for the ambient handlers.
#[repr(C)]
pub struct FfiAmbientInput {
    pub x: f64,
    pub y: f64,
    pub eye_y: f64,
    pub z: f64,
    pub mood_offset: f64,
    pub additions_chance: f64,
    pub flags: u32,
    /// Java's identity key for the current biome.
    pub biome: i32,
    pub sky_light: i32,
    pub block_light: i32,
    pub mood_tick_delay: i32,
    pub mood_extent: i32,
}

#[repr(C)]
pub struct FfiAmbientOutput {
    /// The level random's raw state to write back.
    pub random: i64,
    pub sample_x: i32,
    pub sample_y: i32,
    pub sample_z: i32,
    /// 1 when `mattmc_sound_ambient_finish` must follow with that block's light.
    pub has_sample: i32,
    pub moodiness: f32,
    pub constant_moodiness: f32,
}

fn write_output(out: &mut FfiAmbientOutput, output: AmbientOutput) {
    out.random = output.random;
    let sample = output.mood_sample.unwrap_or([0; 3]);
    out.sample_x = sample[0];
    out.sample_y = sample[1];
    out.sample_z = sample[2];
    out.has_sample = output.mood_sample.is_some() as i32;
    out.moodiness = output.moodiness;
    out.constant_moodiness = output.constant_moodiness;
}

/// A `LocalPlayer`'s ambient handlers' tick (`owner` identifies the player),
/// drawing from the level random's raw state. The biome strings are read
/// only when the biome changed.
///
/// # Safety
/// `input` readable, `out` writable, strings valid for their lengths.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sound_ambient_tick(
    owner: u64,
    input: *const FfiAmbientInput,
    ambient_loop: *const u8,
    ambient_loop_len: u64,
    mood: *const u8,
    mood_len: u64,
    additions: *const u8,
    additions_len: u64,
    level_random: i64,
    out: *mut FfiAmbientOutput,
) -> i32 {
    if input.is_null() || out.is_null() {
        return -1;
    }
    let raw = unsafe { &*input };
    let has = |flag: u32| raw.flags & flag != 0;
    let text = |ptr: *const u8, len: u64, present: bool| -> Option<Arc<str>> {
        if present {
            unsafe { str_arg(ptr, len) }.map(Arc::from)
        } else {
            None
        }
    };
    let biome_change = has(AMBIENT_BIOME_CHANGED).then(|| BiomeChange {
        biome: raw.biome,
        ambient_loop: text(ambient_loop, ambient_loop_len, has(AMBIENT_HAS_LOOP)),
        mood: text(mood, mood_len, has(AMBIENT_HAS_MOOD)).map(|event| MoodSettings {
            event,
            tick_delay: raw.mood_tick_delay,
            block_search_extent: raw.mood_extent,
            sound_position_offset: raw.mood_offset,
        }),
        additions: text(additions, additions_len, has(AMBIENT_HAS_ADDITIONS))
            .map(|event| AdditionsSettings { event, tick_chance: raw.additions_chance }),
    });
    let input = AmbientInput {
        x: raw.x,
        y: raw.y,
        eye_y: raw.eye_y,
        z: raw.z,
        underwater: has(AMBIENT_UNDERWATER),
        spectator: has(AMBIENT_SPECTATOR),
        bubble_column: if has(AMBIENT_BUBBLE_DOWN) {
            BubbleColumn::Downwards
        } else if has(AMBIENT_BUBBLE_UP) {
            BubbleColumn::Upwards
        } else {
            BubbleColumn::None
        },
        biome_change,
        sky_light: raw.sky_light,
        block_light: raw.block_light,
    };
    let out = unsafe { &mut *out };
    with_actions(|client| {
        write_output(out, client.ambient_tick(owner, &input, level_random));
        0
    })
}

/// The mood handler's rest, with the sampled block's sky and block light.
///
/// # Safety
/// `out` must be writable.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sound_ambient_finish(sky_light: i32, block_light: i32, out: *mut FfiAmbientOutput) -> i32 {
    if out.is_null() {
        return -1;
    }
    let out = unsafe { &mut *out };
    with_actions(|client| {
        write_output(out, client.ambient_finish(sky_light, block_light));
        0
    })
}

/// The local player `owner` entered water: the underwater ambience loop.
#[no_mangle]
pub extern "C" fn mattmc_sound_play_underwater_loop(owner: u64) -> i32 {
    with_actions(|client| {
        client.play_underwater_loop(owner);
        0
    })
}

/// `SoundPreviewHandler.preview` for a `SoundSource` ordinal.
#[no_mangle]
pub extern "C" fn mattmc_sound_preview(category: i32, volume: f32) -> i32 {
    let Ok(category) = u8::try_from(category) else { return 0 };
    with_actions(|client| {
        client.preview(category, volume);
        0
    })
}

/// Registers or removes the subtitle overlay (`showSubtitles`), and whether
/// subtitles show event paths (`DEBUG_SUBTITLES`).
#[no_mangle]
pub extern "C" fn mattmc_sound_subtitles_listen(listening: i32, debug: i32) {
    ENGINE.with_client(|client| {
        client.subtitles.listening = listening != 0;
        client.subtitles.debug = debug != 0;
    });
}

/// Computes this frame's audible subtitles for the listener position;
/// returns how many. Copy them out with `mattmc_sound_subtitles_take`.
#[no_mangle]
pub extern "C" fn mattmc_sound_subtitles_frame(x: f64, y: f64, z: f64, display_ms: f64) -> i64 {
    let frame = ENGINE.with_client(|client| {
        let now = client.now_ms();
        client.subtitles.frame([x, y, z], display_ms, now)
    });
    let count = frame.len();
    *lock(&FRAME) = frame;
    count as i64
}

#[repr(C)]
pub struct FfiSubtitle {
    pub x: f64,
    pub y: f64,
    pub z: f64,
    pub age_ms: i64,
    pub text_len: u32,
    pub reserved: u32,
}

/// Copies the last frame: `records` get one entry each, `text` the texts
/// back to back. Returns the text bytes needed; copies only when both fit
/// (records need the frame's count).
///
/// # Safety
/// `records` writable for `record_capacity` entries, `text` for `text_capacity` bytes.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sound_subtitles_take(
    records: *mut FfiSubtitle,
    record_capacity: u64,
    text: *mut u8,
    text_capacity: u64,
) -> i64 {
    let frame = lock(&FRAME);
    let needed: usize = frame.iter().map(|visible| visible.text.len()).sum();
    if records.is_null() || text.is_null() || (record_capacity as usize) < frame.len() || (text_capacity as usize) < needed {
        return needed as i64;
    }
    let mut offset = 0;
    for (index, visible) in frame.iter().enumerate() {
        unsafe {
            *records.add(index) = FfiSubtitle {
                x: visible.position[0],
                y: visible.position[1],
                z: visible.position[2],
                age_ms: visible.age_ms,
                text_len: visible.text.len() as u32,
                reserved: 0,
            };
            ptr::copy_nonoverlapping(visible.text.as_ptr(), text.add(offset), visible.text.len());
        }
        offset += visible.text.len();
    }
    needed as i64
}

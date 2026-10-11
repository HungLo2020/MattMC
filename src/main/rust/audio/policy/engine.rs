//! Playback policy (`SoundEngine` without the Java instance objects).
//!
//! Tracks active instances by Java instance ID, category volumes, the
//! minimum source lifetime, delayed and manually looping playback,
//! pause-except and released-source cleanup. It is device-free: every audio
//! effect becomes a [`VoiceOp`] the device-owner thread executes in order, so
//! the policy is deterministic and testable without OpenAL.
//!
//! A *voice* is one started playback (Java's `ChannelHandle`). Replaying an
//! instance starts a new voice; an older voice may still play until it stops,
//! as with Java's replaced channel map entry.

use std::collections::HashMap;
use std::sync::Arc;

use super::super::commands::{
    SoundConfigRecord, SOUND_FLAG_DISABLE_ATTENUATION, SOUND_FLAG_LINEAR_ATTENUATION,
    SOUND_FLAG_LOOPING, SOUND_FLAG_RELATIVE, SOUND_UPDATE_GAIN, SOUND_UPDATE_PITCH,
    SOUND_UPDATE_POSITION,
};
use super::super::listener::ListenerTransform;
use super::assets::{AssetCache, AssetLookup};

/// `SoundSource` ordinals.
pub(crate) const CATEGORY_COUNT: usize = 11;
pub(crate) const CATEGORY_MUSIC: u8 = 1;
/// `MIN_SOURCE_LIFETIME` in ticks.
const MIN_SOURCE_LIFETIME: i32 = 20;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum PlayResult {
    Started = 0,
    StartedSilently = 1,
    NotStarted = 2,
}

/// One `SoundEngine.play` request after Java resolved the event and notified
/// its listeners.
#[derive(Clone, Debug)]
pub(crate) struct PlayRequest {
    pub(crate) instance: u64,
    /// The instance's event location (`SoundInstance.getLocation()`).
    pub(crate) event: Arc<str>,
    /// The resolved sound's file path (`Sound.getPath()`).
    pub(crate) path: String,
    pub(crate) category: u8,
    /// `SoundInstance.getVolume()` / `getPitch()` (already sampled).
    pub(crate) volume: f32,
    pub(crate) pitch: f32,
    pub(crate) position: [f32; 3],
    pub(crate) linear_attenuation: bool,
    pub(crate) relative: bool,
    pub(crate) looping: bool,
    pub(crate) delay: i32,
    pub(crate) stream: bool,
    pub(crate) attenuation_distance: i32,
    pub(crate) can_start_silent: bool,
}

/// Device work, executed in order by the audio thread.
#[derive(Clone, Debug, PartialEq)]
pub(crate) enum VoiceOp {
    /// Prepares a voice; `asset` attaches and plays it at once when ready.
    Start { voice: u64, config: SoundConfigRecord, stream: bool, stream_looping: bool, asset: Option<u64> },
    Attach { voice: u64, asset: u64 },
    FailAttachment { voice: u64 },
    Update { voice: u64, mask: u32, config: SoundConfigRecord },
    Stop { voice: u64 },
    Pause { voice: u64 },
    ResumeAll,
    Listener(ListenerTransform),
    Tick,
}

#[derive(Debug)]
struct Instance {
    voice: u64,
    event: Arc<str>,
    category: u8,
    /// Last `getVolume()` (play or tick update), for category volume changes.
    volume: f32,
    delay: i32,
    manual_loop: bool,
    delete_tick: i32,
    released: bool,
}

/// One instance whose voice was released and forgotten this tick.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct Released {
    pub(crate) instance: u64,
    /// Requeued for a manual loop, so Java keeps the instance object.
    pub(crate) requeued: bool,
}

#[derive(Debug)]
pub(crate) struct Policy {
    pub(crate) loaded: bool,
    tick: i32,
    next_voice: u64,
    category_volumes: [f32; CATEGORY_COUNT],
    active: HashMap<u64, Instance>,
    /// Voice → instance for voices still owned by the policy.
    voices: HashMap<u64, u64>,
    queued: HashMap<u64, i32>,
    pub(crate) assets: AssetCache,
    ops: Vec<VoiceOp>,
}

impl Default for Policy {
    fn default() -> Self {
        Self {
            loaded: false,
            tick: 0,
            next_voice: 1,
            category_volumes: [1.0; CATEGORY_COUNT],
            active: HashMap::new(),
            voices: HashMap::new(),
            queued: HashMap::new(),
            assets: AssetCache::default(),
            ops: Vec::new(),
        }
    }
}

/// `Mth.clamp`: `value < min ? min : Math.min(value, max)`, so NaN stays NaN.
fn clamp(value: f32, min: f32, max: f32) -> f32 {
    if value < min {
        min
    } else if value.is_nan() || value <= max {
        value
    } else {
        max
    }
}

impl Policy {
    pub(crate) fn take_ops(&mut self) -> Vec<VoiceOp> {
        std::mem::take(&mut self.ops)
    }

    pub(crate) fn tick_count(&self) -> i32 {
        self.tick
    }

    pub(crate) fn is_active(&self, instance: u64) -> bool {
        self.active.contains_key(&instance)
    }

    pub(crate) fn set_category_volumes(&mut self, volumes: [f32; CATEGORY_COUNT]) {
        self.category_volumes = volumes;
    }

    fn calculate_volume(&self, volume: f32, category: u8) -> f32 {
        let category_volume = self.category_volumes.get(category as usize).copied().unwrap_or(1.0);
        clamp(volume, 0.0, 1.0) * clamp(category_volume, 0.0, 1.0)
    }

    fn update(&mut self, voice: u64, mask: u32, config: SoundConfigRecord) {
        self.ops.push(VoiceOp::Update { voice, mask, config });
    }

    /// `SoundEngine.play` from the volume decision on.
    pub(crate) fn play(&mut self, request: PlayRequest) -> PlayResult {
        if !self.loaded {
            return PlayResult::NotStarted;
        }
        let gain = self.calculate_volume(request.volume, request.category);
        let mut silent = false;
        if gain == 0.0 {
            if !request.can_start_silent && request.category != CATEGORY_MUSIC {
                return PlayResult::NotStarted;
            }
            silent = true;
        }
        let manual_loop = request.looping && request.delay > 0;
        let auto_loop = request.looping && request.delay <= 0;
        let attenuation = request.volume.max(1.0) * request.attenuation_distance as f32;
        let mut flags = if request.linear_attenuation {
            SOUND_FLAG_LINEAR_ATTENUATION
        } else {
            SOUND_FLAG_DISABLE_ATTENUATION
        };
        if auto_loop && !request.stream {
            flags |= SOUND_FLAG_LOOPING;
        }
        if request.relative {
            flags |= SOUND_FLAG_RELATIVE;
        }
        let config = SoundConfigRecord {
            x: request.position[0],
            y: request.position[1],
            z: request.position[2],
            pitch: clamp(request.pitch, 0.5, 2.0),
            gain,
            attenuation_distance: if request.linear_attenuation { attenuation } else { 0.0 },
            flags,
        };
        let voice = self.next_voice;
        self.next_voice += 1;
        let asset = match self.assets.acquire(&request.path, voice) {
            AssetLookup::Ready(handle) => Some(handle),
            AssetLookup::Pending | AssetLookup::Failed => None,
        };
        let failed = asset.is_none() && !self.assets.awaits(&request.path, self.assets.generation());
        self.ops.push(VoiceOp::Start { voice, config, stream: request.stream, stream_looping: auto_loop, asset });
        if failed {
            self.ops.push(VoiceOp::FailAttachment { voice });
        }
        if let Some(previous) = self.active.get(&request.instance) {
            // The replaced voice keeps playing unowned, as Java's overwritten
            // channel map entry does; it is released when it stops.
            self.voices.remove(&previous.voice);
        }
        self.voices.insert(voice, request.instance);
        self.active.insert(
            request.instance,
            Instance {
                voice,
                event: request.event,
                category: request.category,
                volume: request.volume,
                delay: request.delay,
                manual_loop,
                delete_tick: self.tick + MIN_SOURCE_LIFETIME,
                released: false,
            },
        );
        if silent {
            PlayResult::StartedSilently
        } else {
            PlayResult::Started
        }
    }

    pub(crate) fn play_delayed(&mut self, instance: u64, delay: i32) {
        self.queued.insert(instance, self.tick + delay);
    }

    /// Whether Java must keep the instance object (active or queued).
    pub(crate) fn knows(&self, instance: u64) -> bool {
        self.active.contains_key(&instance) || self.queued.contains_key(&instance)
    }

    pub(crate) fn stop(&mut self, instance: u64) {
        if self.loaded {
            if let Some(active) = self.active.get(&instance) {
                self.ops.push(VoiceOp::Stop { voice: active.voice });
            }
        }
    }

    /// `stop(location, source)` with `location` and `category` both present
    /// or one absent; both absent is `stop_all`.
    pub(crate) fn stop_matching(&mut self, event: Option<&str>, category: Option<u8>) {
        let voices: Vec<u64> = self
            .active
            .values()
            .filter(|active| category.is_none_or(|category| active.category == category))
            .filter(|active| event.is_none_or(|event| &*active.event == event))
            .map(|active| active.voice)
            .collect();
        if self.loaded {
            self.ops.extend(voices.into_iter().map(|voice| VoiceOp::Stop { voice }));
        }
    }

    /// Forgets every instance (`clearSoundPolicyState`); the caller releases all voices.
    pub(crate) fn clear(&mut self) {
        self.active.clear();
        self.voices.clear();
        self.queued.clear();
        self.ops.clear();
    }

    pub(crate) fn set_volume(&mut self, instance: u64, factor: f32, volume: f32) {
        if !self.loaded {
            return;
        }
        if let Some(active) = self.active.get(&instance) {
            let gain = factor * self.calculate_volume(volume, active.category);
            let voice = active.voice;
            self.update(voice, SOUND_UPDATE_GAIN, SoundConfigRecord { gain, ..SoundConfigRecord::default() });
        }
    }

    /// `updateCategoryVolume`: recomputes every active instance's gain.
    pub(crate) fn refresh_volumes(&mut self) {
        if !self.loaded {
            return;
        }
        let updates: Vec<(u64, f32)> = self
            .active
            .values()
            .map(|active| (active.voice, self.calculate_volume(active.volume, active.category)))
            .collect();
        for (voice, gain) in updates {
            self.update(voice, SOUND_UPDATE_GAIN, SoundConfigRecord { gain, ..SoundConfigRecord::default() });
        }
    }

    /// One ticking instance's per-tick values.
    pub(crate) fn update_ticking(&mut self, instance: u64, volume: f32, pitch: f32, position: [f32; 3]) {
        let Some(active) = self.active.get_mut(&instance) else {
            return;
        };
        active.volume = volume;
        let (voice, category) = (active.voice, active.category);
        let config = SoundConfigRecord {
            x: position[0],
            y: position[1],
            z: position[2],
            pitch: clamp(pitch, 0.5, 2.0),
            gain: self.calculate_volume(volume, category),
            ..SoundConfigRecord::default()
        };
        self.update(voice, SOUND_UPDATE_GAIN | SOUND_UPDATE_PITCH | SOUND_UPDATE_POSITION, config);
    }

    pub(crate) fn pause_all_except(&mut self, kept_categories: u32) {
        if !self.loaded {
            return;
        }
        let voices: Vec<u64> = self
            .active
            .values()
            .filter(|active| kept_categories & (1 << active.category) == 0)
            .map(|active| active.voice)
            .collect();
        self.ops.extend(voices.into_iter().map(|voice| VoiceOp::Pause { voice }));
    }

    pub(crate) fn resume(&mut self) {
        if self.loaded {
            self.ops.push(VoiceOp::ResumeAll);
        }
    }

    pub(crate) fn listener(&mut self, transform: ListenerTransform) {
        if self.loaded {
            self.ops.push(VoiceOp::Listener(transform));
        }
    }

    /// A voice the audio thread released (stopped, failed or never attached).
    pub(crate) fn voice_released(&mut self, voice: u64) {
        if let Some(instance) = self.voices.remove(&voice) {
            if let Some(active) = self.active.get_mut(&instance) {
                if active.voice == voice {
                    active.released = true;
                }
            }
        }
    }

    /// Start of `SoundEngine.tick`: in-game ticks advance the counter.
    pub(crate) fn begin_tick(&mut self, paused: bool) {
        if !paused {
            self.tick += 1;
        }
    }

    /// End of `SoundEngine.tick`: released-source cleanup, then (in game) the
    /// due delayed instances. Java plays those, then calls [`Policy::schedule_tick`].
    pub(crate) fn end_tick(&mut self, paused: bool) -> (Vec<Released>, Vec<u64>) {
        let mut released = Vec::new();
        let mut due = Vec::new();
        if !paused {
            self.cleanup(true, true, |_| true, &mut released);
            let tick = self.tick;
            self.queued.retain(|&instance, &mut at| {
                if tick >= at {
                    due.push(instance);
                    false
                } else {
                    true
                }
            });
        } else {
            self.cleanup(false, false, |category| category == CATEGORY_MUSIC, &mut released);
            self.cleanup(false, true, |category| category != CATEGORY_MUSIC, &mut released);
        }
        (released, due)
    }

    /// `ChannelAccess.scheduleTick`: the device tick and stopped-voice scan.
    pub(crate) fn schedule_tick(&mut self) {
        self.ops.push(VoiceOp::Tick);
    }

    /// `cleanupReleasedSoundPolicies`.
    fn cleanup(
        &mut self,
        allow_manual_loop: bool,
        respect_lifetime: bool,
        filter: impl Fn(u8) -> bool,
        out: &mut Vec<Released>,
    ) {
        let tick = self.tick;
        let forget: Vec<u64> = self
            .active
            .iter()
            .filter(|(_, active)| {
                filter(active.category)
                    && active.released
                    && (!respect_lifetime || active.delete_tick <= tick)
            })
            .map(|(&instance, _)| instance)
            .collect();
        for instance in forget {
            let Some(active) = self.active.remove(&instance) else { continue };
            self.voices.remove(&active.voice);
            let requeued = allow_manual_loop && active.manual_loop;
            if requeued {
                self.queued.insert(instance, tick + active.delay);
            }
            out.push(Released { instance, requeued: requeued || self.queued.contains_key(&instance) });
        }
    }

    /// Answers an asset load: waiting voices attach (or fail).
    pub(crate) fn asset_ready(&mut self, waiting: Vec<u64>, asset: u64) {
        self.ops.extend(waiting.into_iter().map(|voice| VoiceOp::Attach { voice, asset }));
    }

    pub(crate) fn asset_failed(&mut self, waiting: Vec<u64>) {
        self.ops.extend(waiting.into_iter().map(|voice| VoiceOp::FailAttachment { voice }));
    }
}

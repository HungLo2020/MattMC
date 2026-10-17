//! The client sound system above the playback [`Policy`].
//!
//! Two kinds of instances play through one policy:
//!
//! - **Java instances** keep their `SoundInstance` object: Java resolves them
//!   with the instance's own random and calls [`Client::play_java`]. Vanilla
//!   tickables that follow an entity send a [`Sample`] each tick, and their
//!   [`RuleState`] here runs the `tick()` body.
//! - **Native instances** exist only in Rust ([`NATIVE_INSTANCE`] IDs): world
//!   sound events, music, ambience and previews. [`Client::play_native`]
//!   resolves them with a Rust-owned Java-compatible random.
//!
//! Both kinds record subtitles and share the once-per-event warnings.

use std::collections::{HashMap, HashSet};
use std::sync::{Arc, LazyLock};
use std::time::Instant;

use crate::world::level::levelgen::random::Legacy;

use super::ambient::Ambient;
use super::engine::{PlayRequest, PlayResult, Released, CATEGORY_COUNT};
use super::events::{SoundRegistry, SoundSpec};
use super::music::Music;
use super::preview::Preview;
use super::rules::{RuleKind, RuleState, Sample, SAMPLE_REMOVED, SAMPLE_UNDERWATER};
use super::subtitles::Subtitles;
use super::engine::Policy;

/// Bit set in every Rust-owned instance ID; Java's IDs stay below it.
pub(crate) const NATIVE_INSTANCE: u64 = 1 << 60;
const INTENTIONALLY_EMPTY: &str = "minecraft:intentionally_empty";
/// `RandomSupport.SEED_UNIQUIFIER`'s start and step.
const SEED_UNIQUIFIER: i64 = 8_682_522_807_148_012;
const SEED_STEP: i64 = 1_181_783_497_276_652_981;

static CLOCK: LazyLock<Instant> = LazyLock::new(Instant::now);

/// A Rust-owned play: the fields of the `SimpleSoundInstance` (or ambient
/// instance) Java would have built.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct NativeSound {
    pub(crate) event: Arc<str>,
    pub(crate) category: u8,
    pub(crate) volume: f32,
    pub(crate) pitch: f32,
    pub(crate) position: [f64; 3],
    /// `Attenuation.LINEAR` (else `NONE`).
    pub(crate) linear: bool,
    pub(crate) relative: bool,
    pub(crate) looping: bool,
    pub(crate) rule: Option<RuleKind>,
}

impl NativeSound {
    /// `new SimpleSoundInstance(event, source, volume, pitch, random, x, y, z)`.
    pub(crate) fn at(event: Arc<str>, category: u8, volume: f32, pitch: f32, position: [f64; 3]) -> Self {
        Self { event, category, volume, pitch, position, linear: true, relative: false, looping: false, rule: None }
    }

    /// `SimpleSoundInstance.forUI` / `forMusic` / `forLocalAmbience`: relative
    /// and unattenuated at the listener.
    pub(crate) fn listener_relative(event: Arc<str>, category: u8, volume: f32, pitch: f32) -> Self {
        Self {
            event,
            category,
            volume,
            pitch,
            position: [0.0; 3],
            linear: false,
            relative: true,
            looping: false,
            rule: None,
        }
    }

    /// A relative, linearly attenuated tickable (the ambient loops and the
    /// underwater additions keep `AbstractSoundInstance`'s default attenuation).
    pub(crate) fn ambient_tickable(event: Arc<str>, looping: bool, rule: RuleKind) -> Self {
        Self {
            event,
            category: super::engine::CATEGORY_AMBIENT,
            volume: 1.0,
            pitch: 1.0,
            position: [0.0; 3],
            linear: true,
            relative: true,
            looping,
            rule: Some(rule),
        }
    }
}

/// A Java-instance rule's starting fields.
#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) struct JavaRule {
    pub(crate) kind: RuleKind,
    pub(crate) parameter: i32,
    pub(crate) base_volume: f32,
    pub(crate) base_pitch: f32,
    pub(crate) sound_volume: f32,
    pub(crate) sound_pitch: f32,
}

#[derive(Debug)]
struct NativeInstance {
    rule: Option<RuleState>,
    /// A controller still references it (music, a biome loop, the preview).
    held: bool,
    /// The resolved sound's location (`getSound().getLocation()`).
    location: Option<Arc<str>>,
    /// `getVolume()` at play, for `setVolume`.
    volume: f32,
    /// The local player a player-bound rule follows.
    player: u64,
}

#[derive(Debug)]
struct DelayedSound {
    due: i32,
    sound: NativeSound,
    random: Legacy,
}

/// The local player facts Rust-owned underwater sounds follow.
#[derive(Debug, Default)]
pub(crate) struct PlayerState {
    /// The `LocalPlayer` whose handlers last ticked or played; a sound bound
    /// to an earlier one sees its player removed.
    pub(crate) owner: u64,
    pub(crate) underwater: bool,
}

/// One ticked Java rule's outcome for Java.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct RuleOutcome {
    pub(crate) instance: u64,
    pub(crate) stopped: bool,
    pub(crate) switched: bool,
}

/// One entry of Java's per-tick batch: a plain tickable's values, or a ruled
/// instance's sample.
#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) enum TickUpdate {
    Plain { instance: u64, volume: f32, pitch: f32, position: [f64; 3] },
    Sampled { instance: u64, sample: Sample },
}

#[derive(Debug)]
pub(crate) struct Client {
    pub(crate) policy: Policy,
    pub(crate) registry: Arc<SoundRegistry>,
    natives: HashMap<u64, NativeInstance>,
    next_native: u64,
    java_rules: HashMap<u64, RuleState>,
    delayed: Vec<DelayedSound>,
    pub(crate) subtitles: Subtitles,
    pub(super) music: Music,
    pub(super) ambient: Ambient,
    pub(super) preview: Preview,
    pub(super) player: PlayerState,
    warned: HashSet<Arc<str>>,
    logs: Vec<String>,
    seed_uniquifier: i64,
    /// Test clock (milliseconds); the monotonic clock otherwise.
    pub(crate) fixed_time: Option<i64>,
}

impl Default for Client {
    fn default() -> Self {
        Self {
            policy: Policy::default(),
            registry: Arc::new(SoundRegistry::default()),
            natives: HashMap::new(),
            next_native: 1,
            java_rules: HashMap::new(),
            delayed: Vec::new(),
            subtitles: Subtitles::default(),
            music: Music::default(),
            ambient: Ambient::default(),
            preview: Preview::default(),
            player: PlayerState::default(),
            warned: HashSet::new(),
            logs: Vec::new(),
            seed_uniquifier: SEED_UNIQUIFIER,
            fixed_time: None,
        }
    }
}

/// `Math.max(volume, 1) * distance` for attenuated, non-relative sounds.
fn hearing_range(volume: f32, attenuation_distance: i32, linear: bool, relative: bool) -> f32 {
    if !relative && linear {
        volume.max(1.0) * attenuation_distance as f32
    } else {
        f32::INFINITY
    }
}

impl Client {
    /// Milliseconds on the subtitle clock (`Util.getMillis` equivalent).
    pub(crate) fn now_ms(&self) -> i64 {
        self.fixed_time.unwrap_or_else(|| CLOCK.elapsed().as_millis() as i64)
    }

    /// `RandomSource.create()`: a Legacy source with a fresh unique seed.
    pub(crate) fn unseeded_random(&mut self) -> Legacy {
        self.seed_uniquifier = self.seed_uniquifier.wrapping_mul(SEED_STEP);
        let nanos = CLOCK.elapsed().as_nanos() as i64;
        Legacy::new(self.seed_uniquifier ^ nanos)
    }

    /// Queues a log line (`W ` warn or `E ` error prefix) for Java's logger.
    pub(crate) fn log(&mut self, line: String) {
        self.logs.push(line);
    }

    pub(crate) fn take_logs(&mut self) -> Vec<String> {
        std::mem::take(&mut self.logs)
    }

    pub(crate) fn requeue_logs(&mut self, lines: Vec<String>) {
        let newer = std::mem::replace(&mut self.logs, lines);
        self.logs.extend(newer);
    }

    /// `ONLY_WARN_ONCE.add`.
    pub(crate) fn warn_once(&mut self, event: &str) -> bool {
        if self.warned.contains(event) {
            return false;
        }
        self.warned.insert(Arc::from(event));
        true
    }

    /// `reload`: forgets the warnings, then records the events found missing.
    pub(crate) fn reset_warnings<'a>(&mut self, missing: impl IntoIterator<Item = &'a str>) {
        self.warned.clear();
        self.warned.extend(missing.into_iter().map(Arc::from));
    }

    /// `SoundEventListener.onPlaySound` for every listener (the subtitle overlay).
    fn notify_listeners(&mut self, event: &str, position: [f64; 3], range: f32) {
        if !self.subtitles.listening {
            return;
        }
        let Some(index) = self.registry.event_index(event) else { return };
        let text = if self.subtitles.debug {
            let path = event.split_once(':').map_or(event, |(_, path)| path);
            Arc::from(path)
        } else {
            match &self.registry.events[index as usize].subtitle {
                Some(subtitle) => subtitle.clone(),
                None => return,
            }
        };
        let now = self.now_ms();
        self.subtitles.on_play(text, position, range, now);
    }

    /// `SoundEngine.play` for a Java instance after Java resolved it.
    pub(crate) fn play_java(&mut self, request: PlayRequest, position: [f64; 3], rule: Option<JavaRule>) -> PlayResult {
        let range = hearing_range(
            request.volume,
            request.attenuation_distance,
            request.linear_attenuation,
            request.relative,
        );
        self.notify_listeners(&request.event, position, range);
        let instance = request.instance;
        let result = self.policy.play(request);
        if result != PlayResult::NotStarted {
            if let Some(rule) = rule {
                // Replaying a still-active instance keeps its fields, as the
                // Java object does; only the re-resolved sound changes.
                self.java_rules
                    .entry(instance)
                    .and_modify(|state| state.set_sound(rule.sound_volume, rule.sound_pitch))
                    .or_insert_with(|| {
                    RuleState::new(
                        rule.kind,
                        rule.parameter,
                        rule.base_volume,
                        rule.base_pitch,
                        position,
                        rule.sound_volume,
                        rule.sound_pitch,
                    )
                });
            }
        }
        result
    }

    /// `SoundEngine.play` for a Rust-owned instance, resolved with `random`.
    /// `hold` keeps the instance (and its ID) for a controller even if it does
    /// not start. Returns the result and the ID (0 when nothing was kept).
    pub(crate) fn play_native(&mut self, sound: NativeSound, random: &mut Legacy, hold: bool) -> (PlayResult, u64) {
        let id = NATIVE_INSTANCE | self.next_native;
        self.next_native += 1;
        let mut instance = NativeInstance {
            rule: None,
            held: hold,
            location: None,
            volume: sound.volume,
            player: self.player.owner,
        };
        let result = self.start_native(id, &sound, random, &mut instance);
        if hold || result != PlayResult::NotStarted {
            self.natives.insert(id, instance);
            (result, id)
        } else {
            (result, 0)
        }
    }

    fn start_native(
        &mut self,
        id: u64,
        sound: &NativeSound,
        random: &mut Legacy,
        instance: &mut NativeInstance,
    ) -> PlayResult {
        if !self.policy.loaded || &*sound.event == INTENTIONALLY_EMPTY {
            return PlayResult::NotStarted;
        }
        let registry = self.registry.clone();
        let Some(event) = registry.event_index(&sound.event) else {
            if self.warn_once(&sound.event) {
                self.log(format!("W Unable to play unknown soundEvent: {}", sound.event));
            }
            return PlayResult::NotStarted;
        };
        let Some(resolved) = registry.resolve_with(event, &mut |bound| random.next_int_bound(bound)) else {
            if self.warn_once(&sound.event) {
                self.log(format!("W Unable to play empty soundEvent: {}", sound.event));
            }
            return PlayResult::NotStarted;
        };
        let volume = sound.volume * resolved.volume;
        let pitch = sound.pitch * resolved.pitch;
        instance.location = Some(resolved.location.clone());
        instance.volume = volume;
        let range = hearing_range(volume, resolved.attenuation_distance, sound.linear, sound.relative);
        self.notify_listeners(&sound.event, sound.position, range);
        let result = self.policy.play(PlayRequest {
            instance: id,
            event: sound.event.clone(),
            path: SoundSpec::file_path(&resolved.location),
            category: sound.category.min(CATEGORY_COUNT as u8 - 1),
            volume,
            pitch,
            position: sound.position.map(|axis| axis as f32),
            linear_attenuation: sound.linear,
            relative: sound.relative,
            looping: sound.looping,
            delay: 0,
            stream: resolved.stream,
            attenuation_distance: resolved.attenuation_distance,
            can_start_silent: false,
        });
        if result != PlayResult::NotStarted {
            instance.rule = sound
                .rule
                .map(|kind| RuleState::new(kind, 0, sound.volume, sound.pitch, sound.position, resolved.volume, resolved.pitch));
        }
        result
    }

    /// `playDelayed` for a Rust-owned instance: it resolves when due.
    pub(crate) fn play_native_delayed(&mut self, sound: NativeSound, random: Legacy, delay: i32) {
        let due = self.policy.tick_count().wrapping_add(delay);
        self.delayed.push(DelayedSound { due, sound, random });
    }

    /// Releases a controller's reference; the instance is forgotten once it
    /// no longer plays.
    pub(crate) fn release_hold(&mut self, id: u64) {
        if let Some(instance) = self.natives.get_mut(&id) {
            instance.held = false;
            if !self.policy.knows(id) {
                self.natives.remove(&id);
            }
        }
    }

    pub(crate) fn native_rule(&self, id: u64) -> Option<&RuleState> {
        self.natives.get(&id).and_then(|instance| instance.rule.as_ref())
    }

    pub(crate) fn native_rule_mut(&mut self, id: u64) -> Option<&mut RuleState> {
        self.natives.get_mut(&id).and_then(|instance| instance.rule.as_mut())
    }

    pub(crate) fn native_location(&self, id: u64) -> Option<Arc<str>> {
        self.natives.get(&id).and_then(|instance| instance.location.clone())
    }

    pub(crate) fn native_volume(&self, id: u64) -> f32 {
        self.natives.get(&id).map_or(1.0, |instance| instance.volume)
    }

    /// Java's per-tick batch: plain tickables' values and ruled samples.
    pub(crate) fn update_ticking(&mut self, updates: &[TickUpdate]) -> Vec<RuleOutcome> {
        let mut outcomes = Vec::new();
        for update in updates {
            match *update {
                TickUpdate::Plain { instance, volume, pitch, position } => {
                    self.policy.update_ticking(instance, volume, pitch, position.map(|axis| axis as f32));
                }
                TickUpdate::Sampled { instance, sample } => {
                    let Some(rule) = self.java_rules.get_mut(&instance) else { continue };
                    let step = rule.tick(&sample);
                    let (volume, pitch, position) = (rule.effective_volume(), rule.effective_pitch(), rule.position);
                    if step.stopped {
                        self.policy.stop(instance);
                    } else {
                        self.policy.update_ticking(instance, volume, pitch, position.map(|axis| axis as f32));
                    }
                    if step.stopped || step.switched {
                        outcomes.push(RuleOutcome { instance, stopped: step.stopped, switched: step.switched });
                    }
                }
            }
        }
        outcomes
    }

    /// Rust-owned tickables (`tickInGameSound` for the ambient loops and
    /// underwater sounds), in ID order.
    fn tick_natives(&mut self) {
        let mut ids: Vec<u64> = self
            .natives
            .iter()
            .filter(|(id, instance)| instance.rule.is_some() && self.policy.is_active(**id))
            .map(|(&id, _)| id)
            .collect();
        ids.sort_unstable();
        let underwater = self.player.underwater;
        let owner = self.player.owner;
        for id in ids {
            let Some(instance) = self.natives.get_mut(&id) else { continue };
            let removed = instance.player != owner;
            let Some(rule) = instance.rule.as_mut() else { continue };
            let sample = Sample {
                flags: (if removed { SAMPLE_REMOVED } else { 0 }) | (if underwater { SAMPLE_UNDERWATER } else { 0 }),
                ..Sample::default()
            };
            let step = rule.tick(&sample);
            let (volume, pitch, position) = (rule.effective_volume(), rule.effective_pitch(), rule.position);
            if step.stopped {
                self.policy.stop(id);
            } else {
                self.policy.update_ticking(id, volume, pitch, position.map(|axis| axis as f32));
            }
        }
    }

    pub(crate) fn begin_tick(&mut self, paused: bool) {
        self.policy.begin_tick(paused);
    }

    /// The end of `SoundEngine.tick`: Rust-owned tickables, released-source
    /// cleanup and due delayed sounds. Returns Java's released and due instances.
    pub(crate) fn end_tick(&mut self, paused: bool) -> (Vec<Released>, Vec<u64>) {
        if !paused {
            self.tick_natives();
        }
        let (released, due) = self.policy.end_tick(paused);
        let mut java_released = Vec::with_capacity(released.len());
        for entry in released {
            if entry.instance & NATIVE_INSTANCE != 0 {
                if self.natives.get(&entry.instance).is_some_and(|instance| !instance.held) {
                    self.natives.remove(&entry.instance);
                }
            } else {
                if !entry.requeued {
                    self.java_rules.remove(&entry.instance);
                }
                java_released.push(entry);
            }
        }
        if !paused {
            let tick = self.policy.tick_count();
            let (ready, waiting): (Vec<_>, Vec<_>) = std::mem::take(&mut self.delayed).into_iter().partition(|delayed| tick >= delayed.due);
            self.delayed = waiting;
            for mut delayed in ready {
                self.play_native(delayed.sound, &mut delayed.random, false);
            }
        }
        (java_released, due)
    }

    /// `stopAll` after the policy and voices were cleared: controllers keep
    /// their (now inactive) instances, as Java's keep their objects.
    pub(crate) fn clear_playback(&mut self) {
        self.natives.retain(|_, instance| instance.held);
        self.java_rules.clear();
        self.delayed.clear();
    }

    /// Makes `owner` the local player whose handlers run. A new player's
    /// handlers start fresh (as vanilla builds new ones) and the previous
    /// player's underwater sounds see it removed. Players that never tick
    /// (a map mod's camera) never take over.
    pub(crate) fn use_player(&mut self, owner: u64) {
        if owner == self.player.owner {
            return;
        }
        let loops = std::mem::take(&mut self.ambient).into_held();
        for id in loops {
            self.release_hold(id);
        }
        self.player.owner = owner;
        self.player.underwater = false;
    }

    #[cfg(test)]
    pub(crate) fn native_count(&self) -> usize {
        self.natives.len()
    }
}

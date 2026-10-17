//! The local player's ambient sound handlers, in `LocalPlayer`'s order:
//! `UnderwaterAmbientSoundHandler`, `BubbleColumnAmbientSoundHandler` and
//! `BiomeAmbientSoundsHandler` (with Iris's constant moodiness).
//!
//! They draw from the client level's shared random, so Java hands over that
//! source's raw state ([`Legacy::from_raw`]) and takes it back afterwards.
//! The mood sample needs a world light lookup at a position drawn mid-tick,
//! so a tick is two calls: [`Client::ambient_tick`] returns the block to
//! sample and [`Client::ambient_finish`] takes its light levels.

use std::sync::Arc;

use crate::world::level::levelgen::random::Legacy;

use super::client::{Client, NativeSound};
use super::engine::{CATEGORY_AMBIENT, CATEGORY_PLAYERS};
use super::rules::{clamp, RuleKind};

pub(crate) const UNDERWATER_LOOP: &str = "minecraft:ambient.underwater.loop";
const UNDERWATER_ADDITIONS: &str = "minecraft:ambient.underwater.loop.additions";
const UNDERWATER_ADDITIONS_RARE: &str = "minecraft:ambient.underwater.loop.additions.rare";
const UNDERWATER_ADDITIONS_ULTRA_RARE: &str = "minecraft:ambient.underwater.loop.additions.ultra_rare";
const BUBBLE_COLUMN_WHIRLPOOL_INSIDE: &str = "minecraft:block.bubble_column.whirlpool_inside";
const BUBBLE_COLUMN_UPWARDS_INSIDE: &str = "minecraft:block.bubble_column.upwards_inside";

/// `AmbientMoodSettings`.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct MoodSettings {
    pub(crate) event: Arc<str>,
    pub(crate) tick_delay: i32,
    pub(crate) block_search_extent: i32,
    pub(crate) sound_position_offset: f64,
}

/// `AmbientAdditionsSettings`.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct AdditionsSettings {
    pub(crate) event: Arc<str>,
    pub(crate) tick_chance: f64,
}

/// A biome the player entered this tick (`biome != previousBiome`).
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct BiomeChange {
    /// Java's identity key for the `Biome` object.
    pub(crate) biome: i32,
    pub(crate) ambient_loop: Option<Arc<str>>,
    pub(crate) mood: Option<MoodSettings>,
    pub(crate) additions: Option<AdditionsSettings>,
}

/// The bubble column state under the player's shrunk bounding box.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum BubbleColumn {
    None,
    Upwards,
    /// `DRAG_DOWN` (whirlpool).
    Downwards,
}

/// One tick's player facts.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct AmbientInput {
    /// `getX()`, `getY()`, `getEyeY()`, `getZ()`.
    pub(crate) x: f64,
    pub(crate) y: f64,
    pub(crate) eye_y: f64,
    pub(crate) z: f64,
    pub(crate) underwater: bool,
    pub(crate) spectator: bool,
    pub(crate) bubble_column: BubbleColumn,
    pub(crate) biome_change: Option<BiomeChange>,
    /// Sky and block light at `blockPosition()` (Iris's constant mood).
    pub(crate) sky_light: i32,
    pub(crate) block_light: i32,
}

/// What Java reads back after each call.
#[derive(Clone, Copy, Debug, Default, PartialEq)]
pub(crate) struct AmbientOutput {
    /// The level random's raw state to write back.
    pub(crate) random: i64,
    /// The mood light sample position when [`Client::ambient_finish`] must follow.
    pub(crate) mood_sample: Option<[i32; 3]>,
    pub(crate) moodiness: f32,
    pub(crate) constant_moodiness: f32,
}

#[derive(Debug)]
struct PendingMood {
    sample: [i32; 3],
    player: [f64; 3],
    settings: MoodSettings,
    random: Legacy,
}

#[derive(Debug)]
pub(crate) struct Ambient {
    underwater_tick_delay: i32,
    was_in_bubble_column: bool,
    first_tick: bool,
    /// `loopSounds`, in insertion order: (biome key, held native instance).
    loops: Vec<(i32, u64)>,
    mood: Option<MoodSettings>,
    additions: Option<AdditionsSettings>,
    moodiness: f32,
    constant_moodiness: f32,
    pending: Option<PendingMood>,
}

impl Default for Ambient {
    fn default() -> Self {
        Self {
            underwater_tick_delay: 0,
            was_in_bubble_column: false,
            first_tick: true,
            loops: Vec::new(),
            mood: None,
            additions: None,
            moodiness: 0.0,
            constant_moodiness: 0.0,
            pending: None,
        }
    }
}

impl Ambient {
    /// The held loop instances, released when the handlers are replaced.
    pub(crate) fn into_held(self) -> Vec<u64> {
        self.loops.into_iter().map(|(_, id)| id).collect()
    }
}

impl Client {
    /// `LocalPlayer.updateIsUnderwater` entering water: the underwater loop.
    pub(crate) fn play_underwater_loop(&mut self, owner: u64) {
        self.use_player(owner);
        let mut random = self.unseeded_random();
        let sound = NativeSound::ambient_tickable(Arc::from(UNDERWATER_LOOP), true, RuleKind::UnderwaterLoop);
        self.play_native(sound, &mut random, false);
    }

    /// `owner`'s handlers' `tick()` up to the mood light sample.
    pub(crate) fn ambient_tick(&mut self, owner: u64, input: &AmbientInput, level_random: i64) -> AmbientOutput {
        self.use_player(owner);
        self.player.underwater = input.underwater;
        self.ambient.pending = None;
        let mut random = Legacy::from_raw(level_random);
        self.underwater_tick(input, &mut random);
        self.bubble_column_tick(input, &mut random);
        let sample = self.biome_tick(input, &mut random);
        match sample {
            Some(pending) => {
                let sample = pending.sample;
                let random = pending.random.raw();
                self.ambient.pending = Some(pending);
                AmbientOutput { random, mood_sample: Some(sample), ..self.ambient_output(0) }
            }
            None => self.ambient_output(random.raw()),
        }
    }

    /// The rest of the mood handler, with the sampled sky and block light.
    pub(crate) fn ambient_finish(&mut self, sky_light: i32, block_light: i32) -> AmbientOutput {
        let Some(mut pending) = self.ambient.pending.take() else {
            return self.ambient_output(0);
        };
        let settings = &pending.settings;
        if sky_light > 0 {
            self.ambient.moodiness -= sky_light as f32 / 15.0 * 0.001;
        } else {
            self.ambient.moodiness -= (block_light - 1) as f32 / settings.tick_delay as f32;
        }
        if self.ambient.moodiness >= 1.0 {
            let [px, eye_y, pz] = pending.player;
            let d = f64::from(pending.sample[0]) + 0.5;
            let e = f64::from(pending.sample[1]) + 0.5;
            let f = f64::from(pending.sample[2]) + 0.5;
            let g = d - px;
            let h = e - eye_y;
            let k = f - pz;
            let l = (g * g + h * h + k * k).sqrt();
            let m = l + settings.sound_position_offset;
            let position = [px + g / l * m, eye_y + h / l * m, pz + k / l * m];
            let sound = NativeSound::at(settings.event.clone(), CATEGORY_AMBIENT, 1.0, 1.0, position);
            self.play_native(sound, &mut pending.random, false);
            self.ambient.moodiness = 0.0;
        } else {
            self.ambient.moodiness = self.ambient.moodiness.max(0.0);
        }
        self.ambient_output(pending.random.raw())
    }

    fn ambient_output(&self, random: i64) -> AmbientOutput {
        AmbientOutput {
            random,
            mood_sample: None,
            moodiness: self.ambient.moodiness,
            constant_moodiness: self.ambient.constant_moodiness,
        }
    }

    /// `UnderwaterAmbientSoundHandler.tick`.
    fn underwater_tick(&mut self, input: &AmbientInput, random: &mut Legacy) {
        self.ambient.underwater_tick_delay -= 1;
        if self.ambient.underwater_tick_delay > 0 || !input.underwater {
            return;
        }
        let chance = random.next_float();
        let event = if chance < 1.0e-4 {
            UNDERWATER_ADDITIONS_ULTRA_RARE
        } else if chance < 0.001 {
            UNDERWATER_ADDITIONS_RARE
        } else if chance < 0.01 {
            UNDERWATER_ADDITIONS
        } else {
            return;
        };
        self.ambient.underwater_tick_delay = 0;
        let mut sound_random = self.unseeded_random();
        let sound = NativeSound::ambient_tickable(Arc::from(event), false, RuleKind::UnderwaterSub);
        self.play_native(sound, &mut sound_random, false);
    }

    /// `BubbleColumnAmbientSoundHandler.tick`.
    fn bubble_column_tick(&mut self, input: &AmbientInput, random: &mut Legacy) {
        if input.bubble_column == BubbleColumn::None {
            self.ambient.was_in_bubble_column = false;
        } else {
            if !self.ambient.was_in_bubble_column && !self.ambient.first_tick && !input.spectator {
                let event = if input.bubble_column == BubbleColumn::Downwards {
                    BUBBLE_COLUMN_WHIRLPOOL_INSIDE
                } else {
                    BUBBLE_COLUMN_UPWARDS_INSIDE
                };
                // `LocalPlayer.playSound` -> `ClientLevel.playLocalSound`,
                // seeded from the level random.
                let mut sound_random = Legacy::new(random.next_long());
                let sound = NativeSound::at(Arc::from(event), CATEGORY_PLAYERS, 1.0, 1.0, [input.x, input.y, input.z]);
                self.play_native(sound, &mut sound_random, false);
            }
            self.ambient.was_in_bubble_column = true;
        }
        self.ambient.first_tick = false;
    }

    /// `BiomeAmbientSoundsHandler.tick` up to the mood light lookup.
    fn biome_tick(&mut self, input: &AmbientInput, random: &mut Legacy) -> Option<PendingMood> {
        let loops = std::mem::take(&mut self.ambient.loops);
        let mut kept = Vec::with_capacity(loops.len());
        for (biome, id) in loops {
            if self.native_rule(id).is_some_and(|rule| rule.stopped) {
                self.release_hold(id);
            } else {
                kept.push((biome, id));
            }
        }
        self.ambient.loops = kept;
        if let Some(change) = &input.biome_change {
            self.ambient.mood = change.mood.clone();
            self.ambient.additions = change.additions.clone();
            for (_, id) in self.ambient.loops.clone() {
                self.loop_rule(id, |rule| rule.fade_out());
            }
            if let Some(event) = &change.ambient_loop {
                let existing = self.ambient.loops.iter().find(|(biome, _)| *biome == change.biome).map(|&(_, id)| id);
                let id = match existing {
                    Some(id) => id,
                    None => {
                        let mut sound_random = self.unseeded_random();
                        let sound = NativeSound::ambient_tickable(event.clone(), true, RuleKind::BiomeLoop);
                        let (_, id) = self.play_native(sound, &mut sound_random, true);
                        self.ambient.loops.push((change.biome, id));
                        id
                    }
                };
                self.loop_rule(id, |rule| rule.fade_in());
            }
        }
        if let Some(additions) = self.ambient.additions.clone() {
            if random.next_double() < additions.tick_chance {
                let mut sound_random = self.unseeded_random();
                let sound = NativeSound::listener_relative(additions.event, CATEGORY_AMBIENT, 1.0, 1.0);
                self.play_native(sound, &mut sound_random, false);
            }
        }
        // Iris: constant moodiness for shader uniforms.
        if input.sky_light > 0 {
            self.ambient.constant_moodiness -= input.sky_light as f32 / 15.0 * 0.001;
        } else {
            self.ambient.constant_moodiness -= (input.block_light - 1) as f32 / 6000.0;
        }
        self.ambient.constant_moodiness = clamp(self.ambient.constant_moodiness, 0.0, 1.0);
        let settings = self.ambient.mood.clone()?;
        let extent = settings.block_search_extent;
        let bound = extent.wrapping_mul(2).wrapping_add(1);
        let x = (input.x + f64::from(random.next_int_bound(bound)) - f64::from(extent)).floor() as i32;
        let y = (input.eye_y + f64::from(random.next_int_bound(bound)) - f64::from(extent)).floor() as i32;
        let z = (input.z + f64::from(random.next_int_bound(bound)) - f64::from(extent)).floor() as i32;
        Some(PendingMood {
            sample: [x, y, z],
            player: [input.x, input.eye_y, input.z],
            settings,
            random: random.clone(),
        })
    }

    /// Applies a fade to a loop. One that never started has no rule: Java
    /// fades its unplayed object, which never ticks, so nothing observes it.
    fn loop_rule(&mut self, id: u64, f: impl FnOnce(&mut super::rules::RuleState)) {
        if let Some(rule) = self.native_rule_mut(id) {
            f(rule);
        }
    }
}

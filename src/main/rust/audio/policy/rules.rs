//! Per-tick sound rules: the vanilla `TickableSoundInstance.tick()` bodies.
//!
//! Java samples the entity facts a rule reads (removed, riding, speed, ...)
//! into a [`Sample`]; the rule state here owns everything the Java instance
//! fields held (volume, pitch, position, fades and counters). The arithmetic
//! is transcribed in Java's `float` order so results match bit for bit.

/// The sampled entity is removed (or, for Rust-owned player sounds, the
/// local player was replaced).
pub(crate) const SAMPLE_REMOVED: u32 = 1 << 0;
/// The local player rides the sound's vehicle.
pub(crate) const SAMPLE_RIDING: u32 = 1 << 1;
pub(crate) const SAMPLE_UNDERWATER: u32 = 1 << 2;
/// `!minecart.isOnRails() && minecart.getBehavior() instanceof NewMinecartBehavior`.
pub(crate) const SAMPLE_OFF_RAILS: u32 = 1 << 3;
/// `level.tickRateManager().runsNormally()`.
pub(crate) const SAMPLE_RUNS_NORMALLY: u32 = 1 << 4;
pub(crate) const SAMPLE_FALL_FLYING: u32 = 1 << 5;
/// `BeeSoundInstance.shouldSwitchSounds()`.
pub(crate) const SAMPLE_SWITCH: u32 = 1 << 6;
pub(crate) const SAMPLE_BABY: u32 = 1 << 7;
pub(crate) const SAMPLE_HAS_TARGET: u32 = 1 << 8;
/// `Sniffer.canPlayDiggingSound()`.
pub(crate) const SAMPLE_CAN_DIG: u32 = 1 << 9;

/// Which `tick()` body an instance runs.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum RuleKind {
    EntityBound,
    Minecart,
    /// Parameter: 1 for the underwater variant.
    RidingMinecart,
    Elytra,
    /// `BeeFlyingSoundInstance` / `BeeAggressiveSoundInstance`.
    Bee,
    GuardianAttack,
    RidingHappyGhast,
    Sniffer,
    /// `BiomeAmbientSoundsHandler.LoopSoundInstance` (Rust-owned).
    BiomeLoop,
    /// `UnderwaterAmbientSoundInstance` (Rust-owned).
    UnderwaterLoop,
    /// `UnderwaterAmbientSoundInstances.SubSound` (Rust-owned).
    UnderwaterSub,
}

impl RuleKind {
    /// The Java-sampled kinds, by their ABI number (0 is "no rule").
    pub(crate) fn from_abi(kind: i32) -> Option<Self> {
        Some(match kind {
            1 => Self::EntityBound,
            2 => Self::Minecart,
            3 => Self::RidingMinecart,
            4 => Self::Elytra,
            5 => Self::Bee,
            6 => Self::GuardianAttack,
            7 => Self::RidingHappyGhast,
            8 => Self::Sniffer,
            _ => return None,
        })
    }
}

/// One tick's sampled facts.
#[derive(Clone, Copy, Debug, Default, PartialEq)]
pub(crate) struct Sample {
    pub(crate) flags: u32,
    pub(crate) position: [f64; 3],
    /// The rule's one measured value: horizontal speed, `lengthSqr`, the
    /// guardian attack scale or the happy ghast speed.
    pub(crate) value: f32,
}

impl Sample {
    fn has(&self, flag: u32) -> bool {
        self.flags & flag != 0
    }
}

/// What a tick asks the engine to do.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub(crate) struct Step {
    /// `isStopped()` after the tick.
    pub(crate) stopped: bool,
    /// A bee queued its alternative instance this tick.
    pub(crate) switched: bool,
}

/// `Mth.clamp(float)`: `value < min ? min : Math.min(value, max)`, so NaN stays NaN.
pub(crate) fn clamp(value: f32, min: f32, max: f32) -> f32 {
    if value < min {
        min
    } else if value.is_nan() || value <= max {
        value
    } else {
        max
    }
}

/// `Mth.lerp(float delta, float start, float end)`.
fn lerp(delta: f32, start: f32, end: f32) -> f32 {
    start + delta * (end - start)
}

/// `Mth.clampedLerp(float, float, float)`.
fn clamped_lerp(start: f32, end: f32, delta: f32) -> f32 {
    if delta < 0.0 {
        start
    } else if delta > 1.0 {
        end
    } else {
        lerp(delta, start, end)
    }
}

/// `(float)entity.getX()` stored in a double field.
fn float_position(position: [f64; 3]) -> [f64; 3] {
    position.map(|axis| f64::from(axis as f32))
}

/// The instance fields a rule reads and writes.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct RuleState {
    pub(crate) kind: RuleKind,
    parameter: i32,
    /// `AbstractSoundInstance.volume` / `pitch` / position.
    pub(crate) volume: f32,
    pub(crate) pitch: f32,
    pub(crate) position: [f64; 3],
    /// The resolved `Sound`'s volume and pitch factors.
    sound_volume: f32,
    sound_pitch: f32,
    /// Elytra `time`, or a fade counter.
    counter: i32,
    /// Biome loop fade direction.
    direction: i32,
    /// `BeeSoundInstance.hasSwitched`.
    switched: bool,
    pub(crate) stopped: bool,
}

impl RuleState {
    pub(crate) fn new(
        kind: RuleKind,
        parameter: i32,
        volume: f32,
        pitch: f32,
        position: [f64; 3],
        sound_volume: f32,
        sound_pitch: f32,
    ) -> Self {
        Self {
            kind,
            parameter,
            volume,
            pitch,
            position,
            sound_volume,
            sound_pitch,
            counter: 0,
            direction: 0,
            switched: false,
            stopped: false,
        }
    }

    /// A re-resolved sound's factors.
    pub(crate) fn set_sound(&mut self, volume: f32, pitch: f32) {
        self.sound_volume = volume;
        self.sound_pitch = pitch;
    }

    /// `getVolume()`: the instance volume times the sound's.
    pub(crate) fn effective_volume(&self) -> f32 {
        self.volume * self.sound_volume
    }

    /// `getPitch()`.
    pub(crate) fn effective_pitch(&self) -> f32 {
        self.pitch * self.sound_pitch
    }

    /// `AbstractTickableSoundInstance.stop()`.
    fn stop(&mut self) {
        self.stopped = true;
    }

    /// `LoopSoundInstance.fadeOut`.
    pub(crate) fn fade_out(&mut self) {
        self.counter = self.counter.min(40);
        self.direction = -1;
    }

    /// `LoopSoundInstance.fadeIn`.
    pub(crate) fn fade_in(&mut self) {
        self.counter = self.counter.max(0);
        self.direction = 1;
    }

    /// One `tick()`.
    pub(crate) fn tick(&mut self, sample: &Sample) -> Step {
        let mut step = Step::default();
        match self.kind {
            RuleKind::EntityBound => {
                if sample.has(SAMPLE_REMOVED) {
                    self.stop();
                } else {
                    self.position = float_position(sample.position);
                }
            }
            RuleKind::Minecart => {
                if sample.has(SAMPLE_REMOVED) {
                    self.stop();
                } else {
                    self.position = float_position(sample.position);
                    let speed = sample.value;
                    // Its private `pitch` shadows the instance field, so the
                    // sound's pitch never changes.
                    if speed >= 0.01 && sample.has(SAMPLE_RUNS_NORMALLY) && !sample.has(SAMPLE_OFF_RAILS) {
                        self.volume = lerp(clamp(speed, 0.0, 0.5), 0.0, 0.7);
                    } else {
                        self.volume = 0.0;
                    }
                }
            }
            RuleKind::RidingMinecart => {
                if sample.has(SAMPLE_REMOVED) || !sample.has(SAMPLE_RIDING) {
                    self.stop();
                } else if (self.parameter != 0) != sample.has(SAMPLE_UNDERWATER) {
                    self.volume = 0.0;
                } else {
                    let speed = sample.value;
                    if speed >= 0.01 && !sample.has(SAMPLE_OFF_RAILS) {
                        self.volume = clamped_lerp(0.0, 0.75, speed);
                    } else {
                        self.volume = 0.0;
                    }
                }
            }
            RuleKind::Elytra => {
                self.counter = self.counter.wrapping_add(1);
                let time = self.counter;
                if !sample.has(SAMPLE_REMOVED) && (time <= 20 || sample.has(SAMPLE_FALL_FLYING)) {
                    self.position = float_position(sample.position);
                    let speed = sample.value;
                    self.volume = if f64::from(speed) >= 1.0e-7 { clamp(speed / 4.0, 0.0, 1.0) } else { 0.0 };
                    if time < 20 {
                        self.volume = 0.0;
                    } else if time < 40 {
                        self.volume *= (time - 20) as f32 / 20.0;
                    }
                    self.pitch = if self.volume > 0.8 { 1.0 + (self.volume - 0.8) } else { 1.0 };
                } else {
                    self.stop();
                }
            }
            RuleKind::Bee => {
                if sample.has(SAMPLE_SWITCH) && !self.stopped {
                    step.switched = true;
                    self.switched = true;
                }
                if !sample.has(SAMPLE_REMOVED) && !self.switched {
                    self.position = float_position(sample.position);
                    let speed = sample.value;
                    let (min, max) = if sample.has(SAMPLE_BABY) { (1.1, 1.5) } else { (0.7, 1.1) };
                    if speed >= 0.01 {
                        self.pitch = lerp(clamp(speed, min, max), min, max);
                        self.volume = lerp(clamp(speed, 0.0, 0.5), 0.0, 1.2);
                    } else {
                        self.pitch = 0.0;
                        self.volume = 0.0;
                    }
                } else {
                    self.stop();
                }
            }
            RuleKind::GuardianAttack => {
                if !sample.has(SAMPLE_REMOVED) && !sample.has(SAMPLE_HAS_TARGET) {
                    self.position = float_position(sample.position);
                    let scale = sample.value;
                    self.volume = 0.0 + 1.0 * scale * scale;
                    self.pitch = 0.7 + 0.5 * scale;
                } else {
                    self.stop();
                }
            }
            RuleKind::RidingHappyGhast => {
                if !sample.has(SAMPLE_REMOVED) && sample.has(SAMPLE_RIDING) {
                    let speed = sample.value;
                    self.volume = if speed >= 0.01 { 5.0 * clamped_lerp(0.0, 1.0, speed) } else { 0.0 };
                } else {
                    self.stop();
                }
            }
            RuleKind::Sniffer => {
                if !sample.has(SAMPLE_REMOVED) && !sample.has(SAMPLE_HAS_TARGET) && sample.has(SAMPLE_CAN_DIG) {
                    self.position = float_position(sample.position);
                    self.volume = 1.0;
                    self.pitch = 1.0;
                } else {
                    self.stop();
                }
            }
            RuleKind::BiomeLoop => {
                if self.counter < 0 {
                    self.stop();
                }
                self.counter = self.counter.wrapping_add(self.direction);
                self.volume = clamp(self.counter as f32 / 40.0, 0.0, 1.0);
            }
            RuleKind::UnderwaterLoop => {
                if !sample.has(SAMPLE_REMOVED) && self.counter >= 0 {
                    if sample.has(SAMPLE_UNDERWATER) {
                        self.counter += 1;
                    } else {
                        self.counter -= 2;
                    }
                    self.counter = self.counter.min(40);
                    self.volume = 0.0f32.max((self.counter as f32 / 40.0).min(1.0));
                } else {
                    self.stop();
                }
            }
            RuleKind::UnderwaterSub => {
                if sample.has(SAMPLE_REMOVED) || !sample.has(SAMPLE_UNDERWATER) {
                    self.stop();
                }
            }
        }
        step.stopped = self.stopped;
        step
    }
}

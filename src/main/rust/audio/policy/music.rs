//! Situational music (`MusicManager`).
//!
//! Java reports the situation each tick (`Minecraft.getSituationalMusic()`:
//! the music event, its delays, whether it replaces the current track, and
//! the volume); Rust owns the current track, the song delay, cross-fades,
//! the music random and the "now playing" toast state. Results are toast
//! actions for Java's UI.

use std::sync::Arc;

use crate::world::level::levelgen::random::Legacy;

use super::client::{Client, NativeSound};
use super::engine::{PlayResult, CATEGORY_MUSIC};
use super::rules::clamp;

/// `MusicManager.STARTING_DELAY`.
const STARTING_DELAY: i32 = 100;
pub(crate) const ACTION_SHOW_TOAST: i32 = 1 << 0;
pub(crate) const ACTION_HIDE_TOAST: i32 = 1 << 1;

/// `Music`: the track and its delays.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Track {
    pub(crate) event: Arc<str>,
    pub(crate) min_delay: i32,
    pub(crate) max_delay: i32,
    pub(crate) replace_current: bool,
}

/// `MusicInfo`.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Situation {
    pub(crate) track: Option<Track>,
    pub(crate) volume: f32,
}

/// `MusicManager.MusicFrequency` by ordinal: `DEFAULT`, `FREQUENT`, `CONSTANT`.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum Frequency {
    Default,
    Frequent,
    Constant,
}

impl Frequency {
    pub(crate) fn from_ordinal(ordinal: i32) -> Self {
        match ordinal {
            1 => Self::Frequent,
            2 => Self::Constant,
            _ => Self::Default,
        }
    }

    fn max_frequency(self) -> i32 {
        match self {
            Self::Default => 20 * 1200,
            Self::Frequent => 10 * 1200,
            Self::Constant => 0,
        }
    }

    /// `MusicFrequency.getNextSongDelay`.
    fn next_song_delay(self, track: Option<&Track>, random: &mut Legacy) -> i32 {
        let Some(track) = track else { return self.max_frequency() };
        if self == Self::Constant {
            return 100;
        }
        let min = track.min_delay.min(self.max_frequency());
        let max = track.max_delay.min(self.max_frequency());
        next_int(random, min, max)
    }
}

/// `Mth.nextInt(random, min, max)`.
fn next_int(random: &mut Legacy, min: i32, max: i32) -> i32 {
    if min >= max {
        min
    } else {
        random.next_int_bound(max.wrapping_sub(min).wrapping_add(1)).wrapping_add(min)
    }
}

#[derive(Debug)]
pub(crate) struct Music {
    /// The current track's native instance (held) and its event location.
    current: Option<(u64, Arc<str>)>,
    frequency: Frequency,
    gain: f32,
    next_song_delay: i32,
    toast_shown: bool,
    random: Legacy,
}

impl Default for Music {
    fn default() -> Self {
        Self::new(Frequency::Default, Legacy::new(0))
    }
}

impl Music {
    pub(crate) fn new(frequency: Frequency, random: Legacy) -> Self {
        Self { current: None, frequency, gain: 1.0, next_song_delay: STARTING_DELAY, toast_shown: false, random }
    }

    #[cfg(test)]
    pub(crate) fn next_song_delay(&self) -> i32 {
        self.next_song_delay
    }
}

impl Client {
    /// `new MusicManager(minecraft)`.
    pub(crate) fn music_reset(&mut self, frequency: Frequency) {
        if let Some((id, _)) = self.music.current.take() {
            self.release_hold(id);
        }
        let random = self.unseeded_random();
        self.music = Music::new(frequency, random);
    }

    /// `MusicManager.tick`.
    pub(crate) fn music_tick(&mut self, situation: &Situation) -> i32 {
        let mut actions = 0;
        let volume = situation.volume;
        if self.music.current.is_some() && self.music.gain != volume && !self.music_fade(volume, &mut actions) {
            return actions;
        }
        let Some(track) = &situation.track else {
            self.music.next_song_delay = self.music.next_song_delay.max(STARTING_DELAY);
            return actions;
        };
        if let Some((id, event)) = self.music.current.clone() {
            if track.replace_current && track.event != event {
                self.policy.stop(id);
                self.music.next_song_delay = next_int(&mut self.music.random, 0, track.min_delay / 2);
            }
            if !self.policy.is_active(id) {
                self.music.current = None;
                self.release_hold(id);
                let delay = self.music.frequency.next_song_delay(Some(track), &mut self.music.random);
                self.music.next_song_delay = self.music.next_song_delay.min(delay);
            }
        }
        let delay = self.music.frequency.next_song_delay(Some(track), &mut self.music.random);
        self.music.next_song_delay = self.music.next_song_delay.min(delay);
        if self.music.current.is_none() {
            let due = self.music.next_song_delay <= 0;
            self.music.next_song_delay = self.music.next_song_delay.wrapping_sub(1);
            if due {
                actions |= self.music_start(&track.event.clone(), volume);
            }
        }
        actions
    }

    /// `MusicManager.startPlaying`.
    pub(crate) fn music_start(&mut self, event: &Arc<str>, volume: f32) -> i32 {
        if let Some((id, _)) = self.music.current.take() {
            self.release_hold(id);
        }
        let mut random = self.unseeded_random();
        let sound = NativeSound::listener_relative(event.clone(), CATEGORY_MUSIC, volume, 1.0);
        let (result, id) = self.play_native(sound, &mut random, true);
        self.music.current = Some((id, event.clone()));
        let mut actions = 0;
        match result {
            PlayResult::Started => {
                actions |= ACTION_SHOW_TOAST;
                self.music.toast_shown = true;
            }
            PlayResult::StartedSilently => self.music.toast_shown = false,
            PlayResult::NotStarted => {}
        }
        self.music.next_song_delay = i32::MAX;
        self.music.gain = volume;
        actions
    }

    /// `MusicManager.showNowPlayingToastIfNeeded`.
    pub(crate) fn music_toast_if_needed(&mut self) -> i32 {
        if self.music.toast_shown {
            0
        } else {
            self.music.toast_shown = true;
            ACTION_SHOW_TOAST
        }
    }

    /// `MusicManager.stopPlaying()`.
    pub(crate) fn music_stop(&mut self) -> i32 {
        let mut actions = 0;
        if let Some((id, _)) = self.music.current.take() {
            self.policy.stop(id);
            self.release_hold(id);
            actions |= ACTION_HIDE_TOAST;
        }
        self.music.next_song_delay = self.music.next_song_delay.wrapping_add(100);
        actions
    }

    /// `MusicManager.stopPlaying(music)`.
    pub(crate) fn music_stop_track(&mut self, event: &str) -> i32 {
        if self.music_is_playing(event) {
            self.music_stop()
        } else {
            0
        }
    }

    /// `MusicManager.isPlayingMusic`.
    pub(crate) fn music_is_playing(&self, event: &str) -> bool {
        self.music.current.as_ref().is_some_and(|(_, current)| &**current == event)
    }

    /// The current track's resolved sound location (for its translation key).
    pub(crate) fn music_current_sound(&self) -> Option<Arc<str>> {
        let (id, _) = self.music.current.as_ref()?;
        self.native_location(*id)
    }

    /// `MusicManager.setMinutesBetweenSongs`.
    pub(crate) fn music_set_frequency(&mut self, frequency: Frequency, track: Option<&Track>) {
        self.music.frequency = frequency;
        self.music.next_song_delay = frequency.next_song_delay(track, &mut self.music.random);
    }

    /// `MusicManager.fadePlaying`: false when the fade stopped the track.
    fn music_fade(&mut self, volume: f32, actions: &mut i32) -> bool {
        let Some((id, _)) = self.music.current.clone() else { return false };
        let mut gain = self.music.gain;
        if gain == volume {
            return true;
        }
        if gain < volume {
            gain += clamp(gain, 5.0e-4, 0.005);
            if gain > volume {
                gain = volume;
            }
        } else {
            gain = 0.03 * volume + 0.97 * gain;
            if (gain - volume).abs() < 1.0e-4 || gain < volume {
                gain = volume;
            }
        }
        gain = clamp(gain, 0.0, 1.0);
        self.music.gain = gain;
        if gain <= 1.0e-4 {
            *actions |= self.music_stop();
            false
        } else {
            let instance_volume = self.native_volume(id);
            self.policy.set_volume(id, gain, instance_volume);
            true
        }
    }
}

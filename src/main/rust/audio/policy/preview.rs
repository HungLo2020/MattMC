//! The volume slider preview (`SoundPreviewHandler`).

use std::sync::Arc;

use super::client::{Client, NativeSound};
use super::engine::CATEGORY_UI;

/// The preview event for each `SoundSource` ordinal (`EMPTY` for the rest).
pub(crate) const PREVIEW_EVENTS: [Option<&str>; 11] = [
    None,                                      // MASTER
    None,                                      // MUSIC
    Some("minecraft:block.note_block.guitar"), // RECORDS
    Some("minecraft:entity.lightning_bolt.thunder"), // WEATHER
    Some("minecraft:block.grass.place"),       // BLOCKS
    Some("minecraft:entity.zombie.ambient"),   // HOSTILE
    Some("minecraft:entity.cow.ambient"),      // NEUTRAL
    Some("minecraft:entity.generic.eat"),      // PLAYERS
    Some("minecraft:ambient.cave"),            // AMBIENT
    None,                                      // VOICE
    Some("minecraft:ui.button.click"),         // UI
];

#[derive(Debug, Default)]
pub(crate) struct Preview {
    /// The last preview instance (0 when it never got an ID).
    active: Option<u64>,
    previous_category: Option<u8>,
}

impl Client {
    /// `SoundPreviewHandler.preview(soundManager, source, volume)`.
    pub(crate) fn preview(&mut self, category: u8, volume: f32) {
        if self.preview.previous_category != Some(category) {
            self.preview.previous_category = Some(category);
            if let Some(id) = self.preview.active {
                self.policy.stop(id);
            }
        }
        if self.preview.active.is_some_and(|id| self.policy.is_active(id)) {
            return;
        }
        let Some(event) = PREVIEW_EVENTS.get(category as usize).copied().flatten() else { return };
        let mut random = self.unseeded_random();
        let sound = NativeSound::listener_relative(Arc::from(event), CATEGORY_UI, volume, 1.0);
        let (_, id) = self.play_native(sound, &mut random, false);
        self.preview.active = Some(id);
    }
}

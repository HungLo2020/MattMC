//! Shared content parameters for interactive block families. World mutation,
//! scheduling, entity queries and audio playback remain with their consumers.
mod sets;
mod woods;
mod ffi;
#[cfg(test)]
mod tests;

pub use sets::BlockSet;
pub use woods::Wood;
use crate::content::sound::{self, EventId, SoundType};
use std::sync::OnceLock;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
#[repr(u8)]
pub enum PressureSensitivity { Everything, Mobs }

pub struct BlockSetDefinition {
    pub id: BlockSet,
    pub name: &'static str,
    pub can_open_by_hand: bool,
    pub can_open_by_wind_charge: bool,
    pub arrows_activate_button: bool,
    pub pressure_sensitivity: PressureSensitivity,
    pub sound: SoundType,
    /// Door close/open, trapdoor close/open, plate off/on, button off/on.
    pub events: [EventId; 8],
}

pub struct WoodDefinition {
    pub id: Wood,
    pub name: &'static str,
    pub set: BlockSet,
    pub sound: SoundType,
    pub hanging_sign_sound: SoundType,
    /// Gate close/open.
    pub gate_events: [EventId; 2],
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
#[repr(u8)]
pub enum SignAttachment { Standing, Wall, CeilingHanging, WallHanging }

/// Only the parameters appropriate to each family can be represented.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Family {
    None,
    Door(BlockSet),
    Trapdoor(BlockSet),
    Button { set: BlockSet, press_ticks: u16 },
    PressurePlate(BlockSet),
    WeightedPlate { set: BlockSet, max_weight: u16 },
    FenceGate(Wood),
    Sign { wood: Wood, attachment: SignAttachment },
}
impl Family {
    /// Compact immutable CPU projection for temporary Java construction.
    pub fn words(self) -> [i32; 3] {
        match self {
            Self::None => [0, 0, 0],
            Self::Door(set) => [1, set as i32, 0],
            Self::Trapdoor(set) => [2, set as i32, 0],
            Self::Button { set, press_ticks } => {
                assert!((1..=1024).contains(&press_ticks), "bounded button duration");
                [3, set as i32, i32::from(press_ticks)]
            }
            Self::PressurePlate(set) => [4, set as i32, 0],
            Self::WeightedPlate { set, max_weight } => {
                assert!((1..=1024).contains(&max_weight), "bounded plate weight");
                [5, set as i32, i32::from(max_weight)]
            }
            Self::FenceGate(wood) => [6, wood as i32, 0],
            Self::Sign { wood, attachment } => [7 + attachment as i32, wood as i32, 0],
        }
    }
}

pub struct Registry {
    sets: Vec<BlockSetDefinition>,
    woods: Vec<WoodDefinition>,
    header: [i32; 4],
    set_rows: Vec<i32>,
    wood_rows: Vec<i32>,
    strings: Vec<u8>,
}
impl Registry {
    fn build() -> Self {
        let sounds = sound::registry();
        let find = |key: &str| sounds.find_event(key).expect("declared family sound").id;
        let sets = sets::build(find);
        let woods = woods::build(find);
        let mut result = Self { sets, woods, header: [0; 4], set_rows: Vec::new(), wood_rows: Vec::new(), strings: Vec::new() };
        for set in &result.sets {
            result.set_rows.extend([result.strings.len() as i32, set.name.len() as i32,
                i32::from(set.can_open_by_hand), i32::from(set.can_open_by_wind_charge),
                i32::from(set.arrows_activate_button), set.pressure_sensitivity as i32, set.sound as i32]);
            result.set_rows.extend(set.events.map(|event| i32::from(event.0)));
            result.strings.extend_from_slice(set.name.as_bytes());
        }
        for wood in &result.woods {
            result.wood_rows.extend([result.strings.len() as i32, wood.name.len() as i32,
                wood.set as i32, wood.sound as i32, wood.hanging_sign_sound as i32,
                i32::from(wood.gate_events[0].0), i32::from(wood.gate_events[1].0)]);
            result.strings.extend_from_slice(wood.name.as_bytes());
        }
        result.header = [1, result.sets.len() as i32, result.woods.len() as i32, result.strings.len() as i32];
        result
    }
    pub fn sets(&self) -> &[BlockSetDefinition] { &self.sets }
    pub fn woods(&self) -> &[WoodDefinition] { &self.woods }
}

pub fn registry() -> &'static Registry {
    static REGISTRY: OnceLock<Registry> = OnceLock::new();
    REGISTRY.get_or_init(Registry::build)
}

fn block_rows() -> &'static [i32] {
    static ROWS: OnceLock<Vec<i32>> = OnceLock::new();
    ROWS.get_or_init(|| super::definitions::registry().definitions().iter()
        .flat_map(|block| block.family.words()).collect())
}

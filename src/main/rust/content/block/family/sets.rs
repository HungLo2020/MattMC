//! Block-set definitions shared by doors, trapdoors, buttons and plates.
use super::{BlockSetDefinition, PressureSensitivity};
use crate::content::sound::{EventId, SoundType};
macro_rules! sets {
    ($($id:ident => ($name:literal, $hand:expr, $wind:expr, $arrows:expr, $sensitivity:ident, $sound:ident, $events:expr)),+ $(,)?) => {
        #[derive(Clone, Copy, Debug, PartialEq, Eq)]
        #[repr(u8)]
        pub enum BlockSet { $($id),+ }
        impl BlockSet {
            pub fn definition(self) -> &'static BlockSetDefinition { &super::registry().sets[self as usize] }
        }
        pub(super) fn build(find: impl Fn(&str) -> EventId) -> Vec<BlockSetDefinition> {
            vec![$(BlockSetDefinition { id: BlockSet::$id, name: $name,
                can_open_by_hand: $hand, can_open_by_wind_charge: $wind,
                arrows_activate_button: $arrows, pressure_sensitivity: PressureSensitivity::$sensitivity,
                sound: SoundType::$sound, events: $events.map(|key| find(key)) }),+]
        }
    };
}
sets! {
    Iron => ("iron", false, false, false, Everything, Iron,
        ["minecraft:block.iron_door.close", "minecraft:block.iron_door.open", "minecraft:block.iron_trapdoor.close", "minecraft:block.iron_trapdoor.open", "minecraft:block.metal_pressure_plate.click_off", "minecraft:block.metal_pressure_plate.click_on", "minecraft:block.stone_button.click_off", "minecraft:block.stone_button.click_on"]),
    Copper => ("copper", true, true, false, Everything, Copper,
        ["minecraft:block.copper_door.close", "minecraft:block.copper_door.open", "minecraft:block.copper_trapdoor.close", "minecraft:block.copper_trapdoor.open", "minecraft:block.metal_pressure_plate.click_off", "minecraft:block.metal_pressure_plate.click_on", "minecraft:block.stone_button.click_off", "minecraft:block.stone_button.click_on"]),
    Gold => ("gold", false, true, false, Everything, Metal,
        ["minecraft:block.iron_door.close", "minecraft:block.iron_door.open", "minecraft:block.iron_trapdoor.close", "minecraft:block.iron_trapdoor.open", "minecraft:block.metal_pressure_plate.click_off", "minecraft:block.metal_pressure_plate.click_on", "minecraft:block.stone_button.click_off", "minecraft:block.stone_button.click_on"]),
    Stone => ("stone", true, true, false, Mobs, Stone,
        ["minecraft:block.iron_door.close", "minecraft:block.iron_door.open", "minecraft:block.iron_trapdoor.close", "minecraft:block.iron_trapdoor.open", "minecraft:block.stone_pressure_plate.click_off", "minecraft:block.stone_pressure_plate.click_on", "minecraft:block.stone_button.click_off", "minecraft:block.stone_button.click_on"]),
    PolishedBlackstone => ("polished_blackstone", true, true, false, Mobs, Stone,
        ["minecraft:block.iron_door.close", "minecraft:block.iron_door.open", "minecraft:block.iron_trapdoor.close", "minecraft:block.iron_trapdoor.open", "minecraft:block.stone_pressure_plate.click_off", "minecraft:block.stone_pressure_plate.click_on", "minecraft:block.stone_button.click_off", "minecraft:block.stone_button.click_on"]),
    Oak => ("oak", true, true, true, Everything, Wood,
        ["minecraft:block.wooden_door.close", "minecraft:block.wooden_door.open", "minecraft:block.wooden_trapdoor.close", "minecraft:block.wooden_trapdoor.open", "minecraft:block.wooden_pressure_plate.click_off", "minecraft:block.wooden_pressure_plate.click_on", "minecraft:block.wooden_button.click_off", "minecraft:block.wooden_button.click_on"]),
    Spruce => ("spruce", true, true, true, Everything, Wood,
        ["minecraft:block.wooden_door.close", "minecraft:block.wooden_door.open", "minecraft:block.wooden_trapdoor.close", "minecraft:block.wooden_trapdoor.open", "minecraft:block.wooden_pressure_plate.click_off", "minecraft:block.wooden_pressure_plate.click_on", "minecraft:block.wooden_button.click_off", "minecraft:block.wooden_button.click_on"]),
    Birch => ("birch", true, true, true, Everything, Wood,
        ["minecraft:block.wooden_door.close", "minecraft:block.wooden_door.open", "minecraft:block.wooden_trapdoor.close", "minecraft:block.wooden_trapdoor.open", "minecraft:block.wooden_pressure_plate.click_off", "minecraft:block.wooden_pressure_plate.click_on", "minecraft:block.wooden_button.click_off", "minecraft:block.wooden_button.click_on"]),
    Acacia => ("acacia", true, true, true, Everything, Wood,
        ["minecraft:block.wooden_door.close", "minecraft:block.wooden_door.open", "minecraft:block.wooden_trapdoor.close", "minecraft:block.wooden_trapdoor.open", "minecraft:block.wooden_pressure_plate.click_off", "minecraft:block.wooden_pressure_plate.click_on", "minecraft:block.wooden_button.click_off", "minecraft:block.wooden_button.click_on"]),
    Cherry => ("cherry", true, true, true, Everything, CherryWood,
        ["minecraft:block.cherry_wood_door.close", "minecraft:block.cherry_wood_door.open", "minecraft:block.cherry_wood_trapdoor.close", "minecraft:block.cherry_wood_trapdoor.open", "minecraft:block.cherry_wood_pressure_plate.click_off", "minecraft:block.cherry_wood_pressure_plate.click_on", "minecraft:block.cherry_wood_button.click_off", "minecraft:block.cherry_wood_button.click_on"]),
    Jungle => ("jungle", true, true, true, Everything, Wood,
        ["minecraft:block.wooden_door.close", "minecraft:block.wooden_door.open", "minecraft:block.wooden_trapdoor.close", "minecraft:block.wooden_trapdoor.open", "minecraft:block.wooden_pressure_plate.click_off", "minecraft:block.wooden_pressure_plate.click_on", "minecraft:block.wooden_button.click_off", "minecraft:block.wooden_button.click_on"]),
    DarkOak => ("dark_oak", true, true, true, Everything, Wood,
        ["minecraft:block.wooden_door.close", "minecraft:block.wooden_door.open", "minecraft:block.wooden_trapdoor.close", "minecraft:block.wooden_trapdoor.open", "minecraft:block.wooden_pressure_plate.click_off", "minecraft:block.wooden_pressure_plate.click_on", "minecraft:block.wooden_button.click_off", "minecraft:block.wooden_button.click_on"]),
    PaleOak => ("pale_oak", true, true, true, Everything, Wood,
        ["minecraft:block.wooden_door.close", "minecraft:block.wooden_door.open", "minecraft:block.wooden_trapdoor.close", "minecraft:block.wooden_trapdoor.open", "minecraft:block.wooden_pressure_plate.click_off", "minecraft:block.wooden_pressure_plate.click_on", "minecraft:block.wooden_button.click_off", "minecraft:block.wooden_button.click_on"]),
    Crimson => ("crimson", true, true, true, Everything, NetherWood,
        ["minecraft:block.nether_wood_door.close", "minecraft:block.nether_wood_door.open", "minecraft:block.nether_wood_trapdoor.close", "minecraft:block.nether_wood_trapdoor.open", "minecraft:block.nether_wood_pressure_plate.click_off", "minecraft:block.nether_wood_pressure_plate.click_on", "minecraft:block.nether_wood_button.click_off", "minecraft:block.nether_wood_button.click_on"]),
    Warped => ("warped", true, true, true, Everything, NetherWood,
        ["minecraft:block.nether_wood_door.close", "minecraft:block.nether_wood_door.open", "minecraft:block.nether_wood_trapdoor.close", "minecraft:block.nether_wood_trapdoor.open", "minecraft:block.nether_wood_pressure_plate.click_off", "minecraft:block.nether_wood_pressure_plate.click_on", "minecraft:block.nether_wood_button.click_off", "minecraft:block.nether_wood_button.click_on"]),
    Mangrove => ("mangrove", true, true, true, Everything, Wood,
        ["minecraft:block.wooden_door.close", "minecraft:block.wooden_door.open", "minecraft:block.wooden_trapdoor.close", "minecraft:block.wooden_trapdoor.open", "minecraft:block.wooden_pressure_plate.click_off", "minecraft:block.wooden_pressure_plate.click_on", "minecraft:block.wooden_button.click_off", "minecraft:block.wooden_button.click_on"]),
    Bamboo => ("bamboo", true, true, true, Everything, BambooWood,
        ["minecraft:block.bamboo_wood_door.close", "minecraft:block.bamboo_wood_door.open", "minecraft:block.bamboo_wood_trapdoor.close", "minecraft:block.bamboo_wood_trapdoor.open", "minecraft:block.bamboo_wood_pressure_plate.click_off", "minecraft:block.bamboo_wood_pressure_plate.click_on", "minecraft:block.bamboo_wood_button.click_off", "minecraft:block.bamboo_wood_button.click_on"]),
}

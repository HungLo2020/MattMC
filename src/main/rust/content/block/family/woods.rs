//! Wood definitions shared by gates and all sign attachment variants.
use super::{WoodDefinition, BlockSet};
use crate::content::sound::{EventId, SoundType};
macro_rules! woods {
    ($($id:ident => ($name:literal, $set:ident, $sound:ident, $hanging:ident, $events:expr)),+ $(,)?) => {
        #[derive(Clone, Copy, Debug, PartialEq, Eq)]
        #[repr(u8)]
        pub enum Wood { $($id),+ }
        impl Wood {
            pub fn definition(self) -> &'static WoodDefinition { &super::registry().woods[self as usize] }
        }
        pub(super) fn build(find: impl Fn(&str) -> EventId) -> Vec<WoodDefinition> {
            vec![$(WoodDefinition { id: Wood::$id, name: $name, set: BlockSet::$set,
                sound: SoundType::$sound, hanging_sign_sound: SoundType::$hanging,
                gate_events: $events.map(|key| find(key)) }),+]
        }
    };
}
woods! {
    Oak => ("oak", Oak, Wood, HangingSign,
        ["minecraft:block.fence_gate.close", "minecraft:block.fence_gate.open"]),
    Spruce => ("spruce", Spruce, Wood, HangingSign,
        ["minecraft:block.fence_gate.close", "minecraft:block.fence_gate.open"]),
    Birch => ("birch", Birch, Wood, HangingSign,
        ["minecraft:block.fence_gate.close", "minecraft:block.fence_gate.open"]),
    Acacia => ("acacia", Acacia, Wood, HangingSign,
        ["minecraft:block.fence_gate.close", "minecraft:block.fence_gate.open"]),
    Cherry => ("cherry", Cherry, CherryWood, CherryWoodHangingSign,
        ["minecraft:block.cherry_wood_fence_gate.close", "minecraft:block.cherry_wood_fence_gate.open"]),
    Jungle => ("jungle", Jungle, Wood, HangingSign,
        ["minecraft:block.fence_gate.close", "minecraft:block.fence_gate.open"]),
    DarkOak => ("dark_oak", DarkOak, Wood, HangingSign,
        ["minecraft:block.fence_gate.close", "minecraft:block.fence_gate.open"]),
    PaleOak => ("pale_oak", PaleOak, Wood, HangingSign,
        ["minecraft:block.fence_gate.close", "minecraft:block.fence_gate.open"]),
    Crimson => ("crimson", Crimson, NetherWood, NetherWoodHangingSign,
        ["minecraft:block.nether_wood_fence_gate.close", "minecraft:block.nether_wood_fence_gate.open"]),
    Warped => ("warped", Warped, NetherWood, NetherWoodHangingSign,
        ["minecraft:block.nether_wood_fence_gate.close", "minecraft:block.nether_wood_fence_gate.open"]),
    Mangrove => ("mangrove", Mangrove, Wood, HangingSign,
        ["minecraft:block.fence_gate.close", "minecraft:block.fence_gate.open"]),
    Bamboo => ("bamboo", Bamboo, BambooWood, BambooWoodHangingSign,
        ["minecraft:block.bamboo_wood_fence_gate.close", "minecraft:block.bamboo_wood_fence_gate.open"]),
}

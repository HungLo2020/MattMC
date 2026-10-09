//! Note instrument names, event bindings and interaction categories.
use super::{InstrumentDefinition, InstrumentKind, EventId};
macro_rules! instruments {
    ($($variant:ident => ($name:literal, $event:literal, $kind:ident)),+ $(,)?) => {
        #[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
        #[repr(u8)]
        pub enum Instrument { $($variant),+ }
        pub(crate) const NAMES: &[&str] = &[$($name),+];
        impl Instrument {
            pub fn definition(self) -> &'static InstrumentDefinition { &super::registry().instruments[self as usize] }
        }
        pub(super) fn build(find: impl Fn(&str) -> EventId) -> Vec<InstrumentDefinition> {
            vec![$(InstrumentDefinition { id: Instrument::$variant, name: $name,
                event: find($event), kind: InstrumentKind::$kind }),+]
        }
    };
}
instruments! {
    Harp => ("harp", "minecraft:block.note_block.harp", Base),
    Basedrum => ("basedrum", "minecraft:block.note_block.basedrum", Base),
    Snare => ("snare", "minecraft:block.note_block.snare", Base),
    Hat => ("hat", "minecraft:block.note_block.hat", Base),
    Bass => ("bass", "minecraft:block.note_block.bass", Base),
    Flute => ("flute", "minecraft:block.note_block.flute", Base),
    Bell => ("bell", "minecraft:block.note_block.bell", Base),
    Guitar => ("guitar", "minecraft:block.note_block.guitar", Base),
    Chime => ("chime", "minecraft:block.note_block.chime", Base),
    Xylophone => ("xylophone", "minecraft:block.note_block.xylophone", Base),
    IronXylophone => ("iron_xylophone", "minecraft:block.note_block.iron_xylophone", Base),
    CowBell => ("cow_bell", "minecraft:block.note_block.cow_bell", Base),
    Didgeridoo => ("didgeridoo", "minecraft:block.note_block.didgeridoo", Base),
    Bit => ("bit", "minecraft:block.note_block.bit", Base),
    Banjo => ("banjo", "minecraft:block.note_block.banjo", Base),
    Pling => ("pling", "minecraft:block.note_block.pling", Base),
    Zombie => ("zombie", "minecraft:block.note_block.imitate.zombie", Head),
    Skeleton => ("skeleton", "minecraft:block.note_block.imitate.skeleton", Head),
    Creeper => ("creeper", "minecraft:block.note_block.imitate.creeper", Head),
    Dragon => ("dragon", "minecraft:block.note_block.imitate.ender_dragon", Head),
    WitherSkeleton => ("wither_skeleton", "minecraft:block.note_block.imitate.wither_skeleton", Head),
    Piglin => ("piglin", "minecraft:block.note_block.imitate.piglin", Head),
    CustomHead => ("custom_head", "minecraft:ui.button.click", Custom),
}

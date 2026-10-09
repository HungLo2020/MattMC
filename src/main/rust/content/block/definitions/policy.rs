//! State-only tick and occlusion policy. Evaluate once into shared native columns;
//! scheduling, world-dependent predicates and shape production are separate owners.
#[cfg(test)]
mod tests;
use super::physics::PhysicalFlags;
use crate::content::{fluid::{self, Family, FluidStateId}, property::{Builtin, Domain}};

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
#[repr(transparent)]
pub struct StatePolicy(u8);
impl StatePolicy {
    pub const fn bits(self) -> u8 { self.0 }
    pub const fn randomly_ticking(self) -> bool { self.0 & 1 != 0 }
    pub const fn uses_light_shape(self) -> bool { self.0 & 2 != 0 }
    pub const fn leaves(self) -> bool { self.0 & 4 != 0 }
    pub const fn block_entity(self) -> bool { self.0 & 8 != 0 }
}

#[derive(Clone, Copy)]
enum TickRule {
    Physical,
    Fluid,
    Constant(bool),
    WhenTrue(Builtin),
    AgeBelow { property: Builtin, maximum: u16 },
    IntegerEquals { property: Builtin, value: u16 },
    DecayingLeaves,
    LowerCrop { age: Builtin, maximum: u16 },
}
#[derive(Clone, Copy)]
enum LightShapeRule { Constant(bool), Slab, WhenTrue(Builtin) }

pub struct Rules {
    ticks: TickRule,
    light_shape: LightShapeRule,
    leaves: bool,
    block_entity: bool,
}

struct Values<'a> { properties: &'a [Builtin], indices: &'a [i32] }
impl Values<'_> {
    fn index(&self, property: Builtin) -> u16 {
        let slot = self.properties.iter().position(|&p| p == property).expect("policy property missing");
        u16::try_from(self.indices[slot]).expect("policy property index")
    }
    fn boolean(&self, property: Builtin) -> bool {
        property.domain().boolean(self.index(property)).expect("policy boolean domain")
    }
    fn integer(&self, property: Builtin) -> u16 {
        property.domain().integer(self.index(property)).expect("policy integer domain")
    }
    fn variant(&self, property: Builtin) -> &'static str {
        let Domain::Enum(values) = property.domain() else { panic!("policy enum domain"); };
        values[self.index(property) as usize]
    }
}

impl Rules {
    pub(super) fn evaluate(&self, properties: &[Builtin], indices: &[i32], physical: PhysicalFlags, fluid: FluidStateId) -> StatePolicy {
        let s = Values { properties, indices };
        let ticks = match self.ticks {
            TickRule::Physical => physical.contains(PhysicalFlags::RANDOM_TICKS),
            TickRule::Fluid => {
                let fluids = fluid::registry();
                let state = fluids.state(fluid).expect("policy canonical fluid state");
                fluids.definition(state.fluid).expect("policy canonical fluid definition").family == Family::Lava
            },
            TickRule::Constant(value) => value,
            TickRule::WhenTrue(property) => s.boolean(property),
            TickRule::AgeBelow { property, maximum } => s.integer(property) < maximum,
            TickRule::IntegerEquals { property, value } => s.integer(property) == value,
            TickRule::DecayingLeaves => s.integer(Builtin::Distance) == 7 && !s.boolean(Builtin::Persistent),
            TickRule::LowerCrop { age, maximum } => s.variant(Builtin::DoubleBlockHalf) == "lower" && s.integer(age) < maximum,
        };
        let light_shape = match self.light_shape {
            LightShapeRule::Constant(value) => value,
            LightShapeRule::Slab => s.variant(Builtin::SlabType) != "double",
            LightShapeRule::WhenTrue(property) => s.boolean(property),
        };
        StatePolicy(ticks as u8 | ((light_shape as u8) << 1) | ((self.leaves as u8) << 2) | ((self.block_entity as u8) << 3))
    }
}

macro_rules! profiles {
    ($($name:ident => $value:expr),+ $(,)?) => {
        #[derive(Clone, Copy)]
        #[repr(u8)]
        pub(super) enum PolicySet { $($name),+ }
        pub(super) static PROFILES: &[Rules] = &[$($value),+];
    };
}
profiles! {
    Air => Rules { ticks: TickRule::Physical, light_shape: LightShapeRule::Constant(false), leaves: false, block_entity: false },
    LimestoneStairs => Rules { ticks: TickRule::Physical, light_shape: LightShapeRule::Constant(true), leaves: false, block_entity: false },
    LimestoneSlab => Rules { ticks: TickRule::Physical, light_shape: LightShapeRule::Slab, leaves: false, block_entity: false },
    Water => Rules { ticks: TickRule::Fluid, light_shape: LightShapeRule::Constant(false), leaves: false, block_entity: false },
    SuspiciousSand => Rules { ticks: TickRule::Physical, light_shape: LightShapeRule::Constant(false), leaves: false, block_entity: true },
    OakLeaves => Rules { ticks: TickRule::DecayingLeaves, light_shape: LightShapeRule::Constant(false), leaves: true, block_entity: false },
    StickyPiston => Rules { ticks: TickRule::Physical, light_shape: LightShapeRule::WhenTrue(Builtin::Extended), leaves: false, block_entity: false },
    AcaciaShelf => Rules { ticks: TickRule::Physical, light_shape: LightShapeRule::Constant(true), leaves: false, block_entity: true },
    Wheat => Rules { ticks: TickRule::AgeBelow { property: Builtin::Age7, maximum: 7 }, light_shape: LightShapeRule::Constant(false), leaves: false, block_entity: false },
    RedstoneOre => Rules { ticks: TickRule::WhenTrue(Builtin::Lit), light_shape: LightShapeRule::Constant(false), leaves: false, block_entity: false },
    CopperBars => Rules { ticks: TickRule::Constant(true), light_shape: LightShapeRule::Constant(false), leaves: false, block_entity: false },
    OxidizedCopperBars => Rules { ticks: TickRule::Constant(false), light_shape: LightShapeRule::Constant(false), leaves: false, block_entity: false },
    NetherWart => Rules { ticks: TickRule::AgeBelow { property: Builtin::Age3, maximum: 3 }, light_shape: LightShapeRule::Constant(false), leaves: false, block_entity: false },
    Cocoa => Rules { ticks: TickRule::AgeBelow { property: Builtin::Age2, maximum: 2 }, light_shape: LightShapeRule::Constant(false), leaves: false, block_entity: false },
    ChorusFlower => Rules { ticks: TickRule::AgeBelow { property: Builtin::Age5, maximum: 5 }, light_shape: LightShapeRule::Constant(false), leaves: false, block_entity: false },
    TorchflowerCrop => Rules { ticks: TickRule::AgeBelow { property: Builtin::Age1, maximum: 2 }, light_shape: LightShapeRule::Constant(false), leaves: false, block_entity: false },
    PitcherCrop => Rules { ticks: TickRule::LowerCrop { age: Builtin::Age4, maximum: 4 }, light_shape: LightShapeRule::Constant(false), leaves: false, block_entity: false },
    Kelp => Rules { ticks: TickRule::AgeBelow { property: Builtin::Age25, maximum: 25 }, light_shape: LightShapeRule::Constant(false), leaves: false, block_entity: false },
    Bamboo => Rules { ticks: TickRule::IntegerEquals { property: Builtin::Stage, value: 0 }, light_shape: LightShapeRule::Constant(false), leaves: false, block_entity: false },
    OxidizedCutCopperStairs => Rules { ticks: TickRule::Constant(false), light_shape: LightShapeRule::Constant(true), leaves: false, block_entity: false },
    WeatheredCutCopperStairs => Rules { ticks: TickRule::Constant(true), light_shape: LightShapeRule::Constant(true), leaves: false, block_entity: false },
    OxidizedCutCopperSlab => Rules { ticks: TickRule::Constant(false), light_shape: LightShapeRule::Slab, leaves: false, block_entity: false },
    WeatheredCutCopperSlab => Rules { ticks: TickRule::Constant(true), light_shape: LightShapeRule::Slab, leaves: false, block_entity: false },
    CopperChest => Rules { ticks: TickRule::Constant(true), light_shape: LightShapeRule::Constant(false), leaves: false, block_entity: true },
    OxidizedCopperChest => Rules { ticks: TickRule::Constant(false), light_shape: LightShapeRule::Constant(false), leaves: false, block_entity: true },
}

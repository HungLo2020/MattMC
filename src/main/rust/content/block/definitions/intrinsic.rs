//! State-only content rules, evaluated once into immutable native columns.
//! Map colors are semantic palette identities, not render materials or tints.
mod declarations;
pub use crate::content::map_color::MapColor;
pub(super) use declarations::{IntrinsicSet, PROFILES};

use crate::content::{fluid::{self, Family, FluidStateId}, property::{Builtin, Domain}};

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct StateTraits {
    pub map_color: MapColor,
    pub emission: u8,
    pub fluid: FluidStateId,
}
impl StateTraits {
    pub(super) fn packed(self) -> i32 {
        i32::from(self.map_color as u8) | (i32::from(self.emission) << 8) | (i32::from(self.fluid.0) << 12)
    }
}

#[derive(Clone, Copy)]
pub(super) enum ColorRule {
    Constant(MapColor),
    Log { bark: MapColor, end: MapColor },
    Bed { foot: MapColor },
    Wheat,
    Waterlogged,
}

#[derive(Clone, Copy)]
pub(super) enum EmissionRule {
    Constant(u8),
    WhenTrue(Builtin, u8),
    GlowLichen,
    Level,
    SeaPickles,
    Candles,
    RespawnAnchor,
    TrialSpawner,
    Vault,
}

#[derive(Clone, Copy)]
pub(super) enum FluidRule {
    Empty,
    Water,
    Waterlogged { falling: bool },
    Liquid(Family),
}

pub(super) struct Rules {
    pub color: ColorRule,
    pub emission: EmissionRule,
    pub fluid: FluidRule,
}

struct Values<'a> {
    properties: &'a [Builtin],
    indices: &'a [i32],
}
impl Values<'_> {
    fn index(&self, property: Builtin) -> u16 {
        let slot = self.properties.iter().position(|&p| p == property).expect("native intrinsic property missing");
        u16::try_from(self.indices[slot]).expect("native property index")
    }
    fn boolean(&self, property: Builtin) -> bool {
        property.domain().boolean(self.index(property)).expect("native boolean property")
    }
    fn integer(&self, property: Builtin) -> u16 {
        property.domain().integer(self.index(property)).expect("native integer property")
    }
    fn variant(&self, property: Builtin) -> &'static str {
        let Domain::Enum(values) = property.domain() else { panic!("native enum property"); };
        values[self.index(property) as usize]
    }
}

impl Rules {
    pub(super) fn evaluate(&self, properties: &[Builtin], indices: &[i32]) -> StateTraits {
        let s = Values { properties, indices };
        let map_color = match self.color {
            ColorRule::Constant(color) => color,
            ColorRule::Log { bark, end } => if s.variant(Builtin::Axis) == "y" { end } else { bark },
            ColorRule::Bed { foot } => if s.variant(Builtin::BedPart) == "foot" { foot } else { MapColor::Wool },
            ColorRule::Wheat => if s.integer(Builtin::Age7) >= 6 { MapColor::ColorYellow } else { MapColor::Plant },
            ColorRule::Waterlogged => if s.boolean(Builtin::Waterlogged) { MapColor::Water } else { MapColor::None },
        };
        let emission = match self.emission {
            EmissionRule::Constant(level) => level,
            EmissionRule::WhenTrue(property, level) => if s.boolean(property) { level } else { 0 },
            EmissionRule::GlowLichen => if [Builtin::Down, Builtin::East, Builtin::North, Builtin::South, Builtin::Up, Builtin::West]
                .into_iter().any(|p| s.boolean(p)) { 7 } else { 0 },
            EmissionRule::Level => s.integer(Builtin::Level) as u8,
            EmissionRule::SeaPickles => if s.boolean(Builtin::Waterlogged) { 3 + 3 * s.integer(Builtin::Pickles) as u8 } else { 0 },
            EmissionRule::Candles => if s.boolean(Builtin::Lit) { 3 * s.integer(Builtin::Candles) as u8 } else { 0 },
            EmissionRule::RespawnAnchor => (s.integer(Builtin::RespawnAnchorCharges) * 15 / 4) as u8,
            EmissionRule::TrialSpawner => match s.variant(Builtin::TrialSpawnerState) {
                "inactive" | "cooldown" => 0,
                "waiting_for_players" => 4,
                "active" | "waiting_for_reward_ejection" | "ejecting_reward" => 8,
                _ => panic!("unknown native trial spawner state"),
            },
            EmissionRule::Vault => match s.variant(Builtin::VaultState) {
                "inactive" => 6,
                "active" | "unlocking" | "ejecting" => 12,
                _ => panic!("unknown native vault state"),
            },
        };
        assert!(emission <= 15, "native light emission range");
        let fluids = fluid::registry();
        let (family, source, falling, amount) = match self.fluid {
            FluidRule::Empty => (Family::Empty, false, false, 0),
            FluidRule::Water => (Family::Water, true, false, 8),
            FluidRule::Waterlogged { falling } => if s.boolean(Builtin::Waterlogged) {
                (Family::Water, true, falling, 8)
            } else { (Family::Empty, false, false, 0) },
            FluidRule::Liquid(family) => match s.integer(Builtin::Level) {
                0 => (family, true, false, 8),
                level @ 1..=7 => (family, false, false, 8 - level as u8),
                8..=15 => (family, false, true, 8),
                _ => panic!("native liquid level range"),
            },
        };
        let fluid = fluids.state_by_traits(family, source, falling, amount).expect("canonical native fluid state");
        StateTraits { map_color, emission, fluid }
    }
}

impl Rules {
    // Dependencies are also used to project immutable CPU lookup tables for
    // legacy Properties copies. Registered states read native columns directly.
    pub(super) fn dependencies(&self, emission: bool) -> Vec<Builtin> {
        if emission {
            match self.emission {
                EmissionRule::Constant(_) => vec![],
                EmissionRule::WhenTrue(property, _) => vec![property],
                EmissionRule::GlowLichen => vec![Builtin::Down, Builtin::East, Builtin::North, Builtin::South, Builtin::Up, Builtin::West],
                EmissionRule::Level => vec![Builtin::Level],
                EmissionRule::SeaPickles => vec![Builtin::Pickles, Builtin::Waterlogged],
                EmissionRule::Candles => vec![Builtin::Candles, Builtin::Lit],
                EmissionRule::RespawnAnchor => vec![Builtin::RespawnAnchorCharges],
                EmissionRule::TrialSpawner => vec![Builtin::TrialSpawnerState],
                EmissionRule::Vault => vec![Builtin::VaultState],
            }
        } else {
            match self.color {
                ColorRule::Constant(_) => vec![],
                ColorRule::Log { .. } => vec![Builtin::Axis],
                ColorRule::Bed { .. } => vec![Builtin::BedPart],
                ColorRule::Wheat => vec![Builtin::Age7],
                ColorRule::Waterlogged => vec![Builtin::Waterlogged],
            }
        }
    }
}

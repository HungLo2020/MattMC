//! Built-in fluid definitions and intrinsic state facts, owned by Rust.
//! World-dependent flow, ticking and replacement still use Java compatibility
//! adapters. Registry order and state IDs are part of the save/network contract.
pub(crate) mod ffi;

use crate::content::state::StateLayout;
use std::sync::OnceLock;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct FluidId(pub u16);
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct FluidStateId(pub u16);

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
#[repr(i32)]
pub enum Family {
    Empty = 0,
    Water = 1,
    Lava = 2,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
#[repr(i32)]
pub enum Property {
    /// Ordered domain: true, false (not false, true).
    Falling = 1,
    /// Ordered domain: 1 through 8.
    Level = 2,
}

impl Property {
    fn count(self) -> u16 {
        match self {
            Self::Falling => 2,
            Self::Level => 8,
        }
    }
}

pub struct Definition {
    pub id: FluidId,
    pub name: &'static str,
    pub family: Family,
    pub source: bool,
    pub properties: &'static [Property],
    pub first_state: FluidStateId,
    pub default_local_state: u16,
    pub explosion_resistance: f32,
    layout: StateLayout,
}

impl Definition {
    pub fn state_count(&self) -> usize {
        self.layout.state_count()
    }
    pub fn default_state(&self) -> FluidStateId {
        FluidStateId(self.first_state.0 + self.default_local_state)
    }
}

#[derive(Clone, Copy, Debug, PartialEq)]
pub struct StateTraits {
    pub amount: u8,
    pub source: bool,
    pub own_height: f32,
    pub legacy_level: u8,
}

pub struct Registry {
    definitions: Vec<Definition>,
    states: Vec<StateTraits>,
    // Immutable CPU projections for the temporary Java views; lengths are in
    // elements. No per-frame calls, handles, or Java-supplied definitions.
    header: [i32; 7],
    definition_rows: Vec<i32>,
    properties: Vec<i32>,
    state_rows: Vec<i32>,
    names: Vec<u8>,
}

impl Registry {
    fn build() -> Self {
        use Family::*;
        use Property::*;
        const FLOWING: &[Property] = &[Falling, Level];
        const SOURCE: &[Property] = &[Falling];
        // Explicit order preserves the existing registry and contiguous IDs.
        let declarations = [
            ("minecraft:empty", Empty, false, &[][..]),
            ("minecraft:flowing_water", Water, false, FLOWING),
            ("minecraft:water", Water, true, SOURCE),
            ("minecraft:flowing_lava", Lava, false, FLOWING),
            ("minecraft:lava", Lava, true, SOURCE),
        ];
        let mut registry = Self {
            definitions: Vec::new(),
            states: Vec::new(),
            header: [0; 7],
            definition_rows: Vec::new(),
            properties: Vec::new(),
            state_rows: Vec::new(),
            names: Vec::new(),
        };
        for (name, family, source, properties) in declarations {
            let layout =
                StateLayout::new(&properties.iter().map(|p| p.count()).collect::<Vec<_>>())
                    .expect("bounded built-in fluid domains");
            let definition = Definition {
                id: FluidId(registry.definitions.len() as u16),
                name,
                family,
                source,
                properties,
                first_state: FluidStateId(registry.states.len() as u16),
                default_local_state: 0,
                explosion_resistance: if family == Empty { 0.0 } else { 100.0 },
                layout,
            };
            for state in 0..definition.state_count() {
                let falling = properties.first() == Some(&Falling)
                    && definition.layout.slots()[0].value(state as u16) == 0;
                let amount = if family == Empty {
                    0
                } else if source {
                    8
                } else {
                    definition.layout.slots()[1].value(state as u16) as u8 + 1
                };
                let traits = StateTraits {
                    amount,
                    source,
                    own_height: amount as f32 / 9.0,
                    legacy_level: if source || family == Empty {
                        0
                    } else {
                        8 - amount + if falling { 8 } else { 0 }
                    },
                };
                registry.state_rows.extend([
                    amount as i32,
                    i32::from(source),
                    traits.own_height.to_bits() as i32,
                    traits.legacy_level as i32,
                ]);
                registry.states.push(traits);
            }
            registry.definition_rows.extend([
                registry.names.len() as i32,
                name.len() as i32,
                family as i32,
                i32::from(source),
                registry.properties.len() as i32,
                properties.len() as i32,
                definition.first_state.0 as i32,
                definition.state_count() as i32,
                definition.default_local_state as i32,
                definition.explosion_resistance.to_bits() as i32,
            ]);
            registry.names.extend_from_slice(name.as_bytes());
            registry
                .properties
                .extend(properties.iter().map(|&p| p as i32));
            registry.definitions.push(definition);
        }
        registry.header = [
            1,
            registry.definitions.len() as i32,
            registry.states.len() as i32,
            10,
            4,
            registry.properties.len() as i32,
            registry.names.len() as i32,
        ];
        registry
    }

    pub fn definitions(&self) -> &[Definition] {
        &self.definitions
    }
    pub fn definition(&self, id: FluidId) -> Option<&Definition> {
        self.definitions.get(id.0 as usize)
    }
    pub fn state(&self, id: FluidStateId) -> Option<&StateTraits> {
        self.states.get(id.0 as usize)
    }
}

pub fn registry() -> &'static Registry {
    static REGISTRY: OnceLock<Registry> = OnceLock::new();
    REGISTRY.get_or_init(Registry::build)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ordered_ids_defaults_and_all_traits_match_reference_domains() {
        let registry = registry();
        let expected = [
            ("minecraft:empty", 0, 1),
            ("minecraft:flowing_water", 1, 16),
            ("minecraft:water", 17, 2),
            ("minecraft:flowing_lava", 19, 16),
            ("minecraft:lava", 35, 2),
        ];
        for (id, (name, first, count)) in expected.into_iter().enumerate() {
            let d = registry.definition(FluidId(id as u16)).unwrap();
            assert_eq!(
                (d.name, d.first_state.0, d.state_count()),
                (name, first, count)
            );
            assert_eq!(d.default_state(), FluidStateId(first));
        }
        // Independent nested domains, including true-first defaults.
        let mut expected_states = vec![(0, false, 0)];
        for _ in 0..2 {
            for falling in [true, false] {
                for amount in 1..=8 {
                    expected_states.push((amount, false, 8 - amount + if falling { 8 } else { 0 }));
                }
            }
            expected_states.extend([(8, true, 0), (8, true, 0)]);
        }
        assert_eq!(registry.states.len(), 37);
        for (id, (amount, source, legacy_level)) in expected_states.into_iter().enumerate() {
            assert_eq!(
                *registry.state(FluidStateId(id as u16)).unwrap(),
                StateTraits {
                    amount,
                    source,
                    own_height: amount as f32 / 9.0,
                    legacy_level,
                }
            );
        }
        assert!(registry.definition(FluidId(5)).is_none());
        assert!(registry.state(FluidStateId(37)).is_none());
    }

    #[test]
    fn ffi_projection_is_bounded_and_matches_typed_definitions() {
        let r = registry();
        assert_eq!(r.header, [1, 5, 37, 10, 4, 6, 89]);
        for d in r.definitions() {
            let row = &r.definition_rows[d.id.0 as usize * 10..][..10];
            assert_eq!(
                &r.names[row[0] as usize..][..row[1] as usize],
                d.name.as_bytes()
            );
            assert_eq!(row[6], d.first_state.0 as i32);
            assert_eq!(row[9] as u32, d.explosion_resistance.to_bits());
        }
        let mut len = -1;
        unsafe {
            assert!(!ffi::mattmc_fluid_definitions_buffer(3, &mut len).is_null());
            assert_eq!(len, 37 * 4);
            assert!(ffi::mattmc_fluid_definitions_buffer(5, &mut len).is_null());
            assert_eq!(len, 0);
            assert!(ffi::mattmc_fluid_definitions_buffer(0, std::ptr::null_mut()).is_null());
        }
    }
}

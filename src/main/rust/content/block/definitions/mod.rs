//! Native block registration identities, state domains and defaults.
//! Behavior, shapes and remaining intrinsic facts are separate migration work.
mod catalog;
mod ffi;
mod templates;
pub mod physics;
pub mod intrinsic;
pub mod material;
#[cfg(test)]
mod tests;

use super::{BlockId, StateId};
use crate::content::{property::Builtin, state::{StateGraph, StateLayout}};
use std::{collections::HashMap, sync::OnceLock};

pub struct Template {
    pub properties: Vec<Builtin>,
    pub default_local: u16,
    pub graph: u16,
}

pub struct Definition {
    pub id: BlockId,
    pub name: &'static str,
    pub first_state: StateId,
    pub template: u16,
    pub physics: &'static physics::Physics,
    pub material: &'static material::Material,
}

pub struct Registry {
    definitions: Vec<Definition>,
    templates: Vec<Template>,
    graphs: Vec<StateGraph>,
    by_name: HashMap<&'static str, BlockId>,
    header: [i32; 15],
    rows: Vec<i32>,
    template_rows: Vec<i32>,
    properties: Vec<i32>,
    graph_headers: Vec<[i32; 5]>,
    names: Vec<u8>,
    physics_rows: Vec<i32>,
    material_rows: Vec<i32>,
    state_sounds: Vec<crate::content::sound::SoundType>,
    offset_rows: Vec<i32>,
    offset_values: Vec<f64>,
    intrinsic_states: Vec<intrinsic::StateTraits>,
    intrinsic_rows: Vec<i32>,
    rule_refs: Vec<i32>,
    rule_rows: Vec<i32>,
    rule_properties: Vec<i32>,
    rule_values: Vec<i32>,
}

impl Registry {
    fn build() -> Self {
        let mut r = Self {
            definitions: Vec::new(), templates: Vec::new(), graphs: Vec::new(), by_name: HashMap::new(),
            header: [0; 15], rows: Vec::new(), template_rows: Vec::new(), properties: Vec::new(),
            graph_headers: Vec::new(), names: Vec::new(),
            physics_rows: physics::PROFILES.iter().flat_map(physics::Physics::words).collect(),
            material_rows: material::PROFILES.iter().flat_map(|m| [m.base_sound as i32,m.instrument as i32,m.offset as i32]).collect(),
            state_sounds: Vec::new(),offset_rows: Vec::new(),offset_values: Vec::new(),
            intrinsic_states: Vec::new(), intrinsic_rows: Vec::new(),
            rule_refs: Vec::new(), rule_rows: Vec::new(), rule_properties: Vec::new(), rule_values: Vec::new(),
        };
        for config in super::offset::PROFILES {
            r.offset_rows.extend([config.kind as i32,config.horizontal.to_bits() as i32,config.vertical.to_bits() as i32,r.offset_values.len() as i32,config.table_len() as i32]);
            r.offset_values.extend(config.table());
        }
        // Pure index/transition graphs depend only on ordered cardinalities.
        // The finite declaration table bounds this pool; there is no runtime
        // cache accepting arbitrary user-supplied keys.
        let mut graphs: HashMap<Vec<u16>, u16> = HashMap::new();
        for declarations in templates::STATE_SETS {
            let properties: Vec<_> = declarations.iter().map(|&(property, _)| property).collect();
            assert!(properties.windows(2).all(|pair| pair[0].definition().schema.name() < pair[1].definition().schema.name()));
            let counts: Vec<_> = properties.iter().map(|p| p.domain().count()).collect();
            let layout = StateLayout::new(&counts).expect("bounded native block domains");
            let mut default_local = 0;
            for ((property, value), slot) in declarations.iter().zip(layout.slots()) {
                let index = property.definition().schema.values().iter().position(|s| s == value).expect("valid native default value");
                default_local += index as u16 * slot.stride;
            }
            let graph = *graphs.entry(counts.clone()).or_insert_with(|| {
                let graph = StateGraph::new(&counts).expect("bounded native block graph");
                let id = u16::try_from(r.graphs.len()).expect("bounded graph identities");
                r.graph_headers.push(graph.header());
                r.graphs.push(graph);
                id
            });
            r.template_rows.extend([r.properties.len() as i32, properties.len() as i32, default_local as i32, graph as i32]);
            r.properties.extend(properties.iter().map(|&p| p as i32));
            r.templates.push(Template { properties, default_local, graph });
        }
        let mut next_state = 0;
        for &(name, template, physical, intrinsic, material) in catalog::BLOCKS {
            let id = BlockId(u16::try_from(r.definitions.len()).expect("bounded native block IDs"));
            let t = &r.templates[template as usize];
            let states = r.graph_headers[t.graph as usize][0] as usize;
            assert!(next_state + states <= u16::MAX as usize, "native block state ceiling");
            assert!(r.by_name.insert(name, id).is_none(), "duplicate native block name");
            r.rows.extend([r.names.len() as i32, name.len() as i32, next_state as i32, template as i32, physical as i32, material as i32]);
            r.names.extend_from_slice(name.as_bytes());
            r.definitions.push(Definition { id, name, first_state: StateId(next_state as u16), template: template as u16, physics: &physics::PROFILES[physical as usize], material: &material::PROFILES[material as usize] });
            let values = r.graphs[t.graph as usize].buffer(0).expect("native graph values");
            let rules = &intrinsic::PROFILES[intrinsic as usize];
            for local in 0..states {
                let width = t.properties.len();
                let facts = rules.evaluate(&t.properties, &values[local * width..(local + 1) * width]);
                r.state_sounds.push(material::PROFILES[material as usize].sound(&t.properties,&values[local * width..(local + 1) * width]));
                r.intrinsic_rows.push(facts.packed());
                r.intrinsic_states.push(facts);
            }
            next_state += states;
        }
        // A finite projection of native rules supports temporary Java property
        // function copies without duplicating declarations or making downcalls.
        let mut rule_ids = HashMap::<(Vec<u16>, Vec<i32>), u16>::new();
        for d in &r.definitions {
            let t = &r.templates[d.template as usize];
            let rules = &intrinsic::PROFILES[catalog::BLOCKS[d.id.0 as usize].3 as usize];
            let graph = &r.graphs[t.graph as usize];
            let states = r.graph_headers[t.graph as usize][0] as usize;
            let indices = graph.buffer(0).expect("native graph values");
            for emission in [false, true] {
                let dependencies = rules.dependencies(emission);
                let positions: Vec<_> = dependencies.iter().map(|p| t.properties.iter().position(|q| p == q).expect("rule dependency")).collect();
                let layout = StateLayout::new(&dependencies.iter().map(|p| p.domain().count()).collect::<Vec<_>>()).expect("rule projection size");
                let mut values = vec![-1; layout.state_count()];
                for local in 0..states {
                    let row = &indices[local * t.properties.len()..(local + 1) * t.properties.len()];
                    let at: usize = positions.iter().zip(layout.slots()).map(|(&p, slot)| row[p] as usize * slot.stride as usize).sum();
                    let state = r.intrinsic_states[d.first_state.0 as usize + local];
                    let value = if emission { state.emission as i32 } else { state.map_color as i32 };
                    assert!(values[at] == -1 || values[at] == value, "incomplete native rule dependencies");
                    values[at] = value;
                }
                assert!(values.iter().all(|&v| v >= 0), "incomplete native rule projection");
                let key = (dependencies.iter().map(|&p| p as u16).collect(), values);
                let id = *rule_ids.entry(key.clone()).or_insert_with(|| {
                    let id = u16::try_from(r.rule_rows.len() / 4).expect("bounded rule identity");
                    r.rule_rows.extend([r.rule_properties.len() as i32, key.0.len() as i32, r.rule_values.len() as i32, key.1.len() as i32]);
                    r.rule_properties.extend(key.0.iter().map(|&p| p as i32));
                    r.rule_values.extend(&key.1);
                    id
                });
                r.rule_refs.push(id as i32);
            }
        }
        r.header = [4, r.definitions.len() as i32, next_state as i32, r.templates.len() as i32,
            r.properties.len() as i32, r.names.len() as i32, r.graphs.len() as i32, physics::PROFILES.len() as i32,
            (r.rule_rows.len() / 4) as i32, r.rule_properties.len() as i32, r.rule_values.len() as i32,
            crate::content::fluid::registry().definitions().iter().map(|d| d.state_count()).sum::<usize>() as i32,material::PROFILES.len() as i32,super::offset::PROFILES.len() as i32,r.offset_values.len() as i32];
        r
    }

    pub fn state_sound(&self, id: StateId) -> Option<&crate::content::sound::SoundTypeDefinition> {
        self.state_sounds.get(id.0 as usize).map(|&sound| sound.definition())
    }
    pub fn state_traits(&self, id: StateId) -> Option<&intrinsic::StateTraits> { self.intrinsic_states.get(id.0 as usize) }
    pub fn definitions(&self) -> &[Definition] { &self.definitions }
    pub fn definition(&self, id: BlockId) -> Option<&Definition> { self.definitions.get(id.0 as usize) }
    pub fn find(&self, name: &str) -> Option<&Definition> { self.by_name.get(name).and_then(|&id| self.definition(id)) }
    pub fn template(&self, d: &Definition) -> &Template { &self.templates[d.template as usize] }
    pub fn state_count(&self, d: &Definition) -> usize { self.graph_headers[self.template(d).graph as usize][0] as usize }
    pub fn default_state(&self, d: &Definition) -> StateId { StateId(d.first_state.0 + self.template(d).default_local) }
    pub fn graph_count(&self) -> usize { self.graphs.len() }
    pub fn graph_entries(&self) -> usize { self.graph_headers.iter().map(|h| (h[3] + h[4]) as usize).sum() }
}

pub fn registry() -> &'static Registry {
    static REGISTRY: OnceLock<Registry> = OnceLock::new();
    REGISTRY.get_or_init(Registry::build)
}

//! Native block registration identities, state domains and defaults.
//! Behavior, shapes and remaining intrinsic facts are separate migration work.
mod catalog;
mod ffi;
mod templates;
pub mod physics;
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
}

pub struct Registry {
    definitions: Vec<Definition>,
    templates: Vec<Template>,
    graphs: Vec<StateGraph>,
    by_name: HashMap<&'static str, BlockId>,
    header: [i32; 8],
    rows: Vec<i32>,
    template_rows: Vec<i32>,
    properties: Vec<i32>,
    graph_headers: Vec<[i32; 5]>,
    names: Vec<u8>,
    physics_rows: Vec<i32>,
}

impl Registry {
    fn build() -> Self {
        let mut r = Self {
            definitions: Vec::new(), templates: Vec::new(), graphs: Vec::new(), by_name: HashMap::new(),
            header: [0; 8], rows: Vec::new(), template_rows: Vec::new(), properties: Vec::new(),
            graph_headers: Vec::new(), names: Vec::new(),
            physics_rows: physics::PROFILES.iter().flat_map(physics::Physics::words).collect(),
        };
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
        for &(name, template, physical) in catalog::BLOCKS {
            let id = BlockId(u16::try_from(r.definitions.len()).expect("bounded native block IDs"));
            let t = &r.templates[template as usize];
            let states = r.graph_headers[t.graph as usize][0] as usize;
            assert!(next_state + states <= u16::MAX as usize, "native block state ceiling");
            assert!(r.by_name.insert(name, id).is_none(), "duplicate native block name");
            r.rows.extend([r.names.len() as i32, name.len() as i32, next_state as i32, template as i32, physical as i32]);
            r.names.extend_from_slice(name.as_bytes());
            r.definitions.push(Definition { id, name, first_state: StateId(next_state as u16), template: template as u16, physics: &physics::PROFILES[physical as usize] });
            next_state += states;
        }
        r.header = [2, r.definitions.len() as i32, next_state as i32, r.templates.len() as i32,
            r.properties.len() as i32, r.names.len() as i32, r.graphs.len() as i32, physics::PROFILES.len() as i32];
        r
    }

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

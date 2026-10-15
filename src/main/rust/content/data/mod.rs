//! Data-driven entity, item and biome definitions: Rust parses data pack
//! files, decodes them with each registry's schema, stores the decoded
//! definitions and encodes them for saves and registry synchronization.
//! The definitions stay data: files in data packs define them, and data
//! packs can override or add them. Java's registry objects are projections.
//! See docs/development/game-model/DATA-DEFINITIONS.md.
pub mod enchant;
pub mod ffi;
pub mod items;
pub mod registries;
pub mod schema;
pub mod text;
#[cfg(test)]
mod tests;
pub mod value;
pub mod values;

use schema::{Cx, D, R};
use value::{Mode, Node};

/// One decoded definition: its canonical value, the input nodes of its
/// delegated slots, and the partial errors decoding logged.
#[derive(Clone, Debug)]
pub struct Definition {
    pub registry: usize,
    pub network: bool,
    pub value: D,
    pub slots: Vec<Option<Node>>,
    pub logs: Vec<String>,
}

fn registry(index: usize) -> R<&'static registries::Registry> {
    registries::REGISTRIES.get(index).ok_or_else(|| format!("unknown data registry {index}"))
}

/// A failed decode: DFU's message and, where DFU keeps one, the partial definition.
#[derive(Debug)]
pub struct Failed {
    pub msg: String,
    pub partial: Option<Definition>,
}

impl From<String> for Failed {
    fn from(msg: String) -> Failed {
        Failed { msg, partial: None }
    }
}

/// Decodes `node` as an element of `registry` (direct or network form).
pub fn decode(index: usize, network: bool, node: &Node, mode: Mode, registry_ops: bool) -> R<Definition> {
    decode_partial(index, network, node, mode, registry_ops).map_err(|f| f.msg)
}

/// [`decode`], keeping a failure's partial definition (`DataResult.Error`'s partial).
pub fn decode_partial(index: usize, network: bool, node: &Node, mode: Mode, registry_ops: bool) -> Result<Definition, Failed> {
    let reg = registry(index)?;
    let mut cx = Cx::new(mode, registry_ops);
    match schema::decode(if network { reg.network } else { reg.direct }, node, &mut cx) {
        Ok(value) => Ok(Definition { registry: index, network, value, slots: cx.slots, logs: cx.logs }),
        Err(fail) => Err(Failed {
            msg: fail.msg,
            partial: fail.partial.map(|value| Definition { registry: index, network, value, slots: cx.slots, logs: cx.logs }),
        }),
    }
}

/// Parses and decodes a data pack file.
pub fn decode_file(index: usize, network: bool, bytes: &[u8]) -> Result<Definition, Failed> {
    let node: Node = crate::content::json::parse_into(bytes)?;
    decode_partial(index, network, &node, Mode::Json, true)
}

/// A definition Java built, checked by encoding it and decoding the result
/// (delegated slots stand in as placeholders).
pub fn define(index: usize, value: D, slots: usize) -> R<Definition> {
    let def = Definition { registry: index, network: false, value, slots: vec![None; slots], logs: Vec::new() };
    let encoded = encode(&def, false)?;
    decode(index, false, &schema::to_node(&encoded), Mode::Json, true)?;
    Ok(def)
}

/// Encodes a stored definition in direct or network form.
pub fn encode(def: &Definition, network: bool) -> R<schema::O<'_>> {
    let reg = registry(def.registry)?;
    let stored = if def.network { reg.network } else { reg.direct };
    schema::encode(if network { reg.network } else { reg.direct }, stored, &def.value)
}

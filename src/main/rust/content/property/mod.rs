//! Shared property schemas and built-in declarations. No Java or renderer
//! dependencies: consumers share immutable schemas and use compact value indices.
mod builtin;
mod ffi;
pub use builtin::Builtin;

use std::sync::{Arc, OnceLock};

/// Serialized names in state enumeration order. Shared between native
/// declarations and installed block registries; custom Java properties remain
/// imported until their owning content is migrated.
#[derive(Debug, PartialEq, Eq)]
pub struct Schema {
    name: String,
    values: Vec<String>,
}

impl Schema {
    pub fn new(name: String, values: Vec<String>) -> Option<Self> {
        if values.is_empty() || values.len() > u16::MAX as usize {
            return None;
        }
        Some(Self { name, values })
    }
    pub fn name(&self) -> &str { &self.name }
    pub fn values(&self) -> &[String] { &self.values }
}

#[derive(Clone, Copy, Debug)]
pub enum Domain {
    Boolean,
    Integer { min: u16, max: u16 },
    Enum(&'static [&'static str]),
}

impl Domain {
    pub fn count(self) -> u16 {
        match self {
            Self::Boolean => 2,
            Self::Integer { min, max } => max - min + 1,
            Self::Enum(values) => values.len() as u16,
        }
    }
    pub fn boolean(self, index: u16) -> Option<bool> {
        match (self, index) {
            (Self::Boolean, 0) => Some(true),
            (Self::Boolean, 1) => Some(false),
            _ => None,
        }
    }
    pub fn integer(self, index: u16) -> Option<u16> {
        match self {
            Self::Integer { min, max } if u32::from(min) + u32::from(index) <= u32::from(max) => Some(min + index),
            _ => None,
        }
    }
    fn names(self) -> Vec<String> {
        match self {
            Self::Boolean => vec!["true".into(), "false".into()],
            Self::Integer { min, max } => (min..=max).map(|v| v.to_string()).collect(),
            Self::Enum(values) => values.iter().map(|v| (*v).to_owned()).collect(),
        }
    }
}

struct Declaration {
    key: &'static str,
    name: &'static str,
    domain: Domain,
}

pub struct Definition {
    pub key: &'static str,
    pub domain: Domain,
    pub schema: Arc<Schema>,
}

pub struct Registry {
    definitions: Vec<Definition>,
    header: [i32; 6],
    rows: Vec<i32>,
    values: Vec<i32>,
    text: Vec<u8>,
}

impl Registry {
    fn build() -> Self {
        let mut r = Self { definitions: Vec::new(), header: [0; 6], rows: Vec::new(), values: Vec::new(), text: Vec::new() };
        for d in builtin::DECLARATIONS {
            let schema = Arc::new(Schema::new(d.name.to_owned(), d.domain.names()).expect("valid built-in property"));
            let key = r.text.len();
            r.text.extend_from_slice(d.key.as_bytes());
            let name = r.text.len();
            r.text.extend_from_slice(d.name.as_bytes());
            let kind = match d.domain { Domain::Boolean => 0, Domain::Integer { .. } => 1, Domain::Enum(_) => 2 };
            r.rows.extend([key as i32, d.key.len() as i32, name as i32, d.name.len() as i32, kind,
                (r.values.len() / 2) as i32, schema.values.len() as i32]);
            for value in &schema.values {
                r.values.extend([r.text.len() as i32, value.len() as i32]);
                r.text.extend_from_slice(value.as_bytes());
            }
            r.definitions.push(Definition { key: d.key, domain: d.domain, schema });
        }
        r.header = [1, r.definitions.len() as i32, 7, (r.values.len() / 2) as i32, 2, r.text.len() as i32];
        r
    }
    pub fn definitions(&self) -> &[Definition] { &self.definitions }
    pub fn get(&self, index: u16) -> Option<&Definition> { self.definitions.get(index as usize) }
}

pub fn registry() -> &'static Registry {
    static REGISTRY: OnceLock<Registry> = OnceLock::new();
    REGISTRY.get_or_init(Registry::build)
}

impl Builtin {
    pub fn definition(self) -> &'static Definition { &registry().definitions[self as usize] }
    pub fn domain(self) -> Domain { builtin::DECLARATIONS[self as usize].domain }
}

#[cfg(test)]
mod tests;

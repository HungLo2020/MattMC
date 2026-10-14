//! Input values as DFU sees them: a parsed data pack file, or a tree Java
//! walked from any `DynamicOps` input. Numbers keep Java's representation so
//! each target type converts them as Java does.

use std::sync::Arc;

use crate::content::json::{number, Build, Json};

/// A number as Java holds it: source text (`LazilyParsedNumber`) or a boxed primitive.
#[derive(Clone, Debug, PartialEq)]
pub enum Num {
    Text(String),
    Byte(i8),
    Short(i16),
    Int(i32),
    Long(i64),
    Float(f32),
    Double(f64),
}

impl Num {
    /// `Number.intValue`.
    pub fn int_value(&self) -> i32 {
        match self {
            Num::Text(text) => number::int_value(text),
            Num::Byte(v) => i32::from(*v),
            Num::Short(v) => i32::from(*v),
            Num::Int(v) => *v,
            Num::Long(v) => *v as i32,
            // Java's narrowing saturates and maps NaN to 0, as `as` does.
            Num::Float(v) => *v as i32,
            Num::Double(v) => *v as i32,
        }
    }
    /// `Number.floatValue`.
    pub fn float_value(&self) -> f32 {
        match self {
            Num::Text(text) => number::float_value(text),
            Num::Byte(v) => f32::from(*v),
            Num::Short(v) => f32::from(*v),
            Num::Int(v) => *v as f32,
            Num::Long(v) => *v as f32,
            Num::Float(v) => *v,
            Num::Double(v) => *v as f32,
        }
    }
    /// `Number.doubleValue`.
    pub fn double_value(&self) -> f64 {
        match self {
            Num::Text(text) => number::double_value(text),
            Num::Byte(v) => f64::from(*v),
            Num::Short(v) => f64::from(*v),
            Num::Int(v) => f64::from(*v),
            Num::Long(v) => *v as f64,
            Num::Float(v) => f64::from(*v),
            Num::Double(v) => *v,
        }
    }
    /// `Number.byteValue` (`NbtOps` reads booleans as `byteValue() != 0`).
    pub fn byte_value(&self) -> i8 {
        match self {
            Num::Byte(v) => *v,
            Num::Short(v) => *v as i8,
            Num::Long(v) => *v as i8,
            Num::Float(v) => (*v as i32) as i8,
            Num::Double(v) => (*v as i32) as i8,
            other => other.int_value() as i8,
        }
    }
}

impl std::fmt::Display for Num {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Num::Text(text) => f.write_str(text),
            Num::Byte(v) => write!(f, "{v}b"),
            Num::Short(v) => write!(f, "{v}s"),
            Num::Int(v) => write!(f, "{v}"),
            Num::Long(v) => write!(f, "{v}L"),
            Num::Float(v) => write!(f, "{v}f"),
            Num::Double(v) => write!(f, "{v}d"),
        }
    }
}

#[derive(Clone, Debug, PartialEq)]
pub enum Value {
    Null,
    Bool(bool),
    Number(Num),
    String(String),
    /// Shared, so keeping a delegated subtree does not copy it.
    List(Arc<[Node]>),
    Map(Arc<[(String, Node)]>),
}

/// An input value and, for trees from Java, its preorder index there.
#[derive(Clone, Debug, PartialEq)]
pub struct Node {
    pub index: Option<u32>,
    pub value: Value,
}

impl Node {
    pub fn new(value: Value) -> Node {
        Node { index: None, value }
    }

    pub fn from_json(json: Json) -> Node {
        Node::new(match json {
            Json::Null => Value::Null,
            Json::Bool(v) => Value::Bool(v),
            Json::Number(text) => Value::Number(Num::Text(text)),
            Json::String(text) => Value::String(text),
            Json::Array(items) => Value::List(items.into_iter().map(Node::from_json).collect()),
            Json::Object(members) => Value::Map(members.into_iter().map(|(k, v)| (k, Node::from_json(v))).collect()),
        })
    }

    /// A map member; null counts as absent, as `JsonOps` and `NbtOps` maps do.
    pub fn get(&self, key: &str) -> Option<&Node> {
        match &self.value {
            Value::Map(members) => members.iter().find(|(k, _)| k == key).map(|(_, v)| v).filter(|v| v.value != Value::Null),
            _ => None,
        }
    }
}

impl std::fmt::Display for Node {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match &self.value {
            Value::Null => f.write_str("null"),
            Value::Bool(v) => write!(f, "{v}"),
            Value::Number(n) => write!(f, "{n}"),
            Value::String(s) => write!(f, "{}", Json::String(s.clone())),
            Value::List(items) => {
                f.write_str("[")?;
                for (i, item) in items.iter().enumerate() {
                    if i > 0 {
                        f.write_str(",")?;
                    }
                    write!(f, "{item}")?;
                }
                f.write_str("]")
            }
            Value::Map(members) => {
                f.write_str("{")?;
                for (i, (k, v)) in members.iter().enumerate() {
                    if i > 0 {
                        f.write_str(",")?;
                    }
                    write!(f, "{}:{v}", Json::String(k.clone()))?;
                }
                f.write_str("}")
            }
        }
    }
}

/// The parser builds nodes directly, without an intermediate `Json` tree.
impl Build for Node {
    fn null() -> Self {
        Node::new(Value::Null)
    }
    fn boolean(value: bool) -> Self {
        Node::new(Value::Bool(value))
    }
    fn number(text: String) -> Self {
        Node::new(Value::Number(Num::Text(text)))
    }
    fn string(text: String) -> Self {
        Node::new(Value::String(text))
    }
    fn array(items: Vec<Self>) -> Self {
        Node::new(Value::List(items.into()))
    }
    fn object(members: Vec<(String, Self)>) -> Self {
        Node::new(Value::Map(members.into()))
    }
    fn is_null(&self) -> bool {
        self.value == Value::Null
    }
}

/// How the source `DynamicOps` reads primitives.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Mode {
    /// `JsonOps`: booleans are only JSON booleans.
    Json,
    /// `NbtOps`: booleans are numbers (`byteValue() != 0`).
    Nbt,
}

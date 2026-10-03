//! Immutable typed programs for active custom property uniforms.
//!
//! Compilation links only required definitions and their dependency closure.
//! Input names resolve to the closed frame-semantic catalog, never raw bytes,
//! Java evaluator state or GPU objects.

mod compiler;
mod evaluate;
mod inputs;
mod parser;
#[cfg(test)]
mod tests;

use super::{TerrainSourceUniformFrame, TerrainSourceUniformSemantic};
use crate::render::shaderpack::lowering::TerrainSourceUniformType;
use crate::render::shaderpack::properties::custom_uniforms::CustomUniformDefinition;
use crate::render::vulkanic::error::{GalError, GalResult};
use inputs::Input;
use parser::{Expr, Op};
use std::collections::{BTreeMap, BTreeSet};

type Id = u16;
const MAX_NODES: usize = 8192;
const MAX_DEPTH: usize = 128;

fn invalid(message: impl Into<String>) -> GalError {
    GalError::invalid_argument(message)
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
enum Ty {
    Float,
    Int,
    Bool,
    Vector(u8),
}

impl Ty {
    fn from_property(name: &str) -> GalResult<Self> {
        match name {
            "float" => Ok(Self::Float),
            "int" => Ok(Self::Int),
            "bool" => Ok(Self::Bool),
            "vec2" => Ok(Self::Vector(2)),
            "vec3" => Ok(Self::Vector(3)),
            "vec4" => Ok(Self::Vector(4)),
            _ => Err(invalid(format!(
                "unsupported active custom property type '{name}'"
            ))),
        }
    }
    fn uniform_type(self) -> TerrainSourceUniformType {
        match self {
            Self::Float => TerrainSourceUniformType::Float,
            Self::Int => TerrainSourceUniformType::Int,
            Self::Bool => TerrainSourceUniformType::Bool,
            Self::Vector(2) => TerrainSourceUniformType::Vec2,
            Self::Vector(3) => TerrainSourceUniformType::Vec3,
            Self::Vector(4) => TerrainSourceUniformType::Vec4,
            _ => unreachable!("validated vector width"),
        }
    }
    fn numeric(self) -> bool {
        matches!(self, Self::Int | Self::Float)
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
enum Literal {
    Float(u32),
    Int(i32),
}

#[derive(Clone, Copy, Debug, PartialEq)]
pub(super) enum Value {
    Float(f32),
    Int(i32),
    Bool(bool),
    Vector([f32; 4], u8),
}

impl Value {
    fn float(self) -> GalResult<f32> {
        match self {
            Self::Float(value) => Ok(value),
            Self::Int(value) => Ok(value as f32),
            _ => Err(invalid("custom expression requires numeric scalar")),
        }
    }
    fn boolean(self) -> GalResult<bool> {
        match self {
            Self::Bool(value) => Ok(value),
            _ => Err(invalid("custom expression requires boolean")),
        }
    }
    fn finite(self) -> GalResult<Self> {
        let finite = match self {
            Self::Float(value) => value.is_finite(),
            Self::Vector(values, count) => values[..count as usize]
                .iter()
                .all(|value| value.is_finite()),
            _ => true,
        };
        if finite {
            Ok(self)
        } else {
            Err(invalid("custom expression produced a non-finite value"))
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
enum Function {
    Min,
    Max,
    Clamp,
    Fmod,
    Atan,
    Log,
    Abs,
    Floor,
    Ceil,
    Sqrt,
    Pow,
    Sin,
    Cos,
    Exp,
    Frac,
}

#[derive(Clone, Debug, Eq, PartialEq)]
enum Kind {
    Literal(Literal),
    Input(Input),
    Neg(Id),
    Not(Id),
    Binary(Op, Id, Id),
    Cast(Id),
    Component(Id, u8),
    Vector(Vec<Id>),
    Select(Vec<(Id, Id)>, Id),
    Function(Function, Vec<Id>),
}

#[derive(Clone, Debug, Eq, PartialEq)]
struct Node {
    ty: Ty,
    kind: Kind,
    depth: usize,
}

#[derive(Clone, Debug, Default, Eq, PartialEq)]
pub(super) struct Program {
    nodes: Vec<Node>,
    roots: BTreeMap<String, Id>,
}

impl Program {
    pub(super) fn compile(
        definitions: &BTreeMap<String, CustomUniformDefinition>,
        roots: &[(&str, TerrainSourceUniformType)],
    ) -> GalResult<Self> {
        compiler::compile(definitions, roots)
    }

    pub(super) fn root(&self, name: &str) -> Option<Id> {
        self.roots.get(name).copied()
    }

    pub(super) fn evaluate(
        &self,
        frame: &TerrainSourceUniformFrame,
    ) -> GalResult<Vec<Option<Value>>> {
        let mut memo = vec![None; self.nodes.len()];
        for &root in self.roots.values() {
            evaluate::node(self, root, frame, &mut memo)?;
        }
        Ok(memo)
    }
}

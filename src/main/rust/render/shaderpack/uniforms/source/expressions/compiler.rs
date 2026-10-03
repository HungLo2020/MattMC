//! Type-directed overload resolution following Frozen's Stareval resolver.

use super::*;

pub(super) fn compile(
    definitions: &BTreeMap<String, CustomUniformDefinition>,
    roots: &[(&str, TerrainSourceUniformType)],
) -> GalResult<Program> {
    let mut compiler = Compiler {
        definitions,
        nodes: Vec::new(),
        linked: BTreeMap::new(),
        visiting: BTreeSet::new(),
        work: 0,
    };
    let mut resolved = BTreeMap::new();
    for &(name, expected) in roots {
        let definition = definitions
            .get(name)
            .ok_or_else(|| invalid("missing custom uniform definition"))?;
        if !definition.uniform || Ty::from_property(&definition.ty)?.uniform_type() != expected {
            return Err(invalid(format!(
                "custom uniform '{name}' property does not match its GLSL declaration"
            )));
        }
        resolved.insert(name.to_string(), compiler.definition(name, 0)?);
    }
    Ok(Program {
        nodes: compiler.nodes,
        roots: resolved,
    })
}

struct Compiler<'a> {
    definitions: &'a BTreeMap<String, CustomUniformDefinition>,
    nodes: Vec<Node>,
    linked: BTreeMap<String, Id>,
    visiting: BTreeSet<String>,
    work: usize,
}

impl Compiler<'_> {
    fn add(&mut self, ty: Ty, kind: Kind, dependencies: &[Id]) -> GalResult<Id> {
        let depth = dependencies
            .iter()
            .map(|&id| self.nodes[id as usize].depth)
            .max()
            .unwrap_or(0)
            + 1;
        if depth > MAX_DEPTH || self.nodes.len() >= MAX_NODES {
            return Err(invalid("custom expression graph exceeds depth/node budget"));
        }
        let id = self.nodes.len() as Id;
        self.nodes.push(Node { ty, kind, depth });
        Ok(id)
    }
    fn ty(&self, id: Id) -> Ty {
        self.nodes[id as usize].ty
    }
    fn definition(&mut self, name: &str, depth: usize) -> GalResult<Id> {
        if depth > MAX_DEPTH {
            return Err(invalid("custom expression dependency depth exceeded"));
        }
        if let Some(&id) = self.linked.get(name) {
            return Ok(id);
        }
        if !self.visiting.insert(name.to_string()) {
            return Err(invalid(format!(
                "cyclic custom expression dependency '{name}'"
            )));
        }
        let definition = self
            .definitions
            .get(name)
            .ok_or_else(|| invalid(format!("unknown custom expression input '{name}'")))?;
        let ty = Ty::from_property(&definition.ty)?;
        let expression = parser::parse(&definition.expression)?;
        let id = self
            .resolve(&expression, ty, true, true, depth + 1)?
            .ok_or_else(|| {
                invalid(format!(
                    "custom property '{name}' cannot resolve to its declared type"
                ))
            })?;
        self.visiting.remove(name);
        self.linked.insert(name.to_string(), id);
        Ok(id)
    }
    fn name(&mut self, name: &str, depth: usize) -> GalResult<Id> {
        if let Some((input, ty)) = Input::resolve(name)? {
            return self.add(ty, Kind::Input(input), &[]);
        }
        let mut parts = name.split('.');
        let id = self.definition(parts.next().unwrap(), depth + 1)?;
        match parts.next() {
            None => Ok(id),
            Some(component) => {
                if parts.next().is_some() {
                    return Err(invalid("custom vector access has too many components"));
                }
                let Ty::Vector(width) = self.ty(id) else {
                    return Err(invalid("custom component access requires a vector"));
                };
                let component = inputs::component(component)?;
                if component >= width {
                    return Err(invalid("custom vector component exceeds width"));
                }
                self.add(Ty::Float, Kind::Component(id, component), &[id])
            }
        }
    }
    fn resolve(
        &mut self,
        expression: &Expr,
        target: Ty,
        direct: bool,
        implicit: bool,
        depth: usize,
    ) -> GalResult<Option<Id>> {
        self.work += 1;
        if depth > MAX_DEPTH || self.work > 32768 {
            return Err(invalid(
                "custom expression resolution exceeds depth/work budget",
            ));
        }
        let leaf = match expression {
            Expr::Literal(value) => {
                let ty = match value {
                    Literal::Float(_) => Ty::Float,
                    Literal::Int(_) => Ty::Int,
                };
                if ty != target && !(implicit && ty == Ty::Int && target == Ty::Float) {
                    return Ok(None);
                }
                Some(self.add(ty, Kind::Literal(*value), &[])?)
            }
            Expr::Name(name) => Some(self.name(name, depth + 1)?),
            _ => None,
        };
        if let Some(id) = leaf {
            return if self.ty(id) == target {
                Ok(Some(id))
            } else if implicit && self.ty(id) == Ty::Int && target == Ty::Float {
                Ok(Some(self.add(target, Kind::Cast(id), &[id])?))
            } else {
                Ok(None)
            };
        }
        // Match exact signatures first. Next try one final implicit cast of
        // the whole expression. Only then insert casts inside arguments.
        if direct {
            if let Some(id) = self.call(expression, target, false, depth + 1)? {
                return Ok(Some(id));
            }
        }
        if !implicit {
            return Ok(None);
        }
        if target == Ty::Float {
            if let Some(id) = self.resolve(expression, Ty::Int, true, true, depth + 1)? {
                return Ok(Some(self.add(Ty::Float, Kind::Cast(id), &[id])?));
            }
        }
        self.call(expression, target, true, depth + 1)
    }
    fn arguments(
        &mut self,
        expressions: &[&Expr],
        types: &[Ty],
        implicit: bool,
        depth: usize,
    ) -> GalResult<Option<Vec<Id>>> {
        let mut args = Vec::with_capacity(expressions.len());
        for (&expression, &ty) in expressions.iter().zip(types) {
            let Some(id) = self.resolve(
                expression,
                ty,
                !implicit || expressions.len() > 1,
                implicit,
                depth + 1,
            )?
            else {
                return Ok(None);
            };
            args.push(id);
        }
        Ok(Some(args))
    }
    fn call(
        &mut self,
        expression: &Expr,
        target: Ty,
        implicit: bool,
        depth: usize,
    ) -> GalResult<Option<Id>> {
        match expression {
            Expr::Neg(value) => {
                if !target.numeric() {
                    return Ok(None);
                }
                let Some(args) = self.arguments(&[value], &[target], implicit, depth)? else {
                    return Ok(None);
                };
                Ok(Some(self.add(target, Kind::Neg(args[0]), &args)?))
            }
            Expr::Not(value) => {
                if target != Ty::Bool {
                    return Ok(None);
                }
                let Some(args) = self.arguments(&[value], &[Ty::Bool], implicit, depth)? else {
                    return Ok(None);
                };
                Ok(Some(self.add(target, Kind::Not(args[0]), &args)?))
            }
            Expr::Binary(op, left, right) => {
                let comparison = matches!(op, Op::Eq | Op::Ne | Op::Lt | Op::Le | Op::Gt | Op::Ge);
                let logical = matches!(op, Op::And | Op::Or);
                let candidates: &[Ty] = if logical {
                    if target != Ty::Bool {
                        return Ok(None);
                    }
                    &[Ty::Bool]
                } else if comparison {
                    if target != Ty::Bool {
                        return Ok(None);
                    }
                    if matches!(op, Op::Eq | Op::Ne) {
                        &[Ty::Int, Ty::Float, Ty::Bool]
                    } else {
                        &[Ty::Int, Ty::Float]
                    }
                } else {
                    if !target.numeric() || (*op == Op::Div && target != Ty::Float) {
                        return Ok(None);
                    }
                    std::slice::from_ref(&target)
                };
                let mut resolved = None;
                for &ty in candidates {
                    if let Some(args) =
                        self.arguments(&[left, right], &[ty, ty], implicit, depth)?
                    {
                        // Stareval rejects equal-priority overload ambiguities.
                        if resolved.is_some() {
                            return Err(invalid("ambiguous custom expression operator"));
                        }
                        resolved =
                            Some(self.add(target, Kind::Binary(*op, args[0], args[1]), &args)?);
                    }
                }
                Ok(resolved)
            }
            Expr::Call(name, expressions) => {
                let expressions = expressions.iter().collect::<Vec<_>>();
                if name == "if" {
                    if expressions.len() < 3 || expressions.len() % 2 != 1 {
                        return Err(invalid(
                            "custom if requires condition/value pairs and an else value",
                        ));
                    }
                    let last = expressions.len() - 1;
                    let types = (0..expressions.len())
                        .map(|i| {
                            if i < last && i % 2 == 0 {
                                Ty::Bool
                            } else {
                                target
                            }
                        })
                        .collect::<Vec<_>>();
                    let Some(args) = self.arguments(&expressions, &types, implicit, depth)? else {
                        return Ok(None);
                    };
                    let pairs = args[..last]
                        .chunks_exact(2)
                        .map(|pair| (pair[0], pair[1]))
                        .collect();
                    return Ok(Some(self.add(
                        target,
                        Kind::Select(pairs, args[last]),
                        &args,
                    )?));
                }
                if matches!(name.as_str(), "vec2" | "vec3" | "vec4") {
                    let ty = Ty::from_property(name)?;
                    let Ty::Vector(width) = ty else {
                        unreachable!()
                    };
                    if expressions.len() != width as usize {
                        return Err(invalid("custom vector constructor has wrong arity"));
                    }
                    if ty != target {
                        return Ok(None);
                    }
                    let Some(args) = self.arguments(
                        &expressions,
                        &vec![Ty::Float; width as usize],
                        implicit,
                        depth,
                    )?
                    else {
                        return Ok(None);
                    };
                    return Ok(Some(self.add(target, Kind::Vector(args.clone()), &args)?));
                }
                if name == "toInt" || name == "toFloat" {
                    if expressions.len() != 1 {
                        return Err(invalid("custom explicit cast requires one argument"));
                    }
                    let (result, argument) = if name == "toInt" {
                        (Ty::Int, Ty::Float)
                    } else {
                        (Ty::Float, Ty::Int)
                    };
                    if target != result {
                        return Ok(None);
                    }
                    let Some(args) = self.arguments(&expressions, &[argument], implicit, depth)?
                    else {
                        return Ok(None);
                    };
                    return Ok(Some(self.add(target, Kind::Cast(args[0]), &args)?));
                }
                let (function, arity) = match name.as_str() {
                    "min" => (Function::Min, 0),
                    "max" => (Function::Max, 0),
                    "clamp" => (Function::Clamp, 3),
                    "fmod" => (Function::Fmod, 2),
                    "atan" | "atan2" => (
                        Function::Atan,
                        if expressions.len() == 1 && name == "atan" {
                            1
                        } else {
                            2
                        },
                    ),
                    "log" => (Function::Log, if expressions.len() == 2 { 2 } else { 1 }),
                    "abs" => (Function::Abs, 1),
                    "floor" => (Function::Floor, 1),
                    "ceil" => (Function::Ceil, 1),
                    "sqrt" => (Function::Sqrt, 1),
                    "pow" => (Function::Pow, 2),
                    "sin" => (Function::Sin, 1),
                    "cos" => (Function::Cos, 1),
                    "exp" => (Function::Exp, 1),
                    "frac" => (Function::Frac, 1),
                    _ => {
                        return Err(invalid(format!(
                            "unsupported active custom function '{name}'"
                        )))
                    }
                };
                if (arity == 0 && expressions.len() < 2)
                    || (arity != 0 && expressions.len() != arity)
                {
                    return Err(invalid("custom function has incompatible arguments"));
                }
                let integer = matches!(
                    function,
                    Function::Min
                        | Function::Max
                        | Function::Clamp
                        | Function::Fmod
                        | Function::Abs
                        | Function::Floor
                        | Function::Ceil
                );
                if !target.numeric() || (target == Ty::Int && !integer) {
                    return Ok(None);
                }
                let argument = if matches!(function, Function::Floor | Function::Ceil) {
                    Ty::Float
                } else {
                    target
                };
                let Some(args) = self.arguments(
                    &expressions,
                    &vec![argument; expressions.len()],
                    implicit,
                    depth,
                )?
                else {
                    return Ok(None);
                };
                Ok(Some(self.add(
                    target,
                    Kind::Function(function, args.clone()),
                    &args,
                )?))
            }
            _ => unreachable!("leaf expressions already resolved"),
        }
    }
}

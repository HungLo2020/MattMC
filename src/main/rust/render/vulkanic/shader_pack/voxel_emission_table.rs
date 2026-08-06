//! Source-derived colored-light emissions for the owned voxel volume.

use std::collections::BTreeMap;

use crate::render::vulkanic::error::{GalError, GalResult};

use super::source::ShaderPackSource;
use super::terrain_contract::TerrainPassContract;

/// The selected source currently declares IDs `200..219`. Keeping this
/// explicit makes a changed source table reject rather than silently binding
/// a differently shaped UBO to the propagation kernel.
pub const VOXEL_TINT_COUNT: usize = 20;

#[derive(Clone, Debug, PartialEq)]
pub struct VoxelEmissionTable {
    shader_pack_generation: u64,
    values: [[f32; 4]; 256],
    tints: [[f32; 4]; VOXEL_TINT_COUNT],
}

impl VoxelEmissionTable {
    pub fn derive(source: &ShaderPackSource, contract: &TerrainPassContract) -> GalResult<Self> {
        if source.generation() != contract.generation {
            return Err(GalError::invalid_argument(
                "voxel emission generation mismatch",
            ));
        }
        let text = source
            .get("lib/colors/blocklightColors.glsl")
            .ok_or_else(|| {
                GalError::invalid_argument("selected shader pack has no blocklight color source")
            })?;
        let active = active_lines(text, &contract.property_defines)?;
        let global_declarations = active
            .split("vec4 GetSpecialBlocklightColor(")
            .next()
            .ok_or_else(|| {
                GalError::invalid_argument("selected shader emission function prefix is missing")
            })?;
        let variables = parse_variables(global_declarations, &contract.property_defines)?;
        let body = function_body(&active, "GetSpecialBlocklightColor")?;
        let mut values = [[0.0; 4]; 256];
        values.fill(parse_default(&body, &variables)?);
        for (id, expression) in parse_material_returns(&body)? {
            values[usize::from(id)] = evaluate_vec4(&expression, &variables)?;
        }
        let tints = parse_tints(&active, &variables)?;
        Ok(Self {
            shader_pack_generation: source.generation(),
            values,
            tints,
        })
    }

    pub fn shader_pack_generation(&self) -> u64 {
        self.shader_pack_generation
    }
    pub fn value(&self, voxel_id: u8) -> [f32; 4] {
        self.values[usize::from(voxel_id)]
    }
    pub fn tint_value(&self, voxel_id: u8) -> [f32; 4] {
        self.tints[usize::from(voxel_id.saturating_sub(200)).min(VOXEL_TINT_COUNT - 1)]
    }
    pub fn std140_bytes(&self) -> Vec<u8> {
        self.values
            .iter()
            .flat_map(|value| value.iter().flat_map(|component| component.to_ne_bytes()))
            .collect()
    }
    pub fn tint_std140_bytes(&self) -> Vec<u8> {
        self.tints
            .iter()
            .flat_map(|value| value.iter().flat_map(|component| component.to_ne_bytes()))
            .collect()
    }
}

#[derive(Default)]
struct Variables {
    scalars: BTreeMap<String, f32>,
    vectors3: BTreeMap<String, [f32; 3]>,
    vectors4: BTreeMap<String, [f32; 4]>,
}

fn parse_variables(source: &str, defines: &BTreeMap<String, String>) -> GalResult<Variables> {
    let mut values = Variables::default();
    for (name, value) in defines {
        if let Ok(value) = value.parse::<f32>() {
            values.scalars.insert(name.clone(), value);
        }
    }
    let mut brace_depth = 0_i32;
    for line in source.lines() {
        let statement = line.split_once("//").map_or(line, |(code, _)| code).trim();
        let statement = statement.strip_suffix(';').unwrap_or(statement).trim();
        if brace_depth == 0 {
            if let Some(rest) = statement.strip_prefix("float ") {
                let (name, expression) = rest.split_once('=').ok_or_else(|| {
                    GalError::invalid_argument("malformed shader float declaration")
                })?;
                let scalar = evaluate_scalar(expression, &values)?;
                values.scalars.insert(name.trim().to_string(), scalar);
            } else if let Some(rest) = statement.strip_prefix("vec3 ") {
                let (name, expression) = rest.split_once('=').ok_or_else(|| {
                    GalError::invalid_argument("malformed shader vec3 declaration")
                })?;
                let vector = evaluate_vec3(expression, &values)?;
                values.vectors3.insert(name.trim().to_string(), vector);
            } else if let Some(rest) = statement.strip_prefix("vec4 ") {
                let (name, expression) = rest.split_once('=').ok_or_else(|| {
                    GalError::invalid_argument("malformed shader vec4 declaration")
                })?;
                let vector = evaluate_vec4(expression, &values)?;
                values.vectors4.insert(name.trim().to_string(), vector);
            }
        }
        brace_depth += statement
            .chars()
            .filter(|character| *character == '{')
            .count() as i32;
        brace_depth -= statement
            .chars()
            .filter(|character| *character == '}')
            .count() as i32;
        if brace_depth < 0 {
            return Err(GalError::invalid_argument(
                "unbalanced shader braces before emission function",
            ));
        }
    }
    Ok(values)
}

fn parse_material_returns(body: &str) -> GalResult<Vec<(u8, String)>> {
    let compact = body.split_whitespace().collect::<String>();
    let mut entries = Vec::new();
    let mut rest = compact.as_str();
    while let Some(position) = rest.find("if(mat==") {
        rest = &rest[position + 8..];
        let end = rest
            .find(')')
            .ok_or_else(|| GalError::invalid_argument("malformed voxel emission material rule"))?;
        let id = rest[..end]
            .parse::<u8>()
            .map_err(|_| GalError::invalid_argument("voxel emission id is outside r8ui"))?;
        let after = &rest[end + 1..];
        if let Some(expression) = after.strip_prefix("return") {
            let expression_end = expression
                .find(';')
                .ok_or_else(|| GalError::invalid_argument("unterminated voxel emission return"))?;
            entries.push((id, expression[..expression_end].to_string()));
            rest = &expression[expression_end + 1..];
        } else {
            rest = after;
        }
    }
    if entries.is_empty() {
        Err(GalError::invalid_argument(
            "selected shader contains no voxel emission material returns",
        ))
    } else {
        Ok(entries)
    }
}

fn parse_default(body: &str, variables: &Variables) -> GalResult<[f32; 4]> {
    let compact = body.split_whitespace().collect::<String>();
    let position = compact.rfind("return").ok_or_else(|| {
        GalError::invalid_argument("selected shader has no voxel emission default")
    })?;
    let expression = &compact[position + 6..];
    let end = expression
        .find(';')
        .ok_or_else(|| GalError::invalid_argument("unterminated voxel emission default"))?;
    evaluate_vec4(&expression[..end], variables)
}

fn parse_tints(source: &str, variables: &Variables) -> GalResult<[[f32; 4]; VOXEL_TINT_COUNT]> {
    let marker = "vec3[] specialTintColor = vec3[](";
    let start = source.find(marker).ok_or_else(|| {
        GalError::invalid_argument("selected shader source has no specialTintColor table")
    })? + marker.len();
    let end = source[start..]
        .find(");")
        .map(|offset| start + offset)
        .ok_or_else(|| GalError::invalid_argument("specialTintColor table is unterminated"))?;
    let mut values = Vec::new();
    let mut rest = &source[start..end];
    while let Some(position) = rest.find("vec3(") {
        rest = &rest[position + 5..];
        let mut depth = 1_i32;
        let mut close = None;
        for (offset, character) in rest.char_indices() {
            match character {
                '(' => depth += 1,
                ')' => {
                    depth -= 1;
                    if depth == 0 {
                        close = Some(offset);
                        break;
                    }
                }
                _ => {}
            }
        }
        let close = close.ok_or_else(|| {
            GalError::invalid_argument("specialTintColor entry has unbalanced parentheses")
        })?;
        let expression = format!("vec3({})", &rest[..close]);
        let rgb = evaluate_vec3(&expression, variables)?;
        values.push([rgb[0], rgb[1], rgb[2], 0.0]);
        rest = &rest[close + 1..];
    }
    if values.len() != VOXEL_TINT_COUNT {
        return Err(GalError::invalid_argument(format!(
            "selected shader specialTintColor table has {} entries; expected {VOXEL_TINT_COUNT}",
            values.len()
        )));
    }
    values.try_into().map_err(|_| {
        GalError::invalid_argument("specialTintColor table did not fit its declared fixed contract")
    })
}

fn evaluate_scalar(expression: &str, values: &Variables) -> GalResult<f32> {
    let expression = trim_parens(expression.trim());
    if let Some((left, right)) = split_top(expression, "*") {
        return Ok(evaluate_scalar(left, values)? * evaluate_scalar(right, values)?);
    }
    expression
        .parse::<f32>()
        .ok()
        .or_else(|| values.scalars.get(expression).copied())
        .ok_or_else(|| {
            GalError::invalid_argument(format!("unsupported voxel emission scalar {expression}"))
        })
}

fn evaluate_vec3(expression: &str, values: &Variables) -> GalResult<[f32; 3]> {
    let expression = trim_parens(expression.trim());
    if let Some((left, right)) = split_top(expression, "*") {
        if let Ok(vector) = evaluate_vec3(left, values) {
            if let Ok(scalar) = evaluate_scalar(right, values) {
                return Ok(vector.map(|value| value * scalar));
            }
            if let Ok(other) = evaluate_vec3(right, values) {
                return Ok(std::array::from_fn(|index| vector[index] * other[index]));
            }
        }
        if let (Ok(scalar), Ok(vector)) =
            (evaluate_scalar(left, values), evaluate_vec3(right, values))
        {
            return Ok(vector.map(|value| value * scalar));
        }
    }
    if let Some(inner) = expression
        .strip_prefix("vec3(")
        .and_then(|value| value.strip_suffix(')'))
    {
        let components = split_arguments(inner);
        return match components.as_slice() {
            [value] => Ok([evaluate_scalar(value, values)?; 3]),
            [red, green, blue] => Ok([
                evaluate_scalar(red, values)?,
                evaluate_scalar(green, values)?,
                evaluate_scalar(blue, values)?,
            ]),
            _ => Err(GalError::invalid_argument(format!(
                "unsupported vec3 voxel emission expression {expression}"
            ))),
        };
    }
    values.vectors3.get(expression).copied().ok_or_else(|| {
        GalError::invalid_argument(format!("unsupported voxel emission vector {expression}"))
    })
}

fn evaluate_vec4(expression: &str, values: &Variables) -> GalResult<[f32; 4]> {
    let expression = trim_parens(expression.trim());
    if let Some(inner) = expression
        .strip_prefix("vec4(")
        .and_then(|value| value.strip_suffix(')'))
    {
        let components = split_arguments(inner);
        return match components.as_slice() {
            [scalar] => Ok([evaluate_scalar(scalar, values)?; 4]),
            [vector, alpha] => {
                let rgb = evaluate_vec3(vector, values)?;
                Ok([rgb[0], rgb[1], rgb[2], evaluate_scalar(alpha, values)?])
            }
            [red, green, blue, alpha] => Ok([
                evaluate_scalar(red, values)?,
                evaluate_scalar(green, values)?,
                evaluate_scalar(blue, values)?,
                evaluate_scalar(alpha, values)?,
            ]),
            _ => Err(GalError::invalid_argument(
                "unsupported vec4 voxel emission expression",
            )),
        };
    }
    if let Some((left, right)) = split_top(expression, "*") {
        let vector = evaluate_vec4(left, values)?;
        let scalar = evaluate_scalar(right, values)?;
        return Ok(vector.map(|value| value * scalar));
    }
    values.vectors4.get(expression).copied().ok_or_else(|| {
        GalError::invalid_argument(format!("unsupported voxel emission color {expression}"))
    })
}

fn active_lines(source: &str, defines: &BTreeMap<String, String>) -> GalResult<String> {
    // The selected source only has simple #if branches here. Carrying the
    // selected defines makes source-derived emission deterministic.
    let mut out = String::new();
    let mut active = true;
    let mut stack = Vec::new();
    for raw in source.lines() {
        let line = raw.trim();
        if let Some(directive) = line.strip_prefix('#').map(str::trim) {
            if let Some(expr) = directive.strip_prefix("if ") {
                stack.push(active);
                active &= condition(expr, defines)?;
                continue;
            }
            if let Some(name) = directive.strip_prefix("ifdef ") {
                stack.push(active);
                active &= defines.contains_key(name.trim());
                continue;
            }
            if let Some(name) = directive.strip_prefix("ifndef ") {
                stack.push(active);
                active &= !defines.contains_key(name.trim());
                continue;
            }
            if directive == "else" {
                let parent = stack
                    .last()
                    .copied()
                    .ok_or_else(|| GalError::invalid_argument("shader #else without #if"))?;
                active = parent && !active;
                continue;
            }
            if directive == "endif" {
                active = stack
                    .pop()
                    .ok_or_else(|| GalError::invalid_argument("shader #endif without #if"))?;
                continue;
            }
        }
        if active {
            out.push_str(raw);
            out.push('\n');
        }
    }
    if stack.is_empty() {
        Ok(out)
    } else {
        Err(GalError::invalid_argument(
            "unterminated shader conditional in emission source",
        ))
    }
}
fn condition(expression: &str, defines: &BTreeMap<String, String>) -> GalResult<bool> {
    let expression = expression.trim();
    for op in [">=", "<=", "==", ">", "<"] {
        if let Some((left, right)) = split_top(expression, op) {
            let number = |value: &str| {
                value
                    .trim()
                    .parse::<f32>()
                    .ok()
                    .or_else(|| defines.get(value.trim()).and_then(|v| v.parse().ok()))
                    .ok_or_else(|| GalError::invalid_argument("unsupported emission conditional"))
            };
            let (a, b) = (number(left)?, number(right)?);
            return Ok(match op {
                ">=" => a >= b,
                "<=" => a <= b,
                "==" => a == b,
                ">" => a > b,
                _ => a < b,
            });
        }
    }
    Ok(defines.get(expression).map(String::as_str).unwrap_or("0") != "0")
}
fn function_body(source: &str, name: &str) -> GalResult<String> {
    let start = source.find(&format!("vec4 {name}(")).ok_or_else(|| {
        GalError::invalid_argument("selected shader source has no emission function")
    })?;
    let open = source[start..]
        .find('{')
        .map(|x| start + x)
        .ok_or_else(|| GalError::invalid_argument("emission function has no body"))?;
    let mut depth = 0;
    for (offset, ch) in source[open..].char_indices() {
        match ch {
            '{' => depth += 1,
            '}' => {
                depth -= 1;
                if depth == 0 {
                    return Ok(source[open + 1..open + offset].to_string());
                }
            }
            _ => {}
        }
    }
    Err(GalError::invalid_argument(
        "emission function is unterminated",
    ))
}
fn trim_parens(mut value: &str) -> &str {
    while value.starts_with('(') && value.ends_with(')') {
        value = value[1..value.len() - 1].trim();
    }
    value
}
fn split_top<'a>(value: &'a str, operator: &str) -> Option<(&'a str, &'a str)> {
    let mut depth = 0;
    for (index, ch) in value.char_indices() {
        match ch {
            '(' => depth += 1,
            ')' => depth -= 1,
            _ if depth == 0 && value[index..].starts_with(operator) => {
                return Some((&value[..index], &value[index + operator.len()..]))
            }
            _ => {}
        }
    }
    None
}
fn split_arguments(value: &str) -> Vec<&str> {
    let (mut depth, mut start) = (0, 0);
    let mut output = Vec::new();
    for (index, ch) in value.char_indices() {
        match ch {
            '(' => depth += 1,
            ')' => depth -= 1,
            ',' if depth == 0 => {
                output.push(value[start..index].trim());
                start = index + 1;
            }
            _ => {}
        }
    }
    output.push(value[start..].trim());
    output
}

#[cfg(test)]
mod tests {
    use super::super::terrain_contract::{
        bundled_complementary_hung_loified_source, derive_complementary_terrain_contract,
    };
    use super::*;
    #[test]
    fn bundled_source_derives_emission_values() {
        let source = bundled_complementary_hung_loified_source(1).unwrap();
        let contract = derive_complementary_terrain_contract(&source).unwrap();
        let table = VoxelEmissionTable::derive(&source, &contract).unwrap();
        assert_eq!([0.0; 4], table.value(59));
        assert!(table.value(10)[0] > 1.0);
        assert_eq!(4096, table.std140_bytes().len());
        assert_eq!([1.0, 0.3, 0.1, 0.0], table.tint_value(201));
        assert_eq!(VOXEL_TINT_COUNT * 16, table.tint_std140_bytes().len());
    }
}

//! Source-derived semantic material-to-voxel mapping.
//!
//! Complementary's occupancy image is not a generic "solid" mask: its
//! `GetVoxelIDs(mat)` function maps shader material ids to stable semantic
//! voxel values.  This module parses the supported, explicit rule subset from
//! shader-pack source.  It deliberately rejects unfamiliar expressions rather
//! than silently substituting an approximate mapping.

use std::collections::BTreeMap;

use crate::render::vulkanic::error::{GalError, GalResult};

use super::source::ShaderPackSource;
use super::terrain_contract::TerrainPassContract;

#[derive(Clone, Debug, Eq, PartialEq)]
enum VoxelMaterialRule {
    Exact {
        material: i32,
        voxel: u8,
    },
    RangeLinear {
        start_inclusive: i32,
        end_exclusive: i32,
        base: i32,
        divisor: i32,
        offset: i32,
    },
}

impl VoxelMaterialRule {
    fn value(&self, material: i32) -> Option<u8> {
        match *self {
            Self::Exact {
                material: expected,
                voxel,
            } if material == expected => Some(voxel),
            Self::RangeLinear {
                start_inclusive,
                end_exclusive,
                base,
                divisor,
                offset,
            } if material >= start_inclusive && material < end_exclusive => {
                let value = offset.checked_add((material - base) / divisor)?;
                u8::try_from(value).ok()
            }
            _ => None,
        }
    }
}

/// Stable source-derived lookup for the occupancy/material volume. It holds no
/// renderer object, atlas object, native handle, or backend policy.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct VoxelMaterialMap {
    shader_pack_generation: u64,
    source_path: String,
    water_material: i32,
    non_solid_less_than: i32,
    non_solid_remainder: i32,
    non_solid_divisor: i32,
    rules: Vec<VoxelMaterialRule>,
    default_voxel: u8,
}

impl VoxelMaterialMap {
    pub fn derive(source: &ShaderPackSource, contract: &TerrainPassContract) -> GalResult<Self> {
        if source.generation() != contract.generation {
            return Err(GalError::invalid_argument(
                "voxel material source generation does not match terrain contract",
            ));
        }
        let source_path = "lib/misc/voxelization.glsl";
        let text = source.get(source_path).ok_or_else(|| {
            GalError::invalid_argument("selected shader pack has no voxelization source")
        })?;
        let active = active_lines(text, &contract.property_defines)?;
        let function = function_body(&active, "GetVoxelIDs")?;
        // `UpdateVoxelMap` is compiled only for the shadow vertex stage. Its
        // admission expression is still source semantics for the owned
        // voxelizer, so inspect that function before stage-condition pruning.
        let update = function_body(text, "UpdateVoxelMap")?;
        let (water_material, non_solid_less_than, non_solid_divisor, non_solid_remainder) =
            parse_admission(&update)?;
        let (rules, default_voxel) = parse_rules(&function)?;
        if rules.is_empty() {
            return Err(GalError::invalid_argument(
                "selected shader voxel mapping contains no material rules",
            ));
        }
        Ok(Self {
            shader_pack_generation: source.generation(),
            source_path: source_path.to_string(),
            water_material,
            non_solid_less_than,
            non_solid_remainder,
            non_solid_divisor,
            rules,
            default_voxel,
        })
    }

    pub fn shader_pack_generation(&self) -> u64 {
        self.shader_pack_generation
    }

    pub fn source_path(&self) -> &str {
        &self.source_path
    }

    /// `None` means the selected source intentionally does not write this
    /// material into the occupancy volume.
    pub fn occupancy_value(&self, material: i32) -> Option<u8> {
        if material == self.water_material
            || (material < self.non_solid_less_than
                && material.rem_euclid(self.non_solid_divisor) == self.non_solid_remainder)
        {
            return None;
        }
        self.rules
            .iter()
            .find_map(|rule| rule.value(material))
            .or(Some(self.default_voxel))
    }
}

fn active_lines(source: &str, initial: &BTreeMap<String, String>) -> GalResult<String> {
    #[derive(Clone, Copy)]
    struct Frame {
        parent: bool,
        active: bool,
        seen_true: bool,
    }
    let mut defines = initial.clone();
    let mut frames = Vec::<Frame>::new();
    let mut enabled = true;
    let mut out = String::new();
    for raw in source.lines() {
        let line = raw.trim();
        let directive = line.strip_prefix('#').map(str::trim);
        if let Some(directive) = directive {
            if let Some(expr) = directive.strip_prefix("if ") {
                let parent = enabled;
                let condition = evaluate_condition(expr, &defines)?;
                let active = parent && condition;
                frames.push(Frame {
                    parent,
                    active,
                    seen_true: condition,
                });
                enabled = active;
                continue;
            }
            if let Some(name) = directive.strip_prefix("ifdef ") {
                let parent = enabled;
                let condition = defines.contains_key(name.trim());
                frames.push(Frame {
                    parent,
                    active: parent && condition,
                    seen_true: condition,
                });
                enabled = parent && condition;
                continue;
            }
            if let Some(name) = directive.strip_prefix("ifndef ") {
                let parent = enabled;
                let condition = !defines.contains_key(name.trim());
                frames.push(Frame {
                    parent,
                    active: parent && condition,
                    seen_true: condition,
                });
                enabled = parent && condition;
                continue;
            }
            if let Some(expr) = directive.strip_prefix("elif ") {
                let frame = frames
                    .last_mut()
                    .ok_or_else(|| GalError::invalid_argument("shader #elif without #if"))?;
                let condition = evaluate_condition(expr, &defines)?;
                frame.active = frame.parent && !frame.seen_true && condition;
                frame.seen_true |= condition;
                enabled = frame.active;
                continue;
            }
            if directive == "else" || directive.starts_with("else //") {
                let frame = frames
                    .last_mut()
                    .ok_or_else(|| GalError::invalid_argument("shader #else without #if"))?;
                frame.active = frame.parent && !frame.seen_true;
                frame.seen_true = true;
                enabled = frame.active;
                continue;
            }
            if directive == "endif" || directive.starts_with("endif //") {
                let frame = frames
                    .pop()
                    .ok_or_else(|| GalError::invalid_argument("shader #endif without #if"))?;
                enabled = frame.parent;
                continue;
            }
            if let Some(rest) = directive.strip_prefix("define ") {
                if enabled {
                    let mut tokens = rest.split_whitespace();
                    let key = tokens
                        .next()
                        .ok_or_else(|| GalError::invalid_argument("shader #define has no key"))?;
                    defines.insert(key.to_string(), tokens.next().unwrap_or("1").to_string());
                }
                continue;
            }
            if let Some(name) = directive.strip_prefix("undef ") {
                if enabled {
                    defines.remove(name.trim());
                }
                continue;
            }
        }
        if enabled {
            out.push_str(raw);
            out.push('\n');
        }
    }
    if !frames.is_empty() {
        return Err(GalError::invalid_argument(
            "unterminated shader conditional in voxel source",
        ));
    }
    Ok(out)
}

fn evaluate_condition(expression: &str, defines: &BTreeMap<String, String>) -> GalResult<bool> {
    let expression = trim_parens(expression.trim());
    if let Some((left, right)) = split_top(expression, "||") {
        return Ok(evaluate_condition(left, defines)? || evaluate_condition(right, defines)?);
    }
    if let Some((left, right)) = split_top(expression, "&&") {
        return Ok(evaluate_condition(left, defines)? && evaluate_condition(right, defines)?);
    }
    if let Some(rest) = expression.strip_prefix('!') {
        return Ok(!evaluate_condition(rest, defines)?);
    }
    let name = expression
        .strip_prefix("defined(")
        .and_then(|value| value.strip_suffix(')'))
        .or_else(|| expression.strip_prefix("defined "))
        .map(str::trim);
    if let Some(name) = name {
        return Ok(defines.contains_key(name));
    }
    if let Ok(value) = expression.parse::<i64>() {
        return Ok(value != 0);
    }
    Ok(defines
        .get(expression)
        .map(|value| value != "0")
        .unwrap_or(false))
}

fn trim_parens(mut value: &str) -> &str {
    while value.starts_with('(') && value.ends_with(')') {
        value = value[1..value.len() - 1].trim();
    }
    value
}

fn split_top<'a>(value: &'a str, operator: &str) -> Option<(&'a str, &'a str)> {
    let mut depth = 0_i32;
    for (index, character) in value.char_indices() {
        match character {
            '(' => depth += 1,
            ')' => depth -= 1,
            _ if depth == 0 && value[index..].starts_with(operator) => {
                return Some((&value[..index], &value[index + operator.len()..]));
            }
            _ => {}
        }
    }
    None
}

fn function_body(source: &str, name: &str) -> GalResult<String> {
    let signature = format!("int {name}(");
    let start = source
        .find(&signature)
        .or_else(|| source.find(&format!("void {name}(")))
        .ok_or_else(|| {
            GalError::invalid_argument(format!("selected shader source has no {name} function"))
        })?;
    let body_start = source[start..]
        .find('{')
        .map(|offset| start + offset)
        .ok_or_else(|| {
            GalError::invalid_argument(format!("selected shader {name} function has no body"))
        })?;
    let mut depth = 0_i32;
    for (offset, character) in source[body_start..].char_indices() {
        match character {
            '{' => depth += 1,
            '}' => {
                depth -= 1;
                if depth == 0 {
                    return Ok(source[body_start + 1..body_start + offset].to_string());
                }
            }
            _ => {}
        }
    }
    Err(GalError::invalid_argument(format!(
        "selected shader {name} function is unterminated"
    )))
}

fn parse_admission(body: &str) -> GalResult<(i32, i32, i32, i32)> {
    let compact = body.split_whitespace().collect::<String>();
    let water_start = compact.find("mat==").ok_or_else(|| {
        GalError::invalid_argument("unsupported voxel water admission expression")
    })?;
    let water = parse_number_after(&compact[water_start..], "mat==")?;
    let marker = "mat<";
    let start = compact.find(marker).ok_or_else(|| {
        GalError::invalid_argument("unsupported voxel non-solid admission expression")
    })?;
    let threshold_start = start + marker.len();
    let threshold_end = compact[threshold_start..]
        .find("&&mat%")
        .map(|offset| threshold_start + offset)
        .ok_or_else(|| {
            GalError::invalid_argument("unsupported voxel non-solid admission expression")
        })?;
    let threshold = compact[threshold_start..threshold_end]
        .parse::<i32>()
        .map_err(|_| GalError::invalid_argument("invalid voxel non-solid threshold"))?;
    let divisor_start = threshold_end + "&&mat%".len();
    let divisor_end = compact[divisor_start..]
        .find("==")
        .map(|offset| divisor_start + offset)
        .ok_or_else(|| {
            GalError::invalid_argument("unsupported voxel non-solid admission expression")
        })?;
    let divisor = compact[divisor_start..divisor_end]
        .parse::<i32>()
        .map_err(|_| GalError::invalid_argument("invalid voxel non-solid divisor"))?;
    let remainder = parse_number_after(&compact[divisor_end..], "==")?;
    if divisor <= 0 {
        return Err(GalError::invalid_argument(
            "voxel non-solid divisor must be positive",
        ));
    }
    Ok((water, threshold, divisor, remainder))
}

fn parse_rules(body: &str) -> GalResult<(Vec<VoxelMaterialRule>, u8)> {
    let compact = body.split_whitespace().collect::<String>();
    let mut rules = Vec::new();
    let mut rest = compact.as_str();
    while let Some(position) = rest.find("if(mat==") {
        rest = &rest[position + "if(mat==".len()..];
        let material_end = rest
            .find(')')
            .ok_or_else(|| GalError::invalid_argument("malformed voxel material equality rule"))?;
        let material = rest[..material_end]
            .parse::<i32>()
            .map_err(|_| GalError::invalid_argument("invalid voxel material id"))?;
        let return_start = material_end + 1;
        let prefix = "return";
        if !rest[return_start..].starts_with(prefix) {
            rest = &rest[return_start..];
            continue;
        }
        let value = parse_number_after(&rest[return_start..], prefix)?;
        rules.push(VoxelMaterialRule::Exact {
            material,
            voxel: u8::try_from(value)
                .map_err(|_| GalError::invalid_argument("voxel value does not fit r8ui"))?,
        });
        rest = &rest[return_start..];
    }
    // Complementary's stained-glass range is an arithmetic source rule rather
    // than a large duplicated table. Parse it semantically, not as a pack id.
    let range_start = compact.match_indices("if(mat>=").find_map(|(start, _)| {
        let candidate = &compact[start + "if(mat>=".len()..];
        let separator = candidate.find("&&mat<")?;
        let after_separator = candidate.as_bytes().get(separator + "&&mat<".len())?;
        (*after_separator != b'=').then_some(start)
    });
    if let Some(range_start) = range_start {
        let range = &compact[range_start + "if(mat>=".len()..];
        let start_end = range
            .find("&&mat<")
            .ok_or_else(|| GalError::invalid_argument("unsupported voxel range rule"))?;
        let start_inclusive = range[..start_end]
            .parse::<i32>()
            .map_err(|_| GalError::invalid_argument("invalid voxel range start"))?;
        let range = &range[start_end + "&&mat<".len()..];
        let end_end = range
            .find(')')
            .ok_or_else(|| GalError::invalid_argument("unsupported voxel range end"))?;
        let end_exclusive = range[..end_end]
            .parse::<i32>()
            .map_err(|_| GalError::invalid_argument("invalid voxel range end"))?;
        let expression = range[end_end..]
            .strip_prefix(")return")
            .ok_or_else(|| GalError::invalid_argument("unsupported voxel range return"))?;
        let (offset_text, remainder) = expression
            .split_once("+(mat-")
            .ok_or_else(|| GalError::invalid_argument("unsupported voxel range expression"))?;
        let offset = offset_text
            .parse::<i32>()
            .map_err(|_| GalError::invalid_argument("invalid voxel range offset"))?;
        let (base_text, _divisor_text) = remainder
            .split_once(")/")
            .ok_or_else(|| GalError::invalid_argument("unsupported voxel range expression"))?;
        let base = base_text
            .parse::<i32>()
            .map_err(|_| GalError::invalid_argument("invalid voxel range base"))?;
        let divisor_end = remainder
            .find(';')
            .ok_or_else(|| GalError::invalid_argument("unterminated voxel range expression"))?;
        let divisor = remainder[base_text.len() + 2..divisor_end]
            .parse::<i32>()
            .map_err(|_| GalError::invalid_argument("invalid voxel range divisor"))?;
        if divisor <= 0 {
            return Err(GalError::invalid_argument(
                "voxel range divisor must be positive",
            ));
        }
        rules.push(VoxelMaterialRule::RangeLinear {
            start_inclusive,
            end_exclusive,
            base,
            divisor,
            offset,
        });
    }
    let default_pos = compact.rfind("return").ok_or_else(|| {
        GalError::invalid_argument("selected shader voxel mapping has no default return")
    })?;
    let default_voxel = parse_number_after(&compact[default_pos..], "return")?;
    Ok((
        rules,
        u8::try_from(default_voxel)
            .map_err(|_| GalError::invalid_argument("voxel default does not fit r8ui"))?,
    ))
}

fn parse_number_after(value: &str, prefix: &str) -> GalResult<i32> {
    let rest = value
        .strip_prefix(prefix)
        .ok_or_else(|| GalError::invalid_argument("missing numeric shader expression"))?;
    let digits = rest
        .chars()
        .take_while(|character| character.is_ascii_digit() || *character == '-')
        .collect::<String>();
    digits
        .parse::<i32>()
        .map_err(|_| GalError::invalid_argument("invalid numeric shader expression"))
}

#[cfg(test)]
mod tests {
    use super::super::source::ShaderSourceFile;
    use super::super::terrain_contract::{
        TerrainPassContract, TerrainPassInput, TerrainPassOperation, TerrainPassOutput,
    };
    use super::*;
    use std::collections::BTreeSet;

    fn contract() -> TerrainPassContract {
        TerrainPassContract {
            pack_name: "test".to_string(),
            generation: 7,
            program_path: "unused".to_string(),
            material_classes: BTreeSet::new(),
            inputs: BTreeSet::from([TerrainPassInput::ColoredVoxelLightVolume]),
            outputs: BTreeSet::from([TerrainPassOutput::LitTerrainColor]),
            property_defines: BTreeMap::from([("ENABLE_LIGHT".to_string(), "1".to_string())]),
            material_ids: BTreeMap::new(),
            operations: vec![TerrainPassOperation::ColoredVoxelLighting],
            required_resources: BTreeSet::new(),
            voxel_light_volume_requirements: None,
            unsupported: BTreeSet::new(),
        }
    }

    #[test]
    fn derives_selected_rules_and_rejects_non_solids() {
        let source = ShaderPackSource::new("test", 7, vec![ShaderSourceFile::new("lib/misc/voxelization.glsl", r#"
            int GetVoxelIDs(int mat) {
            #if defined ENABLE_LIGHT
                if (mat == 42) return 7;
            #endif
                if (mat >= 31000 && mat < 32000) return 200 + (mat - 31000) / 2;
                return 1;
            }
            void UpdateVoxelMap(int mat) { if (mat == 32000 || mat < 30000 && mat % 4 == 1) return; }
        "#)]).unwrap();
        let map = VoxelMaterialMap::derive(&source, &contract()).unwrap();
        assert_eq!(Some(7), map.occupancy_value(42));
        assert_eq!(Some(201), map.occupancy_value(31002));
        assert_eq!(Some(1), map.occupancy_value(10000));
        assert_eq!(None, map.occupancy_value(32000));
        assert_eq!(None, map.occupancy_value(10001));
    }

    #[test]
    fn bundled_complementary_mapping_keeps_source_voxel_ids() {
        let source =
            super::super::terrain_contract::bundled_complementary_hung_loified_source(1).unwrap();
        let contract =
            super::super::terrain_contract::derive_complementary_terrain_contract(&source).unwrap();
        let map = VoxelMaterialMap::derive(&source, &contract).unwrap();
        assert_eq!(Some(255), map.occupancy_value(10228)); // Bedrock
        assert_eq!(Some(10), map.occupancy_value(10412)); // Glowstone
        assert_eq!(Some(254), map.occupancy_value(30008)); // Tinted glass
        assert_eq!(Some(216), map.occupancy_value(32004)); // Ice
        assert_eq!(Some(200), map.occupancy_value(31000)); // Stained glass range
        assert_eq!(None, map.occupancy_value(32000)); // Water
    }
}

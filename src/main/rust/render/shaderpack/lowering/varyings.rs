//! Varying declaration parsing and location assignment.

use super::*;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(super) enum VaryingStorage {
    In,
    Out,
}

impl VaryingStorage {
    pub(super) fn keyword(self) -> &'static str {
        match self {
            Self::In => "in",
            Self::Out => "out",
        }
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub(super) struct SourceVaryingDeclaration {
    pub(super) name: String,
    pub(super) type_name: String,
    pub(super) interpolation: String,
}

/// Derives the exact simple field interface used by a vertex/fragment pair.
/// Missing or incompatible fragment inputs fail source preparation rather than
/// relying on compiler auto-assignment or an implicit legacy link convention.
pub fn derive_terrain_source_varying_contract(
    vertex: &PreprocessedShaderSource,
    fragment: &PreprocessedShaderSource,
) -> GalResult<TerrainSourceVaryingContract> {
    derive_simple_varying_contract(vertex.expanded_source(), fragment.expanded_source())
}

/// Shared source-level interface linking. No runtime renderer state is used.
pub(crate) fn bind_simple_paired_varyings(
    vertex: &str,
    fragment: &str,
) -> GalResult<(String, String)> {
    let contract = derive_simple_varying_contract(vertex, fragment)?;
    if contract.fields.iter().any(|field| {
        !matches!(
            field.type_name.as_str(),
            "float"
                | "vec2"
                | "vec3"
                | "vec4"
                | "int"
                | "ivec2"
                | "ivec3"
                | "ivec4"
                | "uint"
                | "uvec2"
                | "uvec3"
                | "uvec4"
        )
    }) {
        return Err(GalError::unsupported_feature(
            "post-effect varying requires a multi-location interface contract",
        ));
    }
    for (source, storage) in [
        (vertex, VaryingStorage::Out),
        (fragment, VaryingStorage::In),
    ] {
        if !collect_stage_varyings(source, storage)?.is_empty()
            && source.lines().any(|line| {
                line.trim().starts_with("layout")
                    && line.contains(&format!(" {} ", storage.keyword()))
            })
        {
            return Err(GalError::unsupported_feature(
                "post-effect mixed explicit and implicit varying locations are unavailable",
            ));
        }
    }
    Ok((
        apply_varying_locations(vertex, VaryingStorage::Out, &contract)?,
        apply_varying_locations(fragment, VaryingStorage::In, &contract)?,
    ))
}

pub(super) fn derive_simple_varying_contract(
    vertex: &str,
    fragment: &str,
) -> GalResult<TerrainSourceVaryingContract> {
    let mut vertex_outputs = BTreeMap::new();
    for field in collect_stage_varyings(vertex, VaryingStorage::Out)? {
        insert_stage_varying(&mut vertex_outputs, field, "vertex output")?;
    }
    let mut fragment_inputs = BTreeMap::new();
    for field in collect_stage_varyings(fragment, VaryingStorage::In)? {
        insert_stage_varying(&mut fragment_inputs, field, "fragment input")?;
    }

    if glsl_identifiers(vertex).contains("gl_FogFragCoord")
        || glsl_identifiers(fragment).contains("gl_FogFragCoord") {
        let field = SourceVaryingDeclaration {
            name: "vulkanic_source_fog_frag_coord".to_owned(),
            type_name: "float".to_owned(), interpolation: String::new(),
        };
        insert_stage_varying(&mut vertex_outputs, field.clone(), "legacy fog output")?;
        if glsl_identifiers(fragment).contains("gl_FogFragCoord") {
            insert_stage_varying(&mut fragment_inputs, field, "legacy fog input")?;
        }
    }

    for (name, fragment_field) in &fragment_inputs {
        let Some(vertex_field) = vertex_outputs.get(name) else {
            return Err(GalError::invalid_argument(format!(
                "terrain fragment input '{name}' has no matching vertex output"
            )));
        };
        if vertex_field.type_name != fragment_field.type_name
            || vertex_field.interpolation != fragment_field.interpolation
        {
            return Err(GalError::invalid_argument(format!(
                "terrain varying '{name}' differs between vertex output ('{} {}') and fragment input ('{} {}')",
                vertex_field.interpolation,
                vertex_field.type_name,
                fragment_field.interpolation,
                fragment_field.type_name,
            )));
        }
    }

    let mut fields = Vec::with_capacity(vertex_outputs.len());
    let mut location = 0u32;
    for field in vertex_outputs.into_values() {
        let slots = varying_location_count(&field.type_name)?;
        let next = location.checked_add(slots).ok_or_else(||
            GalError::invalid_argument("source varying location count overflow"))?;
        fields.push(TerrainSourceVaryingField {
            name: field.name, type_name: field.type_name,
            interpolation: field.interpolation, location,
        });
        location = next;
    }
    Ok(TerrainSourceVaryingContract { fields })
}

fn varying_location_count(type_name: &str) -> GalResult<u32> {
    match type_name {
        "float" | "vec2" | "vec3" | "vec4" | "int" | "ivec2" | "ivec3" | "ivec4"
        | "uint" | "uvec2" | "uvec3" | "uvec4" => Ok(1),
        // Matrix varyings occupy one location per column, independently of
        // their row count. Later fields must not overlap those locations.
        "mat2" | "mat2x2" | "mat2x3" | "mat2x4" => Ok(2),
        "mat3" | "mat3x2" | "mat3x3" | "mat3x4" => Ok(3),
        "mat4" | "mat4x2" | "mat4x3" | "mat4x4" => Ok(4),
        _ => Err(GalError::unsupported_feature(format!(
            "source varying type '{type_name}' has no explicit location contract"
        ))),
    }
}

pub(super) fn insert_stage_varying(
    fields: &mut BTreeMap<String, SourceVaryingDeclaration>,
    field: SourceVaryingDeclaration,
    stage: &str,
) -> GalResult<()> {
    match fields.get(&field.name) {
        Some(existing) if existing != &field => Err(GalError::invalid_argument(format!(
            "terrain {stage} '{}' has incompatible repeated declarations",
            field.name
        ))),
        Some(_) => Ok(()),
        None => {
            fields.insert(field.name.clone(), field);
            Ok(())
        }
    }
}

pub(super) fn collect_stage_varyings(
    source: &str,
    storage: VaryingStorage,
) -> GalResult<Vec<SourceVaryingDeclaration>> {
    let mut fields = Vec::new();
    let stripped = crate::render::shaderpack::source::dialect::strip_comments(source);
    for line in stripped.lines() {
        let Some(declarations) = parse_varying_declaration(line.trim(), storage)? else {
            continue;
        };
        fields.extend(declarations);
    }
    Ok(fields)
}

pub(super) fn parse_varying_declaration(
    line: &str,
    storage: VaryingStorage,
) -> GalResult<Option<Vec<SourceVaryingDeclaration>>> {
    if !line.ends_with(';') || line.contains('(') || line.starts_with("layout")
        || line.trim_end_matches(';').contains(';')
    {
        return Ok(None);
    }
    let words = line
        .trim_end_matches(';')
        .split_whitespace()
        .collect::<Vec<_>>();
    // Legacy `varying` is an output in the vertex stage and an input in
    // the fragment stage. Discover it before either stage is rewritten so
    // both receive the same explicit locations and type checks.
    let Some(storage_index) = words.iter().position(|word|
        *word == storage.keyword() || *word == "varying"
    ) else {
        return Ok(None);
    };
    if storage_index > 1 || words.len() < storage_index + 3 {
        return Ok(None);
    }
    let interpolation = words[..storage_index].join(" ");
    if !interpolation.is_empty()
        && !matches!(interpolation.as_str(), "flat" | "smooth" | "noperspective")
    {
        return Err(GalError::unsupported_feature(format!(
            "terrain {} varying uses unsupported interpolation qualifier '{interpolation}'",
            storage.keyword()
        )));
    }
    let type_name = words[storage_index + 1];
    let names = words[storage_index + 2..].join(" ");
    if names.contains('[') || names.contains(']') {
        return Err(GalError::unsupported_feature(
            "terrain varying arrays need an explicit multi-location contract",
        ));
    }
    let mut declarations = Vec::new();
    for name in names.split(',').map(str::trim) {
        if name.is_empty() || !valid_identifier(name) {
            return Err(GalError::invalid_argument(format!(
                "terrain {} varying has invalid field name '{name}'",
                storage.keyword()
            )));
        }
        declarations.push(SourceVaryingDeclaration {
            name: name.to_string(),
            type_name: type_name.to_string(),
            interpolation: interpolation.clone(),
        });
    }
    Ok(Some(declarations))
}

pub(super) fn apply_varying_locations(
    source: &str,
    storage: VaryingStorage,
    contract: &TerrainSourceVaryingContract,
) -> GalResult<String> {
    let mut output = String::with_capacity(source.len());
    let stripped = crate::render::shaderpack::source::dialect::strip_comments(source);
    for (line, semantic_line) in source.lines().zip(stripped.lines()) {
        let Some(fields) = parse_varying_declaration(semantic_line.trim(), storage)? else {
            output.push_str(line);
            output.push('\n');
            continue;
        };
        if contract.location_for(&fields[0].name).is_none() {
            output.push_str(line);
            output.push('\n');
            continue;
        }
        let mut declarations = String::new();
        for field in fields {
            let Some(location) = contract.location_for(&field.name) else {
                return Err(GalError::invalid_argument(format!(
                    "terrain {} varying '{}' is absent from the paired contract",
                    storage.keyword(),
                    field.name
                )));
            };
            if field.interpolation.is_empty() {
                declarations.push_str(&format!(
                    "layout(location = {location}) {} {} {};\n",
                    storage.keyword(),
                    field.type_name,
                    field.name
                ));
            } else {
                declarations.push_str(&format!(
                    "layout(location = {location}) {} {} {} {};\n",
                    field.interpolation,
                    storage.keyword(),
                    field.type_name,
                    field.name
                ));
            }
        }
        append_rewritten_declaration_line(&mut output, line, semantic_line, declarations.trim_end());
    }
    if let Some(location) = contract.location_for("vulkanic_source_fog_frag_coord") {
        if storage == VaryingStorage::Out || glsl_identifiers(source).contains("gl_FogFragCoord") {
            output = replace_identifier(&output, "gl_FogFragCoord", "vulkanic_source_fog_frag_coord");
            output = insert_after_version(&output, &format!(
                "layout(location = {location}) {} float vulkanic_source_fog_frag_coord;\n", storage.keyword()))?;
            if storage == VaryingStorage::Out {
                // Frozen CommonTransformer initializes the implicit output
                // before pack main; the pack's own distance writes survive.
                let (opening, _) = text::main_function_body_range(&output)
                    .ok_or_else(|| GalError::invalid_argument("legacy fog output requires a vertex main body"))?;
                output.insert_str(opening + 1, "\n    vulkanic_source_fog_frag_coord = 0.0;\n");
            }
        }
    }
    Ok(output)
}

pub(super) fn valid_identifier(name: &str) -> bool {
    name.bytes()
        .next()
        .is_some_and(|byte| byte == b'_' || byte.is_ascii_alphabetic())
        && name.bytes().all(is_identifier_byte)
}

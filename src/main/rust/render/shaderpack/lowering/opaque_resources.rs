//! Opaque (sampler/image/storage) resource declaration parsing and binding.

use super::*;

#[derive(Clone, Debug, Eq, PartialEq)]
pub(super) struct SourceOpaqueResourceDeclaration {
    pub(super) name: String,
    pub(super) type_name: String,
    pub(super) qualifiers: String,
    pub(super) kind: TerrainSourceOpaqueResourceKind,
    pub(super) active: bool,
}

/// Derives one deterministic table for source-declared samplers/images across
/// both stages. Explicitly unsupported declaration forms fail rather than
/// falling back to compiler-assigned bindings.
pub fn derive_terrain_source_opaque_resource_contract(
    vertex: &PreprocessedShaderSource,
    fragment: &PreprocessedShaderSource,
) -> GalResult<TerrainSourceOpaqueResourceContract> {
    let mut resources = BTreeMap::<String, SourceOpaqueResourceDeclaration>::new();
    for source in [vertex, fragment] {
        for resource in collect_opaque_resources(source.expanded_source())? {
            if let Some(existing) = resources.get(&resource.name) {
                if existing.type_name != resource.type_name
                    || existing.qualifiers != resource.qualifiers
                    || existing.kind != resource.kind
                {
                    return Err(GalError::invalid_argument(format!(
                        "terrain opaque resource '{}' has incompatible paired declarations",
                        resource.name
                    )));
                }
                let active = existing.active || resource.active;
                resources
                    .get_mut(&resource.name)
                    .expect("opaque resource entry disappeared during paired lowering")
                    .active = active;
            } else {
                resources.insert(resource.name.clone(), resource);
            }
        }
    }
    Ok(TerrainSourceOpaqueResourceContract {
        resources: resources
            .into_values()
            .enumerate()
            .map(|(binding, resource)| TerrainSourceOpaqueResource {
                name: resource.name,
                type_name: resource.type_name,
                qualifiers: resource.qualifiers,
                kind: resource.kind,
                binding: binding as u32,
                active: resource.active,
            })
            .collect(),
    })
}

pub(super) fn collect_opaque_resources(source: &str) -> GalResult<Vec<SourceOpaqueResourceDeclaration>> {
    let stripped = crate::render::shaderpack::source::dialect::strip_comments(source);
    let source = stripped.as_str();
    let source_without_declarations = strip_opaque_resource_declarations(source)?;
    let referenced = glsl_identifiers(&source_without_declarations);
    let mut resources = Vec::new();
    for line in source.lines() {
        let Some(mut resource) = parse_opaque_resource_declaration(line.trim())? else {
            continue;
        };
        resource.active = referenced.contains(&resource.name);
        resources.push(resource);
    }
    Ok(resources)
}

/// Removes opaque declarations before the bounded reference scan so a global
/// shader header cannot make a sampler appear required merely by declaring
/// it. Active preprocessor definitions remain in the source and therefore
/// count as uses conservatively, exactly as scalar-uniform collection does.
pub(super) fn strip_opaque_resource_declarations(source: &str) -> GalResult<String> {
    let stripped = crate::render::shaderpack::source::dialect::strip_comments(source);
    let mut output = String::with_capacity(source.len());
    for (line, semantic_line) in source.lines().zip(stripped.lines()) {
        if parse_opaque_resource_declaration(semantic_line.trim())?.is_some() {
            append_rewritten_declaration_line(&mut output, line, semantic_line, "");
            continue;
        }
        output.push_str(line);
        output.push('\n');
    }
    Ok(output)
}

pub(super) fn parse_opaque_resource_declaration(
    line: &str,
) -> GalResult<Option<SourceOpaqueResourceDeclaration>> {
    if !line.ends_with(';') || line.contains('(') {
        return Ok(None);
    }
    if line.starts_with("layout") {
        if line.contains("sampler") || line.contains("image") {
            return Err(GalError::unsupported_feature(
                "terrain opaque resources with pre-existing layouts need an explicit source layout contract",
            ));
        }
        return Ok(None);
    }
    let words = line
        .trim_end_matches(';')
        .split_whitespace()
        .collect::<Vec<_>>();
    let Some(uniform_index) = words.iter().position(|word| *word == "uniform") else {
        return Ok(None);
    };
    if !words[..uniform_index].iter().all(|qualifier| {
        matches!(
            *qualifier,
            "readonly" | "writeonly" | "coherent" | "volatile" | "restrict"
        )
    }) {
        return Ok(None);
    }
    if words.len() != uniform_index + 3 || line.contains(',') || line.contains('[') {
        return Ok(None);
    }
    let type_name = words[uniform_index + 1];
    let kind = if type_name.contains("sampler") {
        TerrainSourceOpaqueResourceKind::CombinedTextureSampler
    } else if type_name.contains("image") {
        TerrainSourceOpaqueResourceKind::StorageImage
    } else {
        return Ok(None);
    };
    let name = words[uniform_index + 2];
    if !valid_identifier(name) {
        return Err(GalError::invalid_argument(format!(
            "terrain opaque resource has invalid name '{name}'"
        )));
    }
    let qualifiers = words[..uniform_index].join(" ");
    if !qualifiers.is_empty()
        && !qualifiers.split_whitespace().all(|qualifier| {
            matches!(
                qualifier,
                "readonly" | "writeonly" | "coherent" | "volatile" | "restrict"
            )
        })
    {
        return Err(GalError::unsupported_feature(format!(
            "terrain opaque resource '{name}' has unsupported qualifiers '{qualifiers}'"
        )));
    }
    Ok(Some(SourceOpaqueResourceDeclaration {
        name: name.to_string(),
        type_name: type_name.to_string(),
        qualifiers,
        kind,
        active: false,
    }))
}

pub(super) fn apply_opaque_resource_bindings(
    source: &str,
    contract: &TerrainSourceOpaqueResourceContract,
) -> GalResult<String> {
    let stripped = crate::render::shaderpack::source::dialect::strip_comments(source);
    let mut output = String::with_capacity(source.len());
    for (line, semantic_line) in source.lines().zip(stripped.lines()) {
        let Some(declaration) = parse_opaque_resource_declaration(semantic_line.trim())? else {
            output.push_str(line);
            output.push('\n');
            continue;
        };
        let Some(resource) = contract.resource_for(&declaration.name) else {
            return Err(GalError::invalid_argument(format!(
                "terrain opaque resource '{}' is absent from the paired contract",
                declaration.name
            )));
        };
        let replacement = if declaration.qualifiers.is_empty() {
            format!(
                "layout(set = 1, binding = {}) uniform {} {};",
                resource.binding, resource.type_name, resource.name
            )
        } else {
            format!(
                "layout(set = 1, binding = {}) {} uniform {} {};",
                resource.binding, resource.qualifiers, resource.type_name, resource.name
            )
        };
        append_rewritten_declaration_line(&mut output, line, semantic_line, &replacement);
    }
    Ok(output)
}

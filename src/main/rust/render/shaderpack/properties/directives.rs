//! Bounded directive traversal in Frozen ProgramSet order.

use super::super::contracts::terrain::TerrainProgramScope;
use super::super::source::{
    preprocess::{preprocess_artifact_with_runtime_options, PreprocessedShaderSource},
    ShaderPackSource,
};
use crate::render::vulkanic::error::{GalError, GalResult};

#[derive(Default)]
pub(crate) struct FragmentDirectiveScan {
    pub fragment_count: usize,
    pub property_defines: Vec<(String, String)>,
}

pub(crate) fn scope_prefix(source: &ShaderPackSource, scope: TerrainProgramScope) -> &'static str {
    let requested = match scope {
        TerrainProgramScope::Default => "",
        TerrainProgramScope::Overworld => "world0/",
        TerrainProgramScope::Nether => "world-1/",
        TerrainProgramScope::End => "world1/",
    };
    if source.paths().any(|path| path.starts_with(requested)) {
        requested
    } else {
        ""
    }
}

pub(crate) fn visit_fragment_directives(
    source: &ShaderPackSource,
    scope: TerrainProgramScope,
    compact_common: bool,
    mut visit: impl FnMut(&PreprocessedShaderSource) -> GalResult<()>,
) -> GalResult<FragmentDirectiveScan> {
    let prefix = scope_prefix(source, scope);
    let mut scan = FragmentDirectiveScan::default();
    let mut bytes = 0usize;
    for name in fragment_program_order() {
        let path = format!("{prefix}{name}.fsh");
        if source.get(&path).is_none() {
            continue;
        }
        let artifact = preprocess_artifact_with_runtime_options(source, &path, &[])?;
        bytes = bytes
            .checked_add(artifact.expanded_source().len())
            .ok_or_else(|| GalError::invalid_argument("pack directive byte count overflow"))?;
        if bytes > 128 * 1024 * 1024 {
            return Err(GalError::invalid_argument(
                "pack directive expansion exceeds byte budget",
            ));
        }
        visit(&artifact)?;
        scan.fragment_count += 1;
        scan.property_defines = artifact.defines().to_vec();
    }
    if scan.fragment_count == 0
        && compact_common
        && !source.paths().any(|path| path.ends_with(".fsh"))
        && source.get("lib/common.glsl").is_some()
    {
        let artifact = preprocess_artifact_with_runtime_options(source, "lib/common.glsl", &[])?;
        visit(&artifact)?;
        scan.property_defines = artifact.defines().to_vec();
    }
    Ok(scan)
}

pub(crate) fn resolved_properties(
    source: &ShaderPackSource,
    defines: &[(String, String)],
) -> GalResult<Option<PreprocessedShaderSource>> {
    if source.get("shaders.properties").is_none() {
        return Ok(None);
    }
    let references = defines
        .iter()
        .map(|(key, value)| (key.as_str(), value.as_str()))
        .collect::<Vec<_>>();
    preprocess_artifact_with_runtime_options(source, "shaders.properties", &references).map(Some)
}

/// Resolve aliases left in normal GLSL text by the Rust preprocessor.
pub(crate) fn constant_alias<'a>(
    mut value: &'a str,
    defines: &'a [(String, String)],
) -> GalResult<&'a str> {
    for _ in 0..32 {
        let Some((_, replacement)) = defines.iter().find(|(key, _)| key == value) else {
            return Ok(value);
        };
        value = replacement.trim();
    }
    Err(GalError::invalid_argument(
        "pack directive constant alias exceeds bounded depth",
    ))
}

fn fragment_program_order() -> Vec<String> {
    let mut names = Vec::new();
    fn array(names: &mut Vec<String>, prefix: &str) {
        for index in 0..100 {
            names.push(if index == 0 {
                prefix.to_string()
            } else {
                format!("{prefix}{index}")
            });
        }
    }
    for prefix in ["shadowcomp", "begin", "prepare"] {
        array(&mut names, prefix);
    }
    // Frozen ProgramId declaration order, including the final program before
    // deferred/composite. Setup/compute/vertex files are not pack directives.
    names.extend(
        [
            "shadow",
            "shadow_solid",
            "shadow_cutout",
            "shadow_water",
            "shadow_entities",
            "shadow_lightning",
            "shadow_block",
            "gbuffers_basic",
            "gbuffers_line",
            "gbuffers_textured",
            "gbuffers_textured_lit",
            "gbuffers_skybasic",
            "gbuffers_skytextured",
            "gbuffers_clouds",
            "gbuffers_terrain",
            "gbuffers_terrain_solid",
            "gbuffers_terrain_cutout",
            "gbuffers_damagedblock",
            "gbuffers_block",
            "gbuffers_block_translucent",
            "gbuffers_beaconbeam",
            "gbuffers_item",
            "gbuffers_entities",
            "gbuffers_entities_translucent",
            "gbuffers_lightning",
            "gbuffers_particles",
            "gbuffers_particles_translucent",
            "gbuffers_entities_glowing",
            "gbuffers_armor_glint",
            "gbuffers_spidereyes",
            "gbuffers_hand",
            "gbuffers_weather",
            "gbuffers_water",
            "gbuffers_hand_water",
            "dh_terrain",
            "dh_water",
            "dh_generic",
            "dh_shadow",
            "final",
        ]
        .into_iter()
        .map(str::to_string),
    );
    for prefix in ["deferred", "composite"] {
        array(&mut names, prefix);
    }
    names
}

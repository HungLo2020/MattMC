//! Standard Iris source aliases, before any GAL resource is created.

use super::*;
use crate::render::shaderpack::contracts::terrain::TerrainSourceStage;
use crate::render::shaderpack::source::preprocess::{
    preprocess_artifact_with_runtime_options, PreprocessedShaderSource,
};

const COLOR_NAMES: [&str; 8] = [
    "primary",
    "secondary",
    "auxiliary_c",
    "auxiliary_d",
    "auxiliary_e",
    "auxiliary_f",
    "auxiliary_g",
    "auxiliary_h",
];
const COLOR_ALIASES: [&str; 8] = [
    "gcolor",
    "gdepth",
    "gnormal",
    "composite",
    "gaux1",
    "gaux2",
    "gaux3",
    "gaux4",
];

impl TerrainSourceResourceBindings {
    fn protocol_bindings(source: &ShaderPackSource) -> Self {
        // A raw arbitrary GLSL pair has no implicit Minecraft material domain.
        // Conventional pack entry points establish the source-language protocol.
        if !source.paths().any(|path| {
            matches!(path.rsplit('/').next(), Some("gbuffers_terrain.fsh"))
                && source.get(&path.replace(".fsh", ".vsh")).is_some()
        }) {
            return Self::default();
        }
        let mut bindings = BTreeMap::new();
        for (name, role) in [
            ("tex", TerrainSourceResourceRole::MaterialAtlas),
            ("texture", TerrainSourceResourceRole::MaterialAtlas),
            ("gtexture", TerrainSourceResourceRole::MaterialAtlas),
            ("normals", TerrainSourceResourceRole::MaterialNormalMap),
            ("specular", TerrainSourceResourceRole::MaterialSpecularMap),
            ("lightmap", TerrainSourceResourceRole::Lightmap),
            ("noisetex", TerrainSourceResourceRole::Noise),
            ("shadowtex0", TerrainSourceResourceRole::ShadowDepthPrimary),
            (
                "shadowtex1",
                TerrainSourceResourceRole::ShadowDepthSecondary,
            ),
            ("shadowcolor", TerrainSourceResourceRole::ShadowColor),
            ("shadowcolor0", TerrainSourceResourceRole::ShadowColor),
            (
                "shadowcolor1",
                TerrainSourceResourceRole::ShadowColorSecondary,
            ),
            ("depthtex0", TerrainSourceResourceRole::MainDepth),
            ("gdepthtex", TerrainSourceResourceRole::MainDepth),
            (
                "depthtex1",
                TerrainSourceResourceRole::MainDepthBeforeTranslucency,
            ),
            (
                "dhDepthTex",
                TerrainSourceResourceRole::DistantHorizonsOpaqueDepth,
            ),
            (
                "dhDepthTex1",
                TerrainSourceResourceRole::DistantHorizonsDepthBeforeTranslucency,
            ),
        ] {
            bindings.insert(name.to_owned(), role);
        }
        for (slot, name) in COLOR_NAMES.iter().enumerate() {
            let role = TerrainSourceResourceRole::ShaderPackColor((*name).to_owned());
            bindings.insert(format!("colortex{slot}"), role.clone());
            bindings.insert(COLOR_ALIASES[slot].to_owned(), role);
        }
        Self {
            bindings,
            color_outputs: COLOR_NAMES
                .iter()
                .enumerate()
                .map(|(slot, name)| {
                    (
                        slot as u32,
                        TerrainSourceResourceRole::ShaderPackColor((*name).to_owned()),
                    )
                })
                .collect(),
        }
    }

    pub(super) fn from_legacy_source(source: &ShaderPackSource) -> GalResult<Self> {
        let mut result = Self::protocol_bindings(source);
        result.restrict_geometry_colors();
        // A pack-wide table cannot decide conditional pass-specific custom
        // textures. Remove those names until a preprocessed stage resolves
        // them, rather than accidentally sampling the ordinary color target.
        if let Some(properties) = source.get("shaders.properties") {
            for line in properties.lines() {
                if let Some((key, _)) = line.trim().split_once('=') {
                    if let Some(rest) = key.trim().strip_prefix("texture.") {
                        if let Some((phase, name)) = rest.split_once('.') {
                            if matches!(phase, "gbuffers" | "shadow" | "begin" | "prepare" | "deferred" | "composite") {
                                result
                                    .bindings
                                    .remove(name.split('.').next().unwrap_or(name));
                            }
                        }
                    }
                }
            }
        }
        Ok(result)
    }

    fn restrict_geometry_colors(&mut self) {
        // IrisSamplers.addRenderTargetSamplers begins at slot four for
        // geometry; output declarations are independent from sampling.
        for (slot, alias) in COLOR_ALIASES.iter().enumerate().take(4) {
            self.bindings.remove(*alias);
            self.bindings.remove(&format!("colortex{slot}"));
        }
    }

    /// Resolves standard protocol aliases and custom textures for one actual
    /// source stage. Explicit transported manifests retain their existing
    /// semantics; no protocol defaults are added to a partial manifest.
    pub fn from_source_stage(
        source: &ShaderPackSource,
        stage: &TerrainSourceStage,
    ) -> GalResult<Self> {
        if source.get(TERRAIN_RESOURCE_BINDINGS_PATH).is_some() {
            return Self::from_source(source);
        }
        let defines = stage
            .defines
            .iter()
            .map(|(key, value)| (key.as_str(), value.as_str()))
            .collect::<Vec<_>>();
        let artifact = preprocess_artifact_with_runtime_options(source, &stage.path, &defines)?;
        Self::from_preprocessed_stage(source, &artifact)
    }

    pub fn from_preprocessed_stage(
        source: &ShaderPackSource,
        stage: &PreprocessedShaderSource,
    ) -> GalResult<Self> {
        if source.get(TERRAIN_RESOURCE_BINDINGS_PATH).is_some() {
            return Self::from_source(source);
        }
        let mut result = Self::protocol_bindings(source);
        let name = stage.entry_path().rsplit('/').next().unwrap_or("");
        let phase = if name.starts_with("begin") {
            "begin"
        } else if name.starts_with("prepare") {
            "prepare"
        } else if name.starts_with("deferred") {
            "deferred"
        } else if name.starts_with("composite") || name == "final.fsh" || name == "final.vsh" {
            "composite"
        } else if name.starts_with("shadow") {
            "shadow"
        } else {
            "gbuffers"
        };
        let protocol = source.get("shaders.properties");
        if !matches!(phase, "begin" | "prepare" | "deferred" | "composite") {
            result.restrict_geometry_colors();
        }
        if matches!(phase, "begin" | "prepare" | "deferred" | "composite") {
            // Frozen's fullscreen default sampler is the first scene color;
            // geometry keeps its atlas/local-material domain.
            result.bindings.insert(
                "tex".to_owned(),
                TerrainSourceResourceRole::ShaderPackColor(COLOR_NAMES[0].to_owned()),
            );
        }
        if protocol.is_some() {
            let defines = stage
                .defines()
                .iter()
                .map(|(key, value)| (key.as_str(), value.as_str()))
                .collect::<Vec<_>>();
            let properties =
                preprocess_artifact_with_runtime_options(source, "shaders.properties", &defines)?;
            let prefix = format!("texture.{phase}.");
            for line in properties.expanded_source().lines() {
                let Some((key, value)) = line.trim().split_once('=') else {
                    continue;
                };
                let sampler = if key.trim() == "texture.noise" {
                    Some("noisetex")
                } else {
                    key.trim().strip_prefix(&prefix)
                };
                let Some(sampler) = sampler else { continue };
                if !valid_identifier(sampler) || value.split_ascii_whitespace().count() != 1 {
                    return Err(GalError::unsupported_feature("legacy source custom texture requires a sampler and one normalized PNG path"));
                }
                let path = normalized_pack_texture_path(value)?;
                if !path.to_ascii_lowercase().ends_with(".png") {
                    return Err(GalError::unsupported_feature(
                        "legacy source custom texture requires a PNG asset",
                    ));
                }
                result.bindings.insert(
                    sampler.to_owned(),
                    TerrainSourceResourceRole::PackTexture(path),
                );
                if result.bindings.len()
                    > crate::render::shaderpack::source::assets::MAX_TERRAIN_SAMPLER_DECLARATIONS
                {
                    return Err(GalError::invalid_argument(
                        "legacy source sampler declarations exceed bounded limit",
                    ));
                }
            }
        }
        Ok(result)
    }
}

#[cfg(test)]
mod tests;

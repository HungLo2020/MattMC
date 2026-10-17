//! Selected ProgramSet shadow directives; expanded sources are never retained.

use super::*;
use crate::render::shaderpack::properties::directives::{
    constant_alias, resolved_properties, visit_fragment_directives,
};
use std::sync::OnceLock;

#[derive(Clone, Debug, Default)]
pub(crate) struct ShadowPolicies([OnceLock<GalResult<Option<ShaderPackShadowPolicy>>>; 4]);
impl PartialEq for ShadowPolicies {
    fn eq(&self, _: &Self) -> bool {
        true
    }
}
impl Eq for ShadowPolicies {}
impl ShadowPolicies {
    pub(crate) fn get(
        &self,
        source: &ShaderPackSource,
        scope: TerrainProgramScope,
    ) -> GalResult<Option<ShaderPackShadowPolicy>> {
        let index = match scope {
            TerrainProgramScope::Default => 0,
            TerrainProgramScope::Overworld => 1,
            TerrainProgramScope::Nether => 2,
            TerrainProgramScope::End => 3,
        };
        self.0[index]
            .get_or_init(|| ShaderPackShadowPolicy::from_source_for_scope(source, scope))
            .clone()
    }
}

impl ShaderPackShadowPolicy {
    pub fn from_source_for_scope(
        source: &ShaderPackSource,
        scope: TerrainProgramScope,
    ) -> GalResult<Option<Self>> {
        let mut policy = Self {
            generation: source.generation(),
            distance: DEFAULT_SHADOW_DISTANCE,
            resolution: DEFAULT_SHADOW_RESOLUTION,
            cutout_alpha_cutoff: Some(0.1),
            render_translucent: true,
            caster_selection: Some(ShadowCasterSelection::Advanced),
            voxel_distance: 0.0,
            near_plane: DEFAULT_SHADOW_NEAR_PLANE,
            far_plane: DEFAULT_SHADOW_FAR_PLANE,
            interval_size: DEFAULT_SHADOW_INTERVAL,
            sun_path_rotation_degrees: DEFAULT_SUN_PATH_ROTATION_DEGREES,
            supports_end_flash: false,
            casters: ShadowCasterDirectives {
                entities: true,
                player: false,
                block_entities: true,
            },
            caster_distances: None,
        };
        let mut perspective = false;
        let mut terrain_multiplier = -1.0;
        let mut entity_multiplier = 1.0;
        let scan = visit_fragment_directives(source, scope, false, |artifact| {
            read_constants(
                &mut policy,
                artifact.expanded_source(),
                artifact.defines(),
                &mut perspective,
                &mut terrain_multiplier,
                &mut entity_multiplier,
            )
        });
        let scan = match scan {
            Ok(scan) => scan,
            // Bounded transported fixtures can omit executable includes, but
            // their explicit compact metadata does not admit missing stages.
            Err(error)
                if source.get("lib/pipelineSettings.glsl").is_some()
                    && error.to_string().contains("missing shader source") =>
            {
                return Self::from_source(source)
            }
            Err(error) => return Err(error),
        };
        if scan.fragment_count == 0 && !source.paths().any(|path| path.ends_with(".fsh")) {
            return Self::from_source(source);
        }
        if perspective {
            return Err(GalError::unsupported_feature(
                "source perspective shadowMapFov requires a supported shadow projection",
            ));
        }
        policy.caster_distances = Some(selection::ShadowCasterDistancePolicy {
            terrain_multiplier, entity_multiplier,
        });
        if let Some(properties) = resolved_properties(source, &scan.property_defines)? {
            read_properties(&mut policy, properties.expanded_source())?;
        }
        policy.validate()?;
        Ok(Some(policy))
    }
}

fn read_constants(
    policy: &mut ShaderPackShadowPolicy,
    text: &str,
    defines: &[(String, String)],
    perspective: &mut bool,
    terrain_multiplier: &mut f32,
    entity_multiplier: &mut f32,
) -> GalResult<()> {
    for raw in text.lines() {
        let line = raw.split_once("//").map_or(raw, |(code, _)| code).trim();
        // Frozen accepts these legacy comment directives as well as constants.
        if let Some(comment) = line
            .strip_prefix("/*")
            .and_then(|v| v.split_once("*/").map(|(v, _)| v))
        {
            if let Some((key, value)) = comment.trim().split_once(':') {
                match key.trim() {
                    "SHADOWRES" => policy.resolution = integer(value.trim())?,
                    "SHADOWHPL" => policy.distance = float(value.trim())?,
                    "SHADOWFOV" => {
                        float(value.trim())?;
                        *perspective = true;
                    }
                    _ => {}
                }
            }
            continue;
        }
        let Some((declaration, value)) = line.split_once('=') else {
            continue;
        };
        let words = declaration.split_whitespace().collect::<Vec<_>>();
        let ["const", ty, name] = words.as_slice() else {
            continue;
        };
        let expected = match *name {
            "shadowMapResolution" => "int",
            "shadowDistance"
            | "shadowNearPlane"
            | "shadowFarPlane"
            | "shadowIntervalSize"
            | "sunPathRotation"
            | "voxelDistance"
            | "shadowMapFov"
            | "shadowDistanceRenderMul"
            | "entityShadowDistanceMul" => "float",
            _ => continue,
        };
        if *ty != expected {
            return Err(GalError::invalid_argument(format!(
                "source shadow directive {name} has incompatible type"
            )));
        }
        let value = value
            .split_once(';')
            .ok_or_else(|| GalError::invalid_argument("source shadow directive lacks semicolon"))?
            .0
            .trim();
        let value = constant_alias(value, defines)?;
        if *name == "shadowMapResolution" {
            policy.resolution = integer(value)?;
            continue;
        }
        let value = float(value)?;
        match *name {
            "shadowDistance" => policy.distance = value,
            "shadowNearPlane" => policy.near_plane = value,
            "shadowFarPlane" => policy.far_plane = value,
            "shadowIntervalSize" => policy.interval_size = value,
            "sunPathRotation" => policy.sun_path_rotation_degrees = value,
            "voxelDistance" => policy.voxel_distance = value,
            "shadowMapFov" => *perspective = true,
            // Keep both values: the entity path can reuse terrain selection
            // or request its own multiplied distance, just as Frozen does.
            "shadowDistanceRenderMul" => *terrain_multiplier = value,
            "entityShadowDistanceMul" => *entity_multiplier = value,
            _ => unreachable!(),
        }
    }
    Ok(())
}

fn integer(value: &str) -> GalResult<u32> {
    value.parse().map_err(|_| {
        GalError::invalid_argument("shadow resolution requires a resolved unsigned integer")
    })
}
fn float(value: &str) -> GalResult<f32> {
    let value: f32 = value.trim_end_matches(['f', 'F']).parse().map_err(|_| {
        GalError::invalid_argument("shadow directive requires a resolved float literal")
    })?;
    if !value.is_finite() {
        return Err(GalError::invalid_argument(
            "shadow directive requires a finite float",
        ));
    }
    Ok(value)
}
fn boolean(value: &str) -> GalResult<bool> {
    match value {
        "true" => Ok(true),
        "false" => Ok(false),
        _ => Err(GalError::invalid_argument(
            "shadow property requires a resolved boolean",
        )),
    }
}
fn read_properties(policy: &mut ShaderPackShadowPolicy, text: &str) -> GalResult<()> {
    for raw in text.lines() {
        let line = raw.trim();
        if line.starts_with('#') {
            continue;
        }
        let Some((key, value)) = line.split_once('=') else {
            continue;
        };
        let value = value.trim();
        match key.trim() {
            "shadowTranslucent" => policy.render_translucent = boolean(value)?,
            "endFlashShadows" => policy.supports_end_flash = boolean(value)?,
            "shadowEntities" => policy.casters.entities = boolean(value)?,
            "shadowPlayer" => policy.casters.player = boolean(value)?,
            "shadowBlockEntities" => policy.casters.block_entities = boolean(value)?,
            "shadow.culling" => {
                policy.caster_selection = Some(match value {
                    "true" => ShadowCasterSelection::Advanced,
                    "reversed" | "safe_zone" => ShadowCasterSelection::SafeZone,
                    "false" => ShadowCasterSelection::Distance,
                    _ => {
                        return Err(GalError::invalid_argument(
                            "unsupported resolved shadow.culling property",
                        ))
                    }
                })
            }
            "alphaTest.shadow" => {
                policy.cutout_alpha_cutoff = parse_alpha_cutoff(value, "alphaTest.shadow")?;
            }
            _ => {}
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests;

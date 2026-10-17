//! Source-derived shadow-camera semantics for shader-pack uniform preparation.
//!
//! This module mirrors the documented Iris shadow matrix rules from pack
//! directives and copied gameplay values. It owns no Java/Iris renderer state,
//! shader object, or backend resource. End flash support is selected only by
//! the pack's explicit `endFlashShadows` property and copied angle semantics.

use crate::render::vulkanic::error::{GalError, GalResult};

use crate::render::shaderpack::source::preprocess::preprocess_artifact_with_runtime_options;
use crate::render::shaderpack::source::ShaderPackSource;
use crate::render::shaderpack::contracts::terrain::TerrainProgramScope;
use crate::render::shaderpack::voxels::light_volume::invert_column_major_mat4;

const DEFAULT_SHADOW_DISTANCE: f32 = 160.0;
const DEFAULT_SHADOW_RESOLUTION: u32 = 1024;
const MAX_SUPPORTED_SHADOW_RESOLUTION: u32 = 4096;
const DEFAULT_SHADOW_NEAR_PLANE: f32 = -100.05;
const DEFAULT_SHADOW_FAR_PLANE: f32 = 156.0;
const DEFAULT_SHADOW_INTERVAL: f32 = 2.0;
const DEFAULT_SUN_PATH_ROTATION_DEGREES: f32 = 0.0;

mod scoped;
mod selection;
pub(crate) use scoped::ShadowPolicies;
pub(crate) use selection::{ShadowCasterFrameDistances, ShadowCasterKind};

/// Immutable pack-generation shadow directives. These are source semantics,
/// not a cached Java matrix or an OpenGL/Vulkan implementation detail.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct ShaderPackShadowPolicy {
    generation: u64,
    distance: f32,
    resolution: u32,
    cutout_alpha_cutoff: Option<f32>,
    render_translucent: bool,
    caster_selection: Option<ShadowCasterSelection>,
    voxel_distance: f32,
    near_plane: f32,
    far_plane: f32,
    interval_size: f32,
    sun_path_rotation_degrees: f32,
    supports_end_flash: bool,
    casters: ShadowCasterDirectives,
    // Compact transported fixtures retain their historical culling contract.
    // Normal scoped sources retain exact terrain/entity multipliers.
    caster_distances: Option<selection::ShadowCasterDistancePolicy>,
}

/// Iris's non-terrain shadow caster directives (`PackShadowDirectives`),
/// resolved against the selected pack options. Defaults match Iris:
/// entities and block entities render, the local player only when entities
/// are disabled and `shadowPlayer` is set.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct ShadowCasterDirectives {
    pub entities: bool,
    pub player: bool,
    pub block_entities: bool,
}

/// One complete ordinary-world shadow uniform set. Matrices use the same
/// column-major convention as the copied world matrices and GLSL mat4 values.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct ShaderPackShadowUniforms {
    pub model_view: [f32; 16],
    pub model_view_inverse: [f32; 16],
    pub projection: [f32; 16],
    pub projection_inverse: [f32; 16],
}

/// Frozen's advanced caster frustum derived from copied camera matrices and
/// the source celestial light. The bounds passed to `intersects` are relative
/// to the same camera origin as the copied terrain placements.
pub(crate) struct AdvancedShadowCasterFrustum {
    planes: Vec<[f32; 4]>,
    safe_zone: Option<(f64, f64)>,
    distance_limit: Option<f64>,
}

#[derive(Clone, Copy, Debug, PartialEq)]
enum ShadowCasterSelection {
    Advanced,
    SafeZone,
    Distance,
}

impl AdvancedShadowCasterFrustum {
    pub(crate) fn from_frame(
        policy: ShaderPackShadowPolicy,
        time_of_day: f32,
        projection: [f32; 16],
        view: [f32; 16],
    ) -> GalResult<Self> {
        Self::from_frame_with_selection(policy, time_of_day, projection, view, None, ShadowCasterKind::Terrain)
    }

    pub(crate) fn from_frame_with_distances(
        policy: ShaderPackShadowPolicy,
        time_of_day: f32,
        projection: [f32; 16],
        view: [f32; 16],
        distances: ShadowCasterFrameDistances,
        kind: ShadowCasterKind,
    ) -> GalResult<Self> {
        Self::from_frame_with_selection(policy, time_of_day, projection, view, Some(distances), kind)
    }

    fn from_frame_with_selection(
        policy: ShaderPackShadowPolicy,
        time_of_day: f32,
        projection: [f32; 16],
        view: [f32; 16],
        distances: Option<ShadowCasterFrameDistances>,
        kind: ShadowCasterKind,
    ) -> GalResult<Self> {
        let limits = policy.caster_limits(distances, kind)?;
        if !time_of_day.is_finite()
            || projection.iter().chain(view.iter()).any(|value| !value.is_finite())
        {
            return Err(GalError::invalid_argument(
                "source shadow caster frustum requires finite camera semantics",
            ));
        }
        if !limits.use_planes {
            return Ok(Self { planes: Vec::new(), safe_zone: limits.safe_zone,
                distance_limit: limits.distance_limit });
        }
        let celestial = multiply(
            multiply(rotation_y(-90.0), rotation_z(policy.sun_path_rotation_degrees)),
            rotation_x(time_of_day * 360.0),
        );
        let sign = if source_sun_angle(time_of_day) <= 0.5 { 1.0 } else { -1.0 };
        let mut light = [celestial[4] * sign, celestial[5] * sign, celestial[6] * sign];
        let light_length = dot3(light, light).sqrt();
        if !light_length.is_finite() || light_length == 0.0 {
            return Err(GalError::invalid_argument("source shadow light vector is degenerate"));
        }
        light = light.map(|value| value / light_length);
        let matrix = multiply(projection, view);
        let clip = [
            [-1.0, 0.0, 0.0, 1.0], [1.0, 0.0, 0.0, 1.0],
            [0.0, -1.0, 0.0, 1.0], [0.0, 1.0, 0.0, 1.0],
            [0.0, 0.0, -1.0, 1.0], [0.0, 0.0, 1.0, 1.0],
        ];
        // BaseClippingPlanes normalizes the four coefficients of each plane
        // after multiplying by transpose(projection * view).
        let base = clip.map(|vector| {
            let plane: [f32; 4] = std::array::from_fn(|row| {
                (0..4).map(|column| matrix[row * 4 + column] * vector[column]).sum()
            });
            let length = plane.iter().map(|value| value * value).sum::<f32>().sqrt();
            plane.map(|value| value / length)
        });
        if base.iter().flatten().any(|value| !value.is_finite()) {
            return Err(GalError::invalid_argument("source shadow camera plane is degenerate"));
        }
        let back = base.map(|plane| dot3([plane[0], plane[1], plane[2]], light) > 0.0);
        let mut planes = Vec::with_capacity(13);
        for (plane, is_back) in base.iter().zip(back) {
            if is_back || dot3([plane[0], plane[1], plane[2]], light) == 0.0 {
                planes.push(*plane);
            }
        }
        const NEIGHBORS: [[usize; 4]; 3] = [
            [2, 3, 4, 5], [0, 1, 4, 5], [0, 1, 2, 3],
        ];
        for (index, back_plane) in base.iter().enumerate() {
            if !back[index] { continue; }
            for neighbor in NEIGHBORS[index / 2] {
                if back[neighbor] { continue; }
                let front_plane = base[neighbor];
                let back_normal = [back_plane[0], back_plane[1], back_plane[2]];
                let front_normal = [front_plane[0], front_plane[1], front_plane[2]];
                let intersection = cross3(back_normal, front_normal);
                let length_squared = dot3(intersection, intersection);
                if !length_squared.is_finite() || length_squared == 0.0 {
                    return Err(GalError::invalid_argument("source shadow edge plane is degenerate"));
                }
                let ixb = cross3(intersection, back_normal);
                let fxi = cross3(front_normal, intersection);
                let point: [f32; 3] = std::array::from_fn(|axis| {
                    (ixb[axis] * -front_plane[3] + fxi[axis] * -back_plane[3]) / length_squared
                });
                let normal = cross3(intersection, light);
                planes.push([normal[0], normal[1], normal[2], -dot3(normal, point)]);
            }
        }
        if planes.len() > 13 || planes.iter().flatten().any(|value| !value.is_finite()) {
            return Err(GalError::invalid_argument("source shadow caster planes exceed finite bound"));
        }
        Ok(Self { planes, safe_zone: limits.safe_zone, distance_limit: limits.distance_limit })
    }

    pub(crate) fn intersects(&self, min: [f32; 3], max: [f32; 3]) -> bool {
        self.intersects_double_relative(min.map(f64::from), max.map(f64::from))
    }

    /// Camera-relative distance boxes used by the terrain/Sodium domain.
    pub(crate) fn intersects_double_relative(&self, min: [f64; 3], max: [f64; 3]) -> bool {
        if let Some(distance) = self.distance_limit {
            if (0..3).any(|axis| max[axis] < -distance || min[axis] > distance) {
                return false;
            }
        }
        if let Some((inner, outer)) = self.safe_zone {
            if (0..3).any(|axis| max[axis] < -outer || min[axis] > outer) {
                return false;
            }
            if (0..3).all(|axis| max[axis] >= -inner && min[axis] <= inner) {
                return true;
            }
        }
        let min = min.map(|value| value as f32);
        let max = max.map(|value| value as f32);
        self.intersects_planes(min,max)
    }

    /// Frozen BoxCuller.isCulled(AABB) casts absolute bounds to floats, but
    /// advanced clipping subtracts the double camera before its float cast.
    pub(crate) fn intersects_entity_world(&self, bounds: [f64;6], camera: [f64;3]) -> bool {
        let within = |distance:f64| (0..3).all(|axis| {
            f64::from(bounds[axis+3] as f32) >= camera[axis]-distance
                && f64::from(bounds[axis] as f32) <= camera[axis]+distance
        });
        if self.distance_limit.is_some_and(|distance| !within(distance)) {return false;}
        if let Some((_inner,outer))=self.safe_zone {
            // Preserve Frozen's entity-AABB entry point separately from
            // Sodium terrain: SafeZoneCullingFrustum.isVisible(AABB) compares
            // its advanced result to zero. OUTSIDE/INTERSECT/INSIDE are all
            // nonzero, so every box inside the outer distance limit survives.
            // The compiled Frozen world-bounds probe covers this behavior.
            return within(outer);
        }
        self.intersects_planes(std::array::from_fn(|i|(bounds[i]-camera[i]) as f32),
            std::array::from_fn(|i|(bounds[i+3]-camera[i]) as f32))
    }

    fn intersects_planes(&self,min:[f32;3],max:[f32;3]) -> bool {
        self.planes.iter().all(|plane| {
            let furthest: [f32; 3] = std::array::from_fn(|axis| {
                if plane[axis] < 0.0 { min[axis] } else { max[axis] }
            });
            dot3([plane[0], plane[1], plane[2]], furthest) + plane[3] >= 0.0
        })
    }
}

fn dot3(left: [f32; 3], right: [f32; 3]) -> f32 {
    left.iter().zip(right).map(|(a, b)| a * b).sum()
}

fn cross3(left: [f32; 3], right: [f32; 3]) -> [f32; 3] {
    [
        left[1] * right[2] - left[2] * right[1],
        left[2] * right[0] - left[0] * right[2],
        left[0] * right[1] - left[1] * right[0],
    ]
}

impl ShaderPackShadowPolicy {
    /// Parses the source generation's ordinary-world directives. A missing
    /// common source means this policy is not applicable; it is not a default
    /// to a guessed source contract.
    pub fn from_source(source: &ShaderPackSource) -> GalResult<Option<Self>> {
        if source.get("lib/common.glsl").is_none() {
            return Ok(None);
        }
        let artifact = preprocess_artifact_with_runtime_options(source, "lib/common.glsl", &[])?;
        let common = artifact.expanded_source();
        let cutout_alpha_cutoff = source_shadow_alpha_cutoff(source)?;
        let policy = Self {
            generation: source.generation(),
            distance: source_float_constant(&common, "shadowDistance")?
                .unwrap_or(DEFAULT_SHADOW_DISTANCE),
            resolution: source_int_constant(&common, "shadowMapResolution")?
                .unwrap_or(DEFAULT_SHADOW_RESOLUTION),
            cutout_alpha_cutoff,
            render_translucent: source_bool_property(source, "shadowTranslucent")?.unwrap_or(true),
            caster_selection: source_caster_selection(source)?,
            voxel_distance: source.get("program/gbuffers_terrain.glsl")
                .map(|_| preprocess_artifact_with_runtime_options(source, "program/gbuffers_terrain.glsl", &[]))
                .transpose()?
                .map(|artifact| source_float_constant(&artifact.expanded_source(), "voxelDistance"))
                .transpose()?
                .flatten()
                .unwrap_or(0.0),
            near_plane: source_float_constant(&common, "shadowNearPlane")?
                .unwrap_or(DEFAULT_SHADOW_NEAR_PLANE),
            far_plane: source_float_constant(&common, "shadowFarPlane")?
                .unwrap_or(DEFAULT_SHADOW_FAR_PLANE),
            interval_size: source_float_constant(&common, "shadowIntervalSize")?
                .unwrap_or(DEFAULT_SHADOW_INTERVAL),
            sun_path_rotation_degrees: source_float_constant(&common, "sunPathRotation")?
                .unwrap_or(DEFAULT_SUN_PATH_ROTATION_DEGREES),
            supports_end_flash: source_bool_property(source, "endFlashShadows")?.unwrap_or(false),
            casters: source_shadow_caster_directives(source)?,
            caster_distances: None,
        };
        policy.validate()?;
        Ok(Some(policy))
    }

    pub fn generation(self) -> u64 {
        self.generation
    }

    /// Square shadow attachment edge requested by the copied, preprocessed pack.
    pub fn resolution(self) -> u32 {
        self.resolution
    }

    /// Source shadow-pass alpha rule for cutout materials. `None` means the
    /// pack explicitly disabled the test; opaque materials never use it.
    pub fn cutout_alpha_cutoff(self) -> Option<f32> {
        self.cutout_alpha_cutoff
    }

    /// Entity, player, and block-entity shadow caster directives.
    pub fn casters(self) -> ShadowCasterDirectives {
        self.casters
    }

    /// Whether the selected pack admits translucent terrain into its shadow
    /// pass. Iris defaults this directive to true.
    pub fn render_translucent(self) -> bool {
        self.render_translucent
    }

    /// Admit only resolved light-aware advanced or safe-zone source culling.
    fn require_supported_caster_selection(self) -> GalResult<ShadowCasterSelection> {
        match self.caster_selection {
            Some(mode @ (ShadowCasterSelection::Advanced | ShadowCasterSelection::SafeZone)) => Ok(mode),
            Some(ShadowCasterSelection::Distance) => Err(GalError::unsupported_feature(
                "source shadow-only selection does not support distance-only shadow.culling",
            )),
            None => Err(GalError::unsupported_feature(
                "source shadow.culling requires resolved runtime property options",
            )),
        }
    }

    /// Source-defined celestial path rotation shared by the owned shadow and
    /// sky transforms. This exposes a pack semantic only; it carries no Iris
    /// pipeline, renderer, or backend state.
    pub fn sun_path_rotation_degrees(self) -> f32 {
        self.sun_path_rotation_degrees
    }

    /// Derives shadow uniforms from semantic frame inputs. End uses the
    /// ordinary celestial transform unless the pack explicitly opts into its
    /// copied End flash branch via `uniforms_with_end_flash`.
    pub fn uniforms(
        self,
        scope: TerrainProgramScope,
        time_of_day: f32,
        camera_world_position: [f32; 3],
    ) -> GalResult<ShaderPackShadowUniforms> {
        self.uniforms_with_end_flash(scope, time_of_day, camera_world_position, None)
    }

    /// Derives shadow uniforms with the copied End flash angles. Packs only
    /// enter the End branch when they explicitly opt into `endFlashShadows`;
    /// other End packs retain Iris's ordinary celestial shadow transform.
    pub fn uniforms_with_end_flash(
        self,
        scope: TerrainProgramScope,
        time_of_day: f32,
        camera_world_position: [f32; 3],
        end_flash_angles: Option<[f32; 2]>,
    ) -> GalResult<ShaderPackShadowUniforms> {
        if !time_of_day.is_finite()
            || camera_world_position
                .iter()
                .any(|coordinate| !coordinate.is_finite())
        {
            return Err(GalError::invalid_argument(
                "source shadow matrix inputs must be finite",
            ));
        }
        let model_view = if scope == TerrainProgramScope::End && self.supports_end_flash {
            let angles = end_flash_angles.ok_or_else(|| {
                GalError::unsupported_feature("End shadow support requires copied End flash angles")
            })?;
            if angles.iter().any(|value| !value.is_finite()) {
                return Err(GalError::invalid_argument(
                    "End flash shadow angles must be finite",
                ));
            }
            end_shadow_model_view(
                angles[0],
                angles[1],
                self.interval_size,
                camera_world_position,
            )
        } else {
            let sun_angle = source_sun_angle(time_of_day);
            let shadow_angle = if sun_angle <= 0.5 {
                sun_angle
            } else {
                sun_angle - 0.5
            };
            shadow_model_view(
                shadow_angle,
                self.sun_path_rotation_degrees,
                self.interval_size,
                camera_world_position,
            )
        };
        let projection = shadow_ortho_projection(self.distance, self.near_plane, self.far_plane);
        Ok(ShaderPackShadowUniforms {
            model_view,
            model_view_inverse: invert_column_major_mat4(model_view, "source shadow model view")?,
            projection,
            projection_inverse: invert_column_major_mat4(projection, "source shadow projection")?,
        })
    }

    fn validate(self) -> GalResult<()> {
        for (label, value) in [
            ("shadow distance", self.distance),
            ("shadow near plane", self.near_plane),
            ("shadow far plane", self.far_plane),
            ("shadow interval size", self.interval_size),
            ("sun path rotation", self.sun_path_rotation_degrees),
            ("voxel distance", self.voxel_distance),
        ] {
            if !value.is_finite() {
                return Err(GalError::invalid_argument(format!(
                    "source {label} must be finite"
                )));
            }
        }
        if self.distance <= 0.0 {
            return Err(GalError::invalid_argument(
                "source shadow distance must be positive",
            ));
        }
        if self.voxel_distance < 0.0 {
            return Err(GalError::invalid_argument("source voxel distance must be nonnegative"));
        }
        if self.resolution == 0 || self.resolution > MAX_SUPPORTED_SHADOW_RESOLUTION {
            return Err(GalError::unsupported_feature(format!(
                "source shadow map resolution {} exceeds supported range 1..={MAX_SUPPORTED_SHADOW_RESOLUTION}",
                self.resolution
            )));
        }
        if (self.near_plane - self.far_plane).abs() <= f32::EPSILON {
            return Err(GalError::invalid_argument(
                "source shadow near and far planes must differ",
            ));
        }
        Ok(())
    }
}

fn source_shadow_alpha_cutoff(source: &ShaderPackSource) -> GalResult<Option<f32>> {
    source_alpha_test_cutoff(source, "alphaTest.shadow", 0.1)
}

/// Resolves one unconditional `alphaTest.<program>` property like Iris:
/// absent keeps the program's default cutoff, `off`/`false` disables the
/// test, and `GREATER x` selects `x`. Other comparisons are not modeled.
pub(crate) fn source_alpha_test_cutoff(
    source: &ShaderPackSource,
    property: &str,
    default_cutoff: f32,
) -> GalResult<Option<f32>> {
    let Some(raw_properties) = source.get("shaders.properties") else {
        return Ok(Some(default_cutoff));
    };
    let mut selected = None;
    let mut conditional_depth = 0usize;
    for raw_line in raw_properties.lines() {
        let line = raw_line.trim();
        if line.starts_with("#if ") || line.starts_with("#if(")
            || line.starts_with("#ifdef ") || line.starts_with("#ifndef ")
        {
            conditional_depth += 1;
            continue;
        }
        if line.starts_with("#endif") {
            conditional_depth = conditional_depth.saturating_sub(1);
            continue;
        }
        let Some((key, value)) = line.split_once('=') else {
            continue;
        };
        if key.trim() != property {
            continue;
        }
        if conditional_depth != 0 {
            return Err(GalError::unsupported_feature(
                format!("conditional {property} requires source option resolution"),
            ));
        }
        if selected.replace(value.trim().to_string()).is_some() {
            return Err(GalError::invalid_argument(format!(
                "source declares {property} more than once"
            )));
        }
    }
    let Some(selected) = selected else {
        return Ok(Some(default_cutoff));
    };
    parse_alpha_cutoff(&selected, property)
}

fn parse_alpha_cutoff(selected: &str, property: &str) -> GalResult<Option<f32>> {
    if selected == "off" || selected == "false" {
        return Ok(None);
    }
    let mut parts = selected.split_ascii_whitespace();
    let (Some("GREATER"), Some(threshold), None) =
        (parts.next(), parts.next(), parts.next())
    else {
        return Err(GalError::unsupported_feature(format!(
            "source {property} '{selected}' is not modeled"
        )));
    };
    let threshold = threshold.parse::<f32>().map_err(|_| {
        GalError::invalid_argument(format!("source {property} threshold must be a finite number"))
    })?;
    if !threshold.is_finite() || !(0.0..=1.0).contains(&threshold) {
        return Err(GalError::invalid_argument(format!(
            "source {property} threshold must be in [0, 1]"
        )));
    }
    Ok(Some(threshold))
}

fn source_bool_property(source: &ShaderPackSource, key: &str) -> GalResult<Option<bool>> {
    let Some(properties) = source.get("shaders.properties") else {
        return Ok(None);
    };
    let mut result = None;
    let mut conditional_depth = 0usize;
    for (line_number, raw_line) in properties.lines().enumerate() {
        let directive = raw_line.trim_start();
        if directive.starts_with("#if ")
            || directive.starts_with("#if\t")
            || directive.starts_with("#if(")
            || directive.starts_with("#ifdef ")
            || directive.starts_with("#ifndef ")
        {
            conditional_depth += 1;
            continue;
        }
        if directive.starts_with("#endif") {
            conditional_depth = conditional_depth.saturating_sub(1);
            continue;
        }
        let line = raw_line
            .split_once('#')
            .map_or(raw_line, |(code, _)| code)
            .trim();
        let Some((name, value)) = line.split_once('=') else {
            continue;
        };
        if name.trim() != key {
            continue;
        }
        if conditional_depth != 0 {
            return Err(GalError::unsupported_feature(format!(
                "conditional shaders.properties {key} requires source option resolution"
            )));
        }
        if result.is_some() {
            return Err(GalError::invalid_argument(format!(
                "shaders.properties declares {key} more than once"
            )));
        }
        result = Some(match value.trim() {
            "true" => true,
            "false" => false,
            other => {
                return Err(GalError::invalid_argument(format!(
                    "shaders.properties {key} line {} must be true or false, got {other}",
                    line_number + 1
                )))
            }
        });
    }
    Ok(result)
}

fn source_shadow_caster_directives(source: &ShaderPackSource) -> GalResult<ShadowCasterDirectives> {
    let mut directives = ShadowCasterDirectives {
        entities: true,
        player: false,
        block_entities: true,
    };
    let Some((properties, _)) =
        crate::render::shaderpack::contracts::fullscreen::resolved_source_properties(source, TerrainProgramScope::Overworld)?
    else {
        return Ok(directives);
    };
    let mut seen = std::collections::BTreeSet::new();
    for raw in properties.lines() {
        let line = raw.trim();
        if line.is_empty() || line.starts_with('#') {
            continue;
        }
        let Some((key, value)) = line.split_once('=') else {
            continue;
        };
        let key = key.trim();
        let slot = match key {
            "shadowEntities" => &mut directives.entities,
            "shadowPlayer" => &mut directives.player,
            "shadowBlockEntities" => &mut directives.block_entities,
            _ => continue,
        };
        if !seen.insert(key.to_string()) {
            return Err(GalError::invalid_argument(format!(
                "active shaders.properties declares {key} more than once"
            )));
        }
        *slot = match value.trim() {
            "true" => true,
            "false" => false,
            other => {
                return Err(GalError::invalid_argument(format!(
                    "active shaders.properties {key} must be true or false, got {other}"
                )))
            }
        };
    }
    Ok(directives)
}

fn source_caster_selection(source: &ShaderPackSource) -> GalResult<Option<ShadowCasterSelection>> {
    if source.get("shaders.properties").is_none() {
        return Ok(Some(ShadowCasterSelection::Advanced));
    }
    let properties = match preprocess_artifact_with_runtime_options(source, "shaders.properties", &[]) {
        Ok(artifact) => artifact,
        Err(_) => return Ok(None),
    };
    let mut selected = None;
    for line in properties.expanded_source().lines() {
        let Some((key, value)) = line.split_once('=') else { continue; };
        if key.trim() != "shadow.culling" { continue; }
        let selection = match value.trim() {
            "true" => ShadowCasterSelection::Advanced,
            "reversed" | "safe_zone" => ShadowCasterSelection::SafeZone,
            "false" => ShadowCasterSelection::Distance,
            other => return Err(GalError::invalid_argument(format!(
                "unsupported active shadow.culling value {other}"
            ))),
        };
        if selected.replace(selection).is_some() {
            return Err(GalError::invalid_argument(
                "source declares active shadow.culling more than once",
            ));
        }
    }
    Ok(Some(selected.unwrap_or(ShadowCasterSelection::Advanced)))
}

fn source_int_constant(source: &str, name: &str) -> GalResult<Option<u32>> {
    let declaration = format!("const int {name}");
    let mut value = None;
    for (line_number, raw_line) in source.lines().enumerate() {
        let line = raw_line
            .split_once("//")
            .map_or(raw_line, |(code, _)| code)
            .trim();
        if !line.starts_with(&declaration) {
            continue;
        }
        let remainder = line[declaration.len()..].trim_start();
        let Some(expression) = remainder
            .strip_prefix('=')
            .and_then(|value| value.trim().strip_suffix(';'))
        else {
            return Err(GalError::invalid_argument(format!(
                "source {name} declaration on line {} must be one integer literal",
                line_number + 1
            )));
        };
        let parsed = expression.trim().parse::<u32>().map_err(|_| {
            GalError::invalid_argument(format!(
                "source {name} declaration on line {} is not an unsigned integer literal",
                line_number + 1
            ))
        })?;
        if value.replace(parsed).is_some() {
            return Err(GalError::invalid_argument(format!(
                "source declares {name} more than once after preprocessing"
            )));
        }
    }
    Ok(value)
}

fn source_float_constant(source: &str, name: &str) -> GalResult<Option<f32>> {
    let declaration = format!("const float {name}");
    let mut value = None;
    for (line_number, raw_line) in source.lines().enumerate() {
        // Shader-pack directive lists conventionally trail the declaration
        // with a line comment. Preprocessing has already selected branches;
        // this parser needs only the declaration token sequence.
        let line = raw_line
            .split_once("//")
            .map_or(raw_line, |(code, _)| code)
            .trim();
        if !line.starts_with(&declaration) {
            continue;
        }
        let remainder = line[declaration.len()..].trim_start();
        let Some(expression) = remainder
            .strip_prefix('=')
            .and_then(|value| value.trim().strip_suffix(';'))
        else {
            return Err(GalError::invalid_argument(format!(
                "source {name} declaration on line {} must be one float literal",
                line_number + 1
            )));
        };
        let parsed = expression.trim().parse::<f32>().map_err(|_| {
            GalError::invalid_argument(format!(
                "source {name} declaration on line {} is not a float literal",
                line_number + 1
            ))
        })?;
        if !parsed.is_finite() {
            return Err(GalError::invalid_argument(format!(
                "source {name} declaration on line {} must be finite",
                line_number + 1
            )));
        }
        if value.replace(parsed).is_some() {
            return Err(GalError::invalid_argument(format!(
                "source declares {name} more than once after preprocessing"
            )));
        }
    }
    Ok(value)
}

fn source_sun_angle(sky_angle: f32) -> f32 {
    if sky_angle < 0.75 {
        sky_angle + 0.25
    } else {
        sky_angle - 0.75
    }
}

fn shadow_model_view(
    shadow_angle: f32,
    sun_path_rotation_degrees: f32,
    interval_size: f32,
    camera: [f32; 3],
) -> [f32; 16] {
    let sky_angle = if shadow_angle < 0.25 {
        shadow_angle + 0.75
    } else {
        shadow_angle - 0.25
    };
    let mut result = identity();
    result = multiply(result, rotation_x(90.0));
    result = multiply(result, rotation_z(sky_angle * -360.0));
    result = multiply(result, rotation_x(sun_path_rotation_degrees));
    if interval_size.abs() > 0.0 {
        // Java intentionally uses f32 remainder, which preserves a negative
        // remainder for negative camera coordinates.
        let offset = camera.map(|coordinate| coordinate % interval_size - interval_size / 2.0);
        result = multiply(result, translation(offset));
    }
    result
}

fn end_shadow_model_view(
    x_angle: f32,
    y_angle: f32,
    interval_size: f32,
    camera: [f32; 3],
) -> [f32; 16] {
    let mut result = identity();
    result = multiply(result, rotation_x(-x_angle));
    result = multiply(result, rotation_y(y_angle));
    if interval_size.abs() > 0.0 {
        let offset = camera.map(|coordinate| coordinate % interval_size - interval_size / 2.0);
        result = multiply(result, translation(offset));
    }
    result
}

fn shadow_ortho_projection(distance: f32, near_plane: f32, far_plane: f32) -> [f32; 16] {
    let depth = near_plane - far_plane;
    [
        distance.recip(),
        0.0,
        0.0,
        0.0,
        0.0,
        distance.recip(),
        0.0,
        0.0,
        0.0,
        0.0,
        2.0 / depth,
        0.0,
        0.0,
        0.0,
        (far_plane + near_plane) / depth,
        1.0,
    ]
}

fn identity() -> [f32; 16] {
    [
        1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
    ]
}

fn translation(offset: [f32; 3]) -> [f32; 16] {
    [
        1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, offset[0], offset[1],
        offset[2], 1.0,
    ]
}

fn rotation_x(degrees: f32) -> [f32; 16] {
    let (sine, cosine) = degrees.to_radians().sin_cos();
    [
        1.0, 0.0, 0.0, 0.0, 0.0, cosine, sine, 0.0, 0.0, -sine, cosine, 0.0, 0.0, 0.0, 0.0, 1.0,
    ]
}

fn rotation_z(degrees: f32) -> [f32; 16] {
    let (sine, cosine) = degrees.to_radians().sin_cos();
    [
        cosine, sine, 0.0, 0.0, -sine, cosine, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
    ]
}

fn rotation_y(degrees: f32) -> [f32; 16] {
    let (sine, cosine) = degrees.to_radians().sin_cos();
    [
        cosine, 0.0, -sine, 0.0, 0.0, 1.0, 0.0, 0.0, sine, 0.0, cosine, 0.0, 0.0, 0.0, 0.0, 1.0,
    ]
}

fn multiply(left: [f32; 16], right: [f32; 16]) -> [f32; 16] {
    std::array::from_fn(|index| {
        let column = index / 4;
        let row = index % 4;
        (0..4)
            .map(|inner| left[inner * 4 + row] * right[column * 4 + inner])
            .sum()
    })
}

#[cfg(test)]
mod tests;

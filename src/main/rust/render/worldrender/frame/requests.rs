//! The world frame the bridge hands the renderer: requests, LOD records, background/sky, first-person and submit statistics.

use crate::render::worldrender::*;

#[derive(Clone, Copy, Debug)]
pub struct WorldLineSegmentRequest {
    pub stratum: u32,
    pub style: u32,
    pub depth_policy: u32,
    pub color_argb: u32,
    pub line_width: f32,
    pub start: [f32; 3],
    pub end: [f32; 3],
    pub viewport_width: u32,
    pub viewport_height: u32,
}

#[derive(Clone, Debug)]
pub struct WorldCrackQuadRequest {
    pub stratum: u32,
    pub stage: u32,
    pub depth_policy: u32,
    pub color_argb: u32,
    pub vertices: [[f32; 3]; 4],
    pub viewport_width: u32,
    pub viewport_height: u32,
}

#[derive(Clone, Debug)]
pub struct WorldBorderQuadRequest {
    pub stratum: u32,
    pub texture_id: u32,
    pub depth_policy: u32,
    pub blend_policy: u32,
    pub cull_policy: u32,
    pub color_argb: u32,
    pub border_size: f32,
    pub distance_to_border: f32,
    pub scroll: [f32; 2],
    pub uv_region: [f32; 4],
    pub vertices: [[f32; 3]; 4],
    pub viewport_width: u32,
    pub viewport_height: u32,
}

#[derive(Clone, Debug)]
pub struct WorldMaterialQuadRequest {
    pub stratum: u32,
    pub material_id: u32,
    pub texture_id: u32,
    pub material_mode: u32,
    pub depth_policy: u32,
    pub cull_policy: u32,
    pub topology: u32,
    pub winding: u32,
    pub color_argb: u32,
    pub vertices: [[f32; 3]; 4],
    pub uvs: [[f32; 2]; 4],
    pub viewport_width: u32,
    pub viewport_height: u32,
    pub source_program: u32,
    pub source_uv_space: u32,
    pub source_color_argb: u32,
    pub packed_light: u32,
    /// Optional per-vertex modulation for dynamic world primitives. Ordinary
    /// material quads retain a compact uniform value in every lane; producers
    /// such as a leash can preserve vanilla's endpoint light interpolation
    /// without introducing a producer-specific command family.
    pub vertex_color_argb: [u32; 4],
    pub vertex_packed_light: [u32; 4],
    pub block_entity_id: i32,
}

#[derive(Clone, Debug)]
pub struct WorldBorderAssetPayload {
    pub texture_id: u32,
    pub png_bytes: Vec<u8>,
}

#[derive(Clone, Debug)]
pub struct WorldMaterialAssetPayload {
    pub texture_id: u32,
    pub png_bytes: Vec<u8>,
}

/// One backend-neutral DH LOD vertex. Positions are unsigned column-local
/// coordinates. `packed_light_and_micro_offset` retains the producer's
/// semantic bit fields without treating them as an OpenGL attribute format.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct WorldLodVertex {
    pub local_position: [u16; 3],
    pub packed_light_and_micro_offset: u16,
    pub color_rgba: [u8; 4],
    pub material_id: u8,
    pub normal_index: u8,
}

/// A quad-aligned draw segment copied from one DH CPU buffer. Segment
/// boundaries are semantic batching boundaries, not native VBO identities.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct WorldLodSegment {
    pub layer: u32,
    pub vertices: Vec<WorldLodVertex>,
}

/// Immutable, generation-bound LOD column asset. A later frame request will
/// reference this key rather than resend vertex data every frame.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct WorldLodColumnAsset {
    pub column_key: u64,
    pub column_generation: u64,
    pub vertex_layout_version: u32,
    pub origin: [i32; 3],
    pub segments: Vec<WorldLodSegment>,
}

/// Rust-owned copy of a source material identity for a reduced DH column.
/// It remains deliberately distinct from a resolved texture: reduced quads
/// may be unavailable or mixed and must never be assigned a guessed sprite.
#[derive(Clone, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub struct WorldLodMaterialIdentity {
    pub block_state_identity: String,
    pub biome_identity: String,
}

/// One segment's one-ID-per-quad material sidecar. IDs are one-based into the
/// enclosing column identity table, with explicit unavailable/mixed sentinels.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct WorldLodSegmentMaterialProvenance {
    pub layer: u32,
    pub segment_index: u32,
    pub quad_material_ids: Vec<u32>,
    /// Per-quad source-position provenance for weighted Minecraft models.
    /// The position is meaningful only for `WORLD_LOD_VARIANT_EXACT`.
    pub quad_variant_states: Vec<u8>,
    pub quad_variant_positions: Vec<u64>,
}

/// Exact source atlas region for one face of one recoverable reduced LOD
/// material. Missing faces remain unavailable; Rust never guesses a sprite.
#[derive(Clone, Debug, PartialEq)]
pub struct WorldLodFaceMaterial {
    pub material_id: u32,
    pub face: u32,
    /// Ordered co-planar material layer for one reduced DH face. Layer zero
    /// is the base; later layers are alpha-tested overlays from the same
    /// source model face.
    pub face_layer: u32,
    /// Applies the copied semantic vertex tint to the atlas color. Untinted
    /// baked faces must not multiply their real sprite by DH's legacy surface
    /// color, which already encodes a representative final appearance.
    pub tinted: bool,
    pub tint_rgb: [f32; 3],
    pub atlas_identity: String,
    pub sprite_identity: String,
    pub atlas_uv: [f32; 4],
    /// Maps canonical DH face corners `[00, 01, 11, 10]` to source sprite
    /// corners. This preserves rotation/mirroring without a source model.
    pub uv_corner_order: u32,
    /// Canonical zero for seed-independent models, otherwise the exact packed
    /// Minecraft block position that selected the weighted model part.
    pub variant_position: u64,
}

/// Immutable provenance paired with exactly one LOD column generation. This
/// is diagnostic/resource-resolution input only; the current color-only DH
/// material pass does not consume it as a texture binding.
#[derive(Clone, Debug, PartialEq)]
pub struct WorldLodColumnMaterialProvenance {
    pub column_key: u64,
    pub column_generation: u64,
    pub identities: Vec<WorldLodMaterialIdentity>,
    pub segments: Vec<WorldLodSegmentMaterialProvenance>,
    pub face_materials: Vec<WorldLodFaceMaterial>,
}

/// Compact visible-column reference for the eventual combined world frame.
/// Layer and ordering are explicit because transparent LOD streams cannot be
/// merged into opaque terrain submission accidentally.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct WorldLodColumnInstanceRequest {
    pub column_key: u64,
    pub column_generation: u64,
    pub layer: u32,
    pub segment_index: u32,
    pub order: u32,
}

/// Resolved, backend-neutral Distant Horizons terrain-program inputs for one
/// frame. Java supplies scalar gameplay/configuration semantics only; Rust
/// owns the future shader, lightmap resource binding, pass, and draw.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct WorldLodRenderFrame {
    pub enabled: bool,
    pub flags: u32,
    pub world_y_offset: i32,
    /// The exact DH camera used for the copied column model offsets. This is
    /// deliberately independent of optional voxel-volume work.
    pub camera_world_position: [f32; 3],
    /// The exact DH projection-times-model-view matrix, in the producer's
    /// declared matrix convention. A later LOD material pass normalizes it at
    /// its own shader boundary rather than inferring it from Java GL state.
    pub combined_matrix: [f32; 16],
    /// Explicit DH model-view/projection semantics for selected-source
    /// shader-pack lowering. They are copied frame data, never a Java/DH
    /// renderer object or an OpenGL state query.
    pub model_view_matrix: [f32; 16],
    pub projection_matrix: [f32; 16],
    pub projection_inverse_matrix: [f32; 16],
    pub clip_distance: f32,
    pub micro_offset: f32,
    pub noise_intensity: f32,
    pub earth_radius: f32,
    pub noise_steps: u32,
    pub noise_dropoff: i32,
    pub dh_fog_parameters: [f32; 20],
    /// Copied level maximum used by the vanilla fade cloud guard.
    pub max_level_height: i32,
    /// Explicit DH SSAO configuration copied from the semantic frame. The
    /// direct compositor consumes this data without opening a Java pass.
    pub ssao_parameters: [f32; 8],
}

impl WorldLodRenderFrame {
    /// A copied DH render frame can supply pack-wide source uniforms even if
    /// the current frame has no Rust-owned LOD geometry. The route flag stays
    /// separate: only it authorizes DH mesh execution.
    pub const fn source_semantics_available(self) -> bool {
        self.enabled
    }

    pub const fn rust_route_selected(self) -> bool {
        self.enabled && self.flags & WORLD_LOD_FLAG_RUST_ROUTE_SELECTED != 0
    }

    pub const fn rust_opaque_route_selected(self) -> bool {
        self.rust_route_selected()
    }
}

impl Default for WorldLodRenderFrame {
    fn default() -> Self {
        Self {
            enabled: false,
            flags: 0,
            world_y_offset: 0,
            camera_world_position: [0.0; 3],
            combined_matrix: [0.0; 16],
            model_view_matrix: [0.0; 16],
            projection_matrix: [0.0; 16],
            projection_inverse_matrix: [0.0; 16],
            clip_distance: 0.0,
            micro_offset: 0.0,
            noise_intensity: 0.0,
            earth_radius: 0.0,
            noise_steps: 0,
            noise_dropoff: 0,
            dh_fog_parameters: [0.0; 20],
            max_level_height: 0,
            ssao_parameters: [0.0; 8],
        }
    }
}

/// Explicit retirement record for a cached LOD column. The expected asset
/// generation makes late disposal from an old DH build unable to remove a
/// replacement column.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct WorldLodColumnRetirement {
    pub column_key: u64,
    pub column_generation: u64,
}

#[derive(Clone, Debug)]
pub struct WorldMeshSortedIndexUpdate {
    pub mesh_key: u64,
    pub mesh_generation: u64,
    pub index_generation: u64,
    pub index_type: IndexType,
    pub index_bytes: Vec<u8>,
}

#[derive(Clone, Debug)]
pub struct WorldMeshAnimationFrame {
    pub frame_index: u32,
    pub duration_ticks: u32,
}

#[derive(Clone, Debug)]
pub struct WorldMeshTextureAssetPayload {
    pub texture_id: u32,
    pub png_bytes: Vec<u8>,
    /// Copied semantic mip PNGs in ascending order, excluding base mip zero.
    /// Present only for assets whose producer supplies an exact CPU mip chain.
    pub mip_png_bytes: Vec<Vec<u8>>,
    pub frame_width: u32,
    pub frame_height: u32,
    pub frame_count: u32,
    pub frame_ticks: u32,
    pub animation_flags: u32,
    pub frame_row_size: u32,
    pub interpolation_policy: u32,
    pub animation_frames: Vec<WorldMeshAnimationFrame>,
    pub coordinate_origin: u32,
    /// Explicit copied resource metadata, not inherited backend sampler state.
    pub sampling: Option<crate::render::shared::texture_sampling::TextureSampling>,
    pub requested_mip_levels: u32,
}

#[derive(Clone, Debug)]
pub struct WorldMeshInstanceRequest {
    pub entity_culling: Option<super::entity_culling::WorldEntityCullingInputs>,
    /// Authored model collection order, independent of material and arrival order.
    pub model_submission_order: Option<i32>,
    /// Original-resource standard foil semantics, never a Java texture matrix.
    /// Supplied only by explicitly selected standard-foil callsites.
    pub item_foil: Option<crate::render::shared::item_foil::StandardItemFoil>,
    pub decal_foil: Option<crate::render::worldrender::features::decal_foil::WorldDecalFoilProjection>,
    pub stratum: u32,
    pub mesh_key: u64,
    pub mesh_generation: u64,
    pub mesh_section_index: u32,
    /// Camera-side Sodium face selection for ordinary vanilla terrain only.
    /// Source shadow passes use their own visibility policy.
    pub terrain_visible_facing_mask: u8,
    pub depth_policy: u32,
    pub cull_policy: u32,
    pub winding: u32,
    pub color_argb: u32,
    /// Shared entity semantic identity. Non-entity indexed mesh producers
    /// carry the explicit neutral value zero.
    pub entity_id: i32,
    /// Shared ARGB entity-color override. Alpha zero preserves source color.
    pub entity_color_argb: u32,
    /// Copied packed vanilla UV2 light used by stable model assets when the
    /// explicit instance-light flag is present. Terrain leaves this zero and
    /// carries its baked light per vertex.
    pub packed_light: u32,
    /// Semantic entity-outline color. Alpha zero means no outline request.
    pub outline_color_argb: u32,
    /// Explicit semantic instance flags. Bit 0 is outline-only.
    pub flags: u32,
    /// Copied vanilla block-state registry identity for block-entity shader
    /// semantics. -1 means this instance is not block-entity-owned.
    pub block_entity_id: i32,
    pub transform: [f32; 16],
    pub viewport_width: u32,
    pub viewport_height: u32,
}

#[derive(Clone, Debug)]
pub struct WorldCrackAssetPayload {
    pub stage: u32,
    pub png_bytes: Vec<u8>,
}

#[derive(Clone, Copy, Debug, Default)]
pub struct WorldSkyRequest {
    pub visible: bool,
    pub sunrise_or_sunset: bool,
    pub dark_disc: bool,
    pub sun_angle: f32,
    pub time_of_day: f32,
    pub rain_brightness: f32,
    pub star_brightness: f32,
    pub sunrise_and_sunset_color_argb: u32,
    pub moon_phase: i32,
    pub end_flash_intensity: f32,
    pub end_flash_x_angle: f32,
    pub end_flash_y_angle: f32,
    pub sky_color_argb: u32,
}

#[derive(Clone, Copy, Debug, Default)]
pub struct WorldBackgroundRequest {
    pub enabled: bool,
    pub sky_type: u32,
    pub color_argb: u32,
    pub load_intent: u32,
    pub store_intent: u32,
    pub viewport_width: u32,
    pub viewport_height: u32,
    /// Exact vanilla sky extraction attached to the same world-frame
    /// background scope. Sun/moon geometry is transported as explicit
    /// material quads; these scalars remain available for the other sky
    /// features (stars, sunrise, and dimension-specific effects).
    pub sky: WorldSkyRequest,
}

/// Explicit world/camera mapping input for a future private shader-pack
/// volume. It is semantic frame data and does not select or bind a shader
/// resource by itself.
#[derive(Clone, Copy, Debug, Default, PartialEq)]
pub struct WorldVoxelVolumeFrame {
    pub enabled: bool,
    pub world_generation: u64,
    pub resource_generation: u64,
    pub camera_world_position: [f32; 3],
}

/// Explicit copied vanilla environment inputs. These remain separate from
/// voxel-volume mapping because shader-pack execution will validate each
/// resource contract independently.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct WorldShaderEnvironmentFrame {
    pub enabled: bool,
    pub world_generation: u64,
    pub world_time: u64,
    pub frame_counter: i32,
    pub frame_time_seconds: f32,
    pub frame_time_counter: f32,
    pub world_day: i32,
    pub moon_phase: i32,
    pub time_of_day: f32,
    pub rain_strength: f32,
    pub thunder_strength: f32,
    pub sky_darken: f32,
    pub eye_submersion: i32,
    pub screen_brightness: f32,
    pub far_plane: f32,
    /// Copied Distant Horizons block render distance. This is a gameplay
    /// configuration semantic used by selected pack sources when their shared
    /// `DISTANT_HORIZONS` branch is active; it is not a DH renderer object.
    pub distant_horizons_render_distance: i32,
    /// Copied user maximum shadow distance in chunks. This is configuration
    /// data only; all source selection and culler construction stay in Rust.
    pub configured_shadow_distance_chunks: i32,
    pub relative_eye_position: [f32; 3],
    pub sky_color: [f32; 3],
    pub darkness_light_factor: f32,
    /// Raw blindness effect factor copied from vanilla gameplay. Rust owns
    /// all shader-pack composition using this value.
    pub blindness: f32,
    /// Raw darkness effect blend copied from vanilla gameplay, distinct from
    /// the lightmap's pulsed darkness-light factor.
    pub darkness_factor: f32,
    /// Vanilla camera-cell block/sky light in Iris-compatible 0..240 steps.
    /// These are semantic light values, not a lightmap texture or handle.
    pub eye_brightness: [i32; 2],
    pub night_vision: f32,
    pub fog_color: [f32; 3],
    /// Copied vanilla/Sodium fog parameters. These are deliberately source
    /// semantics rather than Iris uniform state; shader-pack lowering derives
    /// its legacy fog compatibility values from this complete range.
    pub fog_parameter_color: [f32; 4],
    pub fog_environmental_start: f32,
    pub fog_environmental_end: f32,
    pub fog_render_distance_start: f32,
    pub fog_render_distance_end: f32,
    /// Exact vanilla `FogSkyEnd` range used by Frozen's core/sky shader.
    pub fog_sky_end: f32,
    /// Exact vanilla `FogCloudsEnd` range used by Frozen's cloud shader.
    /// It must not be inferred from sky or terrain fog ranges.
    pub fog_clouds_end: f32,
    /// Vanilla precipitation at the camera block: 0 none, 1 rain, 2 snow.
    pub biome_precipitation: i32,
    /// Canonical resource location for the camera biome. This is copied
    /// gameplay identity, never an Iris registry object or integer mapping.
    pub biome_resource_location: String,
    /// Vanilla item-model identities copied from the player inventory. Rust
    /// resolves pack-specific item IDs from its source generation.
    pub main_hand_item_model_resource_location: String,
    pub off_hand_item_model_resource_location: String,
    /// Raw vanilla emission for each held item. The selected shader-pack's
    /// legacy hand-composition rule remains Rust-owned source semantics.
    pub main_hand_item_light_emission: i32,
    pub off_hand_item_light_emission: i32,
    /// Exact scalar inputs of Minecraft's dynamic 16x16 lightmap. Rust owns
    /// image reconstruction and resource lifetime; Java never transfers its
    /// generated GPU texture or a native view.
    pub vanilla_lightmap: Option<crate::render::shaderpack::vanilla::lightmap::VanillaLightmapFrame>,
}

/// Explicit inventory of feature families that Java extracted for this frame
/// but which have not necessarily entered a selected-source writer yet. The
/// selected route records these as deliberate omissions: it must never revive
/// a Java draw or silently use the compatibility graph for the same frame.
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct WorldFeatureCoverageFrame {
    pub model_submits: u32,
    pub model_part_submits: u32,
    pub block_model_submits: u32,
    pub ordinary_block_submits: u32,
    pub item_submits: u32,
    pub custom_geometry_submits: u32,
    pub shadow_submits: u32,
    pub flame_submits: u32,
    pub name_tag_submits: u32,
    pub text_submits: u32,
    pub hitbox_submits: u32,
    pub leash_submits: u32,
    pub particle_group_submits: u32,
}

/// Explicit first-person projection/depth semantics for a future Rust-owned
/// hand pass. This is not a renderer route and has no Java/Iris state in it.
/// Individual item transforms continue to be copied through semantic mesh
/// instances, while this frame record describes their shared first-person
/// projection and the required depth-domain boundary.
#[derive(Clone, Debug, PartialEq)]
pub struct WorldFirstPersonFrame {
    pub enabled: bool,
    pub clear_depth_before: bool,
    /// The first-person mesh stream is ordered as main-hand records followed
    /// by off-hand records. This is a copied gameplay semantic, not a Java
    /// renderer callback or an inferred ordering rule.
    pub main_hand_instance_count: u32,
    pub projection_matrix: [f32; 16],
    pub model_view_matrix: [f32; 16],
    /// Copied Iris `isHandTranslucent` per hand (bit 0 main, bit 1 off).
    pub translucent_hand_mask: u8,
}

impl WorldFirstPersonFrame {
    pub(in crate::render::worldrender) fn hand_is_translucent(&self, hand: FirstPersonHand) -> bool {
        let bit = match hand {
            FirstPersonHand::Main => 1,
            FirstPersonHand::Off => 2,
        };
        self.translucent_hand_mask & bit != 0
    }
}

impl Default for WorldFirstPersonFrame {
    fn default() -> Self {
        Self {
            enabled: false,
            clear_depth_before: false,
            main_hand_instance_count: 0,
            projection_matrix: [0.0; 16],
            model_view_matrix: [0.0; 16],
            translucent_hand_mask: 0,
        }
    }
}

/// Semantic ownership of a first-person mesh record. The transport keeps
/// main-hand records before off-hand records so Rust can preserve gameplay
/// ordering without accepting Java renderer objects or callbacks.
#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub(in crate::render::worldrender) enum FirstPersonHand {
    Main,
    Off,
}

impl WorldFirstPersonFrame {
    pub(in crate::render::worldrender) fn hand_for_instance(
        &self,
        instance_index: usize,
        instance_count: usize,
    ) -> GalResult<FirstPersonHand> {
        let main_count = usize::try_from(self.main_hand_instance_count).map_err(|_| {
            GalError::invalid_argument("first-person main-hand instance count exceeds usize")
        })?;
        if main_count > instance_count {
            return Err(GalError::invalid_argument(format!(
                "first-person main-hand instance count {} exceeds stream length {}",
                main_count, instance_count
            )));
        }
        if instance_index >= instance_count {
            return Err(GalError::invalid_argument(format!(
                "first-person instance index {} exceeds stream length {}",
                instance_index, instance_count
            )));
        }
        Ok(if instance_index < main_count {
            FirstPersonHand::Main
        } else {
            FirstPersonHand::Off
        })
    }
}

impl WorldFeatureCoverageFrame {
    pub(in crate::render::worldrender) fn unsupported_families(self) -> Vec<(&'static str, u32)> {
        // Entity shadows, flames, and leashes are deliberately absent here:
        // their Java coverage counters are represented by Rust-owned textured
        // material quads and are validated against that stream below. World
        // name-tags/text are likewise represented by the copied text-quad
        // stream and validated against it before source admission. Every
        // remaining family still requires its own admitted source writer.
        [
            ("custom-geometry", self.custom_geometry_submits),
            ("hitbox", self.hitbox_submits),
        ]
        .into_iter()
        .filter(|(_, count)| *count != 0)
        .collect()
    }
}

#[derive(Clone, Debug)]
pub struct WorldPrimitiveFrame {
    pub(crate) engine_globals: Option<crate::render::shaderpack::vanilla::engine_globals::EngineGlobals>,
    pub frame_id: u64,
    pub correlation_id: u64,
    pub viewport_width: u32,
    pub viewport_height: u32,
    pub view_matrix: [f32; 16],
    pub projection_matrix: [f32; 16],
    pub voxel_volume: WorldVoxelVolumeFrame,
    pub shader_environment: WorldShaderEnvironmentFrame,
    pub feature_coverage: WorldFeatureCoverageFrame,
    pub first_person: WorldFirstPersonFrame,
    /// First-person mesh references stay separate from camera-space world
    /// batching. The eventual hand pass owns this stream's projection and
    /// cleared depth domain.
    pub first_person_mesh_instances: Vec<WorldMeshInstanceRequest>,
    pub background: WorldBackgroundRequest,
    pub segments: Vec<WorldLineSegmentRequest>,
    pub crack_quads: Vec<WorldCrackQuadRequest>,
    pub border_quads: Vec<WorldBorderQuadRequest>,
    pub material_quads: Vec<WorldMaterialQuadRequest>,
    /// Copied DH generic objects retain one semantic record per box. Rust
    /// resolves their six shaded faces in the private DH material pass.
    pub dh_generic_boxes: Vec<WorldDistantHorizonsGenericBoxRequest>,
    pub mesh_instances: Vec<WorldMeshInstanceRequest>,
    pub(crate) text_quads: Vec<features::world_text::WorldTextQuadRequest>,
    /// Visible DH LOD references are transport-only until a complete Rust LOD
    /// material/pass contract is admitted. Keeping them in the combined frame
    /// preserves the real producer ordering without borrowing legacy GL state.
    pub lod_instances: Vec<WorldLodColumnInstanceRequest>,
    pub lod_render_frame: WorldLodRenderFrame,
    /// Off-camera terrain shadow casters. The frontend expands them into
    /// `mesh_instances` before validation; see `frame::shadow_casters`.
    pub static_terrain_shadow_casters: StaticTerrainShadowCasters,
    pub static_terrain_sections: StaticTerrainSections,
}

impl WorldPrimitiveFrame {
    /// The frame's scalar state and static terrain with every per-frame
    /// geometry list empty, for a derived pass (the first-person hand) that
    /// supplies its own instances. Lists by field so a new field must choose.
    pub(crate) fn clone_without_geometry(&self) -> Self {
        Self {
            engine_globals: self.engine_globals.clone(),
            frame_id: self.frame_id,
            correlation_id: self.correlation_id,
            viewport_width: self.viewport_width,
            viewport_height: self.viewport_height,
            view_matrix: self.view_matrix,
            projection_matrix: self.projection_matrix,
            voxel_volume: self.voxel_volume.clone(),
            shader_environment: self.shader_environment.clone(),
            feature_coverage: self.feature_coverage.clone(),
            first_person: self.first_person.clone(),
            first_person_mesh_instances: Vec::new(),
            background: self.background.clone(),
            segments: Vec::new(),
            crack_quads: Vec::new(),
            border_quads: Vec::new(),
            material_quads: Vec::new(),
            dh_generic_boxes: Vec::new(),
            mesh_instances: Vec::new(),
            text_quads: Vec::new(),
            lod_instances: Vec::new(),
            lod_render_frame: self.lod_render_frame.clone(),
            static_terrain_shadow_casters: self.static_terrain_shadow_casters.clone(),
            static_terrain_sections: self.static_terrain_sections.clone(),
        }
    }
}

/// One copied off-camera static-terrain section layer: resident mesh
/// identity, integer section origin and the layer's depth policy.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct StaticTerrainShadowCaster {
    pub mesh_key: u64,
    pub mesh_generation: u64,
    pub origin: [i32; 3],
    pub depth_policy: u32,
}

#[derive(Clone, Debug, Default, PartialEq)]
pub struct StaticTerrainShadowCasters {
    /// Full-precision terrain camera shared by every caster placement.
    pub camera: [f64; 3],
    pub casters: Vec<StaticTerrainShadowCaster>,
}

/// One camera-visible static-terrain section layer: copied mesh identity,
/// integer section origin, the layer's depth policy and camera-sort flag.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct StaticTerrainSection {
    pub mesh_key: u64,
    pub depth_policy: u32,
    pub origin: [i32; 3],
    /// `WORLD_MESH_INSTANCE_FLAG_CAMERA_SORTED_QUADS` or zero.
    pub flags: u32,
}

/// The frame's camera-visible static terrain, in draw order.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct StaticTerrainSections {
    /// Full-precision terrain camera shared by every section placement.
    pub camera: [f64; 3],
    pub sections: Vec<StaticTerrainSection>,
}

#[derive(Clone, Copy, Debug)]
pub struct WorldDistantHorizonsGenericBoxRequest {
    pub min: [f32; 3],
    pub max: [f32; 3],
    pub color_argb: u32,
    pub packed_light: u32,
    pub shading: [f32; 6],
    pub ssao_enabled: bool,
    /// DH `EDhApiBlockMaterial` index; Iris feeds it to `dhMaterialId`.
    pub material: u32,
    /// Render-group ordinal within this frame: boxes of one group share its
    /// origin, so one source range (and model offset) per group is exact.
    pub group: u32,
}

impl WorldPrimitiveFrame {
    /// Copies only source-uniform values with an already proven semantic
    /// equivalent in the ordinary world frame. Ambiguous legacy shader-pack
    /// values intentionally remain absent: this helper must not invent a
    /// frame counter. `world_time` is already normalized at Java's explicit
    /// dimension-semantic boundary, and `sun_angle` is the source-defined
    /// transformation of the copied sky angle.
    pub(crate) fn source_uniform_frame(&self) -> GalResult<TerrainSourceUniformFrame> {
        validate_shader_environment(&self.shader_environment)?;
        if self
            .view_matrix
            .iter()
            .chain(self.projection_matrix.iter())
            .any(|value| !value.is_finite())
        {
            return Err(GalError::invalid_argument(
                "world-frame matrices must be finite for source uniform preparation",
            ));
        }
        let environment = &self.shader_environment;
        let (camera_world_position, camera_world_position_int, camera_world_position_fract) =
            if self.voxel_volume.enabled {
                if self
                    .voxel_volume
                    .camera_world_position
                    .iter()
                    .any(|value| !value.is_finite())
                {
                    return Err(GalError::invalid_argument(
                        "world voxel-volume camera position must be finite for source uniform preparation",
                    ));
                }
                let position = self.voxel_volume.camera_world_position;
                let integer_component = |coordinate: f32| {
                    let floored = coordinate.floor();
                    if !(i32::MIN as f32..=i32::MAX as f32).contains(&floored) {
                        return Err(GalError::invalid_argument(
                            "world voxel-volume camera position exceeds source integer camera range",
                        ));
                    }
                    Ok(floored as i32)
                };
                let integer = [
                    integer_component(position[0])?,
                    integer_component(position[1])?,
                    integer_component(position[2])?,
                ];
                let fraction = position.map(|coordinate| coordinate - coordinate.floor());
                (Some(position), Some(integer), Some(fraction))
            } else {
                (None, None, None)
            };
        let view_matrix_inverse = invert_column_major_mat4(self.view_matrix, "source view")?;
        let projection_matrix_inverse =
            invert_column_major_mat4(self.projection_matrix, "source projection")?;
        Ok(TerrainSourceUniformFrame {
            frame_counter: environment.enabled.then_some(environment.frame_counter),
            frame_modulo_eight: environment
                .enabled
                .then_some((environment.frame_counter % 8) as f32),
            world_time: environment.enabled.then_some(environment.world_time as i32),
            frame_time_seconds: environment
                .enabled
                .then_some(environment.frame_time_seconds),
            frame_time_counter: environment
                .enabled
                .then_some(environment.frame_time_counter),
            aspect_ratio: environment
                .enabled
                .then(|| self.viewport_width as f32 / self.viewport_height.max(1) as f32),
            blindness: environment.enabled.then_some(environment.blindness),
            darkness_factor: environment.enabled.then_some(environment.darkness_factor),
            max_blindness_darkness: environment
                .enabled
                .then_some(environment.blindness.max(environment.darkness_factor)),
            world_day: environment.enabled.then_some(environment.world_day),
            moon_phase: environment.enabled.then_some(environment.moon_phase),
            sun_angle: environment
                .enabled
                .then_some(source_sun_angle(environment.time_of_day)),
            celestial_time_of_day: Some(self.background.sky.time_of_day),
            rain_strength: environment.enabled.then_some(environment.rain_strength),
            thunder_strength: environment.enabled.then_some(environment.thunder_strength),
            sky_darken: environment.enabled.then_some(environment.sky_darken),
            eye_submersion: environment.enabled.then_some(environment.eye_submersion),
            screen_brightness: environment.enabled.then_some(environment.screen_brightness),
            darkness_light_factor: environment
                .enabled
                .then_some(environment.darkness_light_factor),
            night_vision: environment.enabled.then_some(environment.night_vision),
            far_plane: environment.enabled.then_some(environment.far_plane),
            distant_horizons_render_distance: environment
                .enabled
                .then_some(environment.distant_horizons_render_distance),
            relative_eye_position: environment
                .enabled
                .then_some(environment.relative_eye_position),
            // Static terrain has no current entity or block entity. These
            // source-standard sentinels are explicit Rust pass semantics, not
            // borrowed Iris uniform state.
            entity_id: environment.enabled.then_some(-1),
            current_rendered_item_id: environment.enabled.then_some(-1),
            block_entity_id: environment.enabled.then_some(-1),
            sky_color: environment.enabled.then_some(environment.sky_color),
            fog_color: environment.enabled.then_some(environment.fog_color),
            legacy_fog_parameter_color: environment
                .enabled
                .then_some(environment.fog_parameter_color),
            legacy_fog_environmental_start: environment
                .enabled
                .then_some(environment.fog_environmental_start),
            legacy_fog_environmental_end: environment
                .enabled
                .then_some(environment.fog_environmental_end),
            biome_precipitation: environment
                .enabled
                .then_some(environment.biome_precipitation),
            biome_resource_location: environment
                .enabled
                .then(|| environment.biome_resource_location.clone()),
            camera_world_position,
            camera_world_position_int,
            camera_world_position_fract,
            view_matrix: Some(self.view_matrix),
            view_matrix_inverse: Some(view_matrix_inverse),
            projection_matrix: Some(self.projection_matrix),
            projection_matrix_inverse: Some(projection_matrix_inverse),
            // Complementary declares the DH transform globals in the shared
            // terrain interface even on frames with no selected DH draw. In
            // that case the only coherent semantic value is the normal
            // camera transform; a real DH submission replaces these fields
            // with its independently copied DH matrices below.
            distant_model_view: Some(self.view_matrix),
            distant_projection: Some(self.projection_matrix),
            distant_projection_inverse: Some(projection_matrix_inverse),
            viewport_width: Some(self.viewport_width as f32),
            viewport_height: Some(self.viewport_height as f32),
            near_plane: environment.enabled.then_some(0.05),
            eye_brightness: environment.enabled.then_some(environment.eye_brightness),
            ..TerrainSourceUniformFrame::default()
        })
    }
}

pub(in crate::render::worldrender) fn source_frame_includes_distant_horizons(frame: &WorldPrimitiveFrame) -> bool {
    frame.lod_render_frame.rust_route_selected() && !frame.lod_instances.is_empty()
}

/// Entity work has its own source-program contract, but it participates in
/// the same owned frame transaction as terrain and Distant Horizons. Keep the
/// presence test semantic so route admission never mistakes a valid entity-
/// only frame for zero work.
pub(in crate::render::worldrender) fn source_frame_includes_entity_meshes(frame: &WorldPrimitiveFrame) -> bool {
    frame
        .mesh_instances
        .iter()
        .any(|instance| instance.stratum == WORLD_STRATUM_ENTITY_MESH)
}

/// A discovered source sky program is not a license to draw it. The world
/// frame must explicitly carry visible vanilla sky semantics for the selected
/// dimension; fog-obscured and menu-like frames retain the normal clear path.
pub(in crate::render::worldrender) fn source_sky_initializer_requested(frame: &WorldPrimitiveFrame) -> bool {
    frame.background.enabled
        && frame.background.sky.visible
        && matches!(
            frame.background.sky_type,
            WORLD_BACKGROUND_SKY_OVERWORLD | WORLD_BACKGROUND_SKY_NETHER | WORLD_BACKGROUND_SKY_END
        )
}

/// Iris draws its horizon during clear, even when fog/blindness hides the
/// later vanilla disc. Standard End/Nether dimensions have no such horizon;
/// custom skylit dimensions require their own semantic admission.
pub(in crate::render::worldrender) fn source_horizon_initializer_requested(frame: &WorldPrimitiveFrame) -> bool {
    frame.background.enabled && frame.background.sky_type == WORLD_BACKGROUND_SKY_OVERWORLD
}

/// The selected source uses Iris's documented solar-angle convention. This
/// transforms only the copied vanilla sky angle, without reading an Iris
/// camera, program, or renderer object.
pub(in crate::render::worldrender) fn source_sun_angle(sky_angle: f32) -> f32 {
    if sky_angle < 0.75 {
        sky_angle + 0.25
    } else {
        sky_angle - 0.75
    }
}

pub(in crate::render::worldrender) fn apply_distant_horizons_source_matrices(
    uniforms: &mut TerrainSourceUniformFrame,
    frame: &WorldLodRenderFrame,
) -> GalResult<()> {
    let matrices = [
        ("model-view", frame.model_view_matrix),
        ("projection", frame.projection_matrix),
        ("projection inverse", frame.projection_inverse_matrix),
    ];
    for (name, matrix) in matrices {
        if matrix.iter().any(|value| !value.is_finite()) {
            return Err(GalError::invalid_argument(format!(
                "Distant Horizons source {name} matrix must be finite"
            )));
        }
    }
    let inverse_residual = matrix4_max_abs_difference(
        matrix4_column_major_multiply(frame.projection_matrix, frame.projection_inverse_matrix),
        matrix4_identity(),
    );
    if inverse_residual > 0.001 {
        return Err(GalError::invalid_argument(format!(
            "Distant Horizons source projection inverse does not reconstruct identity (max residual {inverse_residual:.6})"
        )));
    }
    uniforms.distant_model_view = Some(frame.model_view_matrix);
    uniforms.distant_projection = Some(frame.projection_matrix);
    uniforms.distant_projection_inverse = Some(frame.projection_inverse_matrix);
    // Iris keeps gbufferModelView tied to the normal captured camera while
    // its DH program supplies the separate DH transform through dhModelView.
    // The selected source uses both spaces when deriving player-relative
    // positions, so replacing the normal pair here corrupts that conversion.
    Ok(())
}

/// Applies Iris's Distant Horizons projection pair to a shared post-terrain
/// source pass. Fullscreen consumers keep the normal gbuffer matrices for
/// ordinary terrain pixels; `dhProjection`/`dhProjectionInverse` follow Iris's
/// fullscreen definition (see below), not DH's own geometry-pass pair.
pub(in crate::render::worldrender) fn apply_distant_horizons_fullscreen_projection(
    uniforms: &mut TerrainSourceUniformFrame,
    frame: &WorldLodRenderFrame,
) -> GalResult<()> {
    for (name, matrix) in [
        ("projection", frame.projection_matrix),
        ("projection inverse", frame.projection_inverse_matrix),
    ] {
        if matrix.iter().any(|value| !value.is_finite()) {
            return Err(GalError::invalid_argument(format!(
                "Distant Horizons fullscreen source {name} matrix must be finite"
            )));
        }
    }
    let inverse_residual = matrix4_max_abs_difference(
        matrix4_column_major_multiply(frame.projection_matrix, frame.projection_inverse_matrix),
        matrix4_identity(),
    );
    if inverse_residual > 0.001 {
        return Err(GalError::invalid_argument(format!(
            "Distant Horizons fullscreen projection inverse does not reconstruct identity (max residual {inverse_residual:.6})"
        )));
    }
    // Iris (MatrixUniforms / DHCompat.getProjection) gives fullscreen
    // stages `perspective(gbuffer fov, gbuffer aspect, DH near, Iris far)`,
    // with Iris's far plane `(DH block distance + 512) * sqrt(2)`, rather
    // than the pair DH rendered its depth with. Frozen's deferred stages
    // reconstruct DH distance in that space; reproduce it exactly.
    let normal = uniforms.projection_matrix.ok_or_else(|| {
        GalError::invalid_argument(
            "Distant Horizons fullscreen projection requires the gbuffer projection",
        )
    })?;
    let dh_blocks = uniforms.distant_horizons_render_distance.ok_or_else(|| {
        GalError::invalid_argument(
            "Distant Horizons fullscreen projection requires the DH render distance",
        )
    })?;
    let dh = frame.projection_matrix;
    let near = dh[14] / (dh[10] - 1.0);
    let far = ((dh_blocks as f64 + 512.0) * std::f64::consts::SQRT_2) as f32;
    if !near.is_finite() || near <= 0.0 || !far.is_finite() || far <= near {
        return Err(GalError::invalid_argument(format!(
            "Distant Horizons fullscreen clip planes are invalid (near {near}, far {far})"
        )));
    }
    let mut projection = [0.0_f32; 16];
    projection[0] = normal[0];
    projection[5] = normal[5];
    projection[10] = (far + near) / (near - far);
    projection[11] = -1.0;
    projection[14] = 2.0 * far * near / (near - far);
    uniforms.distant_projection_inverse = Some(invert_column_major_mat4(
        projection,
        "Iris Distant Horizons fullscreen projection",
    )?);
    uniforms.distant_projection = Some(projection);
    Ok(())
}

#[derive(Clone, Debug, Default)]
pub struct WorldPrimitiveSubmitStats {
    pub submission_id: u64,
    pub segment_count: u64,
    pub vertex_count: u64,
    pub primitive_batch_count: u64,
    pub cache_hits: u64,
    pub cache_misses: u64,
    pub outline_cache_hits: u64,
    pub outline_cache_misses: u64,
    pub crack_cache_hits: u64,
    pub crack_cache_misses: u64,
    pub resource_creates: u64,
    pub depth_attachment_creates: u64,
    pub depth_attachment_reuses: u64,
    pub depth_attachment_retires: u64,
    pub command_lists: u64,
    pub command_ops: u64,
    pub world_draws: u64,
    pub crack_quad_count: u64,
    pub crack_batch_count: u64,
    pub crack_draw_count: u64,
    pub border_quad_count: u64,
    pub border_batch_count: u64,
    pub border_draw_count: u64,
    pub material_quad_count: u64,
    pub material_batch_count: u64,
    pub material_draw_count: u64,
    /// Whether the Rust-owned DH SSAO pass was appended for this submission.
    /// This is diagnostic semantic state, not a backend handle or pipeline ID.
    pub lod_ssao_pass_appended: bool,
    /// Number of Rust-owned DH fullscreen composites appended at the semantic
    /// fade boundaries. NONE/SINGLE_PASS use one; DOUBLE_PASS uses two.
    pub lod_direct_composite_pass_count: u32,
    /// Semantic text execution coverage. These values are populated after
    /// glyph validation and before backend lowering, so capture gates can
    /// distinguish absent text work from submitted-but-invisible text.
    pub world_text_quad_count: u64,
    pub world_text_batch_count: u64,
    pub world_text_draw_count: u64,
    pub world_text_clip_xy_visible_quad_count: u64,
    pub world_text_first_ndc_bounds: Option<[f32; 4]>,
    pub world_text_first_ndc_corners: Option<[[f32; 2]; 4]>,
    pub world_text_ndc_bounds_sample: Vec<[f32; 4]>,
    pub mesh_instance_count: u64,
    pub mesh_batch_count: u64,
    pub mesh_draw_count: u64,
    /// Bounded audit-only breakdown for the selected source terrain pass.
    /// These are semantic batches/draws, not backend handles or GL/Vulkan
    /// state, and remain zero for the ordinary non-source routes.
    pub source_opaque_batch_count: u64,
    pub source_cutout_batch_count: u64,
    pub source_opaque_instance_count: u64,
    pub source_cutout_instance_count: u64,
    pub source_opaque_index_count: u64,
    pub source_cutout_index_count: u64,
    pub source_opaque_draw_count: u64,
    pub source_cutout_draw_count: u64,
    pub source_opaque_draw_index_count: u64,
    pub source_cutout_draw_index_count: u64,
    pub source_translucent_batch_count: u64,
    pub source_translucent_instance_count: u64,
    pub source_translucent_index_count: u64,
    pub source_translucent_draw_count: u64,
    pub source_translucent_draw_index_count: u64,
    /// Bounded semantic coverage for the selected `gbuffers_entities` writer.
    /// These counts describe copied entity mesh work only; they contain no
    /// backend object or Java renderer identity.
    pub source_entity_instance_count: u64,
    pub source_entity_draw_count: u64,
    pub source_entity_index_count: u64,
    /// Bounded audit-only breakdown for the source-derived
    /// `gbuffers_textured` writer. These are semantic primitive counts and
    /// direct-draw vertices, never backend texture or pipeline identities.
    pub source_material_batch_count: u64,
    pub source_material_quad_count: u64,
    pub source_material_draw_count: u64,
    pub source_material_vertex_count: u64,
    /// Per-writer source-material coverage. These retain the distinction
    /// between admitted semantic work and the writer that actually executed
    /// it in the one selected-source submission.
    pub source_textured_material_batch_count: u64,
    pub source_textured_material_quad_count: u64,
    pub source_textured_material_draw_count: u64,
    pub source_textured_material_vertex_count: u64,
    /// Count of copied entity-model quads carried by the textured writer.
    pub source_entity_model_quad_count: u64,
    pub source_weather_batch_count: u64,
    pub source_weather_quad_count: u64,
    pub source_weather_draw_count: u64,
    pub source_weather_vertex_count: u64,
    pub source_cloud_batch_count: u64,
    pub source_cloud_quad_count: u64,
    pub source_cloud_draw_count: u64,
    pub source_cloud_vertex_count: u64,
    pub source_cloud_suppressed_quad_count: u64,
    pub source_cloud_faces_suppressed: bool,
    /// A source pack can intentionally discard vanilla cloud faces and
    /// synthesize clouds in a Rust-owned fullscreen stage. Keep that evidence
    /// separate from material-stream draws.
    pub source_cloud_fullscreen_stage_count: u64,
    pub border_cache_hits: u64,
    pub border_cache_misses: u64,
    pub material_cache_hits: u64,
    pub material_cache_misses: u64,
    pub mesh_cache_hits: u64,
    pub mesh_cache_misses: u64,
    pub border_asset_generation: u64,
    pub border_asset_payload_bytes: u64,
    pub border_asset_update_failures: u64,
    pub crack_asset_generation: u64,
    pub crack_asset_payload_bytes: u64,
    pub crack_asset_update_failures: u64,
    pub material_asset_generation: u64,
    pub material_asset_payload_bytes: u64,
    pub material_asset_update_failures: u64,
    pub mesh_asset_generation: u64,
    pub mesh_asset_payload_bytes: u64,
    pub mesh_asset_update_failures: u64,
    pub background_clear_count: u64,
    pub background_diagnostic_fallback_count: u64,
    pub background_sky_type: u64,
    pub background_color_argb: u64,
    pub profile: WholeFrameProfile,
}

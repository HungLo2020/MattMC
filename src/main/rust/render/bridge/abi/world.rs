//! Per-frame world records: quads, lines, mesh instances, background, environment, particles and orbs.

use super::*;

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiWorldLineSegmentRequest {
    pub byte_size: u32,
    pub stratum: u32,
    pub style: u32,
    pub depth_policy: u32,
    pub color_argb: u32,
    pub line_width: f32,
    pub start_x: f32,
    pub start_y: f32,
    pub start_z: f32,
    pub end_x: f32,
    pub end_y: f32,
    pub end_z: f32,
    pub viewport_width: i32,
    pub viewport_height: i32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiWorldCrackQuadRequest {
    pub byte_size: u32,
    pub stratum: u32,
    pub stage: u32,
    pub depth_policy: u32,
    pub blend_policy: u32,
    pub cull_policy: u32,
    pub color_argb: u32,
    pub reserved0: u32,
    pub p0_x: f32,
    pub p0_y: f32,
    pub p0_z: f32,
    pub p1_x: f32,
    pub p1_y: f32,
    pub p1_z: f32,
    pub p2_x: f32,
    pub p2_y: f32,
    pub p2_z: f32,
    pub p3_x: f32,
    pub p3_y: f32,
    pub p3_z: f32,
    pub viewport_width: i32,
    pub viewport_height: i32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiWorldBorderQuadRequest {
    pub byte_size: u32,
    pub stratum: u32,
    pub texture_id: u32,
    pub depth_policy: u32,
    pub blend_policy: u32,
    pub cull_policy: u32,
    pub color_argb: u32,
    pub reserved0: u32,
    pub border_size: f32,
    pub distance_to_border: f32,
    pub scroll_u: f32,
    pub scroll_v: f32,
    pub uv_u: f32,
    pub uv_v: f32,
    pub uv_width: f32,
    pub uv_height: f32,
    pub p0_x: f32,
    pub p0_y: f32,
    pub p0_z: f32,
    pub p1_x: f32,
    pub p1_y: f32,
    pub p1_z: f32,
    pub p2_x: f32,
    pub p2_y: f32,
    pub p2_z: f32,
    pub p3_x: f32,
    pub p3_y: f32,
    pub p3_z: f32,
    pub viewport_width: i32,
    pub viewport_height: i32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiWorldMaterialQuadRequest {
    pub byte_size: u32,
    pub stratum: u32,
    pub material_id: u32,
    pub texture_id: u32,
    pub material_mode: u32,
    pub depth_policy: u32,
    pub cull_policy: u32,
    pub topology: u32,
    pub color_argb: u32,
    /// `0` preserves the historical CCW default. This direct-record field is
    /// part of the existing winding contract and must not be reused.
    pub winding: u32,
    pub p0_x: f32,
    pub p0_y: f32,
    pub p0_z: f32,
    pub p1_x: f32,
    pub p1_y: f32,
    pub p1_z: f32,
    pub p2_x: f32,
    pub p2_y: f32,
    pub p2_z: f32,
    pub p3_x: f32,
    pub p3_y: f32,
    pub p3_z: f32,
    pub uv0_u: f32,
    pub uv0_v: f32,
    pub uv1_u: f32,
    pub uv1_v: f32,
    pub uv2_u: f32,
    pub uv2_v: f32,
    pub uv3_u: f32,
    pub uv3_v: f32,
    pub viewport_width: i32,
    pub viewport_height: i32,
    /// Named source-pack material family. This is semantic program selection,
    /// never an Iris program or backend object.
    pub source_program: u32,
    /// Unlit producer color retained for a future source-derived material
    /// program. `color_argb` preserves the ordinary prelit material route.
    pub source_color_argb: u32,
    /// Vanilla packed block/sky light retained without baking it into source
    /// shader inputs.
    pub packed_light: u32,
    /// Semantic source UV coordinate space. It is ignored by the ordinary
    /// material route and consumed only by future source-derived staging.
    pub source_uv_space: u32,
    /// Per-vertex source modulation. Uniform quads repeat `source_color_argb`
    /// and `packed_light`; the legacy/full-record transport is reserved for
    /// the rare dynamic primitive that needs distinct endpoint values.
    pub vertex0_color_argb: u32,
    pub vertex1_color_argb: u32,
    pub vertex2_color_argb: u32,
    pub vertex3_color_argb: u32,
    pub vertex0_packed_light: u32,
    pub vertex1_packed_light: u32,
    pub vertex2_packed_light: u32,
    pub vertex3_packed_light: u32,
    pub block_entity_id: i32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiWorldMaterialTableRecord {
    pub byte_size: u32,
    pub stratum: u32,
    pub material_id: u32,
    pub texture_id: u32,
    pub material_mode: u32,
    pub depth_policy: u32,
    pub cull_policy: u32,
    pub topology: u32,
    pub winding: u32,
    pub source_program: u32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiWorldMaterialCompactQuadRequest {
    pub byte_size: u32,
    pub material_index: u32,
    pub color_argb: u32,
    /// Semantic source UV coordinate space, retained per quad because one
    /// material table entry can be used by more than one source family.
    pub source_uv_space: u32,
    pub p0_x: f32,
    pub p0_y: f32,
    pub p0_z: f32,
    pub p1_x: f32,
    pub p1_y: f32,
    pub p1_z: f32,
    pub p2_x: f32,
    pub p2_y: f32,
    pub p2_z: f32,
    pub p3_x: f32,
    pub p3_y: f32,
    pub p3_z: f32,
    pub uv0_u: f32,
    pub uv0_v: f32,
    pub uv1_u: f32,
    pub uv1_v: f32,
    pub uv2_u: f32,
    pub uv2_v: f32,
    pub uv3_u: f32,
    pub uv3_v: f32,
    pub source_color_argb: u32,
    pub packed_light: u32,
    pub block_entity_id: i32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiWorldMeshInstanceRecord {
    pub byte_size: u32,
    pub stratum: u32,
    pub mesh_section_index: u32,
    pub depth_policy: u32,
    pub cull_policy: u32,
    pub winding: u32,
    pub color_argb: u32,
    pub viewport_width: i32,
    pub viewport_height: i32,
    pub mesh_key: u64,
    pub mesh_generation: u64,
    /// Semantic entity/material identity for source-selected entity passes.
    /// Zero is the explicit generic default, not a backend handle or renderer
    /// object.
    pub entity_id: i32,
    /// Straight ARGB semantic entity-color override. A zero alpha value means
    /// no override, matching the source-program mix contract.
    pub entity_color_argb: u32,
    pub transform: [f32; 16],
    /// Straight ARGB semantic outline color consumed by the Rust-owned entity
    /// mask and post-effect chain; zero means no outline request.
    pub outline_color_argb: u32,
    /// Explicit semantic instance flags. Bit 0 requests outline-only drawing:
    /// the mesh feeds the Rust outline mask but is omitted from the regular
    /// material pass.
    pub flags: u32,
    /// Copied vanilla block-state registry identity for block-entity shader
    /// semantics. -1 is the explicit non-block-entity value; this is never an
    /// Iris map lookup or backend handle.
    pub block_entity_id: i32,
    /// 0: explicit matrix; 1: semantic section origin/full-precision camera.
    pub terrain_placement_mode: u32,
    pub terrain_origin: [i32; 3],
    pub terrain_camera: [f64; 3],
    pub item_foil_mode: u32,
    pub item_foil_clock_millis: u64,
    pub item_foil_speed: f64,
    pub item_foil_strength: f32,
    pub decal_foil_mode: u32,
    pub decal_normal_mode: u32,
    pub decal_model_pose: [f32; 16],
    pub decal_normal_pose: [f32; 9],
    /// Optional authored model collection order; mode 0 is absent, 1 is present.
    pub model_submission_order_mode: u32,
    pub model_submission_order: i32,
    /// Copied packed vanilla UV2 light. This is an instance semantic lane, not
    /// a Java lightmap object or backend resource.
    pub packed_light: u32,
    /// Bounded immutable entity-culling CPU data; zero mode is canonical absent.
    pub entity_culling_mode: u32,
    pub entity_culling_flags: u32,
    pub entity_culling_bounds: [f64; 6],
    pub entity_culling_leash_bounds: [f64; 6],
    pub entity_culling_camera: [f64; 3],
}

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiWorldBackgroundRequest {
    pub byte_size: u32,
    pub enabled: u32,
    pub sky_type: u32,
    pub load_intent: u32,
    pub store_intent: u32,
    pub color_argb: u32,
    pub viewport_width: i32,
    pub viewport_height: i32,
    /// Appended vanilla sky semantics. These are copied gameplay/render-state
    /// values, not Java renderer resources, Iris state, or backend handles.
    pub sky_visible: u32,
    pub sky_sunrise_or_sunset: u32,
    pub sky_dark_disc: u32,
    pub sky_reserved0: u32,
    pub sky_sun_angle: f32,
    pub sky_time_of_day: f32,
    pub sky_rain_brightness: f32,
    pub sky_star_brightness: f32,
    pub sky_sunrise_and_sunset_color_argb: u32,
    pub sky_moon_phase: i32,
    pub sky_end_flash_intensity: f32,
    pub sky_end_flash_x_angle: f32,
    pub sky_end_flash_y_angle: f32,
    pub sky_color_argb: u32,
}

/// Coarse, backend-neutral world and camera semantics for a future
/// Rust-owned volume mapping. This deliberately carries no resource, shader,
/// renderer, or native backend object.
#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiWorldVoxelVolumeFrame {
    pub byte_size: u32,
    pub enabled: u32,
    pub reserved0: u32,
    pub reserved1: u32,
    pub world_generation: u64,
    pub resource_generation: u64,
    pub camera_x: f32,
    pub camera_y: f32,
    pub camera_z: f32,
    pub reserved2: u32,
}

/// Coarse, copied vanilla environment semantics for future Rust-owned
/// shader-pack execution. This deliberately excludes Iris state, shader
/// objects, GL/Vulkan objects, and pack-specific derived values.
#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiWorldShaderEnvironmentFrame {
    pub byte_size: u32,
    pub enabled: u32,
    pub frame_counter: i32,
    pub world_day: i32,
    pub world_generation: u64,
    pub world_time: u64,
    pub frame_time_seconds: f32,
    pub frame_time_counter: f32,
    pub time_of_day: f32,
    pub rain_strength: f32,
    pub thunder_strength: f32,
    pub sky_darken: f32,
    pub moon_phase: i32,
    /// Vanilla camera-submersion classification. This is semantic game
    /// state, never an Iris fog object: 0 none, 1 water, 2 lava, 3 powder
    /// snow.
    pub eye_submersion: i32,
    pub screen_brightness: f32,
    pub far_plane: f32,
    pub relative_eye_x: f32,
    pub relative_eye_y: f32,
    pub relative_eye_z: f32,
    pub sky_color_r: f32,
    pub sky_color_g: f32,
    pub sky_color_b: f32,
    pub darkness_light_factor: f32,
    pub night_vision: f32,
    pub fog_color_r: f32,
    pub fog_color_g: f32,
    pub fog_color_b: f32,
    /// Vanilla precipitation at the camera block: 0 none, 1 rain, 2 snow.
    /// Rust owns shader-pack-specific smoothing and use of this semantic.
    pub biome_precipitation: i32,
    /// Canonical vanilla or modded biome resource location at the camera.
    /// This remains gameplay data; Rust evaluates source-defined biome maps.
    pub biome_resource_location_utf8: FfiBytes,
    /// Canonical vanilla item-model identity in the player's main hand. An
    /// empty value represents no semantic held item; Rust owns `item.properties`
    /// resolution and never receives an Iris integer map.
    pub main_hand_item_model_resource_location_utf8: FfiBytes,
    /// Canonical vanilla item-model identity in the player's off hand. See
    /// `main_hand_item_model_resource_location_utf8` for ownership rules.
    pub off_hand_item_model_resource_location_utf8: FfiBytes,
    /// Raw vanilla block-light emission for the main-hand item. Rust applies
    /// pack-owned hand composition and never receives an Iris light provider.
    pub main_hand_item_light_emission: i32,
    /// Raw vanilla block-light emission for the off-hand item.
    pub off_hand_item_light_emission: i32,
    /// Exact scalar inputs for Rust's owned vanilla lightmap reconstruction.
    /// These are appended to preserve all prior Panama field offsets.
    pub lightmap_enabled: u32,
    pub lightmap_reserved: u32,
    pub lightmap_generation: u64,
    pub lightmap_ambient_light_factor: f32,
    pub lightmap_sky_factor: f32,
    pub lightmap_block_factor: f32,
    pub lightmap_night_vision_factor: f32,
    pub lightmap_darkness_scale: f32,
    pub lightmap_darken_world_factor: f32,
    pub lightmap_brightness_factor: f32,
    pub lightmap_sky_light_r: f32,
    pub lightmap_sky_light_g: f32,
    pub lightmap_sky_light_b: f32,
    pub lightmap_ambient_r: f32,
    pub lightmap_ambient_g: f32,
    pub lightmap_ambient_b: f32,
    /// Appended semantic shader-frame inputs. These preserve every prior
    /// Panama offset and contain only copied vanilla gameplay values.
    pub blindness: f32,
    pub darkness_factor: f32,
    pub eye_brightness_block: i32,
    pub eye_brightness_sky: i32,
    /// Copied vanilla/Sodium fog parameters. These preserve all earlier
    /// Panama offsets and are semantic inputs only: Rust derives any
    /// shader-pack compatibility representation from them.
    pub fog_parameter_color_r: f32,
    pub fog_parameter_color_g: f32,
    pub fog_parameter_color_b: f32,
    pub fog_parameter_color_a: f32,
    pub fog_environmental_start: f32,
    pub fog_environmental_end: f32,
    pub fog_render_distance_start: f32,
    pub fog_render_distance_end: f32,
    /// Appended copied DH configuration semantic. It intentionally carries no
    /// renderer instance, Java callback, or backend state.
    pub distant_horizons_render_distance: i32,
    /// Appended vanilla SKY pipeline fog range. This is immutable semantic
    /// game data, never a Java uniform buffer, GL state, or Iris object.
    pub fog_sky_end: f32,
    /// Appended vanilla CLOUDS pipeline fog range. It is distinct from the
    /// sky range in Frozen and remains copied gameplay data only.
    pub fog_clouds_end: f32,
    /// Appended in ABI v67: copied user max-shadow distance in chunks.
    /// Rust combines this setting with source directives; no Iris culler or
    /// renderer state crosses the bridge.
    pub configured_shadow_distance_chunks: i32,
}

/// Coarse inventory of Java feature families observed during the same real
/// whole-frame extraction. It is diagnostic route policy only: no Java
/// renderer object, render type, shader object, or backend state crosses FFI.
#[repr(C)]
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct FfiWorldFeatureCoverage {
    pub byte_size: u32,
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

/// Copied first-person frame semantics. This is deliberately an append-only
/// frame record: Java supplies neither an Iris hand pass nor any backend
/// object. Per-item transforms remain in ordinary semantic mesh records; the
/// hand pass owns its model-view, projection, and isolated depth domain
/// separately.
#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiWorldFirstPersonFrame {
    pub byte_size: u32,
    pub enabled: u32,
    pub clear_depth_before: u32,
    /// Number of leading entries in `world_first_person_mesh_instances` that
    /// belong to the main hand. Remaining entries belong to the off hand.
    /// This repurposes an ABI-reserved lane without changing the record size
    /// or any established Panama offset.
    pub main_hand_instance_count: u32,
    pub projection_matrix: [f32; 16],
    /// Appended in ABI v23 to preserve every pre-existing field offset.
    pub model_view_matrix: [f32; 16],
    /// Appended in ABI v66. Bit 0: main hand, bit 1: off hand. A hand whose
    /// copied held stack is a translucent-layer block item draws in the late
    /// translucent-hand pass (Iris `HandRenderer.isHandTranslucent`).
    pub translucent_hand_mask: u32,
}

/// One copied, backend-neutral world-text glyph quad. The atlas asset is a
/// semantic Rust-owned resource identity; it is never a Java font texture or
/// a native backend handle.
#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiWorldTextQuadRequest {
    pub byte_size: u32,
    pub flags: u32,
    pub depth_policy: u32,
    pub packed_light: u32,
    pub color_argb: u32,
    pub reserved0: u32,
    pub asset_id: u64,
    pub atlas_generation: u64,
    pub atlas_revision: u64,
    pub distance_to_camera_sq: f64,
    pub model_view_matrix: [f32; 16],
    pub positions: [f32; 12],
    pub uvs: [f32; 8],
    /// Scoped semantic block-entity identity; `-1` means no block entity.
    pub block_entity_id: i32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiWorldDistantHorizonsGenericBoxRecord {
    pub byte_size: u32,
    /// Bit 0 retains the producer's SSAO request as semantic provenance.
    pub flags: u32,
    pub min: [f32; 3],
    pub max: [f32; 3],
    pub color_argb: u32,
    pub packed_light: u32,
    /// North, south, east, west, top, bottom directional multipliers.
    pub shading: [f32; 6],
}

/// ABI v69: one off-camera static-terrain shadow caster. Java copies only the
/// section's resident mesh identity, integer origin and layer depth policy;
/// Rust expands it into a shadow-only terrain instance against the frame's
/// terrain camera. No transform, flags or per-instance record is transported.
#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiStaticTerrainShadowCaster {
    pub mesh_key: u64,
    pub mesh_generation: u64,
    pub origin: [i32; 3],
    pub depth_policy: u32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiWorldExperienceOrbInstanceRecord {
    pub byte_size: u32,
    /// Insert before this ordinal in the non-orb mesh instance stream.
    pub mesh_index: u32,
    pub mesh_key: u64,
    pub mesh_generation: u64,
    pub entity_transform: [f32; 16],
    pub camera_orientation: [f32; 4],
    pub entity_id: i32,
    pub reserved0: u32,
    pub entity_culling_mode: u32,
    pub entity_culling_flags: u32,
    pub entity_culling_bounds: [f64; 6],
    pub entity_culling_leash_bounds: [f64; 6],
    pub shadow_only: u32,
    pub entity_culling_camera: [f64; 3],
}

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiWorldParticleQuadRequest {
    pub byte_size: u32,
    pub texture_id: u32,
    /// ABI 51: 0 ordinary opaque, 1 ordinary translucent, 2 terrain opaque,
    /// 3 terrain cutout. Backend policy is chosen in Rust, not transported.
    pub surface_kind: u32,
    /// Insert before this index in the decoded non-particle material stream.
    /// Nondecreasing values retain input order, including coincident particles.
    pub material_index: u32,
    pub center: [f32; 3],
    pub rotation: [f32; 4],
    pub size: f32,
    pub uv_bounds: [f32; 4],
    pub color_argb: u32,
    pub packed_light: u32,
}

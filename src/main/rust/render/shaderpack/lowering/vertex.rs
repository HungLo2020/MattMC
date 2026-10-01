//! Vertex-surface lowering and the semantic preambles it inserts.

use super::*;

/// Lowers the audited terrain vertex compatibility names into an explicit
/// indexed source stream. This is not a generic GLSL compatibility shim: a
/// source declaring any unrecognized legacy attribute is rejected rather than
/// assigned an invented location or default value.
pub fn lower_terrain_vertex_surface(
    source: &PreprocessedShaderSource,
) -> GalResult<LoweredTerrainVertexSource> {
    let uniform_contract = derive_terrain_source_uniform_contract(source, source)?;
    let varying_contract = derive_terrain_source_varying_contract(source, source)?;
    let opaque_resource_contract = derive_terrain_source_opaque_resource_contract(source, source)?;
    lower_source_vertex_surface_with_contracts(
        source,
        &uniform_contract,
        &varying_contract,
        &opaque_resource_contract,
        SourceTransformSemantics::Terrain,
    )
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(super) enum SourceTransformSemantics {
    Terrain,
    Entity,
    Hand,
    TexturedMaterial,
    Weather,
    Cloud,
    Shadow,
    DistantHorizons,
    Fullscreen,
}

impl SourceTransformSemantics {
    pub(super) fn model_view_expression(self) -> &'static str {
        match self {
            // The copied hand instance contains the inverse world view and
            // the first-person item pose. Match the OpenGL model-view stack:
            // view * (inverse_view * hand_pose).
            Self::Hand => "(gbufferModelView * vulkanic_source_model_transform)",
            Self::Terrain | Self::Entity => "(gbufferModelView * vulkanic_source_model_transform)",
            Self::Shadow => "(shadowModelView * vulkanic_source_model_transform)",
            Self::DistantHorizons => "(dhModelView * vulkanic_source_model_transform)",
            Self::TexturedMaterial | Self::Weather | Self::Cloud | Self::Fullscreen => {
                "gbufferModelView"
            }
        }
    }

    pub(super) fn model_view_uniform(self) -> &'static str {
        match self {
            Self::Terrain => "gbufferModelView",
            Self::Entity => "gbufferModelView",
            Self::Hand => "gbufferModelView",
            Self::TexturedMaterial => "gbufferModelView",
            Self::Weather => "gbufferModelView",
            Self::Cloud => "gbufferModelView",
            Self::Shadow => "shadowModelView",
            Self::DistantHorizons => "dhModelView",
            Self::Fullscreen => "vulkanic_source_fullscreen_unused_model_view",
        }
    }

    pub(super) fn projection_uniform(self) -> &'static str {
        match self {
            Self::Terrain => "gbufferProjection",
            Self::Entity => "gbufferProjection",
            // Iris keeps gbufferProjection as the world camera uniform while
            // its legacy GL projection changes for first-person geometry.
            Self::Hand => "vulkanic_source_hand_projection",
            Self::TexturedMaterial => "gbufferProjection",
            Self::Weather => "gbufferProjection",
            Self::Cloud => "gbufferProjection",
            Self::Shadow => "shadowProjection",
            Self::DistantHorizons => "dhProjection",
            Self::Fullscreen => "vulkanic_source_fullscreen_unused_projection",
        }
    }
}

pub(super) fn lower_source_vertex_surface_with_contracts(
    source: &PreprocessedShaderSource,
    uniform_contract: &TerrainSourceUniformContract,
    varying_contract: &TerrainSourceVaryingContract,
    opaque_resource_contract: &TerrainSourceOpaqueResourceContract,
    transforms: SourceTransformSemantics,
) -> GalResult<LoweredTerrainVertexSource> {
    let mut lowered = upgrade_version(source.expanded_source())?;
    lowered = remove_known_legacy_attributes(&lowered)?;
    lowered = strip_nonopaque_uniforms(&lowered)?;
    let uses_legacy_fog = lower_legacy_fog(&mut lowered);
    lowered = replace_identifier(&lowered, "varying", "out");
    for (legacy, explicit) in [
        ("gl_TextureMatrix", "vulkanic_source_texture_matrix"),
        ("gl_MultiTexCoord0", "vulkanic_source_atlas_uv"),
        ("gl_MultiTexCoord1", "vulkanic_source_lightmap_uv"),
        ("gl_Color", "vulkanic_source_vertex_color"),
        ("gl_NormalMatrix", "vulkanic_source_normal_matrix"),
        ("gl_Normal", "vulkanic_source_normal"),
        ("gl_ModelViewMatrix", "vulkanic_source_model_view"),
        // Legacy source still spells the projection built-in in a few DH
        // include paths. That builtin belongs to the transform family being
        // lowered; routing it through gbufferProjection makes far geometry
        // use the near-terrain clip volume.
        ("gl_ProjectionMatrix", transforms.projection_uniform()),
        ("gl_VertexID", "gl_VertexIndex"),
        ("gl_Vertex", "vulkanic_source_position"),
        ("mc_Entity", "vulkanic_source_entity"),
        ("mc_midTexCoord", "vulkanic_source_mid_tex_coord"),
        ("at_tangent", "vulkanic_source_tangent"),
        ("at_midBlock", "vulkanic_source_mid_block"),
        ("dhMaterialId", "vulkanic_source_dh_material_id"),
        (
            "GetLightMapCoordinates",
            "vulkanic_source_dh_lightmap_coordinates",
        ),
        ("ftransform", "vulkanic_source_ftransform"),
        ("texture2D", "texture"),
    ] {
        lowered = replace_identifier(&lowered, legacy, explicit);
    }
    // The OpenGL pack reconstructs player-space geometry with
    // `gbufferModelViewInverse * gl_ModelViewMatrix * gl_Vertex`.  A Rust
    // source instance already carries the camera-relative model transform;
    // applying that legacy inverse/re-application sequence would feed a
    // camera-relative position through a second, incompatible reconstruction
    // path.  Preserve the pack's final camera projection while making the
    // semantic world-position step explicit.  This is limited to terrain and
    // entity meshes; hand/material families have distinct pose contracts.
    if matches!(
        transforms,
        SourceTransformSemantics::Terrain | SourceTransformSemantics::Entity
    ) {
        lowered = lowered.replace(
            "gbufferModelViewInverse * vulkanic_source_model_view * vulkanic_source_position",
            "vulkanic_source_model_transform * vulkanic_source_position",
        );
    }
    apply_selected_source_wave_probe(&mut lowered)?;
    apply_selected_source_taa_probe(&mut lowered)?;
    apply_selected_source_vertex_probe(&mut lowered)?;
    lowered = apply_varying_locations(&lowered, VaryingStorage::Out, varying_contract)?;
    lowered = apply_opaque_resource_bindings(&lowered, opaque_resource_contract)?;
    if uses_legacy_fog {
        lowered = insert_after_version(&lowered, LEGACY_FOG_SEMANTIC_PREAMBLE)?;
    }
    lowered = insert_after_version(&lowered, &uniform_block(uniform_contract))?;
    lowered = insert_after_version(&lowered, &vertex_semantic_preamble(transforms))?;
    // Shader-pack source is authored for OpenGL clip depth. The Vulkan backend
    // selects the explicit zero-to-one convention through a source define, so
    // apply the conversion after the pack has completed every vertex write.
    // Keeping it in lowered source leaves GAL state backend-neutral and keeps
    // the OpenGL source byte-for-byte equivalent at runtime.
    lowered = append_clip_depth_convention_finalizer(&lowered)?;
    apply_selected_source_vertex_probe_for_entry(&mut lowered, source.entry_path())?;
    dump_selected_source_lowered_shader(source.entry_path(), "vertex", &lowered);
    let remaining_dialect = analyze_glsl_text(source.entry_path(), &lowered);
    Ok(LoweredTerrainVertexSource {
        entry_path: source.entry_path().to_string(),
        source: lowered,
        remaining_dialect,
    })
}

pub(super) fn vertex_semantic_preamble(transforms: SourceTransformSemantics) -> String {
    if transforms == SourceTransformSemantics::DistantHorizons {
        return DISTANT_HORIZONS_VERTEX_SEMANTIC_PREAMBLE.to_string();
    }
    if matches!(
        transforms,
        SourceTransformSemantics::TexturedMaterial
            | SourceTransformSemantics::Weather
            | SourceTransformSemantics::Cloud
    ) {
        return TEXTURED_MATERIAL_VERTEX_SEMANTIC_PREAMBLE.to_string();
    }
    VERTEX_SEMANTIC_PREAMBLE_TEMPLATE
        .replace("{model_view}", transforms.model_view_uniform())
        .replace("{model_view_expr}", transforms.model_view_expression())
        .replace(
            "{hand_projection_field}",
            if transforms == SourceTransformSemantics::Hand {
                "mat4 vulkanic_source_hand_projection;"
            } else {
                ""
            },
        )
        .replace("{projection}", transforms.projection_uniform())
}

// `invariant` makes passes that recompute the same position expression in
// different pipelines (entity + glint, hand + hand glint) produce bit-identical
// depth, which vanilla's EQUAL-depth glint relies on.
pub(super) const VERTEX_SEMANTIC_PREAMBLE_TEMPLATE: &str = r#"invariant gl_Position;
struct VulkanicSourceTerrainVertex {
    vec4 position;
    vec4 color;
    vec4 normal_light;
    vec4 atlas_uv_lightmap;
    vec4 entity;
    vec4 mid_tex_coord;
    vec4 tangent;
    vec4 mid_block;
};
layout(set = 0, binding = 0, std430) readonly buffer VulkanicSourceTerrainVertices {
    VulkanicSourceTerrainVertex vulkanic_source_vertices[];
};
layout(set = 0, binding = 1, std140) uniform VulkanicSourceTerrainLegacyTransforms {
    mat4 vulkanic_source_texture_matrix[2];
    {hand_projection_field}
};
struct VulkanicSourceTerrainInstance {
    mat4 model_transform;
    vec4 color_modulation;
};
layout(set = 0, binding = 3, std430) readonly buffer VulkanicSourceTerrainInstances {
    VulkanicSourceTerrainInstance vulkanic_source_instances[];
};
#define vulkanic_source_vertex vulkanic_source_vertices[gl_VertexIndex]
#define vulkanic_source_instance vulkanic_source_instances[gl_InstanceIndex]
#define vulkanic_source_model_transform vulkanic_source_instance.model_transform
#define vulkanic_source_model_view {model_view_expr}
#define vulkanic_source_normal_matrix transpose(inverse(mat3(vulkanic_source_model_view)))
#define vulkanic_source_position vulkanic_source_vertex.position
#define vulkanic_source_vertex_color (vulkanic_source_vertex.color * vulkanic_source_instance.color_modulation)
// Stored normals are model-space face normals. Iris computes them from the
// pose-transformed positions, which flips them under a mirroring pose.
#define vulkanic_source_normal (vulkanic_source_vertex.normal_light.xyz * (determinant(mat3(vulkanic_source_model_transform)) < 0.0 ? -1.0 : 1.0))
#define vulkanic_source_atlas_uv vec4(vulkanic_source_vertex.atlas_uv_lightmap.xy, 0.0, 1.0)
#define vulkanic_source_lightmap_uv vec4(vulkanic_source_vertex.atlas_uv_lightmap.zw, 0.0, 1.0)
#define vulkanic_source_entity vulkanic_source_vertex.entity
#define vulkanic_source_mid_tex_coord vulkanic_source_vertex.mid_tex_coord
#define vulkanic_source_tangent vulkanic_source_vertex.tangent
#define vulkanic_source_mid_block vulkanic_source_vertex.mid_block.xyz
#define vulkanic_source_ftransform() ({projection} * vulkanic_source_model_view * vulkanic_source_position)
"#;

/// Compact Rust-owned stream for selected `gbuffers_textured` programs. It
/// intentionally has no terrain material/entity/tangent lanes: the material
/// contract rejects programs requiring those inputs before this lowering can
/// be prepared. Positions are copied camera-relative semantics and therefore
/// use the selected source `gbufferModelView`/`gbufferProjection` uniforms
/// without a hidden Java pose-stack or Iris vertex-format dependency.
pub(super) const TEXTURED_MATERIAL_VERTEX_SEMANTIC_PREAMBLE: &str = r#"struct VulkanicSourceTexturedMaterialVertex {
    vec4 position;
    vec4 color;
    vec4 normal_light;
    vec4 texture_uv_lightmap;
};
layout(set = 0, binding = 0, std430) readonly buffer VulkanicSourceTexturedMaterialVertices {
    VulkanicSourceTexturedMaterialVertex vulkanic_source_textured_vertices[];
};
layout(set = 0, binding = 1, std140) uniform VulkanicSourceTexturedMaterialLegacyTransforms {
    mat4 vulkanic_source_texture_matrix[2];
};
// The compact material stream stores four semantic vertices per quad. Source
// material draws expand those quads as two triangles in the owned vertex
// preamble, so no Java index buffer, backend base-vertex convention, or
// per-frame index upload is required.
const int vulkanic_source_textured_quad_indices[6] = int[6](0, 1, 2, 2, 3, 0);
#define vulkanic_source_vertex vulkanic_source_textured_vertices[((gl_VertexIndex / 6) * 4) + vulkanic_source_textured_quad_indices[gl_VertexIndex % 6]]
// The stored (indexed) vertex id, matching GL's gl_VertexID for an indexed
// quad draw. Line sources use its parity to pick a segment side.
#define vulkanic_source_stored_vertex_id (((gl_VertexIndex / 6) * 4) + vulkanic_source_textured_quad_indices[gl_VertexIndex % 6])
#define vulkanic_source_model_view gbufferModelView
#define vulkanic_source_normal_matrix transpose(inverse(mat3(vulkanic_source_model_view)))
#define vulkanic_source_position vulkanic_source_vertex.position
#define vulkanic_source_vertex_color vulkanic_source_vertex.color
#define vulkanic_source_normal vulkanic_source_vertex.normal_light.xyz
#define vulkanic_source_atlas_uv vec4(vulkanic_source_vertex.texture_uv_lightmap.xy, 0.0, 1.0)
#define vulkanic_source_lightmap_uv vec4(vulkanic_source_vertex.texture_uv_lightmap.zw, 0.0, 1.0)
#define vulkanic_source_ftransform() (gbufferProjection * vulkanic_source_model_view * vulkanic_source_position)
"#;

/// Rust-owned source interface for the copied DH CPU stream. The storage
/// layout matches `world_primitive_frontend::lod`'s 16-byte packed vertex
/// record. The per-draw column origin comes from the owned LOD frame block;
/// source-declared `dhModelView`/`dhProjection` remain scalar semantic
/// uniforms and therefore must be supplied before a route can be admitted.
pub(super) const DISTANT_HORIZONS_VERTEX_SEMANTIC_PREAMBLE: &str = r#"struct VulkanicDistantHorizonsVertex {
    uvec4 data;
};
layout(set = 0, binding = 0, std430) readonly buffer VulkanicDistantHorizonsVertices {
    VulkanicDistantHorizonsVertex vulkanic_source_dh_vertices[];
};
layout(set = 0, binding = 1, std140) uniform VulkanicDistantHorizonsColumnFrame {
    mat4 vulkanic_source_dh_unused_combined_matrix;
    vec4 vulkanic_source_dh_column_origin_and_world_y;
    vec4 vulkanic_source_dh_model_offset_and_reserved;
    vec4 vulkanic_source_dh_clip_micro_noise_earth;
    uvec4 vulkanic_source_dh_flags_and_noise;
};
#define vulkanic_source_dh_vertex vulkanic_source_dh_vertices[gl_VertexIndex + int(vulkanic_source_dh_model_offset_and_reserved.w)]
int vulkanic_source_dh_i16(uint value) {
    int decoded = int(value & 0xffffu);
    return decoded >= 32768 ? decoded - 65536 : decoded;
}
float vulkanic_source_dh_micro(uint bits) {
    return (bits & 2u) != 0u ? -vulkanic_source_dh_clip_micro_noise_earth.y
        : ((bits & 1u) != 0u ? vulkanic_source_dh_clip_micro_noise_earth.y : 0.0);
}
vec3 vulkanic_source_dh_normal(uint normal) {
    if (normal == 0u) return vec3(0.0, -1.0, 0.0);
    if (normal == 1u) return vec3(0.0, 1.0, 0.0);
    if (normal == 2u) return vec3(0.0, 0.0, -1.0);
    if (normal == 3u) return vec3(0.0, 0.0, 1.0);
    if (normal == 4u) return vec3(-1.0, 0.0, 0.0);
    return vec3(1.0, 0.0, 0.0);
}
vec4 vulkanic_source_dh_position() {
    uint micro = (vulkanic_source_dh_vertex.data.y >> 16u) & 0xffu;
    return vec4(
        vec3(
            vulkanic_source_dh_i16(vulkanic_source_dh_vertex.data.x),
            vulkanic_source_dh_i16(vulkanic_source_dh_vertex.data.x >> 16u),
            vulkanic_source_dh_i16(vulkanic_source_dh_vertex.data.y)
        )
            // Iris's DHTerrainTransformer applies compact micro offsets only
            // on X/Z. Preserve the copied Y bits as semantic data, but do not
            // turn them into terrain height offsets in this source pass.
            + vec3(
                vulkanic_source_dh_micro(micro),
                0.0,
                vulkanic_source_dh_micro(micro >> 4u)
            )
            // The copied DH model offset already carries the column's
            // dimension-local minimum Y. `worldYOffset` remains source-pack
            // scalar context, not a second geometry translation.
            + vulkanic_source_dh_model_offset_and_reserved.xyz,
        1.0
    );
}
vec4 vulkanic_source_dh_vertex_color() {
    return vec4(
        float(vulkanic_source_dh_vertex.data.z & 0xffu),
        float((vulkanic_source_dh_vertex.data.z >> 8u) & 0xffu),
        float((vulkanic_source_dh_vertex.data.z >> 16u) & 0xffu),
        float((vulkanic_source_dh_vertex.data.z >> 24u) & 0xffu)
    ) / 255.0;
}
vec2 vulkanic_source_dh_packed_lightmap_coordinates() {
    return (vec2(
        // Distant Horizons packs its source byte as `(skyLight, blockLight)`.
        // Iris's terrain transformer expands it as `(blockLight, skyLight)`
        // before the shader pack's lightmap conversion. Keep the generic
        // lowered stream aligned with the exact-atlas DH stream.
        float((vulkanic_source_dh_vertex.data.w >> 8u) & 0xffu),
        float(vulkanic_source_dh_vertex.data.w & 0xffu)
    ) + vec2(0.5)) / 16.0;
}
#define vulkanic_source_texture_matrix (mat4[2](mat4(1.0), mat4(1.0)))
#define vulkanic_source_lightmap_uv vec4(vulkanic_source_dh_packed_lightmap_coordinates(), 0.0, 1.0)
#define vulkanic_source_position vulkanic_source_dh_position()
#define vulkanic_source_vertex_color vulkanic_source_dh_vertex_color()
#define vulkanic_source_normal vulkanic_source_dh_normal((vulkanic_source_dh_vertex.data.w >> 24u) & 0xffu)
// Complementary's dh_terrain contract consumes DH's own coarse material
// category (leaves/grass/lava/etc.), not a Minecraft atlas or a guessed block
// state. Keep that category in the copied semantic vertex stream so reduced
// mixed tiles remain representable without inventing per-quad identities.
#define vulkanic_source_dh_material_id int((vulkanic_source_dh_vertex.data.w >> 16u) & 0xffu)
#define vulkanic_source_model_view dhModelView
#define vulkanic_source_normal_matrix transpose(inverse(mat3(vulkanic_source_model_view)))
#define vulkanic_source_ftransform() (dhProjection * vulkanic_source_model_view * vulkanic_source_position)
"#;

pub(super) fn fullscreen_vertex_semantic_preamble(
    raster_primitive: FullscreenSourceRasterPrimitive,
) -> String {
    let geometry = match raster_primitive {
        FullscreenSourceRasterPrimitive::FullscreenTriangle => {
            r#"
vec2 vulkanic_source_fullscreen_position() {
    const vec2 positions[3] = vec2[3](
        vec2(-1.0, -1.0), vec2(3.0, -1.0), vec2(-1.0, 3.0)
    );
    return positions[vulkanic_source_fullscreen_vertex_index()];
}
vec2 vulkanic_source_fullscreen_uv_coordinates() {
    const vec2 coordinates[3] = vec2[3](
        vec2(0.0, 0.0), vec2(2.0, 0.0), vec2(0.0, 2.0)
    );
    vec2 coordinate = coordinates[vulkanic_source_fullscreen_vertex_index()];
    // Legacy source `texture2D` calls use Minecraft's OpenGL texture origin.
    // Vulkan images are sampled with the opposite vertical convention, while
    // integer texelFetch keeps its native image addressing. Preserve the
    // source sampler contract here, at the owned fullscreen vertex boundary.
    #ifdef VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH
        coordinate.y = 1.0 - coordinate.y;
    #endif
    return coordinate;
}
#define vulkanic_source_fullscreen_transform() vec4(vulkanic_source_fullscreen_position(), 0.0, 1.0)
"#.to_string()
        }
        FullscreenSourceRasterPrimitive::VanillaSkyDisc => {
            r#"
// Expanded eight-wedge form of Minecraft's top SkyRenderer disc. The source
// program's legacy ftransform() sees the same local-space sky geometry rather
// than an approximation at synthetic fullscreen depth.
vec4 vulkanic_source_fullscreen_sky_position() {
    const vec3 outer[9] = vec3[9](
        vec3(-512.0, 16.0, 0.0),
        vec3(-362.03867, 16.0, -362.03867),
        vec3(0.0, 16.0, -512.0),
        vec3(362.03867, 16.0, -362.03867),
        vec3(512.0, 16.0, 0.0),
        vec3(362.03867, 16.0, 362.03867),
        vec3(0.0, 16.0, 512.0),
        vec3(-362.03867, 16.0, 362.03867),
        vec3(-512.0, 16.0, 0.0)
    );
    int vertex = vulkanic_source_fullscreen_vertex_index();
    int wedge = vertex / 3;
    int corner = vertex - wedge * 3;
    return corner == 0
        ? vec4(0.0, 16.0, 0.0, 1.0)
        : vec4(outer[wedge + corner - 1], 1.0);
}
vec2 vulkanic_source_fullscreen_uv_coordinates() { return vec2(0.0); }
#define vulkanic_source_fullscreen_transform() (gbufferProjection * gbufferModelView * vulkanic_source_fullscreen_sky_position())
"#.to_string()
        }
        FullscreenSourceRasterPrimitive::VanillaCelestialQuad => {
            r#"
// Matches SkyRenderer's indexed quad topology and semantic transform:
// Y(-90) * X(sunAngle) * Z(sunPathRotation) * translate * scale. The moon
// preserves vanilla's reversed position/UV ordering for its phase sheet.
int vulkanic_source_celestial_corner() {
    const int triangle_corners[6] = int[6](0, 1, 2, 0, 2, 3);
    return triangle_corners[vulkanic_source_fullscreen_vertex_index() % 6];
}
int vulkanic_source_celestial_face() {
    return vulkanic_source_fullscreen_vertex_index() / 6;
}
bool vulkanic_source_celestial_end_sky() {
    return vulkanic_source_celestial_is_moon == 2;
}
vec3 vulkanic_source_rotate_x(vec3 value, float angle) {
    float s = sin(angle);
    float c = cos(angle);
    return vec3(value.x, c * value.y - s * value.z, s * value.y + c * value.z);
}
vec3 vulkanic_source_rotate_y(vec3 value, float angle) {
    float s = sin(angle);
    float c = cos(angle);
    return vec3(c * value.x + s * value.z, value.y, -s * value.x + c * value.z);
}
vec3 vulkanic_source_rotate_z(vec3 value, float angle) {
    float s = sin(angle);
    float c = cos(angle);
    return vec3(c * value.x - s * value.y, s * value.x + c * value.y, value.z);
}
vec4 vulkanic_source_fullscreen_celestial_position() {
    int face = vulkanic_source_celestial_face();
    if (vulkanic_source_celestial_end_sky()) {
        // SkyRenderer.buildEndSky: one +/-100 face per rotation.
        const vec3 box_corners[4] = vec3[4](
            vec3(-100.0, -100.0, -100.0), vec3(-100.0, -100.0, 100.0),
            vec3(100.0, -100.0, 100.0), vec3(100.0, -100.0, -100.0)
        );
        vec3 corner = box_corners[vulkanic_source_celestial_corner()];
        if (face == 1) corner = vulkanic_source_rotate_x(corner, 1.57079632679);
        else if (face == 2) corner = vulkanic_source_rotate_x(corner, -1.57079632679);
        else if (face == 3) corner = vulkanic_source_rotate_x(corner, 3.14159265359);
        else if (face == 4) corner = vulkanic_source_rotate_z(corner, 1.57079632679);
        else if (face == 5) corner = vulkanic_source_rotate_z(corner, -1.57079632679);
        return vec4(corner, 1.0);
    }
    if (face != 0) {
        // Sun and moon draw one quad; collapse the remaining faces.
        return vec4(0.0, 100.0, 0.0, 1.0);
    }
    const vec2 sun_corners[4] = vec2[4](
        vec2(-1.0, -1.0), vec2(1.0, -1.0), vec2(1.0, 1.0), vec2(-1.0, 1.0)
    );
    const vec2 moon_corners[4] = vec2[4](
        vec2(-1.0, 1.0), vec2(1.0, 1.0), vec2(1.0, -1.0), vec2(-1.0, -1.0)
    );
    bool moon = vulkanic_source_celestial_is_moon == 1;
    vec2 corner = moon ? moon_corners[vulkanic_source_celestial_corner()]
                       : sun_corners[vulkanic_source_celestial_corner()];
    float size = moon ? 20.0 : 30.0;
    float height = moon ? -100.0 : 100.0;
    vec3 position = vec3(corner.x * size, height, corner.y * size);
    position = vulkanic_source_rotate_z(position, radians(vulkanic_source_celestial_sun_path_rotation));
    position = vulkanic_source_rotate_x(position, sunAngle * 6.28318530718);
    position = vulkanic_source_rotate_y(position, -1.57079632679);
    return vec4(position, 1.0);
}
vec2 vulkanic_source_fullscreen_uv_coordinates() {
    int corner = vulkanic_source_celestial_corner();
    bool moon = vulkanic_source_celestial_is_moon == 1;
    vec2 coordinate;
    if (vulkanic_source_celestial_end_sky()) {
        const vec2 end_uv[4] = vec2[4](
            vec2(0.0, 0.0), vec2(0.0, 16.0), vec2(16.0, 16.0), vec2(16.0, 0.0)
        );
        coordinate = end_uv[corner];
    } else if (moon) {
        int phase = clamp(moonPhase, 0, 7);
        float u0 = float(phase % 4) * 0.25;
        float v0 = float(phase / 4) * 0.5;
        const vec2 moon_uv[4] = vec2[4](
            vec2(1.0, 1.0), vec2(0.0, 1.0), vec2(0.0, 0.0), vec2(1.0, 0.0)
        );
        coordinate = vec2(u0, v0) + moon_uv[corner] * vec2(0.25, 0.5);
    } else {
        const vec2 sun_uv[4] = vec2[4](
            vec2(0.0, 0.0), vec2(1.0, 0.0), vec2(1.0, 1.0), vec2(0.0, 1.0)
        );
        coordinate = sun_uv[corner];
    }
#ifdef VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH
    coordinate.y = 1.0 - coordinate.y;
#endif
    return coordinate;
}
#define vulkanic_source_fullscreen_transform() (gbufferProjection * gbufferModelView * vulkanic_source_fullscreen_celestial_position())
"#.to_string()
        }
    };
    let vertex_color = match raster_primitive {
        // SkyRenderer builds its top disc with the extracted vanilla sky
        // color. The selected source's sky vertex uses this for its legacy
        // `gl_Color` semantic (including star discrimination), so it cannot
        // be replaced with the generic procedural white value.
        FullscreenSourceRasterPrimitive::VanillaSkyDisc => {
            "#define vulkanic_source_fullscreen_vertex_color vec4(skyColor, 1.0)"
        }
        // The End sky box uses SkyRenderer's constant 0xFF282828 vertex color.
        FullscreenSourceRasterPrimitive::VanillaCelestialQuad => {
            "#define vulkanic_source_fullscreen_vertex_color (vulkanic_source_celestial_is_moon == 2 ? vec4(vec3(40.0 / 255.0), 1.0) : vec4(1.0, 1.0, 1.0, vulkanic_source_celestial_alpha))"
        }
        FullscreenSourceRasterPrimitive::FullscreenTriangle => {
            "const vec4 vulkanic_source_fullscreen_vertex_color = vec4(1.0);"
        }
    };
    r#"// Source stages use Rust-owned procedural geometry. This avoids
// inheriting a Java/Iris vertex stream and stays compatible with the OpenGL
// backend's intentionally storage-buffer-only mesh path.
layout(set = 0, binding = 0, std140) uniform VulkanicSourceFullscreenFrame {
    mat4 vulkanic_source_fullscreen_texture_matrix[2];
};
int vulkanic_source_fullscreen_vertex_index() {
#ifdef VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH
    return gl_VertexIndex;
#else
    return gl_VertexID;
#endif
}
VULKANIC_SOURCE_FULLSCREEN_GEOMETRY
#define vulkanic_source_fullscreen_uv vec4(vulkanic_source_fullscreen_uv_coordinates(), 0.0, 1.0)
#define vulkanic_source_fullscreen_secondary_uv vec4(vulkanic_source_fullscreen_uv_coordinates(), 0.0, 1.0)
VULKANIC_SOURCE_FULLSCREEN_VERTEX_COLOR
"#
    .replace("VULKANIC_SOURCE_FULLSCREEN_GEOMETRY", &geometry)
    .replace("VULKANIC_SOURCE_FULLSCREEN_VERTEX_COLOR", vertex_color)
}

pub(super) const LEGACY_FOG_SEMANTIC_PREAMBLE: &str = r#"struct VulkanicSourceLegacyFogParameters {
    vec4 color;
    float density;
    float start;
    float end;
    float scale;
};
VulkanicSourceLegacyFogParameters vulkanic_source_fog() {
    return VulkanicSourceLegacyFogParameters(
        vulkanic_source_fog_parameter_color,
        0.0,
        vulkanic_source_fog_environmental_start,
        vulkanic_source_fog_environmental_end,
        1.0 / (vulkanic_source_fog_environmental_end - vulkanic_source_fog_environmental_start)
    );
}
"#;

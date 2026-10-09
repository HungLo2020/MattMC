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
    WorldGlyph,
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
            Self::TexturedMaterial | Self::WorldGlyph | Self::Weather | Self::Cloud | Self::Fullscreen => {
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
            Self::WorldGlyph => "gbufferModelView",
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
            Self::WorldGlyph => "gbufferProjection",
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
    lowered = replace_identifier(&lowered, "gl_ModelViewProjectionMatrix",
        &format!("({} * vulkanic_source_model_view)", transforms.projection_uniform()));
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
    if transforms == SourceTransformSemantics::WorldGlyph {
        return format!("{TEXTURED_MATERIAL_VERTEX_SEMANTIC_PREAMBLE}\n{WORLD_GLYPH_VERTEX_SEMANTICS}");
    }
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
// Four packed words per vertex (TERRAIN_SOURCE_VERTEX_BYTES); decoded to the
// exact semantic lanes the earlier float record carried.
layout(set = 0, binding = 0, std430) readonly buffer VulkanicSourceTerrainVertices {
    uvec4 vulkanic_source_vertex_words[];
};
vec4 vulkanic_source_signed_bytes(uint word) {
    return vec4(ivec4(word << 24u, word << 16u, word << 8u, word) >> 24);
}
VulkanicSourceTerrainVertex vulkanic_source_decode_vertex(int index) {
    uvec4 a = vulkanic_source_vertex_words[index * 4];
    uvec4 b = vulkanic_source_vertex_words[index * 4 + 1];
    uvec4 c = vulkanic_source_vertex_words[index * 4 + 2];
    uvec4 d = vulkanic_source_vertex_words[index * 4 + 3];
    VulkanicSourceTerrainVertex vertex;
    vertex.position = vec4(uintBitsToFloat(a.xyz), 1.0);
    vertex.color = vec4((uvec4(a.w >> 16u, a.w >> 8u, a.w, a.w >> 24u) & 0xffu)) / 255.0;
    vertex.normal_light = vec4(clamp(vulkanic_source_signed_bytes(b.z).xyz / 127.0, -1.0, 1.0), 0.0);
    vertex.atlas_uv_lightmap = vec4(uintBitsToFloat(b.xy), float(b.w & 0xffu), float((b.w >> 16u) & 0xffu));
    vertex.entity = vec4(float(int(c.x)), float((b.w >> 8u) & 0xffu), 0.0, 1.0);
    vertex.mid_tex_coord = vec4(uintBitsToFloat(c.zw), 0.0, 1.0);
    vertex.tangent = uintBitsToFloat(d);
    vertex.mid_block = vulkanic_source_signed_bytes(c.y);
    return vertex;
}
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
#define vulkanic_source_vertex vulkanic_source_decode_vertex(gl_VertexIndex)
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
/// intentionally has no terrain material/entity/tangent lanes. Its entity
/// input is the explicit generic value of a disabled compact GL attribute,
/// observed on Frozen's particle draws; remaining terrain lanes are rejected.
/// Positions are copied camera-relative semantics and therefore
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
// Frozen's compact particle stream has no mc_Entity array. Three captured
// MakeUp oak-leaves draws consume this generic value, including w = 1.
#define vulkanic_source_entity vec4(0.0, 0.0, 0.0, 1.0)
#define vulkanic_source_ftransform() (gbufferProjection * vulkanic_source_model_view * vulkanic_source_position)
"#;

const WORLD_GLYPH_VERTEX_SEMANTICS: &str = include_str!("glyph_vertex.glsl");

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

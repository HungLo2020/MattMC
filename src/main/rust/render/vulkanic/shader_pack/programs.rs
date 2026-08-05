#[derive(Clone, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub struct ProgramIdentity(String);

impl ProgramIdentity {
    pub fn new(value: impl Into<String>) -> Self {
        Self(value.into())
    }

    pub fn as_str(&self) -> &str {
        &self.0
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ShaderStageKind {
    Vertex,
    Fragment,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ShaderStageSource {
    pub stage: ShaderStageKind,
    pub label: String,
    pub source: String,
    pub entry_point: String,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TerrainMaterialProgram {
    pub identity: ProgramIdentity,
    pub vertex: ShaderStageSource,
    pub fragment: ShaderStageSource,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct CompositeProgram {
    pub identity: ProgramIdentity,
    pub vertex: ShaderStageSource,
    pub fragment: ShaderStageSource,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum TerrainMaterialProgramKind {
    Opaque,
    Cutout,
    Translucent,
}

impl TerrainMaterialProgramKind {
    fn identity(self) -> ProgramIdentity {
        match self {
            Self::Opaque => ProgramIdentity::new("vulkanic:builtin/terrain_opaque_v1"),
            Self::Cutout => ProgramIdentity::new("vulkanic:builtin/terrain_cutout_v1"),
            Self::Translucent => ProgramIdentity::new("vulkanic:builtin/terrain_translucent_v1"),
        }
    }

    fn label_suffix(self) -> &'static str {
        match self {
            Self::Opaque => "opaque",
            Self::Cutout => "cutout",
            Self::Translucent => "translucent",
        }
    }
}

pub fn minimal_terrain_solid_program() -> TerrainMaterialProgram {
    minimal_terrain_material_program(TerrainMaterialProgramKind::Opaque)
}

pub fn minimal_terrain_cutout_program() -> TerrainMaterialProgram {
    minimal_terrain_material_program(TerrainMaterialProgramKind::Cutout)
}

pub fn minimal_terrain_translucent_program() -> TerrainMaterialProgram {
    minimal_direct_terrain_material_program(TerrainMaterialProgramKind::Translucent)
}

pub fn minimal_direct_terrain_solid_program() -> TerrainMaterialProgram {
    minimal_direct_terrain_material_program(TerrainMaterialProgramKind::Opaque)
}

pub fn minimal_direct_terrain_cutout_program() -> TerrainMaterialProgram {
    minimal_direct_terrain_material_program(TerrainMaterialProgramKind::Cutout)
}

pub fn minimal_terrain_material_program(
    kind: TerrainMaterialProgramKind,
) -> TerrainMaterialProgram {
    TerrainMaterialProgram {
        identity: kind.identity(),
        vertex: ShaderStageSource {
            stage: ShaderStageKind::Vertex,
            label: format!("minimal-terrain-{}.vertex", kind.label_suffix()),
            source: MINIMAL_TERRAIN_MATERIAL_VERTEX.to_string(),
            entry_point: "main".to_string(),
        },
        fragment: ShaderStageSource {
            stage: ShaderStageKind::Fragment,
            label: format!("minimal-terrain-{}.fragment", kind.label_suffix()),
            source: MINIMAL_TERRAIN_MATERIAL_FRAGMENT.to_string(),
            entry_point: "main".to_string(),
        },
    }
}

/// Builds the first executable Rust-owned lowering for the normal terrain
/// contract. It is deliberately admitted only for a fully supported source
/// profile; callers must not use it as a fallback for a richer selected pack.
pub fn complementary_terrain_subset_program(
    contract: &TerrainPassContract,
    kind: TerrainMaterialProgramKind,
) -> GalResult<TerrainMaterialProgram> {
    complementary_terrain_subset_program_with_resources(contract, kind, None, 0)
}

/// Selects the source-derived lowering only after every semantic resource
/// required by the selected profile has a complete, matching generation.
pub fn complementary_terrain_subset_program_with_resources(
    contract: &TerrainPassContract,
    kind: TerrainMaterialProgramKind,
    voxel_light_volume: Option<&VoxelLightVolumeCache>,
    frame_counter: u64,
) -> GalResult<TerrainMaterialProgram> {
    contract.require_selected_subset_with_resources(voxel_light_volume, frame_counter)?;
    if !matches!(
        kind,
        TerrainMaterialProgramKind::Opaque | TerrainMaterialProgramKind::Cutout
    ) {
        return Err(
            crate::render::vulkanic::error::GalError::unsupported_feature(
                "Complementary terrain subset only supports opaque and cutout materials",
            ),
        );
    }
    Ok(TerrainMaterialProgram {
        identity: ProgramIdentity::new(format!(
            "vulkanic:shader-pack/{}/terrain_{}_subset_v1",
            contract.pack_name.to_ascii_lowercase(),
            kind.label_suffix()
        )),
        vertex: ShaderStageSource {
            stage: ShaderStageKind::Vertex,
            label: format!("terrain-contract-{}.vertex", kind.label_suffix()),
            source: MINIMAL_TERRAIN_MATERIAL_VERTEX.to_string(),
            entry_point: "main".to_string(),
        },
        fragment: ShaderStageSource {
            stage: ShaderStageKind::Fragment,
            label: format!("terrain-contract-{}.fragment", kind.label_suffix()),
            source: COMPLEMENTARY_TERRAIN_SUBSET_FRAGMENT.to_string(),
            entry_point: "main".to_string(),
        },
    })
}

pub fn minimal_direct_terrain_material_program(
    kind: TerrainMaterialProgramKind,
) -> TerrainMaterialProgram {
    TerrainMaterialProgram {
        identity: ProgramIdentity::new(match kind {
            TerrainMaterialProgramKind::Opaque => "vulkanic:builtin/direct_terrain_opaque_v1",
            TerrainMaterialProgramKind::Cutout => "vulkanic:builtin/direct_terrain_cutout_v1",
            TerrainMaterialProgramKind::Translucent => "vulkanic:builtin/terrain_translucent_v1",
        }),
        vertex: ShaderStageSource {
            stage: ShaderStageKind::Vertex,
            label: format!("minimal-direct-terrain-{}.vertex", kind.label_suffix()),
            source: MINIMAL_TERRAIN_MATERIAL_VERTEX.to_string(),
            entry_point: "main".to_string(),
        },
        fragment: ShaderStageSource {
            stage: ShaderStageKind::Fragment,
            label: format!("minimal-direct-terrain-{}.fragment", kind.label_suffix()),
            source: MINIMAL_TERRAIN_MATERIAL_FRAGMENT_DIRECT.to_string(),
            entry_point: "main".to_string(),
        },
    }
}

pub fn minimal_g_buffer_composite_program() -> CompositeProgram {
    minimal_deferred_lighting_program()
}

pub fn minimal_deferred_lighting_program() -> CompositeProgram {
    CompositeProgram {
        identity: ProgramIdentity::new("vulkanic:builtin/deferred_lighting_v1"),
        vertex: ShaderStageSource {
            stage: ShaderStageKind::Vertex,
            label: "minimal-deferred-lighting.vertex".to_string(),
            source: MINIMAL_FULLSCREEN_VERTEX.to_string(),
            entry_point: "main".to_string(),
        },
        fragment: ShaderStageSource {
            stage: ShaderStageKind::Fragment,
            label: "minimal-deferred-lighting.fragment".to_string(),
            source: MINIMAL_DEFERRED_LIGHTING_FRAGMENT.to_string(),
            entry_point: "main".to_string(),
        },
    }
}

pub fn minimal_composite_color_grade_program() -> CompositeProgram {
    CompositeProgram {
        identity: ProgramIdentity::new("vulkanic:builtin/composite_color_grade_v1"),
        vertex: ShaderStageSource {
            stage: ShaderStageKind::Vertex,
            label: "minimal-composite-color-grade.vertex".to_string(),
            source: MINIMAL_FULLSCREEN_VERTEX.to_string(),
            entry_point: "main".to_string(),
        },
        fragment: ShaderStageSource {
            stage: ShaderStageKind::Fragment,
            label: "minimal-composite-color-grade.fragment".to_string(),
            source: MINIMAL_COMPOSITE_COLOR_GRADE_FRAGMENT.to_string(),
            entry_point: "main".to_string(),
        },
    }
}

pub fn minimal_composite_depth_fog_program() -> CompositeProgram {
    CompositeProgram {
        identity: ProgramIdentity::new("vulkanic:builtin/composite_depth_fog_v1"),
        vertex: ShaderStageSource {
            stage: ShaderStageKind::Vertex,
            label: "minimal-composite-depth-fog.vertex".to_string(),
            source: MINIMAL_FULLSCREEN_VERTEX.to_string(),
            entry_point: "main".to_string(),
        },
        fragment: ShaderStageSource {
            stage: ShaderStageKind::Fragment,
            label: "minimal-composite-depth-fog.fragment".to_string(),
            source: MINIMAL_COMPOSITE_DEPTH_FOG_FRAGMENT.to_string(),
            entry_point: "main".to_string(),
        },
    }
}

pub fn minimal_final_copy_program() -> CompositeProgram {
    CompositeProgram {
        identity: ProgramIdentity::new("vulkanic:builtin/final_copy_v1"),
        vertex: ShaderStageSource {
            stage: ShaderStageKind::Vertex,
            label: "minimal-final-copy.vertex".to_string(),
            source: MINIMAL_FULLSCREEN_VERTEX.to_string(),
            entry_point: "main".to_string(),
        },
        fragment: ShaderStageSource {
            stage: ShaderStageKind::Fragment,
            label: "minimal-final-copy.fragment".to_string(),
            source: MINIMAL_FINAL_COPY_FRAGMENT.to_string(),
            entry_point: "main".to_string(),
        },
    }
}

pub fn minimal_shadow_depth_program() -> TerrainMaterialProgram {
    TerrainMaterialProgram {
        identity: ProgramIdentity::new("vulkanic:builtin/shadow_depth_v1"),
        vertex: ShaderStageSource {
            stage: ShaderStageKind::Vertex,
            label: "minimal-shadow-depth.vertex".to_string(),
            source: MINIMAL_SHADOW_DEPTH_VERTEX.to_string(),
            entry_point: "main".to_string(),
        },
        fragment: ShaderStageSource {
            stage: ShaderStageKind::Fragment,
            label: "minimal-shadow-depth.fragment".to_string(),
            source: MINIMAL_SHADOW_DEPTH_FRAGMENT.to_string(),
            entry_point: "main".to_string(),
        },
    }
}

pub fn shader_stage_code_for_backend(api: BackendApi, source: &str) -> Vec<u8> {
    if api != BackendApi::Vulkan {
        return source.as_bytes().to_vec();
    }
    source
        .replacen(
            "#version 450\n",
            "#version 450\n#define VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH 1\n#define VULKANIC_GAL_FLIP_FULLSCREEN_UV_Y 1\n",
            1,
        )
        .into_bytes()
}

pub const MINIMAL_TERRAIN_MATERIAL_VERTEX: &str = r#"#version 450
struct MeshVertex {
    vec4 position_uv;
    vec4 color_uv;
    vec4 normal_light;
    vec4 extra_data;
    vec4 shader_data;
};
layout(set = 0, binding = 0, std430) readonly buffer WorldMeshVertices {
    MeshVertex vertices[];
};
struct MeshInstance {
    mat4 model;
    vec4 color;
    vec4 material;
    vec4 animation_region;
    vec4 animation_next_region;
};
layout(set = 0, binding = 1, std430) readonly buffer WorldMeshInstances {
    mat4 view;
    mat4 projection;
    mat4 light_view_projection;
    vec4 shadow_params;
    MeshInstance instances[];
};
layout(location = 0) out vec2 v_uv;
layout(location = 1) out vec4 v_color;
layout(location = 2) flat out vec4 v_material;
layout(location = 3) out vec3 v_normal;
layout(location = 4) out vec2 v_light;
layout(location = 5) out vec3 v_world_position;
layout(location = 6) flat out vec4 v_animation_region;
layout(location = 7) flat out vec4 v_animation_next_region;
void main() {
    MeshVertex vertex = vertices[gl_VertexIndex];
    MeshInstance instance = instances[gl_InstanceIndex];
    vec4 world = instance.model * vec4(vertex.position_uv.xyz, 1.0);
    vec4 clip = projection * view * world;
#ifdef VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH
    clip.z = clip.z * 0.5 + clip.w * 0.5;
#endif
    gl_Position = clip;
    v_uv = vec2(vertex.position_uv.w, vertex.color_uv.w);
    v_color = vec4(vertex.color_uv.rgb * vertex.normal_light.x, vertex.normal_light.w) * instance.color;
    v_material = instance.material;
    v_animation_region = instance.animation_region;
    v_animation_next_region = instance.animation_next_region;
    v_normal = normalize(vec3(vertex.normal_light.yz, vertex.extra_data.z));
    v_light = clamp(vertex.extra_data.xy, vec2(0.0), vec2(1.0));
    float shadow_range = max(shadow_params.w, 1.0);
    v_world_position = world.xyz / shadow_range * 0.5 + 0.5;
}
"#;

pub const MINIMAL_TERRAIN_MATERIAL_FRAGMENT: &str = r#"#version 450
layout(set = 0, binding = 2) uniform texture2D Tex0;
layout(set = 0, binding = 3) uniform sampler Samp0;
layout(location = 0) in vec2 v_uv;
layout(location = 1) in vec4 v_color;
layout(location = 2) flat in vec4 v_material;
layout(location = 3) in vec3 v_normal;
layout(location = 4) in vec2 v_light;
layout(location = 5) in vec3 v_world_position;
layout(location = 6) flat in vec4 v_animation_region;
layout(location = 7) flat in vec4 v_animation_next_region;
// These locations are implementation details. The shader-pack contract names
// the values terrain_lit_color, terrain_view_space_normal, and
// terrain_material_auxiliary.
layout(location = 0) out vec4 out_terrain_lit_color;
layout(location = 1) out vec4 out_terrain_view_space_normal;
layout(location = 2) out vec4 out_terrain_material_auxiliary;
layout(location = 3) out vec4 out_world_position;
void main() {
    vec2 sample_uv = v_animation_region.xy + v_uv * v_animation_region.zw;
    vec4 color = texture(sampler2D(Tex0, Samp0), sample_uv);
    if (v_material.y > 0.5) {
        vec2 next_uv = v_animation_next_region.xy + v_uv * v_animation_next_region.zw;
        vec4 next_color = texture(sampler2D(Tex0, Samp0), next_uv);
        color = mix(color, next_color, clamp(v_material.z, 0.0, 1.0));
    }
    color *= v_color;
    if (v_material.x > 0.0 && color.a < v_material.x) {
        discard;
    }
    vec3 n = normalize(v_normal) * 0.5 + 0.5;
    out_terrain_lit_color = color;
    out_terrain_view_space_normal = vec4(n, color.a);
    out_terrain_material_auxiliary = vec4(v_material.x, v_light.x, v_light.y, color.a);
    out_world_position = vec4(v_world_position, color.a);
}
"#;

/// Rust-owned lowering of the stable normal-terrain expressions shared by the
/// audited Complementary source: atlas sample, alpha discard, tint/AO, light
/// map contribution, directional shade, and named G-buffer outputs. Richer
/// branches are admitted only after their semantic inputs have implementations.
pub const COMPLEMENTARY_TERRAIN_SUBSET_FRAGMENT: &str = r#"#version 450
layout(set = 0, binding = 2) uniform texture2D TerrainAtlasColor;
layout(set = 0, binding = 3) uniform sampler TerrainAtlasSampler;
layout(location = 0) in vec2 v_uv;
layout(location = 1) in vec4 v_color;
layout(location = 2) flat in vec4 v_material;
layout(location = 3) in vec3 v_normal;
layout(location = 4) in vec2 v_light;
layout(location = 5) in vec3 v_world_position;
layout(location = 6) flat in vec4 v_animation_region;
layout(location = 7) flat in vec4 v_animation_next_region;
layout(location = 0) out vec4 out_terrain_lit_color;
layout(location = 1) out vec4 out_terrain_view_space_normal;
layout(location = 2) out vec4 out_terrain_material_auxiliary;
layout(location = 3) out vec4 out_world_position;

float complementary_block_light(float value) {
    float steep = pow(value * value, 4.0) * 3.8;
    float calm = value * 1.8;
    return pow(steep + calm, 2.25);
}

void main() {
    vec2 sample_uv = v_animation_region.xy + v_uv * v_animation_region.zw;
    vec4 sampled_atlas_color = texture(sampler2D(TerrainAtlasColor, TerrainAtlasSampler), sample_uv);
    if (v_material.y > 0.5) {
        vec2 next_uv = v_animation_next_region.xy + v_uv * v_animation_next_region.zw;
        sampled_atlas_color = mix(
            sampled_atlas_color,
            texture(sampler2D(TerrainAtlasColor, TerrainAtlasSampler), next_uv),
            clamp(v_material.z, 0.0, 1.0)
        );
    }
    // gbuffers_terrain: if (color.a <= 0.00001) discard;
    if (sampled_atlas_color.a <= 0.00001) discard;

    // gbuffers_terrain: color.rgb *= glColor.rgb;
    vec3 tint_result = sampled_atlas_color.rgb * v_color.rgb;
    float raw_ao = clamp(v_color.a, 0.0, 1.0);
    vec3 normal = normalize(v_normal);
    float directional_shade = 0.75 + 0.25 * max(normal.y, 0.0);
    float block_light = complementary_block_light(clamp(v_light.x, 0.0, 1.0));
    float sky_light = clamp(v_light.y, 0.0, 1.0);
    vec3 scene_lighting = vec3(0.18 + 0.82 * sky_light);
    vec3 final_diffuse = sqrt(max(
        vec3(raw_ao * directional_shade * directional_shade) *
        (vec3(block_light) + scene_lighting * scene_lighting),
        vec3(0.0)
    ));
    vec4 terrain_lit_color = vec4(tint_result * final_diffuse, sampled_atlas_color.a);
    // gbuffers_terrain writes this only when its reflection profile is on.
    // The output is still pass-local and does not require implementing the
    // later deferred reflection consumer.
    float sky_light_factor = pow(max(sky_light - 0.7, 0.0) * 3.33333, 2.0);

    out_terrain_lit_color = terrain_lit_color;
    out_terrain_material_auxiliary = vec4(0.0, 0.0, sky_light_factor, 1.0);
    out_terrain_view_space_normal = vec4(normal, 1.0);
    out_world_position = vec4(v_world_position, terrain_lit_color.a);
}
"#;

pub const MINIMAL_TERRAIN_MATERIAL_FRAGMENT_DIRECT: &str = r#"#version 450
layout(set = 0, binding = 2) uniform texture2D Tex0;
layout(set = 0, binding = 3) uniform sampler Samp0;
layout(location = 0) in vec2 v_uv;
layout(location = 1) in vec4 v_color;
layout(location = 2) flat in vec4 v_material;
layout(location = 6) flat in vec4 v_animation_region;
layout(location = 7) flat in vec4 v_animation_next_region;
layout(location = 0) out vec4 out_color;
void main() {
    vec2 sample_uv = v_animation_region.xy + v_uv * v_animation_region.zw;
    vec4 color = texture(sampler2D(Tex0, Samp0), sample_uv);
    if (v_material.y > 0.5) {
        vec2 next_uv = v_animation_next_region.xy + v_uv * v_animation_next_region.zw;
        vec4 next_color = texture(sampler2D(Tex0, Samp0), next_uv);
        color = mix(color, next_color, clamp(v_material.z, 0.0, 1.0));
    }
    color *= v_color;
    if (v_material.x > 0.0 && color.a < v_material.x) {
        discard;
    }
    out_color = color;
}
"#;

pub const MINIMAL_G_BUFFER_COMPOSITE_VERTEX: &str = MINIMAL_FULLSCREEN_VERTEX;

pub const MINIMAL_FULLSCREEN_VERTEX: &str = r#"#version 450
layout(location = 0) out vec2 v_uv;
void main() {
    vec2 positions[3] = vec2[](
        vec2(-1.0, -1.0),
        vec2( 3.0, -1.0),
        vec2(-1.0,  3.0)
    );
    vec2 uvs[3] = vec2[](
        vec2(0.0, 0.0),
        vec2(2.0, 0.0),
        vec2(0.0, 2.0)
    );
    gl_Position = vec4(positions[gl_VertexIndex], 0.0, 1.0);
    v_uv = uvs[gl_VertexIndex];
#ifdef VULKANIC_GAL_FLIP_FULLSCREEN_UV_Y
    v_uv.y = 1.0 - v_uv.y;
#endif
}
"#;

pub const MINIMAL_G_BUFFER_COMPOSITE_FRAGMENT: &str = MINIMAL_DEFERRED_LIGHTING_FRAGMENT;

pub const MINIMAL_DEFERRED_LIGHTING_FRAGMENT: &str = r#"#version 450
layout(set = 0, binding = 0) uniform texture2D AlbedoTex;
layout(set = 0, binding = 1) uniform texture2D NormalTex;
layout(set = 0, binding = 2) uniform texture2D MaterialLightTex;
layout(set = 0, binding = 3) uniform texture2D WorldPositionTex;
layout(set = 0, binding = 4) uniform texture2D ShadowDepthTex;
layout(set = 0, binding = 5) uniform sampler Samp0;
layout(set = 0, binding = 6, std430) readonly buffer ShaderCompositeUniforms {
    mat4 light_view_projection;
    vec4 shadow_params;
    vec4 color_grade_params;
    vec4 fog_params;
};
layout(location = 0) in vec2 v_uv;
layout(location = 0) out vec4 out_color;
void main() {
    vec4 albedo = texture(sampler2D(AlbedoTex, Samp0), v_uv);
    vec3 normal = normalize(texture(sampler2D(NormalTex, Samp0), v_uv).xyz * 2.0 - 1.0);
    vec4 material_light = texture(sampler2D(MaterialLightTex, Samp0), v_uv);
    if (material_light.a < 0.5) {
        out_color = vec4(albedo.rgb, 0.0);
        return;
    }
    float face = clamp(dot(normal, normalize(vec3(0.35, 0.65, 0.68))), 0.18, 1.0);
    float light = clamp(max(material_light.y, material_light.z) * 0.75 + 0.25, 0.2, 1.0);
    vec4 packed_world = texture(sampler2D(WorldPositionTex, Samp0), v_uv);
    float shadow_range = max(shadow_params.w, 1.0);
    vec3 world_position = (packed_world.xyz * 2.0 - 1.0) * shadow_range;
    vec4 light_clip = light_view_projection * vec4(world_position, 1.0);
    vec3 light_ndc = light_clip.xyz / max(abs(light_clip.w), 0.0001);
    vec2 shadow_uv = light_ndc.xy * 0.5 + 0.5;
    float shadow_factor = 1.0;
    if (shadow_params.x > 0.5
            && shadow_uv.x >= 0.0 && shadow_uv.x <= 1.0
            && shadow_uv.y >= 0.0 && shadow_uv.y <= 1.0) {
        float shadow_depth = texture(sampler2D(ShadowDepthTex, Samp0), shadow_uv).r;
        float compare_depth = light_ndc.z * 0.5 + 0.5;
        float bias = shadow_params.y;
        shadow_factor = compare_depth - bias > shadow_depth ? shadow_params.z : 1.0;
    }
    out_color = vec4(albedo.rgb * face * light * shadow_factor, albedo.a);
}
"#;

pub const MINIMAL_COMPOSITE_COLOR_GRADE_FRAGMENT: &str = r#"#version 450
layout(set = 0, binding = 0) uniform texture2D Tex0;
layout(set = 0, binding = 5) uniform sampler Samp0;
layout(set = 0, binding = 6, std430) readonly buffer ShaderCompositeUniforms {
    mat4 light_view_projection;
    vec4 shadow_params;
    vec4 color_grade_params;
    vec4 fog_params;
};
layout(location = 0) in vec2 v_uv;
layout(location = 0) out vec4 out_color;
void main() {
    vec4 color = texture(sampler2D(Tex0, Samp0), v_uv);
    if (color.a < 0.5) {
        out_color = color;
        return;
    }
    float exposure = color_grade_params.x;
    vec3 lift = vec3(color_grade_params.y);
    vec3 graded = pow(max(color.rgb * exposure + lift, vec3(0.0)), vec3(color_grade_params.z));
    out_color = vec4(graded, color.a);
}
"#;

pub const MINIMAL_COMPOSITE_DEPTH_FOG_FRAGMENT: &str = r#"#version 450
layout(set = 0, binding = 0) uniform texture2D Tex0;
layout(set = 0, binding = 3) uniform texture2D WorldPositionTex;
layout(set = 0, binding = 5) uniform sampler Samp0;
layout(set = 0, binding = 6, std430) readonly buffer ShaderCompositeUniforms {
    mat4 light_view_projection;
    vec4 shadow_params;
    vec4 color_grade_params;
    vec4 fog_params;
};
layout(location = 0) in vec2 v_uv;
layout(location = 0) out vec4 out_color;
void main() {
    vec4 color = texture(sampler2D(Tex0, Samp0), v_uv);
    vec3 world_position = texture(sampler2D(WorldPositionTex, Samp0), v_uv).xyz * 2.0 - 1.0;
    float height_fog = clamp((world_position.y + 0.45) * fog_params.w, 0.0, 1.0);
    vec3 fog_color = fog_params.xyz;
    out_color = vec4(mix(color.rgb, fog_color, height_fog * color.a), color.a);
}
"#;

pub const MINIMAL_FINAL_COPY_FRAGMENT: &str = r#"#version 450
layout(set = 0, binding = 0) uniform texture2D Tex0;
layout(set = 0, binding = 5) uniform sampler Samp0;
layout(set = 0, binding = 6, std430) readonly buffer ShaderCompositeUniforms {
    mat4 light_view_projection;
    vec4 shadow_params;
    vec4 color_grade_params;
    vec4 fog_params;
};
layout(location = 0) in vec2 v_uv;
layout(location = 0) out vec4 out_color;
void main() {
    vec4 color = texture(sampler2D(Tex0, Samp0), v_uv);
    out_color = vec4(color.rgb + vec3(shadow_params.x * 0.0), color.a);
}
"#;

pub const MINIMAL_SHADOW_DEPTH_VERTEX: &str = r#"#version 450
struct MeshVertex {
    vec4 position_uv;
    vec4 color_uv;
    vec4 normal_light;
    vec4 extra_data;
    vec4 shader_data;
};
layout(set = 0, binding = 0, std430) readonly buffer WorldMeshVertices {
    MeshVertex vertices[];
};
struct MeshInstance {
    mat4 model;
    vec4 color;
    vec4 material;
    vec4 animation_region;
    vec4 animation_next_region;
};
layout(set = 0, binding = 1, std430) readonly buffer WorldMeshInstances {
    mat4 view;
    mat4 projection;
    mat4 light_view_projection;
    vec4 shadow_params;
    MeshInstance instances[];
};
layout(location = 0) out vec2 v_uv;
layout(location = 1) out vec4 v_color;
layout(location = 2) flat out vec4 v_material;
layout(location = 6) flat out vec4 v_animation_region;
layout(location = 7) flat out vec4 v_animation_next_region;
void main() {
    MeshVertex vertex = vertices[gl_VertexIndex];
    MeshInstance instance = instances[gl_InstanceIndex];
    vec4 clip = light_view_projection * instance.model * vec4(vertex.position_uv.xyz, 1.0);
#ifdef VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH
    clip.z = clip.z * 0.5 + clip.w * 0.5;
#endif
    gl_Position = clip;
    v_uv = vec2(vertex.position_uv.w, vertex.color_uv.w);
    v_color = vec4(vertex.color_uv.rgb * vertex.normal_light.x, vertex.normal_light.w) * instance.color;
    v_material = instance.material;
    v_animation_region = instance.animation_region;
    v_animation_next_region = instance.animation_next_region;
}
"#;

pub const MINIMAL_SHADOW_DEPTH_FRAGMENT: &str = r#"#version 450
layout(set = 0, binding = 2) uniform texture2D Tex0;
layout(set = 0, binding = 3) uniform sampler Samp0;
layout(location = 0) in vec2 v_uv;
layout(location = 1) in vec4 v_color;
layout(location = 2) flat in vec4 v_material;
layout(location = 6) flat in vec4 v_animation_region;
layout(location = 7) flat in vec4 v_animation_next_region;
void main() {
    vec2 sample_uv = v_animation_region.xy + v_uv * v_animation_region.zw;
    vec4 color = texture(sampler2D(Tex0, Samp0), sample_uv);
    if (v_material.y > 0.5) {
        vec2 next_uv = v_animation_next_region.xy + v_uv * v_animation_next_region.zw;
        vec4 next_color = texture(sampler2D(Tex0, Samp0), next_uv);
        color = mix(color, next_color, clamp(v_material.z, 0.0, 1.0));
    }
    color *= v_color;
    if (v_material.x > 0.0 && color.a < v_material.x) {
        discard;
    }
}
"#;
use crate::render::vulkanic::error::GalResult;
use crate::render::vulkanic::resources::BackendApi;

use super::terrain_contract::TerrainPassContract;
use super::voxel_light_volume::VoxelLightVolumeCache;

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
}

impl TerrainMaterialProgramKind {
    fn identity(self) -> ProgramIdentity {
        match self {
            Self::Opaque => ProgramIdentity::new("vulkanic:builtin/terrain_opaque_v1"),
            Self::Cutout => ProgramIdentity::new("vulkanic:builtin/terrain_cutout_v1"),
        }
    }

    fn label_suffix(self) -> &'static str {
        match self {
            Self::Opaque => "opaque",
            Self::Cutout => "cutout",
        }
    }
}

pub fn minimal_terrain_solid_program() -> TerrainMaterialProgram {
    minimal_terrain_material_program(TerrainMaterialProgramKind::Opaque)
}

pub fn minimal_terrain_cutout_program() -> TerrainMaterialProgram {
    minimal_terrain_material_program(TerrainMaterialProgramKind::Cutout)
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

pub fn minimal_direct_terrain_material_program(
    kind: TerrainMaterialProgramKind,
) -> TerrainMaterialProgram {
    TerrainMaterialProgram {
        identity: ProgramIdentity::new(match kind {
            TerrainMaterialProgramKind::Opaque => "vulkanic:builtin/direct_terrain_opaque_v1",
            TerrainMaterialProgramKind::Cutout => "vulkanic:builtin/direct_terrain_cutout_v1",
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
    CompositeProgram {
        identity: ProgramIdentity::new("vulkanic:builtin/g_buffer_composite_v1"),
        vertex: ShaderStageSource {
            stage: ShaderStageKind::Vertex,
            label: "minimal-g-buffer-composite.vertex".to_string(),
            source: MINIMAL_G_BUFFER_COMPOSITE_VERTEX.to_string(),
            entry_point: "main".to_string(),
        },
        fragment: ShaderStageSource {
            stage: ShaderStageKind::Fragment,
            label: "minimal-g-buffer-composite.fragment".to_string(),
            source: MINIMAL_G_BUFFER_COMPOSITE_FRAGMENT.to_string(),
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
            "#version 450\n#define VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH 1\n",
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
};
layout(set = 0, binding = 1, std430) readonly buffer WorldMeshInstances {
    mat4 view;
    mat4 projection;
    MeshInstance instances[];
};
layout(location = 0) out vec2 v_uv;
layout(location = 1) out vec4 v_color;
layout(location = 2) flat out vec4 v_material;
layout(location = 3) out vec3 v_normal;
layout(location = 4) out vec2 v_light;
void main() {
    MeshVertex vertex = vertices[gl_VertexIndex];
    MeshInstance instance = instances[gl_InstanceIndex];
    vec4 clip = projection * view * instance.model * vec4(vertex.position_uv.xyz, 1.0);
#ifdef VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH
    clip.z = clip.z * 0.5 + clip.w * 0.5;
#endif
    gl_Position = clip;
    v_uv = vec2(vertex.position_uv.w, vertex.color_uv.w);
    v_color = vec4(vertex.color_uv.rgb * vertex.normal_light.x, vertex.normal_light.w) * instance.color;
    v_material = vec4(instance.material.x, 0.0, 0.0, 0.0);
    v_normal = normalize(vertex.normal_light.yzw);
    v_light = clamp(vertex.extra_data.xy, vec2(0.0), vec2(1.0));
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
layout(location = 0) out vec4 out_albedo;
layout(location = 1) out vec4 out_normal;
layout(location = 2) out vec4 out_material_light;
void main() {
    vec4 color = texture(sampler2D(Tex0, Samp0), v_uv) * v_color;
    if (v_material.x > 0.0 && color.a < v_material.x) {
        discard;
    }
    vec3 n = normalize(v_normal) * 0.5 + 0.5;
    out_albedo = color;
    out_normal = vec4(n, color.a);
    out_material_light = vec4(v_material.x, v_light.x, v_light.y, color.a);
}
"#;

pub const MINIMAL_TERRAIN_MATERIAL_FRAGMENT_DIRECT: &str = r#"#version 450
layout(set = 0, binding = 2) uniform texture2D Tex0;
layout(set = 0, binding = 3) uniform sampler Samp0;
layout(location = 0) in vec2 v_uv;
layout(location = 1) in vec4 v_color;
layout(location = 2) flat in vec4 v_material;
layout(location = 0) out vec4 out_color;
void main() {
    vec4 color = texture(sampler2D(Tex0, Samp0), v_uv) * v_color;
    if (v_material.x > 0.0 && color.a < v_material.x) {
        discard;
    }
    out_color = color;
}
"#;

pub const MINIMAL_G_BUFFER_COMPOSITE_VERTEX: &str = r#"#version 450
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
}
"#;

pub const MINIMAL_G_BUFFER_COMPOSITE_FRAGMENT: &str = r#"#version 450
layout(set = 0, binding = 0) uniform texture2D AlbedoTex;
layout(set = 0, binding = 1) uniform texture2D NormalTex;
layout(set = 0, binding = 2) uniform texture2D MaterialLightTex;
layout(set = 0, binding = 3) uniform sampler Samp0;
layout(location = 0) in vec2 v_uv;
layout(location = 0) out vec4 out_color;
void main() {
    vec4 albedo = texture(sampler2D(AlbedoTex, Samp0), v_uv);
    vec3 normal = normalize(texture(sampler2D(NormalTex, Samp0), v_uv).xyz * 2.0 - 1.0);
    vec4 material_light = texture(sampler2D(MaterialLightTex, Samp0), v_uv);
    if (material_light.a < 0.5) {
        out_color = albedo;
        return;
    }
    float face = clamp(dot(normal, normalize(vec3(0.35, 0.65, 0.68))), 0.18, 1.0);
    float light = clamp(max(material_light.y, material_light.z) * 0.75 + 0.25, 0.2, 1.0);
    out_color = vec4(albedo.rgb * face * light, albedo.a);
}
"#;
use crate::render::vulkanic::resources::BackendApi;

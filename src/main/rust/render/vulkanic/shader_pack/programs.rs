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
}
"#;

pub const MINIMAL_TERRAIN_MATERIAL_FRAGMENT: &str = r#"#version 450
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

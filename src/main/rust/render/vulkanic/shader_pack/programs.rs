use crate::render::vulkanic::error::{GalError, GalResult};
use crate::render::vulkanic::resources::{
    BackendApi, ShaderCodeFormat, ShaderModuleDesc, ShaderStage,
};

use super::lowering::{
    LoweredTerrainSourcePair, TerrainSourceOpaqueResourceBindingPlan, TerrainSourceUniformField,
};
use super::source_uniforms::{TerrainSourceUniformFrame, TerrainSourceUniformRequirements};
use super::terrain_contract::{
    TerrainMaterialClass, TerrainPassContract, TerrainPassRequiredResource,
};

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

impl ShaderStageSource {
    /// Converts owned shader-pack text into an explicit GAL shader-module
    /// description. This selects only the portable coordinate convention
    /// required by the target API; backend compilation and native objects
    /// remain private to their respective backends.
    pub fn shader_module_descriptor(&self, api: BackendApi) -> ShaderModuleDesc {
        let stage = match self.stage {
            ShaderStageKind::Vertex => ShaderStage::Vertex,
            ShaderStageKind::Fragment => ShaderStage::Fragment,
        };
        ShaderModuleDesc {
            label: self.label.clone(),
            stage,
            code_format: ShaderCodeFormat::Glsl,
            code: shader_stage_code_for_backend(api, &self.source),
            entry_point: self.entry_point.clone(),
        }
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TerrainMaterialProgram {
    pub identity: ProgramIdentity,
    pub vertex: ShaderStageSource,
    pub fragment: ShaderStageSource,
    /// Backend-neutral semantic resources required in addition to the mesh
    /// material set. Pipeline construction turns these into ordinary GAL
    /// resource layouts; program descriptions never contain backend handles.
    pub required_resources: Vec<TerrainProgramResource>,
}

/// Actual source text after Rust's bounded terrain lowering, paired with the
/// pack-declared semantic sampler roles it needs. This is intentionally not a
/// `TerrainMaterialProgram`: no existing renderer path can compile or draw it
/// until a later source-mesh and semantic resource-set slice is complete.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LoweredTerrainSourceProgram {
    pub identity: ProgramIdentity,
    pub vertex: ShaderStageSource,
    pub fragment: ShaderStageSource,
    /// Fixed semantic bindings inserted by the source lowerer. They describe
    /// Rust-owned data, never a Java vertex layout or native backend object.
    pub execution_interface: TerrainSourceExecutionInterface,
    /// Typed scalar input requirements derived from the selected source.
    /// Unresolved entries intentionally keep this preparation artifact out of
    /// execution; this is never an untyped uniform payload.
    pub scalar_uniform_requirements: TerrainSourceUniformRequirements,
    pub opaque_resource_bindings: TerrainSourceOpaqueResourceBindingPlan,
    pub required_resources: Vec<TerrainProgramResource>,
}

impl LoweredTerrainSourceProgram {
    /// Produces explicit GAL shader descriptions from the retained, lowered
    /// source pair. Creating these descriptions does not allocate shader
    /// modules, make a pipeline, bind resources, or select a render route.
    pub fn shader_module_descriptors(&self, api: BackendApi) -> [ShaderModuleDesc; 2] {
        [
            self.vertex.shader_module_descriptor(api),
            self.fragment.shader_module_descriptor(api),
        ]
    }

    /// Packs only the named source semantics admitted by this prepared
    /// program. It cannot accept arbitrary bytes or backend state, and is
    /// still preparation-only until a future runtime owns the corresponding
    /// GAL buffer/resource-set lifecycle.
    pub fn pack_scalar_uniforms(&self, frame: &TerrainSourceUniformFrame) -> GalResult<Vec<u8>> {
        let bytes = frame.pack_std140(&self.scalar_uniform_requirements)?;
        if bytes.len() != self.execution_interface.scalar_uniform_bytes as usize {
            return Err(GalError::invalid_argument(format!(
                "terrain source scalar uniform pack is {} bytes but program ABI requires {}",
                bytes.len(),
                self.execution_interface.scalar_uniform_bytes
            )));
        }
        Ok(bytes)
    }
}

/// Backend-neutral storage kind required by one fixed source-lowering
/// binding. Resource-layout construction maps these semantic requirements to
/// ordinary GAL bindings in a later, explicit pipeline slice.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum TerrainSourceBindingKind {
    StorageBuffer,
    UniformBuffer,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct TerrainSourceFixedBinding {
    pub set: u32,
    pub binding: u32,
    pub kind: TerrainSourceBindingKind,
}

/// One fixed `vec4` lane of the source-lowering terrain vertex stream. These
/// names match Rust-owned lowered GLSL fields, not legacy Java attributes.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct TerrainSourceVertexField {
    pub name: &'static str,
    pub offset: u32,
    pub component_count: u32,
}

/// Source-derived requirements common to both lowered terrain stages. These
/// values match the lowered GLSL preamble and make its future GAL layout
/// explicit without allocating buffers or deciding routing.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TerrainSourceExecutionInterface {
    pub vertex_stream: TerrainSourceFixedBinding,
    pub vertex_stride: u32,
    pub vertex_fields: [TerrainSourceVertexField; 7],
    pub legacy_transforms: TerrainSourceFixedBinding,
    pub scalar_uniforms: Option<TerrainSourceFixedBinding>,
    pub legacy_transform_bytes: u32,
    pub scalar_uniform_bytes: u32,
    pub scalar_uniform_fields: Vec<TerrainSourceUniformField>,
}

impl TerrainSourceExecutionInterface {
    const VERTEX_STREAM: TerrainSourceFixedBinding = TerrainSourceFixedBinding {
        set: 0,
        binding: 0,
        kind: TerrainSourceBindingKind::StorageBuffer,
    };
    const LEGACY_TRANSFORMS: TerrainSourceFixedBinding = TerrainSourceFixedBinding {
        set: 0,
        binding: 1,
        kind: TerrainSourceBindingKind::UniformBuffer,
    };
    const SCALAR_UNIFORMS: TerrainSourceFixedBinding = TerrainSourceFixedBinding {
        set: 0,
        binding: 2,
        kind: TerrainSourceBindingKind::UniformBuffer,
    };

    fn from_lowered_pair(lowered: &LoweredTerrainSourcePair) -> Self {
        Self {
            vertex_stream: Self::VERTEX_STREAM,
            vertex_stride: 7 * 4 * std::mem::size_of::<f32>() as u32,
            vertex_fields: [
                TerrainSourceVertexField {
                    name: "position",
                    offset: 0,
                    component_count: 4,
                },
                TerrainSourceVertexField {
                    name: "color",
                    offset: 16,
                    component_count: 4,
                },
                TerrainSourceVertexField {
                    name: "normal_light",
                    offset: 32,
                    component_count: 4,
                },
                TerrainSourceVertexField {
                    name: "atlas_uv_lightmap",
                    offset: 48,
                    component_count: 4,
                },
                TerrainSourceVertexField {
                    name: "entity",
                    offset: 64,
                    component_count: 4,
                },
                TerrainSourceVertexField {
                    name: "mid_tex_coord",
                    offset: 80,
                    component_count: 4,
                },
                TerrainSourceVertexField {
                    name: "tangent",
                    offset: 96,
                    component_count: 4,
                },
            ],
            legacy_transforms: Self::LEGACY_TRANSFORMS,
            scalar_uniforms: (!lowered.uniform_contract().fields().is_empty())
                .then_some(Self::SCALAR_UNIFORMS),
            // Two std140 mat4 texture transforms.
            legacy_transform_bytes: 2 * 16 * std::mem::size_of::<f32>() as u32,
            scalar_uniform_bytes: lowered.uniform_contract().std140_size(),
            scalar_uniform_fields: lowered.uniform_contract().fields().to_vec(),
        }
    }

    /// Rejects an internally inconsistent prepared-source interface before a
    /// later runtime slice can turn it into GAL layouts and uploads. The
    /// source lowerer owns this ABI; callers must not be able to reinterpret
    /// it as a legacy renderer vertex format or arbitrary binding scheme.
    pub fn validate(&self) -> GalResult<()> {
        const EXPECTED_FIELDS: [(&str, u32); 7] = [
            ("position", 0),
            ("color", 16),
            ("normal_light", 32),
            ("atlas_uv_lightmap", 48),
            ("entity", 64),
            ("mid_tex_coord", 80),
            ("tangent", 96),
        ];

        if self.vertex_stream != Self::VERTEX_STREAM {
            return Err(GalError::invalid_argument(
                "terrain source vertex stream must use fixed set 0 binding 0 storage-buffer ABI",
            ));
        }
        if self.vertex_stride != 112 {
            return Err(GalError::invalid_argument(format!(
                "terrain source vertex stride {} does not match the fixed 112-byte ABI",
                self.vertex_stride
            )));
        }
        for (field, (expected_name, expected_offset)) in
            self.vertex_fields.iter().zip(EXPECTED_FIELDS)
        {
            if field.name != expected_name
                || field.offset != expected_offset
                || field.component_count != 4
            {
                return Err(GalError::invalid_argument(format!(
                    "terrain source vertex field '{}' is not the fixed {} vec4 lane at offset {}",
                    field.name, expected_name, expected_offset
                )));
            }
        }
        if self.legacy_transforms != Self::LEGACY_TRANSFORMS || self.legacy_transform_bytes != 128 {
            return Err(GalError::invalid_argument(
                "terrain source legacy transforms must use fixed set 0 binding 1 with two std140 mat4 values",
            ));
        }

        match (self.scalar_uniforms, self.scalar_uniform_fields.is_empty()) {
            (None, true) if self.scalar_uniform_bytes == 0 => return Ok(()),
            (Some(binding), false) if binding == Self::SCALAR_UNIFORMS => {}
            (None, false) => {
                return Err(GalError::invalid_argument(
                    "terrain source scalar fields require fixed set 0 binding 2",
                ));
            }
            (Some(_), true) => {
                return Err(GalError::invalid_argument(
                    "terrain source scalar binding is present without scalar fields",
                ));
            }
            (Some(_), false) => {
                return Err(GalError::invalid_argument(
                    "terrain source scalar block must use fixed set 0 binding 2 uniform-buffer ABI",
                ));
            }
            (None, true) => {
                return Err(GalError::invalid_argument(
                    "empty terrain source scalar block has non-zero byte size",
                ));
            }
        }

        let mut previous_end = 0_u32;
        let mut previous_name = "";
        for field in &self.scalar_uniform_fields {
            if field.name() <= previous_name {
                return Err(GalError::invalid_argument(
                    "terrain source scalar fields must be strictly name-sorted",
                ));
            }
            if field.offset() < previous_end || field.offset() % 4 != 0 {
                return Err(GalError::invalid_argument(format!(
                    "terrain source scalar field '{}' has overlapping or unaligned std140 offset {}",
                    field.name(),
                    field.offset()
                )));
            }
            let end = field.offset().checked_add(field.size()).ok_or_else(|| {
                GalError::invalid_argument("terrain source scalar field range overflows u32")
            })?;
            if field.array_length() == 1 && field.array_stride() != 0 {
                return Err(GalError::invalid_argument(format!(
                    "terrain source scalar field '{}' is not an array but has an array stride",
                    field.name()
                )));
            }
            if field.array_length() > 1
                && (field.array_stride() == 0 || field.array_stride() % 16 != 0)
            {
                return Err(GalError::invalid_argument(format!(
                    "terrain source scalar array '{}' has invalid std140 stride {}",
                    field.name(),
                    field.array_stride()
                )));
            }
            previous_end = end;
            previous_name = field.name();
        }
        if self.scalar_uniform_bytes == 0
            || self.scalar_uniform_bytes % 16 != 0
            || previous_end > self.scalar_uniform_bytes
        {
            return Err(GalError::invalid_argument(
                "terrain source scalar block size is not a valid std140 envelope",
            ));
        }
        Ok(())
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum TerrainProgramResource {
    ColoredVoxelLightVolume,
}

impl TerrainMaterialProgram {
    pub fn requires(&self, resource: TerrainProgramResource) -> bool {
        self.required_resources.contains(&resource)
    }
}

/// Forms an owned source-program preparation artifact only after the pair has
/// completed backend-neutral dialect lowering and its semantic sampler plan
/// is proven to belong to the same lowered source. The result is deliberately
/// kept separate from executable terrain programs until its source vertex
/// stream, scalar uniforms, and semantic resource sets are all supplied.
pub fn prepare_lowered_terrain_source_program(
    contract: &TerrainPassContract,
    lowered: &LoweredTerrainSourcePair,
    opaque_resource_bindings: &TerrainSourceOpaqueResourceBindingPlan,
    kind: TerrainMaterialProgramKind,
) -> GalResult<LoweredTerrainSourceProgram> {
    let material_class = match kind {
        TerrainMaterialProgramKind::Opaque => TerrainMaterialClass::Opaque,
        TerrainMaterialProgramKind::Cutout => TerrainMaterialClass::Cutout,
        TerrainMaterialProgramKind::Translucent => {
            return Err(GalError::unsupported_feature(
                "lowered terrain source preparation supports only opaque and cutout materials",
            ));
        }
    };
    if !contract.material_classes.contains(&material_class) {
        return Err(GalError::unsupported_feature(format!(
            "terrain source contract does not admit {:?} material preparation",
            kind
        )));
    }
    lowered.require_backend_neutral_lowering()?;
    lowered.require_matching_opaque_resource_bindings(opaque_resource_bindings)?;
    let suffix = match kind {
        TerrainMaterialProgramKind::Opaque => "opaque",
        TerrainMaterialProgramKind::Cutout => "cutout",
        TerrainMaterialProgramKind::Translucent => unreachable!(),
    };
    let requires_colored_voxel_light = contract
        .required_resources
        .contains(&TerrainPassRequiredResource::ColoredVoxelLightVolume);
    let execution_interface = TerrainSourceExecutionInterface::from_lowered_pair(lowered);
    execution_interface.validate()?;
    let scalar_uniform_requirements =
        TerrainSourceUniformRequirements::from_contract(lowered.uniform_contract())?;
    scalar_uniform_requirements.require_fully_semantic()?;
    Ok(LoweredTerrainSourceProgram {
        identity: ProgramIdentity::new(format!(
            "vulkanic:shader-pack/{}/terrain_{}_source_gen{}",
            contract.pack_name.to_ascii_lowercase(),
            suffix,
            contract.generation
        )),
        vertex: ShaderStageSource {
            stage: ShaderStageKind::Vertex,
            label: format!("{}:lowered-vertex", lowered.vertex().entry_path()),
            source: lowered.vertex().source().to_string(),
            entry_point: "main".to_string(),
        },
        fragment: ShaderStageSource {
            stage: ShaderStageKind::Fragment,
            label: format!("{}:lowered-fragment", lowered.fragment().entry_path()),
            source: lowered.fragment().source().to_string(),
            entry_point: "main".to_string(),
        },
        execution_interface,
        scalar_uniform_requirements,
        opaque_resource_bindings: opaque_resource_bindings.clone(),
        required_resources: if requires_colored_voxel_light {
            vec![TerrainProgramResource::ColoredVoxelLightVolume]
        } else {
            Vec::new()
        },
    })
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
        required_resources: Vec::new(),
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
    voxel_light_volume: Option<&VoxelLightVolumeReadiness>,
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
    let requires_colored_voxel_light = contract
        .required_resources
        .contains(&super::terrain_contract::TerrainPassRequiredResource::ColoredVoxelLightVolume);
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
            source: complementary_terrain_subset_fragment_source(requires_colored_voxel_light),
            entry_point: "main".to_string(),
        },
        required_resources: if requires_colored_voxel_light {
            vec![TerrainProgramResource::ColoredVoxelLightVolume]
        } else {
            Vec::new()
        },
    })
}

/// Keeps the shader interface exactly aligned with the semantic resource
/// contract. A profile without `ColoredVoxelLighting` must not declare an
/// unbound set-1 D3 interface merely because another admitted profile uses it.
fn complementary_terrain_subset_fragment_source(requires_colored_voxel_light: bool) -> String {
    if requires_colored_voxel_light {
        COMPLEMENTARY_TERRAIN_SUBSET_FRAGMENT.replacen(
            "#version 450\n",
            "#version 450\n#define VULKANIC_TERRAIN_COLORED_VOXEL_LIGHT 1\n",
            1,
        )
    } else {
        COMPLEMENTARY_TERRAIN_SUBSET_FRAGMENT.to_string()
    }
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
        required_resources: Vec::new(),
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
        required_resources: Vec::new(),
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
#ifdef VULKANIC_TERRAIN_COLORED_VOXEL_LIGHT
// Rust-owned semantic ColoredVoxelLighting resources. These use a second
// ordinary GAL resource set rather than any Iris or backend-private state.
// The matching layout is created by TerrainVoxelLightSamplingResources.
layout(set = 1, binding = 0) uniform utexture3D TerrainVoxelOccupancy;
layout(set = 1, binding = 1) uniform texture3D TerrainColoredVoxelLight;
layout(set = 1, binding = 2) uniform sampler TerrainVoxelLightSampler;
layout(set = 1, binding = 3, std140) uniform TerrainVoxelLightMapping {
    vec4 scene_to_volume_offset_and_normal_offset;
    vec4 scene_to_volume_scale;
    vec4 inverse_extent;
    ivec4 valid_world_min;
    ivec4 valid_world_max_exclusive;
    ivec4 camera_cell;
};
#endif
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

#ifdef VULKANIC_TERRAIN_COLORED_VOXEL_LIGHT
vec3 terrain_voxel_coordinate(vec3 world_position, vec3 surface_normal) {
    vec3 shifted_world = world_position
        + surface_normal * scene_to_volume_offset_and_normal_offset.w;
    return (shifted_world - vec3(camera_cell.xyz)
        + scene_to_volume_offset_and_normal_offset.xyz)
        * scene_to_volume_scale.xyz
        * inverse_extent.xyz;
}
#endif

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
#ifdef VULKANIC_TERRAIN_COLORED_VOXEL_LIGHT
    vec3 voxel_coordinate = terrain_voxel_coordinate(v_world_position, normal);
    bool voxel_coordinate_in_bounds = all(greaterThanEqual(voxel_coordinate, vec3(0.0)))
        && all(lessThanEqual(voxel_coordinate, vec3(1.0)));
    // The occupancy lookup keeps the material field in the semantic contract
    // and makes a missing/incorrect integer D3 binding visible rather than
    // silently using only the flood-fill texture.
    uint voxel_occupancy = voxel_coordinate_in_bounds
        ? texture(usampler3D(TerrainVoxelOccupancy, TerrainVoxelLightSampler), voxel_coordinate).r
        : 0u;
    vec3 colored_voxel_light = voxel_coordinate_in_bounds && voxel_occupancy != 0u
        ? texture(sampler3D(TerrainColoredVoxelLight, TerrainVoxelLightSampler), voxel_coordinate).rgb
        : vec3(0.0);
#else
    vec3 colored_voxel_light = vec3(0.0);
#endif
    vec3 final_diffuse = sqrt(max(
        vec3(raw_ao * directional_shade * directional_shade) *
        (vec3(block_light) + scene_lighting * scene_lighting + colored_voxel_light),
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

#[cfg(test)]
mod tests {
    use super::*;
    use crate::render::vulkanic::shader_pack::lowering::lower_terrain_source_pair;
    use crate::render::vulkanic::shader_pack::preprocess::preprocess_terrain_sources;
    use crate::render::vulkanic::shader_pack::source::{ShaderPackSource, ShaderSourceFile};
    use crate::render::vulkanic::shader_pack::terrain_contract::derive_complementary_terrain_contract;
    use crate::render::vulkanic::shader_pack::terrain_source_resources::{
        TerrainSourceResourceBindings, TerrainSourceResourceRole, TERRAIN_RESOURCE_BINDINGS_PATH,
    };

    fn paired_source() -> ShaderPackSource {
        ShaderPackSource::new(
            "lowered-source-test",
            9,
            vec![
                ShaderSourceFile::new(
                    "gbuffers_terrain.vsh",
                    "#version 130\nout vec2 texCoord;\nout vec4 glColor;\nout float smoothnessD;\nout float materialMask;\nout float skyLightFactor;\nuniform sampler2D tex;\nvoid main() { texCoord = gl_MultiTexCoord0.xy; glColor = vec4(1.0); smoothnessD = 0.0; materialMask = 0.0; skyLightFactor = 1.0; gl_Position = ftransform(); }",
                ),
                ShaderSourceFile::new(
                    "gbuffers_terrain.fsh",
                    "#version 130\nin vec2 texCoord;\nin vec4 glColor;\nin float smoothnessD;\nin float materialMask;\nin float skyLightFactor;\nuniform sampler2D tex;\nvoid DoLighting() {}\n/* DRAWBUFFERS:06 */\nvoid main() { vec4 color = texture2D(tex, texCoord); if (color.a <= 0.00001) discard; color.rgb *= glColor.rgb; DoLighting(); gl_FragData[0] = color; gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 1.0); }",
                ),
                ShaderSourceFile::new("lib/common.glsl", "#define TEST 1\n"),
                ShaderSourceFile::new("shaders.properties", ""),
                ShaderSourceFile::new("block.properties", ""),
                ShaderSourceFile::new(TERRAIN_RESOURCE_BINDINGS_PATH, "tex=material_atlas\n"),
            ],
        )
        .unwrap()
    }

    #[test]
    fn lowered_source_program_preserves_real_stages_and_semantic_sampler_plan() {
        let source = paired_source();
        let contract = derive_complementary_terrain_contract(&source).unwrap();
        let artifacts =
            preprocess_terrain_sources(&source, &contract.source_stages().unwrap()).unwrap();
        let lowered = lower_terrain_source_pair(&artifacts.vertex, &artifacts.fragment).unwrap();
        let declarations = TerrainSourceResourceBindings::from_source(&source).unwrap();
        let bindings = lowered
            .opaque_resource_contract()
            .bind_semantic_roles(&declarations)
            .unwrap();

        let program = prepare_lowered_terrain_source_program(
            &contract,
            &lowered,
            &bindings,
            TerrainMaterialProgramKind::Opaque,
        )
        .unwrap();

        assert_eq!(
            "vulkanic:shader-pack/lowered-source-test/terrain_opaque_source_gen9",
            program.identity.as_str()
        );
        assert_eq!(lowered.vertex().source(), program.vertex.source);
        assert_eq!(lowered.fragment().source(), program.fragment.source);
        assert_eq!(1, program.opaque_resource_bindings.bindings().len());
        assert_eq!(
            TerrainSourceResourceRole::MaterialAtlas,
            program.opaque_resource_bindings.bindings()[0].role()
        );
        assert_eq!(
            crate::render::vulkanic::shader_pack::lowering::TerrainSourceOpaqueResourceKind::CombinedTextureSampler,
            program.opaque_resource_bindings.bindings()[0].kind()
        );
        let vulkan_modules = program.shader_module_descriptors(BackendApi::Vulkan);
        assert_eq!(ShaderStage::Vertex, vulkan_modules[0].stage);
        assert_eq!(ShaderStage::Fragment, vulkan_modules[1].stage);
        assert_eq!(ShaderCodeFormat::Glsl, vulkan_modules[0].code_format);
        assert!(std::str::from_utf8(&vulkan_modules[0].code)
            .unwrap()
            .contains("VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH"));
        let opengl_modules = program.shader_module_descriptors(BackendApi::OpenGl);
        assert!(!std::str::from_utf8(&opengl_modules[0].code)
            .unwrap()
            .contains("VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH"));
        assert_eq!(program.vertex.label, vulkan_modules[0].label);
        assert_eq!(program.fragment.label, vulkan_modules[1].label);
        assert_eq!(
            TerrainSourceFixedBinding {
                set: 0,
                binding: 0,
                kind: TerrainSourceBindingKind::StorageBuffer,
            },
            program.execution_interface.vertex_stream
        );
        assert_eq!(112, program.execution_interface.vertex_stride);
        assert_eq!(
            [
                "position",
                "color",
                "normal_light",
                "atlas_uv_lightmap",
                "entity",
                "mid_tex_coord",
                "tangent",
            ],
            program
                .execution_interface
                .vertex_fields
                .map(|field| field.name)
        );
        assert_eq!(
            TerrainSourceFixedBinding {
                set: 0,
                binding: 1,
                kind: TerrainSourceBindingKind::UniformBuffer,
            },
            program.execution_interface.legacy_transforms
        );
        assert_eq!(128, program.execution_interface.legacy_transform_bytes);
        assert_eq!(
            Some(TerrainSourceFixedBinding {
                set: 0,
                binding: 2,
                kind: TerrainSourceBindingKind::UniformBuffer,
            }),
            program.execution_interface.scalar_uniforms
        );
        assert_eq!(128, program.execution_interface.scalar_uniform_bytes);
        assert_eq!(
            program.execution_interface.scalar_uniform_bytes,
            lowered.uniform_contract().std140_size()
        );
        assert_eq!(
            program.execution_interface.scalar_uniform_fields,
            lowered.uniform_contract().fields()
        );
        assert_eq!(
            program
                .execution_interface
                .scalar_uniform_fields
                .iter()
                .map(|field| field.name())
                .collect::<Vec<_>>(),
            ["gbufferModelView", "gbufferProjection"]
        );
        assert!(program
            .pack_scalar_uniforms(&TerrainSourceUniformFrame::default())
            .unwrap_err()
            .to_string()
            .contains("view matrix"));
        assert_eq!(
            128,
            program
                .pack_scalar_uniforms(&TerrainSourceUniformFrame {
                    view_matrix: Some([1.0; 16]),
                    projection_matrix: Some([2.0; 16]),
                    ..TerrainSourceUniformFrame::default()
                })
                .unwrap()
                .len()
        );
        assert!(program.required_resources.is_empty());
        assert!(prepare_lowered_terrain_source_program(
            &contract,
            &lowered,
            &bindings,
            TerrainMaterialProgramKind::Translucent,
        )
        .is_err());
    }

    #[test]
    fn prepared_source_interface_rejects_mutated_fixed_abi_fields() {
        let source = paired_source();
        let contract = derive_complementary_terrain_contract(&source).unwrap();
        let artifacts =
            preprocess_terrain_sources(&source, &contract.source_stages().unwrap()).unwrap();
        let lowered = lower_terrain_source_pair(&artifacts.vertex, &artifacts.fragment).unwrap();
        let declarations = TerrainSourceResourceBindings::from_source(&source).unwrap();
        let bindings = lowered
            .opaque_resource_contract()
            .bind_semantic_roles(&declarations)
            .unwrap();
        let program = prepare_lowered_terrain_source_program(
            &contract,
            &lowered,
            &bindings,
            TerrainMaterialProgramKind::Opaque,
        )
        .unwrap();

        program.execution_interface.validate().unwrap();

        let mut wrong_stride = program.execution_interface.clone();
        wrong_stride.vertex_stride = 96;
        assert!(wrong_stride
            .validate()
            .unwrap_err()
            .to_string()
            .contains("112-byte ABI"));

        let mut wrong_lane = program.execution_interface.clone();
        wrong_lane.vertex_fields[3].offset = 64;
        assert!(wrong_lane
            .validate()
            .unwrap_err()
            .to_string()
            .contains("atlas_uv_lightmap"));

        let mut missing_scalar_binding = program.execution_interface.clone();
        missing_scalar_binding.scalar_uniforms = None;
        assert!(missing_scalar_binding
            .validate()
            .unwrap_err()
            .to_string()
            .contains("scalar fields require fixed set 0 binding 2"));
    }
}
use super::voxel_light_volume::VoxelLightVolumeReadiness;

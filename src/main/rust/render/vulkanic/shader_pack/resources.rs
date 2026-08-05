use crate::render::vulkanic::error::{GalError, GalResult};

use super::manifest::{ShaderPackConfig, ShaderPackManifest};
use super::pass_graph::{AttachmentIdentity, AttachmentRole, PassGraph, PassIdentity};
use super::programs::{
    complementary_terrain_subset_program, minimal_composite_color_grade_program,
    minimal_composite_depth_fog_program, minimal_deferred_lighting_program,
    minimal_final_copy_program, minimal_shadow_depth_program, minimal_terrain_cutout_program,
    minimal_terrain_solid_program, minimal_terrain_translucent_program, CompositeProgram,
    ProgramIdentity, TerrainMaterialProgram, TerrainMaterialProgramKind,
};
use super::terrain_contract::{
    bundled_complementary_hung_loified_source, derive_complementary_terrain_contract,
    TerrainPassContract,
};
use super::voxel_light_volume::{VoxelLightVolumeCache, VoxelLightVolumeDescriptor};

#[derive(Clone, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub struct MaterialIdentity(String);

impl MaterialIdentity {
    pub fn new(value: impl Into<String>) -> Self {
        Self(value.into())
    }

    pub fn as_str(&self) -> &str {
        &self.0
    }
}

#[derive(Clone, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub struct SamplerIdentity(String);

impl SamplerIdentity {
    pub fn new(value: impl Into<String>) -> Self {
        Self(value.into())
    }

    pub fn as_str(&self) -> &str {
        &self.0
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct ResourceGeneration(pub u64);

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ShaderPackResourceManifest {
    pub generation: ResourceGeneration,
    pub programs: Vec<ProgramIdentity>,
    pub passes: Vec<PassIdentity>,
    pub attachments: Vec<AttachmentIdentity>,
    pub materials: Vec<MaterialIdentity>,
    pub samplers: Vec<SamplerIdentity>,
}

#[derive(Clone, Debug, PartialEq)]
pub struct ShaderPackProgramSet {
    pub shadow_depth: TerrainMaterialProgram,
    pub terrain_opaque: TerrainMaterialProgram,
    pub terrain_cutout: TerrainMaterialProgram,
    pub terrain_translucent: TerrainMaterialProgram,
    pub deferred_lighting: CompositeProgram,
    pub composite_0: CompositeProgram,
    pub composite_1: CompositeProgram,
    pub final_output: CompositeProgram,
}

impl ShaderPackProgramSet {
    pub fn terrain_material_multipass_v1() -> Self {
        Self {
            shadow_depth: minimal_shadow_depth_program(),
            terrain_opaque: minimal_terrain_solid_program(),
            terrain_cutout: minimal_terrain_cutout_program(),
            terrain_translucent: minimal_terrain_translucent_program(),
            deferred_lighting: minimal_deferred_lighting_program(),
            composite_0: minimal_composite_color_grade_program(),
            composite_1: minimal_composite_depth_fog_program(),
            final_output: minimal_final_copy_program(),
        }
    }
}

#[derive(Clone, Debug, PartialEq)]
pub struct ShaderPackRuntimePlan {
    pub generation: u64,
    pub config: ShaderPackConfig,
    pub graph: PassGraph,
    pub resources: ShaderPackResourceManifest,
    pub programs: ShaderPackProgramSet,
    /// Source-derived semantics for the terrain material pass. This carries no
    /// Java, Iris, GL, or Vulkan objects; executable program selection remains
    /// a Rust shader-pack-runtime decision.
    pub terrain_contract: Option<TerrainPassContract>,
    /// Semantic identity and generation only; backend resources stay private.
    pub voxel_light_volume: Option<VoxelLightVolumeDescriptor>,
}

impl ShaderPackRuntimePlan {
    pub fn terrain_material_multipass_v1(generation: u64) -> GalResult<Self> {
        let config = ShaderPackConfig::internal_shadow_composite_fixture(generation)?;
        let graph = config.pass_graph()?;
        Ok(Self {
            generation,
            config,
            graph,
            resources: ShaderPackResourceManifest::terrain_material_v1(generation)?,
            programs: ShaderPackProgramSet::terrain_material_multipass_v1(),
            terrain_contract: None,
            voxel_light_volume: None,
        })
    }

    pub fn complementary_terrain_contract_v1(generation: u64) -> GalResult<Self> {
        let source = bundled_complementary_hung_loified_source(generation)?;
        Self::terrain_material_from_source(generation, &source)
    }

    pub fn discover_terrain_contract_from_source(
        generation: u64,
        source: &super::source::ShaderPackSource,
    ) -> GalResult<TerrainPassContract> {
        if source.generation() != generation {
            return Err(crate::render::vulkanic::error::GalError::invalid_argument(
                "shader-pack source generation must match runtime generation",
            ));
        }
        derive_complementary_terrain_contract(source)
    }

    pub fn terrain_material_from_source(
        generation: u64,
        source: &super::source::ShaderPackSource,
    ) -> GalResult<Self> {
        let contract = Self::discover_terrain_contract_from_source(generation, source)?;
        let terrain_opaque =
            complementary_terrain_subset_program(&contract, TerrainMaterialProgramKind::Opaque)?;
        let terrain_cutout =
            complementary_terrain_subset_program(&contract, TerrainMaterialProgramKind::Cutout)?;
        let mut plan = Self::terrain_material_multipass_v1(generation)?;
        plan.resources.programs[1] = terrain_opaque.identity.clone();
        plan.resources.programs[2] = terrain_cutout.identity.clone();
        plan.programs.terrain_opaque = terrain_opaque;
        plan.programs.terrain_cutout = terrain_cutout;
        plan.terrain_contract = Some(contract);
        Ok(plan)
    }

    /// Creates a selected-source plan only after the required colored-light
    /// volume has been fully decoded and validated by Rust.
    pub fn terrain_material_from_source_with_voxel_light_volume(
        generation: u64,
        source: &super::source::ShaderPackSource,
        volume: &VoxelLightVolumeCache,
        frame_counter: u64,
    ) -> GalResult<Self> {
        let descriptor = volume.descriptor().ok_or_else(|| {
            GalError::invalid_argument(
                "missing voxel-light volume descriptor for selected terrain source",
            )
        })?;
        if descriptor.shader_pack_generation != generation {
            return Err(GalError::invalid_argument(
                "voxel-light shader-pack generation does not match terrain source",
            ));
        }
        let contract = Self::discover_terrain_contract_from_source(generation, source)?;
        let terrain_opaque = super::programs::complementary_terrain_subset_program_with_resources(
            &contract,
            TerrainMaterialProgramKind::Opaque,
            Some(volume),
            frame_counter,
        )?;
        let terrain_cutout = super::programs::complementary_terrain_subset_program_with_resources(
            &contract,
            TerrainMaterialProgramKind::Cutout,
            Some(volume),
            frame_counter,
        )?;
        let mut plan = Self::terrain_material_multipass_v1(generation)?;
        plan.resources.programs[1] = terrain_opaque.identity.clone();
        plan.resources.programs[2] = terrain_cutout.identity.clone();
        plan.programs.terrain_opaque = terrain_opaque;
        plan.programs.terrain_cutout = terrain_cutout;
        plan.terrain_contract = Some(contract);
        plan.voxel_light_volume = Some(descriptor.clone());
        Ok(plan)
    }

    pub fn terrain_contract_diagnostic_json(&self) -> String {
        let Some(contract) = &self.terrain_contract else {
            return "{\"source_contract_discovered\":false,\"execution\":\"internal-terrain-fixture\"}".to_string();
        };
        let inputs = contract
            .inputs
            .iter()
            .map(|input| format!("\"{input:?}\""))
            .collect::<Vec<_>>()
            .join(",");
        let outputs = contract
            .outputs
            .iter()
            .map(|output| format!("\"{}\"", output.semantic_name()))
            .collect::<Vec<_>>()
            .join(",");
        let operations = contract
            .operations
            .iter()
            .map(|operation| format!("\"{operation:?}\""))
            .collect::<Vec<_>>()
            .join(",");
        let unsupported = contract
            .unsupported
            .iter()
            .map(|feature| format!("\"{feature:?}\""))
            .collect::<Vec<_>>()
            .join(",");
        let required_resources = contract
            .required_resources
            .iter()
            .map(|resource| format!("\"{resource:?}\""))
            .collect::<Vec<_>>()
            .join(",");
        let voxel_generation = self
            .voxel_light_volume
            .as_ref()
            .map(|volume| volume.resource_generation.to_string())
            .unwrap_or_else(|| "null".to_string());
        format!(
            concat!(
                "{{\"source_contract_discovered\":true,\"pack\":\"{}\",\"generation\":{},",
                "\"program\":\"{}\",\"operations\":[{}],\"outputs\":[{}],\"inputs\":[{}],",
                "\"input_bindings\":{{\"AtlasColor\":\"semantic material atlas texture\",",
                "\"AtlasUv\":\"WorldMeshVertex.shader_atlas_uv\",",
                "\"Tint\":\"WorldMeshVertex.color_argb.rgb\",",
                "\"AmbientOcclusion\":\"WorldMeshVertex.color_argb.alpha when separate AO\",",
                "\"PackedBlockLight\":\"WorldMeshVertex.light[0..3]\",",
                "\"PackedSkyLight\":\"WorldMeshVertex.light[20..23]\",",
                "\"GeometricNormal\":\"WorldMeshVertex.normal_packed\",",
                "\"MaterialIdentity\":\"WorldMeshVertex.shader_block_id\",",
                "\"WorldPosition\":\"WorldMeshVertex.position plus semantic instance model\"}},",
                "\"output_bindings\":{{\"terrain_lit_color\":\"terrain color attachment location 0\",",
                "\"terrain_material_auxiliary\":\"terrain auxiliary attachment location 2\",",
                "\"terrain_view_space_normal\":\"terrain normal attachment location 1 when declared\"}},",
                "\"unsupported\":[{}],\"required_resources\":[{}],\"voxel_light_volume_generation\":{},\"selected_source_plan_admitted\":{},\"execution\":\"internal-terrain-fixture; selected-source execution is explicitly unavailable until its semantic resources are GPU-bound and the source-derived program is lowered\"}}"
            ),
            json_escape(&contract.pack_name),
            contract.generation,
            json_escape(&contract.program_path),
            operations,
            outputs,
            inputs,
            unsupported,
            required_resources,
            voxel_generation,
            contract.supports_selected_subset()
                && (contract.required_resources.is_empty() || self.voxel_light_volume.is_some()),
        )
    }

    pub fn declared_attachment_roles(&self) -> Vec<AttachmentRole> {
        self.config
            .attachments
            .iter()
            .map(|attachment| attachment.role)
            .collect()
    }
}

fn json_escape(value: &str) -> String {
    value.replace('\\', "\\\\").replace('"', "\\\"")
}

impl ShaderPackResourceManifest {
    pub fn terrain_material_v1(generation: u64) -> GalResult<Self> {
        if generation == 0 {
            return Err(GalError::invalid_argument(
                "shader-pack resource generation must be non-zero",
            ));
        }
        Ok(Self {
            generation: ResourceGeneration(generation),
            programs: vec![
                ProgramIdentity::new("vulkanic:builtin/shadow_depth_v1"),
                ProgramIdentity::new("vulkanic:builtin/terrain_opaque_v1"),
                ProgramIdentity::new("vulkanic:builtin/terrain_cutout_v1"),
                ProgramIdentity::new("vulkanic:builtin/terrain_translucent_v1"),
                ProgramIdentity::new("vulkanic:builtin/deferred_lighting_v1"),
                ProgramIdentity::new("vulkanic:builtin/composite_color_grade_v1"),
                ProgramIdentity::new("vulkanic:builtin/composite_depth_fog_v1"),
                ProgramIdentity::new("vulkanic:builtin/final_copy_v1"),
            ],
            passes: vec![
                PassIdentity::new("vulkanic:pass/shadow_depth"),
                PassIdentity::new("vulkanic:pass/terrain_opaque"),
                PassIdentity::new("vulkanic:pass/terrain_cutout"),
                PassIdentity::new("vulkanic:pass/deferred_lighting"),
                PassIdentity::new("vulkanic:pass/terrain_translucent"),
                PassIdentity::new("vulkanic:pass/composite_0"),
                PassIdentity::new("vulkanic:pass/composite_1"),
                PassIdentity::new("vulkanic:pass/final_output"),
            ],
            attachments: vec![
                AttachmentIdentity::new("vulkanic:attachment/shadow_depth"),
                AttachmentIdentity::new("vulkanic:attachment/g_buffer_albedo"),
                AttachmentIdentity::new("vulkanic:attachment/g_buffer_normal"),
                AttachmentIdentity::new("vulkanic:attachment/g_buffer_material_light"),
                AttachmentIdentity::new("vulkanic:attachment/g_buffer_world_position"),
                AttachmentIdentity::new("vulkanic:attachment/g_buffer_depth"),
                AttachmentIdentity::new("vulkanic:attachment/deferred_lit_color"),
                AttachmentIdentity::new("vulkanic:attachment/composite_0"),
                AttachmentIdentity::new("vulkanic:attachment/composite_1"),
                AttachmentIdentity::new("vulkanic:attachment/final_color"),
            ],
            materials: vec![
                MaterialIdentity::new("minecraft:material/opaque_textured"),
                MaterialIdentity::new("minecraft:material/cutout_textured"),
                MaterialIdentity::new("minecraft:material/translucent_textured"),
            ],
            samplers: vec![SamplerIdentity::new("vulkanic:sampler/nearest_clamp")],
        })
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ActiveShaderPack {
    pub generation: u64,
    pub manifest: ShaderPackManifest,
    pub resources: ShaderPackResourceManifest,
}

#[derive(Default)]
pub struct ShaderPackResources {
    active: Option<ActiveShaderPack>,
    failed_generations: Vec<u64>,
}

impl ShaderPackResources {
    pub fn apply_generation(
        &mut self,
        generation: u64,
        manifest: ShaderPackManifest,
    ) -> GalResult<()> {
        self.apply_resource_generation(
            generation,
            manifest,
            ShaderPackResourceManifest::terrain_material_v1(generation)?,
        )
    }

    pub fn apply_resource_generation(
        &mut self,
        generation: u64,
        manifest: ShaderPackManifest,
        resources: ShaderPackResourceManifest,
    ) -> GalResult<()> {
        if generation == 0 || generation != manifest.generation() {
            self.failed_generations.push(generation);
            return Err(GalError::invalid_argument(
                "shader-pack generation must match manifest generation",
            ));
        }
        if generation != resources.generation.0 {
            self.failed_generations.push(generation);
            return Err(GalError::invalid_argument(
                "shader-pack generation must match resource generation",
            ));
        }
        if self
            .active
            .as_ref()
            .map(|active| generation <= active.generation)
            .unwrap_or(false)
        {
            self.failed_generations.push(generation);
            return Err(GalError::invalid_argument("stale shader-pack generation"));
        }
        self.active = Some(ActiveShaderPack {
            generation,
            manifest,
            resources,
        });
        Ok(())
    }

    pub fn active_generation(&self) -> Option<u64> {
        self.active.as_ref().map(|active| active.generation)
    }

    pub fn active(&self) -> Option<&ActiveShaderPack> {
        self.active.as_ref()
    }

    pub fn failed_generations(&self) -> &[u64] {
        &self.failed_generations
    }
}

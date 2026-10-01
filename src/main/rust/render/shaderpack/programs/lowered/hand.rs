//! Lowered first-person hand source programs.

use super::*;

/// Prepared selected-source first-person hand/item program. It has the same
/// owned indexed-stream ABI as an entity program, but retains a distinct hand
/// contract so a later writer cannot accidentally use world-camera transforms
/// or the entity pass depth domain.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LoweredHandSourceProgram {
    pub identity: ProgramIdentity,
    pub shader_pack_generation: u64,
    pub(in crate::render::shaderpack::programs) hand_contract: HandPassContract,
    pub vertex: ShaderStageSource,
    pub fragment: ShaderStageSource,
    pub execution_interface: TerrainSourceExecutionInterface,
    pub scalar_uniform_requirements: TerrainSourceUniformRequirements,
    pub opaque_resource_bindings: TerrainSourceOpaqueResourceBindingPlan,
    pub(in crate::render::shaderpack::programs) named_output_color_slots: Vec<(TerrainPassOutput, u32)>,
}

impl LoweredHandSourceProgram {
    pub fn shader_module_descriptors(&self, conventions: ShaderConventions) -> [ShaderModuleDesc; 2] {
        [
            self.vertex.shader_module_descriptor(conventions),
            self.fragment.shader_module_descriptor(conventions),
        ]
    }

    /// The first-person writer has the same explicit cutout contract as an
    /// entity writer, but the projection/depth domain remains hand-specific.
    pub fn shader_module_descriptors_with_alpha_cutoff(
        &self,
        conventions: ShaderConventions,
        alpha_cutoff: Option<f32>,
    ) -> [ShaderModuleDesc; 2] {
        let fragment_source = match alpha_cutoff {
            Some(cutoff) => {
                debug_assert!(cutoff.is_finite() && cutoff >= 0.0);
                let declaration =
                    format!("#define VULKANIC_SOURCE_ENTITY_ALPHA_CUTOFF {cutoff:.8}\n");
                self.fragment.source.replacen(
                    "#version 450\n",
                    &format!("#version 450\n{declaration}"),
                    1,
                )
            }
            None => self.fragment.source.clone(),
        };
        [
            self.vertex.shader_module_descriptor(conventions),
            ShaderModuleDesc {
                label: self.fragment.label.clone(),
                stage: ShaderStage::Fragment,
                code_format: ShaderCodeFormat::Glsl,
                code: shader_stage_code(conventions, &fragment_source),
                entry_point: self.fragment.entry_point.clone(),
            },
        ]
    }

    pub fn pack_scalar_uniforms(&self, frame: &TerrainSourceUniformFrame) -> GalResult<Vec<u8>> {
        let bytes = frame.pack_std140(&self.scalar_uniform_requirements)?;
        if bytes.len() != self.execution_interface.scalar_uniform_bytes as usize {
            return Err(GalError::invalid_argument(format!(
                "hand source scalar uniform pack is {} bytes but program ABI requires {}",
                bytes.len(),
                self.execution_interface.scalar_uniform_bytes
            )));
        }
        Ok(bytes)
    }

    pub fn pack_legacy_texture_transforms(
        &self,
        transforms: &TerrainSourceTextureTransforms,
        hand_projection: &[f32; 16],
    ) -> GalResult<Vec<u8>> {
        self.execution_interface.validate()?;
        if self.execution_interface.legacy_transform_bytes != 192 {
            return Err(GalError::invalid_argument(
                "hand source requires two texture matrices and a distinct clip projection",
            ));
        }
        transforms.validate()?;
        if !hand_projection.iter().all(|value| value.is_finite()) {
            return Err(GalError::invalid_argument(
                "hand source projection matrix contains a non-finite value",
            ));
        }
        let mut bytes =
            Vec::with_capacity(self.execution_interface.legacy_transform_bytes as usize);
        for value in transforms
            .atlas_texture_matrix
            .iter()
            .chain(transforms.lightmap_texture_matrix.iter())
            .chain(hand_projection.iter())
        {
            bytes.extend_from_slice(&value.to_ne_bytes());
        }
        if bytes.len() != self.execution_interface.legacy_transform_bytes as usize {
            return Err(GalError::invalid_argument(
                "hand source legacy texture transform pack does not match its fixed ABI size",
            ));
        }
        Ok(bytes)
    }

    pub fn execution_resource_layouts(&self) -> GalResult<TerrainSourceExecutionLayouts> {
        self.execution_interface.validate()?;
        let mut source_data_bindings = vec![
            resource_binding_descriptor(self.execution_interface.vertex_stream, false),
            resource_binding_descriptor(self.execution_interface.legacy_transforms, true),
            resource_binding_descriptor(self.execution_interface.instance_stream, true),
        ];
        if let Some(scalar_uniforms) = self.execution_interface.scalar_uniforms {
            source_data_bindings.push(resource_binding_descriptor(scalar_uniforms, true));
        }
        source_data_bindings.sort_by_key(|binding| binding.binding);
        validate_unique_layout_bindings("hand source data", &source_data_bindings)?;

        let mut pack_resource_bindings =
            Vec::with_capacity(self.opaque_resource_bindings.bindings().len());
        for source_binding in self.opaque_resource_bindings.bindings() {
            let kind = match source_binding.kind() {
                TerrainSourceOpaqueResourceKind::CombinedTextureSampler => {
                    ResourceBindingKind::CombinedTextureSampler
                }
                TerrainSourceOpaqueResourceKind::StorageImage => {
                    ResourceBindingKind::StorageTexture
                }
            };
            pack_resource_bindings.push(ResourceBindingDesc {
                binding: source_binding.binding(),
                kind,
                stages: PipelineStageFlags::DRAW,
                array_count: 1,
                optional: false,
                dynamic_offset_count: 0,
            });
        }
        pack_resource_bindings.sort_by_key(|binding| binding.binding);
        validate_unique_layout_bindings("hand source pack resources", &pack_resource_bindings)?;
        Ok(TerrainSourceExecutionLayouts {
            source_data: ResourceLayoutDesc {
                label: format!("{}:source-data", self.identity.as_str()),
                bindings: source_data_bindings,
            },
            pack_resources: ResourceLayoutDesc {
                label: format!("{}:pack-resources", self.identity.as_str()),
                bindings: pack_resource_bindings,
            },
        })
    }

    pub fn require_semantic_resources(
        &self,
        availability: &TerrainSourceResourceAvailabilitySet,
    ) -> GalResult<()> {
        if availability.shader_pack_generation() != self.shader_pack_generation {
            return Err(GalError::invalid_argument(format!(
                "hand source program generation {} does not match resource availability generation {}",
                self.shader_pack_generation,
                availability.shader_pack_generation()
            )));
        }
        for binding in self.opaque_resource_bindings.bindings() {
            if availability.resource_for(binding.role()).is_none() {
                return Err(GalError::invalid_argument(format!(
                    "hand source resource '{}' is unavailable for semantic role '{}'",
                    binding.resource_name(),
                    binding.role().semantic_name()
                )));
            }
        }
        Ok(())
    }

    pub fn pack_resource_set_desc(
        &self,
        label: impl Into<String>,
        layout: Handle,
        resources: &TerrainSourceOwnedResourceSet,
    ) -> GalResult<ResourceSetDesc> {
        if layout.kind() != Some(HandleKind::ResourceLayout) {
            return Err(GalError::invalid_argument(
                "hand source pack resources require a GAL resource-layout handle",
            ));
        }
        self.execution_resource_layouts()?;
        self.require_semantic_resources(resources.availability())?;
        let mut bindings = Vec::with_capacity(self.opaque_resource_bindings.bindings().len());
        for source_binding in self.opaque_resource_bindings.bindings() {
            let (resource, kind, access) = match source_binding.kind() {
                TerrainSourceOpaqueResourceKind::CombinedTextureSampler => (
                    resources.combined_sampler_for(source_binding.role()).ok_or_else(|| {
                        GalError::invalid_argument(format!(
                            "hand source resource '{}' has no owned sampler for semantic role '{}'",
                            source_binding.resource_name(),
                            source_binding.role().semantic_name()
                        ))
                    })?,
                    ResourceBindingKind::CombinedTextureSampler,
                    AccessFlags::READ,
                ),
                TerrainSourceOpaqueResourceKind::StorageImage => (
                    resources.storage_texture_for(source_binding.role()).ok_or_else(|| {
                        GalError::invalid_argument(format!(
                            "hand source resource '{}' has no owned storage texture view for semantic role '{}'",
                            source_binding.resource_name(),
                            source_binding.role().semantic_name()
                        ))
                    })?,
                    ResourceBindingKind::StorageTexture,
                    source_storage_access(source_binding.qualifiers())?,
                ),
            };
            bindings.push(ResourceBinding {
                binding: source_binding.binding(),
                array_index: 0,
                resource,
                kind,
                access,
                dynamic_offsets: Vec::new(),
                buffer_range: None,
            });
        }
        Ok(ResourceSetDesc {
            label: label.into(),
            layout,
            bindings,
        })
    }

    pub fn named_output_color_slots(&self) -> &[(TerrainPassOutput, u32)] {
        &self.named_output_color_slots
    }

    pub fn contract(&self) -> &HandPassContract {
        &self.hand_contract
    }
}

impl LocalTexturedSourceProgram for LoweredHandSourceProgram {
    fn identity(&self) -> &ProgramIdentity {
        &self.identity
    }

    fn shader_pack_generation(&self) -> u64 {
        self.shader_pack_generation
    }

    fn execution_interface(&self) -> &TerrainSourceExecutionInterface {
        &self.execution_interface
    }

    fn shader_module_descriptors_with_alpha_cutoff(
        &self,
        conventions: ShaderConventions,
        alpha_cutoff: Option<f32>,
    ) -> [ShaderModuleDesc; 2] {
        LoweredHandSourceProgram::shader_module_descriptors_with_alpha_cutoff(
            self,
            conventions,
            alpha_cutoff,
        )
    }

    fn execution_resource_layouts(&self) -> GalResult<TerrainSourceExecutionLayouts> {
        LoweredHandSourceProgram::execution_resource_layouts(self)
    }

    fn require_semantic_resources(
        &self,
        availability: &TerrainSourceResourceAvailabilitySet,
    ) -> GalResult<()> {
        LoweredHandSourceProgram::require_semantic_resources(self, availability)
    }

    fn pack_resource_set_desc(
        &self,
        label: String,
        layout: Handle,
        resources: &TerrainSourceOwnedResourceSet,
    ) -> GalResult<ResourceSetDesc> {
        LoweredHandSourceProgram::pack_resource_set_desc(self, label, layout, resources)
    }
}

/// Prepares a selected first-person source program while keeping it separate
/// from the entity writer. Preparation proves that source code, semantic
/// resources, and the fixed owned stream ABI agree; it intentionally does
/// not allocate a pipeline, select a route, or create the hand pass.
pub fn prepare_lowered_hand_source_program(
    contract: &HandPassContract,
    lowered: &LoweredHandSourcePair,
    opaque_resource_bindings: &TerrainSourceOpaqueResourceBindingPlan,
) -> GalResult<LoweredHandSourceProgram> {
    if contract.generation == 0 {
        return Err(GalError::invalid_argument(
            "hand source preparation requires a non-zero shader-pack generation",
        ));
    }
    if contract.outputs.is_empty() || contract.outputs.len() != contract.output_color_slots.len() {
        return Err(GalError::invalid_argument(
            "hand source outputs and shader-pack slots must be non-empty and aligned",
        ));
    }
    lowered.require_backend_neutral_lowering()?;
    lowered.require_matching_opaque_resource_bindings(opaque_resource_bindings)?;
    let execution_interface = TerrainSourceExecutionInterface::from_lowered_hand_pair(lowered);
    execution_interface.validate()?;
    let scalar_uniform_requirements =
        TerrainSourceUniformRequirements::from_contract(lowered.uniform_contract())?;
    scalar_uniform_requirements.require_fully_semantic()?;
    let named_output_color_slots = contract
        .outputs
        .iter()
        .copied()
        .zip(contract.output_color_slots.iter().copied())
        .map(|(output, slot)| (hand_output_to_terrain_output(output), slot))
        .collect::<Vec<_>>();
    let program = LoweredHandSourceProgram {
        identity: ProgramIdentity::new(format!(
            "vulkanic:shader-pack/{}/hand_source_gen{}{}",
            contract.pack_name.to_ascii_lowercase(),
            contract.generation,
            scope_identity_tag(contract.scope)
        )),
        shader_pack_generation: contract.generation,
        hand_contract: contract.clone(),
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
        named_output_color_slots,
    };
    program.execution_resource_layouts()?;
    Ok(program)
}

pub(crate) fn hand_output_to_terrain_output(output: HandSourceOutput) -> TerrainPassOutput {
    match output {
        HandSourceOutput::LitColor => TerrainPassOutput::LitTerrainColor,
        HandSourceOutput::MaterialAuxiliary => TerrainPassOutput::MaterialAuxiliary,
        HandSourceOutput::ViewSpaceNormal => TerrainPassOutput::ViewSpaceNormal,
    }
}

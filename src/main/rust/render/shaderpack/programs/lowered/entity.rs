//! Lowered entity and entity-shadow source programs.

use super::*;

/// Prepared selected-source entity program. This owns source text and typed
/// semantic requirements only; it cannot compile a backend pipeline, bind a
/// native texture, or select a producer route. Keeping it distinct from
/// terrain prevents local entity textures and `entity.properties` IDs from
/// being mistaken for terrain-atlas semantics.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LoweredEntitySourceProgram {
    pub identity: ProgramIdentity,
    pub shader_pack_generation: u64,
    /// Exact source generation for the Rust-owned entity ID map.
    pub entity_id_generation: u64,
    /// The source-pack entity map remains Rust-owned with the prepared
    /// program. Frame preparation resolves copied canonical identities here;
    /// Java never transports a shader-pack numeric entity ID.
    pub(in crate::render::shaderpack::programs) entity_contract: EntityPassContract,
    pub vertex: ShaderStageSource,
    pub fragment: ShaderStageSource,
    pub execution_interface: TerrainSourceExecutionInterface,
    pub scalar_uniform_requirements: TerrainSourceUniformRequirements,
    pub opaque_resource_bindings: TerrainSourceOpaqueResourceBindingPlan,
    pub(in crate::render::shaderpack::programs) named_output_color_slots: Vec<(TerrainPassOutput, u32)>,
}

impl LoweredEntitySourceProgram {
    /// Converts retained entity source into explicit GAL shader descriptions.
    /// Backend compilation remains private to the backend and this does not
    /// allocate a pipeline or choose a rendering route.
    pub fn shader_module_descriptors(&self, conventions: ShaderConventions) -> [ShaderModuleDesc; 2] {
        [
            self.vertex.shader_module_descriptor(conventions),
            self.fragment.shader_module_descriptor(conventions),
        ]
    }

    /// Creates the source-derived entity stages for one explicit material
    /// alpha contract.  This is a semantic pipeline specialization, not a
    /// backend alpha-test state: Java's cutout policy is represented by the
    /// copied material mode and Rust supplies the corresponding shader hook.
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

    /// Packs only source-declared entity uniforms through the typed semantic
    /// catalog. The caller must supply resolved `entityId` and `entityColor`;
    /// arbitrary bytes and Java/Iris uniform state are rejected by design.
    pub fn pack_scalar_uniforms(&self, frame: &TerrainSourceUniformFrame) -> GalResult<Vec<u8>> {
        let bytes = frame.pack_std140(&self.scalar_uniform_requirements)?;
        if bytes.len() != self.execution_interface.scalar_uniform_bytes as usize {
            return Err(GalError::invalid_argument(format!(
                "entity source scalar uniform pack is {} bytes but program ABI requires {}",
                bytes.len(),
                self.execution_interface.scalar_uniform_bytes
            )));
        }
        Ok(bytes)
    }

    /// Packs the fixed source texture/lightmap transform block used by the
    /// owned entity stream. Entity-local UVs use the identity atlas lane;
    /// packed light coordinates retain the same explicit Minecraft semantic
    /// conversion as terrain. No Java/Iris uniform block is borrowed.
    pub fn pack_legacy_texture_transforms(
        &self,
        transforms: &TerrainSourceTextureTransforms,
    ) -> GalResult<Vec<u8>> {
        self.execution_interface.validate()?;
        transforms.validate()?;
        let mut bytes =
            Vec::with_capacity(self.execution_interface.legacy_transform_bytes as usize);
        for value in transforms
            .atlas_texture_matrix
            .iter()
            .chain(transforms.lightmap_texture_matrix.iter())
        {
            bytes.extend_from_slice(&value.to_ne_bytes());
        }
        if bytes.len() != self.execution_interface.legacy_transform_bytes as usize {
            return Err(GalError::invalid_argument(
                "entity source legacy texture transform pack does not match its fixed ABI size",
            ));
        }
        Ok(bytes)
    }

    /// Derives the closed GAL layout contract for one Rust-owned entity
    /// writer. It uses the same fixed source stream ABI as terrain, but its
    /// set-one roles remain entity-local (not terrain-atlas aliases).
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
        validate_unique_layout_bindings("entity source data", &source_data_bindings)?;

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
        validate_unique_layout_bindings("entity source pack resources", &pack_resource_bindings)?;

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

    /// Validates that every selected-pack entity resource is owned by Rust
    /// for the same source generation. This is semantic availability only;
    /// no descriptor set or backend object is created here.
    pub fn require_semantic_resources(
        &self,
        availability: &TerrainSourceResourceAvailabilitySet,
    ) -> GalResult<()> {
        if availability.shader_pack_generation() != self.shader_pack_generation {
            return Err(GalError::invalid_argument(format!(
                "entity source program generation {} does not match resource availability generation {}",
                self.shader_pack_generation,
                availability.shader_pack_generation()
            )));
        }
        for binding in self.opaque_resource_bindings.bindings() {
            if availability.resource_for(binding.role()).is_none() {
                return Err(GalError::invalid_argument(format!(
                    "entity source resource '{}' is unavailable for semantic role '{}'",
                    binding.resource_name(),
                    binding.role().semantic_name()
                )));
            }
        }
        Ok(())
    }

    /// Builds the explicit semantic set-one description for a future owned
    /// entity pass. The caller must provide a local material resource from
    /// Rust's cache; Java/Iris bindings cannot enter this boundary.
    pub fn pack_resource_set_desc(
        &self,
        label: impl Into<String>,
        layout: Handle,
        resources: &TerrainSourceOwnedResourceSet,
    ) -> GalResult<ResourceSetDesc> {
        if layout.kind() != Some(HandleKind::ResourceLayout) {
            return Err(GalError::invalid_argument(
                "entity source pack resources require a GAL resource-layout handle",
            ));
        }
        self.execution_resource_layouts()?;
        self.require_semantic_resources(resources.availability())?;
        let mut bindings = Vec::with_capacity(self.opaque_resource_bindings.bindings().len());
        for source_binding in self.opaque_resource_bindings.bindings() {
            let (resource, kind, access) = match source_binding.kind() {
                TerrainSourceOpaqueResourceKind::CombinedTextureSampler => (
                    resources
                        .combined_sampler_for(source_binding.role())
                        .ok_or_else(|| {
                            GalError::invalid_argument(format!(
                                "entity source resource '{}' has no owned sampler for semantic role '{}'",
                                source_binding.resource_name(),
                                source_binding.role().semantic_name()
                            ))
                        })?,
                    ResourceBindingKind::CombinedTextureSampler,
                    AccessFlags::READ,
                ),
                TerrainSourceOpaqueResourceKind::StorageImage => (
                    resources
                        .storage_texture_for(source_binding.role())
                        .ok_or_else(|| {
                            GalError::invalid_argument(format!(
                                "entity source resource '{}' has no owned storage texture view for semantic role '{}'",
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

    /// Resolves one copied semantic entity identity through the immutable
    /// source generation retained by this program. This is CPU-only semantic
    /// preparation, not a backend binding or uniform-location lookup.
    pub fn resolve_draw_semantics(
        &self,
        entity_identity: &str,
        entity_color_argb: u32,
    ) -> GalResult<EntitySourceDrawSemantics> {
        let semantics = self
            .entity_contract
            .resolve_draw_semantics(entity_identity, entity_color_argb)?;
        if semantics.entity_id_generation != self.entity_id_generation {
            return Err(GalError::invalid_argument(
                "entity source program resolved a stale entity-id generation",
            ));
        }
        Ok(semantics)
    }

    /// Named source outputs retain their shader-pack slot metadata without
    /// exposing attachment indices or backend framebuffer state.
    pub fn named_output_color_slots(&self) -> &[(TerrainPassOutput, u32)] {
        &self.named_output_color_slots
    }
}

impl LocalTexturedSourceProgram for LoweredEntitySourceProgram {
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
        LoweredEntitySourceProgram::shader_module_descriptors_with_alpha_cutoff(
            self,
            conventions,
            alpha_cutoff,
        )
    }

    fn execution_resource_layouts(&self) -> GalResult<TerrainSourceExecutionLayouts> {
        LoweredEntitySourceProgram::execution_resource_layouts(self)
    }

    fn require_semantic_resources(
        &self,
        availability: &TerrainSourceResourceAvailabilitySet,
    ) -> GalResult<()> {
        LoweredEntitySourceProgram::require_semantic_resources(self, availability)
    }

    fn pack_resource_set_desc(
        &self,
        label: String,
        layout: Handle,
        resources: &TerrainSourceOwnedResourceSet,
    ) -> GalResult<ResourceSetDesc> {
        LoweredEntitySourceProgram::pack_resource_set_desc(self, label, layout, resources)
    }
}

/// Prepares one selected ordinary-entity source program after its source
/// contract, Rust-owned entity-ID generation, lowered shader pair, and local
/// material resource plan agree. It intentionally stops before pipeline,
/// target, geometry stream, and draw construction; no compatibility route can
/// mistake this preparation artifact for executed entity work.
pub fn prepare_lowered_entity_source_program(
    contract: &EntityPassContract,
    lowered: &LoweredEntitySourcePair,
    opaque_resource_bindings: &TerrainSourceOpaqueResourceBindingPlan,
) -> GalResult<LoweredEntitySourceProgram> {
    if contract.generation == 0 || contract.entity_id_generation() == 0 {
        return Err(GalError::invalid_argument(
            "entity source preparation requires non-zero shader and entity-id generations",
        ));
    }
    if contract.outputs.is_empty() || contract.outputs.len() != contract.output_color_slots.len() {
        return Err(GalError::invalid_argument(
            "entity source outputs and shader-pack slots must be non-empty and aligned",
        ));
    }
    lowered.require_backend_neutral_lowering()?;
    lowered.require_matching_opaque_resource_bindings(opaque_resource_bindings)?;
    let execution_interface = TerrainSourceExecutionInterface::from_lowered_entity_pair(lowered);
    execution_interface.validate()?;
    let scalar_uniform_requirements =
        TerrainSourceUniformRequirements::from_contract(lowered.uniform_contract())?;
    scalar_uniform_requirements.require_fully_semantic()?;
    let named_output_color_slots = contract
        .outputs
        .iter()
        .copied()
        .zip(contract.output_color_slots.iter().copied())
        .map(|(output, slot)| (entity_output_to_terrain_output(output), slot))
        .collect::<Vec<_>>();
    let program = LoweredEntitySourceProgram {
        identity: ProgramIdentity::new(format!(
            "vulkanic:shader-pack/{}/entity_source_gen{}{}",
            contract.pack_name.to_ascii_lowercase(),
            contract.generation,
            scope_identity_tag(contract.scope)
        )),
        shader_pack_generation: contract.generation,
        entity_id_generation: contract.entity_id_generation(),
        entity_contract: contract.clone(),
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

/// Prepares the entity-stream shadow caster program. It shares the entity
/// program ABI (per-draw entity texture, owned indexed stream) but has its own
/// identity so its shadow-target pipelines never alias `gbuffers_entities`.
pub fn prepare_lowered_entity_shadow_source_program(
    contract: &EntityPassContract,
    lowered: &LoweredEntitySourcePair,
    opaque_resource_bindings: &TerrainSourceOpaqueResourceBindingPlan,
) -> GalResult<LoweredEntitySourceProgram> {
    let mut program =
        prepare_lowered_entity_source_program(contract, lowered, opaque_resource_bindings)?;
    program.identity = ProgramIdentity::new(format!(
        "vulkanic:shader-pack/{}/entity_shadow_source_gen{}{}",
        contract.pack_name.to_ascii_lowercase(),
        contract.generation,
            scope_identity_tag(contract.scope)
    ));
    Ok(program)
}

pub(crate) fn entity_output_to_terrain_output(output: EntitySourceOutput) -> TerrainPassOutput {
    match output {
        EntitySourceOutput::LitColor => TerrainPassOutput::LitTerrainColor,
        EntitySourceOutput::MaterialAuxiliary => TerrainPassOutput::MaterialAuxiliary,
        EntitySourceOutput::ViewSpaceNormal => TerrainPassOutput::ViewSpaceNormal,
    }
}

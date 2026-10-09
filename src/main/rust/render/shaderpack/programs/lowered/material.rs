//! Lowered textured-material, weather, cloud, line and damaged-block source programs.

use super::*;

/// Shared typed contract for Rust-owned indexed streams with a local material
/// texture. Entity and hand passes intentionally retain distinct source
/// contracts and pass ownership, but their frontend resource preparation uses
/// the same fixed ABI and semantic resource-set rules.
///
/// This is not a backend abstraction: it exposes only source-derived shader
/// metadata and typed GAL descriptions. The frontend still owns cache keys,
/// uploads, and pass scheduling.
pub trait LocalTexturedSourceProgram {
    fn identity(&self) -> &ProgramIdentity;
    fn shader_pack_generation(&self) -> u64;
    fn execution_interface(&self) -> &TerrainSourceExecutionInterface;
    fn shader_module_descriptors_with_alpha_cutoff(
        &self,
        conventions: ShaderConventions,
        alpha_cutoff: Option<f32>,
    ) -> [ShaderModuleDesc; 2];
    fn execution_resource_layouts(&self) -> GalResult<TerrainSourceExecutionLayouts>;
    fn require_semantic_resources(
        &self,
        availability: &TerrainSourceResourceAvailabilitySet,
    ) -> GalResult<()>;
    fn pack_resource_set_desc(
        &self,
        label: String,
        layout: Handle,
        resources: &TerrainSourceOwnedResourceSet,
    ) -> GalResult<ResourceSetDesc>;
}

/// Source-derived program preparation for generic textured world material.
/// It retains the selected source's named outputs and semantic resource plan,
/// but has no executable vertex-stream/pipeline allocation yet. That keeps
/// the legacy post-final overlay from being mistaken for shader-pack
/// participation while a dedicated Rust-owned material writer is staged.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LoweredTexturedMaterialSourceProgram {
    pub identity: ProgramIdentity,
    pub shader_pack_generation: u64,
    pub vertex: ShaderStageSource,
    pub fragment: ShaderStageSource,
    /// Fixed source-material stream contract. It has no terrain-only lanes
    /// and no per-instance transform table because its vertices are explicit
    /// camera-relative semantic positions.
    pub execution_interface: TexturedMaterialSourceExecutionInterface,
    pub scalar_uniform_requirements: TerrainSourceUniformRequirements,
    pub opaque_resource_bindings: TerrainSourceOpaqueResourceBindingPlan,
    pub(in crate::render::shaderpack::programs) named_output_color_slots: Vec<(TerrainPassOutput, u32)>,
}

/// Prepared selected-source weather program. It owns no target, pipeline, or
/// route; the runtime must later provide a dedicated named weather pass.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LoweredWeatherSourceProgram {
    pub identity: ProgramIdentity,
    pub shader_pack_generation: u64,
    pub vertex: ShaderStageSource,
    pub fragment: ShaderStageSource,
    pub execution_interface: TexturedMaterialSourceExecutionInterface,
    pub scalar_uniform_requirements: TerrainSourceUniformRequirements,
    pub opaque_resource_bindings: TerrainSourceOpaqueResourceBindingPlan,
    pub lit_color_output_slot: u8,
    pub alpha_discard_threshold_bits: u32,
    pub blend: WeatherBlend,
}

/// Prepared selected-source vanilla-cloud program. It owns no target,
/// pipeline, or route; a future cloud writer must provide an explicit named
/// pass and fully semantic resource availability.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LoweredCloudSourceProgram {
    pub identity: ProgramIdentity,
    pub shader_pack_generation: u64,
    pub vertex: ShaderStageSource,
    pub fragment: ShaderStageSource,
    pub execution_interface: TexturedMaterialSourceExecutionInterface,
    pub scalar_uniform_requirements: TerrainSourceUniformRequirements,
    pub opaque_resource_bindings: TerrainSourceOpaqueResourceBindingPlan,
    pub blend: CloudBlend,
    pub(in crate::render::shaderpack::programs) named_output_color_slots: Vec<(TerrainPassOutput, u32)>,
}

/// Backend-neutral layouts for the compact source-material stream. Set zero
/// contains only Rust-owned vertex/transforms/scalar data; set one contains
/// the selected pack's pass-local semantic resources.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TexturedMaterialSourceExecutionLayouts {
    pub source_data: ResourceLayoutDesc,
    pub pack_resources: ResourceLayoutDesc,
}

/// Fixed stream ABI for a source-derived generic textured-material pass.
/// This explicitly models the data `gbuffers_textured` may consume and keeps
/// it distinct from terrain's material/entity/tangent stream.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TexturedMaterialSourceExecutionInterface {
    pub vertex_stream: TerrainSourceFixedBinding,
    pub vertex_stride: u32,
    pub vertex_fields: [TerrainSourceVertexField; 4],
    pub legacy_transforms: TerrainSourceFixedBinding,
    pub scalar_uniforms: Option<TerrainSourceFixedBinding>,
    pub legacy_transform_bytes: u32,
    pub scalar_uniform_bytes: u32,
    pub scalar_uniform_fields: Vec<TerrainSourceUniformField>,
}

impl LoweredTexturedMaterialSourceProgram {
    /// Exact named shader-pack outputs produced by this source stage. These
    /// remain semantic target roles; neither a GLSL output location nor a
    /// backend attachment index escapes program preparation.
    pub fn named_output_color_slots(&self) -> &[(TerrainPassOutput, u32)] {
        &self.named_output_color_slots
    }

    pub fn shader_module_descriptors(&self, conventions: ShaderConventions) -> [ShaderModuleDesc; 2] {
        [
            self.vertex.shader_module_descriptor(conventions),
            self.fragment.shader_module_descriptor(conventions),
        ]
    }

    /// Builds the bounded `gbuffers_textured` source stream. The selected
    /// program declares one semantic base-color sampler; the frontend binds
    /// either the copied terrain atlas or one Rust-owned local material asset
    /// for a compatible batch. No backend object or Java texture leaks into
    /// that selection.
    pub fn pack_material_primitives(
        &self,
        primitives: &[TexturedMaterialSourcePrimitive],
    ) -> GalResult<Vec<u8>> {
        self.execution_interface.validate()?;
        if !self
            .opaque_resource_bindings
            .bindings()
            .iter()
            .any(|binding| matches!(binding.role(), TerrainSourceResourceRole::MaterialAtlas | TerrainSourceResourceRole::MaterialTexture))
        {
            return Err(GalError::unsupported_feature(
                "textured material source program has no declared base-color sampler",
            ));
        }
        pack_textured_material_source_primitives(primitives)
    }

    pub fn pack_scalar_uniforms(&self, frame: &TerrainSourceUniformFrame) -> GalResult<Vec<u8>> {
        self.execution_interface.validate()?;
        let bytes = frame.pack_std140(&self.scalar_uniform_requirements)?;
        if bytes.len() != self.execution_interface.scalar_uniform_bytes as usize {
            return Err(GalError::invalid_argument(format!(
                "textured material source scalar uniform pack is {} bytes but program ABI requires {}",
                bytes.len(), self.execution_interface.scalar_uniform_bytes
            )));
        }
        Ok(bytes)
    }

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
                "textured material source texture transforms do not match their fixed ABI size",
            ));
        }
        Ok(bytes)
    }

    pub fn execution_resource_layouts(&self) -> GalResult<TexturedMaterialSourceExecutionLayouts> {
        self.execution_interface.validate()?;
        let mut source_data_bindings = vec![
            resource_binding_descriptor(self.execution_interface.vertex_stream, true),
            resource_binding_descriptor(self.execution_interface.legacy_transforms, true),
        ];
        if let Some(scalar_uniforms) = self.execution_interface.scalar_uniforms {
            source_data_bindings.push(resource_binding_descriptor(scalar_uniforms, true));
        }
        source_data_bindings.sort_by_key(|binding| binding.binding);
        validate_unique_layout_bindings("textured material source data", &source_data_bindings)?;
        Ok(TexturedMaterialSourceExecutionLayouts {
            source_data: ResourceLayoutDesc {
                label: format!("{}:source-data", self.identity.as_str()),
                bindings: source_data_bindings,
            },
            pack_resources: source_pack_resource_layout(
                format!("{}:pack-resources", self.identity.as_str()),
                &self.opaque_resource_bindings,
            )?,
        })
    }

    pub fn require_semantic_resources(
        &self,
        availability: &TerrainSourceResourceAvailabilitySet,
    ) -> GalResult<()> {
        if availability.shader_pack_generation() != self.shader_pack_generation {
            return Err(GalError::invalid_argument(format!(
                "textured material source generation {} does not match resource generation {}",
                self.shader_pack_generation,
                availability.shader_pack_generation()
            )));
        }
        for binding in self.opaque_resource_bindings.bindings() {
            if availability.resource_for(binding.role()).is_none() {
                return Err(GalError::invalid_argument(format!(
                    "textured material source resource '{}' is unavailable for semantic role '{}'",
                    binding.resource_name(),
                    binding.role().semantic_name()
                )));
            }
        }
        Ok(())
    }

    /// Materializes the selected source's set-one semantic resource plan for
    /// the compact textured-material writer. Set zero remains a distinct
    /// Rust-owned quad stream; this method deliberately shares only the
    /// backend-neutral resource-role contract with terrain.
    pub fn pack_resource_set_desc(
        &self,
        label: impl Into<String>,
        layout: Handle,
        resources: &TerrainSourceOwnedResourceSet,
    ) -> GalResult<ResourceSetDesc> {
        if layout.kind() != Some(HandleKind::ResourceLayout) {
            return Err(GalError::invalid_argument(
                "textured material source pack resources require a GAL resource-layout handle",
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
                                "textured material source resource '{}' has no owned sampler for semantic role '{}'",
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
                                "textured material source resource '{}' has no owned storage texture view for semantic role '{}'",
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
}

impl LoweredWeatherSourceProgram {
    /// Adapts the weather program to the shared compact world-material stream.
    /// The adapter preserves the weather program identity, shader source,
    /// named output, and semantic bindings; it merely avoids a second copy of
    /// the Rust-owned quad transport and resource-set machinery.
    pub fn material_stream_program(&self) -> LoweredTexturedMaterialSourceProgram {
        LoweredTexturedMaterialSourceProgram {
            identity: self.identity.clone(),
            shader_pack_generation: self.shader_pack_generation,
            vertex: self.vertex.clone(),
            fragment: self.fragment.clone(),
            execution_interface: self.execution_interface.clone(),
            scalar_uniform_requirements: self.scalar_uniform_requirements.clone(),
            opaque_resource_bindings: self.opaque_resource_bindings.clone(),
            named_output_color_slots: vec![(
                TerrainPassOutput::LitTerrainColor,
                u32::from(self.lit_color_output_slot),
            )],
        }
    }

    pub fn shader_module_descriptors(&self, conventions: ShaderConventions) -> [ShaderModuleDesc; 2] {
        [
            self.vertex.shader_module_descriptor(conventions),
            self.fragment.shader_module_descriptor(conventions),
        ]
    }

    pub fn alpha_discard_threshold(&self) -> f32 {
        f32::from_bits(self.alpha_discard_threshold_bits)
    }

    pub fn execution_resource_layouts(&self) -> GalResult<TexturedMaterialSourceExecutionLayouts> {
        self.execution_interface.validate()?;
        let mut source_data_bindings = vec![
            resource_binding_descriptor(self.execution_interface.vertex_stream, true),
            resource_binding_descriptor(self.execution_interface.legacy_transforms, true),
        ];
        if let Some(scalar_uniforms) = self.execution_interface.scalar_uniforms {
            source_data_bindings.push(resource_binding_descriptor(scalar_uniforms, true));
        }
        source_data_bindings.sort_by_key(|binding| binding.binding);
        validate_unique_layout_bindings("weather source data", &source_data_bindings)?;
        Ok(TexturedMaterialSourceExecutionLayouts {
            source_data: ResourceLayoutDesc {
                label: format!("{}:source-data", self.identity.as_str()),
                bindings: source_data_bindings,
            },
            pack_resources: source_pack_resource_layout(
                format!("{}:pack-resources", self.identity.as_str()),
                &self.opaque_resource_bindings,
            )?,
        })
    }

    pub fn require_semantic_resources(
        &self,
        availability: &TerrainSourceResourceAvailabilitySet,
    ) -> GalResult<()> {
        if availability.shader_pack_generation() != self.shader_pack_generation {
            return Err(GalError::invalid_argument(format!(
                "weather source generation {} does not match resource generation {}",
                self.shader_pack_generation,
                availability.shader_pack_generation()
            )));
        }
        for binding in self.opaque_resource_bindings.bindings() {
            if availability.resource_for(binding.role()).is_none() {
                return Err(GalError::invalid_argument(format!(
                    "weather source resource '{}' is unavailable for semantic role '{}'",
                    binding.resource_name(),
                    binding.role().semantic_name()
                )));
            }
        }
        Ok(())
    }
}

impl LoweredCloudSourceProgram {
    /// Adapts the cloud stage to the shared compact material stream while
    /// preserving cloud's separate source identity and named outputs.
    pub fn material_stream_program(&self) -> LoweredTexturedMaterialSourceProgram {
        LoweredTexturedMaterialSourceProgram {
            identity: self.identity.clone(),
            shader_pack_generation: self.shader_pack_generation,
            vertex: self.vertex.clone(),
            fragment: self.fragment.clone(),
            execution_interface: self.execution_interface.clone(),
            scalar_uniform_requirements: self.scalar_uniform_requirements.clone(),
            opaque_resource_bindings: self.opaque_resource_bindings.clone(),
            named_output_color_slots: self.named_output_color_slots.clone(),
        }
    }

    pub fn shader_module_descriptors(&self, conventions: ShaderConventions) -> [ShaderModuleDesc; 2] {
        [
            self.vertex.shader_module_descriptor(conventions),
            self.fragment.shader_module_descriptor(conventions),
        ]
    }

    pub fn named_output_color_slots(&self) -> &[(TerrainPassOutput, u32)] {
        &self.named_output_color_slots
    }

    pub fn execution_resource_layouts(&self) -> GalResult<TexturedMaterialSourceExecutionLayouts> {
        self.execution_interface.validate()?;
        let mut source_data_bindings = vec![
            resource_binding_descriptor(self.execution_interface.vertex_stream, true),
            resource_binding_descriptor(self.execution_interface.legacy_transforms, true),
        ];
        if let Some(scalar_uniforms) = self.execution_interface.scalar_uniforms {
            source_data_bindings.push(resource_binding_descriptor(scalar_uniforms, true));
        }
        source_data_bindings.sort_by_key(|binding| binding.binding);
        validate_unique_layout_bindings("cloud source data", &source_data_bindings)?;
        Ok(TexturedMaterialSourceExecutionLayouts {
            source_data: ResourceLayoutDesc {
                label: format!("{}:source-data", self.identity.as_str()),
                bindings: source_data_bindings,
            },
            pack_resources: source_pack_resource_layout(
                format!("{}:pack-resources", self.identity.as_str()),
                &self.opaque_resource_bindings,
            )?,
        })
    }

    pub fn require_semantic_resources(
        &self,
        availability: &TerrainSourceResourceAvailabilitySet,
    ) -> GalResult<()> {
        if availability.shader_pack_generation() != self.shader_pack_generation {
            return Err(GalError::invalid_argument(format!(
                "cloud source generation {} does not match resource generation {}",
                self.shader_pack_generation,
                availability.shader_pack_generation()
            )));
        }
        for binding in self.opaque_resource_bindings.bindings() {
            if availability.resource_for(binding.role()).is_none() {
                return Err(GalError::invalid_argument(format!(
                    "cloud source resource '{}' is unavailable for semantic role '{}'",
                    binding.resource_name(),
                    binding.role().semantic_name()
                )));
            }
        }
        Ok(())
    }
}

impl TexturedMaterialSourceExecutionInterface {
    pub(crate) const VERTEX_STREAM: TerrainSourceFixedBinding = TerrainSourceFixedBinding {
        set: 0,
        binding: 0,
        kind: TerrainSourceBindingKind::StorageBuffer,
    };

    pub(crate) const LEGACY_TRANSFORMS: TerrainSourceFixedBinding = TerrainSourceFixedBinding {
        set: 0,
        binding: 1,
        kind: TerrainSourceBindingKind::UniformBuffer,
    };

    pub(crate) const SCALAR_UNIFORMS: TerrainSourceFixedBinding = TerrainSourceFixedBinding {
        set: 0,
        binding: 2,
        kind: TerrainSourceBindingKind::UniformBuffer,
    };

    pub(crate) fn from_uniform_contract(contract: &crate::render::shaderpack::lowering::TerrainSourceUniformContract) -> Self {
        Self {
            vertex_stream: Self::VERTEX_STREAM,
            vertex_stride: TEXTURED_MATERIAL_SOURCE_VERTEX_BYTES as u32,
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
                    name: "texture_uv_lightmap",
                    offset: 48,
                    component_count: 4,
                },
            ],
            legacy_transforms: Self::LEGACY_TRANSFORMS,
            scalar_uniforms: (!contract.fields().is_empty()).then_some(Self::SCALAR_UNIFORMS),
            legacy_transform_bytes: 2 * 16 * std::mem::size_of::<f32>() as u32,
            scalar_uniform_bytes: contract.std140_size(),
            scalar_uniform_fields: contract.fields().to_vec(),
        }
    }

    pub(crate) fn from_lowered_pair(lowered: &LoweredTexturedMaterialSourcePair) -> Self {
        Self::from_uniform_contract(lowered.uniform_contract())
    }

    pub fn validate(&self) -> GalResult<()> {
        const EXPECTED_FIELDS: [(&str, u32); 4] = [
            ("position", 0),
            ("color", 16),
            ("normal_light", 32),
            ("texture_uv_lightmap", 48),
        ];
        if self.vertex_stream != Self::VERTEX_STREAM
            || self.vertex_stride != TEXTURED_MATERIAL_SOURCE_VERTEX_BYTES as u32
        {
            return Err(GalError::invalid_argument(
                "textured material source must use its fixed set 0 binding 0 64-byte storage stream",
            ));
        }
        for (field, (name, offset)) in self.vertex_fields.iter().zip(EXPECTED_FIELDS) {
            if field.name != name || field.offset != offset || field.component_count != 4 {
                return Err(GalError::invalid_argument(format!(
                    "textured material source field '{}' is not the fixed {} vec4 lane at offset {}",
                    field.name, name, offset
                )));
            }
        }
        if self.legacy_transforms != Self::LEGACY_TRANSFORMS || self.legacy_transform_bytes != 128 {
            return Err(GalError::invalid_argument(
                "textured material source transforms must use fixed set 0 binding 1 with two std140 mat4 values",
            ));
        }
        validate_source_scalar_uniform_block(
            self.scalar_uniforms,
            self.scalar_uniform_bytes,
            &self.scalar_uniform_fields,
            Self::SCALAR_UNIFORMS,
            "textured material source",
        )
    }
}

/// Prepares one source-derived `gbuffers_textured` program only after its
/// shader-pack generation, source lowering, scalar uniforms, and pass-local
/// semantic resource plan agree. This intentionally stops before pipeline and
/// stream construction: the Rust-owned material stream and named-target pass
/// are constructed by the world frontend rather than reusing the final-output
/// overlay path.
pub fn prepare_lowered_textured_material_source_program(
    contract: &TexturedMaterialPassContract,
    lowered: &LoweredTexturedMaterialSourcePair,
    opaque_resource_bindings: &TerrainSourceOpaqueResourceBindingPlan,
) -> GalResult<LoweredTexturedMaterialSourceProgram> {
    if contract.generation == 0 {
        return Err(GalError::invalid_argument(
            "textured material source preparation requires a non-zero shader-pack generation",
        ));
    }
    if contract.outputs.len() != contract.output_color_slots.len() || contract.outputs.is_empty() {
        return Err(GalError::invalid_argument(
            "textured material source outputs and shader-pack slots must be non-empty and aligned",
        ));
    }
    lowered.require_backend_neutral_lowering()?;
    lowered.require_matching_opaque_resource_bindings(opaque_resource_bindings)?;
    let execution_interface = TexturedMaterialSourceExecutionInterface::from_lowered_pair(lowered);
    execution_interface.validate()?;
    let scalar_uniform_requirements =
        TerrainSourceUniformRequirements::from_contract(lowered.uniform_contract())?;
    scalar_uniform_requirements.require_fully_semantic()?;
    let named_output_color_slots = contract
        .outputs
        .iter()
        .copied()
        .zip(contract.output_color_slots.iter().copied())
        .map(|(output, slot)| (output.terrain_output(), slot))
        .collect::<Vec<_>>();
    if named_output_color_slots
        .iter()
        .map(|(output, _)| output)
        .collect::<BTreeSet<_>>()
        .len()
        != named_output_color_slots.len()
    {
        return Err(GalError::invalid_argument(
            "textured material source maps multiple outputs to one semantic named target",
        ));
    }
    // The shared colored-texture stream includes ordinary particles. Iris
    // tests their primary output even when the producer uses opaque blending;
    // alpha-zero texels must preserve the existing scene and depth.
    let fragment_source = match contract.alpha_cutoff_bits.map(f32::from_bits) {
        None => lowered.fragment().source().to_string(),
        Some(cutoff) => {
            if !cutoff.is_finite() || !(0.0..=1.0).contains(&cutoff) {
                return Err(GalError::invalid_argument(
                    "textured material alpha cutoff must be finite and in [0, 1]",
                ));
            }
            let renamed = crate::render::shaderpack::lowering::rename_glsl_main(&lowered.fragment().source(), "vulkanic_source_textured_main")?;
            format!(
                "{renamed}\nvoid main() {{\n    vulkanic_source_textured_main();\n    if (!(out_textured_material_lit_color.a > {cutoff:?})) discard;\n}}\n"
            )
        }
    };
    let program = LoweredTexturedMaterialSourceProgram {
        identity: ProgramIdentity::new(format!(
            "vulkanic:shader-pack/{}/textured_material_source_gen{}{}",
            contract.pack_name.to_ascii_lowercase(),
            contract.generation,
            scope_identity_tag(contract.scope)
        )),
        shader_pack_generation: contract.generation,
        vertex: ShaderStageSource {
            stage: ShaderStageKind::Vertex,
            label: format!("{}:lowered-vertex", lowered.vertex().entry_path()),
            source: lowered.vertex().source().to_string(),
            entry_point: "main".to_string(),
        },
        fragment: ShaderStageSource {
            stage: ShaderStageKind::Fragment,
            label: format!("{}:lowered-fragment", lowered.fragment().entry_path()),
            source: fragment_source,
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

/// Prepares the independently selected weather source. This deliberately
/// stops before target creation and draw recording, so preparation cannot
/// select a mixed Java/Rust weather route.
pub fn prepare_lowered_weather_source_program(
    contract: &WeatherPassContract,
    lowered: &LoweredWeatherSourcePair,
    opaque_resource_bindings: &TerrainSourceOpaqueResourceBindingPlan,
) -> GalResult<LoweredWeatherSourceProgram> {
    if contract.generation == 0 || contract.lit_color_output_slot != 0 {
        return Err(GalError::invalid_argument(
            "weather source preparation requires a non-zero generation and named lit-color slot zero",
        ));
    }
    lowered.require_backend_neutral_lowering()?;
    lowered.require_matching_opaque_resource_bindings(opaque_resource_bindings)?;
    let execution_interface =
        TexturedMaterialSourceExecutionInterface::from_uniform_contract(lowered.uniform_contract());
    execution_interface.validate()?;
    let scalar_uniform_requirements =
        TerrainSourceUniformRequirements::from_contract(lowered.uniform_contract())?;
    scalar_uniform_requirements.require_fully_semantic()?;
    let program = LoweredWeatherSourceProgram {
        identity: ProgramIdentity::new(format!(
            "vulkanic:shader-pack/{}/weather_source_gen{}{}",
            contract.pack_name.to_ascii_lowercase(),
            contract.generation,
            scope_identity_tag(contract.scope)
        )),
        shader_pack_generation: contract.generation,
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
        lit_color_output_slot: contract.lit_color_output_slot,
        alpha_discard_threshold_bits: contract.alpha_discard_threshold_bits,
        blend: contract.blend,
    };
    program.execution_resource_layouts()?;
    Ok(program)
}

/// Prepares the selected vanilla-cloud source without creating a target,
/// pipeline, or route. The dedicated Rust-owned cloud writer remains a later
/// transaction that must prove its resources and named targets explicitly.
/// Prepares the selected block-selection line source on the compact material
/// stream. It has no sampled base color, so it never passes through the
/// textured-material primitive packer that requires an atlas binding.
pub fn prepare_lowered_line_source_program(
    contract: &crate::render::shaderpack::contracts::line::LinePassContract,
    lowered: &LoweredCloudSourcePair,
    opaque_resource_bindings: &TerrainSourceOpaqueResourceBindingPlan,
) -> GalResult<LoweredTexturedMaterialSourceProgram> {
    if contract.generation == 0
        || contract.outputs.is_empty()
        || contract.outputs.len() != contract.output_color_slots.len()
    {
        return Err(GalError::invalid_argument(
            "line source preparation requires a non-zero generation and paired named outputs",
        ));
    }
    lowered.require_backend_neutral_lowering()?;
    lowered.require_matching_opaque_resource_bindings(opaque_resource_bindings)?;
    let execution_interface =
        TexturedMaterialSourceExecutionInterface::from_uniform_contract(lowered.uniform_contract());
    execution_interface.validate()?;
    let scalar_uniform_requirements =
        TerrainSourceUniformRequirements::from_contract(lowered.uniform_contract())?;
    scalar_uniform_requirements.require_fully_semantic()?;
    let named_output_color_slots = contract
        .outputs
        .iter()
        .copied()
        .zip(contract.output_color_slots.iter().copied())
        .map(|(output, slot)| (output.terrain_output(), slot))
        .collect::<Vec<_>>();
    let program = LoweredTexturedMaterialSourceProgram {
        identity: ProgramIdentity::new(format!(
            "vulkanic:shader-pack/{}/line_source_gen{}{}",
            contract.pack_name.to_ascii_lowercase(),
            contract.generation,
            scope_identity_tag(contract.scope)
        )),
        shader_pack_generation: contract.generation,
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

/// Prepares the selected `gbuffers_damagedblock` stage for the compact
/// material stream. Iris applies the program's alpha test to the primary
/// output after the pack fragment runs; that rule is folded into the
/// source specialization so the pack's own fragment logic stays intact.
pub fn prepare_lowered_damaged_block_source_program(
    contract: &crate::render::shaderpack::contracts::damaged_block::DamagedBlockPassContract,
    lowered: &LoweredCloudSourcePair,
    opaque_resource_bindings: &TerrainSourceOpaqueResourceBindingPlan,
) -> GalResult<LoweredTexturedMaterialSourceProgram> {
    if contract.generation == 0
        || contract.outputs.is_empty()
        || contract.outputs.len() != contract.output_color_slots.len()
    {
        return Err(GalError::invalid_argument(
            "damagedblock source preparation requires a non-zero generation and paired named outputs",
        ));
    }
    lowered.require_backend_neutral_lowering()?;
    lowered.require_matching_opaque_resource_bindings(opaque_resource_bindings)?;
    let execution_interface =
        TexturedMaterialSourceExecutionInterface::from_uniform_contract(lowered.uniform_contract());
    execution_interface.validate()?;
    let scalar_uniform_requirements =
        TerrainSourceUniformRequirements::from_contract(lowered.uniform_contract())?;
    scalar_uniform_requirements.require_fully_semantic()?;
    let named_output_color_slots = contract
        .outputs
        .iter()
        .copied()
        .zip(contract.output_color_slots.iter().copied())
        .map(|(output, slot)| (output.terrain_output(), slot))
        .collect::<Vec<_>>();
    let fragment_source = match contract.alpha_cutoff {
        None => lowered.fragment().source().to_string(),
        Some(cutoff) => {
            if !cutoff.is_finite() || !(0.0..=1.0).contains(&cutoff) {
                return Err(GalError::invalid_argument(
                    "damagedblock alpha cutoff must be finite and in [0, 1]",
                ));
            }
            let renamed = crate::render::shaderpack::lowering::rename_glsl_main(
                lowered.fragment().source(), "vulkanic_damaged_block_main")?;
            format!(
                "{renamed}\nvoid main() {{\n    vulkanic_damaged_block_main();\n    if (!(out_cloud_lit_color.a > {cutoff:.8})) discard;\n}}\n"
            )
        }
    };
    let program = LoweredTexturedMaterialSourceProgram {
        identity: ProgramIdentity::new(format!(
            "vulkanic:shader-pack/{}/damagedblock_source_gen{}{}",
            contract.pack_name.to_ascii_lowercase(),
            contract.generation,
            scope_identity_tag(contract.scope)
        )),
        shader_pack_generation: contract.generation,
        vertex: ShaderStageSource {
            stage: ShaderStageKind::Vertex,
            label: format!("{}:lowered-vertex", lowered.vertex().entry_path()),
            source: lowered.vertex().source().to_string(),
            entry_point: "main".to_string(),
        },
        fragment: ShaderStageSource {
            stage: ShaderStageKind::Fragment,
            label: format!("{}:lowered-fragment", lowered.fragment().entry_path()),
            source: fragment_source,
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

pub fn prepare_lowered_cloud_source_program(
    contract: &CloudPassContract,
    lowered: &LoweredCloudSourcePair,
    opaque_resource_bindings: &TerrainSourceOpaqueResourceBindingPlan,
) -> GalResult<LoweredCloudSourceProgram> {
    if contract.generation == 0 || contract.outputs.is_empty() {
        return Err(GalError::invalid_argument(
            "cloud source preparation requires a non-zero generation and named outputs",
        ));
    }
    if contract.outputs.len() != 3 || contract.outputs.len() != contract.output_color_slots.len() {
        return Err(GalError::unsupported_feature(
            "cloud source preparation supports exactly lit, material, and translucency outputs",
        ));
    }
    lowered.require_backend_neutral_lowering()?;
    lowered.require_matching_opaque_resource_bindings(opaque_resource_bindings)?;
    let execution_interface =
        TexturedMaterialSourceExecutionInterface::from_uniform_contract(lowered.uniform_contract());
    execution_interface.validate()?;
    let scalar_uniform_requirements =
        TerrainSourceUniformRequirements::from_contract(lowered.uniform_contract())?;
    scalar_uniform_requirements.require_fully_semantic()?;
    let named_output_color_slots = contract
        .outputs
        .iter()
        .copied()
        .zip(contract.output_color_slots.iter().copied())
        .map(|(output, slot)| (output.terrain_output(), slot))
        .collect::<Vec<_>>();
    if named_output_color_slots
        .iter()
        .map(|(output, _)| output)
        .collect::<BTreeSet<_>>()
        .len()
        != named_output_color_slots.len()
    {
        return Err(GalError::invalid_argument(
            "cloud source maps multiple outputs to one semantic named target",
        ));
    }
    let program = LoweredCloudSourceProgram {
        identity: ProgramIdentity::new(format!(
            "vulkanic:shader-pack/{}/cloud_source_gen{}{}",
            contract.pack_name.to_ascii_lowercase(),
            contract.generation,
            scope_identity_tag(contract.scope)
        )),
        shader_pack_generation: contract.generation,
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
        blend: contract.blend,
        named_output_color_slots,
    };
    program.execution_resource_layouts()?;
    Ok(program)
}

//! Lowered fullscreen source programs, feedback and mipmap requirements.

use super::*;

/// Source-derived preparation for a pass-local fullscreen stage. This covers
/// deferred/composite consumers without relabeling them as terrain meshes or
/// inheriting an Iris/Java fullscreen draw. It owns no target, pipeline,
/// descriptor set, or route selection.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LoweredFullscreenSourceProgram {
    pub identity: ProgramIdentity,
    /// Pack-relative source stage identity retained for diagnostics and
    /// source-to-runtime correlation. It is semantic shader-pack metadata,
    /// not an attachment, native program, or backend handle.
    pub source_stage_path: String,
    pub shader_pack_generation: u64,
    /// Explicit Rust-owned geometry contract for this source stage. Backends
    /// receive only its draw count, never Java/Iris buffers or state.
    pub raster_primitive: FullscreenSourceRasterPrimitive,
    pub vertex: ShaderStageSource,
    pub fragment: ShaderStageSource,
    pub execution_interface: FullscreenSourceExecutionInterface,
    pub scalar_uniform_requirements: TerrainSourceUniformRequirements,
    pub opaque_resource_bindings: TerrainSourceOpaqueResourceBindingPlan,
    pub outputs: Vec<FullscreenSourceFragmentOutput>,
    /// A source fullscreen pass cannot sample and write the same semantic
    /// color resource in one draw. These requirements force a future Rust
    /// executor to allocate an explicit previous/current pair instead of
    /// accidentally binding one image for both uses.
    pub feedback_requirements: Vec<FullscreenSourceFeedbackRequirement>,
    /// Program-local source directives requesting mip generation for a
    /// semantic pack color before this pass samples it. The source language
    /// keeps these directives in the fragment stage; they are not global
    /// attachment metadata and cannot be guessed from texture usage alone.
    pub mipmap_requirements: Vec<FullscreenSourceMipmapRequirement>,
}

/// One source-declared sampled/output alias that needs a Rust-owned feedback
/// pair. The source location and binding are retained only for diagnostic
/// correlation; allocation and native image identity remain runtime-private.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct FullscreenSourceFeedbackRequirement {
    pub role: TerrainSourceResourceRole,
    pub sampled_binding: u32,
    pub output_location: u32,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct FullscreenSourceMipmapRequirement {
    pub role: TerrainSourceResourceRole,
    pub sampled_binding: u32,
}

/// Fixed backend-neutral source ABI for a fullscreen pass. A Rust-owned
/// procedural triangle supplies position, primary UV, and secondary UV from
/// the draw vertex index, so no Java/Iris stream or backend-specific vertex
/// declaration participates. The second coordinate retains the semantic role
/// of legacy texture-coordinate set one. Set zero owns only semantic texture
/// transforms and scalar source uniforms; sampled resources remain in the
/// separate pack set.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct FullscreenSourceExecutionInterface {
    pub texture_transforms: TerrainSourceFixedBinding,
    pub texture_transform_bytes: u32,
    pub scalar_uniforms: Option<TerrainSourceFixedBinding>,
    pub scalar_uniform_bytes: u32,
    pub scalar_uniform_fields: Vec<TerrainSourceUniformField>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct FullscreenSourceExecutionLayouts {
    pub source_data: ResourceLayoutDesc,
    pub pack_resources: ResourceLayoutDesc,
}

impl FullscreenSourceExecutionInterface {
    pub(crate) const TEXTURE_TRANSFORMS: TerrainSourceFixedBinding = TerrainSourceFixedBinding {
        set: 0,
        binding: 0,
        kind: TerrainSourceBindingKind::UniformBuffer,
    };

    pub(crate) const SCALAR_UNIFORMS: TerrainSourceFixedBinding = TerrainSourceFixedBinding {
        set: 0,
        binding: 2,
        kind: TerrainSourceBindingKind::UniformBuffer,
    };

    pub(crate) fn from_lowered_pair(lowered: &LoweredFullscreenSourcePair) -> Self {
        let contract = lowered.uniform_contract();
        Self {
            texture_transforms: Self::TEXTURE_TRANSFORMS,
            texture_transform_bytes: 2 * 16 * std::mem::size_of::<f32>() as u32,
            scalar_uniforms: (!contract.fields().is_empty()).then_some(Self::SCALAR_UNIFORMS),
            scalar_uniform_bytes: contract.std140_size(),
            scalar_uniform_fields: contract.fields().to_vec(),
        }
    }

    pub fn validate(&self) -> GalResult<()> {
        if self.texture_transforms != Self::TEXTURE_TRANSFORMS
            || self.texture_transform_bytes != 128
        {
            return Err(GalError::invalid_argument(
                "fullscreen source texture transforms must use fixed set 0 binding 0 with two std140 mat4 values",
            ));
        }
        validate_source_scalar_uniform_block(
            self.scalar_uniforms,
            self.scalar_uniform_bytes,
            &self.scalar_uniform_fields,
            Self::SCALAR_UNIFORMS,
            "fullscreen source",
        )
    }
}

impl LoweredFullscreenSourceProgram {
    pub fn shader_module_descriptors(&self, conventions: ShaderConventions) -> [ShaderModuleDesc; 2] {
        [
            self.vertex.shader_module_descriptor(conventions),
            self.fragment.shader_module_descriptor(conventions),
        ]
    }

    pub fn pack_scalar_uniforms(&self, frame: &TerrainSourceUniformFrame) -> GalResult<Vec<u8>> {
        let bytes = frame.pack_std140(&self.scalar_uniform_requirements)?;
        if bytes.len() != self.execution_interface.scalar_uniform_bytes as usize {
            return Err(GalError::invalid_argument(format!(
                "fullscreen source scalar uniform pack is {} bytes but program ABI requires {}",
                bytes.len(),
                self.execution_interface.scalar_uniform_bytes
            )));
        }
        Ok(bytes)
    }

    pub fn pack_texture_transforms(
        &self,
        transforms: &TerrainSourceTextureTransforms,
    ) -> GalResult<Vec<u8>> {
        self.execution_interface.validate()?;
        transforms.validate()?;
        let mut bytes =
            Vec::with_capacity(self.execution_interface.texture_transform_bytes as usize);
        for value in transforms
            .atlas_texture_matrix
            .iter()
            .chain(transforms.lightmap_texture_matrix.iter())
        {
            bytes.extend_from_slice(&value.to_ne_bytes());
        }
        if bytes.len() != self.execution_interface.texture_transform_bytes as usize {
            return Err(GalError::invalid_argument(
                "fullscreen source texture transform pack does not match its fixed ABI size",
            ));
        }
        Ok(bytes)
    }

    pub fn execution_resource_layouts(&self) -> GalResult<FullscreenSourceExecutionLayouts> {
        self.execution_interface.validate()?;
        let mut source_data_bindings = vec![resource_binding_descriptor(
            self.execution_interface.texture_transforms,
            true,
        )];
        if let Some(scalar_uniforms) = self.execution_interface.scalar_uniforms {
            source_data_bindings.push(resource_binding_descriptor(scalar_uniforms, true));
        }
        source_data_bindings.sort_by_key(|binding| binding.binding);
        validate_unique_layout_bindings("fullscreen source data", &source_data_bindings)?;
        Ok(FullscreenSourceExecutionLayouts {
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
                "fullscreen source program generation {} does not match resource availability generation {}",
                self.shader_pack_generation,
                availability.shader_pack_generation()
            )));
        }
        for binding in self.opaque_resource_bindings.bindings() {
            if availability.resource_for(binding.role()).is_none() {
                return Err(GalError::invalid_argument(format!(
                    "fullscreen source resource '{}' is unavailable for semantic role '{}'",
                    binding.resource_name(),
                    binding.role().semantic_name()
                )));
            }
        }
        Ok(())
    }

    /// Builds the deterministic set-one GAL description for the semantic
    /// fullscreen source-resource plan. The caller owns the resources and
    /// pass lifetime; this only maps already validated semantic roles into a
    /// backend-neutral GAL descriptor set.
    pub fn pack_resource_set_desc(
        &self,
        label: impl Into<String>,
        layout: Handle,
        resources: &TerrainSourceOwnedResourceSet,
    ) -> GalResult<ResourceSetDesc> {
        if layout.kind() != Some(HandleKind::ResourceLayout) {
            return Err(GalError::invalid_argument(
                "fullscreen source pack resources require a GAL resource-layout handle",
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
                                "fullscreen source resource '{}' has no owned sampler for semantic role '{}'",
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
                                "fullscreen source storage resource '{}' has no owned texture view for semantic role '{}'",
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

/// Creates a preparation artifact for one source-defined fullscreen consumer.
/// The caller must later supply named output attachments, owned fullscreen
/// geometry, complete source uniforms, semantic sampler resources, and pass
/// ordering. This function performs none of those actions and cannot admit a
/// live route on its own.
pub fn prepare_lowered_fullscreen_source_program(
    pack_name: &str,
    shader_pack_generation: u64,
    stage_path: &str,
    lowered: &LoweredFullscreenSourcePair,
    opaque_resource_bindings: &TerrainSourceOpaqueResourceBindingPlan,
) -> GalResult<LoweredFullscreenSourceProgram> {
    if pack_name.trim().is_empty() || shader_pack_generation == 0 || stage_path.trim().is_empty() {
        return Err(GalError::invalid_argument(
            "fullscreen source program requires pack name, generation, and stage path",
        ));
    }
    lowered.require_backend_neutral_lowering()?;
    lowered.require_matching_opaque_resource_bindings(opaque_resource_bindings)?;
    let execution_interface = FullscreenSourceExecutionInterface::from_lowered_pair(lowered);
    execution_interface.validate()?;
    let scalar_uniform_requirements =
        TerrainSourceUniformRequirements::from_contract(lowered.uniform_contract())?;
    scalar_uniform_requirements.require_fully_semantic()?;
    let outputs = lowered.fragment().outputs().to_vec();
    if outputs.is_empty() {
        return Err(GalError::invalid_argument(
            "fullscreen source program requires at least one named semantic output",
        ));
    }
    let mut feedback_requirements = Vec::new();
    for output in &outputs {
        if !matches!(output.role(), TerrainSourceResourceRole::ShaderPackColor(_)) {
            return Err(GalError::invalid_argument(format!(
                "fullscreen source output '{}' is not a semantic shader-pack color resource",
                output.semantic_name()
            )));
        }
        for binding in opaque_resource_bindings.bindings() {
            if binding.kind() == TerrainSourceOpaqueResourceKind::CombinedTextureSampler
                && binding.role() == output.role()
            {
                feedback_requirements.push(FullscreenSourceFeedbackRequirement {
                    role: output.role(),
                    sampled_binding: binding.binding(),
                    output_location: output.source_location(),
                });
            }
        }
    }
    feedback_requirements.sort_by(|left, right| {
        left.output_location
            .cmp(&right.output_location)
            .then_with(|| left.sampled_binding.cmp(&right.sampled_binding))
            .then_with(|| left.role.cmp(&right.role))
    });
    feedback_requirements.dedup();
    let mipmap_requirements = derive_fullscreen_mipmap_requirements(
        lowered.fragment().source(),
        opaque_resource_bindings,
    )?;
    let program = LoweredFullscreenSourceProgram {
        identity: ProgramIdentity::new(format!(
            "vulkanic:shader-pack/{}/{}-source-gen{}",
            pack_name.to_ascii_lowercase(),
            stage_path
                .trim_end_matches(".fsh")
                .trim_end_matches(".glsl")
                .replace('/', "-"),
            shader_pack_generation
        )),
        source_stage_path: stage_path.to_string(),
        shader_pack_generation,
        raster_primitive: lowered.raster_primitive(),
        vertex: ShaderStageSource {
            stage: ShaderStageKind::Vertex,
            label: format!(
                "{}:lowered-fullscreen-vertex",
                lowered.vertex().entry_path()
            ),
            source: lowered.vertex().source().to_string(),
            entry_point: "main".to_string(),
        },
        fragment: ShaderStageSource {
            stage: ShaderStageKind::Fragment,
            label: format!(
                "{}:lowered-fullscreen-fragment",
                lowered.fragment().entry_path()
            ),
            source: lowered.fragment().source().to_string(),
            entry_point: "main".to_string(),
        },
        execution_interface,
        scalar_uniform_requirements,
        opaque_resource_bindings: opaque_resource_bindings.clone(),
        outputs,
        feedback_requirements,
        mipmap_requirements,
    };
    program.execution_resource_layouts()?;
    Ok(program)
}

pub(crate) fn derive_fullscreen_mipmap_requirements(
    fragment_source: &str,
    bindings: &TerrainSourceOpaqueResourceBindingPlan,
) -> GalResult<Vec<FullscreenSourceMipmapRequirement>> {
    let mut requested = std::collections::BTreeMap::<String, bool>::new();
    for line in fragment_source.lines() {
        let Some(fragment) = line.find("const bool ").map(|index| &line[index..]) else {
            continue;
        };
        let Some((name, value)) = fragment
            .strip_prefix("const bool ")
            .and_then(|rest| rest.split_once('='))
        else {
            continue;
        };
        let name = name.trim();
        let Some(resource_name) = name.strip_suffix("MipmapEnabled") else {
            continue;
        };
        let value = value
            .split_once(';')
            .map(|(value, _)| value)
            .unwrap_or(value)
            .trim();
        let enabled = match value {
            "true" => true,
            "false" => false,
            _ => {
                return Err(GalError::invalid_argument(format!(
                    "fullscreen source mip directive '{name}' must be true or false"
                )))
            }
        };
        requested.insert(resource_name.to_string(), enabled);
    }
    let mut requirements = Vec::new();
    for (resource_name, enabled) in requested {
        if !enabled {
            continue;
        }
        let binding = bindings
            .bindings()
            .iter()
            .find(|binding| binding.resource_name() == resource_name)
            .ok_or_else(|| GalError::unsupported_feature(format!(
                "fullscreen source requests mipmaps for '{resource_name}', which has no active semantic resource binding"
            )))?;
        if binding.kind() != TerrainSourceOpaqueResourceKind::CombinedTextureSampler
            || !matches!(
                binding.role(),
                TerrainSourceResourceRole::ShaderPackColor(_)
            )
        {
            return Err(GalError::unsupported_feature(format!(
                "fullscreen source requests mipmaps for '{resource_name}', which is not a semantic shader-pack color sampler"
            )));
        }
        requirements.push(FullscreenSourceMipmapRequirement {
            role: binding.role(),
            sampled_binding: binding.binding(),
        });
    }
    requirements.sort_by(|left, right| {
        left.sampled_binding
            .cmp(&right.sampled_binding)
            .then_with(|| left.role.cmp(&right.role))
    });
    requirements.dedup();
    Ok(requirements)
}

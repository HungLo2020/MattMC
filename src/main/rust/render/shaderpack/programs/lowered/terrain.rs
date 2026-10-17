//! Lowered terrain, translucent-terrain and shadow source programs and their execution interface.

use super::*;

/// Fixed std430 source-terrain vertex record declared by the Rust source
/// lowerer: four `uvec4` words that the vertex preamble decodes into the
/// eight semantic lanes. Frontend staging may use this semantic ABI, but it
/// is not a Java vertex layout or a native backend format.
///
/// | word | x | y | z | w |
/// | --- | --- | --- | --- | --- |
/// | 0 | position.x bits | position.y bits | position.z bits | color ARGB8 |
/// | 1 | uv.x bits | uv.y bits | normal i8×3 | light: block, render type, sky bytes |
/// | 2 | block id (i32) | mid-block i8×4 | mid-tex.x bits | mid-tex.y bits |
/// | 3 | tangent.x bits | tangent.y bits | tangent.z bits | tangent.w bits |
pub(crate) const TERRAIN_SOURCE_VERTEX_BYTES: usize = 4 * 4 * std::mem::size_of::<u32>();

/// Test mirror of the vertex preamble's decode: the eight semantic vec4
/// lanes of one packed record, in `EXPECTED_FIELDS` order.
#[cfg(test)]
pub(crate) fn decode_source_vertex_record(record: &[u8]) -> [[f32; 4]; 8] {
    let word = |index: usize| u32::from_ne_bytes(record[index * 4..index * 4 + 4].try_into().unwrap());
    let float = |index: usize| f32::from_bits(word(index));
    let signed = |packed: u32, lane: u32| ((packed >> (lane * 8)) as u8 as i8) as f32;
    let color = word(3);
    let light = word(7);
    [
        [float(0), float(1), float(2), 1.0],
        [16, 8, 0, 24].map(|shift| ((color >> shift) & 0xff) as f32 / 255.0),
        [
            (signed(word(6), 0) / 127.0).clamp(-1.0, 1.0),
            (signed(word(6), 1) / 127.0).clamp(-1.0, 1.0),
            (signed(word(6), 2) / 127.0).clamp(-1.0, 1.0),
            0.0,
        ],
        [float(4), float(5), (light & 0xff) as f32, ((light >> 16) & 0xff) as f32],
        [word(8) as i32 as f32, ((light >> 8) & 0xff) as f32, 0.0, 1.0],
        [float(10), float(11), 0.0, 1.0],
        [float(12), float(13), float(14), float(15)],
        [0, 1, 2, 3].map(|lane| signed(word(9), lane)),
    ]
}

/// Byte offsets of packed lanes inside one source vertex record.
pub(crate) const TERRAIN_SOURCE_VERTEX_UV_OFFSET: usize = 16;
pub(crate) const TERRAIN_SOURCE_VERTEX_NORMAL_OFFSET: usize = 24;
pub(crate) const TERRAIN_SOURCE_VERTEX_LIGHT_OFFSET: usize = 28;

/// Packs one source vertex record (see [`TERRAIN_SOURCE_VERTEX_BYTES`]).
#[allow(clippy::too_many_arguments)]
pub(crate) fn push_source_vertex_record(
    out: &mut Vec<u8>,
    position: [f32; 3],
    color_argb: u32,
    uv: [f32; 2],
    normal_packed: u32,
    light: u32,
    render_type: u8,
    block_id: i32,
    mid_block_packed: u32,
    mid_tex_coord: [f32; 2],
    tangent: [f32; 4],
) {
    let words = [
        position[0].to_bits(),
        position[1].to_bits(),
        position[2].to_bits(),
        color_argb,
        uv[0].to_bits(),
        uv[1].to_bits(),
        normal_packed & 0x00ff_ffff,
        (light & 0x00ff_00ff) | (u32::from(render_type) << 8),
        block_id as u32,
        mid_block_packed,
        mid_tex_coord[0].to_bits(),
        mid_tex_coord[1].to_bits(),
        tangent[0].to_bits(),
        tangent[1].to_bits(),
        tangent[2].to_bits(),
        tangent[3].to_bits(),
    ];
    for word in words {
        out.extend_from_slice(&word.to_ne_bytes());
    }
}

/// One source terrain instance carries a copied semantic model transform and
/// color modulation. This is deliberately independent of a Java vertex
/// layout or backend state, but preserves the per-instance appearance already
/// present in the shared world-mesh semantic request.
pub(crate) const TERRAIN_SOURCE_INSTANCE_BYTES: usize = 20 * std::mem::size_of::<f32>();

/// Actual source text after Rust's bounded source lowering, paired with the
/// pack-declared semantic sampler roles it needs. It may represent a normal
/// terrain or shadow-material source pair; its identity and named outputs keep
/// those uses distinct. This is intentionally not a `TerrainMaterialProgram`:
/// no existing renderer path can compile or draw it until a later source-mesh
/// and semantic resource-set slice is complete.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LoweredTerrainSourceProgram {
    pub identity: ProgramIdentity,
    /// Semantic material pass admitted by this program. Shadow-only programs
    /// intentionally carry no material kind.
    pub material_kind: Option<TerrainMaterialProgramKind>,
    /// Exact shader-pack source generation which produced this program.
    pub shader_pack_generation: u64,
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
    /// Exact named terrain outputs retained from the source contract. Shadow
    /// programs intentionally leave this absent because their outputs belong
    /// to a separate shadow pass schema.
    pub(in crate::render::shaderpack::programs) terrain_outputs: Option<Vec<TerrainPassOutput>>,
    /// Source-declared shader-pack color slot for each named terrain output.
    /// These slots are source metadata used to resolve Rust-owned semantic
    /// targets; they are never GAL attachment indices or backend bindings.
    pub(in crate::render::shaderpack::programs) terrain_output_color_slots: Option<Vec<(TerrainPassOutput, u32)>>,
    /// Explicit source-derived raster semantics for the separate translucent
    /// stage. Normal terrain and shadow programs deliberately leave this
    /// empty instead of borrowing a renderer default.
    pub(in crate::render::shaderpack::programs) translucent_raster_state: Option<TerrainTranslucentRasterState>,
}

/// Backend-neutral layouts required to execute a lowered source-terrain
/// program. These are descriptions only: callers still need to allocate
/// Rust-owned resources, create GAL layouts/sets, and prove source-plan
/// readiness before any render route can use them.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TerrainSourceExecutionLayouts {
    /// Fixed Rust-owned streams and scalar blocks inserted by the source
    /// lowerer. This is always descriptor set zero.
    pub source_data: ResourceLayoutDesc,
    /// Selected-pack sampler resources, mapped from declared semantic roles.
    /// This is always descriptor set one.
    pub pack_resources: ResourceLayoutDesc,
}

impl LoweredTerrainSourceProgram {
    /// Returns the source-derived terrain output schema when this is a normal
    /// terrain program. Consumers use the semantic names to build Rust-owned
    /// targets; attachment locations and native handles stay elsewhere.
    pub fn terrain_outputs(&self) -> Option<&[TerrainPassOutput]> {
        self.terrain_outputs.as_deref()
    }

    /// Retains the selected source pass's named-output to shader-pack-color
    /// mapping. Normal terrain consumers resolve this through the source
    /// target manifest before constructing an explicit GAL target.
    pub fn terrain_output_color_slots(&self) -> Option<&[(TerrainPassOutput, u32)]> {
        self.terrain_output_color_slots.as_deref()
    }

    /// Produces explicit GAL shader descriptions from the retained, lowered
    /// source pair. Creating these descriptions does not allocate shader
    /// modules, make a pipeline, bind resources, or select a render route.
    pub fn shader_module_descriptors(&self, conventions: ShaderConventions) -> [ShaderModuleDesc; 2] {
        [
            self.vertex.shader_module_descriptor(conventions),
            self.fragment.shader_module_descriptor(conventions),
        ]
    }

    /// Sodium's shadow cutout pass tests the shader's primary output alpha
    /// after the pack fragment runs. Keep that material rule in the source
    /// specialization so opaque shadow geometry retains the original shader.
    pub fn shadow_shader_module_descriptors(
        &self,
        conventions: ShaderConventions,
        alpha_cutoff: Option<f32>,
    ) -> GalResult<[ShaderModuleDesc; 2]> {
        let Some(alpha_cutoff) = alpha_cutoff else {
            return Ok(self.shader_module_descriptors(conventions));
        };
        if !alpha_cutoff.is_finite() || !(0.0..=1.0).contains(&alpha_cutoff) {
            return Err(GalError::invalid_argument(
                "shadow cutout alpha threshold must be finite and in [0, 1]",
            ));
        }
        let fragment_source = crate::render::shaderpack::lowering::rename_glsl_main(
            &self.fragment.source, "vulkanic_shadow_cutout_main")?;
        let fragment_source = format!(
            "{fragment_source}\nvoid main() {{\n    vulkanic_shadow_cutout_main();\n    if (!(out_shadow_color.a > {alpha_cutoff:.8})) discard;\n}}\n"
        );
        Ok([
            self.vertex.shader_module_descriptor(conventions),
            ShaderModuleDesc {
                label: self.fragment.label.clone(),
                stage: ShaderStage::Fragment,
                code_format: ShaderCodeFormat::Glsl,
                code: shader_stage_code(conventions, &fragment_source),
                entry_point: self.fragment.entry_point.clone(),
            },
        ])
    }

    /// Returns the source-declared raster semantics only for the independent
    /// translucent stage. Callers must translate this to explicit GAL state;
    /// it never exposes a legacy renderer state object.
    pub fn translucent_raster_state(&self) -> Option<TerrainTranslucentRasterState> {
        self.translucent_raster_state
    }

    /// Maps the source's semantic translucent blend rule to the explicit GAL
    /// pipeline model. This mapping is shared by both Rust backends; neither
    /// OpenGL blend state nor Vulkan blend factors leak out of the backends.
    pub fn translucent_blend_mode(&self) -> Option<BlendMode> {
        self.translucent_raster_state
            .map(|state| match state.blend {
                TerrainTranslucentBlend::SourceAlphaOver => BlendMode::Alpha,
            })
    }

    /// Packs only the named source semantics admitted by this prepared
    /// program. It cannot accept arbitrary bytes or backend state. The
    /// world frontend couples the returned bytes to the explicit GAL
    /// buffer/resource-set lifecycle in the selected Rust submission.
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

    /// Packs the two fixed legacy texture transforms consumed by the lowered
    /// source preamble. They remain an explicit semantic frame input instead
    /// of a borrowed Java/Iris uniform block or a guessed identity matrix.
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
                "terrain source legacy texture transform pack does not match its fixed ABI size",
            ));
        }
        Ok(bytes)
    }

    /// Derives the closed GAL layout contract from the fixed source ABI and
    /// the selected pack's semantic resource plan. It never accepts native
    /// objects, Java renderer state, or arbitrary binding declarations.
    pub fn execution_resource_layouts(&self) -> GalResult<TerrainSourceExecutionLayouts> {
        self.execution_interface.validate()?;

        let mut source_data_bindings = vec![
            // Material vertices share the completion-gated frame stream with
            // transforms and scalar data. The explicit per-draw byte range
            // is selected through this dynamic offset.
            resource_binding_descriptor(self.execution_interface.vertex_stream, false),
            resource_binding_descriptor(self.execution_interface.legacy_transforms, true),
            resource_binding_descriptor(self.execution_interface.instance_stream, true),
        ];
        if let Some(scalar_uniforms) = self.execution_interface.scalar_uniforms {
            source_data_bindings.push(resource_binding_descriptor(scalar_uniforms, true));
        }
        source_data_bindings.sort_by_key(|binding| binding.binding);
        validate_unique_layout_bindings("terrain source data", &source_data_bindings)?;

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
        validate_unique_layout_bindings("terrain source pack resources", &pack_resource_bindings)?;

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

    /// Ensures every active source sampler role has a Rust-owned semantic
    /// resource in the same shader-pack generation. This remains independent
    /// of native handles and does not create a GAL resource set.
    pub fn require_semantic_resources(
        &self,
        availability: &TerrainSourceResourceAvailabilitySet,
    ) -> GalResult<()> {
        if availability.shader_pack_generation() != self.shader_pack_generation {
            return Err(GalError::invalid_argument(format!(
                "terrain source program generation {} does not match resource availability generation {}",
                self.shader_pack_generation,
                availability.shader_pack_generation()
            )));
        }
        for binding in self.opaque_resource_bindings.bindings() {
            if availability.resource_for(binding.role()).is_none() {
                return Err(GalError::invalid_argument(format!(
                    "terrain source resource '{}' is unavailable for semantic role '{}'",
                    binding.resource_name(),
                    binding.role().semantic_name()
                )));
            }
        }
        Ok(())
    }

    /// Builds the deterministic set-one GAL description for the semantic
    /// source-resource plan. The caller owns all semantic resource lifetimes;
    /// sampled and writable image bindings remain distinct in the resulting
    /// GAL set.
    pub fn pack_resource_set_desc(
        &self,
        label: impl Into<String>,
        layout: Handle,
        resources: &TerrainSourceOwnedResourceSet,
    ) -> GalResult<ResourceSetDesc> {
        if layout.kind() != Some(HandleKind::ResourceLayout) {
            return Err(GalError::invalid_argument(
                "terrain source pack resources require a GAL resource-layout handle",
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
                                "terrain source resource '{}' has no owned sampler for semantic role '{}'",
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
                                "terrain source resource '{}' has no owned storage texture view for semantic role '{}'",
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

/// Exact semantic values for the two fixed legacy texture coordinates used by
/// the lowered terrain source. The first transforms atlas UVs; the second
/// transforms packed lightmap coordinates. Neither is a native uniform
/// location, renderer object, or a license to borrow shader-pack state.
#[derive(Clone, Debug, PartialEq)]
pub struct TerrainSourceTextureTransforms {
    pub atlas_texture_matrix: [f32; 16],
    pub lightmap_texture_matrix: [f32; 16],
}

impl TerrainSourceTextureTransforms {
    /// Canonical Minecraft terrain texture transforms, expressed as owned
    /// semantic values. Atlas coordinates are already normalized atlas UVs;
    /// lightmap coordinates are the original UV2 integer values and require
    /// the vanilla 1/256 scale plus 1/32 texel-center offset. This matches
    /// the documented `LightTexture` replacement transform, without reading
    /// an Iris uniform, texture unit, or mutable GL matrix.
    pub fn canonical_minecraft_terrain() -> Self {
        Self {
            atlas_texture_matrix: [
                1.0, 0.0, 0.0, 0.0, // column 0
                0.0, 1.0, 0.0, 0.0, // column 1
                0.0, 0.0, 1.0, 0.0, // column 2
                0.0, 0.0, 0.0, 1.0, // column 3
            ],
            lightmap_texture_matrix: [
                1.0 / 256.0,
                0.0,
                0.0,
                0.0,
                0.0,
                1.0 / 256.0,
                0.0,
                0.0,
                0.0,
                0.0,
                1.0 / 256.0,
                0.0,
                1.0 / 32.0,
                1.0 / 32.0,
                1.0 / 32.0,
                1.0,
            ],
        }
    }

    pub fn validate(&self) -> GalResult<()> {
        for (name, matrix) in [
            ("atlas", &self.atlas_texture_matrix),
            ("lightmap", &self.lightmap_texture_matrix),
        ] {
            if matrix.iter().any(|value| !value.is_finite()) {
                return Err(GalError::invalid_argument(format!(
                    "terrain source {name} texture matrix contains a non-finite value"
                )));
            }
        }
        Ok(())
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
    pub vertex_fields: [TerrainSourceVertexField; 8],
    pub legacy_transforms: TerrainSourceFixedBinding,
    pub scalar_uniforms: Option<TerrainSourceFixedBinding>,
    pub instance_stream: TerrainSourceFixedBinding,
    pub instance_stride: u32,
    pub legacy_transform_bytes: u32,
    pub scalar_uniform_bytes: u32,
    pub scalar_uniform_fields: Vec<TerrainSourceUniformField>,
}

impl TerrainSourceExecutionInterface {
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

    pub(crate) const INSTANCE_STREAM: TerrainSourceFixedBinding = TerrainSourceFixedBinding {
        set: 0,
        binding: 3,
        kind: TerrainSourceBindingKind::StorageBuffer,
    };

    pub(crate) fn from_uniform_contract(contract: &crate::render::shaderpack::lowering::TerrainSourceUniformContract) -> Self {
        Self {
            vertex_stream: Self::VERTEX_STREAM,
            vertex_stride: TERRAIN_SOURCE_VERTEX_BYTES as u32,
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
                TerrainSourceVertexField {
                    name: "mid_block",
                    offset: 112,
                    component_count: 4,
                },
            ],
            legacy_transforms: Self::LEGACY_TRANSFORMS,
            scalar_uniforms: (!contract.fields().is_empty()).then_some(Self::SCALAR_UNIFORMS),
            instance_stream: Self::INSTANCE_STREAM,
            instance_stride: TERRAIN_SOURCE_INSTANCE_BYTES as u32,
            // Two std140 mat4 texture transforms.
            legacy_transform_bytes: 2 * 16 * std::mem::size_of::<f32>() as u32,
            scalar_uniform_bytes: contract.std140_size(),
            scalar_uniform_fields: contract.fields().to_vec(),
        }
    }

    pub(crate) fn from_lowered_pair(lowered: &LoweredTerrainSourcePair) -> Self {
        Self::from_uniform_contract(lowered.uniform_contract())
    }

    pub(crate) fn from_lowered_entity_pair(lowered: &LoweredEntitySourcePair) -> Self {
        Self::from_uniform_contract(lowered.uniform_contract())
    }

    pub(crate) fn from_lowered_hand_pair(lowered: &LoweredHandSourcePair) -> Self {
        let mut interface = Self::from_uniform_contract(lowered.uniform_contract());
        // Hand clip projection is distinct from the pack's world-camera
        // gbufferProjection uniform and follows the two texture matrices.
        interface.legacy_transform_bytes = 3 * 16 * std::mem::size_of::<f32>() as u32;
        interface
    }

    pub(crate) fn from_lowered_translucent_pair(lowered: &LoweredTranslucentTerrainSourcePair) -> Self {
        Self::from_uniform_contract(lowered.uniform_contract())
    }

    pub(crate) fn from_lowered_shadow_pair(lowered: &LoweredShadowSourcePair) -> Self {
        Self::from_uniform_contract(lowered.uniform_contract())
    }

    /// Rejects an internally inconsistent prepared-source interface before a
    /// later runtime slice can turn it into GAL layouts and uploads. The
    /// source lowerer owns this ABI; callers must not be able to reinterpret
    /// it as a legacy renderer vertex format or arbitrary binding scheme.
    pub fn validate(&self) -> GalResult<()> {
        const EXPECTED_FIELDS: [(&str, u32); 8] = [
            ("position", 0),
            ("color", 16),
            ("normal_light", 32),
            ("atlas_uv_lightmap", 48),
            ("entity", 64),
            ("mid_tex_coord", 80),
            ("tangent", 96),
            ("mid_block", 112),
        ];

        if self.vertex_stream != Self::VERTEX_STREAM {
            return Err(GalError::invalid_argument(
                "terrain source vertex stream must use fixed set 0 binding 0 storage-buffer ABI",
            ));
        }
        if self.vertex_stride != TERRAIN_SOURCE_VERTEX_BYTES as u32 {
            return Err(GalError::invalid_argument(format!(
                "terrain source vertex stride {} does not match the fixed {}-byte ABI",
                self.vertex_stride, TERRAIN_SOURCE_VERTEX_BYTES
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
        if self.legacy_transforms != Self::LEGACY_TRANSFORMS
            || !matches!(self.legacy_transform_bytes, 128 | 192)
        {
            return Err(GalError::invalid_argument(
                "terrain source legacy transforms must use fixed set 0 binding 1 with two or three std140 mat4 values",
            ));
        }
        if self.instance_stream != Self::INSTANCE_STREAM
            || self.instance_stride != TERRAIN_SOURCE_INSTANCE_BYTES as u32
        {
            return Err(GalError::invalid_argument(format!(
                "terrain source instance stream must use fixed set 0 binding 3 with {}-byte transform/color records",
                TERRAIN_SOURCE_INSTANCE_BYTES
            )));
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
    let terrain_outputs = contract.outputs.iter().copied().collect::<Vec<_>>();
    let terrain_output_color_slots = terrain_outputs
        .iter()
        .copied()
        .map(|output| {
            contract
                .output_color_slot(output)
                .map(|slot| (output, slot))
                .ok_or_else(|| {
                    GalError::invalid_argument(format!(
                        "selected terrain source contract has no shader-pack color slot for '{}'",
                        output.semantic_name()
                    ))
                })
        })
        .collect::<GalResult<Vec<_>>>()?;
    let fragment_source = match contract.normal_alpha_test.cutoff(material_class) {
        None => lowered.fragment().source().to_owned(),
        Some(cutoff) => {
            let renamed = crate::render::shaderpack::lowering::rename_glsl_main(
                lowered.fragment().source(), "vulkanic_source_terrain_main")?;
            format!("{renamed}\nvoid main() {{\n    vulkanic_source_terrain_main();\n    if (!({}.a > {cutoff:?})) discard;\n}}\n",
                crate::render::shaderpack::lowering::TerrainFragmentOutput::LitColor.semantic_name())
        }
    };
    let program = LoweredTerrainSourceProgram {
        identity: ProgramIdentity::new(format!(
            "vulkanic:shader-pack/{}/terrain_{}_source_gen{}{}",
            contract.pack_name.to_ascii_lowercase(),
            suffix,
            contract.generation,
            program_path_identity_tag(&contract.program_path)
        )),
        material_kind: Some(kind),
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
        required_resources: if requires_colored_voxel_light {
            vec![TerrainProgramResource::ColoredVoxelLightVolume]
        } else {
            Vec::new()
        },
        terrain_outputs: Some(terrain_outputs),
        terrain_output_color_slots: Some(terrain_output_color_slots),
        translucent_raster_state: None,
    };
    program.execution_resource_layouts()?;
    Ok(program)
}

/// Prepares the distinct translucent terrain source program without merging it
/// into the normal opaque/cutout G-buffer contract. This remains preparation
/// only: the caller must later provide an explicit target, depth/history, and
/// blend pass before it can execute.
pub fn prepare_lowered_translucent_terrain_source_program(
    contract: &TerrainPassContract,
    lowered: &LoweredTranslucentTerrainSourcePair,
    opaque_resource_bindings: &TerrainSourceOpaqueResourceBindingPlan,
) -> GalResult<LoweredTerrainSourceProgram> {
    if contract.pass_kind != crate::render::shaderpack::contracts::terrain::TerrainSourcePassKind::Translucent
        || !contract
            .material_classes
            .contains(&TerrainMaterialClass::Translucent)
    {
        return Err(GalError::unsupported_feature(
            "translucent source preparation requires a translucent terrain contract",
        ));
    }
    let translucent_raster_state = contract.translucent_raster_state.ok_or_else(|| {
        GalError::unsupported_feature(
            "selected translucent terrain source has no explicit alpha/blend raster contract",
        )
    })?;
    // Parsing a `gbuffers_water` stage is not enough to admit it. The source
    // contract must have modeled every feature it selected before a later
    // executor may even receive this preparation artifact.
    contract.require_selected_subset()?;
    lowered.require_backend_neutral_lowering()?;
    lowered.require_matching_opaque_resource_bindings(opaque_resource_bindings)?;
    let execution_interface =
        TerrainSourceExecutionInterface::from_lowered_translucent_pair(lowered);
    execution_interface.validate()?;
    let scalar_uniform_requirements =
        TerrainSourceUniformRequirements::from_contract(lowered.uniform_contract())?;
    scalar_uniform_requirements.require_fully_semantic()?;
    let terrain_outputs = contract.outputs.iter().copied().collect::<Vec<_>>();
    let terrain_output_color_slots = terrain_outputs
        .iter()
        .copied()
        .map(|output| {
            contract
                .output_color_slot(output)
                .map(|slot| (output, slot))
                .ok_or_else(|| {
                    GalError::invalid_argument(format!(
                    "selected translucent source contract has no shader-pack color slot for '{}'",
                    output.semantic_name()
                ))
                })
        })
        .collect::<GalResult<Vec<_>>>()?;
    let fragment_source = match translucent_raster_state.alpha_test {
        None => lowered.fragment().source().to_owned(),
        Some(alpha_test) => {
            let renamed = crate::render::shaderpack::lowering::rename_glsl_main(&lowered.fragment().source(), "vulkanic_source_translucent_main")?;
            format!(
                "{renamed}\nvoid main() {{\n    vulkanic_source_translucent_main();\n    if (!({}.a > {:?})) discard;\n}}\n",
                crate::render::shaderpack::lowering::TranslucentTerrainFragmentOutput::LitColor.semantic_name(),
                alpha_test.greater_than(),
            )
        }
    };
    let program = LoweredTerrainSourceProgram {
        identity: ProgramIdentity::new(format!(
            "vulkanic:shader-pack/{}/terrain_translucent_source_gen{}{}",
            contract.pack_name.to_ascii_lowercase(),
            contract.generation,
            program_path_identity_tag(&contract.program_path)
        )),
        material_kind: Some(TerrainMaterialProgramKind::Translucent),
        shader_pack_generation: contract.generation,
        vertex: ShaderStageSource {
            stage: ShaderStageKind::Vertex,
            label: format!(
                "{}:lowered-translucent-vertex",
                lowered.vertex().entry_path()
            ),
            source: lowered.vertex().source().to_string(),
            entry_point: "main".to_string(),
        },
        fragment: ShaderStageSource {
            stage: ShaderStageKind::Fragment,
            label: format!(
                "{}:lowered-translucent-fragment",
                lowered.fragment().entry_path()
            ),
            source: fragment_source,
            entry_point: "main".to_string(),
        },
        execution_interface,
        scalar_uniform_requirements,
        opaque_resource_bindings: opaque_resource_bindings.clone(),
        required_resources: Vec::new(),
        terrain_outputs: Some(terrain_outputs),
        terrain_output_color_slots: Some(terrain_output_color_slots),
        translucent_raster_state: Some(translucent_raster_state),
    };
    program.execution_resource_layouts()?;
    Ok(program)
}

/// Forms an owned source-shadow program preparation artifact from one exact
/// scoped shadow pair. The program retains shadow-specific output names and
/// transform semantics; it is not a normal terrain material program and it
/// cannot select a route, allocate a shadow target, or issue a draw.
pub fn prepare_lowered_shadow_source_program(
    pack_name: &str,
    shader_pack_generation: u64,
    lowered: &LoweredShadowSourcePair,
    opaque_resource_bindings: &TerrainSourceOpaqueResourceBindingPlan,
) -> GalResult<LoweredTerrainSourceProgram> {
    if pack_name.trim().is_empty() || shader_pack_generation == 0 {
        return Err(GalError::invalid_argument(
            "source shadow program requires a non-empty pack name and non-zero generation",
        ));
    }
    lowered.require_backend_neutral_lowering()?;
    lowered.require_matching_opaque_resource_bindings(opaque_resource_bindings)?;
    let execution_interface = TerrainSourceExecutionInterface::from_lowered_shadow_pair(lowered);
    execution_interface.validate()?;
    let scalar_uniform_requirements =
        TerrainSourceUniformRequirements::from_contract(lowered.uniform_contract())?;
    scalar_uniform_requirements.require_fully_semantic()?;
    let program = LoweredTerrainSourceProgram {
        identity: ProgramIdentity::new(format!(
            "vulkanic:shader-pack/{}/shadow_source_gen{}{}",
            pack_name.to_ascii_lowercase(),
            shader_pack_generation,
            shadow_program_identity_tag(lowered, opaque_resource_bindings)
        )),
        material_kind: None,
        shader_pack_generation,
        vertex: ShaderStageSource {
            stage: ShaderStageKind::Vertex,
            label: format!("{}:lowered-shadow-vertex", lowered.vertex().entry_path()),
            source: lowered.vertex().source().to_string(),
            entry_point: "main".to_string(),
        },
        fragment: ShaderStageSource {
            stage: ShaderStageKind::Fragment,
            label: format!(
                "{}:lowered-shadow-fragment",
                lowered.fragment().entry_path()
            ),
            source: lowered.fragment().source().to_string(),
            entry_point: "main".to_string(),
        },
        execution_interface,
        scalar_uniform_requirements,
        opaque_resource_bindings: opaque_resource_bindings.clone(),
        required_resources: Vec::new(),
        terrain_outputs: None,
        terrain_output_color_slots: None,
        translucent_raster_state: None,
    };
    program.execution_resource_layouts()?;
    Ok(program)
}

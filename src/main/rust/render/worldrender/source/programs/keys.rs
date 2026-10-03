//! Cache keys and resource records of lowered source programs.

use super::*;

/// Private key for the real lowered-source set-zero data ABI. It is distinct
/// from the older internal-fixture source variant above: this key owns the
/// fixed source vertex stream and never aliases ordinary mesh buffers.
#[derive(Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub(crate) struct LoweredSourceTerrainDataKey {
    pub(in crate::render::worldrender) mesh_key: u64,
    pub(in crate::render::worldrender) mesh_generation: u64,
    pub(in crate::render::worldrender) abi: SourceGeometryAbi,
}

/// The fixed source vertex record is shared only by writers that agree on
/// every semantic lane. Shader program selection is deliberately absent:
/// it affects pipeline and descriptor state, never immutable geometry bytes.
#[derive(Clone, Copy, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub(crate) enum SourceGeometryAbi {
    Terrain,
    LocalTextured,
    /// A shared terrain geometry page (`mesh_key` is the page buffer handle):
    /// frame-data sets keyed by it serve every multi-drawn mesh on the page.
    TerrainPage,
}

/// Stable binding identity for a program's frame-varying set-zero resources.
/// Keep it separate from `geometry`: the same terrain bytes can be consumed
/// by multiple source programs, while their descriptor layouts cannot.
#[derive(Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub(crate) struct LoweredSourceTerrainFrameDataKey {
    pub(in crate::render::worldrender) geometry: LoweredSourceTerrainDataKey,
    pub(in crate::render::worldrender) shader_program_identity: ProgramIdentity,
    pub(in crate::render::worldrender) shader_pack_generation: u64,
    pub(in crate::render::worldrender) stream_buffer: Handle,
    pub(in crate::render::worldrender) instance_bytes: u64,
}

/// Stable frontend key for set-one semantic source resources. The signature
/// contains role/generation pairs rather than GAL handles so resource
/// replacement cannot accidentally retain stale bindings.
#[derive(Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub(crate) struct LoweredSourceTerrainPackKey {
    pub(in crate::render::worldrender) shader_program_identity: ProgramIdentity,
    pub(in crate::render::worldrender) shader_pack_generation: u64,
    pub(in crate::render::worldrender) world_generation: u64,
    pub(in crate::render::worldrender) resource_generations: Vec<(TerrainSourceResourceRole, u64)>,
}

/// Source-material set-one bindings need one extra semantic discriminator:
/// a local texture can replace the selected program's base-color sampler for
/// its own ordered batch while terrain continues to use the copied atlas.
/// The discriminator contains stable material identity/generation facts, not
/// a GAL or native texture handle.
#[derive(Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub(crate) struct LoweredTexturedMaterialSourcePackKey {
    pub(in crate::render::worldrender) shader_program_identity: ProgramIdentity,
    pub(in crate::render::worldrender) shader_pack_generation: u64,
    pub(in crate::render::worldrender) world_generation: u64,
    pub(in crate::render::worldrender) resource_generations: Vec<(TerrainSourceResourceRole, u64)>,
    pub(in crate::render::worldrender) local_texture: Option<(u32, u64)>,
}

/// Set-one identity for the separate `gbuffers_entities` contract. Entity
/// local material is explicit because it is not interchangeable with the
/// terrain atlas role, even when the underlying decoded bytes happen to be
/// identical.
#[derive(Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub(crate) struct LoweredEntitySourcePackKey {
    pub(in crate::render::worldrender) shader_program_identity: ProgramIdentity,
    pub(in crate::render::worldrender) shader_pack_generation: u64,
    pub(in crate::render::worldrender) world_generation: u64,
    pub(in crate::render::worldrender) resource_generations: Vec<(TerrainSourceResourceRole, u64)>,
    pub(in crate::render::worldrender) local_texture: (u32, u64),
}

#[derive(Clone, Copy, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub(crate) struct LoweredTexturedMaterialSourceLocalTextureKey {
    pub(in crate::render::worldrender) shader_pack_generation: u64,
    pub(in crate::render::worldrender) world_generation: u64,
    pub(in crate::render::worldrender) texture_id: u32,
    pub(in crate::render::worldrender) texture_generation: u64,
}

pub(crate) struct LoweredTexturedMaterialSourceLocalTextureResources {
    pub(in crate::render::worldrender) combined_sampler: Handle,
}

#[derive(Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub(crate) struct LoweredSourceTerrainProgramKey {
    pub(in crate::render::worldrender) shader_program_identity: ProgramIdentity,
    pub(in crate::render::worldrender) shader_pack_generation: u64,
}

#[derive(Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub(crate) struct LoweredSourceTerrainPipelineKey {
    pub(in crate::render::worldrender) program: LoweredSourceTerrainProgramKey,
    pub(in crate::render::worldrender) material_mode: u32,
    pub(in crate::render::worldrender) cull_policy: u32,
    pub(in crate::render::worldrender) winding: u32,
    /// Source fragment output locations are part of pipeline compatibility.
    /// Keeping their exact formats in the key prevents an earlier private
    /// fixture target from being reused for a later source-derived target
    /// with a different semantic output schema.
    pub(in crate::render::worldrender) color_formats: Vec<TextureFormat>,
    /// A dimension-specific shadow alpha specialization belongs in identity.
    pub(in crate::render::worldrender) shadow_alpha_cutoff_bits: Option<u32>,
    /// Shadow maps rasterize natively (GL memory layout for matrix-addressed
    /// lookups); screen-space writers use the GL-style flipped viewport.
    pub(in crate::render::worldrender) raster_y_direction: crate::render::vulkanic::resources::RasterYDirection,
}

/// Private set-zero identity for a compact source-material payload. The
/// stream buffer is completion-gated by frame ownership; payload bytes vary
/// only through dynamic offsets and never become a native/backend identity.
#[derive(Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub(crate) struct LoweredTexturedMaterialSourceFrameDataKey {
    pub(in crate::render::worldrender) shader_program_identity: ProgramIdentity,
    pub(in crate::render::worldrender) shader_pack_generation: u64,
    pub(in crate::render::worldrender) stream_buffer: Handle,
    pub(in crate::render::worldrender) vertex_bytes: u64,
}

#[derive(Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub(crate) struct LoweredTexturedMaterialSourcePipelineKey {
    pub(in crate::render::worldrender) shader_program_identity: ProgramIdentity,
    pub(in crate::render::worldrender) shader_pack_generation: u64,
    pub(in crate::render::worldrender) material_mode: u32,
    pub(in crate::render::worldrender) depth_policy: u32,
    pub(in crate::render::worldrender) cull_policy: u32,
    pub(in crate::render::worldrender) winding: u32,
    pub(in crate::render::worldrender) color_formats: Vec<TextureFormat>,
}

#[derive(Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub(crate) struct LoweredEntitySourcePipelineKey {
    pub(in crate::render::worldrender) shader_program_identity: ProgramIdentity,
    pub(in crate::render::worldrender) shader_pack_generation: u64,
    pub(in crate::render::worldrender) material_mode: u32,
    pub(in crate::render::worldrender) depth_policy: u32,
    pub(in crate::render::worldrender) cull_policy: u32,
    pub(in crate::render::worldrender) winding: u32,
    /// The hand adapter uses D24S8 for optical stencil roles while ordinary
    /// entities use D32.  Keep the attachment format in the identity: Vulkan
    /// pipeline compatibility includes the depth/stencil attachment format,
    /// so sharing only shader/material state can bind an invalid pipeline.
    pub(in crate::render::worldrender) depth_format: TextureFormat,
    pub(in crate::render::worldrender) color_formats: Vec<TextureFormat>,
    /// Iris shadow-pass entity casters: native raster, no culling, opaque
    /// depth write, the pack's shadow alpha test.
    pub(in crate::render::worldrender) shadow_caster: bool,
}

/// Persistent Rust-owned geometry for one lowered source-terrain mesh
/// generation. These buffers never contain frame-varying transforms or
/// instances, so they can be safely shared by every compatible prepared draw.
pub(crate) struct LoweredSourceTerrainGeometryResources {
    pub(in crate::render::worldrender) vertex_buffer: Handle,
    pub(in crate::render::worldrender) index_buffer: Handle,
    pub(in crate::render::worldrender) byte_size: u64,
    /// Host-visible source of the first upload into device-local geometry,
    /// released once the submission carrying that copy is confirmed.
    pub(in crate::render::worldrender) staging_buffer: Option<Handle>,
    /// Page ranges of paged terrain geometry; `vertex_buffer`/`index_buffer`
    /// are then shared pages that must never be destroyed with this mesh.
    pub(in crate::render::worldrender) paged: Option<(SourceGeometryRange, SourceGeometryRange)>,
}

/// Rust-owned set-zero bindings for one immutable geometry stream pair. The
/// frame payload itself remains outside this cache and is supplied through
/// dynamic offsets into the private source frame stream.
pub(crate) struct LoweredSourceTerrainFrameDataResources {
    pub(in crate::render::worldrender) resource_set: Handle,
}

/// Frame-independent selection of one copied terrain range for one program
/// kind. Mesh generations are immutable, so the resolved sections are reused
/// across frames while the converted source mesh is slim (GPU resident).
#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
pub(crate) struct SourceTerrainRangeKey {
    pub(in crate::render::worldrender) mesh_key: u64,
    pub(in crate::render::worldrender) mesh_generation: u64,
    pub(in crate::render::worldrender) material_ids: bool,
    pub(in crate::render::worldrender) index_offset: u64,
    pub(in crate::render::worldrender) index_count: u32,
    pub(in crate::render::worldrender) kind: u8,
}

#[derive(Clone)]
pub(crate) struct SourceTerrainRangeSelection {
    pub(in crate::render::worldrender) mesh: Arc<SourceTerrainMeshAsset>,
    pub(in crate::render::worldrender) section_indices: Vec<u32>,
    pub(in crate::render::worldrender) index_subrange: Option<(u32, u32)>,
}

pub(crate) const SOURCE_TERRAIN_RANGE_MEMO_MAX_ENTRIES: usize = 65_536;

/// One staged terrain batch payload and its set-zero identity.
pub(crate) struct LoweredSourceTerrainFrameData {
    pub(in crate::render::worldrender) geometry_key: LoweredSourceTerrainDataKey,
    pub(in crate::render::worldrender) frame_data_key: LoweredSourceTerrainFrameDataKey,
    pub(in crate::render::worldrender) stream: SourceTerrainFrameStreamAllocation,
    /// `Some` when the batch is multi-drawn: its instance records start at
    /// this `firstInstance` of a set bound from stream offset zero.
    pub(in crate::render::worldrender) multidraw_first_instance: Option<u32>,
}

impl LoweredSourceTerrainFrameData {
    pub(crate) fn dynamic_offsets(&self) -> SmallVec<[u64; 3]> {
        let mut offsets = SmallVec::new();
        offsets.push(self.stream.legacy_transform_offset);
        if let Some(offset) = self.stream.scalar_uniform_offset {
            offsets.push(offset);
        }
        offsets.push(if self.multidraw_first_instance.is_some() {
            0
        } else {
            self.stream.instance_offset
        });
        offsets
    }
}

pub(crate) struct LoweredSourceTerrainPackResources {
    pub(in crate::render::worldrender) resource_set: Handle,
}

pub(crate) struct LoweredSourceTerrainPipelineResources {
    pub(in crate::render::worldrender) vertex_shader: Handle,
    pub(in crate::render::worldrender) fragment_shader: Handle,
    pub(in crate::render::worldrender) pipeline_layout: Handle,
    pub(in crate::render::worldrender) pipeline: Handle,
}

pub(crate) struct LoweredTexturedMaterialSourceFrameDataResources {
    pub(in crate::render::worldrender) resource_set: Handle,
}

pub(crate) struct LoweredTexturedMaterialSourcePackResources {
    pub(in crate::render::worldrender) resource_set: Handle,
}

pub(crate) struct LoweredEntitySourcePackResources {
    pub(in crate::render::worldrender) resource_set: Handle,
}

pub(crate) struct LoweredTexturedMaterialSourcePipelineResources {
    pub(in crate::render::worldrender) vertex_shader: Handle,
    pub(in crate::render::worldrender) fragment_shader: Handle,
    pub(in crate::render::worldrender) pipeline_layout: Handle,
    pub(in crate::render::worldrender) pipeline: Handle,
}

pub(crate) struct LoweredEntitySourcePipelineResources {
    pub(in crate::render::worldrender) vertex_shader: Handle,
    pub(in crate::render::worldrender) fragment_shader: Handle,
    pub(in crate::render::worldrender) pipeline_layout: Handle,
    pub(in crate::render::worldrender) pipeline: Handle,
}

#[derive(Clone, Copy)]
pub(crate) struct LoweredSourceTerrainProgramLayouts {
    pub(in crate::render::worldrender) source_data: Handle,
    pub(in crate::render::worldrender) pack_resources: Handle,
}

#[derive(Clone, Copy)]
pub(crate) struct LoweredTexturedMaterialSourceProgramLayouts {
    pub(in crate::render::worldrender) source_data: Handle,
    pub(in crate::render::worldrender) pack_resources: Handle,
}

impl SourceMeshResources {
    pub(crate) fn handles_in_destroy_order(&self) -> [Handle; 1] {
        [self.resource_set]
    }
}

impl LoweredSourceTerrainGeometryResources {
    pub(crate) fn handles_in_destroy_order(&self) -> Vec<Handle> {
        let mut handles = if self.paged.is_some() {
            Vec::new()
        } else {
            vec![self.index_buffer, self.vertex_buffer]
        };
        handles.extend(self.staging_buffer);
        handles.dedup();
        handles
    }

    /// Byte offset of this mesh's indices inside its (possibly shared) buffer.
    pub(crate) fn index_offset(&self) -> u64 {
        self.paged.map_or(0, |(_, index)| index.offset)
    }
}

impl LoweredSourceTerrainFrameDataResources {
    pub(crate) fn handles_in_destroy_order(&self) -> Vec<Handle> {
        let mut handles = vec![Some(self.resource_set)]
            .into_iter()
            .flatten()
            .collect::<Vec<_>>();
        handles.dedup();
        handles
    }
}

impl LoweredSourceTerrainPackResources {
    pub(crate) fn handles_in_destroy_order(&self) -> [Handle; 1] {
        [self.resource_set]
    }
}

impl LoweredSourceTerrainPipelineResources {
    pub(crate) fn handles_in_destroy_order(&self) -> [Handle; 4] {
        [
            self.pipeline,
            self.pipeline_layout,
            self.fragment_shader,
            self.vertex_shader,
        ]
    }
}

impl LoweredTexturedMaterialSourceFrameDataResources {
    pub(crate) fn handles_in_destroy_order(&self) -> [Handle; 1] {
        [self.resource_set]
    }
}

impl LoweredTexturedMaterialSourcePackResources {
    pub(crate) fn handles_in_destroy_order(&self) -> [Handle; 1] {
        [self.resource_set]
    }
}

impl LoweredEntitySourcePackResources {
    pub(crate) fn handles_in_destroy_order(&self) -> [Handle; 1] {
        [self.resource_set]
    }
}

impl LoweredTexturedMaterialSourcePipelineResources {
    pub(crate) fn handles_in_destroy_order(&self) -> [Handle; 4] {
        [
            self.pipeline,
            self.pipeline_layout,
            self.fragment_shader,
            self.vertex_shader,
        ]
    }
}

impl LoweredEntitySourcePipelineResources {
    pub(crate) fn handles_in_destroy_order(&self) -> [Handle; 4] {
        [
            self.pipeline,
            self.pipeline_layout,
            self.fragment_shader,
            self.vertex_shader,
        ]
    }
}

impl LoweredTexturedMaterialSourceProgramLayouts {
    pub(crate) fn handles_in_destroy_order(&self) -> [Handle; 2] {
        [self.pack_resources, self.source_data]
    }
}

impl LoweredSourceTerrainProgramLayouts {
    pub(crate) fn handles_in_destroy_order(&self) -> [Handle; 2] {
        [self.pack_resources, self.source_data]
    }
}

#[derive(Clone)]
pub(crate) struct SourceTerrainPrograms {
    pub(in crate::render::worldrender) opaque: TerrainSourceProgramCandidate,
    pub(in crate::render::worldrender) cutout: TerrainSourceProgramCandidate,
}

impl SourceTerrainPrograms {
    pub(crate) fn for_material_mode(&self, material_mode: u32) -> GalResult<&TerrainSourceProgramCandidate> {
        match material_mode {
            WORLD_MATERIAL_MODE_OPAQUE => Ok(&self.opaque),
            WORLD_MATERIAL_MODE_CUTOUT => Ok(&self.cutout),
            _ => Err(GalError::unsupported_feature(
                "selected source terrain execution supports only opaque and cutout mesh materials",
            )),
        }
    }
}

/// Paired source-derived terrain programs prepared from one shader-pack
/// generation. This is separate from the internal fixture pair above: it
/// contains only lowered selected-source programs and has no route policy.
#[derive(Clone)]
pub(crate) struct LoweredSourceTerrainPrograms {
    pub(in crate::render::worldrender) opaque: LoweredTerrainSourceProgram,
    pub(in crate::render::worldrender) cutout: LoweredTerrainSourceProgram,
    pub(in crate::render::worldrender) shadow: LoweredTerrainSourceProgram,
    /// The source-derived translucent stage is deliberately optional until a
    /// frame actually contains translucent terrain. Its absence cannot block
    /// ordinary vanilla terrain or Distant Horizons, but a translucent batch
    /// must fail before any route is selected.
    pub(in crate::render::worldrender) translucent: Option<LoweredTerrainSourceProgram>,
}

impl LoweredSourceTerrainPrograms {
    pub(crate) fn for_material_mode(&self, material_mode: u32) -> GalResult<&LoweredTerrainSourceProgram> {
        match material_mode {
            WORLD_MATERIAL_MODE_OPAQUE => Ok(&self.opaque),
            WORLD_MATERIAL_MODE_CUTOUT => Ok(&self.cutout),
            WORLD_MATERIAL_MODE_TRANSLUCENT => self.translucent.as_ref().ok_or_else(|| {
                GalError::unsupported_feature(
                    "lowered source terrain preparation has no admitted translucent program",
                )
            }),
            _ => Err(GalError::unsupported_feature(
                "lowered source terrain preparation supports only opaque and cutout mesh materials",
            )),
        }
    }

    pub(crate) fn shader_pack_generation(&self) -> GalResult<u64> {
        if self.opaque.shader_pack_generation == 0
            || self.cutout.shader_pack_generation == 0
            || self.shadow.shader_pack_generation == 0
            || self.opaque.shader_pack_generation != self.cutout.shader_pack_generation
            || self.opaque.shader_pack_generation != self.shadow.shader_pack_generation
            || self.translucent.as_ref().is_some_and(|program| {
                program.shader_pack_generation != self.opaque.shader_pack_generation
            })
        {
            return Err(GalError::invalid_argument(
                "lowered opaque and cutout terrain programs must share one non-zero shader-pack generation",
            ));
        }
        Ok(self.opaque.shader_pack_generation)
    }
}

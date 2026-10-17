//! Resource records and cache keys of the built-in route (lines, sky disc, crack, border, materials, meshes).

use super::*;

pub(crate) struct WorldLineResources {
    pub(in crate::render::worldrender) uniform_buffer: Handle,
    pub(in crate::render::worldrender) vertex_shader: Handle,
    pub(in crate::render::worldrender) fragment_shader: Handle,
    pub(in crate::render::worldrender) resource_layout: Handle,
    pub(in crate::render::worldrender) resource_set: Handle,
    pub(in crate::render::worldrender) pipeline_layout: Handle,
    pub(in crate::render::worldrender) pipeline_depth_disabled: Handle,
    pub(in crate::render::worldrender) pipeline_depth_test_no_write: Handle,
    pub(in crate::render::worldrender) pipeline_depth_test_write: Handle,
}

pub(crate) struct WorldSkyDiscResources {
    pub(in crate::render::worldrender) uniform_buffer: Handle,
    pub(in crate::render::worldrender) vertex_shader: Handle,
    pub(in crate::render::worldrender) fragment_shader: Handle,
    pub(in crate::render::worldrender) resource_layout: Handle,
    pub(in crate::render::worldrender) resource_set: Handle,
    pub(in crate::render::worldrender) pipeline_layout: Handle,
    pub(in crate::render::worldrender) pipeline: Handle,
}

impl WorldSkyDiscResources {
    pub(crate) fn handles_in_destroy_order(&self) -> [Handle; 7] {
        [
            self.pipeline,
            self.pipeline_layout,
            self.resource_set,
            self.resource_layout,
            self.fragment_shader,
            self.vertex_shader,
            self.uniform_buffer,
        ]
    }
}

impl WorldLineResources {
    pub(crate) fn handles_in_destroy_order(&self) -> [Handle; 9] {
        [
            self.pipeline_depth_test_write,
            self.pipeline_depth_test_no_write,
            self.pipeline_depth_disabled,
            self.pipeline_layout,
            self.resource_set,
            self.resource_layout,
            self.fragment_shader,
            self.vertex_shader,
            self.uniform_buffer,
        ]
    }
}

pub(crate) struct CrackResources {
    pub(in crate::render::worldrender) upload_buffer: Handle,
    pub(in crate::render::worldrender) uniform_buffer: Handle,
    pub(in crate::render::worldrender) texture: Handle,
    pub(in crate::render::worldrender) sampler: Handle,
    pub(in crate::render::worldrender) vertex_shader: Handle,
    pub(in crate::render::worldrender) fragment_shader: Handle,
    pub(in crate::render::worldrender) texture_view: Handle,
    pub(in crate::render::worldrender) resource_layout: Handle,
    pub(in crate::render::worldrender) resource_set: Handle,
    pub(in crate::render::worldrender) pipeline_layout: Handle,
    pub(in crate::render::worldrender) pipeline_depth_disabled: Handle,
    pub(in crate::render::worldrender) pipeline_depth_test_write: Handle,
}

impl CrackResources {
    pub(crate) fn handles_in_destroy_order(&self) -> [Handle; 12] {
        [
            self.pipeline_depth_test_write,
            self.pipeline_depth_disabled,
            self.pipeline_layout,
            self.resource_set,
            self.resource_layout,
            self.texture_view,
            self.fragment_shader,
            self.vertex_shader,
            self.sampler,
            self.texture,
            self.uniform_buffer,
            self.upload_buffer,
        ]
    }
}

pub(crate) struct BorderResources {
    pub(in crate::render::worldrender) upload_buffer: Handle,
    pub(in crate::render::worldrender) uniform_buffer: Handle,
    pub(in crate::render::worldrender) texture: Handle,
    pub(in crate::render::worldrender) sampler: Handle,
    pub(in crate::render::worldrender) vertex_shader: Handle,
    pub(in crate::render::worldrender) fragment_shader: Handle,
    pub(in crate::render::worldrender) texture_view: Handle,
    pub(in crate::render::worldrender) resource_layout: Handle,
    pub(in crate::render::worldrender) resource_set: Handle,
    pub(in crate::render::worldrender) pipeline_layout: Handle,
    pub(in crate::render::worldrender) pipeline_depth_disabled: Handle,
    pub(in crate::render::worldrender) pipeline_depth_test_write: Handle,
}

impl BorderResources {
    pub(crate) fn handles_in_destroy_order(&self) -> [Handle; 12] {
        [
            self.pipeline_depth_test_write,
            self.pipeline_depth_disabled,
            self.pipeline_layout,
            self.resource_set,
            self.resource_layout,
            self.texture_view,
            self.fragment_shader,
            self.vertex_shader,
            self.sampler,
            self.texture,
            self.uniform_buffer,
            self.upload_buffer,
        ]
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq, Hash, Ord, PartialOrd)]
pub(crate) struct MaterialResourceKey {
    pub(in crate::render::worldrender) raster_y_direction: RasterYDirection,
    pub(in crate::render::worldrender) compact_dh_box: bool,
    pub(in crate::render::worldrender) stratum: u32,
    pub(in crate::render::worldrender) material_id: u32,
    pub(in crate::render::worldrender) texture_id: u32,
    /// Semantic producer family is part of resource identity even when two
    /// families currently share pixels.  This prevents weather/cloud/particle
    /// quads from being merged with generic textured work before their
    /// external transparency attachments are partitioned.
    pub(in crate::render::worldrender) source_program: u32,
    pub(in crate::render::worldrender) material_mode: u32,
    pub(in crate::render::worldrender) depth_policy: u32,
    pub(in crate::render::worldrender) cull_policy: u32,
    /// Winding is explicit geometry state. It participates in resource
    /// identity because the immutable index buffer realizes its orientation;
    /// the shader never rewrites copied semantic vertices.
    pub(in crate::render::worldrender) winding: u32,
    pub(in crate::render::worldrender) color_format: ColorFormat,
}

pub(crate) struct MaterialResources {
    /// None when the sampled image belongs to the explicit texture registry.
    /// Only standalone bundled material images own an upload/image allocation.
    pub(in crate::render::worldrender) upload_buffer: Option<Handle>,
    pub(in crate::render::worldrender) index_upload_buffer: Handle,
    pub(in crate::render::worldrender) index_buffer: Handle,
    pub(in crate::render::worldrender) texture: Handle,
    pub(in crate::render::worldrender) sampler: Handle,
    pub(in crate::render::worldrender) vertex_shader: Handle,
    pub(in crate::render::worldrender) fragment_shader: Handle,
    pub(in crate::render::worldrender) texture_view: Handle,
    pub(in crate::render::worldrender) resource_layout: Handle,
    /// Weather and particles declare Frozen's particle/lightmap contract. This
    /// second Rust-owned descriptor layout is deliberately absent from other
    /// material families, including menus where no world lightmap exists.
    pub(in crate::render::worldrender) lightmap_resource_layout: Option<Handle>,
    pub(in crate::render::worldrender) pipeline_layout: Handle,
    pub(in crate::render::worldrender) pipeline: Handle,
    pub(in crate::render::worldrender) data_slots: Vec<MaterialDataSlot>,
}

pub(crate) struct MaterialDataSlot {
    pub(in crate::render::worldrender) uniform_buffer: Handle,
    pub(in crate::render::worldrender) resource_set: Handle,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq, Ord, PartialOrd, Hash)]
pub(crate) struct MeshResourceKey {
    pub(in crate::render::worldrender) raster_y_direction: RasterYDirection,
    pub(in crate::render::worldrender) standard_item_foil: bool,
    pub(in crate::render::worldrender) view_layering: Option<crate::render::shared::view_layering::Projection>,
    pub(in crate::render::worldrender) decal_vertex_count: usize,
    pub(in crate::render::worldrender) g_buffer: bool,
    pub(in crate::render::worldrender) stratum: u32,
    pub(in crate::render::worldrender) mesh_key: u64,
    pub(in crate::render::worldrender) mesh_generation: u64,
    pub(in crate::render::worldrender) section_index: u32,
    pub(in crate::render::worldrender) material_id: u32,
    pub(in crate::render::worldrender) texture_id: u32,
    /// The compact direct terrain fragment has a separate immutable shader
    /// identity for textures whose validated animation table has one frame.
    /// Keep this in the resource key so a later atlas generation or animated
    /// asset can never alias the static pipeline.
    pub(in crate::render::worldrender) texture_animated: bool,
    pub(in crate::render::worldrender) material_mode: u32,
    pub(in crate::render::worldrender) winding: u32,
    pub(in crate::render::worldrender) depth_policy: u32,
    pub(in crate::render::worldrender) cull_policy: u32,
    pub(in crate::render::worldrender) color_format: ColorFormat,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub(crate) enum MeshVertexAbi {
    Rich80,
    DirectTerrain32,
}

impl MeshVertexAbi {
    pub(crate) const fn stride(self) -> usize {
        match self {
            Self::Rich80 => WORLD_MESH_GPU_VERTEX_BYTES,
            Self::DirectTerrain32 => WORLD_MESH_DIRECT_TERRAIN_VERTEX_BYTES,
        }
    }
}

/// Immutable geometry is shared by every material section of one copied mesh
/// generation. Section bindings still own their pipeline/resource-set state,
/// but duplicating the full vertex/index payload per section multiplies static
/// terrain residency by the number of layers in a section.
#[derive(Clone, Copy, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub(crate) struct MeshGeometryResourceKey {
    pub(in crate::render::worldrender) mesh_key: u64,
    pub(in crate::render::worldrender) mesh_generation: u64,
    pub(in crate::render::worldrender) vertex_abi: MeshVertexAbi,
}

impl MeshResourceKey {
    pub(crate) fn geometry_key(self) -> MeshGeometryResourceKey {
        self.geometry_key_for_abi(mesh_vertex_abi_for_builtin(self))
    }

    pub(crate) const fn geometry_key_for_abi(self, vertex_abi: MeshVertexAbi) -> MeshGeometryResourceKey {
        MeshGeometryResourceKey {
            mesh_key: self.mesh_key,
            mesh_generation: self.mesh_generation,
            vertex_abi,
        }
    }
}

#[derive(Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub(crate) struct MeshPipelineResourceKey {
    pub(in crate::render::worldrender) vertex_abi: MeshVertexAbi,
    pub(in crate::render::worldrender) raster_y_direction: RasterYDirection,
    pub(in crate::render::worldrender) g_buffer: bool,
    pub(in crate::render::worldrender) material_mode: u32,
    pub(in crate::render::worldrender) winding: u32,
    pub(in crate::render::worldrender) depth_policy: u32,
    pub(in crate::render::worldrender) cull_policy: u32,
    pub(in crate::render::worldrender) color_format: ColorFormat,
    /// The source-derived program identity is part of the persistent pipeline
    /// key. Two compatible resource layouts must not accidentally share a
    /// pipeline when their shader source differs.
    pub(in crate::render::worldrender) shader_program_identity: ProgramIdentity,
    /// Optional shader-pack semantic resources are always assigned after the
    /// mesh/material layout. This is a GAL layout handle, never a backend
    /// descriptor or native binding.
    pub(in crate::render::worldrender) shader_resource_layout: Option<Handle>,
}

/// One source-derived shader variant shares the owned mesh geometry but has
/// its own layout-compatible set-0 resource set and pipeline. This keeps
/// source program selection entirely inside Rust and prevents a set created
/// for the builtin layout from being rebound against a distinct pipeline.
#[derive(Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub(crate) struct SourceMeshResourceKey {
    pub(in crate::render::worldrender) mesh: MeshResourceKey,
    pub(in crate::render::worldrender) shader_program_identity: ProgramIdentity,
    pub(in crate::render::worldrender) shader_resource_layout: Option<Handle>,
    pub(in crate::render::worldrender) shader_resource_generation: u64,
}

pub(crate) struct MeshPipelineResources {
    pub(in crate::render::worldrender) vertex_observation: Option<diagnostics::vertex_observation::Observation>,
    pub(in crate::render::worldrender) vertex_shader: Handle,
    pub(in crate::render::worldrender) fragment_shader: Handle,
    pub(in crate::render::worldrender) shadow_vertex_shader: Option<Handle>,
    pub(in crate::render::worldrender) shadow_fragment_shader: Option<Handle>,
    pub(in crate::render::worldrender) resource_layout: Handle,
    pub(in crate::render::worldrender) pipeline_layout: Handle,
    pub(in crate::render::worldrender) pipeline: Handle,
    pub(in crate::render::worldrender) shadow_pipeline: Option<Handle>,
}

pub(crate) struct MeshResources {
    pub(in crate::render::worldrender) geometry_key: MeshGeometryResourceKey,
    pub(in crate::render::worldrender) vertex_buffer: Handle,
    pub(in crate::render::worldrender) vertex_offset: u64,
    pub(in crate::render::worldrender) vertex_range: u64,
    pub(in crate::render::worldrender) vertex_stride: usize,
    pub(in crate::render::worldrender) index_buffer: Handle,
    pub(in crate::render::worldrender) index_offset: u64,
    pub(in crate::render::worldrender) index_type: IndexType,
    pub(in crate::render::worldrender) pipeline_layout: Handle,
    pub(in crate::render::worldrender) pipeline: Handle,
    pub(in crate::render::worldrender) shadow_pipeline: Option<Handle>,
    /// Per-mesh binding, created on first use by `ensure_mesh_resource_set`.
    /// Ordinary opaque/cutout terrain draws through the shared page set and
    /// never needs one, so creating it eagerly cost a descriptor set per new
    /// section key while moving through the world.
    pub(in crate::render::worldrender) resource_set: Option<Handle>,
    /// Inputs for creating `resource_set` later; owned by the pipeline cache.
    pub(in crate::render::worldrender) resource_layout: Handle,
    pub(in crate::render::worldrender) observation_buffer: Option<Handle>,
    /// Borrowed from `mesh_page_resource_sets`; cleared whenever that owner
    /// retires its sets or the instance-stream binding changes.
    pub(in crate::render::worldrender) page_resource_set: Option<Handle>,
}

impl MeshPipelineResources {
    pub(crate) fn handles_in_destroy_order(&self) -> Vec<Handle> {
        [
            self.shadow_pipeline,
            Some(self.pipeline),
            Some(self.pipeline_layout),
            Some(self.resource_layout),
            self.shadow_fragment_shader,
            self.shadow_vertex_shader,
            Some(self.fragment_shader),
            Some(self.vertex_shader),
        ]
        .into_iter()
        .flatten()
        .chain(
            self.vertex_observation
                .iter()
                .flat_map(|observation| observation.handles()),
        )
        .collect()
    }
}

impl MeshResources {
    pub(crate) fn handles_in_destroy_order(&self) -> Vec<Handle> {
        self.resource_set.into_iter().collect()
    }
}

pub(crate) struct MeshTextureResources {
    pub(in crate::render::worldrender) upload_buffer: Handle,
    pub(in crate::render::worldrender) texture: Handle,
    pub(in crate::render::worldrender) sampler: Handle,
    pub(in crate::render::worldrender) view: Handle,
    pub(in crate::render::worldrender) width: u32,
    pub(in crate::render::worldrender) height: u32,
    pub(in crate::render::worldrender) mip_levels: u32,
}

impl MeshTextureResources {
    pub(crate) fn handles_in_destroy_order(&self) -> [Handle; 4] {
        [self.view, self.sampler, self.texture, self.upload_buffer]
    }
}

impl MaterialResources {
    pub(crate) fn handles_in_destroy_order(&self) -> Vec<Handle> {
        // Resource sets retain the layout, sampled view, sampler, and their
        // per-slot uniform buffers. Retire those dependents before releasing
        // the pipeline/layout or backing resources so immediate-lifetime
        // validation remains valid as well as deferred GAL retirement.
        let mut handles = Vec::with_capacity(self.data_slots.len() * 2 + 11);
        for slot in self.data_slots.iter().rev() {
            handles.push(slot.resource_set);
            handles.push(slot.uniform_buffer);
        }
        handles.extend([
            self.pipeline,
            self.pipeline_layout,
            self.resource_layout,
            self.texture_view,
            self.fragment_shader,
            self.vertex_shader,
            self.sampler,
        ]);
        if self.upload_buffer.is_some() {
            handles.push(self.texture);
        }
        handles.extend([self.index_buffer, self.index_upload_buffer]);
        if let Some(upload_buffer) = self.upload_buffer {
            handles.push(upload_buffer);
        }
        handles
    }
}

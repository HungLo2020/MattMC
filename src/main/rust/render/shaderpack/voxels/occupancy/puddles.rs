//! Puddle occupancy voxelization, GPU resources and runtime.

use super::*;

/// Semantic description of Complementary's camera-relative puddle exclusion
/// field. It is deliberately independent from the colored-light D3 volume:
/// the source writes a fixed 128x128 unsigned image from shadow-scene
/// coordinates, then samples it while shading opaque/cutout terrain.
#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) struct PuddleOccupancyDescriptor {
    pub shader_pack_generation: u64,
    pub world_generation: u64,
    pub resource_generation: u64,
    pub camera_fraction: [f32; 3],
    /// Column-major semantic transform corresponding to the source expression
    /// `shadowModelViewInverse * gl_ModelViewMatrix`. It is copied uniform
    /// data, never a backend matrix or Iris state object.
    pub shadow_scene_from_world: [f32; 16],
}

impl PuddleOccupancyDescriptor {
    pub const EXTENT: u32 = 128;

    pub(super) fn validate(self) -> GalResult<()> {
        if self.shader_pack_generation == 0
            || self.world_generation == 0
            || self.resource_generation == 0
        {
            return Err(GalError::invalid_argument(
                "puddle occupancy descriptor requires non-zero shader-pack, world, and resource generations",
            ));
        }
        if self
            .camera_fraction
            .iter()
            .any(|value| !value.is_finite() || !(0.0..1.0).contains(value))
            || self
                .shadow_scene_from_world
                .iter()
                .any(|value| !value.is_finite())
        {
            return Err(GalError::invalid_argument(
                "puddle occupancy descriptor requires finite camera fraction and shadow-scene transform",
            ));
        }
        Ok(())
    }
}

/// Bounded diagnostic facts from one semantic puddle voxelization update.
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub(crate) struct PuddleOccupancyUpdateStats {
    pub translucent_samples: u32,
    pub water_samples_skipped: u32,
    pub below_scene_samples_skipped: u32,
    pub out_of_bounds_samples_skipped: u32,
    pub changed_texels: u32,
}

/// Bounded, handle-free state for source-route admission diagnostics.
/// This distinguishes an uninitialized puddle field from a resource snapshot
/// assembly problem without exposing backend residency details.
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub(crate) struct TerrainPuddleDiagnosticState {
    pub ready: bool,
    pub submission_pending: bool,
    pub initialized: bool,
    pub changed_texels: u32,
}

/// Rust-owned CPU semantic generation for the source-derived puddle field.
/// GPU allocation/upload and source-program binding remain a later private
/// runtime step; this type exists so those layers consume an exact, tested
/// field instead of replaying source GLSL or guessing terrain material layers.
#[derive(Clone, Debug)]
pub(crate) struct TerrainPuddleVoxelizer {
    pub(super) descriptor: PuddleOccupancyDescriptor,
    pub(super) texels: Vec<u8>,
}

/// Private GAL residency for one completed puddle field. Its handles never
/// cross FFI; a later shader-runtime transaction may expose them only through
/// the `PuddleOccupancy` semantic role after submission confirmation.
#[derive(Debug)]
pub(crate) struct TerrainPuddleGpuResources {
    pub(super) texture: Handle,
    pub(super) view: Handle,
    pub(super) sampler: Handle,
    pub(super) combined_sampler: Handle,
    pub(super) upload_buffer: Handle,
    pub(super) descriptor: PuddleOccupancyDescriptor,
    pub(super) initialized: bool,
    pub(super) upload_pending: bool,
}

/// One private, source-derived puddle field generation. Unlike the colored
/// voxel-light volume this has no compute passes: Complementary's shadow
/// stage writes a fixed unsigned 2D occupancy image and terrain later samples
/// it. The runtime keeps the copied semantic reconstruction and GAL upload in
/// the same submission transaction so a rejected frame cannot expose a new
/// camera mapping or a partially uploaded field.
#[derive(Debug)]
pub(crate) struct TerrainPuddleRuntime {
    pub(super) voxelizer: TerrainPuddleVoxelizer,
    pub(super) resources: TerrainPuddleGpuResources,
    pub(super) pending_voxelizer_rollback: Option<TerrainPuddleVoxelizer>,
    pub(super) pending_resource_descriptor_rollback: Option<PuddleOccupancyDescriptor>,
    pub(super) submission_pending: bool,
    pub(super) last_update: PuddleOccupancyUpdateStats,
}

impl TerrainPuddleVoxelizer {
    pub(crate) fn new(descriptor: PuddleOccupancyDescriptor) -> GalResult<Self> {
        descriptor.validate()?;
        let len = usize::try_from(PuddleOccupancyDescriptor::EXTENT)
            .ok()
            .and_then(|extent| extent.checked_mul(extent))
            .ok_or_else(|| GalError::invalid_argument("puddle occupancy extent overflows"))?;
        Ok(Self {
            descriptor,
            texels: vec![0; len],
        })
    }

    pub(crate) fn descriptor(&self) -> PuddleOccupancyDescriptor {
        self.descriptor
    }

    pub(crate) fn texels(&self) -> &[u8] {
        &self.texels
    }

    pub(super) fn replace_descriptor(&mut self, descriptor: PuddleOccupancyDescriptor) -> GalResult<()> {
        descriptor.validate()?;
        if !self.descriptor.resource_compatible_with(descriptor) {
            return Err(GalError::invalid_argument(
                "puddle occupancy descriptor changed resource generation without replacing residency",
            ));
        }
        self.descriptor = descriptor;
        Ok(())
    }

    /// Rebuilds the exact fixed-size source field from copied translucent
    /// mesh indices. Source code emits value 10 for non-water vertices inside
    /// the volume and above the shadow-scene floor; repeated quad vertices
    /// are idempotent writes to the same unsigned texel.
    pub(crate) fn rebuild_from_meshes(
        &mut self,
        meshes: impl IntoIterator<Item = TerrainVoxelSourceMesh>,
    ) -> GalResult<PuddleOccupancyUpdateStats> {
        self.descriptor.validate()?;
        let mut next = vec![0; self.texels.len()];
        let mut stats = PuddleOccupancyUpdateStats::default();
        for mesh in meshes {
            for index in mesh.translucent_indices.iter().copied() {
                let vertex = mesh.vertices.get(index as usize).ok_or_else(|| {
                    GalError::invalid_argument(format!(
                        "puddle occupancy mesh {} generation {} references translucent vertex {} outside {} vertices",
                        mesh.mesh_key,
                        mesh.mesh_generation,
                        index,
                        mesh.vertices.len()
                    ))
                })?;
                stats.translucent_samples = stats.translucent_samples.saturating_add(1);
                if vertex.shader_material_id == 32_000 {
                    stats.water_samples_skipped = stats.water_samples_skipped.saturating_add(1);
                    continue;
                }
                let world = TerrainVoxelSample {
                    vertex_position: vertex.position,
                    mid_block_packed: vertex.mid_block_packed,
                    shader_material_id: vertex.shader_material_id,
                    model_transform: mesh.transform,
                }
                .world_block_center()?;
                let scene = transform_point(self.descriptor.shadow_scene_from_world, world)?;
                if scene[1] < -3.5 {
                    stats.below_scene_samples_skipped =
                        stats.below_scene_samples_skipped.saturating_add(1);
                    continue;
                }
                let coordinate = [
                    (scene[0]
                        + self.descriptor.camera_fraction[0]
                        + PuddleOccupancyDescriptor::EXTENT as f32 * 0.5)
                        .floor() as i32,
                    (scene[2]
                        + self.descriptor.camera_fraction[2]
                        + PuddleOccupancyDescriptor::EXTENT as f32 * 0.5)
                        .floor() as i32,
                ];
                if coordinate
                    .iter()
                    .any(|value| *value < 0 || *value >= PuddleOccupancyDescriptor::EXTENT as i32)
                {
                    stats.out_of_bounds_samples_skipped =
                        stats.out_of_bounds_samples_skipped.saturating_add(1);
                    continue;
                }
                let offset = coordinate[1] as usize * PuddleOccupancyDescriptor::EXTENT as usize
                    + coordinate[0] as usize;
                next[offset] = 10;
            }
        }
        stats.changed_texels = self
            .texels
            .iter()
            .zip(&next)
            .filter(|(previous, next)| previous != next)
            .count()
            .try_into()
            .unwrap_or(u32::MAX);
        self.texels = next;
        Ok(stats)
    }
}

impl TerrainPuddleGpuResources {
    pub(crate) fn create(
        gal: &mut VulkanicGal,
        descriptor: PuddleOccupancyDescriptor,
    ) -> GalResult<Self> {
        descriptor.validate()?;
        let extent = Extent3d {
            width: PuddleOccupancyDescriptor::EXTENT,
            height: PuddleOccupancyDescriptor::EXTENT,
            depth: 1,
        };
        let label = format!(
            "shader-pack.puddle.{}.{}.{}",
            descriptor.shader_pack_generation,
            descriptor.world_generation,
            descriptor.resource_generation
        );
        let texture = gal.create_texture(TextureDesc {
            label: format!("{label}.texture"),
            dimension: TextureDimension::D2,
            format: TextureFormat::R8Uint,
            extent,
            mip_levels: 1,
            array_layers: 1,
            usages: vec![
                TextureUsage::Sampled,
                TextureUsage::Storage,
                TextureUsage::TransferDst,
            ],
        })?;
        let view = match gal.create_texture_view(TextureViewDesc {
            label: format!("{label}.view"),
            texture,
            format: TextureFormat::R8Uint,
            base_mip: 0,
            mip_count: 1,
            base_layer: 0,
            layer_count: 1,
        }) {
            Ok(value) => value,
            Err(error) => {
                let _ = gal.retire(texture);
                return Err(error);
            }
        };
        let sampler = match gal.create_sampler(SamplerDesc {
            label: format!("{label}.sampler"),
            min_filter: SamplerFilter::Nearest,
            mag_filter: SamplerFilter::Nearest,
            mip_filter: SamplerFilter::Nearest,
            address_u: SamplerAddressMode::ClampToEdge,
            address_v: SamplerAddressMode::ClampToEdge,
            address_w: SamplerAddressMode::ClampToEdge,
            comparison: None,
        }) {
            Ok(value) => value,
            Err(error) => {
                let _ = gal.retire(view);
                let _ = gal.retire(texture);
                return Err(error);
            }
        };
        let combined_sampler =
            match gal.create_combined_texture_sampler(CombinedTextureSamplerDesc {
                label: format!("{label}.combined"),
                texture_view: view,
                sampler,
            }) {
                Ok(value) => value,
                Err(error) => {
                    let _ = gal.retire(sampler);
                    let _ = gal.retire(view);
                    let _ = gal.retire(texture);
                    return Err(error);
                }
            };
        let upload_buffer = match gal.create_buffer(BufferDesc {
            label: format!("{label}.upload"),
            size: u64::from(PuddleOccupancyDescriptor::EXTENT).pow(2),
            memory: MemoryDomain::Upload,
            usages: vec![BufferUsage::HostWrite, BufferUsage::TransferSrc],
        }) {
            Ok(value) => value,
            Err(error) => {
                let _ = gal.retire(combined_sampler);
                let _ = gal.retire(sampler);
                let _ = gal.retire(view);
                let _ = gal.retire(texture);
                return Err(error);
            }
        };
        Ok(Self {
            texture,
            view,
            sampler,
            combined_sampler,
            upload_buffer,
            descriptor,
            initialized: false,
            upload_pending: false,
        })
    }

    pub(crate) fn append_upload(
        &mut self,
        texels: &[u8],
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<()> {
        if self.upload_pending
            || texels.len()
                != PuddleOccupancyDescriptor::EXTENT as usize
                    * PuddleOccupancyDescriptor::EXTENT as usize
        {
            return Err(GalError::invalid_argument(
                "puddle occupancy upload is pending or has an invalid fixed extent",
            ));
        }
        operations.push(CommandOp::HostWriteBuffer {
            buffer: self.upload_buffer,
            offset: 0,
            data: texels.to_vec(),
        });
        operations.push(CommandOp::Barrier(resource_barrier(
            self.upload_buffer,
            None,
            TextureUsageState::TransferDst,
            TextureUsageState::TransferSrc,
        )));
        operations.push(CommandOp::Barrier(resource_barrier(
            self.texture,
            None,
            if self.initialized {
                TextureUsageState::ShaderRead
            } else {
                TextureUsageState::Undefined
            },
            TextureUsageState::TransferDst,
        )));
        operations.push(CommandOp::CopyBufferToTexture(BufferImageCopyRegion {
            buffer: self.upload_buffer,
            buffer_offset: 0,
            bytes_per_row: PuddleOccupancyDescriptor::EXTENT,
            rows_per_image: PuddleOccupancyDescriptor::EXTENT,
            texture: self.texture,
            texture_mip: 0,
            texture_layer: 0,
            texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
            extent: Extent3d {
                width: PuddleOccupancyDescriptor::EXTENT,
                height: PuddleOccupancyDescriptor::EXTENT,
                depth: 1,
            },
        }));
        operations.push(CommandOp::Barrier(resource_barrier(
            self.texture,
            None,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        )));
        self.upload_pending = true;
        Ok(())
    }

    pub(crate) fn confirm_submission(&mut self) -> GalResult<()> {
        if !self.upload_pending {
            return Err(GalError::invalid_argument(
                "no puddle occupancy upload is pending confirmation",
            ));
        }
        self.initialized = true;
        self.upload_pending = false;
        Ok(())
    }

    pub(crate) fn discard_pending_submission(&mut self) {
        self.upload_pending = false;
    }

    pub(super) fn replace_descriptor(&mut self, descriptor: PuddleOccupancyDescriptor) -> GalResult<()> {
        descriptor.validate()?;
        if !self.descriptor.resource_compatible_with(descriptor) {
            return Err(GalError::invalid_argument(
                "puddle occupancy residency cannot adopt a different resource generation",
            ));
        }
        self.descriptor = descriptor;
        Ok(())
    }

    pub(crate) fn destroy(self, gal: &mut VulkanicGal) -> GalResult<()> {
        gal.destroy(self.upload_buffer)?;
        gal.destroy(self.combined_sampler)?;
        gal.destroy(self.sampler)?;
        gal.destroy(self.view)?;
        gal.destroy(self.texture)
    }
}

impl PuddleOccupancyDescriptor {
    pub(super) fn resource_compatible_with(self, other: Self) -> bool {
        self.shader_pack_generation == other.shader_pack_generation
            && self.world_generation == other.world_generation
            && self.resource_generation == other.resource_generation
    }
}

impl TerrainPuddleRuntime {
    pub(crate) fn create(
        gal: &mut VulkanicGal,
        descriptor: PuddleOccupancyDescriptor,
    ) -> GalResult<Self> {
        let voxelizer = TerrainPuddleVoxelizer::new(descriptor)?;
        let resources = TerrainPuddleGpuResources::create(gal, descriptor)?;
        Ok(Self {
            voxelizer,
            resources,
            pending_voxelizer_rollback: None,
            pending_resource_descriptor_rollback: None,
            submission_pending: false,
            last_update: PuddleOccupancyUpdateStats::default(),
        })
    }

    pub(crate) fn descriptor(&self) -> PuddleOccupancyDescriptor {
        self.voxelizer.descriptor()
    }

    pub(crate) fn resource_compatible_with(&self, descriptor: PuddleOccupancyDescriptor) -> bool {
        self.descriptor().resource_compatible_with(descriptor)
    }

    pub(crate) fn is_ready(&self) -> bool {
        self.resources.initialized && !self.submission_pending
    }

    pub(crate) fn has_pending_submission(&self) -> bool {
        self.submission_pending
    }

    pub(crate) fn last_update(&self) -> PuddleOccupancyUpdateStats {
        self.last_update
    }

    pub(crate) fn diagnostic_state(&self) -> TerrainPuddleDiagnosticState {
        TerrainPuddleDiagnosticState {
            ready: self.is_ready(),
            submission_pending: self.submission_pending,
            initialized: self.resources.initialized,
            changed_texels: self.last_update.changed_texels,
        }
    }

    pub(crate) fn append_terrain_source_snapshot(
        &mut self,
        descriptor: PuddleOccupancyDescriptor,
        meshes: impl IntoIterator<Item = TerrainVoxelSourceMesh>,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<PuddleOccupancyUpdateStats> {
        if self.submission_pending {
            return Err(GalError::invalid_argument(
                "puddle occupancy runtime already has a submission awaiting confirmation",
            ));
        }
        if !self.resource_compatible_with(descriptor) {
            return Err(GalError::invalid_argument(
                "puddle occupancy runtime received a mismatched resource generation",
            ));
        }
        let previous_voxelizer = self.voxelizer.clone();
        let previous_resource_descriptor = self.resources.descriptor;
        let result = (|| {
            self.voxelizer.replace_descriptor(descriptor)?;
            let stats = self.voxelizer.rebuild_from_meshes(meshes)?;
            if !self.resources.initialized || stats.changed_texels != 0 {
                self.resources
                    .append_upload(self.voxelizer.texels(), operations)?;
                self.pending_voxelizer_rollback = Some(previous_voxelizer.clone());
                self.pending_resource_descriptor_rollback = Some(previous_resource_descriptor);
                self.submission_pending = true;
            } else {
                self.resources.replace_descriptor(descriptor)?;
            }
            self.last_update = stats;
            Ok(stats)
        })();
        if result.is_err() {
            self.voxelizer = previous_voxelizer;
            let _ = self
                .resources
                .replace_descriptor(previous_resource_descriptor);
            self.pending_voxelizer_rollback = None;
            self.pending_resource_descriptor_rollback = None;
            self.submission_pending = false;
        }
        result
    }

    pub(crate) fn confirm_submission(&mut self) -> GalResult<()> {
        if !self.submission_pending {
            return Err(GalError::invalid_argument(
                "no puddle occupancy submission is pending confirmation",
            ));
        }
        self.resources.confirm_submission()?;
        self.resources
            .replace_descriptor(self.voxelizer.descriptor())?;
        self.pending_voxelizer_rollback = None;
        self.pending_resource_descriptor_rollback = None;
        self.submission_pending = false;
        Ok(())
    }

    pub(crate) fn discard_submission(&mut self) {
        if !self.submission_pending {
            return;
        }
        self.resources.discard_pending_submission();
        if let Some(previous) = self.pending_voxelizer_rollback.take() {
            self.voxelizer = previous;
        }
        if let Some(previous) = self.pending_resource_descriptor_rollback.take() {
            let _ = self.resources.replace_descriptor(previous);
        }
        self.submission_pending = false;
    }

    pub(crate) fn semantic_resource_set(&self) -> GalResult<TerrainSourceOwnedResourceSet> {
        if !self.is_ready() {
            return Err(GalError::invalid_argument(
                "puddle occupancy resources are not confirmed for sampling",
            ));
        }
        self.semantic_resource_set_unchecked()
    }

    pub(crate) fn semantic_resource_set_for_pending_submission(
        &self,
    ) -> GalResult<TerrainSourceOwnedResourceSet> {
        if !self.submission_pending {
            return Err(GalError::invalid_argument(
                "puddle occupancy pending sampler set requires the exact pending submission",
            ));
        }
        self.semantic_resource_set_unchecked()
    }

    pub(super) fn semantic_resource_set_unchecked(&self) -> GalResult<TerrainSourceOwnedResourceSet> {
        let descriptor = self.descriptor();
        TerrainSourceOwnedResourceSet::with_storage_resources(
            TerrainSourceResourceAvailabilitySet::new(
                descriptor.shader_pack_generation,
                descriptor.world_generation,
                [TerrainSourceResourceAvailability {
                    role: TerrainSourceResourceRole::PuddleOccupancy,
                    shape: TerrainSourceSampledResourceShape::UnsignedTexture2d,
                    resource_generation: descriptor.resource_generation,
                }],
            )?,
            [TerrainSourceOwnedResource {
                role: TerrainSourceResourceRole::PuddleOccupancy,
                combined_sampler: self.resources.combined_sampler,
            }],
            [TerrainSourceOwnedStorageResource {
                role: TerrainSourceResourceRole::PuddleOccupancy,
                texture_view: self.resources.view,
            }],
        )
    }

    pub(crate) fn destroy(self, gal: &mut VulkanicGal) -> GalResult<()> {
        self.resources.destroy(gal)
    }
}

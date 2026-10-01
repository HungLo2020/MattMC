//! Colored-light runtime and voxel-light sampling resources.

use super::*;

/// Private, Rust-owned occupancy plus colored-light preparation. It combines
/// the independently validated D3 occupancy upload, source-derived emission
/// tables, and ping-pong compute lifecycle into one submission transaction.
/// It still has no terrain-program binding or selected-source admission path.
#[derive(Debug)]
pub struct TerrainColoredLightRuntime {
    pub(super) occupancy: TerrainOccupancyRuntime,
    pub(super) flood_fill: TerrainFloodFillGpuResources,
    pub(super) compute: TerrainFloodFillComputeResources,
    pub(super) sampling: TerrainVoxelLightSamplingResources,
    pub(super) emission: VoxelEmissionTable,
    pub(super) submission_pending: bool,
    pub(super) pending_compute_rollback: Option<TerrainFloodFillComputeResources>,
    pub(super) pending_flood_mapping_rollback: Option<VoxelLightVolumeDescriptor>,
    // Bounded accounting from the last semantic voxelization pass that
    // actually inspected source samples. This is diagnostics only: it keeps
    // an unchanged snapshot from erasing the evidence needed to distinguish
    // unsupported materials from out-of-volume geometry.
    pub(super) last_occupancy_update: TerrainOccupancyUpdateStats,
}

/// Private, backend-neutral draw binding for one confirmed semantic volume.
/// The handles stay wholly inside Rust; the frontend will eventually place
/// this layout after its existing mesh layout when it lowers a selected source
/// program. No Java, FFI, or backend-native object sees this contract.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) struct TerrainVoxelLightSamplingBinding {
    pub resource_layout: Handle,
    pub resource_set: Handle,
    pub active_light_field: VoxelLightVolumeKind,
    pub resource_generation: u64,
}

/// Bounded, semantic-only visibility into colored-light preparation. It is
/// intentionally limited to transaction readiness facts so graphics audit
/// rows can identify the first unmet dependency without exposing any texture,
/// descriptor, or backend object.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) struct TerrainColoredLightDiagnosticState {
    /// Semantic extent of the owned volume. These fields intentionally expose
    /// no backend allocation or handle details; they let capture rows separate
    /// the declared D3 footprint from accidental per-frame residency growth.
    pub extent_width: u32,
    pub extent_height: u32,
    pub extent_depth: u32,
    pub expected_owned_bytes: u64,
    pub mesh_snapshot_count: usize,
    pub submission_pending: bool,
    pub occupancy_upload_pending: bool,
    pub occupancy_initialized: bool,
    pub emission_ready: bool,
    pub tint_ready: bool,
    pub sampling_mapping_ready: bool,
    pub compute_even_initialized: bool,
    pub compute_odd_initialized: bool,
    pub pending_initialization_frame: Option<u64>,
    pub pending_propagation_frame: Option<u64>,
    pub pending_compute_output_ready: bool,
    pub frame_ready: bool,
    pub occupancy_input_samples: u32,
    pub occupancy_emitted_samples: u32,
    pub occupancy_overwritten_samples: u32,
    pub occupancy_skipped_non_solid_samples: u32,
    pub occupancy_skipped_out_of_bounds_samples: u32,
    pub occupancy_changed_voxels: u32,
    pub occupancy_uploaded_bytes: u32,
}

#[derive(Debug)]
pub(super) struct TerrainVoxelLightSamplingResources {
    pub(super) resource_layout: Handle,
    pub(super) even_resource_set: Handle,
    pub(super) odd_resource_set: Handle,
    pub(super) occupancy_sampler: Handle,
    pub(super) even_light_sampler: Handle,
    pub(super) odd_light_sampler: Handle,
    pub(super) mapping_buffer: Handle,
    pub(super) confirmed_mapping: Option<VoxelLightVolumeMapping>,
    pub(super) mapping_upload_pending: bool,
}

impl TerrainVoxelLightSamplingResources {
    pub(super) fn create(
        gal: &mut VulkanicGal,
        occupancy: &TerrainOccupancyGpuResources,
        flood_fill: &TerrainFloodFillGpuResources,
    ) -> GalResult<Self> {
        if occupancy.descriptor != flood_fill.descriptor {
            return Err(GalError::invalid_argument(
                "voxel-light sampling resources require matching D3 generations",
            ));
        }
        let label = occupancy.descriptor.identity.as_str();
        let occupancy_sampler =
            gal.create_combined_texture_sampler(CombinedTextureSamplerDesc {
                label: format!("{label}.terrain-volume.occupancy-sampler"),
                texture_view: occupancy.view,
                sampler: flood_fill.sampler,
            })?;
        let even_light_sampler =
            match gal.create_combined_texture_sampler(CombinedTextureSamplerDesc {
                label: format!("{label}.terrain-volume.even-light-sampler"),
                texture_view: flood_fill.even_view,
                sampler: flood_fill.sampler,
            }) {
                Ok(handle) => handle,
                Err(error) => {
                    let _ = gal.destroy(occupancy_sampler);
                    return Err(error);
                }
            };
        let odd_light_sampler =
            match gal.create_combined_texture_sampler(CombinedTextureSamplerDesc {
                label: format!("{label}.terrain-volume.odd-light-sampler"),
                texture_view: flood_fill.odd_view,
                sampler: flood_fill.sampler,
            }) {
                Ok(handle) => handle,
                Err(error) => {
                    let _ = gal.destroy(even_light_sampler);
                    let _ = gal.destroy(occupancy_sampler);
                    return Err(error);
                }
            };
        let resource_layout = match gal.create_resource_layout(ResourceLayoutDesc {
            label: format!("{label}.terrain-volume.sample-layout"),
            bindings: vec![
                ResourceBindingDesc {
                    binding: 0,
                    kind: ResourceBindingKind::CombinedTextureSampler,
                    stages: PipelineStageFlags::DRAW,
                    array_count: 1,
                    optional: false,
                    dynamic_offset_count: 0,
                },
                ResourceBindingDesc {
                    binding: 1,
                    kind: ResourceBindingKind::CombinedTextureSampler,
                    stages: PipelineStageFlags::DRAW,
                    array_count: 1,
                    optional: false,
                    dynamic_offset_count: 0,
                },
                ResourceBindingDesc {
                    binding: 2,
                    kind: ResourceBindingKind::UniformBuffer,
                    stages: PipelineStageFlags::DRAW,
                    array_count: 1,
                    optional: false,
                    dynamic_offset_count: 0,
                },
            ],
        }) {
            Ok(handle) => handle,
            Err(error) => {
                let _ = gal.destroy(odd_light_sampler);
                let _ = gal.destroy(even_light_sampler);
                let _ = gal.destroy(occupancy_sampler);
                return Err(error);
            }
        };
        let mapping_buffer = match gal.create_buffer(BufferDesc {
            label: format!("{label}.terrain-volume.mapping"),
            size: VoxelLightVolumeShaderMapping::STD140_SIZE as u64,
            memory: MemoryDomain::Upload,
            usages: vec![BufferUsage::Uniform, BufferUsage::HostWrite],
        }) {
            Ok(handle) => handle,
            Err(error) => {
                let _ = gal.destroy(resource_layout);
                let _ = gal.destroy(odd_light_sampler);
                let _ = gal.destroy(even_light_sampler);
                let _ = gal.destroy(occupancy_sampler);
                return Err(error);
            }
        };
        let create_set = |gal: &mut VulkanicGal, name: &str, light_sampler: Handle| {
            gal.create_resource_set(ResourceSetDesc {
                label: format!("{label}.terrain-volume.{name}.set"),
                layout: resource_layout,
                bindings: vec![
                    ResourceBinding {
                        binding: 0,
                        array_index: 0,
                        resource: occupancy_sampler,
                        kind: ResourceBindingKind::CombinedTextureSampler,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: None,
                    },
                    ResourceBinding {
                        binding: 1,
                        array_index: 0,
                        resource: light_sampler,
                        kind: ResourceBindingKind::CombinedTextureSampler,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: None,
                    },
                    ResourceBinding {
                        binding: 2,
                        array_index: 0,
                        resource: mapping_buffer,
                        kind: ResourceBindingKind::UniformBuffer,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: Some(VoxelLightVolumeShaderMapping::STD140_SIZE as u64),
                    },
                ],
            })
        };
        let even_resource_set = match create_set(gal, "even", even_light_sampler) {
            Ok(set) => set,
            Err(error) => {
                let _ = gal.destroy(mapping_buffer);
                let _ = gal.destroy(resource_layout);
                let _ = gal.destroy(odd_light_sampler);
                let _ = gal.destroy(even_light_sampler);
                let _ = gal.destroy(occupancy_sampler);
                return Err(error);
            }
        };
        let odd_resource_set = match create_set(gal, "odd", odd_light_sampler) {
            Ok(set) => set,
            Err(error) => {
                let _ = gal.destroy(even_resource_set);
                let _ = gal.destroy(mapping_buffer);
                let _ = gal.destroy(resource_layout);
                let _ = gal.destroy(odd_light_sampler);
                let _ = gal.destroy(even_light_sampler);
                let _ = gal.destroy(occupancy_sampler);
                return Err(error);
            }
        };
        Ok(Self {
            resource_layout,
            even_resource_set,
            odd_resource_set,
            occupancy_sampler,
            even_light_sampler,
            odd_light_sampler,
            mapping_buffer,
            confirmed_mapping: None,
            mapping_upload_pending: false,
        })
    }

    pub(super) fn append_mapping_upload(
        &mut self,
        descriptor: &VoxelLightVolumeDescriptor,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<bool> {
        descriptor.validate()?;
        if self.mapping_upload_pending {
            return Err(GalError::invalid_argument(
                "terrain voxel-light mapping upload is already pending confirmation",
            ));
        }
        if self.confirmed_mapping.as_ref() == Some(&descriptor.mapping) {
            return Ok(false);
        }
        operations.push(CommandOp::HostWriteBuffer {
            buffer: self.mapping_buffer,
            offset: 0,
            data: VoxelLightVolumeShaderMapping::from_descriptor(descriptor)?
                .std140_bytes()
                .to_vec(),
        });
        self.mapping_upload_pending = true;
        Ok(true)
    }

    pub(super) fn mapping_ready_for(&self, descriptor: &VoxelLightVolumeDescriptor) -> bool {
        !self.mapping_upload_pending && self.confirmed_mapping.as_ref() == Some(&descriptor.mapping)
    }

    pub(super) fn confirm_mapping_submission(
        &mut self,
        descriptor: &VoxelLightVolumeDescriptor,
    ) -> GalResult<()> {
        if self.mapping_upload_pending {
            self.confirmed_mapping = Some(descriptor.mapping);
            self.mapping_upload_pending = false;
        }
        Ok(())
    }

    pub(super) fn discard_pending_mapping_upload(&mut self) {
        self.mapping_upload_pending = false;
    }

    pub(super) fn binding_for_frame(
        &self,
        readiness: &VoxelLightVolumeReadiness,
        frame_counter: u64,
    ) -> GalResult<TerrainVoxelLightSamplingBinding> {
        let binding = readiness.binding_for_frame(frame_counter)?;
        let resource_set = match binding.active_light_field {
            VoxelLightVolumeKind::FloodFillEven => self.even_resource_set,
            VoxelLightVolumeKind::FloodFillOdd => self.odd_resource_set,
            VoxelLightVolumeKind::Occupancy => {
                return Err(GalError::invalid_argument(
                    "terrain voxel-light sampling cannot select the occupancy field as colored light",
                ));
            }
        };
        Ok(TerrainVoxelLightSamplingBinding {
            resource_layout: self.resource_layout,
            resource_set,
            active_light_field: binding.active_light_field,
            resource_generation: binding.resource_generation,
        })
    }

    /// Exposes the complete, parity-correct D3 sampler subset through the
    /// common source-resource contract. This remains resource preparation
    /// only: it creates no program layout and cannot select a terrain route.
    pub(super) fn semantic_resource_set_for_frame(
        &self,
        readiness: &VoxelLightVolumeReadiness,
        frame_counter: u64,
        occupancy_view: Handle,
    ) -> GalResult<TerrainSourceOwnedResourceSet> {
        let active = readiness.binding_for_frame(frame_counter)?;
        let (current_light, previous_light) = match active.active_light_field {
            VoxelLightVolumeKind::FloodFillEven => {
                (self.even_light_sampler, self.odd_light_sampler)
            }
            VoxelLightVolumeKind::FloodFillOdd => (self.odd_light_sampler, self.even_light_sampler),
            VoxelLightVolumeKind::Occupancy => {
                return Err(GalError::invalid_argument(
                    "terrain voxel-light sampling cannot expose occupancy as a colored-light field",
                ));
            }
        };
        let descriptor = readiness.descriptor();
        let availability = TerrainSourceResourceAvailabilitySet::new(
            descriptor.shader_pack_generation,
            descriptor.world_generation,
            [
                TerrainSourceResourceAvailability {
                    role: TerrainSourceResourceRole::ColoredVoxelOccupancy,
                    shape: TerrainSourceSampledResourceShape::UnsignedTexture3d,
                    resource_generation: descriptor.resource_generation,
                },
                TerrainSourceResourceAvailability {
                    role: TerrainSourceResourceRole::ColoredVoxelLightCurrent,
                    shape: TerrainSourceSampledResourceShape::FloatTexture3d,
                    resource_generation: active.resource_generation,
                },
                TerrainSourceResourceAvailability {
                    role: TerrainSourceResourceRole::ColoredVoxelLightPrevious,
                    shape: TerrainSourceSampledResourceShape::FloatTexture3d,
                    resource_generation: active.resource_generation,
                },
            ],
        )?;
        TerrainSourceOwnedResourceSet::with_storage_resources(
            availability,
            [
                TerrainSourceOwnedResource {
                    role: TerrainSourceResourceRole::ColoredVoxelOccupancy,
                    combined_sampler: self.occupancy_sampler,
                },
                TerrainSourceOwnedResource {
                    role: TerrainSourceResourceRole::ColoredVoxelLightCurrent,
                    combined_sampler: current_light,
                },
                TerrainSourceOwnedResource {
                    role: TerrainSourceResourceRole::ColoredVoxelLightPrevious,
                    combined_sampler: previous_light,
                },
            ],
            [TerrainSourceOwnedStorageResource {
                role: TerrainSourceResourceRole::ColoredVoxelOccupancy,
                texture_view: occupancy_view,
            }],
        )
    }

    pub(super) fn destroy(self, gal: &mut VulkanicGal) -> GalResult<()> {
        gal.destroy(self.odd_resource_set)?;
        gal.destroy(self.even_resource_set)?;
        gal.destroy(self.odd_light_sampler)?;
        gal.destroy(self.even_light_sampler)?;
        gal.destroy(self.occupancy_sampler)?;
        gal.destroy(self.mapping_buffer)?;
        gal.destroy(self.resource_layout)
    }
}

impl TerrainColoredLightRuntime {
    /// Occupancy and flood-fill fields rest in `ShaderStorageRead` (GENERAL)
    /// for the Rust-owned compute. Returns those holding data (confirmed or
    /// written earlier in the current submission) that a pass may sample.
    pub(crate) fn storage_volume_textures(&self) -> Vec<Handle> {
        let mut textures = self.occupancy.storage_volume_textures();
        for (field, texture) in [
            (VoxelLightVolumeKind::FloodFillEven, self.flood_fill.even_texture),
            (VoxelLightVolumeKind::FloodFillOdd, self.flood_fill.odd_texture),
        ] {
            if self.compute.field_written_or_initialized(field) {
                textures.push(texture);
            }
        }
        textures
    }

    pub fn create(
        gal: &mut VulkanicGal,
        descriptor: VoxelLightVolumeDescriptor,
        materials: VoxelMaterialMap,
        emission: VoxelEmissionTable,
    ) -> GalResult<Self> {
        if emission.shader_pack_generation() != descriptor.shader_pack_generation {
            return Err(GalError::invalid_argument(
                "voxel emission table must match the colored-light generation",
            ));
        }
        let occupancy = TerrainOccupancyRuntime::create(gal, descriptor.clone(), materials)?;
        let flood_fill = match TerrainFloodFillGpuResources::create(gal, &descriptor) {
            Ok(resources) => resources,
            Err(error) => {
                let _ = occupancy.destroy(gal);
                return Err(error);
            }
        };
        let sampling = match TerrainVoxelLightSamplingResources::create(
            gal,
            &occupancy.resources,
            &flood_fill,
        ) {
            Ok(resources) => resources,
            Err(error) => {
                let _ = flood_fill.destroy(gal);
                let _ = occupancy.destroy(gal);
                return Err(error);
            }
        };
        let compute = match TerrainFloodFillComputeResources::create(
            gal,
            &occupancy.resources,
            &flood_fill,
        ) {
            Ok(resources) => resources,
            Err(error) => {
                let _ = sampling.destroy(gal);
                let _ = flood_fill.destroy(gal);
                let _ = occupancy.destroy(gal);
                return Err(error);
            }
        };
        Ok(Self {
            occupancy,
            flood_fill,
            compute,
            sampling,
            emission,
            submission_pending: false,
            pending_compute_rollback: None,
            pending_flood_mapping_rollback: None,
            last_occupancy_update: TerrainOccupancyUpdateStats::default(),
        })
    }

    pub fn descriptor(&self) -> &VoxelLightVolumeDescriptor {
        self.occupancy.descriptor()
    }

    pub fn mesh_snapshot_count(&self) -> usize {
        self.occupancy.mesh_snapshot_count()
    }

    pub fn is_ready_for_frame(&self, frame_counter: u64) -> bool {
        self.occupancy.is_initialized()
            && self.flood_fill.emission_ready()
            && self.flood_fill.tint_ready()
            && self.sampling.mapping_ready_for(self.occupancy.descriptor())
            && self.compute.is_initialized_for_frame(frame_counter)
            && self.compute.mapping_ready_for(self.occupancy.descriptor())
            && !self.submission_pending
    }

    /// Returns semantic admission data only after the exact owned occupancy
    /// and both ping-pong fields have reached confirmed, matching state. The
    /// value has no texture handles and cannot bind or select a terrain pass.
    pub(crate) fn readiness(&self) -> GalResult<VoxelLightVolumeReadiness> {
        if self.submission_pending {
            return Err(GalError::invalid_argument(
                "colored-light volume generation has a submission pending confirmation",
            ));
        }
        if !self.flood_fill.emission_ready() || !self.flood_fill.tint_ready() {
            return Err(GalError::invalid_argument(
                "colored-light volume source tables are not confirmed for this generation",
            ));
        }
        if !self.sampling.mapping_ready_for(self.occupancy.descriptor()) {
            return Err(GalError::invalid_argument(
                "colored-light volume mapping uniform is not confirmed for this generation",
            ));
        }
        if !self.compute.mapping_ready_for(self.occupancy.descriptor()) {
            return Err(GalError::invalid_argument(
                "colored-light flood-fill parity is not confirmed for the current camera mapping",
            ));
        }
        VoxelLightVolumeReadiness::new(
            self.occupancy.descriptor().clone(),
            self.occupancy.is_initialized(),
            self.compute.even_initialized,
            self.compute.odd_initialized,
        )
    }

    /// Returns the parity-selected owned resource set only for a complete
    /// semantic generation. This is preparation for a future selected terrain
    /// pipeline and cannot issue a draw by itself.
    pub(crate) fn sampling_binding(
        &self,
        frame_counter: u64,
    ) -> GalResult<TerrainVoxelLightSamplingBinding> {
        self.sampling
            .binding_for_frame(&self.readiness()?, frame_counter)
    }

    /// Returns the semantic D3 sampler table for this exact confirmed frame
    /// parity. It is intentionally separate from the legacy compact sampling
    /// binding while selected-source terrain execution remains unavailable.
    pub(crate) fn semantic_resource_set_for_frame(
        &self,
        frame_counter: u64,
    ) -> GalResult<TerrainSourceOwnedResourceSet> {
        self.sampling.semantic_resource_set_for_frame(
            &self.readiness()?,
            frame_counter,
            self.occupancy.resources.view,
        )
    }

    /// Returns the parity-correct sampler table for a field that will become
    /// shader-readable earlier in the same combined submission. This is not
    /// a relaxed readiness check: it is valid only for the exact pending
    /// compute frame, after that compute pass has appended its explicit
    /// write-to-read transition, and it remains rollback-bound until the
    /// submission is confirmed.
    pub(crate) fn semantic_resource_set_for_pending_submission(
        &self,
        frame_counter: u64,
    ) -> GalResult<TerrainSourceOwnedResourceSet> {
        if !self.submission_pending {
            return Err(GalError::invalid_argument(
                "colored-light same-submission resources require a pending submission",
            ));
        }
        if (!self.flood_fill.emission_ready() && !self.flood_fill.emission_upload_pending)
            || (!self.flood_fill.tint_ready() && !self.flood_fill.tint_upload_pending)
        {
            return Err(GalError::invalid_argument(
                "colored-light same-submission resources require ordered emission and tint tables",
            ));
        }
        if !self.sampling.mapping_ready_for(self.occupancy.descriptor())
            && !self.sampling.mapping_upload_pending
        {
            return Err(GalError::invalid_argument(
                "colored-light same-submission resources require an ordered mapping upload",
            ));
        }
        if !self
            .compute
            .has_pending_output_for_frame(frame_counter, self.occupancy.descriptor())
        {
            return Err(GalError::invalid_argument(
                "colored-light same-submission resources require the exact frame's pending flood-fill output",
            ));
        }
        let active = flood_fill_output_field_for_frame(frame_counter);
        // Initialization writes both temporal fields before either can be
        // sampled. Treat both as pending-ready inside this one command list;
        // propagation, by contrast, only produces its selected target field.
        let pending_initialization = self.compute.pending_initialization.is_some();
        let readiness = VoxelLightVolumeReadiness::new(
            self.occupancy.descriptor().clone(),
            true,
            self.compute.even_initialized
                || pending_initialization
                || active == VoxelLightVolumeKind::FloodFillEven,
            self.compute.odd_initialized
                || pending_initialization
                || active == VoxelLightVolumeKind::FloodFillOdd,
        )?;
        self.sampling.semantic_resource_set_for_frame(
            &readiness,
            frame_counter,
            self.occupancy.resources.view,
        )
    }

    pub(crate) fn has_pending_submission(&self) -> bool {
        self.submission_pending
    }

    /// Whether this exact pending transaction has already produced a
    /// shader-readable flood-fill field. A first-generation table or mapping
    /// upload is valid Rust-owned preparation, but it is not yet a source
    /// terrain input. Keeping that distinction here lets higher-level
    /// admission report the roles as unavailable without treating expected
    /// transactional ordering as an asset failure.
    pub(crate) fn pending_sampling_ready_for_frame(&self, frame_counter: u64) -> bool {
        self.submission_pending
            && (self.occupancy.is_initialized() || self.occupancy.has_pending_submission())
            && (self.flood_fill.emission_ready() || self.flood_fill.emission_upload_pending)
            && (self.flood_fill.tint_ready() || self.flood_fill.tint_upload_pending)
            && (self.sampling.mapping_ready_for(self.occupancy.descriptor())
                || self.sampling.mapping_upload_pending)
            && self
                .compute
                .has_pending_output_for_frame(frame_counter, self.occupancy.descriptor())
    }

    pub(crate) fn diagnostic_state(
        &self,
        frame_counter: u64,
    ) -> TerrainColoredLightDiagnosticState {
        let extent = self.occupancy.descriptor().extent;
        let expected_owned_bytes =
            extent
                .byte_len(crate::render::shaderpack::voxels::light_volume::VoxelLightVolumeFormat::OccupancyR8Uint)
                .saturating_add(extent.byte_len(
                    crate::render::shaderpack::voxels::light_volume::VoxelLightVolumeFormat::LightingRgba16Float,
                ))
                .saturating_add(extent.byte_len(
                    crate::render::shaderpack::voxels::light_volume::VoxelLightVolumeFormat::LightingRgba16Float,
                ));
        TerrainColoredLightDiagnosticState {
            extent_width: extent.width,
            extent_height: extent.height,
            extent_depth: extent.depth,
            expected_owned_bytes,
            mesh_snapshot_count: self.occupancy.mesh_snapshot_count(),
            submission_pending: self.submission_pending,
            occupancy_upload_pending: self.occupancy.has_pending_submission(),
            occupancy_initialized: self.occupancy.is_initialized(),
            emission_ready: self.flood_fill.emission_ready(),
            tint_ready: self.flood_fill.tint_ready(),
            sampling_mapping_ready: self.sampling.mapping_ready_for(self.occupancy.descriptor()),
            compute_even_initialized: self.compute.even_initialized,
            compute_odd_initialized: self.compute.odd_initialized,
            pending_initialization_frame: self.compute.pending_initialization,
            pending_propagation_frame: self.compute.pending_propagation,
            pending_compute_output_ready: self
                .compute
                .has_pending_output_for_frame(frame_counter, self.occupancy.descriptor()),
            frame_ready: self.is_ready_for_frame(frame_counter),
            occupancy_input_samples: self.last_occupancy_update.input_samples,
            occupancy_emitted_samples: self.last_occupancy_update.emitted_samples,
            occupancy_overwritten_samples: self.last_occupancy_update.overwritten_samples,
            occupancy_skipped_non_solid_samples: self
                .last_occupancy_update
                .skipped_non_solid_samples,
            occupancy_skipped_out_of_bounds_samples: self
                .last_occupancy_update
                .skipped_out_of_bounds_samples,
            occupancy_changed_voxels: self.last_occupancy_update.changed_voxels,
            occupancy_uploaded_bytes: self.last_occupancy_update.uploaded_bytes,
        }
    }

    /// Appends at most one colored-light compute step after its exact owned
    /// occupancy and source-derived tables have been ordered into this
    /// combined submission. The upload paths transition every input to shader
    /// read before this dispatch, while confirmation/rollback remains atomic
    /// with the enclosing submission.
    pub(crate) fn append_terrain_source_snapshot_for_mapping(
        &mut self,
        frame_counter: u64,
        mapping: VoxelLightVolumeMapping,
        view_direction: Option<VoxelLightVolumeViewDirection>,
        meshes: impl IntoIterator<Item = TerrainVoxelSourceMesh>,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<TerrainOccupancyUpdateStats> {
        if self.submission_pending {
            return Err(GalError::invalid_argument(
                "colored-light runtime already has a submission awaiting confirmation",
            ));
        }
        let result = (|| {
            let had_confirmed_occupancy = self.occupancy.is_initialized();
            let stats = self
                .occupancy
                .append_terrain_source_snapshot_for_mapping(mapping, meshes, operations)?;
            if stats.input_samples != 0 || stats.updated_region.is_some() {
                self.last_occupancy_update = stats;
            }
            let mapping_changed =
                self.flood_fill.descriptor.mapping != self.occupancy.descriptor().mapping;
            if mapping_changed {
                self.pending_flood_mapping_rollback = Some(self.flood_fill.descriptor.clone());
                self.flood_fill
                    .update_mapping(self.occupancy.descriptor())?;
            }
            // Complementary re-voxelizes occupancy independently of its
            // flood-fill history. Only the first complete field needs a seed;
            // later mesh or camera-cell updates are consumed by the next
            // `previousCameraPosition - cameraPosition` propagation.
            if stats.updated_region.is_some() && !had_confirmed_occupancy {
                self.pending_compute_rollback = Some(self.compute.clone());
                self.compute.invalidate();
            }
            self.flood_fill
                .append_emission_upload(&self.emission, operations)?;
            self.flood_fill
                .append_tint_upload(&self.emission, operations)?;
            self.sampling
                .append_mapping_upload(self.occupancy.descriptor(), operations)?;

            // A discovered source candidate may exist before any visible
            // terrain mesh has populated the owned occupancy field. Uploading
            // tables/mapping is safe preparation; dispatching flood-fill is
            // not. Keep that state explicitly unready instead of making the
            // ordinary whole-frame route fail during source preflight.
            // A confirmed field remains valid for a stable camera cell and
            // unchanged terrain. Re-dispatching propagation unconditionally
            // makes an otherwise complete source route permanently pending at
            // its own admission boundary. Only seed once, or propagate after
            // an occupancy/mapping change that actually changes semantic
            // input to the selected terrain pass.
            let propagation_required = !self.compute.has_seed()
                || stats.updated_region.is_some()
                // The mapping upload commits before a temporal propagation.
                // Compare the compute stage's confirmed mapping, not only the
                // flood-fill descriptor just updated above, so a cell move
                // schedules exactly one later propagation and a stable frame
                // schedules none.
                || !self.compute.mapping_ready_for(self.occupancy.descriptor());
            if propagation_required
                && (self.occupancy.is_initialized() || self.occupancy.has_pending_submission())
                && (self.flood_fill.emission_ready() || self.flood_fill.emission_upload_pending)
                && (self.flood_fill.tint_ready() || self.flood_fill.tint_upload_pending)
            {
                if !self.compute.has_seed() {
                    self.compute.append_initialization(
                        &self.occupancy.resources,
                        &self.flood_fill,
                        frame_counter,
                        operations,
                    )?;
                } else {
                    self.compute.append_propagation(
                        &self.occupancy.resources,
                        &self.flood_fill,
                        frame_counter,
                        view_direction,
                        operations,
                    )?;
                }
            }
            self.submission_pending = self.occupancy.has_pending_submission()
                || self.flood_fill.has_pending_upload()
                || self.sampling.mapping_upload_pending
                || self.compute.has_pending_submission();
            Ok(stats)
        })();
        if result.is_err() {
            self.discard_submission();
        }
        result
    }

    pub fn confirm_submission(&mut self) -> GalResult<()> {
        if !self.submission_pending {
            return Err(GalError::invalid_argument(
                "no colored-light submission is pending confirmation",
            ));
        }
        if self.occupancy.has_pending_submission() {
            self.occupancy.confirm_submission()?;
        }
        self.flood_fill.confirm_pending_uploads()?;
        self.sampling
            .confirm_mapping_submission(self.occupancy.descriptor())?;
        self.compute.confirm_pending_submission()?;
        self.submission_pending = false;
        self.pending_compute_rollback = None;
        self.pending_flood_mapping_rollback = None;
        Ok(())
    }

    pub fn discard_submission(&mut self) {
        self.occupancy.discard_submission();
        self.flood_fill.discard_pending_uploads();
        self.sampling.discard_pending_mapping_upload();
        if let Some(previous) = self.pending_compute_rollback.take() {
            self.compute = previous;
        } else {
            self.compute.discard_pending_submission();
        }
        if let Some(previous) = self.pending_flood_mapping_rollback.take() {
            self.flood_fill.descriptor = previous;
        }
        self.submission_pending = false;
    }

    pub fn destroy(self, gal: &mut VulkanicGal) -> GalResult<()> {
        self.compute.destroy(gal)?;
        self.sampling.destroy(gal)?;
        self.flood_fill.destroy(gal)?;
        self.occupancy.destroy(gal)
    }
}

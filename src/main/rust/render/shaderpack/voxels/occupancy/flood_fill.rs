//! Colored-light flood-fill compute kernels, dispatch and GPU resources.

use super::*;

/// Private owned ping-pong storage for the semantic colored-light field. The
/// resource pair is intentionally separate from the flood-fill algorithm: it
/// establishes backend-neutral generation, parity, binding, and retirement
/// before any shader-pack program can consume the data.
#[derive(Clone, Debug)]
pub struct TerrainFloodFillGpuResources {
    pub(super) descriptor: VoxelLightVolumeDescriptor,
    pub even_texture: Handle,
    pub even_view: Handle,
    pub odd_texture: Handle,
    pub odd_view: Handle,
    pub sampler: Handle,
    pub(super) emission_buffer: Handle,
    pub(super) tint_buffer: Handle,
    pub(super) emission_generation: Option<u64>,
    pub(super) emission_upload_pending: bool,
    pub(super) tint_generation: Option<u64>,
    pub(super) tint_upload_pending: bool,
}

/// A single source-derived flood-fill work step. This is intentionally only a
/// GAL command contract: resource-set construction and native descriptor
/// lowering remain outside the shader-pack semantic layer.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct TerrainFloodFillDispatch {
    pub frame_counter: u64,
    pub groups_x: u32,
    pub groups_y: u32,
    pub groups_z: u32,
}

/// Private owned compute objects for initializing and propagating the
/// source-derived voxel field. This deliberately has no terrain-pass binding
/// or source-plan admission path: it exists solely to establish the explicit
/// GAL resource, descriptor, and synchronization contract first.
#[derive(Clone, Debug)]
pub struct TerrainFloodFillComputeResources {
    pub(super) init_layout: Handle,
    pub(super) init_pipeline_layout: Handle,
    pub(super) init_shader: Handle,
    pub(super) init_pipeline: Handle,
    pub(super) even_init_set: Handle,
    pub(super) odd_init_set: Handle,
    pub(super) propagate_layout: Handle,
    pub(super) propagate_pipeline_layout: Handle,
    pub(super) propagate_shader: Handle,
    pub(super) propagate_pipeline: Handle,
    pub(super) temporal_mapping_buffer: Handle,
    pub(super) even_to_odd_set: Handle,
    pub(super) odd_to_even_set: Handle,
    pub(super) even_initialized: bool,
    pub(super) odd_initialized: bool,
    pub(super) pending_initialization: Option<u64>,
    pub(super) pending_propagation: Option<u64>,
    pub(super) confirmed_frame_mapping: Option<VoxelLightVolumeFrameMapping>,
    pub(super) pending_frame_mapping: Option<VoxelLightVolumeFrameMapping>,
}

impl TerrainFloodFillDispatch {
    pub fn prepare(
        occupancy: &TerrainOccupancyGpuResources,
        flood_fill: &TerrainFloodFillGpuResources,
        frame_counter: u64,
    ) -> GalResult<Self> {
        if occupancy.descriptor != flood_fill.descriptor {
            return Err(GalError::invalid_argument(
                "flood-fill occupancy and light resources use different generations",
            ));
        }
        if !occupancy.is_initialized() && !occupancy.has_pending_submission() {
            return Err(GalError::invalid_argument(
                "flood-fill dispatch requires initialized or ordered occupancy data",
            ));
        }
        if !flood_fill.emission_ready() && !flood_fill.emission_upload_pending {
            return Err(GalError::invalid_argument(
                "flood-fill dispatch requires a confirmed or ordered source-derived emission table",
            ));
        }
        if !flood_fill.tint_ready() && !flood_fill.tint_upload_pending {
            return Err(GalError::invalid_argument(
                "flood-fill dispatch requires a confirmed or ordered source-derived tint table",
            ));
        }
        let extent = occupancy.descriptor.extent;
        Ok(Self {
            frame_counter,
            groups_x: extent.width.div_ceil(8),
            groups_y: extent.height.div_ceil(8),
            groups_z: extent.depth.div_ceil(8),
        })
    }

    pub fn append(
        self,
        pipeline: Handle,
        pipeline_layout: Handle,
        resource_set: Handle,
        operations: &mut Vec<CommandOp>,
    ) {
        operations.push(CommandOp::BindComputePipeline(pipeline));
        operations.push(CommandOp::BindResourceSet {
            pipeline_layout,
            set_index: 0,
            set: resource_set,
            dynamic_offsets: Vec::new(),
        });
        operations.push(CommandOp::Dispatch {
            groups_x: self.groups_x,
            groups_y: self.groups_y,
            groups_z: self.groups_z,
        });
    }
}

impl TerrainFloodFillComputeResources {
    pub fn create(
        gal: &mut VulkanicGal,
        occupancy: &TerrainOccupancyGpuResources,
        flood_fill: &TerrainFloodFillGpuResources,
    ) -> GalResult<Self> {
        if occupancy.descriptor != flood_fill.descriptor {
            return Err(GalError::invalid_argument(
                "flood-fill compute resources require matching occupancy and light generations",
            ));
        }
        let label = flood_fill.descriptor.identity.as_str();
        let init_layout = gal.create_resource_layout(ResourceLayoutDesc {
            label: format!("{label}.flood-fill.init.layout"),
            bindings: vec![
                compute_binding(0, ResourceBindingKind::StorageTexture),
                compute_binding(1, ResourceBindingKind::StorageTexture),
                compute_binding(2, ResourceBindingKind::UniformBuffer),
            ],
        })?;
        let init_pipeline_layout = match gal.create_pipeline_layout(PipelineLayoutDesc {
            label: format!("{label}.flood-fill.init.pipeline-layout"),
            resource_layouts: vec![init_layout],
        }) {
            Ok(handle) => handle,
            Err(error) => {
                let _ = gal.destroy(init_layout);
                return Err(error);
            }
        };
        let init_shader = match gal.create_shader_module(ShaderModuleDesc {
            label: format!("{label}.flood-fill.init.compute"),
            stage: ShaderStage::Compute,
            code_format: ShaderCodeFormat::Glsl,
            code: shader_stage_code(gal.capabilities().shader_conventions, FLOOD_FILL_INIT_SHADER),
            entry_point: "main".to_owned(),
        }) {
            Ok(handle) => handle,
            Err(error) => {
                let _ = gal.destroy(init_pipeline_layout);
                let _ = gal.destroy(init_layout);
                return Err(error);
            }
        };
        let init_pipeline = match gal.create_compute_pipeline(ComputePipelineDesc {
            label: format!("{label}.flood-fill.init.pipeline"),
            layout: init_pipeline_layout,
            shader: init_shader,
        }) {
            Ok(handle) => handle,
            Err(error) => {
                let _ = gal.destroy(init_shader);
                let _ = gal.destroy(init_pipeline_layout);
                let _ = gal.destroy(init_layout);
                return Err(error);
            }
        };
        let even_init_set = match gal.create_resource_set(ResourceSetDesc {
            label: format!("{label}.flood-fill.init.even.set"),
            layout: init_layout,
            bindings: vec![
                compute_resource(
                    0,
                    occupancy.view,
                    ResourceBindingKind::StorageTexture,
                    AccessFlags::READ,
                ),
                compute_resource(
                    1,
                    flood_fill.even_view,
                    ResourceBindingKind::StorageTexture,
                    AccessFlags::WRITE,
                ),
                compute_resource(
                    2,
                    flood_fill.emission_buffer,
                    ResourceBindingKind::UniformBuffer,
                    AccessFlags::READ,
                ),
            ],
        }) {
            Ok(handle) => handle,
            Err(error) => {
                let _ = gal.destroy(init_pipeline);
                let _ = gal.destroy(init_shader);
                let _ = gal.destroy(init_pipeline_layout);
                let _ = gal.destroy(init_layout);
                return Err(error);
            }
        };

        let odd_init_set = match gal.create_resource_set(ResourceSetDesc {
            label: format!("{label}.flood-fill.init.odd.set"),
            layout: init_layout,
            bindings: vec![
                compute_resource(
                    0,
                    occupancy.view,
                    ResourceBindingKind::StorageTexture,
                    AccessFlags::READ,
                ),
                compute_resource(
                    1,
                    flood_fill.odd_view,
                    ResourceBindingKind::StorageTexture,
                    AccessFlags::WRITE,
                ),
                compute_resource(
                    2,
                    flood_fill.emission_buffer,
                    ResourceBindingKind::UniformBuffer,
                    AccessFlags::READ,
                ),
            ],
        }) {
            Ok(handle) => handle,
            Err(error) => {
                destroy_init_compute(
                    gal,
                    even_init_set,
                    None,
                    init_pipeline,
                    init_shader,
                    init_pipeline_layout,
                    init_layout,
                );
                return Err(error);
            }
        };

        let propagate_layout = match gal.create_resource_layout(ResourceLayoutDesc {
            label: format!("{label}.flood-fill.propagate.layout"),
            bindings: vec![
                compute_binding(0, ResourceBindingKind::StorageTexture),
                compute_binding(1, ResourceBindingKind::StorageTexture),
                compute_binding(2, ResourceBindingKind::StorageTexture),
                compute_binding(3, ResourceBindingKind::UniformBuffer),
                compute_binding(4, ResourceBindingKind::UniformBuffer),
                compute_binding(5, ResourceBindingKind::UniformBuffer),
            ],
        }) {
            Ok(handle) => handle,
            Err(error) => {
                destroy_init_compute(
                    gal,
                    even_init_set,
                    Some(odd_init_set),
                    init_pipeline,
                    init_shader,
                    init_pipeline_layout,
                    init_layout,
                );
                return Err(error);
            }
        };
        let propagate_pipeline_layout = match gal.create_pipeline_layout(PipelineLayoutDesc {
            label: format!("{label}.flood-fill.propagate.pipeline-layout"),
            resource_layouts: vec![propagate_layout],
        }) {
            Ok(handle) => handle,
            Err(error) => {
                let _ = gal.destroy(propagate_layout);
                destroy_init_compute(
                    gal,
                    even_init_set,
                    Some(odd_init_set),
                    init_pipeline,
                    init_shader,
                    init_pipeline_layout,
                    init_layout,
                );
                return Err(error);
            }
        };
        let propagate_shader = match gal.create_shader_module(ShaderModuleDesc {
            label: format!("{label}.flood-fill.propagate.compute"),
            stage: ShaderStage::Compute,
            code_format: ShaderCodeFormat::Glsl,
            code: shader_stage_code(
                gal.capabilities().shader_conventions,
                FLOOD_FILL_PROPAGATE_SHADER,
            ),
            entry_point: "main".to_owned(),
        }) {
            Ok(handle) => handle,
            Err(error) => {
                let _ = gal.destroy(propagate_pipeline_layout);
                let _ = gal.destroy(propagate_layout);
                destroy_init_compute(
                    gal,
                    even_init_set,
                    Some(odd_init_set),
                    init_pipeline,
                    init_shader,
                    init_pipeline_layout,
                    init_layout,
                );
                return Err(error);
            }
        };
        let propagate_pipeline = match gal.create_compute_pipeline(ComputePipelineDesc {
            label: format!("{label}.flood-fill.propagate.pipeline"),
            layout: propagate_pipeline_layout,
            shader: propagate_shader,
        }) {
            Ok(handle) => handle,
            Err(error) => {
                let _ = gal.destroy(propagate_shader);
                let _ = gal.destroy(propagate_pipeline_layout);
                let _ = gal.destroy(propagate_layout);
                destroy_init_compute(
                    gal,
                    even_init_set,
                    Some(odd_init_set),
                    init_pipeline,
                    init_shader,
                    init_pipeline_layout,
                    init_layout,
                );
                return Err(error);
            }
        };
        let temporal_mapping_buffer = match gal.create_buffer(BufferDesc {
            label: format!("{label}.flood-fill.temporal-mapping"),
            size: VoxelLightVolumeTemporalMapping::STD140_SIZE as u64,
            memory: MemoryDomain::Upload,
            usages: vec![BufferUsage::Uniform, BufferUsage::HostWrite],
        }) {
            Ok(handle) => handle,
            Err(error) => {
                destroy_propagate_compute(
                    gal,
                    propagate_pipeline,
                    propagate_shader,
                    propagate_pipeline_layout,
                    propagate_layout,
                );
                destroy_init_compute(
                    gal,
                    even_init_set,
                    Some(odd_init_set),
                    init_pipeline,
                    init_shader,
                    init_pipeline_layout,
                    init_layout,
                );
                return Err(error);
            }
        };
        let even_to_odd_set = match gal.create_resource_set(ResourceSetDesc {
            label: format!("{label}.flood-fill.even-to-odd.set"),
            layout: propagate_layout,
            bindings: propagation_bindings(
                occupancy.view,
                flood_fill.even_view,
                flood_fill.odd_view,
                flood_fill.emission_buffer,
                flood_fill.tint_buffer,
                temporal_mapping_buffer,
            ),
        }) {
            Ok(handle) => handle,
            Err(error) => {
                destroy_propagate_compute(
                    gal,
                    propagate_pipeline,
                    propagate_shader,
                    propagate_pipeline_layout,
                    propagate_layout,
                );
                let _ = gal.destroy(temporal_mapping_buffer);
                destroy_init_compute(
                    gal,
                    even_init_set,
                    Some(odd_init_set),
                    init_pipeline,
                    init_shader,
                    init_pipeline_layout,
                    init_layout,
                );
                return Err(error);
            }
        };
        let odd_to_even_set = match gal.create_resource_set(ResourceSetDesc {
            label: format!("{label}.flood-fill.odd-to-even.set"),
            layout: propagate_layout,
            bindings: propagation_bindings(
                occupancy.view,
                flood_fill.odd_view,
                flood_fill.even_view,
                flood_fill.emission_buffer,
                flood_fill.tint_buffer,
                temporal_mapping_buffer,
            ),
        }) {
            Ok(handle) => handle,
            Err(error) => {
                let _ = gal.destroy(even_to_odd_set);
                destroy_propagate_compute(
                    gal,
                    propagate_pipeline,
                    propagate_shader,
                    propagate_pipeline_layout,
                    propagate_layout,
                );
                let _ = gal.destroy(temporal_mapping_buffer);
                destroy_init_compute(
                    gal,
                    even_init_set,
                    Some(odd_init_set),
                    init_pipeline,
                    init_shader,
                    init_pipeline_layout,
                    init_layout,
                );
                return Err(error);
            }
        };
        Ok(Self {
            init_layout,
            init_pipeline_layout,
            init_shader,
            init_pipeline,
            even_init_set,
            odd_init_set,
            propagate_layout,
            propagate_pipeline_layout,
            propagate_shader,
            propagate_pipeline,
            temporal_mapping_buffer,
            even_to_odd_set,
            odd_to_even_set,
            even_initialized: false,
            odd_initialized: false,
            pending_initialization: None,
            pending_propagation: None,
            confirmed_frame_mapping: None,
            pending_frame_mapping: None,
        })
    }

    pub fn append_initialization(
        &mut self,
        occupancy: &TerrainOccupancyGpuResources,
        flood_fill: &TerrainFloodFillGpuResources,
        frame_counter: u64,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<()> {
        if self.pending_initialization.is_some() || self.pending_frame_mapping.is_some() {
            return Err(GalError::invalid_argument(
                "flood-fill initialization is already pending confirmation",
            ));
        }
        let dispatch = TerrainFloodFillDispatch::prepare(occupancy, flood_fill, frame_counter)?;
        let frame_mapping = VoxelLightVolumeFrameMapping::from_descriptor(&occupancy.descriptor);
        // Gameplay frame IDs may skip parity while resources are warming up.
        // Seed both fields from the same coherent occupancy generation so the
        // first propagation always has a defined source field. Subsequent
        // iterations retain Complementary's frame-parity selection.
        for (target_field, target_texture, init_set) in [
            (
                VoxelLightVolumeKind::FloodFillEven,
                flood_fill.even_texture,
                self.even_init_set,
            ),
            (
                VoxelLightVolumeKind::FloodFillOdd,
                flood_fill.odd_texture,
                self.odd_init_set,
            ),
        ] {
            let target_initialized = self.field_initialized(target_field);
            operations.push(CommandOp::Barrier(resource_barrier(
                target_texture,
                None,
                if target_initialized {
                    TextureUsageState::ShaderStorageRead
                } else {
                    TextureUsageState::Undefined
                },
                TextureUsageState::ShaderWrite,
            )));
            dispatch.append(
                self.init_pipeline,
                self.init_pipeline_layout,
                init_set,
                operations,
            );
            operations.push(CommandOp::Barrier(resource_barrier(
                target_texture,
                None,
                TextureUsageState::ShaderWrite,
                TextureUsageState::ShaderStorageRead,
            )));
        }
        self.pending_initialization = Some(frame_counter);
        self.pending_frame_mapping = Some(frame_mapping);
        Ok(())
    }

    pub fn confirm_initialization(&mut self) -> GalResult<()> {
        self.pending_initialization.take().ok_or_else(|| {
            GalError::invalid_argument("no flood-fill initialization is pending confirmation")
        })?;
        self.mark_field_initialized(VoxelLightVolumeKind::FloodFillEven)?;
        self.mark_field_initialized(VoxelLightVolumeKind::FloodFillOdd)?;
        self.confirmed_frame_mapping = self.pending_frame_mapping.take();
        Ok(())
    }

    pub(super) fn has_seed(&self) -> bool {
        self.even_initialized || self.odd_initialized
    }

    pub(super) fn is_initialized_for_frame(&self, frame_counter: u64) -> bool {
        self.field_initialized(flood_fill_output_field_for_frame(frame_counter))
    }

    pub(super) fn mapping_ready_for(&self, descriptor: &VoxelLightVolumeDescriptor) -> bool {
        self.pending_frame_mapping.is_none()
            && self
                .confirmed_frame_mapping
                .as_ref()
                .is_some_and(|mapping| mapping.matches_descriptor(descriptor))
    }

    pub(super) fn has_pending_submission(&self) -> bool {
        self.pending_initialization.is_some() || self.pending_propagation.is_some()
    }

    pub(super) fn has_pending_output_for_frame(
        &self,
        frame_counter: u64,
        descriptor: &VoxelLightVolumeDescriptor,
    ) -> bool {
        (self.pending_initialization == Some(frame_counter)
            || self.pending_propagation == Some(frame_counter))
            && self
                .pending_frame_mapping
                .as_ref()
                .is_some_and(|mapping| mapping.matches_descriptor(descriptor))
    }

    pub(super) fn confirm_pending_submission(&mut self) -> GalResult<()> {
        if self.pending_initialization.is_some() {
            self.confirm_initialization()
        } else if self.pending_propagation.is_some() {
            self.confirm_propagation()
        } else {
            Ok(())
        }
    }

    pub(super) fn discard_pending_submission(&mut self) {
        self.discard_initialization();
        self.discard_propagation();
    }

    pub(super) fn invalidate(&mut self) {
        self.discard_pending_submission();
        self.even_initialized = false;
        self.odd_initialized = false;
    }

    pub fn discard_initialization(&mut self) {
        self.pending_initialization = None;
        self.pending_frame_mapping = None;
    }

    pub fn append_propagation(
        &mut self,
        occupancy: &TerrainOccupancyGpuResources,
        flood_fill: &TerrainFloodFillGpuResources,
        frame_counter: u64,
        view_direction: Option<VoxelLightVolumeViewDirection>,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<()> {
        if self.pending_initialization.is_some()
            || self.pending_propagation.is_some()
            || self.pending_frame_mapping.is_some()
        {
            return Err(GalError::invalid_argument(
                "flood-fill propagation requires a confirmed seed and no pending compute submission",
            ));
        }
        let dispatch = TerrainFloodFillDispatch::prepare(occupancy, flood_fill, frame_counter)?;
        let current_frame_mapping =
            VoxelLightVolumeFrameMapping::from_descriptor(&occupancy.descriptor);
        let previous_frame_mapping = self.confirmed_frame_mapping.as_ref().ok_or_else(|| {
            GalError::invalid_argument(
                "flood-fill propagation requires a confirmed previous frame mapping",
            )
        })?;
        let temporal_mapping = VoxelLightVolumeTemporalMapping::from_frame_mappings(
            &current_frame_mapping,
            previous_frame_mapping,
            occupancy.descriptor.extent,
            occupancy.descriptor.requirements.update_policy,
            frame_counter,
            view_direction,
        )?;
        let source_field = flood_fill_source_field_for_frame(frame_counter);
        let target_field = flood_fill_output_field_for_frame(frame_counter);
        let (target_texture, set) = match (source_field, target_field) {
            (VoxelLightVolumeKind::FloodFillEven, VoxelLightVolumeKind::FloodFillOdd) => {
                (flood_fill.odd_texture, self.even_to_odd_set)
            }
            (VoxelLightVolumeKind::FloodFillOdd, VoxelLightVolumeKind::FloodFillEven) => {
                (flood_fill.even_texture, self.odd_to_even_set)
            }
            _ => {
                return Err(GalError::invalid_argument(
                    "flood-fill source/output parity contract is invalid",
                ));
            }
        };
        let source_initialized = self.field_initialized(source_field);
        let target_initialized = self.field_initialized(target_field);
        if !source_initialized {
            return Err(GalError::invalid_argument(
                "flood-fill propagation source parity is not initialized",
            ));
        }
        operations.push(CommandOp::HostWriteBuffer {
            buffer: self.temporal_mapping_buffer,
            offset: 0,
            data: temporal_mapping.std140_bytes().to_vec(),
        });
        operations.push(CommandOp::Barrier(resource_barrier(
            self.temporal_mapping_buffer,
            None,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        )));
        // The source was made shader-readable by its preceding seed or
        // propagation submission. A read-to-read barrier is intentionally
        // omitted because GAL requires semantic state transitions only.
        operations.push(CommandOp::Barrier(resource_barrier(
            target_texture,
            None,
            if target_initialized {
                TextureUsageState::ShaderStorageRead
            } else {
                TextureUsageState::Undefined
            },
            TextureUsageState::ShaderWrite,
        )));
        dispatch.append(
            self.propagate_pipeline,
            self.propagate_pipeline_layout,
            set,
            operations,
        );
        operations.push(CommandOp::Barrier(resource_barrier(
            target_texture,
            None,
            TextureUsageState::ShaderWrite,
            TextureUsageState::ShaderStorageRead,
        )));
        self.pending_propagation = Some(frame_counter);
        self.pending_frame_mapping = Some(current_frame_mapping);
        Ok(())
    }

    pub fn confirm_propagation(&mut self) -> GalResult<()> {
        let frame_counter = self.pending_propagation.take().ok_or_else(|| {
            GalError::invalid_argument("no flood-fill propagation is pending confirmation")
        })?;
        self.mark_field_initialized(flood_fill_output_field_for_frame(frame_counter))?;
        self.confirmed_frame_mapping = self.pending_frame_mapping.take();
        Ok(())
    }

    pub(super) fn field_written_or_initialized(&self, field: VoxelLightVolumeKind) -> bool {
        self.field_initialized(field)
            || self.pending_initialization.is_some()
            || self
                .pending_propagation
                .is_some_and(|frame| flood_fill_output_field_for_frame(frame) == field)
    }

    pub(super) fn field_initialized(&self, field: VoxelLightVolumeKind) -> bool {
        match field {
            VoxelLightVolumeKind::FloodFillEven => self.even_initialized,
            VoxelLightVolumeKind::FloodFillOdd => self.odd_initialized,
            VoxelLightVolumeKind::Occupancy => false,
        }
    }

    pub(super) fn mark_field_initialized(&mut self, field: VoxelLightVolumeKind) -> GalResult<()> {
        match field {
            VoxelLightVolumeKind::FloodFillEven => self.even_initialized = true,
            VoxelLightVolumeKind::FloodFillOdd => self.odd_initialized = true,
            VoxelLightVolumeKind::Occupancy => {
                return Err(GalError::invalid_argument(
                    "occupancy is not a flood-fill parity field",
                ));
            }
        }
        Ok(())
    }

    pub fn discard_propagation(&mut self) {
        self.pending_propagation = None;
        self.pending_frame_mapping = None;
    }

    pub fn destroy(self, gal: &mut VulkanicGal) -> GalResult<()> {
        gal.destroy(self.odd_to_even_set)?;
        gal.destroy(self.even_to_odd_set)?;
        gal.destroy(self.propagate_pipeline)?;
        gal.destroy(self.propagate_shader)?;
        gal.destroy(self.propagate_pipeline_layout)?;
        gal.destroy(self.propagate_layout)?;
        gal.destroy(self.temporal_mapping_buffer)?;
        gal.destroy(self.odd_init_set)?;
        gal.destroy(self.even_init_set)?;
        gal.destroy(self.init_pipeline)?;
        gal.destroy(self.init_shader)?;
        gal.destroy(self.init_pipeline_layout)?;
        gal.destroy(self.init_layout)
    }
}

pub(super) fn compute_binding(binding: u32, kind: ResourceBindingKind) -> ResourceBindingDesc {
    ResourceBindingDesc {
        binding,
        kind,
        stages: PipelineStageFlags::COMPUTE,
        array_count: 1,
        optional: false,
        dynamic_offset_count: 0,
    }
}

pub(super) fn compute_resource(
    binding: u32,
    resource: Handle,
    kind: ResourceBindingKind,
    access: AccessFlags,
) -> ResourceBinding {
    ResourceBinding {
        binding,
        array_index: 0,
        resource,
        kind,
        access,
        dynamic_offsets: Vec::new(),
        buffer_range: None,
    }
}

pub(super) fn propagation_bindings(
    occupancy: Handle,
    source: Handle,
    target: Handle,
    emission: Handle,
    tint: Handle,
    temporal_mapping: Handle,
) -> Vec<ResourceBinding> {
    vec![
        compute_resource(
            0,
            occupancy,
            ResourceBindingKind::StorageTexture,
            AccessFlags::READ,
        ),
        compute_resource(
            1,
            source,
            ResourceBindingKind::StorageTexture,
            AccessFlags::READ,
        ),
        compute_resource(
            2,
            target,
            ResourceBindingKind::StorageTexture,
            AccessFlags::WRITE,
        ),
        compute_resource(
            3,
            emission,
            ResourceBindingKind::UniformBuffer,
            AccessFlags::READ,
        ),
        compute_resource(
            4,
            tint,
            ResourceBindingKind::UniformBuffer,
            AccessFlags::READ,
        ),
        compute_resource(
            5,
            temporal_mapping,
            ResourceBindingKind::UniformBuffer,
            AccessFlags::READ,
        ),
    ]
}

pub(super) fn destroy_init_compute(
    gal: &mut VulkanicGal,
    even_set: Handle,
    odd_set: Option<Handle>,
    pipeline: Handle,
    shader: Handle,
    pipeline_layout: Handle,
    layout: Handle,
) {
    if let Some(odd_set) = odd_set {
        let _ = gal.destroy(odd_set);
    }
    let _ = gal.destroy(even_set);
    let _ = gal.destroy(pipeline);
    let _ = gal.destroy(shader);
    let _ = gal.destroy(pipeline_layout);
    let _ = gal.destroy(layout);
}

pub(super) fn destroy_propagate_compute(
    gal: &mut VulkanicGal,
    pipeline: Handle,
    shader: Handle,
    pipeline_layout: Handle,
    layout: Handle,
) {
    let _ = gal.destroy(pipeline);
    let _ = gal.destroy(shader);
    let _ = gal.destroy(pipeline_layout);
    let _ = gal.destroy(layout);
}

// These kernels encode source-derived initialization, six-neighbor propagation,
// temporal reprojection, and Complementary's alternating X half-rate schedule.
// These semantics remain private until the broader selected-source terrain
// route is explicitly admitted.
pub(super) const FLOOD_FILL_INIT_SHADER: &str = include_str!("glsl/flood_fill_init_shader.comp.glsl");

pub(super) const FLOOD_FILL_PROPAGATE_SHADER: &str = include_str!("glsl/flood_fill_propagate_shader.comp.glsl");

impl TerrainFloodFillGpuResources {
    pub fn create(
        gal: &mut VulkanicGal,
        descriptor: &VoxelLightVolumeDescriptor,
    ) -> GalResult<Self> {
        descriptor.validate()?;
        let extent = Extent3d {
            width: descriptor.extent.width,
            height: descriptor.extent.height,
            depth: descriptor.extent.depth,
        };
        let create_texture = |gal: &mut VulkanicGal, label: &str| {
            gal.create_texture(TextureDesc {
                label: format!("{}.flood-fill.{label}", descriptor.identity.as_str()),
                dimension: TextureDimension::D3,
                format: TextureFormat::Rgba16Float,
                extent,
                mip_levels: 1,
                array_layers: 1,
                usages: vec![TextureUsage::Sampled, TextureUsage::Storage],
            })
        };
        let even_texture = create_texture(gal, "even")?;
        let even_view = match create_volume_view(gal, descriptor, "even", even_texture) {
            Ok(view) => view,
            Err(error) => {
                let _ = gal.destroy(even_texture);
                return Err(error);
            }
        };
        let odd_texture = match create_texture(gal, "odd") {
            Ok(texture) => texture,
            Err(error) => {
                let _ = gal.destroy(even_view);
                let _ = gal.destroy(even_texture);
                return Err(error);
            }
        };
        let odd_view = match create_volume_view(gal, descriptor, "odd", odd_texture) {
            Ok(view) => view,
            Err(error) => {
                let _ = gal.destroy(odd_texture);
                let _ = gal.destroy(even_view);
                let _ = gal.destroy(even_texture);
                return Err(error);
            }
        };
        let sampler = match gal.create_sampler(SamplerDesc {
            label: format!("{}.flood-fill.sampler", descriptor.identity.as_str()),
            // The existing private volume contract samples discrete voxel
            // cells with one nearest sampler. Sampling-resource creation now
            // pairs that sampler with each view explicitly without changing
            // the established filter semantics.
            min_filter: SamplerFilter::Nearest,
            mag_filter: SamplerFilter::Nearest,
            mip_filter: SamplerFilter::Nearest,
            address_u: SamplerAddressMode::ClampToEdge,
            address_v: SamplerAddressMode::ClampToEdge,
            address_w: SamplerAddressMode::ClampToEdge,
            comparison: None,
        }) {
            Ok(sampler) => sampler,
            Err(error) => {
                let _ = gal.destroy(odd_view);
                let _ = gal.destroy(odd_texture);
                let _ = gal.destroy(even_view);
                let _ = gal.destroy(even_texture);
                return Err(error);
            }
        };
        let emission_buffer = match gal.create_buffer(BufferDesc {
            label: format!("{}.flood-fill.emission-table", descriptor.identity.as_str()),
            size: 256 * 16,
            memory: MemoryDomain::Upload,
            usages: vec![
                BufferUsage::Uniform,
                BufferUsage::HostWrite,
                BufferUsage::TransferDst,
            ],
        }) {
            Ok(buffer) => buffer,
            Err(error) => {
                let _ = gal.destroy(sampler);
                let _ = gal.destroy(odd_view);
                let _ = gal.destroy(odd_texture);
                let _ = gal.destroy(even_view);
                let _ = gal.destroy(even_texture);
                return Err(error);
            }
        };
        let tint_buffer = match gal.create_buffer(BufferDesc {
            label: format!("{}.flood-fill.tint-table", descriptor.identity.as_str()),
            size: (VOXEL_TINT_COUNT * 16) as u64,
            memory: MemoryDomain::Upload,
            usages: vec![
                BufferUsage::Uniform,
                BufferUsage::HostWrite,
                BufferUsage::TransferDst,
            ],
        }) {
            Ok(buffer) => buffer,
            Err(error) => {
                let _ = gal.destroy(emission_buffer);
                let _ = gal.destroy(sampler);
                let _ = gal.destroy(odd_view);
                let _ = gal.destroy(odd_texture);
                let _ = gal.destroy(even_view);
                let _ = gal.destroy(even_texture);
                return Err(error);
            }
        };
        Ok(Self {
            descriptor: descriptor.clone(),
            even_texture,
            even_view,
            odd_texture,
            odd_view,
            sampler,
            emission_buffer,
            tint_buffer,
            emission_generation: None,
            emission_upload_pending: false,
            tint_generation: None,
            tint_upload_pending: false,
        })
    }

    pub fn descriptor(&self) -> &VoxelLightVolumeDescriptor {
        &self.descriptor
    }

    pub(super) fn update_mapping(&mut self, descriptor: &VoxelLightVolumeDescriptor) -> GalResult<()> {
        descriptor.validate()?;
        let active = &self.descriptor;
        if descriptor.identity != active.identity
            || descriptor.shader_pack_generation != active.shader_pack_generation
            || descriptor.world_generation != active.world_generation
            || descriptor.resource_generation != active.resource_generation
            || descriptor.extent != active.extent
            || descriptor.requirements != active.requirements
        {
            return Err(GalError::invalid_argument(
                "flood-fill mapping update would reuse an incompatible D3 resource",
            ));
        }
        self.descriptor = descriptor.clone();
        Ok(())
    }

    /// A compute pass may bind the table only after the exact source-derived
    /// bytes reached the owned buffer.
    pub fn emission_ready(&self) -> bool {
        self.emission_generation == Some(self.descriptor.shader_pack_generation)
            && !self.emission_upload_pending
    }

    pub fn tint_ready(&self) -> bool {
        self.tint_generation == Some(self.descriptor.shader_pack_generation)
            && !self.tint_upload_pending
    }

    pub(super) fn has_pending_upload(&self) -> bool {
        self.emission_upload_pending || self.tint_upload_pending
    }

    pub(super) fn confirm_pending_uploads(&mut self) -> GalResult<()> {
        if self.emission_upload_pending {
            self.confirm_emission_submission()?;
        }
        if self.tint_upload_pending {
            self.confirm_tint_submission()?;
        }
        Ok(())
    }

    pub(super) fn discard_pending_uploads(&mut self) {
        self.discard_emission_submission();
        self.discard_tint_submission();
    }

    /// Returns the field read by the source-derived compute update for this
    /// frame. Native views remain private to the backend resource layer.
    pub fn propagation_source_view(&self, frame_counter: u64) -> Handle {
        if frame_counter & 1 == 0 {
            self.even_view
        } else {
            self.odd_view
        }
    }

    /// Returns the field written and then sampled by terrain for this frame.
    pub fn terrain_sample_view(&self, frame_counter: u64) -> Handle {
        if frame_counter & 1 == 0 {
            self.odd_view
        } else {
            self.even_view
        }
    }

    /// Records the selected source's semantic emission table exactly once per
    /// shader-pack generation. The table stays private until an explicit
    /// compute pass binds it through a normal GAL resource set.
    pub fn append_emission_upload(
        &mut self,
        table: &VoxelEmissionTable,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<bool> {
        if table.shader_pack_generation() != self.descriptor.shader_pack_generation {
            return Err(GalError::invalid_argument(
                "voxel emission table does not match flood-fill shader-pack generation",
            ));
        }
        if self.emission_upload_pending {
            return Err(GalError::invalid_argument(
                "voxel emission upload is already awaiting submission confirmation",
            ));
        }
        if self.emission_generation == Some(table.shader_pack_generation()) {
            return Ok(false);
        }
        operations.push(CommandOp::HostWriteBuffer {
            buffer: self.emission_buffer,
            offset: 0,
            data: table.std140_bytes(),
        });
        operations.push(CommandOp::Barrier(resource_barrier(
            self.emission_buffer,
            None,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        )));
        self.emission_upload_pending = true;
        Ok(true)
    }

    pub fn confirm_emission_submission(&mut self) -> GalResult<()> {
        if !self.emission_upload_pending {
            return Err(GalError::invalid_argument(
                "no voxel emission upload is pending confirmation",
            ));
        }
        self.emission_generation = Some(self.descriptor.shader_pack_generation);
        self.emission_upload_pending = false;
        Ok(())
    }

    pub fn discard_emission_submission(&mut self) {
        self.emission_upload_pending = false;
    }

    /// Records the selected source's `specialTintColor` table once per
    /// shader-pack generation. Propagation never substitutes an identity
    /// tint when this source-derived table is missing.
    pub fn append_tint_upload(
        &mut self,
        table: &VoxelEmissionTable,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<bool> {
        if table.shader_pack_generation() != self.descriptor.shader_pack_generation {
            return Err(GalError::invalid_argument(
                "voxel tint table does not match flood-fill shader-pack generation",
            ));
        }
        if self.tint_upload_pending {
            return Err(GalError::invalid_argument(
                "voxel tint upload is already awaiting submission confirmation",
            ));
        }
        if self.tint_generation == Some(table.shader_pack_generation()) {
            return Ok(false);
        }
        operations.push(CommandOp::HostWriteBuffer {
            buffer: self.tint_buffer,
            offset: 0,
            data: table.tint_std140_bytes(),
        });
        operations.push(CommandOp::Barrier(resource_barrier(
            self.tint_buffer,
            None,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        )));
        self.tint_upload_pending = true;
        Ok(true)
    }

    pub fn confirm_tint_submission(&mut self) -> GalResult<()> {
        if !self.tint_upload_pending {
            return Err(GalError::invalid_argument(
                "no voxel tint upload is pending confirmation",
            ));
        }
        self.tint_generation = Some(self.descriptor.shader_pack_generation);
        self.tint_upload_pending = false;
        Ok(())
    }

    pub fn discard_tint_submission(&mut self) {
        self.tint_upload_pending = false;
    }

    pub fn destroy(self, gal: &mut VulkanicGal) -> GalResult<()> {
        gal.destroy(self.tint_buffer)?;
        gal.destroy(self.emission_buffer)?;
        gal.destroy(self.sampler)?;
        gal.destroy(self.odd_view)?;
        gal.destroy(self.odd_texture)?;
        gal.destroy(self.even_view)?;
        gal.destroy(self.even_texture)
    }
}

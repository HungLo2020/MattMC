//! Semantic, camera-relative colored-voxel-light resource contract.
//!
//! Complementary calls these resources `voxel_sampler`, `floodfill_sampler`,
//! and `floodfill_sampler_copy`.  Those are implementation names, not part of
//! this contract: Java may only copy typed voxel data, while the shader-pack
//! runtime owns resource selection, generations, validation, and retirement.

use std::collections::BTreeMap;

use crate::render::vulkanic::error::{GalError, GalResult};

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum VoxelLightVolumeFormat {
    /// Complementary's voxel occupancy/material field (`r8ui`).
    OccupancyR8Uint,
    /// Complementary's flood-fill light field (`rgba16f`).
    LightingRgba16Float,
}

impl VoxelLightVolumeFormat {
    pub const fn bytes_per_texel(self) -> u32 {
        match self {
            Self::OccupancyR8Uint => 1,
            Self::LightingRgba16Float => 8,
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum VoxelLightVolumeKind {
    Occupancy,
    FloodFillEven,
    FloodFillOdd,
}

/// Complementary's `shadowcomp` reads the primary flood-fill field and writes
/// the copy field on even frames; odd frames perform the inverse operation.
/// Keep this source-derived semantic rule centralized so resource readiness,
/// compute dispatch, and terrain sampling cannot drift independently.
pub(crate) fn flood_fill_output_field_for_frame(frame_counter: u64) -> VoxelLightVolumeKind {
    if frame_counter & 1 == 0 {
        VoxelLightVolumeKind::FloodFillOdd
    } else {
        VoxelLightVolumeKind::FloodFillEven
    }
}

pub(crate) fn flood_fill_source_field_for_frame(frame_counter: u64) -> VoxelLightVolumeKind {
    match flood_fill_output_field_for_frame(frame_counter) {
        VoxelLightVolumeKind::FloodFillEven => VoxelLightVolumeKind::FloodFillOdd,
        VoxelLightVolumeKind::FloodFillOdd => VoxelLightVolumeKind::FloodFillEven,
        VoxelLightVolumeKind::Occupancy => unreachable!("flood-fill output cannot be occupancy"),
    }
}

/// Source-derived requirements for Complementary's colored-lighting volume.
/// This intentionally records semantic formats and update behavior, never an
/// Iris image name, binding point, or GL texture object.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct VoxelLightVolumeRequirements {
    pub extent: VoxelLightVolumeExtent,
    pub occupancy_format: VoxelLightVolumeFormat,
    pub lighting_format: VoxelLightVolumeFormat,
    pub ping_pong_by_frame_parity: bool,
    pub linear_filtered_lighting: bool,
    /// Source-derived scheduling semantics for updating this persistent field.
    /// This remains backend-neutral: it identifies temporal work rather than a
    /// compute shader, frame-buffer, or backend synchronization primitive.
    pub update_policy: VoxelLightVolumeUpdatePolicy,
}

/// Work scheduling declared by a shader pack for a persistent voxel-light
/// volume. A future runtime must either implement the exact policy or reject
/// the selected source; it must never silently reinterpret it as full-rate.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct VoxelLightVolumeUpdatePolicy {
    pub temporal_reprojection: bool,
    pub alternate_x_half_rate: bool,
    pub preserve_behind_view: bool,
}

impl VoxelLightVolumeUpdatePolicy {
    pub const fn full_rate() -> Self {
        Self {
            temporal_reprojection: false,
            alternate_x_half_rate: false,
            preserve_behind_view: false,
        }
    }

    /// Derives the selected source's semantic scheduling requirements without
    /// retaining a program object, OpenGL image, or Iris implementation detail.
    pub fn from_shadow_compute_source(source: &str) -> GalResult<Self> {
        if !source.contains("floor(previousCameraPosition) - floor(cameraPosition)") {
            return Err(GalError::invalid_argument(
                "voxel-light shadow compute source lacks temporal camera reprojection",
            ));
        }
        Ok(Self {
            temporal_reprojection: true,
            alternate_x_half_rate: source.contains("#define OPTIMIZATION_ACL_HALF_RATE_UPDATES"),
            preserve_behind_view: source.contains("#define OPTIMIZATION_ACL_BEHIND_PLAYER"),
        })
    }

    /// Selected-source execution may proceed only when every declared update
    /// behavior has a matching owned implementation. The private candidate
    /// implements temporal reprojection, alternating-X propagation, and the
    /// camera-direction-dependent behind-view preservation declared by the
    /// selected Complementary source.
    pub fn require_selected_source_support(self) -> GalResult<()> {
        Ok(())
    }
}

impl VoxelLightVolumeRequirements {
    pub fn complementary(colored_lighting: u32) -> GalResult<Self> {
        let (width, height, depth) = match colored_lighting {
            128 => (128, 64, 128),
            192 => (192, 96, 192),
            256 => (256, 128, 256),
            384 => (384, 192, 384),
            512 => (512, 256, 512),
            768 => (768, 256, 768),
            1024 => (1024, 256, 1024),
            _ => {
                return Err(GalError::unsupported_feature(format!(
                    "unsupported Complementary colored-light volume size {colored_lighting}"
                )))
            }
        };
        Ok(Self {
            extent: VoxelLightVolumeExtent {
                width,
                height,
                depth,
            },
            occupancy_format: VoxelLightVolumeFormat::OccupancyR8Uint,
            lighting_format: VoxelLightVolumeFormat::LightingRgba16Float,
            ping_pong_by_frame_parity: true,
            linear_filtered_lighting: true,
            update_policy: VoxelLightVolumeUpdatePolicy {
                temporal_reprojection: true,
                alternate_x_half_rate: true,
                preserve_behind_view: true,
            },
        })
    }
}

impl VoxelLightVolumeKind {
    pub const fn format(self) -> VoxelLightVolumeFormat {
        match self {
            Self::Occupancy => VoxelLightVolumeFormat::OccupancyR8Uint,
            Self::FloodFillEven | Self::FloodFillOdd => VoxelLightVolumeFormat::LightingRgba16Float,
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct VoxelLightVolumeExtent {
    pub width: u32,
    pub height: u32,
    pub depth: u32,
}

impl VoxelLightVolumeExtent {
    pub const fn texel_count(self) -> u64 {
        self.width as u64 * self.height as u64 * self.depth as u64
    }

    pub const fn byte_len(self, format: VoxelLightVolumeFormat) -> u64 {
        self.texel_count() * format.bytes_per_texel() as u64
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct VoxelLightVolumeRegion {
    pub x: u32,
    pub y: u32,
    pub z: u32,
    pub extent: VoxelLightVolumeExtent,
}

impl VoxelLightVolumeRegion {
    pub const fn whole(extent: VoxelLightVolumeExtent) -> Self {
        Self {
            x: 0,
            y: 0,
            z: 0,
            extent,
        }
    }

    pub fn validate(self, parent: VoxelLightVolumeExtent) -> GalResult<()> {
        if self.extent.width == 0 || self.extent.height == 0 || self.extent.depth == 0 {
            return Err(GalError::invalid_argument(
                "voxel-light volume update region is empty",
            ));
        }
        if self
            .x
            .checked_add(self.extent.width)
            .is_none_or(|end| end > parent.width)
            || self
                .y
                .checked_add(self.extent.height)
                .is_none_or(|end| end > parent.height)
            || self
                .z
                .checked_add(self.extent.depth)
                .is_none_or(|end| end > parent.depth)
        {
            return Err(GalError::invalid_argument(
                "voxel-light volume update region is out of bounds",
            ));
        }
        Ok(())
    }
}

/// Stable identity for a semantic volume. This is intentionally independent
/// of GL texture names, Vulkan images, Java shader objects, and pack handles.
#[derive(Clone, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub struct VoxelLightVolumeIdentity(String);

impl VoxelLightVolumeIdentity {
    pub fn new(value: impl Into<String>) -> GalResult<Self> {
        let value = value.into();
        if value.is_empty() || !value.starts_with("shader-pack:") {
            return Err(GalError::invalid_argument(
                "voxel-light volume identity must use the shader-pack: namespace",
            ));
        }
        Ok(Self(value))
    }

    pub fn as_str(&self) -> &str {
        &self.0
    }
}

/// Camera-relative mapping used by Complementary's `SceneToVoxel` function.
/// `camera_fraction` is the fractional camera position used to keep the volume
/// stable while the integer camera cell scrolls through the world.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct VoxelLightVolumeMapping {
    pub camera_cell: [i32; 3],
    pub camera_fraction: [f32; 3],
    pub scene_to_volume_scale: [f32; 3],
    pub valid_world_min: [i32; 3],
    pub valid_world_max_exclusive: [i32; 3],
    pub sample_normal_offset: f32,
}

impl VoxelLightVolumeMapping {
    pub fn complementary(
        extent: VoxelLightVolumeExtent,
        camera_cell: [i32; 3],
        camera_fraction: [f32; 3],
    ) -> GalResult<Self> {
        if extent.width == 0 || extent.height == 0 || extent.depth == 0 {
            return Err(GalError::invalid_argument(
                "voxel-light volume extent is empty",
            ));
        }
        if camera_fraction
            .iter()
            .any(|value| !value.is_finite() || !(0.0..1.0).contains(value))
        {
            return Err(GalError::invalid_argument(
                "voxel-light camera fraction must be finite and in [0, 1)",
            ));
        }
        let half = [
            extent.width as i32 / 2,
            extent.height as i32 / 2,
            extent.depth as i32 / 2,
        ];
        Ok(Self {
            camera_cell,
            camera_fraction,
            scene_to_volume_scale: [1.0, 1.0, 1.0],
            valid_world_min: [
                camera_cell[0] - half[0],
                camera_cell[1] - half[1],
                camera_cell[2] - half[2],
            ],
            valid_world_max_exclusive: [
                camera_cell[0] - half[0] + extent.width as i32,
                camera_cell[1] - half[1] + extent.height as i32,
                camera_cell[2] - half[2] + extent.depth as i32,
            ],
            // Complementary uses 0.55 for world terrain to avoid slab flicker.
            sample_normal_offset: 0.55,
        })
    }

    pub fn validate(&self, extent: VoxelLightVolumeExtent) -> GalResult<()> {
        if self
            .camera_fraction
            .iter()
            .any(|value| !value.is_finite() || !(0.0..1.0).contains(value))
            || self
                .scene_to_volume_scale
                .iter()
                .any(|value| !value.is_finite() || *value <= 0.0)
            || !self.sample_normal_offset.is_finite()
        {
            return Err(GalError::invalid_argument(
                "invalid voxel-light world-to-volume mapping",
            ));
        }
        for axis in 0..3 {
            let span =
                self.valid_world_max_exclusive[axis].saturating_sub(self.valid_world_min[axis]);
            let expected = [extent.width, extent.height, extent.depth][axis] as i32;
            if span != expected {
                return Err(GalError::invalid_argument(
                    "voxel-light valid world region does not match extent",
                ));
            }
        }
        Ok(())
    }
}

/// GPU-ready, backend-neutral representation of the source-derived
/// camera-relative mapping. The byte layout deliberately mirrors std140
/// `vec4` fields without exposing an Iris uniform location or native buffer.
///
/// `scene_to_volume_offset_and_normal_offset.xyz` implements
/// Complementary's `SceneToVoxel(scenePos)` offset. `w` retains the terrain
/// sample normal offset, which must stay coupled to the same volume mapping.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct VoxelLightVolumeShaderMapping {
    pub scene_to_volume_offset_and_normal_offset: [f32; 4],
    pub scene_to_volume_scale: [f32; 4],
    pub inverse_extent: [f32; 4],
    pub valid_world_min: [i32; 4],
    pub valid_world_max_exclusive: [i32; 4],
    /// Integer camera cell used to reconstruct Complementary's `scenePos`
    /// from a Rust world-space mesh position before applying the fractional
    /// camera offset above.
    pub camera_cell: [i32; 4],
}

impl VoxelLightVolumeShaderMapping {
    pub const STD140_SIZE: usize = 96;

    pub fn from_descriptor(descriptor: &VoxelLightVolumeDescriptor) -> GalResult<Self> {
        descriptor.validate()?;
        let extent = descriptor.extent;
        let mapping = &descriptor.mapping;
        let half = [
            extent.width as f32 * 0.5,
            extent.height as f32 * 0.5,
            extent.depth as f32 * 0.5,
        ];
        Ok(Self {
            scene_to_volume_offset_and_normal_offset: [
                mapping.camera_fraction[0] + half[0],
                mapping.camera_fraction[1] + half[1],
                mapping.camera_fraction[2] + half[2],
                mapping.sample_normal_offset,
            ],
            scene_to_volume_scale: [
                mapping.scene_to_volume_scale[0],
                mapping.scene_to_volume_scale[1],
                mapping.scene_to_volume_scale[2],
                0.0,
            ],
            inverse_extent: [
                1.0 / extent.width as f32,
                1.0 / extent.height as f32,
                1.0 / extent.depth as f32,
                0.0,
            ],
            valid_world_min: [
                mapping.valid_world_min[0],
                mapping.valid_world_min[1],
                mapping.valid_world_min[2],
                0,
            ],
            valid_world_max_exclusive: [
                mapping.valid_world_max_exclusive[0],
                mapping.valid_world_max_exclusive[1],
                mapping.valid_world_max_exclusive[2],
                0,
            ],
            camera_cell: [
                mapping.camera_cell[0],
                mapping.camera_cell[1],
                mapping.camera_cell[2],
                0,
            ],
        })
    }

    pub fn std140_bytes(self) -> [u8; Self::STD140_SIZE] {
        let mut bytes = [0; Self::STD140_SIZE];
        for (field_index, field) in [
            self.scene_to_volume_offset_and_normal_offset,
            self.scene_to_volume_scale,
            self.inverse_extent,
        ]
        .into_iter()
        .enumerate()
        {
            for (component_index, component) in field.into_iter().enumerate() {
                let offset = (field_index * 16) + (component_index * 4);
                bytes[offset..offset + 4].copy_from_slice(&component.to_le_bytes());
            }
        }
        for (field_index, field) in [
            self.valid_world_min,
            self.valid_world_max_exclusive,
            self.camera_cell,
        ]
        .into_iter()
        .enumerate()
        {
            for (component_index, component) in field.into_iter().enumerate() {
                let offset = ((field_index + 3) * 16) + (component_index * 4);
                bytes[offset..offset + 4].copy_from_slice(&component.to_le_bytes());
            }
        }
        bytes
    }

    /// Computes the normalized D3 coordinate using exactly the values sent to
    /// the owned shader binding. This is the CPU diagnostic counterpart to
    /// `SceneToVoxel(scenePos)` and prevents the source probes from using a
    /// subtly different camera-origin convention than the eventual shader.
    pub fn normalized_coordinate_for_world(
        self,
        world_position: [f32; 3],
        normal: [f32; 3],
    ) -> GalResult<[f32; 3]> {
        if world_position
            .iter()
            .chain(normal.iter())
            .any(|value| !value.is_finite())
        {
            return Err(GalError::invalid_argument(
                "voxel-light shader mapping position and normal must be finite",
            ));
        }
        let mut normalized = [0.0; 3];
        for axis in 0..3 {
            let world = world_position[axis]
                + normal[axis] * self.scene_to_volume_offset_and_normal_offset[3];
            normalized[axis] = (world - self.camera_cell[axis] as f32
                + self.scene_to_volume_offset_and_normal_offset[axis])
                * self.scene_to_volume_scale[axis]
                * self.inverse_extent[axis];
            if !(0.0..=1.0).contains(&normalized[axis]) {
                return Err(GalError::invalid_argument(format!(
                    "voxel-light shader mapping sample is out of bounds on axis {axis}: {}",
                    normalized[axis]
                )));
            }
        }
        Ok(normalized)
    }
}

#[derive(Clone, Debug, PartialEq)]
pub struct VoxelLightVolumeDescriptor {
    pub identity: VoxelLightVolumeIdentity,
    pub shader_pack_generation: u64,
    pub world_generation: u64,
    pub resource_generation: u64,
    pub extent: VoxelLightVolumeExtent,
    pub requirements: VoxelLightVolumeRequirements,
    pub mapping: VoxelLightVolumeMapping,
}

/// Per-frame semantic mapping for a persistent camera-relative volume. It
/// carries only the integer cells used by the selected source's
/// `floor(previousCameraPosition) - floor(cameraPosition)` reprojection; no
/// renderer object, texture coordinate, or native state crosses this boundary.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct VoxelLightVolumeFrameMapping {
    pub identity: VoxelLightVolumeIdentity,
    pub shader_pack_generation: u64,
    pub world_generation: u64,
    pub resource_generation: u64,
    pub camera_cell: [i32; 3],
}

impl VoxelLightVolumeFrameMapping {
    pub fn from_descriptor(descriptor: &VoxelLightVolumeDescriptor) -> Self {
        Self {
            identity: descriptor.identity.clone(),
            shader_pack_generation: descriptor.shader_pack_generation,
            world_generation: descriptor.world_generation,
            resource_generation: descriptor.resource_generation,
            camera_cell: descriptor.mapping.camera_cell,
        }
    }

    pub fn validate_against(&self, descriptor: &VoxelLightVolumeDescriptor) -> GalResult<()> {
        if self.identity != descriptor.identity
            || self.shader_pack_generation != descriptor.shader_pack_generation
            || self.world_generation != descriptor.world_generation
            || self.resource_generation != descriptor.resource_generation
        {
            return Err(GalError::invalid_argument(
                "voxel-light frame mapping mixes volume generations",
            ));
        }
        Ok(())
    }

    /// Generation identity alone is not enough to sample a completed field:
    /// after a camera-cell move, the temporal propagation must first produce
    /// the field for the descriptor's new camera-relative mapping.
    pub fn matches_descriptor(&self, descriptor: &VoxelLightVolumeDescriptor) -> bool {
        self.validate_against(descriptor).is_ok()
            && self.camera_cell == descriptor.mapping.camera_cell
    }

    /// Matches Complementary's `floor(previousCameraPosition) -
    /// floor(cameraPosition)` offset used to read the previous ping-pong
    /// field for the current cell.
    pub fn previous_to_current_offset(&self, previous: &Self) -> GalResult<[i32; 3]> {
        self.previous_camera_minus_current_offset(previous)
    }

    pub fn previous_camera_minus_current_offset(&self, previous: &Self) -> GalResult<[i32; 3]> {
        self.validate_same_volume(previous)?;
        Ok(std::array::from_fn(|axis| {
            previous.camera_cell[axis].saturating_sub(self.camera_cell[axis])
        }))
    }

    fn validate_same_volume(&self, other: &Self) -> GalResult<()> {
        if self.identity != other.identity
            || self.shader_pack_generation != other.shader_pack_generation
            || self.world_generation != other.world_generation
            || self.resource_generation != other.resource_generation
        {
            return Err(GalError::invalid_argument(
                "voxel-light frame mappings do not describe the same generation",
            ));
        }
        Ok(())
    }
}

/// Versioned, backend-neutral per-frame input for the flood-fill update. The
/// integer offset is consumed as `previousPos = position - offset`, matching
/// Complementary's shadowcomp program without exposing a uniform location or
/// a backend buffer binding.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct VoxelLightVolumeTemporalMapping {
    pub previous_camera_minus_current: [i32; 4],
    pub extent: [i32; 4],
    /// x: alternating X-half-rate enabled, y: frame parity, z: source asks
    /// for behind-view preservation.
    pub update_schedule: [i32; 4],
    /// Normalized camera-forward direction in the volume's world-space axes.
    /// This is derived from copied camera matrices, not from a backend program
    /// or renderer object.
    pub camera_forward: [f32; 4],
}

impl VoxelLightVolumeTemporalMapping {
    pub const STD140_SIZE: usize = 64;

    pub fn from_frame_mappings(
        current: &VoxelLightVolumeFrameMapping,
        previous: &VoxelLightVolumeFrameMapping,
        extent: VoxelLightVolumeExtent,
        policy: VoxelLightVolumeUpdatePolicy,
        frame_counter: u64,
        view_direction: Option<VoxelLightVolumeViewDirection>,
    ) -> GalResult<Self> {
        let offset = current.previous_camera_minus_current_offset(previous)?;
        let camera_forward = match (policy.preserve_behind_view, view_direction) {
            (true, Some(direction)) => direction.normalized_camera_forward,
            (true, None) => {
                return Err(GalError::invalid_argument(
                    "behind-view voxel-light preservation requires a camera direction",
                ));
            }
            (false, Some(direction)) => direction.normalized_camera_forward,
            (false, None) => [0.0, 0.0, 1.0],
        };
        Ok(Self {
            previous_camera_minus_current: [offset[0], offset[1], offset[2], 0],
            extent: [
                i32::try_from(extent.width)
                    .map_err(|_| GalError::invalid_argument("voxel width exceeds i32"))?,
                i32::try_from(extent.height)
                    .map_err(|_| GalError::invalid_argument("voxel height exceeds i32"))?,
                i32::try_from(extent.depth)
                    .map_err(|_| GalError::invalid_argument("voxel depth exceeds i32"))?,
                0,
            ],
            update_schedule: [
                i32::from(policy.alternate_x_half_rate),
                (frame_counter & 1) as i32,
                i32::from(policy.preserve_behind_view),
                0,
            ],
            camera_forward: [camera_forward[0], camera_forward[1], camera_forward[2], 0.0],
        })
    }

    pub fn std140_bytes(self) -> [u8; Self::STD140_SIZE] {
        let mut bytes = [0; Self::STD140_SIZE];
        for (field_index, field) in [
            self.previous_camera_minus_current,
            self.extent,
            self.update_schedule,
        ]
        .into_iter()
        .enumerate()
        {
            for (component_index, component) in field.into_iter().enumerate() {
                let offset = field_index * 16 + component_index * 4;
                bytes[offset..offset + 4].copy_from_slice(&component.to_le_bytes());
            }
        }
        for (component_index, component) in self.camera_forward.into_iter().enumerate() {
            let offset = 48 + component_index * 4;
            bytes[offset..offset + 4].copy_from_slice(&component.to_le_bytes());
        }
        bytes
    }
}

/// Semantic camera direction used by source-derived volume update policies.
/// The calculation follows the shader-pack expression
/// `mat3(model_view_inverse) * (projection_inverse * center_far_clip)` while
/// keeping matrix algebra out of backend lowering.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct VoxelLightVolumeViewDirection {
    pub normalized_camera_forward: [f32; 3],
}

impl VoxelLightVolumeViewDirection {
    pub fn from_camera_matrices(
        view_matrix: [f32; 16],
        projection_matrix: [f32; 16],
    ) -> GalResult<Self> {
        let projection_inverse = invert_column_major_mat4(projection_matrix, "projection")?;
        let model_view_inverse = invert_column_major_mat4(view_matrix, "view")?;
        let view_point = multiply_column_major_mat4_vec4(projection_inverse, [0.0, 0.0, 1.0, 1.0]);
        if !view_point.iter().all(|value| value.is_finite()) || view_point[3].abs() <= f32::EPSILON
        {
            return Err(GalError::invalid_argument(
                "voxel-light projection inverse produced an invalid center ray",
            ));
        }
        let view_direction = [
            view_point[0] / view_point[3],
            view_point[1] / view_point[3],
            view_point[2] / view_point[3],
        ];
        let world_direction = [
            model_view_inverse[0] * view_direction[0]
                + model_view_inverse[4] * view_direction[1]
                + model_view_inverse[8] * view_direction[2],
            model_view_inverse[1] * view_direction[0]
                + model_view_inverse[5] * view_direction[1]
                + model_view_inverse[9] * view_direction[2],
            model_view_inverse[2] * view_direction[0]
                + model_view_inverse[6] * view_direction[1]
                + model_view_inverse[10] * view_direction[2],
        ];
        normalize_direction(world_direction)
    }
}

fn multiply_column_major_mat4_vec4(matrix: [f32; 16], vector: [f32; 4]) -> [f32; 4] {
    std::array::from_fn(|row| {
        (0..4)
            .map(|column| matrix[column * 4 + row] * vector[column])
            .sum()
    })
}

fn normalize_direction(direction: [f32; 3]) -> GalResult<VoxelLightVolumeViewDirection> {
    if !direction.iter().all(|value| value.is_finite()) {
        return Err(GalError::invalid_argument(
            "voxel-light camera direction must be finite",
        ));
    }
    let squared_length = direction.iter().map(|value| value * value).sum::<f32>();
    if !squared_length.is_finite() || squared_length <= f32::EPSILON {
        return Err(GalError::invalid_argument(
            "voxel-light camera direction must have non-zero length",
        ));
    }
    let inverse_length = squared_length.sqrt().recip();
    Ok(VoxelLightVolumeViewDirection {
        normalized_camera_forward: std::array::from_fn(|axis| direction[axis] * inverse_length),
    })
}

pub(crate) fn invert_column_major_mat4(matrix: [f32; 16], label: &str) -> GalResult<[f32; 16]> {
    if !matrix.iter().all(|value| value.is_finite()) {
        return Err(GalError::invalid_argument(format!(
            "voxel-light {label} matrix must be finite"
        )));
    }
    let mut augmented = [[0.0f32; 8]; 4];
    for row in 0..4 {
        for column in 0..4 {
            augmented[row][column] = matrix[column * 4 + row];
        }
        augmented[row][row + 4] = 1.0;
    }
    for pivot_column in 0..4 {
        let pivot_row = (pivot_column..4)
            .max_by(|left, right| {
                augmented[*left][pivot_column]
                    .abs()
                    .total_cmp(&augmented[*right][pivot_column].abs())
            })
            .expect("pivot range is non-empty");
        if augmented[pivot_row][pivot_column].abs() <= f32::EPSILON {
            return Err(GalError::invalid_argument(format!(
                "voxel-light {label} matrix is not invertible"
            )));
        }
        augmented.swap(pivot_column, pivot_row);
        let pivot = augmented[pivot_column][pivot_column];
        for value in &mut augmented[pivot_column] {
            *value /= pivot;
        }
        for row in 0..4 {
            if row == pivot_column {
                continue;
            }
            let factor = augmented[row][pivot_column];
            for column in 0..8 {
                augmented[row][column] -= factor * augmented[pivot_column][column];
            }
        }
    }
    let mut inverse = [0.0f32; 16];
    for row in 0..4 {
        for column in 0..4 {
            inverse[column * 4 + row] = augmented[row][column + 4];
        }
    }
    Ok(inverse)
}

impl VoxelLightVolumeDescriptor {
    /// Returns whether two descriptors can share the same owned 3D resource
    /// allocation. Camera-relative mapping is deliberately excluded: it is
    /// dynamic frame semantics, while identity, generations, extent, and
    /// declared formats/policy define the resource generation.
    pub fn resource_compatible_with(&self, other: &Self) -> bool {
        self.identity == other.identity
            && self.shader_pack_generation == other.shader_pack_generation
            && self.world_generation == other.world_generation
            && self.resource_generation == other.resource_generation
            && self.extent == other.extent
            && self.requirements == other.requirements
    }

    pub fn validate(&self) -> GalResult<()> {
        if self.shader_pack_generation == 0
            || self.world_generation == 0
            || self.resource_generation == 0
        {
            return Err(GalError::invalid_argument(
                "voxel-light volume generations must be non-zero",
            ));
        }
        if self.extent.width < 2 || self.extent.height < 2 || self.extent.depth < 2 {
            return Err(GalError::invalid_argument(
                "voxel-light volume extent must support filtered sampling",
            ));
        }
        if self.requirements.extent != self.extent
            || self.requirements.occupancy_format != VoxelLightVolumeFormat::OccupancyR8Uint
            || self.requirements.lighting_format != VoxelLightVolumeFormat::LightingRgba16Float
            || !self.requirements.ping_pong_by_frame_parity
            || !self.requirements.linear_filtered_lighting
        {
            return Err(GalError::invalid_argument(
                "unsupported semantic voxel-light volume requirements",
            ));
        }
        self.mapping.validate(self.extent)
    }
}

#[derive(Clone, Debug, PartialEq)]
pub struct VoxelLightVolumeUpdate {
    pub identity: VoxelLightVolumeIdentity,
    pub shader_pack_generation: u64,
    pub world_generation: u64,
    pub resource_generation: u64,
    pub kind: VoxelLightVolumeKind,
    pub region: VoxelLightVolumeRegion,
    /// Owned semantic texel bytes. The caller can mutate its original memory
    /// after decoding without affecting Rust's resource state.
    pub texels: Vec<u8>,
}

impl VoxelLightVolumeUpdate {
    pub fn validate(&self, descriptor: &VoxelLightVolumeDescriptor) -> GalResult<()> {
        if self.identity != descriptor.identity
            || self.shader_pack_generation != descriptor.shader_pack_generation
            || self.world_generation != descriptor.world_generation
            || self.resource_generation != descriptor.resource_generation
        {
            return Err(GalError::invalid_argument(
                "stale or mixed voxel-light volume generation",
            ));
        }
        self.region.validate(descriptor.extent)?;
        let expected = self.region.extent.byte_len(self.kind.format());
        if self.texels.len() as u64 != expected {
            return Err(GalError::invalid_argument(format!(
                "voxel-light update byte length {} does not match {expected}",
                self.texels.len()
            )));
        }
        Ok(())
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum VoxelLightSamplingPolicy {
    /// Trilinear filtered flood-fill sampling, equivalent to Complementary's
    /// `texture(sampler3D, normalized_coordinate)` lookup.
    LinearClamp,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct VoxelLightVolumeBinding {
    pub active_light_field: VoxelLightVolumeKind,
    pub sampling: VoxelLightSamplingPolicy,
    pub resource_generation: u64,
}

/// Backend-neutral proof that all fields required by one selected terrain
/// frame belong to the same confirmed semantic volume generation. This is
/// deliberately distinct from a CPU cache: owned GPU generation state can be
/// ready without retaining CPU copies of flood-fill texels.
#[derive(Clone, Debug, PartialEq)]
pub struct VoxelLightVolumeReadiness {
    descriptor: VoxelLightVolumeDescriptor,
    occupancy_complete: bool,
    even_complete: bool,
    odd_complete: bool,
}

impl VoxelLightVolumeReadiness {
    pub fn new(
        descriptor: VoxelLightVolumeDescriptor,
        occupancy_complete: bool,
        even_complete: bool,
        odd_complete: bool,
    ) -> GalResult<Self> {
        descriptor.validate()?;
        Ok(Self {
            descriptor,
            occupancy_complete,
            even_complete,
            odd_complete,
        })
    }

    pub fn descriptor(&self) -> &VoxelLightVolumeDescriptor {
        &self.descriptor
    }

    pub fn binding_for_frame(&self, frame_counter: u64) -> GalResult<VoxelLightVolumeBinding> {
        if !self.occupancy_complete {
            return Err(GalError::invalid_argument(
                "incomplete voxel-light volume field Occupancy",
            ));
        }
        if !self.even_complete {
            return Err(GalError::invalid_argument(
                "incomplete voxel-light volume field FloodFillEven",
            ));
        }
        if !self.odd_complete {
            return Err(GalError::invalid_argument(
                "incomplete voxel-light volume field FloodFillOdd",
            ));
        }
        Ok(VoxelLightVolumeBinding {
            // Complementary reads `_copy` on even frames and the primary field
            // on odd frames. Names are deliberately not exposed by this API.
            active_light_field: flood_fill_output_field_for_frame(frame_counter),
            sampling: VoxelLightSamplingPolicy::LinearClamp,
            resource_generation: self.descriptor.resource_generation,
        })
    }
}

/// A source-to-contract probe used to correlate a terrain fragment with the
/// selected semantic volume. It deliberately uses world and normalized-volume
/// coordinates, never texture objects or API-specific UVW conventions.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct VoxelLightSampleProbe {
    pub world_position: [f32; 3],
    pub normal: [f32; 3],
    pub volume_coordinate: [f32; 3],
    pub normalized_coordinate: [f32; 3],
    pub active_light_field: VoxelLightVolumeKind,
    pub resource_generation: u64,
}

#[derive(Clone, Debug)]
pub struct VoxelLightVolumeCache {
    descriptor: Option<VoxelLightVolumeDescriptor>,
    fields: BTreeMap<VoxelLightVolumeKind, Vec<u8>>,
    complete_fields: BTreeMap<VoxelLightVolumeKind, bool>,
}

impl VoxelLightVolumeCache {
    pub fn new() -> Self {
        Self {
            descriptor: None,
            fields: BTreeMap::new(),
            complete_fields: BTreeMap::new(),
        }
    }

    pub fn descriptor(&self) -> Option<&VoxelLightVolumeDescriptor> {
        self.descriptor.as_ref()
    }

    pub fn replace_descriptor(&mut self, descriptor: VoxelLightVolumeDescriptor) -> GalResult<()> {
        descriptor.validate()?;
        if let Some(active) = &self.descriptor {
            if descriptor.identity == active.identity
                && descriptor.world_generation == active.world_generation
                && descriptor.shader_pack_generation == active.shader_pack_generation
                && descriptor.resource_generation <= active.resource_generation
            {
                return Err(GalError::invalid_argument(
                    "stale voxel-light volume resource generation",
                ));
            }
        }
        self.descriptor = Some(descriptor);
        self.fields.clear();
        self.complete_fields.clear();
        Ok(())
    }

    /// Updates only the camera-relative interpretation of an existing volume.
    /// Identity, formats, extents, and resource generations stay fixed.
    pub fn update_mapping(&mut self, mapping: VoxelLightVolumeMapping) -> GalResult<()> {
        let descriptor = self.descriptor.as_mut().ok_or_else(|| {
            GalError::invalid_argument("voxel-light volume mapping has no active descriptor")
        })?;
        mapping.validate(descriptor.extent)?;
        descriptor.mapping = mapping;
        Ok(())
    }

    pub fn invalidate_fields(&mut self) {
        self.fields.clear();
        self.complete_fields.clear();
    }

    pub fn apply_update(&mut self, update: VoxelLightVolumeUpdate) -> GalResult<()> {
        let descriptor = self.descriptor.as_ref().ok_or_else(|| {
            GalError::invalid_argument("voxel-light volume update has no descriptor")
        })?;
        update.validate(descriptor)?;
        let expected_total = descriptor.extent.byte_len(update.kind.format()) as usize;
        let field = self
            .fields
            .entry(update.kind)
            .or_insert_with(|| vec![0; expected_total]);
        if field.len() != expected_total {
            return Err(GalError::invalid_argument(
                "voxel-light field size disagrees with descriptor",
            ));
        }
        // A full update replaces the staged field atomically. Region updates are
        // bounded by the descriptor and retain the existing complete generation.
        if update.region == VoxelLightVolumeRegion::whole(descriptor.extent) {
            *field = update.texels;
            self.complete_fields.insert(update.kind, true);
            return Ok(());
        }
        if !self
            .complete_fields
            .get(&update.kind)
            .copied()
            .unwrap_or(false)
        {
            return Err(GalError::invalid_argument(
                "partial voxel-light update requires an initialized field",
            ));
        }
        copy_region(
            field,
            descriptor.extent,
            update.kind.format(),
            update.region,
            &update.texels,
        );
        Ok(())
    }

    pub fn binding_for_frame(&self, frame_counter: u64) -> GalResult<VoxelLightVolumeBinding> {
        self.readiness()?
            .ok_or_else(|| GalError::invalid_argument("missing voxel-light volume generation"))?
            .binding_for_frame(frame_counter)
    }

    /// Describes cache completeness using the same semantic contract consumed
    /// by source-plan admission. It intentionally exposes no texels or API
    /// resource identity.
    pub fn readiness(&self) -> GalResult<Option<VoxelLightVolumeReadiness>> {
        let Some(descriptor) = self.descriptor.clone() else {
            return Ok(None);
        };
        Ok(Some(VoxelLightVolumeReadiness::new(
            descriptor,
            self.complete_fields
                .get(&VoxelLightVolumeKind::Occupancy)
                .copied()
                .unwrap_or(false),
            self.complete_fields
                .get(&VoxelLightVolumeKind::FloodFillEven)
                .copied()
                .unwrap_or(false),
            self.complete_fields
                .get(&VoxelLightVolumeKind::FloodFillOdd)
                .copied()
                .unwrap_or(false),
        )?))
    }

    /// Returns the exact semantic coordinate used by Complementary terrain
    /// lookup: world position plus the outward-normal offset, then the
    /// camera-relative volume translation. A stale mapping is rejected rather
    /// than silently clamped.
    pub fn sample_probe(
        &self,
        frame_counter: u64,
        world_position: [f32; 3],
        normal: [f32; 3],
    ) -> GalResult<VoxelLightSampleProbe> {
        let descriptor = self
            .descriptor
            .as_ref()
            .ok_or_else(|| GalError::invalid_argument("missing voxel-light volume generation"))?;
        let binding = self.binding_for_frame(frame_counter)?;
        if world_position
            .iter()
            .chain(normal.iter())
            .any(|value| !value.is_finite())
        {
            return Err(GalError::invalid_argument(
                "voxel-light sample position and normal must be finite",
            ));
        }
        let shader_mapping = VoxelLightVolumeShaderMapping::from_descriptor(descriptor)?;
        let normalized_coordinate =
            shader_mapping.normalized_coordinate_for_world(world_position, normal)?;
        let extent = [
            descriptor.extent.width as f32,
            descriptor.extent.height as f32,
            descriptor.extent.depth as f32,
        ];
        let volume_coordinate = [
            normalized_coordinate[0] * extent[0],
            normalized_coordinate[1] * extent[1],
            normalized_coordinate[2] * extent[2],
        ];
        Ok(VoxelLightSampleProbe {
            world_position,
            normal,
            volume_coordinate,
            normalized_coordinate,
            active_light_field: binding.active_light_field,
            resource_generation: binding.resource_generation,
        })
    }

    pub fn retire_world_generation(&mut self, world_generation: u64) {
        if self
            .descriptor
            .as_ref()
            .is_some_and(|descriptor| descriptor.world_generation == world_generation)
        {
            self.descriptor = None;
            self.fields.clear();
            self.complete_fields.clear();
        }
    }
}

impl Default for VoxelLightVolumeCache {
    fn default() -> Self {
        Self::new()
    }
}

fn copy_region(
    destination: &mut [u8],
    full: VoxelLightVolumeExtent,
    format: VoxelLightVolumeFormat,
    region: VoxelLightVolumeRegion,
    source: &[u8],
) {
    let bytes_per_texel = format.bytes_per_texel() as usize;
    let row_bytes = region.extent.width as usize * bytes_per_texel;
    let mut source_offset = 0;
    for z in region.z..region.z + region.extent.depth {
        for y in region.y..region.y + region.extent.height {
            let destination_texel = ((z as usize * full.height as usize + y as usize)
                * full.width as usize)
                + region.x as usize;
            let destination_offset = destination_texel * bytes_per_texel;
            destination[destination_offset..destination_offset + row_bytes]
                .copy_from_slice(&source[source_offset..source_offset + row_bytes]);
            source_offset += row_bytes;
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn descriptor() -> VoxelLightVolumeDescriptor {
        let extent = VoxelLightVolumeExtent {
            width: 8,
            height: 4,
            depth: 8,
        };
        VoxelLightVolumeDescriptor {
            identity: VoxelLightVolumeIdentity::new(
                "shader-pack:complementary/colored-voxel-light",
            )
            .unwrap(),
            shader_pack_generation: 4,
            world_generation: 7,
            resource_generation: 9,
            extent,
            requirements: VoxelLightVolumeRequirements {
                extent,
                occupancy_format: VoxelLightVolumeFormat::OccupancyR8Uint,
                lighting_format: VoxelLightVolumeFormat::LightingRgba16Float,
                ping_pong_by_frame_parity: true,
                linear_filtered_lighting: true,
                update_policy: VoxelLightVolumeUpdatePolicy::full_rate(),
            },
            mapping: VoxelLightVolumeMapping::complementary(
                extent,
                [32, 64, -16],
                [0.25, 0.5, 0.75],
            )
            .unwrap(),
        }
    }

    #[test]
    fn frame_mapping_preserves_generation_and_derives_source_camera_offset() {
        let descriptor = descriptor();
        let previous = VoxelLightVolumeFrameMapping::from_descriptor(&descriptor);
        let mut current = previous.clone();
        current.camera_cell = [
            previous.camera_cell[0] + 3,
            previous.camera_cell[1] - 1,
            previous.camera_cell[2] - 2,
        ];
        assert_eq!(
            [-3, 1, 2],
            current.previous_to_current_offset(&previous).unwrap()
        );

        let mut stale = current.clone();
        stale.world_generation += 1;
        assert!(current.previous_to_current_offset(&stale).is_err());
        assert!(stale.validate_against(&descriptor).is_err());
    }

    #[test]
    fn resource_compatibility_excludes_mapping_but_not_generation_or_extent() {
        let descriptor = descriptor();
        let mut fractional = descriptor.clone();
        fractional.mapping.camera_fraction = [0.75, 0.25, 0.5];
        assert!(descriptor.resource_compatible_with(&fractional));

        let mut next_generation = descriptor.clone();
        next_generation.resource_generation += 1;
        assert!(!descriptor.resource_compatible_with(&next_generation));

        let mut different_extent = descriptor.clone();
        different_extent.extent.width += 1;
        assert!(!descriptor.resource_compatible_with(&different_extent));
    }

    #[test]
    fn temporal_mapping_packs_shadowcomp_previous_position_inputs() {
        let descriptor = descriptor();
        let previous = VoxelLightVolumeFrameMapping::from_descriptor(&descriptor);
        let mut current = previous.clone();
        current.camera_cell = [
            previous.camera_cell[0] + 2,
            previous.camera_cell[1] - 3,
            previous.camera_cell[2] + 1,
        ];
        let mapping = VoxelLightVolumeTemporalMapping::from_frame_mappings(
            &current,
            &previous,
            descriptor.extent,
            VoxelLightVolumeUpdatePolicy {
                temporal_reprojection: true,
                alternate_x_half_rate: true,
                preserve_behind_view: true,
            },
            7,
            Some(VoxelLightVolumeViewDirection {
                normalized_camera_forward: [0.0, 0.0, 1.0],
            }),
        )
        .unwrap();
        assert_eq!([-2, 3, -1, 0], mapping.previous_camera_minus_current);
        assert_eq!(
            [
                descriptor.extent.width as i32,
                descriptor.extent.height as i32,
                descriptor.extent.depth as i32,
                0,
            ],
            mapping.extent
        );
        assert_eq!([1, 1, 1, 0], mapping.update_schedule);
        assert_eq!([0.0, 0.0, 1.0, 0.0], mapping.camera_forward);
        assert_eq!((-2_i32).to_le_bytes(), mapping.std140_bytes()[0..4]);
        assert_eq!(
            (descriptor.extent.width as i32).to_le_bytes(),
            mapping.std140_bytes()[16..20]
        );
    }

    #[test]
    fn behind_view_policy_requires_a_valid_camera_direction() {
        let descriptor = descriptor();
        let mapping = VoxelLightVolumeFrameMapping::from_descriptor(&descriptor);
        assert!(VoxelLightVolumeTemporalMapping::from_frame_mappings(
            &mapping,
            &mapping,
            descriptor.extent,
            VoxelLightVolumeUpdatePolicy {
                temporal_reprojection: true,
                alternate_x_half_rate: true,
                preserve_behind_view: true,
            },
            0,
            None,
        )
        .is_err());
    }

    #[test]
    fn camera_direction_matches_the_source_center_far_ray_and_rejects_singular_matrices() {
        let identity = [
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ];
        assert_eq!(
            [0.0, 0.0, 1.0],
            VoxelLightVolumeViewDirection::from_camera_matrices(identity, identity)
                .unwrap()
                .normalized_camera_forward
        );
        assert!(VoxelLightVolumeViewDirection::from_camera_matrices([0.0; 16], identity).is_err());
    }

    #[test]
    fn shader_mapping_preserves_complementary_scene_to_voxel_inputs_in_std140_order() {
        let descriptor = descriptor();
        let mapping = VoxelLightVolumeShaderMapping::from_descriptor(&descriptor).unwrap();
        assert_eq!(
            [
                descriptor.mapping.camera_fraction[0] + descriptor.extent.width as f32 * 0.5,
                descriptor.mapping.camera_fraction[1] + descriptor.extent.height as f32 * 0.5,
                descriptor.mapping.camera_fraction[2] + descriptor.extent.depth as f32 * 0.5,
                descriptor.mapping.sample_normal_offset,
            ],
            mapping.scene_to_volume_offset_and_normal_offset
        );
        assert_eq!(
            [
                1.0 / descriptor.extent.width as f32,
                1.0 / descriptor.extent.height as f32,
                1.0 / descriptor.extent.depth as f32,
                0.0,
            ],
            mapping.inverse_extent
        );
        assert_eq!(
            [
                descriptor.mapping.scene_to_volume_scale[0],
                descriptor.mapping.scene_to_volume_scale[1],
                descriptor.mapping.scene_to_volume_scale[2],
                0.0,
            ],
            mapping.scene_to_volume_scale
        );
        let bytes = mapping.std140_bytes();
        assert_eq!(VoxelLightVolumeShaderMapping::STD140_SIZE, bytes.len());
        assert_eq!(
            mapping.scene_to_volume_offset_and_normal_offset[0].to_le_bytes(),
            bytes[0..4]
        );
        assert_eq!(
            mapping.scene_to_volume_scale[0].to_le_bytes(),
            bytes[16..20]
        );
        assert_eq!(mapping.inverse_extent[0].to_le_bytes(), bytes[32..36]);
        assert_eq!(mapping.valid_world_min[0].to_le_bytes(), bytes[48..52]);
        assert_eq!(
            mapping.valid_world_max_exclusive[0].to_le_bytes(),
            bytes[64..68]
        );
        assert_eq!(mapping.camera_cell[0].to_le_bytes(), bytes[80..84]);
    }

    fn update(
        kind: VoxelLightVolumeKind,
        region: VoxelLightVolumeRegion,
    ) -> VoxelLightVolumeUpdate {
        let descriptor = descriptor();
        VoxelLightVolumeUpdate {
            identity: descriptor.identity.clone(),
            shader_pack_generation: 4,
            world_generation: 7,
            resource_generation: 9,
            kind,
            region,
            texels: vec![
                kind.format().bytes_per_texel() as u8;
                region.extent.byte_len(kind.format()) as usize
            ],
        }
    }

    #[test]
    fn complete_generation_requires_all_semantic_fields_and_ping_pongs_by_frame() {
        let descriptor = descriptor();
        let mut cache = VoxelLightVolumeCache::new();
        cache.replace_descriptor(descriptor.clone()).unwrap();
        assert!(cache.binding_for_frame(0).is_err());
        for kind in [
            VoxelLightVolumeKind::Occupancy,
            VoxelLightVolumeKind::FloodFillEven,
            VoxelLightVolumeKind::FloodFillOdd,
        ] {
            cache
                .apply_update(update(
                    kind,
                    VoxelLightVolumeRegion::whole(descriptor.extent),
                ))
                .unwrap();
        }
        assert_eq!(
            VoxelLightVolumeKind::FloodFillOdd,
            cache.binding_for_frame(0).unwrap().active_light_field
        );
        assert_eq!(
            VoxelLightVolumeKind::FloodFillEven,
            cache.binding_for_frame(1).unwrap().active_light_field
        );
    }

    #[test]
    fn complementary_ping_pong_contract_swaps_source_and_output_by_frame_parity() {
        assert_eq!(
            VoxelLightVolumeKind::FloodFillEven,
            flood_fill_source_field_for_frame(0)
        );
        assert_eq!(
            VoxelLightVolumeKind::FloodFillOdd,
            flood_fill_output_field_for_frame(0)
        );
        assert_eq!(
            VoxelLightVolumeKind::FloodFillOdd,
            flood_fill_source_field_for_frame(1)
        );
        assert_eq!(
            VoxelLightVolumeKind::FloodFillEven,
            flood_fill_output_field_for_frame(1)
        );
    }

    #[test]
    fn shadow_compute_policy_requires_explicit_temporal_source_semantics() {
        assert!(
            VoxelLightVolumeUpdatePolicy::from_shadow_compute_source("void main() {}").is_err()
        );
        let policy = VoxelLightVolumeUpdatePolicy::from_shadow_compute_source(
            "#define OPTIMIZATION_ACL_HALF_RATE_UPDATES\n#define OPTIMIZATION_ACL_BEHIND_PLAYER\nvoid main() { vec3 offset = floor(previousCameraPosition) - floor(cameraPosition); }",
        )
        .unwrap();
        assert!(policy.temporal_reprojection);
        assert!(policy.alternate_x_half_rate);
        assert!(policy.preserve_behind_view);
        assert!(policy.require_selected_source_support().is_ok());
        assert!(VoxelLightVolumeUpdatePolicy {
            temporal_reprojection: true,
            alternate_x_half_rate: true,
            preserve_behind_view: false,
        }
        .require_selected_source_support()
        .is_ok());
    }

    #[test]
    fn partial_update_is_rejected_until_the_field_has_a_complete_base() {
        let descriptor = descriptor();
        let mut cache = VoxelLightVolumeCache::new();
        cache.replace_descriptor(descriptor.clone()).unwrap();
        let region = VoxelLightVolumeRegion {
            x: 1,
            y: 1,
            z: 1,
            extent: VoxelLightVolumeExtent {
                width: 2,
                height: 1,
                depth: 2,
            },
        };
        assert!(cache
            .apply_update(update(VoxelLightVolumeKind::FloodFillEven, region))
            .is_err());
        cache
            .apply_update(update(
                VoxelLightVolumeKind::FloodFillEven,
                VoxelLightVolumeRegion::whole(descriptor.extent),
            ))
            .unwrap();
        cache
            .apply_update(update(VoxelLightVolumeKind::FloodFillEven, region))
            .unwrap();
    }

    #[test]
    fn stale_or_mixed_generation_is_rejected_before_copying() {
        let descriptor = descriptor();
        let mut cache = VoxelLightVolumeCache::new();
        cache.replace_descriptor(descriptor.clone()).unwrap();
        let mut stale = update(
            VoxelLightVolumeKind::Occupancy,
            VoxelLightVolumeRegion::whole(descriptor.extent),
        );
        stale.world_generation = 8;
        assert!(cache.apply_update(stale).is_err());
        assert!(cache.fields.is_empty());
    }

    #[test]
    fn stale_descriptor_cannot_replace_a_complete_generation() {
        let descriptor = descriptor();
        let mut cache = VoxelLightVolumeCache::new();
        cache.replace_descriptor(descriptor.clone()).unwrap();
        assert!(cache.replace_descriptor(descriptor).is_err());
    }

    #[test]
    fn world_retirement_drops_owned_fields() {
        let descriptor = descriptor();
        let mut cache = VoxelLightVolumeCache::new();
        cache.replace_descriptor(descriptor.clone()).unwrap();
        cache
            .apply_update(update(
                VoxelLightVolumeKind::Occupancy,
                VoxelLightVolumeRegion::whole(descriptor.extent),
            ))
            .unwrap();
        cache.retire_world_generation(7);
        assert!(cache.descriptor().is_none());
        assert!(cache.fields.is_empty());
    }

    #[test]
    fn semantic_sample_probe_uses_camera_relative_mapping_and_rejects_bounds() {
        let descriptor = descriptor();
        let mut cache = VoxelLightVolumeCache::new();
        cache.replace_descriptor(descriptor.clone()).unwrap();
        for kind in [
            VoxelLightVolumeKind::Occupancy,
            VoxelLightVolumeKind::FloodFillEven,
            VoxelLightVolumeKind::FloodFillOdd,
        ] {
            cache
                .apply_update(update(
                    kind,
                    VoxelLightVolumeRegion::whole(descriptor.extent),
                ))
                .unwrap();
        }
        let probe = cache
            .sample_probe(0, [32.0, 64.0, -16.0], [0.0, 1.0, 0.0])
            .unwrap();
        assert_eq!(VoxelLightVolumeKind::FloodFillOdd, probe.active_light_field);
        assert!(probe
            .normalized_coordinate
            .iter()
            .all(|value| *value > 0.0 && *value < 1.0));
        assert!(cache
            .sample_probe(0, [10_000.0, 64.0, -16.0], [0.0, 1.0, 0.0])
            .is_err());
    }
}

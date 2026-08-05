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

impl VoxelLightVolumeDescriptor {
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
        let descriptor = self
            .descriptor
            .as_ref()
            .ok_or_else(|| GalError::invalid_argument("missing voxel-light volume generation"))?;
        for kind in [
            VoxelLightVolumeKind::Occupancy,
            VoxelLightVolumeKind::FloodFillEven,
            VoxelLightVolumeKind::FloodFillOdd,
        ] {
            if !self.complete_fields.get(&kind).copied().unwrap_or(false) {
                return Err(GalError::invalid_argument(format!(
                    "incomplete voxel-light volume field {kind:?}"
                )));
            }
        }
        Ok(VoxelLightVolumeBinding {
            // Complementary reads `_copy` on even frames and the primary field
            // on odd frames. Names are deliberately not exposed by this API.
            active_light_field: if frame_counter & 1 == 0 {
                VoxelLightVolumeKind::FloodFillEven
            } else {
                VoxelLightVolumeKind::FloodFillOdd
            },
            sampling: VoxelLightSamplingPolicy::LinearClamp,
            resource_generation: descriptor.resource_generation,
        })
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
        let mapping = descriptor.mapping;
        let extent = [
            descriptor.extent.width as f32,
            descriptor.extent.height as f32,
            descriptor.extent.depth as f32,
        ];
        let mut volume_coordinate = [0.0; 3];
        let mut normalized_coordinate = [0.0; 3];
        for axis in 0..3 {
            let world = world_position[axis] + normal[axis] * mapping.sample_normal_offset;
            volume_coordinate[axis] = (world + mapping.camera_fraction[axis]
                - mapping.camera_cell[axis] as f32
                + extent[axis] * 0.5)
                * mapping.scene_to_volume_scale[axis];
            normalized_coordinate[axis] = volume_coordinate[axis] / extent[axis];
            if !(0.0..=extent[axis]).contains(&volume_coordinate[axis]) {
                return Err(GalError::invalid_argument(format!(
                    "voxel-light sample is out of bounds on axis {axis}: {}",
                    volume_coordinate[axis]
                )));
            }
        }
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
            },
            mapping: VoxelLightVolumeMapping::complementary(
                extent,
                [32, 64, -16],
                [0.25, 0.5, 0.75],
            )
            .unwrap(),
        }
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
            VoxelLightVolumeKind::FloodFillEven,
            cache.binding_for_frame(0).unwrap().active_light_field
        );
        assert_eq!(
            VoxelLightVolumeKind::FloodFillOdd,
            cache.binding_for_frame(1).unwrap().active_light_field
        );
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
        assert_eq!(
            VoxelLightVolumeKind::FloodFillEven,
            probe.active_light_field
        );
        assert!(probe
            .normalized_coordinate
            .iter()
            .all(|value| *value > 0.0 && *value < 1.0));
        assert!(cache
            .sample_probe(0, [10_000.0, 64.0, -16.0], [0.0, 1.0, 0.0])
            .is_err());
    }
}

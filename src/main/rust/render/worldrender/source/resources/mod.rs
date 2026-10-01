//! Source color-pass targets, candidate resources, colored light and depth inputs.

mod distant_horizons;
mod color_targets;
mod candidates;
mod voxels;

pub(crate) use self::distant_horizons::*;
pub(crate) use self::candidates::*;
pub(crate) use self::voxels::*;

use super::*;

impl WorldPrimitiveFrontend {

}

impl WorldPrimitiveFrontend {

    /// Converts the coarse frame-owned camera/world identity into the
    /// camera-relative mapping consumed by the private occupancy runtime.
    /// This is deliberately a semantic conversion: no texture, shader, or
    /// backend identity is available here.
    pub(crate) fn voxel_volume_mapping_from_frame(
        frame: WorldVoxelVolumeFrame,
        descriptor: &VoxelLightVolumeDescriptor,
    ) -> GalResult<VoxelLightVolumeMapping> {
        if !frame.enabled {
            return Err(GalError::invalid_argument(
                "private terrain occupancy requires an enabled voxel-volume frame record",
            ));
        }
        if frame.world_generation != descriptor.world_generation
            || frame.resource_generation != descriptor.resource_generation
        {
            return Err(GalError::invalid_argument(
                "voxel-volume frame record does not match the private occupancy generation",
            ));
        }

        let camera = frame.camera_world_position;
        let mut cell = [0_i32; 3];
        let mut fraction = [0.0_f32; 3];
        for axis in 0..3 {
            let coordinate = camera[axis];
            if !coordinate.is_finite() {
                return Err(GalError::invalid_argument(
                    "voxel-volume camera position must be finite",
                ));
            }
            let floored = coordinate.floor();
            if floored < i32::MIN as f32 || floored > i32::MAX as f32 {
                return Err(GalError::invalid_argument(
                    "voxel-volume camera cell is outside the supported i32 range",
                ));
            }
            cell[axis] = floored as i32;
            fraction[axis] = coordinate - floored;
        }
        VoxelLightVolumeMapping::complementary(descriptor.extent, cell, fraction)
    }
}

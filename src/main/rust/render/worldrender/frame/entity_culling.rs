//! Immutable gameplay world bounds and camera inputs, independent of GPU meshes.

use crate::render::vulkanic::error::{GalError, GalResult};

pub const ENTITY_CULL_ELIGIBLE: u32 = 1;
pub const ENTITY_CULL_BYPASS_FRUSTUM: u32 = 2;
pub const ENTITY_CULL_PLAYER_GROUP: u32 = 4;
pub const ENTITY_CULL_UNRESOLVED_HOOKS: u32 = 8;
pub const ENTITY_CULL_LEASH_HOLDER: u32 = 16;
pub const ENTITY_CULL_PLAYER_ONLY_VARIANT: u32 = 32;
pub const ENTITY_CULL_FLAGS: u32 = 63;

#[derive(Clone, Copy, Debug, PartialEq)]
pub struct WorldEntityCullingInputs {
    pub flags: u32,
    /// Absolute min xyz, max xyz. Frozen's distance boxes cast these to floats.
    pub bounds: [f64; 6],
    pub leash_holder_bounds: Option<[f64; 6]>,
    pub camera: [f64; 3],
}

impl WorldEntityCullingInputs {
    pub fn validate(self) -> GalResult<()> {
        if self.camera.iter().any(|value| !value.is_finite()) {
            return Err(GalError::invalid_argument(
                "entity culling camera must be finite",
            ));
        }
        if self.flags & !ENTITY_CULL_FLAGS != 0
            || (self.flags & ENTITY_CULL_LEASH_HOLDER != 0) != self.leash_holder_bounds.is_some()
            || (self.flags & ENTITY_CULL_PLAYER_ONLY_VARIANT != 0
                && self.flags & ENTITY_CULL_PLAYER_GROUP == 0)
        {
            return Err(GalError::invalid_argument(
                "incoherent copied entity culling flags",
            ));
        }
        for bounds in std::iter::once(self.bounds).chain(self.leash_holder_bounds) {
            if bounds.iter().any(|value| !value.is_finite())
                || (0..3).any(|axis| bounds[axis] > bounds[axis + 3])
            {
                return Err(GalError::invalid_argument(
                    "copied entity culling bounds must be finite and ordered",
                ));
            }
        }
        Ok(())
    }
}

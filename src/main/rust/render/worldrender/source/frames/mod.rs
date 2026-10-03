//! Per-frame source terrain/entity/hand/material frames, transactions and voxel sources.

mod terrain;
mod entities;
mod casters;
mod materials;
mod voxels;

pub(crate) use self::terrain::*;
pub(crate) use self::entities::*;
pub(crate) use self::casters::*;
pub(crate) use self::materials::*;
pub(crate) use self::voxels::*;

use super::*;

pub(crate) const IDENTITY_WORLD_TRANSFORM: [f32; 16] = [
    1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
];

impl WorldPrimitiveFrontend {

}

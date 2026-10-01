//! GUI entry points and decoding: frames of sprites, affine and tiled quads
//! and meshes, GUI asset and raw-image updates, and atlas references, all
//! handed to the GUI renderer.

mod atlas;
mod quads;
mod mesh;
mod frame;
mod assets;

#[cfg(test)]
pub(crate) use self::atlas::*;
pub(crate) use self::quads::*;
pub(crate) use self::mesh::*;
pub(crate) use self::frame::*;
#[cfg(test)]
pub(crate) use self::assets::*;

use crate::render::bridge::*;
use crate::render::guirender::frontend::{
    GUI_MAX_RAW_IMAGE_BYTES_TOTAL, GUI_MAX_RAW_IMAGE_PIXELS, GUI_MAX_VIEWPORT_AXIS,
};
use crate::render::guirender::mesh::GUI_MESH_MAX_FRAME_PAYLOAD_BYTES;


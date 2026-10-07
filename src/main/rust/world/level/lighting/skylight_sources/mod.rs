//! Chunk skylight-source reconstruction from packed sections.
mod ffi;
mod scan;
#[cfg(test)]
mod tests;

use crate::content::block::{BlockRegistry, FaceId, StateFlags, StateId};
use std::collections::HashMap;
use std::sync::OnceLock;

/// The descriptor of a custom `BlockState` subclass, which Java scans itself.
pub(crate) const CUSTOM: u32 = u32::MAX;
/// Most up/down faces the scan supports.
const MAX_FACES: usize = 512;
const UP: usize = 1;
const DOWN: usize = 0;

/// Per state `(getLightBlock() != 0) | up << 1 | down << 16` over the
/// distinct up and down light occlusion faces (empty first, then in state
/// order), and `Shapes.faceShapeOccludes` over those faces.
pub(crate) struct Tables {
    pub descriptors: Vec<u32>,
    pub faces: usize,
    pub edges: Vec<u8>,
    /// Some state is [`CUSTOM`]: the global palette stays with Java.
    pub custom: bool,
}

/// `None` when the states have more than [`MAX_FACES`] distinct up and down faces.
pub(crate) fn tables(registry: &BlockRegistry) -> Option<Tables> {
    let mut local: HashMap<FaceId, u32> = HashMap::new();
    let mut faces = vec![FaceId(0)];
    local.insert(FaceId(0), 0);
    let mut intern = |face: FaceId| -> u32 {
        *local.entry(face).or_insert_with(|| {
            faces.push(face);
            faces.len() as u32 - 1
        })
    };
    let mut custom = false;
    let mut descriptors = Vec::with_capacity(registry.state_count());
    for s in 0..registry.state_count() {
        let state = StateId(s as u16);
        if registry.flags(state).contains(StateFlags::CUSTOM) {
            custom = true;
            descriptors.push(CUSTOM);
            continue;
        }
        let up = intern(registry.light_face(state, UP));
        let down = intern(registry.light_face(state, DOWN));
        descriptors.push(u32::from(registry.light_block(state) != 0) | up << 1 | down << 16);
    }
    if faces.len() > MAX_FACES {
        return None;
    }
    let mut edges = Vec::with_capacity(faces.len() * faces.len());
    for &down in &faces {
        for &up in &faces {
            edges.push(u8::from(registry.face_occludes(down, up)));
        }
    }
    Some(Tables { descriptors, faces: faces.len(), edges, custom })
}

/// [`tables`] of the installed registry; `None` until it is installed or
/// when its faces do not fit.
pub(crate) fn installed_tables() -> Option<&'static Tables> {
    static TABLES: OnceLock<Option<Tables>> = OnceLock::new();
    let registry = crate::content::block::installed()?;
    TABLES.get_or_init(|| tables(registry)).as_ref()
}

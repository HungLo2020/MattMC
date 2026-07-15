use super::face::{ao_face_data, AoFace};
use super::NativeSectionBlockRecord;

/// Per-quad AO face cache.
///
/// A smooth quad can sample the same direct and offset face several times.
/// Entries are keyed by direction plus offset flag and are valid only for the
/// current `NativeSectionBlockRecord`. Reusing a cache across records would mix
/// neighborhood light words and break parity.
#[derive(Default)]
pub(in crate::render::chunk::meshing) struct AoFaceCache {
    faces: [Option<AoFace>; 12],
}

impl AoFaceCache {
    #[inline(always)]
    pub(in crate::render::chunk::meshing) fn get(
        &mut self,
        block: &NativeSectionBlockRecord,
        direction: i32,
        offset: bool,
    ) -> AoFace {
        let index = ao_face_cache_index(direction, offset);
        if let Some(face) = self.faces[index] {
            return face;
        }
        let face = ao_face_data(block, direction, offset);
        self.faces[index] = Some(face);
        face
    }
}

#[inline(always)]
fn ao_face_cache_index(direction: i32, offset: bool) -> usize {
    let direction = direction.clamp(0, 5) as usize;
    (direction << 1) | usize::from(offset)
}

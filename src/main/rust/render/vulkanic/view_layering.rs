//! Explicit indexed-model view offsets, shared by base materials and overlays.
use super::error::{GalError, GalResult};

pub const PERSPECTIVE_FLAG: u32 = 4;
pub const ORTHOGRAPHIC_FLAG: u32 = 8;
pub const FLAGS: u32 = PERSPECTIVE_FLAG | ORTHOGRAPHIC_FLAG;

#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub enum Projection { Perspective, Orthographic }

pub fn from_flags(flags: u32) -> Option<Projection> {
    match flags & FLAGS {
        PERSPECTIVE_FLAG => Some(Projection::Perspective),
        ORTHOGRAPHIC_FLAG => Some(Projection::Orthographic),
        _ => None,
    }
}

pub fn validate_flags(flags: u32, entity: bool, ordinary: bool) -> GalResult<()> {
    if flags & FLAGS != 0 && (flags & FLAGS == FLAGS || flags & !FLAGS != 0 || !entity || !ordinary) {
        return Err(GalError::invalid_argument("view layering requires one projection and an ordinary entity mesh"));
    }
    Ok(())
}

/// Frozen ModelViewMat.scale/translate: postmultiply before model placement.
/// Never infer projection conventions from matrix coefficients.
pub fn apply(mut view: [f32; 16], projection: Option<Projection>) -> GalResult<[f32; 16]> {
    if view.iter().any(|value| !value.is_finite()) {
        return Err(GalError::invalid_argument("non-finite layered view matrix"));
    }
    match projection {
        Some(Projection::Perspective) => {
            for value in &mut view[..12] { *value *= 1.0 - 1.0 / 4096.0; }
        }
        Some(Projection::Orthographic) => {
            for row in 0..4 { view[12 + row] += view[8 + row] * (1.0 / 512.0); }
        }
        None => {}
    }
    if view.iter().any(|value| !value.is_finite()) {
        return Err(GalError::invalid_argument("view layering overflow"));
    }
    Ok(view)
}

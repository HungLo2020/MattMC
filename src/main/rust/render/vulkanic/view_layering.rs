//! Explicit indexed-model view offsets, shared by base materials and overlays.
use super::error::{GalError, GalResult};

pub const PERSPECTIVE_FLAG: u32 = 4;
pub const ORTHOGRAPHIC_FLAG: u32 = 8;
pub const FLAGS: u32 = PERSPECTIVE_FLAG | ORTHOGRAPHIC_FLAG;

#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub enum Projection {
    Perspective,
    Orthographic,
}

pub fn from_flags(flags: u32) -> Option<Projection> {
    match flags & FLAGS {
        PERSPECTIVE_FLAG => Some(Projection::Perspective),
        ORTHOGRAPHIC_FLAG => Some(Projection::Orthographic),
        _ => None,
    }
}

pub fn validate_flags(flags: u32, entity: bool, ordinary: bool) -> GalResult<()> {
    if flags & FLAGS != 0 && (flags & FLAGS == FLAGS || flags & !FLAGS != 0 || !entity || !ordinary)
    {
        return Err(GalError::invalid_argument(
            "view layering requires one projection and an ordinary entity mesh",
        ));
    }
    Ok(())
}

/// Applies the same layering to a column-major model transform instead of the
/// view: `view * L * model == view * (L * model)`. Source shader-pack writers
/// use this so `gbufferModelView` stays the world view (as Iris exposes it)
/// while vertex positions receive vanilla's layered ModelViewMat.
pub fn apply_to_model(mut model: [f32; 16], projection: Option<Projection>) -> GalResult<[f32; 16]> {
    if model.iter().any(|value| !value.is_finite()) {
        return Err(GalError::invalid_argument("non-finite layered model matrix"));
    }
    match projection {
        Some(Projection::Perspective) => {
            // L = diag(s, s, s, 1): scales rows 0..3 of every column.
            for column in 0..4 {
                for row in 0..3 {
                    model[column * 4 + row] *= 1.0 - 1.0 / 4096.0;
                }
            }
        }
        Some(Projection::Orthographic) => {
            // L = translate(0, 0, 1/512): row 2 += row 3 / 512.
            for column in 0..4 {
                model[column * 4 + 2] += model[column * 4 + 3] * (1.0 / 512.0);
            }
        }
        None => {}
    }
    if model.iter().any(|value| !value.is_finite()) {
        return Err(GalError::invalid_argument("model layering overflow"));
    }
    Ok(model)
}

/// Frozen ModelViewMat.scale/translate: postmultiply before model placement.
/// Never infer projection conventions from matrix coefficients.
pub fn apply(mut view: [f32; 16], projection: Option<Projection>) -> GalResult<[f32; 16]> {
    if view.iter().any(|value| !value.is_finite()) {
        return Err(GalError::invalid_argument("non-finite layered view matrix"));
    }
    match projection {
        Some(Projection::Perspective) => {
            for value in &mut view[..12] {
                *value *= 1.0 - 1.0 / 4096.0;
            }
        }
        Some(Projection::Orthographic) => {
            for row in 0..4 {
                view[12 + row] += view[8 + row] * (1.0 / 512.0);
            }
        }
        None => {}
    }
    if view.iter().any(|value| !value.is_finite()) {
        return Err(GalError::invalid_argument("view layering overflow"));
    }
    Ok(view)
}

#[cfg(test)]
mod model_layering_tests {
    use super::*;

    fn mul(a: &[f32; 16], b: &[f32; 16]) -> [f32; 16] {
        let mut out = [0.0; 16];
        for c in 0..4 {
            for r in 0..4 {
                out[c * 4 + r] = (0..4).map(|k| a[k * 4 + r] * b[c * 4 + k]).sum();
            }
        }
        out
    }

    #[test]
    fn model_layering_matches_view_layering() {
        let view = [
            0.6, 0.1, -0.79, 0.0, 0.2, 0.97, 0.05, 0.0, 0.77, -0.2, 0.6, 0.0, 0.01, -0.02, 0.03, 1.0,
        ];
        let model = [
            1.0, 0.0, 0.0, 0.0, 0.0, 0.8, 0.6, 0.0, 0.0, -0.6, 0.8, 0.0, 3.5, -1.25, 7.0, 1.0,
        ];
        for projection in [Projection::Perspective, Projection::Orthographic] {
            let expected = mul(&apply(view, Some(projection)).unwrap(), &model);
            let actual = mul(&view, &apply_to_model(model, Some(projection)).unwrap());
            for (e, a) in expected.iter().zip(actual.iter()) {
                assert!((e - a).abs() < 1e-5, "{projection:?}: {expected:?} vs {actual:?}");
            }
        }
    }
}

//! Rasterizer state of a mesh draw: winding, culling and model transform math.

use super::*;

/// The standard 3D item PIP route accepts vanilla's SOLID, CUTOUT, and
/// TRANSLUCENT item layers. Their alpha threshold is explicit per draw, while their
/// fixed-function policy is shared: depth-tested, back-face-culled draws.
/// Translucent layers additionally use the explicit GAL alpha blend equation;
/// depth writes remain enabled to match vanilla's item-entity translucent
/// render type ordering inside the private PIP target.
/// Keeping this policy here prevents a GUI texture-group blend policy from
/// silently changing copied item-model geometry.
pub(super) fn gui_mesh_raster_state(
    material_mode: GuiMeshMaterialMode,
) -> (CullMode, BlendMode, Option<CompareOp>, bool) {
    match material_mode {
        GuiMeshMaterialMode::ModelOverlay => (
            CullMode::None,
            BlendMode::Alpha,
            Some(CompareOp::LessOrEqual),
            false,
        ),
        GuiMeshMaterialMode::Panorama => (CullMode::None, BlendMode::Disabled, None, false),
        GuiMeshMaterialMode::EntityCutoutNoCull => (
            CullMode::None,
            BlendMode::Disabled,
            Some(CompareOp::LessOrEqual),
            true,
        ),
        GuiMeshMaterialMode::EntityTranslucentNoCull => (
            CullMode::None,
            BlendMode::Alpha,
            Some(CompareOp::LessOrEqual),
            true,
        ),
        GuiMeshMaterialMode::EntityDecalCutoutNoCull => (
            CullMode::None,
            BlendMode::Disabled,
            Some(CompareOp::Equal),
            true,
        ),
        GuiMeshMaterialMode::Opaque | GuiMeshMaterialMode::Cutout => (
            CullMode::Back,
            BlendMode::Disabled,
            Some(CompareOp::LessOrEqual),
            true,
        ),
        GuiMeshMaterialMode::Translucent => (
            CullMode::Back,
            BlendMode::Alpha,
            Some(CompareOp::LessOrEqual),
            true,
        ),
        GuiMeshMaterialMode::Glint => (
            CullMode::None,
            BlendMode::SrcColorAdditive,
            Some(CompareOp::Equal),
            false,
        ),
    }
}

pub(super) fn transformed_front_face(
    matrix: [f32; 16],
    vertices: &[GuiMeshPreparedVertex],
    indices: &[u32],
) -> GalResult<crate::render::vulkanic::resources::FrontFace> {
    let determinant = model_transform_determinant(matrix)?;
    // The mesh vertex stage maps GUI pixels to top-left-origin clip space,
    // which contributes one final Y reflection. This must be included with
    // the copied model basis when deciding the front face. Vanilla's
    // standard PIP pose itself is reflected, so its complete transform
    // remains counter-clockwise rather than being culled as an interior.
    let mut front_face = if determinant.is_sign_negative() {
        crate::render::vulkanic::resources::FrontFace::CounterClockwise
    } else {
        crate::render::vulkanic::resources::FrontFace::Clockwise
    };

    // A copied GUI item batch is one baked quad. Its source winding is a
    // per-face semantic, so the item transform alone cannot choose culling
    // correctly for every model face. Reconcile the first copied triangle
    // against its transformed vertex normal before choosing raster state.
    let [first, second, third] = first_triangle_indices(indices, vertices.len())?;
    let first = vertices[first];
    let second = vertices[second];
    let third = vertices[third];
    let geometric_normal = cross(
        subtract(second.position, first.position),
        subtract(third.position, first.position),
    );
    let average_normal = normalize(add(add(first.normal, second.normal), third.normal))?;
    let alignment = dot(geometric_normal, average_normal);
    if !alignment.is_finite() || alignment.abs() <= f32::EPSILON {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI mesh triangle winding cannot be reconciled with its copied normal",
        ));
    }
    // Under a reflected transform the geometric cross product changes handedness
    // while the inverse-transposed normal intentionally does not. A negative
    // alignment is therefore expected precisely when the model determinant is
    // negative. Only the opposite relation denotes an independently reversed
    // source quad.
    if alignment.is_sign_negative() != determinant.is_sign_negative() {
        front_face = flip_front_face(front_face);
    }
    Ok(front_face)
}

pub(super) fn model_transform_determinant(matrix: [f32; 16]) -> GalResult<f32> {
    let a = matrix[0];
    let b = matrix[4];
    let c = matrix[8];
    let d = matrix[1];
    let e = matrix[5];
    let f = matrix[9];
    let g = matrix[2];
    let h = matrix[6];
    let i = matrix[10];
    let determinant = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g);
    if !determinant.is_finite() || determinant.abs() <= f32::EPSILON {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI mesh model transform has no usable winding determinant",
        ));
    }
    Ok(determinant)
}

/// Native item meshes carry original model-space normals. Resolve the
/// inverse transpose in Rust, including the native GUI Y reflection.
/// Explicit/PIP meshes already carry resolved normals and do not use this.
pub(super) fn transform_model_item_normal(matrix: [f32; 16], normal: [f32; 3]) -> GalResult<[f32; 3]> {
    let determinant = model_transform_determinant(matrix)?;
    let a = [matrix[0], matrix[1], matrix[2]];
    let b = [matrix[4], matrix[5], matrix[6]];
    let c = [matrix[8], matrix[9], matrix[10]];
    let cofactors = [cross(b, c), cross(c, a), cross(a, b)];
    normalize(std::array::from_fn(|axis| {
        (cofactors[0][axis] * normal[0]
            + cofactors[1][axis] * normal[1]
            + cofactors[2][axis] * normal[2])
            / determinant
    }))
}

pub(super) fn first_triangle_indices(indices: &[u32], vertex_count: usize) -> GalResult<[usize; 3]> {
    let [first, second, third, ..] = indices else {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI mesh requires a first triangle to establish front-face orientation",
        ));
    };
    let indices = [*first as usize, *second as usize, *third as usize];
    if indices.iter().any(|index| *index >= vertex_count) {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI mesh front-face triangle references a missing vertex",
        ));
    }
    Ok(indices)
}

pub(super) fn flip_front_face(front_face: crate::render::vulkanic::resources::FrontFace) -> crate::render::vulkanic::resources::FrontFace {
    match front_face {
        crate::render::vulkanic::resources::FrontFace::Clockwise => crate::render::vulkanic::resources::FrontFace::CounterClockwise,
        crate::render::vulkanic::resources::FrontFace::CounterClockwise => crate::render::vulkanic::resources::FrontFace::Clockwise,
    }
}

pub(super) fn subtract(left: [f32; 3], right: [f32; 3]) -> [f32; 3] {
    [left[0] - right[0], left[1] - right[1], left[2] - right[2]]
}

pub(super) fn add(left: [f32; 3], right: [f32; 3]) -> [f32; 3] {
    [left[0] + right[0], left[1] + right[1], left[2] + right[2]]
}

pub(super) fn cross(left: [f32; 3], right: [f32; 3]) -> [f32; 3] {
    [
        left[1] * right[2] - left[2] * right[1],
        left[2] * right[0] - left[0] * right[2],
        left[0] * right[1] - left[1] * right[0],
    ]
}

pub(super) fn dot(left: [f32; 3], right: [f32; 3]) -> f32 {
    left[0] * right[0] + left[1] * right[1] + left[2] * right[2]
}

pub(super) fn normalize(vector: [f32; 3]) -> GalResult<[f32; 3]> {
    let length_squared = dot(vector, vector);
    if !length_squared.is_finite() || length_squared <= f32::EPSILON {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI mesh normal must have a finite non-zero length",
        ));
    }
    let inverse_length = length_squared.sqrt().recip();
    Ok([
        vector[0] * inverse_length,
        vector[1] * inverse_length,
        vector[2] * inverse_length,
    ])
}

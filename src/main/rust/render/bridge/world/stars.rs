//! The vanilla star field. Like Frozen's `SkyRenderer`, which builds its star
//! buffer once and draws it under the sky rotation, Java publishes each
//! star's local corners once and every frame carries only the sky rotation
//! (`FfiWorldBackgroundRequest::sky_star_transform`). Decoding expands the
//! stars into compact material quads where Java used to enqueue them.

use std::sync::{Arc, OnceLock, RwLock};

use super::*;
use crate::render::scene::material::{WORLD_MATERIAL_ID_CELESTIAL, WORLD_MATERIAL_ID_SKY_STARS};
use crate::render::scene::textures::WORLD_MATERIAL_TEXTURE_GENERATED_WHITE;

/// One star's four local corners, before the sky rotation.
type Star = [[f32; 3]; 4];

/// `SkyRenderer.buildStars` draws at most 1500 stars.
const MAX_STARS: usize = 1500;
/// `LightTexture.FULL_BRIGHT`.
const FULL_BRIGHT: u32 = 15_728_880;

fn field() -> &'static RwLock<Option<Arc<[Star]>>> {
    static FIELD: OnceLock<RwLock<Option<Arc<[Star]>>>> = OnceLock::new();
    FIELD.get_or_init(Default::default)
}

/// Publishes (or replaces) the star field: `count` stars of four corners of
/// three floats. Returns 0, or -2 for an invalid field.
///
/// # Safety
/// `corners` readable for `count * 12` floats.
#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_world_star_field_publish(corners: *const f32, count: u32) -> i32 {
    let count = count as usize;
    if corners.is_null() || count == 0 || count > MAX_STARS {
        return -2;
    }
    let values = unsafe { std::slice::from_raw_parts(corners, count * 12) };
    if !values.iter().all(|value| value.is_finite()) {
        return -2;
    }
    let stars: Arc<[Star]> = values
        .chunks_exact(12)
        .map(|star| std::array::from_fn(|corner| [star[corner * 3], star[corner * 3 + 1], star[corner * 3 + 2]]))
        .collect();
    match field().write() {
        Ok(mut field) => {
            *field = Some(stars);
            0
        }
        Err(_) => -2,
    }
}

/// JOML `Matrix4f.transformPosition` (column-major, no FMA: each product and
/// sum rounds, in JOML's association order).
fn transform_position(m: &[f32; 16], [x, y, z]: [f32; 3]) -> [f32; 3] {
    [
        m[0] * x + (m[4] * y + (m[8] * z + m[12])),
        m[1] * x + (m[5] * y + (m[9] * z + m[13])),
        m[2] * x + (m[6] * y + (m[10] * z + m[14])),
    ]
}

/// `ARGB.colorFromFloat(b, b, b, b)` for the clamped star brightness.
fn star_color(brightness: f32) -> u32 {
    let channel = ((brightness.clamp(0.0, 1.0) * 255.0).floor() as i32 & 0xff) as u32;
    channel << 24 | channel << 16 | channel << 8 | channel
}

/// The star quads `RustGalWorldPrimitiveRenderer.enqueueVanillaStarsLocked`
/// produced: translucent white quads under the sky rotation.
fn star_quads(stars: &[Star], transform: &[f32; 16], brightness: f32, viewport: [u32; 2]) -> Vec<WorldMaterialQuadRequest> {
    let color = star_color(brightness);
    stars
        .iter()
        .map(|corners| WorldMaterialQuadRequest {
            stratum: WORLD_STRATUM_OPAQUE_TEXTURED_GEOMETRY,
            material_id: WORLD_MATERIAL_ID_SKY_STARS,
            texture_id: WORLD_MATERIAL_TEXTURE_GENERATED_WHITE,
            material_mode: WORLD_MATERIAL_MODE_TRANSLUCENT,
            depth_policy: WORLD_DEPTH_POLICY_TEST_NO_WRITE,
            cull_policy: WORLD_CULL_NONE,
            topology: WORLD_TOPOLOGY_TRIANGLES,
            winding: WORLD_WINDING_CCW,
            color_argb: color,
            vertices: corners.map(|corner| transform_position(transform, corner)),
            uvs: [[0.0, 0.0], [1.0, 0.0], [1.0, 1.0], [0.0, 1.0]],
            viewport_width: viewport[0],
            viewport_height: viewport[1],
            source_program: WORLD_MATERIAL_SOURCE_TEXTURED,
            source_uv_space: WORLD_MATERIAL_SOURCE_UV_LOCAL_TEXTURE,
            source_color_argb: color,
            packed_light: FULL_BRIGHT,
            vertex_color_argb: [color; 4],
            vertex_packed_light: [FULL_BRIGHT; 4],
            block_entity_id: -1,
        })
        .collect()
}

/// Inserts the frame's stars after its sun and moon, where Java enqueued
/// them, when the background asks for the published field.
pub(super) fn expand_stars(
    request: &FfiWorldBackgroundRequest,
    background: &WorldBackgroundRequest,
    material_quads: &mut Vec<WorldMaterialQuadRequest>,
    max_material_quads: usize,
) -> GalResult<()> {
    if !bool_flag(request.sky_native_stars, "world sky native stars")? {
        return Ok(());
    }
    if !background.enabled || !background.sky.visible || !(background.sky.star_brightness > 0.0) {
        return Err(GalError::invalid_argument("native stars require a visible starry sky"));
    }
    let transform = request.sky_star_transform;
    if !transform.iter().all(|value| value.is_finite()) {
        return Err(GalError::invalid_argument("native star transform must be finite"));
    }
    let stars = field()
        .read()
        .map_err(|_| GalError::invalid_argument("star field poisoned"))?
        .clone()
        .ok_or_else(|| GalError::invalid_argument("native stars requested before the star field was published"))?;
    if material_quads.len() + stars.len() > max_material_quads {
        return Err(GalError::invalid_argument("stars exceed the frame's material quad bound"));
    }
    let after_moon = material_quads
        .iter()
        .rposition(|quad| quad.material_id == WORLD_MATERIAL_ID_CELESTIAL)
        .ok_or_else(|| GalError::invalid_argument("native stars require the frame's sun and moon"))?
        + 1;
    let quads = star_quads(&stars, &transform, background.sky.star_brightness, [background.viewport_width, background.viewport_height]);
    material_quads.splice(after_moon..after_moon, quads);
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn transforms_like_joml_and_colors_like_argb() {
        // rotateY(-pi/2) of identity, as JOML stores it column-major.
        let m = [0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 0.0, 0.0, -1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0];
        assert_eq!([-3.0, 2.0, 1.0], transform_position(&m, [1.0, 2.0, 3.0]));
        assert_eq!(0x7f7f_7f7f, star_color(0.5));
        assert_eq!(0xffff_ffff, star_color(2.0));
        assert_eq!(0, star_color(-1.0));
    }

    #[test]
    fn quads_carry_javas_star_record() {
        let star: Star = [[0.1, 99.0, 0.2], [0.3, 99.0, 0.4], [0.5, 99.0, 0.6], [0.7, 99.0, 0.8]];
        let identity = [1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0];
        let quads = star_quads(&[star], &identity, 0.25, [1280, 720]);
        assert_eq!(1, quads.len());
        let quad = &quads[0];
        assert_eq!(star, quad.vertices);
        assert_eq!(WORLD_MATERIAL_ID_SKY_STARS, quad.material_id);
        assert_eq!(0x3f3f_3f3f, quad.color_argb);
        assert_eq!([1280, 720], [quad.viewport_width, quad.viewport_height]);
        assert_eq!(-1, quad.block_entity_id);
    }

    fn celestial(id: u32) -> WorldMaterialQuadRequest {
        let mut quad = star_quads(&[[[0.0; 3]; 4]], &[0.0; 16], 1.0, [1, 1]).remove(0);
        quad.material_id = id;
        quad
    }

    #[test]
    fn expands_after_the_moon_only_when_asked() {
        let corners = [0.5_f32; 12];
        assert_eq!(0, unsafe { mattmc_vulkanic_world_star_field_publish(corners.as_ptr(), 1) });
        // SAFETY: a plain repr(C) record of integers and floats.
        let mut request: FfiWorldBackgroundRequest = unsafe { std::mem::zeroed() };
        let mut background = WorldBackgroundRequest { enabled: true, viewport_width: 1280, viewport_height: 720, ..Default::default() };
        background.sky.visible = true;
        background.sky.star_brightness = 1.0;
        let mut quads = vec![celestial(WORLD_MATERIAL_ID_CELESTIAL), celestial(WORLD_MATERIAL_ID_CELESTIAL), celestial(1)];
        expand_stars(&request, &background, &mut quads, 16).unwrap();
        assert_eq!(3, quads.len(), "no flag, no stars");
        request.sky_native_stars = 1;
        request.sky_star_transform[0] = 2.0;
        request.sky_star_transform[5] = 1.0;
        request.sky_star_transform[10] = 1.0;
        request.sky_star_transform[15] = 1.0;
        expand_stars(&request, &background, &mut quads, 16).unwrap();
        assert_eq!(4, quads.len());
        assert_eq!(WORLD_MATERIAL_ID_SKY_STARS, quads[2].material_id);
        assert_eq!([1.0, 0.5, 0.5], quads[2].vertices[0]);
        assert_eq!(1, quads[3].material_id);
        assert!(expand_stars(&request, &background, &mut quads.clone(), 4).is_err(), "bounded");
        background.sky.star_brightness = 0.0;
        assert!(expand_stars(&request, &background, &mut quads, 16).is_err(), "flag without stars");
    }
}

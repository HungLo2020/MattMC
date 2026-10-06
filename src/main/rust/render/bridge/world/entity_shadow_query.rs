//! Shadow-pass entity admission for Java's shadow-only extraction.
//!
//! The query is a standalone handle: it shares only the active pack's shadow
//! policy with its context, so answering never joins a pipelined frame. It
//! applies the admission the frame plan applies later, so Java can skip
//! extracting entities the shadow pass would reject.

use crate::render::bridge::*;
use crate::render::shaderpack::properties::shadow::ShadowCasterFrameDistances;
use crate::render::worldrender::frame::entity_culling::{WorldEntityCullingInputs, ENTITY_CULL_LEASH_HOLDER};
use crate::render::worldrender::{EntityShadowFrame, EntityShadowQuery};

const DECIDED: i32 = 0;
const UNDECIDED: i32 = 1;
const ERR_NULL_POINTER: i32 = -1;

/// Creates a query sharing `context_id`'s shadow policy; null if unknown.
#[no_mangle]
pub extern "C" fn mattmc_vulkanic_gal_entity_shadow_query_create(context_id: u64) -> *mut EntityShadowQuery {
    with_registry(|registry| {
        registry.contexts.get(&context_id).map_or(std::ptr::null_mut(), |context| {
            Box::into_raw(Box::new(context.world_primitive_frontend.entity_shadow_query()))
        })
    })
}

/// # Safety
/// `query` comes from `mattmc_vulkanic_gal_entity_shadow_query_create` and is
/// not used afterwards.
#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_entity_shadow_query_destroy(query: *mut EntityShadowQuery) {
    if !query.is_null() {
        drop(Box::from_raw(query));
    }
}

/// Writes 1 to `out[i]` for each candidate the shadow pass admits and returns
/// `DECIDED`, or returns `UNDECIDED` when the caller must keep every
/// candidate. Inputs are the frame's copied shader-environment values and
/// matrices; candidates are culling facts (`bounds`/`holder_bounds` hold six
/// doubles each; holder bounds are read only with the leash-holder flag).
///
/// # Safety
/// `projection`/`view` address 16 floats, `camera` 3 doubles, `flags` and
/// `out` `count` elements, `bounds` and `holder_bounds` `6 * count` doubles.
#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_entity_shadow_query_select(
    query: *const EntityShadowQuery,
    sky_type: u32,
    time_of_day: f32,
    projection: *const f32,
    view: *const f32,
    far_plane: f32,
    configured_shadow_distance_chunks: i32,
    camera: *const f64,
    count: u32,
    flags: *const u32,
    bounds: *const f64,
    holder_bounds: *const f64,
    out: *mut u8,
) -> i32 {
    let Some(query) = query.as_ref() else {
        return ERR_NULL_POINTER;
    };
    if projection.is_null() || view.is_null() || camera.is_null() {
        return ERR_NULL_POINTER;
    }
    if count == 0 {
        return DECIDED;
    }
    if flags.is_null() || bounds.is_null() || holder_bounds.is_null() || out.is_null() {
        return ERR_NULL_POINTER;
    }
    let count = count as usize;
    let camera: [f64; 3] = std::ptr::read_unaligned(camera.cast());
    let frame = EntityShadowFrame {
        time_of_day,
        projection: std::ptr::read_unaligned(projection.cast()),
        view: std::ptr::read_unaligned(view.cast()),
        distances: ShadowCasterFrameDistances {
            render_distance_blocks: far_plane,
            configured_shadow_distance_chunks,
        },
    };
    let flags = std::slice::from_raw_parts(flags, count);
    let bounds = std::slice::from_raw_parts(bounds, count * 6);
    let holder_bounds = std::slice::from_raw_parts(holder_bounds, count * 6);
    let out = std::slice::from_raw_parts_mut(out, count);
    let six = |values: &[f64], index: usize| -> [f64; 6] { std::array::from_fn(|i| values[index * 6 + i]) };
    let candidates = (0..count).map(|index| WorldEntityCullingInputs {
        flags: flags[index],
        bounds: six(bounds, index),
        leash_holder_bounds: (flags[index] & ENTITY_CULL_LEASH_HOLDER != 0).then(|| six(holder_bounds, index)),
        camera,
    });
    match query.select(sky_type, frame, candidates, out) {
        Some(()) => DECIDED,
        None => UNDECIDED,
    }
}

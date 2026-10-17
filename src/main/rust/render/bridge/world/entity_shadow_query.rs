//! Shadow-pass entity admission for Java's shadow-only extraction.
//!
//! The query is a standalone handle: it shares only the active pack's shadow
//! policy with its context, so answering never joins a pipelined frame. It
//! applies the admission the frame plan applies later, so Java can skip
//! extracting entities the shadow pass would reject.

use crate::render::bridge::*;
use crate::render::shaderpack::properties::shadow::ShadowCasterFrameDistances;
mod candidates;
use crate::render::worldrender::{EntityShadowFrame, EntityShadowQuery};
use candidates::Candidates;

const DECIDED: i32 = 0;
const UNDECIDED: i32 = 1;
const ERR_NULL_POINTER: i32 = -1;

/// Creates a query sharing `context_id`'s shadow policy; null if unknown.
#[no_mangle]
pub extern "C" fn mattmc_vulkanic_gal_entity_shadow_query_create(
    context_id: u64,
) -> *mut EntityShadowQuery {
    with_registry(|registry| {
        registry
            .contexts
            .get(&context_id)
            .map_or(std::ptr::null_mut(), |context| {
                Box::into_raw(Box::new(
                    context.world_primitive_frontend.entity_shadow_query(),
                ))
            })
    })
}

/// # Safety
/// `query` comes from `mattmc_vulkanic_gal_entity_shadow_query_create` and is
/// not used afterwards.
#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_entity_shadow_query_destroy(
    query: *mut EntityShadowQuery,
) {
    if !query.is_null() {
        drop(Box::from_raw(query));
    }
}

/// Writes 1 to the owned admission slot for each candidate the shadow pass admits and returns
/// `DECIDED`, or returns `UNDECIDED` when the caller must keep every
/// candidate. Inputs are the frame's copied shader-environment values and
/// matrices; candidate storage is owned by Rust and borrowed by the producer.
///
/// # Safety
/// `projection`/`view` address 16 floats, `camera` 3 doubles. `storage` is
/// exclusively borrowed and its first `count` slots were initialized by the
/// producer. No borrowed view is accessed concurrently with this call.
#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_entity_shadow_query_select_candidates(
    query: *const EntityShadowQuery,
    sky_type: u32,
    time_of_day: f32,
    projection: *const f32,
    view: *const f32,
    far_plane: f32,
    configured_shadow_distance_chunks: i32,
    camera: *const f64,
    count: u32,
    storage: *mut Candidates,
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
    let Some(storage) = storage.as_mut() else {
        return ERR_NULL_POINTER;
    };
    let count = count as usize;
    if count > storage.slots.len() || count > storage.admitted.len() {
        return -2;
    }
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
    let candidates = storage.slots[..count]
        .iter()
        .map(|slot| slot.inputs(camera));
    let out = &mut storage.admitted[..count];
    match query.select(sky_type, frame, candidates, out) {
        Some(()) => DECIDED,
        None => UNDECIDED,
    }
}

#[cfg(test)]
mod tests;

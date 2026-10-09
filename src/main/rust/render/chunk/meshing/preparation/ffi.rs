use super::*;
use crate::content::block::{installed, StateFlags, StateId};
use crate::world::level::lighting::layers::ffi::Projection;

fn state_facts(registry: &crate::content::block::BlockRegistry, id: i32) -> Option<Facts> {
    if id < 0 || id as usize >= registry.state_count() { return None; }
    let state = StateId(id as u16);
    let flags = registry.flags(state);
    if flags.contains(StateFlags::CUSTOM) { return None; }
    Some(Facts { emission: i32::from(registry.emission(state)),
        light_block: i32::from(registry.light_block(state)),
        full_opaque: flags.contains(StateFlags::SOLID_RENDER) })
}

/// # Safety
/// IDs address CELLS live immutable aligned entries.
#[no_mangle]
pub unsafe extern "C" fn mattmc_terrain_light_admit(ids: *const i32, count: u32) -> i32 {
    if ids.is_null() || count != CELLS as u32 || ids as usize % 4 != 0 { return -1; }
    let Some(registry) = installed() else { return -3; };
    if std::slice::from_raw_parts(ids, CELLS).iter().any(|id| state_facts(registry, *id).is_none()) { -2 } else { 0 }
}

/// # Safety
/// Each optional projection is a live production CPU lease, retained through
/// this call. IDs/contexts cover CELLS immutable entries; output covers CELLS
/// writable i32s. All spans are aligned, valid and disjoint from writable output.
#[no_mangle]
pub unsafe extern "C" fn mattmc_terrain_light_prepare(projections: *const *const Projection,
    view_count: u32, ids: *const i32, contexts: *const Context, count: u32,
    output: *mut i32) -> i32 {
    if projections.is_null() || view_count != VIEWS as u32 || count != CELLS as u32
        || ids.is_null() || contexts.is_null() || output.is_null()
        || projections as usize % std::mem::align_of::<*const Projection>() != 0
        || ids as usize % 4 != 0 || contexts as usize % 4 != 0 || output as usize % 4 != 0 {
        return -1;
    }
    let Some(registry) = installed() else { return -3; };
    let pointers = &*projections.cast::<[*const Projection; VIEWS]>();
    if pointers.iter().any(|p| !p.is_null() && *p as usize % std::mem::align_of::<Projection>() != 0) { return -1; }
    let views = pointers.each_ref().map(|p| p.as_ref().map(Projection::borrowed_view));
    let ids = std::slice::from_raw_parts(ids, CELLS);
    let contexts = std::slice::from_raw_parts(contexts, CELLS);
    let output = std::slice::from_raw_parts_mut(output, CELLS);
    prepare(views, ids, contexts, output, |id| state_facts(registry, id)).map_or_else(|error| error, |()| 0)
}

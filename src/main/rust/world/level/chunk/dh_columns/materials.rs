//! Intrinsic DH policy derived once from semantic registered material identities.
use crate::content::block::{StateFlags, StateId};
#[repr(C)]
#[derive(Clone, Copy)]
pub(crate) struct Fact {
    pub(crate) opacity: u8,
    pub(crate) air: bool,
    pub(crate) liquid: bool,
}
pub(crate) struct Materials(pub(crate) Box<[Fact]>);

fn build(states: impl ExactSizeIterator<Item = u32>) -> Option<Materials> {
    let catalog = crate::content::block::collision::installed()?;
    let registry = crate::content::block::installed()?;
    let mut facts = Vec::new();
    if states.len() == 0 { return None; }
    facts.try_reserve_exact(states.len()).ok()?;
    for state in states {
        let dh = catalog.dh(state)?;
        if state as usize >= registry.state_count() { return None; }
        let flags = registry.flags(StateId(state as u16));
        facts.push(Fact { opacity: dh.opacity, air: flags.contains(StateFlags::AIR), liquid: flags.contains(StateFlags::HAS_FLUID) });
    }
    Some(Materials(facts.into_boxed_slice()))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_materials_create(states: *const u32, count: i32) -> *mut Materials {
    if count <= 0 || states.is_null() || states as usize % 4 != 0 { return std::ptr::null_mut(); }
    build(std::slice::from_raw_parts(states, count as usize).iter().copied())
        .map_or(std::ptr::null_mut(), |m| Box::into_raw(Box::new(m)))
}
/// Consume existing authoritative identity entries. Outputs own independent facts;
/// the map is borrowed/pinned only for this call, and no point or wrapper array is exported.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_materials_from_map(map: *const super::id_map::Map) -> *mut Materials {
    let Some(map) = map.as_ref() else { return std::ptr::null_mut(); };
    build(map.state_ids()).map_or(std::ptr::null_mut(), |m| Box::into_raw(Box::new(m)))
}
/// Facts read by Java from each mapping entry's wrapper (`getOpacity`, `isAir`,
/// `isLiquid`), three bytes per ID. Used when the mapping holds no registered
/// block states (before the block catalog is installed).
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_materials_from_facts(facts: *const u8, count: i32) -> *mut Materials {
    if count <= 0 || facts.is_null() { return std::ptr::null_mut(); }
    let raw = std::slice::from_raw_parts(facts, count as usize * 3);
    let facts: Box<[Fact]> = raw.chunks_exact(3)
        .map(|f| Fact { opacity: f[0], air: f[1] != 0, liquid: f[2] != 0 }).collect();
    Box::into_raw(Box::new(Materials(facts)))
}
/// Private CPU view for consumer/lifetime verification: pointer and count of three-byte facts.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_materials_view(owner: *const Materials, output: *mut usize) -> i32 {
    if owner.is_null() || output.is_null() || output as usize % std::mem::align_of::<usize>() != 0 { return 0; }
    let facts = &(*owner).0;
    std::slice::from_raw_parts_mut(output, 2).copy_from_slice(&[facts.as_ptr() as usize, facts.len()]); 1
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_materials_release(owner: *mut Materials) {
    if !owner.is_null() { drop(Box::from_raw(owner)); }
}

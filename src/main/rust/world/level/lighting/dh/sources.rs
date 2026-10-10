//! First-enumeration emitter cache over authoritative native sections.
//! Other DH consumers can borrow this owner before the lighting pass runs.
use super::*;
pub(crate) struct Sources {
    pub(crate) min_y: i32,
    pub(crate) positions: Arc<[u32]>,
}
pub(crate) fn build(
    owners: &[&live::Owner],
    min_y: i32,
    emission: impl Fn(u32) -> Option<u8>,
) -> Option<Box<Sources>> {
    if !(1..=256).contains(&owners.len()) || min_y % 16 != 0 || min_y.unsigned_abs() > 1_000_000 {
        return None;
    }
    let mut positions = Vec::new();
    for (section, owner) in owners.iter().enumerate() {
        owner.with_state_reader(|reader| {
            if !reader.all_states(|id| emission(id).is_some_and(|e| e <= 15)) {
                return None;
            }
            for index in 0..4096 {
                if emission(reader.get(index))? != 0 {
                    positions.push((section * 4096 + index) as u32);
                }
            }
            Some(())
        })?;
    }
    Some(Box::new(Sources {
        min_y,
        positions: positions.into(),
    }))
}
/// Owner pointers are aligned, valid and pinned until return; no Java callbacks.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_sources_build(
    owners: *const usize,
    count: i32,
    min_y: i32,
) -> *mut Sources {
    if owners.is_null()
        || owners as usize % std::mem::align_of::<usize>() != 0
        || !(1..=256).contains(&count)
    {
        return std::ptr::null_mut();
    }
    let Some(registry) = crate::content::block::installed() else {
        return std::ptr::null_mut();
    };
    let pointers = std::slice::from_raw_parts(owners, count as usize);
    let mut sections = Vec::with_capacity(count as usize);
    for &pointer in pointers {
        if pointer == 0 || pointer % std::mem::align_of::<live::Owner>() != 0 {
            return std::ptr::null_mut();
        }
        sections.push(&*(pointer as *const live::Owner));
    }
    let emission = |id: u32| {
        if id as usize >= registry.state_count() {
            return None;
        }
        let state = crate::content::block::StateId(id as u16);
        (!registry
            .flags(state)
            .contains(crate::content::block::StateFlags::CUSTOM))
        .then(|| registry.emission(state))
    };
    build(&sections, min_y, emission).map_or(std::ptr::null_mut(), Box::into_raw)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_light_field_sources(field: *const Field) -> *mut Sources {
    if field.is_null() || field as usize % std::mem::align_of::<Field>() != 0 {
        return std::ptr::null_mut();
    }
    let f = &*field;
    Box::into_raw(Box::new(Sources {
        min_y: f.min_y,
        positions: f.sources.clone(),
    }))
}
/// Three CPU metadata values: immutable u32 index data, count, minimum Y.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_sources_view(owner: *const Sources, out: *mut usize) -> i32 {
    if owner.is_null()
        || owner as usize % std::mem::align_of::<Sources>() != 0
        || out.is_null()
        || out as usize % std::mem::align_of::<usize>() != 0
    {
        return -1;
    }
    let s = &*owner;
    std::slice::from_raw_parts_mut(out, 3).copy_from_slice(&[
        s.positions.as_ptr() as usize,
        s.positions.len(),
        s.min_y as usize,
    ]);
    1
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_sources_release(owner: *mut Sources) {
    if !owner.is_null() {
        drop(Box::from_raw(owner));
    }
}

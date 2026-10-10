//! Rust-owned DH height fields, scanning existing live sections directly.
//! This is world CPU policy; it borrows no renderer or external GPU state.
use super::{counters, live};
use crate::content::block::collision::Catalog;

/// Read-only compatibility CPU view. Owner lifetime is independent of inputs.
#[repr(C)]
#[derive(Debug, PartialEq)]
pub(crate) struct Heightmaps {
    pub(crate) min: i32,
    pub(crate) max: i32,
    pub(crate) solid: [i32; 256],
    pub(crate) blocking: [i32; 256],
}
pub(crate) fn build(
    sections: &[(&live::Owner, &counters::Owner)],
    min_y: i32,
    catalog: &Catalog,
) -> Option<Box<Heightmaps>> {
    if sections.is_empty()
        || sections.len() > 256
        || min_y % 16 != 0
        || min_y.unsigned_abs() > 1_000_000
    {
        return None;
    }
    let empty: Vec<_> = sections
        .iter()
        .map(|(_, counts)| counts.values()[0] == 0)
        .collect();
    let bottom = empty.iter().position(|&e| !e).unwrap_or(0);
    let top = empty.iter().rposition(|&e| !e).unwrap_or(0);
    let min = min_y + bottom as i32 * 16;
    let max = min_y + (top as i32 + 1) * 16;
    let mut result = Box::new(Heightmaps {
        min,
        max,
        solid: [min; 256],
        blocking: [min; 256],
    });
    let mut remaining = 256;
    for section in (bottom..=top).rev() {
        if empty[section] {
            continue;
        }
        sections[section].0.with_state_reader(|reader| {
            // Decline any contextual state in an accessed section. No partial
            // result escapes; the caller can still execute original callbacks.
            if !reader.all_states(|id| catalog.dh(id).is_some()) {
                return None;
            }
            if remaining == 0 {
                return Some(());
            }
            for y in (0..16).rev() {
                let height = min_y + section as i32 * 16 + y;
                // Frozen deliberately does not test its minimum row.
                if height <= min {
                    continue;
                }
                for x in 0..16 {
                    for z in 0..16 {
                        let column = x * 16 + z;
                        if result.solid[column] != min && result.blocking[column] != min {
                            continue;
                        }
                        let facts = catalog.dh(reader.get(y as usize * 256 + z * 16 + x))?;
                        if result.solid[column] == min && facts.solid {
                            result.solid[column] = height;
                        }
                        if result.blocking[column] == min && facts.opacity != 0 {
                            result.blocking[column] = height;
                        }
                        if result.solid[column] != min && result.blocking[column] != min {
                            remaining -= 1;
                        }
                    }
                }
                if remaining == 0 {
                    break;
                }
            }
            Some(())
        })?;
    }
    Some(result)
}
/// Borrow [live owner, counter owner] pointer pairs. Each owner is pinned for
/// the call; owners/counters retain their normal mutation/exclusion rules.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_heightmaps_create(
    pairs: *const usize,
    count: i32,
    min_y: i32,
) -> *mut Heightmaps {
    if pairs.is_null()
        || pairs as usize % std::mem::align_of::<usize>() != 0
        || !(1..=256).contains(&count)
    {
        return std::ptr::null_mut();
    }
    let Some(catalog) = crate::content::block::collision::installed() else {
        return std::ptr::null_mut();
    };
    let pairs = std::slice::from_raw_parts(pairs, count as usize * 2);
    let mut sections = Vec::with_capacity(count as usize);
    for pair in pairs.chunks_exact(2) {
        if pair[0] == 0
            || pair[1] == 0
            || pair[0] % std::mem::align_of::<live::Owner>() != 0
            || pair[1] % std::mem::align_of::<counters::Owner>() != 0
        {
            return std::ptr::null_mut();
        }
        sections.push((
            &*(pair[0] as *const live::Owner),
            &*(pair[1] as *const counters::Owner),
        ));
    }
    build(&sections, min_y, catalog).map_or(std::ptr::null_mut(), Box::into_raw)
}
/// Release exactly once after CPU views and calls finish.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_heightmaps_release(owner: *mut Heightmaps) {
    if !owner.is_null() {
        drop(Box::from_raw(owner));
    }
}
#[cfg(test)]
pub(crate) mod fixture;
#[cfg(test)]
mod tests;

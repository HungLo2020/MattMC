//! Mutable DH column storage. Java holds CPU leases, never a mirrored point array.
//! Callers retain the original DH exclusion rules; views expire on mutation.
use std::slice;

pub(crate) struct Column(pub(crate) Vec<u64>);

unsafe fn input<'a, T>(data: *const T, count: i32) -> Option<&'a [T]> {
    if count < 0 || (count != 0 && (data.is_null() || data as usize % std::mem::align_of::<T>() != 0)) {
        return None;
    }
    Some(if count == 0 { &[] } else { slice::from_raw_parts(data, count as usize) })
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_column_create(data: *const u64, count: i32) -> *mut Column {
    let Some(data) = input(data, count) else { return std::ptr::null_mut(); };
    let mut points = Vec::new();
    if points.try_reserve_exact(data.len()).is_err() { return std::ptr::null_mut(); }
    points.extend_from_slice(data);
    Box::into_raw(Box::new(Column(points)))
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_column_release(owner: *mut Column) {
    if !owner.is_null() { drop(Box::from_raw(owner)); }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_column_view(owner: *const Column, out: *mut usize) -> i32 {
    if owner.is_null() || out.is_null() || out as usize % std::mem::align_of::<usize>() != 0 { return 0; }
    let points = &(*owner).0;
    slice::from_raw_parts_mut(out, 2).copy_from_slice(&[points.as_ptr() as usize, points.len()]);
    1
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_column_set(owner: *mut Column, index: i32, value: u64) -> i32 {
    let Some(owner) = owner.as_mut() else { return 0; };
    let Some(point) = owner.0.get_mut(index as usize) else { return 0; };
    *point = value; 1
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_column_add(owner: *mut Column, index: i32, value: u64) -> i32 {
    let Some(owner) = owner.as_mut() else { return 0; };
    if index < 0 || index as usize > owner.0.len() || owner.0.len() == i32::MAX as usize { return 0; }
    if owner.0.try_reserve(1).is_err() { return -1; }
    owner.0.insert(index as usize, value); 1
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_column_remove(owner: *mut Column, from: i32, to: i32) -> i32 {
    let Some(owner) = owner.as_mut() else { return 0; };
    if from < 0 || to < from || to as usize > owner.0.len() { return 0; }
    owner.0.drain(from as usize..to as usize); 1
}

/// Self-insertion reads the original range before growth/mutation, without an
/// aliasing mutable reference to the borrowed input allocation.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_column_insert(owner: *mut Column, index: i32, data: *const u64, count: i32) -> i32 {
    let Some(previous) = owner.as_ref() else { return 0; };
    if index < 0 || index as usize > previous.0.len() || count < 0
        || previous.0.len().checked_add(count as usize).is_none_or(|n| n > i32::MAX as usize) { return 0; }
    let Some(values) = input(data, count) else { return 0; };
    let start = previous.0.as_ptr() as usize;
    let end = start + previous.0.capacity() * 8;
    let address = data as usize;
    let alias = count != 0 && address < end && address.saturating_add(count as usize * 8) > start;
    let copy = alias.then(|| values.to_vec());
    let values = copy.as_deref().unwrap_or(values);
    let owner = &mut *owner;
    if owner.0.try_reserve(values.len()).is_err() { return -1; }
    owner.0.splice(index as usize..index as usize, values.iter().copied()); 1
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_column_hash(data: *const u64, count: i32) -> i32 {
    input(data, count).map_or(0, |points| points.iter().fold(1i32, |h, &p|
        h.wrapping_mul(31).wrapping_add((p ^ (p >> 32)) as i32)))
}

/// Match the old loop's successful mutation prefix if a later ID is invalid.
/// Return the first failing point index, or -1 on success.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_column_remap(owner: *mut Column, ids: *const i32, count: i32) -> i32 {
    let Some(ids) = input(ids, count) else { return 0; };
    let Some(owner) = owner.as_mut() else { return 0; };
    for (i, point) in owner.0.iter_mut().enumerate() {
        let id = *point as u32 as i32;
        let Some(&mapped) = ids.get(id as usize) else { return i as i32; };
        *point = (*point & !0xffff_ffff) | (mapped as i64 as u64);
    }
    -1
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn native_storage_handles_aliasing_growth_and_failed_remap_prefix() {
        unsafe {
            let owner = mattmc_dh_column_create([0u64, 1, 2].as_ptr(), 3);
            assert!(!owner.is_null());
            let data = (*owner).0.as_ptr();
            assert_eq!(mattmc_dh_column_insert(owner, 1, data, 3), 1);
            assert_eq!((*owner).0, [0, 0, 1, 2, 1, 2]);
            assert_eq!(mattmc_dh_column_remove(owner, 1, 4), 1);
            assert_eq!((*owner).0, [0, 1, 2]);
            assert_eq!(mattmc_dh_column_remap(owner, [9, 8].as_ptr(), 2), 2);
            assert_eq!((*owner).0, [9, 8, 2]);
            assert_eq!(mattmc_dh_column_remove(owner, 3, 2), 0);
            assert_eq!(mattmc_dh_column_insert(owner, -1, std::ptr::null(), 0), 0);
            mattmc_dh_column_release(owner);
        }
    }
}

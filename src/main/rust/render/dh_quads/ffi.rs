use super::{Builder, Policy, Quad};
use std::{
    panic::{catch_unwind, AssertUnwindSafe},
    ptr,
};

fn policy(flags: u64) -> Policy {
    Policy {
        exact: flags & (1 << 49) != 0,
        overlap_errors: flags & (1 << 50) != 0,
        curvature: flags & (1 << 51) != 0,
    }
}

#[no_mangle]
pub extern "C" fn mattmc_dh_quads_create(transparency: i32) -> *mut Builder {
    Box::into_raw(Box::new(Builder::new(transparency != 0)))
}

/// Caller owns this build lease exclusively; no borrowed projections escape.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_quads_release(owner: *mut Builder) {
    if !owner.is_null() {
        drop(Box::from_raw(owner));
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_quads_add(
    owner: *mut Builder,
    geometry: u64,
    attributes: u64,
    color: u32,
    identity: i32,
    position: i64,
) -> i32 {
    catch_unwind(AssertUnwindSafe(|| {
        let Some(owner) = owner.as_mut() else {
            return 0;
        };
        let quad = Quad {
            x: geometry as i16,
            y: (geometry >> 16) as i16,
            z: (geometry >> 32) as i16,
            width: (geometry >> 48) as i16,
            height: attributes as i16,
            direction: (attributes >> 16) as u8,
            material: (attributes >> 24) as u8,
            sky: (attributes >> 32) as u8,
            block: (attributes >> 40) as u8,
            error: 0,
            variant: (attributes >> 56) as u8,
            color,
            identity,
            position,
        };
        i32::from(owner.add(quad, attributes & (1 << 48) != 0, policy(attributes)))
    }))
    .unwrap_or(0)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_quads_merge(owner: *mut Builder, flags: u64) -> i32 {
    catch_unwind(AssertUnwindSafe(|| {
        let Some(owner) = owner.as_mut() else {
            return 0;
        };
        owner.merge(policy(flags));
        1
    }))
    .unwrap_or(0)
}

/// Whole immutable CPU snapshot into caller-owned bytes, never native handles.
/// Null output queries count. Capacity is in rows; bytes may be unaligned.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_quads_copy(
    owner: *const Builder,
    layer: i32,
    direction: i32,
    output: *mut u8,
    capacity: i32,
) -> i32 {
    let Some(owner) = owner.as_ref() else {
        return -1;
    };
    if !(0..2).contains(&layer) || !(0..6).contains(&direction) || capacity < 0 {
        return -1;
    }
    let list = &owner.lists[layer as usize][direction as usize];
    let Ok(count) = i32::try_from(list.len()) else {
        return -1;
    };
    if output.is_null() {
        return count;
    }
    if capacity < count {
        return -1;
    }
    ptr::copy_nonoverlapping(
        list.as_ptr().cast::<u8>(),
        output,
        list.len() * std::mem::size_of::<Quad>(),
    );
    count
}

/// 0/1: layer count; 2..7: emitted semantic coverage; 8: premerge count.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_quads_count(owner: *const Builder, field: i32) -> i32 {
    let Some(owner) = owner.as_ref() else {
        return -1;
    };
    match field {
        0 | 1 => owner.lists[field as usize]
            .iter()
            .fold(0i32, |n, list| n.wrapping_add(list.len() as i32)),
        2..=7 => owner.coverage[field as usize - 2],
        8 => owner.premerges,
        _ => -1,
    }
}

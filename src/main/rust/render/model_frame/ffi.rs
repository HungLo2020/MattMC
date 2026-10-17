//! Private CPU producer ABI. Java serializes each owner and closes its scope
//! after frame completion (or abort), invalidating all borrowed views first.
use super::{instance::Input, Frame};

#[no_mangle]
pub extern "C" fn mattmc_model_frame_create() -> *mut Frame {
    Box::into_raw(Box::default())
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_model_frame_release(owner: *mut Frame) {
    if !owner.is_null() {
        drop(Box::from_raw(owner));
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_model_frame_reserve_poses(owner: *mut Frame, count: u32) -> i32 {
    (*owner)
        .reserve_poses(count as usize)
        .map_or(-1, |first| first as i32)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_model_frame_commit_poses(
    owner: *mut Frame,
    first: u32,
    count: u32,
) -> i32 {
    i32::from((*owner).commit_poses(first as usize, count as usize))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_model_frame_append(owner: *mut Frame, input: *const Input) -> i32 {
    if input.is_null() {
        return -1;
    }
    (*owner).append(&*input).map_or(-1, |index| index as i32)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_model_frame_variant(
    owner: *mut Frame,
    index: u32,
    kind: u32,
    a: i64,
    b: i64,
    speed: f64,
    strength: f32,
) -> i32 {
    (*owner)
        .variant(index as usize, kind, a, b, speed, strength)
        .map_or(-1, |index| index as i32)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_model_frame_rollback(
    owner: *mut Frame,
    instances: u32,
    poses: u32,
) -> i32 {
    i32::from((*owner).rollback(instances as usize, poses as usize))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_model_frame_seal(owner: *mut Frame) {
    (*owner).seal();
}

use super::Owner;
use std::ptr;

/// Inputs are bounded immutable CPU data. The result has one release; its two
/// poses never change. Java pins the owner through every native decode.
#[unsafe(no_mangle)]
pub(crate) unsafe extern "C" fn mattmc_item_transform_create(
    authored: *const f32,
    no_transform: u32,
) -> *mut Owner {
    if authored.is_null() || no_transform > 1 {
        return ptr::null_mut();
    }
    let a: [f32; 9] = unsafe { std::slice::from_raw_parts(authored, 9) }
        .try_into()
        .unwrap();
    Owner::new(a, no_transform != 0).map_or(ptr::null_mut(), |owner| Box::into_raw(Box::new(owner)))
}
#[unsafe(no_mangle)]
pub(crate) unsafe extern "C" fn mattmc_item_transform_release(owner: *mut Owner) {
    if !owner.is_null() {
        unsafe { drop(Box::from_raw(owner)) };
    }
}

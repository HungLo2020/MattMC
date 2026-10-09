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

/// Compatibility CPU projection only. Ordinary rendering resolves from its
/// pinned owner during request decoding and never calls back into Java.
#[unsafe(no_mangle)]
pub(crate) unsafe extern "C" fn mattmc_item_transform_resolve(
    owner: *const Owner,
    mode: u32,
    properties: u32,
    model: *const f32,
    normal: *const f32,
    trusted: u32,
    output: *mut super::Pose,
) -> u32 {
    if owner.is_null() || model.is_null() || normal.is_null() || output.is_null() || trusted > 1 {
        return 1;
    }
    let parent = super::world_pose::ResolvedPose {
        model: unsafe { std::slice::from_raw_parts(model, 16) }
            .try_into()
            .unwrap(),
        normal: unsafe { std::slice::from_raw_parts(normal, 9) }
            .try_into()
            .unwrap(),
        trusted: trusted != 0,
    };
    let Some(pose) = unsafe { &*owner }.resolve(mode, parent, properties) else {
        return 1;
    };
    unsafe { output.write(pose) };
    0
}

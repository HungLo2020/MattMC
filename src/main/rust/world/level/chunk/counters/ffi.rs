use super::Owner;
/// Returned CPU owner is eight-byte aligned. Caller adopts and releases it once.
#[no_mangle]
pub extern "C" fn mattmc_section_counters_create(packed: u64) -> *mut Owner {
    Box::into_raw(Box::new(Owner::new(packed)))
}
/// Release only after all calls and CPU reads have finished.
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_counters_release(owner: *mut Owner) {
    if !owner.is_null() {
        drop(Box::from_raw(owner));
    }
}
/// All owner arguments are live, aligned pointers from create; never retained.
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_counters_set(owner: *const Owner, a: i32, b: i32, c: i32) {
    if let Some(owner) = owner.as_ref() {
        owner.set([a, b, c]);
    }
}
/// Preserve compatibility callback prefixes: each successful short update is
/// published before a later callback can throw. Replace is used by network read.
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_counters_adjust(
    owner: *const Owner,
    lane: i32,
    value: i32,
    replace: i32,
) {
    if !(0..3).contains(&lane) {
        return;
    }
    if let Some(owner) = owner.as_ref() {
        owner.adjust(lane as usize, value, replace != 0);
    }
}

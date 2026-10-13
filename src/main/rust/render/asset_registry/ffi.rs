//! Private CPU ABI. The caller pins the owner and serializes all access.
//! Header/slot views are borrowed; refresh the slot view after a mutation.
use super::Registry;

#[no_mangle]
pub extern "C" fn mattmc_asset_registry_create() -> *mut Registry {
    Box::into_raw(Box::default())
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_asset_registry_release(owner: *mut Registry) {
    if !owner.is_null() {
        drop(Box::from_raw(owner));
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_asset_registry_find(owner: *const Registry, key: i64) -> i32 {
    (*owner).keys.get(&key).map_or(-1, |&slot| slot as i32)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_asset_registry_put(owner: *mut Registry, key: i64) -> i32 {
    (*owner).put(key) as i32
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_asset_registry_remove(owner: *mut Registry, key: i64) -> i32 {
    (*owner).remove(key).map_or(-1, |slot| slot as i32)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_asset_registry_clear(owner: *mut Registry) {
    (*owner).clear();
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_asset_registry_reset(owner: *mut Registry) {
    (*owner).reset();
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_asset_registry_added(
    owner: *mut Registry,
    epoch: u64,
    position: i32,
) -> i32 {
    let owner = &mut *owner;
    if owner.added_since(epoch, position) {
        owner.added.len() as i32
    } else {
        -1
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_asset_registry_added_data(owner: *const Registry) -> *const i64 {
    (*owner).added.as_ptr()
}

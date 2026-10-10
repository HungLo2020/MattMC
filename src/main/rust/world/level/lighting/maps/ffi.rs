//! World light-map CPU ABI. All inbound handles are typed, live Rust owners retained
//! by the caller for the call; no GPU resources or game-object pointers occur.
use super::super::layers::Layer;
use super::{
    registry::{Registry, Reservation},
    Entry, LightMap,
};
use std::sync::atomic::{AtomicI32, Ordering};
use std::sync::{Arc, RwLock};

#[repr(C)]
pub struct MapMetadata {
    lowest: AtomicI32,
    default: AtomicI32,
}
pub struct Owner {
    registry: Arc<Registry>,
    state: RwLock<LightMap>,
    metadata: MapMetadata,
}
#[repr(C)]
pub struct MapView {
    metadata: *const MapMetadata,
    _owner: Arc<Owner>,
}
#[repr(C)]
pub struct RegistryView {
    metadata: *const super::registry::Metadata,
    _registry: Arc<Registry>,
}

unsafe fn retain<T>(pointer: *const T) -> Arc<T> {
    Arc::increment_strong_count(pointer);
    Arc::from_raw(pointer)
}
fn owner(registry: Arc<Registry>, state: LightMap) -> Arc<Owner> {
    let metadata = MapMetadata {
        lowest: AtomicI32::new(state.lowest_y),
        default: AtomicI32::new(state.top_default),
    };
    Arc::new(Owner {
        registry,
        state: RwLock::new(state),
        metadata,
    })
}
#[no_mangle]
pub extern "C" fn mattmc_light_map_registry_create() -> *const Registry {
    Arc::into_raw(Registry::new())
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_map_registry_release(pointer: *const Registry) {
    if !pointer.is_null() {
        drop(Arc::from_raw(pointer));
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_map_registry_view(
    pointer: *const Registry,
) -> *mut RegistryView {
    if pointer.is_null() {
        return std::ptr::null_mut();
    }
    Box::into_raw(Box::new(RegistryView {
        metadata: &(*pointer).metadata,
        _registry: retain(pointer),
    }))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_map_registry_view_release(pointer: *mut RegistryView) {
    if !pointer.is_null() {
        drop(Box::from_raw(pointer));
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_map_reserve(
    pointer: *const Registry,
    slot: *mut u32,
) -> *mut Reservation {
    if pointer.is_null() || slot.is_null() {
        return std::ptr::null_mut();
    }
    let registry = retain(pointer);
    let Some(reservation) = registry.reserve() else {
        return std::ptr::null_mut();
    };
    *slot = reservation.slot;
    Box::into_raw(Box::new(reservation))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_map_reservation_release(pointer: *mut Reservation) {
    if !pointer.is_null() {
        drop(Box::from_raw(pointer));
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_map_retired(
    pointer: *const Registry,
    out: *mut u32,
    capacity: u32,
) -> i32 {
    if pointer.is_null() || out.is_null() || capacity == 0 || capacity > 64 {
        return -1;
    }
    (*pointer).take_retired_into(std::slice::from_raw_parts_mut(out, capacity as usize)) as i32
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_map_retired_ack(
    pointer: *const Registry,
    slots: *const u32,
    count: u32,
) -> i32 {
    if pointer.is_null() || slots.is_null() || count > 64 {
        return -1;
    }
    if (*pointer).acknowledge(std::slice::from_raw_parts(slots, count as usize)) {
        0
    } else {
        -1
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_map_create(registry: *const Registry) -> *const Owner {
    if registry.is_null() {
        return std::ptr::null();
    }
    Arc::into_raw(owner(retain(registry), LightMap::new()))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_map_copy(pointer: *const Owner) -> *const Owner {
    if pointer.is_null() {
        return std::ptr::null();
    }
    let parent = &*pointer;
    Arc::into_raw(owner(
        Arc::clone(&parent.registry),
        parent.state.read().unwrap().snapshot(),
    ))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_map_release(pointer: *const Owner) {
    if !pointer.is_null() {
        drop(Arc::from_raw(pointer));
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_map_view(pointer: *const Owner) -> *mut MapView {
    if pointer.is_null() {
        return std::ptr::null_mut();
    }
    Box::into_raw(Box::new(MapView {
        metadata: &(*pointer).metadata,
        _owner: retain(pointer),
    }))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_map_view_release(pointer: *mut MapView) {
    if !pointer.is_null() {
        drop(Box::from_raw(pointer));
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_map_get(pointer: *const Owner, key: i64, raw: u32) -> u32 {
    if pointer.is_null() {
        return 0;
    }
    let source = &*pointer;
    {
        let state = source.state.read().unwrap();
        if raw != 0 || !state.cache_enabled {
            return state.raw(key).map_or(0, |entry| entry.id);
        }
    }
    source
        .state
        .write()
        .unwrap()
        .get(key)
        .map_or(0, |entry| entry.id)
}
/// # Safety
/// Owner is live and caller-retained; light_on is immutable CPU column state.
/// Zero declines; a high-word marker plus the full low i32 represents any result.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_map_sample(
    pointer: *const Owner,
    block: i64,
    sky: u32,
    updating: u32,
    light_on: u32,
) -> i64 {
    let Some(owner) = pointer.as_ref() else {
        return 0;
    };
    let encode = |value: Option<i32>| {
        value.map_or(0, |value| ((1u64 << 32) | u64::from(value as u32)) as i64)
    };
    {
        let state = owner.state.read().unwrap();
        if !state.cache_enabled {
            return encode(state.sample_light_uncached(
                block,
                sky != 0,
                updating != 0,
                light_on != 0,
            ));
        }
    }
    encode(
        owner
            .state
            .write()
            .unwrap()
            .sample_light(block, sky != 0, updating != 0, light_on != 0),
    )
}

/// # Safety
/// Owner is live; the returned independent CPU lease is caller-released.
/// Missing/compatibility/escaped layers decline instead of borrowing stale bytes.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_map_raw_layer_view(
    pointer: *const Owner,
    key: i64,
) -> *mut super::super::layers::ffi::Projection {
    let Some(owner) = pointer.as_ref() else {
        return std::ptr::null_mut();
    };
    let Some(entry) = owner.state.read().unwrap().raw(key) else {
        return std::ptr::null_mut();
    };
    let Some(layer) = entry.layer.as_ref().filter(|layer| layer.is_valid()) else {
        return std::ptr::null_mut();
    };
    super::super::layers::ffi::projection(layer)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_light_map_has(pointer: *const Owner, key: i64) -> i32 {
    if pointer.is_null() {
        return 0;
    }
    i32::from((*pointer).state.read().unwrap().contains(key))
}
/// A successful non-null set consumes its reservation. A rejected call leaves
/// it caller-owned, so Java can clear its CPU slot and release the reservation.
/// The optional layer is a live Arc-owned native layer; no Java/GPU pointers.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_map_set(
    pointer: *const Owner,
    key: i64,
    reservation: *mut Reservation,
    layer: *const Layer,
) -> i32 {
    if pointer.is_null() {
        return -1;
    }
    let source = &*pointer;
    if reservation.is_null() {
        source.state.write().unwrap().set(key, None);
        return 0;
    }
    if !Arc::ptr_eq(&source.registry, &(*reservation).registry) {
        return -1;
    }
    let pin = Box::from_raw(reservation).install();
    let id = pin.slot;
    let layer = if layer.is_null() {
        None
    } else {
        Some(retain(layer))
    };
    source.state.write().unwrap().set(
        key,
        Some(Arc::new(Entry {
            id,
            layer,
            _pin: Some(pin),
        })),
    );
    0
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_map_remove(pointer: *const Owner, key: i64) -> u32 {
    if pointer.is_null() {
        return 0;
    }
    // Keep the returned object pinned until Java resolves it, then acknowledges
    // queued retirement; Java resolves the slot before its next bounded drain.
    (*pointer)
        .state
        .write()
        .unwrap()
        .remove(key)
        .map_or(0, |entry| entry.id)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_map_cache(pointer: *const Owner, disable: u32) -> i32 {
    if pointer.is_null() {
        return -1;
    }
    let mut state = (*pointer).state.write().unwrap();
    if disable != 0 {
        state.cache_enabled = false;
    } else {
        state.clear_cache();
    }
    0
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_map_top(pointer: *const Owner, key: i64) -> i32 {
    if pointer.is_null() {
        return 0;
    }
    (*pointer).state.read().unwrap().top(key)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_map_top_set(
    pointer: *const Owner,
    key: i64,
    value: i32,
    remove: u32,
) -> i32 {
    if pointer.is_null() {
        return 0;
    }
    let mut state = (*pointer).state.write().unwrap();
    if remove != 0 {
        state.remove_top(key)
    } else {
        state.put_top(key, value)
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_map_min_y(
    pointer: *const Owner,
    value: i32,
    default_only: u32,
) -> i32 {
    if pointer.is_null() {
        return -1;
    }
    let source = &*pointer;
    let mut state = source.state.write().unwrap();
    if default_only == 0 {
        state.lowest_y = value;
        source.metadata.lowest.store(value, Ordering::Release);
    }
    state.top_default = value;
    source.metadata.default.store(value, Ordering::Release);
    0
}

/// Verification projection only; normal light publication/consumers do not enumerate maps.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_map_count(pointer: *const Owner, tops: u32) -> i32 {
    if pointer.is_null() {
        return -1;
    }
    i32::try_from((*pointer).state.read().unwrap().count(tops != 0)).unwrap_or(-1)
}
/// # Safety
/// Output is an aligned caller-owned CPU span; map mutation is caller-excluded.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_map_keys(
    pointer: *const Owner,
    tops: u32,
    output: *mut i64,
    capacity: i32,
) -> i32 {
    if pointer.is_null() || capacity < 0 {
        return -1;
    }
    let state = (*pointer).state.read().unwrap();
    let count = state.count(tops != 0);
    if count > capacity as usize || (count != 0 && output.is_null()) {
        return -1;
    }
    if count != 0 {
        let keys = state.keys(tops != 0);
        std::ptr::copy_nonoverlapping(keys.as_ptr(), output, keys.len());
    }
    count as i32
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn exported_layer_owner_survives_java_release_and_declines_after_escape() {
        use super::super::super::layers::ffi as light;
        unsafe {
            let registry = mattmc_light_map_registry_create();
            let map = mattmc_light_map_create(registry);
            let mut slot = 0;
            let reservation = mattmc_light_map_reserve(registry, &mut slot);
            let mut projection = std::ptr::null_mut();
            let layer = light::mattmc_light_layer_create(7, &mut projection);
            assert_eq!(mattmc_light_map_set(map, 9, reservation, layer), 0);
            let snapshot = mattmc_light_map_copy(map);
            light::mattmc_light_layer_view_release(projection);
            let initial = mattmc_light_map_raw_layer_view(snapshot, 9);
            assert_eq!((*initial).borrowed_view().get(0), Ok(7));
            assert_eq!(light::mattmc_light_layer_fill(layer, 3, &mut projection), 0);
            let filled = mattmc_light_map_raw_layer_view(snapshot, 9);
            assert_eq!((*filled).borrowed_view().get(0), Ok(3));
            assert_eq!((*initial).borrowed_view().get(0), Ok(7));
            light::mattmc_light_layer_invalidate(layer);
            assert!(mattmc_light_map_raw_layer_view(snapshot, 9).is_null());
            light::mattmc_light_layer_release(layer);
            light::mattmc_light_layer_view_release(projection);
            mattmc_light_map_release(map);
            mattmc_light_map_release(snapshot);
            // Independent generation leases outlive both typed owners/maps.
            assert_eq!((*filled).borrowed_view().get(0), Ok(3));
            assert_eq!((*initial).borrowed_view().get(0), Ok(7));
            light::mattmc_light_layer_view_release(initial);
            light::mattmc_light_layer_view_release(filled);
            let mut retired = [0; 64];
            assert_eq!(
                mattmc_light_map_retired(registry, retired.as_mut_ptr(), 64),
                1
            );
            assert_eq!(retired[0], slot);
            assert_eq!(
                mattmc_light_map_retired_ack(registry, retired.as_ptr(), 1),
                0
            );
            mattmc_light_map_registry_release(registry);
        }
    }

    #[test]
    fn foreign_reservation_rejection_does_not_consume_the_slot() {
        unsafe {
            let first = mattmc_light_map_registry_create();
            let other = mattmc_light_map_registry_create();
            let map = mattmc_light_map_create(first);
            let mut slot = 0;
            let reservation = mattmc_light_map_reserve(other, &mut slot);
            assert_eq!(
                mattmc_light_map_set(map, 7, reservation, std::ptr::null()),
                -1
            );
            assert_eq!(mattmc_light_map_has(map, 7), 0);
            mattmc_light_map_reservation_release(reservation);
            mattmc_light_map_release(map);
            mattmc_light_map_registry_release(first);
            mattmc_light_map_registry_release(other);
        }
    }
    #[test]
    fn snapshot_cache_and_projection_keep_owners_pinned() {
        unsafe {
            let registry = mattmc_light_map_registry_create();
            let map = mattmc_light_map_create(registry);
            let mut slot = 0;
            let reservation = mattmc_light_map_reserve(registry, &mut slot);
            let layer = Arc::into_raw(Arc::new(Layer::new(7)));
            assert_eq!(mattmc_light_map_set(map, 1, reservation, layer), 0);
            drop(Arc::from_raw(layer));
            assert_eq!(mattmc_light_map_get(map, 1, 0), slot);
            let snapshot = mattmc_light_map_copy(map);
            let view = mattmc_light_map_view(snapshot);
            assert_eq!(mattmc_light_map_remove(map, 1), slot);
            mattmc_light_map_cache(map, 0);
            mattmc_light_map_release(map);
            mattmc_light_map_release(snapshot);
            let mut retired = [0; 64];
            assert_eq!(
                mattmc_light_map_retired(registry, retired.as_mut_ptr(), 64),
                0
            );
            assert_eq!((*(*view).metadata).lowest.load(Ordering::Acquire), i32::MAX);
            mattmc_light_map_view_release(view);
            assert_eq!(
                mattmc_light_map_retired(registry, retired.as_mut_ptr(), 64),
                1
            );
            assert_eq!(retired[0], slot);
            assert_eq!(
                mattmc_light_map_retired_ack(registry, retired.as_ptr(), 1),
                0
            );
            mattmc_light_map_registry_release(registry);
        }
    }
}

//! World light-map CPU ABI. All inbound handles are typed, live Rust owners retained
//! by the caller for the call; no GPU resources or game-object pointers occur.
use super::super::layers::Layer;
use super::{
    registry::{Registry, Reservation, ReservationBatch, ReservationBatchMetadata},
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
    pub(super) registry: Arc<Registry>,
    pub(super) state: RwLock<LightMap>,
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
/// Typed sibling-owner operations. Every lock ends before the caller can
/// return an effect to Java; snapshots retain both map roots and layer pins.
impl Owner {
    pub(in crate::world::level::lighting) fn snapshot(&self) -> Arc<Self> {
        owner(Arc::clone(&self.registry), self.state.read().unwrap().snapshot())
    }
    pub(in crate::world::level::lighting) fn raw(&self, key: i64) -> Option<Arc<Entry>> {
        self.state.read().unwrap().raw(key)
    }
    pub(in crate::world::level::lighting) fn get(&self, key: i64) -> Option<Arc<Entry>> {
        self.state.write().unwrap().get(key)
    }
    /// A complete native-only reconciliation consumes the actual updating map
    /// under one ownership guard. The caller must return before Java effects.
    pub(in crate::world::level::lighting) fn reconcile<R>(&self,run:impl FnOnce(&mut LightMap)->R)->R {
        run(&mut self.state.write().unwrap())
    }
    pub(in crate::world::level::lighting) fn set(&self, key: i64, entry: Option<Arc<Entry>>) {
        self.state.write().unwrap().set(key, entry);
    }
    pub(in crate::world::level::lighting) fn remove(&self, key: i64) -> Option<Arc<Entry>> {
        self.state.write().unwrap().remove(key)
    }
    pub(in crate::world::level::lighting) fn clear_cache(&self, disable: bool) {
        let mut map = self.state.write().unwrap();
        if disable { map.cache_enabled = false; } else { map.clear_cache(); }
    }
    pub(in crate::world::level::lighting) fn native_entry(&self, layer: Arc<Layer>) -> Option<Arc<Entry>> {
        self.registry.native_layer(layer)
    }
    pub(in crate::world::level::lighting) fn top(&self, key: i64) -> i32 {
        self.state.read().unwrap().top(key)
    }
    pub(in crate::world::level::lighting) fn lowest(&self) -> i32 {
        self.metadata.lowest.load(Ordering::Acquire)
    }
    pub(in crate::world::level::lighting) fn set_lowest(&self, value: i32) {
        let mut map = self.state.write().unwrap();
        map.lowest_y = value; map.top_default = value;
        self.metadata.lowest.store(value, Ordering::Release);
        self.metadata.default.store(value, Ordering::Release);
    }
    pub(in crate::world::level::lighting) fn set_top(&self, key: i64, value: Option<i32>) {
        let mut map = self.state.write().unwrap();
        if let Some(value) = value { map.put_top(key, value); } else { map.remove_top(key); }
    }
    pub(in crate::world::level::lighting) fn owns_batch(&self,batch:&ReservationBatch)->bool{batch.belongs_to(&self.registry)}
    pub(in crate::world::level::lighting) fn owns_reservation(&self, reservation: &Reservation) -> bool {
        Arc::ptr_eq(&self.registry, &reservation.registry)
    }
    pub(in crate::world::level::lighting) fn entry(&self, reservation: Reservation, layer: Option<Arc<Layer>>, identity: u64) -> Option<Arc<Entry>> {
        if !Arc::ptr_eq(&self.registry, &reservation.registry) { return None; }
        let pin = reservation.install();
        let entry=Arc::new(Entry { id: pin.slot, identity, layer, _pin: Some(pin) });
        self.registry.bind_entry(&entry);Some(entry)
    }
    pub(in crate::world::level::lighting) fn existing_entry(&self, identity:u64, layer:*const Layer)->Option<Arc<Entry>> {
        self.registry.existing_entry(identity,layer)
    }
    pub(in crate::world::level::lighting) fn owns_entry(&self,entry:&Entry)->bool {self.registry.owns_entry(entry)}
    pub(in crate::world::level::lighting) fn binding_pending(&self)->bool {self.registry.metadata.retired.load(Ordering::Acquire)!=0}
}

/// Retain an existing actual VM identity entry, preserving its current primary
/// layer capability. The caller's identity comes from its actual resource root.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_map_entry_existing(owner:*const Owner,identity:u64,layer:*const Layer)->*const Entry {
    owner.as_ref().and_then(|owner|owner.existing_entry(identity,layer)).map_or(std::ptr::null(),Arc::into_raw)
}

/// Materialize a Java facade lazily for a native-created identity. Both returned
/// leases are independent of map/registry lifetime and consumed by DataLayer.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_map_materialize(
    pointer: *const Registry, slot: u32,
    output: *mut u64,
) -> *const Layer {
    materialize(pointer, slot, output, true)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_light_map_materialize_primary(
    pointer: *const Registry, slot: u32, output: *mut u64,
) -> *const Layer {
    materialize(pointer, slot, output, false)
}

unsafe fn materialize(pointer: *const Registry, slot: u32, output: *mut u64, view: bool) -> *const Layer {
    if pointer.is_null() || output.is_null() { return std::ptr::null(); }
    *output = 0; *output.add(1) = 0;
    let Some((layer, identity)) = (*pointer).layer_for_slot(slot) else { return std::ptr::null(); };
    if view { *output = super::super::layers::ffi::projection(&layer) as u64; }
    *output.add(1) = identity;
    Arc::into_raw(layer)
}

/// Metadata is part of the actual map root. A Java facade retaining that root
/// needs no separate MapView allocation or independent cleanup registration.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_map_metadata(pointer: *const Owner) -> *const MapMetadata {
    pointer.as_ref().map_or(std::ptr::null(), |owner| &owner.metadata)
}

/// A temporary typed entry lease used for a contextual/custom Java result.
/// A valid successful call consumes the reservation; null return leaves it
/// caller-owned. The coordinator clones the entry, then Java releases this lease.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_map_entry_create(
    pointer: *const Owner, reservation: *mut Reservation, layer: *const Layer, identity: u64,
) -> *const Entry {
    if pointer.is_null() || reservation.is_null() || identity == 0 || !Arc::ptr_eq(&(*pointer).registry, &(*reservation).registry) {
        return std::ptr::null();
    }
    let reservation = *Box::from_raw(reservation);
    let entry = (*pointer).entry(reservation, if layer.is_null() { None } else { Some(retain(layer)) }, identity);
    entry.map_or(std::ptr::null(), Arc::into_raw)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_map_entry_release(pointer: *const Entry) {
    if !pointer.is_null() { drop(Arc::from_raw(pointer)); }
}
#[no_mangle]
pub extern "C" fn mattmc_light_map_registry_create() -> *const Registry {
    Arc::into_raw(Registry::new())
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_map_identity_create(pointer: *const Registry) -> u64 {
    pointer.as_ref().and_then(Registry::identity).unwrap_or(0)
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
    mattmc_light_map_set_identity(pointer, key, reservation, layer, 0)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_light_map_set_identity(
    pointer: *const Owner, key: i64, reservation: *mut Reservation,
    layer: *const Layer, identity: u64,
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
    let identity = if identity == 0 {
        let Some(identity) = source.registry.identity() else { return -1; };
        identity
    } else { identity };
    let reservation = *Box::from_raw(reservation);
    let layer = if layer.is_null() {
        None
    } else {
        Some(retain(layer))
    };
    let entry=source.entry(reservation,layer,identity).expect("validated registry owns its reservation");
    source.state.write().unwrap().set(key,Some(entry));
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

/// Private queued-input transport lease. Published individual-reservation ABI
/// stays unchanged; unused cells return to the registry when this lease dies.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_map_reservation_batch_create(registry:*const Registry,limit:usize)->*const ReservationBatch{
    if registry.is_null(){return std::ptr::null();}
    ReservationBatch::new(&retain(registry),limit).map_or(std::ptr::null(),Arc::into_raw)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_map_reservation_batch_header(batch:*const ReservationBatch)->*const ReservationBatchMetadata{
    batch.as_ref().map_or(std::ptr::null(),|batch|&batch.metadata)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_map_reservation_batch_release(batch:*const ReservationBatch){if !batch.is_null(){drop(Arc::from_raw(batch));}}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_map_reservation_batch_cancel(batch:*const ReservationBatch,index:i32)->i32{
    if batch.is_null()||index<0{return -1;}if (*batch).cancel(index as usize){0}else{-1}
}

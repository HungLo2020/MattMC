//! CPU light-layer boundary. Handles and view leases are separately released;
//! every input pointer is live and caller-owned for the duration of its call.
use super::*;

#[repr(C)]
pub struct Projection {
    default: i32,
    allocated: u32,
    bytes: *const AtomicU8,
    _view: View,
}

impl Projection {
    pub(crate) fn borrowed_view(&self) -> &View {
        &self._view
    }
}

pub(crate) fn projection(layer: &Layer) -> *mut Projection {
    projection_view(layer.view())
}

fn projection_view(view: View) -> *mut Projection {
    Box::into_raw(Box::new(Projection {
        default: view.default_value(),
        allocated: u32::from(view.allocated()),
        bytes: view.bytes_address().unwrap_or(std::ptr::null()),
        _view: view,
    }))
}

/// The primary owner already retains its metadata. Internal production and
/// mutation do not need to allocate an independent generation projection.
#[no_mangle]
pub extern "C" fn mattmc_light_layer_primary_create(default: i32) -> *const Layer {
    Arc::into_raw(Arc::new(Layer::new(default)))
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_light_layer_primary_import(bytes: *const u8, length: u32) -> *const Layer {
    if bytes.is_null() || length != BYTES as u32 { return std::ptr::null(); }
    Arc::into_raw(Arc::new(Layer::from_bytes(*bytes.cast::<[u8; BYTES]>())))
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_light_layer_primary_copy(layer: *const Layer, repeat: i32) -> *const Layer {
    let Some(layer) = layer.as_ref() else { return std::ptr::null(); };
    if !layer.is_valid() || !(0..=1).contains(&repeat) { return std::ptr::null(); }
    Arc::into_raw(Arc::new(if repeat == 0 { layer.copy() } else { layer.repeat_first() }))
}

/// Header is borrowed from this actual retained Layer allocation; it contains
/// no byte pointer. The caller fences the owner for every metadata read.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_layer_primary_header(layer: *const Layer) -> *const Header {
    layer.as_ref().map_or(std::ptr::null(), |layer| &layer.header)
}

/// op0 set, op1 fill, op2 materialize. A fill revives the same escaped identity.
/// All other native operations require its native generation to be authoritative.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_layer_primary_change(layer: *const Layer, op: i32, index: i32, value: i32) -> i32 {
    let Some(layer) = layer.as_ref() else { return -1; };
    if op != 1 && !layer.is_valid() { return -1; }
    match op {
        0 if (0..4096).contains(&index) => { layer.set(index, value).expect("validated light index"); }
        1 => layer.fill(value),
        2 => { layer.materialize(); }
        _ => return -1,
    }
    0
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_light_layer_primary_export(layer: *const Layer, bytes: *mut u8, length: u32) -> i32 {
    let Some(layer) = layer.as_ref() else { return -1; };
    if !layer.is_valid() || bytes.is_null() || length != BYTES as u32 { return -1; }
    layer.export_materialized(&mut *bytes.cast::<[u8; BYTES]>());
    0
}

/// An actual Java reader explicitly acquires this independent generation lease.
/// Return its identity from that SAME pinned generation, avoiding header/view
/// races or address reuse between independent reads of the primary owner.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_layer_primary_view(layer: *const Layer, identity: *mut u64) -> *mut Projection {
    if identity.is_null() { return std::ptr::null_mut(); }
    *identity = 0;
    let Some(layer) = layer.as_ref() else { return std::ptr::null_mut(); };
    if !layer.is_valid() { return std::ptr::null_mut(); }
    let view = layer.view();
    *identity = Arc::as_ptr(&view.generation) as u64;
    projection_view(view)
}

/// Independent current-generation lease after a native coordinator write.
/// Existing generation leases keep their original lifetime and contents.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_layer_view(layer: *const Layer) -> *mut Projection {
    layer.as_ref().map_or(std::ptr::null_mut(), projection)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_light_layer_create(
    default: i32,
    view: *mut *mut Projection,
) -> *mut Layer {
    if view.is_null() {
        return std::ptr::null_mut();
    }
    let layer = Arc::new(Layer::new(default));
    unsafe {
        *view = projection(&layer);
    }
    Arc::into_raw(layer) as *mut Layer
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_light_layer_import(
    bytes: *const u8,
    length: u32,
    view: *mut *mut Projection,
) -> *mut Layer {
    if bytes.is_null() || length != BYTES as u32 || view.is_null() {
        return std::ptr::null_mut();
    }
    let values = unsafe { *(bytes.cast::<[u8; BYTES]>()) };
    let layer = Arc::new(Layer::from_bytes(values));
    unsafe {
        *view = projection(&layer);
    }
    Arc::into_raw(layer) as *mut Layer
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_light_layer_copy(
    layer: *const Layer,
    view: *mut *mut Projection,
) -> *mut Layer {
    if layer.is_null() || view.is_null() {
        return std::ptr::null_mut();
    }
    let copy = Arc::new(unsafe { &*layer }.copy());
    unsafe {
        *view = projection(&copy);
    }
    Arc::into_raw(copy) as *mut Layer
}

/// # Safety
/// Layer is live and caller-excluded; view addresses one writable lease pointer.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_layer_repeat(
    layer: *const Layer,
    view: *mut *mut Projection,
) -> *mut Layer {
    if layer.is_null() || view.is_null() {
        return std::ptr::null_mut();
    }
    let repeated = Arc::new((&*layer).repeat_first());
    *view = projection(&repeated);
    Arc::into_raw(repeated) as *mut Layer
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_light_layer_set(
    layer: *const Layer,
    index: i32,
    value: i32,
    view: *mut *mut Projection,
) -> i32 {
    // Java preserves its original error/array escape path for invalid indices.
    if layer.is_null() || !(0..4096).contains(&index) || view.is_null() {
        return -1;
    }
    let layer = unsafe { &*layer };
    let changed = layer.set(index, value).expect("validated light index");
    unsafe {
        *view = if changed {
            projection(layer)
        } else {
            std::ptr::null_mut()
        };
    }
    0
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_light_layer_fill(
    layer: *const Layer,
    default: i32,
    view: *mut *mut Projection,
) -> i32 {
    if layer.is_null() || view.is_null() {
        return -1;
    }
    let layer = unsafe { &*layer };
    layer.fill(default);
    unsafe {
        *view = projection(layer);
    }
    0
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_light_layer_materialize(
    layer: *const Layer,
    view: *mut *mut Projection,
) -> i32 {
    if layer.is_null() || view.is_null() {
        return -1;
    }
    let layer = unsafe { &*layer };
    let changed = layer.materialize();
    unsafe {
        *view = if changed {
            projection(layer)
        } else {
            std::ptr::null_mut()
        };
    }
    0
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_light_layer_install(
    layer: *const Layer,
    bytes: *const u8,
    length: u32,
    view: *mut *mut Projection,
) -> i32 {
    if layer.is_null() || bytes.is_null() || length != BYTES as u32 || view.is_null() {
        return -1;
    }
    let values = unsafe { *(bytes.cast::<[u8; BYTES]>()) };
    let layer = unsafe { &*layer };
    let changed = layer.install_bytes(values);
    unsafe {
        *view = if changed {
            projection(layer)
        } else {
            std::ptr::null_mut()
        };
    }
    0
}

/// # Safety
/// Layer is a live caller-retained CPU owner. Existing leases remain valid.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_layer_invalidate(layer: *const Layer) {
    if let Some(layer) = layer.as_ref() {
        layer.invalidate();
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_light_layer_release(layer: *mut Layer) {
    if !layer.is_null() {
        drop(unsafe { Arc::from_raw(layer) });
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_light_layer_view_release(view: *mut Projection) {
    if !view.is_null() {
        drop(unsafe { Box::from_raw(view) });
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn primary_identity_survives_escape_fill_and_explicit_views_outlive_owner() {
        unsafe {
            let owner = mattmc_light_layer_primary_create(16);
            let header = mattmc_light_layer_primary_header(owner);
            assert_eq!((*header).default.load(Ordering::Acquire), 16);
            assert_eq!((*header).allocated.load(Ordering::Acquire), 0);
            let mut lazy_identity = 0;
            let lazy = mattmc_light_layer_primary_view(owner, &mut lazy_identity);
            assert_eq!(lazy_identity, (*header).generation.load(Ordering::Acquire));
            let mut bytes = [0; BYTES];
            assert_eq!(mattmc_light_layer_primary_export(owner, bytes.as_mut_ptr(), BYTES as u32), 0);
            assert_eq!(bytes, [0x10; BYTES]);
            assert_eq!((*header).allocated.load(Ordering::Acquire), 1);
            assert_ne!(lazy_identity, (*header).generation.load(Ordering::Acquire));
            assert_eq!((*lazy)._view.get(-5000), Ok(16));

            let mut allocated_identity = 0;
            let allocated = mattmc_light_layer_primary_view(owner, &mut allocated_identity);
            assert_eq!(mattmc_light_layer_primary_change(owner, 0, 0, 9), 0);
            assert_eq!((*allocated)._view.get(0), Ok(9));
            assert_eq!(allocated_identity, (*header).generation.load(Ordering::Acquire));
            mattmc_light_layer_invalidate(owner);
            assert_eq!((*header).valid.load(Ordering::Acquire), 0);
            assert!(mattmc_light_layer_primary_copy(owner, 0).is_null());
            assert_eq!(mattmc_light_layer_primary_change(owner, 0, 0, 4), -1);
            assert_eq!(mattmc_light_layer_primary_change(owner, 1, 0, 3), 0);
            assert_eq!(mattmc_light_layer_primary_header(owner), header);
            assert_eq!((*header).valid.load(Ordering::Acquire), 1);
            assert_eq!((*header).default.load(Ordering::Acquire), 3);
            assert_eq!((*header).allocated.load(Ordering::Acquire), 0);
            assert_ne!(allocated_identity, (*header).generation.load(Ordering::Acquire));
            assert_eq!((*allocated)._view.get(0), Ok(9));

            let copy = mattmc_light_layer_primary_copy(owner, 0);
            assert!(!copy.is_null());
            assert_eq!(mattmc_light_layer_primary_change(copy, 1, 0, 7), 0);
            assert_eq!((*header).default.load(Ordering::Acquire), 3);
            mattmc_light_layer_release(owner.cast_mut());
            mattmc_light_layer_release(copy.cast_mut());
            assert_eq!((*lazy)._view.get(1), Ok(16));
            assert_eq!((*allocated)._view.get(0), Ok(9));
            mattmc_light_layer_view_release(lazy);
            mattmc_light_layer_view_release(allocated);
        }
    }

    #[test]
    fn primary_import_copies_input_and_declines_invalid_operations_before_mutation() {
        unsafe {
            let mut bytes = [0x23; BYTES];
            let owner = mattmc_light_layer_primary_import(bytes.as_ptr(), BYTES as u32);
            let header = mattmc_light_layer_primary_header(owner);
            let identity = (*header).generation.load(Ordering::Acquire);
            bytes.fill(0x45);
            assert_eq!((&*owner).view().get(0), Ok(3));
            assert_eq!(mattmc_light_layer_primary_change(owner, 0, -1, 9), -1);
            assert_eq!(mattmc_light_layer_primary_change(owner, 99, 0, 9), -1);
            assert_eq!((*header).generation.load(Ordering::Acquire), identity);
            assert_eq!((&*owner).view().get(0), Ok(3));
            assert!(mattmc_light_layer_primary_import(bytes.as_ptr(), 2047).is_null());
            assert!(mattmc_light_layer_primary_copy(owner, 2).is_null());
            mattmc_light_layer_release(owner.cast_mut());
        }
    }

    #[test]
    fn exported_leases_survive_owner_release_and_preserve_copy_default() {
        unsafe {
            let mut view = std::ptr::null_mut();
            let owner = mattmc_light_layer_create(15, &mut view);
            let lazy = view;
            assert_eq!((*lazy).default, 15);
            assert_eq!(mattmc_light_layer_set(owner, 7, 3, &mut view), 0);
            let allocated = view;
            assert_eq!((*allocated).allocated, 1);
            let copy = mattmc_light_layer_copy(owner, &mut view);
            assert_eq!((*view).default, 0);
            assert_eq!(
                mattmc_light_layer_set(copy, 7, 4, &mut std::ptr::null_mut()),
                0
            );
            let copy_view = view;
            let values = [0x12; BYTES];
            assert_eq!(
                mattmc_light_layer_install(owner, values.as_ptr(), BYTES as u32, &mut view),
                0
            );
            assert!(
                view.is_null(),
                "allocated writes retain their existing projection"
            );
            assert_eq!((*allocated).default, 15);
            assert_eq!((*allocated)._view.get(7), Ok(1));
            assert_eq!((*copy_view)._view.get(7), Ok(4));
            mattmc_light_layer_release(owner);
            mattmc_light_layer_release(copy);
            assert_eq!((*allocated)._view.get(7), Ok(1));
            for retained in [lazy, allocated, copy_view] {
                mattmc_light_layer_view_release(retained);
            }
        }
    }
}

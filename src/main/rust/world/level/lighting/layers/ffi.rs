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
    let view = layer.view();
    Box::into_raw(Box::new(Projection {
        default: view.default_value(),
        allocated: u32::from(view.allocated()),
        bytes: view.bytes_address().unwrap_or(std::ptr::null()),
        _view: view,
    }))
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

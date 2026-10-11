//! C ABI for resource managers (`NativeResourceManager`).
//!
//! Results are owned buffers in one record format: a namespace table (u32
//! count, then u32-length names), u32 location count, and per location the
//! namespace index (u32), path (u32 length + bytes) and u32 source count,
//! each source being pack (u16), padding (u16), entry (u64), sidecar pack
//! (i32, -1 for none) and sidecar entry (u64).

use std::collections::HashMap;
use std::slice;
use std::sync::atomic::{AtomicPtr, Ordering};
use std::sync::Arc;

use super::manager::{Filters, Found, Located, Manager, NoFilters};
use crate::assets::ffi_util::{give, str_arg, Writer};
use crate::assets::packs::ffi::pack;
use crate::assets::packs::pack::PACK_TYPE_COUNT;

const KIND_NAMESPACE: i32 = 0;
const KIND_PATH: i32 = 1;

type FilterUpcall = unsafe extern "C" fn(token: u64, pack: u32, kind: i32, text: *const u8, length: u64) -> i32;

static FILTER: AtomicPtr<()> = AtomicPtr::new(std::ptr::null_mut());

/// Java's filter callback, tagged with the manager's token.
struct JavaFilters {
    token: u64,
}

impl JavaFilters {
    fn ask(&self, pack: u16, kind: i32, text: &str) -> bool {
        let upcall = FILTER.load(Ordering::Acquire);
        if upcall.is_null() {
            return false;
        }
        let upcall: FilterUpcall = unsafe { std::mem::transmute::<*mut (), FilterUpcall>(upcall) };
        unsafe { upcall(self.token, u32::from(pack), kind, text.as_ptr(), text.len() as u64) != 0 }
    }
}

impl Filters for JavaFilters {
    fn namespace_filtered(&self, pack: u16, namespace: &str) -> bool {
        self.ask(pack, KIND_NAMESPACE, namespace)
    }

    fn path_filtered(&self, pack: u16, path: &str) -> bool {
        self.ask(pack, KIND_PATH, path)
    }
}

struct Handle {
    manager: Manager,
    filters: JavaFilters,
}

impl Handle {
    fn filters(&self) -> &dyn Filters {
        if self.manager.has_filters() {
            &self.filters
        } else {
            &NoFilters
        }
    }
}

unsafe fn handle(handle: u64) -> Option<&'static Handle> {
    if handle == 0 {
        None
    } else {
        Some(unsafe { &*(handle as *const Handle) })
    }
}

/// Registers Java's filter upcall (`filter(token, pack, kind, text) != 0`).
///
/// # Safety
/// `upcall` must stay valid for the process.
#[no_mangle]
pub unsafe extern "C" fn mattmc_resources_register_filter(upcall: Option<FilterUpcall>) {
    FILTER.store(upcall.map_or(std::ptr::null_mut(), |upcall| upcall as *mut ()), Ordering::Release);
}

/// `new MultiPackResourceManager(type, packs)` over live pack handles;
/// `has_filter[i]` marks packs with a filter section, matched through the
/// filter upcall with `token`. Returns a handle (0 on bad input).
///
/// # Safety
/// `packs` and `has_filter` must hold `count` values; pack handles live.
#[no_mangle]
pub unsafe extern "C" fn mattmc_resources_create(
    pack_type: u32,
    packs: *const u64,
    has_filter: *const u8,
    count: u64,
    token: u64,
) -> u64 {
    if pack_type as usize >= PACK_TYPE_COUNT || (count > 0 && (packs.is_null() || has_filter.is_null())) {
        return 0;
    }
    let handles = if count == 0 { &[][..] } else { unsafe { slice::from_raw_parts(packs, count as usize) } };
    let filtered = if count == 0 { &[][..] } else { unsafe { slice::from_raw_parts(has_filter, count as usize) } };
    let mut list = Vec::with_capacity(handles.len());
    for &pack_handle in handles {
        match unsafe { pack(pack_handle) } {
            Some(pack) => list.push(Arc::clone(pack)),
            None => return 0,
        }
    }
    let filters = JavaFilters { token };
    let has_filter: Vec<bool> = filtered.iter().map(|&flag| flag != 0).collect();
    let any = has_filter.iter().any(|&flag| flag);
    let manager = Manager::new(pack_type as usize, list, has_filter, if any { &filters } else { &NoFilters });
    Box::into_raw(Box::new(Handle { manager, filters })) as u64
}

/// Drops a manager handle (its packs stay open).
///
/// # Safety
/// `manager` must be live; it is invalid afterwards.
#[no_mangle]
pub unsafe extern "C" fn mattmc_resources_release(manager: u64) {
    if manager != 0 {
        drop(unsafe { Box::from_raw(manager as *mut Handle) });
    }
}

/// `MultiPackResourceManager.close`: closes every pack.
///
/// # Safety
/// `manager` must be live.
#[no_mangle]
pub unsafe extern "C" fn mattmc_resources_close(manager: u64) {
    if let Some(handle) = unsafe { handle(manager) } {
        handle.manager.close();
    }
}

/// `getNamespaces()` in insertion order, `\n`-separated.
///
/// # Safety
/// `manager` live; outputs writable.
#[no_mangle]
pub unsafe extern "C" fn mattmc_resources_namespaces(manager: u64, out_ptr: *mut *mut u8, out_len: *mut u64) -> i32 {
    let Some(handle) = (unsafe { handle(manager) }) else { return -1 };
    let names: Vec<&str> = handle.manager.namespaces().collect();
    unsafe { give(names.join("\n").into_bytes(), out_ptr, out_len) };
    0
}

#[repr(C)]
pub struct FfiLocated {
    pub pack: u32,
    pub position: u32,
    pub entry: u64,
}

/// `getResource(namespace:path)`: 1 with the pack, its stack position (for
/// the sidecar search) and entry, else 0.
///
/// # Safety
/// `manager` live; strings valid; `out` writable.
#[no_mangle]
pub unsafe extern "C" fn mattmc_resources_get(
    manager: u64,
    namespace: *const u8,
    namespace_len: u64,
    path: *const u8,
    path_len: u64,
    out: *mut FfiLocated,
) -> i32 {
    let (Some(handle), Some(namespace), Some(path), false) =
        (unsafe { handle(manager) }, unsafe { str_arg(namespace, namespace_len) }, unsafe { str_arg(path, path_len) }, out.is_null())
    else {
        return 0;
    };
    match handle.manager.resource(namespace, path, handle.filters()) {
        Some((located, position)) => {
            unsafe {
                *out = FfiLocated { pack: u32::from(located.pack), position: position as u32, entry: located.entry.encode() };
            }
            1
        }
        None => 0,
    }
}

/// The sidecar search of a `getResource` result from its stack position.
///
/// # Safety
/// `manager` live; strings valid; `out` writable.
#[no_mangle]
pub unsafe extern "C" fn mattmc_resources_stack_metadata(
    manager: u64,
    namespace: *const u8,
    namespace_len: u64,
    path: *const u8,
    path_len: u64,
    position: u32,
    out: *mut FfiLocated,
) -> i32 {
    let (Some(handle), Some(namespace), Some(path), false) =
        (unsafe { handle(manager) }, unsafe { str_arg(namespace, namespace_len) }, unsafe { str_arg(path, path_len) }, out.is_null())
    else {
        return 0;
    };
    match handle.manager.stack_metadata(namespace, path, position as usize, handle.filters()) {
        Some(located) => {
            unsafe { *out = FfiLocated { pack: u32::from(located.pack), position, entry: located.entry.encode() } };
            1
        }
        None => 0,
    }
}

fn write_results(results: &[((String, String), Vec<Found>)]) -> Vec<u8> {
    let mut namespaces: Vec<&str> = Vec::new();
    let mut indices: HashMap<&str, u32> = HashMap::new();
    for ((namespace, _), _) in results {
        indices.entry(namespace.as_str()).or_insert_with(|| {
            namespaces.push(namespace.as_str());
            (namespaces.len() - 1) as u32
        });
    }
    let mut writer = Writer::default();
    writer.u32(namespaces.len() as u32);
    for namespace in &namespaces {
        writer.text(namespace);
    }
    writer.u32(results.len() as u32);
    for ((namespace, path), sources) in results {
        writer.u32(indices[namespace.as_str()]);
        writer.text(path);
        writer.u32(sources.len() as u32);
        for found in sources {
            writer.u16(found.resource.pack);
            writer.u16(0);
            writer.u64(found.resource.entry.encode());
            match found.metadata {
                Some(Located { pack, entry }) => {
                    writer.i32(i32::from(pack));
                    writer.u64(entry.encode());
                }
                None => {
                    writer.i32(-1);
                    writer.u64(0);
                }
            }
        }
    }
    writer.bytes
}

/// `getResourceStack(namespace:path)` in the record format (one location,
/// or none).
///
/// # Safety
/// `manager` live; strings valid; outputs writable.
#[no_mangle]
pub unsafe extern "C" fn mattmc_resources_stack(
    manager: u64,
    namespace: *const u8,
    namespace_len: u64,
    path: *const u8,
    path_len: u64,
    out_ptr: *mut *mut u8,
    out_len: *mut u64,
) -> i32 {
    let (Some(handle), Some(namespace), Some(path)) =
        (unsafe { handle(manager) }, unsafe { str_arg(namespace, namespace_len) }, unsafe { str_arg(path, path_len) })
    else {
        return -1;
    };
    let stack = handle.manager.resource_stack(namespace, path, handle.filters());
    let results = if stack.is_empty() { Vec::new() } else { vec![((namespace.to_owned(), path.to_owned()), stack)] };
    unsafe { give(write_results(&results), out_ptr, out_len) };
    0
}

/// `listResources(directory)` in the record format (one source each).
///
/// # Safety
/// `manager` live; strings valid; outputs writable.
#[no_mangle]
pub unsafe extern "C" fn mattmc_resources_list(
    manager: u64,
    directory: *const u8,
    directory_len: u64,
    out_ptr: *mut *mut u8,
    out_len: *mut u64,
) -> i32 {
    let (Some(handle), Some(directory)) = (unsafe { handle(manager) }, unsafe { str_arg(directory, directory_len) }) else {
        return -1;
    };
    let results: Vec<((String, String), Vec<Found>)> = handle
        .manager
        .list(directory, handle.filters())
        .into_iter()
        .map(|(location, found)| (location, vec![found]))
        .collect();
    unsafe { give(write_results(&results), out_ptr, out_len) };
    0
}

/// `listResourceStacks(directory)` in the record format.
///
/// # Safety
/// `manager` live; strings valid; outputs writable.
#[no_mangle]
pub unsafe extern "C" fn mattmc_resources_list_stacks(
    manager: u64,
    directory: *const u8,
    directory_len: u64,
    out_ptr: *mut *mut u8,
    out_len: *mut u64,
) -> i32 {
    let (Some(handle), Some(directory)) = (unsafe { handle(manager) }, unsafe { str_arg(directory, directory_len) }) else {
        return -1;
    };
    let results = handle.manager.list_stacks(directory, handle.filters());
    unsafe { give(write_results(&results), out_ptr, out_len) };
    0
}

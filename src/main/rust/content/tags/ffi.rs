//! C ABI for tag loading and resolution (`TagLoader`).
//!
//! Java lists the tag file stacks and passes their bytes; Rust parses them,
//! plans `DependencySorter`'s order and the element lookups, and resolves
//! the tags from Java's lookup results.

use super::{load, plan, resolve, Loaded, Location, Plan, TagResource};
use crate::assets::ffi_util::{bytes_arg, give, Reader, Writer};

struct Handle {
    loaded: Loaded,
    plan: Option<Plan>,
}

unsafe fn handle<'a>(handle: u64) -> Option<&'a mut Handle> {
    if handle == 0 {
        None
    } else {
        Some(unsafe { &mut *(handle as *mut Handle) })
    }
}

/// Parses tag files: records of file location, tag namespace, tag path,
/// source pack (u32-length texts) and contents (u32-length bytes), in file
/// location order with each stack lowest pack first. Returns a handle.
///
/// # Safety
/// `records` valid for its length.
#[no_mangle]
pub unsafe extern "C" fn mattmc_tags_load(records: *const u8, records_len: u64) -> u64 {
    let Some(bytes) = (unsafe { bytes_arg(records, records_len) }) else { return 0 };
    let mut reader = Reader::new(bytes);
    let mut resources = Vec::new();
    while !reader.done() {
        let (Some(file), Some(namespace), Some(path), Some(pack), Some(contents)) =
            (reader.text(), reader.text(), reader.text(), reader.text(), reader.bytes())
        else {
            return 0;
        };
        resources.push(TagResource { file, id: Location::new(namespace, path), pack, bytes: contents });
    }
    Box::into_raw(Box::new(Handle { loaded: load(resources), plan: None })) as u64
}

/// Drops a tag handle.
///
/// # Safety
/// `tags` must be live; it is invalid afterwards.
#[no_mangle]
pub unsafe extern "C" fn mattmc_tags_release(tags: u64) {
    if tags != 0 {
        drop(unsafe { Box::from_raw(tags as *mut Handle) });
    }
}

/// Plans resolution. Writes the element lookups in call order (records of
/// namespace and path texts and a required byte) and returns 0; when Java's
/// cycle check would recurse forever, writes that tag's location as text
/// and returns 1.
///
/// # Safety
/// `tags` live; outputs writable.
#[no_mangle]
pub unsafe extern "C" fn mattmc_tags_plan(tags: u64, out_ptr: *mut *mut u8, out_len: *mut u64) -> i32 {
    let Some(handle) = (unsafe { handle(tags) }) else { return -1 };
    let mut writer = Writer::default();
    match plan(&handle.loaded) {
        Ok(planned) => {
            for (location, required) in &planned.lookups {
                writer.text(&location.namespace);
                writer.text(&location.path);
                writer.bytes.push(u8::from(*required));
            }
            handle.plan = Some(planned);
            unsafe { give(writer.bytes, out_ptr, out_len) };
            0
        }
        Err(overflow) => {
            writer.bytes.extend_from_slice(overflow.0.to_string().as_bytes());
            unsafe { give(writer.bytes, out_ptr, out_len) };
            1
        }
    }
}

/// Resolves the planned tags from `found` (one byte per lookup, nonzero when
/// Java's lookup returned a value). Writes, per tag in build order, its
/// namespace and path texts, a success byte, then u32 lookup indices (its
/// values) or the missing entries as texts, each list u32-counted.
///
/// # Safety
/// `tags` live and planned; `found` valid for `count` bytes; outputs writable.
#[no_mangle]
pub unsafe extern "C" fn mattmc_tags_resolve(tags: u64, found: *const u8, count: u64, out_ptr: *mut *mut u8, out_len: *mut u64) -> i32 {
    let Some(handle) = (unsafe { handle(tags) }) else { return -1 };
    let Some(planned) = handle.plan.as_ref() else { return -1 };
    let Some(found) = (unsafe { bytes_arg(found, count) }) else { return -1 };
    if found.len() != planned.lookups.len() {
        return -1;
    }
    let found: Vec<bool> = found.iter().map(|&flag| flag != 0).collect();
    let mut writer = Writer::default();
    for (location, built) in resolve(&handle.loaded, planned, &found) {
        writer.text(&location.namespace);
        writer.text(&location.path);
        match built {
            Ok(values) => {
                writer.bytes.push(1);
                writer.u32(values.len() as u32);
                for value in values {
                    writer.u32(value as u32);
                }
            }
            Err(missing) => {
                writer.bytes.push(0);
                writer.u32(missing.len() as u32);
                for entry in &missing {
                    writer.text(entry);
                }
            }
        }
    }
    unsafe { give(writer.bytes, out_ptr, out_len) };
    0
}

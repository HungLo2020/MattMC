//! C ABI for opened packs (`NativePackResources`).
//!
//! A handle owns one `Arc<Pack>`; Java releases it when its pack object is
//! unreachable (closing only releases archive files). Entry references are
//! [`EntryRef::encode`] values; a read takes the entry's path relative to its
//! root: `namespace/path` for resources, the joined segments for root files.

use std::path::PathBuf;
use std::sync::Arc;

use super::pack::{EntryRef, Pack, PackFlags, Root, ZipAccess, PACK_TYPE_COUNT};
use crate::assets::ffi_util::{bytes_arg, give, str_arg, write_text, Reader, Writer};

const FLAG_RUNNING_IN_IDE: u32 = 1 << 0;
const FLAG_VALIDATE_PATH_CASE: u32 = 1 << 1;

fn flags(bits: u32) -> PackFlags {
    PackFlags {
        running_in_ide: bits & FLAG_RUNNING_IN_IDE != 0,
        validate_path_case: bits & FLAG_VALIDATE_PATH_CASE != 0,
    }
}

fn into_handle(pack: Pack) -> u64 {
    Box::into_raw(Box::new(Arc::new(pack))) as u64
}

/// The pack behind a live handle.
///
/// # Safety
/// `handle` must come from a `mattmc_pack_open_*` call and not be released.
pub(crate) unsafe fn pack(handle: u64) -> Option<&'static Arc<Pack>> {
    if handle == 0 {
        None
    } else {
        Some(unsafe { &*(handle as *const Arc<Pack>) })
    }
}

fn overlay_list(text: &str) -> Vec<String> {
    text.split('\n').filter(|overlay| !overlay.is_empty()).map(str::to_owned).collect()
}

/// Opens a directory pack with its overlay directories (`\n`-separated).
///
/// # Safety
/// Strings must be valid for their lengths.
#[no_mangle]
pub unsafe extern "C" fn mattmc_pack_open_directory(
    id: *const u8,
    id_len: u64,
    path: *const u8,
    path_len: u64,
    overlays: *const u8,
    overlays_len: u64,
    flag_bits: u32,
) -> u64 {
    let (Some(id), Some(path), Some(overlays)) =
        (unsafe { (str_arg(id, id_len), str_arg(path, path_len), str_arg(overlays, overlays_len)) })
    else {
        return 0;
    };
    into_handle(Pack::directory(id.to_owned(), PathBuf::from(path), &overlay_list(overlays), flags(flag_bits)))
}

/// Opens a ZIP pack (lazily: the archive opens on first access).
///
/// # Safety
/// Strings must be valid for their lengths.
#[no_mangle]
pub unsafe extern "C" fn mattmc_pack_open_zip(
    id: *const u8,
    id_len: u64,
    path: *const u8,
    path_len: u64,
    overlays: *const u8,
    overlays_len: u64,
    flag_bits: u32,
) -> u64 {
    let (Some(id), Some(path), Some(overlays)) =
        (unsafe { (str_arg(id, id_len), str_arg(path, path_len), str_arg(overlays, overlays_len)) })
    else {
        return 0;
    };
    let access = ZipAccess::new(PathBuf::from(path));
    into_handle(Pack::zip(id.to_owned(), access, &overlay_list(overlays), flags(flag_bits)))
}

/// Opens a directory pack inside an archive: `prefix` (empty or ending in
/// `/`) names the directory, overlays are `\n`-separated.
///
/// # Safety
/// Strings must be valid for their lengths.
#[no_mangle]
pub unsafe extern "C" fn mattmc_pack_open_archive_directory(
    id: *const u8,
    id_len: u64,
    archive: *const u8,
    archive_len: u64,
    prefix: *const u8,
    prefix_len: u64,
    overlays: *const u8,
    overlays_len: u64,
    flag_bits: u32,
) -> u64 {
    let (Some(id), Some(archive), Some(prefix), Some(overlays)) = (unsafe {
        (str_arg(id, id_len), str_arg(archive, archive_len), str_arg(prefix, prefix_len), str_arg(overlays, overlays_len))
    }) else {
        return 0;
    };
    let access = ZipAccess::new(PathBuf::from(archive));
    into_handle(Pack::archive_directory(id.to_owned(), access, prefix, &overlay_list(overlays), flags(flag_bits)))
}

/// Opens the vanilla pack. `roots` holds records, highest priority first:
/// a role byte (`R` root resources, `0`/`1` a pack type), a kind byte (`D`
/// directory, `Z` archive), the path, and for archives the entry prefix
/// (empty or ending in `/`). `namespaces` is `\n`-separated.
///
/// # Safety
/// Buffers must be valid for their lengths.
#[no_mangle]
pub unsafe extern "C" fn mattmc_pack_open_vanilla(
    id: *const u8,
    id_len: u64,
    roots: *const u8,
    roots_len: u64,
    namespaces: *const u8,
    namespaces_len: u64,
    flag_bits: u32,
) -> u64 {
    let (Some(id), Some(roots), Some(namespaces)) =
        (unsafe { (str_arg(id, id_len), bytes_arg(roots, roots_len), str_arg(namespaces, namespaces_len)) })
    else {
        return 0;
    };
    let mut archives: Vec<Arc<ZipAccess>> = Vec::new();
    let mut root_roots = Vec::new();
    let mut type_roots: [Vec<Root>; PACK_TYPE_COUNT] = Default::default();
    let mut reader = Reader::new(roots);
    while !reader.done() {
        let (Some(role), Some(kind), Some(path), Some(prefix)) = (reader.u8(), reader.u8(), reader.text(), reader.text()) else {
            return 0;
        };
        let root = match kind {
            b'D' => Root::Dir(PathBuf::from(path)),
            b'Z' => {
                let access = match archives.iter().find(|access| access.path() == std::path::Path::new(path)) {
                    Some(access) => access.clone(),
                    None => {
                        let access = ZipAccess::new(PathBuf::from(path));
                        archives.push(access.clone());
                        access
                    }
                };
                Root::Zip { access, prefix: prefix.to_owned() }
            }
            _ => return 0,
        };
        match role {
            b'R' => root_roots.push(root),
            b'0' => type_roots[0].push(root),
            b'1' => type_roots[1].push(root),
            _ => return 0,
        }
    }
    let namespaces = namespaces.split('\n').filter(|name| !name.is_empty()).map(str::to_owned).collect();
    into_handle(Pack::vanilla(id.to_owned(), root_roots, type_roots, namespaces, flags(flag_bits)))
}

/// Drops a handle.
///
/// # Safety
/// `handle` must be live; it is invalid afterwards.
#[no_mangle]
pub unsafe extern "C" fn mattmc_pack_release(handle: u64) {
    if handle != 0 {
        drop(unsafe { Box::from_raw(handle as *mut Arc<Pack>) });
    }
}

/// `PackResources.close`.
///
/// # Safety
/// `handle` must be live.
#[no_mangle]
pub unsafe extern "C" fn mattmc_pack_close(handle: u64) {
    if let Some(pack) = unsafe { pack(handle) } {
        pack.close();
    }
}

/// `getRootResource(elements)`, the elements `\0`-separated. Returns 1 with
/// the entry, 0 when absent, or the negated length of the
/// `IllegalArgumentException` message (copied when it fits).
///
/// # Safety
/// `handle` live; buffers valid for their lengths; `out` writable.
#[no_mangle]
pub unsafe extern "C" fn mattmc_pack_root_resource(
    handle: u64,
    path: *const u8,
    path_len: u64,
    out: *mut u64,
    error: *mut u8,
    error_capacity: u64,
) -> i64 {
    let (Some(pack), Some(path), false) = (unsafe { pack(handle) }, unsafe { str_arg(path, path_len) }, out.is_null()) else {
        return 0;
    };
    let segments: Vec<&str> = path.split('\0').collect();
    match pack.root_resource(&segments) {
        Ok(Some(entry)) => {
            unsafe { *out = entry.encode() };
            1
        }
        Ok(None) => 0,
        Err(message) => -(unsafe { write_text(&message, error, error_capacity) }).max(1),
    }
}

/// `getResource(type, namespace:path)`: 1 with the entry, else 0.
///
/// # Safety
/// `handle` live; strings valid; `out` writable.
#[no_mangle]
pub unsafe extern "C" fn mattmc_pack_resource(
    handle: u64,
    pack_type: u32,
    namespace: *const u8,
    namespace_len: u64,
    path: *const u8,
    path_len: u64,
    out: *mut u64,
) -> i32 {
    let (Some(pack), Some(namespace), Some(path), false) = (
        unsafe { pack(handle) },
        unsafe { str_arg(namespace, namespace_len) },
        unsafe { str_arg(path, path_len) },
        out.is_null() || pack_type as usize >= PACK_TYPE_COUNT,
    ) else {
        return 0;
    };
    match pack.resource(pack_type as usize, namespace, path) {
        Some(entry) => {
            unsafe { *out = entry.encode() };
            1
        }
        None => 0,
    }
}

/// `getNamespaces(type)`, `\n`-separated, as an owned buffer.
///
/// # Safety
/// `handle` live; outputs writable.
#[no_mangle]
pub unsafe extern "C" fn mattmc_pack_namespaces(handle: u64, pack_type: u32, out_ptr: *mut *mut u8, out_len: *mut u64) -> i32 {
    let Some(pack) = (unsafe { pack(handle) }) else { return -1 };
    if pack_type as usize >= PACK_TYPE_COUNT {
        return -1;
    }
    unsafe { give(pack.namespaces(pack_type as usize).join("\n").into_bytes(), out_ptr, out_len) };
    0
}

/// `listResources(type, namespace, directory)` as an owned buffer of
/// records: path (u32 length + bytes), entry (u64).
///
/// # Safety
/// `handle` live; strings valid; outputs writable.
#[no_mangle]
pub unsafe extern "C" fn mattmc_pack_list(
    handle: u64,
    pack_type: u32,
    namespace: *const u8,
    namespace_len: u64,
    directory: *const u8,
    directory_len: u64,
    out_ptr: *mut *mut u8,
    out_len: *mut u64,
) -> i32 {
    let (Some(pack), Some(namespace), Some(directory)) =
        (unsafe { pack(handle) }, unsafe { str_arg(namespace, namespace_len) }, unsafe { str_arg(directory, directory_len) })
    else {
        return -1;
    };
    if pack_type as usize >= PACK_TYPE_COUNT {
        return -1;
    }
    let mut writer = Writer::default();
    pack.list(pack_type as usize, namespace, directory, &mut |path, entry| {
        writer.text(path);
        writer.u64(entry.encode());
    });
    unsafe { give(writer.bytes, out_ptr, out_len) };
    0
}

/// Reads an entry into an owned buffer. Returns 0, or the negated length of
/// the error message (copied when it fits).
///
/// # Safety
/// `handle` live; buffers valid; outputs writable.
#[no_mangle]
pub unsafe extern "C" fn mattmc_pack_read(
    handle: u64,
    entry: u64,
    relative: *const u8,
    relative_len: u64,
    out_ptr: *mut *mut u8,
    out_len: *mut u64,
    error: *mut u8,
    error_capacity: u64,
) -> i64 {
    let (Some(pack), Some(relative)) = (unsafe { pack(handle) }, unsafe { str_arg(relative, relative_len) }) else {
        return -(unsafe { write_text("invalid pack read", error, error_capacity) });
    };
    match pack.read(EntryRef::decode(entry), relative) {
        Ok(bytes) => {
            unsafe { give(bytes, out_ptr, out_len) };
            0
        }
        Err(failure) => -(unsafe { write_text(&failure.to_string(), error, error_capacity) }).max(1),
    }
}

// -- pack.mcmeta format declarations -----------------------------------------

use super::metadata::{self, Format, Overlay, Range};

fn json_arg(text: &str) -> Result<serde_json::Value, String> {
    serde_json::from_str(text).map_err(|error| error.to_string())
}

/// Decodes a `pack` section's format fields (a JSON object). Writes the
/// range's min major/minor and max major/minor; returns 0, or the negated
/// length of the error (copied when it fits).
///
/// # Safety
/// `section` valid for its length; `out` writable for 4 values; `error`
/// writable for `error_capacity` bytes.
#[no_mangle]
pub unsafe extern "C" fn mattmc_pack_formats_decode(
    section: *const u8,
    section_len: u64,
    pack_type: u32,
    out: *mut i32,
    error: *mut u8,
    error_capacity: u64,
) -> i64 {
    let Some(section) = (unsafe { str_arg(section, section_len) }) else { return -1 };
    match json_arg(section).and_then(|value| metadata::decode_pack_formats(&value, pack_type as usize)) {
        Ok(range) => {
            unsafe {
                *out = range.min.major;
                *out.add(1) = range.min.minor;
                *out.add(2) = range.max.major;
                *out.add(3) = range.max.minor;
            }
            0
        }
        Err(message) => -(unsafe { write_text(&message, error, error_capacity) }).max(1),
    }
}

/// Encodes a range's format fields as a JSON object (owned buffer).
///
/// # Safety
/// Outputs writable.
#[no_mangle]
pub unsafe extern "C" fn mattmc_pack_formats_encode(
    min_major: i32,
    min_minor: i32,
    max_major: i32,
    max_minor: i32,
    pack_type: u32,
    out_ptr: *mut *mut u8,
    out_len: *mut u64,
) {
    let range = Range { min: Format { major: min_major, minor: min_minor }, max: Format { major: max_major, minor: max_minor } };
    let encoded = serde_json::Value::Object(metadata::encode_pack_formats(range, pack_type as usize));
    unsafe { give(encoded.to_string().into_bytes(), out_ptr, out_len) };
}

/// Decodes an `overlays` section. On success writes an owned buffer of
/// warnings (u32 count, texts) then overlays (u32 count; directory text and
/// four i32 range values each) and returns 0; else the negated error length.
///
/// # Safety
/// `section` valid; outputs writable; `error` writable for its capacity.
#[no_mangle]
pub unsafe extern "C" fn mattmc_pack_overlays_decode(
    section: *const u8,
    section_len: u64,
    pack_type: u32,
    out_ptr: *mut *mut u8,
    out_len: *mut u64,
    error: *mut u8,
    error_capacity: u64,
) -> i64 {
    let Some(section) = (unsafe { str_arg(section, section_len) }) else { return -1 };
    match json_arg(section).and_then(|value| metadata::decode_overlays(&value, pack_type as usize)) {
        Ok(decoded) => {
            let mut writer = Writer::default();
            writer.u32(decoded.warnings.len() as u32);
            for warning in &decoded.warnings {
                writer.text(warning);
            }
            writer.u32(decoded.overlays.len() as u32);
            for overlay in &decoded.overlays {
                writer.text(&overlay.directory);
                for value in [overlay.range.min.major, overlay.range.min.minor, overlay.range.max.major, overlay.range.max.minor] {
                    writer.i32(value);
                }
            }
            unsafe { give(writer.bytes, out_ptr, out_len) };
            0
        }
        Err(message) => -(unsafe { write_text(&message, error, error_capacity) }).max(1),
    }
}

/// Encodes overlays (records: directory text, four i32 range values) as an
/// `overlays` section JSON object (owned buffer).
///
/// # Safety
/// `records` valid for its length; outputs writable.
#[no_mangle]
pub unsafe extern "C" fn mattmc_pack_overlays_encode(
    records: *const u8,
    records_len: u64,
    pack_type: u32,
    out_ptr: *mut *mut u8,
    out_len: *mut u64,
) -> i32 {
    let Some(bytes) = (unsafe { bytes_arg(records, records_len) }) else { return -1 };
    let mut reader = Reader::new(bytes);
    let mut overlays = Vec::new();
    while !reader.done() {
        let (Some(directory), Some(a), Some(b), Some(c), Some(d)) =
            (reader.text(), reader.u32(), reader.u32(), reader.u32(), reader.u32())
        else {
            return -1;
        };
        overlays.push(Overlay {
            directory: directory.to_owned(),
            range: Range { min: Format { major: a as i32, minor: b as i32 }, max: Format { major: c as i32, minor: d as i32 } },
        });
    }
    unsafe { give(metadata::encode_overlays(&overlays, pack_type as usize).to_string().into_bytes(), out_ptr, out_len) };
    0
}

/// `PackCompatibility.forVersion` ordinal (`TOO_OLD`, `TOO_NEW`, `UNKNOWN`, `COMPATIBLE`).
#[no_mangle]
pub extern "C" fn mattmc_pack_compatibility(
    min_major: i32,
    min_minor: i32,
    max_major: i32,
    max_minor: i32,
    current_major: i32,
    current_minor: i32,
) -> i32 {
    let range = Range { min: Format { major: min_major, minor: min_minor }, max: Format { major: max_major, minor: max_minor } };
    metadata::compatibility(range, Format { major: current_major, minor: current_minor }) as i32
}

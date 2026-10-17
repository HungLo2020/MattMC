//! Pack discovery and selection (`PackDetector`, `FolderRepositorySource`,
//! `PackRepository.rebuildSelected`).
//!
//! Symbolic links are reported with their target: the allow list is a Java
//! `PathMatcher` (`DirectoryValidator`), so Java decides whether a link may
//! be followed and then asks for the target's kind.

use std::fs;
use std::path::{Path, PathBuf};

/// What a folder entry is (`PackDetector.detectPackResources`).
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) enum Detection {
    /// Not a pack (or gone).
    None,
    /// A directory with a regular `pack.mcmeta`.
    Directory(PathBuf),
    /// A regular `*.zip` file.
    Zip(PathBuf),
    /// A link to validate before following.
    Link { link: PathBuf, target: PathBuf },
    /// A directory to validate (`validateKnownDirectory`) before use.
    KnownDirectory(PathBuf),
}

/// Classifies a path without following a final link.
pub(crate) fn detect(path: &Path) -> Detection {
    let Ok(metadata) = fs::symlink_metadata(path) else { return Detection::None };
    if metadata.file_type().is_symlink() {
        return match fs::read_link(path) {
            Ok(target) => Detection::Link { link: path.to_path_buf(), target },
            Err(_) => Detection::None,
        };
    }
    classify(path, &metadata)
}

/// The kind of a link's target (`Files.readAttributes(target, NOFOLLOW_LINKS)`).
pub(crate) fn detect_target(target: &Path) -> Detection {
    match fs::symlink_metadata(target) {
        Ok(metadata) => classify(target, &metadata),
        Err(_) => Detection::None,
    }
}

fn classify(path: &Path, metadata: &fs::Metadata) -> Detection {
    if metadata.is_dir() {
        // Java validates the directory's contents' links, then requires a
        // regular pack.mcmeta (`Files.isRegularFile` follows links).
        Detection::KnownDirectory(path.to_path_buf())
    } else if metadata.is_file() && path.file_name().is_some_and(|name| name.to_string_lossy().ends_with(".zip")) {
        Detection::Zip(path.to_path_buf())
    } else {
        Detection::None
    }
}

/// After validation: a directory is a pack when it holds a regular `pack.mcmeta`.
pub(crate) fn directory_pack(path: &Path) -> Detection {
    if fs::metadata(path.join("pack.mcmeta")).is_ok_and(|metadata| metadata.is_file()) {
        Detection::Directory(path.to_path_buf())
    } else {
        Detection::None
    }
}

/// `FolderRepositorySource.discoverPacks`: the folder's entries (directory
/// stream order) with their detection. `Err` is the folder listing failure.
pub(crate) fn discover(folder: &Path) -> std::io::Result<Vec<(PathBuf, Detection)>> {
    let mut found = Vec::new();
    for entry in fs::read_dir(folder)? {
        let path = entry?.path();
        let detection = detect(&path);
        found.push((path, detection));
    }
    Ok(found)
}

/// One child of a folder inside an archive.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct ArchiveChild {
    pub(crate) name: String,
    /// Entries exist below it (or an explicit directory entry).
    pub(crate) directory: bool,
    /// It holds a `pack.mcmeta` file entry.
    pub(crate) has_pack_meta: bool,
}

/// A folder inside an archive (the game jar's built-in packs): its direct
/// children in first-seen order. `prefix` is empty or ends with `/`.
pub(crate) fn discover_archive(archive: &super::packs::zip::Archive, prefix: &str) -> Vec<ArchiveChild> {
    let mut found: Vec<ArchiveChild> = Vec::new();
    for entry in archive.entries() {
        let Some(rest) = entry.name.strip_prefix(prefix) else { continue };
        let (name, below) = match rest.split_once('/') {
            Some((name, below)) => (name, Some(below)),
            None => (rest, None),
        };
        if name.is_empty() {
            continue;
        }
        let slot = match found.iter().position(|child| child.name == name) {
            Some(slot) => slot,
            None => {
                found.push(ArchiveChild { name: name.to_owned(), directory: false, has_pack_meta: false });
                found.len() - 1
            }
        };
        if let Some(below) = below {
            found[slot].directory = true;
            if below == "pack.mcmeta" {
                found[slot].has_pack_meta = true;
            }
        }
    }
    found
}

/// `Pack.Position`.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum Position {
    Top,
    Bottom,
}

/// One available pack's `PackSelectionConfig`.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct Selectable {
    pub(crate) id: String,
    pub(crate) required: bool,
    pub(crate) position: Position,
    pub(crate) fixed: bool,
}

/// `Pack.Position.insert`'s index: past the fixed entries pinned to the
/// chosen end. `pinned(i)` says whether entry `i` is fixed at `position`.
pub(crate) fn insert_index(length: usize, position: Position, pinned: impl Fn(usize) -> bool) -> usize {
    match position {
        Position::Bottom => (0..length).find(|&index| !pinned(index)).unwrap_or(length),
        Position::Top => (0..length).rev().find(|&index| !pinned(index)).map_or(0, |index| index + 1),
    }
}

fn insert(list: &mut Vec<usize>, packs: &[Selectable], pack: usize) {
    let position = packs[pack].position;
    let at = insert_index(list.len(), position, |index| packs[list[index]].fixed && packs[list[index]].position == position);
    list.insert(at, pack);
}

/// `PackRepository.rebuildSelected(requested)`: the requested packs that are
/// available, in order (duplicates kept, as Java's list keeps them), then
/// each required pack not yet selected, in `available` order.
pub(crate) fn rebuild_selected(available: &[Selectable], requested: &[&str]) -> Vec<usize> {
    let mut list: Vec<usize> = requested
        .iter()
        .filter_map(|id| available.iter().position(|pack| pack.id == *id))
        .collect();
    for (index, pack) in available.iter().enumerate() {
        if pack.required && !list.contains(&index) {
            insert(&mut list, available, index);
        }
    }
    list
}

#[cfg(test)]
mod tests {
    use super::*;

    fn pack(id: &str, required: bool, position: Position, fixed: bool) -> Selectable {
        Selectable { id: id.into(), required, position, fixed }
    }

    #[test]
    fn required_packs_insert_past_pinned_packs() {
        let available = vec![
            pack("a", false, Position::Top, false),
            pack("pinned_bottom", true, Position::Bottom, true),
            pack("vanilla", true, Position::Bottom, false),
            pack("top_fixed", true, Position::Top, true),
            pack("b", false, Position::Top, false),
        ];
        let ids = |order: Vec<usize>| order.into_iter().map(|index| available[index].id.clone()).collect::<Vec<_>>();
        assert_eq!(vec!["pinned_bottom", "vanilla", "a", "b", "top_fixed"], ids(rebuild_selected(&available, &["a", "b"])));
        assert_eq!(vec!["pinned_bottom", "vanilla", "b", "top_fixed"], ids(rebuild_selected(&available, &["pinned_bottom", "b", "missing"])));
        assert_eq!(vec!["pinned_bottom", "vanilla", "a", "a", "top_fixed"], ids(rebuild_selected(&available, &["a", "a"])));
    }
}

/// C ABI for `FolderRepositorySource`, `PackDetector` and `PackRepository`.
pub mod ffi {
    use std::path::{Path, PathBuf};

    use super::{detect_target, directory_pack, discover, discover_archive, rebuild_selected, Detection, Position, Selectable};
    use crate::assets::ffi_util::{bytes_arg, give, str_arg, write_text, Reader, Writer};
    use crate::assets::packs::zip::Archive;

    const KIND_NONE: u8 = 0;
    const KIND_DIRECTORY: u8 = 1;
    const KIND_ZIP: u8 = 2;
    const KIND_LINK: u8 = 3;
    const KIND_KNOWN_DIRECTORY: u8 = 4;

    fn write_detection(writer: &mut Writer, path: &Path, detection: &Detection) {
        let (kind, target): (u8, PathBuf) = match detection {
            Detection::None => (KIND_NONE, PathBuf::new()),
            Detection::Directory(path) => (KIND_DIRECTORY, path.clone()),
            Detection::Zip(path) => (KIND_ZIP, path.clone()),
            Detection::Link { target, .. } => (KIND_LINK, target.clone()),
            Detection::KnownDirectory(path) => (KIND_KNOWN_DIRECTORY, path.clone()),
        };
        writer.bytes.push(kind);
        writer.text(&path.to_string_lossy());
        writer.text(&target.to_string_lossy());
    }

    /// Lists a folder's entries with their detection: records of kind (u8),
    /// entry path and kind's path (link target, or the pack path). Returns 0,
    /// or the negated length of the listing error (copied when it fits).
    ///
    /// # Safety
    /// `folder` valid; outputs writable; `error` writable for its capacity.
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_pack_discover(
        folder: *const u8,
        folder_len: u64,
        out_ptr: *mut *mut u8,
        out_len: *mut u64,
        error: *mut u8,
        error_capacity: u64,
    ) -> i64 {
        let Some(folder) = (unsafe { str_arg(folder, folder_len) }) else { return -1 };
        match discover(Path::new(folder)) {
            Ok(entries) => {
                let mut writer = Writer::default();
                for (path, detection) in &entries {
                    write_detection(&mut writer, path, detection);
                }
                unsafe { give(writer.bytes, out_ptr, out_len) };
                0
            }
            Err(failure) => -(unsafe { write_text(&failure.to_string(), error, error_capacity) }).max(1),
        }
    }

    /// One path's detection as a record like `mattmc_pack_discover`'s:
    /// `mode` 0 detects (reporting links), 1 classifies a validated link's
    /// target, 2 checks a validated directory for a `pack.mcmeta`.
    ///
    /// # Safety
    /// `path` valid; outputs writable.
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_pack_detect(path: *const u8, path_len: u64, mode: i32, out_ptr: *mut *mut u8, out_len: *mut u64) -> i32 {
        let Some(path) = (unsafe { str_arg(path, path_len) }) else { return -1 };
        let path = Path::new(path);
        let detection = match mode {
            0 => super::detect(path),
            1 => detect_target(path),
            _ => directory_pack(path),
        };
        let mut writer = Writer::default();
        write_detection(&mut writer, path, &detection);
        unsafe { give(writer.bytes, out_ptr, out_len) };
        0
    }

    /// A folder inside an archive: records of name (text) and flags (u8:
    /// bit 0 directory, bit 1 has `pack.mcmeta`). Returns 0, or -1 when the
    /// archive cannot be opened.
    ///
    /// # Safety
    /// Strings valid; outputs writable.
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_pack_discover_archive(
        archive: *const u8,
        archive_len: u64,
        prefix: *const u8,
        prefix_len: u64,
        out_ptr: *mut *mut u8,
        out_len: *mut u64,
    ) -> i32 {
        let (Some(archive), Some(prefix)) = (unsafe { str_arg(archive, archive_len) }, unsafe { str_arg(prefix, prefix_len) }) else {
            return -1;
        };
        let Ok(archive) = Archive::open(Path::new(archive)) else { return -1 };
        let mut writer = Writer::default();
        for child in discover_archive(&archive, prefix) {
            writer.text(&child.name);
            writer.bytes.push(u8::from(child.directory) | (u8::from(child.has_pack_meta) << 1));
        }
        unsafe { give(writer.bytes, out_ptr, out_len) };
        0
    }

    /// `Pack.Position.insert(list, pack, config, opposite)`'s index: `configs`
    /// holds each list entry's fixed (u8) and position (u8: 0 top, 1 bottom).
    ///
    /// # Safety
    /// `configs` valid for `count * 2` bytes.
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_pack_insert_index(configs: *const u8, count: u64, position: u32, opposite: i32) -> i64 {
        let Some(configs) = (unsafe { bytes_arg(configs, count * 2) }) else { return -1 };
        let own = if position == 0 { Position::Top } else { Position::Bottom };
        // Java scans from the opposite end when asked, but still treats as
        // pinned the entries fixed at its own position.
        let scan = match (own, opposite != 0) {
            (Position::Top, true) => Position::Bottom,
            (Position::Bottom, true) => Position::Top,
            (own, false) => own,
        };
        let wanted = position as u8;
        super::insert_index(count as usize, scan, |index| configs[index * 2] != 0 && configs[index * 2 + 1] == wanted) as i64
    }

    /// `PackRepository.rebuildSelected`: `available` records (id text, then
    /// required, position (0 top, 1 bottom) and fixed bytes) and `requested`
    /// ids (`\n`-separated); writes the selection as u32 indices into
    /// `available`.
    ///
    /// # Safety
    /// Buffers valid; outputs writable.
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_pack_rebuild_selected(
        available: *const u8,
        available_len: u64,
        requested: *const u8,
        requested_len: u64,
        out_ptr: *mut *mut u8,
        out_len: *mut u64,
    ) -> i32 {
        let (Some(available), Some(requested)) = (unsafe { bytes_arg(available, available_len) }, unsafe { str_arg(requested, requested_len) }) else {
            return -1;
        };
        let mut reader = Reader::new(available);
        let mut packs = Vec::new();
        while !reader.done() {
            let (Some(id), Some(required), Some(position), Some(fixed)) = (reader.text(), reader.u8(), reader.u8(), reader.u8()) else {
                return -1;
            };
            packs.push(Selectable {
                id: id.to_owned(),
                required: required != 0,
                position: if position == 0 { Position::Top } else { Position::Bottom },
                fixed: fixed != 0,
            });
        }
        let requested: Vec<&str> = if requested.is_empty() { Vec::new() } else { requested.split('\n').collect() };
        let mut writer = Writer::default();
        for index in rebuild_selected(&packs, &requested) {
            writer.u32(index as u32);
        }
        unsafe { give(writer.bytes, out_ptr, out_len) };
        0
    }
}

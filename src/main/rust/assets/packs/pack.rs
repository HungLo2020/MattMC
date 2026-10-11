//! One opened pack (`PackResources`): a directory pack, a ZIP pack, or the
//! vanilla pack's multiple roots, each with any overlay directories.
//!
//! A pack is a table of [`Root`]s with two search orders: the roots used
//! for root resources (`pack.mcmeta`, `pack.png`) and, per pack type, the
//! roots holding `<namespace>/<path>` resources, highest priority first.
//! Composite (overlay) packs search their overlays in reverse declaration
//! order before the primary root, as `CompositePackResources` does.

use std::collections::BTreeSet;
use std::fs;
use std::io;
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};

use super::zip::Archive;
use crate::core::id;
use crate::core::java_log;

/// `PackType` ordinals and their directories.
pub(crate) const PACK_TYPE_COUNT: usize = 2;
pub(crate) const PACK_TYPE_DIRECTORIES: [&str; PACK_TYPE_COUNT] = ["assets", "data"];

/// A located resource inside a pack: which root, and for ZIP roots which
/// entry. Directory files are re-resolved from the root and relative path.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub(crate) struct EntryRef {
    pub(crate) root: u16,
    pub(crate) zip_entry: Option<u32>,
}

const ENTRY_HAS_ZIP: u64 = 1 << 48;

impl EntryRef {
    /// The ABI form: root in bits 32..48, bit 48 for a ZIP entry in bits 0..32.
    pub(crate) fn encode(self) -> u64 {
        (u64::from(self.root) << 32)
            | match self.zip_entry {
                Some(entry) => ENTRY_HAS_ZIP | u64::from(entry),
                None => 0,
            }
    }

    pub(crate) fn decode(value: u64) -> EntryRef {
        EntryRef {
            root: ((value >> 32) & 0xFFFF) as u16,
            zip_entry: (value & ENTRY_HAS_ZIP != 0).then_some(value as u32),
        }
    }
}

/// A shared, lazily opened archive (`FilePackResources.SharedZipFileAccess`):
/// a failed open is remembered; closing drops the file and a later access
/// reopens it, as Java does.
#[derive(Debug)]
pub(crate) struct ZipAccess {
    path: PathBuf,
    state: Mutex<ZipState>,
}

#[derive(Debug)]
enum ZipState {
    Closed,
    Open(Arc<Archive>),
    Failed,
}

impl ZipAccess {
    pub(crate) fn new(path: PathBuf) -> Arc<ZipAccess> {
        Arc::new(ZipAccess { path, state: Mutex::new(ZipState::Closed) })
    }

    pub(crate) fn path(&self) -> &Path {
        &self.path
    }

    pub(crate) fn archive(&self) -> Option<Arc<Archive>> {
        let mut state = self.state.lock().unwrap_or_else(|poisoned| poisoned.into_inner());
        match &*state {
            ZipState::Open(archive) => return Some(archive.clone()),
            ZipState::Failed => return None,
            ZipState::Closed => {}
        }
        match Archive::open(&self.path) {
            Ok(archive) => {
                let archive = Arc::new(archive);
                *state = ZipState::Open(archive.clone());
                Some(archive)
            }
            Err(error) => {
                java_log::error(&format!("Failed to open pack {}: {error}", self.path.display()));
                *state = ZipState::Failed;
                None
            }
        }
    }

    pub(crate) fn close(&self) {
        let mut state = self.state.lock().unwrap_or_else(|poisoned| poisoned.into_inner());
        if matches!(*state, ZipState::Open(_)) {
            *state = ZipState::Closed;
        }
    }
}

/// A directory tree: on disk, or inside an archive under a name prefix.
#[derive(Clone, Debug)]
pub(crate) enum Root {
    Dir(PathBuf),
    /// `prefix` is empty or ends with `/`.
    Zip { access: Arc<ZipAccess>, prefix: String },
}

impl Root {
    pub(crate) fn child(&self, name: &str) -> Root {
        match self {
            Root::Dir(path) => Root::Dir(path.join(name)),
            Root::Zip { access, prefix } => Root::Zip { access: access.clone(), prefix: format!("{prefix}{name}/") },
        }
    }

    fn display(&self) -> String {
        match self {
            Root::Dir(path) => path.display().to_string(),
            Root::Zip { access, prefix } => format!("{}!/{prefix}", access.path().display()),
        }
    }
}

/// `Path.resolve` of `/`-joined segments.
fn join_segments(base: &Path, relative: &str) -> PathBuf {
    let mut path = base.to_path_buf();
    for segment in relative.split('/') {
        path.push(segment);
    }
    path
}

/// `Files.exists` (follows links; any error is "absent").
fn path_exists(path: &Path) -> bool {
    fs::metadata(path).is_ok()
}

/// One listed file: its path relative to the listing base and its entry.
pub(crate) type ListOutput<'a> = dyn FnMut(&str, EntryRef) + 'a;

#[derive(Clone, Copy, Debug, Default)]
pub(crate) struct PackFlags {
    /// `SharedConstants.IS_RUNNING_IN_IDE`: listings skip `.DS_Store`.
    pub(crate) running_in_ide: bool,
    /// `SharedConstants.DEBUG_VALIDATE_RESOURCE_PATH_CASE`.
    pub(crate) validate_path_case: bool,
}

#[derive(Debug)]
pub(crate) struct Pack {
    pub(crate) id: String,
    roots: Vec<Root>,
    root_order: Vec<u16>,
    type_order: [Vec<u16>; PACK_TYPE_COUNT],
    /// Vanilla's exposed namespaces; otherwise discovered per type.
    fixed_namespaces: Option<Vec<String>>,
    /// Directory and vanilla packs validate resource paths; ZIP packs look
    /// names up literally.
    validates_paths: bool,
    archives: Vec<Arc<ZipAccess>>,
    flags: PackFlags,
}

impl Pack {
    fn push_root(roots: &mut Vec<Root>, root: Root) -> u16 {
        roots.push(root);
        (roots.len() - 1) as u16
    }

    /// `PathResourcesSupplier.openFull` (or `openPrimary` without overlays).
    pub(crate) fn directory(id: String, path: PathBuf, overlays: &[String], flags: PackFlags) -> Pack {
        let primary = Root::Dir(path.clone());
        let layers: Vec<Root> = std::iter::once(primary.clone())
            .chain(overlays.iter().map(|overlay| Root::Dir(path.join(overlay))))
            .collect();
        Pack::layered(id, primary, layers, true, Vec::new(), flags)
    }

    /// `FileResourcesSupplier.openFull` over one shared archive.
    pub(crate) fn zip(id: String, access: Arc<ZipAccess>, overlays: &[String], flags: PackFlags) -> Pack {
        let primary = Root::Zip { access: access.clone(), prefix: String::new() };
        let layers: Vec<Root> = std::iter::once(primary.clone())
            .chain(overlays.iter().map(|overlay| Root::Zip { access: access.clone(), prefix: format!("{overlay}/") }))
            .collect();
        Pack::layered(id, primary, layers, false, vec![access], flags)
    }

    /// A directory pack whose directory lies inside an archive (the game
    /// jar's built-in data packs): directory-pack rules over archive entries.
    /// `prefix` is empty or ends with `/`.
    pub(crate) fn archive_directory(id: String, access: Arc<ZipAccess>, prefix: &str, overlays: &[String], flags: PackFlags) -> Pack {
        let primary = Root::Zip { access: access.clone(), prefix: prefix.to_owned() };
        let layers: Vec<Root> = std::iter::once(primary.clone()).chain(overlays.iter().map(|overlay| primary.child(overlay))).collect();
        Pack::layered(id, primary, layers, true, vec![access], flags)
    }

    /// A primary layer and its overlays: overlays search before the primary,
    /// the last declared first.
    fn layered(id: String, primary: Root, layers: Vec<Root>, validates_paths: bool, archives: Vec<Arc<ZipAccess>>, flags: PackFlags) -> Pack {
        let mut roots = Vec::new();
        let root_order = vec![Pack::push_root(&mut roots, primary)];
        let mut type_order: [Vec<u16>; PACK_TYPE_COUNT] = Default::default();
        for layer in layers.iter().skip(1).rev().chain(layers.first()) {
            for (pack_type, directory) in PACK_TYPE_DIRECTORIES.iter().enumerate() {
                type_order[pack_type].push(Pack::push_root(&mut roots, layer.child(directory)));
            }
        }
        Pack { id, roots, root_order, type_order, fixed_namespaces: None, validates_paths, archives, flags }
    }

    /// `VanillaPackResources`: root resource roots and per-type roots, each
    /// highest priority first, and the exposed namespaces.
    pub(crate) fn vanilla(
        id: String,
        root_roots: Vec<Root>,
        type_roots: [Vec<Root>; PACK_TYPE_COUNT],
        namespaces: Vec<String>,
        flags: PackFlags,
    ) -> Pack {
        let mut roots = Vec::new();
        let mut archives: Vec<Arc<ZipAccess>> = Vec::new();
        let mut note_archive = |root: &Root| {
            if let Root::Zip { access, .. } = root {
                if !archives.iter().any(|known| Arc::ptr_eq(known, access)) {
                    archives.push(access.clone());
                }
            }
        };
        let root_order = root_roots
            .into_iter()
            .map(|root| {
                note_archive(&root);
                Pack::push_root(&mut roots, root)
            })
            .collect();
        let type_order = type_roots.map(|list| {
            list.into_iter()
                .map(|root| {
                    note_archive(&root);
                    Pack::push_root(&mut roots, root)
                })
                .collect()
        });
        Pack { id, roots, root_order, type_order, fixed_namespaces: Some(namespaces), validates_paths: true, archives, flags }
    }

    /// `PackResources.close`: releases archive files (a later read reopens).
    pub(crate) fn close(&self) {
        for archive in &self.archives {
            archive.close();
        }
    }

    /// `PathPackResources.validatePath`: the real path ends with the path.
    fn case_valid(&self, path: &Path) -> bool {
        if !self.flags.validate_path_case {
            return true;
        }
        match fs::canonicalize(path) {
            Ok(real) => real.ends_with(path),
            Err(error) => {
                java_log::warn(&format!("Failed to resolve real path for {}: {error}", path.display()));
                false
            }
        }
    }

    /// Looks a relative path up in one root.
    fn find_in(&self, root_index: u16, relative: &str) -> Option<EntryRef> {
        match &self.roots[root_index as usize] {
            Root::Dir(base) => {
                let path = join_segments(base, relative);
                (path_exists(&path) && self.case_valid(&path)).then_some(EntryRef { root: root_index, zip_entry: None })
            }
            Root::Zip { access, prefix } => {
                let archive = access.archive()?;
                let entry = archive.find(&format!("{prefix}{relative}"))?;
                Some(EntryRef { root: root_index, zip_entry: Some(entry) })
            }
        }
    }

    /// `getRootResource(segments)`; `Err` is Java's `IllegalArgumentException`.
    pub(crate) fn root_resource(&self, segments: &[&str]) -> Result<Option<EntryRef>, String> {
        if self.validates_paths {
            id::validate_root_path(segments)?;
        }
        let relative = segments.join("/");
        Ok(self.root_order.iter().find_map(|&root| self.find_in(root, &relative)))
    }

    /// `getResource(type, namespace:path)`.
    pub(crate) fn resource(&self, pack_type: usize, namespace: &str, path: &str) -> Option<EntryRef> {
        if self.validates_paths {
            if let Err(error) = id::decompose_path(path) {
                java_log::error(&format!("Invalid path {namespace}:{path}: {error}"));
                return None;
            }
        }
        let relative = format!("{namespace}/{path}");
        self.type_order[pack_type].iter().find_map(|&root| self.find_in(root, &relative))
    }

    /// `listResources(type, namespace, directory, output)`: every regular file
    /// under `namespace/directory`, each location once (highest priority
    /// root wins). `output` receives the path relative to the namespace.
    pub(crate) fn list(&self, pack_type: usize, namespace: &str, directory: &str, output: &mut ListOutput<'_>) {
        let segments = if self.validates_paths {
            match id::decompose_path(directory) {
                Ok(segments) => segments,
                Err(error) => {
                    java_log::error(&format!("Invalid path {directory}: {error}"));
                    return;
                }
            }
        } else {
            Vec::new()
        };
        let roots = &self.type_order[pack_type];
        if roots.len() == 1 {
            self.list_root(roots[0], namespace, directory, &segments, output);
            return;
        }
        let mut seen = BTreeSet::new();
        for &root in roots {
            self.list_root(root, namespace, directory, &segments, &mut |path, entry| {
                if seen.insert(path.to_owned()) {
                    output(path, entry);
                }
            });
        }
    }

    fn list_root(&self, root_index: u16, namespace: &str, directory: &str, segments: &[&str], output: &mut ListOutput<'_>) {
        match &self.roots[root_index as usize] {
            Root::Dir(base) => {
                let namespace_dir = base.join(namespace);
                let start = segments.iter().fold(namespace_dir.clone(), |path, segment| path.join(segment));
                self.walk(root_index, namespace, &namespace_dir, &start, output);
            }
            Root::Zip { access, prefix } => {
                let Some(archive) = access.archive() else { return };
                let base = format!("{prefix}{namespace}/");
                let wanted = format!("{base}{directory}/");
                for (index, entry) in archive.entries().iter().enumerate() {
                    if entry.is_directory() || !entry.name.starts_with(&wanted) {
                        continue;
                    }
                    let path = &entry.name[base.len()..];
                    if id::is_valid_namespace(namespace) && id::is_valid_path(path) {
                        output(path, EntryRef { root: root_index, zip_entry: Some(index as u32) });
                    } else if self.validates_paths {
                        java_log::error(&format!("Invalid path in pack: {namespace}:{path}, ignoring"));
                    } else {
                        java_log::warn(&format!("Invalid path in datapack: {namespace}:{path}, ignoring"));
                    }
                }
            }
        }
    }

    /// `PathPackResources.listPath`: regular files (links are not followed),
    /// relative to `namespace_dir`, joined with `/`.
    fn walk(&self, root_index: u16, namespace: &str, namespace_dir: &Path, start: &Path, output: &mut ListOutput<'_>) {
        let start_type = match fs::symlink_metadata(start) {
            Ok(metadata) => metadata.file_type(),
            Err(_) => return,
        };
        let mut emit = |path: &Path| {
            let Ok(relative) = path.strip_prefix(namespace_dir) else { return };
            let relative: Vec<String> = relative.iter().map(|part| part.to_string_lossy().into_owned()).collect();
            let relative = relative.join("/");
            if id::is_valid_namespace(namespace) && id::is_valid_path(&relative) {
                output(&relative, EntryRef { root: root_index, zip_entry: None });
            } else {
                java_log::error(&format!("Invalid path in pack: {namespace}:{relative}, ignoring"));
            }
        };
        if start_type.is_file() {
            emit(start);
            return;
        }
        if !start_type.is_dir() {
            return;
        }
        let mut stack = vec![start.to_path_buf()];
        while let Some(directory) = stack.pop() {
            let entries = match fs::read_dir(&directory) {
                Ok(entries) => entries,
                Err(error) => {
                    java_log::error(&format!("Failed to list path {}: {error}", directory.display()));
                    continue;
                }
            };
            let mut files = Vec::new();
            for entry in entries.flatten() {
                let Ok(file_type) = entry.file_type() else { continue };
                if file_type.is_dir() {
                    stack.push(entry.path());
                } else if file_type.is_file() {
                    if self.flags.running_in_ide && entry.file_name().eq_ignore_ascii_case(".ds_store") {
                        continue;
                    }
                    files.push(entry.path());
                }
            }
            for file in files {
                emit(&file);
            }
        }
    }

    /// `getNamespaces(type)`, in discovery order without duplicates.
    pub(crate) fn namespaces(&self, pack_type: usize) -> Vec<String> {
        if let Some(fixed) = &self.fixed_namespaces {
            return fixed.clone();
        }
        let mut found: Vec<String> = Vec::new();
        for &root in &self.type_order[pack_type] {
            let root = &self.roots[root as usize];
            let mut add = |name: &str| {
                if id::is_valid_namespace(name) {
                    if !found.iter().any(|known| known == name) {
                        found.push(name.to_owned());
                    }
                } else {
                    java_log::warn(&format!(
                        "Non [a-z0-9_.-] character in namespace {name} in pack {}, ignoring",
                        root.display()
                    ));
                }
            };
            match root {
                Root::Dir(path) => {
                    let Ok(entries) = fs::read_dir(path) else { continue };
                    for entry in entries.flatten() {
                        add(&entry.file_name().to_string_lossy());
                    }
                }
                Root::Zip { access, prefix } => {
                    let Some(archive) = access.archive() else { continue };
                    for entry in archive.entries() {
                        let Some(rest) = entry.name.strip_prefix(prefix.as_str()) else { continue };
                        let name = rest.split('/').next().unwrap_or("");
                        if !name.is_empty() {
                            add(name);
                        }
                    }
                }
            }
        }
        found
    }

    /// Reads a located entry; `relative` is its path under the entry's root.
    pub(crate) fn read(&self, entry: EntryRef, relative: &str) -> io::Result<Vec<u8>> {
        let root = self
            .roots
            .get(entry.root as usize)
            .ok_or_else(|| io::Error::new(io::ErrorKind::NotFound, "no such pack root"))?;
        match (root, entry.zip_entry) {
            (Root::Dir(base), None) => fs::read(join_segments(base, relative)),
            (Root::Zip { access, .. }, Some(index)) => {
                let archive = access
                    .archive()
                    .ok_or_else(|| io::Error::other(format!("Failed to open pack {}", access.path().display())))?;
                archive.read(index)
            }
            _ => Err(io::Error::new(io::ErrorKind::InvalidInput, "mismatched pack entry")),
        }
    }
}

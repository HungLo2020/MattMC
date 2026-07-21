use std::collections::{BTreeMap, BTreeSet};
use std::path::Path;
use std::time::Instant;

use super::errors::PackResult;
use super::index::{
    IndexedPack, NativePack, PackOpenStats, ResourceEntry, ResourceKey, ResourceSource,
};
use super::resource_path::{
    is_resource_location_path, is_valid_namespace, relative_slash_path, PACK_TYPES,
};

pub fn open(root: &Path) -> PackResult<(NativePack, PackOpenStats)> {
    let started = Instant::now();
    let mut index = IndexedPack {
        root: root.to_path_buf(),
        entries: BTreeMap::new(),
        root_entries: BTreeMap::new(),
        namespaces: BTreeMap::new(),
        counters: Default::default(),
    };
    rebuild(&mut index)?;
    index.finish_indexing();
    let stats = index.open_stats(started.elapsed());
    Ok((NativePack::Directory(index), stats))
}

pub fn rebuild(index: &mut IndexedPack) -> PackResult<()> {
    let counters = index.counters;
    index.entries.clear();
    index.root_entries.clear();
    index.namespaces.clear();
    let root = index.root.clone();
    index_root_files(&root, index)?;
    for pack_type in PACK_TYPES {
        index_pack_type(&root, pack_type, index)?;
    }
    index.finish_indexing();
    index.counters.list_ops = counters.list_ops;
    index.counters.exists_ops = counters.exists_ops;
    index.counters.read_ops = counters.read_ops;
    index.counters.bytes_returned = counters.bytes_returned;
    index.counters.invalid_path_rejections = counters.invalid_path_rejections;
    index.counters.stale_handle_attempts = counters.stale_handle_attempts;
    Ok(())
}

fn index_root_files(root: &Path, index: &mut IndexedPack) -> PackResult<()> {
    if !root.is_dir() {
        return Ok(());
    }
    for entry in std::fs::read_dir(root)? {
        let entry = entry?;
        let path = entry.path();
        if path.symlink_metadata()?.file_type().is_file() {
            if let Some(name) = path
                .file_name()
                .map(|name| name.to_string_lossy().to_string())
            {
                if is_resource_location_path(&name) {
                    index.root_entries.insert(
                        name,
                        ResourceEntry {
                            len: path.metadata()?.len(),
                            source: ResourceSource::Directory(path),
                        },
                    );
                }
            }
        }
    }
    Ok(())
}

fn index_pack_type(root: &Path, pack_type: &str, index: &mut IndexedPack) -> PackResult<()> {
    let type_root = root.join(pack_type);
    if !type_root.is_dir() {
        return Ok(());
    }
    for namespace in std::fs::read_dir(&type_root)? {
        let namespace = namespace?;
        if !namespace.path().is_dir() {
            continue;
        }
        let namespace_name = namespace.file_name().to_string_lossy().to_string();
        if !is_valid_namespace(&namespace_name) {
            continue;
        }
        index
            .namespaces
            .entry(pack_type.to_string())
            .or_insert_with(BTreeSet::new)
            .insert(namespace_name.clone());
        walk_namespace(
            pack_type,
            &namespace_name,
            &namespace.path(),
            &namespace.path(),
            index,
        )?;
    }
    Ok(())
}

fn walk_namespace(
    pack_type: &str,
    namespace: &str,
    namespace_root: &Path,
    path: &Path,
    index: &mut IndexedPack,
) -> PackResult<()> {
    for entry in std::fs::read_dir(path)? {
        let entry = entry?;
        let path = entry.path();
        let file_type = path.symlink_metadata()?.file_type();
        if file_type.is_dir() {
            walk_namespace(pack_type, namespace, namespace_root, &path, index)?;
        } else if file_type.is_file() {
            let Ok(relative) = path.strip_prefix(namespace_root) else {
                continue;
            };
            let Some(resource_path) = relative_slash_path(relative) else {
                continue;
            };
            if !is_resource_location_path(&resource_path) {
                continue;
            }
            index.entries.insert(
                ResourceKey {
                    pack_type: pack_type.to_string(),
                    namespace: namespace.to_string(),
                    path: resource_path,
                },
                ResourceEntry {
                    len: path.metadata()?.len(),
                    source: ResourceSource::Directory(path),
                },
            );
        }
    }
    Ok(())
}

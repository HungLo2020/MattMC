use std::collections::{BTreeMap, BTreeSet};
use std::fs::File;
use std::path::Path;
use std::time::Instant;

use super::errors::PackResult;
use super::index::{
    IndexedPack, NativePack, PackOpenStats, ResourceEntry, ResourceKey, ResourceSource, ZipPack,
};
use super::resource_path::{
    is_resource_location_path, is_valid_namespace, safe_zip_path, PACK_TYPES,
};

pub fn open(path: &Path, prefix: &str) -> PackResult<(NativePack, PackOpenStats)> {
    let started = Instant::now();
    let file = File::open(path)?;
    let mut archive = zip::ZipArchive::new(file)?;
    let mut index = IndexedPack {
        root: path.to_path_buf(),
        entries: BTreeMap::new(),
        root_entries: BTreeMap::new(),
        namespaces: BTreeMap::new(),
        counters: Default::default(),
    };
    let prefix = normalize_prefix(prefix)?;
    for i in 0..archive.len() {
        let file = archive.by_index(i)?;
        if file.is_dir() {
            continue;
        }
        let archive_name = file.name().replace('\\', "/");
        let Some(trimmed) = strip_prefix(&archive_name, &prefix) else {
            continue;
        };
        if let Some((pack_type, namespace, resource_path)) = split_resource_path(trimmed) {
            if safe_zip_path(resource_path).is_ok() {
                index
                    .namespaces
                    .entry(pack_type.to_string())
                    .or_insert_with(BTreeSet::new)
                    .insert(namespace.to_string());
                index
                    .entries
                    .entry(ResourceKey {
                        pack_type: pack_type.to_string(),
                        namespace: namespace.to_string(),
                        path: resource_path.to_string(),
                    })
                    .or_insert(ResourceEntry {
                        len: file.size(),
                        source: ResourceSource::Zip {
                            archive_name: archive_name.clone(),
                        },
                    });
            }
        } else if safe_zip_path(trimmed).is_ok() {
            index
                .root_entries
                .entry(trimmed.to_string())
                .or_insert(ResourceEntry {
                    len: file.size(),
                    source: ResourceSource::Zip {
                        archive_name: archive_name.clone(),
                    },
                });
        }
    }
    index.finish_indexing();
    let stats = index.open_stats(started.elapsed());
    Ok((NativePack::Zip(ZipPack { index, archive }), stats))
}

fn normalize_prefix(prefix: &str) -> PackResult<String> {
    if prefix.is_empty() {
        return Ok(String::new());
    }
    let prefix = prefix.trim_matches('/');
    safe_zip_path(prefix)?;
    Ok(prefix.to_string())
}

fn strip_prefix<'a>(name: &'a str, prefix: &str) -> Option<&'a str> {
    if prefix.is_empty() {
        Some(name)
    } else {
        name.strip_prefix(prefix)
            .and_then(|rest| rest.strip_prefix('/'))
    }
}

fn split_resource_path(path: &str) -> Option<(&str, &str, &str)> {
    for pack_type in PACK_TYPES {
        let Some(rest) = path
            .strip_prefix(pack_type)
            .and_then(|rest| rest.strip_prefix('/'))
        else {
            continue;
        };
        let (namespace, resource_path) = rest.split_once('/')?;
        if is_valid_namespace(namespace) && is_resource_location_path(resource_path) {
            return Some((pack_type, namespace, resource_path));
        }
    }
    None
}

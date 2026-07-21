use std::collections::{BTreeMap, BTreeSet};
use std::fs::File;
use std::io::Read;
use std::path::PathBuf;
use std::time::Duration;

use super::directory;
use super::errors::{PackError, PackResult};
use super::resource_path::{
    join_strict, safe_zip_path, validate_namespace, validate_pack_type,
    validate_resource_location_path,
};

#[derive(Debug)]
pub enum NativePack {
    Directory(IndexedPack),
    Zip(ZipPack),
}

#[derive(Debug)]
pub struct ZipPack {
    pub index: IndexedPack,
    pub archive: zip::ZipArchive<File>,
}

#[derive(Clone, Debug, Default)]
pub struct IndexedPack {
    pub root: PathBuf,
    pub entries: BTreeMap<ResourceKey, ResourceEntry>,
    pub root_entries: BTreeMap<String, ResourceEntry>,
    pub namespaces: BTreeMap<String, BTreeSet<String>>,
    pub counters: PackCounters,
}

#[derive(Clone, Debug)]
pub struct ResourceKey {
    pub pack_type: String,
    pub namespace: String,
    pub path: String,
}

impl PartialEq for ResourceKey {
    fn eq(&self, other: &Self) -> bool {
        self.pack_type == other.pack_type
            && self.namespace == other.namespace
            && self.path == other.path
    }
}

impl Eq for ResourceKey {}

impl PartialOrd for ResourceKey {
    fn partial_cmp(&self, other: &Self) -> Option<std::cmp::Ordering> {
        Some(self.cmp(other))
    }
}

impl Ord for ResourceKey {
    fn cmp(&self, other: &Self) -> std::cmp::Ordering {
        (&self.pack_type, &self.namespace, &self.path).cmp(&(
            &other.pack_type,
            &other.namespace,
            &other.path,
        ))
    }
}

#[derive(Clone, Debug)]
pub struct ResourceEntry {
    pub source: ResourceSource,
    pub len: u64,
}

#[derive(Clone, Debug)]
pub enum ResourceSource {
    Directory(PathBuf),
    Zip { archive_name: String },
}

#[derive(Clone, Copy, Debug, Default)]
pub struct PackOpenStats {
    pub entries_indexed: u64,
    pub namespaces_indexed: u64,
    pub index_nanos: u64,
}

#[derive(Clone, Copy, Debug, Default)]
pub struct PackCounters {
    pub list_ops: u64,
    pub exists_ops: u64,
    pub read_ops: u64,
    pub bytes_returned: u64,
    pub invalid_path_rejections: u64,
    pub stale_handle_attempts: u64,
    pub entries_indexed: u64,
    pub namespaces_indexed: u64,
}

impl IndexedPack {
    pub fn open_stats(&self, duration: Duration) -> PackOpenStats {
        PackOpenStats {
            entries_indexed: self.entries.len() as u64 + self.root_entries.len() as u64,
            namespaces_indexed: self.namespaces.values().map(|set| set.len() as u64).sum(),
            index_nanos: duration.as_nanos().min(u128::from(u64::MAX)) as u64,
        }
    }

    pub fn finish_indexing(&mut self) {
        self.counters.entries_indexed = self.entries.len() as u64 + self.root_entries.len() as u64;
        self.counters.namespaces_indexed =
            self.namespaces.values().map(|set| set.len() as u64).sum();
    }
}

impl NativePack {
    pub fn list_namespaces(&mut self, pack_type: &str) -> PackResult<Vec<String>> {
        let pack_type = validate_pack_type(pack_type)?;
        self.refresh_directory_index()?;
        let index = self.index_mut();
        index.counters.list_ops += 1;
        Ok(index
            .namespaces
            .get(pack_type)
            .map(|set| set.iter().cloned().collect())
            .unwrap_or_default())
    }

    pub fn list_resources(
        &mut self,
        pack_type: &str,
        namespace: &str,
        prefix: &str,
    ) -> PackResult<Vec<String>> {
        let pack_type = validate_pack_type(pack_type)?;
        validate_namespace(namespace)?;
        self.refresh_directory_index()?;
        if prefix.is_empty() {
            self.index_mut().counters.list_ops += 1;
            return Ok(Vec::new());
        }
        validate_resource_location_path(prefix)?;
        let index = self.index_mut();
        index.counters.list_ops += 1;
        Ok(index
            .entries
            .keys()
            .filter(|key| {
                key.pack_type == pack_type
                    && key.namespace == namespace
                    && key.path.starts_with(prefix)
            })
            .map(|key| key.path.clone())
            .collect())
    }

    pub fn exists(&mut self, pack_type: &str, namespace: &str, path: &str) -> PackResult<bool> {
        let pack_type = validate_pack_type(pack_type)?;
        validate_namespace(namespace)?;
        self.refresh_directory_index()?;
        if matches!(self, NativePack::Directory(_)) {
            if let Err(error) = join_strict(&PathBuf::new(), path) {
                self.index_mut().counters.invalid_path_rejections += 1;
                return Err(error);
            }
        } else if let Err(error) = safe_zip_path(path) {
            self.index_mut().counters.invalid_path_rejections += 1;
            return Err(error);
        }
        let index = self.index_mut();
        index.counters.exists_ops += 1;
        Ok(index.entries.contains_key(&ResourceKey {
            pack_type: pack_type.to_string(),
            namespace: namespace.to_string(),
            path: path.to_string(),
        }))
    }

    pub fn read_resource(
        &mut self,
        pack_type: &str,
        namespace: &str,
        path: &str,
    ) -> PackResult<Option<Vec<u8>>> {
        if !self.exists(pack_type, namespace, path)? {
            return Ok(None);
        }
        let key = ResourceKey {
            pack_type: pack_type.to_string(),
            namespace: namespace.to_string(),
            path: path.to_string(),
        };
        self.read_entry(&key)
    }

    pub fn read_root_resource(&mut self, path: &str) -> PackResult<Option<Vec<u8>>> {
        self.refresh_directory_index()?;
        if matches!(self, NativePack::Directory(_)) {
            if let Err(error) = join_strict(&PathBuf::new(), path) {
                self.index_mut().counters.invalid_path_rejections += 1;
                return Err(error);
            }
        } else if let Err(error) = safe_zip_path(path) {
            self.index_mut().counters.invalid_path_rejections += 1;
            return Err(error);
        }
        let entry = self.index().root_entries.get(path).cloned();
        match entry {
            Some(entry) => self.read_source(&entry.source).map(Some),
            None => Ok(None),
        }
    }

    pub fn root_exists(&mut self, path: &str) -> PackResult<bool> {
        self.refresh_directory_index()?;
        if matches!(self, NativePack::Directory(_)) {
            if let Err(error) = join_strict(&PathBuf::new(), path) {
                self.index_mut().counters.invalid_path_rejections += 1;
                return Err(error);
            }
        } else if let Err(error) = safe_zip_path(path) {
            self.index_mut().counters.invalid_path_rejections += 1;
            return Err(error);
        }
        let index = self.index_mut();
        index.counters.exists_ops += 1;
        Ok(index.root_entries.contains_key(path))
    }

    pub fn counters(&self) -> PackCounters {
        self.index().counters
    }

    fn read_entry(&mut self, key: &ResourceKey) -> PackResult<Option<Vec<u8>>> {
        let entry = self.index().entries.get(key).cloned();
        match entry {
            Some(entry) => self.read_source(&entry.source).map(Some),
            None => Ok(None),
        }
    }

    fn read_source(&mut self, source: &ResourceSource) -> PackResult<Vec<u8>> {
        let bytes = match source {
            ResourceSource::Directory(path) => match self {
                NativePack::Directory(_) => std::fs::read(path)?,
                NativePack::Zip(_) => {
                    return Err(PackError::invalid_argument(
                        "directory entry source does not belong to zip pack",
                    ))
                }
            },
            ResourceSource::Zip { archive_name } => match self {
                NativePack::Zip(pack) => {
                    let mut file = pack.archive.by_name(archive_name)?;
                    let mut bytes = Vec::with_capacity(file.size().min(usize::MAX as u64) as usize);
                    file.read_to_end(&mut bytes)?;
                    bytes
                }
                NativePack::Directory(_) => {
                    return Err(PackError::invalid_argument(
                        "zip entry source does not belong to directory pack",
                    ))
                }
            },
        };
        let index = self.index_mut();
        index.counters.read_ops += 1;
        index.counters.bytes_returned = index
            .counters
            .bytes_returned
            .saturating_add(bytes.len() as u64);
        Ok(bytes)
    }

    fn index(&self) -> &IndexedPack {
        match self {
            NativePack::Directory(index) => index,
            NativePack::Zip(pack) => &pack.index,
        }
    }

    fn index_mut(&mut self) -> &mut IndexedPack {
        match self {
            NativePack::Directory(index) => index,
            NativePack::Zip(pack) => &mut pack.index,
        }
    }

    fn refresh_directory_index(&mut self) -> PackResult<()> {
        match self {
            NativePack::Directory(index) => directory::rebuild(index),
            NativePack::Zip(_) => Ok(()),
        }
    }
}

//! The resource manager over a pack stack (`MultiPackResourceManager` with
//! one `FallbackResourceManager` per namespace).
//!
//! Packs are listed lowest priority first. A pack's `filter` section blocks
//! namespaces and paths of every pack below it; its patterns are Java regular
//! expressions, so matching calls back into Java ([`Filters`]). Listing
//! callers' predicates only test locations, so Java applies them to the
//! results; filtering and sidecar pairing do not depend on them.

use std::collections::{BTreeMap, HashMap};
use std::sync::Arc;

use crate::assets::packs::pack::{EntryRef, Pack};
use crate::core::id;
use crate::core::java_log;

const METADATA_SUFFIX: &str = ".mcmeta";

/// Asks whether pack `pack`'s filter blocks a namespace or a path.
pub(crate) trait Filters {
    fn namespace_filtered(&self, pack: u16, namespace: &str) -> bool;
    fn path_filtered(&self, pack: u16, path: &str) -> bool;
}

/// No pack in the stack has a filter section.
pub(crate) struct NoFilters;

impl Filters for NoFilters {
    fn namespace_filtered(&self, _: u16, _: &str) -> bool {
        false
    }

    fn path_filtered(&self, _: u16, _: &str) -> bool {
        false
    }
}

/// One `FallbackResourceManager.PackEntry`.
#[derive(Clone, Copy, Debug)]
struct PackEntry {
    /// The pack (absent for a filter-only entry).
    pack: Option<u16>,
    /// The pack whose path filter applies in this namespace.
    filter: Option<u16>,
}

#[derive(Debug)]
struct Namespace {
    name: String,
    fallbacks: Vec<PackEntry>,
}

/// A located resource: the pack (index into the manager's packs) and entry.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct Located {
    pub(crate) pack: u16,
    pub(crate) entry: EntryRef,
}

/// A resource and its sidecar metadata (`None`: `ResourceMetadata.EMPTY`).
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct Found {
    pub(crate) resource: Located,
    pub(crate) metadata: Option<Located>,
}

pub(crate) struct Manager {
    pack_type: usize,
    packs: Vec<Arc<Pack>>,
    has_filter: Vec<bool>,
    namespaces: Vec<Namespace>,
    by_name: HashMap<String, usize>,
}

fn metadata_path(path: &str) -> String {
    format!("{path}{METADATA_SUFFIX}")
}

impl Manager {
    /// `new MultiPackResourceManager(type, packs)`; `has_filter[i]` says
    /// whether pack `i` has a (decoded) filter section.
    pub(crate) fn new(pack_type: usize, packs: Vec<Arc<Pack>>, has_filter: Vec<bool>, filters: &dyn Filters) -> Manager {
        let pack_namespaces: Vec<Vec<String>> = packs.iter().map(|pack| pack.namespaces(pack_type)).collect();
        let mut all: Vec<&str> = Vec::new();
        for names in &pack_namespaces {
            for name in names {
                if !all.contains(&name.as_str()) {
                    all.push(name);
                }
            }
        }
        let mut namespaces: Vec<Namespace> = Vec::new();
        let mut by_name: HashMap<String, usize> = HashMap::new();
        for (index, names) in pack_namespaces.iter().enumerate() {
            let pack = index as u16;
            let filtered = has_filter.get(index).copied().unwrap_or(false);
            for &name in &all {
                let present = names.iter().any(|known| known == name);
                let namespace_filtered = filtered && filters.namespace_filtered(pack, name);
                if !present && !namespace_filtered {
                    continue;
                }
                let slot = *by_name.entry(name.to_owned()).or_insert_with(|| {
                    namespaces.push(Namespace { name: name.to_owned(), fallbacks: Vec::new() });
                    namespaces.len() - 1
                });
                let entry = match (present, namespace_filtered) {
                    (true, true) => PackEntry { pack: Some(pack), filter: Some(pack) },
                    (true, false) => PackEntry { pack: Some(pack), filter: None },
                    _ => PackEntry { pack: None, filter: Some(pack) },
                };
                namespaces[slot].fallbacks.push(entry);
            }
        }
        Manager { pack_type, packs, has_filter, namespaces, by_name }
    }

    /// `getNamespaces()` in the Java map's insertion order.
    pub(crate) fn namespaces(&self) -> impl Iterator<Item = &str> {
        self.namespaces.iter().map(|namespace| namespace.name.as_str())
    }

    fn namespace(&self, name: &str) -> Option<&Namespace> {
        self.by_name.get(name).map(|&slot| &self.namespaces[slot])
    }

    fn filtered(&self, entry: &PackEntry, path: &str, filters: &dyn Filters) -> bool {
        entry.filter.is_some_and(|pack| filters.path_filtered(pack, path))
    }

    fn pack_name(&self, entry: &PackEntry) -> &str {
        let index = entry.pack.or(entry.filter).unwrap_or(0) as usize;
        self.packs.get(index).map_or("", |pack| pack.id.as_str())
    }

    /// Reads a located resource (`Resource.open`).
    pub(crate) fn read(&self, located: Located, namespace: &str, path: &str) -> std::io::Result<Vec<u8>> {
        self.packs[located.pack as usize].read(located.entry, &format!("{namespace}/{path}"))
    }

    /// `Resource.sourcePackId()`.
    pub(crate) fn pack_id(&self, pack: u16) -> &str {
        self.packs.get(pack as usize).map_or("", |pack| pack.id.as_str())
    }

    fn lookup(&self, pack: u16, namespace: &str, path: &str) -> Option<Located> {
        self.packs[pack as usize]
            .resource(self.pack_type, namespace, path)
            .map(|entry| Located { pack, entry })
    }

    /// `getResource(location)`: the highest pack with the file, stopping at
    /// a filter that blocks it. The sidecar is found lazily by
    /// [`Manager::stack_metadata`] from the returned position.
    pub(crate) fn resource(&self, namespace: &str, path: &str, filters: &dyn Filters) -> Option<(Located, usize)> {
        let stack = self.namespace(namespace)?;
        for position in (0..stack.fallbacks.len()).rev() {
            let entry = &stack.fallbacks[position];
            if let Some(pack) = entry.pack {
                if let Some(found) = self.lookup(pack, namespace, path) {
                    return Some((found, position));
                }
            }
            if self.filtered(entry, path, filters) {
                java_log::warn(&format!(
                    "Resource {namespace}:{path} not found, but was filtered by pack {}",
                    self.pack_name(entry)
                ));
                return None;
            }
        }
        None
    }

    /// `createStackMetadataFinder(location, position)`: the sidecar in the
    /// highest pack at or above `position`, stopping at a filter.
    pub(crate) fn stack_metadata(&self, namespace: &str, path: &str, position: usize, filters: &dyn Filters) -> Option<Located> {
        let stack = self.namespace(namespace)?;
        let metadata = metadata_path(path);
        for at in (position..stack.fallbacks.len()).rev() {
            let entry = &stack.fallbacks[at];
            if let Some(pack) = entry.pack {
                if let Some(found) = self.lookup(pack, namespace, &metadata) {
                    return Some(found);
                }
            }
            if self.filtered(entry, &metadata, filters) {
                break;
            }
        }
        None
    }

    /// `getResourceStack(location)`, lowest pack first; each entry's sidecar
    /// comes from its own pack unless a higher filter blocked sidecars.
    pub(crate) fn resource_stack(&self, namespace: &str, path: &str, filters: &dyn Filters) -> Vec<Found> {
        let Some(stack) = self.namespace(namespace) else { return Vec::new() };
        let metadata = metadata_path(path);
        let mut found = Vec::new();
        let mut metadata_blocked = false;
        let mut filtered_by: Option<&str> = None;
        for position in (0..stack.fallbacks.len()).rev() {
            let entry = &stack.fallbacks[position];
            if let Some(pack) = entry.pack {
                if let Some(resource) = self.lookup(pack, namespace, path) {
                    let sidecar = if metadata_blocked { None } else { self.lookup(pack, namespace, &metadata) };
                    found.push(Found { resource, metadata: sidecar });
                }
            }
            if self.filtered(entry, path, filters) {
                filtered_by = Some(self.pack_name(entry));
                break;
            }
            if self.filtered(entry, &metadata, filters) {
                metadata_blocked = true;
            }
        }
        if found.is_empty() {
            if let Some(pack) = filtered_by {
                java_log::warn(&format!("Resource {namespace}:{path} not found, but was filtered by pack {pack}"));
            }
        }
        found.reverse();
        found
    }

    /// `listResources(directory, _)`: every location's highest file, in
    /// `ResourceLocation` order (path, then namespace).
    pub(crate) fn list(&self, directory: &str, filters: &dyn Filters) -> Vec<((String, String), Found)> {
        let mut results = Vec::new();
        for stack in &self.namespaces {
            let mut files: HashMap<String, (Located, usize)> = HashMap::new();
            let mut sidecars: HashMap<String, (Located, usize)> = HashMap::new();
            for (position, entry) in stack.fallbacks.iter().enumerate() {
                if entry.filter.is_some() {
                    files.retain(|path, _| !self.filtered(entry, path, filters));
                    sidecars.retain(|path, _| !self.filtered(entry, path, filters));
                }
                let Some(pack) = entry.pack else { continue };
                self.packs[pack as usize].list(self.pack_type, &stack.name, directory, &mut |path, located| {
                    let value = (Located { pack, entry: located }, position);
                    if path.ends_with(METADATA_SUFFIX) {
                        sidecars.insert(path.to_owned(), value);
                    } else {
                        files.insert(path.to_owned(), value);
                    }
                });
            }
            for (path, (resource, position)) in files {
                let metadata = sidecars
                    .get(&metadata_path(&path))
                    .filter(|(_, sidecar_position)| *sidecar_position >= position)
                    .map(|(located, _)| *located);
                results.push(((stack.name.clone(), path), Found { resource, metadata }));
            }
        }
        results.sort_by(|(a, _), (b, _)| id::compare_locations((&a.0, &a.1), (&b.0, &b.1)));
        results
    }

    /// `listResourceStacks(directory, _)`: each location's files, lowest pack
    /// first, each with the sidecar from its own pack.
    pub(crate) fn list_stacks(&self, directory: &str, filters: &dyn Filters) -> Vec<((String, String), Vec<Found>)> {
        struct EntryStack {
            files: Vec<Located>,
            sidecars: BTreeMap<u16, Located>,
        }
        let mut results = Vec::new();
        for stack in &self.namespaces {
            let mut map: HashMap<String, EntryStack> = HashMap::new();
            for entry in &stack.fallbacks {
                if entry.filter.is_some() {
                    for (path, entry_stack) in map.iter_mut() {
                        if self.filtered(entry, path, filters) {
                            entry_stack.files.clear();
                        } else if self.filtered(entry, &metadata_path(path), filters) {
                            entry_stack.sidecars.clear();
                        }
                    }
                }
                let Some(pack) = entry.pack else { continue };
                self.packs[pack as usize].list(self.pack_type, &stack.name, directory, &mut |path, located| {
                    let located = Located { pack, entry: located };
                    if let Some(base) = path.strip_suffix(METADATA_SUFFIX) {
                        map.entry(base.to_owned())
                            .or_insert_with(|| EntryStack { files: Vec::new(), sidecars: BTreeMap::new() })
                            .sidecars
                            .insert(pack, located);
                    } else {
                        map.entry(path.to_owned())
                            .or_insert_with(|| EntryStack { files: Vec::new(), sidecars: BTreeMap::new() })
                            .files
                            .push(located);
                    }
                });
            }
            for (path, entry_stack) in map {
                if entry_stack.files.is_empty() {
                    continue;
                }
                let found = entry_stack
                    .files
                    .iter()
                    .map(|&resource| Found { resource, metadata: entry_stack.sidecars.get(&resource.pack).copied() })
                    .collect();
                results.push(((stack.name.clone(), path), found));
            }
        }
        results.sort_by(|(a, _), (b, _)| id::compare_locations((&a.0, &a.1), (&b.0, &b.1)));
        results
    }

    pub(crate) fn has_filters(&self) -> bool {
        self.has_filter.iter().any(|&filter| filter)
    }

    pub(crate) fn close(&self) {
        for pack in &self.packs {
            pack.close();
        }
    }
}

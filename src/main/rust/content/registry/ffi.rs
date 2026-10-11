//! C ABI for registry identity snapshots (`NativeRegistrySnapshot`).

use super::{current, publish, Elements, KnownPack, Snapshot, Table, Tags};
use crate::assets::ffi_util::{bytes_arg, give, str_arg, Reader, Writer};
use crate::content::tags::Location;
use std::sync::Arc;

const STALE: i64 = -2;
const MISSING: i64 = -1;

fn read_pack(reader: &mut Reader<'_>) -> Option<KnownPack> {
    Some(KnownPack { namespace: Arc::from(reader.text()?), id: Arc::from(reader.text()?), version: Arc::from(reader.text()?) })
}

fn read_location(reader: &mut Reader<'_>) -> Option<Location> {
    Some(Location::new(reader.text()?, reader.text()?))
}

/// A member head with this bit refers to a previous tag's members by index.
const PREVIOUS: u32 = 1 << 31;
/// A name head announcing a new name (namespace and path follow).
const NEW_NAME: u32 = u32::MAX;

/// One registry's tags: u32 flags (bit 0: names follow) and u32 tag count;
/// per tag, a member head: a count of member IDs that follow, or
/// [`PREVIOUS`] | k to keep previous tag k's members. With bit 0, per tag a
/// name head: [`NEW_NAME`] then namespace and path, or k for previous tag
/// k's name. Without names, the tags keep `previous`'s names in order.
fn read_tags(reader: &mut Reader<'_>, previous: Option<&Tags>) -> Option<Tags> {
    let flags = reader.u32()?;
    let count = reader.u32()? as usize;
    let earlier = |index: u32| previous.and_then(|previous| previous.get(index as usize));
    let members = (0..count)
        .map(|_| match reader.u32()? {
            head if head & PREVIOUS != 0 => earlier(head & !PREVIOUS).map(|(_, members)| Arc::clone(members)),
            length => (0..length).map(|_| reader.u32()).collect::<Option<Arc<[u32]>>>(),
        })
        .collect::<Option<Vec<_>>>()?;
    let names = if flags & 1 != 0 {
        (0..count)
            .map(|_| match reader.u32()? {
                NEW_NAME => read_location(reader),
                index => earlier(index).map(|(name, _)| name.clone()),
            })
            .collect::<Option<Vec<_>>>()?
    } else {
        let previous = previous.filter(|previous| previous.len() == count)?;
        previous.iter().map(|(name, _)| name.clone()).collect()
    };
    Some(names.into_iter().zip(members).collect())
}

fn parse(bytes: &[u8]) -> Option<Snapshot> {
    let mut reader = Reader::new(bytes);
    let packs = (0..reader.u32()?).map(|_| read_pack(&mut reader)).collect::<Option<Vec<_>>>()?;
    let mut tables = Vec::new();
    for _ in 0..reader.u32()? {
        let key = read_location(&mut reader)?;
        let mut elements = Elements::default();
        for _ in 0..reader.u32()? {
            let element = read_location(&mut reader)?;
            let known = reader.u32()?;
            elements.push(element, (known != u32::MAX).then_some(known));
        }
        let tags = read_tags(&mut reader, None)?;
        tables.push(Table::new(key, elements, tags));
    }
    reader.done().then(|| Snapshot::new(packs, tables))
}

/// Publishes a snapshot for `context`: known packs (u32 count; namespace,
/// id, version texts), then registries (u32 count; key namespace and path,
/// u32 element count with each element's namespace, path and known pack
/// index (u32, `u32::MAX` for none), then its tags with names (see
/// `read_tags`)). Returns the generation, or 0 on malformed input.
///
/// # Safety
/// `records` valid for its length.
#[no_mangle]
pub unsafe extern "C" fn mattmc_registry_publish(context: u32, records: *const u8, records_len: u64) -> u64 {
    let Some(bytes) = (unsafe { bytes_arg(records, records_len) }) else { return 0 };
    match parse(bytes) {
        Some(snapshot) => publish(context, snapshot),
        None => 0,
    }
}

/// Publishes new tags for `context`'s current `generation` (a tag rebind):
/// a u32 count of rebound registries, each its u32 publish index and tags
/// (see `read_tags`; names may be omitted when unchanged). Elements, IDs,
/// packs and the other registries' tags carry over. Returns
/// the new generation, 0 on malformed input, or -2 cast to u64 when
/// `generation` is not current.
///
/// # Safety
/// `records` valid for its length.
#[no_mangle]
pub unsafe extern "C" fn mattmc_registry_retag(context: u32, generation: u64, records: *const u8, records_len: u64) -> u64 {
    let Some(snapshot) = current(context, generation) else { return STALE as u64 };
    let Some(bytes) = (unsafe { bytes_arg(records, records_len) }) else { return 0 };
    let mut reader = Reader::new(bytes);
    let changes = reader.u32().and_then(|count| {
        (0..count)
            .map(|_| {
                let index = reader.u32()? as usize;
                let previous = snapshot.table_at(index).map(|table| &*table.tags);
                Some((index, read_tags(&mut reader, previous)?))
            })
            .collect::<Option<Vec<_>>>()
    });
    match changes.filter(|_| reader.done()).and_then(|changes| snapshot.retagged(changes)) {
        Some(retagged) => publish(context, retagged),
        None => 0,
    }
}

unsafe fn location(namespace: *const u8, namespace_len: u64, path: *const u8, path_len: u64) -> Option<Location> {
    Some(Location::new(unsafe { str_arg(namespace, namespace_len) }?, unsafe { str_arg(path, path_len) }?))
}

/// An element's network ID: the ID, -1 when the registry or element is
/// absent, or -2 when `generation` is not current.
///
/// # Safety
/// Strings valid for their lengths.
#[no_mangle]
pub unsafe extern "C" fn mattmc_registry_id(
    context: u32,
    generation: u64,
    registry_namespace: *const u8,
    registry_namespace_len: u64,
    registry_path: *const u8,
    registry_path_len: u64,
    namespace: *const u8,
    namespace_len: u64,
    path: *const u8,
    path_len: u64,
) -> i64 {
    let Some(snapshot) = current(context, generation) else { return STALE };
    let (Some(registry), Some(element)) = (unsafe {
        (location(registry_namespace, registry_namespace_len, registry_path, registry_path_len), location(namespace, namespace_len, path, path_len))
    }) else {
        return MISSING;
    };
    snapshot.table(&registry).and_then(|table| table.id(&element)).map_or(MISSING, i64::from)
}

/// The elements a client already has from the registry at publish `index`:
/// one byte per element ID (1 when its known pack is among `known`, records
/// of namespace, id and version texts). Returns 0, -1 for an absent
/// registry, -2 for a retired generation.
///
/// # Safety
/// `known` valid for its length; outputs writable.
#[no_mangle]
pub unsafe extern "C" fn mattmc_registry_known_elements(
    context: u32,
    generation: u64,
    index: u32,
    known: *const u8,
    known_len: u64,
    out_ptr: *mut *mut u8,
    out_len: *mut u64,
) -> i64 {
    let Some(snapshot) = current(context, generation) else { return STALE };
    let Some(known) = (unsafe { bytes_arg(known, known_len) }) else { return MISSING };
    let mut reader = Reader::new(known);
    let mut packs = Vec::new();
    while !reader.done() {
        match read_pack(&mut reader) {
            Some(pack) => packs.push(pack),
            None => return MISSING,
        }
    }
    match snapshot.known_elements_at(index as usize, &packs) {
        Some(mask) => {
            unsafe { give(mask.into_iter().map(u8::from).collect(), out_ptr, out_len) };
            0
        }
        None => MISSING,
    }
}

/// Tags of the registry at publish `index` for the network: per tag in
/// publish order (Java's tag iteration order, whose names Java kept),
/// u32-counted member IDs. Returns 0, -1 for an absent registry, -2 for a
/// retired generation.
///
/// # Safety
/// Outputs writable.
#[no_mangle]
pub unsafe extern "C" fn mattmc_registry_tags(context: u32, generation: u64, index: u32, out_ptr: *mut *mut u8, out_len: *mut u64) -> i64 {
    let Some(snapshot) = current(context, generation) else { return STALE };
    let Some(table) = snapshot.table_at(index as usize) else { return MISSING };
    let mut writer = Writer::default();
    for (_, members) in table.tags.iter() {
        writer.u32(members.len() as u32);
        for &member in members.iter() {
            writer.u32(member);
        }
    }
    unsafe { give(writer.bytes, out_ptr, out_len) };
    0
}

/// Drops `context`'s snapshot (its registries were discarded).
#[no_mangle]
pub extern "C" fn mattmc_registry_release(context: u32) {
    super::release(context);
}

#[cfg(test)]
mod tests {
    use super::*;

    fn text(writer: &mut Writer, value: &str) {
        writer.text(value);
    }

    /// One registry (`test:words`, two elements) with `tags` (name, members).
    fn publish_records(tags: &[(&str, Result<&[u32], u32>)]) -> Vec<u8> {
        let mut writer = Writer::default();
        writer.u32(0);
        writer.u32(1);
        text(&mut writer, "test");
        text(&mut writer, "words");
        writer.u32(2);
        for element in ["a", "b"] {
            text(&mut writer, "test");
            text(&mut writer, element);
            writer.u32(u32::MAX);
        }
        tag_records(&mut writer, tags, true);
        writer.bytes
    }

    /// Members: `Ok(ids)`, or `Err(k)` for previous tag k's. Names: a new
    /// name, or `#k` for previous tag k's.
    fn tag_records(writer: &mut Writer, tags: &[(&str, Result<&[u32], u32>)], names: bool) {
        writer.u32(u32::from(names));
        writer.u32(tags.len() as u32);
        for (_, members) in tags {
            match members {
                Ok(members) => {
                    writer.u32(members.len() as u32);
                    members.iter().for_each(|&member| writer.u32(member));
                }
                Err(previous) => writer.u32(PREVIOUS | previous),
            }
        }
        if names {
            for (name, _) in tags {
                match name.strip_prefix('#') {
                    Some(previous) => writer.u32(previous.parse().unwrap()),
                    None => {
                        writer.u32(NEW_NAME);
                        text(writer, "test");
                        text(writer, name);
                    }
                }
            }
        }
    }

    fn retag(context: u32, generation: u64, tags: &[(&str, Result<&[u32], u32>)], names: bool) -> u64 {
        let mut writer = Writer::default();
        writer.u32(1);
        writer.u32(0);
        tag_records(&mut writer, tags, names);
        unsafe { mattmc_registry_retag(context, generation, writer.bytes.as_ptr(), writer.bytes.len() as u64) }
    }

    #[test]
    fn retags_reuse_names_and_reject_mismatched_records() {
        let context = 31;
        let records = publish_records(&[("first", Ok(&[0, 1])), ("second", Ok(&[1]))]);
        let generation = unsafe { mattmc_registry_publish(context, records.as_ptr(), records.len() as u64) };
        assert_ne!(0, generation);
        let words = Location::new("test", "words");
        let tags = |generation: u64| -> Vec<(String, Vec<u32>)> {
            let snapshot = current(context, generation).unwrap();
            snapshot.table(&words).unwrap().tags.iter().map(|(name, members)| (name.path.to_string(), members.to_vec())).collect()
        };

        let unnamed = retag(context, generation, &[("", Ok(&[1])), ("", Err(1))], false);
        assert!(unnamed != 0 && unnamed != STALE as u64);
        assert_eq!(vec![("first".into(), vec![1]), ("second".into(), vec![1])], tags(unnamed), "an unchanged tag keeps its members");

        // Reordered and extended: names and members by reference, plus a new tag.
        let reordered = retag(context, unnamed, &[("#1", Err(1)), ("third", Ok(&[0])), ("#0", Err(0))], true);
        assert_eq!(
            vec![("second".into(), vec![1]), ("third".into(), vec![0]), ("first".into(), vec![1])],
            tags(reordered)
        );

        assert_eq!(0, retag(context, reordered, &[("", Ok(&[0]))], false), "unnamed tags must match the previous count");
        assert_eq!(0, retag(context, reordered, &[("#7", Ok(&[]))], true), "references must name previous tags");
        assert_eq!(0, retag(context, reordered, &[("fourth", Err(9))], true), "references must name previous tags");
        assert_eq!(STALE as u64, retag(context, generation, &[], true), "retired generations are refused");
        super::super::release(context);
    }
}

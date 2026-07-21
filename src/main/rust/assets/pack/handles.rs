use std::sync::{Arc, Mutex, OnceLock};

use super::errors::{PackError, PackResult};
use super::index::NativePack;

const PACK_HANDLE_KIND: u64 = 0x50;
const HANDLE_KIND_SHIFT: u64 = 56;
const HANDLE_GENERATION_SHIFT: u64 = 32;
const HANDLE_GENERATION_MASK: u64 = 0x00ff_ffff;
const HANDLE_SLOT_MASK: u64 = 0xffff_ffff;

#[derive(Default)]
pub struct PackHandleTable {
    entries: Vec<PackHandleEntry>,
}

#[derive(Default)]
struct PackHandleEntry {
    generation: u32,
    pack: Option<Arc<Mutex<NativePack>>>,
}

impl PackHandleTable {
    pub fn global() -> &'static Mutex<PackHandleTable> {
        static TABLE: OnceLock<Mutex<PackHandleTable>> = OnceLock::new();
        TABLE.get_or_init(|| Mutex::new(PackHandleTable::default()))
    }

    pub fn insert(&mut self, pack: NativePack) -> u64 {
        for (slot, entry) in self.entries.iter_mut().enumerate() {
            if entry.pack.is_none() {
                entry.generation = next_generation(entry.generation);
                entry.pack = Some(Arc::new(Mutex::new(pack)));
                return encode_handle(slot as u32, entry.generation);
            }
        }
        let slot = self.entries.len() as u32;
        self.entries.push(PackHandleEntry {
            generation: 1,
            pack: Some(Arc::new(Mutex::new(pack))),
        });
        encode_handle(slot, 1)
    }

    pub fn get(&self, handle: u64) -> PackResult<Arc<Mutex<NativePack>>> {
        let (slot, generation) = decode_handle(handle)?;
        let entry = self
            .entries
            .get(slot as usize)
            .ok_or_else(PackError::invalid_handle)?;
        if entry.generation != generation {
            return Err(PackError::invalid_handle());
        }
        entry
            .pack
            .as_ref()
            .cloned()
            .ok_or_else(PackError::invalid_handle)
    }

    pub fn remove(&mut self, handle: u64) -> PackResult<Arc<Mutex<NativePack>>> {
        let (slot, generation) = decode_handle(handle)?;
        let entry = self
            .entries
            .get_mut(slot as usize)
            .ok_or_else(PackError::invalid_handle)?;
        if entry.generation != generation {
            return Err(PackError::invalid_handle());
        }
        entry.pack.take().ok_or_else(PackError::invalid_handle)
    }
}

fn encode_handle(slot: u32, generation: u32) -> u64 {
    (PACK_HANDLE_KIND << HANDLE_KIND_SHIFT)
        | (((generation as u64) & HANDLE_GENERATION_MASK) << HANDLE_GENERATION_SHIFT)
        | slot as u64
}

fn decode_handle(handle: u64) -> PackResult<(u32, u32)> {
    let kind = handle >> HANDLE_KIND_SHIFT;
    let generation = ((handle >> HANDLE_GENERATION_SHIFT) & HANDLE_GENERATION_MASK) as u32;
    let slot = (handle & HANDLE_SLOT_MASK) as u32;
    if kind != PACK_HANDLE_KIND || generation == 0 {
        return Err(PackError::invalid_handle());
    }
    Ok((slot, generation))
}

fn next_generation(current: u32) -> u32 {
    let next = (current.wrapping_add(1)) & HANDLE_GENERATION_MASK as u32;
    if next == 0 {
        1
    } else {
        next
    }
}

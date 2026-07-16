use std::collections::HashMap;

const KIND_SHIFT: u64 = 56;
const GENERATION_SHIFT: u64 = 32;
const KIND_MASK: u64 = 0xff;
const GENERATION_MASK: u64 = 0x00ff_ffff;
const SLOT_MASK: u64 = 0xffff_ffff;
const MAX_GENERATION: u32 = GENERATION_MASK as u32;

/// Native audio resource kind encoded into the high byte of every public handle.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum ResourceKind {
    Device = 1,
    Source = 2,
    Buffer = 3,
}

impl ResourceKind {
    fn from_byte(value: u8) -> Option<Self> {
        match value {
            1 => Some(Self::Device),
            2 => Some(Self::Source),
            3 => Some(Self::Buffer),
            _ => None,
        }
    }
}

/// Packed 64-bit handle layout shared with Java as an opaque `long`.
///
/// ```text
/// 63..56  resource kind: 1=device, 2=source, 3=buffer
/// 55..32  generation: monotonically advanced on every insert
/// 31..00  slot: table-local nonzero index
/// ```
///
/// The generation prevents a destroyed handle from silently resolving to a new
/// resource if a slot is reused after reload/shutdown. The kind byte makes
/// wrong-resource calls fail before they can hit an unrelated table.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct NativeHandle {
    pub(crate) kind: ResourceKind,
    pub(crate) generation: u32,
    pub(crate) slot: u32,
}

impl NativeHandle {
    pub(crate) fn new(kind: ResourceKind, generation: u32, slot: u32) -> Option<Self> {
        if generation == 0 || generation > MAX_GENERATION || slot == 0 {
            return None;
        }
        Some(Self {
            kind,
            generation,
            slot,
        })
    }

    pub(crate) fn raw(self) -> u64 {
        ((self.kind as u64) << KIND_SHIFT)
            | ((self.generation as u64) << GENERATION_SHIFT)
            | self.slot as u64
    }

    pub(crate) fn decode(raw: u64) -> Option<Self> {
        let kind = ResourceKind::from_byte(((raw >> KIND_SHIFT) & KIND_MASK) as u8)?;
        let generation = ((raw >> GENERATION_SHIFT) & GENERATION_MASK) as u32;
        let slot = (raw & SLOT_MASK) as u32;
        Self::new(kind, generation, slot)
    }
}

struct Entry<T> {
    generation: u32,
    value: T,
}

pub(crate) struct HandleTable<T> {
    kind: ResourceKind,
    next_slot: u32,
    next_generation: u32,
    entries: HashMap<u32, Entry<T>>,
}

impl<T> HandleTable<T> {
    pub(crate) fn new(kind: ResourceKind) -> Self {
        Self {
            kind,
            next_slot: 0,
            next_generation: 0,
            entries: HashMap::new(),
        }
    }

    pub(crate) fn insert(&mut self, value: T) -> u64 {
        let slot = self.allocate_slot();
        let generation = self.allocate_generation();
        self.entries.insert(slot, Entry { generation, value });
        NativeHandle::new(self.kind, generation, slot)
            .expect("allocated nonzero handle fields")
            .raw()
    }

    pub(crate) fn get(&self, handle: u64) -> Option<&T> {
        let handle = self.decode_for_table(handle)?;
        self.entries
            .get(&handle.slot)
            .filter(|entry| entry.generation == handle.generation)
            .map(|entry| &entry.value)
    }

    pub(crate) fn get_mut(&mut self, handle: u64) -> Option<&mut T> {
        let handle = self.decode_for_table(handle)?;
        self.entries
            .get_mut(&handle.slot)
            .filter(|entry| entry.generation == handle.generation)
            .map(|entry| &mut entry.value)
    }

    pub(crate) fn remove(&mut self, handle: u64) -> Option<T> {
        let handle = self.decode_for_table(handle)?;
        if self
            .entries
            .get(&handle.slot)
            .is_some_and(|entry| entry.generation == handle.generation)
        {
            return self.entries.remove(&handle.slot).map(|entry| entry.value);
        }
        None
    }

    pub(crate) fn handles_for<F>(&self, mut f: F) -> Vec<u64>
    where
        F: FnMut(&T) -> bool,
    {
        self.entries
            .iter()
            .filter_map(|(slot, entry)| {
                f(&entry.value).then(|| {
                    NativeHandle::new(self.kind, entry.generation, *slot)
                        .expect("stored handle fields are nonzero")
                        .raw()
                })
            })
            .collect()
    }

    pub(crate) fn len(&self) -> usize {
        self.entries.len()
    }

    pub(crate) fn values(&self) -> impl Iterator<Item = &T> {
        self.entries.values().map(|entry| &entry.value)
    }

    fn decode_for_table(&self, handle: u64) -> Option<NativeHandle> {
        NativeHandle::decode(handle).filter(|handle| handle.kind == self.kind)
    }

    fn allocate_slot(&mut self) -> u32 {
        loop {
            self.next_slot = self.next_slot.wrapping_add(1).max(1);
            if !self.entries.contains_key(&self.next_slot) {
                return self.next_slot;
            }
        }
    }

    fn allocate_generation(&mut self) -> u32 {
        self.next_generation = if self.next_generation >= MAX_GENERATION {
            1
        } else {
            self.next_generation + 1
        };
        self.next_generation
    }
}

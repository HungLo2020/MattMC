//! Primary visible-entity identity/tracking registry, shared by client/server.
//! Java retains behavior bindings, never ID/UUID maps or an ordered member copy.
mod ffi;
mod id_table;
use id_table::Table;
use std::{
    collections::HashMap,
    sync::atomic::{AtomicI32, AtomicU64, Ordering},
};

type Uuid = Option<(u64, u64)>;
#[repr(C)]
#[derive(Default)]
struct View {
    pointer: AtomicU64,
    length: AtomicU64,
    revision: AtomicU64,
    first: AtomicI32,
    uuid_count: AtomicI32,
}
#[derive(Default)]
struct Binding {
    generation: u32,
    references: u32,
    pending: bool,
}
#[derive(Default)]
struct Registry {
    ids: Table,
    uuids: HashMap<Uuid, u64>,
    bindings: Vec<Binding>,
    free: Vec<u32>,
}
impl Registry {
    fn publish(&self, view: &View) {
        view.pointer
            .store(self.ids.nodes.as_ptr() as u64, Ordering::Release);
        view.length
            .store(self.ids.nodes.len() as u64, Ordering::Release);
        view.first.store(self.ids.first, Ordering::Release);
        view.uuid_count.store(
            self.uuids
                .len()
                .try_into()
                .expect("entity count exceeds Java range"),
            Ordering::Release,
        );
        view.revision.store(self.ids.revision, Ordering::Release);
    }
    fn binding(&mut self, token: u64) -> Option<&mut Binding> {
        let b = self.bindings.get_mut(token as u32 as usize)?;
        (b.references != 0 && b.generation == (token >> 32) as u32).then_some(b)
    }
    fn allocate(&mut self) -> u64 {
        while let Some(i) = self.free.pop() {
            let b = &mut self.bindings[i as usize];
            if let Some(generation) = b.generation.checked_add(1) {
                *b = Binding {
                    generation,
                    references: 2,
                    pending: true,
                };
                return (u64::from(generation) << 32) | u64::from(i);
            }
        }
        let i = u32::try_from(self.bindings.len()).expect("entity bindings exhausted");
        assert!(i <= i32::MAX as u32);
        self.bindings.push(Binding {
            generation: 1,
            references: 2,
            pending: true,
        });
        (1u64 << 32) | u64::from(i)
    }
    fn release(&mut self, token: u64) -> u64 {
        if token == 0 {
            return 0;
        }
        let b = self.binding(token).expect("invalid entity binding release");
        b.references -= 1;
        if b.references != 0 {
            return 0;
        }
        self.free.push(token as u32);
        token
    }
    /// UUID commits before Java's virtual ID getter. A pending reference keeps
    /// that receiver alive across nested removals, replacements and exceptions.
    fn begin_add(&mut self, uuid: Uuid) -> u64 {
        if self.uuids.contains_key(&uuid) {
            return 0;
        }
        let token = self.allocate();
        self.uuids.insert(uuid, token);
        token
    }
    fn finish_add(&mut self, token: u64, id: Option<i32>) -> Option<u64> {
        let b = self.binding(token)?;
        if !b.pending {
            return None;
        }
        b.pending = false;
        let retired = if let Some(id) = id {
            b.references += 1;
            let old = self.ids.put(id, token);
            self.release(old)
        } else {
            0
        };
        let pending_retired = self.release(token);
        debug_assert!(retired == 0 || pending_retired == 0);
        Some(retired.max(pending_retired))
    }
    fn remove_uuid(&mut self, uuid: Uuid) -> u64 {
        let old = self.uuids.remove(&uuid).unwrap_or(0);
        self.release(old)
    }
    fn remove_id(&mut self, id: i32) -> u64 {
        let old = self.ids.remove(id);
        self.release(old)
    }
}

#[cfg(test)]
mod tests;

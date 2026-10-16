//! Per-source ownership of JVM Ticket resources. Counts are native ownership
//! metadata, not a second ticket store. Queue records contain only source/cell
//! identities; Java's global source registry is weak.
use super::Fault;
use std::collections::{HashMap, VecDeque};
use std::sync::{Mutex, OnceLock, atomic::{AtomicBool, AtomicU64, Ordering}};

#[derive(Default)]
struct Releases { active: usize, events: VecDeque<(u64, u64)> }
static RELEASES: OnceLock<Mutex<Releases>> = OnceLock::new();
static PENDING: AtomicBool = AtomicBool::new(false);
fn releases() -> std::sync::MutexGuard<'static, Releases> {
    RELEASES.get_or_init(|| Mutex::new(Releases::default())).lock().unwrap_or_else(|p| p.into_inner())
}
pub(super) trait RootKey: Copy + Eq + std::hash::Hash { fn erased(self)->u64; fn is_null(self)->bool; }
impl RootKey for u32 { fn erased(self)->u64{self as u64} fn is_null(self)->bool{self==u32::MAX} }
impl RootKey for u64 { fn erased(self)->u64{self} fn is_null(self)->bool{self==u64::MAX} }
#[derive(Default)]
pub(super) struct ResourceRoots<K:RootKey> { source: u64, counts: HashMap<K,u32> }
pub(super) type Roots=ResourceRoots<u32>;
pub(super) type MapRoots=ResourceRoots<u64>;
static NEXT_SOURCE:AtomicU64=AtomicU64::new(1);
pub(super) fn allocate_source()->Option<u64>{NEXT_SOURCE.fetch_update(Ordering::Relaxed,Ordering::Relaxed,|v|v.checked_add(1)).ok()}
impl<K:RootKey> ResourceRoots<K> {
    pub(super) fn enable(&mut self, source: u64) -> Result<(), Fault> {
        if source == 0 || self.source != 0 || !self.counts.is_empty() { return Err(Fault::IllegalState); }
        self.source = source;
        Ok(())
    }
    pub(super) fn source(&self)->u64 {self.source}
    pub(super) fn retain(&mut self, cell: K) -> Result<(), Fault> {
        if self.source == 0 || cell.is_null() { return Ok(()); }
        if let Some(count) = self.counts.get_mut(&cell) { *count += 1; return Ok(()); }
        self.counts.try_reserve(1).map_err(|_| Fault::Allocation)?;
        let mut queue = releases();
        // Reserve each association's eventual release before publishing it.
        let needed = queue.active + 1;
        queue.events.try_reserve(needed).map_err(|_| Fault::Allocation)?;
        queue.active += 1;
        self.counts.insert(cell, 1);
        Ok(())
    }
    pub(super) fn release(&mut self, cell: K) {
        if self.source == 0 || cell.is_null() { return; }
        let count = self.counts.get_mut(&cell).expect("source root without an entry owner");
        if *count > 1 { *count -= 1; return; }
        self.counts.remove(&cell);
        let mut queue = releases();
        queue.active -= 1;
        queue.events.push_back((self.source, cell.erased()));
        PENDING.store(true, Ordering::Release);
    }
    pub(super) fn count(&self, cell: K) -> u32 { self.counts.get(&cell).copied().unwrap_or(0) }
}
impl<K:RootKey> Drop for ResourceRoots<K> {
    fn drop(&mut self) {
        if self.source == 0 { return; }
        let mut queue = releases();
        for cell in self.counts.keys() { queue.active -= 1; queue.events.push_back((self.source, cell.erased())); }
        if !queue.events.is_empty() { PENDING.store(true, Ordering::Release); }
    }
}
pub(super) fn pending() -> bool { PENDING.load(Ordering::Acquire) }
pub(super) fn drain(out: &mut [i64]) -> usize {
    let mut queue = releases();
    let count = (out.len() / 2).min(queue.events.len());
    for index in 0..count {
        let (source, cell) = queue.events.pop_front().unwrap();
        out[index*2] = source as i64; out[index*2+1] = cell as i64;
    }
    PENDING.store(!queue.events.is_empty(), Ordering::Release);
    count
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test] fn one_sources_last_entry_releases_without_waiting_for_another_source() {
        let mut first=Roots::default();first.enable(11).unwrap();
        let mut second=Roots::default();second.enable(12).unwrap();
        first.retain(7).unwrap();first.retain(7).unwrap();second.retain(7).unwrap();
        first.release(7);assert_eq!(first.count(7),1);
        first.release(7);assert_eq!(second.count(7),1);
        let mut out=[0;16];let n=drain(&mut out);
        assert!(out[..n*2].chunks_exact(2).any(|pair|pair==[11,7]));
        second.release(7);
    }
    #[test] fn source_identity_distinguishes_recycled_cell_bindings() {
        let mut old=Roots::default();old.enable(21).unwrap();old.retain(9).unwrap();old.release(9);
        let mut new=Roots::default();new.enable(22).unwrap();new.retain(9).unwrap();
        let mut out=[0;16];let n=drain(&mut out);
        assert!(out[..n*2].chunks_exact(2).any(|pair|pair==[21,9]));assert_eq!(new.count(9),1);
    }
}

//! Client loaded-chunk policy. Object bindings and side effects are external;
//! slot identity, counts, empty membership and transition ordering are owned here.
//! Integration must deliver callbacks at the returned boundary, never under a
//! world lock, and resume only afterwards. See the Frozen operation corpus.
mod empty_set;
mod ffi;
use empty_set::EmptySet;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) enum Event { Unload(u64), NonEmpty(u64) }
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) enum Fault { Input, Callback }
/// Access existing semantic owners. This is not a reconstructed block array.
/// Counters must be read at each step, including after a reentrant callback.
pub(crate) trait Source {
    fn position(&self, id: u64) -> (i32, i32);
    fn sections(&self, id: u64) -> usize;
    fn section_y(&self, id: u64, index: usize) -> i32;
    fn empty(&self, id: u64, index: usize) -> Result<bool, Fault>;
}
pub(crate) struct Residency {
    pub(crate) radius: i32,
    pub(crate) center: (i32, i32),
    pub(crate) slots: Box<[u64]>,
    pub(crate) count: i32,
    pub(crate) position_fingerprint: u64,
    pub(crate) empty: EmptySet,
}
impl Residency {
    pub(crate) fn new(radius: i32) -> Option<Self> {
        if !(0..=127).contains(&radius) { return None; }
        let width = (radius * 2 + 1) as usize;
        Some(Self { radius, center: (0, 0), slots: vec![0; width * width].into_boxed_slice(),
            count: 0, position_fingerprint: 0, empty: EmptySet::new() })
    }
    pub(crate) fn width(&self) -> i32 { self.radius * 2 + 1 }
    pub(crate) fn index(&self, x: i32, z: i32) -> usize {
        (z.rem_euclid(self.width()) * self.width() + x.rem_euclid(self.width())) as usize
    }
    pub(crate) fn in_range(&self, x: i32, z: i32) -> bool {
        x.wrapping_sub(self.center.0).wrapping_abs() <= self.radius
            && z.wrapping_sub(self.center.1).wrapping_abs() <= self.radius
    }
    pub(crate) fn lookup(&self, x: i32, z: i32, source: &impl Source) -> u64 {
        if !self.in_range(x, z) { return 0; }
        let id = self.slots[self.index(x, z)];
        if id != 0 && source.position(id) == (x, z) { id } else { 0 }
    }
    /// Visit old physical slots in order and publish the new window only after
    /// all admitted replacements finish. Moving center alone never evicts slots.
    pub(crate) fn relocated(&self, radius: i32, source: &impl Source,
        mut callback: impl FnMut(Event) -> Result<(), Fault>) -> Result<Self, Fault> {
        let mut next = Self::new(radius).ok_or(Fault::Input)?; next.center = self.center;
        for &id in self.slots.iter().filter(|&&id| id != 0) {
            let (x,z) = source.position(id);
            if next.in_range(x,z) { next.replace(next.index(x,z),id,source,&mut callback)?; }
        }
        Ok(next)
    }
    /// Staged publication permits Java compatibility work between count, keys
    /// and callback boundaries. The normal Rust producer uses the same steps.
    pub(crate) fn publish(&mut self, index: usize, id: u64) -> Result<u64, Fault> {
        let slot = self.slots.get_mut(index).ok_or(Fault::Input)?;
        let old = std::mem::replace(slot, id);
        if old != 0 { self.adjust_count(-1); }
        Ok(old)
    }
    pub(crate) fn adjust_count(&mut self, delta: i32) {
        self.count = self.count.wrapping_add(delta);
    }
    pub(crate) fn position_changed(&mut self, position: (i32,i32)) {
        self.position_fingerprint ^= mix_position(position);
    }
    pub(crate) fn empty_changed(&mut self, key: u64, empty: bool) -> bool {
        if empty { self.empty.add(key) } else { self.empty.remove(key) }
    }
    /// Resume a stock scan only after its external callback has completed.
    pub(crate) fn scan_empty(&mut self,id:u64,source:&impl Source,start:usize,mode:i32)
        -> Result<Option<(u64,usize)>,Fault> {
        for row in start..source.sections(id) {
            let key=section_key(source.position(id),source.section_y(id,row));
            if mode==0 {self.empty.remove(key);}
            else if source.empty(id,row)? {self.empty.add(key);}
            else if mode==2 && self.empty.remove(key) {return Ok(Some((key,row+1)));}
        }
        Ok(None)
    }
    /// Publication precedes removal/unload, and new count/admission follow it.
    /// Failure retains the original completed prefix rather than rolling back.
    pub(crate) fn replace(&mut self, index: usize, id: u64, source: &impl Source,
        mut callback: impl FnMut(Event) -> Result<(), Fault>) -> Result<(), Fault> {
        let old = self.publish(index, id)?;
        if old != 0 {
            self.position_changed(source.position(old));
            self.drop_empty(old, source);
            callback(Event::Unload(old))?;
        }
        if id != 0 {
            self.adjust_count(1);
            self.position_changed(source.position(id));
            self.scan_empty(id,source,0,1)?;
        }
        Ok(())
    }
    /// Failed CAS still unloads and invokes Java drop hooks. The adapter owns
    /// those hook calls; callbacks occur even when the supplied ID was stale.
    pub(crate) fn drop_chunk(&mut self, index: usize, id: u64, source: &impl Source,
        mut callback: impl FnMut(Event) -> Result<(), Fault>) -> Result<(), Fault> {
        if id == 0 { return Err(Fault::Input); }
        let slot = self.slots.get_mut(index).ok_or(Fault::Input)?;
        if *slot == id {
            *slot = 0; self.count = self.count.wrapping_sub(1);
            self.position_fingerprint ^= mix_position(source.position(id));
            self.drop_empty(id, source);
        }
        callback(Event::Unload(id))
    }
    fn drop_empty(&mut self, id: u64, source: &impl Source) {
        self.scan_empty(id,source,0,0).unwrap();
    }
    pub(crate) fn refresh(&mut self, id: u64, source: &impl Source,
        mut callback: impl FnMut(Event) -> Result<(), Fault>) -> Result<(), Fault> {
        let mut cursor=0;
        while let Some((key,next))=self.scan_empty(id,source,cursor,2)? {
            callback(Event::NonEmpty(key))?;cursor=next;
        }
        Ok(())
    }
    pub(crate) fn section_changed(&mut self, x: i32, y: i32, z: i32, empty: bool,
        mut callback: impl FnMut(Event) -> Result<(), Fault>) -> Result<(), Fault> {
        if self.in_range(x, z) {
            let key = section_key((x, z), y);
            if empty { self.empty.add(key); }
            else if self.empty.remove(key) { callback(Event::NonEmpty(key))?; }
        }
        Ok(())
    }
}
fn section_key((x,z): (i32,i32), y: i32) -> u64 {
    ((x as u64 & 0x3fffff) << 42) | ((z as u64 & 0x3fffff) << 20) | (y as u64 & 0xfffff)
}
fn mix_position((x,z): (i32,i32)) -> u64 {
    let mut v = x as u32 as u64 | ((z as u32 as u64) << 32);
    v ^= v >> 30; v = v.wrapping_mul(0xbf58476d1ce4e5b9);
    v ^= v >> 27; v = v.wrapping_mul(0x94d049bb133111eb);
    v ^ (v >> 31)
}
#[cfg(test)] mod tests;

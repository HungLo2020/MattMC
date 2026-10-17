//! Ordered component patch authority, including the original explicit COW points.
//! A Java equality/prototype callback may change sharing after `prepare_change`.
//! Consequently writes after that callback must NOT automatically detach again.
use std::sync::{Arc, Mutex};
use std::collections::HashMap;
use super::components::Payload;
use std::sync::atomic::{AtomicU64, AtomicUsize, AtomicBool, Ordering};

#[repr(transparent)]
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub struct Key(pub u64);
#[repr(transparent)]
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub struct Leaf(pub u64);
#[repr(C, u64)]
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Value { Removed = 0, Present(Leaf) = 1 }
#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Entry { pub key: Key, pub value: Value }

/// Sharing this backing is observable through built patches and escaped views.
#[derive(Clone, Debug)]
pub struct Patch(Arc<Backing>, Option<Arc<AtomicBool>>);
#[derive(Debug)]
struct Backing { version: u64, contents: Mutex<Contents>, view: ReadView, pristine: Arc<AtomicBool> }
#[derive(Clone, Debug)]
struct Contents { entries: Vec<Entry>, payloads: HashMap<Leaf, Payload> }
impl Contents {
    fn retire(&mut self, value: Value) {
        if let Value::Present(leaf) = value {
            if !self.entries.iter().any(|entry| entry.value == Value::Present(leaf)) { self.payloads.remove(&leaf); }
        }
    }
}
/// Java may read only under the shared root guard used by every mutation/alias.
/// The retained Patch Arc pins this header; it points at canonical rows, not a copy.
#[repr(C)]
#[derive(Debug)]
struct ReadView { rows: AtomicUsize, length: AtomicUsize }
impl ReadView {
    fn new(entries: &[Entry]) -> Self { Self { rows: AtomicUsize::new(entries.as_ptr() as usize), length: AtomicUsize::new(entries.len()) } }
    fn publish(&self, entries: &[Entry]) {
        self.rows.store(entries.as_ptr() as usize, Ordering::Release);
        self.length.store(entries.len(), Ordering::Release);
    }
}
static NEXT_VERSION: AtomicU64 = AtomicU64::new(1);
impl Default for Patch { fn default() -> Self { Self::from_entries(Vec::new()) } }
impl Patch {
    fn from_entries(entries: Vec<Entry>) -> Self {
        let pristine=Arc::new(AtomicBool::new(entries.is_empty()));Self::from_entries_marked(entries,pristine)
    }
    fn from_entries_marked(entries:Vec<Entry>,pristine:Arc<AtomicBool>)->Self {
        Self::from_contents_marked(Contents { entries, payloads: HashMap::new() }, pristine)
    }
    fn from_contents_marked(contents:Contents,pristine:Arc<AtomicBool>)->Self {
        let version = NEXT_VERSION.fetch_update(Ordering::Relaxed, Ordering::Relaxed,
            |id| id.checked_add(1).filter(|next| *next <= i64::MAX as u64))
            .expect("item patch version space exhausted");
        let view = ReadView::new(&contents.entries);
        Self(Arc::new(Backing { version, contents: Mutex::new(contents), view, pristine }),None)
    }
    pub fn pristine_marker(&self)->Arc<AtomicBool> {self.0.pristine.clone()}
    fn with_control(mut self,control:Arc<AtomicBool>)->Self {self.1=Some(control);self}
    fn invalidate(&self) {self.0.pristine.store(false,Ordering::Release);if let Some(control)=&self.1 {control.store(false,Ordering::Release);}}
    /// Facades sharing this version share one precise opaque-leaf root bag.
    pub fn view_address(&self) -> usize { &self.0.view as *const ReadView as usize }
    pub fn version(&self) -> u64 { self.0.version }
    pub fn shares_backing(&self, other: &Self) -> bool { Arc::ptr_eq(&self.0, &other.0) }
    /// Return a result only if the original Optional/value comparisons cannot
    /// invoke user code. Never compare opaque leaf numbers across backings.
    pub fn pure_equal(&self, other: &Self) -> Option<bool> { self.pure_equal_with(other, false).ok().flatten() }
    /// The same comparison taking no blocking lock: Err on contention, Ok(None)
    /// when a value needs the original Java equals (a definite answer).
    pub fn pure_equal_bounded(&self, other: &Self) -> Result<Option<bool>,()> { self.pure_equal_with(other, true) }
    fn pure_equal_with(&self, other: &Self, bounded: bool) -> Result<Option<bool>,()> {
        if self.shares_backing(other) { return Ok(Some(true)); }
        let (a,b,swapped)=if Arc::as_ptr(&self.0) < Arc::as_ptr(&other.0) {(&self.0,&other.0,false)} else {(&other.0,&self.0,true)};
        let (a,b)=if bounded {(a.contents.try_lock().map_err(|_|())?,b.contents.try_lock().map_err(|_|())?)} else {(a.contents.lock().unwrap(),b.contents.lock().unwrap())};
        Self::compare_contents(&a,&b,swapped,bounded)
    }
    /// Patches above this size are not compared by the bounded entry: its work is
    /// at most BOUNDED_ENTRIES^2 key comparisons plus 2*BOUNDED_ENTRIES payload lookups.
    pub const BOUNDED_ENTRIES: usize = 16;
    /// Payload tables whose retained capacity exceeds this (after earlier growth)
    /// are declined too, so each bounded lookup probes a table of bounded size.
    pub const BOUNDED_PAYLOAD_CAPACITY: usize = 64;
    /// Err: the bounded entry cannot decide without unbounded work or a blocking
    /// lock (the regular entry retries). Ok(None): a value needs the original Java equals.
    fn compare_contents(a:&Contents,b:&Contents,swapped:bool,bounded:bool) -> Result<Option<bool>,()> {
        let (left,right)=if swapped {(&*b,&*a)} else {(&*a,&*b)};
        if bounded && (left.entries.len()>Self::BOUNDED_ENTRIES || right.entries.len()>Self::BOUNDED_ENTRIES
            || left.payloads.capacity()>Self::BOUNDED_PAYLOAD_CAPACITY || right.payloads.capacity()>Self::BOUNDED_PAYLOAD_CAPACITY) {return Err(());}
        if left.entries.len()!=right.entries.len(){return Ok(Some(false));}
        let mut equal=true;
        for entry in &right.entries {
            let Some(left_entry)=left.entries.iter().find(|left|left.key==entry.key) else {equal=false;continue;};
            let compared=match (left_entry.value,entry.value) {
                (Value::Removed,Value::Removed)=>Some(true),
                (Value::Removed,Value::Present(_))=>Some(false),
                (Value::Present(l),Value::Removed)=>match left.payloads.get(&l) {
                    Some(Payload::Integer(_)|Payload::Enchantments(_))=>Some(false),_=>None,
                },
                (Value::Present(l),Value::Present(r))=>match (left.payloads.get(&l),right.payloads.get(&r)) {
                    (Some(Payload::Integer(a)),Some(Payload::Integer(b)))=>Some(a==b),
                    (Some(Payload::Integer(_)),_)=>Some(false),
                    (Some(Payload::Enchantments(a)),Some(Payload::Enchantments(b)))=>{
                        // size() takes the shape's blocking lock: the bounded entry declines.
                        if Arc::ptr_eq(a,b){Some(true)}else if bounded{return Err(())}else{let sa=a.size();let sb=b.size();if sa!=sb{Some(false)}else if sa==0{Some(true)}else{None}}
                    },
                    _=>None,
                },
            };
            // A later unknown pair may have preceded an earlier mismatch in the
            // original right-map traversal. Decline the whole comparison then.
            let Some(compared)=compared else{return Ok(None)};
            equal &= compared;
        }
        Ok(Some(equal))
    }
    pub fn len(&self) -> usize { self.0.contents.lock().unwrap().entries.len() }
    pub fn is_empty(&self) -> bool { self.len() == 0 }
    pub fn get(&self, key: Key) -> Option<Value> {
        self.0.contents.lock().unwrap().entries.iter().find(|entry| entry.key == key).map(|entry| entry.value)
    }
    pub fn get_payload(&self, key: Key) -> Option<(Value, Option<Payload>)> {
        let contents = self.0.contents.lock().unwrap();
        let value = contents.entries.iter().find(|entry| entry.key == key)?.value;
        let payload = match value { Value::Present(leaf) => contents.payloads.get(&leaf).cloned(), Value::Removed => None };
        Some((value, payload))
    }
    /// Read at the live cursor only when the caller is ready for this entry.
    pub fn at(&self, index: usize) -> Option<Entry> { self.0.contents.lock().unwrap().entries.get(index).copied() }
    pub fn put(&self, key: Key, value: Value) -> Option<Value> {
        self.put_payload(key, value, None).expect("item payload allocation failed before publication")
    }
    /// Native and VM staging have completed before publication. Reserve native
    /// storage first so a recoverable reserve failure cannot publish membership.
    pub fn put_payload(&self, key: Key, value: Value, payload: Option<Payload>) -> Result<Option<Value>, ()> {
        let mut contents = self.0.contents.lock().unwrap();
        let index = contents.entries.iter().position(|entry| entry.key == key);
        // The published read view addresses entries directly. Complete every
        // other fallible reservation before a successful entry-buffer move;
        // no error may leave that view pointing at the freed old allocation.
        if payload.is_some() { contents.payloads.try_reserve(1).map_err(|_| ())?; }
        if index.is_none() { contents.entries.try_reserve(1).map_err(|_| ())?; }
        self.invalidate();
        if let Value::Present(leaf) = value {
            if let Some(payload) = payload { contents.payloads.insert(leaf, payload); } else { contents.payloads.remove(&leaf); }
        }
        let old = if let Some(index) = index { Some(std::mem::replace(&mut contents.entries[index].value, value)) }
            else { contents.entries.push(Entry { key, value }); None };
        if let Some(old) = old { contents.retire(old); }
        self.0.view.publish(&contents.entries); Ok(old)
    }
    pub fn remove(&self, key: Key) -> Option<Value> {
        self.invalidate();
        let mut contents = self.0.contents.lock().unwrap();
        let index = contents.entries.iter().position(|entry| entry.key == key)?;
        let old = contents.entries.remove(index).value; contents.retire(old); self.0.view.publish(&contents.entries); Some(old)
    }
    pub fn clear(&self) { self.invalidate(); let mut contents = self.0.contents.lock().unwrap(); contents.entries.clear(); contents.payloads.clear(); self.0.view.publish(&contents.entries); }
    pub fn detached(&self) -> Self {
        let contents=self.0.contents.lock().unwrap().clone();
        // Empty COW descendants share a conservative invalidation lineage. A
        // later alias write invalidates every admission captured before the split;
        // the marker retains no backing, rows, payloads or Java object.
        let marker=if contents.entries.is_empty(){self.0.pristine.clone()}else{Arc::new(AtomicBool::new(false))};
        Self::from_contents_marked(contents,marker)
    }
}

#[derive(Debug)]
pub struct Overlay {
    pub prototype: u64,
    pub defaults: Option<Arc<super::slots::BoundaryOwner<defaults::Store>>>,
    patch: Patch,
    copy_on_write: bool,
    pub pristine: Arc<AtomicBool>,
}
impl Overlay {
    pub fn new(prototype: u64) -> Self { Self { prototype, defaults: None, patch: Patch::default(), copy_on_write: true, pristine: Arc::new(AtomicBool::new(true)) } }
    /// Only after the caller has completed the original sanitization callbacks.
    pub fn from_sanitized(prototype: u64, patch: Patch) -> Self { Self { prototype, defaults: None, pristine: Arc::new(AtomicBool::new(patch.0.pristine.load(Ordering::Acquire))), patch, copy_on_write: true } }
    pub fn get_override(&self, key: Key) -> Option<Value> { self.patch.get(key) }
    pub fn get_override_payload(&self, key: Key) -> Option<(Value, Option<Payload>)> { self.patch.get_payload(key) }
    pub fn shares_patch(&self, other: &Self) -> bool { self.patch.shares_backing(&other.patch) }
    /// Retain a live iterator backing without changing the explicit Java COW flag.
    pub fn view_patch(&self) -> Patch { self.patch.clone().with_control(self.pristine.clone()) }
    /// The live patch, borrowed (comparison only; no control marker or clone).
    pub fn patch_ref(&self) -> &Patch { &self.patch }
    pub fn backing_marker(&self)->Arc<AtomicBool> {self.patch.pristine_marker()}
    pub fn patch_len(&self) -> usize { self.patch.len() }
    pub fn patch_version(&self) -> u64 { self.patch.version() }
    /// The original operation's COW point, before prototype.get / value.equals.
    pub fn prepare_change(&mut self) {
        if self.copy_on_write { self.patch = self.patch.detached(); self.copy_on_write = false; }
    }
    /// Build the actual next backing without publishing it. Java must finish
    /// its matching opaque-root bag before this becomes the owner's root.
    pub fn needs_change(&self)->bool{self.copy_on_write}
    pub fn stage_change(&self)->Option<Patch>{self.copy_on_write.then(||self.patch.detached())}
    pub fn commit_change(&mut self,expected:u64,staged:Patch)->bool {
        if !self.copy_on_write||self.patch.version()!=expected||staged.version()==expected{return false;}
        self.patch=staged;self.copy_on_write=false;true
    }
    /// Source lookup/equality runs between prepare_change and this call, without a lock.
    /// A tombstone's previous set result is the prototype, unlike remove's result.
    pub fn set_after_compare(&mut self, key: Key, value: Option<Leaf>, prototype: Option<Leaf>, equal: bool) -> Option<Leaf> {
        self.set_after_compare_payload(key, value, prototype, equal, None).expect("component payload allocation failed before publication")
    }
    pub fn set_after_compare_payload(&mut self, key: Key, value: Option<Leaf>, prototype: Option<Leaf>, equal: bool, payload: Option<Payload>) -> Result<Option<Leaf>, ()> {
        self.pristine.store(false,Ordering::Release);
        let previous = if equal { self.patch.remove(key) }
            else { self.patch.put_payload(key, value.map(Value::Present).unwrap_or(Value::Removed), payload)? };
        Ok(match previous { Some(Value::Present(old)) => Some(old), _ => prototype })
    }
    pub fn remove_after_lookup(&mut self, key: Key, prototype: Option<Leaf>) -> Option<Leaf> {
        self.pristine.store(false,Ordering::Release);
        let previous = if prototype.is_some() { self.patch.put(key, Value::Removed) } else { self.patch.remove(key) };
        match previous { Some(Value::Present(old)) => Some(old), Some(Value::Removed) => None, None => prototype }
    }
    /// applyPatch prepares once; each source row keeps its original callback position.
    pub fn apply_after_compare(&mut self, entry: Entry, prototype_exists: bool, equal: bool) {
        self.apply_after_compare_payload(entry, prototype_exists, equal, None).expect("component payload allocation failed before publication");
    }
    pub fn apply_after_compare_payload(&mut self, entry: Entry, prototype_exists: bool, equal: bool, payload: Option<Payload>) -> Result<(), ()> {
        self.pristine.store(false,Ordering::Release);
        match entry.value {
            Value::Present(_) if equal => { self.patch.remove(entry.key); }
            Value::Present(_) => { self.patch.put_payload(entry.key, entry.value, payload)?; }
            Value::Removed if prototype_exists => { self.patch.put(entry.key, Value::Removed); }
            Value::Removed => { self.patch.remove(entry.key); }
        }
        Ok(())
    }
    pub fn copy(&mut self) -> Self {
        self.copy_on_write = true;
        Self { prototype: self.prototype, defaults: self.defaults.clone(), patch: self.patch.clone(), copy_on_write: true, pristine: Arc::new(AtomicBool::new(self.pristine.load(Ordering::Acquire) && self.patch.0.pristine.load(Ordering::Acquire))) }
    }
    /// None means Java's singleton EMPTY, which does not change this COW flag.
    pub fn as_patch(&mut self) -> Option<Patch> {
        if self.patch.is_empty() { None } else { self.copy_on_write = true; Some(self.patch.clone().with_control(self.pristine.clone())) }
    }
    pub fn clear(&mut self) { self.pristine.store(false,Ordering::Release); self.prepare_change(); self.patch.clear(); }
    pub fn restore(&mut self, source: &Patch) {
        self.pristine.store(false,Ordering::Release);
        self.prepare_change(); self.patch.clear();
        let mut index = 0;
        while let Some(entry) = source.at(index) {
            let payload = source.get_payload(entry.key).and_then(|(_, payload)| payload);
            self.patch.put_payload(entry.key, entry.value, payload).expect("component payload allocation failed before publication"); index += 1;
        }
    }
}

#[cfg(test)] mod tests {
    use super::*;
    const A: Key = Key(1); const B: Key = Key(2); const BASE: Leaf = Leaf(10); const NEW: Leaf = Leaf(20);
    fn put(map: &mut Overlay, key: Key, value: Leaf) { map.prepare_change(); map.set_after_compare(key, Some(value), None, false); }
    #[test] fn equality_callback_can_expose_the_write_before_a_later_detach() {
        let mut map = Overlay::new(1); put(&mut map, B, BASE);
        map.prepare_change(); // Java prototype.get/equals now executes.
        let escaped = map.as_patch().unwrap(); let mut copied = map.copy();
        assert_eq!(map.patch_version(), escaped.version());
        assert_eq!(copied.patch_version(), escaped.version());
        map.set_after_compare(A, Some(NEW), Some(BASE), false);
        assert_eq!(escaped.get(A), Some(Value::Present(NEW)));
        assert_eq!(copied.get_override(A), Some(Value::Present(NEW)));
        put(&mut map, B, NEW);
        assert_ne!(map.patch_version(), escaped.version());
        assert_eq!(escaped.get(B), Some(Value::Present(BASE)));
        assert_eq!(copied.get_override(B), Some(Value::Present(BASE)));
        copied.prepare_change(); copied.remove_after_lookup(A, None);
        assert_eq!(map.get_override(A), Some(Value::Present(NEW)));
    }
    #[test] fn nested_callback_mutation_detaches_before_the_outer_write() {
        let mut map = Overlay::new(1); put(&mut map, B, BASE); map.prepare_change();
        let escaped = map.as_patch().unwrap(); let copied = map.copy();
        put(&mut map, B, NEW); // Reentrant mutation performs its own COW point.
        map.set_after_compare(A, Some(NEW), Some(BASE), false);
        assert_eq!(escaped.get(A), None); assert_eq!(copied.get_override(A), None);
        assert_eq!(map.get_override(A), Some(Value::Present(NEW)));
    }
    // The bounded entry compares at most BOUNDED_ENTRIES per side; a larger patch
    // that the regular entry decides natively is declined (Err) before any scan.
    #[test] fn bounded_comparison_declines_patches_above_its_work_bound() {
        let (a, b) = (Patch::default(), Patch::default());
        for key in 0..Patch::BOUNDED_ENTRIES as u64 { a.put(Key(1000 + key), Value::Removed); b.put(Key(1000 + key), Value::Removed); }
        assert_eq!(a.pure_equal_bounded(&b), Ok(Some(true)), "at the bound: decided");
        a.put(Key(5000), Value::Removed); b.put(Key(5000), Value::Removed);
        assert_eq!(a.pure_equal_bounded(&b), Err(()), "above the bound: declined before scanning");
        assert_eq!(a.pure_equal(&b), Some(true), "the regular entry still decides");
    }
    // Two distinct enchantment stores would need size() (the shape's blocking
    // lock): the bounded entry declines before it, even while that lock is held.
    #[test] fn bounded_comparison_declines_distinct_enchantments_without_waiting() {
        let (x, y) = (Arc::new(super::super::components::Enchantments::new(2)), Arc::new(super::super::components::Enchantments::new(2)));
        let (a, b) = (Patch::default(), Patch::default());
        a.put_payload(Key(7), Value::Present(Leaf(70)), Some(Payload::Enchantments(x.clone()))).unwrap();
        b.put_payload(Key(7), Value::Present(Leaf(71)), Some(Payload::Enchantments(y.clone()))).unwrap();
        let held = x.hold_shape_for_test();
        let (send, receive) = std::sync::mpsc::channel();
        let (left, right) = (a.clone(), b.clone());
        std::thread::spawn(move || { send.send(left.pure_equal_bounded(&right)).unwrap(); });
        assert_eq!(receive.recv_timeout(std::time::Duration::from_secs(1)), Ok(Err(())), "declined without waiting on the held shape lock");
        drop(held);
        assert_eq!(a.pure_equal(&b), Some(true), "the regular entry decides two empty stores (size 0)");
        let same = Patch::default(); same.put_payload(Key(7), Value::Present(Leaf(72)), Some(Payload::Enchantments(x.clone()))).unwrap();
        let _held = x.hold_shape_for_test();
        assert_eq!(a.pure_equal_bounded(&same), Ok(Some(true)), "identical stores decide without the lock");
    }
    // A payload table that grew and shrank keeps its capacity: the bounded entry
    // declines it before any lookup, whatever the current entry count.
    #[test] fn bounded_comparison_declines_payload_tables_with_large_retained_capacity() {
        let (a, b) = (Patch::default(), Patch::default());
        for id in 0..200 { a.put_payload(Key(id), Value::Present(Leaf(1000 + id)), Some(Payload::Integer(id as i32))).unwrap(); }
        for id in 1..200 { a.remove(Key(id)); }
        b.put_payload(Key(0), Value::Present(Leaf(5000)), Some(Payload::Integer(0))).unwrap();
        assert_eq!(a.len(), 1);
        assert_eq!(a.pure_equal_bounded(&b), Err(()), "large retained payload capacity: declined before lookup");
        assert_eq!(a.pure_equal(&b), Some(true), "the regular entry still decides");
    }
    #[test] fn tombstone_set_and_remove_have_different_previous_results() {
        let mut map = Overlay::new(1); map.prepare_change();
        assert_eq!(map.remove_after_lookup(A, Some(BASE)), Some(BASE));
        assert_eq!(map.remove_after_lookup(A, Some(BASE)), None);
        assert_eq!(map.set_after_compare(A, Some(NEW), Some(BASE), false), Some(BASE));
        assert_eq!(map.set_after_compare(A, Some(BASE), Some(BASE), true), Some(NEW));
        assert_eq!(map.get_override(A), None);
    }
    #[test] fn escaped_patch_removal_is_shared_and_builders_keep_their_backing() {
        let builder = Patch::default(); builder.put(A, Value::Present(BASE)); let built = builder.clone();
        builder.put(B, Value::Removed); let mut map = Overlay::from_sanitized(1, built.clone());
        let escaped = map.as_patch().unwrap(); let copied = map.copy(); escaped.remove(A);
        assert_eq!(builder.get(A), None); assert_eq!(map.get_override(A), None); assert_eq!(copied.get_override(A), None);
        assert_eq!(built.at(0), Some(Entry { key: B, value: Value::Removed }));
        map.clear(); assert_eq!(map.patch_len(), 0); assert_eq!(builder.len(), 1);
    }
    #[test] fn retained_read_view_layout_tracks_canonical_rows_and_reallocation() {
        assert_eq!(std::mem::size_of::<Entry>(), 24); assert_eq!(std::mem::offset_of!(Entry, value), 8);
        assert_eq!(std::mem::size_of::<ReadView>(), 16);
        let patch = Patch::default(); let address = patch.view_address(); let retained = patch.clone();
        for id in 1..=1000 { patch.put(Key(id), Value::Present(Leaf(id + 10))); }
        assert_eq!(address, patch.view_address()); drop(patch);
        let view = unsafe { &*(address as *const ReadView) };
        assert_eq!(view.length.load(Ordering::Acquire), 1000);
        let rows = unsafe { std::slice::from_raw_parts(view.rows.load(Ordering::Acquire) as *const Entry, 1000) };
        assert_eq!(rows[999].key, Key(1000));
        let raw = rows.as_ptr().cast::<u64>();
        assert_eq!(unsafe { *raw.add(1) }, 1); assert_eq!(unsafe { *raw.add(2) }, 11);
        retained.put(Key(1), Value::Removed); assert_eq!(unsafe { *raw.add(1) }, 0);
        retained.remove(Key(1)); assert_eq!(view.length.load(Ordering::Acquire), 999);
        retained.clear(); assert_eq!(view.length.load(Ordering::Acquire), 0);
    }
    #[test] fn iteration_view_does_not_create_a_new_copy_on_write_point() {
        let mut map = Overlay::new(1); put(&mut map, A, BASE);
        let view = map.view_patch(); let version = view.version();
        map.prepare_change(); map.set_after_compare(B, Some(NEW), None, false);
        assert_eq!(map.patch_version(), version); assert_eq!(view.get(B), Some(Value::Present(NEW)));
        let escaped = map.as_patch().unwrap(); map.prepare_change();
        assert_ne!(map.patch_version(), version); assert_eq!(escaped.version(), version);
    }
    #[test] fn replacement_keeps_position_and_remove_then_put_appends() {
        let patch = Patch::default(); patch.put(A, Value::Present(BASE)); patch.put(B, Value::Removed);
        patch.put(A, Value::Present(NEW)); assert_eq!(patch.at(0).unwrap().key, A);
        patch.remove(A); patch.put(A, Value::Removed); assert_eq!(patch.at(0).unwrap().key, B); assert_eq!(patch.at(1).unwrap().key, A);
    }
}

/// Isolated access-architecture lower bound. Fixed allocation, one authoritative
/// atomic membership/leaf token per cell; not a full ordered component overlay.
/// 0=absent, 1=tombstone, larger values=opaque Java binding incarnations.
pub struct AtomicAccess { cells: Box<[AtomicU64]> }
impl AtomicAccess {
    pub fn new(length: usize) -> Option<Self> {
        length.checked_mul(std::mem::size_of::<AtomicU64>())?;
        let mut cells = Vec::new(); cells.try_reserve_exact(length).ok()?;
        cells.extend((0..length).map(|_| AtomicU64::new(0)));
        Some(Self { cells: cells.into_boxed_slice() })
    }
    pub fn address(&self) -> usize { self.cells.as_ptr() as usize }
}

/// Full dynamic-key access experiment. Not selected by production overlays.
/// The mutable hash index and order belong to this native backing. Old indexes
/// survive growth until the actual backing dies, so a pinned reader never
/// dereferences freed memory. Only geometric growth retires an index.
#[repr(C)]
struct AccessSlot { key: AtomicU64, value: AtomicU64 }
#[repr(C)]
struct AccessTable { mask: usize, rows: usize, slots: Box<[AccessSlot]> }
impl AccessTable {
    fn new(capacity: usize) -> Self {
        let slots: Box<[AccessSlot]> = (0..capacity).map(|_| AccessSlot { key: AtomicU64::new(0), value: AtomicU64::new(0) }).collect();
        Self { mask: capacity - 1, rows: slots.as_ptr() as usize, slots }
    }
    fn position(&self, key: u64) -> (usize, bool) {
        let mask = self.slots.len() - 1;
        let mut index = access_hash(key) as usize & mask;
        let mut deleted = None;
        for _ in 0..self.slots.len() {
            let found = self.slots[index].key.load(Ordering::Relaxed);
            if found == key { return (index, true); }
            if found == 0 { return (deleted.unwrap_or(index), false); }
            if found == u64::MAX && deleted.is_none() { deleted = Some(index); }
            index = (index + 1) & mask;
        }
        (deleted.expect("native access table has no insertion slot"), false)
    }
    fn put(&self, key: u64, value: u64) -> u64 {
        let (index, found) = self.position(key); let slot = &self.slots[index];
        let old = if found { slot.value.load(Ordering::Relaxed) } else { 0 };
        slot.value.store(value, Ordering::Release); slot.key.store(key, Ordering::Release); old
    }
    fn get(&self, key: u64) -> u64 {
        let (index, found) = self.position(key);
        if found { self.slots[index].value.load(Ordering::Relaxed) } else { 0 }
    }
}
fn access_hash(key: u64) -> u64 { let mixed = key.wrapping_mul(0x9e3779b97f4a7c15); mixed ^ (mixed >> 32) }
#[repr(C)]
struct AccessHeader { sequence: AtomicU64, slots: AtomicUsize, mask: AtomicUsize, length: AtomicUsize, inline: [AtomicU64; 4] }
// Immutable shared COW body. Backing identity remains mutable/shared by escaped
// views; detached backings share this body and add ordered edits independently.
enum OrderedBody {
    Flat(Vec<(Key, u64)>),
    Edit { base: Arc<OrderedBody>, key: Key, value: u64, length: usize, depth: usize },
}
impl OrderedBody {
    fn get(&self, key: Key) -> u64 {
        match self {
            Self::Flat(rows) => rows.iter().find(|row| row.0 == key).map(|row| row.1).unwrap_or(0),
            Self::Edit { base, key: edited, value, .. } => if *edited == key { *value } else { base.get(key) },
        }
    }
    fn len(&self) -> usize { match self { Self::Flat(rows) => rows.len(), Self::Edit { length, .. } => *length } }
    fn depth(&self) -> usize { match self { Self::Flat(_) => 0, Self::Edit { depth, .. } => *depth } }
    fn rows(&self) -> Vec<(Key, u64)> {
        match self {
            Self::Flat(rows) => rows.clone(),
            Self::Edit { base, key, value, .. } => { let mut rows = base.rows(); Self::edit_rows(&mut rows, *key, *value); rows }
        }
    }
    fn edit_rows(rows: &mut Vec<(Key, u64)>, key: Key, value: u64) {
        if let Some(index) = rows.iter().position(|row| row.0 == key) {
            if value == 0 { rows.remove(index); } else { rows[index].1 = value; }
        } else if value != 0 { rows.push((key, value)); }
    }
    fn change(body: &mut Arc<Self>, key: Key, value: u64) -> u64 {
        let old = body.get(key);
        if old == value { return old; }
        if let Some(unique) = Arc::get_mut(body) {
            match unique {
                Self::Flat(rows) => { Self::edit_rows(rows, key, value); return old; }
                // Replacements preserve order. A removal followed by reinsertion
                // must stay two edits so the reinsertion appends at the end.
                Self::Edit { key: last, value: previous, .. } if *last == key && *previous != 0 && value != 0 => {
                    *previous = value; return old;
                }
                _ => {}
            }
        }
        if body.depth() >= 32 {
            let mut rows = body.rows(); Self::edit_rows(&mut rows, key, value); *body = Arc::new(Self::Flat(rows));
        } else {
            let length = body.len() + usize::from(old == 0 && value != 0) - usize::from(old != 0 && value == 0);
            let depth = body.depth() + 1;
            *body = Arc::new(Self::Edit { base: body.clone(), key, value, length, depth });
        }
        old
    }
}
struct AccessState { table: Box<AccessTable>, retired: Vec<Box<AccessTable>>, order: Vec<Key>, body: Arc<OrderedBody> }
struct AccessBacking { version: u64, header: AccessHeader, state: Mutex<AccessState> }
#[derive(Clone)]
pub struct AccessPatch(Arc<AccessBacking>);
impl Default for AccessPatch {
    fn default() -> Self {
        let table = Box::new(AccessTable::new(8));
        let header = AccessHeader { sequence: AtomicU64::new(0), slots: AtomicUsize::new(&*table as *const AccessTable as usize),
            mask: AtomicUsize::new(7), length: AtomicUsize::new(0), inline: std::array::from_fn(|_| AtomicU64::new(0)) };
        let version = NEXT_VERSION.fetch_update(Ordering::Relaxed, Ordering::Relaxed,
            |id| id.checked_add(1).filter(|next| *next <= i64::MAX as u64)).expect("access version exhausted");
        Self(Arc::new(AccessBacking { version, header, state: Mutex::new(AccessState { table, retired: Vec::new(), order: Vec::new(), body: Arc::new(OrderedBody::Flat(Vec::new())) }) }))
    }
}
impl AccessPatch {
    pub fn version(&self) -> u64 { self.0.version }
    pub fn address(&self) -> usize { &self.0.header as *const AccessHeader as usize }
    pub fn len(&self) -> usize { self.0.header.length.load(Ordering::Acquire) }
    pub fn get(&self, key: Key) -> u64 { self.0.state.lock().unwrap().body.get(key) }
    /// 0 absent, 1 tombstone, >1 opaque incarnation. Key IDs are never 0/MAX.
    pub fn put(&self, key: Key, value: u64) -> u64 {
        assert!(key.0 != 0 && key.0 != u64::MAX);
        let mut state = self.0.state.lock().unwrap();
        let (_, found) = state.table.position(key.0);
        // Reserve all containers before publishing the write epoch.
        if !found && value != 0 { state.order.reserve(1); }
        let grown = if !found && value != 0 && (state.order.len() + 1) * 2 > state.table.slots.len() {
            let table = Box::new(AccessTable::new(state.table.slots.len().checked_mul(2).expect("access capacity overflow")));
            for key in &state.order { table.put(key.0, state.table.get(key.0)); }
            state.retired.reserve(1); Some(table)
        } else { None };
        let body_old = OrderedBody::change(&mut state.body, key, value);
        self.0.header.sequence.fetch_add(1, Ordering::AcqRel);
        if let Some(table) = grown {
            let old = std::mem::replace(&mut state.table, table); state.retired.push(old);
            self.0.header.slots.store(&*state.table as *const AccessTable as usize, Ordering::Release);
            self.0.header.mask.store(state.table.slots.len() - 1, Ordering::Release);
        }
        let old = if value == 0 {
            let (index, found) = state.table.position(key.0);
            if found {
                let old = state.table.slots[index].value.load(Ordering::Relaxed);
                state.table.slots[index].key.store(u64::MAX, Ordering::Release);
                state.table.slots[index].value.store(0, Ordering::Release);
                let index = state.order.iter().position(|found| *found == key).unwrap(); state.order.remove(index); old
            } else { 0 }
        } else {
            let old = state.table.put(key.0, value);
            if old == 0 { state.order.push(key); } old
        };
        debug_assert_eq!(old, body_old); debug_assert_eq!(state.order.len(), state.body.len());
        for index in 0..4 {
            let value = state.order.get(index).map(|key| state.body.get(*key)).unwrap_or(0);
            self.0.header.inline[index].store(value, Ordering::Release);
        }
        self.0.header.length.store(state.order.len(), Ordering::Release);
        self.0.header.sequence.fetch_add(1, Ordering::Release); old
    }
    pub fn at(&self, index: usize) -> Option<(Key, u64)> {
        let state = self.0.state.lock().unwrap(); state.order.get(index).map(|key| (*key, state.body.get(*key)))
    }
    pub fn detached(&self) -> Self {
        let mut state = self.0.state.lock().unwrap();
        // Normalize a shared source once. Otherwise every sibling's first edit
        // can compact the same deep prefix independently. This changes neither
        // backing identity nor visible tokens/order, including older children.
        if state.body.depth() != 0 {
            let rows = state.body.rows(); state.body = Arc::new(OrderedBody::Flat(rows));
        }
        // Copy only the native read projection in one pass. The ordered body is
        // shared, and the first subsequent edit does not rebuild its entries.
        let table = Box::new(AccessTable::new(state.table.slots.len()));
        for (to, from) in table.slots.iter().zip(state.table.slots.iter()) {
            to.key.store(from.key.load(Ordering::Relaxed), Ordering::Relaxed);
            to.value.store(from.value.load(Ordering::Relaxed), Ordering::Relaxed);
        }
        let version = NEXT_VERSION.fetch_update(Ordering::Relaxed, Ordering::Relaxed,
            |id| id.checked_add(1).filter(|next| *next <= i64::MAX as u64)).expect("access version exhausted");
        let header = AccessHeader { sequence: AtomicU64::new(0), slots: AtomicUsize::new(&*table as *const AccessTable as usize),
            mask: AtomicUsize::new(table.mask), length: AtomicUsize::new(state.order.len()),
            inline: std::array::from_fn(|index| AtomicU64::new(state.order.get(index).map(|key| state.body.get(*key)).unwrap_or(0))) };
        Self(Arc::new(AccessBacking { version, header, state: Mutex::new(AccessState {
            table, retired: Vec::new(), order: state.order.clone(), body: state.body.clone(),
        }) }))
    }
    #[cfg(test)] fn allocated_slots(&self) -> usize {
        let state = self.0.state.lock().unwrap(); state.table.slots.len() + state.retired.iter().map(|table| table.slots.len()).sum::<usize>()
    }
}
/// Explicit phase split preserves the same reentrant COW points as Overlay.
pub struct AccessOverlay { pub prototype: u64, patch: AccessPatch, cow: AtomicU64 }
impl AccessOverlay {
    pub fn new(prototype: u64) -> Self { Self { prototype, patch: AccessPatch::default(), cow: AtomicU64::new(1) } }
    pub fn view(&self) -> AccessPatch { self.patch.clone() }
    pub fn prepare(&mut self) -> u64 {
        if let Some(staged) = self.stage() { let expected = self.patch.version(); assert!(self.commit(expected, staged)); } self.patch.version()
    }
    pub fn cow_address(&self) -> usize { &self.cow as *const AtomicU64 as usize }
    pub fn stage(&self) -> Option<AccessPatch> { (self.cow.load(Ordering::Acquire) != 0).then(|| self.patch.detached()) }
    pub fn commit(&mut self, expected: u64, staged: AccessPatch) -> bool {
        if self.cow.load(Ordering::Acquire) == 0 || self.patch.version() != expected { return false; }
        self.patch = staged; self.cow.store(0, Ordering::Release); true
    }
    pub fn copy(&mut self) -> Self {
        self.cow.store(1, Ordering::Release); Self { prototype: self.prototype, patch: self.patch.clone(), cow: AtomicU64::new(1) }
    }
    pub fn escape(&mut self) -> Option<AccessPatch> {
        if self.patch.len() == 0 { None } else { self.cow.store(1, Ordering::Release); Some(self.patch.clone()) }
    }
    pub fn put_prepared(&self, key: Key, value: u64) -> u64 { self.patch.put(key, value) }
}
#[cfg(test)] mod access_tests {
    use super::*;
    #[test] fn dynamic_keys_growth_reuse_order_and_retained_atomic_indexes() {
        assert_eq!(std::mem::size_of::<AccessHeader>(), 64);
        assert_eq!(std::mem::size_of::<AccessSlot>(), 16);
        let patch = AccessPatch::default(); let header = patch.address();
        for id in 1..=1000 { assert_eq!(patch.put(Key(id), id + 1), 0); }
        let allocated = patch.allocated_slots(); assert!(allocated < 4096);
        for id in 1..=1000 { assert_eq!(patch.get(Key(id)), id + 1); }
        for round in 0..10_000 { let key = Key(10_000 + round); patch.put(key, 7); patch.put(key, 0); }
        assert_eq!(patch.address(), header); assert_eq!(patch.allocated_slots(), allocated);
        assert_eq!(patch.len(), 1000); assert_eq!(patch.at(999), Some((Key(1000), 1001)));
        patch.put(Key(1), 1); assert_eq!(patch.at(0), Some((Key(1), 1)));
        patch.put(Key(1), 0); patch.put(Key(1), 8); assert_eq!(patch.at(999), Some((Key(1), 8)));
    }
    #[test] fn staged_edit_failure_keeps_original_root_and_compaction_preserves_order() {
        let mut owner = AccessOverlay::new(71); owner.prepare(); owner.put_prepared(Key(1), 2);
        let escaped = owner.escape().unwrap(); let original = owner.view().version();
        let staged = owner.stage().unwrap(); assert_ne!(staged.version(), original); drop(staged);
        assert_eq!(owner.view().version(), original); escaped.put(Key(1), 3); assert_eq!(owner.view().get(Key(1)), 3);
        let staged = owner.stage().unwrap(); assert!(!owner.commit(original + 1000, staged.clone()));
        assert_eq!(owner.view().version(), original); assert!(owner.commit(original, staged));
        owner.put_prepared(Key(1), 4); escaped.put(Key(1), 5); assert_eq!(owner.view().get(Key(1)), 4);
        let mut expected = vec![(Key(1), 4)];
        for index in 0..500 {
            let key = Key((index * 17 % 47) + 1); let value = if index % 5 == 0 { 0 } else { index + 2 };
            let old = expected.iter().find(|row| row.0 == key).map(|row| row.1).unwrap_or(0);
            OrderedBody::edit_rows(&mut expected, key, value);
            let snapshot = owner.view().detached(); let rows = snapshot.0.state.lock().unwrap().body.rows();
            assert_eq!(owner.put_prepared(key, value), old);
            assert_eq!(snapshot.0.state.lock().unwrap().body.rows(), rows, "later parent writes must not alter detached child");
            let view = owner.view(); let state = view.0.state.lock().unwrap();
            assert_eq!(state.body.rows(), expected); assert!(state.body.depth() <= 32);
            for (key, value) in &expected { assert_eq!(state.table.get(key.0), *value); }
        }
    }
    #[test] fn sibling_cow_staging_normalizes_one_source_without_repeating_compaction() {
        let source = AccessPatch::default(); for key in 1..=32 { source.put(Key(key), key + 10); }
        let retained = source.detached();
        for key in 1..=32 { source.put(Key(key), key + 100); }
        assert_eq!(source.0.state.lock().unwrap().body.depth(), 32);
        let version = source.version(); let address = source.address();
        for id in 0..100 {
            let child = source.detached(); assert_eq!(source.0.state.lock().unwrap().body.depth(), 0);
            child.put(Key(1), id + 1000); assert_eq!(child.0.state.lock().unwrap().body.depth(), 1);
            assert_eq!(source.get(Key(1)), 101); assert_eq!(retained.get(Key(1)), 11);
            assert_eq!(source.version(), version); assert_eq!(source.address(), address);
        }
    }
    #[test] fn ordered_body_compaction_is_bounded_before_later_cow_normalization() {
        let source = AccessPatch::default(); source.put(Key(1), 2); let retained = source.detached();
        for key in 2..200 {
            source.put(Key(key), key + 10); assert!(source.0.state.lock().unwrap().body.depth() <= 32);
        }
        assert_eq!(retained.len(), 1); assert_eq!(retained.get(Key(1)), 2);
        assert_eq!(source.at(198), Some((Key(199), 209)));
    }
    #[test] fn full_access_preserves_pre_callback_detach_and_post_callback_aliases() {
        let mut owner = AccessOverlay::new(91); owner.prepare(); owner.put_prepared(Key(1), 2);
        owner.prepare(); let escaped = owner.escape().unwrap(); let mut copied = owner.copy();
        owner.put_prepared(Key(2), 3); assert_eq!(escaped.get(Key(2)), 3); assert_eq!(copied.view().get(Key(2)), 3);
        copied.prepare(); copied.put_prepared(Key(1), 1); assert_eq!(owner.view().get(Key(1)), 2);
        owner.prepare(); owner.put_prepared(Key(1), 4); assert_eq!(escaped.get(Key(1)), 2);
    }
}

// Canonical native entry cells. All structural producers and critical writers
// must share the caller's root serialization guard; Java holds its Roots monitor.
mod canonical_cells {
use super::{Key, NEXT_VERSION};
use std::sync::{Arc, Mutex};
use std::sync::atomic::{AtomicU64, AtomicUsize, Ordering};
static NEXT_CELL: AtomicU64 = AtomicU64::new(1);
#[repr(C)]
pub struct Cell {
    token: AtomicU64, key: AtomicU64, owner: u64,
    shared: AtomicU64, active: AtomicU64, incarnation: AtomicU64,
}
impl Cell {
    fn fresh(owner: u64, key: Key, value: u64) -> Self {
        Self { token: AtomicU64::new(value), key: AtomicU64::new(key.0), owner,
            shared: AtomicU64::new(0), active: AtomicU64::new(1), incarnation: AtomicU64::new(next_cell()) }
    }
    pub fn address(&self) -> usize { self as *const Self as usize }
    fn token(&self) -> u64 { self.token.load(Ordering::Acquire) }
    fn private(&self, owner: u64) -> bool {
        self.owner == owner && self.shared.load(Ordering::Acquire) == 0 && self.active.load(Ordering::Acquire) != 0
    }
}
fn next_cell() -> u64 { NEXT_CELL.fetch_update(Ordering::Relaxed, Ordering::Relaxed,
    |id| id.checked_add(1).filter(|next| *next <= i64::MAX as u64)).expect("cell incarnation exhausted") }
fn version() -> u64 { NEXT_VERSION.fetch_update(Ordering::Relaxed, Ordering::Relaxed,
    |id| id.checked_add(1).filter(|next| *next <= i64::MAX as u64)).expect("cell backing version exhausted") }
/// The caller pins the actual allocation and serializes alias writes, reuse and
/// new snapshot sharing through the SAME guard for admission AND replacement.
/// Returns None before any effect. No allocation, lock, lookup, callback or loop.
pub unsafe fn replace(cell: *const Cell, owner: u64, key: u64, incarnation: u64, expected: u64, next: u64) -> Option<u64> {
    if cell.is_null() || next <= 1 { return None; }
    let cell = &*cell;
    if cell.owner != owner || cell.key.load(Ordering::Acquire) != key
        || cell.incarnation.load(Ordering::Acquire) != incarnation
        || !cell.private(owner) || expected <= 1 || cell.token() != expected { return None; }
    Some(cell.token.swap(next, Ordering::AcqRel))
}
#[derive(Clone)]
enum Body {
    Flat(Vec<(Key, Arc<Cell>)>),
    Edit { base: Arc<Body>, key: Key, cell: Option<Arc<Cell>>, length: usize, depth: usize },
}
impl Body {
    fn get(&self, key: Key) -> Option<Arc<Cell>> {
        match self {
            Self::Flat(rows) => rows.iter().find(|row| row.0 == key).map(|row| row.1.clone()),
            Self::Edit { base, key: edited, cell, .. } => if key == *edited { cell.clone() } else { base.get(key) },
        }
    }
    fn len(&self) -> usize { match self { Self::Flat(rows) => rows.len(), Self::Edit { length, .. } => *length } }
    fn depth(&self) -> usize { match self { Self::Flat(_) => 0, Self::Edit { depth, .. } => *depth } }
    fn rows(&self) -> Vec<(Key, Arc<Cell>)> {
        match self {
            Self::Flat(rows) => rows.clone(),
            Self::Edit { base, key, cell, .. } => { let mut rows = base.rows(); Self::edit(&mut rows, *key, cell.clone()); rows }
        }
    }
    fn edit(rows: &mut Vec<(Key, Arc<Cell>)>, key: Key, cell: Option<Arc<Cell>>) {
        if let Some(index) = rows.iter().position(|row| row.0 == key) {
            if let Some(cell) = cell { rows[index].1 = cell; } else { rows.remove(index); }
        } else if let Some(cell) = cell { rows.push((key, cell)); }
    }
    fn change(body: &mut Arc<Self>, key: Key, cell: Option<Arc<Cell>>) {
        let had = body.get(key).is_some(); let has = cell.is_some();
        if !had && !has { return; }
        if let Some(unique) = Arc::get_mut(body) {
            match unique {
                Self::Flat(rows) => { Self::edit(rows, key, cell); return; }
                Self::Edit { key: last, cell: previous, .. } if *last == key && previous.is_some() && has => { *previous = cell; return; }
                _ => {}
            }
        }
        if body.depth() >= 32 {
            let mut rows = body.rows(); Self::edit(&mut rows, key, cell); *body = Arc::new(Self::Flat(rows));
        } else {
            let length = body.len() + usize::from(!had && has) - usize::from(had && !has);
            let depth = body.depth() + 1;
            *body = Arc::new(Self::Edit { base: body.clone(), key, cell, length, depth });
        }
    }
}
#[repr(C)]
struct Slot { key: AtomicU64, cell: AtomicUsize }
#[repr(C)]
struct Table { mask: usize, rows: usize, slots: Box<[Slot]> }
fn hash(key: u64) -> usize { let mixed = key.wrapping_mul(0x9e3779b97f4a7c15); (mixed ^ (mixed >> 32)) as usize }
impl Table {
    fn new(capacity: usize) -> Self {
        let slots: Box<[Slot]> = (0..capacity).map(|_| Slot { key: AtomicU64::new(0), cell: AtomicUsize::new(0) }).collect();
        Self { mask: capacity - 1, rows: slots.as_ptr() as usize, slots }
    }
    fn position(&self, key: u64) -> (usize, bool) {
        let mut index = hash(key) & self.mask; let mut deleted = None;
        for _ in 0..self.slots.len() {
            let found = self.slots[index].key.load(Ordering::Relaxed);
            if found == key { return (index, true); }
            if found == 0 { return (deleted.unwrap_or(index), false); }
            if found == u64::MAX && deleted.is_none() { deleted = Some(index); }
            index = (index + 1) & self.mask;
        }
        (deleted.expect("canonical table full"), false)
    }
    fn put(&self, key: u64, cell: usize) {
        let (index, _) = self.position(key); self.slots[index].cell.store(cell, Ordering::Release); self.slots[index].key.store(key, Ordering::Release);
    }
    fn remove(&self, key: u64) {
        let (index, found) = self.position(key);
        if found { self.slots[index].key.store(u64::MAX, Ordering::Release); self.slots[index].cell.store(0, Ordering::Release); }
    }
}
#[repr(C)]
struct Header { sequence: AtomicU64, descriptor: AtomicUsize, mask: AtomicUsize, length: AtomicUsize, inline: [AtomicUsize; 4] }
struct State {
    body: Arc<Body>, order: Vec<Key>, table: Box<Table>, retired_tables: Vec<Box<Table>>,
    // Owned arena cells stay allocated through this backing's scope. A raw reader
    // may hold their address while a no-longer-referenced slot is reused.
    owned: Vec<Arc<Cell>>,
    // Pin inherited cells BEFORE removing the body reference that may be their
    // last owner. A root lease alone would not otherwise pin that allocation.
    retired_foreign: Vec<Arc<Cell>>,
}
struct Backing { version: u64, header: Header, state: Mutex<State> }
impl Drop for Backing {
    fn drop(&mut self) {
        let state = self.state.get_mut().unwrap_or_else(|poisoned| poisoned.into_inner());
        for cell in &state.owned { cell.active.store(0, Ordering::Release); }
    }
}
#[derive(Clone)]
pub struct AccessPatch(Arc<Backing>);
impl AccessPatch {
    fn build(version: u64, body: Arc<Body>, order: Vec<Key>, table: Box<Table>) -> Self {
        let header = Header { sequence: AtomicU64::new(0), descriptor: AtomicUsize::new(&*table as *const Table as usize),
            mask: AtomicUsize::new(table.mask), length: AtomicUsize::new(order.len()),
            inline: std::array::from_fn(|index| AtomicUsize::new(order.get(index).and_then(|key| body.get(*key)).map(|cell| cell.address()).unwrap_or(0))) };
        Self(Arc::new(Backing { version, header, state: Mutex::new(State { body, order, table, retired_tables: Vec::new(), owned: Vec::new(), retired_foreign: Vec::new() }) }))
    }
    pub fn version(&self) -> u64 { self.0.version }
    pub fn address(&self) -> usize { &self.0.header as *const Header as usize }
    pub fn len(&self) -> usize { self.0.header.length.load(Ordering::Acquire) }
    pub fn get(&self, key: Key) -> u64 { self.0.state.lock().unwrap().body.get(key).map(|cell| cell.token()).unwrap_or(0) }
    pub fn at(&self, index: usize) -> Option<(Key, u64)> {
        let state = self.0.state.lock().unwrap(); state.order.get(index).and_then(|key| state.body.get(*key).map(|cell| (*key, cell.token())))
    }
    pub fn retain_cell(&self, address: usize) -> Option<Arc<Cell>> {
        let state = self.0.state.lock().unwrap();
        state.owned.iter().chain(state.retired_foreign.iter()).find(|cell| cell.address() == address).cloned()
            .or_else(|| state.body.rows().into_iter().find(|row| row.1.address() == address).map(|row| row.1))
    }
    pub fn put(&self, key: Key, value: u64) -> u64 {
        assert!(key.0 != 0 && key.0 != u64::MAX);
        let mut state = self.0.state.lock().unwrap();
        let old_cell = state.body.get(key); let old = old_cell.as_ref().map(|cell| cell.token()).unwrap_or(0);
        if value != 0 {
            if let Some(cell) = &old_cell {
                if cell.private(self.version()) {
                    // The body and every index reference THIS canonical atomic.
                    return cell.token.swap(value, Ordering::AcqRel);
                }
            }
        } else if old_cell.is_none() { return 0; }
        if value != 0 && old_cell.is_none() { state.order.reserve(1); }
        let grown = if value != 0 && old_cell.is_none() && (state.order.len() + 1) * 2 > state.table.slots.len() {
            let table = Box::new(Table::new(state.table.slots.len().checked_mul(2).expect("cell index capacity overflow")));
            for (key, cell) in state.body.rows() { table.put(key.0, cell.address()); }
            state.retired_tables.reserve(1); Some(table)
        } else { None };
        let new_cell = if value == 0 { None } else {
            let reusable = state.owned.iter().find(|cell| Arc::strong_count(cell) == 1 && cell.active.load(Ordering::Acquire) == 0).cloned();
            let cell = if let Some(cell) = reusable {
                cell.shared.store(0, Ordering::Release); cell.key.store(key.0, Ordering::Release);
                cell.incarnation.store(next_cell(), Ordering::Release); cell.active.store(1, Ordering::Release); cell.token.store(value, Ordering::Release); cell
            } else {
                let cell = Arc::new(Cell::fresh(self.version(), key, value)); state.owned.push(cell.clone()); cell
            };
            Some(cell)
        };
        if let Some(cell) = &old_cell {
            if cell.owner != self.version() { state.retired_foreign.push(cell.clone()); }
        }
        // No Java effects or unprotected reader accesses the body. Native atomic
        // projections publish before return; old cell memory remains pinned.
        Body::change(&mut state.body, key, new_cell.clone());
        self.0.header.sequence.fetch_add(1, Ordering::AcqRel);
        if let Some(table) = grown {
            let old = std::mem::replace(&mut state.table, table); state.retired_tables.push(old);
            self.0.header.descriptor.store(&*state.table as *const Table as usize, Ordering::Release);
            self.0.header.mask.store(state.table.mask, Ordering::Release);
        }
        if let Some(cell) = new_cell {
            state.table.put(key.0, cell.address()); if old_cell.is_none() { state.order.push(key); }
        } else {
            state.table.remove(key.0); let index = state.order.iter().position(|found| *found == key).unwrap(); state.order.remove(index);
        }
        if let Some(cell) = old_cell { if cell.owner == self.version() { cell.active.store(0, Ordering::Release); } }
        for index in 0..4 {
            let address = state.order.get(index).and_then(|key| state.body.get(*key)).map(|cell| cell.address()).unwrap_or(0);
            self.0.header.inline[index].store(address, Ordering::Release);
        }
        self.0.header.length.store(state.order.len(), Ordering::Release);
        self.0.header.sequence.fetch_add(1, Ordering::Release); old
    }
    pub fn detached(&self) -> Self {
        let mut state = self.0.state.lock().unwrap();
        if state.body.depth() != 0 { let rows = state.body.rows(); state.body = Arc::new(Body::Flat(rows)); }
        let rows = state.body.rows();
        // Caller holds the same root guard as every critical/alias writer.
        for (_, cell) in &rows { cell.shared.store(1, Ordering::Release); }
        let table = Box::new(Table::new(state.table.slots.len()));
        for (key, cell) in rows { table.put(key.0, cell.address()); }
        Self::build(version(), state.body.clone(), state.order.clone(), table)
    }
    #[cfg(test)] fn owned_cells(&self) -> usize { self.0.state.lock().unwrap().owned.len() }
}
impl Default for AccessPatch { fn default() -> Self { Self::build(version(), Arc::new(Body::Flat(Vec::new())), Vec::new(), Box::new(Table::new(8))) } }
pub struct AccessOverlay { pub prototype: u64, patch: AccessPatch, cow: AtomicU64 }
impl AccessOverlay {
    pub fn new(prototype: u64) -> Self { Self { prototype, patch: AccessPatch::default(), cow: AtomicU64::new(1) } }
    pub fn view(&self) -> AccessPatch { self.patch.clone() }
    pub fn cow_address(&self) -> usize { &self.cow as *const AtomicU64 as usize }
    pub fn stage(&self) -> Option<AccessPatch> { (self.cow.load(Ordering::Acquire) != 0).then(|| self.patch.detached()) }
    pub fn commit(&mut self, expected: u64, staged: AccessPatch) -> bool {
        if self.cow.load(Ordering::Acquire) == 0 || self.patch.version() != expected { return false; }
        self.patch = staged; self.cow.store(0, Ordering::Release); true
    }
    pub fn prepare(&mut self) -> u64 {
        if let Some(staged) = self.stage() { let expected = self.patch.version(); assert!(self.commit(expected, staged)); } self.patch.version()
    }
    pub fn copy(&mut self) -> Self {
        self.cow.store(1, Ordering::Release); Self { prototype: self.prototype, patch: self.patch.clone(), cow: AtomicU64::new(1) }
    }
    pub fn escape(&mut self) -> Option<AccessPatch> {
        if self.patch.len() == 0 { None } else { self.cow.store(1, Ordering::Release); Some(self.patch.clone()) }
    }
    pub fn put_prepared(&self, key: Key, value: u64) -> u64 { self.patch.put(key, value) }
}
#[cfg(test)] mod tests {
use super::*;
#[test] fn canonical_critical_replace_changes_body_and_every_index_without_structural_edit() {
    assert_eq!(std::mem::size_of::<Cell>(), 48); assert_eq!(std::mem::size_of::<Header>(), 64);
    let patch = AccessPatch::default(); patch.put(Key(1), 2);
    let cell = patch.0.state.lock().unwrap().body.get(Key(1)).unwrap();
    assert_eq!(unsafe { replace(Arc::as_ptr(&cell), patch.version(), 1, cell.incarnation.load(Ordering::Acquire), 2, 3) }, Some(2));
    assert_eq!(patch.get(Key(1)), 3); assert_eq!(patch.at(0), Some((Key(1), 3))); assert_eq!(patch.len(), 1);
}
#[test] fn shared_admission_rejects_then_commit_only_edit_preserves_parent_and_escaped_backing() {
    let patch = AccessPatch::default(); patch.put(Key(1), 2);
    let cell = patch.0.state.lock().unwrap().body.get(Key(1)).unwrap(); let incarnation = cell.incarnation.load(Ordering::Acquire);
    let child = patch.detached();
    assert_eq!(unsafe { replace(Arc::as_ptr(&cell), patch.version(), 1, incarnation, 2, 3) }, None);
    assert_eq!(patch.get(Key(1)), 2); assert_eq!(child.get(Key(1)), 2);
    let alias = patch.clone(); patch.put(Key(1), 3); assert_eq!(alias.get(Key(1)), 3); assert_eq!(child.get(Key(1)), 2);
    child.put(Key(1), 4); assert_eq!(patch.get(Key(1)), 3); assert_eq!(child.get(Key(1)), 4);
}
#[test] fn retained_cell_view_blocks_reuse_and_old_foreign_cells_survive_body_compaction() {
    let parent = AccessPatch::default(); parent.put(Key(1), 2);
    let address = parent.0.state.lock().unwrap().body.get(Key(1)).unwrap().address();
    let held = parent.retain_cell(address).unwrap(); let child = parent.detached(); drop(parent);
    child.put(Key(1), 3);
    for key in 2..100 { child.put(Key(key), key + 10); }
    assert_eq!(unsafe { (*(address as *const Cell)).token() }, 2); drop(held);
    assert!(child.retain_cell(address).is_some(), "retired foreign cell must stay alive through this backing");
}
#[test] fn live_children_and_retained_views_bound_arena_growth_and_delay_actual_reuse() {
    let source = AccessPatch::default(); source.put(Key(1), 2);
    let address = source.0.state.lock().unwrap().body.get(Key(1)).unwrap().address();
    let held = source.retain_cell(address).unwrap(); let old_incarnation = held.incarnation.load(Ordering::Acquire);
    let mut children = std::collections::VecDeque::new();
    for step in 0..5000 {
        let expected = source.get(Key(1)); children.push_back((source.detached(), expected));
        source.put(Key(1), step + 3);
        if children.len() > 16 { children.pop_front(); }
        if step % 31 == 0 {
            for (child, expected) in &children { assert_eq!(child.get(Key(1)), *expected); }
        }
        assert_eq!(held.token(), 2, "retained view prevents reusing its allocation");
        assert!(source.owned_cells() <= 20, "native arena must follow peak live sharing, not mutation count");
    }
    children.clear(); drop(held); drop(source.detached());
    source.put(Key(2), 999);
    let reused = source.retain_cell(address).unwrap();
    assert_eq!(reused.key.load(Ordering::Acquire), 2, "positive control: retired allocation is actually reused");
    assert_ne!(reused.incarnation.load(Ordering::Acquire), old_incarnation);
    assert_eq!(unsafe { replace(Arc::as_ptr(&reused), source.version(), 1, old_incarnation, 2, 1234) }, None);
    assert_eq!(source.get(Key(2)), 999); assert!(source.owned_cells() <= 20);
}
#[test] fn arena_reuse_is_bounded_after_all_body_child_and_view_references_retire() {
    let patch = AccessPatch::default();
    for id in 0..10_000 { patch.put(Key(1), id + 2); patch.put(Key(1), 0); }
    assert!(patch.owned_cells() <= 2);
    patch.put(Key(1), 2); let address = patch.0.state.lock().unwrap().body.get(Key(1)).unwrap().address();
    let held = patch.retain_cell(address).unwrap(); let old_incarnation = held.incarnation.load(Ordering::Acquire);
    patch.put(Key(1), 0); patch.put(Key(2), 3); assert_eq!(held.token(), 2); drop(held);
    for id in 0..100 { patch.put(Key(2), 0); patch.put(Key(2), id + 10); }
    let reused = patch.retain_cell(address).unwrap();
    if reused.active.load(Ordering::Acquire) != 0 {
        assert_eq!(unsafe { replace(Arc::as_ptr(&reused), patch.version(), 1, old_incarnation, 2, 999) }, None);
    }
    assert!(patch.owned_cells() <= 3);
}
}
}
pub use canonical_cells::{AccessOverlay as CanonicalOverlay, AccessPatch as CanonicalPatch, Cell as CanonicalCell};
pub use canonical_cells::replace as canonical_replace;


/// Actual retained defaults, including identity-hash layout and typed gameplay values.
/// Java objects are identity/reachability anchors; they do not own membership or scalars.
pub mod defaults {
    use std::sync::atomic::{AtomicUsize, Ordering};
    use std::sync::Arc;
    use std::collections::HashMap;
    use super::Payload;
    #[repr(C)]
    #[derive(Clone, Copy, Debug, Default)]
    pub struct Row { pub key: u64, pub leaf: i64, pub hash: u32, pub kind: u32, pub scalar: i32, pub reserved: i32 }
    #[repr(C)]
    #[derive(Debug)]
    pub struct Header { pub rows: AtomicUsize, pub len: AtomicUsize, pub capacity: AtomicUsize, pub hashed: AtomicUsize }
    #[derive(Debug)]
    pub struct Store { pub header: Header, rows: Vec<Row>, size: usize, hashed: bool, min_n: usize, typed: [Option<(u64,u32)>;4], typed_duplicates: [bool;4], enchantments: HashMap<i64, Arc<super::super::components::Enchantments>> }
    fn mix(hash: u32) -> usize { let h = hash.wrapping_mul(0x9e3779b9); (h ^ (h >> 16)) as usize }
    fn capacity(size: usize) -> usize { ((size * 4).div_ceil(3)).next_power_of_two().max(2) }
    impl Store {
        pub fn new(expected: usize) -> Self {
            let hashed = expected >= 8; let n = if hashed { capacity(expected) } else { expected };
            let rows = vec![Row::default(); n + usize::from(hashed)];
            let header = Header { rows: AtomicUsize::new(rows.as_ptr() as usize), len: AtomicUsize::new(0), capacity: AtomicUsize::new(n), hashed: AtomicUsize::new(usize::from(hashed)) };
            Self { header, rows, size: 0, hashed, min_n: n, typed: [None;4], typed_duplicates: [false;4], enchantments: HashMap::new() }
        }
        fn publish(&self) {
            self.header.rows.store(self.rows.as_ptr() as usize, Ordering::Release);
            self.header.capacity.store(self.n(), Ordering::Release);
            self.header.len.store(self.size, Ordering::Release);
        }
        pub fn n(&self) -> usize { self.rows.len() - usize::from(self.hashed) }
        pub fn hashed(&self) -> bool { self.hashed }
        pub fn len(&self) -> usize { self.size }
        pub fn at(&self, index: usize) -> Option<Row> { self.rows.get(index).copied() }
        pub fn find(&self, key: u64, hash: u32) -> Option<usize> {
            if !self.hashed { return (0..self.size).rev().find(|&i| self.rows[i].key == key); }
            if key == 0 { return (self.rows[self.n()].leaf != 0).then_some(self.n()); }
            let mask = self.n()-1; let mut pos = mix(hash) & mask;
            for _ in 0..self.n() { let row = self.rows[pos]; if row.leaf == 0 { return None; } if row.key == key { return Some(pos); } pos=(pos+1)&mask; }
            None
        }
        pub fn get(&self, key: u64, hash: u32) -> Option<Row> { self.find(key,hash).map(|i| self.rows[i]) }
        pub fn get_payload(&self, key: u64, hash: u32) -> Option<(Row, Option<Payload>)> {
            let row = self.get(key, hash)?;
            let payload = match row.reserved {
                1 => Some(Payload::Integer(row.scalar)),
                2 => self.enchantments.get(&row.leaf).cloned().map(Payload::Enchantments),
                3 => Some(Payload::Null), _ => None,
            };
            Some((row, payload))
        }
        fn typed_row(&self,kind:u32)->Option<Row> {
            let index=kind.checked_sub(1)? as usize;
            // Valid component types have one key per semantic role. Keep the
            // old canonical-row result for deliberately overlapping internal inputs.
            if *self.typed_duplicates.get(index)? {return self.rows.iter().copied().find(|r|r.leaf!=0 && r.kind==kind);}
            let (key,hash)=self.typed.get(index).copied().flatten()?;
            self.get(key,hash)
        }
        pub fn bounded_transfer(&self)->bool {self.rows.len()<=128 && !self.typed_duplicates.iter().any(|duplicate|*duplicate)}
        pub fn scalar(&self,kind:u32)->Option<i32> { self.typed_row(kind).filter(|r|r.kind&0x80000000==0).map(|r|r.scalar) }
        pub fn scalar_valid(&self,kind:u32)->bool { self.typed_row(kind).is_none_or(|r|r.kind&0x80000000==0) }
        fn index_typed(&mut self,row:Row) {
            let kind=row.kind&0x7fffffff;if (1..=4).contains(&kind) {
                let i=kind as usize-1;
                if self.typed[i].is_some_and(|old|old!=(row.key,row.hash)) {self.typed_duplicates[i]=true;}
                self.typed[i]=Some((row.key,row.hash));
            }
        }
        fn unindex_typed(&mut self,row:Row) {
            let kind=row.kind&0x7fffffff;if (1..=4).contains(&kind) && self.typed[kind as usize-1]==Some((row.key,row.hash)) {self.typed[kind as usize-1]=None;}
        }
        pub fn put(&mut self, row: Row) -> Option<Row> {
            let payload = if (1..=3).contains(&row.kind) { Some(Payload::Integer(row.scalar)) }
                else if row.kind == (4 | 0x80000000) { Some(Payload::Null) } else { None };
            self.put_payload(row, payload).expect("default payload allocation failed before publication")
        }
        pub fn put_payload(&mut self, mut row: Row, payload: Option<Payload>) -> Result<Option<Row>, ()> {
            if matches!(payload, Some(Payload::Enchantments(_))) { self.enchantments.try_reserve(1).map_err(|_| ())?; }
            row.reserved = match payload {
                Some(Payload::Integer(value)) => { row.scalar = value; 1 },
                Some(Payload::Enchantments(owner)) => { self.enchantments.insert(row.leaf, owner); 2 },
                Some(Payload::Null) => 3, None => 0,
            };
            if row.reserved != 2 { self.enchantments.remove(&row.leaf); }
            let old = self.put_row(row);
            if let Some(old) = old { self.retire_payload(old.leaf); }
            Ok(old)
        }
        fn retire_payload(&mut self, leaf: i64) {
            if !self.rows.iter().any(|row| row.leaf == leaf) { self.enchantments.remove(&leaf); }
        }
        fn put_row(&mut self, row: Row) -> Option<Row> {
            assert!(row.leaf > 0);
            if let Some(index)=self.find(row.key,row.hash) {
                let old=std::mem::replace(&mut self.rows[index],row);self.unindex_typed(old);self.index_typed(row);return Some(old);
            }
            self.index_typed(row);
            if !self.hashed {
                if self.size==self.rows.len() { self.rows.resize((self.size*2).max(2),Row::default()); }
                self.rows[self.size]=row;
            } else {
                let n=self.n(); let mask=n-1; let mut pos=if row.key==0 { n } else { mix(row.hash)&mask };
                while self.rows[pos].leaf!=0 { pos=(pos+1)&mask; }
                self.rows[pos]=row;
            }
            self.size+=1;
            if self.hashed && self.size > (self.n()*3).div_ceil(4).min(self.n()-1) { self.rehash(capacity(self.size+1)); }
            self.publish(); None
        }
        fn rehash(&mut self,n: usize) {
            let old=std::mem::replace(&mut self.rows,vec![Row::default();n+1]); let mask=n-1;
            // Fastutil reinserts non-null keys in descending old bucket order.
            for row in old[..old.len()-1].iter().rev().copied().filter(|r| r.leaf!=0) {
                let mut pos=mix(row.hash)&mask; while self.rows[pos].leaf!=0 { pos=(pos+1)&mask; } self.rows[pos]=row;
            }
            self.rows[n]=old[old.len()-1]; self.publish();
        }
        fn shift(&mut self,mut pos: usize,mut wrapped: Option<&mut Vec<(u64,u32)>>) {
            let mask=self.n()-1;
            loop {
                let last=pos; pos=(pos+1)&mask;
                loop {
                    let row=self.rows[pos]; if row.leaf==0 { self.rows[last]=Row::default(); return; }
                    let slot=mix(row.hash)&mask;
                    if if last<=pos { last>=slot || slot>pos } else { last>=slot && slot>pos } { break; }
                    pos=(pos+1)&mask;
                }
                if pos<last { if let Some(keys)=wrapped.as_mut() { keys.push((self.rows[pos].key,self.rows[pos].hash)); } }
                self.rows[last]=self.rows[pos];
            }
        }
        pub fn remove_index(&mut self,index: usize,wrapped: Option<&mut Vec<(u64,u32)>>,shrink: bool) -> Option<Row> {
            let old=*self.rows.get(index)?; if old.leaf==0 { return None; }
            if !self.hashed {
                if index>=self.size { return None; }
                self.rows.copy_within(index+1..self.size,index); self.rows[self.size-1]=Row::default();
            } else if index==self.n() { self.rows[index]=Row::default(); }
            else { self.shift(index,wrapped); }
            self.size-=1; self.unindex_typed(old);
            self.retire_payload(old.leaf);
            if shrink && self.hashed && self.n()>self.min_n && self.n()>16 && self.size< (self.n()*3).div_ceil(4)/4 { self.rehash(self.n()/2); }
            self.publish(); Some(old)
        }
        pub fn remove(&mut self,key:u64,hash:u32) -> Option<Row> { let index=self.find(key,hash)?; self.remove_index(index,None,true) }
        pub fn clear(&mut self) { self.rows.fill(Row::default()); self.enchantments.clear(); self.size=0; self.typed=[None;4]; self.typed_duplicates=[false;4]; self.publish(); }
    }
    /// Cursor state is native iteration bookkeeping over the same mutable store.
    pub struct Cursor { pos: i64, remaining: usize, last: Option<usize>, null_first: bool, wrapped: Vec<(u64,u32)>, pub failure:u8, pub failure_index:usize }
    impl Cursor {
        pub fn new(store:&Store) -> Self { Self { pos:if store.hashed {store.n() as i64} else {0},remaining:store.size,last:None,null_first:store.hashed && store.rows[store.n()].leaf!=0,wrapped:Vec::new(),failure:0,failure_index:0 } }
        pub fn has_next(&self,store:&Store)->bool { if store.hashed {self.remaining!=0} else {self.pos < store.size as i64} }
        pub fn next(&mut self,store:&Store)->Option<usize> {
            self.failure=0;
            if !self.has_next(store) {return None;}
            if !store.hashed {let index=self.pos as usize;self.pos+=1;self.last=Some(index);return Some(index);}
            self.remaining-=1;
            if self.null_first {self.null_first=false;self.last=Some(store.n());return self.last;}
            loop {
                self.pos-=1;
                if self.pos<0 {
                    self.last=Some(usize::MAX);self.failure_index=(-self.pos-1) as usize;
                    if self.wrapped.is_empty(){self.failure=1;return None;}
                    let Some(&(key,hash))=self.wrapped.get(self.failure_index)else{self.failure=2;return None;};
                    return store.find(key,hash);
                }
                let index=self.pos as usize;
                let Some(row)=store.rows.get(index)else{self.failure=3;self.failure_index=index;return None;};
                if row.leaf!=0 {self.last=Some(index);return Some(index);}
            }
        }
        pub fn position(&self)->usize {self.pos.max(0) as usize}
        pub fn wrapped_len(&self)->usize {self.wrapped.len()}
        pub fn next_foreach(&mut self,store:&Store)->Option<usize> {
            if !store.hashed {self.failure=0;let index=self.pos as usize;self.last=Some(index);self.pos+=1;return Some(index);}
            let had=self.has_next(store);let found=self.next(store);if had {self.remaining+=1;}found
        }
        pub fn foreach_complete(&mut self,store:&Store) {if store.hashed {self.remaining=self.remaining.wrapping_sub(1);}}
        pub fn remove(&mut self,store:&mut Store)->Option<Row> {
            self.failure=0;let index=self.last?;
            if !store.hashed {let old=store.remove_index(index,None,false)?;self.pos-=1;self.last=None;return Some(old);}
            if self.pos<0 {
                self.failure_index=(-self.pos-1) as usize;
                if self.wrapped.is_empty(){self.failure=1;return None;}
                let Some(&(key,hash))=self.wrapped.get(self.failure_index)else{self.failure=2;return None;};
                let old=store.remove(key,hash);self.last=None;return old;
            }
            let old=store.remove_index(index,Some(&mut self.wrapped),false);self.last=None;old
        }
    }
}

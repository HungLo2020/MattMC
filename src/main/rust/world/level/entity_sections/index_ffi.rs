//! Primary section identity/storage and bounded live spatial cursors.
//! No native lock spans a Java visibility, section or consumer callback.
use super::{
    bindings::Bindings,
    section_map::{Bounds, SectionMap},
    section_tree::SectionTree,
};
use std::{
    collections::HashMap,
    ffi::c_void,
    sync::{
        atomic::{AtomicI32, Ordering},
        Arc, Mutex,
    },
};

#[repr(C)]
#[derive(Default)]
struct Change {
    token: u64,
    slot: i32,
    length: i32,
}
#[repr(C)]
#[derive(Default)]
struct CursorView {
    active: i32,
    padding: i32,
    key: i64,
    end: i64,
    token: u64,
}
#[derive(Clone, Copy)]
struct Spatial {
    x: i32,
    max_x: i32,
    min_y: i32,
    max_y: i32,
    min_z: i32,
    max_z: i32,
    opened: bool,
}
#[derive(Clone, Copy)]
struct Cursor {
    head: usize,
    end: i64,
    spatial: Option<Spatial>,
}
struct Data {
    map: SectionMap,
    tree: SectionTree,
    cursors: HashMap<u64, Cursor>,
    next: u64,
    chunks: Vec<i64>,
}
struct Index {
    data: Mutex<Data>,
    bindings: Bindings,
    count: AtomicI32,
}
struct Lease {
    owner: Arc<Index>,
    id: u64,
    view: CursorView,
}
fn encode(x: i32, y: i32, z: i32) -> i64 {
    (((x as u64) & 0x3fffff) << 42 | ((z as u64) & 0x3fffff) << 20 | ((y as u64) & 0xfffff)) as i64
}
fn chunk(key: i64) -> i64 {
    (key >> 42) as u32 as u64 as i64 | ((((key << 22) >> 42) as u32 as u64) << 32) as i64
}
fn section(v: f64) -> i32 {
    let i = v as i32;
    (if v < (i as f64) { i.wrapping_sub(1) } else { i }) >> 4
}
impl Data {
    fn sweep(&mut self) {
        if !self.tree.needs_sweep() {
            return;
        }
        let heads: Vec<_> = self.cursors.values().map(|c| c.head).collect();
        self.tree.sweep(&heads);
    }
}
fn status(result: Result<(), Bounds>, out: *mut Change) -> i32 {
    match result {
        Ok(()) => 0,
        Err(e) => {
            if let Some(out) = unsafe { out.as_mut() } {
                out.slot = e.index;
                out.length = e.length as i32;
            }
            2
        }
    }
}
#[no_mangle]
pub extern "C" fn mattmc_section_index_create() -> *const c_void {
    Arc::into_raw(Arc::new(Index {
        data: Mutex::new(Data {
            map: SectionMap::default(),
            tree: SectionTree::default(),
            cursors: HashMap::new(),
            next: 0,
            chunks: Vec::new(),
        }),
        bindings: Bindings::default(),
        count: AtomicI32::new(0),
    }))
    .cast()
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_index_release(owner: *const c_void) {
    if !owner.is_null() {
        drop(Arc::from_raw(owner.cast::<Index>()));
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_index_view(owner: *const c_void) -> *const c_void {
    owner
        .cast::<Index>()
        .as_ref()
        .map_or(std::ptr::null(), |o| (&o.count as *const AtomicI32).cast())
}
#[no_mangle]
pub extern "C" fn mattmc_section_index_chunk(key: i64) -> i64 {
    chunk(key)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_index_get(owner: *const c_void, key: i64) -> u64 {
    owner
        .cast::<Index>()
        .as_ref()
        .map_or(0, |o| o.data.lock().unwrap().map.get(key))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_index_find(
    owner: *const c_void,
    key: i64,
    out: *mut Change,
) -> i32 {
    let (Some(o), Some(out)) = (owner.cast::<Index>().as_ref(), out.as_mut()) else {
        return -1;
    };
    let d = o.data.lock().unwrap();
    *out = match d.map.find(key) {
        Ok(i) => Change {
            token: d.map.cells[i].token,
            slot: -1,
            length: 0,
        },
        Err(i) => Change {
            token: 0,
            slot: i as i32,
            length: 0,
        },
    };
    0
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_index_add_key(owner: *const c_void, key: i64) {
    if let Some(o) = owner.cast::<Index>().as_ref() {
        let mut d = o.data.lock().unwrap();
        d.tree.add(key);
        o.count.store(d.tree.count, Ordering::Release);
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_index_allocate(owner: *const c_void) -> u64 {
    owner
        .cast::<Index>()
        .as_ref()
        .map_or(0, |o| o.bindings.allocate())
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_index_finish(owner: *const c_void, token: u64) {
    if let Some(o) = owner.cast::<Index>().as_ref() {
        o.bindings.release(token);
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_index_retired(owner: *const c_void) -> u64 {
    owner
        .cast::<Index>()
        .as_ref()
        .map_or(0, |o| o.bindings.retired())
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_index_insert(
    owner: *const c_void,
    key: i64,
    slot: i32,
    token: u64,
    out: *mut Change,
) -> i32 {
    let Some(o) = owner.cast::<Index>().as_ref() else {
        return -1;
    };
    let mut d = o.data.lock().unwrap();
    let in_bounds = (slot as usize) < d.map.cells.len();
    let mut overwritten = 0;
    if in_bounds {
        o.bindings.retain(token);
    }
    let result = d.map.insert_at(slot as usize, key, token, &mut overwritten);
    if overwritten != 0 {
        o.bindings.release(overwritten);
    }
    status(result, out)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_index_remove(
    owner: *const c_void,
    key: i64,
    out: *mut Change,
) -> i32 {
    let Some(o) = owner.cast::<Index>().as_ref() else {
        return -1;
    };
    let mut d = o.data.lock().unwrap();
    let old = d.map.get(key);
    let result = d.map.remove(key);
    if old != 0 {
        o.bindings.release(old);
    }
    // Frozen commits map removal first; a failed shrink never removes its tree key.
    match result {
        Err(e) => status(Err(e), out),
        Ok(_) => {
            d.tree.remove(key);
            d.sweep();
            o.count.store(d.tree.count, Ordering::Release);
            0
        }
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_index_chunks(
    owner: *const c_void,
    out: *mut *const i64,
) -> i32 {
    let (Some(o), Some(out)) = (owner.cast::<Index>().as_ref(), out.as_mut()) else {
        return -1;
    };
    let mut d = o.data.lock().unwrap();
    d.chunks = d.map.keys().map(chunk).collect();
    *out = d.chunks.as_ptr();
    d.chunks.len() as i32
}
unsafe fn lease(owner: *const Index, d: &mut Data, cursor: Cursor, active: bool) -> *mut c_void {
    d.next = d
        .next
        .checked_add(1)
        .expect("section cursor identity exhausted");
    let id = d.next;
    d.cursors.insert(id, cursor);
    Arc::increment_strong_count(owner);
    Box::into_raw(Box::new(Lease {
        owner: Arc::from_raw(owner),
        id,
        view: CursorView {
            active: i32::from(active),
            ..Default::default()
        },
    }))
    .cast()
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_index_chunk_cursor(
    owner: *const c_void,
    chunk_key: i64,
    out: *mut i64,
) -> *mut c_void {
    let Some(o) = owner.cast::<Index>().as_ref() else {
        return std::ptr::null_mut();
    };
    let x = chunk_key as i32;
    let z = (chunk_key >> 32) as i32;
    let from = encode(x, 0, z);
    let end = encode(x, -1, z).wrapping_add(1);
    if !out.is_null() {
        *out = from;
        *out.add(1) = end;
    }
    if from > end {
        return std::ptr::null_mut();
    }
    let mut d = o.data.lock().unwrap();
    let head = d.tree.first_in(from, end);
    lease(
        o,
        &mut d,
        Cursor {
            head,
            end,
            spatial: None,
        },
        head != 0,
    )
}
fn spatial(bounds: &[f64; 6]) -> Cursor {
    Cursor {
        head: 0,
        end: 0,
        spatial: Some(Spatial {
            x: section(bounds[0] - 2.0),
            min_y: section(bounds[1] - 4.0),
            min_z: section(bounds[2] - 2.0),
            max_x: section(bounds[3] + 2.0),
            max_y: section(bounds[4] + 0.0),
            max_z: section(bounds[5] + 2.0),
            opened: false,
        }),
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_index_spatial_cursor(
    owner: *const c_void,
    bounds: *const f64,
) -> *mut c_void {
    let Some(o) = owner.cast::<Index>().as_ref() else {
        return std::ptr::null_mut();
    };
    if bounds.is_null() {
        return std::ptr::null_mut();
    }
    let c = spatial(&*bounds.cast::<[f64; 6]>());
    let active = c.spatial.is_some_and(|s| s.x <= s.max_x);
    let mut d = o.data.lock().unwrap();
    lease(o, &mut d, c, active)
}
/// Reuses a closed cursor's lease for a new spatial search, so a hot query
/// path allocates no lease (and Java no cleanup registration) per search.
/// Returns 0, or -1 for a missing/open cursor or missing bounds.
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_index_spatial_reopen(cursor: *mut c_void, bounds: *const f64) -> i32 {
    let Some(l) = cursor.cast::<Lease>().as_mut() else {
        return -1;
    };
    if bounds.is_null() {
        return -1;
    }
    let c = spatial(&*bounds.cast::<[f64; 6]>());
    let active = c.spatial.is_some_and(|s| s.x <= s.max_x);
    let mut d = l.owner.data.lock().unwrap();
    if d.cursors.contains_key(&l.id) {
        return -1;
    }
    d.next = d
        .next
        .checked_add(1)
        .expect("section cursor identity exhausted");
    l.id = d.next;
    d.cursors.insert(l.id, c);
    l.view = CursorView {
        active: i32::from(active),
        ..Default::default()
    };
    0
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_index_cursor_view(cursor: *const c_void) -> *const c_void {
    cursor
        .cast::<Lease>()
        .as_ref()
        .map_or(std::ptr::null(), |l| (&l.view as *const CursorView).cast())
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_index_cursor_next(cursor: *mut c_void) -> i32 {
    let Some(l) = cursor.cast::<Lease>().as_mut() else {
        return -1;
    };
    if l.view.active == 0 {
        return 0;
    }
    let mut d = l.owner.data.lock().unwrap();
    let Some(mut c) = d.cursors.get(&l.id).copied() else {
        return -1;
    };
    if c.spatial.is_none() {
        let key = d.tree.key(c.head);
        c.head = d.tree.next(c.head);
        if c.head != 0 && d.tree.key(c.head) >= c.end {
            c.head = 0;
        }
        l.view.key = key;
        l.view.active = i32::from(c.head != 0);
        d.cursors.insert(l.id, c);
        d.sweep();
        return 1;
    }
    let mut s = c.spatial.unwrap();
    loop {
        if !s.opened {
            let from = encode(s.x, 0, 0);
            c.end = encode(s.x, -1, -1).wrapping_add(1);
            if from > c.end {
                l.view.active = 0;
                l.view.key = from;
                l.view.end = c.end;
                d.cursors.remove(&l.id);
                d.sweep();
                return -2;
            }
            c.head = d.tree.first_in(from, c.end);
            s.opened = true;
        }
        if c.head == 0 {
            if s.x == s.max_x {
                l.view.active = 0;
                d.cursors.remove(&l.id);
                d.sweep();
                return 0;
            }
            s.x = s.x.wrapping_add(1);
            s.opened = false;
            continue;
        }
        let key = d.tree.key(c.head);
        c.head = d.tree.next(c.head);
        if c.head != 0 && d.tree.key(c.head) >= c.end {
            c.head = 0;
        }
        let y = ((key << 44) >> 44) as i32;
        let z = ((key << 22) >> 42) as i32;
        if y >= s.min_y && y <= s.max_y && z >= s.min_z && z <= s.max_z {
            // Leave the next raw node and unopened future x-ranges intact.
            // The Java consumer may mutate them before the next call.
            c.spatial = Some(s);
            l.view.key = key;
            l.view.token = d.map.get(key);
            d.cursors.insert(l.id, c);
            d.sweep();
            return 1;
        }
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_index_cursor_close(cursor: *mut c_void) {
    if let Some(l) = cursor.cast::<Lease>().as_mut() {
        let mut d = l.owner.data.lock().unwrap();
        d.cursors.remove(&l.id);
        d.sweep();
        l.view.active = 0;
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_index_cursor_release(cursor: *mut c_void) {
    if !cursor.is_null() {
        mattmc_section_index_cursor_close(cursor);
        drop(Box::from_raw(cursor.cast::<Lease>()));
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    unsafe fn insert(owner: *const c_void, key: i64) {
        let mut change = Change::default();
        assert_eq!(mattmc_section_index_find(owner, key, &mut change), 0);
        assert!(change.slot >= 0);
        let slot = change.slot;
        let token = mattmc_section_index_allocate(owner);
        mattmc_section_index_add_key(owner, key);
        assert_eq!(
            mattmc_section_index_insert(owner, key, slot, token, &mut change),
            0
        );
        mattmc_section_index_finish(owner, token);
    }
    #[test]
    fn future_x_subset_opens_after_consumer_mutation_and_owner_release_is_leased() {
        unsafe {
            let owner = mattmc_section_index_create();
            insert(owner, encode(0, 0, 0));
            let b = [0.0, 0.0, 0.0, 17.0, 16.0, 0.0];
            let cursor = mattmc_section_index_spatial_cursor(owner, b.as_ptr());
            assert_eq!(mattmc_section_index_cursor_next(cursor), 1);
            assert_eq!((*cursor.cast::<Lease>()).view.key, encode(0, 0, 0));
            insert(owner, encode(1, 0, 0));
            let weak = {
                Arc::increment_strong_count(owner.cast::<Index>());
                let a = Arc::from_raw(owner.cast::<Index>());
                Arc::downgrade(&a)
            };
            mattmc_section_index_release(owner);
            assert_eq!(mattmc_section_index_cursor_next(cursor), 1);
            assert_eq!((*cursor.cast::<Lease>()).view.key, encode(1, 0, 0));
            assert_eq!(mattmc_section_index_cursor_next(cursor), 0);
            mattmc_section_index_cursor_release(cursor);
            assert!(weak.upgrade().is_none());
        }
    }
    #[test]
    fn reopened_spatial_cursor_searches_again_and_rejects_open_leases() {
        unsafe {
            let owner = mattmc_section_index_create();
            insert(owner, encode(0, 0, 0));
            let b = [1.0, 1.0, 1.0, 2.0, 2.0, 2.0];
            let cursor = mattmc_section_index_spatial_cursor(owner, b.as_ptr());
            assert_eq!(-1, mattmc_section_index_spatial_reopen(cursor, b.as_ptr()));
            assert_eq!(mattmc_section_index_cursor_next(cursor), 1);
            assert_eq!(mattmc_section_index_cursor_next(cursor), 0);
            mattmc_section_index_cursor_close(cursor);
            for _ in 0..2 {
                assert_eq!(0, mattmc_section_index_spatial_reopen(cursor, b.as_ptr()));
                assert_eq!(mattmc_section_index_cursor_next(cursor), 1);
                assert_eq!((*cursor.cast::<Lease>()).view.key, encode(0, 0, 0));
                mattmc_section_index_cursor_close(cursor);
            }
            assert!((*owner.cast::<Index>()).data.lock().unwrap().cursors.is_empty());
            mattmc_section_index_cursor_release(cursor);
            mattmc_section_index_release(owner);
        }
    }
    #[test]
    fn removed_prefetched_key_is_returned_by_position_cursor_then_binding_is_absent() {
        unsafe {
            let owner = mattmc_section_index_create();
            let first = encode(0, 0, 0);
            let second = encode(0, 1, 0);
            insert(owner, first);
            insert(owner, second);
            let mut bounds = [0; 2];
            let cursor = mattmc_section_index_chunk_cursor(owner, 0, bounds.as_mut_ptr());
            let mut change = Change::default();
            assert_eq!(mattmc_section_index_remove(owner, first, &mut change), 0);
            assert_eq!(mattmc_section_index_cursor_next(cursor), 1);
            assert_eq!((*cursor.cast::<Lease>()).view.key, first);
            assert_eq!(mattmc_section_index_get(owner, first), 0);
            assert_eq!(mattmc_section_index_cursor_next(cursor), 1);
            assert_eq!((*cursor.cast::<Lease>()).view.key, second);
            assert_eq!((*cursor.cast::<Lease>()).view.active, 0);
            mattmc_section_index_cursor_release(cursor);
            mattmc_section_index_release(owner);
        }
    }
    #[test]
    fn empty_spatial_search_still_reports_signed_subset_overflow() {
        unsafe {
            let owner = mattmc_section_index_create();
            let x = 2_097_151.0 * 16.0;
            let b = [x + 2.0, 0.0, 0.0, x + 2.0, 0.0, 0.0];
            let cursor = mattmc_section_index_spatial_cursor(owner, b.as_ptr());
            assert_eq!(mattmc_section_index_cursor_next(cursor), -2);
            let view = &(*cursor.cast::<Lease>()).view;
            assert_eq!(view.key, encode(2_097_151, 0, 0));
            assert_eq!(view.end, i64::MIN);
            mattmc_section_index_cursor_release(cursor);
            mattmc_section_index_release(owner);
        }
    }
}

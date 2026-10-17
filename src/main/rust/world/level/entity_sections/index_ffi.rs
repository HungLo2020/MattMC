//! Primary section identity/storage and bounded live spatial cursors.
//! No native lock spans a Java visibility, section or consumer callback.
#[path = "collection.rs"]
mod collection;
use super::{
    bindings::Bindings,
    ffi::Context,
    section_map::{Bounds, SectionMap},
    section_tree::SectionTree,
};
use std::{
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
    // Slots stay reserved until lease release, including closed pooled leases.
    cursors: Vec<Option<Cursor>>,
    free_cursors: Vec<usize>,
    chunks: Vec<i64>,
    // Binding slots are already dense and generational; no second key hash is needed.
    members: Vec<Option<(u64, Arc<Context>)>>,
}
struct Index {
    data: Mutex<Data>,
    bindings: Bindings,
    count: AtomicI32,
}
struct Lease {
    owner: Arc<Index>,
    id: usize,
    view: CursorView,
    bounds: Option<[f64; 6]>,
    collection_cursor: Option<i32>,
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
        let heads: Vec<_> = self.cursors.iter().flatten().map(|c| c.head).collect();
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
            cursors: Vec::new(),
            free_cursors: Vec::new(),
            chunks: Vec::new(),
            members: Vec::new(),
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
        .map_or(0, |o| {
            let token = o.bindings.retired();
            if token != 0 {
                if let Some(slot) = o.data.lock().unwrap().members.get_mut(token as u32 as usize) {
                    if slot.as_ref().is_some_and(|(generation, _)| *generation == token) { *slot = None; }
                }
            }
            token
        })
}
/// Retain the primary member/visibility owner, never a Java object or GPU handle.
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_index_attach_members(owner: *const c_void, token: u64, members: *const c_void) -> i32 {
    let Some(o) = owner.cast::<Index>().as_ref() else { return -1; };
    if members.is_null() || token == 0 { return -1; }
    let mut d = o.data.lock().unwrap();
    let slot = token as u32 as usize;
    if slot >= d.members.len() { d.members.resize_with(slot + 1, || None); }
    Arc::increment_strong_count(members.cast::<Context>());
    d.members[slot] = Some((token, Arc::from_raw(members.cast::<Context>())));
    0
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
    let id = d.free_cursors.pop().unwrap_or_else(|| {
        d.cursors.push(None);
        d.cursors.len() - 1
    });
    d.cursors[id] = Some(cursor);
    Arc::increment_strong_count(owner);
    Box::into_raw(Box::new(Lease {
        owner: Arc::from_raw(owner),
        id,
        view: CursorView {
            active: i32::from(active),
            ..Default::default()
        },
        bounds: None,
        collection_cursor: None,
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
    let pointer = lease(o, &mut d, c, active);
    (*pointer.cast::<Lease>()).bounds = Some(*bounds.cast::<[f64; 6]>());
    pointer
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
    if d.cursors[l.id].is_some() {
        return -1;
    }
    d.cursors[l.id] = Some(c);
    l.view = CursorView {
        active: i32::from(active),
        ..Default::default()
    };
    l.bounds = Some(*bounds.cast::<[f64; 6]>());
    l.collection_cursor = None;
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
    advance(&mut d, l.id, &mut l.view)
}

/// Advances through rejected sections within the same native call. Stops before
/// every Java callback, leaving the original live cursor's next raw node intact.
/// `scan_members` is only valid for untyped queries; typed casts may have effects.
/// Returns 1..128 matches, 129=accessible section, 130=custom/null-status section,
/// 0=end, -2=coordinate range error, -4=iterator mutation, -5=no next member.
/// A match batch packs its iterator version in the high half of out[1].
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_index_query_next(cursor: *mut c_void, scan_members: i32, output: *mut u64) -> i32 {
    query_next(cursor, scan_members != 0, 0, output)
}

/// Stock class queries may scan an already admitted typed group. Unknown types
/// yield to Java at the original section boundary for lazy admission/failure.
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_index_query_typed(cursor: *mut c_void, type_identity: u64, output: *mut u64) -> i32 {
    if type_identity == 0 { return -1; }
    query_next(cursor, true, type_identity, output)
}

unsafe fn query_next(cursor: *mut c_void, scan_members: bool, type_identity: u64, output: *mut u64) -> i32 {
    let Some(l) = cursor.cast::<Lease>().as_mut() else { return -1; };
    if output.is_null() || l.bounds.is_none() { return -1; }
    let out = std::slice::from_raw_parts_mut(output, 130);
    let mut d = l.owner.data.lock().unwrap();
    loop {
        let status = advance(&mut d, l.id, &mut l.view);
        if status != 1 { return status; }
        if l.view.token == 0 { continue; }
        if let Some((_, context)) = d.members.get(l.view.token as u32 as usize)
            .and_then(Option::as_ref).filter(|(token, _)| *token == l.view.token) {
            if let Some((count, modifications)) = context.query(if scan_members { l.bounds } else { None }, type_identity, out) {
                if count == 0 { continue; }
                if count == -2 { return -4; } // Iterator modification failure.
                if count == -3 { return -5; } // Failed mutation left no next element.
                if count < -1 { return -3; }
                if count == -1 { return 129; }
                out[1] |= u64::from(modifications as u32) << 32;
                return count;
            }
        }
        return 130;
    }
}

fn advance(d: &mut Data, id: usize, view: &mut CursorView) -> i32 {
    if view.active == 0 { return 0; }
    let Some(mut c) = d.cursors[id] else {
        return -1;
    };
    if c.spatial.is_none() {
        let key = d.tree.key(c.head);
        c.head = d.tree.next(c.head);
        if c.head != 0 && d.tree.key(c.head) >= c.end {
            c.head = 0;
        }
        view.key = key;
        view.active = i32::from(c.head != 0);
        d.cursors[id] = Some(c);
        d.sweep();
        return 1;
    }
    let mut s = c.spatial.unwrap();
    loop {
        if !s.opened {
            let from = encode(s.x, 0, 0);
            c.end = encode(s.x, -1, -1).wrapping_add(1);
            if from > c.end {
                view.active = 0;
                view.key = from;
                view.end = c.end;
                d.cursors[id] = None;
                d.sweep();
                return -2;
            }
            c.head = d.tree.first_in(from, c.end);
            s.opened = true;
        }
        if c.head == 0 {
            if s.x == s.max_x {
                view.active = 0;
                d.cursors[id] = None;
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
            view.key = key;
            view.token = d.map.get(key);
            d.cursors[id] = Some(c);
            d.sweep();
            return 1;
        }
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_index_cursor_close(cursor: *mut c_void) {
    if let Some(l) = cursor.cast::<Lease>().as_mut() {
        let mut d = l.owner.data.lock().unwrap();
        d.cursors[l.id] = None;
        d.sweep();
        l.view.active = 0;
        l.collection_cursor = None;
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_index_cursor_release(cursor: *mut c_void) {
    if !cursor.is_null() {
        mattmc_section_index_cursor_close(cursor);
        let lease = Box::from_raw(cursor.cast::<Lease>());
        lease.owner.data.lock().unwrap().free_cursors.push(lease.id);
        drop(lease);
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use super::super::ffi::{
        mattmc_section_members_create, mattmc_section_members_release,
        mattmc_section_members_allocate, mattmc_section_members_add,
        mattmc_section_members_finish_binding, mattmc_section_members_visibility,
        mattmc_section_members_attach_geometry,
    };

    #[test]
    fn closed_cursor_reserves_its_slot_until_release_and_released_slots_are_bounded() {
        unsafe {
            let owner = mattmc_section_index_create();
            insert(owner, encode(0, 0, 0));
            let bounds = [0., 0., 0., 1., 1., 1.];
            let first = mattmc_section_index_spatial_cursor(owner, bounds.as_ptr());
            mattmc_section_index_cursor_close(first);
            let second = mattmc_section_index_spatial_cursor(owner, bounds.as_ptr());
            assert_ne!((*first.cast::<Lease>()).id, (*second.cast::<Lease>()).id);
            assert_eq!(mattmc_section_index_spatial_reopen(first, bounds.as_ptr()), 0);
            assert_eq!(mattmc_section_index_cursor_next(first), 1);
            assert_eq!(mattmc_section_index_cursor_next(second), 1);
            mattmc_section_index_cursor_release(first);
            for _ in 0..10_000 {
                let cursor = mattmc_section_index_spatial_cursor(owner, bounds.as_ptr());
                assert_eq!(mattmc_section_index_cursor_next(cursor), 1);
                mattmc_section_index_cursor_release(cursor);
            }
            assert_eq!((*owner.cast::<Index>()).data.lock().unwrap().cursors.len(), 2);
            assert_eq!(mattmc_section_index_cursor_next(second), 0);
            mattmc_section_index_cursor_release(second);
            mattmc_section_index_release(owner);
        }
    }

    #[test]
    fn typed_query_yields_until_admission_then_scans_only_the_selected_group() {
        use super::super::ffi::{mattmc_section_members_new_group, mattmc_section_members_bind_type};
        unsafe {
            let owner = mattmc_section_index_create();
            insert(owner, encode(0, 0, 0));
            let members = mattmc_section_members_create();
            mattmc_section_members_visibility(members, 1);
            let first = mattmc_section_members_allocate(members);
            let second = mattmc_section_members_allocate(members);
            mattmc_section_members_add(members, 0, first, std::ptr::null_mut());
            mattmc_section_members_add(members, 0, second, std::ptr::null_mut());
            mattmc_section_index_attach_members(owner, mattmc_section_index_get(owner, encode(0, 0, 0)), members);
            let bounds = [0., 0., 0., 1., 1., 1.];
            let cursor = mattmc_section_index_spatial_cursor(owner, bounds.as_ptr());
            let mut out = [0; 130];
            assert_eq!(mattmc_section_index_query_typed(cursor, 42, out.as_mut_ptr()), 129);
            mattmc_section_index_cursor_close(cursor);
            let group = mattmc_section_members_new_group(members);
            assert_eq!(mattmc_section_members_bind_type(members, group, 42), 0);
            assert_eq!(mattmc_section_members_bind_type(members, 0, 42), -1);
            assert_eq!(mattmc_section_members_bind_type(members, group, 43), -1);
            mattmc_section_index_spatial_reopen(cursor, bounds.as_ptr());
            assert_eq!(mattmc_section_index_query_typed(cursor, 42, out.as_mut_ptr()), 0); // Empty typed group; root is nonempty.
            mattmc_section_index_cursor_close(cursor);
            mattmc_section_members_add(members, group, second, std::ptr::null_mut());
            mattmc_section_members_finish_binding(members, first);
            mattmc_section_members_finish_binding(members, second);
            mattmc_section_index_spatial_reopen(cursor, bounds.as_ptr());
            assert_eq!(mattmc_section_index_query_typed(cursor, 42, out.as_mut_ptr()), 1);
            assert_eq!(out[2] as u32, second as u32);
            assert_eq!(out[1] >> 32, 1); // Typed group's modification count, not root's two inserts.
            mattmc_section_index_cursor_close(cursor);
            mattmc_section_members_visibility(members, -1);
            mattmc_section_index_spatial_reopen(cursor, bounds.as_ptr());
            assert_eq!(mattmc_section_index_query_typed(cursor, 42, out.as_mut_ptr()), 130); // Null visibility precedes typed filtering.
            mattmc_section_index_cursor_release(cursor);
            mattmc_section_members_release(members);
            mattmc_section_index_release(owner);
        }
    }

    #[test]
    fn query_retains_members_until_index_retirement_and_preserves_null_visibility() {
        unsafe {
            let owner = mattmc_section_index_create();
            insert(owner, encode(0, 0, 0));
            let token = mattmc_section_index_get(owner, encode(0, 0, 0));
            let members = mattmc_section_members_create();
            let weak = {
                Arc::increment_strong_count(members.cast::<Context>());
                Arc::downgrade(&Arc::from_raw(members.cast::<Context>()))
            };
            assert_eq!(mattmc_section_index_attach_members(owner, token, members), 0);
            let member = mattmc_section_members_allocate(members);
            assert_eq!(mattmc_section_members_add(members, 0, member, std::ptr::null_mut()), 0);
            mattmc_section_members_finish_binding(members, member);
            let bounds = [0., 0., 0., 1., 1., 1.];
            let cursor = mattmc_section_index_spatial_cursor(owner, bounds.as_ptr());
            let mut out = [0; 130];
            assert_eq!(mattmc_section_index_query_next(cursor, 1, out.as_mut_ptr()), 130); // Null yields for Java's NPE.
            mattmc_section_index_cursor_close(cursor);
            assert_eq!(mattmc_section_members_visibility(members, 1), -1);
            mattmc_section_members_release(members);
            assert!(weak.upgrade().is_some());
            assert_eq!(mattmc_section_index_spatial_reopen(cursor, bounds.as_ptr()), 0);
            assert_eq!(mattmc_section_index_query_next(cursor, 1, out.as_mut_ptr()), 1);
            assert_eq!(out[1] >> 32, 1); // Member iterator version is part of the same batch.
            assert_eq!(out[2] as u32, member as u32); // Unknown bounds yield a behavior binding.
            let mut change = Change::default();
            assert_eq!(mattmc_section_index_remove(owner, encode(0, 0, 0), &mut change), 0);
            assert_eq!(mattmc_section_index_retired(owner), token);
            assert!(weak.upgrade().is_none());
            mattmc_section_index_cursor_release(cursor);
            mattmc_section_index_release(owner);
        }
    }

    #[test]
    fn query_skips_stock_misses_but_opens_future_columns_only_after_match_consumption() {
        use crate::world::level::entity_geometry::*;
        unsafe {
            let owner = mattmc_section_index_create();
            let world = mattmc_entity_geometry_world_create();
            let body = mattmc_entity_geometry_create(world, 0., 0., 0., 0., 8., 0., 1., 9., 1.);
            let members = mattmc_section_members_create();
            let member = mattmc_section_members_allocate(members);
            mattmc_section_members_attach_geometry(members, member, body, 0);
            assert_eq!(mattmc_section_members_add(members, 0, member, std::ptr::null_mut()), 0);
            mattmc_section_members_finish_binding(members, member);
            mattmc_section_members_visibility(members, 1);
            insert(owner, encode(0, 0, 0));
            let token = mattmc_section_index_get(owner, encode(0, 0, 0));
            mattmc_section_index_attach_members(owner, token, members);
            let bounds = [0., 0., 0., 33., 2., 2.];
            let cursor = mattmc_section_index_spatial_cursor(owner, bounds.as_ptr());
            let mut out = [0; 130];
            assert_eq!(mattmc_section_index_query_next(cursor, 1, out.as_mut_ptr()), 0);
            mattmc_section_index_cursor_close(cursor);
            mattmc_entity_geometry_change(body, 1, 0., 0., 0., 1., 1., 1.);
            mattmc_section_index_spatial_reopen(cursor, bounds.as_ptr());
            assert_eq!(mattmc_section_index_query_next(cursor, 1, out.as_mut_ptr()), 1);
            assert_eq!(out[2] >> 63, 1);
            insert(owner, encode(1, 0, 0)); // Simulate a callback populating a future column.
            assert_eq!(mattmc_section_index_query_next(cursor, 1, out.as_mut_ptr()), 130);
            assert_eq!((*cursor.cast::<Lease>()).view.key, encode(1, 0, 0));
            assert_eq!(mattmc_section_index_query_next(cursor, 1, out.as_mut_ptr()), 0);
            mattmc_section_index_cursor_release(cursor);
            mattmc_section_members_release(members);
            mattmc_section_index_release(owner);
            mattmc_entity_geometry_release(body);
            mattmc_entity_geometry_world_release(world);
        }
    }
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
            assert!((*owner.cast::<Index>()).data.lock().unwrap().cursors.iter().all(Option::is_none));
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

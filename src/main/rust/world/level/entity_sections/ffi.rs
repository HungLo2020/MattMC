//! Scalar member mutations and immutable retained CPU-array leases.
use super::{
    bindings::Bindings,
    members::{AddError, Array, Members, RemoveError, View},
};
use std::{
    ffi::c_void,
    sync::{atomic::{AtomicI32, Ordering}, Arc, Mutex},
};
pub(super) struct Context {
    bindings: Arc<Bindings>,
    groups: Mutex<Vec<Box<Members>>>,
    visibility: AtomicI32,
}
#[repr(C)]
pub(super) struct Change {
    source: i32,
    destination: i32,
    length: i32,
    capacity: i32,
}
#[repr(C)]
struct Capture {
    lease: *mut c_void,
    pointer: *const c_void,
    capacity: i32,
    size: i32,
}
#[no_mangle]
pub extern "C" fn mattmc_section_members_create() -> *const c_void {
    let bindings = Arc::new(Bindings::default());
    let root = Box::new(Members::with_bindings(bindings.clone()));
    Arc::into_raw(Arc::new(Context {
        bindings,
        groups: Mutex::new(vec![root]),
        visibility: AtomicI32::new(-1),
    }))
    .cast()
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_members_release(owner: *const c_void) {
    if !owner.is_null() {
        drop(Arc::from_raw(owner.cast::<Context>()));
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_members_visibility_view(owner: *const c_void) -> *const AtomicI32 {
    owner.cast::<Context>().as_ref().map_or(std::ptr::null(), |c| &c.visibility)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_members_visibility(owner: *const c_void, value: i32) -> i32 {
    owner.cast::<Context>().as_ref().map_or(-1, |c| c.visibility.swap(value, Ordering::AcqRel))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_members_view(
    owner: *const c_void,
    group: i32,
) -> *const c_void {
    let Some(c) = owner.cast::<Context>().as_ref() else {
        return std::ptr::null();
    };
    let groups = c.groups.lock().unwrap();
    groups
        .get(group as usize)
        .map_or(std::ptr::null(), |m| (&m.view as *const View).cast())
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_members_new_group(owner: *const c_void) -> i32 {
    let Some(c) = owner.cast::<Context>().as_ref() else {
        return -1;
    };
    let mut groups = c.groups.lock().unwrap();
    let id = i32::try_from(groups.len()).unwrap();
    groups.push(Box::new(Members::with_bindings(c.bindings.clone())));
    id
}
/// Publish a successfully constructed Java class group under a stable CPU type
/// identity. Zero is reserved for untyped queries; no class objects are retained.
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_members_bind_type(owner: *const c_void, group: i32, identity: u64) -> i32 {
    let Some(c) = owner.cast::<Context>().as_ref() else { return -1; };
    if identity == 0 { return -1; }
    let mut groups = c.groups.lock().unwrap();
    if groups.iter().enumerate().any(|(i, m)| i != group as usize && m.query_type == identity) { return -1; }
    let Some(m) = groups.get_mut(group as usize) else { return -1; };
    if m.query_type != 0 && m.query_type != identity { return -1; }
    m.query_type = identity;
    0
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_members_allocate(owner: *const c_void) -> u64 {
    owner
        .cast::<Context>()
        .as_ref()
        .map_or(0, |c| c.bindings.allocate())
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_members_finish_binding(owner: *const c_void, token: u64) {
    if let Some(c) = owner.cast::<Context>().as_ref() {
        c.bindings.release(token);
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_members_retired(owner: *const c_void) -> u64 {
    owner
        .cast::<Context>()
        .as_ref()
        .map_or(0, |c| c.bindings.retired())
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_members_add(
    owner: *const c_void,
    group: i32,
    token: u64,
    out: *mut Change,
) -> i32 {
    let Some(c) = owner.cast::<Context>().as_ref() else {
        return -1;
    };
    let mut groups = c.groups.lock().unwrap();
    let Some(m) = groups.get_mut(group as usize) else {
        return -1;
    };
    match m.add(token) {
        Ok(()) => 0,
        Err(AddError::StoreBounds { index, capacity }) => {
            if let Some(out) = out.as_mut() {
                *out = Change {
                    source: index,
                    destination: 0,
                    length: 0,
                    capacity: capacity as i32,
                };
            }
            2
        }
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_members_capture(
    owner: *const c_void,
    group: i32,
    out: *mut Capture,
) -> i32 {
    let Some(c) = owner.cast::<Context>().as_ref() else {
        return -1;
    };
    let Some(out) = out.as_mut() else { return -1 };
    let groups = c.groups.lock().unwrap();
    let Some(m) = groups.get(group as usize) else {
        return -1;
    };
    let array = m.capture();
    let pointer = array.cells.as_ptr().cast();
    let capacity = array.cells.len() as i32;
    *out = Capture {
        lease: Box::into_raw(Box::new(array)).cast(),
        pointer,
        capacity,
        size: m.size(),
    };
    0
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_members_release_capture(lease: *mut c_void) {
    if !lease.is_null() {
        drop(Box::from_raw(lease.cast::<Arc<Array>>()));
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_members_remove(
    owner: *const c_void,
    group: i32,
    lease: *const c_void,
    index: i32,
    out: *mut Change,
) -> i32 {
    let Some(c) = owner.cast::<Context>().as_ref() else {
        return -1;
    };
    let Some(array) = lease.cast::<Arc<Array>>().as_ref() else {
        return -1;
    };
    let mut groups = c.groups.lock().unwrap();
    let Some(m) = groups.get_mut(group as usize) else {
        return -1;
    };
    match m.remove_from(array, index) {
        Ok(()) => 0,
        Err(RemoveError::CopyBounds {
            source,
            destination,
            length,
            capacity,
        }) => {
            if let Some(out) = out.as_mut() {
                *out = Change {
                    source,
                    destination,
                    length,
                    capacity: capacity as i32,
                }
            };
            1
        }
        Err(RemoveError::StoreBounds { index, capacity }) => {
            if let Some(out) = out.as_mut() {
                *out = Change {
                    source: index,
                    destination: 0,
                    length: 0,
                    capacity: capacity as i32,
                }
            };
            2
        }
    }
}

/// Attach a typed CPU geometry lease once at section insertion. No Java or GPU
/// pointer is retained, and world transfer preserves this view's identity.
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_members_attach_geometry(
    owner: *const c_void, token: u64, body: *const super::super::entity_geometry::Body, non_spectator: i32,
) -> i32 {
    let (Some(c), Some(body)) = (owner.cast::<Context>().as_ref(), body.as_ref()) else { return -1; };
    c.bindings.attach_geometry(token, body.retained_view(), non_spectator != 0);
    0
}

/// Bounded output: revision, next source cursor, then packed match indices.
/// Low 32 bits: binding slot; bits32..62: source index; bit63: bounds matched.
/// Returns count, or -2=concurrent modification, -3=no element, -4=invalid input.
/// Revalidate revision/modification count after Java callbacks before consuming
/// prefetched matches. Never hold either owner lock across behavior calls.
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_members_intersections(
    owner: *const c_void, group: i32, cursor: i32, expected: i32,
    a: f64, b: f64, c: f64, d: f64, e: f64, f: f64,
    output: *mut u64, capacity: i32,
) -> i32 {
    let Some(context) = owner.cast::<Context>().as_ref() else { return -4; };
    if output.is_null() || capacity < 1 || capacity > 128 { return -4; }
    let out = std::slice::from_raw_parts_mut(output, capacity as usize + 2);
    let groups = context.groups.lock().unwrap();
    let Some(m) = groups.get(group as usize) else { return -4; };
    scan(context, m, cursor, expected, [a,b,c,d,e,f], out)
}

/// None yields for Java status/custom-section behavior; zero skips this section.
/// A positive result is the first selected member batch and its iterator version.
pub(super) enum CollectionStep {
    Done,
    Full(i32),
    Callback { cursor: i32, expected: i32 },
    Custom,
}
impl Context {
    /// Append pure stock matches without invoking Java or opening future sections.
    /// Stop at the first bounds/spectator callback, leaving its iterator position intact.
    pub(super) fn collect_non_spectators(&self, bounds: [f64; 6], section: u64,
        mut cursor: i32, out: &mut [u64], count: &mut usize) -> CollectionStep {
        let groups = self.groups.lock().unwrap();
        let members = &groups[0];
        if members.size() == 0 { return CollectionStep::Done; }
        match self.visibility.load(Ordering::Acquire) {
            0 => return CollectionStep::Done,
            1 | 2 => {},
            _ => return CollectionStep::Custom,
        }
        let geometry = self.bindings.geometry();
        while cursor != members.size() {
            let Some(token) = members.get(cursor as usize).filter(|_| cursor >= 0 && cursor < members.size()) else {
                return CollectionStep::Callback { cursor, expected: members.modifications() };
            };
            match geometry.intersects(token, bounds) {
                Some(false) => {},
                Some(true) if geometry.non_spectator(token) => {
                    out[2 * *count] = section;
                    out[2 * *count + 1] = token;
                    *count += 1;
                },
                _ => return CollectionStep::Callback { cursor, expected: members.modifications() },
            }
            cursor += 1;
            if 2 * *count == out.len() { return CollectionStep::Full(cursor); }
        }
        CollectionStep::Done
    }

    pub(super) fn query(&self, bounds: Option<[f64; 6]>, type_identity: u64, out: &mut [u64]) -> Option<(i32, i32)> {
        let groups = self.groups.lock().unwrap();
        let m = &groups[0];
        if m.size() == 0 { return Some((0, 0)); }
        match self.visibility.load(Ordering::Acquire) {
            0 => Some((0, 0)),
            1 | 2 => match bounds {
                Some(bounds) => {
                    // Admission remains lazy: an unknown class must yield to
                    // Java even when its eventual group would contain no hits.
                    let m = if type_identity == 0 { m } else {
                        let Some(group) = groups.iter().find(|m| m.query_type == type_identity) else { return Some((-1, 0)); };
                        group
                    };
                    // Java's typed collection returns before acquiring an
                    // iterator when empty; no geometry lease is needed here.
                    if m.size() == 0 { return Some((0, 0)); }
                    Some((scan(self, m, 0, m.modifications(), bounds, out), m.modifications()))
                },
                None => Some((-1, 0)), // Accessible, but this traversal must not inspect entity bounds.
            },
            _ => None, // Preserve a null visibility's Java failure after the empty check.
        }
    }
}

fn scan(context: &Context, m: &Members, mut cursor: i32, expected: i32, bounds: [f64; 6], out: &mut [u64]) -> i32 {
    let geometry = context.bindings.geometry();
    out[0] = super::super::entity_geometry::bounds_revision();
    let mut count = 0;
    loop {
        // ArrayList's hasNext precedes next's modification check.
        if cursor == m.size() { break; }
        if expected != m.modifications() { return -2; }
        if cursor < 0 || cursor >= m.size() { return -3; }
        let Some(token) = m.get(cursor as usize) else { return -2; };
        let found = geometry.intersects(token, bounds);
        if found != Some(false) {
            // Membership retains this binding until mutation. Java checks the
            // modification count before consuming any cached slot.
            out[count + 2] = u64::from(token as u32) | (u64::from(cursor as u32) << 32)
                | (u64::from(found == Some(true)) << 63);
            count += 1;
        }
        cursor += 1;
        if count == out.len() - 2 { break; }
    }
    out[1] = cursor as u64;
    count as i32
}

#[cfg(test)]
mod spatial_tests {
    use super::*;
    use crate::world::level::entity_geometry::*;

    unsafe fn query(owner: *const c_void, cursor: i32, expected: i32) -> i64 {
        let mut output = [0u64;3];
        let count = mattmc_section_members_intersections(owner,0,cursor,expected,-1.0,-1.0,-1.0,1.0,1.0,1.0,output.as_mut_ptr(),1);
        match count { 0 => -1, 1 => (((output[2] >> 32) & 0x7fff_ffff) | ((output[2] >> 63) << 32)) as i64, error => i64::from(error) }
    }

    #[test]
    fn retained_geometry_tracks_mutations_and_releases_with_membership() {
        unsafe {
            let world=mattmc_entity_geometry_world_create();
            let body=mattmc_entity_geometry_create(world,0.0,0.0,0.0,0.0,0.0,0.0,0.5,0.5,0.5);
            let weak=Arc::downgrade(&(*body).retained_view());
            let owner=mattmc_section_members_create();
            let token=mattmc_section_members_allocate(owner);
            assert_eq!(mattmc_section_members_attach_geometry(owner,token,body,0),0);
            assert_eq!(mattmc_section_members_add(owner,0,token,std::ptr::null_mut()),0);
            mattmc_section_members_finish_binding(owner,token);
            assert_eq!(query(owner,0,1),1i64<<32);
            // Move into a non-intersecting box without replacing the binding.
            mattmc_entity_geometry_change(body,1,10.0,10.0,10.0,11.0,11.0,11.0);
            assert_eq!(query(owner,0,1),-1);
            // Custom and null bounds must yield for Java behavior, even outside.
            mattmc_entity_geometry_change(body,2,10.0,10.0,10.0,11.0,11.0,11.0);
            assert_eq!(query(owner,0,1),0);
            mattmc_entity_geometry_change(body,3,0.0,0.0,0.0,0.0,0.0,0.0);
            assert_eq!(query(owner,0,1),0);
            mattmc_entity_geometry_change(body,1,1.0,0.0,0.0,2.0,1.0,1.0);
            assert_eq!(query(owner,0,1),-1); // Touching faces do not intersect.
            mattmc_entity_geometry_release(body);
            mattmc_entity_geometry_world_release(world);
            assert!(weak.upgrade().is_some());
            mattmc_section_members_release(owner);
            assert!(weak.upgrade().is_none());
        }
    }

    #[test]
    fn cursor_preserves_iterator_end_before_modification_check() {
        unsafe {
            let owner=mattmc_section_members_create();
            assert_eq!(query(owner,0,99),-1);
            let token=mattmc_section_members_allocate(owner);
            let before=super::super::super::entity_geometry::bounds_revision();
            assert_eq!(mattmc_section_members_add(owner,0,token,std::ptr::null_mut()),0);
            assert_ne!(before,super::super::super::entity_geometry::bounds_revision());
            mattmc_section_members_finish_binding(owner,token);
            assert_eq!(query(owner,0,0),-2);
            assert_eq!(query(owner,0,1),0); // Unknown member must yield to Java.
            assert_eq!(query(owner,1,0),-1);
            assert_eq!(query(owner,2,1),-3);
            mattmc_section_members_release(owner);
        }
    }
}

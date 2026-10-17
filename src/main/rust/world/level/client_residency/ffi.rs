//! Private semantic CPU bridge. No Java callback runs under this lock. The
//! header is a read-only projection of Rust policy, not a second mutable cache.
use super::*;
use crate::world::level::chunk::inputs::{Chunk, Sections};
use std::sync::{atomic::{AtomicI32, AtomicU64, Ordering}, Arc, Mutex};

#[repr(C)]
struct View {
    count: AtomicI32,
    x: AtomicI32,
    z: AtomicI32,
    radius: i32,
    fingerprint: AtomicU64,
    revision: AtomicU64,
}
struct State {
    policy: Residency,
    owners: Box<[Option<Arc<Chunk>>]>,
    serial: u64,
    escaped: bool,
    range: range::RangeIndex,
}
struct NativeSource<'a> { owner:Option<&'a Chunk>,position:(i32,i32),min:i32,count:usize }
impl Source for NativeSource<'_> {
    fn position(&self,_:u64)->(i32,i32) {self.position}
    fn sections(&self,_:u64)->usize {self.count}
    fn section_y(&self,_:u64,row:usize)->i32 {self.min.wrapping_add(row as i32)}
    fn empty(&self,_:u64,row:usize)->Result<bool,Fault> {
        Ok(Sections::Retained(self.owner.ok_or(Fault::Input)?).counter(row).unwrap().packed() as u16==0)
    }
}
pub(crate) struct World { view: View, state: Mutex<State> }

/// Camera consumes already-retained chunk bindings, observing live palette and
/// counter mutations. Custom/missing bindings decline instead of reading stale data.
/// Input: position, forward, left, up (12 doubles); no pointer is retained.
#[no_mangle]
pub unsafe extern "C" fn mattmc_client_residency_camera(world: *const World,
    min_y: i32, max_y: i32, input: *const f64, half_height: f64, aspect: f64,
    tags: u32) -> i32 {
    let Some(world) = world.as_ref() else { return -1; };
    let Some(registry) = crate::content::block::installed() else { return -1; };
    if input.is_null() || input as usize % 8 != 0 || min_y > max_y { return -1; }
    let values = std::slice::from_raw_parts(input, 12);
    let position = [values[0], values[1], values[2]];
    let axes = std::array::from_fn(|i| [values[3+i*3], values[4+i*3], values[5+i*3]]);
    let Some(air) = registry.air().map(|id| registry.block(id).default_state()) else { return -1; };
    let state = world.state.lock().unwrap_or_else(|e| e.into_inner());
    // Near-plane points commonly occupy the same cell. Reuse only within this
    // callback-free query; no frame/tick cache can hide subsequent world edits.
    let mut recent = [None; 2]; let mut cursor = 0;
    let mut read = |[x, y, z]: [i32; 3]| {
            for &(position, id) in recent.iter().flatten() {
                if position == [x, y, z] { return Some(id); }
            }
            if y < min_y || y > max_y { return Some(air); }
            let Some(index) = state.range.lookup(&state.policy, x >> 4, z >> 4)? else { return Some(air); };
            let chunk = state.owners[index].as_ref()?;
            let sections = Sections::Retained(chunk);
            let relative = y.checked_sub(chunk.min_y)?;
            if relative < 0 || relative as usize / 16 >= sections.len() { return Some(air); }
            let row = relative as usize / 16;
            if sections.counter(row)?.packed() as u16 == 0 { return Some(air); }
            let id = sections.block(row).world_value(((y & 15) * 256 + (z & 15) * 16 + (x & 15)) as usize)?;
            if id as usize >= registry.state_count() { return None; }
            let id = crate::content::block::StateId(id as u16);
            recent[cursor] = Some(([x, y, z], id)); cursor ^= 1;
            Some(id)
        };
    use crate::world::level::camera_environment::{query, NONE};
    let result = query(registry, position, &axes, half_height, aspect, tags, false, &mut read);
    if result != Some(NONE) { return result.unwrap_or(-1); }
    query(registry, position, &axes, half_height, aspect, tags, true, &mut read).unwrap_or(-1)
}
impl World {
    fn project(&self, state: &State) {
        self.view.count.store(state.policy.count, Ordering::Release);
        self.view.fingerprint.store(state.policy.position_fingerprint, Ordering::Release);
    }
}
#[no_mangle]
pub extern "C" fn mattmc_client_residency_create(radius: i32) -> *mut World {
    let Some(policy) = Residency::new(radius) else { return std::ptr::null_mut(); };
    let owners = (0..policy.slots.len()).map(|_|None).collect::<Vec<_>>().into_boxed_slice();
    let range = range::RangeIndex::new(policy.slots.len());
    Box::into_raw(Box::new(World {view: View {count:AtomicI32::new(0),x:AtomicI32::new(0),z:AtomicI32::new(0),radius,fingerprint:AtomicU64::new(0),revision:AtomicU64::new(0)},
        state:Mutex::new(State {policy,owners,serial:0,escaped:false,range})}))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_client_residency_release(world: *mut World) {
    if !world.is_null() { drop(Box::from_raw(world)); }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_client_residency_view(world: *const World) -> *const std::ffi::c_void {
    if world.is_null() { std::ptr::null() } else { &(*world).view as *const View as *const _ }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_client_residency_identity(world:*const World,index:i32) -> u64 {
    let Some(world)=world.as_ref() else {return 0;};
    let state=world.state.lock().unwrap_or_else(|e|e.into_inner());
    if index<0 {0} else {state.policy.slots.get(index as usize).copied().unwrap_or(0)}
}
/// action 0 publishes a reference, 1 is a successful Java-reference CAS drop,
/// 2 increments the admitted count, 3 changes center, 4 toggles position hash.
/// Java-reference identity is a bounded semantic fact, not a GPU/runtime handle.
#[no_mangle]
pub unsafe extern "C" fn mattmc_client_residency_change(world: *const World, action:i32, index:i32, a:i32, b:i32) -> i64 {
    let Some(world)=world.as_ref() else {return -1;};
    let mut state=world.state.lock().unwrap_or_else(|e|e.into_inner());
    let result=match action {
        0|1|5 => {
            if index<0 || index as usize>=state.policy.slots.len() {return -1;}
            let id=if action==5 || action==0 && a!=0 {
                if state.serial==i64::MAX as u64 {return -1;}
                state.serial+=1;state.serial
            } else {0};
            state.policy.publish(index as usize,id).unwrap();state.owners[index as usize]=None;id as i64
        },
        2 => {state.policy.adjust_count(1);0},
        3 => {state.policy.center=(a,b);world.view.x.store(a,Ordering::Release);world.view.z.store(b,Ordering::Release);0},
        4 => {state.policy.position_changed((a,b));0},
        _=>return -1,
    };
    if action==0 || action==1 || action==5 {
        let indexed=result!=0 && (action!=5 || state.policy.index(a,b)==index as usize);
        state.range.publish(index as usize, indexed, if action==5 {Some((a,b))} else {None});
    }
    if action==0 || action==1 || action==3 || action==5 {
        world.view.revision.fetch_add(1,Ordering::Release);
    }
    world.project(&state);result
}
/// Borrowed page of physical indices and immutable stock coordinates. The
/// page revision must be checked between external callbacks; no lock/borrow
/// crosses a callback. No Java object address or GPU handle is retained.
#[no_mangle]
pub unsafe extern "C" fn mattmc_client_residency_range(world:*const World,
    min_x:i32,max_x:i32,min_z:i32,max_z:i32,cursor_x:i32,cursor_z:i32,
    output:*mut i64,length:i32) -> i32 {
    let Some(world)=world.as_ref() else {return -1;};
    if output.is_null() || output as usize%8!=0 || !(5..=194).contains(&length)
        || (length-2)%3!=0 || min_x>max_x || min_z>max_z
        || cursor_x<min_x || cursor_x>max_x || cursor_z<min_z || cursor_z>max_z {return -1;}
    let state=world.state.lock().unwrap_or_else(|e|e.into_inner());
    let buffer=std::slice::from_raw_parts_mut(output,length as usize);
    let Some(count)=state.range.page(&state.policy,[min_x,max_x,min_z,max_z],(cursor_x,cursor_z),&mut buffer[2..]) else {return -2;};
    buffer[0]=count as i64;
    buffer[1]=world.view.revision.load(Ordering::Acquire) as i64;
    count as i32
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_client_residency_empty(world:*const World, key:u64, empty:i32) -> i32 {
    let Some(world)=world.as_ref() else {return -1;};
    let mut state=world.state.lock().unwrap_or_else(|e|e.into_inner());
    if state.escaped {return -1;}
    i32::from(state.policy.empty_changed(key,empty!=0))
}
/// mode 0 drops the immutable stock geometry; modes 1/2 add/refresh directly
/// from retained mutable counters. A refresh stops immediately after one
/// removal and writes its key/cursor. Re-admission follows the Java callback.
/// A null owner on refresh invalidates a slot's previous retained binding.
#[no_mangle]
pub unsafe extern "C" fn mattmc_client_residency_scan(world:*const World, owner:*const Chunk,
    index:i32,id:u64,x:i32,z:i32,min:i32,count:i32,cursor:i32,mode:i32,result:*mut u64) -> i32 {
    let Some(world)=world.as_ref() else {return -1;};
    if !(0..=256).contains(&count) || cursor<0 || cursor>count || !(0..=3).contains(&mode) {return -1;}
    if mode==1 || mode==2 {
        if owner.is_null() || result.is_null() || result as usize%8!=0 {return -1;}
        let source=Sections::Retained(&*owner);
        if source.len()!=count as usize || (*owner).min_y/16!=min {return -1;}
    }
    let mut state=world.state.lock().unwrap_or_else(|e|e.into_inner());
    if id!=0 && index>=0 && (index as usize)<state.owners.len() && state.policy.slots[index as usize]==id {
        let slot=&mut state.owners[index as usize];
        if owner.is_null() {*slot=None;}
        else if slot.as_ref().is_none_or(|old|!std::ptr::eq(Arc::as_ptr(old),owner)) {
            Arc::increment_strong_count(owner);*slot=Some(Arc::from_raw(owner));
        }
    }
    if mode==3 {return 0;}
    if state.escaped {return -1;}
    let source=NativeSource {owner:owner.as_ref(),position:(x,z),min,count:count as usize};
    match state.policy.scan_empty(id,&source,cursor as usize,mode) {
        Ok(Some((key,next)))=>{result.write(key);result.add(1).write(next as u64);1},
        Ok(None)=>0,Err(_)=>-1,
    }
}
/// Immutable table lease; exact physical layout preserves fastutil iteration,
/// future insertion/deletion, zero handling and default minimum capacity.
pub(crate) struct Snapshot { size:usize,zero:bool,keys:Box<[u64]> }
#[no_mangle]
pub unsafe extern "C" fn mattmc_client_residency_snapshot(world:*const World,detach:i32) -> *mut Snapshot {
    let Some(world)=world.as_ref() else {return std::ptr::null_mut();};
    let mut state=world.state.lock().unwrap_or_else(|e|e.into_inner());
    if state.escaped {return std::ptr::null_mut();}
    let snapshot=Box::new(Snapshot {size:state.policy.empty.size,zero:state.policy.empty.zero,keys:state.policy.empty.keys.clone().into_boxed_slice()});
    if detach!=0 {state.escaped=true;state.policy.empty=EmptySet::new();}
    Box::into_raw(snapshot)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_client_residency_snapshot_value(snapshot:*const Snapshot,field:i32) -> i64 {
    let Some(snapshot)=snapshot.as_ref() else {return -1;};
    match field {0=>snapshot.keys.len() as i64,1=>snapshot.size as i64,2=>i64::from(snapshot.zero),_=>-1}
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_client_residency_snapshot_copy(snapshot:*const Snapshot,keys:*mut u64,length:i32) -> i32 {
    let Some(snapshot)=snapshot.as_ref() else {return -1;};
    if length<0 || length as usize!=snapshot.keys.len() || keys.is_null() || keys as usize%8!=0 {return -1;}
    std::ptr::copy_nonoverlapping(snapshot.keys.as_ptr(),keys,snapshot.keys.len());0
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_client_residency_snapshot_release(snapshot:*mut Snapshot) {
    if !snapshot.is_null() {drop(Box::from_raw(snapshot));}
}

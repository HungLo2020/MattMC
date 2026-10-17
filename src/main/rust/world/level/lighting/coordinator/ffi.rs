//! CPU-only bridge. Each call owns its guard to completion and publishes only
//! immutable outputs/atomic projections before Java can perform an effect.
use super::*;
use std::{panic::{catch_unwind, AssertUnwindSafe}, sync::{Mutex, atomic::{AtomicI32, AtomicU32, AtomicU64, Ordering}}};

static LIVE: AtomicU64 = AtomicU64::new(0);
#[repr(C)]
struct Header {
    sizes: [AtomicI32; 8],
    inconsistent: AtomicI32,
    sky_seeds: AtomicU32,
    phases: [AtomicU64; 4],
    bytes: AtomicU64,
    saved_layers: AtomicU32,
    retiring_save_layers: AtomicU32,
}
pub(super) struct Owner { header: Header, state: Mutex<State>, refreshed_ids: Mutex<Vec<u32>> }
impl Drop for Owner { fn drop(&mut self) { LIVE.fetch_sub(1, Ordering::Relaxed); } }
pub(super) fn publish(owner: &Owner, state: &mut State) {
    state.cleanup_save_columns();
    owner.header.saved_layers.store(state.save_count.min(u32::MAX as usize) as u32, Ordering::Release);
    owner.header.retiring_save_layers.store(state.retiring_save.len().min(u32::MAX as usize) as u32, Ordering::Release);
    for (target, table) in owner.header.sizes.iter().zip(&state.tables) { target.store(table.len(), Ordering::Release); }
    owner.header.sky_seeds.store(state.sky_seeds,Ordering::Release);
    owner.header.inconsistent.store(i32::from(state.inconsistent), Ordering::Release);
    for (target, count) in owner.header.phases.iter().zip(state.phase_counts) { target.store(count, Ordering::Release); }
    let publication_pending = state.tables[CHANGED].len() != 0 || state.tables[AFFECTED].len() != 0;
    for engine in state.engines.values() {
        engine.projection.nodes.store(engine.nodes.len(), Ordering::Release);
        let pending = state.inconsistent || engine.nodes.len() != 0;
        // Only exact stock engines have unobservable empty propagation leaves.
        // An empty table enlarged by iterator removal still needs original trim.
        engine.queues.publish_coordinator(&engine.projection, pending,
            !pending && !publication_pending && engine.kind != 2 && engine.nodes.table_len() <= 1025);
    }
    owner.header.bytes.store((state.tables.iter().map(Table::allocation_bytes).sum::<usize>()
        + state.engines.values().map(|e| e.nodes.allocation_bytes()).sum::<usize>() + state.save_bytes) as u64, Ordering::Release);
}
unsafe fn retain<T>(pointer: *const T) -> Arc<T> { Arc::increment_strong_count(pointer); Arc::from_raw(pointer) }
fn failure(error: Error, out: &mut [i64]) -> i32 {
    match error {
        Error::Table(Fault::Allocation) => -20,
        Error::Table(Fault::NullPointer) => -21,
        Error::Table(Fault::ArrayIndex(index, length)) => { out[0] = i64::from(index); out[1] = length as i64; -22 }
        Error::Table(Fault::IndexOutOfBounds(index, length)) => { out[0] = i64::from(index); out[1] = length as i64; -23 }
        Error::Table(Fault::NoSuchElement) => -24,
        Error::Table(Fault::IllegalState) => -25,
        Error::Neighbours => -26,
        Error::MissingOwner => -27,
        Error::Unsupported => -28,
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_coordinator_create(map: *const LightMap, sky: i32) -> *mut Owner {
    if map.is_null() { return std::ptr::null_mut(); }
    let Ok(state) = State::new(sky != 0, retain(map)) else { return std::ptr::null_mut(); };
    let owner = Arc::new(Owner { header: Header { sizes: std::array::from_fn(|_| AtomicI32::new(0)), inconsistent: AtomicI32::new(0),
        sky_seeds:AtomicU32::new(0), phases: std::array::from_fn(|_| AtomicU64::new(0)), bytes: AtomicU64::new(0), saved_layers: AtomicU32::new(0), retiring_save_layers: AtomicU32::new(0) }, state: Mutex::new(state), refreshed_ids: Mutex::new(Vec::new()) });
    publish(&owner, &mut owner.state.lock().unwrap()); LIVE.fetch_add(1, Ordering::Relaxed); Arc::into_raw(owner) as *mut Owner
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_coordinator_release(owner: *mut Owner) { if !owner.is_null() { drop(Arc::from_raw(owner)); } }
#[no_mangle]
pub extern "C" fn mattmc_light_coordinator_live() -> u64 { LIVE.load(Ordering::Relaxed) }
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_coordinator_engines(owner: *const Owner) -> i32 {
    owner.as_ref().map_or(-1, |owner| owner.state.lock().unwrap().engines.len() as i32)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_coordinator_header(owner: *const Owner) -> *const Header {
    owner.as_ref().map_or(std::ptr::null(), |o| &o.header)
}
pub(super) struct Binding { owner: Arc<Owner>, id: u64, projection: Arc<EngineProjection>, levels:Arc<LevelFollowers> }
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_coordinator_bind(owner: *const Owner, queues: *const Queues, kind: i32) -> *mut Binding {
    if owner.is_null() || queues.is_null() || !(0..3).contains(&kind) { return std::ptr::null_mut(); }
    let mut state = (*owner).state.lock().unwrap();
    let Ok((id, projection)) = state.attach(retain(queues), kind) else { return std::ptr::null_mut(); };
    let levels=Arc::clone(&state.engines.get(&id).unwrap().levels);
    publish(&*owner, &mut state);
    drop(state);
    Box::into_raw(Box::new(Binding { owner: retain(owner), id, projection, levels }))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_coordinator_bind_header(binding: *const Binding) -> *const EngineProjection {
    binding.as_ref().map_or(std::ptr::null(), |binding| Arc::as_ptr(&binding.projection))
}
/// Binding and frame leases retain the actual storage allocation independently
/// of Java facade cleanup, without a reciprocal owner-to-binding root.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_coordinator_bind_release(binding: *mut Binding) {
    if !binding.is_null() {
        let binding = Box::from_raw(binding);
        binding.owner.state.lock().unwrap().detach(binding.id);
    }
}
unsafe fn engine_id(owner: *const Owner, binding: *const Binding) -> Result<u64, Error> {
    if binding.is_null() { return Ok(0); }
    if Arc::as_ptr(&(*binding).owner) != owner { return Err(Error::MissingOwner); }
    Ok((*binding).id)
}

/// Collection output is [return, diagnostic length]. The queued value is a
/// borrowed typed entry, retained before this call returns. Returned identities
/// must be resolved by Java before draining its map registry retirement.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_coordinator_collection(owner: *const Owner, binding: *const Binding, which: i32, op: i32,
    key: i64, value: i64, entry: *const Entry, output: *mut i64) -> i32 {
    if owner.is_null() || output.is_null() || !(0..8).contains(&which) { return -27; }
    let o = &*owner; let out = std::slice::from_raw_parts_mut(output, 2); out.fill(0);
    catch_unwind(AssertUnwindSafe(|| {
        let mut state = o.state.lock().unwrap(); let which = which as usize;
        let Ok(engine) = engine_id(owner, binding) else { return -27; };
        let result: Result<i64, Error> = (|| Ok(match op {
            0 => i64::from(state.table(engine, which)?.find(key).is_ok()),
            1 => i64::from(state.table(engine, which)?.get(key)),
            2 if which == QUEUED => i64::from(state.put_queued(key, if entry.is_null() { None } else { Some(retain(entry)) })?),
            2 => i64::from(state.table_mut(engine, which)?.put(key, value as u32)?),
            3 if which == QUEUED => i64::from(id(&state.remove_queued(key)?)),
            3 => i64::from(state.table_mut(engine, which)?.remove(key)?),
            4 => i64::from(state.table(engine, which)?.len()),
            5 => { state.table_mut(engine, which)?.clear_membership(); 0 },
            6 => i64::from(state.state_default),
            7 => { state.state_default = value as u8; 0 },
            8 => i64::from(state.table(engine, which)?.key_at(value as i32)?),
            9 => i64::from(state.table(engine, which)?.value_at(value as i32)?),
            10 => { state.inconsistent = value != 0; 0 },
            11 => {
                let new_value = if which == QUEUED { if entry.is_null() { 0 } else { (*entry).id } } else { value as u32 };
                let table = state.table_mut(engine, which)?;
                let previous = table.set_value_at(key as i32,new_value,
                    if which==QUEUED && !entry.is_null() {Some(retain(entry))}else{None})?;
                i64::from(previous)
            },
            12 if which == QUEUED => { state.queued_default = if entry.is_null() { None } else { Some(retain(entry)) }; 0 },
            13 if which == QUEUED => i64::from(id(&state.queued_default)),
            _ => return Err(Error::MissingOwner),
        }))();
        if matches!(op, 2 | 3 | 5 | 7 | 10 | 11 | 12) { publish(o, &mut state); }
        match result { Ok(value) => { out[0] = value; 0 }, Err(error) => failure(error, out) }
    })).unwrap_or(-99)
}

pub(super) struct Walk { owner: Arc<Owner>, which: usize, engine: u64, cursor: Mutex<Cursor> }
impl Drop for Walk {fn drop(&mut self){self.owner.state.lock().unwrap().release_engine(self.engine);}}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_coordinator_walk_create(owner: *const Owner, binding: *const Binding, which: i32) -> *mut Walk {
    if owner.is_null() || !(0..8).contains(&which) { return std::ptr::null_mut(); }
    let mut state = (*owner).state.lock().unwrap();
    let Ok(engine) = engine_id(owner, binding) else { return std::ptr::null_mut(); };
    let Ok(table) = state.table(engine, which as usize) else { return std::ptr::null_mut(); };
    let cursor = table.cursor();
    if state.retain_engine(engine).is_err() { return std::ptr::null_mut(); }
    Arc::into_raw(Arc::new(Walk { owner: retain(owner), which: which as usize, engine, cursor:Mutex::new(cursor) })) as *mut Walk
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_coordinator_walk_release(walk: *mut Walk) {
    if !walk.is_null() {
        drop(Arc::from_raw(walk));
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_coordinator_walk(owner: *const Owner, walk: *mut Walk, op: i32, output: *mut i64) -> i32 {
    if owner.is_null() || walk.is_null() || output.is_null() { return -27; }
    let o = &*owner; let w = &*walk; let out = std::slice::from_raw_parts_mut(output, 3); out.fill(0);
    if Arc::as_ptr(&w.owner) != owner { return -27; }
    catch_unwind(AssertUnwindSafe(|| {
        let mut state = o.state.lock().unwrap();
        let mut cursor=w.cursor.lock().unwrap();
        let result: Result<(), Error> = (|| {
            match op {
                0 => {
                    let table = state.table(w.engine, w.which)?;
                    if w.which!=STATES && w.which!=QUEUED {
                        out[0]=cursor.next_key(table)?;out[1]=1;out[2]=i64::from(cursor.mark().last());
                    }else{
                        let at=cursor.next_entry(table)?;
                        out[0] = table.key_at(at)?; out[1] = i64::from(table.value_at(at)?); out[2] = i64::from(at);
                    }
                }
                1 => {
                    if w.which!=STATES && w.which!=QUEUED {cursor.remove_set(state.table_mut(w.engine,w.which)?)?;}
                    else{cursor.remove(state.table_mut(w.engine, w.which)?)?;}
                }
                2 => { out[0] = i64::from(cursor.has_next()); }
                _ => return Err(Error::MissingOwner),
            }
            Ok(())
        })();
        if op == 1 { publish(o, &mut state); }
        match result { Ok(()) => 0, Err(error) => failure(error, out) }
    })).unwrap_or(-99)
}

enum BulkState { Iterator(ordered::Bulk), Range(Arc<Split>,ordered::RangeBulk) }
struct BulkLease { walk:Arc<Walk>, bulk:Mutex<BulkState> }
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_coordinator_bulk_begin(walk:*const Walk,mode:i32)->u64{
    let Some(walk_ref)=walk.as_ref() else{return 0;};
    let state=walk_ref.owner.state.lock().unwrap();
    let Ok(table)=state.table(walk_ref.engine,walk_ref.which) else{return 0;};
    let Ok(bulk)=ordered::Bulk::new(mode,table) else{return 0;};
    Box::into_raw(Box::new(BulkLease{walk:retain(walk),bulk:Mutex::new(BulkState::Iterator(bulk))})) as u64
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_coordinator_bulk_step(lease:u64,commit:i32,output:*mut i64)->i32{
    if lease==0 || output.is_null(){return -27;}
    let lease=&*(lease as *const BulkLease);let out=std::slice::from_raw_parts_mut(output,3);out.fill(0);
    catch_unwind(AssertUnwindSafe(||{
        let state=lease.walk.owner.state.lock().unwrap();
        let mut bulk=lease.bulk.lock().unwrap();
        let result=(||{
            let table=state.table(lease.walk.engine,lease.walk.which)?;
            match &mut *bulk {
                BulkState::Iterator(bulk)=>{
                    let mut cursor=lease.walk.cursor.lock().unwrap();
                    if commit!=0{bulk.commit(&mut cursor);Ok(None)}else{Ok::<_,Error>(bulk.next(&mut cursor,table)?)}
                },
                BulkState::Range(range,bulk)=>{
                    let mut split=range.state.lock().unwrap();
                    if split.pending.is_some(){return Err(Error::MissingOwner);}
                    if commit!=0{bulk.commit(&mut split.range);Ok(None)}else{Ok(bulk.next(&mut split.range,table)?)}
                },
            }
        })();
        match result {
            Ok(Some((at,key)))=>{out[0]=1;out[1]=i64::from(at);out[2]=key;0},
            Ok(None)=>0,
            Err(error)=>failure(error,out),
        }
    })).unwrap_or(-99)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_coordinator_bulk_end(lease:u64){
    if lease!=0 {drop(Box::from_raw(lease as *mut BulkLease));}
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_light_coordinator_begin(owner: *const Owner, binding: *const Binding, op: i32, a: i64, b: i64) -> u64 {
    if owner.is_null() { return 0; }
    let op = match op {
        0 => Op::Status(a, b != 0), 1 => Op::Reconcile, 2 => Op::Publish,
        3 => Op::WriteStored(a, b as i32), 4 => Op::Writable(a), 5 => Op::Run, 6 => Op::Node(a),
        7 => Op::ResultWalk(a as u64, 0, (b as u64 >> 32) as usize, (b as u64 & 0xffffffff) as usize),
        8 => Op::PutState(a, b as u8), 9 => Op::AffectSection(a),
        _ => return 0,
    };
    let Ok(engine) = engine_id(owner, binding) else { return 0; };
    let Ok(id) = (*owner).state.lock().unwrap().begin(op, engine) else { return 0; };
    Box::into_raw(Box::new(FrameLease { owner: retain(owner), id })) as u64
}
struct FrameLease { owner: Arc<Owner>, id: u64 }
/// Copies bounded immutable CPU inputs into the actual continuation owner.
/// No Java pointer or borrowed CPU span survives this admission call.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_coordinator_seed_begin(owner:*const Owner,binding:*const Binding,chunk:i64,limits:i64,columns:*const i32)->u64{
    if owner.is_null()||binding.is_null()||columns.is_null(){return 0;}
    let Ok(engine)=engine_id(owner,binding) else{return 0;};
    let columns=std::slice::from_raw_parts(columns,propagation::seed::COLUMNS).to_vec().into_boxed_slice();
    let op=Op::SeedSky(chunk,(limits>>32) as i32,limits as i32,columns);
    let Ok(id)=(*owner).state.lock().unwrap().begin(op,engine) else{return 0;};
    Box::into_raw(Box::new(FrameLease{owner:retain(owner),id})) as u64
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_light_coordinator_collection_begin(owner:*const Owner,binding:*const Binding,
    which:i32,kind:i32,key:i64,input:u32,entry:*const Entry)->u64{
    if owner.is_null() || !matches!(which,1|5) || !(0..10).contains(&kind){return 0;}
    let Ok(engine)=engine_id(owner,binding) else{return 0;};
    let edit=CollectionEdit{which:which as usize,kind,key,pos:0,input:if which==5 && !entry.is_null(){(*entry).id}else{input},
        input_entry:if entry.is_null(){None}else{Some(retain(entry))},held_old:None};
    let op=if kind==9{Op::ValuesStart(which as usize,input)}else{Op::CollectionStart(edit)};
    let Ok(id)=(*owner).state.lock().unwrap().begin(op,engine) else{return 0;};
    Box::into_raw(Box::new(FrameLease{owner:retain(owner),id})) as u64
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_coordinator_end(owner: *const Owner, id: u64) -> i32 {
    if owner.is_null() || id == 0 { return -27; }
    let lease = &*(id as *const FrameLease);
    if Arc::as_ptr(&lease.owner) != owner { return -27; }
    let lease = Box::from_raw(id as *mut FrameLease);
    let result = { let mut state = lease.owner.state.lock().unwrap(); state.end(lease.id) };
    match result { Ok(()) => 0, Err(_) => -27 }
}

/// Capture the actual Source allocation in this already-waiting query frame.
/// Caller pins the facade through this synchronous borrow; no VM pointer stays.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_coordinator_bind_source(owner:*const Owner,
    id:u64,source:*const super::super::source_owner::Source)->i32 {
    if owner.is_null() || id==0 || source.is_null()
        || source as usize % std::mem::align_of::<super::super::source_owner::Source>() != 0 { return -27; }
    let lease=&*(id as *const FrameLease);
    if Arc::as_ptr(&lease.owner)!=owner { return -27; }
    catch_unwind(AssertUnwindSafe(|| (*owner).state.lock().unwrap()
        .bind_source(lease.id,&*source).map_or(-27,|()|0))).unwrap_or(-99)
}

/// Capture an existing section allocation; 1 declines without changing the frame.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_coordinator_bind_block(owner:*const Owner,id:u64,
    section:*const crate::world::level::chunk::live::Owner,index:i32)->i32 {
    if owner.is_null() || id==0 || section.is_null()
        || section as usize % std::mem::align_of::<crate::world::level::chunk::live::Owner>()!=0{return -27;}
    let lease=&*(id as *const FrameLease);
    if Arc::as_ptr(&lease.owner)!=owner{return -27;}
    catch_unwind(AssertUnwindSafe(||(*owner).state.lock().unwrap().bind_block(lease.id,section,index as usize))).unwrap_or(-99)
}

/// Diagnostic weak handle to the actual section Arc. It owns no section or VM root.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_coordinator_block_probe_new(section:*const crate::world::level::chunk::live::Owner)->u64 {
    if section.is_null(){return 0;}
    let owner=retain(section);
    Box::into_raw(Box::new(Arc::downgrade(&owner))) as u64
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_coordinator_block_probe_owners(probe:u64)->u64 {
    if probe==0{return 0;}
    (*(probe as *const std::sync::Weak<crate::world::level::chunk::live::Owner>)).strong_count() as u64
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_coordinator_block_probe_release(probe:u64) {
    if probe!=0{drop(Box::from_raw(probe as *mut std::sync::Weak<crate::world::level::chunk::live::Owner>));}
}

/// Output [a,b,c,keep-VM-block-state,changed-slot-address,changed-slot-count]. Consume the
/// bounded projection before another coordinator call or any Java effect.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_coordinator_step(owner: *const Owner, id: u64, scalar: i64,
    entry: *const Entry, output: *mut i64) -> i32 {
    if owner.is_null() || output.is_null() || id == 0 { return -27; }
    let lease = &*(id as *const FrameLease);
    if Arc::as_ptr(&lease.owner) != owner { return -27; }
    let o = &*owner; let out = std::slice::from_raw_parts_mut(output, 6); out.fill(0);
    catch_unwind(AssertUnwindSafe(|| {
        let mut state = o.state.lock().unwrap();
        let result = state.step(lease.id, scalar, if entry.is_null() { None } else { Some(retain(entry)) });
        out[3]=state.frames.get(lease.id.wrapping_sub(1)as usize).and_then(Option::as_ref).map_or(0,|frame|i64::from(frame.vm_block_state));
        let mut ids = o.refreshed_ids.lock().unwrap(); ids.clear(); ids.extend(state.refreshed.iter().map(|e| e.id));
        out[4] = ids.as_ptr() as i64; out[5] = ids.len() as i64; publish(o, &mut state);
        match result {
            Ok(Effect::Done(value)) => { out[0] = value; 0 },
            Ok(Effect::Repeat(slot)) => { out[0] = i64::from(slot); 1 },
            Ok(Effect::Copy(slot)) => { out[0] = i64::from(slot); 2 },
            Ok(Effect::Read(slot, index)) => { out[0] = i64::from(slot); out[1] = i64::from(index); 3 },
            Ok(Effect::Write(slot, index, value)) => { out[0] = i64::from(slot); out[1] = i64::from(index); out[2] = i64::from(value); 4 },
            Ok(Effect::Publish(map)) => { out[0] = Arc::into_raw(map) as i64; 5 },
            Ok(Effect::Notify(key)) => { out[0] = key; 6 },
            Ok(Effect::BlockState(pos)) => { out[0] = pos; 7 },
            Ok(Effect::SkySource(x, z, default)) => { out[0] = i64::from(x); out[1] = i64::from(z); out[2] = i64::from(default); 8 },
            Ok(Effect::Propagate) => 9,
            Ok(Effect::ClearChunkCache) => 10,
            Ok(Effect::BlockEmission) => 11,
            Ok(Effect::BlockEmpty) => 12,
            Ok(Effect::InstallResult(slot, index)) => { out[0] = i64::from(slot); out[1] = index as i64; 13 },
            Ok(Effect::CustomNode(pos)) => { out[0] = pos; 14 },
            Ok(Effect::CollectionCall(leaf,key,previous))=>{out[0]=i64::from(leaf);out[1]=key;out[2]=i64::from(previous);15},
            Ok(Effect::HighestSource(..)|Effect::LayerEmpty(..)|Effect::LayerFill(..)|Effect::BlockSources(..)|Effect::SkySources(..))=>-28,
            Err(error) => failure(error, out),
        }
    })).unwrap_or(-99)
}

pub(super) struct Split {
    walk:Arc<Walk>,
    state:Mutex<SplitState>,
}
struct SplitState {
    range:ordered::Range,
    pending:Option<(Arc<Split>,ordered::Range)>,
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_coordinator_split_create(owner:*const Owner,binding:*const Binding,which:i32)->*const Split{
    let pointer=mattmc_light_coordinator_walk_create(owner,binding,which);
    if pointer.is_null(){return std::ptr::null();}
    let walk=Arc::from_raw(pointer);
    let range={
        let state=walk.owner.state.lock().unwrap();
        let Ok(table)=state.table(walk.engine,walk.which) else{return std::ptr::null();};
        ordered::Range::new(table)
    };
    Arc::into_raw(Arc::new(Split{walk,state:Mutex::new(SplitState{range,pending:None})}))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_coordinator_split_release(pointer:*const Split){
    if !pointer.is_null(){drop(Arc::from_raw(pointer));}
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_coordinator_split_prepare(pointer:*const Split)->*const Split{
    let Some(parent)=pointer.as_ref() else{return std::ptr::null();};
    let _state=parent.walk.owner.state.lock().unwrap();
    let state=parent.state.lock().unwrap();
    if state.pending.is_some(){return std::ptr::null();}
    let Some(child)=state.range.proposal() else{return std::ptr::null();};
    Arc::into_raw(Arc::new(Split{walk:Arc::clone(&parent.walk),state:Mutex::new(SplitState{
        range:child,pending:Some((retain(pointer),state.range)),
    })}))
}
/// Called only after the Java wrapper has been fully allocated/adopted.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_coordinator_split_commit(pointer:*const Split)->i32{
    let Some(child)=pointer.as_ref() else{return -27;};
    let _state=child.walk.owner.state.lock().unwrap();
    let mut child_state=child.state.lock().unwrap();
    let Some((parent,expected))=child_state.pending.as_ref() else{return -27;};
    {
        let mut parent_state=parent.state.lock().unwrap();
        if parent_state.range!=*expected{return -27;}
        parent_state.range.commit_split(&child_state.range);
    }
    child_state.pending=None;0
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_coordinator_split_query(pointer:*const Split,op:i32,arg:i64,output:*mut i64)->i32{
    if pointer.is_null() || output.is_null(){return -27;}
    let range=&*pointer;let out=std::slice::from_raw_parts_mut(output,2);out.fill(0);
    catch_unwind(AssertUnwindSafe(||{
        let state=range.walk.owner.state.lock().unwrap();let mut split=range.state.lock().unwrap();
        if split.pending.is_some(){return -27;}
        let result=(||{
            let table=state.table(range.walk.engine,range.walk.which)?;
            match op{
                0=>{let (estimate,flags)=split.range.info(table,arg as i32);out[0]=estimate;out[1]=flags;},
                1=>{out[0]=split.range.skip(table,arg)?;},
                _=>return Err(Error::Unsupported),
            }
            Ok::<_,Error>(())
        })();
        match result{Ok(())=>0,Err(error)=>failure(error,out)}
    })).unwrap_or(-99)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_coordinator_split_bulk_begin(pointer:*const Split,all:i32)->u64{
    let Some(range)=pointer.as_ref() else{return 0;};
    let state=range.walk.owner.state.lock().unwrap();let split=range.state.lock().unwrap();
    if split.pending.is_some(){return 0;}
    let Ok(table)=state.table(range.walk.engine,range.walk.which) else{return 0;};
    let bulk=ordered::RangeBulk::new(table,all!=0,range.walk.which!=STATES && range.walk.which!=QUEUED);
    Box::into_raw(Box::new(BulkLease{walk:Arc::clone(&range.walk),bulk:Mutex::new(BulkState::Range(retain(pointer),bulk))})) as u64
}

/// The transfer receipt is written before consuming a validated reservation.
/// A rejected call leaves the reservation caller-owned; every later exit owns
/// and drops it or its installed entry exactly once, including table failure.
pub(super) unsafe fn queue_admit(
    state: &mut State, key: i64,
    reservation: *mut super::super::maps::registry::Reservation,
    layer: *const super::super::layers::Layer, identity: u64,
    output: &mut [i64], after_transfer: impl FnOnce() -> Result<(), Error>,
) -> Result<(), Error> {
    if reservation.is_null() {
        if !layer.is_null() || identity != 0 { return Err(Error::MissingOwner); }
        state.remove_queued(key)?;
        return Ok(());
    }
    if identity == 0 || !state.updating.owns_reservation(&*reservation) {
        return Err(Error::MissingOwner);
    }
    output[2] = 1;
    let reservation = *Box::from_raw(reservation);
    let entry = state.updating.entry(reservation,
        if layer.is_null() { None } else { Some(retain(layer)) }, identity)
        .ok_or(Error::MissingOwner)?;
    after_transfer()?;
    state.put_queued(key, Some(entry))?;
    state.inconsistent = true;
    Ok(())
}

/// Void public saved-layer admission. Protected Map.put keeps its independent
/// return/default/collection contract. No Java effect occurs under this guard.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_coordinator_queue(
    owner: *const Owner, key: i64,
    reservation: *mut super::super::maps::registry::Reservation,
    layer: *const super::super::layers::Layer, identity: u64, output: *mut i64,
) -> i32 {
    if output.is_null() { return -27; }
    let out = std::slice::from_raw_parts_mut(output, 3); out.fill(0);
    if owner.is_null() { return -27; }
    let owner = &*owner;
    catch_unwind(AssertUnwindSafe(|| {
        let mut state = owner.state.lock().unwrap();
        let result = queue_admit(&mut state, key, reservation, layer, identity, out, || Ok(()));
        // A failed insertion can preserve its original partial table prefix.
        publish(owner, &mut state);
        match result { Ok(()) => 0, Err(error) => failure(error, out) }
    })).unwrap_or(-99)
}

/// Same queued ownership transfer as the individual protocol, using an actual
/// reserved native cell. Rejection leaves it ready; consumption is explicit.
pub(super) unsafe fn queue_batch_admit(state:&mut State,key:i64,
    batch:*const super::super::maps::registry::ReservationBatch,index:i32,
    layer:*const super::super::layers::Layer,identity:u64,out:&mut[i64],
    after_transfer:impl FnOnce()->Result<(),Error>)->Result<(),Error>{
    if batch.is_null(){
        if index!=-1||!layer.is_null()||identity!=0{return Err(Error::MissingOwner);}
        state.remove_queued(key)?;return Ok(());
    }
    if index<0||identity==0||!state.updating.owns_batch(&*batch){return Err(Error::MissingOwner);}
    let reservation=(*batch).take(index as usize).ok_or(Error::MissingOwner)?;
    out[2]=1;
    let entry=state.updating.entry(reservation,if layer.is_null(){None}else{Some(retain(layer))},identity).ok_or(Error::MissingOwner)?;
    after_transfer()?;
    state.put_queued(key,Some(entry))?;state.inconsistent=true;Ok(())
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_coordinator_queue_batch(owner:*const Owner,key:i64,
    batch:*const super::super::maps::registry::ReservationBatch,index:i32,
    layer:*const super::super::layers::Layer,identity:u64,output:*mut i64)->i32{
    if output.is_null(){return -27;}let out=std::slice::from_raw_parts_mut(output,3);out.fill(0);
    if owner.is_null(){return -27;}let owner=&*owner;
    catch_unwind(AssertUnwindSafe(||{
        let mut state=owner.state.lock().unwrap();
        let result=queue_batch_admit(&mut state,key,batch,index,layer,identity,out,||Ok(()));
        publish(owner, &mut state);match result{Ok(())=>0,Err(error)=>failure(error,out)}
    })).unwrap_or(-99)
}

/// Re-admit the same actual object entry without preparing a second binding
/// slot. Missing/capability-changed identities return 1 before any mutation.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_coordinator_queue_existing(owner:*const Owner,key:i64,
    layer:*const Layer,identity:u64,output:*mut i64)->i32 {
    if output.is_null(){return -27;}
    let out=std::slice::from_raw_parts_mut(output,3);out.fill(0);
    let Some(owner)=owner.as_ref() else{return -27;};
    catch_unwind(AssertUnwindSafe(||{
        let mut state=owner.state.lock().unwrap();
        let Some(entry)=state.updating.existing_entry(identity,layer) else{return 1;};
        let result=state.put_queued(key,Some(entry));
        if result.is_ok(){state.inconsistent=true;}
        publish(owner, &mut state);
        match result{Ok(_)=>0,Err(error)=>failure(error,out)}
    })).unwrap_or(-99)
}

pub(in crate::world::level::lighting) struct PropagationInput {
    owner: Arc<Owner>,
    engine: u64,
    pub(super) projection:Arc<EngineProjection>,
    pub(super) levels:Arc<LevelFollowers>,
}
pub(in crate::world::level::lighting) enum LayerInput {
    Fallback,
    Missing,
    Ready(bool),
}
#[cfg(test)]
pub(in crate::world::level::lighting) fn connected_test_input(map: &Arc<LightMap>) -> PropagationInput {
    unsafe {
        let owner = mattmc_light_coordinator_create(Arc::as_ptr(map), 0);
        let work = Arc::new(Queues::default());
        let binding = mattmc_light_coordinator_bind(owner, Arc::as_ptr(&work), 0);
        let input = PropagationInput::retain(binding.cast()).unwrap();
        mattmc_light_coordinator_bind_release(binding);
        mattmc_light_coordinator_release(owner);
        input
    }
}
impl Drop for PropagationInput {
    fn drop(&mut self) { self.owner.state.lock().unwrap().release_engine(self.engine); }
}
impl PropagationInput {
    pub(super) fn binding_pending(&self)->bool{self.owner.state.lock().unwrap().updating.binding_pending()}
    pub(super) fn begin_level(&self,kind:i32,key:i64,value:bool)->Result<u64,i32>{
        let mut state=self.owner.state.lock().unwrap();
        let op=match kind{0=>Op::Status(key,value),1=>Op::Enable(key,value),2=>Op::Sources(key),3=>Op::Run,_=>return Err(-28)};
        state.begin_retained(op,self.engine).map_err(|error|failure(error,&mut[0;2]))
    }
    pub(super) fn bind_source(&self,id:u64,source:&super::super::source_owner::Source)->i32 {
        self.owner.state.lock().unwrap().bind_source(id,source).map_or(-27,|()|0)
    }
    pub(super) unsafe fn bind_block(&self,id:u64,section:*const crate::world::level::chunk::live::Owner,index:i32)->i32 {
        self.owner.state.lock().unwrap().bind_block(id,section,index as usize)
    }
    pub(super) unsafe fn level_step(&self,id:u64,scalar:i64,entry:*const Entry,output:&mut[i64],snapshot:&mut Option<Arc<LightMap>>)->i32{
        let mut state=self.owner.state.lock().unwrap();
        let result=state.step(id,scalar,if entry.is_null(){None}else{Some(retain(entry))});
        output[3]=state.frames.get(id.wrapping_sub(1)as usize).and_then(Option::as_ref).map_or(0,|frame|i64::from(frame.vm_block_state));
        let mut ids=self.owner.refreshed_ids.lock().unwrap();ids.clear();ids.extend(state.refreshed.iter().map(|entry|entry.id));
        output[4]=ids.as_ptr() as i64;output[5]=ids.len() as i64;publish(&self.owner, &mut state);
        match result{
            Ok(Effect::Done(value))=>{output[0]=value;0},
            Ok(Effect::Repeat(slot))=>{output[0]=i64::from(slot);1},
            Ok(Effect::Copy(slot))=>{output[0]=i64::from(slot);2},
            Ok(Effect::Read(slot,index))=>{output[0]=i64::from(slot);output[1]=i64::from(index);3},
            Ok(Effect::Write(slot,index,value))=>{output[0]=i64::from(slot);output[1]=i64::from(index);output[2]=i64::from(value);4},
            Ok(Effect::Publish(map))=>{output[0]=Arc::as_ptr(&map) as i64;*snapshot=Some(map);5},
            Ok(Effect::Notify(key))=>{output[0]=key;6},
            Ok(Effect::BlockState(key))=>{output[0]=key;7},
            Ok(Effect::SkySource(x,z,limit))=>{output[0]=i64::from(x);output[1]=i64::from(z);output[2]=i64::from(limit);8},
            Ok(Effect::Propagate)=>9,
            Ok(Effect::ClearChunkCache)=>10,
            Ok(Effect::BlockEmission)=>11,
            Ok(Effect::BlockEmpty)=>12,
            Ok(Effect::CustomNode(key))=>{output[0]=key;14},
            Ok(Effect::HighestSource(x,z))=>{output[0]=i64::from(x);output[1]=i64::from(z);17},
            Ok(Effect::LayerEmpty(slot))=>{output[0]=i64::from(slot);18},
            Ok(Effect::LayerFill(slot,value))=>{output[0]=i64::from(slot);output[1]=i64::from(value);19},
            Ok(Effect::BlockSources(x,z))=>{output[0]=i64::from(x);output[1]=i64::from(z);20},
            Ok(Effect::SkySources(x,z))=>{output[0]=i64::from(x);output[1]=i64::from(z);21},
            Ok(_)=>-28,
            Err(error)=>failure(error,output),
        }
    }
    pub(super) fn end_status(&self,id:u64){let _=self.owner.state.lock().unwrap().end(id);}
    pub(super) fn retain_data(&self,key:i64,value:bool)->i32{
        let mut state=self.owner.state.lock().unwrap();
        let result=if value{state.tables[RETAINED].put(key,1)}else{state.tables[RETAINED].remove(key)};
        publish(&self.owner, &mut state);
        match result{Ok(_)=>0,Err(Fault::Allocation)=>-20,Err(_)=>-27}
    }
    pub(super) unsafe fn import(&self,key:i64,entry:*const Entry)->i32{
        let mut state=self.owner.state.lock().unwrap();
        if !entry.is_null()&&!state.updating.owns_entry(&*entry){return -27;}
        let result=if entry.is_null(){state.remove_queued(key).map(|_|())}else{
            state.put_queued(key,Some(retain(entry))).map(|_|{state.inconsistent=true;})
        };
        publish(&self.owner, &mut state);
        match result{Ok(())=>0,Err(Error::Table(Fault::Allocation))=>-20,Err(_)=>-27}
    }
    pub(in crate::world::level::lighting) fn enqueue_node(&self, pos: i64) -> i32 {
        let mut state = self.owner.state.lock().unwrap();
        let result = (|| -> Result<(), Error> {
            state.table_mut(self.engine, NODES)?.put(pos, 1)?;
            Ok(())
        })();
        publish(&self.owner, &mut state);
        match result { Ok(()) => 0, Err(Error::Table(Fault::Allocation)) => -20, Err(_) => -27 }
    }
    pub(in crate::world::level::lighting) unsafe fn retain(binding: *const std::ffi::c_void) -> Option<Self> {
        let binding = binding.cast::<Binding>().as_ref()?;
        let owner = Arc::clone(&binding.owner);
        owner.state.lock().unwrap().retain_engine(binding.id).ok()?;
        Some(Self { owner, engine: binding.id, projection:Arc::clone(&binding.projection),levels:Arc::clone(&binding.levels) })
    }
    pub(in crate::world::level::lighting) fn light_enabled(&self,key:i64)->bool {
        self.owner.state.lock().unwrap().enabled(key)
    }
    pub(in crate::world::level::lighting) fn matches_block_queues(&self,queues:*const Queues)->bool {
        let state=self.owner.state.lock().unwrap();
        state.engines.get(&self.engine).is_some_and(|engine|engine.kind==0&&Arc::as_ptr(&engine.queues)==queues)
    }
    pub(in crate::world::level::lighting) fn stored_section(&self,key:i64)->Option<bool> {
        let state=self.owner.state.lock().unwrap();
        if state.updating.binding_pending(){return None;}
        Some(state.updating.get(key).is_some())
    }
    pub(in crate::world::level::lighting) fn stored_read(&self,pos:i64)->Result<Option<i32>,(i32,[i64;2])> {
        let state=self.owner.state.lock().unwrap();
        if state.updating.binding_pending(){return Ok(None);}
        let Some(entry)=state.updating.get(section(pos))else{return Ok(None);};
        let Some(layer)=entry.layer.as_ref().filter(|layer|layer.is_valid())else{return Ok(None);};
        layer.view().get(index(pos)).map(Some).map_err(|error|{
            let mut args=[0;2];let status=failure(Error::Table(Fault::ArrayIndex(error.0,2048)),&mut args);(status,args)
        })
    }
    /// Reuse the actual coordinator COW/write/affected policy. Admission has no
    /// mutation; unsupported layers still perform their original VM effects.
    pub(in crate::world::level::lighting) fn stored_write(&self,pos:i64,value:i32)->Result<Option<(Vec<u32>,bool)>,(i32,[i64;2])> {
        let mut state=self.owner.state.lock().unwrap();
        if state.updating.binding_pending(){return Ok(None);}
        if !state.updating.raw(section(pos)).as_ref().and_then(|entry|entry.layer.as_ref()).is_some_and(|layer|layer.is_valid()){return Ok(None);}
        let result=(||->Result<(Vec<u32>,bool),Error>{
            let frame=state.begin_retained(Op::WriteStored(pos,value),self.engine)?;
            let stepped=state.step(frame,0,None);
            let ids=state.refreshed.iter().map(|entry|entry.id).collect();
            let ended=state.end(frame);
            if !matches!(stepped?,Effect::Done(_)){return Err(Error::Unsupported);}
            ended?;
            Ok((ids,state.updating.binding_pending()))
        })();
        publish(&self.owner,&mut state);
        result.map(Some).map_err(|error|{let mut args=[0;2];let status=failure(error,&mut args);(status,args)})
    }
    pub(in crate::world::level::lighting) fn layer(&self, key: i64, output: &mut [u8; 2048]) -> Result<LayerInput, propagation::Error> {
        let state = self.owner.state.lock().unwrap();
        // Same actual cache lookup before enabled/default decisions. The extra
        // identical lookup on a fallback does not alter its final cache order.
        let Some(entry) = state.updating.get(key) else { return Ok(LayerInput::Missing); };
        let Some(layer) = entry.layer.as_ref().filter(|layer| layer.is_valid()) else { return Ok(LayerInput::Fallback); };
        let light_on = state.enabled(key);
        let view = layer.view();
        if !view.copy_bytes_into(output) {
            let default = view.default_value();
            if !(0..16).contains(&default) { return Err(propagation::Error::Unsupported); }
            output.fill((default * 17) as u8);
        }
        Ok(LayerInput::Ready(light_on))
    }
}


/// Prepare save custody before Java commits the corresponding visible facade.
/// No borrow, map guard, or native lock survives this call.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_coordinator_prepare_save_publication(owner: *const Owner,
    previous: *const LightMap, next: *const LightMap, output: *mut i64) -> i32 {
    if output.is_null() { return -27; }
    let out = std::slice::from_raw_parts_mut(output, 2); out.fill(0);
    let (Some(owner), Some(previous), Some(next)) = (owner.as_ref(), previous.as_ref(), next.as_ref()) else { return -27; };
    catch_unwind(AssertUnwindSafe(|| {
        let mut state = owner.state.lock().unwrap();
        let result = state.prepare_save_publication(previous, next);
        publish(owner, &mut state);
        match result { Ok(()) => 0, Err(error) => failure(error, out) }
    })).unwrap_or(-99)
}
/// A scoped actual Entry Arc protects identity/materialization after the state
/// guard ends, including reentry or concurrent column retirement in Java.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_coordinator_saved_acquire(owner: *const Owner, key: i64, output: *mut i64) -> i32 {
    if output.is_null() { return -27; }
    let out = std::slice::from_raw_parts_mut(output, 2); out.fill(0);
    let Some(owner) = owner.as_ref() else { return -27; };
    catch_unwind(AssertUnwindSafe(|| {
        let entry = owner.state.lock().unwrap().saved_layer(key);
        if let Some(entry) = entry {
            let id = entry.id;
            let token = Arc::into_raw(entry);
            out[0] = token as i64; out[1] = i64::from(id);
        }
        0
    })).unwrap_or(-99)
}
/// Primitive release remains usable on VM allocation/error paths.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_coordinator_saved_release(token: i64) {
    if token != 0 { drop(Arc::from_raw(token as *const Entry)); }
}

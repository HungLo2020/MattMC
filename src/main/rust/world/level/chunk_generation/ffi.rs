//! Each handle is one Arc reference. Java fences its arena through downcalls and
//! direct header reads; callbacks never execute under a native lock.
use std::sync::Arc;
use std::sync::atomic::Ordering;
use super::{Header, Holder, Pending, Status, Task, TaskEffects, TaskPolicy};
use super::PublicationEffects;

pub(crate) unsafe fn holder<'a>(id: u64) -> &'a Holder {
    &*std::ptr::with_exposed_provenance::<Holder>(id as usize)
}

/// Caller keeps the Java incarnation arena alive through this clone. The
/// registry retains only native control, never a Java object/global root.
pub(crate) unsafe fn retain_holder(id: u64) -> Arc<Holder> {
    let pointer = std::ptr::with_exposed_provenance::<Holder>(id as usize);
    Arc::increment_strong_count(pointer);
    Arc::from_raw(pointer)
}

#[no_mangle]
pub extern "C" fn mattmc_chunk_generation_holder_create(statuses: i32) -> u64 {
    if !(1..=1024).contains(&statuses) { return 0; }
    Holder::new(statuses as usize).map_or(0, |owner| Arc::into_raw(Arc::new(owner)).expose_provenance() as u64)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_holder_release(id: u64) {
    if id != 0 { drop(Arc::from_raw(std::ptr::with_exposed_provenance::<Holder>(id as usize))); }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_holder_header(id: u64) -> *const Header {
    &holder(id).header
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_holder_fields(id: u64) -> *const u64 {
    holder(id).fields.as_ptr().cast::<u64>()
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_holder_status_reserve(id: u64, slot: i32, identity: i32) -> u64 {
    let owner = holder(id);
    let cell = match slot { 1 => &owner.header.started, _ => return 0 };
    cell.reserve(identity as u32)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_holder_status_change(id: u64, slot: i32, expected: u64, word: u64, compare: i32) -> i32 {
    let owner = holder(id);
    let cell = match slot { 1 => &owner.header.started, _ => return -14 };
    if compare == 0 { cell.set(word); 1 } else { i32::from(cell.compare_exchange(expected, word)) }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_holder_future_reserve(id: u64, field: i32, present: i32) -> u64 {
    if !(0..=1).contains(&present) { return 0; }
    holder(id).reserve_future(field, present != 0)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_holder_allowed_reserve(id: u64, identity: i32) -> u64 {
    holder(id).reserve_allowed(identity as u32)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_holder_future_publish(id: u64, field: i32, expected: u64, word: u64) -> i32 {
    holder(id).publish_future(field, expected, word)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_holder_change(id: u64, op: i32, a: i32, b: i32) -> i64 {
    holder(id).change(op, a, b)
}

/// Payload: generation radii then loading radii, each target+1 entries, then
/// dependencies by radius. Java supplies immutable pyramid projections.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_task_create(target: i32, x: i32, z: i32, data: *const i32, length: i32,
    holders: *const u64, holder_count: i32) -> u64 {
    if !(-1..1024).contains(&target) || data.is_null() || length < 2 { return 0; }
    let data = std::slice::from_raw_parts(data, length as usize);
    let status_count = data[0];
    if !(1..=1024).contains(&status_count) { return 0; }
    if target == -1 {
        if data.len() != 2 { return 0; }
        let policy = TaskPolicy { generation_radii: Vec::new(), loading_radii: Vec::new(), loading_dependencies: Vec::new(),
            status_count, foreign_radius: Some(data[1]) };
        return Task::new(target, x, z, policy).map_or(0, |task| bind_task(task, holders, holder_count));
    }
    let count = target as usize + 1;
    if data.len() < 1 + 2 * count || target >= status_count { return 0; }
    let mut generation_radii = Vec::new();
    let mut loading_radii = Vec::new();
    let mut loading_dependencies = Vec::new();
    if generation_radii.try_reserve_exact(count).is_err() || loading_radii.try_reserve_exact(count).is_err()
        || loading_dependencies.try_reserve_exact(data.len() - 1 - 2 * count).is_err() { return 0; }
    generation_radii.extend_from_slice(&data[1..1 + count]);
    loading_radii.extend_from_slice(&data[1 + count..1 + 2 * count]);
    loading_dependencies.extend_from_slice(&data[1 + 2 * count..]);
    let policy = TaskPolicy { generation_radii, loading_radii, loading_dependencies, status_count, foreign_radius: None };
    Task::new(target, x, z, policy).map_or(0, |task| bind_task(task, holders, holder_count))
}

unsafe fn bind_task(mut task: Task, holders: *const u64, count: i32) -> u64 {
    if count < 0 { return 0; }
    if count != 0 {
        let Some(side) = task.cache_radius.checked_mul(2).and_then(|n| n.checked_add(1)) else { return 0; };
        if side <= 0 || side.checked_mul(side) != Some(count) || holders.is_null() { return 0; }
        let mut owners = Vec::new();
        if owners.try_reserve_exact(count as usize).is_err() { return 0; }
        for &id in std::slice::from_raw_parts(holders, count as usize) {
            if id == 0 { return 0; }
            owners.push(retain_holder(id));
        }
        task.release_holders = Some(owners);
    }
    Arc::into_raw(Arc::new(task)).expose_provenance() as u64
}

unsafe fn task<'a>(id: u64) -> &'a Task { &*std::ptr::with_exposed_provenance::<Task>(id as usize) }

#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_task_release(id: u64) {
    if id != 0 { drop(Arc::from_raw(std::ptr::with_exposed_provenance::<Task>(id as usize))); }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_task_cancel(id: u64) { task(id).cancel(); }

pub(crate) type TaskCallback = unsafe extern "C" fn(i32, i32, i32, i32, u64) -> i64;
struct JavaTaskEffects(TaskCallback);
impl TaskEffects for JavaTaskEffects {
    async fn call(&mut self, op: i32, a: i32, b: i32, c: i32, token: u64) -> Result<i64, ()> {
        let value = unsafe { (self.0)(op, a, b, c, token) };
        if value == i64::MIN { Err(()) } else { Ok(value) }
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_task_run(id: u64, callback: TaskCallback) -> i32 {
    match task(id).run(&mut JavaTaskEffects(callback)) {
        Ok(waiting) => i32::from(waiting),
        Err(()) => -1,
    }
}

/// Creating a driver retains the actual task allocation before any Java
/// effect. Its private Box is explicitly released by that invocation's finally.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_task_driver_create(id: u64) -> u64 {
    let pointer = std::ptr::with_exposed_provenance::<Task>(id as usize);
    Arc::increment_strong_count(pointer);
    let owner = Arc::from_raw(pointer);
    Box::into_raw(Box::new(super::TaskDriver::new(owner))).expose_provenance() as u64
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_task_driver_next(id: u64, value: i64) -> i32 {
    (&mut *std::ptr::with_exposed_provenance_mut::<super::TaskDriver>(id as usize)).next(value)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_task_driver_release(id: u64) {
    if id != 0 { drop(Box::from_raw(std::ptr::with_exposed_provenance_mut::<super::TaskDriver>(id as usize))); }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_task_cache_release_count(id: u64) -> u64 {
    task(id).cache_release_count.load(Ordering::Relaxed)
}

/// Diagnostic owner/route counts, not an estimate of total native memory.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_statistics(output: *mut u64) {
    for (index, counter) in [&super::LIVE_HOLDERS, &super::LIVE_TASKS, &super::CREATED_HOLDERS,
        &super::CREATED_TASKS, &super::TASK_RUNS, &super::TASK_LAYERS,
        &super::LIVE_STATUSES, &super::CREATED_STATUSES, &super::FULL_PUBLICATIONS,
        &super::STAGE_COMPLETIONS, &super::LIVE_PENDING, &super::CREATED_PENDING].into_iter().enumerate() {
        output.add(index).write(counter.load(Ordering::Relaxed));
    }
}

#[no_mangle]
pub extern "C" fn mattmc_chunk_generation_status_create() -> u64 {
    Arc::into_raw(Arc::new(Status::new())).expose_provenance() as u64
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_status_release(id: u64) {
    if id != 0 { drop(Arc::from_raw(std::ptr::with_exposed_provenance::<Status>(id as usize))); }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_status_set(id: u64, status: u64) {
    let status_owner = &*std::ptr::with_exposed_provenance::<Status>(id as usize);
    status_owner.cell.set(status);
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_status_reserve(id: u64, identity: i32) -> u64 {
    let status_owner = &*std::ptr::with_exposed_provenance::<Status>(id as usize);
    status_owner.cell.reserve(identity as u32)
}

type PublicationCallback = unsafe extern "C" fn(i32) -> i32;
struct JavaPublicationEffects(PublicationCallback);
impl PublicationEffects for JavaPublicationEffects {
    fn call(&mut self, op: i32) -> Result<i32, ()> {
        let result = unsafe { (self.0)(op) };
        if result == i32::MIN { Err(()) } else { Ok(result) }
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_publish(kind: i32, callback: PublicationCallback) -> i32 {
    let mut effects = JavaPublicationEffects(callback);
    let result = match kind {
        0 => super::publish_full(&mut effects),
        1 => super::complete_stage(&mut effects),
        _ => return -14,
    };
    if result.is_ok() { 0 } else { -1 }
}

#[no_mangle]
pub extern "C" fn mattmc_chunk_generation_pending_create() -> u64 {
    Pending::new().map_or(0, |owner| Arc::into_raw(Arc::new(owner)).expose_provenance() as u64)
}

unsafe fn pending<'a>(id: u64) -> &'a Pending { &*std::ptr::with_exposed_provenance::<Pending>(id as usize) }

#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_pending_release(id: u64) {
    if id != 0 { drop(Arc::from_raw(std::ptr::with_exposed_provenance::<Pending>(id as usize))); }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_pending_change(id: u64, op: i32, row: i32) -> i64 {
    pending(id).change(op, row as u32)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_pending_walk(id: u64, callback: unsafe extern "C" fn(u32) -> i32) -> i32 {
    pending(id).walk(|row| if callback(row) == 0 { Ok(()) } else { Err(()) })
}


#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_pending_walk_create(id: u64) -> u64 {
    let pointer = std::ptr::with_exposed_provenance::<Pending>(id as usize);
    Arc::increment_strong_count(pointer);
    super::PendingWalk::new(Arc::from_raw(pointer))
        .map_or(0, |walk| Box::into_raw(Box::new(walk)).expose_provenance() as u64)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_pending_walk_next(id: u64) -> i64 {
    (&mut *std::ptr::with_exposed_provenance_mut::<super::PendingWalk>(id as usize)).next()
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_pending_walk_release(id: u64) {
    if id != 0 { drop(Box::from_raw(std::ptr::with_exposed_provenance_mut::<super::PendingWalk>(id as usize))); }
}

/// Initialize one holder's lifetime state, independently of a recyclable registry id.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_holder_levels_init(id: u64, level: i32, unloaded: i32, full: i32, block: i32, entity: i32) -> *const super::HolderLevels {
    let state = &holder(id).levels;
    state.level.store(level, Ordering::SeqCst);
    state.old.store(unloaded, Ordering::SeqCst);
    state.queue.store(unloaded, Ordering::SeqCst);
    for (slot, value) in state.status_levels.iter().zip([full, block, entity]) { slot.store(value, Ordering::SeqCst); }
    state
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_holder_level_change(id: u64, field: i32, value: i32) {
    let state = &holder(id).levels;
    match field { 0 => state.level.store(value, Ordering::SeqCst), 1 => state.queue.store(value, Ordering::SeqCst), 2 => state.refresh(), _ => () }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_holder_lifecycle(id: u64, operation: i32) -> u64 {
    Box::into_raw(Box::new(super::request::lifecycle(retain_holder(id), operation))).expose_provenance() as u64
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_request_graph_create(tickets: u64, holders: u64) -> u64 {
    let graph = super::request::RequestGraph {
        tickets: crate::world::level::chunk_distance::ticket_storage::ffi::retain_storage(tickets),
        holders: crate::world::level::chunk_holders::ffi::retain_registry(holders),
        requests: std::sync::Mutex::new(super::request::RequestPool::default()),
        unknown: std::sync::OnceLock::new(),
        cache: std::sync::Mutex::new(super::request::RequestCache::default()),
    };
    Arc::into_raw(Arc::new(graph)).expose_provenance() as u64
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_request_graph_release(id: u64) {
    if id != 0 { drop(Arc::from_raw(std::ptr::with_exposed_provenance::<super::request::RequestGraph>(id as usize))); }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_request_create(graph: u64, required: i32, create: i32, operation: i32) -> u64 {
    let graph = if graph == 0 { None } else {
        let pointer = std::ptr::with_exposed_provenance::<super::request::RequestGraph>(graph as usize);
        Arc::increment_strong_count(pointer); Some(Arc::from_raw(pointer))
    };
    Box::into_raw(Box::new(super::request::request(graph, required, create != 0, operation))).expose_provenance() as u64
}

unsafe fn request_graph(id: u64) -> Option<Arc<super::request::RequestGraph>> {
    if id == 0 { return None; }
    let pointer = std::ptr::with_exposed_provenance::<super::request::RequestGraph>(id as usize);
    Arc::increment_strong_count(pointer); Some(Arc::from_raw(pointer))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_request_begin(graph: u64, level: i32, create: i32, operation: i32) -> u64 {
    super::request::direct_begin(request_graph(graph), level, create != 0, operation, false)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_request_begin_fast(graph: u64, level: i32, create: i32, operation: i32) -> u64 {
    super::request::direct_begin(request_graph(graph), level, create != 0, operation, true)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_request_next(id: u64, response: i64, auxiliary: i32) -> u64 {
    super::request::direct_next(id, response, auxiliary, false)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_request_next_fast(id: u64, response: i64, auxiliary: i32) -> u64 {
    super::request::direct_next(id, response, auxiliary, true)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_request_abort(id: u64) { super::request::direct_abort(id); }

/// Bind immutable JVM type identity/flags to this actual native owner once.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_request_bind_unknown(id: u64, type_id: i32, flags: i32, timeout: i64) {
    let graph = &*std::ptr::with_exposed_provenance::<super::request::RequestGraph>(id as usize);
    let _ = graph.unknown.set(super::request::UnknownTicket { type_id, flags, timeout });
}
/// Consume the same existing ticket producer/notification algorithm directly.
/// No Java effect/dirty observation is moved into or across this call.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_request_add_unknown(id: u64, key: i64, level: i32) -> u64 {
    let graph = &*std::ptr::with_exposed_provenance::<super::request::RequestGraph>(id as usize);
    let Some(spec) = graph.unknown.get() else { return 14; };
    crate::world::level::chunk_distance::ticket_storage::ffi::mattmc_ticket_storage_add_adopt(
        Arc::as_ptr(&graph.tickets).expose_provenance() as u64, key, spec.type_id, level, spec.flags, spec.timeout, -1, spec.timeout)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_holder_dependency_command(id: u64, operation: i32, done: i32) -> u64 {
    let owner = holder(id);
    let offset = match operation { 1 => 4, 2 => 5, _ => return 0 };
    ((owner.statuses as u64 + 2 + offset) << 8) | if done != 0 { 31 } else { 32 }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_caller_begin(graph: u64, operation: i32, main: i32, key: u64, create: i32) -> u64 {
    let Some(graph) = request_graph(graph) else { return 0; };
    super::request::caller_begin(graph, operation, main != 0, key, create != 0)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_generation_request_cache_change(graph: u64, key: u64, clear: i32) -> i32 {
    let Some(graph) = request_graph(graph) else { return -1; };
    let mut cache = graph.cache.lock().unwrap_or_else(|p| p.into_inner());
    if clear != 0 { *cache = super::request::RequestCache::default(); -1 } else { cache.insert(key) as i32 }
}

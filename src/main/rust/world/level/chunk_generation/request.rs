//! Whole holder lifecycle control. Actual Java futures and observable effects are
//! opaque leaves; every callback returns before control rereads the live owner.
use super::*;

pub(super) fn lifecycle(owner: Arc<Holder>, operation: i32) -> TaskDriver {
    let exchange = Arc::new(DriverExchange::new());
    let mut effects = ContinuationEffects { exchange: exchange.clone() };
    let future = Box::pin(async move {
        let base = owner.statuses as i32 + 2;
        if operation == 1 || operation == 2 {
            let slot = base + if operation == 1 { 4 } else { 5 };
            let done = effects.call(30, slot, 0, 0, 0).await?;
            effects.call(if done != 0 { 31 } else { 32 }, slot, 0, 0, 0).await?;
            return Ok(false);
        }
        if operation != 0 { return Err(()); }
        let transition = owner.levels.begin();
        for (index, prepare, unload) in [(0, 1, 2), (1, 4, 8), (2, 16, 32)] {
            let slot = base + index;
            if transition & prepare != 0 {
                if index == 2 && effects.call(41, slot, 0, 0, 0).await? == 0 {
                    effects.call(42, 0, 0, 0, 0).await?;
                }
                effects.call(33, slot, index, 0, 0).await?;
                effects.call(34, slot, 0, 0, 0).await?;
                effects.call(36, base + 3, 0, 0, 0).await?;
                effects.call(37, base + 3, index + 1, 0, 0).await?;
                effects.call(38, slot, 0, 0, 0).await?;
                effects.call(35, slot, 0, 0, 0).await?;
            }
            if transition & unload != 0 {
                effects.call(39, slot, 0, 0, 0).await?;
                effects.call(40, slot, 0, 0, 0).await?;
            }
        }
        let demotion = ((transition >> 8) & 255) as i32;
        if demotion != 0 {
            effects.call(36, base + 3, 0, 0, 0).await?;
            effects.call(43, demotion - 1, 0, 0, 0).await?;
        }
        effects.call(44, 0, 0, 0, 0).await?;
        owner.levels.commit();
        Ok(false)
    });
    TaskDriver::from_future(future, exchange)
}


/// One connected native owner graph; the existing native ticket algorithms and
/// registry keep their actual allocations throughout request and generation work.
pub(super) struct RequestGraph {
    pub(super) tickets: Arc<std::sync::Mutex<super::super::chunk_distance::ticket_storage::TicketStorage>>,
    pub(super) holders: Arc<std::sync::Mutex<super::super::chunk_holders::Registry>>,
    pub(super) requests: std::sync::Mutex<RequestPool>,
    pub(super) unknown: std::sync::OnceLock<UnknownTicket>,
    pub(super) cache: std::sync::Mutex<RequestCache>,
}

/// Keys and insertion order belong to the native caller. The four physical VM
/// slots contain only actual status/chunk roots; match/presence bits are opaque
/// reference observations, not another key/order or cache-membership mirror.
pub(super) struct RequestCache { keys: [u64; 4], order: [usize; 4] }
impl Default for RequestCache {
    fn default() -> Self { Self { keys: [1875066u64 | (1875066u64 << 32); 4], order: [0,1,2,3] } }
}
impl RequestCache {
    pub(super) fn insert(&mut self, key: u64) -> usize {
        let slot = self.order[3];
        self.order.copy_within(0..3, 1);
        self.order[0] = slot; self.keys[slot] = key; slot
    }
    pub(super) fn find(&self, key: u64, observations: u32, create: bool, now: bool) -> Option<usize> {
        self.order.iter().copied().find(|&slot| self.keys[slot] == key && observations & (1 << slot) != 0
            && (now || !create || observations & (1 << (slot + 4)) != 0))
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(super) struct UnknownTicket { pub(super) type_id: i32, pub(super) flags: i32, pub(super) timeout: i64 }

enum RequestHolder { Absent, Java, Native(Arc<Holder>) }
impl RequestHolder {
    unsafe fn bind(value: i64) -> Self {
        match value { 0 => Self::Absent, 1 => Self::Java, _ => Self::Native(super::ffi::retain_holder(value as u64)) }
    }
    async fn absent(&self, required: i32, effects: &mut impl TaskEffects) -> Result<bool, ()> {
        Ok(match self {
            Self::Absent => true,
            Self::Java => effects.call(52, 0, 0, 0, 0).await? > i64::from(required),
            Self::Native(owner) => owner.levels.level.load(Ordering::SeqCst) > required,
        })
    }
}

pub(super) fn request(graph: Option<Arc<RequestGraph>>, required: i32, create: bool, operation: i32) -> TaskDriver {
    let exchange = Arc::new(DriverExchange::new());
    let mut effects = ContinuationEffects { exchange: exchange.clone() };
    let future = Box::pin(async move {
        let _graph = graph;
        let mut holder = unsafe { RequestHolder::bind(effects.call(50, 0, 0, 0, 0).await?) };
        if operation == 1 {
            let required = effects.call(62, 0, 0, 0, 0).await? as i32;
            let absent = holder.absent(required, &mut effects).await?;
            effects.call(67, i32::from(!absent), 0, 0, 0).await?;
            return Ok(false);
        }
        if operation == 2 {
            let kind = if matches!(holder, RequestHolder::Absent) { 0 } else { effects.call(63, 0, 0, 0, 0).await? };
            if kind != 0 { effects.call(64, 0, 0, 0, 0).await?; }
            effects.call(if kind == 2 { 66 } else { 65 }, 0, 0, 0, 0).await?;
            return Ok(false);
        }
        if create {
            effects.call(51, 0, 0, 0, 0).await?;
            if holder.absent(required, &mut effects).await? {
                effects.call(53, 0, 0, 0, 0).await?;
                effects.call(54, 0, 0, 0, 0).await?;
                holder = unsafe { RequestHolder::bind(effects.call(55, 0, 0, 0, 0).await?) };
                effects.call(56, 0, 0, 0, 0).await?;
                if holder.absent(required, &mut effects).await? { effects.call(57, 0, 0, 0, 0).await?; }
            }
        }
        let absent = holder.absent(required, &mut effects).await?;
        effects.call(if absent { 59 } else { 58 }, 0, 0, 0, 0).await?;
        Ok(false)
    });
    TaskDriver::from_future(future, exchange)
}


/// Reusable invocation storage belongs to the actual request owner. A free frame
/// contains no graph/holder Arc, so the pool cannot keep its owner alive in a cycle.
#[derive(Default)]
pub(super) struct RequestPool { free: Vec<Box<DirectRequest>>, allocated: usize }
#[derive(Clone, Copy)]
enum Phase { Bind, FirstQuery, RetriedBind, RetriedQuery, FinalQuery, HasQuery, NowKind, CallerProfile, CallerBindings, CallerResult, UpdateDistance, UpdatePromote, UpdateTasks }
#[repr(C, align(128))]
pub(super) struct DirectRequest {
    graph: Option<Arc<RequestGraph>>,
    holder: RequestHolder,
    required: i32,
    create: bool,
    operation: i32,
    phase: Phase,
    key: u64,
    updated: bool,
}
pub(super) const TERMINAL: u64 = 1 << 63;
pub(super) const RELEASE_LATER: u64 = 1 << 62;
enum Command { Effect(u64), Finish(u64) }
impl DirectRequest {
    fn empty() -> Self { Self { graph: None, holder: RequestHolder::Absent, required: 0, create: false, operation: 0, phase: Phase::Bind, key: 0, updated: false } }
    fn absence(&self) -> Option<bool> {
        match &self.holder {
            RequestHolder::Absent => Some(true), RequestHolder::Java => None,
            RequestHolder::Native(owner) => Some(owner.levels.level.load(Ordering::SeqCst) > self.required),
        }
    }
    fn final_check(&mut self) -> Command {
        match self.absence() {
            Some(absent) => Command::Finish(if absent { 59 } else { 58 }),
            None => { self.phase = Phase::FinalQuery; Command::Effect(52) }
        }
    }
    fn first_answer(&mut self, absent: bool) -> Command {
        if absent { self.phase = Phase::RetriedBind; Command::Effect(71) }
        else { self.final_check() }
    }
    fn retry_answer(&mut self, absent: bool) -> Command {
        if absent { Command::Finish(57) } else { self.final_check() }
    }
    fn has_answer(absent: bool) -> Command { Command::Finish(67 | u64::from(!absent) << 8) }
    /// Only native state/metadata is touched here. Commands compose engine effects
    /// only where the original has no intervening native/control observation.
    unsafe fn advance(&mut self, response: i64, auxiliary: i32) -> Command {
        match self.phase {
            Phase::Bind => {
                self.holder = RequestHolder::bind(response);
                if self.operation == 1 {
                    self.required = auxiliary;
                    return match self.absence() { Some(absent) => Self::has_answer(absent), None => { self.phase = Phase::HasQuery; Command::Effect(52) } };
                }
                if self.operation == 2 {
                    return if matches!(self.holder, RequestHolder::Absent) { Command::Finish(65) }
                        else { self.phase = Phase::NowKind; Command::Effect(63) };
                }
                if !self.create { return self.final_check(); }
                match self.absence() { Some(absent) => self.first_answer(absent), None => { self.phase = Phase::FirstQuery; Command::Effect(52) } }
            }
            Phase::FirstQuery => self.first_answer(response > i64::from(self.required)),
            Phase::RetriedBind => {
                self.holder = RequestHolder::bind(response);
                match self.absence() { Some(absent) => self.retry_answer(absent), None => { self.phase = Phase::RetriedQuery; Command::Effect(52) } }
            }
            Phase::RetriedQuery => self.retry_answer(response > i64::from(self.required)),
            Phase::FinalQuery => Command::Finish(if response > i64::from(self.required) { 59 } else { 58 }),
            Phase::HasQuery => Self::has_answer(response > i64::from(self.required)),
            Phase::NowKind => Command::Finish(match response { 0 => 65, 2 => 69, _ => 70 }),
            Phase::CallerProfile => { self.phase = Phase::CallerBindings; Command::Effect(85) }
            Phase::CallerBindings => {
                let cache = self.graph.as_ref().unwrap().cache.lock().unwrap_or_else(|p| p.into_inner());
                if let Some(slot) = cache.find(self.key, response as u32, self.create, self.operation == 4) {
                    let op = if self.operation == 4 { if response & (1 << (slot + 8)) != 0 { 96 } else { 81 } } else { 86 };
                    Command::Finish(op | (slot as u64) << 8)
                } else {
                    self.phase = Phase::CallerResult;
                    Command::Effect(if self.operation == 4 { 90 } else { 87 })
                }
            }
            Phase::CallerResult => {
                if self.operation == 4 { return Command::Finish(98); }
                if response == 0 && self.create { return Command::Finish(88); }
                let slot = self.graph.as_ref().unwrap().cache.lock().unwrap_or_else(|p| p.into_inner()).insert(self.key);
                Command::Finish(89 | (slot as u64) << 8)
            }
            Phase::UpdateDistance => { self.updated = response != 0; self.phase = Phase::UpdatePromote; Command::Effect(92) }
            Phase::UpdatePromote => { self.updated |= response != 0; self.phase = Phase::UpdateTasks; Command::Effect(93) }
            Phase::UpdateTasks => Command::Finish(if self.updated { 94 } else { 95 }),
        }
    }
}

pub(super) fn direct_begin(graph: Option<Arc<RequestGraph>>, required: i32, create: bool, operation: i32, fast: bool) -> u64 {
    let mut frame = if let Some(owner) = &graph {
        let mut pool = if fast { match owner.requests.try_lock() { Ok(pool) => pool, Err(_) => return 0 } }
            else { owner.requests.lock().unwrap_or_else(|p| p.into_inner()) };
        if let Some(frame) = pool.free.pop() { frame }
        else {
            if fast { return 0; }
            let needed = pool.allocated + 1 - pool.free.len();
            if pool.free.try_reserve(needed).is_err() { return 0; }
            pool.allocated += 1;
            Box::new(DirectRequest::empty())
        }
    } else {
        if fast { return 0; }
        Box::new(DirectRequest::empty())
    };
    frame.graph = graph; frame.required = required; frame.create = create; frame.operation = operation; frame.phase = Phase::Bind;
    debug_assert!(matches!(frame.holder, RequestHolder::Absent));
    let first = if operation == 1 { 72 } else if create { 68 } else { 50 };
    Box::into_raw(frame).expose_provenance() as u64 | first
}

/// Noncritical retirement may release an old incarnation after lookup replacement.
/// The hot fast path is used only while Java still pins its current graph/holder.
fn retire(mut frame: Box<DirectRequest>, fast: bool) -> Result<(), Box<DirectRequest>> {
    if let Some(graph) = frame.graph.take() {
        let pool = if fast { match graph.requests.try_lock() { Ok(pool) => pool, Err(_) => { frame.graph = Some(graph.clone()); return Err(frame); } } }
            else { graph.requests.lock().unwrap_or_else(|p| p.into_inner()) };
        let mut pool = pool;
        frame.holder = RequestHolder::Absent;
        debug_assert!(pool.free.len() < pool.allocated && pool.free.capacity() >= pool.allocated);
        pool.free.push(frame);
        drop(pool);
        Ok(())
    } else { drop(frame); Ok(()) }
}

pub(super) unsafe fn direct_next(id: u64, response: i64, auxiliary: i32, fast: bool) -> u64 {
    let mut frame = Box::from_raw(std::ptr::with_exposed_provenance_mut::<DirectRequest>(id as usize));
    match frame.advance(response, auxiliary) {
        Command::Effect(command) => { let _ = Box::into_raw(frame); command }
        Command::Finish(command) => match retire(frame, fast) {
            Ok(()) => TERMINAL | command,
            Err(frame) => { let _ = Box::into_raw(frame); TERMINAL | RELEASE_LATER | command }
        }
    }
}
pub(super) unsafe fn direct_abort(id: u64) {
    if id != 0 {
        let frame = Box::from_raw(std::ptr::with_exposed_provenance_mut::<DirectRequest>(id as usize));
        let _ = retire(frame, false);
    }
}

/// Whole public caller entry: thread dispatch, cache consumption/publication,
/// failure decision and update invalidation share the actual request owner.
/// Every VM callback runs after releasing both frame-pool and cache locks.
pub(super) unsafe fn caller_begin(graph: Arc<RequestGraph>, operation: i32, main: bool, key: u64, create: bool) -> u64 {
    if !main && operation != 6 { return TERMINAL | match operation { 3 => 80, 4 => 81, 5 => 82, _ => 95 }; }
    if operation == 5 { return TERMINAL | 97; }
    let mut issued = direct_begin(Some(graph.clone()), 0, create, operation, true);
    if issued == 0 { issued = direct_begin(Some(graph), 0, create, operation, false); }
    if issued == 0 { return 0; }
    let id = issued & !127;
    let frame = &mut *std::ptr::with_exposed_provenance_mut::<DirectRequest>(id as usize);
    frame.key = key;
    frame.phase = if operation == 6 { Phase::UpdateDistance } else { Phase::CallerProfile };
    id | match operation { 3 => 83, 4 => 84, 6 => 91, _ => unreachable!() }
}

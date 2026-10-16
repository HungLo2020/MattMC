//! Retained generation control. Java owns opaque object reachability and performs
//! future/executor/engine effects; it does not keep a second control-state copy.
//!
//! The owner is an incarnation, not a chunk coordinate or a reusable registry id.
//! The Java auto arena holds an Arc until all calls/escaped header reads finish.
//! A callback is always outside the owner lock. In particular, completing a Java
//! future is not an atomic operation on this state: its inline callbacks observe
//! the exact prefix already committed here.

pub(crate) mod ffi;
mod request;
mod readiness;

use std::sync::{atomic::{AtomicBool, AtomicI32, AtomicI64, AtomicU64, Ordering}, Arc, Mutex};

static LIVE_HOLDERS: AtomicU64 = AtomicU64::new(0);
static LIVE_TASKS: AtomicU64 = AtomicU64::new(0);
static CREATED_HOLDERS: AtomicU64 = AtomicU64::new(0);
static CREATED_TASKS: AtomicU64 = AtomicU64::new(0);
static TASK_RUNS: AtomicU64 = AtomicU64::new(0);
static TASK_LAYERS: AtomicU64 = AtomicU64::new(0);
static LIVE_STATUSES: AtomicU64 = AtomicU64::new(0);
static CREATED_STATUSES: AtomicU64 = AtomicU64::new(0);
static FULL_PUBLICATIONS: AtomicU64 = AtomicU64::new(0);
static STAGE_COMPLETIONS: AtomicU64 = AtomicU64::new(0);
static LIVE_PENDING: AtomicU64 = AtomicU64::new(0);
static CREATED_PENDING: AtomicU64 = AtomicU64::new(0);

/// Shared read-only projection for Java's volatile reads. Only Rust writes it.
#[repr(C)]
pub(crate) struct Header {
    pub(crate) claims: AtomicI32,
    pub(crate) started: StatusCell,
}

/// Low16 is null/canonical/foreign identity; high48 is the native publication
/// generation. Every assignment gets a fresh tag, including canonical/null
/// writes: the matching Java opaque leaf provides the original heap publication
/// ordering. No Java object pointer or copied completion truth is stored here.
#[repr(C)]
pub(crate) struct StatusCell {
    value: AtomicU64,
    next_epoch: AtomicU64,
}

impl StatusCell {
    fn new(value: u64) -> Self {
        Self { value: AtomicU64::new(value), next_epoch: AtomicU64::new(1) }
    }
    fn reserve(&self, identity: u32) -> u64 {
        if identity > u16::MAX as u32 { return 0; }
        match self.next_epoch.fetch_update(Ordering::SeqCst, Ordering::SeqCst,
            |epoch| if epoch <= u64::MAX >> 16 { Some(epoch + 1) } else { None }) {
            Ok(epoch) => epoch << 16 | u64::from(identity),
            Err(_) => 0,
        }
    }
    fn set(&self, value: u64) { self.value.store(value, Ordering::SeqCst); }
    fn compare_exchange(&self, expected: u64, value: u64) -> bool {
        self.value.compare_exchange(expected, value, Ordering::SeqCst, Ordering::SeqCst).is_ok()
    }
}

#[derive(Default)]
struct State {
    allocated: Vec<bool>,
    free: Vec<u32>,
}

impl State {
    fn allocate(&mut self) -> Option<u32> {
        if let Some(row) = self.free.pop() {
            self.allocated[row as usize - 1] = true;
            return Some(row);
        }
        let row = u32::try_from(self.allocated.len().checked_add(1)?).ok()?;
        if row > (i32::MAX - 8) as u32 { return None; }
        // Retiring any live row must not allocate after a field is committed.
        self.free.try_reserve(self.allocated.len() + 1 - self.free.len()).ok()?;
        self.allocated.try_reserve(1).ok()?;
        self.allocated.push(true);
        Some(row)
    }

    fn retire(&mut self, row: u32) {
        if row != 0 {
            debug_assert!(self.allocated[row as usize - 1]);
            self.allocated[row as usize - 1] = false;
            self.free.push(row);
        }
    }
}

/// Stable incarnation state shared by registry, requests and retained Java holders.
/// No registry id or Java mirror is a lifetime/identity authority.
#[repr(C)]
pub(crate) struct HolderLevels {
    pub(crate) level: AtomicI32,
    pub(crate) old: AtomicI32,
    pub(crate) queue: AtomicI32,
    pub(crate) accessible: AtomicI32,
    status_levels: [AtomicI32; 3],
}
impl HolderLevels {
    fn new() -> Self { Self { level: AtomicI32::new(0), old: AtomicI32::new(0), queue: AtomicI32::new(0), accessible: AtomicI32::new(0), status_levels: std::array::from_fn(|_| AtomicI32::new(0)) } }
    pub(crate) fn snapshot(&self) -> super::chunk_holders::Levels {
        super::chunk_holders::Levels { level: self.level.load(Ordering::SeqCst), old_level: self.old.load(Ordering::SeqCst), accessible: self.accessible.load(Ordering::SeqCst) != 0 }
    }
    pub(crate) fn restore(&self, state: super::chunk_holders::Levels) {
        self.level.store(state.level, Ordering::SeqCst);
        self.old.store(state.old_level, Ordering::SeqCst);
        self.accessible.store(i32::from(state.accessible), Ordering::SeqCst);
    }
    fn thresholds(&self) -> [i32; 3] { std::array::from_fn(|i| self.status_levels[i].load(Ordering::SeqCst)) }
    fn begin(&self) -> i64 {
        let state = self.snapshot();
        if super::chunk_holders::full_status(self.thresholds(), state.level) >= 1 { self.accessible.store(1, Ordering::SeqCst); }
        super::chunk_holders::transition(self.thresholds(), state.old_level, state.level)
    }
    pub(crate) fn commit(&self) { self.old.store(self.level.load(Ordering::SeqCst), Ordering::SeqCst); }
    pub(crate) fn refresh(&self) { self.accessible.store(i32::from(super::chunk_holders::full_status(self.thresholds(), self.level.load(Ordering::SeqCst)) >= 1), Ordering::SeqCst); }
}

pub(crate) struct Holder {
    pub(crate) header: Header,
    pub(crate) levels: HolderLevels,
    statuses: usize,
    /// Fixed native membership: upper60 is a nonwrapping publication tag;
    /// bit0 is presence. Bits1..2 carry Rust's FULL admission command:
    /// 0=disallowed, 1=canonical allowed, 2=foreign virtual-query effect.
    /// Bit3 selects one of two fixed Java opaque binding locations. Rust
    /// reserves the opposite of the PUBLISHED location, never Java bookkeeping.
    /// The same tag selects BOTH opaque allowed status and actual future.
    /// Other slots have command0. No copied completion truth.
    fields: Box<[AtomicU64]>,
    reservations: Box<[AtomicU64]>,
    next_future_epoch: AtomicU64,
}

impl Holder {
    fn new(statuses: usize) -> Option<Self> {
        let count = statuses.checked_add(8)?;
        let mut fields = Vec::new();
        fields.try_reserve_exact(count).ok()?;
        fields.resize_with(count, || AtomicU64::new(0));
        let mut reservations = Vec::new();
        reservations.try_reserve_exact(count).ok()?;
        reservations.resize_with(count, || AtomicU64::new(0));
        LIVE_HOLDERS.fetch_add(1, Ordering::Relaxed);
        CREATED_HOLDERS.fetch_add(1, Ordering::Relaxed);
        Some(Self {
            header: Header { claims: AtomicI32::new(0), started: StatusCell::new(0) },
            levels: HolderLevels::new(),
            statuses,
            fields: fields.into_boxed_slice(),
            reservations: reservations.into_boxed_slice(),
            next_future_epoch: AtomicU64::new(1),
        })
    }

    fn full_field(&self) -> usize { self.statuses - 1 }

    fn reserve_binding(&self, field: i32, present: bool, admission: u8) -> u64 {
        let Some(reserved) = self.reservations.get(field as usize) else { return 0; };
        let phase = ((self.fields[field as usize].load(Ordering::SeqCst) >> 3) ^ 1) & 1;
        let word = self.next_future_epoch.fetch_update(Ordering::SeqCst, Ordering::SeqCst,
            |epoch| (epoch != 0 && epoch <= u64::MAX >> 4).then(|| epoch + 1))
            .map_or(0, |epoch| epoch << 4 | phase << 3 | u64::from(admission) << 1 | u64::from(present));
        if word != 0 { reserved.store(word, Ordering::SeqCst); }
        word
    }

    fn reserve_future(&self, field: i32, present: bool) -> u64 {
        let admission = if field as usize == self.full_field() {
            ((self.fields[self.full_field()].load(Ordering::SeqCst) >> 1) & 3) as u8
        } else { 0 };
        self.reserve_binding(field, present, admission)
    }

    fn reserve_allowed(&self, identity: u32) -> u64 {
        let statuses = self.statuses as u32;
        let admission = if identity == u16::MAX as u32 { 2 }
            else if identity <= statuses { u8::from(identity == statuses) }
            else { return 0; };
        let field = self.full_field();
        let present = self.fields[field].load(Ordering::SeqCst) & 1 != 0;
        self.reserve_binding(field as i32, present, admission)
    }

    fn publish_future(&self, field: i32, expected: u64, word: u64) -> i32 {
        let Some(slot) = self.fields.get(field as usize) else { return -14; };
        if word >> 4 == 0 { return -14; }
        if slot.load(Ordering::SeqCst) != expected { return 0; }
        if self.reservations[field as usize].compare_exchange(word, 0, Ordering::SeqCst, Ordering::SeqCst).is_err() { return -14; }
        i32::from(slot.compare_exchange(expected, word, Ordering::SeqCst, Ordering::SeqCst).is_ok())
    }

    /// Pure bounded atomic control: no allocation, lock, callback or Java root.
    fn change(&self, op: i32, a: i32, _b: i32) -> i64 {
        match op {
            0 => self.fields.get(a as usize).map_or(-14, |field| field.load(Ordering::SeqCst) as i64),
            6 => self.header.claims.fetch_add(1, Ordering::SeqCst) as i64,
            7 => self.header.claims.fetch_sub(1, Ordering::SeqCst).wrapping_sub(1) as i64,
            8 => self.fields[..self.statuses + 2].iter().filter(|field| field.load(Ordering::SeqCst) & 1 != 0).count() as i64,
            _ => -14,
        }
    }
}

impl Drop for Holder {
    fn drop(&mut self) { LIVE_HOLDERS.fetch_sub(1, Ordering::Relaxed); }
}

/// Immutable projections of the two Java pyramids for this target. They contain
/// no callbacks or mutable generation truth and are captured before a task runs.
struct TaskPolicy {
    generation_radii: Vec<i32>,
    loading_radii: Vec<i32>,
    loading_dependencies: Vec<i32>,
    status_count: i32,
    foreign_radius: Option<i32>,
}

struct TaskState {
    scheduled: i32,
    needs_generation: bool,
    pending: Vec<u32>,
    pins: State,
}

pub(crate) struct Task {
    target: i32,
    x: i32,
    z: i32,
    cache_radius: i32,
    policy: TaskPolicy,
    cancelled: AtomicBool,
    state: Mutex<TaskState>,
    // Same holder incarnations as the immutable Java cache, never registry ids.
    // Exact stock dispatch permits native claim mutation between engine effects.
    release_holders: Option<Vec<Arc<Holder>>>,
    cache_release_count: AtomicU64,
}

/// Engine/object effects, always invoked without a native lock or borrowed
/// native output. The immediate apply/getNow/isSuccess slice has no intervening
/// task-state observation, including after reentry. Pending `GET_NOW` and
/// `IS_SUCCESS` stay separate because pop must commit between them.
trait TaskEffects {
    async fn call(&mut self, op: i32, a: i32, b: i32, c: i32, token: u64) -> Result<i64, ()>;
}

impl Task {
    fn new(target: i32, x: i32, z: i32, policy: TaskPolicy) -> Option<Self> {
        let cache_radius = policy.foreign_radius.or_else(|| policy.generation_radii.first().copied())?;
        let pins = State::default();
        LIVE_TASKS.fetch_add(1, Ordering::Relaxed);
        CREATED_TASKS.fetch_add(1, Ordering::Relaxed);
        Some(Self {
            target, x, z, cache_radius, policy,
            cancelled: AtomicBool::new(false),
            state: Mutex::new(TaskState { scheduled: -1, needs_generation: false, pending: Vec::new(), pins }),
            release_holders: None,
            cache_release_count: AtomicU64::new(0),
        })
    }

    fn lock(&self) -> std::sync::MutexGuard<'_, TaskState> {
        self.state.lock().unwrap_or_else(|poison| poison.into_inner())
    }

    fn cancel(&self) { self.cancelled.store(true, Ordering::SeqCst); }

    async fn pending(&self, effects: &mut impl TaskEffects) -> Result<bool, ()> {
        loop {
            let token = self.lock().pending.last().copied();
            let Some(token) = token else { return Ok(false); };
            if effects.call(2, 0, 0, 0, token as u64).await? == 0 {
                effects.call(6, 0, 0, 0, token as u64).await?;
                return Ok(true);
            }
            // Java's removeLast acts on the live list, including mutations made
            // by a reentrant getNow. Do not replace it with pop-if-token-matches.
            let removed = self.lock().pending.pop();
            let Some(removed) = removed else { effects.call(13, 0, 0, 0, 0).await?; return Err(()); };
            effects.call(5, 0, 0, 0, removed as u64).await?;
            self.lock().pins.retire(removed);
            if effects.call(3, 0, 0, 0, 0).await? == 0 { self.cancel(); }
        }
    }

    async fn can_load(&self, effects: &mut impl TaskEffects) -> Result<bool, ()> {
        if self.target == 0 { return Ok(true); }
        if effects.call(17, self.x, self.z, 0, 0).await? == 0 { return Ok(false); }
        let dynamic = self.policy.foreign_radius.is_some();
        let radius = if dynamic { effects.call(18, 0, 0, 0, 0).await? as i32 }
            else { self.policy.loading_dependencies.len().saturating_sub(1) as i32 };
        for x in self.x - radius..=self.x + radius {
            for z in self.z - radius..=self.z + radius {
                let distance = (x - self.x).abs().max((z - self.z).abs()) as usize;
                let required = if dynamic { distance as i32 } else { self.policy.loading_dependencies[distance] };
                if effects.call(19, x, z, required, u64::from(dynamic)).await? == 0 { return Ok(false); }
            }
        }
        Ok(true)
    }

    async fn retain_pending(&self, effects: &mut impl TaskEffects) -> Result<(), ()> {
        let row = {
            let mut state = self.lock();
            if state.pending.try_reserve(1).is_err() { None } else { state.pins.allocate() }
        };
        let Some(row) = row else { effects.call(12, 0, 0, 0, 0).await?; return Err(()); };
        if effects.call(4, 0, 0, 0, row as u64).await.is_err() {
            self.lock().pins.retire(row);
            return Err(());
        }
        self.lock().pending.push(row);
        Ok(())
    }

    async fn schedule_layer(&self, effects: &mut impl TaskEffects, status: i32, generation: bool) -> Result<(), ()> {
        TASK_LAYERS.fetch_add(1, Ordering::Relaxed);
        // The resource may have been acquired before addText throws. CLOSE is
        // also called on OPEN failure; the adapter knows whether a Zone exists
        // and implements Java's primary/suppressed exception precedence.
        let result = async {
            effects.call(7, status, 0, 0, 0).await?;
            let radii = if generation { &self.policy.generation_radii } else { &self.policy.loading_radii };
            let radius = match radii.get(status as usize).filter(|_| self.policy.foreign_radius.is_none()) {
                Some(&radius) => radius,
                None => effects.call(14, status, i32::from(generation), 0, 0).await? as i32,
            };
            for x in self.x - radius..=self.x + radius {
                for z in self.z - radius..=self.z + radius {
                    // A foreign target can change the queried radius. Original
                    // cache lookup happens before cancellation, including faults.
                    if self.policy.foreign_radius.is_some() { effects.call(20, x, z, 0, 0).await?; }
                    if self.cancelled.load(Ordering::SeqCst) { return Ok(()); }
                    let generate = effects.call(16, status, x, z, u64::from(self.policy.foreign_radius.is_some())).await? != 0;
                    if generate && !generation { effects.call(11, 0, 0, 0, 0).await?; return Err(()); }
                    let immediate = effects.call(1, status, x, z, u64::from(generate)).await?;
                    if immediate == 0 {
                        self.retain_pending(effects).await?;
                    } else if immediate == 2 {
                        self.cancel();
                        return Ok(());
                    }
                }
            }
            Ok(())
        }.await;
        let close = effects.call(8, 0, 0, 0, 0).await;
        result.and(close.map(|_| ()))
    }

    async fn schedule_next(&self, effects: &mut impl TaskEffects) -> Result<(), ()> {
        let (scheduled, generation) = { let state = self.lock(); (state.scheduled, state.needs_generation) };
        let status = if scheduled == -1 {
            0
        } else if !generation && scheduled == 0 && !self.can_load(effects).await? {
            self.lock().needs_generation = true;
            0
        } else {
            self.lock().scheduled + 1
        };
        if status >= self.policy.status_count { effects.call(15, status, 0, 0, 0).await?; }
        let generation = self.lock().needs_generation;
        self.schedule_layer(effects, status, generation).await?;
        self.lock().scheduled = status;
        Ok(())
    }

    async fn release_async(&self, effects: &mut impl TaskEffects) -> Result<(), ()> {
        if let Some(holders) = &self.release_holders {
            self.cache_release_count.fetch_add(1, Ordering::Relaxed);
            // Remove the actual task, then capture the first actual fence BEFORE
            // decrementing its claim. After each completion, capture the next
            // live fence: reentry can replace that later holder's save epoch.
            effects.call(21, self.x - self.cache_radius, self.z - self.cache_radius, 0, 0).await?;
            let side = 2 * self.cache_radius + 1;
            for (index, holder) in holders.iter().enumerate() {
                let count = holder.header.claims.fetch_sub(1, Ordering::SeqCst).wrapping_sub(1);
                let next = index + 1 < holders.len();
                if count <= 0 || next {
                    let index = index as i32 + 1;
                    effects.call(22, count, self.x - self.cache_radius + index / side,
                        self.z - self.cache_radius + index % side, u64::from(next)).await?;
                }
            }
            return Ok(());
        }
        effects.call(9, self.x, self.z, 0, 0).await?;
        for x in self.x - self.cache_radius..=self.x + self.cache_radius {
            for z in self.z - self.cache_radius..=self.z + self.cache_radius {
                effects.call(10, x, z, 0, 0).await?;
            }
        }
        // No "released" bit: an exception keeps its exact prefix and a retry
        // starts again, as the original Java cache.forEach does.
        Ok(())
    }

    async fn run_async(&self, effects: &mut impl TaskEffects) -> Result<bool, ()> {
        TASK_RUNS.fetch_add(1, Ordering::Relaxed);
        loop {
            if self.pending(effects).await? { return Ok(true); }
            // Scheduled layers are canonical objects. A foreign target never
            // equals one merely because getIndex returns the same number.
            let finished = self.cancelled.load(Ordering::SeqCst) || self.target >= 0 && self.lock().scheduled == self.target;
            if finished {
                self.release_async(effects).await?;
                return Ok(false);
            }
            self.schedule_next(effects).await?;
        }
    }
    // The synchronous callback adapter and models exercise the SAME async
    // control. Their effects are immediately ready, so one poll completes it.
    fn run(&self, effects: &mut impl TaskEffects) -> Result<bool, ()> {
        complete_ready(self.run_async(effects))
    }
    #[cfg(test)]
    fn release(&self, effects: &mut impl TaskEffects) -> Result<(), ()> {
        complete_ready(self.release_async(effects))
    }
}

/// One invocation owns its suspended Rust locals and shares only the actual
/// task Arc. No scheduler runtime/thread is involved: Java supplies an effect
/// result then directly polls until the next command or terminal outcome.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
struct TaskCommand { op: i32, a: i32, b: i32, c: i32, token: u64 }

/// One driver exclusively polls one future, and consumes its command only
/// AFTER poll returns Pending. Java never touches this exchange. Atomics keep
/// the owned frame Send across serialized carrier-thread changes, with no
/// mutex/unsafe shared cell and no concurrent poll of an invocation.
struct DriverExchange {
    command: [AtomicU64; 3],
    response: AtomicI64,
}
impl DriverExchange {
    fn new() -> Self { Self { command: std::array::from_fn(|_| AtomicU64::new(0)), response: AtomicI64::new(0) } }
    fn publish(&self, command: TaskCommand) {
        let pair = |low: i32, high: i32| u64::from(low as u32) | u64::from(high as u32) << 32;
        self.command[0].store(pair(command.op, command.a), Ordering::Relaxed);
        self.command[1].store(pair(command.b, command.c), Ordering::Relaxed);
        self.command[2].store(command.token, Ordering::Relaxed);
    }
}

struct ContinuationEffects { exchange: Arc<DriverExchange> }
impl TaskEffects for ContinuationEffects {
    async fn call(&mut self, op: i32, a: i32, b: i32, c: i32, token: u64) -> Result<i64, ()> {
        let mut sent = false;
        std::future::poll_fn(|_| {
            if !sent {
                self.exchange.publish(TaskCommand { op, a, b, c, token });
                sent = true;
                std::task::Poll::Pending
            } else {
                let value = self.exchange.response.load(Ordering::Relaxed);
                std::task::Poll::Ready(if value == i64::MIN { Err(()) } else { Ok(value) })
            }
        }).await
    }
}

#[repr(C)]
pub(crate) struct TaskDriver {
    // Java copies these three atomic words before invoking ANY engine effect.
    // Reentrant invocations own different drivers, never this mutable output.
    header: [AtomicU64; 3],
    future: std::pin::Pin<Box<dyn std::future::Future<Output = Result<bool, ()>> + Send>>,
    exchange: Arc<DriverExchange>,
    awaiting: bool,
    terminal: Option<i32>,
}

impl TaskDriver {
    fn new(task: Arc<Task>) -> Self {
        let exchange = Arc::new(DriverExchange::new());
        let effects = ContinuationEffects { exchange: exchange.clone() };
        let future = Box::pin(async move {
            let mut effects = effects;
            task.run_async(&mut effects).await
        });
        Self::from_future(future, exchange)
    }

    fn from_future(future: std::pin::Pin<Box<dyn std::future::Future<Output = Result<bool, ()>> + Send>>, exchange: Arc<DriverExchange>) -> Self {
        Self { header: std::array::from_fn(|_| AtomicU64::new(0)), future, exchange, awaiting: false, terminal: None }
    }

    /// 1=command, 0=finished, 2=waiting, -1=engine failure. No lock/borrow of
    /// mutable output survives this return. The caller serializes one driver.
    fn next(&mut self, value: i64) -> i32 {
        if let Some(status) = self.terminal { return status; }
        if self.awaiting {
            self.exchange.response.store(value, Ordering::Relaxed);
            self.awaiting = false;
        }
        let mut context = std::task::Context::from_waker(std::task::Waker::noop());
        match self.future.as_mut().poll(&mut context) {
            std::task::Poll::Ready(result) => {
                let status = match result { Ok(false) => 0, Ok(true) => 2, Err(()) => -1 };
                self.terminal = Some(status);
                status
            }
            std::task::Poll::Pending => {
                for (destination, source) in self.header.iter().zip(&self.exchange.command) {
                    destination.store(source.load(Ordering::Relaxed), Ordering::Release);
                }
                self.awaiting = true;
                1
            }
        }
    }

    #[cfg(test)]
    fn command(&self) -> TaskCommand {
        let first = self.header[0].load(Ordering::SeqCst);
        let second = self.header[1].load(Ordering::SeqCst);
        TaskCommand { op: first as i32, a: (first >> 32) as i32, b: second as i32,
            c: (second >> 32) as i32, token: self.header[2].load(Ordering::SeqCst) }
    }
}

fn complete_ready<F: std::future::Future>(future: F) -> F::Output {
    use std::future::Future;
    let mut future = std::pin::pin!(future);
    let mut context = std::task::Context::from_waker(std::task::Waker::noop());
    match future.as_mut().poll(&mut context) {
        std::task::Poll::Ready(value) => value,
        std::task::Poll::Pending => panic!("immediate generation effect unexpectedly suspended"),
    }
}

impl Drop for Task {
    fn drop(&mut self) { LIVE_TASKS.fetch_sub(1, Ordering::Relaxed); }
}

/// Stable chunk-incarnation control, independent of replaceable section/input
/// captures. Java reads this atomic through a read-only shared segment.
#[repr(C)]
pub(crate) struct Status {
    cell: StatusCell,
}

impl Status {
    fn new() -> Self {
        LIVE_STATUSES.fetch_add(1, Ordering::Relaxed);
        CREATED_STATUSES.fetch_add(1, Ordering::Relaxed);
        Self { cell: StatusCell::new(1) }
    }
}

impl Drop for Status {
    fn drop(&mut self) { LIVE_STATUSES.fetch_sub(1, Ordering::Relaxed); }
}

trait PublicationEffects {
    fn call(&mut self, op: i32) -> Result<i32, ()>;
}

/// Executes only after the original stage future/executor dependencies. Each
/// effect commits its real Java engine state before the next callback. Errors
/// retain that exact prefix; the calling CompletableFuture cannot succeed.
fn publish_full(effects: &mut impl PublicationEffects) -> Result<(), ()> {
    if effects.call(0)? != 0 {
        effects.call(1)?; // unwrap an already-full saved chunk
    } else {
        effects.call(2)?; // real LevelChunk constructor / retained post-load callback
        effects.call(3)?; // one shared imposter replacement future
    }
    for op in 4..=9 { effects.call(op)?; }
    FULL_PUBLICATIONS.fetch_add(1, Ordering::Relaxed);
    Ok(())
}

fn complete_stage(effects: &mut impl PublicationEffects) -> Result<(), ()> {
    if effects.call(10)? != 0 && effects.call(11)? != 0 { effects.call(12)?; }
    effects.call(13)?;
    STAGE_COMPLETIONS.fetch_add(1, Ordering::Relaxed);
    Ok(())
}

struct PendingState {
    items: Vec<u32>,
    version: i32,
    pins: State,
}

pub(crate) struct Pending {
    state: Mutex<PendingState>,
}

impl Pending {
    fn new() -> Option<Self> {
        let pins = State::default();
        LIVE_PENDING.fetch_add(1, Ordering::Relaxed);
        CREATED_PENDING.fetch_add(1, Ordering::Relaxed);
        Some(Self { state: Mutex::new(PendingState { items: Vec::new(), version: 0, pins }) })
    }

    fn change(&self, op: i32, row: u32) -> i64 {
        let mut state = self.state.lock().unwrap_or_else(|poison| poison.into_inner());
        match op {
            0 => {
                if state.items.try_reserve(1).is_err() { return -3; }
                state.pins.allocate().map_or(-3, i64::from)
            }
            1 => {
                if row == 0 || !state.pins.allocated.get(row as usize - 1).copied().unwrap_or(false) || state.items.contains(&row) {
                    return -14;
                }
                state.items.push(row);
                state.version = state.version.wrapping_add(1);
                0
            }
            2 => {
                if row == 0 || !state.pins.allocated.get(row as usize - 1).copied().unwrap_or(false) || state.items.contains(&row) {
                    return -14;
                }
                state.pins.retire(row);
                0
            }
            3 => {
                // ArrayList.clear increments modCount even for an empty list.
                state.version = state.version.wrapping_add(1);
                let PendingState { items, pins, .. } = &mut *state;
                for token in items.drain(..) { pins.retire(token); }
                0
            }
            4 => state.items.len() as i64,
            _ => -14,
        }
    }

    /// ArrayList.forEach semantics: capture the size/version, read live rows,
    /// check the version before every callback and once after the last. A
    /// callback throw wins over a concurrent-modification error and keeps all
    /// pending entries. No eager detached/drained batch.
    fn walk(&self, mut effect: impl FnMut(u32) -> Result<(), ()>) -> i32 {
        let (version, size) = {
            let state = self.state.lock().unwrap_or_else(|poison| poison.into_inner());
            (state.version, state.items.len())
        };
        for index in 0..size {
            let row = {
                let state = self.state.lock().unwrap_or_else(|poison| poison.into_inner());
                if state.version != version { return 2; }
                state.items[index]
            };
            if effect(row).is_err() { return -1; }
        }
        let current = self.state.lock().unwrap_or_else(|poison| poison.into_inner()).version;
        if current != version { 2 } else { 0 }
    }
}

/// One actual live pass owns its queue Arc, captured bound/version and cursor.
/// Each next checks version BEFORE testing the bound, including after the last
/// callback. Dropping after a callback throw performs no later version check.
pub(crate) struct PendingWalk {
    owner: Arc<Pending>,
    version: i32,
    size: usize,
    index: usize,
}
impl PendingWalk {
    fn new(owner: Arc<Pending>) -> Option<Self> {
        let (version, size) = {
            let state = owner.state.lock().unwrap_or_else(|p| p.into_inner());
            (state.version, state.items.len())
        };
        if size == 0 { None } else { Some(Self { owner, version, size, index: 0 }) }
    }
    fn next(&mut self) -> i64 {
        let state = self.owner.state.lock().unwrap_or_else(|p| p.into_inner());
        if state.version != self.version { return -2; }
        if self.index == self.size { return 0; }
        let row = state.items[self.index];
        self.index += 1;
        i64::from(row)
    }
}

impl Drop for Pending {
    fn drop(&mut self) { LIVE_PENDING.fetch_sub(1, Ordering::Relaxed); }
}

#[cfg(test)]
mod tests;

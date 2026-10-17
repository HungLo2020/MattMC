//! Per-holder native stage binding identities and callback/consumer leases.
//! Java roots are weak projections here; native memory never contains VM pointers.
use super::{Holder, TaskDriver, TaskEffects, DriverExchange, ContinuationEffects};
use std::collections::{BTreeMap, HashMap};
use std::sync::{Arc, Weak, Mutex, atomic::{AtomicU64, Ordering}};

static LIVE: AtomicU64 = AtomicU64::new(0);
static CREATED: AtomicU64 = AtomicU64::new(0);
static RETIRED: AtomicU64 = AtomicU64::new(0);
static CALLBACKS: AtomicU64 = AtomicU64::new(0);
static CALLBACK_CREATED: AtomicU64 = AtomicU64::new(0);
static CALLBACK_RETIRED: AtomicU64 = AtomicU64::new(0);
static COMPLETIONS: AtomicU64 = AtomicU64::new(0);
static CONSUMERS: AtomicU64 = AtomicU64::new(0);
static PENDING: AtomicU64 = AtomicU64::new(0);

#[derive(Default)]
pub(super) struct Bindings {
    next: u64,
    live: BTreeMap<u64, Weak<Binding>>,
    status: HashMap<i32, Weak<Binding>>,
    work: HashMap<u32, BTreeMap<u64, Weak<Binding>>>,
}

pub(super) struct Binding {
    pub(super) holder: Arc<Holder>,
    pub(super) key: u64,
    status: i32,
    hashes: Mutex<[Option<u32>; 2]>,
}
impl Binding {
    pub(super) fn create(holder: Arc<Holder>, status: i32) -> Option<Arc<Self>> {
        let key = {
            let mut roots = holder.stage_bindings.lock().unwrap_or_else(|p| p.into_inner());
            let next = roots.next.checked_add(1)?;
            roots.next = next;
            next
        };
        let binding = Arc::new(Self { holder, key, status, hashes: Mutex::new([None, None]) });
        binding.holder.stage_bindings.lock().unwrap_or_else(|p| p.into_inner()).live.insert(key, Arc::downgrade(&binding));
        LIVE.fetch_add(1, Ordering::Relaxed); CREATED.fetch_add(1, Ordering::Relaxed);
        Some(binding)
    }
    // Publish only after the actual VM weak projection is initialized.
    pub(super) fn publish(self: &Arc<Self>) {
        if self.status >= 0 {
            self.holder.stage_bindings.lock().unwrap_or_else(|p| p.into_inner()).status.insert(self.status, Arc::downgrade(self));
        }
    }
    pub(super) fn bind_future(self: &Arc<Self>, role: usize, hash: u32) {
        if role > 1 { return; }
        let mut hashes = self.hashes.lock().unwrap_or_else(|p| p.into_inner());
        let old = hashes[role].replace(hash);
        let mut roots = self.holder.stage_bindings.lock().unwrap_or_else(|p| p.into_inner());
        if let Some(old) = old.filter(|old| !hashes.contains(&Some(*old))) {
            let empty = if let Some(bucket) = roots.work.get_mut(&old) { bucket.remove(&self.key); bucket.is_empty() } else { false };
            if empty { roots.work.remove(&old); }
        }
        roots.work.entry(hash).or_default().insert(self.key, Arc::downgrade(self));
    }
    pub(super) fn claim(holder: &Holder, key: u64) -> Option<Arc<Self>> {
        holder.stage_bindings.lock().unwrap_or_else(|p| p.into_inner()).live.get(&key).and_then(Weak::upgrade)
    }
    pub(super) fn status_key(holder: &Holder, status: i32) -> u64 {
        let binding = { holder.stage_bindings.lock().unwrap_or_else(|p| p.into_inner()).status.get(&status).and_then(Weak::upgrade) };
        binding.map_or(0, |b| b.key)
    }
}
impl Drop for Binding {
    fn drop(&mut self) {
        let address = self as *const Self;
        let hashes = self.hashes.get_mut().unwrap_or_else(|p| p.into_inner());
        let mut roots = self.holder.stage_bindings.lock().unwrap_or_else(|p| p.into_inner());
        roots.live.remove(&self.key);
        if roots.status.get(&self.status).is_some_and(|b| std::ptr::eq(b.as_ptr(), address)) { roots.status.remove(&self.status); }
        for hash in hashes.iter().flatten() {
            let empty = if let Some(bucket) = roots.work.get_mut(hash) {
                bucket.remove(&self.key); bucket.is_empty()
            } else { false };
            if empty { roots.work.remove(hash); }
        }
        LIVE.fetch_sub(1, Ordering::Relaxed); RETIRED.fetch_add(1, Ordering::Relaxed);
    }
}

/// Actual callback custody is independent of task cancellation and observation.
pub(super) struct Callback { pub(super) binding: Arc<Binding> }
impl Callback {
    pub(super) fn create(binding: Arc<Binding>) -> Arc<Self> {
        CALLBACKS.fetch_add(1, Ordering::Relaxed); CALLBACK_CREATED.fetch_add(1, Ordering::Relaxed);
        Arc::new(Self { binding })
    }
}
impl Drop for Callback {
    fn drop(&mut self) { CALLBACKS.fetch_sub(1, Ordering::Relaxed); CALLBACK_RETIRED.fetch_add(1, Ordering::Relaxed); }
}

/// Every actual invocation owns independent completion state through the same
/// callback binding. No shared mutable completion scratch or terminal mirror.
pub(super) fn completion(callback: Arc<Callback>) -> TaskDriver {
    let exchange = Arc::new(DriverExchange::new());
    let mut effects = ContinuationEffects { exchange: exchange.clone() };
    let future = Box::pin(async move {
        COMPLETIONS.fetch_add(1, Ordering::Relaxed);
        super::stage::completed(&mut effects).await?;
        drop(callback);
        Ok(false)
    });
    TaskDriver::from_future(future, exchange)
}

/// Native membership selects candidates; the VM supplies only genuine identity
/// comparisons against its weak roots. The selected Arc pins each native row
/// across that VM effect without a holder-table lock crossing the boundary.
pub(super) fn lookup(holder: Arc<Holder>, hash: u32) -> Option<TaskDriver> {
    if !holder.stage_bindings.lock().unwrap_or_else(|p| p.into_inner()).work.contains_key(&hash) { return None; }
    let exchange = Arc::new(DriverExchange::new());
    let mut effects = ContinuationEffects { exchange: exchange.clone() };
    let future = Box::pin(async move {
        let mut after = 0;
        loop {
            let candidate = {
                let roots = holder.stage_bindings.lock().unwrap_or_else(|p| p.into_inner());
                roots.work.get(&hash).and_then(|bucket| bucket.range((std::ops::Bound::Excluded(after), std::ops::Bound::Unbounded))
                    .find_map(|(_, weak)| weak.upgrade()))
            };
            let Some(candidate) = candidate else { break; };
            after = candidate.key;
            if effects.call(1, 0, 0, 0, candidate.key).await? != 0 {
                effects.call(2, 0, 0, 0, candidate.key).await?;
                break;
            }
        }
        Ok(false)
    });
    Some(TaskDriver::from_future(future, exchange))
}

pub(super) fn consume(binding: &Binding) { let _ = binding; CONSUMERS.fetch_add(1, Ordering::Relaxed); }
pub(super) fn pending() { PENDING.fetch_add(1, Ordering::Relaxed); }
pub(super) fn statistics() -> [u64; 9] {
    [&LIVE,&CREATED,&RETIRED,&CALLBACKS,&CALLBACK_CREATED,&CALLBACK_RETIRED,&COMPLETIONS,&CONSUMERS,&PENDING]
        .map(|x| x.load(Ordering::Relaxed))
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn unadopted_and_stale_binding_retirement_cannot_clear_the_new_status_owner() {
        let holder = Arc::new(Holder::new(12).unwrap());
        let first = Binding::create(holder.clone(), 0).unwrap(); first.publish();
        let unadopted = Binding::create(holder.clone(), 0).unwrap();
        assert_eq!(Binding::status_key(&holder, 0), first.key);
        drop(unadopted);
        assert_eq!(Binding::status_key(&holder, 0), first.key);
        let next = Binding::create(holder.clone(), 0).unwrap(); next.publish();
        let key = next.key; drop(first);
        assert_eq!(Binding::status_key(&holder, 0), key);
        drop(next);
        assert_eq!(Binding::status_key(&holder, 0), 0);
        assert!(holder.stage_bindings.lock().unwrap().live.is_empty());
    }
    #[test]
    fn alias_roles_retire_old_hashes_without_losing_shared_identity_or_retaining_roots() {
        let holder = Arc::new(Holder::new(12).unwrap());
        let binding = Binding::create(holder.clone(), 0).unwrap(); binding.publish();
        binding.bind_future(0, 7); binding.bind_future(1, 7); binding.bind_future(1, 9);
        { let roots = holder.stage_bindings.lock().unwrap(); assert!(roots.work.contains_key(&7)); assert!(roots.work.contains_key(&9)); }
        binding.bind_future(0, 11);
        { let roots = holder.stage_bindings.lock().unwrap(); assert!(!roots.work.contains_key(&7)); assert_eq!(roots.work.len(), 2); }
        let weak = Arc::downgrade(&binding); drop(binding);
        assert!(weak.upgrade().is_none());
        let roots = holder.stage_bindings.lock().unwrap(); assert!(roots.live.is_empty()); assert!(roots.work.is_empty());
    }
    #[test]
    fn binding_identity_exhaustion_never_reuses_or_rebinds_an_existing_row() {
        let holder = Arc::new(Holder::new(12).unwrap());
        holder.stage_bindings.lock().unwrap().next = u64::MAX - 1;
        let last = Binding::create(holder.clone(), 0).unwrap(); last.publish();
        assert_eq!(last.key, u64::MAX);
        assert!(Binding::create(holder.clone(), 0).is_none());
        assert_eq!(Binding::status_key(&holder, 0), u64::MAX);
        drop(last);
        assert!(Binding::create(holder.clone(), 0).is_none());
        assert_eq!(Binding::status_key(&holder, 0), 0);
    }
    #[test]
    fn callback_and_native_consumer_own_independent_arcs_of_one_binding() {
        let holder = Arc::new(Holder::new(12).unwrap());
        let binding = Binding::create(holder.clone(), 0).unwrap(); binding.publish();
        let weak = Arc::downgrade(&binding);
        let callback = Callback::create(binding.clone());
        let consumer = binding.clone();
        drop(binding); drop(callback);
        assert!(weak.upgrade().is_some(), "callback retirement invalidated a native consumer");
        drop(consumer);
        assert!(weak.upgrade().is_none());
        assert!(holder.stage_bindings.lock().unwrap().live.is_empty());
    }
    #[test]
    fn claim_uses_live_native_identity_and_cannot_resurrect_retired_rows() {
        let holder = Arc::new(Holder::new(12).unwrap());
        let binding = Binding::create(holder.clone(), 0).unwrap();
        let key = binding.key;
        let consumer = Binding::claim(&holder, key).unwrap();
        assert!(Arc::ptr_eq(&binding, &consumer));
        drop(binding);
        assert!(Binding::claim(&holder, key).is_some());
        drop(consumer);
        assert!(Binding::claim(&holder, key).is_none());
        assert!(Binding::claim(&holder, key + 1).is_none());
    }
    #[test]
    fn native_invocation_retains_binding_through_terminal_result_until_driver_release() {
        let holder = Arc::new(Holder::new(12).unwrap());
        let binding = Binding::create(holder.clone(), 0).unwrap();
        let weak = Arc::downgrade(&binding);
        let mut driver = TaskDriver::from_future(Box::pin(std::future::ready(Ok(false))), Arc::new(DriverExchange::new()));
        driver.stage_binding = Some(binding);
        assert_eq!(driver.next(0), 0);
        assert!(weak.upgrade().is_some(), "terminal response prematurely retired the invocation lease");
        drop(driver);
        assert!(weak.upgrade().is_none());
        assert!(holder.stage_bindings.lock().unwrap().live.is_empty());
    }
}

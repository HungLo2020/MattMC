//! Shared biome temperature and key-only per-caller cache. Java retains only
//! genuine position/base-value observations and immutable synthesis bindings.
use std::collections::{HashMap, VecDeque};
use std::sync::{
    atomic::{AtomicU64, Ordering},
    Arc, Mutex,
};
mod ffi;
const CAPACITY: usize = 1024;
static LIVE: AtomicU64 = AtomicU64::new(0);
static CREATED: AtomicU64 = AtomicU64::new(0);
static RETIRED: AtomicU64 = AtomicU64::new(0);
static HITS: AtomicU64 = AtomicU64::new(0);
static MISSES: AtomicU64 = AtomicU64::new(0);
static COMMITS: AtomicU64 = AtomicU64::new(0);
static FRAMES: AtomicU64 = AtomicU64::new(0);
#[derive(Default)]
struct Store {
    values: HashMap<i64, f32>,
    order: VecDeque<i64>,
}
struct Cache {
    store: Mutex<Store>,
}
impl Cache {
    fn create() -> Option<Arc<Self>> {
        let mut store = Store::default();
        store.values.try_reserve(CAPACITY).ok()?;
        store.order.try_reserve(CAPACITY).ok()?;
        LIVE.fetch_add(1, Ordering::Relaxed);
        CREATED.fetch_add(1, Ordering::Relaxed);
        Some(Arc::new(Self {
            store: Mutex::new(store),
        }))
    }
    fn lookup(&self, key: i64, mode: i32) -> i32 {
        let value = self
            .store
            .lock()
            .unwrap_or_else(|p| p.into_inner())
            .values
            .get(&key)
            .copied();
        if let Some(value) = value.filter(|x| !x.is_nan()) {
            HITS.fetch_add(1, Ordering::Relaxed);
            1 + i32::from(predicate(value, mode))
        } else {
            MISSES.fetch_add(1, Ordering::Relaxed);
            0
        }
    }
    fn commit(&self, key: i64, value: f32) {
        let mut s = self.store.lock().unwrap_or_else(|p| p.into_inner());
        // Reread after all genuine callbacks. Even an existing NaN entry evicts
        // the live first entry when full; replacing an entry never refreshes it.
        if s.values.len() == CAPACITY {
            if let Some(first) = s.order.pop_front() {
                s.values.remove(&first);
            }
        }
        if !s.values.contains_key(&key) {
            s.order.push_back(key);
        }
        s.values.insert(key, value);
        COMMITS.fetch_add(1, Ordering::Relaxed);
    }
}
impl Drop for Cache {
    fn drop(&mut self) {
        LIVE.fetch_sub(1, Ordering::Relaxed);
        RETIRED.fetch_add(1, Ordering::Relaxed);
    }
}
fn predicate(value: f32, mode: i32) -> bool {
    if mode == 0 {
        value >= 0.15f32
    } else {
        value > 0.1f32
    }
}
// VM commands: 1 base value, 2 Y, 3 X, 4 Z, 5 synthesis, 6 null modifier throw.
#[repr(C)]
struct Frame {
    header: [u64; 4],
    cache: Option<Arc<Cache>>,
    key: i64,
    sea: i32,
    mode: i32,
    modifier: i32,
    phase: i32,
    base: f32,
    modified: f32,
    x: i32,
    z: i32,
    first_noise: f64,
    height_noise: f32,
    noise_reply: Option<u64>,
    terminal: Option<i32>,
}
impl Frame {
    fn new(
        cache: Option<Arc<Cache>>,
        key: i64,
        sea: i32,
        mode: i32,
        modifier: i32,
        base: f32,
    ) -> Self {
        FRAMES.fetch_add(1, Ordering::Relaxed);
        Self {
            header: [0; 4],
            cache,
            key,
            sea,
            mode,
            modifier,
            phase: if mode == 2 { 100 } else { 0 },
            base,
            modified: base,
            x: 0,
            z: 0,
            first_noise: 0.,
            height_noise: 0.,
            noise_reply: None,
            terminal: None,
        }
    }
    fn effect(&mut self, phase: i32, op: u64) -> i32 {
        self.phase = phase;
        self.header = [op, 0, 0, 0];
        1
    }
    fn noise(&mut self, phase: i32, kind: u64, x: f64, z: f64) -> i32 {
        self.phase = phase;
        self.header = [5, kind, x.to_bits(), z.to_bits()];
        1
    }
    fn finish(&mut self, value: f32) -> i32 {
        if let Some(cache) = &self.cache {
            cache.commit(self.key, value);
        }
        self.header = [
            if self.mode == 2 {
                u64::from(value.to_bits())
            } else {
                u64::from(predicate(value, self.mode))
            },
            0,
            0,
            0,
        ];
        self.terminal = Some(0);
        0
    }
    fn height(&mut self) -> i32 {
        if self.mode == 2 {
            self.finish(self.modified)
        } else {
            self.effect(20, 2)
        }
    }
    fn modifier(&mut self) -> i32 {
        match self.modifier {
            0 => {
                self.modified = self.base;
                self.height()
            }
            1 => self.effect(2, 3),
            _ => self.effect(99, 6),
        }
    }
    fn next(&mut self, response: u64, failed: bool) -> i32 {
        if let Some(done) = self.terminal {
            return done;
        }
        if failed {
            self.terminal = Some(-1);
            return -1;
        }
        let response = self.noise_reply.take().unwrap_or(response);
        match self.phase {
            0 => self.effect(1, 1),
            1 => {
                self.base = f32::from_bits(response as u32);
                self.modifier()
            }
            100 => self.modifier(),
            2 => {
                self.x = response as i32;
                self.effect(3, 4)
            }
            3 => {
                self.z = response as i32;
                self.noise(4, 0, self.x as f64 * 0.05, self.z as f64 * 0.05)
            }
            4 => {
                self.first_noise = f64::from_bits(response) * 7.0;
                self.effect(5, 3)
            }
            5 => {
                self.x = response as i32;
                self.effect(6, 4)
            }
            6 => {
                self.z = response as i32;
                self.noise(7, 1, self.x as f64 * 0.2, self.z as f64 * 0.2)
            }
            7 => {
                if self.first_noise + f64::from_bits(response) < 0.3 {
                    self.effect(8, 3)
                } else {
                    self.modified = self.base;
                    self.height()
                }
            }
            8 => {
                self.x = response as i32;
                self.effect(9, 4)
            }
            9 => {
                self.z = response as i32;
                self.noise(10, 1, self.x as f64 * 0.09, self.z as f64 * 0.09)
            }
            10 => {
                self.modified = if f64::from_bits(response) < 0.8 {
                    0.2f32
                } else {
                    self.base
                };
                self.height()
            }
            20 => {
                if response as i32 > self.sea.wrapping_add(17) {
                    self.effect(21, 3)
                } else {
                    self.finish(self.modified)
                }
            }
            21 => {
                self.x = response as i32;
                self.effect(22, 4)
            }
            22 => {
                self.z = response as i32;
                self.noise(
                    23,
                    2,
                    (self.x as f32 / 8.0f32) as f64,
                    (self.z as f32 / 8.0f32) as f64,
                )
            }
            23 => {
                self.height_noise = (f64::from_bits(response) * 8.0) as f32;
                self.effect(24, 2)
            }
            24 => {
                let adjusted = self.modified
                    - (self.height_noise + response as i32 as f32
                        - self.sea.wrapping_add(17) as f32)
                        * 0.05f32
                        / 40.0f32;
                self.finish(adjusted)
            }
            _ => {
                self.terminal = Some(-1);
                -1
            }
        }
    }
}
impl Drop for Frame {
    fn drop(&mut self) {
        FRAMES.fetch_sub(1, Ordering::Relaxed);
    }
}
fn statistics() -> [u64; 7] {
    [&LIVE, &CREATED, &RETIRED, &HITS, &MISSES, &COMMITS, &FRAMES]
        .map(|n| n.load(Ordering::Relaxed))
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn fifo_hits_do_not_refresh_and_nan_is_a_miss() {
        let c = Cache::create().unwrap();
        for i in 0..1024 {
            c.commit(i, 0.2);
        }
        assert_eq!(c.lookup(0, 0), 2);
        c.commit(1024, 0.2);
        assert_eq!(c.lookup(0, 0), 0);
        assert_eq!(c.lookup(1, 0), 2);
        c.commit(1025, f32::NAN);
        assert_eq!(c.lookup(1025, 0), 0);
    }
    #[test]
    fn nan_replacement_preserves_existing_order_and_live_capacity() {
        let c = Cache::create().unwrap();
        for i in 0..1024 {
            c.commit(i, f32::NAN);
        }
        c.commit(500, 0.2);
        let s = c.store.lock().unwrap();
        assert_eq!(s.values.len(), 1023);
        assert_eq!(s.order.front(), Some(&1));
        assert_eq!(s.order.back(), Some(&1023));
        assert!(!s.values.contains_key(&0));
    }
    #[test]
    fn callbacks_are_outside_cache_locks_and_commit_uses_the_live_store() {
        let c = Cache::create().unwrap();
        for i in 0..1023 {
            c.commit(i, 0.2);
        }
        let mut f = Frame::new(Some(c.clone()), 4000, 63, 0, 0, 0.);
        assert_eq!(f.next(0, false), 1);
        assert_eq!(f.header[0], 1);
        assert_eq!(f.next(u64::from(0.2f32.to_bits()), false), 1);
        assert_eq!(f.header[0], 2);
        c.commit(3000, 0.2);
        assert_eq!(f.next(0, false), 0);
        assert_eq!(c.lookup(0, 0), 0);
        assert_eq!(c.lookup(1, 0), 2);
    }
    #[test]
    fn failed_effect_never_commits_and_frame_retains_actual_cache() {
        let c = Cache::create().unwrap();
        let weak = Arc::downgrade(&c);
        let mut f = Frame::new(Some(c), 1, 63, 0, 0, 0.);
        assert_eq!(f.next(0, false), 1);
        assert_eq!(f.next(0, true), -1);
        assert!(weak
            .upgrade()
            .unwrap()
            .store
            .lock()
            .unwrap()
            .values
            .is_empty());
        drop(f);
        assert!(weak.upgrade().is_none());
    }
}

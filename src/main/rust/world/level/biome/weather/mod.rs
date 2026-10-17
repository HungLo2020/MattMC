//! Shared precipitation, freezing and snow decisions. Only genuine VM facts
//! cross the boundary; temperature/cache ownership stays in the same runtime.
use super::temperature::{Cache, Frame as Temperature};
use std::sync::{
    atomic::{AtomicU64, Ordering},
    Arc,
};
mod ffi;
static LIVE: AtomicU64 = AtomicU64::new(0);
static CREATED: AtomicU64 = AtomicU64::new(0);
static RETIRED: AtomicU64 = AtomicU64::new(0);
// Header-compatible genuine VM effects; 1..6 belong to the temperature child.
const SEA: u64 = 100;
const PRECIPITATES: u64 = 101;
const KEY: u64 = 102;
const CACHE: u64 = 103;
const Y: u64 = 104;
const INSIDE: u64 = 105;
const LIGHT: u64 = 106;
const BLOCK: u64 = 107;
const FLUID: u64 = 108;
const WATER_TYPE: u64 = 109;
const LIQUID_BLOCK: u64 = 110;
const AIR: u64 = 111;
const SNOW: u64 = 112;
const SURVIVES: u64 = 113;
const WATER_WEST: u64 = 120;

#[repr(C)]
struct Frame {
    header: [u64; 4],
    mode: i32, // 0 precipitation enum, 1 freeze boolean, 2 snow boolean
    sea: i32,
    edge: bool,
    phase: u64,
    key: i64,
    child: Option<Temperature>,
    warm_reply: Option<bool>,
    terminal: Option<i32>,
}
impl Frame {
    fn new(mode: i32, sea: i32, edge: bool) -> Self {
        LIVE.fetch_add(1, Ordering::Relaxed);
        CREATED.fetch_add(1, Ordering::Relaxed);
        Self {
            header: [0; 4],
            mode,
            sea,
            edge,
            phase: 0,
            key: 0,
            child: None,
            warm_reply: None,
            terminal: None,
        }
    }
    fn effect(&mut self, command: u64, argument: u64) -> i32 {
        self.phase = command;
        self.header = [command, argument, 0, 0];
        1
    }
    fn finish(&mut self, value: u64) -> i32 {
        self.header = [value, 0, 0, 0];
        self.terminal = Some(0);
        0
    }
    /// Called only at the original cache lookup point, after actual sea/key
    /// callbacks. A miss owns the actual cache Arc throughout the child query.
    fn bind(&mut self, cache: Arc<Cache>, modifier: i32) -> i32 {
        if self.phase != CACHE || self.child.is_some() || self.warm_reply.is_some() {
            return -1;
        }
        let hit = cache.lookup(self.key, 0);
        if hit == 0 {
            self.child = Some(Temperature::new(
                Some(cache),
                self.key,
                self.sea,
                0,
                modifier,
                0.,
            ));
        } else {
            self.warm_reply = Some(hit == 2);
        }
        0
    }
    fn after_warm(&mut self, warm: bool) -> i32 {
        if self.mode == 0 {
            // Biome.Precipitation NONE/RAIN/SNOW retain ordinals0/1/2.
            self.finish(if warm { 1 } else { 2 })
        } else if warm {
            self.finish(0)
        } else {
            self.effect(Y, 0)
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
        if let Some(child) = &mut self.child {
            let status = child.next(response, false);
            if status == 1 {
                self.header = child.header;
                return 1;
            }
            if status != 0 {
                self.terminal = Some(-1);
                return -1;
            }
            let warm = child.header[0] != 0;
            // Retire the completed temperature invocation before later world
            // effects, just as the original public temperature call returns.
            self.child = None;
            return self.after_warm(warm);
        }
        if let Some(warm) = self.warm_reply.take() {
            return self.after_warm(warm);
        }
        match self.phase {
            0 => {
                if self.mode == 0 {
                    self.effect(PRECIPITATES, 0)
                } else {
                    self.effect(SEA, 0)
                }
            }
            SEA => {
                self.sea = response as i32;
                if self.mode == 2 {
                    self.effect(PRECIPITATES, 0)
                } else {
                    self.effect(KEY, 0)
                }
            }
            PRECIPITATES => {
                if response == 0 {
                    self.finish(0)
                } else {
                    self.effect(KEY, 0)
                }
            }
            KEY => {
                self.key = response as i64;
                self.effect(CACHE, 0)
            }
            CACHE => {
                self.terminal = Some(-1);
                -1
            } // Missing actual binding.
            Y => self.effect(INSIDE, response),
            INSIDE => {
                if response == 0 {
                    self.finish(0)
                } else {
                    self.effect(LIGHT, 0)
                }
            }
            LIGHT => {
                if response as i32 >= 10 {
                    self.finish(0)
                } else {
                    self.effect(BLOCK, 0)
                }
            }
            BLOCK => {
                if self.mode == 1 {
                    self.effect(FLUID, 0)
                } else {
                    self.effect(AIR, 0)
                }
            }
            FLUID => self.effect(WATER_TYPE, 0),
            WATER_TYPE => {
                if response == 0 {
                    self.finish(0)
                } else {
                    self.effect(LIQUID_BLOCK, 0)
                }
            }
            LIQUID_BLOCK => {
                if response == 0 {
                    self.finish(0)
                } else if !self.edge {
                    self.finish(1)
                } else {
                    self.effect(WATER_WEST, 0)
                }
            }
            AIR => {
                if response != 0 {
                    self.effect(SURVIVES, 0)
                } else {
                    self.effect(SNOW, 0)
                }
            }
            SNOW => {
                if response == 0 {
                    self.finish(0)
                } else {
                    self.effect(SURVIVES, 0)
                }
            }
            SURVIVES => self.finish(u64::from(response != 0)),
            120..=123 => {
                if response == 0 {
                    self.finish(1)
                } else if self.phase == 123 {
                    self.finish(0)
                } else {
                    self.effect(self.phase + 1, 0)
                }
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
        LIVE.fetch_sub(1, Ordering::Relaxed);
        RETIRED.fetch_add(1, Ordering::Relaxed);
    }
}
fn statistics() -> [u64; 3] {
    [&LIVE, &CREATED, &RETIRED].map(|n| n.load(Ordering::Relaxed))
}

#[cfg(test)]
mod tests {
    use super::*;
    fn cold(f: &mut Frame, cache: Arc<Cache>) {
        assert_eq!(f.next(0, false), 1);
        assert_eq!(f.header[0], SEA);
        assert_eq!(f.next(63, false), 1);
        assert_eq!(f.header[0], KEY);
        assert_eq!(f.next(1, false), 1);
        assert_eq!(f.header[0], CACHE);
        assert_eq!(f.bind(cache, 0), 0);
        assert_eq!(f.next(0, false), 1);
        assert_eq!(f.header[0], 1);
        assert_eq!(f.next(0, false), 1);
        assert_eq!(f.header[0], 2);
        assert_eq!(f.next(70, false), 1);
        assert_eq!(f.header[0], Y);
    }
    #[test]
    fn child_owns_actual_cache_until_its_real_last_use() {
        let cache = Cache::create().unwrap();
        let weak = Arc::downgrade(&cache);
        let mut f = Frame::new(1, 0, false);
        cold(&mut f, cache);
        assert!(
            weak.upgrade().is_none(),
            "completed child retained cache into unrelated world effects"
        );
        assert_eq!(f.next(70, false), 1);
        assert_eq!(f.header, [INSIDE, 70, 0, 0]);
    }
    #[test]
    fn dark_water_reads_block_then_fluid_before_type_and_block_kind() {
        let mut f = Frame::new(1, 0, false);
        cold(&mut f, Cache::create().unwrap());
        for (answer, op) in [
            (70, INSIDE),
            (1, LIGHT),
            (0, BLOCK),
            (0, FLUID),
            (0, WATER_TYPE),
            (1, LIQUID_BLOCK),
        ] {
            assert_eq!(f.next(answer, false), 1);
            assert_eq!(f.header[0], op);
        }
        assert_eq!(f.next(1, false), 0);
        assert_eq!(f.header[0], 1);
    }
    #[test]
    fn dry_snow_never_requests_a_position_or_cache() {
        let mut f = Frame::new(2, 0, false);
        assert_eq!(f.next(0, false), 1);
        assert_eq!(f.header[0], SEA);
        assert_eq!(f.next(63, false), 1);
        assert_eq!(f.header[0], PRECIPITATES);
        assert_eq!(f.next(0, false), 0);
        assert_eq!(f.header[0], 0);
    }
    #[test]
    fn failure_retires_live_temperature_child_without_cache_commit() {
        let cache = Cache::create().unwrap();
        let weak = Arc::downgrade(&cache);
        let mut f = Frame::new(1, 0, false);
        f.next(0, false);
        f.next(63, false);
        f.next(42, false);
        assert_eq!(f.bind(cache, 0), 0);
        assert!(weak.upgrade().is_some());
        assert_eq!(f.next(0, true), -1);
        drop(f);
        assert!(weak.upgrade().is_none());
    }
}

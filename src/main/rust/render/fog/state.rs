//! Original process-wide water transition and per-environment rain history.
use super::policy::{clamp, floor};
use std::sync::{Mutex, OnceLock};
#[derive(Default)]
pub(super) struct Rain(Mutex<f32>);
impl Rain {
    pub(super) fn update(&self, rain: f32, sky_light: i32, precipitation: i32, ticks: f32) -> f32 {
        let target = rain
            * clamp((sky_light as f32 - 8.0) / 7.0, 0.0, 1.0)
            * if precipitation != 0 { 1.0 } else { 0.5 };
        let mut current = self.0.lock().unwrap();
        *current = *current + (target - *current) * ticks * 0.2;
        *current
    }
}
pub(super) struct Water {
    target: i32,
    previous: i32,
    pub(super) changed: i64,
}
impl Default for Water {
    fn default() -> Self {
        Self {
            target: -1,
            previous: -1,
            changed: -1,
        }
    }
}
impl Water {
    pub(super) fn color(&mut self, now: i64, biome: i32) -> i32 {
        if self.changed < 0 {
            self.target = biome;
            self.previous = biome;
            self.changed = now;
        }
        let t = clamp(now.wrapping_sub(self.changed) as f32 / 5000.0, 0.0, 1.0);
        let mut result = 0u32;
        for shift in [24, 16, 8, 0] {
            let start = ((self.previous as u32 >> shift) & 255) as i32;
            let end = ((self.target as u32 >> shift) & 255) as i32;
            result |= (start.wrapping_add(floor(t * (end - start) as f32)) as u32) << shift;
        }
        if self.target != biome {
            self.target = biome;
            self.previous = result as i32;
            self.changed = now;
        }
        result as i32
    }
}
pub(super) fn water() -> &'static Mutex<Water> {
    static WATER: OnceLock<Mutex<Water>> = OnceLock::new();
    WATER.get_or_init(|| Mutex::new(Water::default()))
}

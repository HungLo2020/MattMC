//! Section-local counters. Storage aliases never alias these counters.
//! A single atomic CPU word lets compatibility readers observe signed shorts
//! without downcalls or a second mutable mirror.
use crate::content::block::{BlockRegistry, FluidKind, StateFlags, StateId};
use std::sync::{
    atomic::{AtomicU64, Ordering},
    OnceLock,
};
mod ffi;
#[cfg(test)]
mod tests;

#[repr(transparent)]
pub(crate) struct Owner(AtomicU64);
impl Owner {
    pub(crate) fn new(packed: u64) -> Self {
        Self(AtomicU64::new(packed & 0xffff_ffff_ffff))
    }
    pub(crate) fn packed(&self) -> u64 {
        self.0.load(Ordering::Acquire)
    }
    pub(crate) fn values(&self) -> [i32; 3] {
        unpack(self.packed())
    }
    pub(crate) fn set(&self, values: [i32; 3]) {
        self.0.store(pack(values), Ordering::Release);
    }
    pub(crate) fn adjust(&self, lane: usize, delta: i32, replace: bool) {
        self.0
            .fetch_update(Ordering::AcqRel, Ordering::Acquire, |old| {
                let mut values = unpack(old);
                values[lane] = if replace {
                    delta
                } else {
                    values[lane].wrapping_add(delta)
                };
                Some(pack(values))
            })
            .unwrap();
    }
    pub(crate) fn increment(&self, old: Policy, new: Policy) {
        let a = old.incremental();
        let b = new.incremental();
        if a == b {
            return;
        }
        self.0
            .fetch_update(Ordering::AcqRel, Ordering::Acquire, |packed| {
                let mut values = unpack(packed);
                for lane in 0..3 {
                    values[lane] += b[lane] - a[lane];
                }
                Some(pack(values))
            })
            .unwrap();
    }
}
fn pack(values: [i32; 3]) -> u64 {
    (values[0] as u16 as u64)
        | ((values[1] as u16 as u64) << 16)
        | ((values[2] as u16 as u64) << 32)
}
fn unpack(packed: u64) -> [i32; 3] {
    [
        packed as i16 as i32,
        (packed >> 16) as i16 as i32,
        (packed >> 32) as i16 as i32,
    ]
}
#[derive(Clone, Copy)]
pub(crate) struct Policy {
    air: bool,
    ticking: bool,
    fluid: bool,
    fluid_ticks: bool,
}
impl Policy {
    fn incremental(self) -> [i32; 3] {
        [
            i32::from(!self.air),
            i32::from(!self.air && self.ticking),
            i32::from(self.fluid),
        ]
    }
    pub(crate) fn recount(self) -> [i32; 3] {
        [
            i32::from(!self.air) + i32::from(self.fluid),
            i32::from(!self.air && self.ticking),
            i32::from(self.fluid && self.fluid_ticks),
        ]
    }
}
fn policies(registry: &BlockRegistry) -> Vec<Option<Policy>> {
    registry
        .flag_column()
        .iter()
        .enumerate()
        .map(|(id, flags)| {
            if flags.contains(StateFlags::CUSTOM) {
                return None;
            }
            Some(Policy {
                air: flags.contains(StateFlags::AIR),
                ticking: flags.contains(StateFlags::RANDOM_TICKS),
                fluid: flags.contains(StateFlags::HAS_FLUID),
                // Frozen's built-in LavaFluid alone overrides Fluid.isRandomlyTicking.
                fluid_ticks: registry.fluid(StateId(id as u16)) == FluidKind::Lava,
            })
        })
        .collect()
}
pub(crate) fn installed_policies() -> Option<&'static [Option<Policy>]> {
    static POLICIES: OnceLock<Vec<Option<Policy>>> = OnceLock::new();
    let registry = crate::content::block::installed()?;
    Some(POLICIES.get_or_init(|| policies(registry)))
}

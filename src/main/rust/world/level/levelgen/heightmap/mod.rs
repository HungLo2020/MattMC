//! Packed chunk-column heightmap reconstruction. No block mutation or pointers retained.
mod ffi;
mod scan;
#[cfg(test)]
mod tests;

use crate::content::block::{BlockRegistry, StateFlags};
use std::sync::OnceLock;

/// The mask of a custom `BlockState` subclass, which Java scans itself.
pub(crate) const CUSTOM: u32 = u32::MAX;

/// Per state, bit `Heightmap.Types.ordinal()` set when that type's
/// `isOpaque` accepts it, with the original primer's explicit test making
/// the AIR block itself 0.
pub(crate) struct Masks {
    pub masks: Vec<u32>,
    /// Some state is [`CUSTOM`]: the global palette stays with Java.
    pub custom: bool,
}

pub(crate) fn masks(registry: &BlockRegistry) -> Masks {
    let air = registry.air();
    let masks: Vec<u32> = registry
        .flag_column()
        .iter()
        .zip(registry.block_column())
        .map(|(&f, &block)| {
            if f.contains(StateFlags::CUSTOM) {
                return CUSTOM;
            }
            if Some(block) == air {
                return 0;
            }
            let not_air = !f.contains(StateFlags::AIR);
            let motion = f.contains(StateFlags::BLOCKS_MOTION);
            let blocking = motion || f.contains(StateFlags::HAS_FLUID);
            // WORLD_SURFACE_WG, WORLD_SURFACE, OCEAN_FLOOR_WG, OCEAN_FLOOR,
            // MOTION_BLOCKING, MOTION_BLOCKING_NO_LEAVES.
            [not_air, not_air, motion, motion, blocking, blocking && !f.contains(StateFlags::LEAVES)]
                .iter()
                .enumerate()
                .fold(0, |mask, (bit, &set)| if set { mask | 1 << bit } else { mask })
        })
        .collect();
    let custom = masks.contains(&CUSTOM);
    Masks { masks, custom }
}

/// [`masks`] of the installed registry; `None` until it is installed.
pub(crate) fn installed_masks() -> Option<&'static Masks> {
    static MASKS: OnceLock<Masks> = OnceLock::new();
    let registry = crate::content::block::installed()?;
    Some(MASKS.get_or_init(|| masks(registry)))
}

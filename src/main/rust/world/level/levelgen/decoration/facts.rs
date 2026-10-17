//! Per-state facts `WorldGenRegion.setBlock` needs beyond the block registry,
//! installed once by Java: POI type, post-processing and light-shape use.
use std::sync::OnceLock;

/// Java's word per state: bits 0–15 the POI type index plus one (0 none),
/// bits 16–17 `hasPostProcess` (0 false, 1 true, 2 depends on the world),
/// bit 18 `useShapeForLightOcclusion`, bit 19 `hasDynamicShape`, bits 20–22 the
/// fluid is a source, is still water, is empty.
pub(crate) struct Facts {
    words: Box<[u32]>,
}

pub(crate) const POST_PROCESS_UNKNOWN: u32 = 2;

impl Facts {
    pub(crate) fn poi(&self, state: u32) -> u32 {
        self.words.get(state as usize).map_or(0, |w| w & 0xffff)
    }

    pub(crate) fn post_process(&self, state: u32) -> u32 {
        self.words.get(state as usize).map_or(POST_PROCESS_UNKNOWN, |w| (w >> 16) & 3)
    }

    pub(crate) fn uses_shape_for_light_occlusion(&self, state: u32) -> bool {
        self.words.get(state as usize).is_some_and(|w| w & (1 << 18) != 0)
    }

    /// `Block.hasDynamicShape`: no shape cache, so sturdy-face and support
    /// answers depend on the world and come from Java.
    pub(crate) fn dynamic_shape(&self, state: u32) -> bool {
        self.words.get(state as usize).is_none_or(|w| w & (1 << 19) != 0)
    }

    /// Fluid bits in the template layout: 4 source, 8 still water, 16 empty
    /// (Java's bits 20, 21 and 22).
    pub(crate) fn fluid_flags(&self, state: u32) -> u32 {
        self.words.get(state as usize).map_or(0, |w| ((w >> 20) & 7) << 2)
    }

    pub(crate) fn len(&self) -> usize {
        self.words.len()
    }

    /// Some state's post-processing depends on the world.
    pub(crate) fn any_unknown(&self) -> bool {
        self.words.iter().any(|w| (w >> 16) & 3 == POST_PROCESS_UNKNOWN)
    }
}

static FACTS: OnceLock<Facts> = OnceLock::new();

pub(crate) fn installed() -> Option<&'static Facts> {
    FACTS.get()
}

/// Installs the facts once; a second install must be identical.
pub(crate) fn install(words: &[u32]) -> bool {
    let facts = FACTS.get_or_init(|| Facts { words: words.into() });
    *facts.words == *words
}

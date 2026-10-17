//! Distant Horizons LOD wire vocabulary: frame flags, layers, material
//! variants and the vertex layout version.

use std::{ops::Deref, sync::Arc};

/// One ordered reference to an immutable, published CPU column generation.
/// The collector and renderer share this vocabulary without converting it at
/// the frame boundary. These are semantic identities, never GPU handles.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct WorldLodColumnInstance {
    pub column_key: u64,
    pub column_generation: u64,
    pub layer: u32,
    pub segment_index: u32,
    pub order: u32,
}

/// Immutable selected LOD work. Derived passes and queued frames share its
/// allocation; clearing a pass drops its reference without copying the list.
/// Empty frames need no allocation.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct WorldLodInstances(Option<Arc<Vec<WorldLodColumnInstance>>>);

impl WorldLodInstances {
    pub fn clear(&mut self) { self.0 = None; }

    pub fn shares_storage(&self, other: &Self) -> bool {
        match (&self.0, &other.0) {
            (None, None) => true,
            (Some(a), Some(b)) => Arc::ptr_eq(a, b),
            _ => false,
        }
    }
}

impl From<Vec<WorldLodColumnInstance>> for WorldLodInstances {
    fn from(instances: Vec<WorldLodColumnInstance>) -> Self {
        Self((!instances.is_empty()).then(|| Arc::new(instances)))
    }
}

impl Deref for WorldLodInstances {
    type Target = [WorldLodColumnInstance];
    fn deref(&self) -> &Self::Target { self.0.as_ref().map(|instances| instances.as_slice()).unwrap_or(&[]) }
}

impl<'a> IntoIterator for &'a WorldLodInstances {
    type Item = &'a WorldLodColumnInstance;
    type IntoIter = std::slice::Iter<'a, WorldLodColumnInstance>;
    fn into_iter(self) -> Self::IntoIter { self.iter() }
}

/// Stable semantic layout decoded from Distant Horizons' CPU LOD builder.
/// This is intentionally separate from `WorldMeshVertex`: DH has vertex
/// color/material/light semantics but no Minecraft atlas UV ownership.
pub const WORLD_LOD_VERTEX_LAYOUT_V1: u32 = 1;
pub const WORLD_LOD_MATERIAL_UNAVAILABLE: u32 = 0;
pub const WORLD_LOD_MATERIAL_MIXED: u32 = u32::MAX;
pub const WORLD_LOD_VARIANT_UNAVAILABLE: u8 = 0;
pub const WORLD_LOD_VARIANT_EXACT: u8 = 1;
pub const WORLD_LOD_VARIANT_MIXED: u8 = 2;
pub const WORLD_LOD_LAYER_OPAQUE: u32 = 1;
pub const WORLD_LOD_LAYER_TRANSPARENT_SIDE: u32 = 2;
pub const WORLD_LOD_LAYER_TRANSPARENT_UP: u32 = 3;
pub const WORLD_LOD_LAYER_TRANSPARENT_WATER_UP: u32 = 4;
/// The Java-side DH preflight selected this exact non-water frame for the Rust
/// whole-frame route. Capture-only LOD semantics deliberately leave this clear,
/// so observing Java/DH geometry cannot accidentally create a second renderer.
pub const WORLD_LOD_FLAG_RUST_ROUTE_SELECTED: u32 = 1 << 4;
/// Compatibility name for existing semantic transport users. The bit and ABI
/// are unchanged; it now admits opaque plus explicitly ordered non-water
/// transparent DH work.
pub const WORLD_LOD_FLAG_RUST_OPAQUE_ROUTE_SELECTED: u32 = WORLD_LOD_FLAG_RUST_ROUTE_SELECTED;
/// Copied DH vanilla-transition policy. NONE leaves the direct sparse
/// compositor at its existing boundary; one bit selects SINGLE or DOUBLE,
/// while both bits encode DH's LOD-only replacement at both source callsites.
pub const WORLD_LOD_FLAG_VANILLA_FADE_SINGLE_PASS: u32 = 1 << 5;
pub const WORLD_LOD_FLAG_VANILLA_FADE_DOUBLE_PASS: u32 = 1 << 6;
/// Frozen's DH far-clip fade is applied before the copied vanilla transition.
/// The bit is semantic policy only; Rust owns the private color/depth pass.
pub const WORLD_LOD_FLAG_DH_FAR_CLIP_FADE: u32 = 1 << 7;

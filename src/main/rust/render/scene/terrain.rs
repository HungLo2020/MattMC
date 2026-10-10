//! Immutable CPU terrain vocabulary shared by selection and frame consumers.
use std::sync::Arc;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct StaticTerrainShadowCaster {
    pub mesh_key: u64,
    pub mesh_generation: u64,
    pub origin: [i32; 3],
    pub depth_policy: u32,
}

#[derive(Clone, Debug, Default, PartialEq)]
pub struct StaticTerrainShadowCasters {
    pub camera: [f64; 3],
    pub casters: Arc<Vec<StaticTerrainShadowCaster>>,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct StaticTerrainSection {
    pub mesh_key: u64,
    pub depth_policy: u32,
    pub origin: [i32; 3],
    pub flags: u32,
}

#[derive(Clone, Debug, Default, PartialEq)]
pub struct StaticTerrainSections {
    pub camera: [f64; 3],
    pub sections: Arc<Vec<StaticTerrainSection>>,
}

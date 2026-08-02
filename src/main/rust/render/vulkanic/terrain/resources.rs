#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct TerrainResourceGeneration(pub u64);

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct TerrainAtlasIdentity {
    pub texture_id: u32,
    pub generation: TerrainResourceGeneration,
}

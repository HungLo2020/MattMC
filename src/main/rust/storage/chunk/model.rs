use crate::storage::nbt::model::JavaString;

#[derive(Clone, Debug, PartialEq)]
pub struct ChunkSectionDecode {
    pub data_version: i32,
    pub chunk_x: i32,
    pub chunk_z: i32,
    pub y_pos: i32,
    pub status: JavaString,
    pub is_light_on: bool,
    pub last_update: i64,
    pub inhabited_time: i64,
    pub requires_dfu: bool,
    pub sections: Vec<ChunkSectionRecord>,
    pub heightmaps: Vec<HeightmapRecord>,
}

#[derive(Clone, Debug, PartialEq)]
pub struct ChunkSectionRecord {
    pub section_y: i32,
    pub has_block_states: bool,
    pub has_biomes: bool,
    pub block_palette: Vec<Vec<u8>>,
    pub block_data: Vec<i64>,
    pub biome_palette: Vec<BiomePaletteEntry>,
    pub biome_data: Vec<i64>,
    pub block_light: Option<Vec<u8>>,
    pub sky_light: Option<Vec<u8>>,
}

#[derive(Clone, Debug, PartialEq)]
pub enum BiomePaletteEntry {
    ResourceId(JavaString),
    Tape(Vec<u8>),
}

#[derive(Clone, Debug, PartialEq)]
pub struct HeightmapRecord {
    pub name: JavaString,
    pub data: Vec<i64>,
}

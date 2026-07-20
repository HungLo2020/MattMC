#[derive(Clone, Debug, PartialEq)]
pub struct EntityChunkEnvelope {
    pub data_version: i32,
    pub chunk_x: i32,
    pub chunk_z: i32,
    pub requires_dfu: bool,
    pub entities: Vec<EntityEnvelope>,
}

#[derive(Clone, Debug, PartialEq)]
pub struct EntityEnvelope {
    pub id: Option<String>,
    pub id_malformed: bool,
    pub uuid: Option<(i64, i64)>,
    pub position: Option<(u64, u64, u64)>,
    pub passenger_count: u32,
    pub passenger_depth: u32,
    pub nbt_tape: Vec<u8>,
}

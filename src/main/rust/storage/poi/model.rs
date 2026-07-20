#[derive(Clone, Debug, PartialEq, Eq)]
pub struct PoiChunk {
    pub sections: Vec<PoiSection>,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct PoiSection {
    pub section_y: i32,
    pub valid: bool,
    pub records: Vec<PoiRecord>,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct PoiRecord {
    pub x: i32,
    pub y: i32,
    pub z: i32,
    pub poi_type: String,
    pub free_tickets: i32,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq, Hash)]
pub struct SectionMeshKey {
    pub section_pos: i64,
    pub layer: u8,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct SectionMeshGeneration(pub u64);

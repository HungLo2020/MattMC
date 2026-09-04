use super::*;
use crate::render::chunk::meshing::lighting::neighborhood_index;

struct CompactSnapshotStorage {
    active_indices: Vec<u16>,
    padded_state_ids: Vec<i32>,
    padded_light_words: Vec<i32>,
    block_ids: Vec<i32>,
    seed_los: Vec<i32>,
    seed_his: Vec<i32>,
    tints: Vec<i32>,
    tint_lattices: Vec<i32>,
    fluid_tints: Vec<i32>,
    fluid_flow_x: Vec<f32>,
    fluid_flow_z: Vec<f32>,
    fluid_block_ids: Vec<i32>,
    flags: Vec<i32>,
}

impl CompactSnapshotStorage {
    fn new() -> Self {
        let padded_len = COMPACT_SECTION_PADDED_LENGTH
            * COMPACT_SECTION_PADDED_LENGTH
            * COMPACT_SECTION_PADDED_LENGTH;
        Self {
            active_indices: vec![0],
            padded_state_ids: vec![0; padded_len],
            padded_light_words: vec![0; padded_len],
            block_ids: vec![0; COMPACT_SECTION_BLOCK_COUNT],
            seed_los: vec![0; COMPACT_SECTION_BLOCK_COUNT],
            seed_his: vec![0; COMPACT_SECTION_BLOCK_COUNT],
            tints: vec![0; COMPACT_SECTION_BLOCK_COUNT],
            tint_lattices: vec![0; COMPACT_SECTION_BLOCK_COUNT * 9],
            fluid_tints: vec![0; COMPACT_SECTION_BLOCK_COUNT],
            fluid_flow_x: vec![0.0; COMPACT_SECTION_BLOCK_COUNT],
            fluid_flow_z: vec![0.0; COMPACT_SECTION_BLOCK_COUNT],
            fluid_block_ids: vec![0; COMPACT_SECTION_BLOCK_COUNT],
            flags: vec![0; COMPACT_SECTION_BLOCK_COUNT],
        }
    }

    fn header(&self, active_count: i32) -> CompactSectionSnapshotHeader {
        CompactSectionSnapshotHeader {
            version: COMPACT_SECTION_SNAPSHOT_VERSION,
            active_count,
            min_x: 100,
            min_y: 200,
            min_z: 300,
            _padding: 0,
            active_indices_address: self.active_indices.as_ptr() as u64,
            padded_state_ids_address: self.padded_state_ids.as_ptr() as u64,
            padded_light_words_address: self.padded_light_words.as_ptr() as u64,
            block_ids_address: self.block_ids.as_ptr() as u64,
            seed_los_address: self.seed_los.as_ptr() as u64,
            seed_his_address: self.seed_his.as_ptr() as u64,
            tints_address: self.tints.as_ptr() as u64,
            fluid_tints_address: self.fluid_tints.as_ptr() as u64,
            fluid_flow_x_address: self.fluid_flow_x.as_ptr() as u64,
            fluid_flow_z_address: self.fluid_flow_z.as_ptr() as u64,
            fluid_block_ids_address: self.fluid_block_ids.as_ptr() as u64,
            flags_address: self.flags.as_ptr() as u64,
            tint_lattices_address: self.tint_lattices.as_ptr() as u64,
        }
    }
}

#[test]
fn compact_section_snapshot_header_layout_matches_java() {
    assert_eq!(128, std::mem::size_of::<CompactSectionSnapshotHeader>());
    assert_eq!(
        0,
        std::mem::offset_of!(CompactSectionSnapshotHeader, version)
    );
    assert_eq!(
        4,
        std::mem::offset_of!(CompactSectionSnapshotHeader, active_count)
    );
    assert_eq!(8, std::mem::offset_of!(CompactSectionSnapshotHeader, min_x));
    assert_eq!(
        12,
        std::mem::offset_of!(CompactSectionSnapshotHeader, min_y)
    );
    assert_eq!(
        16,
        std::mem::offset_of!(CompactSectionSnapshotHeader, min_z)
    );
    assert_eq!(
        24,
        std::mem::offset_of!(CompactSectionSnapshotHeader, active_indices_address)
    );
    assert_eq!(
        32,
        std::mem::offset_of!(CompactSectionSnapshotHeader, padded_state_ids_address)
    );
    assert_eq!(
        40,
        std::mem::offset_of!(CompactSectionSnapshotHeader, padded_light_words_address)
    );
    assert_eq!(
        48,
        std::mem::offset_of!(CompactSectionSnapshotHeader, block_ids_address)
    );
    assert_eq!(
        56,
        std::mem::offset_of!(CompactSectionSnapshotHeader, seed_los_address)
    );
    assert_eq!(
        64,
        std::mem::offset_of!(CompactSectionSnapshotHeader, seed_his_address)
    );
    assert_eq!(
        72,
        std::mem::offset_of!(CompactSectionSnapshotHeader, tints_address)
    );
    assert_eq!(
        80,
        std::mem::offset_of!(CompactSectionSnapshotHeader, fluid_tints_address)
    );
    assert_eq!(
        88,
        std::mem::offset_of!(CompactSectionSnapshotHeader, fluid_flow_x_address)
    );
    assert_eq!(
        96,
        std::mem::offset_of!(CompactSectionSnapshotHeader, fluid_flow_z_address)
    );
    assert_eq!(
        104,
        std::mem::offset_of!(CompactSectionSnapshotHeader, fluid_block_ids_address)
    );
    assert_eq!(
        112,
        std::mem::offset_of!(CompactSectionSnapshotHeader, flags_address)
    );
}

#[test]
fn compact_section_snapshot_rejects_malformed_headers_and_pointer_sets() {
    let storage = CompactSnapshotStorage::new();
    assert_eq!(Err(ERR_NULL_POINTER), unsafe {
        CompactSectionSnapshot::from_address(0).map(|_| ())
    });

    let mut header = storage.header(1);
    header.version = COMPACT_SECTION_SNAPSHOT_VERSION + 1;
    assert_eq!(Err(ERR_INVALID_ARGUMENT), unsafe {
        CompactSectionSnapshot::from_address(&header as *const _ as u64).map(|_| ())
    });

    header = storage.header(-1);
    assert_eq!(Err(ERR_INVALID_ARGUMENT), unsafe {
        CompactSectionSnapshot::from_address(&header as *const _ as u64).map(|_| ())
    });

    header = storage.header((COMPACT_SECTION_BLOCK_COUNT + 1) as i32);
    assert_eq!(Err(ERR_INVALID_ARGUMENT), unsafe {
        CompactSectionSnapshot::from_address(&header as *const _ as u64).map(|_| ())
    });

    header = storage.header(1);
    header.padded_light_words_address = 0;
    assert_eq!(Err(ERR_NULL_POINTER), unsafe {
        CompactSectionSnapshot::from_address(&header as *const _ as u64).map(|_| ())
    });
}

#[test]
fn compact_section_snapshot_reconstructs_padded_border_record() {
    let mut storage = CompactSnapshotStorage::new();
    storage.active_indices[0] = 0;
    storage.block_ids[0] = 77;
    storage.seed_los[0] = 11;
    storage.seed_his[0] = 12;
    storage.tints[0] = 13;
    storage.fluid_tints[0] = 14;
    storage.fluid_flow_x[0] = 0.25;
    storage.fluid_flow_z[0] = -0.5;
    storage.fluid_block_ids[0] = 78;
    storage.flags[0] = NATIVE_SECTION_BLOCK_FLAG_SUPPRESS_FLUID;

    let set_cell = |states: &mut [i32], lights: &mut [i32], x, y, z, state, light| {
        let index = CompactSectionSnapshot::padded_index(x, y, z);
        states[index] = state;
        lights[index] = light;
    };
    set_cell(
        &mut storage.padded_state_ids,
        &mut storage.padded_light_words,
        1,
        1,
        1,
        42,
        420,
    );
    set_cell(
        &mut storage.padded_state_ids,
        &mut storage.padded_light_words,
        1,
        0,
        1,
        101,
        1001,
    );
    set_cell(
        &mut storage.padded_state_ids,
        &mut storage.padded_light_words,
        1,
        2,
        1,
        102,
        1002,
    );
    set_cell(
        &mut storage.padded_state_ids,
        &mut storage.padded_light_words,
        1,
        1,
        0,
        103,
        1003,
    );
    set_cell(
        &mut storage.padded_state_ids,
        &mut storage.padded_light_words,
        1,
        1,
        2,
        104,
        1004,
    );
    set_cell(
        &mut storage.padded_state_ids,
        &mut storage.padded_light_words,
        0,
        1,
        1,
        105,
        1005,
    );
    set_cell(
        &mut storage.padded_state_ids,
        &mut storage.padded_light_words,
        2,
        1,
        1,
        106,
        1006,
    );

    let header = storage.header(1);
    let snapshot =
        unsafe { CompactSectionSnapshot::from_address(&header as *const _ as u64).unwrap() };
    let record = unsafe { snapshot.record_at(0).unwrap() };

    assert_eq!(42, record.state_id);
    assert_eq!([101, 102, 103, 104, 105, 106], record.neighbor_state_ids);
    assert_eq!(100, record.absolute_x);
    assert_eq!(200, record.absolute_y);
    assert_eq!(300, record.absolute_z);
    assert_eq!(77, record.block_id);
    assert_eq!(11, record.seed_lo);
    assert_eq!(12, record.seed_hi);
    assert_eq!(13, record.tint);
    assert_eq!(14, record.fluid_tint);
    assert_eq!(78, record.fluid_block_id);
    assert_eq!(NATIVE_SECTION_BLOCK_FLAG_SUPPRESS_FLUID, record.flags);
    assert_eq!(
        105,
        record.neighborhood_state_ids[neighborhood_index(-1, 0, 0)]
    );
    assert_eq!(1006, record.light_words[neighborhood_index(1, 0, 0)]);
}

#[test]
fn compact_section_snapshot_rejects_stale_or_invalid_active_indexes() {
    let mut storage = CompactSnapshotStorage::new();
    storage.active_indices[0] = COMPACT_SECTION_BLOCK_COUNT as u16;
    let header = storage.header(1);
    let snapshot =
        unsafe { CompactSectionSnapshot::from_address(&header as *const _ as u64).unwrap() };

    assert_eq!(Err(ERR_INVALID_ARGUMENT), unsafe {
        snapshot.record_at(0).map(|_| ())
    });
}

use super::*;

#[test]
fn source_multidraw_stream_packs_records_and_preserves_descriptor_alignment() {
    let mut gal = gal();
    let mut frontend = WorldPrimitiveFrontend::default();
    frontend
        .reserve_source_terrain_frame_stream_capacity(&mut gal, 91, 64 * 1024)
        .unwrap();
    let first = frontend
        .allocate_source_terrain_frame_stream_aligned(
            &mut gal,
            91,
            128,
            64,
            80,
            None,
            SOURCE_TERRAIN_MULTIDRAW_INSTANCE_ALIGNMENT,
        )
        .unwrap();
    assert_eq!(first.legacy_transform_offset, 0);
    assert_eq!(first.scalar_uniform_offset, Some(256));
    assert_eq!(first.instance_offset, 320);
    let mut transaction = SourceTerrainFrameTransaction {
        frame_id: 91,
        stream_buffer: first.buffer,
        stream_epoch: first.epoch,
        operations: Vec::new(),
        source_material_texture_ids: BTreeSet::new(),
        geometry_uploads: Vec::new(),
        stream_staging: Vec::new(),
        shared_uniforms: Vec::new(),
        indirect_buffer: None,
        indirect_staging: Vec::new(),
    };
    transaction.stage_stream_parts(first, &[7; 128], &[9; 64], &[0; 80]);
    let shared = Some((first.legacy_transform_offset, first.scalar_uniform_offset));
    let mut previous_end = first.instance_offset + 80;
    for identity in 1..=128u8 {
        let next = frontend
            .allocate_source_terrain_frame_stream_aligned(
                &mut gal,
                91,
                128,
                64,
                80,
                shared,
                SOURCE_TERRAIN_MULTIDRAW_INSTANCE_ALIGNMENT,
            )
            .unwrap();
        assert_eq!(next.buffer, first.buffer);
        assert_eq!(next.epoch, first.epoch);
        assert_eq!(next.legacy_transform_offset, first.legacy_transform_offset);
        assert_eq!(next.scalar_uniform_offset, first.scalar_uniform_offset);
        assert_eq!(
            next.instance_offset, previous_end,
            "shared records must be contiguous"
        );
        assert_eq!(next.instance_offset % 80, 0);
        transaction.stage_stream_write(next.instance_offset, &[identity; 80]);
        previous_end = next.instance_offset + 80;
    }
    assert_eq!(&transaction.stream_staging[..128], &[7; 128]);
    assert_eq!(&transaction.stream_staging[256..320], &[9; 64]);
    for identity in 0..=128usize {
        // This is the exact firstInstance addressing used by the draw, from
        // a storage descriptor bound at zero, across every staged record.
        let first_instance = first.instance_offset as usize / 80 + identity;
        assert_eq!(
            &transaction.stream_staging[first_instance * 80..(first_instance + 1) * 80],
            &[identity as u8; 80],
        );
    }
    // A changed uniform payload after densely packed instances must still
    // have aligned absolute descriptor offsets, not offsets relative to a
    // record-aligned batch base.
    let changed = frontend
        .allocate_source_terrain_frame_stream_aligned(
            &mut gal,
            91,
            128,
            64,
            80,
            None,
            SOURCE_TERRAIN_MULTIDRAW_INSTANCE_ALIGNMENT,
        )
        .unwrap();
    assert!(changed.legacy_transform_offset >= previous_end);
    assert_eq!(changed.legacy_transform_offset % 256, 0);
    assert_eq!(changed.scalar_uniform_offset.unwrap() % 256, 0);
    assert!(changed.scalar_uniform_offset.unwrap() >= changed.legacy_transform_offset + 128);
    assert!(changed.instance_offset >= changed.scalar_uniform_offset.unwrap() + 64);
    assert_eq!(changed.instance_offset % 80, 0);

    // Direct draws bind instances using a dynamic descriptor offset and
    // retain their 256-byte alignment even in the same mixed-use slot.
    let direct = frontend
        .allocate_source_terrain_frame_stream(&mut gal, 91, 128, 64, 80)
        .unwrap();
    assert!(direct.legacy_transform_offset >= changed.instance_offset + 80);
    assert_eq!(direct.legacy_transform_offset % 256, 0);
    assert_eq!(direct.scalar_uniform_offset.unwrap() % 256, 0);
    assert_eq!(direct.instance_offset % 256, 0);
    let slot = frontend
        .source_terrain_frame_stream_slots
        .iter()
        .find(|slot| slot.frame_id == Some(91))
        .unwrap();
    assert_eq!(slot.cursor, direct.instance_offset + 80);
    assert!(slot.cursor <= slot.capacity);
    frontend.destroy_resources(&mut gal);
    assert_eq!(
        gal.metrics().resource_creates,
        gal.metrics().resource_destroys
    );
}

#[test]
fn source_stream_packing_rejects_invalid_ranges_without_allocating_or_advancing() {
    let mut gal = gal();
    let mut frontend = WorldPrimitiveFrontend::default();
    let creates = gal.metrics().resource_creates;
    for (legacy, scalar, instance, alignment) in [
        (128, 64, 80, 0),
        (u64::MAX, 64, 80, 80),
        (128, u64::MAX, 80, 80),
        (128, 64, u64::MAX, 80),
    ] {
        assert!(
            frontend
                .allocate_source_terrain_frame_stream_aligned(
                    &mut gal, 92, legacy, scalar, instance, None, alignment,
                )
                .is_err()
        );
        assert_eq!(gal.metrics().resource_creates, creates);
        assert!(frontend.source_terrain_frame_stream_slots.is_empty());
    }
    let first = frontend
        .allocate_source_terrain_frame_stream_aligned(&mut gal, 92, 128, 64, 80, None, 80)
        .unwrap();
    let capacity = frontend.source_terrain_frame_stream_slots[0].capacity;
    let cursor = frontend.source_terrain_frame_stream_slots[0].cursor;
    assert!(
        frontend
            .allocate_source_terrain_frame_stream_aligned(
                &mut gal,
                92,
                128,
                64,
                capacity,
                Some((0, Some(256))),
                80,
            )
            .is_err()
    );
    assert_eq!(frontend.source_terrain_frame_stream_slots[0].cursor, cursor);
    let next = frontend
        .allocate_source_terrain_frame_stream_aligned(
            &mut gal,
            92,
            128,
            64,
            80,
            Some((first.legacy_transform_offset, first.scalar_uniform_offset)),
            80,
        )
        .unwrap();
    assert_eq!(next.instance_offset, cursor);
    frontend.destroy_resources(&mut gal);
}

use super::*;

#[test]
fn shared_graph_buffers_are_local_and_never_caller_owned() {
    let r = registry();
    let oak = r.find("minecraft:oak_stairs").unwrap();
    let birch = r.find("minecraft:birch_stairs").unwrap();
    assert_ne!(oak.first_state.0, birch.first_state.0);
    assert_eq!(r.template(oak).graph, r.template(birch).graph);
    let graph = r.template(oak).graph as usize;
    assert!(r.graphs[graph].buffer(1).unwrap().iter().all(|&v| v >= 0 && (v as usize) < r.state_count(oak)));
    unsafe {
        let mut length = -1;
        let first = ffi::mattmc_block_definition_graph_buffer(graph as i32, 2, &mut length);
        assert_eq!(length as usize, r.graphs[graph].buffer(1).unwrap().len());
        let second = ffi::mattmc_block_definition_graph_buffer(r.template(birch).graph as i32, 2, &mut length);
        assert_eq!(first, second);
        assert_eq!(first, r.graphs[graph].buffer(1).unwrap().as_ptr());
        assert!(ffi::mattmc_block_definition_graph_buffer(-1, 0, &mut length).is_null());
        assert_eq!(length, 0);
        assert!(ffi::mattmc_block_definition_graph_buffer(0, 4, &mut length).is_null());
        assert_eq!(length, 0);
        assert!(ffi::mattmc_block_definition_graph_buffer(0, 0, std::ptr::null_mut()).is_null());
        assert!(ffi::mattmc_block_definitions_buffer(15, &mut length).is_null());
        assert_eq!(length, 0);
        assert!(ffi::mattmc_block_definitions_buffer(0, std::ptr::null_mut()).is_null());
    }
}

#[test]
fn physical_profiles_are_shared_independently_of_state_domains() {
    let r = registry();
    let stone = r.find("minecraft:stone").unwrap();
    let granite = r.find("minecraft:granite").unwrap();
    let air = r.find("minecraft:air").unwrap();
    assert!(std::ptr::eq(stone.physics, granite.physics));
    assert_eq!(stone.template, air.template);
    assert!(!std::ptr::eq(stone.physics, air.physics));
    assert!(stone.physics.flags.contains(physics::PhysicalFlags::HAS_COLLISION));
    assert!(!air.physics.flags.contains(physics::PhysicalFlags::HAS_COLLISION));
    assert!(air.physics.flags.contains(physics::PhysicalFlags::AIR));
    let negative_hardness = r.find("minecraft:bedrock").unwrap().physics;
    assert_eq!(negative_hardness.hardness, -1.0);
    assert_eq!(negative_hardness.resistance, 3_600_000.0);
    unsafe {
        let mut length = 0;
        let rows = ffi::mattmc_block_definitions_buffer(5, &mut length);
        assert_eq!(length as usize, physics::PROFILES.len() * 7);
        assert_eq!(rows, r.physics_rows.as_ptr().cast());
        assert_eq!(ffi::mattmc_block_definitions_buffer(5, &mut length), rows);
    }
}

#[test]
fn intrinsic_and_rule_buffers_are_bounded_process_lifetime_views() {
    let r = registry();
    assert_eq!(r.intrinsic_states.len(), r.intrinsic_rows.len());
    assert_eq!(r.rule_refs.len(), r.definitions.len() * 2);
    assert!(r.state_traits(StateId(u16::MAX)).is_none());
    unsafe {
        let mut length = -1;
        for (kind, values) in [(6, &r.intrinsic_rows), (7, &r.rule_refs), (8, &r.rule_rows), (9, &r.rule_properties), (10, &r.rule_values)] {
            let first = ffi::mattmc_block_definitions_buffer(kind, &mut length);
            assert_eq!(length as usize, values.len());
            assert_eq!(first, values.as_ptr().cast());
            assert_eq!(ffi::mattmc_block_definitions_buffer(kind, &mut length), first);
        }
        assert!(ffi::mattmc_block_definitions_buffer(15, &mut length).is_null());
        assert_eq!(length, 0);
    }
}

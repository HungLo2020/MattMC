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
        assert!(ffi::mattmc_block_definitions_buffer(5, &mut length).is_null());
        assert_eq!(length, 0);
        assert!(ffi::mattmc_block_definitions_buffer(0, std::ptr::null_mut()).is_null());
    }
}

use super::*;

#[test]
fn native_pass_index_accepts_only_runtime_render_passes() {
    assert_eq!(Some(0), native_pass_index(0));
    assert_eq!(Some(1), native_pass_index(1));
    assert_eq!(Some(2), native_pass_index(2));
    assert_eq!(None, native_pass_index(-1));
    assert_eq!(None, native_pass_index(3));
}

#[test]
fn scan_dispatch_applies_fluid_suppression_without_hiding_other_producers() {
    let dispatch = scan_dispatch(
        STATE_FLAG_MODEL | STATE_FLAG_FLUID | STATE_FLAG_LIGHT_BLOCK,
        NATIVE_SECTION_BLOCK_FLAG_SUPPRESS_FLUID,
    );

    assert!(dispatch.has_model);
    assert!(dispatch.has_light_block);
    assert!(!dispatch.has_fluid);
}

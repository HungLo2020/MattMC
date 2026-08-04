use super::*;

#[test]
fn native_quad_layout_matches_java_stride() {
    assert_eq!(32, std::mem::size_of::<QuadVertex>());
    assert_eq!(152, std::mem::size_of::<NativeQuad>());
    assert_eq!(156, std::mem::size_of::<FlatQuadRecord>());
    assert_eq!(24, std::mem::size_of::<LightBlockRecord>());
    assert_eq!(176, std::mem::size_of::<FluidFaceRecord>());
}

#[test]
fn compact_format_metadata_is_rust_owned() {
    assert_eq!(20, compact_format_value(COMPACT_VALUE_STRIDE));
    assert_eq!(0, compact_format_value(COMPACT_VALUE_POSITION_OFFSET));
    assert_eq!(8, compact_format_value(COMPACT_VALUE_COLOR_OFFSET));
    assert_eq!(12, compact_format_value(COMPACT_VALUE_TEXTURE_OFFSET));
    assert_eq!(
        16,
        compact_format_value(COMPACT_VALUE_LIGHT_MATERIAL_INDEX_OFFSET)
    );
    assert_eq!(0, compact_format_value(COMPACT_VALUE_BLOCK_ID_OFFSET));
    assert_eq!(
        1 << 20,
        compact_format_value(COMPACT_VALUE_POSITION_MAX_VALUE)
    );
    assert_eq!(
        1 << 15,
        compact_format_value(COMPACT_VALUE_TEXTURE_MAX_VALUE)
    );
}

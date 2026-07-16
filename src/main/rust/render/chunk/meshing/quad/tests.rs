use super::*;

#[test]
fn native_quad_write_helpers_populate_rust_owned_layout() {
    let mut quad = NativeQuad::default();
    let address = &mut quad as *mut NativeQuad as u64;

    unsafe {
        assert_eq!(
            OK,
            write_native_quad_metadata(address, 13, 2, 1, 99, 4, 5, 6, 7)
        );
        assert_eq!(
            OK,
            write_native_quad_vertex(
                address, 2, 1.25, 2.5, 3.75, 0x11223344, 0.875, 0.125, 0.625, 0x00f000f0,
            )
        );
    }

    assert_eq!(13, quad.block_emission);
    assert_eq!(2, quad.render_type);
    assert_eq!(1, quad.ignore_mid_block);
    assert_eq!(0, quad._padding);
    assert_eq!(99, quad.block_id);
    assert_eq!(4, quad.local_x);
    assert_eq!(5, quad.local_y);
    assert_eq!(6, quad.local_z);
    assert_eq!(7, quad.material_bits);
    assert_eq!(1.25, quad.vertices[2].x);
    assert_eq!(2.5, unsafe { native_quad_position(address, 2, 1) });
    assert_eq!(3.75, unsafe { native_quad_position(address, 2, 2) });
    assert_eq!(0x11223344, quad.vertices[2].color);
    assert_eq!(0.875, quad.vertices[2].ao);
    assert_eq!(0.125, quad.vertices[2].u);
    assert_eq!(0.625, quad.vertices[2].v);
    assert_eq!(0x00f000f0, quad.vertices[2].light);
}

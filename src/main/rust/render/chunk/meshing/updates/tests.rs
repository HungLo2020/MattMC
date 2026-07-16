use super::*;

fn quad() -> NativeQuad {
    NativeQuad {
        vertices: [QuadVertex {
            x: 0.0,
            y: 0.0,
            z: 0.0,
            color: 0xff804020u32 as i32,
            ao: 0.5,
            u: 0.0,
            v: 0.0,
            light: 0x00f000f0,
        }; 4],
        block_emission: 7,
        render_type: 1,
        ignore_mid_block: 0,
        _padding: 0,
        block_id: 41,
        local_x: 1,
        local_y: 2,
        local_z: 3,
        material_bits: 5,
    }
}

#[test]
fn native_quad_buffer_create_and_grow_returns_writable_memory() {
    unsafe {
        let mut handle = 0u64;
        let mut address = 0u64;
        assert_eq!(
            OK,
            mattmc_sodium_chunk_quad_buffer_create(1, &mut handle, &mut address)
        );
        assert_ne!(0, handle);
        assert_ne!(0, address);

        *(address as *mut NativeQuad) = quad();

        assert_eq!(
            OK,
            mattmc_sodium_chunk_quad_buffer_ensure_capacity(handle, 4, &mut address)
        );
        assert_ne!(0, address);
        *(address as *mut NativeQuad).add(3) = quad();

        assert_eq!(OK, mattmc_sodium_chunk_quad_buffer_destroy(handle));
    }
}

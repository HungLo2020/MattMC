//! Updated-quad collection and standalone native quad buffer exports.

use super::*;

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_updated_quads_create(output_handle: *mut u64) -> i32 {
    if output_handle.is_null() {
        return ERR_NULL_POINTER;
    }

    *output_handle = Box::into_raw(Box::new(NativeUpdatedQuads {
        quads: Vec::new(),
        mesh_quad_count: 0,
        index_quad_count: 0,
    })) as u64;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_updated_quads_destroy(handle: u64) -> i32 {
    if handle == 0 {
        return OK;
    }

    drop(Box::from_raw(handle as *mut NativeUpdatedQuads));
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_updated_quads_add(handle: u64, quad_handle: u64) -> i32 {
    if handle == 0 || quad_handle == 0 {
        return ERR_NULL_POINTER;
    }

    let updated_quads = &mut *(handle as *mut NativeUpdatedQuads);
    updated_quads.quads.push(quad_handle);
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_updated_quads_set_counts(
    handle: u64,
    mesh_quad_count: i32,
    index_quad_count: i32,
) -> i32 {
    if handle == 0 {
        return ERR_NULL_POINTER;
    }
    if mesh_quad_count < 0 || index_quad_count < 0 {
        return ERR_INVALID_ARGUMENT;
    }

    let updated_quads = &mut *(handle as *mut NativeUpdatedQuads);
    updated_quads.mesh_quad_count = mesh_quad_count;
    updated_quads.index_quad_count = index_quad_count;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_updated_quads_counts(
    handle: u64,
    output_counts: *mut i32,
    output_counts_len: i32,
) -> i32 {
    if handle == 0 || output_counts.is_null() {
        return ERR_NULL_POINTER;
    }
    if output_counts_len < 2 {
        return ERR_INVALID_ARGUMENT;
    }

    let updated_quads = &*(handle as *const NativeUpdatedQuads);
    *output_counts = updated_quads.mesh_quad_count;
    *output_counts.add(1) = updated_quads.index_quad_count;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_updated_quads_apply(
    handle: u64,
    output_address: u64,
    output_capacity: i32,
    quad_stride: i32,
    vertex_stride: i32,
    block_id_offset: i32,
    normal_offset: i32,
    tangent_offset: i32,
    mid_uv_offset: i32,
    mid_block_offset: i32,
    section_index: i32,
    separate_ao: i32,
    material_bits: i32,
) -> i32 {
    if handle == 0 {
        return ERR_NULL_POINTER;
    }
    let format = match NativeFormat::from_abi(
        quad_stride,
        vertex_stride,
        block_id_offset,
        normal_offset,
        tangent_offset,
        mid_uv_offset,
        mid_block_offset,
        section_index,
        separate_ao,
    ) {
        Ok(value) => value,
        Err(status) => return status,
    };

    let updated_quads = &*(handle as *const NativeUpdatedQuads);
    updated_quads_apply(
        updated_quads,
        output_address,
        output_capacity,
        format,
        material_bits,
    )
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_chunk_quad_buffer_create(
    capacity: i32,
    output_handle: *mut u64,
    output_address: *mut u64,
) -> i32 {
    if capacity < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if output_handle.is_null() || output_address.is_null() {
        return ERR_NULL_POINTER;
    }

    let capacity = match usize::try_from(capacity) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };
    let mut buffer = Box::new(NativeQuadBuffer {
        quads: vec![NativeQuad::default(); capacity],
        encoded: Vec::new(),
        encoded_format: None,
    });

    *output_address = if buffer.quads.is_empty() {
        0
    } else {
        buffer.quads.as_mut_ptr() as u64
    };
    *output_handle = Box::into_raw(buffer) as u64;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_chunk_quad_buffer_ensure_capacity(
    handle: u64,
    capacity: i32,
    output_address: *mut u64,
) -> i32 {
    if capacity < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if handle == 0 || output_address.is_null() {
        return ERR_NULL_POINTER;
    }

    let capacity = match usize::try_from(capacity) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };
    let buffer = &mut *(handle as *mut NativeQuadBuffer);
    if buffer.quads.len() < capacity {
        buffer.quads.resize(capacity, NativeQuad::default());
    }

    *output_address = if buffer.quads.is_empty() {
        0
    } else {
        buffer.quads.as_mut_ptr() as u64
    };
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_chunk_quad_buffer_destroy(handle: u64) -> i32 {
    if handle == 0 {
        return OK;
    }

    drop(Box::from_raw(handle as *mut NativeQuadBuffer));
    OK
}

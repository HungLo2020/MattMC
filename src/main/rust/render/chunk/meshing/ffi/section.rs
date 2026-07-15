//! Native section ingestion exports for static models, legacy records, and compact production snapshots.

use super::*;

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_append_static_model_batch_encoded(
    handle: u64,
    record_address: u64,
    record_count: i32,
    record_stride: i32,
    quad_stride: i32,
    vertex_stride: i32,
    block_id_offset: i32,
    normal_offset: i32,
    tangent_offset: i32,
    mid_uv_offset: i32,
    mid_block_offset: i32,
    section_index: i32,
    separate_ao: i32,
    store_raw_quads: i32,
    output_committed_count: *mut i32,
) -> i32 {
    if handle == 0 || output_committed_count.is_null() {
        return ERR_NULL_POINTER;
    }
    if record_count < 0 || record_stride < 0 {
        return ERR_INVALID_ARGUMENT;
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
    let record_stride = match usize::try_from(record_stride) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };

    let builder = &mut *(handle as *mut NativeSectionMeshBuilder);
    match section_builder_append_static_model_records_encoded(
        builder,
        record_address,
        record_count as usize,
        record_stride,
        format,
        store_raw_quads != 0,
    ) {
        Ok(committed_count) => {
            *output_committed_count = committed_count;
            OK
        }
        Err(status) => status,
    }
}

#[no_mangle]
/// Legacy 316-byte per-block record bridge retained for historical microbenchmarks and ABI tests.
/// Production section meshing enters Rust through the compact all-pass snapshot bridge below.
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_append_native_section_encoded(
    handle: u64,
    record_address: u64,
    record_count: i32,
    record_stride: i32,
    pass_id: i32,
    quad_stride: i32,
    vertex_stride: i32,
    block_id_offset: i32,
    normal_offset: i32,
    tangent_offset: i32,
    mid_uv_offset: i32,
    mid_block_offset: i32,
    section_index: i32,
    separate_ao: i32,
    store_raw_quads: i32,
    analyzer_handle: u64,
    output_committed_count: *mut i32,
) -> i32 {
    if handle == 0 || output_committed_count.is_null() {
        return ERR_NULL_POINTER;
    }
    if record_count < 0 || record_stride < 0 {
        return ERR_INVALID_ARGUMENT;
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
    let record_stride = match usize::try_from(record_stride) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };

    let builder = &mut *(handle as *mut NativeSectionMeshBuilder);
    match section_builder_append_native_section_records_encoded(
        builder,
        record_address,
        record_count as usize,
        record_stride,
        pass_id,
        if analyzer_handle == 0 {
            None
        } else {
            Some(analyzer_handle)
        },
        format,
        store_raw_quads != 0,
    ) {
        Ok(committed_count) => {
            *output_committed_count = committed_count;
            OK
        }
        Err(status) => status,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builders_append_compact_native_section_all_passes_encoded(
    solid_handle: u64,
    cutout_handle: u64,
    translucent_handle: u64,
    snapshot_address: u64,
    quad_stride: i32,
    vertex_stride: i32,
    block_id_offset: i32,
    normal_offset: i32,
    tangent_offset: i32,
    mid_uv_offset: i32,
    mid_block_offset: i32,
    section_index: i32,
    separate_ao: i32,
    translucent_analyzer_handle: u64,
    output_committed_counts: *mut i32,
) -> i32 {
    if solid_handle == 0
        || cutout_handle == 0
        || translucent_handle == 0
        || snapshot_address == 0
        || output_committed_counts.is_null()
    {
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

    let solid_builder = &mut *(solid_handle as *mut NativeSectionMeshBuilder);
    let cutout_builder = &mut *(cutout_handle as *mut NativeSectionMeshBuilder);
    let translucent_builder = &mut *(translucent_handle as *mut NativeSectionMeshBuilder);
    match section_builders_append_compact_native_section_all_passes_encoded(
        solid_builder,
        cutout_builder,
        translucent_builder,
        snapshot_address,
        if translucent_analyzer_handle == 0 {
            None
        } else {
            Some(translucent_analyzer_handle)
        },
        format,
    ) {
        Ok(committed_counts) => {
            let output = slice::from_raw_parts_mut(output_committed_counts, 3);
            output.copy_from_slice(&committed_counts);
            OK
        }
        Err(status) => status,
    }
}

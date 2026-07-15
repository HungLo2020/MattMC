//! C ABI wrappers for translucent analyzer, sorter, and BSP handles.
//!
//! Keep these functions thin: validate pointer/handle arguments, create safe
//! slices or owned handles, call the analyzer/topology/BSP routines, then return
//! the stable Java-facing result code.
//!
//! # Safety
//!
//! These exports are entered from Java through raw C ABI calls. Java must pass
//! handles created by the matching native constructor, destroy each handle at
//! most once, keep input buffers alive for the duration of the call, and ensure
//! output buffers are writable for the supplied element count. The wrappers
//! reject null pointers, negative counts, invalid enum-like arguments, and
//! capacity mismatches before constructing Rust slices, but non-null foreign
//! pointers and nonzero handles are trusted to still refer to live allocations.

use super::*;

#[no_mangle]
pub extern "C" fn mattmc_sodium_translucent_analyzer_verify() -> i32 {
    verify()
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_analyzer_create(output_handle: *mut u64) -> i32 {
    if output_handle.is_null() {
        return ERR_NULL_POINTER;
    }

    *output_handle = Box::into_raw(Box::new(NativeTranslucentAnalyzer {
        records: Vec::new(),
    })) as u64;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_analyzer_destroy(handle: u64) -> i32 {
    if handle == 0 {
        return OK;
    }

    drop(Box::from_raw(handle as *mut NativeTranslucentAnalyzer));
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_analyzer_append_record(
    handle: u64,
    record: *const TranslucentQuadRecord,
) -> i32 {
    if handle == 0 || record.is_null() {
        return ERR_NULL_POINTER;
    }

    let analyzer = &mut *(handle as *mut NativeTranslucentAnalyzer);
    let record = *record;
    if record_is_invalid(&record) {
        return SORT_FAILED;
    }

    analyzer.records.push(record);
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_analyzer_append_native_quad(
    handle: u64,
    native_quad_address: u64,
    facing: i32,
    packed_normal: i32,
) -> i32 {
    if handle == 0 || native_quad_address == 0 {
        return ERR_NULL_POINTER;
    }
    if !(0..FACING_COUNT as i32).contains(&facing) {
        return ERR_INVALID_ARGUMENT;
    }

    let analyzer = &mut *(handle as *mut NativeTranslucentAnalyzer);
    let record = record_from_native_quad(native_quad_address, facing, packed_normal);
    if record_is_invalid(&record) {
        return SORT_FAILED;
    }

    analyzer.records.push(record);
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_analyzer_append_native_quad_batch(
    handle: u64,
    native_quad_address: u64,
    quad_count: i32,
    facing: i32,
    packed_normals: *const i32,
    validity_output_address: u64,
    output_valid_count: *mut i32,
) -> i32 {
    append_native_quad_batch_to_analyzer(
        handle,
        native_quad_address,
        quad_count,
        facing,
        packed_normals,
        validity_output_address,
        output_valid_count,
    )
}

pub unsafe fn append_native_quad_batch_to_analyzer(
    handle: u64,
    native_quad_address: u64,
    quad_count: i32,
    facing: i32,
    packed_normals: *const i32,
    validity_output_address: u64,
    output_valid_count: *mut i32,
) -> i32 {
    if handle == 0 || output_valid_count.is_null() {
        return ERR_NULL_POINTER;
    }
    if quad_count < 0 || !(0..FACING_COUNT as i32).contains(&facing) {
        return ERR_INVALID_ARGUMENT;
    }
    if quad_count == 0 {
        *output_valid_count = 0;
        return OK;
    }
    if native_quad_address == 0 || packed_normals.is_null() || validity_output_address == 0 {
        return ERR_NULL_POINTER;
    }

    let quad_count = quad_count as usize;
    let analyzer = &mut *(handle as *mut NativeTranslucentAnalyzer);
    let packed_normals = slice::from_raw_parts(packed_normals, quad_count);
    let validity_output = slice::from_raw_parts_mut(validity_output_address as *mut u8, quad_count);
    let mut valid_count = 0i32;

    for index in 0..quad_count {
        let quad_address = native_quad_address + (index as u64 * NATIVE_QUAD_STRIDE);
        let record = record_from_native_quad(quad_address, facing, packed_normals[index]);

        if record_is_invalid(&record) {
            validity_output[index] = 0;
            continue;
        }

        validity_output[index] = 1;
        analyzer.records.push(record);
        valid_count += 1;
    }

    *output_valid_count = valid_count;
    OK
}

pub unsafe fn append_quad_positions_to_analyzer(
    handle: u64,
    positions: [f32; 12],
    facing: i32,
    packed_normal: i32,
) -> Result<bool, i32> {
    if handle == 0 {
        return Err(ERR_NULL_POINTER);
    }
    if !(0..FACING_COUNT as i32).contains(&facing) {
        return Err(ERR_INVALID_ARGUMENT);
    }

    let record = TranslucentQuadRecord {
        positions,
        facing,
        packed_normal,
    };
    if record_is_invalid(&record) {
        return Ok(false);
    }

    let analyzer = &mut *(handle as *mut NativeTranslucentAnalyzer);
    analyzer.records.push(record);
    Ok(true)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_analyzer_record_count(
    handle: u64,
    output_count: *mut i32,
) -> i32 {
    if handle == 0 || output_count.is_null() {
        return ERR_NULL_POINTER;
    }

    let analyzer = &*(handle as *const NativeTranslucentAnalyzer);
    *output_count = analyzer.records.len() as i32;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_analyzer_write_records_by_facing(
    handle: u64,
    output_records: *mut TranslucentQuadRecord,
    output_records_len: i32,
) -> i32 {
    if handle == 0 {
        return ERR_NULL_POINTER;
    }
    if output_records_len < 0 {
        return ERR_INVALID_ARGUMENT;
    }

    let analyzer = &*(handle as *const NativeTranslucentAnalyzer);
    if output_records_len as usize != analyzer.records.len() {
        return ERR_CAPACITY;
    }
    if analyzer.records.is_empty() {
        return OK;
    }
    if !analyzer.records.is_empty() && output_records.is_null() {
        return ERR_NULL_POINTER;
    }

    let output_records = slice::from_raw_parts_mut(output_records, output_records_len as usize);
    let mut output_index = 0usize;
    for facing in 0..FACING_COUNT as i32 {
        for record in &analyzer.records {
            if record.facing == facing {
                output_records[output_index] = *record;
                output_index += 1;
            }
        }
    }

    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_analyzer_analyze(
    records: *const TranslucentQuadRecord,
    record_count: i32,
    sort_mode: i32,
    metrics: *mut i32,
    metrics_len: i32,
    mesh_facing_counts: *mut i32,
    mesh_facing_counts_len: i32,
    static_keys: *mut i32,
    static_keys_len: i32,
) -> i32 {
    if record_count < 0
        || metrics_len < 5
        || mesh_facing_counts_len != FACING_COUNT as i32
        || static_keys_len < 0
    {
        return ERR_INVALID_ARGUMENT;
    }
    if metrics.is_null() || mesh_facing_counts.is_null() {
        return ERR_NULL_POINTER;
    }
    if record_count > 0 && records.is_null() {
        return ERR_NULL_POINTER;
    }

    let records = if record_count == 0 {
        &[]
    } else {
        slice::from_raw_parts(records, record_count as usize)
    };
    let analyzer = match analyze(records, sort_mode) {
        Ok(value) => value,
        Err(status) => return status,
    };
    if analyzer.static_keys.len() > static_keys_len as usize {
        return ERR_CAPACITY;
    }
    if !analyzer.static_keys.is_empty() && static_keys.is_null() {
        return ERR_NULL_POINTER;
    }

    let metrics = slice::from_raw_parts_mut(metrics, metrics_len as usize);
    metrics[0] = analyzer.sort_type;
    metrics[1] = analyzer.quad_hash;
    metrics[2] = analyzer.aligned_facing_bitmap;
    metrics[3] = if analyzer.is_double_unaligned { 1 } else { 0 };
    metrics[4] = analyzer.static_keys.len() as i32;

    let mesh_facing_counts =
        slice::from_raw_parts_mut(mesh_facing_counts, mesh_facing_counts_len as usize);
    mesh_facing_counts.copy_from_slice(&analyzer.mesh_facing_counts);

    if !analyzer.static_keys.is_empty() {
        let static_keys = slice::from_raw_parts_mut(static_keys, static_keys_len as usize);
        static_keys[..analyzer.static_keys.len()].copy_from_slice(&analyzer.static_keys);
    }

    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_analyzer_analyze_handle(
    handle: u64,
    sort_mode: i32,
    metrics: *mut i32,
    metrics_len: i32,
    mesh_facing_counts: *mut i32,
    mesh_facing_counts_len: i32,
    static_keys: *mut i32,
    static_keys_len: i32,
) -> i32 {
    if handle == 0 {
        return ERR_NULL_POINTER;
    }

    let analyzer = &*(handle as *const NativeTranslucentAnalyzer);
    mattmc_sodium_translucent_analyzer_analyze(
        analyzer.records.as_ptr(),
        analyzer.records.len() as i32,
        sort_mode,
        metrics,
        metrics_len,
        mesh_facing_counts,
        mesh_facing_counts_len,
        static_keys,
        static_keys_len,
    )
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_static_topo_sort(
    records: *const TranslucentQuadRecord,
    record_count: i32,
    fail_on_intersection: i32,
    output_indices: *mut i32,
    output_indices_len: i32,
) -> i32 {
    if record_count < 0 || output_indices_len < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if record_count > 0 && (records.is_null() || output_indices.is_null()) {
        return ERR_NULL_POINTER;
    }
    if output_indices_len < record_count {
        return ERR_CAPACITY;
    }

    let records = if record_count == 0 {
        &[]
    } else {
        slice::from_raw_parts(records, record_count as usize)
    };
    let order = match static_topo_sort(records, fail_on_intersection != 0) {
        Ok(Some(value)) => value,
        Ok(None) => return SORT_FAILED,
        Err(status) => return status,
    };

    if !order.is_empty() {
        let output_indices = slice::from_raw_parts_mut(output_indices, output_indices_len as usize);
        output_indices[..order.len()].copy_from_slice(&order);
    }

    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_static_topo_sort_handle(
    handle: u64,
    fail_on_intersection: i32,
    output_indices: *mut i32,
    output_indices_len: i32,
) -> i32 {
    if handle == 0 {
        return ERR_NULL_POINTER;
    }

    let analyzer = &*(handle as *const NativeTranslucentAnalyzer);
    mattmc_sodium_translucent_static_topo_sort(
        analyzer.records.as_ptr(),
        analyzer.records.len() as i32,
        fail_on_intersection,
        output_indices,
        output_indices_len,
    )
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_topo_graph_sort_records(
    records: *const TranslucentTopoQuadRecord,
    record_count: i32,
    active_to_real_index: *const i32,
    active_to_real_index_len: i32,
    fail_on_intersection: i32,
    output_indices: *mut i32,
    output_indices_len: i32,
) -> i32 {
    if record_count < 0 || active_to_real_index_len < 0 || output_indices_len < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if record_count > 0 && (records.is_null() || output_indices.is_null()) {
        return ERR_NULL_POINTER;
    }
    if output_indices_len < record_count {
        return ERR_CAPACITY;
    }

    let records = if record_count == 0 {
        &[]
    } else {
        slice::from_raw_parts(records, record_count as usize)
    };
    let active_to_real_index = if active_to_real_index.is_null() {
        None
    } else {
        if active_to_real_index_len < record_count {
            return ERR_CAPACITY;
        }
        Some(slice::from_raw_parts(
            active_to_real_index,
            active_to_real_index_len as usize,
        ))
    };
    let order = match topo_graph_sort_topo_records(
        records,
        active_to_real_index,
        fail_on_intersection != 0,
    ) {
        Ok(Some(value)) => value,
        Ok(None) => return SORT_FAILED,
        Err(status) => return status,
    };

    if !order.is_empty() {
        let output_indices = slice::from_raw_parts_mut(output_indices, output_indices_len as usize);
        output_indices[..order.len()].copy_from_slice(&order);
    }

    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_topo_quad_store_create(
    records: *const TranslucentTopoQuadRecord,
    record_count: i32,
    output_handle: *mut u64,
) -> i32 {
    if record_count < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if output_handle.is_null() || (record_count > 0 && records.is_null()) {
        return ERR_NULL_POINTER;
    }

    let records = if record_count == 0 {
        &[]
    } else {
        slice::from_raw_parts(records, record_count as usize)
    };
    let store = match create_topo_quad_store(records) {
        Ok(value) => value,
        Err(status) => return status,
    };

    *output_handle = Box::into_raw(Box::new(store)) as u64;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_topo_quad_store_destroy(handle: u64) -> i32 {
    if handle == 0 {
        return ERR_NULL_POINTER;
    }

    drop(Box::from_raw(handle as *mut NativeTopoQuadStore));
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_topo_quad_store_set(
    handle: u64,
    index: i32,
    record: *const TranslucentTopoQuadRecord,
) -> i32 {
    if handle == 0 || record.is_null() {
        return ERR_NULL_POINTER;
    }

    let store = &mut *(handle as *mut NativeTopoQuadStore);
    topo_quad_store_set(store, index, &*record)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_topo_quad_store_remove(
    handle: u64,
    index: i32,
) -> i32 {
    if handle == 0 {
        return ERR_NULL_POINTER;
    }

    let store = &mut *(handle as *mut NativeTopoQuadStore);
    topo_quad_store_remove(store, index)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_topo_quad_store_bsp_double_leaf_possible(
    handle: u64,
    quad_a_index: i32,
    quad_b_index: i32,
    fail_on_intersection: i32,
    output_result: *mut i32,
) -> i32 {
    if handle == 0 || output_result.is_null() {
        return ERR_NULL_POINTER;
    }
    if quad_a_index < 0 || quad_b_index < 0 {
        return ERR_INVALID_ARGUMENT;
    }

    let store = &*(handle as *const NativeTopoQuadStore);
    let Some(Some(quad_a)) = store.quads.get(quad_a_index as usize) else {
        return ERR_INVALID_ARGUMENT;
    };
    let Some(Some(quad_b)) = store.quads.get(quad_b_index as usize) else {
        return ERR_INVALID_ARGUMENT;
    };

    *output_result = if bsp_double_leaf_possible(quad_a, quad_b, fail_on_intersection != 0) {
        1
    } else {
        0
    };
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_full_quad_create(
    native_quad_address: u64,
    facing: i32,
    packed_normal: i32,
    output_handle: *mut u64,
    output_state: *mut NativeFullQuadState,
) -> i32 {
    if native_quad_address == 0 || output_handle.is_null() || output_state.is_null() {
        return ERR_NULL_POINTER;
    }

    let source = &*(native_quad_address as *const NativeFullQuadBuffer);
    let quad = match create_full_quad(source, facing, packed_normal) {
        Ok(value) => value,
        Err(status) => return status,
    };
    let boxed = Box::new(quad);
    let handle = Box::into_raw(boxed) as u64;
    let status = write_full_quad_state(&*(handle as *const NativeFullTQuad), output_state);
    if status != OK {
        drop(Box::from_raw(handle as *mut NativeFullTQuad));
        return status;
    }

    *output_handle = handle;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_full_quad_destroy(handle: u64) -> i32 {
    if handle == 0 {
        return ERR_NULL_POINTER;
    }

    drop(Box::from_raw(handle as *mut NativeFullTQuad));
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_full_quad_copy(
    handle: u64,
    output_handle: *mut u64,
    output_state: *mut NativeFullQuadState,
) -> i32 {
    if handle == 0 || output_handle.is_null() || output_state.is_null() {
        return ERR_NULL_POINTER;
    }

    let source = &*(handle as *const NativeFullTQuad);
    let copied = Box::new(source.clone());
    let new_handle = Box::into_raw(copied) as u64;
    let status = write_full_quad_state(&*(new_handle as *const NativeFullTQuad), output_state);
    if status != OK {
        drop(Box::from_raw(new_handle as *mut NativeFullTQuad));
        return status;
    }

    *output_handle = new_handle;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_full_quad_write_state(
    handle: u64,
    output_state: *mut NativeFullQuadState,
) -> i32 {
    if handle == 0 {
        return ERR_NULL_POINTER;
    }

    write_full_quad_state(&*(handle as *const NativeFullTQuad), output_state)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_full_quad_get_very_accurate_normal(
    handle: u64,
    output_state: *mut NativeFullQuadState,
) -> i32 {
    if handle == 0 {
        return ERR_NULL_POINTER;
    }

    let quad = &mut *(handle as *mut NativeFullTQuad);
    full_quad_very_accurate_normal(quad);
    write_full_quad_state(quad, output_state)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_full_quad_classify(
    handle: u64,
    normal_x: f32,
    normal_y: f32,
    normal_z: f32,
    distance: f32,
    output_maps: *mut i32,
) -> i32 {
    if handle == 0 || output_maps.is_null() {
        return ERR_NULL_POINTER;
    }

    let quad = &*(handle as *const NativeFullTQuad);
    let (inside_map, on_plane_map) =
        full_quad_classify(quad, (normal_x, normal_y, normal_z), distance);
    *output_maps.add(0) = inside_map;
    *output_maps.add(1) = on_plane_map;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_full_quad_trigger_update(handle: u64) -> i32 {
    if handle == 0 {
        return ERR_NULL_POINTER;
    }

    let quad = &mut *(handle as *mut NativeFullTQuad);
    if quad.has_updated_vertices {
        0
    } else {
        quad.has_updated_vertices = true;
        1
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_full_quad_set_write_to_index(
    handle: u64,
    write_to_index: i32,
) -> i32 {
    if handle == 0 {
        return ERR_NULL_POINTER;
    }

    let quad = &mut *(handle as *mut NativeFullTQuad);
    quad.write_to_index = write_to_index;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_full_quad_write_to_native_buffer(
    handle: u64,
    output_native_quad_address: u64,
    material_bits: i32,
) -> i32 {
    native_full_quad_write_to_native_buffer(handle, output_native_quad_address, material_bits)
}

pub(crate) unsafe fn native_full_quad_write_to_index(
    handle: u64,
    output_write_to_index: *mut i32,
) -> i32 {
    if handle == 0 || output_write_to_index.is_null() {
        return ERR_NULL_POINTER;
    }

    let quad = &*(handle as *const NativeFullTQuad);
    *output_write_to_index = quad.write_to_index;
    OK
}

pub(crate) unsafe fn native_full_quad_write_to_native_buffer(
    handle: u64,
    output_native_quad_address: u64,
    _material_bits: i32,
) -> i32 {
    if handle == 0 || output_native_quad_address == 0 {
        return ERR_NULL_POINTER;
    }

    let quad = &*(handle as *const NativeFullTQuad);
    let output = &mut *(output_native_quad_address as *mut NativeFullQuadBuffer);
    *output = quad.quad;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_full_quad_split_even(
    vertex_inside_map: i32,
    inside_handle: u64,
    outside_handle: u64,
    normal_x: f32,
    normal_y: f32,
    normal_z: f32,
    distance: f32,
    inside_state: *mut NativeFullQuadState,
    outside_state: *mut NativeFullQuadState,
) -> i32 {
    if inside_handle == 0 || outside_handle == 0 {
        return ERR_NULL_POINTER;
    }
    if inside_handle == outside_handle {
        return ERR_INVALID_ARGUMENT;
    }

    let inside = &mut *(inside_handle as *mut NativeFullTQuad);
    let outside = &mut *(outside_handle as *mut NativeFullTQuad);
    if let Err(status) = full_quad_split_even(
        vertex_inside_map,
        inside,
        outside,
        (normal_x, normal_y, normal_z),
        distance,
    ) {
        return status;
    }

    let status = write_full_quad_state(inside, inside_state);
    if status != OK {
        return status;
    }
    write_full_quad_state(outside, outside_state)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_full_quad_split_odd(
    corner_index: i32,
    corner_handle: u64,
    cut_handle: u64,
    bulk_handle: u64,
    normal_x: f32,
    normal_y: f32,
    normal_z: f32,
    distance: f32,
    corner_state: *mut NativeFullQuadState,
    cut_state: *mut NativeFullQuadState,
    bulk_state: *mut NativeFullQuadState,
) -> i32 {
    if corner_handle == 0 || cut_handle == 0 || bulk_handle == 0 {
        return ERR_NULL_POINTER;
    }
    if corner_handle == cut_handle || corner_handle == bulk_handle || cut_handle == bulk_handle {
        return ERR_INVALID_ARGUMENT;
    }

    let corner = &mut *(corner_handle as *mut NativeFullTQuad);
    let cut = &mut *(cut_handle as *mut NativeFullTQuad);
    let bulk = &mut *(bulk_handle as *mut NativeFullTQuad);
    if let Err(status) = full_quad_split_odd(
        corner_index as usize,
        corner,
        cut,
        bulk,
        (normal_x, normal_y, normal_z),
        distance,
    ) {
        return status;
    }

    let status = write_full_quad_state(corner, corner_state);
    if status != OK {
        return status;
    }
    let status = write_full_quad_state(cut, cut_state);
    if status != OK {
        return status;
    }
    write_full_quad_state(bulk, bulk_state)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_full_quad_split_triangle_corner(
    corner_index: i32,
    corner_handle: u64,
    bulk_handle: u64,
    normal_x: f32,
    normal_y: f32,
    normal_z: f32,
    distance: f32,
    corner_state: *mut NativeFullQuadState,
    bulk_state: *mut NativeFullQuadState,
) -> i32 {
    if corner_handle == 0 || bulk_handle == 0 {
        return ERR_NULL_POINTER;
    }
    if corner_handle == bulk_handle {
        return ERR_INVALID_ARGUMENT;
    }

    let corner = &mut *(corner_handle as *mut NativeFullTQuad);
    let bulk = &mut *(bulk_handle as *mut NativeFullTQuad);
    if let Err(status) = full_quad_split_triangle_corner(
        corner_index as usize,
        corner,
        bulk,
        (normal_x, normal_y, normal_z),
        distance,
    ) {
        return status;
    }

    let status = write_full_quad_state(corner, corner_state);
    if status != OK {
        return status;
    }
    write_full_quad_state(bulk, bulk_state)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_full_quad_split_triangle_vertex(
    inside_index: i32,
    outside_index: i32,
    duplicate_index: i32,
    duplicate_is_inside: i32,
    inside_handle: u64,
    outside_handle: u64,
    normal_x: f32,
    normal_y: f32,
    normal_z: f32,
    distance: f32,
    inside_state: *mut NativeFullQuadState,
    outside_state: *mut NativeFullQuadState,
) -> i32 {
    if inside_handle == 0 || outside_handle == 0 {
        return ERR_NULL_POINTER;
    }
    if inside_handle == outside_handle {
        return ERR_INVALID_ARGUMENT;
    }

    let inside = &mut *(inside_handle as *mut NativeFullTQuad);
    let outside = &mut *(outside_handle as *mut NativeFullTQuad);
    if let Err(status) = full_quad_split_triangle_vertex(
        inside_index as usize,
        outside_index as usize,
        duplicate_index,
        duplicate_is_inside != 0,
        inside,
        outside,
        (normal_x, normal_y, normal_z),
        distance,
    ) {
        return status;
    }

    let status = write_full_quad_state(inside, inside_state);
    if status != OK {
        return status;
    }
    write_full_quad_state(outside, outside_state)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_geometry_planes_create_from_records(
    records: *const TranslucentQuadRecord,
    record_count: i32,
    output_handle: *mut u64,
) -> i32 {
    if record_count < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if output_handle.is_null() || (record_count > 0 && records.is_null()) {
        return ERR_NULL_POINTER;
    }

    let records = if record_count == 0 {
        &[]
    } else {
        slice::from_raw_parts(records, record_count as usize)
    };
    let collector = match create_geometry_planes_from_records(records) {
        Ok(value) => value,
        Err(status) => return status,
    };

    *output_handle = Box::into_raw(Box::new(collector)) as u64;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_geometry_planes_create_from_analyzer(
    analyzer_handle: u64,
    output_handle: *mut u64,
) -> i32 {
    if analyzer_handle == 0 {
        return ERR_NULL_POINTER;
    }

    let analyzer = &*(analyzer_handle as *const NativeTranslucentAnalyzer);
    mattmc_sodium_geometry_planes_create_from_records(
        analyzer.records.as_ptr(),
        analyzer.records.len() as i32,
        output_handle,
    )
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_sort_data_static_topo_create(
    records: *const TranslucentQuadRecord,
    record_count: i32,
    fail_on_intersection: i32,
    output_handle: *mut u64,
) -> i32 {
    if record_count < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if output_handle.is_null() || (record_count > 0 && records.is_null()) {
        return ERR_NULL_POINTER;
    }

    let records = if record_count == 0 {
        &[]
    } else {
        slice::from_raw_parts(records, record_count as usize)
    };
    let sort_data = match create_static_topo_sort_data(records, fail_on_intersection != 0) {
        Ok(Some(value)) => value,
        Ok(None) => {
            *output_handle = 0;
            return SORT_FAILED;
        }
        Err(status) => return status,
    };

    *output_handle = Box::into_raw(Box::new(sort_data)) as u64;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_sort_data_static_topo_create_from_analyzer(
    analyzer_handle: u64,
    fail_on_intersection: i32,
    output_handle: *mut u64,
) -> i32 {
    if analyzer_handle == 0 {
        return ERR_NULL_POINTER;
    }

    let analyzer = &*(analyzer_handle as *const NativeTranslucentAnalyzer);
    mattmc_sodium_translucent_sort_data_static_topo_create(
        analyzer.records.as_ptr(),
        analyzer.records.len() as i32,
        fail_on_intersection,
        output_handle,
    )
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_sort_data_static_order_create(
    quad_count: i32,
    quad_indexes: *const i32,
    quad_index_count: i32,
    output_handle: *mut u64,
) -> i32 {
    if quad_count < 0 || quad_index_count < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if output_handle.is_null() || (quad_index_count > 0 && quad_indexes.is_null()) {
        return ERR_NULL_POINTER;
    }
    if quad_index_count > quad_count {
        return ERR_INVALID_ARGUMENT;
    }

    let quad_indexes = if quad_index_count == 0 {
        &[]
    } else {
        slice::from_raw_parts(quad_indexes, quad_index_count as usize)
    };
    let sort_data = match create_static_order_sort_data(quad_count as usize, quad_indexes) {
        Ok(value) => value,
        Err(status) => return status,
    };

    *output_handle = Box::into_raw(Box::new(sort_data)) as u64;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_sort_data_static_snr_create(
    mesh_facing_counts: *const i32,
    mesh_facing_count_len: i32,
    sort_keys: *const i32,
    sort_key_len: i32,
    quad_count: i32,
    is_double_unaligned: i32,
    output_handle: *mut u64,
) -> i32 {
    if mesh_facing_count_len < 0 || sort_key_len < 0 || quad_count < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if output_handle.is_null()
        || mesh_facing_counts.is_null()
        || (sort_key_len > 0 && sort_keys.is_null())
    {
        return ERR_NULL_POINTER;
    }

    let mesh_facing_counts =
        slice::from_raw_parts(mesh_facing_counts, mesh_facing_count_len as usize);
    let sort_keys = if sort_key_len == 0 {
        &[]
    } else {
        slice::from_raw_parts(sort_keys, sort_key_len as usize)
    };
    let sort_data = match create_static_normal_relative_sort_data(
        mesh_facing_counts,
        sort_keys,
        quad_count as usize,
        is_double_unaligned != 0,
    ) {
        Ok(value) => value,
        Err(status) => return status,
    };

    *output_handle = Box::into_raw(Box::new(sort_data)) as u64;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_sort_data_dynamic_topo_create(
    records: *const TranslucentQuadRecord,
    record_count: i32,
    output_handle: *mut u64,
) -> i32 {
    if record_count < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if output_handle.is_null() || (record_count > 0 && records.is_null()) {
        return ERR_NULL_POINTER;
    }

    let records = if record_count == 0 {
        &[]
    } else {
        slice::from_raw_parts(records, record_count as usize)
    };
    let sort_data = match create_dynamic_topo_sort_data(records) {
        Ok(value) => value,
        Err(status) => return status,
    };

    *output_handle = Box::into_raw(Box::new(sort_data)) as u64;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_sort_data_dynamic_topo_create_from_analyzer(
    analyzer_handle: u64,
    output_handle: *mut u64,
) -> i32 {
    if analyzer_handle == 0 {
        return ERR_NULL_POINTER;
    }

    let analyzer = &*(analyzer_handle as *const NativeTranslucentAnalyzer);
    mattmc_sodium_translucent_sort_data_dynamic_topo_create(
        analyzer.records.as_ptr(),
        analyzer.records.len() as i32,
        output_handle,
    )
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_sort_data_destroy(handle: u64) -> i32 {
    if handle == 0 {
        return OK;
    }

    drop(Box::from_raw(handle as *mut NativeTranslucentSortData));
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_sort_data_static_write(
    handle: u64,
    output_address: u64,
    output_capacity: i32,
) -> i32 {
    if output_capacity < 0 || output_capacity % std::mem::size_of::<i32>() as i32 != 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if handle == 0 || output_address == 0 {
        return ERR_NULL_POINTER;
    }

    let sort_data = &*(handle as *const NativeTranslucentSortData);
    let output = slice::from_raw_parts_mut(
        output_address as *mut i32,
        output_capacity as usize / std::mem::size_of::<i32>(),
    );
    write_static_sort_data(sort_data, output)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_sort_data_dynamic_write(
    handle: u64,
    output_address: u64,
    output_capacity: i32,
    camera_x: f32,
    camera_y: f32,
    camera_z: f32,
    initial: i32,
    is_direct_trigger: i32,
    gfni_trigger: i32,
    direct_trigger: i32,
    consecutive_topo_sort_failures: i32,
    output_state: *mut i32,
    output_state_len: i32,
) -> i32 {
    if output_capacity < 0 || output_capacity % std::mem::size_of::<i32>() as i32 != 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if output_state_len < 3 || consecutive_topo_sort_failures < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if handle == 0 || output_address == 0 || output_state.is_null() {
        return ERR_NULL_POINTER;
    }

    let sort_data = &*(handle as *const NativeTranslucentSortData);
    let output = slice::from_raw_parts_mut(
        output_address as *mut i32,
        output_capacity as usize / std::mem::size_of::<i32>(),
    );
    let state = DynamicSortState {
        gfni_trigger: gfni_trigger != 0,
        direct_trigger: direct_trigger != 0,
        consecutive_topo_sort_failures,
    };
    let state = match write_dynamic_sort_data(
        sort_data,
        output,
        camera_x,
        camera_y,
        camera_z,
        initial != 0,
        is_direct_trigger != 0,
        state,
    ) {
        Ok(value) => value,
        Err(status) => return status,
    };

    let output_state = slice::from_raw_parts_mut(output_state, output_state_len as usize);
    output_state[0] = if state.gfni_trigger { 1 } else { 0 };
    output_state[1] = if state.direct_trigger { 1 } else { 0 };
    output_state[2] = state.consecutive_topo_sort_failures;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_bsp_build_result_create(
    output_handle: *mut u64,
) -> i32 {
    if output_handle.is_null() {
        return ERR_NULL_POINTER;
    }

    *output_handle = Box::into_raw(Box::new(create_bsp_build_result())) as u64;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_bsp_build_result_destroy(handle: u64) -> i32 {
    if handle == 0 {
        return OK;
    }

    drop(Box::from_raw(handle as *mut NativeBspBuildResult));
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_bsp_build_result_add_aligned_plane(
    handle: u64,
    axis: i32,
    distance: f32,
) -> i32 {
    if handle == 0 {
        return ERR_NULL_POINTER;
    }

    let result = &mut *(handle as *mut NativeBspBuildResult);
    let Some(geometry_planes) = result.geometry_planes.as_mut() else {
        return ERR_INVALID_ARGUMENT;
    };

    match geometry_planes.add_double_sided_aligned_plane(axis, distance) {
        Ok(()) => OK,
        Err(status) => status,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_bsp_build_result_add_unaligned_plane(
    handle: u64,
    normal_x: f32,
    normal_y: f32,
    normal_z: f32,
    distance: f32,
) -> i32 {
    if handle == 0 {
        return ERR_NULL_POINTER;
    }

    let result = &mut *(handle as *mut NativeBspBuildResult);
    let Some(geometry_planes) = result.geometry_planes.as_mut() else {
        return ERR_INVALID_ARGUMENT;
    };

    match geometry_planes.add_double_sided_unaligned_plane([normal_x, normal_y, normal_z], distance)
    {
        Ok(()) => OK,
        Err(status) => status,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_bsp_build_result_take_geometry_planes(
    handle: u64,
    output_geometry_planes_handle: *mut u64,
) -> i32 {
    if handle == 0 || output_geometry_planes_handle.is_null() {
        return ERR_NULL_POINTER;
    }

    let result = &mut *(handle as *mut NativeBspBuildResult);
    let Some(geometry_planes) = result.geometry_planes.take() else {
        return ERR_INVALID_ARGUMENT;
    };

    *output_geometry_planes_handle = Box::into_raw(geometry_planes) as u64;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_bsp_build_result_set_tree(
    handle: u64,
    tree_handle: u64,
) -> i32 {
    if handle == 0 || tree_handle == 0 {
        return ERR_NULL_POINTER;
    }

    let result = &mut *(handle as *mut NativeBspBuildResult);
    if result.tree.is_some() {
        return ERR_INVALID_ARGUMENT;
    }

    result.tree = Some(Box::from_raw(tree_handle as *mut NativeBspTree));
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_bsp_build_result_write_index_buffer(
    handle: u64,
    output_address: u64,
    output_capacity: i32,
    camera_x: f32,
    camera_y: f32,
    camera_z: f32,
) -> i32 {
    if output_capacity < 0 || output_capacity % std::mem::size_of::<i32>() as i32 != 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if handle == 0 || output_address == 0 {
        return ERR_NULL_POINTER;
    }

    let result = &*(handle as *const NativeBspBuildResult);
    let Some(tree) = result.tree.as_ref() else {
        return ERR_INVALID_ARGUMENT;
    };
    let output = slice::from_raw_parts_mut(
        output_address as *mut i32,
        output_capacity as usize / std::mem::size_of::<i32>(),
    );
    write_bsp_tree_index_buffer(tree, output, camera_x, camera_y, camera_z)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_bsp_build_records(
    records: *const TranslucentTopoQuadRecord,
    record_count: i32,
    result_handle: u64,
    old_root_handle: u64,
    prepare_node_reuse: i32,
    output_tree_handle: *mut u64,
    output_index_quad_count: *mut i32,
    output_reusable_root_handle: *mut u64,
) -> i32 {
    if record_count < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if result_handle == 0
        || output_tree_handle.is_null()
        || output_index_quad_count.is_null()
        || output_reusable_root_handle.is_null()
    {
        return ERR_NULL_POINTER;
    }
    if record_count > 0 && records.is_null() {
        return ERR_NULL_POINTER;
    }

    let records = if record_count == 0 {
        &[]
    } else {
        slice::from_raw_parts(records, record_count as usize)
    };
    let result = &mut *(result_handle as *mut NativeBspBuildResult);
    let Some(geometry_planes) = result.geometry_planes.as_mut() else {
        return ERR_INVALID_ARGUMENT;
    };

    let output = match build_bsp_tree_from_topo_records_with_reuse(
        records,
        geometry_planes,
        old_root_handle,
        prepare_node_reuse != 0,
    ) {
        Ok(value) => value,
        Err(status) => return status,
    };

    *output_index_quad_count = output.tree.index_quad_count as i32;
    *output_reusable_root_handle = output
        .reusable_root
        .map(|root| Box::into_raw(root) as u64)
        .unwrap_or(0);
    *output_tree_handle = Box::into_raw(Box::new(output.tree)) as u64;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_bsp_build_full_quads(
    quad_handles: *const u64,
    quad_count: i32,
    result_handle: u64,
    max_quad_count: i32,
    quantize_trigger_normals: i32,
    old_root_handle: u64,
    prepare_node_reuse: i32,
    output_tree_handle: *mut u64,
    output_index_quad_count: *mut i32,
    output_updated_quads_handle: *mut u64,
    output_reusable_root_handle: *mut u64,
) -> i32 {
    if quad_count < 0 || max_quad_count < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if result_handle == 0
        || output_tree_handle.is_null()
        || output_index_quad_count.is_null()
        || output_updated_quads_handle.is_null()
        || output_reusable_root_handle.is_null()
    {
        return ERR_NULL_POINTER;
    }
    if quad_count > 0 && quad_handles.is_null() {
        return ERR_NULL_POINTER;
    }

    let handles = if quad_count == 0 {
        &[]
    } else {
        slice::from_raw_parts(quad_handles, quad_count as usize)
    };
    let result = &mut *(result_handle as *mut NativeBspBuildResult);
    let Some(geometry_planes) = result.geometry_planes.as_mut() else {
        return ERR_INVALID_ARGUMENT;
    };

    let output = match build_bsp_tree_from_full_quad_handles_with_reuse(
        handles,
        geometry_planes,
        max_quad_count as usize,
        quantize_trigger_normals != 0,
        old_root_handle,
        prepare_node_reuse != 0,
    ) {
        Ok(value) => value,
        Err(status) => return status,
    };

    let updated_quads_handle = if output.updated_quad_handles.is_empty() {
        0
    } else {
        meshing::updated_quads_create_from_handles(
            output.updated_quad_handles,
            match i32::try_from(output.mesh_quad_count) {
                Ok(value) => value,
                Err(_) => return ERR_CAPACITY,
            },
            match i32::try_from(output.index_quad_count) {
                Ok(value) => value,
                Err(_) => return ERR_CAPACITY,
            },
        )
    };

    result.owned_split_quads.extend(output.owned_split_quads);
    *output_index_quad_count = output.tree.index_quad_count as i32;
    *output_updated_quads_handle = updated_quads_handle;
    *output_reusable_root_handle = output
        .reusable_root
        .map(|root| Box::into_raw(root) as u64)
        .unwrap_or(0);
    *output_tree_handle = Box::into_raw(Box::new(output.tree)) as u64;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_bsp_reusable_root_destroy(handle: u64) -> i32 {
    if handle == 0 {
        return OK;
    }

    drop(Box::from_raw(handle as *mut NativeBspReusableRoot));
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_bsp_tree_destroy(handle: u64) -> i32 {
    if handle == 0 {
        return OK;
    }

    drop(Box::from_raw(handle as *mut NativeBspTree));
    OK
}

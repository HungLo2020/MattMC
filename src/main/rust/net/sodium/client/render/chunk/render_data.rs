use std::{alloc, ptr, slice};

const OK: i32 = 0;
const ERR_NULL_POINTER: i32 = -1;
const ERR_INVALID_ARGUMENT: i32 = -2;
const ERR_CAPACITY: i32 = -3;

const REGION_WIDTH: i32 = 8;
const REGION_HEIGHT: i32 = 4;
const REGION_LENGTH: i32 = 8;
const REGION_SIZE: usize = (REGION_WIDTH * REGION_HEIGHT * REGION_LENGTH) as usize;

const MODEL_QUAD_FACING_COUNT: usize = 7;
const MODEL_POS_X: i32 = 0;
const MODEL_POS_Y: i32 = 1;
const MODEL_POS_Z: i32 = 2;
const MODEL_NEG_X: i32 = 3;
const MODEL_NEG_Y: i32 = 4;
const MODEL_NEG_Z: i32 = 5;
const MODEL_UNASSIGNED: i32 = 6;
const MODEL_QUAD_FACING_ALL: i32 = (1 << MODEL_QUAD_FACING_COUNT) - 1;

const OFFSET_BASE_ELEMENT: usize = 0;
const OFFSET_BASE_VERTEX: usize = 4;
const OFFSET_FACING_LIST: usize = 8;
const OFFSET_IS_LOCAL_INDEX: usize = 15;
const OFFSET_SLICE_MASK: usize = 16;
const OFFSET_VERTEX_COUNTS: usize = 20;
const SECTION_RENDER_DATA_ALIGNMENT: usize = 8;
const SECTION_RENDER_DATA_STRIDE: usize = 48;

pub fn verify() -> i32 {
    if REGION_SIZE == 256
        && MODEL_QUAD_FACING_COUNT == 7
        && OFFSET_BASE_ELEMENT == 0
        && OFFSET_BASE_VERTEX == 4
        && OFFSET_FACING_LIST == 8
        && OFFSET_IS_LOCAL_INDEX == 15
        && OFFSET_SLICE_MASK == 16
        && OFFSET_VERTEX_COUNTS == 20
        && SECTION_RENDER_DATA_ALIGNMENT == 8
        && SECTION_RENDER_DATA_STRIDE == 48
    {
        OK
    } else {
        ERR_INVALID_ARGUMENT
    }
}

unsafe fn allocate_heap(count: i32) -> u64 {
    if count <= 0 {
        return 0;
    }

    let Some(bytes) = (count as usize).checked_mul(SECTION_RENDER_DATA_STRIDE) else {
        return 0;
    };
    let Ok(layout) = alloc::Layout::from_size_align(bytes, SECTION_RENDER_DATA_ALIGNMENT) else {
        return 0;
    };

    let ptr = alloc::alloc_zeroed(layout);
    ptr as u64
}

unsafe fn free_heap(pointer: u64, count: i32) -> i32 {
    if pointer == 0 {
        return OK;
    }
    if count <= 0 {
        return ERR_INVALID_ARGUMENT;
    }

    let Some(bytes) = (count as usize).checked_mul(SECTION_RENDER_DATA_STRIDE) else {
        return ERR_INVALID_ARGUMENT;
    };
    let Ok(layout) = alloc::Layout::from_size_align(bytes, SECTION_RENDER_DATA_ALIGNMENT) else {
        return ERR_INVALID_ARGUMENT;
    };

    alloc::dealloc(pointer as *mut u8, layout);
    OK
}

fn section_offset(index: i32) -> Result<usize, i32> {
    let index = usize::try_from(index).map_err(|_| ERR_INVALID_ARGUMENT)?;
    if index >= REGION_SIZE {
        return Err(ERR_INVALID_ARGUMENT);
    }

    index
        .checked_mul(SECTION_RENDER_DATA_STRIDE)
        .ok_or(ERR_INVALID_ARGUMENT)
}

unsafe fn section_ptr(base_address: u64, index: i32) -> Result<*mut u8, i32> {
    if base_address == 0 {
        return Err(ERR_NULL_POINTER);
    }

    Ok((base_address as *mut u8).add(section_offset(index)?))
}

unsafe fn clear_full(base_address: u64, section_index: i32) -> i32 {
    let Ok(ptr) = section_ptr(base_address, section_index) else {
        return error_for_section_ptr(base_address, section_index);
    };
    ptr::write_bytes(ptr, 0, SECTION_RENDER_DATA_STRIDE);
    OK
}

unsafe fn clear_vertex_data(base_address: u64, section_index: i32) -> i32 {
    let Ok(ptr) = section_ptr(base_address, section_index) else {
        return error_for_section_ptr(base_address, section_index);
    };

    let base_element = read_u32(ptr, OFFSET_BASE_ELEMENT);
    let is_local_index = read_u8(ptr, OFFSET_IS_LOCAL_INDEX);
    ptr::write_bytes(ptr, 0, SECTION_RENDER_DATA_STRIDE);
    write_u32(ptr, OFFSET_BASE_ELEMENT, base_element);
    write_u8(ptr, OFFSET_IS_LOCAL_INDEX, is_local_index);
    OK
}

unsafe fn clear_index_data(base_address: u64, section_index: i32) -> i32 {
    let Ok(ptr) = section_ptr(base_address, section_index) else {
        return error_for_section_ptr(base_address, section_index);
    };

    write_u32(ptr, OFFSET_BASE_ELEMENT, 0);
    write_u8(ptr, OFFSET_IS_LOCAL_INDEX, 0);
    OK
}

unsafe fn set_vertex_data(
    base_address: u64,
    section_index: i32,
    base_vertex: u64,
    vertex_segments_ptr: *const i32,
    vertex_segments_len: i32,
) -> i32 {
    if vertex_segments_len != (MODEL_QUAD_FACING_COUNT * 2) as i32 {
        return ERR_INVALID_ARGUMENT;
    }
    if vertex_segments_ptr.is_null() {
        return ERR_NULL_POINTER;
    }

    let Ok(ptr) = section_ptr(base_address, section_index) else {
        return error_for_section_ptr(base_address, section_index);
    };
    let vertex_segments = slice::from_raw_parts(vertex_segments_ptr, MODEL_QUAD_FACING_COUNT * 2);

    let mut slice_mask = 0i32;
    let mut facing_list = 0u64;
    for index in 0..MODEL_QUAD_FACING_COUNT {
        let segment_index = index << 1;
        let vertex_count = vertex_segments[segment_index] as u32;
        let facing = vertex_segments[segment_index + 1];
        if !(0..MODEL_QUAD_FACING_COUNT as i32).contains(&facing) {
            return ERR_INVALID_ARGUMENT;
        }

        facing_list |= (facing as u64) << (index * 8);
        write_u32(ptr, OFFSET_VERTEX_COUNTS + (index * 4), vertex_count);
        if vertex_count > 0 {
            slice_mask |= 1 << facing;
        }
    }

    write_u32(ptr, OFFSET_BASE_VERTEX, base_vertex as u32);
    write_i32(ptr, OFFSET_SLICE_MASK, slice_mask);
    write_facing_list(ptr, facing_list);
    OK
}

unsafe fn set_local_base_element(base_address: u64, section_index: i32, value: u64) -> i32 {
    let Ok(ptr) = section_ptr(base_address, section_index) else {
        return error_for_section_ptr(base_address, section_index);
    };

    write_u32(ptr, OFFSET_BASE_ELEMENT, value as u32);
    write_u8(ptr, OFFSET_IS_LOCAL_INDEX, 1);
    OK
}

unsafe fn set_shared_base_element(base_address: u64, section_index: i32, value: u64) -> i32 {
    let Ok(ptr) = section_ptr(base_address, section_index) else {
        return error_for_section_ptr(base_address, section_index);
    };

    write_u32(ptr, OFFSET_BASE_ELEMENT, value as u32);
    write_u8(ptr, OFFSET_IS_LOCAL_INDEX, 0);
    OK
}

unsafe fn set_base_vertex(base_address: u64, section_index: i32, value: u64) -> i32 {
    let Ok(ptr) = section_ptr(base_address, section_index) else {
        return error_for_section_ptr(base_address, section_index);
    };

    write_u32(ptr, OFFSET_BASE_VERTEX, value as u32);
    OK
}

unsafe fn fill_draw_commands(
    base_address: u64,
    section_indices_ptr: *const u8,
    section_count: i32,
    reverse_sections: i32,
    region_chunk_x: i32,
    region_chunk_y: i32,
    region_chunk_z: i32,
    camera_x: i32,
    camera_y: i32,
    camera_z: i32,
    use_block_face_culling: i32,
    use_indexed_tessellation: i32,
    element_pointer_address: u64,
    element_count_address: u64,
    base_vertex_address: u64,
    draw_capacity: i32,
    output_size: *mut i32,
) -> i32 {
    if section_count < 0 || draw_capacity < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if base_address == 0
        || element_pointer_address == 0
        || element_count_address == 0
        || base_vertex_address == 0
        || output_size.is_null()
    {
        return ERR_NULL_POINTER;
    }
    if section_count == 0 {
        *output_size = 0;
        return OK;
    }
    if section_indices_ptr.is_null() {
        return ERR_NULL_POINTER;
    }

    let section_count = section_count as usize;
    if section_count > REGION_SIZE {
        return ERR_INVALID_ARGUMENT;
    }

    let section_indices = slice::from_raw_parts(section_indices_ptr, section_count);
    let element_pointers = slice::from_raw_parts_mut(
        element_pointer_address as *mut usize,
        draw_capacity as usize,
    );
    let element_counts =
        slice::from_raw_parts_mut(element_count_address as *mut i32, draw_capacity as usize);
    let base_vertices =
        slice::from_raw_parts_mut(base_vertex_address as *mut i32, draw_capacity as usize);

    let mut size = 0usize;
    if reverse_sections != 0 {
        for &section_index in section_indices.iter().rev() {
            let status = append_section_draws(
                base_address,
                section_index,
                region_chunk_x,
                region_chunk_y,
                region_chunk_z,
                camera_x,
                camera_y,
                camera_z,
                use_block_face_culling != 0,
                use_indexed_tessellation != 0,
                element_pointers,
                element_counts,
                base_vertices,
                &mut size,
            );
            if status != OK {
                return status;
            }
        }
    } else {
        for &section_index in section_indices {
            let status = append_section_draws(
                base_address,
                section_index,
                region_chunk_x,
                region_chunk_y,
                region_chunk_z,
                camera_x,
                camera_y,
                camera_z,
                use_block_face_culling != 0,
                use_indexed_tessellation != 0,
                element_pointers,
                element_counts,
                base_vertices,
                &mut size,
            );
            if status != OK {
                return status;
            }
        }
    }

    *output_size = size as i32;
    OK
}

#[allow(clippy::too_many_arguments)]
unsafe fn append_section_draws(
    base_address: u64,
    section_index: u8,
    region_chunk_x: i32,
    region_chunk_y: i32,
    region_chunk_z: i32,
    camera_x: i32,
    camera_y: i32,
    camera_z: i32,
    use_block_face_culling: bool,
    use_indexed_tessellation: bool,
    element_pointers: &mut [usize],
    element_counts: &mut [i32],
    base_vertices: &mut [i32],
    size: &mut usize,
) -> i32 {
    let section_index = section_index as usize;
    let ptr = (base_address as *mut u8).add(section_index * SECTION_RENDER_DATA_STRIDE);
    let chunk_x = region_chunk_x + unpack_x(section_index);
    let chunk_y = region_chunk_y + unpack_y(section_index);
    let chunk_z = region_chunk_z + unpack_z(section_index);

    let visible_faces = if use_block_face_culling {
        visible_faces(camera_x, camera_y, camera_z, chunk_x, chunk_y, chunk_z)
    } else {
        MODEL_QUAD_FACING_ALL
    };
    let slices = visible_faces & read_i32(ptr, OFFSET_SLICE_MASK);
    if slices == 0 {
        return OK;
    }

    if use_indexed_tessellation && read_u8(ptr, OFFSET_IS_LOCAL_INDEX) != 0 {
        append_local_indexed_draws(
            ptr,
            slices,
            element_pointers,
            element_counts,
            base_vertices,
            size,
        )
    } else {
        append_shared_indexed_draws(
            ptr,
            slices,
            element_pointers,
            element_counts,
            base_vertices,
            size,
        )
    }
}

unsafe fn append_local_indexed_draws(
    ptr: *const u8,
    mask: i32,
    element_pointers: &mut [usize],
    element_counts: &mut [i32],
    base_vertices: &mut [i32],
    size: &mut usize,
) -> i32 {
    let mut element_offset = read_u32(ptr, OFFSET_BASE_ELEMENT) as u64;
    let mut base_vertex = read_u32(ptr, OFFSET_BASE_VERTEX) as u64;

    for facing in 0..MODEL_QUAD_FACING_COUNT {
        let vertex_count = read_u32(ptr, OFFSET_VERTEX_COUNTS + (facing * 4)) as u64;
        let element_count = (vertex_count >> 2) * 6;

        if *size >= element_counts.len() {
            return ERR_CAPACITY;
        }

        element_counts[*size] = element_count as i32;
        base_vertices[*size] = base_vertex as i32;
        element_pointers[*size] = (element_offset << 2) as usize;

        base_vertex += vertex_count;
        element_offset += element_count;
        *size += ((mask >> facing) & 1) as usize;
    }

    OK
}

unsafe fn append_shared_indexed_draws(
    ptr: *const u8,
    mask: i32,
    element_pointers: &mut [usize],
    element_counts: &mut [i32],
    base_vertices: &mut [i32],
    size: &mut usize,
) -> i32 {
    let element_offset_bytes = (read_u32(ptr, OFFSET_BASE_ELEMENT) as usize) << 2;
    let facing_list = read_u64(ptr, OFFSET_FACING_LIST);
    let mut group_vertex_count = 0u64;
    let mut base_vertex = read_u32(ptr, OFFSET_BASE_VERTEX) as u64;
    let mut last_mask_bit = 0i32;

    for index in 0..=MODEL_QUAD_FACING_COUNT {
        let mut mask_bit = 0i32;
        let mut vertex_count = 0u64;
        if index < MODEL_QUAD_FACING_COUNT {
            vertex_count = read_u32(ptr, OFFSET_VERTEX_COUNTS + (index * 4)) as u64;
            if vertex_count != 0 {
                let facing = ((facing_list >> (index * 8)) & 0xFF) as i32;
                mask_bit = (mask >> facing) & 1;
            }
        }

        if mask_bit == 0 {
            if last_mask_bit == 1 {
                if index < MODEL_QUAD_FACING_COUNT && vertex_count == 0 {
                    continue;
                }
                if *size >= element_counts.len() {
                    return ERR_CAPACITY;
                }

                element_counts[*size] = ((group_vertex_count >> 2) * 6) as i32;
                base_vertices[*size] = base_vertex as i32;
                element_pointers[*size] = element_offset_bytes;
                *size += 1;
                base_vertex += group_vertex_count;
                group_vertex_count = 0;
            }

            base_vertex += vertex_count;
        } else {
            group_vertex_count += vertex_count;
        }

        last_mask_bit = mask_bit;
    }

    OK
}

fn unpack_x(index: usize) -> i32 {
    ((index >> 5) & 0b111) as i32
}

fn unpack_y(index: usize) -> i32 {
    (index & 0b11) as i32
}

fn unpack_z(index: usize) -> i32 {
    ((index >> 2) & 0b111) as i32
}

fn visible_faces(
    origin_x: i32,
    origin_y: i32,
    origin_z: i32,
    chunk_x: i32,
    chunk_y: i32,
    chunk_z: i32,
) -> i32 {
    let bounds_min_x = chunk_x << 4;
    let bounds_min_y = chunk_y << 4;
    let bounds_min_z = chunk_z << 4;
    let bounds_max_x = bounds_min_x + 16;
    let bounds_max_y = bounds_min_y + 16;
    let bounds_max_z = bounds_min_z + 16;

    let mut planes = 1 << MODEL_UNASSIGNED;
    planes |= greater_than(origin_x, bounds_min_x - 3) << MODEL_POS_X;
    planes |= greater_than(origin_y, bounds_min_y - 3) << MODEL_POS_Y;
    planes |= greater_than(origin_z, bounds_min_z - 3) << MODEL_POS_Z;
    planes |= less_than(origin_x, bounds_max_x + 3) << MODEL_NEG_X;
    planes |= less_than(origin_y, bounds_max_y + 3) << MODEL_NEG_Y;
    planes |= less_than(origin_z, bounds_max_z + 3) << MODEL_NEG_Z;
    planes
}

fn less_than(a: i32, b: i32) -> i32 {
    ((a.wrapping_sub(b) as u32) >> 31) as i32
}

fn greater_than(a: i32, b: i32) -> i32 {
    ((b.wrapping_sub(a) as u32) >> 31) as i32
}

unsafe fn write_facing_list(ptr: *mut u8, facing_list: u64) {
    let is_local_index = read_u8(ptr, OFFSET_IS_LOCAL_INDEX);
    write_u64(ptr, OFFSET_FACING_LIST, facing_list);
    write_u8(ptr, OFFSET_IS_LOCAL_INDEX, is_local_index);
}

unsafe fn read_u8(ptr: *const u8, offset: usize) -> u8 {
    ptr::read_unaligned(ptr.add(offset))
}

unsafe fn write_u8(ptr: *mut u8, offset: usize, value: u8) {
    ptr::write_unaligned(ptr.add(offset), value);
}

unsafe fn read_u32(ptr: *const u8, offset: usize) -> u32 {
    ptr::read_unaligned(ptr.add(offset) as *const u32)
}

unsafe fn write_u32(ptr: *mut u8, offset: usize, value: u32) {
    ptr::write_unaligned(ptr.add(offset) as *mut u32, value);
}

unsafe fn read_i32(ptr: *const u8, offset: usize) -> i32 {
    ptr::read_unaligned(ptr.add(offset) as *const i32)
}

unsafe fn write_i32(ptr: *mut u8, offset: usize, value: i32) {
    ptr::write_unaligned(ptr.add(offset) as *mut i32, value);
}

unsafe fn read_u64(ptr: *const u8, offset: usize) -> u64 {
    ptr::read_unaligned(ptr.add(offset) as *const u64)
}

unsafe fn write_u64(ptr: *mut u8, offset: usize, value: u64) {
    ptr::write_unaligned(ptr.add(offset) as *mut u64, value);
}

fn error_for_section_ptr(base_address: u64, section_index: i32) -> i32 {
    if base_address == 0 {
        ERR_NULL_POINTER
    } else if section_index < 0 || section_index as usize >= REGION_SIZE {
        ERR_INVALID_ARGUMENT
    } else {
        ERR_INVALID_ARGUMENT
    }
}

#[no_mangle]
pub extern "C" fn mattmc_sodium_section_render_data_verify() -> i32 {
    verify()
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_render_data_allocate(count: i32) -> u64 {
    allocate_heap(count)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_render_data_free(pointer: u64, count: i32) -> i32 {
    free_heap(pointer, count)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_render_data_set_vertex_data(
    base_address: u64,
    section_index: i32,
    base_vertex: u64,
    vertex_segments_ptr: *const i32,
    vertex_segments_len: i32,
) -> i32 {
    set_vertex_data(
        base_address,
        section_index,
        base_vertex,
        vertex_segments_ptr,
        vertex_segments_len,
    )
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_render_data_set_local_base_element(
    base_address: u64,
    section_index: i32,
    value: u64,
) -> i32 {
    set_local_base_element(base_address, section_index, value)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_render_data_set_shared_base_element(
    base_address: u64,
    section_index: i32,
    value: u64,
) -> i32 {
    set_shared_base_element(base_address, section_index, value)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_render_data_set_base_vertex(
    base_address: u64,
    section_index: i32,
    value: u64,
) -> i32 {
    set_base_vertex(base_address, section_index, value)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_render_data_clear_full(
    base_address: u64,
    section_index: i32,
) -> i32 {
    clear_full(base_address, section_index)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_render_data_clear_vertex_data(
    base_address: u64,
    section_index: i32,
) -> i32 {
    clear_vertex_data(base_address, section_index)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_render_data_clear_index_data(
    base_address: u64,
    section_index: i32,
) -> i32 {
    clear_index_data(base_address, section_index)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_render_data_fill_draw_commands(
    base_address: u64,
    section_indices_ptr: *const u8,
    section_count: i32,
    reverse_sections: i32,
    region_chunk_x: i32,
    region_chunk_y: i32,
    region_chunk_z: i32,
    camera_x: i32,
    camera_y: i32,
    camera_z: i32,
    use_block_face_culling: i32,
    use_indexed_tessellation: i32,
    element_pointer_address: u64,
    element_count_address: u64,
    base_vertex_address: u64,
    draw_capacity: i32,
    output_size: *mut i32,
) -> i32 {
    fill_draw_commands(
        base_address,
        section_indices_ptr,
        section_count,
        reverse_sections,
        region_chunk_x,
        region_chunk_y,
        region_chunk_z,
        camera_x,
        camera_y,
        camera_z,
        use_block_face_culling,
        use_indexed_tessellation,
        element_pointer_address,
        element_count_address,
        base_vertex_address,
        draw_capacity,
        output_size,
    )
}

#[no_mangle]
pub extern "C" fn mattmc_sodium_section_render_data_visible_faces(
    origin_x: i32,
    origin_y: i32,
    origin_z: i32,
    chunk_x: i32,
    chunk_y: i32,
    chunk_z: i32,
) -> i32 {
    visible_faces(origin_x, origin_y, origin_z, chunk_x, chunk_y, chunk_z)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn pack(x: usize, y: usize, z: usize) -> u8 {
        (((x & 0b111) << 5) | (y & 0b11) | ((z & 0b111) << 2)) as u8
    }

    #[test]
    fn vertex_data_update_writes_layout_and_shared_commands() {
        let mut storage = [0u8; SECTION_RENDER_DATA_STRIDE * REGION_SIZE];
        let vertex_segments = [
            4,
            MODEL_POS_X, //
            8,
            MODEL_POS_Y, //
            0,
            MODEL_POS_Z, //
            4,
            MODEL_NEG_X, //
            0,
            MODEL_NEG_Y, //
            0,
            MODEL_NEG_Z, //
            4,
            MODEL_UNASSIGNED,
        ];
        let section = pack(0, 0, 0);

        let status = unsafe {
            set_vertex_data(
                storage.as_mut_ptr() as u64,
                section as i32,
                12,
                vertex_segments.as_ptr(),
                vertex_segments.len() as i32,
            )
        };

        assert_eq!(OK, status);

        let sections = [section];
        let mut element_pointers = [0usize; MODEL_QUAD_FACING_COUNT];
        let mut element_counts = [0i32; MODEL_QUAD_FACING_COUNT];
        let mut base_vertices = [0i32; MODEL_QUAD_FACING_COUNT];
        let mut size = 0i32;
        let status = unsafe {
            fill_draw_commands(
                storage.as_mut_ptr() as u64,
                sections.as_ptr(),
                sections.len() as i32,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                element_pointers.as_mut_ptr() as u64,
                element_counts.as_mut_ptr() as u64,
                base_vertices.as_mut_ptr() as u64,
                MODEL_QUAD_FACING_COUNT as i32,
                &mut size,
            )
        };

        assert_eq!(OK, status);
        assert_eq!(1, size);
        assert_eq!([30], element_counts[..1]);
        assert_eq!([12], base_vertices[..1]);
        assert_eq!([0], element_pointers[..1]);
    }

    #[test]
    fn local_indexed_commands_preserve_per_facing_offsets() {
        let mut storage = [0u8; SECTION_RENDER_DATA_STRIDE * REGION_SIZE];
        let vertex_segments = [
            4,
            MODEL_POS_X, //
            8,
            MODEL_POS_Y, //
            0,
            MODEL_POS_Z, //
            4,
            MODEL_NEG_X, //
            0,
            MODEL_NEG_Y, //
            0,
            MODEL_NEG_Z, //
            4,
            MODEL_UNASSIGNED,
        ];
        let section = pack(0, 0, 0);
        unsafe {
            assert_eq!(
                OK,
                set_vertex_data(
                    storage.as_mut_ptr() as u64,
                    section as i32,
                    12,
                    vertex_segments.as_ptr(),
                    vertex_segments.len() as i32,
                )
            );
            assert_eq!(
                OK,
                set_local_base_element(storage.as_mut_ptr() as u64, section as i32, 20)
            );
        }

        let sections = [section];
        let mut element_pointers = [0usize; MODEL_QUAD_FACING_COUNT];
        let mut element_counts = [0i32; MODEL_QUAD_FACING_COUNT];
        let mut base_vertices = [0i32; MODEL_QUAD_FACING_COUNT];
        let mut size = 0i32;
        let status = unsafe {
            fill_draw_commands(
                storage.as_mut_ptr() as u64,
                sections.as_ptr(),
                sections.len() as i32,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                1,
                element_pointers.as_mut_ptr() as u64,
                element_counts.as_mut_ptr() as u64,
                base_vertices.as_mut_ptr() as u64,
                MODEL_QUAD_FACING_COUNT as i32,
                &mut size,
            )
        };

        assert_eq!(OK, status);
        assert_eq!(4, size);
        assert_eq!([6, 12, 6, 6], element_counts[..4]);
        assert_eq!([12, 16, 24, 28], base_vertices[..4]);
        assert_eq!([80, 104, 152, 176], element_pointers[..4]);
    }

    #[test]
    fn block_face_culling_filters_by_camera_position() {
        assert_eq!(
            (1 << MODEL_UNASSIGNED) | (1 << MODEL_NEG_X) | (1 << MODEL_NEG_Y) | (1 << MODEL_NEG_Z),
            visible_faces(0, 0, 0, 1, 1, 1)
        );
    }
}

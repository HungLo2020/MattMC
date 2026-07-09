use std::slice;

const OK: i32 = 0;
const ERR_NULL_POINTER: i32 = -1;
const ERR_INVALID_ARGUMENT: i32 = -2;
const ERR_CAPACITY: i32 = -3;

const REGION_WIDTH: i32 = 8;
const REGION_HEIGHT: i32 = 4;
const REGION_LENGTH: i32 = 8;
const REGION_SIZE: usize = (REGION_WIDTH * REGION_HEIGHT * REGION_LENGTH) as usize;
const SECTION_MAP_LONGS: usize = REGION_SIZE / u64::BITS as usize;
const SECTION_HISTOGRAM_SIZE: usize = (REGION_WIDTH + REGION_HEIGHT + REGION_LENGTH - 2) as usize;

pub fn verify() -> i32 {
    if REGION_SIZE == 256 && SECTION_MAP_LONGS == 4 && SECTION_HISTOGRAM_SIZE == 18 {
        OK
    } else {
        ERR_INVALID_ARGUMENT
    }
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

unsafe fn sort_sections(
    section_map: *const u64,
    section_map_len: i32,
    output_sections: *mut u8,
    output_sections_len: i32,
    relative_camera_section_x: i32,
    relative_camera_section_y: i32,
    relative_camera_section_z: i32,
    output_count: *mut i32,
) -> i32 {
    if section_map.is_null() || output_sections.is_null() || output_count.is_null() {
        return ERR_NULL_POINTER;
    }
    if section_map_len < 0 || output_sections_len < 0 {
        return ERR_INVALID_ARGUMENT;
    }

    let section_map_len = section_map_len as usize;
    if section_map_len != SECTION_MAP_LONGS {
        return ERR_INVALID_ARGUMENT;
    }

    let output_sections_len = output_sections_len as usize;
    let section_map = slice::from_raw_parts(section_map, section_map_len);
    let output_sections = slice::from_raw_parts_mut(output_sections, output_sections_len);
    let camera_x = relative_camera_section_x.clamp(0, REGION_WIDTH - 1);
    let camera_y = relative_camera_section_y.clamp(0, REGION_HEIGHT - 1);
    let camera_z = relative_camera_section_z.clamp(0, REGION_LENGTH - 1);

    let mut histogram = [0i32; SECTION_HISTOGRAM_SIZE];
    let mut sort_items = [0i32; REGION_SIZE];
    let mut count = 0usize;

    for (map_index, map) in section_map.iter().enumerate() {
        let mut map = *map;
        let map_offset = map_index << 6;

        while map != 0 {
            let index = map.trailing_zeros() as usize + map_offset;
            map &= map - 1;

            let x = (unpack_x(index) - camera_x).abs();
            let y = (unpack_y(index) - camera_y).abs();
            let z = (unpack_z(index) - camera_z).abs();
            let distance = (x + y + z) as usize;

            if distance >= SECTION_HISTOGRAM_SIZE || count >= REGION_SIZE {
                return ERR_INVALID_ARGUMENT;
            }

            histogram[distance] += 1;
            sort_items[count] = ((distance as i32) << 8) | index as i32;
            count += 1;
        }
    }

    if output_sections_len < count {
        return ERR_CAPACITY;
    }

    for index in 1..SECTION_HISTOGRAM_SIZE {
        histogram[index] += histogram[index - 1];
    }

    for item in sort_items.iter().take(count) {
        let distance = ((*item as u32) >> 8) as usize;
        histogram[distance] -= 1;
        output_sections[histogram[distance] as usize] = (*item & 0xFF) as u8;
    }

    *output_count = count as i32;
    OK
}

unsafe fn sort_regions(
    region_coordinates: *const i32,
    region_count: i32,
    output_indices: *mut i32,
    output_indices_len: i32,
    camera_region_x: i32,
    camera_region_y: i32,
    camera_region_z: i32,
) -> i32 {
    if region_coordinates.is_null() || output_indices.is_null() {
        return ERR_NULL_POINTER;
    }
    if region_count < 0 || output_indices_len < 0 {
        return ERR_INVALID_ARGUMENT;
    }

    let region_count = region_count as usize;
    let output_indices_len = output_indices_len as usize;
    if output_indices_len < region_count {
        return ERR_CAPACITY;
    }

    let coordinate_count = match region_count.checked_mul(3) {
        Some(value) => value,
        None => return ERR_INVALID_ARGUMENT,
    };
    let region_coordinates = slice::from_raw_parts(region_coordinates, coordinate_count);
    let output_indices = slice::from_raw_parts_mut(output_indices, output_indices_len);

    for index in 0..region_count {
        output_indices[index] = index as i32;
    }

    output_indices[..region_count].sort_unstable_by(|left, right| {
        let left = *left as usize;
        let right = *right as usize;
        let left_distance = region_distance(
            region_coordinates,
            left,
            camera_region_x,
            camera_region_y,
            camera_region_z,
        );
        let right_distance = region_distance(
            region_coordinates,
            right,
            camera_region_x,
            camera_region_y,
            camera_region_z,
        );

        left_distance
            .cmp(&right_distance)
            .then_with(|| left.cmp(&right))
    });

    OK
}

fn region_distance(
    region_coordinates: &[i32],
    index: usize,
    camera_region_x: i32,
    camera_region_y: i32,
    camera_region_z: i32,
) -> i32 {
    let base = index * 3;
    let x = (region_coordinates[base] - camera_region_x).abs();
    let y = (region_coordinates[base + 1] - camera_region_y).abs();
    let z = (region_coordinates[base + 2] - camera_region_z).abs();
    x + y + z
}

unsafe fn prepare_frame(
    region_coordinates_address: u64,
    region_count: i32,
    output_region_indices_address: u64,
    output_region_indices_len: i32,
    section_maps_address: u64,
    section_batch_count: i32,
    section_camera_positions_address: u64,
    output_section_counts_address: u64,
    output_sections_address: u64,
    output_section_stride: i32,
    camera_region_x: i32,
    camera_region_y: i32,
    camera_region_z: i32,
) -> i32 {
    if region_count < 0 || output_region_indices_len < 0 || section_batch_count < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if region_count > 0 && (region_coordinates_address == 0 || output_region_indices_address == 0) {
        return ERR_NULL_POINTER;
    }

    let status = sort_regions(
        region_coordinates_address as *const i32,
        region_count,
        output_region_indices_address as *mut i32,
        output_region_indices_len,
        camera_region_x,
        camera_region_y,
        camera_region_z,
    );
    if status != OK {
        return status;
    }

    if section_batch_count == 0 {
        return OK;
    }
    if output_section_stride < REGION_SIZE as i32 {
        return ERR_CAPACITY;
    }
    if section_maps_address == 0
        || section_camera_positions_address == 0
        || output_section_counts_address == 0
        || output_sections_address == 0
    {
        return ERR_NULL_POINTER;
    }

    let section_batch_count = section_batch_count as usize;
    let section_camera_positions = slice::from_raw_parts(
        section_camera_positions_address as *const i32,
        section_batch_count * 3,
    );
    let output_section_counts = slice::from_raw_parts_mut(
        output_section_counts_address as *mut i32,
        section_batch_count,
    );
    let output_section_stride = output_section_stride as usize;

    for batch_index in 0..section_batch_count {
        let map_address = section_maps_address
            + (batch_index * SECTION_MAP_LONGS * std::mem::size_of::<u64>()) as u64;
        let output_address = output_sections_address + (batch_index * output_section_stride) as u64;
        let camera_offset = batch_index * 3;

        let status = sort_sections(
            map_address as *const u64,
            SECTION_MAP_LONGS as i32,
            output_address as *mut u8,
            output_section_stride as i32,
            section_camera_positions[camera_offset],
            section_camera_positions[camera_offset + 1],
            section_camera_positions[camera_offset + 2],
            output_section_counts.as_mut_ptr().add(batch_index),
        );
        if status != OK {
            return status;
        }
    }

    OK
}

#[no_mangle]
pub extern "C" fn mattmc_sodium_render_list_verify() -> i32 {
    verify()
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_render_list_sort_sections(
    section_map: *const u64,
    section_map_len: i32,
    output_sections: *mut u8,
    output_sections_len: i32,
    relative_camera_section_x: i32,
    relative_camera_section_y: i32,
    relative_camera_section_z: i32,
    output_count: *mut i32,
) -> i32 {
    sort_sections(
        section_map,
        section_map_len,
        output_sections,
        output_sections_len,
        relative_camera_section_x,
        relative_camera_section_y,
        relative_camera_section_z,
        output_count,
    )
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_render_list_sort_regions(
    region_coordinates: *const i32,
    region_count: i32,
    output_indices: *mut i32,
    output_indices_len: i32,
    camera_region_x: i32,
    camera_region_y: i32,
    camera_region_z: i32,
) -> i32 {
    sort_regions(
        region_coordinates,
        region_count,
        output_indices,
        output_indices_len,
        camera_region_x,
        camera_region_y,
        camera_region_z,
    )
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_render_list_prepare_frame(
    region_coordinates_address: u64,
    region_count: i32,
    output_region_indices_address: u64,
    output_region_indices_len: i32,
    section_maps_address: u64,
    section_batch_count: i32,
    section_camera_positions_address: u64,
    output_section_counts_address: u64,
    output_sections_address: u64,
    output_section_stride: i32,
    camera_region_x: i32,
    camera_region_y: i32,
    camera_region_z: i32,
) -> i32 {
    prepare_frame(
        region_coordinates_address,
        region_count,
        output_region_indices_address,
        output_region_indices_len,
        section_maps_address,
        section_batch_count,
        section_camera_positions_address,
        output_section_counts_address,
        output_sections_address,
        output_section_stride,
        camera_region_x,
        camera_region_y,
        camera_region_z,
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    fn pack(x: usize, y: usize, z: usize) -> usize {
        ((x & 0b111) << 5) | (y & 0b11) | ((z & 0b111) << 2)
    }

    #[test]
    fn section_sort_matches_histogram_order() {
        let mut map = [0u64; SECTION_MAP_LONGS];
        for index in [pack(0, 0, 0), pack(1, 0, 0), pack(0, 1, 0), pack(2, 0, 0)] {
            map[index >> 6] |= 1u64 << (index & 0b111111);
        }

        let mut output = [0u8; REGION_SIZE];
        let mut count = 0i32;

        let status = unsafe {
            sort_sections(
                map.as_ptr(),
                map.len() as i32,
                output.as_mut_ptr(),
                output.len() as i32,
                0,
                0,
                0,
                &mut count,
            )
        };

        assert_eq!(OK, status);
        assert_eq!(4, count);
        assert_eq!(
            [
                pack(0, 0, 0) as u8,
                pack(1, 0, 0) as u8,
                pack(0, 1, 0) as u8,
                pack(2, 0, 0) as u8,
            ],
            output[..4]
        );
    }

    #[test]
    fn region_sort_uses_distance_then_original_index() {
        let coordinates = [
            4, 0, 0, //
            1, 0, 0, //
            0, 0, 1, //
            2, 2, 2,
        ];
        let mut output = [0i32; 4];

        let status = unsafe {
            sort_regions(
                coordinates.as_ptr(),
                4,
                output.as_mut_ptr(),
                output.len() as i32,
                0,
                0,
                0,
            )
        };

        assert_eq!(OK, status);
        assert_eq!([1, 2, 0, 3], output);
    }

    #[test]
    fn prepare_frame_sorts_regions_and_dirty_section_maps() {
        let coordinates = [
            4, 0, 0, //
            1, 0, 0, //
            0, 0, 1,
        ];
        let mut region_indices = [0i32; 3];
        let mut maps = [0u64; SECTION_MAP_LONGS * 2];
        let camera_positions = [0i32; 6];
        let mut section_counts = [0i32; 2];
        let mut sections = [0u8; REGION_SIZE * 2];

        for index in [pack(0, 0, 0), pack(1, 0, 0)] {
            maps[index >> 6] |= 1u64 << (index & 0b111111);
        }
        let second_offset = SECTION_MAP_LONGS;
        for index in [pack(2, 0, 0), pack(0, 1, 0)] {
            maps[second_offset + (index >> 6)] |= 1u64 << (index & 0b111111);
        }

        let status = unsafe {
            prepare_frame(
                coordinates.as_ptr() as u64,
                3,
                region_indices.as_mut_ptr() as u64,
                3,
                maps.as_ptr() as u64,
                2,
                camera_positions.as_ptr() as u64,
                section_counts.as_mut_ptr() as u64,
                sections.as_mut_ptr() as u64,
                REGION_SIZE as i32,
                0,
                0,
                0,
            )
        };

        assert_eq!(OK, status);
        assert_eq!([1, 2, 0], region_indices);
        assert_eq!([2, 2], section_counts);
        assert_eq!([pack(0, 0, 0) as u8, pack(1, 0, 0) as u8], sections[..2]);
        assert_eq!(
            [pack(0, 1, 0) as u8, pack(2, 0, 0) as u8],
            sections[REGION_SIZE..REGION_SIZE + 2]
        );
    }
}

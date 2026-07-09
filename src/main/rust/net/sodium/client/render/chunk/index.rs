use std::slice;

const OK: i32 = 0;
const ERR_NULL_POINTER: i32 = -1;
const ERR_INVALID_ARGUMENT: i32 = -2;
const ERR_CAPACITY: i32 = -3;

const INDICES_PER_QUAD: usize = 6;
const VERTICES_PER_QUAD: i32 = 4;

pub fn write_shared_quad_index_buffer(
    output: &mut [u8],
    index_stride: i32,
    primitive_count: i32,
) -> i32 {
    if primitive_count < 0 {
        return ERR_INVALID_ARGUMENT;
    }

    match index_stride {
        2 => write_shared_u16(output, primitive_count as usize),
        4 => write_shared_i32(output, primitive_count as usize),
        _ => ERR_INVALID_ARGUMENT,
    }
}

pub fn write_sorted_quad_index_buffer(output: &mut [i32], quad_indexes: &[i32]) -> i32 {
    let required = match quad_indexes.len().checked_mul(INDICES_PER_QUAD) {
        Some(value) => value,
        None => return ERR_INVALID_ARGUMENT,
    };
    if output.len() < required {
        return ERR_CAPACITY;
    }

    for (output_quad, quad_index) in output.chunks_exact_mut(INDICES_PER_QUAD).zip(quad_indexes) {
        write_quad_i32(output_quad, *quad_index);
    }

    OK
}

pub fn write_key_sorted_quad_index_buffer(output: &mut [i32], keys: &[i32]) -> i32 {
    let required = match keys.len().checked_mul(INDICES_PER_QUAD) {
        Some(value) => value,
        None => return ERR_INVALID_ARGUMENT,
    };
    if output.len() < required {
        return ERR_CAPACITY;
    }

    let mut quad_indexes: Vec<i32> = (0..keys.len() as i32).collect();
    radix_sort_indirect(&mut quad_indexes, keys);

    write_sorted_quad_index_buffer(output, &quad_indexes)
}

fn radix_sort_indirect(quad_indexes: &mut [i32], keys: &[i32]) {
    if quad_indexes.len() <= 1 {
        return;
    }

    let mut next = vec![0i32; quad_indexes.len()];
    let mut offsets = [0usize; 256];

    for digit in 0..4 {
        offsets.fill(0);

        for &quad_index in quad_indexes.iter() {
            offsets[extract_digit(keys[quad_index as usize], digit)] += 1;
        }

        let mut sum = 0usize;
        for offset in offsets.iter_mut() {
            let previous = *offset;
            *offset = sum;
            sum += previous;
        }

        for &quad_index in quad_indexes.iter() {
            let bucket = extract_digit(keys[quad_index as usize], digit);
            next[offsets[bucket]] = quad_index;
            offsets[bucket] += 1;
        }

        quad_indexes.copy_from_slice(&next);
    }
}

fn extract_digit(key: i32, digit: usize) -> usize {
    ((key as u32 >> (digit * 8)) & 0xff) as usize
}

fn write_shared_u16(output: &mut [u8], primitive_count: usize) -> i32 {
    let required = match primitive_count.checked_mul(INDICES_PER_QUAD * 2) {
        Some(value) => value,
        None => return ERR_INVALID_ARGUMENT,
    };
    if output.len() < required {
        return ERR_CAPACITY;
    }

    for primitive_index in 0..primitive_count {
        let vertex_offset = match (primitive_index as i32).checked_mul(VERTICES_PER_QUAD) {
            Some(value) => value,
            None => return ERR_INVALID_ARGUMENT,
        };
        let index_offset = primitive_index * INDICES_PER_QUAD * 2;

        for (element, value) in quad_indices(vertex_offset).iter().enumerate() {
            output[index_offset + element * 2..index_offset + element * 2 + 2]
                .copy_from_slice(&(*value as u16).to_ne_bytes());
        }
    }

    OK
}

fn write_shared_i32(output: &mut [u8], primitive_count: usize) -> i32 {
    let required = match primitive_count.checked_mul(INDICES_PER_QUAD * 4) {
        Some(value) => value,
        None => return ERR_INVALID_ARGUMENT,
    };
    if output.len() < required {
        return ERR_CAPACITY;
    }

    for primitive_index in 0..primitive_count {
        let vertex_offset = match (primitive_index as i32).checked_mul(VERTICES_PER_QUAD) {
            Some(value) => value,
            None => return ERR_INVALID_ARGUMENT,
        };
        let index_offset = primitive_index * INDICES_PER_QUAD * 4;

        for (element, value) in quad_indices(vertex_offset).iter().enumerate() {
            output[index_offset + element * 4..index_offset + element * 4 + 4]
                .copy_from_slice(&value.to_ne_bytes());
        }
    }

    OK
}

fn write_quad_i32(output: &mut [i32], quad_index: i32) {
    let vertex_offset = quad_index * VERTICES_PER_QUAD;
    output.copy_from_slice(&quad_indices(vertex_offset));
}

fn quad_indices(vertex_offset: i32) -> [i32; INDICES_PER_QUAD] {
    [
        vertex_offset,
        vertex_offset + 1,
        vertex_offset + 2,
        vertex_offset + 2,
        vertex_offset + 3,
        vertex_offset,
    ]
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_chunk_shared_quad_index_buffer_write(
    output_address: u64,
    output_capacity: i32,
    index_stride: i32,
    primitive_count: i32,
) -> i32 {
    if output_capacity < 0 || primitive_count < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if primitive_count == 0 {
        return OK;
    }
    if output_address == 0 {
        return ERR_NULL_POINTER;
    }

    let output = slice::from_raw_parts_mut(output_address as *mut u8, output_capacity as usize);
    write_shared_quad_index_buffer(output, index_stride, primitive_count)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_chunk_sorted_quad_index_buffer_write(
    output_address: u64,
    output_capacity: i32,
    quad_indexes: *const i32,
    quad_index_count: i32,
) -> i32 {
    if output_capacity < 0 || quad_index_count < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if quad_index_count == 0 {
        return OK;
    }
    if output_address == 0 || quad_indexes.is_null() {
        return ERR_NULL_POINTER;
    }

    let output = slice::from_raw_parts_mut(output_address as *mut i32, output_capacity as usize);
    let quad_indexes = slice::from_raw_parts(quad_indexes, quad_index_count as usize);
    write_sorted_quad_index_buffer(output, quad_indexes)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_chunk_key_sorted_quad_index_buffer_write(
    output_address: u64,
    output_capacity: i32,
    keys: *const i32,
    key_count: i32,
) -> i32 {
    if output_capacity < 0 || key_count < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if key_count == 0 {
        return OK;
    }
    if output_address == 0 || keys.is_null() {
        return ERR_NULL_POINTER;
    }

    let output = slice::from_raw_parts_mut(output_address as *mut i32, output_capacity as usize);
    let keys = slice::from_raw_parts(keys, key_count as usize);
    write_key_sorted_quad_index_buffer(output, keys)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn writes_shared_int_indices() {
        let mut output = vec![0u8; 2 * INDICES_PER_QUAD * 4];

        assert_eq!(OK, write_shared_quad_index_buffer(&mut output, 4, 2));

        let values: Vec<i32> = output
            .chunks_exact(4)
            .map(|bytes| i32::from_ne_bytes(bytes.try_into().unwrap()))
            .collect();
        assert_eq!(vec![0, 1, 2, 2, 3, 0, 4, 5, 6, 6, 7, 4], values);
    }

    #[test]
    fn writes_shared_short_indices() {
        let mut output = vec![0u8; 2 * INDICES_PER_QUAD * 2];

        assert_eq!(OK, write_shared_quad_index_buffer(&mut output, 2, 2));

        let values: Vec<u16> = output
            .chunks_exact(2)
            .map(|bytes| u16::from_ne_bytes(bytes.try_into().unwrap()))
            .collect();
        assert_eq!(vec![0, 1, 2, 2, 3, 0, 4, 5, 6, 6, 7, 4], values);
    }

    #[test]
    fn writes_sorted_quad_indices() {
        let mut output = vec![0; 3 * INDICES_PER_QUAD];

        assert_eq!(OK, write_sorted_quad_index_buffer(&mut output, &[2, 0, 1]));

        assert_eq!(
            vec![8, 9, 10, 10, 11, 8, 0, 1, 2, 2, 3, 0, 4, 5, 6, 6, 7, 4],
            output
        );
    }

    #[test]
    fn sorts_unsigned_keys_and_writes_quad_indices() {
        let mut output = vec![0; 4 * INDICES_PER_QUAD];

        assert_eq!(
            OK,
            write_key_sorted_quad_index_buffer(&mut output, &[30, -1, 10, 20])
        );

        assert_eq!(
            vec![8, 9, 10, 10, 11, 8, 12, 13, 14, 14, 15, 12, 0, 1, 2, 2, 3, 0, 4, 5, 6, 6, 7, 4,],
            output
        );
    }

    #[test]
    fn radix_sort_matches_unsigned_key_order() {
        let keys = [0x8000_0000u32 as i32, 5, 0xffff_ffffu32 as i32, 0, 5];
        let mut indexes = vec![0, 1, 2, 3, 4];

        radix_sort_indirect(&mut indexes, &keys);

        assert_eq!(vec![3, 1, 4, 0, 2], indexes);
    }
}

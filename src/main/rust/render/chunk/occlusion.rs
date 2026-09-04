use std::slice;

const OK: i32 = 0;
const ERR_NULL_POINTER: i32 = -1;
const ERR_INVALID_ARGUMENT: i32 = -2;

const GRAPH_DIRECTION_COUNT: usize = 6;
const GRAPH_DIRECTION_ALL: i32 = (1 << GRAPH_DIRECTION_COUNT) - 1;
const VISIBILITY_MATRIX_LEN: usize = GRAPH_DIRECTION_COUNT * GRAPH_DIRECTION_COUNT;

const DOWN: i32 = 0;
const UP: i32 = 1;
const NORTH: i32 = 2;
const SOUTH: i32 = 3;
const WEST: i32 = 4;
const EAST: i32 = 5;

const UP_DOWN_OCCLUDED: u64 = bit_mask(DOWN, UP) | bit_mask(UP, DOWN);
const NORTH_SOUTH_OCCLUDED: u64 = bit_mask(NORTH, SOUTH) | bit_mask(SOUTH, NORTH);
const WEST_EAST_OCCLUDED: u64 = bit_mask(WEST, EAST) | bit_mask(EAST, WEST);

const fn bit(from: i32, to: i32) -> i32 {
    from * 8 + to
}

const fn bit_mask(from: i32, to: i32) -> u64 {
    1u64 << bit(from, to)
}

pub fn verify() -> i32 {
    if bit(DOWN, UP) == 1
        && bit(EAST, WEST) == 44
        && GRAPH_DIRECTION_ALL == 0b11_1111
        && VISIBILITY_MATRIX_LEN == 36
    {
        OK
    } else {
        ERR_INVALID_ARGUMENT
    }
}

pub fn encode_visibility_matrix(matrix: &[u8]) -> Result<u64, i32> {
    if matrix.len() != VISIBILITY_MATRIX_LEN {
        return Err(ERR_INVALID_ARGUMENT);
    }

    let mut visibility_data = 0u64;
    for from in 0..GRAPH_DIRECTION_COUNT {
        for to in 0..GRAPH_DIRECTION_COUNT {
            if matrix[(from * GRAPH_DIRECTION_COUNT) + to] != 0 {
                visibility_data |= 1u64 << bit(from as i32, to as i32);
            }
        }
    }

    Ok(visibility_data)
}

pub fn connections_for_visibility(visibility_data: u64, incoming: i32, use_incoming: bool) -> i32 {
    let data = if use_incoming {
        visibility_data & create_mask(incoming & GRAPH_DIRECTION_ALL)
    } else {
        visibility_data
    };

    fold_outgoing_directions(data)
}

pub fn connections_for_section(
    visibility_data: u64,
    incoming: i32,
    camera_delta_x: f64,
    camera_delta_y: f64,
    camera_delta_z: f64,
) -> i32 {
    let masked_visibility =
        visibility_data & angle_visibility_mask(camera_delta_x, camera_delta_y, camera_delta_z);
    connections_for_visibility(masked_visibility, incoming, true)
}

fn create_mask(incoming: i32) -> u64 {
    let expanded = 0b0000001_0000001_0000001_0000001_0000001_0000001u64 * (incoming as u32 as u64);
    (expanded & 0b00000001_00000001_00000001_00000001_00000001_00000001u64) * 0xFF
}

fn fold_outgoing_directions(data: u64) -> i32 {
    let mut folded = data;
    folded |= folded >> 32;
    folded |= folded >> 16;
    folded |= folded >> 8;

    (folded & GRAPH_DIRECTION_ALL as u64) as i32
}

fn angle_visibility_mask(camera_delta_x: f64, camera_delta_y: f64, camera_delta_z: f64) -> u64 {
    let dx = camera_delta_x.abs();
    let dy = camera_delta_y.abs();
    let dz = camera_delta_z.abs();

    let mut angle_occlusion_mask = 0u64;
    if dx > dy || dz > dy {
        angle_occlusion_mask |= UP_DOWN_OCCLUDED;
    }
    if dx > dz || dy > dz {
        angle_occlusion_mask |= NORTH_SOUTH_OCCLUDED;
    }
    if dy > dx || dz > dx {
        angle_occlusion_mask |= WEST_EAST_OCCLUDED;
    }

    !angle_occlusion_mask
}

#[no_mangle]
pub extern "C" fn mattmc_sodium_occlusion_verify() -> i32 {
    verify()
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_occlusion_encode_visibility(
    matrix_ptr: *const u8,
    matrix_len: i32,
) -> u64 {
    if matrix_ptr.is_null() || matrix_len != VISIBILITY_MATRIX_LEN as i32 {
        return 0;
    }

    let matrix = slice::from_raw_parts(matrix_ptr, VISIBILITY_MATRIX_LEN);
    encode_visibility_matrix(matrix).unwrap_or(0)
}

#[no_mangle]
pub extern "C" fn mattmc_sodium_occlusion_connections(
    visibility_data: u64,
    incoming: i32,
    use_incoming: i32,
) -> i32 {
    connections_for_visibility(visibility_data, incoming, use_incoming != 0)
}

#[no_mangle]
pub extern "C" fn mattmc_sodium_occlusion_connections_for_camera(
    visibility_data: u64,
    incoming: i32,
    camera_delta_x: f64,
    camera_delta_y: f64,
    camera_delta_z: f64,
) -> i32 {
    connections_for_section(
        visibility_data,
        incoming,
        camera_delta_x,
        camera_delta_y,
        camera_delta_z,
    )
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_occlusion_connections_batch(
    visibility_data_ptr: *const u64,
    visibility_data_len: i32,
    incoming_ptr: *const i32,
    incoming_len: i32,
    camera_delta_ptr: *const f64,
    camera_delta_len: i32,
    output_ptr: *mut i32,
    output_len: i32,
) -> i32 {
    if visibility_data_len < 0 || incoming_len < 0 || camera_delta_len < 0 || output_len < 0 {
        return ERR_INVALID_ARGUMENT;
    }

    let count = visibility_data_len as usize;
    if incoming_len as usize != count
        || output_len as usize != count
        || camera_delta_len as usize != count.saturating_mul(3)
    {
        return ERR_INVALID_ARGUMENT;
    }

    if count == 0 {
        return OK;
    }

    if visibility_data_ptr.is_null()
        || incoming_ptr.is_null()
        || camera_delta_ptr.is_null()
        || output_ptr.is_null()
    {
        return ERR_NULL_POINTER;
    }

    let visibility_data = slice::from_raw_parts(visibility_data_ptr, count);
    let incoming = slice::from_raw_parts(incoming_ptr, count);
    let camera_delta = slice::from_raw_parts(camera_delta_ptr, count * 3);
    let output = slice::from_raw_parts_mut(output_ptr, count);

    for index in 0..count {
        let delta_offset = index * 3;
        output[index] = connections_for_section(
            visibility_data[index],
            incoming[index],
            camera_delta[delta_offset],
            camera_delta[delta_offset + 1],
            camera_delta[delta_offset + 2],
        );
    }

    OK
}

#[cfg(test)]
mod tests {
    use super::*;

    fn matrix(entries: &[(i32, i32)]) -> [u8; VISIBILITY_MATRIX_LEN] {
        let mut matrix = [0u8; VISIBILITY_MATRIX_LEN];
        for (from, to) in entries {
            matrix[(*from as usize * GRAPH_DIRECTION_COUNT) + *to as usize] = 1;
        }
        matrix
    }

    #[test]
    fn encodes_visibility_matrix_using_sodium_bit_layout() {
        let matrix = matrix(&[(DOWN, UP), (WEST, EAST), (EAST, WEST)]);
        let encoded = encode_visibility_matrix(&matrix).unwrap();

        assert_eq!(
            bit_mask(DOWN, UP) | bit_mask(WEST, EAST) | bit_mask(EAST, WEST),
            encoded
        );
    }

    #[test]
    fn folds_all_outgoing_directions() {
        let matrix = matrix(&[(DOWN, NORTH), (UP, SOUTH), (WEST, EAST)]);
        let encoded = encode_visibility_matrix(&matrix).unwrap();

        assert_eq!(
            (1 << NORTH) | (1 << SOUTH) | (1 << EAST),
            connections_for_visibility(encoded, 0, false)
        );
    }

    #[test]
    fn filters_outgoing_directions_by_incoming_set() {
        let matrix = matrix(&[(DOWN, NORTH), (UP, SOUTH), (WEST, EAST)]);
        let encoded = encode_visibility_matrix(&matrix).unwrap();
        let incoming = (1 << DOWN) | (1 << WEST);

        assert_eq!(
            (1 << NORTH) | (1 << EAST),
            connections_for_visibility(encoded, incoming, true)
        );
    }

    #[test]
    fn applies_angle_occlusion_before_folding_connections() {
        let matrix = matrix(&[(DOWN, UP), (DOWN, NORTH)]);
        let encoded = encode_visibility_matrix(&matrix).unwrap();
        let incoming = 1 << DOWN;

        assert_eq!(
            1 << NORTH,
            connections_for_section(encoded, incoming, 20.0, 1.0, 0.0)
        );
    }

    #[test]
    fn camera_aware_ffi_matches_the_portal_angle_contract() {
        let encoded = encode_visibility_matrix(&matrix(&[(DOWN, UP), (DOWN, NORTH)])).unwrap();
        assert_eq!(
            1 << NORTH,
            mattmc_sodium_occlusion_connections_for_camera(encoded, 1 << DOWN, 20.0, 1.0, 0.0)
        );
    }

    #[test]
    fn exported_batch_connections_processes_each_section() {
        let first = encode_visibility_matrix(&matrix(&[(DOWN, UP), (DOWN, NORTH)])).unwrap();
        let second = encode_visibility_matrix(&matrix(&[(WEST, EAST)])).unwrap();
        let visibility = [first, second];
        let incoming = [1 << DOWN, 1 << WEST];
        let camera_delta = [
            20.0, 1.0, 0.0, //
            3.0, 0.0, 0.0,
        ];
        let mut output = [0i32; 2];

        let status = unsafe {
            mattmc_sodium_occlusion_connections_batch(
                visibility.as_ptr(),
                visibility.len() as i32,
                incoming.as_ptr(),
                incoming.len() as i32,
                camera_delta.as_ptr(),
                camera_delta.len() as i32,
                output.as_mut_ptr(),
                output.len() as i32,
            )
        };

        assert_eq!(OK, status);
        assert_eq!([1 << NORTH, 1 << EAST], output);
    }
}

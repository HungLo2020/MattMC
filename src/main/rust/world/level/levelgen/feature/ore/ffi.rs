use super::geometry;

/// Caller owns aligned nonoverlapping arrays for this call only. Returns the
/// required output int count, or -1 for invalid lengths/pointers. Input radii
/// are pruned in place. Output length is rounded down to complete four-int spans.
#[no_mangle]
pub unsafe extern "C" fn mattmc_ore_geometry(
    spheres: *mut f64,
    count: i32,
    output: *mut i32,
    capacity: i32,
    x: i32,
    y: i32,
    z: i32,
) -> i64 {
    if count < 0
        || capacity < 0
        || spheres.is_null()
        || output.is_null()
        || (spheres as usize) % 8 != 0
        || (output as usize) % 4 != 0
    {
        return -1;
    }
    let spheres = std::slice::from_raw_parts_mut(spheres, count as usize * 4);
    let output = std::slice::from_raw_parts_mut(output, capacity as usize);
    geometry::prune(spheres);
    geometry::raster(spheres, [x, y, z], output) as i64
}

/// Samples contains six endpoint doubles followed by count random doubles;
/// shape contains count pairs (float fraction, float sine-plus-one). All arrays
/// must be valid for their declared lengths and disjoint from output.
#[no_mangle]
pub unsafe extern "C" fn mattmc_ore_vein(
    samples: *const f64,
    shape: *const f64,
    count: i32,
    output: *mut i32,
    capacity: i32,
    x: i32,
    y: i32,
    z: i32,
) -> i64 {
    if count < 0
        || capacity < 0
        || samples.is_null()
        || shape.is_null()
        || output.is_null()
        || (samples as usize) % 8 != 0
        || (shape as usize) % 8 != 0
        || (output as usize) % 4 != 0
    {
        return -1;
    }
    let input = std::slice::from_raw_parts(samples, 6 + count as usize);
    let shape = std::slice::from_raw_parts(shape, count as usize * 2);
    let output = std::slice::from_raw_parts_mut(output, capacity as usize);
    let mut stack = [0.0; 64 * 4];
    let mut large;
    let spheres = if count <= 64 {
        &mut stack[..count as usize * 4]
    } else {
        large = vec![0.0; count as usize * 4];
        &mut large
    };
    super::spheres::prepare(input, shape, spheres);
    geometry::prune(spheres);
    geometry::raster(spheres, [x, y, z], output) as i64
}

/// Strictly bounded nonblocking entry; unsuitable samples return -2 before any
/// evaluation. An ordinary call then handles custom RandomSource values.
#[no_mangle]
pub unsafe extern "C" fn mattmc_ore_vein_small(
    samples: *const f64,
    shape: *const f64,
    count: i32,
    output: *mut i32,
    capacity: i32,
    x: i32,
    y: i32,
    z: i32,
) -> i64 {
    if !(0..=16).contains(&count)
        || samples.is_null()
        || shape.is_null()
        || (samples as usize) % 8 != 0
        || (shape as usize) % 8 != 0
    {
        return -2;
    }
    if capacity < 0 || output.is_null() || (output as usize) % 4 != 0 {
        return -1;
    }
    let input = std::slice::from_raw_parts(samples, 6 + count as usize);
    let shape = std::slice::from_raw_parts(shape, count as usize * 2);
    let mut stack = [0.0; 16 * 4];
    let spheres = &mut stack[..count as usize * 4];
    super::spheres::prepare(input, shape, spheres);
    for sphere in spheres.chunks_exact(4) {
        if sphere[..3].iter().any(|v| !(v.abs() <= 1_000_000_000.0))
            || !sphere[3].is_finite()
            || sphere[3].abs() > 2.0
        {
            return -2;
        }
    }
    let output = std::slice::from_raw_parts_mut(output, capacity as usize);
    geometry::prune(spheres);
    geometry::raster(spheres, [x, y, z], output) as i64
}

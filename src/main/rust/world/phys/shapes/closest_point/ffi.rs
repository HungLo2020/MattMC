/// Complete closest-point reduction, without allocation, callbacks or retained pointers.
/// Returns 1 for a point, 0 for no boxes, -1 for invalid metadata, -2 for
/// nonfinite coordinates/query (the Java compatibility caller preserves NaN bits).
///
/// # Safety
/// Words are a private snapshot with capacity ceil(nx*ny*nz/64), its first
/// source_len words initialized. Coordinates have coordinate_len doubles;
/// output has three writable doubles. All
/// buffers must be aligned, disjoint and live for this ordinary FFM call.
#[no_mangle]
pub unsafe extern "C" fn mattmc_voxel_closest_point(words: *mut u64, source_len: i32,
    nx: i32, ny: i32, nz: i32, coordinates: *const f64, coordinate_len: i32,
    cubes: i32, qx: f64, qy: f64, qz: f64, output: *mut f64) -> i32 {
    if words.is_null() || coordinates.is_null() || output.is_null()
        || words as usize % 8 != 0 || coordinates as usize % 8 != 0
        || output as usize % 8 != 0
        || !(0..=1024).contains(&source_len) || !(0..=7).contains(&cubes)
        || !(1..=256).contains(&nx) || !(1..=256).contains(&ny) || !(1..=256).contains(&nz)
    { return -1; }
    let dims = [nx as usize, ny as usize, nz as usize];
    let cells = dims.iter().product::<usize>();
    if cells > 65536 || coordinate_len != nx + ny + nz + 3
    { return -1; }
    let coords = std::slice::from_raw_parts(coordinates, coordinate_len as usize);
    let mut at = 0;
    for axis in 0..3 {
        if cubes & (1 << axis) == 0 && !coords[at..at + dims[axis] + 1].iter().all(|v| v.is_finite()) { return -2; }
        at += dims[axis] + 1;
    }
    if ![qx, qy, qz].iter().all(|v| v.is_finite()) { return -2; }
    let work = std::slice::from_raw_parts_mut(words, (cells + 63) / 64);
    if (source_len as usize) < work.len() { work[source_len as usize..].fill(0); }
    match super::evaluate::evaluate(work, dims, coords, cubes as u32, [qx, qy, qz]) {
        Some(p) => { std::slice::from_raw_parts_mut(output, 3).copy_from_slice(&p); 1 }
        None => 0,
    }
}

/// Proven full occupancy emits one box. Bounds come from current Java lists.
/// # Safety
/// output is aligned and writable for three doubles for this ordinary call.
#[no_mangle]
pub unsafe extern "C" fn mattmc_voxel_closest_box(qx: f64, qy: f64, qz: f64,
    lx: f64, ly: f64, lz: f64, hx: f64, hy: f64, hz: f64, output: *mut f64) -> i32 {
    if output.is_null() || output as usize % 8 != 0 { return -1; }
    if ![qx,qy,qz,lx,ly,lz,hx,hy,hz].iter().all(|v|v.is_finite()) { return -2; }
    std::slice::from_raw_parts_mut(output,3).copy_from_slice(&[
        super::evaluate::clamp(qx,lx,hx), super::evaluate::clamp(qy,ly,hy), super::evaluate::clamp(qz,lz,hz)]);
    1
}

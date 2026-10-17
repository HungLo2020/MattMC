use super::intersection::Intersection;

unsafe fn publish(hit: Intersection, ray: &[f64; 9], delta: [f64; 3], output: *mut f64) -> i32 {
    if let Some(point) = hit.point(ray, delta) {
        std::slice::from_raw_parts_mut(output, 3).copy_from_slice(&point);
        hit.direction.unwrap() as i32 + 1
    } else { 0 }
}

fn delta(ray: &[f64; 9]) -> Option<[f64; 3]> {
    let result = std::array::from_fn(|a| ray[a + 3] - ray[a]);
    if ray.iter().all(|v| v.is_finite()) && result.iter().all(|v| v.is_finite()) { Some(result) } else { None }
}

/// Return direction ordinal + 1, 0 for miss, -1 invalid metadata, -2 nonfinite
/// inputs requiring Java compatibility. Private bounded endpoint scratch may
/// grow on first use; never callback or retain any caller pointer/input/result.
///
/// # Safety
/// All buffers are aligned, disjoint and live for the downcall. Words have
/// ceil(nx*ny*nz/64) capacity with source_len words initialized. Coordinates
/// have coordinate_len doubles. Ray has nine doubles (start,end,block position),
/// and output has three writable doubles. Only the private words/output mutate.
#[no_mangle]
pub unsafe extern "C" fn mattmc_voxel_ray_clip(words: *mut u64, source_len: i32,
    nx: i32, ny: i32, nz: i32, coordinates: *const f64, coordinate_len: i32,
    cubes: i32, ray: *const f64, output: *mut f64) -> i32 {
    if words.is_null() || coordinates.is_null() || ray.is_null() || output.is_null()
        || [words as usize, coordinates as usize, ray as usize, output as usize].iter().any(|p| p % 8 != 0)
        || !(0..=1024).contains(&source_len) || !(0..=7).contains(&cubes)
        || !(1..=256).contains(&nx) || !(1..=256).contains(&ny) || !(1..=256).contains(&nz)
    { return -1; }
    let dims = [nx as usize, ny as usize, nz as usize];
    let cells = dims.iter().product::<usize>();
    if cells > 65536 || coordinate_len != nx + ny + nz + 3 { return -1; }
    let coords = std::slice::from_raw_parts(coordinates, coordinate_len as usize);
    let ray = &*(ray as *const [f64; 9]);
    let Some(delta) = delta(ray) else { return -2; };
    let Some(ordered) = super::coordinates::ordered(coords, dims, cubes as u32, ray) else { return -2; };
    let work = std::slice::from_raw_parts_mut(words, (cells + 63) / 64);
    if (source_len as usize) < work.len() { work[source_len as usize..].fill(0); }
    publish(super::evaluate::evaluate_validated(work, dims, coords, cubes as u32, ray, delta, ordered), ray, delta, output)
}

/// One proven occupied box, with the same AABB normalization and plane order.
/// # Safety
/// Ray, bounds and output are disjoint aligned buffers of nine, six and three
/// doubles respectively, live throughout the ordinary call. No pointers retained.
#[no_mangle]
pub unsafe extern "C" fn mattmc_voxel_ray_box(ray: *const f64, bounds: *const f64, output: *mut f64) -> i32 {
    if ray.is_null() || bounds.is_null() || output.is_null()
        || [ray as usize, bounds as usize, output as usize].iter().any(|p| p % 8 != 0) { return -1; }
    let ray = &*(ray as *const [f64; 9]);
    let bounds = std::slice::from_raw_parts(bounds, 6);
    let Some(delta) = delta(ray) else { return -2; };
    if !bounds.iter().all(|v| v.is_finite()) { return -2; }
    let mut hit = Intersection::new();
    hit.consider(bounds.try_into().unwrap(), ray, delta);
    publish(hit, ray, delta, output)
}

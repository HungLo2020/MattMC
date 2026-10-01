/// Numeric occupancy join. No allocation, callbacks, or retained pointers.
///
/// # Safety
/// All buffers must be aligned, valid and disjoint for the call. `sizes` covers
/// six i32s; `maps` covers 2*(nx+ny+nz) i32s; `bounds` covers six i32s. Input
/// words cover their supplied lengths, output covers out_len words.
#[no_mangle]
pub unsafe extern "C" fn mattmc_voxel_join(
    a: *const u64,
    a_len: i32,
    b: *const u64,
    b_len: i32,
    sizes: *const i32,
    maps: *const i32,
    nx: i32,
    ny: i32,
    nz: i32,
    truth: i32,
    out: *mut u64,
    out_len: i32,
    bounds: *mut i32,
) -> i32 {
    if a.is_null()
        || b.is_null()
        || sizes.is_null()
        || maps.is_null()
        || out.is_null()
        || bounds.is_null()
        || a as usize % 8 != 0
        || b as usize % 8 != 0
        || sizes as usize % 4 != 0
        || maps as usize % 4 != 0
        || out as usize % 8 != 0
        || bounds as usize % 4 != 0
        || !(0..=4096).contains(&a_len)
        || !(0..=4096).contains(&b_len)
        || !(1..=256).contains(&nx)
        || !(1..=256).contains(&ny)
        || !(1..=256).contains(&nz)
        || !(0..=15).contains(&truth)
        || nx * ny * nz > 262144
        || out_len != (nx * ny * nz + 63) / 64
    {
        return -1;
    }
    let sizes = std::slice::from_raw_parts(sizes, 6);
    if sizes.iter().any(|&n| !(0..=256).contains(&n))
        || sizes[..3].iter().map(|&n| n as i64).product::<i64>() > 262144
        || sizes[3..].iter().map(|&n| n as i64).product::<i64>() > 262144
    {
        return -1;
    }
    super::evaluate::join(
        std::slice::from_raw_parts(a, a_len as usize),
        std::slice::from_raw_parts(b, b_len as usize),
        sizes,
        std::slice::from_raw_parts(maps, 2 * (nx + ny + nz) as usize),
        [nx as usize, ny as usize, nz as usize],
        truth as u32,
        std::slice::from_raw_parts_mut(out, out_len as usize),
        std::slice::from_raw_parts_mut(bounds, 6),
    );
    0
}

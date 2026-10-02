/// Whole packed-grid transform; invalid metadata returns -1 before writing.
///
/// # Safety
/// All buffers are aligned, valid for their supplied lengths and disjoint.
/// `bounds` covers six writable i32s. No allocation or pointer retention occurs.
#[no_mangle]
pub unsafe extern "C" fn mattmc_voxel_rotate(input: *const u64, input_len: i32,
    nx: i32, ny: i32, nz: i32, control: i32, output: *mut u64, output_len: i32,
    bounds: *mut i32) -> i32 {
    if input.is_null() || output.is_null() || bounds.is_null()
        || input as usize % 8 != 0 || output as usize % 8 != 0 || bounds as usize % 4 != 0
        || !(0..=1024).contains(&input_len) || !(1..=256).contains(&nx)
        || !(1..=256).contains(&ny) || !(1..=256).contains(&nz)
        || !(0..=511).contains(&control) { return -1; }
    let dims = [nx as usize, ny as usize, nz as usize];
    let cells = dims.iter().product::<usize>();
    let axes = [(control & 3) as usize, ((control >> 2) & 3) as usize, ((control >> 4) & 3) as usize];
    if cells > 65536 || output_len != ((cells + 63) / 64) as i32
        || axes.iter().any(|&a| a > 2) || axes[0] == axes[1] || axes[0] == axes[2] || axes[1] == axes[2]
        { return -1; }
    super::transform::transform(std::slice::from_raw_parts(input, input_len as usize), dims,
        axes, (control >> 6) as u32, std::slice::from_raw_parts_mut(output, output_len as usize),
        std::slice::from_raw_parts_mut(bounds, 6));
    0
}

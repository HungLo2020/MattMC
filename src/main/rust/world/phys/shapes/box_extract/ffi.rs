/// Extract ordered greedy boxes from a borrowed, mutable private snapshot.
/// No allocation, callbacks or retained pointers.
///
/// # Safety
/// `words` is aligned and valid for ceil(nx*ny*nz/64) u64s, with its first
/// `source_len` words initialized (extra words beyond the grid are ignored).
/// `output` is aligned, writable for `output_len` i32s and disjoint from words.
#[no_mangle]
pub unsafe extern "C" fn mattmc_voxel_boxes(
    words: *mut u64, source_len: i32, nx: i32, ny: i32, nz: i32,
    output: *mut i32, output_len: i32,
) -> i32 {
    if words.is_null() || output.is_null()
        || words as usize % 8 != 0 || output as usize % 4 != 0
        || !(0..=1024).contains(&source_len)
        || !(1..=256).contains(&nx) || !(1..=256).contains(&ny) || !(1..=256).contains(&nz)
    { return -1; }
    let cells = nx as usize * ny as usize * nz as usize;
    let capacity = nx as usize * ny as usize * ((nz as usize + 1) / 2);
    if cells > 65536 || !(0..=393216).contains(&output_len)
        || (output_len as usize) < capacity * 6 { return -1; }
    let length = (cells + 63) / 64;
    let work = std::slice::from_raw_parts_mut(words, length);
    if (source_len as usize) < length { work[source_len as usize..].fill(0); }
    super::extract::extract(work, [nx as usize, ny as usize, nz as usize],
        std::slice::from_raw_parts_mut(output, output_len as usize)) as i32
}

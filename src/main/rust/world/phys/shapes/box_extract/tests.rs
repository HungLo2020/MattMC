use super::{extract::extract, ffi::mattmc_voxel_boxes};

// Independent per-cell translation of the original Java loop, without word searches.
fn original(input: &[u64], d: [usize; 3]) -> Vec<i32> {
    let [nx, ny, nz] = d;
    let mut cells: Vec<bool> = (0..nx * ny * nz)
        .map(|i| input.get(i / 64).unwrap_or(&0) & (1 << (i % 64)) != 0).collect();
    let index = |x, y, z| (x * ny + y) * nz + z;
    let strip = |cells: &[bool], x: usize, y: usize, a: usize, b: usize| {
        x < nx && y < ny && (a..b).all(|z| cells[index(x, y, z)])
    };
    let mut output = Vec::new();
    for y in 0..ny { for x in 0..nx {
        let mut start = None;
        for z in 0..=nz {
            if z < nz && cells[index(x, y, z)] {
                if start.is_none() { start = Some(z); }
            } else if let Some(a) = start {
                for zz in a..z { cells[index(x, y, zz)] = false; }
                let mut xx = x;
                let mut yy = y;
                while strip(&cells, xx + 1, y, a, z) {
                    xx += 1;
                    for zz in a..z { cells[index(xx, y, zz)] = false; }
                }
                while (x..=xx).all(|xxx| strip(&cells, xxx, yy + 1, a, z)) {
                    yy += 1;
                    for xxx in x..=xx { for zz in a..z { cells[index(xxx, yy, zz)] = false; } }
                }
                output.extend([x as i32, y as i32, a as i32, xx as i32 + 1, yy as i32 + 1, z as i32]);
                start = None;
            }
        }
    } }
    output
}

fn check(input: &[u64], d: [usize; 3]) {
    let cells = d.iter().product::<usize>();
    let length = (cells + 63) / 64;
    let mut work = vec![0; length.max(input.len())];
    work[..input.len()].copy_from_slice(input);
    let original = original(input, d);
    let mut output = vec![-7; cells * 6];
    let count = extract(&mut work[..length], d, &mut output);
    assert_eq!(&output[..count * 6], &original, "dims={d:?}, input={input:?}");
    assert!((0..cells).all(|i| work[i / 64] & (1 << (i % 64)) == 0));
    work[..input.len()].copy_from_slice(input);
    let count = unsafe { mattmc_voxel_boxes(work.as_mut_ptr(), input.len() as i32,
        d[0] as i32, d[1] as i32, d[2] as i32, output.as_mut_ptr(), output.len() as i32) };
    assert!(count >= 0);
    assert_eq!(&output[..count as usize * 6], &original);
}

#[test]
fn exhaustive_small_grids_and_cross_word_greedy_order() {
    for d in [[2, 2, 2], [2, 2, 3], [3, 2, 3]] {
        for bits in 0..(1u64 << d.iter().product::<usize>()) { check(&[bits], d); }
    }
    let mut seed = 1977u64;
    for d in [[3, 3, 5], [17, 9, 7], [1, 1, 65], [8, 8, 8], [16, 16, 16], [256, 1, 256], [1, 256, 256]] {
        let words = (d.iter().product::<usize>() + 63) / 64;
        for kind in 0..7 {
            let input: Vec<u64> = (0..words).map(|i| {
                seed ^= seed << 13; seed ^= seed >> 7; seed ^= seed << 17;
                match kind { 0 => 0, 1 => u64::MAX, 2 => 0x5555555555555555,
                    3 => 0xaaaaaaaaaaaaaaaa, 4 => 1 << (i % 64), _ => seed }
            }).collect();
            check(&input, d);
        }
        check(&[], d); // Missing compressed words are zero, despite stale workspace.
    }
}

#[test]
fn ffi_rejects_invalid_metadata_before_any_buffer_mutation() {
    let mut words = [u64::MAX; 1024]; let saved = words;
    let mut output = vec![-77; 512 * 6];
    for (len, x, y, z, capacity) in [(-1,8,8,8,3072),(1025,8,8,8,3072),
        (8,0,8,8,3072),(8,257,8,8,3072),(8,256,256,256,3072),
        (8,8,8,8,1535),(8,8,8,8,-1),(8,8,8,8,393217)] {
        assert_eq!(unsafe { mattmc_voxel_boxes(words.as_mut_ptr(),len,x,y,z,output.as_mut_ptr(),capacity) }, -1);
    }
    assert_eq!(unsafe { mattmc_voxel_boxes(std::ptr::null_mut(),0,8,8,8,output.as_mut_ptr(),3072) }, -1);
    assert_eq!(unsafe { mattmc_voxel_boxes(words.as_mut_ptr(),8,8,8,8,std::ptr::null_mut(),3072) }, -1);
    let unaligned = unsafe { (words.as_mut_ptr() as *mut u8).add(1) as *mut u64 };
    assert_eq!(unsafe { mattmc_voxel_boxes(unaligned,8,8,8,8,output.as_mut_ptr(),3072) }, -1);
    assert_eq!(words, saved); assert!(output.iter().all(|&n| n == -77));
}

#[test]
fn full_and_interior_cavities_match_original_order_and_reject_changed_cells() {
    for dims in [[4,16,4], [8,8,8], [16,16,16], [5,6,32], [5,7,64]] {
        let [nx,ny,nz]=dims;
        let cells=nx*ny*nz;
        for low in [[1,1,1],[1,2,1],[2,1,2]] {
            for high in [[nx-1,ny-1,nz-1],[nx-1,ny-2,nz-1],[nx-2,ny-1,nz-2]] {
                if (0..3).any(|a| low[a]>=high[a]) {continue;}
                let mut words=vec![u64::MAX;(cells+63)/64];
                for x in low[0]..high[0] { for y in low[1]..high[1] { for z in low[2]..high[2] {
                    let i=(x*ny+y)*nz+z; words[i/64]&=!(1u64<<(i%64));
                } } }
                check(&words,dims);
                for i in [0,(low[0]*ny+low[1])*nz+low[2],cells/2,cells-1] {
                    words[i/64]^=1u64<<(i%64);check(&words,dims);words[i/64]^=1u64<<(i%64);
                }
            }
        }
    }
}

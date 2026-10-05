fn valid_shape(shape: &[i32], len: i32) -> bool {
    let row = shape[3] as i64 * shape[4] as i64;
    shape[3] > 0 && shape[4] > 0 && row <= len as i64 && len as i64 % row == 0
}

/// # Safety
/// `centers` contains 12 readable [cache index,x,y,z] tuples. `out` has eight
/// writable i32s. Both pointers are aligned, valid and do not overlap.
#[no_mangle]
pub unsafe extern "C" fn mattmc_aquifer_nearest(
    x: i32,
    y: i32,
    z: i32,
    centers: *const i32,
    out: *mut i32,
) -> i32 {
    if centers.is_null() || out.is_null() {
        return -1;
    }
    let centers = unsafe { std::slice::from_raw_parts(centers.cast::<[i32; 4]>(), 12) };
    let out = unsafe { std::slice::from_raw_parts_mut(out, 8) };
    super::nearest::point(x, y, z, centers, out);
    0
}

/// # Safety
/// Frame has 20 writable i32s, values two writable f64s, cache `count*3`
/// readable i32s. Buffers are aligned and disjoint; no pointer is retained.
#[no_mangle]
pub unsafe extern "C" fn mattmc_aquifer_step(
    frame: *mut i32,
    values: *mut f64,
    cache: *const i32,
    count: i32,
) -> i32 {
    if frame.is_null() || values.is_null() || cache.is_null() || !(1..=65536).contains(&count) {
        return -1;
    }
    let f = unsafe { std::slice::from_raw_parts_mut(frame, 20) };
    let custom = f[16] != 0;
    super::decision::step(
        f,
        unsafe { std::slice::from_raw_parts_mut(values, 2) },
        unsafe { std::slice::from_raw_parts(cache, count as usize * 3) },
        custom,
    )
}

/// # Safety
/// `frame` is an aligned, exclusively writable array of 22 i32s. No pointer is
/// retained and a call performs at most thirteen surface visits before yielding.
#[no_mangle]
pub unsafe extern "C" fn mattmc_aquifer_fluid(frame: *mut i32, noise: f64) -> i32 {
    if frame.is_null() {
        return -1;
    }
    super::fluid::step(unsafe { std::slice::from_raw_parts_mut(frame, 22) }, noise)
}

/// # Safety
/// Frame has 26 writable i32s; policy eight readable i32s; surface width*height*2
/// readable i32s, where width=frame[24], height=frame[25]. Buffers are disjoint,
/// aligned and borrowed for this call. Only pure built-in sources may use it.
#[no_mangle]
pub unsafe extern "C" fn mattmc_aquifer_fluid_pure(
    frame: *mut i32,
    noise: f64,
    policy: *const i32,
    surface: *const i32,
) -> i32 {
    if frame.is_null() || policy.is_null() || surface.is_null() {
        return -1;
    }
    let f = unsafe { std::slice::from_raw_parts_mut(frame, 26) };
    if !(1..=128).contains(&f[24]) || !(1..=128).contains(&f[25]) {
        return -1;
    }
    let size = (f[24] * f[25] * 2) as usize;
    super::fluid::pure_step(
        f,
        noise,
        unsafe { std::slice::from_raw_parts(policy, 8) },
        unsafe { std::slice::from_raw_parts(surface, size) },
    )
}

/// # Safety
/// Frame has 32 writable i32s; values two writable f64s. Density has w*w*h
/// readable doubles and output twice that many writable i32s (w=frame[21],
/// h=frame[22]). Grid and cache have len i64s and len*3 i32s; shape has five
/// i32s. Barrier is null or a live validated immutable noise state. All buffers
/// are aligned and disjoint. No pointer escapes; each call processes <=128 points.
#[no_mangle]
pub unsafe extern "C" fn mattmc_aquifer_materials(
    frame: *mut i32,
    values: *mut f64,
    density: *const f64,
    out: *mut i32,
    grid: *const i64,
    shape: *const i32,
    len: i32,
    cache: *const i32,
    barrier: *const super::super::synth::State,
    xz: f64,
    ys: f64,
) -> i32 {
    if frame.is_null()
        || values.is_null()
        || density.is_null()
        || out.is_null()
        || grid.is_null()
        || shape.is_null()
        || cache.is_null()
        || !(1..=65536).contains(&len)
    {
        return -1;
    }
    let f = unsafe { std::slice::from_raw_parts_mut(frame, 32) };
    if !(1..=16).contains(&f[21]) || !(1..=32).contains(&f[22]) || f[21] * f[21] * f[22] > 2048 {
        return -1;
    }
    let count = (f[21] * f[21] * f[22]) as usize;
    let grid_shape = unsafe { std::slice::from_raw_parts(shape, 5) };
    if !valid_shape(grid_shape, len) {
        return -1;
    }
    unsafe {
        super::cell::materials(
            f,
            std::slice::from_raw_parts_mut(values, 2),
            std::slice::from_raw_parts(density, count),
            std::slice::from_raw_parts_mut(out, count * 2),
            std::slice::from_raw_parts(grid, len as usize),
            grid_shape,
            std::slice::from_raw_parts(cache, len as usize * 3),
            barrier,
            xz,
            ys,
        )
    }
}

/// Batched nearest search for a bounded terrain cell. Grid is densely packed
/// Y,Z,X; each location is Java's packed BlockPos. Missing centers are rejected.
/// # Safety
/// `grid` has `grid_len` i64s, `shape` has [minX,minY,minZ,sizeX,sizeZ], and `out`
/// has width*width*height*8 i32s. Aligned, disjoint, borrowed for this call only.
#[no_mangle]
pub unsafe extern "C" fn mattmc_aquifer_cell(
    x: i32,
    y: i32,
    z: i32,
    width: i32,
    height: i32,
    grid: *const i64,
    grid_len: i32,
    shape: *const i32,
    out: *mut i32,
) -> i32 {
    if grid.is_null()
        || shape.is_null()
        || out.is_null()
        || !(1..=16).contains(&width)
        || !(1..=32).contains(&height)
        || width * width * height > 2048
        || !(1..=65536).contains(&grid_len)
    {
        return -1;
    }
    let grid = unsafe { std::slice::from_raw_parts(grid, grid_len as usize) };
    let s = unsafe { std::slice::from_raw_parts(shape, 5) };
    if !valid_shape(s, grid_len) {
        return -1;
    }
    let output =
        unsafe { std::slice::from_raw_parts_mut(out, (width * width * height * 8) as usize) };
    for iy in 0..height {
        for ix in 0..width {
            for iz in 0..width {
                let px = x.wrapping_add(ix);
                let py = y.wrapping_add(iy);
                let pz = z.wrapping_add(iz);
                let gx = px.wrapping_sub(5) >> 4;
                let gy = py.wrapping_add(1).div_euclid(12);
                let gz = pz.wrapping_sub(5) >> 4;
                let mut centers = [[0; 4]; 12];
                let mut n = 0;
                for dx in 0..2 {
                    for dy in -1..2 {
                        for dz in 0..2 {
                            let rx = gx.wrapping_add(dx).wrapping_sub(s[0]);
                            let ry = gy.wrapping_add(dy).wrapping_sub(s[1]);
                            let rz = gz.wrapping_add(dz).wrapping_sub(s[2]);
                            let index =
                                (ry as i64 * s[4] as i64 + rz as i64) * s[3] as i64 + rx as i64;
                            if rx < 0
                                || rx >= s[3]
                                || ry < 0
                                || rz < 0
                                || rz >= s[4]
                                || index < 0
                                || index >= grid_len as i64
                            {
                                return -2;
                            }
                            let pos = grid[index as usize];
                            if pos == i64::MAX {
                                return -3;
                            }
                            centers[n] = [
                                index as i32,
                                (pos >> 38) as i32,
                                ((pos << 52) >> 52) as i32,
                                ((pos << 26) >> 38) as i32,
                            ];
                            n += 1;
                        }
                    }
                }
                let offset = ((iy * width + ix) * width + iz) as usize * 8;
                super::nearest::point(px, py, pz, &centers, &mut output[offset..offset + 8]);
            }
        }
    }
    0
}

/// Native `cellLocations` for built-in positional factories (`kind` 1 Xoroshiro
/// with seedLo/seedHi, 2 Legacy with seed): fills the missing aquifer centres a
/// cell batch reads. Grid and shape follow `mattmc_aquifer_cell`. Returns 0, or
/// -1 for invalid arguments or a cell outside the grid.
/// # Safety
/// `grid` has `len` writable i64s and `shape` five readable i32s, aligned,
/// disjoint and borrowed for this call only.
#[no_mangle]
pub unsafe extern "C" fn mattmc_aquifer_locations(
    grid: *mut i64,
    len: i32,
    shape: *const i32,
    kind: i32,
    a: i64,
    b: i64,
    x: i32,
    y: i32,
    z: i32,
    width: i32,
    height: i32,
) -> i32 {
    let Some(random) = crate::world::level::levelgen::random::Positional::from_abi(kind, a, b) else {
        return -1;
    };
    if grid.is_null() || shape.is_null() || !(1..=65536).contains(&len) || !(1..=16).contains(&width) || !(1..=32).contains(&height) {
        return -1;
    }
    let grid = unsafe { std::slice::from_raw_parts_mut(grid, len as usize) };
    let shape = unsafe { std::slice::from_raw_parts(shape, 5) };
    if !valid_shape(shape, len) {
        return -1;
    }
    if super::locations::fill_cell_locations(grid, shape, random, x, y, z, width, height) {
        0
    } else {
        -1
    }
}

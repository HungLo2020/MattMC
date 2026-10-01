use super::{
    evaluate::point,
    layout::{Geometry, KERNEL_SIZE, MAX_ENTRIES},
};

/// # Safety
/// Arrays have the declared readable/writable lengths and natural alignment;
/// output does not alias inputs. Java retains all allocations during this call.
#[no_mangle]
pub unsafe extern "C" fn mattmc_beardifier_cell(
    data: *const i32,
    ints: u64,
    kernel: *const f32,
    output: *mut f64,
    count: u64,
    x: i32,
    y: i32,
    z: i32,
    width: i32,
    height: i32,
) -> i32 {
    if data.is_null()
        || kernel.is_null()
        || output.is_null()
        || data as usize % 4 != 0
        || kernel as usize % 4 != 0
        || output as usize % 8 != 0
        || ints < 8
        || ints > (8 + MAX_ENTRIES * 11) as u64
        || !(1..=64).contains(&width)
        || !(1..=4096).contains(&height)
        || count > 4096
        || count != width as u64 * width as u64 * height as u64
    {
        return -1;
    }
    let data = unsafe { std::slice::from_raw_parts(data, ints as usize) };
    let Some(geometry) = Geometry::read(data) else {
        return -2;
    };
    let kernel = unsafe { std::slice::from_raw_parts(kernel, KERNEL_SIZE) };
    let output = unsafe { std::slice::from_raw_parts_mut(output, count as usize) };
    let mut index = 0;
    for dy in (0..height).rev() {
        for dx in 0..width {
            for dz in 0..width {
                output[index] = point(
                    &geometry,
                    kernel,
                    x.wrapping_add(dx),
                    y.wrapping_add(dy),
                    z.wrapping_add(dz),
                );
                index += 1;
            }
        }
    }
    0
}

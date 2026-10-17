use super::reduction::{self, LIMIT};
use std::{
    panic::{catch_unwind, AssertUnwindSafe},
    ptr, slice,
};
fn valid<T>(pointer: *const T, count: usize) -> bool {
    count == 0 || !pointer.is_null() && pointer as usize % std::mem::align_of::<T>() == 0
}
/// Borrowed CPU spans only. Complete reduction precedes every destination write.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_render_reduce(
    input: *const u64,
    count: i32,
    materials: *const i32,
    target: i32,
    output: *mut u64,
    size: i32,
    out_materials: *mut i32,
) -> i32 {
    catch_unwind(AssertUnwindSafe(|| {
        if count < 0 || size < 0 || count as usize > LIMIT || size as usize > LIMIT {
            return 0;
        }
        let (count, size) = (count as usize, size as usize);
        if !valid(input, count)
            || !valid(output, size)
            || (!materials.is_null() && !valid(materials, count))
            || (!out_materials.is_null() && !valid(out_materials, size))
            || materials.is_null() != out_materials.is_null()
        {
            return 0;
        }
        let source = if count == 0 {
            &[]
        } else {
            slice::from_raw_parts(input, count)
        };
        let semantic = if materials.is_null() {
            None
        } else {
            Some(slice::from_raw_parts(materials, count))
        };
        let result = reduction::with_reduced(source, semantic, target, size, |result| {
            // Sources may alias destination. Reduction acquired all source values.
            for (i, &(point, material)) in result.iter().enumerate() {
                ptr::write(output.add(i), point);
                if !out_materials.is_null() {
                    ptr::write(out_materials.add(i), material);
                }
            }
        });
        i32::from(result.is_ok())
    }))
    .unwrap_or(0)
}

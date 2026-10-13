//! Primary CPU render-source storage. Stable point spans feed reduction and box
//! policy directly; no Java arrays, GPU state, or global source cache live here.
use std::{
    panic::{catch_unwind, AssertUnwindSafe},
    ptr, slice,
};

const COLUMNS: usize = 64 * 64;
pub struct Source {
    points: Box<[u64]>,
    materials: Option<Box<[i16]>>,
    variants: Option<Box<[u8]>>,
    uniform: Option<Box<[u8]>>,
}
impl Source {
    /// Zeroes the first `count` points (a conversion scratch window).
    pub(crate) fn conversion_clear(&mut self, count: usize) {
        let count = count.min(self.points.len());
        self.points[..count].fill(0);
    }
    pub(crate) fn points(&self) -> &[u64] {
        &self.points
    }
    pub(crate) fn materials(&self) -> Option<&[i16]> {
        self.materials.as_deref()
    }
    pub(crate) fn variants(&self) -> Option<&[u8]> {
        self.variants.as_deref()
    }
    pub(crate) fn uniform(&self) -> Option<&[u8]> {
        self.uniform.as_deref()
    }
    fn conversion_index(&self, window: u64, index: i32) -> Option<usize> {
        let first = window as u32 as usize;
        let count = (window >> 32) as u32 as usize;
        if index < 0 || index as usize >= count || first > self.points.len()
            || count > self.points.len() - first { return None; }
        Some(first + index as usize)
    }
    pub(crate) fn conversion_read(&self, window: u64, index: i32) -> Option<u64> {
        self.conversion_index(window, index).map(|i| self.points[i])
    }
    pub(crate) fn conversion_write(&mut self, window: u64, index: i32, value: u64) -> bool {
        let Some(i) = self.conversion_index(window, index) else { return false; };
        self.points[i] = value;
        true
    }
    /// A boxed source cannot resize. Resolve bounded neighbor windows directly
    /// while the caller holds its scoped owner, without four Java span adapters.
    pub(crate) fn window(&self, packed: u64) -> Result<(*const u64, i32), ()> {
        if packed == u64::MAX {
            return Ok((ptr::null(), -1));
        }
        let first = packed as u32 as usize;
        let count = (packed >> 32) as u32 as usize;
        if count > super::reduction::LIMIT
            || first > self.points.len()
            || count > self.points.len() - first
        {
            return Err(());
        }
        Ok((unsafe { self.points.as_ptr().add(first) }, count as i32))
    }
}
fn zeros<T: Default + Clone>(count: usize) -> Result<Box<[T]>, ()> {
    let mut values = Vec::new();
    values.try_reserve_exact(count).map_err(|_| ())?;
    values.resize(count, T::default());
    Ok(values.into_boxed_slice())
}
fn valid<T>(p: *const T) -> bool {
    !p.is_null() && p as usize % std::mem::align_of::<T>() == 0
}

/// Output: stable point address and count. Caller excludes concurrent mutations
/// and releases exactly once, after all CPU borrowers have completed.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_render_source_create(height: i32, out: *mut u64) -> *mut Source {
    catch_unwind(AssertUnwindSafe(|| {
        if height < 0 || !valid(out) {
            return ptr::null_mut();
        }
        let Some(count) = (height as usize)
            .checked_mul(COLUMNS)
            .filter(|&n| n <= i32::MAX as usize)
        else {
            return ptr::null_mut();
        };
        let Ok(points) = zeros(count) else {
            return ptr::null_mut();
        };
        let source = Box::new(Source {
            points,
            materials: None,
            variants: None,
            uniform: None,
        });
        ptr::write(out, source.points.as_ptr() as u64);
        ptr::write(out.add(1), count as u64);
        Box::into_raw(source)
    }))
    .unwrap_or(ptr::null_mut())
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_render_source_release(owner: *mut Source) {
    if valid(owner) {
        drop(Box::from_raw(owner));
    }
}
/// Material and variant planes are allocated together on the first nonzero
/// write, not for color-only sources. Their addresses never change thereafter.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_render_source_semantics(
    owner: *mut Source,
    out: *mut u64,
) -> i32 {
    catch_unwind(AssertUnwindSafe(|| {
        if !valid(owner) || !valid(out) {
            return 0;
        }
        let source = &mut *owner;
        if source.materials.is_none() {
            let Ok(materials) = zeros(source.points.len()) else {
                return -1;
            };
            let Ok(variants) = zeros(source.points.len()) else {
                return -1;
            };
            source.materials = Some(materials);
            source.variants = Some(variants);
        }
        ptr::write(out, source.materials.as_ref().unwrap().as_ptr() as u64);
        ptr::write(
            out.add(1),
            source.variants.as_ref().unwrap().as_ptr() as u64,
        );
        1
    }))
    .unwrap_or(0)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_render_source_uniform(owner: *mut Source) -> *mut u8 {
    catch_unwind(AssertUnwindSafe(|| {
        if !valid(owner) {
            return ptr::null_mut();
        }
        let source = &mut *owner;
        if source.uniform.is_none() {
            let Ok(uniform) = zeros(COLUMNS) else {
                return ptr::null_mut();
            };
            source.uniform = Some(uniform);
        }
        source.uniform.as_mut().unwrap().as_mut_ptr()
    }))
    .unwrap_or(ptr::null_mut())
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_render_source_clear_semantics(
    owner: *mut Source,
    column: i32,
    height: i32,
) -> i32 {
    if !valid(owner) || column < 0 || column as usize >= COLUMNS || height < 0 {
        return 0;
    }
    let source = &mut *owner;
    let height = height as usize;
    let Some(first) = (column as usize).checked_mul(height) else {
        return 0;
    };
    let Some(end) = first
        .checked_add(height)
        .filter(|&n| n <= source.points.len())
    else {
        return 0;
    };
    if let Some(materials) = &mut source.materials {
        materials[first..end].fill(0);
    }
    if let Some(variants) = &mut source.variants {
        variants[first..end].fill(0);
    }
    if let Some(uniform) = &mut source.uniform {
        uniform[column as usize] = 0;
    }
    1
}
/// Small bulk policies operate on either canonical heap or native CPU spans.
/// op=0 fills, op=1 computes Java's array hash, op=2 tests nonzero height.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_render_span(
    points: *mut u64,
    count: i32,
    op: i32,
    value: u64,
) -> i32 {
    if count < 0 || count != 0 && !valid(points) {
        return 0;
    }
    if count == 0 {
        return if op == 1 { 1 } else { 0 };
    }
    // Do not construct a mutable slice for read-only queries or while an aliased
    // source is borrowed by a caller. Fill is the only mutating operation.
    match op {
        0 => {
            slice::from_raw_parts_mut(points, count as usize).fill(value);
            1
        }
        1 => slice::from_raw_parts(points, count as usize)
            .iter()
            .fold(1i32, |h, &p| {
                h.wrapping_mul(31).wrapping_add((p ^ (p >> 32)) as i32)
            }),
        2 => i32::from(
            slice::from_raw_parts(points, count as usize)
                .iter()
                .any(|&p| (p >> 8) & 0xff_ffff != 0),
        ),
        _ => 0,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn source_windows_distinguish_absent_empty_and_bounded_ranges() {
        unsafe {
            let mut out = [0; 2];
            let p = mattmc_dh_render_source_create(1, out.as_mut_ptr());
            let source = &*p;
            assert_eq!(source.window(u64::MAX).unwrap(), (ptr::null(), -1));
            assert_eq!(source.window(4096).unwrap().1, 0);
            assert_eq!(source.window(4095 | (1 << 32)).unwrap().1, 1);
            assert!(source.window(4095 | (2 << 32)).is_err());
            assert!(source.window(4097).is_err());
            assert!(source.window(4097 << 32).is_err());
            mattmc_dh_render_source_release(p);
        }
    }
    #[test]
    fn lazy_planes_and_column_clear_preserve_points_and_neighbors() {
        unsafe {
            let mut out = [0; 2];
            let p = mattmc_dh_render_source_create(4, out.as_mut_ptr());
            assert!(!p.is_null());
            assert_eq!(out[1], 16384);
            assert!((*p).materials.is_none());
            assert_eq!(mattmc_dh_render_source_clear_semantics(p, 0, 4), 1);
            assert!((*p).materials.is_none());
            assert_eq!(mattmc_dh_render_source_semantics(p, out.as_mut_ptr()), 1);
            let material = out[0] as *mut i16;
            let variant = out[1] as *mut u8;
            for i in 0..12 {
                *material.add(i) = -1;
                *variant.add(i) = 2;
                (*p).points[i] = i as u64 + 1;
            }
            let uniform = mattmc_dh_render_source_uniform(p);
            *uniform.add(1) = 1;
            assert_eq!(mattmc_dh_render_source_clear_semantics(p, 1, 4), 1);
            for i in 0..12 {
                assert_eq!(*material.add(i), if (4..8).contains(&i) { 0 } else { -1 });
                assert_eq!(*variant.add(i), if (4..8).contains(&i) { 0 } else { 2 });
                assert_eq!((*p).points[i], i as u64 + 1);
            }
            assert_eq!(*uniform.add(1), 0);
            assert_eq!(mattmc_dh_render_source_clear_semantics(p, 4096, 4), 0);
            let previous = out;
            assert_eq!(mattmc_dh_render_source_semantics(p, out.as_mut_ptr()), 1);
            assert_eq!(out, previous);
            mattmc_dh_render_source_release(p);
        }
    }
    #[test]
    fn bulk_queries_match_wrapping_java_hash_and_do_not_mutate() {
        unsafe {
            let mut data = [u64::MAX, 0x1234_5678_abcdef00, 0];
            let old = data;
            let expected = data.iter().fold(1i32, |h, &p| {
                h.wrapping_mul(31).wrapping_add((p ^ (p >> 32)) as i32)
            });
            assert_eq!(mattmc_dh_render_span(data.as_mut_ptr(), 3, 1, 0), expected);
            assert_eq!(data, old);
            assert_eq!(mattmc_dh_render_span(data.as_mut_ptr(), 3, 0, 0), 1);
            assert_eq!(data, [0; 3]);
            assert_eq!(mattmc_dh_render_span(data.as_mut_ptr(), 3, 2, 0), 0);
            data[1] = (19 << 20) | (19 << 8);
            assert_eq!(mattmc_dh_render_span(data.as_mut_ptr(), 3, 2, 0), 1);
            data[1] = 1 << 20;
            assert_eq!(mattmc_dh_render_span(data.as_mut_ptr(), 3, 2, 0), 1);
            assert_eq!(mattmc_dh_render_span(ptr::null_mut(), 0, 1, 0), 1);
        }
    }
}

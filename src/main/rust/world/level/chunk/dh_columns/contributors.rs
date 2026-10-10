//! Immutable, bounded four-column semantic footprints and material remapping.
//! These own their values; source mutation/unload cannot alter a retained view.
pub(crate) struct Snapshot {
    points: Box<[u64]>,
    offsets: [usize; 5],
    present: [bool; 4],
}
impl Snapshot {
    pub(crate) fn descriptors(&self) -> [usize; 8] {
        let mut result = [0; 8];
        for index in 0..4 {
            if self.present[index] {
                result[index * 2] = self.points.as_ptr().wrapping_add(self.offsets[index]) as usize;
                result[index * 2 + 1] = self.offsets[index + 1] - self.offsets[index];
            } else { result[index * 2 + 1] = usize::MAX; }
        }
        result
    }
}
pub(crate) fn capture(columns: [Option<&[u64]>; 4], remap: Option<&[i32]>, limit: usize) -> Option<Snapshot> {
    if limit > 4096 { return None; }
    let size: usize = columns.iter().flatten().map(|c| c.len().min(limit)).sum();
    let mut points = Vec::new();
    points.try_reserve_exact(size).ok()?;
    let mut offsets = [0; 5];
    let present = columns.map(|c| c.is_some());
    for (index, column) in columns.into_iter().enumerate() {
        if let Some(column) = column {
            for &point in &column[..column.len().min(limit)] {
                let value = if let Some(remap) = remap {
                    let id = *remap.get(point as u32 as usize)?;
                    // Match FullDataPointUtil.remap's signed Java int OR exactly.
                    point & !(u32::MAX as u64) | id as i64 as u64
                } else { point };
                points.push(value);
            }
        }
        offsets[index + 1] = points.len();
    }
    Some(Snapshot { points: points.into_boxed_slice(), offsets, present })
}

unsafe fn column<'a>(pointer: *const u64, count: i32) -> Option<Option<&'a [u64]>> {
    if count == -1 { return Some(None); }
    if count < 0 || count != 0 && (pointer.is_null() || pointer as usize % 8 != 0) { return None; }
    Some(Some(if count == 0 { &[] } else { std::slice::from_raw_parts(pointer, count as usize) }))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_contributors_create(
    a: *const u64, an: i32, b: *const u64, bn: i32, c: *const u64, cn: i32, d: *const u64, dn: i32,
    remap: *const i32, remap_count: i32, limit: i32,
) -> *mut Snapshot {
    if !(0..=4096).contains(&limit) || remap_count < -1
        || remap_count > 0 && (remap.is_null() || remap as usize % 4 != 0) { return std::ptr::null_mut(); }
    let (Some(a), Some(b), Some(c), Some(d)) = (column(a, an), column(b, bn), column(c, cn), column(d, dn)) else { return std::ptr::null_mut(); };
    let remap = if remap_count == -1 { None }
        else { Some(if remap_count == 0 { &[][..] } else { std::slice::from_raw_parts(remap, remap_count as usize) }) };
    capture([a, b, c, d], remap, limit as usize).map_or(std::ptr::null_mut(), |snapshot| Box::into_raw(Box::new(snapshot)))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_contributors_view(owner: *const Snapshot, output: *mut usize) -> i32 {
    if owner.is_null() || output.is_null() || output as usize % std::mem::align_of::<usize>() != 0 { return 0; }
    std::slice::from_raw_parts_mut(output, 8).copy_from_slice(&(*owner).descriptors());
    1
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_contributors_release(owner: *mut Snapshot) {
    if !owner.is_null() { drop(Box::from_raw(owner)); }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn footprints_preserve_null_empty_limits_signed_remaps_and_independent_values() {
        let mut input = [1 | 4u64 << 32 | 7u64 << 44 | 12u64 << 60, 0, u32::MAX as u64];
        let snapshot = capture([Some(&input), None, Some(&[]), Some(&input)], Some(&[9, 3]), 2).unwrap();
        assert_eq!(snapshot.present, [true, false, true, true]);
        assert_eq!(snapshot.offsets, [0, 2, 2, 2, 4]);
        let expected = [3 | 4u64 << 32 | 7u64 << 44 | 12u64 << 60, 9];
        assert_eq!(&snapshot.points[..2], expected); assert_eq!(&snapshot.points[2..], expected);
        input[0] = 99; assert_eq!(snapshot.points[0], expected[0]);
        assert!(capture([Some(&input), None, None, None], Some(&[0, 1]), 3).is_none());
        assert_eq!(capture([Some(&[0]), None, None, None], Some(&[-1]), 1).unwrap().points[0], u64::MAX);
        assert!(capture([None; 4], None, 4097).is_none());
        assert!(capture([Some(&input); 4], Some(&[]), 0).unwrap().points.is_empty());
    }
}

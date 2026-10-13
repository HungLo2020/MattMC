//! Immutable, bounded four-column semantic footprints and material remapping.
//! These own their values; source mutation/unload cannot alter a retained view.
pub(crate) struct Snapshot {
    points: Box<[u64]>,
    offsets: [usize; 5],
    present: [bool; 4],
}
impl Snapshot {
    /// The four columns as borrowed spans; `None` is a null column.
    pub(crate) fn spans(&self) -> [Option<&[u64]>; 4] {
        std::array::from_fn(|i| self.present[i].then(|| &self.points[self.offsets[i]..self.offsets[i + 1]]))
    }
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
    capture([a, b, c, d], remap, limit as usize).map_or(std::ptr::null_mut(), |snapshot| std::sync::Arc::into_raw(std::sync::Arc::new(snapshot)) as *mut Snapshot)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_contributors_view(owner: *const Snapshot, output: *mut usize) -> i32 {
    if owner.is_null() || output.is_null() || output as usize % std::mem::align_of::<usize>() != 0 { return 0; }
    std::slice::from_raw_parts_mut(output, 8).copy_from_slice(&(*owner).descriptors());
    1
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_contributors_release(owner: *mut Snapshot) {
    if !owner.is_null() { drop(std::sync::Arc::from_raw(owner as *const Snapshot)); }
}

/// Saved V4 sidecar limit (`MAX_SEMANTIC_HORIZONTAL_CONTRIBUTOR_POINTS`).
const SAVED_LIMIT: i32 = 256;
/// One completed cell: its index, then each column's start offset or `None`.
pub(super) type Cell = (usize, [Option<(usize, usize)>; 4]);

/// `readSemanticHorizontalContributorsFromBlob` over a whole decompressed
/// blob: cells x-major, a presence byte (nonzero is present), then four
/// signed big-endian sizes (negative is null), each followed by that many
/// big-endian points. Trailing bytes are ignored, as in Java.
/// Returns the completed cells and the stopping status: 0 complete,
/// 1 end of input, 2 a size over the saved bound (with that size).
pub(super) fn parse(bytes: &[u8], points: &mut Vec<u64>) -> Result<(Vec<Cell>, i32, i32), ()> {
    let mut at = 0usize;
    let mut cells = Vec::new();
    let take = |at: &mut usize, n: usize| -> Option<&[u8]> {
        let slice = bytes.get(*at..at.checked_add(n)?)?; *at += n; Some(slice)
    };
    for cell in 0..4096 {
        let Some(present) = take(&mut at, 1) else { return Ok((cells, 1, 0)); };
        if present[0] == 0 { continue; }
        let mut columns = [None; 4];
        for column in &mut columns {
            let Some(size) = take(&mut at, 2) else { return Ok((cells, 1, 0)); };
            let size = i16::from_be_bytes([size[0], size[1]]) as i32;
            if size < 0 { continue; }
            if size > SAVED_LIMIT { return Ok((cells, 2, size)); }
            let start = points.len();
            points.try_reserve(size as usize).map_err(|_| ())?;
            for _ in 0..size {
                let Some(value) = take(&mut at, 8) else { return Ok((cells, 1, 0)); };
                points.push(u64::from_be_bytes(value.try_into().unwrap()));
            }
            *column = Some((start, size as usize));
        }
        cells.try_reserve(1).map_err(|_| ())?;
        cells.push((cell, columns));
    }
    Ok((cells, 0, 0))
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

    fn sidecar(cells: &[(usize, [i32; 4])], values: u64) -> Vec<u8> {
        let mut bytes = Vec::new();
        for cell in 0..4096 {
            match cells.iter().find(|(c, _)| *c == cell) {
                None => bytes.push(0),
                Some((_, sizes)) => { bytes.push(2); for &size in sizes {
                    bytes.extend_from_slice(&(size as i16).to_be_bytes());
                    for i in 0..size.max(0) { bytes.extend_from_slice(&(values + i as u64).to_be_bytes()); }
                } }
            }
        }
        bytes
    }
    #[test]
    fn sidecar_decode_into_a_source_keeps_null_empty_order_bounds_and_prefixes() {
        use super::super::source::{mattmc_dh_source_create, mattmc_dh_source_footprints_decode, mattmc_dh_source_release};
        let bytes = sidecar(&[(3, [2, -1, 0, 1]), (4095, [256, -2, -1, 3])], 0x0102_0304_0506_0708);
        let mut result = [0i64; 2];
        unsafe {
            let source = mattmc_dh_source_create();
            assert_eq!(mattmc_dh_source_footprints_decode(source, bytes.as_ptr(), bytes.len() as i32, result.as_mut_ptr()), 1);
            assert_eq!(result, [0, 0]); assert_eq!((*source).footprint_count(), 2);
            let cell = (*source).footprint(3).unwrap().spans();
            assert_eq!(cell[0], Some(&[0x0102_0304_0506_0708, 0x0102_0304_0506_0709][..]), "big-endian points in order");
            assert_eq!((cell[1], cell[2].map(<[u64]>::len), cell[3].map(<[u64]>::len)), (None, Some(0), Some(1)));
            let last = (*source).footprint(4095).unwrap().spans();
            assert_eq!((last[0].map(<[u64]>::len), last[1], last[2]), (Some(256), None, None), "-2 is null like -1");
            assert_eq!((*source).metadata.plane(3)[3], 1);
            mattmc_dh_source_release(source);
            // Truncation keeps completed cells and reports end of input.
            let source = mattmc_dh_source_create();
            let cut = &bytes[..bytes.len() - 1];
            assert_eq!(mattmc_dh_source_footprints_decode(source, cut.as_ptr(), cut.len() as i32, result.as_mut_ptr()), 1);
            assert_eq!(result, [1, 0]); assert_eq!((*source).footprint_count(), 1);
            // A size over the bound stops after the completed prefix.
            let over = sidecar(&[(0, [257, 0, 0, 0])], 0);
            assert_eq!(mattmc_dh_source_footprints_decode(source, over.as_ptr(), over.len() as i32, result.as_mut_ptr()), 1);
            assert_eq!(result, [2, 257]);
            // An empty blob ends before the first presence byte.
            assert_eq!(mattmc_dh_source_footprints_decode(source, std::ptr::null(), 0, result.as_mut_ptr()), 1);
            assert_eq!(result, [1, 0]);
            mattmc_dh_source_release(source);
        }
    }
}

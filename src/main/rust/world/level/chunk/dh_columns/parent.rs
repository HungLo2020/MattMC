//! One retained parent quadrant, produced/remapped directly from native columns.
use super::{contributors::{capture, Snapshot}, materials::Materials, reduction::reduce};

pub(crate) struct ParentColumns {
    points: Box<[u64]>,
    offsets: Box<[usize]>,
    uniform: Box<[u8]>,
    metadata: Box<[[i8; 2]]>,
    hashes: Box<[i32]>,
    descriptors: Box<[[usize; 8]]>,
    // Descriptor pointers borrow these immutable point allocations.
    _contributors: Vec<Snapshot>,
}
#[cfg(test)]
impl ParentColumns {
    pub(crate) fn column(&self, index: usize) -> &[u64] {
        &self.points[self.offsets[index]..self.offsets[index + 1]]
    }
}

pub(crate) fn build(columns: &[&[u64]], materials: &Materials, remap: &[i32]) -> Option<ParentColumns> {
    if columns.len() != 4096 { return None; }
    let mut points = Vec::new();
    let mut offsets = Vec::with_capacity(1025);
    let mut hashes = Vec::with_capacity(1024);
    offsets.push(0);
    for x in (0..64).step_by(2) {
        for z in (0..64).step_by(2) {
            let indices = [x * 64 + z, x * 64 + z + 1, (x + 1) * 64 + z, (x + 1) * 64 + z + 1];
            let column = reduce(indices.map(|i| columns[i]), &materials.0)?;
            points.try_reserve(column.len()).ok()?;
            let mut hash = 1i32;
            for point in column {
                let id = *remap.get(point as u32 as usize)?;
                // Noncanonical caller maps retain Java's signed OR semantics.
                // Decline before publication rather than partially changing a parent.
                if id < 0 { return None; }
                let point = point & !(u32::MAX as u64) | id as u64;
                hash = hash.wrapping_mul(31).wrapping_add((point ^ point >> 32) as i32);
                points.push(point);
            }
            hashes.push(hash);
            offsets.push(points.len());
        }
    }
    Some(ParentColumns { points: points.into_boxed_slice(), offsets: offsets.into_boxed_slice(),
        uniform: vec![0; 1024].into_boxed_slice(), metadata: vec![[0; 2]; 1024].into_boxed_slice(),
        hashes: hashes.into_boxed_slice(), descriptors: vec![[0, usize::MAX - 1, 0, 0, 0, 0, 0, 0]; 1024].into_boxed_slice(),
        _contributors: Vec::new() })
}

// Flags contain source facts only: compression byte, prior uniform proof, presence,
// then the generation-step byte at bit 10. Signed metadata ordering matches Frozen.
// Lighting (bits 56..63) is deliberately excluded from semantic equality.
fn uniform(columns: [Option<&[u64]>; 4], flags: [usize; 4], remap: &[i32]) -> bool {
    if flags.iter().any(|f| f & 255 != 0 || f & 256 == 0) { return false; }
    let reference = columns[0];
    columns.into_iter().all(|column| match (reference, column) {
        (None, None) => true,
        (Some(a), Some(b)) if a.len() == b.len() => a.iter().zip(b).all(|(&a, &b)| {
            a & 0x00ff_ffff_0000_0000 == b & 0x00ff_ffff_0000_0000
                && remap.get(a as u32 as usize).zip(remap.get(b as u32 as usize)).is_some_and(|(a,b)| a == b)
        }),
        _ => false,
    })
}

fn semantics(parent: &mut ParentColumns, columns: &[&[u64]], flags: &[usize], remap: &[i32],
    existing: &[u8], mut retained: usize) -> Option<()> {
    if columns.len() != 4096 || flags.len() != 4096 || existing.len() != 1024
        || existing.iter().any(|&v| v > 1) || existing.iter().filter(|&&v| v != 0).count() > retained { return None; }
    for x in (0..64).step_by(2) {
        for z in (0..64).step_by(2) {
            let index = x / 2 * 32 + z / 2;
            let indices = [x * 64 + z, x * 64 + z + 1, (x + 1) * 64 + z, (x + 1) * 64 + z + 1];
            parent.metadata[index] = [
                indices.map(|i| (flags[i] >> 10) as u8 as i8).into_iter().min().unwrap(),
                indices.map(|i| flags[i] as u8 as i8).into_iter().max().unwrap(),
            ];
            let input = indices.map(|i| if flags[i] & 512 == 0 { None } else { Some(columns[i]) });
            let is_uniform = uniform(input, indices.map(|i| flags[i]), remap);
            parent.uniform[index] = u8::from(is_uniform);
            retained -= usize::from(existing[index]);
            if !is_uniform && retained < 1024 {
                let snapshot = capture(input, Some(remap), 256)?;
                parent.descriptors[index] = snapshot.descriptors();
                parent._contributors.push(snapshot);
                retained += 1;
            }
        }
    }
    Some(())
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_parent_columns_create(
    input: *const usize, materials: *const Materials, remap: *const i32, count: i32,
    existing: *const u8, retained: i32,
) -> *mut ParentColumns {
    if input.is_null() || input as usize % std::mem::align_of::<usize>() != 0
        || materials.is_null() || count < 0 || existing.is_null() || retained < 0
        || count != 0 && (remap.is_null() || remap as usize % 4 != 0) { return std::ptr::null_mut(); }
    let raw = std::slice::from_raw_parts(input, 12288);
    let mut columns = Vec::with_capacity(4096);
    let mut flags = Vec::with_capacity(4096);
    for pair in raw.chunks_exact(3) {
        let (pointer, len) = (pair[0], pair[1]);
        // Ordinary columns have at most one transition per valid DH Y coordinate.
        // Noncanonical oversized lists retain their Java compatibility producer.
        if pair[2] & !262143 != 0 || pair[2] & 512 == 0 && len != 0 || len > 4096 || len != 0 && (pointer == 0 || pointer % 8 != 0) { return std::ptr::null_mut(); }
        flags.push(pair[2]);
        columns.push(if len == 0 { &[][..] } else { std::slice::from_raw_parts(pointer as *const u64, len) });
    }
    let remap = if count == 0 { &[][..] } else { std::slice::from_raw_parts(remap, count as usize) };
    let Some(mut result) = build(&columns, &*materials, remap) else { return std::ptr::null_mut(); };
    if semantics(&mut result, &columns, &flags, remap, std::slice::from_raw_parts(existing, 1024), retained as usize).is_none() {
        return std::ptr::null_mut();
    }
    Box::into_raw(Box::new(result))
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_parent_columns_view(owner: *const ParentColumns, output: *mut usize) -> i32 {
    if owner.is_null() || output.is_null() || output as usize % std::mem::align_of::<usize>() != 0 { return 0; }
    let owner = &*owner;
    std::slice::from_raw_parts_mut(output, 12).copy_from_slice(&[
        owner.points.as_ptr() as usize, owner.points.len(), owner.offsets.as_ptr() as usize, owner.offsets.len(),
        owner.uniform.as_ptr() as usize, owner.uniform.len(), owner.descriptors.as_ptr() as usize, owner.descriptors.len(),
        owner.metadata.as_ptr() as usize, owner.metadata.len(), owner.hashes.as_ptr() as usize, owner.hashes.len(),
    ]);
    1
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_parent_columns_release(owner: *mut ParentColumns) {
    if !owner.is_null() { drop(Box::from_raw(owner)); }
}

#[cfg(test)]
mod tests {
    use super::*;
    use super::super::materials::Fact;
    #[test]
    fn quadrant_remapping_is_independent_and_declines_before_publication() {
        let materials = Materials(vec![Fact { opacity: 0, air: true, liquid: false }, Fact { opacity: 16, air: false, liquid: false }].into_boxed_slice());
        let source = vec![1 | 32u64 << 32 | 8u64 << 44 | 5u64 << 56 | 7u64 << 60];
        let mut columns = vec![&[][..]; 4096];
        for index in [0, 1, 64, 65] { columns[index] = &source; }
        let result = build(&columns, &materials, &[9, 4]).unwrap();
        assert_eq!(result.offsets.len(), 1025);
        assert_eq!(&result.points[..], &[4 | 32u64 << 32 | 8u64 << 44 | 5u64 << 56 | 7u64 << 60]);
        assert_eq!(&result.offsets[1..], &[1; 1024]);
        assert_eq!(source[0] as u32, 1);
        assert!(build(&columns, &materials, &[0]).is_none());
        assert!(build(&columns, &materials, &[0, -1]).is_none());
        assert!(build(&columns[..4095], &materials, &[0, 1]).is_none());
        assert!(build(&vec![&[][..]; 4096], &materials, &[]).unwrap().points.is_empty());
    }
    #[test]
    fn semantic_equality_matches_geometry_remapped_ids_and_presence() {
        let a = [1 | 32u64 << 32 | 8u64 << 44 | 5u64 << 56];
        let b = [2 | 32u64 << 32 | 8u64 << 44 | 7u64 << 60];
        let flags = [256 | 512; 4];
        assert!(uniform([Some(&a), Some(&b), Some(&a), Some(&b)], flags, &[0, 9, 9]));
        assert!(!uniform([Some(&a), Some(&b), Some(&a), Some(&b)], flags, &[0, 9, 8]));
        assert!(!uniform([Some(&a); 4], flags, &[0]));
        assert!(!uniform([Some(&a); 4], [512; 4], &[0, 9]));
        assert!(!uniform([Some(&a); 4], [512 | 256 | 1; 4], &[0, 9]));
        assert!(uniform([None; 4], [256; 4], &[]));
        assert!(uniform([Some(&[]); 4], flags, &[]));
        assert!(!uniform([None, Some(&[]), None, None], [256, 768, 256, 256], &[]));
        let geometry = [a[0] ^ (1u64 << 44)];
        assert!(!uniform([Some(&a), Some(&geometry), Some(&a), Some(&a)], flags, &[0, 9]));
    }
    #[test]
    fn admission_preserves_order_replacement_capacity_clipping_and_absent_cells() {
        let materials = Materials(vec![Fact { opacity: 0, air: true, liquid: false }].into_boxed_slice());
        let columns = vec![&[][..]; 4096];
        let mut parent = build(&columns, &materials, &[]).unwrap();
        let mut flags = vec![0; 4096];
        let mut existing = vec![0; 1024];
        existing[1] = 1;
        // First cell is rejected at capacity; replacing the next one is admitted.
        semantics(&mut parent, &columns, &flags, &[], &existing, 1024).unwrap();
        assert_eq!(parent.descriptors[0][1], usize::MAX - 1);
        assert_eq!(parent.descriptors[1][1], usize::MAX);
        assert_eq!(parent.descriptors[2][1], usize::MAX - 1);
        // A uniform replacement releases its slot; the next cell may claim it.
        for index in [0, 1, 64, 65] { flags[index] = 256; }
        existing.fill(0); existing[0] = 1;
        let mut parent = build(&columns, &materials, &[]).unwrap();
        semantics(&mut parent, &columns, &flags, &[], &existing, 1024).unwrap();
        assert_eq!(parent.uniform[0], 1);
        assert_eq!(parent.descriptors[0][1], usize::MAX - 1);
        assert_eq!(parent.descriptors[1][1], usize::MAX);
        assert_eq!(parent.descriptors[2][1], usize::MAX - 1);
        // Capture only the admitted prefix, retaining present-empty vs null.
        let points = vec![0u64; 300];
        let mut columns = columns;
        columns[0] = &points;
        flags[0] = 512; flags[1] = 512;
        let mut parent = build(&vec![&[][..]; 4096], &materials, &[]).unwrap();
        semantics(&mut parent, &columns, &flags, &[7], &[0; 1024], 0).unwrap();
        assert_eq!(parent.descriptors[0][1], 256);
        assert_eq!(parent.descriptors[0][3], 0);
        assert_eq!(parent.descriptors[0][5], usize::MAX);
        assert!(parent._contributors[0].descriptors()[0] != 0);
        assert!(semantics(&mut parent, &columns, &flags, &[7], &[1; 1024], 0).is_none());
    }

    #[test]
    fn metadata_matches_all_32768_actual_frozen_signed_byte_pairs() {
        use std::io::{Cursor, Read};
        fn text(input: &mut Cursor<Vec<u8>>) -> String {
            let mut size = [0; 2]; input.read_exact(&mut size).unwrap();
            let mut bytes = vec![0; u16::from_be_bytes(size) as usize]; input.read_exact(&mut bytes).unwrap();
            String::from_utf8(bytes).unwrap()
        }
        let mut bytes = Vec::new();
        flate2::read::GzDecoder::new(include_bytes!("../../../../../../test/resources/world/chunk/frozen-dh-parent-metadata.bin.gz").as_slice())
            .read_to_end(&mut bytes).unwrap();
        let mut input = Cursor::new(bytes); let mut number = [0; 4]; input.read_exact(&mut number).unwrap();
        assert_eq!(u32::from_be_bytes(number), 0x44485031);
        assert_eq!(text(&mut input), "dd03b56d30b6f7f02755a6251e30a538f49936da38fcc34777f1c4d0ff9b0ed4");
        assert_eq!(text(&mut input).len(), 64); assert!(text(&mut input).contains("Actual Frozen"));
        input.read_exact(&mut number).unwrap(); assert_eq!(u32::from_be_bytes(number), 32);
        let columns = vec![&[][..]; 4096];
        let materials = Materials(vec![Fact { opacity: 0, air: true, liquid: false }].into_boxed_slice());
        for _ in 0..32 {
            let mut steps = [0u8; 4096]; let mut modes = [0u8; 4096];
            input.read_exact(&mut steps).unwrap(); input.read_exact(&mut modes).unwrap();
            let flags: Vec<_> = (0..4096).map(|i| modes[i] as usize | 512 | (steps[i] as usize) << 10).collect();
            let mut parent = build(&columns, &materials, &[]).unwrap();
            semantics(&mut parent, &columns, &flags, &[], &[0; 1024], 1024).unwrap();
            for index in 0..1024 {
                let mut expected = [0; 2]; input.read_exact(&mut expected).unwrap();
                assert_eq!(parent.metadata[index], expected.map(|v| v as i8));
                assert_eq!(parent.hashes[index], 1);
            }
        }
        assert_eq!(input.position(), input.get_ref().len() as u64);
    }

}

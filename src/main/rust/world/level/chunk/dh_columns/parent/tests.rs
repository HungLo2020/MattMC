use super::*;
use super::super::{materials::Fact, mutable::mattmc_dh_column_create, source::{mattmc_dh_source_create, mattmc_dh_source_release, mattmc_dh_source_set}};

fn point(id: u64, height: u64, bottom: u64, sky: u64) -> u64 { id | height << 32 | bottom << 44 | sky << 56 }
fn air_and_stone() -> Materials {
    Materials(vec![Fact { opacity: 0, air: true, liquid: false }, Fact { opacity: 16, air: false, liquid: false },
        Fact { opacity: 0, air: false, liquid: true }].into_boxed_slice())
}
/// A source whose listed cells hold the given columns (every other cell present-empty).
unsafe fn source_with(cells: &[(usize, Vec<u64>)]) -> *mut Source {
    let source = mattmc_dh_source_create();
    for (index, points) in cells {
        let column = mattmc_dh_column_create(points.as_ptr(), points.len() as i32);
        assert_eq!(mattmc_dh_source_set(source, *index as i32, 2, column), 1);
        super::super::mutable::mattmc_dh_column_release(column);
    }
    source
}
unsafe fn column(source: *const Source, index: usize) -> Vec<u64> { (*source).column(index).unwrap().to_vec() }

#[test]
fn metadata_matches_all_32768_actual_frozen_signed_byte_pairs() {
    use std::io::{Cursor, Read};
    fn text(input: &mut Cursor<Vec<u8>>) -> String {
        let mut size = [0; 2]; input.read_exact(&mut size).unwrap();
        let mut bytes = vec![0; u16::from_be_bytes(size) as usize]; input.read_exact(&mut bytes).unwrap();
        String::from_utf8(bytes).unwrap()
    }
    let mut bytes = Vec::new();
    flate2::read::GzDecoder::new(include_bytes!("../../../../../../../test/resources/world/chunk/frozen-dh-parent-metadata.bin.gz").as_slice())
        .read_to_end(&mut bytes).unwrap();
    let mut input = Cursor::new(bytes); let mut number = [0; 4]; input.read_exact(&mut number).unwrap();
    assert_eq!(u32::from_be_bytes(number), 0x44485031);
    assert_eq!(text(&mut input), "dd03b56d30b6f7f02755a6251e30a538f49936da38fcc34777f1c4d0ff9b0ed4");
    assert_eq!(text(&mut input).len(), 64); assert!(text(&mut input).contains("Actual Frozen"));
    input.read_exact(&mut number).unwrap(); assert_eq!(u32::from_be_bytes(number), 32);
    let materials = Materials(vec![Fact { opacity: 0, air: true, liquid: false }].into_boxed_slice());
    unsafe {
        for _ in 0..32 {
            let (child, parent) = (mattmc_dh_source_create(), mattmc_dh_source_create());
            let (mut steps, mut modes) = ([0u8; 4096], [0u8; 4096]);
            input.read_exact(&mut steps).unwrap(); input.read_exact(&mut modes).unwrap();
            for i in 0..4096 {
                (*child).metadata.plane_mut(0)[i] = steps[i] as i8; (*child).metadata.plane_mut(1)[i] = modes[i] as i8;
            }
            assert!(update_parent(&mut *parent, &*child, &materials, &[], (0, 0)).is_ok());
            for cell in 0..1024 {
                let mut expected = [0; 2]; input.read_exact(&mut expected).unwrap();
                let index = cell / 32 * 64 + cell % 32;
                assert_eq!([(*parent).metadata.plane(0)[index], (*parent).metadata.plane(1)[index]], expected.map(|v| v as i8));
            }
            mattmc_dh_source_release(child); mattmc_dh_source_release(parent);
        }
    }
    assert_eq!(input.position(), input.get_ref().len() as u64);
}

#[test]
fn semantic_uniformity_matches_geometry_remapped_ids_presence_and_compression() {
    let a = [1 | 32u64 << 32 | 8u64 << 44 | 5u64 << 56];
    let b = [2 | 32u64 << 32 | 8u64 << 44 | 7u64 << 60];
    let (merge, proven) = ([0i8; 4], [true; 4]);
    assert!(uniform([Some(&a), Some(&b), Some(&a), Some(&b)], merge, proven, &[0, 9, 9]), "lighting is ignored");
    assert!(!uniform([Some(&a), Some(&b), Some(&a), Some(&b)], merge, proven, &[0, 9, 8]));
    assert!(!uniform([Some(&a); 4], merge, proven, &[0]), "an unmapped ID is not uniform");
    assert!(!uniform([Some(&a); 4], merge, [true, true, false, true], &[0, 9]));
    assert!(!uniform([Some(&a); 4], [0, 1, 0, 0], proven, &[0, 9]));
    assert!(uniform([None; 4], merge, proven, &[]));
    assert!(uniform([Some(&[]); 4], merge, proven, &[]));
    assert!(!uniform([None, Some(&[]), None, None], merge, proven, &[]), "missing and empty differ");
    let geometry = [a[0] ^ (1u64 << 44)];
    assert!(!uniform([Some(&a), Some(&geometry), Some(&a), Some(&a)], merge, proven, &[0, 9]));
}

#[test]
fn parent_update_reduces_remaps_writes_the_offset_quadrant_and_reports_changes() {
    unsafe {
        let stone = point(1, 4, 60, 15);
        let child = source_with(&[(0, vec![stone]), (1, vec![stone]), (64, vec![stone]), (65, vec![stone])]);
        let parent = mattmc_dh_source_create();
        let remap = [0, 7];
        let materials = air_and_stone();
        assert_eq!(update_parent(&mut *parent, &*child, &materials, &remap, (32, 0)), Ok(true));
        let target = 32 * 64;
        assert_eq!(column(parent, target), vec![point(7, 4, 60, 15)], "remapped once into the +x quadrant");
        assert_eq!(column(child, 0), vec![stone], "the child is unchanged");
        assert_eq!((*parent).column(0).unwrap(), &[] as &[u64], "the other quadrant is untouched");
        assert_eq!(update_parent(&mut *parent, &*child, &materials, &remap, (32, 0)), Ok(false), "an identical update changes nothing");
        mattmc_dh_source_release(child); mattmc_dh_source_release(parent);
    }
}

#[test]
fn footprints_follow_cell_order_capacity_and_replacement() {
    unsafe {
        // Every child cell holds a different material, so no parent cell is uniform.
        let cells: Vec<_> = (0..4096).map(|i| (i, vec![point(1 + (i as u64 & 1), 4, 60, 0)])).collect();
        let child = source_with(&cells);
        let parent = mattmc_dh_source_create();
        let materials = Materials(vec![Fact { opacity: 0, air: true, liquid: false }; 3].into_boxed_slice());
        assert!(update_parent(&mut *parent, &*child, &materials, &[0, 1, 2], (0, 0)).is_ok());
        assert_eq!((*parent).footprint_count(), 1024, "capacity is exactly the quadrant");
        let footprint = (*parent).footprint(0).unwrap().spans();
        assert_eq!(footprint[0], Some(&[point(1, 4, 60, 0)][..]));
        assert_eq!(footprint[1], Some(&[point(2, 4, 60, 0)][..]));
        assert_eq!((*parent).metadata.plane(3)[0], 1, "Java's presence plane follows the table");
        // A second quadrant finds the table full: its cells get none, and each
        // replaced cell first gives up its old footprint.
        assert!(update_parent(&mut *parent, &*child, &materials, &[0, 1, 2], (32, 32)).is_ok());
        assert_eq!((*parent).footprint_count(), 1024);
        assert!((*parent).footprint(32 * 64 + 32).is_none());
        // Uniform cells release theirs, letting later cells claim the capacity.
        let uniform_child = source_with(&(0..4096).map(|i| (i, vec![point(1, 4, 60, 0)])).collect::<Vec<_>>());
        for i in 0..4096 { (*uniform_child).metadata.plane_mut(2)[i] = 1; }
        assert!(update_parent(&mut *parent, &*uniform_child, &materials, &[0, 1, 2], (0, 0)).is_ok());
        assert_eq!((*parent).footprint_count(), 0);
        assert_eq!((*parent).metadata.plane(2)[0], 1);
        mattmc_dh_source_release(child); mattmc_dh_source_release(uniform_child); mattmc_dh_source_release(parent);
    }
}

#[test]
fn failures_keep_earlier_cells_and_name_the_bad_id() {
    unsafe {
        let materials = air_and_stone();
        let bad = source_with(&[(2, vec![point(9, 4, 60, 0)])]);
        let parent = mattmc_dh_source_create();
        (*bad).metadata.plane_mut(0)[0] = 5;
        assert_eq!(update_parent(&mut *parent, &*bad, &materials, &[0, 1, 2], (0, 0)), Err(Failure::Material(9)));
        assert_eq!((*parent).metadata.plane(0)[0], 0, "cell 0 reduces cells 0, 1, 64, 65; its generation is the minimum");
        let remapped = source_with(&[(0, vec![point(2, 4, 60, 0)])]);
        assert_eq!(update_parent(&mut *parent, &*remapped, &materials, &[0, 1], (0, 0)), Err(Failure::Remap(2)));
        mattmc_dh_source_release(bad); mattmc_dh_source_release(remapped); mattmc_dh_source_release(parent);
    }
}

#[test]
fn downsample_fills_only_empty_recipients_with_one_remap_and_leaves_the_input_unchanged() {
    unsafe {
        let stone = point(1, 4, 60, 15);
        // Input cell (32, 0) projects onto recipient cells (0..2, 0..2) at offset (32, 0).
        let input = source_with(&[(32 * 64, vec![stone])]);
        (*input).metadata.plane_mut(1)[32 * 64] = 1;
        let child = source_with(&[(1, vec![point(1, 2, 0, 0)])]);
        let remap = [0, 5];
        assert_eq!(downsample(&mut *child, &*input, &remap, (32, 0), &mut [0; 64]), Ok(true));
        assert_eq!(column(child, 0), vec![point(5, 4, 60, 15)]);
        assert_eq!(column(child, 64), vec![point(5, 4, 60, 15)]);
        assert_eq!(column(child, 65), vec![point(5, 4, 60, 15)], "each recipient is remapped once");
        assert_eq!(column(child, 1), vec![point(1, 2, 0, 0)], "existing data is kept");
        assert_eq!(column(input, 32 * 64), vec![stone], "the input is unchanged");
        assert_eq!((*child).metadata.plane(0)[0], DOWN_SAMPLED);
        assert_eq!((*child).metadata.plane(1)[64], 1, "compression comes from the mapped input cell");
        assert_eq!((*child).metadata.plane(2)[0], 0, "a non-MERGE_SAME_BLOCKS cell is not uniform");
        mattmc_dh_source_release(input); mattmc_dh_source_release(child);
    }
}

#[test]
fn downsample_copies_the_mapped_footprint_with_its_remap_and_uniformity() {
    unsafe {
        let input = source_with(&[(1 * 64, vec![point(1, 4, 60, 15)])]);
        let footprint = [point(1, 4, 60, 15)];
        let spans = [footprint.as_ptr() as usize, 1, 0, 0, 0, 0, 0, usize::MAX];
        assert_eq!(super::super::source::mattmc_dh_source_footprint_set(input, 64, spans.as_ptr(), 256), 1);
        let child = mattmc_dh_source_create();
        assert_eq!(downsample(&mut *child, &*input, &[0, 3], (0, 0), &mut [0; 64]), Ok(true));
        // Recipient (2, 0) maps to input (1, 0).
        let copied = (*child).footprint(2 * 64).unwrap().spans();
        assert_eq!(copied[0], Some(&[point(3, 4, 60, 15)][..]));
        assert_eq!(copied[1], Some(&[][..]));
        assert_eq!(copied[3], None);
        assert_eq!((*child).metadata.plane(2)[2 * 64], 0);
        // A uniform MERGE_SAME_BLOCKS input cell yields uniform recipients and no footprint.
        (*input).metadata.plane_mut(2)[64] = 1;
        let uniform_child = mattmc_dh_source_create();
        assert_eq!(downsample(&mut *uniform_child, &*input, &[0, 3], (0, 0), &mut [0; 64]), Ok(true));
        assert_eq!((*uniform_child).metadata.plane(2)[2 * 64], 1);
        assert!((*uniform_child).footprint(2 * 64).is_none());
        mattmc_dh_source_release(input); mattmc_dh_source_release(child); mattmc_dh_source_release(uniform_child);
    }
}

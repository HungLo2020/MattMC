use super::*;

#[test]
fn overlap_is_shared_but_resolvers_and_literal_callbacks_are_distinct() {
    let mut kinds = vec![0; BLOCKS];
    kinds[0] = 1;
    kinds[1] = 1;
    kinds[16] = 2;
    kinds[256] = 4;
    let mut literals = vec![-1; BLOCKS * 64];
    for i in 0..64 {
        literals[256 * 64 + i] = 0xff000000u32 as i32 | i as i32;
    }
    let mut pending = plan([-17, 80, -33], &[0, 1, 16, 256], &kinds, Some(&literals)).unwrap();
    assert_eq!(80 + 64, pending.queries.len());
    for (value, query) in pending.fields.colors.iter_mut().zip(&pending.queries) {
        *value = 0xff000000u32 as i32
            | (query[0] << 24)
            | ((query[1] & 255) << 16)
            | ((query[2] & 255) << 8)
            | (query[3] & 255);
    }
    for block in [0usize, 1, 16, 256] {
        for y in 0..4 {
            for z in 0..4 {
                for x in 0..4 {
                    let expected = if block == 256 {
                        literals[256 * 64 + (y * 4 + z) * 4 + x]
                    } else {
                        0xff000000u32 as i32
                            | (((-17 + (block & 15) as i32 + x as i32 - 1) & 255) << 16)
                            | (((80 + (block >> 8) as i32 + y as i32 - 1) & 255) << 8)
                            | ((-33 + ((block >> 4) & 15) as i32 + z as i32 - 1) & 255)
                    };
                    assert_eq!(expected, pending.fields.sample(block, x, y, z));
                }
            }
        }
    }
}

#[test]
fn full_section_bounds_and_empty_fields() {
    let active: Vec<u16> = (0..BLOCKS as u16).collect();
    let mut pending = plan([0, 0, 0], &active, &vec![1; BLOCKS], None).unwrap();
    assert_eq!(CELLS, pending.queries.len());
    pending.fields.colors.fill(-1);
    assert_eq!(-1, pending.fields.sample(4095, 3, 3, 3));
    assert_eq!(
        vec![1, 17, 17, 17],
        pending.queries.last().unwrap().to_vec()
    );
    let empty = plan([0, 0, 0], &[], &vec![0; BLOCKS], None).unwrap();
    assert!(empty.queries.is_empty());
    assert!(empty.fields.literals.is_empty());
}

#[test]
fn rejects_bad_sources_overflow_duplicates_and_missing_literals() {
    let mut kinds = vec![0; BLOCKS];
    kinds[0] = 4;
    assert!(plan([0, 0, 0], &[0], &kinds, None).is_err());
    kinds[0] = 5;
    assert!(plan([0, 0, 0], &[0], &kinds, None).is_err());
    kinds[0] = 0;
    for active in [vec![4096], vec![0, 0]] {
        assert!(plan([0, 0, 0], &active, &kinds, None).is_err());
    }
    assert!(plan([i32::MAX, 0, 0], &[], &kinds, None).is_err());
}

#[test]
fn lease_rejects_unfinished_released_and_double_finish_but_decoded_owner_survives() {
    let active = [0u16];
    let mut kinds = vec![0; BLOCKS];
    kinds[0] = 1;
    let mut output = [0u64; 4];
    assert_eq!(0, unsafe {
        mattmc_world_section_colors_plan(
            0,
            0,
            0,
            active.as_ptr(),
            1,
            kinds.as_ptr(),
            std::ptr::null(),
            output.as_mut_ptr(),
        )
    });
    let id = output[0];
    assert!(resolve(id).is_err());
    assert_eq!(-2, mattmc_world_section_colors_finish(id));
    unsafe {
        std::slice::from_raw_parts_mut(output[2] as *mut i32, output[3] as usize).fill(-1);
    }
    assert_eq!(0, mattmc_world_section_colors_finish(id));
    let decoded = resolve(id).unwrap();
    assert_eq!(-2, mattmc_world_section_colors_finish(id));
    assert_eq!(0, mattmc_world_section_colors_release(id));
    assert_eq!(-2, mattmc_world_section_colors_release(id));
    assert!(resolve(id).is_err());
    assert_eq!(-1, decoded.sample(0, 3, 3, 3));
}

#[test]
fn literal_staging_can_be_freed_before_sealing_or_meshing() {
    let active = [4095u16];
    let mut kinds = vec![0u8; BLOCKS];
    kinds[4095] = 4;
    let mut literals = vec![-1i32; BLOCKS * 64];
    for i in 0..64 {
        literals[4095 * 64 + i] = 0xff123400u32 as i32 | i as i32;
    }
    let mut lease = [0u64; 4];
    assert_eq!(0, unsafe {
        mattmc_world_section_colors_plan(
            0,
            0,
            0,
            active.as_ptr(),
            1,
            kinds.as_ptr(),
            literals.as_ptr(),
            lease.as_mut_ptr(),
        )
    });
    literals.fill(0);
    drop(literals);
    assert_eq!(0, lease[3]);
    assert_eq!(0, mattmc_world_section_colors_finish(lease[0]));
    let fields = resolve(lease[0]).unwrap();
    assert_eq!(0, mattmc_world_section_colors_release(lease[0]));
    for y in 0..4 {
        for z in 0..4 {
            for x in 0..4 {
                assert_eq!(
                    0xff123400u32 as i32 | ((y * 4 + z) * 4 + x) as i32,
                    fields.sample(4095, x, y, z)
                );
            }
        }
    }
}

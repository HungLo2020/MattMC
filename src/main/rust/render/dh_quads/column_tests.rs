use super::*;
use crate::render::dh_quads::{Builder, Policy, Quad};

#[test]
fn collector_bridge_moves_the_exact_allocation_and_acquires_its_lease() {
    use crate::render::{
        bridge::dh_collector,
        dh_collector::{allocate_generation, ledger},
    };
    let source = source();
    let mut build = ColumnBuild::new();
    for route in 0..4 {
        let plan = Plan::new(&source, route, 0, false).unwrap();
        assert!(build.append(&plan, route as usize, &[], &[]));
    }
    let pointer = build.payload.as_ref().unwrap().layers[0][0].bytes.as_ptr();
    let key = -11003001;
    let generation = allocate_generation();
    let mut out = [0i64; 2];
    let effects = unsafe {
        dh_collector::mattmc_dh_collector_record_native(
            &mut build,
            1,
            key,
            generation,
            4,
            5,
            6,
            -1,
            1,
            1,
            out.as_mut_ptr(),
        )
    };
    assert!(effects >= 0);
    assert_eq!(out[0], generation);
    assert_ne!(out[1], 0);
    assert!(build.payload.is_none());
    {
        let ledger = ledger().lock().unwrap();
        let column = ledger.current(key).unwrap();
        assert_eq!(column.payload.origin, [4, 5, 6]);
        assert_eq!(column.payload.layers[0][0].bytes.as_ptr(), pointer);
    }
    assert_eq!(
        unsafe {
            dh_collector::mattmc_dh_collector_record_native(
                &mut build,
                1,
                key,
                generation,
                4,
                5,
                6,
                -1,
                1,
                1,
                out.as_mut_ptr(),
            )
        },
        -100
    );
    assert!(
        unsafe { dh_collector::mattmc_dh_collector_release_owner(1, key, generation, out[1]) } >= 0
    );
}

fn quad(direction: u8, identity: i32, material: u8) -> Quad {
    Quad {
        x: 1,
        y: 2,
        z: 3,
        width: 1,
        height: 1,
        direction,
        material,
        sky: 255,
        block: 129,
        error: 0,
        variant: 1,
        color: 0xff123456,
        identity,
        position: 99,
    }
}
fn source() -> Builder {
    let mut source = Builder::new(true);
    let policy = Policy {
        exact: false,
        overlap_errors: false,
        curvature: false,
    };
    for q in [quad(0, 1, 13), quad(1, -1, 12), quad(2, 0, 12)] {
        assert!(source.add(q, false, policy));
    }
    source
}
#[test]
fn layer_protocol_callback_validation_and_cpu_ownership() {
    let source = source();
    let mut column = ColumnBuild::new();
    let first = Plan::new(&source, 0, 1, false).unwrap();
    assert!(!column.append(&first, 1, &[0; 4], &[1.0; 4]));
    assert!(!column.append(&first, 0, &[], &[]));
    assert_eq!(column.next_layer, 0);
    assert!(!column.payload.as_ref().unwrap().has_segments());
    assert!(column.append(&first, 0, &[0xff987654u32 as i32; 4], &[0.5; 4]));
    assert!(!column.append(&first, 0, &[0; 4], &[1.0; 4]));
    for route in 1..4 {
        let plan = Plan::new(&source, route, 1, false).unwrap();
        assert!(column.append(&plan, route as usize, &[], &[]));
    }
    assert_eq!(column.validate(), 0);
    assert_eq!(column.coverage, [1, 1, 1, 1, 0, 0]);
    let pointer = column.payload.as_ref().unwrap().layers[0][0].bytes.as_ptr();
    drop(source);
    drop(first);
    let payload = column.payload.take().unwrap();
    assert_eq!(payload.layers[0][0].bytes.as_ptr(), pointer);
    assert_eq!(
        payload.layers.iter().map(Vec::len).collect::<Vec<_>>(),
        [1, 1, 0, 1]
    );
    assert_eq!(payload.layers[0][0].bytes[8..12], [0x4c, 0x3b, 0x2a, 255]);
    assert_eq!(column.validate(), -1);
}
#[test]
fn split_packets_preserve_companions_and_distinct_owned_storage() {
    let mut source = Builder::new(false);
    let policy = Policy {
        exact: false,
        overlap_errors: false,
        curvature: false,
    };
    for i in 0..16_385 {
        let mut q = quad(1, i, 2);
        q.position = i as i64;
        source.add(q, false, policy);
    }
    let plan = Plan::new(&source, 0, 0, false).unwrap();
    let mut column = ColumnBuild::new();
    assert!(column.append(&plan, 0, &[], &[]));
    let layer = &column.payload.as_ref().unwrap().layers[0];
    assert_eq!(layer.len(), 2);
    assert_eq!(layer[0].bytes.len(), 16_384 * 64);
    assert_eq!(layer[1].bytes.len(), 64);
    assert_eq!(layer[1].source_index, 1);
    assert_eq!(column.companions[0][1].identities, [16_384]);
    assert_eq!(column.companions[0][1].positions, [16_384]);
    let mut bytes = vec![7; 64];
    let mut ids = [7];
    let mut variants = [7];
    let mut positions = [7];
    unsafe {
        assert_eq!(
            mattmc_dh_built_column_copy(
                &column,
                0,
                1,
                bytes.as_mut_ptr(),
                ids.as_mut_ptr(),
                variants.as_mut_ptr(),
                positions.as_mut_ptr(),
                2
            ),
            0
        );
        assert_eq!(bytes, vec![7; 64]);
        assert_eq!(ids, [7]);
        assert_eq!(
            mattmc_dh_built_column_copy(
                &column,
                0,
                1,
                bytes.as_mut_ptr(),
                ids.as_mut_ptr(),
                variants.as_mut_ptr(),
                positions.as_mut_ptr(),
                1
            ),
            1
        );
    }
    assert_eq!(ids, [16_384]);
    assert_eq!(variants, [1]);
    assert_eq!(positions, [16_384]);
}
#[test]
fn audit_projection_and_publication_admission_are_bounded() {
    let mut source = Builder::new(true);
    let policy = Policy {
        exact: false,
        overlap_errors: false,
        curvature: false,
    };
    for i in 0..32 {
        let mut q = quad(1, 0, 12);
        q.color = 0x80330000 + i;
        source.add(q, false, policy);
    }
    let mut column = ColumnBuild::new();
    assert_eq!(column.validate(), -1);
    for route in 0..4 {
        let plan = Plan::new(&source, route, 0, false).unwrap();
        assert!(column.append(&plan, route as usize, &[], &[]));
    }
    let mut data = [0i32; 32];
    unsafe {
        assert_eq!(
            mattmc_dh_built_column_water_samples(&column, ptr::null_mut(), 0),
            32
        );
        assert_eq!(
            mattmc_dh_built_column_water_samples(&column, data.as_mut_ptr(), 31),
            -1
        );
        assert_eq!(data, [0; 32]);
        assert_eq!(
            mattmc_dh_built_column_water_samples(&column, data.as_mut_ptr(), 32),
            32
        );
    }
    for i in 0..16 {
        assert_eq!(data[i * 2], 3);
        assert_eq!(data[i * 2 + 1], (0x80330000 + i as u32) as i32);
    }
    // Real inputs, including overlap-error substitution, determine admission.
    // Production never exposes a mutable alias to encoded bytes.
    for (material, error, expected) in [(15, 0, 0), (16, 0, -3), (255, 0, -3), (255, 1, 0)] {
        let mut source = Builder::new(false);
        let mut row = quad(1, 0, material);
        row.error = error;
        source.add(row, false, policy);
        let mut invalid = ColumnBuild::new();
        for route in 0..4 {
            let plan = Plan::new(&source, route, 0, false).unwrap();
            assert!(invalid.append(&plan, route as usize, &[], &[]));
        }
        assert_eq!(invalid.validate(), expected);
        assert_eq!(invalid.validate(), expected);
        if expected == 0 {
            assert!(invalid.take_payload().is_some());
            assert_eq!(invalid.validate(), -1);
        } else {
            assert!(invalid.take_payload().is_none());
            assert!(invalid.payload.is_some());
        }
    }
    column.payload.as_mut().unwrap().layers[3] = vec![
        Segment {
            source_index: 0,
            bytes: Box::default()
        };
        513
    ];
    assert_eq!(column.validate(), -2);
}

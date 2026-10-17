use super::*;

#[test]
fn unchanged_container_stays_single_value() {
    let mut c = Container::new(7, 7);
    for index in 0..ENTRIES {
        c.set(index, 7);
    }
    assert_eq!((c.requested_bits(), c.palette(), c.packed()), (0, &[7][..], vec![]));
}

#[test]
fn recreated_value_stays_in_the_palette() {
    // recreate() keeps the old first value; resizing copies it in.
    let mut c = Container::new(7, 7);
    for index in 0..ENTRIES {
        c.set(index, 3);
    }
    assert_eq!((c.requested_bits(), c.palette()), (1, &[7, 3][..]));
    assert_eq!(c.packed(), vec![-1]);
}

#[test]
fn resize_orders_palette_by_first_storage_occurrence() {
    let mut c = Container::new(7, 7);
    c.set(5, 1);
    c.set(0, 1);
    // Storage index 0 now holds 1, before any 7: the 2-bit palette is [1, 7, 2].
    c.set(9, 2);
    assert_eq!((c.requested_bits(), c.palette()), (2, &[1, 7, 2][..]));
}

#[test]
fn ninth_value_switches_to_the_global_palette() {
    let mut c = Container::new(0, 7);
    for index in 0..9 {
        c.set(index, index as i32 + 1);
    }
    assert_eq!((c.requested_bits(), c.palette()), (4, &[][..]));
    let expected: Vec<u32> = (0..ENTRIES).map(|i| if i < 9 { i as u32 + 1 } else { 0 }).collect();
    assert_eq!(c.packed(), pack(&expected, 7));
}

#[test]
fn quantize_matches_java_float_conversion() {
    assert_eq!(quantize(0.123456789), (0.123456789f64 as f32 * 10000.0f32) as i64);
    // Rounding to float first crosses the integer: a double product gives 1234.
    assert_eq!(quantize(0.12349999999), 1235);
    assert_eq!(quantize(-0.12349999999), -1235);
    assert_eq!(quantize(f64::NAN), 0);
    assert_eq!(quantize(f64::INFINITY), i64::MAX);
    assert_eq!(quantize(-1e300), i64::MIN);
}

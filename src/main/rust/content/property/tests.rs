use super::*;

#[test]
fn builtins_have_unique_keys_and_ordered_domains() {
    let r = registry();
    assert_eq!(r.definitions().len(), 134);
    let mut keys = std::collections::HashSet::new();
    let mut kinds = [0; 3];
    for d in r.definitions() {
        assert!(keys.insert(d.key));
        let values = d.schema.values();
        assert_eq!(values.len(), d.domain.count() as usize);
        assert_eq!(values.iter().collect::<std::collections::HashSet<_>>().len(), values.len());
        match d.domain {
            Domain::Boolean => {
                kinds[0] += 1;
                assert_eq!(values, &["true", "false"]);
                assert_eq!(d.domain.boolean(0), Some(true));
                assert_eq!(d.domain.boolean(1), Some(false));
                assert_eq!(d.domain.boolean(2), None);
            }
            Domain::Integer { min, max } => {
                kinds[1] += 1;
                assert!(min < max);
                for (i, value) in values.iter().enumerate() {
                    assert_eq!(d.domain.integer(i as u16), Some(value.parse().unwrap()));
                }
                assert_eq!(d.domain.integer(d.domain.count()), None);
                assert_eq!(d.domain.integer(u16::MAX), None);
            }
            Domain::Enum(_) => kinds[2] += 1,
        }
    }
    assert_eq!(kinds, [57, 36, 41]);
    assert_eq!(Builtin::Facing.definition().schema.values(), &["north", "east", "south", "west", "up", "down"]);
    assert_eq!(Builtin::HorizontalFacing.definition().schema.values(), &["north", "south", "west", "east"]);
    assert_eq!(Builtin::FacingHopper.definition().schema.values(), &["down", "north", "south", "west", "east"]);
    assert_eq!(Builtin::VerticalDirection.definition().schema.values(), &["up", "down"]);
    assert!(!Arc::ptr_eq(&Builtin::Age1.definition().schema, &Builtin::Age2.definition().schema));
}

#[test]
fn bridge_tables_are_bounded_complete_and_borrowed() {
    let r = registry();
    let mut next = 0;
    for (id, row) in r.rows.chunks_exact(7).enumerate() {
        let text = |at: i32, len: i32| std::str::from_utf8(&r.text[at as usize..(at + len) as usize]).unwrap();
        let d = &r.definitions[id];
        assert_eq!(text(row[0], row[1]), d.key);
        assert_eq!(text(row[2], row[3]), d.schema.name());
        assert_eq!(row[5], next);
        for i in 0..row[6] {
            let v = (next + i) as usize * 2;
            assert_eq!(text(r.values[v], r.values[v + 1]), d.schema.values()[i as usize]);
        }
        next += row[6];
    }
    assert_eq!(next as usize * 2, r.values.len());
    unsafe {
        let mut len = -1;
        assert!(ffi::mattmc_property_definitions_buffer(4, &mut len).is_null());
        assert_eq!(len, 0);
        assert!(ffi::mattmc_property_definitions_buffer(0, std::ptr::null_mut()).is_null());
        assert_eq!(ffi::mattmc_property_definitions_buffer(0, &mut len), r.header.as_ptr().cast());
        assert_eq!(len, 6);
    }
}

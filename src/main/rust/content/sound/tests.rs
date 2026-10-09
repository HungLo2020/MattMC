use super::*;
#[test]
fn native_references_are_canonical_and_aliases_remain_distinct() {
    let r = registry();
    assert!(r.event(EventId(u16::MAX)).is_none());
    for t in r.types() {
        for &id in &t.events {
            let e = r.event(id).unwrap();
            assert_eq!(r.find_event(e.key).unwrap().id, id);
        }
    }
    let native = SoundType::AcPewenBranch.definition();
    let cherry = SoundType::CherryWood.definition();
    assert_ne!(native.id, cherry.id);
    assert_eq!(native.events, cherry.events);
    assert!(Instrument::Harp.definition().is_tunable());
    assert!(Instrument::Zombie.definition().works_above_note_block());
    assert!(!Instrument::Zombie.definition().has_custom_sound());
    assert!(Instrument::CustomHead.definition().has_custom_sound());
}
#[test]
fn range_semantics_preserve_java_threshold_and_nonfinite_inputs() {
    let event = &registry().events()[0];
    for volume in [f32::NEG_INFINITY, -1.0, -0.0, 0.0, 1.0, f32::NAN] { assert_eq!(event.range(volume),16.0); }
    assert_eq!(event.range(2.0),32.0);
    assert_eq!(event.range(f32::INFINITY),f32::INFINITY);
    let fixed = EventDefinition { id: EventId(0), key:"test:fixed",location:"test:fixed",fixed_range:Some(7.0) };
    assert_eq!(fixed.range(f32::NAN),7.0);
    assert_eq!(fixed.range(100.0),7.0);
}
#[test]
fn borrowed_sound_buffers_are_stable_and_invalid_selectors_are_empty() {
    let r = registry();
    unsafe {
        let mut n = 0;
        for (kind, row) in [(0, &r.header[..]),(1,&r.event_rows),(2,&r.type_rows),(3,&r.instrument_rows)] {
            let first = ffi::mattmc_sound_definitions_buffer(kind,&mut n);
            assert_eq!(n as usize,row.len());assert_eq!(first,row.as_ptr().cast());
            assert_eq!(first,ffi::mattmc_sound_definitions_buffer(kind,&mut n));
        }
        assert!(ffi::mattmc_sound_definitions_buffer(5,&mut n).is_null());assert_eq!(n,0);
        assert!(ffi::mattmc_sound_definitions_buffer(0,std::ptr::null_mut()).is_null());
    }
}

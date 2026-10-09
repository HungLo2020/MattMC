use super::*;
use crate::world::level::lighting::layers::Layer;

#[test]
fn recorded_frozen_production_words_cover_all_states_and_variants() {
    let mut bytes = include_bytes!("frozen-terrain-light.bin").as_slice();
    fn u32_at(bytes: &mut &[u8]) -> u32 {
        let value = u32::from_be_bytes(bytes[..4].try_into().unwrap()); *bytes = &bytes[4..]; value
    }
    fn u16_at(bytes: &mut &[u8]) -> u16 {
        let value = u16::from_be_bytes(bytes[..2].try_into().unwrap()); *bytes = &bytes[2..]; value
    }
    assert_eq!(u32_at(&mut bytes), 0x544c4932);
    let states = u32_at(&mut bytes); let variants = u32_at(&mut bytes);
    assert_eq!((states, variants), (31809, 8));
    let rows = u32_at(&mut bytes) as usize;
    for _ in 0..rows {
        let flags = u32_at(&mut bytes); let luminance = u32_at(&mut bytes) as i32;
        let shade = f32::from_bits(u32_at(&mut bytes));
        assert_eq!(u32_at(&mut bytes) as i32, luminance, "Frozen intrinsic/platform emission equality");
        let block = u32_at(&mut bytes) as i32; let sky = u32_at(&mut bytes) as i32;
        assert_eq!(word(flags, luminance, shade, block, sky), u32_at(&mut bytes) as i32);
    }
    let patterns = u32_at(&mut bytes) as usize;
    for _ in 0..patterns * variants as usize { assert!((u16_at(&mut bytes) as usize) < rows); }
    let runs = u32_at(&mut bytes); let mut count = 0;
    for _ in 0..runs {
        count += u32::from(u16_at(&mut bytes));
        assert!((u16_at(&mut bytes) as usize) < patterns);
    }
    assert_eq!(count, states); assert!(bytes.is_empty());
}

#[test]
fn halo_generation_reads_keep_every_face_edge_corner_and_missing_dimension() {
    let owners: [Layer; VIEWS] = std::array::from_fn(|i| Layer::new(i as i32 - 20));
    for (i, owner) in owners.iter().enumerate() {
        if i % 3 == 0 { for cell in 0..4096 { owner.set(cell, cell + i as i32).unwrap(); } }
    }
    let views = owners.each_ref().map(Layer::view);
    let contexts = [Context { flags: 1 | 4, shade: 0.2 }; CELLS];
    let mut output = [0; CELLS]; let ids = [0; CELLS];
    let facts = |_| Some(Facts { emission: 0, light_block: 15, full_opaque: false });
    prepare(views.each_ref().map(Some), &ids, &contexts, &mut output, facts).unwrap();
    for (index, value) in output.into_iter().enumerate() {
        let x = index % PAD; let z = index / PAD % PAD; let y = index / PAD / PAD;
        let section = (((y + 15) / 16 * 3 + (z + 15) / 16) * 3 + (x + 15) / 16) * 2;
        let cell = (((y + 15) % 16 * 16 + (z + 15) % 16) * 16 + (x + 15) % 16) as i32;
        assert_eq!(value, word(1 | 8, 0, 0.2, views[section].get(cell).unwrap(), views[section + 1].get(cell).unwrap()));
    }
    prepare([None; VIEWS], &ids, &contexts, &mut output, facts).unwrap();
    assert!(output.iter().all(|word| *word == super::word(1 | 8, 0, 0.2, 0, 0)));
}

#[test]
fn rejected_semantic_inputs_leave_output_untouched() {
    let mut contexts = [Context { flags: 0, shade: 1.0 }; CELLS];
    let mut ids = [0; CELLS]; let mut output = [42; CELLS];
    let facts = |id| if id == 0 { Some(Facts { emission: 0, light_block: 0, full_opaque: false }) } else { None };
    contexts[CELLS - 1].flags = 16;
    assert_eq!(prepare([None; VIEWS], &ids, &contexts, &mut output, facts), Err(-2));
    assert!(output.iter().all(|value| *value == 42));
    contexts[CELLS - 1].flags = 0; ids[CELLS - 1] = -1;
    assert_eq!(prepare([None; VIEWS], &ids, &contexts, &mut output, facts), Err(-2));
    assert!(output.iter().all(|value| *value == 42));
    assert_eq!(prepare([None; VIEWS], &ids[..1], &contexts, &mut output, facts), Err(-1));
}

#[test]
fn java_float_conversion_handles_non_finite_and_signed_light_defaults() {
    for shade in [f32::NAN, f32::INFINITY, f32::NEG_INFINITY, -1.0, 17.0] {
        let expected_ao = ((shade * 4096.0) as i32 & 65535) << 12;
        assert_eq!(word(1, 0, shade, -1, 16), 15 | expected_ao | 1 << 28);
    }
    assert_eq!(std::mem::size_of::<Context>(), 8);
}

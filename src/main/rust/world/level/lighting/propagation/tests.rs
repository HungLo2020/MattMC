use super::*;

/// Sections keyed by position: stored layers, light-on, uniform block types.
struct Fake {
    layers: Map<([u8; LAYER], bool, u16)>,
    callbacks: usize,
}

impl Source for Fake {
    fn layer(&mut self, section: i64, layer: &mut [u8; LAYER]) -> Result<Option<bool>, Error> {
        self.callbacks += 1;
        Ok(self.layers.get(&section).map(|(bytes, on, _)| {
            layer.copy_from_slice(bytes);
            *on
        }))
    }

    fn blocks(&mut self, section: i64) -> Result<Blocks<'_>, Error> {
        Ok(Blocks::Uniform(self.layers.get(&section).ok_or(Error::Unsupported)?.2))
    }
}

fn tables() -> Tables {
    let air = Type { opacity: 1, emission: 0, empty: true, faces: [0; 6] };
    let stone = Type { opacity: 15, emission: 0, empty: true, faces: [0; 6] };
    Tables { state_types: vec![0, 1], types: vec![air, stone], faces: 1, occludes: vec![0], air: 0 }
}

#[test]
fn primary_owner_capture_matches_full_projection_for_all_palette_widths_and_both_engines() {
    use crate::world::level::chunk::live::Owner;
    struct Inputs { owner: Owner, types: Vec<u16>, live: bool }
    impl Source for Inputs {
        fn layer(&mut self, section: i64, layer: &mut [u8; LAYER]) -> Result<Option<bool>, Error> {
            if section != section_pos(0, 0, 0) { return Ok(None); }
            layer.fill(0); Ok(Some(true))
        }
        fn blocks(&mut self, _: i64) -> Result<Blocks<'_>, Error> {
            Ok(if self.live { Blocks::Live(&self.owner) } else { Blocks::Full(&self.types) })
        }
    }
    // State IDs deliberately differ from light-type IDs.
    let mut tables = tables(); tables.state_types = vec![1, 0];
    for bits in [0usize, 4, 5, 6, 7, 8, 15] {
        let palette = if bits == 15 { vec![] } else if bits == 0 { vec![1] } else { vec![0, 1] };
        let mut words = if bits == 0 { vec![] } else { vec![0u64; 4096usize.div_ceil(64 / bits)] };
        for i in 0..4096 {
            if bits == 0 { break; }
            let state = usize::from(i % 17 != 0);
            let per = 64 / bits; words[i / per] |= (state as u64) << (i % per * bits);
        }
        for sky in [false, true] {
            let make = |live| {
                let owner = Owner::load(bits, bits, &palette, &words, 2, 15).unwrap();
                let types = owner.with_state_reader(|reader|
                    (0..4096).map(|i| tables.state_type(reader.get(i) as u16)).collect());
                Inputs { owner, types, live }
            };
            let mut direct = make(true); let mut projected = make(false);
            let mut a = Engine::new(sky); let mut b = Engine::new(sky);
            let increase = [block_pos(8, 8, 8), increase_from_emission(15, true) as i64];
            let x = a.run(&tables, &mut direct, &[], &increase, 0).unwrap();
            let y = b.run(&tables, &mut projected, &[], &increase, 0).unwrap();
            assert_eq!(x.processed, y.processed, "bits={bits} sky={sky}");
            assert_eq!(a.written().collect::<Vec<_>>(), b.written().collect::<Vec<_>>());
            assert_eq!(a.affected(), b.affected());
        }
    }
}

fn level_at(engine: &Engine, pos: i64) -> Option<i32> {
    let key = section_of(pos);
    engine.written().find(|(k, _)| *k == key).map(|(_, layer)| {
        let i = index(pos);
        (layer[i >> 1] >> ((i & 1) * 4) & 15) as i32
    })
}

#[test]
fn packing_matches_java() {
    let pos = block_pos(-30_000_000, -2048, 29_999_999);
    assert_eq!((block_x(pos), block_y(pos), block_z(pos)), (-30_000_000, -2048, 29_999_999));
    assert_eq!(section_of(block_pos(-1, -1, 16)), section_pos(-1, -1, 1));
    // BlockPos.asLong(1, 2, 3) and SectionPos.asLong(1, 2, 3) from Java.
    assert_eq!(block_pos(1, 2, 3), 274877919234);
    assert_eq!(section_pos(1, 2, 3), 4398049656834);
}

#[test]
fn emitted_block_light_spreads_and_stops_at_section_without_storage() {
    let mut fake = Fake { layers: Map::default(), callbacks: 0 };
    fake.layers.insert(section_pos(0, 0, 0), ([0; LAYER], true, 0));
    let mut engine = Engine::new(false);
    let source = block_pos(8, 8, 8);
    let increase = [source, increase_from_emission(15, true) as i64];
    let outcome = engine.run(&tables(), &mut fake, &[], &increase, i32::MAX).unwrap();
    assert!(outcome.processed > 1);
    assert_eq!(level_at(&engine, source), Some(15));
    assert_eq!(level_at(&engine, block_pos(8, 8, 15)), Some(8));
    assert_eq!(level_at(&engine, block_pos(0, 0, 0)), Some(0));
    assert_eq!(level_at(&engine, block_pos(2, 8, 8)), Some(9));
    // Neighbouring sections are faulted once each and are not stored.
    assert_eq!(fake.callbacks, 7);
    assert!(engine.affected().contains(&section_pos(0, 1, 0)));
}

#[test]
fn reading_a_missing_section_is_rejected() {
    let mut fake = Fake { layers: Map::default(), callbacks: 0 };
    let mut engine = Engine::new(true);
    let increase = [block_pos(0, 0, 0), increase_from_emission(15, true) as i64];
    assert_eq!(engine.run(&tables(), &mut fake, &[], &increase, 0).err(), Some(Error::Unsupported));
}

#[test]
fn retained_inputs_produce_the_same_layers_work_and_notifications() {
    let layer = ([0; LAYER], true, 0);
    let key = section_pos(0, 0, 0);
    let mut copied = Fake { layers: Map::default(), callbacks: 0 };
    let mut owned = Fake { layers: Map::default(), callbacks: 0 };
    copied.layers.insert(key, layer); owned.layers.insert(key, layer);
    let input = [block_pos(8, 8, 8), increase_from_emission(15, true) as i64,
        block_pos(2, 2, 2), increase_from_emission(10, true) as i64];
    let mut old = Engine::new(false); let mut new = Engine::new(false);
    let a = old.run(&tables(), &mut copied, &[], &input, i32::MAX).unwrap();
    let b = new.run_owned(&tables(), &mut owned,
        [VecDeque::new(), VecDeque::from(input)], i32::MAX).unwrap();
    assert_eq!(a.processed, b.processed);
    assert_eq!(old.written().collect::<Vec<_>>(), new.written().collect::<Vec<_>>());
    assert_eq!(old.affected(), new.affected());
    assert_eq!(copied.callbacks, owned.callbacks);
    assert_eq!(new.input_journal()[1], VecDeque::from(input));
}

#[test]
fn a_failed_callback_preserves_inputs_and_its_separate_queue_additions() {
    struct Failure<'a>(&'a work::Queues);
    impl Source for Failure<'_> {
        fn layer(&mut self, _: i64, _: &mut [u8; LAYER]) -> Result<Option<bool>, Error> {
            self.0.data.lock().unwrap()[1].extend([99, 100]);
            Err(Error::Callback)
        }
        fn blocks(&mut self, _: i64) -> Result<Blocks<'_>, Error> { unreachable!() }
    }
    let queues = work::Queues::default();
    let input = [block_pos(8, 8, 8), increase_from_emission(15, true) as i64];
    queues.data.lock().unwrap()[1].extend(input);
    let mut engine = Engine::new(false);
    assert_eq!(engine.run_owned(&tables(), &mut Failure(&queues), queues.take(), i32::MAX).err(), Some(Error::Callback));
    queues.finish(engine.input_journal(), true);
    assert_eq!(queues.data.lock().unwrap()[1], VecDeque::from([99, 100, input[0], input[1]]));
    assert_eq!(engine.written_count(), 0);
}


#[test]
fn sky_section_fills_above_sources_and_enqueues_edges() {
    // Column (0, 0) has its lowest source at 20; its west neighbour's lies at 25.
    let mut columns = [i32::MIN; seed::COLUMNS];
    columns[..256].fill(40);
    columns[0] = 20;
    columns[768] = 25;
    let mut layer = [0u8; LAYER];
    let mut entries = Vec::new();
    let (wrote, below) = seed::sky_section(&mut layer, &columns, 16, 0, 0, &mut |pos, entry| entries.push((pos, entry)));
    assert!(wrote && !below);
    let at = |x: usize, y: usize, z: usize| layer[(y << 8 | z << 4 | x) >> 1] >> (((y << 8 | z << 4 | x) & 1) * 4) & 15;
    assert_eq!((at(0, 31 - 16, 0), at(0, 20 - 16, 0), at(0, 19 - 16, 0), at(1, 31 - 16, 0)), (15, 15, 0, 0));
    // Below the west source the column is lit sideways (west bit), and its lowest source also points down.
    let source = |y: i32| entries.iter().find(|(pos, _)| *pos == block_pos(0, y, 0)).map(|(_, e)| *e);
    assert_eq!(source(20), Some(15 | 1 << 4 | 1 << 8));
    assert_eq!(source(24), Some(15 | 1 << 8));
    assert_eq!(source(25), None);
    assert_eq!(entries.len(), 5);
}

#[test]
fn registry_tables_number_types_in_state_order() {
    use crate::content::block::{Builder, FaceId, StateFacts, StateFlags};
    let state = |flags: u16, light_block: u8, emission: u8, face: u16| StateFacts {
        flags: StateFlags(flags),
        light_block,
        emission,
        light_faces: [FaceId(face); 6],
        ..StateFacts::default()
    };
    let empty = StateFlags::LIGHT_EMPTY_SHAPE.0;
    let mut b = Builder::new();
    b.block("minecraft:stone", &[], 0, vec![state(0, 15, 0, 1)]).unwrap();
    b.block("minecraft:air", &[], 0, vec![state(StateFlags::AIR.0 | empty, 0, 0, 0)]).unwrap();
    b.block("minecraft:cave_air", &[], 0, vec![state(StateFlags::AIR.0 | empty, 0, 0, 0)]).unwrap();
    b.block("minecraft:glowstone", &[], 0, vec![state(0, 15, 15, 1)]).unwrap();
    b.block("custom", &[], 0, vec![state(StateFlags::CUSTOM.0, 15, 0, 1)]).unwrap();
    b.block("minecraft:deepslate", &[], 0, vec![state(0, 15, 0, 1)]).unwrap();
    let registry = b.finish(2, vec![0, 0, 1, 1]).unwrap();
    let t = Tables::from_registry(&registry).unwrap();
    assert_eq!(t.state_types, vec![0, 1, 1, 2, super::UNSUPPORTED, 0]);
    assert_eq!(t.types[0], Type { opacity: 15, emission: 0, empty: false, faces: [1; 6] });
    // getLightBlock 0 still blocks one level.
    assert_eq!(t.types[1], Type { opacity: 1, emission: 0, empty: true, faces: [0; 6] });
    assert_eq!(t.types[2].emission, 15);
    assert_eq!(t.air, 1);
    assert_eq!((t.faces, t.occludes.clone()), (2, vec![0, 0, 1, 1]));
    assert_eq!(t.state_type(5), 0);
    assert_eq!(t.state_type(6), super::UNSUPPORTED);
    assert_eq!(t.state_type(u16::MAX), super::UNSUPPORTED);
}

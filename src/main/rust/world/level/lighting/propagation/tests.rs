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

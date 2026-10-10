use super::*;
use std::io::Read;
struct Reader<'a>(&'a [u8]);
impl Reader<'_> {
    fn byte(&mut self) -> u8 {
        let v = self.0[0];
        self.0 = &self.0[1..];
        v
    }
    fn int(&mut self) -> i32 {
        let v = i32::from_be_bytes(self.0[..4].try_into().unwrap());
        self.0 = &self.0[4..];
        v
    }
    fn text(&mut self) -> String {
        let n = u16::from_be_bytes(self.0[..2].try_into().unwrap()) as usize;
        let s = String::from_utf8(self.0[2..2 + n].to_vec()).unwrap();
        self.0 = &self.0[2 + n..];
        s
    }
    fn bytes(&mut self, n: usize) -> Box<[u8]> {
        let b = self.0[..n].into();
        self.0 = &self.0[n..];
        b
    }
}
fn live(ids: &[u32]) -> live::Owner {
    let mut words = vec![0u64; 1024];
    for (i, &id) in ids.iter().enumerate() {
        words[i / 4] |= (id as u64) << ((i % 4) * 15);
    }
    live::Owner::load(15, 9, &[], &words, 31809, 15).unwrap()
}
#[test]
fn actual_frozen_all_saved_lights_and_work_counts_across_three_passes() {
    let mut bytes = Vec::new();
    flate2::read::GzDecoder::new(
        include_bytes!("../../../../../../test/resources/world/lighting/frozen-dh-lighting.bin.gz")
            .as_slice(),
    )
    .read_to_end(&mut bytes)
    .unwrap();
    let mut r = Reader(&bytes);
    assert_eq!(r.int(), 0x44484c31);
    assert_eq!(r.int(), 7);
    for (name, pin) in [
        (
            "com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper",
            "68c7e9206b17b042ecc9307d02a9fd6dcd3fcf0fbfc5ba02b75951c11f4400cd",
        ),
        (
            "com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper",
            "71dada60ecbd485877e412bf0a2e7b8f437aa09e7f5579817e97f21c55dd69e6",
        ),
        (
            "net.minecraft.world.level.chunk.ProtoChunk",
            "3430938c5556f13efcd286e6312b50312ec7e994673553022d3a70ff4881dbdc",
        ),
        (
            "com.seibel.distanthorizons.core.generation.DhLightingEngine",
            "f08e6005aa6dab745989ce904d04a8fc112f40bfc73f577587bdbc66d86ef1bf",
        ),
        (
            "com.seibel.distanthorizons.core.generation.AdjacentChunkHolder",
            "c0682551e6936b98d487b34ee774d0cebff82fa23bbe7bb384ed279ae0e340d3",
        ),
        (
            "com.seibel.distanthorizons.core.wrapperInterfaces.chunk.ChunkLightStorage",
            "bd2483c1cadc05ddd6216ad57cd2c714db8704525664fc2b9787301f9464a983",
        ),
        (
            "com.seibel.distanthorizons.core.enums.EDhDirection",
            "005cca8ed4f9526cd5e9e9cc1070467ea24555bff75ae85c90828c01a0bf8f58",
        ),
    ] {
        assert_eq!(r.text(), name);
        assert_eq!(r.text(), pin);
    }
    assert_eq!(
        r.text(),
        "ee865336a6a8cc6d801dada6a0b54da98564a6ed6d216df3cefb4666f7fc187b"
    );
    r.text();
    let count = r.int() as usize;
    assert_eq!(count, 31809);
    let fixture = crate::world::level::chunk::dh_heightmaps::fixture::load();
    let mut emission = Vec::new();
    for id in 0..count {
        assert_eq!(r.byte(), fixture.expected[id].1);
        emission.push(r.byte());
    }
    assert_eq!(r.int(), 20);
    for _ in 0..20 {
        let name = r.text();
        let count = r.int() as usize;
        let mut slots = Vec::new();
        let mut mins = Vec::new();
        let mut heights = Vec::new();
        let mut owners = Vec::new();
        let mut fields = Vec::new();
        for _ in 0..count {
            slots.push(r.int() as usize);
            let min_y = r.int();
            mins.push(min_y);
            let height = r.int() as usize;
            heights.push(Heightmaps {
                min: r.int(),
                max: r.int(),
                solid: [0; 256],
                blocking: [0; 256],
            });
            owners.push(
                (0..height / 16)
                    .map(|_| live(&(0..4096).map(|_| r.int() as u32).collect::<Vec<_>>()))
                    .collect::<Vec<_>>(),
            );
            let n = r.int();
            let sources = (0..n).map(|_| r.int() as u32).collect::<Arc<[u32]>>();
            fields.push(Field::new(min_y, &r.bytes(height * 256), sources));
        }
        let n = r.int();
        let order = (0..n).map(|_| r.int() as i8).collect::<Vec<_>>();
        assert_eq!(r.int(), 3);
        let refs = owners
            .iter()
            .map(|s| s.iter().collect::<Vec<_>>())
            .collect::<Vec<_>>();
        if name != "stale-emitter-cache" {
            for i in 0..count {
                let sources =
                    sources::build(&refs[i], mins[i], |id| emission.get(id as usize).copied())
                        .unwrap();
                assert_eq!(
                    sources.positions, fields[i].sources,
                    "Frozen first emitter enumeration {name} slot{}",
                    slots[i]
                );
            }
        }
        for phase in 0..3 {
            let sky = r.int() as u8;
            let block = r.byte() != 0;
            let update_sky = r.byte() != 0;
            let work = r.int() as usize;
            let inputs = (0..count)
                .map(|i| Input {
                    slot: slots[i],
                    sections: &refs[i],
                    heights: &heights[i],
                    min_y: mins[i],
                    previous: Some(&fields[i]),
                    cached_sources: None,
                })
                .collect::<Vec<_>>();
            let result = build(
                &inputs,
                &order,
                &fixture.catalog,
                |id| emission.get(id as usize).copied(),
                sky,
                block,
                update_sky,
            )
            .unwrap_or_else(|| panic!("admission {name} phase{phase}"));
            assert_eq!(result.iterations, work, "{name} phase{phase} work");
            for (i, (slot, field)) in result.fields.into_iter().enumerate() {
                assert_eq!(slot, slots[i]);
                let expected = r.bytes(field.len());
                let actual = field.expand();
                if actual.as_slice() != expected.as_ref() {
                    let differences = actual
                        .iter()
                        .zip(expected.iter())
                        .enumerate()
                        .filter(|(_, (a, b))| a != b)
                        .take(10)
                        .collect::<Vec<_>>();
                    panic!("{name} phase{phase} slot{slot} mismatches {differences:?}");
                }
                let block_correct = r.byte() != 0;
                let sky_correct = r.byte() != 0;
                assert_eq!(block_correct, slot == 4);
                assert_eq!(sky_correct, slot == 4);
                fields[i] = field;
            }
        }
    }
    assert!(r.0.is_empty());
}
#[test]
fn rejects_invalid_admission_without_touching_published_lights() {
    let fixture = crate::world::level::chunk::dh_heightmaps::fixture::load();
    let air = live(&vec![0; 4096]);
    let sections = [&air];
    let heights = Heightmaps {
        min: 0,
        max: 16,
        solid: [0; 256],
        blocking: [0; 256],
    };
    let old = Field::new(0, &vec![0x97; 4096], Arc::from([]));
    let retained = old.clone();
    let i = Input {
        slot: 4,
        sections: &sections,
        heights: &heights,
        min_y: 0,
        previous: Some(&old),
        cached_sources: None,
    };
    assert!(build(&[i], &[9], &fixture.catalog, |_| Some(0), 15, true, true).is_none());
    assert_eq!(old, retained);
    assert!(build(&[], &[], &fixture.catalog, |_| Some(0), 15, true, true).is_none());
    let mut q = Queue::new(1);
    assert!(q.push(47, 4096, 47, 15).is_some());
    assert!(q.push(0, 0, 0, 0).is_none());
    assert_eq!(q.pop(15), Some((47, 4096, 47)));
    assert!(q.push(48, 0, 0, 15).is_none());
}

#[test]
fn published_uniform_sections_are_constants_and_dense_sections_are_packed() {
    let empty = Field::new(-64, &vec![0xf0; 24 * 4096], Arc::from([]));
    assert!(empty.block.data.is_empty());
    assert!(empty.sky.data.is_empty());
    assert_eq!(empty.block.offsets.len(), 24);
    assert_eq!(empty.sky.offsets.len(), 24);
    assert_eq!(empty.level(15, 319, 15, true), 15);
    assert_eq!(empty.level(0, -65, 0, true), 0);
    let mut cells = vec![0xf0; 8192];
    cells[4096 + 1] = 0xf9;
    cells[4096 + 2] = 0x70;
    let dense = Field::new(-16, &cells, Arc::from([4097]));
    assert_eq!(dense.block.data.len(), 2048);
    assert_eq!(dense.sky.data.len(), 2048);
    assert_eq!(dense.expand(), cells);
    assert_eq!(dense.level(1, 0, 0, false), 9);
    assert_eq!(dense.level(2, 0, 0, true), 7);
    assert!(Arc::ptr_eq(&dense.sources, &dense.clone().sources));
}

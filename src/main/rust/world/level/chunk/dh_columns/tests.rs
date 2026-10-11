use super::*;
use crate::content::block::StateFlags;
use std::io::Read;
struct Reader(Vec<u8>, usize);
impl Reader {
    fn bytes(&mut self, n: usize) -> &[u8] {
        let start = self.1;
        self.1 += n;
        &self.0[start..self.1]
    }
    fn i(&mut self) -> i32 {
        i32::from_be_bytes(self.bytes(4).try_into().unwrap())
    }
    fn long(&mut self) -> u64 {
        u64::from_be_bytes(self.bytes(8).try_into().unwrap())
    }
    fn byte(&mut self) -> u8 {
        self.bytes(1)[0]
    }
    fn text(&mut self) -> String {
        let len = u16::from_be_bytes(self.bytes(2).try_into().unwrap()) as usize;
        String::from_utf8(self.bytes(len).to_vec()).unwrap()
    }
}
fn blocks(ids: &[u32]) -> live::Owner {
    let mut words = vec![0; 1024];
    for (i, &id) in ids.iter().enumerate() {
        words[i / 4] |= (id as u64) << ((i % 4) * 15);
    }
    live::Owner::load(15, 9, &[], &words, 31809, 15).unwrap()
}
fn biomes(ids: &[u32]) -> biomes::Owner {
    let mut words = vec![0; 8];
    for (i, &id) in ids.iter().enumerate() {
        words[i / 9] |= (id as u64) << ((i % 9) * 7);
    }
    biomes::Owner::load(7, 4, &[], &words, 68, 7).unwrap()
}
#[test]
fn all_actual_frozen_columns_material_order_and_hashes_match() {
    let mut bytes = Vec::new();
    flate2::read::GzDecoder::new(
        include_bytes!("../../../../../../test/resources/world/chunk/frozen-dh-columns.bin.gz")
            .as_slice(),
    )
    .read_to_end(&mut bytes)
    .unwrap();
    let mut r = Reader(bytes, 0);
    assert_eq!(r.i(), 0x44484331);
    assert_eq!(r.i(), 11);
    for _ in 0..11 {
        assert!(!r.text().is_empty());
        assert_eq!(r.text().len(), 64);
    }
    assert_eq!(
        r.text(),
        "ee865336a6a8cc6d801dada6a0b54da98564a6ed6d216df3cefb4666f7fc187b"
    );
    assert!(r.text().contains("Supplemental CPU fixture"));
    let mut biome_hashes = Vec::new();
    for _ in 0..r.i() {
        r.text();
        biome_hashes.push(r.i());
    }
    let mut state_hashes = Vec::new();
    for _ in 0..r.i() {
        r.text();
        state_hashes.push(r.i());
    }
    assert_eq!((biome_hashes.len(), state_hashes.len()), (68, 31809));
    let fixture = super::super::dh_heightmaps::fixture::load();
    assert_eq!(r.i(), 32);
    let mut total_points = 0;
    for _ in 0..32 {
        let name = r.text();
        let chunk_x = r.i();
        let chunk_z = r.i();
        let min_y = r.i();
        let height = r.i() as usize;
        let min = r.i();
        let max = r.i();
        let states: Vec<_> = (0..height * 256).map(|_| r.i() as u32).collect();
        let biome_ids: Vec<_> = (0..height / 16 * 64).map(|_| r.i() as u32).collect();
        let lights = Field::new(min_y, r.bytes(height * 256), std::sync::Arc::from([]));
        let mut heights = Heightmaps {
            min,
            max,
            solid: [0; 256],
            blocking: [0; 256],
        };
        for i in 0..256 {
            heights.solid[i] = r.i();
            heights.blocking[i] = r.i();
        }
        let pos = r.long();
        assert_eq!(r.byte(), 0);
        assert_eq!(r.byte(), 1);
        let source_hash = r.i();
        let mapping_hash = r.i();
        let mut materials = Vec::new();
        for _ in 0..r.i() {
            materials.push(Material {
                biome: r.i() as u32,
                state: r.i() as u32,
            });
            r.text();
            r.text();
        }
        let mut expected = Vec::new();
        let mut compression = None;
        for _ in 0..4096 {
            let generation = r.byte();
            let mode = r.byte();
            let count = r.i();
            assert!(count >= 0);
            let points: Vec<_> = (0..count).map(|_| r.long()).collect();
            if count != 0 {
                assert_eq!(generation, 9);
                assert_eq!(mode, 1);
                compression = Some(mode);
            }
            expected.push(points);
        }
        let block_owners: Vec<_> = states.chunks_exact(4096).map(blocks).collect();
        let biome_owners: Vec<_> = biome_ids.chunks_exact(64).map(biomes).collect();
        let sections: Vec<_> = block_owners.iter().zip(&biome_owners).collect();
        let output = build(
            Input {
                sections: Sections::Columns(&sections, None),
                heights: &heights,
                lights: &lights,
                min_y,
                chunk_x,
                chunk_z,
                compression: compression.unwrap(),
            },
            |id| {
                let value = fixture.catalog.dh(id)?;
                let flags = fixture.flags[id as usize];
                let air = flags.contains(StateFlags::AIR);
                Some(Facts {
                    state: if air { 0 } else { id },
                    air,
                    solid: value.solid,
                    liquid: flags.contains(StateFlags::HAS_FLUID),
                    opacity: value.opacity,
                })
            },
        )
        .unwrap();
        drop(sections);
        drop(block_owners);
        drop(biome_owners);
        drop(lights);
        drop(heights);
        assert_eq!(output.pos, pos, "{name} position");
        assert_eq!(
            output.materials.as_ref(),
            materials,
            "{name} material IDs/order"
        );
        let ox = (chunk_x & 3) as usize * 16;
        let oz = (chunk_z & 3) as usize * 16;
        for x in 0..16 {
            for z in 0..16 {
                assert_eq!(
                    output.column(x, z),
                    expected[(x + ox) * 64 + z + oz],
                    "{name} {x},{z}"
                );
                total_points += output.column(x, z).len();
            }
        }
        assert_eq!(
            output.source_hash(),
            source_hash,
            "{name} full data-source hash"
        );
        let list_hash = output.materials.iter().fold(1i32, |h, m| {
            let pair = 31i32
                .wrapping_add(biome_hashes[m.biome as usize])
                .wrapping_mul(31)
                .wrapping_add(state_hashes[m.state as usize]);
            h.wrapping_mul(31).wrapping_add(pair)
        });
        let hash = output
            .materials
            .iter()
            .fold((pos ^ (pos >> 32)) as i32, |h, _| {
                h.wrapping_mul(31).wrapping_add(list_hash)
            });
        assert_eq!(hash, mapping_hash, "{name} original mapping hash");
    }
    assert_eq!(r.1, r.0.len());
    assert!(total_points > 100_000);
}

#[test]
fn complete_ordered_occlusion_passes_match_actual_frozen_saved_columns() {
    let mut bytes = Vec::new();
    flate2::read::GzDecoder::new(include_bytes!("../../../../../../test/resources/world/chunk/frozen-dh-occlusion.bin.gz").as_slice())
        .read_to_end(&mut bytes).unwrap();
    let mut r = Reader(bytes, 0);
    assert_eq!(r.i(), 0x44484f31); assert_eq!(r.i(), 6);
    for _ in 0..6 { assert!(!r.text().is_empty()); assert_eq!(r.text().len(), 64); }
    assert_eq!(r.text().len(), 64); assert!(r.text().contains("Supplemental CPU evidence"));
    assert_eq!(r.i(), 32);
    let mut removed = 0;
    for _ in 0..32 {
        let name = r.text(); let count = r.i(); let mut opacities = Vec::new();
        for _ in 0..count { r.i(); opacities.push(r.byte()); }
        let mut columns: Vec<Vec<u64>> = (0..4096).map(|_| { let n = r.i(); (0..n).map(|_| r.long()).collect() }).collect();
        let policy = materials::Materials(opacities.iter().map(|&opacity| materials::Fact { opacity, air: false, liquid: false }).collect());
        let mut batch: Vec<_> = columns.iter().cloned().map(mutable::Column::new).collect();
        for pass in 0..2 {
            let raw: Vec<usize> = batch.iter_mut().flat_map(|c| [c as *mut mutable::Column as usize, 0, 0]).collect();
            let mut output = vec![0usize; 4096 * 4];
            unsafe { assert_eq!(occlusion_pass::mattmc_dh_columns_cull_pass(raw.as_ptr(), &policy, output.as_mut_ptr()), 1); }
            for x in 1..63 { for z in 1..63 {
                let index = x * 64 + z;
                if columns[index].len() <= 1 { continue; }
                // Temporarily remove the center owner to borrow four immutable
                // neighboring owners; mutation still follows Frozen x/z order.
                let mut center = std::mem::take(&mut columns[index]);
                let before = center.len();
                super::occlusion::cull(&mut center, [&columns[index + 64], &columns[index - 64], &columns[index + 1], &columns[index - 1]], &|p| opacities[p as u32 as usize] < 16);
                removed += before - center.len(); columns[index] = center;
            }}
            for (index, column) in columns.iter().enumerate() {
                assert_eq!(batch[index].points(), column, "{name} whole pass {pass} column {index}");
                assert_eq!(output[index * 4 + 1], column.len());
                assert_eq!(r.i() as usize, column.len(), "{name} pass {pass} column {index}");
                for &point in column { assert_eq!(r.long(), point, "{name} pass {pass} column {index}"); }
            }
        }
    }
    assert!(removed > 0, "Saved corpus must exercise actual mutation");
    assert_eq!(r.1, r.0.len());
}

#[test]
fn all_32768_parent_reductions_match_actual_frozen_production_columns() {
    let mut bytes = Vec::new();
    flate2::read::GzDecoder::new(include_bytes!("../../../../../../test/resources/world/chunk/frozen-dh-reduction.bin.gz").as_slice())
        .read_to_end(&mut bytes).unwrap();
    let mut r = Reader(bytes, 0);
    assert_eq!(r.i(), 0x44485231); assert_eq!(r.i(), 5);
    for _ in 0..5 { assert!(!r.text().is_empty()); assert_eq!(r.text().len(), 64); }
    assert_eq!(r.text(), "3419a7da318caf78ddec3df594f83841b78a5307033577d7d3073df50b9b01eb");
    assert!(r.text().contains("Supplemental CPU evidence")); assert_eq!(r.i(), 32);
    let mut nonempty = 0;
    for _ in 0..32 {
        let name = r.text(); let count = r.i(); let mut facts = Vec::new();
        for _ in 0..count {
            r.i(); facts.push(super::materials::Fact { opacity: r.byte(), air: r.byte() != 0, liquid: r.byte() != 0 });
        }
        let columns: Vec<Vec<u64>> = (0..4096).map(|_| { let n = r.i(); (0..n).map(|_| r.long()).collect() }).collect();
        let materials = super::materials::Materials(facts.clone().into_boxed_slice());
        let inputs: Vec<_> = columns.iter().map(Vec::as_slice).collect();
        let identity: Vec<_> = (0..count).collect();
        let quadrant = super::parent::build(&inputs, &materials, &identity).unwrap();
        for x in (0..64).step_by(2) { for z in (0..64).step_by(2) {
            let index = x * 64 + z;
            let output = super::reduction::reduce([&columns[index], &columns[index + 1], &columns[index + 64], &columns[index + 65]], &facts).unwrap();
            assert_eq!(quadrant.column(x / 2 * 32 + z / 2), output, "{name} native quadrant {x},{z}");
            assert_eq!(r.i() as usize, output.len(), "{name} {x},{z}");
            for &point in &output { assert_eq!(r.long(), point, "{name} {x},{z}"); }
            nonempty += usize::from(!output.is_empty());
        }}
    }
    assert!(nonempty > 1000); assert_eq!(r.1, r.0.len());
}

#[test]
fn parent_reduction_declines_unencodable_output_and_invalid_ids_before_publication() {
    use super::{materials::Fact, reduction::reduce};
    let facts = [Fact { opacity: 16, air: false, liquid: false }];
    let full_world = [encode(0, 2048, 2048, 0, 0), encode(0, 2048, 0, 0, 0)];
    assert!(reduce([&full_world; 4], &facts).is_none());
    assert!(reduce([&[encode(1, 1, 0, 0, 0)]; 4], &facts).is_none());
    assert!(reduce([&[encode(0, 0, 1, 0, 0)]; 4], &facts).is_none());
    assert_eq!(reduce([&[]; 4], &facts), Some(Vec::new()));
    assert_eq!(reduce([&[0]; 4], &facts), Some(Vec::new()));
}

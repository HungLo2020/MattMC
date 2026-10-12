//! DH full-resolution columns built from existing authoritative CPU owners.
//! Output points and material IDs own their storage independently of inputs.
use super::{biomes, counters, dh_heightmaps::Heightmaps, live, inputs::Sections};
use crate::world::level::lighting::dh::Field;
use std::collections::HashMap;
mod ffi;
mod id_map;
mod mutable;
mod occlusion;
mod occlusion_pass;
mod same_detail;
mod materials;
mod reduction;
mod parent;
mod contributors;
mod tint;
mod source;
mod metadata;
mod codec;
mod metadata_codec;

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
pub(crate) struct Material {
    pub(crate) biome: u32,
    pub(crate) state: u32,
}
#[derive(Clone, Copy)]
pub(crate) struct Facts {
    pub(crate) state: u32,
    pub(crate) air: bool,
    pub(crate) solid: bool,
    pub(crate) liquid: bool,
    pub(crate) opacity: u8,
}
pub(crate) struct Input<'a> {
    pub(crate) sections: Sections<'a>,
    pub(crate) heights: &'a Heightmaps,
    pub(crate) lights: &'a Field,
    pub(crate) min_y: i32,
    pub(crate) chunk_x: i32,
    pub(crate) chunk_z: i32,
    pub(crate) compression: u8,
}
pub(crate) struct Columns {
    pub(crate) pos: u64,
    pub(crate) chunk_x: i32,
    pub(crate) chunk_z: i32,
    pub(crate) compression: u8,
    pub(crate) offsets: Box<[u32; 257]>,
    pub(crate) points: Box<[u64]>,
    pub(crate) materials: Box<[Material]>,
}
impl Columns {
    pub(crate) fn column(&self, x: usize, z: usize) -> &[u64] {
        let index = x * 16 + z;
        &self.points[self.offsets[index] as usize..self.offsets[index + 1] as usize]
    }
    /// Frozen's full 64x64 data-source hash, including untouched empty columns.
    pub(crate) fn source_hash(&self) -> i32 {
        fn fold(hash: i32, item: i32) -> i32 {
            hash.wrapping_mul(31).wrapping_add(item)
        }
        let ox = (self.chunk_x & 3) as usize * 16;
        let oz = (self.chunk_z & 3) as usize * 16;
        let (mut data, mut generation, mut compression) = (1i32, 1i32, 1i32);
        for x in 0..64 {
            for z in 0..64 {
                let occupied = (ox..ox + 16).contains(&x) && (oz..oz + 16).contains(&z);
                let column_hash = if occupied {
                    self.column(x - ox, z - oz)
                        .iter()
                        .fold(1, |h, &p| fold(h, (p ^ (p >> 32)) as i32))
                } else {
                    1
                };
                data = fold(data, column_hash);
                generation = fold(generation, if occupied { 9 } else { 0 });
                compression = fold(
                    compression,
                    if occupied {
                        self.compression as i8 as i32
                    } else {
                        0
                    },
                );
            }
        }
        let pos_hash = (self.pos ^ (self.pos >> 32)) as i32;
        fold(pos_hash, data)
            .wrapping_mul(17)
            .wrapping_add(generation)
            .wrapping_mul(43)
            .wrapping_add(compression)
    }
}
fn encode(id: u32, height: i32, bottom: i32, block: u8, sky: u8) -> u64 {
    u64::from(id & 0x7fff_ffff)
        | ((height as u64 & 4095) << 32)
        | ((bottom as u64 & 4095) << 44)
        | (u64::from(sky) << 56)
        | (u64::from(block) << 60)
}
/// Borrow section owners under their normal locks. Unsupported policy declines
/// before publication; the original caller's chunk exclusion remains required.
pub(crate) fn build(
    input: Input<'_>,
    facts: impl Fn(u32) -> Option<Facts>,
) -> Option<Box<Columns>> {
    let count = input.sections.len();
    let height = count.checked_mul(16)?;
    let max = input.min_y.checked_add(height as i32)?;
    if !(1..=255).contains(&count)
        || input.min_y % 16 != 0
        || input.min_y.unsigned_abs() > 1_000_000
        || input.lights.min_y != input.min_y
        || input.lights.len() != height * 256
        || input.heights.min < input.min_y
        || !input.sections.valid_counts()
        || input.heights.min >= max
        || input.heights.max <= input.heights.min
        || input.heights.max > max
    {
        return None;
    }
    let limit = input.sections.biome(0)?.limit;
    let mut states = Vec::with_capacity(height * 256);
    let mut biome_cells = Vec::with_capacity(count * 64);
    let mut policy = HashMap::new();
    for section in 0..count {
        let blocks = input.sections.block(section);
        let biomes = input.sections.biome(section)?;
        if !biomes.is_valid() || biomes.limit != limit {
            return None;
        }
        blocks.with_state_reader(|reader| {
            if !reader.all_states(|id| {
                if policy.contains_key(&id) {
                    return true;
                }
                if let Some(value) = facts(id) {
                    policy.insert(id, value);
                    true
                } else {
                    false
                }
            }) {
                return None;
            }
            states.extend((0..4096).map(|i| reader.get(i)));
            Some(())
        })?;
        let (_, cells, _) = biomes.snapshot();
        if cells.iter().any(|&id| id >= limit) {
            return None;
        }
        biome_cells.extend(cells);
    }
    for section in 0..count {
            if input.sections.counter(section).is_some_and(|c| c.values()[0] == 0) {
                states[section * 4096..(section + 1) * 4096].fill(0);
            }
    }
    if states.contains(&0) { policy.entry(0).or_insert(facts(0)?); }
    let air = Facts {
        state: 0,
        air: true,
        solid: false,
        liquid: false,
        opacity: 0,
    };
    let block_at = |x: usize, y: i32, z: usize| -> Facts {
        if y < input.min_y || y >= max {
            air
        } else {
            policy[&states[(y - input.min_y) as usize * 256 + z * 16 + x]]
        }
    };
    let biome_at = |x: usize, y: i32, z: usize| -> u32 {
        let y = (y - input.min_y).clamp(0, height as i32 - 1) as usize / 4;
        biome_cells[y * 16 + z / 4 * 4 + x / 4]
    };
    let mut materials = Vec::new();
    let mut ids = HashMap::new();
    let mut mapped = |material: Material| -> u32 {
        *ids.entry(material).or_insert_with(|| {
            let id = materials.len() as u32;
            materials.push(material);
            id
        })
    };
    let mut offsets = Box::new([0; 257]);
    let mut points = Vec::new();
    for x in 0..16 {
        for z in 0..16 {
            let column = x * 16 + z;
            let mut last_y = max;
            let mut current = air;
            let mut biome = biome_at(x, max, z);
            let mut id = mapped(Material { biome, state: 0 });
            let (mut block, mut sky) = (0, 15);
            let mut y = input.heights.solid[column].max(input.heights.blocking[column]);
            if y < input.heights.min || y >= max {
                return None;
            }
            while !block_at(x, y, z).air && y < max {
                y += 1;
            }
            let mut force = false;
            while y >= input.heights.min {
                let next = block_at(x, y, z);
                let next_biome = biome_at(x, y, z);
                if next_biome != biome || next.state != current.state || force {
                    force =
                        !current.air && !current.solid && !current.liquid && current.opacity != 16;
                    points.push(encode(id, last_y - y, y + 1 - input.min_y, block, sky));
                    current = next;
                    biome = next_biome;
                    id = mapped(Material {
                        biome,
                        state: current.state,
                    });
                    block = input.lights.level(x, y + 1, z, false);
                    sky = input.lights.level(x, y + 1, z, true);
                    last_y = y;
                }
                y -= 1;
            }
            points.push(encode(id, last_y - y, y + 1 - input.min_y, block, sky));
            offsets[column + 1] = u32::try_from(points.len()).ok()?;
        }
    }
    let pos = 6
        | (((input.chunk_x.div_euclid(4) as u64) & 0x0fff_ffff) << 8)
        | (((input.chunk_z.div_euclid(4) as u64) & 0x0fff_ffff) << 36);
    Some(Box::new(Columns {
        pos,
        chunk_x: input.chunk_x,
        chunk_z: input.chunk_z,
        compression: input.compression,
        offsets,
        points: points.into_boxed_slice(),
        materials: materials.into_boxed_slice(),
    }))
}
#[cfg(test)]
mod tests;

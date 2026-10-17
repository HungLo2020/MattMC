//! DH change detection over retained live chunk inputs, independent of rendering.
//! The emitter callback boundary separates sampling from finishing the same hash.
use super::{dh_heightmaps::Heightmaps, inputs::Sections};
use std::sync::RwLock;
mod ffi;
#[cfg(test)] mod tests;

pub(crate) struct Facts {
    states: Box<[Option<i32>]>,
    biomes: Box<[Option<i32>]>,
}
pub(crate) struct Domain(RwLock<Facts>);
impl Domain {
    fn new(states: usize, biomes: usize) -> Option<Self> {
        if !(1..=65535).contains(&states) || !(1..=65535).contains(&biomes) { return None; }
        Some(Self(RwLock::new(Facts { states: vec![None; states].into_boxed_slice(), biomes: vec![None; biomes].into_boxed_slice() })))
    }
    /// Objects.hash(serializedIdentity), using Java's UTF-16 and integer wrapping.
    fn register(&self, biome: bool, id: usize, text: &[u16]) -> bool {
        let hash = text.iter().fold(0i32, |h, &c| h.wrapping_mul(31).wrapping_add(c as i32)).wrapping_add(31);
        let mut facts = self.0.write().unwrap();
        let table = if biome { &mut facts.biomes } else { &mut facts.states };
        let Some(slot) = table.get_mut(id) else { return false; };
        *slot = Some(hash); true
    }
}
const STEP: i32 = 227i32.wrapping_mul(701).wrapping_mul(137);
fn triple(hash: i32, state: i32, biome: i32, y: i32) -> i32 {
    hash.wrapping_mul(227).wrapping_add(state).wrapping_mul(701).wrapping_add(biome).wrapping_mul(137).wrapping_add(y)
}
fn valid(sections: Sections<'_>, min: i32) -> Option<i32> {
    if !(1..=256).contains(&sections.len()) || min % 16 != 0 || min.unsigned_abs() > 1_000_000 || !sections.valid_counts() { return None; }
    min.checked_add(sections.len() as i32 * 16)
}
/// Read each primary block owner once per section, without a dense voxel copy.
/// Per-column affine contributions restore Frozen's exact x/z/y sampling order.
pub(crate) fn sample(sections: Sections<'_>, min: i32, heights: &Heightmaps, facts: &Facts) -> Option<i32> {
    let max = valid(sections, min)?;
    if heights.min < min || heights.max > max || heights.min >= heights.max || heights.min % 16 != 0 || heights.max % 16 != 0 { return None; }
    let air = *facts.states.first()?.as_ref()?;
    let mut contributions = vec![[0i32; 64]; sections.len()];
    let mut powers = vec![1i32; sections.len()];
    let mut biome_cells = Vec::with_capacity(sections.len());
    let mut surfaces = [air; 256];
    for section in 0..sections.len() {
        let owner = sections.biome(section)?;
        if !owner.is_valid() || owner.limit as usize != facts.biomes.len() { return None; }
        let (_, cells, _) = owner.snapshot();
        if cells.iter().any(|&id| facts.biomes.get(id as usize).is_none_or(Option::is_none)) { return None; }
        let base = min + section as i32 * 16;
        let sampled = (heights.min..heights.max).contains(&base);
        let empty = sections.counter(section)?.values()[0] == 0;
        sections.block(section).with_state_reader(|reader| {
            if !empty && !reader.all_states(|id| facts.states.get(id as usize).is_some_and(Option::is_some)) { return None; }
            if sampled {
                powers[section] = STEP.wrapping_pow(8);
                for x in (0..16).step_by(2) {
                    for z in (0..16).step_by(2) {
                        let column = x / 2 * 8 + z / 2;
                        for y in (0..16).step_by(2) {
                            let state = if empty { air } else { facts.states[reader.get(y * 256 + z * 16 + x) as usize]? };
                            let biome = facts.biomes[cells[y / 4 * 16 + z / 4 * 4 + x / 4] as usize]?;
                            contributions[section][column] = triple(contributions[section][column], state, biome, base + y as i32);
                        }
                    }
                }
            }
            if !empty {
                for x in 0..16 {
                    for z in 0..16 {
                        let column = x * 16 + z;
                        let y = heights.blocking[column] - base;
                        if (0..16).contains(&y) {
                            surfaces[column] = facts.states[reader.get(y as usize * 256 + z * 16 + x) as usize]?;
                        }
                    }
                }
            }
            Some(())
        })?;
        biome_cells.push(cells);
    }
    let mut hash = 31i32;
    for column in 0..64 {
        for section in 0..sections.len() {
            hash = hash.wrapping_mul(powers[section]).wrapping_add(contributions[section][column]);
        }
    }
    let biome = |x: usize, y: i32, z: usize| -> Option<i32> {
        let y = (y - min).clamp(0, max - min - 1) as usize;
        facts.biomes[biome_cells[y / 16][(y & 15) / 4 * 16 + z / 4 * 4 + x / 4] as usize]
    };
    for x in 0..16 {
        for z in 0..16 {
            let c = x * 16 + z;
            let blocking = heights.blocking[c]; let solid = heights.solid[c];
            // Preserve the original surface branch: both block reads use blocking Y.
            hash = triple(hash, surfaces[c], biome(x, blocking, z)?, blocking);
            if solid != blocking { hash = triple(hash, surfaces[c], biome(x, solid, z)?, solid); }
        }
    }
    Some(hash)
}
#[repr(C)]
#[derive(Clone, Copy)]
pub(crate) struct Position { pub(crate) x: i32, pub(crate) y: i32, pub(crate) z: i32 }
/// Retained native emitters or an escaped mutable list supply CPU positions only.
/// Consecutive positions in one section share its existing mutation lock.
pub(crate) fn finish(sections: Sections<'_>, min: i32, facts: &Facts, mut hash: i32,
    positions: impl Iterator<Item=Position>) -> Option<i32> {
    let max = valid(sections, min)?;
    let air = *facts.states.first()?.as_ref()?;
    let mut positions = positions.peekable();
    while let Some(position) = positions.peek().copied() {
        // Frozen's development relative-position guard admits its exclusive top+1.
        // Anything else declines to the original callback/exception path.
        if !(0..16).contains(&position.x) || !(0..16).contains(&position.z) || position.y < min || position.y > max + 1 { return None; }
        if position.y >= max {
            positions.next(); hash = hash.wrapping_mul(227).wrapping_add(air).wrapping_mul(137).wrapping_add(position.y); continue;
        }
        let section = ((position.y - min) / 16) as usize;
        let empty = sections.counter(section)?.values()[0] == 0;
        sections.block(section).with_state_reader(|reader| {
            while let Some(p) = positions.peek().copied() {
                if !(0..16).contains(&p.x) || !(0..16).contains(&p.z) || p.y < min || p.y >= max || ((p.y - min) / 16) as usize != section { break; }
                positions.next();
                let index = ((p.y - min) as usize & 15) * 256 + p.z as usize * 16 + p.x as usize;
                let state = if empty { air } else { *facts.states.get(reader.get(index) as usize)?.as_ref()? };
                hash = hash.wrapping_mul(227).wrapping_add(state).wrapping_mul(137).wrapping_add(p.y);
            }
            Some(())
        })?;
    }
    Some(hash)
}

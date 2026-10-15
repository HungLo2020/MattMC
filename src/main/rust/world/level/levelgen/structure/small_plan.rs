//! The small structures' `generatePieces` (their `findGenerationPoint` is
//! `onTopOfChunkCenter`, which draws nothing): swamp hut, desert pyramid,
//! jungle temple (`ScatteredFeaturePiece`'s random direction), buried
//! treasure, igloo (`IglooPieces.addPieces`), shipwreck
//! (`ShipwreckPieces.addRandomPiece`) and ocean ruin
//! (`OceanRuinPieces.addPieces` with `addClusterRuins`), with the generation
//! context's random. Java rebuilds the pieces from the plan (each family's
//! `addNativePieces`).
//!
//! `params[0]` is the family (0 swamp hut, 1 desert pyramid, 2 jungle temple,
//! 3 buried treasure, 4 igloo, 5 shipwreck, 6 ocean ruin), the rest is what
//! the structure's `nativePlanParams` adds:
//! - igloo: the sizes (x, y, z) of `igloo/top`, `igloo/middle`, `igloo/bottom`;
//! - shipwreck: `[is beached, template count, sizes...]` over the beached or
//!   ocean template list;
//! - ocean ruin: `[cold, large probability bits, cluster probability bits,
//!   template count, sizes...]` over warm `[small 8, big 4]` or cold
//!   `[brick 8, cracked 8, mossy 8, big brick 4, big cracked 4, big mossy 4]`.
//!
//! Piece kinds and fields (genDepth 0 throughout):
//! - 1 `SwampHutPiece`, 3 `DesertPyramidPiece`, 4 `JungleTemplePiece` (the
//!   kinds of their `nativeWords`): `[width, height, depth, heightPosition]`;
//! - 2 `BuriedTreasurePiece`: none, orientation null;
//! - 60 `IglooPiece`, 61 `ShipwreckPiece`, 62 `OceanRuinPiece`: `[template
//!   index, rotation, mirror, template x, y, z]`, then the shipwreck's
//!   `isBeached` or the ocean ruin's `[integrity float bits, cold, isLarge]`.

use crate::world::level::levelgen::decoration::template::transform;
use crate::world::level::levelgen::random::Legacy;

/// `Direction.get2DDataValue()`.
const SOUTH: i32 = 0;
const WEST: i32 = 1;
const NORTH: i32 = 2;
const EAST: i32 = 3;

const SWAMP_HUT: i32 = 1;
const BURIED_TREASURE: i32 = 2;
const DESERT_PYRAMID: i32 = 3;
const JUNGLE_TEMPLE: i32 = 4;
const IGLOO: i32 = 60;
const SHIPWRECK: i32 = 61;
const OCEAN_RUIN: i32 = 62;

/// `IglooPieces.PIVOTS` and `OFFSETS` for top, middle (ladder), bottom (laboratory).
const IGLOO_PIVOTS: [[i32; 3]; 3] = [[3, 5, 5], [1, 3, 1], [3, 6, 7]];
const IGLOO_OFFSETS: [[i32; 3]; 3] = [[0, 0, 0], [2, -3, 4], [0, -3, -2]];
/// `ShipwreckPieces.PIVOT`.
const SHIPWRECK_PIVOT: [i32; 3] = [4, 0, 15];

fn push(out: &mut Vec<i32>, kind: i32, bb: [i32; 6], orientation: i32, fields: &[i32]) {
    out.push(kind);
    out.push(0);
    out.extend_from_slice(&bb);
    out.push(orientation);
    out.push(fields.len() as i32);
    out.extend_from_slice(fields);
}

fn from_corners(a: [i32; 3], b: [i32; 3]) -> [i32; 6] {
    [a[0].min(b[0]), a[1].min(b[1]), a[2].min(b[2]), a[0].max(b[0]), a[1].max(b[1]), a[2].max(b[2])]
}

fn intersects(a: &[i32; 6], b: &[i32; 6]) -> bool {
    a[3] >= b[0] && a[0] <= b[3] && a[5] >= b[2] && a[2] <= b[5] && a[4] >= b[1] && a[1] <= b[4]
}

fn offset(a: [i32; 3], b: [i32; 3]) -> [i32; 3] {
    [a[0].wrapping_add(b[0]), a[1].wrapping_add(b[1]), a[2].wrapping_add(b[2])]
}

/// `StructureTemplate.getBoundingBox(pos, rotation, pivot, Mirror.NONE, size)`.
fn template_box(pos: [i32; 3], rotation: usize, pivot: [i32; 3], size: [i32; 3]) -> [i32; 6] {
    let a = transform([0, 0, 0], 0, rotation, pivot);
    let b = transform([size[0] - 1, size[1] - 1, size[2] - 1], 0, rotation, pivot);
    let bb = from_corners(a, b);
    let (lo, hi) = (offset([bb[0], bb[1], bb[2]], pos), offset([bb[3], bb[4], bb[5]], pos));
    [lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]]
}

fn size(sizes: &[i32], index: usize) -> Result<[i32; 3], ()> {
    let s = sizes.get(index * 3..index * 3 + 3).ok_or(())?;
    Ok([s[0], s[1], s[2]])
}

/// `ScatteredFeaturePiece(type, x, 64, z, width, height, depth, getRandomHorizontalDirection(random))`.
fn scattered(out: &mut Vec<i32>, random: &mut Legacy, kind: i32, chunk: [i32; 2], w: i32, h: i32, d: i32) {
    // Plane.HORIZONTAL is north, east, south, west.
    let direction = [NORTH, EAST, SOUTH, WEST][random.next_int_bound(4) as usize];
    let (x, z) = (chunk[0].wrapping_mul(16), chunk[1].wrapping_mul(16));
    let (sx, sz) = if direction == NORTH || direction == SOUTH { (w, d) } else { (d, w) };
    push(out, kind, [x, 64, z, x + sx - 1, 64 + h - 1, z + sz - 1], direction, &[w, h, d, -1]);
}

/// `Mth.nextInt(random, min, max)`.
fn next_int_between(random: &mut Legacy, min: i32, max: i32) -> i32 {
    if min >= max { min } else { random.next_int_bound(max - min + 1) + min }
}

fn template_piece(out: &mut Vec<i32>, kind: i32, index: usize, rotation: usize, pos: [i32; 3], bb: [i32; 6], extra: &[i32]) {
    let mut fields = vec![index as i32, rotation as i32, 0, pos[0], pos[1], pos[2]];
    fields.extend_from_slice(extra);
    push(out, kind, bb, NORTH, &fields);
}

fn igloo(out: &mut Vec<i32>, random: &mut Legacy, chunk: [i32; 2], sizes: &[i32]) -> Result<(), ()> {
    let base = [chunk[0].wrapping_mul(16), 90, chunk[1].wrapping_mul(16)];
    let rotation = random.next_int_bound(4) as usize;
    let piece = |out: &mut Vec<i32>, index: usize, below: i32| -> Result<(), ()> {
        let pos = offset(offset(base, IGLOO_OFFSETS[index]), [0, -below, 0]);
        let bb = template_box(pos, rotation, IGLOO_PIVOTS[index], size(sizes, index)?);
        template_piece(out, IGLOO, index, rotation, pos, bb, &[]);
        Ok(())
    };
    if random.next_double() < 0.5 {
        let i = random.next_int_bound(8) + 4;
        piece(out, 2, i * 3)?;
        for j in 0..i - 1 {
            piece(out, 1, j * 3)?;
        }
    }
    piece(out, 0, 0)
}

fn shipwreck(out: &mut Vec<i32>, random: &mut Legacy, chunk: [i32; 2], params: &[i32]) -> Result<(), ()> {
    let beached = *params.first().ok_or(())?;
    let count = *params.get(1).ok_or(())?;
    let sizes = params.get(2..).ok_or(())?;
    if count <= 0 || sizes.len() != count as usize * 3 {
        return Err(());
    }
    let rotation = random.next_int_bound(4) as usize;
    let pos = [chunk[0].wrapping_mul(16), 90, chunk[1].wrapping_mul(16)];
    let index = random.next_int_bound(count) as usize;
    let s = size(sizes, index)?;
    // isTooBigToFitInWorldGenRegion moves it with heights (and a draw if beached): Java's path.
    if s[0] > 32 || s[1] > 32 {
        return Err(());
    }
    let bb = template_box(pos, rotation, SHIPWRECK_PIVOT, s);
    template_piece(out, SHIPWRECK, index, rotation, pos, bb, &[beached]);
    Ok(())
}

struct Ruins<'a> {
    cold: bool,
    sizes: &'a [i32],
}

impl Ruins<'_> {
    /// `OceanRuinPieces.addPiece`.
    fn add(&self, out: &mut Vec<i32>, random: &mut Legacy, pos: [i32; 3], rotation: usize, large: bool, integrity: f32) -> Result<(), ()> {
        let piece = |out: &mut Vec<i32>, index: usize, integrity: f32| -> Result<(), ()> {
            let bb = template_box(pos, rotation, [0, 0, 0], size(self.sizes, index)?);
            template_piece(out, OCEAN_RUIN, index, rotation, pos, bb, &[integrity.to_bits() as i32, i32::from(self.cold), i32::from(large)]);
            Ok(())
        };
        if self.cold {
            let (count, brick, cracked, mossy) = if large { (4, 24, 28, 32) } else { (8, 0, 8, 16) };
            let i = random.next_int_bound(count) as usize;
            piece(out, brick + i, integrity)?;
            piece(out, cracked + i, 0.7)?;
            piece(out, mossy + i, 0.5)
        } else if large {
            let i = random.next_int_bound(4) as usize;
            piece(out, 8 + i, integrity)
        } else {
            let i = random.next_int_bound(8) as usize;
            piece(out, i, integrity)
        }
    }
}

fn ocean_ruin(out: &mut Vec<i32>, random: &mut Legacy, chunk: [i32; 2], params: &[i32]) -> Result<(), ()> {
    let cold = match *params.first().ok_or(())? {
        0 => false,
        1 => true,
        _ => return Err(()),
    };
    let large_probability = f32::from_bits(*params.get(1).ok_or(())? as u32);
    let cluster_probability = f32::from_bits(*params.get(2).ok_or(())? as u32);
    let count = *params.get(3).ok_or(())?;
    let sizes = params.get(4..).ok_or(())?;
    if count != if cold { 36 } else { 12 } || sizes.len() != count as usize * 3 {
        return Err(());
    }
    let ruins = Ruins { cold, sizes };
    let pos = [chunk[0].wrapping_mul(16), 90, chunk[1].wrapping_mul(16)];
    let rotation = random.next_int_bound(4) as usize;
    let large = random.next_float() <= large_probability;
    ruins.add(out, random, pos, rotation, large, if large { 0.9 } else { 0.8 })?;
    if large && random.next_float() <= cluster_probability {
        // addClusterRuins.
        let corner = offset(transform([15, 0, 15], 0, rotation, [0, 0, 0]), pos);
        let bb = from_corners(pos, corner);
        let origin = [pos[0].min(corner[0]), pos[1], pos[2].min(corner[2])];
        // allPositions: [x range, z range] offsets, drawn x then z.
        let ranges: [(i32, i32, i32, i32, i32, i32); 8] = [
            (-16, 1, 8, 16, 1, 7),
            (-16, 1, 8, 0, 1, 7),
            (-16, 1, 8, -16, 4, 8),
            (0, 1, 7, 16, 1, 7),
            (0, 1, 7, -16, 4, 6),
            (16, 1, 7, 16, 3, 8),
            (16, 1, 7, 0, 1, 7),
            (16, 1, 7, -16, 4, 8),
        ];
        let mut list = Vec::with_capacity(8);
        for (bx, x0, x1, bz, z0, z1) in ranges {
            let dx = bx + next_int_between(random, x0, x1);
            let dz = bz + next_int_between(random, z0, z1);
            list.push(offset(origin, [dx, 0, dz]));
        }
        let i = next_int_between(random, 4, 8);
        for _ in 0..i {
            if !list.is_empty() {
                let k = random.next_int_bound(list.len() as i32) as usize;
                let at = list.remove(k);
                let rotation2 = random.next_int_bound(4) as usize;
                let far = offset(transform([5, 0, 6], 0, rotation2, [0, 0, 0]), at);
                if !intersects(&from_corners(at, far), &bb) {
                    ruins.add(out, random, at, rotation2, false, 0.8)?;
                }
            }
        }
    }
    Ok(())
}

/// The family's pieces as words (see the module docs).
pub(crate) fn plan(random: &mut Legacy, chunk: [i32; 2], _stub: [i32; 3], params: &[i32]) -> Result<Vec<i32>, ()> {
    let mut out = Vec::new();
    let rest = params.get(1..).ok_or(())?;
    match *params.first().ok_or(())? {
        0 => scattered(&mut out, random, SWAMP_HUT, chunk, 7, 7, 9),
        1 => scattered(&mut out, random, DESERT_PYRAMID, chunk, 21, 15, 21),
        2 => scattered(&mut out, random, JUNGLE_TEMPLE, chunk, 12, 10, 15),
        3 => {
            let (x, z) = (chunk[0].wrapping_mul(16) + 9, chunk[1].wrapping_mul(16) + 9);
            push(&mut out, BURIED_TREASURE, [x, 90, z, x, 90, z], -1, &[]);
        }
        4 => {
            if rest.len() != 9 {
                return Err(());
            }
            igloo(&mut out, random, chunk, rest)?;
        }
        5 => shipwreck(&mut out, random, chunk, rest)?,
        6 => ocean_ruin(&mut out, random, chunk, rest)?,
        _ => return Err(()),
    }
    Ok(out)
}

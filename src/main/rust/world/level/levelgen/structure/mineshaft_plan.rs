//! `MineshaftStructure.findGenerationPoint`: the room at the chunk, the
//! recursive corridors, crossings and stairs (`MineshaftPieces.addChildren`)
//! with the generation context's random, then the vertical move (below sea
//! level, or onto the mesa surface). Java builds the pieces from the plan.

use crate::world::level::levelgen::random::Legacy;

/// `Direction.get2DDataValue()`.
const SOUTH: i32 = 0;
const WEST: i32 = 1;
const NORTH: i32 = 2;
const EAST: i32 = 3;

pub(crate) const CORRIDOR: i32 = 0;
pub(crate) const CROSSING: i32 = 1;
pub(crate) const ROOM: i32 = 2;
pub(crate) const STAIRS: i32 = 3;

pub(crate) struct Piece {
    pub kind: i32,
    pub depth: i32,
    /// [min x, min y, min z, max x, max y, max z].
    pub bb: [i32; 6],
    pub direction: i32,
    /// Corridor: rails, spider corridor, sections; crossing: two floors.
    pub fields: [i32; 3],
    /// The room's child entrance boxes.
    pub entrances: Vec<[i32; 6]>,
}

fn intersects(a: &[i32; 6], b: &[i32; 6]) -> bool {
    a[3] >= b[0] && a[0] <= b[3] && a[5] >= b[2] && a[2] <= b[5] && a[4] >= b[1] && a[1] <= b[4]
}

fn moved(b: [i32; 6], x: i32, y: i32, z: i32) -> [i32; 6] {
    [b[0] + x, b[1] + y, b[2] + z, b[3] + x, b[4] + y, b[5] + z]
}

fn span(b: &[i32; 6], axis: usize) -> i32 {
    b[axis + 3] - b[axis] + 1
}

struct Planner<'a> {
    random: &'a mut Legacy,
    pieces: Vec<Piece>,
}

impl Planner<'_> {
    fn free(&self, b: &[i32; 6]) -> bool {
        !self.pieces.iter().any(|p| intersects(&p.bb, b))
    }

    fn next_boolean(&mut self) -> bool {
        self.random.next(1) != 0
    }

    /// `generateAndAddPiece` from the start room: the new piece's index.
    fn add(&mut self, x: i32, y: i32, z: i32, direction: i32, depth: i32) -> Option<usize> {
        let start = self.pieces[0].bb;
        if depth > 8 || (x - start[0]).abs() > 80 || (z - start[2]).abs() > 80 {
            return None;
        }
        let depth = depth + 1;
        let m = self.random.next_int_bound(100);
        let piece = if m >= 80 {
            let l = if self.random.next_int_bound(4) == 0 { 6 } else { 2 };
            let b = match direction {
                SOUTH => [-1, 0, 0, 3, l, 4],
                WEST => [-4, 0, -1, 0, l, 3],
                EAST => [0, 0, -1, 4, l, 3],
                _ => [-1, 0, -4, 3, l, 0],
            };
            let b = moved(b, x, y, z);
            self.free(&b).then(|| Piece { kind: CROSSING, depth, bb: b, direction, fields: [i32::from(span(&b, 1) > 3), 0, 0], entrances: Vec::new() })
        } else if m >= 70 {
            let b = match direction {
                SOUTH => [0, -5, 0, 2, 2, 8],
                WEST => [-8, -5, 0, 0, 2, 2],
                EAST => [0, -5, 0, 8, 2, 2],
                _ => [0, -5, -8, 2, 2, 0],
            };
            let b = moved(b, x, y, z);
            self.free(&b).then(|| Piece { kind: STAIRS, depth, bb: b, direction, fields: [0; 3], entrances: Vec::new() })
        } else {
            let mut found = None;
            let mut l = self.random.next_int_bound(3) + 2;
            while l > 0 {
                let m = l * 5;
                let b = match direction {
                    SOUTH => [0, 0, 0, 2, 2, m - 1],
                    WEST => [-(m - 1), 0, 0, 0, 2, 2],
                    EAST => [0, 0, 0, m - 1, 2, 2],
                    _ => [0, 0, -(m - 1), 2, 2, 0],
                };
                let b = moved(b, x, y, z);
                if self.free(&b) {
                    found = Some(b);
                    break;
                }
                l -= 1;
            }
            found.map(|b| {
                let rails = self.random.next_int_bound(3) == 0;
                let spider = !rails && self.random.next_int_bound(23) == 0;
                let sections = if direction == NORTH || direction == SOUTH { span(&b, 2) } else { span(&b, 0) } / 5;
                Piece { kind: CORRIDOR, depth, bb: b, direction, fields: [i32::from(rails), i32::from(spider), sections], entrances: Vec::new() }
            })
        }?;
        self.pieces.push(piece);
        let index = self.pieces.len() - 1;
        self.children(index);
        Some(index)
    }

    fn children(&mut self, index: usize) {
        let (kind, b, direction, i) = {
            let p = &self.pieces[index];
            (p.kind, p.bb, p.direction, p.depth)
        };
        let [min_x, min_y, min_z, max_x, _, max_z] = b;
        match kind {
            CORRIDOR => {
                let j = self.random.next_int_bound(4);
                let y = min_y - 1 + self.random.next_int_bound(3);
                let (x, z, d) = match direction {
                    SOUTH if j <= 1 => (min_x, max_z + 1, direction),
                    SOUTH if j == 2 => (min_x - 1, max_z - 3, WEST),
                    SOUTH => (max_x + 1, max_z - 3, EAST),
                    WEST if j <= 1 => (min_x - 1, min_z, direction),
                    WEST if j == 2 => (min_x, min_z - 1, NORTH),
                    WEST => (min_x, max_z + 1, SOUTH),
                    EAST if j <= 1 => (max_x + 1, min_z, direction),
                    EAST if j == 2 => (max_x - 3, min_z - 1, NORTH),
                    EAST => (max_x - 3, max_z + 1, SOUTH),
                    _ if j <= 1 => (min_x, min_z - 1, direction),
                    _ if j == 2 => (min_x - 1, min_z, WEST),
                    _ => (max_x + 1, min_z, EAST),
                };
                self.add(x, y, z, d, i);
                if i < 8 {
                    if direction != NORTH && direction != SOUTH {
                        let mut k = min_x + 3;
                        while k + 3 <= max_x {
                            match self.random.next_int_bound(5) {
                                0 => {
                                    self.add(k, min_y, min_z - 1, NORTH, i + 1);
                                }
                                1 => {
                                    self.add(k, min_y, max_z + 1, SOUTH, i + 1);
                                }
                                _ => {}
                            }
                            k += 5;
                        }
                    } else {
                        let mut k = min_z + 3;
                        while k + 3 <= max_z {
                            match self.random.next_int_bound(5) {
                                0 => {
                                    self.add(min_x - 1, min_y, k, WEST, i + 1);
                                }
                                1 => {
                                    self.add(max_x + 1, min_y, k, EAST, i + 1);
                                }
                                _ => {}
                            }
                            k += 5;
                        }
                    }
                }
            }
            CROSSING => {
                let sides = match direction {
                    SOUTH => [(min_x + 1, max_z + 1, SOUTH), (min_x - 1, min_z + 1, WEST), (max_x + 1, min_z + 1, EAST)],
                    WEST => [(min_x + 1, min_z - 1, NORTH), (min_x + 1, max_z + 1, SOUTH), (min_x - 1, min_z + 1, WEST)],
                    EAST => [(min_x + 1, min_z - 1, NORTH), (min_x + 1, max_z + 1, SOUTH), (max_x + 1, min_z + 1, EAST)],
                    _ => [(min_x + 1, min_z - 1, NORTH), (min_x - 1, min_z + 1, WEST), (max_x + 1, min_z + 1, EAST)],
                };
                for (x, z, d) in sides {
                    self.add(x, min_y, z, d, i);
                }
                if self.pieces[index].fields[0] != 0 {
                    for (x, z, d) in [(min_x + 1, min_z - 1, NORTH), (min_x - 1, min_z + 1, WEST), (max_x + 1, min_z + 1, EAST), (min_x + 1, max_z + 1, SOUTH)] {
                        if self.next_boolean() {
                            self.add(x, min_y + 3 + 1, z, d, i);
                        }
                    }
                }
            }
            STAIRS => {
                let (x, z) = match direction {
                    SOUTH => (min_x, max_z + 1),
                    WEST => (min_x - 1, min_z),
                    EAST => (max_x + 1, min_z),
                    _ => (min_x, min_z - 1),
                };
                self.add(x, min_y, z, direction, i);
            }
            _ => {
                let j = (span(&b, 1) - 3 - 1).max(1);
                // North, south, west, east walls: along x for the first two, z for the others.
                for side in 0..4 {
                    let length = if side < 2 { span(&b, 0) } else { span(&b, 2) };
                    let mut k = 0;
                    while k < length {
                        k += self.random.next_int_bound(length);
                        if k + 3 > length {
                            break;
                        }
                        let y = min_y + self.random.next_int_bound(j) + 1;
                        let (x, z, d) = match side {
                            0 => (min_x + k, min_z - 1, NORTH),
                            1 => (min_x + k, max_z + 1, SOUTH),
                            2 => (min_x - 1, min_z + k, WEST),
                            _ => (max_x + 1, min_z + k, EAST),
                        };
                        if let Some(child) = self.add(x, y, z, d, i) {
                            let c = self.pieces[child].bb;
                            let entrance = match side {
                                0 => [c[0], c[1], min_z, c[3], c[4], min_z + 1],
                                1 => [c[0], c[1], max_z - 1, c[3], c[4], max_z],
                                2 => [min_x, c[1], c[2], min_x + 1, c[4], c[5]],
                                _ => [max_x - 1, c[1], c[2], max_x, c[4], c[5]],
                            };
                            self.pieces[index].entrances.push(entrance);
                        }
                        k += 4;
                    }
                }
            }
        }
    }
}

/// The pieces (moved vertically) and the vertical offset. `surface` answers
/// `getBaseHeight(x, z, WORLD_SURFACE_WG)` for mesa mineshafts.
pub(crate) fn plan(chunk: [i32; 2], seed: i64, mesa: bool, sea_level: i32, generator_min_y: i32,
    surface: &mut dyn FnMut(i32, i32) -> Result<i32, ()>) -> Result<(Vec<Piece>, i32), ()> {
    let mut random = Legacy::new(0);
    random.set_large_feature_seed(seed, chunk[0], chunk[1]);
    random.next_double();
    let (x, z) = (chunk[0] * 16 + 2, chunk[1] * 16 + 2);
    let bb = [x, 50, z, x + 7 + random.next_int_bound(6), 54 + random.next_int_bound(6), z + 7 + random.next_int_bound(6)];
    let mut planner = Planner { random: &mut random, pieces: vec![Piece { kind: ROOM, depth: 0, bb, direction: -1, fields: [0; 3], entrances: Vec::new() }] };
    planner.children(0);
    let mut pieces = planner.pieces;
    let mut all = pieces[0].bb;
    for p in &pieces {
        for a in 0..3 {
            all[a] = all[a].min(p.bb[a]);
            all[a + 3] = all[a + 3].max(p.bb[a + 3]);
        }
    }
    let offset = if mesa {
        let centre = [all[0] + span(&all, 0) / 2, all[1] + span(&all, 1) / 2, all[2] + span(&all, 2) / 2];
        let j = surface(centre[0], centre[2])?;
        let k = if j <= sea_level { sea_level } else { random.next_int_bound(j - sea_level + 1) + sea_level };
        k - centre[1]
    } else {
        // moveBelowSeaLevel(seaLevel, minY, random, 10).
        let l = sea_level - 10;
        let mut m = span(&all, 1) + generator_min_y + 1;
        if m < l {
            m += random.next_int_bound(l - m);
        }
        m - all[4]
    };
    for p in &mut pieces {
        p.bb = moved(p.bb, 0, offset, 0);
        for e in &mut p.entrances {
            *e = moved(*e, 0, offset, 0);
        }
    }
    Ok((pieces, offset))
}

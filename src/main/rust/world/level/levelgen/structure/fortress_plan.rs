//! `NetherFortressStructure.generatePieces`: the start crossing at the chunk,
//! the weighted bridge and castle pieces grown from the pending-children list
//! (`NetherFortressPieces`' `addChildren`, `generateChild*`, `generatePiece`)
//! with the generation context's random, then `moveInsideHeights(random, 48,
//! 70)`. Java rebuilds the pieces from the plan
//! (`NetherFortressPieces.addNativePieces`).
//!
//! Piece kinds (the same numbers as the pieces' `nativeWords`, plus the start):
//! 30 `BridgeCrossing`, 31 `BridgeEndFiller`, 32 `BridgeStraight`,
//! 33 `CastleCorridorStairsPiece`, 34 `CastleCorridorTBalconyPiece`,
//! 35 `CastleEntrance`, 36 `CastleSmallCorridorCrossingPiece`,
//! 37 `CastleSmallCorridorLeftTurnPiece`, 38 `CastleSmallCorridorPiece`,
//! 39 `CastleSmallCorridorRightTurnPiece`, 40 `CastleStalkRoom`,
//! 41 `MonsterThrone`, 42 `RoomCrossing`, 43 `StairsRoom`, 44 `StartPiece`.
//!
//! Fields: 31 `[selfSeed]`, 37 and 39 `[isNeedingChest as 0/1]`, every other
//! kind none (`MonsterThrone.hasPlacedSpawner` starts false).

use crate::world::level::levelgen::random::Legacy;

/// `Direction.get2DDataValue()`.
const SOUTH: i32 = 0;
const WEST: i32 = 1;
const NORTH: i32 = 2;
const EAST: i32 = 3;

const BRIDGE_CROSSING: i32 = 30;
const BRIDGE_END_FILLER: i32 = 31;
const BRIDGE_STRAIGHT: i32 = 32;
const CORRIDOR_STAIRS: i32 = 33;
const CORRIDOR_T_BALCONY: i32 = 34;
const CASTLE_ENTRANCE: i32 = 35;
const SMALL_CORRIDOR_CROSSING: i32 = 36;
const SMALL_CORRIDOR_LEFT_TURN: i32 = 37;
const SMALL_CORRIDOR: i32 = 38;
const SMALL_CORRIDOR_RIGHT_TURN: i32 = 39;
const STALK_ROOM: i32 = 40;
const MONSTER_THRONE: i32 = 41;
const ROOM_CROSSING: i32 = 42;
const STAIRS_ROOM: i32 = 43;
const START: i32 = 44;

/// `PieceWeight`: kind, weight, max place count, allow in row.
const BRIDGE_PIECE_WEIGHTS: [(i32, i32, i32, bool); 6] = [
    (BRIDGE_STRAIGHT, 30, 0, true),
    (BRIDGE_CROSSING, 10, 4, false),
    (ROOM_CROSSING, 10, 4, false),
    (STAIRS_ROOM, 10, 3, false),
    (MONSTER_THRONE, 5, 2, false),
    (CASTLE_ENTRANCE, 5, 1, false),
];
const CASTLE_PIECE_WEIGHTS: [(i32, i32, i32, bool); 7] = [
    (SMALL_CORRIDOR, 25, 0, true),
    (SMALL_CORRIDOR_CROSSING, 15, 5, false),
    (SMALL_CORRIDOR_RIGHT_TURN, 5, 10, false),
    (SMALL_CORRIDOR_LEFT_TURN, 5, 10, false),
    (CORRIDOR_STAIRS, 10, 3, true),
    (CORRIDOR_T_BALCONY, 7, 2, false),
    (STALK_ROOM, 5, 2, false),
];

struct Weight {
    kind: i32,
    weight: i32,
    place_count: i32,
    max_place_count: i32,
    allow_in_row: bool,
}

impl Weight {
    /// `doPlace` and `isValid` (identical).
    fn can_place(&self) -> bool {
        self.max_place_count == 0 || self.place_count < self.max_place_count
    }
}

fn weights(table: &[(i32, i32, i32, bool)]) -> Vec<Weight> {
    table.iter().map(|&(kind, weight, max_place_count, allow_in_row)| Weight { kind, weight, place_count: 0, max_place_count, allow_in_row }).collect()
}

struct Piece {
    kind: i32,
    depth: i32,
    /// [min x, min y, min z, max x, max y, max z].
    bb: [i32; 6],
    direction: i32,
    /// `selfSeed` or `isNeedingChest`; unused by the other kinds.
    field: i32,
}

fn intersects(a: &[i32; 6], b: &[i32; 6]) -> bool {
    a[3] >= b[0] && a[0] <= b[3] && a[5] >= b[2] && a[2] <= b[5] && a[4] >= b[1] && a[1] <= b[4]
}

/// `BoundingBox.orientBox`.
#[allow(clippy::too_many_arguments)]
fn orient_box(x: i32, y: i32, z: i32, l: i32, m: i32, n: i32, o: i32, p: i32, q: i32, direction: i32) -> [i32; 6] {
    match direction {
        NORTH => [x + l, y + m, z - q + 1 + n, x + o - 1 + l, y + p - 1 + m, z + n],
        WEST => [x - q + 1 + n, y + m, z + l, x + n, y + p - 1 + m, z + o - 1 + l],
        EAST => [x + n, y + m, z + l, x + q - 1 + n, y + p - 1 + m, z + o - 1 + l],
        _ => [x + l, y + m, z + n, x + o - 1 + l, y + p - 1 + m, z + q - 1 + n],
    }
}

/// Each kind's `createPiece` box: offset (x, y, z) and size (width, height, depth).
fn shape(kind: i32) -> Option<[i32; 6]> {
    Some(match kind {
        BRIDGE_CROSSING => [-8, -3, 0, 19, 10, 19],
        BRIDGE_END_FILLER => [-1, -3, 0, 5, 10, 8],
        BRIDGE_STRAIGHT => [-1, -3, 0, 5, 10, 19],
        CORRIDOR_STAIRS => [-1, -7, 0, 5, 14, 10],
        CORRIDOR_T_BALCONY => [-3, 0, 0, 9, 7, 9],
        CASTLE_ENTRANCE | STALK_ROOM => [-5, -3, 0, 13, 14, 13],
        SMALL_CORRIDOR_CROSSING | SMALL_CORRIDOR_LEFT_TURN | SMALL_CORRIDOR | SMALL_CORRIDOR_RIGHT_TURN => [-1, 0, 0, 5, 7, 5],
        MONSTER_THRONE => [-2, 0, 0, 7, 8, 9],
        ROOM_CROSSING => [-2, 0, 0, 7, 9, 7],
        STAIRS_ROOM => [-2, 0, 0, 7, 11, 7],
        _ => return None,
    })
}

struct Planner<'a> {
    random: &'a mut Legacy,
    pieces: Vec<Piece>,
    bridge: Vec<Weight>,
    castle: Vec<Weight>,
    /// `startPiece.previousPiece`, by kind (each weight's kind is unique).
    previous: Option<i32>,
    pending: Vec<usize>,
}

impl Planner<'_> {
    /// `<Kind>.createPiece`: the piece when its box is above y 10 and free.
    /// Not added to the builder.
    fn create(&mut self, kind: i32, x: i32, y: i32, z: i32, direction: i32, depth: i32) -> Result<Option<Piece>, ()> {
        let [l, m, n, o, p, q] = shape(kind).ok_or(())?;
        let bb = orient_box(x, y, z, l, m, n, o, p, q, direction);
        if bb[1] <= 10 || self.pieces.iter().any(|piece| intersects(&piece.bb, &bb)) {
            return Ok(None);
        }
        let field = match kind {
            BRIDGE_END_FILLER => self.random.next(32),
            SMALL_CORRIDOR_LEFT_TURN | SMALL_CORRIDOR_RIGHT_TURN => i32::from(self.random.next_int_bound(3) == 0),
            _ => 0,
        };
        Ok(Some(Piece { kind, depth, bb, direction, field }))
    }

    /// `NetherBridgePiece.generatePiece`.
    fn generate(&mut self, castle: bool, x: i32, y: i32, z: i32, direction: i32, depth: i32) -> Result<Option<Piece>, ()> {
        let list = if castle { &self.castle } else { &self.bridge };
        let any = list.iter().any(|w| w.max_place_count > 0 && w.place_count < w.max_place_count);
        let total = if any { list.iter().map(|w| w.weight).sum() } else { -1 };
        let allowed = total > 0 && depth <= 30;
        let mut tries = 0;
        while tries < 5 && allowed {
            tries += 1;
            let mut o = self.random.next_int_bound(total);
            let mut index = 0;
            loop {
                let list = if castle { &self.castle } else { &self.bridge };
                let Some(w) = list.get(index) else { break };
                let (kind, weight, can_place, allow_in_row) = (w.kind, w.weight, w.can_place(), w.allow_in_row);
                o -= weight;
                if o < 0 {
                    if !can_place || (self.previous == Some(kind) && !allow_in_row) {
                        break;
                    }
                    if let Some(piece) = self.create(kind, x, y, z, direction, depth)? {
                        let list = if castle { &mut self.castle } else { &mut self.bridge };
                        list[index].place_count += 1;
                        self.previous = Some(kind);
                        if !list[index].can_place() {
                            list.remove(index);
                        }
                        return Ok(Some(piece));
                    }
                }
                index += 1;
            }
        }
        self.create(BRIDGE_END_FILLER, x, y, z, direction, depth)
    }

    /// `NetherBridgePiece.generateAndAddPiece`.
    fn add(&mut self, x: i32, y: i32, z: i32, direction: i32, depth: i32, castle: bool) -> Result<(), ()> {
        let start = self.pieces[0].bb;
        if (x - start[0]).abs() <= 112 && (z - start[2]).abs() <= 112 {
            if let Some(piece) = self.generate(castle, x, y, z, direction, depth + 1)? {
                self.pieces.push(piece);
                self.pending.push(self.pieces.len() - 1);
            }
        } else {
            // Created (drawing its seed when it fits) but never added.
            self.create(BRIDGE_END_FILLER, x, y, z, direction, depth)?;
        }
        Ok(())
    }

    fn forward(&mut self, index: usize, i: i32, j: i32, castle: bool) -> Result<(), ()> {
        let p = &self.pieces[index];
        let ([min_x, min_y, min_z, max_x, _, max_z], d, depth) = (p.bb, p.direction, p.depth);
        let (x, z) = match d {
            NORTH => (min_x + i, min_z - 1),
            SOUTH => (min_x + i, max_z + 1),
            WEST => (min_x - 1, min_z + i),
            EAST => (max_x + 1, min_z + i),
            _ => return Err(()),
        };
        self.add(x, min_y + j, z, d, depth, castle)
    }

    fn left(&mut self, index: usize, i: i32, j: i32, castle: bool) -> Result<(), ()> {
        let p = &self.pieces[index];
        let ([min_x, min_y, min_z, ..], d, depth) = (p.bb, p.direction, p.depth);
        let (x, z, d) = match d {
            NORTH | SOUTH => (min_x - 1, min_z + j, WEST),
            WEST | EAST => (min_x + j, min_z - 1, NORTH),
            _ => return Err(()),
        };
        self.add(x, min_y + i, z, d, depth, castle)
    }

    fn right(&mut self, index: usize, i: i32, j: i32, castle: bool) -> Result<(), ()> {
        let p = &self.pieces[index];
        let ([min_x, min_y, min_z, max_x, _, max_z], d, depth) = (p.bb, p.direction, p.depth);
        let (x, z, d) = match d {
            NORTH | SOUTH => (max_x + 1, min_z + j, EAST),
            WEST | EAST => (min_x + j, max_z + 1, SOUTH),
            _ => return Err(()),
        };
        self.add(x, min_y + i, z, d, depth, castle)
    }

    /// Each kind's `addChildren`.
    fn children(&mut self, index: usize) -> Result<(), ()> {
        match self.pieces[index].kind {
            START | BRIDGE_CROSSING => {
                self.forward(index, 8, 3, false)?;
                self.left(index, 3, 8, false)?;
                self.right(index, 3, 8, false)
            }
            BRIDGE_STRAIGHT => self.forward(index, 1, 3, false),
            CORRIDOR_STAIRS | SMALL_CORRIDOR => self.forward(index, 1, 0, true),
            CORRIDOR_T_BALCONY => {
                let d = self.pieces[index].direction;
                let i = if d == WEST || d == NORTH { 5 } else { 1 };
                // The flag argument draws before the call runs.
                let left = self.random.next_int_bound(8) > 0;
                self.left(index, 0, i, left)?;
                let right = self.random.next_int_bound(8) > 0;
                self.right(index, 0, i, right)
            }
            CASTLE_ENTRANCE => self.forward(index, 5, 3, true),
            SMALL_CORRIDOR_CROSSING => {
                self.forward(index, 1, 0, true)?;
                self.left(index, 0, 1, true)?;
                self.right(index, 0, 1, true)
            }
            SMALL_CORRIDOR_LEFT_TURN => self.left(index, 0, 1, true),
            SMALL_CORRIDOR_RIGHT_TURN => self.right(index, 0, 1, true),
            STALK_ROOM => {
                self.forward(index, 5, 3, true)?;
                self.forward(index, 5, 11, true)
            }
            ROOM_CROSSING => {
                self.forward(index, 2, 0, false)?;
                self.left(index, 0, 2, false)?;
                self.right(index, 0, 2, false)
            }
            STAIRS_ROOM => self.right(index, 6, 2, false),
            BRIDGE_END_FILLER | MONSTER_THRONE => Ok(()),
            _ => Err(()),
        }
    }
}

/// `NetherFortressStructure.generatePieces` with the seeded context random:
/// the start crossing at (chunk min x + 2, 64, chunk min z + 2). The pieces in
/// builder order, moved vertically, as `[kind, gen depth, min x, min y, min z,
/// max x, max y, max z, orientation, field count, fields...]` (see the module
/// docs for kinds and fields).
pub(crate) fn plan(random: &mut Legacy, chunk: [i32; 2]) -> Result<Vec<i32>, ()> {
    // getRandomHorizontalDirection: Plane.HORIZONTAL is north, east, south, west.
    let direction = [NORTH, EAST, SOUTH, WEST][random.next_int_bound(4) as usize];
    let (x, z) = (chunk[0].wrapping_mul(16) + 2, chunk[1].wrapping_mul(16) + 2);
    // makeBoundingBox(x, 64, z, direction, 19, 10, 19): square, so either axis gives this.
    let bb = [x, 64, z, x + 18, 73, z + 18];
    let mut planner = Planner {
        random,
        pieces: vec![Piece { kind: START, depth: 0, bb, direction, field: 0 }],
        bridge: weights(&BRIDGE_PIECE_WEIGHTS),
        castle: weights(&CASTLE_PIECE_WEIGHTS),
        previous: None,
        pending: Vec::new(),
    };
    planner.children(0)?;
    while !planner.pending.is_empty() {
        let i = planner.random.next_int_bound(planner.pending.len() as i32) as usize;
        let index = planner.pending.remove(i);
        planner.children(index)?;
    }
    let Planner { random, mut pieces, .. } = planner;

    // moveInsideHeights(random, 48, 70).
    let (min_y, max_y) = pieces.iter().fold((i32::MAX, i32::MIN), |(lo, hi), p| (lo.min(p.bb[1]), hi.max(p.bb[4])));
    let k = 70 - 48 + 1 - (max_y - min_y + 1);
    let target = if k > 1 { 48 + random.next_int_bound(k) } else { 48 };
    let offset = target - min_y;

    let mut words = Vec::with_capacity(pieces.len() * 11);
    for p in &mut pieces {
        p.bb[1] += offset;
        p.bb[4] += offset;
        words.extend_from_slice(&[p.kind, p.depth]);
        words.extend_from_slice(&p.bb);
        words.push(p.direction);
        match p.kind {
            BRIDGE_END_FILLER | SMALL_CORRIDOR_LEFT_TURN | SMALL_CORRIDOR_RIGHT_TURN => words.extend_from_slice(&[1, p.field]),
            _ => words.push(0),
        }
    }
    Ok(words)
}

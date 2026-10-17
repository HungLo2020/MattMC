//! `StrongholdStructure.generatePieces`: the start stairs at the chunk, the
//! weighted piece picks from each small door (`StrongholdPieces`), the
//! pending-children loop, then the move below sea level, retried with the next
//! seed until a portal room exists. Java's static planner state
//! (`currentPieces`, `imposedPiece`, `totalWeight`) lives on the planner.
//! Java rebuilds the pieces from the words (`StrongholdPieces.addNativePieces`).
//!
//! Piece kinds (the `NativeStructurePieces` stronghold kinds, plus the start):
//! 10 `ChestCorridor`, 11 `FillerCorridor`, 12 `FiveCrossing`, 13 `LeftTurn`,
//! 14 `Library`, 15 `PortalRoom`, 16 `PrisonHall`, 17 `RightTurn`,
//! 18 `RoomCrossing`, 19 `StairsDown`, 20 `Straight`, 21 `StraightStairsDown`,
//! 22 `StartPiece`.
//!
//! Fields (the first is always the entry door's `SmallDoorType` ordinal):
//! 10 `[door, hasPlacedChest]`, 11 `[door, steps]`,
//! 12 `[door, leftLow, leftHigh, rightLow, rightHigh]`, 13 `[door]`,
//! 14 `[door, isTall]`, 15 `[door, hasPlacedSpawner]`, 16 `[door]`, 17 `[door]`,
//! 18 `[door, type]`, 19 `[door, isSource]`, 20 `[door, leftChild, rightChild]`,
//! 21 `[door]`, 22 `[door, isSource]`.

use crate::world::level::levelgen::random::Legacy;

/// `Direction.get2DDataValue()`.
const SOUTH: i32 = 0;
const WEST: i32 = 1;
const NORTH: i32 = 2;
const EAST: i32 = 3;

pub(crate) const CHEST_CORRIDOR: i32 = 10;
pub(crate) const FILLER_CORRIDOR: i32 = 11;
pub(crate) const FIVE_CROSSING: i32 = 12;
pub(crate) const LEFT_TURN: i32 = 13;
pub(crate) const LIBRARY: i32 = 14;
pub(crate) const PORTAL_ROOM: i32 = 15;
pub(crate) const PRISON_HALL: i32 = 16;
pub(crate) const RIGHT_TURN: i32 = 17;
pub(crate) const ROOM_CROSSING: i32 = 18;
pub(crate) const STAIRS_DOWN: i32 = 19;
pub(crate) const STRAIGHT: i32 = 20;
pub(crate) const STRAIGHT_STAIRS_DOWN: i32 = 21;
pub(crate) const START: i32 = 22;

/// Java never gives up; this bounds a pathological seed (Java's path then runs).
const MAX_ATTEMPTS: i64 = 10_000;

/// `STRONGHOLD_PIECE_WEIGHTS`: kind, weight, max place count.
const WEIGHTS: [(i32, i32, i32); 11] = [
    (STRAIGHT, 40, 0),
    (PRISON_HALL, 5, 5),
    (LEFT_TURN, 20, 0),
    (RIGHT_TURN, 20, 0),
    (ROOM_CROSSING, 10, 6),
    (STRAIGHT_STAIRS_DOWN, 5, 5),
    (STAIRS_DOWN, 5, 5),
    (FIVE_CROSSING, 5, 4),
    (CHEST_CORRIDOR, 5, 4),
    (LIBRARY, 10, 2),
    (PORTAL_ROOM, 20, 1),
];

struct Piece {
    kind: i32,
    depth: i32,
    /// [min x, min y, min z, max x, max y, max z].
    bb: [i32; 6],
    direction: i32,
    /// The entry door, then the kind's fields (see the module doc).
    fields: Vec<i32>,
}

fn intersects(a: &[i32; 6], b: &[i32; 6]) -> bool {
    a[3] >= b[0] && a[0] <= b[3] && a[5] >= b[2] && a[2] <= b[5] && a[4] >= b[1] && a[1] <= b[4]
}

/// `BoundingBox.orientBox`.
#[allow(clippy::too_many_arguments)]
fn orient(i: i32, j: i32, k: i32, l: i32, m: i32, n: i32, o: i32, p: i32, q: i32, direction: i32) -> [i32; 6] {
    match direction {
        NORTH => [i + l, j + m, k - q + 1 + n, i + o - 1 + l, j + p - 1 + m, k + n],
        WEST => [i - q + 1 + n, j + m, k + l, i + n, j + p - 1 + m, k + o - 1 + l],
        EAST => [i + n, j + m, k + l, i + q - 1 + n, j + p - 1 + m, k + o - 1 + l],
        _ => [i + l, j + m, k + n, i + o - 1 + l, j + p - 1 + m, k + q - 1 + n],
    }
}

/// `StrongholdPiece.isOkBox`.
fn ok_box(b: &[i32; 6]) -> bool {
    b[1] > 10
}

struct Planner<'a> {
    random: &'a mut Legacy,
    /// The builder's pieces, in order.
    pieces: Vec<Piece>,
    /// `StartPiece.pendingChildren`: piece indices.
    pending: Vec<usize>,
    /// `PieceWeight.placeCount` per `WEIGHTS` entry.
    counts: [i32; 11],
    /// `currentPieces`: `WEIGHTS` indices.
    current: Vec<usize>,
    imposed: Option<i32>,
    total_weight: i32,
    /// `StartPiece.previousPiece`: a `WEIGHTS` index.
    previous: Option<usize>,
    portal_room: bool,
}

impl Planner<'_> {
    fn next_boolean(&mut self) -> bool {
        self.random.next(1) != 0
    }

    fn collision(&self, b: &[i32; 6]) -> Option<usize> {
        self.pieces.iter().position(|p| intersects(&p.bb, b))
    }

    /// `randomSmallDoor`: OPENING (0, 1), WOOD_DOOR (2), GRATES (3), IRON_DOOR (4).
    fn small_door(&mut self) -> i32 {
        match self.random.next_int_bound(5) {
            2 => 1,
            3 => 2,
            4 => 3,
            _ => 0,
        }
    }

    /// `PieceWeight.doPlace` (with the library and portal room overrides).
    fn do_place(&self, w: usize, depth: i32) -> bool {
        let (kind, _, max) = WEIGHTS[w];
        let base = max == 0 || self.counts[w] < max;
        match kind {
            LIBRARY => base && depth > 4,
            PORTAL_ROOM => base && depth > 5,
            _ => base,
        }
    }

    fn is_valid(&self, w: usize) -> bool {
        let (_, _, max) = WEIGHTS[w];
        max == 0 || self.counts[w] < max
    }

    fn update_piece_weight(&mut self) -> bool {
        let mut any = false;
        self.total_weight = 0;
        for &w in &self.current {
            let (_, weight, max) = WEIGHTS[w];
            if max > 0 && self.counts[w] < max {
                any = true;
            }
            self.total_weight += weight;
        }
        any
    }

    /// `findAndCreatePieceFactory`: each class's `createPiece`.
    fn create(&mut self, kind: i32, x: i32, y: i32, z: i32, d: i32, depth: i32) -> Option<Piece> {
        let simple = |planner: &Self, b: [i32; 6]| ok_box(&b) && planner.collision(&b).is_none();
        let (bb, fields) = match kind {
            STRAIGHT => {
                let b = orient(x, y, z, -1, -1, 0, 5, 5, 7, d);
                if !simple(self, b) {
                    return None;
                }
                let door = self.small_door();
                let left = self.random.next_int_bound(2) == 0;
                let right = self.random.next_int_bound(2) == 0;
                (b, vec![door, i32::from(left), i32::from(right)])
            }
            PRISON_HALL | LEFT_TURN | RIGHT_TURN | STRAIGHT_STAIRS_DOWN | STAIRS_DOWN | CHEST_CORRIDOR => {
                let b = match kind {
                    PRISON_HALL => orient(x, y, z, -1, -1, 0, 9, 5, 11, d),
                    LEFT_TURN | RIGHT_TURN => orient(x, y, z, -1, -1, 0, 5, 5, 5, d),
                    STRAIGHT_STAIRS_DOWN => orient(x, y, z, -1, -7, 0, 5, 11, 8, d),
                    STAIRS_DOWN => orient(x, y, z, -1, -7, 0, 5, 11, 5, d),
                    _ => orient(x, y, z, -1, -1, 0, 5, 5, 7, d),
                };
                if !simple(self, b) {
                    return None;
                }
                let door = self.small_door();
                // StairsDown: isSource false; ChestCorridor: hasPlacedChest false.
                let fields = if kind == STAIRS_DOWN || kind == CHEST_CORRIDOR { vec![door, 0] } else { vec![door] };
                (b, fields)
            }
            ROOM_CROSSING => {
                let b = orient(x, y, z, -4, -1, 0, 11, 7, 11, d);
                if !simple(self, b) {
                    return None;
                }
                let door = self.small_door();
                let room_type = self.random.next_int_bound(5);
                (b, vec![door, room_type])
            }
            FIVE_CROSSING => {
                let b = orient(x, y, z, -4, -3, 0, 10, 9, 11, d);
                if !simple(self, b) {
                    return None;
                }
                let door = self.small_door();
                let left_low = self.next_boolean();
                let left_high = self.next_boolean();
                let right_low = self.next_boolean();
                let right_high = self.random.next_int_bound(3) > 0;
                (b, vec![door, i32::from(left_low), i32::from(left_high), i32::from(right_low), i32::from(right_high)])
            }
            LIBRARY => {
                let mut b = orient(x, y, z, -4, -1, 0, 14, 11, 15, d);
                if !simple(self, b) {
                    b = orient(x, y, z, -4, -1, 0, 14, 6, 15, d);
                    if !simple(self, b) {
                        return None;
                    }
                }
                let door = self.small_door();
                let tall = b[4] - b[1] + 1 > 6;
                (b, vec![door, i32::from(tall)])
            }
            PORTAL_ROOM => {
                let b = orient(x, y, z, -4, -1, 0, 11, 8, 16, d);
                if !simple(self, b) {
                    return None;
                }
                (b, vec![0, 0])
            }
            _ => return None,
        };
        Some(Piece { kind, depth, bb, direction: d, fields })
    }

    /// `FillerCorridor.findPieceBox`.
    fn filler_box(&self, x: i32, y: i32, z: i32, d: i32) -> Option<[i32; 6]> {
        let b = orient(x, y, z, -1, -1, 0, 5, 5, 4, d);
        let hit = self.pieces[self.collision(&b)?].bb;
        if hit[1] == b[1] {
            for m in (1..=2).rev() {
                let b = orient(x, y, z, -1, -1, 0, 5, 5, m, d);
                if !intersects(&hit, &b) {
                    return Some(orient(x, y, z, -1, -1, 0, 5, 5, m + 1, d));
                }
            }
        }
        None
    }

    /// `generatePieceFromSmallDoor`.
    fn from_small_door(&mut self, x: i32, y: i32, z: i32, d: i32, depth: i32) -> Option<Piece> {
        if !self.update_piece_weight() {
            return None;
        }
        if let Some(kind) = self.imposed {
            let piece = self.create(kind, x, y, z, d, depth);
            self.imposed = None;
            if piece.is_some() {
                return piece;
            }
        }
        for _ in 0..5 {
            let mut n = self.random.next_int_bound(self.total_weight);
            let mut pos = 0;
            while pos < self.current.len() {
                let w = self.current[pos];
                n -= WEIGHTS[w].1;
                if n < 0 {
                    if !self.do_place(w, depth) || self.previous == Some(w) {
                        break;
                    }
                    if let Some(piece) = self.create(WEIGHTS[w].0, x, y, z, d, depth) {
                        self.counts[w] += 1;
                        self.previous = Some(w);
                        if !self.is_valid(w) {
                            self.current.remove(pos);
                        }
                        return Some(piece);
                    }
                }
                pos += 1;
            }
        }
        let b = self.filler_box(x, y, z, d)?;
        if b[1] <= 1 {
            return None;
        }
        let steps = if d != NORTH && d != SOUTH { b[3] - b[0] + 1 } else { b[5] - b[2] + 1 };
        Some(Piece { kind: FILLER_CORRIDOR, depth, bb: b, direction: d, fields: vec![0, steps] })
    }

    /// `generateAndAddPiece`.
    fn add(&mut self, x: i32, y: i32, z: i32, d: i32, depth: i32) {
        if depth > 50 {
            return;
        }
        let start = self.pieces[0].bb;
        if (x - start[0]).abs() <= 112 && (z - start[2]).abs() <= 112 {
            if let Some(piece) = self.from_small_door(x, y, z, d, depth + 1) {
                self.pieces.push(piece);
                self.pending.push(self.pieces.len() - 1);
            }
        }
    }

    fn forward(&mut self, index: usize, i: i32, j: i32) {
        let p = &self.pieces[index];
        let (b, d, depth) = (p.bb, p.direction, p.depth);
        match d {
            NORTH => self.add(b[0] + i, b[1] + j, b[2] - 1, d, depth),
            SOUTH => self.add(b[0] + i, b[1] + j, b[5] + 1, d, depth),
            WEST => self.add(b[0] - 1, b[1] + j, b[2] + i, d, depth),
            _ => self.add(b[3] + 1, b[1] + j, b[2] + i, d, depth),
        }
    }

    fn left(&mut self, index: usize, i: i32, j: i32) {
        let p = &self.pieces[index];
        let (b, d, depth) = (p.bb, p.direction, p.depth);
        if d == NORTH || d == SOUTH {
            self.add(b[0] - 1, b[1] + i, b[2] + j, WEST, depth);
        } else {
            self.add(b[0] + j, b[1] + i, b[2] - 1, NORTH, depth);
        }
    }

    fn right(&mut self, index: usize, i: i32, j: i32) {
        let p = &self.pieces[index];
        let (b, d, depth) = (p.bb, p.direction, p.depth);
        if d == NORTH || d == SOUTH {
            self.add(b[3] + 1, b[1] + i, b[2] + j, EAST, depth);
        } else {
            self.add(b[0] + j, b[1] + i, b[5] + 1, SOUTH, depth);
        }
    }

    /// Each class's `addChildren`.
    fn children(&mut self, index: usize) {
        let kind = self.pieces[index].kind;
        let d = self.pieces[index].direction;
        let field = |planner: &Self, n: usize| planner.pieces[index].fields[n] != 0;
        match kind {
            STRAIGHT => {
                self.forward(index, 1, 1);
                if field(self, 1) {
                    self.left(index, 1, 2);
                }
                if field(self, 2) {
                    self.right(index, 1, 2);
                }
            }
            PRISON_HALL | CHEST_CORRIDOR | STRAIGHT_STAIRS_DOWN => self.forward(index, 1, 1),
            LEFT_TURN => {
                if d != NORTH && d != EAST {
                    self.right(index, 1, 1);
                } else {
                    self.left(index, 1, 1);
                }
            }
            RIGHT_TURN => {
                if d != NORTH && d != EAST {
                    self.left(index, 1, 1);
                } else {
                    self.right(index, 1, 1);
                }
            }
            ROOM_CROSSING => {
                self.forward(index, 4, 1);
                self.left(index, 1, 4);
                self.right(index, 1, 4);
            }
            STAIRS_DOWN | START => {
                if field(self, 1) {
                    self.imposed = Some(FIVE_CROSSING);
                }
                self.forward(index, 1, 1);
            }
            FIVE_CROSSING => {
                let (i, j) = if d == WEST || d == NORTH { (5, 3) } else { (3, 5) };
                self.forward(index, 5, 1);
                if field(self, 1) {
                    self.left(index, i, 1);
                }
                if field(self, 2) {
                    self.left(index, j, 7);
                }
                if field(self, 3) {
                    self.right(index, i, 1);
                }
                if field(self, 4) {
                    self.right(index, j, 7);
                }
            }
            PORTAL_ROOM => self.portal_room = true,
            _ => {}
        }
    }
}

/// StrongholdStructure.generatePieces with the context random (already seeded for the first attempt; re-seed it per retry as Java does).
pub(crate) fn plan(random: &mut Legacy, seed: i64, chunk: [i32; 2], sea_level: i32, generator_min_y: i32) -> Result<Vec<i32>, ()> {
    let (x, z) = ((chunk[0] << 4) + 2, (chunk[1] << 4) + 2);
    let mut attempt: i64 = 0;
    loop {
        if attempt >= MAX_ATTEMPTS {
            return Err(());
        }
        random.set_large_feature_seed(seed.wrapping_add(attempt), chunk[0], chunk[1]);
        attempt += 1;
        // getRandomHorizontalDirection: Plane.HORIZONTAL is north, east, south, west.
        let direction = [NORTH, EAST, SOUTH, WEST][random.next_int_bound(4) as usize];
        let start = Piece { kind: START, depth: 0, bb: [x, 64, z, x + 4, 74, z + 4], direction, fields: vec![0, 1] };
        let mut planner = Planner {
            random: &mut *random,
            pieces: vec![start],
            pending: Vec::new(),
            counts: [0; 11],
            current: (0..WEIGHTS.len()).collect(),
            imposed: None,
            total_weight: 0,
            previous: None,
            portal_room: false,
        };
        planner.children(0);
        while !planner.pending.is_empty() {
            let j = planner.random.next_int_bound(planner.pending.len() as i32) as usize;
            let index = planner.pending.remove(j);
            planner.children(index);
        }
        let portal_room = planner.portal_room;
        let mut pieces = planner.pieces;
        // moveBelowSeaLevel(seaLevel, minY, random, 10) over the builder's box.
        let mut all = pieces[0].bb;
        for p in &pieces {
            for a in 0..3 {
                all[a] = all[a].min(p.bb[a]);
                all[a + 3] = all[a + 3].max(p.bb[a + 3]);
            }
        }
        let l = sea_level - 10;
        let mut m = all[4] - all[1] + 1 + generator_min_y + 1;
        if m < l {
            m += random.next_int_bound(l - m);
        }
        let offset = m - all[4];
        if !portal_room {
            continue;
        }
        for p in &mut pieces {
            p.bb[1] += offset;
            p.bb[4] += offset;
        }
        let mut words = Vec::with_capacity(pieces.len() * 13);
        for p in &pieces {
            words.extend_from_slice(&[p.kind, p.depth]);
            words.extend_from_slice(&p.bb);
            words.push(p.direction);
            words.push(p.fields.len() as i32);
            words.extend_from_slice(&p.fields);
        }
        return Ok(words);
    }
}

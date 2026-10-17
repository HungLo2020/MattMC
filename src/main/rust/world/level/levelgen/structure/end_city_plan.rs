//! `EndCityStructure` piece planning: the rotation `findGenerationPoint`
//! draws, then `EndCityPieces.startHouseTower` and its recursive section
//! generators (house tower, tower, tower bridge, fat tower) with the
//! generation context's random. Every piece is an `EndCityPiece`, whose box
//! is the template's `getBoundingBox` (rotation only, no mirror, zero pivot).
//! Java rebuilds the pieces from the plan.

use crate::world::level::levelgen::random::Legacy;

/// The only piece kind: `EndCityPieces.EndCityPiece`.
pub(crate) const END_CITY_PIECE: i32 = 0;

/// The template names, in the order `EndCityPieces.nativePlanParams` lists
/// their sizes.
pub(crate) const TEMPLATES: [&str; 19] = [
    "base_floor",
    "base_roof",
    "second_floor_1",
    "second_floor_2",
    "second_roof",
    "third_floor_1",
    "third_floor_2",
    "third_roof",
    "tower_base",
    "tower_piece",
    "tower_top",
    "bridge_end",
    "bridge_piece",
    "bridge_steep_stairs",
    "bridge_gentle_stairs",
    "ship",
    "fat_tower_base",
    "fat_tower_middle",
    "fat_tower_top",
];

const BASE_FLOOR: usize = 0;
const BASE_ROOF: usize = 1;
const SECOND_FLOOR_1: usize = 2;
const SECOND_FLOOR_2: usize = 3;
const SECOND_ROOF: usize = 4;
const THIRD_FLOOR_1: usize = 5;
const THIRD_FLOOR_2: usize = 6;
const THIRD_ROOF: usize = 7;
const TOWER_BASE: usize = 8;
const TOWER_PIECE: usize = 9;
const TOWER_TOP: usize = 10;
const BRIDGE_END: usize = 11;
const BRIDGE_PIECE: usize = 12;
const BRIDGE_STEEP_STAIRS: usize = 13;
const BRIDGE_GENTLE_STAIRS: usize = 14;
const SHIP: usize = 15;
const FAT_TOWER_BASE: usize = 16;
const FAT_TOWER_MIDDLE: usize = 17;
const FAT_TOWER_TOP: usize = 18;

/// `TemplateStructurePiece` sets `Direction.NORTH` (2D value 2).
const NORTH: i32 = 2;

/// Rotation ordinals: NONE 0, CLOCKWISE_90 1, CLOCKWISE_180 2, COUNTERCLOCKWISE_90 3.
const CW_90: i32 = 1;
const CW_180: i32 = 2;
const CCW_90: i32 = 3;

/// `TOWER_BRIDGES` / `FAT_TOWER_BRIDGES`: (rotation, offset).
const TOWER_BRIDGES: [(i32, [i32; 3]); 4] = [(0, [1, -1, 0]), (CW_90, [6, -1, 1]), (CCW_90, [0, -1, 5]), (CW_180, [5, -1, 6])];
const FAT_TOWER_BRIDGES: [(i32, [i32; 3]); 4] = [(0, [4, -1, 0]), (CW_90, [12, -1, 4]), (CCW_90, [0, -1, 8]), (CW_180, [8, -1, 12])];

#[derive(Clone, Copy)]
enum Section {
    HouseTower,
    Tower,
    TowerBridge,
    FatTower,
}

struct Piece {
    template: usize,
    rotation: i32,
    overwrite: bool,
    position: [i32; 3],
    /// [min x, min y, min z, max x, max y, max z].
    bb: [i32; 6],
    depth: i32,
}

/// `StructureTemplate.transform(pos, Mirror.NONE, rotation, BlockPos.ZERO)`.
fn rotate(p: [i32; 3], rotation: i32) -> [i32; 3] {
    let [x, y, z] = p;
    match rotation {
        CCW_90 => [z, y, -x],
        CW_90 => [-z, y, x],
        CW_180 => [-x, y, -z],
        _ => [x, y, z],
    }
}

fn intersects(a: &[i32; 6], b: &[i32; 6]) -> bool {
    a[3] >= b[0] && a[0] <= b[3] && a[5] >= b[2] && a[2] <= b[5] && a[4] >= b[1] && a[1] <= b[4]
}

struct Planner<'a> {
    random: &'a mut Legacy,
    sizes: [[i32; 3]; 19],
    pieces: Vec<Piece>,
    ship_created: bool,
}

impl Planner<'_> {
    fn next_boolean(&mut self) -> bool {
        self.random.next(1) != 0
    }

    /// `new EndCityPiece(manager, name, position, rotation, overwrite)`.
    fn piece(&mut self, template: usize, position: [i32; 3], rotation: i32, overwrite: bool) -> usize {
        let [sx, sy, sz] = self.sizes[template];
        let far = rotate([sx - 1, sy - 1, sz - 1], rotation);
        let bb = [
            far[0].min(0) + position[0],
            far[1].min(0) + position[1],
            far[2].min(0) + position[2],
            far[0].max(0) + position[0],
            far[1].max(0) + position[1],
            far[2].max(0) + position[2],
        ];
        self.pieces.push(Piece { template, rotation, overwrite, position, bb, depth: 0 });
        self.pieces.len() - 1
    }

    /// `addHelper(list, addPiece(manager, parent, offset, name, rotation, overwrite))`.
    fn add(&mut self, list: &mut Vec<usize>, parent: usize, offset: [i32; 3], template: usize, rotation: i32, overwrite: bool) -> usize {
        let p = &self.pieces[parent];
        let d = rotate(offset, p.rotation);
        let at = [p.position[0] + d[0], p.position[1] + d[1], p.position[2] + d[2]];
        let index = self.piece(template, at, rotation, overwrite);
        list.push(index);
        index
    }

    /// `EndCityPieces.recursiveChildren`.
    fn recursive_children(&mut self, section: Section, depth: i32, parent: usize, offset: [i32; 3], list: &mut Vec<usize>) -> bool {
        if depth > 8 {
            return false;
        }
        let mut children = Vec::new();
        if !self.generate(section, depth, parent, offset, &mut children) {
            return false;
        }
        let j = self.random.next(32);
        let parent_depth = self.pieces[parent].depth;
        for &child in &children {
            self.pieces[child].depth = j;
            let bb = self.pieces[child].bb;
            if let Some(&hit) = list.iter().find(|&&other| intersects(&self.pieces[other].bb, &bb)) {
                if self.pieces[hit].depth != parent_depth {
                    return false;
                }
            }
        }
        list.extend(children);
        true
    }

    /// `offset` is the `BlockPos` argument; only the house tower reads it
    /// (the others are passed null).
    fn generate(&mut self, section: Section, i: i32, parent: usize, offset: [i32; 3], list: &mut Vec<usize>) -> bool {
        let rotation = self.pieces[parent].rotation;
        match section {
            Section::HouseTower => {
                if i > 8 {
                    return false;
                }
                let mut p = self.add(list, parent, offset, BASE_FLOOR, rotation, true);
                match self.random.next_int_bound(3) {
                    0 => {
                        self.add(list, p, [-1, 4, -1], BASE_ROOF, rotation, true);
                    }
                    1 => {
                        p = self.add(list, p, [-1, 0, -1], SECOND_FLOOR_2, rotation, false);
                        p = self.add(list, p, [-1, 8, -1], SECOND_ROOF, rotation, false);
                        self.recursive_children(Section::Tower, i + 1, p, [0; 3], list);
                    }
                    _ => {
                        p = self.add(list, p, [-1, 0, -1], SECOND_FLOOR_2, rotation, false);
                        p = self.add(list, p, [-1, 4, -1], THIRD_FLOOR_2, rotation, false);
                        p = self.add(list, p, [-1, 8, -1], THIRD_ROOF, rotation, true);
                        self.recursive_children(Section::Tower, i + 1, p, [0; 3], list);
                    }
                }
                true
            }
            Section::Tower => {
                let x = 3 + self.random.next_int_bound(2);
                let z = 3 + self.random.next_int_bound(2);
                let mut p = self.add(list, parent, [x, -3, z], TOWER_BASE, rotation, true);
                p = self.add(list, p, [0, 7, 0], TOWER_PIECE, rotation, true);
                let mut bridge_floor = (self.random.next_int_bound(3) == 0).then_some(p);
                let j = 1 + self.random.next_int_bound(3);
                for k in 0..j {
                    p = self.add(list, p, [0, 4, 0], TOWER_PIECE, rotation, true);
                    if k < j - 1 && self.next_boolean() {
                        bridge_floor = Some(p);
                    }
                }
                if let Some(floor) = bridge_floor {
                    for (turn, offset) in TOWER_BRIDGES {
                        if self.next_boolean() {
                            let end = self.add(list, floor, offset, BRIDGE_END, (rotation + turn) & 3, true);
                            self.recursive_children(Section::TowerBridge, i + 1, end, [0; 3], list);
                        }
                    }
                } else if i != 7 {
                    return self.recursive_children(Section::FatTower, i + 1, p, [0; 3], list);
                }
                self.add(list, p, [-1, 4, -1], TOWER_TOP, rotation, true);
                true
            }
            Section::TowerBridge => {
                let j = self.random.next_int_bound(4) + 1;
                let mut p = self.add(list, parent, [0, 0, -4], BRIDGE_PIECE, rotation, true);
                self.pieces[p].depth = -1;
                let mut k = 0;
                for _ in 0..j {
                    if self.next_boolean() {
                        p = self.add(list, p, [0, k, -4], BRIDGE_PIECE, rotation, true);
                        k = 0;
                    } else {
                        p = if self.next_boolean() {
                            self.add(list, p, [0, k, -4], BRIDGE_STEEP_STAIRS, rotation, true)
                        } else {
                            self.add(list, p, [0, k, -8], BRIDGE_GENTLE_STAIRS, rotation, true)
                        };
                        k = 4;
                    }
                }
                if !self.ship_created && self.random.next_int_bound(10 - i) == 0 {
                    let x = -8 + self.random.next_int_bound(8);
                    let z = -70 + self.random.next_int_bound(10);
                    self.add(list, p, [x, k, z], SHIP, rotation, true);
                    self.ship_created = true;
                } else if !self.recursive_children(Section::HouseTower, i + 1, p, [-3, k + 1, -11], list) {
                    return false;
                }
                let end = self.add(list, p, [4, k, 0], BRIDGE_END, (rotation + CW_180) & 3, true);
                self.pieces[end].depth = -1;
                true
            }
            Section::FatTower => {
                let mut p = self.add(list, parent, [-3, 4, -3], FAT_TOWER_BASE, rotation, true);
                p = self.add(list, p, [0, 4, 0], FAT_TOWER_MIDDLE, rotation, true);
                let mut j = 0;
                while j < 2 && self.random.next_int_bound(3) != 0 {
                    p = self.add(list, p, [0, 8, 0], FAT_TOWER_MIDDLE, rotation, true);
                    for (turn, offset) in FAT_TOWER_BRIDGES {
                        if self.next_boolean() {
                            let end = self.add(list, p, offset, BRIDGE_END, (rotation + turn) & 3, true);
                            self.recursive_children(Section::TowerBridge, i + 1, end, [0; 3], list);
                        }
                    }
                    j += 1;
                }
                self.add(list, p, [-2, 8, -2], FAT_TOWER_TOP, rotation, true);
                true
            }
        }
    }
}

/// `params`: the 19 template sizes (x, y, z) in [`TEMPLATES`] order.
///
/// Words per piece: `[kind, gen depth, box (6), orientation, 6, template
/// index, rotation, overwrite, template x, y, z]`.
pub(crate) fn plan(random: &mut Legacy, _chunk: [i32; 2], stub: [i32; 3], params: &[i32]) -> Result<Vec<i32>, ()> {
    if params.len() != TEMPLATES.len() * 3 {
        return Err(());
    }
    let mut sizes = [[0; 3]; 19];
    for (t, size) in sizes.iter_mut().enumerate() {
        *size = [params[t * 3], params[t * 3 + 1], params[t * 3 + 2]];
        if size.iter().any(|&s| !(1..=256).contains(&s)) {
            return Err(());
        }
    }
    // findGenerationPoint: Rotation.getRandom before the height lookup.
    let rotation = random.next_int_bound(4);
    let mut planner = Planner { random, sizes, pieces: Vec::new(), ship_created: false };

    // startHouseTower.
    let mut list = Vec::new();
    let mut p = planner.piece(BASE_FLOOR, stub, rotation, true);
    list.push(p);
    p = planner.add(&mut list, p, [-1, 0, -1], SECOND_FLOOR_1, rotation, false);
    p = planner.add(&mut list, p, [-1, 4, -1], THIRD_FLOOR_1, rotation, false);
    p = planner.add(&mut list, p, [-1, 8, -1], THIRD_ROOF, rotation, true);
    planner.recursive_children(Section::Tower, 1, p, [0; 3], &mut list);

    let mut words = Vec::with_capacity(list.len() * 16);
    for &index in &list {
        let piece = &planner.pieces[index];
        words.extend_from_slice(&[END_CITY_PIECE, piece.depth]);
        words.extend_from_slice(&piece.bb);
        words.extend_from_slice(&[NORTH, 6, piece.template as i32, piece.rotation, i32::from(piece.overwrite)]);
        words.extend_from_slice(&piece.position);
    }
    Ok(words)
}

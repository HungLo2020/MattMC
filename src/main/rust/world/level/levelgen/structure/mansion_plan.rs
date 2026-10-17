//! `WoodlandMansionStructure.findGenerationPoint` + `generatePieces`
//! (`WoodlandMansionPieces.generateMansion`): the rotation draw, the
//! `MansionGrid` layout (corridor recursion, edge cleaning, room
//! identification, third floor), then `MansionPiecePlacer.createMansion`
//! (entrance, outer walls, roofs, corridors, carpets, indoor walls, rooms).
//! Java rebuilds the pieces from the words (`WoodlandMansionPieces.addNativePieces`).
//!
//! Every piece is a `WoodlandMansionPiece` (kind [`MANSION_PIECE`], depth 0,
//! orientation NORTH) with fields `[template, rotation, mirror, tx, ty, tz]`:
//! `template` indexes [`NAMES`] (same table as `WoodlandMansionPieces.NATIVE_TEMPLATES`).
//!
//! `params` (`WoodlandMansionPieces.nativePlanParams`): `[NAMES.len(), then the
//! template size x, y, z for each name in order]`; the sizes only give the boxes.

use crate::world::level::levelgen::random::Legacy;

pub(crate) const MANSION_PIECE: i32 = 80;

/// Template names (`woodland_mansion/<name>`); index = template field.
pub(crate) const NAMES: [&str; 73] = [
    "entrance", "wall_flat", "wall_window", "wall_corner", "roof", "roof_front", "small_wall",
    "small_wall_corner", "roof_corner", "roof_inner_corner", "corridor_floor", "carpet_north",
    "carpet_east", "carpet_south_1", "carpet_south_2", "carpet_west_1", "carpet_west_2",
    "indoors_wall_1", "indoors_wall_2", "indoors_door_1", "indoors_door_2",
    "1x1_a1", "1x1_a2", "1x1_a3", "1x1_a4", "1x1_a5",
    "1x1_as1", "1x1_as2", "1x1_as3", "1x1_as4",
    "1x2_a1", "1x2_a2", "1x2_a3", "1x2_a4", "1x2_a5", "1x2_a6", "1x2_a7", "1x2_a8", "1x2_a9",
    "1x2_b1", "1x2_b2", "1x2_b3", "1x2_b4", "1x2_b5",
    "1x2_s1", "1x2_s2",
    "2x2_a1", "2x2_a2", "2x2_a3", "2x2_a4",
    "2x2_s1",
    "1x1_b1", "1x1_b2", "1x1_b3", "1x1_b4", "1x1_b5",
    "1x2_c_stairs", "1x2_c1", "1x2_c2", "1x2_c3", "1x2_c4",
    "1x2_d_stairs", "1x2_d1", "1x2_d2", "1x2_d3", "1x2_d4", "1x2_d5",
    "1x2_se1",
    "2x2_b1", "2x2_b2", "2x2_b3", "2x2_b4", "2x2_b5",
];

const ENTRANCE: usize = 0;
const WALL_FLAT: usize = 1;
const WALL_WINDOW: usize = 2;
const WALL_CORNER: usize = 3;
const ROOF: usize = 4;
const ROOF_FRONT: usize = 5;
const SMALL_WALL: usize = 6;
const SMALL_WALL_CORNER: usize = 7;
const ROOF_CORNER: usize = 8;
const ROOF_INNER_CORNER: usize = 9;
const CORRIDOR_FLOOR: usize = 10;
const CARPET_NORTH: usize = 11;
const CARPET_EAST: usize = 12;
const CARPET_SOUTH_1: usize = 13;
const CARPET_SOUTH_2: usize = 14;
const CARPET_WEST_1: usize = 15;
const CARPET_WEST_2: usize = 16;
const INDOORS_WALL_1: usize = 17;
const INDOORS_WALL_2: usize = 18;
const INDOORS_DOOR_1: usize = 19;
const INDOORS_DOOR_2: usize = 20;
const R1X1_A: usize = 21; // 1..5
const R1X1_AS: usize = 26; // 1..4
const R1X2_A: usize = 30; // 1..9
const R1X2_B: usize = 39; // 1..5
const R1X2_S: usize = 44; // 1..2
const R2X2_A: usize = 46; // 1..4
const R2X2_S1: usize = 50;
const R1X1_B: usize = 51; // 1..5
const R1X2_C_STAIRS: usize = 56;
const R1X2_C: usize = 57; // 1..4
const R1X2_D_STAIRS: usize = 61;
const R1X2_D: usize = 62; // 1..5
const R1X2_SE: usize = 67; // 1
const R2X2_B: usize = 68; // 1..5

/// `Direction.get2DDataValue()`; `UP` only appears as a room door marker.
const SOUTH: i32 = 0;
const WEST: i32 = 1;
const NORTH: i32 = 2;
const EAST: i32 = 3;
const UP: i32 = 4;
/// `Direction.Plane.HORIZONTAL` iteration order.
const HORIZONTAL: [i32; 4] = [NORTH, EAST, SOUTH, WEST];

/// Rotation ordinals.
const CW90: i32 = 1;
const CW180: i32 = 2;
const CCW90: i32 = 3;
/// Mirror ordinals.
const LEFT_RIGHT: i32 = 1;
const FRONT_BACK: i32 = 2;

fn step(d: i32) -> (i32, i32) {
    match d {
        SOUTH => (0, 1),
        WEST => (-1, 0),
        NORTH => (0, -1),
        EAST => (1, 0),
        _ => (0, 0),
    }
}
fn cw(d: i32) -> i32 {
    (d + 1) & 3
}
fn ccw(d: i32) -> i32 {
    (d + 3) & 3
}
fn opposite(d: i32) -> i32 {
    (d + 2) & 3
}
/// `Rotation.rotate(Direction)` for horizontal directions.
fn rot_dir(rot: i32, d: i32) -> i32 {
    (d + rot) & 3
}
/// `Rotation.getRotated`.
fn rotated(a: i32, b: i32) -> i32 {
    (a + b) & 3
}
/// `BlockPos.relative(direction, n)`.
fn rel(p: [i32; 3], d: i32, n: i32) -> [i32; 3] {
    let (x, z) = step(d);
    [p[0] + x * n, p[1], p[2] + z * n]
}
fn above(p: [i32; 3], n: i32) -> [i32; 3] {
    [p[0], p[1] + n, p[2]]
}

const CLEAR: i32 = 0;
const CORRIDOR: i32 = 1;
const ROOM: i32 = 2;
const START_ROOM: i32 = 3;
const BLOCKED: i32 = 5;
const ROOM_1X1: i32 = 65536;
const ROOM_1X2: i32 = 131072;
const ROOM_2X2: i32 = 262144;
const ROOM_ORIGIN_FLAG: i32 = 1048576;
const ROOM_DOOR_FLAG: i32 = 2097152;
const ROOM_STAIRS_FLAG: i32 = 4194304;
const ROOM_CORRIDOR_FLAG: i32 = 8388608;
const ROOM_TYPE_MASK: i32 = 983040;
const ROOM_ID_MASK: i32 = 65535;

/// `SimpleGrid`.
#[derive(Clone)]
struct Grid {
    cells: Vec<i32>,
    width: i32,
    height: i32,
    outside: i32,
}

impl Grid {
    fn new(width: i32, height: i32, outside: i32) -> Self {
        Grid { cells: vec![0; (width * height) as usize], width, height, outside }
    }
    fn inside(&self, i: i32, j: i32) -> bool {
        i >= 0 && i < self.width && j >= 0 && j < self.height
    }
    fn set(&mut self, i: i32, j: i32, k: i32) {
        if self.inside(i, j) {
            self.cells[(i * self.height + j) as usize] = k;
        }
    }
    fn set_rect(&mut self, i: i32, j: i32, k: i32, l: i32, m: i32) {
        for n in j..=l {
            for o in i..=k {
                self.set(o, n, m);
            }
        }
    }
    fn get(&self, i: i32, j: i32) -> i32 {
        if self.inside(i, j) {
            self.cells[(i * self.height + j) as usize]
        } else {
            self.outside
        }
    }
    fn setif(&mut self, i: i32, j: i32, k: i32, l: i32) {
        if self.get(i, j) == k {
            self.set(i, j, l);
        }
    }
    fn edges_to(&self, i: i32, j: i32, k: i32) -> bool {
        self.get(i - 1, j) == k || self.get(i + 1, j) == k || self.get(i, j + 1) == k || self.get(i, j - 1) == k
    }
}

fn is_house(g: &Grid, i: i32, j: i32) -> bool {
    matches!(g.get(i, j), 1..=4)
}

/// `MansionGrid`.
struct MansionGrid {
    base: Grid,
    third: Grid,
    floors: [Grid; 3],
    entrance_x: i32,
    entrance_y: i32,
}

fn recursive_corridor(random: &mut Legacy, g: &mut Grid, i: i32, j: i32, direction: i32, k: i32) {
    if k <= 0 {
        return;
    }
    let (dx, dz) = step(direction);
    g.set(i, j, CORRIDOR);
    g.setif(i + dx, j + dz, CLEAR, CORRIDOR);
    for _ in 0..8 {
        // Direction.from2DDataValue(nextInt(4)).
        let d2 = random.next_int_bound(4);
        if d2 != opposite(direction) && (d2 != EAST || random.next(1) == 0) {
            let (m, n) = (i + dx, j + dz);
            let (sx, sz) = step(d2);
            if g.get(m + sx, n + sz) == CLEAR && g.get(m + sx * 2, n + sz * 2) == CLEAR {
                recursive_corridor(random, g, i + dx + sx, j + dz + sz, d2, k - 1);
                break;
            }
        }
    }
    let (ax, az) = step(cw(direction));
    let (bx, bz) = step(ccw(direction));
    g.setif(i + ax, j + az, CLEAR, ROOM);
    g.setif(i + bx, j + bz, CLEAR, ROOM);
    g.setif(i + dx + ax, j + dz + az, CLEAR, ROOM);
    g.setif(i + dx + bx, j + dz + bz, CLEAR, ROOM);
    g.setif(i + dx * 2, j + dz * 2, CLEAR, ROOM);
    g.setif(i + ax * 2, j + az * 2, CLEAR, ROOM);
    g.setif(i + bx * 2, j + bz * 2, CLEAR, ROOM);
}

fn clean_edges(g: &mut Grid) -> bool {
    let mut changed = false;
    for i in 0..g.height {
        for j in 0..g.width {
            if g.get(j, i) == CLEAR {
                let k = is_house(g, j + 1, i) as i32
                    + is_house(g, j - 1, i) as i32
                    + is_house(g, j, i + 1) as i32
                    + is_house(g, j, i - 1) as i32;
                if k >= 3 {
                    g.set(j, i, ROOM);
                    changed = true;
                } else if k == 2 {
                    let l = is_house(g, j + 1, i + 1) as i32
                        + is_house(g, j - 1, i + 1) as i32
                        + is_house(g, j + 1, i - 1) as i32
                        + is_house(g, j - 1, i - 1) as i32;
                    if l <= 1 {
                        g.set(j, i, ROOM);
                        changed = true;
                    }
                }
            }
        }
    }
    changed
}

fn identify_rooms(random: &mut Legacy, g: &Grid, g2: &mut Grid) {
    let mut cells = Vec::new();
    for i in 0..g.height {
        for j in 0..g.width {
            if g.get(j, i) == ROOM {
                cells.push((j, i));
            }
        }
    }
    // Util.shuffle.
    let mut jj = cells.len();
    while jj > 1 {
        let k = random.next_int_bound(jj as i32) as usize;
        cells.swap(jj - 1, k);
        jj -= 1;
    }
    let mut id = 10;
    for &(k, l) in &cells {
        if g2.get(k, l) != 0 {
            continue;
        }
        let (mut m, mut n, mut o, mut p) = (k, k, l, l);
        let mut q = ROOM_1X1;
        if g2.get(k + 1, l) == 0 && g2.get(k, l + 1) == 0 && g2.get(k + 1, l + 1) == 0
            && g.get(k + 1, l) == ROOM && g.get(k, l + 1) == ROOM && g.get(k + 1, l + 1) == ROOM
        {
            n = k + 1;
            p = l + 1;
            q = ROOM_2X2;
        } else if g2.get(k - 1, l) == 0 && g2.get(k, l + 1) == 0 && g2.get(k - 1, l + 1) == 0
            && g.get(k - 1, l) == ROOM && g.get(k, l + 1) == ROOM && g.get(k - 1, l + 1) == ROOM
        {
            m = k - 1;
            p = l + 1;
            q = ROOM_2X2;
        } else if g2.get(k - 1, l) == 0 && g2.get(k, l - 1) == 0 && g2.get(k - 1, l - 1) == 0
            && g.get(k - 1, l) == ROOM && g.get(k, l - 1) == ROOM && g.get(k - 1, l - 1) == ROOM
        {
            m = k - 1;
            o = l - 1;
            q = ROOM_2X2;
        } else if g2.get(k + 1, l) == 0 && g.get(k + 1, l) == ROOM {
            n = k + 1;
            q = ROOM_1X2;
        } else if g2.get(k, l + 1) == 0 && g.get(k, l + 1) == ROOM {
            p = l + 1;
            q = ROOM_1X2;
        } else if g2.get(k - 1, l) == 0 && g.get(k - 1, l) == ROOM {
            m = k - 1;
            q = ROOM_1X2;
        } else if g2.get(k, l - 1) == 0 && g.get(k, l - 1) == ROOM {
            o = l - 1;
            q = ROOM_1X2;
        }
        let mut r = if random.next(1) != 0 { m } else { n };
        let mut s = if random.next(1) != 0 { o } else { p };
        let mut t = ROOM_DOOR_FLAG;
        if !g.edges_to(r, s, CORRIDOR) {
            r = if r == m { n } else { m };
            s = if s == o { p } else { o };
            if !g.edges_to(r, s, CORRIDOR) {
                s = if s == o { p } else { o };
                if !g.edges_to(r, s, CORRIDOR) {
                    r = if r == m { n } else { m };
                    s = if s == o { p } else { o };
                    if !g.edges_to(r, s, CORRIDOR) {
                        t = 0;
                        r = m;
                        s = o;
                    }
                }
            }
        }
        for u in o..=p {
            for v in m..=n {
                if v == r && u == s {
                    g2.set(v, u, ROOM_ORIGIN_FLAG | t | q | id);
                } else {
                    g2.set(v, u, q | id);
                }
            }
        }
        id += 1;
    }
}

impl MansionGrid {
    fn new(random: &mut Legacy) -> Result<Self, ()> {
        let (ex, ey) = (7, 4);
        let mut base = Grid::new(11, 11, BLOCKED);
        base.set_rect(ex, ey, ex + 1, ey + 1, START_ROOM);
        base.set_rect(ex - 1, ey, ex - 1, ey + 1, ROOM);
        base.set_rect(ex + 2, ey - 2, ex + 3, ey + 3, BLOCKED);
        base.set_rect(ex + 1, ey - 2, ex + 1, ey - 1, CORRIDOR);
        base.set_rect(ex + 1, ey + 2, ex + 1, ey + 3, CORRIDOR);
        base.set(ex - 1, ey - 1, CORRIDOR);
        base.set(ex - 1, ey + 2, CORRIDOR);
        base.set_rect(0, 0, 11, 1, BLOCKED);
        base.set_rect(0, 9, 11, 11, BLOCKED);
        recursive_corridor(random, &mut base, ex, ey - 2, WEST, 6);
        recursive_corridor(random, &mut base, ex, ey + 3, WEST, 6);
        recursive_corridor(random, &mut base, ex - 2, ey - 1, WEST, 3);
        recursive_corridor(random, &mut base, ex - 2, ey + 2, WEST, 3);
        while clean_edges(&mut base) {}
        let mut floors = [Grid::new(11, 11, BLOCKED), Grid::new(11, 11, BLOCKED), Grid::new(11, 11, BLOCKED)];
        identify_rooms(random, &base, &mut floors[0]);
        identify_rooms(random, &base, &mut floors[1]);
        floors[0].set_rect(ex + 1, ey, ex + 1, ey + 1, ROOM_CORRIDOR_FLAG);
        floors[1].set_rect(ex + 1, ey, ex + 1, ey + 1, ROOM_CORRIDOR_FLAG);
        let third = Grid::new(base.width, base.height, BLOCKED);
        let mut grid = MansionGrid { base, third, floors, entrance_x: ex, entrance_y: ey };
        grid.setup_third_floor(random)?;
        let third = grid.third.clone();
        identify_rooms(random, &third, &mut grid.floors[2]);
        Ok(grid)
    }

    fn is_room_id(&self, i: i32, j: i32, k: usize, l: i32) -> bool {
        (self.floors[k].get(i, j) & ROOM_ID_MASK) == l
    }

    fn get_1x2_room_direction(&self, i: i32, j: i32, k: usize, l: i32) -> Option<i32> {
        HORIZONTAL.into_iter().find(|&d| {
            let (x, z) = step(d);
            self.is_room_id(i + x, j + z, k, l)
        })
    }

    fn setup_third_floor(&mut self, random: &mut Legacy) -> Result<(), ()> {
        let mut list = Vec::new();
        for i in 0..self.third.height {
            for j in 0..self.third.width {
                let k = self.floors[1].get(j, i);
                if (k & ROOM_TYPE_MASK) == ROOM_1X2 && (k & ROOM_DOOR_FLAG) == ROOM_DOOR_FLAG {
                    list.push((j, i));
                }
            }
        }
        let (w, h) = (self.third.width, self.third.height);
        if list.is_empty() {
            self.third.set_rect(0, 0, w, h, BLOCKED);
            return Ok(());
        }
        let (ta, tb) = list[random.next_int_bound(list.len() as i32) as usize];
        let jx = self.floors[1].get(ta, tb);
        self.floors[1].set(ta, tb, jx | ROOM_STAIRS_FLAG);
        // Java would throw on a missing direction.
        let direction = self.get_1x2_room_direction(ta, tb, 1, jx & ROOM_ID_MASK).ok_or(())?;
        let (sx, sz) = step(direction);
        let (l, m) = (ta + sx, tb + sz);
        for n in 0..h {
            for o in 0..w {
                if !is_house(&self.base, o, n) {
                    self.third.set(o, n, BLOCKED);
                } else if o == ta && n == tb {
                    self.third.set(o, n, START_ROOM);
                } else if o == l && n == m {
                    self.third.set(o, n, START_ROOM);
                    self.floors[2].set(o, n, ROOM_CORRIDOR_FLAG);
                }
            }
        }
        let mut list2 = Vec::new();
        for d in HORIZONTAL {
            let (x, z) = step(d);
            if self.third.get(l + x, m + z) == CLEAR {
                list2.push(d);
            }
        }
        if list2.is_empty() {
            self.third.set_rect(0, 0, w, h, BLOCKED);
            self.floors[1].set(ta, tb, jx);
        } else {
            let d3 = list2[random.next_int_bound(list2.len() as i32) as usize];
            let (x, z) = step(d3);
            recursive_corridor(random, &mut self.third, l + x, m + z, d3, 4);
            while clean_edges(&mut self.third) {}
        }
        Ok(())
    }
}

/// `PlacementData`.
struct Placement {
    rotation: i32,
    position: [i32; 3],
    wall: usize,
}

struct Placer<'a> {
    random: &'a mut Legacy,
    sizes: &'a [i32],
    start_x: i32,
    start_y: i32,
    out: Vec<i32>,
}

impl Placer<'_> {
    /// `new WoodlandMansionPiece(manager, name, pos, rotation, mirror)`.
    fn add(&mut self, template: usize, pos: [i32; 3], rotation: i32, mirror: i32) {
        let size = [self.sizes[template * 3], self.sizes[template * 3 + 1], self.sizes[template * 3 + 2]];
        let a = transform([0, 0, 0], mirror, rotation);
        let b = transform([size[0] - 1, size[1] - 1, size[2] - 1], mirror, rotation);
        self.out.extend_from_slice(&[
            MANSION_PIECE,
            0,
            a[0].min(b[0]) + pos[0],
            a[1].min(b[1]) + pos[1],
            a[2].min(b[2]) + pos[2],
            a[0].max(b[0]) + pos[0],
            a[1].max(b[1]) + pos[1],
            a[2].max(b[2]) + pos[2],
            NORTH,
            6,
            template as i32,
            rotation,
            mirror,
            pos[0],
            pos[1],
            pos[2],
        ]);
    }

    fn add0(&mut self, template: usize, pos: [i32; 3], rotation: i32) {
        self.add(template, pos, rotation, 0);
    }

    fn create_mansion(&mut self, pos: [i32; 3], rotation: i32, grid: &MansionGrid) -> Result<(), ()> {
        let mut pd = Placement { rotation, position: pos, wall: WALL_FLAT };
        self.entrance(&mut pd);
        let mut pd2 = Placement { rotation: pd.rotation, position: above(pd.position, 8), wall: WALL_WINDOW };
        let base = &grid.base;
        let third = &grid.third;
        self.start_x = grid.entrance_x + 1;
        self.start_y = grid.entrance_y + 1;
        let (i, j) = (grid.entrance_x + 1, grid.entrance_y);
        let (sx, sy) = (self.start_x, self.start_y);
        self.traverse_outer_walls(&mut pd, base, SOUTH, sx, sy, i, j)?;
        self.traverse_outer_walls(&mut pd2, base, SOUTH, sx, sy, i, j)?;
        let mut pd3 = Placement { rotation: pd.rotation, position: above(pd.position, 19), wall: WALL_WINDOW };
        let mut found = false;
        let mut k = 0;
        while k < third.height && !found {
            let mut l = third.width - 1;
            while l >= 0 && !found {
                if is_house(third, l, k) {
                    pd3.position = rel(pd3.position, rot_dir(rotation, SOUTH), 8 + (k - self.start_y) * 8);
                    pd3.position = rel(pd3.position, rot_dir(rotation, EAST), (l - self.start_x) * 8);
                    self.traverse_wall_piece(&mut pd3);
                    self.traverse_outer_walls(&mut pd3, third, SOUTH, l, k, l, k)?;
                    found = true;
                }
                l -= 1;
            }
            k += 1;
        }
        self.create_roof(above(pos, 16), rotation, base, Some(third));
        self.create_roof(above(pos, 27), rotation, third, None);

        for lx in 0..3usize {
            let pos2 = above(pos, 8 * lx as i32 + if lx == 2 { 3 } else { 0 });
            let g3 = &grid.floors[lx];
            let g4 = if lx == 2 { third } else { base };
            let carpet_s = if lx == 0 { CARPET_SOUTH_1 } else { CARPET_SOUTH_2 };
            let carpet_w = if lx == 0 { CARPET_WEST_1 } else { CARPET_WEST_2 };
            for m in 0..g4.height {
                for n in 0..g4.width {
                    if g4.get(n, m) == CORRIDOR {
                        let mut p3 = rel(pos2, rot_dir(rotation, SOUTH), 8 + (m - self.start_y) * 8);
                        p3 = rel(p3, rot_dir(rotation, EAST), (n - self.start_x) * 8);
                        self.add0(CORRIDOR_FLOOR, p3, rotation);
                        if g4.get(n, m - 1) == CORRIDOR || (g3.get(n, m - 1) & ROOM_CORRIDOR_FLAG) == ROOM_CORRIDOR_FLAG {
                            self.add0(CARPET_NORTH, above(rel(p3, rot_dir(rotation, EAST), 1), 1), rotation);
                        }
                        if g4.get(n + 1, m) == CORRIDOR || (g3.get(n + 1, m) & ROOM_CORRIDOR_FLAG) == ROOM_CORRIDOR_FLAG {
                            let p = rel(rel(p3, rot_dir(rotation, SOUTH), 1), rot_dir(rotation, EAST), 5);
                            self.add0(CARPET_EAST, above(p, 1), rotation);
                        }
                        if g4.get(n, m + 1) == CORRIDOR || (g3.get(n, m + 1) & ROOM_CORRIDOR_FLAG) == ROOM_CORRIDOR_FLAG {
                            let p = rel(rel(p3, rot_dir(rotation, SOUTH), 5), rot_dir(rotation, WEST), 1);
                            self.add0(carpet_s, p, rotation);
                        }
                        if g4.get(n - 1, m) == CORRIDOR || (g3.get(n - 1, m) & ROOM_CORRIDOR_FLAG) == ROOM_CORRIDOR_FLAG {
                            let p = rel(rel(p3, rot_dir(rotation, WEST), 1), rot_dir(rotation, NORTH), 1);
                            self.add0(carpet_w, p, rotation);
                        }
                    }
                }
            }

            let wall = if lx == 0 { INDOORS_WALL_1 } else { INDOORS_WALL_2 };
            let door = if lx == 0 { INDOORS_DOOR_1 } else { INDOORS_DOOR_2 };
            let mut list2: Vec<i32> = Vec::new();
            for o in 0..g4.height {
                for p in 0..g4.width {
                    let mut bl2 = lx == 2 && g4.get(p, o) == START_ROOM;
                    if g4.get(p, o) == ROOM || bl2 {
                        let q = g3.get(p, o);
                        let r = q & ROOM_TYPE_MASK;
                        let s = q & ROOM_ID_MASK;
                        bl2 = bl2 && (q & ROOM_CORRIDOR_FLAG) == ROOM_CORRIDOR_FLAG;
                        list2.clear();
                        if (q & ROOM_DOOR_FLAG) == ROOM_DOOR_FLAG {
                            for d in HORIZONTAL {
                                let (x, z) = step(d);
                                if g4.get(p + x, o + z) == CORRIDOR {
                                    list2.push(d);
                                }
                            }
                        }
                        let d2: Option<i32> = if !list2.is_empty() {
                            Some(list2[self.random.next_int_bound(list2.len() as i32) as usize])
                        } else if (q & ROOM_ORIGIN_FLAG) == ROOM_ORIGIN_FLAG {
                            Some(UP)
                        } else {
                            None
                        };
                        let mut p4 = rel(pos2, rot_dir(rotation, SOUTH), 8 + (o - self.start_y) * 8);
                        p4 = rel(p4, rot_dir(rotation, EAST), -1 + (p - self.start_x) * 8);
                        if is_house(g4, p - 1, o) && !grid.is_room_id(p - 1, o, lx, s) {
                            self.add0(if d2 == Some(WEST) { door } else { wall }, p4, rotation);
                        }
                        if g4.get(p + 1, o) == CORRIDOR && !bl2 {
                            let p5 = rel(p4, rot_dir(rotation, EAST), 8);
                            self.add0(if d2 == Some(EAST) { door } else { wall }, p5, rotation);
                        }
                        if is_house(g4, p, o + 1) && !grid.is_room_id(p, o + 1, lx, s) {
                            let p5 = rel(rel(p4, rot_dir(rotation, SOUTH), 7), rot_dir(rotation, EAST), 7);
                            self.add0(if d2 == Some(SOUTH) { door } else { wall }, p5, rotated(rotation, CW90));
                        }
                        if g4.get(p, o - 1) == CORRIDOR && !bl2 {
                            let p5 = rel(rel(p4, rot_dir(rotation, NORTH), 1), rot_dir(rotation, EAST), 7);
                            self.add0(if d2 == Some(NORTH) { door } else { wall }, p5, rotated(rotation, CW90));
                        }
                        if r == ROOM_1X1 {
                            self.add_room_1x1(p4, rotation, d2, lx);
                        } else if r == ROOM_1X2 && d2.is_some() {
                            let d3 = grid.get_1x2_room_direction(p, o, lx, s);
                            let stairs = (q & ROOM_STAIRS_FLAG) == ROOM_STAIRS_FLAG;
                            self.add_room_1x2(p4, rotation, d3, d2.unwrap_or(-1), lx, stairs);
                        } else if r == ROOM_2X2 && d2.is_some() && d2 != Some(UP) {
                            let d2v = d2.unwrap_or(0);
                            let mut d3 = cw(d2v);
                            let (x, z) = step(d3);
                            if !grid.is_room_id(p + x, o + z, lx, s) {
                                d3 = opposite(d3);
                            }
                            self.add_room_2x2(p4, rotation, d3, d2v, lx);
                        } else if r == ROOM_2X2 && d2 == Some(UP) {
                            self.add0(R2X2_S1, rel(p4, rot_dir(rotation, EAST), 1), rotation);
                        }
                    }
                }
            }
        }
        Ok(())
    }

    fn traverse_outer_walls(&mut self, pd: &mut Placement, g: &Grid, mut direction: i32, i: i32, j: i32, k: i32, l: i32) -> Result<(), ()> {
        let (mut m, mut n) = (i, j);
        let d2 = direction;
        let mut guard = 0u32;
        loop {
            guard += 1;
            if guard > 100_000 {
                return Err(());
            }
            let (dx, dz) = step(direction);
            let (cx, cz) = step(ccw(direction));
            if !is_house(g, m + dx, n + dz) {
                self.traverse_turn(pd);
                direction = cw(direction);
                if m != k || n != l || d2 != direction {
                    self.traverse_wall_piece(pd);
                }
            } else if is_house(g, m + dx + cx, n + dz + cz) {
                self.traverse_inner_turn(pd);
                m += dx;
                n += dz;
                direction = ccw(direction);
            } else {
                m += dx;
                n += dz;
                if m != k || n != l || d2 != direction {
                    self.traverse_wall_piece(pd);
                }
            }
            if !(m != k || n != l || d2 != direction) {
                return Ok(());
            }
        }
    }

    fn create_roof(&mut self, pos: [i32; 3], rotation: i32, g: &Grid, g2: Option<&Grid>) {
        let s = rot_dir(rotation, SOUTH);
        let e = rot_dir(rotation, EAST);
        let w = rot_dir(rotation, WEST);
        let nn = rot_dir(rotation, NORTH);
        let cell = |this: &Self, i: i32, j: i32| rel(rel(pos, s, 8 + (i - this.start_y) * 8), e, (j - this.start_x) * 8);
        for i in 0..g.height {
            for j in 0..g.width {
                let p2 = cell(self, i, j);
                let bl = g2.is_some_and(|g2| is_house(g2, j, i));
                if is_house(g, j, i) && !bl {
                    self.add0(ROOF, above(p2, 3), rotation);
                    if !is_house(g, j + 1, i) {
                        self.add0(ROOF_FRONT, rel(p2, e, 6), rotation);
                    }
                    if !is_house(g, j - 1, i) {
                        self.add0(ROOF_FRONT, rel(rel(p2, e, 0), s, 7), rotated(rotation, CW180));
                    }
                    if !is_house(g, j, i - 1) {
                        self.add0(ROOF_FRONT, rel(p2, w, 1), rotated(rotation, CCW90));
                    }
                    if !is_house(g, j, i + 1) {
                        self.add0(ROOF_FRONT, rel(rel(p2, e, 6), s, 6), rotated(rotation, CW90));
                    }
                }
            }
        }
        if let Some(g2) = g2 {
            for i in 0..g.height {
                for j in 0..g.width {
                    let p2 = cell(self, i, j);
                    if is_house(g, j, i) && is_house(g2, j, i) {
                        if !is_house(g, j + 1, i) {
                            self.add0(SMALL_WALL, rel(p2, e, 7), rotation);
                        }
                        if !is_house(g, j - 1, i) {
                            self.add0(SMALL_WALL, rel(rel(p2, w, 1), s, 6), rotated(rotation, CW180));
                        }
                        if !is_house(g, j, i - 1) {
                            self.add0(SMALL_WALL, rel(rel(p2, w, 0), nn, 1), rotated(rotation, CCW90));
                        }
                        if !is_house(g, j, i + 1) {
                            self.add0(SMALL_WALL, rel(rel(p2, e, 6), s, 7), rotated(rotation, CW90));
                        }
                        if !is_house(g, j + 1, i) {
                            if !is_house(g, j, i - 1) {
                                self.add0(SMALL_WALL_CORNER, rel(rel(p2, e, 7), nn, 2), rotation);
                            }
                            if !is_house(g, j, i + 1) {
                                self.add0(SMALL_WALL_CORNER, rel(rel(p2, e, 8), s, 7), rotated(rotation, CW90));
                            }
                        }
                        if !is_house(g, j - 1, i) {
                            if !is_house(g, j, i - 1) {
                                self.add0(SMALL_WALL_CORNER, rel(rel(p2, w, 2), nn, 1), rotated(rotation, CCW90));
                            }
                            if !is_house(g, j, i + 1) {
                                self.add0(SMALL_WALL_CORNER, rel(rel(p2, w, 1), s, 8), rotated(rotation, CW180));
                            }
                        }
                    }
                }
            }
        }
        for i in 0..g.height {
            for j in 0..g.width {
                let p2 = cell(self, i, j);
                let bl = g2.is_some_and(|g2| is_house(g2, j, i));
                if is_house(g, j, i) && !bl {
                    if !is_house(g, j + 1, i) {
                        let p3 = rel(p2, e, 6);
                        if !is_house(g, j, i + 1) {
                            self.add0(ROOF_CORNER, rel(p3, s, 6), rotation);
                        } else if is_house(g, j + 1, i + 1) {
                            self.add0(ROOF_INNER_CORNER, rel(p3, s, 5), rotation);
                        }
                        if !is_house(g, j, i - 1) {
                            self.add0(ROOF_CORNER, p3, rotated(rotation, CCW90));
                        } else if is_house(g, j + 1, i - 1) {
                            self.add0(ROOF_INNER_CORNER, rel(rel(p2, e, 9), nn, 2), rotated(rotation, CW90));
                        }
                    }
                    if !is_house(g, j - 1, i) {
                        let p3 = rel(rel(p2, e, 0), s, 0);
                        if !is_house(g, j, i + 1) {
                            self.add0(ROOF_CORNER, rel(p3, s, 6), rotated(rotation, CW90));
                        } else if is_house(g, j - 1, i + 1) {
                            self.add0(ROOF_INNER_CORNER, rel(rel(p3, s, 8), w, 3), rotated(rotation, CCW90));
                        }
                        if !is_house(g, j, i - 1) {
                            self.add0(ROOF_CORNER, p3, rotated(rotation, CW180));
                        } else if is_house(g, j - 1, i - 1) {
                            self.add0(ROOF_INNER_CORNER, rel(p3, s, 1), rotated(rotation, CW180));
                        }
                    }
                }
            }
        }
    }

    fn entrance(&mut self, pd: &mut Placement) {
        let d = rot_dir(pd.rotation, WEST);
        self.add0(ENTRANCE, rel(pd.position, d, 9), pd.rotation);
        pd.position = rel(pd.position, rot_dir(pd.rotation, SOUTH), 16);
    }

    fn traverse_wall_piece(&mut self, pd: &mut Placement) {
        self.add0(pd.wall, rel(pd.position, rot_dir(pd.rotation, EAST), 7), pd.rotation);
        pd.position = rel(pd.position, rot_dir(pd.rotation, SOUTH), 8);
    }

    fn traverse_turn(&mut self, pd: &mut Placement) {
        pd.position = rel(pd.position, rot_dir(pd.rotation, SOUTH), -1);
        self.add0(WALL_CORNER, pd.position, pd.rotation);
        pd.position = rel(pd.position, rot_dir(pd.rotation, SOUTH), -7);
        pd.position = rel(pd.position, rot_dir(pd.rotation, WEST), -6);
        pd.rotation = rotated(pd.rotation, CW90);
    }

    fn traverse_inner_turn(&mut self, pd: &mut Placement) {
        pd.position = rel(pd.position, rot_dir(pd.rotation, SOUTH), 6);
        pd.position = rel(pd.position, rot_dir(pd.rotation, EAST), 8);
        pd.rotation = rotated(pd.rotation, CCW90);
    }

    // FloorRoomCollection picks (floor 0 = First, 1 and 2 = Second/Third).
    fn get_1x1(&mut self, floor: usize) -> usize {
        let base = if floor == 0 { R1X1_A } else { R1X1_B };
        base + self.random.next_int_bound(5) as usize
    }
    fn get_1x1_secret(&mut self) -> usize {
        R1X1_AS + self.random.next_int_bound(4) as usize
    }
    fn get_1x2_side(&mut self, floor: usize, stairs: bool) -> usize {
        if floor == 0 {
            R1X2_A + self.random.next_int_bound(9) as usize
        } else if stairs {
            R1X2_C_STAIRS
        } else {
            R1X2_C + self.random.next_int_bound(4) as usize
        }
    }
    fn get_1x2_front(&mut self, floor: usize, stairs: bool) -> usize {
        if floor == 0 {
            R1X2_B + self.random.next_int_bound(5) as usize
        } else if stairs {
            R1X2_D_STAIRS
        } else {
            R1X2_D + self.random.next_int_bound(5) as usize
        }
    }
    fn get_1x2_secret(&mut self, floor: usize) -> usize {
        if floor == 0 {
            R1X2_S + self.random.next_int_bound(2) as usize
        } else {
            R1X2_SE + self.random.next_int_bound(1) as usize
        }
    }
    fn get_2x2(&mut self, floor: usize) -> usize {
        if floor == 0 {
            R2X2_A + self.random.next_int_bound(4) as usize
        } else {
            R2X2_B + self.random.next_int_bound(5) as usize
        }
    }

    fn add_room_1x1(&mut self, pos: [i32; 3], rotation: i32, direction: Option<i32>, floor: usize) {
        let mut rot2 = 0;
        let mut name = self.get_1x1(floor);
        if direction != Some(EAST) {
            match direction {
                Some(NORTH) => rot2 = rotated(rot2, CCW90),
                Some(WEST) => rot2 = rotated(rot2, CW180),
                Some(SOUTH) => rot2 = rotated(rot2, CW90),
                _ => name = self.get_1x1_secret(),
            }
        }
        // StructureTemplate.getZeroPositionWithTransform((1, 0, 0), NONE, rot2, 7, 7).
        let (i, j) = (6, 6);
        let zp = match rot2 {
            CCW90 => [1, 0, i],
            CW90 => [1 + j, 0, 0],
            CW180 => [1 + i, 0, j],
            _ => [1, 0, 0],
        };
        let rot2 = rotated(rot2, rotation);
        // BlockPos.rotate(rotation).
        let zp = match rotation {
            CW90 => [-zp[2], zp[1], zp[0]],
            CW180 => [-zp[0], zp[1], -zp[2]],
            CCW90 => [zp[2], zp[1], -zp[0]],
            _ => zp,
        };
        self.add0(name, [pos[0] + zp[0], pos[1], pos[2] + zp[2]], rot2);
    }

    fn add_room_1x2(&mut self, pos: [i32; 3], rotation: i32, direction: Option<i32>, d2: i32, floor: usize, stairs: bool) {
        let Some(d) = direction else { return };
        let e = rot_dir(rotation, EAST);
        let s = rot_dir(rotation, SOUTH);
        let w = rot_dir(rotation, WEST);
        let n = rot_dir(rotation, NORTH);
        match (d2, d) {
            (EAST, SOUTH) => {
                let t = self.get_1x2_side(floor, stairs);
                self.add(t, rel(pos, e, 1), rotation, 0);
            }
            (EAST, NORTH) => {
                let t = self.get_1x2_side(floor, stairs);
                self.add(t, rel(rel(pos, e, 1), s, 6), rotation, LEFT_RIGHT);
            }
            (WEST, NORTH) => {
                let t = self.get_1x2_side(floor, stairs);
                self.add(t, rel(rel(pos, e, 7), s, 6), rotated(rotation, CW180), 0);
            }
            (WEST, SOUTH) => {
                let t = self.get_1x2_side(floor, stairs);
                self.add(t, rel(pos, e, 7), rotation, FRONT_BACK);
            }
            (SOUTH, EAST) => {
                let t = self.get_1x2_side(floor, stairs);
                self.add(t, rel(pos, e, 1), rotated(rotation, CW90), LEFT_RIGHT);
            }
            (SOUTH, WEST) => {
                let t = self.get_1x2_side(floor, stairs);
                self.add(t, rel(pos, e, 7), rotated(rotation, CW90), 0);
            }
            (NORTH, WEST) => {
                let t = self.get_1x2_side(floor, stairs);
                self.add(t, rel(rel(pos, e, 7), s, 6), rotated(rotation, CW90), FRONT_BACK);
            }
            (NORTH, EAST) => {
                let t = self.get_1x2_side(floor, stairs);
                self.add(t, rel(rel(pos, e, 1), s, 6), rotated(rotation, CCW90), 0);
            }
            (SOUTH, NORTH) => {
                let t = self.get_1x2_front(floor, stairs);
                self.add(t, rel(rel(pos, e, 1), n, 8), rotation, 0);
            }
            (NORTH, SOUTH) => {
                let t = self.get_1x2_front(floor, stairs);
                self.add(t, rel(rel(pos, e, 7), s, 14), rotated(rotation, CW180), 0);
            }
            (WEST, EAST) => {
                let t = self.get_1x2_front(floor, stairs);
                self.add(t, rel(pos, e, 15), rotated(rotation, CW90), 0);
            }
            (EAST, WEST) => {
                let t = self.get_1x2_front(floor, stairs);
                self.add(t, rel(rel(pos, w, 7), s, 6), rotated(rotation, CCW90), 0);
            }
            (UP, EAST) => {
                let t = self.get_1x2_secret(floor);
                self.add(t, rel(pos, e, 15), rotated(rotation, CW90), 0);
            }
            (UP, SOUTH) => {
                let t = self.get_1x2_secret(floor);
                self.add(t, rel(rel(pos, e, 1), n, 0), rotation, 0);
            }
            _ => {}
        }
    }

    fn add_room_2x2(&mut self, pos: [i32; 3], rotation: i32, d: i32, d2: i32, floor: usize) {
        let (mut i, mut j, mut rot2, mut mirror) = (0, 0, rotation, 0);
        match (d2, d) {
            (EAST, SOUTH) => i = -7,
            (EAST, NORTH) => {
                i = -7;
                j = 6;
                mirror = LEFT_RIGHT;
            }
            (NORTH, EAST) => {
                i = 1;
                j = 14;
                rot2 = rotated(rotation, CCW90);
            }
            (NORTH, WEST) => {
                i = 7;
                j = 14;
                rot2 = rotated(rotation, CCW90);
                mirror = LEFT_RIGHT;
            }
            (SOUTH, WEST) => {
                i = 7;
                j = -8;
                rot2 = rotated(rotation, CW90);
            }
            (SOUTH, EAST) => {
                i = 1;
                j = -8;
                rot2 = rotated(rotation, CW90);
                mirror = LEFT_RIGHT;
            }
            (WEST, NORTH) => {
                i = 15;
                j = 6;
                rot2 = rotated(rotation, CW180);
            }
            (WEST, SOUTH) => {
                i = 15;
                mirror = FRONT_BACK;
            }
            _ => {}
        }
        let p2 = rel(rel(pos, rot_dir(rotation, EAST), i), rot_dir(rotation, SOUTH), j);
        let t = self.get_2x2(floor);
        self.add(t, p2, rot2, mirror);
    }
}

/// `StructureTemplate.transform(pos, mirror, rotation, BlockPos.ZERO)`.
fn transform(p: [i32; 3], mirror: i32, rotation: i32) -> [i32; 3] {
    let [mut i, j, mut k] = p;
    match mirror {
        LEFT_RIGHT => k = -k,
        FRONT_BACK => i = -i,
        _ => {}
    }
    match rotation {
        CCW90 => [k, j, -i],
        CW90 => [-k, j, i],
        CW180 => [-i, j, -k],
        _ => [i, j, k],
    }
}

/// Plans the mansion at `stub` (findGenerationPoint's lowest-Y position).
pub(crate) fn plan(random: &mut Legacy, _chunk: [i32; 2], stub: [i32; 3], params: &[i32]) -> Result<Vec<i32>, ()> {
    if params.first() != Some(&(NAMES.len() as i32)) || params.len() != 1 + NAMES.len() * 3 {
        return Err(());
    }
    // findGenerationPoint: Rotation.getRandom.
    let rotation = random.next_int_bound(4);
    let grid = MansionGrid::new(random)?;
    let mut placer = Placer { random, sizes: &params[1..], start_x: 0, start_y: 0, out: Vec::new() };
    placer.create_mansion(stub, rotation, &grid)?;
    Ok(placer.out)
}

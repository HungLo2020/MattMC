//! `OceanMonumentStructure.generatePieces`: the facing draw, then the
//! `MonumentBuilding` constructor (`generateRoomGraph`, the room fitters, the
//! wing rooms and the penthouse) with the generation context's random. Java
//! rebuilds the building from the plan (`OceanMonumentPieces.addNativePieces`).
//!
//! One piece: kind 50, gen depth 0, the building box, its orientation, then
//! fields:
//! `[room count, per room (index, opening bits, claimed, isSource, scanIndex,
//! six connection slots or -1), source slot, core slot, child count, per child
//! (kind, gen depth, box, orientation, room slot or -1, main design)]`.
//! Child kinds follow `MonumentBuilding.nativeKind` (0 entry .. 10 penthouse).

use crate::world::level::levelgen::random::Legacy;

pub(crate) const BUILDING: i32 = 50;

/// `Direction.get2DDataValue()`.
const SOUTH2: i32 = 0;
const WEST2: i32 = 1;
const NORTH2: i32 = 2;
const EAST2: i32 = 3;

/// `Direction.get3DDataValue()` (also `Direction.values()` order).
const DOWN: usize = 0;
const UP: usize = 1;
const NORTH: usize = 2;
const SOUTH: usize = 3;
const WEST: usize = 4;
const EAST: usize = 5;
/// Steps of `Direction.values()`: x, y, z.
const STEPS: [[i32; 3]; 6] = [[0, -1, 0], [0, 1, 0], [0, 0, -1], [0, 0, 1], [-1, 0, 0], [1, 0, 0]];
const OPPOSITE: [usize; 6] = [UP, DOWN, SOUTH, NORTH, EAST, WEST];

const ENTRY: i32 = 0;
const CORE: i32 = 1;
const DOUBLE_X: i32 = 2;
const DOUBLE_XY: i32 = 3;
const DOUBLE_Y: i32 = 4;
const DOUBLE_YZ: i32 = 5;
const DOUBLE_Z: i32 = 6;
const SIMPLE: i32 = 7;
const SIMPLE_TOP: i32 = 8;
const WING: i32 = 9;
const PENTHOUSE: i32 = 10;

#[derive(Clone)]
struct Room {
    index: i32,
    conn: [Option<usize>; 6],
    open: [bool; 6],
    claimed: bool,
    is_source: bool,
    scan_index: i32,
}

impl Room {
    fn new(index: i32) -> Self {
        Room { index, conn: [None; 6], open: [false; 6], claimed: false, is_source: false, scan_index: 0 }
    }
}

struct Graph {
    rooms: Vec<Room>,
}

impl Graph {
    fn add(&mut self, index: i32) -> usize {
        self.rooms.push(Room::new(index));
        self.rooms.len() - 1
    }

    fn set_connection(&mut self, a: usize, d: usize, b: usize) {
        self.rooms[a].conn[d] = Some(b);
        self.rooms[b].conn[OPPOSITE[d]] = Some(a);
    }

    fn update_openings(&mut self, a: usize) {
        for d in 0..6 {
            self.rooms[a].open[d] = self.rooms[a].conn[d].is_some();
        }
    }

    fn find_source(&mut self, a: usize, i: i32) -> bool {
        if self.rooms[a].is_source {
            return true;
        }
        self.rooms[a].scan_index = i;
        for j in 0..6 {
            if let Some(n) = self.rooms[a].conn[j] {
                if self.rooms[a].open[j] && self.rooms[n].scan_index != i && self.find_source(n, i) {
                    return true;
                }
            }
        }
        false
    }

    fn to(&self, a: usize, d: usize) -> Result<usize, ()> {
        self.rooms[a].conn[d].ok_or(())
    }

    /// `hasOpening[d] && !connections[d].claimed`.
    fn free(&self, a: usize, d: usize) -> Result<bool, ()> {
        if !self.rooms[a].open[d] {
            return Ok(false);
        }
        Ok(!self.rooms[self.to(a, d)?].claimed)
    }
}

fn room_index(x: i32, y: i32, z: i32) -> i32 {
    y * 25 + z * 5 + x
}

struct Child {
    kind: i32,
    bb: [i32; 6],
    room: i32,
    main_design: i32,
}

fn moved(b: [i32; 6], x: i32, y: i32, z: i32) -> [i32; 6] {
    [b[0] + x, b[1] + y, b[2] + z, b[3] + x, b[4] + y, b[5] + z]
}

fn from_corners(a: [i32; 3], b: [i32; 3]) -> [i32; 6] {
    [a[0].min(b[0]), a[1].min(b[1]), a[2].min(b[2]), a[0].max(b[0]), a[1].max(b[1]), a[2].max(b[2])]
}

/// `OceanMonumentPiece.makeBoundingBox(direction, room, i, j, k)` (before the
/// building's move).
fn room_box(direction: i32, index: i32, i: i32, j: i32, k: i32) -> [i32; 6] {
    let m = index % 5;
    let n = index / 5 % 5;
    let o = index / 25;
    let (sx, sz) = if direction == NORTH2 || direction == SOUTH2 { (i * 8, k * 8) } else { (k * 8, i * 8) };
    let b = [0, 0, 0, sx - 1, j * 4 - 1, sz - 1];
    match direction {
        NORTH2 => moved(b, m * 8, o * 4, -(n + k) * 8 + 1),
        SOUTH2 => moved(b, m * 8, o * 4, n * 8),
        WEST2 => moved(b, -(n + k) * 8 + 1, o * 4, m * 8),
        _ => moved(b, n * 8, o * 4, m * 8),
    }
}

/// `StructurePiece.getWorldPos` for the building box.
fn world_pos(bb: &[i32; 6], direction: i32, i: i32, j: i32, k: i32) -> [i32; 3] {
    let x = match direction {
        NORTH2 | SOUTH2 => bb[0] + i,
        WEST2 => bb[3] - k,
        _ => bb[0] + k,
    };
    let z = match direction {
        NORTH2 => bb[5] - k,
        SOUTH2 => bb[2] + k,
        _ => bb[2] + i,
    };
    [x, bb[1] + j, z]
}

pub(crate) fn plan(random: &mut Legacy, chunk: [i32; 2], _stub: [i32; 3], _params: &[i32]) -> Result<Vec<i32>, ()> {
    // createTopPiece: the building at (chunk * 16 - 29) with the drawn direction.
    plan_at(random, chunk[0] * 16 - 29, chunk[1] * 16 - 29, None)
}

/// `new MonumentBuilding(random, x0, z0, direction)` after the caller's
/// `Direction.Plane.HORIZONTAL.getRandomDirection(random)`: that draw is always
/// consumed, then `orientation` (a 2D data value) replaces it when given, as
/// `regeneratePiecesAfterLoad` keeps a saved building's orientation.
pub(crate) fn plan_at(random: &mut Legacy, x0: i32, z0: i32, orientation: Option<i32>) -> Result<Vec<i32>, ()> {
    // Direction.Plane.HORIZONTAL [N, E, S, W].
    let drawn = [NORTH2, EAST2, SOUTH2, WEST2][random.next_int_bound(4) as usize];
    let direction = match orientation {
        Some(value) if (0..4).contains(&value) => value,
        Some(_) => return Err(()),
        None => drawn,
    };
    let bb = [x0, 39, z0, x0 + 57, 39 + 22, z0 + 57];

    // generateRoomGraph.
    let mut g = Graph { rooms: Vec::with_capacity(49) };
    let mut grid: [Option<usize>; 75] = [None; 75];
    for (xs, ys, zs) in [(0..5, 0, 0..4), (0..5, 1, 0..4), (1..4, 2, 0..2)] {
        for i in xs {
            for j in zs.clone() {
                let l = room_index(i, ys, j);
                grid[l as usize] = Some(g.add(l));
            }
        }
    }
    let source = grid[room_index(2, 0, 0) as usize].ok_or(())?;
    for i in 0..5 {
        for j in 0..5 {
            for k in 0..3 {
                let Some(a) = grid[room_index(i, k, j) as usize] else { continue };
                for (d, s) in STEPS.iter().enumerate() {
                    let (m, n, o) = (i + s[0], k + s[1], j + s[2]);
                    if (0..5).contains(&m) && (0..5).contains(&o) && (0..3).contains(&n) {
                        if let Some(b) = grid[room_index(m, n, o) as usize] {
                            let dir = if o == j { d } else { OPPOSITE[d] };
                            g.set_connection(a, dir, b);
                        }
                    }
                }
            }
        }
    }
    let penthouse = g.add(1003);
    let left = g.add(1001);
    let right = g.add(1002);
    g.set_connection(grid[room_index(2, 2, 0) as usize].ok_or(())?, UP, penthouse);
    g.set_connection(grid[room_index(0, 1, 0) as usize].ok_or(())?, SOUTH, left);
    g.set_connection(grid[room_index(4, 1, 0) as usize].ok_or(())?, SOUTH, right);
    for r in [penthouse, left, right] {
        g.rooms[r].claimed = true;
    }
    g.rooms[source].is_source = true;
    let core = grid[room_index(random.next_int_bound(4), 0, 2) as usize].ok_or(())?;
    let e = g.to(core, EAST)?;
    let nn = g.to(core, NORTH)?;
    let en = g.to(e, NORTH)?;
    let u = g.to(core, UP)?;
    let eu = g.to(e, UP)?;
    let nu = g.to(nn, UP)?;
    let enu = g.to(en, UP)?;
    for r in [core, e, nn, en, u, eu, nu, enu] {
        g.rooms[r].claimed = true;
    }
    let mut list: Vec<usize> = Vec::with_capacity(49);
    for slot in grid.iter().flatten() {
        g.update_openings(*slot);
        list.push(*slot);
    }
    g.update_openings(penthouse);
    // Util.shuffle.
    for j in (2..=list.len()).rev() {
        let k = random.next_int_bound(j as i32) as usize;
        list.swap(j - 1, k);
    }
    let mut q = 1;
    for &a in &list {
        let mut r = 0;
        let mut m = 0;
        while r < 2 && m < 5 {
            m += 1;
            let n = random.next_int_bound(6) as usize;
            if g.rooms[a].open[n] {
                let o = OPPOSITE[n];
                let b = g.to(a, n)?;
                g.rooms[a].open[n] = false;
                g.rooms[b].open[o] = false;
                let q1 = q;
                q += 1;
                let ok = g.find_source(a, q1) && {
                    let q2 = q;
                    q += 1;
                    g.find_source(b, q2)
                };
                if ok {
                    r += 1;
                } else {
                    g.rooms[a].open[n] = true;
                    g.rooms[b].open[o] = true;
                }
            }
        }
    }
    list.push(penthouse);
    list.push(left);
    list.push(right);

    // The constructor's children.
    let mut children: Vec<Child> = Vec::new();
    let room_child = |g: &Graph, kind: i32, room: usize, size: [i32; 3], main_design: i32| Child {
        kind,
        bb: room_box(direction, g.rooms[room].index, size[0], size[1], size[2]),
        room: room as i32,
        main_design,
    };
    g.rooms[source].claimed = true;
    children.push(room_child(&g, ENTRY, source, [1, 1, 1], 0));
    children.push(room_child(&g, CORE, core, [2, 2, 2], 0));
    for &a in &list {
        if g.rooms[a].claimed || g.rooms[a].index >= 75 {
            continue;
        }
        // FitDoubleXYRoom.
        if g.free(a, EAST)? && g.free(a, UP)? && g.free(g.to(a, EAST)?, UP)? {
            let e = g.to(a, EAST)?;
            let u = g.to(a, UP)?;
            let eu = g.to(e, UP)?;
            for r in [a, e, u, eu] {
                g.rooms[r].claimed = true;
            }
            children.push(room_child(&g, DOUBLE_XY, a, [2, 2, 1], 0));
        } else if g.free(a, NORTH)? && g.free(a, UP)? && g.free(g.to(a, NORTH)?, UP)? {
            // FitDoubleYZRoom.
            let n = g.to(a, NORTH)?;
            let u = g.to(a, UP)?;
            let nu = g.to(n, UP)?;
            for r in [a, n, u, nu] {
                g.rooms[r].claimed = true;
            }
            children.push(room_child(&g, DOUBLE_YZ, a, [1, 2, 2], 0));
        } else if g.free(a, NORTH)? {
            // FitDoubleZRoom (its fallback to the south room never applies
            // once it fits, but mirror it).
            let r2 = if !g.free(a, NORTH)? { g.to(a, SOUTH)? } else { a };
            let n = g.to(r2, NORTH)?;
            g.rooms[r2].claimed = true;
            g.rooms[n].claimed = true;
            children.push(room_child(&g, DOUBLE_Z, r2, [1, 1, 2], 0));
        } else if g.free(a, EAST)? {
            // FitDoubleXRoom.
            let e = g.to(a, EAST)?;
            g.rooms[a].claimed = true;
            g.rooms[e].claimed = true;
            children.push(room_child(&g, DOUBLE_X, a, [2, 1, 1], 0));
        } else if g.free(a, UP)? {
            // FitDoubleYRoom.
            let u = g.to(a, UP)?;
            g.rooms[a].claimed = true;
            g.rooms[u].claimed = true;
            children.push(room_child(&g, DOUBLE_Y, a, [1, 2, 1], 0));
        } else if !g.rooms[a].open[WEST] && !g.rooms[a].open[EAST] && !g.rooms[a].open[NORTH] && !g.rooms[a].open[SOUTH] && !g.rooms[a].open[UP] {
            // FitSimpleTopRoom.
            g.rooms[a].claimed = true;
            children.push(room_child(&g, SIMPLE_TOP, a, [1, 1, 1], 0));
        } else {
            // FitSimpleRoom.
            g.rooms[a].claimed = true;
            let mut c = room_child(&g, SIMPLE, a, [1, 1, 1], 0);
            c.main_design = random.next_int_bound(3);
            children.push(c);
        }
    }
    let p = world_pos(&bb, direction, 9, 0, 22);
    for c in &mut children {
        c.bb = moved(c.bb, p[0], p[1], p[2]);
    }
    let wing1 = from_corners(world_pos(&bb, direction, 1, 1, 1), world_pos(&bb, direction, 23, 8, 21));
    let wing2 = from_corners(world_pos(&bb, direction, 34, 1, 1), world_pos(&bb, direction, 56, 8, 21));
    let top = from_corners(world_pos(&bb, direction, 22, 13, 22), world_pos(&bb, direction, 35, 17, 35));
    let k = random.next(32);
    children.push(Child { kind: WING, bb: wing1, room: -1, main_design: k & 1 });
    children.push(Child { kind: WING, bb: wing2, room: -1, main_design: k.wrapping_add(1) & 1 });
    children.push(Child { kind: PENTHOUSE, bb: top, room: -1, main_design: 0 });

    let mut fields: Vec<i32> = Vec::with_capacity(4 + g.rooms.len() * 11 + children.len() * 11);
    fields.push(g.rooms.len() as i32);
    for r in &g.rooms {
        let open = (0..6).filter(|&d| r.open[d]).fold(0, |acc, d| acc | (1 << d));
        fields.extend([r.index, open, i32::from(r.claimed), i32::from(r.is_source), r.scan_index]);
        fields.extend(r.conn.iter().map(|c| c.map_or(-1, |s| s as i32)));
    }
    fields.push(source as i32);
    fields.push(core as i32);
    fields.push(children.len() as i32);
    for c in &children {
        fields.extend([c.kind, 1]);
        fields.extend(c.bb);
        fields.extend([direction, c.room, c.main_design]);
    }

    let mut out = Vec::with_capacity(10 + fields.len());
    out.extend([BUILDING, 0]);
    out.extend(bb);
    out.extend([direction, fields.len() as i32]);
    out.extend(fields);
    Ok(out)
}

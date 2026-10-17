//! `CoralFeature` and its claw, tree and mushroom shapes.
use super::placement::Placement;
use super::program::Reader;
use super::region::Result;

/// `Direction.Plane.HORIZONTAL` faces: north, east, south, west (clockwise).
const HORIZONTAL: [[i32; 3]; 4] = [[0, 0, -1], [1, 0, 0], [0, 0, 1], [-1, 0, 0]];
const UP: [i32; 3] = [0, 1, 0];

#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) enum Shape {
    Claw,
    Tree,
    Mushroom,
}

#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Coral {
    shape: Shape,
    /// `#coral_blocks` default states in tag order.
    blocks: Vec<u32>,
    /// `#corals` default states in tag order.
    corals: Vec<u32>,
    /// `#wall_corals` in tag order: facing north, east, south, west.
    wall_corals: Vec<[u32; 4]>,
    /// Sea pickles with one to four pickles.
    pickles: [u32; 4],
    water: usize,
    coral_tag: usize,
}

fn add(pos: [i32; 3], by: [i32; 3]) -> [i32; 3] {
    [pos[0] + by[0], pos[1] + by[1], pos[2] + by[2]]
}

fn neg(by: [i32; 3]) -> [i32; 3] {
    [-by[0], -by[1], -by[2]]
}

/// `Util.shuffle` over direction indices.
fn shuffle(placement: &mut Placement, items: &mut [usize]) {
    for j in (2..=items.len()).rev() {
        let k = placement.random.next_int(j as i32) as usize;
        items.swap(j - 1, k);
    }
}

impl Coral {
    pub(crate) fn read(shape: Shape, reader: &mut Reader) -> Option<Self> {
        let list = |reader: &mut Reader| -> Option<Vec<u32>> {
            let count = reader.count(256)?;
            (0..count).map(|_| reader.write_state()).collect()
        };
        let blocks = list(reader)?;
        let corals = list(reader)?;
        let walls = reader.count(256)?;
        let wall_corals = (0..walls)
            .map(|_| Some([reader.write_state()?, reader.write_state()?, reader.write_state()?, reader.write_state()?]))
            .collect::<Option<Vec<_>>>()?;
        Some(Self {
            shape,
            blocks,
            corals,
            wall_corals,
            pickles: [reader.write_state()?, reader.write_state()?, reader.write_state()?, reader.write_state()?],
            water: reader.set()?,
            coral_tag: reader.set()?,
        })
    }

    pub(crate) fn kind(&self) -> i32 {
        match self.shape {
            Shape::Claw => 25,
            Shape::Tree => 26,
            Shape::Mushroom => 27,
        }
    }

    fn in_set(placement: &Placement, set: usize, pos: [i32; 3]) -> Result<bool> {
        Ok(placement.program.in_set(set, placement.region.level_block_state(pos[0], pos[1], pos[2])?))
    }

    /// `Registry.getRandomElementOf`: `nextInt(size)`, drawing nothing for an empty tag.
    fn pick<T: Copy>(placement: &mut Placement, items: &[T]) -> Option<T> {
        (!items.is_empty()).then(|| items[placement.random.next_int(items.len() as i32) as usize])
    }

    /// `CoralFeature.placeCoralBlock`.
    fn place_block(&self, placement: &mut Placement, pos: [i32; 3], block: u32) -> Result<bool> {
        let above = add(pos, UP);
        let here = placement.region.level_block_state(pos[0], pos[1], pos[2])?;
        if !(placement.program.in_set(self.water, here) || placement.program.in_set(self.coral_tag, here)) || !Self::in_set(placement, self.water, above)? {
            return Ok(false);
        }
        placement.region.set_block(pos[0], pos[1], pos[2], block, 3)?;
        if placement.random.next_float() < 0.25f32 {
            if let Some(coral) = Self::pick(placement, &self.corals) {
                placement.region.set_block(above[0], above[1], above[2], coral, 2)?;
            }
        } else if placement.random.next_float() < 0.05f32 {
            let pickle = self.pickles[placement.random.next_int(4) as usize];
            placement.region.set_block(above[0], above[1], above[2], pickle, 2)?;
        }
        for (direction, by) in HORIZONTAL.iter().enumerate() {
            if placement.random.next_float() < 0.2f32 {
                let side = add(pos, *by);
                if Self::in_set(placement, self.water, side)? {
                    if let Some(wall) = Self::pick(placement, &self.wall_corals) {
                        placement.region.set_block(side[0], side[1], side[2], wall[direction], 2)?;
                    }
                }
            }
        }
        Ok(true)
    }

    pub(crate) fn place(&self, placement: &mut Placement, origin: [i32; 3]) -> Result<bool> {
        let Some(block) = Self::pick(placement, &self.blocks) else {
            return Ok(false);
        };
        match self.shape {
            Shape::Claw => self.claw(placement, origin, block),
            Shape::Tree => self.tree(placement, origin, block),
            Shape::Mushroom => self.mushroom(placement, origin, block),
        }
    }

    fn claw(&self, placement: &mut Placement, origin: [i32; 3], block: u32) -> Result<bool> {
        if !self.place_block(placement, origin, block)? {
            return Ok(false);
        }
        let direction = placement.random.next_int(4) as usize;
        let i = placement.random.next_int(2) + 2;
        // `toShuffledList(Stream.of(direction, clockwise, counterclockwise))`.
        let mut list = [direction, (direction + 1) % 4, (direction + 3) % 4];
        shuffle(placement, &mut list);
        for &branch in &list[..i as usize] {
            let mut pos = origin;
            let j = placement.random.next_int(2) + 1;
            pos = add(pos, HORIZONTAL[branch]);
            let (step, k);
            if branch == direction {
                step = HORIZONTAL[direction];
                k = placement.random.next_int(3) + 2;
            } else {
                pos = add(pos, UP);
                let choices = [HORIZONTAL[branch], UP];
                step = choices[placement.random.next_int(2) as usize];
                k = placement.random.next_int(3) + 3;
            }
            let mut l = 0;
            while l < j && self.place_block(placement, pos, block)? {
                pos = add(pos, step);
                l += 1;
            }
            pos = add(add(pos, neg(step)), UP);
            for _ in 0..k {
                pos = add(pos, HORIZONTAL[direction]);
                if !self.place_block(placement, pos, block)? {
                    break;
                }
                if placement.random.next_float() < 0.25f32 {
                    pos = add(pos, UP);
                }
            }
        }
        Ok(true)
    }

    fn tree(&self, placement: &mut Placement, origin: [i32; 3], block: u32) -> Result<bool> {
        let mut pos = origin;
        let i = placement.random.next_int(3) + 1;
        for _ in 0..i {
            if !self.place_block(placement, pos, block)? {
                return Ok(true);
            }
            pos = add(pos, UP);
        }
        let top = pos;
        let k = placement.random.next_int(3) + 2;
        let mut list = [0, 1, 2, 3];
        shuffle(placement, &mut list);
        for &direction in &list[..k as usize] {
            let mut pos = add(top, HORIZONTAL[direction]);
            let l = placement.random.next_int(5) + 2;
            let mut m = 0;
            let mut n = 0;
            while n < l && self.place_block(placement, pos, block)? {
                m += 1;
                pos = add(pos, UP);
                if n == 0 || m >= 2 && placement.random.next_float() < 0.25f32 {
                    pos = add(pos, HORIZONTAL[direction]);
                    m = 0;
                }
                n += 1;
            }
        }
        Ok(true)
    }

    fn mushroom(&self, placement: &mut Placement, origin: [i32; 3], block: u32) -> Result<bool> {
        let i = placement.random.next_int(3) + 3;
        let j = placement.random.next_int(3) + 3;
        let k = placement.random.next_int(3) + 3;
        let l = placement.random.next_int(3) + 1;
        for m in 0..=j {
            for n in 0..=i {
                for o in 0..=k {
                    let pos = [m + origin[0], n + origin[1] - l, o + origin[2]];
                    let mx = m != 0 && m != j;
                    let ny = n != 0 && n != i;
                    let oz = o != 0 && o != k;
                    if (mx || ny) && (oz || ny) && (mx || oz) && (!mx || !ny || !oz) && !(placement.random.next_float() < 0.1f32) {
                        self.place_block(placement, pos, block)?;
                    }
                }
            }
        }
        Ok(true)
    }
}

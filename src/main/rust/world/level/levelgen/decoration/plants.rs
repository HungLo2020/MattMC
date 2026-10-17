//! `BambooFeature`, the huge mushrooms, `HugeFungusFeature` and
//! `ChorusPlantFeature`.
use super::placement::Placement;
use super::program::{Predicate, Reader};
use super::region::Result;
use super::tree::Provider;
use super::vegetation::{in_set, is_air, offset, set_block};

/// `Heightmap.Types.WORLD_SURFACE`.
const WORLD_SURFACE: u32 = 1;

/// `BambooFeature`.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Bamboo {
    probability: f32,
    survives: Predicate,
    dirt: usize,
    podzol: u32,
    trunk: u32,
    final_large: u32,
    top_large: u32,
    top_small: u32,
}

impl Bamboo {
    pub(crate) fn read(reader: &mut Reader) -> Option<Self> {
        Some(Self {
            probability: reader.float()?,
            survives: Predicate::read_checked(reader)?,
            dirt: reader.set()?,
            podzol: reader.write_state()?,
            trunk: reader.write_state()?,
            final_large: reader.write_state()?,
            top_large: reader.write_state()?,
            top_small: reader.write_state()?,
        })
    }

    pub(crate) fn place(&self, placement: &mut Placement, origin: [i32; 3]) -> Result<bool> {
        if !is_air(placement, origin)? {
            return Ok(false);
        }
        if placement.test(&self.survives, origin)? {
            let j = placement.random.next_int(12) + 5;
            if placement.random.next_float() < self.probability {
                let k = placement.random.next_int(4) + 1;
                for x in origin[0] - k..=origin[0] + k {
                    for z in origin[2] - k..=origin[2] + k {
                        let (n, o) = (x - origin[0], z - origin[2]);
                        if n * n + o * o <= k * k {
                            let pos = [x, placement.region.height_by_type(WORLD_SURFACE, x, z)? - 1, z];
                            if in_set(placement, self.dirt, pos)? {
                                set_block(placement, pos, self.podzol, 2)?;
                            }
                        }
                    }
                }
            }
            let mut pos = origin;
            let mut placed = 0;
            while placed < j && is_air(placement, pos)? {
                set_block(placement, pos, self.trunk, 2)?;
                pos[1] += 1;
                placed += 1;
            }
            if pos[1] - origin[1] >= 3 {
                set_block(placement, pos, self.final_large, 2)?;
                pos[1] -= 1;
                set_block(placement, pos, self.top_large, 2)?;
                pos[1] -= 1;
                set_block(placement, pos, self.top_small, 2)?;
            }
        }
        Ok(true)
    }
}

/// `HugeRedMushroomFeature` and `HugeBrownMushroomFeature`.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct HugeMushroom {
    red: bool,
    cap: Provider,
    stem: Provider,
    foliage_radius: i32,
    /// Per cap state with the face properties: its states by face bits
    /// (west 1, east 2, north 4, south 8, up 16; brown caps ignore up).
    faces: Vec<(u32, Vec<u32>)>,
    /// Dirt or `mushroom_grow_block`.
    grows_on: usize,
    /// Air or leaves.
    clear: usize,
    /// Air or `replaceable_by_mushrooms`.
    replaceable: usize,
}

impl HugeMushroom {
    pub(crate) fn read(red: bool, reader: &mut Reader) -> Option<Self> {
        let provider = |reader: &mut Reader| {
            let provider = Provider::read(reader)?;
            let mut states = Vec::new();
            provider.states(&mut states);
            reader.record_writes(states);
            Some(provider)
        };
        let cap = provider(reader)?;
        let stem = provider(reader)?;
        let foliage_radius = reader.next()?;
        let count = reader.count(256)?;
        let mut faces = Vec::with_capacity(count);
        for _ in 0..count {
            let base = reader.state()?;
            let states = (0..32).map(|_| reader.write_state()).collect::<Option<Vec<_>>>()?;
            faces.push((base, states));
        }
        Some(Self {
            red,
            cap,
            stem,
            foliage_radius,
            faces,
            grows_on: reader.set()?,
            clear: reader.set()?,
            replaceable: reader.set()?,
        })
    }

    pub(crate) fn kind(&self) -> i32 {
        if self.red { 36 } else { 37 }
    }

    fn with_faces(&self, state: u32, bits: usize) -> u32 {
        match self.faces.iter().find(|(base, _)| *base == state) {
            Some((_, states)) => states[bits],
            None => state,
        }
    }

    /// `placeMushroomBlock`: `setBlock(…, 3)` over air or replaceable blocks.
    fn put(&self, placement: &mut Placement, pos: [i32; 3], state: u32) -> Result<()> {
        if in_set(placement, self.replaceable, pos)? {
            set_block(placement, pos, state, 3)?;
        }
        Ok(())
    }

    pub(crate) fn place(&self, placement: &mut Placement, origin: [i32; 3]) -> Result<bool> {
        let mut height = placement.random.next_int(3) + 4;
        if placement.random.next_int(12) == 0 {
            height *= 2;
        }
        if !self.valid(placement, origin, height)? {
            return Ok(false);
        }
        let r = self.foliage_radius;
        if self.red {
            let inner = r - 2;
            for j in height - 3..=height {
                let k = if j < height { r } else { r - 1 };
                for m in -k..=k {
                    for n in -k..=k {
                        let x_edge = m == -k || m == k;
                        let z_edge = n == -k || n == k;
                        if j >= height || x_edge != z_edge {
                            let state = self.cap.state(placement);
                            let bits = (m < -inner) as usize
                                | ((m > inner) as usize) << 1
                                | ((n < -inner) as usize) << 2
                                | ((n > inner) as usize) << 3
                                | ((j >= height - 1) as usize) << 4;
                            let state = self.with_faces(state, bits);
                            self.put(placement, offset(origin, [m, j, n]), state)?;
                        }
                    }
                }
            }
        } else {
            for k in -r..=r {
                for l in -r..=r {
                    let (west, east, north, south) = (k == -r, k == r, l == -r, l == r);
                    let x_edge = west || east;
                    let z_edge = north || south;
                    if !x_edge || !z_edge {
                        let bits = (west || z_edge && k == 1 - r) as usize
                            | ((east || z_edge && k == r - 1) as usize) << 1
                            | ((north || x_edge && l == 1 - r) as usize) << 2
                            | ((south || x_edge && l == r - 1) as usize) << 3;
                        let state = self.cap.state(placement);
                        let state = self.with_faces(state, bits);
                        self.put(placement, offset(origin, [k, height, l]), state)?;
                    }
                }
            }
        }
        for j in 0..height {
            let state = self.stem.state(placement);
            self.put(placement, offset(origin, [0, j, 0]), state)?;
        }
        Ok(true)
    }

    /// `isValidPosition`, whose radius call passes −1 for the tree height:
    /// red mushrooms check only their stem column.
    fn valid(&self, placement: &mut Placement, origin: [i32; 3], height: i32) -> Result<bool> {
        let region = &placement.region;
        let max_y = region.min_y + region.sections as i32 * 16 - 1;
        let y = origin[1];
        if y < region.min_y + 1 || y + height + 1 > max_y {
            return Ok(false);
        }
        if !in_set(placement, self.grows_on, offset(origin, [0, -1, 0]))? {
            return Ok(false);
        }
        for k in 0..=height {
            let l = if self.red || k <= 3 { 0 } else { self.foliage_radius };
            for m in -l..=l {
                for n in -l..=l {
                    if !in_set(placement, self.clear, offset(origin, [m, k, n]))? {
                        return Ok(false);
                    }
                }
            }
        }
        Ok(true)
    }
}

/// `HugeFungusFeature` with `planted` false.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct HugeFungus {
    /// The valid base state's block.
    base: usize,
    stem: u32,
    hat: u32,
    decor: u32,
    /// `replaceableBlocks`.
    replaceable: Predicate,
    /// `BlockStateBase.canBeReplaced()`.
    can_be_replaced: usize,
    /// The hat state's block, for `placeHatDropBlock`.
    hat_block: usize,
    /// `hatState.is(NETHER_WART_BLOCK)`: hats grow weeping vines.
    wart: bool,
    gen_depth: i32,
    air: u32,
    /// Weeping vines with ages 23 to 25, then the plant.
    vines: [u32; 3],
    vines_plant: u32,
}

impl HugeFungus {
    pub(crate) fn read(reader: &mut Reader) -> Option<Self> {
        Some(Self {
            base: reader.set()?,
            stem: reader.write_state()?,
            hat: reader.write_state()?,
            decor: reader.write_state()?,
            replaceable: Predicate::read_checked(reader)?,
            can_be_replaced: reader.set()?,
            hat_block: reader.set()?,
            wart: reader.flag()?,
            gen_depth: reader.next()?,
            air: reader.write_state()?,
            vines: [reader.write_state()?, reader.write_state()?, reader.write_state()?],
            vines_plant: reader.write_state()?,
        })
    }

    pub(crate) fn place(&self, placement: &mut Placement, origin: [i32; 3]) -> Result<bool> {
        if !in_set(placement, self.base, offset(origin, [0, -1, 0]))? {
            return Ok(false);
        }
        let mut height = placement.random.next_int(10) + 4;
        if placement.random.next_int(12) == 0 {
            height *= 2;
        }
        if origin[1] + height + 1 >= self.gen_depth {
            return Ok(false);
        }
        let huge = placement.random.next_float() < 0.06f32;
        set_block(placement, origin, self.air, 260)?;
        self.stem(placement, origin, height, huge)?;
        self.hat(placement, origin, height, huge)?;
        Ok(true)
    }

    fn stem(&self, placement: &mut Placement, origin: [i32; 3], height: i32, huge: bool) -> Result<()> {
        let j = huge as i32;
        for k in -j..=j {
            for l in -j..=j {
                let corner = huge && k.abs() == j && l.abs() == j;
                for m in 0..height {
                    let pos = offset(origin, [k, m, l]);
                    if in_set(placement, self.can_be_replaced, pos)? || placement.test(&self.replaceable, pos)? {
                        if !corner || placement.random.next_float() < 0.1f32 {
                            set_block(placement, pos, self.stem, 3)?;
                        }
                    }
                }
            }
        }
        Ok(())
    }

    fn hat(&self, placement: &mut Placement, origin: [i32; 3], height: i32, huge: bool) -> Result<()> {
        let i = height;
        let j = (placement.random.next_int(1 + i / 3) + 5).min(i);
        let k = i - j;
        let wart = |chance: f32| if self.wart { chance } else { 0.0 };
        for l in k..=i {
            let mut m = if l < i - placement.random.next_int(3) { 2 } else { 1 };
            if j > 8 && l < k + 4 {
                m = 3;
            }
            if huge {
                m += 1;
            }
            for n in -m..=m {
                for o in -m..=m {
                    let x_edge = n == -m || n == m;
                    let z_edge = o == -m || o == m;
                    let inner = !x_edge && !z_edge && l != i;
                    let corner = x_edge && z_edge;
                    let pos = offset(origin, [n, l, o]);
                    if !in_set(placement, self.can_be_replaced, pos)? {
                        continue;
                    }
                    if l < k + 3 {
                        if !inner {
                            self.drop_block(placement, pos)?;
                        }
                    } else if inner {
                        self.hat_block(placement, pos, 0.1, 0.2, wart(0.1))?;
                    } else if corner {
                        self.hat_block(placement, pos, 0.01, 0.7, wart(0.083))?;
                    } else {
                        self.hat_block(placement, pos, 5.0E-4, 0.98, wart(0.07))?;
                    }
                }
            }
        }
        Ok(())
    }

    fn hat_block(&self, placement: &mut Placement, pos: [i32; 3], decor: f32, hat: f32, vines: f32) -> Result<()> {
        if placement.random.next_float() < decor {
            set_block(placement, pos, self.decor, 3)?;
        } else if placement.random.next_float() < hat {
            set_block(placement, pos, self.hat, 3)?;
            if placement.random.next_float() < vines {
                self.vines(placement, pos)?;
            }
        }
        Ok(())
    }

    fn drop_block(&self, placement: &mut Placement, pos: [i32; 3]) -> Result<()> {
        if in_set(placement, self.hat_block, offset(pos, [0, -1, 0]))? {
            set_block(placement, pos, self.hat, 3)?;
        } else if (placement.random.next_float() as f64) < 0.15 {
            set_block(placement, pos, self.hat, 3)?;
            if self.wart && placement.random.next_int(11) == 0 {
                self.vines(placement, pos)?;
            }
        }
        Ok(())
    }

    /// `tryPlaceWeepingVines` and `WeepingVinesFeature.placeWeepingVinesColumn(…, 23, 25)`.
    fn vines(&self, placement: &mut Placement, pos: [i32; 3]) -> Result<()> {
        let mut at = offset(pos, [0, -1, 0]);
        if !is_air(placement, at)? {
            return Ok(());
        }
        let mut length = placement.random.next_int(5) + 1;
        if placement.random.next_int(7) == 0 {
            length *= 2;
        }
        for l in 0..=length {
            if is_air(placement, at)? {
                if l == length || !is_air(placement, offset(at, [0, -1, 0]))? {
                    let head = self.vines[placement.random.next_int(3) as usize];
                    set_block(placement, at, head, 2)?;
                    break;
                }
                set_block(placement, at, self.vines_plant, 2)?;
            }
            at[1] -= 1;
        }
        Ok(())
    }
}

/// `Direction.Plane.HORIZONTAL`: north, east, south, west.
const HORIZONTAL: [[i32; 3]; 4] = [[0, 0, -1], [1, 0, 0], [0, 0, 1], [-1, 0, 0]];
/// `getStateWithConnections`' reads: down, up, north, east, south, west, with
/// each one's `Direction` ordinal.
const CONNECTIONS: [([i32; 3], u32); 6] =
    [([0, -1, 0], 0), ([0, 1, 0], 1), ([0, 0, -1], 2), ([1, 0, 0], 5), ([0, 0, 1], 3), ([-1, 0, 0], 4)];

/// `ChorusPlantFeature` and `ChorusFlowerBlock.generatePlant(…, 8)`.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Chorus {
    end_stone: usize,
    /// Chorus plant or chorus flower.
    chorus: usize,
    /// The plant's states by connected faces (bits by `Direction` ordinal).
    plants: Vec<u32>,
    flower: u32,
}

impl Chorus {
    pub(crate) fn read(reader: &mut Reader) -> Option<Self> {
        Some(Self {
            end_stone: reader.set()?,
            chorus: reader.set()?,
            plants: (0..64).map(|_| reader.write_state()).collect::<Option<Vec<_>>>()?,
            flower: reader.write_state()?,
        })
    }

    pub(crate) fn place(&self, placement: &mut Placement, origin: [i32; 3]) -> Result<bool> {
        if !is_air(placement, origin)? || !in_set(placement, self.end_stone, offset(origin, [0, -1, 0]))? {
            return Ok(false);
        }
        self.connect(placement, origin)?;
        self.grow(placement, origin, origin, 0)?;
        Ok(true)
    }

    /// `setBlock(pos, getStateWithConnections(level, pos, plant), 2)`.
    fn connect(&self, placement: &mut Placement, pos: [i32; 3]) -> Result<()> {
        let mut bits = 0;
        for (by, ordinal) in CONNECTIONS {
            let neighbour = offset(pos, by);
            if in_set(placement, self.chorus, neighbour)? || ordinal == 0 && in_set(placement, self.end_stone, neighbour)? {
                bits |= 1 << ordinal;
            }
        }
        set_block(placement, pos, self.plants[bits], 2)
    }

    fn neighbours_empty(&self, placement: &Placement, pos: [i32; 3], except: Option<usize>) -> Result<bool> {
        for (index, by) in HORIZONTAL.iter().enumerate() {
            if Some(index) != except && !is_air(placement, offset(pos, *by))? {
                return Ok(false);
            }
        }
        Ok(true)
    }

    /// `growTreeRecursive` with a maximum horizontal distance of 8.
    fn grow(&self, placement: &mut Placement, pos: [i32; 3], root: [i32; 3], depth: i32) -> Result<()> {
        let mut k = placement.random.next_int(4) + 1;
        if depth == 0 {
            k += 1;
        }
        for l in 0..k {
            let at = offset(pos, [0, l + 1, 0]);
            if !self.neighbours_empty(placement, at, None)? {
                return Ok(());
            }
            self.connect(placement, at)?;
            self.connect(placement, offset(at, [0, -1, 0]))?;
        }
        let mut branched = false;
        if depth < 4 {
            let mut m = placement.random.next_int(4);
            if depth == 0 {
                m += 1;
            }
            for _ in 0..m {
                let direction = placement.random.next_int(4) as usize;
                let by = HORIZONTAL[direction];
                let opposite = (direction + 2) % 4;
                let branch = offset(offset(pos, [0, k, 0]), by);
                if (branch[0] - root[0]).abs() < 8
                    && (branch[2] - root[2]).abs() < 8
                    && is_air(placement, branch)?
                    && is_air(placement, offset(branch, [0, -1, 0]))?
                    && self.neighbours_empty(placement, branch, Some(opposite))?
                {
                    branched = true;
                    self.connect(placement, branch)?;
                    self.connect(placement, offset(branch, HORIZONTAL[opposite]))?;
                    self.grow(placement, branch, root, depth + 1)?;
                }
            }
        }
        if !branched {
            set_block(placement, offset(pos, [0, k, 0]), self.flower, 2)?;
        }
        Ok(())
    }
}

/// `Mth.nextInt(random, min, max)`.
fn next_between(placement: &mut Placement, min: i32, max: i32) -> i32 {
    if min >= max { min } else { placement.random.next_int(max - min + 1) + min }
}

/// `WeepingVinesFeature` and `TwistingVinesFeature`.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct NetherVines {
    /// Twisting vines grow up from a floor; weeping vines hang from a roof.
    twisting: Option<[i32; 3]>,
    /// Weeping: netherrack or nether wart block. Twisting: netherrack,
    /// warped nylium or warped wart block.
    support: usize,
    /// Weeping only: the nether wart block.
    wart: u32,
    /// The vine head with ages 17 to 25.
    heads: [u32; 9],
    plant: u32,
}

impl NetherVines {
    pub(crate) fn read(twisting: bool, reader: &mut Reader) -> Option<Self> {
        let spread = if twisting { Some([reader.next()?, reader.next()?, reader.next()?]) } else { None };
        let support = reader.set()?;
        let wart = reader.write_state()?;
        let mut heads = [0; 9];
        for head in heads.iter_mut() {
            *head = reader.write_state()?;
        }
        Some(Self { twisting: spread, support, wart, heads, plant: reader.write_state()? })
    }

    pub(crate) fn kind(&self) -> i32 {
        if self.twisting.is_some() { 45 } else { 44 }
    }

    pub(crate) fn place(&self, placement: &mut Placement, origin: [i32; 3]) -> Result<bool> {
        match self.twisting {
            Some(spread) => self.twisting(placement, origin, spread),
            None => self.weeping(placement, origin),
        }
    }

    fn weeping(&self, placement: &mut Placement, origin: [i32; 3]) -> Result<bool> {
        if !is_air(placement, origin)? || !in_set(placement, self.support, offset(origin, [0, 1, 0]))? {
            return Ok(false);
        }
        set_block(placement, origin, self.wart, 2)?;
        for _ in 0..200 {
            let random = &mut placement.random;
            let dx = random.next_int(6) - random.next_int(6);
            let dy = random.next_int(2) - random.next_int(5);
            let dz = random.next_int(6) - random.next_int(6);
            let pos = offset(origin, [dx, dy, dz]);
            if is_air(placement, pos)? {
                let mut supports = 0;
                for by in super::vegetation::DIRECTIONS {
                    if in_set(placement, self.support, offset(pos, by))? {
                        supports += 1;
                    }
                    if supports > 1 {
                        break;
                    }
                }
                if supports == 1 {
                    set_block(placement, pos, self.wart, 2)?;
                }
            }
        }
        for _ in 0..100 {
            let random = &mut placement.random;
            let dx = random.next_int(8) - random.next_int(8);
            let dy = random.next_int(2) - random.next_int(7);
            let dz = random.next_int(8) - random.next_int(8);
            let mut pos = offset(origin, [dx, dy, dz]);
            if is_air(placement, pos)? && in_set(placement, self.support, offset(pos, [0, 1, 0]))? {
                let mut length = next_between(placement, 1, 8);
                if placement.random.next_int(6) == 0 {
                    length *= 2;
                }
                if placement.random.next_int(5) == 0 {
                    length = 1;
                }
                for l in 0..=length {
                    if is_air(placement, pos)? {
                        if l == length || !is_air(placement, offset(pos, [0, -1, 0]))? {
                            let head = self.heads[placement.random.next_int(9) as usize];
                            set_block(placement, pos, head, 2)?;
                            break;
                        }
                        set_block(placement, pos, self.plant, 2)?;
                    }
                    pos[1] -= 1;
                }
            }
        }
        Ok(true)
    }

    fn invalid(&self, placement: &Placement, pos: [i32; 3]) -> Result<bool> {
        Ok(!is_air(placement, pos)? || !in_set(placement, self.support, offset(pos, [0, -1, 0]))?)
    }

    fn twisting(&self, placement: &mut Placement, origin: [i32; 3], [width, height, max_height]: [i32; 3]) -> Result<bool> {
        if self.invalid(placement, origin)? {
            return Ok(false);
        }
        for _ in 0..width.wrapping_mul(width) {
            let dx = next_between(placement, -width, width);
            let dy = next_between(placement, -height, height);
            let dz = next_between(placement, -width, width);
            let mut pos = offset(origin, [dx, dy, dz]);
            // `findFirstAirBlockAboveGround`.
            let grounded = loop {
                pos[1] -= 1;
                if placement.region.outside_build_height(pos[1]) {
                    break false;
                }
                if !is_air(placement, pos)? {
                    pos[1] += 1;
                    break true;
                }
            };
            if grounded && !self.invalid(placement, pos)? {
                let mut length = next_between(placement, 1, max_height);
                if placement.random.next_int(6) == 0 {
                    length *= 2;
                }
                if placement.random.next_int(5) == 0 {
                    length = 1;
                }
                for l in 1..=length {
                    if is_air(placement, pos)? {
                        if l == length || !is_air(placement, offset(pos, [0, 1, 0]))? {
                            let head = self.heads[placement.random.next_int(9) as usize];
                            set_block(placement, pos, head, 2)?;
                            break;
                        }
                        set_block(placement, pos, self.plant, 2)?;
                    }
                    pos[1] += 1;
                }
            }
        }
        Ok(true)
    }
}

//! `FoliagePlacer`s and their shared leaf rows.
use super::trunk::Attachment;
use super::{properties, Setter, Tree, HORIZONTAL};
use crate::world::level::levelgen::decoration::program::{IntProvider, Reader};
use crate::world::level::levelgen::decoration::region::Result;

#[derive(Clone, Debug, PartialEq)]
pub(crate) enum Kind {
    Blob { height: i32 },
    Fancy { height: i32 },
    Bush { height: i32 },
    Spruce { trunk_height: IntProvider },
    Pine { height: IntProvider },
    Acacia,
    MegaJungle { height: i32 },
    MegaPine { crown_height: IntProvider },
    DarkOak,
    RandomSpread { foliage_height: IntProvider, attempts: i32 },
    Cherry { height: IntProvider, wide_hole: f32, corner_hole: f32, hanging: f32, hanging_extension: f32 },
}

#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Foliage {
    radius: IntProvider,
    offset: IntProvider,
    kind: Kind,
}

impl Foliage {
    pub(crate) fn read(reader: &mut Reader) -> Option<Self> {
        let kind = reader.next()?;
        let radius = IntProvider::read(reader)?;
        let offset = IntProvider::read(reader)?;
        let kind = match kind {
            1 => Kind::Blob { height: reader.next()? },
            2 => Kind::Fancy { height: reader.next()? },
            3 => Kind::Bush { height: reader.next()? },
            4 => Kind::Spruce { trunk_height: IntProvider::read(reader)? },
            5 => Kind::Pine { height: IntProvider::read(reader)? },
            6 => Kind::Acacia,
            7 => Kind::MegaJungle { height: reader.next()? },
            8 => Kind::MegaPine { crown_height: IntProvider::read(reader)? },
            9 => Kind::DarkOak,
            10 => Kind::RandomSpread { foliage_height: IntProvider::read(reader)?, attempts: reader.next()? },
            11 => Kind::Cherry {
                height: IntProvider::read(reader)?,
                wide_hole: reader.float()?,
                corner_hole: reader.float()?,
                hanging: reader.float()?,
                hanging_extension: reader.float()?,
            },
            _ => return None,
        };
        Some(Self { radius, offset, kind })
    }

    /// `foliageHeight(random, treeHeight, config)`.
    pub(crate) fn foliage_height(&self, placement: &mut super::Placement, tree_height: i32) -> i32 {
        match &self.kind {
            Kind::Blob { height } | Kind::Fancy { height } | Kind::Bush { height } | Kind::MegaJungle { height } => *height,
            Kind::Spruce { trunk_height } => 4.max(tree_height - trunk_height.sample(placement)),
            Kind::Pine { height } => height.sample(placement),
            Kind::Acacia => 0,
            Kind::MegaPine { crown_height } => crown_height.sample(placement),
            Kind::DarkOak => 4,
            Kind::RandomSpread { foliage_height, .. } => foliage_height.sample(placement),
            Kind::Cherry { height, .. } => height.sample(placement),
        }
    }

    /// `foliageRadius(random, trunkHeight)`.
    pub(crate) fn foliage_radius(&self, placement: &mut super::Placement, trunk_height: i32) -> i32 {
        let radius = self.radius.sample(placement);
        match self.kind {
            Kind::Pine { .. } => radius + placement.random.next_int((trunk_height + 1).max(1)),
            _ => radius,
        }
    }

    /// `createFoliage(level, setter, random, config, freeHeight, attachment, foliageHeight, radius)`.
    pub(crate) fn create(&self, tree: &mut Tree, _free: i32, attachment: Attachment, j: i32, k: i32) -> Result<()> {
        let l = self.offset.sample(tree.placement);
        let pos = attachment.pos;
        let ro = attachment.radius_offset;
        let double = attachment.double_trunk;
        match self.kind {
            Kind::Blob { .. } => {
                let mut m = l;
                while m >= l - j {
                    let n = (k + ro - 1 - m / 2).max(0);
                    self.row(tree, pos, n, m, double)?;
                    m -= 1;
                }
            }
            Kind::Fancy { .. } => {
                let mut m = l;
                while m >= l - j {
                    let n = k + i32::from(m != l && m != l - j);
                    self.row(tree, pos, n, m, double)?;
                    m -= 1;
                }
            }
            Kind::Bush { .. } => {
                let mut m = l;
                while m >= l - j {
                    self.row(tree, pos, k + ro - 1 - m, m, double)?;
                    m -= 1;
                }
            }
            Kind::Spruce { .. } => {
                let mut m = tree.placement.random.next_int(2);
                let mut n = 1;
                let mut o = 0;
                let mut p = l;
                while p >= -j {
                    self.row(tree, pos, m, p, double)?;
                    if m >= n {
                        m = o;
                        o = 1;
                        n = (n + 1).min(k + ro);
                    } else {
                        m += 1;
                    }
                    p -= 1;
                }
            }
            Kind::Pine { .. } => {
                let mut m = 0;
                let mut n = l;
                while n >= l - j {
                    self.row(tree, pos, m, n, double)?;
                    if m >= 1 && n == l - j + 1 {
                        m -= 1;
                    } else if m < k + ro {
                        m += 1;
                    }
                    n -= 1;
                }
            }
            Kind::Acacia => {
                let base = [pos[0], pos[1] + l, pos[2]];
                self.row(tree, base, k + ro, -1 - j, double)?;
                self.row(tree, base, k - 1, -j, double)?;
                self.row(tree, base, k + ro - 1, 0, double)?;
            }
            Kind::MegaJungle { .. } => {
                let m = if double { j } else { 1 + tree.placement.random.next_int(2) };
                let mut n = l;
                while n >= l - m {
                    self.row(tree, pos, k + ro + 1 - n, n, double)?;
                    n -= 1;
                }
            }
            Kind::MegaPine { .. } => {
                let mut m = 0;
                for n in pos[1] - j + l..=pos[1] + l {
                    let o = pos[1] - n;
                    let p = k + ro + floor_f(o as f32 / j as f32 * 3.5f32);
                    let q = if o > 0 && p == m && (n & 1) == 0 { p + 1 } else { p };
                    self.row(tree, [pos[0], n, pos[2]], q, 0, double)?;
                    m = p;
                }
            }
            Kind::DarkOak => {
                let base = [pos[0], pos[1] + l, pos[2]];
                if double {
                    self.row(tree, base, k + 2, -1, double)?;
                    self.row(tree, base, k + 3, 0, double)?;
                    self.row(tree, base, k + 2, 1, double)?;
                    if tree.placement.random.next(1) != 0 {
                        self.row(tree, base, k, 2, double)?;
                    }
                } else {
                    self.row(tree, base, k + 2, -1, double)?;
                    self.row(tree, base, k + 1, 0, double)?;
                }
            }
            Kind::RandomSpread { attempts, .. } => {
                for _ in 0..attempts {
                    let random = &mut tree.placement.random;
                    let dx = random.next_int(k) - random.next_int(k);
                    let dy = random.next_int(j) - random.next_int(j);
                    let dz = random.next_int(k) - random.next_int(k);
                    try_place_leaf(tree, [pos[0] + dx, pos[1] + dy, pos[2] + dz])?;
                }
            }
            Kind::Cherry { hanging, hanging_extension, .. } => {
                let base = [pos[0], pos[1] + l, pos[2]];
                let m = k + ro - 1;
                self.row(tree, base, m - 2, j - 3, double)?;
                self.row(tree, base, m - 1, j - 4, double)?;
                let mut n = j - 5;
                while n >= 0 {
                    self.row(tree, base, m, n, double)?;
                    n -= 1;
                }
                self.row_with_hanging(tree, base, m, -1, double, hanging, hanging_extension)?;
                self.row_with_hanging(tree, base, m - 1, -2, double, hanging, hanging_extension)?;
            }
        }
        Ok(())
    }

    /// `shouldSkipLocation(random, dx, y, dz, radius, doubleTrunk)`.
    fn skip(&self, tree: &mut Tree, i: i32, j: i32, k: i32, l: i32, double: bool) -> bool {
        let random = &mut tree.placement.random;
        match self.kind {
            Kind::Blob { .. } => i == l && k == l && (random.next_int(2) == 0 || j == 0),
            Kind::Fancy { .. } => {
                let (a, b) = (i as f32 + 0.5f32, k as f32 + 0.5f32);
                a * a + b * b > (l * l) as f32
            }
            Kind::Bush { .. } => i == l && k == l && random.next_int(2) == 0,
            Kind::Spruce { .. } | Kind::Pine { .. } => i == l && k == l && l > 0,
            Kind::Acacia => {
                if j == 0 { (i > 1 || k > 1) && i != 0 && k != 0 } else { i == l && k == l && l > 0 }
            }
            Kind::MegaJungle { .. } | Kind::MegaPine { .. } => i + k >= 7 || i * i + k * k > l * l,
            Kind::DarkOak => {
                if j == -1 && !double {
                    i == l && k == l
                } else {
                    j == 1 && i + k > l * 2 - 2
                }
            }
            Kind::RandomSpread { .. } => false,
            Kind::Cherry { wide_hole, corner_hole, .. } => {
                if j == -1 && (i == l || k == l) && random.next_float() < wide_hole {
                    return true;
                }
                let corner = i == l && k == l;
                if l > 2 {
                    corner || (i + k > l * 2 - 2 && random.next_float() < corner_hole)
                } else {
                    corner && random.next_float() < corner_hole
                }
            }
        }
    }

    /// `shouldSkipLocationSigned`, with the dark oak override.
    fn skip_signed(&self, tree: &mut Tree, i: i32, j: i32, k: i32, l: i32, double: bool) -> bool {
        if matches!(self.kind, Kind::DarkOak) && !(j != 0 || !double || (i != -l && i < l) || (k != -l && k < l)) {
            return true;
        }
        let (m, n) = if double { (i.abs().min((i - 1).abs()), k.abs().min((k - 1).abs())) } else { (i.abs(), k.abs()) };
        self.skip(tree, m, j, n, l, double)
    }

    /// `placeLeavesRow`.
    fn row(&self, tree: &mut Tree, pos: [i32; 3], i: i32, j: i32, double: bool) -> Result<()> {
        let k = i32::from(double);
        for l in -i..=i + k {
            for m in -i..=i + k {
                if !self.skip_signed(tree, l, j, m, i, double) {
                    try_place_leaf(tree, [pos[0] + l, pos[1] + j, pos[2] + m])?;
                }
            }
        }
        Ok(())
    }

    /// `placeLeavesRowWithHangingLeavesBelow`.
    #[allow(clippy::too_many_arguments)]
    fn row_with_hanging(&self, tree: &mut Tree, pos: [i32; 3], i: i32, j: i32, double: bool, f: f32, g: f32) -> Result<()> {
        self.row(tree, pos, i, j, double)?;
        let k = i32::from(double);
        let below = [pos[0], pos[1] - 1, pos[2]];
        for direction in 0..4 {
            let clockwise = (direction + 1) % 4;
            let [cx, cz] = HORIZONTAL[clockwise];
            let positive = cx > 0 || cz > 0;
            let l = if positive { i + k } else { i };
            let [dx, dz] = HORIZONTAL[direction];
            let mut cursor = [pos[0] + cx * l - dx * i, pos[1] + j - 1, pos[2] + cz * l - dz * i];
            let mut m = -i;
            while m < i + k {
                let set = tree.built.foliage.contains([cursor[0], cursor[1] + 1, cursor[2]]);
                if set && try_place_extension(tree, f, below, cursor)? {
                    let lower = [cursor[0], cursor[1] - 1, cursor[2]];
                    try_place_extension(tree, g, below, lower)?;
                }
                m += 1;
                cursor = [cursor[0] + dx, cursor[1], cursor[2] + dz];
            }
        }
        Ok(())
    }
}

/// `FoliagePlacer.tryPlaceExtension`.
fn try_place_extension(tree: &mut Tree, chance: f32, below: [i32; 3], pos: [i32; 3]) -> Result<bool> {
    let distance = (pos[0] - below[0]).abs() + (pos[1] - below[1]).abs() + (pos[2] - below[2]).abs();
    if distance >= 7 {
        return Ok(false);
    }
    if tree.placement.random.next_float() > chance {
        return Ok(false);
    }
    try_place_leaf(tree, pos)
}

/// `FoliagePlacer.tryPlaceLeaf`.
pub(crate) fn try_place_leaf(tree: &mut Tree, pos: [i32; 3]) -> Result<bool> {
    let properties = properties().expect("checked before the placement");
    let state = tree.state(pos)?;
    let persistent = tree.value(state, properties.persistent) == Some(properties.persistent_true);
    if persistent || !tree.valid_tree_pos(pos)? {
        return Ok(false);
    }
    let mut leaf = tree.config.foliage_provider.state(tree.placement);
    if tree.value(leaf, properties.waterlogged).is_some() {
        let water = tree.is(tree.config.sets.water_source, pos)?;
        let index = if water { properties.waterlogged_true } else { properties.waterlogged_false };
        leaf = tree.with_value(leaf, properties.waterlogged, index).unwrap_or(leaf);
    }
    tree.set(Setter::Foliage, pos, leaf)?;
    Ok(true)
}

fn floor_f(value: f32) -> i32 {
    let truncated = value as i32;
    if value < truncated as f32 { truncated - 1 } else { truncated }
}

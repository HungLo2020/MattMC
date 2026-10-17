//! `TrunkPlacer`s. Each returns its foliage attachments in Java's list order.
use super::{Setter, Tree, HORIZONTAL};
use crate::core::java_trig;
use crate::world::level::levelgen::decoration::program::{IntProvider, Reader};
use crate::world::level::levelgen::decoration::region::Result;

/// `FoliagePlacer.FoliageAttachment`.
#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) struct Attachment {
    pub(crate) pos: [i32; 3],
    pub(crate) radius_offset: i32,
    pub(crate) double_trunk: bool,
}

#[derive(Clone, Debug, PartialEq)]
pub(crate) enum Kind {
    Straight,
    Forking,
    Fancy,
    Giant,
    MegaJungle,
    DarkOak,
    Bending { min_height_for_leaves: i32, bend_length: IntProvider },
    Cherry { branch_count: IntProvider, horizontal_length: IntProvider, start_min: i32, start_max: i32, end_offset: IntProvider },
    UpwardsBranching { extra_steps: IntProvider, branch_probability: f32, extra_length: IntProvider, grow_through: usize },
    /// MattMC's offshoot trunk: a straight trunk with up to one sideways log.
    Offshoot,
}

#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Trunk {
    base: i32,
    rand_a: i32,
    rand_b: i32,
    kind: Kind,
}

/// `Direction.Plane.HORIZONTAL.getRandomDirection`: north, east, south, west.
fn random_horizontal(tree: &mut Tree) -> usize {
    tree.placement.random.next_int(4) as usize
}

/// `Direction.getAxis()` of a horizontal direction index: x for east/west, z otherwise.
fn axis_of(direction: usize) -> u16 {
    if direction == 1 || direction == 3 { 0 } else { 2 }
}

/// `Mth.floor(double)`.
fn floor(value: f64) -> i32 {
    let truncated = value as i32;
    if value < truncated as f64 { truncated - 1 } else { truncated }
}

/// `Mth.floor(float)`.
fn floor_f(value: f32) -> i32 {
    let truncated = value as i32;
    if value < truncated as f32 { truncated - 1 } else { truncated }
}

impl Trunk {
    pub(crate) fn read(reader: &mut Reader) -> Option<Self> {
        let kind = reader.next()?;
        let base = reader.next()?;
        let rand_a = reader.next()?;
        let rand_b = reader.next()?;
        let kind = match kind {
            1 => Kind::Straight,
            2 => Kind::Forking,
            3 => Kind::Fancy,
            4 => Kind::Giant,
            5 => Kind::MegaJungle,
            6 => Kind::DarkOak,
            7 => Kind::Bending { min_height_for_leaves: reader.next()?, bend_length: IntProvider::read(reader)? },
            8 => Kind::Cherry {
                branch_count: IntProvider::read(reader)?,
                horizontal_length: IntProvider::read(reader)?,
                start_min: reader.next()?,
                start_max: reader.next()?,
                end_offset: IntProvider::read(reader)?,
            },
            9 => Kind::UpwardsBranching {
                extra_steps: IntProvider::read(reader)?,
                branch_probability: reader.float()?,
                extra_length: IntProvider::read(reader)?,
                grow_through: reader.index()?,
            },
            10 => Kind::Offshoot,
            _ => return None,
        };
        Some(Self { base, rand_a, rand_b, kind })
    }

    pub(crate) fn sets(&self, out: &mut Vec<usize>) {
        if let Kind::UpwardsBranching { grow_through, .. } = self.kind {
            out.push(grow_through);
        }
    }

    /// `getTreeHeight`.
    pub(crate) fn tree_height(&self, placement: &mut super::Placement) -> i32 {
        let a = placement.random.next_int(self.rand_a + 1);
        let b = placement.random.next_int(self.rand_b + 1);
        self.base + a + b
    }

    /// `TrunkPlacer.validTreePos`, with the upwards-branching override.
    fn valid(&self, tree: &Tree, pos: [i32; 3]) -> Result<bool> {
        if tree.valid_tree_pos(pos)? {
            return Ok(true);
        }
        match self.kind {
            Kind::UpwardsBranching { grow_through, .. } => tree.is(grow_through, pos),
            _ => Ok(false),
        }
    }

    /// `isFree`: a valid tree position, or a log.
    pub(crate) fn is_free(&self, tree: &Tree, pos: [i32; 3]) -> Result<bool> {
        Ok(self.valid(tree, pos)? || tree.is(tree.config.sets.logs, pos)?)
    }

    /// `placeLog(..., function)`; `axis` applies `trySetValue(AXIS, axis)` to the provided state.
    fn place_log(&self, tree: &mut Tree, pos: [i32; 3], axis: Option<u16>) -> Result<bool> {
        if !self.valid(tree, pos)? {
            return Ok(false);
        }
        let state = tree.config.trunk_provider.state(tree.placement);
        let state = match axis {
            Some(axis) => tree.try_axis(state, axis),
            None => state,
        };
        tree.set(Setter::Log, pos, state)?;
        Ok(true)
    }

    fn place_log_if_free(&self, tree: &mut Tree, pos: [i32; 3]) -> Result<()> {
        if self.is_free(tree, pos)? {
            self.place_log(tree, pos, None)?;
        }
        Ok(())
    }

    fn above(pos: [i32; 3], dy: i32) -> [i32; 3] {
        [pos[0], pos[1] + dy, pos[2]]
    }

    pub(crate) fn place(&self, tree: &mut Tree, height: i32, origin: [i32; 3]) -> Result<Vec<Attachment>> {
        let below = Self::above(origin, -1);
        match self.kind {
            Kind::Straight => {
                tree.set_dirt(below)?;
                for y in 0..height {
                    self.place_log(tree, Self::above(origin, y), None)?;
                }
                Ok(vec![Attachment { pos: Self::above(origin, height), radius_offset: 0, double_trunk: false }])
            }
            Kind::Offshoot => {
                tree.set_dirt(below)?;
                for y in 0..height {
                    self.place_log(tree, Self::above(origin, y), None)?;
                }
                if height >= 5 {
                    let count = tree.placement.random.next_int(2);
                    let max = height - 3;
                    for _ in 0..count {
                        let offshoot = 2 + tree.placement.random.next_int((max - 2).max(1));
                        let direction = random_horizontal(tree);
                        let [dx, dz] = HORIZONTAL[direction];
                        let pos = [origin[0] + dx, origin[1] + offshoot, origin[2] + dz];
                        // `state.setValue(AXIS, ...)`: the trunk provider's states carry AXIS.
                        self.place_log(tree, pos, Some(axis_of(direction)))?;
                    }
                }
                Ok(vec![Attachment { pos: Self::above(origin, height), radius_offset: 0, double_trunk: false }])
            }
            Kind::Forking => self.forking(tree, height, origin),
            Kind::Fancy => self.fancy(tree, height, origin),
            Kind::Giant => self.giant(tree, height, origin),
            Kind::MegaJungle => {
                let mut list = self.giant(tree, height, origin)?;
                let mut j = height - 2 - tree.placement.random.next_int(4);
                while j > height / 2 {
                    let f = tree.placement.random.next_float() * (std::f64::consts::PI * 2.0) as f32;
                    let (mut k, mut l) = (0, 0);
                    for m in 0..5 {
                        k = (1.5f32 + mth_cos(f) * m as f32) as i32;
                        l = (1.5f32 + mth_sin(f) * m as f32) as i32;
                        self.place_log(tree, [origin[0] + k, origin[1] + j - 3 + m / 2, origin[2] + l], None)?;
                    }
                    list.push(Attachment { pos: [origin[0] + k, origin[1] + j, origin[2] + l], radius_offset: -2, double_trunk: false });
                    j -= 2 + tree.placement.random.next_int(4);
                }
                Ok(list)
            }
            Kind::DarkOak => self.dark_oak(tree, height, origin),
            Kind::Bending { min_height_for_leaves, ref bend_length } => {
                let direction = random_horizontal(tree);
                let [dx, dz] = HORIZONTAL[direction];
                let j = height - 1;
                let mut pos = origin;
                tree.set_dirt(below)?;
                let mut list = Vec::new();
                for k in 0..=j {
                    if k + 1 >= j + tree.placement.random.next_int(2) {
                        pos = [pos[0] + dx, pos[1], pos[2] + dz];
                    }
                    if tree.valid_tree_pos(pos)? {
                        self.place_log(tree, pos, None)?;
                    }
                    if k >= min_height_for_leaves {
                        list.push(Attachment { pos, radius_offset: 0, double_trunk: false });
                    }
                    pos[1] += 1;
                }
                let length = bend_length.sample(tree.placement);
                for _ in 0..=length {
                    if tree.valid_tree_pos(pos)? {
                        self.place_log(tree, pos, None)?;
                    }
                    list.push(Attachment { pos, radius_offset: 0, double_trunk: false });
                    pos = [pos[0] + dx, pos[1], pos[2] + dz];
                }
                Ok(list)
            }
            Kind::Cherry { .. } => self.cherry(tree, height, origin),
            Kind::UpwardsBranching { .. } => self.upwards_branching(tree, height, origin),
        }
    }

    fn forking(&self, tree: &mut Tree, height: i32, origin: [i32; 3]) -> Result<Vec<Attachment>> {
        tree.set_dirt(Self::above(origin, -1))?;
        let mut list = Vec::new();
        let direction = random_horizontal(tree);
        let j = height - tree.placement.random.next_int(4) - 1;
        let mut k = 3 - tree.placement.random.next_int(3);
        let (mut l, mut m) = (origin[0], origin[2]);
        let mut top = None;
        for n in 0..height {
            let o = origin[1] + n;
            if n >= j && k > 0 {
                l += HORIZONTAL[direction][0];
                m += HORIZONTAL[direction][1];
                k -= 1;
            }
            if self.place_log(tree, [l, o, m], None)? {
                top = Some(o + 1);
            }
        }
        if let Some(y) = top {
            list.push(Attachment { pos: [l, y, m], radius_offset: 1, double_trunk: false });
        }
        let (mut l, mut m) = (origin[0], origin[2]);
        let second = random_horizontal(tree);
        if second != direction {
            let start = j - tree.placement.random.next_int(2) - 1;
            let mut p = 1 + tree.placement.random.next_int(3);
            let mut top = None;
            let mut q = start;
            while q < height && p > 0 {
                if q >= 1 {
                    let r = origin[1] + q;
                    l += HORIZONTAL[second][0];
                    m += HORIZONTAL[second][1];
                    if self.place_log(tree, [l, r, m], None)? {
                        top = Some(r + 1);
                    }
                }
                q += 1;
                p -= 1;
            }
            if let Some(y) = top {
                list.push(Attachment { pos: [l, y, m], radius_offset: 0, double_trunk: false });
            }
        }
        Ok(list)
    }

    fn giant(&self, tree: &mut Tree, height: i32, origin: [i32; 3]) -> Result<Vec<Attachment>> {
        let below = Self::above(origin, -1);
        tree.set_dirt(below)?;
        tree.set_dirt([below[0] + 1, below[1], below[2]])?;
        tree.set_dirt([below[0], below[1], below[2] + 1])?;
        tree.set_dirt([below[0] + 1, below[1], below[2] + 1])?;
        for j in 0..height {
            self.place_log_if_free(tree, [origin[0], origin[1] + j, origin[2]])?;
            if j < height - 1 {
                self.place_log_if_free(tree, [origin[0] + 1, origin[1] + j, origin[2]])?;
                self.place_log_if_free(tree, [origin[0] + 1, origin[1] + j, origin[2] + 1])?;
                self.place_log_if_free(tree, [origin[0], origin[1] + j, origin[2] + 1])?;
            }
        }
        Ok(vec![Attachment { pos: Self::above(origin, height), radius_offset: 0, double_trunk: true }])
    }

    fn dark_oak(&self, tree: &mut Tree, height: i32, origin: [i32; 3]) -> Result<Vec<Attachment>> {
        let mut list = Vec::new();
        let below = Self::above(origin, -1);
        tree.set_dirt(below)?;
        tree.set_dirt([below[0] + 1, below[1], below[2]])?;
        tree.set_dirt([below[0], below[1], below[2] + 1])?;
        tree.set_dirt([below[0] + 1, below[1], below[2] + 1])?;
        let direction = random_horizontal(tree);
        let j = height - tree.placement.random.next_int(4);
        let mut k = 2 - tree.placement.random.next_int(3);
        let [l, m, n] = origin;
        let (mut o, mut p) = (l, n);
        let q = m + height - 1;
        for r in 0..height {
            if r >= j && k > 0 {
                o += HORIZONTAL[direction][0];
                p += HORIZONTAL[direction][1];
                k -= 1;
            }
            let pos = [o, m + r, p];
            if tree.is(tree.config.sets.leaves, pos)? || tree.is_air(pos)? {
                self.place_log(tree, pos, None)?;
                self.place_log(tree, [o + 1, m + r, p], None)?;
                self.place_log(tree, [o, m + r, p + 1], None)?;
                self.place_log(tree, [o + 1, m + r, p + 1], None)?;
            }
        }
        list.push(Attachment { pos: [o, q, p], radius_offset: 0, double_trunk: true });
        for r in -1..=2 {
            for s in -1..=2 {
                if (r < 0 || r > 1 || s < 0 || s > 1) && tree.placement.random.next_int(3) <= 0 {
                    let t = tree.placement.random.next_int(3) + 2;
                    for u in 0..t {
                        self.place_log(tree, [l + r, q - u - 1, n + s], None)?;
                    }
                    list.push(Attachment { pos: [l + r, q, n + s], radius_offset: 0, double_trunk: false });
                }
            }
        }
        Ok(list)
    }

    fn fancy(&self, tree: &mut Tree, height: i32, origin: [i32; 3]) -> Result<Vec<Attachment>> {
        let k = height + 2;
        let l = floor(k as f64 * 0.618);
        tree.set_dirt(Self::above(origin, -1))?;
        // `Math.pow(1.0 * k / 13.0, 2.0)`: HotSpot computes y == 2.0 as x * x.
        let ratio = 1.0 * k as f64 / 13.0;
        let m = 1.min(floor(1.382 + ratio * ratio));
        let n = origin[1] + l;
        let mut o = k - 5;
        let mut list: Vec<(Attachment, i32)> = vec![(Attachment { pos: Self::above(origin, o), radius_offset: 0, double_trunk: false }, n)];
        while o >= 0 {
            let f = tree_shape(k, o);
            if f >= 0.0 {
                for _ in 0..m {
                    // `1.0 * f * (nextFloat() + 0.328)`: the sum is already double.
                    let g = 1.0 * f as f64 * (f64::from(tree.placement.random.next_float()) + 0.328);
                    let h = (tree.placement.random.next_float() * 2.0f32) as f64 * std::f64::consts::PI;
                    let q = g * java_trig::sin(h).ok_or(crate::world::level::levelgen::decoration::region::Failure::Unbound)? + 0.5;
                    let r = g * java_trig::cos(h).ok_or(crate::world::level::levelgen::decoration::region::Failure::Unbound)? + 0.5;
                    let branch = [origin[0] + floor(q), origin[1] + o - 1, origin[2] + floor(r)];
                    let top = Self::above(branch, 5);
                    if self.make_limb(tree, branch, top, false)? {
                        let s = origin[0] - branch[0];
                        let t = origin[2] - branch[2];
                        let u = branch[1] as f64 - ((s * s + t * t) as f64).sqrt() * 0.381;
                        let v = if u > n as f64 { n } else { u as i32 };
                        let base = [origin[0], v, origin[2]];
                        if self.make_limb(tree, base, branch, false)? {
                            list.push((Attachment { pos: branch, radius_offset: 0, double_trunk: false }, base[1]));
                        }
                    }
                }
            }
            o -= 1;
        }
        self.make_limb(tree, origin, Self::above(origin, l), true)?;
        for &(attachment, branch_base) in &list {
            let base = [origin[0], branch_base, origin[2]];
            if base != attachment.pos && trim_branches(k, branch_base - origin[1]) {
                self.make_limb(tree, base, attachment.pos, true)?;
            }
        }
        Ok(list.into_iter().filter(|&(_, base)| trim_branches(k, base - origin[1])).map(|(a, _)| a).collect())
    }

    /// `FancyTrunkPlacer.makeLimb`.
    fn make_limb(&self, tree: &mut Tree, from: [i32; 3], to: [i32; 3], place: bool) -> Result<bool> {
        if !place && from == to {
            return Ok(true);
        }
        let delta = [to[0] - from[0], to[1] - from[1], to[2] - from[2]];
        let steps = delta[0].abs().max(delta[1].abs().max(delta[2].abs()));
        let f = delta[0] as f32 / steps as f32;
        let g = delta[1] as f32 / steps as f32;
        let h = delta[2] as f32 / steps as f32;
        for j in 0..=steps {
            let pos = [
                from[0] + floor_f(0.5f32 + j as f32 * f),
                from[1] + floor_f(0.5f32 + j as f32 * g),
                from[2] + floor_f(0.5f32 + j as f32 * h),
            ];
            if place {
                // `getLogAxis(from, pos)`.
                let dx = (pos[0] - from[0]).abs();
                let dz = (pos[2] - from[2]).abs();
                let longest = dx.max(dz);
                let axis = if longest > 0 { if dx == longest { 0 } else { 2 } } else { 1 };
                self.place_log(tree, pos, Some(axis))?;
            } else if !self.is_free(tree, pos)? {
                return Ok(false);
            }
        }
        Ok(true)
    }

    fn cherry(&self, tree: &mut Tree, height: i32, origin: [i32; 3]) -> Result<Vec<Attachment>> {
        let Kind::Cherry { ref branch_count, ref horizontal_length, start_min, start_max, ref end_offset } = self.kind else {
            unreachable!()
        };
        tree.set_dirt(Self::above(origin, -1))?;
        let j = 0.max(height - 1 + IntProvider::Uniform(start_min, start_max).sample(tree.placement));
        let mut k = 0.max(height - 1 + IntProvider::Uniform(start_min, start_max - 1).sample(tree.placement));
        if k >= j {
            k += 1;
        }
        let l = branch_count.sample(tree.placement);
        let three = l == 3;
        let two = l >= 2;
        let m = if three { height } else if two { j.max(k) + 1 } else { j + 1 };
        for n in 0..m {
            self.place_log(tree, Self::above(origin, n), None)?;
        }
        let mut list = Vec::new();
        if three {
            list.push(Attachment { pos: Self::above(origin, m), radius_offset: 0, double_trunk: false });
        }
        let direction = random_horizontal(tree);
        let axis = axis_of(direction);
        list.push(self.cherry_branch(tree, height, origin, axis, direction, j, j < m - 1, end_offset, horizontal_length)?);
        if two {
            let opposite = (direction + 2) % 4;
            list.push(self.cherry_branch(tree, height, origin, axis, opposite, k, k < m - 1, end_offset, horizontal_length)?);
        }
        Ok(list)
    }

    #[allow(clippy::too_many_arguments)]
    fn cherry_branch(
        &self,
        tree: &mut Tree,
        height: i32,
        origin: [i32; 3],
        axis: u16,
        direction: usize,
        start: i32,
        below_top: bool,
        end_offset: &IntProvider,
        horizontal_length: &IntProvider,
    ) -> Result<Attachment> {
        let [dx, dz] = HORIZONTAL[direction];
        let mut pos = Self::above(origin, start);
        let k = height - 1 + end_offset.sample(tree.placement);
        let extended = below_top || k < start;
        let l = horizontal_length.sample(tree.placement) + i32::from(extended);
        let end = [origin[0] + dx * l, origin[1] + k, origin[2] + dz * l];
        for _ in 0..(if extended { 2 } else { 1 }) {
            pos = [pos[0] + dx, pos[1], pos[2] + dz];
            self.place_log(tree, pos, Some(axis))?;
        }
        let vertical = if end[1] > pos[1] { 1 } else { -1 };
        loop {
            let distance = (pos[0] - end[0]).abs() + (pos[1] - end[1]).abs() + (pos[2] - end[2]).abs();
            if distance == 0 {
                return Ok(Attachment { pos: Self::above(end, 1), radius_offset: 0, double_trunk: false });
            }
            let f = (end[1] - pos[1]).abs() as f32 / distance as f32;
            let up = tree.placement.random.next_float() < f;
            pos = if up { Self::above(pos, vertical) } else { [pos[0] + dx, pos[1], pos[2] + dz] };
            self.place_log(tree, pos, if up { None } else { Some(axis) })?;
        }
    }

    fn upwards_branching(&self, tree: &mut Tree, height: i32, origin: [i32; 3]) -> Result<Vec<Attachment>> {
        let Kind::UpwardsBranching { ref extra_steps, branch_probability, ref extra_length, .. } = self.kind else {
            unreachable!()
        };
        let mut list = Vec::new();
        for j in 0..height {
            let k = origin[1] + j;
            let pos = [origin[0], k, origin[2]];
            if self.place_log(tree, pos, None)? && j < height - 1 && tree.placement.random.next_float() < branch_probability {
                let direction = random_horizontal(tree);
                let l = extra_length.sample(tree.placement);
                let m = 0.max(l - extra_length.sample(tree.placement) - 1);
                let n = extra_steps.sample(tree.placement);
                self.upwards_branch(tree, height, &mut list, pos, k, direction, m, n)?;
            }
            if j == height - 1 {
                list.push(Attachment { pos: [origin[0], k + 1, origin[2]], radius_offset: 0, double_trunk: false });
            }
        }
        Ok(list)
    }

    #[allow(clippy::too_many_arguments)]
    fn upwards_branch(
        &self,
        tree: &mut Tree,
        height: i32,
        list: &mut Vec<Attachment>,
        start: [i32; 3],
        j: i32,
        direction: usize,
        k: i32,
        mut l: i32,
    ) -> Result<()> {
        let mut m = j + k;
        let (mut n, mut o) = (start[0], start[2]);
        let mut p = k;
        while p < height && l > 0 {
            if p >= 1 {
                let q = j + p;
                n += HORIZONTAL[direction][0];
                o += HORIZONTAL[direction][1];
                m = q;
                if self.place_log(tree, [n, q, o], None)? {
                    m = q + 1;
                }
                list.push(Attachment { pos: [n, q, o], radius_offset: 0, double_trunk: false });
            }
            p += 1;
            l -= 1;
        }
        if m - j > 1 {
            list.push(Attachment { pos: [n, m, o], radius_offset: 0, double_trunk: false });
            list.push(Attachment { pos: [n, m - 2, o], radius_offset: 0, double_trunk: false });
        }
        Ok(())
    }
}

/// `FancyTrunkPlacer.trimBranches`.
fn trim_branches(height: i32, y: i32) -> bool {
    y as f64 >= height as f64 * 0.2
}

/// `FancyTrunkPlacer.treeShape`.
fn tree_shape(height: i32, y: i32) -> f32 {
    if (y as f32) < height as f32 * 0.3f32 {
        return -1.0;
    }
    let f = height as f32 / 2.0;
    let g = f - y as f32;
    let mut h = (f * f - g * g).sqrt();
    if g == 0.0 {
        h = f;
    } else if g.abs() >= f {
        return 0.0;
    }
    h * 0.5
}

fn mth_sin(value: f32) -> f32 {
    crate::world::level::levelgen::decoration::ore::sine_table()[((value * 10430.378_f32) as i32 & 65535) as usize]
}

fn mth_cos(value: f32) -> f32 {
    crate::world::level::levelgen::decoration::ore::sine_table()[((value * 10430.378_f32 + 16384.0f32) as i32 & 65535) as usize]
}

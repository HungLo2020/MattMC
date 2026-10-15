//! `PlacedFeature.placeWithContext`: each modifier's `getPositions` runs as its
//! stream's `flatMap` reaches it, so draws interleave depth first with the
//! configured feature's own draws, exactly as Java's lazy streams order them.
//! Nested placed features (selectors) run the same walk without a biome filter.
use super::program::{Anchor, Feature, Modifier, Placed, Predicate, Program};
use super::random::WorldgenRandom;
use super::region::{Region, Result};

pub(crate) struct Placement<'p, 'r, 'a> {
    pub(crate) program: &'p Program,
    pub(crate) region: &'r mut Region<'a>,
    pub(crate) random: WorldgenRandom,
    pub(crate) scratch: Scratch,
}

/// One column's `findOnGroundYPosition` progress: grounds found so far, top down.
#[derive(Clone)]
struct GroundScan {
    grounds: Vec<i32>,
    y: i32,
    above: u32,
    done: bool,
}

/// Per-call buffers reused by every vein of one placement.
#[derive(Default)]
pub(crate) struct Scratch {
    pub(crate) samples: Vec<f64>,
    pub(crate) spheres: Vec<f64>,
    pub(crate) spans: Vec<i32>,
    pub(crate) visited: Vec<u64>,
}

/// Height providers whose range is empty in this context: Java warns there, so
/// the whole placement stays in Java. Checked before any draw, nested ones too.
pub(crate) fn heights_resolve(program: &Program, min_gen_y: i32, gen_depth: i32) -> bool {
    placed_heights_resolve(&program.placed, min_gen_y, gen_depth)
}

fn placed_heights_resolve(placed: &Placed, min_gen_y: i32, gen_depth: i32) -> bool {
    let resolve = |a: Anchor| a.resolve(min_gen_y, gen_depth);
    let own = placed.modifiers.iter().all(|m| match *m {
        Modifier::HeightUniform(min, max) | Modifier::HeightTrapezoid(min, max, _) => resolve(min) <= resolve(max),
        Modifier::HeightBiasedToBottom(min, max, inner) | Modifier::HeightVeryBiasedToBottom(min, max, inner) => {
            resolve(max).wrapping_sub(resolve(min)).wrapping_sub(inner).wrapping_add(1) > 0
        }
        _ => true,
    });
    own && match &placed.feature {
        Feature::RandomSelector { entries, default } => {
            entries.iter().all(|(_, p)| placed_heights_resolve(p, min_gen_y, gen_depth))
                && placed_heights_resolve(default, min_gen_y, gen_depth)
        }
        Feature::SimpleRandomSelector(children) => children.iter().all(|p| placed_heights_resolve(p, min_gen_y, gen_depth)),
        Feature::RandomBooleanSelector(a, b) => {
            placed_heights_resolve(a, min_gen_y, gen_depth) && placed_heights_resolve(b, min_gen_y, gen_depth)
        }
        Feature::RandomPatch { placed, .. } => placed_heights_resolve(placed, min_gen_y, gen_depth),
        Feature::Patch(patch) => placed_heights_resolve(&patch.vegetation, min_gen_y, gen_depth),
        _ => true,
    }
}

impl<'p, 'r, 'a> Placement<'p, 'r, 'a> {
    pub(crate) fn new(program: &'p Program, region: &'r mut Region<'a>, random: WorldgenRandom, scratch: Scratch) -> Self {
        Self { program, region, random, scratch }
    }

    /// Returns whether any position placed the feature, the scratch for reuse
    /// and the random's gaussian cache for the next feature.
    pub(crate) fn run(mut self, origin: [i32; 3]) -> (Result<bool>, Scratch, Option<f64>) {
        let program = self.program;
        let result = self.place_placed(&program.placed, origin).and_then(|placed| self.region.check_deferred().map(|_| placed));
        (result, self.scratch, self.random.gaussian)
    }

    /// `run`, handing back the whole random (a caller-owned stream).
    pub(crate) fn run_keeping_random(mut self, origin: [i32; 3]) -> (Result<bool>, Scratch, WorldgenRandom) {
        let program = self.program;
        let result = self.place_placed(&program.placed, origin).and_then(|placed| self.region.check_deferred().map(|_| placed));
        (result, self.scratch, self.random)
    }

    /// `PlacedFeature.place`/`placeWithBiomeCheck`.
    pub(crate) fn place_placed(&mut self, placed: &'p Placed, origin: [i32; 3]) -> Result<bool> {
        let mut any = false;
        self.walk(placed, 0, origin, &mut any)?;
        Ok(any)
    }

    fn resolve(&self, anchor: Anchor) -> i32 {
        anchor.resolve(self.region.min_gen_y, self.region.gen_depth)
    }

    fn walk(&mut self, placed: &'p Placed, index: usize, pos: [i32; 3], any: &mut bool) -> Result<()> {
        let Some(modifier) = placed.modifiers.get(index) else {
            // `ConfiguredFeature.place` -> `Feature.place`: ensureCanWrite at the origin first.
            if self.region.ensure_can_write(pos[0], pos[1], pos[2]) && self.place_feature(&placed.feature, pos)? {
                *any = true;
            }
            return Ok(());
        };
        let next = index + 1;
        match *modifier {
            Modifier::Count(ref provider) => {
                let count = provider.sample(self);
                for _ in 0..count.max(0) {
                    self.walk(placed, next, pos, any)?;
                }
            }
            Modifier::InSquare => {
                let x = self.random.next_int(16).wrapping_add(pos[0]);
                let z = self.random.next_int(16).wrapping_add(pos[2]);
                self.walk(placed, next, [x, pos[1], z], any)?;
            }
            Modifier::HeightUniform(min, max) => {
                let y = self.random.between_inclusive(self.resolve(min), self.resolve(max));
                self.walk(placed, next, [pos[0], y, pos[2]], any)?;
            }
            Modifier::HeightTrapezoid(min, max, plateau) => {
                let (i, j) = (self.resolve(min), self.resolve(max));
                let k = j.wrapping_sub(i);
                let y = if plateau >= k {
                    self.random.between_inclusive(i, j)
                } else {
                    let l = (k - plateau) / 2;
                    let m = k - l;
                    let a = self.random.between_inclusive(0, m);
                    let b = self.random.between_inclusive(0, l);
                    i.wrapping_add(a).wrapping_add(b)
                };
                self.walk(placed, next, [pos[0], y, pos[2]], any)?;
            }
            Modifier::HeightBiasedToBottom(min, max, inner) => {
                let (i, j) = (self.resolve(min), self.resolve(max));
                let k = self.random.next_int(j.wrapping_sub(i).wrapping_sub(inner).wrapping_add(1));
                let y = self.random.next_int(k.wrapping_add(inner)).wrapping_add(i);
                self.walk(placed, next, [pos[0], y, pos[2]], any)?;
            }
            Modifier::Fixed(ref positions) => {
                let (cx, cz) = (pos[0] >> 4, pos[2] >> 4);
                for &at in positions {
                    if at[0] >> 4 == cx && at[2] >> 4 == cz {
                        self.walk(placed, next, at, any)?;
                    }
                }
            }
            Modifier::HeightVeryBiasedToBottom(min, max, inner) => {
                let (i, j) = (self.resolve(min), self.resolve(max));
                // `Mth.nextInt(random, a, b)`: `a` without a draw when `a >= b`.
                let mut between = |a: i32, b: i32| if a >= b { a } else { self.random.next_int(b.wrapping_sub(a).wrapping_add(1)).wrapping_add(a) };
                let k = between(i.wrapping_add(inner), j);
                let l = between(i, k.wrapping_sub(1));
                let y = between(i, l.wrapping_sub(1).wrapping_add(inner));
                self.walk(placed, next, [pos[0], y, pos[2]], any)?;
            }
            Modifier::Biome => {
                let biome = self.region.biome(pos[0], pos[1], pos[2])?;
                if self.program.biome_allowed(biome) {
                    self.walk(placed, next, pos, any)?;
                }
            }
            Modifier::Rarity(chance) => {
                if self.random.next_float() < 1.0f32 / chance as f32 {
                    self.walk(placed, next, pos, any)?;
                }
            }
            Modifier::BlockPredicate(ref predicate) => {
                if self.test(predicate, pos)? {
                    self.walk(placed, next, pos, any)?;
                }
            }
            Modifier::Heightmap(ordinal) => {
                let y = self.region.height_by_type(ordinal, pos[0], pos[2])?;
                if y > self.region.min_y {
                    self.walk(placed, next, [pos[0], y, pos[2]], any)?;
                }
            }
            Modifier::CountOnEveryLayer { ref count, empty, bedrock } => {
                // Eager: every layer's positions are found before any is placed,
                // so nothing writes while they are found and each column's
                // ground layers are scanned once, top down, as far as needed.
                let mut positions = Vec::new();
                let mut columns: Vec<Option<GroundScan>> = vec![None; 256];
                let mut layer = 0;
                loop {
                    let mut found = false;
                    let mut j = 0;
                    while j < count.sample(self) {
                        let dx = self.random.next_int(16);
                        let dz = self.random.next_int(16);
                        let (x, z) = (dx.wrapping_add(pos[0]), dz.wrapping_add(pos[2]));
                        let column = &mut columns[(dz * 16 + dx) as usize];
                        if column.is_none() {
                            let top = self.region.height_by_type(4, x, z)?;
                            *column = Some(GroundScan { grounds: Vec::new(), y: top, above: self.region.level_block_state(x, top, z)?, done: false });
                        }
                        let scan = column.as_mut().expect("column scan initialised");
                        if let Some(y) = self.program_ground(scan, x, z, layer, empty, bedrock)? {
                            positions.push([x, y, z]);
                            found = true;
                        }
                        j += 1;
                    }
                    layer += 1;
                    if !found {
                        break;
                    }
                }
                for position in positions {
                    self.walk(placed, next, position, any)?;
                }
            }
            Modifier::EnvironmentScan { up, max_steps, ref target, ref allowed } => {
                if let Some(found) = self.scan(pos, up, max_steps, target, allowed)? {
                    self.walk(placed, next, found, any)?;
                }
            }
            Modifier::RandomOffset(ref xz, ref y) => {
                let x = pos[0].wrapping_add(xz.sample(self));
                let dy = pos[1].wrapping_add(y.sample(self));
                let z = pos[2].wrapping_add(xz.sample(self));
                self.walk(placed, next, [x, dy, z], any)?;
            }
            Modifier::NoiseThresholdCount { noise, level, below, above } => {
                let value = self.program.noise(noise, pos[0] as f64 / 200.0, pos[2] as f64 / 200.0, 0.0);
                self.repeat(placed, next, pos, if value < level { below } else { above }, any)?;
            }
            Modifier::NoiseBasedCount { noise, ratio, factor, offset } => {
                let value = self.program.noise(noise, pos[0] as f64 / factor, pos[2] as f64 / factor, 0.0);
                // Java's `(int)Math.ceil(...)`: saturating, NaN to zero, as Rust's cast.
                let count = ((value + offset) * ratio as f64).ceil() as i32;
                self.repeat(placed, next, pos, count, any)?;
            }
            Modifier::SurfaceRelativeThreshold { heightmap, min, max } => {
                let height = self.region.height_by_type(heightmap, pos[0], pos[2])? as i64;
                let y = pos[1] as i64;
                if height + min as i64 <= y && y <= height + max as i64 {
                    self.walk(placed, next, pos, any)?;
                }
            }
            Modifier::SurfaceWaterDepth(max) => {
                let floor = self.region.height_by_type(3, pos[0], pos[2])?;
                let surface = self.region.height_by_type(1, pos[0], pos[2])?;
                if surface - floor <= max {
                    self.walk(placed, next, pos, any)?;
                }
            }
        }
        Ok(())
    }

    /// `CountOnEveryLayerPlacement.findOnGroundYPosition` resumed from the
    /// column's previous scan: the `layer`th ground from the top, if any.
    fn program_ground(&self, scan: &mut GroundScan, x: i32, z: i32, layer: i32, empty: usize, bedrock: usize) -> Result<Option<i32>> {
        while scan.grounds.len() <= layer as usize && !scan.done {
            if scan.y < self.region.min_y + 1 {
                scan.done = true;
                break;
            }
            let below = self.region.level_block_state(x, scan.y - 1, z)?;
            if !self.program.in_set(empty, below) && self.program.in_set(empty, scan.above) && !self.program.in_set(bedrock, below) {
                scan.grounds.push(scan.y);
            }
            scan.above = below;
            scan.y -= 1;
        }
        Ok(scan.grounds.get(layer as usize).copied())
    }

    /// `EnvironmentScanPlacement.getPositions`.
    fn scan(&mut self, start: [i32; 3], up: bool, max_steps: i32, target: &Predicate, allowed: &Predicate) -> Result<Option<[i32; 3]>> {
        let mut pos = start;
        if !self.test(allowed, pos)? {
            return Ok(None);
        }
        for _ in 0..max_steps {
            if self.test(target, pos)? {
                return Ok(Some(pos));
            }
            pos[1] += if up { 1 } else { -1 };
            if self.region.outside_build_height(pos[1]) {
                return Ok(None);
            }
            if !self.test(allowed, pos)? {
                break;
            }
        }
        Ok(self.test(target, pos)?.then_some(pos))
    }

    /// `BlockPredicate.test(level, pos)`: reads only, no draws. Java answers
    /// what Rust cannot (dynamic shapes, `Predicate::Java`) through a sync upcall.
    pub(crate) fn test(&mut self, predicate: &Predicate, pos: [i32; 3]) -> Result<bool> {
        Ok(match predicate {
            Predicate::True => true,
            Predicate::AllOf(children) => {
                for child in children {
                    if !self.test(child, pos)? {
                        return Ok(false);
                    }
                }
                true
            }
            Predicate::AnyOf(children) => {
                for child in children {
                    if self.test(child, pos)? {
                        return Ok(true);
                    }
                }
                false
            }
            Predicate::Not(child) => !self.test(child, pos)?,
            Predicate::StateIn { offset, set } => {
                let state = self.region.level_block_state(pos[0] + offset[0], pos[1] + offset[1], pos[2] + offset[2])?;
                self.program.in_set(*set, state)
            }
            Predicate::Checked { offset, set, java } => {
                let state = self.region.level_block_state(pos[0] + offset[0], pos[1] + offset[1], pos[2] + offset[2])?;
                if super::facts::installed().is_some_and(|facts| facts.dynamic_shape(state)) {
                    self.java_test(*java, pos)?
                } else {
                    self.program.in_set(*set, state)
                }
            }
            Predicate::InsideWorld(offset) => !self.region.outside_build_height(pos[1] + offset[1]),
            Predicate::Java(java) => self.java_test(*java, pos)?,
        })
    }

    /// The program's Java step `index` at `pos`, drawing from this random.
    pub(crate) fn java_step(&mut self, index: i64, pos: [i32; 3]) -> Result<()> {
        let (lo, hi) = self.random.state();
        let [have, bits] = super::random::gaussian_words(self.random.gaussian);
        let mut args = [index, pos[0] as i64, pos[1] as i64, pos[2] as i64, lo, hi, self.region.slot, have, bits];
        self.region.sync_call(super::region::OP_JAVA_STEP, &mut args)?;
        self.random.set_state(args[4], args[5]);
        self.random.gaussian = super::random::gaussian_from(args[7], args[8]);
        Ok(())
    }

    /// HotSpot's `Math.log(a)` or `Math.pow(a, b)`: Java's intrinsics, which libm need not match.
    pub(crate) fn java_math(&self, op: i32, a: f64, b: f64) -> Result<f64> {
        let mut args = [a.to_bits() as i64, b.to_bits() as i64];
        self.region.pure_call(op, &mut args)?;
        Ok(f64::from_bits(args[0] as u64))
    }

    /// `MarsagliaPolarGaussian.nextGaussian` over this random; `Math.log` from Java.
    pub(crate) fn next_gaussian(&mut self) -> Result<f64> {
        if let Some(value) = self.random.gaussian.take() {
            return Ok(value);
        }
        loop {
            let d = 2.0 * self.random.next_double() - 1.0;
            let e = 2.0 * self.random.next_double() - 1.0;
            let f = d * d + e * e;
            if f >= 1.0 || f == 0.0 {
                continue;
            }
            let mut args = [f.to_bits() as i64];
            self.region.pure_call(super::region::OP_MATH_LOG, &mut args)?;
            let g = (-2.0 * f64::from_bits(args[0] as u64) / f).sqrt();
            self.random.gaussian = Some(e * g);
            return Ok(d * g);
        }
    }

    /// Java's predicate `index` at `pos`, on the world as Rust has written it so far.
    pub(crate) fn java_test(&mut self, index: usize, pos: [i32; 3]) -> Result<bool> {
        let mut args = [index as i64, pos[0] as i64, pos[1] as i64, pos[2] as i64, self.region.slot];
        Ok(self.region.sync_call(super::region::OP_TEST_PREDICATE, &mut args)? == 1)
    }

    /// `RepeatingPlacement`: the count first, then every copy of the position.
    fn repeat(&mut self, placed: &'p Placed, next: usize, pos: [i32; 3], count: i32, any: &mut bool) -> Result<()> {
        for _ in 0..count.max(0) {
            self.walk(placed, next, pos, any)?;
        }
        Ok(())
    }

    fn place_feature(&mut self, feature: &'p Feature, origin: [i32; 3]) -> Result<bool> {
        match feature {
            Feature::Ore { .. } => super::ore::place(self, feature, origin),
            Feature::RandomSelector { entries, default } => {
                for (chance, placed) in entries {
                    if self.random.next_float() < *chance {
                        return self.place_placed(placed, origin);
                    }
                }
                self.place_placed(default, origin)
            }
            Feature::SimpleRandomSelector(children) => {
                let index = self.random.next_int(children.len() as i32) as usize;
                self.place_placed(&children[index], origin)
            }
            Feature::RandomBooleanSelector(a, b) => {
                let choice = self.random.next(1) != 0;
                self.place_placed(if choice { a } else { b }, origin)
            }
            Feature::Tree(tree) => super::tree::place(self, tree, origin),
            Feature::RandomPatch { tries, xz, y, placed } => {
                let (j, k) = (xz + 1, y + 1);
                let mut any = 0;
                for _ in 0..*tries {
                    let random = &mut self.random;
                    let dx = random.next_int(j) - random.next_int(j);
                    let dy = random.next_int(k) - random.next_int(k);
                    let dz = random.next_int(j) - random.next_int(j);
                    if self.place_placed(placed, [origin[0] + dx, origin[1] + dy, origin[2] + dz])? {
                        any += 1;
                    }
                }
                Ok(any > 0)
            }
            Feature::SimpleBlock { provider, survival, schedule_tick, water_fluid } => {
                super::simple::simple_block(self, provider, survival, *schedule_tick, *water_fluid, origin)
            }
            Feature::Vegetation(vegetation) => super::vegetation::place(self, vegetation, origin),
            Feature::Patch(patch) => self.place_patch(patch, origin),
            Feature::Java(index) => {
                let (lo, hi) = self.random.state();
                let [have, bits] = super::random::gaussian_words(self.random.gaussian);
                let mut args = [*index as i64, origin[0] as i64, origin[1] as i64, origin[2] as i64, lo, hi, self.region.slot, have, bits];
                let placed = self.region.sync_call(super::region::OP_PLACE_JAVA, &mut args)?;
                self.random.set_state(args[4], args[5]);
                self.random.gaussian = super::random::gaussian_from(args[7], args[8]);
                Ok(placed == 1)
            }
            _ => super::simple::place(self, feature, origin),
        }
    }
}

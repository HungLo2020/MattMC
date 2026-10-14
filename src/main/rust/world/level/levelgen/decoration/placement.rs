//! `PlacedFeature.placeWithContext`: each modifier's `getPositions` runs as its
//! stream's `flatMap` reaches it, so draws interleave depth first with the
//! configured feature's own draws, exactly as Java's lazy streams order them.
use super::program::{Anchor, Feature, Modifier, Program};
use super::random::WorldgenRandom;
use super::region::{Region, Result};

pub(crate) struct Placement<'p, 'r, 'a> {
    pub(crate) program: &'p Program,
    pub(crate) region: &'r mut Region<'a>,
    pub(crate) random: WorldgenRandom,
    placed: bool,
}

/// Height providers whose range is empty in this context: Java warns there, so
/// the whole placement stays in Java. Checked before any draw.
pub(crate) fn heights_resolve(program: &Program, min_gen_y: i32, gen_depth: i32) -> bool {
    let resolve = |a: Anchor| a.resolve(min_gen_y, gen_depth);
    program.modifiers.iter().all(|m| match *m {
        Modifier::HeightUniform(min, max) | Modifier::HeightTrapezoid(min, max, _) => resolve(min) <= resolve(max),
        Modifier::HeightBiasedToBottom(min, max, inner) => {
            resolve(max).wrapping_sub(resolve(min)).wrapping_sub(inner).wrapping_add(1) > 0
        }
        _ => true,
    })
}

impl<'p, 'r, 'a> Placement<'p, 'r, 'a> {
    pub(crate) fn new(program: &'p Program, region: &'r mut Region<'a>, random: WorldgenRandom) -> Self {
        Self { program, region, random, placed: false }
    }

    /// Returns whether any position placed the feature.
    pub(crate) fn run(mut self, origin: [i32; 3]) -> Result<bool> {
        self.walk(0, origin)?;
        Ok(self.placed)
    }

    fn resolve(&self, anchor: Anchor) -> i32 {
        anchor.resolve(self.region.min_gen_y, self.region.gen_depth)
    }

    fn walk(&mut self, index: usize, pos: [i32; 3]) -> Result<()> {
        let Some(&modifier) = self.program.modifiers.get(index) else {
            // `ConfiguredFeature.place` -> `Feature.place`: ensureCanWrite at the origin first.
            if self.region.ensure_can_write(pos[0], pos[1], pos[2]) && self.place(pos)? {
                self.placed = true;
            }
            return Ok(());
        };
        let next = index + 1;
        match modifier {
            Modifier::CountConstant(count) => {
                for _ in 0..count.max(0) {
                    self.walk(next, pos)?;
                }
            }
            Modifier::CountUniform(min, max) => {
                let count = self.random.between_inclusive(min, max);
                for _ in 0..count.max(0) {
                    self.walk(next, pos)?;
                }
            }
            Modifier::InSquare => {
                let x = self.random.next_int(16).wrapping_add(pos[0]);
                let z = self.random.next_int(16).wrapping_add(pos[2]);
                self.walk(next, [x, pos[1], z])?;
            }
            Modifier::HeightUniform(min, max) => {
                let y = self.random.between_inclusive(self.resolve(min), self.resolve(max));
                self.walk(next, [pos[0], y, pos[2]])?;
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
                self.walk(next, [pos[0], y, pos[2]])?;
            }
            Modifier::HeightBiasedToBottom(min, max, inner) => {
                let (i, j) = (self.resolve(min), self.resolve(max));
                let k = self.random.next_int(j.wrapping_sub(i).wrapping_sub(inner).wrapping_add(1));
                let y = self.random.next_int(k.wrapping_add(inner)).wrapping_add(i);
                self.walk(next, [pos[0], y, pos[2]])?;
            }
            Modifier::Biome => {
                let biome = self.region.biome(pos[0], pos[1], pos[2])?;
                if self.program.biome_allowed(biome) {
                    self.walk(next, pos)?;
                }
            }
            Modifier::Rarity(chance) => {
                if self.random.next_float() < 1.0f32 / chance as f32 {
                    self.walk(next, pos)?;
                }
            }
        }
        Ok(())
    }

    fn place(&mut self, origin: [i32; 3]) -> Result<bool> {
        match &self.program.feature {
            Feature::Ore { .. } => super::ore::place(self, origin),
        }
    }
}

//! `VegetationPatchFeature` and `WaterloggedVegetationPatchFeature`: a ground
//! patch on a cave floor or ceiling, then the nested placed feature on a share
//! of its blocks in `java.util.HashSet` order.
use super::java_set::JavaHashSet;
use super::placement::Placement;
use super::program::{IntProvider, Placed, Predicate, Reader};
use super::region::{Failure, Result};
use super::tree::Provider;
use crate::content::block::{StateFlags, StateId};

#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Patch {
    /// `is(replaceable)`.
    replaceable: usize,
    ground: Provider,
    /// The surface direction: down for floors, up for ceilings.
    direction: [i32; 3],
    depth: IntProvider,
    extra_bottom_block_chance: f32,
    vertical_range: i32,
    vegetation_chance: f32,
    xz_radius: IntProvider,
    extra_edge_column_chance: f32,
    /// The block one step along the surface direction is sturdy facing back.
    surface_sturdy: Predicate,
    pub(crate) vegetation: Placed,
    waterlogged: Option<Box<Waterlogged>>,
}

/// `WaterloggedVegetationPatchFeature`'s extras.
#[derive(Clone, Debug, PartialEq)]
struct Waterlogged {
    water: u32,
    /// North, east, south, west, down: the neighbour's face towards the block is sturdy.
    covered: Vec<Predicate>,
}

impl Patch {
    pub(crate) fn read(reader: &mut Reader, depth: usize) -> Option<Self> {
        let waterlogged = reader.flag()?;
        let replaceable = reader.set()?;
        let ground = Provider::read(reader)?;
        let mut states = Vec::new();
        ground.states(&mut states);
        reader.record_writes(states);
        let direction = if reader.flag()? { [0, 1, 0] } else { [0, -1, 0] };
        let patch_depth = IntProvider::read(reader)?;
        let extra_bottom_block_chance = reader.float()?;
        let vertical_range = reader.next()?;
        let vegetation_chance = reader.float()?;
        let xz_radius = IntProvider::read(reader)?;
        let extra_edge_column_chance = reader.float()?;
        let surface_sturdy = Predicate::read_checked(reader)?;
        let waterlogged = if waterlogged {
            let water = reader.write_state()?;
            let covered = (0..5).map(|_| Predicate::read_checked(reader)).collect::<Option<Vec<_>>>()?;
            // Waterlogged states the vegetation may be turned into.
            let count = reader.count(1 << 16)?;
            for _ in 0..count {
                reader.write_state()?;
            }
            Some(Box::new(Waterlogged { water, covered }))
        } else {
            None
        };
        let vegetation = Placed::read(reader, depth + 1, false)?;
        Some(Self {
            replaceable, ground, direction, depth: patch_depth, extra_bottom_block_chance, vertical_range, vegetation_chance,
            xz_radius, extra_edge_column_chance, surface_sturdy, vegetation, waterlogged,
        })
    }

    pub(crate) fn kind(&self) -> i32 {
        if self.waterlogged.is_some() { 48 } else { 47 }
    }

    pub(crate) fn sets(&self, out: &mut Vec<usize>) {
        out.push(self.replaceable);
        self.vegetation.sets(out);
    }
}

fn add(pos: [i32; 3], by: [i32; 3]) -> [i32; 3] {
    [pos[0] + by[0], pos[1] + by[1], pos[2] + by[2]]
}

fn is_air(placement: &Placement, pos: [i32; 3]) -> Result<bool> {
    let state = placement.region.level_block_state(pos[0], pos[1], pos[2])?;
    Ok(placement.region.registry().flags(StateId(state as u16)).contains(StateFlags::AIR))
}

impl<'p, 'r, 'a> Placement<'p, 'r, 'a> {
    pub(crate) fn place_patch(&mut self, patch: &'p Patch, origin: [i32; 3]) -> Result<bool> {
        let i = patch.xz_radius.sample(self) + 1;
        let j = patch.xz_radius.sample(self) + 1;
        let set = self.ground_patch(patch, origin, i, j)?;
        let back = [-patch.direction[0], -patch.direction[1], -patch.direction[2]];
        for pos in set.to_vec() {
            if patch.vegetation_chance > 0.0 && self.random.next_float() < patch.vegetation_chance {
                match &patch.waterlogged {
                    None => {
                        self.place_placed(&patch.vegetation, add(pos, back))?;
                    }
                    Some(_) => {
                        if self.place_placed(&patch.vegetation, add(add(pos, [0, -1, 0]), back))? {
                            self.waterlog(pos)?;
                        }
                    }
                }
            }
        }
        Ok(!set.is_empty())
    }

    /// `hasProperty(WATERLOGGED) && !waterlogged`: set it, `setBlock(…, 2)`.
    fn waterlog(&mut self, pos: [i32; 3]) -> Result<()> {
        let properties = super::tree::properties().ok_or(Failure::Unbound)?;
        let state = StateId(self.region.level_block_state(pos[0], pos[1], pos[2])? as u16);
        let registry = self.region.registry();
        if registry.value(state, properties.waterlogged) == Some(properties.waterlogged_false) {
            let wet = registry.with_value(state, properties.waterlogged, properties.waterlogged_true).ok_or(Failure::Unbound)?;
            self.region.set_block(pos[0], pos[1], pos[2], wet.0 as u32, 2)?;
        }
        Ok(())
    }

    fn ground_patch(&mut self, patch: &'p Patch, origin: [i32; 3], i: i32, j: i32) -> Result<JavaHashSet> {
        let direction = patch.direction;
        let back = [-direction[0], -direction[1], -direction[2]];
        let mut set = JavaHashSet::new();
        for k in -i..=i {
            let x_edge = k == -i || k == i;
            for l in -j..=j {
                let z_edge = l == -j || l == j;
                let corner = x_edge && z_edge;
                let edge = (x_edge || z_edge) && !corner;
                if corner {
                    continue;
                }
                if edge && !(patch.extra_edge_column_chance != 0.0 && !(self.random.next_float() > patch.extra_edge_column_chance)) {
                    continue;
                }
                let mut pos = add(origin, [k, 0, l]);
                let mut m = 0;
                while is_air(self, pos)? && m < patch.vertical_range {
                    pos = add(pos, direction);
                    m += 1;
                }
                let mut m = 0;
                while !is_air(self, pos)? && m < patch.vertical_range {
                    pos = add(pos, back);
                    m += 1;
                }
                let surface = add(pos, direction);
                if is_air(self, pos)? && self.test(&patch.surface_sturdy, pos)? {
                    let mut n = patch.depth.sample(self);
                    if patch.extra_bottom_block_chance > 0.0 && self.random.next_float() < patch.extra_bottom_block_chance {
                        n += 1;
                    }
                    if self.place_ground(patch, surface, n)? {
                        set.insert(surface);
                    }
                }
            }
        }
        let Some(waterlogged) = &patch.waterlogged else {
            return Ok(set);
        };
        let mut inner = JavaHashSet::new();
        for pos in set.to_vec() {
            let mut exposed = false;
            for covered in &waterlogged.covered {
                if !self.test(covered, pos)? {
                    exposed = true;
                    break;
                }
            }
            if !exposed {
                inner.insert(pos);
            }
        }
        for pos in inner.to_vec() {
            self.region.set_block(pos[0], pos[1], pos[2], waterlogged.water, 2)?;
        }
        Ok(inner)
    }

    fn place_ground(&mut self, patch: &'p Patch, mut pos: [i32; 3], n: i32) -> Result<bool> {
        for j in 0..n {
            let state = patch.ground.state(self);
            let there = self.region.level_block_state(pos[0], pos[1], pos[2])?;
            let registry = self.region.registry();
            if registry.block_of(StateId(state as u16)) != registry.block_of(StateId(there as u16)) {
                if !self.program.in_set(patch.replaceable, there) {
                    return Ok(j != 0);
                }
                self.region.set_block(pos[0], pos[1], pos[2], state, 2)?;
                pos = add(pos, patch.direction);
            }
        }
        Ok(true)
    }
}

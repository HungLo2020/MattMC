//! `TreeDecorator`s with Rust implementations. The context's logs, leaves and
//! roots are copies of the tree's hash sets, stably sorted by Y as
//! `TreeDecorator.Context` sorts them; decorations go through the fourth setter.
use super::{Built, Provider, Setter, Tree, DIRECTIONS, HORIZONTAL};
use crate::content::block::{StateFlags, StateId};
use crate::world::level::levelgen::decoration::program::Reader;
use crate::world::level::levelgen::decoration::region::Result;
use crate::world::level::levelgen::decoration::write::FINAL_ORDINALS;

#[derive(Clone, Debug, PartialEq)]
pub(crate) enum Decorator {
    TrunkVine,
    LeaveVine { probability: f32 },
    /// Cocoa states by `age * 4 + horizontal facing` (north, east, south, west).
    Cocoa { probability: f32, states: Vec<u32> },
    AlterGround { provider: Provider },
    AttachedToLeaves { probability: f32, exclusion_xz: i32, exclusion_y: i32, provider: Provider, required_empty: i32, directions: Vec<usize> },
    PlaceOnGround { tries: i32, radius: i32, height: i32, provider: Provider },
    CreakingHeart { probability: f32, state: u32 },
    /// `BeehiveDecorator`: the bee nest facing south.
    Beehive { probability: f32, nest: u32 },
    /// `PaleMossDecorator`: hanging moss (not tip, tip) and the Java step that
    /// places the registry's pale moss patch.
    PaleMoss { leaves: f32, trunk: f32, ground: f32, moss: [u32; 2], patch_step: i64 },
}

impl Decorator {
    pub(crate) fn read(reader: &mut Reader) -> Option<Self> {
        let kind = reader.next()?;
        let length = reader.count(1 << 12)?;
        let end = reader.at + length;
        let decorator = match kind {
            1 => Decorator::TrunkVine,
            2 => Decorator::LeaveVine { probability: reader.float()? },
            3 => Decorator::Cocoa { probability: reader.float()?, states: (0..12).map(|_| reader.state()).collect::<Option<_>>()? },
            4 => Decorator::AlterGround { provider: Provider::read(reader)? },
            5 => {
                let probability = reader.float()?;
                let exclusion_xz = reader.next()?;
                let exclusion_y = reader.next()?;
                let provider = Provider::read(reader)?;
                let required_empty = reader.next()?;
                let count = reader.count(6).filter(|&n| n > 0)?;
                let directions = (0..count).map(|_| reader.count(5)).collect::<Option<_>>()?;
                Decorator::AttachedToLeaves { probability, exclusion_xz, exclusion_y, provider, required_empty, directions }
            }
            6 => Decorator::PlaceOnGround { tries: reader.next()?, radius: reader.next()?, height: reader.next()?, provider: Provider::read(reader)? },
            7 => Decorator::CreakingHeart { probability: reader.float()?, state: reader.state()? },
            8 => Decorator::Beehive { probability: reader.float()?, nest: reader.state()? },
            9 => Decorator::PaleMoss {
                leaves: reader.float()?,
                trunk: reader.float()?,
                ground: reader.float()?,
                moss: [reader.state()?, reader.state()?],
                patch_step: reader.count(1 << 16)? as i64,
            },
            _ => return None,
        };
        (reader.at == end).then_some(decorator)
    }

    pub(crate) fn states(&self, out: &mut Vec<u32>) {
        match self {
            Decorator::TrunkVine | Decorator::LeaveVine { .. } => {}
            Decorator::Cocoa { states, .. } => out.extend(states),
            Decorator::AlterGround { provider }
            | Decorator::AttachedToLeaves { provider, .. }
            | Decorator::PlaceOnGround { provider, .. } => provider.states(out),
            Decorator::CreakingHeart { state, .. } => out.push(*state),
            Decorator::Beehive { nest, .. } => out.push(*nest),
            Decorator::PaleMoss { moss, .. } => out.extend(moss),
        }
    }

    pub(crate) fn place(&self, tree: &mut Tree, context: &mut Context) -> Result<()> {
        match self {
            Decorator::TrunkVine => {
                for &pos in &context.logs {
                    for (side, vine) in VINE_SIDES {
                        if tree.placement.random.next_int(3) > 0 {
                            let at = offset(pos, side);
                            if tree.is_air(at)? {
                                tree.set(Setter::Decoration, at, tree.config.vines[vine])?;
                            }
                        }
                    }
                }
            }
            Decorator::LeaveVine { probability } => {
                for &pos in &context.leaves {
                    for (side, vine) in VINE_SIDES {
                        if tree.placement.random.next_float() < *probability {
                            let at = offset(pos, side);
                            if tree.is_air(at)? {
                                hanging_vine(tree, at, tree.config.vines[vine])?;
                            }
                        }
                    }
                }
            }
            Decorator::Cocoa { probability, states } => {
                if tree.placement.random.next_float() < *probability {
                    if let Some(first) = context.logs.first() {
                        let base = first[1];
                        for &pos in context.logs.iter().filter(|p| p[1] - base <= 2) {
                            for (facing, [dx, dz]) in HORIZONTAL.iter().enumerate() {
                                if tree.placement.random.next_float() <= 0.25 {
                                    // The opposite direction's step.
                                    let at = [pos[0] - dx, pos[1], pos[2] - dz];
                                    if tree.is_air(at)? {
                                        let age = tree.placement.random.next_int(3) as usize;
                                        tree.set(Setter::Decoration, at, states[age * 4 + facing])?;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Decorator::AlterGround { provider } => {
                let list = context.lowest_trunk_or_root();
                if let Some(first) = list.first() {
                    let y = first[1];
                    for pos in list.iter().copied().filter(|p| p[1] == y) {
                        for [dx, dz] in [[-1, -1], [2, -1], [-1, 2], [2, 2]] {
                            place_circle(tree, provider, [pos[0] + dx, pos[1], pos[2] + dz])?;
                        }
                        for _ in 0..5 {
                            let j = tree.placement.random.next_int(64);
                            let (k, l) = (j % 8, j / 8);
                            if k == 0 || k == 7 || l == 0 || l == 7 {
                                place_circle(tree, provider, [pos[0] - 3 + k, pos[1], pos[2] - 3 + l])?;
                            }
                        }
                    }
                }
            }
            Decorator::AttachedToLeaves { probability, exclusion_xz, exclusion_y, provider, required_empty, directions } => {
                let mut excluded = std::collections::HashSet::new();
                let leaves = shuffled(tree, context.leaves.clone());
                for pos in leaves {
                    let direction = DIRECTIONS[directions[tree.placement.random.next_int(directions.len() as i32) as usize]];
                    let at = offset(pos, direction);
                    if !excluded.contains(&at) && tree.placement.random.next_float() < *probability && has_empty(tree, pos, direction, *required_empty)? {
                        for x in at[0] - exclusion_xz..=at[0] + exclusion_xz {
                            for y in at[1] - exclusion_y..=at[1] + exclusion_y {
                                for z in at[2] - exclusion_xz..=at[2] + exclusion_xz {
                                    excluded.insert([x, y, z]);
                                }
                            }
                        }
                        let state = provider.state(tree.placement);
                        tree.set(Setter::Decoration, at, state)?;
                    }
                }
            }
            Decorator::PlaceOnGround { tries, radius, height, provider } => {
                let list = context.lowest_trunk_or_root();
                if let Some(&first) = list.first() {
                    let y = first[1];
                    let (mut min_x, mut max_x, mut min_z, mut max_z) = (first[0], first[0], first[2], first[2]);
                    for p in list.iter().filter(|p| p[1] == y) {
                        min_x = min_x.min(p[0]);
                        max_x = max_x.max(p[0]);
                        min_z = min_z.min(p[2]);
                        max_z = max_z.max(p[2]);
                    }
                    let (min, max) = ([min_x - radius, y - height, min_z - radius], [max_x + radius, y + height, max_z + radius]);
                    for _ in 0..*tries {
                        let x = tree.placement.random.between_inclusive(min[0], max[0]);
                        let py = tree.placement.random.between_inclusive(min[1], max[1]);
                        let z = tree.placement.random.between_inclusive(min[2], max[2]);
                        place_on_ground(tree, provider, [x, py, z])?;
                    }
                }
            }
            Decorator::CreakingHeart { probability, state } => {
                if !context.logs.is_empty() && tree.placement.random.next_float() < *probability {
                    let logs = shuffled(tree, context.logs.clone());
                    for pos in logs {
                        let mut surrounded = true;
                        for direction in DIRECTIONS {
                            if !tree.is(tree.config.sets.logs, offset(pos, direction))? {
                                surrounded = false;
                                break;
                            }
                        }
                        if surrounded {
                            tree.set(Setter::Decoration, pos, *state)?;
                            break;
                        }
                    }
                }
            }
            Decorator::Beehive { probability, nest } => beehive(tree, context, *probability, *nest)?,
            Decorator::PaleMoss { leaves, trunk, ground, moss, patch_step } => {
                let list = shuffled(tree, context.logs.clone());
                if let Some(&first) = list.first() {
                    let mut lowest = first;
                    for &pos in &list {
                        if pos[1] < lowest[1] {
                            lowest = pos;
                        }
                    }
                    if tree.placement.random.next_float() < *ground {
                        tree.placement.java_step(*patch_step, [lowest[0], lowest[1] + 1, lowest[2]])?;
                    }
                    for (positions, probability) in [(context.logs.clone(), *trunk), (context.leaves.clone(), *leaves)] {
                        for pos in positions {
                            if tree.placement.random.next_float() < probability {
                                let below = [pos[0], pos[1] - 1, pos[2]];
                                if tree.is_air(below)? {
                                    moss_hanger(tree, below, *moss)?;
                                }
                            }
                        }
                    }
                }
            }
        }
        Ok(())
    }
}

/// `PaleMossDecorator.addMossHanger`.
fn moss_hanger(tree: &mut Tree, mut pos: [i32; 3], moss: [u32; 2]) -> Result<()> {
    while tree.is_air([pos[0], pos[1] - 1, pos[2]])? && !(tree.placement.random.next_float() < 0.5) {
        tree.set(Setter::Decoration, pos, moss[0])?;
        pos[1] -= 1;
    }
    tree.set(Setter::Decoration, pos, moss[1])?;
    Ok(())
}

/// `BeehiveDecorator.place`. The nest's block entity and bees stay Java's: the
/// draws happen here and Java stores the bees through a sync upcall.
fn beehive(tree: &mut Tree, context: &Context, probability: f32, nest: u32) -> Result<()> {
    let (Some(&first_log), Some(&last_log)) = (context.logs.first(), context.logs.last()) else {
        return Ok(());
    };
    if tree.placement.random.next_float() >= probability {
        return Ok(());
    }
    let y = match context.leaves.first() {
        Some(leaf) => (leaf[1] - 1).max(first_log[1] + 1),
        None => (first_log[1] + 1 + tree.placement.random.next_int(3)).min(last_log[1]),
    };
    // `SPAWN_DIRECTIONS`: horizontal directions except north (the opposite of the south facing).
    let mut candidates: Vec<[i32; 3]> = context.logs.iter().filter(|p| p[1] == y)
        .flat_map(|&p| [[1, 0], [0, 1], [-1, 0]].map(|[dx, dz]| [p[0] + dx, p[1], p[2] + dz]))
        .collect();
    if candidates.is_empty() {
        return Ok(());
    }
    candidates = shuffled(tree, candidates);
    for pos in candidates {
        if tree.is_air(pos)? && tree.is_air([pos[0], pos[1], pos[2] + 1])? {
            tree.set(Setter::Decoration, pos, nest)?;
            let present = tree.placement.region.sync_call(crate::world::level::levelgen::decoration::region::OP_BEEHIVE_PRESENT,
                &mut [pos[0] as i64, pos[1] as i64, pos[2] as i64])?;
            if present == 1 {
                let count = 2 + tree.placement.random.next_int(2);
                let mut args = vec![pos[0] as i64, pos[1] as i64, pos[2] as i64];
                for _ in 0..count {
                    args.push(tree.placement.random.next_int(599) as i64);
                }
                tree.placement.region.sync_call(crate::world::level::levelgen::decoration::region::OP_STORE_BEES, &mut args)?;
            }
            break;
        }
    }
    Ok(())
}

/// `TreeDecorator.Context`'s lists.
pub(crate) struct Context {
    logs: Vec<[i32; 3]>,
    leaves: Vec<[i32; 3]>,
    roots: Vec<[i32; 3]>,
}

impl Context {
    pub(crate) fn new(built: &Built) -> Self {
        let sorted = |set: &super::JavaHashSet| {
            let mut list = set.to_vec();
            list.sort_by_key(|p| p[1]);
            list
        };
        Self { logs: sorted(&built.logs), leaves: sorted(&built.foliage), roots: sorted(&built.roots) }
    }

    /// `TreeFeature.getLowestTrunkOrRootOfTree`.
    fn lowest_trunk_or_root(&self) -> Vec<[i32; 3]> {
        if self.roots.is_empty() {
            self.logs.clone()
        } else if !self.logs.is_empty() && self.roots[0][1] == self.logs[0][1] {
            self.logs.iter().chain(self.roots.iter()).copied().collect()
        } else {
            self.roots.clone()
        }
    }
}

/// (side offset, vine index): west gets EAST, east WEST, north SOUTH, south NORTH.
const VINE_SIDES: [([i32; 3], usize); 4] = [([-1, 0, 0], 0), ([1, 0, 0], 1), ([0, 0, -1], 2), ([0, 0, 1], 3)];

fn offset(pos: [i32; 3], d: [i32; 3]) -> [i32; 3] {
    [pos[0] + d[0], pos[1] + d[1], pos[2] + d[2]]
}

/// `LeaveVineDecorator.addHangingVine`.
fn hanging_vine(tree: &mut Tree, pos: [i32; 3], vine: u32) -> Result<()> {
    tree.set(Setter::Decoration, pos, vine)?;
    let mut remaining = 4;
    let mut below = [pos[0], pos[1] - 1, pos[2]];
    while tree.is_air(below)? && remaining > 0 {
        tree.set(Setter::Decoration, below, vine)?;
        below[1] -= 1;
        remaining -= 1;
    }
    Ok(())
}

/// `Util.shuffledCopy`/`Util.shuffle`.
fn shuffled(tree: &mut Tree, mut list: Vec<[i32; 3]>) -> Vec<[i32; 3]> {
    let mut j = list.len();
    while j > 1 {
        let k = tree.placement.random.next_int(j as i32) as usize;
        list.swap(j - 1, k);
        j -= 1;
    }
    list
}

fn has_empty(tree: &Tree, pos: [i32; 3], direction: [i32; 3], required: i32) -> Result<bool> {
    for i in 1..=required {
        if !tree.is_air([pos[0] + direction[0] * i, pos[1] + direction[1] * i, pos[2] + direction[2] * i])? {
            return Ok(false);
        }
    }
    Ok(true)
}

/// `AlterGroundDecorator.placeCircle`.
fn place_circle(tree: &mut Tree, provider: &Provider, pos: [i32; 3]) -> Result<()> {
    for i in -2..=2 {
        for j in -2..=2 {
            if i32::abs(i) != 2 || i32::abs(j) != 2 {
                place_block_at(tree, provider, [pos[0] + i, pos[1], pos[2] + j])?;
            }
        }
    }
    Ok(())
}

/// `AlterGroundDecorator.placeBlockAt`.
fn place_block_at(tree: &mut Tree, provider: &Provider, pos: [i32; 3]) -> Result<()> {
    for i in (-3..=2).rev() {
        let at = [pos[0], pos[1] + i, pos[2]];
        if tree.is(tree.config.sets.dirt, at)? {
            let state = provider.state(tree.placement);
            tree.set(Setter::Decoration, at, state)?;
            break;
        }
        if !tree.is_air(at)? && i < 0 {
            break;
        }
    }
    Ok(())
}

/// `PlaceOnGroundDecorator.attemptToPlaceBlockAbove`.
fn place_on_ground(tree: &mut Tree, provider: &Provider, pos: [i32; 3]) -> Result<()> {
    let above = [pos[0], pos[1] + 1, pos[2]];
    if !(tree.is_air(above)? || tree.is(tree.config.sets.vine, above)?) {
        return Ok(());
    }
    let state = tree.state(pos)?;
    if !tree.placement.region.registry().flags(StateId(state as u16)).contains(StateFlags::SOLID_RENDER) {
        return Ok(());
    }
    // `getHeightmapPos(MOTION_BLOCKING_NO_LEAVES, pos).getY() <= above.getY()`.
    let kind = FINAL_ORDINALS.iter().position(|&o| o == 5).expect("MOTION_BLOCKING_NO_LEAVES is final");
    if tree.placement.region.final_height_at(kind, pos[0], pos[2])? <= above[1] {
        let state = provider.state(tree.placement);
        tree.set(Setter::Decoration, above, state)?;
    }
    Ok(())
}

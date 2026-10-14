//! `TreeFeature` with its trunk, foliage and root placers, feature sizes,
//! state providers and decorators. Logs, leaves, roots and decorations go
//! through `WorldGenRegion.setBlock(pos, state, 19)` and are recorded in
//! Java-order hash sets; `updateLeaves` runs here and the closing
//! `StructureTemplate.updateShapeAtEdge` runs in Java through a sync upcall.
mod decorator;
mod foliage;
mod trunk;

use super::java_set::JavaHashSet;
use super::placement::Placement;
use super::program::{IntProvider, Reader};
use super::region::{Failure, Result};
use crate::content::block::{PropertyId, StateFlags, StateId};

pub(crate) use decorator::Decorator;
pub(crate) use foliage::Foliage;
pub(crate) use trunk::Trunk;

/// `BlockStateProvider`s: simple (one entry) and weighted lists, optionally
/// wrapped in a `RandomizedIntStateProvider`.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Provider {
    entries: Vec<(i32, u32)>,
    total: i32,
    randomized: Option<Box<Randomized>>,
}

/// `RandomizedIntStateProvider`: per source state, its states by property
/// value from `min` (empty when the state lacks the property).
#[derive(Clone, Debug, PartialEq)]
struct Randomized {
    values: IntProvider,
    tables: Vec<(u32, i32, Vec<u32>)>,
}

impl Provider {
    pub(crate) fn read(reader: &mut Reader) -> Option<Self> {
        let kind = reader.next()?;
        if kind == 3 {
            let values = IntProvider::read(reader)?;
            let mut provider = Self::read(reader).filter(|p| p.randomized.is_none())?;
            let count = reader.count(256)?;
            let mut tables = Vec::with_capacity(count);
            for _ in 0..count {
                let (state, min, n) = (reader.state()?, reader.next()?, reader.count(256)?);
                tables.push((state, min, (0..n).map(|_| reader.state()).collect::<Option<Vec<_>>>()?));
            }
            provider.randomized = Some(Box::new(Randomized { values, tables }));
            return Some(provider);
        }
        let count = reader.count(256).filter(|&n| n > 0)?;
        let mut entries = Vec::with_capacity(count);
        for _ in 0..count {
            entries.push((reader.next().filter(|&w| w > 0)?, reader.state()?));
        }
        let total = entries.iter().try_fold(0i32, |t, &(w, _)| t.checked_add(w))?;
        match kind {
            1 if count == 1 => {}
            2 => {}
            _ => return None,
        }
        Some(Self { entries: if kind == 1 { vec![(0, entries[0].1)] } else { entries }, total, randomized: None })
    }

    /// `getState(random, pos)`: simple providers draw nothing; weighted lists
    /// draw `nextInt(totalWeight)` and walk the entries.
    pub(crate) fn state(&self, placement: &mut Placement) -> u32 {
        let base = self.base_state(placement);
        let Some(randomized) = &self.randomized else {
            return base;
        };
        match randomized.tables.iter().find(|(state, ..)| *state == base) {
            Some((_, min, states)) if !states.is_empty() => {
                let value = randomized.values.sample(placement);
                // The encoder checked every sampled value lies in the property's range.
                states.get(value.wrapping_sub(*min) as usize).copied().unwrap_or(base)
            }
            _ => base,
        }
    }

    fn base_state(&self, placement: &mut Placement) -> u32 {
        if self.entries.len() == 1 && self.entries[0].0 == 0 {
            return self.entries[0].1;
        }
        let mut pick = placement.random.next_int(self.total);
        for &(weight, state) in &self.entries {
            pick -= weight;
            if pick < 0 {
                return state;
            }
        }
        unreachable!("weights sum to the total")
    }

    pub(crate) fn states(&self, out: &mut Vec<u32>) {
        out.extend(self.entries.iter().map(|&(_, state)| state));
        if let Some(randomized) = &self.randomized {
            out.extend(randomized.tables.iter().flat_map(|(_, _, states)| states.iter().copied()));
        }
    }
}

/// `FeatureSize`.
#[derive(Clone, Debug, PartialEq)]
pub(crate) enum Size {
    Two { min_clipped: Option<i32>, limit: i32, lower: i32, upper: i32 },
    Three { min_clipped: Option<i32>, limit: i32, upper_limit: i32, lower: i32, middle: i32, upper: i32 },
}

impl Size {
    fn read(reader: &mut Reader) -> Option<Self> {
        let kind = reader.next()?;
        let clipped = reader.next()?;
        let min_clipped = (clipped >= 0).then_some(clipped);
        let w = reader.take(5)?;
        match kind {
            1 => Some(Size::Two { min_clipped, limit: w[0], lower: w[1], upper: w[2] }),
            2 => Some(Size::Three { min_clipped, limit: w[0], upper_limit: w[1], lower: w[2], middle: w[3], upper: w[4] }),
            _ => None,
        }
    }

    fn min_clipped(&self) -> Option<i32> {
        match *self {
            Size::Two { min_clipped, .. } | Size::Three { min_clipped, .. } => min_clipped,
        }
    }

    /// `getSizeAtHeight(height, y)`.
    fn at(&self, height: i32, y: i32) -> i32 {
        match *self {
            Size::Two { limit, lower, upper, .. } => {
                if y < limit { lower } else { upper }
            }
            Size::Three { limit, upper_limit, lower, middle, upper, .. } => {
                if y < limit {
                    lower
                } else if y >= height - upper_limit {
                    upper
                } else {
                    middle
                }
            }
        }
    }
}

/// `MangroveRootPlacer` with its `MangroveRootPlacement` and optional above-root placement.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Roots {
    trunk_offset_y: IntProvider,
    provider: Provider,
    above: Option<(Provider, f32)>,
    grow_through: usize,
    muddy_in: usize,
    muddy_provider: Provider,
    max_width: i32,
    max_length: i32,
    skew_chance: f32,
}

/// State sets every tree program carries (tags resolved by Java).
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Sets {
    /// `isAir() || is(REPLACEABLE_BY_TREES)`.
    pub(crate) valid_tree: usize,
    pub(crate) logs: usize,
    pub(crate) leaves: usize,
    pub(crate) vine: usize,
    /// `isDirt && !GRASS_BLOCK && !MYCELIUM`.
    pub(crate) plain_dirt: usize,
    pub(crate) dirt: usize,
    /// `getFluidState().isSourceOfType(WATER)`.
    pub(crate) water_source: usize,
    /// `getFluidState().is(FluidTags.WATER)`.
    pub(crate) water_fluid: usize,
}

#[derive(Clone, Debug, PartialEq)]
pub(crate) struct TreeConfig {
    pub(crate) trunk_provider: Provider,
    pub(crate) foliage_provider: Provider,
    pub(crate) dirt_provider: Provider,
    pub(crate) trunk: Trunk,
    pub(crate) foliage: Foliage,
    size: Size,
    roots: Option<Roots>,
    decorators: Vec<Decorator>,
    ignore_vines: bool,
    pub(crate) force_dirt: bool,
    pub(crate) sets: Sets,
    /// `Blocks.VINE` with EAST, WEST, SOUTH, NORTH true (the `placeVine` states).
    pub(crate) vines: [u32; 4],
}

impl TreeConfig {
    pub(crate) fn read(reader: &mut Reader) -> Option<Self> {
        let trunk_provider = Provider::read(reader)?;
        let foliage_provider = Provider::read(reader)?;
        let dirt_provider = Provider::read(reader)?;
        let trunk = Trunk::read(reader)?;
        let foliage = Foliage::read(reader)?;
        let size = Size::read(reader)?;
        let roots = match reader.next()? {
            0 => None,
            1 => Some(Roots {
                trunk_offset_y: IntProvider::read(reader)?,
                provider: Provider::read(reader)?,
                above: if reader.flag()? { Some((Provider::read(reader)?, reader.float()?)) } else { None },
                grow_through: reader.index()?,
                muddy_in: reader.index()?,
                muddy_provider: Provider::read(reader)?,
                max_width: reader.next()?,
                max_length: reader.next()?,
                skew_chance: reader.float()?,
            }),
            _ => return None,
        };
        let count = reader.count(16)?;
        let decorators = (0..count).map(|_| Decorator::read(reader)).collect::<Option<Vec<_>>>()?;
        let ignore_vines = reader.flag()?;
        let force_dirt = reader.flag()?;
        let sets = Sets {
            valid_tree: reader.index()?,
            logs: reader.index()?,
            leaves: reader.index()?,
            vine: reader.index()?,
            plain_dirt: reader.index()?,
            dirt: reader.index()?,
            water_source: reader.index()?,
            water_fluid: reader.index()?,
        };
        let vines = [reader.state()?, reader.state()?, reader.state()?, reader.state()?];
        Some(Self { trunk_provider, foliage_provider, dirt_provider, trunk, foliage, size, roots, decorators, ignore_vines, force_dirt, sets, vines })
    }

    pub(crate) fn states(&self, out: &mut Vec<u32>) {
        self.trunk_provider.states(out);
        self.foliage_provider.states(out);
        self.dirt_provider.states(out);
        if let Some(roots) = &self.roots {
            roots.provider.states(out);
            roots.muddy_provider.states(out);
            if let Some((provider, _)) = &roots.above {
                provider.states(out);
            }
        }
        out.extend(self.vines);
        self.decorators.iter().for_each(|d| d.states(out));
    }

    pub(crate) fn sets(&self, out: &mut Vec<usize>) {
        let s = &self.sets;
        out.extend([s.valid_tree, s.logs, s.leaves, s.vine, s.plain_dirt, s.dirt, s.water_source, s.water_fluid]);
        self.trunk.sets(out);
        if let Some(roots) = &self.roots {
            out.extend([roots.grow_through, roots.muddy_in]);
        }
    }
}

/// Registry properties trees edit, found by name and value names.
pub(crate) struct Properties {
    pub(crate) axis: PropertyId,
    pub(crate) distance: PropertyId,
    pub(crate) persistent: PropertyId,
    pub(crate) waterlogged: PropertyId,
    pub(crate) persistent_true: u16,
    pub(crate) waterlogged_true: u16,
    pub(crate) waterlogged_false: u16,
}

pub(crate) fn properties() -> Option<&'static Properties> {
    static PROPERTIES: std::sync::OnceLock<Option<Properties>> = std::sync::OnceLock::new();
    PROPERTIES
        .get_or_init(|| {
            let registry = crate::content::block::installed()?;
            let find = |name: &str, values: &[&str]| {
                registry.properties().iter().position(|p| p.name() == name && p.values().iter().map(String::as_str).eq(values.iter().copied()))
                    .map(|i| PropertyId(i as u16))
            };
            let index = |property: PropertyId, value: &str| {
                registry.property(property).values().iter().position(|v| v == value).map(|i| i as u16)
            };
            let booleans = ["true", "false"];
            let persistent = find("persistent", &booleans).or_else(|| find("persistent", &["false", "true"]))?;
            let waterlogged = find("waterlogged", &booleans).or_else(|| find("waterlogged", &["false", "true"]))?;
            Some(Properties {
                axis: find("axis", &["x", "y", "z"])?,
                distance: find("distance", &["1", "2", "3", "4", "5", "6", "7"])?,
                persistent,
                waterlogged,
                persistent_true: index(persistent, "true")?,
                waterlogged_true: index(waterlogged, "true")?,
                waterlogged_false: index(waterlogged, "false")?,
            })
        })
        .as_ref()
}

/// One `TreeFeature.place` call's collected positions (`Sets.newHashSet()` each).
pub(crate) struct Built {
    pub(crate) roots: JavaHashSet,
    pub(crate) logs: JavaHashSet,
    pub(crate) foliage: JavaHashSet,
    pub(crate) decorations: JavaHashSet,
}

/// Which setter a write goes through.
#[derive(Clone, Copy, PartialEq)]
pub(crate) enum Setter {
    Root,
    Log,
    Foliage,
    Decoration,
}

pub(crate) struct Tree<'t, 'x, 'p, 'r, 'a> {
    pub(crate) placement: &'x mut Placement<'p, 'r, 'a>,
    pub(crate) config: &'t TreeConfig,
    pub(crate) built: Built,
}

impl Tree<'_, '_, '_, '_, '_> {
    pub(crate) fn state(&self, pos: [i32; 3]) -> Result<u32> {
        self.placement.region.level_block_state(pos[0], pos[1], pos[2])
    }

    pub(crate) fn is(&self, set: usize, pos: [i32; 3]) -> Result<bool> {
        Ok(self.placement.program.in_set(set, self.state(pos)?))
    }

    pub(crate) fn is_air(&self, pos: [i32; 3]) -> Result<bool> {
        let state = self.state(pos)?;
        Ok(self.placement.region.registry().flags(StateId(state as u16)).contains(StateFlags::AIR))
    }

    /// `TreeFeature.validTreePos`.
    pub(crate) fn valid_tree_pos(&self, pos: [i32; 3]) -> Result<bool> {
        self.is(self.config.sets.valid_tree, pos)
    }

    /// The four `TreeFeature` setters: record the position, then `setBlock(pos, state, 19)`.
    pub(crate) fn set(&mut self, setter: Setter, pos: [i32; 3], state: u32) -> Result<()> {
        let set = match setter {
            Setter::Root => &mut self.built.roots,
            Setter::Log => &mut self.built.logs,
            Setter::Foliage => &mut self.built.foliage,
            Setter::Decoration => &mut self.built.decorations,
        };
        set.insert(pos);
        self.placement.region.set_block(pos[0], pos[1], pos[2], state, 19)?;
        Ok(())
    }

    pub(crate) fn with_value(&self, state: u32, property: PropertyId, index: u16) -> Option<u32> {
        self.placement.region.registry().with_value(StateId(state as u16), property, index).map(|s| s.0 as u32)
    }

    pub(crate) fn value(&self, state: u32, property: PropertyId) -> Option<u16> {
        self.placement.region.registry().value(StateId(state as u16), property)
    }

    /// `blockState.trySetValue(RotatedPillarBlock.AXIS, axis)`; 0 x, 1 y, 2 z.
    pub(crate) fn try_axis(&self, state: u32, axis: u16) -> u32 {
        let properties = properties().expect("checked before the placement");
        self.with_value(state, properties.axis, axis).unwrap_or(state)
    }

    /// `TrunkPlacer.setDirtAt`.
    pub(crate) fn set_dirt(&mut self, pos: [i32; 3]) -> Result<()> {
        if self.config.force_dirt || !self.is(self.config.sets.plain_dirt, pos)? {
            let state = self.config.dirt_provider.state(self.placement);
            self.set(Setter::Log, pos, state)?;
        }
        Ok(())
    }
}

/// `TreeFeature.place`.
pub(crate) fn place(placement: &mut Placement, config: &TreeConfig, origin: [i32; 3]) -> Result<bool> {
    if properties().is_none() {
        return Err(Failure::Unbound);
    }
    let mut tree = Tree {
        placement,
        config,
        built: Built { roots: JavaHashSet::new(), logs: JavaHashSet::new(), foliage: JavaHashSet::new(), decorations: JavaHashSet::new() },
    };
    if !do_place(&mut tree, origin)? || (tree.built.logs.is_empty() && tree.built.foliage.is_empty()) {
        return Ok(false);
    }
    if !config.decorators.is_empty() {
        let mut context = decorator::Context::new(&tree.built);
        for decorator in &config.decorators {
            decorator.place(&mut tree, &mut context)?;
        }
    }
    let all: Vec<[i32; 3]> = tree.built.roots.iter().chain(tree.built.logs.iter()).chain(tree.built.foliage.iter())
        .chain(tree.built.decorations.iter()).collect();
    if all.is_empty() {
        return Ok(false);
    }
    let mut min = all[0];
    let mut max = all[0];
    for pos in &all {
        for axis in 0..3 {
            min[axis] = min[axis].min(pos[axis]);
            max[axis] = max[axis].max(pos[axis]);
        }
    }
    let shape = update_leaves(&mut tree, min, max)?;
    tree.placement.region.update_shape_at_edge(3, min, [max[0] - min[0] + 1, max[1] - min[1] + 1, max[2] - min[2] + 1], &shape)?;
    Ok(true)
}

/// `TreeFeature.doPlace`.
fn do_place(tree: &mut Tree, origin: [i32; 3]) -> Result<bool> {
    let config = tree.config;
    let height = config.trunk.tree_height(tree.placement);
    let foliage_height = config.foliage.foliage_height(tree.placement, height);
    let trunk_without_foliage = height - foliage_height;
    let radius = config.foliage.foliage_radius(tree.placement, trunk_without_foliage);
    let trunk_origin = match &config.roots {
        Some(roots) => {
            let offset = roots.trunk_offset_y.sample(tree.placement);
            [origin[0], origin[1] + offset, origin[2]]
        }
        None => origin,
    };
    let low = origin[1].min(trunk_origin[1]);
    let high = origin[1].max(trunk_origin[1]) + height + 1;
    let region = &tree.placement.region;
    let max_y = region.min_y + region.sections as i32 * 16 - 1;
    if low < region.min_y + 1 || high > max_y + 1 {
        return Ok(false);
    }
    let free = max_free_tree_height(tree, height, trunk_origin)?;
    if !(free >= height || config.size.min_clipped().is_some_and(|clipped| free >= clipped)) {
        return Ok(false);
    }
    if let Some(roots) = &config.roots {
        if !place_roots(tree, roots, origin, trunk_origin)? {
            return Ok(false);
        }
    }
    let attachments = config.trunk.place(tree, free, trunk_origin)?;
    for attachment in attachments {
        config.foliage.create(tree, free, attachment, foliage_height, radius)?;
    }
    Ok(true)
}

/// `TreeFeature.getMaxFreeTreeHeight`.
fn max_free_tree_height(tree: &Tree, height: i32, origin: [i32; 3]) -> Result<i32> {
    for y in 0..=height + 1 {
        let size = tree.config.size.at(height, y);
        for dx in -size..=size {
            for dz in -size..=size {
                let pos = [origin[0] + dx, origin[1] + y, origin[2] + dz];
                if !tree.config.trunk.is_free(tree, pos)? || (!tree.config.ignore_vines && tree.is(tree.config.sets.vine, pos)?) {
                    return Ok(y - 2);
                }
            }
        }
    }
    Ok(height)
}

/// `MangroveRootPlacer.placeRoots`.
fn place_roots(tree: &mut Tree, roots: &Roots, origin: [i32; 3], trunk: [i32; 3]) -> Result<bool> {
    let can_place = |tree: &Tree, pos: [i32; 3]| -> Result<bool> {
        Ok(tree.valid_tree_pos(pos)? || tree.is(roots.grow_through, pos)?)
    };
    let mut y = origin[1];
    while y < trunk[1] {
        if !can_place(tree, [origin[0], y, origin[2]])? {
            return Ok(false);
        }
        y += 1;
    }
    let mut list = vec![[trunk[0], trunk[1] - 1, trunk[2]]];
    for [dx, dz] in HORIZONTAL {
        let start = [trunk[0] + dx, trunk[1], trunk[2] + dz];
        let mut branch = Vec::new();
        if !simulate_roots(tree, roots, start, [dx, dz], trunk, &mut branch, 0, &can_place)? {
            return Ok(false);
        }
        list.extend(branch);
        list.push(start);
    }
    for pos in list {
        // `MangroveRootPlacer.placeRoot`.
        if tree.is(roots.muddy_in, pos)? {
            let state = roots.muddy_provider.state(tree.placement);
            let state = waterlogged_for_roots(tree, pos, state)?;
            tree.set(Setter::Root, pos, state)?;
        } else if can_place(tree, pos)? {
            let state = roots.provider.state(tree.placement);
            let state = waterlogged_for_roots(tree, pos, state)?;
            tree.set(Setter::Root, pos, state)?;
            if let Some((provider, chance)) = &roots.above {
                let above = [pos[0], pos[1] + 1, pos[2]];
                if tree.placement.random.next_float() < *chance && tree.is_air(above)? {
                    let state = provider.state(tree.placement);
                    let state = waterlogged_for_roots(tree, above, state)?;
                    tree.set(Setter::Root, above, state)?;
                }
            }
        }
    }
    Ok(true)
}

#[allow(clippy::too_many_arguments)]
fn simulate_roots(
    tree: &mut Tree,
    roots: &Roots,
    pos: [i32; 3],
    direction: [i32; 2],
    trunk: [i32; 3],
    list: &mut Vec<[i32; 3]>,
    depth: i32,
    can_place: &dyn Fn(&Tree, [i32; 3]) -> Result<bool>,
) -> Result<bool> {
    if depth == roots.max_length || list.len() as i32 > roots.max_length {
        return Ok(false);
    }
    for next in potential_roots(tree, roots, pos, direction, trunk) {
        if can_place(tree, next)? {
            list.push(next);
            if !simulate_roots(tree, roots, next, direction, trunk, list, depth + 1, can_place)? {
                return Ok(false);
            }
        }
    }
    Ok(true)
}

/// `MangroveRootPlacer.potentialRootPositions`.
fn potential_roots(tree: &mut Tree, roots: &Roots, pos: [i32; 3], [dx, dz]: [i32; 2], trunk: [i32; 3]) -> Vec<[i32; 3]> {
    let below = [pos[0], pos[1] - 1, pos[2]];
    let ahead = [pos[0] + dx, pos[1], pos[2] + dz];
    let distance = (pos[0] - trunk[0]).abs() + (pos[1] - trunk[1]).abs() + (pos[2] - trunk[2]).abs();
    let random = &mut tree.placement.random;
    if distance > roots.max_width - 3 && distance <= roots.max_width {
        if random.next_float() < roots.skew_chance {
            vec![below, [ahead[0], ahead[1] - 1, ahead[2]]]
        } else {
            vec![below]
        }
    } else if distance > roots.max_width || random.next_float() < roots.skew_chance {
        vec![below]
    } else if random.next(1) != 0 {
        vec![ahead]
    } else {
        vec![below]
    }
}

/// `RootPlacer.getPotentiallyWaterloggedState`: `fluidState.is(FluidTags.WATER)`.
fn waterlogged_for_roots(tree: &Tree, pos: [i32; 3], state: u32) -> Result<u32> {
    let properties = properties().expect("checked before the placement");
    if tree.value(state, properties.waterlogged).is_none() {
        return Ok(state);
    }
    let water = tree.is(tree.config.sets.water_fluid, pos)?;
    let index = if water { properties.waterlogged_true } else { properties.waterlogged_false };
    Ok(tree.with_value(state, properties.waterlogged, index).unwrap_or(state))
}

/// `Direction.Plane.HORIZONTAL` as (dx, dz): north, east, south, west.
pub(crate) const HORIZONTAL: [[i32; 2]; 4] = [[0, -1], [1, 0], [0, 1], [-1, 0]];
/// `Direction.values()`: down, up, north, south, west, east.
pub(crate) const DIRECTIONS: [[i32; 3]; 6] = [[0, -1, 0], [0, 1, 0], [0, 0, -1], [0, 0, 1], [-1, 0, 0], [1, 0, 0]];

/// `TreeFeature.updateLeaves`: re-distance leaves outward from the logs inside
/// the tree's box, in Java's set order; returns the filled voxels.
fn update_leaves(tree: &mut Tree, min: [i32; 3], max: [i32; 3]) -> Result<Vec<u64>> {
    let span = [max[0] - min[0] + 1, max[1] - min[1] + 1, max[2] - min[2] + 1];
    let volume = (span[0] * span[1] * span[2]) as usize;
    let mut shape = vec![0u64; volume.div_ceil(64)];
    let index = |pos: [i32; 3]| (((pos[0] - min[0]) * span[1] + (pos[1] - min[1])) * span[2] + (pos[2] - min[2])) as usize;
    let inside = |pos: [i32; 3]| (0..3).all(|a| pos[a] >= min[a] && pos[a] <= max[a]);
    let mut lists: Vec<JavaHashSet> = (0..7).map(|_| JavaHashSet::new()).collect();
    // `Lists.newArrayList(Sets.union(logs... no: union(foliage-set2? ...))`: Java passes (logs, decorations, roots).
    let union: Vec<[i32; 3]> = {
        let mut items = tree.built.decorations.to_vec();
        items.extend(tree.built.roots.iter().filter(|p| !tree.built.decorations.contains(*p)));
        items
    };
    for pos in union {
        if inside(pos) {
            let i = index(pos);
            shape[i >> 6] |= 1 << (i & 63);
        }
    }
    for pos in tree.built.logs.to_vec() {
        lists[0].insert(pos);
    }
    let properties = properties().expect("checked before the placement");
    let mut k = 0usize;
    loop {
        while k >= 7 || !lists[k].is_empty() {
            if k >= 7 {
                return Ok(shape);
            }
            let pos = lists[k].first().expect("not empty");
            lists[k].remove(pos);
            if !inside(pos) {
                continue;
            }
            if k != 0 {
                let state = tree.state(pos)?;
                let set = tree.with_value(state, properties.distance, (k - 1) as u16).ok_or(Failure::Unbound)?;
                tree.placement.region.set_block(pos[0], pos[1], pos[2], set, 19)?;
            }
            let i = index(pos);
            shape[i >> 6] |= 1 << (i & 63);
            for [dx, dy, dz] in DIRECTIONS {
                let next = [pos[0] + dx, pos[1] + dy, pos[2] + dz];
                if !inside(next) {
                    continue;
                }
                let j = index(next);
                if shape[j >> 6] & (1 << (j & 63)) != 0 {
                    continue;
                }
                let state = tree.state(next)?;
                // `LeavesBlock.getOptionalDistanceAt`.
                let distance = if tree.placement.program.in_set(tree.config.sets.logs, state) {
                    Some(0)
                } else {
                    tree.value(state, properties.distance).map(|v| v as usize + 1)
                };
                if let Some(distance) = distance {
                    let o = distance.min(k + 1);
                    if o < 7 {
                        lists[o].insert(next);
                        k = k.min(o);
                    }
                }
            }
        }
        k += 1;
    }
}

//! `StructureTemplate.placeInWorld` for one palette: `processBlockInfos` with
//! the supported processors, then the write loop with waterlogging, over a
//! bound decoration region. Java keeps palette choice, NBT, block-entity
//! loading (recorded here as ordered events), entities and shape updates.
use super::region::{Failure, Region, Result};
use super::program::Reader;
use crate::world::level::levelgen::random::Legacy;

/// `BlockPos` relative to the template, its state and its NBT (index into Java's list, or -1).
#[derive(Clone, Debug)]
struct Block {
    pos: [i32; 3],
    state: u32,
    nbt: i32,
    /// `JigsawReplacementProcessor`'s parsed `final_state` (None: drop the block).
    jigsaw: Option<Option<u32>>,
}

/// Per state used by a template: its 12 mirror × rotation transforms and its facts.
#[derive(Clone, Debug)]
struct StateFacts {
    transforms: [u32; 12],
    /// Bit 0 block entity, 1 randomizable container, 2 fluid is a source, 3 fluid is
    /// still water, 4 fluid empty, 5 simple waterloggable, 6 waterlogged, 7 other
    /// liquid container whose `placeLiquid` does nothing, 8 jigsaw.
    flags: u32,
    /// The state with `WATERLOGGED` true (simple waterloggable states).
    waterlogged: u32,
}

const BLOCK_ENTITY: u32 = 1;
const RANDOMIZABLE: u32 = 2;
const FLUID_SOURCE: u32 = 4;
const STILL_WATER: u32 = 8;
const SIMPLE_WATERLOGGED: u32 = 32;
const WATERLOGGED: u32 = 64;
const NO_OP_CONTAINER: u32 = 128;

pub(crate) struct Template {
    blocks: Vec<Block>,
    facts: std::collections::HashMap<u32, StateFacts>,
    barrier: u32,
    water_fluid: u32,
}

/// A rule's block-entity modifier: `Passthrough`, `AppendLoot`, `AppendStatic` or `Clear`.
#[derive(Clone, Copy, Debug, PartialEq)]
enum Modifier {
    Passthrough,
    /// The loot table's index in Java's list; draws the seed.
    AppendLoot(i32),
    AppendStatic(i32),
    Clear,
}

/// NBT as Java will build it: the template's tag (or none), then rule edits in order.
#[derive(Clone, Debug, PartialEq)]
struct Nbt {
    source: i32,
    /// `(kind, argument, seed)`: 1 append loot, 2 append static, 3 clear.
    ops: Vec<(i64, i64, i64)>,
}

#[derive(Clone, Debug)]
enum Test {
    Always,
    In(Vec<u32>),
    /// `RandomBlockMatchTest`/`RandomBlockStateMatchTest`: set membership, then a draw.
    RandomIn(Vec<u32>, f32),
}

#[derive(Clone, Debug)]
enum PosTest {
    Always,
    /// `AxisAlignedLinearPosTest(axis)` / `LinearPosTest`: chances and distances.
    Linear { axis: Option<usize>, min_chance: f32, max_chance: f32, min_dist: i32, max_dist: i32 },
}

#[derive(Clone, Debug)]
struct Rule {
    input: Test,
    location: Test,
    pos: PosTest,
    output: u32,
    modifier: Modifier,
}

#[derive(Clone, Debug)]
enum Processor {
    Ignore(Vec<u32>),
    Jigsaw,
    Rule(Vec<Rule>),
    /// `BlockRotProcessor`: rottable states (None: every block) and integrity.
    Rot(Option<Vec<u32>>, f32),
    /// `GravityProcessor`: heightmap ordinal and offset.
    Gravity(u32, i32),
    /// `ProtectedBlockProcessor`: states it may replace.
    Protected(Vec<u32>),
}

pub(crate) struct Processors {
    list: Vec<Processor>,
}

fn bit(set: &[u32], state: u32) -> bool {
    set.get((state >> 5) as usize).is_some_and(|w| w & (1 << (state & 31)) != 0)
}

fn read_set(reader: &mut Reader) -> Option<Vec<u32>> {
    let length = reader.count(1 << 16)?;
    Some(reader.take(length)?.iter().map(|&w| w as u32).collect())
}

impl Template {
    /// `[blocks, (x, y, z, state, nbt, jigsaw)*, states, (state, flags, waterlogged, 12 transforms)*, barrier, water fluid]`;
    /// jigsaw is -2 for none, -1 to drop, else the replacement state.
    pub(crate) fn decode(words: &[i32]) -> Option<Self> {
        let mut reader = Reader::new(words);
        let count = reader.count(1 << 20)?;
        let mut blocks = Vec::with_capacity(count);
        for _ in 0..count {
            let pos = [reader.next()?, reader.next()?, reader.next()?];
            let state = reader.state()?;
            let nbt = reader.next()?;
            let jigsaw = match reader.next()? {
                -2 => None,
                -1 => Some(None),
                s => Some(Some(u32::try_from(s).ok()?)),
            };
            blocks.push(Block { pos, state, nbt, jigsaw });
        }
        let states = reader.count(1 << 16)?;
        let mut facts = std::collections::HashMap::with_capacity(states);
        for _ in 0..states {
            let state = reader.state()?;
            let flags = reader.state()?;
            let waterlogged = reader.state()?;
            let mut transforms = [0; 12];
            for t in transforms.iter_mut() {
                *t = reader.state()?;
            }
            facts.insert(state, StateFacts { transforms, flags, waterlogged });
        }
        let barrier = reader.state()?;
        let water_fluid = reader.state()?;
        if reader.at != words.len() {
            return None;
        }
        // Every state the loop can write or test needs its facts.
        let complete = blocks.iter().all(|b| facts.contains_key(&b.state) && b.jigsaw.flatten().is_none_or(|s| facts.contains_key(&s)))
            && facts.values().all(|f| f.transforms.iter().all(|t| facts.contains_key(t)) && (f.flags & SIMPLE_WATERLOGGED == 0 || facts.contains_key(&f.waterlogged)));
        complete.then_some(Self { blocks, facts, barrier, water_fluid })
    }

    /// Every state the template can write, for counter-policy admission.
    pub(crate) fn states(&self) -> impl Iterator<Item = u32> + '_ {
        self.facts.iter().flat_map(|(&s, f)| [s, f.waterlogged].into_iter().chain(f.transforms)).chain(std::iter::once(self.barrier))
    }
}

impl Processors {
    pub(crate) fn decode(words: &[i32]) -> Option<Self> {
        let mut reader = Reader::new(words);
        let count = reader.count(64)?;
        let mut list = Vec::with_capacity(count);
        let test = |reader: &mut Reader| -> Option<Test> {
            Some(match reader.next()? {
                0 => Test::Always,
                1 => Test::In(read_set(reader)?),
                2 => {
                    let probability = reader.float()?;
                    Test::RandomIn(read_set(reader)?, probability)
                }
                _ => return None,
            })
        };
        for _ in 0..count {
            list.push(match reader.next()? {
                1 => Processor::Ignore(read_set(&mut reader)?),
                2 => Processor::Jigsaw,
                3 => {
                    let rules = reader.count(256)?;
                    let mut out = Vec::with_capacity(rules);
                    for _ in 0..rules {
                        let input = test(&mut reader)?;
                        let location = test(&mut reader)?;
                        let pos = match reader.next()? {
                            0 => PosTest::Always,
                            kind @ (1 | 2) => {
                                let axis = reader.next()?;
                                PosTest::Linear {
                                    axis: if kind == 1 { Some(usize::try_from(axis).ok().filter(|&a| a < 3)?) } else { None },
                                    min_chance: reader.float()?,
                                    max_chance: reader.float()?,
                                    min_dist: reader.next()?,
                                    max_dist: reader.next()?,
                                }
                            }
                            _ => return None,
                        };
                        let output = reader.state()?;
                        let modifier = match (reader.next()?, reader.next()?) {
                            (0, _) => Modifier::Passthrough,
                            (1, table) => Modifier::AppendLoot(table),
                            (2, tag) => Modifier::AppendStatic(tag),
                            (3, _) => Modifier::Clear,
                            _ => return None,
                        };
                        out.push(Rule { input, location, pos, output, modifier });
                    }
                    Processor::Rule(out)
                }
                4 => {
                    let integrity = reader.float()?;
                    Processor::Rot(if reader.flag()? { Some(read_set(&mut reader)?) } else { None }, integrity)
                }
                5 => Processor::Gravity(reader.state().filter(|&h| h < 6)?, reader.next()?),
                6 => Processor::Protected(read_set(&mut reader)?),
                _ => return None,
            });
        }
        (reader.at == words.len()).then_some(Self { list })
    }

    /// Output states rules can write.
    pub(crate) fn states(&self) -> impl Iterator<Item = u32> + '_ {
        self.list.iter().flat_map(|p| match p {
            Processor::Rule(rules) => rules.iter().map(|r| r.output).collect::<Vec<_>>(),
            _ => Vec::new(),
        })
    }
}

/// `Mth.getSeed(x, y, z)`.
fn position_seed(pos: [i32; 3]) -> i64 {
    crate::world::level::levelgen::random::position_seed(pos[0], pos[1], pos[2])
}

/// `StructureTemplate.transform(pos, mirror, rotation, pivot)`: mirror 0 none,
/// 1 left-right, 2 front-back; rotation 0 none, 1 clockwise 90, 2 180, 3 counterclockwise 90.
fn transform(pos: [i32; 3], mirror: usize, rotation: usize, pivot: [i32; 3]) -> [i32; 3] {
    let [mut i, j, mut k] = pos;
    match mirror {
        1 => k = -k,
        2 => i = -i,
        _ => {}
    }
    let (l, m) = (pivot[0], pivot[2]);
    match rotation {
        3 => [l - m + k, j, l + m - i],
        1 => [l + m - k, j, m - l + i],
        2 => [l + l - i, j, m + m - k],
        _ => [i, j, k],
    }
}

/// `Mth.clampedLerp(min, max, Mth.inverseLerp(i, minDist, maxDist))` over floats.
fn linear_chance(i: i32, min_chance: f32, max_chance: f32, min_dist: i32, max_dist: i32) -> f32 {
    let delta = (i as f32 - min_dist as f32) / (max_dist as f32 - min_dist as f32);
    if delta < 0.0 {
        min_chance
    } else if delta > 1.0 {
        max_chance
    } else {
        min_chance + delta * (max_chance - min_chance)
    }
}

/// One `StructureBlockInfo` after processors.
#[derive(Clone, Debug, PartialEq)]
struct Info {
    pos: [i32; 3],
    state: u32,
    nbt: Option<Nbt>,
}

/// One placement's inputs.
pub(crate) struct Call {
    pub(crate) origin: [i32; 3],
    /// `placeInWorld`'s second position: processors' position tests measure from it.
    pub(crate) pivot: [i32; 3],
    /// `StructurePlaceSettings.getRotationPivot()`: positions rotate about it.
    pub(crate) rotation_pivot: [i32; 3],
    pub(crate) mirror: usize,
    pub(crate) rotation: usize,
    /// `StructurePlaceSettings.getBoundingBox()`, or None.
    pub(crate) bounds: Option<[i32; 6]>,
    pub(crate) flags: i32,
    pub(crate) waterlogging: bool,
    /// The placement random's Xoroshiro state (loot seeds), updated in place.
    pub(crate) random: super::random::WorldgenRandom,
    /// The settings random is the placement random: block rot draws from it.
    pub(crate) shared_random: bool,
}

/// What Java finishes: every placed position (shape updates) and block-entity loads.
pub(crate) struct Placed {
    pub(crate) positions: Vec<[i32; 3]>,
    /// `[x, y, z, source, op count, (kind, argument, seed)*]` per load, as event arguments index.
    pub(crate) loads: Vec<i64>,
}

/// The block-entity load event: argument is the load's offset in `Placed::loads`.
pub(crate) const EVENT_TEMPLATE_BLOCK_ENTITY: i64 = 9;

impl Template {
    fn facts(&self, state: u32) -> Result<&StateFacts> {
        self.facts.get(&state).ok_or(Failure::Unbound)
    }

    pub(crate) fn place(&self, processors: &Processors, region: &mut Region, call: &mut Call) -> Result<Placed> {
        let infos = self.process(processors, region, call)?;
        let mut placed = Placed { positions: Vec::new(), loads: Vec::new() };
        let mut flowing: Vec<[i32; 3]> = Vec::new();
        let mut sources: Vec<[i32; 3]> = Vec::new();
        let transform_index = call.mirror * 4 + call.rotation;
        for info in infos {
            let pos = info.pos;
            if let Some(b) = call.bounds {
                if !(pos[0] >= b[0] && pos[0] <= b[3] && pos[2] >= b[2] && pos[2] <= b[5] && pos[1] >= b[1] && pos[1] <= b[4]) {
                    continue;
                }
            }
            let fluid = if call.waterlogging { Some(region.level_block_state(pos[0], pos[1], pos[2])?) } else { None };
            // `state.mirror(mirror).rotate(rotation)`.
            let state = self.facts(info.state)?.transforms[transform_index];
            if info.nbt.is_some() {
                region.set_block(pos[0], pos[1], pos[2], self.barrier, 820)?;
            }
            if !region.set_block(pos[0], pos[1], pos[2], state, call.flags)? {
                continue;
            }
            placed.positions.push(pos);
            let facts = self.facts(state)?.clone();
            if let Some(nbt) = &info.nbt {
                // `getBlockEntity` finds one only for a block-entity state; randomizable
                // containers then take a loot seed from the placement random.
                if facts.flags & BLOCK_ENTITY != 0 {
                    let seed = if facts.flags & RANDOMIZABLE != 0 { Some(call.random.next_long()) } else { None };
                    let at = placed.loads.len() as i64;
                    placed.loads.extend([pos[0] as i64, pos[1] as i64, pos[2] as i64, nbt.source as i64,
                        seed.map_or(0, |_| 1), seed.unwrap_or(0), nbt.ops.len() as i64]);
                    for &(kind, argument, op_seed) in &nbt.ops {
                        placed.loads.extend([kind, argument, op_seed]);
                    }
                    region.event(EVENT_TEMPLATE_BLOCK_ENTITY, pos[0], pos[1], pos[2], at);
                }
            }
            if let Some(world) = fluid {
                let world_facts = self.fluid_facts(region, world)?;
                if facts.flags & FLUID_SOURCE != 0 {
                    sources.push(pos);
                } else if facts.flags & (SIMPLE_WATERLOGGED | NO_OP_CONTAINER) != 0 {
                    // `instanceof LiquidBlockContainer`: the encoder admits only these two kinds.
                    self.place_liquid(region, pos, &facts, world_facts)?;
                    if world_facts & FLUID_SOURCE == 0 {
                        flowing.push(pos);
                    }
                }
            }
        }
        // Flowing fluids take a neighbouring source not placed by the template.
        const SIDES: [[i32; 3]; 5] = [[0, 1, 0], [0, 0, -1], [1, 0, 0], [0, 0, 1], [-1, 0, 0]];
        let mut changed = true;
        while changed && !flowing.is_empty() {
            changed = false;
            let mut kept = Vec::with_capacity(flowing.len());
            for pos in std::mem::take(&mut flowing) {
                let mut fluid = self.fluid_facts(region, region.level_block_state(pos[0], pos[1], pos[2])?)?;
                for side in SIDES {
                    if fluid & FLUID_SOURCE != 0 {
                        break;
                    }
                    let next = [pos[0] + side[0], pos[1] + side[1], pos[2] + side[2]];
                    let there = self.fluid_facts(region, region.level_block_state(next[0], next[1], next[2])?)?;
                    if there & FLUID_SOURCE != 0 && !sources.contains(&next) {
                        fluid = there;
                    }
                }
                if fluid & FLUID_SOURCE != 0 {
                    let state = region.level_block_state(pos[0], pos[1], pos[2])?;
                    let facts = self.facts.get(&state).cloned();
                    if let Some(facts) = facts.filter(|f| f.flags & (SIMPLE_WATERLOGGED | NO_OP_CONTAINER) != 0) {
                        self.place_liquid(region, pos, &facts, fluid)?;
                        changed = true;
                        continue;
                    }
                }
                kept.push(pos);
            }
            flowing = kept;
        }
        Ok(placed)
    }

    /// A world state's fluid bits (source, still water, empty) from the installed facts.
    fn fluid_facts(&self, _region: &Region, state: u32) -> Result<u32> {
        let facts = super::facts::installed().ok_or(Failure::Unbound)?;
        Ok(facts.fluid_flags(state))
    }

    /// `SimpleWaterloggedBlock.placeLiquid`; the other admitted containers do nothing.
    fn place_liquid(&self, region: &mut Region, pos: [i32; 3], facts: &StateFacts, fluid: u32) -> Result<()> {
        if facts.flags & SIMPLE_WATERLOGGED != 0 && facts.flags & WATERLOGGED == 0 && fluid & STILL_WATER != 0 {
            region.set_block(pos[0], pos[1], pos[2], facts.waterlogged, 3)?;
            region.schedule_tick(true, pos[0], pos[1], pos[2], self.water_fluid, 5);
        }
        Ok(())
    }

    /// `StructureTemplate.processBlockInfos`.
    fn process(&self, processors: &Processors, region: &mut Region, call: &mut Call) -> Result<Vec<Info>> {
        let mut out = Vec::with_capacity(self.blocks.len());
        for block in &self.blocks {
            let relative = transform(block.pos, call.mirror, call.rotation, call.rotation_pivot);
            let pos = [relative[0] + call.origin[0], relative[1] + call.origin[1], relative[2] + call.origin[2]];
            let mut info = Some(Info { pos, state: block.state, nbt: (block.nbt >= 0).then(|| Nbt { source: block.nbt, ops: Vec::new() }) });
            for processor in &processors.list {
                let Some(current) = info.take() else {
                    break;
                };
                info = self.process_one(processor, region, call, block, current)?;
            }
            if let Some(info) = info {
                out.push(info);
            }
        }
        Ok(out)
    }

    fn process_one(&self, processor: &Processor, region: &mut Region, call: &mut Call, block: &Block, info: Info) -> Result<Option<Info>> {
        Ok(match processor {
            Processor::Ignore(set) => (!bit(set, info.state)).then_some(info),
            Processor::Jigsaw => {
                // Applies to the template's own jigsaw blocks; the encoder parsed `final_state`.
                if info.state == block.state && info.nbt.as_ref().is_some_and(|n| n.ops.is_empty() && n.source == block.nbt) {
                    match block.jigsaw {
                        Some(Some(state)) => Some(Info { pos: info.pos, state, nbt: None }),
                        Some(None) => None,
                        None => Some(info),
                    }
                } else if self.facts(info.state)?.flags & 256 != 0 {
                    // A jigsaw block whose NBT a rule rewrote: the encoder declines these.
                    return Err(Failure::Declined);
                } else {
                    Some(info)
                }
            }
            Processor::Rule(rules) => {
                let mut random = Legacy::new(position_seed(info.pos));
                let location = region.level_block_state(info.pos[0], info.pos[1], info.pos[2])?;
                for rule in rules {
                    if test(&rule.input, info.state, &mut random)
                        && test(&rule.location, location, &mut random)
                        && pos_test(&rule.pos, block.pos, info.pos, call.pivot, &mut random)
                    {
                        let nbt = match rule.modifier {
                            Modifier::Passthrough => info.nbt.clone(),
                            Modifier::AppendLoot(table) => {
                                let mut nbt = info.nbt.clone().unwrap_or(Nbt { source: -1, ops: Vec::new() });
                                nbt.ops.push((1, table as i64, random.next_long()));
                                Some(nbt)
                            }
                            Modifier::AppendStatic(tag) => {
                                let mut nbt = info.nbt.clone().unwrap_or(Nbt { source: -1, ops: Vec::new() });
                                nbt.ops.push((2, tag as i64, 0));
                                Some(nbt)
                            }
                            Modifier::Clear => Some(Nbt { source: -1, ops: vec![(3, 0, 0)] }),
                        };
                        return Ok(Some(Info { pos: info.pos, state: rule.output, nbt }));
                    }
                }
                Some(info)
            }
            Processor::Rot(rottable, integrity) => {
                // `settings.getRandom(pos)`: the settings random, or a fresh legacy
                // source seeded by the position.
                let applies = rottable.as_ref().is_none_or(|set| bit(set, block.state));
                let draw = |call: &mut Call| {
                    if call.shared_random { call.random.next_float() } else { Legacy::new(position_seed(info.pos)).next_float() }
                };
                if applies && !(draw(call) <= *integrity) {
                    None
                } else {
                    Some(info)
                }
            }
            Processor::Gravity(heightmap, offset) => {
                let height = region.height_by_type(*heightmap, info.pos[0], info.pos[2])? + offset;
                Some(Info { pos: [info.pos[0], height + block.pos[1], info.pos[2]], state: info.state, nbt: info.nbt })
            }
            Processor::Protected(replaceable) => {
                let there = region.level_block_state(info.pos[0], info.pos[1], info.pos[2])?;
                bit(replaceable, there).then_some(info)
            }
        })
    }
}

fn test(test: &Test, state: u32, random: &mut Legacy) -> bool {
    match test {
        Test::Always => true,
        Test::In(set) => bit(set, state),
        Test::RandomIn(set, probability) => bit(set, state) && random.next_float() < *probability,
    }
}

/// `PosRuleTest.test(templatePos, worldPos, pivot, random)`.
fn pos_test(test: &PosTest, _template: [i32; 3], world: [i32; 3], pivot: [i32; 3], random: &mut Legacy) -> bool {
    match *test {
        PosTest::Always => true,
        PosTest::Linear { axis, min_chance, max_chance, min_dist, max_dist } => {
            let i = match axis {
                // `AxisAlignedLinearPosTest`: the distance along the positive direction of `axis`.
                Some(a) => {
                    let d = [(world[0] - pivot[0]).abs() as f32, (world[1] - pivot[1]).abs() as f32, (world[2] - pivot[2]).abs() as f32];
                    let unit = [f32::from(u8::from(a == 0)), f32::from(u8::from(a == 1)), f32::from(u8::from(a == 2))];
                    (d[0] * unit[0] + d[1] * unit[1] + d[2] * unit[2]) as i32
                }
                // `LinearPosTest`: `worldPos.distManhattan(pivot)` (float sums).
                None => ((pivot[0] - world[0]).abs() as f32 + (pivot[1] - world[1]).abs() as f32 + (pivot[2] - world[2]).abs() as f32) as i32,
            };
            random.next_float() <= linear_chance(i, min_chance, max_chance, min_dist, max_dist)
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// `StructureTemplate.transform` for each mirror and rotation, about a pivot.
    #[test]
    fn transform_matches_java() {
        let pos = [1, 2, 3];
        let pivot = [4, 0, 7];
        let (l, m) = (pivot[0], pivot[2]);
        assert_eq!(transform(pos, 0, 0, pivot), [1, 2, 3]);
        assert_eq!(transform(pos, 0, 1, pivot), [l + m - 3, 2, m - l + 1]);
        assert_eq!(transform(pos, 0, 2, pivot), [l + l - 1, 2, m + m - 3]);
        assert_eq!(transform(pos, 0, 3, pivot), [l - m + 3, 2, l + m - 1]);
        assert_eq!(transform(pos, 1, 0, pivot), [1, 2, -3]);
        assert_eq!(transform(pos, 2, 0, pivot), [-1, 2, 3]);
        assert_eq!(transform(pos, 1, 1, pivot), [l + m + 3, 2, m - l + 1]);
    }

    /// `Mth.clampedLerp(min, max, Mth.inverseLerp(i, minDist, maxDist))` over floats.
    #[test]
    fn linear_chance_clamps_like_java() {
        assert_eq!(linear_chance(0, 0.1, 0.9, 5, 15), 0.1);
        assert_eq!(linear_chance(20, 0.1, 0.9, 5, 15), 0.9);
        assert_eq!(linear_chance(10, 0.0, 1.0, 5, 15), 0.5);
    }

    #[test]
    fn processors_decode_and_reject_unknown_kinds() {
        // Ignore (empty set), jigsaw, gravity (WORLD_SURFACE_WG, -1).
        let words = [3, 1, 0, 2, 5, 0, -1];
        let processors = Processors::decode(&words).expect("processors");
        assert_eq!(processors.list.len(), 3);
        assert!(Processors::decode(&[1, 9]).is_none());
        assert!(Processors::decode(&[1, 5, 7, 0]).is_none(), "heightmap ordinal out of range");
    }
}

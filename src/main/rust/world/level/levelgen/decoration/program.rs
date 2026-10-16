//! Compiled placed-feature programs. Java resolves registry, tag and biome
//! questions once per generator/context and encodes the answers as words;
//! Rust validates them on creation and retains the program until Java releases it.
//!
//! Layout (`VERSION` 3): `[VERSION, placed, sets, biomes, noises]` where
//! `placed = [modifier count, modifiers (MODIFIER_WORDS each), feature]`,
//! `feature = [kind, payload length, payload]`, `sets = [count, (length, words)*]`
//! `biomes = [length, words]` and `noises = [count, (address low, high)*]`
//! (Java-owned native noise states the program keeps alive). A block
//! predicate follows its modifier.

use super::tree::TreeConfig;

pub(crate) const VERSION: i32 = 3;
pub(crate) const MODIFIER_WORDS: usize = 6;

#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) enum Anchor {
    Absolute(i32),
    AboveBottom(i32),
    BelowTop(i32),
}

impl Anchor {
    fn decode(kind: i32, offset: i32) -> Option<Self> {
        match kind {
            0 => Some(Anchor::Absolute(offset)),
            1 => Some(Anchor::AboveBottom(offset)),
            2 => Some(Anchor::BelowTop(offset)),
            _ => None,
        }
    }

    /// `VerticalAnchor.resolveY` for a `WorldGenerationContext`.
    pub(crate) fn resolve(self, min_gen_y: i32, gen_depth: i32) -> i32 {
        match self {
            Anchor::Absolute(y) => y,
            Anchor::AboveBottom(offset) => min_gen_y.wrapping_add(offset),
            Anchor::BelowTop(offset) => gen_depth.wrapping_sub(1).wrapping_add(min_gen_y).wrapping_sub(offset),
        }
    }
}

/// `IntProvider`s with Rust implementations.
#[derive(Clone, Debug, PartialEq)]
pub(crate) enum IntProvider {
    Constant(i32),
    /// `UniformInt(min, max)`.
    Uniform(i32, i32),
    /// `BiasedToBottomInt(min, max)`.
    BiasedToBottom(i32, i32),
    /// `WeightedListInt`: `(weight, provider)` entries and their total weight.
    Weighted(Box<[(i32, IntProvider)]>, i32),
    /// `ClampedInt(source, min, max)`.
    Clamped(Box<IntProvider>, i32, i32),
    /// `ClampedNormalInt(mean, deviation, min, max)`.
    ClampedNormal(f32, f32, i32, i32),
}

impl IntProvider {
    pub(crate) fn decode(kind: i32, a: i32, b: i32) -> Option<Self> {
        match kind {
            1 => Some(IntProvider::Constant(a)),
            2 if a <= b => Some(IntProvider::Uniform(a, b)),
            3 if a <= b => Some(IntProvider::BiasedToBottom(a, b)),
            _ => None,
        }
    }

    /// `[kind, a, b]`, or `[4, count, (weight, provider)*]` for a weighted list.
    pub(crate) fn read(reader: &mut Reader) -> Option<Self> {
        Self::read_depth(reader, 0)
    }

    fn read_depth(reader: &mut Reader, depth: usize) -> Option<Self> {
        let kind = reader.next()?;
        if kind == 4 {
            if depth > 4 {
                return None;
            }
            let count = reader.count(256).filter(|&n| n > 0)?;
            let mut entries = Vec::with_capacity(count);
            for _ in 0..count {
                let weight = reader.next().filter(|&w| w > 0)?;
                entries.push((weight, Self::read_depth(reader, depth + 1)?));
            }
            let total = entries.iter().try_fold(0i32, |t, (w, _)| t.checked_add(*w))?;
            return Some(IntProvider::Weighted(entries.into(), total));
        }
        if kind == 6 {
            let (mean, deviation, min, max) = (reader.float()?, reader.float()?, reader.next()?, reader.next()?);
            return (min <= max).then_some(IntProvider::ClampedNormal(mean, deviation, min, max));
        }
        if kind == 5 {
            let (min, max) = (reader.next()?, reader.next()?);
            if depth > 4 || min > max {
                return None;
            }
            return Some(IntProvider::Clamped(Box::new(Self::read_depth(reader, depth + 1)?), min, max));
        }
        let a = reader.next()?;
        let b = reader.next()?;
        Self::decode(kind, a, b)
    }
}

/// `FloatProvider`s with Rust implementations.
#[derive(Clone, Debug, PartialEq)]
pub(crate) enum FloatProvider {
    Constant(f32),
    /// `UniformFloat(minInclusive, maxExclusive)`.
    Uniform(f32, f32),
    /// `ClampedNormalFloat(mean, deviation, min, max)`.
    ClampedNormal(f32, f32, f32, f32),
    /// `TrapezoidFloat(min, max, plateau)`.
    Trapezoid(f32, f32, f32),
}

impl FloatProvider {
    pub(crate) fn read(reader: &mut Reader) -> Option<Self> {
        Some(match reader.next()? {
            1 => FloatProvider::Constant(reader.float()?),
            2 => FloatProvider::Uniform(reader.float()?, reader.float()?),
            3 => FloatProvider::ClampedNormal(reader.float()?, reader.float()?, reader.float()?, reader.float()?),
            4 => FloatProvider::Trapezoid(reader.float()?, reader.float()?, reader.float()?),
            _ => return None,
        })
    }
}

/// `BlockPredicate`s compiled to state sets.
#[derive(Clone, Debug, PartialEq)]
pub(crate) enum Predicate {
    True,
    AllOf(Vec<Predicate>),
    AnyOf(Vec<Predicate>),
    Not(Box<Predicate>),
    /// The state at `pos + offset` is in `set` (`matching_blocks`, tags, fluids, `would_survive`'s supports).
    StateIn { offset: [i32; 3], set: usize },
    /// A shape-dependent `set` (sturdy faces, support shapes) built without a
    /// world: a dynamic-shape state at `pos + offset` asks Java's predicate `java` instead.
    Checked { offset: [i32; 3], set: usize, java: usize },
    /// `insideWorldBounds`: `pos + offset` is inside the build height.
    InsideWorld([i32; 3]),
    /// A predicate only Java can answer, by its index in the program's Java predicate table.
    Java(usize),
}

impl Predicate {
    pub(crate) fn read(reader: &mut Reader, depth: usize) -> Option<Self> {
        if depth > 16 {
            return None;
        }
        Some(match reader.next()? {
            1 => Predicate::True,
            kind @ (2 | 3) => {
                let count = reader.count(64)?;
                let children = (0..count).map(|_| Self::read(reader, depth + 1)).collect::<Option<Vec<_>>>()?;
                if kind == 2 { Predicate::AllOf(children) } else { Predicate::AnyOf(children) }
            }
            4 => Predicate::Not(Box::new(Self::read(reader, depth + 1)?)),
            5 => Predicate::StateIn {
                offset: [reader.next()?, reader.next()?, reader.next()?],
                set: reader.set()?,
            },
            6 => Predicate::Checked {
                offset: [reader.next()?, reader.next()?, reader.next()?],
                set: reader.set()?,
                java: reader.count(1 << 16)?,
            },
            7 => Predicate::InsideWorld([reader.next()?, reader.next()?, reader.next()?]),
            8 => Predicate::Java(reader.count(1 << 16)?),
            _ => return None,
        })
    }

    fn sets(&self, out: &mut Vec<usize>) {
        match self {
            Predicate::True => {}
            Predicate::AllOf(children) | Predicate::AnyOf(children) => children.iter().for_each(|c| c.sets(out)),
            Predicate::Not(child) => child.sets(out),
            Predicate::StateIn { set, .. } => out.push(*set),
            Predicate::Checked { .. } | Predicate::InsideWorld(_) | Predicate::Java(_) => {}
        }
    }
}

#[derive(Clone, Debug, PartialEq)]
pub(crate) enum Modifier {
    /// `CountPlacement`.
    Count(IntProvider),
    InSquare,
    /// `HeightRangePlacement` with `UniformHeight`.
    HeightUniform(Anchor, Anchor),
    /// `HeightRangePlacement` with `TrapezoidHeight` and its plateau.
    HeightTrapezoid(Anchor, Anchor, i32),
    /// `HeightRangePlacement` with `BiasedToBottomHeight` and its inner value.
    HeightBiasedToBottom(Anchor, Anchor, i32),
    /// `HeightRangePlacement` with `VeryBiasedToBottomHeight` and its inner value.
    HeightVeryBiasedToBottom(Anchor, Anchor, i32),
    /// `FixedPlacement`: its positions, kept to those in the origin's chunk.
    Fixed(Vec<[i32; 3]>),
    /// `BiomeFilter` against the program's biome set (top-level placements only).
    Biome,
    /// `RarityFilter` with its chance.
    Rarity(i32),
    /// `BlockPredicateFilter`.
    BlockPredicate(Predicate),
    /// `HeightmapPlacement` with a `Heightmap.Types` ordinal.
    Heightmap(u32),
    /// `SurfaceWaterDepthFilter`.
    SurfaceWaterDepth(i32),
    /// `CountOnEveryLayerPlacement`: its count, and the sets for empty (air, water,
    /// lava) and bedrock.
    CountOnEveryLayer { count: IntProvider, empty: usize, bedrock: usize },
    /// `EnvironmentScanPlacement`: up when true, the step limit, target and allowed predicates.
    EnvironmentScan { up: bool, max_steps: i32, target: Predicate, allowed: Predicate },
    /// `RandomOffsetPlacement`: X/Z then Y spread.
    RandomOffset(IntProvider, IntProvider),
    /// `NoiseThresholdCountPlacement` over `Biome.BIOME_INFO_NOISE` (noise table index).
    NoiseThresholdCount { noise: usize, level: f64, below: i32, above: i32 },
    /// `NoiseBasedCountPlacement`.
    NoiseBasedCount { noise: usize, ratio: i32, factor: f64, offset: f64 },
    /// `SurfaceRelativeThresholdFilter`: heightmap ordinal, min and max offsets.
    SurfaceRelativeThreshold { heightmap: u32, min: i32, max: i32 },
}

/// `BlockState.canSurvive` for the block families simple blocks place.
#[derive(Clone, Debug, PartialEq)]
pub(crate) enum Survival {
    /// `canSurvive` compiled to a predicate at the position.
    Predicate(Predicate),
    /// `DoublePlantBlock`: the placed lower half's `canSurvive`; `placeAt`
    /// writes these lower and upper states.
    DoublePlant { survives: Predicate, lower: u32, upper: u32 },
}

impl Survival {
    fn read(reader: &mut Reader) -> Option<Self> {
        Some(match reader.next()? {
            2 => Survival::DoublePlant { survives: Predicate::read(reader, 0)?, lower: reader.state()?, upper: reader.state()? },
            3 => Survival::Predicate(Predicate::read(reader, 0)?),
            _ => return None,
        })
    }
}

/// `OreConfiguration.TargetBlockState`'s rule, compiled to a state set.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Target {
    pub(crate) set: usize,
    /// `RandomBlockMatchTest`/`RandomBlockStateMatchTest`: a draw only after a set match.
    pub(crate) probability: Option<f32>,
    pub(crate) state: u32,
}

/// A `PlacedFeature`: its modifiers, then its configured feature.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Placed {
    pub(crate) modifiers: Vec<Modifier>,
    pub(crate) feature: Feature,
}

#[derive(Clone, Debug, PartialEq)]
pub(crate) enum Feature {
    /// `OreFeature`, with its shape factors precomputed per size.
    Ore { size: i32, discard_chance_on_air_exposure: f32, targets: Vec<Target>, shape: Box<[f64]> },
    /// `ReplaceBlobsFeature`: the target block's states as a set.
    ReplaceBlobs { radius: IntProvider, target: usize, replacement: u32 },
    /// `GlowstoneFeature`: host blocks above, and the glowstone block's states.
    Glowstone { glowstone: u32, hosts: usize, glowstone_blocks: usize },
    /// `SpringFeature`: the fluid's legacy block state and fluid registry ID.
    Spring { requires_below: bool, rocks: i32, holes: i32, valid: usize, state: u32, fluid: u32 },
    /// `RandomSelectorFeature`: entries tried in order by chance, then the default.
    RandomSelector { entries: Vec<(f32, Placed)>, default: Box<Placed> },
    /// `SimpleRandomSelectorFeature`.
    SimpleRandomSelector(Vec<Placed>),
    /// `RandomBooleanSelectorFeature`: the true then false feature.
    RandomBooleanSelector(Box<Placed>, Box<Placed>),
    /// `TreeFeature`.
    Tree(Box<TreeConfig>),
    /// Complete fallen-tree cursor over the existing native region.
    Fallen(Box<super::fallen_tree::Config>),
    /// `RandomPatchFeature`: tries, X/Z and Y spread, and the nested placement.
    RandomPatch { tries: i32, xz: i32, y: i32, placed: Box<Placed> },
    /// `SimpleBlockFeature` over a simple or weighted provider, with each
    /// providable state's `canSurvive` rule.
    SimpleBlock { provider: super::tree::Provider, survival: Vec<(u32, Survival)>, schedule_tick: bool, water_fluid: usize },
    /// A configured feature that still runs in Java, by its index in the
    /// program's Java table: placed through a sync upcall with this random's state.
    Java(i32),
    /// Features over `setBlock` in `vegetation.rs`.
    Vegetation(Box<super::vegetation::Vegetation>),
    /// `VegetationPatchFeature` and its waterlogged variant (`patch.rs`).
    Patch(Box<super::patch::Patch>),
}

impl Feature {
    /// States the feature (and nested features) write through `WorldGenRegion.setBlock`
    /// from its configuration; trees also rewrite leaves the world holds.
    pub(crate) fn set_block_states(&self) -> Vec<u32> {
        let mut states = Vec::new();
        self.collect_set_block_states(&mut states);
        states
    }

    /// Whether the feature writes through `setBlock` at all.
    pub(crate) fn uses_set_block(&self) -> bool {
        match self {
            Feature::Ore { .. } => false,
            Feature::RandomSelector { entries, default } => {
                entries.iter().any(|(_, p)| p.feature.uses_set_block()) || default.feature.uses_set_block()
            }
            Feature::SimpleRandomSelector(children) => children.iter().any(|p| p.feature.uses_set_block()),
            Feature::RandomBooleanSelector(a, b) => a.feature.uses_set_block() || b.feature.uses_set_block(),
            Feature::RandomPatch { placed, .. } => placed.feature.uses_set_block(),
            _ => true,
        }
    }

    /// Program kind reported to Java statistics (the outermost feature).
    pub(crate) fn kind(&self) -> i32 {
        match self {
            Feature::Ore { .. } => 1,
            Feature::ReplaceBlobs { .. } => 2,
            Feature::Glowstone { .. } => 3,
            Feature::Spring { .. } => 4,
            Feature::RandomSelector { .. } => 5,
            Feature::SimpleRandomSelector(_) => 6,
            Feature::RandomBooleanSelector(..) => 7,
            Feature::Tree(_) => 8,
            Feature::Fallen(_) => 61,
            Feature::Java(_) => 9,
            Feature::RandomPatch { .. } => 10,
            Feature::SimpleBlock { .. } => 11,
            Feature::Vegetation(feature) => feature.kind(),
            Feature::Patch(patch) => patch.kind(),
        }
    }

    fn collect_set_block_states(&self, out: &mut Vec<u32>) {
        match self {
            Feature::Ore { .. } => {}
            Feature::ReplaceBlobs { replacement, .. } => out.push(*replacement),
            Feature::Glowstone { glowstone, .. } => out.push(*glowstone),
            Feature::Spring { state, .. } => out.push(*state),
            Feature::RandomSelector { entries, default } => {
                entries.iter().for_each(|(_, p)| p.feature.collect_set_block_states(out));
                default.feature.collect_set_block_states(out);
            }
            Feature::SimpleRandomSelector(children) => children.iter().for_each(|p| p.feature.collect_set_block_states(out)),
            Feature::RandomBooleanSelector(a, b) => {
                a.feature.collect_set_block_states(out);
                b.feature.collect_set_block_states(out);
            }
            Feature::Tree(tree) => tree.states(out),
            Feature::Fallen(config) => config.states(out),
            Feature::Java(_) => {}
            Feature::RandomPatch { placed, .. } => placed.feature.collect_set_block_states(out),
            // Recorded by the reader while decoding.
            Feature::Vegetation(_) => {}
            Feature::Patch(patch) => patch.vegetation.feature.collect_set_block_states(out),
            Feature::SimpleBlock { survival, .. } => {
                for (state, rule) in survival {
                    out.push(*state);
                    if let Survival::DoublePlant { lower, upper, .. } = rule {
                        out.extend([*lower, *upper]);
                    }
                }
            }
        }
    }

    fn sets(&self, out: &mut Vec<usize>) {
        match self {
            Feature::Ore { targets, .. } => out.extend(targets.iter().map(|t| t.set)),
            Feature::ReplaceBlobs { target, .. } => out.push(*target),
            Feature::Glowstone { hosts, glowstone_blocks, .. } => out.extend([*hosts, *glowstone_blocks]),
            Feature::Spring { valid, .. } => out.push(*valid),
            Feature::RandomSelector { entries, default } => {
                entries.iter().for_each(|(_, p)| p.sets(out));
                default.sets(out);
            }
            Feature::SimpleRandomSelector(children) => children.iter().for_each(|p| p.sets(out)),
            Feature::RandomBooleanSelector(a, b) => {
                a.sets(out);
                b.sets(out);
            }
            Feature::Tree(tree) => tree.sets(out),
            Feature::Fallen(config) => config.sets(out),
            Feature::Java(_) | Feature::Vegetation(_) => {}
            Feature::Patch(patch) => patch.sets(out),
            Feature::RandomPatch { placed, .. } => placed.sets(out),
            // Survival predicates' sets are recorded by the reader.
            Feature::SimpleBlock { water_fluid, .. } => out.push(*water_fluid),
        }
    }

    fn read(reader: &mut Reader, depth: usize) -> Option<Self> {
        let kind = reader.next()?;
        let length = reader.count(1 << 20)?;
        let end = reader.at.checked_add(length)?;
        let feature = match kind {
            1 => {
                let size = reader.next()?;
                let discard = reader.float()?;
                let count = reader.count(64)?;
                let mut targets = Vec::with_capacity(count);
                for _ in 0..count {
                    let rule = reader.next()?;
                    let probability = reader.float()?;
                    let set = reader.index()?;
                    let state = reader.state()?;
                    let probability = match rule {
                        1 => None,
                        2 => Some(probability),
                        _ => return None,
                    };
                    targets.push(Target { set, probability, state });
                }
                if !(0..=64).contains(&size) {
                    return None;
                }
                Feature::Ore { size, discard_chance_on_air_exposure: discard, targets, shape: super::ore::shape(size) }
            }
            2 => Feature::ReplaceBlobs { radius: IntProvider::read(reader)?, target: reader.index()?, replacement: reader.state()? },
            3 => Feature::Glowstone { glowstone: reader.state()?, hosts: reader.index()?, glowstone_blocks: reader.index()? },
            4 => Feature::Spring {
                requires_below: reader.flag()?,
                rocks: reader.next()?,
                holes: reader.next()?,
                valid: reader.index()?,
                state: reader.state()?,
                fluid: reader.state()?,
            },
            5 => {
                let count = reader.count(64)?;
                let mut entries = Vec::with_capacity(count);
                for _ in 0..count {
                    let chance = reader.float()?;
                    entries.push((chance, Placed::read(reader, depth + 1, false)?));
                }
                Feature::RandomSelector { entries, default: Box::new(Placed::read(reader, depth + 1, false)?) }
            }
            6 => {
                let count = reader.count(64).filter(|&n| n > 0)?;
                Feature::SimpleRandomSelector((0..count).map(|_| Placed::read(reader, depth + 1, false)).collect::<Option<_>>()?)
            }
            7 => Feature::RandomBooleanSelector(
                Box::new(Placed::read(reader, depth + 1, false)?),
                Box::new(Placed::read(reader, depth + 1, false)?),
            ),
            8 => Feature::Tree(Box::new(TreeConfig::read(reader)?)),
            61 => Feature::Fallen(Box::new(super::fallen_tree::Config::read(reader)?)),
            99 => Feature::Java(reader.next().filter(|&i| i >= 0)?),
            47 | 48 => Feature::Patch(Box::new(super::patch::Patch::read(reader, depth)?)),
            12..=46 | 49..=60 => Feature::Vegetation(Box::new(super::vegetation::Vegetation::read(kind, reader, depth)?)),
            10 => Feature::RandomPatch {
                tries: reader.next()?,
                xz: reader.next()?,
                y: reader.next()?,
                placed: Box::new(Placed::read(reader, depth + 1, false)?),
            },
            11 => {
                let provider = super::tree::Provider::read(reader)?;
                let schedule_tick = reader.flag()?;
                let water_fluid = reader.index()?;
                let count = reader.count(256)?;
                let survival = (0..count).map(|_| Some((reader.state()?, Survival::read(reader)?))).collect::<Option<Vec<_>>>()?;
                Feature::SimpleBlock { provider, survival, schedule_tick, water_fluid }
            }
            _ => return None,
        };
        (reader.at == end).then_some(feature)
    }
}

impl Placed {
    /// `top` placements may use `BiomeFilter`; nested ones would throw in Java and are never encoded.
    pub(crate) fn read(reader: &mut Reader, depth: usize, top: bool) -> Option<Self> {
        if depth > 8 {
            return None;
        }
        let count = reader.count(64)?;
        let mut modifiers = Vec::with_capacity(count);
        for _ in 0..count {
            let m: [i32; MODIFIER_WORDS] = reader.take(MODIFIER_WORDS)?.try_into().ok()?;
            modifiers.push(match m[0] {
                1 => Modifier::Count(IntProvider::Constant(m[1])),
                2 => Modifier::Count(IntProvider::decode(2, m[1], m[2])?),
                9 => Modifier::Count(IntProvider::decode(3, m[1], m[2])?),
                3 => Modifier::InSquare,
                4 => Modifier::HeightUniform(Anchor::decode(m[1], m[2])?, Anchor::decode(m[3], m[4])?),
                5 => Modifier::HeightTrapezoid(Anchor::decode(m[1], m[2])?, Anchor::decode(m[3], m[4])?, m[5]),
                6 => Modifier::HeightBiasedToBottom(Anchor::decode(m[1], m[2])?, Anchor::decode(m[3], m[4])?, m[5]),
                20 => Modifier::HeightVeryBiasedToBottom(Anchor::decode(m[1], m[2])?, Anchor::decode(m[3], m[4])?, m[5]),
                7 if top => Modifier::Biome,
                8 if m[1] > 0 => Modifier::Rarity(m[1]),
                10 => Modifier::BlockPredicate(Predicate::read(reader, 0)?),
                11 if (0..6).contains(&m[1]) => Modifier::Heightmap(m[1] as u32),
                12 => Modifier::SurfaceWaterDepth(m[1]),
                13 => Modifier::Count(IntProvider::read(reader)?),
                14 => Modifier::CountOnEveryLayer {
                    empty: usize::try_from(m[1]).ok()?,
                    bedrock: usize::try_from(m[2]).ok()?,
                    count: IntProvider::read(reader)?,
                },
                15 if (1..=32).contains(&m[2]) => Modifier::EnvironmentScan {
                    up: m[1] != 0,
                    max_steps: m[2],
                    target: Predicate::read(reader, 0)?,
                    allowed: Predicate::read(reader, 0)?,
                },
                16 => Modifier::RandomOffset(IntProvider::read(reader)?, IntProvider::read(reader)?),
                21 => {
                    let count = usize::try_from(m[1]).ok().filter(|&n| n <= 4096)?;
                    Modifier::Fixed((0..count).map(|_| Some([reader.next()?, reader.next()?, reader.next()?])).collect::<Option<Vec<_>>>()?)
                }
                17 => Modifier::NoiseThresholdCount {
                    level: double(m[1], m[2]),
                    below: m[3],
                    above: m[4],
                    noise: reader.noise(usize::try_from(m[5]).ok()?)?,
                },
                18 => {
                    let index = reader.count(1 << 16)?;
                    Modifier::NoiseBasedCount { ratio: m[1], factor: double(m[2], m[3]), offset: double(m[4], m[5]), noise: reader.noise(index)? }
                }
                19 if (0..6).contains(&m[1]) => Modifier::SurfaceRelativeThreshold { heightmap: m[1] as u32, min: m[2], max: m[3] },
                _ => return None,
            });
        }
        Some(Self { modifiers, feature: Feature::read(reader, depth)? })
    }

    pub(crate) fn sets(&self, out: &mut Vec<usize>) {
        for modifier in &self.modifiers {
            match modifier {
                Modifier::BlockPredicate(predicate) => predicate.sets(out),
                Modifier::CountOnEveryLayer { empty, bedrock, .. } => out.extend([*empty, *bedrock]),
                Modifier::EnvironmentScan { target, allowed, .. } => {
                    target.sets(out);
                    allowed.sets(out);
                }
                _ => {}
            }
        }
        self.feature.sets(out);
    }
}

/// A cursor over program words.
pub(crate) struct Reader<'w> {
    words: &'w [i32],
    pub(crate) at: usize,
    /// One past the highest set index read through `set`, checked against the set table.
    sets_used: usize,
    /// One past the highest noise index read, checked against the noise table.
    noises_used: usize,
    /// States features read through `write_state` may write.
    writes: Vec<u32>,
}

/// A `double` from its low and high words.
pub(crate) fn double(low: i32, high: i32) -> f64 {
    f64::from_bits((low as u32 as u64) | ((high as u32 as u64) << 32))
}

impl<'w> Reader<'w> {
    pub(crate) fn new(words: &'w [i32]) -> Self {
        Self { words, at: 0, sets_used: 0, noises_used: 0, writes: Vec::new() }
    }

    /// A set index, validated once the set table is known.
    pub(crate) fn set(&mut self) -> Option<usize> {
        let index = self.index()?;
        self.sets_used = self.sets_used.max(index + 1);
        Some(index)
    }

    pub(crate) fn noise(&mut self, index: usize) -> Option<usize> {
        self.noises_used = self.noises_used.max(index.checked_add(1)?);
        Some(index)
    }

    /// A state the feature writes through `setBlock`.
    pub(crate) fn write_state(&mut self) -> Option<u32> {
        let state = self.state()?;
        self.writes.push(state);
        Some(state)
    }

    /// Records states read some other way (providers) as writes.
    pub(crate) fn record_writes(&mut self, states: impl IntoIterator<Item = u32>) {
        self.writes.extend(states);
    }

    pub(crate) fn double(&mut self) -> Option<f64> {
        Some(double(self.next()?, self.next()?))
    }

    pub(crate) fn next(&mut self) -> Option<i32> {
        let value = *self.words.get(self.at)?;
        self.at += 1;
        Some(value)
    }

    pub(crate) fn take(&mut self, count: usize) -> Option<&'w [i32]> {
        let slice = self.words.get(self.at..self.at.checked_add(count)?)?;
        self.at += count;
        Some(slice)
    }

    /// A non-negative count no larger than `limit`.
    pub(crate) fn count(&mut self, limit: usize) -> Option<usize> {
        usize::try_from(self.next()?).ok().filter(|&n| n <= limit)
    }

    /// A set index; validated against the set table after decoding.
    pub(crate) fn index(&mut self) -> Option<usize> {
        usize::try_from(self.next()?).ok()
    }

    /// A state or registry ID.
    pub(crate) fn state(&mut self) -> Option<u32> {
        u32::try_from(self.next()?).ok()
    }

    pub(crate) fn float(&mut self) -> Option<f32> {
        Some(f32::from_bits(self.next()? as u32))
    }

    pub(crate) fn flag(&mut self) -> Option<bool> {
        match self.next()? {
            0 => Some(false),
            1 => Some(true),
            _ => None,
        }
    }
}

pub(crate) struct Program {
    pub(crate) placed: Placed,
    sets: Vec<Box<[u32]>>,
    biomes: Box<[u32]>,
    /// Addresses of Java-owned, validated native noise states.
    noises: Box<[usize]>,
    /// States decoded features write besides those `collect_set_block_states` finds.
    writes: Box<[u32]>,
}

fn bit(words: &[u32], index: u32) -> bool {
    words.get((index >> 5) as usize).is_some_and(|w| w & (1 << (index & 31)) != 0)
}

impl Program {
    pub(crate) fn in_set(&self, set: usize, state: u32) -> bool {
        bit(&self.sets[set], state)
    }

    /// `PerlinSimplexNoise.getValue(x, z, false)` (or a normal noise's `getValue(x, y, z)`) for a table entry.
    pub(crate) fn noise(&self, index: usize, x: f64, y: f64, z: f64) -> f64 {
        let state = self.noises[index] as *const crate::world::level::levelgen::synth::State;
        // SAFETY: Java validated the state and keeps it alive with the program.
        unsafe { crate::world::level::levelgen::synth::noise_eval(state, x, y, z, 0.0, 0.0, 0) }
    }

    /// Every state in a set, in ID order.
    pub(crate) fn set_states(&self, set: usize) -> impl Iterator<Item = u32> + '_ {
        self.sets[set].iter().enumerate().flat_map(|(word_index, &word)| {
            (0..32).filter(move |bit| word & (1 << bit) != 0).map(move |bit| (word_index * 32 + bit) as u32)
        })
    }

    pub(crate) fn biome_allowed(&self, biome: u32) -> bool {
        bit(&self.biomes, biome)
    }

    /// Every state a section write can replace or write has a counter policy,
    /// so no write can decline after the placement has started drawing.
    /// `setBlock` features replace whatever the world holds; the write path
    /// checks those against the installed facts before the first draw.
    pub(crate) fn writes_admitted(&self, policies: &[Option<crate::world::level::chunk::counters::Policy>]) -> bool {
        let admitted = |state: u32| policies.get(state as usize).is_some_and(Option::is_some);
        let mut ores = Vec::new();
        collect_ores(&self.placed.feature, &mut ores);
        ores.iter().all(|targets| targets.iter().all(|t| admitted(t.state) && self.set_states(t.set).all(admitted)))
            && self.placed.feature.set_block_states().into_iter().all(admitted)
            && self.writes.iter().all(|&state| admitted(state))
    }

    pub(crate) fn decode(words: &[i32]) -> Option<Self> {
        let mut reader = Reader::new(words);
        if reader.next()? != VERSION {
            return None;
        }
        let placed = Placed::read(&mut reader, 0, true)?;
        let set_count = reader.count(256)?;
        let mut sets = Vec::with_capacity(set_count);
        for _ in 0..set_count {
            let length = reader.count(1 << 16)?;
            sets.push(reader.take(length)?.iter().map(|&w| w as u32).collect::<Box<[u32]>>());
        }
        let biome_length = reader.count(1 << 16)?;
        let biomes = reader.take(biome_length)?.iter().map(|&w| w as u32).collect();
        let noise_count = reader.count(64)?;
        let mut noises = Vec::with_capacity(noise_count);
        for _ in 0..noise_count {
            let address = reader.next()? as u32 as u64 | ((reader.next()? as u32 as u64) << 32);
            if address == 0 || address % 8 != 0 {
                return None;
            }
            noises.push(address as usize);
        }
        if reader.at != words.len() || reader.sets_used > sets.len() || reader.noises_used > noises.len() {
            return None;
        }
        let mut used = Vec::new();
        placed.sets(&mut used);
        if used.iter().any(|&set| set >= sets.len()) {
            return None;
        }
        Some(Self { placed, sets, biomes, noises: noises.into(), writes: std::mem::take(&mut reader.writes).into() })
    }
}

fn collect_ores<'p>(feature: &'p Feature, out: &mut Vec<&'p [Target]>) {
    match feature {
        Feature::RandomPatch { placed, .. } => collect_ores(&placed.feature, out),
        Feature::Patch(patch) => collect_ores(&patch.vegetation.feature, out),
        Feature::Ore { targets, .. } => out.push(targets),
        Feature::RandomSelector { entries, default } => {
            entries.iter().for_each(|(_, p)| collect_ores(&p.feature, out));
            collect_ores(&default.feature, out);
        }
        Feature::SimpleRandomSelector(children) => children.iter().for_each(|p| collect_ores(&p.feature, out)),
        Feature::RandomBooleanSelector(a, b) => {
            collect_ores(&a.feature, out);
            collect_ores(&b.feature, out);
        }
        _ => {}
    }
}

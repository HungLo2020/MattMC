//! Compiled placed-feature programs. Java resolves registry, tag and biome
//! questions once per generator/context and encodes the answers as words;
//! Rust validates them on creation and retains the program until Java releases it.

/// Program word layout version.
pub(crate) const VERSION: i32 = 1;
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

#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) enum Modifier {
    /// `CountPlacement` with `ConstantInt`.
    CountConstant(i32),
    /// `CountPlacement` with `UniformInt`.
    CountUniform(i32, i32),
    InSquare,
    /// `HeightRangePlacement` with `UniformHeight`.
    HeightUniform(Anchor, Anchor),
    /// `HeightRangePlacement` with `TrapezoidHeight` and its plateau.
    HeightTrapezoid(Anchor, Anchor, i32),
    /// `HeightRangePlacement` with `BiasedToBottomHeight` and its inner value.
    HeightBiasedToBottom(Anchor, Anchor, i32),
    /// `BiomeFilter` against the program's biome set.
    Biome,
    /// `RarityFilter` with its chance.
    Rarity(i32),
}

/// `OreConfiguration.TargetBlockState`'s rule, compiled to a state set.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Target {
    pub(crate) set: usize,
    /// `RandomBlockMatchTest`/`RandomBlockStateMatchTest`: a draw only after a set match.
    pub(crate) probability: Option<f32>,
    pub(crate) state: u32,
}

#[derive(Clone, Debug, PartialEq)]
pub(crate) enum Feature {
    Ore { size: i32, discard_chance_on_air_exposure: f32, targets: Vec<Target> },
}

pub(crate) struct Program {
    pub(crate) modifiers: Vec<Modifier>,
    pub(crate) feature: Feature,
    sets: Vec<Box<[u32]>>,
    biomes: Box<[u32]>,
    /// Ore shape factors: per size, `size` pairs of (fraction, `Mth.sin` + 1).
    pub(crate) ore_shape: Box<[f64]>,
}

fn bit(words: &[u32], index: u32) -> bool {
    words.get((index >> 5) as usize).is_some_and(|w| w & (1 << (index & 31)) != 0)
}

impl Program {
    pub(crate) fn in_set(&self, set: usize, state: u32) -> bool {
        bit(&self.sets[set], state)
    }

    /// Every state a target can replace or write has a counter policy, so no
    /// section write can decline after the placement has started drawing.
    pub(crate) fn writes_admitted(&self, policies: &[Option<crate::world::level::chunk::counters::Policy>]) -> bool {
        let Feature::Ore { ref targets, .. } = self.feature;
        targets.iter().all(|target| {
            policies.get(target.state as usize).is_some_and(Option::is_some)
                && self.sets[target.set].iter().enumerate().all(|(word_index, &word)| {
                    (0..32).filter(|bit| word & (1 << bit) != 0).all(|bit| {
                        policies.get(word_index * 32 + bit).is_some_and(Option::is_some)
                    })
                })
        })
    }

    pub(crate) fn biome_allowed(&self, biome: u32) -> bool {
        bit(&self.biomes, biome)
    }

    pub(crate) fn decode(words: &[i32]) -> Option<Self> {
        let mut at = 0;
        let mut next = |count: usize| -> Option<&[i32]> {
            let slice = words.get(at..at + count)?;
            at += count;
            Some(slice)
        };
        let header = next(2)?;
        if header[0] != VERSION {
            return None;
        }
        let modifier_count = usize::try_from(header[1]).ok().filter(|&n| n <= 64)?;
        let mut modifiers = Vec::with_capacity(modifier_count);
        for _ in 0..modifier_count {
            let m = next(MODIFIER_WORDS)?;
            modifiers.push(match m[0] {
                1 => Modifier::CountConstant(m[1]),
                2 if m[1] <= m[2] => Modifier::CountUniform(m[1], m[2]),
                3 => Modifier::InSquare,
                4 => Modifier::HeightUniform(Anchor::decode(m[1], m[2])?, Anchor::decode(m[3], m[4])?),
                5 => Modifier::HeightTrapezoid(Anchor::decode(m[1], m[2])?, Anchor::decode(m[3], m[4])?, m[5]),
                6 => Modifier::HeightBiasedToBottom(Anchor::decode(m[1], m[2])?, Anchor::decode(m[3], m[4])?, m[5]),
                7 => Modifier::Biome,
                8 if m[1] > 0 => Modifier::Rarity(m[1]),
                _ => return None,
            });
        }
        let feature_header = next(4)?;
        let feature = match feature_header[0] {
            1 => {
                let size = feature_header[1];
                let target_count = usize::try_from(feature_header[3]).ok().filter(|&n| n <= 64)?;
                let mut targets = Vec::with_capacity(target_count);
                for _ in 0..target_count {
                    let t = next(4)?;
                    let set = usize::try_from(t[2]).ok()?;
                    let probability = match t[0] {
                        1 => None,
                        2 => Some(f32::from_bits(t[1] as u32)),
                        _ => return None,
                    };
                    targets.push(Target { set, probability, state: u32::try_from(t[3]).ok()? });
                }
                if !(0..=64).contains(&size) {
                    return None;
                }
                Feature::Ore {
                    size,
                    discard_chance_on_air_exposure: f32::from_bits(feature_header[2] as u32),
                    targets,
                }
            }
            _ => return None,
        };
        let set_count = usize::try_from(next(1)?[0]).ok().filter(|&n| n <= 64)?;
        let mut sets = Vec::with_capacity(set_count);
        for _ in 0..set_count {
            let length = usize::try_from(next(1)?[0]).ok().filter(|&n| n <= 1 << 16)?;
            sets.push(next(length)?.iter().map(|&w| w as u32).collect::<Box<[u32]>>());
        }
        let biome_length = usize::try_from(next(1)?[0]).ok().filter(|&n| n <= 1 << 16)?;
        let biomes = next(biome_length)?.iter().map(|&w| w as u32).collect();
        if at != words.len() {
            return None;
        }
        let Feature::Ore { size, ref targets, .. } = feature;
        if targets.iter().any(|t| t.set >= sets.len()) {
            return None;
        }
        let ore_shape = super::ore::shape(size);
        Some(Self { modifiers, feature, sets, biomes, ore_shape })
    }
}

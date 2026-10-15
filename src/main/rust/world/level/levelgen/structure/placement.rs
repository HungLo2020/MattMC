//! `StructurePlacement.isStructureChunk` for a generator's structure sets:
//! random-spread grids and concentric rings, with their frequency reducers and
//! exclusion zones. Java builds the table once per structure state (after the
//! rings exist) and asks, per chunk, which sets place there.
use crate::world::level::levelgen::random::Legacy;
use std::collections::HashSet;

/// `StructurePlacement.FrequencyReductionMethod`.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum Reducer {
    Default,
    /// `legacy_type_1`: the pillager outpost's.
    Legacy1,
    /// `legacy_type_2`: an arbitrary fixed salt.
    Legacy2,
    /// `legacy_type_3`: a double draw.
    Legacy3,
}

#[derive(Clone, Debug)]
enum Kind {
    /// `RandomSpreadStructurePlacement`: spacing, separation, triangular spread.
    Spread { spacing: i32, separation: i32, triangular: bool },
    /// `ConcentricRingsStructurePlacement`: its ring chunks.
    Rings(HashSet<(i32, i32)>),
    /// A placement Java evaluates itself.
    Java,
}

#[derive(Clone, Debug)]
struct Placement {
    kind: Kind,
    frequency: f32,
    reducer: Reducer,
    salt: i32,
    /// The other set's index and the chunk count.
    exclusion: Option<(usize, i32)>,
}

pub(crate) struct Placements {
    seed: i64,
    list: Vec<Placement>,
}

/// `WorldgenRandom.setLargeFeatureWithSalt(seed, x, z, salt)`.
fn large_feature_with_salt(seed: i64, x: i32, z: i32, salt: i32) -> Legacy {
    Legacy::new((x as i64).wrapping_mul(341_873_128_712).wrapping_add((z as i64).wrapping_mul(132_897_987_541)).wrapping_add(seed).wrapping_add(salt as i64))
}

impl Reducer {
    fn should_generate(self, seed: i64, salt: i32, x: i32, z: i32, frequency: f32) -> bool {
        match self {
            Reducer::Default => large_feature_with_salt(seed, x, z, salt).next_float() < frequency,
            Reducer::Legacy1 => {
                let (m, n) = (x >> 4, z >> 4);
                let mut random = Legacy::new(((m ^ n.wrapping_shl(4)) as i64) ^ seed);
                random.next(32);
                random.next_int_bound((1.0f32 / frequency) as i32) == 0
            }
            Reducer::Legacy2 => large_feature_with_salt(seed, x, z, 10_387_320).next_float() < frequency,
            Reducer::Legacy3 => {
                let mut random = Legacy::new(0);
                random.set_large_feature_seed(seed, x, z);
                random.next_double() < f64::from(frequency)
            }
        }
    }
}

impl Placements {
    /// Words: `[count, per placement: kind (0 spread, 1 rings, 2 Java), frequency
    /// bits, reducer, salt, exclusion set (-1 none), exclusion chunk count, then
    /// spread: spacing, separation, triangular; rings: chunk count, (x, z) each]`.
    pub(crate) fn decode(seed: i64, words: &[i32]) -> Option<Self> {
        let mut at = 0;
        let mut next = || -> Option<i32> {
            let word = *words.get(at)?;
            at += 1;
            Some(word)
        };
        let count = usize::try_from(next()?).ok()?;
        let mut list = Vec::with_capacity(count);
        for _ in 0..count {
            let kind = next()?;
            let frequency = f32::from_bits(next()? as u32);
            let reducer = match next()? {
                0 => Reducer::Default,
                1 => Reducer::Legacy1,
                2 => Reducer::Legacy2,
                3 => Reducer::Legacy3,
                _ => return None,
            };
            let salt = next()?;
            let (other, chunks) = (next()?, next()?);
            let exclusion = if other < 0 { None } else { Some((usize::try_from(other).ok()?, chunks)) };
            let kind = match kind {
                0 => {
                    let (spacing, separation, triangular) = (next()?, next()?, next()? != 0);
                    if spacing <= separation || separation < 0 {
                        return None;
                    }
                    Kind::Spread { spacing, separation, triangular }
                }
                1 => {
                    let n = usize::try_from(next()?).ok()?;
                    let mut set = HashSet::with_capacity(n);
                    for _ in 0..n {
                        set.insert((next()?, next()?));
                    }
                    Kind::Rings(set)
                }
                2 => Kind::Java,
                _ => return None,
            };
            list.push(Placement { kind, frequency, reducer, salt, exclusion });
        }
        if at != words.len() || list.iter().any(|p| p.exclusion.is_some_and(|(o, c)| o >= list.len() || !(1..=16).contains(&c))) {
            return None;
        }
        Some(Self { seed, list })
    }

    /// `isStructureChunk`, or None when Java must evaluate the placement.
    pub(crate) fn is_structure_chunk(&self, index: usize, x: i32, z: i32) -> Option<bool> {
        self.check(index, x, z, 0)
    }

    fn check(&self, index: usize, x: i32, z: i32, depth: u32) -> Option<bool> {
        let p = self.list.get(index)?;
        let placement = match &p.kind {
            Kind::Spread { spacing, separation, triangular } => {
                let (k, m) = (x.div_euclid(*spacing), z.div_euclid(*spacing));
                let mut random = large_feature_with_salt(self.seed, k, m, p.salt);
                let n = spacing - separation;
                let mut evaluate = || if *triangular { (random.next_int_bound(n) + random.next_int_bound(n)) / 2 } else { random.next_int_bound(n) };
                let o = evaluate();
                let q = evaluate();
                k.wrapping_mul(*spacing).wrapping_add(o) == x && m.wrapping_mul(*spacing).wrapping_add(q) == z
            }
            Kind::Rings(set) => set.contains(&(x, z)),
            Kind::Java => return None,
        };
        if !placement || !(p.frequency >= 1.0 || p.reducer.should_generate(self.seed, p.salt, x, z, p.frequency)) {
            return Some(false);
        }
        if let Some((other, chunks)) = p.exclusion {
            // `ExclusionZone.isPlacementForbidden`: the other set placed within range.
            if depth > 8 {
                return None;
            }
            for l in x - chunks..=x + chunks {
                for m in z - chunks..=z + chunks {
                    if self.check(other, l, m, depth + 1)? {
                        return Some(false);
                    }
                }
            }
        }
        Some(true)
    }

    pub(crate) fn len(&self) -> usize {
        self.list.len()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn spread_places_one_chunk_per_cell() {
        // A village-like grid: spacing 34, separation 8, linear.
        let words = [1, 0, 1.0f32.to_bits() as i32, 0, 10387312, -1, 0, 34, 8, 0];
        let placements = Placements::decode(42, &words).expect("placements");
        let mut found = 0;
        for x in 0..34 {
            for z in 0..34 {
                if placements.is_structure_chunk(0, x, z) == Some(true) {
                    found += 1;
                }
            }
        }
        assert_eq!(found, 1);
    }
}

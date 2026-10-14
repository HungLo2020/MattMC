//! `WorldgenRandom` over `XoroshiroRandomSource`, as `ChunkGenerator.applyBiomeDecoration`
//! creates it. Draws go through `BitRandomSource`'s derivations of `next(bits)`,
//! which takes the top bits of one Xoroshiro long; they are not Xoroshiro's own
//! `nextInt`/`nextFloat`.
use crate::world::level::levelgen::random::Xoroshiro;

const GOLDEN_RATIO_64: i64 = -7_046_029_254_386_353_131;
const SILVER_RATIO_64: i64 = 7_640_891_576_956_012_809;

/// `RandomSupport.mixStafford13`.
pub(crate) fn mix_stafford13(mut seed: i64) -> i64 {
    seed = (seed ^ ((seed as u64) >> 30) as i64).wrapping_mul(-4_658_895_280_553_007_687);
    seed = (seed ^ ((seed as u64) >> 27) as i64).wrapping_mul(-7_723_592_293_110_705_685);
    seed ^ ((seed as u64) >> 31) as i64
}

pub(crate) struct WorldgenRandom {
    source: Xoroshiro,
}

impl WorldgenRandom {
    pub(crate) fn new(seed: i64) -> Self {
        let mut random = Self { source: Xoroshiro::new(0, 0) };
        random.set_seed(seed);
        random
    }

    /// `XoroshiroRandomSource.setSeed`: `upgradeSeedTo128bit`, then mixed.
    pub(crate) fn set_seed(&mut self, seed: i64) {
        let lo = seed ^ SILVER_RATIO_64;
        let hi = lo.wrapping_add(GOLDEN_RATIO_64);
        self.source = Xoroshiro::new(mix_stafford13(lo), mix_stafford13(hi));
    }

    /// `WorldgenRandom.setDecorationSeed`; returns the seed it installs.
    pub(crate) fn set_decoration_seed(&mut self, seed: i64, x: i32, z: i32) -> i64 {
        self.set_seed(seed);
        let m = self.next_long() | 1;
        let n = self.next_long() | 1;
        let result = (x as i64).wrapping_mul(m).wrapping_add((z as i64).wrapping_mul(n)) ^ seed;
        self.set_seed(result);
        result
    }

    /// `WorldgenRandom.setFeatureSeed`.
    pub(crate) fn set_feature_seed(&mut self, decoration: i64, index: i32, step: i32) {
        self.set_seed(decoration.wrapping_add(index as i64).wrapping_add(10_000i64.wrapping_mul(step as i64)));
    }

    /// `WorldgenRandom.next`: the high bits of one Xoroshiro long.
    pub(crate) fn next(&mut self, bits: u32) -> i32 {
        ((self.source.next_long() as u64) >> (64 - bits)) as i32
    }

    /// `BitRandomSource.nextInt(int)`; `bound` must be positive.
    pub(crate) fn next_int(&mut self, bound: i32) -> i32 {
        debug_assert!(bound > 0);
        if bound & (bound - 1) == 0 {
            return ((bound as i64 * self.next(31) as i64) >> 31) as i32;
        }
        loop {
            let j = self.next(31);
            let k = j % bound;
            if j.wrapping_sub(k).wrapping_add(bound - 1) >= 0 {
                return k;
            }
        }
    }

    /// `Mth.randomBetweenInclusive`.
    pub(crate) fn between_inclusive(&mut self, min: i32, max: i32) -> i32 {
        self.next_int(max.wrapping_sub(min).wrapping_add(1)).wrapping_add(min)
    }

    pub(crate) fn next_long(&mut self) -> i64 {
        let high = self.next(32) as i64;
        let low = self.next(32) as i64;
        (high << 32).wrapping_add(low)
    }

    pub(crate) fn next_float(&mut self) -> f32 {
        self.next(24) as f32 * 5.960_464_5e-8_f32
    }

    /// `BitRandomSource.nextDouble`: the scale is a float, so this has float precision.
    pub(crate) fn next_double(&mut self) -> f64 {
        let high = self.next(26) as i64;
        let low = self.next(27) as i64;
        f64::from(((high << 27) + low) as f32 * 1.110_223e-16_f32)
    }
}

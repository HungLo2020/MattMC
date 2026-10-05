//! Bit-exact ports of Java's world-generation randomness: `Xoroshiro128PlusPlus`
//! behind `XoroshiroRandomSource`, the 48-bit LCG behind `LegacyRandomSource`,
//! and their positional factories' `at(x, y, z)`. Only the draws world
//! generation uses natively are provided; Java keeps every other caller.

/// `Mth.getSeed(int, int, int)`: the x product is an int multiply, sign-extended.
pub(crate) fn position_seed(x: i32, y: i32, z: i32) -> i64 {
    let l = (x.wrapping_mul(3_129_871) as i64) ^ (z as i64).wrapping_mul(116_129_781) ^ (y as i64);
    let l = l.wrapping_mul(l).wrapping_mul(42_317_861).wrapping_add(l.wrapping_mul(11));
    l >> 16
}

/// `XoroshiroRandomSource`.
pub(crate) struct Xoroshiro {
    lo: i64,
    hi: i64,
}

impl Xoroshiro {
    /// `Xoroshiro128PlusPlus(long, long)`, including its all-zero replacement.
    pub fn new(lo: i64, hi: i64) -> Self {
        if (lo | hi) == 0 {
            Self { lo: -7_046_029_254_386_353_131, hi: 7_640_891_576_956_012_809 }
        } else {
            Self { lo, hi }
        }
    }

    pub fn next_long(&mut self) -> i64 {
        let l = self.lo;
        let m = self.hi;
        let n = l.wrapping_add(m).rotate_left(17).wrapping_add(l);
        let m = m ^ l;
        self.lo = l.rotate_left(49) ^ m ^ (m << 21);
        self.hi = m.rotate_left(28);
        n
    }

    pub fn next_int(&mut self) -> i32 {
        self.next_long() as i32
    }

    /// `XoroshiroRandomSource.nextInt(int)`: unsigned multiply-high with rejection.
    pub fn next_int_bound(&mut self, bound: i32) -> i32 {
        debug_assert!(bound > 0);
        let bound64 = bound as i64;
        let mut l = self.next_int() as u32 as i64;
        let mut m = l * bound64;
        let mut n = m & 0xffff_ffff;
        if n < bound64 {
            let j = ((!bound).wrapping_add(1) as u32 % bound as u32) as i64;
            while n < j {
                l = self.next_int() as u32 as i64;
                m = l * bound64;
                n = m & 0xffff_ffff;
            }
        }
        (m >> 32) as i32
    }

    pub fn next_float(&mut self) -> f32 {
        ((self.next_long() as u64) >> 40) as f32 * 5.960_464_5e-8_f32
    }
}

/// `LegacyRandomSource` (single-threaded; the Java atomics only detect misuse).
pub(crate) struct Legacy {
    seed: i64,
}

const MULTIPLIER: i64 = 0x5_DEEC_E66D;
const MASK: i64 = (1 << 48) - 1;

impl Legacy {
    pub fn new(seed: i64) -> Self {
        Self { seed: (seed ^ MULTIPLIER) & MASK }
    }

    pub fn next(&mut self, bits: u32) -> i32 {
        self.seed = self.seed.wrapping_mul(MULTIPLIER).wrapping_add(11) & MASK;
        (self.seed >> (48 - bits)) as i32
    }

    /// `BitRandomSource.nextInt(int)`.
    pub fn next_int_bound(&mut self, bound: i32) -> i32 {
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

    pub fn next_float(&mut self) -> f32 {
        self.next(24) as f32 * 5.960_464_5e-8_f32
    }
}

pub(crate) enum Random {
    Xoroshiro(Xoroshiro),
    Legacy(Legacy),
}

impl Random {
    pub fn next_int_bound(&mut self, bound: i32) -> i32 {
        match self {
            Random::Xoroshiro(random) => random.next_int_bound(bound),
            Random::Legacy(random) => random.next_int_bound(bound),
        }
    }

    pub fn next_float(&mut self) -> f32 {
        match self {
            Random::Xoroshiro(random) => random.next_float(),
            Random::Legacy(random) => random.next_float(),
        }
    }
}

/// `XoroshiroPositionalRandomFactory` / `LegacyPositionalRandomFactory`.
#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) enum Positional {
    Xoroshiro { lo: i64, hi: i64 },
    Legacy { seed: i64 },
}

impl Positional {
    /// ABI kind 1 is Xoroshiro (`a` = seedLo, `b` = seedHi), 2 is Legacy (`a` = seed).
    pub fn from_abi(kind: i32, a: i64, b: i64) -> Option<Self> {
        match kind {
            1 => Some(Positional::Xoroshiro { lo: a, hi: b }),
            2 => Some(Positional::Legacy { seed: a }),
            _ => None,
        }
    }

    pub fn at(&self, x: i32, y: i32, z: i32) -> Random {
        let seed = position_seed(x, y, z);
        match *self {
            Positional::Xoroshiro { lo, hi } => Random::Xoroshiro(Xoroshiro::new(seed ^ lo, hi)),
            Positional::Legacy { seed: factory } => Random::Legacy(Legacy::new(seed ^ factory)),
        }
    }
}

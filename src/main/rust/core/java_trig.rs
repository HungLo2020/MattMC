//! Bit-exact `java.lang.Math.sin/cos` as HotSpot computes them on x86_64.
//!
//! HotSpot replaces both methods with Intel LIBM stubs in the interpreter and
//! both compilers (`stubGenerator_x86_64_{sin,cos}.cpp`, `_constants.cpp`,
//! jdk-25+36). Their results differ from the platform libm in the last bit for
//! some inputs, which moves world-generation geometry. This module transcribes
//! the stubs' main path instruction by instruction: each `X` is an XMM register
//! (low, high lane), and every operation keeps SSE2 rounding without FMA.
//! Inputs outside the main path's range other than zero return `None`; the
//! caller must keep the JVM for them.

const ONEHALF: [u64; 2] = [0x3fe0000000000000, 0x3fe0000000000000];
const SIGN_MASK: u64 = 0x8000000000000000;
const SC_1: [u64; 2] = [0xbfc5555555555555, 0xbfe0000000000000];
const SC_2: [u64; 2] = [0x3f81111111111111, 0x3fa5555555555555];
const SC_3: [u64; 2] = [0xbf2a01a01a01a01a, 0xbf56c16c16c16c17];
const SC_4: [u64; 2] = [0x3ec71de3a556c734, 0x3efa01a01a01a01a];
const PI32INV: u64 = 0x40245f306dc9c883;
const P_1: u64 = 0x3fb921fb54400000;
const P_2: [u64; 2] = [0x3d90b4611a600000, 0x3d90b4611a600000];
const P_3: u64 = 0x3b63198a2e037073;
const ALL_ONES: u64 = 0x3fefffffffffffff;
const ONE: u64 = 0x3ff0000000000000;
/// 64 rows of four doubles: indexed by N mod 64 of the pi/32 reduction.
const CTABLE: [u64; 256] = [
    0x0000000000000000, 0x0000000000000000, 0x0000000000000000, 0x3ff0000000000000,
    0xbf73b92e176d6d31, 0x3fb917a6bc29b42c, 0xbc3e2718e0000000, 0x3ff0000000000000,
    0xbf93ad06011469fb, 0x3fc8f8b83c69a60b, 0xbc626d19c0000000, 0x3ff0000000000000,
    0xbfa60bea939d225a, 0x3fd294062ed59f06, 0xbc75d28da0000000, 0x3ff0000000000000,
    0xbfb37ca1866b95cf, 0x3fd87de2a6aea963, 0xbc672cede0000000, 0x3ff0000000000000,
    0xbfbe3a6873fa1279, 0x3fde2b5d3806f63b, 0x3c5e0d8920000000, 0x3ff0000000000000,
    0xbfc592675bc57974, 0x3fe1c73b39ae68c8, 0x3c8b25dd20000000, 0x3ff0000000000000,
    0xbfcd0dfe53aba2fd, 0x3fe44cf325091dd6, 0x3c68076a20000000, 0x3ff0000000000000,
    0x3fca827999fcef32, 0x3fe6a09e667f3bcd, 0xbc8bdd3420000000, 0x3fe0000000000000,
    0x3fc133cc94247758, 0x3fe8bc806b151741, 0xbc82c5e120000000, 0x3fe0000000000000,
    0x3fac73b39ae68c87, 0x3fea9b66290ea1a3, 0x3c39f630e0000000, 0x3fe0000000000000,
    0xbf9d4a2c7f909c4e, 0x3fec38b2f180bdb1, 0xbc76e0b180000000, 0x3fe0000000000000,
    0xbfbe087565455a75, 0x3fed906bcf328d46, 0x3c7457e620000000, 0x3fe0000000000000,
    0x3fa4a03176acf82d, 0x3fee9f4156c62dda, 0x3c8760b1e0000000, 0x3fd0000000000000,
    0xbfac1d1f0e5967d5, 0x3fef6297cff75cb0, 0x3c75621720000000, 0x3fd0000000000000,
    0xbf9ba1650f592f50, 0x3fefd88da3d12526, 0xbc887df640000000, 0x3fc0000000000000,
    0x0000000000000000, 0x3ff0000000000000, 0x0000000000000000, 0x0000000000000000,
    0x3f9ba1650f592f50, 0x3fefd88da3d12526, 0xbc887df640000000, 0xbfc0000000000000,
    0x3fac1d1f0e5967d5, 0x3fef6297cff75cb0, 0x3c75621720000000, 0xbfd0000000000000,
    0xbfa4a03176acf82d, 0x3fee9f4156c62dda, 0x3c8760b1e0000000, 0xbfd0000000000000,
    0x3fbe087565455a75, 0x3fed906bcf328d46, 0x3c7457e620000000, 0xbfe0000000000000,
    0x3f9d4a2c7f909c4e, 0x3fec38b2f180bdb1, 0xbc76e0b180000000, 0xbfe0000000000000,
    0xbfac73b39ae68c87, 0x3fea9b66290ea1a3, 0x3c39f630e0000000, 0xbfe0000000000000,
    0xbfc133cc94247758, 0x3fe8bc806b151741, 0xbc82c5e120000000, 0xbfe0000000000000,
    0xbfca827999fcef32, 0x3fe6a09e667f3bcd, 0xbc8bdd3420000000, 0xbfe0000000000000,
    0x3fcd0dfe53aba2fd, 0x3fe44cf325091dd6, 0x3c68076a20000000, 0xbff0000000000000,
    0x3fc592675bc57974, 0x3fe1c73b39ae68c8, 0x3c8b25dd20000000, 0xbff0000000000000,
    0x3fbe3a6873fa1279, 0x3fde2b5d3806f63b, 0x3c5e0d8920000000, 0xbff0000000000000,
    0x3fb37ca1866b95cf, 0x3fd87de2a6aea963, 0xbc672cede0000000, 0xbff0000000000000,
    0x3fa60bea939d225a, 0x3fd294062ed59f06, 0xbc75d28da0000000, 0xbff0000000000000,
    0x3f93ad06011469fb, 0x3fc8f8b83c69a60b, 0xbc626d19c0000000, 0xbff0000000000000,
    0x3f73b92e176d6d31, 0x3fb917a6bc29b42c, 0xbc3e2718e0000000, 0xbff0000000000000,
    0x0000000000000000, 0x0000000000000000, 0x0000000000000000, 0xbff0000000000000,
    0x3f73b92e176d6d31, 0xbfb917a6bc29b42c, 0x3c3e2718e0000000, 0xbff0000000000000,
    0x3f93ad06011469fb, 0xbfc8f8b83c69a60b, 0x3c626d19c0000000, 0xbff0000000000000,
    0x3fa60bea939d225a, 0xbfd294062ed59f06, 0x3c75d28da0000000, 0xbff0000000000000,
    0x3fb37ca1866b95cf, 0xbfd87de2a6aea963, 0x3c672cede0000000, 0xbff0000000000000,
    0x3fbe3a6873fa1279, 0xbfde2b5d3806f63b, 0xbc5e0d8920000000, 0xbff0000000000000,
    0x3fc592675bc57974, 0xbfe1c73b39ae68c8, 0xbc8b25dd20000000, 0xbff0000000000000,
    0x3fcd0dfe53aba2fd, 0xbfe44cf325091dd6, 0xbc68076a20000000, 0xbff0000000000000,
    0xbfca827999fcef32, 0xbfe6a09e667f3bcd, 0x3c8bdd3420000000, 0xbfe0000000000000,
    0xbfc133cc94247758, 0xbfe8bc806b151741, 0x3c82c5e120000000, 0xbfe0000000000000,
    0xbfac73b39ae68c87, 0xbfea9b66290ea1a3, 0xbc39f630e0000000, 0xbfe0000000000000,
    0x3f9d4a2c7f909c4e, 0xbfec38b2f180bdb1, 0x3c76e0b180000000, 0xbfe0000000000000,
    0x3fbe087565455a75, 0xbfed906bcf328d46, 0xbc7457e620000000, 0xbfe0000000000000,
    0xbfa4a03176acf82d, 0xbfee9f4156c62dda, 0xbc8760b1e0000000, 0xbfd0000000000000,
    0x3fac1d1f0e5967d5, 0xbfef6297cff75cb0, 0xbc75621720000000, 0xbfd0000000000000,
    0x3f9ba1650f592f50, 0xbfefd88da3d12526, 0x3c887df640000000, 0xbfc0000000000000,
    0x0000000000000000, 0xbff0000000000000, 0x0000000000000000, 0x0000000000000000,
    0xbf9ba1650f592f50, 0xbfefd88da3d12526, 0x3c887df640000000, 0x3fc0000000000000,
    0xbfac1d1f0e5967d5, 0xbfef6297cff75cb0, 0xbc75621720000000, 0x3fd0000000000000,
    0x3fa4a03176acf82d, 0xbfee9f4156c62dda, 0xbc8760b1e0000000, 0x3fd0000000000000,
    0xbfbe087565455a75, 0xbfed906bcf328d46, 0xbc7457e620000000, 0x3fe0000000000000,
    0xbf9d4a2c7f909c4e, 0xbfec38b2f180bdb1, 0x3c76e0b180000000, 0x3fe0000000000000,
    0x3fac73b39ae68c87, 0xbfea9b66290ea1a3, 0xbc39f630e0000000, 0x3fe0000000000000,
    0x3fc133cc94247758, 0xbfe8bc806b151741, 0x3c82c5e120000000, 0x3fe0000000000000,
    0x3fca827999fcef32, 0xbfe6a09e667f3bcd, 0x3c8bdd3420000000, 0x3fe0000000000000,
    0xbfcd0dfe53aba2fd, 0xbfe44cf325091dd6, 0xbc68076a20000000, 0x3ff0000000000000,
    0xbfc592675bc57974, 0xbfe1c73b39ae68c8, 0xbc8b25dd20000000, 0x3ff0000000000000,
    0xbfbe3a6873fa1279, 0xbfde2b5d3806f63b, 0xbc5e0d8920000000, 0x3ff0000000000000,
    0xbfb37ca1866b95cf, 0xbfd87de2a6aea963, 0x3c672cede0000000, 0x3ff0000000000000,
    0xbfa60bea939d225a, 0xbfd294062ed59f06, 0x3c75d28da0000000, 0x3ff0000000000000,
    0xbf93ad06011469fb, 0xbfc8f8b83c69a60b, 0x3c626d19c0000000, 0x3ff0000000000000,
    0xbf73b92e176d6d31, 0xbfb917a6bc29b42c, 0x3c3e2718e0000000, 0x3ff0000000000000,];

#[derive(Clone, Copy)]
struct X(f64, f64);

fn d(bits: u64) -> f64 { f64::from_bits(bits) }
fn pair(bits: [u64; 2]) -> X { X(d(bits[0]), d(bits[1])) }
fn t(row: usize, index: usize) -> f64 { d(CTABLE[row * 4 + index]) }
fn mulpd(a: X, b: X) -> X { X(a.0 * b.0, a.1 * b.1) }
fn addpd(a: X, b: X) -> X { X(a.0 + b.0, a.1 + b.1) }
fn subpd(a: X, b: X) -> X { X(a.0 - b.0, a.1 - b.1) }
fn dup(a: X) -> X { X(a.0, a.0) }

/// `hi & 0x7fff0000` minus the main path's lower bound: `None` above, as in the stub's unsigned compare.
fn main_path(x: f64) -> bool {
    let hi = (x.to_bits() >> 32) as u32;
    (hi & 0x7fff_0000).wrapping_sub(0x3030_0000) <= 0x10c5_0000
}

/// Reduction shared by both stubs up to the table row: returns (xmm1 after `cvtsi2sdl`, N).
fn reduce(x: f64) -> (X, i32) {
    let mut xmm1 = X(d(PI32INV), 0.0);
    xmm1.0 *= x;
    let xmm4 = X(d(SIGN_MASK & x.to_bits()), 0.0);
    let xmm5 = pair(ONEHALF);
    let xmm5 = X(d(xmm5.0.to_bits() | xmm4.0.to_bits()), xmm5.1);
    xmm1 = addpd(xmm1, xmm5);
    // cvttsd2sil: truncation; the main-path bound keeps it in range.
    let n = xmm1.0 as i32;
    xmm1.0 = f64::from(n);
    (xmm1, n)
}

/// HotSpot `Math.sin` on x86_64, for zero and the stub's main path.
pub(crate) fn sin(x: f64) -> Option<f64> {
    if !main_path(x) {
        let hi = (x.to_bits() >> 32) as u32;
        // Signed "not greater" and exponent field 3325: zero or a tiny input.
        let shifted = (hi & 0x7fff_0000).wrapping_sub(0x3030_0000) >> 20;
        return (x == 0.0 && shifted == 3325).then(|| x * d(ALL_ONES));
    }
    let (mut xmm1, n) = reduce(x);
    let mut xmm6 = pair(P_2);
    let mut xmm3 = X(d(P_1), 0.0);
    let mut xmm5 = pair(SC_4);
    let mut xmm4 = X(x, x);
    xmm3.0 *= xmm1.0;
    xmm1 = dup(xmm1);
    let row = (n & 63) as usize;
    xmm6 = mulpd(xmm6, xmm1);
    xmm1.0 *= d(P_3);
    xmm4.0 -= xmm3.0;
    let mut xmm7 = X(t(row, 1), 0.0);
    let mut xmm0 = X(x, 0.0);
    xmm0.0 -= xmm3.0;
    xmm3 = dup(xmm4);
    xmm4.0 -= xmm6.0;
    xmm0 = dup(xmm0);
    let mut xmm2 = X(t(row, 0), t(row, 1));
    xmm5 = mulpd(xmm5, xmm0);
    xmm0 = subpd(xmm0, xmm6);
    xmm7.0 *= xmm4.0;
    xmm3.0 -= xmm4.0;
    xmm5 = mulpd(xmm5, xmm0);
    xmm0 = mulpd(xmm0, xmm0);
    xmm3.0 -= xmm6.0;
    xmm6 = pair(SC_2);
    xmm1.0 -= xmm3.0;
    xmm3 = X(t(row, 3), 0.0);
    xmm2.0 += xmm3.0;
    xmm7.0 -= xmm2.0;
    xmm2.0 *= xmm4.0;
    xmm6 = mulpd(xmm6, xmm0);
    xmm3.0 *= xmm4.0;
    xmm2 = mulpd(xmm2, xmm0);
    xmm0 = mulpd(xmm0, xmm0);
    xmm5 = addpd(xmm5, pair(SC_3));
    xmm4.0 *= t(row, 0);
    xmm6 = addpd(xmm6, pair(SC_1));
    xmm5 = mulpd(xmm5, xmm0);
    xmm0 = xmm3;
    xmm3.0 += t(row, 1);
    xmm1 = mulpd(xmm1, xmm7);
    xmm7 = xmm4;
    xmm4.0 += xmm3.0;
    xmm6 = addpd(xmm6, xmm5);
    xmm5 = X(t(row, 1), 0.0);
    xmm5.0 -= xmm3.0;
    xmm3.0 -= xmm4.0;
    xmm1.0 += t(row, 2);
    xmm6 = mulpd(xmm6, xmm2);
    xmm5.0 += xmm0.0;
    xmm3.0 += xmm7.0;
    xmm1.0 += xmm5.0;
    xmm1.0 += xmm3.0;
    xmm1.0 += xmm6.0;
    xmm6 = X(xmm6.1, xmm6.1);
    xmm0 = xmm4;
    xmm1.0 += xmm6.0;
    xmm0.0 += xmm1.0;
    Some(xmm0.0)
}

/// HotSpot `Math.cos` on x86_64, for zero and the stub's main path.
pub(crate) fn cos(x: f64) -> Option<f64> {
    if !main_path(x) {
        // Signed "not greater": 1 - |x| for zero and tiny inputs.
        return (x == 0.0).then(|| d(ONE) - x.abs());
    }
    let (mut xmm1, n) = reduce(x);
    let mut xmm2 = pair(P_2);
    let mut xmm3 = X(d(P_1), 0.0);
    xmm3.0 *= xmm1.0;
    xmm1 = dup(xmm1);
    let row = (n.wrapping_add(1_865_232) & 63) as usize;
    let mut xmm4 = X(x, 0.0);
    let mut xmm5 = pair(SC_4);
    xmm2 = mulpd(xmm2, xmm1);
    let mut xmm0 = X(x, 0.0);
    xmm0.0 -= xmm3.0;
    xmm1.0 *= d(P_3);
    xmm4.0 -= xmm3.0;
    let mut xmm7 = X(t(row, 1), 0.0);
    xmm0 = dup(xmm0);
    xmm3 = xmm4;
    xmm4.0 -= xmm2.0;
    xmm5 = mulpd(xmm5, xmm0);
    xmm0 = subpd(xmm0, xmm2);
    let mut xmm6 = pair(SC_2);
    xmm7.0 *= xmm4.0;
    xmm3.0 -= xmm4.0;
    xmm5 = mulpd(xmm5, xmm0);
    xmm0 = mulpd(xmm0, xmm0);
    xmm3.0 -= xmm2.0;
    xmm2 = X(t(row, 0), t(row, 1));
    xmm1.0 -= xmm3.0;
    xmm3 = X(t(row, 3), 0.0);
    xmm2.0 += xmm3.0;
    xmm7.0 -= xmm2.0;
    xmm2.0 *= xmm4.0;
    xmm6 = mulpd(xmm6, xmm0);
    xmm3.0 *= xmm4.0;
    xmm2 = mulpd(xmm2, xmm0);
    xmm0 = mulpd(xmm0, xmm0);
    xmm5 = addpd(xmm5, pair(SC_3));
    xmm4.0 *= t(row, 0);
    xmm6 = addpd(xmm6, pair(SC_1));
    xmm5 = mulpd(xmm5, xmm0);
    xmm0 = xmm3;
    xmm3.0 += t(row, 1);
    xmm1 = mulpd(xmm1, xmm7);
    xmm7 = xmm4;
    xmm4.0 += xmm3.0;
    xmm6 = addpd(xmm6, xmm5);
    xmm5 = X(t(row, 1), 0.0);
    xmm5.0 -= xmm3.0;
    xmm3.0 -= xmm4.0;
    xmm1.0 += t(row, 2);
    xmm6 = mulpd(xmm6, xmm2);
    xmm0.0 += xmm5.0;
    xmm3.0 += xmm7.0;
    xmm0.0 += xmm1.0;
    xmm0.0 += xmm3.0;
    xmm0.0 += xmm6.0;
    xmm6 = X(xmm6.1, xmm6.1);
    xmm0.0 += xmm6.0;
    xmm0.0 += xmm4.0;
    Some(xmm0.0)
}

#[cfg(test)]
mod tests {
    /// Every `OreFeature` angle, `nextFloat() * (float) PI`, folded as Java
    /// `hash = hash * 31 + bits` over sin then cos. The constant was produced by
    /// HotSpot 25.0.1 on x86_64 (interpreted, C1 and C2 rounds agree).
    #[test]
    fn every_ore_angle_matches_hotspot() {
        let mut hash = 0i64;
        for k in 0..(1u32 << 24) {
            let f = (k as f32 * 5.960_464_5e-8_f32) * std::f64::consts::PI as f32;
            let x = f64::from(f);
            hash = hash.wrapping_mul(31).wrapping_add(super::sin(x).unwrap().to_bits() as i64);
            hash = hash.wrapping_mul(31).wrapping_add(super::cos(x).unwrap().to_bits() as i64);
        }
        assert_eq!(hash, 2_485_921_060_041_987_419);
    }
}

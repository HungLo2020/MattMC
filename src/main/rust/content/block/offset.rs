//! Position-dependent model offsets, with Java's exact float-to-double steps.
//! The finite declarations can also project immutable CPU tables to old views.
use super::OffsetType;
use crate::core::math::position_seed;
mod declarations;
pub use declarations::Profile;
pub(crate) use declarations::PROFILES;

#[derive(Clone, Copy, Debug)]
pub struct Config {
    pub kind: OffsetType,
    pub horizontal: f32,
    pub vertical: f32,
}
impl Config {
    pub fn offset(self, x: i32, z: i32) -> [f64; 3] { self.from_seed(position_seed(x,0,z)) }
    pub fn from_seed(self, seed: i64) -> [f64; 3] {
        if self.kind == OffsetType::None { return [0.0; 3]; }
        let h = f64::from(self.horizontal);
        let horizontal = |bits| ((f64::from(bits as f32 / 15.0) - 0.5) * 0.5).clamp(-h,h);
        let y = if self.kind == OffsetType::Xyz {
            (f64::from((seed >> 4 & 15) as f32 / 15.0) - 1.0) * f64::from(self.vertical)
        } else { 0.0 };
        [horizontal(seed & 15), y, horizontal(seed >> 8 & 15)]
    }
    pub fn table_len(self) -> usize {
        match self.kind { OffsetType::None => 1, OffsetType::Xz => 256, OffsetType::Xyz => 4096 }
    }
    pub fn table(self) -> Vec<f64> {
        (0..self.table_len()).flat_map(|index| {
            let seed = if self.kind == OffsetType::Xz { (index & 15) | ((index & 240) << 4) } else { index };
            self.from_seed(seed as i64)
        }).collect()
    }
}

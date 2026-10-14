//! `EndIslandFeature`.
use super::placement::Placement;
use super::program::Reader;
use super::region::Result;
use super::vegetation::{offset, set_block};

#[derive(Clone, Debug, PartialEq)]
pub(crate) struct EndIsland {
    end_stone: u32,
}

/// `Mth.floor(float)`.
fn floor(f: f32) -> i32 {
    let i = f as i32;
    if f < i as f32 { i - 1 } else { i }
}

/// `Mth.ceil(float)`.
fn ceil(f: f32) -> i32 {
    let i = f as i32;
    if f > i as f32 { i + 1 } else { i }
}

impl EndIsland {
    pub(crate) fn read(reader: &mut Reader) -> Option<Self> {
        Some(Self { end_stone: reader.write_state()? })
    }

    pub(crate) fn place(&self, placement: &mut Placement, origin: [i32; 3]) -> Result<bool> {
        let mut f = placement.random.next_int(3) as f32 + 4.0f32;
        let mut i = 0;
        while f > 0.5f32 {
            for j in floor(-f)..=ceil(f) {
                for k in floor(-f)..=ceil(f) {
                    if ((j * j + k * k) as f32) <= (f + 1.0f32) * (f + 1.0f32) {
                        set_block(placement, offset(origin, [j, i, k]), self.end_stone, 3)?;
                    }
                }
            }
            f -= placement.random.next_int(2) as f32 + 0.5f32;
            i -= 1;
        }
        Ok(true)
    }
}

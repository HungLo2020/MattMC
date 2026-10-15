//! `EndIslandFeature`, `SpikeFeature`, `EndGatewayFeature` and `EndPlatformFeature`.
//! End crystals and gateway exits are Java steps (entities and block entities).
use super::placement::Placement;
use super::program::Reader;
use super::region::Result;
use super::vegetation::{in_set, offset, set_block};

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

/// One `SpikeFeature.EndSpike`.
#[derive(Clone, Debug, PartialEq)]
struct Spike {
    x: i32,
    z: i32,
    radius: i32,
    height: i32,
    guarded: bool,
}

/// `SpikeFeature` with its spikes resolved (configured, or the level seed's).
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Spikes {
    spikes: Vec<Spike>,
    obsidian: u32,
    air: u32,
    /// Iron bars by north, south, west, east bits (1, 2, 4, 8).
    bars: [u32; 16],
    /// Java's crystal, bedrock and fire, drawing the crystal's yaw.
    crystal_step: i64,
}

impl Spikes {
    pub(crate) fn read(reader: &mut Reader) -> Option<Self> {
        let count = reader.count(64)?;
        let spikes = (0..count)
            .map(|_| Some(Spike { x: reader.next()?, z: reader.next()?, radius: reader.next()?, height: reader.next()?, guarded: reader.flag()? }))
            .collect::<Option<Vec<_>>>()?;
        let obsidian = reader.write_state()?;
        let air = reader.write_state()?;
        let mut bars = [0; 16];
        for bar in bars.iter_mut() {
            *bar = reader.write_state()?;
        }
        Some(Self { spikes, obsidian, air, bars, crystal_step: reader.count(1 << 16)? as i64 })
    }

    pub(crate) fn place(&self, placement: &mut Placement, origin: [i32; 3]) -> Result<bool> {
        for spike in &self.spikes {
            if spike.x >> 4 == origin[0] >> 4 && spike.z >> 4 == origin[2] >> 4 {
                self.spike(placement, spike)?;
            }
        }
        Ok(true)
    }

    fn spike(&self, placement: &mut Placement, spike: &Spike) -> Result<()> {
        let r = spike.radius;
        let min_y = placement.region.min_y;
        // `BlockPos.betweenClosed`: X fastest, then Y, then Z.
        for z in spike.z - r..=spike.z + r {
            for y in min_y..=spike.height + 10 {
                for x in spike.x - r..=spike.x + r {
                    let (dx, dz) = ((x - spike.x) as f64, (z - spike.z) as f64);
                    if dx * dx + dz * dz <= f64::from(r * r + 1) && y < spike.height {
                        set_block(placement, [x, y, z], self.obsidian, 3)?;
                    } else if y > 65 {
                        set_block(placement, [x, y, z], self.air, 3)?;
                    }
                }
            }
        }
        if spike.guarded {
            for m in -2..=2i32 {
                for n in -2..=2i32 {
                    for o in 0..=3 {
                        let (x_edge, z_edge, top) = (m.abs() == 2, n.abs() == 2, o == 3);
                        if x_edge || z_edge || top {
                            let ns = m == -2 || m == 2 || top;
                            let we = n == -2 || n == 2 || top;
                            let bits = (ns && n != -2) as usize
                                | ((ns && n != 2) as usize) << 1
                                | ((we && m != -2) as usize) << 2
                                | ((we && m != 2) as usize) << 3;
                            set_block(placement, [spike.x + m, spike.height + o, spike.z + n], self.bars[bits], 3)?;
                        }
                    }
                }
            }
        }
        placement.java_step(self.crystal_step, [spike.x, spike.height + 1, spike.z])
    }
}

/// `EndGatewayFeature`; the exit position, when configured, is a Java step.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Gateway {
    gateway: u32,
    air: u32,
    bedrock: u32,
    exit_step: Option<i64>,
}

impl Gateway {
    pub(crate) fn read(reader: &mut Reader) -> Option<Self> {
        let gateway = reader.write_state()?;
        let air = reader.write_state()?;
        let bedrock = reader.write_state()?;
        let exit_step = if reader.flag()? { Some(reader.count(1 << 16)? as i64) } else { None };
        Some(Self { gateway, air, bedrock, exit_step })
    }

    pub(crate) fn place(&self, placement: &mut Placement, origin: [i32; 3]) -> Result<bool> {
        for z in origin[2] - 1..=origin[2] + 1 {
            for y in origin[1] - 2..=origin[1] + 2 {
                for x in origin[0] - 1..=origin[0] + 1 {
                    let (on_x, on_y, on_z) = (x == origin[0], y == origin[1], z == origin[2]);
                    let ends = (y - origin[1]).abs() == 2;
                    let state = if on_x && on_y && on_z {
                        set_block(placement, [x, y, z], self.gateway, 3)?;
                        if let Some(step) = self.exit_step {
                            placement.java_step(step, [x, y, z])?;
                        }
                        continue;
                    } else if on_y {
                        self.air
                    } else if ends && on_x && on_z {
                        self.bedrock
                    } else if (on_x || on_z) && !ends {
                        self.bedrock
                    } else {
                        self.air
                    };
                    set_block(placement, [x, y, z], state, 3)?;
                }
            }
        }
        Ok(true)
    }
}

/// `EndPlatformFeature`: obsidian under four layers of air, writing only what differs.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Platform {
    obsidian: u32,
    air: u32,
    obsidian_block: usize,
    air_block: usize,
}

impl Platform {
    pub(crate) fn read(reader: &mut Reader) -> Option<Self> {
        Some(Self { obsidian: reader.write_state()?, air: reader.write_state()?, obsidian_block: reader.set()?, air_block: reader.set()? })
    }

    pub(crate) fn place(&self, placement: &mut Placement, origin: [i32; 3]) -> Result<bool> {
        for i in -2..=2 {
            for j in -2..=2 {
                for k in -1..3 {
                    let pos = offset(origin, [j, k, i]);
                    let (state, block) = if k == -1 { (self.obsidian, self.obsidian_block) } else { (self.air, self.air_block) };
                    if !in_set(placement, block, pos)? {
                        set_block(placement, pos, state, 3)?;
                    }
                }
            }
        }
        Ok(true)
    }
}

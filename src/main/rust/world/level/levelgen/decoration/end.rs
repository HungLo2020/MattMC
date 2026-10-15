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

/// The deterministic End geometry uses one ordered effect interface. Live
/// levels answer in Java; generation uses the existing native storage adapter.
#[derive(Clone, Copy)]
pub(super) enum Material { Obsidian, Air, Bedrock, Gateway, Bars(u8) }

pub(super) trait Effects {
    type Error;
    fn min_y(&mut self) -> std::result::Result<i32, Self::Error>;
    fn origin(&mut self) -> std::result::Result<[i32; 3], Self::Error>;
    fn set(&mut self, pos: [i32; 3], material: Material) -> std::result::Result<(), Self::Error>;
    fn matches(&mut self, pos: [i32; 3], material: Material) -> std::result::Result<bool, Self::Error>;
    fn destroy(&mut self, pos: [i32; 3]) -> std::result::Result<(), Self::Error>;
    fn crystal(&mut self, pos: [i32; 3]) -> std::result::Result<(), Self::Error>;
    fn exit(&mut self, pos: [i32; 3]) -> std::result::Result<(), Self::Error>;
    fn platform_cell(&mut self, pos: [i32; 3], material: Material, destroy: bool) -> std::result::Result<(), Self::Error> {
        let action = platform_action(self.matches(pos, material)?, destroy);
        if action & 2 != 0 { self.destroy(pos)?; }
        if action & 1 != 0 { self.set(pos, material)?; }
        Ok(())
    }
}

/// One authoritative replacement decision, also called synchronously after a
/// live Java read. Java only dispatches the returned effect bits in order.
pub(super) fn platform_action(matches: bool, destroy: bool) -> i32 {
    if matches { 0 } else { 1 | ((destroy as i32) << 1) }
}

/// One descriptor. Java retains its cache/list/iterator authority.
#[derive(Clone, Copy, Debug, PartialEq)]
pub(super) struct Spike {
    pub x: i32,
    pub z: i32,
    pub radius: i32,
    pub height: i32,
    pub guarded: bool,
}

/// Same X-fastest normalized bounds as BlockPos.betweenClosed. The live
/// adapter retains Java for descriptors whose dimensions overflow its iterator.
pub(super) fn place_spike<S: Effects>(sink: &mut S, spike: Spike) -> std::result::Result<(), S::Error> {
    let r = spike.radius;
    let min_y = sink.min_y()?;
    let xa = spike.x.wrapping_sub(r);
    let xb = spike.x.wrapping_add(r);
    let za = spike.z.wrapping_sub(r);
    let zb = spike.z.wrapping_add(r);
    let top = spike.height.wrapping_add(10);
    for z in za.min(zb)..=za.max(zb) {
        for y in min_y.min(top)..=min_y.max(top) {
            for x in xa.min(xb)..=xa.max(xb) {
                // Vec3i widens coordinates before subtraction.
                let (dx, dz) = (f64::from(x) - f64::from(spike.x), f64::from(z) - f64::from(spike.z));
                if dx * dx + dz * dz <= f64::from(r.wrapping_mul(r).wrapping_add(1)) && y < spike.height {
                    sink.set([x, y, z], Material::Obsidian)?;
                } else if y > 65 {
                    sink.set([x, y, z], Material::Air)?;
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
                        let bits = (ns && n != -2) as u8
                            | ((ns && n != 2) as u8) << 1
                            | ((we && m != -2) as u8) << 2
                            | ((we && m != 2) as u8) << 3;
                        sink.set([spike.x.wrapping_add(m), spike.height.wrapping_add(o), spike.z.wrapping_add(n)], Material::Bars(bits))?;
                    }
                }
            }
        }
    }
    sink.crystal([spike.x, spike.height.wrapping_add(1), spike.z])
}

pub(super) fn place_gateway<S: Effects>(sink: &mut S) -> std::result::Result<(), S::Error> {
    let origin = sink.origin()?;
    for z in origin[2] - 1..=origin[2] + 1 {
        for y in origin[1] - 2..=origin[1] + 2 {
            for x in origin[0] - 1..=origin[0] + 1 {
                let current = sink.origin()?;
                let (on_x, on_y, on_z) = (x == current[0], y == current[1], z == current[2]);
                let ends = y.wrapping_sub(current[1]).wrapping_abs() == 2;
                let state = if on_x && on_y && on_z {
                    sink.set([x, y, z], Material::Gateway)?;
                    sink.exit([x, y, z])?;
                    continue;
                } else if on_y {
                    Material::Air
                } else if ends && on_x && on_z || (on_x || on_z) && !ends {
                    Material::Bedrock
                } else { Material::Air };
                sink.set([x, y, z], state)?;
            }
        }
    }
    Ok(())
}

pub(super) fn place_platform<S: Effects>(sink: &mut S, destroy: bool) -> std::result::Result<(), S::Error> {
    for i in -2..=2 {
        for j in -2..=2 {
            for k in -1..3 {
                let origin = sink.origin()?;
                let pos = [origin[0].wrapping_add(j), origin[1].wrapping_add(k), origin[2].wrapping_add(i)];
                let material = if k == -1 { Material::Obsidian } else { Material::Air };
                sink.platform_cell(pos, material, destroy)?;
            }
        }
    }
    Ok(())
}

#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Spikes {
    spikes: Vec<Spike>, obsidian: u32, air: u32, bars: [u32; 16], crystal_step: i64,
}
impl Spikes {
    pub(crate) fn read(reader: &mut Reader) -> Option<Self> {
        let count = reader.count(64)?;
        let spikes = (0..count).map(|_| Some(Spike { x: reader.next()?, z: reader.next()?, radius: reader.next()?, height: reader.next()?, guarded: reader.flag()? })).collect::<Option<Vec<_>>>()?;
        let obsidian = reader.write_state()?;
        let air = reader.write_state()?;
        let mut bars = [0; 16];
        for bar in &mut bars { *bar = reader.write_state()?; }
        Some(Self { spikes, obsidian, air, bars, crystal_step: reader.count(1 << 16)? as i64 })
    }
    pub(crate) fn place(&self, placement: &mut Placement, origin: [i32; 3]) -> Result<bool> {
        let mut sink = Storage { placement, origin, config: Config::Spikes(self) };
        for &spike in &self.spikes {
            if spike.x >> 4 == origin[0] >> 4 && spike.z >> 4 == origin[2] >> 4 { place_spike(&mut sink, spike)?; }
        }
        Ok(true)
    }
}

#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Gateway { gateway: u32, air: u32, bedrock: u32, exit_step: Option<i64> }
impl Gateway {
    pub(crate) fn read(reader: &mut Reader) -> Option<Self> {
        Some(Self { gateway: reader.write_state()?, air: reader.write_state()?, bedrock: reader.write_state()?,
            exit_step: if reader.flag()? { Some(reader.count(1 << 16)? as i64) } else { None } })
    }
    pub(crate) fn place(&self, placement: &mut Placement, origin: [i32; 3]) -> Result<bool> {
        place_gateway(&mut Storage { placement, origin, config: Config::Gateway(self) })?;
        Ok(true)
    }
}

#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Platform { obsidian: u32, air: u32, obsidian_block: usize, air_block: usize }
impl Platform {
    pub(crate) fn read(reader: &mut Reader) -> Option<Self> {
        Some(Self { obsidian: reader.write_state()?, air: reader.write_state()?, obsidian_block: reader.set()?, air_block: reader.set()? })
    }
    pub(crate) fn place(&self, placement: &mut Placement, origin: [i32; 3]) -> Result<bool> {
        place_platform(&mut Storage { placement, origin, config: Config::Platform(self) }, false)?;
        Ok(true)
    }
}

enum Config<'a> { Spikes(&'a Spikes), Gateway(&'a Gateway), Platform(&'a Platform) }
struct Storage<'p, 'r, 'a, 's, 'c> { placement: &'s mut Placement<'p, 'r, 'a>, origin: [i32; 3], config: Config<'c> }
impl Effects for Storage<'_, '_, '_, '_, '_> {
    type Error = super::region::Failure;
    fn min_y(&mut self) -> Result<i32> { Ok(self.placement.region.min_y) }
    fn origin(&mut self) -> Result<[i32; 3]> { Ok(self.origin) }
    fn set(&mut self, pos: [i32; 3], material: Material) -> Result<()> { self.set_material(pos, material) }
    fn matches(&mut self, pos: [i32; 3], material: Material) -> Result<bool> {
        if let Config::Platform(p) = &self.config {
            return in_set(self.placement, if matches!(material, Material::Obsidian) { p.obsidian_block } else { p.air_block }, pos);
        }
        Err(super::region::Failure::Unbound)
    }
    fn destroy(&mut self, _: [i32; 3]) -> Result<()> { Err(super::region::Failure::Unbound) }
    fn crystal(&mut self, pos: [i32; 3]) -> Result<()> {
        if let Config::Spikes(s) = &self.config { self.placement.java_step(s.crystal_step, pos) } else { Err(super::region::Failure::Unbound) }
    }
    fn exit(&mut self, pos: [i32; 3]) -> Result<()> {
        if let Config::Gateway(g) = &self.config { if let Some(step) = g.exit_step { self.placement.java_step(step, pos)?; } }
        Ok(())
    }
}
impl Storage<'_, '_, '_, '_, '_> {
    fn set_material(&mut self, pos: [i32; 3], material: Material) -> Result<()> {
        let state = match (&self.config, material) {
            (Config::Spikes(s), Material::Obsidian) => s.obsidian,
            (Config::Spikes(s), Material::Air) => s.air,
            (Config::Spikes(s), Material::Bars(bits)) => s.bars[bits as usize],
            (Config::Gateway(g), Material::Gateway) => g.gateway,
            (Config::Gateway(g), Material::Air) => g.air,
            (Config::Gateway(g), Material::Bedrock) => g.bedrock,
            (Config::Platform(p), Material::Obsidian) => p.obsidian,
            (Config::Platform(p), Material::Air) => p.air,
            _ => return Err(super::region::Failure::Unbound),
        };
        set_block(self.placement, pos, state, 3)
    }
}

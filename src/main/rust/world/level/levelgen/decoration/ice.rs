//! `BlueIceFeature`, `IceSpikeFeature` and `IcebergFeature`.
use super::placement::Placement;
use super::program::Reader;
use super::region::{Result, OP_MATH_COS, OP_MATH_SIN};
use crate::core::java_trig;
use super::vegetation::{in_set, is_air, offset, set_block, DIRECTIONS};

/// `BlueIceFeature`.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct BlueIce {
    sea_level: i32,
    water: usize,
    packed_ice: usize,
    blue_ice: usize,
    /// Air, water, packed ice or ice.
    spreads_into: usize,
    state: u32,
}

impl BlueIce {
    pub(crate) fn read(reader: &mut Reader) -> Option<Self> {
        Some(Self {
            sea_level: reader.next()?,
            water: reader.set()?,
            packed_ice: reader.set()?,
            blue_ice: reader.set()?,
            spreads_into: reader.set()?,
            state: reader.write_state()?,
        })
    }

    pub(crate) fn place(&self, placement: &mut Placement, origin: [i32; 3]) -> Result<bool> {
        if origin[1] > self.sea_level - 1 {
            return Ok(false);
        }
        if !in_set(placement, self.water, origin)? && !in_set(placement, self.water, offset(origin, [0, -1, 0]))? {
            return Ok(false);
        }
        let mut touches_packed = false;
        for direction in &DIRECTIONS[1..] {
            if in_set(placement, self.packed_ice, offset(origin, *direction))? {
                touches_packed = true;
                break;
            }
        }
        if !touches_packed {
            return Ok(false);
        }
        set_block(placement, origin, self.state, 2)?;
        for _ in 0..200 {
            let random = &mut placement.random;
            let j = random.next_int(5) - random.next_int(6);
            let k = if j < 2 { 3 + j / 2 } else { 3 };
            if k >= 1 {
                let dx = random.next_int(k) - random.next_int(k);
                let dz = random.next_int(k) - random.next_int(k);
                let pos = offset(origin, [dx, j, dz]);
                if in_set(placement, self.spreads_into, pos)? {
                    for direction in &DIRECTIONS {
                        if in_set(placement, self.blue_ice, offset(pos, *direction))? {
                            set_block(placement, pos, self.state, 2)?;
                            break;
                        }
                    }
                }
            }
        }
        Ok(true)
    }
}

/// `IceSpikeFeature`.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct IceSpike {
    snow_block: usize,
    /// Air, dirt, snow block or ice: what the spike replaces.
    spike_into: usize,
    /// The same plus packed ice: what the base column replaces.
    column_into: usize,
    packed_ice: u32,
}

/// `Mth.ceil(float)`.
fn ceil(f: f32) -> i32 {
    let i = f as i32;
    if f > i as f32 { i + 1 } else { i }
}

impl IceSpike {
    pub(crate) fn read(reader: &mut Reader) -> Option<Self> {
        Some(Self {
            snow_block: reader.set()?,
            spike_into: reader.set()?,
            column_into: reader.set()?,
            packed_ice: reader.write_state()?,
        })
    }

    pub(crate) fn place(&self, placement: &mut Placement, origin: [i32; 3]) -> Result<bool> {
        let mut pos = origin;
        let floor = placement.region.min_y + 2;
        while is_air(placement, pos)? && pos[1] > floor {
            pos[1] -= 1;
        }
        if !in_set(placement, self.snow_block, pos)? {
            return Ok(false);
        }
        let random = &mut placement.random;
        pos[1] += random.next_int(4);
        let i = random.next_int(4) + 7;
        let j = i / 4 + random.next_int(2);
        if j > 1 && random.next_int(60) == 0 {
            pos[1] += 10 + random.next_int(30);
        }
        for k in 0..i {
            let f = (1.0f32 - k as f32 / i as f32) * j as f32;
            let l = ceil(f);
            for m in -l..=l {
                let g = m.abs() as f32 - 0.25f32;
                for n in -l..=l {
                    let h = n.abs() as f32 - 0.25f32;
                    let inside = m == 0 && n == 0 || !(g * g + h * h > f * f);
                    if inside && (m != -l && m != l && n != -l && n != l || !(placement.random.next_float() > 0.75f32)) {
                        let up = offset(pos, [m, k, n]);
                        if in_set(placement, self.spike_into, up)? {
                            set_block(placement, up, self.packed_ice, 3)?;
                        }
                        if k != 0 && l > 1 {
                            let down = offset(pos, [m, -k, n]);
                            if in_set(placement, self.spike_into, down)? {
                                set_block(placement, down, self.packed_ice, 3)?;
                            }
                        }
                    }
                }
            }
        }
        let k = (j - 1).clamp(0, 1);
        for o in -k..=k {
            for l in -k..=k {
                let mut column = offset(pos, [o, -1, l]);
                let mut p = 50;
                if o.abs() == 1 && l.abs() == 1 {
                    p = placement.random.next_int(5);
                }
                while column[1] > 50 {
                    if !in_set(placement, self.column_into, column)? {
                        break;
                    }
                    set_block(placement, column, self.packed_ice, 3)?;
                    column[1] -= 1;
                    p -= 1;
                    if p <= 0 {
                        column[1] -= placement.random.next_int(5) + 1;
                        p = placement.random.next_int(5);
                    }
                }
            }
        }
        Ok(true)
    }
}

/// `IcebergFeature`.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Iceberg {
    sea_level: i32,
    state: u32,
    snow_block_state: u32,
    water_state: u32,
    air_state: u32,
    /// Air, snow block, ice or water: what `setIcebergBlock` replaces.
    replaces: usize,
    water: usize,
    /// `isIcebergState`: packed ice, snow block or blue ice.
    iceberg: usize,
    snow_block: usize,
    /// The snow layer block.
    snow: usize,
}

/// `Math.sin` and `Math.cos` of an angle: the transcribed HotSpot stubs, or Java's.
fn trig(placement: &Placement, angle: f64) -> Result<(f64, f64)> {
    let sin = match java_trig::sin(angle) {
        Some(sin) => sin,
        None => placement.java_math(OP_MATH_SIN, angle, 0.0)?,
    };
    let cos = match java_trig::cos(angle) {
        Some(cos) => cos,
        None => placement.java_math(OP_MATH_COS, angle, 0.0)?,
    };
    Ok((sin, cos))
}

/// `signedDistanceEllipse` about `center` (`Math.pow(x, 2.0)` is `x * x` on HotSpot).
fn ellipse(x: i32, z: i32, center: [i32; 3], k: i32, l: i32, (sin, cos): (f64, f64)) -> f64 {
    let (dx, dz) = (f64::from(x - center[0]), f64::from(z - center[2]));
    let a = (dx * cos - dz * sin) / f64::from(k);
    let b = (dx * sin + dz * cos) / f64::from(l);
    a * a + b * b - 1.0
}

/// `heightDependentRadiusRound`.
fn radius_round(placement: &mut Placement, i: i32, j: i32, k: i32) -> i32 {
    let f = 3.5f32 - placement.random.next_float();
    let mut g = (1.0f32 - (i * i) as f32 / (j as f32 * f)) * k as f32;
    if j > 15 + placement.random.next_int(5) {
        let l = if i < 3 + placement.random.next_int(6) { i / 2 } else { i };
        g = (1.0f32 - l as f32 / (j as f32 * f * 0.4f32)) * k as f32;
    }
    ceil(g / 2.0f32)
}

/// `heightDependentRadiusEllipse`.
fn radius_ellipse(i: i32, j: i32, k: i32) -> i32 {
    let g = (1.0f32 - (i * i) as f32 / (j as f32 * 1.0f32)) * k as f32;
    ceil(g / 2.0f32)
}

/// `heightDependentRadiusSteep`.
fn radius_steep(placement: &mut Placement, i: i32, j: i32, k: i32) -> i32 {
    let f = 1.0f32 + placement.random.next_float() / 2.0f32;
    let g = (1.0f32 - i as f32 / (j as f32 * f)) * k as f32;
    ceil(g / 2.0f32)
}

/// The shape shared by both block loops.
struct Shape {
    origin: [i32; 3],
    ellipse: bool,
    snowy: bool,
    /// `j` in `place`: the 3 to 5 that narrows the ellipse near the top.
    taper: i32,
    angle: (f64, f64),
}

impl Iceberg {
    pub(crate) fn read(reader: &mut Reader) -> Option<Self> {
        Some(Self {
            sea_level: reader.next()?,
            state: reader.write_state()?,
            snow_block_state: reader.write_state()?,
            water_state: reader.write_state()?,
            air_state: reader.write_state()?,
            replaces: reader.set()?,
            water: reader.set()?,
            iceberg: reader.set()?,
            snow_block: reader.set()?,
            snow: reader.set()?,
        })
    }

    pub(crate) fn place(&self, placement: &mut Placement, origin: [i32; 3]) -> Result<bool> {
        let origin = [origin[0], self.sea_level, origin[2]];
        let random = &mut placement.random;
        let snowy = random.next_double() > 0.7;
        let d = random.next_double() * 2.0 * std::f64::consts::PI;
        let i = 11 - random.next_int(5);
        let j = 3 + random.next_int(3);
        let ellipse = random.next_double() > 0.7;
        let mut l = if ellipse { random.next_int(6) + 6 } else { random.next_int(15) + 3 };
        if !ellipse && random.next_double() > 0.9 {
            l += random.next_int(19) + 7;
        }
        let m = (l + random.next_int(11)).min(18);
        let n = (l + random.next_int(7) - random.next_int(5)).min(11);
        let o = if ellipse { i } else { 11 };
        let shape = Shape { origin, ellipse, snowy, taper: j, angle: trig(placement, d)? };
        for p in -o..o {
            for q in -o..o {
                for r in 0..l {
                    let s = if ellipse { radius_ellipse(r, l, n) } else { radius_round(placement, r, l, n) };
                    if ellipse || p < s {
                        self.block(placement, &shape, l, p, r, q, s, o)?;
                    }
                }
            }
        }
        self.smooth(placement, origin, n, l, ellipse, i)?;
        for p in -o..o {
            for q in -o..o {
                let mut rx = -1;
                while rx > -m {
                    let s = if ellipse { ceil(o as f32 * (1.0f32 - (rx * rx) as f32 / (m as f32 * 8.0f32))) } else { o };
                    let t = radius_steep(placement, -rx, m, n);
                    if p < t {
                        self.block(placement, &shape, m, p, rx, q, t, s)?;
                    }
                    rx -= 1;
                }
            }
        }
        let cut = if ellipse { placement.random.next_double() > 0.1 } else { placement.random.next_double() > 0.7 };
        if cut {
            self.cut_out(placement, n, l, origin, ellipse, i, d, j)?;
        }
        Ok(true)
    }

    /// `generateIcebergBlock(level, random, origin, height, x, y, z, radius, n, …)`.
    #[allow(clippy::too_many_arguments)]
    fn block(&self, placement: &mut Placement, shape: &Shape, height: i32, x: i32, y: i32, z: i32, radius: i32, n: i32) -> Result<()> {
        let e = if shape.ellipse {
            let c = if y > 0 && height - y <= 3 { shape.taper - (4 - (height - y)) } else { shape.taper };
            ellipse(x, z, [0, 0, 0], n, c, shape.angle)
        } else {
            let value = placement.random.next_float();
            let clamped = if value < 0.2f32 { 0.2f32 } else { value.min(0.8f32) };
            let f = 10.0f32 * clamped / radius as f32;
            f as f64 + f64::from(x * x) + f64::from(z * z) - f64::from(radius * radius)
        };
        if e < 0.0 {
            let pos = offset(shape.origin, [x, y, z]);
            let f = if shape.ellipse { -0.5 } else { f64::from(-6 - placement.random.next_int(3)) };
            if e > f && placement.random.next_double() > 0.9 {
                return Ok(());
            }
            self.set_iceberg_block(placement, pos, height - y, height, shape.ellipse, shape.snowy)?;
        }
        Ok(())
    }

    fn set_iceberg_block(&self, placement: &mut Placement, pos: [i32; 3], i: i32, j: i32, ellipse: bool, snowy: bool) -> Result<()> {
        let state = placement.region.level_block_state(pos[0], pos[1], pos[2])?;
        if !placement.program.in_set(self.replaces, state) {
            return Ok(());
        }
        let keep = !ellipse || placement.random.next_double() > 0.05;
        let k = if ellipse { 3 } else { 2 };
        let snow = snowy
            && !placement.program.in_set(self.water, state)
            && f64::from(i) <= f64::from(placement.random.next_int((j / k).max(1))) + f64::from(j) * 0.6
            && keep;
        set_block(placement, pos, if snow { self.snow_block_state } else { self.state }, 3)
    }

    fn smooth(&self, placement: &mut Placement, origin: [i32; 3], i: i32, j: i32, ellipse: bool, k: i32) -> Result<()> {
        let l = if ellipse { k } else { i / 2 };
        for m in -l..=l {
            for n in -l..=l {
                for o in 0..=j {
                    let pos = offset(origin, [m, o, n]);
                    let state = placement.region.level_block_state(pos[0], pos[1], pos[2])?;
                    let iceberg = placement.program.in_set(self.iceberg, state);
                    if !iceberg && !placement.program.in_set(self.snow, state) {
                        continue;
                    }
                    if is_air(placement, offset(pos, [0, -1, 0]))? {
                        set_block(placement, pos, self.air_state, 3)?;
                        set_block(placement, offset(pos, [0, 1, 0]), self.air_state, 3)?;
                    } else if iceberg {
                        let mut open = 0;
                        for by in [[-1, 0, 0], [1, 0, 0], [0, 0, -1], [0, 0, 1]] {
                            if !in_set(placement, self.iceberg, offset(pos, by))? {
                                open += 1;
                            }
                        }
                        if open >= 3 {
                            set_block(placement, pos, self.air_state, 3)?;
                        }
                    }
                }
            }
        }
        Ok(())
    }

    /// `generateCutOut(random, level, i, j, origin, ellipse, k, d, l)`.
    #[allow(clippy::too_many_arguments)]
    fn cut_out(&self, placement: &mut Placement, i: i32, j: i32, origin: [i32; 3], ellipse: bool, k: i32, d: f64, l: i32) -> Result<()> {
        let random = &mut placement.random;
        let m = if random.next_boolean() { -1 } else { 1 };
        let n = if random.next_boolean() { -1 } else { 1 };
        let mut o = random.next_int((i / 2 - 2).max(1));
        if random.next_boolean() {
            o = i / 2 + 1 - random.next_int((i - i / 2 - 1).max(1));
        }
        let mut p = random.next_int((i / 2 - 2).max(1));
        if random.next_boolean() {
            p = i / 2 + 1 - random.next_int((i - i / 2 - 1).max(1));
        }
        if ellipse {
            o = random.next_int((k - 5).max(1));
            p = o;
        }
        let center = [m * o, 0, n * p];
        let e = if ellipse { d + std::f64::consts::PI / 2.0 } else { random.next_double() * 2.0 * std::f64::consts::PI };
        let angle = trig(placement, e)?;
        for q in 0..j - 3 {
            let r = radius_round(placement, q, j, i);
            self.carve(placement, r, q, origin, false, angle, center, k, l)?;
        }
        let mut q = -1;
        while q > -j + placement.random.next_int(5) {
            let r = radius_steep(placement, -q, j, i);
            self.carve(placement, r, q, origin, true, angle, center, k, l)?;
            q -= 1;
        }
        Ok(())
    }

    #[allow(clippy::too_many_arguments)]
    fn carve(&self, placement: &mut Placement, i: i32, j: i32, origin: [i32; 3], flood: bool, angle: (f64, f64), center: [i32; 3], k: i32, l: i32) -> Result<()> {
        let m = i + 1 + k / 3;
        let n = (i - 3).min(3) + l / 2 - 1;
        for o in -m..m {
            for p in -m..m {
                if ellipse(o, p, center, m, n, angle) < 0.0 {
                    let pos = offset(origin, [o, j, p]);
                    let state = placement.region.level_block_state(pos[0], pos[1], pos[2])?;
                    if placement.program.in_set(self.iceberg, state) || placement.program.in_set(self.snow_block, state) {
                        if flood {
                            set_block(placement, pos, self.water_state, 3)?;
                        } else {
                            set_block(placement, pos, self.air_state, 3)?;
                            let above = offset(pos, [0, 1, 0]);
                            if in_set(placement, self.snow, above)? {
                                set_block(placement, above, self.air_state, 3)?;
                            }
                        }
                    }
                }
            }
        }
        Ok(())
    }
}

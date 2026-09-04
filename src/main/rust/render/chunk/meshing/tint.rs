use super::*;

pub(super) fn native_tint_color(
    block: &NativeSectionBlockRecord,
    state: NativeMeshingState,
    fluid: bool,
) -> i32 {
    if fluid {
        if state.fluid_type == FLUID_WATER && block.fluid_tint != -1 {
            return block.fluid_tint;
        }
        return -1;
    }
    match state.tint_type {
        TINT_SPRUCE => return 0xff619961u32 as i32,
        TINT_BIRCH => return 0xff80a755u32 as i32,
        _ => {}
    }

    if block.tint != -1 {
        return block.tint;
    }
    match state.tint_type {
        TINT_NONE => -1,
        TINT_REDSTONE | TINT_CONSTANT | TINT_STEM => block.tint,
        TINT_WATER => block.tint,
        _ => block.tint,
    }
}

pub(super) fn native_vertex_tint_color(
    block: &NativeSectionBlockRecord,
    state: NativeMeshingState,
    x: f32,
    y: f32,
    z: f32,
) -> i32 {
    let base = native_tint_color(block, state, false);
    if base == -1 || matches!(state.tint_type, TINT_NONE | TINT_SPRUCE | TINT_BIRCH | TINT_CONSTANT | TINT_STEM | TINT_REDSTONE) {
        return base;
    }
    // Exact Java BlendedColorProvider domain: floor(vertex - 0.5), then the
    // fractional remainder. The 4x4x4 snapshot covers source-model vertices
    // through [-0.5, 1.5], including resource-pack geometry beyond a block.
    let lattice_coordinate = |value: f32| {
        let shifted = value - 0.5;
        let base = shifted.floor() as i32;
        ((base + 1) as usize, shifted - base as f32)
    };
    let (ix, fx) = lattice_coordinate(x);
    let (iy, _) = lattice_coordinate(y);
    let (iz, fz) = lattice_coordinate(z);
    const TINT_LATTICE_FLAG: i32 = 1 << 1;
    if block.flags & TINT_LATTICE_FLAG == 0 {
        return base;
    }
    if ix + 1 >= 4 || iy >= 4 || iz + 1 >= 4 {
        // A model outside the explicit semantic lattice is not admissible:
        // guessing a biome cell would violate parity. The route keeps this
        // capability private until a wider extractor is supplied.
        return -1;
    }
    let sample = |x: usize, z: usize| block.tint_lattice[iy][z][x] as u32;
    let x1 = (fx * 255.0) as u32;
    let z1 = (fz * 255.0) as u32;
    let mix = |a: u32, b: u32, weight: u32| ((a * (255 - weight) + b * weight + 255) >> 8) & 255;
    let channel = |shift| {
        let a = mix((sample(ix, iz) >> shift) & 255u32, (sample(ix + 1, iz) >> shift) & 255u32, x1);
        let b = mix((sample(ix, iz + 1) >> shift) & 255u32, (sample(ix + 1, iz + 1) >> shift) & 255u32, x1);
        mix(a, b, z1)
    };
    (0xff00_0000 | (channel(16) << 16) | (channel(8) << 8) | channel(0)) as i32
}

pub(super) fn multiply_argb(color: i32, tint: i32) -> i32 {
    if tint == -1 {
        return color;
    }
    let ca = (color as u32 >> 24) & 0xff;
    let cr = (color as u32 >> 16) & 0xff;
    let cg = (color as u32 >> 8) & 0xff;
    let cb = color as u32 & 0xff;
    let tr = (tint as u32 >> 16) & 0xff;
    let tg = (tint as u32 >> 8) & 0xff;
    let tb = tint as u32 & 0xff;
    // This is Sodium's ColorMixer.mulComponentWise contract.  In particular,
    // it is *not* division by 255: Sodium rounds the Q8.8 product with an
    // added 0xff and then shifts by eight.  Native section colors are hashed
    // and consumed as compact bytes, so even its one-value rounding boundary
    // must agree with the Java OpenGL baseline.
    let multiply = |left: u32, right: u32| ((left * right + 0xff) >> 8) & 0xff;
    ((multiply(ca, (tint as u32 >> 24) & 0xff) << 24)
        | (multiply(cr, tr) << 16)
        | (multiply(cg, tg) << 8)
        | multiply(cb, tb)) as i32
}

pub(super) fn argb_to_abgr(color: i32) -> i32 {
    let color = color as u32;
    let alpha_green = color & 0xff00_ff00;
    let red = (color & 0x00ff_0000) >> 16;
    let blue = (color & 0x0000_00ff) << 16;
    (alpha_green | red | blue) as i32
}

#[cfg(test)]
mod tests;

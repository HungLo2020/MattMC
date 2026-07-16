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
    ((ca << 24) | ((cr * tr / 255) << 16) | ((cg * tg / 255) << 8) | (cb * tb / 255)) as i32
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

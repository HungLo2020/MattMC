//! Stateless float/color/range rules, preserving Java operation order.
pub(super) fn min(a: f32, b: f32) -> f32 {
    if a.is_nan() || b.is_nan() {
        f32::NAN
    } else if a == 0.0 && b == 0.0 {
        f32::from_bits(a.to_bits() | b.to_bits())
    } else {
        a.min(b)
    }
}
pub(super) fn max(a: f32, b: f32) -> f32 {
    if a.is_nan() || b.is_nan() {
        f32::NAN
    } else if a == 0.0 && b == 0.0 {
        f32::from_bits(a.to_bits() & b.to_bits())
    } else {
        a.max(b)
    }
}
pub(super) fn clamp(value: f32, low: f32, high: f32) -> f32 {
    if value < low {
        low
    } else {
        min(value, high)
    }
}
fn lerp(t: f32, start: f32, end: f32) -> f32 {
    start + t * (end - start)
}
pub(super) fn floor(value: f32) -> i32 {
    let i = value as i32;
    if value < i as f32 {
        i.wrapping_sub(1)
    } else {
        i
    }
}
fn channel(color: i32, shift: u32) -> f32 {
    ((color as u32 >> shift) & 255) as f32 / 255.0
}
pub(super) fn pack(rgb: [f32; 3]) -> i32 {
    (255u32 << 24
        | ((floor(rgb[0] * 255.0) as u32) & 255) << 16
        | ((floor(rgb[1] * 255.0) as u32) & 255) << 8
        | (floor(rgb[2] * 255.0) as u32) & 255) as i32
}

/// FogData order: environment start, render start, environment end, render end, sky, cloud.
pub(super) fn ranges(
    kind: i32,
    distance: f32,
    parameter: f32,
    cloud: f32,
    flags: i32,
    out: &mut [f32; 6],
) {
    let pair = match kind {
        0 => {
            if flags & 1 != 0 {
                [-8.0, distance * 0.5]
            } else if flags & 2 != 0 {
                [0.0, 5.0]
            } else {
                [0.25, 1.0]
            }
        }
        1 => {
            if flags & 1 != 0 {
                [-8.0, distance * 0.5]
            } else {
                [0.0, 2.0]
            }
        }
        2 => {
            let mut end = 96.0;
            if flags & 1 != 0 {
                end *= max(0.25, parameter);
                if flags & 2 != 0 {
                    end *= 0.85;
                }
            }
            [-8.0, end]
        }
        3 => [distance * 0.05, min(distance, 192.0) * 0.5],
        4 => {
            let end = if flags & 1 != 0 {
                5.0
            } else {
                lerp(min(1.0, parameter / 20.0), distance, 5.0)
            };
            [end * 0.25, end]
        }
        5 => {
            let end = lerp(parameter, distance, 15.0);
            [end * 0.75, end]
        }
        6 => [parameter * -160.0, 1024.0 + -256.0 * parameter],
        7 => {
            out[1] = distance - clamp(distance / 10.0, 4.0, 64.0);
            out[3] = distance;
            return;
        }
        // Water callbacks observe these committed stages, even when they throw.
        8 => {
            out[0] = -8.0;
            out[2] = 96.0;
            return;
        }
        9 => {
            out[2] *= max(0.25, parameter);
            return;
        }
        10 => {
            out[2] *= 0.85;
            return;
        }
        11 => {
            out[4] = out[2];
            out[5] = out[2];
            return;
        }
        _ => return,
    };
    out[0] = pair[0];
    out[2] = pair[1];
    out[4] = if kind == 4 {
        pair[1] * 0.8
    } else if kind == 6 {
        distance
    } else {
        pair[1]
    };
    out[5] = if kind == 6 { cloud } else { out[4] };
}

pub(super) fn finish_color(
    base: i32,
    kind: i32,
    darkness: f32,
    world_darkening: f32,
    vision: f32,
) -> [f32; 4] {
    let mut rgb = [channel(base, 16), channel(base, 8), channel(base, 0)];
    if darkness > 0.0 && kind != 0 && kind != 2 {
        let scale = (1.0 - darkness) * (1.0 - darkness);
        for c in &mut rgb {
            *c *= scale;
        }
    }
    if world_darkening > 0.0 {
        for (c, factor) in rgb.iter_mut().zip([0.7, 0.6, 0.6]) {
            *c = lerp(world_darkening, *c, *c * factor);
        }
    }
    if rgb.iter().all(|&c| c != 0.0) {
        let scale = 1.0 / max(rgb[0], max(rgb[1], rgb[2]));
        for c in &mut rgb {
            *c = lerp(vision, *c, *c * scale);
        }
    }
    [rgb[0], rgb[1], rgb[2], 1.0]
}

pub(super) fn air(
    mut rgb: [f32; 3],
    sunrise: i32,
    sun_weight: f32,
    sky: i32,
    distance: i32,
    rain: f32,
    thunder: f32,
) -> i32 {
    if sun_weight > 0.0 {
        let t = sun_weight * channel(sunrise, 24);
        for (c, shift) in rgb.iter_mut().zip([16, 8, 0]) {
            *c = lerp(t, *c, channel(sunrise, shift));
        }
    }
    let scale = 0.25f32 + 0.75f32 * distance as f32 / 32.0f32;
    let scale = 1.0 - (scale as f64).powf(0.25) as f32;
    for (c, shift) in rgb.iter_mut().zip([16, 8, 0]) {
        *c += (channel(sky, shift) - *c) * scale;
    }
    if rain > 0.0 {
        rgb[0] *= 1.0 - rain * 0.5;
        rgb[1] *= 1.0 - rain * 0.5;
        rgb[2] *= 1.0 - rain * 0.4;
    }
    if thunder > 0.0 {
        for c in &mut rgb {
            *c *= 1.0 - thunder * 0.5;
        }
    }
    pack(rgb)
}

//! Complete DH box production from bounded CPU facts, borrowed neighbors and spans.
use super::{faces, packets, Builder, Policy, Quad};

fn low(p: u64) -> i16 {
    ((p >> 8) & 4095) as i16
}
fn high(p: u64) -> i16 {
    ((p >> 20) & 4095) as i16
}
fn transparent(p: u64, enabled: bool) -> bool {
    p != 0 && enabled && ((p >> 56) & 15 != 15 || p >> 60 == 12)
}

/// mode: AUTO=0, ENABLED=1, DISABLED=2; world, ambient, flat bits: 2,3,4.
pub(super) fn shade(facts: u32, direction: u8) -> f32 {
    let mode = facts & 3;
    if mode == 2 {
        return 1.0;
    }
    if mode == 0 && facts & 4 == 0 {
        return 0.0;
    }
    let ambient = mode == 0 && facts & 8 != 0;
    if mode == 0 && facts & 16 != 0 {
        return if ambient { 0.9 } else { 1.0 };
    }
    match direction {
        0 => {
            if ambient {
                0.9
            } else {
                0.5
            }
        }
        1 => {
            if ambient {
                0.9
            } else {
                1.0
            }
        }
        2 | 3 => 0.8,
        _ => 0.6,
    }
}

fn material_at(mut q: Quad, y: i32, spans: &[u64]) -> Quad {
    for s in spans.chunks_exact(3) {
        if s[0] as i32 <= y && y < (s[0] >> 32) as i32 {
            q.identity = s[1] as i32;
            q.variant = (s[1] >> 32) as u8;
            q.position = s[2] as i64;
            break;
        }
    }
    q
}
fn horizontal(owner: &mut Builder, q: Quad) -> i32 {
    if q.width == 0 || q.height == 0 {
        return -2;
    }
    if q.width < 0 || q.height < 0 {
        return -3;
    }
    i32::from(owner.add(
        q,
        false,
        Policy {
            exact: false,
            overlap_errors: false,
            curvature: false,
        },
    ))
}

/// Flags: transparency bit0, fake ocean bit1, same-detail bits2..5;
/// shade facts bits8..12, explicit replay shades bit16. Neighbor order N,S,W,E.
pub(super) fn produce(
    owner: &mut Builder,
    mut q: Quad,
    top: u64,
    bottom: u64,
    neighbors: [Option<&[u64]>; 4],
    cave_y: i32,
    flags: u32,
    spans: &[u64],
    explicit_shades: [u64; 3],
    policy: Policy,
) -> i32 {
    let transparency = flags & 1 != 0;
    let is_transparent = transparency && (q.color >> 24 < 255 || q.material == 12);
    let top_transparent = transparent(top, transparency);
    let bottom_transparent = transparent(bottom, transparency);
    let over_void = bottom == 0;
    let mut sky_top = q.sky;
    let sky_bottom = if over_void { 0 } else { (bottom & 15) as u8 };
    let max_x = q.x.wrapping_add(q.width);
    let max_z = q.z.wrapping_add(q.width);
    if over_void {
        q.color |= 0xff000000;
    }
    if transparency && flags & 2 != 0 {
        if !is_transparent && top_transparent && top != 0 {
            sky_top = (15 - (i32::from(high(top)) - i32::from(q.y))).clamp(0, 15) as u8;
            q.height = (i32::from(high(top)) - i32::from(q.y) - 1) as i16;
        } else if is_transparent && !bottom_transparent && bottom != 0 {
            q.y = q.y.wrapping_add(q.height).wrapping_sub(1);
            q.height = 1;
        }
    }
    let max_y = q.y.wrapping_add(q.height);
    let color = |direction: u8| {
        let value = if flags & (1 << 16) != 0 {
            f32::from_bits(
                (explicit_shades[direction as usize / 2] >> (32 * (direction % 2))) as u32,
            )
        } else {
            shade(flags >> 8, direction)
        };
        packets::shade(q.color, value)
    };
    let top_occludes_water = matches!(top >> 60, 2 | 3 | 4 | 5 | 6 | 7 | 9);
    let skip_top = top != 0
        && low(top) == max_y
        && !top_transparent
        && (q.material != 12 || top_occludes_water);
    if !skip_top {
        let face = material_at(
            Quad {
                y: max_y,
                height: q.width,
                direction: 1,
                color: color(1),
                sky: sky_top,
                ..q
            },
            i32::from(max_y) - 1,
            spans,
        );
        let status = horizontal(owner, face);
        if status != 1 {
            return status;
        }
    }
    let skip_bottom = bottom != 0 && high(bottom) == q.y && !bottom_transparent;
    if !skip_bottom {
        let face = material_at(
            Quad {
                height: q.width,
                direction: 0,
                color: color(0),
                sky: sky_bottom,
                ..q
            },
            i32::from(q.y),
            spans,
        );
        let status = horizontal(owner, face);
        if status != 1 {
            return status;
        }
    }
    for (index, points) in neighbors.into_iter().enumerate() {
        let direction = index as u8 + 2;
        let face = Quad {
            direction,
            x: if direction == 5 { max_x } else { q.x },
            z: if direction == 3 { max_z } else { q.z },
            ..q
        };
        let status = match points {
            None if !is_transparent || over_void => {
                faces::vertical(owner, Quad { sky: 15, ..face }, spans, policy)
            }
            None => 1,
            Some(points) => faces::adjacent(
                owner,
                Quad {
                    color: color(direction),
                    ..face
                },
                points,
                flags & (1 << (index + 2)) != 0,
                cave_y,
                transparency,
                spans,
                policy,
            ),
        };
        if status != 1 {
            return status;
        }
    }
    1
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn actual_frozen_client_shade_getter() {
        let bytes = include_bytes!("../../../../test/resources/rendering/frozen-dh-shades.bin");
        let mut cursor = 0usize;
        fn int(b: &[u8], i: &mut usize) -> u32 {
            let n = u32::from_be_bytes(b[*i..*i + 4].try_into().unwrap());
            *i += 4;
            n
        }
        assert_eq!(int(bytes, &mut cursor), 0x44485331);
        let pins = int(bytes, &mut cursor);
        assert_eq!(pins, 6);
        for _ in 0..pins {
            for _ in 0..2 {
                let n = u16::from_be_bytes(bytes[cursor..cursor + 2].try_into().unwrap()) as usize;
                cursor += 2 + n;
            }
        }
        let count = int(bytes, &mut cursor);
        assert_eq!(count, 48);
        for _ in 0..count {
            let facts = int(bytes, &mut cursor);
            let direction = bytes[cursor];
            cursor += 1;
            let expected = int(bytes, &mut cursor);
            assert_eq!(shade(facts, direction).to_bits(), expected);
        }
        assert_eq!(cursor, bytes.len());
    }
    #[test]
    fn directional_shade_modes_and_world_facts() {
        let directional = [0.5, 1.0, 0.8, 0.8, 0.6, 0.6];
        for d in 0..6 {
            assert_eq!(shade(0, d), 0.0);
            assert_eq!(shade(1, d), directional[d as usize]);
            assert_eq!(shade(4, d), directional[d as usize]);
            assert_eq!(shade(2, d), 1.0);
            assert_eq!(shade(4 | 16, d), 1.0);
            assert_eq!(shade(4 | 8 | 16, d), 0.9);
            assert_eq!(
                shade(4 | 8, d),
                if d < 2 { 0.9 } else { directional[d as usize] }
            );
        }
    }
}

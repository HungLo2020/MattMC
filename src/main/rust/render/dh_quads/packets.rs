//! Immutable build plans consume retained quads and produce final CPU packets.
use super::{Builder, Quad};

pub(super) const CORNERS: [[(i16, i16); 4]; 6] = [
    [(1, 0), (1, 1), (0, 1), (0, 0)],
    [(0, 0), (0, 1), (1, 1), (1, 0)],
    [(0, 0), (0, 1), (1, 1), (1, 0)],
    [(1, 0), (1, 1), (0, 1), (0, 0)],
    [(0, 0), (1, 0), (1, 1), (0, 1)],
    [(0, 1), (1, 1), (1, 0), (0, 0)],
];

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(super) struct Event {
    pub vertex: i32,
    pub kind: u8,
    pub direction: u8,
    reserved: u16,
    pub color: u32,
    pub callback: i32,
}
pub(super) struct Entry {
    pub quad: Quad,
    pub callbacks: [i32; 4],
}
pub(super) struct Plan {
    pub route: i32,
    pub entries: Vec<Entry>,
    pub events: Vec<Event>,
    pub callbacks: usize,
    pub transparency: bool,
}

impl Plan {
    /// Grass mode 0=unchanged/debug, 1=all dirt, 2=fade; water events retain audit order.
    pub fn new(builder: &Builder, route: i32, grass: i32, audit: bool) -> Option<Self> {
        let (layer, directions): (usize, &[usize]) = match route {
            0 => (0, &[0, 1, 2, 3, 4, 5]),
            1 => (1, &[0, 2, 3, 4, 5]),
            2 | 3 => (1, &[1]),
            _ => return None,
        };
        if !(0..=3).contains(&grass) {
            return None;
        }
        let mut plan = Self {
            route,
            entries: Vec::new(),
            events: Vec::new(),
            callbacks: 0,
            transparency: builder.transparency,
        };
        for &direction in directions {
            for &quad in &builder.lists[layer][direction] {
                if route == 2 && quad.material == 12 || route == 3 && quad.material != 12 {
                    continue;
                }
                let mut entry = Entry {
                    quad,
                    callbacks: [-1; 4],
                };
                for (vertex, &(_, second)) in CORNERS[direction].iter().enumerate() {
                    let index =
                        i32::try_from(plan.entries.len().checked_mul(4)?.checked_add(vertex)?)
                            .ok()?;
                    if quad.material == 13
                        && grass != 0
                        && direction != 1
                        && (grass == 1 || grass == 2 && second == 0 || direction == 0)
                    {
                        let callback = i32::try_from(plan.callbacks).ok()?;
                        entry.callbacks[vertex] = callback;
                        plan.callbacks += 1;
                        plan.events.push(Event {
                            vertex: index,
                            kind: 1,
                            direction: direction as u8,
                            reserved: 0,
                            color: 0,
                            callback,
                        });
                    } else if audit && quad.material == 12 && quad.error == 0 {
                        let color = if plan.transparency {
                            quad.color
                        } else {
                            quad.color | 0xff000000
                        };
                        plan.events.push(Event {
                            vertex: index,
                            kind: 2,
                            direction: direction as u8,
                            reserved: 0,
                            color,
                            callback: -1,
                        });
                    }
                }
                plan.entries.push(entry);
            }
        }
        Some(plan)
    }
}

pub(super) fn shade(color: u32, shade: f32) -> u32 {
    let channel = |shift: u32| {
        let value = ((color >> shift) & 255u32) as f32 * shade;
        // Java Math.min/max propagate NaN; float-to-int maps it to zero.
        let value = if value.is_nan() {
            0.0
        } else if shade < 1.0 {
            value.max(0.0)
        } else {
            value.min(255.0)
        };
        value as u32
    };
    (color & 0xff000000) | (channel(16) << 16) | (channel(8) << 8) | channel(0)
}

pub(super) fn encode(
    entry: &Entry,
    transparency: bool,
    colors: &[i32],
    shades: &[f32],
) -> [u8; 64] {
    let quad = entry.quad;
    let mut bytes = [0u8; 64];
    for (vertex, &(a, b)) in CORNERS[quad.direction as usize].iter().enumerate() {
        let (dx, dy, dz, mx, my, mz) = match quad.direction {
            0 | 1 => (
                a.wrapping_mul(quad.width),
                0,
                b.wrapping_mul(quad.height),
                a * 2 - 1,
                0,
                b * 2 - 1,
            ),
            2 | 3 => (
                a.wrapping_mul(quad.width),
                b.wrapping_mul(quad.height),
                0,
                a * 2 - 1,
                b * 2 - 1,
                0,
            ),
            _ => (
                0,
                b.wrapping_mul(quad.height),
                a.wrapping_mul(quad.width),
                0,
                b * 2 - 1,
                a * 2 - 1,
            ),
        };
        let callback = entry.callbacks[vertex];
        let mut color = if callback < 0 {
            quad.color
        } else {
            shade(colors[callback as usize] as u32, shades[callback as usize])
        };
        let (normal, material, sky, block) = if quad.error == 0 {
            (
                quad.direction,
                quad.material,
                quad.sky as i8,
                quad.block as i8,
            )
        } else {
            color = 0xffff0000;
            (0, 0, 15, 15)
        };
        let offset = |v| {
            if v > 0 {
                1u16
            } else if v < 0 {
                3u16
            } else {
                0u16
            }
        };
        // Signed Java byte remainder and sign-extending OR are deliberate.
        let light = (i16::from(sky % 16) | (i16::from(block % 16) << 4)) as u16;
        let meta = light | ((offset(mx) | offset(my) << 2 | offset(mz) << 4) << 8);
        let p = vertex * 16;
        bytes[p..p + 2].copy_from_slice(&quad.x.wrapping_add(dx).to_ne_bytes());
        bytes[p + 2..p + 4].copy_from_slice(&quad.y.wrapping_add(dy).to_ne_bytes());
        bytes[p + 4..p + 6].copy_from_slice(&quad.z.wrapping_add(dz).to_ne_bytes());
        bytes[p + 6..p + 8].copy_from_slice(&meta.to_ne_bytes());
        bytes[p + 8] = (color >> 16) as u8;
        bytes[p + 9] = (color >> 8) as u8;
        bytes[p + 10] = color as u8;
        bytes[p + 11] = if transparency {
            (color >> 24) as u8
        } else {
            255
        };
        bytes[p + 12] = material;
        bytes[p + 13] = normal;
    }
    bytes
}

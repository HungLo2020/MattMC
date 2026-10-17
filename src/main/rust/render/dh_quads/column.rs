//! Scoped CPU column builds hand owned bytes directly to the DH ledger.
use super::packets::{encode, Plan};
use crate::render::dh_collector::{Payload, Segment};
use crate::render::worldrender::frame::limits::WORLD_LOD_MAX_SEGMENTS_PER_COLUMN;
use std::{
    panic::{catch_unwind, AssertUnwindSafe},
    ptr,
};

const QUADS_PER_PACKET: usize = 16_384;
#[cfg(test)]
#[path = "column_tests.rs"]
mod tests;
#[derive(Default)]
struct Companions {
    identities: Vec<i32>,
    variants: Vec<u8>,
    positions: Vec<i64>,
}
pub(crate) struct ColumnBuild {
    payload: Option<Payload>,
    companions: [Vec<Companions>; 4],
    next_layer: usize,
    coverage: [i32; 6],
    invalid_vertices: bool,
}
impl ColumnBuild {
    fn new() -> Self {
        Self {
            payload: Some(Payload::default()),
            companions: Default::default(),
            next_layer: 0,
            coverage: [0; 6],
            invalid_vertices: false,
        }
    }
    fn append(&mut self, plan: &Plan, layer: usize, colors: &[i32], shades: &[f32]) -> bool {
        if layer != self.next_layer
            || layer >= 4
            || plan.route != layer as i32
            || self.payload.is_none()
            || colors.len() != plan.callbacks
            || shades.len() != plan.callbacks
        {
            return false;
        }
        let payload = self.payload.as_mut().unwrap();
        for (index, entries) in plan.entries.chunks(QUADS_PER_PACKET).enumerate() {
            let mut bytes = Vec::with_capacity(entries.len() * 64);
            let mut companions = Companions {
                identities: Vec::with_capacity(entries.len()),
                variants: Vec::with_capacity(entries.len()),
                positions: Vec::with_capacity(entries.len()),
            };
            for entry in entries {
                let encoded = encode(entry, plan.transparency, colors, shades);
                // All four vertices have the same material/normal. Check the encoded
                // result, including overlap-error substitution, before retaining it.
                self.invalid_vertices |= encoded[12] > 15 || encoded[13] > 5;
                bytes.extend_from_slice(&encoded);
                companions.identities.push(entry.quad.identity);
                companions.variants.push(entry.quad.variant);
                companions.positions.push(entry.quad.position);
                let field = if entry.quad.identity > 0 {
                    0
                } else if entry.quad.identity == -1 {
                    1
                } else {
                    2
                };
                self.coverage[field] = self.coverage[field].wrapping_add(1);
                if layer == 0 {
                    self.coverage[field + 3] = self.coverage[field + 3].wrapping_add(1);
                }
            }
            payload.layers[layer].push(Segment {
                source_index: index as i32,
                bytes: bytes.into_boxed_slice(),
            });
            self.companions[layer].push(companions);
        }
        self.next_layer += 1;
        true
    }
    /// Match snapshot admission using facts from encoding immutable owned records.
    pub(crate) fn validate(&self) -> i32 {
        let Some(payload) = &self.payload else {
            return -1;
        };
        if self.next_layer != 4 {
            return -1;
        }
        if payload.layers.iter().map(Vec::len).sum::<usize>() > WORLD_LOD_MAX_SEGMENTS_PER_COLUMN {
            return -2;
        }
        if self.invalid_vertices {
            return -3;
        }
        0
    }
    /// The only production mutation after encoding is this ownership transfer.
    pub(crate) fn take_payload(&mut self) -> Option<Payload> {
        if self.validate() != 0 {
            return None;
        }
        self.payload.take()
    }
    fn segment(&self, layer: i32, index: i32) -> Option<(&Segment, &Companions)> {
        if !(0..4).contains(&layer) || index < 0 {
            return None;
        }
        Some((
            self.payload.as_ref()?.layers[layer as usize].get(index as usize)?,
            self.companions[layer as usize].get(index as usize)?,
        ))
    }
}

#[no_mangle]
pub extern "C" fn mattmc_dh_built_column_create() -> *mut ColumnBuild {
    Box::into_raw(Box::new(ColumnBuild::new()))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_built_column_release(owner: *mut ColumnBuild) {
    if !owner.is_null() {
        drop(Box::from_raw(owner));
    }
}
/// Exclusive CPU owners; callback arrays are borrowed for this call only.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_built_column_append(
    owner: *mut ColumnBuild,
    plan: *const Plan,
    layer: i32,
    colors: *const i32,
    shades: *const f32,
    count: i32,
) -> i32 {
    catch_unwind(AssertUnwindSafe(|| {
        let (Some(owner), Some(plan)) = (owner.as_mut(), plan.as_ref()) else {
            return 0;
        };
        if !(0..4).contains(&layer) || count < 0 || count as usize != plan.callbacks {
            return 0;
        }
        let (colors, shades) = if count == 0 {
            (&[][..], &[][..])
        } else {
            if colors.is_null()
                || shades.is_null()
                || colors as usize % 4 != 0
                || shades as usize % 4 != 0
            {
                return 0;
            }
            (
                std::slice::from_raw_parts(colors, count as usize),
                std::slice::from_raw_parts(shades, count as usize),
            )
        };
        i32::from(owner.append(plan, layer as usize, colors, shades))
    }))
    .unwrap_or(0)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_built_column_validate(owner: *const ColumnBuild) -> i32 {
    owner.as_ref().map_or(-1, ColumnBuild::validate)
}
/// Index -1 returns the layer's segment count; otherwise returns quad count.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_built_column_count(
    owner: *const ColumnBuild,
    layer: i32,
    index: i32,
) -> i32 {
    let Some(owner) = owner.as_ref() else {
        return -1;
    };
    if !(0..4).contains(&layer) {
        return -1;
    }
    if index == -1 {
        return owner
            .payload
            .as_ref()
            .map_or(-1, |p| p.layers[layer as usize].len() as i32);
    }
    owner
        .segment(layer, index)
        .map_or(-1, |(_, c)| c.identities.len() as i32)
}
/// Copies one immutable segment and companions for requested Java diagnostics only.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_built_column_copy(
    owner: *const ColumnBuild,
    layer: i32,
    index: i32,
    bytes: *mut u8,
    ids: *mut i32,
    variants: *mut u8,
    positions: *mut i64,
    count: i32,
) -> i32 {
    let Some(owner) = owner.as_ref() else {
        return 0;
    };
    let Some((segment, c)) = owner.segment(layer, index) else {
        return 0;
    };
    if count < 0
        || count as usize != c.identities.len()
        || bytes.is_null()
        || ids.is_null()
        || variants.is_null()
        || positions.is_null()
        || ids as usize % 4 != 0
        || positions as usize % 8 != 0
    {
        return 0;
    }
    ptr::copy_nonoverlapping(segment.bytes.as_ptr(), bytes, segment.bytes.len());
    ptr::copy_nonoverlapping(c.identities.as_ptr(), ids, c.identities.len());
    ptr::copy_nonoverlapping(c.variants.as_ptr(), variants, c.variants.len());
    ptr::copy_nonoverlapping(c.positions.as_ptr(), positions, c.positions.len());
    1
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_built_column_coverage(
    owner: *const ColumnBuild,
    out: *mut i32,
) -> i32 {
    let Some(owner) = owner.as_ref() else {
        return 0;
    };
    if out.is_null() || out as usize % 4 != 0 {
        return 0;
    }
    ptr::copy_nonoverlapping(owner.coverage.as_ptr(), out, 6);
    1
}
/// First 16 distinct stream/color samples per layer suffice for Java's global 16-entry audit.
/// Null output queries length in ints; capacity is checked before any write.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_built_column_water_samples(
    owner: *const ColumnBuild,
    out: *mut i32,
    capacity: i32,
) -> i32 {
    let Some(owner) = owner.as_ref() else {
        return -1;
    };
    let Some(payload) = &owner.payload else {
        return -1;
    };
    let mut values = Vec::new();
    for (layer, segments) in payload.layers.iter().enumerate() {
        let mut colors = Vec::new();
        'stream: for segment in segments {
            for v in segment.bytes.chunks_exact(16) {
                if v[12] != 12 {
                    continue;
                }
                let color = u32::from_be_bytes([v[11], v[8], v[9], v[10]]) as i32;
                if colors.contains(&color) {
                    continue;
                }
                colors.push(color);
                values.extend([layer as i32, color]);
                if colors.len() == 16 {
                    break 'stream;
                }
            }
        }
    }
    if out.is_null() {
        return values.len() as i32;
    }
    if capacity < 0 || (capacity as usize) < values.len() || out as usize % 4 != 0 {
        return -1;
    }
    ptr::copy_nonoverlapping(values.as_ptr(), out, values.len());
    values.len() as i32
}

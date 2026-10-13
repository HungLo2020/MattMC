use super::packets::Plan;
use super::{Builder, Policy, Quad};
use std::{
    panic::{catch_unwind, AssertUnwindSafe},
    ptr,
};

fn policy(flags: u64) -> Policy {
    Policy {
        exact: flags & (1 << 49) != 0,
        overlap_errors: flags & (1 << 50) != 0,
        curvature: flags & (1 << 51) != 0,
    }
}

fn quad(geometry: u64, attributes: u64, color: u32, identity: i32, position: i64) -> Quad {
    Quad {
        x: geometry as i16,
        y: (geometry >> 16) as i16,
        z: (geometry >> 32) as i16,
        width: (geometry >> 48) as i16,
        height: attributes as i16,
        direction: (attributes >> 16) as u8,
        material: (attributes >> 24) as u8,
        sky: (attributes >> 32) as u8,
        block: (attributes >> 40) as u8,
        error: 0,
        variant: (attributes >> 56) as u8,
        color,
        identity,
        position,
    }
}

/// The common interior-column route retains one CPU source scope and resolves
/// all four neighbors in Rust. Cross-source/heap neighbors use the span entry.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_box_add_source(
    owner: *mut Builder,
    geometry: u64,
    attributes: u64,
    color: u32,
    identity: i32,
    position: i64,
    top: u64,
    bottom: u64,
    source: *const crate::render::dh_columns::source::Source,
    north: u64,
    south: u64,
    west: u64,
    east: u64,
    cave_y: i32,
    flags: u32,
    spans: *const u64,
    span_count: i32,
    shade01: u64,
    shade23: u64,
    shade45: u64,
) -> i32 {
    catch_unwind(AssertUnwindSafe(|| {
        if !aligned(source) {
            return 0;
        }
        let source = &*source;
        let (Ok(n), Ok(s), Ok(w), Ok(e)) = (
            source.window(north),
            source.window(south),
            source.window(west),
            source.window(east),
        ) else {
            return 0;
        };
        mattmc_dh_box_add(
            owner, geometry, attributes, color, identity, position, top, bottom, n.0, n.1, s.0,
            s.1, w.0, w.1, e.0, e.1, cave_y, flags, spans, span_count, shade01, shade23, shade45,
        )
    }))
    .unwrap_or(0)
}

/// Whole box facts and four borrowed exact neighbor windows; no source pointer escapes.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_box_add(
    owner: *mut Builder,
    geometry: u64,
    attributes: u64,
    color: u32,
    identity: i32,
    position: i64,
    top: u64,
    bottom: u64,
    north: *const u64,
    north_count: i32,
    south: *const u64,
    south_count: i32,
    west: *const u64,
    west_count: i32,
    east: *const u64,
    east_count: i32,
    cave_y: i32,
    flags: u32,
    spans: *const u64,
    span_count: i32,
    shade01: u64,
    shade23: u64,
    shade45: u64,
) -> i32 {
    catch_unwind(AssertUnwindSafe(|| {
        let inputs = [
            (north, north_count),
            (south, south_count),
            (west, west_count),
            (east, east_count),
        ];
        if inputs
            .iter()
            .any(|&(p, n)| !(-1..=super::faces::LIMIT as i32).contains(&n) || n > 0 && !aligned(p))
            || !(0..=super::faces::LIMIT as i32).contains(&span_count)
            || span_count > 0 && !aligned(spans)
        {
            return 0;
        }
        let Some(owner) = owner.as_mut() else {
            return 0;
        };
        let neighbors = inputs.map(|(p, n)| {
            if n < 0 {
                None
            } else if n == 0 {
                Some(&[][..])
            } else {
                Some(std::slice::from_raw_parts(p, n as usize))
            }
        });
        let spans = if span_count == 0 {
            &[][..]
        } else {
            std::slice::from_raw_parts(spans, span_count as usize * 3)
        };
        super::boxes::produce(
            owner,
            quad(geometry, attributes, color, identity, position),
            top,
            bottom,
            neighbors,
            cave_y,
            flags,
            spans,
            [shade01, shade23, shade45],
            policy(attributes),
        )
    }))
    .unwrap_or(0)
}

#[no_mangle]
pub extern "C" fn mattmc_dh_quads_create(transparency: i32) -> *mut Builder {
    Box::into_raw(Box::new(Builder::new(transparency != 0)))
}

/// Caller owns this build lease exclusively; no borrowed projections escape.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_quads_release(owner: *mut Builder) {
    if !owner.is_null() {
        drop(Box::from_raw(owner));
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_quads_add(
    owner: *mut Builder,
    geometry: u64,
    attributes: u64,
    color: u32,
    identity: i32,
    position: i64,
) -> i32 {
    catch_unwind(AssertUnwindSafe(|| {
        let Some(owner) = owner.as_mut() else {
            return 0;
        };
        let quad = quad(geometry, attributes, color, identity, position);
        i32::from(owner.add(quad, attributes & (1 << 48) != 0, policy(attributes)))
    }))
    .unwrap_or(0)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_quads_merge(owner: *mut Builder, flags: u64) -> i32 {
    catch_unwind(AssertUnwindSafe(|| {
        let Some(owner) = owner.as_mut() else {
            return 0;
        };
        owner.merge(policy(flags));
        1
    }))
    .unwrap_or(0)
}

/// Whole immutable CPU snapshot into caller-owned bytes, never native handles.
/// Null output queries count. Capacity is in rows; bytes may be unaligned.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_quads_copy(
    owner: *const Builder,
    layer: i32,
    direction: i32,
    output: *mut u8,
    capacity: i32,
) -> i32 {
    let Some(owner) = owner.as_ref() else {
        return -1;
    };
    if !(0..2).contains(&layer) || !(0..6).contains(&direction) || capacity < 0 {
        return -1;
    }
    let list = &owner.lists[layer as usize][direction as usize];
    let Ok(count) = i32::try_from(list.len()) else {
        return -1;
    };
    if output.is_null() {
        return count;
    }
    if capacity < count {
        return -1;
    }
    ptr::copy_nonoverlapping(
        list.as_ptr().cast::<u8>(),
        output,
        list.len() * std::mem::size_of::<Quad>(),
    );
    count
}

/// 0/1: layer count; 2..7: emitted semantic coverage; 8: premerge count.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_quads_count(owner: *const Builder, field: i32) -> i32 {
    let Some(owner) = owner.as_ref() else {
        return -1;
    };
    match field {
        0 | 1 => owner.lists[field as usize]
            .iter()
            .fold(0i32, |n, list| n.wrapping_add(list.len() as i32)),
        2..=7 => owner.coverage[field as usize - 2],
        8 => owner.premerges,
        _ => -1,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_packet_plan(
    owner: *const Builder,
    route: i32,
    grass: i32,
    audit: i32,
) -> *mut Plan {
    catch_unwind(AssertUnwindSafe(|| {
        owner
            .as_ref()
            .and_then(|owner| Plan::new(owner, route, grass, audit != 0))
            .map(|plan| Box::into_raw(Box::new(plan)))
            .unwrap_or(ptr::null_mut())
    }))
    .unwrap_or(ptr::null_mut())
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_packet_release(plan: *mut Plan) {
    if !plan.is_null() {
        drop(Box::from_raw(plan));
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_packet_count(plan: *const Plan, field: i32) -> i32 {
    let Some(plan) = plan.as_ref() else {
        return -1;
    };
    let count = match field {
        0 => plan.entries.len(),
        1 => plan.events.len(),
        2 => plan.callbacks,
        _ => return -1,
    };
    i32::try_from(count).unwrap_or(-1)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_packet_events(
    plan: *const Plan,
    output: *mut u8,
    count: i32,
) -> i32 {
    let Some(plan) = plan.as_ref() else {
        return 0;
    };
    if count < 0 || count as usize != plan.events.len() || count > 0 && output.is_null() {
        return 0;
    }
    if count > 0 {
        ptr::copy_nonoverlapping(
            plan.events.as_ptr().cast::<u8>(),
            output,
            plan.events.len() * 16,
        );
    }
    1
}
fn aligned<T>(p: *const T) -> bool {
    !p.is_null() && p as usize % std::mem::align_of::<T>() == 0
}


/// One complete section build into this owner. Inputs stay borrowed for the
/// call only; the center and neighbor sources are pinned by the caller's scope.
/// Returns 1 + the count of semantic identities written to `order` in first-use
/// order, a negative size status (-2 zero, -3 negative quad, -4 negative point
/// height), or 0 on failure.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_section_build(
    owner: *mut Builder,
    params: *const i64,
    center: *const crate::render::dh_columns::source::Source,
    neighbors: *const *const crate::render::dh_columns::source::Source,
    min_x: *const i16,
    min_z: *const i16,
    flag_colors: *const u32,
    spans: *const i64,
    span_len: i32,
    contributors: *const i64,
    contributor_len: i32,
    order: *mut i32,
    order_capacity: i32,
) -> i32 {
    use super::section::*;
    catch_unwind(AssertUnwindSafe(|| {
        let (Some(owner), Some(center)) = (owner.as_mut(), center.as_ref()) else {
            return 0;
        };
        if !aligned(params) || !aligned(neighbors) || !aligned(min_x) || !aligned(min_z)
            || span_len < 0 || contributor_len < 0 || order_capacity < 0
            || span_len > 0 && !aligned(spans) || contributor_len > 0 && !aligned(contributors)
            || order_capacity > 0 && !aligned(order) || !flag_colors.is_null() && !aligned(flag_colors)
        {
            return 0;
        }
        let params = &*(params as *const [i64; PARAMS]);
        let sized = |source: &crate::render::dh_columns::source::Source, height: i64| {
            height >= 0 && (height as usize).checked_mul(64 * 64) == Some(source.points().len())
        };
        if !sized(center, params[P_HEIGHT]) || !(0..=31).contains(&params[P_DETAIL]) {
            return 0;
        }
        let mut adjacent = [None; 4];
        for (d, slot) in adjacent.iter_mut().enumerate() {
            if let Some(source) = (*neighbors.add(d)).as_ref() {
                if !sized(source, params[P_NEIGHBOR_HEIGHT + d]) {
                    return 0;
                }
                *slot = Some(source);
            }
        }
        let section = Section {
            params,
            center,
            neighbors: adjacent,
            min_x: &*(min_x as *const [i16; 64]),
            min_z: &*(min_z as *const [i16; 64]),
            flag_colors: (!flag_colors.is_null()).then(|| std::slice::from_raw_parts(flag_colors, 64 * 64)),
            sidecars: Sidecars {
                spans: if span_len == 0 { &[] } else { std::slice::from_raw_parts(spans, span_len as usize) },
                contributors: if contributor_len == 0 {
                    &[]
                } else {
                    std::slice::from_raw_parts(contributors, contributor_len as usize)
                },
            },
        };
        let order = if order_capacity == 0 {
            &mut [][..]
        } else {
            std::slice::from_raw_parts_mut(order, order_capacity as usize)
        };
        build(owner, &section, order)
    }))
    .unwrap_or(0)
}

//! Java bridge for the DH semantic collector's ledger (`render/dh_collector`);
//! see `DistantHorizonsSemanticCollector`. Java calls these while holding
//! its collector lock, so variable-length results (effects, updates,
//! segment lists, text) wait in this thread's buffers until it takes them.
use crate::render::dh_collector::{
    allocate_generation, ledger, peek_next_generation, Acknowledged, Built, Column, Config, Counts, Effect, Failure, Instance,
    Ledger, Visible,
};
use std::cell::RefCell;
use std::ffi::{c_char, CStr};

thread_local! {
    static EFFECTS: RefCell<Vec<Effect>> = const { RefCell::new(Vec::new()) };
    static OUTPUT: RefCell<Vec<i64>> = const { RefCell::new(Vec::new()) };
    static TEXT: RefCell<Vec<u8>> = const { RefCell::new(Vec::new()) };
}

const ERR_POISONED: i32 = -100;

fn code(failure: Failure) -> i32 {
    match failure {
        Failure::PendingVisibleAdmission => -1,
        Failure::VisibleSegmentCapture => -2,
        Failure::SelectWithoutFrame => -3,
        Failure::SelectUnsupportedSegments => -4,
        Failure::RetentionBounds => -5,
        Failure::ByteOverflow => -6,
    }
}

fn config(bits: i32) -> Config {
    Config {
        rust_whole_frame: bits & 1 != 0,
        legacy_observation: bits & 2 != 0,
        execution_snapshots: bits & 4 != 0,
        trace_publication: bits & 8 != 0,
    }
}

fn with<T>(f: impl FnOnce(&mut Ledger) -> T) -> Option<T> {
    ledger().lock().ok().map(|mut l| f(&mut l))
}

/// Runs a mutating call and stores its effects; returns their count.
fn with_effects(f: impl FnOnce(&mut Ledger, &mut Vec<Effect>) -> Result<(), Failure>) -> i32 {
    let mut effects = Vec::new();
    match with(|l| f(l, &mut effects)) {
        None => ERR_POISONED,
        Some(Err(failure)) => {
            EFFECTS.with(|e| *e.borrow_mut() = effects);
            code(failure)
        }
        Some(Ok(())) => {
            let count = effects.len() as i32;
            EFFECTS.with(|e| *e.borrow_mut() = effects);
            count
        }
    }
}

unsafe fn text<'a>(pointer: *const c_char) -> &'a str {
    if pointer.is_null() {
        return "";
    }
    CStr::from_ptr(pointer).to_str().unwrap_or("")
}

fn store_output(values: Vec<i64>) -> i32 {
    let length = values.len() as i32;
    OUTPUT.with(|o| *o.borrow_mut() = values);
    length
}

fn store_text(values: &[&str]) -> i32 {
    let joined = values.join("\0").into_bytes();
    let length = joined.len() as i32;
    TEXT.with(|t| *t.borrow_mut() = joined);
    length
}

fn push_instances(out: &mut Vec<i64>, instances: &[Instance]) {
    for i in instances {
        out.extend([i.column_key, i.column_generation, i.layer as i64, i.segment_index as i64, i.order as i64]);
    }
}

/// Copies and clears this thread's effects, three longs each: [kind, key,
/// detail]. Kinds: 1 set current, 2 drop current, 3 publish current (detail
/// 1 removes absent provenance), 4 publish update (detail index << 1 |
/// retain payload), 5 drop published, 6 clear. Returns the count copied.
/// # Safety
/// `out` addresses `capacity` longs.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_collector_take_effects(out: *mut i64, capacity: i32) -> i32 {
    EFFECTS.with(|e| {
        let effects = std::mem::take(&mut *e.borrow_mut());
        let count = effects.len().min(capacity.max(0) as usize / 3);
        let out = std::slice::from_raw_parts_mut(out, count * 3);
        for (slot, effect) in out.chunks_exact_mut(3).zip(&effects) {
            let row = match *effect {
                Effect::SetCurrent(key) => [1, key, 0],
                Effect::DropCurrent(key) => [2, key, 0],
                Effect::PublishCurrent { key, remove_absent_provenance } => [3, key, remove_absent_provenance as i64],
                Effect::PublishUpdate { key, index, retain_payload } => [4, key, (index as i64) << 1 | retain_payload as i64],
                Effect::DropPublished(key) => [5, key, 0],
                Effect::Clear => [6, 0, 0],
            };
            slot.copy_from_slice(&row);
        }
        count as i32
    })
}

/// Copies and clears this thread's output longs; returns the count copied.
/// # Safety
/// `out` addresses `capacity` longs.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_collector_take_output(out: *mut i64, capacity: i32) -> i32 {
    OUTPUT.with(|o| {
        let values = std::mem::take(&mut *o.borrow_mut());
        let count = values.len().min(capacity.max(0) as usize);
        std::slice::from_raw_parts_mut(out, count).copy_from_slice(&values[..count]);
        count as i32
    })
}

/// Copies and clears this thread's text: UTF-8, values separated by NUL.
/// With a null `out` it only returns the text's length.
/// # Safety
/// `out` is null or addresses `capacity` bytes.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_collector_take_text(out: *mut u8, capacity: i32) -> i32 {
    if out.is_null() {
        return TEXT.with(|t| t.borrow().len() as i32);
    }
    TEXT.with(|t| {
        let bytes = std::mem::take(&mut *t.borrow_mut());
        let count = bytes.len().min(capacity.max(0) as usize);
        std::slice::from_raw_parts_mut(out, count).copy_from_slice(&bytes[..count]);
        count as i32
    })
}

#[no_mangle]
pub extern "C" fn mattmc_dh_collector_allocate_generation() -> i64 {
    allocate_generation()
}

/// Scalar queries. Kinds: 0 lifecycle, 1 stale route receipts, 2 route
/// executions, 3 route frame, 4 last executed world frame, 5 unpublished
/// visible columns, 6 visible payload stable for `arg` frames, 7 published
/// generation of `arg`, 8 retained published payload generation of `arg`,
/// 9 touch `arg` (`COLUMNS.get`) and return its generation, 10 column count,
/// 11 pending visible segments, 12 frame enabled, 13 frame flags, 14 next
/// generation, 15 hasColumn(`arg`), 16 hasPublishedColumn(`arg`). 0 means
/// "none" for generations. Returns `i64::MIN` for an unknown kind.
#[no_mangle]
pub extern "C" fn mattmc_dh_collector_query(kind: i32, arg: i64) -> i64 {
    with(|l| match kind {
        0 => l.lifecycle(),
        1 => l.receipts.stale_route_executions,
        2 => l.route.execution_count as i64,
        3 => l.route.frame,
        4 => l.route.last_executed_world_frame,
        5 => l.route.unpublished_visible_columns as i64,
        6 => l.visible_payload_stable(arg as i32) as i64,
        7 => l.published_generation(arg).unwrap_or(0),
        8 => l.published_payload_generation(arg).unwrap_or(0),
        9 => l.touch(arg).unwrap_or(0),
        10 => l.column_count() as i64,
        11 => l.pending_segments().len() as i64,
        12 => l.frame().enabled as i64,
        13 => l.frame().flags as i64,
        14 => peek_next_generation(),
        15 => l.has_column(arg) as i64,
        16 => l.has_published_column(arg) as i64,
        _ => i64::MIN,
    })
    .unwrap_or(i64::MIN)
}

#[no_mangle]
pub extern "C" fn mattmc_dh_collector_has_column_generation(key: i64, generation: i64) -> i32 {
    with(|l| l.has_column_generation(key, generation) as i32).unwrap_or(ERR_POISONED)
}

/// Returns 1 when the column is published, 0 when not, or an error code.
#[no_mangle]
pub extern "C" fn mattmc_dh_collector_request_publication(key: i64) -> i32 {
    with(|l| l.request_publication(key).map_or_else(code, |p| p as i32)).unwrap_or(ERR_POISONED)
}

/// `recordBuiltSnapshotLocked` and, with `retain_owner`, the owner lease.
/// `difference` is Java's payload difference (null without a replaced
/// column); `provenance_bytes` is -1 without provenance. Writes [generation,
/// owner token] to `out`. Returns the effect count or an error code.
/// # Safety
/// `difference` is null or NUL-terminated UTF-8; `out` addresses 2 longs.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_collector_record_built(
    config_bits: i32,
    key: i64,
    generation: i64,
    byte_size: i64,
    counts: *const i32,
    has_segments: i32,
    provenance_bytes: i64,
    same_payload: i32,
    same_provenance: i32,
    difference: *const c_char,
    retain_owner: i32,
    out: *mut i64,
) -> i32 {
    let counts = std::slice::from_raw_parts(counts, 4);
    let built = Built {
        column: Column {
            generation,
            byte_size,
            counts: Counts { opaque: counts[0], side: counts[1], up: counts[2], water: counts[3] },
        },
        has_segments: has_segments != 0,
        provenance_bytes: (provenance_bytes >= 0).then_some(provenance_bytes),
        same_payload: same_payload != 0,
        same_provenance: same_provenance != 0,
    };
    let difference = (!difference.is_null()).then(|| text(difference).to_owned());
    let config = config(config_bits);
    let out = std::slice::from_raw_parts_mut(out, 2);
    with_effects(|l, effects| {
        let generation = l.record_built(config, key, built, difference, effects)?;
        let token = if retain_owner != 0 { l.acquire_owner(key, generation) } else { 0 };
        out[0] = generation;
        out[1] = token as i64;
        Ok(())
    })
}

#[no_mangle]
pub extern "C" fn mattmc_dh_collector_release_owner(config_bits: i32, key: i64, generation: i64, token: i64) -> i32 {
    with_effects(|l, effects| {
        l.release_owner(config(config_bits), key, generation, token as u64, effects);
        Ok(())
    })
}

#[no_mangle]
pub extern "C" fn mattmc_dh_collector_record_build_attempt(config_bits: i32) {
    with(|l| l.record_build_attempt(config(config_bits)));
}

/// `removeColumn(key, generation)`, or `removeColumn(key)` when
/// `generation` is negative. Returns the effect count.
#[no_mangle]
pub extern "C" fn mattmc_dh_collector_remove_column(config_bits: i32, key: i64, generation: i64) -> i32 {
    with_effects(|l, effects| {
        if generation < 0 {
            l.remove_column(config(config_bits), key, effects);
        } else {
            l.remove_column_generation(config(config_bits), key, generation, effects);
        }
        Ok(())
    })
}

#[no_mangle]
pub extern "C" fn mattmc_dh_collector_trim(maximum_columns: i32, maximum_bytes: i64) -> i32 {
    with_effects(|l, effects| l.trim(maximum_columns.max(0) as usize, maximum_bytes, effects))
}

/// `beginVisibleFrame`'s state change: an enabled frame with `flags`, or
/// with `accepted` 0 a rejected one for `reason`.
/// # Safety
/// The strings are null or NUL-terminated UTF-8.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_collector_begin_frame(
    accepted: i32,
    flags: i32,
    reason: *const c_char,
    matrix_status: *const c_char,
    matrix_detail: *const c_char,
) {
    let frame = (accepted != 0).then_some(flags);
    let (reason, status, detail) = (text(reason), text(matrix_status), text(matrix_detail));
    with(|l| l.begin_frame(frame, reason, status, detail));
}

#[no_mangle]
pub extern "C" fn mattmc_dh_collector_set_clip_distance(clip_distance: f32) {
    with(|l| l.set_clip_distance(clip_distance));
}

/// `recordVisibleMaterialColumn`'s admission; with `append` the admitted
/// segments are appended too. Writes [generation, opaque, side, up, water].
/// Returns 1 when admitted, 0 when empty, or an error code.
/// # Safety
/// `out` addresses 5 longs.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_collector_visible_column(key: i64, append: i32, out: *mut i64) -> i32 {
    let out = std::slice::from_raw_parts_mut(out, 5);
    with(|l| {
        let visible = match l.visible_column(key) {
            Ok(visible) => visible,
            Err(failure) => return code(failure),
        };
        let Visible::Admit { generation, counts } = visible else { return 0 };
        out.copy_from_slice(&[generation, counts.opaque as i64, counts.side as i64, counts.up as i64, counts.water as i64]);
        if append != 0 {
            if let Err(failure) = l.append_visible_column(key, generation, counts) {
                return code(failure);
            }
        }
        1
    })
    .unwrap_or(ERR_POISONED)
}

#[no_mangle]
pub extern "C" fn mattmc_dh_collector_append_visible_column(key: i64, generation: i64, opaque: i32, side: i32, up: i32, water: i32) -> i32 {
    with(|l| l.append_visible_column(key, generation, Counts { opaque, side, up, water }).map_or_else(code, |_| 0))
        .unwrap_or(ERR_POISONED)
}

/// # Safety
/// `indexes` addresses `count` ints.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_collector_append_segments(key: i64, generation: i64, layer: i32, indexes: *const i32, count: i32) -> i32 {
    let indexes = if count <= 0 { &[][..] } else { std::slice::from_raw_parts(indexes, count as usize) };
    with(|l| l.append_segments(key, generation, layer, indexes).map_or_else(code, |_| 0)).unwrap_or(ERR_POISONED)
}

/// Stores a segment list as output, five longs per segment: 0 pending, 1
/// last consumed. Returns the output length.
#[no_mangle]
pub extern "C" fn mattmc_dh_collector_segments(kind: i32) -> i32 {
    let mut out = Vec::new();
    with(|l| push_instances(&mut out, if kind == 0 { l.pending_segments() } else { l.last_consumed() }));
    store_output(out)
}

/// Consumes the frame. Modes: 0 `consumeVisibleFrame`, 1
/// `consumeRenderFrame`, 2 `consumeVisibleSegments`. Writes [enabled, flags,
/// lifecycle, route frame] and stores the consumed segments as output;
/// returns the output length.
/// # Safety
/// `header` addresses 4 longs.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_collector_consume(mode: i32, header: *mut i64) -> i32 {
    let header = std::slice::from_raw_parts_mut(header, 4);
    let mut out = Vec::new();
    with(|l| {
        let (segments, frame) = match mode {
            0 => l.consume_frame(),
            1 => (Vec::new(), l.consume_render_frame()),
            _ => (l.consume_segments(), l.frame()),
        };
        header.copy_from_slice(&[frame.enabled as i64, frame.flags as i64, l.lifecycle(), l.route.frame]);
        push_instances(&mut out, &segments);
    });
    store_output(out)
}

/// Returns 1 when the route became selected, 0 when it already was, or an
/// error code.
#[no_mangle]
pub extern "C" fn mattmc_dh_collector_select_route(complete_exact_atlas: i32) -> i32 {
    with(|l| l.select_route(complete_exact_atlas != 0).map_or_else(code, |s| s as i32)).unwrap_or(ERR_POISONED)
}

/// # Safety
/// `reason` is NUL-terminated UTF-8.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_collector_reject_route(reason: *const c_char, opaque: i32, transparent: i32, water: i32) {
    let reason = text(reason);
    with(|l| l.reject_route(reason, opaque, transparent, water));
}

/// Records a completed frame's execution; `lifecycle` `i64::MIN` means the
/// current one. Writes the route frame to `out`. Returns 1 when recorded,
/// 0 for a stale lifecycle.
/// # Safety
/// `out` addresses 1 long.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_collector_record_execution(
    lifecycle: i64,
    world_frame: i64,
    submission: i64,
    capture_frame: i64,
    instances: i32,
    opaque: i32,
    transparent: i32,
    water: i32,
    out: *mut i64,
) -> i32 {
    with(|l| {
        let lifecycle = if lifecycle == i64::MIN { l.lifecycle() } else { lifecycle };
        let recorded = l.record_execution(lifecycle, world_frame, submission, capture_frame, instances, opaque, transparent, water);
        *out = l.route.frame;
        recorded as i32
    })
    .unwrap_or(ERR_POISONED)
}

#[no_mangle]
pub extern "C" fn mattmc_dh_collector_observe_render_list(visible_columns: i32) {
    with(|l| l.observe_render_list(visible_columns));
}

/// # Safety
/// `keys` addresses `count` longs.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_collector_record_visibility(candidates: i32, unpublished: i32, keys: *const i64, count: i32) {
    let keys = if count <= 0 { &[][..] } else { std::slice::from_raw_parts(keys, count as usize) };
    with(|l| l.record_visibility(candidates, unpublished, keys));
}

/// `pendingUpdate`: stores [generation, asset count, retirement count, then
/// (key, generation) per asset and per retirement] as output and returns its
/// length, 0 for no update, or an error code.
#[no_mangle]
pub extern "C" fn mattmc_dh_collector_pending_update(config_bits: i32, visible_candidates_only: i32) -> i32 {
    let result = with(|l| l.pending_update(config(config_bits), visible_candidates_only != 0));
    match result {
        None => ERR_POISONED,
        Some(Err(failure)) => code(failure),
        Some(Ok(None)) => 0,
        Some(Ok(Some(update))) => {
            let mut out = vec![update.generation, update.assets.len() as i64, update.retirements.len() as i64];
            for asset in &update.assets {
                out.extend([asset.column_key, asset.generation]);
            }
            for &(key, generation) in &update.retirements {
                out.extend([key, generation]);
            }
            store_output(out)
        }
    }
}

/// `acknowledge(update)`: `assets` holds (key, generation, opaque, side, up,
/// water) per asset, `retirements` (key, generation). Returns the effect count.
/// # Safety
/// The pointers address `asset_count` × 6 and `retirement_count` × 2 longs.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_collector_acknowledge(
    config_bits: i32,
    assets: *const i64,
    asset_count: i32,
    retirements: *const i64,
    retirement_count: i32,
) -> i32 {
    let raw = if asset_count <= 0 { &[][..] } else { std::slice::from_raw_parts(assets, asset_count as usize * 6) };
    let assets: Vec<Acknowledged> = raw
        .chunks_exact(6)
        .map(|a| Acknowledged {
            column_key: a[0],
            generation: a[1],
            counts: Counts { opaque: a[2] as i32, side: a[3] as i32, up: a[4] as i32, water: a[5] as i32 },
        })
        .collect();
    let raw = if retirement_count <= 0 { &[][..] } else { std::slice::from_raw_parts(retirements, retirement_count as usize * 2) };
    let retirements: Vec<(i64, i64)> = raw.chunks_exact(2).map(|r| (r[0], r[1])).collect();
    with_effects(|l, effects| {
        l.acknowledge(config(config_bits), &assets, &retirements, effects);
        Ok(())
    })
}

/// # Safety
/// `assets` addresses `count` × 2 longs (key, generation).
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_collector_release_in_flight(assets: *const i64, count: i32) {
    let raw = if count <= 0 { &[][..] } else { std::slice::from_raw_parts(assets, count as usize * 2) };
    let assets: Vec<(i64, i64)> = raw.chunks_exact(2).map(|a| (a[0], a[1])).collect();
    with(|l| l.release_in_flight(&assets));
}

/// `clearWithReason`. Returns the effect count.
/// # Safety
/// `reason` is NUL-terminated UTF-8.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_collector_clear(reason: *const c_char) -> i32 {
    let reason = text(reason).to_owned();
    with_effects(|l, effects| {
        l.clear(&reason, effects);
        Ok(())
    })
}

#[no_mangle]
pub extern "C" fn mattmc_dh_collector_reset_for_test() -> i32 {
    with_effects(|l, effects| {
        l.reset_for_test(effects);
        Ok(())
    })
}

#[no_mangle]
pub extern "C" fn mattmc_dh_collector_begin_test_frame(enabled: i32) {
    with(|l| l.begin_test_frame(enabled != 0));
}

/// Stores the column keys in LRU order as output; returns its length.
#[no_mangle]
pub extern "C" fn mattmc_dh_collector_column_keys() -> i32 {
    store_output(with(|l| l.column_keys().collect()).unwrap_or_default())
}

/// Stores a column's last payload difference as text; returns its length,
/// or -1 when it has none.
#[no_mangle]
pub extern "C" fn mattmc_dh_collector_payload_difference(key: i64) -> i32 {
    with(|l| l.payload_difference(key).map(|d| store_text(&[d]))).flatten().unwrap_or(-1)
}

/// The ledger's part of `routeDiagnosticsSnapshot`: stores the numbers as
/// output (order in `DistantHorizonsSemanticCollector.routeDiagnosticsSnapshot`)
/// and [decision, reason, matrix status, matrix detail, last payload
/// difference, last reset reason] as text. Returns the output length.
#[no_mangle]
pub extern "C" fn mattmc_dh_collector_diagnostics() -> i32 {
    let numbers = with(|l| {
        let (r, c) = (&l.route, &l.receipts);
        store_text(&[&r.decision, &r.reason, &r.matrix_status, &r.matrix_detail, &c.last_payload_difference, &c.last_reset_reason]);
        vec![
            r.frame,
            r.clip_distance.to_bits() as i64,
            r.opaque_segments as i64,
            r.transparent_segments as i64,
            r.water_segments as i64,
            r.visible_columns as i64,
            r.cached_columns as i64,
            r.candidate_columns as i64,
            r.unpublished_candidates as i64,
            r.unpublished_visible_columns as i64,
            c.build_attempts,
            c.built,
            c.reused,
            c.replaced,
            c.lifecycle_resets,
            c.resource_reload_resets,
            c.world_unload_resets,
            c.last_published_retirements as i64,
            c.last_invalidated_in_flight as i64,
            c.last_retirements_acknowledged as i64,
            c.last_retirements_superseded as i64,
            l.last_lifecycle_retirement_count() as i64,
            l.pending_retirement_count() as i64,
            l.invalidated_in_flight_count() as i64,
            c.last_generation_floor,
            l.minimum_published_generation(),
            l.retained_bytes(),
            l.oversized_column_count() as i64,
            l.frame().enabled as i64,
            r.selected as i64,
            r.last_executed_route_frame,
            r.last_executed_world_frame,
            r.last_executed_submission,
            r.last_executed_capture_frame,
            r.last_executed_instances as i64,
            r.last_executed_opaque as i64,
            r.last_executed_transparent as i64,
            r.last_executed_water as i64,
            r.last_executed_semantics as i64,
        ]
    })
    .unwrap_or_default();
    store_output(numbers)
}

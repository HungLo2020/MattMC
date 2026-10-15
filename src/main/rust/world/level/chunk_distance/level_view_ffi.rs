//! C ABI for Java's `NativeLevelView`. A view handle and a cursor handle are
//! each one `Arc` reference (a cursor also holds its view); Java's auto arenas
//! release them after their last call. Taking a graph's changes copies them
//! under the graph's lock (or with Java holding the player graph's lock), then
//! applies them under the view's: the locks never nest.
//!
//! Status: 0 ok, -3 allocation failure, -14 internal fault.
use std::sync::Arc;

use super::level_view::{LevelView, SharedView, Step, ViewCursor};
use super::ticket_storage::Fault;

fn fault(fault: Fault) -> i64 {
    match fault {
        Fault::Allocation => -3,
        _ => -14,
    }
}

/// Caller must provide a live handle from create and keep its arena live.
unsafe fn view<'a>(id: u64) -> &'a Arc<SharedView> {
    &*std::ptr::with_exposed_provenance::<Arc<SharedView>>(id as usize)
}

unsafe fn cursor<'a>(id: u64) -> &'a mut ViewCursor {
    &mut *std::ptr::with_exposed_provenance_mut::<ViewCursor>(id as usize)
}

fn apply(view: &SharedView, changes: Result<Vec<(i64, i32)>, Fault>) -> i64 {
    match changes.and_then(|changes| view.lock().apply(&changes)) {
        Ok(()) => 0,
        Err(error) => fault(error),
    }
}

fn copy(changes: &[(i64, i32)]) -> Result<Vec<(i64, i32)>, Fault> {
    let mut out = Vec::new();
    out.try_reserve_exact(changes.len()).map_err(|_| Fault::Allocation)?;
    out.extend_from_slice(changes);
    Ok(out)
}

#[no_mangle]
pub extern "C" fn mattmc_level_view_create(absent: i32, remove_from: i32) -> u64 {
    match LevelView::new(absent, remove_from) {
        Ok(view) => Box::into_raw(Box::new(Arc::new(SharedView::new(view)))).expose_provenance() as u64,
        Err(_) => 0,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_level_view_release(id: u64) {
    if id != 0 {
        drop(Box::from_raw(std::ptr::with_exposed_provenance_mut::<Arc<SharedView>>(id as usize)));
    }
}

/// Applies a ticket graph's changes from its last run (`run` left them recorded).
#[no_mangle]
pub unsafe extern "C" fn mattmc_level_view_take_ticket(id: u64, distance: u64) -> i64 {
    let changes = {
        let mut distance = super::ticket_ffi::shared(distance).lock();
        let field = distance.field_mut();
        let changes = copy(field.changes());
        if changes.is_ok() {
            field.clear_changes();
        }
        changes
    };
    apply(view(id), changes)
}

/// Applies a player-distance field's changes from its last run. The caller
/// holds that graph's Java lock, which serializes every access to it.
#[no_mangle]
pub unsafe extern "C" fn mattmc_level_view_take_player(id: u64, distances: u64, field: i32) -> i64 {
    if !(0..2).contains(&field) {
        return -14;
    }
    let changes = {
        let field = super::ffi::handle(distances).field_mut(field as usize);
        let changes = copy(field.changes());
        if changes.is_ok() {
            field.clear_changes();
        }
        changes
    };
    apply(view(id), changes)
}

/// `chunks.get(key)`.
#[no_mangle]
pub unsafe extern "C" fn mattmc_level_view_get(id: u64, key: i64) -> i32 {
    view(id).lock().get(key)
}

/// `chunks.size()`.
#[no_mangle]
pub unsafe extern "C" fn mattmc_level_view_size(id: u64) -> i32 {
    view(id).lock().len() as i32
}

/// A new walk over the view (`fastIterable` / `keySet().iterator()`); 0 on failure.
#[no_mangle]
pub unsafe extern "C" fn mattmc_level_view_cursor(id: u64) -> u64 {
    Box::into_raw(Box::new(ViewCursor::new(Arc::clone(view(id))))).expose_provenance() as u64
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_level_view_cursor_release(id: u64) {
    if id != 0 {
        drop(Box::from_raw(std::ptr::with_exposed_provenance_mut::<ViewCursor>(id as usize)));
    }
}

/// The next entry with level <= `max_level`: 1 with `out = [key, level]`, 0 at
/// the end, 2 for Java's ArrayIndexOutOfBoundsException with `out = [index,
/// length]`, 3 for its NullPointerException (the null wrapped list), or a
/// negative fault.
#[no_mangle]
pub unsafe extern "C" fn mattmc_level_view_cursor_next(id: u64, max_level: i32, out: *mut i64) -> i32 {
    match cursor(id).next(max_level) {
        Ok(Step::Entry(key, level)) => {
            out.write(key);
            out.add(1).write(i64::from(level));
            1
        }
        Ok(Step::End) => 0,
        Ok(Step::OutOfBounds(index, length)) => {
            out.write(i64::from(index));
            out.add(1).write(length as i64);
            2
        }
        Ok(Step::NullWrapped) => 3,
        Err(error) => fault(error) as i32,
    }
}

/// One `setLevel(key, level)` (the tracker rule), for callers outside a graph run.
#[no_mangle]
pub unsafe extern "C" fn mattmc_level_view_set(id: u64, key: i64, level: i32) -> i64 {
    match view(id).lock().set_level(key, level) {
        Ok(()) => 0,
        Err(error) => fault(error),
    }
}

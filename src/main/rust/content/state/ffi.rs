//! Immutable CPU graph ownership for the temporary Java state views.
use super::StateGraph;

/// Returns a native-owned graph, or null for invalid/oversized domains.
/// # Safety
/// `counts` addresses `len` values (may be null for zero); `header` addresses
/// five i32 values. The returned owner must be released exactly once, after
/// all borrowed buffer views have become unreachable.
#[no_mangle]
pub unsafe extern "C" fn mattmc_state_graph_create(
    counts: *const i32,
    len: i32,
    header: *mut i32,
) -> *mut StateGraph {
    if len < 0
        || len as usize > super::MAX_STATES
        || header.is_null()
        || (len > 0 && counts.is_null())
    {
        return std::ptr::null_mut();
    }
    let counts = if len == 0 {
        &[][..]
    } else {
        std::slice::from_raw_parts(counts, len as usize)
    };
    let Ok(counts) = counts
        .iter()
        .map(|&n| u16::try_from(n))
        .collect::<Result<Vec<_>, _>>()
    else {
        return std::ptr::null_mut();
    };
    let Ok(graph) = StateGraph::new(&counts) else {
        return std::ptr::null_mut();
    };
    std::slice::from_raw_parts_mut(header, 5).copy_from_slice(&graph.header());
    Box::into_raw(Box::new(graph))
}

/// Borrows buffer 0 (state values), 1 (transition targets), or 2 (slot offsets).
/// # Safety
/// `owner` is a live graph from create. Borrowed rows are immutable and remain
/// valid only until release. Lengths are supplied by create's header.
#[no_mangle]
pub unsafe extern "C" fn mattmc_state_graph_buffer(
    owner: *const StateGraph,
    kind: i32,
) -> *const i32 {
    let Some(graph) = owner.as_ref() else {
        return std::ptr::null();
    };
    match kind {
        0 => graph.values.as_ptr(),
        1 => graph.targets.as_ptr(),
        2 => graph.offsets.as_ptr(),
        _ => std::ptr::null(),
    }
}

/// # Safety
/// `owner` is null or a graph returned by create, released exactly once after
/// every borrowed view is unreachable.
#[no_mangle]
pub unsafe extern "C" fn mattmc_state_graph_release(owner: *mut StateGraph) {
    if !owner.is_null() {
        drop(Box::from_raw(owner));
    }
}

use super::{search, Node};

fn valid_length(len: i32) -> bool {
    len > 0 && len as usize <= isize::MAX as usize / std::mem::size_of::<Node>()
}

/// # Safety
/// `nodes` addresses `len` aligned readable Nodes, borrowed for this call.
#[no_mangle]
pub unsafe extern "C" fn mattmc_climate_validate(nodes: *const Node, len: i32) -> i32 {
    if nodes.is_null() || !valid_length(len) {
        return -1;
    }
    let nodes = unsafe { std::slice::from_raw_parts(nodes, len as usize) };
    if search::valid(nodes) {
        0
    } else {
        -2
    }
}

/// # Safety
/// Nodes must be an unchanged, validated tree of `len` nodes. Points contains
/// `count` aligned [i64;8] rows (last coordinate zero); output has `count` i32s.
/// All buffers are live, disjoint and borrowed; no pointer is retained. Calls
/// use native buffers without heap pinning; only small trees use critical calls.
#[no_mangle]
pub unsafe extern "C" fn mattmc_climate_batch(
    nodes: *const Node,
    len: i32,
    points: *const [i64; 8],
    out: *mut i32,
    count: i32,
    previous: i32,
) -> i32 {
    if nodes.is_null()
        || points.is_null()
        || out.is_null()
        || !valid_length(len)
        || !(1..=64).contains(&count)
        || previous < -1
        || previous >= len
    {
        return -1;
    }
    let nodes = unsafe { std::slice::from_raw_parts(nodes, len as usize) };
    if previous >= 0 && nodes[previous as usize].leaf != 1 {
        return -2;
    }
    let points = unsafe { std::slice::from_raw_parts(points, count as usize) };
    if points.iter().any(|p| p[7] != 0) {
        return -3;
    }
    search::batch(
        nodes,
        points,
        unsafe { std::slice::from_raw_parts_mut(out, count as usize) },
        previous,
    ) as i32
}

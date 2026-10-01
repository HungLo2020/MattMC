use super::Node;

#[inline]
pub(super) fn distance(node: &Node, point: &[i64; 8]) -> i64 {
    let mut sum = 0i64;
    for i in 0..7 {
        let above = point[i].wrapping_sub(node.max[i]);
        let below = node.min[i].wrapping_sub(point[i]);
        let d = if above > 0 { above } else { below.max(0) };
        sum = sum.wrapping_add(d.wrapping_mul(d));
    }
    sum
}

pub(super) fn valid(nodes: &[Node]) -> bool {
    if nodes.is_empty() || nodes[0].next != nodes.len() as i32 {
        return false;
    }
    let mut ends = vec![nodes.len()];
    for (i, n) in nodes.iter().enumerate() {
        while ends.last() == Some(&i) {
            ends.pop();
        }
        if ends.is_empty()
            || n.next <= i as i32
            || n.next as usize > *ends.last().unwrap()
            || n.min[7] != 0
            || n.max[7] != 0
        {
            return false;
        }
        match n.leaf {
            1 if n.next == i as i32 + 1 => {}
            0 if n.next > i as i32 + 1 => ends.push(n.next as usize),
            _ => return false,
        }
    }
    true
}

/// Iterative preorder traversal retains Java's strict comparisons and child
/// order. Each recursive Java subtree is seeded with the same current best leaf;
/// flattening removes call frames without sorting or changing pruning decisions.
#[inline]
fn lookup(
    nodes: &[Node],
    point: &[i64; 8],
    previous: i32,
    metric: impl Fn(&Node, &[i64; 8]) -> i64,
) -> i32 {
    if nodes[0].leaf == 1 {
        return 0;
    }
    let mut best = previous;
    let mut score = if best < 0 {
        i64::MAX
    } else {
        metric(&nodes[best as usize], point)
    };
    // A recursive Java subtree that returns null is dereferenced by its parent.
    // Overflow can produce MAX_VALUE leaf distances despite a smaller bounding
    // distance. Preserve that failure instead of searching a later sibling.
    let mut empty_end = nodes.len();
    let mut i = 1;
    while i < nodes.len() {
        if best < 0 && i >= empty_end {
            return -1;
        }
        let n = &nodes[i];
        let candidate = metric(n, point);
        if score > candidate {
            if n.leaf == 1 {
                best = i as i32;
                score = candidate;
            } else if best < 0 {
                empty_end = empty_end.min(n.next as usize);
            }
            i += 1;
        } else {
            i = n.next as usize;
        }
    }
    best
}

pub(super) fn scalar(nodes: &[Node], point: &[i64; 8], previous: i32) -> i32 {
    lookup(nodes, point, previous, distance)
}

pub(super) fn batch(
    nodes: &[Node],
    points: &[[i64; 8]],
    out: &mut [i32],
    mut previous: i32,
) -> usize {
    #[cfg(target_arch = "x86_64")]
    let vector = is_x86_feature_detected!("avx2");
    for (i, point) in points.iter().enumerate() {
        #[cfg(target_arch = "x86_64")]
        {
            previous = if vector {
                unsafe { super::simd::lookup(nodes, point, previous) }
            } else {
                scalar(nodes, point, previous)
            };
        }
        #[cfg(not(target_arch = "x86_64"))]
        {
            previous = scalar(nodes, point, previous);
        }
        out[i] = previous;
        if previous < 0 {
            return i + 1;
        }
    }
    points.len()
}

#[cfg(target_arch = "x86_64")]
#[inline]
pub(super) unsafe fn vector_lookup(nodes: &[Node], point: &[i64; 8], previous: i32) -> i32 {
    lookup(nodes, point, previous, |n, p| unsafe {
        super::simd::distance(n, p)
    })
}

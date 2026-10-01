use super::{search, Node};
fn node(min: i64, max: i64, next: i32, leaf: i32) -> Node {
    let mut n = Node {
        min: [min; 8],
        max: [max; 8],
        next,
        leaf,
    };
    n.min[7] = 0;
    n.max[7] = 0;
    n
}
#[test]
fn layout_and_validation() {
    assert_eq!(std::mem::size_of::<Node>(), 136);
    assert!(search::valid(&[node(0, 0, 1, 1)]));
    assert!(!search::valid(&[]));
    assert!(!search::valid(&[node(0, 0, 1, 0)]));
    assert!(!search::valid(&[node(0, 0, 0, 1)]));
    assert!(search::valid(&[
        node(0, 10, 3, 0),
        node(0, 0, 2, 1),
        node(10, 10, 3, 1)
    ]));
    unsafe {
        assert_eq!(super::ffi::mattmc_climate_validate(std::ptr::null(), 1), -1);
        assert_eq!(
            super::ffi::mattmc_climate_batch(
                std::ptr::null(),
                1,
                std::ptr::null(),
                std::ptr::null_mut(),
                1,
                -1
            ),
            -1
        );
    }
}

#[test]
fn ffi_rejects_bad_counts_history_and_padding_without_writing() {
    let nodes = [node(0, 10, 3, 0), node(0, 0, 2, 1), node(10, 10, 3, 1)];
    let mut point = [0i64; 8];
    let mut output = 99;
    unsafe {
        for count in [-1, 0, 65] {
            assert_eq!(
                super::ffi::mattmc_climate_batch(nodes.as_ptr(), 3, &point, &mut output, count, -1),
                -1
            );
        }
        for previous in [-2, 3] {
            assert_eq!(
                super::ffi::mattmc_climate_batch(
                    nodes.as_ptr(),
                    3,
                    &point,
                    &mut output,
                    1,
                    previous
                ),
                -1
            );
        }
        assert_eq!(
            super::ffi::mattmc_climate_batch(nodes.as_ptr(), 3, &point, &mut output, 1, 0),
            -2
        );
        point[7] = 1;
        assert_eq!(
            super::ffi::mattmc_climate_batch(nodes.as_ptr(), 3, &point, &mut output, 1, -1),
            -3
        );
        assert_eq!(output, 99);
        assert_eq!(super::ffi::mattmc_climate_validate(nodes.as_ptr(), 0), -1);
        assert_eq!(
            super::ffi::mattmc_climate_validate([node(0, 0, 1, 0)].as_ptr(), 1),
            -2
        );
    }
}
#[test]
fn previous_leaf_wins_ties_and_batches_retain_order() {
    let nodes = [node(0, 10, 3, 0), node(0, 0, 2, 1), node(10, 10, 3, 1)];
    let mut a = [0; 8];
    a[..7].fill(5);
    assert_eq!(search::scalar(&nodes, &a, -1), 1);
    assert_eq!(search::scalar(&nodes, &a, 2), 2);
    let mut b = [0; 8];
    b[..7].fill(10);
    let mut out = [0; 3];
    assert_eq!(search::batch(&nodes, &[a, b, a], &mut out, -1), 3);
    assert_eq!(out, [1, 2, 2]);
}
#[test]
fn wrapping_distances_match_simd() {
    let mut seed = 7u64;
    let mut next = || {
        seed ^= seed << 13;
        seed ^= seed >> 7;
        seed ^= seed << 17;
        seed as i64
    };
    for _ in 0..100_000 {
        let mut n = node(0, 0, 1, 1);
        let mut p = [0; 8];
        for i in 0..7 {
            n.min[i] = next();
            n.max[i] = next();
            p[i] = next();
        }
        let expected = (0..7).fold(0i64, |sum, i| {
            let above = p[i].wrapping_sub(n.max[i]);
            let below = n.min[i].wrapping_sub(p[i]);
            let d = if above > 0 { above } else { below.max(0) };
            sum.wrapping_add(d.wrapping_mul(d))
        });
        assert_eq!(search::distance(&n, &p), expected);
        #[cfg(target_arch = "x86_64")]
        if is_x86_feature_detected!("avx2") {
            assert_eq!(unsafe { super::simd::distance(&n, &p) }, expected);
        }
    }
}

#[test]
fn overflowing_empty_subtree_stops_before_later_siblings() {
    let coordinates = [3_037_000_499, 76_996, 377, 25, 6, 0, 0, 0];
    let mut a = node(0, 0, 3, 1);
    a.min = coordinates;
    a.max = coordinates;
    let mut b = a;
    b.next = 4;
    b.min[0] = -b.min[0];
    b.max[0] = b.min[0];
    assert_eq!(search::distance(&a, &[0; 8]), i64::MAX);
    assert_eq!(search::distance(&b, &[0; 8]), i64::MAX);
    let mut branch = a;
    branch.leaf = 0;
    branch.next = 4;
    branch.min[0] = b.min[0];
    let nodes = [node(0, 0, 5, 0), branch, a, b, node(0, 0, 5, 1)];
    assert!(search::valid(&nodes));
    assert_eq!(search::scalar(&nodes, &[0; 8], -1), -1);
    let mut out = [99];
    assert_eq!(search::batch(&nodes, &[[0; 8]], &mut out, -1), 1);
    assert_eq!(out, [-1]);
    // With previous history the subtree returns that leaf, and search continues.
    assert_eq!(search::scalar(&nodes, &[0; 8], 2), 4);
}

#[test]
fn nested_traversal_matches_recursive_java_algorithm() {
    fn recursive(nodes: &[Node], index: usize, p: &[i64; 8], previous: i32) -> Result<i32, ()> {
        if nodes[index].leaf == 1 {
            return Ok(index as i32);
        }
        let mut best = previous;
        let mut score = if best < 0 {
            i64::MAX
        } else {
            search::distance(&nodes[best as usize], p)
        };
        let mut child = index + 1;
        while child < nodes[index].next as usize {
            let bound = search::distance(&nodes[child], p);
            if score > bound {
                let found = recursive(nodes, child, p, best)?;
                if found < 0 {
                    return Err(());
                }
                let candidate = if child == found as usize {
                    bound
                } else {
                    search::distance(&nodes[found as usize], p)
                };
                if score > candidate {
                    score = candidate;
                    best = found;
                }
            }
            child = nodes[child].next as usize;
        }
        Ok(best)
    }
    let mut state = 71u64;
    let mut next = || {
        state ^= state << 13;
        state ^= state >> 7;
        state ^= state << 17;
        state as i64
    };
    let mut nodes = vec![node(0, 0, 43, 0)];
    for _ in 0..6 {
        let end = nodes.len() + 7;
        nodes.push(node(-10000, 10000, end as i32, 0));
        for _ in 0..6 {
            let mut leaf = node(0, 0, nodes.len() as i32 + 1, 1);
            for d in 0..7 {
                let a = next();
                let b = next();
                leaf.min[d] = a.min(b);
                leaf.max[d] = a.max(b);
            }
            nodes.push(leaf);
        }
    }
    assert!(search::valid(&nodes));
    let mut previous = -1;
    for _ in 0..100_000 {
        let mut p = [0; 8];
        for d in 0..7 {
            p[d] = next();
        }
        let expected = recursive(&nodes, 0, &p, previous).unwrap_or(-1);
        assert_eq!(search::scalar(&nodes, &p, previous), expected);
        let mut output = [99];
        search::batch(&nodes, &[p], &mut output, previous);
        assert_eq!(output[0], expected);
        previous = expected;
    }
}

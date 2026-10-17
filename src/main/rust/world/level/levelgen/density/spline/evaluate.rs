use super::program::{Knot, Node};
#[inline]
fn lerp(t: f32, a: f32, b: f32) -> f32 {
    a + t * (b - a)
}
// Do not reassociate, use FMA, or widen to double: Java rounds every operation.
pub(crate) fn evaluate(nodes: &[Node], knots: &[Knot], id: usize, axes: &[f32; 4]) -> f32 {
    let n = &nodes[id];
    if n.axis == -1 {
        return n.value;
    }
    let f = axes[n.axis as usize];
    let ks = &knots[n.start as usize..(n.start + n.count) as usize];
    // Match Mth.binarySearch(predicate: f < locations[i]), including NaN.
    let mut first = 0;
    let mut count = ks.len();
    while count > 0 {
        let half = count / 2;
        let mid = first + half;
        if f < ks[mid].location {
            count = half;
        } else {
            first = mid + 1;
            count -= half + 1;
        }
    }
    if first == 0 || first == ks.len() {
        let k = &ks[if first == 0 { 0 } else { first - 1 }];
        let v = evaluate(nodes, knots, k.child as usize, axes);
        return if k.derivative == 0.0 {
            v
        } else {
            v + k.derivative * (f - k.location)
        };
    }
    let a = &ks[first - 1];
    let b = &ks[first];
    let t = (f - a.location) / (b.location - a.location);
    let left = evaluate(nodes, knots, a.child as usize, axes);
    let right = evaluate(nodes, knots, b.child as usize, axes);
    let p = a.derivative * (b.location - a.location) - (right - left);
    let q = -b.derivative * (b.location - a.location) + (right - left);
    lerp(t, left, right) + t * (1.0 - t) * lerp(t, p, q)
}

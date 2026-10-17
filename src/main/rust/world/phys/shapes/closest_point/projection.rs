use super::{coordinates::Coordinates, evaluate::clamp};

fn distance(squares: [f64; 3]) -> f64 { squares[0] + squares[1] + squares[2] }
fn square(value: f64, query: f64) -> f64 { let d = value - query; d * d }

/// A unique floating-point projection onto occupied volume has the same result
/// under every ordered box decomposition. Otherwise retain greedy traversal.
/// Finite inputs are validated by FFI. Strictly increasing coordinates and
/// positive alternative distance increments are verified here, never assumed.
pub(super) fn unique<const CUBES: u32>(words: &[u64], coords: &Coordinates,
    query: [f64; 3]) -> Option<[f64; 3]> {
    if query.iter().any(|v|v.to_bits() == (-0.0f64).to_bits()) { return None; }
    for axis in 0..3 {
        let mut previous = coords.get::<CUBES>(axis,0);
        if previous.to_bits() == (-0.0f64).to_bits() { return None; }
        for i in 1..=coords.dims[axis] {
            let value = coords.get::<CUBES>(axis,i);
            if !(value > previous) || value.to_bits() == (-0.0f64).to_bits() { return None; }
            previous = value;
        }
    }
    let p = std::array::from_fn(|a|clamp(query[a],coords.get::<CUBES>(a,0),coords.get::<CUBES>(a,coords.dims[a])));
    let squares = std::array::from_fn(|a|square(p[a],query[a]));
    let best = distance(squares);
    if !best.is_finite() { return None; }
    let mut cells = [[0;2];3];
    for axis in 0..3 {
        let n = coords.dims[axis];
        let (mut low,mut high) = (0,n+1);
        while low < high {
            let mid = (low + high)/2;
            if coords.get::<CUBES>(axis,mid) < p[axis] { low=mid+1; } else { high=mid; }
        }
        let i = low;
        let equal = coords.get::<CUBES>(axis,i) == p[axis];
        cells[axis] = if equal { [i.saturating_sub(1),i.min(n-1)] } else { [i-1,i-1] };
        let mut alternative = f64::INFINITY;
        if i>0 { alternative=alternative.min(square(coords.get::<CUBES>(axis,i-1),query[axis])); }
        let next = if equal { i+1 } else { i };
        if next<=n { alternative=alternative.min(square(coords.get::<CUBES>(axis,next),query[axis])); }
        let mut changed = squares; changed[axis]=alternative;
        // This rejects cancellation, rounding ties and square underflow, even
        // when the real-number nearest point would be unique.
        if !(distance(changed) > best) { return None; }
    }
    for x in cells[0][0]..=cells[0][1] {
        for y in cells[1][0]..=cells[1][1] {
            for z in cells[2][0]..=cells[2][1] {
                let bit = (x*coords.dims[1]+y)*coords.dims[2]+z;
                if words[bit>>6] & (1u64<<(bit&63)) != 0 { return Some(p); }
            }
        }
    }
    None
}

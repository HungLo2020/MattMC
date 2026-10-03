/// Java Math.min for finite operands, including its negative-zero preference.
#[inline]
fn java_min(a: f64, b: f64) -> f64 {
    if a == 0.0 && b == 0.0 {
        f64::from_bits(a.to_bits() | b.to_bits())
    } else { a.min(b) }
}

#[inline]
pub(super) fn clamp(value: f64, low: f64, high: f64) -> f64 {
    if value < low { low } else { java_min(value, high) }
}

/// Strict comparison keeps the first ordered box on ties, including overflow.
/// Coordinates are copied current Java lists; cube axes use exact index/parts.
pub(super) fn evaluate(words: &mut [u64], dims: [usize; 3], coordinates: &[f64],
    cubes: u32, query: [f64; 3]) -> Option<[f64; 3]> {
    match cubes {
        0 => evaluate_mode::<0>(words,dims,coordinates,query),
        1 => evaluate_mode::<1>(words,dims,coordinates,query),
        2 => evaluate_mode::<2>(words,dims,coordinates,query),
        3 => evaluate_mode::<3>(words,dims,coordinates,query),
        4 => evaluate_mode::<4>(words,dims,coordinates,query),
        5 => evaluate_mode::<5>(words,dims,coordinates,query),
        6 => evaluate_mode::<6>(words,dims,coordinates,query),
        7 => evaluate_mode::<7>(words,dims,coordinates,query),
        _ => unreachable!("FFI validates cube mask"),
    }
}

fn evaluate_mode<const CUBES: u32>(words: &mut [u64], dims: [usize;3], coordinates: &[f64],
    query: [f64;3]) -> Option<[f64;3]> {
    let coords = super::coordinates::Coordinates::new(dims,coordinates);
    if let Some(p) = super::projection::unique::<CUBES>(words,&coords,query) { return Some(p); }
    let coordinate = |axis: usize, index: i32| coords.get::<CUBES>(axis,index as usize);
    let mut best = None;
    let mut distance = 0.0;
    super::super::box_extract::extract::visit(words, dims, |b| {
        let p = std::array::from_fn(|a| clamp(query[a], coordinate(a, b[a]), coordinate(a, b[a + 3])));
        let dx = p[0] - query[0];
        let dy = p[1] - query[1];
        let dz = p[2] - query[2];
        let d = dx * dx + dy * dy + dz * dz;
        if best.is_none() || d < distance { best = Some(p); distance = d; }
    });
    best
}

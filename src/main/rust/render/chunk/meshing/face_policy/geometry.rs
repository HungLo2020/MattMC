use super::Shape;

pub(super) fn valid(s: &Shape) -> bool {
    s.empty == s.boxes.is_empty()
        && s.boxes.len() <= 256
        && (!s.full_identity || s.boxes == [[0., 0., 0., 1., 1., 1.]])
        && s.boxes
            .iter()
            .all(|b| b.iter().all(|v| v.is_finite()) && (0..3).all(|a| b[a] < b[a + 3]))
}
fn contains(s: &Shape, p: [f64; 3]) -> bool {
    s.boxes
        .iter()
        .any(|b| (0..3).all(|a| p[a] >= b[a] && p[a] < b[a + 3]))
}
/// Cached canonical face geometry. Equivalent box lists share the CPU table;
/// full-shape object identity remains a separate semantic flag.
pub(super) fn exposed(a: &Shape, b: &Shape) -> Result<bool, ()> {
    if a.empty {
        return Ok(false);
    }
    if b.empty {
        return Ok(true);
    }
    let axes: [Vec<f64>; 3] = std::array::from_fn(|axis| {
        let mut values: Vec<_> = a
            .boxes
            .iter()
            .chain(&b.boxes)
            .flat_map(|b| [b[axis], b[axis + 3]])
            .collect();
        values.sort_by(f64::total_cmp);
        values.dedup_by(|a, b| (*a - *b).abs() < 1.0e-7);
        values
    });
    if axes
        .iter()
        .try_fold(1usize, |n, a| n.checked_mul(a.len() - 1))
        .filter(|&n| n <= 262144)
        .is_none()
    {
        return Err(());
    }
    for x in axes[0].windows(2) {
        for y in axes[1].windows(2) {
            for z in axes[2].windows(2) {
                let p = [
                    (x[0] + x[1]) * 0.5,
                    (y[0] + y[1]) * 0.5,
                    (z[0] + z[1]) * 0.5,
                ];
                if contains(a, p) && !contains(b, p) {
                    return Ok(true);
                }
            }
        }
    }
    Ok(false)
}

//! Exact OreFeature sphere pruning and ordered rasterization. Do not use FMA,
//! reciprocal multiplication, change strict inequalities, or deduplicate here:
//! the caller's build-height checks occur before its visited-bitset check.
pub(super) fn prune(spheres: &mut [f64]) {
    #[cfg(target_arch = "x86_64")]
    if spheres.len() >= 32 && std::is_x86_feature_detected!("avx2") {
        unsafe {
            super::simd::prune(spheres);
        }
        return;
    }
    prune_scalar(spheres);
}
pub(super) fn prune_scalar(spheres: &mut [f64]) {
    let count = spheres.len() / 4;
    for q in 0..count.saturating_sub(1) {
        let (before, after) = spheres.split_at_mut((q + 1) * 4);
        let current = &mut before[q * 4..];
        if current[3] <= 0.0 {
            continue;
        }
        for other in after.chunks_exact_mut(4) {
            if other[3] <= 0.0 {
                continue;
            }
            let dx = current[0] - other[0];
            let dy = current[1] - other[1];
            let dz = current[2] - other[2];
            let dr = current[3] - other[3];
            if dr * dr > dx * dx + dy * dy + dz * dz {
                if dr > 0.0 {
                    other[3] = -1.0;
                } else {
                    current[3] = -1.0;
                    // Later surviving radii are positive (or NaN), so a dead
                    // current sphere cannot remove any of them.
                    break;
                }
            }
        }
    }
}

#[inline]
pub(super) fn floor(value: f64) -> i32 {
    let truncated = value as i32; // Java narrowing: NaN -> 0; out-of-range values saturate.
    if value < truncated as f64 {
        truncated.wrapping_sub(1)
    } else {
        truncated
    }
}

#[cfg(test)]
pub(super) fn raster_scalar(spheres: &[f64], lower: [i32; 3], output: &mut [i32]) -> usize {
    let mut count = 0;
    for s in spheres.chunks_exact(4) {
        let sphere = <[f64; 4]>::try_from(s).unwrap();
        if !(sphere[3] < 0.0) {
            scan::<false>(sphere, lower, output, &mut count);
        }
    }
    count
}

// Required int count for ordered (x, y, first_z, last_z) runs. Short outputs
// receive only complete spans. No allocation or retained pointers.
pub(super) fn raster(spheres: &[f64], lower: [i32; 3], output: &mut [i32]) -> usize {
    let mut count = 0;
    #[cfg(target_arch = "x86_64")]
    let vector = std::is_x86_feature_detected!("avx2");
    #[cfg(target_arch = "x86_64")]
    if vector
        && !spheres.is_empty()
        && spheres
            .chunks_exact(4)
            .all(|s| s[3] <= 1.0 && s[..3].iter().all(|v| v.abs() <= 1_000_000_000.0))
    {
        let mut groups = spheres.chunks_exact(16);
        for group in &mut groups {
            unsafe {
                super::simd::tiny_batch4(group, lower, output, &mut count);
            }
        }
        let remainder = groups.remainder();
        if !remainder.is_empty() {
            unsafe {
                super::simd::tiny_batch4(remainder, lower, output, &mut count);
            }
        }
        return count;
    }
    for sphere in spheres.chunks_exact(4) {
        let sphere = <[f64; 4]>::try_from(sphere).unwrap();
        if sphere[3] < 0.0 {
            continue;
        }
        #[cfg(target_arch = "x86_64")]
        if sphere[3] > 0.0
            && sphere[3] <= 1.0
            && sphere[..3].iter().all(|v| v.abs() <= 1_000_000_000.0)
            && vector
        {
            unsafe {
                super::simd::tiny(sphere, lower, output, &mut count);
            }
            continue;
        }
        #[cfg(target_arch = "x86_64")]
        if sphere[3] > 1.0
            && sphere[3] <= 7.0
            && sphere[..3].iter().all(|v| v.abs() <= 1_000_000_000.0)
            && vector
        {
            unsafe {
                super::simd::spans(sphere, lower, output, &mut count);
            }
            continue;
        }
        if sphere[3] <= 1.0 {
            scan::<false>(sphere, lower, output, &mut count);
        } else {
            scan::<true>(sphere, lower, output, &mut count);
        }
    }
    count
}

#[inline]
fn scan<const CACHE: bool>(
    sphere: [f64; 4],
    lower: [i32; 3],
    output: &mut [i32],
    count: &mut usize,
) {
    let [cx, cy, cz, r] = sphere;
    let x0 = floor(cx - r).max(lower[0]);
    let x1 = floor(cx + r).max(x0);
    let y0 = floor(cy - r).max(lower[1]);
    let y1 = floor(cy + r).max(y0);
    let z0 = floor(cz - r).max(lower[2]);
    let z1 = floor(cz + r).max(z0);
    let ny = y1 as i64 - y0 as i64 + 1;
    let nz = z1 as i64 - z0 as i64 + 1;
    let mut yy = [0.0; 16];
    let mut zz = [0.0; 16];
    if CACHE && ny <= 16 {
        for i in 0..ny as usize {
            let d = ((y0 as i64 + i as i64) as f64 + 0.5 - cy) / r;
            yy[i] = d * d;
        }
    }
    if CACHE && nz <= 16 {
        for i in 0..nz as usize {
            let d = ((z0 as i64 + i as i64) as f64 + 0.5 - cz) / r;
            zz[i] = d * d;
        }
    }
    for x in x0..=x1 {
        let dx = (x as f64 + 0.5 - cx) / r;
        let xx = dx * dx;
        if !(xx < 1.0) {
            continue;
        }
        for y in y0..=y1 {
            let y2 = if CACHE && ny <= 16 {
                yy[(y as i64 - y0 as i64) as usize]
            } else {
                let d = (y as f64 + 0.5 - cy) / r;
                d * d
            };
            let xy = xx + y2;
            if !(xy < 1.0) {
                continue;
            }
            if CACHE && nz <= 16 {
                let axis = &zz[..nz as usize];
                // Rounded squared distance along one axis decreases then
                // increases. Its strict inside predicate forms one interval.
                // Search its endpoints using the unchanged addition/comparison.
                if let Some(first) = axis.iter().position(|v| xy + v < 1.0) {
                    let last = axis.iter().rposition(|v| xy + v < 1.0).unwrap();
                    emit(output, count, [x, y, z0 + first as i32, z0 + last as i32]);
                }
            } else {
                let mut first = None;
                for z in z0..=z1 {
                    let dz = (z as f64 + 0.5 - cz) / r;
                    if xy + dz * dz < 1.0 {
                        if first.is_none() {
                            first = Some(z);
                        }
                    } else if let Some(start) = first.take() {
                        emit(output, count, [x, y, start, z - 1]);
                    }
                }
                if let Some(start) = first {
                    emit(output, count, [x, y, start, z1]);
                }
            }
        }
    }
}

#[inline]
pub(super) fn emit(output: &mut [i32], count: &mut usize, span: [i32; 4]) {
    if *count + 4 <= output.len() {
        output[*count..*count + 4].copy_from_slice(&span);
    }
    *count += 4;
}

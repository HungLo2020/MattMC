//! Four nearest aquifer centers, including Java's last-visited-wins ties.
#[cfg(target_arch = "x86_64")]
mod avx2;

/// Transposed centers amortize coordinate packing over the neighboring blocks
/// sharing an aquifer grid cell. The padded lanes are never ranked.
pub(super) struct Prepared {
    ids: [i32; 12],
    x: [i32; 16],
    y: [i32; 16],
    z: [i32; 16],
}
impl Prepared {
    pub(super) fn new(centers: &[[i32; 4]; 12]) -> Self {
        let mut result = Self {
            ids: [0; 12],
            x: [0; 16],
            y: [0; 16],
            z: [0; 16],
        };
        for (i, &[id, x, y, z]) in centers.iter().enumerate() {
            result.ids[i] = id;
            result.x[i] = x;
            result.y[i] = y;
            result.z[i] = z;
        }
        result
    }
    pub(super) fn point(&self, x: i32, y: i32, z: i32, out: &mut [i32]) {
        let mut distance = [0; 16];
        #[cfg(target_arch = "x86_64")]
        if is_x86_feature_detected!("avx2") {
            unsafe { avx2::distances(self, x, y, z, &mut distance) };
            rank(self.ids.iter().copied().zip(distance), out);
            return;
        }
        for i in 0..12 {
            let dx = self.x[i].wrapping_sub(x);
            let dy = self.y[i].wrapping_sub(y);
            let dz = self.z[i].wrapping_sub(z);
            distance[i] = dx
                .wrapping_mul(dx)
                .wrapping_add(dy.wrapping_mul(dy))
                .wrapping_add(dz.wrapping_mul(dz));
        }
        rank(self.ids.iter().copied().zip(distance), out);
    }
}

pub(super) fn point(x: i32, y: i32, z: i32, centers: &[[i32; 4]], out: &mut [i32]) {
    rank(
        centers.iter().map(|&[id, cx, cy, cz]| {
            let dx = cx.wrapping_sub(x);
            let dy = cy.wrapping_sub(y);
            let dz = cz.wrapping_sub(z);
            let distance = dx
                .wrapping_mul(dx)
                .wrapping_add(dy.wrapping_mul(dy))
                .wrapping_add(dz.wrapping_mul(dz));
            (id, distance)
        }),
        out,
    );
}

#[inline]
fn rank(candidates: impl Iterator<Item = (i32, i32)>, out: &mut [i32]) {
    let mut ids = [0; 4];
    let mut distances = [i32::MAX; 4];
    for (id, distance) in candidates {
        if distances[0] >= distance {
            distances[3] = distances[2];
            distances[2] = distances[1];
            distances[1] = distances[0];
            distances[0] = distance;
            ids[3] = ids[2];
            ids[2] = ids[1];
            ids[1] = ids[0];
            ids[0] = id;
        } else if distances[1] >= distance {
            distances[3] = distances[2];
            distances[2] = distances[1];
            distances[1] = distance;
            ids[3] = ids[2];
            ids[2] = ids[1];
            ids[1] = id;
        } else if distances[2] >= distance {
            distances[3] = distances[2];
            distances[2] = distance;
            ids[3] = ids[2];
            ids[2] = id;
        } else if distances[3] >= distance {
            distances[3] = distance;
            ids[3] = id;
        }
    }
    out[..4].copy_from_slice(&ids);
    out[4..8].copy_from_slice(&distances);
}

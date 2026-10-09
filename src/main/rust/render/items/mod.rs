//! Authored item-layer transforms. Immutable CPU owners are consumed directly
//! by native GUI decoding; compatibility consumers may read scoped CPU views.
mod ffi;
#[cfg(test)]
mod tests;

#[repr(C)]
#[derive(Clone, Copy, Debug)]
pub(crate) struct Pose {
    pub model: [f32; 16],
    pub normal: [f32; 9],
    pub trusted_normals: u32,
}
#[repr(C)]
pub(crate) struct Owner {
    pub poses: [Pose; 2],
}
impl Owner {
    pub(crate) fn new(authored: [f32; 9], no_transform: bool) -> Option<Self> {
        let poses = [
            pose(authored, false, no_transform),
            pose(authored, true, no_transform),
        ];
        poses
            .iter()
            .all(|p| p.model.iter().chain(&p.normal).all(|v| v.is_finite()))
            .then_some(Self { poses })
    }
}
fn sin(angle: f32) -> f32 {
    f64::from(angle).sin() as f32
}
fn cos_from_sin(s: f32, angle: f32) -> f32 {
    let c = (1.0 - s * s).sqrt();
    let a = angle + std::f32::consts::FRAC_PI_2;
    let pi2 = std::f32::consts::PI * 2.0;
    let mut b = a - (a / pi2) as i32 as f32 * pi2;
    if b < 0.0 {
        b = pi2 + b;
    }
    if b >= std::f32::consts::PI { -c } else { c }
}
fn pose(a: [f32; 9], left: bool, no_transform: bool) -> Pose {
    if no_transform {
        return Pose {
            model: [
                1., 0., 0., 0., 0., 1., 0., 0., 0., 0., 1., 0., -0.5, -0.5, -0.5, 1.,
            ],
            normal: [1., 0., 0., 0., 1., 0., 0., 0., 1.],
            trusted_normals: 1,
        };
    }
    let degrees = (std::f64::consts::PI / 180.0) as f32;
    let hx = (a[0] * degrees) * 0.5;
    let hy = ((if left { -a[1] } else { a[1] }) * degrees) * 0.5;
    let hz = ((if left { -a[2] } else { a[2] }) * degrees) * 0.5;
    let (sx, sy, sz) = (sin(hx), sin(hy), sin(hz));
    let (cx, cy, cz) = (
        cos_from_sin(sx, hx),
        cos_from_sin(sy, hy),
        cos_from_sin(sz, hz),
    );
    let (cycz, sysz, sycz, cysz) = (cy * cz, sy * sz, sy * cz, cy * sz);
    let (w, x, y, z) = (
        cx * cycz - sx * sysz,
        sx * cycz + cx * sysz,
        cx * sycz - sx * cysz,
        cx * cysz + sx * sycz,
    );
    let (w2, x2, y2, z2) = (w * w, x * x, y * y, z * z);
    let (zw, xy, xz, yw, yz, xw) = (z * w, x * y, x * z, y * w, y * z, x * w);
    let (dzw, dxy, dxz, dyw, dyz, dxw) = (zw + zw, xy + xy, xz + xz, yw + yw, yz + yz, xw + xw);
    let r = [
        w2 + x2 - z2 - y2,
        dxy + dzw,
        dxz - dyw,
        -dzw + dxy,
        y2 - z2 + w2 - x2,
        dyz + dxw,
        dyw + dxz,
        dyz - dxw,
        z2 - y2 - x2 + w2,
    ];
    let mut model = [0.; 16];
    for col in 0..3 {
        for row in 0..3 {
            model[col * 4 + row] = r[col * 3 + row] * a[6 + col];
        }
        // JOML also scales the affine zero row, preserving signed zeros.
        model[col * 4 + 3] = 0.0 * a[6 + col];
    }
    let t = [if left { -a[3] } else { a[3] }, a[4], a[5]];
    for row in 0..4 {
        // JOML translateGeneric uses this nested evaluation order without FMA.
        model[12 + row] = model[row] * -0.5
            + (model[4 + row] * -0.5
                + (model[8 + row] * -0.5 + if row == 3 { 1. } else { t[row] }));
    }
    let mut normal = [0.; 9];
    let uniform = a[6].abs() == a[7].abs() && a[6].abs() == a[8].abs();
    for col in 0..3 {
        let n = if uniform {
            if a[6] < 0. || a[7] < 0. || a[8] < 0. {
                if a[6 + col] == 0. {
                    a[6 + col]
                } else {
                    a[6 + col].signum()
                }
            } else {
                1.
            }
        } else {
            1.0 / a[6 + col]
        };
        for row in 0..3 {
            // Matrix3f.rotate multiplies even an identity normal pose.
            let v = (if row == 0 { 1. } else { 0. }) * r[col * 3]
                + (if row == 1 { 1. } else { 0. }) * r[col * 3 + 1]
                + (if row == 2 { 1. } else { 0. }) * r[col * 3 + 2];
            normal[col * 3 + row] = v * n;
        }
    }
    Pose {
        model,
        normal,
        trusted_normals: uniform as u32,
    }
}

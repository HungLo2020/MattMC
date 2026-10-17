//! Native world-item CPU lowering. The two operation orders are distinct:
//! world items apply authored TRS to a parent; hands compose a prepared local pose.
const AFFINE: u32 = 2;
const IDENTITY: u32 = 4;
const TRANSLATION: u32 = 8;
const ORTHONORMAL: u32 = 16;
type Matrix = [f32; 16];
type Normal = [f32; 9];
#[derive(Clone, Copy)]
pub(crate) struct ResolvedPose {
    pub model: Matrix,
    pub normal: Normal,
    pub trusted: bool,
}
#[derive(Clone, Copy)]
pub(crate) struct AuthoredTransform {
    translation: [f32; 3],
    scale: [f32; 3],
    rotation: [f32; 9],
    none: bool,
}
impl AuthoredTransform {
    pub(crate) fn new(a: [f32; 9], left: bool, none: bool) -> Self {
        let degrees = (std::f64::consts::PI / 180.0) as f32;
        let angles = [
            a[0] * degrees * 0.5,
            (if left { -a[1] } else { a[1] }) * degrees * 0.5,
            (if left { -a[2] } else { a[2] }) * degrees * 0.5,
        ];
        let s = angles.map(|v| (v as f64).sin() as f32);
        let c = std::array::from_fn::<_, 3, _>(|i| cos_from_sin(s[i], angles[i]));
        let (cycz, sysz, sycz, cysz) = (c[1] * c[2], s[1] * s[2], s[1] * c[2], c[1] * s[2]);
        let (w, x, y, z) = (
            c[0] * cycz - s[0] * sysz,
            s[0] * cycz + c[0] * sysz,
            c[0] * sycz - s[0] * cysz,
            c[0] * cysz + s[0] * sycz,
        );
        let (w2, x2, y2, z2) = (w * w, x * x, y * y, z * z);
        let (zw, xy, xz, yw, yz, xw) = (z * w, x * y, x * z, y * w, y * z, x * w);
        let (dzw, dxy, dxz, dyw, dyz, dxw) = (zw + zw, xy + xy, xz + xz, yw + yw, yz + yz, xw + xw);
        Self {
            translation: [if left { -a[3] } else { a[3] }, a[4], a[5]],
            scale: [a[6], a[7], a[8]],
            rotation: [
                w2 + x2 - z2 - y2,
                dxy + dzw,
                dxz - dyw,
                -dzw + dxy,
                y2 - z2 + w2 - x2,
                dyz + dxw,
                dyw + dxz,
                dyz - dxw,
                z2 - y2 - x2 + w2,
            ],
            none,
        }
    }
    pub(crate) fn apply(&self, outer: ResolvedPose, mut properties: u32) -> Option<ResolvedPose> {
        if properties & AFFINE == 0 || properties & !31 != 0 {
            return None;
        }
        let mut p = outer;
        if self.none {
            translate(&mut p.model, &mut properties, [-0.5; 3]);
            return Some(p);
        }
        translate(&mut p.model, &mut properties, self.translation);
        if properties & (IDENTITY | TRANSLATION) != 0 {
            let old = p.model;
            p.model = [0.; 16];
            p.model[15] = if properties & IDENTITY != 0 {
                1.
            } else {
                old[15]
            };
            for c in 0..3 {
                for r in 0..3 {
                    p.model[c * 4 + r] = self.rotation[c * 3 + r];
                }
            }
            if properties & IDENTITY == 0 {
                p.model[12..15].copy_from_slice(&old[12..15]);
            }
        } else {
            let old = p.model;
            for c in 0..3 {
                for r in 0..3 {
                    p.model[c * 4 + r] = (old[r] * self.rotation[c * 3]
                        + old[4 + r] * self.rotation[c * 3 + 1])
                        + old[8 + r] * self.rotation[c * 3 + 2];
                }
                p.model[c * 4 + 3] = 0.;
            }
        }
        properties &= !(1 | IDENTITY | TRANSLATION);
        p.normal = normal_mul(p.normal, self.rotation);
        for c in 0..3 {
            for r in 0..4 {
                p.model[c * 4 + r] *= self.scale[c];
            }
        }
        let uniform = self.scale[0].abs() == self.scale[1].abs()
            && self.scale[0].abs() == self.scale[2].abs();
        if uniform {
            if self.scale.iter().any(|v| *v < 0.) {
                for c in 0..3 {
                    let sign = if self.scale[c] == 0. {
                        self.scale[c]
                    } else {
                        self.scale[c].signum()
                    };
                    for r in 0..3 {
                        p.normal[c * 3 + r] *= sign;
                    }
                }
            }
        } else {
            for c in 0..3 {
                let inverse = 1. / self.scale[c];
                for r in 0..3 {
                    p.normal[c * 3 + r] *= inverse;
                }
            }
            p.trusted = false;
        }
        translate(&mut p.model, &mut properties, [-0.5; 3]);
        Some(p)
    }
}
fn cos_from_sin(s: f32, angle: f32) -> f32 {
    let c = (1. - s * s).sqrt();
    let a = angle + std::f32::consts::FRAC_PI_2;
    let pi2 = std::f32::consts::PI * 2.;
    let mut b = a - (a / pi2) as i32 as f32 * pi2;
    if b < 0. {
        b = pi2 + b;
    }
    if b >= std::f32::consts::PI {
        -c
    } else {
        c
    }
}
fn translate(m: &mut Matrix, props: &mut u32, t: [f32; 3]) {
    if *props & IDENTITY != 0 {
        *m = [
            1., 0., 0., 0., 0., 1., 0., 0., 0., 0., 1., 0., t[0], t[1], t[2], 1.,
        ];
        *props = AFFINE | TRANSLATION | ORTHONORMAL;
    } else {
        for r in 0..4 {
            m[12 + r] = m[r] * t[0] + (m[4 + r] * t[1] + (m[8 + r] * t[2] + m[12 + r]));
        }
        *props &= !(1 | IDENTITY);
    }
}
fn normal_mul(l: Normal, r: Normal) -> Normal {
    std::array::from_fn(|i| {
        let c = i / 3;
        let row = i % 3;
        (l[row] * r[c * 3] + l[3 + row] * r[c * 3 + 1]) + l[6 + row] * r[c * 3 + 2]
    })
}
pub(crate) fn compose(
    outer: ResolvedPose,
    properties: u32,
    local: ResolvedPose,
) -> Option<ResolvedPose> {
    if properties & AFFINE == 0 || properties & !31 != 0 {
        return None;
    }
    let r = local.model;
    let l = outer.model;
    let right_identity = r[0] == 1.
        && r[5] == 1.
        && r[10] == 1.
        && r[15] == 1.
        && [1, 2, 3, 4, 6, 7, 8, 9, 11, 12, 13, 14]
            .iter()
            .all(|i| r[*i] == 0.);
    let model = if properties & IDENTITY != 0 {
        r
    } else if right_identity {
        l
    } else if properties & TRANSLATION != 0 {
        let mut m = r;
        for row in 0..3 {
            m[12 + row] = r[12 + row] + l[12 + row];
        }
        for c in 0..4 {
            m[c * 4 + 3] = l[c * 4 + 3];
        }
        m
    } else {
        let mut m = [0.; 16];
        for c in 0..3 {
            for row in 0..3 {
                m[c * 4 + row] =
                    l[row] * r[c * 4] + (l[4 + row] * r[c * 4 + 1] + l[8 + row] * r[c * 4 + 2]);
            }
            m[c * 4 + 3] = l[c * 4 + 3];
        }
        for row in 0..3 {
            m[12 + row] =
                l[row] * r[12] + (l[4 + row] * r[13] + (l[8 + row] * r[14] + l[12 + row]));
        }
        m[15] = l[15];
        m
    };
    Some(ResolvedPose {
        model,
        normal: std::array::from_fn(|i| {
            let c = i / 3;
            let row = i % 3;
            outer.normal[row] * local.normal[c * 3]
                + (outer.normal[3 + row] * local.normal[c * 3 + 1]
                    + outer.normal[6 + row] * local.normal[c * 3 + 2])
        }),
        trusted: outer.trusted && local.trusted,
    })
}

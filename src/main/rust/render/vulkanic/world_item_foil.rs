//! Copied model-space decal semantics for world and first-person baked items.
//! Explicit world instances carry these semantics into native draw lowering.
//! Normal gameplay admission stays closed pending paired capture verification.
use super::error::{GalError, GalResult};
use super::special_item_foil::{FoilDisplayContext, SpecialFoilProjection};

#[derive(Clone, Copy, Debug, PartialEq)]
pub struct WorldDecalFoilProjection {
    pub model_pose: [f32; 16],
    pub normal_pose: [f32; 9],
    pub first_person: bool,
    /// Original PoseStack semantic flag, not inferred from the position matrix.
    pub trusted_normals: bool,
}

impl WorldDecalFoilProjection {
    /// Canonical transport decoding: 0 absent, 1 world, 2 first person.
    /// Normal mode is 0 trusted or 1 normalize before signed-byte emission.
    pub(super) fn decode(
        mode: u32,
        normal_mode: u32,
        model_pose: [f32; 16],
        normal_pose: [f32; 9],
    ) -> GalResult<Option<Self>> {
        if mode == 0
            && normal_mode == 0
            && model_pose
                .iter()
                .chain(normal_pose.iter())
                .all(|v| v.to_bits() == 0)
        {
            return Ok(None);
        }
        if !(1..=2).contains(&mode) || normal_mode > 1 {
            return Err(GalError::invalid_argument(
                "invalid world decal foil semantics",
            ));
        }
        let value = Self {
            model_pose,
            normal_pose,
            first_person: mode == 2,
            trusted_normals: normal_mode == 0,
        };
        value.prepare()?;
        Ok(Some(value))
    }

    pub(super) fn prepare(self) -> GalResult<PreparedWorldDecalFoil> {
        let projection = SpecialFoilProjection::new(
            self.model_pose,
            self.normal_pose,
            if self.first_person {
                FoilDisplayContext::FirstPerson
            } else {
                FoilDisplayContext::World
            },
        )?;
        Ok(PreparedWorldDecalFoil {
            semantics: self,
            projection,
        })
    }
}

pub(super) struct PreparedWorldDecalFoil {
    semantics: WorldDecalFoilProjection,
    projection: SpecialFoilProjection,
}

impl PreparedWorldDecalFoil {
    /// BakedModelEncoder emits positions and packed transformed normals before
    /// SheetedDecalTextureGenerator selects the projection face. In particular,
    /// cancelling the poses algebraically would omit normal quantization.
    pub(super) fn texture_uv(
        &self,
        local_position: [f32; 3],
        local_normal: u32,
    ) -> GalResult<[f32; 2]> {
        if local_position.iter().any(|v| !v.is_finite()) {
            return Err(GalError::invalid_argument("non-finite world decal vertex"));
        }
        let [x, y, z] = local_position;
        let m = self.semantics.model_pose;
        let emitted_position =
            std::array::from_fn(|r| m[r] * x + (m[4 + r] * y + (m[8 + r] * z + m[12 + r])));
        let [x, y, z] = unpack_normal(local_normal);
        let n = self.semantics.normal_pose;
        let mut emitted_normal: [f32; 3] =
            std::array::from_fn(|r| n[r] * x + (n[3 + r] * y + n[6 + r] * z));
        if emitted_normal.iter().any(|v| !v.is_finite()) {
            return Err(GalError::invalid_argument("world decal normal overflow"));
        }
        if !self.semantics.trusted_normals {
            let [x, y, z] = emitted_normal;
            let length_squared = x.mul_add(x, y.mul_add(y, z * z));
            if !length_squared.is_finite() {
                return Err(GalError::invalid_argument(
                    "world decal normal length overflow",
                ));
            }
            // Java's zero normalization produces NaNs, which NormI8 casts to
            // zero bytes. Preserve the resulting NORTH face without NaNs.
            emitted_normal = if length_squared == 0.0 {
                if emitted_normal != [0.0; 3] {
                    return Err(GalError::invalid_argument(
                        "world decal normal length underflow",
                    ));
                }
                [0.0; 3]
            } else {
                emitted_normal.map(|v| v * (1.0 / length_squared.sqrt()))
            };
        }
        // NormI8 truncates toward zero; the world's general rounded-normal
        // helper is not interchangeable with this source emission contract.
        let emitted_normal =
            emitted_normal.map(|v| ((v.clamp(-1.0, 1.0) * 127.0) as i8) as f32 * (1.0 / 127.0));
        self.projection.texture_uv(emitted_position, emitted_normal)
    }
}

fn unpack_normal(value: u32) -> [f32; 3] {
    std::array::from_fn(|i| ((value >> (i * 8)) as u8 as i8) as f32 * (1.0 / 127.0))
}

#[cfg(test)]
mod tests {
    use super::*;
    const MODEL: [f32; 16] = [
        2., 0., 0., 0., 0., -3., 0., 0., 0., 0., 4., 0., 10., 20., 30., 1.,
    ];
    const NORMAL: [f32; 9] = [1., 0., 0., 0., 0.25, 0., 0., 0., 2.];

    #[test]
    fn world_decal_transport_requires_canonical_absence_and_independent_valid_poses() {
        assert!(WorldDecalFoilProjection::decode(0, 0, [0.; 16], [0.; 9])
            .unwrap()
            .is_none());
        assert!(WorldDecalFoilProjection::decode(0, 0, MODEL, NORMAL).is_err());
        let mut signed_zero = [0.; 9];
        signed_zero[8] = -0.0;
        assert!(WorldDecalFoilProjection::decode(0, 0, [0.; 16], signed_zero).is_err());
        for (mode, normals) in [(3, 0), (1, 2), (0, 1)] {
            assert!(WorldDecalFoilProjection::decode(mode, normals, MODEL, NORMAL).is_err());
        }
        assert!(WorldDecalFoilProjection::decode(1, 0, [0.; 16], NORMAL).is_err());
        assert!(WorldDecalFoilProjection::decode(1, 0, MODEL, [0.; 9]).is_err());
        let mut bad = NORMAL;
        bad[1] = f32::NAN;
        assert!(WorldDecalFoilProjection::decode(2, 1, MODEL, bad).is_err());
    }

    #[test]
    fn world_decal_rejects_nonzero_normal_underflow_instead_of_selecting_zero_face() {
        let tiny = [1e-30, 0., 0., 0., 1e-30, 0., 0., 0., 1e-30];
        let prepared = WorldDecalFoilProjection::decode(1, 1, MODEL, tiny)
            .unwrap()
            .unwrap()
            .prepare()
            .unwrap();
        assert!(prepared.texture_uv([2., 3., 5.], 0x007f0000).is_err());
        assert!(prepared.texture_uv([2., 3., 5.], 0).is_ok());
    }

    #[test]
    fn world_decal_bulk_emission_matches_java_reference_vectors() {
        // Actual MatrixHelper + bulk SheetedDecalTextureGenerator vectors in
        // SpecialItemFoilProjectionReferenceTest, independent of native math.
        for mode in [1, 2] {
            for normal_mode in [0, 1] {
                let prepared = WorldDecalFoilProjection::decode(mode, normal_mode, MODEL, NORMAL)
                    .unwrap()
                    .unwrap()
                    .prepare()
                    .unwrap();
                for (normal, axes) in [
                    (
                        0x003f4040,
                        if normal_mode == 0 {
                            [2., 5.]
                        } else {
                            [5., -3.]
                        },
                    ),
                    (0, [-2., -3.]),
                    (0x00007f00, [2., 5.]),
                    (0x007f0000, [2., -3.]),
                ] {
                    let scale = if mode == 2 { 0.75 } else { 1. };
                    let expected = axes.map(|v| v / scale / 128.);
                    let actual = prepared.texture_uv([2., 3., 5.], normal).unwrap();
                    for i in 0..2 {
                        assert!((actual[i]-expected[i]).abs()<1e-6,
                        "mode={mode} normal_mode={normal_mode} normal={normal:x}: {actual:?} != {expected:?}");
                    }
                }
                assert!(prepared.texture_uv([f32::NAN, 0., 0.], 0).is_err());
                assert!(prepared.texture_uv([f32::MAX, 0., 0.], 0).is_err());
            }
        }
    }
}

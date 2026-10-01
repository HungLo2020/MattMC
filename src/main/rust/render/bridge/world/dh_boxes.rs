//! Distant Horizons generic boxes lowered into shaded world quads.

use super::*;

pub(super) const DH_GENERIC_BOX_FACE_COUNT: usize = 6;

pub(super) fn decode_dh_generic_box_semantics(
    records: &[FfiWorldDistantHorizonsGenericBoxRecord],
) -> GalResult<Vec<WorldDistantHorizonsGenericBoxRequest>> {
    records
        .iter()
        .map(|record| {
            validate_item_size::<FfiWorldDistantHorizonsGenericBoxRecord>(
                record.byte_size,
                "DH generic box",
            )?;
            // Bit 0: SSAO request; bits 8-15: DH block material index;
            // bits 16-31: the box's render-group ordinal this frame.
            if record.flags & 0xfe != 0
                || record
                    .min
                    .iter()
                    .chain(record.max.iter())
                    .chain(record.shading.iter())
                    .any(|value| !value.is_finite())
                || record
                    .min
                    .iter()
                    .zip(record.max.iter())
                    .any(|(min, max)| min > max)
            {
                return Err(GalError::invalid_argument(
                    "DH generic box has invalid flags, bounds, or shading",
                ));
            }
            Ok(WorldDistantHorizonsGenericBoxRequest {
                min: record.min,
                max: record.max,
                color_argb: record.color_argb,
                packed_light: record.packed_light,
                shading: record.shading,
                ssao_enabled: record.flags & 1 != 0,
                material: (record.flags >> 8) & 0xff,
                group: record.flags >> 16,
            })
        })
        .collect()
}

#[cfg(test)]
pub(super) fn shade_dh_generic_box_color(color: u32, shading: f32) -> u32 {
    let shade = |component: u32| -> u32 {
        ((component as f32 * shading).round() as i32).clamp(0, 255) as u32
    };
    (color & 0xff00_0000)
        | (shade((color >> 16) & 0xff) << 16)
        | (shade((color >> 8) & 0xff) << 8)
        | shade(color & 0xff)
}

#[cfg(test)]
pub(super) fn decode_dh_generic_boxes(
    records: &[FfiWorldDistantHorizonsGenericBoxRecord],
    viewport_width: u32,
    viewport_height: u32,
) -> GalResult<Vec<WorldMaterialQuadRequest>> {
    let mut quads = Vec::with_capacity(records.len().saturating_mul(DH_GENERIC_BOX_FACE_COUNT));
    for record in records {
        validate_item_size::<FfiWorldDistantHorizonsGenericBoxRecord>(
            record.byte_size,
            "DH generic box",
        )?;
        if record.flags & !1 != 0 {
            return Err(GalError::invalid_argument(
                "DH generic box has unknown semantic flags",
            ));
        }
        if record
            .min
            .iter()
            .chain(record.max.iter())
            .chain(record.shading.iter())
            .any(|value| !value.is_finite())
            || record.min[0] > record.max[0]
            || record.min[1] > record.max[1]
            || record.min[2] > record.max[2]
        {
            return Err(GalError::invalid_argument(
                "DH generic box bounds and shading must be finite and ordered",
            ));
        }
        let [min_x, min_y, min_z] = record.min;
        let [max_x, max_y, max_z] = record.max;
        let faces = [
            // The order and winding match DH's shared indexed cube.
            (
                [
                    [max_x, max_y, min_z],
                    [max_x, min_y, min_z],
                    [min_x, min_y, min_z],
                    [min_x, max_y, min_z],
                ],
                record.shading[0],
            ),
            (
                [
                    [max_x, min_y, max_z],
                    [max_x, max_y, max_z],
                    [min_x, max_y, max_z],
                    [min_x, min_y, max_z],
                ],
                record.shading[1],
            ),
            (
                [
                    [min_x, max_y, min_z],
                    [min_x, min_y, min_z],
                    [min_x, min_y, max_z],
                    [min_x, max_y, max_z],
                ],
                record.shading[3],
            ),
            (
                [
                    [max_x, max_y, min_z],
                    [max_x, max_y, max_z],
                    [max_x, min_y, max_z],
                    [max_x, min_y, min_z],
                ],
                record.shading[2],
            ),
            (
                [
                    [min_x, min_y, min_z],
                    [max_x, min_y, min_z],
                    [max_x, min_y, max_z],
                    [min_x, min_y, max_z],
                ],
                record.shading[5],
            ),
            (
                [
                    [min_x, max_y, max_z],
                    [max_x, max_y, max_z],
                    [max_x, max_y, min_z],
                    [min_x, max_y, min_z],
                ],
                record.shading[4],
            ),
        ];
        for (vertices, shading) in faces {
            let color = shade_dh_generic_box_color(record.color_argb, shading);
            let translucent = color >> 24 != 0xff;
            quads.push(WorldMaterialQuadRequest {
                stratum: if record.flags & 1 != 0 {
                    WORLD_STRATUM_DH_GENERIC_SSAO
                } else {
                    WORLD_STRATUM_DH_GENERIC
                },
                material_id: if translucent {
                    WORLD_MATERIAL_ID_TRANSLUCENT_TEXTURED
                } else {
                    WORLD_MATERIAL_ID_OPAQUE_TEXTURED
                },
                texture_id: WORLD_MATERIAL_TEXTURE_GENERATED_WHITE,
                material_mode: if translucent {
                    WORLD_MATERIAL_MODE_TRANSLUCENT
                } else {
                    WORLD_MATERIAL_MODE_OPAQUE
                },
                depth_policy: WORLD_DEPTH_POLICY_TEST_WRITE,
                cull_policy: WORLD_CULL_BACK,
                topology: WORLD_TOPOLOGY_TRIANGLES,
                winding: WORLD_WINDING_CCW,
                color_argb: color,
                vertices,
                uvs: [[0.0, 0.0], [1.0, 0.0], [1.0, 1.0], [0.0, 1.0]],
                viewport_width,
                viewport_height,
                source_program: WORLD_MATERIAL_SOURCE_TEXTURED,
                source_uv_space: WORLD_MATERIAL_SOURCE_UV_LOCAL_TEXTURE,
                source_color_argb: color,
                packed_light: record.packed_light,
                vertex_color_argb: [color; 4],
                vertex_packed_light: [record.packed_light; 4],
                block_entity_id: -1,
            });
        }
    }
    Ok(quads)
}

#[cfg(test)]
mod dh_generic_box_tests {
    use crate::render::bridge::world::*;

    fn record() -> FfiWorldDistantHorizonsGenericBoxRecord {
        FfiWorldDistantHorizonsGenericBoxRecord {
            byte_size: std::mem::size_of::<FfiWorldDistantHorizonsGenericBoxRecord>() as u32,
            flags: 0,
            min: [-1.0, -2.0, -3.0],
            max: [4.0, 5.0, 6.0],
            color_argb: 0x80a0_b0c0,
            packed_light: 0x00f0_00f0,
            shading: [0.5, 0.6, 0.7, 0.8, 1.0, 0.4],
        }
    }

    #[test]
    fn compact_box_expands_to_six_ordered_cached_quad_instances() {
        let quads = decode_dh_generic_boxes(&[record()], 854, 480).expect("box semantics");
        assert_eq!(6, quads.len());
        assert_eq!(0x8050_5860, quads[0].color_argb);
        assert_eq!([4.0, 5.0, -3.0], quads[0].vertices[0]);
        assert_eq!([-1.0, -2.0, 6.0], quads[2].vertices[2]);
        assert_eq!(WORLD_MATERIAL_TEXTURE_GENERATED_WHITE, quads[5].texture_id);
        assert_eq!(0x00f0_00f0, quads[5].packed_light);
        assert!(quads
            .iter()
            .all(|quad| quad.stratum == WORLD_STRATUM_DH_GENERIC));
    }

    #[test]
    fn compact_box_retains_ssao_phase_semantics() {
        let mut box_record = record();
        box_record.flags = 1;
        let quads = decode_dh_generic_boxes(&[box_record], 854, 480).expect("SSAO box semantics");
        assert!(quads
            .iter()
            .all(|quad| quad.stratum == WORLD_STRATUM_DH_GENERIC_SSAO));
    }
}

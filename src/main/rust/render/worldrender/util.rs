//! Small math, packing, alignment and barrier helpers.

use super::*;

/// Column-major composition for copied source matrices. This stays in the
/// semantic frontend because it derives a shader input; neither backend nor
/// GAL needs to know how a shader pack defines its camera-relative volume.
pub(super) fn multiply_column_major_mat4(left: [f32; 16], right: [f32; 16]) -> [f32; 16] {
    std::array::from_fn(|index| {
        let row = index % 4;
        let column = index / 4;
        (0..4)
            .map(|inner| left[inner * 4 + row] * right[column * 4 + inner])
            .sum()
    })
}

pub(super) fn transform_column_major_vec4(matrix: [f32; 16], vector: [f32; 4]) -> [f32; 4] {
    std::array::from_fn(|row| {
        (0..4)
            .map(|column| matrix[column * 4 + row] * vector[column])
            .sum()
    })
}

pub(super) fn unpack_normal_i8(packed: u32) -> [f32; 3] {
    [
        unpack_normal_i8_component(packed, 0),
        unpack_normal_i8_component(packed, 8),
        unpack_normal_i8_component(packed, 16),
    ]
}

pub(super) fn pack_normal_i8(normal: [f32; 3]) -> u32 {
    let component = |value: f32| -> u32 {
        let signed = (value.clamp(-1.0, 1.0) * 127.0).round() as i8;
        signed as u8 as u32
    };
    component(normal[0]) | component(normal[1]) << 8 | component(normal[2]) << 16
}

pub(super) fn unpack_normal_i8_component(packed: u32, shift: u32) -> f32 {
    let byte = ((packed >> shift) & 0xff) as u8 as i8;
    (byte as f32 / 127.0).clamp(-1.0, 1.0)
}

pub(super) fn baked_light_factor(packed_light: u32) -> f32 {
    if packed_light == 0 {
        return 1.0;
    }
    let block = ((packed_light >> 4) & 0xf) as f32 / 15.0;
    let sky = ((packed_light >> 20) & 0xf) as f32 / 15.0;
    (0.08 + block.max(sky) * 0.92).clamp(0.0, 1.0)
}

pub(super) fn packed_light_channels(packed_light: u32) -> [f32; 2] {
    [
        (packed_light & 0xff) as f32 / 240.0,
        ((packed_light >> 16) & 0xff) as f32 / 240.0,
    ]
}

pub(super) fn shadow_light_view_projection_matrix() -> [f32; 16] {
    let scale = 1.0 / 64.0;
    [
        scale, 0.0, 0.0, 0.0, 0.0, scale, 0.0, 0.0, 0.0, 0.0, scale, 0.0, 0.0, 0.0, 0.0, 1.0,
    ]
}

pub(super) fn argb_to_rgba(argb: u32) -> [f32; 4] {
    let a = ((argb >> 24) & 0xff) as f32 / 255.0;
    let r = ((argb >> 16) & 0xff) as f32 / 255.0;
    let g = ((argb >> 8) & 0xff) as f32 / 255.0;
    let b = (argb & 0xff) as f32 / 255.0;
    [r, g, b, a]
}

pub(super) fn terrain_program_for_mode(
    material_mode: u32,
    g_buffer: bool,
) -> GalResult<TerrainMaterialProgram> {
    match (material_mode, g_buffer) {
        (WORLD_MATERIAL_MODE_TRANSLUCENT_CUTOUT, _) => {
            Ok(minimal_direct_model_translucent_cutout_program())
        }
        (WORLD_MATERIAL_MODE_OPAQUE, true) => Ok(minimal_terrain_solid_program()),
        (WORLD_MATERIAL_MODE_CUTOUT, true) => Ok(minimal_terrain_cutout_program()),
        // The Fabulous translucent phase targets DeferredLitColor directly.
        // It has one color attachment, so it must use the forward material
        // program that resolves the copied vanilla fog before alpha blending;
        // the four-output G-buffer program has no normal target in this pass
        // through which to preserve its deferred fog factor.
        (WORLD_MATERIAL_MODE_TRANSLUCENT, true) => Ok(minimal_direct_terrain_translucent_program()),
        (WORLD_MATERIAL_MODE_GLINT, true) => Ok(minimal_direct_terrain_cutout_program()),
        (WORLD_MATERIAL_MODE_OPAQUE, false) => Ok(minimal_direct_terrain_solid_program()),
        (WORLD_MATERIAL_MODE_CUTOUT, false) => Ok(minimal_direct_terrain_cutout_program()),
        (WORLD_MATERIAL_MODE_TRANSLUCENT, false) => {
            Ok(minimal_direct_terrain_translucent_program())
        }
        (WORLD_MATERIAL_MODE_GLINT, false) => Ok(minimal_direct_terrain_cutout_program()),
        // Optical mask/test sections use the direct mesh ABI but are lowered
        // only by the private first-person target. The test role samples the
        // ordinary cutout program; the write role supplies a zero-alpha
        // fragment and explicit GAL stencil replace state below.
        (WORLD_MATERIAL_MODE_OPTICAL_STENCIL_WRITE, false) => {
            Ok(minimal_optical_stencil_write_program())
        }
        (WORLD_MATERIAL_MODE_OPTICAL_STENCIL_TEST, false) => {
            Ok(minimal_direct_terrain_cutout_program())
        }
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown world mesh material mode {material_mode}"),
        )),
    }
}

pub(super) fn push_f32(out: &mut Vec<u8>, value: f32) {
    out.extend_from_slice(&value.to_ne_bytes());
}

pub(super) fn push_u32(out: &mut Vec<u8>, value: u32) {
    out.extend_from_slice(&value.to_ne_bytes());
}

pub(super) fn align_up_u64(value: u64, alignment: u64) -> GalResult<u64> {
    if alignment == 0 {
        return Err(GalError::invalid_argument("alignment must be non-zero"));
    }
    let mask = alignment - 1;
    if alignment & mask != 0 {
        return Err(GalError::invalid_argument(
            "alignment must be a power of two",
        ));
    }
    value
        .checked_add(mask)
        .map(|value| value & !mask)
        .ok_or_else(|| GalError::invalid_argument("aligned byte count overflow"))
}

pub(super) fn align_up_multiple_u64(value: u64, alignment: u64) -> GalResult<u64> {
    if alignment == 0 {
        return Err(GalError::invalid_argument("alignment cannot be zero"));
    }
    let remainder = value % alignment;
    if remainder == 0 {
        Ok(value)
    } else {
        value
            .checked_add(alignment - remainder)
            .ok_or_else(|| GalError::invalid_argument("aligned byte range overflow"))
    }
}

pub(super) fn buffer_barrier(
    resource: Handle,
    before: TextureUsageState,
    after: TextureUsageState,
) -> ResourceBarrier {
    ResourceBarrier {
        resource,
        subresources: None,
        before,
        after,
        src_queue: QueueClass::Graphics,
        dst_queue: QueueClass::Graphics,
    }
}

pub(super) fn texture_barrier(
    resource: Handle,
    before: TextureUsageState,
    after: TextureUsageState,
) -> ResourceBarrier {
    ResourceBarrier {
        resource,
        subresources: None,
        before,
        after,
        src_queue: QueueClass::Graphics,
        dst_queue: QueueClass::Graphics,
    }
}

pub(super) fn texture_subresource_barrier(
    resource: Handle,
    subresources: TextureSubresourceRange,
    before: TextureUsageState,
    after: TextureUsageState,
) -> ResourceBarrier {
    ResourceBarrier {
        resource,
        subresources: Some(subresources),
        before,
        after,
        src_queue: QueueClass::Graphics,
        dst_queue: QueueClass::Graphics,
    }
}

pub(super) fn sampled_texture_barrier(
    resource: Handle,
    before: TextureUsageState,
    after: TextureUsageState,
) -> ResourceBarrier {
    ResourceBarrier {
        resource,
        subresources: None,
        before,
        after,
        src_queue: QueueClass::Graphics,
        dst_queue: QueueClass::Graphics,
    }
}

pub(super) fn json_escape(value: &str) -> String {
    value
        .replace('\\', "\\\\")
        .replace('"', "\\\"")
        .replace('\n', "\\n")
        .replace('\r', "\\r")
}

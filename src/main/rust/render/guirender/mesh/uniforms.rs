//! Binding descriptors, barriers and uniform and index packing for mesh passes.

use super::*;

pub(super) fn resource_binding_desc(binding: u32, kind: ResourceBindingKind) -> ResourceBindingDesc {
    ResourceBindingDesc {
        binding,
        kind,
        stages: PipelineStageFlags::DRAW,
        array_count: 1,
        optional: false,
        dynamic_offset_count: 0,
    }
}

pub(super) fn dynamic_resource_binding_desc(binding: u32, kind: ResourceBindingKind) -> ResourceBindingDesc {
    ResourceBindingDesc {
        dynamic_offset_count: 1,
        ..resource_binding_desc(binding, kind)
    }
}

pub(super) fn read_binding(binding: u32, resource: Handle, kind: ResourceBindingKind) -> ResourceBinding {
    ResourceBinding {
        binding,
        array_index: 0,
        resource,
        kind,
        access: AccessFlags::READ,
        dynamic_offsets: Vec::new(),
        buffer_range: None,
    }
}

pub(super) fn dynamic_read_binding(
    binding: u32,
    resource: Handle,
    kind: ResourceBindingKind,
    buffer_range: u64,
) -> ResourceBinding {
    ResourceBinding {
        dynamic_offsets: vec![0],
        buffer_range: Some(buffer_range),
        ..read_binding(binding, resource, kind)
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

pub(super) fn packed_indices_with_base(indices: &[u32], vertex_base: u32) -> GalResult<Vec<u8>> {
    let mut bytes = Vec::with_capacity(indices.len() * std::mem::size_of::<u32>());
    for index in indices {
        let adjusted = index.checked_add(vertex_base).ok_or_else(|| {
            GalError::ffi(
                StatusCode::InvalidArgument,
                "GUI mesh index stream base overflows u32",
            )
        })?;
        bytes.extend_from_slice(&adjusted.to_le_bytes());
    }
    Ok(bytes)
}

pub(super) fn draw_frame_uniform_bytes(draw: &GuiMeshPreparedDraw, extent: [f32; 2]) -> Vec<u8> {
    // Opaque means no fragment discard, not an alpha threshold of zero.
    // A zero-alpha opaque model surface still replaces color and writes depth;
    // discarding it exposes geometry behind it (e.g. the shield handle).
    // This is private shader lowering of the explicit material, not new Java
    // policy or an exposed implicit alpha-test state. Other materials retain
    // their declared cutoff unchanged.
    let cutoff = if draw.material_mode == GuiMeshMaterialMode::Opaque {
        -1.0
    } else {
        draw.alpha_cutoff
    };
    let mut bytes = frame_uniform_bytes(extent, cutoff, draw.lighting_mode);
    if draw.material_mode == GuiMeshMaterialMode::ModelOverlay {
        bytes[12..16].copy_from_slice(&3.0_f32.to_le_bytes());
    }
    bytes
}

pub(super) fn frame_uniform_bytes(
    extent: [f32; 2],
    alpha_cutoff: f32,
    lighting_mode: GuiMeshLightingMode,
) -> Vec<u8> {
    let mut bytes = Vec::with_capacity(GUI_MESH_FRAME_UNIFORM_BYTES);
    // Inventory lighting consumes the signed-normalized packed vertex normal
    // directly, as Frozen entity.vsh/light.glsl do. Renormalizing after i8
    // quantization changes the light intensity. Preserve the existing separate
    // upright mesh convention rather than silently changing unrelated routes.
    let lighting_policy = match lighting_mode {
        GuiMeshLightingMode::InventoryBlock
        | GuiMeshLightingMode::FrontModel
        | GuiMeshLightingMode::EntityPreview
        | GuiMeshLightingMode::OversizedItem
        | GuiMeshLightingMode::OversizedItemFlat => 2.0,
        GuiMeshLightingMode::Block => 1.0,
        GuiMeshLightingMode::Flat => 0.0,
    };
    for value in [
        extent[0] as f32,
        extent[1] as f32,
        alpha_cutoff,
        lighting_policy,
    ] {
        bytes.extend_from_slice(&value.to_le_bytes());
    }
    if lighting_mode == GuiMeshLightingMode::EntityPreview {
        // Independent semantic constants from vanilla Lighting.ENTITY_IN_UI.
        // No Java lighting UBO or graphics state crosses this boundary.
        for [x, y, z] in [[0.2_f32, -1.0, 1.0], [-0.2, -1.0, 0.0]] {
            let length = (x * x + y * y + z * z).sqrt();
            for value in [x / length, y / length, z / length, 0.0] {
                bytes.extend_from_slice(&value.to_le_bytes());
            }
        }
        return bytes;
    }
    if matches!(
        lighting_mode,
        GuiMeshLightingMode::FrontModel | GuiMeshLightingMode::OversizedItemFlat
    ) {
        // Frozen Lighting.ITEMS_FLAT: rotationY(-pi/8).rotateX(3pi/4).
        // Normals already include the item atlas's Y reflection; the light
        // directions themselves do not. No borrowed Lighting UBO/state.
        let (sx, cx) = (std::f32::consts::PI * 0.75).sin_cos();
        let (sy, cy) = (-std::f32::consts::PI / 8.0).sin_cos();
        for [x, y, z] in [[0.2_f32, 1.0, -0.7], [-0.2, 1.0, 0.7]] {
            let length = (x * x + y * y + z * z).sqrt();
            let ry = cx * y - sx * z;
            let rz = sx * y + cx * z;
            for value in [
                (cy * x + sy * rz) / length,
                ry / length,
                (-sy * x + cy * rz) / length,
                0.0,
            ] {
                bytes.extend_from_slice(&value.to_le_bytes());
            }
        }
        return bytes;
    }
    // Frozen OpenGL GuiRenderer.renderItemToAtlas uses ITEMS_3D with
    // scale(k, -k, k) normals. ITEMS_3D_UPRIGHT belongs to the separate
    // PIP convention, not ordinary inventory icons. Keep the light-space
    // selection explicit; never infer it from a backend or borrowed UBO.
    // Oversized items carry normals in Frozen's complete PIP pose
    // scale(f,f,-f)*scale(1,-1,-1): the same (k,-k,k) space as the atlas.
    let light_y_sign = if matches!(
        lighting_mode,
        GuiMeshLightingMode::InventoryBlock | GuiMeshLightingMode::OversizedItem
    ) {
        -1.0
    } else {
        1.0
    };
    for value in [
        -0.933_439_2_f32,
        0.262_694_72 * light_y_sign,
        -0.244_300_16,
        0.0,
        -0.103_571_37,
        0.976_606_8 * light_y_sign,
        0.188_446_42,
        0.0,
    ] {
        bytes.extend_from_slice(&value.to_le_bytes());
    }
    bytes
}

pub(super) fn argb_to_rgba(color: u32) -> [f32; 4] {
    [
        ((color >> 16) & 0xff) as f32 / 255.0,
        ((color >> 8) & 0xff) as f32 / 255.0,
        (color & 0xff) as f32 / 255.0,
        ((color >> 24) & 0xff) as f32 / 255.0,
    ]
}

pub(super) fn push_f32(bytes: &mut Vec<u8>, value: f32) {
    bytes.extend_from_slice(&value.to_le_bytes());
}

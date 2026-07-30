use super::*;
use crate::render::vulkanic::world_primitive_frontend::material as world_material_semantics;

pub(crate) unsafe fn decode_whole_frame_submit(
    request: *const FfiWholeFrameSubmitRequest,
    capabilities: BackendCapabilities,
) -> GalResult<(u64, Handle, WorldPrimitiveFrame, Vec<GuiSpriteRequest>)> {
    decode_whole_frame_submit_with_backend_policy(request, capabilities, true)
}

pub(crate) unsafe fn decode_world_primitive_submit(
    request: *const FfiWholeFrameSubmitRequest,
    capabilities: BackendCapabilities,
) -> GalResult<(u64, Handle, WorldPrimitiveFrame)> {
    let (generation, frame_target, frame, gui_sprites) =
        decode_whole_frame_submit_with_backend_policy(request, capabilities, false)?;
    if !gui_sprites.is_empty() {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world primitive submit does not accept GUI sprites",
        ));
    }
    Ok((generation, frame_target, frame))
}

pub(crate) unsafe fn decode_whole_frame_submit_with_backend_policy(
    request: *const FfiWholeFrameSubmitRequest,
    capabilities: BackendCapabilities,
    require_vulkan_whole_frame: bool,
) -> GalResult<(u64, Handle, WorldPrimitiveFrame, Vec<GuiSpriteRequest>)> {
    let request = read_struct(request, "whole-frame submit request")?;
    validate_header::<FfiWholeFrameSubmitRequest>(request.header)?;
    reject_unknown_feature_bits(request.negotiated_feature_bits)?;
    let supported = capability_feature_bits(capabilities);
    if request.negotiated_feature_bits & !supported != 0 {
        return Err(GalError::unsupported_feature(format!(
            "requested unsupported whole-frame feature bits 0x{:x}",
            request.negotiated_feature_bits & !supported
        )));
    }
    if require_vulkan_whole_frame && capabilities.api != BackendApi::Vulkan {
        return Err(GalError::unsupported_feature(
            "whole-frame world primitive submit requires the Rust Vulkan backend",
        ));
    }
    if request.generation == 0 || request.frame_id == 0 || request.correlation_id == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "whole-frame submit requires non-zero generation, frame id, and correlation id",
        ));
    }
    if request.gui_width <= 0
        || request.gui_height <= 0
        || request.viewport_width <= 0
        || request.viewport_height <= 0
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "whole-frame submit requires positive GUI and viewport dimensions",
        ));
    }
    let frame_target = Handle::from(request.frame_target);
    if frame_target.is_null() || frame_target.kind() != Some(HandleKind::FrameTarget) {
        return Err(GalError::ffi(
            StatusCode::WrongHandleType,
            "whole-frame submit requires a frame-target handle",
        ));
    }
    for value in request
        .view_matrix
        .iter()
        .chain(request.projection_matrix.iter())
    {
        if !value.is_finite() {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "whole-frame matrices must contain finite values",
            ));
        }
    }
    let background = decode_world_background_request(
        request.world_background,
        request.viewport_width,
        request.viewport_height,
    )?;
    let raw_segments = read_slice(
        request.world_segments,
        true,
        "world primitive line segments",
    )?;
    if raw_segments.len() > FFI_MAX_BATCH_ITEMS {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "world primitive segment count {} exceeds max {}",
                raw_segments.len(),
                FFI_MAX_BATCH_ITEMS
            ),
        ));
    }
    let mut segments = Vec::with_capacity(raw_segments.len());
    for segment in raw_segments {
        validate_item_size::<FfiWorldLineSegmentRequest>(
            segment.byte_size,
            "world primitive line segment",
        )?;
        let viewport_width = u32::try_from(segment.viewport_width).map_err(|_| {
            GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "world primitive segment viewport width must be non-negative, got {}",
                    segment.viewport_width
                ),
            )
        })?;
        let viewport_height = u32::try_from(segment.viewport_height).map_err(|_| {
            GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "world primitive segment viewport height must be non-negative, got {}",
                    segment.viewport_height
                ),
            )
        })?;
        segments.push(WorldLineSegmentRequest {
            stratum: segment.stratum,
            style: segment.style,
            depth_policy: segment.depth_policy,
            color_argb: segment.color_argb,
            line_width: segment.line_width,
            start: [segment.start_x, segment.start_y, segment.start_z],
            end: [segment.end_x, segment.end_y, segment.end_z],
            viewport_width,
            viewport_height,
        });
    }
    let raw_cracks = read_slice(
        request.world_crack_quads,
        true,
        "world primitive crack quads",
    )?;
    if raw_cracks.len() > FFI_MAX_BATCH_ITEMS {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "world primitive crack quad count {} exceeds max {}",
                raw_cracks.len(),
                FFI_MAX_BATCH_ITEMS
            ),
        ));
    }
    let mut crack_quads = Vec::with_capacity(raw_cracks.len());
    for quad in raw_cracks {
        validate_item_size::<FfiWorldCrackQuadRequest>(
            quad.byte_size,
            "world primitive crack quad",
        )?;
        if quad.blend_policy != 1 {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!("unknown world crack blend policy {}", quad.blend_policy),
            ));
        }
        if quad.cull_policy != 0 {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!("unknown world crack cull policy {}", quad.cull_policy),
            ));
        }
        let viewport_width = u32::try_from(quad.viewport_width).map_err(|_| {
            GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "world crack quad viewport width must be non-negative, got {}",
                    quad.viewport_width
                ),
            )
        })?;
        let viewport_height = u32::try_from(quad.viewport_height).map_err(|_| {
            GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "world crack quad viewport height must be non-negative, got {}",
                    quad.viewport_height
                ),
            )
        })?;
        crack_quads.push(WorldCrackQuadRequest {
            stratum: quad.stratum,
            stage: quad.stage,
            depth_policy: quad.depth_policy,
            color_argb: quad.color_argb,
            vertices: [
                [quad.p0_x, quad.p0_y, quad.p0_z],
                [quad.p1_x, quad.p1_y, quad.p1_z],
                [quad.p2_x, quad.p2_y, quad.p2_z],
                [quad.p3_x, quad.p3_y, quad.p3_z],
            ],
            viewport_width,
            viewport_height,
        });
    }
    let raw_borders = read_slice(
        request.world_border_quads,
        true,
        "world primitive border quads",
    )?;
    if raw_borders.len() > FFI_MAX_BATCH_ITEMS {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "world primitive border quad count {} exceeds max {}",
                raw_borders.len(),
                FFI_MAX_BATCH_ITEMS
            ),
        ));
    }
    let mut border_quads = Vec::with_capacity(raw_borders.len());
    for quad in raw_borders {
        validate_item_size::<FfiWorldBorderQuadRequest>(
            quad.byte_size,
            "world primitive border quad",
        )?;
        if quad.texture_id != 1 {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!("unknown world border texture id {}", quad.texture_id),
            ));
        }
        if quad.blend_policy != 1 {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!("unknown world border blend policy {}", quad.blend_policy),
            ));
        }
        if quad.cull_policy != 0 {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!("unknown world border cull policy {}", quad.cull_policy),
            ));
        }
        let viewport_width = u32::try_from(quad.viewport_width).map_err(|_| {
            GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "world border quad viewport width must be non-negative, got {}",
                    quad.viewport_width
                ),
            )
        })?;
        let viewport_height = u32::try_from(quad.viewport_height).map_err(|_| {
            GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "world border quad viewport height must be non-negative, got {}",
                    quad.viewport_height
                ),
            )
        })?;
        border_quads.push(WorldBorderQuadRequest {
            stratum: quad.stratum,
            texture_id: quad.texture_id,
            depth_policy: quad.depth_policy,
            blend_policy: quad.blend_policy,
            cull_policy: quad.cull_policy,
            color_argb: quad.color_argb,
            border_size: quad.border_size,
            distance_to_border: quad.distance_to_border,
            scroll: [quad.scroll_u, quad.scroll_v],
            uv_region: [quad.uv_u, quad.uv_v, quad.uv_width, quad.uv_height],
            vertices: [
                [quad.p0_x, quad.p0_y, quad.p0_z],
                [quad.p1_x, quad.p1_y, quad.p1_z],
                [quad.p2_x, quad.p2_y, quad.p2_z],
                [quad.p3_x, quad.p3_y, quad.p3_z],
            ],
            viewport_width,
            viewport_height,
        });
    }
    let raw_materials = read_slice(
        request.world_material_quads,
        true,
        "world primitive material quads",
    )?;
    let raw_material_table = read_slice(
        request.world_material_table,
        true,
        "world primitive compact material table",
    )?;
    let raw_compact_materials = read_slice(
        request.world_material_compact_quads,
        true,
        "world primitive compact material quads",
    )?;
    if raw_materials.len() > FFI_MAX_BATCH_ITEMS {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "world primitive material quad count {} exceeds max {}",
                raw_materials.len(),
                FFI_MAX_BATCH_ITEMS
            ),
        ));
    }
    if raw_material_table.len() > FFI_MAX_BATCH_ITEMS {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "world primitive compact material table count {} exceeds max {}",
                raw_material_table.len(),
                FFI_MAX_BATCH_ITEMS
            ),
        ));
    }
    if raw_compact_materials.len() > FFI_MAX_BATCH_ITEMS {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "world primitive compact material quad count {} exceeds max {}",
                raw_compact_materials.len(),
                FFI_MAX_BATCH_ITEMS
            ),
        ));
    }
    if !raw_materials.is_empty()
        && (!raw_material_table.is_empty() || !raw_compact_materials.is_empty())
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world primitive material payload must use either legacy quads or compact material batches, not both",
        ));
    }
    if raw_material_table.is_empty() && !raw_compact_materials.is_empty() {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "compact world material quads require a non-empty material table",
        ));
    }
    if !raw_material_table.is_empty() && raw_compact_materials.is_empty() {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "compact world material table requires compact quad records",
        ));
    }
    let mut material_quads = Vec::with_capacity(raw_materials.len());
    for quad in raw_materials {
        validate_item_size::<FfiWorldMaterialQuadRequest>(
            quad.byte_size,
            "world primitive material quad",
        )?;
        if quad.stratum != WORLD_STRATUM_OPAQUE_TEXTURED_GEOMETRY {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!("unknown world material stratum {}", quad.stratum),
            ));
        }
        let material_id = world_material_semantics::canonical_material_id(quad.material_id)
            .ok_or_else(|| {
                GalError::ffi(
                    StatusCode::UnknownEnum,
                    format!("unknown world material id {}", quad.material_id),
                )
            })?;
        let texture_id = world_material_semantics::canonical_texture_id(quad.texture_id)
            .ok_or_else(|| {
                GalError::ffi(
                    StatusCode::UnknownEnum,
                    format!("unknown world material texture id {}", quad.texture_id),
                )
            })?;
        if quad.material_mode != WORLD_MATERIAL_MODE_OPAQUE
            && quad.material_mode != WORLD_MATERIAL_MODE_CUTOUT
        {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!("unknown world material mode {}", quad.material_mode),
            ));
        }
        if !world_material_semantics::material_matches_mode(material_id, quad.material_mode) {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "world material id {} is incompatible with mode {}",
                    quad.material_id, quad.material_mode
                ),
            ));
        }
        if quad.depth_policy != WORLD_DEPTH_POLICY_DISABLED
            && quad.depth_policy != WORLD_DEPTH_POLICY_TEST_WRITE
            && quad.depth_policy != WORLD_DEPTH_POLICY_TEST_NO_WRITE
        {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!("unknown world material depth policy {}", quad.depth_policy),
            ));
        }
        if quad.cull_policy != WORLD_CULL_NONE
            && quad.cull_policy != WORLD_CULL_BACK
            && quad.cull_policy != WORLD_CULL_FRONT
        {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!("unknown world material cull policy {}", quad.cull_policy),
            ));
        }
        if quad.topology != WORLD_TOPOLOGY_TRIANGLES {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!("unknown world material topology {}", quad.topology),
            ));
        }
        let winding = if quad.reserved0 == 0 {
            WORLD_WINDING_CCW
        } else {
            quad.reserved0
        };
        if winding != WORLD_WINDING_CCW && winding != WORLD_WINDING_CW {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!("unknown world material winding {}", winding),
            ));
        }
        let viewport_width = u32::try_from(quad.viewport_width).map_err(|_| {
            GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "world material quad viewport width must be non-negative, got {}",
                    quad.viewport_width
                ),
            )
        })?;
        let viewport_height = u32::try_from(quad.viewport_height).map_err(|_| {
            GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "world material quad viewport height must be non-negative, got {}",
                    quad.viewport_height
                ),
            )
        })?;
        material_quads.push(WorldMaterialQuadRequest {
            stratum: quad.stratum,
            material_id,
            texture_id,
            material_mode: quad.material_mode,
            depth_policy: quad.depth_policy,
            cull_policy: quad.cull_policy,
            topology: quad.topology,
            winding,
            color_argb: quad.color_argb,
            vertices: [
                [quad.p0_x, quad.p0_y, quad.p0_z],
                [quad.p1_x, quad.p1_y, quad.p1_z],
                [quad.p2_x, quad.p2_y, quad.p2_z],
                [quad.p3_x, quad.p3_y, quad.p3_z],
            ],
            uvs: [
                [quad.uv0_u, quad.uv0_v],
                [quad.uv1_u, quad.uv1_v],
                [quad.uv2_u, quad.uv2_v],
                [quad.uv3_u, quad.uv3_v],
            ],
            viewport_width,
            viewport_height,
        });
    }
    if !raw_compact_materials.is_empty() {
        let viewport_width_for_materials = u32::try_from(request.viewport_width).map_err(|_| {
            GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "whole-frame viewport width must be non-negative, got {}",
                    request.viewport_width
                ),
            )
        })?;
        let viewport_height_for_materials =
            u32::try_from(request.viewport_height).map_err(|_| {
                GalError::ffi(
                    StatusCode::InvalidArgument,
                    format!(
                        "whole-frame viewport height must be non-negative, got {}",
                        request.viewport_height
                    ),
                )
            })?;
        let mut table = Vec::with_capacity(raw_material_table.len());
        for record in raw_material_table {
            validate_item_size::<FfiWorldMaterialTableRecord>(
                record.byte_size,
                "world primitive compact material table record",
            )?;
            if record.stratum != WORLD_STRATUM_OPAQUE_TEXTURED_GEOMETRY {
                return Err(GalError::ffi(
                    StatusCode::UnknownEnum,
                    format!("unknown compact world material stratum {}", record.stratum),
                ));
            }
            let material_id = world_material_semantics::canonical_material_id(record.material_id)
                .ok_or_else(|| {
                GalError::ffi(
                    StatusCode::UnknownEnum,
                    format!("unknown compact world material id {}", record.material_id),
                )
            })?;
            let texture_id = world_material_semantics::canonical_texture_id(record.texture_id)
                .ok_or_else(|| {
                    GalError::ffi(
                        StatusCode::UnknownEnum,
                        format!(
                            "unknown compact world material texture id {}",
                            record.texture_id
                        ),
                    )
                })?;
            if record.material_mode != WORLD_MATERIAL_MODE_OPAQUE
                && record.material_mode != WORLD_MATERIAL_MODE_CUTOUT
            {
                return Err(GalError::ffi(
                    StatusCode::UnknownEnum,
                    format!(
                        "unknown compact world material mode {}",
                        record.material_mode
                    ),
                ));
            }
            if !world_material_semantics::material_matches_mode(material_id, record.material_mode) {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    format!(
                        "compact world material id {} is incompatible with mode {}",
                        record.material_id, record.material_mode
                    ),
                ));
            }
            if record.depth_policy != WORLD_DEPTH_POLICY_DISABLED
                && record.depth_policy != WORLD_DEPTH_POLICY_TEST_WRITE
                && record.depth_policy != WORLD_DEPTH_POLICY_TEST_NO_WRITE
            {
                return Err(GalError::ffi(
                    StatusCode::UnknownEnum,
                    format!(
                        "unknown compact world material depth policy {}",
                        record.depth_policy
                    ),
                ));
            }
            if record.cull_policy != WORLD_CULL_NONE
                && record.cull_policy != WORLD_CULL_BACK
                && record.cull_policy != WORLD_CULL_FRONT
            {
                return Err(GalError::ffi(
                    StatusCode::UnknownEnum,
                    format!(
                        "unknown compact world material cull policy {}",
                        record.cull_policy
                    ),
                ));
            }
            if record.topology != WORLD_TOPOLOGY_TRIANGLES {
                return Err(GalError::ffi(
                    StatusCode::UnknownEnum,
                    format!(
                        "unknown compact world material topology {}",
                        record.topology
                    ),
                ));
            }
            if record.winding != WORLD_WINDING_CCW && record.winding != WORLD_WINDING_CW {
                return Err(GalError::ffi(
                    StatusCode::UnknownEnum,
                    format!("unknown compact world material winding {}", record.winding),
                ));
            }
            let mut canonical_record = *record;
            canonical_record.material_id = material_id;
            canonical_record.texture_id = texture_id;
            table.push(canonical_record);
        }
        material_quads.reserve(raw_compact_materials.len());
        for quad in raw_compact_materials {
            validate_item_size::<FfiWorldMaterialCompactQuadRequest>(
                quad.byte_size,
                "world primitive compact material quad",
            )?;
            let material_index = usize::try_from(quad.material_index).map_err(|_| {
                GalError::ffi(
                    StatusCode::InvalidArgument,
                    format!(
                        "compact world material index {} overflowed usize",
                        quad.material_index
                    ),
                )
            })?;
            let Some(key) = table.get(material_index) else {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    format!(
                        "compact world material quad references table index {} but table has {} entries",
                        quad.material_index,
                        table.len()
                    ),
                ));
            };
            material_quads.push(WorldMaterialQuadRequest {
                stratum: key.stratum,
                material_id: key.material_id,
                texture_id: key.texture_id,
                material_mode: key.material_mode,
                depth_policy: key.depth_policy,
                cull_policy: key.cull_policy,
                topology: key.topology,
                winding: key.winding,
                color_argb: quad.color_argb,
                vertices: [
                    [quad.p0_x, quad.p0_y, quad.p0_z],
                    [quad.p1_x, quad.p1_y, quad.p1_z],
                    [quad.p2_x, quad.p2_y, quad.p2_z],
                    [quad.p3_x, quad.p3_y, quad.p3_z],
                ],
                uvs: [
                    [quad.uv0_u, quad.uv0_v],
                    [quad.uv1_u, quad.uv1_v],
                    [quad.uv2_u, quad.uv2_v],
                    [quad.uv3_u, quad.uv3_v],
                ],
                viewport_width: viewport_width_for_materials,
                viewport_height: viewport_height_for_materials,
            });
        }
    }
    let raw_mesh_instances = read_slice(
        request.world_mesh_instances,
        true,
        "world primitive mesh instances",
    )?;
    if raw_mesh_instances.len() > FFI_MAX_BATCH_ITEMS {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "world primitive mesh instance count {} exceeds max {}",
                raw_mesh_instances.len(),
                FFI_MAX_BATCH_ITEMS
            ),
        ));
    }
    let mut mesh_instances = Vec::with_capacity(raw_mesh_instances.len());
    for instance in raw_mesh_instances {
        validate_item_size::<FfiWorldMeshInstanceRecord>(
            instance.byte_size,
            "world primitive mesh instance",
        )?;
        if instance.stratum != WORLD_STRATUM_OPAQUE_TEXTURED_GEOMETRY {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!("unknown world mesh stratum {}", instance.stratum),
            ));
        }
        if instance.depth_policy != WORLD_DEPTH_POLICY_DISABLED
            && instance.depth_policy != WORLD_DEPTH_POLICY_TEST_WRITE
            && instance.depth_policy != WORLD_DEPTH_POLICY_TEST_NO_WRITE
        {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!("unknown world mesh depth policy {}", instance.depth_policy),
            ));
        }
        if instance.cull_policy != WORLD_CULL_NONE
            && instance.cull_policy != WORLD_CULL_BACK
            && instance.cull_policy != WORLD_CULL_FRONT
        {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!("unknown world mesh cull policy {}", instance.cull_policy),
            ));
        }
        if instance.winding != WORLD_WINDING_CCW && instance.winding != WORLD_WINDING_CW {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!("unknown world mesh winding {}", instance.winding),
            ));
        }
        let viewport_width = u32::try_from(instance.viewport_width).map_err(|_| {
            GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "world mesh viewport width must be non-negative, got {}",
                    instance.viewport_width
                ),
            )
        })?;
        let viewport_height = u32::try_from(instance.viewport_height).map_err(|_| {
            GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "world mesh viewport height must be non-negative, got {}",
                    instance.viewport_height
                ),
            )
        })?;
        mesh_instances.push(WorldMeshInstanceRequest {
            stratum: instance.stratum,
            mesh_key: instance.mesh_key,
            mesh_generation: instance.mesh_generation,
            mesh_section_index: instance.mesh_section_index,
            depth_policy: instance.depth_policy,
            cull_policy: instance.cull_policy,
            winding: instance.winding,
            color_argb: instance.color_argb,
            transform: instance.transform,
            viewport_width,
            viewport_height,
        });
    }
    let raw_gui = FfiGuiFrameSubmitRequest {
        header: FfiHeader {
            version: request.header.version,
            byte_size: size_of::<FfiGuiFrameSubmitRequest>() as u32,
        },
        generation: request.generation,
        frame_id: request.frame_id,
        frame_target: request.frame_target,
        gui_width: request.gui_width,
        gui_height: request.gui_height,
        sprites: request.gui_sprites,
        negotiated_feature_bits: request.negotiated_feature_bits,
    };
    let (_, _, gui_sprites) = decode_gui_frame_submit(&raw_gui, capabilities)?;
    let viewport_width = u32::try_from(request.viewport_width).map_err(|_| {
        GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "whole-frame viewport width must be non-negative, got {}",
                request.viewport_width
            ),
        )
    })?;
    let viewport_height = u32::try_from(request.viewport_height).map_err(|_| {
        GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "whole-frame viewport height must be non-negative, got {}",
                request.viewport_height
            ),
        )
    })?;
    Ok((
        request.generation,
        frame_target,
        WorldPrimitiveFrame {
            frame_id: request.frame_id,
            correlation_id: request.correlation_id,
            viewport_width,
            viewport_height,
            view_matrix: request.view_matrix,
            projection_matrix: request.projection_matrix,
            background,
            segments,
            crack_quads,
            border_quads,
            material_quads,
            mesh_instances,
        },
        gui_sprites,
    ))
}

pub(crate) fn decode_world_background_request(
    request: FfiWorldBackgroundRequest,
    frame_viewport_width: i32,
    frame_viewport_height: i32,
) -> GalResult<WorldBackgroundRequest> {
    validate_item_size::<FfiWorldBackgroundRequest>(request.byte_size, "world background")?;
    let enabled = bool_flag(request.enabled, "world background enabled")?;
    if !enabled {
        return Ok(WorldBackgroundRequest::default());
    }
    if !matches!(
        request.sky_type,
        WORLD_BACKGROUND_SKY_OVERWORLD
            | WORLD_BACKGROUND_SKY_NETHER
            | WORLD_BACKGROUND_SKY_END
            | WORLD_BACKGROUND_SKY_CUSTOM
    ) {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown world background sky type {}", request.sky_type),
        ));
    }
    if request.load_intent != WORLD_BACKGROUND_LOAD_CLEAR {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!(
                "unknown world background load intent {}",
                request.load_intent
            ),
        ));
    }
    if request.store_intent != WORLD_BACKGROUND_STORE_STORE {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!(
                "unknown world background store intent {}",
                request.store_intent
            ),
        ));
    }
    let viewport_width = u32::try_from(request.viewport_width).map_err(|_| {
        GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "world background viewport width must be non-negative, got {}",
                request.viewport_width
            ),
        )
    })?;
    let viewport_height = u32::try_from(request.viewport_height).map_err(|_| {
        GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "world background viewport height must be non-negative, got {}",
                request.viewport_height
            ),
        )
    })?;
    if request.viewport_width != frame_viewport_width
        || request.viewport_height != frame_viewport_height
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world background viewport metadata must match whole-frame viewport",
        ));
    }
    Ok(WorldBackgroundRequest {
        enabled,
        sky_type: request.sky_type,
        color_argb: request.color_argb,
        load_intent: request.load_intent,
        store_intent: request.store_intent,
        viewport_width,
        viewport_height,
    })
}

pub(crate) unsafe fn decode_world_border_asset_update(
    request: *const FfiWorldBorderAssetUpdateRequest,
    capabilities: BackendCapabilities,
) -> GalResult<(u64, WorldBorderAssetPayload)> {
    let request = read_struct(request, "world-border asset update request")?;
    validate_header::<FfiWorldBorderAssetUpdateRequest>(request.header)?;
    reject_unknown_feature_bits(request.negotiated_feature_bits)?;
    let supported = capability_feature_bits(capabilities);
    if request.negotiated_feature_bits & !supported != 0 {
        return Err(GalError::unsupported_feature(format!(
            "requested unsupported world-border asset feature bits 0x{:x}",
            request.negotiated_feature_bits & !supported
        )));
    }
    if request.generation == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world-border asset generation must be non-zero",
        ));
    }
    let png_bytes = read_bounded_bytes(
        request.png_bytes,
        true,
        FFI_MAX_WORLD_BORDER_ASSET_BYTES,
        "world-border texture PNG bytes",
    )?;
    Ok((
        request.generation,
        WorldBorderAssetPayload {
            texture_id: request.texture_id,
            png_bytes,
        },
    ))
}

pub(crate) unsafe fn decode_world_crack_asset_update(
    request: *const FfiWorldCrackAssetUpdateRequest,
    capabilities: BackendCapabilities,
) -> GalResult<(u64, Vec<WorldCrackAssetPayload>)> {
    let request = read_struct(request, "world crack asset update request")?;
    validate_header::<FfiWorldCrackAssetUpdateRequest>(request.header)?;
    reject_unknown_feature_bits(request.negotiated_feature_bits)?;
    let supported = capability_feature_bits(capabilities);
    if request.negotiated_feature_bits & !supported != 0 {
        return Err(GalError::unsupported_feature(format!(
            "requested unsupported world crack asset feature bits 0x{:x}",
            request.negotiated_feature_bits & !supported
        )));
    }
    if request.generation == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world crack asset generation must be non-zero",
        ));
    }
    let raw_assets = read_limited_slice(request.assets, true, "world crack asset payloads")?;
    let mut seen = BTreeMap::new();
    let mut assets = Vec::with_capacity(raw_assets.len());
    for asset in raw_assets {
        validate_item_size::<FfiWorldCrackAssetPayload>(
            asset.byte_size,
            "world crack asset payload",
        )?;
        if asset.stage >= 10 {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!("unknown world crack stage {}", asset.stage),
            ));
        }
        if seen.insert(asset.stage, ()).is_some() {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "duplicate world crack asset payload for stage {}",
                    asset.stage
                ),
            ));
        }
        let png_bytes = read_bounded_bytes(
            asset.png_bytes,
            true,
            FFI_MAX_WORLD_CRACK_ASSET_BYTES,
            "world crack asset PNG bytes",
        )?;
        assets.push(WorldCrackAssetPayload {
            stage: asset.stage,
            png_bytes,
        });
    }
    Ok((request.generation, assets))
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_whole_frame_submit(
    context_id: u64,
    request: *const FfiWholeFrameSubmitRequest,
    out: *mut FfiWholeFrameSubmitResult,
) -> i32 {
    with_registry_mut(|registry| {
        let Some(context) = registry.contexts.get_mut(&context_id) else {
            let error = GalError::ffi(
                StatusCode::StaleHandle,
                format!("unknown context id {context_id}"),
            );
            let _ = write_out(
                out,
                FfiWholeFrameSubmitResult {
                    status: error.code as i32,
                    error_domain: error.domain as u32,
                    ..FfiWholeFrameSubmitResult::default()
                },
                "whole-frame submit result",
            );
            return error.code as i32;
        };
        let input_bytes = if request.is_null() {
            0
        } else {
            input_bytes_for_whole_frame(&*request)
        };
        context.ffi_calls += 1;
        context.ffi_input_bytes = context.ffi_input_bytes.saturating_add(input_bytes);
        context.ffi_output_bytes = context
            .ffi_output_bytes
            .saturating_add(size_of::<FfiWholeFrameSubmitResult>() as u64);
        let result = decode_whole_frame_submit(request, context.gal.capabilities()).and_then(
            |(generation, frame_target, world_frame, gui_sprites)| {
                let (gui_ops, gui_stats) = context.gui_frontend.append_frame_ops(
                    &mut context.gal,
                    generation,
                    frame_target,
                    gui_sprites,
                )?;
                let mut world_stats = context.world_primitive_frontend.submit_whole_frame(
                    &mut context.gal,
                    generation,
                    frame_target,
                    world_frame,
                    gui_ops,
                )?;
                destroy_stale_frame_targets(context)?;
                world_stats.command_lists = 1;
                Ok((world_stats, gui_stats))
            },
        );
        match result {
            Ok((world_stats, gui_stats)) => {
                let value = whole_frame_result_ok(context, world_stats, gui_stats);
                let _ = write_out(out, value, "whole-frame submit result");
                StatusCode::Ok as i32
            }
            Err(error) => {
                set_last_error(context, &error);
                let _ = write_out(
                    out,
                    FfiWholeFrameSubmitResult {
                        status: error.code as i32,
                        error_domain: error.domain as u32,
                        metrics: context_metrics(context),
                        ..FfiWholeFrameSubmitResult::default()
                    },
                    "whole-frame submit result",
                );
                error.code as i32
            }
        }
    })
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_world_primitives_submit(
    context_id: u64,
    request: *const FfiWholeFrameSubmitRequest,
    out: *mut FfiWholeFrameSubmitResult,
) -> i32 {
    with_registry_mut(|registry| {
        let Some(context) = registry.contexts.get_mut(&context_id) else {
            let error = GalError::ffi(
                StatusCode::StaleHandle,
                format!("unknown context id {context_id}"),
            );
            let _ = write_out(
                out,
                FfiWholeFrameSubmitResult {
                    status: error.code as i32,
                    error_domain: error.domain as u32,
                    ..FfiWholeFrameSubmitResult::default()
                },
                "world primitive submit result",
            );
            return error.code as i32;
        };
        let input_bytes = if request.is_null() {
            0
        } else {
            input_bytes_for_whole_frame(&*request)
        };
        context.ffi_calls += 1;
        context.ffi_input_bytes = context.ffi_input_bytes.saturating_add(input_bytes);
        context.ffi_output_bytes = context
            .ffi_output_bytes
            .saturating_add(size_of::<FfiWholeFrameSubmitResult>() as u64);
        let result = decode_world_primitive_submit(request, context.gal.capabilities()).and_then(
            |(generation, frame_target, world_frame)| {
                let world_stats = context.world_primitive_frontend.submit_partial_frame(
                    &mut context.gal,
                    generation,
                    frame_target,
                    world_frame,
                )?;
                destroy_stale_frame_targets(context)?;
                Ok(world_stats)
            },
        );
        match result {
            Ok(world_stats) => {
                let value = whole_frame_result_ok(context, world_stats, GuiSubmitStats::default());
                let _ = write_out(out, value, "world primitive submit result");
                StatusCode::Ok as i32
            }
            Err(error) => {
                set_last_error(context, &error);
                let _ = write_out(
                    out,
                    FfiWholeFrameSubmitResult {
                        status: error.code as i32,
                        error_domain: error.domain as u32,
                        metrics: context_metrics(context),
                        ..FfiWholeFrameSubmitResult::default()
                    },
                    "world primitive submit result",
                );
                error.code as i32
            }
        }
    })
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_world_border_update_asset(
    context_id: u64,
    request: *const FfiWorldBorderAssetUpdateRequest,
    status_out: *mut FfiStatusResult,
) -> i32 {
    with_registry_mut(|registry| {
        let Some(context) = registry.contexts.get_mut(&context_id) else {
            let error = GalError::ffi(
                StatusCode::StaleHandle,
                format!("unknown context id {context_id}"),
            );
            write_status_out(status_out, status_result_from_error(&error));
            return error.code as i32;
        };
        let input_bytes = if request.is_null() {
            0
        } else {
            input_bytes_for_world_border_asset_update(&*request)
        };
        context.ffi_calls += 1;
        context.ffi_input_bytes = context.ffi_input_bytes.saturating_add(input_bytes);
        context.ffi_output_bytes = context
            .ffi_output_bytes
            .saturating_add(size_of::<FfiStatusResult>() as u64);
        let result = decode_world_border_asset_update(request, context.gal.capabilities())
            .and_then(|(generation, payload)| {
                context
                    .world_primitive_frontend
                    .apply_world_border_asset_update(&mut context.gal, generation, payload)
            });
        match result {
            Ok(()) => {
                write_status_out(status_out, status_ok(context));
                StatusCode::Ok as i32
            }
            Err(error) => {
                set_last_error(context, &error);
                write_status_out(status_out, status_error(Some(context), &error));
                error.code as i32
            }
        }
    })
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_world_crack_update_assets(
    context_id: u64,
    request: *const FfiWorldCrackAssetUpdateRequest,
    status_out: *mut FfiStatusResult,
) -> i32 {
    with_registry_mut(|registry| {
        let Some(context) = registry.contexts.get_mut(&context_id) else {
            let error = GalError::ffi(
                StatusCode::StaleHandle,
                format!("unknown context id {context_id}"),
            );
            write_status_out(status_out, status_result_from_error(&error));
            return error.code as i32;
        };
        let input_bytes = if request.is_null() {
            0
        } else {
            input_bytes_for_world_crack_asset_update(&*request)
        };
        context.ffi_calls += 1;
        context.ffi_input_bytes = context.ffi_input_bytes.saturating_add(input_bytes);
        context.ffi_output_bytes = context
            .ffi_output_bytes
            .saturating_add(size_of::<FfiStatusResult>() as u64);
        let result = decode_world_crack_asset_update(request, context.gal.capabilities()).and_then(
            |(generation, payloads)| {
                context
                    .world_primitive_frontend
                    .apply_world_crack_asset_update(&mut context.gal, generation, payloads)
            },
        );
        match result {
            Ok(()) => {
                write_status_out(status_out, status_ok(context));
                StatusCode::Ok as i32
            }
            Err(error) => {
                set_last_error(context, &error);
                write_status_out(status_out, status_error(Some(context), &error));
                error.code as i32
            }
        }
    })
}

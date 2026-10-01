//! Distant Horizons LOD asset updates and the LOD render frame.

use super::*;

/// Decodes one copied DH LOD asset generation. Rendering admission is
/// intentionally separate: this endpoint owns only transport validation and
/// caller-memory copying into the Rust frontend cache.
pub(crate) unsafe fn decode_world_lod_asset_update(
    request: *const FfiWorldLodAssetUpdateRequest,
    capabilities: BackendCapabilities,
) -> GalResult<(
    u64,
    Vec<WorldLodColumnAsset>,
    Vec<WorldLodColumnRetirement>,
    Vec<WorldLodColumnMaterialProvenance>,
)> {
    let request = read_struct(request, "world LOD asset update request")?;
    validate_header::<FfiWorldLodAssetUpdateRequest>(request.header)?;
    reject_unknown_feature_bits(request.negotiated_feature_bits)?;
    let supported = capability_feature_bits(capabilities);
    if request.negotiated_feature_bits & !supported != 0 {
        return Err(GalError::unsupported_feature(format!(
            "requested unsupported world LOD feature bits 0x{:x}",
            request.negotiated_feature_bits & !supported
        )));
    }
    if request.generation == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world LOD asset update generation must be non-zero",
        ));
    }
    let raw_assets = read_limited_slice(request.assets, true, "world LOD column assets")?;
    if raw_assets.len() > WORLD_LOD_MAX_COLUMNS {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "world LOD column asset count {} exceeds bounded limit {WORLD_LOD_MAX_COLUMNS}",
                raw_assets.len()
            ),
        ));
    }
    let mut assets = Vec::with_capacity(raw_assets.len());
    let mut asset_keys = BTreeMap::new();
    for asset in raw_assets {
        validate_item_size::<FfiWorldLodColumnAssetRecord>(
            asset.byte_size,
            "world LOD column asset",
        )?;
        if asset.reserved0 != 0 {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "world LOD column asset reserved field must be zero",
            ));
        }
        if asset.column_generation == 0 {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "world LOD column asset generation must be non-zero",
            ));
        }
        if asset_keys.insert(asset.column_key, ()).is_some() {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!("duplicate world LOD column asset {}", asset.column_key),
            ));
        }
        let raw_segments = read_limited_slice(asset.segments, false, "world LOD segments")?;
        if raw_segments.is_empty() || raw_segments.len() > WORLD_LOD_MAX_SEGMENTS_PER_COLUMN {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "world LOD column {} has {} segments; expected 1..={}",
                    asset.column_key,
                    raw_segments.len(),
                    WORLD_LOD_MAX_SEGMENTS_PER_COLUMN
                ),
            ));
        }
        let mut segments = Vec::with_capacity(raw_segments.len());
        for segment in raw_segments {
            validate_item_size::<FfiWorldLodSegmentRecord>(segment.byte_size, "world LOD segment")?;
            let raw_vertices = read_limited_slice(segment.vertices, true, "world LOD vertices")?;
            // This is a byte stream, not a batch of records: its bound is
            // derived from the explicit vertex limit rather than the generic
            // FFI item-count ceiling.
            let packed_vertices =
                read_slice(segment.packed_vertices, true, "packed world LOD vertices")?;
            if packed_vertices.len() > WORLD_LOD_MAX_VERTICES_PER_SEGMENT * 16 {
                return Err(GalError::ffi(
                    StatusCode::LengthOverflow,
                    "packed world LOD vertices byte length exceeds ABI maximum",
                ));
            }
            if !raw_vertices.is_empty() && !packed_vertices.is_empty() {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    "world LOD segment must use either structured or packed vertices, not both",
                ));
            }
            let vertex_count = if !packed_vertices.is_empty() {
                const PACKED_DH_VERTEX_BYTES: usize = 16;
                if packed_vertices.len() % PACKED_DH_VERTEX_BYTES != 0 {
                    return Err(GalError::ffi(
                        StatusCode::InvalidArgument,
                        "packed world LOD vertices are not 16-byte aligned",
                    ));
                }
                packed_vertices.len() / PACKED_DH_VERTEX_BYTES
            } else {
                raw_vertices.len()
            };
            if vertex_count == 0
                || vertex_count > WORLD_LOD_MAX_VERTICES_PER_SEGMENT
                || vertex_count % 4 != 0
            {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    format!(
                        "world LOD segment has {vertex_count} vertices; expected quad-aligned 1..={}",
                        WORLD_LOD_MAX_VERTICES_PER_SEGMENT
                    ),
                ));
            }
            let mut vertices = Vec::with_capacity(vertex_count);
            if !packed_vertices.is_empty() {
                for bytes in packed_vertices.chunks_exact(16) {
                    let local_x = u16::from_ne_bytes([bytes[0], bytes[1]]);
                    let local_y = u16::from_ne_bytes([bytes[2], bytes[3]]);
                    let local_z = u16::from_ne_bytes([bytes[4], bytes[5]]);
                    let packed_light_and_micro_offset = u16::from_ne_bytes([bytes[6], bytes[7]]);
                    let color_rgba = [bytes[8], bytes[9], bytes[10], bytes[11]];
                    let material_id = bytes[12];
                    let normal_index = bytes[13];
                    if bytes[14] != 0 || bytes[15] != 0 {
                        return Err(GalError::ffi(
                            StatusCode::InvalidArgument,
                            "packed world LOD vertex has non-zero reserved padding",
                        ));
                    }
                    if material_id > 15 || normal_index > WORLD_LOD_MAX_NORMAL_INDEX {
                        return Err(GalError::ffi(
                            StatusCode::InvalidArgument,
                            "packed world LOD vertex contains an out-of-range material or normal",
                        ));
                    }
                    vertices.push(WorldLodVertex {
                        local_position: [local_x, local_y, local_z],
                        packed_light_and_micro_offset,
                        color_rgba,
                        material_id,
                        normal_index,
                    });
                }
            } else {
                for vertex in raw_vertices {
                    validate_item_size::<FfiWorldLodVertex>(vertex.byte_size, "world LOD vertex")?;
                    let material_id = u8::try_from(vertex.material_id).map_err(|_| {
                        GalError::ffi(
                            StatusCode::InvalidArgument,
                            format!("world LOD material id {} exceeds u8", vertex.material_id),
                        )
                    })?;
                    let normal_index = u8::try_from(vertex.normal_index).map_err(|_| {
                        GalError::ffi(
                            StatusCode::InvalidArgument,
                            format!("world LOD normal index {} exceeds u8", vertex.normal_index),
                        )
                    })?;
                    vertices.push(WorldLodVertex {
                        local_position: [vertex.local_x, vertex.local_y, vertex.local_z],
                        packed_light_and_micro_offset: vertex.packed_light_and_micro_offset,
                        color_rgba: vertex.color_rgba.to_le_bytes(),
                        material_id,
                        normal_index,
                    });
                }
            }
            segments.push(WorldLodSegment {
                layer: segment.layer,
                vertices,
            });
        }
        assets.push(WorldLodColumnAsset {
            column_key: asset.column_key,
            column_generation: asset.column_generation,
            vertex_layout_version: asset.vertex_layout_version,
            origin: [asset.origin_x, asset.origin_y, asset.origin_z],
            segments,
        });
    }
    let raw_retirements = read_limited_slice(request.retirements, true, "world LOD retirements")?;
    if raw_retirements.len() > WORLD_LOD_MAX_COLUMNS {
        return Err(GalError::ffi(
            StatusCode::LengthOverflow,
            format!(
                "world LOD retirement count {} exceeds bounded limit {WORLD_LOD_MAX_COLUMNS}",
                raw_retirements.len()
            ),
        ));
    }
    let mut retirements = Vec::with_capacity(raw_retirements.len());
    let mut retirement_keys = BTreeMap::new();
    for retirement in raw_retirements {
        validate_item_size::<FfiWorldLodColumnRetirementRecord>(
            retirement.byte_size,
            "world LOD retirement",
        )?;
        if retirement.reserved0 != 0 || retirement.column_generation == 0 {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "world LOD retirement reserved field and generation must be valid",
            ));
        }
        if retirement_keys.insert(retirement.column_key, ()).is_some()
            || asset_keys.contains_key(&retirement.column_key)
        {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "duplicate or conflicting world LOD retirement {}",
                    retirement.column_key
                ),
            ));
        }
        retirements.push(WorldLodColumnRetirement {
            column_key: retirement.column_key,
            column_generation: retirement.column_generation,
        });
    }
    let raw_provenance = read_limited_slice(
        request.material_provenance,
        true,
        "world LOD material provenance columns",
    )?;
    if raw_provenance.len() > WORLD_LOD_MAX_COLUMNS {
        return Err(GalError::ffi(
            StatusCode::LengthOverflow,
            format!(
                "world LOD material provenance column count {} exceeds bounded limit {WORLD_LOD_MAX_COLUMNS}",
                raw_provenance.len()
            ),
        ));
    }
    let mut provenance = Vec::with_capacity(raw_provenance.len());
    for column in raw_provenance {
        validate_item_size::<FfiWorldLodColumnMaterialProvenanceRecord>(
            column.byte_size,
            "world LOD material provenance column",
        )?;
        if column.reserved0 != 0 || column.column_generation == 0 {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "world LOD material provenance reserved field and generation must be valid",
            ));
        }
        let raw_identities = read_limited_slice(
            column.identities,
            true,
            "world LOD material provenance identities",
        )?;
        if raw_identities.len() > WORLD_LOD_MAX_MATERIAL_IDENTITIES_PER_COLUMN {
            return Err(GalError::ffi(
                StatusCode::LengthOverflow,
                "world LOD material provenance identity count exceeds ABI maximum",
            ));
        }
        let mut identities = Vec::with_capacity(raw_identities.len());
        for identity in raw_identities {
            validate_item_size::<FfiWorldLodMaterialIdentityRecord>(
                identity.byte_size,
                "world LOD material identity",
            )?;
            if identity.reserved0 != 0 {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    "world LOD material identity reserved field must be zero",
                ));
            }
            let block_state_identity = read_label(
                identity.block_state_identity_utf8,
                "world LOD material block-state identity",
            )?;
            let biome_identity = read_label(
                identity.biome_identity_utf8,
                "world LOD material biome identity",
            )?;
            if block_state_identity.is_empty() || biome_identity.is_empty() {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    "world LOD material identities must be non-empty",
                ));
            }
            identities.push(WorldLodMaterialIdentity {
                block_state_identity,
                biome_identity,
            });
        }
        let raw_segments = read_limited_slice(
            column.segments,
            true,
            "world LOD segment material provenance",
        )?;
        if raw_segments.len() > WORLD_LOD_MAX_SEGMENTS_PER_COLUMN {
            return Err(GalError::ffi(
                StatusCode::LengthOverflow,
                format!(
                    "world LOD material provenance segment count {} exceeds bounded limit {WORLD_LOD_MAX_SEGMENTS_PER_COLUMN}",
                    raw_segments.len()
                ),
            ));
        }
        let mut segments = Vec::with_capacity(raw_segments.len());
        for segment in raw_segments {
            validate_item_size::<FfiWorldLodSegmentMaterialProvenanceRecord>(
                segment.byte_size,
                "world LOD segment material provenance",
            )?;
            if segment.reserved0 != 0 {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    "world LOD segment material provenance reserved field must be zero",
                ));
            }
            let quad_material_ids = read_limited_slice(
                segment.quad_material_ids,
                true,
                "world LOD quad material IDs",
            )?
            .to_vec();
            let quad_variant_states = read_limited_slice(
                segment.quad_variant_states,
                true,
                "world LOD quad variant states",
            )?
            .to_vec();
            let quad_variant_positions = read_limited_slice(
                segment.quad_variant_positions,
                true,
                "world LOD quad variant positions",
            )?
            .to_vec();
            if quad_variant_states.len() != quad_material_ids.len()
                || quad_variant_positions.len() != quad_material_ids.len()
            {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    "world LOD quad variant provenance must align with material IDs",
                ));
            }
            segments.push(WorldLodSegmentMaterialProvenance {
                layer: segment.layer,
                segment_index: segment.segment_index,
                quad_material_ids,
                quad_variant_states,
                quad_variant_positions,
            });
        }
        let raw_face_materials = read_limited_slice(
            column.face_materials,
            true,
            "world LOD face material records",
        )?;
        let mut face_materials = Vec::with_capacity(raw_face_materials.len());
        let mut seen_faces = BTreeSet::new();
        for face_material in raw_face_materials {
            validate_item_size::<FfiWorldLodFaceMaterialRecord>(
                face_material.byte_size,
                "world LOD face material",
            )?;
            if face_material.material_id == 0
                || face_material.material_id as usize > identities.len()
                || face_material.face > 5
                || face_material.face_layer & !0x07ff_ffff != 0
            {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    "world LOD face material has an invalid identity, face, or layer",
                ));
            }
            let atlas_identity = read_label(
                face_material.atlas_identity_utf8,
                "world LOD face material atlas identity",
            )?;
            let sprite_identity = read_label(
                face_material.sprite_identity_utf8,
                "world LOD face material sprite identity",
            )?;
            let atlas_uv = [
                face_material.u0,
                face_material.v0,
                face_material.u1,
                face_material.v1,
            ];
            if atlas_identity.is_empty()
                || sprite_identity.is_empty()
                || !atlas_uv.iter().all(|value| value.is_finite())
                || atlas_uv[0] < 0.0
                || atlas_uv[1] < 0.0
                || atlas_uv[2] > 1.0
                || atlas_uv[3] > 1.0
                || atlas_uv[0] >= atlas_uv[2]
                || atlas_uv[1] >= atlas_uv[3]
                || !valid_world_lod_uv_corner_order(face_material.uv_corner_order)
                || !seen_faces.insert((
                    face_material.material_id,
                    face_material.face,
                    face_material.face_layer & 0x3,
                    face_material.variant_position,
                ))
            {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    "world LOD face material must be a unique normalized atlas region",
                ));
            }
            face_materials.push(WorldLodFaceMaterial {
                material_id: face_material.material_id,
                face: face_material.face,
                face_layer: face_material.face_layer & 0x3,
                atlas_identity,
                sprite_identity,
                atlas_uv,
                uv_corner_order: face_material.uv_corner_order,
                tinted: face_material.face_layer & 0x4 != 0,
                tint_rgb: [
                    ((face_material.face_layer >> 3) & 0xff) as f32 / 255.0,
                    ((face_material.face_layer >> 11) & 0xff) as f32 / 255.0,
                    ((face_material.face_layer >> 19) & 0xff) as f32 / 255.0,
                ],
                variant_position: face_material.variant_position,
            });
        }
        provenance.push(WorldLodColumnMaterialProvenance {
            column_key: column.column_key,
            column_generation: column.column_generation,
            identities,
            segments,
            face_materials,
        });
    }
    Ok((request.generation, assets, retirements, provenance))
}

pub(super) fn valid_world_lod_uv_corner_order(order: u32) -> bool {
    if order & !0xff != 0 {
        return false;
    }
    let mut seen = 0u8;
    for index in 0..4 {
        let corner = ((order >> (index * 2)) & 0x3) as u8;
        let bit = 1u8 << corner;
        if seen & bit != 0 {
            return false;
        }
        seen |= bit;
    }
    seen == 0x0f
}

pub(super) fn decode_world_lod_render_frame(
    request: FfiWorldLodRenderFrame,
) -> GalResult<WorldLodRenderFrame> {
    validate_item_size::<FfiWorldLodRenderFrame>(request.byte_size, "world LOD render frame")?;
    if request.reserved0 != 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world LOD render frame reserved field must be zero",
        ));
    }
    let enabled = bool_flag(request.enabled, "world LOD render frame enabled")?;
    if request.flags & !0xff != 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "world LOD render frame has unknown flags 0x{:x}",
                request.flags
            ),
        ));
    }
    let finite = request
        .combined_matrix
        .into_iter()
        .chain(request.model_view_matrix)
        .chain(request.projection_matrix)
        .chain(request.projection_inverse_matrix)
        .chain([
            request.clip_distance,
            request.micro_offset,
            request.noise_intensity,
            request.earth_radius,
        ])
        .chain([
            request.camera_world_x,
            request.camera_world_y,
            request.camera_world_z,
        ])
        .chain(request.dh_fog_parameters)
        .chain(request.ssao_parameters)
        .all(f32::is_finite);
    if !finite {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world LOD render frame contains non-finite scalar or matrix data",
        ));
    }
    let frame = WorldLodRenderFrame {
        enabled,
        flags: request.flags,
        world_y_offset: request.world_y_offset,
        camera_world_position: [
            request.camera_world_x,
            request.camera_world_y,
            request.camera_world_z,
        ],
        combined_matrix: request.combined_matrix,
        model_view_matrix: request.model_view_matrix,
        projection_matrix: request.projection_matrix,
        projection_inverse_matrix: request.projection_inverse_matrix,
        clip_distance: request.clip_distance,
        micro_offset: request.micro_offset,
        noise_intensity: request.noise_intensity,
        earth_radius: request.earth_radius,
        noise_steps: request.noise_steps,
        noise_dropoff: request.noise_dropoff,
        dh_fog_parameters: request.dh_fog_parameters,
        max_level_height: request.max_level_height,
        ssao_parameters: request.ssao_parameters,
    };
    if !enabled && frame != WorldLodRenderFrame::default() {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "disabled world LOD render frame must be zeroed",
        ));
    }
    if enabled && (frame.micro_offset <= 0.0 || frame.clip_distance < 0.0) {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "enabled world LOD render frame has invalid clip or micro-offset semantics",
        ));
    }
    Ok(frame)
}

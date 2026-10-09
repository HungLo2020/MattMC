//! Decoding a whole-frame submit: world primitives, meshes, GUI streams and frame-level records.

use super::*;
use crate::render::scene::material::WORLD_MATERIAL_MODE_TRANSLUCENT_CUTOUT;

/// `MATTMC_TRACE_PARTICLE_QUADS=N` logs the first N decoded particle quads of
/// each frame (texture, surface, UV bounds, ARGB colour, packed light,
/// centre) to stderr, so a colour can be followed from Java to the GPU.
fn trace_particle_quads(particles: &[FfiWorldParticleQuadRequest]) {
    use std::sync::atomic::{AtomicU64, Ordering};
    static LIMIT: std::sync::OnceLock<usize> = std::sync::OnceLock::new();
    static FRAME: AtomicU64 = AtomicU64::new(0);
    let limit = *LIMIT.get_or_init(|| {
        crate::core::environment::var_os("MATTMC_TRACE_PARTICLE_QUADS")
            .and_then(|value| value.to_str().and_then(|value| value.parse().ok()))
            .unwrap_or(0)
    });
    if limit == 0 {
        return;
    }
    let frame = FRAME.fetch_add(1, Ordering::Relaxed);
    for (index, p) in particles.iter().take(limit).enumerate() {
        eprintln!(
            "[mattmc particle-trace] frame={frame} index={index} texture={} surface={} uv={:?} argb={:08x} light={:08x} center={:?} size={}",
            p.texture_id, p.surface_kind, p.uv_bounds, p.color_argb, p.packed_light, p.center, p.size
        );
    }
}

pub(crate) fn merge_particle_semantics(
    materials: Vec<WorldMaterialQuadRequest>,
    particles: &[FfiWorldParticleQuadRequest],
    viewport: [u32; 2],
) -> GalResult<Vec<WorldMaterialQuadRequest>> {
    let count = materials
        .len()
        .checked_add(particles.len())
        .filter(|&n| n <= FFI_MAX_BATCH_ITEMS)
        .ok_or_else(|| {
            GalError::invalid_argument("combined particle/material frame bound exceeded")
        })?;
    let material_count = materials.len();
    let mut source = materials.into_iter();
    let mut output = Vec::with_capacity(count);
    let mut cursor = 0;
    trace_particle_quads(particles);
    for p in particles {
        validate_item_size::<FfiWorldParticleQuadRequest>(p.byte_size, "particle semantics")?;
        let index = p.material_index as usize;
        if index < cursor || index > material_count {
            return Err(GalError::invalid_argument(
                "invalid particle surface or material ordering",
            ));
        }
        let surface = crate::render::worldrender::features::particle::ParticleSurface::from_wire(p.surface_kind)?;
        let quad = crate::render::worldrender::features::particle::ParticleQuad {
            center: p.center,
            rotation: p.rotation,
            size: p.size,
            uv_bounds: p.uv_bounds,
            texture_id: p.texture_id,
            translucent: surface.translucent(),
            color_argb: p.color_argb,
            packed_light: p.packed_light,
        };
        let quad = quad.lower_surface(surface, viewport)?;
        output.extend(source.by_ref().take(index - cursor));
        cursor = index;
        output.push(quad);
    }
    output.extend(source);
    Ok(output)
}

#[cfg(test)]
pub(crate) unsafe fn decode_whole_frame_submit(
    request: *const FfiWholeFrameSubmitRequest,
    capabilities: BackendCapabilities,
) -> GalResult<(u64, Handle, WorldPrimitiveFrame, Vec<GuiSpriteRequest>)> {
    let (generation, frame_target, frame, gui_sprites, _, _, _, _, _) =
        decode_whole_frame_submit_with_gui(request, capabilities)?;
    Ok((generation, frame_target, frame, gui_sprites))
}

#[cfg(test)]
pub(crate) unsafe fn decode_whole_frame_submit_with_gui(
    request: *const FfiWholeFrameSubmitRequest,
    capabilities: BackendCapabilities,
) -> GalResult<(
    u64,
    Handle,
    WorldPrimitiveFrame,
    Vec<GuiSpriteRequest>,
    Vec<GuiAffineQuadRequest>,
    Vec<GuiMeshBatchRequest>,
    i32,
    i32,
    Vec<u8>,
)> {
    let (generation, target, frame, sprites, affine, meshes, boundary, radius, effect, tiles) =
        decode_whole_frame_submit_with_backend_policy(request, capabilities)?;
    if !tiles.is_empty() {
        return Err(GalError::invalid_argument(
            "tiled GUI requires the typed whole-frame submit path",
        ));
    }
    Ok((
        generation, target, frame, sprites, affine, meshes, boundary, radius, effect,
    ))
}

pub(crate) unsafe fn decode_whole_frame_submit_with_tiled_gui(
    request: *const FfiWholeFrameSubmitRequest,
    capabilities: BackendCapabilities,
) -> GalResult<(
    u64,
    Handle,
    WorldPrimitiveFrame,
    Vec<GuiSpriteRequest>,
    Vec<GuiAffineQuadRequest>,
    Vec<GuiMeshBatchRequest>,
    i32,
    i32,
    Vec<u8>,
    Vec<GuiTiledQuadRequest>,
)> {
    decode_whole_frame_submit_with_backend_policy(request, capabilities)
}

pub(crate) unsafe fn decode_world_primitive_submit(
    request: *const FfiWholeFrameSubmitRequest,
    capabilities: BackendCapabilities,
) -> GalResult<(u64, Handle, WorldPrimitiveFrame)> {
    let (
        generation,
        frame_target,
        frame,
        gui_sprites,
        gui_affine_quads,
        gui_mesh_batches,
        gui_blur_before_stratum,
        gui_blur_radius,
        _post_effect_id,
        gui_tiled_quads,
    ) = decode_whole_frame_submit_with_backend_policy(request, capabilities)?;
    if !gui_sprites.is_empty()
        || !gui_affine_quads.is_empty()
        || !gui_mesh_batches.is_empty()
        || !gui_tiled_quads.is_empty()
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world primitive submit does not accept GUI work",
        ));
    }
    if gui_blur_before_stratum >= 0 {
        return Err(GalError::unsupported_feature(
            "GUI blur is unavailable on the world-only submit route",
        ));
    }
    if gui_blur_radius >= 0 && gui_blur_radius > 64 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI blur radius must be within the bounded range 0..=64",
        ));
    }
    Ok((generation, frame_target, frame))
}

pub(super) unsafe fn read_whole_frame_request(
    request: *const FfiWholeFrameSubmitRequest,
) -> GalResult<FfiWholeFrameSubmitRequest> {
    if request.is_null() {
        return Err(GalError::invalid_argument(
            "whole-frame submit request is null",
        ));
    }
    let header = read_struct(request.cast::<FfiHeader>(), "whole-frame header")?;
    validate_header::<FfiWholeFrameSubmitRequest>(header)?;
    read_struct(request, "whole-frame submit request")
}

pub(crate) fn merge_experience_orb_instances(
    meshes: Vec<WorldMeshInstanceRequest>,
    orbs: &[FfiWorldExperienceOrbInstanceRecord],
    viewport: [u32; 2],
) -> GalResult<Vec<WorldMeshInstanceRequest>> {
    let count = meshes
        .len()
        .checked_add(orbs.len())
        .filter(|&count| count <= FFI_MAX_BATCH_ITEMS)
        .ok_or_else(|| GalError::invalid_argument("combined mesh/orb frame bound exceeded"))?;
    if orbs.is_empty() {
        // Most frames carry no orbs; keep the decoded vector instead of
        // copying every mesh instance into a new one.
        return Ok(meshes);
    }
    let mesh_count = meshes.len();
    let mut placed = Vec::with_capacity(orbs.len());
    let mut cursor = 0;
    for orb in orbs {
        validate_item_size::<FfiWorldExperienceOrbInstanceRecord>(orb.byte_size, "orb placement")?;
        let index = orb.mesh_index as usize;
        if orb.reserved0 != 0 || index < cursor || index > mesh_count {
            return Err(GalError::invalid_argument(
                "invalid orb placement ordering or reserved bits",
            ));
        }
        let mut instance = crate::render::worldrender::features::experience_orb::ExperienceOrbPlacement {
            entity_transform: orb.entity_transform,
            camera_orientation: orb.camera_orientation,
            entity_id: orb.entity_id,
        }.instance(orb.mesh_key, orb.mesh_generation, viewport)?;
        instance.entity_culling = decode_entity_culling_record(orb.entity_culling_mode,
            orb.entity_culling_flags,orb.entity_culling_bounds,orb.entity_culling_leash_bounds,orb.entity_culling_camera)?;
        if orb.shadow_only > 1 || (orb.shadow_only == 1 && instance.entity_culling.is_none()) {
            return Err(GalError::invalid_argument("invalid orb shadow-only extraction role"));
        }
        if orb.shadow_only == 1 {
            instance.stratum = crate::render::scene::strata::WORLD_STRATUM_ENTITY_SHADOW_CASTER;
        }
        cursor = index;
        placed.push((index, instance));
    }
    // Merge in place from the back: only instances after the first orb move,
    // instead of copying the whole (mostly terrain) frame into a new vector.
    let mut meshes = meshes;
    meshes.extend(placed.iter().map(|(_, instance)| instance.clone()));
    let mut remaining_meshes = mesh_count;
    let mut write = count;
    while let Some(&(index, _)) = placed.last() {
        write -= 1;
        if index >= remaining_meshes {
            meshes[write] = placed.pop().expect("orb checked above").1;
        } else {
            remaining_meshes -= 1;
            meshes.swap(remaining_meshes, write);
        }
    }
    Ok(meshes)
}

pub(crate) unsafe fn decode_whole_frame_submit_with_backend_policy(
    request: *const FfiWholeFrameSubmitRequest,
    capabilities: BackendCapabilities,
) -> GalResult<(
    u64,
    Handle,
    WorldPrimitiveFrame,
    Vec<GuiSpriteRequest>,
    Vec<GuiAffineQuadRequest>,
    Vec<GuiMeshBatchRequest>,
    i32,
    i32,
    Vec<u8>,
    Vec<GuiTiledQuadRequest>,
)> {
    if request.is_null() {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "whole-frame submit request is null",
        ));
    }
    let request = read_whole_frame_request(request)?;
    let input_bytes = input_bytes_for_whole_frame(&request);
    if input_bytes > FFI_MAX_WHOLE_FRAME_INPUT_BYTES {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "whole-frame semantic request exceeds bounded input size {} bytes (got {})",
                FFI_MAX_WHOLE_FRAME_INPUT_BYTES, input_bytes
            ),
        ));
    }
    reject_unknown_feature_bits(request.negotiated_feature_bits)?;
    let supported = capability_feature_bits(capabilities);
    if request.negotiated_feature_bits & !supported != 0 {
        return Err(GalError::unsupported_feature(format!(
            "requested unsupported whole-frame feature bits 0x{:x}",
            request.negotiated_feature_bits & !supported
        )));
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
    if request.gui_width > GUI_MAX_VIEWPORT_AXIS
        || request.gui_height > GUI_MAX_VIEWPORT_AXIS
        || request.viewport_width > GUI_MAX_VIEWPORT_AXIS
        || request.viewport_height > GUI_MAX_VIEWPORT_AXIS
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "whole-frame GUI and viewport dimensions exceed bounded axis {GUI_MAX_VIEWPORT_AXIS}"
            ),
        ));
    }
    if request.gui_blur_before_stratum < -1 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI blur boundary must be -1 or a non-negative source stratum index",
        ));
    }
    if request.gui_blur_radius < -1 || request.gui_blur_radius > 64 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI blur radius must be -1 or within the bounded range 0..=64",
        ));
    }
    let post_effect_id = read_bounded_bytes(
        request.post_effect_id,
        true,
        256,
        "whole-frame post-effect id",
    )?;
    if !post_effect_id.is_empty() {
        let id = std::str::from_utf8(&post_effect_id).map_err(|_| {
            GalError::ffi(
                StatusCode::InvalidArgument,
                "whole-frame post-effect id must be UTF-8",
            )
        })?;
        if id.trim().is_empty() || id.chars().any(char::is_control) {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "whole-frame post-effect id is empty or contains control characters",
            ));
        }
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
    let voxel_volume = decode_world_voxel_volume_frame(request.voxel_volume_frame)?;
    let shader_environment =
        decode_world_shader_environment_frame(request.shader_environment_frame)?;
    let feature_coverage = decode_world_feature_coverage(request.world_feature_coverage)?;
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
        let viewport_width = decode_world_viewport_axis(
            segment.viewport_width,
            "world primitive segment viewport width",
        )?;
        let viewport_height = decode_world_viewport_axis(
            segment.viewport_height,
            "world primitive segment viewport height",
        )?;
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
        let viewport_width =
            decode_world_viewport_axis(quad.viewport_width, "world crack quad viewport width")?;
        let viewport_height =
            decode_world_viewport_axis(quad.viewport_height, "world crack quad viewport height")?;
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
        let viewport_width =
            decode_world_viewport_axis(quad.viewport_width, "world border quad viewport width")?;
        let viewport_height =
            decode_world_viewport_axis(quad.viewport_height, "world border quad viewport height")?;
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
    if request.world_particle_quads.count > FFI_MAX_BATCH_ITEMS as u64 {
        return Err(GalError::invalid_argument(
            "particle semantic count exceeds frame bound",
        ));
    }
    let raw_particles = read_slice(
        request.world_particle_quads,
        true,
        "world particle semantics",
    )?;
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
    let raw_dh_generic_boxes = read_slice(
        request.world_distant_horizons_generic_boxes,
        true,
        "DH generic box semantics",
    )?;
    if raw_dh_generic_boxes.len() > FFI_MAX_BATCH_ITEMS / DH_GENERIC_BOX_FACE_COUNT {
        return Err(GalError::invalid_argument(
            "DH generic box semantic count exceeds expanded frame bound",
        ));
    }
    let raw_dh_generic_group_instances = read_slice(
        request.world_dh_generic_group_instances,
        true,
        "DH generic group instances",
    )?;
    let retained_dh_generic_boxes = super::dh_generic_groups::expand_dh_generic_groups(
        raw_dh_generic_group_instances,
        request.dh_generic_camera,
    )?;
    if raw_dh_generic_boxes.len() + retained_dh_generic_boxes.len()
        > FFI_MAX_BATCH_ITEMS / DH_GENERIC_BOX_FACE_COUNT
    {
        return Err(GalError::invalid_argument(
            "DH generic box semantic count exceeds expanded frame bound",
        ));
    }
    // Material quads have their own frame bound (clouds at the default
    // cloud range exceed the generic 65,536 FFI item bound).
    let max_material_quads = crate::render::worldrender::WORLD_MAX_MATERIAL_QUADS;
    if raw_materials.len() > max_material_quads {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "world primitive material quad count {} exceeds max {}",
                raw_materials.len(),
                max_material_quads
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
    if raw_compact_materials.len() > max_material_quads {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "world primitive compact material quad count {} exceeds max {}",
                raw_compact_materials.len(),
                max_material_quads
            ),
        ));
    }
    let material_quad_count = raw_materials
        .len()
        .checked_add(raw_compact_materials.len())
        .and_then(|count| count.checked_add(raw_particles.len()))
        .and_then(|count| {
            count.checked_add(
                (raw_dh_generic_boxes.len() + retained_dh_generic_boxes.len()) * DH_GENERIC_BOX_FACE_COUNT,
            )
        })
        .ok_or_else(|| {
            GalError::ffi(
                StatusCode::LengthOverflow,
                "world material quad count overflows",
            )
        })?;
    if material_quad_count > max_material_quads {
        return Err(GalError::ffi(
            StatusCode::LengthOverflow,
            format!(
                "world material quad count {} exceeds max {}",
                material_quad_count, max_material_quads
            ),
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
    // Full records carry the exceptional per-vertex modulation path while
    // compact records remain the normal steady-state representation. A frame
    // may contain both; each form is decoded and validated independently.
    let mut material_quads = Vec::with_capacity(raw_materials.len() + raw_compact_materials.len());
    for quad in raw_materials {
        validate_item_size::<FfiWorldMaterialQuadRequest>(
            quad.byte_size,
            "world primitive material quad",
        )?;
        if !matches!(
            quad.stratum,
            WORLD_STRATUM_OPAQUE_TEXTURED_GEOMETRY | WORLD_STRATUM_DH_GENERIC
        ) {
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
                    format!(
                        "unknown legacy world material quad texture id {}",
                        quad.texture_id
                    ),
                )
            })?;
        if quad.material_mode != WORLD_MATERIAL_MODE_OPAQUE
            && quad.material_mode != WORLD_MATERIAL_MODE_CUTOUT
            && quad.material_mode != WORLD_MATERIAL_MODE_TRANSLUCENT
            && quad.material_mode != WORLD_MATERIAL_MODE_TRANSLUCENT_CUTOUT
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
        let winding = if quad.winding == 0 {
            WORLD_WINDING_CCW
        } else {
            quad.winding
        };
        if winding != WORLD_WINDING_CCW && winding != WORLD_WINDING_CW {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!("unknown world material winding {}", winding),
            ));
        }
        if !matches!(
            quad.source_program,
            WORLD_MATERIAL_SOURCE_UNSPECIFIED
                | WORLD_MATERIAL_SOURCE_TEXTURED
                | WORLD_MATERIAL_SOURCE_ENTITY_MODEL
                | WORLD_MATERIAL_SOURCE_PARTICLES
                | WORLD_MATERIAL_SOURCE_WEATHER
                | WORLD_MATERIAL_SOURCE_CLOUDS
        ) {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!(
                    "unknown world material source program {}",
                    quad.source_program
                ),
            ));
        }
        if !matches!(
            quad.source_uv_space,
            WORLD_MATERIAL_SOURCE_UV_LOCAL_TEXTURE | WORLD_MATERIAL_SOURCE_UV_MINECRAFT_BLOCK_ATLAS
        ) {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!(
                    "unknown world material source UV space {}",
                    quad.source_uv_space
                ),
            ));
        }
        if !world_material_semantics::source_program_supports_uv_space(
            quad.source_program,
            quad.source_uv_space,
        ) {
            return Err(GalError::unsupported_feature(
                "weather and cloud material quads require Rust-owned local texture UV semantics",
            ));
        }
        if !world_material_semantics::texture_supports_uv_space(texture_id, quad.source_uv_space) {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "world material texture {} requires Minecraft atlas UV semantics",
                    quad.texture_id
                ),
            ));
        }
        let viewport_width =
            decode_world_viewport_axis(quad.viewport_width, "world material quad viewport width")?;
        let viewport_height = decode_world_viewport_axis(
            quad.viewport_height,
            "world material quad viewport height",
        )?;
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
            source_program: quad.source_program,
            source_uv_space: quad.source_uv_space,
            source_color_argb: quad.source_color_argb,
            packed_light: quad.packed_light,
            vertex_color_argb: [
                quad.vertex0_color_argb,
                quad.vertex1_color_argb,
                quad.vertex2_color_argb,
                quad.vertex3_color_argb,
            ],
            vertex_packed_light: [
                quad.vertex0_packed_light,
                quad.vertex1_packed_light,
                quad.vertex2_packed_light,
                quad.vertex3_packed_light,
            ],
            block_entity_id: quad.block_entity_id,
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
            if !matches!(
                record.stratum,
                WORLD_STRATUM_OPAQUE_TEXTURED_GEOMETRY | WORLD_STRATUM_DH_GENERIC
            ) {
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
                && record.material_mode != WORLD_MATERIAL_MODE_TRANSLUCENT
                && record.material_mode != WORLD_MATERIAL_MODE_TRANSLUCENT_CUTOUT
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
            if !matches!(
                record.source_program,
                WORLD_MATERIAL_SOURCE_UNSPECIFIED
                    | WORLD_MATERIAL_SOURCE_TEXTURED
                    | WORLD_MATERIAL_SOURCE_ENTITY_MODEL
                    | WORLD_MATERIAL_SOURCE_PARTICLES
                    | WORLD_MATERIAL_SOURCE_WEATHER
                    | WORLD_MATERIAL_SOURCE_CLOUDS
            ) {
                return Err(GalError::ffi(
                    StatusCode::UnknownEnum,
                    format!(
                        "unknown compact world material source program {}",
                        record.source_program
                    ),
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
            if !matches!(
                quad.source_uv_space,
                WORLD_MATERIAL_SOURCE_UV_LOCAL_TEXTURE
                    | WORLD_MATERIAL_SOURCE_UV_MINECRAFT_BLOCK_ATLAS
            ) {
                return Err(GalError::ffi(
                    StatusCode::UnknownEnum,
                    format!(
                        "unknown compact world material source UV space {}",
                        quad.source_uv_space
                    ),
                ));
            }
            if !world_material_semantics::source_program_supports_uv_space(
                key.source_program,
                quad.source_uv_space,
            ) {
                return Err(GalError::unsupported_feature(
                    "weather and cloud material quads require Rust-owned local texture UV semantics",
                ));
            }
            if !world_material_semantics::texture_supports_uv_space(
                key.texture_id,
                quad.source_uv_space,
            ) {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    format!(
                        "compact world material texture {} requires Minecraft atlas UV semantics",
                        key.texture_id
                    ),
                ));
            }
            if quad.block_entity_id < -1 {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    format!(
                        "compact world material quad block entity id must be >= -1, got {}",
                        quad.block_entity_id
                    ),
                ));
            }
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
                source_program: key.source_program,
                source_uv_space: quad.source_uv_space,
                source_color_argb: quad.source_color_argb,
                packed_light: quad.packed_light,
                vertex_color_argb: [quad.source_color_argb; 4],
                vertex_packed_light: [quad.packed_light; 4],
                block_entity_id: quad.block_entity_id,
            });
        }
    }
    let mut decoded_dh_generic_boxes = decode_dh_generic_box_semantics(raw_dh_generic_boxes)?;
    decoded_dh_generic_boxes.extend(retained_dh_generic_boxes);
    if !raw_particles.is_empty() {
        material_quads = merge_particle_semantics(
            material_quads,
            raw_particles,
            [
                decode_world_viewport_axis(request.viewport_width, "particle viewport width")?,
                decode_world_viewport_axis(request.viewport_height, "particle viewport height")?,
            ],
        )?;
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
    if request.world_experience_orbs.count > (FFI_MAX_BATCH_ITEMS - raw_mesh_instances.len()) as u64
    {
        return Err(GalError::invalid_argument(
            "combined mesh/orb frame bound exceeded",
        ));
    }
    let raw_orbs = read_slice(request.world_experience_orbs, true, "orb placements")?;
    // Reserve for the orbs merged below and the shadow casters the frontend
    // appends after decoding, so neither reallocates the decoded stream.
    let appended_capacity = usize::try_from(
        request
            .world_static_terrain_shadow_casters
            .count
            .saturating_add(request.world_experience_orbs.count),
    )
    .unwrap_or(FFI_MAX_BATCH_ITEMS)
    .min(FFI_MAX_BATCH_ITEMS);
    let raw_rig_poses = read_slice(request.world_model_rig_poses, true, "model rig poses")?;
    let mut mesh_instances = Vec::with_capacity(raw_mesh_instances.len() + appended_capacity);
    let mut rig_parts = Vec::new();
    let mut rig_models = Vec::new();
    // Locked once, on the frame's first rig instance.
    let mut rig_table: Option<super::model_rigs::ModelRigTable> = None;
    for raw_instance in raw_mesh_instances {
        validate_item_size::<FfiWorldMeshInstanceRecord>(
            raw_instance.byte_size,
            "world primitive mesh instance",
        )?;
        let resolved_item = unsafe { super::item_poses::resolve_item_pose(raw_instance,false)? };
        let raw_instance = &*resolved_item;
        // A model-rig instance stands for its drawn parts, in place.
        let expanded = if raw_instance.flags & super::model_rigs::WORLD_MESH_INSTANCE_FLAG_MODEL_RIG != 0 {
            rig_parts.clear();
            if rig_table.is_none() {
                rig_table = Some(super::model_rigs::lock_model_rigs()?);
            }
            let rigs = rig_table.as_deref().expect("model rig table locked above");
            super::model_rigs::expand_model_rig(rigs, raw_instance, raw_rig_poses, &mut rig_models, &mut rig_parts)?;
            mesh_instances.reserve(rig_parts.len());
            if mesh_instances.len() + rig_parts.len() > FFI_MAX_BATCH_ITEMS {
                return Err(GalError::invalid_argument("expanded model rig instances exceed the frame bound"));
            }
            &rig_parts[..]
        } else {
            std::slice::from_ref(raw_instance)
        };
        for instance in expanded {
            validate_mesh_instance_semantic_identity(instance, "world primitive mesh instance")?;
            if !is_world_mesh_stratum(instance.stratum) {
                return Err(GalError::ffi(
                    StatusCode::UnknownEnum,
                    format!("unknown world mesh stratum {}", instance.stratum),
                ));
            }
            if instance.depth_policy != WORLD_DEPTH_POLICY_DISABLED
                && instance.depth_policy != WORLD_DEPTH_POLICY_TEST_WRITE
                && instance.depth_policy != WORLD_DEPTH_POLICY_TEST_NO_WRITE
                && !(instance.depth_policy == WORLD_DEPTH_POLICY_TEST_EQUAL_WRITE
                    && instance.stratum == WORLD_STRATUM_ENTITY_MESH)
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
            let viewport_width =
                decode_world_viewport_axis(instance.viewport_width, "world mesh viewport width")?;
            let viewport_height =
                decode_world_viewport_axis(instance.viewport_height, "world mesh viewport height")?;
            let transform = decode_mesh_instance_transform(instance)?;
            mesh_instances.push(WorldMeshInstanceRequest {
                entity_culling: decode_entity_culling(instance, false)?,

                model_submission_order: decode_model_submission_order(instance)?,
                item_foil: decode_world_item_foil(instance)?,
                decal_foil: decode_world_decal_foil(instance, false)?,
                stratum: instance.stratum,
                mesh_key: instance.mesh_key,
                mesh_generation: instance.mesh_generation,
                mesh_section_index: instance.mesh_section_index,
                terrain_visible_facing_mask: terrain_visible_facing_mask(instance),
                depth_policy: instance.depth_policy,
                cull_policy: instance.cull_policy,
                winding: instance.winding,
                color_argb: instance.color_argb,
                entity_id: instance.entity_id,
                entity_color_argb: instance.entity_color_argb,
                packed_light: instance.packed_light,
                transform,
                outline_color_argb: instance.outline_color_argb,
                flags: instance.flags,
                block_entity_id: instance.block_entity_id,
                viewport_width,
                viewport_height,
            });
        }
    }
    if !raw_orbs.is_empty() {
        mesh_instances = merge_experience_orb_instances(
            mesh_instances,
            raw_orbs,
            [
                decode_world_viewport_axis(request.viewport_width, "orb viewport width")?,
                decode_world_viewport_axis(request.viewport_height, "orb viewport height")?,
            ],
        )?;
    }
    let static_terrain_shadow_casters =
        decode_static_terrain_shadow_casters(&request, mesh_instances.len())?;
    let static_terrain_sections = decode_static_terrain_sections(
        &request,
        mesh_instances.len() + static_terrain_shadow_casters.casters.len(),
    )?;
    let raw_text_quads = read_slice(request.world_text_quads, true, "world text quads")?;
    if raw_text_quads.len() > FFI_MAX_BATCH_ITEMS {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "world text quad count {} exceeds max {}",
                raw_text_quads.len(),
                FFI_MAX_BATCH_ITEMS
            ),
        ));
    }
    let mut text_quads = Vec::with_capacity(raw_text_quads.len());
    for quad in raw_text_quads {
        validate_item_size::<FfiWorldTextQuadRequest>(quad.byte_size, "world text quad")?;
        if quad.flags & !1 != 0 || quad.reserved0 != 0 {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "world text quad has unknown flags or a non-zero reserved field",
            ));
        }
        if !matches!(
            quad.depth_policy,
            WORLD_TEXT_DEPTH_SEE_THROUGH
                | WORLD_TEXT_DEPTH_NORMAL
                | WORLD_TEXT_DEPTH_POLYGON_OFFSET
        ) {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!("unknown world text depth policy {}", quad.depth_policy),
            ));
        }
        if quad.asset_id == 0 || quad.atlas_generation == 0 || quad.atlas_revision == 0 {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "world text quad requires non-zero atlas identity, generation, and revision",
            ));
        }
        if !quad.distance_to_camera_sq.is_finite()
            || quad.distance_to_camera_sq < 0.0
            || quad
                .model_view_matrix
                .iter()
                .chain(quad.positions.iter())
                .chain(quad.uvs.iter())
                .any(|value| !value.is_finite())
        {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "world text quad contains invalid geometry",
            ));
        }
        if quad.block_entity_id < -1 {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "world text quad block entity id must be >= -1, got {}",
                    quad.block_entity_id
                ),
            ));
        }
        text_quads.push(WorldTextQuadRequest {
            asset_id: quad.asset_id,
            atlas_generation: quad.atlas_generation,
            atlas_revision: quad.atlas_revision,
            colored: quad.flags & 1 != 0,
            depth_policy: quad.depth_policy,
            packed_light: quad.packed_light,
            distance_to_camera_sq: quad.distance_to_camera_sq,
            model_view_matrix: quad.model_view_matrix,
            positions: [
                [quad.positions[0], quad.positions[1], quad.positions[2]],
                [quad.positions[3], quad.positions[4], quad.positions[5]],
                [quad.positions[6], quad.positions[7], quad.positions[8]],
                [quad.positions[9], quad.positions[10], quad.positions[11]],
            ],
            uvs: [
                [quad.uvs[0], quad.uvs[1]],
                [quad.uvs[2], quad.uvs[3]],
                [quad.uvs[4], quad.uvs[5]],
                [quad.uvs[6], quad.uvs[7]],
            ],
            color_argb: quad.color_argb,
            block_entity_id: quad.block_entity_id,
        });
    }
    let lod_render_frame = decode_world_lod_render_frame(request.world_lod_render_frame)?;
    let lod_instances = if request.world_lod_frame_id != 0 {
        if request.world_lod_instances.count != 0 || request.world_lod_frame_count == 0
            || request.world_lod_frame_count > WORLD_LOD_MAX_VISIBLE_SEGMENTS as u64 {
            return Err(GalError::invalid_argument("invalid native DH frame reference or mixed inline LOD work"));
        }
        let ledger = crate::render::dh_collector::ledger().lock()
            .map_err(|_| GalError::invalid_argument("DH frame ledger is poisoned"))?;
        ledger.resolve_retained_frame(request.world_lod_frame_id, request.world_lod_frame_lifecycle,
            request.world_lod_frame_count, lod_render_frame.enabled, lod_render_frame.flags)
            .ok_or_else(|| GalError::invalid_argument("native DH frame identity, lifecycle, count or selection is stale"))?
    } else {
        if request.world_lod_frame_lifecycle != 0 || request.world_lod_frame_count != 0 {
            return Err(GalError::invalid_argument("absent native DH frame has reference metadata"));
        }
        let raw_lod_instances = read_slice(request.world_lod_instances, true, "world primitive LOD instances")?;
        if raw_lod_instances.len() > WORLD_LOD_MAX_VISIBLE_SEGMENTS {
            return Err(GalError::ffi(StatusCode::InvalidArgument, format!(
                "world primitive LOD instance count {} exceeds max {}",
                raw_lod_instances.len(), WORLD_LOD_MAX_VISIBLE_SEGMENTS)));
        }
        let mut instances = Vec::with_capacity(raw_lod_instances.len());
        for instance in raw_lod_instances {
            validate_item_size::<FfiWorldLodColumnInstanceRecord>(instance.byte_size, "world primitive LOD instance")?;
            instances.push(WorldLodColumnInstanceRequest { column_key: instance.column_key,
                column_generation: instance.column_generation, layer: instance.layer,
                segment_index: instance.segment_index, order: instance.order });
        }
        instances.into()
    };
    // DH may publish generic-object callbacks during the transition before
    // its private route is selected for this exact world frame. The copied
    // records are validated above, but have no admitted destination yet.
    let dh_generic_boxes = if lod_render_frame.rust_route_selected() {
        decoded_dh_generic_boxes
    } else {
        Vec::new()
    };
    let first_person = decode_world_first_person_frame(request.world_first_person_frame)?;
    let first_person_mesh_instances = unsafe {
        decode_world_first_person_mesh_instances(
            request.world_first_person_mesh_instances,
            &first_person,
        )?
    };
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
        affine_quads: request.gui_affine_quads,
        negotiated_feature_bits: request.negotiated_feature_bits,
        mesh_batches: request.gui_mesh_batches,
        gui_projection_width: request.gui_projection_width,
        gui_projection_height: request.gui_projection_height,
        tiled_quads: request.gui_tiled_quads,
    };
    let (_, _, gui_sprites, gui_affine_quads, gui_mesh_batches, gui_tiled_quads) =
        decode_gui_frame_submit_with_tiles(&raw_gui, capabilities)?;
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
            engine_globals: match request.engine_globals_present {
                0 => None,
                1 => {
                    let values =
                        crate::render::shaderpack::vanilla::engine_globals::EngineGlobals {
                            screen_width: request.engine_screen_width,
                            screen_height: request.engine_screen_height,
                            game_ticks: request.engine_game_ticks,
                            partial_tick: request.engine_partial_tick,
                            glint_alpha: request.engine_glint_alpha,
                            menu_blur_radius: request.engine_menu_blur_radius,
                        };
                    values.uniforms()?;
                    Some(values)
                }
                _ => {
                    return Err(GalError::invalid_argument(
                        "engine Globals presence must be zero or one",
                    ))
                }
            },
            frame_id: request.frame_id,
            correlation_id: request.correlation_id,
            viewport_width,
            viewport_height,
            view_matrix: request.view_matrix,
            projection_matrix: request.projection_matrix,
            voxel_volume,
            shader_environment,
            feature_coverage,
            first_person,
            first_person_mesh_instances,
            background,
            segments,
            crack_quads,
            border_quads,
            material_quads,
            dh_generic_boxes,
            mesh_instances,
            text_quads,
            lod_instances,
            lod_render_frame,
            static_terrain_shadow_casters,
            static_terrain_sections,
        },
        gui_sprites,
        gui_affine_quads,
        gui_mesh_batches,
        request.gui_blur_before_stratum,
        request.gui_blur_radius,
        post_effect_id,
        gui_tiled_quads,
    ))
}

/// Copies the compact caster stream. Expansion into shadow-only instances
/// needs the frontend's acknowledged mesh generations, so it happens there.
fn decode_static_terrain_sections(
    request: &FfiWholeFrameSubmitRequest,
    instance_count: usize,
) -> GalResult<crate::render::worldrender::StaticTerrainSections> {
    if request.world_static_terrain_sections.count
        > FFI_MAX_BATCH_ITEMS.saturating_sub(instance_count) as u64
    {
        return Err(GalError::invalid_argument(
            "combined mesh/static-terrain frame bound exceeded",
        ));
    }
    let raw = unsafe {
        read_slice(
            request.world_static_terrain_sections,
            true,
            "static terrain sections",
        )?
    };
    if raw.is_empty() {
        return Ok(Default::default());
    }
    let camera = request.static_terrain_camera;
    if camera.iter().any(|axis| !axis.is_finite()) {
        return Err(GalError::invalid_argument(
            "static terrain sections require a finite terrain camera",
        ));
    }
    let mut sections = Vec::with_capacity(raw.len());
    for section in raw {
        if section.mesh_key == 0 || section.mesh_generation == 0 {
            return Err(GalError::invalid_argument(
                "static terrain section key and generation must be non-zero",
            ));
        }
        if section.depth_policy != WORLD_DEPTH_POLICY_TEST_WRITE
            && section.depth_policy != WORLD_DEPTH_POLICY_TEST_NO_WRITE
        {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!("unknown static terrain section depth policy {}", section.depth_policy),
            ));
        }
        if section.flags & !WORLD_MESH_INSTANCE_FLAG_CAMERA_SORTED_QUADS != 0 || section.reserved != 0 {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!("static terrain section has unsupported flags {:#x}", section.flags),
            ));
        }
        sections.push(crate::render::worldrender::StaticTerrainSection {
            mesh_key: section.mesh_key,
            depth_policy: section.depth_policy,
            origin: section.origin,
            flags: section.flags,
        });
    }
    Ok(crate::render::worldrender::StaticTerrainSections { camera, sections })
}

fn decode_static_terrain_shadow_casters(
    request: &FfiWholeFrameSubmitRequest,
    mesh_instance_count: usize,
) -> GalResult<crate::render::worldrender::StaticTerrainShadowCasters> {
    if request.world_static_terrain_shadow_casters.count
        > FFI_MAX_BATCH_ITEMS.saturating_sub(mesh_instance_count) as u64
    {
        return Err(GalError::invalid_argument(
            "combined mesh/shadow-caster frame bound exceeded",
        ));
    }
    let raw = unsafe {
        read_slice(
            request.world_static_terrain_shadow_casters,
            true,
            "static terrain shadow casters",
        )?
    };
    if raw.is_empty() {
        return Ok(Default::default());
    }
    let camera = request.static_terrain_camera;
    if camera.iter().any(|axis| !axis.is_finite()) {
        return Err(GalError::invalid_argument(
            "static terrain shadow casters require a finite terrain camera",
        ));
    }
    let mut casters = Vec::with_capacity(raw.len());
    for caster in raw {
        if caster.mesh_key == 0 || caster.mesh_generation == 0 {
            return Err(GalError::invalid_argument(
                "static terrain shadow caster key and generation must be non-zero",
            ));
        }
        if caster.depth_policy != WORLD_DEPTH_POLICY_TEST_WRITE
            && caster.depth_policy != WORLD_DEPTH_POLICY_TEST_NO_WRITE
        {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!("unknown shadow caster depth policy {}", caster.depth_policy),
            ));
        }
        casters.push(crate::render::worldrender::StaticTerrainShadowCaster {
            mesh_key: caster.mesh_key,
            mesh_generation: caster.mesh_generation,
            origin: caster.origin,
            depth_policy: caster.depth_policy,
        });
    }
    Ok(crate::render::worldrender::StaticTerrainShadowCasters { camera, casters })
}

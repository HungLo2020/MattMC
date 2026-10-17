//! Validation of frames, mesh/LOD assets, instances and shader environments before any GPU work.

use crate::render::worldrender::*;

pub(in crate::render::worldrender) fn validate_frame(frame: &WorldPrimitiveFrame) -> GalResult<()> {
    if !frame.static_terrain_shadow_casters.casters.is_empty()
        || !frame.static_terrain_sections.sections.is_empty()
    {
        return Err(GalError::invalid_argument(
            "static terrain sections and casters must be admitted before frame validation",
        ));
    }
    frame::header::validate_frame_header(frame)?;
    validate_shader_environment(&frame.shader_environment)?;
    validate_first_person_frame(&frame.first_person)?;
    if !frame.first_person_mesh_instances.is_empty() && !frame.first_person.enabled {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "first-person mesh instances require an enabled first-person frame",
        ));
    }
    if usize::try_from(frame.first_person.main_hand_instance_count)
        .ok()
        .is_none_or(|count| count > frame.first_person_mesh_instances.len())
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "first-person main-hand instance count {} exceeds {} copied instances",
                frame.first_person.main_hand_instance_count,
                frame.first_person_mesh_instances.len()
            ),
        ));
    }
    validate_lod_render_frame(&frame.lod_render_frame, !frame.lod_instances.is_empty())?;
    if frame.dh_generic_boxes.len() > 10_000
        || (!frame.dh_generic_boxes.is_empty() && !frame.lod_render_frame.rust_route_selected())
    {
        return Err(GalError::invalid_argument(
            "DH generic boxes require a selected bounded DH route",
        ));
    }
    for item in &frame.dh_generic_boxes {
        if item
            .min
            .iter()
            .chain(item.max.iter())
            .chain(item.shading.iter())
            .any(|value| !value.is_finite())
            || item
                .min
                .iter()
                .zip(item.max.iter())
                .any(|(min, max)| min > max)
        {
            return Err(GalError::invalid_argument(
                "DH generic box bounds and shading must be finite and ordered",
            ));
        }
    }
    if frame.lod_instances.len() > WORLD_LOD_MAX_VISIBLE_SEGMENTS {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "world LOD visible segment count {} exceeds bounded limit {WORLD_LOD_MAX_VISIBLE_SEGMENTS}",
                frame.lod_instances.len()
            ),
        ));
    }
    frame::background::validate_background(frame)?;
    for segment in &frame.segments {
        features::outline::validate_segment(segment, frame)?;
    }
    for quad in &frame.crack_quads {
        frame::crack_quads::validate_quad(quad, frame)?;
    }
    for quad in &frame.border_quads {
        frame::border_quads::validate_quad(quad, frame)?;
    }
    for quad in &frame.material_quads {
        frame::material_quads::validate_quad(quad, frame)?;
    }
    for instance in &frame.mesh_instances {
        validate_mesh_instance(instance, frame)?;
    }
    for instance in &frame.first_person_mesh_instances {
        if instance.entity_culling.is_some() {
            return Err(GalError::invalid_argument("first-person meshes cannot carry world entity culling"));
        }
        validate_mesh_instance(instance, frame)?;
        if instance.stratum != WORLD_STRATUM_ENTITY_MESH {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "first-person mesh instances must use the generic entity-mesh semantic stratum",
            ));
        }
    }
    Ok(())
}

pub(in crate::render::worldrender) fn validate_first_person_frame(frame: &WorldFirstPersonFrame) -> GalResult<()> {
    if !frame.enabled {
        if *frame != WorldFirstPersonFrame::default() {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "disabled world first-person frame must be zeroed",
            ));
        }
        return Ok(());
    }
    if !frame.clear_depth_before {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "enabled world first-person frame must explicitly clear its depth domain",
        ));
    }
    if frame
        .projection_matrix
        .iter()
        .any(|value| !value.is_finite())
        || frame.projection_matrix.iter().all(|value| *value == 0.0)
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "enabled world first-person frame requires a finite non-zero projection matrix",
        ));
    }
    if frame
        .model_view_matrix
        .iter()
        .any(|value| !value.is_finite())
        || frame.model_view_matrix.iter().all(|value| *value == 0.0)
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "enabled world first-person frame requires a finite non-zero model-view matrix",
        ));
    }
    Ok(())
}

pub(in crate::render::worldrender) fn validate_lod_render_frame(frame: &WorldLodRenderFrame, required: bool) -> GalResult<()> {
    if !frame.enabled {
        if *frame != WorldLodRenderFrame::default() {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "disabled world LOD render frame must be zeroed",
            ));
        }
        if required {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "visible world LOD instances require resolved world LOD render semantics",
            ));
        }
        return Ok(());
    }
    if frame.flags & !0xff != 0
        || frame.micro_offset <= 0.0
        || frame.clip_distance < 0.0
        || frame
            .combined_matrix
            .iter()
            .chain([
                &frame.clip_distance,
                &frame.micro_offset,
                &frame.noise_intensity,
                &frame.earth_radius,
            ])
            .any(|value| !value.is_finite())
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "enabled world LOD render frame has invalid semantic values",
        ));
    }
    Ok(())
}

pub(in crate::render::worldrender) fn validate_shader_environment(environment: &WorldShaderEnvironmentFrame) -> GalResult<()> {
    if !environment.enabled {
        // Ordinary mesh lighting is independent from shader-pack execution.
        // Permit just a copied world/lightmap record while every source-policy
        // field stays default.
        if environment.vanilla_lightmap.is_none() {
            if *environment != WorldShaderEnvironmentFrame::default() {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    "disabled shader environment without a lightmap must be zeroed",
                ));
            }
            return Ok(());
        }
        if environment.world_generation == 0 {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "lightmap-only shader environment requires a world generation",
            ));
        }
        let mut allowed = WorldShaderEnvironmentFrame::default();
        allowed.world_generation = environment.world_generation;
        allowed.vanilla_lightmap = environment.vanilla_lightmap;
        if *environment != allowed {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "disabled shader environment may carry only world generation and vanilla lightmap semantics",
            ));
        }
        environment
            .vanilla_lightmap
            .expect("checked above")
            .validate()?;
        return Ok(());
    }
    if environment.world_generation == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "enabled shader environment frame requires a world generation",
        ));
    }
    for (label, value) in [
        ("time of day", environment.time_of_day),
        ("rain strength", environment.rain_strength),
        ("thunder strength", environment.thunder_strength),
        ("sky darken", environment.sky_darken),
    ] {
        if !value.is_finite() || !(0.0..=1.0).contains(&value) {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!("shader environment {label} must be finite and within [0, 1]"),
            ));
        }
    }
    if !(0..720_720).contains(&environment.frame_counter) {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "shader environment frame counter must be within [0, 720720)",
        ));
    }
    if !(0.0..3600.0).contains(&environment.frame_time_counter) {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "shader environment frame time counter must be within [0, 3600)",
        ));
    }
    if !environment.frame_time_seconds.is_finite()
        || !(0.0..3600.0).contains(&environment.frame_time_seconds)
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "shader environment frame time must be finite and within [0, 3600)",
        ));
    }
    if !(0..=7).contains(&environment.moon_phase) {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "shader environment moon phase must be within [0, 7]",
        ));
    }
    if !(0..=3).contains(&environment.eye_submersion) {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "shader environment eye submersion must be within [0, 3]",
        ));
    }
    if !environment.screen_brightness.is_finite() || environment.screen_brightness < 0.0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "shader environment screen brightness must be finite and non-negative",
        ));
    }
    if !environment.darkness_light_factor.is_finite() || environment.darkness_light_factor < 0.0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "shader environment darkness light factor must be finite and non-negative",
        ));
    }
    for (label, value) in [
        ("blindness", environment.blindness),
        ("darkness factor", environment.darkness_factor),
    ] {
        if !value.is_finite() || !(0.0..=1.0).contains(&value) {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!("shader environment {label} must be finite and within [0, 1]"),
            ));
        }
    }
    if environment
        .eye_brightness
        .iter()
        .any(|value| !(0..=240).contains(value) || value % 16 != 0)
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "shader environment eye brightness must contain packed vanilla light values in [0, 240]",
        ));
    }
    if !environment.night_vision.is_finite() || !(0.0..=1.0).contains(&environment.night_vision) {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "shader environment night vision must be finite and within [0, 1]",
        ));
    }
    if let Some(lightmap) = environment.vanilla_lightmap {
        lightmap.validate()?;
    }
    if !environment.far_plane.is_finite() || environment.far_plane <= 0.0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "shader environment far plane must be finite and positive",
        ));
    }
    if environment
        .relative_eye_position
        .iter()
        .any(|value| !value.is_finite())
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "shader environment relative eye position must be finite",
        ));
    }
    if environment
        .sky_color
        .iter()
        .any(|value| !value.is_finite() || !(0.0..=1.0).contains(value))
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "shader environment sky color must be finite and within [0, 1]",
        ));
    }
    for (label, value) in [
        (
            "main-hand item-model resource location",
            &environment.main_hand_item_model_resource_location,
        ),
        (
            "off-hand item-model resource location",
            &environment.off_hand_item_model_resource_location,
        ),
    ] {
        if !value.is_empty() && canonical_resource_location(value).is_err() {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "shader environment {label} must be empty or canonical namespace:path text"
                ),
            ));
        }
    }
    for (label, emission) in [
        (
            "main-hand item light emission",
            environment.main_hand_item_light_emission,
        ),
        (
            "off-hand item light emission",
            environment.off_hand_item_light_emission,
        ),
    ] {
        if !(0..=15).contains(&emission) {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!("shader environment {label} must be within [0, 15]"),
            ));
        }
    }
    Ok(())
}

pub(in crate::render::worldrender) fn validate_mesh_instance(
    instance: &WorldMeshInstanceRequest,
    frame: &WorldPrimitiveFrame,
) -> GalResult<()> {
    if let Some(inputs) = instance.entity_culling {
        inputs.validate()?;
        if !matches!(instance.stratum, WORLD_STRATUM_ENTITY_MESH | WORLD_STRATUM_ENTITY_SHADOW_CASTER)
            || instance.block_entity_id != -1 {
            return Err(GalError::invalid_argument("entity culling requires an entity draw domain"));
        }
    }
    if instance.model_submission_order.is_some() && instance.stratum != WORLD_STRATUM_ENTITY_MESH {
        return Err(GalError::invalid_argument(
            "model submission order requires an entity mesh",
        ));
    }
    if let Some(decal) = instance.decal_foil {
        decal.prepare()?;
        if !matches!(instance.item_foil, Some(foil) if foil.kind == crate::render::shared::item_foil::StandardFoilKind::Item)
            || instance.transform != decal.model_pose
        {
            return Err(GalError::invalid_argument(
                "world decal requires item foil and matching draw pose",
            ));
        }
    }
    if let Some(foil) = instance.item_foil {
        foil.validate()?;
        if instance.stratum != WORLD_STRATUM_ENTITY_MESH
            || instance.flags != 0
            || instance.block_entity_id != -1
        {
            return Err(GalError::invalid_argument(
                "standard foil requires an ordinary entity mesh instance",
            ));
        }
    }
    if instance.stratum == WORLD_STRATUM_ENTITY_SHADOW_CASTER
        && (instance.flags != 0
            || instance.item_foil.is_some()
            || instance.decal_foil.is_some()
            || instance.model_submission_order.is_some()
            || instance.block_entity_id != -1
            || instance.outline_color_argb != 0)
    {
        return Err(GalError::invalid_argument(
            "shadow-only entity casters must be plain entity meshes without flags, foil, ordering, or outline",
        ));
    }
    if instance.mesh_key == 0 || instance.mesh_generation == 0 {
        return Err(GalError::invalid_argument(
            "world mesh instance key and generation must be non-zero",
        ));
    }
    if instance.block_entity_id < -1 {
        return Err(GalError::invalid_argument(
            "world mesh instance block entity id must be >= -1",
        ));
    }
    if instance.stratum == WORLD_STRATUM_TERRAIN && instance.packed_light != 0 {
        return Err(GalError::invalid_argument(
            "terrain mesh instances must use per-vertex light, not an instance light override",
        ));
    }
    if instance.packed_light > 0x00ff_00ff {
        return Err(GalError::invalid_argument(
            "packed instance light contains invalid vanilla UV2 channels",
        ));
    }
    crate::render::shared::view_layering::validate_flags(
        if instance.stratum == WORLD_STRATUM_TERRAIN {
            instance.flags & !WORLD_MESH_INSTANCE_FLAG_SHADOW_ONLY
        } else {
            instance.flags
        },
        instance.stratum == WORLD_STRATUM_ENTITY_MESH,
        instance.item_foil.is_none() && instance.block_entity_id == -1,
    )?;
    let uv_offset_present = instance.flags & WORLD_MESH_INSTANCE_FLAG_UV_OFFSET_U != 0;
    let uv_offset_payload = instance.flags & WORLD_MESH_INSTANCE_UV_OFFSET_PAYLOAD;
    if (!uv_offset_present && uv_offset_payload != 0)
        || (uv_offset_present
            && (instance.stratum != WORLD_STRATUM_ENTITY_MESH
                || instance.item_foil.is_some()
                || instance.decal_foil.is_some()))
    {
        return Err(GalError::invalid_argument(
            "model UV offset requires a non-foil entity mesh and explicit presence bit",
        ));
    }
    if instance.flags
        & !(WORLD_MESH_INSTANCE_FLAG_OUTLINE_ONLY
            | WORLD_MESH_INSTANCE_FLAG_CAMERA_SORTED_QUADS
            | WORLD_MESH_INSTANCE_FLAG_SHADOW_ONLY
            | crate::render::shared::view_layering::FLAGS
            | WORLD_MESH_INSTANCE_FLAG_UV_OFFSET_U
            | WORLD_MESH_INSTANCE_UV_OFFSET_PAYLOAD)
        != 0
    {
        return Err(GalError::invalid_argument(
            "world mesh instance contains unknown semantic flags",
        ));
    }
    if instance.flags & WORLD_MESH_INSTANCE_FLAG_OUTLINE_ONLY != 0
        && (instance.stratum != WORLD_STRATUM_ENTITY_MESH || instance.outline_color_argb == 0)
    {
        return Err(GalError::invalid_argument(
            "outline-only mesh instances require an entity stratum and outline color",
        ));
    }
    if instance.flags & WORLD_MESH_INSTANCE_FLAG_CAMERA_SORTED_QUADS != 0 {
        geometry::translucent_order::validate_instance(instance)?;
    }
    if instance.stratum == WORLD_STRATUM_TERRAIN
        && instance.flags & WORLD_MESH_INSTANCE_FLAG_SHADOW_ONLY != 0
        && instance.flags & WORLD_MESH_INSTANCE_FLAG_CAMERA_SORTED_QUADS != 0
    {
        return Err(GalError::invalid_argument(
            "shadow-only mesh instances require unsorted static terrain",
        ));
    }
    if !is_world_mesh_stratum(instance.stratum) {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unsupported world mesh stratum {}", instance.stratum),
        ));
    }
    if instance.depth_policy > WORLD_DEPTH_POLICY_TEST_EQUAL_WRITE {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown world mesh depth policy {}", instance.depth_policy),
        ));
    }
    if instance.depth_policy == WORLD_DEPTH_POLICY_TEST_EQUAL_WRITE
        && (instance.stratum != WORLD_STRATUM_ENTITY_MESH || instance.item_foil.is_some())
    {
        return Err(GalError::invalid_argument(
            "equal-depth writes require a non-foil entity mesh",
        ));
    }
    let _ = cull_mode_from_policy(instance.cull_policy)?;
    if !matches!(instance.winding, WORLD_WINDING_CCW | WORLD_WINDING_CW) {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unsupported world mesh winding {}", instance.winding),
        ));
    }
    if instance.viewport_width != frame.viewport_width
        || instance.viewport_height != frame.viewport_height
    {
        return Err(GalError::invalid_argument(
            "world mesh viewport must match frame viewport",
        ));
    }
    if instance.transform.iter().any(|value| !value.is_finite()) {
        return Err(GalError::invalid_argument(
            "world mesh transform is not finite",
        ));
    }
    if instance.outline_color_argb != 0 {
        if instance.stratum != WORLD_STRATUM_ENTITY_MESH {
            return Err(GalError::invalid_argument(
                "entity outline color is only valid for entity mesh instances",
            ));
        }
    }
    Ok(())
}

pub(in crate::render::worldrender) fn is_world_mesh_stratum(stratum: u32) -> bool {
    matches!(
        stratum,
        WORLD_STRATUM_TERRAIN
            | WORLD_STRATUM_OPAQUE_TEXTURED_GEOMETRY
            | WORLD_STRATUM_ORDINARY_BLOCK
            | WORLD_STRATUM_MOVING_MESH
            | WORLD_STRATUM_ENTITY_MESH
            | WORLD_STRATUM_ENTITY_SHADOW_CASTER
    )
}

/// The stable V2 mesh record already carries the copied atlas UV, semantic
/// block/material identity, packed light, normal, and winding inputs needed
/// by a source-derived terrain-material shader. V3 additionally carries the
/// midpoint bytes required by the private terrain voxelizer, so voxelization
/// remains deliberately V3-only below.
pub(in crate::render::worldrender) fn source_mesh_layout_has_shader_semantics(vertex_layout_version: u32) -> bool {
    matches!(
        vertex_layout_version,
        WORLD_MESH_VERTEX_LAYOUT_V2 | WORLD_MESH_VERTEX_LAYOUT_V3
    )
}

/// These mesh strata share the indexed baked-block semantic family. They may
/// enter a selected source terrain-material pass only after their copied mesh
/// asset has independently proven the source vertex/material contract. This
/// is a semantic route boundary, not a producer-specific backend branch.
pub(in crate::render::worldrender) fn is_source_terrain_mesh_stratum(stratum: u32) -> bool {
    matches!(
        stratum,
        WORLD_STRATUM_TERRAIN
            | WORLD_STRATUM_OPAQUE_TEXTURED_GEOMETRY
            | WORLD_STRATUM_ORDINARY_BLOCK
            | WORLD_STRATUM_MOVING_MESH
    )
}

pub(in crate::render::worldrender) fn validate_mesh_asset(mesh: &WorldMeshAsset) -> GalResult<()> {
    if mesh.mesh_key == 0 || mesh.mesh_generation == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world mesh asset key and generation must be non-zero",
        ));
    }
    if !matches!(
        mesh.vertex_layout_version,
        WORLD_MESH_VERTEX_LAYOUT_V2 | WORLD_MESH_VERTEX_LAYOUT_V3
    ) {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!(
                "unsupported world mesh vertex layout {}",
                mesh.vertex_layout_version
            ),
        ));
    }
    if mesh.vertices.is_empty() || mesh.vertices.len() > WORLD_MAX_MESH_VERTICES {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "world mesh vertex count {} must be 1..={}",
                mesh.vertices.len(),
                WORLD_MAX_MESH_VERTICES
            ),
        ));
    }
    if mesh.index_bytes.is_empty() || mesh.index_bytes.len() > WORLD_MAX_MESH_INDEX_BYTES {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "world mesh index bytes {} must be 1..={}",
                mesh.index_bytes.len(),
                WORLD_MAX_MESH_INDEX_BYTES
            ),
        ));
    }
    let index_size = match mesh.index_type {
        IndexType::U16 => 2usize,
        IndexType::U32 => 4usize,
    };
    if mesh.index_bytes.len() % index_size != 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world mesh index bytes are not aligned to index type",
        ));
    }
    let index_count = mesh.index_bytes.len() / index_size;
    if mesh.sections.is_empty() || mesh.sections.len() > WORLD_MAX_MESH_SECTIONS {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "world mesh section count {} must be 1..={}",
                mesh.sections.len(),
                WORLD_MAX_MESH_SECTIONS
            ),
        ));
    }
    for vertex in &mesh.vertices {
        if vertex.position.iter().any(|value| !value.is_finite())
            || vertex.uv.iter().any(|value| !value.is_finite())
            || vertex
                .shader_atlas_uv
                .iter()
                .any(|value| !value.is_finite())
        {
            return Err(GalError::invalid_argument(
                "world mesh vertex position and UV values must be finite",
            ));
        }
    }
    for section in &mesh.sections {
        let optical_mode = matches!(
            section.material_mode,
            WORLD_MATERIAL_MODE_OPTICAL_STENCIL_WRITE | WORLD_MATERIAL_MODE_OPTICAL_STENCIL_TEST
        );
        if (!optical_mode
            && !assets::material_registry::material_matches_mode(
                section.material_id,
                section.material_mode,
            ))
            || (optical_mode && section.material_id != WORLD_MATERIAL_ID_CUTOUT_TEXTURED)
        {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "world mesh material {} is incompatible with mode {}",
                    section.material_id, section.material_mode
                ),
            ));
        }
        if section.texture_id == 0 {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "world mesh section texture id must be non-zero",
            ));
        }
        let _ = cull_mode_from_policy(section.cull_policy)?;
        if section.source_facing > 6 {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!(
                    "unsupported world mesh section source facing {}",
                    section.source_facing
                ),
            ));
        }
        if !matches!(section.winding, WORLD_WINDING_CCW | WORLD_WINDING_CW) {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!("unsupported world mesh section winding {}", section.winding),
            ));
        }
        let start = (section.index_offset as usize)
            .checked_div(index_size)
            .ok_or_else(|| GalError::invalid_argument("invalid world mesh index offset"))?;
        if section.index_offset as usize % index_size != 0 {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "world mesh section index offset is not aligned",
            ));
        }
        let end = start
            .checked_add(section.index_count as usize)
            .ok_or_else(|| GalError::invalid_argument("world mesh section index range overflow"))?;
        if end > index_count {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "world mesh section index range exceeds index payload",
            ));
        }
        for index in start..end {
            let vertex_index = mesh_index_value(&mesh.index_bytes, mesh.index_type, index)?;
            if vertex_index >= mesh.vertices.len() as u32 {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    format!(
                        "world mesh section references vertex {vertex_index} but mesh has {} vertices",
                        mesh.vertices.len()
                    ),
                ));
            }
        }
    }
    Ok(())
}

/// Validates the copied semantic LOD column before it reaches any resource or
/// draw construction. The fixed vertex fields are intentionally not decoded
/// as a backend layout here; shader/runtime code owns their later expansion.
pub fn validate_world_lod_column_asset(asset: &WorldLodColumnAsset) -> GalResult<()> {
    if asset.column_generation == 0 {
        return Err(GalError::invalid_argument(
            "world LOD column generation must be non-zero",
        ));
    }
    if asset.vertex_layout_version != WORLD_LOD_VERTEX_LAYOUT_V1 {
        return Err(GalError::invalid_argument(format!(
            "unsupported world LOD vertex layout {}",
            asset.vertex_layout_version
        )));
    }
    if asset.segments.is_empty() || asset.segments.len() > WORLD_LOD_MAX_SEGMENTS_PER_COLUMN {
        return Err(GalError::invalid_argument(format!(
            "world LOD column has {} segments; expected 1..={}",
            asset.segments.len(),
            WORLD_LOD_MAX_SEGMENTS_PER_COLUMN
        )));
    }
    for (segment_index, segment) in asset.segments.iter().enumerate() {
        if !is_world_lod_layer(segment.layer) {
            return Err(GalError::invalid_argument(format!(
                "world LOD segment {segment_index} has unknown layer {}",
                segment.layer
            )));
        }
        if segment.vertices.is_empty()
            || segment.vertices.len() > WORLD_LOD_MAX_VERTICES_PER_SEGMENT
            || segment.vertices.len() % 4 != 0
        {
            return Err(GalError::invalid_argument(format!(
                "world LOD segment {segment_index} has {} vertices; expected quad-aligned 1..={}",
                segment.vertices.len(),
                WORLD_LOD_MAX_VERTICES_PER_SEGMENT
            )));
        }
        for (vertex_index, vertex) in segment.vertices.iter().enumerate() {
            if vertex.material_id > WORLD_LOD_MAX_MATERIAL_ID {
                return Err(GalError::invalid_argument(format!(
                    "world LOD segment {segment_index} vertex {vertex_index} has unsupported material category {}",
                    vertex.material_id
                )));
            }
            if vertex.normal_index > WORLD_LOD_MAX_NORMAL_INDEX {
                return Err(GalError::invalid_argument(format!(
                    "world LOD segment {segment_index} vertex {vertex_index} has unsupported face normal {}",
                    vertex.normal_index
                )));
            }
        }
    }
    Ok(())
}

pub(in crate::render::worldrender) fn validate_world_lod_material_provenance(
    assets: &[WorldLodColumnAsset],
    retirements: &[WorldLodColumnRetirement],
    provenance: Vec<WorldLodColumnMaterialProvenance>,
) -> GalResult<BTreeMap<u64, WorldLodColumnMaterialProvenance>> {
    let assets_by_key = assets
        .iter()
        .map(|asset| (asset.column_key, asset))
        .collect::<BTreeMap<_, _>>();
    let retirement_keys = retirements
        .iter()
        .map(|retirement| retirement.column_key)
        .collect::<BTreeSet<_>>();
    let mut result = BTreeMap::new();
    for column in provenance {
        let asset = assets_by_key.get(&column.column_key).ok_or_else(|| {
            GalError::invalid_argument(format!(
                "world LOD material provenance references column {} without an asset in the same update",
                column.column_key
            ))
        })?;
        if retirement_keys.contains(&column.column_key)
            || column.column_generation != asset.column_generation
        {
            return Err(GalError::invalid_argument(format!(
                "world LOD material provenance generation for column {} does not match its asset",
                column.column_key
            )));
        }
        if column.identities.len() > WORLD_LOD_MAX_MATERIAL_IDENTITIES_PER_COLUMN {
            return Err(GalError::invalid_argument(format!(
                "world LOD material provenance for column {} has {} identities; maximum is {}",
                column.column_key,
                column.identities.len(),
                WORLD_LOD_MAX_MATERIAL_IDENTITIES_PER_COLUMN
            )));
        }
        let mut face_material_keys = BTreeSet::new();
        for face_material in &column.face_materials {
            if face_material.material_id == 0
                || face_material.material_id as usize > column.identities.len()
                || face_material.face > WORLD_LOD_MAX_NORMAL_INDEX as u32
                || face_material.face_layer > 3
                || face_material.atlas_identity.is_empty()
                || face_material.sprite_identity.is_empty()
                || !face_material.atlas_uv.iter().all(|value| value.is_finite())
                || face_material.atlas_uv[0] < 0.0
                || face_material.atlas_uv[1] < 0.0
                || face_material.atlas_uv[2] > 1.0
                || face_material.atlas_uv[3] > 1.0
                || face_material.atlas_uv[0] >= face_material.atlas_uv[2]
                || face_material.atlas_uv[1] >= face_material.atlas_uv[3]
                || !world_lod_uv_corner_order_is_permutation(face_material.uv_corner_order)
                || !face_material_keys.insert((
                    face_material.material_id,
                    face_material.face,
                    face_material.face_layer,
                    face_material.variant_position,
                ))
            {
                return Err(GalError::invalid_argument(
                    "world LOD face material records must be unique normalized atlas regions",
                ));
            }
        }
        let mut segment_indices = BTreeSet::new();
        for segment in &column.segments {
            let asset_segment = asset
                .segments
                .get(segment.segment_index as usize)
                .ok_or_else(|| {
                    GalError::invalid_argument(format!(
                        "world LOD material provenance references missing segment {} for column {}",
                        segment.segment_index, column.column_key
                    ))
                })?;
            if asset_segment.layer != segment.layer {
                return Err(GalError::invalid_argument(
                    "world LOD material provenance layer does not match its column segment",
                ));
            }
            if !segment_indices.insert(segment.segment_index) {
                return Err(GalError::invalid_argument(
                    "world LOD material provenance contains duplicate segment metadata",
                ));
            }
            if segment.quad_material_ids.len() != asset_segment.vertices.len() / 4 {
                return Err(GalError::invalid_argument(format!(
                    "world LOD material provenance segment {} has {} material IDs for {} quads",
                    segment.segment_index,
                    segment.quad_material_ids.len(),
                    asset_segment.vertices.len() / 4
                )));
            }
            if segment.quad_variant_states.len() != segment.quad_material_ids.len()
                || segment.quad_variant_positions.len() != segment.quad_material_ids.len()
            {
                return Err(GalError::invalid_argument(
                    "world LOD material variant provenance must align one-for-one with material IDs",
                ));
            }
            for ((material_id, variant_state), variant_position) in segment
                .quad_material_ids
                .iter()
                .zip(&segment.quad_variant_states)
                .zip(&segment.quad_variant_positions)
            {
                if *material_id != WORLD_LOD_MATERIAL_UNAVAILABLE
                    && *material_id != WORLD_LOD_MATERIAL_MIXED
                    && (*material_id as usize > column.identities.len())
                {
                    return Err(GalError::invalid_argument(format!(
                        "world LOD material provenance ID {material_id} exceeds column identity table",
                    )));
                }
                if !matches!(
                    *variant_state,
                    WORLD_LOD_VARIANT_UNAVAILABLE
                        | WORLD_LOD_VARIANT_EXACT
                        | WORLD_LOD_VARIANT_MIXED
                ) || (*variant_state != WORLD_LOD_VARIANT_EXACT && *variant_position != 0)
                {
                    return Err(GalError::invalid_argument(
                        "world LOD material variant state or position is invalid",
                    ));
                }
            }
        }
        if segment_indices.len() != asset.segments.len() {
            return Err(GalError::invalid_argument(format!(
                "world LOD material provenance for column {} does not cover every emitted segment",
                column.column_key
            )));
        }
        if result.insert(column.column_key, column).is_some() {
            return Err(GalError::invalid_argument(
                "world LOD material provenance contains duplicate columns",
            ));
        }
    }
    Ok(result)
}

pub(in crate::render::worldrender) fn world_lod_uv_corner_order_is_permutation(order: u32) -> bool {
    if order > 0xff {
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

pub fn validate_world_lod_column_instance(
    instance: &WorldLodColumnInstanceRequest,
    asset: &WorldLodColumnAsset,
) -> GalResult<()> {
    if instance.column_key != asset.column_key
        || instance.column_generation != asset.column_generation
    {
        return Err(GalError::invalid_argument(
            "world LOD instance key or generation does not match its column asset",
        ));
    }
    if !is_world_lod_layer(instance.layer) {
        return Err(GalError::invalid_argument(format!(
            "world LOD instance has unknown layer {}",
            instance.layer
        )));
    }
    let Some(segment) = asset.segments.get(instance.segment_index as usize) else {
        return Err(GalError::invalid_argument(format!(
            "world LOD instance references missing segment {}",
            instance.segment_index
        )));
    };
    if segment.layer != instance.layer {
        return Err(GalError::invalid_argument(
            "world LOD instance layer does not match its referenced segment",
        ));
    }
    Ok(())
}

pub(in crate::render::worldrender) fn is_world_lod_layer(layer: u32) -> bool {
    matches!(
        layer,
        WORLD_LOD_LAYER_OPAQUE
            | WORLD_LOD_LAYER_TRANSPARENT_SIDE
            | WORLD_LOD_LAYER_TRANSPARENT_UP
            | WORLD_LOD_LAYER_TRANSPARENT_WATER_UP
    )
}

pub(in crate::render::worldrender) fn validate_mesh_sorted_index_update(update: &WorldMeshSortedIndexUpdate) -> GalResult<()> {
    if update.mesh_key == 0 || update.mesh_generation == 0 || update.index_generation == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world mesh sorted index update key and generations must be non-zero",
        ));
    }
    if update.index_bytes.is_empty() || update.index_bytes.len() > WORLD_MAX_MESH_INDEX_BYTES {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "world mesh sorted index bytes {} must be 1..={}",
                update.index_bytes.len(),
                WORLD_MAX_MESH_INDEX_BYTES
            ),
        ));
    }
    let index_size = index_stride(update.index_type) as usize;
    if update.index_bytes.len() % index_size != 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world mesh sorted index bytes are not aligned to index type",
        ));
    }
    Ok(())
}

pub(in crate::render::worldrender) fn validate_mesh_sorted_index_payload(
    asset: &MeshAssetStore,
    update: &WorldMeshSortedIndexUpdate,
) -> GalResult<()> {
    let index_size = index_stride(update.index_type) as usize;
    let index_count = update.index_bytes.len() / index_size;
    let vertex_count = asset.vertex_bytes.len() / WORLD_MESH_GPU_VERTEX_BYTES;
    for section in &asset.sections {
        if section.index_offset as usize % index_size != 0 {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "world mesh sorted section index offset is not aligned",
            ));
        }
        let start = (section.index_offset as usize)
            .checked_div(index_size)
            .ok_or_else(|| GalError::invalid_argument("invalid world mesh sorted index offset"))?;
        let end = start
            .checked_add(section.index_count as usize)
            .ok_or_else(|| {
                GalError::invalid_argument("world mesh sorted section index range overflow")
            })?;
        if end > index_count {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "world mesh sorted section index range exceeds index payload",
            ));
        }
        for index in start..end {
            let vertex_index = mesh_index_value(&update.index_bytes, update.index_type, index)?;
            if vertex_index >= vertex_count as u32 {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    format!(
                        "world mesh sorted index references vertex {vertex_index} but mesh has {vertex_count} vertices",
                    ),
                ));
            }
        }
    }
    Ok(())
}

pub(in crate::render::worldrender) fn mesh_index_value(index_bytes: &[u8], index_type: IndexType, index: usize) -> GalResult<u32> {
    match index_type {
        IndexType::U16 => {
            let offset = index.checked_mul(2).ok_or_else(|| {
                GalError::invalid_argument("world mesh u16 index offset overflow")
            })?;
            let bytes = index_bytes.get(offset..offset + 2).ok_or_else(|| {
                GalError::invalid_argument("world mesh u16 index offset exceeds payload")
            })?;
            Ok(u16::from_le_bytes([bytes[0], bytes[1]]) as u32)
        }
        IndexType::U32 => {
            let offset = index.checked_mul(4).ok_or_else(|| {
                GalError::invalid_argument("world mesh u32 index offset overflow")
            })?;
            let bytes = index_bytes.get(offset..offset + 4).ok_or_else(|| {
                GalError::invalid_argument("world mesh u32 index offset exceeds payload")
            })?;
            Ok(u32::from_le_bytes([bytes[0], bytes[1], bytes[2], bytes[3]]))
        }
    }
}

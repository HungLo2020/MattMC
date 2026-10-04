//! Lowering validated batches into prepared draws and packed vertices.

use super::*;

/// Consumes the caller-independent request family into a compact render-plan
/// family. Transforming at this boundary means later GUI mesh resource and
/// command construction only sees Rust-owned data. Standard item foil consumes
/// original `atlas_uv`; other meshes use the supplied local texture UVs.
pub fn prepare_draws(batches: &[GuiMeshBatchRequest]) -> GalResult<Vec<GuiMeshPreparedDraw>> {
    validate_batches(batches)?;
    batches.iter().map(prepare_draw).collect()
}

/// Prepares a semantic mesh family while allowing the caller to skip vertex
/// reconstruction for static items whose Rust-owned offscreen raster is known
/// to be valid.  The returned metadata is sufficient for composition; the
/// raster path never consumes its intentionally empty geometry vectors.
pub(crate) fn prepare_draws_with_reuse(
    batches: &[GuiMeshBatchRequest],
    reusable_items: &BTreeSet<(u32, u64)>,
) -> GalResult<Vec<GuiMeshPreparedDraw>> {
    validate_batches(batches)?;
    batches
        .iter()
        .map(|batch| {
            if reusable_items.contains(&(batch.stratum, batch.sequence)) {
                prepare_reused_draw(batch)
            } else {
                prepare_draw(batch)
            }
        })
        .collect()
}

pub(super) fn prepare_reused_draw(batch: &GuiMeshBatchRequest) -> GalResult<GuiMeshPreparedDraw> {
    let (render_extent, _model_transform, guard_pixels) = resolved_item_raster(batch)?;
    Ok(GuiMeshPreparedDraw {
        item_cache: batch.item_cache,
        stratum: batch.stratum,
        layer_index: batch.layer_index,
        sequence: batch.sequence,
        asset_id: batch.asset_id,
        material_mode: batch.material_mode,
        // Cached pixels do not enter the raster path, so winding is not
        // consulted. Keep a canonical value in the backend-neutral record.
        front_face: crate::render::vulkanic::resources::FrontFace::CounterClockwise,
        lighting_mode: batch.lighting_mode,
        alpha_cutoff: batch.alpha_cutoff,
        gui_pose: batch.gui_pose,
        bounds: resolved_item_bounds(batch)?,
        gui_extent: batch.gui_extent,
        projection_extent: batch.projection_extent,
        render_extent,
        guard_pixels,
        clip_mode: batch.clip_mode,
        clip_left: batch.clip_left,
        clip_top: batch.clip_top,
        clip_width: batch.clip_width,
        clip_height: batch.clip_height,
        vertices: Vec::new(),
        indices: Vec::new(),
    })
}

pub(super) fn prepare_draw(batch: &GuiMeshBatchRequest) -> GalResult<GuiMeshPreparedDraw> {
    let (render_extent, model_transform, guard_pixels) = resolved_item_raster(batch)?;
    let decal_projection = batch
        .decal_foil
        .map(|decal| decal.prepare((batch.item_raster_scale != 0).then_some(model_transform)))
        .transpose()?;
    let vertices = batch
        .vertices
        .iter()
        .map(|vertex| {
            let position = transform_point(model_transform, vertex.position)?;
            let packed_normal = unpack_normal_i8(vertex.normal_packed);
            let mut normal = normalize_semantic_normal(packed_normal)?;
            if vertex.source_foil_type == 1 {
                // Frozen's enchanted GUI multi-consumer emits the baked face
                // direction, unlike the unenchanted packed-vertex encoder.
                // Select and transform it here, never in the semantic caller.
                normal = match vertex.source_face {
                    1 => [0.0, -1.0, 0.0],
                    2 => [0.0, 1.0, 0.0],
                    3 => [0.0, 0.0, -1.0],
                    4 => [0.0, 0.0, 1.0],
                    5 => [-1.0, 0.0, 0.0],
                    6 => [1.0, 0.0, 0.0],
                    _ => return Err(GalError::invalid_argument("missing enchanted baked face")),
                };
            }
            if (batch.lighting_mode == GuiMeshLightingMode::InventoryBlock
                && batch.block_item_raster.is_none())
                || batch.lighting_mode.is_entity_material_lighting()
            {
                normal = packed_normal;
            }
            if batch.item_raster_scale != 0 {
                normal = transform_model_item_normal(model_transform, normal)?;
                if batch.lighting_mode == GuiMeshLightingMode::FrontModel {
                    normal = normal.map(|v| ((v.clamp(-1.0, 1.0) * 127.0) as i8) as f32 / 127.0);
                }
                // Frozen clips model geometry to the item atlas cell. Keep
                // original vertices and let the explicit offscreen viewport
                // clip them; never fit or clamp the model to the image.
            }
            if batch.block_item_raster.is_some() {
                // Out-of-cell vertices are valid for ordinary item models.
                // The explicit raster viewport clips them like Frozen's atlas.
                normal = transform_model_item_normal(model_transform, normal)?;
                // Frozen's item encoder normalizes, then packs to signed i8;
                // its shader consumes that quantized direction. Keep that
                // quantization in Rust rather than asking Java to rasterize normals.
                normal = normal.map(|v| ((v.clamp(-1.0, 1.0) * 127.0) as i8) as f32 / 127.0);
            }
            let mut color = argb_to_rgba(vertex.color_argb);
            let local_uv = if let Some(foil) = batch.item_foil {
                color = foil.color()?;
                let source_uv = if let Some(decal) = &decal_projection {
                    decal.texture_uv(position, normal)?
                } else {
                    vertex.atlas_uv
                };
                foil.texture_uv(source_uv)?
            } else {
                vertex.local_uv
            };
            if batch.item_foil.is_none() && batch.material_mode == GuiMeshMaterialMode::Glint {
                // The copied item glint color's alpha encodes strength, not
                // coverage. Frozen glint.fsh applies GlintAlpha to RGB after
                // the texture alpha test, preserving the destination alpha.
                let strength = color[3];
                color = [
                    color[0] * strength,
                    color[1] * strength,
                    color[2] * strength,
                    1.0,
                ];
            }
            if batch.requires_item_lightmap() {
                color = batch
                    .item_lighting
                    .ok_or_else(|| {
                        GalError::invalid_argument(
                            "GUI item mesh requires resolved frame lightmap semantics",
                        )
                    })?
                    .modulate(color)?;
            }
            Ok(GuiMeshPreparedVertex {
                position,
                local_uv,
                color,
                normal,
            })
        })
        .collect::<GalResult<Vec<_>>>()?;
    let (indices, front_face) = if batch.item_raster_scale != 0 || batch.block_item_raster.is_some()
    {
        reconcile_model_mesh_topology(model_transform, &vertices, &batch.indices)?
    } else {
        (
            batch.indices.clone(),
            transformed_front_face(model_transform, &vertices, &batch.indices)?,
        )
    };
    trace_prepared_item_vertices(batch, &vertices, model_transform, render_extent);
    Ok(GuiMeshPreparedDraw {
        item_cache: batch.item_cache,
        stratum: batch.stratum,
        layer_index: batch.layer_index,
        sequence: batch.sequence,
        asset_id: batch.asset_id,
        material_mode: batch.material_mode,
        front_face,
        lighting_mode: batch.lighting_mode,
        alpha_cutoff: batch.alpha_cutoff,
        gui_pose: batch.gui_pose,
        bounds: match batch
            .block_item_raster
            .map(|block| block.oversized_layout(batch.bounds))
            .transpose()?
            .flatten()
        {
            Some((_, bounds)) => bounds,
            None => batch.bounds,
        },
        gui_extent: batch.gui_extent,
        projection_extent: batch.projection_extent,
        render_extent,
        guard_pixels,
        clip_mode: batch.clip_mode,
        clip_left: batch.clip_left,
        clip_top: batch.clip_top,
        clip_width: batch.clip_width,
        clip_height: batch.clip_height,
        vertices,
        indices,
    })
}

/// Optional bounded CPU observations only; no rendering inputs or GPU handles.
pub(super) fn trace_prepared_item_vertices(
    batch: &GuiMeshBatchRequest,
    vertices: &[GuiMeshPreparedVertex],
    matrix: [f32; 16],
    extent: [u32; 2],
) {
    use std::sync::{
        atomic::{AtomicUsize, Ordering},
        OnceLock,
    };
    static SELECTOR: OnceLock<Option<[i32; 2]>> = OnceLock::new();
    static COUNT: AtomicUsize = AtomicUsize::new(0);
    let selected = SELECTOR.get_or_init(|| {
        std::env::var("MATTMC_GUI_MESH_VERTEX_TRACE")
            .ok()
            .and_then(|text| parse_vertex_trace_selector(&text))
    });
    let Some(selected) = selected else {
        return;
    };
    if batch.bounds[..2] != selected[..] || COUNT.fetch_add(1, Ordering::Relaxed) >= 32 {
        return;
    }
    let copied: Vec<_> = batch
        .vertices
        .iter()
        .zip(vertices)
        .map(|(source, prepared)| {
            [
                source.position[0],
                source.position[1],
                source.position[2],
                prepared.position[0],
                prepared.position[1],
                prepared.position[2],
                source.atlas_uv[0],
                source.atlas_uv[1],
            ]
        })
        .collect();
    crate::core::console::stderr(format_args!("[gui.mesh.vertex-trace] {{\"layer\":{},\"material\":\"{:?}\",\"bounds\":{:?},\"extent\":{:?},\"matrix\":{:?},\"vertices\":{:?}}}",
        batch.layer_index,batch.material_mode,batch.bounds,extent,matrix,copied));
}

pub(super) fn parse_vertex_trace_selector(text: &str) -> Option<[i32; 2]> {
    let (x, y) = text.split_once(',')?;
    Some([x.parse().ok()?, y.parse().ok()?])
}

/// A resource layer can contain multiple faces with different normals. Keep
/// per-triangle normal validation and resolve winding here, not in Java.
/// Reversing a triangle whose copied normal specifies the opposite front
/// preserves its intended culling without splitting one semantic mesh per face.
pub(super) fn reconcile_model_mesh_topology(
    matrix: [f32; 16],
    vertices: &[GuiMeshPreparedVertex],
    indices: &[u32],
) -> GalResult<(Vec<u32>, crate::render::vulkanic::resources::FrontFace)> {
    let front = transformed_front_face(matrix, vertices, indices)?;
    let mut resolved = indices.to_vec();
    for triangle in resolved.chunks_exact_mut(3) {
        let first = vertices[triangle[0] as usize].normal;
        for index in triangle.iter().copied() {
            if vertices[index as usize]
                .normal
                .iter()
                .zip(first)
                .any(|(actual, expected)| (actual - expected).abs() > 0.000001)
            {
                return Err(GalError::unsupported_feature(
                    "flat item face has inconsistent copied normals",
                ));
            }
        }
        if transformed_front_face(matrix, vertices, triangle)? != front {
            triangle.swap(1, 2);
        }
    }
    Ok((resolved, front))
}

/// Packs exactly three vec4 values per vertex: position/local-U, local-V and
/// RGBA, then normal. This is a private streaming layout, deliberately not
/// part of the FFI ABI or a Java renderer contract.
pub fn packed_vertices(draw: &GuiMeshPreparedDraw) -> Vec<u8> {
    let mut bytes = Vec::with_capacity(draw.vertices.len() * GUI_MESH_GPU_VERTEX_BYTES);
    for vertex in &draw.vertices {
        push_f32(&mut bytes, vertex.position[0]);
        push_f32(&mut bytes, vertex.position[1]);
        push_f32(&mut bytes, vertex.position[2]);
        push_f32(&mut bytes, vertex.local_uv[0]);
        push_f32(&mut bytes, vertex.local_uv[1]);
        push_f32(&mut bytes, vertex.color[0]);
        push_f32(&mut bytes, vertex.color[1]);
        push_f32(&mut bytes, vertex.color[2]);
        push_f32(&mut bytes, vertex.color[3]);
        push_f32(&mut bytes, vertex.normal[0]);
        push_f32(&mut bytes, vertex.normal[1]);
        push_f32(&mut bytes, vertex.normal[2]);
    }
    bytes
}

pub(super) fn transform_point(matrix: [f32; 16], position: [f32; 3]) -> GalResult<[f32; 3]> {
    let result = [
        matrix[0] * position[0] + matrix[4] * position[1] + matrix[8] * position[2] + matrix[12],
        matrix[1] * position[0] + matrix[5] * position[1] + matrix[9] * position[2] + matrix[13],
        matrix[2] * position[0] + matrix[6] * position[1] + matrix[10] * position[2] + matrix[14],
    ];
    if result.iter().all(|value| value.is_finite()) {
        Ok(result)
    } else {
        Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI mesh model transform produced a non-finite position",
        ))
    }
}

pub(super) fn normalize_semantic_normal(normal: [f32; 3]) -> GalResult<[f32; 3]> {
    let length_squared = normal.iter().map(|value| value * value).sum::<f32>();
    if !length_squared.is_finite() || length_squared <= f32::EPSILON {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI mesh contains a degenerate item-lighting-space normal",
        ));
    }
    let inverse_length = length_squared.sqrt().recip();
    Ok([
        normal[0] * inverse_length,
        normal[1] * inverse_length,
        normal[2] * inverse_length,
    ])
}

pub(super) fn unpack_normal_i8(packed: u32) -> [f32; 3] {
    let component = |shift| {
        let value = ((packed >> shift) & 0xffu32) as u8 as i8;
        (value as f32 / 127.0).clamp(-1.0, 1.0)
    };
    [component(0), component(8), component(16)]
}

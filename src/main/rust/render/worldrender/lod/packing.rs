//! Expanding columns and planning/packing textured and compact LOD geometry.

use crate::render::worldrender::lod::*;

pub(crate) fn expand_world_lod_column_asset(
    asset: &WorldLodColumnAsset,
) -> GalResult<WorldLodExpandedColumnAsset> {
    validate_world_lod_column_asset(asset)?;
    let mut segments = Vec::with_capacity(asset.segments.len());
    for segment in &asset.segments {
        segments.push(expand_segment(segment)?);
    }
    Ok(WorldLodExpandedColumnAsset {
        column_key: asset.column_key,
        column_generation: asset.column_generation,
        origin: asset.origin,
        segments,
    })
}

/// Resolves the exact atlas UVs for one compact DH segment. The segment's
/// one-ID-per-quad sidecar and face material table must belong to the same
/// immutable column generation; that pairing is validated before this helper
/// is called. This function deliberately does not derive a texture from the
/// vertex material category, because that category is only DH shading data.
pub(crate) fn plan_world_lod_textured_segment(
    segment: &WorldLodSegment,
    quad_material_ids: &[u32],
    face_materials: &[WorldLodFaceMaterial],
) -> GalResult<WorldLodTexturedSegmentPlan> {
    let variant_states = vec![crate::render::scene::lod::WORLD_LOD_VARIANT_EXACT; quad_material_ids.len()];
    let variant_positions = vec![0; quad_material_ids.len()];
    plan_world_lod_textured_segment_with_variants(
        segment,
        quad_material_ids,
        &variant_states,
        &variant_positions,
        face_materials,
    )
}

pub(crate) fn plan_world_lod_textured_segment_with_variants(
    segment: &WorldLodSegment,
    quad_material_ids: &[u32],
    quad_variant_states: &[u8],
    quad_variant_positions: &[u64],
    face_materials: &[WorldLodFaceMaterial],
) -> GalResult<WorldLodTexturedSegmentPlan> {
    let face_material_index = world_lod_face_material_index(face_materials);
    plan_world_lod_textured_segment_with_material_index(
        segment,
        quad_material_ids,
        quad_variant_states,
        quad_variant_positions,
        &face_material_index,
    )
}

/// Indexes immutable Java-copied face provenance once per column generation.
/// A real DH column has tens of thousands of reduced quads and thousands of
/// face records; repeatedly scanning the latter for every quad made asset
/// publication quadratic without adding any rendering semantics.
pub(super) fn world_lod_face_material_index(
    face_materials: &[WorldLodFaceMaterial],
) -> BTreeMap<(u32, u32, u64), Vec<&WorldLodFaceMaterial>> {
    let mut index = BTreeMap::new();
    for material in face_materials {
        index
            .entry((
                material.material_id,
                material.face,
                material.variant_position,
            ))
            .or_insert_with(Vec::new)
            .push(material);
    }
    for layers in index.values_mut() {
        layers.sort_by_key(|material| material.face_layer);
    }
    index
}

/// Capture-only exact-atlas layer selector used to isolate base-face versus
/// alpha-tested overlay behavior. It is deliberately unavailable outside an
/// explicit graphics-audit process and never changes normal admission.
pub(super) fn world_lod_audit_face_layer_limit() -> Option<u32> {
    let audit_enabled = matches!(
        crate::core::environment::var("MATTMC_GRAPHICS_AUDIT").as_deref(),
        Ok("1") | Ok("true") | Ok("TRUE")
    );
    audit_enabled
        .then(|| {
            crate::core::environment::var("MATTMC_RUST_DH_EXACT_ATLAS_MAX_FACE_LAYER")
                .ok()
                .and_then(|value| value.trim().parse::<u32>().ok())
        })
        .flatten()
}

pub(super) fn plan_world_lod_textured_segment_with_material_index(
    segment: &WorldLodSegment,
    quad_material_ids: &[u32],
    quad_variant_states: &[u8],
    quad_variant_positions: &[u64],
    face_materials: &BTreeMap<(u32, u32, u64), Vec<&WorldLodFaceMaterial>>,
) -> GalResult<WorldLodTexturedSegmentPlan> {
    if segment.vertices.len() % 4 != 0 {
        return Err(GalError::invalid_argument(
            "world LOD textured planning requires quad-aligned segment vertices",
        ));
    }
    if quad_material_ids.len() != segment.vertices.len() / 4 {
        return Err(GalError::invalid_argument(format!(
            "world LOD textured planning has {} material IDs for {} quads",
            quad_material_ids.len(),
            segment.vertices.len() / 4
        )));
    }
    if quad_variant_states.len() != quad_material_ids.len()
        || quad_variant_positions.len() != quad_material_ids.len()
    {
        return Err(GalError::invalid_argument(
            "world LOD textured planning requires one variant record per quad",
        ));
    }
    let mut plan = WorldLodTexturedSegmentPlan {
        layer: segment.layer,
        source_quad_count: u32::try_from(quad_material_ids.len())
            .map_err(|_| GalError::invalid_argument("world LOD textured quad count exceeds u32"))?,
        quads: Vec::with_capacity(quad_material_ids.len()),
        unavailable: Vec::new(),
    };
    for (quad_index, ((vertices, &material_id), (&variant_state, &variant_position))) in segment
        .vertices
        .chunks_exact(4)
        .zip(quad_material_ids)
        .zip(quad_variant_states.iter().zip(quad_variant_positions))
        .enumerate()
    {
        let quad_index = u32::try_from(quad_index)
            .map_err(|_| GalError::invalid_argument("world LOD quad index exceeds u32"))?;
        let face = u32::from(vertices[0].normal_index);
        let unavailable_reason = if material_id == crate::render::scene::lod::WORLD_LOD_MATERIAL_UNAVAILABLE {
            Some(WorldLodTexturedQuadUnavailableReason::MaterialUnavailable)
        } else if material_id == crate::render::scene::lod::WORLD_LOD_MATERIAL_MIXED {
            Some(WorldLodTexturedQuadUnavailableReason::MaterialMixed)
        } else if variant_state == crate::render::scene::lod::WORLD_LOD_VARIANT_UNAVAILABLE {
            Some(WorldLodTexturedQuadUnavailableReason::VariantUnavailable)
        } else if variant_state == crate::render::scene::lod::WORLD_LOD_VARIANT_MIXED {
            Some(WorldLodTexturedQuadUnavailableReason::VariantMixed)
        } else if vertices
            .iter()
            .any(|vertex| vertex.normal_index != vertices[0].normal_index)
        {
            Some(WorldLodTexturedQuadUnavailableReason::InconsistentFace)
        } else {
            None
        };
        if let Some(reason) = unavailable_reason {
            plan.unavailable.push(WorldLodTexturedQuadUnavailable {
                quad_index,
                material_id,
                face,
                reason,
            });
            continue;
        }
        // Position-specific material records carry a biome tint or a weighted
        // model selection. A stable position-zero record remains a safe
        // semantic fallback only when no exact override was copied; genuinely
        // weighted models have no such base record and still reject.
        let Some(materials) = face_materials
            .get(&(material_id, face, variant_position))
            .or_else(|| face_materials.get(&(material_id, face, 0)))
        else {
            plan.unavailable.push(WorldLodTexturedQuadUnavailable {
                quad_index,
                material_id,
                face,
                reason: WorldLodTexturedQuadUnavailableReason::MissingFaceMaterial,
            });
            continue;
        };
        let expanded = [
            expand_vertex(&vertices[0])?,
            expand_vertex(&vertices[1])?,
            expand_vertex(&vertices[2])?,
            expand_vertex(&vertices[3])?,
        ];
        let audit_face_layer_limit = world_lod_audit_face_layer_limit();
        for material in materials {
            if audit_face_layer_limit.is_some_and(|limit| material.face_layer > limit) {
                continue;
            }
            let tile_uv = world_lod_face_tile_coordinates(&expanded, face, material)?;
            let vertices = expanded.map(|vertex| WorldLodTexturedVertex {
                local_position: vertex.local_position,
                micro_offset: vertex.micro_offset,
                color_rgba: if material.tinted {
                    [
                        material.tint_rgb[0],
                        material.tint_rgb[1],
                        material.tint_rgb[2],
                        // The copied tint changes RGB only. DH water and
                        // other translucent source materials carry opacity
                        // in semantic vertex alpha; replacing it with one
                        // makes exact-atlas water opaque.
                        vertex.color_rgba[3],
                    ]
                } else {
                    vertex.color_rgba
                },
                tinted: material.tinted,
                sky_light: vertex.sky_light,
                block_light: vertex.block_light,
                material: vertex.material,
                normal: vertex.normal,
                tile_uv: [0.0, 0.0],
                atlas_rect: material.atlas_uv,
            });
            let mut vertices = vertices;
            for (serialized_vertex_index, vertex) in vertices.iter_mut().enumerate() {
                // The serialised order determines the unwrapped tile coordinate;
                // the copied material transform preserves model UV rotation.
                vertex.tile_uv =
                    world_lod_face_sprite_tile_uv(material, tile_uv[serialized_vertex_index]);
            }
            plan.quads.push(WorldLodTexturedQuad {
                quad_index,
                material_id,
                face,
                face_layer: material.face_layer,
                tinted: material.tinted,
                atlas_identity: material.atlas_identity.clone(),
                sprite_identity: material.sprite_identity.clone(),
                vertices,
            });
        }
    }
    Ok(plan)
}

/// Binds each per-quad provenance sidecar to its exact copied column. The
/// result retains incomplete quads as explicit unavailability records, so a
/// later pass can never infer a sprite from a DH material category or a table
/// ordinal when reduced geometry lost that information.
pub(crate) fn plan_world_lod_textured_column(
    asset: &WorldLodColumnAsset,
    provenance: &WorldLodColumnMaterialProvenance,
) -> GalResult<WorldLodTexturedColumnPlan> {
    validate_world_lod_column_asset(asset)?;
    if provenance.column_key != asset.column_key
        || provenance.column_generation != asset.column_generation
    {
        return Err(GalError::invalid_argument(
            "world LOD textured provenance key or generation differs from its column asset",
        ));
    }
    if provenance.segments.len() != asset.segments.len() {
        return Err(GalError::invalid_argument(format!(
            "world LOD textured provenance has {} segments for {} column segments",
            provenance.segments.len(),
            asset.segments.len()
        )));
    }

    let mut segments = Vec::with_capacity(asset.segments.len());
    let face_material_index = world_lod_face_material_index(&provenance.face_materials);
    for (segment_index, segment) in asset.segments.iter().enumerate() {
        let expected_index = u32::try_from(segment_index)
            .map_err(|_| GalError::invalid_argument("world LOD segment index exceeds u32"))?;
        let provenance_segment = provenance.segments.get(segment_index).ok_or_else(|| {
            GalError::invalid_argument("world LOD textured provenance is incomplete")
        })?;
        if provenance_segment.segment_index != expected_index
            || provenance_segment.layer != segment.layer
        {
            return Err(GalError::invalid_argument(format!(
                "world LOD textured provenance segment {segment_index} does not match its geometry layer/order",
            )));
        }
        segments.push(plan_world_lod_textured_segment_with_material_index(
            segment,
            &provenance_segment.quad_material_ids,
            &provenance_segment.quad_variant_states,
            &provenance_segment.quad_variant_positions,
            &face_material_index,
        )?);
    }
    Ok(WorldLodTexturedColumnPlan {
        column_key: asset.column_key,
        column_generation: asset.column_generation,
        segments,
    })
}

/// Converts every resolved, block-atlas-backed DH quad into an owned GPU
/// payload. A source segment can be partial: the later draw planner retains
/// its reduced-color draw for unknown quads, then overlays its resolved atlas
/// subset with the same depth contract. This keeps one source identity per
/// textured quad without assigning an arbitrary sprite to its neighbours.
pub(crate) fn pack_world_lod_textured_column_asset(
    plan: &WorldLodTexturedColumnPlan,
) -> GalResult<WorldLodTexturedGpuColumnAsset> {
    let mut segments = Vec::with_capacity(plan.segments.len());
    let mut unavailable_source_segments = Vec::new();
    for (source_segment_index, segment) in plan.segments.iter().enumerate() {
        let source_segment_index = u32::try_from(source_segment_index).map_err(|_| {
            GalError::invalid_argument("world LOD textured source segment index exceeds u32")
        })?;

        // Atlas identity is a property of the source quad, not the whole
        // segment. DH columns commonly merge thousands of quads into one
        // transport segment; rejecting that segment because a few records
        // have an unsupported atlas silently discarded all otherwise valid
        // exact-atlas work. If one material layer for a source quad cannot
        // use the block atlas, keep that source quad entirely on the coarse
        // path so layered geometry cannot overlap between the two streams.
        let mut unavailable = segment.unavailable.clone();
        let mut unavailable_quad_indices = unavailable
            .iter()
            .map(|quad| quad.quad_index)
            .collect::<BTreeSet<_>>();
        for quad in &segment.quads {
            if quad.atlas_identity != WORLD_LOD_TERRAIN_ATLAS_IDENTITY
                && unavailable_quad_indices.insert(quad.quad_index)
            {
                unavailable.push(WorldLodTexturedQuadUnavailable {
                    quad_index: quad.quad_index,
                    material_id: quad.material_id,
                    face: quad.face,
                    reason: WorldLodTexturedQuadUnavailableReason::UnsupportedAtlas,
                });
            }
        }
        unavailable.sort_by_key(|quad| quad.quad_index);
        if !unavailable.is_empty() {
            unavailable_source_segments.push(source_segment_index);
        }

        let quads = segment
            .quads
            .iter()
            .filter(|quad| {
                quad.atlas_identity == WORLD_LOD_TERRAIN_ATLAS_IDENTITY
                    && !unavailable_quad_indices.contains(&quad.quad_index)
            })
            .collect::<Vec<_>>();
        if quads.is_empty() {
            continue;
        }

        let partial_segment = WorldLodTexturedSegmentPlan {
            layer: segment.layer,
            source_quad_count: segment.source_quad_count,
            quads: quads.iter().map(|quad| (*quad).clone()).collect(),
            unavailable,
        };
        validate_world_lod_textured_segment_coverage(&partial_segment)?;

        let vertex_count = partial_segment.quads.len().checked_mul(4).ok_or_else(|| {
            GalError::invalid_argument("world LOD textured vertex count overflows usize")
        })?;
        let index_count = partial_segment.quads.len().checked_mul(6).ok_or_else(|| {
            GalError::invalid_argument("world LOD textured index count overflows usize")
        })?;
        let mut vertex_bytes = Vec::with_capacity(
            vertex_count
                .checked_mul(WORLD_LOD_TEXTURED_GPU_VERTEX_BYTES)
                .ok_or_else(|| {
                    GalError::invalid_argument("world LOD textured vertex byte count overflows")
                })?,
        );
        let mut index_bytes = Vec::with_capacity(
            index_count
                .checked_mul(std::mem::size_of::<u32>())
                .ok_or_else(|| {
                    GalError::invalid_argument("world LOD textured index byte count overflows")
                })?,
        );
        for quad in &partial_segment.quads {
            let base = u32::try_from(vertex_bytes.len() / WORLD_LOD_TEXTURED_GPU_VERTEX_BYTES)
                .map_err(|_| {
                    GalError::invalid_argument("world LOD textured vertex index exceeds u32")
                })?;
            for vertex in quad.vertices {
                write_world_lod_textured_vertex(&mut vertex_bytes, vertex);
            }
            for index in [base, base + 1, base + 2, base + 2, base + 3, base] {
                index_bytes.extend_from_slice(&index.to_le_bytes());
            }
        }
        debug_assert_eq!(
            vertex_count * WORLD_LOD_TEXTURED_GPU_VERTEX_BYTES,
            vertex_bytes.len()
        );
        let unresolved_index_bytes = (!partial_segment.unavailable.is_empty())
            .then(|| world_lod_unresolved_quad_indices(&partial_segment.unavailable))
            .transpose()?;
        segments.push(WorldLodTexturedGpuSegment {
            source_segment_index,
            layer: segment.layer,
            vertex_layout_version: WORLD_LOD_TEXTURED_GPU_VERTEX_LAYOUT_V2,
            vertex_bytes,
            index_type: IndexType::U32,
            index_bytes,
            unresolved_index_bytes,
        });
    }
    Ok(WorldLodTexturedGpuColumnAsset {
        column_key: plan.column_key,
        column_generation: plan.column_generation,
        segments,
        unavailable_source_segments,
    })
}

pub(super) fn validate_world_lod_textured_segment_coverage(
    segment: &WorldLodTexturedSegmentPlan,
) -> GalResult<()> {
    if segment.source_quad_count == 0 {
        return Err(GalError::invalid_argument(
            "world LOD textured segment has no source quads",
        ));
    }
    let mut covered = vec![false; segment.source_quad_count as usize];
    let mut layers = BTreeSet::new();
    for quad in &segment.quads {
        let slot = covered.get_mut(quad.quad_index as usize).ok_or_else(|| {
            GalError::invalid_argument(
                "world LOD exact-atlas quad index exceeds its source segment",
            )
        })?;
        if !layers.insert((quad.quad_index, quad.face_layer)) {
            return Err(GalError::invalid_argument(
                "world LOD exact-atlas source quad layer is duplicated",
            ));
        }
        *slot = true;
    }
    for unavailable in &segment.unavailable {
        let slot = covered
            .get_mut(unavailable.quad_index as usize)
            .ok_or_else(|| {
                GalError::invalid_argument(
                    "world LOD unresolved quad index exceeds its source segment",
                )
            })?;
        if std::mem::replace(slot, true) {
            return Err(GalError::invalid_argument(
                "world LOD exact-atlas and unresolved streams overlap",
            ));
        }
    }
    if covered.iter().any(|covered| !covered) {
        return Err(GalError::invalid_argument(
            "world LOD textured segment leaves source quads uncovered",
        ));
    }
    Ok(())
}

pub(super) fn world_lod_unresolved_quad_indices(
    unavailable: &[WorldLodTexturedQuadUnavailable],
) -> GalResult<Vec<u8>> {
    let index_count = unavailable.len().checked_mul(6).ok_or_else(|| {
        GalError::invalid_argument("world LOD unresolved index count overflows usize")
    })?;
    let mut bytes = Vec::with_capacity(
        index_count
            .checked_mul(std::mem::size_of::<u32>())
            .ok_or_else(|| {
                GalError::invalid_argument("world LOD unresolved index byte count overflows usize")
            })?,
    );
    for unavailable in unavailable {
        let base = unavailable.quad_index.checked_mul(4).ok_or_else(|| {
            GalError::invalid_argument("world LOD unresolved vertex index overflows u32")
        })?;
        for index in [base, base + 1, base + 2, base + 2, base + 3, base] {
            bytes.extend_from_slice(&index.to_le_bytes());
        }
    }
    Ok(bytes)
}

pub(super) fn write_world_lod_textured_vertex(bytes: &mut Vec<u8>, vertex: WorldLodTexturedVertex) {
    for value in vertex.local_position {
        bytes.extend_from_slice(&value.to_le_bytes());
    }
    for value in vertex.micro_offset {
        bytes.extend_from_slice(&value.to_le_bytes());
    }
    for value in vertex.tile_uv {
        bytes.extend_from_slice(&value.to_le_bytes());
    }
    for value in vertex.atlas_rect {
        bytes.extend_from_slice(&value.to_le_bytes());
    }
    for value in vertex.color_rgba {
        bytes.push((value * 255.0).round().clamp(0.0, 255.0) as u8);
    }
    bytes.extend_from_slice(&[
        vertex.sky_light,
        vertex.block_light,
        face_normal_id(vertex.normal),
        // Keep the private exact-atlas stream compact while retaining both
        // source semantics. DH has sixteen material categories, so one bit
        // for exact face tint plus four category bits fits in this byte.
        (material_category_id(vertex.material) << 1) | u8::from(vertex.tinted),
    ]);
    debug_assert_eq!(0, bytes.len() % WORLD_LOD_TEXTURED_GPU_VERTEX_BYTES);
}

pub(super) fn world_lod_face_tile_coordinates(
    vertices: &[WorldLodExpandedVertex; 4],
    face: u32,
    material: &WorldLodFaceMaterial,
) -> GalResult<[[f32; 2]; 4]> {
    let face = WorldLodFaceNormal::try_from(
        u8::try_from(face).map_err(|_| GalError::invalid_argument("world LOD face exceeds u8"))?,
    )?;
    let mut coordinates = vertices.map(|vertex| match face {
        WorldLodFaceNormal::Down => [vertex.local_position[0], vertex.local_position[2]],
        WorldLodFaceNormal::Up => [-vertex.local_position[0], vertex.local_position[2]],
        WorldLodFaceNormal::North => [vertex.local_position[0], vertex.local_position[1]],
        WorldLodFaceNormal::South => [-vertex.local_position[0], vertex.local_position[1]],
        WorldLodFaceNormal::West => [vertex.local_position[2], vertex.local_position[1]],
        WorldLodFaceNormal::East => [vertex.local_position[2], -vertex.local_position[1]],
    });
    let minimum = coordinates
        .iter()
        .fold([f32::INFINITY; 2], |minimum, value| {
            [minimum[0].min(value[0]), minimum[1].min(value[1])]
        });
    for coordinate in &mut coordinates {
        coordinate[0] -= minimum[0];
        coordinate[1] -= minimum[1];
    }
    if !coordinates.into_iter().flatten().all(f32::is_finite) {
        return Err(GalError::invalid_argument(
            "world LOD exact-atlas quad has non-finite tile coordinates",
        ));
    }
    // Verify the material rotation/mirroring is a square transform before the
    // values reach the private shader. The ABI already rejects duplicates;
    // this makes malformed manual Rust construction fail deterministically.
    for corner in 0..4 {
        let _ = world_lod_face_material_corner(material, corner);
    }
    Ok(coordinates)
}

pub(super) fn world_lod_face_sprite_tile_uv(
    material: &WorldLodFaceMaterial,
    canonical_tile_uv: [f32; 2],
) -> [f32; 2] {
    let origin = world_lod_face_material_corner(material, 0);
    let u_axis = world_lod_face_material_corner(material, 3);
    let v_axis = world_lod_face_material_corner(material, 1);
    [
        origin[0]
            + canonical_tile_uv[0] * (u_axis[0] - origin[0])
            + canonical_tile_uv[1] * (v_axis[0] - origin[0]),
        origin[1]
            + canonical_tile_uv[0] * (u_axis[1] - origin[1])
            + canonical_tile_uv[1] * (v_axis[1] - origin[1]),
    ]
}

pub(super) fn world_lod_face_material_corner(
    material: &WorldLodFaceMaterial,
    corner_index: usize,
) -> [f32; 2] {
    debug_assert!(corner_index < 4);
    let corner = (material.uv_corner_order >> (corner_index * 2)) & 0x3;
    [
        if corner & 0x1 == 0 { 0.0 } else { 1.0 },
        if corner & 0x2 == 0 { 0.0 } else { 1.0 },
    ]
}

/// Maps the compact DH vertex order back to the canonical face-corner order
/// used by the copied Java model-material provenance. DH serializes a quad
/// using `LodQuadBuilder.DIRECTION_VERTEX_IBO_QUAD`; its order is deliberately
/// direction-specific to preserve outward winding. Treating the serialized
/// index as a canonical UV corner rotates or mirrors atlas sprites on four
/// faces even though the semantic sprite identity and atlas region are right.
pub(super) fn world_lod_serialized_vertex_canonical_corner(
    face: u32,
    serialized_vertex_index: usize,
) -> GalResult<usize> {
    if serialized_vertex_index >= 4 {
        return Err(GalError::invalid_argument(
            "world LOD quad vertex index exceeds the four-vertex DH quad contract",
        ));
    }
    let order = match WorldLodFaceNormal::try_from(
        u8::try_from(face).map_err(|_| GalError::invalid_argument("world LOD face exceeds u8"))?,
    )? {
        // DOWN and UP serialize `[10, 11, 01, 00]` in their canonical face
        // axes, while NORTH and SOUTH already serialize canonical corners.
        WorldLodFaceNormal::Down | WorldLodFaceNormal::Up => [3, 2, 1, 0],
        WorldLodFaceNormal::North | WorldLodFaceNormal::South => [0, 1, 2, 3],
        // WEST and EAST serialize `[00, 10, 11, 01]` in their local Z/Y axes.
        WorldLodFaceNormal::West | WorldLodFaceNormal::East => [0, 3, 2, 1],
    };
    Ok(order[serialized_vertex_index])
}

/// Converts typed semantic LOD geometry into a fixed, backend-neutral binary
/// payload. Each little-endian 16-byte vertex retains DH's complete compact
/// semantics: signed i16 XYZ, the three signed micro-offset states, RGBA8,
/// sky/block light, material category, and face. Segments use u16 indices
/// whenever their vertex range permits it and otherwise retain explicit u32
/// indices. Neither representation imports DH's native GL buffer layout.
pub(crate) fn pack_world_lod_gpu_column_asset(
    asset: &WorldLodExpandedColumnAsset,
) -> GalResult<WorldLodGpuColumnAsset> {
    let mut segments = Vec::with_capacity(asset.segments.len());
    for segment in &asset.segments {
        let vertex_count = u32::try_from(segment.vertices.len())
            .map_err(|_| GalError::invalid_argument("world LOD GPU vertex count exceeds u32"))?;
        let index_count = u32::try_from(segment.indices.len())
            .map_err(|_| GalError::invalid_argument("world LOD GPU index count exceeds u32"))?;
        let vertex_capacity = segment
            .vertices
            .len()
            .checked_mul(WORLD_LOD_GPU_VERTEX_BYTES)
            .ok_or_else(|| GalError::invalid_argument("world LOD GPU vertex payload overflows"))?;
        let index_type = if segment.vertices.len() <= (u16::MAX as usize + 1) {
            IndexType::U16
        } else {
            IndexType::U32
        };
        let index_stride = match index_type {
            IndexType::U16 => std::mem::size_of::<u16>(),
            IndexType::U32 => std::mem::size_of::<u32>(),
        };
        let index_capacity = segment
            .indices
            .len()
            .checked_mul(index_stride)
            .ok_or_else(|| GalError::invalid_argument("world LOD GPU index payload overflows"))?;
        let mut vertex_bytes = Vec::with_capacity(vertex_capacity);
        for vertex in &segment.vertices {
            write_vertex(&mut vertex_bytes, vertex);
        }
        let mut index_bytes = Vec::with_capacity(index_capacity);
        for &index in &segment.indices {
            if index as usize >= segment.vertices.len() {
                return Err(GalError::invalid_argument(format!(
                    "world LOD GPU segment index {index} exceeds {} vertices",
                    segment.vertices.len()
                )));
            }
            match index_type {
                IndexType::U16 => index_bytes.extend_from_slice(
                    &u16::try_from(index)
                        .map_err(|_| GalError::invalid_argument("world LOD u16 index overflow"))?
                        .to_le_bytes(),
                ),
                IndexType::U32 => index_bytes.extend_from_slice(&index.to_le_bytes()),
            }
        }
        segments.push(WorldLodGpuSegment {
            layer: segment.layer,
            vertex_layout_version: WORLD_LOD_GPU_VERTEX_LAYOUT_V2,
            vertex_count,
            vertex_bytes,
            index_type,
            index_count,
            index_bytes,
        });
    }
    Ok(WorldLodGpuColumnAsset {
        column_key: asset.column_key,
        column_generation: asset.column_generation,
        origin: asset.origin,
        segments,
    })
}

/// Packs the ordinary reduced-color stream without retaining a second,
/// expanded vertex/index copy. Exact-atlas columns still use the expanded
/// representation for their provenance and source-material checks.
pub(crate) fn pack_world_lod_gpu_column_asset_from_compact(
    asset: &WorldLodColumnAsset,
) -> GalResult<WorldLodGpuColumnAsset> {
    validate_world_lod_column_asset(asset)?;
    let mut segments = Vec::with_capacity(asset.segments.len());
    for segment in &asset.segments {
        let vertex_count = u32::try_from(segment.vertices.len())
            .map_err(|_| GalError::invalid_argument("world LOD GPU vertex count exceeds u32"))?;
        let index_count = u32::try_from(segment.vertices.len() / 4)
            .ok()
            .and_then(|quads| quads.checked_mul(6))
            .ok_or_else(|| GalError::invalid_argument("world LOD GPU index count exceeds u32"))?;
        let vertex_capacity = segment
            .vertices
            .len()
            .checked_mul(WORLD_LOD_GPU_VERTEX_BYTES)
            .ok_or_else(|| GalError::invalid_argument("world LOD GPU vertex payload overflows"))?;
        let index_type = if segment.vertices.len() <= (u16::MAX as usize + 1) {
            IndexType::U16
        } else {
            IndexType::U32
        };
        let index_stride = match index_type {
            IndexType::U16 => std::mem::size_of::<u16>(),
            IndexType::U32 => std::mem::size_of::<u32>(),
        };
        let index_capacity = usize::try_from(index_count)
            .ok()
            .and_then(|count| count.checked_mul(index_stride))
            .ok_or_else(|| GalError::invalid_argument("world LOD GPU index payload overflows"))?;
        let mut vertex_bytes = Vec::with_capacity(vertex_capacity);
        for vertex in &segment.vertices {
            write_compact_vertex(&mut vertex_bytes, vertex);
        }
        let mut index_bytes = Vec::with_capacity(index_capacity);
        for quad in 0..segment.vertices.len() / 4 {
            let base = u32::try_from(quad)
                .ok()
                .and_then(|quad| quad.checked_mul(4))
                .ok_or_else(|| GalError::invalid_argument("world LOD vertex index exceeds u32 range"))?;
            for index in [base, base + 1, base + 2, base + 2, base + 3, base] {
                match index_type {
                    IndexType::U16 => index_bytes.extend_from_slice(
                        &u16::try_from(index)
                            .map_err(|_| GalError::invalid_argument("world LOD u16 index overflow"))?
                            .to_le_bytes(),
                    ),
                    IndexType::U32 => index_bytes.extend_from_slice(&index.to_le_bytes()),
                }
            }
        }
        segments.push(WorldLodGpuSegment {
            layer: segment.layer,
            vertex_layout_version: WORLD_LOD_GPU_VERTEX_LAYOUT_V2,
            vertex_count,
            vertex_bytes,
            index_type,
            index_count,
            index_bytes,
        });
    }
    Ok(WorldLodGpuColumnAsset {
        column_key: asset.column_key,
        column_generation: asset.column_generation,
        origin: asset.origin,
        segments,
    })
}

pub(super) fn write_compact_vertex(bytes: &mut Vec<u8>, vertex: &WorldLodVertex) {
    let mut encoded = [0_u8; WORLD_LOD_GPU_VERTEX_BYTES];
    for (axis, component) in vertex.local_position.into_iter().enumerate() {
        debug_assert!(component <= i16::MAX as u16);
        encoded[axis * 2..axis * 2 + 2].copy_from_slice(&(component as i16).to_le_bytes());
    }
    let micro = (vertex.packed_light_and_micro_offset >> 8) as u8;
    let canonical_axis = |axis: u8| if axis & 0b10 != 0 { 0b10 } else { axis & 0b01 };
    encoded[6] = canonical_axis(micro & 0b11)
        | (canonical_axis((micro >> 2) & 0b11) << 2)
        | (canonical_axis((micro >> 4) & 0b11) << 4);
    encoded[8..12].copy_from_slice(&vertex.color_rgba);
    encoded[12] = (vertex.packed_light_and_micro_offset & 0x0f) as u8;
    encoded[13] = ((vertex.packed_light_and_micro_offset >> 4) & 0x0f) as u8;
    encoded[14] = vertex.material_id;
    encoded[15] = vertex.normal_index;
    bytes.extend_from_slice(&encoded);
}

pub(crate) fn validate_expanded_instance(
    column: &WorldLodExpandedColumnAsset,
    segment_index: u32,
    layer: u32,
) -> GalResult<()> {
    let segment = column.segments.get(segment_index as usize).ok_or_else(|| {
        GalError::invalid_argument(format!(
            "expanded world LOD column {} is missing segment {segment_index}",
            column.column_key
        ))
    })?;
    if segment.layer != layer {
        return Err(GalError::invalid_argument(
            "expanded world LOD segment layer differs from visible instance layer",
        ));
    }
    Ok(())
}

pub(super) fn expand_segment(segment: &WorldLodSegment) -> GalResult<WorldLodExpandedSegment> {
    let mut vertices = Vec::with_capacity(segment.vertices.len());
    let mut indices = Vec::with_capacity(segment.vertices.len() / 4 * 6);
    for (vertex_index, vertex) in segment.vertices.iter().enumerate() {
        vertices.push(expand_vertex(vertex)?);
        if vertex_index % 4 == 3 {
            let base = u32::try_from(vertex_index - 3).map_err(|_| {
                GalError::invalid_argument("world LOD vertex index exceeds u32 range")
            })?;
            indices.extend_from_slice(&[base, base + 1, base + 2, base + 2, base + 3, base]);
        }
    }
    Ok(WorldLodExpandedSegment {
        layer: segment.layer,
        vertices,
        indices,
    })
}

pub(super) fn expand_vertex(vertex: &WorldLodVertex) -> GalResult<WorldLodExpandedVertex> {
    if vertex.normal_index > WORLD_LOD_MAX_NORMAL_INDEX {
        return Err(GalError::invalid_argument(format!(
            "world LOD normal index {} is outside the six DH face directions",
            vertex.normal_index
        )));
    }
    let light = vertex.packed_light_and_micro_offset;
    let micro = (light >> 8) as u8;
    Ok(WorldLodExpandedVertex {
        local_position: vertex.local_position.map(f32::from),
        micro_offset: [
            decode_micro_axis(micro & 0b11),
            decode_micro_axis((micro >> 2) & 0b11),
            decode_micro_axis((micro >> 4) & 0b11),
        ],
        color_rgba: vertex.color_rgba.map(|channel| channel as f32 / 255.0),
        sky_light: (light & 0x0f) as u8,
        block_light: ((light >> 4) & 0x0f) as u8,
        material: WorldLodMaterialCategory::try_from(vertex.material_id)?,
        normal: WorldLodFaceNormal::try_from(vertex.normal_index)?,
    })
}

pub(super) fn decode_micro_axis(bits: u8) -> f32 {
    // This intentionally matches DH's GLSL: a negative flag wins when both
    // bits are set, which is how its CPU builder represents a negative offset.
    if bits & 0b10 != 0 {
        -MICRO_OFFSET_SCALE
    } else if bits & 0b01 != 0 {
        MICRO_OFFSET_SCALE
    } else {
        0.0
    }
}

pub(super) fn write_vertex(bytes: &mut Vec<u8>, vertex: &WorldLodExpandedVertex) {
    for component in vertex.local_position {
        debug_assert!(component.fract() == 0.0);
        debug_assert!((i16::MIN as f32..=i16::MAX as f32).contains(&component));
        bytes.extend_from_slice(&(component as i16).to_le_bytes());
    }
    let micro = encode_micro_axis(vertex.micro_offset[0])
        | (encode_micro_axis(vertex.micro_offset[1]) << 2)
        | (encode_micro_axis(vertex.micro_offset[2]) << 4);
    bytes.extend_from_slice(&[micro, 0]);
    for component in vertex.color_rgba {
        bytes.push((component * 255.0).round().clamp(0.0, 255.0) as u8);
    }
    bytes.extend_from_slice(&[
        vertex.sky_light,
        vertex.block_light,
        material_category_id(vertex.material),
        face_normal_id(vertex.normal),
    ]);
    debug_assert_eq!(0, bytes.len() % WORLD_LOD_GPU_VERTEX_BYTES);
}

pub(super) fn encode_micro_axis(value: f32) -> u8 {
    if value < 0.0 {
        0b10
    } else if value > 0.0 {
        0b01
    } else {
        0
    }
}

pub(crate) fn decode_world_lod_gpu_vertex(bytes: &[u8]) -> Option<WorldLodExpandedVertex> {
    if bytes.len() != WORLD_LOD_GPU_VERTEX_BYTES {
        return None;
    }
    let local_position = [
        i16::from_le_bytes(bytes[0..2].try_into().ok()?) as f32,
        i16::from_le_bytes(bytes[2..4].try_into().ok()?) as f32,
        i16::from_le_bytes(bytes[4..6].try_into().ok()?) as f32,
    ];
    let micro = bytes[6];
    let color_rgba = [bytes[8], bytes[9], bytes[10], bytes[11]].map(|value| value as f32 / 255.0);
    Some(WorldLodExpandedVertex {
        local_position,
        micro_offset: [
            decode_micro_axis(micro & 0b11),
            decode_micro_axis((micro >> 2) & 0b11),
            decode_micro_axis((micro >> 4) & 0b11),
        ],
        color_rgba,
        sky_light: bytes[12],
        block_light: bytes[13],
        material: WorldLodMaterialCategory::try_from(bytes[14]).ok()?,
        normal: WorldLodFaceNormal::try_from(bytes[15]).ok()?,
    })
}

pub(crate) fn material_category_id(value: WorldLodMaterialCategory) -> u8 {
    match value {
        WorldLodMaterialCategory::Unknown => 0,
        WorldLodMaterialCategory::Leaves => 1,
        WorldLodMaterialCategory::Stone => 2,
        WorldLodMaterialCategory::Wood => 3,
        WorldLodMaterialCategory::Metal => 4,
        WorldLodMaterialCategory::Dirt => 5,
        WorldLodMaterialCategory::Lava => 6,
        WorldLodMaterialCategory::Deepslate => 7,
        WorldLodMaterialCategory::Snow => 8,
        WorldLodMaterialCategory::Sand => 9,
        WorldLodMaterialCategory::Terracotta => 10,
        WorldLodMaterialCategory::NetherStone => 11,
        WorldLodMaterialCategory::Water => 12,
        WorldLodMaterialCategory::Grass => 13,
        WorldLodMaterialCategory::Air => 14,
        WorldLodMaterialCategory::Illuminated => 15,
    }
}

pub(crate) fn face_normal_id(value: WorldLodFaceNormal) -> u8 {
    match value {
        WorldLodFaceNormal::Down => 0,
        WorldLodFaceNormal::Up => 1,
        WorldLodFaceNormal::North => 2,
        WorldLodFaceNormal::South => 3,
        WorldLodFaceNormal::West => 4,
        WorldLodFaceNormal::East => 5,
    }
}

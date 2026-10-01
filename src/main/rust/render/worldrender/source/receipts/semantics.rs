//! Semantic JSON of meshes, entities, materials and first-person transforms; transform probes.

use super::*;

/// Capture-only receipt for the first-person pose boundary. This records the
/// semantic transform payload without exposing GPU handles or changing draw
/// behavior, making Java/Rust matrix layout errors directly inspectable.
pub(crate) fn first_person_transform_semantics_json(
    frontend: &WorldPrimitiveFrontend,
    frame: &WorldPrimitiveFrame,
) -> String {
    let matrix = |values: &[f32; 16]| {
        values
            .iter()
            .map(|value| value.to_string())
            .collect::<Vec<_>>()
            .join(",")
    };
    let instance = frame.first_person_mesh_instances.first();
    let projected_bounds = instance
        .and_then(|value| frontend.mesh_assets.get(&value.mesh_key))
        .map(|asset| {
            let mut min = [f32::INFINITY; 3];
            let mut max = [f32::NEG_INFINITY; 3];
            for vertex in asset.vertex_bytes.chunks_exact(TERRAIN_SOURCE_VERTEX_BYTES) {
                let read = |offset: usize| {
                    f32::from_le_bytes(vertex[offset..offset + 4].try_into().unwrap())
                };
                let p = [read(0), read(4), read(8), 1.0];
                let model = instance.map(|value| &value.transform);
                let q = |row: usize| {
                    model
                        .map(|m| {
                            m[row] * p[0]
                                + m[4 + row] * p[1]
                                + m[8 + row] * p[2]
                                + m[12 + row] * p[3]
                        })
                        .unwrap_or(p[row])
                };
                let transformed = [q(0), q(1), q(2), q(3)];
                let v = |row: usize| {
                    frame.first_person.model_view_matrix[row] * transformed[0]
                        + frame.first_person.model_view_matrix[4 + row] * transformed[1]
                        + frame.first_person.model_view_matrix[8 + row] * transformed[2]
                        + frame.first_person.model_view_matrix[12 + row] * transformed[3]
                };
                let view = [v(0), v(1), v(2), v(3)];
                let c = |row: usize| {
                    frame.first_person.projection_matrix[row] * view[0]
                        + frame.first_person.projection_matrix[4 + row] * view[1]
                        + frame.first_person.projection_matrix[8 + row] * view[2]
                        + frame.first_person.projection_matrix[12 + row] * view[3]
                };
                let clip = [c(0), c(1), c(2), c(3)];
                if clip[3].is_finite() && clip[3].abs() > 1.0e-6 {
                    for axis in 0..3 {
                        let ndc = clip[axis] / clip[3];
                        min[axis] = min[axis].min(ndc);
                        max[axis] = max[axis].max(ndc);
                    }
                }
            }
            format!(
                "[[{},{},{}],[{},{},{}]]",
                min[0], min[1], min[2], max[0], max[1], max[2]
            )
        })
        .unwrap_or_else(|| "null".to_string());
    format!(
        "{{\"projection\":[{}],\"model_view\":[{}],\"instance_transform\":{},\"instance_translation\":{},\"projected_bounds_with_instance\":{}}}",
        matrix(&frame.first_person.projection_matrix),
        matrix(&frame.first_person.model_view_matrix),
        instance
            .map(|value| format!("[{}]", matrix(&value.transform)))
            .unwrap_or_else(|| "null".to_string()),
        instance
            .map(|value| format!(
                "[{},{},{}]",
                value.transform[12], value.transform[13], value.transform[14]
            ))
            .unwrap_or_else(|| "null".to_string()),
        projected_bounds,
    )
}

pub(crate) fn terrain_program_scope_for_sky_type(sky_type: u32) -> GalResult<Option<TerrainProgramScope>> {
    match sky_type {
        WORLD_BACKGROUND_SKY_OVERWORLD => Ok(Some(TerrainProgramScope::Overworld)),
        WORLD_BACKGROUND_SKY_NETHER => Ok(Some(TerrainProgramScope::Nether)),
        WORLD_BACKGROUND_SKY_END => Ok(Some(TerrainProgramScope::End)),
        // A custom dimension requires an explicit semantic pack-to-dimension
        // mapping. Do not guess from an active Java/Iris program.
        WORLD_BACKGROUND_SKY_CUSTOM => Ok(None),
        value => Err(GalError::invalid_argument(format!(
            "unknown world sky type {value} for shader-pack terrain selection"
        ))),
    }
}

pub(crate) fn float_json_values(values: &[f32]) -> String {
    let values = values
        .iter()
        .map(|value| {
            if value.is_finite() {
                format!("{value:.7}")
            } else {
                "null".to_string()
            }
        })
        .collect::<Vec<_>>();
    format!("[{}]", values.join(","))
}

#[derive(Clone, Debug)]
pub(crate) struct SelectedSourceTerrainTransformProbe {
    pub(in crate::render::worldrender) mesh_key: u64,
    pub(in crate::render::worldrender) mesh_generation: u64,
    pub(in crate::render::worldrender) material_mode: u32,
    pub(in crate::render::worldrender) section_index: u32,
    pub(in crate::render::worldrender) source_vertex_index: u32,
    pub(in crate::render::worldrender) source_position: [f32; 4],
    pub(in crate::render::worldrender) model_transform: [f32; 16],
    pub(in crate::render::worldrender) model_position: [f32; 4],
    pub(in crate::render::worldrender) view_position: [f32; 4],
    pub(in crate::render::worldrender) clip_position: [f32; 4],
    pub(in crate::render::worldrender) ndc_position: [f32; 3],
}

pub(crate) const SELECTED_SOURCE_TERRAIN_TRANSFORM_PROBE_LIMIT: usize = 12;

pub(in crate::render::worldrender) fn collect_selected_source_terrain_transform_probes(
    probes: &mut Vec<SelectedSourceTerrainTransformProbe>,
    frame: &WorldPrimitiveFrame,
    batch: &MeshBatch,
    prepared: &PreparedSourceTerrainFrame,
) -> GalResult<()> {
    // Diagnostic-only receipt. A mesh whose GPU geometry is resident is
    // cached without its converted bytes, so it cannot be probed here.
    if probes.len() >= SELECTED_SOURCE_TERRAIN_TRANSFORM_PROBE_LIMIT
        || prepared.mesh.index_bytes.is_empty()
    {
        return Ok(());
    }
    let instance_index = *batch.indices.first().ok_or_else(|| {
        GalError::invalid_argument("selected-source terrain batch has no semantic instance")
    })?;
    let instance = frame.mesh_instances.get(instance_index).ok_or_else(|| {
        GalError::invalid_argument("selected-source terrain batch instance is absent from frame")
    })?;
    let section_index = *prepared.section_indices.first().ok_or_else(|| {
        GalError::invalid_argument("selected-source terrain frame selected no mesh sections")
    })?;
    let section = prepared
        .mesh
        .sections
        .get(section_index as usize)
        .ok_or_else(|| {
            GalError::invalid_argument("selected-source terrain probe section is absent")
        })?;
    let first_index_offset = usize::try_from(section.index_offset).map_err(|_| {
        GalError::invalid_argument("selected-source terrain probe index offset overflows")
    })?;
    let first_index_bytes = prepared
        .mesh
        .index_bytes
        .get(first_index_offset..first_index_offset + std::mem::size_of::<u32>())
        .ok_or_else(|| {
            GalError::invalid_argument("selected-source terrain probe index is absent")
        })?;
    let source_vertex_index = u32::from_ne_bytes(
        first_index_bytes
            .try_into()
            .expect("fixed u32 source terrain index length"),
    );
    let vertex_offset = usize::try_from(source_vertex_index)
        .ok()
        .and_then(|index| index.checked_mul(TERRAIN_SOURCE_VERTEX_BYTES))
        .ok_or_else(|| {
            GalError::invalid_argument("selected-source terrain probe vertex offset overflows")
        })?;
    let position_bytes = prepared
        .mesh
        .vertex_bytes
        .get(vertex_offset..vertex_offset + 16)
        .ok_or_else(|| {
            GalError::invalid_argument("selected-source terrain probe vertex is absent")
        })?;
    let source_position = std::array::from_fn(|component| {
        let offset = component * 4;
        f32::from_ne_bytes(
            position_bytes[offset..offset + 4]
                .try_into()
                .expect("fixed source terrain position lane"),
        )
    });
    let model_position = transform_column_major_vec4(instance.transform, source_position);
    let view_position = transform_column_major_vec4(frame.view_matrix, model_position);
    let clip_position = transform_column_major_vec4(frame.projection_matrix, view_position);
    let ndc_position = if clip_position[3].abs() > f32::EPSILON {
        [
            clip_position[0] / clip_position[3],
            clip_position[1] / clip_position[3],
            clip_position[2] / clip_position[3],
        ]
    } else {
        [f32::NAN; 3]
    };
    probes.push(SelectedSourceTerrainTransformProbe {
        mesh_key: batch.key.mesh_key,
        mesh_generation: batch.key.mesh_generation,
        material_mode: batch.key.material_mode,
        section_index,
        source_vertex_index,
        source_position,
        model_transform: instance.transform,
        model_position,
        view_position,
        clip_position,
        ndc_position,
    });
    Ok(())
}

impl WorldPrimitiveFrontend {
    /// Writes a bounded CPU-side receipt for an explicitly opted-in source
    /// shader probe. It proves the semantic scalar bytes before upload without
    /// exposing backend handles or changing the selected-source route.
    pub(crate) fn write_selected_source_scalar_uniform_receipt(
        &self,
        frame_id: u64,
        program: &LoweredTerrainSourceProgram,
        bytes: &[u8],
    ) {
        if std::env::var_os("MATTMC_RUST_SELECTED_SOURCE_FRAGMENT_PROBE").is_none()
            || !self.source_execution_enabled()
            || !matches!(
                std::env::var("MATTMC_GRAPHICS_AUDIT")
                    .as_deref()
                    .map(str::trim),
                Ok("1") | Ok("true") | Ok("TRUE")
            )
        {
            return;
        }
        let Some(dir) = std::env::var_os("MATTMC_TERRAIN_PASS_CONTRACT_DIAGNOSTIC_DIR") else {
            return;
        };
        let fields = program
            .execution_interface
            .scalar_uniform_fields
            .iter()
            .filter(|field| {
                matches!(
                    field.name(),
                    "viewWidth" | "viewHeight" | "gbufferProjection" | "gbufferProjectionInverse"
                )
            })
            .map(|field| {
                let offset = field.offset() as usize;
                let bits = bytes
                    .get(offset..offset.saturating_add(4))
                    .and_then(|value| value.try_into().ok())
                    .map(u32::from_le_bytes)
                    .unwrap_or_default();
                format!(
                    "{{\"name\":\"{}\",\"offset\":{},\"size\":{},\"first_u32\":{},\"first_f32\":{}}}",
                    field.name(),
                    field.offset(),
                    field.size(),
                    bits,
                    f32::from_bits(bits),
                )
            })
            .collect::<Vec<_>>();
        let has_viewport_fields = program
            .execution_interface
            .scalar_uniform_fields
            .iter()
            .any(|field| matches!(field.name(), "viewWidth" | "viewHeight"));
        if !has_viewport_fields {
            return;
        }
        if std::fs::create_dir_all(&dir).is_ok() {
            let _ = std::fs::write(
                Path::new(&dir).join("selected-source-scalar-uniform-receipt.json"),
                format!(
                    "{{\"frame_id\":{},\"program\":\"{}\",\"byte_len\":{},\"fields\":[{}]}}",
                    frame_id,
                    program.identity.as_str(),
                    bytes.len(),
                    fields.join(","),
                ),
            );
        }
    }

    /// Retains bounded semantic transform evidence for the actual source
    /// terrain batches. This is diagnostic-only: it mirrors the lowered
    /// `gbufferProjection * gbufferModelView * model_transform * position`
    /// expression without exposing a pipeline, descriptor, or backend object.
    pub(crate) fn write_selected_source_terrain_transform_receipt(
        &self,
        frame: &WorldPrimitiveFrame,
        probes: &[SelectedSourceTerrainTransformProbe],
    ) {
        if probes.is_empty()
            || !self.source_execution_enabled()
            || !matches!(
                std::env::var("MATTMC_GRAPHICS_AUDIT")
                    .as_deref()
                    .map(str::trim),
                Ok("1") | Ok("true") | Ok("TRUE")
            )
        {
            return;
        }
        let Some(dir) = std::env::var_os("MATTMC_TERRAIN_PASS_CONTRACT_DIAGNOSTIC_DIR") else {
            return;
        };
        let records = probes
            .iter()
            .map(|probe| {
                format!(
                    concat!(
                        "{{\"mesh_key\":{},\"mesh_generation\":{},\"material_mode\":{},",
                        "\"section_index\":{},\"source_vertex_index\":{},",
                        "\"source_position\":{},\"model_transform\":{},",
                        "\"model_position\":{},\"view_position\":{},",
                        "\"clip_position\":{},\"ndc_position\":{}}}"
                    ),
                    probe.mesh_key,
                    probe.mesh_generation,
                    probe.material_mode,
                    probe.section_index,
                    probe.source_vertex_index,
                    float_json_values(&probe.source_position),
                    matrix4_json_array(probe.model_transform),
                    float_json_values(&probe.model_position),
                    float_json_values(&probe.view_position),
                    float_json_values(&probe.clip_position),
                    float_json_values(&probe.ndc_position),
                )
            })
            .collect::<Vec<_>>();
        let dir = Path::new(&dir);
        if std::fs::create_dir_all(dir).is_ok() {
            let _ = std::fs::write(
                dir.join(format!(
                    "selected-source-terrain-transform-frame-{}.json",
                    frame.frame_id
                )),
                format!(
                    concat!(
                        "{{\"frame_id\":{},\"correlation_id\":{},\"viewport\":[{},{}],",
                        "\"view_matrix\":{},\"projection_matrix\":{},\"probes\":[{}]}}\n"
                    ),
                    frame.frame_id,
                    frame.correlation_id,
                    frame.viewport_width,
                    frame.viewport_height,
                    matrix4_json_array(frame.view_matrix),
                    matrix4_json_array(frame.projection_matrix),
                    records.join(","),
                ),
            );
        }
    }
}

/// Bounded source-instance receipt for the mesh family shared by terrain,
/// static displays, and moving blocks. It proves the copied semantic color
/// modulation that the selected source ABI consumes without exposing asset,
/// pipeline, or native resource identities. The fixed sample bound keeps a
/// diagnostic capture from scaling with the world's visible mesh count.
pub(crate) fn source_mesh_instance_semantics_json(frame: &WorldPrimitiveFrame) -> String {
    let mut source_instances = 0u64;
    let mut non_white_color_instances = 0u64;
    let mut translucent_instances = 0u64;
    let mut terrain_instances = 0u64;
    let mut opaque_geometry_instances = 0u64;
    let mut moving_mesh_instances = 0u64;
    let mut records = Vec::new();
    for instance in frame
        .mesh_instances
        .iter()
        .filter(|instance| is_source_terrain_mesh_stratum(instance.stratum))
    {
        source_instances = source_instances.saturating_add(1);
        match instance.stratum {
            WORLD_STRATUM_TERRAIN => terrain_instances = terrain_instances.saturating_add(1),
            WORLD_STRATUM_OPAQUE_TEXTURED_GEOMETRY => {
                opaque_geometry_instances = opaque_geometry_instances.saturating_add(1)
            }
            WORLD_STRATUM_ORDINARY_BLOCK => {
                opaque_geometry_instances = opaque_geometry_instances.saturating_add(1)
            }
            WORLD_STRATUM_MOVING_MESH => {
                moving_mesh_instances = moving_mesh_instances.saturating_add(1)
            }
            _ => unreachable!("source mesh stratum was filtered above"),
        }
        if instance.color_argb != u32::MAX {
            non_white_color_instances = non_white_color_instances.saturating_add(1);
        }
        if instance.depth_policy == WORLD_DEPTH_POLICY_TEST_NO_WRITE {
            translucent_instances = translucent_instances.saturating_add(1);
        }
        if records.len() < 16 {
            let color = argb_to_rgba(instance.color_argb);
            records.push(format!(
                concat!(
                    "{{\"stratum\":{},\"mesh_key\":{},\"mesh_generation\":{},",
                    "\"section_index\":{},\"color_argb\":{},",
                    "\"color_rgba\":[{},{},{},{}],\"depth_policy\":{},",
                    "\"cull_policy\":{},\"winding\":{}}}"
                ),
                instance.stratum,
                instance.mesh_key,
                instance.mesh_generation,
                instance.mesh_section_index,
                instance.color_argb,
                color[0],
                color[1],
                color[2],
                color[3],
                instance.depth_policy,
                instance.cull_policy,
                instance.winding,
            ));
        }
    }
    format!(
        concat!(
            "{{\"source_instances\":{},",
            "\"terrain_instances\":{},\"opaque_geometry_instances\":{},",
            "\"moving_mesh_instances\":{},",
            "\"non_white_color_instances\":{},",
            "\"test_no_write_instances\":{},",
            "\"instance_record_bytes\":{},",
            "\"records\":[{}]}}"
        ),
        source_instances,
        terrain_instances,
        opaque_geometry_instances,
        moving_mesh_instances,
        non_white_color_instances,
        translucent_instances,
        TERRAIN_SOURCE_INSTANCE_BYTES,
        records.join(","),
    )
}

impl WorldPrimitiveFrontend {
    /// The source writer can privately expand a copied mesh, so capture
    /// correlation uses its canonical entity identity rather than a transient
    /// Java-side mesh key.
    pub(crate) fn source_entity_instance_semantics_json(&self, frame: &WorldPrimitiveFrame) -> String {
        let mut instances = 0u64;
        let mut records = Vec::new();
        for instance in frame
            .mesh_instances
            .iter()
            .filter(|instance| instance.stratum == WORLD_STRATUM_ENTITY_MESH)
        {
            instances = instances.saturating_add(1);
            if records.len() < 16 {
                let color = argb_to_rgba(instance.color_argb);
                let entity_identity = self
                    .mesh_assets
                    .get(&instance.mesh_key)
                    .map(|asset| asset.entity_identity.as_str())
                    .unwrap_or("");
                let section_semantics = self
                    .mesh_assets
                    .get(&instance.mesh_key)
                    .map(|asset| {
                        asset
                            .sections
                            .iter()
                            .take(16)
                            .map(|section| {
                                format!(
                                    "{{\"texture_id\":{},\"material_mode\":{},\"depth_policy\":{},\"cull_policy\":{},\"winding\":{}}}",
                                    section.texture_id,
                                    section.material_mode,
                                    instance.depth_policy,
                                    section.cull_policy,
                                    section.winding,
                                )
                            })
                            .collect::<Vec<_>>()
                            .join(",")
                    })
                    .unwrap_or_default();
                let texture_receipt = self
                    .mesh_texture_assets
                    .get(&self
                        .mesh_assets
                        .get(&instance.mesh_key)
                        .and_then(|asset| asset.sections.first())
                        .map(|section| section.texture_id)
                        .unwrap_or_default())
                    .map(|texture| {
                        let alpha_min = texture.rgba.chunks_exact(4).map(|pixel| pixel[3]).min().unwrap_or(0);
                        let alpha_max = texture.rgba.chunks_exact(4).map(|pixel| pixel[3]).max().unwrap_or(0);
                        let alpha_nonzero = texture.rgba.chunks_exact(4).filter(|pixel| pixel[3] != 0).count();
                        format!(
                            "\"texture_width\":{},\"texture_height\":{},\"texture_bytes\":{},\"texture_hash\":\"{:08x}\",\"texture_alpha_min\":{},\"texture_alpha_max\":{},\"texture_alpha_nonzero\":{},",
                            texture.width,
                            texture.height,
                            texture.rgba.len(),
                            xxh32(&texture.rgba, 0x5a4f4d42),
                            alpha_min,
                            alpha_max,
                            alpha_nonzero,
                        )
                    })
                    .unwrap_or_default();
                records.push(format!(
                    concat!(
                        "{{\"mesh_key\":{},\"mesh_generation\":{},",
                        "\"section_index\":{},\"color_argb\":{},",
                        "\"entity_identity\":\"{}\",",
                        "\"color_rgba\":[{},{},{},{}],\"depth_policy\":{},",
                        "\"cull_policy\":{},\"winding\":{},",
                        "\"translation\":[{},{},{}],{}\"section_semantics\":[{}]}}"
                    ),
                    instance.mesh_key,
                    instance.mesh_generation,
                    instance.mesh_section_index,
                    instance.color_argb,
                    json_escape(entity_identity),
                    color[0],
                    color[1],
                    color[2],
                    color[3],
                    instance.depth_policy,
                    instance.cull_policy,
                    instance.winding,
                    instance.transform[12],
                    instance.transform[13],
                    instance.transform[14],
                    texture_receipt,
                    section_semantics,
                ));
            }
        }
        format!(
            concat!(
                "{{\"instances\":{},\"instance_record_bytes\":{},",
                "\"records\":[{}]}}"
            ),
            instances,
            TERRAIN_SOURCE_INSTANCE_BYTES,
            records.join(","),
        )
    }
}

/// Converts only fully classified generic material records into Rust-owned
/// source primitives. This is deliberately kept before source-plan admission:
/// successful staging never means a pack program ran, and an unspecified
/// producer cannot be guessed into a selected source frame.
/// Bounded semantic receipt for source-material work. It deliberately records
/// only producer-owned identity and UV-space classification, never a backend
/// resource handle or shader binding slot.
pub(crate) fn source_material_semantics_json(frame: &WorldPrimitiveFrame) -> String {
    let mut atlas_quads = 0u64;
    let mut local_quads = 0u64;
    let mut unsupported_quads = 0u64;
    let mut textured_quads = 0u64;
    let mut particle_quads = 0u64;
    let mut weather_quads = 0u64;
    let mut cloud_quads = 0u64;
    let mut records = BTreeMap::<(u32, u32, u32, u32), u64>::new();
    for quad in &frame.material_quads {
        if !matches!(
            quad.source_program,
            WORLD_MATERIAL_SOURCE_TEXTURED
                | WORLD_MATERIAL_SOURCE_ENTITY_MODEL
                | WORLD_MATERIAL_SOURCE_PARTICLES
                | WORLD_MATERIAL_SOURCE_WEATHER
                | WORLD_MATERIAL_SOURCE_CLOUDS
        ) {
            continue;
        }
        match quad.source_program {
            WORLD_MATERIAL_SOURCE_TEXTURED => textured_quads += 1,
            WORLD_MATERIAL_SOURCE_ENTITY_MODEL => textured_quads += 1,
            WORLD_MATERIAL_SOURCE_PARTICLES => particle_quads += 1,
            WORLD_MATERIAL_SOURCE_WEATHER => weather_quads += 1,
            WORLD_MATERIAL_SOURCE_CLOUDS => cloud_quads += 1,
            _ => unreachable!("source-material family was filtered above"),
        }
        match quad.source_uv_space {
            WORLD_MATERIAL_SOURCE_UV_MINECRAFT_BLOCK_ATLAS => atlas_quads += 1,
            WORLD_MATERIAL_SOURCE_UV_LOCAL_TEXTURE => local_quads += 1,
            _ => unsupported_quads += 1,
        }
        *records
            .entry((
                quad.source_program,
                quad.material_id,
                quad.texture_id,
                quad.source_uv_space,
            ))
            .or_default() += 1;
    }
    let records = records
        .into_iter()
        .take(16)
        .map(|((source_program, material_id, texture_id, uv_space), quads)| {
            format!(
                "{{\"source_program\":{source_program},\"material_id\":{material_id},\"texture_id\":{texture_id},\"uv_space\":{uv_space},\"quads\":{quads}}}"
            )
        })
        .collect::<Vec<_>>()
        .join(",");
    format!(
        "{{\"textured_quads\":{textured_quads},\"particle_quads\":{particle_quads},\"weather_quads\":{weather_quads},\"cloud_quads\":{cloud_quads},\"atlas_quads\":{atlas_quads},\"local_quads\":{local_quads},\"unsupported_quads\":{unsupported_quads},\"records\":[{records}]}}"
    )
}

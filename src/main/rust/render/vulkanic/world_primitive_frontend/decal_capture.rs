//! Bounded observation of an actual selected command stream. Never a GPU
//! readback or admission decision: semantic inputs and bound uploads are
//! reported separately, with the accepted submission added by the owner.
use super::*;
use serde_json::{json, Value};

const MAX_RECORDS: usize = 64;

fn invalid(message: &str) -> GalError {
    GalError::invalid_argument(message)
}

/// Find the last overlapping host write before this draw. A partial overwrite
/// cannot be ignored in favor of an earlier, conveniently complete payload.
pub(super) fn uploaded_range(
    ops: &[CommandOp],
    buffer: Handle,
    start: u64,
    size: usize,
) -> GalResult<&[u8]> {
    let end = start
        .checked_add(size as u64)
        .ok_or_else(|| invalid("decal capture range overflow"))?;
    for op in ops.iter().rev() {
        if let CommandOp::HostWriteBuffer {
            buffer: written,
            offset,
            data,
        } = op
        {
            if *written != buffer {
                continue;
            }
            let write_end = offset
                .checked_add(data.len() as u64)
                .ok_or_else(|| invalid("decal capture write overflow"))?;
            if *offset >= end || write_end <= start {
                continue;
            }
            if *offset > start || write_end < end {
                return Err(invalid("decal capture overlapping partial upload"));
            }
            return Ok(&data[(start - offset) as usize..(end - offset) as usize]);
        }
    }
    Err(invalid("decal capture missing preceding upload"))
}

pub(super) fn floats(bytes: &[u8]) -> GalResult<Vec<f32>> {
    let values: Vec<_> = bytes
        .chunks_exact(4)
        .map(|b| f32::from_le_bytes(b.try_into().unwrap()))
        .collect();
    if bytes.len() % 4 != 0 || values.iter().any(|v| !v.is_finite()) {
        return Err(invalid("decal capture non-finite or incomplete floats"));
    }
    Ok(values)
}

pub(super) fn observe(
    gal: &VulkanicGal,
    frontend: &WorldPrimitiveFrontend,
    frame: &WorldPrimitiveFrame,
    ops: &[CommandOp],
) -> Value {
    match observe_inner(gal, frontend, frame, ops) {
        Ok((semantics, draws)) => json!({"schema":"world-decal-submission-inputs-v1",
            "complete":true,"gpu_readback":false,"capability_admitted":false,
            "semantic_instances":semantics,"draws":draws}),
        Err(error) => json!({"schema":"world-decal-submission-inputs-v1",
            "complete":false,"gpu_readback":false,"capability_admitted":false,
            "reason":error.to_string()}),
    }
}

fn observe_inner(
    gal: &VulkanicGal,
    frontend: &WorldPrimitiveFrontend,
    frame: &WorldPrimitiveFrame,
    ops: &[CommandOp],
) -> GalResult<(Vec<Value>, Vec<Value>)> {
    let mut semantics = Vec::new();
    for (context, instances) in [
        ("world", &frame.mesh_instances),
        ("first-person", &frame.first_person_mesh_instances),
    ] {
        for (index, instance) in instances.iter().enumerate() {
            let Some(decal) = instance.decal_foil else {
                continue;
            };
            if semantics.len() == MAX_RECORDS {
                return Err(invalid("decal semantic capture limit exceeded"));
            }
            let foil = instance
                .item_foil
                .ok_or_else(|| invalid("decal capture missing foil semantics"))?;
            foil.validate()?;
            semantics.push(json!({"context":context,"instance_index":index,
                "mesh_key":instance.mesh_key,"mesh_generation":instance.mesh_generation,
                "first_person":decal.first_person,"trusted_normals":decal.trusted_normals,
                "model_pose":decal.model_pose,"normal_pose":decal.normal_pose,
                "clock_millis":foil.clock_millis,"speed":foil.speed,"strength":foil.strength,
                "scaled_ticks":(foil.clock_millis as f64 * foil.speed * 8.0) as i64}));
        }
    }
    let mut draws = Vec::new();
    let mut pipeline = Handle::NULL;
    let mut binding = None;
    for (op_index, op) in ops.iter().enumerate() {
        match op {
            CommandOp::BeginPass { .. } | CommandOp::EndPass => {
                pipeline = Handle::NULL;
                binding = None;
            }
            CommandOp::BindGraphicsPipeline(handle) => {
                pipeline = *handle;
            }
            CommandOp::BindResourceSet {
                pipeline_layout,
                set_index: 0,
                set,
                dynamic_offsets,
            } => {
                binding = Some((*pipeline_layout, *set, dynamic_offsets));
            }
            CommandOp::DrawIndexed { indices, instances } => {
                let Some((program, _)) =
                    frontend
                        .mesh_pipeline_resources
                        .iter()
                        .find(|(key, value)| {
                            value.pipeline == pipeline
                                && key.shader_program_identity.as_str()
                                    == WORLD_DECAL_FOIL_PROGRAM_ID
                        })
                else {
                    continue;
                };
                if draws.len() == MAX_RECORDS || *instances as usize > MAX_RECORDS {
                    return Err(invalid("decal draw capture limit exceeded"));
                }
                let (layout, set, offsets) =
                    binding.ok_or_else(|| invalid("decal capture missing binding"))?;
                let (key, resource) = frontend
                    .mesh_resources
                    .iter()
                    .find(|(_, value)| value.resource_set == set)
                    .ok_or_else(|| invalid("decal capture unowned resource set"))?;
                if resource.pipeline != pipeline
                    || resource.pipeline_layout != layout
                    || offsets.len() != 3
                {
                    return Err(invalid("decal capture pipeline/binding mismatch"));
                }
                let asset = frontend
                    .mesh_assets
                    .get(&key.mesh_key)
                    .ok_or_else(|| invalid("decal capture missing source mesh"))?;
                let normals = asset
                    .decal_normals
                    .as_ref()
                    .ok_or_else(|| invalid("decal capture missing source normals"))?;
                if asset.mesh_generation != key.mesh_generation
                    || resource.geometry_key != key.geometry_key()
                    || offsets[0] != resource.vertex_offset
                    || normals.len() != key.decal_vertex_count
                    || asset.vertex_bytes.len() != normals.len() * WORLD_MESH_GPU_VERTEX_BYTES
                {
                    return Err(invalid(
                        "decal capture source geometry incarnation mismatch",
                    ));
                }
                let mut source_vertices = Vec::new();
                for (i, normal) in normals.iter().take(4).enumerate() {
                    let offset = i * WORLD_MESH_GPU_VERTEX_BYTES;
                    source_vertices.push(
                        json!({"position":floats(&asset.vertex_bytes[offset..offset+12])?,
                        "normal_packed":normal}),
                    );
                }
                let descriptor = gal.resource_set_descriptor_for_capture(set)?;
                let texture = frontend
                    .mesh_texture_resources
                    .get(&key.texture_id)
                    .ok_or_else(|| invalid("decal capture missing owned texture"))?;
                let source = frontend
                    .mesh_texture_assets
                    .get(&key.texture_id)
                    .ok_or_else(|| invalid("decal capture missing texture source"))?;
                let sampler = gal.sampler_descriptor_for_capture(texture.sampler)?;
                if !descriptor
                    .bindings
                    .iter()
                    .any(|b| b.binding == 2 && b.resource == texture.view)
                    || !descriptor
                        .bindings
                        .iter()
                        .any(|b| b.binding == 3 && b.resource == texture.sampler)
                {
                    return Err(invalid("decal capture texture/sampler binding mismatch"));
                }
                let foil_binding = descriptor
                    .bindings
                    .iter()
                    .find(|b| b.binding == 4)
                    .ok_or_else(|| invalid("decal capture missing foil binding"))?;
                let instance_binding = descriptor
                    .bindings
                    .iter()
                    .find(|b| b.binding == 1)
                    .ok_or_else(|| invalid("decal capture missing instance binding"))?;
                if foil_binding.dynamic_offsets != [0] || instance_binding.dynamic_offsets != [0] {
                    return Err(invalid("decal capture unexpected static binding offsets"));
                }
                let count = *instances as usize;
                let size = decal_foil_payload_bytes(count, key.decal_vertex_count)?;
                if foil_binding
                    .buffer_range
                    .is_none_or(|range| range < size as u64)
                {
                    return Err(invalid("decal capture descriptor range too small"));
                }
                let payload =
                    uploaded_range(&ops[..op_index], foil_binding.resource, offsets[2], size)?;
                let transforms = uploaded_range(
                    &ops[..op_index],
                    instance_binding.resource,
                    offsets[1],
                    WORLD_MESH_BATCH_HEADER_BYTES + count * WORLD_MESH_INSTANCE_BYTES,
                )?;
                let mut uploaded = Vec::new();
                for i in 0..count {
                    let uv_offset =
                        u32::from_le_bytes(payload[i * 48 + 36..i * 48 + 40].try_into().unwrap())
                            as usize
                            * 4;
                    let expected = count * 48 + i * key.decal_vertex_count * 8;
                    if uv_offset != expected {
                        return Err(invalid("decal capture UV address mismatch"));
                    }
                    let uv_bytes = &payload[uv_offset..uv_offset + key.decal_vertex_count * 8];
                    let model = WORLD_MESH_BATCH_HEADER_BYTES + i * WORLD_MESH_INSTANCE_BYTES;
                    uploaded.push(json!({"model_pose":floats(&transforms[model..model+64])?,
                        "foil_rows":floats(&payload[i*48..i*48+32])?,
                        "strength":floats(&payload[i*48+32..i*48+36])?[0],
                        "uv_word_offset":uv_offset/4,
                        "projected_uv_xxh32":format!("{:08x}",xxh32(uv_bytes,0)),
                        "first_projected_uvs":floats(&uv_bytes[..uv_bytes.len().min(32)])?}));
                }
                draws.push(json!({"command_index":op_index,
                    "program":program.shader_program_identity.as_str(),
                    "mesh_key":key.mesh_key,"mesh_generation":key.mesh_generation,
                    "texture_id":key.texture_id,"material_mode":key.material_mode,
                    "texture_width":source.width,"texture_height":source.height,
                    "texture_rgba_xxh32":format!("{:08x}",xxh32(&source.rgba,0)),
                    "sampler":{"min_filter":format!("{:?}",sampler.min_filter),
                        "mag_filter":format!("{:?}",sampler.mag_filter),
                        "mip_filter":format!("{:?}",sampler.mip_filter),
                        "address_u":format!("{:?}",sampler.address_u),
                        "address_v":format!("{:?}",sampler.address_v),
                        "comparison":sampler.comparison.map(|v| format!("{v:?}"))},
                    "section_index":key.section_index,"vertex_count":key.decal_vertex_count,
                    "first_source_vertices":source_vertices,
                    "index_count":indices,"instance_count":instances,"payload_bytes":size,
                    "payload_xxh32":format!("{:08x}",xxh32(payload,0)),"uploaded_instances":uploaded}));
            }
            _ => {}
        }
    }
    Ok((semantics, draws))
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn decal_capture_requires_the_actual_preceding_upload() {
        let buffer = Handle::NULL;
        let mut ops = vec![CommandOp::HostWriteBuffer {
            buffer,
            offset: 0,
            data: vec![1; 16],
        }];
        assert_eq!(uploaded_range(&ops, buffer, 4, 8).unwrap(), &[1; 8]);
        ops.push(CommandOp::HostWriteBuffer {
            buffer,
            offset: 20,
            data: vec![2; 4],
        });
        assert_eq!(uploaded_range(&ops, buffer, 4, 8).unwrap(), &[1; 8]);
        ops.push(CommandOp::HostWriteBuffer {
            buffer,
            offset: 6,
            data: vec![3; 4],
        });
        assert!(uploaded_range(&ops, buffer, 4, 8).is_err());
        assert!(uploaded_range(&ops, buffer, 30, 4).is_err());
        assert!(uploaded_range(&ops, buffer, u64::MAX, 4).is_err());
        assert!(floats(&f32::NAN.to_le_bytes()).is_err());
    }
}

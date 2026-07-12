use super::*;

pub(crate) fn updated_quads_create_from_handles(
    quads: Vec<u64>,
    mesh_quad_count: i32,
    index_quad_count: i32,
) -> u64 {
    Box::into_raw(Box::new(NativeUpdatedQuads {
        quads,
        mesh_quad_count,
        index_quad_count,
    })) as u64
}

pub(super) unsafe fn section_builder_encode_scattered_unassigned(
    builder: &NativeSectionMeshBuilder,
    output_vertex_offsets: *const i32,
    update_count: i32,
    output_address: u64,
    output_capacity: i32,
    format: NativeFormat,
) -> i32 {
    if update_count < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if update_count == 0 {
        return OK;
    }
    if output_vertex_offsets.is_null() || output_address == 0 {
        return ERR_NULL_POINTER;
    }

    let input_address = builder.buffers[MODEL_QUAD_FACING_UNASSIGNED].quads.as_ptr() as u64;
    encode_scattered(
        input_address,
        output_vertex_offsets,
        update_count,
        output_address,
        output_capacity,
        format,
    )
}

pub(super) unsafe fn updated_quads_apply(
    updated_quads: &NativeUpdatedQuads,
    output_address: u64,
    output_capacity: i32,
    format: NativeFormat,
    material_bits: i32,
) -> i32 {
    if output_capacity < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if updated_quads.quads.is_empty() {
        return OK;
    }
    if output_address == 0 {
        return ERR_NULL_POINTER;
    }

    let mut update_builder = create_section_mesh_builder(updated_quads.quads.len());
    let mut output_vertex_offsets = Vec::with_capacity(updated_quads.quads.len());

    for &quad_handle in &updated_quads.quads {
        let mut write_to_index = -1;
        let status = translucent::native_full_quad_write_to_index(quad_handle, &mut write_to_index);
        if status != OK {
            return status;
        }
        if write_to_index < 0 {
            continue;
        }

        let quad_address =
            match section_builder_prepare_quad(&mut update_builder, MODEL_QUAD_FACING_UNASSIGNED) {
                Ok(value) => value,
                Err(status) => return status,
            };
        let status = translucent::native_full_quad_write_to_native_buffer(
            quad_handle,
            quad_address,
            material_bits,
        );
        if status != OK {
            return status;
        }
        update_builder.counts[MODEL_QUAD_FACING_UNASSIGNED] += 1;

        let Some(vertex_offset) = write_to_index.checked_mul(4) else {
            return ERR_INVALID_ARGUMENT;
        };
        output_vertex_offsets.push(vertex_offset);
    }

    section_builder_encode_scattered_unassigned(
        &update_builder,
        output_vertex_offsets.as_ptr(),
        output_vertex_offsets.len() as i32,
        output_address,
        output_capacity,
        format,
    )
}

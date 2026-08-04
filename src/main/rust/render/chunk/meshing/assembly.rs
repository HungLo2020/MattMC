use super::*;

pub(super) unsafe fn assemble(
    input_addresses: *const u64,
    input_vertex_counts: *const i32,
    input_count: i32,
    output_address: u64,
    output_capacity: i32,
    vertex_segments: *mut i32,
    vertex_segments_len: i32,
    format: NativeFormat,
    visible_slices: i32,
    force_unassigned: i32,
    slice_reordering: i32,
) -> i32 {
    if input_addresses.is_null() || input_vertex_counts.is_null() || vertex_segments.is_null() {
        return ERR_NULL_POINTER;
    }
    if input_count != MODEL_QUAD_FACING_COUNT as i32
        || vertex_segments_len != (MODEL_QUAD_FACING_COUNT * 2) as i32
        || output_capacity < 0
    {
        return ERR_INVALID_ARGUMENT;
    }

    let input_addresses = slice::from_raw_parts(input_addresses, MODEL_QUAD_FACING_COUNT);
    let input_vertex_counts = slice::from_raw_parts(input_vertex_counts, MODEL_QUAD_FACING_COUNT);
    let vertex_segments = slice::from_raw_parts_mut(vertex_segments, MODEL_QUAD_FACING_COUNT * 2);
    vertex_segments.fill(0);

    let total_vertices = match input_vertex_counts.iter().try_fold(0usize, |acc, count| {
        let count = usize::try_from(*count).ok()?;
        if count % 4 != 0 {
            return None;
        }
        acc.checked_add(count)
    }) {
        Some(value) => value,
        None => return ERR_INVALID_ARGUMENT,
    };

    if total_vertices == 0 {
        return OK;
    }
    if output_address == 0 {
        return ERR_NULL_POINTER;
    }

    let output_capacity = match usize::try_from(output_capacity) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };
    let output_len = match total_vertices.checked_mul(format.vertex_stride) {
        Some(value) => value,
        None => return ERR_INVALID_ARGUMENT,
    };
    if output_capacity < output_len {
        return ERR_CAPACITY;
    }

    let output = slice::from_raw_parts_mut(output_address as *mut u8, output_len);
    let mut output_vertex_offset = 0usize;

    if slice_reordering != 0 {
        let mut segment_index = 0usize;
        if let Err(status) = append_segment(
            MODEL_QUAD_FACING_UNASSIGNED,
            input_addresses,
            input_vertex_counts,
            output,
            &mut output_vertex_offset,
            vertex_segments,
            &mut segment_index,
            format,
        ) {
            return status;
        }

        for step in 0..2 {
            for facing in 0..MODEL_QUAD_FACING_COUNT {
                if facing == MODEL_QUAD_FACING_UNASSIGNED
                    || ((visible_slices >> facing) & 1) == step
                {
                    continue;
                }
                if let Err(status) = append_segment(
                    facing,
                    input_addresses,
                    input_vertex_counts,
                    output,
                    &mut output_vertex_offset,
                    vertex_segments,
                    &mut segment_index,
                    format,
                ) {
                    return status;
                }
            }
        }
    } else {
        if force_unassigned != 0 {
            let segment_index = MODEL_QUAD_FACING_UNASSIGNED << 1;
            vertex_segments[segment_index] = total_vertices as i32;
            vertex_segments[segment_index + 1] = MODEL_QUAD_FACING_UNASSIGNED as i32;
        }

        for facing in 0..MODEL_QUAD_FACING_COUNT {
            let vertex_count = match usize::try_from(input_vertex_counts[facing]) {
                Ok(value) => value,
                Err(_) => return ERR_INVALID_ARGUMENT,
            };
            if vertex_count == 0 {
                continue;
            }

            if force_unassigned == 0 {
                let segment_index = facing << 1;
                vertex_segments[segment_index] = vertex_count as i32;
                vertex_segments[segment_index + 1] = facing as i32;
            }

            if let Err(status) = encode_segment(
                input_addresses[facing],
                vertex_count,
                output,
                &mut output_vertex_offset,
                format,
            ) {
                return status;
            }
        }
    }

    OK
}

pub(super) unsafe fn assemble_output(
    input_addresses: *const u64,
    input_vertex_counts: *const i32,
    input_count: i32,
    output_address: u64,
    output_capacity: i32,
    vertex_segments: *mut i32,
    vertex_segments_len: i32,
    format: NativeFormat,
    visible_slices: i32,
    force_unassigned: i32,
    slice_reordering: i32,
    index_output_address: u64,
    index_output_capacity: i32,
    index_mode: i32,
    index_stride: i32,
    index_values: *const i32,
    index_value_count: i32,
) -> i32 {
    let status = assemble(
        input_addresses,
        input_vertex_counts,
        input_count,
        output_address,
        output_capacity,
        vertex_segments,
        vertex_segments_len,
        format,
        visible_slices,
        force_unassigned,
        slice_reordering,
    );
    if status != OK || index_mode == INDEX_MODE_NONE {
        return status;
    }
    if index_output_capacity < 0 || index_output_address == 0 {
        return if index_output_capacity < 0 {
            ERR_INVALID_ARGUMENT
        } else {
            ERR_NULL_POINTER
        };
    }

    match index_mode {
        INDEX_MODE_SHARED => {
            let total_vertices = match total_vertex_count(input_vertex_counts, input_count) {
                Ok(value) => value,
                Err(status) => return status,
            };
            if total_vertices % 4 != 0 {
                return ERR_INVALID_ARGUMENT;
            }

            let output = slice::from_raw_parts_mut(
                index_output_address as *mut u8,
                index_output_capacity as usize,
            );
            index::write_shared_quad_index_buffer(output, index_stride, (total_vertices / 4) as i32)
        }
        INDEX_MODE_SORTED_QUADS | INDEX_MODE_KEY_SORTED => {
            if index_value_count < 0 {
                return ERR_INVALID_ARGUMENT;
            }
            if index_value_count == 0 {
                return OK;
            }
            if index_values.is_null() {
                return ERR_NULL_POINTER;
            }

            let index_capacity = (index_output_capacity as usize) / std::mem::size_of::<i32>();
            let output =
                slice::from_raw_parts_mut(index_output_address as *mut i32, index_capacity);
            let values = slice::from_raw_parts(index_values, index_value_count as usize);

            if index_mode == INDEX_MODE_SORTED_QUADS {
                index::write_sorted_quad_index_buffer(output, values)
            } else {
                index::write_key_sorted_quad_index_buffer(output, values)
            }
        }
        _ => ERR_INVALID_ARGUMENT,
    }
}

pub(super) unsafe fn assemble_section_builder(
    builder: &mut NativeSectionMeshBuilder,
    output_address: u64,
    output_capacity: i32,
    vertex_segments: *mut i32,
    vertex_segments_len: i32,
    format: NativeFormat,
    visible_slices: i32,
    force_unassigned: i32,
    slice_reordering: i32,
) -> i32 {
    let assembly_started = Instant::now();
    if vertex_segments.is_null() || output_capacity < 0 {
        return if vertex_segments.is_null() {
            ERR_NULL_POINTER
        } else {
            ERR_INVALID_ARGUMENT
        };
    }
    if vertex_segments_len != (MODEL_QUAD_FACING_COUNT * 2) as i32 {
        return ERR_INVALID_ARGUMENT;
    }

    let vertex_segments = slice::from_raw_parts_mut(vertex_segments, MODEL_QUAD_FACING_COUNT * 2);
    vertex_segments.fill(0);

    let total_vertices = match builder
        .counts
        .iter()
        .try_fold(0usize, |acc, count| acc.checked_add(count.checked_mul(4)?))
    {
        Some(value) => value,
        None => return ERR_INVALID_ARGUMENT,
    };
    if total_vertices == 0 {
        return OK;
    }
    if output_address == 0 {
        return ERR_NULL_POINTER;
    }

    let output_capacity = match usize::try_from(output_capacity) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };
    let output_len = match total_vertices.checked_mul(format.vertex_stride) {
        Some(value) => value,
        None => return ERR_INVALID_ARGUMENT,
    };
    if output_capacity < output_len {
        return ERR_CAPACITY;
    }

    let output = slice::from_raw_parts_mut(output_address as *mut u8, output_len);
    let mut output_vertex_offset = 0usize;

    if slice_reordering != 0 {
        let mut segment_index = 0usize;
        if let Err(status) = append_builder_segment(
            builder,
            MODEL_QUAD_FACING_UNASSIGNED,
            output,
            &mut output_vertex_offset,
            vertex_segments,
            &mut segment_index,
            format,
        ) {
            return status;
        }

        for step in 0..2 {
            for facing in 0..MODEL_QUAD_FACING_COUNT {
                if facing == MODEL_QUAD_FACING_UNASSIGNED
                    || ((visible_slices >> facing) & 1) == step
                {
                    continue;
                }
                if let Err(status) = append_builder_segment(
                    builder,
                    facing,
                    output,
                    &mut output_vertex_offset,
                    vertex_segments,
                    &mut segment_index,
                    format,
                ) {
                    return status;
                }
            }
        }
    } else {
        if force_unassigned != 0 {
            let segment_index = MODEL_QUAD_FACING_UNASSIGNED << 1;
            vertex_segments[segment_index] = total_vertices as i32;
            vertex_segments[segment_index + 1] = MODEL_QUAD_FACING_UNASSIGNED as i32;
        }

        for facing in 0..MODEL_QUAD_FACING_COUNT {
            let vertex_count = builder.counts[facing] * 4;
            if vertex_count == 0 {
                continue;
            }

            if force_unassigned == 0 {
                let segment_index = facing << 1;
                vertex_segments[segment_index] = vertex_count as i32;
                vertex_segments[segment_index + 1] = facing as i32;
            }

            if let Err(status) = encode_builder_segment(
                &builder.buffers[facing],
                vertex_count,
                output,
                &mut output_vertex_offset,
                format,
            ) {
                return status;
            }
        }
    }

    builder
        .profile
        .add_stage(PROFILE_FINAL_ASSEMBLY, assembly_started);
    OK
}

pub(super) unsafe fn copy_section_builder_primitive_metadata(
    builder: &NativeSectionMeshBuilder,
    output_address: u64,
    output_capacity_records: i32,
    visible_slices: i32,
    force_unassigned: i32,
    slice_reordering: i32,
) -> i32 {
    if output_capacity_records < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    let total_quads = match builder
        .counts
        .iter()
        .try_fold(0usize, |acc, count| acc.checked_add(*count))
    {
        Some(value) => value,
        None => return ERR_INVALID_ARGUMENT,
    };
    if total_quads == 0 {
        return OK;
    }
    if output_address == 0 {
        return ERR_NULL_POINTER;
    }
    let output_capacity = match usize::try_from(output_capacity_records) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };
    if output_capacity < total_quads {
        return ERR_CAPACITY;
    }
    let output =
        slice::from_raw_parts_mut(output_address as *mut NativeTerrainPrimitiveMetadata, total_quads);
    let mut cursor = 0usize;
    if slice_reordering != 0 {
        if let Err(status) = copy_metadata_segment(
            builder,
            MODEL_QUAD_FACING_UNASSIGNED,
            output,
            &mut cursor,
        ) {
            return status;
        }
        for step in 0..2 {
            for facing in 0..MODEL_QUAD_FACING_COUNT {
                if facing == MODEL_QUAD_FACING_UNASSIGNED
                    || ((visible_slices >> facing) & 1) == step
                {
                    continue;
                }
                if let Err(status) = copy_metadata_segment(builder, facing, output, &mut cursor) {
                    return status;
                }
            }
        }
    } else if force_unassigned != 0 {
        for facing in 0..MODEL_QUAD_FACING_COUNT {
            if let Err(status) = copy_metadata_segment(builder, facing, output, &mut cursor) {
                return status;
            }
        }
    } else {
        for facing in 0..MODEL_QUAD_FACING_COUNT {
            if let Err(status) = copy_metadata_segment(builder, facing, output, &mut cursor) {
                return status;
            }
        }
    }
    if cursor != total_quads {
        return ERR_INVALID_ARGUMENT;
    }
    OK
}

fn copy_metadata_segment(
    builder: &NativeSectionMeshBuilder,
    facing: usize,
    output: &mut [NativeTerrainPrimitiveMetadata],
    cursor: &mut usize,
) -> Result<(), i32> {
    let count = builder.counts[facing];
    if count == 0 {
        return Ok(());
    }
    let metadata = builder.buffers[facing]
        .primitive_metadata
        .get(..count)
        .ok_or(ERR_INVALID_ARGUMENT)?;
    let end = cursor.checked_add(count).ok_or(ERR_INVALID_ARGUMENT)?;
    output
        .get_mut(*cursor..end)
        .ok_or(ERR_CAPACITY)?
        .copy_from_slice(metadata);
    *cursor = end;
    Ok(())
}

pub(super) fn append_builder_segment(
    builder: &NativeSectionMeshBuilder,
    facing: usize,
    output: &mut [u8],
    output_vertex_offset: &mut usize,
    vertex_segments: &mut [i32],
    segment_index: &mut usize,
    format: NativeFormat,
) -> Result<(), i32> {
    let vertex_count = builder.counts[facing]
        .checked_mul(4)
        .ok_or(ERR_INVALID_ARGUMENT)?;
    vertex_segments[*segment_index] = vertex_count as i32;
    vertex_segments[*segment_index + 1] = facing as i32;
    *segment_index += 2;

    if vertex_count != 0 {
        encode_builder_segment(
            &builder.buffers[facing],
            vertex_count,
            output,
            output_vertex_offset,
            format,
        )?;
    }

    Ok(())
}

pub(super) fn encode_builder_segment(
    buffer: &NativeQuadBuffer,
    vertex_count: usize,
    output: &mut [u8],
    output_vertex_offset: &mut usize,
    format: NativeFormat,
) -> Result<(), i32> {
    if vertex_count % 4 != 0 {
        return Err(ERR_INVALID_ARGUMENT);
    }

    let byte_offset = output_vertex_offset
        .checked_mul(format.vertex_stride)
        .ok_or(ERR_INVALID_ARGUMENT)?;
    let byte_len = vertex_count
        .checked_mul(format.vertex_stride)
        .ok_or(ERR_INVALID_ARGUMENT)?;
    let output_slice = output
        .get_mut(byte_offset..byte_offset + byte_len)
        .ok_or(ERR_CAPACITY)?;

    if buffer.encoded_format == Some(format) && buffer.encoded.len() >= byte_len {
        output_slice.copy_from_slice(&buffer.encoded[..byte_len]);
    } else {
        if buffer.quads.len() < vertex_count / 4 {
            return Err(ERR_INVALID_ARGUMENT);
        }
        let input_address = buffer.quads.as_ptr() as u64;
        encode_segment(
            input_address,
            vertex_count,
            output,
            output_vertex_offset,
            format,
        )?;
        return Ok(());
    }

    *output_vertex_offset += vertex_count;
    Ok(())
}

pub(super) unsafe fn total_vertex_count(
    input_vertex_counts: *const i32,
    input_count: i32,
) -> Result<usize, i32> {
    if input_vertex_counts.is_null() {
        return Err(ERR_NULL_POINTER);
    }
    if input_count != MODEL_QUAD_FACING_COUNT as i32 {
        return Err(ERR_INVALID_ARGUMENT);
    }

    let input_vertex_counts = slice::from_raw_parts(input_vertex_counts, MODEL_QUAD_FACING_COUNT);
    input_vertex_counts
        .iter()
        .try_fold(0usize, |acc, count| {
            let count = usize::try_from(*count).ok()?;
            if count % 4 != 0 {
                return None;
            }
            acc.checked_add(count)
        })
        .ok_or(ERR_INVALID_ARGUMENT)
}

pub(super) fn append_segment(
    facing: usize,
    input_addresses: &[u64],
    input_vertex_counts: &[i32],
    output: &mut [u8],
    output_vertex_offset: &mut usize,
    vertex_segments: &mut [i32],
    segment_index: &mut usize,
    format: NativeFormat,
) -> Result<(), i32> {
    let vertex_count =
        usize::try_from(input_vertex_counts[facing]).map_err(|_| ERR_INVALID_ARGUMENT)?;
    vertex_segments[*segment_index] = vertex_count as i32;
    vertex_segments[*segment_index + 1] = facing as i32;
    *segment_index += 2;

    if vertex_count != 0 {
        encode_segment(
            input_addresses[facing],
            vertex_count,
            output,
            output_vertex_offset,
            format,
        )?;
    }

    Ok(())
}

pub(super) fn encode_segment(
    input_address: u64,
    vertex_count: usize,
    output: &mut [u8],
    output_vertex_offset: &mut usize,
    format: NativeFormat,
) -> Result<(), i32> {
    if input_address == 0 {
        return Err(ERR_NULL_POINTER);
    }
    if vertex_count % 4 != 0 {
        return Err(ERR_INVALID_ARGUMENT);
    }
    let quad_count = vertex_count / 4;

    let byte_offset = output_vertex_offset
        .checked_mul(format.vertex_stride)
        .ok_or(ERR_INVALID_ARGUMENT)?;
    let byte_len = vertex_count
        .checked_mul(format.vertex_stride)
        .ok_or(ERR_INVALID_ARGUMENT)?;
    let output_slice = output
        .get_mut(byte_offset..byte_offset + byte_len)
        .ok_or(ERR_CAPACITY)?;
    let input = unsafe { slice::from_raw_parts(input_address as *const NativeQuad, quad_count) };

    for (quad_index, quad) in input.iter().enumerate() {
        let start = quad_index * 4 * format.vertex_stride;
        let end = start + 4 * format.vertex_stride;
        encode_quad(quad, &mut output_slice[start..end], format);
    }

    *output_vertex_offset += vertex_count;
    Ok(())
}

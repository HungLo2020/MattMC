//! Rust-owned fluid face sinks and compact encoding paths.
//!
//! Unsafe entry points in this module accept Java-provided record pointers only
//! after the FFI layer has validated ABI counts and strides. Emitted quads either
//! go directly into compact builder storage or through the retained compatibility
//! batch path when raw quads or analyzer fallback are required.

use super::*;

#[derive(Clone, Copy)]
pub(in crate::render::chunk::meshing) struct NativeFluidFace {
    pub vertices: [QuadVertex; 4],
    pub block_emission: u8,
    pub render_type: u8,
    pub ignore_mid_block: u8,
    pub block_id: i32,
    pub local_x: i32,
    pub local_y: i32,
    pub local_z: i32,
    pub material_bits: i32,
    pub packed_normal: i32,
    pub facing: usize,
    pub fluid_type: i32,
    pub face_kind: i32,
}

impl NativeFluidFace {
    #[inline(always)]
    fn to_native_quad(self) -> NativeQuad {
        NativeQuad {
            vertices: self.vertices,
            block_emission: self.block_emission,
            render_type: self.render_type,
            ignore_mid_block: self.ignore_mid_block,
            _padding: 0,
            block_id: self.block_id,
            local_x: self.local_x,
            local_y: self.local_y,
            local_z: self.local_z,
            material_bits: self.material_bits,
        }
    }
}

pub(in crate::render::chunk::meshing) trait NativeFluidFaceSink {
    fn profile(&mut self) -> &mut NativeMeshingProfile;

    fn mark_fluid_sprite(&mut self, mask: i32);

    fn emit(&mut self, face: NativeFluidFace) -> Result<(), i32>;
}

pub(in crate::render::chunk::meshing) struct BuilderFluidFaceSink<'a, 'b> {
    pub(in crate::render::chunk::meshing) builder: &'a mut NativeSectionMeshBuilder,
    pub(in crate::render::chunk::meshing) pending_counts: &'b mut [usize; MODEL_QUAD_FACING_COUNT],
    pub(in crate::render::chunk::meshing) analyzer: Option<u64>,
    pub(in crate::render::chunk::meshing) format: NativeFormat,
    pub(in crate::render::chunk::meshing) store_raw_quads: bool,
    pub(in crate::render::chunk::meshing) profile_scan_substages: bool,
    pub(in crate::render::chunk::meshing) profile_staging_substages: bool,
    pub(in crate::render::chunk::meshing) total_committed: &'b mut i32,
}

impl NativeFluidFaceSink for BuilderFluidFaceSink<'_, '_> {
    #[inline(always)]
    fn profile(&mut self) -> &mut NativeMeshingProfile {
        &mut self.builder.profile
    }

    #[inline(always)]
    fn mark_fluid_sprite(&mut self, mask: i32) {
        self.builder.fluid_sprite_mask |= mask;
    }

    #[inline(always)]
    fn emit(&mut self, face: NativeFluidFace) -> Result<(), i32> {
        let append_started = profile_start(self.profile_scan_substages);
        unsafe {
            if is_compact_fast_format(self.format) && !self.store_raw_quads {
                append_direct_compact_fluid_face(
                    self.builder,
                    face,
                    self.analyzer,
                    self.format,
                    self.profile_staging_substages,
                    self.total_committed,
                )?;
            } else {
                push_native_section_quad(
                    self.builder,
                    face.to_native_quad(),
                    face.packed_normal,
                    if face.fluid_type == FLUID_WATER {
                        TERRAIN_PRIMITIVE_BUILTIN_WATER
                    } else {
                        TERRAIN_PRIMITIVE_UNSUPPORTED_FLUID
                    },
                    face.facing,
                    self.pending_counts,
                    self.analyzer,
                    self.format,
                    self.store_raw_quads,
                    self.profile_staging_substages,
                    self.total_committed,
                )?;
            }
        }
        self.builder
            .profile
            .add_optional_stage(PROFILE_SCAN_QUAD_APPEND, append_started);
        Ok(())
    }
}

pub(in crate::render::chunk::meshing) unsafe fn append_direct_compact_fluid_face(
    builder: &mut NativeSectionMeshBuilder,
    face: NativeFluidFace,
    analyzer: Option<u64>,
    format: NativeFormat,
    profile_staging_substages: bool,
    total_committed: &mut i32,
) -> Result<(), i32> {
    if face.facing >= MODEL_QUAD_FACING_COUNT {
        return Err(ERR_INVALID_ARGUMENT);
    }

    if let Some(analyzer_handle) = analyzer {
        let analyzer_started = profile_start(profile_staging_substages);
        let accepted = translucent::append_quad_positions_to_analyzer(
            analyzer_handle,
            fluid_face_positions(&face),
            face.facing as i32,
            face.packed_normal,
        )?;
        builder
            .profile
            .add_optional_stage(PROFILE_TRANSLUCENT_INGEST, analyzer_started);
        builder
            .profile
            .add_count(PROFILE_COUNT_TRANSLUCENT_ANALYZER_ENTRIES, 1);
        builder
            .profile
            .add_count(PROFILE_COUNT_TRANSLUCENT_VALIDITY_BYTES, 1);
        if !accepted {
            return Ok(());
        }
        builder
            .profile
            .add_count(PROFILE_COUNT_TRANSLUCENT_QUADS, 1);
    }

    let pack_started = profile_start(profile_staging_substages);
    let start = builder.counts[face.facing];
    let required_len = start.checked_add(1).ok_or(ERR_CAPACITY)?;
    let buffer = &mut builder.buffers[face.facing];
    let encoded_quad_len = 4usize
        .checked_mul(format.vertex_stride)
        .ok_or(ERR_INVALID_ARGUMENT)?;

    if !buffer.encoded.is_empty() && buffer.encoded_format != Some(format) {
        buffer.encoded.clear();
        buffer.encoded_format = None;
    }
    if buffer.encoded_format.is_none() {
        buffer.encoded_format = Some(format);
    }

    let required_encoded_len = required_len
        .checked_mul(encoded_quad_len)
        .ok_or(ERR_INVALID_ARGUMENT)?;
    ensure_encoded_len(&mut buffer.encoded, required_encoded_len, format);
    ensure_metadata_len(&mut buffer.primitive_metadata, required_len);
    let primitive_kind = if face.fluid_type == FLUID_WATER {
        TERRAIN_PRIMITIVE_BUILTIN_WATER
    } else {
        TERRAIN_PRIMITIVE_UNSUPPORTED_FLUID
    };
    buffer.primitive_metadata[start] = primitive_metadata_from_quad(
        &face.to_native_quad(),
        primitive_kind,
        face.facing,
        face.face_kind,
    );

    let encoded_start = start * encoded_quad_len;
    let encoded_end = encoded_start + encoded_quad_len;
    encode_compact_quad_vertices(
        &face.vertices,
        face.material_bits,
        format.section_index,
        &mut buffer.encoded[encoded_start..encoded_end],
    );
    builder.counts[face.facing] = required_len;
    *total_committed = total_committed.checked_add(1).ok_or(ERR_CAPACITY)?;
    builder
        .profile
        .add_optional_stage(PROFILE_VERTEX_PACKING, pack_started);
    builder.profile.add_count(PROFILE_COUNT_EMITTED_QUADS, 1);
    Ok(())
}

pub(in crate::render::chunk::meshing) fn fluid_face_positions(face: &NativeFluidFace) -> [f32; 12] {
    let vertices = &face.vertices;
    [
        vertices[0].x,
        vertices[0].y,
        vertices[0].z,
        vertices[1].x,
        vertices[1].y,
        vertices[1].z,
        vertices[2].x,
        vertices[2].y,
        vertices[2].z,
        vertices[3].x,
        vertices[3].y,
        vertices[3].z,
    ]
}

pub(in crate::render::chunk::meshing) unsafe fn section_builder_append_fluid_face_records_encoded(
    builder: &mut NativeSectionMeshBuilder,
    facing: usize,
    record_address: u64,
    record_count: usize,
    record_stride: usize,
    analyzer: Option<(u64, i32)>,
    format: NativeFormat,
    store_raw_quads: bool,
) -> Result<(i32, i32), i32> {
    if facing >= MODEL_QUAD_FACING_COUNT {
        return Err(ERR_INVALID_ARGUMENT);
    }
    if record_count == 0 {
        return Ok((0, 0));
    }
    if record_address == 0 {
        return Err(ERR_NULL_POINTER);
    }
    if record_stride != std::mem::size_of::<FluidFaceRecord>() {
        return Err(ERR_INVALID_ARGUMENT);
    }

    let records = slice::from_raw_parts(record_address as *const FluidFaceRecord, record_count);
    let mut processed = 0usize;
    let mut total_valid = 0i32;
    let mut total_committed = 0i32;

    while processed < record_count {
        let chunk_count = (record_count - processed).min(PENDING_BATCH_QUAD_CAPACITY);
        {
            let geometry_started = Instant::now();
            let pending = &mut builder.pending[facing];
            for index in 0..chunk_count {
                let record = records[processed + index];
                pending.quads[index] = fluid_face_record_to_quad(record)?;
                pending.packed_normals[index] = record.packed_normal;
                pending.primitive_kinds[index] = match record.primitive_kind {
                    TERRAIN_PRIMITIVE_BUILTIN_WATER | TERRAIN_PRIMITIVE_UNSUPPORTED_FLUID => {
                        record.primitive_kind
                    }
                    _ => return Err(ERR_INVALID_ARGUMENT),
                };
            }
            builder
                .profile
                .add_stage(PROFILE_FLUID_GEOM_UV, geometry_started);
        }

        let validity_address = builder.pending[facing].validity.as_mut_ptr() as u64;
        let mut chunk_valid = chunk_count as i32;
        let validity = if let Some((analyzer_handle, translucent_facing)) = analyzer {
            let analyzer_started = Instant::now();
            let status = translucent::append_native_quad_batch_to_analyzer(
                analyzer_handle,
                builder.pending[facing].quads.as_ptr() as u64,
                chunk_count as i32,
                translucent_facing,
                builder.pending[facing].packed_normals.as_ptr(),
                validity_address,
                &mut chunk_valid,
            );
            if status != OK {
                return Err(status);
            }
            builder
                .profile
                .add_stage(PROFILE_TRANSLUCENT_INGEST, analyzer_started);
            builder
                .profile
                .add_count(PROFILE_COUNT_TRANSLUCENT_QUADS, chunk_valid.max(0) as usize);
            Some(slice::from_raw_parts(
                validity_address as *const u8,
                chunk_count,
            ))
        } else {
            None
        };

        let staging_started = Instant::now();
        let primitive_kinds = builder.pending[facing].primitive_kinds[..chunk_count].to_vec();
        let chunk_committed = section_builder_append_batch_encoded_with_kind(
            builder,
            facing,
            builder.pending[facing].quads.as_ptr() as u64,
            chunk_count,
            validity,
            format,
            store_raw_quads,
            None,
            Some(&primitive_kinds),
        )?;
        builder
            .profile
            .add_stage(PROFILE_QUAD_STAGING, staging_started);

        native_fluid_flush_diag(
            facing,
            analyzer.is_some(),
            chunk_count,
            chunk_valid,
            chunk_committed,
            &records[processed..processed + chunk_count],
        );

        total_valid = total_valid.checked_add(chunk_valid).ok_or(ERR_CAPACITY)?;
        total_committed = total_committed
            .checked_add(chunk_committed)
            .ok_or(ERR_CAPACITY)?;
        processed += chunk_count;
    }

    Ok((total_valid, total_committed))
}

use super::*;
use crate::render::chunk::meshing::section::CompactSectionSnapshot;
use std::mem;

struct CompactSnapshotStorage {
    active_indices: Vec<u16>,
    padded_state_ids: Vec<i32>,
    padded_light_words: Vec<i32>,
    block_ids: Vec<i32>,
    seed_los: Vec<i32>,
    seed_his: Vec<i32>,
    tints: Vec<i32>,
    fluid_tints: Vec<i32>,
    fluid_flow_x: Vec<f32>,
    fluid_flow_z: Vec<f32>,
    fluid_block_ids: Vec<i32>,
    flags: Vec<i32>,
}

impl CompactSnapshotStorage {
    fn new() -> Self {
        let padded_count = COMPACT_SECTION_PADDED_LENGTH
            * COMPACT_SECTION_PADDED_LENGTH
            * COMPACT_SECTION_PADDED_LENGTH;
        Self {
            active_indices: vec![0],
            padded_state_ids: vec![0; padded_count],
            padded_light_words: vec![LIGHT_FULL_BRIGHT; padded_count],
            block_ids: vec![0; COMPACT_SECTION_BLOCK_COUNT],
            seed_los: vec![0; COMPACT_SECTION_BLOCK_COUNT],
            seed_his: vec![0; COMPACT_SECTION_BLOCK_COUNT],
            tints: vec![-1; COMPACT_SECTION_BLOCK_COUNT],
            fluid_tints: vec![-1; COMPACT_SECTION_BLOCK_COUNT],
            fluid_flow_x: vec![0.0; COMPACT_SECTION_BLOCK_COUNT],
            fluid_flow_z: vec![0.0; COMPACT_SECTION_BLOCK_COUNT],
            fluid_block_ids: vec![0; COMPACT_SECTION_BLOCK_COUNT],
            flags: vec![0; COMPACT_SECTION_BLOCK_COUNT],
        }
    }

    fn header(&self, active_count: i32) -> CompactSectionSnapshotHeader {
        CompactSectionSnapshotHeader {
            version: COMPACT_SECTION_SNAPSHOT_VERSION,
            active_count,
            min_x: 0,
            min_y: 0,
            min_z: 0,
            _padding: 0,
            active_indices_address: self.active_indices.as_ptr() as u64,
            padded_state_ids_address: self.padded_state_ids.as_ptr() as u64,
            padded_light_words_address: self.padded_light_words.as_ptr() as u64,
            block_ids_address: self.block_ids.as_ptr() as u64,
            seed_los_address: self.seed_los.as_ptr() as u64,
            seed_his_address: self.seed_his.as_ptr() as u64,
            tints_address: self.tints.as_ptr() as u64,
            fluid_tints_address: self.fluid_tints.as_ptr() as u64,
            fluid_flow_x_address: self.fluid_flow_x.as_ptr() as u64,
            fluid_flow_z_address: self.fluid_flow_z.as_ptr() as u64,
            fluid_block_ids_address: self.fluid_block_ids.as_ptr() as u64,
            flags_address: self.flags.as_ptr() as u64,
        }
    }

    fn set_center_state(&mut self, state_id: i32) {
        self.active_indices[0] = 0;
        let center = CompactSectionSnapshot::padded_index(1, 1, 1);
        self.padded_state_ids[center] = state_id;
    }
}

struct BuilderHandles {
    solid: u64,
    cutout: u64,
    translucent: u64,
}

impl BuilderHandles {
    fn new() -> Self {
        unsafe {
            let mut solid = 0;
            let mut cutout = 0;
            let mut translucent = 0;
            assert_eq!(OK, mattmc_sodium_section_mesh_builder_create(4, &mut solid));
            assert_eq!(
                OK,
                mattmc_sodium_section_mesh_builder_create(4, &mut cutout)
            );
            assert_eq!(
                OK,
                mattmc_sodium_section_mesh_builder_create(4, &mut translucent)
            );
            assert_eq!(OK, mattmc_sodium_section_mesh_builder_start(solid));
            assert_eq!(OK, mattmc_sodium_section_mesh_builder_start(cutout));
            assert_eq!(OK, mattmc_sodium_section_mesh_builder_start(translucent));
            Self {
                solid,
                cutout,
                translucent,
            }
        }
    }
}

impl Drop for BuilderHandles {
    fn drop(&mut self) {
        unsafe {
            let _ = mattmc_sodium_section_mesh_builder_destroy(self.solid);
            let _ = mattmc_sodium_section_mesh_builder_destroy(self.cutout);
            let _ = mattmc_sodium_section_mesh_builder_destroy(self.translucent);
        }
    }
}

fn compact_format_args() -> (i32, i32, i32, i32, i32, i32, i32) {
    (
        mem::size_of::<NativeQuad>() as i32,
        COMPACT_VERTEX_STRIDE,
        COMPACT_NATIVE_BLOCK_ID_OFFSET,
        COMPACT_NATIVE_NORMAL_OFFSET,
        COMPACT_NATIVE_TANGENT_OFFSET,
        COMPACT_NATIVE_MID_UV_OFFSET,
        COMPACT_NATIVE_MID_BLOCK_OFFSET,
    )
}

unsafe fn append_compact(
    builders: &BuilderHandles,
    snapshot_address: u64,
    output: &mut [i32; 3],
) -> i32 {
    let (quad_stride, vertex_stride, block_id, normal, tangent, mid_uv, mid_block) =
        compact_format_args();
    mattmc_sodium_section_mesh_builders_append_compact_native_section_all_passes_encoded(
        builders.solid,
        builders.cutout,
        builders.translucent,
        snapshot_address,
        quad_stride,
        vertex_stride,
        block_id,
        normal,
        tangent,
        mid_uv,
        mid_block,
        0,
        0,
        0,
        output.as_mut_ptr(),
    )
}

#[test]
fn ffi_rejects_malformed_handles_lengths_and_formats() {
    unsafe {
        let mut handle = 0;
        assert_eq!(
            ERR_NULL_POINTER,
            mattmc_sodium_section_mesh_builder_create(1, std::ptr::null_mut())
        );
        assert_eq!(
            ERR_INVALID_ARGUMENT,
            mattmc_sodium_section_mesh_builder_create(-1, &mut handle)
        );

        let builders = BuilderHandles::new();
        let mut counts = [0; 3];
        assert_eq!(
            ERR_NULL_POINTER,
            mattmc_sodium_section_mesh_builders_append_compact_native_section_all_passes_encoded(
                0,
                builders.cutout,
                builders.translucent,
                1,
                mem::size_of::<NativeQuad>() as i32,
                COMPACT_VERTEX_STRIDE,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                counts.as_mut_ptr(),
            )
        );
        assert_eq!(
            ERR_NULL_POINTER,
            mattmc_sodium_section_mesh_builders_append_compact_native_section_all_passes_encoded(
                builders.solid,
                builders.cutout,
                builders.translucent,
                0,
                mem::size_of::<NativeQuad>() as i32,
                COMPACT_VERTEX_STRIDE,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                counts.as_mut_ptr(),
            )
        );
        assert_eq!(
            ERR_INVALID_ARGUMENT,
            mattmc_sodium_section_mesh_builders_append_compact_native_section_all_passes_encoded(
                builders.solid,
                builders.cutout,
                builders.translucent,
                1,
                mem::size_of::<NativeQuad>() as i32 - 1,
                COMPACT_VERTEX_STRIDE,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                counts.as_mut_ptr(),
            )
        );

        let mut committed = 0;
        assert_eq!(
            ERR_INVALID_ARGUMENT,
            mattmc_sodium_section_mesh_builder_append_batch_encoded(
                builders.solid,
                0,
                0,
                -1,
                mem::size_of::<NativeQuad>() as i32,
                COMPACT_VERTEX_STRIDE,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                &mut committed,
            )
        );
        assert_eq!(
            ERR_NULL_POINTER,
            mattmc_sodium_section_mesh_builder_copy_profile(
                builders.solid,
                std::ptr::null_mut(),
                0
            )
        );
        assert_eq!(
            ERR_NULL_POINTER,
            mattmc_sodium_chunk_mesh_encode(
                0,
                1,
                0,
                0,
                mem::size_of::<NativeQuad>() as i32,
                COMPACT_VERTEX_STRIDE,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
            )
        );
    }
}

#[test]
fn cache_registration_ffi_rejects_bad_ids_pointers_and_strides() {
    unsafe {
        assert_eq!(OK, mattmc_sodium_static_model_cache_clear());
        assert_eq!(
            ERR_INVALID_ARGUMENT,
            mattmc_sodium_static_model_cache_register(
                -1,
                0,
                0,
                mem::size_of::<StaticModelQuadRecord>() as i32,
            )
        );
        assert_eq!(
            ERR_NULL_POINTER,
            mattmc_sodium_static_model_cache_register(
                0,
                0,
                1,
                mem::size_of::<StaticModelQuadRecord>() as i32,
            )
        );
        assert_eq!(
            ERR_INVALID_ARGUMENT,
            mattmc_sodium_static_model_cache_register(0, 0, 0, 1)
        );
        assert_eq!(
            ERR_INVALID_ARGUMENT,
            mattmc_sodium_native_model_selector_register(
                0,
                SELECTOR_DIRECT,
                0,
                0,
                mem::size_of::<NativeModelSelectorEntry>() as i32 - 1,
            )
        );
        assert_eq!(
            ERR_INVALID_ARGUMENT,
            mattmc_sodium_native_meshing_state_register(
                -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0, 0, 0.0, 0.0, 0, 0.0, 1.0, 0.0, 1.0,
                0.0, 0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 1.0, 0.0, 1.0, 0.0, 0,
            )
        );
    }
}

#[test]
fn staging_and_assembly_ffi_reject_bad_handles_and_formats() {
    unsafe {
        let builders = BuilderHandles::new();
        let mut address = 0;
        let mut normals = 0;
        let mut validity = 0;
        let mut capacity = 0;
        assert_eq!(
            ERR_INVALID_ARGUMENT,
            mattmc_sodium_section_mesh_builder_staging_addresses(
                builders.solid,
                -1,
                &mut address,
                &mut normals,
                &mut validity,
                &mut capacity,
            )
        );
        assert_eq!(
            ERR_NULL_POINTER,
            mattmc_sodium_section_mesh_builder_staging_addresses(
                builders.solid,
                0,
                std::ptr::null_mut(),
                &mut normals,
                &mut validity,
                &mut capacity,
            )
        );
        assert_eq!(
            ERR_INVALID_ARGUMENT,
            mattmc_sodium_section_mesh_builder_assemble(
                builders.solid,
                0,
                0,
                std::ptr::null_mut(),
                0,
                mem::size_of::<NativeQuad>() as i32 - 1,
                COMPACT_VERTEX_STRIDE,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
            )
        );
    }
}

#[test]
fn translucent_append_ffi_rejects_bad_counts_and_analyzer_handles() {
    unsafe {
        let builders = BuilderHandles::new();
        let mut counts = [0; 2];
        assert_eq!(
            ERR_INVALID_ARGUMENT,
            mattmc_sodium_section_mesh_builder_append_translucent_batch(
                builders.translucent,
                -1,
                0,
                0,
                0,
                0,
                0,
                counts.as_mut_ptr(),
                counts.len() as i32,
            )
        );
        assert_eq!(
            ERR_INVALID_ARGUMENT,
            mattmc_sodium_section_mesh_builder_append_translucent_batch(
                builders.translucent,
                0,
                0,
                1,
                0,
                0,
                0,
                counts.as_mut_ptr(),
                1,
            )
        );
        assert_eq!(
            ERR_NULL_POINTER,
            mattmc_sodium_section_mesh_builder_append_translucent_batch(
                builders.translucent,
                0,
                0,
                1,
                0,
                0,
                0,
                counts.as_mut_ptr(),
                counts.len() as i32,
            )
        );
    }
}

#[test]
fn update_buffer_ffi_rejects_bad_handles_counts_and_output_pointers() {
    unsafe {
        let mut handle = 0;
        assert_eq!(
            ERR_NULL_POINTER,
            mattmc_sodium_updated_quads_create(std::ptr::null_mut())
        );
        assert_eq!(OK, mattmc_sodium_updated_quads_create(&mut handle));
        assert_eq!(ERR_NULL_POINTER, mattmc_sodium_updated_quads_add(handle, 0));
        assert_eq!(
            ERR_INVALID_ARGUMENT,
            mattmc_sodium_updated_quads_set_counts(handle, -1, 0)
        );
        assert_eq!(
            ERR_INVALID_ARGUMENT,
            mattmc_sodium_updated_quads_counts(handle, [0; 2].as_mut_ptr(), 1)
        );
        assert_eq!(
            ERR_INVALID_ARGUMENT,
            mattmc_sodium_chunk_quad_buffer_create(-1, &mut 0, &mut 0)
        );
        assert_eq!(OK, mattmc_sodium_updated_quads_destroy(handle));
    }
}

#[test]
fn compact_ffi_rejects_malformed_snapshot_header() {
    let storage = CompactSnapshotStorage::new();
    let builders = BuilderHandles::new();
    let mut counts = [0; 3];

    let mut header = storage.header(1);
    header.version = COMPACT_SECTION_SNAPSHOT_VERSION + 1;
    assert_eq!(ERR_INVALID_ARGUMENT, unsafe {
        append_compact(&builders, &header as *const _ as u64, &mut counts)
    });

    header = storage.header(1);
    header.active_indices_address = 0;
    assert_eq!(ERR_NULL_POINTER, unsafe {
        append_compact(&builders, &header as *const _ as u64, &mut counts)
    });
}

#[test]
fn cleared_native_cache_ids_do_not_resolve_after_reload() {
    unsafe {
        assert_eq!(OK, mattmc_sodium_static_model_cache_clear());

        let quad = StaticModelQuadRecord {
            vertices: [
                StaticModelVertexRecord {
                    x: 0.0,
                    y: 0.0,
                    z: 0.0,
                    color: -1,
                    u: 0.0,
                    v: 0.0,
                    light: LIGHT_FULL_BRIGHT,
                },
                StaticModelVertexRecord {
                    x: 1.0,
                    y: 0.0,
                    z: 0.0,
                    color: -1,
                    u: 1.0,
                    v: 0.0,
                    light: LIGHT_FULL_BRIGHT,
                },
                StaticModelVertexRecord {
                    x: 1.0,
                    y: 1.0,
                    z: 0.0,
                    color: -1,
                    u: 1.0,
                    v: 1.0,
                    light: LIGHT_FULL_BRIGHT,
                },
                StaticModelVertexRecord {
                    x: 0.0,
                    y: 1.0,
                    z: 0.0,
                    color: -1,
                    u: 0.0,
                    v: 1.0,
                    light: LIGHT_FULL_BRIGHT,
                },
            ],
            material_bits: 0,
            cull_face: -1,
            normal_face: 2,
            packed_normal: 0,
            block_emission: 0,
            render_type: 0,
            shade: 0,
            flags: 0,
            light_face: 2,
            tint_index: -1,
            has_ao: 0,
            pass_id: 0,
        };
        assert_eq!(
            OK,
            mattmc_sodium_static_model_cache_register(
                7,
                &quad as *const _ as u64,
                1,
                mem::size_of::<StaticModelQuadRecord>() as i32,
            )
        );
        let entry = NativeModelSelectorEntry {
            target_id: 7,
            weight: 1,
        };
        assert_eq!(
            OK,
            mattmc_sodium_native_model_selector_register(
                5,
                SELECTOR_DIRECT,
                &entry as *const _ as u64,
                1,
                mem::size_of::<NativeModelSelectorEntry>() as i32,
            )
        );
        assert_eq!(
            OK,
            mattmc_sodium_native_meshing_state_register(
                3,
                5,
                STATE_FLAG_MODEL,
                0,
                0,
                0,
                0,
                91,
                0,
                0,
                0,
                0,
                0,
                0.0,
                0,
                OFFSET_NONE,
                0.0,
                0.0,
                TINT_NONE,
                0.0,
                1.0,
                0.0,
                1.0,
                0.0,
                0.0,
                1.0,
                0.0,
                1.0,
                0.0,
                0.0,
                1.0,
                0.0,
                1.0,
                0.0,
                0,
            )
        );

        let mut storage = CompactSnapshotStorage::new();
        storage.set_center_state(3);
        let header = storage.header(1);
        let builders = BuilderHandles::new();
        let mut counts = [0; 3];
        assert_eq!(
            OK,
            append_compact(&builders, &header as *const _ as u64, &mut counts)
        );
        assert_eq!([1, 0, 0], counts);

        assert_eq!(OK, mattmc_sodium_static_model_cache_clear());
        let reloaded_builders = BuilderHandles::new();
        let mut reloaded_counts = [0; 3];
        assert_eq!(
            OK,
            append_compact(
                &reloaded_builders,
                &header as *const _ as u64,
                &mut reloaded_counts
            )
        );
        assert_eq!(
            [0, 0, 0],
            reloaded_counts,
            "stale compact snapshot ids must not resolve after the cache is cleared for a reload"
        );
    }
}

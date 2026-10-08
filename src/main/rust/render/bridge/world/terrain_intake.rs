//! C export of terrain layer intake (`worldrender/terrain/intake.rs`,
//! `assembly.rs`): decodes and assembles one section layer into the
//! world-mesh vertex ABI, index bytes, draw ranges and a receipt.

use crate::render::bridge::abi::{FfiWorldMeshSectionRecord, FfiWorldMeshVertex};
use crate::render::scene::mesh::{WorldMeshSection, WorldMeshVertex};
use crate::render::worldrender::frame::material_quads::canonical_material_id;
use crate::render::worldrender::terrain::staging::StagedAsset;
use crate::render::worldrender::terrain::assembly::{assemble_layer, FfiWaterSprite, LayerAssemblyParams};
use crate::render::worldrender::terrain::staging;
use crate::render::worldrender::terrain::intake::{
    decode_compact_terrain_vertices, FfiCompactTerrainDecodeParams, FfiCompactTerrainDecodeStats,
};

fn abi_vertex(vertex: &WorldMeshVertex) -> FfiWorldMeshVertex {
    FfiWorldMeshVertex {
        byte_size: std::mem::size_of::<FfiWorldMeshVertex>() as u32,
        color_argb: vertex.color_argb,
        normal_packed: vertex.normal_packed,
        light: vertex.light,
        x: vertex.position[0],
        y: vertex.position[1],
        z: vertex.position[2],
        u: vertex.uv[0],
        v: vertex.uv[1],
        atlas_u: vertex.shader_atlas_uv[0],
        atlas_v: vertex.shader_atlas_uv[1],
        shader_block_id: vertex.shader_block_id,
        shader_material_type: vertex.shader_material_type,
        terrain_material_bits: vertex.terrain_material_bits,
        mid_block_packed: vertex.mid_block_packed,
    }
}

/// Layer identity and water sprites for [`mattmc_terrain_assemble_layer`].
#[repr(C)]
#[derive(Clone, Copy, Debug)]
pub struct FfiTerrainLayerAssemblyParams {
    pub section_pos: i64,
    pub atlas_generation: i64,
    pub layer_ordinal: u32,
    /// Non-zero: water samples the block atlas.
    pub water_block_atlas: u32,
    /// Non-zero: `water` holds the still, flow and overlay sprites.
    pub water_present: u32,
    /// [`ASSEMBLY_STAGE_VERTICES`]: do not also write the vertices out (the
    /// layer is always staged in Rust, see `worldrender/terrain/staging.rs`).
    pub flags: u32,
    pub water: [FfiWaterSprite; 3],
    pub reserved_tail: u32,
}

/// Assembly receipt: identities, index layout and translucent accounting.
#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub struct FfiTerrainLayerAssemblyReceipt {
    pub mesh_key: u64,
    pub mesh_generation: u64,
    pub source_hash: u64,
    pub retained_hash: u64,
    pub omitted_hash: u64,
    pub index_type: u32,
    pub index_bytes: u32,
    pub assembled_index_count: u32,
    pub section_count: u32,
    pub max_index: i32,
    pub positive_y_sections: u32,
    pub negative_y_sections: u32,
    pub horizontal_sections: u32,
    pub translucent: u32,
    pub source_primitives: u32,
    pub non_fluid_primitives: u32,
    pub water_primitives: u32,
    pub unsupported_primitives: u32,
    pub retained_primitives: u32,
    pub omitted_primitives: u32,
    pub source_indices: u32,
    pub retained_indices: u32,
    pub omitted_indices: u32,
    pub material_switches: u32,
    pub water_still: u32,
    pub water_flow: u32,
    pub water_overlay: u32,
    pub water_texture_switches: u32,
    pub sample_count: u32,
    /// Non-zero when the vertices were not written out (only staged).
    pub staged: u32,
    pub reserved: u32,
}

const _: () = assert!(std::mem::size_of::<FfiTerrainLayerAssemblyParams>() == 96);
const _: () = assert!(std::mem::size_of::<FfiTerrainLayerAssemblyReceipt>() == 144);

/// Assembly flag: leave the vertices only in Rust's staging (no copy for
/// Java diagnostics).
pub const ASSEMBLY_STAGE_VERTICES: u32 = 1;

/// Ints per primitive sample: id, kind, retained, material, texture, retained index start.
pub const PRIMITIVE_SAMPLE_INTS: usize = 6;

/// Output buffers of [`mattmc_terrain_assemble_layer`].
#[repr(C)]
pub struct FfiTerrainLayerAssemblyOutput {
    pub vertices: *mut FfiWorldMeshVertex,
    pub vertex_capacity: u32,
    pub section_capacity: u32,
    pub indices: *mut u8,
    pub index_capacity: u64,
    pub sections: *mut FfiWorldMeshSectionRecord,
    pub samples: *mut i32,
    pub sample_capacity: u32,
    pub error_capacity: u32,
    pub error: *mut u8,
    pub decode_stats: *mut FfiCompactTerrainDecodeStats,
    pub receipt: *mut FfiTerrainLayerAssemblyReceipt,
}

/// Decodes and assembles one section layer: vertices, index bytes, draw
/// ranges and the receipt. Returns the vertex count; -2 for invalid
/// arguments or capacity; -3 when the mesh is rejected (the reason is
/// written NUL-terminated to `error`).
///
/// # Safety
/// Each pointer addresses its stated element count; `params`, `assembly`
/// and `output` (and its stats/receipt) one record each.
#[no_mangle]
pub unsafe extern "C" fn mattmc_terrain_assemble_layer(
    buffer: *const u8,
    buffer_len: u64,
    params: *const FfiCompactTerrainDecodeParams,
    segments: *const i32,
    segment_ints: u32,
    metadata: *const i32,
    metadata_ints: u32,
    sorted_indices: *const u8,
    sorted_len: u64,
    assembly: *const FfiTerrainLayerAssemblyParams,
    output: *const FfiTerrainLayerAssemblyOutput,
) -> i32 {
    if buffer.is_null() || params.is_null() || assembly.is_null() || output.is_null()
        || (segment_ints != 0 && segments.is_null()) || (metadata_ints != 0 && metadata.is_null())
        || (sorted_len != 0 && sorted_indices.is_null())
    {
        return -2;
    }
    let output = &*output;
    if output.decode_stats.is_null() || output.receipt.is_null()
        || (output.vertex_capacity != 0 && output.vertices.is_null())
        || (output.index_capacity != 0 && output.indices.is_null())
        || (output.section_capacity != 0 && output.sections.is_null())
        || (output.sample_capacity != 0 && output.samples.is_null())
        || (output.error_capacity != 0 && output.error.is_null())
    {
        return -2;
    }
    let ints = |pointer: *const i32, count: u32| {
        if count == 0 { &[][..] } else { std::slice::from_raw_parts(pointer, count as usize) }
    };
    let buffer = std::slice::from_raw_parts(buffer, buffer_len as usize);
    let sorted = if sorted_len == 0 { &[][..] } else { std::slice::from_raw_parts(sorted_indices, sorted_len as usize) };
    let params = std::ptr::read_unaligned(params);
    let assembly = std::ptr::read_unaligned(assembly);
    let reject = |message: &str| {
        if output.error_capacity > 0 {
            let bytes = message.as_bytes();
            let length = bytes.len().min(output.error_capacity as usize - 1);
            std::ptr::copy_nonoverlapping(bytes.as_ptr(), output.error, length);
            output.error.add(length).write(0);
        }
        -3
    };
    thread_local! {
        static SCRATCH: std::cell::RefCell<Vec<WorldMeshVertex>> = const { std::cell::RefCell::new(Vec::new()) };
    }
    SCRATCH.with(|scratch| {
        let mut vertices = scratch.borrow_mut();
        let mut decoded_stats = FfiCompactTerrainDecodeStats::default();
        let segments = ints(segments, segment_ints);
        let metadata = ints(metadata, metadata_ints);
        if let Err(error) =
            decode_compact_terrain_vertices(buffer, &params, segments, metadata, &mut vertices, &mut decoded_stats)
        {
            return reject(&format!("Rust terrain vertex decode rejected the section mesh: {error:?}"));
        }
        let layer = LayerAssemblyParams {
            section_pos: assembly.section_pos,
            layer_ordinal: assembly.layer_ordinal,
            atlas_generation: assembly.atlas_generation,
            water_block_atlas: assembly.water_block_atlas != 0,
            water: (assembly.water_present != 0).then_some(assembly.water),
        };
        let assembled = match assemble_layer(&mut vertices, segments, metadata, sorted, &layer) {
            Ok(assembled) => assembled,
            Err(error) => return reject(&error),
        };
        let accounting = assembled.translucent.clone().unwrap_or_default();
        let vertex_count = vertices.len();
        let stage = assembly.flags & ASSEMBLY_STAGE_VERTICES != 0;
        if (!stage && vertex_count > output.vertex_capacity as usize)
            || assembled.index_bytes.len() as u64 > output.index_capacity
            || assembled.sections.len() > output.section_capacity as usize
        {
            return -2;
        }
        if !stage {
            if vertex_count > output.vertex_capacity as usize {
                return -2;
            }
            for (index, vertex) in vertices.iter().enumerate() {
                output.vertices.add(index).write(abi_vertex(vertex));
            }
        }
        // The update takes the layer from terrain residency, which takes it
        // from staging at registration; the frontend's section form is
        // checked here, once.
        let mut sections = Vec::with_capacity(assembled.sections.len());
        for section in &assembled.sections {
            let Some(material_id) = canonical_material_id(section.material_id) else {
                return reject(&format!("unknown world mesh material id {}", section.material_id));
            };
            if section.source_facing > 6 {
                return reject(&format!("unknown world mesh source facing {}", section.source_facing));
            }
            sections.push(WorldMeshSection { material_id, ..*section });
        }
        let asset = StagedAsset {
            index_type: assembled.index_type,
            vertices: std::mem::take(&mut *vertices),
            index_bytes: assembled.index_bytes.clone(),
            sections,
        };
        if !staging::stage(assembled.mesh_key, assembled.mesh_generation, asset) {
            return reject("Rust terrain staging bound reached");
        }
        let staged = stage;
        std::ptr::copy_nonoverlapping(assembled.index_bytes.as_ptr(), output.indices, assembled.index_bytes.len());
        for (index, section) in assembled.sections.iter().enumerate() {
            output.sections.add(index).write_unaligned(FfiWorldMeshSectionRecord {
                byte_size: std::mem::size_of::<FfiWorldMeshSectionRecord>() as u32,
                material_id: section.material_id,
                texture_id: section.texture_id,
                material_mode: section.material_mode,
                cull_policy: section.cull_policy,
                winding: section.winding,
                index_offset: section.index_offset,
                index_count: section.index_count,
                source_facing: section.source_facing,
            });
        }
        let samples = accounting.samples.len().min(output.sample_capacity as usize / PRIMITIVE_SAMPLE_INTS);
        for (index, sample) in accounting.samples.iter().take(samples).enumerate() {
            let fields = [
                sample.primitive_id as i32,
                sample.primitive_kind,
                sample.retained as i32,
                sample.material_id as i32,
                sample.texture_id as i32,
                sample.retained_index_start as i32,
            ];
            for (field, value) in fields.into_iter().enumerate() {
                output.samples.add(index * PRIMITIVE_SAMPLE_INTS + field).write(value);
            }
        }
        std::ptr::write_unaligned(output.decode_stats, decoded_stats);
        std::ptr::write_unaligned(output.receipt, FfiTerrainLayerAssemblyReceipt {
            mesh_key: assembled.mesh_key,
            mesh_generation: assembled.mesh_generation,
            source_hash: accounting.source_hash,
            retained_hash: accounting.retained_hash,
            omitted_hash: accounting.omitted_hash,
            index_type: assembled.index_type,
            index_bytes: assembled.index_bytes.len() as u32,
            assembled_index_count: assembled.assembled_index_count,
            section_count: assembled.sections.len() as u32,
            max_index: assembled.max_index,
            positive_y_sections: assembled.positive_y_sections,
            negative_y_sections: assembled.negative_y_sections,
            horizontal_sections: assembled.horizontal_sections,
            translucent: assembled.translucent.is_some() as u32,
            source_primitives: accounting.source_primitives,
            non_fluid_primitives: accounting.non_fluid_primitives,
            water_primitives: accounting.water_primitives,
            unsupported_primitives: accounting.unsupported_primitives,
            retained_primitives: accounting.retained_primitives,
            omitted_primitives: accounting.omitted_primitives,
            source_indices: accounting.source_indices,
            retained_indices: accounting.retained_indices,
            omitted_indices: accounting.omitted_indices,
            material_switches: accounting.material_switches,
            water_still: accounting.water_still,
            water_flow: accounting.water_flow,
            water_overlay: accounting.water_overlay,
            water_texture_switches: accounting.water_texture_switches,
            sample_count: samples as u32,
            staged: staged as u32,
            reserved: 0,
        });
        vertex_count as i32
    })
}

/// Drops a layer's staged vertices that will not be published (identical
/// rebuild, removal before upload). `mesh_generation` 0 drops any generation.
#[no_mangle]
pub extern "C" fn mattmc_terrain_discard_staged(mesh_key: u64, mesh_generation: u64) {
    staging::discard(mesh_key, mesh_generation);
}

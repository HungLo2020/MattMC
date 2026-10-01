//! Distant Horizons LOD asset and render-frame records.

use super::*;

/// Coarse, backend-neutral Distant Horizons LOD vertex semantics. This is a
/// copied CPU record, not a legacy DH/OpenGL vertex attribute declaration.
#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiWorldLodVertex {
    pub byte_size: u32,
    pub local_x: u16,
    pub local_y: u16,
    pub local_z: u16,
    pub packed_light_and_micro_offset: u16,
    pub color_rgba: u32,
    pub material_id: u32,
    pub normal_index: u32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiWorldLodSegmentRecord {
    pub byte_size: u32,
    pub layer: u32,
    pub vertices: FfiSlice<FfiWorldLodVertex>,
    /// Fixed 16-byte semantic DH vertex stream. Exactly one of this and
    /// `vertices` is populated; retaining the structured form keeps existing
    /// semantic producers ABI-compatible while the packed form avoids a
    /// Java-record/foreign-struct expansion for the real DH producer.
    pub packed_vertices: FfiSlice<u8>,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiWorldLodColumnAssetRecord {
    pub byte_size: u32,
    pub vertex_layout_version: u32,
    pub origin_x: i32,
    pub origin_y: i32,
    pub origin_z: i32,
    pub reserved0: u32,
    pub column_key: u64,
    pub column_generation: u64,
    pub segments: FfiSlice<FfiWorldLodSegmentRecord>,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiWorldLodColumnRetirementRecord {
    pub byte_size: u32,
    pub reserved0: u32,
    pub column_key: u64,
    pub column_generation: u64,
}

/// One stable, copied source identity for a Distant Horizons reduced quad.
/// The strings are semantic resource identities, never atlas objects or
/// backend handles. IDs in the segment records are one-based into the
/// enclosing column table; zero is unavailable and `u32::MAX` is mixed.
#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiWorldLodMaterialIdentityRecord {
    pub byte_size: u32,
    pub reserved0: u32,
    pub block_state_identity_utf8: FfiBytes,
    pub biome_identity_utf8: FfiBytes,
}

/// Per-emitted-quad semantic material references for one compact LOD segment.
/// This remains separate from DH's copied vertex stream so existing vertex
/// layout/version semantics stay intact.
#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiWorldLodSegmentMaterialProvenanceRecord {
    pub byte_size: u32,
    pub layer: u32,
    pub segment_index: u32,
    pub reserved0: u32,
    pub quad_material_ids: FfiSlice<u32>,
    pub quad_variant_states: FfiSlice<u8>,
    pub quad_variant_positions: FfiSlice<u64>,
}

/// One exact copied atlas region for one material identity and block face.
/// It is source data only: neither an atlas object nor a backend texture.
#[repr(C)]
#[derive(Clone, Copy, Debug, Default, PartialEq)]
pub struct FfiWorldLodFaceMaterialRecord {
    pub byte_size: u32,
    pub material_id: u32,
    pub face: u32,
    /// Ordered co-planar face layer. Kept in the original reserved slot so
    /// the Panama layout remains exactly 88 bytes.
    pub face_layer: u32,
    pub atlas_identity_utf8: FfiBytes,
    pub sprite_identity_utf8: FfiBytes,
    pub u0: f32,
    pub v0: f32,
    pub u1: f32,
    pub v1: f32,
    /// Stable UV-corner permutation for the copied baked face. Tint semantics
    /// live in `face_layer` so this field stays an unambiguous permutation.
    pub uv_corner_order: u32,
    pub variant_position: u64,
}

/// Bounded semantic material sidecar paired with one immutable LOD column
/// asset generation. It is copied at the FFI boundary and cannot select a
/// textured route on its own.
#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiWorldLodColumnMaterialProvenanceRecord {
    pub byte_size: u32,
    pub reserved0: u32,
    pub column_key: u64,
    pub column_generation: u64,
    pub identities: FfiSlice<FfiWorldLodMaterialIdentityRecord>,
    pub segments: FfiSlice<FfiWorldLodSegmentMaterialProvenanceRecord>,
    pub face_materials: FfiSlice<FfiWorldLodFaceMaterialRecord>,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiWorldLodAssetUpdateRequest {
    pub header: FfiHeader,
    pub generation: u64,
    pub assets: FfiSlice<FfiWorldLodColumnAssetRecord>,
    pub retirements: FfiSlice<FfiWorldLodColumnRetirementRecord>,
    pub negotiated_feature_bits: u64,
    pub material_provenance: FfiSlice<FfiWorldLodColumnMaterialProvenanceRecord>,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiWorldLodColumnInstanceRecord {
    pub byte_size: u32,
    pub layer: u32,
    pub segment_index: u32,
    pub order: u32,
    pub column_key: u64,
    pub column_generation: u64,
}

/// Coarse resolved Distant Horizons render semantics for one combined frame.
/// This deliberately contains values consumed by the public DH terrain
/// program, never a program object, VBO/VAO identity, texture unit, or other
/// native renderer state.
#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiWorldLodRenderFrame {
    pub byte_size: u32,
    pub enabled: u32,
    pub flags: u32,
    pub world_y_offset: i32,
    pub combined_matrix: [f32; 16],
    pub model_view_matrix: [f32; 16],
    pub projection_matrix: [f32; 16],
    pub projection_inverse_matrix: [f32; 16],
    pub clip_distance: f32,
    pub micro_offset: f32,
    pub noise_intensity: f32,
    pub earth_radius: f32,
    pub noise_steps: u32,
    pub noise_dropoff: i32,
    pub reserved0: u32,
    pub camera_world_x: f32,
    pub camera_world_y: f32,
    pub camera_world_z: f32,
    pub dh_fog_parameters: [f32; 20],
    /// Copied level maximum used by Frozen's vanilla-fade cloud guard.
    pub max_level_height: i32,
    /// Explicit DH SSAO parameters: enabled, sample count, radius, strength,
    /// minimum light, bias, fade distance, and blur radius. These are copied
    /// configuration values only; Rust owns the SSAO image and pass.
    pub ssao_parameters: [f32; 8],
}

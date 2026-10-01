//! Source vertex semantics, mesh derivation, packing and tangent math.

use super::*;

/// The exact CPU vertex semantics that a source-pack route may need after the
/// ordinary mesh has been packed for its own Rust-owned GPU ABI.  This is
/// deliberately narrower than `WorldMeshAsset`: indices, sections, and the
/// entity identity already have one authoritative retained copy in
/// `MeshAssetStore` and must not be cloned again for deferred source lowering.
#[derive(Clone, Copy, Debug)]
pub(crate) struct SourceEntitySemanticVertex {
    pub(in crate::render::worldrender) position: [f32; 3],
    pub(in crate::render::worldrender) uv: [f32; 2],
    pub(in crate::render::worldrender) color_argb: u32,
    pub(in crate::render::worldrender) normal_packed: u32,
    pub(in crate::render::worldrender) light: u32,
}

#[derive(Clone, Debug)]
pub(crate) enum SourceMeshSemanticInput {
    Terrain(Vec<WorldMeshVertex>),
    Entity(Vec<SourceEntitySemanticVertex>),
}

pub(crate) trait NormalSourceVertex: Copy {
    fn position(self) -> [f32; 3];
    fn normal_packed(self) -> u32;
}

pub(crate) trait TerrainSourceVertex: NormalSourceVertex {
    fn uv(self) -> [f32; 2];
    fn shader_atlas_uv(self) -> [f32; 2];
    fn shader_block_id(self) -> i32;
    fn shader_material_type(self) -> i32;
    fn mid_block_packed(self) -> u32;
    fn color_argb(self) -> u32;
    fn light(self) -> u32;
}

impl NormalSourceVertex for WorldMeshVertex {
    fn position(self) -> [f32; 3] {
        self.position
    }

    fn normal_packed(self) -> u32 {
        self.normal_packed
    }
}

impl TerrainSourceVertex for WorldMeshVertex {
    fn uv(self) -> [f32; 2] {
        self.uv
    }

    fn shader_atlas_uv(self) -> [f32; 2] {
        self.shader_atlas_uv
    }

    fn shader_block_id(self) -> i32 {
        self.shader_block_id
    }

    fn shader_material_type(self) -> i32 {
        self.shader_material_type
    }

    fn mid_block_packed(self) -> u32 {
        self.mid_block_packed
    }

    fn color_argb(self) -> u32 {
        self.color_argb
    }

    fn light(self) -> u32 {
        self.light
    }
}

pub(crate) trait EntitySourceVertex: NormalSourceVertex {
    fn uv(self) -> [f32; 2];
    fn color_argb(self) -> u32;
    fn light(self) -> u32;
}

impl EntitySourceVertex for WorldMeshVertex {
    fn uv(self) -> [f32; 2] {
        self.uv
    }

    fn color_argb(self) -> u32 {
        self.color_argb
    }

    fn light(self) -> u32 {
        self.light
    }
}

impl NormalSourceVertex for SourceEntitySemanticVertex {
    fn position(self) -> [f32; 3] {
        self.position
    }

    fn normal_packed(self) -> u32 {
        self.normal_packed
    }
}

impl EntitySourceVertex for SourceEntitySemanticVertex {
    fn uv(self) -> [f32; 2] {
        self.uv
    }

    fn color_argb(self) -> u32 {
        self.color_argb
    }

    fn light(self) -> u32 {
        self.light
    }
}

/// Borrowed complete source-lowering view assembled from the one authoritative
/// mesh metadata copy plus the semantic sidecar above.  It has no Java or Iris
/// ownership and creates no transient duplicate of an asset while a selected
/// Rust source pass is being prepared.
pub(crate) struct SourceMeshAssetView<'a, V> {
    pub(in crate::render::worldrender) mesh_key: u64,
    pub(in crate::render::worldrender) mesh_generation: u64,
    pub(in crate::render::worldrender) vertices: &'a [V],
    pub(in crate::render::worldrender) index_bytes: &'a [u8],
    pub(in crate::render::worldrender) index_type: IndexType,
    pub(in crate::render::worldrender) sections: &'a [WorldMeshSection],
    pub(in crate::render::worldrender) entity_identity: &'a str,
}

impl WorldMeshAsset {
    pub(crate) fn source_terrain_view(&self) -> SourceMeshAssetView<'_, WorldMeshVertex> {
        SourceMeshAssetView {
            mesh_key: self.mesh_key,
            mesh_generation: self.mesh_generation,
            vertices: &self.vertices,
            index_bytes: &self.index_bytes,
            index_type: self.index_type,
            sections: &self.sections,
            entity_identity: &self.entity_identity,
        }
    }

    pub(crate) fn source_entity_view(&self) -> SourceMeshAssetView<'_, WorldMeshVertex> {
        self.source_terrain_view()
    }
}

/// Rust-owned semantic vertex expansion for a future source-derived terrain
/// program. The current asset ABI stores atlas UVs and indexed quad geometry,
/// but Complementary additionally requires the sprite midpoint and tangent.
/// They are derived here from copied semantic mesh data, never from an Iris
/// vertex buffer, GL attribute stream, or Java rendering object.
#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) struct SourceTerrainVertex {
    pub position: [f32; 3],
    pub atlas_uv: [f32; 2],
    pub color_argb: u32,
    pub normal_packed: u32,
    pub light: u32,
    pub shader_block_id: i32,
    /// `true` only when the canonical raw state ID was resolved through the
    /// active source contract. A resolved pack default remains `-1`; it must
    /// not be confused with missing source semantics.
    pub shader_material_resolved: bool,
    pub shader_material_type: i32,
    /// Copied semantic `at_midBlock` information for the source-lowered
    /// terrain vertex contract.
    pub mid_block_packed: u32,
    pub sprite_midpoint: [f32; 2],
    /// xyz is the normalized tangent and w is its handedness.
    pub tangent: [f32; 4],
}

/// Expanded triangle stream retaining the original indexed-quad winding. It
/// is source-lowering preparation only and intentionally owns no GAL or
/// backend resource.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct SourceTerrainMesh {
    pub vertices: Vec<SourceTerrainVertex>,
    pub indices: Vec<u32>,
}

/// (program key, uniform frame, texture transforms, packed legacy, packed scalar).
pub(crate) type SourceUniformPackMemoEntry = (
    (usize, u64),
    TerrainSourceUniformFrame,
    TerrainSourceTextureTransforms,
    Vec<u8>,
    Vec<u8>,
);

/// Per-frame fast path for the terrain batch loops. The frame's source
/// programs are validated once when the scope opens, and pack keys are
/// memoized by (program, resource snapshot) address. Both are sound only
/// because the scope is closed before those borrowed values can change.
pub(crate) struct SourceTerrainBatchScope {
    pub(in crate::render::worldrender) frame_id: u64,
    /// Whether the audit-only scalar uniform receipt is enabled, read once.
    pub(in crate::render::worldrender) scalar_uniform_receipts: bool,
    pub(in crate::render::worldrender) validated_programs: Vec<usize>,
    pub(in crate::render::worldrender) pack_keys: Vec<(usize, usize, LoweredSourceTerrainPackKey)>,
}

/// Byte budget for converted source terrain streams kept across frames.
pub(crate) const SOURCE_TERRAIN_MESH_CACHE_BYTES: usize = 256 * 1024 * 1024;

#[derive(Default)]
pub(crate) struct SourceTerrainMeshCache {
    pub(in crate::render::worldrender) entries: std::collections::HashMap<(u64, u64, bool), (Arc<SourceTerrainMeshAsset>, u64)>,
    pub(in crate::render::worldrender) bytes: usize,
    pub(in crate::render::worldrender) clock: u64,
}

impl SourceTerrainMeshCache {
    pub(crate) fn entry_bytes(mesh: &SourceTerrainMeshAsset) -> usize {
        mesh.vertex_bytes.len()
            + mesh.index_bytes.len()
            + mesh.sections.len() * std::mem::size_of::<SourceTerrainMeshSection>()
    }

    pub(crate) fn get(&mut self, key: &(u64, u64, bool)) -> Option<Arc<SourceTerrainMeshAsset>> {
        self.clock += 1;
        let clock = self.clock;
        self.entries.get_mut(key).map(|(mesh, used)| {
            *used = clock;
            Arc::clone(mesh)
        })
    }

    pub(crate) fn insert(&mut self, key: (u64, u64, bool), mesh: Arc<SourceTerrainMeshAsset>) {
        self.clock += 1;
        let size = Self::entry_bytes(&mesh);
        if let Some((old, _)) = self.entries.insert(key, (mesh, self.clock)) {
            self.bytes = self.bytes.saturating_sub(Self::entry_bytes(&old));
        }
        self.bytes += size;
        if self.bytes > SOURCE_TERRAIN_MESH_CACHE_BYTES {
            // Evict the least recently used entries down to 3/4 of budget.
            let mut order = self
                .entries
                .iter()
                .map(|(key, (_, used))| (*used, *key))
                .collect::<Vec<_>>();
            order.sort_unstable();
            for (_, key) in order {
                if self.bytes <= SOURCE_TERRAIN_MESH_CACHE_BYTES / 4 * 3 {
                    break;
                }
                if let Some((old, _)) = self.entries.remove(&key) {
                    self.bytes = self.bytes.saturating_sub(Self::entry_bytes(&old));
                }
            }
        }
    }

    pub(crate) fn clear(&mut self) {
        self.entries.clear();
        self.bytes = 0;
    }
}

/// Message prefix of a whole-frame failure the Java coordinator may resubmit
/// once: the shader route was disarmed and the frame can be drawn by the
/// vanilla Rust route.
pub const SELECTED_SOURCE_RETRYABLE_FAILURE: &str = "retryable selected-source failure";

pub(crate) const SOURCE_ENTITY_MESH_CACHE_BYTES: usize = 64 * 1024 * 1024;

/// Converted entity/hand source streams, keyed by exact mesh generation and
/// instance light. Conversion is pure in the mesh, so reuse across frames is
/// exact; entries are evicted least-recently-used above a byte budget.
#[derive(Default)]
pub(crate) struct SourceEntityMeshCache {
    pub(in crate::render::worldrender) entries: std::collections::HashMap<(u64, u64, u32), (Arc<SourceEntityMeshAsset>, u64)>,
    pub(in crate::render::worldrender) bytes: usize,
    pub(in crate::render::worldrender) clock: u64,
}

impl SourceEntityMeshCache {
    pub(crate) fn entry_bytes(mesh: &SourceEntityMeshAsset) -> usize {
        mesh.vertex_bytes.len()
            + mesh.index_bytes.len()
            + mesh.sections.len() * std::mem::size_of::<SourceTerrainMeshSection>()
    }

    pub(crate) fn get(&mut self, key: &(u64, u64, u32)) -> Option<Arc<SourceEntityMeshAsset>> {
        self.clock += 1;
        let clock = self.clock;
        self.entries.get_mut(key).map(|(mesh, used)| {
            *used = clock;
            Arc::clone(mesh)
        })
    }

    pub(crate) fn insert(&mut self, key: (u64, u64, u32), mesh: Arc<SourceEntityMeshAsset>) {
        self.clock += 1;
        let size = Self::entry_bytes(&mesh);
        if let Some((old, _)) = self.entries.insert(key, (mesh, self.clock)) {
            self.bytes = self.bytes.saturating_sub(Self::entry_bytes(&old));
        }
        self.bytes += size;
        if self.bytes > SOURCE_ENTITY_MESH_CACHE_BYTES {
            let mut order = self
                .entries
                .iter()
                .map(|(key, (_, used))| (*used, *key))
                .collect::<Vec<_>>();
            order.sort_unstable();
            for (_, key) in order {
                if self.bytes <= SOURCE_ENTITY_MESH_CACHE_BYTES / 4 * 3 {
                    break;
                }
                if let Some((old, _)) = self.entries.remove(&key) {
                    self.bytes = self.bytes.saturating_sub(Self::entry_bytes(&old));
                }
            }
        }
    }

    pub(crate) fn clear(&mut self) {
        self.entries.clear();
        self.bytes = 0;
    }
}

/// Generation-bound, backend-neutral input for a future source-derived terrain
/// draw. Unlike the ordinary mesh asset, this uses the fixed source semantic
/// vertex record and explicit `u32` indices. Section ranges retain their
/// semantic order while their byte offsets are remapped from the original
/// index element width. It owns no GAL handles and cannot select a route.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct SourceTerrainMeshAsset {
    pub mesh_key: u64,
    pub mesh_generation: u64,
    pub vertex_bytes: Vec<u8>,
    pub index_bytes: Vec<u8>,
    pub sections: Vec<SourceTerrainMeshSection>,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) struct SourceTerrainMeshSection {
    /// Exact material identity from the copied mesh section. Source-derived
    /// terrain programs must choose their pass/resource contract from this
    /// semantic value, never from an overloaded per-vertex render-type lane.
    pub material_id: u32,
    /// Stable semantic texture identity retained for material-specific source
    /// passes such as water. This is not a backend texture handle.
    pub texture_id: u32,
    pub material_mode: u32,
    pub cull_policy: u32,
    pub winding: u32,
    /// Byte offset into `SourceTerrainMeshAsset::index_bytes`.
    pub index_offset: u64,
    pub index_count: u32,
}

impl SourceTerrainMeshAsset {
    /// Validates the fixed source-mesh storage ABI before any later runtime
    /// turns this owned semantic artifact into GAL buffers. This is separate
    /// from ordinary mesh validation: source terrain always uses packed
    /// 128-byte vertices and explicit u32 indices, regardless of the
    /// original indexed-mesh transport width.
    pub(crate) fn validate(&self) -> GalResult<()> {
        if self.mesh_key == 0 || self.mesh_generation == 0 {
            return Err(GalError::invalid_argument(
                "source terrain mesh asset requires non-zero key and generation",
            ));
        }
        if self.vertex_bytes.is_empty()
            || self.vertex_bytes.len() % TERRAIN_SOURCE_VERTEX_BYTES != 0
        {
            return Err(GalError::invalid_argument(format!(
                "source terrain mesh {} vertex bytes are not aligned to the fixed {}-byte ABI",
                self.mesh_key, TERRAIN_SOURCE_VERTEX_BYTES
            )));
        }
        if self.index_bytes.is_empty() || self.index_bytes.len() % std::mem::size_of::<u32>() != 0 {
            return Err(GalError::invalid_argument(format!(
                "source terrain mesh {} index bytes are not aligned to explicit u32 indices",
                self.mesh_key
            )));
        }
        let vertex_count = self.vertex_bytes.len() / TERRAIN_SOURCE_VERTEX_BYTES;
        let index_count = self.index_bytes.len() / std::mem::size_of::<u32>();
        if index_count % 3 != 0 {
            return Err(GalError::invalid_argument(format!(
                "source terrain mesh {} index count {} is not triangle-aligned",
                self.mesh_key, index_count
            )));
        }
        for (index, bytes) in self
            .index_bytes
            .chunks_exact(std::mem::size_of::<u32>())
            .enumerate()
        {
            let vertex = u32::from_ne_bytes(bytes.try_into().expect("u32-sized chunk"));
            if vertex as usize >= vertex_count {
                return Err(GalError::invalid_argument(format!(
                    "source terrain mesh {} index {} references vertex {} outside {} vertices",
                    self.mesh_key, index, vertex, vertex_count
                )));
            }
        }
        if self.sections.is_empty() {
            return Err(GalError::invalid_argument(format!(
                "source terrain mesh {} has no semantic sections",
                self.mesh_key
            )));
        }
        for (section_index, section) in self.sections.iter().enumerate() {
            if section.material_id == 0 || section.texture_id == 0 {
                return Err(GalError::invalid_argument(format!(
                    "source terrain mesh {} section {} lacks material or texture identity",
                    self.mesh_key, section_index
                )));
            }
            if !matches!(
                section.material_mode,
                WORLD_MATERIAL_MODE_OPAQUE
                    | WORLD_MATERIAL_MODE_CUTOUT
                    | WORLD_MATERIAL_MODE_TRANSLUCENT
            ) {
                return Err(GalError::unsupported_feature(format!(
                    "source terrain mesh {} section {} uses unsupported material mode {}",
                    self.mesh_key, section_index, section.material_mode
                )));
            }
            let _ = cull_mode_from_policy(section.cull_policy)?;
            if !matches!(section.winding, WORLD_WINDING_CCW | WORLD_WINDING_CW) {
                return Err(GalError::invalid_argument(format!(
                    "source terrain mesh {} section {} has unsupported winding {}",
                    self.mesh_key, section_index, section.winding
                )));
            }
            if section.index_offset % std::mem::size_of::<u32>() as u64 != 0 {
                return Err(GalError::invalid_argument(format!(
                    "source terrain mesh {} section {} offset {} is not u32-aligned",
                    self.mesh_key, section_index, section.index_offset
                )));
            }
            if section.index_count == 0 || section.index_count % 3 != 0 {
                return Err(GalError::invalid_argument(format!(
                    "source terrain mesh {} section {} index count {} is not a non-empty triangle range",
                    self.mesh_key, section_index, section.index_count
                )));
            }
            let first = section.index_offset / std::mem::size_of::<u32>() as u64;
            let end = first
                .checked_add(u64::from(section.index_count))
                .ok_or_else(|| {
                    GalError::invalid_argument("source terrain mesh section range overflows")
                })?;
            if end > index_count as u64 {
                return Err(GalError::invalid_argument(format!(
                    "source terrain mesh {} section {} range {}..{} exceeds {} indices",
                    self.mesh_key, section_index, first, end, index_count
                )));
            }
        }
        Ok(())
    }
}

/// One copied vertex for a selected source entity program. The fixed storage
/// layout is shared with the owned source vertex preamble, but its UVs are
/// explicitly local to the entity material texture and it carries no terrain
/// block-state or atlas-material meaning.
#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) struct SourceEntityVertex {
    pub position: [f32; 3],
    pub local_uv: [f32; 2],
    pub color_argb: u32,
    pub normal_packed: u32,
    pub light: u32,
    pub texture_midpoint: [f32; 2],
    pub tangent: [f32; 4],
}

#[derive(Clone, Debug, PartialEq)]
pub(crate) struct SourceEntityMesh {
    pub(in crate::render::worldrender) vertices: Vec<SourceEntityVertex>,
    pub(in crate::render::worldrender) indices: Vec<u32>,
}

/// Expanded indexed source mesh for an entity-model producer. This is a
/// CPU-owned preparation artifact: it has no GAL handle, backend state, or
/// route-selection authority. Rust resolves its canonical entity identity
/// through the selected pack's `entity.properties` at the later pass stage.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct SourceEntityMeshAsset {
    pub mesh_key: u64,
    pub mesh_generation: u64,
    pub entity_identity: String,
    pub vertex_bytes: Vec<u8>,
    pub index_bytes: Vec<u8>,
    pub sections: Vec<SourceTerrainMeshSection>,
}

impl SourceEntityMeshAsset {
    pub(crate) fn validate(&self) -> GalResult<()> {
        if self.mesh_key == 0 || self.mesh_generation == 0 {
            return Err(GalError::invalid_argument(
                "source entity mesh asset requires non-zero key and generation",
            ));
        }
        if canonical_resource_location(&self.entity_identity)
            .ok()
            .as_deref()
            != Some(self.entity_identity.as_str())
        {
            return Err(GalError::invalid_argument(format!(
                "source entity mesh {} requires a canonical entity identity",
                self.mesh_key
            )));
        }
        if self.vertex_bytes.is_empty()
            || self.vertex_bytes.len() % TERRAIN_SOURCE_VERTEX_BYTES != 0
        {
            return Err(GalError::invalid_argument(format!(
                "source entity mesh {} vertex bytes are not aligned to the fixed {}-byte ABI",
                self.mesh_key, TERRAIN_SOURCE_VERTEX_BYTES
            )));
        }
        if self.index_bytes.is_empty() || self.index_bytes.len() % std::mem::size_of::<u32>() != 0 {
            return Err(GalError::invalid_argument(format!(
                "source entity mesh {} index bytes are not aligned to explicit u32 indices",
                self.mesh_key
            )));
        }
        let vertex_count = self.vertex_bytes.len() / TERRAIN_SOURCE_VERTEX_BYTES;
        let index_count = self.index_bytes.len() / std::mem::size_of::<u32>();
        if index_count % 3 != 0 {
            return Err(GalError::invalid_argument(format!(
                "source entity mesh {} index count {} is not triangle-aligned",
                self.mesh_key, index_count
            )));
        }
        for (index, bytes) in self
            .index_bytes
            .chunks_exact(std::mem::size_of::<u32>())
            .enumerate()
        {
            let vertex = u32::from_ne_bytes(bytes.try_into().expect("u32-sized chunk"));
            if vertex as usize >= vertex_count {
                return Err(GalError::invalid_argument(format!(
                    "source entity mesh {} index {} references vertex {} outside {} vertices",
                    self.mesh_key, index, vertex, vertex_count
                )));
            }
        }
        if self.sections.is_empty() {
            return Err(GalError::invalid_argument(format!(
                "source entity mesh {} has no semantic sections",
                self.mesh_key
            )));
        }
        for (section_index, section) in self.sections.iter().enumerate() {
            if section.material_id == 0 || section.texture_id == 0 {
                return Err(GalError::invalid_argument(format!(
                    "source entity mesh {} section {} lacks material or texture identity",
                    self.mesh_key, section_index
                )));
            }
            if !matches!(
                section.material_mode,
                WORLD_MATERIAL_MODE_OPAQUE
                    | WORLD_MATERIAL_MODE_CUTOUT
                    | WORLD_MATERIAL_MODE_TRANSLUCENT
                    | WORLD_MATERIAL_MODE_TRANSLUCENT_CUTOUT
                    | WORLD_MATERIAL_MODE_GLINT
                    | WORLD_MATERIAL_MODE_OPTICAL_STENCIL_WRITE
                    | WORLD_MATERIAL_MODE_OPTICAL_STENCIL_TEST
            ) {
                return Err(GalError::unsupported_feature(format!(
                    "source entity mesh {} section {} uses unsupported material mode {}",
                    self.mesh_key, section_index, section.material_mode
                )));
            }
            let _ = cull_mode_from_policy(section.cull_policy)?;
            if !matches!(section.winding, WORLD_WINDING_CCW | WORLD_WINDING_CW) {
                return Err(GalError::invalid_argument(format!(
                    "source entity mesh {} section {} has unsupported winding {}",
                    self.mesh_key, section_index, section.winding
                )));
            }
            if section.index_offset % std::mem::size_of::<u32>() as u64 != 0 {
                return Err(GalError::invalid_argument(format!(
                    "source entity mesh {} section {} offset {} is not u32-aligned",
                    self.mesh_key, section_index, section.index_offset
                )));
            }
            if section.index_count == 0 || section.index_count % 3 != 0 {
                return Err(GalError::invalid_argument(format!(
                    "source entity mesh {} section {} index count {} is not a non-empty triangle range",
                    self.mesh_key, section_index, section.index_count
                )));
            }
            let first = section.index_offset / std::mem::size_of::<u32>() as u64;
            let end = first
                .checked_add(u64::from(section.index_count))
                .ok_or_else(|| {
                    GalError::invalid_argument("source entity mesh section range overflows")
                })?;
            if end > index_count as u64 {
                return Err(GalError::invalid_argument(format!(
                    "source entity mesh {} section {} range {}..{} exceeds {} indices",
                    self.mesh_key, section_index, first, end, index_count
                )));
            }
        }
        Ok(())
    }
}

/// Maps one ordinary indexed-mesh draw range to the exact source-mesh
/// section ordinals it covers. A later selected source route must use this
/// instead of inferring a range from remapped u32 byte offsets: the ordinary
/// mesh can be U16 or U32, and compatible sections may already have been
/// coalesced before source preparation begins.
pub(crate) fn source_section_indices_for_mesh_range(
    original_sections: &[WorldMeshSection],
    original_index_type: IndexType,
    source_mesh: &SourceTerrainMeshAsset,
    index_offset: u64,
    index_count: u32,
) -> GalResult<Vec<u32>> {
    // `source_mesh` was validated once when converted (source_terrain_mesh_asset).
    if original_sections.len() != source_mesh.sections.len() {
        return Err(GalError::invalid_argument(format!(
            "source terrain mesh {} has {} sections but ordinary mesh range has {}",
            source_mesh.mesh_key,
            source_mesh.sections.len(),
            original_sections.len()
        )));
    }
    if index_count == 0 || index_count % 3 != 0 {
        return Err(GalError::invalid_argument(
            "source terrain range requires a non-empty triangle-aligned index count",
        ));
    }
    let stride = index_stride(original_index_type);
    let range_bytes = u64::from(index_count)
        .checked_mul(stride)
        .ok_or_else(|| GalError::invalid_argument("source terrain range byte length overflows"))?;
    let range_end = index_offset
        .checked_add(range_bytes)
        .ok_or_else(|| GalError::invalid_argument("source terrain range end overflows"))?;
    let mut cursor = index_offset;
    let mut selected = Vec::new();

    for (section_index, section) in original_sections.iter().enumerate() {
        let section_start = u64::from(section.index_offset);
        let section_bytes = u64::from(section.index_count)
            .checked_mul(stride)
            .ok_or_else(|| {
                GalError::invalid_argument("source terrain section byte length overflows")
            })?;
        let section_end = section_start
            .checked_add(section_bytes)
            .ok_or_else(|| GalError::invalid_argument("source terrain section end overflows"))?;
        if section_end <= index_offset || section_start >= range_end {
            continue;
        }
        if section_start != cursor || section_end > range_end {
            return Err(GalError::invalid_argument(format!(
                "source terrain range {}..{} splits ordinary mesh section {} range {}..{}",
                index_offset, range_end, section_index, section_start, section_end
            )));
        }
        let source_section = source_mesh
            .sections
            .get(section_index)
            .expect("source section count checked before range mapping");
        if source_section.index_count != section.index_count {
            return Err(GalError::invalid_argument(format!(
                "source terrain section {} index count {} does not match ordinary count {}",
                section_index, source_section.index_count, section.index_count
            )));
        }
        selected.push(u32::try_from(section_index).map_err(|_| {
            GalError::invalid_argument("source terrain section ordinal exceeds u32")
        })?);
        cursor = section_end;
    }
    if cursor != range_end || selected.is_empty() {
        return Err(GalError::invalid_argument(format!(
            "source terrain range {}..{} does not cover whole ordinary mesh sections",
            index_offset, range_end
        )));
    }
    Ok(selected)
}

/// Byte offset of a source draw: the whole section, or a sorted run in it.
pub(crate) fn source_draw_index_offset(section_index_offset: u64, subrange: Option<(u32, u32)>) -> u64 {
    section_index_offset
        + subrange.map_or(0, |(first_index, _)| {
            u64::from(first_index) * std::mem::size_of::<u32>() as u64
        })
}

/// Sodium DYNAMIC-sort translucent sections are drawn in camera-sorted quad
/// order, which the ordinary batcher expresses as contiguous quad runs
/// inside one translucent section. Returns `(section, first_index, count)`
/// when the range is exactly such a run; the source mesh keeps the original
/// per-section index order, so the same run addresses it directly.
pub(crate) fn source_translucent_subrange_for_mesh_range(
    original_sections: &[WorldMeshSection],
    original_index_type: IndexType,
    index_offset: u64,
    index_count: u32,
) -> Option<(u32, u32, u32)> {
    if index_count == 0 || index_count % 6 != 0 {
        return None;
    }
    let stride = index_stride(original_index_type);
    let range_end = index_offset.checked_add(u64::from(index_count).checked_mul(stride)?)?;
    original_sections
        .iter()
        .enumerate()
        .find_map(|(section_index, section)| {
            let start = u64::from(section.index_offset);
            let end = start.checked_add(u64::from(section.index_count).checked_mul(stride)?)?;
            let first_index = (index_offset.checked_sub(start)?) / stride;
            (section.material_mode == WORLD_MATERIAL_MODE_TRANSLUCENT
                && index_offset >= start
                && range_end <= end
                && (index_offset - start) % stride == 0
                && first_index % 6 == 0)
                .then(|| {
                    Some((
                        u32::try_from(section_index).ok()?,
                        u32::try_from(first_index).ok()?,
                        index_count,
                    ))
                })
                .flatten()
        })
}

/// Derives the source-level terrain vertex semantics required by legacy pack
/// source from the existing copied indexed mesh. The present asset transport
/// has a deliberate, stable quad grammar: `[a,b,c, c,d,a]` per quad. Refusing
/// other index arrangements is safer than guessing an atlas sprite midpoint
/// or tangent from arbitrary triangles.
#[cfg(test)]
pub(crate) fn derive_source_terrain_mesh(mesh: &WorldMeshAsset) -> GalResult<SourceTerrainMesh> {
    derive_source_terrain_mesh_view(&mesh.source_terrain_view())
}

pub(crate) fn derive_source_terrain_mesh_view<V: TerrainSourceVertex>(
    mesh: &SourceMeshAssetView<'_, V>,
) -> GalResult<SourceTerrainMesh> {
    let indices = decoded_terrain_voxel_indices(&mesh.index_bytes, mesh.index_type)?;
    if indices.len() % 6 != 0 {
        return Err(GalError::invalid_argument(format!(
            "world mesh {} cannot derive source terrain semantics from non-quad index count {}",
            mesh.mesh_key,
            indices.len()
        )));
    }
    let mut vertices = Vec::with_capacity(indices.len() / 6 * 4);
    let mut expanded_indices = Vec::with_capacity(indices.len());
    for (quad_index, quad) in indices.chunks_exact(6).enumerate() {
        let [a, b, c, c_again, d, a_again] = quad else {
            unreachable!("chunks_exact(6) always yields six indices")
        };
        if c != c_again || a != a_again {
            return Err(GalError::invalid_argument(format!(
                "world mesh {} quad {} does not use the required [a,b,c,c,d,a] topology",
                mesh.mesh_key, quad_index
            )));
        }
        let source_indices = [*a, *b, *c, *d];
        let source = source_indices.map(|index| {
            mesh.vertices.get(index as usize).copied().ok_or_else(|| {
                GalError::invalid_argument(format!(
                    "world mesh {} quad {} references vertex {} outside {} vertices",
                    mesh.mesh_key,
                    quad_index,
                    index,
                    mesh.vertices.len()
                ))
            })
        });
        let [a, b, c, d] = source;
        let (a, b, c, d) = (a?, b?, c?, d?);
        let sprite_midpoint = source_sprite_midpoint(mesh.mesh_key, quad_index, [a, b, c, d])?;
        // Iris's terrain vertex writer (`XHFPTerrainVertex`) replaces every
        // vertex normal with the quad's diagonal face normal and derives one
        // tangent from triangle (0,1,2), retrying (2,3,0) when degenerate.
        let (quad_normal, tangent) =
            iris_terrain_quad_normal_and_tangent(mesh.mesh_key, quad_index, [a, b, c, d])?;
        let base = u32::try_from(vertices.len()).map_err(|_| {
            GalError::invalid_argument("source terrain vertex stream exceeds u32 index range")
        })?;
        for vertex in [a, b, c, d] {
            let normal = quad_normal;
            vertices.push(SourceTerrainVertex {
                position: vertex.position(),
                atlas_uv: vertex.shader_atlas_uv(),
                color_argb: vertex.color_argb(),
                normal_packed: pack_normal_i8(normal),
                light: vertex.light(),
                shader_block_id: vertex.shader_block_id(),
                shader_material_resolved: false,
                shader_material_type: vertex.shader_material_type(),
                mid_block_packed: vertex.mid_block_packed(),
                sprite_midpoint,
                tangent,
            });
        }
        expanded_indices.extend_from_slice(&[base, base + 1, base + 2, base + 2, base + 3, base]);
    }
    Ok(SourceTerrainMesh {
        vertices,
        indices: expanded_indices,
    })
}

/// Builds the owned source-mesh preparation artifact without allocating GPU
/// resources. The original section ordering/counts are preserved exactly;
/// only their byte offsets change because source terrain always indexes the
/// explicit `u32` stream. This is deliberately a strict conversion rather
/// than a best-effort fallback, so a future source route cannot accidentally
/// draw a mismatched subrange from a valid ordinary mesh asset.
#[cfg(test)]
pub(crate) fn prepare_source_terrain_mesh_asset(
    mesh: &WorldMeshAsset,
) -> GalResult<SourceTerrainMeshAsset> {
    prepare_source_terrain_mesh_asset_view_with_material_ids(&mesh.source_terrain_view(), None)
}

#[cfg(test)]
pub(crate) fn prepare_source_terrain_mesh_asset_with_material_ids(
    mesh: &WorldMeshAsset,
    material_ids: Option<&BTreeMap<i32, i32>>,
) -> GalResult<SourceTerrainMeshAsset> {
    prepare_source_terrain_mesh_asset_view_with_material_ids(
        &mesh.source_terrain_view(),
        material_ids,
    )
}

pub(crate) fn prepare_source_terrain_mesh_asset_view_with_material_ids<V: TerrainSourceVertex>(
    mesh: &SourceMeshAssetView<'_, V>,
    material_ids: Option<&BTreeMap<i32, i32>>,
) -> GalResult<SourceTerrainMeshAsset> {
    for (section_index, section) in mesh.sections.iter().enumerate() {
        if !matches!(
            section.material_mode,
            WORLD_MATERIAL_MODE_OPAQUE
                | WORLD_MATERIAL_MODE_CUTOUT
                | WORLD_MATERIAL_MODE_TRANSLUCENT
        ) {
            return Err(GalError::unsupported_feature(format!(
                "source terrain mesh {} section {} uses unsupported material mode {}",
                mesh.mesh_key, section_index, section.material_mode
            )));
        }
    }
    let mut source = derive_source_terrain_mesh_view(mesh)?;
    if let Some(material_ids) = material_ids {
        for (vertex_index, vertex) in source.vertices.iter_mut().enumerate() {
            let raw_state_id = vertex.shader_block_id;
            let material_id = material_ids.get(&raw_state_id).copied().ok_or_else(|| {
                GalError::unsupported_feature(format!(
                    "source terrain vertex {vertex_index} references raw block state {raw_state_id} absent from the active semantic state table"
                ))
            })?;
            vertex.shader_block_id = material_id;
            vertex.shader_material_resolved = true;
        }
    }
    // `shader_material_type` is a source-pack render lane, not a global
    // material classification. In particular, 1 is both a normal cutout
    // render type and still water while 2/3 identify flowing/overlay water.
    // Validate it against the copied section's stable semantic material and
    // texture identity before packing the shared source vertex ABI.
    validate_source_terrain_section_material_types(mesh, &source)?;
    let vertex_bytes = pack_source_terrain_vertices(&source)?;
    let mut index_bytes = Vec::with_capacity(
        source
            .indices
            .len()
            .checked_mul(std::mem::size_of::<u32>())
            .ok_or_else(|| {
                GalError::invalid_argument("source terrain index stream byte count overflow")
            })?,
    );
    for index in source.indices {
        index_bytes.extend_from_slice(&index.to_ne_bytes());
    }

    let source_index_stride = u64::try_from(std::mem::size_of::<u32>())
        .map_err(|_| GalError::invalid_argument("source terrain u32 index stride overflows"))?;
    let original_index_stride = index_stride(mesh.index_type);
    let source_index_count = u64::try_from(index_bytes.len())
        .ok()
        .and_then(|bytes| bytes.checked_div(source_index_stride))
        .ok_or_else(|| GalError::invalid_argument("source terrain index stream length overflow"))?;
    let mut sections = Vec::with_capacity(mesh.sections.len());
    for (section_index, section) in mesh.sections.iter().enumerate() {
        let original_offset = u64::from(section.index_offset);
        if original_offset % original_index_stride != 0 {
            return Err(GalError::invalid_argument(format!(
                "world mesh {} section {} index offset {} is not aligned to its {:?} index stride",
                mesh.mesh_key, section_index, section.index_offset, mesh.index_type
            )));
        }
        let first_index = original_offset / original_index_stride;
        let section_end = first_index
            .checked_add(u64::from(section.index_count))
            .ok_or_else(|| {
                GalError::invalid_argument("source terrain section index range overflows")
            })?;
        if section_end > source_index_count {
            return Err(GalError::invalid_argument(format!(
                "world mesh {} section {} index range {}..{} exceeds {} source indices",
                mesh.mesh_key, section_index, first_index, section_end, source_index_count
            )));
        }
        sections.push(SourceTerrainMeshSection {
            material_id: section.material_id,
            texture_id: section.texture_id,
            material_mode: section.material_mode,
            cull_policy: section.cull_policy,
            winding: section.winding,
            index_offset: first_index
                .checked_mul(source_index_stride)
                .ok_or_else(|| {
                    GalError::invalid_argument("source terrain section byte offset overflows")
                })?,
            index_count: section.index_count,
        });
    }

    let prepared = SourceTerrainMeshAsset {
        mesh_key: mesh.mesh_key,
        mesh_generation: mesh.mesh_generation,
        vertex_bytes,
        index_bytes,
        sections,
    };
    Ok(prepared)
}

/// Derives the source entity vertex stream from the copied indexed mesh using
/// the same canonical quad topology as other baked-model assets. Unlike the
/// terrain conversion, this intentionally consumes `uv` rather than
/// `shader_atlas_uv`: entity programs sample their own Rust-owned material
/// texture and must never acquire Minecraft's terrain atlas semantics.
pub(crate) fn derive_source_entity_mesh_view<V: EntitySourceVertex>(
    mesh: &SourceMeshAssetView<'_, V>,
) -> GalResult<SourceEntityMesh> {
    let indices = decoded_terrain_voxel_indices(&mesh.index_bytes, mesh.index_type)?;
    if indices.len() % 6 != 0 {
        return Err(GalError::invalid_argument(format!(
            "world mesh {} cannot derive source entity semantics from non-quad index count {}",
            mesh.mesh_key,
            indices.len()
        )));
    }
    let mut vertices = Vec::with_capacity(indices.len() / 6 * 4);
    let mut expanded_indices = Vec::with_capacity(indices.len());
    for (quad_index, quad) in indices.chunks_exact(6).enumerate() {
        let [a, b, c, c_again, d, a_again] = quad else {
            unreachable!("chunks_exact(6) always yields six indices")
        };
        if c != c_again || a != a_again {
            return Err(GalError::invalid_argument(format!(
                "world mesh {} quad {} does not use the required [a,b,c,c,d,a] topology",
                mesh.mesh_key, quad_index
            )));
        }
        let source_indices = [*a, *b, *c, *d];
        let source = source_indices.map(|index| {
            mesh.vertices.get(index as usize).copied().ok_or_else(|| {
                GalError::invalid_argument(format!(
                    "world mesh {} quad {} references vertex {} outside {} vertices",
                    mesh.mesh_key,
                    quad_index,
                    index,
                    mesh.vertices.len()
                ))
            })
        });
        let [a, b, c, d] = source;
        let (a, b, c, d) = (a?, b?, c?, d?);
        let texture_midpoint =
            source_local_texture_midpoint(mesh.mesh_key, quad_index, [a, b, c, d])?;
        if ![a, b, c, d]
            .iter()
            .flat_map(|vertex| vertex.position())
            .all(f32::is_finite)
        {
            return Err(GalError::invalid_argument(format!(
                "world mesh {} quad {} has non-finite source position",
                mesh.mesh_key, quad_index
            )));
        }
        // Iris renders entities and hands inside the level with its extended
        // vertex format, whose BufferBuilder replaces every quad's normals
        // with the diagonal face normal (`NormalHelper.computeFaceNormal`)
        // and derives the tangent from triangle (0,1,2) against it. The
        // source vertex stage applies the instance's normal matrix and the
        // sign of its determinant, reproducing that normal computed on
        // pose-transformed positions.
        let positions = [a, b, c, d].map(|vertex| vertex.position());
        // The producer may store a quad with reversed winding (see the
        // instance `winding`); Iris saw the model's own outward order, so
        // orient the face normal to the authored normal's side.
        let authored = [a, b, c, d]
            .iter()
            .find_map(|vertex| try_normalize3(unpack_normal_i8(vertex.normal_packed())));
        let face_normal = try_normalize3(cross3(
            subtract3(positions[2], positions[0]),
            subtract3(positions[3], positions[1]),
        ))
        .map(|normal| match authored {
            Some(authored) if dot3(normal, authored) < 0.0 => scale3(normal, -1.0),
            _ => normal,
        });
        // Entity/item geometry may author a triangle as a quad (collapsed
        // first triangle) or carry faces with no area at all. The latter
        // rasterizes nothing, as in OpenGL, so its basis is unobservable;
        // rejecting it would drop the whole shader frame.
        let iris_basis = face_normal.map(|normal| {
            let uv = [a.uv(), b.uv(), c.uv()];
            let tangent = iris_triangle_tangent([positions[0], positions[1], positions[2]], uv, normal)
                .unwrap_or_else(|| {
                    let tangent = orthogonal_tangent(normal, mesh.mesh_key, quad_index)
                        .unwrap_or([1.0, 0.0, 0.0]);
                    [tangent[0], tangent[1], tangent[2], 1.0]
                });
            (tangent, normal)
        });
        let (tangent, quad_normal) = if let Some(basis) = iris_basis {
            basis
        } else {
            match source_entity_quad_tangent(mesh.mesh_key, quad_index, [a, b, c]).and_then(
                |tangent| {
                    source_quad_normal(
                        [a, b, c],
                        subtract3(b.position(), a.position()),
                        subtract3(c.position(), a.position()),
                        mesh.mesh_key,
                        quad_index,
                    )
                    .map(|normal| (tangent, normal))
                },
            ) {
                Ok(basis) => basis,
                Err(_) => match source_entity_quad_tangent(mesh.mesh_key, quad_index, [c, d, a])
                    .and_then(|tangent| {
                        source_quad_normal(
                            [c, d, a],
                            subtract3(d.position(), c.position()),
                            subtract3(a.position(), c.position()),
                            mesh.mesh_key,
                            quad_index,
                        )
                        .map(|normal| (tangent, normal))
                    }) {
                    Ok(basis) => basis,
                    Err(_) => ([1.0, 0.0, 0.0, 1.0], [0.0, 0.0, 1.0]),
                },
            }
        };
        let base = u32::try_from(vertices.len()).map_err(|_| {
            GalError::invalid_argument("source entity vertex stream exceeds u32 index range")
        })?;
        for vertex in [a, b, c, d] {
            let normal = if face_normal.is_some() {
                quad_normal
            } else {
                normalize3(
                    unpack_normal_i8(vertex.normal_packed()),
                    "normal",
                    mesh.mesh_key,
                    quad_index,
                )
                .unwrap_or(quad_normal)
            };
            vertices.push(SourceEntityVertex {
                position: vertex.position(),
                local_uv: vertex.uv(),
                color_argb: vertex.color_argb(),
                normal_packed: pack_normal_i8(normal),
                light: vertex.light(),
                texture_midpoint,
                tangent,
            });
        }
        expanded_indices.extend_from_slice(&[base, base + 1, base + 2, base + 2, base + 3, base]);
    }
    Ok(SourceEntityMesh {
        vertices,
        indices: expanded_indices,
    })
}

/// Builds an immutable, generation-bound entity source asset. This is not a
/// terrain compatibility route: it rejects empty/noncanonical entity identity
/// and accepts source-owned opaque, cutout, explicit alpha-blended, and
/// first-person optical stencil sections that bind one local material texture.
#[cfg(test)]
pub(crate) fn prepare_source_entity_mesh_asset(
    mesh: &WorldMeshAsset,
) -> GalResult<SourceEntityMeshAsset> {
    prepare_source_entity_mesh_asset_view(&mesh.source_entity_view())
}

pub(crate) fn prepare_source_entity_mesh_asset_view<V: EntitySourceVertex>(
    mesh: &SourceMeshAssetView<'_, V>,
) -> GalResult<SourceEntityMeshAsset> {
    if canonical_resource_location(&mesh.entity_identity)
        .ok()
        .as_deref()
        != Some(mesh.entity_identity)
    {
        return Err(GalError::unsupported_feature(format!(
            "world mesh {} has no canonical entity identity for a source entity pass",
            mesh.mesh_key
        )));
    }
    for (section_index, section) in mesh.sections.iter().enumerate() {
        if !matches!(
            section.material_mode,
            WORLD_MATERIAL_MODE_OPAQUE
                | WORLD_MATERIAL_MODE_CUTOUT
                | WORLD_MATERIAL_MODE_TRANSLUCENT
                | WORLD_MATERIAL_MODE_TRANSLUCENT_CUTOUT
                | WORLD_MATERIAL_MODE_GLINT
                | WORLD_MATERIAL_MODE_OPTICAL_STENCIL_WRITE
                | WORLD_MATERIAL_MODE_OPTICAL_STENCIL_TEST
        ) {
            return Err(GalError::unsupported_feature(format!(
                "source entity mesh {} section {} uses unsupported material mode {}",
                mesh.mesh_key, section_index, section.material_mode
            )));
        }
    }
    let source = derive_source_entity_mesh_view(mesh)?;
    let vertex_bytes = pack_source_entity_vertices(&source.vertices)?;
    let mut index_bytes = Vec::with_capacity(
        source
            .indices
            .len()
            .checked_mul(std::mem::size_of::<u32>())
            .ok_or_else(|| {
                GalError::invalid_argument("source entity index stream byte count overflow")
            })?,
    );
    for index in source.indices {
        index_bytes.extend_from_slice(&index.to_ne_bytes());
    }
    let source_index_stride = std::mem::size_of::<u32>() as u64;
    let original_index_stride = index_stride(mesh.index_type);
    let source_index_count = (index_bytes.len() / std::mem::size_of::<u32>()) as u64;
    let mut sections = Vec::with_capacity(mesh.sections.len());
    for (section_index, section) in mesh.sections.iter().enumerate() {
        let original_offset = u64::from(section.index_offset);
        if original_offset % original_index_stride != 0 {
            return Err(GalError::invalid_argument(format!(
                "world mesh {} section {} index offset {} is not aligned to its {:?} index stride",
                mesh.mesh_key, section_index, section.index_offset, mesh.index_type
            )));
        }
        let first_index = original_offset / original_index_stride;
        let end_index = first_index
            .checked_add(u64::from(section.index_count))
            .ok_or_else(|| {
                GalError::invalid_argument("source entity section index range overflows")
            })?;
        if end_index > source_index_count {
            return Err(GalError::invalid_argument(format!(
                "world mesh {} section {} index range {}..{} exceeds {} source indices",
                mesh.mesh_key, section_index, first_index, end_index, source_index_count
            )));
        }
        sections.push(SourceTerrainMeshSection {
            material_id: section.material_id,
            texture_id: section.texture_id,
            material_mode: section.material_mode,
            cull_policy: section.cull_policy,
            winding: section.winding,
            index_offset: first_index
                .checked_mul(source_index_stride)
                .ok_or_else(|| {
                    GalError::invalid_argument("source entity section byte offset overflows")
                })?,
            index_count: section.index_count,
        });
    }
    let prepared = SourceEntityMeshAsset {
        mesh_key: mesh.mesh_key,
        mesh_generation: mesh.mesh_generation,
        entity_identity: mesh.entity_identity.to_owned(),
        vertex_bytes,
        index_bytes,
        sections,
    };
    prepared.validate()?;
    Ok(prepared)
}

pub(crate) fn pack_source_entity_vertices(vertices: &[SourceEntityVertex]) -> GalResult<Vec<u8>> {
    let byte_count = vertices
        .len()
        .checked_mul(TERRAIN_SOURCE_VERTEX_BYTES)
        .ok_or_else(|| {
            GalError::invalid_argument("source entity vertex stream byte count overflow")
        })?;
    let mut out = Vec::with_capacity(byte_count);
    for (index, vertex) in vertices.iter().enumerate() {
        if !vertex.position.into_iter().all(f32::is_finite)
            || !vertex.local_uv.into_iter().all(f32::is_finite)
            || !vertex.texture_midpoint.into_iter().all(f32::is_finite)
            || !vertex.tangent.into_iter().all(f32::is_finite)
        {
            return Err(GalError::invalid_argument(format!(
                "source entity vertex {index} contains non-finite semantic data"
            )));
        }
        let color = argb_to_rgba(vertex.color_argb);
        let normal = unpack_normal_i8(vertex.normal_packed);
        let [block_light, sky_light] = source_lightmap_coordinates(vertex.light);
        for component in [
            vertex.position[0],
            vertex.position[1],
            vertex.position[2],
            1.0,
        ] {
            push_f32(&mut out, component);
        }
        for component in color {
            push_f32(&mut out, component);
        }
        for component in [normal[0], normal[1], normal[2], 0.0] {
            push_f32(&mut out, component);
        }
        for component in [
            vertex.local_uv[0],
            vertex.local_uv[1],
            block_light,
            sky_light,
        ] {
            push_f32(&mut out, component);
        }
        // Entity identity is a Rust-resolved draw semantic, not a per-vertex
        // Minecraft terrain material lane.
        for component in [0.0, 0.0, 0.0, 1.0] {
            push_f32(&mut out, component);
        }
        for component in [
            vertex.texture_midpoint[0],
            vertex.texture_midpoint[1],
            0.0,
            1.0,
        ] {
            push_f32(&mut out, component);
        }
        for component in vertex.tangent {
            push_f32(&mut out, component);
        }
        for component in [0.0, 0.0, 0.0, 0.0] {
            push_f32(&mut out, component);
        }
    }
    debug_assert_eq!(out.len(), byte_count);
    Ok(out)
}

pub(crate) fn source_entity_light_variant_key(mesh_key: u64, packed_light: u32) -> u64 {
    let mut hash = mesh_key ^ 0x9e37_79b9_7f4a_7c15;
    hash = hash.wrapping_mul(0x1000_0000_01b3);
    hash ^= u64::from(packed_light);
    hash | 1
}

pub(crate) fn override_source_entity_vertex_light(
    mesh: &mut SourceEntityMeshAsset,
    packed_light: u32,
) -> GalResult<()> {
    const LIGHT_OFFSET_IN_VERTEX: usize = 56;
    const LIGHT_LANE_BYTES: usize = 8;
    if mesh.vertex_bytes.len() % TERRAIN_SOURCE_VERTEX_BYTES != 0 {
        return Err(GalError::invalid_argument(
            "source entity vertex payload is not aligned to the fixed source ABI",
        ));
    }
    let [block_light, sky_light] = source_lightmap_coordinates(packed_light);
    for vertex in mesh
        .vertex_bytes
        .chunks_exact_mut(TERRAIN_SOURCE_VERTEX_BYTES)
    {
        let light = &mut vertex[LIGHT_OFFSET_IN_VERTEX..LIGHT_OFFSET_IN_VERTEX + LIGHT_LANE_BYTES];
        light[..4].copy_from_slice(&block_light.to_ne_bytes());
        light[4..].copy_from_slice(&sky_light.to_ne_bytes());
    }
    Ok(())
}

/// Packs the fixed source-lowering vertex record declared in
/// `VERTEX_SEMANTIC_PREAMBLE`. It owns a copy of ordinary Rust semantic mesh
/// data and deliberately rejects meshes that did not carry the source pack's
/// block/material identity. The later Rust resource-set and pipeline slice
/// consumes this exact stream without reinterpreting it as Java/Iris state.
pub(crate) fn pack_source_terrain_vertices(mesh: &SourceTerrainMesh) -> GalResult<Vec<u8>> {
    let byte_count = mesh
        .vertices
        .len()
        .checked_mul(TERRAIN_SOURCE_VERTEX_BYTES)
        .ok_or_else(|| {
            GalError::invalid_argument("source terrain vertex stream byte count overflow")
        })?;
    let mut out = Vec::with_capacity(byte_count);
    for (index, vertex) in mesh.vertices.iter().enumerate() {
        if !vertex.position.into_iter().all(f32::is_finite)
            || !vertex.atlas_uv.into_iter().all(f32::is_finite)
            || !vertex.sprite_midpoint.into_iter().all(f32::is_finite)
            || !vertex.tangent.into_iter().all(f32::is_finite)
        {
            return Err(GalError::invalid_argument(format!(
                "source terrain vertex {index} contains non-finite semantic data"
            )));
        }
        if vertex.shader_block_id < 0 && !vertex.shader_material_resolved {
            return Err(GalError::unsupported_feature(format!(
                "source terrain vertex {index} lacks a semantic shader block identity"
            )));
        }
        // The semantic meaning of this source lane is deliberately checked
        // against the owning mesh section before this generic packer runs.
        // Keep the ABI-level range here so malformed records cannot bypass
        // validation through a direct preparation helper.
        if !(0..=3).contains(&vertex.shader_material_type) {
            return Err(GalError::unsupported_feature(format!(
                "source terrain vertex {index} has unsupported shader material type {} (raw_block_state={}, atlas_uv={:?})",
                vertex.shader_material_type, vertex.shader_block_id, vertex.atlas_uv
            )));
        }

        let color = argb_to_rgba(vertex.color_argb);
        let normal = unpack_normal_i8(vertex.normal_packed);
        let [block_light, sky_light] = source_lightmap_coordinates(vertex.light);

        // position
        push_f32(&mut out, vertex.position[0]);
        push_f32(&mut out, vertex.position[1]);
        push_f32(&mut out, vertex.position[2]);
        push_f32(&mut out, 1.0);
        // color
        for component in color {
            push_f32(&mut out, component);
        }
        // normal_light: the lowered source currently consumes xyz as gl_Normal.
        push_f32(&mut out, normal[0]);
        push_f32(&mut out, normal[1]);
        push_f32(&mut out, normal[2]);
        push_f32(&mut out, 0.0);
        // Preserve the vanilla UV2 values before the owned legacy texture
        // matrix derives the shader-pack lightmap coordinate.
        push_f32(&mut out, vertex.atlas_uv[0]);
        push_f32(&mut out, vertex.atlas_uv[1]);
        push_f32(&mut out, block_light);
        push_f32(&mut out, sky_light);
        // Complementary's terrain source consumes x as the block material ID
        // and y as the render-type flag.
        push_f32(&mut out, vertex.shader_block_id as f32);
        push_f32(&mut out, vertex.shader_material_type as f32);
        push_f32(&mut out, 0.0);
        push_f32(&mut out, 1.0);
        // mc_midTexCoord is a vec4 in the selected source. The semantic
        // midpoint is atlas-space xy; zw follow the fixed-function defaults.
        push_f32(&mut out, vertex.sprite_midpoint[0]);
        push_f32(&mut out, vertex.sprite_midpoint[1]);
        push_f32(&mut out, 0.0);
        push_f32(&mut out, 1.0);
        // at_tangent is already normalized and carries handedness in w.
        for component in vertex.tangent {
            push_f32(&mut out, component);
        }
        // Iris/Sodium expose at_midBlock as four unnormalized signed bytes:
        // xyz are the source relative block-center offsets and w is block
        // emission. Preserve each semantic byte as an exact numeric lane.
        for shift in [0, 8, 16, 24] {
            push_f32(
                &mut out,
                ((vertex.mid_block_packed >> shift) as u8 as i8) as f32,
            );
        }
    }
    debug_assert_eq!(out.len(), byte_count);
    Ok(out)
}

/// Verifies each source render-type lane against the semantic section that
/// owns its primitive range. The source mesh preserves ordinary index order,
/// so original index ordinal ranges map one-to-one to the expanded source
/// stream even when the original transport used u16 indices.
pub(crate) fn validate_source_terrain_section_material_types<V>(
    mesh: &SourceMeshAssetView<'_, V>,
    source: &SourceTerrainMesh,
) -> GalResult<()> {
    let source_index_count = u64::try_from(source.indices.len()).map_err(|_| {
        GalError::invalid_argument(
            "source terrain index stream exceeds u64 while validating material types",
        )
    })?;
    let original_index_stride = index_stride(mesh.index_type);
    for (section_index, section) in mesh.sections.iter().enumerate() {
        let section_offset = u64::from(section.index_offset);
        if section_offset % original_index_stride != 0 {
            return Err(GalError::invalid_argument(format!(
                "source terrain mesh {} section {} index offset {} is not aligned to its {:?} index stride",
                mesh.mesh_key, section_index, section.index_offset, mesh.index_type
            )));
        }
        let first_index = section_offset / original_index_stride;
        let end_index = first_index
            .checked_add(u64::from(section.index_count))
            .ok_or_else(|| {
                GalError::invalid_argument("source terrain section material-type range overflows")
            })?;
        if end_index > source_index_count {
            return Err(GalError::invalid_argument(format!(
                "source terrain mesh {} section {} material-type range {}..{} exceeds {} source indices",
                mesh.mesh_key, section_index, first_index, end_index, source_index_count
            )));
        }
        let first_index = usize::try_from(first_index).map_err(|_| {
            GalError::invalid_argument("source terrain section material-type start exceeds usize")
        })?;
        let end_index = usize::try_from(end_index).map_err(|_| {
            GalError::invalid_argument("source terrain section material-type end exceeds usize")
        })?;
        for &source_vertex_index in &source.indices[first_index..end_index] {
            let vertex = source.vertices.get(source_vertex_index as usize).ok_or_else(|| {
                GalError::invalid_argument(format!(
                    "source terrain mesh {} section {} references source vertex {} outside {} vertices",
                    mesh.mesh_key,
                    section_index,
                    source_vertex_index,
                    source.vertices.len()
                ))
            })?;
            if source_section_accepts_shader_material_type(section, vertex.shader_material_type) {
                continue;
            }
            return Err(GalError::unsupported_feature(format!(
                "source terrain mesh {} section {} rejects shader material type {} for material {} texture {} mode {}",
                mesh.mesh_key,
                section_index,
                vertex.shader_material_type,
                section.material_id,
                section.texture_id,
                section.material_mode
            )));
        }
    }
    Ok(())
}

pub(crate) fn source_section_accepts_shader_material_type(
    section: &WorldMeshSection,
    shader_material_type: i32,
) -> bool {
    match section.material_mode {
        // Sodium's source lane is a render-type bit, not the mesh layer. A
        // copied cutout section can validly carry either normal terrain lane
        // while its semantic section identity still selects the cutout pass.
        WORLD_MATERIAL_MODE_OPAQUE | WORLD_MATERIAL_MODE_CUTOUT => {
            matches!(shader_material_type, 0 | 1)
        }
        WORLD_MATERIAL_MODE_TRANSLUCENT
            if section.material_id == WORLD_MATERIAL_ID_WATER_TRANSLUCENT =>
        {
            // Resource-pack water textures retain the water material identity
            // while their copied texture IDs are not limited to the three
            // built-in sentinel IDs. The source lane still carries the same
            // still/flow/overlay values and must be admitted by material
            // identity, not by a hard-coded texture registry.
            matches!(shader_material_type, 1 | 2 | 3)
        }
        // Sodium's ordinary translucent terrain primitives retain the normal
        // terrain render-type lane. They still select the distinct
        // source-derived translucent pass through the semantic section mode;
        // treating lane zero as water-only would reject valid glass and other
        // non-fluid terrain before that pass can be prepared.
        WORLD_MATERIAL_MODE_TRANSLUCENT
            if section.material_id == WORLD_MATERIAL_ID_TRANSLUCENT_TEXTURED
                && section.texture_id == WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS =>
        {
            shader_material_type == 0
        }
        _ => false,
    }
}

/// Packs the fixed per-instance table used by the source lowerer's
/// `gl_InstanceIndex` path. The table carries copied semantic model transforms
/// and per-instance color modulation, keeping camera/view state in the
/// separately owned source uniform block.
pub(crate) fn pack_source_terrain_instances(instances: &[SourceTerrainInstance]) -> GalResult<Vec<u8>> {
    let byte_count = instances
        .len()
        .checked_mul(TERRAIN_SOURCE_INSTANCE_BYTES)
        .ok_or_else(|| {
            GalError::invalid_argument("source terrain instance stream byte count overflow")
        })?;
    let mut out = Vec::with_capacity(byte_count);
    for (index, (transform, color_argb)) in instances.iter().enumerate() {
        if transform.iter().any(|component| !component.is_finite()) {
            return Err(GalError::invalid_argument(format!(
                "source terrain instance {index} has a non-finite model transform"
            )));
        }
        for component in transform {
            push_f32(&mut out, *component);
        }
        for component in argb_to_rgba(*color_argb) {
            push_f32(&mut out, component);
        }
    }
    debug_assert_eq!(out.len(), byte_count);
    Ok(out)
}

/// Compatibility helper for transform-only callers. Runtime source terrain
/// preparation uses [`pack_source_terrain_instances`] so it cannot lose the
/// per-instance color semantic; this helper gives isolated transform tests a
/// canonical opaque-white modulation record.
pub(crate) fn pack_source_terrain_instance_transforms(
    transforms: &[[f32; 16]],
) -> GalResult<Vec<u8>> {
    let instances = transforms
        .iter()
        .copied()
        .map(|transform| (transform, u32::MAX))
        .collect::<Vec<_>>();
    pack_source_terrain_instances(&instances)
}

/// Returns the original Minecraft UV2 integer coordinate pair represented by
/// the compact copied light value. Unlike the minimal internal terrain
/// program, source-derived shaders must not treat zero as a synthetic fully
/// lit value: their declared texture matrix owns that interpretation.
pub(crate) fn source_lightmap_coordinates(packed_light: u32) -> [f32; 2] {
    [
        (packed_light & 0xff) as f32,
        ((packed_light >> 16) & 0xff) as f32,
    ]
}

pub(crate) fn source_sprite_midpoint<V: TerrainSourceVertex>(
    mesh_key: u64,
    quad_index: usize,
    vertices: [V; 4],
) -> GalResult<[f32; 2]> {
    let mut midpoint = [0.0f32; 2];
    for vertex in vertices {
        for (axis, value) in vertex.shader_atlas_uv().into_iter().enumerate() {
            if !value.is_finite() {
                return Err(GalError::invalid_argument(format!(
                    "world mesh {mesh_key} quad {quad_index} has non-finite atlas UV"
                )));
            }
            midpoint[axis] += value * 0.25;
        }
    }
    Ok(midpoint)
}

pub(crate) fn source_local_texture_midpoint<V: EntitySourceVertex>(
    mesh_key: u64,
    quad_index: usize,
    vertices: [V; 4],
) -> GalResult<[f32; 2]> {
    let mut midpoint = [0.0f32; 2];
    for vertex in vertices {
        for (axis, value) in vertex.uv().into_iter().enumerate() {
            if !value.is_finite() {
                return Err(GalError::invalid_argument(format!(
                    "world mesh {mesh_key} quad {quad_index} has non-finite local texture UV"
                )));
            }
            midpoint[axis] += value * 0.25;
        }
    }
    Ok(midpoint)
}

pub(crate) fn source_quad_tangent<V: TerrainSourceVertex>(
    mesh_key: u64,
    quad_index: usize,
    vertices: [V; 3],
) -> GalResult<[f32; 4]> {
    let edge_ab = subtract3(vertices[1].position(), vertices[0].position());
    let edge_ac = subtract3(vertices[2].position(), vertices[0].position());
    if !edge_ab.into_iter().chain(edge_ac).all(f32::is_finite) {
        return Err(GalError::invalid_argument(format!(
            "world mesh {mesh_key} quad {quad_index} has non-finite source position"
        )));
    }
    let atlas_uv_ab = subtract2(vertices[1].shader_atlas_uv(), vertices[0].shader_atlas_uv());
    let atlas_uv_ac = subtract2(vertices[2].shader_atlas_uv(), vertices[0].shader_atlas_uv());
    let normal = source_quad_normal(vertices, edge_ab, edge_ac, mesh_key, quad_index)?;
    if let Some(tangent) =
        tangent_from_uv_basis(edge_ab, edge_ac, atlas_uv_ab, atlas_uv_ac, normal)?
    {
        return Ok(tangent);
    }

    let local_uv_ab = subtract2(vertices[1].uv(), vertices[0].uv());
    let local_uv_ac = subtract2(vertices[2].uv(), vertices[0].uv());
    if let Some(tangent) =
        tangent_from_uv_basis(edge_ab, edge_ac, local_uv_ab, local_uv_ac, normal)?
    {
        return Ok(tangent);
    }

    // A copied terrain quad can legitimately collapse both its atlas and
    // local UVs while retaining valid world geometry. Source shaders still
    // require an orthogonal tangent attribute, so use a stable basis rather
    // than rejecting the entire mesh.
    let tangent = orthogonal_tangent(normal, mesh_key, quad_index)?;
    Ok([tangent[0], tangent[1], tangent[2], 1.0])
}

/// Iris `NormalHelper.computeFaceNormalManual` + `computeTangent` for one
/// terrain quad, preserving its float conventions (`f = 1` for a zero UV
/// determinant, `rsqrt(0) = 1`, handedness from bitangent vs tangent x normal).
pub(crate) fn iris_terrain_quad_normal_and_tangent<V: TerrainSourceVertex>(
    mesh_key: u64,
    quad_index: usize,
    vertices: [V; 4],
) -> GalResult<([f32; 3], [f32; 4])> {
    let p = vertices.map(|vertex| vertex.position());
    if !p.iter().flatten().all(|value| value.is_finite()) {
        return Err(GalError::invalid_argument(format!(
            "world mesh {mesh_key} quad {quad_index} has non-finite source position"
        )));
    }
    let normal = match normalize3(
        cross3(subtract3(p[2], p[0]), subtract3(p[3], p[1])),
        "face normal",
        mesh_key,
        quad_index,
    ) {
        Ok(normal) => normal,
        // A collapsed quad has no face normal; keep its authored one.
        Err(_) => source_quad_normal(
            [vertices[0], vertices[1], vertices[2]],
            subtract3(p[1], p[0]),
            subtract3(p[2], p[0]),
            mesh_key,
            quad_index,
        )?,
    };
    let uv = vertices.map(|vertex| vertex.shader_atlas_uv());
    let tangent = iris_triangle_tangent([p[0], p[1], p[2]], [uv[0], uv[1], uv[2]], normal)
        .or_else(|| iris_triangle_tangent([p[2], p[3], p[0]], [uv[2], uv[3], uv[0]], normal))
        .unwrap_or_else(|| {
            let tangent = orthogonal_tangent(normal, mesh_key, quad_index)
                .unwrap_or([1.0, 0.0, 0.0]);
            [tangent[0], tangent[1], tangent[2], 1.0]
        });
    Ok((normal, tangent))
}

pub(crate) fn iris_triangle_tangent(p: [[f32; 3]; 3], uv: [[f32; 2]; 3], normal: [f32; 3]) -> Option<[f32; 4]> {
    let rsqrt = |value: f32| if value == 0.0 { 1.0 } else { 1.0 / value.sqrt() };
    let edge1 = subtract3(p[1], p[0]);
    let edge2 = subtract3(p[2], p[0]);
    let (du1, dv1) = (uv[1][0] - uv[0][0], uv[1][1] - uv[0][1]);
    let (du2, dv2) = (uv[2][0] - uv[0][0], uv[2][1] - uv[0][1]);
    let denom = du1 * dv2 - du2 * dv1;
    let f = if denom == 0.0 { 1.0 } else { 1.0 / denom };
    let mut tangent = [0.0f32; 3];
    let mut bitangent = [0.0f32; 3];
    for axis in 0..3 {
        tangent[axis] = f * (dv2 * edge1[axis] - dv1 * edge2[axis]);
        bitangent[axis] = f * (-du2 * edge1[axis] + du1 * edge2[axis]);
    }
    let t = rsqrt(tangent.iter().map(|v| v * v).sum());
    tangent = tangent.map(|v| v * t);
    if tangent == [0.0, 0.0, 0.0] {
        return None;
    }
    let b = rsqrt(bitangent.iter().map(|v| v * v).sum());
    bitangent = bitangent.map(|v| v * b);
    let predicted = [
        tangent[1] * normal[2] - tangent[2] * normal[1],
        tangent[2] * normal[0] - tangent[0] * normal[2],
        tangent[0] * normal[1] - tangent[1] * normal[0],
    ];
    let dot: f32 = (0..3).map(|axis| bitangent[axis] * predicted[axis]).sum();
    Some([tangent[0], tangent[1], tangent[2], if dot < 0.0 { -1.0 } else { 1.0 }])
}

pub(crate) fn source_entity_quad_tangent<V: EntitySourceVertex>(
    mesh_key: u64,
    quad_index: usize,
    vertices: [V; 3],
) -> GalResult<[f32; 4]> {
    let edge_ab = subtract3(vertices[1].position(), vertices[0].position());
    let edge_ac = subtract3(vertices[2].position(), vertices[0].position());
    if !edge_ab.into_iter().chain(edge_ac).all(f32::is_finite) {
        return Err(GalError::invalid_argument(format!(
            "world mesh {mesh_key} quad {quad_index} has non-finite source position"
        )));
    }
    let local_uv_ab = subtract2(vertices[1].uv(), vertices[0].uv());
    let local_uv_ac = subtract2(vertices[2].uv(), vertices[0].uv());
    let normal = source_quad_normal(vertices, edge_ab, edge_ac, mesh_key, quad_index)?;
    if let Some(tangent) =
        tangent_from_uv_basis(edge_ab, edge_ac, local_uv_ab, local_uv_ac, normal)?
    {
        return Ok(tangent);
    }
    let tangent = orthogonal_tangent(normal, mesh_key, quad_index)?;
    Ok([tangent[0], tangent[1], tangent[2], 1.0])
}

/// Baked terrain vertices carry the face normal independently from their
/// positions. A copied quad may therefore have a collapsed first triangle
/// (or a zero normal in its first lane) while another vertex still carries
/// the authoritative baked face normal. Select that semantic normal before
/// falling back to geometric reconstruction; only reject when neither exists.
pub(crate) fn source_quad_normal<V: NormalSourceVertex>(
    vertices: [V; 3],
    edge_ab: [f32; 3],
    edge_ac: [f32; 3],
    mesh_key: u64,
    quad_index: usize,
) -> GalResult<[f32; 3]> {
    for vertex in vertices {
        if let Ok(normal) = normalize3(
            unpack_normal_i8(vertex.normal_packed()),
            "normal",
            mesh_key,
            quad_index,
        ) {
            return Ok(normal);
        }
    }
    normalize3(
        cross3(edge_ab, edge_ac),
        "geometric normal",
        mesh_key,
        quad_index,
    )
}

pub(crate) fn tangent_from_uv_basis(
    edge_ab: [f32; 3],
    edge_ac: [f32; 3],
    uv_ab: [f32; 2],
    uv_ac: [f32; 2],
    normal: [f32; 3],
) -> GalResult<Option<[f32; 4]>> {
    let determinant = uv_ab[0] * uv_ac[1] - uv_ac[0] * uv_ab[1];
    if !determinant.is_finite() || determinant.abs() <= f32::EPSILON {
        return Ok(None);
    }
    let inverse = determinant.recip();
    let Some(tangent) = try_normalize3(subtract3(
        scale3(edge_ab, uv_ac[1] * inverse),
        scale3(edge_ac, uv_ab[1] * inverse),
    )) else {
        return Ok(None);
    };
    let Some(bitangent) = try_normalize3(subtract3(
        scale3(edge_ac, uv_ab[0] * inverse),
        scale3(edge_ab, uv_ac[0] * inverse),
    )) else {
        return Ok(None);
    };
    let handedness = if dot3(cross3(normal, tangent), bitangent) < 0.0 {
        -1.0
    } else {
        1.0
    };
    Ok(Some([tangent[0], tangent[1], tangent[2], handedness]))
}

pub(crate) fn orthogonal_tangent(normal: [f32; 3], mesh_key: u64, quad_index: usize) -> GalResult<[f32; 3]> {
    let reference = if normal[2].abs() < 0.9 {
        [0.0, 0.0, 1.0]
    } else {
        [0.0, 1.0, 0.0]
    };
    normalize3(
        cross3(reference, normal),
        "fallback tangent",
        mesh_key,
        quad_index,
    )
}

/// Scale-independent: sub-millimetre first-person item faces keep their
/// direction; only an exactly collapsed or non-finite vector has none.
pub(crate) fn try_normalize3(value: [f32; 3]) -> Option<[f32; 3]> {
    let largest = value.iter().fold(0.0f32, |largest, component| largest.max(component.abs()));
    if !largest.is_finite() || largest == 0.0 {
        return None;
    }
    let scaled = scale3(value, largest.recip());
    Some(scale3(scaled, dot3(scaled, scaled).sqrt().recip()))
}

pub(crate) fn subtract2(left: [f32; 2], right: [f32; 2]) -> [f32; 2] {
    [left[0] - right[0], left[1] - right[1]]
}

pub(crate) fn subtract3(left: [f32; 3], right: [f32; 3]) -> [f32; 3] {
    [left[0] - right[0], left[1] - right[1], left[2] - right[2]]
}

pub(crate) fn scale3(value: [f32; 3], factor: f32) -> [f32; 3] {
    [value[0] * factor, value[1] * factor, value[2] * factor]
}

pub(crate) fn cross3(left: [f32; 3], right: [f32; 3]) -> [f32; 3] {
    [
        left[1] * right[2] - left[2] * right[1],
        left[2] * right[0] - left[0] * right[2],
        left[0] * right[1] - left[1] * right[0],
    ]
}

pub(crate) fn dot3(left: [f32; 3], right: [f32; 3]) -> f32 {
    left[0] * right[0] + left[1] * right[1] + left[2] * right[2]
}

pub(crate) fn normalize3(
    value: [f32; 3],
    semantic: &str,
    mesh_key: u64,
    quad_index: usize,
) -> GalResult<[f32; 3]> {
    try_normalize3(value).ok_or_else(|| {
        GalError::invalid_argument(format!(
            "world mesh {mesh_key} quad {quad_index} has degenerate {semantic} basis"
        ))
    })
}

pub(crate) fn packed_mesh_vertices(vertices: &[WorldMeshVertex]) -> Vec<u8> {
    let mut out = Vec::with_capacity(vertices.len() * WORLD_MESH_GPU_VERTEX_BYTES);
    for vertex in vertices {
        let normal = unpack_normal_i8(vertex.normal_packed);
        let [block_light, sky_light] = packed_light_channels(vertex.light);
        push_f32(&mut out, vertex.position[0]);
        push_f32(&mut out, vertex.position[1]);
        push_f32(&mut out, vertex.position[2]);
        push_f32(&mut out, vertex.uv[0]);
        let color = argb_to_rgba(vertex.color_argb);
        push_f32(&mut out, color[0]);
        push_f32(&mut out, color[1]);
        push_f32(&mut out, color[2]);
        push_f32(&mut out, vertex.uv[1]);
        // The compact non-separate-AO layout has already multiplied AO into
        // RGB, but it preserves the original vertex alpha for blending. Light
        // remains an explicit coordinate for the vanilla lightmap; do not
        // synthesize a second alpha value from it.
        push_f32(&mut out, color[3]);
        push_f32(&mut out, normal[0]);
        push_f32(&mut out, normal[1]);
        push_f32(&mut out, color[3]);
        push_f32(&mut out, block_light);
        push_f32(&mut out, sky_light);
        push_f32(&mut out, normal[2]);
        push_f32(&mut out, vertex.terrain_material_bits as f32);
        push_f32(&mut out, vertex.shader_atlas_uv[0]);
        push_f32(&mut out, vertex.shader_atlas_uv[1]);
        push_u32(&mut out, vertex.shader_block_id as u32);
        push_u32(&mut out, vertex.shader_material_type as u32);
    }
    out
}

pub(crate) fn compact_direct_terrain_vertices(rich_vertices: &[u8]) -> GalResult<Vec<u8>> {
    if rich_vertices.len() % WORLD_MESH_GPU_VERTEX_BYTES != 0 {
        return Err(GalError::invalid_argument(
            "rich world mesh vertex payload is not record aligned",
        ));
    }
    let vertex_count = rich_vertices.len() / WORLD_MESH_GPU_VERTEX_BYTES;
    let mut out = Vec::with_capacity(vertex_count * WORLD_MESH_DIRECT_TERRAIN_VERTEX_BYTES);
    for vertex in rich_vertices.chunks_exact(WORLD_MESH_GPU_VERTEX_BYTES) {
        // The direct-only ABI retains exact float position and atlas UV lanes.
        // Future source/Iris routes continue to use the complete 80-byte
        // semantic vertex. Byte-originated values return to canonical packed
        // integers instead of occupying separate float lanes here.
        out.extend_from_slice(&vertex[0..12]);
        let material = f32::from_ne_bytes(vertex[60..64].try_into().unwrap());
        if !material.is_finite() || material.fract() != 0.0 || !(0.0..=511.0).contains(&material) {
            return Err(GalError::invalid_argument(
                "direct terrain material byte is not canonical",
            ));
        }
        push_u32(&mut out, material as u32);
        out.extend_from_slice(&vertex[64..72]);
        let mut color_rgba = 0u32;
        for (channel, offset) in [16usize, 20, 24, 44].into_iter().enumerate() {
            let value = f32::from_ne_bytes(vertex[offset..offset + 4].try_into().unwrap());
            if !value.is_finite() || !(0.0..=1.0).contains(&value) {
                return Err(GalError::invalid_argument(
                    "direct terrain color channel is not normalized",
                ));
            }
            color_rgba |= ((value * 255.0).round() as u32) << (channel * 8);
        }
        push_u32(&mut out, color_rgba);
        let mut packed_light = 0u32;
        for (channel, offset) in [48usize, 52].into_iter().enumerate() {
            let value = f32::from_ne_bytes(vertex[offset..offset + 4].try_into().unwrap());
            let byte = (value * 240.0).round();
            if !value.is_finite()
                || !(0.0..=255.0).contains(&byte)
                || (value - byte / 240.0).abs() > 1.0e-6
            {
                return Err(GalError::invalid_argument(
                    "direct terrain light coordinate is not byte-exact",
                ));
            }
            packed_light |= (byte as u32) << (channel * 8);
        }
        push_u32(&mut out, packed_light);
    }
    debug_assert_eq!(
        out.len(),
        vertex_count * WORLD_MESH_DIRECT_TERRAIN_VERTEX_BYTES
    );
    Ok(out)
}

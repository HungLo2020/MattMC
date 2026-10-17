//! Expanded and textured LOD vertices, segments, columns and GPU column records.

use crate::render::worldrender::geometry::arenas::SourceGeometryRange;
use crate::render::worldrender::lod::*;

/// DH emits one direction code for every generated quad. Retaining it as a
/// semantic face is required for source-independent directional shading and
/// for later shader-pack material lowering.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) enum WorldLodFaceNormal {
    Down,
    Up,
    North,
    South,
    West,
    East,
}

impl TryFrom<u8> for WorldLodFaceNormal {
    type Error = GalError;

    fn try_from(value: u8) -> Result<Self, Self::Error> {
        match value {
            0 => Ok(Self::Down),
            1 => Ok(Self::Up),
            2 => Ok(Self::North),
            3 => Ok(Self::South),
            4 => Ok(Self::West),
            5 => Ok(Self::East),
            _ => Err(GalError::invalid_argument(format!(
                "unknown Distant Horizons face normal {value}"
            ))),
        }
    }
}

#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) struct WorldLodExpandedVertex {
    /// Column-local position before the asset origin is applied.
    pub local_position: [f32; 3],
    /// DH's signed micro offset, decoded from its semantic packed metadata.
    pub micro_offset: [f32; 3],
    pub color_rgba: [f32; 4],
    pub sky_light: u8,
    pub block_light: u8,
    pub material: WorldLodMaterialCategory,
    pub normal: WorldLodFaceNormal,
}

#[derive(Clone, Debug, PartialEq)]
pub(crate) struct WorldLodExpandedSegment {
    pub layer: u32,
    pub vertices: Vec<WorldLodExpandedVertex>,
    /// The exact quad expansion used by DH's shared element buffer:
    /// `[a, b, c, c, d, a]` for each four-vertex quad.
    pub indices: Vec<u32>,
}

#[derive(Clone, Debug, PartialEq)]
pub(crate) struct WorldLodExpandedColumnAsset {
    pub column_key: u64,
    pub column_generation: u64,
    pub origin: [i32; 3],
    pub segments: Vec<WorldLodExpandedSegment>,
}

/// One fully resolved textured LOD vertex. It is a Rust frontend semantic
/// artifact, not a producer ABI or backend vertex format. The atlas rectangle
/// comes only from a generation-bound face material record, never from a
/// guessed block category or the pre-resolved DH color. `tile_uv` is allowed
/// to exceed one for a coalesced DH face so the private shader can repeat the
/// named sprite without sampling neighbouring atlas tiles.
#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) struct WorldLodTexturedVertex {
    pub local_position: [f32; 3],
    pub micro_offset: [f32; 3],
    pub color_rgba: [f32; 4],
    pub tinted: bool,
    pub sky_light: u8,
    pub block_light: u8,
    /// DH's source-defined coarse material category. Exact-atlas rendering
    /// still needs this for shader-pack material branches after it resolves a
    /// concrete sprite; atlas identity alone cannot replace it.
    pub material: WorldLodMaterialCategory,
    pub normal: WorldLodFaceNormal,
    pub tile_uv: [f32; 2],
    pub atlas_rect: [f32; 4],
}

/// A single DH reduced quad that can be rendered with an exact source atlas
/// region. The owner can batch these only with compatible atlas/material
/// resources; this structure deliberately contains no native resource ID.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct WorldLodTexturedQuad {
    pub quad_index: u32,
    pub material_id: u32,
    pub face: u32,
    pub face_layer: u32,
    pub tinted: bool,
    pub atlas_identity: String,
    pub sprite_identity: String,
    pub vertices: [WorldLodTexturedVertex; 4],
}

/// Returns the unwrapped semantic tile coverage of an exact-atlas quad. This
/// is diagnostic-facing data: a merged LOD face must retain its repeat span
/// instead of being reduced to one global-atlas UV rectangle.
pub(crate) fn world_lod_textured_quad_tile_span(quad: &WorldLodTexturedQuad) -> [f32; 2] {
    let minimum = quad
        .vertices
        .iter()
        .fold([f32::INFINITY; 2], |minimum, vertex| {
            [
                minimum[0].min(vertex.tile_uv[0]),
                minimum[1].min(vertex.tile_uv[1]),
            ]
        });
    let maximum = quad
        .vertices
        .iter()
        .fold([f32::NEG_INFINITY; 2], |maximum, vertex| {
            [
                maximum[0].max(vertex.tile_uv[0]),
                maximum[1].max(vertex.tile_uv[1]),
            ]
        });
    [maximum[0] - minimum[0], maximum[1] - minimum[1]]
}

/// Why a reduced DH quad cannot participate in an exact-atlas route. These
/// reasons are intentionally semantic so callers can diagnose the producer
/// without conflating them with pipeline or backend availability.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) enum WorldLodTexturedQuadUnavailableReason {
    MaterialUnavailable,
    MaterialMixed,
    VariantUnavailable,
    VariantMixed,
    InconsistentFace,
    MissingFaceMaterial,
    UnsupportedAtlas,
}

impl WorldLodTexturedQuadUnavailableReason {
    /// Stable diagnostic category for a quad deliberately retained on the
    /// reduced-color path. This is semantic planner evidence, not backend
    /// policy or a shader fallback selector.
    pub(crate) const fn as_str(self) -> &'static str {
        match self {
            Self::MaterialUnavailable => "material-unavailable",
            Self::MaterialMixed => "material-mixed",
            Self::VariantUnavailable => "variant-unavailable",
            Self::VariantMixed => "variant-mixed",
            Self::InconsistentFace => "inconsistent-face",
            Self::MissingFaceMaterial => "missing-face-material",
            Self::UnsupportedAtlas => "unsupported-atlas",
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) struct WorldLodTexturedQuadUnavailable {
    pub quad_index: u32,
    /// Builder-local semantic material ID and DH face are retained solely to
    /// explain why a reduced quad could not enter the exact-atlas stream.
    /// They never select a replacement material.
    pub material_id: u32,
    pub face: u32,
    pub reason: WorldLodTexturedQuadUnavailableReason,
}

/// Exact-atlas planning result for one already copied DH segment. A segment
/// may be partial: unavailable source quads remain explicit so the later
/// packer can retain their reduced-color index range without substituting a
/// guessed texture identity.
#[derive(Clone, Debug, Default, PartialEq)]
pub(crate) struct WorldLodTexturedSegmentPlan {
    pub layer: u32,
    /// Source quad cardinality stays explicit so a partial exact-atlas plan
    /// can construct a complementary coarse index stream without guessing
    /// from the resolved subset.
    pub source_quad_count: u32,
    pub quads: Vec<WorldLodTexturedQuad>,
    pub unavailable: Vec<WorldLodTexturedQuadUnavailable>,
}

/// Exact-atlas planning result for one immutable DH column generation. This
/// binds the provenance sidecar to copied geometry before any future texture
/// resource or draw is considered, so reloads cannot combine old atlas UVs
/// with a new reduced-column payload.
#[derive(Clone, Debug, Default, PartialEq)]
pub(crate) struct WorldLodTexturedColumnPlan {
    pub column_key: u64,
    pub column_generation: u64,
    pub segments: Vec<WorldLodTexturedSegmentPlan>,
}

/// One exact-atlas segment ready for a later private GPU residency. The
/// source ordinal is preserved so it can replace only its matching legacy
/// color-only segment; no duplicate same-frame geometry is implied here.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct WorldLodTexturedGpuSegment {
    pub source_segment_index: u32,
    pub layer: u32,
    pub vertex_layout_version: u32,
    pub vertex_bytes: Vec<u8>,
    pub index_type: IndexType,
    pub index_bytes: Vec<u8>,
    /// For a mixed source segment, the legacy DH vertex stream consumes this
    /// complementary index list. Known quads must not be drawn once by the
    /// coarse pass and again by the exact-atlas pass.
    pub unresolved_index_bytes: Option<Vec<u8>>,
}

/// A partial exact-atlas asset is deliberate. Known quads are packed into the
/// atlas stream while unknown quads retain the existing reduced-color stream.
/// `unavailable_source_segments` records exactly those mixed source segments;
/// it prevents the caller from suppressing their coarse geometry wholesale.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub(crate) struct WorldLodTexturedGpuColumnAsset {
    pub column_key: u64,
    pub column_generation: u64,
    pub segments: Vec<WorldLodTexturedGpuSegment>,
    pub unavailable_source_segments: Vec<u32>,
}

/// Immutable, owned bytes ready for a future Rust LOD GPU asset. Keeping this
/// separate from the expanded records gives the eventual asset cache a stable
/// payload without retaining DH's CPU layout or any Java-owned memory.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct WorldLodGpuSegment {
    pub layer: u32,
    pub vertex_layout_version: u32,
    /// Counts survive upload-payload release. They are validated while the
    /// immutable asset enters Rust and are all later draw planning needs once
    /// private Vulkan buffers own the geometry.
    pub vertex_count: u32,
    pub vertex_bytes: Vec<u8>,
    pub index_type: IndexType,
    pub index_count: u32,
    pub index_bytes: Vec<u8>,
}

impl WorldLodGpuSegment {
    /// Frees the CPU upload copy only after the same generation has become a
    /// live private GPU resource. Retaining the layout, counts, layer, and
    /// index type keeps generation-checked draw validation intact without
    /// duplicating immutable geometry for the lifetime of a visible column.
    pub(crate) fn release_uploaded_payload(&mut self) {
        self.vertex_bytes = Vec::new();
        self.index_bytes = Vec::new();
    }

    pub(super) fn upload_payload_is_present(&self) -> bool {
        self.vertex_bytes.len()
            == usize::try_from(self.vertex_count)
                .ok()
                .and_then(|count| count.checked_mul(WORLD_LOD_GPU_VERTEX_BYTES))
                .unwrap_or(usize::MAX)
            && self.index_bytes.len()
                == usize::try_from(self.index_count)
                    .ok()
                    .and_then(|count| {
                        count.checked_mul(match self.index_type {
                            IndexType::U16 => std::mem::size_of::<u16>(),
                            IndexType::U32 => std::mem::size_of::<u32>(),
                        })
                    })
                    .unwrap_or(usize::MAX)
    }
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct WorldLodGpuColumnAsset {
    pub column_key: u64,
    pub column_generation: u64,
    pub origin: [i32; 3],
    pub segments: Vec<WorldLodGpuSegment>,
}

/// One segment's place in the shared DH geometry pages: the vertex page
/// with the segment's first vertex, and its byte offset in the index page.
#[derive(Clone, Copy, Debug)]
pub(crate) struct WorldLodGpuSegmentResources {
    pub vertex_buffer: Handle,
    pub vertex_base: u32,
    pub index_offset: u64,
}

/// A column's ranges in the shared DH geometry pages
/// ([`WorldLodGpuResidency::pages`]). Columns no longer own buffers: their
/// ranges return to the pages once no submission can read them.
#[derive(Debug)]
pub(crate) struct WorldLodGpuColumnResources {
    pub column_generation: u64,
    pub segments: Vec<WorldLodGpuSegmentResources>,
    pub index_buffer: Handle,
    pub(in crate::render::worldrender::lod) vertex_range: SourceGeometryRange,
    pub(in crate::render::worldrender::lod) index_range: SourceGeometryRange,
}

/// One generation-checked LOD draw range ready for a later Rust-owned
/// material pass. This is deliberately an internal frontend record: it keeps
/// the stable semantic column identity alongside private GAL buffers without
/// exposing either native backend state or the original DH vertex layout.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct WorldLodGpuDraw {
    pub column_key: u64,
    pub column_generation: u64,
    pub origin: [i32; 3],
    pub layer: u32,
    pub segment_index: u32,
    /// Stable visible-list order copied from DH semantic extraction. It is
    /// irrelevant to opaque batching but required to preserve transparent
    /// ordering before the backend receives any draw operations.
    pub order: u32,
    pub vertex_buffer: Handle,
    /// Vertex address within the private column stream; original segment
    /// indices remain unchanged in both the direct and source paths.
    pub vertex_base: u32,
    pub index_buffer: Handle,
    pub index_offset: u64,
    pub index_type: IndexType,
    pub index_count: u32,
}


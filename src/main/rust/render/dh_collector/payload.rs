//! Copied DH column payloads: packed 16-byte vertices per transport segment,
//! in the four layer streams. They are immutable per column generation.
use super::Counts;
use crate::render::vulkanic::error::{GalError, GalResult, StatusCode};
use crate::render::worldrender::frame::limits::{
    WORLD_LOD_MAX_NORMAL_INDEX, WORLD_LOD_MAX_SEGMENTS_PER_COLUMN, WORLD_LOD_MAX_VERTICES_PER_SEGMENT,
};
use crate::render::worldrender::frame::requests::{WorldLodColumnAsset, WorldLodSegment, WorldLodVertex};

/// `VERTEX_LAYOUT_VERSION`.
pub(crate) const VERTEX_LAYOUT_VERSION: i32 = 1;
pub(crate) const VERTEX_STRIDE: usize = 16;
const LAYER_NAMES: [&str; 4] = ["opaque", "transparent-side", "transparent-up", "transparent-water-up"];

/// One transport segment: its source DH buffer and its packed vertices.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct Segment {
    pub source_index: i32,
    pub bytes: Box<[u8]>,
}

/// A column's payload: origin and segments per layer (opaque, transparent
/// side, transparent up, water). Segments are never empty.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub(crate) struct Payload {
    pub origin: [i32; 3],
    pub layers: [Vec<Segment>; 4],
}

/// A packed vertex's fields as `LodVertex` decodes them (native byte order).
fn vertex_fields(bytes: &[u8]) -> [(&'static str, u32); 11] {
    let short = |at: usize| u16::from_ne_bytes([bytes[at], bytes[at + 1]]) as u32;
    [
        ("local-x", short(0)),
        ("local-y", short(2)),
        ("local-z", short(4)),
        ("packed-light-micro", short(6)),
        ("red", bytes[8] as u32),
        ("green", bytes[9] as u32),
        ("blue", bytes[10] as u32),
        ("alpha", bytes[11] as u32),
        ("material-id", bytes[12] as u32),
        ("normal-index", bytes[13] as u32),
        ("padding", short(14)),
    ]
}

impl Payload {
    /// `LodColumnSnapshot.byteSize()`.
    pub(crate) fn byte_size(&self) -> i64 {
        self.layers.iter().flatten().map(|s| s.bytes.len() as i64).sum()
    }

    /// `LodColumnSnapshot.hasSegments()`.
    pub(crate) fn has_segments(&self) -> bool {
        self.layers.iter().any(|layer| !layer.is_empty())
    }

    /// Emitted segments per layer (every stored segment is non-empty).
    pub(crate) fn counts(&self) -> Counts {
        let count = |layer: usize| self.layers[layer].iter().filter(|s| !s.bytes.is_empty()).count() as i32;
        Counts { opaque: count(0), side: count(1), up: count(2), water: count(3) }
    }

    /// `replaced.payloadDifference(snapshot)`, with `self` the replaced payload.
    pub(crate) fn difference(&self, other: &Payload) -> String {
        if self.origin != other.origin {
            return "origin".into();
        }
        for (layer, name) in LAYER_NAMES.iter().enumerate() {
            if let Some(difference) = Self::layer_difference(&self.layers[layer], &other.layers[layer], name) {
                return difference;
            }
        }
        "unknown".into()
    }

    fn layer_difference(left: &[Segment], right: &[Segment], name: &str) -> Option<String> {
        if left.len() != right.len() {
            return Some(format!("{name}-segment-count"));
        }
        for (index, (l, r)) in left.iter().zip(right).enumerate() {
            if l.source_index != r.source_index {
                return Some(format!("{name}-source-index"));
            }
            if l.bytes.len() != r.bytes.len() {
                return Some(format!("{name}-vertex-count"));
            }
            for (vertex, (lv, rv)) in l.bytes.chunks_exact(VERTEX_STRIDE).zip(r.bytes.chunks_exact(VERTEX_STRIDE)).enumerate() {
                for ((field, a), (_, b)) in vertex_fields(lv).into_iter().zip(vertex_fields(rv)) {
                    if a != b {
                        return Some(format!("{name}[{index}].vertex[{vertex}].{field}={a}->{b}"));
                    }
                }
            }
        }
        None
    }

    /// The frontend asset for this payload: `toBridgeRecord` as
    /// `decode_world_lod_asset_update` decodes and checks it, with the same
    /// errors.
    pub(crate) fn asset(&self, column_key: i64, column_generation: i64) -> GalResult<WorldLodColumnAsset> {
        let invalid = |message: String| GalError::ffi(StatusCode::InvalidArgument, message);
        if column_generation == 0 {
            return Err(invalid("world LOD column asset generation must be non-zero".into()));
        }
        let segment_count = self.layers.iter().map(Vec::len).sum::<usize>();
        if segment_count == 0 || segment_count > WORLD_LOD_MAX_SEGMENTS_PER_COLUMN {
            return Err(invalid(format!(
                "world LOD column {} has {segment_count} segments; expected 1..={WORLD_LOD_MAX_SEGMENTS_PER_COLUMN}",
                column_key as u64
            )));
        }
        let mut segments = Vec::with_capacity(segment_count);
        for (layer, stream) in self.layers.iter().enumerate() {
            for segment in stream {
                if segment.bytes.len() > WORLD_LOD_MAX_VERTICES_PER_SEGMENT * VERTEX_STRIDE {
                    return Err(GalError::ffi(
                        StatusCode::LengthOverflow,
                        "packed world LOD vertices byte length exceeds ABI maximum",
                    ));
                }
                if segment.bytes.len() % VERTEX_STRIDE != 0 {
                    return Err(invalid("packed world LOD vertices are not 16-byte aligned".into()));
                }
                let vertex_count = segment.bytes.len() / VERTEX_STRIDE;
                if vertex_count == 0 || vertex_count % 4 != 0 {
                    return Err(invalid(format!(
                        "world LOD segment has {vertex_count} vertices; expected quad-aligned 1..={WORLD_LOD_MAX_VERTICES_PER_SEGMENT}"
                    )));
                }
                let mut vertices = Vec::with_capacity(vertex_count);
                for b in segment.bytes.chunks_exact(VERTEX_STRIDE) {
                    if b[14] != 0 || b[15] != 0 {
                        return Err(invalid("packed world LOD vertex has non-zero reserved padding".into()));
                    }
                    if b[12] > 15 || b[13] > WORLD_LOD_MAX_NORMAL_INDEX {
                        return Err(invalid("packed world LOD vertex contains an out-of-range material or normal".into()));
                    }
                    vertices.push(WorldLodVertex {
                        local_position: [
                            u16::from_ne_bytes([b[0], b[1]]),
                            u16::from_ne_bytes([b[2], b[3]]),
                            u16::from_ne_bytes([b[4], b[5]]),
                        ],
                        packed_light_and_micro_offset: u16::from_ne_bytes([b[6], b[7]]),
                        color_rgba: [b[8], b[9], b[10], b[11]],
                        material_id: b[12],
                        normal_index: b[13],
                    });
                }
                segments.push(WorldLodSegment { layer: layer as u32 + 1, vertices });
            }
        }
        Ok(WorldLodColumnAsset {
            column_key: column_key as u64,
            column_generation: column_generation as u64,
            vertex_layout_version: VERTEX_LAYOUT_VERSION as u32,
            origin: self.origin,
            segments,
        })
    }
}

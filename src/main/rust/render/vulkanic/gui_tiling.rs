//! Backend-neutral lowering of immutable tiled GUI geometry.
//! No Java texture objects, GPU handles, pipeline state, or presenter enter here.
//! Expansion is preflighted before allocation and feeds ordered frame submission.

use super::{GalError, GalResult, SEMANTIC_MAX_VIEWPORT_AXIS};

/// Same finite expanded-segment bound as the existing copied GUI path. A large
/// window is not rejected at Java's separate 4096-geometric-tile threshold.
const MAX_SEGMENTS: usize = 16_384;

#[derive(Clone, Copy, Debug)]
pub(crate) struct GuiTileGeometry {
    pub bounds: [i32; 4],
    pub tile_extent: [u32; 2],
    /// Source UV endpoints: u0, v0, u1, v1, repeated from the start each tile.
    pub uv: [f32; 4],
    /// Column-major 2D affine transform, including translation.
    pub pose: [f32; 6],
}

#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) struct GuiTileQuad {
    pub origin: [f32; 2],
    pub axis_u: [f32; 2],
    pub axis_v: [f32; 2],
    pub uv: [f32; 4],
}

#[derive(Clone, Copy)]
struct AxisPiece {
    tile_offset: u32,
    start: f32,
    end: f32,
    uv_start: f32,
    uv_end: f32,
}

fn invalid() -> GalError {
    GalError::invalid_argument("invalid or over-budget semantic GUI tile geometry")
}

fn tile_uv_end(start: f32, end: f32, length: u32, tile: u32) -> f32 {
    // Frozen TiledBlitRenderState uses the original endpoint for full tiles and
    // Mth.lerp(remaining/tileSize, start, end) for a partial last tile.
    if length == tile {
        end
    } else {
        start + (length as f32 / tile as f32) * (end - start)
    }
}

fn wrapped_piece_count(start: f32, end: f32) -> usize {
    // A partial tile can round to a constant UV interval. It still emits one
    // geometric quad, exactly as the reference's vertex stream does.
    ((end as f64).ceil() - (start as f64).floor()).max(1.0) as usize
}

fn axis_piece_count(length: u32, tile: u32, start: f32, end: f32) -> GalResult<usize> {
    let mut count = 0usize;
    for offset in (0..length).step_by(tile as usize) {
        let size = tile.min(length - offset);
        count = count
            .checked_add(wrapped_piece_count(
                start,
                tile_uv_end(start, end, size, tile),
            ))
            .ok_or_else(invalid)?;
        if count > MAX_SEGMENTS {
            return Err(invalid());
        }
    }
    Ok(count)
}

fn axis_pieces(
    origin: i32,
    length: u32,
    tile: u32,
    start: f32,
    end: f32,
    count: usize,
) -> Vec<AxisPiece> {
    let mut pieces = Vec::with_capacity(count);
    for offset in (0..length).step_by(tile as usize) {
        let size = tile.min(length - offset);
        let limit = tile_uv_end(start, end, size, tile) as f64;
        let first = start as f64;
        let local_origin = origin as f32 + offset as f32;
        if first == limit {
            pieces.push(AxisPiece {
                tile_offset: offset,
                start: local_origin,
                end: local_origin + size as f32,
                uv_start: (first - first.floor()) as f32,
                uv_end: (first - first.floor()) as f32,
            });
            continue;
        }
        let mut cursor = first;
        while cursor < limit {
            let unit = cursor.floor();
            let next = (unit + 1.0).min(limit);
            pieces.push(AxisPiece {
                tile_offset: offset,
                start: local_origin + size as f32 * ((cursor - first) / (limit - first)) as f32,
                end: local_origin + size as f32 * ((next - first) / (limit - first)) as f32,
                uv_start: (cursor - unit) as f32,
                uv_end: (next - unit) as f32,
            });
            cursor = next;
        }
    }
    debug_assert_eq!(count, pieces.len());
    pieces
}

fn preflight(geometry: GuiTileGeometry) -> GalResult<(u32, u32, usize, usize)> {
    let [left, top, right, bottom] = geometry.bounds;
    let width = i64::from(right) - i64::from(left);
    let height = i64::from(bottom) - i64::from(top);
    let [tile_width, tile_height] = geometry.tile_extent;
    let [u0, v0, u1, v1] = geometry.uv;
    if width <= 0
        || height <= 0
        || width > i64::from(SEMANTIC_MAX_VIEWPORT_AXIS)
        || height > i64::from(SEMANTIC_MAX_VIEWPORT_AXIS)
        || tile_width == 0
        || tile_height == 0
        || geometry
            .uv
            .iter()
            .chain(geometry.pose.iter())
            .any(|v| !v.is_finite())
        || u1 <= u0
        || v1 <= v0
        || u1 - u0 > 4096.0
        || v1 - v0 > 4096.0
    {
        return Err(invalid());
    }
    let width = width as u32;
    let height = height as u32;
    // Exact preflight before allocating either axis or any output. Hostile UV
    // repetition cannot create a partial command list or unbounded residency.
    let x_count = axis_piece_count(width, tile_width, u0, u1)?;
    let y_count = axis_piece_count(height, tile_height, v0, v1)?;
    x_count
        .checked_mul(y_count)
        .filter(|n| *n <= MAX_SEGMENTS)
        .ok_or_else(invalid)?;
    Ok((width, height, x_count, y_count))
}

pub(crate) fn tile_segment_count(geometry: GuiTileGeometry) -> GalResult<usize> {
    let (_, _, x, y) = preflight(geometry)?;
    Ok(x * y)
}

pub(crate) fn lower_tiles(geometry: GuiTileGeometry) -> GalResult<Vec<GuiTileQuad>> {
    let (width, height, x_count, y_count) = preflight(geometry)?;
    let count = x_count * y_count;
    let [left, top, _, _] = geometry.bounds;
    let [tile_width, tile_height] = geometry.tile_extent;
    let [u0, v0, u1, v1] = geometry.uv;
    let xs = axis_pieces(left, width, tile_width, u0, u1, x_count);
    let ys = axis_pieces(top, height, tile_height, v0, v1, y_count);
    let [m00, m01, m10, m11, m20, m21] = geometry.pose;
    let transform = |x, y| [m00 * x + m10 * y + m20, m01 * x + m11 * y + m21];
    let mut result = Vec::with_capacity(count);
    // Preserve tile-X, tile-Y, wrapped-X, wrapped-Y order. Splitting an atlas
    // interval must not reorder children across their semantic parent tiles.
    for x_tile in xs.chunk_by(|a, b| a.tile_offset == b.tile_offset) {
        for y_tile in ys.chunk_by(|a, b| a.tile_offset == b.tile_offset) {
            for x in x_tile {
                for y in y_tile {
                    let origin = transform(x.start, y.start);
                    let end_u = transform(x.end, y.start);
                    let end_v = transform(x.start, y.end);
                    let quad = GuiTileQuad {
                        origin,
                        axis_u: [end_u[0] - origin[0], end_u[1] - origin[1]],
                        axis_v: [end_v[0] - origin[0], end_v[1] - origin[1]],
                        uv: [x.uv_start, y.uv_start, x.uv_end, y.uv_end],
                    };
                    if quad
                        .origin
                        .iter()
                        .chain(quad.axis_u.iter())
                        .chain(quad.axis_v.iter())
                        .any(|v| !v.is_finite())
                    {
                        return Err(invalid());
                    }
                    result.push(quad);
                }
            }
        }
    }
    Ok(result)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn geometry() -> GuiTileGeometry {
        GuiTileGeometry {
            bounds: [7, 11, 77, 50],
            tile_extent: [32, 32],
            uv: [0.25, 0.5, 0.75, 1.0],
            pose: [1.0, 0.0, 0.0, 1.0, 0.0, 0.0],
        }
    }

    #[test]
    fn rust_gui_tiles_reset_atlas_uvs_and_shorten_partial_edges() {
        let quads = lower_tiles(geometry()).unwrap();
        assert_eq!(6, quads.len());
        assert_eq!([7.0, 11.0], quads[0].origin);
        assert_eq!([0.25, 0.5, 0.75, 1.0], quads[0].uv);
        assert_eq!(quads[0].uv, quads[2].uv);
        assert_eq!([71.0, 43.0], quads[5].origin);
        assert_eq!([6.0, 0.0], quads[5].axis_u);
        assert_eq!([0.0, 7.0], quads[5].axis_v);
        assert_eq!([0.25, 0.5, 0.34375, 0.609375], quads[5].uv);
    }

    #[test]
    fn rust_gui_tiles_cover_4k_at_scale_one_without_java_tile_limit() {
        let quads = lower_tiles(GuiTileGeometry {
            bounds: [0, 0, 3840, 2160],
            uv: [0.0, 0.0, 1.0, 1.0],
            ..geometry()
        })
        .unwrap();
        assert_eq!(8160, quads.len());
        assert_eq!([3808.0, 2144.0], quads.last().unwrap().origin);
        assert_eq!([0.0, 16.0], quads.last().unwrap().axis_v);
        assert_eq!([0.0, 0.0, 1.0, 0.5], quads.last().unwrap().uv);
        let area: f32 = quads.iter().map(|q| q.axis_u[0] * q.axis_v[1]).sum();
        assert_eq!(3840.0 * 2160.0, area);
    }

    #[test]
    fn rust_gui_tiles_wrap_negative_offsets_without_losing_geometry() {
        let quads = lower_tiles(GuiTileGeometry {
            bounds: [0, 0, 32, 32],
            uv: [-0.25, 0.0, 0.75, 1.0],
            ..geometry()
        })
        .unwrap();
        assert_eq!(2, quads.len());
        assert_eq!([0.75, 0.0, 1.0, 1.0], quads[0].uv);
        assert_eq!([8.0, 0.0], quads[0].axis_u);
        assert_eq!([8.0, 0.0], quads[1].origin);
        assert_eq!([0.0, 0.0, 0.75, 1.0], quads[1].uv);
        assert_eq!([24.0, 0.0], quads[1].axis_u);
    }

    #[test]
    fn rust_gui_tiles_preserve_rotation_shear_and_translation() {
        let quads = lower_tiles(GuiTileGeometry {
            pose: [0.0, 2.0, -3.0, 0.5, 100.0, -20.0],
            ..geometry()
        })
        .unwrap();
        assert_eq!([67.0, -0.5], quads[0].origin);
        assert_eq!([0.0, 64.0], quads[0].axis_u);
        assert_eq!([-96.0, 16.0], quads[0].axis_v);
    }

    #[test]
    fn rust_gui_tiles_preserve_parent_order_when_both_axes_wrap() {
        let quads = lower_tiles(GuiTileGeometry {
            bounds: [0, 0, 64, 64],
            uv: [0.5, 0.5, 1.5, 1.5],
            ..geometry()
        })
        .unwrap();
        assert_eq!(16, quads.len());
        assert_eq!([0.0, 0.0], quads[0].origin);
        assert_eq!([0.0, 16.0], quads[1].origin);
        assert_eq!([16.0, 0.0], quads[2].origin);
        assert_eq!([16.0, 16.0], quads[3].origin);
        assert_eq!([0.0, 32.0], quads[4].origin);
        assert_eq!([32.0, 0.0], quads[8].origin);
    }

    #[test]
    fn rust_gui_tiles_reject_hostile_expansion_and_nonfinite_geometry() {
        for bad in [
            GuiTileGeometry {
                bounds: [0, 0, 16384, 16384],
                tile_extent: [1, 1],
                ..geometry()
            },
            GuiTileGeometry {
                uv: [0.0, 0.0, 4096.0, 4096.0],
                ..geometry()
            },
            GuiTileGeometry {
                bounds: [i32::MIN, 0, i32::MAX, 32],
                ..geometry()
            },
            GuiTileGeometry {
                tile_extent: [0, 32],
                ..geometry()
            },
            GuiTileGeometry {
                uv: [f32::NAN, 0.0, 1.0, 1.0],
                ..geometry()
            },
            GuiTileGeometry {
                pose: [f32::MAX; 6],
                ..geometry()
            },
        ] {
            assert!(lower_tiles(bad).is_err(), "{bad:?}");
        }
    }

    #[test]
    fn rust_gui_tiles_keep_the_existing_segment_budget_exact() {
        let full = GuiTileGeometry {
            bounds: [0, 0, 4096, 4096],
            uv: [0.0, 0.0, 1.0, 1.0],
            ..geometry()
        };
        assert_eq!(MAX_SEGMENTS, lower_tiles(full).unwrap().len());
        assert!(lower_tiles(GuiTileGeometry {
            bounds: [0, 0, 4097, 4096],
            ..full
        })
        .is_err());
    }

    #[test]
    fn rust_gui_tiles_retain_constant_uv_after_partial_endpoint_rounding() {
        let quads = lower_tiles(GuiTileGeometry {
            bounds: [0, 0, 1, 1],
            tile_extent: [u32::MAX, u32::MAX],
            uv: [0.5, 0.5, 0.75, 0.75],
            ..geometry()
        })
        .unwrap();
        assert_eq!(1, quads.len());
        assert_eq!([0.5; 4], quads[0].uv);
        assert_eq!([1.0, 0.0], quads[0].axis_u);
        assert_eq!([0.0, 1.0], quads[0].axis_v);
    }
}

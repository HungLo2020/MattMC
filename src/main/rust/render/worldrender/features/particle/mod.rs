//! Ordinary particle semantics lowered without Java geometry or GPU state.
//! Camera-relative orientation, size and UV bounds arrive as immutable game data.

#[cfg(test)]
mod tests;
use crate::render::worldrender::*;

#[derive(Clone, Copy, Debug)]
pub(crate) enum ParticleSurface {
    OrdinaryOpaque,
    OrdinaryTranslucent,
    TerrainOpaque,
    TerrainCutout,
    TerrainTranslucent,
}

impl ParticleSurface {
    pub(crate) fn from_wire(value: u32) -> GalResult<Self> {
        match value {
            0 => Ok(Self::OrdinaryOpaque),
            1 => Ok(Self::OrdinaryTranslucent),
            2 => Ok(Self::TerrainOpaque),
            3 => Ok(Self::TerrainCutout),
            4 => Ok(Self::TerrainTranslucent),
            _ => Err(GalError::invalid_argument("unknown particle surface")),
        }
    }
    pub(crate) fn translucent(self) -> bool {
        matches!(self, Self::OrdinaryTranslucent | Self::TerrainTranslucent)
    }
}

#[derive(Clone, Copy, Debug)]
pub(crate) struct ParticleQuad {
    pub center: [f32; 3],
    /// Gameplay orientation, in x/y/z/w order; not necessarily unit length.
    pub rotation: [f32; 4],
    /// Signed lifetime-interpolated size. Zero is a valid degenerate quad.
    pub size: f32,
    /// Original sprite bounds: u0/u1/v0/v1, including reversed U.
    pub uv_bounds: [f32; 4],
    pub color_argb: u32,
    pub packed_light: u32,
    /// Semantic identity of an independently published Rust-owned texture.
    pub texture_id: u32,
    pub translucent: bool,
}

/// A fragment of a block sprite, with game-provided tint and sampled lighting.
/// Separate from ordinary particles: these UVs address the owned block atlas,
/// and alpha-tested block surfaces are not alpha-blended particles.
/// Private until the typed transport and paired terrain captures are wired.
#[derive(Clone, Copy, Debug)]
pub(crate) struct TerrainParticleQuad {
    pub quad: ParticleQuad,
    pub alpha_tested: bool,
}

impl TerrainParticleQuad {
    pub(crate) fn lower(self, viewport: [u32; 2]) -> GalResult<WorldMaterialQuadRequest> {
        if self.quad.translucent && self.alpha_tested {
            return Err(GalError::invalid_argument(
                "terrain particle cannot be both cutout and translucent",
            ));
        }
        let mut request = self.quad.lower(viewport)?;
        request.source_uv_space = WORLD_MATERIAL_SOURCE_UV_MINECRAFT_BLOCK_ATLAS;
        if self.alpha_tested {
            request.material_id = WORLD_MATERIAL_ID_CUTOUT_TEXTURED;
            request.material_mode = WORLD_MATERIAL_MODE_CUTOUT;
        }
        Ok(request)
    }
}

impl ParticleQuad {
    pub(crate) fn lower_surface(
        self,
        surface: ParticleSurface,
        viewport: [u32; 2],
    ) -> GalResult<WorldMaterialQuadRequest> {
        if self.translucent != surface.translucent() {
            return Err(GalError::invalid_argument(
                "particle surface disagrees with translucency",
            ));
        }
        match surface {
            ParticleSurface::TerrainOpaque
            | ParticleSurface::TerrainCutout
            | ParticleSurface::TerrainTranslucent => TerrainParticleQuad {
                quad: self,
                alpha_tested: matches!(surface, ParticleSurface::TerrainCutout),
            }
            .lower(viewport),
            _ => self.lower(viewport),
        }
    }
    pub(crate) fn lower(self, viewport: [u32; 2]) -> GalResult<WorldMaterialQuadRequest> {
        if self.texture_id == 0
            || viewport
                .iter()
                .any(|&v| v == 0 || v > crate::render::scene::SEMANTIC_MAX_VIEWPORT_AXIS as u32)
        {
            return Err(GalError::invalid_argument(
                "particle requires a texture identity and bounded viewport",
            ));
        }
        if self
            .center
            .iter()
            .chain(self.rotation.iter())
            .chain(self.uv_bounds.iter())
            .chain(std::iter::once(&self.size))
            .any(|v| !v.is_finite())
        {
            return Err(GalError::invalid_argument(
                "particle semantics must be finite",
            ));
        }
        let [u0, u1, v0, v1] = self.uv_bounds;
        for span in [(u1 - u0).abs(), (v1 - v0).abs()] {
            if !span.is_finite() || span == 0.0 || span > 4096.0 {
                return Err(GalError::invalid_argument(
                    "particle sprite span is invalid",
                ));
            }
        }
        let [x, y, z, w] = self.rotation;
        let (xx, yy, zz, ww) = (x * x, y * y, z * z, w * w);
        let norm = xx + yy + zz + ww;
        if !norm.is_finite() || norm <= 1.0e-8 {
            return Err(GalError::invalid_argument(
                "particle rotation must be nonzero and bounded",
            ));
        }
        let k = 1.0 / norm;
        let (xy, xz, yz, xw, zw, yw) = (x * y, x * z, y * z, x * w, z * w, y * w);
        // Frozen QuadParticleRenderState.renderVertex rotates the unit corner,
        // then multiplies by signed size, then adds camera-relative position.
        // Use the general quaternion transform, not a unit-quaternion shortcut.
        let columns = [
            [
                (xx - yy - zz + ww) * k,
                2.0 * (xy + zw) * k,
                2.0 * (xz - yw) * k,
            ],
            [
                2.0 * (xy - zw) * k,
                (yy - xx - zz + ww) * k,
                2.0 * (yz + xw) * k,
            ],
        ];
        let vertices = [[1.0, -1.0], [1.0, 1.0], [-1.0, 1.0], [-1.0, -1.0]].map(|[a, b]| {
            std::array::from_fn(|i| {
                (columns[0][i] * a + columns[1][i] * b) * self.size + self.center[i]
            })
        });
        if vertices.iter().flatten().any(|v: &f32| !v.is_finite()) {
            return Err(GalError::invalid_argument("particle geometry overflow"));
        }
        Ok(WorldMaterialQuadRequest {
            stratum: WORLD_STRATUM_OPAQUE_TEXTURED_GEOMETRY,
            material_id: if self.translucent {
                WORLD_MATERIAL_ID_TRANSLUCENT_TEXTURED
            } else {
                WORLD_MATERIAL_ID_OPAQUE_TEXTURED
            },
            texture_id: self.texture_id,
            material_mode: if self.translucent {
                WORLD_MATERIAL_MODE_TRANSLUCENT
            } else {
                WORLD_MATERIAL_MODE_OPAQUE
            },
            // Ordinary Frozen particle pipelines retain depth writes and back culling.
            depth_policy: WORLD_DEPTH_POLICY_TEST_WRITE,
            cull_policy: WORLD_CULL_BACK,
            topology: WORLD_TOPOLOGY_TRIANGLES,
            winding: WORLD_WINDING_CCW,
            color_argb: self.color_argb,
            vertices,
            uvs: [[u1, v1], [u1, v0], [u0, v0], [u0, v1]],
            viewport_width: viewport[0],
            viewport_height: viewport[1],
            source_program: WORLD_MATERIAL_SOURCE_PARTICLES,
            source_uv_space: WORLD_MATERIAL_SOURCE_UV_LOCAL_TEXTURE,
            source_color_argb: self.color_argb,
            packed_light: self.packed_light,
            vertex_color_argb: [self.color_argb; 4],
            vertex_packed_light: [self.packed_light; 4],
            block_entity_id: -1,
        })
    }
}


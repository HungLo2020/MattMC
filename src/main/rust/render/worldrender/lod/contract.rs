//! LOD material categories, pass classes and material contracts; per-frame draw planning and admission.

use crate::render::worldrender::lod::*;

/// Distant Horizons' public block-material classification. It deliberately
/// remains a small semantic category: source-specific texture and shader-pack
/// policy belong to a later Rust-owned LOD material resolver.
#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub(crate) enum WorldLodMaterialCategory {
    Unknown,
    Leaves,
    Stone,
    Wood,
    Metal,
    Dirt,
    Lava,
    Deepslate,
    Snow,
    Sand,
    Terracotta,
    NetherStone,
    Water,
    Grass,
    Air,
    Illuminated,
}

impl TryFrom<u8> for WorldLodMaterialCategory {
    type Error = GalError;

    fn try_from(value: u8) -> Result<Self, Self::Error> {
        match value {
            0 => Ok(Self::Unknown),
            1 => Ok(Self::Leaves),
            2 => Ok(Self::Stone),
            3 => Ok(Self::Wood),
            4 => Ok(Self::Metal),
            5 => Ok(Self::Dirt),
            6 => Ok(Self::Lava),
            7 => Ok(Self::Deepslate),
            8 => Ok(Self::Snow),
            9 => Ok(Self::Sand),
            10 => Ok(Self::Terracotta),
            11 => Ok(Self::NetherStone),
            12 => Ok(Self::Water),
            13 => Ok(Self::Grass),
            14 => Ok(Self::Air),
            15 => Ok(Self::Illuminated),
            _ => Err(GalError::invalid_argument(format!(
                "unknown Distant Horizons material category {value}"
            ))),
        }
    }
}

/// The Rust-owned material pass class is explicit in the semantic planner.
/// Transparent work keeps the exact legacy-visible order and remains separate
/// from opaque work; neither backend infers blending policy from a layer id.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) enum WorldLodPassClass {
    Opaque,
    /// DH's `TRANSPARENT_DETAIL` state for horizontal/side geometry. It uses
    /// source-alpha blending, LESS depth testing, and preserves the existing
    /// depth value while the vanilla renderer owns the final opaque history.
    TransparentSide,
    /// DH's inherited `TRANSPARENT` state for upward foliage geometry. It has
    /// the same blend/depth comparison, but writes depth just like Frozen's
    /// no-shader transparent pass.
    TransparentUp,
    WaterSurface,
}

/// A named material contract for the first Rust-owned DH pass. DH emits
/// pre-resolved terrain color rather than Minecraft atlas UVs, so this cannot
/// share the atlas/material bindings used by static chunk terrain. The
/// lightmap is still a semantic Rust-owned resource, not a Java texture.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) struct WorldLodMaterialContract {
    pub pass: WorldLodPassClass,
    pub vertex_layout_version: u32,
    pub requires_vanilla_lightmap: bool,
    pub uses_vertex_color: bool,
    pub uses_face_normal: bool,
    pub uses_material_category: bool,
}

impl WorldLodMaterialContract {
    pub(crate) const OPAQUE: Self = Self {
        pass: WorldLodPassClass::Opaque,
        vertex_layout_version: WORLD_LOD_GPU_VERTEX_LAYOUT_V2,
        requires_vanilla_lightmap: true,
        uses_vertex_color: true,
        uses_face_normal: true,
        uses_material_category: true,
    };

    pub(crate) const TRANSPARENT_SIDE: Self = Self {
        pass: WorldLodPassClass::TransparentSide,
        vertex_layout_version: WORLD_LOD_GPU_VERTEX_LAYOUT_V2,
        requires_vanilla_lightmap: true,
        uses_vertex_color: true,
        uses_face_normal: true,
        uses_material_category: true,
    };

    pub(crate) const TRANSPARENT_UP: Self = Self {
        pass: WorldLodPassClass::TransparentUp,
        vertex_layout_version: WORLD_LOD_GPU_VERTEX_LAYOUT_V2,
        requires_vanilla_lightmap: true,
        uses_vertex_color: true,
        uses_face_normal: true,
        uses_material_category: true,
    };

    pub(crate) const WATER_SURFACE: Self = Self {
        pass: WorldLodPassClass::WaterSurface,
        vertex_layout_version: WORLD_LOD_GPU_VERTEX_LAYOUT_V2,
        requires_vanilla_lightmap: true,
        uses_vertex_color: true,
        uses_face_normal: true,
        uses_material_category: true,
    };
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) enum WorldLodAdmissionError {
    UnknownLayer(u32),
    WaterLayerRequiresWaterPath(u32),
}

impl std::fmt::Display for WorldLodAdmissionError {
    fn fmt(&self, formatter: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::UnknownLayer(layer) => {
                write!(formatter, "unknown Distant Horizons layer {layer}")
            }
            Self::WaterLayerRequiresWaterPath(layer) => write!(
                formatter,
                "Distant Horizons water layer {layer} must use the explicit water-surface admission path"
            ),
        }
    }
}

/// A fully resolved, backend-neutral opaque LOD draw. It preserves the
/// copied semantic material categories for a later Rust material resolver,
/// while all GPU handles remain private to the frontend.
#[derive(Clone, Copy, Debug)]
pub(crate) struct WorldLodOpaqueDraw {
    pub draw: WorldLodGpuDraw,
    pub uniforms: WorldLodDrawUniform,
    pub pass: WorldLodPassClass,
    pub material_contract: WorldLodMaterialContract,
}

/// A resolved non-water transparent draw. `order` is copied from the actual
/// DH visible-list traversal and is retained until the private transparent
/// pass consumes it; no backend-side resorting is permitted.
#[derive(Clone, Copy, Debug)]
pub(crate) struct WorldLodTransparentDraw {
    pub draw: WorldLodGpuDraw,
    pub uniforms: WorldLodDrawUniform,
    pub pass: WorldLodPassClass,
    pub material_contract: WorldLodMaterialContract,
}

/// A resolved DH water-surface draw. DH emits this as its own stream because
/// it must preserve the material's depth/cull policy independently of general
/// transparent detail geometry.
#[derive(Clone, Copy, Debug)]
pub(crate) struct WorldLodWaterDraw {
    pub draw: WorldLodGpuDraw,
    pub uniforms: WorldLodDrawUniform,
    pub pass: WorldLodPassClass,
    pub material_contract: WorldLodMaterialContract,
}

/// Complete semantic classification of the DH work visible in one frame. The
/// planner owns no rendering policy beyond the source layer contract: the
/// caller receives one admitted stream for every visible segment.
#[derive(Clone, Debug, Default)]
pub(crate) struct WorldLodFramePlan {
    pub opaque_draws: Vec<WorldLodOpaqueDraw>,
    pub transparent_draws: Vec<WorldLodTransparentDraw>,
    pub water_draws: Vec<WorldLodWaterDraw>,
}

/// A frame plan contains only copied DH draw classifications and uniforms.
/// Reuse its vectors between ordinary frames, but do not retain an accidental
/// very large visibility spike indefinitely on the render thread.
pub(super) const MAX_REUSABLE_WORLD_LOD_PLAN_ELEMENTS: usize = 4096;

impl WorldLodFramePlan {
    pub(crate) fn prepare_for_reuse(&mut self) {
        clear_reusable_world_lod_plan_vec(&mut self.opaque_draws);
        clear_reusable_world_lod_plan_vec(&mut self.transparent_draws);
        clear_reusable_world_lod_plan_vec(&mut self.water_draws);
    }
}

pub(super) fn clear_reusable_world_lod_plan_vec<T>(draws: &mut Vec<T>) {
    if draws.capacity() > MAX_REUSABLE_WORLD_LOD_PLAN_ELEMENTS {
        *draws = Vec::new();
    } else {
        draws.clear();
    }
}

pub(crate) fn plan_world_lod_frame(
    frame: &WorldLodRenderFrame,
    draws: &[WorldLodGpuDraw],
) -> GalResult<WorldLodFramePlan> {
    plan_world_lod_frame_with_camera(frame, draws, [0.0; 3])
}

/// Resolves visible DH draws using the copied semantic camera position. This
/// is intentionally a frontend transform input, not a backend or FFI detail.
pub(crate) fn plan_world_lod_frame_with_camera(
    frame: &WorldLodRenderFrame,
    draws: &[WorldLodGpuDraw],
    camera_world_position: [f32; 3],
) -> GalResult<WorldLodFramePlan> {
    let mut plan = WorldLodFramePlan {
        opaque_draws: Vec::with_capacity(draws.len()),
        transparent_draws: Vec::new(),
        water_draws: Vec::new(),
    };
    plan_world_lod_frame_with_camera_into(frame, draws, camera_world_position, &mut plan)?;
    Ok(plan)
}

/// Resolves visible DH draws into caller-owned scratch storage. Only bounded
/// vector capacity survives the call; classifications, uniforms, and ordering
/// are rebuilt from the current copied frame and camera semantics.
pub(crate) fn plan_world_lod_frame_with_camera_into(
    frame: &WorldLodRenderFrame,
    draws: &[WorldLodGpuDraw],
    camera_world_position: [f32; 3],
    plan: &mut WorldLodFramePlan,
) -> GalResult<()> {
    plan.prepare_for_reuse();
    for &draw in draws {
        match draw.layer {
            WORLD_LOD_LAYER_OPAQUE => plan.opaque_draws.push(admit_world_lod_draw_with_camera(
                frame,
                draw,
                camera_world_position,
            )?),
            WORLD_LOD_LAYER_TRANSPARENT_SIDE | WORLD_LOD_LAYER_TRANSPARENT_UP => {
                plan.transparent_draws
                    .push(admit_world_lod_transparent_draw_with_camera(
                        frame,
                        draw,
                        camera_world_position,
                    )?);
            }
            WORLD_LOD_LAYER_TRANSPARENT_WATER_UP => {
                plan.water_draws
                    .push(admit_world_lod_water_draw_with_camera(
                        frame,
                        draw,
                        camera_world_position,
                    )?);
            }
            layer => {
                return Err(GalError::invalid_argument(
                    WorldLodAdmissionError::UnknownLayer(layer).to_string(),
                ));
            }
        }
    }
    plan.transparent_draws.sort_by_key(|draw| {
        (
            draw.draw.order,
            draw.draw.layer,
            draw.draw.column_key,
            draw.draw.segment_index,
        )
    });
    plan.water_draws.sort_by_key(|draw| {
        (
            draw.draw.order,
            draw.draw.layer,
            draw.draw.column_key,
            draw.draw.segment_index,
        )
    });
    Ok(())
}

pub(crate) fn admit_world_lod_water_draw_with_camera(
    frame: &WorldLodRenderFrame,
    draw: WorldLodGpuDraw,
    camera_world_position: [f32; 3],
) -> GalResult<WorldLodWaterDraw> {
    if draw.layer != WORLD_LOD_LAYER_TRANSPARENT_WATER_UP {
        return Err(GalError::invalid_argument(
            "Distant Horizons water-surface draw received a non-water layer",
        ));
    }
    let uniforms =
        WorldLodDrawUniform::from_semantics_with_camera(frame, draw, camera_world_position)?;
    Ok(WorldLodWaterDraw {
        draw,
        uniforms,
        pass: WorldLodPassClass::WaterSurface,
        material_contract: WorldLodMaterialContract::WATER_SURFACE,
    })
}

pub(crate) fn admit_world_lod_draw(
    frame: &WorldLodRenderFrame,
    draw: WorldLodGpuDraw,
) -> GalResult<WorldLodOpaqueDraw> {
    admit_world_lod_draw_with_camera(frame, draw, [0.0; 3])
}

pub(crate) fn admit_world_lod_draw_with_camera(
    frame: &WorldLodRenderFrame,
    draw: WorldLodGpuDraw,
    camera_world_position: [f32; 3],
) -> GalResult<WorldLodOpaqueDraw> {
    let pass = match draw.layer {
        WORLD_LOD_LAYER_OPAQUE => WorldLodPassClass::Opaque,
        WORLD_LOD_LAYER_TRANSPARENT_SIDE
        | WORLD_LOD_LAYER_TRANSPARENT_UP
        | WORLD_LOD_LAYER_TRANSPARENT_WATER_UP => {
            return Err(GalError::unsupported_feature(
                "Distant Horizons transparent draw must use the explicit transparent admission path",
            ));
        }
        layer => {
            return Err(GalError::invalid_argument(
                WorldLodAdmissionError::UnknownLayer(layer).to_string(),
            ));
        }
    };
    // Frame validation happens before draw resolution. Preserve that division:
    // admission only classifies the semantic layer and never hides malformed
    // render-frame state behind an "unsupported" result.
    let uniforms =
        WorldLodDrawUniform::from_semantics_with_camera(frame, draw, camera_world_position)?;
    Ok(WorldLodOpaqueDraw {
        draw,
        uniforms,
        pass,
        material_contract: WorldLodMaterialContract::OPAQUE,
    })
}

pub(crate) fn admit_world_lod_transparent_draw(
    frame: &WorldLodRenderFrame,
    draw: WorldLodGpuDraw,
) -> GalResult<WorldLodTransparentDraw> {
    admit_world_lod_transparent_draw_with_camera(frame, draw, [0.0; 3])
}

pub(crate) fn admit_world_lod_transparent_draw_with_camera(
    frame: &WorldLodRenderFrame,
    draw: WorldLodGpuDraw,
    camera_world_position: [f32; 3],
) -> GalResult<WorldLodTransparentDraw> {
    let (pass, material_contract) = match draw.layer {
        WORLD_LOD_LAYER_TRANSPARENT_SIDE => (
            WorldLodPassClass::TransparentSide,
            WorldLodMaterialContract::TRANSPARENT_SIDE,
        ),
        WORLD_LOD_LAYER_TRANSPARENT_UP => (
            WorldLodPassClass::TransparentUp,
            WorldLodMaterialContract::TRANSPARENT_UP,
        ),
        WORLD_LOD_LAYER_TRANSPARENT_WATER_UP => {
            return Err(GalError::unsupported_feature(
                WorldLodAdmissionError::WaterLayerRequiresWaterPath(draw.layer).to_string(),
            ));
        }
        WORLD_LOD_LAYER_OPAQUE => {
            return Err(GalError::invalid_argument(
                "Distant Horizons opaque draw must use the opaque admission path",
            ));
        }
        layer => {
            return Err(GalError::invalid_argument(
                WorldLodAdmissionError::UnknownLayer(layer).to_string(),
            ));
        }
    };
    let uniforms =
        WorldLodDrawUniform::from_semantics_with_camera(frame, draw, camera_world_position)?;
    Ok(WorldLodTransparentDraw {
        draw,
        uniforms,
        pass,
        material_contract,
    })
}

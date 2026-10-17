//! Plan records shared by the named-source families (materials, lines, glint, writers).

use super::*;

/// One complete private source-derived terrain frame handoff. It keeps the
/// frame's semantic draws inseparable from the upload transaction that makes
/// their geometry, uniforms, and instance stream visible to the same GAL
/// submission.
pub(crate) struct PreparedLoweredSourceTerrainFramePlan {
    pub(in crate::render::worldrender) frame_id: u64,
    pub(in crate::render::worldrender) draws: Vec<TerrainMeshDraw>,
    // A selected source frame can be DH-only. In that case the shared named
    // color transaction still records and submits, but no indexed near-terrain
    // stream range was reserved to confirm.
    pub(in crate::render::worldrender) transaction: Option<SourceTerrainFrameTransaction>,
}

/// One ordered `gbuffers_textured` source writer. Its target and resource
/// data are owned entirely by Rust and share the enclosing source frame's
/// transaction; it cannot become an after-final overlay or a second submit.
pub(crate) struct PreparedNamedSourceTexturedMaterialFramePlan {
    pub(in crate::render::worldrender) targets: TerrainSourceColorPassTargets,
    pub(in crate::render::worldrender) draws: Vec<TexturedMaterialSourceDraw>,
}

/// Adjacent material batches sharing one source writer and output schema.
/// Groups retain source order; glyph and particle writers cannot share targets
/// just because both consume the compact quad storage.
pub(in crate::render::worldrender) struct SourceMaterialProgramGroup {
    pub(in crate::render::worldrender) program: std::sync::Arc<LoweredTexturedMaterialSourceProgram>,
    pub(in crate::render::worldrender) batches: Vec<SourceTexturedMaterialBatch>,
    pub(in crate::render::worldrender) targets: TerrainSourceColorPassTargets,
    pub(in crate::render::worldrender) resources: TerrainSourceOwnedResourceSet,
    pub(in crate::render::worldrender) formats: Vec<ColorFormat>,
}

/// One ordered `gbuffers_weather` source writer. It shares the compact
/// Rust-owned material stream with generic textured material, but retains a
/// separately lowered source program, output contract, and alpha-over pass.
pub(crate) struct PreparedNamedSourceWeatherFramePlan {
    pub(in crate::render::worldrender) targets: TerrainSourceColorPassTargets,
    pub(in crate::render::worldrender) draws: Vec<TexturedMaterialSourceDraw>,
}

/// One ordered `gbuffers_clouds` source writer. It uses the same owned
/// compact material stream but remains a distinct source program and pass.
pub(crate) struct PreparedNamedSourceCloudFramePlan {
    pub(in crate::render::worldrender) targets: TerrainSourceColorPassTargets,
    pub(in crate::render::worldrender) draws: Vec<TexturedMaterialSourceDraw>,
}

/// Block-selection outlines through the pack's `gbuffers_line`. Iris draws
/// ordinary-block outlines after the opaque flush (before deferred) and
/// translucent-block outlines after translucent terrain.
pub(crate) struct PreparedNamedSourceLineFramePlan {
    pub(in crate::render::worldrender) targets: TerrainSourceColorPassTargets,
    pub(in crate::render::worldrender) opaque_draws: Vec<TexturedMaterialSourceDraw>,
    pub(in crate::render::worldrender) translucent_draws: Vec<TexturedMaterialSourceDraw>,
}

/// Block-breaking progress through the pack's `gbuffers_damagedblock`. Iris
/// draws the crumbling buffer after the opaque flush and block outline and
/// before `beginTranslucents` (hand, depth copies, deferred).
pub(crate) struct PreparedNamedSourceDamagedBlockFramePlan {
    pub(in crate::render::worldrender) targets: TerrainSourceColorPassTargets,
    pub(in crate::render::worldrender) draws: Vec<TexturedMaterialSourceDraw>,
}

/// `gbuffers_armor_glint` over already drawn entity or hand geometry. Iris
/// flushes world glint after the entity/block-entity sheets and hand glint
/// inside the hand pass, both with vanilla's GLINT state.
pub(crate) struct PreparedNamedSourceGlintFramePlan {
    pub(in crate::render::worldrender) targets: TerrainSourceColorPassTargets,
    pub(in crate::render::worldrender) draws: Vec<EntitySourceDraw>,
}

/// One run of copied crumbling quads sharing a destroy-stage texture and cull
/// policy.
pub(crate) struct SourceDamagedBlockBatch {
    pub(in crate::render::worldrender) texture_id: u32,
    pub(in crate::render::worldrender) cull_policy: u32,
    pub(in crate::render::worldrender) primitives: Vec<TexturedMaterialSourcePrimitive>,
}

/// One ordered run of block-selection segments sharing pass placement and
/// depth policy.
pub(crate) struct SourceLineBatch {
    pub(in crate::render::worldrender) translucent_target: bool,
    pub(in crate::render::worldrender) depth_policy: u32,
    pub(in crate::render::worldrender) primitives: Vec<TexturedMaterialSourcePrimitive>,
}

/// Copies semantic outline segments into the compact material stream as
/// vanilla `Mode.LINES` quads: each endpoint twice (`s, s, e, e`) with the
/// segment direction as its normal, so the pack's `gl_VertexID` parity picks
/// the side exactly as on OpenGL. Vanilla's `VIEW_OFFSET_Z_LAYERING`
/// (perspective `1 - 1/4096` model-view scale) is folded into the copied
/// camera-relative position and direction.
pub(crate) fn source_line_batches(frame: &WorldPrimitiveFrame) -> GalResult<Vec<SourceLineBatch>> {
    const LAYERING_SCALE: f32 = 1.0 - 1.0 / 4096.0;
    let mut batches: Vec<SourceLineBatch> = Vec::new();
    for (index, segment) in frame.segments.iter().enumerate() {
        let delta = [
            segment.end[0] - segment.start[0],
            segment.end[1] - segment.start[1],
            segment.end[2] - segment.start[2],
        ];
        let length = (delta[0] * delta[0] + delta[1] * delta[1] + delta[2] * delta[2]).sqrt();
        if !length.is_finite() || length <= 0.0 {
            return Err(GalError::invalid_argument(format!(
                "block-selection segment {index} has no finite direction",
            )));
        }
        let normal = delta.map(|component| component / length * LAYERING_SCALE);
        let vertex = |position: [f32; 3]| TexturedMaterialSourceVertex {
            camera_relative_position: position.map(|component| component * LAYERING_SCALE),
            texture_uv: [0.0, 0.0],
            source_color_argb: segment.color_argb,
            packed_light: 0x00F0_00F0,
            geometric_normal: normal,
        };
        let primitive = TexturedMaterialSourcePrimitive {
            position_space: TexturedMaterialPositionSpace::CameraRelative,
            texture_coordinates: TexturedMaterialTextureCoordinates::MinecraftBlockAtlas,
            winding: TexturedMaterialWinding::CounterClockwise,
            vertices: [
                vertex(segment.start),
                vertex(segment.start),
                vertex(segment.end),
                vertex(segment.end),
            ],
        };
        let translucent_target = segment.style & WORLD_LINE_STYLE_FLAG_TRANSLUCENT_TARGET != 0;
        match batches.last_mut() {
            Some(batch)
                if batch.translucent_target == translucent_target
                    && batch.depth_policy == segment.depth_policy =>
            {
                batch.primitives.push(primitive);
            }
            _ => batches.push(SourceLineBatch {
                translucent_target,
                depth_policy: segment.depth_policy,
                primitives: vec![primitive],
            }),
        }
    }
    Ok(batches)
}

/// One ordered `gbuffers_entities` source writer. Its indexed geometry,
/// entity identity, and local material are all Rust-owned semantic resources;
/// the enclosing source transaction remains the sole owner of submission,
/// history, and presentation.
pub(crate) struct PreparedNamedSourceEntityFramePlan {
    pub(in crate::render::worldrender) targets: TerrainSourceColorPassTargets,
    pub(in crate::render::worldrender) draws: Vec<EntitySourceDraw>,
}

/// One ordered `gbuffers_hand` source writer. It intentionally shares the
/// indexed local-material stream implementation with entity meshes while
/// retaining an isolated depth target and pass phase. The enclosing frame plan
/// remains the only submission/presentation owner.
pub(crate) struct PreparedNamedSourceHandFramePlan {
    pub(in crate::render::worldrender) targets: TerrainSourceColorPassTargets,
    pub(in crate::render::worldrender) draws: Vec<EntitySourceDraw>,
    /// Draws of a hand whose held stack is a translucent-layer block item.
    /// Iris skips that whole hand in the solid hand pass and draws it in
    /// `HandRenderer.renderTranslucent`, after every world writer and just
    /// before the composite chain, into the main depth.
    pub(in crate::render::worldrender) translucent_draws: Vec<EntitySourceDraw>,
    pub(in crate::render::worldrender) copies_world_depth: bool,
}

#[derive(Clone, Copy)]
pub(crate) enum SourceMaterialWriterKind {
    TexturedMaterial,
    Weather,
    Clouds,
    Lines,
    DamagedBlock,
}

/// Frozen's authoritative frame graph executes translucent terrain, particles,
/// clouds, then weather after the main opaque/entity pass.  These writers all
/// compose over the same named outputs, so their order is observable and must
/// remain explicit rather than following the incidental order in which Java
/// happened to extract their semantics.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) enum VanillaPostTerrainSourceWriter {
    TranslucentTerrain,
    TexturedMaterial,
    Clouds,
    Weather,
}

pub(crate) const VANILLA_POST_TERRAIN_SOURCE_WRITER_ORDER: [VanillaPostTerrainSourceWriter; 4] = [
    VanillaPostTerrainSourceWriter::TranslucentTerrain,
    VanillaPostTerrainSourceWriter::TexturedMaterial,
    VanillaPostTerrainSourceWriter::Clouds,
    VanillaPostTerrainSourceWriter::Weather,
];

impl PreparedLoweredSourceTerrainFramePlan {
    pub(crate) fn into_submission_parts(
        self,
    ) -> (
        Vec<TerrainMeshDraw>,
        Vec<CommandOp>,
        Option<SourceTerrainFrameSubmission>,
    ) {
        match self.transaction {
            Some(transaction) => {
                let (operations, submission) = transaction.into_submission_parts();
                (self.draws, operations, Some(submission))
            }
            None => (self.draws, Vec::new(), None),
        }
    }
}

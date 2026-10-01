//! Per-frame draws, targets and uniforms the world renderer hands the pack runtime.

use super::*;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) enum TerrainMaterialPassMode {
    Opaque,
    Cutout,
    Translucent,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(super) enum TerrainGraphIsolation {
    Full,
    TerrainOnly,
    GBufferNoShadow,
    TerrainPlusShadow,
    FullDrawsSkipped,
}

impl TerrainGraphIsolation {
    pub(super) fn from_env() -> Self {
        match std::env::var("MATTMC_RUST_SHADER_GRAPH_ISOLATION")
            .unwrap_or_default()
            .trim()
        {
            "terrain-only" => Self::TerrainOnly,
            "terrain-plus-gbuffer-no-shadow" | "gbuffer-no-shadow" => Self::GBufferNoShadow,
            "terrain-plus-shadow" => Self::TerrainPlusShadow,
            "full-draws-skipped" => Self::FullDrawsSkipped,
            _ => Self::Full,
        }
    }
}

#[derive(Clone, Debug)]
pub(crate) struct TerrainMeshDraw {
    /// Optional source-specific shadow binding. Built-in draws leave this
    /// unset and reuse their terrain mesh binding for the minimal shadow pass.
    /// A lowered source shadow pair supplies its own program, scalar uniforms,
    /// and semantic set-one resources without borrowing terrain-pass state.
    pub shadow: Option<TerrainShadowDraw>,
    pub pipeline: Handle,
    /// Optional direct-DH pipeline variant that writes the private DH depth
    /// target. Ordinary terrain and source-derived draws leave this unset.
    pub offscreen_pipeline: Option<Handle>,
    pub pipeline_layout: Handle,
    pub resource_set: Handle,
    /// Dynamic offsets required by the semantic mesh resource set. Fixture
    /// meshes use one streamed-instance offset; source-derived terrain sets
    /// may own static bindings and therefore require none.
    pub resource_set_dynamic_offsets: SmallVec<[u64; 3]>,
    /// Optional shader-pack-owned semantic resources. This is distinct from
    /// the mesh/material set and intentionally carries only GAL handles; the
    /// frontend never sees backend state or shader-pack internals.
    pub shader_resource_set: Option<TerrainShaderResourceSet>,
    pub index_buffer: Handle,
    pub index_offset: u64,
    pub index_type: IndexType,
    pub index_count: u32,
    pub instance_count: u32,
    /// Optional address of one packed VkDrawIndexedIndirectCommand-compatible
    /// record. Consecutive draws with identical bindings are coalesced by the
    /// runtime into one backend-neutral multidraw command.
    pub indexed_indirect: Option<TerrainIndexedIndirect>,
    /// Semantic producer stratum.  The terrain graph uses this to keep
    /// translucent entity meshes out of the deferred capture when Fabulous
    /// owns their named `item_entity` attachment.
    pub stratum: u32,
    pub material_mode: TerrainMaterialPassMode,
    /// The pass contract makes shadow participation explicit. A draw with an
    /// unavailable shadow program is skipped only when its semantic material
    /// route declares that omission up front; missing shadow bindings for
    /// ordinary terrain remain a hard error.
    pub shadow_participation: TerrainShadowParticipation,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) struct TerrainIndexedIndirect {
    pub buffer: Handle,
    pub offset: u64,
}

/// A direct world-material batch which must be composed after deferred terrain
/// lighting. Unlike terrain mesh draws this record does not participate in
/// G-buffer or shadow production; it keeps the already-decoded semantic
/// material resource binding and depth policy explicit while the runtime owns
/// the graph placement.
#[derive(Clone, Debug)]
pub(crate) struct TerrainForwardMaterialDraw {
    pub pipeline: Handle,
    pub pipeline_layout: Handle,
    pub resource_set: Handle,
    /// Optional Rust-owned semantic resource set declared by this direct
    /// material pipeline (currently vanilla weather's copied lightmap).
    /// It is never a Java or backend-native handle.
    pub shader_resource_set: Option<TerrainShaderResourceSet>,
    pub index_buffer: Handle,
    pub index_offset: u64,
    pub index_type: IndexType,
    pub index_count: u32,
    pub instance_count: u32,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) enum TerrainShadowParticipation {
    Required,
    Unavailable,
}

#[derive(Clone, Debug)]
pub(crate) struct TerrainShadowDraw {
    pub pipeline: Handle,
    pub pipeline_layout: Handle,
    pub resource_set: Handle,
    pub resource_set_dynamic_offsets: SmallVec<[u64; 3]>,
    pub shader_resource_set: Option<TerrainShaderResourceSet>,
}

/// A caster admitted only to the shadow scene. It carries no color pipeline
/// or color target state, so an off-camera section cannot accidentally enter
/// the G-buffer merely because its immutable mesh is resident.
#[derive(Clone, Debug)]
pub(crate) struct TerrainShadowMeshDraw {
    pub shadow: TerrainShadowDraw,
    pub index_buffer: Handle,
    pub index_offset: u64,
    pub index_type: IndexType,
    pub index_count: u32,
    pub instance_count: u32,
    pub indexed_indirect: Option<TerrainIndexedIndirect>,
    pub material_mode: TerrainMaterialPassMode,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) struct TerrainShaderResourceSet {
    pub set_index: u32,
    pub set: Handle,
}

/// A generation-coherent ordinary-terrain lightmap descriptor. The descriptor
/// belongs to the same Rust residency generation as its sampled view, so the
/// runtime retires it before replacing that view.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) struct VanillaLightmapResourceSet {
    pub world_generation: u64,
    pub lightmap_generation: u64,
    pub set: Handle,
}

/// One draw in the source-derived generic textured-material stage. It is
/// intentionally separate from [`TerrainMeshDraw`]: material vertices are a
/// compact explicit source stream, not indexed terrain sections, and this
/// writer must never be selected by terrain material-mode filtering.
#[derive(Clone, Debug)]
pub(crate) struct TexturedMaterialSourceDraw {
    pub pipeline: Handle,
    pub pipeline_layout: Handle,
    pub resource_set: Handle,
    pub resource_set_dynamic_offsets: Vec<u64>,
    pub shader_resource_set: Option<TerrainShaderResourceSet>,
    /// The source vertex preamble expands each compact quad to two triangles
    /// from `gl_VertexIndex`. Keeping this a direct draw avoids inventing an
    /// indexed terrain buffer or a backend-specific index upload for generic
    /// source-material primitives.
    pub vertices: u32,
}

/// One indexed/instanced draw in the source-derived entity stage. It cannot
/// be confused with terrain or generic textured-material work: entity-local
/// texture and Rust-resolved identity are both baked into its owning set-one
/// resource contract before this record is created.
#[derive(Clone, Debug)]
pub(crate) struct EntitySourceDraw {
    pub pipeline: Handle,
    pub pipeline_layout: Handle,
    pub resource_set: Handle,
    pub resource_set_dynamic_offsets: Vec<u64>,
    pub shader_resource_set: TerrainShaderResourceSet,
    pub index_buffer: Handle,
    pub index_offset: u64,
    pub index_type: IndexType,
    pub index_count: u32,
    pub instance_count: u32,
}

/// A complete Rust-owned semantic binding for a terrain program requirement.
/// The contained handles are GAL resources visible only inside Rust frontend
/// and runtime code; Java and the shader-pack contract never see them.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) struct TerrainShaderProgramBinding {
    pub resource: TerrainProgramResource,
    pub resource_layout: Handle,
    pub resource_set: TerrainShaderResourceSet,
    pub resource_generation: u64,
}

/// An indivisible built-in candidate-subset terrain program and its matching
/// semantic shader resource set. It exercises resource-generation coherence
/// in focused tests, but is not lowered shader-pack source execution. The
/// latter remains unavailable until its source vertex and semantic resource
/// interfaces are assembled into explicit GAL layouts.
#[derive(Clone, Debug)]
pub(crate) struct TerrainSourceProgramCandidate {
    pub program: TerrainMaterialProgram,
    pub binding: Option<TerrainShaderProgramBinding>,
}

/// Fully source-derived, backend-neutral inputs needed to create an owned
/// colored-light runtime for one world/resource generation. It is deliberately
/// preparation data only: constructing it cannot allocate a native resource,
/// select a shader program, or alter route ownership.
#[derive(Clone, Debug)]
pub(crate) struct TerrainColoredLightPreparation {
    pub descriptor: VoxelLightVolumeDescriptor,
    pub materials: VoxelMaterialMap,
    pub emission: VoxelEmissionTable,
}

#[derive(Clone, Copy, Debug)]
pub(crate) struct TerrainTranslucentCaptureTargets {
    pub extent: Extent3d,
    pub color_texture: Handle,
    pub color_view: Handle,
    pub depth_texture: Handle,
    pub depth_view: Handle,
    pub target: Handle,
    pub pass: Handle,
}

#[derive(Clone, Copy)]
pub(crate) struct TerrainRuntimeTargets {
    pub shadow_depth_texture: Handle,
    pub shadow_depth_view: Handle,
    pub shadow_color_texture: Handle,
    pub shadow_color_view: Handle,
    pub shadow_light_shaft_texture: Handle,
    pub shadow_light_shaft_view: Handle,
    pub shadow_target: Handle,
    pub shadow_pass: Handle,
    pub albedo_texture: Handle,
    pub albedo_view: Handle,
    pub normal_texture: Handle,
    pub normal_view: Handle,
    pub material_light_texture: Handle,
    pub material_light_view: Handle,
    pub world_position_texture: Handle,
    pub world_position_view: Handle,
    pub depth_texture: Handle,
    pub depth_view: Handle,
    /// Rust-owned depth snapshots retained for source-derived terrain passes.
    /// The plan controls their validity; these handles alone do not admit a
    /// selected source route or make uninitialized depth observable.
    pub depth_history: TerrainDepthHistoryTargets,
    pub target: Handle,
    pub g_buffer_pass: Handle,
    pub deferred_lit_texture: Handle,
    pub deferred_lit_view: Handle,
    pub deferred_lit_target: Handle,
    pub deferred_lighting_pass: Handle,
    pub deferred_lighting_pipeline: Handle,
    pub deferred_lighting_resource_set: Handle,
    pub translucent_target: Handle,
    pub translucent_pass: Handle,
    /// Optional isolated color/depth capture for translucent terrain. The normal
    /// deferred path still writes `deferred_lit`; this target preserves only
    /// the translucent draw stream for a later explicit Fabulous handoff.
    pub translucent_capture: Option<TerrainTranslucentCaptureTargets>,
    pub translucent_capture_initialized: bool,
    pub composite0_texture: Handle,
    pub composite0_view: Handle,
    pub composite0_target: Handle,
    pub composite0_pass: Handle,
    pub composite0_pipeline: Handle,
    pub composite0_resource_set: Handle,
    pub composite1_texture: Handle,
    pub composite1_view: Handle,
    pub composite1_target: Handle,
    pub composite1_pass: Handle,
    pub composite1_pipeline: Handle,
    pub composite1_resource_set: Handle,
    pub final_pass: Handle,
    pub final_pipeline: Handle,
    pub final_resource_set: Handle,
    pub screen_pipeline_layout: Handle,
    pub composite_uniform_buffer: Handle,
    pub shadow_targets_initialized: bool,
}

/// The source-derived shadow writer's complete Rust-owned target contract.
/// It is intentionally smaller than the whole terrain graph: normal terrain
/// and Distant Horizons source passes need only these explicit shadow
/// attachments before they write pack-named color targets. No Iris state,
/// attachment-number convention, or backend object escapes through it.
#[derive(Clone, Copy, Debug)]
pub(crate) struct TerrainSourceShadowPassTargets {
    pub shadow_depth_texture: Handle,
    /// Receives the opaque/cutout shadow depth before translucent casters
    /// (Iris `shadowtex1`). NULL keeps the legacy single-depth behaviour.
    pub shadow_depth_opaque_texture: Handle,
    pub shadow_extent: Extent3d,
    pub shadow_depth_view: Handle,
    pub shadow_color_texture: Handle,
    pub shadow_color_view: Handle,
    pub shadow_light_shaft_texture: Handle,
    pub shadow_light_shaft_view: Handle,
    pub shadow_target: Handle,
    pub shadow_pass: Handle,
    pub initialized: bool,
}

impl From<TerrainRuntimeTargets> for TerrainSourceShadowPassTargets {
    fn from(targets: TerrainRuntimeTargets) -> Self {
        Self {
            shadow_depth_texture: targets.shadow_depth_texture,
            shadow_depth_opaque_texture: Handle::NULL,
            shadow_extent: Extent3d { width: 0, height: 0, depth: 1 },
            shadow_depth_view: targets.shadow_depth_view,
            shadow_color_texture: targets.shadow_color_texture,
            shadow_color_view: targets.shadow_color_view,
            shadow_light_shaft_texture: targets.shadow_light_shaft_texture,
            shadow_light_shaft_view: targets.shadow_light_shaft_view,
            shadow_target: targets.shadow_target,
            shadow_pass: targets.shadow_pass,
            initialized: targets.shadow_targets_initialized,
        }
    }
}

/// The bounded normal-terrain portion of a selected source frame. Color
/// attachments come from the pack's named Rust-owned target generation,
/// while depth remains an explicit Rust-owned world attachment. This is not
/// a general G-buffer schema and deliberately carries no Iris or backend
/// state.
#[derive(Clone, Debug)]
pub(crate) struct TerrainSourceColorPassTargets {
    /// The source-stage ordering contract for this writer. Bootstrap terrain
    /// initializes pack-declared attachments and the main depth image;
    /// translucency subsequently loads both. This is semantic pass ordering,
    /// not an attachment-slot or backend-state convention.
    pub phase: TerrainSourceColorPassPhase,
    pub color_attachments: Vec<TerrainSourceColorAttachment>,
    /// Per-frame semantic clear inputs. The named attachment retains the
    /// source declaration; this supplies only dynamic fog for its portable
    /// primary-color default.
    pub clear_values: ShaderPackColorBootstrapClearValues,
    pub depth_texture: Handle,
    pub depth_view: Handle,
    pub target: Handle,
    pub pass: Handle,
}

/// The two world-material writers currently admitted by the source-derived
/// terrain graph. Keeping their load/store semantics explicit prevents a
/// future translucent pass from clearing opaque/cutout output or rewriting
/// depth as if it were the first terrain writer.
#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub(crate) enum TerrainSourceColorPassPhase {
    Bootstrap,
    /// A source-defined sky initializer has already written the pack's named
    /// primary color. Opaque/cutout terrain must load that attachment while
    /// preserving the normal bootstrap behavior for every other target and
    /// for depth.
    BootstrapAfterSky,
    /// The distinct source-derived `gbuffers_textured` writer runs after
    /// opaque/cutout terrain against the same named color/depth generation.
    /// It always loads existing attachments: generic material cannot
    /// bootstrap, clear, or silently replace terrain output.
    TexturedMaterial,
    /// `gbuffers_weather` is a distinct source-defined alpha-over writer.
    /// It shares named targets with terrain but never bootstraps or clears.
    Weather,
    /// `gbuffers_clouds` is a separate source-defined alpha-over writer.
    /// It shares the named terrain targets but has its own source contract,
    /// so cloud work cannot be relabelled as weather or generic material.
    Clouds,
    /// `gbuffers_line` block-selection outlines: a separate source-defined
    /// alpha-over writer that loads the named G-buffer targets.
    Lines,
    /// `gbuffers_damagedblock` block-breaking progress: a separate
    /// source-defined multiply writer that loads the named G-buffer targets
    /// after the opaque/entity writers and before deferred.
    DamagedBlock,
    /// `gbuffers_armor_glint` over world entity/item geometry: load-only,
    /// EQUAL depth against the entity pass's depth.
    EntityGlint,
    /// `gbuffers_armor_glint` over first-person geometry, against the hand
    /// pass's private depth.
    HandGlint,
    /// `gbuffers_entities` consumes an entity-local material texture and
    /// Rust-resolved entity identity. It loads existing named targets and
    /// never bootstraps or clears terrain output.
    Entities,
    /// `gbuffers_hand` is a distinct first-person writer. It preserves the
    /// Rust-owned named color generation. Ordinary D32 hands load an explicit
    /// world-depth copy; optical D24S8 hands retain the private clear path.
    Hands,
    /// The first terrain writer is translucent. It must initialize named
    /// color/depth attachments before alpha-over drawing rather than loading
    /// an image that has not been written by a bootstrap pass.
    TranslucentFirst,
    Translucent,
}

impl TerrainSourceColorPassPhase {
    /// Derives the writer ordering from the lowered source program rather
    /// than a caller-selected material label. The only program carrying the
    /// explicit source alpha/blend contract is the separate translucent
    /// stage; normal terrain remains the bootstrap writer.
    pub(crate) fn for_program(program: &LoweredTerrainSourceProgram) -> Self {
        if program.translucent_raster_state().is_some() {
            Self::Translucent
        } else {
            Self::Bootstrap
        }
    }

    pub(crate) fn compatible_with_program(self, program: &LoweredTerrainSourceProgram) -> bool {
        matches!(
            (self, Self::for_program(program)),
            (Self::Bootstrap, Self::Bootstrap)
                | (Self::BootstrapAfterSky, Self::Bootstrap)
                | (Self::Translucent, Self::Translucent)
                | (Self::TranslucentFirst, Self::Translucent)
        )
    }

    pub(super) fn accepts_material(self, material_mode: TerrainMaterialPassMode) -> bool {
        match self {
            Self::Bootstrap | Self::BootstrapAfterSky => matches!(
                material_mode,
                TerrainMaterialPassMode::Opaque | TerrainMaterialPassMode::Cutout
            ),
            // `gbuffers_textured` has its own semantic stream and draw type.
            // Never let the terrain writer accidentally record a terrain mesh
            // into this load-only pass merely because both are world material.
            Self::TexturedMaterial
            | Self::Weather
            | Self::Clouds
            | Self::Lines
            | Self::DamagedBlock
            | Self::EntityGlint
            | Self::HandGlint
            | Self::Entities
            | Self::Hands => false,
            Self::Translucent => material_mode == TerrainMaterialPassMode::Translucent,
            Self::TranslucentFirst => material_mode == TerrainMaterialPassMode::Translucent,
        }
    }

    pub(super) fn depth_before(self) -> TextureUsageState {
        match self {
            Self::Bootstrap | Self::BootstrapAfterSky => TextureUsageState::Undefined,
            Self::TexturedMaterial
            | Self::Weather
            | Self::Clouds
            | Self::Lines
            | Self::DamagedBlock
            | Self::EntityGlint
            | Self::HandGlint
            | Self::Entities => TextureUsageState::ShaderRead,
            // The optical hand fallback clears its private depth attachment.
            // The ordinary copied-depth path overrides this predecessor.
            Self::Hands => TextureUsageState::Undefined,
            Self::Translucent => TextureUsageState::ShaderRead,
            Self::TranslucentFirst => TextureUsageState::Undefined,
        }
    }

    pub(super) fn color_load_op(self, attachment: &TerrainSourceColorAttachment) -> AttachmentLoadOp {
        match self {
            Self::Bootstrap if attachment.clear_each_frame => AttachmentLoadOp::Clear,
            Self::BootstrapAfterSky
                if attachment.role.shader_pack_color_name() == Some("primary") =>
            {
                AttachmentLoadOp::Load
            }
            Self::BootstrapAfterSky if attachment.clear_each_frame => AttachmentLoadOp::Clear,
            Self::Bootstrap
            | Self::BootstrapAfterSky
            | Self::TexturedMaterial
            | Self::Weather
            | Self::Clouds
            | Self::Lines
            | Self::DamagedBlock
            | Self::EntityGlint
            | Self::HandGlint
            | Self::Entities
            | Self::Hands
            | Self::Translucent => AttachmentLoadOp::Load,
            Self::TranslucentFirst if attachment.clear_each_frame => AttachmentLoadOp::Clear,
            Self::TranslucentFirst => AttachmentLoadOp::Load,
        }
    }

    pub(super) fn depth_load_op(self) -> AttachmentLoadOp {
        match self {
            Self::Bootstrap | Self::BootstrapAfterSky => AttachmentLoadOp::Clear,
            Self::TexturedMaterial
            | Self::Weather
            | Self::Clouds
            | Self::Lines
            | Self::DamagedBlock
            | Self::EntityGlint
            | Self::HandGlint
            | Self::Entities
            | Self::Translucent => AttachmentLoadOp::Load,
            Self::TranslucentFirst => AttachmentLoadOp::Clear,
            // Optical hands retain a private clear; ordinary copied-depth
            // hands override this to Load. Color always loads world output.
            Self::Hands => AttachmentLoadOp::Clear,
        }
    }
}

/// The three semantic images involved in a main-depth snapshot transaction.
/// It is intentionally independent of render targets, attachment slots, and
/// native image identity so both backends consume the same GAL copy contract.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) struct TerrainDepthHistoryTargets {
    pub main_depth_texture: Handle,
    pub before_translucency_texture: Handle,
    pub previous_texture: Handle,
}

/// Per-frame depth-history copy plan supplied by the world frontend. The
/// frontend advances validity only after the combined frame submission is
/// accepted, so this declaration never turns an aborted frame into history.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) struct TerrainDepthHistoryPlan {
    /// Whether the prior `before_translucency` snapshot may be copied into
    /// `previous` before replacing it with this frame's opaque/cutout depth.
    pub prior_before_translucency_valid: bool,
    /// The prior state of the destination snapshot. A valid destination is
    /// transitioned from shader-read; an invalid one starts undefined.
    pub prior_previous_valid: bool,
    pub extent: Extent3d,
}

#[derive(Clone, Copy, Debug)]
pub(crate) struct TerrainRuntimeFrame {
    pub frame_target: Handle,
    pub color_attachment: Handle,
    pub background_color: ClearColor,
    /// A Rust-owned semantic background writer has initialized the complete
    /// G-buffer before opaque terrain. The terrain graph must load rather
    /// than clear it; this is explicit pass ordering, never backend state.
    pub g_buffer_background_initialized: bool,
    pub uniforms: TerrainCompositeUniforms,
    /// Optional private source-depth history. Normal world material output is
    /// unchanged when no source stage declares a need for these snapshots.
    pub depth_history: Option<TerrainDepthHistoryPlan>,
    /// Shadow attachments are persistent graph resources just like the
    /// deferred screen targets; only their first use starts from Undefined.
    pub shadow_targets_initialized: bool,
    /// Composite attachments persist across frames. The first frame starts
    /// from Undefined; later frames begin from ShaderRead after the prior
    /// composite chain's final transition.
    pub screen_targets_initialized: bool,
    /// The isolated translucent capture has a valid ShaderRead layout after
    /// its first completed graph write.
    pub translucent_capture_initialized: bool,
    /// When true, translucent entity meshes are lowered into Fabulous's
    /// external `item_entity` attachment by the enclosing frontend and must
    /// not also be rasterized into either deferred transparency destination.
    pub translucent_entity_external: bool,
    /// When true, translucent terrain is retained only in the explicit
    /// Fabulous `translucent` attachment.  The enclosing handoff composes
    /// that attachment over the copied opaque main image; writing it into the
    /// normal deferred image as well would blend every pane twice.
    pub translucent_terrain_external: bool,
}

#[derive(Clone, Copy, Debug)]
pub(crate) struct TerrainCompositeUniforms {
    pub light_view_projection: [f32; 16],
    pub shadow_params: [f32; 4],
    pub color_grade_params: [f32; 4],
    /// Inverse of the copied game projection. The depth composite uses this
    /// only to reconstruct camera-relative distance from the explicit main
    /// depth attachment; no backend depth/state query is involved.
    pub projection_inverse: [f32; 16],
    /// Copied vanilla fog color plus environmental spherical-fog start.
    pub fog_color_and_environmental_start: [f32; 4],
    /// Environmental end, render-distance cylindrical start/end, and copied
    /// vanilla fog-color alpha for direct Sodium-compatible fog.
    pub fog_ranges: [f32; 4],
}

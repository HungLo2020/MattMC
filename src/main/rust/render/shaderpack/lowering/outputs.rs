//! Fragment output kinds and the lowered vertex/fragment stage records.

use super::*;

/// Raster primitive semantics supplied by a Rust-owned procedural source
/// stage. This selects only owned geometry; it never borrows an Iris vertex
/// stream or backend state.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum FullscreenSourceRasterPrimitive {
    /// The standard three-vertex coverage triangle for deferred, composite,
    /// and final source stages.
    FullscreenTriangle,
    /// Vanilla's top sky disc expanded from its triangle fan into eight owned
    /// wedges. This preserves the source program's real geometric depth field
    /// for `gl_FragCoord.z` ray reconstruction.
    VanillaSkyDisc,
    /// Iris's enclosing octagonal horizon and tiled top/bottom planes, drawn
    /// before the vanilla disc through the same selected sky source.
    ShaderPackHorizon,
    /// Vanilla's sun/moon quad geometry, reconstructed from copied sky
    /// semantics and source-pack configuration. It owns both positions and
    /// UVs; no SkyRenderer buffer, Iris vertex format, or native state is
    /// borrowed by the selected source route. With the celestial selector at
    /// 2 it instead draws SkyRenderer's six-face End sky box (the same
    /// `gbuffers_skytextured` writer Iris uses for the End sky); sun and moon
    /// draws leave the extra faces degenerate.
    VanillaCelestialQuad,
}

impl FullscreenSourceRasterPrimitive {
    pub const fn vertex_count(self) -> u32 {
        match self {
            Self::FullscreenTriangle => 3,
            Self::VanillaSkyDisc => 24,
            Self::ShaderPackHorizon => 2_076,
            Self::VanillaCelestialQuad => 36,
        }
    }
}

/// Named terrain outputs recovered from the audited `DRAWBUFFERS:06` source
/// contract. The identifiers are semantic; only a later pass description maps
/// them to a concrete attachment set.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum TerrainFragmentOutput {
    LitColor,
    MaterialAuxiliary,
    ViewSpaceNormal,
}

/// Named outputs of the source pack's distinct translucent terrain stage.
/// The source locations are retained only while rewriting legacy GLSL; later
/// pass construction consumes the semantic names and never raw DRAWBUFFERS
/// indices.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum TranslucentTerrainFragmentOutput {
    LitColor,
    TranslucencyAuxiliary,
    MaterialAuxiliary,
}

/// Named outputs of `gbuffers_textured`. These have the same GLSL output
/// locations as the selected pack's textured pass, but they are not terrain
/// normals: the final lane carries the pass's translucency auxiliary data.
/// Keeping this separate from both terrain and water prevents a future
/// source-material writer from binding an otherwise compatible target with
/// the wrong semantic interpretation.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum TexturedMaterialFragmentOutput {
    LitColor,
    MaterialAuxiliary,
    TranslucencyAuxiliary,
}

/// The weather stage writes a single lit scene-color output. It is distinct
/// from terrain, water, and generic textured material outputs so later pass
/// scheduling cannot reinterpret its source slot as a G-buffer attachment.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum WeatherFragmentOutput {
    LitColor,
}

/// Named outputs of the source pack's distinct vanilla cloud stage. Clouds
/// share `DRAWBUFFERS:063` with generic textured material, but keep their own
/// names so a future writer cannot bind them as terrain or an overlay.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum CloudFragmentOutput {
    LitColor,
    MaterialAuxiliary,
    TranslucencyAuxiliary,
}

impl TexturedMaterialFragmentOutput {
    pub(super) fn legacy_index(self) -> u32 {
        match self {
            Self::LitColor => 0,
            Self::MaterialAuxiliary => 1,
            Self::TranslucencyAuxiliary => 2,
        }
    }

    pub(super) fn semantic_name(self) -> &'static str {
        match self {
            Self::LitColor => "out_textured_material_lit_color",
            Self::MaterialAuxiliary => "out_textured_material_auxiliary",
            Self::TranslucencyAuxiliary => "out_textured_material_translucency_auxiliary",
        }
    }
}

impl WeatherFragmentOutput {
    pub(super) fn legacy_index(self) -> u32 {
        match self {
            Self::LitColor => 0,
        }
    }

    pub(super) fn semantic_name(self) -> &'static str {
        match self {
            Self::LitColor => "out_weather_lit_color",
        }
    }
}

impl CloudFragmentOutput {
    pub(super) fn legacy_index(self) -> u32 {
        match self {
            Self::LitColor => 0,
            Self::MaterialAuxiliary => 1,
            Self::TranslucencyAuxiliary => 2,
        }
    }

    pub(super) fn semantic_name(self) -> &'static str {
        match self {
            Self::LitColor => "out_cloud_lit_color",
            Self::MaterialAuxiliary => "out_cloud_material_auxiliary",
            Self::TranslucencyAuxiliary => "out_cloud_translucency_auxiliary",
        }
    }
}

impl TranslucentTerrainFragmentOutput {
    pub(super) fn legacy_index(self) -> u32 {
        match self {
            Self::LitColor => 0,
            Self::TranslucencyAuxiliary => 1,
            Self::MaterialAuxiliary => 2,
        }
    }

    pub(crate) fn semantic_name(self) -> &'static str {
        match self {
            Self::LitColor => "out_terrain_lit_color",
            Self::TranslucencyAuxiliary => "out_terrain_translucency_auxiliary",
            Self::MaterialAuxiliary => "out_terrain_material_auxiliary",
        }
    }
}

impl TerrainFragmentOutput {
    pub(super) fn legacy_index(self) -> u32 {
        match self {
            Self::LitColor => 0,
            Self::MaterialAuxiliary => 1,
            Self::ViewSpaceNormal => 2,
        }
    }

    pub(crate) fn semantic_name(self) -> &'static str {
        match self {
            Self::LitColor => "out_terrain_lit_color",
            Self::MaterialAuxiliary => "out_terrain_material_auxiliary",
            Self::ViewSpaceNormal => "out_terrain_view_space_normal",
        }
    }
}

/// Named outputs from a source-derived shadow fragment. These are distinct
/// from terrain G-buffer outputs: a later shadow pass maps them to owned
/// shadow attachments rather than reusing a terrain attachment by index.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ShadowFragmentOutput {
    ShadowColor,
    LightShaftColor,
}

/// The Distant Horizons terrain source writes one lit color target. Its depth
/// is the explicit depth attachment of the DH pass, not a second fragment
/// output. Keeping this distinct from ordinary terrain's G-buffer outputs
/// prevents source lowering from silently changing pack composition rules.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum DistantHorizonsFragmentOutput {
    LitColor,
}

impl DistantHorizonsFragmentOutput {
    pub(super) fn legacy_index(self) -> u32 {
        match self {
            Self::LitColor => 0,
        }
    }

    pub(super) fn semantic_name(self) -> &'static str {
        match self {
            Self::LitColor => "out_distant_horizons_lit_color",
        }
    }
}

impl ShadowFragmentOutput {
    pub(super) fn legacy_index(self) -> u32 {
        match self {
            Self::ShadowColor => 0,
            Self::LightShaftColor => 1,
        }
    }

    pub(super) fn semantic_name(self) -> &'static str {
        match self {
            Self::ShadowColor => "out_shadow_color",
            Self::LightShaftColor => "out_shadow_light_shaft_color",
        }
    }
}

/// Owned intermediate source from one lowering step. It does not indicate
/// executable readiness and has no backend-specific binding information.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LoweredTerrainFragmentSource {
    pub(super) entry_path: String,
    pub(super) source: String,
    pub(super) outputs: Vec<TerrainFragmentOutput>,
    pub(super) remaining_dialect: GlslDialectReport,
}

/// Owned source for the separate translucent terrain fragment stage. It is a
/// preparation artifact only; it carries no framebuffer, blend state, or
/// backend object.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LoweredTranslucentTerrainFragmentSource {
    pub(super) entry_path: String,
    pub(super) source: String,
    pub(super) outputs: Vec<TranslucentTerrainFragmentOutput>,
    pub(super) remaining_dialect: GlslDialectReport,
}

/// Owned fragment source for the generic `gbuffers_textured` pass. This is
/// source preparation only; the named target writer remains a separate,
/// explicitly admitted runtime slice.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LoweredTexturedMaterialFragmentSource {
    pub(super) entry_path: String,
    pub(super) source: String,
    pub(super) outputs: Vec<TexturedMaterialFragmentOutput>,
    pub(super) remaining_dialect: GlslDialectReport,
}

/// Owned source for the selected weather fragment. It remains backend-neutral
/// source preparation: pass targets, blending, and execution are separate
/// runtime responsibilities.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LoweredWeatherFragmentSource {
    pub(super) entry_path: String,
    pub(super) source: String,
    pub(super) outputs: Vec<WeatherFragmentOutput>,
    pub(super) remaining_dialect: GlslDialectReport,
}

/// Owned fragment source for the selected vanilla cloud stage. It owns no
/// target or pipeline and remains distinct from generic material semantics.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LoweredCloudFragmentSource {
    pub(super) entry_path: String,
    pub(super) source: String,
    pub(super) outputs: Vec<CloudFragmentOutput>,
    pub(super) remaining_dialect: GlslDialectReport,
}

/// Owned shadow fragment source after the bounded output lowering step. It
/// has no pipeline, attachment, or backend binding; those require a later
/// complete source shadow-pass contract.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LoweredShadowFragmentSource {
    pub(super) entry_path: String,
    pub(super) source: String,
    pub(super) outputs: Vec<ShadowFragmentOutput>,
    pub(super) remaining_dialect: GlslDialectReport,
}

/// Owned DH fragment source after its legacy output has been given a named
/// semantic meaning. It is intentionally not a terrain G-buffer program and
/// remains unexecutable until the DH color/depth consumer is fully owned.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LoweredDistantHorizonsFragmentSource {
    pub(super) entry_path: String,
    pub(super) source: String,
    pub(super) outputs: Vec<DistantHorizonsFragmentOutput>,
    pub(super) remaining_dialect: GlslDialectReport,
}

/// One manifest-derived semantic target written by a source fullscreen stage.
/// `source_location` is retained only to lower the source text; scheduling and
/// attachment ownership use `role`, never a raw legacy draw-buffer number.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct FullscreenSourceFragmentOutput {
    /// GLSL `gl_FragData` ordinal, preserved as the explicit output location
    /// of the lowered shader.
    pub(super) source_location: u32,
    /// Semantic shader-pack color destination recovered from the source
    /// `DRAWBUFFERS` declaration. This is deliberately distinct from the GLSL
    /// output location: `gl_FragData[0]` may target `colortex3`.
    pub(super) source_slot: u32,
    pub(super) role: TerrainSourceResourceRole,
    pub(super) semantic_name: String,
}

impl FullscreenSourceFragmentOutput {
    pub fn source_location(&self) -> u32 {
        self.source_location
    }

    pub fn source_slot(&self) -> u32 {
        self.source_slot
    }

    pub fn role(&self) -> TerrainSourceResourceRole {
        self.role.clone()
    }

    pub fn semantic_name(&self) -> &str {
        &self.semantic_name
    }
}

/// Owned fullscreen fragment source after legacy outputs have been mapped to
/// pack-declared semantic color roles. It is intentionally distinct from the
/// terrain-mesh fragment types: a fullscreen stage must not inherit a mesh
/// vertex ABI merely because it is a later shader-pack consumer.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LoweredFullscreenSourceFragment {
    pub(super) entry_path: String,
    pub(super) source: String,
    pub(super) outputs: Vec<FullscreenSourceFragmentOutput>,
    pub(super) remaining_dialect: GlslDialectReport,
}

/// Rust-owned source for a standard fullscreen semantic triangle/quad input.
/// The only fixed stream is position plus UV; this source carries no Java,
/// Iris, OpenGL, or backend object identity.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LoweredFullscreenSourceVertex {
    pub(super) entry_path: String,
    pub(super) source: String,
    pub(super) remaining_dialect: GlslDialectReport,
}

/// Owned GLSL 450 vertex source after legacy terrain names have been mapped to
/// the future explicit source-vertex stream. The stream binding is deliberately
/// private preparation: no current render route allocates or binds it.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LoweredTerrainVertexSource {
    pub(super) entry_path: String,
    pub(super) source: String,
    pub(super) remaining_dialect: GlslDialectReport,
}

impl LoweredTerrainVertexSource {
    pub fn entry_path(&self) -> &str {
        &self.entry_path
    }

    pub fn source(&self) -> &str {
        &self.source
    }

    pub fn remaining_dialect(&self) -> &GlslDialectReport {
        &self.remaining_dialect
    }
}

impl LoweredTerrainFragmentSource {
    pub fn entry_path(&self) -> &str {
        &self.entry_path
    }

    pub fn source(&self) -> &str {
        &self.source
    }

    pub fn outputs(&self) -> &[TerrainFragmentOutput] {
        &self.outputs
    }

    pub fn remaining_dialect(&self) -> &GlslDialectReport {
        &self.remaining_dialect
    }
}

impl LoweredTranslucentTerrainFragmentSource {
    pub fn entry_path(&self) -> &str {
        &self.entry_path
    }

    pub fn source(&self) -> &str {
        &self.source
    }

    pub fn outputs(&self) -> &[TranslucentTerrainFragmentOutput] {
        &self.outputs
    }

    pub fn remaining_dialect(&self) -> &GlslDialectReport {
        &self.remaining_dialect
    }
}

impl LoweredTexturedMaterialFragmentSource {
    pub fn entry_path(&self) -> &str {
        &self.entry_path
    }

    pub fn source(&self) -> &str {
        &self.source
    }

    pub fn outputs(&self) -> &[TexturedMaterialFragmentOutput] {
        &self.outputs
    }

    pub fn remaining_dialect(&self) -> &GlslDialectReport {
        &self.remaining_dialect
    }
}

impl LoweredWeatherFragmentSource {
    pub fn entry_path(&self) -> &str {
        &self.entry_path
    }

    pub fn source(&self) -> &str {
        &self.source
    }

    pub fn outputs(&self) -> &[WeatherFragmentOutput] {
        &self.outputs
    }

    pub fn remaining_dialect(&self) -> &GlslDialectReport {
        &self.remaining_dialect
    }
}

impl LoweredCloudFragmentSource {
    pub fn entry_path(&self) -> &str {
        &self.entry_path
    }

    pub fn source(&self) -> &str {
        &self.source
    }

    pub fn outputs(&self) -> &[CloudFragmentOutput] {
        &self.outputs
    }

    pub fn remaining_dialect(&self) -> &GlslDialectReport {
        &self.remaining_dialect
    }
}

impl LoweredShadowFragmentSource {
    pub fn entry_path(&self) -> &str {
        &self.entry_path
    }

    pub fn source(&self) -> &str {
        &self.source
    }

    pub fn outputs(&self) -> &[ShadowFragmentOutput] {
        &self.outputs
    }

    pub fn remaining_dialect(&self) -> &GlslDialectReport {
        &self.remaining_dialect
    }
}

impl LoweredDistantHorizonsFragmentSource {
    pub fn entry_path(&self) -> &str {
        &self.entry_path
    }

    pub fn source(&self) -> &str {
        &self.source
    }

    pub fn outputs(&self) -> &[DistantHorizonsFragmentOutput] {
        &self.outputs
    }

    pub fn remaining_dialect(&self) -> &GlslDialectReport {
        &self.remaining_dialect
    }
}

impl LoweredFullscreenSourceFragment {
    pub fn entry_path(&self) -> &str {
        &self.entry_path
    }

    pub fn source(&self) -> &str {
        &self.source
    }

    pub fn outputs(&self) -> &[FullscreenSourceFragmentOutput] {
        &self.outputs
    }

    pub fn remaining_dialect(&self) -> &GlslDialectReport {
        &self.remaining_dialect
    }
}

impl LoweredFullscreenSourceVertex {
    pub fn entry_path(&self) -> &str {
        &self.entry_path
    }

    pub fn source(&self) -> &str {
        &self.source
    }

    pub fn remaining_dialect(&self) -> &GlslDialectReport {
        &self.remaining_dialect
    }
}

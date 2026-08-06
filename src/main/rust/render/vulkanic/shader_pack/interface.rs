//! Semantic terrain-vertex interface discovery for source-derived programs.
//!
//! Complementary's historical shader sources name OpenGL compatibility
//! attributes. This module translates those names only into reusable terrain
//! semantics. It deliberately does not assign locations, construct a VAO, or
//! retain any Iris/GL state. A later source lowerer must provide all reported
//! semantics through an explicit Rust mesh layout before it can compile or
//! execute the source-derived program.

use std::collections::BTreeSet;

use crate::render::vulkanic::error::{GalError, GalResult};

use super::dialect::source_identifiers;
use super::preprocess::PreprocessedShaderSource;

/// Backend-neutral per-vertex terrain information required by source text.
#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum TerrainVertexSemantic {
    Position,
    AtlasUv,
    LightmapUv,
    VertexColor,
    GeometricNormal,
    MaterialIdentity,
    SpriteMidpoint,
    Tangent,
}

impl TerrainVertexSemantic {
    pub fn semantic_name(self) -> &'static str {
        match self {
            Self::Position => "position",
            Self::AtlasUv => "atlas_uv",
            Self::LightmapUv => "lightmap_uv",
            Self::VertexColor => "vertex_color",
            Self::GeometricNormal => "geometric_normal",
            Self::MaterialIdentity => "material_identity",
            Self::SpriteMidpoint => "sprite_midpoint",
            Self::Tangent => "tangent",
        }
    }
}

/// Immutable requirements observed in one fully preprocessed vertex stage.
/// The entry is retained so a diagnostic can identify the selected pack
/// source rather than referring to a backend pipeline or Java renderer.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TerrainVertexInterface {
    entry_path: String,
    required: BTreeSet<TerrainVertexSemantic>,
}

impl TerrainVertexInterface {
    pub fn entry_path(&self) -> &str {
        &self.entry_path
    }

    pub fn required(&self) -> &BTreeSet<TerrainVertexSemantic> {
        &self.required
    }

    /// Rejects a source stage whose semantic requirements exceed the current
    /// shared world-mesh source preparation. Sprite midpoint and tangent are
    /// derived from the copied indexed quad semantics by Rust, while the
    /// remaining values arrive through the stable mesh transport. This is
    /// intentionally a pre-admission check; it makes no claim that a source
    /// was lowered or compiled.
    pub fn require_current_world_mesh_support(&self) -> GalResult<()> {
        let available = available_world_mesh_source_semantics();
        let missing = self
            .required
            .difference(&available)
            .copied()
            .collect::<Vec<_>>();
        if missing.is_empty() {
            return Ok(());
        }
        Err(GalError::unsupported_feature(format!(
            "terrain source '{}' requires unavailable world-mesh semantics: {}",
            self.entry_path,
            missing
                .iter()
                .map(|semantic| semantic.semantic_name())
                .collect::<Vec<_>>()
                .join(", ")
        )))
    }
}

/// Maps legacy identifiers to their semantic meaning after the ordinary owned
/// preprocessor has selected branches and expanded includes. The mapping is
/// deliberately conservative: unknown names never acquire implicit meaning.
pub fn analyze_terrain_vertex_interface(
    source: &PreprocessedShaderSource,
) -> TerrainVertexInterface {
    let tokens = source_identifiers(source.expanded_source());
    let mut required = BTreeSet::new();
    if tokens.contains("gl_Vertex") || tokens.contains("ftransform") {
        required.insert(TerrainVertexSemantic::Position);
    }
    if tokens.contains("gl_MultiTexCoord0") {
        required.insert(TerrainVertexSemantic::AtlasUv);
    }
    if tokens.contains("gl_MultiTexCoord1") {
        required.insert(TerrainVertexSemantic::LightmapUv);
    }
    if tokens.contains("gl_Color") {
        required.insert(TerrainVertexSemantic::VertexColor);
    }
    if tokens.contains("gl_Normal") {
        required.insert(TerrainVertexSemantic::GeometricNormal);
    }
    if tokens.contains("mc_Entity") {
        required.insert(TerrainVertexSemantic::MaterialIdentity);
    }
    if tokens.contains("mc_midTexCoord") {
        required.insert(TerrainVertexSemantic::SpriteMidpoint);
    }
    if tokens.contains("at_tangent") {
        required.insert(TerrainVertexSemantic::Tangent);
    }
    TerrainVertexInterface {
        entry_path: source.entry_path().to_string(),
        required,
    }
}

/// Semantics transported directly by the versioned `WorldMeshVertex` ABI.
/// Packed block/sky light is represented as a lightmap UV pair at
/// shader-lowering level.
pub fn world_mesh_transport_semantics() -> BTreeSet<TerrainVertexSemantic> {
    BTreeSet::from([
        TerrainVertexSemantic::Position,
        TerrainVertexSemantic::AtlasUv,
        TerrainVertexSemantic::LightmapUv,
        TerrainVertexSemantic::VertexColor,
        TerrainVertexSemantic::GeometricNormal,
        TerrainVertexSemantic::MaterialIdentity,
    ])
}

/// Full semantic coverage available to a future source lowerer. The source
/// mesh expander derives sprite midpoint and tangent from copied indexed quad
/// geometry; this still has no dependency on Iris state or backend objects.
pub fn available_world_mesh_source_semantics() -> BTreeSet<TerrainVertexSemantic> {
    let mut available = world_mesh_transport_semantics();
    available.insert(TerrainVertexSemantic::SpriteMidpoint);
    available.insert(TerrainVertexSemantic::Tangent);
    available
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::render::vulkanic::shader_pack::preprocess::{preprocess_artifact, PreprocessInput};
    use crate::render::vulkanic::shader_pack::source::{ShaderPackSource, ShaderSourceFile};

    fn artifact(source: &str) -> PreprocessedShaderSource {
        let pack = ShaderPackSource::new(
            "test",
            1,
            vec![ShaderSourceFile::new("terrain.vsh", source)],
        )
        .unwrap();
        preprocess_artifact(PreprocessInput {
            source: &pack,
            entry: "terrain.vsh",
            defines: &[],
        })
        .unwrap()
    }

    #[test]
    fn maps_legacy_terrain_attributes_to_semantics_without_locations() {
        let interface = analyze_terrain_vertex_interface(&artifact(
            "attribute vec4 mc_Entity; attribute vec4 mc_midTexCoord; attribute vec4 at_tangent;\n\
             void main() { vec4 c = gl_Color + gl_MultiTexCoord0 + gl_MultiTexCoord1;\n\
             vec3 n = gl_Normal + at_tangent.xyz; gl_Position = ftransform() + gl_Vertex + c; }",
        ));
        assert_eq!(
            BTreeSet::from([
                TerrainVertexSemantic::Position,
                TerrainVertexSemantic::AtlasUv,
                TerrainVertexSemantic::LightmapUv,
                TerrainVertexSemantic::VertexColor,
                TerrainVertexSemantic::GeometricNormal,
                TerrainVertexSemantic::MaterialIdentity,
                TerrainVertexSemantic::SpriteMidpoint,
                TerrainVertexSemantic::Tangent,
            ]),
            *interface.required()
        );
        assert!(interface.require_current_world_mesh_support().is_ok());
        let missing_from_transport = interface
            .required()
            .difference(&world_mesh_transport_semantics())
            .copied()
            .collect::<Vec<_>>();
        assert_eq!(
            vec![
                TerrainVertexSemantic::SpriteMidpoint,
                TerrainVertexSemantic::Tangent
            ],
            missing_from_transport
        );
    }

    #[test]
    fn comments_do_not_invent_terrain_semantics() {
        let interface = analyze_terrain_vertex_interface(&artifact(
            "// mc_Entity mc_midTexCoord at_tangent gl_MultiTexCoord1\nvoid main() {}",
        ));
        assert!(interface.required().is_empty());
    }
}

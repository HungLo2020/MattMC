//! Source-derived contract for generic textured world material.
//!
//! This is deliberately separate from terrain: it records source-pack
//! semantics for standalone textured quads without borrowing an Iris program,
//! framebuffer, or vertex format. Discovery alone cannot admit execution.

use crate::render::vulkanic::error::{GalError, GalResult};

use super::lowering::lower_textured_material_source_pair as lower_textured_material_stages;
pub use super::lowering::LoweredTexturedMaterialSourcePair;
use super::preprocess::preprocess_artifact_with_runtime_options;
use super::source::ShaderPackSource;
use super::terrain_contract::{
    parse_draw_buffers_slots, terrain_source_stages, TerrainPassOutput, TerrainProgramScope,
    TerrainSourceStages,
};

/// Fixed std430 record consumed by the lowered `gbuffers_textured` vertex
/// preamble: position, color, normal, and texture/lightmap coordinates.
/// This is a Rust-owned source-stream ABI, not a Java vertex format or a
/// backend buffer declaration.
pub const TEXTURED_MATERIAL_SOURCE_VERTEX_BYTES: usize = 4 * 4 * std::mem::size_of::<f32>();

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum TexturedMaterialSourceInput {
    MaterialTexture,
    VertexColor,
    PackedLight,
    ViewSpaceNormal,
    CameraAndEnvironment,
    PackNoise,
    MainDepth,
}

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum TexturedMaterialSourceOutput {
    LitColor,
    MaterialAuxiliary,
    TranslucencyAuxiliary,
}

impl TexturedMaterialSourceOutput {
    /// Maps the textured pass's source-declared outputs onto the shared named
    /// shader-pack color roles. The mapping is semantic and deliberately
    /// independent from the GLSL output locations `0, 1, 2`.
    pub(crate) fn terrain_output(self) -> TerrainPassOutput {
        match self {
            Self::LitColor => TerrainPassOutput::LitTerrainColor,
            Self::MaterialAuxiliary => TerrainPassOutput::MaterialAuxiliary,
            Self::TranslucencyAuxiliary => TerrainPassOutput::TranslucencyAuxiliary,
        }
    }
}

/// Position convention for generic textured world material. The established
/// material producers build their billboards relative to the active camera;
/// this keeps the source contract explicit without passing a renderer matrix
/// or a backend object through the semantic frame.
#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum TexturedMaterialPositionSpace {
    CameraRelative,
}

/// The semantic coordinate space used by source material UVs. A source pass
/// can distinguish a Rust-owned local texture from a copied Minecraft atlas
/// region without receiving an atlas object or backend-specific binding.
#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum TexturedMaterialTextureCoordinates {
    LocalTexture,
    MinecraftBlockAtlas,
}

/// Winding used by a semantic material quad. This is deliberately independent
/// of a backend front-face constant: the source vertex contract needs the same
/// outward normal on OpenGL and Vulkan before either backend lowers a draw.
#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum TexturedMaterialWinding {
    CounterClockwise,
    Clockwise,
}

/// One Rust-owned source vertex ready for a future source-derived textured
/// material pass. `texture_uv` remains local to the semantic material texture;
/// it is not an atlas coordinate and does not imply a Java texture object.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct TexturedMaterialSourceVertex {
    pub camera_relative_position: [f32; 3],
    pub texture_uv: [f32; 2],
    pub source_color_argb: u32,
    pub packed_light: u32,
    pub geometric_normal: [f32; 3],
}

/// Backend-neutral primitive staging for a source-derived generic textured
/// material draw. It is private source-contract preparation only: discovery
/// and staging do not select or admit a gameplay route.
#[derive(Clone, Debug, PartialEq)]
pub struct TexturedMaterialSourcePrimitive {
    pub position_space: TexturedMaterialPositionSpace,
    pub texture_coordinates: TexturedMaterialTextureCoordinates,
    pub winding: TexturedMaterialWinding,
    pub vertices: [TexturedMaterialSourceVertex; 4],
}

/// Copies semantic quad values into a Rust-owned source-material vertex
/// primitive and derives its outward geometric normal. The input order is the
/// established `p0, p1, p2, p3` quad order; clockwise semantic winding swaps
/// the effective front face just as the ordinary material route does.
pub fn stage_textured_material_primitive(
    positions: [[f32; 3]; 4],
    texture_uvs: [[f32; 2]; 4],
    source_color_argb: u32,
    packed_light: u32,
    texture_coordinates: TexturedMaterialTextureCoordinates,
    winding: TexturedMaterialWinding,
) -> GalResult<TexturedMaterialSourcePrimitive> {
    stage_textured_material_primitive_with_vertex_modulation(
        positions,
        texture_uvs,
        [source_color_argb; 4],
        [packed_light; 4],
        texture_coordinates,
        winding,
    )
}

/// Stages one copied textured quad while preserving vertex-local color and
/// packed-light inputs. This is shared material semantics: the producer does
/// not select a pipeline or backend representation.
pub fn stage_textured_material_primitive_with_vertex_modulation(
    positions: [[f32; 3]; 4],
    texture_uvs: [[f32; 2]; 4],
    source_colors_argb: [u32; 4],
    packed_lights: [u32; 4],
    texture_coordinates: TexturedMaterialTextureCoordinates,
    winding: TexturedMaterialWinding,
) -> GalResult<TexturedMaterialSourcePrimitive> {
    for (index, position) in positions.iter().enumerate() {
        if !position.iter().all(|value| value.is_finite()) {
            return Err(GalError::invalid_argument(format!(
                "textured material vertex {index} has non-finite camera-relative position"
            )));
        }
    }
    for (index, uv) in texture_uvs.iter().enumerate() {
        if !uv.iter().all(|value| value.is_finite()) {
            return Err(GalError::invalid_argument(format!(
                "textured material vertex {index} has non-finite texture UV"
            )));
        }
    }

    let first_edge = subtract(positions[1], positions[0]);
    let second_edge = subtract(positions[3], positions[0]);
    let mut normal = normalize(cross(first_edge, second_edge)).ok_or_else(|| {
        GalError::invalid_argument("textured material quad has zero-area geometric normal")
    })?;
    if winding == TexturedMaterialWinding::Clockwise {
        normal = [-normal[0], -normal[1], -normal[2]];
    }
    let vertices = std::array::from_fn(|index| TexturedMaterialSourceVertex {
        camera_relative_position: positions[index],
        texture_uv: texture_uvs[index],
        source_color_argb: source_colors_argb[index],
        packed_light: packed_lights[index],
        geometric_normal: normal,
    });
    Ok(TexturedMaterialSourcePrimitive {
        position_space: TexturedMaterialPositionSpace::CameraRelative,
        texture_coordinates,
        winding,
        vertices,
    })
}

/// Packs source-material primitives into the compact vertex stream declared
/// by the selected-source lowerer. The copy is caller-independent and retains
/// raw Minecraft UV2 light coordinates; the owned source texture matrices,
/// not this packer, decide how those coordinates are sampled.
pub fn pack_textured_material_source_primitives(
    primitives: &[TexturedMaterialSourcePrimitive],
) -> GalResult<Vec<u8>> {
    let vertex_count = primitives.len().checked_mul(4).ok_or_else(|| {
        GalError::invalid_argument("textured material source vertex count overflows")
    })?;
    let byte_count = vertex_count
        .checked_mul(TEXTURED_MATERIAL_SOURCE_VERTEX_BYTES)
        .ok_or_else(|| {
            GalError::invalid_argument("textured material source vertex byte count overflows")
        })?;
    let mut bytes = Vec::with_capacity(byte_count);
    for (primitive_index, primitive) in primitives.iter().enumerate() {
        if primitive.position_space != TexturedMaterialPositionSpace::CameraRelative {
            return Err(GalError::invalid_argument(format!(
                "textured material primitive {primitive_index} does not use camera-relative positions"
            )));
        }
        // The lowered source vertex preamble always expands its four stored
        // vertices as `0, 1, 2, 2, 3, 0`.  Store clockwise primitives in the
        // reverse canonical order so that fixed expansion becomes Frozen's
        // inside-cloud sequence `3, 2, 1, 1, 0, 3`.  Merely changing the
        // pipeline cull mode leaves the triangles counter-clockwise and
        // makes copied clockwise semantics visibly wrong.
        let source_indices = match primitive.winding {
            TexturedMaterialWinding::CounterClockwise => [0, 1, 2, 3],
            TexturedMaterialWinding::Clockwise => [3, 2, 1, 0],
        };
        for (packed_index, vertex_index) in source_indices.into_iter().enumerate() {
            let vertex = primitive.vertices[vertex_index];
            if !vertex
                .camera_relative_position
                .into_iter()
                .chain(vertex.texture_uv)
                .chain(vertex.geometric_normal)
                .all(f32::is_finite)
            {
                return Err(GalError::invalid_argument(format!(
                    "textured material primitive {primitive_index} vertex {packed_index} has non-finite source data"
                )));
            }
            let color = argb_to_rgba(vertex.source_color_argb);
            let [block_light, sky_light] = source_lightmap_coordinates(vertex.packed_light);
            push_f32(&mut bytes, vertex.camera_relative_position[0]);
            push_f32(&mut bytes, vertex.camera_relative_position[1]);
            push_f32(&mut bytes, vertex.camera_relative_position[2]);
            push_f32(&mut bytes, 1.0);
            for component in color {
                push_f32(&mut bytes, component);
            }
            push_f32(&mut bytes, vertex.geometric_normal[0]);
            push_f32(&mut bytes, vertex.geometric_normal[1]);
            push_f32(&mut bytes, vertex.geometric_normal[2]);
            push_f32(&mut bytes, 0.0);
            push_f32(&mut bytes, vertex.texture_uv[0]);
            push_f32(&mut bytes, vertex.texture_uv[1]);
            push_f32(&mut bytes, block_light);
            push_f32(&mut bytes, sky_light);
        }
    }
    debug_assert_eq!(bytes.len(), byte_count);
    Ok(bytes)
}

/// Packs only primitives whose UVs address the owned Minecraft block atlas.
/// `gbuffers_textured` declares the pack's `tex` sampler semantic, so binding
/// a standalone Rust texture to this stream would make a visually plausible
/// but semantically incorrect source draw. A later source-material program
/// may introduce an explicitly declared local-texture role; it must not reuse
/// this atlas-only contract.
pub fn pack_textured_material_atlas_primitives(
    primitives: &[TexturedMaterialSourcePrimitive],
) -> GalResult<Vec<u8>> {
    for (primitive_index, primitive) in primitives.iter().enumerate() {
        if primitive.texture_coordinates != TexturedMaterialTextureCoordinates::MinecraftBlockAtlas
        {
            return Err(GalError::unsupported_feature(format!(
                "textured material primitive {primitive_index} uses a local texture but the selected source material pass requires Minecraft block-atlas UVs",
            )));
        }
    }
    pack_textured_material_source_primitives(primitives)
}

fn source_lightmap_coordinates(packed_light: u32) -> [f32; 2] {
    [
        ((packed_light >> 4) & 0xf) as f32 * 16.0,
        ((packed_light >> 20) & 0xf) as f32 * 16.0,
    ]
}

fn argb_to_rgba(argb: u32) -> [f32; 4] {
    [
        ((argb >> 16) & 0xff) as f32 / 255.0,
        ((argb >> 8) & 0xff) as f32 / 255.0,
        (argb & 0xff) as f32 / 255.0,
        ((argb >> 24) & 0xff) as f32 / 255.0,
    ]
}

fn push_f32(bytes: &mut Vec<u8>, value: f32) {
    bytes.extend_from_slice(&value.to_ne_bytes());
}

fn subtract(left: [f32; 3], right: [f32; 3]) -> [f32; 3] {
    [left[0] - right[0], left[1] - right[1], left[2] - right[2]]
}

fn cross(left: [f32; 3], right: [f32; 3]) -> [f32; 3] {
    [
        left[1] * right[2] - left[2] * right[1],
        left[2] * right[0] - left[0] * right[2],
        left[0] * right[1] - left[1] * right[0],
    ]
}

fn normalize(value: [f32; 3]) -> Option<[f32; 3]> {
    let length_squared = value
        .iter()
        .map(|component| component * component)
        .sum::<f32>();
    if !length_squared.is_finite() || length_squared <= f32::EPSILON {
        return None;
    }
    let inverse_length = length_squared.sqrt().recip();
    Some([
        value[0] * inverse_length,
        value[1] * inverse_length,
        value[2] * inverse_length,
    ])
}

/// Immutable source-pack discovery result. It deliberately holds no compiled
/// source, backend object, or selected route state.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TexturedMaterialPassContract {
    pub pack_name: String,
    pub generation: u64,
    pub scope: TerrainProgramScope,
    pub program_path: String,
    pub stages: TerrainSourceStages,
    pub inputs: Vec<TexturedMaterialSourceInput>,
    pub outputs: Vec<TexturedMaterialSourceOutput>,
    /// Source color slots aligned with `outputs`; these are pack metadata, not
    /// GAL attachment indices or native bindings.
    pub output_color_slots: Vec<u32>,
}

pub fn derive_textured_material_contract(
    source: &ShaderPackSource,
    scope: TerrainProgramScope,
) -> GalResult<TexturedMaterialPassContract> {
    let program_path = scope
        .textured_material_entry_candidates()
        .iter()
        .copied()
        .find(|path| source.get(path).is_some())
        .ok_or_else(|| {
            GalError::invalid_argument(format!(
                "missing textured material fragment source for {scope:?}; tried {}",
                scope.textured_material_entry_candidates().join(", ")
            ))
        })?;
    let stages = terrain_source_stages(program_path)?;
    let vertex = preprocess_stage(source, &stages.vertex.path, &stages.vertex.defines)?;
    let fragment = preprocess_stage(source, &stages.fragment.path, &stages.fragment.defines)?;

    require(&vertex, "GetLightMapCoordinates()")?;
    require(&vertex, "gl_Normal")?;
    require(&vertex, "gl_Color")?;
    require(&fragment, "texture2D(tex, texCoord)")?;
    require(&fragment, "color *= glColor")?;
    require(&fragment, "DoLighting(")?;
    require(&fragment, "gl_FragData[0] = color")?;
    require(&fragment, "gl_FragData[1]")?;
    require(&fragment, "gl_FragData[2]")?;

    let slots = parse_draw_buffers_slots(&fragment)?;
    if slots != [0, 6, 3] {
        return Err(GalError::unsupported_feature(format!(
            "selected textured material source requires unsupported DRAWBUFFERS schema {slots:?}; expected [0, 6, 3]"
        )));
    }
    Ok(TexturedMaterialPassContract {
        pack_name: source.name().to_string(),
        generation: source.generation(),
        scope,
        program_path: program_path.to_string(),
        stages,
        inputs: vec![
            TexturedMaterialSourceInput::MaterialTexture,
            TexturedMaterialSourceInput::VertexColor,
            TexturedMaterialSourceInput::PackedLight,
            TexturedMaterialSourceInput::ViewSpaceNormal,
            TexturedMaterialSourceInput::CameraAndEnvironment,
            TexturedMaterialSourceInput::PackNoise,
            TexturedMaterialSourceInput::MainDepth,
        ],
        outputs: vec![
            TexturedMaterialSourceOutput::LitColor,
            TexturedMaterialSourceOutput::MaterialAuxiliary,
            TexturedMaterialSourceOutput::TranslucencyAuxiliary,
        ],
        output_color_slots: slots,
    })
}

/// Reuses the paired source compiler for the bounded material contract while
/// enforcing that the selected source can be fed exclusively by the generic
/// semantic material vertex stream. Terrain-only metadata is rejected here,
/// before any route can interpret default zeroes as a valid block/material.
pub fn lower_textured_material_source_pair(
    source: &ShaderPackSource,
    contract: &TexturedMaterialPassContract,
) -> GalResult<LoweredTexturedMaterialSourcePair> {
    if contract.pack_name != source.name() || contract.generation != source.generation() {
        return Err(GalError::invalid_argument(
            "textured material contract does not belong to the supplied shader-pack source",
        ));
    }
    let vertex = preprocess_stage_artifact(
        source,
        &contract.stages.vertex.path,
        &contract.stages.vertex.defines,
    )?;
    let fragment = preprocess_stage_artifact(
        source,
        &contract.stages.fragment.path,
        &contract.stages.fragment.defines,
    )?;
    // Legacy textured programs commonly use `ftransform()` without spelling
    // `gl_Vertex` directly. Both forms are lowered through the same explicit
    // Rust position semantic; requiring only the latter incorrectly rejects
    // the normal Complementary entry before the source pair reaches lowering.
    require_any(vertex.expanded_source(), &["gl_Vertex", "ftransform"])?;
    for unsupported in ["mc_Entity", "mc_midTexCoord", "at_tangent", "at_midBlock"] {
        require_absent(vertex.expanded_source(), unsupported)?;
    }
    let lowered = lower_textured_material_stages(&vertex, &fragment)?;
    lowered.require_backend_neutral_lowering()?;
    Ok(lowered)
}

fn preprocess_stage(
    source: &ShaderPackSource,
    path: &str,
    defines: &std::collections::BTreeMap<String, String>,
) -> GalResult<String> {
    Ok(preprocess_stage_artifact(source, path, defines)?
        .expanded_source()
        .to_string())
}

fn preprocess_stage_artifact(
    source: &ShaderPackSource,
    path: &str,
    defines: &std::collections::BTreeMap<String, String>,
) -> GalResult<super::preprocess::PreprocessedShaderSource> {
    let defines = defines
        .iter()
        .map(|(key, value)| (key.as_str(), value.as_str()))
        .collect::<Vec<_>>();
    preprocess_artifact_with_runtime_options(source, path, &defines)
}

fn require(source: &str, expression: &str) -> GalResult<()> {
    if source.contains(expression) {
        Ok(())
    } else {
        Err(GalError::unsupported_feature(format!(
            "selected textured material source is missing required expression '{expression}'"
        )))
    }
}

fn require_any(source: &str, expressions: &[&str]) -> GalResult<()> {
    if expressions
        .iter()
        .any(|expression| source.contains(expression))
    {
        Ok(())
    } else {
        Err(GalError::unsupported_feature(format!(
            "selected textured material source is missing every required expression in {:?}",
            expressions
        )))
    }
}

fn require_absent(source: &str, expression: &str) -> GalResult<()> {
    if source.contains(expression) {
        Err(GalError::unsupported_feature(format!(
            "selected textured material source requires unsupported terrain-only attribute '{expression}'"
        )))
    } else {
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::render::vulkanic::shader_pack::source::ShaderSourceFile;

    fn source(fragment_draw_buffers: &str) -> ShaderPackSource {
        ShaderPackSource::new(
            "textured-fixture",
            7,
            vec![
                ShaderSourceFile::new(
                    "world0/gbuffers_textured.vsh",
                    "#version 130\n#define VERTEX_SHADER\nvoid main() { vec4 p = gl_Vertex; vec2 lm = GetLightMapCoordinates(); vec3 n = gl_Normal; vec4 c = gl_Color; gl_Position = gl_ProjectionMatrix * gl_ModelViewMatrix * p; }",
                ),
                ShaderSourceFile::new(
                    "world0/gbuffers_textured.fsh",
                    format!(
                        "#version 130\n#define FRAGMENT_SHADER\nvoid DoLighting() {{}}\nvoid main() {{ vec4 color = texture2D(tex, texCoord); color *= glColor; DoLighting(); gl_FragData[0] = color; gl_FragData[1] = color; gl_FragData[2] = color; /* DRAWBUFFERS:{fragment_draw_buffers} */ }}"
                    ),
                ),
            ],
        )
        .unwrap()
    }

    #[test]
    fn discovers_explicit_textured_material_semantics_without_renderer_state() {
        let contract =
            derive_textured_material_contract(&source("063"), TerrainProgramScope::Overworld)
                .unwrap();
        assert_eq!("world0/gbuffers_textured.fsh", contract.program_path);
        assert_eq!(vec![0, 6, 3], contract.output_color_slots);
        assert!(contract
            .inputs
            .contains(&TexturedMaterialSourceInput::PackedLight));
        assert!(contract
            .inputs
            .contains(&TexturedMaterialSourceInput::ViewSpaceNormal));
        assert_eq!(3, contract.outputs.len());
    }

    #[test]
    fn rejects_incompatible_textured_material_output_schema() {
        let error =
            derive_textured_material_contract(&source("06"), TerrainProgramScope::Overworld)
                .unwrap_err();
        assert!(error.to_string().contains("DRAWBUFFERS schema"));
    }

    #[test]
    fn lowers_a_bounded_textured_material_pair_without_terrain_only_attributes() {
        let source = source("063");
        let contract =
            derive_textured_material_contract(&source, TerrainProgramScope::Overworld).unwrap();
        let lowered = lower_textured_material_source_pair(&source, &contract).unwrap();

        lowered.require_backend_neutral_lowering().unwrap();
        assert!(lowered
            .vertex()
            .source()
            .contains("VulkanicSourceTexturedMaterialVertex"));
        assert!(!lowered.vertex().source().contains("vulkanic_source_entity"));
        assert!(lowered
            .fragment()
            .source()
            .contains("out_textured_material_lit_color"));
        assert!(lowered
            .fragment()
            .source()
            .contains("out_textured_material_translucency_auxiliary"));
        assert!(!lowered
            .fragment()
            .source()
            .contains("out_terrain_view_space_normal"));
    }

    #[test]
    fn lowers_ftransform_only_textured_material_vertex_semantics() {
        let source = ShaderPackSource::new(
            "textured-ftransform",
            8,
            vec![
                ShaderSourceFile::new(
                    "world0/gbuffers_textured.vsh",
                    "#version 130\nvoid main() { vec2 lm = GetLightMapCoordinates(); vec3 n = gl_Normal; vec4 c = gl_Color; gl_Position = ftransform(); }",
                ),
                ShaderSourceFile::new(
                    "world0/gbuffers_textured.fsh",
                    "#version 130\nvoid DoLighting() {}\nvoid main() { vec4 color = texture2D(tex, texCoord); color *= glColor; DoLighting(); gl_FragData[0] = color; gl_FragData[1] = color; gl_FragData[2] = color; /* DRAWBUFFERS:063 */ }",
                ),
            ],
        )
        .unwrap();
        let contract =
            derive_textured_material_contract(&source, TerrainProgramScope::Overworld).unwrap();
        let lowered = lower_textured_material_source_pair(&source, &contract).unwrap();

        lowered.require_backend_neutral_lowering().unwrap();
        assert!(lowered
            .vertex()
            .source()
            .contains("vulkanic_source_ftransform"));
    }

    #[test]
    fn rejects_textured_material_source_with_terrain_only_attributes() {
        let source = ShaderPackSource::new(
            "textured-terrain-attribute-fixture",
            9,
            vec![
                ShaderSourceFile::new(
                    "world0/gbuffers_textured.vsh",
                    "#version 130\n#define VERTEX_SHADER\nvoid main() { vec4 p = gl_Vertex + vec4(mc_Entity.x); vec2 lm = GetLightMapCoordinates(); vec3 n = gl_Normal; vec4 c = gl_Color; gl_Position = gl_ProjectionMatrix * gl_ModelViewMatrix * p; }",
                ),
                ShaderSourceFile::new(
                    "world0/gbuffers_textured.fsh",
                    "#version 130\n#define FRAGMENT_SHADER\nvoid DoLighting() {}\nvoid main() { vec4 color = texture2D(tex, texCoord); color *= glColor; DoLighting(); gl_FragData[0] = color; gl_FragData[1] = color; gl_FragData[2] = color; /* DRAWBUFFERS:063 */ }",
                ),
            ],
        )
        .unwrap();
        let contract =
            derive_textured_material_contract(&source, TerrainProgramScope::Overworld).unwrap();
        let error = lower_textured_material_source_pair(&source, &contract).unwrap_err();
        assert!(error
            .to_string()
            .contains("terrain-only attribute 'mc_Entity'"));
    }

    #[test]
    fn stages_camera_relative_material_vertices_with_an_outward_normal() {
        let primitive = stage_textured_material_primitive(
            [
                [-1.0, -1.0, 2.0],
                [1.0, -1.0, 2.0],
                [1.0, 1.0, 2.0],
                [-1.0, 1.0, 2.0],
            ],
            [[0.2, 0.3], [0.8, 0.3], [0.8, 0.9], [0.2, 0.9]],
            0x7f12_3456,
            0x00f0_00b0,
            TexturedMaterialTextureCoordinates::MinecraftBlockAtlas,
            TexturedMaterialWinding::CounterClockwise,
        )
        .unwrap();

        assert_eq!(
            TexturedMaterialPositionSpace::CameraRelative,
            primitive.position_space
        );
        assert_eq!(
            TexturedMaterialTextureCoordinates::MinecraftBlockAtlas,
            primitive.texture_coordinates
        );
        assert_eq!([0.0, 0.0, 1.0], primitive.vertices[0].geometric_normal);
        assert_eq!([0.8, 0.9], primitive.vertices[2].texture_uv);
        assert_eq!(0x7f12_3456, primitive.vertices[3].source_color_argb);
        assert_eq!(0x00f0_00b0, primitive.vertices[1].packed_light);
    }

    #[test]
    fn vertex_modulated_material_stream_preserves_endpoint_color_and_light() {
        let primitive = stage_textured_material_primitive_with_vertex_modulation(
            [
                [0.0, 0.0, 0.0],
                [1.0, 0.0, 0.0],
                [1.0, 1.0, 0.0],
                [0.0, 1.0, 0.0],
            ],
            [[0.0, 0.0], [1.0, 0.0], [1.0, 1.0], [0.0, 1.0]],
            [0xff80_6040, 0xff80_6040, 0xff38_2c20, 0xff38_2c20],
            [0x00f0_00d0, 0x00f0_00d0, 0x00b0_0070, 0x00b0_0070],
            TexturedMaterialTextureCoordinates::LocalTexture,
            TexturedMaterialWinding::CounterClockwise,
        )
        .unwrap();

        assert_eq!(0xff80_6040, primitive.vertices[0].source_color_argb);
        assert_eq!(0xff38_2c20, primitive.vertices[3].source_color_argb);
        assert_eq!(0x00f0_00d0, primitive.vertices[1].packed_light);
        assert_eq!(0x00b0_0070, primitive.vertices[2].packed_light);
    }

    #[test]
    fn packs_compact_textured_material_stream_with_raw_uv2_light() {
        let primitive = stage_textured_material_primitive(
            [
                [0.0, 0.0, 0.0],
                [1.0, 0.0, 0.0],
                [1.0, 1.0, 0.0],
                [0.0, 1.0, 0.0],
            ],
            [[0.25, 0.5], [0.75, 0.5], [0.75, 1.0], [0.25, 1.0]],
            0x8040_80ff,
            0x00d0_00a0,
            TexturedMaterialTextureCoordinates::MinecraftBlockAtlas,
            TexturedMaterialWinding::CounterClockwise,
        )
        .unwrap();
        let packed = pack_textured_material_source_primitives(&[primitive]).unwrap();
        assert_eq!(4 * TEXTURED_MATERIAL_SOURCE_VERTEX_BYTES, packed.len());
        let lanes = packed
            .chunks_exact(std::mem::size_of::<f32>())
            .map(|lane| f32::from_ne_bytes(lane.try_into().unwrap()))
            .collect::<Vec<_>>();
        assert_eq!([0.0, 0.0, 0.0, 1.0], lanes[0..4]);
        assert_eq!(
            [64.0 / 255.0, 128.0 / 255.0, 1.0, 128.0 / 255.0],
            lanes[4..8]
        );
        assert_eq!([0.0, 0.0, 1.0, 0.0], lanes[8..12]);
        assert_eq!([0.25, 0.5, 160.0, 208.0], lanes[12..16]);
    }

    #[test]
    fn atlas_source_stream_rejects_standalone_texture_coordinates() {
        let primitive = stage_textured_material_primitive(
            [
                [-1.0, -1.0, 2.0],
                [1.0, -1.0, 2.0],
                [1.0, 1.0, 2.0],
                [-1.0, 1.0, 2.0],
            ],
            [[0.0, 0.0], [1.0, 0.0], [1.0, 1.0], [0.0, 1.0]],
            0xffff_ffff,
            0,
            TexturedMaterialTextureCoordinates::LocalTexture,
            TexturedMaterialWinding::CounterClockwise,
        )
        .unwrap();

        let error = pack_textured_material_atlas_primitives(&[primitive]).unwrap_err();
        assert!(error
            .to_string()
            .contains("requires Minecraft block-atlas UVs"));
    }

    #[test]
    fn source_material_winding_flips_the_geometric_normal_without_reordering_semantics() {
        let primitive = stage_textured_material_primitive(
            [
                [-1.0, -1.0, 2.0],
                [1.0, -1.0, 2.0],
                [1.0, 1.0, 2.0],
                [-1.0, 1.0, 2.0],
            ],
            [[0.0, 0.0], [1.0, 0.0], [1.0, 1.0], [0.0, 1.0]],
            0xffff_ffff,
            0,
            TexturedMaterialTextureCoordinates::LocalTexture,
            TexturedMaterialWinding::Clockwise,
        )
        .unwrap();

        assert_eq!(
            [-1.0, -1.0, 2.0],
            primitive.vertices[0].camera_relative_position
        );
        assert_eq!([0.0, 0.0], primitive.vertices[0].texture_uv);
        assert_eq!([0.0, 0.0, -1.0], primitive.vertices[0].geometric_normal);
    }

    #[test]
    fn clockwise_source_stream_reverses_storage_for_owned_triangle_expansion() {
        let primitive = stage_textured_material_primitive(
            [
                [0.0, 0.0, 0.0],
                [1.0, 0.0, 0.0],
                [1.0, 1.0, 0.0],
                [0.0, 1.0, 0.0],
            ],
            [[0.0, 0.0], [1.0, 0.0], [1.0, 1.0], [0.0, 1.0]],
            0xffff_ffff,
            0,
            TexturedMaterialTextureCoordinates::LocalTexture,
            TexturedMaterialWinding::Clockwise,
        )
        .unwrap();
        let packed = pack_textured_material_source_primitives(&[primitive]).unwrap();
        let positions = packed
            .chunks_exact(TEXTURED_MATERIAL_SOURCE_VERTEX_BYTES)
            .map(|vertex| {
                [
                    f32::from_ne_bytes(vertex[0..4].try_into().unwrap()),
                    f32::from_ne_bytes(vertex[4..8].try_into().unwrap()),
                    f32::from_ne_bytes(vertex[8..12].try_into().unwrap()),
                ]
            })
            .collect::<Vec<_>>();
        assert_eq!(
            vec![[0.0, 1.0, 0.0], [1.0, 1.0, 0.0], [1.0, 0.0, 0.0], [0.0, 0.0, 0.0]],
            positions
        );
    }

    #[test]
    fn rejects_degenerate_or_non_finite_source_material_quad_data() {
        let degenerate = stage_textured_material_primitive(
            [[0.0, 0.0, 0.0]; 4],
            [[0.0, 0.0]; 4],
            0,
            0,
            TexturedMaterialTextureCoordinates::LocalTexture,
            TexturedMaterialWinding::CounterClockwise,
        )
        .unwrap_err();
        assert!(degenerate.to_string().contains("zero-area"));

        let non_finite = stage_textured_material_primitive(
            [
                [f32::NAN, 0.0, 0.0],
                [1.0, 0.0, 0.0],
                [1.0, 1.0, 0.0],
                [0.0, 1.0, 0.0],
            ],
            [[0.0, 0.0], [1.0, 0.0], [1.0, 1.0], [0.0, 1.0]],
            0,
            0,
            TexturedMaterialTextureCoordinates::LocalTexture,
            TexturedMaterialWinding::CounterClockwise,
        )
        .unwrap_err();
        assert!(non_finite.to_string().contains("non-finite"));
    }
}

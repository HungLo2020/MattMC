//! Lowered Distant Horizons source programs, including the exact-atlas vertex/fragment adapters.

use super::*;

/// Fixed std430 record copied from the DH CPU mesh stream and expanded by the
/// Rust world frontend. This is intentionally distinct from the near-terrain
/// source record: DH has pre-resolved vertex color/light/material data rather
/// than atlas-backed Minecraft terrain vertices.
pub(crate) const DISTANT_HORIZONS_SOURCE_VERTEX_BYTES: usize = 32;

/// Private Rust source-stream record for a DH range with complete atlas
/// provenance. It extends the regular DH semantic record with one exact
/// atlas rectangle and repeated tile coordinates; it is not a Java/DH GL
/// layout or backend-native vertex format.
pub(crate) const DISTANT_HORIZONS_EXACT_ATLAS_SOURCE_VERTEX_BYTES: usize = 56;

/// The per-column std140 semantic frame block consumed by the lowered DH
/// source preamble. It carries a Rust-owned column origin and LOD controls;
/// source-declared `dh*` matrices live in the separately typed scalar block.
pub(crate) const DISTANT_HORIZONS_SOURCE_COLUMN_FRAME_BYTES: usize = 128;

/// Source-derived executable preparation for the distinct Distant Horizons
/// terrain stage. The program is deliberately separate from
/// [`LoweredTerrainSourceProgram`]: DH uses a copied 32-byte column stream,
/// one column-frame block per draw, and `dh*` transform semantics. Keeping
/// the ABI distinct prevents the near-terrain selected-source route from
/// interpreting a DH asset as an atlas-backed mesh.
///
/// This remains a preparation artifact. It owns no native object, creates no
/// target, and cannot select a gameplay route.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LoweredDistantHorizonsSourceProgram {
    pub identity: ProgramIdentity,
    pub shader_pack_generation: u64,
    /// The semantic DH material phase selected from source. It prevents a
    /// later pass owner from applying opaque depth/blend behavior to a
    /// separately lowered `dh_water` program.
    pub pass_kind: DistantHorizonsPassKind,
    /// Source-declared blend semantics for the translucent phase only. This
    /// is intentionally not an API pipeline flag.
    pub translucent_blend: Option<TerrainTranslucentBlend>,
    pub vertex: ShaderStageSource,
    pub fragment: ShaderStageSource,
    pub execution_interface: DistantHorizonsSourceExecutionInterface,
    pub scalar_uniform_requirements: TerrainSourceUniformRequirements,
    pub opaque_resource_bindings: TerrainSourceOpaqueResourceBindingPlan,
    pub required_resources: Vec<TerrainProgramResource>,
}

/// Source-derived DH program variant for a range whose copied semantic
/// provenance resolves one exact Minecraft atlas sprite per quad. It keeps
/// the selected pack's transform, material-category, lighting, and fragment
/// logic intact; the adapter supplies only the source `gl_Color` input from
/// Rust-owned atlas data.
///
/// This is deliberately distinct from the reduced-color stream ABI. It owns
/// no target or native resource and remains a shader-pack preparation
/// artifact, while the world frontend owns its private vertex stream and
/// atlas resource lifetime.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LoweredDistantHorizonsExactAtlasSourceProgram {
    pub source: LoweredDistantHorizonsSourceProgram,
}

impl LoweredDistantHorizonsExactAtlasSourceProgram {
    pub fn shader_module_descriptors(&self, conventions: ShaderConventions) -> [ShaderModuleDesc; 2] {
        self.source.shader_module_descriptors(conventions)
    }
}

/// Derives the exact-atlas variant of an already admitted DH source program.
/// The selected source remains authoritative for all shader-pack logic. This
/// adapter changes only the private Rust vertex/material input convention so
/// a provenance-resolved atlas tile can become `gl_Color` before the source
/// vertex and fragment stages execute.
pub fn prepare_lowered_distant_horizons_exact_atlas_source_program(
    program: &LoweredDistantHorizonsSourceProgram,
) -> GalResult<LoweredDistantHorizonsExactAtlasSourceProgram> {
    if program.pass_kind != DistantHorizonsPassKind::Opaque || program.translucent_blend.is_some() {
        return Err(GalError::invalid_argument(
            "exact-atlas Distant Horizons source adapter requires an opaque source program",
        ));
    }
    if program.vertex.source.contains("vulkanic_source_dh_atlas_")
        || program
            .fragment
            .source
            .contains("vulkanic_source_dh_atlas_")
    {
        return Err(GalError::invalid_argument(
            "Distant Horizons source program already contains an exact-atlas adapter interface",
        ));
    }
    if !program
        .vertex
        .source
        .contains("vulkanic_source_vertex_color")
        || !program
            .vertex
            .source
            .contains("vulkanic_source_dh_material_id")
    {
        return Err(GalError::unsupported_feature(
            "Distant Horizons source program does not expose the required semantic color/material interface",
        ));
    }

    let varying_locations = exact_atlas_distant_horizons_varying_locations(
        &program.vertex.source,
        &program.fragment.source,
    )?;
    let vertex = inject_exact_atlas_distant_horizons_vertex_adapter(
        &program.vertex.source,
        varying_locations,
    )?;
    let fragment = inject_exact_atlas_distant_horizons_fragment_adapter(
        &program.fragment.source,
        varying_locations,
    )?;
    let mut source = program.clone();
    source.identity = ProgramIdentity::new(format!("{}:exact-atlas", program.identity.as_str()));
    source.vertex = ShaderStageSource {
        stage: ShaderStageKind::Vertex,
        label: format!("{}:exact-atlas-adapter", program.vertex.label),
        source: vertex,
        entry_point: "main".to_string(),
    };
    source.fragment = ShaderStageSource {
        stage: ShaderStageKind::Fragment,
        label: format!("{}:exact-atlas-adapter", program.fragment.label),
        source: fragment,
        entry_point: "main".to_string(),
    };
    source.execution_interface.vertex_stride =
        DISTANT_HORIZONS_EXACT_ATLAS_SOURCE_VERTEX_BYTES as u32;
    source.execution_interface.material_identity_contract =
        DistantHorizonsMaterialIdentityContract::AtlasBacked;
    source.execution_interface.validate()?;
    Ok(LoweredDistantHorizonsExactAtlasSourceProgram { source })
}

#[derive(Clone, Copy, Debug)]
pub(crate) struct ExactAtlasDistantHorizonsVaryingLocations {
    pub(in crate::render::shaderpack::programs) tile_uv: u32,
    pub(in crate::render::shaderpack::programs) atlas_rect: u32,
    pub(in crate::render::shaderpack::programs) tint_and_material: u32,
}

/// Allocates three locations outside the selected source interface. The
/// adapter must not reserve a high arbitrary location: Vulkan counts declared
/// interface components against the device limit even when lower locations are
/// unused.
pub(crate) fn exact_atlas_distant_horizons_varying_locations(
    vertex: &str,
    fragment: &str,
) -> GalResult<ExactAtlasDistantHorizonsVaryingLocations> {
    let mut occupied = std::collections::BTreeSet::new();
    for source in [vertex, fragment] {
        for line in source.lines() {
            let Some(location_start) = line.find("layout(location = ") else {
                continue;
            };
            let value = &line[location_start + "layout(location = ".len()..];
            let Some(location_end) = value.find(')') else {
                return Err(GalError::invalid_argument(
                    "Distant Horizons source has an unterminated explicit varying location",
                ));
            };
            let location = value[..location_end].trim().parse::<u32>().map_err(|_| {
                GalError::invalid_argument(
                    "Distant Horizons source has a non-numeric explicit varying location",
                )
            })?;
            occupied.insert(location);
        }
    }
    for first in 0..=u32::MAX - 2 {
        if !occupied.contains(&first)
            && !occupied.contains(&(first + 1))
            && !occupied.contains(&(first + 2))
        {
            return Ok(ExactAtlasDistantHorizonsVaryingLocations {
                tile_uv: first,
                atlas_rect: first + 1,
                tint_and_material: first + 2,
            });
        }
    }
    Err(GalError::unsupported_feature(
        "Distant Horizons source has no three-location interval for the exact-atlas adapter",
    ))
}

pub(crate) fn inject_exact_atlas_distant_horizons_vertex_adapter(
    source: &str,
    locations: ExactAtlasDistantHorizonsVaryingLocations,
) -> GalResult<String> {
    let start = source.find("struct VulkanicDistantHorizonsVertex {").ok_or_else(|| {
        GalError::unsupported_feature(
            "Distant Horizons source vertex adapter could not find the semantic vertex-color definition",
        )
    })?;
    let end_marker = "#define vulkanic_source_ftransform() (dhProjection * vulkanic_source_model_view * vulkanic_source_position)";
    let end = source[start..]
        .find(end_marker)
        .map(|offset| start + offset + end_marker.len())
        .ok_or_else(|| {
            GalError::unsupported_feature(
                "Distant Horizons source vertex adapter could not find the semantic transform definition",
            )
        })?;
    let preamble = r#"struct VulkanicDistantHorizonsExactAtlasVertex {
    float local_x;
    float local_y;
    float local_z;
    float micro_x;
    float micro_y;
    float micro_z;
    float tile_u;
    float tile_v;
    float atlas_u0;
    float atlas_v0;
    float atlas_u1;
    float atlas_v1;
    uint color_rgba;
    uint light_normal_tint_material;
};
layout(set = 0, binding = 0, std430) readonly buffer VulkanicDistantHorizonsExactAtlasVertices {
    VulkanicDistantHorizonsExactAtlasVertex vulkanic_source_dh_vertices[];
};
layout(set = 0, binding = 1, std140) uniform VulkanicDistantHorizonsColumnFrame {
    mat4 vulkanic_source_dh_unused_combined_matrix;
    vec4 vulkanic_source_dh_column_origin_and_world_y;
    vec4 vulkanic_source_dh_model_offset_and_reserved;
    vec4 vulkanic_source_dh_clip_micro_noise_earth;
    uvec4 vulkanic_source_dh_flags_and_noise;
};
#define VULKANIC_SOURCE_DH_ATLAS_TILE_LOCATION __VULKANIC_SOURCE_DH_ATLAS_TILE_LOCATION__
#define VULKANIC_SOURCE_DH_ATLAS_RECT_LOCATION __VULKANIC_SOURCE_DH_ATLAS_RECT_LOCATION__
#define VULKANIC_SOURCE_DH_ATLAS_TINT_LOCATION __VULKANIC_SOURCE_DH_ATLAS_TINT_LOCATION__
layout(location = VULKANIC_SOURCE_DH_ATLAS_TILE_LOCATION) out vec2 vulkanic_source_dh_atlas_tile_uv;
layout(location = VULKANIC_SOURCE_DH_ATLAS_RECT_LOCATION) flat out vec4 vulkanic_source_dh_atlas_rect;
layout(location = VULKANIC_SOURCE_DH_ATLAS_TINT_LOCATION) flat out uint vulkanic_source_dh_atlas_tint_and_material;
#define vulkanic_source_dh_vertex vulkanic_source_dh_vertices[gl_VertexIndex]
vec3 vulkanic_source_dh_normal(uint normal) {
    if (normal == 0u) return vec3(0.0, -1.0, 0.0);
    if (normal == 1u) return vec3(0.0, 1.0, 0.0);
    if (normal == 2u) return vec3(0.0, 0.0, -1.0);
    if (normal == 3u) return vec3(0.0, 0.0, 1.0);
    if (normal == 4u) return vec3(-1.0, 0.0, 0.0);
    return vec3(1.0, 0.0, 0.0);
}
vec4 vulkanic_source_dh_position() {
    return vec4(
        vec3(vulkanic_source_dh_vertex.local_x, vulkanic_source_dh_vertex.local_y, vulkanic_source_dh_vertex.local_z)
            // Match Iris's DHTerrainTransformer: compact DH micro offsets
            // perturb the horizontal quad edges only.
            + vec3(vulkanic_source_dh_vertex.micro_x, 0.0, vulkanic_source_dh_vertex.micro_z)
            // The column origin already carries the dimension's minimum Y.
            // This source shader consumes the copied DH model offset without
            // adding worldYOffset a second time.
            + vulkanic_source_dh_model_offset_and_reserved.xyz,
        1.0
    );
}
vec4 vulkanic_source_dh_vertex_color() {
    return vec4(
        float(vulkanic_source_dh_vertex.color_rgba & 0xffu),
        float((vulkanic_source_dh_vertex.color_rgba >> 8u) & 0xffu),
        float((vulkanic_source_dh_vertex.color_rgba >> 16u) & 0xffu),
        float((vulkanic_source_dh_vertex.color_rgba >> 24u) & 0xffu)
    ) / 255.0;
}
vec2 vulkanic_source_dh_packed_lightmap_coordinates() {
    return (vec2(
        // Iris's DHTerrainTransformer expands the packed byte as
        // `(blockLight, skyLight)`: upper nibble first, lower nibble second.
        // The copied DH semantic record retains the original order as
        // `(skyLight, blockLight)`, so preserve the source lightmap contract
        // here instead of treating its byte layout as a texture coordinate.
        float((vulkanic_source_dh_vertex.light_normal_tint_material >> 8u) & 0xffu),
        float(vulkanic_source_dh_vertex.light_normal_tint_material & 0xffu)
    ) + vec2(0.5)) / 16.0;
}
#define vulkanic_source_texture_matrix (mat4[2](mat4(1.0), mat4(1.0)))
#define vulkanic_source_lightmap_uv vec4(vulkanic_source_dh_packed_lightmap_coordinates(), 0.0, 1.0)
#define vulkanic_source_position vulkanic_source_dh_position()
#define vulkanic_source_vertex_color vulkanic_source_dh_vertex_color()
#define vulkanic_source_normal vulkanic_source_dh_normal((vulkanic_source_dh_vertex.light_normal_tint_material >> 16u) & 0xffu)
#define vulkanic_source_dh_material_id int((vulkanic_source_dh_vertex.light_normal_tint_material >> 25u) & 0x0fu)
#define vulkanic_source_model_view dhModelView
#define vulkanic_source_normal_matrix transpose(inverse(mat3(vulkanic_source_model_view)))
#define vulkanic_source_ftransform() (dhProjection * vulkanic_source_model_view * vulkanic_source_position)"#;
    let preamble = preamble
        .replace(
            "__VULKANIC_SOURCE_DH_ATLAS_TILE_LOCATION__",
            &locations.tile_uv.to_string(),
        )
        .replace(
            "__VULKANIC_SOURCE_DH_ATLAS_RECT_LOCATION__",
            &locations.atlas_rect.to_string(),
        )
        .replace(
            "__VULKANIC_SOURCE_DH_ATLAS_TINT_LOCATION__",
            &locations.tint_and_material.to_string(),
        );
    let mut lowered = String::with_capacity(source.len() + preamble.len());
    lowered.push_str(&source[..start]);
    lowered.push_str(&preamble);
    lowered.push_str(&source[end..]);
    let closing = main_function_closing_brace(&lowered).ok_or_else(|| {
        GalError::invalid_argument(
            "Distant Horizons source vertex adapter requires a brace-balanced void main body",
        )
    })?;
    lowered.insert_str(
        closing,
        r#"
    vulkanic_source_dh_atlas_tile_uv = vec2(vulkanic_source_dh_vertex.tile_u, vulkanic_source_dh_vertex.tile_v);
    vulkanic_source_dh_atlas_rect = vec4(vulkanic_source_dh_vertex.atlas_u0, vulkanic_source_dh_vertex.atlas_v0, vulkanic_source_dh_vertex.atlas_u1, vulkanic_source_dh_vertex.atlas_v1);
    vulkanic_source_dh_atlas_tint_and_material = vulkanic_source_dh_vertex.light_normal_tint_material >> 24u;
"#,
    );
    Ok(lowered)
}

pub(crate) fn inject_exact_atlas_distant_horizons_fragment_adapter(
    source: &str,
    locations: ExactAtlasDistantHorizonsVaryingLocations,
) -> GalResult<String> {
    if !source.contains("void main()") {
        return Err(GalError::invalid_argument(
            "Distant Horizons source fragment adapter requires a void main function",
        ));
    }
    let declarations = r#"
layout(set = 2, binding = 0) uniform texture2D vulkanic_source_dh_atlas_texture;
layout(set = 2, binding = 1) uniform sampler vulkanic_source_dh_atlas_sampler;
#define VULKANIC_SOURCE_DH_ATLAS_TILE_LOCATION __VULKANIC_SOURCE_DH_ATLAS_TILE_LOCATION__
#define VULKANIC_SOURCE_DH_ATLAS_RECT_LOCATION __VULKANIC_SOURCE_DH_ATLAS_RECT_LOCATION__
#define VULKANIC_SOURCE_DH_ATLAS_TINT_LOCATION __VULKANIC_SOURCE_DH_ATLAS_TINT_LOCATION__
layout(location = VULKANIC_SOURCE_DH_ATLAS_TILE_LOCATION) in vec2 vulkanic_source_dh_atlas_tile_uv;
layout(location = VULKANIC_SOURCE_DH_ATLAS_RECT_LOCATION) flat in vec4 vulkanic_source_dh_atlas_rect;
layout(location = VULKANIC_SOURCE_DH_ATLAS_TINT_LOCATION) flat in uint vulkanic_source_dh_atlas_tint_and_material;
vec4 vulkanic_source_dh_atlas_color() {
    vec2 extent = vec2(textureSize(sampler2D(vulkanic_source_dh_atlas_texture, vulkanic_source_dh_atlas_sampler), 0));
    vec2 texel = vec2(0.5) / extent;
    vec2 sprite_min = vulkanic_source_dh_atlas_rect.xy + texel;
    vec2 sprite_max = vulkanic_source_dh_atlas_rect.zw - texel;
    vec2 atlas_uv = mix(sprite_min, max(sprite_min, sprite_max), fract(vulkanic_source_dh_atlas_tile_uv));
    vec4 color = texture(sampler2D(vulkanic_source_dh_atlas_texture, vulkanic_source_dh_atlas_sampler), atlas_uv);
    // DH's reduced vertex color is still a semantic material input after an
    // exact sprite replaces the reduced-color-only route. It carries the
    // source material's face color for ordinary blocks as well as biome tint
    // for tinted blocks. Applying it only to the tint bit loses that source
    // factor for materials such as redstone ore and terracotta.
    color.rgb *= glColor.rgb;
    color.a *= glColor.a;
    return color;
}
"#;
    // The lowered source declares pack varyings, including `glColor`, between
    // its version line and main. Insert our extra interface after that source
    // interface so the adapter can consume the selected program's color
    // semantic without assuming a declaration order.
    let declarations = declarations
        .replace(
            "__VULKANIC_SOURCE_DH_ATLAS_TILE_LOCATION__",
            &locations.tile_uv.to_string(),
        )
        .replace(
            "__VULKANIC_SOURCE_DH_ATLAS_RECT_LOCATION__",
            &locations.atlas_rect.to_string(),
        )
        .replace(
            "__VULKANIC_SOURCE_DH_ATLAS_TINT_LOCATION__",
            &locations.tint_and_material.to_string(),
        );
    let mut lowered = insert_before_main(source, &declarations)?;
    let anchor = "vec4 color = vec4(glColor.rgb, 1.0);";
    if !lowered.contains(anchor) {
        return Err(GalError::unsupported_feature(
            "Distant Horizons source fragment adapter could not locate the source color initialization",
        ));
    }
    lowered = lowered.replacen(anchor, "vec4 color = vulkanic_source_dh_atlas_color();", 1);
    apply_exact_atlas_distant_horizons_fragment_probe(
        &mut lowered,
        crate::core::environment::var("MATTMC_RUST_SELECTED_SOURCE_FRAGMENT_PROBE")
            .ok()
            .as_deref(),
    )?;
    Ok(lowered)
}

/// The regular selected-source fragment probe operates on the near-terrain
/// `tex`/`texCoord` interface. The exact-atlas DH adapter has a deliberately
/// different interface, so its probe must be injected here, after this
/// adapter has supplied the atlas color. Keeping it local prevents a capture
/// receipt from claiming that a near-terrain-only probe observed DH output.
pub(crate) fn apply_exact_atlas_distant_horizons_fragment_probe(
    source: &mut String,
    mode: Option<&str>,
) -> GalResult<()> {
    let Some(mode) = mode.map(str::trim).filter(|mode| !mode.is_empty()) else {
        return Ok(());
    };
    let (label, probe) = match mode {
        "atlas" => (
            "atlas",
            "out_distant_horizons_lit_color = color;\n    return;",
        ),
        "atlas-uv" => (
            "atlas-uv",
            "out_distant_horizons_lit_color = vec4(fract(vulkanic_source_dh_atlas_tile_uv), 0.0, 1.0);\n    return;",
        ),
        "atlas-alpha" => (
            "atlas-alpha",
            "out_distant_horizons_lit_color = vec4(vec3(color.a), 1.0);\n    return;",
        ),
        // These checkpoints preserve the real selected-source program and
        // its Rust-owned target/resources while locating whether the first
        // visible loss is before or inside the pack lighting function.
        // They remain unavailable unless a graphics-audit capture opts in.
        "pre-lighting" => (
            "pre-lighting",
            "out_distant_horizons_lit_color = color;\n    return;",
        ),
        "lightmap" => (
            "lightmap",
            "out_distant_horizons_lit_color = vec4(lmCoord, 0.0, 1.0);\n    return;",
        ),
        // Other modes are defined by the near-terrain source interface and
        // intentionally leave the DH program unmodified. This lets a single
        // capture use a terrain-only probe without inventing DH semantics.
        _ => return Ok(()),
    };
    let anchor = "vec4 color = vulkanic_source_dh_atlas_color();";
    if !source.contains(anchor) {
        return Err(GalError::invalid_argument(format!(
            "exact-atlas Distant Horizons {label} probe could not locate the adapter color initialization"
        )));
    }
    let insertion = match label {
        "pre-lighting" => {
            let anchor = "DoLighting(color, shadowMult,";
            source.find(anchor).ok_or_else(|| {
                GalError::invalid_argument(
                    "exact-atlas Distant Horizons pre-lighting probe could not locate the source lighting call",
                )
            })?
        }
        _ => source.find(anchor).expect("checked source anchor") + anchor.len(),
    };
    source.insert_str(
        insertion,
        &format!("\n    // selected-source diagnostic probe: {label}\n    {probe}\n"),
    );
    Ok(())
}

pub(crate) fn main_function_closing_brace(source: &str) -> Option<usize> {
    let main = source.find("void main()")?;
    let open = source[main..].find('{')? + main;
    let mut depth = 0usize;
    for (offset, character) in source[open..].char_indices() {
        match character {
            '{' => depth += 1,
            '}' => {
                depth = depth.checked_sub(1)?;
                if depth == 0 {
                    return Some(open + offset);
                }
            }
            _ => {}
        }
    }
    None
}

pub(crate) fn insert_before_main(source: &str, declarations: &str) -> GalResult<String> {
    let main = source.find("void main()").ok_or_else(|| {
        GalError::invalid_argument("lowered shader source has no void main function")
    })?;
    let mut output = String::with_capacity(source.len() + declarations.len());
    output.push_str(&source[..main]);
    output.push_str(declarations);
    output.push_str(&source[main..]);
    Ok(output)
}

/// Fixed Rust-owned descriptor ABI inserted by the DH source lowerer.
/// Binding numbers describe owned semantic resources only; they do not map to
/// Java, Iris, OpenGL, or Vulkan objects.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct DistantHorizonsSourceExecutionInterface {
    pub vertex_stream: TerrainSourceFixedBinding,
    pub vertex_stride: u32,
    /// The texture identity carried by the source vertex stream. This is
    /// semantic shader-pack data, never a backend texture binding.
    pub material_identity_contract: DistantHorizonsMaterialIdentityContract,
    pub column_frame: TerrainSourceFixedBinding,
    pub column_frame_bytes: u32,
    pub scalar_uniforms: Option<TerrainSourceFixedBinding>,
    pub scalar_uniform_bytes: u32,
    pub scalar_uniform_fields: Vec<TerrainSourceUniformField>,
}

/// Describes whether a Distant Horizons source stream can identify the exact
/// Minecraft material sampled by a terrain fragment. A reduced vertex color
/// and material category are deliberately insufficient: they cannot stand in
/// for atlas UVs and an atlas-backed material identity.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum DistantHorizonsMaterialIdentityContract {
    ReducedColorMaterialCategory,
    AtlasBacked,
}

/// Backend-neutral layouts for a lowered DH source program. Descriptor set
/// zero contains only fixed DH geometry/frame/scalar semantics; descriptor
/// set one contains source-declared semantic sampler/image roles.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct DistantHorizonsSourceExecutionLayouts {
    pub source_data: ResourceLayoutDesc,
    pub pack_resources: ResourceLayoutDesc,
}

impl DistantHorizonsSourceExecutionInterface {
    pub(crate) const VERTEX_STREAM: TerrainSourceFixedBinding = TerrainSourceFixedBinding {
        set: 0,
        binding: 0,
        kind: TerrainSourceBindingKind::StorageBuffer,
    };

    pub(crate) const COLUMN_FRAME: TerrainSourceFixedBinding = TerrainSourceFixedBinding {
        set: 0,
        binding: 1,
        kind: TerrainSourceBindingKind::UniformBuffer,
    };

    pub(crate) const SCALAR_UNIFORMS: TerrainSourceFixedBinding = TerrainSourceFixedBinding {
        set: 0,
        binding: 2,
        kind: TerrainSourceBindingKind::UniformBuffer,
    };

    pub(crate) fn from_lowered_pair(lowered: &LoweredDistantHorizonsSourcePair) -> Self {
        let contract = lowered.uniform_contract();
        Self {
            vertex_stream: Self::VERTEX_STREAM,
            vertex_stride: DISTANT_HORIZONS_SOURCE_VERTEX_BYTES as u32,
            material_identity_contract:
                DistantHorizonsMaterialIdentityContract::ReducedColorMaterialCategory,
            column_frame: Self::COLUMN_FRAME,
            column_frame_bytes: DISTANT_HORIZONS_SOURCE_COLUMN_FRAME_BYTES as u32,
            scalar_uniforms: (!contract.fields().is_empty()).then_some(Self::SCALAR_UNIFORMS),
            scalar_uniform_bytes: contract.std140_size(),
            scalar_uniform_fields: contract.fields().to_vec(),
        }
    }

    pub fn validate(&self) -> GalResult<()> {
        if self.vertex_stream != Self::VERTEX_STREAM
            || !matches!(
                self.vertex_stride as usize,
                DISTANT_HORIZONS_SOURCE_VERTEX_BYTES
                    | DISTANT_HORIZONS_EXACT_ATLAS_SOURCE_VERTEX_BYTES
            )
        {
            return Err(GalError::invalid_argument(format!(
                "Distant Horizons source vertex stream must use fixed set 0 binding 0 with {}-byte reduced-color or {}-byte exact-atlas records",
                DISTANT_HORIZONS_SOURCE_VERTEX_BYTES,
                DISTANT_HORIZONS_EXACT_ATLAS_SOURCE_VERTEX_BYTES,
            )));
        }
        if self.column_frame != Self::COLUMN_FRAME
            || self.column_frame_bytes != DISTANT_HORIZONS_SOURCE_COLUMN_FRAME_BYTES as u32
        {
            return Err(GalError::invalid_argument(format!(
                "Distant Horizons source column frame must use fixed set 0 binding 1 with {} bytes",
                DISTANT_HORIZONS_SOURCE_COLUMN_FRAME_BYTES
            )));
        }
        validate_source_scalar_uniform_block(
            self.scalar_uniforms,
            self.scalar_uniform_bytes,
            &self.scalar_uniform_fields,
            Self::SCALAR_UNIFORMS,
            "Distant Horizons source",
        )
    }

    pub fn has_exact_material_texture_identity(&self) -> bool {
        matches!(
            self.material_identity_contract,
            DistantHorizonsMaterialIdentityContract::AtlasBacked
        )
    }
}

impl LoweredDistantHorizonsSourceProgram {
    /// A selected shader-pack DH program may own a frame only when its copied
    /// semantic stream can identify the exact material texture it samples.
    /// Declaring an atlas sampler alone is not sufficient; the vertex stream
    /// must also carry atlas-addressable material identity.
    pub fn has_exact_material_texture_identity(&self) -> bool {
        self.execution_interface
            .has_exact_material_texture_identity()
            && self
                .opaque_resource_bindings
                .bindings()
                .iter()
                .any(|binding| matches!(binding.role(), TerrainSourceResourceRole::MaterialAtlas))
    }

    pub fn shader_module_descriptors(&self, conventions: ShaderConventions) -> [ShaderModuleDesc; 2] {
        [
            self.vertex.shader_module_descriptor(conventions),
            self.fragment.shader_module_descriptor(conventions),
        ]
    }

    pub fn pack_scalar_uniforms(&self, frame: &TerrainSourceUniformFrame) -> GalResult<Vec<u8>> {
        let bytes = frame.pack_std140(&self.scalar_uniform_requirements)?;
        if bytes.len() != self.execution_interface.scalar_uniform_bytes as usize {
            return Err(GalError::invalid_argument(format!(
                "Distant Horizons source scalar uniform pack is {} bytes but program ABI requires {}",
                bytes.len(), self.execution_interface.scalar_uniform_bytes
            )));
        }
        Ok(bytes)
    }

    pub fn execution_resource_layouts(&self) -> GalResult<DistantHorizonsSourceExecutionLayouts> {
        self.execution_interface.validate()?;
        let mut source_data_bindings = vec![
            resource_binding_descriptor(self.execution_interface.vertex_stream, false),
            resource_binding_descriptor(self.execution_interface.column_frame, true),
        ];
        if let Some(scalar_uniforms) = self.execution_interface.scalar_uniforms {
            source_data_bindings.push(resource_binding_descriptor(scalar_uniforms, true));
        }
        source_data_bindings.sort_by_key(|binding| binding.binding);
        validate_unique_layout_bindings("Distant Horizons source data", &source_data_bindings)?;
        Ok(DistantHorizonsSourceExecutionLayouts {
            source_data: ResourceLayoutDesc {
                label: format!("{}:source-data", self.identity.as_str()),
                bindings: source_data_bindings,
            },
            pack_resources: source_pack_resource_layout(
                format!("{}:pack-resources", self.identity.as_str()),
                &self.opaque_resource_bindings,
            )?,
        })
    }

    /// Requires every source-declared sampler/image to be available as a
    /// Rust-owned semantic resource from the same shader-pack generation.
    /// This admits neither legacy DH textures nor Iris-managed bindings.
    pub fn require_semantic_resources(
        &self,
        availability: &TerrainSourceResourceAvailabilitySet,
    ) -> GalResult<()> {
        if availability.shader_pack_generation() != self.shader_pack_generation {
            return Err(GalError::invalid_argument(format!(
                "Distant Horizons source program generation {} does not match resource availability generation {}",
                self.shader_pack_generation,
                availability.shader_pack_generation()
            )));
        }
        for binding in self.opaque_resource_bindings.bindings() {
            if availability.resource_for(binding.role()).is_none() {
                return Err(GalError::invalid_argument(format!(
                    "Distant Horizons source resource '{}' is unavailable for semantic role '{}'",
                    binding.resource_name(),
                    binding.role().semantic_name()
                )));
            }
        }
        Ok(())
    }

    /// Builds the deterministic source pack-resource set for the DH program.
    /// The resource set contains only owned GAL handles after their semantic
    /// role and generation were checked above.
    pub fn pack_resource_set_desc(
        &self,
        label: impl Into<String>,
        layout: Handle,
        resources: &TerrainSourceOwnedResourceSet,
    ) -> GalResult<ResourceSetDesc> {
        if layout.kind() != Some(HandleKind::ResourceLayout) {
            return Err(GalError::invalid_argument(
                "Distant Horizons source pack resources require a GAL resource-layout handle",
            ));
        }
        self.execution_resource_layouts()?;
        self.require_semantic_resources(resources.availability())?;
        let mut bindings = Vec::with_capacity(self.opaque_resource_bindings.bindings().len());
        for source_binding in self.opaque_resource_bindings.bindings() {
            let (resource, kind, access) = match source_binding.kind() {
                TerrainSourceOpaqueResourceKind::CombinedTextureSampler => (
                    resources
                        .combined_sampler_for(source_binding.role())
                        .ok_or_else(|| {
                            GalError::invalid_argument(format!(
                                "Distant Horizons source resource '{}' has no owned sampler for semantic role '{}'",
                                source_binding.resource_name(),
                                source_binding.role().semantic_name()
                            ))
                        })?,
                    ResourceBindingKind::CombinedTextureSampler,
                    AccessFlags::READ,
                ),
                TerrainSourceOpaqueResourceKind::StorageImage => (
                    resources
                        .storage_texture_for(source_binding.role())
                        .ok_or_else(|| {
                            GalError::invalid_argument(format!(
                                "Distant Horizons source resource '{}' has no owned storage texture view for semantic role '{}'",
                                source_binding.resource_name(),
                                source_binding.role().semantic_name()
                            ))
                        })?,
                    ResourceBindingKind::StorageTexture,
                    source_storage_access(source_binding.qualifiers())?,
                ),
            };
            bindings.push(ResourceBinding {
                binding: source_binding.binding(),
                array_index: 0,
                resource,
                kind,
                access,
                dynamic_offsets: Vec::new(),
                buffer_range: None,
            });
        }
        Ok(ResourceSetDesc {
            label: label.into(),
            layout,
            bindings,
        })
    }
}

/// Forms the owned preparation artifact for the selected Distant Horizons
/// source stage. Unlike near terrain, this requires the exact DH column-stream
/// ABI and exposes only the source's own named distant color output. It is
/// deliberately not a route selector or a compatibility path for Iris.
pub fn prepare_lowered_distant_horizons_source_program(
    contract: &DistantHorizonsPassContract,
    lowered: &LoweredDistantHorizonsSourcePair,
    opaque_resource_bindings: &TerrainSourceOpaqueResourceBindingPlan,
) -> GalResult<LoweredDistantHorizonsSourceProgram> {
    if contract.pack_name.trim().is_empty() || contract.generation == 0 {
        return Err(GalError::invalid_argument(
            "Distant Horizons source program requires a non-empty pack name and non-zero generation",
        ));
    }
    if lowered.fragment().outputs() != [DistantHorizonsFragmentOutput::LitColor] {
        return Err(GalError::unsupported_feature(
            "Distant Horizons source program requires exactly one named lit-color output",
        ));
    }
    match (contract.pass_kind, contract.translucent_blend) {
        (DistantHorizonsPassKind::Opaque, None)
        | (DistantHorizonsPassKind::Translucent, Some(TerrainTranslucentBlend::SourceAlphaOver)) => {
        }
        (DistantHorizonsPassKind::Opaque, Some(_)) => {
            return Err(GalError::invalid_argument(
                "opaque Distant Horizons source program must not carry translucent blend semantics",
            ));
        }
        (DistantHorizonsPassKind::Translucent, None) => {
            return Err(GalError::invalid_argument(
                "translucent Distant Horizons source program requires explicit source blend semantics",
            ));
        }
    }
    lowered.require_backend_neutral_lowering()?;
    lowered.require_matching_opaque_resource_bindings(opaque_resource_bindings)?;
    let execution_interface = DistantHorizonsSourceExecutionInterface::from_lowered_pair(lowered);
    execution_interface.validate()?;
    let scalar_uniform_requirements =
        TerrainSourceUniformRequirements::from_contract(lowered.uniform_contract())?;
    scalar_uniform_requirements.require_fully_semantic()?;
    let program = LoweredDistantHorizonsSourceProgram {
        identity: ProgramIdentity::new(format!(
            "vulkanic:shader-pack/{}/distant_horizons_{}_source_gen{}{}",
            contract.pack_name.to_ascii_lowercase(),
            match contract.pass_kind {
                DistantHorizonsPassKind::Opaque => "opaque",
                DistantHorizonsPassKind::Translucent => "translucent",
            },
            contract.generation,
            scope_identity_tag(contract.scope)
        )),
        shader_pack_generation: contract.generation,
        pass_kind: contract.pass_kind,
        translucent_blend: contract.translucent_blend,
        vertex: ShaderStageSource {
            stage: ShaderStageKind::Vertex,
            label: format!(
                "{}:lowered-distant-horizons-vertex",
                lowered.vertex().entry_path()
            ),
            source: lowered.vertex().source().to_string(),
            entry_point: "main".to_string(),
        },
        fragment: ShaderStageSource {
            stage: ShaderStageKind::Fragment,
            label: format!(
                "{}:lowered-distant-horizons-fragment",
                lowered.fragment().entry_path()
            ),
            source: lowered.fragment().source().to_string(),
            entry_point: "main".to_string(),
        },
        execution_interface,
        scalar_uniform_requirements,
        opaque_resource_bindings: opaque_resource_bindings.clone(),
        required_resources: if contract
            .required_resources
            .contains(&TerrainPassRequiredResource::ColoredVoxelLightVolume)
        {
            vec![TerrainProgramResource::ColoredVoxelLightVolume]
        } else {
            Vec::new()
        },
    };
    program.execution_resource_layouts()?;
    Ok(program)
}

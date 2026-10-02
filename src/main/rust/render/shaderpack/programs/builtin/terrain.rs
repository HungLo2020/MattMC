//! Built-in terrain material programs: direct, compact and deferred terrain and the Complementary subset.

use super::*;

pub fn minimal_terrain_solid_program() -> TerrainMaterialProgram {
    minimal_terrain_material_program(TerrainMaterialProgramKind::Opaque)
}

pub fn minimal_terrain_cutout_program() -> TerrainMaterialProgram {
    minimal_terrain_material_program(TerrainMaterialProgramKind::Cutout)
}

pub fn minimal_terrain_translucent_program() -> TerrainMaterialProgram {
    minimal_terrain_material_program(TerrainMaterialProgramKind::Translucent)
}

pub fn minimal_direct_terrain_solid_program() -> TerrainMaterialProgram {
    minimal_direct_terrain_material_program(TerrainMaterialProgramKind::Opaque)
}

pub fn minimal_direct_terrain_cutout_program() -> TerrainMaterialProgram {
    minimal_direct_terrain_material_program(TerrainMaterialProgramKind::Cutout)
}

pub const COMPACT_DIRECT_TERRAIN_OPAQUE_PROGRAM_ID: &str =
    "vulkanic:builtin/direct_terrain_opaque_compact32_v1";

pub const COMPACT_DIRECT_TERRAIN_CUTOUT_PROGRAM_ID: &str =
    "vulkanic:builtin/direct_terrain_cutout_compact32_v1";

pub const COMPACT_DIRECT_TERRAIN_TRANSLUCENT_PROGRAM_ID: &str =
    "vulkanic:builtin/direct_terrain_translucent_compact32_v1";

pub const STATIC_COMPACT_DIRECT_TERRAIN_OPAQUE_PROGRAM_ID: &str =
    "vulkanic:builtin/direct_terrain_opaque_compact32_static_v1";

pub const STATIC_COMPACT_DIRECT_TERRAIN_CUTOUT_PROGRAM_ID: &str =
    "vulkanic:builtin/direct_terrain_cutout_compact32_static_v1";

pub const STATIC_COMPACT_DIRECT_TERRAIN_TRANSLUCENT_PROGRAM_ID: &str =
    "vulkanic:builtin/direct_terrain_translucent_compact32_static_v1";

/// Shader-off atlas-backed terrain has a deliberately smaller GPU-only vertex ABI.
/// The authoritative copied mesh remains the rich semantic form used by
/// source-derived programs; this program may only be selected alongside the
/// matching `DirectTerrain32` lowering in the world frontend.
pub fn minimal_compact_direct_terrain_program(
    kind: TerrainMaterialProgramKind,
) -> TerrainMaterialProgram {
    let mut program = minimal_direct_terrain_material_program(kind);
    program.identity = ProgramIdentity::new(match kind {
        TerrainMaterialProgramKind::Opaque => COMPACT_DIRECT_TERRAIN_OPAQUE_PROGRAM_ID,
        TerrainMaterialProgramKind::Cutout => COMPACT_DIRECT_TERRAIN_CUTOUT_PROGRAM_ID,
        TerrainMaterialProgramKind::Translucent => COMPACT_DIRECT_TERRAIN_TRANSLUCENT_PROGRAM_ID,
    });
    program.vertex.label = format!(
        "minimal-direct-terrain-{}-compact32.vertex",
        kind.label_suffix()
    );
    program.vertex.source = compact_direct_terrain_vertex_source();
    // Compact terrain instances carry only the static terrain material
    // contract. Entity-only overlay, per-face-back-color, and model cutoff
    // branches stay on the rich direct-terrain program used by future source
    // and shader-pack paths.
    program.fragment.source = compact_direct_terrain_fragment_source(program.fragment.source);
    program
}

/// Compact direct terrain variant for a validated one-frame texture asset.
/// The instance ABI remains unchanged so static and animated sections can
/// share geometry and stream packing, while the fragment interface omits the
/// animation varyings and second texture sample entirely.
pub fn minimal_static_compact_direct_terrain_program(
    kind: TerrainMaterialProgramKind,
) -> TerrainMaterialProgram {
    let mut program = minimal_compact_direct_terrain_program(kind);
    program.identity = ProgramIdentity::new(match kind {
        TerrainMaterialProgramKind::Opaque => STATIC_COMPACT_DIRECT_TERRAIN_OPAQUE_PROGRAM_ID,
        TerrainMaterialProgramKind::Cutout => STATIC_COMPACT_DIRECT_TERRAIN_CUTOUT_PROGRAM_ID,
        TerrainMaterialProgramKind::Translucent => {
            STATIC_COMPACT_DIRECT_TERRAIN_TRANSLUCENT_PROGRAM_ID
        }
    });
    program.vertex.label = format!(
        "minimal-direct-terrain-{}-compact32-static.vertex",
        kind.label_suffix()
    );
    program.fragment.label = format!(
        "minimal-direct-terrain-{}-compact32-static.fragment",
        kind.label_suffix()
    );
    program.vertex.source = compact_static_direct_terrain_vertex_source(program.vertex.source);
    program.fragment.source =
        compact_static_direct_terrain_fragment_source(program.fragment.source);
    program
}

pub(crate) fn compact_static_direct_terrain_vertex_source(source: String) -> String {
    let source = source
        .replace("layout(location = 2) flat out vec4 v_material;\n", "")
        .replace(
            "layout(location = 6) flat out vec4 v_animation_region;\n",
            "",
        )
        .replace(
            "layout(location = 7) flat out vec4 v_animation_next_region;\n",
            "",
        )
        .replace("    v_material = instance.material;\n", "")
        .replace("    v_animation_region = instance.animation_region;\n", "")
        .replace(
            "    v_animation_next_region = instance.animation_next_region;\n",
            "",
        );
    // Keep a process-local opt-out for paired GPU captures. It changes only
    // this static shader source, so the A/B isolates the branch removal while
    // retaining the same geometry, resources, and draw schedule.
    if matches!(
        crate::core::environment::var("MATTMC_RUST_DISABLE_STATIC_TERRAIN_LIGHT_BRANCH").as_deref(),
        Ok("1") | Ok("true") | Ok("yes")
    ) {
        return source;
    }
    // Static terrain owns its baked light in each copied vertex. The semantic
    // frame contract rejects per-instance light overrides for this stratum, so
    // keep the shared ABI layout but remove the dead material read/branch from
    // the hot vertex path.
    source
        .replace("    uint material_semantics = uint(instance.material.w);\n", "")
        .replace(
            "    // Preserve the shared ABI's explicit per-instance light override. Normal\n    // static terrain leaves this bit clear; retaining the branch keeps custom\n    // copied terrain semantics intact without reintroducing entity lighting.\n    if ((material_semantics & 1024u) != 0u) {\n        uint packed_instance_light = floatBitsToUint(instance.texture_transform.w);\n        light_coordinates = vec2(\n            float(packed_instance_light & 0xffu) / 240.0,\n            float((packed_instance_light >> 16u) & 0xffu) / 240.0);\n    }\n",
            "",
        )
}

pub(crate) fn compact_static_direct_terrain_fragment_source(source: String) -> String {
    source
        .replace("layout(location = 2) flat in vec4 v_material;\n", "")
        .replace("layout(location = 6) flat in vec4 v_animation_region;\n", "")
        .replace("layout(location = 7) flat in vec4 v_animation_next_region;\n", "")
        .replace(
            "    vec2 sample_uv = v_animation_region.xy + v_uv * v_animation_region.zw;\n",
            "    vec2 sample_uv = v_uv;\n",
        )
        .replace(
            "    if (v_material.y > 0.5) {\n        vec2 next_uv = v_animation_next_region.xy + v_uv * v_animation_next_region.zw;\n        vec4 next_color = texture(sampler2D(Tex0, Samp0), next_uv);\n        color = mix(color, next_color, clamp(v_material.z, 0.0, 1.0));\n    }\n",
            "",
        )
}

pub(crate) fn compact_direct_terrain_fragment_source(source: String) -> String {
    source
        .replace("layout(location = 8) flat in vec4 v_overlay_color;\n", "")
        .replace("layout(location = 15) in vec4 v_back_color;\n", "")
        .replace(
            "    bool per_face_lighting = (uint(v_material.w) & 64u) != 0u;\n    color *= per_face_lighting && !gl_FrontFacing ? v_back_color : v_color;\n    if (v_overlay_color.a > 0.0) {\n        color.rgb = mix(color.rgb, v_overlay_color.rgb, v_overlay_color.a);\n    }\n",
            "    color *= v_color;\n",
        )
        .replace(
            "    bool model_cutout = (uint(v_material.w) & 32u) != 0u;\n    float alpha_cutoff = model_cutout ? v_material.x\n        : float[4](0.0, 0.1, 0.1, 1.0)[alpha_cutoff_class];\n",
            "    float alpha_cutoff = float[4](0.0, 0.1, 0.1, 1.0)[alpha_cutoff_class];\n",
        )
}

/// Direct forward counterpart of the deferred translucent terrain program.
/// Vanilla's non-source route has one color attachment, so it must never bind
/// the multi-output G-buffer fragment merely because the material is water or
/// another translucent block.
pub fn minimal_direct_terrain_translucent_program() -> TerrainMaterialProgram {
    minimal_direct_terrain_material_program(TerrainMaterialProgramKind::Translucent)
}

/// Blended model texture with the vanilla item/entity alpha-discard contract.
/// This is not Sodium translucent terrain: model sampling uses implicit LOD,
/// and alpha below 0.1 must discard before any depth/stencil write.
pub fn minimal_direct_model_translucent_cutout_program() -> TerrainMaterialProgram {
    let mut program = minimal_direct_terrain_translucent_program();
    program.identity = ProgramIdentity::new("vulkanic:builtin/direct_model_translucent_cutout_v1");
    program.vertex.label = "direct-model-translucent-cutout.vertex".to_string();
    program.vertex.source = program.vertex.source.replacen(
        "#version 450\n",
        "#version 450\n#define VULKANIC_MODEL_TRANSLUCENT_CUTOUT 1\n",
        1,
    );
    program.fragment.label = "direct-model-translucent-cutout.fragment".to_string();
    program.fragment.source = program.fragment.source.replacen(
        "#version 450\n",
        "#version 450\n#define VULKANIC_MODEL_TRANSLUCENT_CUTOUT 1\n",
        1,
    );
    program
}

pub fn minimal_terrain_material_program(
    kind: TerrainMaterialProgramKind,
) -> TerrainMaterialProgram {
    TerrainMaterialProgram {
        identity: kind.identity(),
        vertex: ShaderStageSource {
            stage: ShaderStageKind::Vertex,
            label: format!("minimal-terrain-{}.vertex", kind.label_suffix()),
            source: MINIMAL_TERRAIN_MATERIAL_VERTEX.to_string(),
            entry_point: "main".to_string(),
        },
        fragment: ShaderStageSource {
            stage: ShaderStageKind::Fragment,
            label: format!("minimal-terrain-{}.fragment", kind.label_suffix()),
            // Frozen Sodium compiles `USE_FRAGMENT_DISCARD` exclusively for
            // the cutout pass.  Fabulous opaque terrain shares this deferred
            // program, so it must not accidentally inherit cutout's
            // front-face/alpha rejection before deferred lighting.  The
            // semantic pass kind selects the source-equivalent define here;
            // no backend state or Java renderer policy is involved.
            source: minimal_deferred_terrain_fragment_source(kind),
            entry_point: "main".to_string(),
        },
        required_resources: Vec::new(),
    }
}

/// Builds the first executable Rust-owned lowering for the normal terrain
/// contract. It is deliberately admitted only for a fully supported source
/// profile; callers must not use it as a fallback for a richer selected pack.
pub fn complementary_terrain_subset_program(
    contract: &TerrainPassContract,
    kind: TerrainMaterialProgramKind,
) -> GalResult<TerrainMaterialProgram> {
    complementary_terrain_subset_program_with_resources(contract, kind, None, 0)
}

/// Selects the source-derived lowering only after every semantic resource
/// required by the selected profile has a complete, matching generation.
pub fn complementary_terrain_subset_program_with_resources(
    contract: &TerrainPassContract,
    kind: TerrainMaterialProgramKind,
    voxel_light_volume: Option<&VoxelLightVolumeReadiness>,
    frame_counter: u64,
) -> GalResult<TerrainMaterialProgram> {
    contract.require_selected_subset_with_resources(voxel_light_volume, frame_counter)?;
    if !matches!(
        kind,
        TerrainMaterialProgramKind::Opaque | TerrainMaterialProgramKind::Cutout
    ) {
        return Err(
            crate::render::vulkanic::error::GalError::unsupported_feature(
                "Complementary terrain subset only supports opaque and cutout materials",
            ),
        );
    }
    let requires_colored_voxel_light = contract
        .required_resources
        .contains(&crate::render::shaderpack::contracts::terrain::TerrainPassRequiredResource::ColoredVoxelLightVolume);
    Ok(TerrainMaterialProgram {
        identity: ProgramIdentity::new(format!(
            "vulkanic:shader-pack/{}/terrain_{}_subset_v1",
            contract.pack_name.to_ascii_lowercase(),
            kind.label_suffix()
        )),
        vertex: ShaderStageSource {
            stage: ShaderStageKind::Vertex,
            label: format!("terrain-contract-{}.vertex", kind.label_suffix()),
            source: MINIMAL_TERRAIN_MATERIAL_VERTEX.to_string(),
            entry_point: "main".to_string(),
        },
        fragment: ShaderStageSource {
            stage: ShaderStageKind::Fragment,
            label: format!("terrain-contract-{}.fragment", kind.label_suffix()),
            source: complementary_terrain_subset_fragment_source(requires_colored_voxel_light),
            entry_point: "main".to_string(),
        },
        required_resources: if requires_colored_voxel_light {
            vec![TerrainProgramResource::ColoredVoxelLightVolume]
        } else {
            Vec::new()
        },
    })
}

/// Keeps the shader interface exactly aligned with the semantic resource
/// contract. A profile without `ColoredVoxelLighting` must not declare an
/// unbound set-1 D3 interface merely because another admitted profile uses it.
pub(crate) fn complementary_terrain_subset_fragment_source(requires_colored_voxel_light: bool) -> String {
    if requires_colored_voxel_light {
        COMPLEMENTARY_TERRAIN_SUBSET_FRAGMENT.replacen(
            "#version 450\n",
            "#version 450\n#define VULKANIC_TERRAIN_COLORED_VOXEL_LIGHT 1\n",
            1,
        )
    } else {
        COMPLEMENTARY_TERRAIN_SUBSET_FRAGMENT.to_string()
    }
}

pub fn minimal_direct_terrain_material_program(
    kind: TerrainMaterialProgramKind,
) -> TerrainMaterialProgram {
    TerrainMaterialProgram {
        identity: ProgramIdentity::new(match kind {
            TerrainMaterialProgramKind::Opaque => "vulkanic:builtin/direct_terrain_opaque_v1",
            TerrainMaterialProgramKind::Cutout => "vulkanic:builtin/direct_terrain_cutout_v1",
            TerrainMaterialProgramKind::Translucent => {
                "vulkanic:builtin/direct_terrain_translucent_v1"
            }
        }),
        vertex: ShaderStageSource {
            stage: ShaderStageKind::Vertex,
            label: format!("minimal-direct-terrain-{}.vertex", kind.label_suffix()),
            source: minimal_direct_terrain_vertex_source(),
            entry_point: "main".to_string(),
        },
        fragment: ShaderStageSource {
            stage: ShaderStageKind::Fragment,
            label: format!("minimal-direct-terrain-{}.fragment", kind.label_suffix()),
            // Frozen Sodium compiles `USE_FRAGMENT_DISCARD` only for the
            // cutout terrain pass.  In particular, translucent terrain must
            // retain both faces and its source alpha for the later Fabulous
            // composition; applying the cutout discard rule there changes
            // glass and fluid coverage before the compositor ever sees it.
            source: minimal_direct_terrain_fragment_source(kind),
            entry_point: "main".to_string(),
        },
        required_resources: Vec::new(),
    }
}

pub(crate) fn minimal_direct_terrain_fragment_source(kind: TerrainMaterialProgramKind) -> String {
    let source =
        terrain_fragment_source_with_pass_define(MINIMAL_TERRAIN_MATERIAL_FRAGMENT_DIRECT, kind);
    terrain_fragment_coordinate_probe(
        source,
        crate::core::environment::var("MATTMC_RUST_TERRAIN_COORDINATE_PROBE")
            .ok()
            .as_deref(),
    )
}

/// Opt-in diagnostic output, never a normal material or parity result. The
/// existing explicit pass and Rust presenter carry the low 24 float bits in
/// RGB8; the positive normalized atlas coordinate's exponent is known from
/// the copied CPU source. No backend objects or GPU state enter the callsite.
pub(crate) fn terrain_fragment_coordinate_probe(source: String, probe: Option<&str>) -> String {
    if probe == Some("clip-bits") {
        let source = source.replacen(
            "#version 450\n",
            "#version 450\nlayout(location = 14) flat in vec4 diagnostic_clip;\n",
            1,
        );
        let anchor = "vec2 sample_uv = v_animation_region.xy + v_uv * v_animation_region.zw;";
        return source.replacen(anchor, &format!("{anchor}\n\
            // DIAGNOSTIC ONLY: complete clip float bits across eight adjacent pixels.\n\
            // Retain the previously measured UV sample as an instrumentation control.\n\
            if (all(equal(ivec2(gl_FragCoord.xy), ivec2(764, 320)))) {{\n\
                uint uv_bits = floatBitsToUint(sample_uv.y);\n\
                out_color = vec4(vec3(uv_bits & 255u, (uv_bits >> 8u) & 255u, (uv_bits >> 16u) & 255u) / 255.0, 1.0);\n\
                return;\n\
            }}\n\
            uint probe_x = uint(gl_FragCoord.x);\n\
            uint coordinate_bits = floatBitsToUint(diagnostic_clip[(probe_x >> 1u) & 3u]);\n\
            uvec3 probe_bytes = (probe_x & 1u) == 0u\n\
                ? uvec3(coordinate_bits & 255u, (coordinate_bits >> 8u) & 255u, (coordinate_bits >> 16u) & 255u)\n\
                : uvec3((coordinate_bits >> 24u) & 255u, 165u, 90u);\n\
            out_color = vec4(vec3(probe_bytes) / 255.0, 1.0);\n\
            return;"), 1);
    }
    let coordinate = match probe {
        Some("u-bits") => "sample_uv.x",
        Some("v-bits") => "sample_uv.y",
        // Compare rasterized depth with the Frozen observer independently of
        // atlas UV interpolation. This remains diagnostic color output, not
        // a depth attachment override or a material admitted for normal play.
        Some("depth-bits") => "gl_FragCoord.z",
        _ => return source,
    };
    let anchor = "vec2 sample_uv = v_animation_region.xy + v_uv * v_animation_region.zw;";
    let output = format!("{anchor}\n    // DIAGNOSTIC ONLY: terrain coordinate {coordinate} float bits, not color parity.\n    uint coordinate_bits = floatBitsToUint({coordinate});\n    out_color = vec4(vec3(coordinate_bits & 255u, (coordinate_bits >> 8u) & 255u, (coordinate_bits >> 16u) & 255u) / 255.0, 1.0);\n    return;");
    source.replacen(anchor, &output, 1)
}

pub(crate) fn minimal_deferred_terrain_fragment_source(kind: TerrainMaterialProgramKind) -> String {
    terrain_fragment_source_with_pass_define(MINIMAL_TERRAIN_MATERIAL_FRAGMENT, kind)
}

pub(crate) fn terrain_fragment_source_with_pass_define(
    source: &str,
    kind: TerrainMaterialProgramKind,
) -> String {
    let discard_define = terrain_fragment_discard_define(kind);
    source.replacen(
        "#version 450\n",
        &format!("#version 450\n{discard_define}"),
        1,
    )
}

pub(crate) fn terrain_fragment_discard_define(kind: TerrainMaterialProgramKind) -> &'static str {
    matches!(kind, TerrainMaterialProgramKind::Cutout)
        .then_some("#define VULKANIC_TERRAIN_FRAGMENT_DISCARD 1\n")
        .unwrap_or_default()
}

/// Frozen's active OpenGL Sodium terrain program samples the 16×16 lightmap at each
/// vertex from its packed `a_LightAndData.xy / 256` coordinate, then lets
/// normal varying interpolation carry the lit vertex colour across the
/// triangle. Sodium's CPU encoder writes each packed 0..15 light level as a
/// byte coordinate `level * 16`, and its OpenGL block-layer vertex shader divides that byte by
/// 256 before sampling it with LightTexture's linear sampler. The semantic
/// level/15 representation therefore converts to `level * 15 / 16`, not a
/// texel-centre coordinate.
/// Both direct and deferred builtin terrain use this exact vertex contract.
pub(crate) fn minimal_direct_terrain_vertex_source() -> String {
    terrain_vertex_coordinate_probe(
        MINIMAL_TERRAIN_MATERIAL_VERTEX.to_string(),
        crate::core::environment::var("MATTMC_RUST_TERRAIN_COORDINATE_PROBE")
            .ok()
            .as_deref(),
    )
}

pub(crate) fn compact_direct_terrain_vertex_source() -> String {
    // This source is deliberately separate from MINIMAL_TERRAIN_MATERIAL_VERTEX.
    // DirectTerrain32 is admitted only for copied, atlas-backed terrain
    // (opaque, cutout, or translucent) with translation-only instances. Keeping that contract explicit
    // removes entity/model lighting branches and unused rich varyings without
    // changing the rich ABI used by source-derived Iris/DH programs.
    let source = r#"#version 450
layout(set = 1, binding = 0) uniform texture2D LightmapTexture;
layout(set = 1, binding = 1) uniform sampler LightmapSampler;
struct MeshVertex {
    vec3 position;
    uint material;
    vec2 atlas_uv;
    uint color_rgba;
    uint light_block_sky;
};
layout(set = 0, binding = 0, std430) readonly buffer WorldMeshVertices {
    MeshVertex vertices[];
};
struct MeshInstance {
    mat4 model;
    vec4 color;
    vec4 material;
    vec4 animation_region;
    vec4 animation_next_region;
    vec4 overlay_color;
    vec4 texture_transform;
};
layout(set = 0, binding = 1, std430) readonly buffer WorldMeshInstances {
    mat4 view;
    mat4 projection;
    mat4 light_view_projection;
    vec4 shadow_params;
    vec4 fog_color_and_environmental_start;
    vec4 fog_ranges;
    MeshInstance instances[];
};
layout(location = 0) out vec2 v_uv;
layout(location = 1) out vec4 v_color;
layout(location = 2) flat out vec4 v_material;
layout(location = 6) flat out vec4 v_animation_region;
layout(location = 7) flat out vec4 v_animation_next_region;
layout(location = 9) out vec2 v_fog_distances;
layout(location = 10) flat out vec4 v_fog_color_and_environmental_start;
layout(location = 11) flat out vec4 v_fog_ranges;
layout(location = 12) flat out uint v_terrain_material_bits;
void main() {
    MeshVertex vertex = vertices[gl_VertexIndex];
    MeshInstance instance = instances[gl_InstanceIndex];
    uint material_semantics = uint(instance.material.w);
    vec4 world = instance.model * vec4(vertex.position, 1.0);
    vec4 clip = projection * view * world;
#ifdef VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH
    clip.z = clip.z * 0.5 + clip.w * 0.5;
#endif
    gl_Position = clip;
    // DirectTerrain32 owns resolved atlas coordinates and byte-exact light
    // levels. Divide by 240 before applying Sodium's /256-equivalent sample
    // coordinate so fractional levels remain visible to the linear lightmap.
    v_uv = vertex.atlas_uv;
    vec2 light_coordinates = vec2(
        float(vertex.light_block_sky & 0xffu) / 240.0,
        float((vertex.light_block_sky >> 8u) & 0xffu) / 240.0);
    // Preserve the shared ABI's explicit per-instance light override. Normal
    // static terrain leaves this bit clear; retaining the branch keeps custom
    // copied terrain semantics intact without reintroducing entity lighting.
    if ((material_semantics & 1024u) != 0u) {
        uint packed_instance_light = floatBitsToUint(instance.texture_transform.w);
        light_coordinates = vec2(
            float(packed_instance_light & 0xffu) / 240.0,
            float((packed_instance_light >> 16u) & 0xffu) / 240.0);
    }
    vec2 light_uv = clamp(light_coordinates, vec2(0.0), vec2(255.0 / 240.0))
        * (15.0 / 16.0);
    vec4 light_color = texture(sampler2D(LightmapTexture, LightmapSampler), light_uv);
    vec4 terrain_color = unpackUnorm4x8(vertex.color_rgba);
    // The copied Sodium separate-AO layout keeps AO in color alpha for Iris.
    // The direct vanilla path consumes it here, without changing source data.
    if ((vertex.material & 256u) != 0u) {
        terrain_color.rgb *= terrain_color.a;
        terrain_color.a = 1.0;
    }
    v_color = terrain_color * instance.color * light_color;
    v_material = instance.material;
    v_animation_region = instance.animation_region;
    v_animation_next_region = instance.animation_next_region;
    v_terrain_material_bits = vertex.material & 255u;
    vec3 fog_position = world.xyz;
    v_fog_distances = vec2(
        length(fog_position),
        max(length(fog_position.xz), abs(fog_position.y))
    );
    v_fog_color_and_environmental_start = fog_color_and_environmental_start;
    v_fog_ranges = fog_ranges;
}
"#;
    terrain_vertex_coordinate_probe(
        source.to_string(),
        crate::core::environment::var("MATTMC_RUST_TERRAIN_COORDINATE_PROBE")
            .ok()
            .as_deref(),
    )
}

/// Observe the actual vertex expression before backend clip-depth conversion.
/// Flat transport carries the pipeline's explicitly selected vertex unchanged;
/// it does not replace position arithmetic or inspect backend resources.
pub(crate) fn terrain_vertex_coordinate_probe(source: String, probe: Option<&str>) -> String {
    if probe != Some("clip-bits") {
        return source;
    }
    source
        .replacen(
            "#version 450\n",
            "#version 450\nlayout(location = 14) flat out vec4 diagnostic_clip;\n",
            1,
        )
        .replacen(
            "vec4 clip = projection * view * world;",
            "vec4 clip = projection * view * world;\n    diagnostic_clip = clip;",
            1,
        )
}

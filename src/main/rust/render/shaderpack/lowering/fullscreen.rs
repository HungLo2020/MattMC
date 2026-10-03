//! Fullscreen-pass vertex and fragment lowering.

use super::*;

// Frozen's composite transformer uses a unit quad, an identity model-view
// and this projection (including its all-zero Z column). These constants
// belong to source semantics, not the copied world-camera uniform block.
const COMPOSITE_TRANSFORM_PREAMBLE: &str = r#"
const mat4 vulkanic_source_composite_projection = mat4(
    vec4(2.0, 0.0, 0.0, 0.0), vec4(0.0, 2.0, 0.0, 0.0),
    vec4(0.0), vec4(-1.0, -1.0, 0.0, 1.0)
);
const mat4 vulkanic_source_composite_texture_matrix[8] = mat4[8](
    mat4(1.0), mat4(1.0), mat4(1.0), mat4(1.0),
    mat4(1.0), mat4(1.0), mat4(1.0), mat4(1.0)
);
"#;

fn lower_composite_transform(mut source: String) -> GalResult<String> {
    for (legacy, explicit) in [
        ("gl_ModelViewProjectionMatrix", "(vulkanic_source_composite_projection * mat4(1.0))"),
        ("gl_ProjectionMatrix", "vulkanic_source_composite_projection"),
        ("gl_ModelViewMatrix", "mat4(1.0)"),
        ("gl_NormalMatrix", "mat3(1.0)"),
        ("gl_TextureMatrix", "vulkanic_source_composite_texture_matrix"),
        ("gl_Color", "vec4(1.0)"),
    ] {
        source = replace_identifier(&source, legacy, explicit);
    }
    insert_after_version(&source, COMPOSITE_TRANSFORM_PREAMBLE)
}

fn uses_composite_transform(entry_path: &str, raster_primitive: FullscreenSourceRasterPrimitive) -> bool {
    raster_primitive == FullscreenSourceRasterPrimitive::FullscreenTriangle
        // World source transforms must never acquire composite defaults.
        && !entry_path.rsplit('/').next().is_some_and(|name| name.starts_with("gbuffers_"))
}

fn lower_owned_sky_transform(
    mut source: String,
    raster_primitive: FullscreenSourceRasterPrimitive,
) -> GalResult<String> {
    let model_view = match raster_primitive {
        FullscreenSourceRasterPrimitive::VanillaSkyDisc
        | FullscreenSourceRasterPrimitive::ShaderPackHorizon => "gbufferModelView",
        FullscreenSourceRasterPrimitive::VanillaCelestialQuad => "vulkanic_source_celestial_model_view()",
        FullscreenSourceRasterPrimitive::FullscreenTriangle => return Ok(source),
    };
    for (legacy, explicit) in [
        ("gl_ModelViewProjectionMatrix", format!("(gbufferProjection * {model_view})")),
        ("gl_ModelViewMatrix", model_view.to_string()),
        ("gl_ProjectionMatrix", "gbufferProjection".to_string()),
        ("gl_NormalMatrix", format!("transpose(inverse(mat3({model_view})))")),
    ] {
        source = replace_identifier(&source, legacy, &explicit);
    }
    Ok(source)
}

pub(super) fn lower_fullscreen_source_vertex_with_contracts(
    source: &PreprocessedShaderSource,
    uniform_contract: &TerrainSourceUniformContract,
    varying_contract: &TerrainSourceVaryingContract,
    opaque_resource_contract: &TerrainSourceOpaqueResourceContract,
    raster_primitive: FullscreenSourceRasterPrimitive,
) -> GalResult<LoweredFullscreenSourceVertex> {
    let mut lowered = upgrade_version(source.expanded_source())?;
    lowered = strip_nonopaque_uniforms(&lowered)?;
    let uses_legacy_fog = lower_legacy_fog(&mut lowered);
    lowered = replace_identifier(&lowered, "varying", "out");
    if uses_composite_transform(source.entry_path(), raster_primitive) {
        lowered = lower_composite_transform(lowered)?;
        lowered = replace_identifier(&lowered, "gl_Vertex", "vulkanic_source_fullscreen_vertex()");
        lowered = replace_identifier(&lowered, "gl_Normal", "vec3(0.0, 0.0, 1.0)");
        // Frozen's quad has only primary UVs. Unused compatibility texture
        // coordinate sets retain the generic (0,0,0,1), not the primary UV.
        for index in 1..=7 {
            lowered = replace_identifier(&lowered, &format!("gl_MultiTexCoord{index}"), "vec4(0.0, 0.0, 0.0, 1.0)");
        }
    } else {
        lowered = lower_owned_sky_transform(lowered, raster_primitive)?;
        let vertex = match raster_primitive {
            FullscreenSourceRasterPrimitive::VanillaSkyDisc => Some("vulkanic_source_fullscreen_sky_position()"),
            FullscreenSourceRasterPrimitive::ShaderPackHorizon => Some("vulkanic_source_fullscreen_horizon_position()"),
            FullscreenSourceRasterPrimitive::VanillaCelestialQuad => Some("vulkanic_source_fullscreen_celestial_position()"),
            FullscreenSourceRasterPrimitive::FullscreenTriangle => None,
        };
        if let Some(vertex) = vertex {
            lowered = replace_identifier(&lowered, "gl_Vertex", vertex);
            // Frozen's sky/celestial formats have no light attribute. Iris
            // supplies full-bright coordinates for both compatibility sets,
            // and aliases texture-matrix slots 1 and 2 to the lightmap matrix.
            // Keep the owned two-matrix uniform block and its ABI unchanged.
            for coordinate in ["gl_MultiTexCoord1", "gl_MultiTexCoord2"] {
                lowered = replace_identifier(&lowered, coordinate, "vec4(240.0, 240.0, 0.0, 1.0)");
            }
            lowered = replace_identifier(
                &lowered,
                "gl_TextureMatrix",
                "(mat4[3](vulkanic_source_fullscreen_texture_matrix[0], vulkanic_source_fullscreen_texture_matrix[1], vulkanic_source_fullscreen_texture_matrix[1]))",
            );
        }
    }
    for (legacy, explicit) in [
        ("texture2DLod", "textureLod"),
        ("texture3DLod", "textureLod"),
        ("textureCubeLod", "textureLod"),
        ("texture2D", "texture"),
        ("texture3D", "texture"),
        ("textureCube", "texture"),
        (
            "gl_TextureMatrix",
            "vulkanic_source_fullscreen_texture_matrix",
        ),
        ("gl_MultiTexCoord0", "vulkanic_source_fullscreen_uv"),
        (
            "gl_MultiTexCoord1",
            "vulkanic_source_fullscreen_secondary_uv",
        ),
        ("gl_Color", "vulkanic_source_fullscreen_vertex_color"),
        ("ftransform", "vulkanic_source_fullscreen_transform"),
    ] {
        lowered = replace_identifier(&lowered, legacy, explicit);
    }
    lowered = apply_varying_locations(&lowered, VaryingStorage::Out, varying_contract)?;
    lowered = apply_opaque_resource_bindings(&lowered, opaque_resource_contract)?;
    lowered = insert_after_version(
        &lowered,
        &fullscreen_vertex_semantic_preamble(raster_primitive),
    )?;
    if raster_primitive == FullscreenSourceRasterPrimitive::VanillaCelestialQuad {
        lowered = insert_after_version(&lowered, CELESTIAL_MODEL_TRANSFORM)?;
    }
    if uses_legacy_fog {
        lowered = insert_after_version(&lowered, LEGACY_FOG_SEMANTIC_PREAMBLE)?;
    }
    // GLSL declarations must precede the procedural helper functions that
    // consume them. `insert_after_version` prepends each insertion, so stage
    // helpers are inserted first and the semantic scalar block last.
    lowered = lower_fullscreen_source_history_corner(lowered, uniform_contract)?;
    lowered = insert_after_version(&lowered, &uniform_block(uniform_contract))?;
    // Fullscreen source stages are authored with the same OpenGL clip-depth
    // convention as terrain sources. Their procedural coverage must receive
    // the identical backend-selected finalization, otherwise a Vulkan stage
    // that reconstructs from gl_FragCoord sees a different depth convention.
    lowered = append_clip_depth_convention_finalizer(&lowered)?;
    let remaining_dialect = analyze_glsl_text(source.entry_path(), &lowered);
    Ok(LoweredFullscreenSourceVertex {
        entry_path: source.entry_path().to_string(),
        source: lowered,
        remaining_dialect,
    })
}

pub(super) fn lower_fullscreen_source_fragment_with_contracts(
    source: &PreprocessedShaderSource,
    uniform_contract: &TerrainSourceUniformContract,
    varying_contract: &TerrainSourceVaryingContract,
    opaque_resource_contract: &TerrainSourceOpaqueResourceContract,
    bindings: &TerrainSourceResourceBindings,
    raster_primitive: FullscreenSourceRasterPrimitive,
) -> GalResult<LoweredFullscreenSourceFragment> {
    let mut lowered = upgrade_version(source.expanded_source())?;
    lowered = strip_nonopaque_uniforms(&lowered)?;
    if uses_composite_transform(source.entry_path(), raster_primitive) {
        lowered = lower_composite_transform(lowered)?;
    } else {
        lowered = lower_owned_sky_transform(lowered, raster_primitive)?;
    }
    let uses_legacy_fog = lower_legacy_fog(&mut lowered);
    for (legacy, explicit) in [
        ("texture2DLod", "textureLod"),
        ("texture3DLod", "textureLod"),
        ("textureCubeLod", "textureLod"),
        ("texture2D", "texture"),
        ("texture3D", "texture"),
        ("textureCube", "texture"),
        ("shadow2D", "vulkanic_source_shadow2D"),
        ("shadow2DLod", "vulkanic_source_shadow2DLod"),
    ] {
        lowered = replace_identifier(&lowered, legacy, explicit);
    }
    lowered = replace_identifier(&lowered, "varying", "in");
    lowered = apply_varying_locations(&lowered, VaryingStorage::In, varying_contract)?;
    lowered = apply_opaque_resource_bindings(&lowered, opaque_resource_contract)?;
    let draw_buffer_slots = if source.entry_path().rsplit('/').next() == Some("final.fsh")
        && !source.expanded_source().contains("DRAWBUFFERS:") {
        // The final stage writes the sole displayed color rather than an
        // indexed scene MRT. Keep it in the Rust-owned primary target for
        // the normal final-copy path. Other stages still require directives,
        // and malformed explicit final directives still fail parsing below.
        vec![0]
    } else {
        parse_draw_buffers_slots(source.expanded_source())?
    };
    let mut outputs = Vec::new();
    for location in 0..8 {
        let provisional_name = format!("out_vulkanic_source_color_{location}");
        let (rewritten, occurrences) =
            replace_fragment_output(&lowered, location, &provisional_name)?;
        if occurrences == 0 {
            continue;
        }
        let source_slot = *draw_buffer_slots.get(location as usize).ok_or_else(|| {
            GalError::unsupported_feature(format!(
                "fullscreen source fragment '{}' writes gl_FragData[{location}] but DRAWBUFFERS declares only {} outputs",
                source.entry_path(),
                draw_buffer_slots.len()
            ))
        })?;
        let role = bindings.shader_pack_color_output_for_slot(source_slot)?;
        let Some(name) = role.shader_pack_color_name() else {
            unreachable!("shader_pack_color_output_for_slot returns a color role");
        };
        let semantic_name = format!("out_vulkanic_source_color_{name}");
        lowered = replace_identifier(&rewritten, &provisional_name, &semantic_name);
        outputs.push(FullscreenSourceFragmentOutput {
            source_location: location,
            source_slot,
            role,
            semantic_name,
        });
    }
    if contains_fragment_output(&lowered)? {
        return Err(GalError::unsupported_feature(format!(
            "fullscreen source fragment '{}' writes an output without a declared semantic color role",
            source.entry_path()
        )));
    }
    if outputs.is_empty() {
        return Err(GalError::invalid_argument(format!(
            "fullscreen source fragment '{}' has no semantic color output to lower",
            source.entry_path()
        )));
    }
    if outputs
        .iter()
        .enumerate()
        .any(|(expected, output)| output.source_location != expected as u32)
    {
        return Err(GalError::unsupported_feature(format!(
            "fullscreen source fragment '{}' uses sparse gl_FragData locations; compact source outputs are required",
            source.entry_path()
        )));
    }
    apply_selected_source_sky_fragment_probe(&mut lowered, source.entry_path())?;
    apply_selected_source_fullscreen_probe(&mut lowered, &outputs, source.entry_path())?;
    let declarations = outputs
        .iter()
        .map(|output| {
            format!(
                "layout(location = {}) out vec4 {};\n",
                output.source_location, output.semantic_name
            )
        })
        .collect::<String>();
    if uses_legacy_fog {
        lowered = insert_after_version(&lowered, LEGACY_FOG_SEMANTIC_PREAMBLE)?;
    }
    // Fullscreen source stages need two explicit coordinate domains. Source
    // math (fog, reconstruction, dithering) is authored around OpenGL's
    // lower-left gl_FragCoord, while source target color images retain their
    // native presentation row order. Depth attachments use that same target
    // coordinate here so color and depth remain aligned during composites.
    lowered = match raster_primitive {
        FullscreenSourceRasterPrimitive::FullscreenTriangle =>
            lower_fullscreen_fragment_coordinates(lowered, uniform_contract, varying_contract)?,
        // Sky/celestial fragments derive screen UVs from source gl_FragCoord.
        // Their target samplers therefore use the ordinary world conversion;
        // they do not inherit the composite vertex's image-domain UV stream.
        FullscreenSourceRasterPrimitive::VanillaSkyDisc
        | FullscreenSourceRasterPrimitive::ShaderPackHorizon
        | FullscreenSourceRasterPrimitive::VanillaCelestialQuad =>
            lower_world_material_fragment_coordinates(lowered, uniform_contract, source.world_custom_samplers())?,
    };
    lowered = lower_fullscreen_source_history_corner(lowered, uniform_contract)?;
    // Fullscreen shader-pack stages commonly reconstruct view space from a
    // sampled depth value using the legacy OpenGL clip-depth mapping.  The
    // Vulkan depth attachment is zero-to-one, so normalize these source
    // reconstruction forms at the semantic lowering boundary while keeping
    // the OpenGL source expression intact behind the backend define.
    lowered = insert_after_version(&lowered, FRAGMENT_SEMANTIC_PREAMBLE)?;
    if raster_primitive == FullscreenSourceRasterPrimitive::VanillaCelestialQuad {
        lowered = insert_after_version(&lowered, CELESTIAL_MODEL_TRANSFORM)?;
    }
    lowered = insert_after_version(&lowered, &uniform_block(uniform_contract))?;
    lowered = insert_after_version(&lowered, &declarations)?;
    let remaining_dialect = analyze_glsl_text(source.entry_path(), &lowered);
    Ok(LoweredFullscreenSourceFragment {
        entry_path: source.entry_path().to_string(),
        source: lowered,
        outputs,
        remaining_dialect,
    })
}

/// Converts an explicit source-color history texel from OpenGL's lower-left
/// address space to the Rust target's Vulkan row order. The top-right corner
/// carries Complementary's persistent lightshaft factor. Fragment-coordinate
/// writes already use the converted source coordinate, but a vertex-stage
/// texelFetch does not pass through fragment-coordinate lowering.
pub(super) fn lower_fullscreen_source_history_corner(
    mut source: String,
    uniform_contract: &TerrainSourceUniformContract,
) -> GalResult<String> {
    const SOURCE_FETCH: &str =
        "texelFetch(colortex4, ivec2(viewWidth-1, viewHeight-1), 0)";
    if !source.contains(SOURCE_FETCH) {
        return Ok(source);
    }
    if !uniform_contract.fields().iter().any(|field| field.name() == "viewHeight") {
        return Err(GalError::unsupported_feature(
            "fullscreen source history-corner fetch requires copied viewHeight",
        ));
    }
    source = source.replace(
        SOURCE_FETCH,
        "texelFetch(colortex4, vulkanic_source_fullscreen_history_corner(viewWidth, viewHeight), 0)",
    );
    insert_after_version(
        &source,
        r#"ivec2 vulkanic_source_fullscreen_history_corner(float source_width, float source_height) {
    ivec2 source_texel = ivec2(source_width - 1.0, source_height - 1.0);
#ifdef VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH
    return ivec2(source_texel.x, int(source_height) - 1 - source_texel.y);
#else
    return source_texel;
#endif
}
"#,
    )
}

/// Inserts an image-to-source UV conversion at the top of every
/// `...Reprojection(vec3 pos...)` helper whose body starts by expanding `pos`
/// from screen space (`pos = pos * 2.0 - 1.0;`).
pub(super) fn convert_reprojection_screen_inputs(source: &str) -> String {
    const EXPANSION: &str = "pos = pos * 2.0 - 1.0;";
    let mut out = String::with_capacity(source.len() + 256);
    let mut rest = source;
    while let Some(found) = rest.find("Reprojection(vec3 pos") {
        let Some(open) = rest[found..].find('{').map(|offset| found + offset + 1) else {
            break;
        };
        out.push_str(&rest[..open]);
        let body = &rest[open..];
        if body.trim_start().starts_with(EXPANSION) {
            out.push_str("\n        pos.xy = vulkanic_source_fullscreen_screen_uv(pos.xy);");
        }
        rest = body;
    }
    out.push_str(rest);
    out
}

/// Preserves the source pack's lower-left fragment-space convention in
/// fullscreen stages without reinterpreting source-target image addresses.
///
/// Complementary's shared `common.glsl` derives `texelCoord` directly from
/// `gl_FragCoord` and uses it for `texelFetch`. Its fullscreen varying
/// `texCoord` is also dual-purpose: it samples Rust-owned target storage and
/// forms source-space view/reprojection coordinates. On Vulkan, the sampler
/// coordinate must remain vertically flipped while the reconstruction
/// coordinate must retain the source pack's lower-left convention. Rewrite
/// those distinct source forms explicitly instead of making either backend
/// interpretation leak into the pass graph.
pub(super) fn lower_fullscreen_fragment_coordinates(
    mut source: String,
    uniform_contract: &TerrainSourceUniformContract,
    varying_contract: &TerrainSourceVaryingContract,
) -> GalResult<String> {
    let reads_fragment_coordinate = glsl_identifiers(&source).contains("gl_FragCoord");
    let (rewritten, converts_projection) = lower_inline_projection_uvs(&source, varying_contract);
    source = rewritten;
    if !reads_fragment_coordinate && !converts_projection {
        return Ok(source);
    }
    if reads_fragment_coordinate && !uniform_contract
        .fields()
        .iter()
        .any(|field| field.name() == "viewHeight")
    {
        return Err(GalError::unsupported_feature(
            "fullscreen source reads gl_FragCoord but does not declare viewHeight for explicit coordinate conversion",
        ));
    }
    if reads_fragment_coordinate {
        source = replace_identifier(
            &source, "gl_FragCoord", "vulkanic_source_fullscreen_fragment_coord()",
        );
    }
    source = source.replace(
        "ivec2 texelCoord = ivec2(vulkanic_source_fullscreen_fragment_coord().xy);",
        "// Source texelCoord addresses the Rust-owned target color storage.\n        ivec2 texelCoord = ivec2(gl_FragCoord.xy);",
    );
    // `texCoord` is the target-sampler domain established by the owned
    // fullscreen vertex stream. Source reconstruction still uses the legacy
    // lower-left screen domain, so convert only position vectors that feed
    // projection inversion. This keeps texture sampling and source math from
    // silently sharing a backend row convention.
    for anchor in [
        "vec4 screenPos = vec4(texCoord,",
        "vec4 screenPosDH = vec4(texCoord,",
        "vec4 screenPos1 = vec4(texCoord,",
        "vec4 screenPos1DH = vec4(texCoord,",
    ] {
        source = source.replace(
            anchor,
            &anchor.replace(
                "vec4(texCoord,",
                "vec4(vulkanic_source_fullscreen_screen_uv(texCoord),",
            ),
        );
    }
    // Included source helpers can invert the projection inline rather than
    // naming a screenPos vector. The same source-screen UV rule applies.
    source = source.replace(
        "gbufferProjectionInverse * (vec4(texCoord,",
        "gbufferProjectionInverse * (vec4(vulkanic_source_fullscreen_screen_uv(texCoord),",
    );
    // Reprojection helpers (TAA, temporal reflection filters) return an
    // OpenGL screen UV that callers use to sample history images and compare
    // with the image-space `texCoord`. Convert that result once, at the
    // helper, so every call site shares the image domain. A static camera
    // hides a missing conversion (the mirror cancels); motion does not.
    const REPROJECTION_RETURN: &str =
        "return previousPosition.xy / previousPosition.w * 0.5 + 0.5;";
    if source.contains(REPROJECTION_RETURN) {
        source = source.replace(
            REPROJECTION_RETURN,
            "return vulkanic_source_fullscreen_screen_uv(previousPosition.xy / previousPosition.w * 0.5 + 0.5);",
        );
    } else {
        // A helper without the canonical return still gets its TAA result
        // converted at the call site.
        source = source.replace(
            "prvCoord = Reprojection(viewPos1);",
            "prvCoord = vulkanic_source_fullscreen_screen_uv(Reprojection(viewPos1));",
        );
    }
    // Helpers that take an image-space `vec3(texCoord, depth)` and expand it
    // as an OpenGL screen position convert the incoming UV first.
    source = convert_reprojection_screen_inputs(&source);
    // Copied pack noise images retain their decoded texels. Fullscreen source
    // noise lookups authored from texCoord still require OpenGL's lower-left
    // screen UV; color/depth target lookups keep the native image UV.
    for sampler_call in ["texture2D", "texture"] {
        for suffix in [" *", ")"] {
            let source_call = format!("{sampler_call}(noisetex, texCoord{suffix}");
            let converted_call = format!(
                "{sampler_call}(noisetex, vulkanic_source_fullscreen_screen_uv(texCoord){suffix}"
            );
            source = source.replace(&source_call, &converted_call);
        }
    }
    let fragment_coordinate = if reads_fragment_coordinate {
        r#"vec4 vulkanic_source_fullscreen_fragment_coord() {
    vec4 coordinate = gl_FragCoord;
#ifdef VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH
    coordinate.y = viewHeight - coordinate.y;
#endif
    return coordinate;
}
"#
    } else { "" };
    insert_after_version(
        &source,
        &format!("{fragment_coordinate}{}", r#"vec2 vulkanic_source_fullscreen_screen_uv(vec2 image_uv) {
#ifdef VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH
    return vec2(image_uv.x, 1.0 - image_uv.y);
#else
    return image_uv;
#endif
}
"#),
    )
}

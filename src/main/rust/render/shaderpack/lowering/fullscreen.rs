//! Fullscreen-pass vertex and fragment lowering.

use super::*;

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
    for (legacy, explicit) in [
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
) -> GalResult<LoweredFullscreenSourceFragment> {
    let mut lowered = upgrade_version(source.expanded_source())?;
    lowered = strip_nonopaque_uniforms(&lowered)?;
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
    let draw_buffer_slots = parse_draw_buffers_slots(source.expanded_source())?;
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
    lowered = lower_fullscreen_fragment_coordinates(lowered, uniform_contract)?;
    lowered = lower_fullscreen_source_history_corner(lowered, uniform_contract)?;
    // Fullscreen shader-pack stages commonly reconstruct view space from a
    // sampled depth value using the legacy OpenGL clip-depth mapping.  The
    // Vulkan depth attachment is zero-to-one, so normalize these source
    // reconstruction forms at the semantic lowering boundary while keeping
    // the OpenGL source expression intact behind the backend define.
    lowered = insert_after_version(&lowered, FRAGMENT_SEMANTIC_PREAMBLE)?;
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
) -> GalResult<String> {
    if !glsl_identifiers(&source).contains("gl_FragCoord") {
        return Ok(source);
    }
    if !uniform_contract
        .fields()
        .iter()
        .any(|field| field.name() == "viewHeight")
    {
        return Err(GalError::unsupported_feature(
            "fullscreen source reads gl_FragCoord but does not declare viewHeight for explicit coordinate conversion",
        ));
    }
    source = replace_identifier(
        &source,
        "gl_FragCoord",
        "vulkanic_source_fullscreen_fragment_coord()",
    );
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
    insert_after_version(
        &source,
        r#"vec4 vulkanic_source_fullscreen_fragment_coord() {
    vec4 coordinate = gl_FragCoord;
#ifdef VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH
    coordinate.y = viewHeight - coordinate.y;
#endif
    return coordinate;
}
vec2 vulkanic_source_fullscreen_screen_uv(vec2 image_uv) {
#ifdef VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH
    return vec2(image_uv.x, 1.0 - image_uv.y);
#else
    return image_uv;
#endif
}
"#,
    )
}

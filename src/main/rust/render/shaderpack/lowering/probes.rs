//! Diagnostic probes injected into selected-source programs when requested by environment variables.

use super::*;

/// Capture-only probe for the first-person hand vertex boundary. Hand meshes
/// use a distinct projection/model-view transport, so the ordinary terrain
/// probe must not silently alter them during diagnostics.
pub(super) fn apply_selected_source_hand_vertex_probe(
    source: &mut LoweredTerrainVertexSource,
) -> GalResult<()> {
    let mode = crate::core::environment::var("MATTMC_RUST_SELECTED_SOURCE_HAND_VERTEX_PROBE").ok();
    apply_selected_source_vertex_position_probe_mode(
        &mut source.source,
        mode.as_deref().map(str::trim),
    )
}

/// Capture-only probe for the distinct local-texture entity source contract.
/// It is intentionally separate from terrain's atlas probe: entity UVs are
/// local to a Rust-owned material texture and must not inherit terrain-atlas
/// assumptions while we diagnose selected-source material coverage.
pub(super) fn apply_selected_source_entity_fragment_probe(
    fragment: &mut LoweredTerrainFragmentSource,
) -> GalResult<()> {
    let mode = crate::core::environment::var("MATTMC_RUST_SELECTED_SOURCE_ENTITY_FRAGMENT_PROBE").ok();
    apply_selected_source_entity_fragment_probe_mode(fragment, mode.as_deref())
}

pub(super) fn apply_selected_source_entity_fragment_probe_mode(
    fragment: &mut LoweredTerrainFragmentSource,
    mode: Option<&str>,
) -> GalResult<()> {
    let Some(mode) = mode.map(str::trim).filter(|value| !value.is_empty()) else {
        return Ok(());
    };
    let (label, replacement) = match mode {
        "texture" => (
            "texture",
            "out_terrain_lit_color = color;\n    out_terrain_material_auxiliary = vec4(0.0);\n    return;",
        ),
        "texture-center" => (
            "texture-center",
            "out_terrain_lit_color = texture(tex, vec2(0.5, 0.5));\n    out_terrain_material_auxiliary = vec4(0.0);\n    return;",
        ),
        "texture-arm" => (
            "texture-arm",
            "out_terrain_lit_color = texture(tex, vec2(0.6875, 0.375));\n    out_terrain_material_auxiliary = vec4(0.0);\n    return;",
        ),
        "texture-arm-opaque" => (
            "texture-arm-opaque",
            "out_terrain_lit_color = vec4(texture(tex, vec2(0.6875, 0.375)).rgb, 1.0);\n    out_terrain_material_auxiliary = vec4(0.0);\n    return;",
        ),
        "uv" => (
            "uv",
            "out_terrain_lit_color = vec4(texCoord, 0.0, 1.0);\n    out_terrain_material_auxiliary = vec4(0.0);\n    return;",
        ),
        "constant-red" => (
            "constant-red",
            "out_terrain_lit_color = vec4(1.0, 0.0, 0.0, 1.0);\n    out_terrain_material_auxiliary = vec4(0.0);\n    return;",
        ),
        "lit" => return Ok(()),
        other => {
            return Err(GalError::invalid_argument(format!(
                "unknown selected-source entity fragment probe '{other}'; expected texture, texture-center, texture-arm, texture-arm-opaque, uv, constant-red, or lit"
            )));
        }
    };
    const ANCHOR: &str = "vec4 color = texture(tex, texCoord);";
    if !fragment.source.contains(ANCHOR) {
        return Err(GalError::invalid_argument(
            "selected-source entity fragment probe could not locate the local material sample",
        ));
    }
    fragment.source = fragment.source.replacen(
        ANCHOR,
        &format!("{ANCHOR}\n    {replacement} // selected-source entity diagnostic probe: {label}"),
        1,
    );
    Ok(())
}

pub(super) trait SelectedSourceFragmentTarget {
    fn entry_path(&self) -> &str;
    fn source_text(&self) -> &str;
    fn source_text_mut(&mut self) -> &mut String;
    fn diagnostic_auxiliary_output(&self) -> &'static str;
}

impl SelectedSourceFragmentTarget for LoweredTerrainFragmentSource {
    fn entry_path(&self) -> &str {
        &self.entry_path
    }

    fn source_text(&self) -> &str {
        &self.source
    }

    fn source_text_mut(&mut self) -> &mut String {
        &mut self.source
    }

    fn diagnostic_auxiliary_output(&self) -> &'static str {
        "out_terrain_material_auxiliary"
    }
}

impl SelectedSourceFragmentTarget for LoweredTranslucentTerrainFragmentSource {
    fn entry_path(&self) -> &str {
        &self.entry_path
    }

    fn source_text(&self) -> &str {
        &self.source
    }

    fn source_text_mut(&mut self) -> &mut String {
        &mut self.source
    }

    fn diagnostic_auxiliary_output(&self) -> &'static str {
        "out_terrain_translucency_auxiliary"
    }
}

/// Replaces the selected-source terrain color only for an explicitly opted-in
/// diagnostic capture. The probe is deliberately applied after normal source
/// lowering, so it retains the identical semantic mesh, target, resource set,
/// and backend pipeline contract while isolating the first fragment stage that
/// loses visible color. Normal execution never observes this environment key.
pub(super) fn apply_selected_source_fragment_probe<T: SelectedSourceFragmentTarget>(
    fragment: &mut T,
) -> GalResult<()> {
    let mode = crate::core::environment::var("MATTMC_RUST_SELECTED_SOURCE_FRAGMENT_PROBE").ok();
    apply_selected_source_fragment_probe_mode(fragment, mode.as_deref())
}

pub(super) fn apply_selected_source_fragment_probe_mode<T: SelectedSourceFragmentTarget>(
    fragment: &mut T,
    mode: Option<&str>,
) -> GalResult<()> {
    let mode = match mode {
        Some(mode) => mode,
        None => return Ok(()),
    };
    let water_only = mode.trim().ends_with("-water");
    let mode = mode.trim().strip_suffix("-water").unwrap_or(mode.trim());
    if water_only && !fragment.entry_path().to_ascii_lowercase().contains("water") {
        return Ok(());
    }
    if mode == "shadow-primary" {
        const SHADOW_RETURN: &str = "return shadowcol * (1.0 - shadow0) + shadow0;";
        if !fragment.source_text().contains(SHADOW_RETURN) {
            return Err(GalError::invalid_argument(
                "selected-source shadow-primary probe could not locate SampleShadow return",
            ));
        }
        *fragment.source_text_mut() = fragment.source_text().replacen(
            SHADOW_RETURN,
            "return vec3(shadow0); // selected-source diagnostic probe: shadow-primary",
            1,
        );
        return Ok(());
    }
    // The exact-atlas Distant Horizons adapter owns this checkpoint because
    // its source initializes color differently from normal terrain.  Accept
    // it here so a bounded DH diagnostic does not prevent the paired normal
    // terrain program from preparing; the normal fragment remains unchanged.
    if mode == "pre-lighting" {
        return Ok(());
    }
    let (label, anchor, injected) = match mode {
        "force-opaque" => (
            "force-opaque",
            "if (color.a <= 0.00001) discard;",
            "if (false) discard; // selected-source diagnostic probe: force-opaque",
        ),
        "atlas" => (
            "atlas",
            "vec4 color = texture(tex, texCoord);",
            "out_terrain_lit_color = color;\n    out_terrain_material_auxiliary = vec4(0.0);\n    return;",
        ),
        // Keeps the identical selected-source vertex, index, instance, and
        // resource-set path while exposing the interpolated atlas coordinate.
        // It is capture-only evidence for distinguishing a corrupted source
        // vertex stream from a sampler/descriptor mismatch; it never changes
        // the normal selected-source fragment path.
        "atlas-uv" => (
            "atlas-uv",
            "vec4 color = texture(tex, texCoord);",
            "out_terrain_lit_color = vec4(texCoord, 0.0, 1.0);\n    out_terrain_material_auxiliary = vec4(0.0);\n    return;",
        ),
        "atlas-alpha" => (
            "atlas-alpha",
            "vec4 color = texture(tex, texCoord);",
            "out_terrain_lit_color = vec4(vec3(color.a), 1.0);\n    out_terrain_material_auxiliary = vec4(0.0);\n    return;",
        ),
        // Diagnostic-only row-origin check. The selected source normally owns
        // its UV convention; this does not change it or make flipped sampling
        // available to production execution.
        "atlas-alpha-flipped-v" => (
            "atlas-alpha-flipped-v",
            "vec4 color = texture(tex, texCoord);",
            "vec4 vulkanic_flipped_v_color = texture(tex, vec2(texCoord.x, 1.0 - texCoord.y));\n    out_terrain_lit_color = vec4(vec3(vulkanic_flipped_v_color.a), 1.0);\n    out_terrain_material_auxiliary = vec4(0.0);\n    return;",
        ),
        "tint" => (
            "tint",
            "vec4 color = texture(tex, texCoord);",
            "out_terrain_lit_color = vec4(color.rgb * glColor.rgb, color.a);\n    out_terrain_material_auxiliary = vec4(0.0);\n    return;",
        ),
        // This capture-only probe observes the fragment varying after the
        // source vertex path, before Complementary's lighting terms consume
        // its alpha as vanilla AO. It distinguishes vertex-interface loss
        // from a later lighting calculation without changing production code.
        "vertex-color" => (
            "vertex-color",
            "vec4 color = texture(tex, texCoord);",
            "out_terrain_lit_color = glColor;\n    out_terrain_material_auxiliary = vec4(0.0);\n    return;",
        ),
        // Keeps the actual RGB varying but makes the diagnostic target opaque.
        // A difference from `vertex-color` proves an AO/alpha-lane issue
        // rather than a loss of the whole interpolated color.
        "vertex-color-opaque" => (
            "vertex-color-opaque",
            "vec4 color = texture(tex, texCoord);",
            "out_terrain_lit_color = vec4(glColor.rgb, 1.0);\n    out_terrain_material_auxiliary = vec4(0.0);\n    return;",
        ),
        "vertex-color-raw" => (
            "vertex-color-raw",
            "vec4 color = texture(tex, texCoord);",
            "out_terrain_lit_color = vec4(glColorRaw.rgb, 1.0);\n    out_terrain_material_auxiliary = vec4(0.0);\n    return;",
        ),
        "lightmap" => (
            "lightmap",
            "vec4 color = texture(tex, texCoord);",
            "out_terrain_lit_color = vec4(lmCoord.x, lmCoord.y, glColor.a, 1.0);\n    out_terrain_material_auxiliary = vec4(0.0);\n    return;",
        ),
        "scene-light" => (
            "scene-light",
            "vec3 sceneLighting = lightColorM * shadowMult + ambientColorM * ambientMult;",
            "color = vec4(sceneLighting, 1.0);\n    return;",
        ),
        "light-color" => (
            "light-color",
            "vec3 sceneLighting = lightColorM * shadowMult + ambientColorM * ambientMult;",
            "color = vec4(lightColorM, 1.0);\n    return;",
        ),
        "ambient-color" => (
            "ambient-color",
            "vec3 sceneLighting = lightColorM * shadowMult + ambientColorM * ambientMult;",
            "color = vec4(ambientColorM, 1.0);\n    return;",
        ),
        "shadow-mult" => (
            "shadow-mult",
            "vec3 sceneLighting = lightColorM * shadowMult + ambientColorM * ambientMult;",
            "color = vec4(shadowMult, 1.0);\n    return;",
        ),
        "final-diffuse" => (
            "final-diffuse",
            "color.rgb *= finalDiffuse;",
            "color = vec4(finalDiffuse, 1.0);\n    return;",
        ),
        "lighting-factors" => (
            "lighting-factors",
            "vec3 finalDiffuse = pow2(directionShade * vanillaAO) * (blockLighting + pow2(sceneLighting) + minLighting) + pow2(emission);",
            "color = vec4(\n        clamp(directionShade, 0.0, 1.0),\n        clamp(vanillaAO, 0.0, 1.0),\n        clamp(max(max(sceneLighting.r, sceneLighting.g), sceneLighting.b), 0.0, 1.0),\n        1.0\n    );\n    return;",
        ),
        // The source-derived lighting path has several independently semantic
        // inputs. These two capture-only probes identify the first zero term
        // without changing normal selected-source execution.
        "lighting-components-a" => (
            "lighting-components-a",
            "vec3 finalDiffuse = pow2(directionShade * vanillaAO) * (blockLighting + pow2(sceneLighting) + minLighting) + pow2(emission);",
            "color = vec4(\n        clamp(max(max(lightColorM.r, lightColorM.g), lightColorM.b), 0.0, 1.0),\n        clamp(max(max(ambientColorM.r, ambientColorM.g), ambientColorM.b), 0.0, 1.0),\n        clamp(max(max(shadowMult.r, shadowMult.g), shadowMult.b), 0.0, 1.0),\n        1.0\n    );\n    return;",
        ),
        "lighting-components-b" => (
            "lighting-components-b",
            "vec3 finalDiffuse = pow2(directionShade * vanillaAO) * (blockLighting + pow2(sceneLighting) + minLighting) + pow2(emission);",
            "color = vec4(\n        clamp(ambientMult, 0.0, 1.0),\n        clamp(max(max(blockLighting.r, blockLighting.g), blockLighting.b), 0.0, 1.0),\n        clamp(max(max(minLighting.r, minLighting.g), minLighting.b), 0.0, 1.0),\n        1.0\n    );\n    return;",
        ),
        "darkness-scale" => (
            "darkness-scale",
            "color.rgb *= pow2(1.0 - darknessLightFactor);",
            "color = vec4(vec3(pow2(1.0 - darknessLightFactor)), 1.0);\n    return;",
        ),
        "shadow-coordinate" => (
            "shadow-coordinate",
            "vec3 shadowPos = GetShadowPos(playerPosM);",
            "color = vec4(shadowPos, 1.0);\n    return;",
        ),
        "shadow-coordinate-centered" => (
            "shadow-coordinate-centered",
            "vec3 shadowPos = GetShadowPos(playerPosM);",
            "color = vec4(clamp(shadowPos * 0.5 + 0.5, 0.0, 1.0), 1.0);\n    return;",
        ),
        // Capture-only reconstruction probe. Complementary derives the
        // player-space position from gl_FragCoord and the semantic view/
        // projection inverses before shadow sampling. Keeping this separate
        // from the shadow-coordinate probe isolates a bad reconstruction from
        // a later shadow matrix or depth-compare failure.
        "player-position" => (
            "player-position",
            "vec3 playerPos = ViewToPlayer(viewPos);",
            "out_terrain_lit_color = vec4(clamp(playerPos / 384.0 + 0.5, 0.0, 1.0), 1.0);\n    out_terrain_material_auxiliary = vec4(0.0);\n    return;",
        ),
        // R is the raster depth received by the source fragment; G is the
        // reconstructed camera-relative height; B is reconstructed view
        // distance. This single bounded diagnostic distinguishes a depth
        // convention fault from a matrix-inverse fault without perturbing the
        // selected source route.
        "reconstruction" => (
            "reconstruction",
            "vec3 playerPos = ViewToPlayer(viewPos);",
            "out_terrain_lit_color = vec4(gl_FragCoord.z, clamp(playerPos.y / 384.0 + 0.5, 0.0, 1.0), clamp(length(viewPos) / 384.0, 0.0, 1.0), 1.0);\n    out_terrain_material_auxiliary = vec4(0.0);\n    return;",
        ),
        // Encodes the reconstructed view vector without clipping large values
        // to a single color. This is capture-only and separates a projection
        // inverse failure from the following ViewToPlayer conversion.
        "view-reconstruction-components" => (
            "view-reconstruction-components",
            "vec3 playerPos = ViewToPlayer(viewPos);",
            "out_terrain_lit_color = vec4(atan(viewPos / 32.0) / 3.14159265 + 0.5, 1.0);\n    out_terrain_material_auxiliary = vec4(0.0);\n    return;",
        ),
        // Captures the exact normalized screen input to ScreenToView. It
        // rules out a bad viewport scalar before diagnosing inverse matrices.
        "screen-reconstruction-input" => (
            "screen-reconstruction-input",
            "vec3 playerPos = ViewToPlayer(viewPos);",
            "out_terrain_lit_color = vec4(gl_FragCoord.xy / vec2(viewWidth, viewHeight), gl_FragCoord.z, 1.0);\n    out_terrain_material_auxiliary = vec4(0.0);\n    return;",
        ),
        // Separates the scalar UBO values from gl_FragCoord itself. The
        // atan encoding preserves useful evidence for zero, expected, and
        // implausibly large viewport values without changing production
        // shader behavior or source-pass semantics.
        "viewport-uniforms" => (
            "viewport-uniforms",
            "vec3 playerPos = ViewToPlayer(viewPos);",
            "out_terrain_lit_color = vec4(atan(viewWidth / 1024.0) * 0.63661977, atan(viewHeight / 1024.0) * 0.63661977, clamp(gl_FragCoord.x / 1280.0, 0.0, 1.0), 1.0);\n    out_terrain_material_auxiliary = vec4(0.0);\n    return;",
        ),
        // Capture-only scalar-block probe. The selected source reconstructs
        // view space from these exact matrices, so exposing a bounded basis
        // sample distinguishes a broken dynamic UBO binding from a later
        // shadow-coordinate or compare-sampler fault.
        "matrix-basis" => (
            "matrix-basis",
            "vec3 playerPos = ViewToPlayer(viewPos);",
            "out_terrain_lit_color = vec4(\n        clamp(abs(gbufferProjection[0][0]) * 0.25, 0.0, 1.0),\n        clamp(abs(gbufferProjection[1][1]) * 0.25, 0.0, 1.0),\n        clamp(abs(gbufferProjectionInverse[3][2]) * 0.001, 0.0, 1.0),\n        1.0\n    );\n    out_terrain_material_auxiliary = vec4(0.0);\n    return;",
        ),
        "constant-red" => (
            "constant-red",
            "vec3 sceneLighting = lightColorM * shadowMult + ambientColorM * ambientMult;",
            "out_terrain_lit_color = vec4(1.0, 0.0, 0.0, 1.0);\n    out_terrain_material_auxiliary = vec4(0.0);\n    return;",
        ),
        "shadow-compare" => (
            "shadow-compare",
            "vec3 shadowPos = GetShadowPos(playerPosM);",
            "color = vec4(vec3(vulkanic_source_shadow2D(shadowtex0, vec3(shadowPos.st, shadowPos.z)).x), 1.0);\n    return;",
        ),
        "" | "lit" => return Ok(()),
        other => {
            return Err(GalError::invalid_argument(format!(
                "unknown selected-source fragment probe '{other}'; expected force-opaque, atlas, atlas-uv, atlas-alpha, atlas-alpha-flipped-v, tint, vertex-color, vertex-color-opaque, vertex-color-raw, lightmap, scene-light, light-color, ambient-color, shadow-mult, shadow-primary, pre-lighting, final-diffuse, lighting-factors, lighting-components-a, lighting-components-b, darkness-scale, player-position, reconstruction, view-reconstruction-components, screen-reconstruction-input, viewport-uniforms, matrix-basis, shadow-coordinate, shadow-coordinate-centered, shadow-compare, constant-red, or lit"
            )));
        }
    };
    // Terrain and water source programs use different semantic names for
    // the first atlas sample (`color` versus `colorP`). Keep the probe at
    // the same source boundary for both programs; otherwise an atlas probe
    // silently exercises only opaque terrain and provides no evidence for
    // the translucent writer that owns the failing fixture.
    let (anchor, sample_variable) = if fragment.source_text().contains(anchor) {
        (anchor, "color")
    } else if anchor == "vec4 color = texture(tex, texCoord);"
        && fragment
            .source_text()
            .contains("vec4 colorP = texture(tex, texCoord);")
    {
        ("vec4 colorP = texture(tex, texCoord);", "colorP")
    } else {
        return Err(GalError::invalid_argument(
            "selected-source fragment probe could not locate the lowered atlas sample",
        ));
    };
    let injected = if sample_variable == "colorP" {
        injected.replace("color.", "colorP.")
    } else {
        injected.to_string()
    };
    let injected = injected.replace(
        "out_terrain_material_auxiliary",
        fragment.diagnostic_auxiliary_output(),
    );
    let injected = format!("{anchor}\n    {injected} // selected-source diagnostic probe: {label}");
    *fragment.source_text_mut() = fragment.source_text().replacen(anchor, &injected, 1);
    Ok(())
}

/// Applies a strictly capture-only checkpoint to the reduced Distant Horizons
/// source stream.  DH has a separate fragment interface from near terrain, so
/// sharing the near-terrain probe would either mutate the wrong program or
/// reject a valid source pair before it can be observed.
///
/// The probe is intentionally selected by a distinct environment key.  It
/// never changes the semantic stream, pass target, resources, or route; it
/// only replaces the final color write after the real DH source has run far
/// enough to establish the requested varying.  This distinguishes an invalid
/// projected stream from source lighting/composition failure without a Java
/// fallback or a production rendering workaround.
pub(super) fn apply_selected_source_distant_horizons_fragment_probe(
    fragment: &mut LoweredDistantHorizonsFragmentSource,
) -> GalResult<()> {
    let Some(mode) = crate::core::environment::var("MATTMC_RUST_SELECTED_SOURCE_DH_FRAGMENT_PROBE")
        .ok()
        .map(|value| value.trim().to_owned())
        .filter(|value| !value.is_empty())
    else {
        return Ok(());
    };

    let (label, expression) = match mode.as_str() {
        "constant-red" => ("constant-red", "vec4(1.0, 0.0, 0.0, 1.0)"),
        "vertex-color" => ("vertex-color", "vec4(glColor.rgb, 1.0)"),
        "lightmap" => ("lightmap", "vec4(lmCoord, 0.0, 1.0)"),
        "normal" => ("normal", "vec4(normalize(normal) * 0.5 + 0.5, 1.0)"),
        "player-position" => (
            "player-position",
            "vec4(clamp(playerPos / 512.0 + 0.5, 0.0, 1.0), 1.0)",
        ),
        "lit" => return Ok(()),
        other => {
            return Err(GalError::invalid_argument(format!(
                "unknown Distant Horizons selected-source fragment probe '{other}'; expected constant-red, vertex-color, lightmap, normal, player-position, or lit"
            )));
        }
    };
    const OUTPUT: &str = "out_distant_horizons_lit_color = color;";
    if !fragment.source.contains(OUTPUT) {
        return Err(GalError::invalid_argument(
            "Distant Horizons selected-source fragment probe could not locate the final lit-color write",
        ));
    }
    fragment.source = fragment.source.replacen(
        OUTPUT,
        &format!(
            "out_distant_horizons_lit_color = {expression}; // selected-source DH diagnostic probe: {label}"
        ),
        1,
    );
    Ok(())
}

pub(super) fn dump_selected_source_lowered_shader(entry_path: &str, stage: &str, source: &str) {
    let Ok(dir) = crate::core::environment::var("MATTMC_RUST_SHADER_DUMP_DIR") else {
        return;
    };
    let safe_name = entry_path
        .chars()
        .map(|c| if c.is_ascii_alphanumeric() { c } else { '_' })
        .collect::<String>();
    let path = std::path::Path::new(&dir).join(format!("{stage}_{safe_name}.glsl"));
    if std::fs::create_dir_all(&dir).is_ok() {
        let _ = std::fs::write(path, source);
    }
}

/// Capture-only probe for isolating invalid TAA frame-modulo semantics.  The
/// normal source route retains the pack's jitter; this replacement is never
/// admitted by route selection.
pub(super) fn apply_selected_source_taa_probe(source: &mut String) -> GalResult<()> {
    let Some(mode) = crate::core::environment::var("MATTMC_RUST_SELECTED_SOURCE_TAA_PROBE")
        .ok()
        .map(|value| value.trim().to_owned())
        .filter(|value| !value.is_empty())
    else {
        return Ok(());
    };
    if mode != "disable" {
        return Err(GalError::invalid_argument(format!(
            "unknown selected-source TAA probe '{mode}'; expected disable"
        )));
    }
    const ASSIGNMENT: &str = "gl_Position.xy = TAAJitter(gl_Position.xy, gl_Position.w);";
    if source.contains("selected-source diagnostic probe: TAA disabled") {
        return Ok(());
    }
    if source.contains(ASSIGNMENT) {
        *source = source.replacen(
            ASSIGNMENT,
            "// selected-source diagnostic probe: TAA disabled",
            1,
        );
    }
    Ok(())
}

/// Test-only selected-source vertex probe. It modifies one semantic varying
/// assignment before source compilation so the matching fragment probe can
/// distinguish a vertex-buffer field failure from a stage-interface failure.
/// Normal source execution never observes this opt-in environment key.
pub(super) fn apply_selected_source_vertex_probe(source: &mut String) -> GalResult<()> {
    let mode = crate::core::environment::var("MATTMC_RUST_SELECTED_SOURCE_VERTEX_PROBE").ok();
    if matches!(
        mode.as_deref().map(str::trim),
        Some("direct-model-transform-water") | Some("clip-quad-water")
    ) {
        return Ok(());
    }
    apply_selected_source_vertex_position_probe_mode(source, mode.as_deref())
}

pub(super) fn apply_selected_source_vertex_probe_for_entry(
    source: &mut String,
    entry_path: &str,
) -> GalResult<()> {
    let mode = crate::core::environment::var("MATTMC_RUST_SELECTED_SOURCE_VERTEX_PROBE").ok();
    if matches!(
        mode.as_deref().map(str::trim),
        Some("direct-model-transform-water") | Some("clip-quad-water")
    ) && !entry_path.contains("water")
    {
        return Ok(());
    }
    apply_selected_source_vertex_position_probe_mode(source, mode.as_deref())
}

/// Capture-only position isolation. Every copied terrain quad has the stable
/// `[a,b,c,c,d,a]` source index grammar, so replacing the first quad of each
/// mesh asset by a small centered clip-space quad makes the existing indexed
/// draws cover a bounded diagnostic footprint without changing their fragment
/// program, resource sets, or attachments. Keeping both the footprint and
/// primitive count small is important: a quad per terrain primitive can turn
/// the diagnostic into a GPU-bound stress test and hide the result behind
/// presentation timeouts.
/// It distinguishes a transform/clip rejection from a color-output failure;
/// normal source execution never observes this environment key.
pub(super) fn apply_selected_source_vertex_position_probe_mode(
    source: &mut String,
    mode: Option<&str>,
) -> GalResult<()> {
    match mode {
        None | Some("") => Ok(()),
        Some("constant-red") => {
            const ASSIGNMENT: &str = "glColorRaw = vulkanic_source_vertex_color;";
            if !source.contains(ASSIGNMENT) {
                return Err(GalError::invalid_argument(
                    "selected-source vertex probe could not locate glColorRaw assignment",
                ));
            }
            *source = source.replacen(
                ASSIGNMENT,
                "glColorRaw = vec4(1.0, 0.0, 0.0, 1.0); // selected-source vertex diagnostic probe: constant-red",
                1,
            );
            Ok(())
        }
        Some("clip-quad" | "clip-quad-water") => {
            let closing_brace = main_function_closing_brace(source).ok_or_else(|| {
                GalError::invalid_argument(
                    "selected-source vertex position probe requires a brace-balanced void main() body",
                )
            })?;
            let probe = r#"
#ifndef VULKANIC_SOURCE_PROBE_CORNERS_DEFINED
#define VULKANIC_SOURCE_PROBE_CORNERS_DEFINED
    const vec2 vulkanic_source_probe_corners[4] = vec2[4](
        vec2(-0.15, -0.15), vec2(0.15, -0.15), vec2(0.15, 0.15), vec2(-0.15, 0.15)
    );
#endif
    int vulkanic_source_probe_corner = gl_VertexIndex % 4;
    gl_Position = vec4(vulkanic_source_probe_corners[vulkanic_source_probe_corner], 0.0, 1.0);
"#;
            source.insert_str(closing_brace, probe);
            Ok(())
        }
        Some("raw-position") => {
            let closing_brace = main_function_closing_brace(source).ok_or_else(|| {
                GalError::invalid_argument(
                    "selected-source raw-position probe requires a brace-balanced void main() body",
                )
            })?;
            source.insert_str(
                closing_brace,
                "\n    // selected-source diagnostic probe: raw-position\n    gl_Position = vec4(vulkanic_source_position.xyz / 16.0, 1.0);\n",
            );
            Ok(())
        }
        Some("instance-translation") => {
            // Lowering may revisit an already-lowered module while rebuilding
            // the source pipeline cache.  Keep this capture-only probe
            // idempotent so a second pass cannot produce duplicate GLSL
            // declarations and turn a diagnostic run into a client crash.
            if source.contains("selected-source diagnostic probe: instance-translation") {
                return Ok(());
            }
            // Only the terrain/entity source families expose the instance
            // transform semantic.  Other source vertex modules (for example
            // gbuffers_textured) must remain untouched by this diagnostic.
            if !source.contains("vulkanic_source_model_transform") {
                return Ok(());
            }
            let closing_brace = main_function_closing_brace(source).ok_or_else(|| {
                GalError::invalid_argument(
                    "selected-source instance-translation probe requires a brace-balanced void main() body",
                )
            })?;
            let probe = r#"
    // selected-source diagnostic probe: instance-translation
    vec2 vulkanic_instance_probe_origin = vulkanic_source_model_transform[3].xy / 100.0;
    vec2 vulkanic_instance_probe_corner = vec2(
        (gl_VertexIndex & 1) != 0 ? 0.08 : -0.08,
        (gl_VertexIndex & 2) != 0 ? 0.08 : -0.08
    );
    gl_Position = vec4(vulkanic_instance_probe_origin + vulkanic_instance_probe_corner, 0.0, 1.0);
"#;
            source.insert_str(closing_brace, probe);
            Ok(())
        }
        Some("direct-transform") => {
            // This probe is meaningful only for the indexed terrain stream;
            // other source vertex families intentionally do not expose a
            // per-instance model-transform semantic.
            if !source.contains("vulkanic_source_model_transform")
                || !source.contains("gbufferProjection")
                || source.contains("shadowProjection *")
            {
                return Ok(());
            }
            let closing_brace = main_function_closing_brace(source).ok_or_else(|| {
                GalError::invalid_argument(
                    "selected-source direct-transform probe requires a brace-balanced void main() body",
                )
            })?;
            source.insert_str(
                closing_brace,
                "\n    gl_Position = gbufferProjection * vulkanic_source_model_view * vulkanic_source_position; // selected-source diagnostic probe: direct-transform\n",
            );
            Ok(())
        }
        Some("direct-model-transform") => {
            // Bypass the pack's legacy world-position reconstruction entirely
            // while retaining the explicit Rust-owned model/view/projection
            // matrices. This isolates a bad inverse/legacy expression from
            // the semantic instance and vertex streams.
            if !source.contains("vulkanic_source_model_transform")
                || !source.contains("gbufferProjection")
                || source.contains("shadowProjection *")
            {
                return Ok(());
            }
            let closing_brace = main_function_closing_brace(source).ok_or_else(|| {
                GalError::invalid_argument(
                    "selected-source direct-model-transform probe requires a brace-balanced void main() body",
                )
            })?;
            source.insert_str(
                closing_brace,
                "\n    gl_Position = gbufferProjection * gbufferModelView * vulkanic_source_model_transform * vulkanic_source_position; // selected-source diagnostic probe: direct-model-transform\n",
            );
            Ok(())
        }
        Some("direct-model-transform-water") => {
            if !source.contains("vulkanic_source_model_transform")
                || !source.contains("gbufferProjection")
            {
                return Ok(());
            }
            let closing_brace = main_function_closing_brace(source).ok_or_else(|| {
                GalError::invalid_argument(
                    "selected-source water transform probe requires a brace-balanced void main() body",
                )
            })?;
            source.insert_str(
                closing_brace,
                "\n    gl_Position = gbufferProjection * gbufferModelView * vulkanic_source_model_transform * vulkanic_source_position; // selected-source diagnostic probe: direct-model-transform-water\n",
            );
            Ok(())
        }
        Some("direct-model-transform-inline") => {
            // Replace only the source body's final projection assignment.
            // Unlike the ordinary direct-model probe (which appends after
            // the source body), this preserves source-side jitter and any
            // later semantic work while isolating the legacy position chain.
            if !source.contains("vulkanic_source_model_transform")
                || !source.contains("gbufferProjection")
                || source.contains("shadowProjection *")
            {
                return Ok(());
            }
            const LEGACY: &str =
                "gl_Position = gbufferProjection * gbufferModelView * position;";
            if source.contains(LEGACY) {
                *source = source.replacen(
                    LEGACY,
                    "gl_Position = gbufferProjection * gbufferModelView * vulkanic_source_model_transform * vulkanic_source_position; // selected-source diagnostic probe: direct-model-transform-inline",
                    1,
                );
            }
            Ok(())
        }
        Some("force-depth") => {
            let closing_brace = main_function_closing_brace(source).ok_or_else(|| {
                GalError::invalid_argument(
                    "selected-source hand depth probe requires a brace-balanced void main() body",
                )
            })?;
            source.insert_str(
                closing_brace,
                "\n    gl_Position.z = 0.0; // selected-source hand diagnostic probe: force-depth\n",
            );
            Ok(())
        }
        Some("clip-depth") => {
            if !source.contains("glColorRaw") {
                return Ok(());
            }
            let closing_brace = main_function_closing_brace(source).ok_or_else(|| {
                GalError::invalid_argument(
                    "selected-source clip-depth probe requires a brace-balanced void main() body",
                )
            })?;
            source.insert_str(
                closing_brace,
                "\n    // selected-source diagnostic probe: clip-depth\n    glColorRaw = vec4(clamp(gl_Position.z / max(abs(gl_Position.w), 0.0001), -1.0, 1.0) * 0.5 + 0.5, 0.0, 0.0, 1.0);\n",
            );
            Ok(())
        }
        Some("no-instance-transform") => {
            const NEEDLE: &str = "#define vulkanic_source_model_view (gbufferModelView * vulkanic_source_model_transform)";
            if source.contains(NEEDLE) {
                *source = source.replacen(
                    NEEDLE,
                    "#define vulkanic_source_model_view gbufferModelView // selected-source hand diagnostic probe: no-instance-transform",
                    1,
                );
            }
            Ok(())
        }
        Some("reverse-transform-order") => {
            const NEEDLE: &str = "#define vulkanic_source_model_view (gbufferModelView * vulkanic_source_model_transform)";
            if source.contains(NEEDLE) {
                *source = source.replacen(
                    NEEDLE,
                    "#define vulkanic_source_model_view (vulkanic_source_model_transform * gbufferModelView) // selected-source hand diagnostic probe: reverse-transform-order",
                    1,
                );
            }
            Ok(())
        }
        Some(other) => Err(GalError::invalid_argument(format!(
            "unknown selected-source vertex probe '{other}'; expected constant-red, clip-quad, raw-position, instance-translation, direct-transform, direct-model-transform, force-depth, clip-depth, no-instance-transform, or reverse-transform-order"
        ))),
    }
}

/// Capture-only shader-pack wave isolation. The Complementary terrain vertex
/// source mutates the reconstructed position through `DoWave`; replacing that
/// call with a no-op distinguishes pack animation semantics from the explicit
/// transform/attachment path. This is never enabled by normal execution.
pub(super) fn apply_selected_source_wave_probe(source: &mut String) -> GalResult<()> {
    let Some(mode) = crate::core::environment::var("MATTMC_RUST_SELECTED_SOURCE_WAVE_PROBE")
        .ok()
        .map(|value| value.trim().to_owned())
        .filter(|value| !value.is_empty())
    else {
        return Ok(());
    };
    if mode != "disable" {
        return Err(GalError::invalid_argument(format!(
            "unknown selected-source wave probe '{mode}'; expected disable"
        )));
    }
    let assignment = "DoWave(position.xyz, mat);";
    if !source.contains(assignment) {
        // The selected profile may preprocess the waving branch away. In
        // that case there is no mutation to disable and the probe is a
        // semantic no-op; source admission must remain private and coherent.
        return Ok(());
    }
    *source = source.replacen(
        assignment,
        "// selected-source diagnostic probe: DoWave disabled",
        1,
    );
    Ok(())
}

/// Capture-only diagnostic for the source-defined sky initializer. It keeps
/// the selected program, rasterization, and semantic UBO unchanged while
/// exposing the exact directional terms consumed by `GetSky`. This prevents
/// an apparently valid sky source from being mistaken for equivalent camera
/// reconstruction across backend coordinate conventions.
pub(super) fn apply_selected_source_sky_fragment_probe(
    source: &mut String,
    entry_path: &str,
) -> GalResult<()> {
    let Some(mode) = crate::core::environment::var("MATTMC_RUST_SELECTED_SOURCE_SKY_FRAGMENT_PROBE")
        .ok()
        .map(|value| value.trim().to_owned())
        .filter(|value| !value.is_empty())
    else {
        return Ok(());
    };
    if !entry_path
        .replace('\\', "/")
        .ends_with("gbuffers_skybasic.fsh")
    {
        return Ok(());
    }
    let (anchor, replacement) = match mode.as_str() {
        "vectors" => (
            "color.rgb = GetSky(VdotU, VdotS, dither, true, false);",
            "color.rgb = vec3(clamp(VdotU * 0.5 + 0.5, 0.0, 1.0), clamp(VdotS * 0.5 + 0.5, 0.0, 1.0), clamp(dot(sunVec, upVec) * 0.5 + 0.5, 0.0, 1.0)); // selected-source sky diagnostic probe: vectors",
        ),
        "fragment-coordinates" => (
            "color.rgb = GetSky(VdotU, VdotS, dither, true, false);",
            "color.rgb = vec3(gl_FragCoord.xy / vec2(viewWidth, viewHeight), gl_FragCoord.z); // selected-source sky diagnostic probe: fragment-coordinates",
        ),
        other => {
            return Err(GalError::invalid_argument(format!(
                "unknown selected-source sky fragment probe '{other}'; expected vectors or fragment-coordinates"
            )));
        }
    };
    if !source.contains(anchor) {
        return Err(GalError::invalid_argument(
            "selected-source sky fragment probe could not locate the GetSky assignment",
        ));
    }
    *source = source.replacen(anchor, replacement, 1);
    Ok(())
}

/// Replaces the primary output of one lowered fullscreen source stage only
/// for an explicitly requested diagnostic capture. The probe remains at the
/// source-stage boundary: it reads the same named semantic resource and uses
/// the same procedural fullscreen geometry as the normal program.
pub(super) fn apply_selected_source_fullscreen_probe(
    source: &mut String,
    outputs: &[FullscreenSourceFragmentOutput],
    entry_path: &str,
) -> GalResult<()> {
    let Some(mode) = crate::core::environment::var("MATTMC_RUST_SELECTED_SOURCE_FULLSCREEN_PROBE")
        .ok()
        .map(|value| value.trim().to_owned())
        .filter(|value| !value.is_empty())
    else {
        return Ok(());
    };
    if mode != "distant-horizons-depth"
        && mode != "distant-horizons-depth-routing"
        && mode != "distant-horizons-depth-coordinate"
        && mode != "distant-horizons-fog-inputs"
        && mode != "distant-horizons-fog-effect"
        && mode != "gbuffer-inputs"
        && mode != "gbuffer-primary"
        && mode != "depth-input"
        && mode != "depth-input-flipped"
        && mode != "depth-input-amplified"
        && mode != "deferred-fog-inputs"
        && mode != "composite5-fog-inputs"
        && mode != "composite7-without-fxaa"
    {
        return Err(GalError::invalid_argument(format!(
            "unknown selected-source fullscreen probe '{mode}'; expected distant-horizons-depth, distant-horizons-depth-routing, distant-horizons-depth-coordinate, distant-horizons-fog-inputs, distant-horizons-fog-effect, gbuffer-inputs, gbuffer-primary, depth-input, depth-input-flipped, depth-input-amplified, deferred-fog-inputs, composite5-fog-inputs, or composite7-without-fxaa"
        )));
    }
    if mode == "composite5-fog-inputs" {
        if !entry_path
            .replace('\\', "/")
            .ends_with("world0/composite5.fsh")
        {
            return Ok(());
        }
        let output = outputs.first().ok_or_else(|| {
            GalError::invalid_argument("composite5 fog probe requires a color output")
        })?;
        let assignment = format!("{} = vec4(color, 1.0);", output.semantic_name);
        if !source.contains(&assignment)
            || !source.contains("float z0 = texture(depthtex0, texCoord).r;")
        {
            return Err(GalError::invalid_argument(
                "composite5 fog probe could not locate its depth declaration or output",
            ));
        }
        *source = source.replacen(
            &assignment,
            &format!(
                "{} = vec4(vec3(z0, clamp(lViewPos / max(far, 1.0), 0.0, 1.0), texCoord.y), 1.0); // selected-source fullscreen diagnostic probe: composite5-fog-inputs",
                output.semantic_name
            ),
            1,
        );
        return Ok(());
    }
    if mode == "composite7-without-fxaa" {
        if !entry_path
            .replace('\\', "/")
            .ends_with("world0/composite7.fsh")
        {
            return Ok(());
        }
        const FXAA_CALL: &str = "FXAA311(color);";
        if !source.contains(FXAA_CALL) {
            return Err(GalError::invalid_argument(
                "composite7 FXAA diagnostic could not locate the source FXAA call",
            ));
        }
        *source = source.replacen(
            FXAA_CALL,
            "/* selected-source fullscreen diagnostic: FXAA call suppressed */",
            1,
        );
        return Ok(());
    }
    // A selected-source pack has many fullscreen stages, including others
    // that incidentally declare these names. This probe is only meaningful
    // for Complementary's DH-aware deferred1 fog stage; all other programs
    // must lower unchanged so diagnostic capture cannot affect admission.
    if !entry_path
        .replace('\\', "/")
        .ends_with("world0/deferred1.fsh")
    {
        return Ok(());
    }
    if mode == "gbuffer-inputs" || mode == "gbuffer-primary" {
        let Some(primary) = outputs
            .iter()
            .find(|output| output.role.shader_pack_color_name() == Some("primary"))
        else {
            return Err(GalError::invalid_argument(
                "gbuffer input probe requires a primary color output",
            ));
        };
        const ASSIGNMENT: &str = "{} = vec4(color, 1.0);";
        let assignment = ASSIGNMENT.replacen("{}", &primary.semantic_name, 1);
        if !source.contains(&assignment) {
            return Err(GalError::invalid_argument(
                "gbuffer input probe could not locate the primary color assignment",
            ));
        }
        let replacement = if mode == "gbuffer-inputs" {
            format!("{} = vec4(texelFetch(colortex5, texelCoord, 0).rgb, texelFetch(colortex6, texelCoord, 0).r); // selected-source fullscreen diagnostic probe: gbuffer-inputs", primary.semantic_name)
        } else {
            format!("{} = vec4(texelFetch(colortex0, texelCoord, 0).rgb, 1.0); // selected-source fullscreen diagnostic probe: gbuffer-primary", primary.semantic_name)
        };
        *source = source.replacen(&assignment, &replacement, 1);
        return Ok(());
    }
    if mode == "depth-input" || mode == "depth-input-flipped" || mode == "depth-input-amplified" {
        let Some(primary) = outputs
            .iter()
            .find(|output| output.role.shader_pack_color_name() == Some("primary"))
        else {
            return Err(GalError::invalid_argument(
                "depth input probe requires a primary color output",
            ));
        };
        let assignment = format!("{} = vec4(color, 1.0);", primary.semantic_name);
        if !source.contains(&assignment) || !source.contains("float z0 =") {
            return Err(GalError::invalid_argument(
                "depth input probe could not locate deferred depth or primary output",
            ));
        }
        let expression = if mode == "depth-input-flipped" {
            "texelFetch(depthtex0, ivec2(texelCoord.x, int(viewHeight) - 1 - texelCoord.y), 0).r"
        } else if mode == "depth-input-amplified" {
            "fract(z0 * 1024.0)"
        } else {
            "z0"
        };
        *source = source.replacen(
            &assignment,
            &format!(
                "{} = vec4(vec3({expression}), 1.0); // selected-source fullscreen diagnostic probe: {mode}",
                primary.semantic_name
            ),
            1,
        );
        return Ok(());
    }
    if mode == "deferred-fog-inputs" {
        let Some(primary) = outputs
            .iter()
            .find(|output| output.role.shader_pack_color_name() == Some("primary"))
        else {
            return Err(GalError::invalid_argument(
                "deferred fog probe requires a primary color output",
            ));
        };
        const MAIN_DEPTH_DECLARATION: &str = "float z0 = texelFetch(depthtex0, texelCoord, 0).r;";
        if !source.contains(MAIN_DEPTH_DECLARATION) {
            return Err(GalError::invalid_argument(
                "deferred fog probe could not locate deferred depth declaration",
            ));
        }
        let assignment = format!("{} = vec4(color, 1.0);", primary.semantic_name);
        if !source.contains(&assignment) {
            return Err(GalError::invalid_argument(
                "deferred fog probe could not locate primary color assignment",
            ));
        }
        *source = source.replacen(
            MAIN_DEPTH_DECLARATION,
            "float z0 = texelFetch(depthtex0, texelCoord, 0).r; vec3 vulkanicDeferredFogInputs = vec3(z0, 0.0, 0.0);",
            1,
        );
        // Initialize the distance channel before the depth branch so a
        // far-plane sample (z0 == 1) still reports the reconstructed distance
        // instead of looking indistinguishable from an unexecuted probe.
        let view_distance = "float lViewPos = length(viewPos);";
        if !source.contains(view_distance) {
            return Err(GalError::invalid_argument(
                "deferred fog probe could not locate reconstructed view distance",
            ));
        }
        *source = source.replacen(
            view_distance,
            "float lViewPos = length(viewPos); vulkanicDeferredFogInputs = vec3(z0, clamp(lViewPos / max(far, 1.0), 0.0, 1.0), 0.0);",
            1,
        );
        // Capture the values after the ordinary-world fog path has established
        // skyFade and color; the probe does not alter the normal route.
        // Complementary's deferred stage passes its local `vec3 color`
        // directly, while small synthetic fixtures and some pack variants
        // spell the same call as `color.rgb`. Accept either exact source form
        // so this diagnostic observes the real fog boundary instead of
        // rejecting source preparation before the capture can run.
        let fog_call = if source
            .contains("DoFog(color, skyFade, lViewPos, playerPos, VdotU, VdotS, dither);")
        {
            "DoFog(color, skyFade, lViewPos, playerPos, VdotU, VdotS, dither);"
        } else if source
            .contains("DoFog(color.rgb, skyFade, lViewPos, playerPos, VdotU, VdotS, dither);")
        {
            "DoFog(color.rgb, skyFade, lViewPos, playerPos, VdotU, VdotS, dither);"
        } else {
            return Err(GalError::invalid_argument(
                "deferred fog probe could not locate fog call",
            ));
        };
        *source = source.replacen(
            fog_call,
            "DoFog(color.rgb, skyFade, lViewPos, playerPos, VdotU, VdotS, dither); vulkanicDeferredFogInputs = vec3(z0, clamp(lViewPos / max(far, 1.0), 0.0, 1.0), clamp(skyFade, 0.0, 1.0));",
            1,
        );
        *source = source.replacen(
            &assignment,
            &format!(
                "{} = vec4(vulkanicDeferredFogInputs, 1.0); // selected-source fullscreen diagnostic probe: deferred-fog-inputs",
                primary.semantic_name
            ),
            1,
        );
        return Ok(());
    }
    if !source.contains("dhDepthTex") || !source.contains("texelCoord") {
        return Err(GalError::invalid_argument(
            "distant-horizons-depth fullscreen probe target lacks dhDepthTex or texelCoord",
        ));
    }
    let Some(primary) = outputs
        .iter()
        .find(|output| output.role.shader_pack_color_name() == Some("primary"))
    else {
        return Err(GalError::invalid_argument(
            "distant-horizons-depth fullscreen probe requires a primary color output",
        ));
    };
    match mode.as_str() {
        "distant-horizons-depth" => {
            let assignment = format!("{} = vec4(color, 1.0);", primary.semantic_name);
            if !source.contains(&assignment) {
                return Err(GalError::invalid_argument(
                    "distant-horizons-depth fullscreen probe could not locate the primary color assignment",
                ));
            }
            *source = source.replacen(
                &assignment,
                &format!(
                    "{} = vec4(vec3(texelFetch(dhDepthTex, texelCoord, 0).r), 1.0); // selected-source fullscreen diagnostic probe: distant-horizons-depth",
                    primary.semantic_name
                ),
                1,
            );
        }
        "distant-horizons-depth-routing" => {
            // `deferred1` chooses the DH path only where the main terrain
            // depth is clear and the DH depth is populated. Expose both
            // values and that exact predicate in one source-stage image.
            // This is capture-only evidence; it neither changes the normal
            // shader nor turns a depth mismatch into a rendering workaround.
            const MAIN_DEPTH_DECLARATION: &str =
                "float z0 = texelFetch(depthtex0, texelCoord, 0).r;";
            if !source.contains(MAIN_DEPTH_DECLARATION) {
                return Err(GalError::invalid_argument(
                    "distant-horizons depth-routing probe could not locate deferred1 main-depth declaration",
                ));
            }
            let assignment = format!("{} = vec4(color, 1.0);", primary.semantic_name);
            if !source.contains(&assignment) {
                return Err(GalError::invalid_argument(
                    "distant-horizons depth-routing probe could not locate the primary color assignment",
                ));
            }
            *source = source.replacen(
                MAIN_DEPTH_DECLARATION,
                "float z0 = texelFetch(depthtex0, texelCoord, 0).r; float vulkanicDhDepthProbe = texelFetch(dhDepthTex, texelCoord, 0).r;",
                1,
            );
            *source = source.replacen(
                &assignment,
                &format!(
                    "{} = vec4(z0, vulkanicDhDepthProbe, (z0 >= 1.0 && vulkanicDhDepthProbe < 1.0) ? 1.0 : 0.0, 1.0); // selected-source fullscreen diagnostic probe: distant-horizons-depth-routing",
                    primary.semantic_name
                ),
                1,
            );
        }
        "distant-horizons-depth-coordinate" => {
            // Keep this at the source-stage boundary and expose both the
            // texel selected by deferred1 and its vertically mirrored peer.
            // A DH depth image can be perfectly valid while a fullscreen
            // coordinate convention reads it upside down, so aggregate depth
            // images alone cannot distinguish these two cases.
            const MAIN_DEPTH_DECLARATION: &str =
                "float z0 = texelFetch(depthtex0, texelCoord, 0).r;";
            if !source.contains(MAIN_DEPTH_DECLARATION) {
                return Err(GalError::invalid_argument(
                    "distant-horizons depth-coordinate probe could not locate deferred1 main-depth declaration",
                ));
            }
            if !source.contains("viewHeight") {
                return Err(GalError::invalid_argument(
                    "distant-horizons depth-coordinate probe requires deferred1 viewHeight semantics",
                ));
            }
            insert_selected_source_distant_horizons_probe_state(source)?;
            replace_selected_source_primary_output(
                source,
                primary,
                "vec4(vulkanicDhDepthProbe, texelFetch(dhDepthTex, ivec2(texelCoord.x, int(viewHeight) - 1 - texelCoord.y), 0).r, texCoord.y, 1.0); // selected-source fullscreen diagnostic probe: distant-horizons-depth-coordinate",
            )?;
        }
        "distant-horizons-fog-inputs" => {
            // This replacement is deliberately inside deferred1's DH-only
            // sky branch. It exposes the exact semantic values consumed by
            // DoFog: depth, reconstructed distance relative to the pack's
            // DH render distance, and the source UV orientation. It cannot
            // turn into a production lighting or fog workaround.
            let fog_call = "DoFog(color.rgb, skyFade, lViewPos, playerPos, VdotU, VdotS, dither);";
            if !source.contains(fog_call) || !source.contains("dhRenderDistance") {
                return Err(GalError::invalid_argument(
                    "distant-horizons-fog-inputs fullscreen probe target lacks the DH fog call or dhRenderDistance",
                ));
            }
            insert_selected_source_distant_horizons_probe_state(source)?;
            replace_selected_source_distant_horizons_fog_call(
                source,
                fog_call,
                "vulkanicDhFogInputs = vec3(vulkanicDhDepthProbe, clamp(lViewPos / max(float(dhRenderDistance), 1.0), 0.0, 1.0), texCoord.y); DoFog(color.rgb, skyFade, lViewPos, playerPos, VdotU, VdotS, dither);",
            )?;
            replace_selected_source_primary_output(
                source,
                primary,
                "vec4(vulkanicDhFogInputs, 1.0); // selected-source fullscreen diagnostic probe: distant-horizons-fog-inputs",
            )?;
        }
        "distant-horizons-fog-effect" => {
            // Keep the real fog call, then encode its observable effect.
            // Logarithmic distance avoids the ordinary DH configuration
            // clamp hiding the difference between 64 and 1,024 blocks.
            let fog_call = "DoFog(color.rgb, skyFade, lViewPos, playerPos, VdotU, VdotS, dither);";
            if !source.contains(fog_call) {
                return Err(GalError::invalid_argument(
                    "distant-horizons-fog-effect fullscreen probe target lacks the DH fog call",
                ));
            }
            insert_selected_source_distant_horizons_probe_state(source)?;
            replace_selected_source_distant_horizons_fog_call(
                source,
                fog_call,
                "vec3 vulkanicFogInputColor = color.rgb; DoFog(color.rgb, skyFade, lViewPos, playerPos, VdotU, VdotS, dither); vulkanicDhFogInputs = vec3(vulkanicDhDepthProbe, clamp(log2(max(lViewPos, 1.0)) / 12.0, 0.0, 1.0), clamp(length(vulkanicFogInputColor - color.rgb), 0.0, 1.0));",
            )?;
            replace_selected_source_primary_output(
                source,
                primary,
                "vec4(vulkanicDhFogInputs, 1.0); // selected-source fullscreen diagnostic probe: distant-horizons-fog-effect",
            )?;
        }
        _ => unreachable!("validated selected-source fullscreen probe mode"),
    }
    Ok(())
}

pub(super) fn insert_selected_source_distant_horizons_probe_state(source: &mut String) -> GalResult<()> {
    const MAIN_DEPTH_DECLARATION: &str = "float z0 = texelFetch(depthtex0, texelCoord, 0).r;";
    if !source.contains(MAIN_DEPTH_DECLARATION) {
        return Err(GalError::invalid_argument(
            "distant-horizons fullscreen probe could not locate deferred1 main-depth declaration",
        ));
    }
    *source = source.replacen(
        MAIN_DEPTH_DECLARATION,
        "float z0 = texelFetch(depthtex0, texelCoord, 0).r; float vulkanicDhDepthProbe = texelFetch(dhDepthTex, texelCoord, 0).r; vec3 vulkanicDhFogInputs = vec3(0.0);",
        1,
    );
    Ok(())
}

pub(super) fn replace_selected_source_primary_output(
    source: &mut String,
    primary: &FullscreenSourceFragmentOutput,
    replacement: &str,
) -> GalResult<()> {
    let assignment = format!("{} = vec4(color, 1.0);", primary.semantic_name);
    if !source.contains(&assignment) {
        return Err(GalError::invalid_argument(
            "distant-horizons fullscreen probe could not locate deferred1 primary output",
        ));
    }
    *source = source.replacen(
        &assignment,
        &format!("{} = {replacement}", primary.semantic_name),
        1,
    );
    Ok(())
}

/// Replaces only `deferred1`'s DH-depth branch. The program has an earlier
/// ordinary-world fog call with the same source text; touching it would refer
/// to DH locals before they exist and turn a diagnostic into invalid shader
/// source.
pub(super) fn replace_selected_source_distant_horizons_fog_call(
    source: &mut String,
    fog_call: &str,
    replacement: &str,
) -> GalResult<()> {
    const DH_DEPTH_BRANCH: &str = "if (z0DH < 1.0) { // Distant Horizons Chunks";
    let branch_start = source.find(DH_DEPTH_BRANCH).ok_or_else(|| {
        GalError::invalid_argument(
            "distant-horizons fullscreen probe could not locate deferred1's DH-depth branch",
        )
    })?;
    let call_offset = source[branch_start..].find(fog_call).ok_or_else(|| {
        GalError::invalid_argument(
            "distant-horizons fullscreen probe could not locate the DH-only fog call",
        )
    })?;
    let call_start = branch_start + call_offset;
    source.replace_range(call_start..call_start + fog_call.len(), replacement);
    Ok(())
}

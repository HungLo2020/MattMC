//! Source-authored integer target addresses are distinct from image-domain UVs.
use super::*;

const SOURCE: u8 = 1;
const NATIVE: u8 = 2;
const FETCH: &str = "vulkanic_source_fullscreen_fetch_source_texel";

/// Convert absolute/view-relative source pixel coordinates, including aliases,
/// without flipping the native `texelCoord` or image-varying addressing again.
/// Select targets by their declared role; a PNG override must retain its rows.
pub(super) fn lower_fullscreen_authored_texels(
    source: String,
    varyings: &TerrainSourceVaryingContract,
    bindings: &TerrainSourceResourceBindings,
) -> GalResult<String> {
    let semantic = crate::render::shaderpack::source::dialect::strip_comments(&source);
    let bytes = semantic.as_bytes();
    let mut origins = BTreeMap::from([
        ("viewWidth".to_string(), SOURCE),
        ("viewHeight".to_string(), SOURCE),
        (
            "vulkanic_source_fullscreen_fragment_coord".to_string(),
            SOURCE,
        ),
        ("vulkanic_source_fullscreen_screen_uv".to_string(), SOURCE),
        ("texelCoord".to_string(), NATIVE),
        ("gl_FragCoord".to_string(), NATIVE),
        (
            "vulkanic_source_fullscreen_history_corner".to_string(),
            NATIVE,
        ),
    ]);
    for field in varyings
        .fields()
        .iter()
        .filter(|field| field.type_name() == "vec2")
    {
        origins.insert(field.name().to_string(), NATIVE);
    }
    // Reprojection lowering converts source-screen returns into image UVs.
    // Their callers may have no direct image-varying dependency to reveal that
    // domain (for example an integer history read from a reconstructed point).
    for (at, _) in semantic.match_indices("vec2") {
        if !identifier_at(bytes, at, b"vec2") {
            continue;
        }
        let Some(name_start) = skip_glsl_trivia(bytes, at + 4) else {
            continue;
        };
        let mut name_end = name_start;
        while bytes.get(name_end).is_some_and(is_glsl_identifier_byte) {
            name_end += 1;
        }
        if name_end == name_start {
            continue;
        }
        let Some(open) = skip_glsl_trivia(bytes, name_end) else {
            continue;
        };
        if bytes.get(open) != Some(&b'(') {
            continue;
        }
        let Some(close) = matching_paren(bytes, open) else {
            continue;
        };
        let Some(body) = skip_glsl_trivia(bytes, close + 1) else {
            continue;
        };
        if bytes.get(body) != Some(&b'{') {
            continue;
        }
        let Some(end) = matching_brace(bytes, body) else {
            continue;
        };
        let returns = semantic[body + 1..end]
            .match_indices("return")
            .map(|(offset, _)| body + 1 + offset)
            .filter(|offset| identifier_at(bytes, *offset, b"return"))
            .collect::<Vec<_>>();
        if !returns.is_empty()
            && returns.iter().all(|offset| {
                skip_glsl_trivia(bytes, offset + 6).is_some_and(|start| {
                    identifier_at(bytes, start, b"vulkanic_source_fullscreen_screen_uv")
                })
            })
        {
            origins.insert(semantic[name_start..name_end].to_string(), NATIVE);
        }
    }
    // Follow direct assignments rather than assuming a spelling such as `view`.
    // Names shared by different scopes conservatively retain both origins.
    let mut assignments = Vec::new();
    for (at, _) in semantic.match_indices('=') {
        if bytes.get(at + 1) == Some(&b'=') {
            continue;
        }
        let mut end = at;
        while end > 0 && bytes[end - 1].is_ascii_whitespace() {
            end -= 1;
        }
        let mut start = end;
        while start > 0 && is_glsl_identifier_byte(&bytes[start - 1]) {
            start -= 1;
        }
        if start == end {
            continue;
        }
        let mut before_name = start;
        while before_name > 0 && bytes[before_name - 1].is_ascii_whitespace() {
            before_name -= 1;
        }
        if before_name > 0 && bytes[before_name - 1] == b'.' {
            continue;
        }
        let Some(rhs_end) = semantic[at + 1..].find(';').map(|end| at + 1 + end) else {
            continue;
        };
        assignments.push((&semantic[start..end], &semantic[at + 1..rhs_end]));
    }
    // Each name can acquire only two origin bits. A dependency worklist
    // reaches the fixed point without a depth cutoff or repeated full scans.
    // Unknown/mixed domains keep their existing image addressing.
    let mut dependents: BTreeMap<String, Vec<usize>> = BTreeMap::new();
    for (index, (_, expression)) in assignments.iter().enumerate() {
        for dependency in value_identifiers(expression) {
            dependents.entry(dependency).or_default().push(index);
        }
    }
    let mut pending = (0..assignments.len()).collect::<std::collections::VecDeque<_>>();
    let mut queued = vec![true; assignments.len()];
    while let Some(index) = pending.pop_front() {
        queued[index] = false;
        let (name, expression) = assignments[index];
        if name == "texelCoord" {
            continue;
        }
        let origin = expression_origin(expression, &origins);
        let entry = origins.entry(name.to_string()).or_default();
        let combined = *entry | origin;
        if combined == *entry {
            continue;
        }
        *entry = combined;
        for dependent in dependents.get(name).into_iter().flatten().copied() {
            if !queued[dependent] {
                queued[dependent] = true;
                pending.push_back(dependent);
            }
        }
    }
    let mut edits = Vec::new();
    for (at, _) in semantic.match_indices("texelFetch") {
        if !identifier_at(bytes, at, b"texelFetch") {
            continue;
        }
        let Some(open) = skip_glsl_trivia(bytes, at + "texelFetch".len()) else {
            continue;
        };
        if bytes.get(open) != Some(&b'(') {
            continue;
        }
        let Some(close) = matching_paren(bytes, open) else {
            continue;
        };
        let args = split_top_level_arguments(&semantic[open + 1..close]);
        if args.len() != 3 {
            continue;
        }
        if !matches!(
            bindings.role_for(args[0].trim()),
            Some(
                TerrainSourceResourceRole::MainDepth
                    | TerrainSourceResourceRole::MainDepthBeforeTranslucency
                    | TerrainSourceResourceRole::MainDepthPrevious
                    | TerrainSourceResourceRole::DistantHorizonsOpaqueDepth
                    | TerrainSourceResourceRole::DistantHorizonsDepthBeforeTranslucency
                    | TerrainSourceResourceRole::ShaderPackColor(_)
            )
        ) {
            continue;
        }
        if expression_origin(args[1], &origins) == SOURCE {
            edits.push(at);
        }
    }
    if edits.is_empty() {
        return Ok(source);
    }
    let mut result = source;
    for at in edits.into_iter().rev() {
        result.replace_range(at..at + "texelFetch".len(), FETCH);
    }
    // Function arguments evaluate coordinate and LOD expressions only once.
    // textureSize uses the sampled mip's dimensions, without a new uniform.
    let mut helpers = String::from(
        r#"ivec2 vulkanic_source_fullscreen_source_texel(ivec2 coordinate, ivec2 extent) {
#ifdef VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH
    return ivec2(coordinate.x, extent.y - 1 - coordinate.y);
#else
    return coordinate;
#endif
}
"#,
    );
    for (sampler, result) in [
        ("sampler2D", "vec4"),
        ("isampler2D", "ivec4"),
        ("usampler2D", "uvec4"),
    ] {
        helpers.push_str(&format!(
            "{result} {FETCH}({sampler} source_sampler, ivec2 coordinate, int level) {{ return texelFetch(source_sampler, vulkanic_source_fullscreen_source_texel(coordinate, textureSize(source_sampler, level)), level); }}\n"
        ));
    }
    insert_after_version(&result, &helpers)
}

fn expression_origin(expression: &str, origins: &BTreeMap<String, u8>) -> u8 {
    let identifiers = value_identifiers(expression);
    // Explicit conversions establish the output domain even when the input
    // is an image varying. Already converted history coordinates are native.
    if expression
        .trim_start()
        .starts_with("vulkanic_source_fullscreen_history_corner")
    {
        return NATIVE;
    }
    if expression
        .trim_start()
        .starts_with("vulkanic_source_fullscreen_screen_uv")
        || expression
            .trim_start()
            .starts_with("vulkanic_source_fullscreen_fragment_coord")
    {
        return SOURCE;
    }
    let origin = identifiers.iter().fold(0, |origin, name| {
        origin | origins.get(name).copied().unwrap_or_default()
    });
    if origin != 0 {
        return origin;
    }
    // A literal integer-vector address is authored in source rows. Scalars
    // alone have no coordinate domain and do not taint unrelated expressions.
    if expression.trim_start().starts_with("ivec2")
        && identifiers.iter().all(|name| {
            matches!(
                name.as_str(),
                "ivec2" | "int" | "uint" | "float" | "u" | "f"
            )
        })
    {
        SOURCE
    } else {
        0
    }
}

/// Member selectors are not variables; a local `y` must not change `view.y`'s
/// origin. Numeric exponents/suffixes are not source identifiers either.
fn value_identifiers(expression: &str) -> BTreeSet<String> {
    let bytes = expression.as_bytes();
    let mut identifiers = BTreeSet::new();
    let mut cursor = 0;
    while cursor < bytes.len() {
        if !(bytes[cursor].is_ascii_alphabetic() || bytes[cursor] == b'_')
            || (cursor > 0 && is_glsl_identifier_byte(&bytes[cursor - 1]))
        {
            cursor += 1;
            continue;
        }
        let start = cursor;
        while cursor < bytes.len() && is_glsl_identifier_byte(&bytes[cursor]) {
            cursor += 1;
        }
        let mut before = start;
        while before > 0 && bytes[before - 1].is_ascii_whitespace() {
            before -= 1;
        }
        if before == 0 || bytes[before - 1] != b'.' {
            identifiers.insert(expression[start..cursor].to_string());
        }
    }
    identifiers
}

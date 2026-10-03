//! Convert an image-domain varying only where it enters inverse projection.
use super::*;

pub(super) fn lower_inline_projection_uvs(
    source: &str, varyings: &TerrainSourceVaryingContract,
) -> (String, bool) {
    let semantic = crate::render::shaderpack::source::dialect::strip_comments(source);
    let bytes = semantic.as_bytes();
    let mut edits = Vec::new();
    for (at, _) in semantic.match_indices("vec4") {
        if !identifier_at(bytes, at, b"vec4") { continue; }
        // Accept arbitrary whitespace/comments and nested parentheses between
        // the inverse matrix and its constructor. Only a direct matrix operand
        // is recognized; unrelated constructors and authored source UVs stay.
        let mut before = at;
        while before > 0 && (bytes[before - 1].is_ascii_whitespace() || bytes[before - 1] == b'(') {
            before -= 1;
        }
        if before == 0 || bytes[before - 1] != b'*' { continue; }
        before -= 1;
        while before > 0 && bytes[before - 1].is_ascii_whitespace() { before -= 1; }
        let matrix_end = before;
        while before > 0 && is_glsl_identifier_byte(&bytes[before - 1]) { before -= 1; }
        if !matches!(&semantic[before..matrix_end], "gbufferProjectionInverse" | "dhProjectionInverse") {
            continue;
        }
        let Some(open) = skip_glsl_trivia(bytes, at + 4) else { continue; };
        if bytes.get(open) != Some(&b'(') { continue; }
        let Some(start) = skip_glsl_trivia(bytes, open + 1) else { continue; };
        let mut end = start;
        while bytes.get(end).is_some_and(is_glsl_identifier_byte) { end += 1; }
        let name = &semantic[start..end];
        if !varyings.fields().iter().any(|field| field.type_name() == "vec2" && field.name() == name) {
            continue;
        }
        let Some(comma) = skip_glsl_trivia(bytes, end) else { continue; };
        if bytes.get(comma) != Some(&b',') { continue; }
        edits.push((start, end));
    }
    let changed = !edits.is_empty();
    let mut output = source.to_string();
    for (start, end) in edits.into_iter().rev() {
        let replacement = format!("vulkanic_source_fullscreen_screen_uv({})", &source[start..end]);
        output.replace_range(start..end, &replacement);
    }
    (output, changed)
}

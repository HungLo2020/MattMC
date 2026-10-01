//! GLSL text utilities: version/declaration insertion, identifiers, brace and argument matching.

use super::*;

pub(super) fn matching_paren(bytes: &[u8], open: usize) -> Option<usize> {
    let mut depth = 0i32;
    for (index, &byte) in bytes.iter().enumerate().skip(open) {
        match byte {
            b'(' => depth += 1,
            b')' => {
                depth -= 1;
                if depth == 0 {
                    return Some(index);
                }
            }
            _ => {}
        }
    }
    None
}

pub(super) fn matching_brace(bytes: &[u8], open: usize) -> Option<usize> {
    let mut depth = 0i32;
    for (index, &byte) in bytes.iter().enumerate().skip(open) {
        match byte {
            b'{' => depth += 1,
            b'}' => {
                depth -= 1;
                if depth == 0 {
                    return Some(index);
                }
            }
            _ => {}
        }
    }
    None
}

pub(super) fn split_top_level_arguments(arguments: &str) -> Vec<&str> {
    let mut parts = Vec::new();
    let mut depth = 0i32;
    let mut start = 0usize;
    for (index, character) in arguments.char_indices() {
        match character {
            '(' | '[' => depth += 1,
            ')' | ']' => depth -= 1,
            ',' if depth == 0 => {
                parts.push(&arguments[start..index]);
                start = index + 1;
            }
            _ => {}
        }
    }
    parts.push(&arguments[start..]);
    parts
}

pub(super) fn remove_known_legacy_attributes(source: &str) -> GalResult<String> {
    let mut output = String::with_capacity(source.len());
    for line in source.lines() {
        let trimmed = line.trim();
        if let Some(declaration) = trimmed.strip_prefix("attribute ") {
            let name = declaration
                .trim_end_matches(';')
                .split_whitespace()
                .last()
                .ok_or_else(|| GalError::invalid_argument("malformed legacy terrain attribute"))?;
            let name = name.trim_end_matches(|character| character == ';' || character == ']');
            if !matches!(
                name,
                "mc_Entity" | "mc_midTexCoord" | "at_tangent" | "at_midBlock"
            ) {
                return Err(GalError::unsupported_feature(format!(
                    "terrain vertex declares unsupported legacy attribute '{name}'"
                )));
            }
            continue;
        }
        output.push_str(line);
        output.push('\n');
    }
    Ok(output)
}

pub(super) fn upgrade_version(source: &str) -> GalResult<String> {
    let lines = source.lines().collect::<Vec<_>>();
    if lines.is_empty() {
        return Err(GalError::invalid_argument("shader source is empty"));
    }
    // The owned preprocessor may inject semantic `#define`s ahead of the
    // source's root version. GLSL requires version first, so move exactly one
    // root directive to the prologue without dropping the configured defines.
    let mut output = String::from("#version 450\n");
    // Shader packs commonly leave the version to Iris's compile wrapper. The
    // copied, fully preprocessed source is still complete semantic input, so
    // Rust owns the target GLSL version when the pack did not declare one.
    let version_line = lines
        .iter()
        .position(|line| line.trim_start().starts_with("#version"));
    for (index, line) in lines.into_iter().enumerate() {
        if Some(index) == version_line {
            continue;
        }
        output.push_str(line);
        output.push('\n');
    }
    Ok(output)
}

pub(super) fn insert_after_version(source: &str, declarations: &str) -> GalResult<String> {
    let Some(newline) = source.find('\n') else {
        return Err(GalError::invalid_argument(
            "lowered shader version line has no body",
        ));
    };
    let mut output = String::with_capacity(source.len() + declarations.len());
    output.push_str(&source[..newline + 1]);
    output.push_str(declarations);
    output.push_str(&source[newline + 1..]);
    Ok(output)
}

pub(super) fn append_clip_depth_convention_finalizer(source: &str) -> GalResult<String> {
    let closing_brace = main_function_closing_brace(source).ok_or_else(|| {
        GalError::invalid_argument(
            "lowered source vertex shader has no brace-balanced void main() body for clip-depth convention lowering",
        )
    })?;
    let mut output = String::with_capacity(source.len() + 160);
    output.push_str(&source[..closing_brace]);
    output.push_str("\n#ifdef VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH\n");
    output.push_str("    gl_Position.z = (gl_Position.z + gl_Position.w) * 0.5;\n");
    output.push_str("#endif\n");
    output.push_str(&source[closing_brace..]);
    Ok(output)
}

pub(super) fn main_function_closing_brace(source: &str) -> Option<usize> {
    let bytes = source.as_bytes();
    let mut cursor = 0;
    while cursor < bytes.len() {
        cursor = skip_glsl_trivia(bytes, cursor)?;
        if !identifier_at(bytes, cursor, b"void") {
            cursor = cursor.saturating_add(1);
            continue;
        }
        let mut after_void = cursor + b"void".len();
        after_void = skip_glsl_trivia(bytes, after_void)?;
        if !identifier_at(bytes, after_void, b"main") {
            cursor = after_void;
            continue;
        }
        let mut signature = skip_glsl_trivia(bytes, after_void + b"main".len())?;
        if bytes.get(signature) != Some(&b'(') {
            cursor = signature;
            continue;
        }
        let mut parens = 0u32;
        loop {
            signature = skip_glsl_trivia(bytes, signature)?;
            match *bytes.get(signature)? {
                b'(' => parens = parens.checked_add(1)?,
                b')' => {
                    parens = parens.checked_sub(1)?;
                    if parens == 0 {
                        signature += 1;
                        break;
                    }
                }
                _ => {}
            }
            signature += 1;
        }
        let mut body = skip_glsl_trivia(bytes, signature)?;
        if bytes.get(body) != Some(&b'{') {
            cursor = body;
            continue;
        }
        let mut braces = 0u32;
        loop {
            body = skip_glsl_trivia(bytes, body)?;
            match *bytes.get(body)? {
                b'{' => braces = braces.checked_add(1)?,
                b'}' => {
                    braces = braces.checked_sub(1)?;
                    if braces == 0 {
                        return Some(body);
                    }
                }
                _ => {}
            }
            body += 1;
        }
    }
    None
}

pub(super) fn skip_glsl_trivia(bytes: &[u8], mut cursor: usize) -> Option<usize> {
    loop {
        while bytes.get(cursor).is_some_and(u8::is_ascii_whitespace) {
            cursor += 1;
        }
        if bytes.get(cursor..cursor + 2) == Some(b"//") {
            cursor += 2;
            while bytes.get(cursor).is_some_and(|byte| *byte != b'\n') {
                cursor += 1;
            }
            continue;
        }
        if bytes.get(cursor..cursor + 2) == Some(b"/*") {
            cursor += 2;
            while bytes.get(cursor..cursor + 2) != Some(b"*/") {
                bytes.get(cursor)?;
                cursor += 1;
            }
            cursor += 2;
            continue;
        }
        return Some(cursor);
    }
}

pub(super) fn identifier_at(bytes: &[u8], start: usize, identifier: &[u8]) -> bool {
    let Some(end) = start.checked_add(identifier.len()) else {
        return false;
    };
    bytes.get(start..end) == Some(identifier)
        && !bytes
            .get(start.wrapping_sub(1))
            .is_some_and(is_glsl_identifier_byte)
        && !bytes.get(end).is_some_and(is_glsl_identifier_byte)
}

pub(super) fn is_glsl_identifier_byte(byte: &u8) -> bool {
    byte.is_ascii_alphanumeric() || *byte == b'_'
}

pub(super) fn replace_identifier(source: &str, from: &str, to: &str) -> String {
    let mut output = String::with_capacity(source.len());
    let bytes = source.as_bytes();
    let mut cursor = 0;
    while cursor < bytes.len() {
        let Some(relative) = source[cursor..].find(from) else {
            output.push_str(&source[cursor..]);
            break;
        };
        let start = cursor + relative;
        let end = start + from.len();
        let before = start == 0 || !is_identifier_byte(bytes[start - 1]);
        let after = end == bytes.len() || !is_identifier_byte(bytes[end]);
        output.push_str(&source[cursor..start]);
        if before && after {
            output.push_str(to);
        } else {
            output.push_str(from);
        }
        cursor = end;
    }
    output
}

/// Converts a source's legacy fog record to an explicit function backed by
/// named semantic uniforms. The returned flag controls whether the matching
/// source preamble must be emitted.
pub(super) fn lower_legacy_fog(source: &mut String) -> bool {
    if !glsl_identifiers(source).contains("gl_Fog") {
        return false;
    }
    *source = replace_identifier(source, "gl_Fog", "vulkanic_source_fog()");
    true
}

pub(super) fn replace_fragment_output(
    source: &str,
    index: u32,
    replacement: &str,
) -> GalResult<(String, u32)> {
    let needle = "gl_FragData";
    let bytes = source.as_bytes();
    let mut output = String::with_capacity(source.len());
    let mut cursor = 0;
    let mut occurrences = 0;
    while cursor < bytes.len() {
        let Some(relative) = source[cursor..].find(needle) else {
            output.push_str(&source[cursor..]);
            break;
        };
        let start = cursor + relative;
        let end = start + needle.len();
        output.push_str(&source[cursor..start]);
        if (start > 0 && is_identifier_byte(bytes[start - 1]))
            || (end < bytes.len() && is_identifier_byte(bytes[end]))
        {
            output.push_str(needle);
            cursor = end;
            continue;
        }
        let mut offset = end;
        skip_space(bytes, &mut offset);
        if bytes.get(offset) != Some(&b'[') {
            output.push_str(needle);
            cursor = end;
            continue;
        }
        offset += 1;
        skip_space(bytes, &mut offset);
        let digits_start = offset;
        while bytes.get(offset).is_some_and(u8::is_ascii_digit) {
            offset += 1;
        }
        let Some(found_index) = source[digits_start..offset].parse::<u32>().ok() else {
            return Err(GalError::unsupported_feature(
                "gl_FragData index is not a literal",
            ));
        };
        skip_space(bytes, &mut offset);
        if bytes.get(offset) != Some(&b']') {
            return Err(GalError::unsupported_feature(
                "malformed gl_FragData output index",
            ));
        }
        offset += 1;
        if found_index == index {
            output.push_str(replacement);
            occurrences += 1;
        } else {
            output.push_str(&source[start..offset]);
        }
        cursor = offset;
    }
    Ok((output, occurrences))
}

pub(super) fn contains_fragment_output(source: &str) -> GalResult<bool> {
    let (_, occurrences) = replace_fragment_output(source, u32::MAX, "")?;
    Ok(occurrences > 0 || source.contains("gl_FragData"))
}

pub(super) fn skip_space(bytes: &[u8], offset: &mut usize) {
    while bytes.get(*offset).is_some_and(u8::is_ascii_whitespace) {
        *offset += 1;
    }
}

pub(super) fn is_identifier_byte(byte: u8) -> bool {
    byte == b'_' || byte.is_ascii_alphanumeric()
}

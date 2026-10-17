//! Identifier and resource path rules shared by packs, registries and tags
//! (`ResourceLocation` and `FileUtil`, including Iris's two path changes).

use std::cmp::Ordering;

/// `ResourceLocation.validNamespaceChar`.
pub(crate) fn valid_namespace_char(byte: u8) -> bool {
    matches!(byte, b'_' | b'-' | b'a'..=b'z' | b'0'..=b'9' | b'.')
}

/// `ResourceLocation.validPathChar`: Iris also allows upper case letters.
pub(crate) fn valid_path_char(byte: u8) -> bool {
    matches!(byte, b'A'..=b'Z' | b'_' | b'-' | b'a'..=b'z' | b'0'..=b'9' | b'/' | b'.')
}

/// `ResourceLocation.isValidNamespace` (the empty string is valid).
pub(crate) fn is_valid_namespace(namespace: &str) -> bool {
    namespace.bytes().all(valid_namespace_char)
}

/// `ResourceLocation.isValidPath`: Iris rejects the literal `DUMMY`.
pub(crate) fn is_valid_path(path: &str) -> bool {
    path != "DUMMY" && path.bytes().all(valid_path_char)
}

/// `FileUtil.isValidStrictPathSegment`: `[-._a-z0-9]+`.
pub(crate) fn is_valid_strict_segment(segment: &str) -> bool {
    !segment.is_empty() && segment.bytes().all(|byte| matches!(byte, b'-' | b'.' | b'_' | b'a'..=b'z' | b'0'..=b'9'))
}

/// `FileUtil.decomposePath`: the `/`-separated segments, each strict and
/// neither empty, `.` nor `..`. The error is Java's message.
pub(crate) fn decompose_path(path: &str) -> Result<Vec<&str>, String> {
    if !path.contains('/') {
        return match path {
            "" | "." | ".." => Err(format!("Invalid path '{path}'")),
            _ if !is_valid_strict_segment(path) => Err(format!("Invalid path '{path}'")),
            _ => Ok(vec![path]),
        };
    }
    let mut segments = Vec::new();
    for segment in path.split('/') {
        if matches!(segment, "" | "." | "..") || !is_valid_strict_segment(segment) {
            return Err(format!("Invalid segment '{segment}' in path '{path}'"));
        }
        segments.push(segment);
    }
    Ok(segments)
}

/// `FileUtil.validatePath(String...)`: Java throws `IllegalArgumentException`.
pub(crate) fn validate_root_path(segments: &[&str]) -> Result<(), String> {
    if segments.is_empty() {
        return Err("Path must have at least one element".into());
    }
    for segment in segments {
        if *segment == ".." || *segment == "." || !is_valid_strict_segment(segment) {
            return Err(format!("Illegal segment {segment} in path [{}]", segments.join(", ")));
        }
    }
    Ok(())
}

/// `ResourceLocation.compareTo`: path first, then namespace. Identifier text
/// is ASCII, so byte order is Java's UTF-16 order.
pub(crate) fn compare_locations(a: (&str, &str), b: (&str, &str)) -> Ordering {
    a.1.cmp(b.1).then_with(|| a.0.cmp(b.0))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn decompose_and_validate_match_file_util() {
        assert_eq!(Ok(vec!["textures", "block", "a.png"]), decompose_path("textures/block/a.png"));
        assert_eq!(Err("Invalid path ''".to_string()), decompose_path(""));
        assert_eq!(Err("Invalid path '..'".to_string()), decompose_path(".."));
        assert_eq!(Err("Invalid segment '' in path 'a//b'".to_string()), decompose_path("a//b"));
        assert_eq!(Err("Invalid segment 'B' in path 'a/B'".to_string()), decompose_path("a/B"));
        assert_eq!(Err("Invalid segment '' in path 'a/'".to_string()), decompose_path("a/"));
        assert!(validate_root_path(&["pack.mcmeta"]).is_ok());
        assert!(validate_root_path(&[]).is_err());
        assert!(is_valid_path("Textures/A.png") && !is_valid_path("DUMMY") && !is_valid_path("a b"));
        assert!(is_valid_namespace("") && is_valid_namespace(".mcassetsroot") && !is_valid_namespace("A"));
        assert_eq!(Ordering::Less, compare_locations(("z", "a"), ("a", "b")));
        assert_eq!(Ordering::Greater, compare_locations(("z", "a"), ("a", "a")));
    }
}

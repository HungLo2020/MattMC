use std::path::{Component, Path, PathBuf};

use super::errors::{PackError, PackResult};

pub const PACK_TYPES: [&str; 2] = ["assets", "data"];

pub fn validate_pack_type(pack_type: &str) -> PackResult<&str> {
    match pack_type {
        "assets" | "data" => Ok(pack_type),
        _ => Err(PackError::invalid_argument(format!(
            "unsupported pack type root '{pack_type}'"
        ))),
    }
}

pub fn is_valid_namespace(namespace: &str) -> bool {
    !namespace.is_empty()
        && namespace.bytes().all(|b| {
            b == b'_' || b == b'-' || b == b'.' || b.is_ascii_lowercase() || b.is_ascii_digit()
        })
}

pub fn validate_namespace(namespace: &str) -> PackResult<&str> {
    if is_valid_namespace(namespace) {
        Ok(namespace)
    } else {
        Err(PackError::invalid_path(format!(
            "invalid resource namespace '{namespace}'"
        )))
    }
}

pub fn is_resource_location_path(path: &str) -> bool {
    !path.is_empty()
        && path != "DUMMY"
        && path.bytes().all(|b| {
            b == b'_'
                || b == b'-'
                || b == b'.'
                || b == b'/'
                || b.is_ascii_lowercase()
                || b.is_ascii_uppercase()
                || b.is_ascii_digit()
        })
}

pub fn validate_resource_location_path(path: &str) -> PackResult<&str> {
    if is_resource_location_path(path) {
        Ok(path)
    } else {
        Err(PackError::invalid_path(format!(
            "invalid resource path '{path}'"
        )))
    }
}

pub fn decompose_strict_path(path: &str) -> PackResult<Vec<&str>> {
    let path = validate_resource_location_path(path)?;
    let mut parts = Vec::new();
    for part in path.split('/') {
        if part.is_empty() || part == "." || part == ".." {
            return Err(PackError::invalid_path(format!(
                "invalid path segment '{part}' in '{path}'"
            )));
        }
        if !part.bytes().all(|b| {
            b == b'_' || b == b'-' || b == b'.' || b.is_ascii_lowercase() || b.is_ascii_digit()
        }) {
            return Err(PackError::invalid_path(format!(
                "invalid strict path segment '{part}' in '{path}'"
            )));
        }
        parts.push(part);
    }
    Ok(parts)
}

pub fn safe_zip_path(path: &str) -> PackResult<&str> {
    let path = validate_resource_location_path(path)?;
    for part in path.split('/') {
        if part.is_empty() || part == "." || part == ".." {
            return Err(PackError::invalid_path(format!(
                "unsafe zip path segment '{part}' in '{path}'"
            )));
        }
    }
    if Path::new(path).is_absolute() || path.contains('\\') {
        return Err(PackError::invalid_path(format!("unsafe zip path '{path}'")));
    }
    Ok(path)
}

pub fn join_strict(root: &Path, path: &str) -> PackResult<PathBuf> {
    let parts = decompose_strict_path(path)?;
    let mut out = root.to_path_buf();
    for part in parts {
        out.push(part);
    }
    Ok(out)
}

pub fn relative_slash_path(path: &Path) -> Option<String> {
    let mut parts = Vec::new();
    for component in path.components() {
        match component {
            Component::Normal(part) => parts.push(part.to_string_lossy().replace('\\', "/")),
            _ => return None,
        }
    }
    if parts.is_empty() {
        None
    } else {
        Some(parts.join("/"))
    }
}

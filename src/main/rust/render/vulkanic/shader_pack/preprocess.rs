use std::collections::BTreeSet;
use std::path::Path;

use crate::render::vulkanic::error::{GalError, GalResult};

use super::source::ShaderPackSource;

pub struct PreprocessInput<'a> {
    pub source: &'a ShaderPackSource,
    pub entry: &'a str,
    pub defines: &'a [(&'a str, &'a str)],
}

pub fn preprocess(input: PreprocessInput<'_>) -> GalResult<String> {
    let mut out = String::new();
    out.push_str(&format!("// shader-pack: {}\n", input.source.name()));
    out.push_str(&format!("// generation: {}\n", input.source.generation()));
    for (key, value) in input.defines {
        validate_define(key, value)?;
        out.push_str("#define ");
        out.push_str(key);
        out.push(' ');
        out.push_str(value);
        out.push('\n');
    }
    expand_file(input.source, input.entry, &mut BTreeSet::new(), &mut out)?;
    Ok(out)
}

fn expand_file(
    source: &ShaderPackSource,
    path: &str,
    stack: &mut BTreeSet<String>,
    out: &mut String,
) -> GalResult<()> {
    let path = normalize_path(path);
    if !stack.insert(path.clone()) {
        return Err(GalError::invalid_argument(format!(
            "cyclic shader include {path}"
        )));
    }
    let contents = source
        .get(&path)
        .ok_or_else(|| GalError::invalid_argument(format!("missing shader source {path}")))?;
    for line in contents.lines() {
        if let Some(include) = parse_include(line) {
            let include = resolve_include(&path, include);
            expand_file(source, &include, stack, out)?;
        } else {
            out.push_str(line);
            out.push('\n');
        }
    }
    stack.remove(&path);
    Ok(())
}

fn parse_include(line: &str) -> Option<&str> {
    let trimmed = line.trim();
    let rest = trimmed.strip_prefix("#include")?.trim();
    rest.strip_prefix('"')
        .and_then(|include| include.strip_suffix('"'))
        .or_else(|| {
            rest.strip_prefix('<')
                .and_then(|include| include.strip_suffix('>'))
        })
}

fn normalize_path(path: &str) -> String {
    path.trim_start_matches('/').replace('\\', "/")
}

fn resolve_include(parent: &str, include: &str) -> String {
    if include.starts_with('/') {
        return normalize_path(include);
    }
    let parent = Path::new(parent);
    parent
        .parent()
        .unwrap_or_else(|| Path::new(""))
        .join(include)
        .to_string_lossy()
        .replace('\\', "/")
}

fn validate_define(key: &str, value: &str) -> GalResult<()> {
    if key.is_empty()
        || !key
            .bytes()
            .all(|byte| byte == b'_' || byte.is_ascii_alphanumeric())
    {
        return Err(GalError::invalid_argument("invalid shader define key"));
    }
    if value.contains('\n') || value.contains('\r') {
        return Err(GalError::invalid_argument("invalid shader define value"));
    }
    Ok(())
}

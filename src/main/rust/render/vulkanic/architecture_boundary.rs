use std::fs;
use std::path::{Path, PathBuf};

const RUST_ROOT: &str = env!("CARGO_MANIFEST_DIR");
const ASH_TOKEN: &str = "ash";
const GLOW_TOKEN: &str = "glow";
const SHADERC_TOKEN: &str = "shaderc";

#[test]
fn backends_module_is_private_to_vulkanic() {
    let rust_root = Path::new(RUST_ROOT);
    let vulkanic_mod = rust_root.join("render/vulkanic/mod.rs");
    let backends_mod = rust_root.join("render/vulkanic/backends/mod.rs");

    let vulkanic_source = read_source(&vulkanic_mod);
    let backends_source = read_source(&backends_mod);

    // This guardrail is intentional: Rust backend implementations are private
    // implementation details. Only render::vulkanic may route to them, and all
    // other Rust code must go through Vulkanic frontend modules.
    assert!(
        contains_module_declaration(&vulkanic_source, "mod backends;"),
        "{} must declare `mod backends;` so Rust privacy blocks access from non-Vulkanic modules",
        relative(&vulkanic_mod)
    );
    assert_no_public_backend_exposure(&vulkanic_mod, &vulkanic_source);
    assert_no_public_backend_exposure(&backends_mod, &backends_source);

    assert!(
        contains_module_declaration(&backends_source, "mod opengl;")
            || contains_module_declaration(&backends_source, "pub(super) mod opengl;"),
        "{} must keep the Rust OpenGL backend module present behind the private backend boundary",
        relative(&backends_mod)
    );
    assert!(
        contains_module_declaration(&backends_source, "mod vulkan;")
            || contains_module_declaration(&backends_source, "pub(super) mod vulkan;"),
        "{} must keep the Rust Vulkan backend module present behind the private backend boundary",
        relative(&backends_mod)
    );
}

#[test]
fn non_vulkanic_rust_code_does_not_reference_backend_modules() {
    let rust_root = Path::new(RUST_ROOT);
    let mut violations = Vec::new();

    for file in rust_files(rust_root) {
        if is_inside(&file, &rust_root.join("render/vulkanic")) {
            continue;
        }

        let source = read_source(&file);
        for (line_index, line) in source.lines().enumerate() {
            let compact = compact_line(line);
            if compact.contains("render::vulkanic::backends")
                || compact.contains("crate::render::vulkanic::backends")
            {
                violations.push(format!(
                    "{}:{}: {}",
                    relative(&file),
                    line_index + 1,
                    line.trim()
                ));
            }
        }
    }

    assert!(
        violations.is_empty(),
        "Rust code outside render::vulkanic must not reference backend implementation modules:\n{}",
        violations.join("\n")
    );
}

#[test]
fn backend_specific_crates_stay_inside_their_backend_modules() {
    let rust_root = Path::new(RUST_ROOT);
    let vulkan_backend = rust_root.join("render/vulkanic/backends/vulkan");
    let opengl_backend = rust_root.join("render/vulkanic/backends/opengl");
    let mut violations = Vec::new();

    for file in rust_files(rust_root) {
        let source = read_source(&file);
        for (line_index, line) in source.lines().enumerate() {
            if !is_inside(&file, &vulkan_backend) {
                if line.contains(&format!("{ASH_TOKEN}::"))
                    || line.contains(&format!("{SHADERC_TOKEN}::"))
                {
                    violations.push(format!(
                        "{}:{}: {}",
                        relative(&file),
                        line_index + 1,
                        line.trim()
                    ));
                }
            }

            if !is_inside(&file, &opengl_backend) && line.contains(&format!("{GLOW_TOKEN}::")) {
                violations.push(format!(
                    "{}:{}: {}",
                    relative(&file),
                    line_index + 1,
                    line.trim()
                ));
            }
        }
    }

    assert!(
        violations.is_empty(),
        "Backend-specific Rust graphics dependencies must stay inside their backend modules:\n{}",
        violations.join("\n")
    );
}

#[test]
fn backend_modules_do_not_reference_each_other() {
    let rust_root = Path::new(RUST_ROOT);
    let vulkan_backend = rust_root.join("render/vulkanic/backends/vulkan");
    let opengl_backend = rust_root.join("render/vulkanic/backends/opengl");

    let vulkan_violations = backend_reference_violations(&vulkan_backend, "opengl");
    assert!(
        vulkan_violations.is_empty(),
        "Rust Vulkan backend code must not reference OpenGL backend implementation modules:\n{}",
        vulkan_violations.join("\n")
    );

    let opengl_violations = backend_reference_violations(&opengl_backend, "vulkan");
    assert!(
        opengl_violations.is_empty(),
        "Rust OpenGL backend code must not reference Vulkan backend implementation modules:\n{}",
        opengl_violations.join("\n")
    );
}

fn assert_no_public_backend_exposure(path: &Path, source: &str) {
    let mut violations = Vec::new();

    for (line_index, line) in source.lines().enumerate() {
        let compact = compact_line(line);
        if compact.contains("pubmodbackends;")
            || compact.contains("pub(crate)modbackends;")
            || compact.contains("pub(super)modbackends;")
            || compact.contains("pubusebackends")
            || compact.contains("pubuseself::backends")
            || compact.contains("pub(crate)usebackends")
            || compact.contains("pub(crate)useself::backends")
            || compact.contains("pub(super)usebackends")
            || compact.contains("pub(super)useself::backends")
        {
            violations.push(format!(
                "{}:{}: {}",
                relative(path),
                line_index + 1,
                line.trim()
            ));
        }
    }

    assert!(
        violations.is_empty(),
        "Rust Vulkanic backends are intentionally private and must not be re-exported:\n{}",
        violations.join("\n")
    );
}

fn backend_reference_violations(backend_path: &Path, forbidden_backend: &str) -> Vec<String> {
    let mut violations = Vec::new();

    for file in rust_files(backend_path) {
        let source = read_source(&file);
        for (line_index, line) in source.lines().enumerate() {
            let compact = compact_line(line);
            if compact.contains(&format!("backends::{forbidden_backend}"))
                || compact.contains(&format!("super::{forbidden_backend}"))
                || compact.contains(&format!("render::vulkanic::backends::{forbidden_backend}"))
                || compact.contains(&format!(
                    "crate::render::vulkanic::backends::{forbidden_backend}"
                ))
            {
                violations.push(format!(
                    "{}:{}: {}",
                    relative(&file),
                    line_index + 1,
                    line.trim()
                ));
            }
        }
    }

    violations
}

fn contains_module_declaration(source: &str, declaration: &str) -> bool {
    let expected = compact_line(declaration);
    source.lines().any(|line| compact_line(line) == expected)
}

fn rust_files(root: &Path) -> Vec<PathBuf> {
    let mut files = Vec::new();
    collect_rust_files(root, &mut files);
    files.sort();
    files
}

fn collect_rust_files(path: &Path, files: &mut Vec<PathBuf>) {
    if path.file_name().is_some_and(|name| name == "target") {
        return;
    }

    if path.is_file() {
        if path.extension().is_some_and(|extension| extension == "rs") {
            files.push(path.to_path_buf());
        }
        return;
    }

    for entry in fs::read_dir(path).unwrap_or_else(|error| {
        panic!("failed to read {}: {error}", path.display());
    }) {
        let entry = entry.unwrap_or_else(|error| {
            panic!(
                "failed to read directory entry under {}: {error}",
                path.display()
            );
        });
        collect_rust_files(&entry.path(), files);
    }
}

fn read_source(path: &Path) -> String {
    fs::read_to_string(path).unwrap_or_else(|error| {
        panic!("failed to read {}: {error}", path.display());
    })
}

fn compact_line(line: &str) -> String {
    line.chars()
        .filter(|character| !character.is_whitespace())
        .collect()
}

fn is_inside(path: &Path, parent: &Path) -> bool {
    path.starts_with(parent)
}

fn relative(path: &Path) -> String {
    path.strip_prefix(RUST_ROOT)
        .unwrap_or(path)
        .display()
        .to_string()
}

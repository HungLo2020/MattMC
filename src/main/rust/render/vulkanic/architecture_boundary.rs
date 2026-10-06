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
                if references_module(line, ASH_TOKEN)
                    || references_module(line, SHADERC_TOKEN)
                {
                    violations.push(format!(
                        "{}:{}: {}",
                        relative(&file),
                        line_index + 1,
                        line.trim()
                    ));
                }
            }

            if !is_inside(&file, &opengl_backend) && references_module(line, GLOW_TOKEN) {
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

fn references_module(line: &str, module: &str) -> bool {
    line.match_indices(&format!("{module}::")).any(|(offset, _)| {
        offset == 0 || !line[..offset].chars().next_back()
            .is_some_and(|ch| ch.is_alphanumeric() || ch == '_')
    })
}

#[test]
fn graphics_dependency_scan_matches_complete_module_names() {
    for module in [ASH_TOKEN, SHADERC_TOKEN, GLOW_TOKEN] {
        assert!(references_module(&format!("use {module}::Type;"), module));
        assert!(references_module(&format!("::{module}::Type"), module));
        assert!(references_module(&format!("let value = {module}::function();"), module));
        assert!(!references_module(&format!("use super::position_{module}::mix;"), module));
        assert!(!references_module(&format!("use longer{module}::Type;"), module));
    }
    assert!(!references_module(&format!("use std::{}::Hash;", "hash"), ASH_TOKEN));
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

#[test]
fn bridge_is_split_into_focused_modules() {
    let rust_root = Path::new(RUST_ROOT);
    for removed in ["render/vulkanic/ffi", "render/vulkanic/ffi.rs", "render/bridge.rs"] {
        assert!(
            !rust_root.join(removed).exists(),
            "{removed} must not return; the bridge lives in render/bridge/"
        );
    }
    let bridge = rust_root.join("render/bridge");
    let required = [
        "mod.rs",
        "abi/mod.rs",
        "abi/common.rs",
        "layout.rs",
        "memory.rs",
        "accounting.rs",
        "wire.rs",
        "capabilities.rs",
        "status.rs",
        "context.rs",
        "resources.rs",
        "submission.rs",
        "frame.rs",
        "gui/mod.rs",
        "world/mod.rs",
        "world/mesh_assets.rs",
        "shader_pack.rs",
        "sprite_animation.rs",
        "tests/mod.rs",
    ];
    for file in required {
        let path = bridge.join(file);
        assert!(path.is_file(), "missing focused bridge module {}", relative(&path));
    }
}

#[test]
fn semantic_ffi_modules_do_not_construct_rendering_policy() {
    let rust_root = Path::new(RUST_ROOT);
    let mut checked = rust_files(&rust_root.join("render/bridge/gui"));
    checked.extend(rust_files(&rust_root.join("render/bridge/world")));
    assert!(checked.iter().any(|file| file.ends_with("world/mesh_assets.rs")));
    let forbidden_tokens = [
        "CommandOp::",
        "create_graphics_pipeline",
        "create_pipeline_layout",
        "create_resource_set",
        "create_shader_module",
        "TextureDesc",
        "SamplerDesc",
        "ResourceSetDesc",
        "GraphicsPipelineDesc",
        "atlas_for",
        "build_atlas",
        "material_batches(",
        "crack_batches(",
        "border_batches(",
        "ensure_material_resources",
        "ensure_crack_resources",
        "ensure_border_resources",
    ];
    let mut violations = Vec::new();

    for file in checked {
        let source = read_source(&file);
        for (line_index, line) in source.lines().enumerate() {
            for token in forbidden_tokens {
                if line.contains(token) {
                    violations.push(format!(
                        "{}:{}: {}",
                        relative(&file),
                        line_index + 1,
                        line.trim()
                    ));
                }
            }
        }
    }

    assert!(
        violations.is_empty(),
        "Semantic bridge modules must only decode/copy transport records and call renderers:\n{}",
        violations.join("\n")
    );
}

#[test]
fn ffi_abi_does_not_accumulate_producer_specific_schema_names() {
    let rust_root = Path::new(RUST_ROOT);
    let ffi_dir = rust_root.join("render/bridge");
    let forbidden = [
        "BlockMarker",
        "TerrainParticle",
        "BlockDisplay",
        "BlockSubmit",
        "Armor",
        "Hunger",
        "Absorption",
        "BossBar",
        "Crosshair",
        "Hotbar",
        "ExperienceBar",
    ];
    let mut violations = Vec::new();

    for file in rust_files(&ffi_dir) {
        let source = read_source(&file);
        for (line_index, line) in source.lines().enumerate() {
            for token in forbidden {
                if line.contains(token) {
                    violations.push(format!(
                        "{}:{}: {}",
                        relative(&file),
                        line_index + 1,
                        line.trim()
                    ));
                }
            }
        }
    }

    assert!(
        violations.is_empty(),
        "FFI schemas must extend shared GUI/world/material record families, not named producers:\n{}",
        violations.join("\n")
    );
}

#[test]
fn opengl_backend_does_not_borrow_iris_renderer_internals() {
    let rust_root = Path::new(RUST_ROOT);
    let opengl_backend = rust_root.join("render/vulkanic/backends/opengl");
    let forbidden = [
        "borrowed_iris",
        "uses_borrowed_iris",
        "IrisVertexFormats",
        "IrisRenderSystem",
        "WorldRenderingPhase",
        "GbufferPrograms",
    ];
    let mut violations = Vec::new();

    for file in rust_files(&opengl_backend) {
        let source = read_source(&file);
        for (line_index, line) in source.lines().enumerate() {
            for token in forbidden {
                if line.contains(token) {
                    violations.push(format!(
                        "{}:{}: {}",
                        relative(&file),
                        line_index + 1,
                        line.trim()
                    ));
                }
            }
        }
    }

    assert!(
        violations.is_empty(),
        "Rust OpenGL backend must lower explicit GAL state, not borrow Iris renderer internals:\n{}",
        violations.join("\n")
    );
}

#[test]
fn shader_pack_policy_stays_out_of_ffi_and_backends() {
    let rust_root = Path::new(RUST_ROOT);
    let checked_roots = [
        rust_root.join("render/bridge"),
        rust_root.join("render/vulkanic/backends/opengl"),
        rust_root.join("render/vulkanic/backends/vulkan"),
    ];
    let forbidden = [
        "shaderpack::source::manifest",
        "shaderpack::source::preprocess",
        "shaderpack::plan::pass_graph",
        "ShaderPackManifest",
        "PassGraph",
    ];
    let mut violations = Vec::new();

    // Production code only: backend conformance suites use pack sources as
    // fixtures.
    for root in checked_roots {
        for file in production_files(&root) {
            for (line_number, line) in production_lines(&read_source(&file)) {
                for token in forbidden {
                    if line.contains(token) {
                        violations.push(format!("{}:{}: {}", relative(&file), line_number, line.trim()));
                    }
                }
            }
        }
    }

    assert!(
        violations.is_empty(),
        "Shader-pack policy must stay in render::shaderpack and frontends, not FFI/backends. Versioned source-file transport is allowed in FFI, but parsing and pass policy are not:\n{}",
        violations.join("\n")
    );
}

#[test]
fn shader_pack_runtime_owns_whole_frame_pass_order() {
    let rust_root = Path::new(RUST_ROOT);
    let world_frontend = rust_root.join("render/worldrender");
    let production_source = world_renderer_production_source(rust_root);
    let forbidden = [
        "ShaderPackRuntimePlan::terrain_material_multipass_v1",
        "builtin_terrain_material_pass_graph",
        "vulkanic:pass/shadow_depth",
        "vulkanic:pass/terrain_opaque",
        "vulkanic:pass/terrain_cutout",
        "vulkanic:pass/deferred_lighting",
        "vulkanic:pass/composite_0",
        "vulkanic:pass/composite_1",
        "vulkanic:pass/final_output",
    ];
    let mut violations = Vec::new();

    for (line_index, line) in production_source.lines().enumerate() {
        for token in forbidden {
            if line.contains(token) {
                violations.push(format!(
                    "{}:{}: {}",
                    relative(&world_frontend),
                    line_index + 1,
                    line.trim()
                ));
            }
        }
    }

    assert!(
        violations.is_empty(),
        "Whole-frame shader pass ordering belongs in shaderpack::runtime, not the world frontend:\n{}",
        violations.join("\n")
    );
}

#[test]
fn shader_pack_runtime_owns_private_terrain_volume_resources() {
    let rust_root = Path::new(RUST_ROOT);
    let runtime = rust_root.join("render/shaderpack/runtime/mod.rs");
    let production_world_source = world_renderer_production_source(rust_root);
    let runtime_source = read_source(&runtime);

    assert!(
        !production_world_source.contains("TerrainOccupancyRuntime"),
        "World frontend must not own shader-pack volume resources; use the shader runtime instead"
    );
    assert!(
        runtime_source.contains("terrain_occupancy: Option<TerrainOccupancyRuntime>"),
        "Private terrain volume residency must remain owned by shaderpack::runtime"
    );
}

#[test]
fn source_terrain_execution_is_rust_owned_and_never_an_ffi_or_iris_route() {
    let rust_root = Path::new(RUST_ROOT);
    let ffi_dir = rust_root.join("render/bridge");
    let source = world_renderer_source(rust_root);

    assert!(
        source.contains("#[cfg(test)]\n    candidate_subset_execution_enabled: bool,"),
        "the internal fixture selector must not exist in production builds"
    );
    assert!(
        ["", "pub(crate) ", "pub(super) "].iter().any(|visibility| {
            source.contains(&format!(
                "#[cfg(not(test))]\n    {visibility}fn candidate_subset_programs_for_frame"
            ))
        })
            && source.contains("Ok(None)")
            && source.contains("prepared_lowered_terrain_source_program(TerrainMaterialProgramKind::Opaque)")
            && source.contains("prepared_lowered_shadow_source_program()?"),
        "production must keep the fixture selector unavailable while admitting only explicitly lowered source programs"
    );
    assert!(
        source.contains("MATTMC_RUST_SELECTED_SOURCE_EXECUTION")
            && source.contains("self.shader_pack_sources\n            .active()")
            && source.contains("fn source_execution_enabled(&self)")
            && source.contains("source_execution_armed: bool")
            && source.contains("arm_runtime_source_execution_if_ready")
            && source.contains("prepare_runtime_source_snapshot"),
        "selected-source execution must remain Rust-owned, explicitly disableable for isolation, and armed only after coherent Rust preparation"
    );
    for file in rust_files(&ffi_dir) {
        assert!(
            !read_source(&file).contains("source_terrain_execution"),
            "FFI must not expose the private source terrain selector: {}",
            relative(&file)
        );
    }
}

#[test]
fn backend_identity_is_not_part_of_the_gal_api() {
    // Frontends select behavior through capability facts (feature flags,
    // limits, shader conventions), never by asking which backend is running.
    let rust_root = Path::new(RUST_ROOT);
    let mut violations = Vec::new();
    for file in rust_files(rust_root) {
        if file.ends_with("architecture_boundary.rs") {
            continue;
        }
        for (line_index, line) in read_source(&file).lines().enumerate() {
            if line.contains("BackendApi") {
                violations.push(format!("{}:{}: {}", relative(&file), line_index + 1, line.trim()));
            }
        }
    }
    let resources = rust_root.join("render/vulkanic/resources.rs");
    let source = read_source(&resources);
    let capabilities = source
        .split("pub struct BackendCapabilities {")
        .nth(1)
        .and_then(|rest| rest.split('}').next())
        .expect("BackendCapabilities is declared in resources.rs");
    for field in capabilities.lines().map(str::trim).filter(|line| line.starts_with("pub ")) {
        if field.starts_with("pub api") || field.starts_with("pub backend") || field.starts_with("pub kind") {
            violations.push(format!("{}: BackendCapabilities field `{field}`", relative(&resources)));
        }
    }
    assert!(
        violations.is_empty(),
        "Backend identity must not be exposed to or tested by frontends; add a capability instead:\n{}",
        violations.join("\n")
    );
}

#[test]
fn frontend_production_code_does_not_name_backend_implementations() {
    let rust_root = Path::new(RUST_ROOT);
    let backends = rust_root.join("render/vulkanic/backends");
    let forbidden = [
        "backends::vulkan",
        "backends::opengl",
        "VulkanBackend",
        "OpenGlBackend",
        "\"Rust Vulkan\"",
        "\"Rust OpenGL\"",
        "capabilities().name ==",
    ];
    let mut violations = Vec::new();
    for file in production_files(&rust_root.join("render")) {
        if is_inside(&file, &backends) {
            continue;
        }
        // Backends are chosen through `vulkanic::create`; nothing names them.
        for (line_number, line) in production_lines(&read_source(&file)) {
            for token in forbidden {
                if line.contains(token) {
                    violations.push(format!("{}:{}: {}", relative(&file), line_number, line.trim()));
                }
            }
        }
    }
    assert!(
        violations.is_empty(),
        "Frontend/GAL production code must reach backends only through the GAL:\n{}",
        violations.join("\n")
    );
}

#[test]
fn core_gal_and_backends_do_not_depend_on_frontends() {
    let rust_root = Path::new(RUST_ROOT);
    let forbidden = [
        "world_primitive_frontend",
        "gui_frontend",
        "gui_mesh_frontend",
        "worldrender",
        "guirender",
        "shaderpack",
        "render::scene",
        "render::shared",
        "render::bridge",
        "vulkanic::terrain",
        "vulkanic::ffi",
        "super::ffi",
    ];
    let mut violations = Vec::new();
    for file in core_and_backend_production_files(rust_root) {
        for (line_number, line) in production_lines(&read_source(&file)) {
            for token in forbidden {
                if line.contains(token) {
                    violations.push(format!("{}:{}: {}", relative(&file), line_number, line.trim()));
                }
            }
        }
    }
    assert!(
        violations.is_empty(),
        "The core GAL and its backends must not depend on rendering frontends:\n{}",
        violations.join("\n")
    );
}

#[test]
fn core_gal_and_backends_carry_no_game_vocabulary() {
    // Game concepts (content, render features, shader-pack names) belong to
    // frontends. The GAL and backends see opaque scopes, labels and states.
    let rust_root = Path::new(RUST_ROOT);
    let forbidden_words = [
        "minecraft", "terrain", "iris", "optifine", "lightmap", "entity", "entities", "glint",
        "vignette", "crumbling", "particle", "particles", "gbuffer", "dh", "voxel", "sky",
        "weather", "gui", "hud",
    ];
    let forbidden_phrases = [
        ["distant", "horizons"],
        ["shadow", "depth"],
        ["g", "buffer"],
        ["shader", "pack"],
        ["world", "lod"],
        ["world", "mesh"],
    ];
    let mut violations = Vec::new();
    for file in core_and_backend_production_files(rust_root) {
        for (line_number, line) in production_lines(&read_source(&file)) {
            let words = identifier_words(&line);
            let hit = words.iter().any(|word| forbidden_words.contains(&word.as_str()))
                || words.windows(2).any(|pair| {
                    forbidden_phrases
                        .iter()
                        .any(|phrase| pair[0] == phrase[0] && pair[1] == phrase[1])
                });
            if hit {
                violations.push(format!("{}:{}: {}", relative(&file), line_number, line.trim()));
            }
        }
    }
    assert!(
        violations.is_empty(),
        "Game vocabulary must stay in frontends, not the core GAL or backends:\n{}",
        violations.join("\n")
    );
}

#[test]
fn identifier_words_split_snake_kebab_and_camel_case() {
    assert_eq!(
        vec!["egl", "int", "binding", "buffer", "distant", "horizons", "lod", "frame", "gl", "vertex", "id"],
        identifier_words("EglInt binding_buffer distant-horizons LodFrame gl_VertexID")
    );
}

/// Lowercase words of every identifier or literal on a line, split at
/// `_`, `-`, `.`, spaces and camelCase boundaries (`GLCapsTest` → gl caps test).
fn identifier_words(line: &str) -> Vec<String> {
    let mut words = Vec::new();
    for chunk in line.split(|character: char| !character.is_ascii_alphanumeric()) {
        let characters: Vec<char> = chunk.chars().collect();
        let mut word = String::new();
        for (index, character) in characters.iter().enumerate() {
            let previous = index.checked_sub(1).map(|index| characters[index]);
            let next = characters.get(index + 1);
            let boundary = character.is_ascii_uppercase()
                && previous.is_some_and(|previous| {
                    previous.is_ascii_lowercase()
                        || (previous.is_ascii_uppercase()
                            && next.is_some_and(|next| next.is_ascii_lowercase()))
                });
            if boundary && !word.is_empty() {
                words.push(std::mem::take(&mut word));
            }
            word.push(character.to_ascii_lowercase());
        }
        if !word.is_empty() {
            words.push(word);
        }
    }
    words
}

#[test]
fn core_gal_and_backends_do_not_branch_on_resource_labels() {
    // Labels are diagnostics. Behavior keyed on label text (profiling
    // classification, feature detection) belongs to the frontend that chose
    // the label; env-selected trace filters take the text from outside.
    let rust_root = Path::new(RUST_ROOT);
    let forbidden = [
        "label.contains(\"",
        "label.starts_with(\"",
        "label.ends_with(\"",
        "label == \"",
    ];
    let mut violations = Vec::new();
    for file in core_and_backend_production_files(rust_root) {
        for (line_number, line) in production_lines(&read_source(&file)) {
            let compact = compact_line(&line);
            for token in forbidden {
                if compact.contains(&compact_line(token)) {
                    violations.push(format!("{}:{}: {}", relative(&file), line_number, line.trim()));
                }
            }
        }
    }
    assert!(
        violations.is_empty(),
        "The core GAL and backends must not change behavior based on resource label text:\n{}",
        violations.join("\n")
    );
}

/// GAL modules that code outside `render::vulkanic` may use.
const PUBLIC_GAL_MODULES: [&str; 9] =
    ["gal", "create", "resources", "commands", "handles", "error", "frame", "sync", "metrics"];

/// `render::vulkanic::X` modules named on a line, excluding the public GAL
/// modules and the items the GAL re-exports at its root (`GalError`, ...).
fn non_public_gal_references(line: &str) -> Vec<String> {
    let mut modules = Vec::new();
    for (index, token) in line.match_indices("render::vulkanic::") {
        let rest = &line[index + token.len()..];
        let segments: Vec<String> = if let Some(group) = rest.strip_prefix('{') {
            group
                .split('}')
                .next()
                .unwrap_or("")
                .split(',')
                .map(|member| member.trim().split("::").next().unwrap_or("").to_string())
                .collect()
        } else {
            vec![rest
                .chars()
                .take_while(|character| character.is_ascii_alphanumeric() || *character == '_')
                .collect()]
        };
        for segment in segments {
            let root_item = segment.chars().next().is_some_and(|c| c.is_ascii_uppercase());
            if !segment.is_empty() && !root_item && !PUBLIC_GAL_MODULES.contains(&segment.as_str()) {
                modules.push(segment);
            }
        }
    }
    modules
}

#[test]
fn shaderpack_uses_only_the_scene_and_the_public_gal() {
    let rust_root = Path::new(RUST_ROOT);
    let shaderpack = rust_root.join("render/shaderpack");
    assert!(
        !rust_root.join("render/vulkanic/shader_pack").exists(),
        "the shader pack lives in render/shaderpack, not inside the GAL"
    );
    let mut violations = Vec::new();
    for file in production_files(&shaderpack) {
        for (line_number, line) in production_lines(&read_source(&file)) {
            let names_renderer = ["worldrender", "guirender", "bridge"]
                .iter()
                .any(|layer| line.contains(&format!("render::{layer}")));
            if !non_public_gal_references(&line).is_empty() || names_renderer {
                violations.push(format!("{}:{}: {}", relative(&file), line_number, line.trim()));
            }
        }
    }
    assert!(
        violations.is_empty(),
        "render::shaderpack may depend on render::scene and the public GAL modules {PUBLIC_GAL_MODULES:?} only:\n{}",
        violations.join("\n")
    );
}

#[test]
fn worldrender_uses_the_public_gal_and_lower_layers_only() {
    let rust_root = Path::new(RUST_ROOT);
    for removed in ["render/vulkanic/world_primitive_frontend.rs", "render/vulkanic/world_primitive_frontend", "render/vulkanic/terrain"] {
        assert!(
            !rust_root.join(removed).exists(),
            "{removed} moved to render/worldrender and must not return"
        );
    }
    let mut violations = Vec::new();
    for file in production_files(&rust_root.join("render/worldrender")) {
        for (line_number, line) in production_lines(&read_source(&file)) {
            // The whole-frame submit composes the GUI over the world, so the
            // world renderer may call into `guirender`; the reverse is forbidden.
            if !non_public_gal_references(&line).is_empty() || line.contains("render::bridge") {
                violations.push(format!("{}:{}: {}", relative(&file), line_number, line.trim()));
            }
        }
    }
    assert!(
        violations.is_empty(),
        "render::worldrender may use render::{{scene, shared, shaderpack, guirender}} and the public GAL modules {PUBLIC_GAL_MODULES:?}:\n{}",
        violations.join("\n")
    );
}

#[test]
fn guirender_uses_the_public_gal_and_lower_layers_only() {
    let rust_root = Path::new(RUST_ROOT);
    for removed in [
        "gui_frontend.rs",
        "gui_frontend",
        "gui_mesh_frontend.rs",
        "gui_atlas_reference.rs",
        "gui_item_layout.rs",
        "gui_item_material.rs",
        "gui_item_raster.rs",
        "gui_item_raster_gpu_tests.rs",
        "gui_tiling.rs",
        "fixtures",
    ] {
        assert!(
            !rust_root.join("render/vulkanic").join(removed).exists(),
            "render/vulkanic/{removed} moved to render/guirender and must not return"
        );
    }
    let mut violations = Vec::new();
    for file in production_files(&rust_root.join("render/guirender")) {
        for (line_number, line) in production_lines(&read_source(&file)) {
            // World-owned atlases reach the GUI only through `GuiAtlasOwner`.
            let names_upper_layer = ["worldrender", "bridge"]
                .iter()
                .any(|layer| line.contains(&format!("render::{layer}")))
                || line.contains("WorldPrimitiveFrontend");
            if !non_public_gal_references(&line).is_empty() || names_upper_layer {
                violations.push(format!("{}:{}: {}", relative(&file), line_number, line.trim()));
            }
        }
    }
    assert!(
        violations.is_empty(),
        "render::guirender may use render::{{scene, shared, shaderpack}} and the public GAL modules {PUBLIC_GAL_MODULES:?}:\n{}",
        violations.join("\n")
    );
}

#[test]
fn bridge_is_the_composition_root_over_the_public_gal() {
    let rust_root = Path::new(RUST_ROOT);
    let mut violations = Vec::new();
    // The bridge reaches the GAL through its public modules only.
    for file in production_files(&rust_root.join("render/bridge")) {
        for (line_number, line) in production_lines(&read_source(&file)) {
            if !non_public_gal_references(&line).is_empty() {
                violations.push(format!("{}:{}: {}", relative(&file), line_number, line.trim()));
            }
        }
    }
    // Only the bridge creates GALs and chooses their backend.
    let creation = [
        "BackendChoice",
        "NativeWindow",
        "VulkanicGal::create",
        "set_gpu_timestamps_requested",
    ];
    let render = rust_root.join("render");
    for file in production_files(&render) {
        if is_inside(&file, &render.join("bridge")) || is_inside(&file, &render.join("vulkanic")) {
            continue;
        }
        for (line_number, line) in production_lines(&read_source(&file)) {
            if creation.iter().any(|token| line.contains(token)) {
                violations.push(format!("{}:{}: {}", relative(&file), line_number, line.trim()));
            }
        }
    }
    assert!(
        violations.is_empty(),
        "render::bridge uses the public GAL modules {PUBLIC_GAL_MODULES:?}, and only it creates GALs:\n{}",
        violations.join("\n")
    );
}

#[test]
fn public_gal_api_stays_documented() {
    // Every public GAL item is documented; the lint keeps new ones that way.
    let source = read_source(&Path::new(RUST_ROOT).join("render/vulkanic/mod.rs"));
    assert!(
        source.lines().any(|line| line.trim() == "#![warn(missing_docs)]"),
        "render/vulkanic/mod.rs must keep #![warn(missing_docs)] so the public GAL API stays documented"
    );
}

#[test]
fn shared_helpers_depend_only_on_the_public_gal() {
    let rust_root = Path::new(RUST_ROOT);
    let mut violations = Vec::new();
    for file in production_files(&rust_root.join("render/shared")) {
        for (line_number, line) in production_lines(&read_source(&file)) {
            let names_renderer = ["worldrender", "guirender", "shaderpack", "bridge"]
                .iter()
                .any(|layer| line.contains(&format!("render::{layer}")));
            if !non_public_gal_references(&line).is_empty() || names_renderer {
                violations.push(format!("{}:{}: {}", relative(&file), line_number, line.trim()));
            }
        }
    }
    assert!(
        violations.is_empty(),
        "render::shared sits below the renderers and may use the public GAL only:\n{}",
        violations.join("\n")
    );
}

#[test]
fn scene_is_data_without_rendering_dependencies() {
    let rust_root = Path::new(RUST_ROOT);
    let mut violations = Vec::new();
    for file in production_files(&rust_root.join("render/scene")) {
        for (line_number, line) in production_lines(&read_source(&file)) {
            let references_gal_objects = line.contains("render::vulkanic::")
                && !line.contains("render::vulkanic::resources::");
            let references_renderers = ["shaderpack", "worldrender", "guirender", "bridge"]
                .iter()
                .any(|layer| line.contains(&format!("render::{layer}")));
            if references_gal_objects || references_renderers {
                violations.push(format!("{}:{}: {}", relative(&file), line_number, line.trim()));
            }
        }
    }
    assert!(
        violations.is_empty(),
        "render::scene holds data and wire vocabulary only (GAL resource value types allowed):\n{}",
        violations.join("\n")
    );
}

#[test]
fn production_line_filter_drops_comments_and_test_items() {
    let source = "fn keep() {}\n// terrain comment\n#[cfg(test)]\nmod tests {\n    fn x() { let _ = \"}\"; }\n}\nfn after() {} // tail\n#[cfg(test)]\nmod more;\n";
    let lines: Vec<(usize, String)> = production_lines(source);
    let text: Vec<&str> = lines.iter().map(|(_, line)| line.trim()).filter(|line| !line.is_empty()).collect();
    assert_eq!(vec!["fn keep() {}", "fn after() {}"], text);
    assert_eq!(7, lines.iter().find(|(_, line)| line.contains("after")).unwrap().0);
}

/// Concatenated source of every file of the world renderer.
fn world_renderer_source(rust_root: &Path) -> String {
    rust_files(&rust_root.join("render/worldrender"))
        .iter()
        .map(|file| read_source(file))
        .collect::<Vec<_>>()
        .join("\n")
}

/// Concatenated production code (no comments or test items) of the world
/// renderer.
fn world_renderer_production_source(rust_root: &Path) -> String {
    production_files(&rust_root.join("render/worldrender"))
        .iter()
        .flat_map(|file| production_lines(&read_source(file)).into_iter().map(|(_, line)| line))
        .collect::<Vec<_>>()
        .join("\n")
}

/// Every production file of the GAL: its core modules and its backends.
fn core_and_backend_production_files(rust_root: &Path) -> Vec<PathBuf> {
    let files = production_files(&rust_root.join("render/vulkanic"));
    assert!(files.iter().any(|file| file.ends_with("vulkanic/buffer_upload_capture.rs")));
    files
}

/// Rust files that are not test-only suites (by the repository's naming).
fn production_files(root: &Path) -> Vec<PathBuf> {
    rust_files(root)
        .into_iter()
        .filter(|file| {
            let name = file.file_name().and_then(|name| name.to_str()).unwrap_or_default();
            let in_tests_dir = file.components().any(|part| part.as_os_str() == "tests");
            !in_tests_dir
                && name != "tests.rs"
                && name != "test_support.rs"
                && name != "architecture_boundary.rs"
                && !name.ends_with("_tests.rs")
                && !name.contains("conformance")
                && name != "terrain_interpolation_observation.rs"
        })
        .collect()
}

/// Production source lines with `//` comments and `#[cfg(test)]` items
/// removed, paired with their 1-based line numbers.
fn production_lines(source: &str) -> Vec<(usize, String)> {
    let bytes = source.as_bytes();
    let mut keep = vec![true; bytes.len()];
    let marker = b"#[cfg(test)]";
    let mut index = 0;
    while index + marker.len() <= bytes.len() {
        if &bytes[index..index + marker.len()] != marker {
            index += 1;
            continue;
        }
        let start = index;
        let mut cursor = index + marker.len();
        let mut depth = 0usize;
        let mut end = bytes.len();
        while cursor < bytes.len() {
            match bytes[cursor] {
                b'"' => cursor = skip_string(bytes, cursor),
                b'/' if bytes.get(cursor + 1) == Some(&b'/') => {
                    while cursor < bytes.len() && bytes[cursor] != b'\n' {
                        cursor += 1;
                    }
                }
                b';' if depth == 0 => {
                    end = cursor + 1;
                    break;
                }
                b'{' => depth += 1,
                b'}' => {
                    depth = depth.saturating_sub(1);
                    if depth == 0 {
                        end = cursor + 1;
                        break;
                    }
                }
                _ => {}
            }
            cursor += 1;
        }
        for slot in keep.iter_mut().take(end).skip(start) {
            *slot = false;
        }
        index = end;
    }
    let mut kept = String::with_capacity(source.len());
    for (offset, character) in source.char_indices() {
        kept.push(if keep[offset] || character == '\n' { character } else { ' ' });
    }
    kept.lines()
        .enumerate()
        .map(|(line_index, line)| (line_index + 1, strip_line_comment(line).to_string()))
        .collect()
}

fn skip_string(bytes: &[u8], open: usize) -> usize {
    let mut cursor = open + 1;
    while cursor < bytes.len() {
        match bytes[cursor] {
            b'\\' => cursor += 2,
            b'"' => return cursor,
            _ => cursor += 1,
        }
    }
    cursor
}

fn strip_line_comment(line: &str) -> &str {
    let mut in_string = false;
    let mut previous = '\0';
    for (offset, character) in line.char_indices() {
        if character == '"' && previous != '\\' {
            in_string = !in_string;
        }
        if !in_string && character == '/' && line[offset + 1..].starts_with('/') {
            return &line[..offset];
        }
        previous = character;
    }
    line
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

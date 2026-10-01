//! GLSL sources of the GUI mesh, panorama and composite programs.

use super::*;

pub(super) const GUI_MESH_VERTEX_SHADER_OPENGL: &[u8] = include_bytes!("glsl/mesh_vertex_opengl.glsl");

pub(super) const GUI_MESH_VERTEX_SHADER_VULKAN: &[u8] = include_bytes!("glsl/mesh_vertex_vulkan.glsl");

pub(super) const GUI_MESH_FRAGMENT_SHADER_OPENGL: &[u8] = include_bytes!("glsl/mesh_fragment_opengl.glsl");

pub(super) const GUI_MESH_FRAGMENT_SHADER_VULKAN: &[u8] = include_bytes!("glsl/mesh_fragment_vulkan.glsl");

// The title panorama is semantic background imagery, not a copied 3D item.
// It deliberately shares the bounded mesh stream and Rust-owned image cache
// with GUI meshes, but must not inherit the item alpha-cutoff or directional
// lighting policy.  In particular, a cube-face edge with a transparent texel
// must not punch a hole into the title background.
pub(super) const GUI_PANORAMA_VERTEX_SHADER_OPENGL: &[u8] = include_bytes!("glsl/panorama_vertex_opengl.glsl");

pub(super) const GUI_PANORAMA_VERTEX_SHADER_VULKAN: &[u8] = include_bytes!("glsl/panorama_vertex_vulkan.glsl");

pub(super) const GUI_PANORAMA_FRAGMENT_SHADER_OPENGL: &[u8] = include_bytes!("glsl/panorama_fragment_opengl.glsl");

pub(super) const GUI_PANORAMA_FRAGMENT_SHADER_VULKAN: &[u8] = include_bytes!("glsl/panorama_fragment_vulkan.glsl");

pub(super) const GUI_MESH_COMPOSITE_VERTEX_SHADER_OPENGL: &[u8] = include_bytes!("glsl/mesh_composite_vertex_opengl.glsl");

pub(super) const GUI_MESH_COMPOSITE_VERTEX_SHADER_VULKAN: &[u8] = include_bytes!("glsl/mesh_composite_vertex_vulkan.glsl");

pub(super) const GUI_MESH_COMPOSITE_FRAGMENT_SHADER_OPENGL: &[u8] = include_bytes!("glsl/mesh_composite_fragment_opengl.glsl");

pub(super) const GUI_MESH_COMPOSITE_FRAGMENT_SHADER_VULKAN: &[u8] = include_bytes!("glsl/mesh_composite_fragment_vulkan.glsl");

#[cfg(test)]
pub(crate) fn vulkan_shader_sources_for_backend_test() -> (&'static str, &'static str) {
    vulkan_shader_sources_for_material(GuiMeshMaterialMode::Opaque)
}

#[cfg(test)]
pub(super) fn vulkan_shader_sources_for_material(
    material_mode: GuiMeshMaterialMode,
) -> (&'static str, &'static str) {
    let (vertex, fragment) = gui_mesh_shader_sources(GlslDialect::ExplicitBindings, material_mode);
    (
        std::str::from_utf8(vertex).expect("GUI mesh Vulkan vertex source is UTF-8"),
        std::str::from_utf8(fragment).expect("GUI mesh Vulkan fragment source is UTF-8"),
    )
}

#[cfg(test)]
pub(crate) fn vulkan_panorama_shader_sources_for_backend_test() -> (&'static str, &'static str) {
    vulkan_shader_sources_for_material(GuiMeshMaterialMode::Panorama)
}

#[cfg(test)]
pub(crate) fn opengl_panorama_shader_sources_for_backend_test() -> (&'static str, &'static str) {
    let (vertex, fragment) =
        gui_mesh_shader_sources(GlslDialect::CoreProfile, GuiMeshMaterialMode::Panorama);
    (
        std::str::from_utf8(vertex).expect("GUI panorama OpenGL vertex source is UTF-8"),
        std::str::from_utf8(fragment).expect("GUI panorama OpenGL fragment source is UTF-8"),
    )
}

pub(super) fn gui_mesh_shader_sources(
    dialect: GlslDialect,
    material_mode: GuiMeshMaterialMode,
) -> (&'static [u8], &'static [u8]) {
    let fragment_is_panorama = material_mode == GuiMeshMaterialMode::Panorama;
    match dialect {
        GlslDialect::CoreProfile => (
            if fragment_is_panorama {
                GUI_PANORAMA_VERTEX_SHADER_OPENGL
            } else {
                GUI_MESH_VERTEX_SHADER_OPENGL
            },
            if fragment_is_panorama {
                GUI_PANORAMA_FRAGMENT_SHADER_OPENGL
            } else {
                GUI_MESH_FRAGMENT_SHADER_OPENGL
            },
        ),
        GlslDialect::ExplicitBindings => (
            if fragment_is_panorama {
                GUI_PANORAMA_VERTEX_SHADER_VULKAN
            } else {
                GUI_MESH_VERTEX_SHADER_VULKAN
            },
            if fragment_is_panorama {
                GUI_PANORAMA_FRAGMENT_SHADER_VULKAN
            } else {
                GUI_MESH_FRAGMENT_SHADER_VULKAN
            },
        ),
    }
}

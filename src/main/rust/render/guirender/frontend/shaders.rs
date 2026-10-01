//! GLSL sources of the GUI sprite, blur and post-effect programs.

pub(super) const VERTEX_SHADER_OPENGL: &[u8] = include_bytes!("glsl/sprite_vertex_opengl.glsl");

pub(super) const VERTEX_SHADER_VULKAN: &[u8] = include_bytes!("glsl/sprite_vertex_vulkan.glsl");

pub(super) const FRAGMENT_SHADER_OPENGL: &[u8] = include_bytes!("glsl/sprite_fragment_opengl.glsl");

pub(super) const FRAGMENT_SHADER_VULKAN: &[u8] = include_bytes!("glsl/sprite_fragment_vulkan.glsl");

pub(super) const BLUR_VERTEX_SHADER_VULKAN: &[u8] = include_bytes!("glsl/blur_vertex_vulkan.glsl");

pub(super) const BLUR_FRAGMENT_SHADER_VULKAN: &[u8] = include_bytes!("glsl/blur_fragment_vulkan.glsl");

pub(super) const INVERT_FRAGMENT_SHADER_VULKAN: &[u8] = include_bytes!("glsl/invert_fragment_vulkan.glsl");

pub(super) const CREEPER_COLOR_FRAGMENT_SHADER_VULKAN: &[u8] = include_bytes!("glsl/creeper_color_fragment_vulkan.glsl");

pub(super) const CREEPER_BITS_FRAGMENT_SHADER_VULKAN: &[u8] = include_bytes!("glsl/creeper_bits_fragment_vulkan.glsl");

pub(super) const SPIDER_BOX_BLUR_FRAGMENT_SHADER_VULKAN: &[u8] = include_bytes!("glsl/spider_box_blur_fragment_vulkan.glsl");

pub(super) const SPIDER_ROT_SCALE_VERTEX_SHADER_VULKAN: &[u8] = include_bytes!("glsl/spider_rot_scale_vertex_vulkan.glsl");

pub(super) const SPIDER_CLIP_FRAGMENT_SHADER_VULKAN: &[u8] = include_bytes!("glsl/spider_clip_fragment_vulkan.glsl");

pub(super) const SPIDER_BLIT_FRAGMENT_SHADER_VULKAN: &[u8] = include_bytes!("glsl/spider_blit_fragment_vulkan.glsl");

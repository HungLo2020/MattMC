// This module is intentionally private to net::vulkanic. Rust backend
// implementations must stay behind the Vulkanic frontend boundary, matching the
// Java rule that non-Vulkanic code cannot import net.vulkanic.backends.*.
mod opengl;
mod vulkan;

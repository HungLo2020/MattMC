// Stub OpenGL backend module for the Rust Vulkanic backend tree. It is
// intentionally private; Rust callers outside render::vulkanic must use Vulkanic
// frontend modules instead of depending on backend implementation modules.

#[allow(dead_code)]
pub(super) fn binding_marker() -> u32 {
    glow::ARRAY_BUFFER
}

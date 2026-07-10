pub mod shaderc_spirv_compiler;

#[allow(dead_code)]
pub(super) fn binding_marker_size() -> usize {
    std::mem::size_of::<ash::vk::ApplicationInfo<'static>>()
}

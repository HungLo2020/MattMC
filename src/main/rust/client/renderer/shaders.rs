use std::sync::Arc;
use vulkano::device::Device;
use vulkano::shader::{ShaderModule, ShaderModuleCreateInfo};
use vulkano::shader::spirv;

pub mod vertex_shader {
    use super::*;

    /// Push constants for the vertex shader
    #[repr(C)]
    #[derive(Clone, Copy, Debug, bytemuck::Pod, bytemuck::Zeroable)]
    pub struct PushConstants {
        pub mvp: [[f32; 4]; 4],
    }

    /// Load the pre-compiled vertex shader
    pub fn load(device: Arc<Device>) -> Result<Arc<ShaderModule>, vulkano::Validated<vulkano::VulkanError>> {
        // Load pre-compiled SPIR-V shader
        let spirv_bytes = include_bytes!("../../../../../shaders/compiled/vertex.spv");
        let spirv_words = spirv::bytes_to_words(spirv_bytes).unwrap();
        let create_info = ShaderModuleCreateInfo::new(&spirv_words);
        unsafe { ShaderModule::new(device, create_info) }
    }
}

pub mod fragment_shader {
    use super::*;

    /// Load the pre-compiled fragment shader
    pub fn load(device: Arc<Device>) -> Result<Arc<ShaderModule>, vulkano::Validated<vulkano::VulkanError>> {
        // Load pre-compiled SPIR-V shader
        let spirv_bytes = include_bytes!("../../../../../shaders/compiled/fragment.spv");
        let spirv_words = spirv::bytes_to_words(spirv_bytes).unwrap();
        let create_info = ShaderModuleCreateInfo::new(&spirv_words);
        unsafe { ShaderModule::new(device, create_info) }
    }
}

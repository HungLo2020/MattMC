package net.vulkanic.backends.vulkan;

import net.vulkanic.PipelineDescriptor;
import net.vulkanic.VulkanicShaderStage;
import org.lwjgl.vulkan.VK10;

import java.util.Set;

final class VulkanDescriptorResourceClassifier {
    private VulkanDescriptorResourceClassifier() {
    }

    static int toVkDescriptorType(PipelineDescriptor.ResourceType type) {
        return switch (type) {
            case SAMPLER, COMPARISON_SAMPLER -> VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
            case UNIFORM_BUFFER -> VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
            case STORAGE_IMAGE -> VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
            case TEXEL_BUFFER -> VK10.VK_DESCRIPTOR_TYPE_UNIFORM_TEXEL_BUFFER;
        };
    }

    static int toVkShaderStageFlags(Set<VulkanicShaderStage> stages) {
        int flags = 0;
        for (VulkanicShaderStage stage : stages) {
            flags |= switch (stage) {
                case VERTEX -> VK10.VK_SHADER_STAGE_VERTEX_BIT;
                case FRAGMENT -> VK10.VK_SHADER_STAGE_FRAGMENT_BIT;
                case GEOMETRY -> VK10.VK_SHADER_STAGE_GEOMETRY_BIT;
                case COMPUTE -> VK10.VK_SHADER_STAGE_COMPUTE_BIT;
                case TESSELLATION_CONTROL -> VK10.VK_SHADER_STAGE_TESSELLATION_CONTROL_BIT;
                case TESSELLATION_EVALUATION -> VK10.VK_SHADER_STAGE_TESSELLATION_EVALUATION_BIT;
            };
        }
        return flags;
    }
}

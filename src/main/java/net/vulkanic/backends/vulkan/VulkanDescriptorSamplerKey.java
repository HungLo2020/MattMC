package net.vulkanic.backends.vulkan;

record VulkanDescriptorSamplerKey(
    int minFilter,
    int magFilter,
    int wrapS,
    int wrapT,
    int wrapR,
    int maxLod,
    int compareMode,
    int compareFunc
) {
}

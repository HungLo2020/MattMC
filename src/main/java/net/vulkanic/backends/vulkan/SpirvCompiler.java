package net.vulkanic.backends.vulkan;

import net.vulkanic.VulkanicShaderStage;
import net.vulkanic.VulkanicSpirvModule;

@FunctionalInterface
interface SpirvCompiler {
    VulkanicSpirvModule compile(VulkanicShaderStage stage, CharSequence source, String sourceName, String entryPoint);
}
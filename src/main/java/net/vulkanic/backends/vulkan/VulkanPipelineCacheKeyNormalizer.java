package net.vulkanic.backends.vulkan;

import net.vulkanic.VulkanicShaderStage;
import net.vulkanic.VulkanicSpirvModule;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

final class VulkanPipelineCacheKeyNormalizer {
    private VulkanPipelineCacheKeyNormalizer() {
    }

    static GraphicsPipelineCacheKey graphicsKey(
        VulkanPipelineCreationPlanner.GraphicsPipelinePlan plan,
        List<VulkanicSpirvModule> spirvModules
    ) {
        Objects.requireNonNull(plan, "plan");
        return new GraphicsPipelineCacheKey(
            shaderPayloads(spirvModules),
            plan.fragmentRenderTargetContract().compatibilityKey(),
            plan.pipelineLayoutCompatibility(),
            plan.renderPassCompatibility(),
            plan.vertexInput().cacheKey(),
            plan.inputAssembly(),
            plan.viewportState(),
            plan.rasterizationState(),
            plan.multisampleState(),
            plan.depthStencilState().cacheKey(),
            plan.colorBlendState().cacheKey(),
            plan.dynamicStates(),
            plan.pipelineCreateFlags()
        );
    }

    static ComputePipelineCacheKey computeKey(
        VulkanPipelineCreationPlanner.ComputePipelinePlan plan,
        List<VulkanicSpirvModule> spirvModules
    ) {
        Objects.requireNonNull(plan, "plan");
        return new ComputePipelineCacheKey(
            shaderPayloads(spirvModules),
            plan.pipelineLayoutCompatibility()
        );
    }

    private static List<ShaderPayloadKey> shaderPayloads(List<VulkanicSpirvModule> modules) {
        return List.copyOf(Objects.requireNonNull(modules, "modules").stream()
            .map(module -> new ShaderPayloadKey(
                module.stage(),
                module.entryPoint(),
                module.byteSize(),
                sha256Hex(module.spirvBytes())
            ))
            .sorted(Comparator.comparing(ShaderPayloadKey::stage))
            .toList());
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    record GraphicsPipelineCacheKey(
        List<ShaderPayloadKey> shaderPayloads,
        VulkanFragmentRenderTargetInterfacePlanner.InterfaceCompatibilityKey fragmentRenderTargetInterface,
        VulkanPipelineCreationPlanner.PipelineLayoutCompatibilityInputs pipelineLayout,
        VulkanPipelineCreationPlanner.RenderPassCompatibilityPlan renderPassCompatibility,
        VulkanPipelineCreationPlanner.VertexInputCacheKey vertexInput,
        VulkanPipelineCreationPlanner.InputAssemblyPlan inputAssembly,
        VulkanPipelineCreationPlanner.ViewportStatePlan viewportState,
        VulkanPipelineCreationPlanner.RasterizationStatePlan rasterizationState,
        VulkanPipelineCreationPlanner.MultisampleStatePlan multisampleState,
        VulkanPipelineCreationPlanner.DepthStencilCacheKey depthStencilState,
        VulkanPipelineCreationPlanner.ColorBlendCacheKey colorBlendState,
        List<Integer> dynamicStates,
        int pipelineCreateFlags
    ) {
        GraphicsPipelineCacheKey {
            shaderPayloads = List.copyOf(shaderPayloads);
            Objects.requireNonNull(fragmentRenderTargetInterface, "fragmentRenderTargetInterface");
            Objects.requireNonNull(pipelineLayout, "pipelineLayout");
            Objects.requireNonNull(renderPassCompatibility, "renderPassCompatibility");
            Objects.requireNonNull(vertexInput, "vertexInput");
            Objects.requireNonNull(inputAssembly, "inputAssembly");
            Objects.requireNonNull(viewportState, "viewportState");
            Objects.requireNonNull(rasterizationState, "rasterizationState");
            Objects.requireNonNull(multisampleState, "multisampleState");
            Objects.requireNonNull(depthStencilState, "depthStencilState");
            Objects.requireNonNull(colorBlendState, "colorBlendState");
            dynamicStates = List.copyOf(dynamicStates);
        }
    }

    record ComputePipelineCacheKey(
        List<ShaderPayloadKey> shaderPayloads,
        VulkanPipelineCreationPlanner.PipelineLayoutCompatibilityInputs pipelineLayout
    ) {
        ComputePipelineCacheKey {
            shaderPayloads = List.copyOf(shaderPayloads);
            Objects.requireNonNull(pipelineLayout, "pipelineLayout");
        }
    }

    record ShaderPayloadKey(
        VulkanicShaderStage stage,
        String entryPoint,
        int byteSize,
        String bytesSha256
    ) {
        ShaderPayloadKey {
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(entryPoint, "entryPoint");
            Objects.requireNonNull(bytesSha256, "bytesSha256");
        }
    }
}

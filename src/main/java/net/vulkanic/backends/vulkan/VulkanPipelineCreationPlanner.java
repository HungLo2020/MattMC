package net.vulkanic.backends.vulkan;

import net.blaze3d.platform.PolygonMode;
import net.blaze3d.vertex.VertexFormat;
import net.blaze3d.vertex.VertexFormatElement;
import net.vulkanic.PipelineDescriptor;
import net.vulkanic.VulkanicShaderStage;
import net.vulkanic.VulkanicSpirvModule;
import org.lwjgl.vulkan.EXTAttachmentFeedbackLoopLayout;
import org.lwjgl.vulkan.VK10;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class VulkanPipelineCreationPlanner {
    GraphicsPipelinePlan planGraphics(GraphicsPlanRequest request) {
        Objects.requireNonNull(request, "request");
        PipelineDescriptor descriptor = Objects.requireNonNull(request.descriptor(), "descriptor");
        PipelineDescriptor.PortableState portableState = descriptor.getPortableState();
        VulkanRenderPassCompatibilityKey renderPassCompatibilityKey =
            Objects.requireNonNull(request.renderPassCompatibilityKey(), "renderPassCompatibilityKey");
        VulkanDescriptorSetLayoutPlanner.DescriptorLayoutPlan descriptorLayoutPlan =
            Objects.requireNonNull(request.descriptorLayoutPlan(), "descriptorLayoutPlan");

        List<ShaderStagePlan> shaderStages = graphicsShaderStages(descriptor.getSpirvModules());
        VertexInputPlan vertexInput = vertexInputPlan(portableState, descriptor.getVertexInputState());
        InputAssemblyPlan inputAssembly = new InputAssemblyPlan(
            VulkanPipelineFormatClassifier.toVkPrimitiveTopology(portableState.vertexFormatMode()),
            false
        );
        ViewportStatePlan viewportState = new ViewportStatePlan(1, 1);
        VulkanPipelineState pipelineState = VulkanPipelineState.from(
            portableState,
            renderPassCompatibilityKey.colorAttachmentCount(),
            request.polygonModeResolver(),
            request.blendStateResolver(),
            request.stencilState(),
            renderPassCompatibilityKey.hasStencilAttachment()
        );
        RasterizationStatePlan rasterizationState = new RasterizationStatePlan(
            false,
            false,
            pipelineState.polygonMode(),
            1.0f,
            pipelineState.cullMode(),
            pipelineState.frontFace(),
            pipelineState.depthBiasEnabled(),
            pipelineState.depthBiasConstantFactor(),
            pipelineState.depthBiasSlopeFactor(),
            0.0f
        );
        MultisampleStatePlan multisampleState = new MultisampleStatePlan(false, VK10.VK_SAMPLE_COUNT_1_BIT);
        DepthStencilStatePlan depthStencilState = new DepthStencilStatePlan(
            pipelineState.depthTestEnabled(),
            pipelineState.depthWriteEnabled(),
            pipelineState.depthCompareOp(),
            false,
            pipelineState.stencilTestEnabled(),
            pipelineState.frontStencil(),
            pipelineState.backStencil()
        );
        ColorBlendStatePlan colorBlendState = new ColorBlendStatePlan(
            pipelineState.logicOpEnabled(),
            pipelineState.logicOp(),
            pipelineState.colorBlendAttachments(),
            List.of(0f, 0f, 0f, 0f)
        );
        List<Integer> dynamicStates = List.of(VK10.VK_DYNAMIC_STATE_VIEWPORT, VK10.VK_DYNAMIC_STATE_SCISSOR);
        int pipelineCreateFlags = request.attachmentFeedbackLoopLayoutEnabled()
            ? EXTAttachmentFeedbackLoopLayout.VK_PIPELINE_CREATE_COLOR_ATTACHMENT_FEEDBACK_LOOP_BIT_EXT
                | EXTAttachmentFeedbackLoopLayout.VK_PIPELINE_CREATE_DEPTH_STENCIL_ATTACHMENT_FEEDBACK_LOOP_BIT_EXT
            : 0;
        PipelineLayoutCompatibilityInputs layoutInputs = pipelineLayoutInputs(
            descriptorLayoutPlan,
            descriptor.getPushConstantRanges()
        );

        return new GraphicsPipelinePlan(
            shaderStages,
            vertexInput,
            inputAssembly,
            viewportState,
            rasterizationState,
            multisampleState,
            depthStencilState,
            colorBlendState,
            dynamicStates,
            new RenderPassCompatibilityPlan(renderPassCompatibilityKey, 0),
            layoutInputs,
            new PipelineCacheKeyInputs(
                descriptor.getPipelineCompilationKey(),
                descriptor.getResourceLayoutCacheKey(),
                renderPassCompatibilityKey,
                layoutInputs,
                vertexInput.cacheKey(),
                inputAssembly,
                rasterizationState,
                multisampleState,
                depthStencilState.cacheKey(),
                colorBlendState.cacheKey(),
                dynamicStates
            ),
            pipelineCreateFlags
        );
    }

    ComputePipelinePlan planCompute(ComputePlanRequest request) {
        Objects.requireNonNull(request, "request");
        PipelineDescriptor descriptor = Objects.requireNonNull(request.descriptor(), "descriptor");
        VulkanDescriptorSetLayoutPlanner.DescriptorLayoutPlan descriptorLayoutPlan =
            Objects.requireNonNull(request.descriptorLayoutPlan(), "descriptorLayoutPlan");
        ShaderStagePlan computeStage = computeShaderStage(descriptor.getSpirvModules());
        PipelineLayoutCompatibilityInputs layoutInputs = pipelineLayoutInputs(
            descriptorLayoutPlan,
            descriptor.getPushConstantRanges()
        );
        return new ComputePipelinePlan(
            computeStage,
            layoutInputs,
            new ComputePipelineCacheKeyInputs(
                descriptor.getPipelineCompilationKey(),
                descriptor.getResourceLayoutCacheKey(),
                layoutInputs,
                computeStage
            )
        );
    }

    private static List<ShaderStagePlan> graphicsShaderStages(List<VulkanicSpirvModule> modules) {
        Map<VulkanicShaderStage, VulkanicSpirvModule> byStage = modulesByStage(modules);
        ShaderStagePlan vertex = stagePlan(requiredStage(byStage, VulkanicShaderStage.VERTEX));
        ShaderStagePlan fragment = stagePlan(requiredStage(byStage, VulkanicShaderStage.FRAGMENT));
        VulkanicSpirvModule geometryModule = byStage.get(VulkanicShaderStage.GEOMETRY);
        if (geometryModule == null) {
            return List.of(vertex, fragment);
        }
        return List.of(vertex, stagePlan(geometryModule), fragment);
    }

    private static ShaderStagePlan computeShaderStage(List<VulkanicSpirvModule> modules) {
        Map<VulkanicShaderStage, VulkanicSpirvModule> byStage = modulesByStage(modules);
        return stagePlan(requiredStage(byStage, VulkanicShaderStage.COMPUTE));
    }

    private static Map<VulkanicShaderStage, VulkanicSpirvModule> modulesByStage(List<VulkanicSpirvModule> modules) {
        Map<VulkanicShaderStage, VulkanicSpirvModule> byStage = new EnumMap<>(VulkanicShaderStage.class);
        for (VulkanicSpirvModule module : modules) {
            VulkanicSpirvModule previous = byStage.putIfAbsent(module.stage(), module);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate SPIR-V module for Vulkan shader stage " + module.stage());
            }
        }
        return byStage;
    }

    private static VulkanicSpirvModule requiredStage(
        Map<VulkanicShaderStage, VulkanicSpirvModule> modules,
        VulkanicShaderStage stage
    ) {
        VulkanicSpirvModule module = modules.get(stage);
        if (module == null) {
            throw new IllegalArgumentException("Missing required Vulkan shader stage " + stage);
        }
        return module;
    }

    private static ShaderStagePlan stagePlan(VulkanicSpirvModule module) {
        return new ShaderStagePlan(
            module.stage(),
            VulkanDescriptorResourceClassifier.toVkShaderStageFlags(java.util.Set.of(module.stage())),
            module.entryPoint(),
            module.sourceName(),
            module.compilerName()
        );
    }

    private static VertexInputPlan vertexInputPlan(
        PipelineDescriptor.PortableState portableState,
        PipelineDescriptor.VertexInputState explicitVertexInput
    ) {
        if (explicitVertexInput != null) {
            List<VertexBindingPlan> bindings = new ArrayList<>(explicitVertexInput.bindings().size());
            for (PipelineDescriptor.VertexInputBinding binding : explicitVertexInput.bindings()) {
                bindings.add(new VertexBindingPlan(
                    binding.binding(),
                    binding.stride(),
                    binding.inputRate() == PipelineDescriptor.VertexInputRate.INSTANCE
                        ? VK10.VK_VERTEX_INPUT_RATE_INSTANCE
                        : VK10.VK_VERTEX_INPUT_RATE_VERTEX
                ));
            }
            List<VertexAttributePlan> attributes = new ArrayList<>(explicitVertexInput.attributes().size());
            for (PipelineDescriptor.VertexInputAttribute attribute : explicitVertexInput.attributes()) {
                attributes.add(new VertexAttributePlan(
                    attribute.location(),
                    attribute.binding(),
                    VulkanPipelineFormatClassifier.toVkVertexAttributeFormat(attribute.format()),
                    attribute.offset()
                ));
            }
            return new VertexInputPlan(bindings, attributes, true);
        }

        VertexFormat vertexFormat = portableState.vertexFormat();
        List<VertexFormatElement> elements = vertexFormat.getElements();
        List<VertexBindingPlan> bindings = List.of(new VertexBindingPlan(
            0,
            vertexFormat.getVertexSize(),
            VK10.VK_VERTEX_INPUT_RATE_VERTEX
        ));
        List<VertexAttributePlan> attributes = new ArrayList<>(elements.size());
        for (int i = 0; i < elements.size(); i++) {
            VertexFormatElement element = elements.get(i);
            attributes.add(new VertexAttributePlan(
                vertexFormat.getShaderAttributeLocation(i),
                0,
                VulkanPipelineFormatClassifier.toVkVertexElementFormat(element),
                vertexFormat.getOffset(element)
            ));
        }
        return new VertexInputPlan(bindings, attributes, false);
    }

    private static PipelineLayoutCompatibilityInputs pipelineLayoutInputs(
        VulkanDescriptorSetLayoutPlanner.DescriptorLayoutPlan descriptorLayoutPlan,
        List<PipelineDescriptor.PushConstantRange> pushConstantRanges
    ) {
        List<PushConstantRangePlan> plannedRanges = pushConstantRanges.stream()
            .map(range -> new PushConstantRangePlan(
                VulkanDescriptorResourceClassifier.toVkShaderStageFlags(range.stages()),
                range.offset(),
                range.size()
            ))
            .toList();
        return new PipelineLayoutCompatibilityInputs(
            descriptorLayoutPlan.compatibilityKey(),
            plannedRanges
        );
    }

    record GraphicsPlanRequest(
        PipelineDescriptor descriptor,
        VulkanDescriptorSetLayoutPlanner.DescriptorLayoutPlan descriptorLayoutPlan,
        VulkanRenderPassCompatibilityKey renderPassCompatibilityKey,
        VulkanPipelineState.PolygonModeResolver polygonModeResolver,
        VulkanPipelineState.BlendStateResolver blendStateResolver,
        VulkanPipelineState.StencilState stencilState,
        boolean attachmentFeedbackLoopLayoutEnabled
    ) {
        GraphicsPlanRequest {
            Objects.requireNonNull(polygonModeResolver, "polygonModeResolver");
            Objects.requireNonNull(blendStateResolver, "blendStateResolver");
            Objects.requireNonNull(stencilState, "stencilState");
        }
    }

    record ComputePlanRequest(
        PipelineDescriptor descriptor,
        VulkanDescriptorSetLayoutPlanner.DescriptorLayoutPlan descriptorLayoutPlan
    ) {
    }

    record GraphicsPipelinePlan(
        List<ShaderStagePlan> shaderStages,
        VertexInputPlan vertexInput,
        InputAssemblyPlan inputAssembly,
        ViewportStatePlan viewportState,
        RasterizationStatePlan rasterizationState,
        MultisampleStatePlan multisampleState,
        DepthStencilStatePlan depthStencilState,
        ColorBlendStatePlan colorBlendState,
        List<Integer> dynamicStates,
        RenderPassCompatibilityPlan renderPassCompatibility,
        PipelineLayoutCompatibilityInputs pipelineLayoutCompatibility,
        PipelineCacheKeyInputs cacheKeyInputs,
        int pipelineCreateFlags
    ) {
        GraphicsPipelinePlan {
            shaderStages = List.copyOf(shaderStages);
            dynamicStates = List.copyOf(dynamicStates);
            Objects.requireNonNull(vertexInput, "vertexInput");
            Objects.requireNonNull(inputAssembly, "inputAssembly");
            Objects.requireNonNull(viewportState, "viewportState");
            Objects.requireNonNull(rasterizationState, "rasterizationState");
            Objects.requireNonNull(multisampleState, "multisampleState");
            Objects.requireNonNull(depthStencilState, "depthStencilState");
            Objects.requireNonNull(colorBlendState, "colorBlendState");
            Objects.requireNonNull(renderPassCompatibility, "renderPassCompatibility");
            Objects.requireNonNull(pipelineLayoutCompatibility, "pipelineLayoutCompatibility");
            Objects.requireNonNull(cacheKeyInputs, "cacheKeyInputs");
        }
    }

    record ComputePipelinePlan(
        ShaderStagePlan shaderStage,
        PipelineLayoutCompatibilityInputs pipelineLayoutCompatibility,
        ComputePipelineCacheKeyInputs cacheKeyInputs
    ) {
        ComputePipelinePlan {
            Objects.requireNonNull(shaderStage, "shaderStage");
            Objects.requireNonNull(pipelineLayoutCompatibility, "pipelineLayoutCompatibility");
            Objects.requireNonNull(cacheKeyInputs, "cacheKeyInputs");
        }
    }

    record ShaderStagePlan(
        VulkanicShaderStage stage,
        int vkStageFlag,
        String entryPoint,
        String sourceName,
        String compilerName
    ) {
        ShaderStagePlan {
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(entryPoint, "entryPoint");
            Objects.requireNonNull(sourceName, "sourceName");
            Objects.requireNonNull(compilerName, "compilerName");
        }
    }

    record VertexInputPlan(
        List<VertexBindingPlan> bindings,
        List<VertexAttributePlan> attributes,
        boolean explicit
    ) {
        VertexInputPlan {
            bindings = List.copyOf(bindings);
            attributes = List.copyOf(attributes);
        }

        VertexInputCacheKey cacheKey() {
            return new VertexInputCacheKey(bindings, attributes, explicit);
        }
    }

    record VertexBindingPlan(int binding, int stride, int inputRate) {
    }

    record VertexAttributePlan(int location, int binding, int format, int offset) {
    }

    record InputAssemblyPlan(int topology, boolean primitiveRestartEnabled) {
    }

    record ViewportStatePlan(int viewportCount, int scissorCount) {
    }

    record RasterizationStatePlan(
        boolean depthClampEnabled,
        boolean rasterizerDiscardEnabled,
        int polygonMode,
        float lineWidth,
        int cullMode,
        int frontFace,
        boolean depthBiasEnabled,
        float depthBiasConstantFactor,
        float depthBiasSlopeFactor,
        float depthBiasClamp
    ) {
    }

    record MultisampleStatePlan(boolean sampleShadingEnabled, int rasterizationSamples) {
    }

    record DepthStencilStatePlan(
        boolean depthTestEnabled,
        boolean depthWriteEnabled,
        int depthCompareOp,
        boolean depthBoundsTestEnabled,
        boolean stencilTestEnabled,
        VulkanPipelineState.StencilFaceState frontStencil,
        VulkanPipelineState.StencilFaceState backStencil
    ) {
        DepthStencilStatePlan {
            Objects.requireNonNull(frontStencil, "frontStencil");
            Objects.requireNonNull(backStencil, "backStencil");
        }

        DepthStencilCacheKey cacheKey() {
            return new DepthStencilCacheKey(
                depthTestEnabled,
                depthWriteEnabled,
                depthCompareOp,
                depthBoundsTestEnabled,
                stencilTestEnabled,
                frontStencil,
                backStencil
            );
        }
    }

    record ColorBlendStatePlan(
        boolean logicOpEnabled,
        int logicOp,
        List<VulkanPipelineState.ColorBlendAttachment> attachments,
        List<Float> blendConstants
    ) {
        ColorBlendStatePlan {
            attachments = List.copyOf(attachments);
            blendConstants = List.copyOf(blendConstants);
        }

        ColorBlendCacheKey cacheKey() {
            return new ColorBlendCacheKey(logicOpEnabled, logicOp, attachments, blendConstants);
        }
    }

    record RenderPassCompatibilityPlan(
        VulkanRenderPassCompatibilityKey compatibilityKey,
        int subpass
    ) {
        RenderPassCompatibilityPlan {
            Objects.requireNonNull(compatibilityKey, "compatibilityKey");
        }
    }

    record PipelineLayoutCompatibilityInputs(
        VulkanDescriptorSetLayoutPlanner.PipelineLayoutCompatibilityKey descriptorSets,
        List<PushConstantRangePlan> pushConstantRanges
    ) {
        PipelineLayoutCompatibilityInputs {
            Objects.requireNonNull(descriptorSets, "descriptorSets");
            pushConstantRanges = List.copyOf(pushConstantRanges);
        }
    }

    record PushConstantRangePlan(int stageFlags, int offset, int size) {
    }

    record PipelineCacheKeyInputs(
        String pipelineCompilationKey,
        String resourceLayoutCacheKey,
        VulkanRenderPassCompatibilityKey renderPassCompatibilityKey,
        PipelineLayoutCompatibilityInputs pipelineLayoutCompatibility,
        VertexInputCacheKey vertexInput,
        InputAssemblyPlan inputAssembly,
        RasterizationStatePlan rasterizationState,
        MultisampleStatePlan multisampleState,
        DepthStencilCacheKey depthStencilState,
        ColorBlendCacheKey colorBlendState,
        List<Integer> dynamicStates
    ) {
        PipelineCacheKeyInputs {
            Objects.requireNonNull(pipelineCompilationKey, "pipelineCompilationKey");
            Objects.requireNonNull(resourceLayoutCacheKey, "resourceLayoutCacheKey");
            Objects.requireNonNull(renderPassCompatibilityKey, "renderPassCompatibilityKey");
            Objects.requireNonNull(pipelineLayoutCompatibility, "pipelineLayoutCompatibility");
            Objects.requireNonNull(vertexInput, "vertexInput");
            Objects.requireNonNull(inputAssembly, "inputAssembly");
            Objects.requireNonNull(rasterizationState, "rasterizationState");
            Objects.requireNonNull(multisampleState, "multisampleState");
            Objects.requireNonNull(depthStencilState, "depthStencilState");
            Objects.requireNonNull(colorBlendState, "colorBlendState");
            dynamicStates = List.copyOf(dynamicStates);
        }
    }

    record ComputePipelineCacheKeyInputs(
        String pipelineCompilationKey,
        String resourceLayoutCacheKey,
        PipelineLayoutCompatibilityInputs pipelineLayoutCompatibility,
        ShaderStagePlan shaderStage
    ) {
        ComputePipelineCacheKeyInputs {
            Objects.requireNonNull(pipelineCompilationKey, "pipelineCompilationKey");
            Objects.requireNonNull(resourceLayoutCacheKey, "resourceLayoutCacheKey");
            Objects.requireNonNull(pipelineLayoutCompatibility, "pipelineLayoutCompatibility");
            Objects.requireNonNull(shaderStage, "shaderStage");
        }
    }

    record VertexInputCacheKey(
        List<VertexBindingPlan> bindings,
        List<VertexAttributePlan> attributes,
        boolean explicit
    ) {
        VertexInputCacheKey {
            bindings = bindings.stream()
                .sorted(Comparator.comparingInt(VertexBindingPlan::binding))
                .toList();
            attributes = attributes.stream()
                .sorted(Comparator.comparingInt(VertexAttributePlan::location)
                    .thenComparingInt(VertexAttributePlan::binding))
                .toList();
        }
    }

    record DepthStencilCacheKey(
        boolean depthTestEnabled,
        boolean depthWriteEnabled,
        int depthCompareOp,
        boolean depthBoundsTestEnabled,
        boolean stencilTestEnabled,
        VulkanPipelineState.StencilFaceState frontStencil,
        VulkanPipelineState.StencilFaceState backStencil
    ) {
    }

    record ColorBlendCacheKey(
        boolean logicOpEnabled,
        int logicOp,
        List<VulkanPipelineState.ColorBlendAttachment> attachments,
        List<Float> blendConstants
    ) {
        ColorBlendCacheKey {
            attachments = List.copyOf(attachments);
            blendConstants = List.copyOf(blendConstants);
        }
    }

    @FunctionalInterface
    interface PolygonModeResolver extends VulkanPipelineState.PolygonModeResolver {
        @Override
        int resolve(PolygonMode mode);
    }
}

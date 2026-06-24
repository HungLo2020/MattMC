package net.sodium.client.render.chunk.shader;

import net.blaze3d.pipeline.RenderPipeline;
import net.vulkanic.PipelineDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class VulkanTerrainPipelineDiagnostics {
    private static final Logger LOGGER = LoggerFactory.getLogger("MattMC-VulkanTerrainParity");
    private static final boolean ENABLED = Boolean.parseBoolean(
        System.getProperty(
            "mattmc.vulkanTerrainParity",
            System.getenv().getOrDefault("MATTMC_VULKAN_TERRAIN_PARITY", "false")
        )
    );
    private static final int MAX_LOGS = Integer.getInteger("mattmc.vulkanTerrainParity.maxLogs", 128);
    private static final AtomicInteger PIPELINE_LOGS = new AtomicInteger();
    private static final AtomicInteger RESOURCE_LOGS = new AtomicInteger();

    private VulkanTerrainPipelineDiagnostics() {
    }

    public static boolean enabled() {
        return ENABLED;
    }

    public static void logPipeline(RenderPipeline pipeline, TerrainPipelineContract contract) {
        if (!ENABLED || PIPELINE_LOGS.getAndIncrement() >= MAX_LOGS) {
            return;
        }

        LOGGER.info(
            "pipeline location={} source={} pass={} reloadVersion={} shadow={} depthWrite={} cull={} blendPresent={} colorWrite={} alphaWrite={} depthTest={} samplers={}",
            pipeline.getLocation(),
            contract.sourcePipelineLocation(),
            contract.passKind(),
            contract.shaderReloadVersion(),
            contract.shadowPass(),
            pipeline.isWriteDepth(),
            pipeline.isCull(),
            pipeline.getBlendFunction().isPresent(),
            pipeline.isWriteColor(),
            pipeline.isWriteAlpha(),
            pipeline.getDepthTestFunction(),
            contract.samplerNames()
        );
    }

    public static void logResourceSubmission(
        RenderPipeline pipeline,
        PipelineDescriptor expectedDescriptor,
        PipelineDescriptor submittedDescriptor,
        List<String> missingResources
    ) {
        if (!ENABLED || RESOURCE_LOGS.getAndIncrement() >= MAX_LOGS) {
            return;
        }

        LOGGER.info(
            "resources pipeline={} expectedBindings={} submittedBindings={} completeCoverage={} missing={}",
            pipeline.getLocation(),
            expectedDescriptor.getResourceLayout().bindings().size(),
            submittedDescriptor.getResourceLayout().bindings().size(),
            expectedDescriptor.getResourceLayout().bindings().size() == submittedDescriptor.getResourceLayout().bindings().size(),
            missingResources
        );
    }
}

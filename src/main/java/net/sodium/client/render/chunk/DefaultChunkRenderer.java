package net.sodium.client.render.chunk;

import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.buffers.GpuBufferSlice;
import net.blaze3d.buffers.Std140Builder;
import net.blaze3d.buffers.Std140SizeCalculator;
import net.blaze3d.pipeline.RenderTarget;
import net.blaze3d.systems.CommandEncoder;
import net.blaze3d.systems.RenderPass;
import net.blaze3d.textures.GpuTextureView;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.sodium.client.SodiumClientMod;
import net.sodium.client.gl.device.CommandList;
import net.sodium.client.gl.device.DrawCommandList;
import net.sodium.client.gl.device.MultiDrawBatch;
import net.sodium.client.gl.device.RenderDevice;
import net.sodium.client.render.device.RenderTessellation;
import net.sodium.client.render.device.RenderTessellationBinding;
import net.sodium.client.render.chunk.data.SectionRenderDataStorage;
import net.sodium.client.render.chunk.lists.ChunkRenderList;
import net.sodium.client.render.chunk.lists.ChunkRenderListIterable;
import net.sodium.client.render.chunk.region.RenderRegion;
import net.sodium.client.render.chunk.shader.ChunkShaderInterface;
import net.sodium.client.render.chunk.shader.RenderPassChunkShaderInterface;
import net.sodium.client.render.chunk.shader.SharedChunkProgramOverrides;
import net.sodium.client.render.chunk.shader.SodiumChunkRenderPipelines;
import net.sodium.client.render.chunk.shader.TerrainPipelineContract;
import net.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.sodium.client.render.chunk.vertex.format.ChunkMeshFormats;
import net.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.sodium.client.render.viewport.CameraTransform;
import net.sodium.client.util.FogParameters;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicIndexType;
import net.vulkanic.VulkanicPrimitiveMode;
import net.vulkanic.VulkanicRenderTargetDescriptor;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Pointer;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public class DefaultChunkRenderer extends ShaderChunkRenderer {
    private static final int SODIUM_CHUNK_PARAMS_UBO_SIZE = new Std140SizeCalculator()
        .putVec2()
        .putVec4()
        .putVec2()
        .putVec2()
        .get();
    private static final Logger LOGGER = LoggerFactory.getLogger("Sodium-VulkanTerrain");
    private static final int MAX_VULKAN_RENDER_PROBES = 48;
    private static final boolean TRACE_VULKAN_TERRAIN_RENDER_TARGETS = Boolean.getBoolean("mattmc.vulkan.traceTerrainRenderTargets");
    private static final boolean USE_DESCRIPTOR_TERRAIN_RENDER_PASS =
        Boolean.parseBoolean(System.getProperty("mattmc.vulkan.useDescriptorTerrainRenderPass", "true"));

    private static int vulkanRenderProbeCount;

    private final SharedQuadIndexBuffer sharedIndexBuffer;
    private final GpuBuffer sodiumChunkParamsBuffer;
    public DefaultChunkRenderer(RenderDevice device, ChunkVertexType vertexType) {
        super(device, vertexType);

        this.sharedIndexBuffer = new SharedQuadIndexBuffer(device.createCommandList(), SharedQuadIndexBuffer.IndexType.INTEGER);
        this.sodiumChunkParamsBuffer = VulkanicAPI.createBuffer(
            () -> "Sodium chunk params UBO",
            GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
            SODIUM_CHUNK_PARAMS_UBO_SIZE
        );
    }

    /**
     * Renders the terrain for a particular render pass. Each region is rendered
     * with one draw call. The command buffer for each draw command is filled by
     * iterating the sections and adding the draw commands for each section.
     */
    @Override
    public void render(ChunkRenderMatrices matrices,
                       CommandList commandList,
                       ChunkRenderListIterable renderLists,
                       TerrainRenderPass renderPass,
                       CameraTransform camera,
                       FogParameters parameters,
                       boolean indexedRenderingEnabled) {
        if (VulkanicAPI.isVulkanBackendSelected()) {
			this.renderWithVulkan(matrices, commandList, renderLists, renderPass, camera, parameters, indexedRenderingEnabled);
            return;
        }

        super.begin(renderPass, parameters);

        // Iris: From MixinDefaultChunkRenderer - disable block face culling in shadow pass
        final boolean useBlockFaceCulling = net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered() 
            ? false 
            : SodiumClientMod.options().performance.useBlockFaceCulling;
        final boolean useIndexedTessellation = renderPass.isTranslucent() && indexedRenderingEnabled;

        ChunkShaderInterface shader = this.activeProgram.getInterface();
        shader.setProjectionMatrix(matrices.projection());
        shader.setModelViewMatrix(matrices.modelView());

        Iterator<ChunkRenderList> iterator = renderLists.iterator(renderPass.isTranslucent());

        while (iterator.hasNext()) {
            ChunkRenderList renderList = iterator.next();

            var region = renderList.getRegion();
            var storage = region.getStorage(renderPass);

            if (storage == null) {
                continue;
            }

            var batch = region.getCachedBatch(renderPass);
            if (!batch.isFilled) {
                fillCommandBuffer(batch, region, storage, renderList, camera, renderPass, useBlockFaceCulling, useIndexedTessellation);
            }

            if (batch.isEmpty()) {
                continue;
            }

            // When the shared index buffer is being used, we must ensure the storage has been allocated *before*
            // the tessellation is prepared.
            if (!useIndexedTessellation) {
                this.sharedIndexBuffer.ensureCapacity(commandList, batch.getIndexBufferSize());
            }

            RenderTessellation tessellation;

            if (useIndexedTessellation) {
                tessellation = this.prepareIndexedTessellation(commandList, region);
            } else {
                tessellation = this.prepareTessellation(commandList, region);
            }

            setModelMatrixUniforms(shader, region, camera);
            try (VulkanicAPI.ShaderInputParityScope ignored = beginSodiumTerrainParityScope(
                "sodium-terrain-opengl-region-batch",
                renderPass,
                region,
                batch
            )) {
                executeDrawBatch(commandList, tessellation, batch);
            }
        }

        super.end(renderPass);
    }

    private void renderWithVulkan(
        ChunkRenderMatrices matrices,
        CommandList commandList,
        ChunkRenderListIterable renderLists,
        TerrainRenderPass terrainPass,
        CameraTransform camera,
		FogParameters parameters,
        boolean indexedRenderingEnabled
    ) {
		super.begin(terrainPass, parameters);
        boolean shadersEnabled = Iris.getIrisConfig().areShadersEnabled();
        if (shadersEnabled) {
            SharedChunkProgramOverrides.pushActiveProgram(this.activeProgram);
        } else {
            SharedChunkProgramOverrides.clearActiveProgram();
        }
		ChunkShaderInterface shader = this.activeProgram.getInterface();
        RenderPassChunkShaderInterface renderPassShader = shadersEnabled && shader instanceof RenderPassChunkShaderInterface sharedShader
            ? sharedShader
            : null;
        TerrainPipelineContract pipelineContract = SodiumChunkRenderPipelines.createContract(terrainPass, renderPassShader);
		shader.setProjectionMatrix(matrices.projection());
		shader.setModelViewMatrix(matrices.modelView());

		try {
        final boolean useBlockFaceCulling = net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered()
            ? false
            : SodiumClientMod.options().performance.useBlockFaceCulling;
        final boolean useIndexedTessellation = terrainPass.isTranslucent() && indexedRenderingEnabled;

        RenderTarget target = terrainPass.getTarget();
        GlFramebuffer shaderFramebuffer = this.resolveShaderFramebuffer(terrainPass, shadersEnabled);
        GpuTextureView colorTargetView = VulkanicAPI.getOutputColorTextureOverride() != null
            ? VulkanicAPI.getOutputColorTextureOverride()
            : target.getColorTextureView();
        GpuTextureView depthTargetView = this.resolveVulkanTerrainDepthTarget(target);
        if (!net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
            VulkanicAPI.setDynamicViewport(
                VulkanicAPI.getCommandContext(),
                0,
                0,
                colorTargetView.getWidth(0),
                colorTargetView.getHeight(0)
            );
        }

        CommandEncoder commandEncoder = VulkanicAPI.createNativeTerrainCommandEncoder();
        GpuBufferSlice chunkParams = this.writeChunkParams(commandEncoder, parameters);
        List<PreparedRegionDraw> preparedDraws = new ArrayList<>();
        double nearestRegionDistanceSq = Double.POSITIVE_INFINITY;
        int nearestRegionOriginX = 0;
        int nearestRegionOriginY = 0;
        int nearestRegionOriginZ = 0;
        float nearestModelOffsetX = 0.0F;
        float nearestModelOffsetY = 0.0F;
        float nearestModelOffsetZ = 0.0F;
        int nearestBatchSize = 0;
        int totalBatchDrawCommands = 0;
        Iterator<ChunkRenderList> iterator = renderLists.iterator(terrainPass.isTranslucent());
        while (iterator.hasNext()) {
            ChunkRenderList renderList = iterator.next();
            RenderRegion region = renderList.getRegion();
            SectionRenderDataStorage storage = region.getStorage(terrainPass);

            if (storage == null) {
                continue;
            }

            MultiDrawBatch batch = region.getCachedBatch(terrainPass);
            if (!batch.isFilled) {
                fillCommandBuffer(batch, region, storage, renderList, camera, terrainPass, useBlockFaceCulling, useIndexedTessellation);
            }

            if (batch.isEmpty()) {
                continue;
            }

            totalBatchDrawCommands += batch.size;

            GpuBuffer vertexBuffer = region.getResources().getGeometryGpuBuffer(GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST);
            GpuBuffer indexBuffer;
            if (useIndexedTessellation) {
                indexBuffer = region.getResources().getIndexGpuBuffer(GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST);
            } else {
                this.sharedIndexBuffer.ensureCapacity(commandList, batch.getIndexBufferSize());
                indexBuffer = this.sharedIndexBuffer.gpuBufferView(GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST);
            }

            float modelOffsetX = getCameraTranslation(region.getOriginX(), camera.intX, camera.fracX);
            float modelOffsetY = getCameraTranslation(region.getOriginY(), camera.intY, camera.fracY);
            float modelOffsetZ = getCameraTranslation(region.getOriginZ(), camera.intZ, camera.fracZ);
            double regionDistanceSq = modelOffsetX * modelOffsetX + modelOffsetY * modelOffsetY + modelOffsetZ * modelOffsetZ;
            if (regionDistanceSq < nearestRegionDistanceSq) {
                nearestRegionDistanceSq = regionDistanceSq;
                nearestRegionOriginX = region.getOriginX();
                nearestRegionOriginY = region.getOriginY();
                nearestRegionOriginZ = region.getOriginZ();
                nearestModelOffsetX = modelOffsetX;
                nearestModelOffsetY = modelOffsetY;
                nearestModelOffsetZ = modelOffsetZ;
                nearestBatchSize = batch.size;
            }

            preparedDraws.add(new PreparedRegionDraw(
                batch,
                vertexBuffer,
                indexBuffer,
                region,
                this.writeDynamicTransforms(matrices.modelView(), modelOffsetX, modelOffsetY, modelOffsetZ)
            ));
        }


        int submittedDrawCommands = 0;
        long submittedIndexCount = 0L;
        try (RenderPass renderPass = this.createVulkanTerrainRenderPass(
            commandEncoder,
            shaderFramebuffer,
            colorTargetView,
            depthTargetView
        )) {
            VulkanicAPI.bindDefaultUniforms(renderPass);
			renderPass.setPipeline(SodiumChunkRenderPipelines.forContract(pipelineContract));
            renderPass.bindSampler("Sampler0", terrainPass.getAtlas());
            renderPass.bindSampler("Sampler2", net.minecraft.client.Minecraft.getInstance().gameRenderer.lightTexture().getTextureView());
			if (renderPassShader != null) {
				renderPassShader.bindRenderPassResources(renderPass, terrainPass);
			}
            if (!Iris.getIrisConfig().areShadersEnabled()) {
                // In no-shader mode, active shared chunk overrides expect u_BlockTex/u_LightTex aliases.
                renderPass.bindSampler("u_BlockTex", terrainPass.getAtlas());
                renderPass.bindSampler("u_LightTex", net.minecraft.client.Minecraft.getInstance().gameRenderer.lightTexture().getTextureView());
            }
            renderPass.setUniform("SodiumChunkParams", chunkParams);

            for (PreparedRegionDraw preparedDraw : preparedDraws) {
                renderPass.setVertexBuffer(0, preparedDraw.vertexBuffer());
                renderPass.setIndexBuffer(preparedDraw.indexBuffer(), net.blaze3d.vertex.VertexFormat.IndexType.INT);
                renderPass.setUniform("DynamicTransforms", preparedDraw.transforms());

                try (VulkanicAPI.ShaderInputParityScope ignored = beginSodiumTerrainParityScope(
                    "sodium-terrain-vulkan-region-batch",
                    terrainPass,
                    preparedDraw.region(),
                    preparedDraw.batch()
                )) {
                    for (int drawIndex = 0; drawIndex < preparedDraw.batch().size; drawIndex++) {
                        int indexCount = MemoryUtil.memGetInt(preparedDraw.batch().pElementCount + ((long) drawIndex << 2));
                        if (indexCount <= 0) {
                            continue;
                        }

                        int baseVertex = MemoryUtil.memGetInt(preparedDraw.batch().pBaseVertex + ((long) drawIndex << 2));
                        long rawIndexOffsetBytes = MemoryUtil.memGetAddress(preparedDraw.batch().pElementPointer + ((long) drawIndex << Pointer.POINTER_SHIFT));
                        int firstIndex = Math.toIntExact(rawIndexOffsetBytes / Integer.BYTES);

                        renderPass.drawIndexed(baseVertex, firstIndex, indexCount, 1);
                        submittedDrawCommands++;
                        submittedIndexCount += indexCount;
                    }
                }
            }
        }

        this.logVulkanRenderProbe(
            terrainPass,
            preparedDraws.size(),
            totalBatchDrawCommands,
            submittedDrawCommands,
            submittedIndexCount,
            nearestRegionDistanceSq,
            nearestRegionOriginX,
            nearestRegionOriginY,
            nearestRegionOriginZ,
            nearestModelOffsetX,
            nearestModelOffsetY,
            nearestModelOffsetZ,
            nearestBatchSize
        );
		} finally {
			SharedChunkProgramOverrides.clearActiveProgram();
			super.end(terrainPass);
		}
    }

    private GlFramebuffer resolveShaderFramebuffer(TerrainRenderPass terrainPass, boolean shadersEnabled) {
        if (!shadersEnabled || !(Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline irisRenderingPipeline)) {
            return null;
        }

        return irisRenderingPipeline.getSodiumPrograms().getFramebuffer(terrainPass);
    }

    private GpuTextureView resolveVulkanTerrainDepthTarget(RenderTarget target) {
        GpuTextureView depthOverride = VulkanicAPI.getOutputDepthTextureOverride();
        if (depthOverride != null) {
            return depthOverride;
        }

        GpuTextureView targetDepth = target.getDepthTextureView();
        if (targetDepth != null) {
            return targetDepth;
        }

        return net.minecraft.client.Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
    }

    private RenderPass createVulkanTerrainRenderPass(
        CommandEncoder commandEncoder,
        GlFramebuffer shaderFramebuffer,
        GpuTextureView colorTargetView,
        GpuTextureView depthTargetView
    ) {
        if (shaderFramebuffer == null) {
            return commandEncoder.createRenderPass(
                () -> "Sodium chunk terrain",
                colorTargetView,
                OptionalInt.empty(),
                depthTargetView,
                OptionalDouble.empty()
            );
        }

        VulkanicRenderTargetDescriptor descriptor =
            shaderFramebuffer.createRenderTargetDescriptor(() -> "Sodium chunk terrain");
        this.traceVulkanTerrainRenderTargetDescriptor(descriptor);
        boolean preferDescriptor = USE_DESCRIPTOR_TERRAIN_RENDER_PASS
            && VulkanicAPI.isVulkanBackendInitializedAndSelected();
        return commandEncoder.createRenderPass(descriptor, shaderFramebuffer.getId(), preferDescriptor);
    }

    private void traceVulkanTerrainRenderTargetDescriptor(VulkanicRenderTargetDescriptor descriptor) {
        if (!TRACE_VULKAN_TERRAIN_RENDER_TARGETS) {
            return;
        }

        LOGGER.info(
            "Sodium Vulkan terrain render target descriptor label={} colors={} colorTextureIds={} depth={} depthTextureId={} explicitExtent={}x{}",
            descriptor.label().get(),
            descriptor.colorAttachments().size(),
            descriptor.colorAttachments()
                .stream()
                .map(attachment -> Integer.toString(attachment.textureId()))
                .collect(Collectors.joining(",")),
            descriptor.hasDepthAttachment(),
            descriptor.depthAttachment() != null ? descriptor.depthAttachment().textureId() : 0,
            descriptor.width(),
            descriptor.height()
        );
    }

    @Override
    public void endFrame() {
    }

    private GpuBufferSlice writeChunkParams(CommandEncoder commandEncoder, FogParameters fogParameters) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var textureAtlas = (net.minecraft.client.renderer.texture.TextureAtlas) net.minecraft.client.Minecraft.getInstance()
                .getTextureManager()
                .getTexture(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS);

            double subTexelPrecision = (1 << RenderDevice.instance().getSubTexelPrecisionBits());
            double subTexelOffset = 1.0f / ChunkMeshFormats.COMPACT_TEXTURE_MAX_VALUE;
            float shrinkX = (float) (subTexelOffset - (((1.0D / textureAtlas.width) / subTexelPrecision)));
            float shrinkY = (float) (subTexelOffset - (((1.0D / textureAtlas.height) / subTexelPrecision)));

            commandEncoder.writeToBuffer(
                this.sodiumChunkParamsBuffer.slice(),
                Std140Builder.onStack(stack, SODIUM_CHUNK_PARAMS_UBO_SIZE)
                    .putVec2(shrinkX, shrinkY)
                    .putVec4(fogParameters.red(), fogParameters.green(), fogParameters.blue(), fogParameters.alpha())
                    .putVec2(fogParameters.environmentalStart(), fogParameters.environmentalEnd())
                    .putVec2(fogParameters.renderStart(), fogParameters.renderEnd())
                    .get()
            );
        }

        return this.sodiumChunkParamsBuffer.slice();
    }

    private GpuBufferSlice writeDynamicTransforms(Matrix4fc modelViewMatrix, float modelOffsetX, float modelOffsetY, float modelOffsetZ) {
        return VulkanicAPI.getDynamicUniforms().writeTransform(
            modelViewMatrix,
            new Vector4f(1.0F, 1.0F, 1.0F, 1.0F),
            new Vector3f(modelOffsetX, modelOffsetY, modelOffsetZ),
            VulkanicAPI.getTextureMatrix(),
            1.0F
        );
    }

    private void logVulkanRenderProbe(
        TerrainRenderPass terrainPass,
        int preparedRegionCount,
        int totalBatchDrawCommands,
        int submittedDrawCommands,
        long submittedIndexCount,
        double nearestRegionDistanceSq,
        int nearestRegionOriginX,
        int nearestRegionOriginY,
        int nearestRegionOriginZ,
        float nearestModelOffsetX,
        float nearestModelOffsetY,
        float nearestModelOffsetZ,
        int nearestBatchSize
    ) {
        if (vulkanRenderProbeCount >= MAX_VULKAN_RENDER_PROBES) {
            return;
        }

        vulkanRenderProbeCount++;
        LOGGER.info(
            "Sodium Vulkan chunk render probe#{} pass={} fragmentDiscard={} translucent={} preparedRegions={} batchDraws={} submittedDraws={} submittedIndices={} nearestRegionOrigin=({}, {}, {}) nearestModelOffset=({}, {}, {}) nearestRegionDistanceSq={} nearestBatchSize={}",
            vulkanRenderProbeCount,
            terrainPass.getPipeline().getLocation(),
            terrainPass.supportsFragmentDiscard(),
            terrainPass.isTranslucent(),
            preparedRegionCount,
            totalBatchDrawCommands,
            submittedDrawCommands,
            submittedIndexCount,
            nearestRegionOriginX,
            nearestRegionOriginY,
            nearestRegionOriginZ,
            nearestModelOffsetX,
            nearestModelOffsetY,
            nearestModelOffsetZ,
            nearestRegionDistanceSq,
            nearestBatchSize
        );
    }

    private record PreparedRegionDraw(
        MultiDrawBatch batch,
        GpuBuffer vertexBuffer,
        GpuBuffer indexBuffer,
        RenderRegion region,
        GpuBufferSlice transforms
    ) {
    }

    private static VulkanicAPI.ShaderInputParityScope beginSodiumTerrainParityScope(
        String source,
        TerrainRenderPass terrainPass,
        RenderRegion region,
        MultiDrawBatch batch
    ) {
        return VulkanicAPI.beginShaderInputParitySemanticDraw(
            source,
            "sodium-terrain",
            sodiumTerrainPassLabel(terrainPass, region),
            terrainPass.getPipeline(),
            null,
            sodiumTerrainMaterial(terrainPass),
            sodiumTerrainOutput(terrainPass),
            true,
            0,
            0,
            0,
            sodiumTerrainIndexCount(batch),
            Math.max(1, batch.size),
            0
        );
    }

    private static String sodiumTerrainPassLabel(TerrainRenderPass terrainPass, RenderRegion region) {
        return String.format(
            "terrain:%s:region=%d,%d,%d:origin=%d,%d,%d",
            sodiumTerrainKind(terrainPass),
            region.getX(),
            region.getY(),
            region.getZ(),
            region.getOriginX(),
            region.getOriginY(),
            region.getOriginZ()
        );
    }

    private static String sodiumTerrainMaterial(TerrainRenderPass terrainPass) {
        return "terrain:" + sodiumTerrainKind(terrainPass) + ":" + terrainPass.getPipeline().getLocation();
    }

    private static String sodiumTerrainOutput(TerrainRenderPass terrainPass) {
        return terrainPass.isTranslucent() && net.minecraft.client.Minecraft.useShaderTransparency()
            ? "terrain-translucent-target"
            : "terrain-main-target";
    }

    private static String sodiumTerrainKind(TerrainRenderPass terrainPass) {
        if (terrainPass.isTranslucent()) {
            return "translucent";
        }
        return terrainPass.supportsFragmentDiscard() ? "cutout" : "solid";
    }

    private static int sodiumTerrainIndexCount(MultiDrawBatch batch) {
        long total = 0L;
        for (int drawIndex = 0; drawIndex < batch.size; drawIndex++) {
            int indexCount = MemoryUtil.memGetInt(batch.pElementCount + ((long) drawIndex << 2));
            if (indexCount > 0) {
                total += indexCount;
            }
        }
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    private static void fillCommandBuffer(MultiDrawBatch batch,
                                          RenderRegion renderRegion,
                                          SectionRenderDataStorage renderDataStorage,
                                          ChunkRenderList renderList,
                                          CameraTransform camera,
                                          TerrainRenderPass pass,
                                          boolean useBlockFaceCulling,
                                          boolean useIndexedTessellation) {
        renderDataStorage.fillDrawCommandBuffer(batch, renderRegion, renderList, camera, pass.isTranslucent(),
                useBlockFaceCulling, useIndexedTessellation);
    }

    private static void setModelMatrixUniforms(ChunkShaderInterface shader, RenderRegion region, CameraTransform camera) {
        float x = getCameraTranslation(region.getOriginX(), camera.intX, camera.fracX);
        float y = getCameraTranslation(region.getOriginY(), camera.intY, camera.fracY);
        float z = getCameraTranslation(region.getOriginZ(), camera.intZ, camera.fracZ);

        shader.setRegionOffset(x, y, z);
    }

    private static float getCameraTranslation(int chunkBlockPos, int cameraBlockPos, float cameraPos) {
        return (chunkBlockPos - cameraBlockPos) - cameraPos;
    }

    private RenderTessellation prepareTessellation(CommandList commandList, RenderRegion region) {
        var resources = region.getResources();

        RenderTessellation tessellation = resources.getTessellation();
        if (tessellation == null) {
            tessellation = this.createRegionTessellation(commandList, resources, true);
            resources.updateTessellation(commandList, tessellation);
        }

        return tessellation;
    }

    private RenderTessellation prepareIndexedTessellation(CommandList commandList, RenderRegion region) {
        var resources = region.getResources();

        RenderTessellation tessellation = resources.getIndexedTessellation();
        if (tessellation == null) {
            // Iris: From MixinDefaultChunkRenderer - don't use shared index buffer in shadow pass
            boolean useSharedIndexBuffer = net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered() 
                ? false 
                : false;
            tessellation = this.createRegionTessellation(commandList, resources, useSharedIndexBuffer);
            resources.updateIndexedTessellation(commandList, tessellation);
        }

        return tessellation;
    }

    private RenderTessellation createRegionTessellation(CommandList commandList, RenderRegion.DeviceResources resources, boolean useSharedIndexBuffer) {
        RenderTessellationBinding vertexBinding;
        RenderTessellationBinding indexBinding;

        if (VulkanicAPI.isVulkanBackendInitializedAndSelected()) {
            vertexBinding = RenderTessellationBinding.forVertexBuffer(
                    resources.getGeometryGpuBuffer(GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST),
                    this.vertexFormat.getShaderBindings());
            indexBinding = RenderTessellationBinding.forElementBuffer(useSharedIndexBuffer
                    ? this.sharedIndexBuffer.gpuBufferView(GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST)
                    : resources.getIndexGpuBuffer(GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST));
        } else {
            vertexBinding = RenderTessellationBinding.forVertexBuffer(resources.getGeometryBuffer(), this.vertexFormat.getShaderBindings());
            indexBinding = RenderTessellationBinding.forElementBuffer(useSharedIndexBuffer
                    ? this.sharedIndexBuffer.getBufferObject()
                    : resources.getIndexBuffer());
        }

        return commandList.createTessellation(VulkanicPrimitiveMode.TRIANGLES, new RenderTessellationBinding[] {
                vertexBinding,
                indexBinding
        });
    }

    private static void executeDrawBatch(CommandList commandList, RenderTessellation tessellation, MultiDrawBatch batch) {
        try (DrawCommandList drawCommandList = commandList.beginTessellating(tessellation)) {
            drawCommandList.multiDrawElementsBaseVertex(batch, VulkanicIndexType.INT);
        }
    }

    @Override
    public void delete(CommandList commandList) {
        super.delete(commandList);

        this.sharedIndexBuffer.delete(commandList);
        this.sodiumChunkParamsBuffer.close();
    }
}

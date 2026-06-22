package net.vulkanic.backends.vulkan;

import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.buffers.GpuFence;
import net.blaze3d.buffers.GpuBufferSlice;
import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.platform.NativeImage;
import net.blaze3d.systems.CommandEncoder;
import net.blaze3d.systems.RenderPass;
import net.blaze3d.textures.GpuTexture;
import net.blaze3d.textures.GpuTextureView;
import net.blaze3d.vertex.VertexFormat;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.pbr.TextureTracker;
import net.sodium.client.render.chunk.shader.SharedChunkProgramOverrides;
import net.sodium.client.render.chunk.shader.VulkanTerrainPipelineDiagnostics;
import net.vulkanic.CommandContext;
import net.vulkanic.PipelineDescriptor;
import net.vulkanic.PipelineHandle;
import net.vulkanic.PipelineResourceBindings;
import net.vulkanic.PipelineResourcePlanner;
import net.vulkanic.RenderPassResourceBinder;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicBuffer;
import net.vulkanic.VulkanicBufferSlice;
import net.vulkanic.VulkanicIndexType;
import net.vulkanic.VulkanicRenderPass;
import net.vulkanic.VulkanicRenderTargetDescriptor;
import net.vulkanic.VulkanicTexture;
import net.vulkanic.VulkanicTextureView;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;

/**
 * Native Vulkan command encoder for migrated render-pass slices.
 *
 * <p>This deliberately remains narrower than the compatibility
 * {@link net.blaze3d.opengl.GlCommandEncoder}. It covers Vulkan-backed render passes whose
 * attachments, pipeline handles, and resource bindings are already expressed through Vulkanic
 * abstractions. More OpenGL-shaped or Iris framebuffer-specialized paths can continue to use the
 * compatibility encoder until they have equivalent native state coverage.</p>
 */
class VulkanNativeCommandEncoder implements CommandEncoder {
    enum ResourceMode {
        GENERAL,
        TERRAIN
    }

    private final VulkanBackend backend;
    private final ResourceMode resourceMode;
    private boolean inRenderPass;

    VulkanNativeCommandEncoder(VulkanBackend backend) {
        this(backend, ResourceMode.GENERAL);
    }

    VulkanNativeCommandEncoder(VulkanBackend backend, ResourceMode resourceMode) {
        this.backend = backend;
        this.resourceMode = resourceMode;
    }

    @Override
    public RenderPass createRenderPass(Supplier<String> label, GpuTextureView colorTextureView, OptionalInt clearColor) {
        return this.createRenderPass(label, colorTextureView, clearColor, null, OptionalDouble.empty());
    }

    @Override
    public RenderPass createRenderPass(
        Supplier<String> label,
        GpuTextureView colorTextureView,
        OptionalInt clearColor,
        @Nullable GpuTextureView depthTextureView,
        OptionalDouble clearDepth
    ) {
        this.ensureNoRenderPass();
        CommandContext ctx = this.backend.beginCommandBuffer();
        VulkanicTextureView colorView = this.createTextureView(colorTextureView);
        VulkanicTextureView depthView = depthTextureView != null ? this.createTextureView(depthTextureView) : null;
        boolean renderPassStarted = false;
        try {
            VulkanicRenderPass pass = this.backend.beginRenderPass(ctx, label, colorView, clearColor, depthView, clearDepth);
            renderPassStarted = true;
            this.inRenderPass = true;
            return new NativeRenderPass(ctx, pass, 0, null, colorView, depthView);
        } finally {
            if (!renderPassStarted) {
                colorView.close();
                if (depthView != null) {
                    depthView.close();
                }
            }
        }
    }

    @Override
    public RenderPass createRenderPass(Supplier<String> label, int framebuffer, boolean hasDepthTexture) {
        this.ensureNoRenderPass();
        CommandContext ctx = this.backend.beginCommandBuffer();
        VulkanicRenderPass pass = this.backend.beginRenderPass(ctx, label, framebuffer);
        this.inRenderPass = true;
        return new NativeRenderPass(ctx, pass, framebuffer, null, null, null);
    }

    @Override
    public RenderPass createRenderPass(VulkanicRenderTargetDescriptor descriptor) {
        this.ensureNoRenderPass();
        CommandContext ctx = this.backend.beginCommandBuffer();
        VulkanicRenderPass pass = this.backend.beginRenderPass(ctx, descriptor);
        this.inRenderPass = true;
        return new NativeRenderPass(ctx, pass, 0, descriptor, null, null);
    }

    @Override
    public void writeToBuffer(GpuBufferSlice slice, ByteBuffer data) {
        this.ensureNoRenderPass();
        int handle = this.requireBufferHandle(slice.buffer(), "writeToBuffer");
        VulkanicAPI.namedBufferSubDataDSA(VulkanicAPI.getCommandContext(), handle, slice.offset(), data);
    }

    @Override
    public GpuBuffer.MappedView mapBuffer(GpuBuffer buffer, boolean read, boolean write) {
        return this.mapBuffer(buffer.slice(), read, write);
    }

    @Override
    public GpuBuffer.MappedView mapBuffer(GpuBufferSlice slice, boolean read, boolean write) {
        this.ensureNoRenderPass();
        if (slice.buffer().isClosed()) {
            throw new IllegalStateException("Buffer already closed");
        }
        if (read && (slice.buffer().usage() & GpuBuffer.USAGE_MAP_READ) == 0) {
            throw new IllegalStateException("Buffer is not readable");
        }
        if (write && (slice.buffer().usage() & GpuBuffer.USAGE_MAP_WRITE) == 0) {
            throw new IllegalStateException("Buffer is not writable");
        }

        int access = 0;
        if (read) {
            access |= 1;
        }
        if (write) {
            access |= 34;
        }

        int handle = this.requireBufferHandle(slice.buffer(), "mapBuffer");
        ByteBuffer mapped = VulkanicAPI.mapNamedBufferRangeDSA(
            VulkanicAPI.getCommandContext(),
            handle,
            slice.offset(),
            slice.length(),
            access
        );
        if (mapped == null) {
            throw new IllegalStateException("Unable to map Vulkan buffer");
        }

        return new GpuBuffer.MappedView() {
            private boolean closed;

            @Override
            public ByteBuffer data() {
                return mapped;
            }

            @Override
            public void close() {
                if (!this.closed) {
                    this.closed = true;
                    VulkanicAPI.unmapNamedBufferDSA(VulkanicAPI.getCommandContext(), handle);
                }
            }
        };
    }

    @Override
    public void copyToBuffer(GpuBufferSlice source, GpuBufferSlice target) {
        this.unsupported("copyToBuffer");
    }

    @Override
    public void clearColorTexture(GpuTexture texture, int clearColor) {
        this.unsupported("clearColorTexture");
    }

    @Override
    public void clearColorAndDepthTextures(GpuTexture colorTexture, int clearColor, GpuTexture depthTexture, double clearDepth) {
        this.unsupported("clearColorAndDepthTextures");
    }

    @Override
    public void clearColorAndDepthTextures(GpuTexture colorTexture, int clearColor, GpuTexture depthTexture, double clearDepth, int x, int y, int width, int height) {
        this.unsupported("clearColorAndDepthTextures(region)");
    }

    @Override
    public void clearDepthTexture(GpuTexture texture, double clearDepth) {
        this.unsupported("clearDepthTexture");
    }

    @Override
    public void writeToTexture(GpuTexture texture, NativeImage image) {
        this.unsupported("writeToTexture");
    }

    @Override
    public void writeToTexture(GpuTexture texture, NativeImage image, int mipLevel, int sourceX, int sourceY, int targetX, int targetY, int width, int height, int depth) {
        this.unsupported("writeToTexture(region)");
    }

    @Override
    public void writeToTexture(GpuTexture texture, ByteBuffer data, NativeImage.Format format, int mipLevel, int targetX, int targetY, int width, int height, int depth) {
        this.unsupported("writeToTexture(buffer)");
    }

    @Override
    public void copyTextureToBuffer(GpuTexture texture, GpuBuffer buffer, int mipLevel, Runnable callback, int alignment) {
        this.unsupported("copyTextureToBuffer");
    }

    @Override
    public void copyTextureToBuffer(GpuTexture texture, GpuBuffer buffer, int mipLevel, Runnable callback, int x, int y, int width, int height, int alignment) {
        this.unsupported("copyTextureToBuffer(region)");
    }

    @Override
    public void copyTextureToTexture(GpuTexture source, GpuTexture target, int sourceMip, int targetMip, int sourceX, int sourceY, int targetX, int targetY, int width) {
        this.unsupported("copyTextureToTexture");
    }

    @Override
    public void applyPipelineState(RenderPipeline renderPipeline) {
    }

    @Override
    public void invalidateCachedProgramBinding() {
    }

    @Override
    public void presentTexture(GpuTextureView textureView) {
        this.unsupported("presentTexture");
    }

    @Override
    public GpuFence createFence() {
        this.unsupported("createFence");
        throw new AssertionError("unreachable");
    }

    private VulkanicTextureView createTextureView(GpuTextureView view) {
        if (!(view.texture() instanceof VulkanicTexture texture)) {
            throw new IllegalArgumentException("Render-pass texture is not VulkanicTexture: " + view.texture().getClass().getName());
        }
        return VulkanicAPI.createManagedTextureView(texture, view.baseMipLevel(), view.mipLevels());
    }

    private int requireBufferHandle(GpuBuffer buffer, String operation) {
        int handle = VulkanicAPI.getBufferHandle(buffer);
        if (handle == 0) {
            throw new IllegalArgumentException(operation + " requires a Vulkan-resolvable buffer, got " + buffer.getClass().getName());
        }
        return handle;
    }

    private void ensureNoRenderPass() {
        if (this.inRenderPass) {
            throw new IllegalStateException("Close the existing Vulkan render pass before issuing another encoder command");
        }
    }

    private void unsupported(String operation) {
        throw new UnsupportedOperationException(
            "Vulkan native command encoder does not support " + operation
                + "; migrate that callsite through an explicit native Vulkan encoder slice first."
        );
    }

    private final class NativeRenderPass implements RenderPass, RenderPassResourceBinder {
        private final CommandContext ctx;
        private final VulkanicRenderPass pass;
        private final int framebuffer;
        @Nullable
        private final VulkanicRenderTargetDescriptor renderTargetDescriptor;
        @Nullable
        private final VulkanicTextureView colorView;
        @Nullable
        private final VulkanicTextureView depthView;
        private final Map<String, VulkanicTextureView> samplers = new HashMap<>();
        private final Map<String, Integer> samplerUnits = new HashMap<>();
        private final Map<String, VulkanicBufferSlice> uniforms = new HashMap<>();
        @Nullable
        private RenderPipeline renderPipeline;
        @Nullable
        private PipelineDescriptor pipelineDescriptor;
        @Nullable
        private GpuBuffer indexBuffer;
        private VertexFormat.IndexType indexType = VertexFormat.IndexType.INT;
        private boolean closed;
        private int debugGroups;

        private NativeRenderPass(
            CommandContext ctx,
            VulkanicRenderPass pass,
            int framebuffer,
            @Nullable VulkanicRenderTargetDescriptor renderTargetDescriptor,
            @Nullable VulkanicTextureView colorView,
            @Nullable VulkanicTextureView depthView
        ) {
            this.ctx = ctx;
            this.pass = pass;
            this.framebuffer = framebuffer;
            this.renderTargetDescriptor = renderTargetDescriptor;
            this.colorView = colorView;
            this.depthView = depthView;
        }

        @Override
        public void pushDebugGroup(Supplier<String> label) {
            this.checkOpen();
            this.debugGroups++;
        }

        @Override
        public void popDebugGroup() {
            this.checkOpen();
            if (this.debugGroups == 0) {
                throw new IllegalStateException("Can't pop more debug groups than were pushed");
            }
            this.debugGroups--;
        }

        @Override
        public void setPipeline(RenderPipeline renderPipeline) {
            this.checkOpen();
            this.renderPipeline = renderPipeline;
            this.pipelineDescriptor = VulkanNativeCommandEncoder.this.backend.resolvePrecompiledPipelineDescriptor(renderPipeline);
            if (this.pipelineDescriptor == null) {
                this.pipelineDescriptor = PipelineDescriptor.fromRenderPipeline(renderPipeline);
            }
        }

        @Override
        public void bindSampler(String name, @Nullable GpuTextureView view) {
            Integer samplerUnit = parseSamplerIndex(name);
            this.bindSampler(name, view, samplerUnit != null ? samplerUnit : -1);
        }

        @Override
        public void bindSampler(String name, @Nullable GpuTextureView view, int textureUnit) {
            this.checkOpen();
            this.closeSampler(name);
            if (view == null) {
                this.samplerUnits.remove(name);
                return;
            }

            if (!(view.texture() instanceof VulkanicTexture texture)) {
                throw new IllegalArgumentException("Sampler " + name + " is not backed by a VulkanicTexture");
            }

            if (textureUnit >= 0) {
                this.samplerUnits.put(name, textureUnit);
            } else {
                this.samplerUnits.remove(name);
            }
            this.samplers.put(name, VulkanicAPI.createManagedTextureView(texture, view.baseMipLevel(), view.mipLevels()));
        }

        @Override
        public boolean bindLegacySampler(String name, int textureId, int textureUnit) {
            this.checkOpen();
            this.closeSampler(name);
            if (textureId <= 0) {
                this.samplerUnits.remove(name);
                return false;
            }

            VulkanicTextureView view = VulkanicAPI.createManagedLegacyTextureView(textureId);
            if (view == null) {
                this.samplerUnits.remove(name);
                return false;
            }

            if (textureUnit >= 0) {
                this.samplerUnits.put(name, textureUnit);
            } else {
                this.samplerUnits.remove(name);
            }
            this.samplers.put(name, view);
            return true;
        }

        @Override
        public void setUniform(String name, GpuBuffer buffer) {
            this.setUniform(name, buffer.slice());
        }

        @Override
        public void setUniform(String name, GpuBufferSlice slice) {
            this.checkOpen();
            this.uniforms.put(
                name,
                new VulkanicBufferSlice(
                    VulkanicAPI.resolveVulkanicBuffer(slice.buffer()),
                    slice.offset(),
                    slice.length()
                )
            );
        }

        @Override
        public void enableScissor(int x, int y, int width, int height) {
            this.checkOpen();
            VulkanicAPI.setScissorTestEnabled(this.ctx, true);
            VulkanicAPI.setDynamicScissor(this.ctx, x, y, width, height);
        }

        @Override
        public void disableScissor() {
            this.checkOpen();
            VulkanicAPI.setScissorTestEnabled(this.ctx, false);
        }

        @Override
        public void setVertexBuffer(int slot, GpuBuffer buffer) {
            this.checkOpen();
            VulkanicBuffer vulkanBuffer = VulkanicAPI.resolveVulkanicBuffer(buffer);
            this.pass.setVertexBuffer(slot, vulkanBuffer);
        }

        @Override
        public void setIndexBuffer(GpuBuffer buffer, VertexFormat.IndexType indexType) {
            this.checkOpen();
            this.indexBuffer = buffer;
            this.indexType = indexType;
            if (buffer != null) {
                this.pass.setIndexBuffer(VulkanicAPI.resolveVulkanicBuffer(buffer), toVulkanicIndexType(indexType));
            }
        }

        @Override
        public void drawIndexed(int baseVertex, int firstIndex, int indexCount, int instanceCount) {
            this.checkOpen();
            if (this.indexBuffer == null) {
                throw new IllegalStateException("Can't draw indexed without an index buffer");
            }
            this.bindPipelineAndResources();
            this.pass.drawIndexed(firstIndex, indexCount, baseVertex, instanceCount);
        }

        @Override
        public <T> void drawMultipleIndexed(
            Collection<RenderPass.Draw<T>> draws,
            @Nullable GpuBuffer indexBuffer,
            @Nullable VertexFormat.IndexType indexType,
            Collection<String> dynamicUniforms,
            T uploaderState
        ) {
            this.checkOpen();
            for (RenderPass.Draw<T> draw : draws) {
                GpuBuffer drawIndexBuffer = indexBuffer != null ? indexBuffer : draw.indexBuffer();
                VertexFormat.IndexType drawIndexType = indexType != null ? indexType : draw.indexType();
                if (drawIndexBuffer == null || drawIndexType == null) {
                    throw new IllegalStateException("drawMultipleIndexed requires an index buffer and index type");
                }

                this.setIndexBuffer(drawIndexBuffer, drawIndexType);
                this.setVertexBuffer(draw.slot(), draw.vertexBuffer());
                if (draw.uniformUploaderConsumer() != null) {
                    draw.uniformUploaderConsumer().accept(uploaderState, (name, slice) -> this.setUniform(name, slice));
                }
                this.drawIndexed(0, draw.firstIndex(), draw.indexCount(), 1);
            }
        }

        @Override
        public void draw(int firstVertex, int vertexCount) {
            this.checkOpen();
            this.bindPipelineAndResources();
            this.pass.draw(firstVertex, vertexCount);
        }

        @Override
        public void close() {
            if (this.closed) {
                return;
            }
            if (this.debugGroups != 0) {
                throw new IllegalStateException("Render pass had debug groups left open");
            }
            this.closed = true;
            try {
                this.pass.close();
                VulkanNativeCommandEncoder.this.backend.submitCommandBuffer(this.ctx);
            } finally {
                VulkanNativeCommandEncoder.this.inRenderPass = false;
                for (VulkanicTextureView view : this.samplers.values()) {
                    view.close();
                }
                this.samplers.clear();
                if (this.colorView != null) {
                    this.colorView.close();
                }
                if (this.depthView != null) {
                    this.depthView.close();
                }
            }
        }

        private void bindPipelineAndResources() {
            RenderPipeline pipeline = this.requirePipeline();
            PipelineDescriptor baseDescriptor = this.requirePipelineDescriptor();
            PipelineDescriptor selectedDescriptor = this.selectDescriptor(pipeline, baseDescriptor);
            PipelineResourcePlanner.Plan submission = this.buildResourceBindings(selectedDescriptor);
            if (submission == null) {
                throw new IllegalStateException("No Vulkan resource bindings available for pipeline " + pipeline.getLocation());
            }

            PipelineHandle handle = this.renderTargetDescriptor != null
                ? VulkanNativeCommandEncoder.this.backend.resolvePipelineHandle(pipeline, submission.descriptor(), this.renderTargetDescriptor)
                : this.framebuffer != 0
                    ? VulkanNativeCommandEncoder.this.backend.resolvePipelineHandle(pipeline, submission.descriptor(), this.framebuffer)
                    : this.colorView != null
                        ? VulkanNativeCommandEncoder.this.backend.resolvePipelineHandle(
                            pipeline,
                            submission.descriptor(),
                            this.colorView,
                            this.depthView
                        )
                        : VulkanNativeCommandEncoder.this.backend.resolvePipelineHandle(pipeline, submission.descriptor());
            if (handle == null || !handle.isValid()) {
                throw new IllegalStateException("Unable to resolve native Vulkan pipeline handle for " + pipeline.getLocation());
            }

            this.pass.setPipeline(handle);
            VulkanNativeCommandEncoder.this.backend.bindPipelineResources(this.ctx, handle, submission.descriptor(), submission.bindings());
        }

        private PipelineDescriptor selectDescriptor(RenderPipeline pipeline, PipelineDescriptor baseDescriptor) {
            if (VulkanNativeCommandEncoder.this.resourceMode != ResourceMode.TERRAIN) {
                return baseDescriptor;
            }

            int activeProgram = SharedChunkProgramOverrides.activeProgramHandle(pipeline);
            if (activeProgram <= 0 || !SharedChunkProgramOverrides.isTracked(pipeline)) {
                return baseDescriptor;
            }

            try {
                java.util.Set<String> bindableSamplers = SharedChunkProgramOverrides.bindableSamplers(pipeline);
                PipelineDescriptor liveDescriptor = VulkanicAPI.createLiveProgramPipelineDescriptor(
                    this.ctx,
                    baseDescriptor,
                    activeProgram,
                    binding -> switch (binding.type()) {
                        case SAMPLER, COMPARISON_SAMPLER -> bindableSamplers.contains(binding.name());
                        case UNIFORM_BUFFER, TEXEL_BUFFER -> true;
                    }
                );
                if (liveDescriptor != null && liveDescriptor.hasSpirvModules()) {
                    return liveDescriptor;
                }
            } catch (RuntimeException ignored) {
            }

            return baseDescriptor;
        }

        @Nullable
        private PipelineResourcePlanner.Plan buildResourceBindings(PipelineDescriptor descriptor) {
            PipelineResourcePlanner.Plan submission = PipelineResourcePlanner.buildPlan(
                descriptor,
                binding -> {
                    switch (binding.type()) {
                        case SAMPLER, COMPARISON_SAMPLER -> {
                            VulkanicTextureView view = this.getSamplerView(binding.name());
                            if (view != null) {
                                int unit = this.resolveSamplerUnit(binding);
                                Integer samplerObject = currentBoundSamplerObject(unit);
                                return PipelineResourcePlanner.ResolvedResource.sampler(
                                    new PipelineResourceBindings.SamplerBinding(unit, samplerObject, view)
                                );
                            }
                        }
                        case UNIFORM_BUFFER -> {
                            VulkanicBufferSlice slice = this.uniforms.get(binding.name());
                            if (slice == null
                                && VulkanNativeCommandEncoder.this.resourceMode == ResourceMode.TERRAIN
                                && VulkanicAPI.generatedStandaloneUniformBlockName().equals(binding.name())) {
                                int activeProgram = this.renderPipeline != null
                                    ? SharedChunkProgramOverrides.activeProgramHandle(this.renderPipeline)
                                    : -1;
                                if (activeProgram > 0) {
                                    slice = VulkanicAPI.getStandaloneUniformBufferSlice(this.ctx, activeProgram);
                                }
                            }
                            if (slice != null) {
                                return PipelineResourcePlanner.ResolvedResource.uniformBuffer(slice);
                            }
                        }
                        case TEXEL_BUFFER -> {
                        }
                    }
                    return null;
                },
                resourcePlannerOptions()
                    .missingResourceDescriber(
                        VulkanNativeCommandEncoder.this.resourceMode == ResourceMode.TERRAIN && VulkanTerrainPipelineDiagnostics.enabled()
                            ? PipelineResourcePlanner.MissingResourceDescriber.DEFAULT
                            : PipelineResourcePlanner.MissingResourceDescriber.NONE
                    )
            );

            if (submission != null
                && this.renderPipeline != null
                && VulkanNativeCommandEncoder.this.resourceMode == ResourceMode.TERRAIN) {
                VulkanTerrainPipelineDiagnostics.logResourceSubmission(
                    this.renderPipeline,
                    descriptor,
                    submission.descriptor(),
                    submission.missingResources()
                );
            }

            return submission;
        }

        @Nullable
        private VulkanicTextureView getSamplerView(String name) {
            VulkanicTextureView view = this.samplers.get(name);
            if (view != null) {
                return view;
            }

            GpuTextureView recovered = this.recoverSamplerView(name);
            if (recovered == null) {
                return null;
            }

            this.bindSampler(name, recovered);
            return this.samplers.get(name);
        }

        private int resolveSamplerUnit(PipelineDescriptor.ResourceBinding binding) {
            Integer boundUnit = this.samplerUnits.get(binding.name());
            if (boundUnit != null) {
                return boundUnit;
            }

            Integer parsedUnit = parseSamplerIndex(binding.name());
            return parsedUnit != null ? parsedUnit : binding.binding();
        }

        @Nullable
        private GpuTextureView recoverSamplerView(String name) {
            Integer samplerIndex = parseSamplerIndex(name);
            if (samplerIndex == null) {
                return null;
            }

            GpuTextureView shaderTexture = TextureTracker.INSTANCE.getShaderTexture(samplerIndex);
            if (shaderTexture != null) {
                return shaderTexture;
            }

            int textureId = IrisRenderSystem.getTextureBinding(samplerIndex);
            if (textureId == 0) {
                return null;
            }

            return TextureTracker.INSTANCE.getTextureView(textureId);
        }

        private RenderPipeline requirePipeline() {
            if (this.renderPipeline == null) {
                throw new IllegalStateException("Can't draw without a render pipeline");
            }
            return this.renderPipeline;
        }

        private PipelineDescriptor requirePipelineDescriptor() {
            if (this.pipelineDescriptor == null) {
                throw new IllegalStateException("No pipeline descriptor is available for render pass");
            }
            return this.pipelineDescriptor;
        }

        private void closeSampler(String name) {
            VulkanicTextureView previous = this.samplers.remove(name);
            if (previous != null) {
                previous.close();
            }
        }

        private void unsupported(String operation) {
            throw new UnsupportedOperationException("Vulkan native render pass does not support " + operation);
        }

        private void checkOpen() {
            if (this.closed) {
                throw new IllegalStateException("Can't use a closed render pass");
            }
        }
    }

    private PipelineResourcePlanner.Options resourcePlannerOptions() {
        PipelineResourcePlanner.Options options = PipelineResourcePlanner.options();
        if (this.resourceMode == ResourceMode.GENERAL) {
            options = options.requireAtLeastOneBinding(false);
        }
        return options;
    }

    private static VulkanicIndexType toVulkanicIndexType(VertexFormat.IndexType type) {
        return switch (type) {
            case SHORT -> VulkanicIndexType.SHORT;
            case INT -> VulkanicIndexType.INT;
        };
    }

    @Nullable
    private static Integer currentBoundSamplerObject(int samplerUnit) {
        int samplerObject = IrisRenderSystem.getBoundSamplerOnUnit(samplerUnit);
        return samplerObject > 0 ? samplerObject : null;
    }

    @Nullable
    private static Integer parseSamplerIndex(String samplerName) {
        if (samplerName == null || !samplerName.startsWith("Sampler")) {
            return null;
        }

        try {
            return Integer.parseInt(samplerName.substring("Sampler".length()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}

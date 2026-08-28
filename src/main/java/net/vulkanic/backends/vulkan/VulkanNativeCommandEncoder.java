package net.vulkanic.backends.vulkan;

import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.buffers.GpuFence;
import net.blaze3d.buffers.GpuBufferSlice;
import net.blaze3d.opengl.GlConst;
import net.blaze3d.opengl.GlProgram;
import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.platform.NativeImage;
import net.blaze3d.systems.CommandEncoder;
import net.blaze3d.systems.RenderPass;
import net.blaze3d.textures.GpuTexture;
import net.blaze3d.textures.GpuTextureView;
import net.blaze3d.vertex.VertexFormat;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.mixinterface.CustomPass;
import net.irisshaders.iris.pbr.TextureTracker;
import net.irisshaders.iris.pipeline.IrisPipelines;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.IrisProgram;
import net.irisshaders.iris.pipeline.programs.ShaderKey;
import net.irisshaders.iris.vertices.ImmediateState;
import net.sodium.client.render.chunk.shader.SharedChunkProgramOverrides;
import net.sodium.client.render.chunk.shader.VulkanTerrainPipelineDiagnostics;
import net.minecraft.util.ARGB;
import net.logging.LogUtils;
import net.vulkanic.CommandContext;
import net.vulkanic.PipelineDescriptor;
import net.vulkanic.PipelineHandle;
import net.vulkanic.PipelineResourceBindings;
import net.vulkanic.PipelineResourcePlanner;
import net.vulkanic.RenderPassResourceBinder;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicBuffer;
import net.vulkanic.VulkanicBufferSlice;
import net.vulkanic.VulkanicCoreAPI;
import net.vulkanic.VulkanicDrawStateDiagnostics;
import net.vulkanic.VulkanicDrawStateSnapshot;
import net.vulkanic.VulkanicIndexType;
import net.vulkanic.VulkanicPipelineResourceResolver;
import net.vulkanic.VulkanicRenderPass;
import net.vulkanic.VulkanicRenderTargetDescriptor;
import net.vulkanic.VulkanicTexture;
import net.vulkanic.VulkanicTextureView;
import net.vulkanic.bridge.RustGalVulkanWholeFrameMode;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;
import org.slf4j.Logger;

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
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean DEBUG_DESCRIPTOR_BINDINGS =
        Boolean.getBoolean("mattmc.vulkan.debugDescriptorBindingSeam");
    private static final int MAX_IRIS_PROGRAM_LIVE_DESCRIPTOR_CACHE_ENTRIES = 128;
    private static final int MAX_RENDER_PASS_SAMPLERS = 128;
    private static final int MAX_RENDER_PASS_UNIFORMS = 256;
    private static final int MAX_RENDER_PASS_IRIS_PROGRAM_STATES = 128;
    private static final Map<IrisProgramLiveDescriptorKey, PipelineDescriptor> IRIS_PROGRAM_LIVE_DESCRIPTOR_CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static int debugCustomPassLogs;
    private static final java.util.Set<String> WARNED_INCOMPLETE_CUSTOM_PASS_KEYS =
        java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    enum ResourceMode {
        GENERAL,
        TERRAIN
    }

    private record CachedResourceSubmission(
        PipelineHandle pipelineHandle,
        PipelineDescriptor descriptor,
        PipelineResourceBindings bindings,
        long resourceStateGeneration,
        boolean customPass
    ) {}

    private record IrisProgramLiveDescriptorKey(RenderPipeline renderPipeline, PipelineDescriptor baseDescriptor, int programHandle) {
        @Override
        public boolean equals(Object object) {
            return object instanceof IrisProgramLiveDescriptorKey other
                && this.renderPipeline == other.renderPipeline
                && this.programHandle == other.programHandle
                && Objects.equals(this.baseDescriptor, other.baseDescriptor);
        }

        @Override
        public int hashCode() {
            return Objects.hash(System.identityHashCode(this.renderPipeline), this.baseDescriptor, this.programHandle);
        }
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

    @Nullable
    private static GlProgram resolveIrisOverrideProgram(RenderPipeline renderPipeline) {
        if (RustGalVulkanWholeFrameMode.enabled()
            || VulkanicAPI.isVulkanBackendSelected()) {
            // Rust Vulkan consumes explicit semantic pipeline descriptors; do not
            // inspect Iris' live pipeline manager from the Java compatibility encoder.
            return null;
        }
        if (renderPipeline == net.irisshaders.iris.pipeline.CompositeRenderer.COMPOSITE_PIPELINE) {
            return null;
        }
        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (!(pipeline instanceof IrisRenderingPipeline irisPipeline)
            || !irisPipeline.shouldOverrideShaders()
            || ImmediateState.bypass) {
            return null;
        }

        ShaderKey shaderKey = IrisPipelines.getPipeline(irisPipeline, renderPipeline);
        if (shaderKey == null) {
            return null;
        }
        return irisPipeline.getShaderMap().getShader(shaderKey);
    }

    @Nullable
    private static PipelineDescriptor createIrisProgramLiveDescriptor(
        CommandContext ctx,
        RenderPipeline renderPipeline,
        PipelineDescriptor baseDescriptor,
        GlProgram program
    ) {
        if (!(program instanceof IrisProgram) || program == GlProgram.INVALID_PROGRAM) {
            return null;
        }

        VertexFormat effectiveVertexFormat = renderPipeline.getVertexFormat();
        if (!effectiveVertexFormat.equals(baseDescriptor.getPortableState().vertexFormat())) {
            baseDescriptor = baseDescriptor.withPortableVertexFormat(effectiveVertexFormat);
        }

        int programHandle = program.getProgramId();
        if (programHandle <= 0 || VulkanicAPI.getLinkedProgramSpirvModules(ctx, programHandle).isEmpty()) {
            return null;
        }

        IrisProgramLiveDescriptorKey cacheKey = new IrisProgramLiveDescriptorKey(renderPipeline, baseDescriptor, programHandle);
        PipelineDescriptor cachedDescriptor = IRIS_PROGRAM_LIVE_DESCRIPTOR_CACHE.get(cacheKey);
        if (cachedDescriptor != null) {
            return cachedDescriptor;
        }

        PipelineDescriptor liveDescriptor = VulkanicAPI.createLiveProgramPipelineDescriptor(ctx, baseDescriptor, programHandle);
        if (liveDescriptor != null && liveDescriptor.hasSpirvModules()) {
            if (IRIS_PROGRAM_LIVE_DESCRIPTOR_CACHE.size() >= MAX_IRIS_PROGRAM_LIVE_DESCRIPTOR_CACHE_ENTRIES) {
                IRIS_PROGRAM_LIVE_DESCRIPTOR_CACHE.clear();
            }
            IRIS_PROGRAM_LIVE_DESCRIPTOR_CACHE.put(cacheKey, liveDescriptor);
            return liveDescriptor;
        }
        return null;
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
        this.ensureJavaVulkanRenderingAvailable();
        this.ensureNoRenderPass();
        CommandContext ctx = this.backend.beginCommandBuffer();
        VulkanicTextureView colorView = this.createTextureView(colorTextureView);
        VulkanicTextureView depthView = depthTextureView != null ? this.createTextureView(depthTextureView) : null;
        boolean renderPassStarted = false;
        try {
            VulkanicRenderPass pass = this.backend.beginRenderPass(ctx, label, colorView, clearColor, depthView, clearDepth);
            renderPassStarted = true;
            this.inRenderPass = true;
            VulkanicAPI.traceShaderInputParityOrdering(
                "pass-begin",
                "vulkan-native-commandencoder-createRenderPass-texture",
                "color=" + colorTextureView.getWidth(0) + "x" + colorTextureView.getHeight(0)
                    + "|clearColor=" + clearColor.isPresent()
                    + "|depth=" + (depthTextureView != null)
                    + "|clearDepth=" + clearDepth.isPresent()
            );
            return new NativeRenderPass(ctx, pass, 0, depthView != null, null, colorView, depthView);
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
        this.ensureJavaVulkanRenderingAvailable();
        this.ensureNoRenderPass();
        if (this.resourceMode == ResourceMode.GENERAL
                && !this.backend.canCreateNativeFramebufferRenderPass(framebuffer, hasDepthTexture)) {
            return this.backend.createCompatibilityCommandEncoder().createRenderPass(label, framebuffer, hasDepthTexture);
        }
        CommandContext ctx = this.backend.beginCommandBuffer();
        VulkanicRenderPass pass = this.backend.beginRenderPass(ctx, label, framebuffer, hasDepthTexture);
        this.inRenderPass = true;
        VulkanicAPI.traceShaderInputParityOrdering(
            "pass-begin",
            "vulkan-native-commandencoder-createRenderPass-framebuffer",
            "framebuffer=" + framebuffer + "|depth=" + hasDepthTexture
        );
        return new NativeRenderPass(ctx, pass, framebuffer, hasDepthTexture, null, null, null);
    }

    @Override
    public RenderPass createRenderPass(VulkanicRenderTargetDescriptor descriptor) {
        this.ensureJavaVulkanRenderingAvailable();
        this.ensureNoRenderPass();
        CommandContext ctx = this.backend.beginCommandBuffer();
        VulkanicRenderPass pass = this.backend.beginRenderPass(ctx, descriptor);
        this.inRenderPass = true;
        VulkanicAPI.traceShaderInputParityOrdering(
            "pass-begin",
            "vulkan-native-commandencoder-createRenderPass-descriptor",
            "target=" + descriptor.debugSignature()
        );
        return new NativeRenderPass(ctx, pass, 0, descriptor.hasDepthAttachment(), descriptor, null, null);
    }

    @Override
    public RenderPass createRenderPass(
        VulkanicRenderTargetDescriptor descriptor,
        int fallbackFramebuffer,
        boolean preferDescriptor
    ) {
        this.ensureJavaVulkanRenderingAvailable();
        this.ensureNoRenderPass();
        if (preferDescriptor
                && this.backend.renderTargetDescriptorCompatibilityWithFramebuffer(fallbackFramebuffer, descriptor)
                    .allowsDescriptorBackedRenderPass()) {
            return this.createRenderPass(descriptor);
        }

        return this.createRenderPass(descriptor.label(), fallbackFramebuffer, descriptor.hasDepthAttachment());
    }

    @Override
    public void writeToBuffer(GpuBufferSlice slice, ByteBuffer data) {
        this.ensureJavaVulkanRenderingAvailable();
        if (!legacyImmediatePassIgnored()) {
            this.ensureNoRenderPass();
        }
        GpuBuffer buffer = slice.buffer();
        if (buffer.isClosed()) {
            throw new IllegalStateException("Buffer already closed");
        }
        if ((buffer.usage() & GpuBuffer.USAGE_COPY_DST) == 0) {
            throw new IllegalStateException("Buffer needs USAGE_COPY_DST to be a destination for a copy");
        }

        int bytes = data.remaining();
        if (bytes > slice.length()) {
            throw new IllegalArgumentException(
                "Cannot write more data than the slice allows (attempting to write "
                    + bytes + " bytes into a slice of length " + slice.length() + ")"
            );
        }
        if (slice.offset() + slice.length() > buffer.size()) {
            throw new IllegalArgumentException(
                "Cannot write more data than this buffer can hold (attempting to write "
                    + bytes + " bytes at offset " + slice.offset() + " to " + buffer.size() + " size buffer)"
            );
        }

        int handle = this.requireBufferHandle(buffer, "writeToBuffer");
        VulkanicAPI.traceShaderInputParityOrdering(
            "buffer-upload",
            "vulkan-native-commandencoder-writeToBuffer",
            "offset=" + slice.offset() + "|length=" + bytes + "|sliceLength=" + slice.length()
        );
        VulkanicAPI.namedBufferSubDataDSA(this.commandContext(), handle, slice.offset(), data);
    }

    @Override
    public GpuBuffer.MappedView mapBuffer(GpuBuffer buffer, boolean read, boolean write) {
        this.ensureJavaVulkanRenderingAvailable();
        return this.mapBuffer(buffer.slice(), read, write);
    }

    @Override
    public GpuBuffer.MappedView mapBuffer(GpuBufferSlice slice, boolean read, boolean write) {
        this.ensureJavaVulkanRenderingAvailable();
        this.ensureNoRenderPass();
        if (slice.buffer().isClosed()) {
            throw new IllegalStateException("Buffer already closed");
        }
        if (!read && !write) {
            throw new IllegalArgumentException("At least read or write must be true");
        }
        if (read && (slice.buffer().usage() & GpuBuffer.USAGE_MAP_READ) == 0) {
            throw new IllegalStateException("Buffer is not readable");
        }
        if (write && (slice.buffer().usage() & GpuBuffer.USAGE_MAP_WRITE) == 0) {
            throw new IllegalStateException("Buffer is not writable");
        }
        if (slice.offset() + slice.length() > slice.buffer().size()) {
            throw new IllegalArgumentException(
                "Cannot map more data than this buffer can hold (attempting to map "
                    + slice.length() + " bytes at offset " + slice.offset() + " from " + slice.buffer().size() + " size buffer)"
            );
        }

        int access = 0;
        if (read) {
            access |= 1;
        }
        if (write) {
            access |= 34;
        }

        int handle = this.requireBufferHandle(slice.buffer(), "mapBuffer");
        CommandContext ctx = this.commandContext();
        ByteBuffer mapped = VulkanicAPI.mapNamedBufferRangeDSA(
            ctx,
            handle,
            slice.offset(),
            slice.length(),
            access
        );
        if (mapped == null) {
            throw new IllegalStateException("Unable to map Vulkan buffer");
        }
        mapped.order(ByteOrder.nativeOrder());

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
                    VulkanicAPI.unmapNamedBufferDSA(ctx, handle);
                }
            }
        };
    }

    @Override
    public void copyToBuffer(GpuBufferSlice source, GpuBufferSlice target) {
        this.ensureJavaVulkanRenderingAvailable();
        this.ensureNoRenderPass();
        GpuBuffer sourceBuffer = source.buffer();
        GpuBuffer targetBuffer = target.buffer();
        if (sourceBuffer.isClosed()) {
            throw new IllegalStateException("Source buffer already closed");
        }
        if ((sourceBuffer.usage() & GpuBuffer.USAGE_COPY_SRC) == 0) {
            throw new IllegalStateException("Source buffer needs USAGE_COPY_SRC to be a source for a copy");
        }
        if (targetBuffer.isClosed()) {
            throw new IllegalStateException("Target buffer already closed");
        }
        if ((targetBuffer.usage() & GpuBuffer.USAGE_COPY_DST) == 0) {
            throw new IllegalStateException("Target buffer needs USAGE_COPY_DST to be a destination for a copy");
        }
        if (source.length() != target.length()) {
            throw new IllegalArgumentException(
                "Cannot copy from slice of size " + source.length() + " to slice of size " + target.length() + ", they must be equal"
            );
        }
        if (source.offset() + source.length() > sourceBuffer.size()) {
            throw new IllegalArgumentException(
                "Cannot copy more data than the source buffer holds (attempting to copy "
                    + source.length() + " bytes at offset " + source.offset() + " from " + sourceBuffer.size() + " size buffer)"
            );
        }
        if (target.offset() + target.length() > targetBuffer.size()) {
            throw new IllegalArgumentException(
                "Cannot copy more data than the target buffer can hold (attempting to copy "
                    + target.length() + " bytes at offset " + target.offset() + " to " + targetBuffer.size() + " size buffer)"
            );
        }

        this.backend.copyNamedBufferSubDataDSA(
            this.commandContext(),
            this.requireBufferHandle(sourceBuffer, "copyToBuffer(source)"),
            this.requireBufferHandle(targetBuffer, "copyToBuffer(target)"),
            source.offset(),
            target.offset(),
            source.length()
        );
    }

    @Override
    public void clearColorTexture(GpuTexture texture, int clearColor) {
        this.ensureJavaVulkanRenderingAvailable();
        this.ensureNoRenderPass();
        this.verifyColorTexture(texture);
        CommandContext ctx = this.backend.beginCommandBuffer();
        try (VulkanicTextureView colorView = this.createTextureView(texture);
             VulkanicRenderPass ignored = this.backend.beginRenderPass(
                 ctx,
                 () -> "Clear color texture",
                 colorView,
                 OptionalInt.of(clearColor)
             )) {
        } finally {
            this.backend.submitCommandBuffer(ctx);
        }
    }

    @Override
    public void clearColorAndDepthTextures(GpuTexture colorTexture, int clearColor, GpuTexture depthTexture, double clearDepth) {
        this.ensureJavaVulkanRenderingAvailable();
        this.ensureNoRenderPass();
        this.verifyColorTexture(colorTexture);
        this.verifyDepthTexture(depthTexture);
        CommandContext ctx = this.backend.beginCommandBuffer();
        try (VulkanicTextureView colorView = this.createTextureView(colorTexture);
             VulkanicTextureView depthView = this.createTextureView(depthTexture);
             VulkanicRenderPass ignored = this.backend.beginRenderPass(
                 ctx,
                 () -> "Clear color/depth textures",
                 colorView,
                 OptionalInt.of(clearColor),
                 depthView,
                 OptionalDouble.of(clearDepth)
             )) {
        } finally {
            this.backend.submitCommandBuffer(ctx);
        }
    }

    @Override
    public void clearColorAndDepthTextures(GpuTexture colorTexture, int clearColor, GpuTexture depthTexture, double clearDepth, int x, int y, int width, int height) {
        this.ensureJavaVulkanRenderingAvailable();
        this.ensureNoRenderPass();
        this.verifyColorTexture(colorTexture);
        this.verifyDepthTexture(depthTexture);
        this.verifyRegion(colorTexture, x, y, width, height);
        CommandContext ctx = this.backend.beginCommandBuffer();
        try (VulkanicTextureView colorView = this.createTextureView(colorTexture);
             VulkanicTextureView depthView = this.createTextureView(depthTexture);
             VulkanicRenderPass ignored = this.backend.beginRenderPass(
                 ctx,
                 () -> "Clear color/depth texture region",
                 colorView,
                 OptionalInt.empty(),
                 depthView,
                 OptionalDouble.empty()
             )) {
            VulkanicAPI.setDynamicScissor(ctx, x, y, width, height);
            VulkanicAPI.setScissorTestEnabled(ctx, true);
            VulkanicAPI.setClearDepth(ctx, clearDepth);
            VulkanicAPI.setClearColor(ctx, ARGB.redFloat(clearColor), ARGB.greenFloat(clearColor), ARGB.blueFloat(clearColor), ARGB.alphaFloat(clearColor));
            net.irisshaders.iris.gl.blending.DepthColorStorage.setDepthMask(true);
            net.irisshaders.iris.gl.blending.DepthColorStorage.setColorMask(true, true, true, true);
            VulkanicAPI.clearColorAndDepthBuffersWithMacosWorkaround(ctx);
            VulkanicAPI.setScissorTestEnabled(ctx, false);
        } finally {
            this.backend.submitCommandBuffer(ctx);
        }
    }

    @Override
    public void clearDepthTexture(GpuTexture texture, double clearDepth) {
        this.ensureJavaVulkanRenderingAvailable();
        this.ensureNoRenderPass();
        this.verifyDepthTexture(texture);
        CommandContext ctx = this.commandContext();
        int framebuffer = VulkanicAPI.createFramebuffer(ctx);
        try {
            VulkanicAPI.bindFramebuffer(ctx, VulkanicAPI.GL_FRAMEBUFFER, framebuffer);
            VulkanicAPI.framebufferDepthAttachmentTexture2D(ctx, VulkanicAPI.GL_FRAMEBUFFER, VulkanicCoreAPI.textureId(texture), 0);
            VulkanicAPI.setDrawBufferNone(ctx);
            VulkanicAPI.setClearDepth(ctx, clearDepth);
            net.irisshaders.iris.gl.blending.DepthColorStorage.setDepthMask(true);
            VulkanicAPI.setScissorTestEnabled(ctx, false);
            VulkanicAPI.clearDepthBufferWithMacosWorkaround(ctx);
            VulkanicAPI.framebufferDepthAttachmentTexture2D(ctx, VulkanicAPI.GL_FRAMEBUFFER, 0, 0);
        } finally {
            VulkanicAPI.setDrawBufferColorAttachment0(ctx);
            VulkanicAPI.bindDefaultFramebuffer(ctx);
            VulkanicAPI.deleteFramebuffer(ctx, framebuffer);
        }
    }

    @Override
    public void writeToTexture(GpuTexture texture, NativeImage image) {
        this.ensureJavaVulkanRenderingAvailable();
        int width = texture.getWidth(0);
        int height = texture.getHeight(0);
        if (image.getWidth() != width || image.getHeight() != height) {
            throw new IllegalArgumentException(
                "Cannot replace texture of size " + width + "x" + height + " with image of size " + image.getWidth() + "x" + image.getHeight()
            );
        }
        if (texture.isClosed()) {
            throw new IllegalStateException("Destination texture is closed");
        }
        if ((texture.usage() & GpuTexture.USAGE_COPY_DST) == 0) {
            throw new IllegalStateException("Color texture must have USAGE_COPY_DST to be a destination for a write");
        }
        this.writeToTexture(texture, image, 0, 0, 0, 0, width, height, 0, 0);
    }

    @Override
    public void writeToTexture(GpuTexture texture, NativeImage image, int mipLevel, int depth, int targetX, int targetY, int width, int height, int sourceX, int sourceY) {
        this.ensureJavaVulkanRenderingAvailable();
        if (!legacyImmediatePassIgnored()) {
            this.ensureNoRenderPass();
        }
        this.verifyTextureWrite(texture, mipLevel, sourceX, sourceY, targetX, targetY, width, height, depth);
        if (sourceX + width > image.getWidth() || sourceY + height > image.getHeight()) {
            throw new IllegalArgumentException(
                "Copy source (" + image.getWidth() + "x" + image.getHeight() + ") is not large enough to read a rectangle of "
                    + width + "x" + height + " from " + sourceX + "x" + sourceY
            );
        }

        CommandContext ctx = this.commandContext();
        int textureHandle = VulkanicCoreAPI.textureId(texture);
        boolean cubemap = (texture.usage() & GpuTexture.USAGE_CUBEMAP_COMPATIBLE) != 0;
        int uploadTarget = this.bindTextureForUpload(ctx, texture, textureHandle, depth, cubemap);
        VulkanicAPI.setPixelStore(ctx, VulkanicAPI.GL_UNPACK_ROW_LENGTH, image.getWidth());
        VulkanicAPI.setPixelStore(ctx, VulkanicAPI.GL_UNPACK_SKIP_PIXELS, sourceX);
        VulkanicAPI.setPixelStore(ctx, VulkanicAPI.GL_UNPACK_SKIP_ROWS, sourceY);
        VulkanicAPI.setPixelStore(ctx, VulkanicAPI.GL_UNPACK_ALIGNMENT, image.format().components());
        this.uploadTextureSubImage(ctx, cubemap, uploadTarget, mipLevel, targetX, targetY, width, height, GlConst.toGl(image.format()), image.getPointer());
    }

    @Override
    public void writeToTexture(GpuTexture texture, ByteBuffer data, NativeImage.Format format, int mipLevel, int depth, int targetX, int targetY, int width, int height) {
        this.ensureJavaVulkanRenderingAvailable();
        if (!legacyImmediatePassIgnored()) {
            this.ensureNoRenderPass();
        }
        this.verifyTextureWrite(texture, mipLevel, 0, 0, targetX, targetY, width, height, depth);
        if (width * height * format.components() > data.remaining()) {
            throw new IllegalArgumentException(
                "Copy would overrun the source buffer (remaining length of " + data.remaining() + ", but copy is "
                    + width + "x" + height + " of format " + format + ")"
            );
        }

        CommandContext ctx = this.commandContext();
        int textureHandle = VulkanicCoreAPI.textureId(texture);
        boolean cubemap = (texture.usage() & GpuTexture.USAGE_CUBEMAP_COMPATIBLE) != 0;
        int uploadTarget = this.bindTextureForUpload(ctx, texture, textureHandle, depth, cubemap);
        VulkanicAPI.setPixelStore(ctx, VulkanicAPI.GL_UNPACK_ROW_LENGTH, width);
        VulkanicAPI.setPixelStore(ctx, VulkanicAPI.GL_UNPACK_SKIP_PIXELS, 0);
        VulkanicAPI.setPixelStore(ctx, VulkanicAPI.GL_UNPACK_SKIP_ROWS, 0);
        VulkanicAPI.setPixelStore(ctx, VulkanicAPI.GL_UNPACK_ALIGNMENT, format.components());
        this.uploadTextureSubImage(ctx, cubemap, uploadTarget, mipLevel, targetX, targetY, width, height, GlConst.toGl(format), data);
    }

    @Override
    public void copyTextureToBuffer(GpuTexture texture, GpuBuffer buffer, int offset, Runnable callback, int mipLevel) {
        this.ensureJavaVulkanRenderingAvailable();
        this.ensureNoRenderPass();
        this.copyTextureToBuffer(texture, buffer, offset, callback, mipLevel, 0, 0, texture.getWidth(mipLevel), texture.getHeight(mipLevel));
    }

    @Override
    public void copyTextureToBuffer(GpuTexture texture, GpuBuffer buffer, int offset, Runnable callback, int mipLevel, int x, int y, int width, int height) {
        this.ensureJavaVulkanRenderingAvailable();
        this.ensureNoRenderPass();
        this.verifyTextureCopyToBuffer(texture, buffer, mipLevel, x, y, width, height, offset);
        CommandContext ctx = this.commandContext();
        while (VulkanicAPI.getError(ctx) != 0) {
        }
        int framebuffer = VulkanicAPI.resolveFramebufferForTextures(texture, null);
        VulkanicAPI.bindReadFramebuffer(ctx, framebuffer);
        VulkanicAPI.bindPixelPackBuffer(ctx, this.requireBufferHandle(buffer, "copyTextureToBuffer"));
        VulkanicAPI.setPixelStore(ctx, VulkanicAPI.GL_PACK_ROW_LENGTH, width);
        VulkanicAPI.readPixels(ctx, x, y, width, height, GlConst.toGlExternalId(texture.getFormat()), GlConst.toGlType(texture.getFormat()), offset);
        VulkanicAPI.queueFencedTask(callback);
        VulkanicAPI.framebufferColorAttachment0Texture2D(ctx, VulkanicAPI.GL_READ_FRAMEBUFFER, 0, mipLevel);
        VulkanicAPI.bindReadFramebuffer(ctx, 0);
        VulkanicAPI.bindPixelPackBuffer(ctx, 0);
        int error = VulkanicAPI.getError(ctx);
        if (error != 0) {
            throw new IllegalStateException("Couldn't perform copyToBuffer for texture " + texture.getLabel() + ": GL error " + error);
        }
    }

    @Override
    public void copyTextureToTexture(GpuTexture source, GpuTexture target, int mipLevel, int targetX, int targetY, int sourceX, int sourceY, int width, int height) {
        this.ensureJavaVulkanRenderingAvailable();
        this.ensureNoRenderPass();
        this.verifyTextureCopyToTexture(source, target, mipLevel, targetX, targetY, sourceX, sourceY, width, height);
        VulkanicAPI.copyImageSubData2D(
            this.commandContext(),
            VulkanicCoreAPI.textureId(source),
            mipLevel,
            sourceX,
            sourceY,
            0,
            VulkanicCoreAPI.textureId(target),
            mipLevel,
            targetX,
            targetY,
            0,
            width,
            height,
            1
        );
    }

    @Override
    public void applyPipelineState(RenderPipeline renderPipeline) {
    }

    @Override
    public void invalidateCachedProgramBinding() {
    }

    @Override
    public void presentTexture(GpuTextureView textureView) {
        if (RustGalVulkanWholeFrameMode.enabled()
                || net.vulkanic.VulkanicAPI.isVulkanBackendSelected()) {
            throw new IllegalStateException(
                "Java Vulkan command-encoder presentation is unavailable while Rust owns the selected Vulkan presentation route"
            );
        }
        this.ensureNoRenderPass();
        if (!textureView.texture().getFormat().hasColorAspect()) {
            throw new IllegalStateException("Cannot present a non-color texture!");
        }
        if ((textureView.texture().usage() & GpuTexture.USAGE_RENDER_ATTACHMENT) == 0) {
            throw new IllegalStateException("Color texture must have USAGE_RENDER_ATTACHMENT to presented to the screen");
        }
        if (textureView.texture().getDepthOrLayers() > 1) {
            throw new UnsupportedOperationException("Textures with multiple depths or layers are not yet supported for presentation");
        }
        this.backend.presentTextureToScreen(this.commandContext(), textureView);
    }

    @Override
    public GpuFence createFence() {
        this.ensureJavaVulkanRenderingAvailable();
        this.ensureNoRenderPass();
        return new NativeFence(this.commandContext());
    }

    private CommandContext commandContext() {
        return this.backend.getCurrentCommandContext();
    }

    private VulkanicTextureView createTextureView(GpuTextureView view) {
        if (!(view.texture() instanceof VulkanicTexture texture)) {
            throw new IllegalArgumentException("Render-pass texture is not VulkanicTexture: " + view.texture().getClass().getName());
        }
        return VulkanicAPI.createManagedTextureView(texture, view.baseMipLevel(), view.mipLevels());
    }

    private VulkanicTextureView createTextureView(GpuTexture texture) {
        if (!(texture instanceof VulkanicTexture vulkanicTexture)) {
            throw new IllegalArgumentException("Texture is not VulkanicTexture: " + texture.getClass().getName());
        }
        return VulkanicAPI.createManagedTextureView(vulkanicTexture, 0, 1);
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

    /** Java Vulkan render passes are never a fallback once Rust owns presentation. */
    private void ensureJavaVulkanRenderingAvailable() {
        if (net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
                && !RustGalVulkanWholeFrameMode.enabled()) {
            throw new IllegalStateException(
                "Selected Vulkan Java render passes are unavailable; Rust semantic rendering is not a fallback"
            );
        }
        if (RustGalVulkanWholeFrameMode.enabled()) {
            throw new IllegalStateException(
                "Java Vulkan render passes are unavailable while Rust owns whole-frame presentation"
            );
        }
    }

    private void verifyColorTexture(GpuTexture texture) {
        if (!texture.getFormat().hasColorAspect()) {
            throw new IllegalStateException("Trying to clear a non-color texture as color");
        }
        if (texture.isClosed()) {
            throw new IllegalStateException("Color texture is closed");
        }
        if ((texture.usage() & GpuTexture.USAGE_RENDER_ATTACHMENT) == 0) {
            throw new IllegalStateException("Color texture must have USAGE_RENDER_ATTACHMENT");
        }
        if (texture.getDepthOrLayers() > 1) {
            throw new UnsupportedOperationException("Clearing a texture with multiple layers or depths is not yet supported");
        }
    }

    private void verifyDepthTexture(GpuTexture texture) {
        if (!texture.getFormat().hasDepthAspect()) {
            throw new IllegalStateException("Trying to clear a non-depth texture as depth");
        }
        if (texture.isClosed()) {
            throw new IllegalStateException("Depth texture is closed");
        }
        if ((texture.usage() & GpuTexture.USAGE_RENDER_ATTACHMENT) == 0) {
            throw new IllegalStateException("Depth texture must have USAGE_RENDER_ATTACHMENT");
        }
        if (texture.getDepthOrLayers() > 1) {
            throw new UnsupportedOperationException("Clearing a texture with multiple layers or depths is not yet supported");
        }
    }

    private void verifyRegion(GpuTexture texture, int x, int y, int width, int height) {
        if (x < 0 || x >= texture.getWidth(0)) {
            throw new IllegalArgumentException("regionX should not be outside of the texture");
        }
        if (y < 0 || y >= texture.getHeight(0)) {
            throw new IllegalArgumentException("regionY should not be outside of the texture");
        }
        if (width <= 0) {
            throw new IllegalArgumentException("regionWidth should be greater than 0");
        }
        if (x + width > texture.getWidth(0)) {
            throw new IllegalArgumentException("regionWidth + regionX should be less than the texture width");
        }
        if (height <= 0) {
            throw new IllegalArgumentException("regionHeight should be greater than 0");
        }
        if (y + height > texture.getHeight(0)) {
            throw new IllegalArgumentException("regionWidth + regionX should be less than the texture height");
        }
    }

    private void verifyTextureWrite(GpuTexture texture, int mipLevel, int sourceX, int sourceY, int targetX, int targetY, int width, int height, int depth) {
        if (mipLevel < 0 || mipLevel >= texture.getMipLevels()) {
            throw new IllegalArgumentException("Invalid mipLevel " + mipLevel + ", must be >= 0 and < " + texture.getMipLevels());
        }
        if (targetX + width > texture.getWidth(mipLevel) || targetY + height > texture.getHeight(mipLevel)) {
            throw new IllegalArgumentException(
                "Dest texture (" + texture.getWidth(mipLevel) + "x" + texture.getHeight(mipLevel)
                    + ") is not large enough to write a rectangle of " + width + "x" + height + " at " + targetX + "x" + targetY
            );
        }
        if (texture.isClosed()) {
            throw new IllegalStateException("Destination texture is closed");
        }
        if ((texture.usage() & GpuTexture.USAGE_COPY_DST) == 0) {
            throw new IllegalStateException("Color texture must have USAGE_COPY_DST to be a destination for a write");
        }
        if (depth >= texture.getDepthOrLayers()) {
            throw new UnsupportedOperationException("Depth or layer is out of range, must be >= 0 and < " + texture.getDepthOrLayers());
        }
        if (sourceX < 0 || sourceY < 0 || targetX < 0 || targetY < 0 || width <= 0 || height <= 0 || depth < 0) {
            throw new IllegalArgumentException("Invalid texture write region");
        }
    }

    private int bindTextureForUpload(CommandContext ctx, GpuTexture texture, int textureHandle, int depth, boolean cubemap) {
        if (cubemap) {
            VulkanicAPI.bindCubemapTexture(ctx, textureHandle);
            return GlConst.CUBEMAP_TARGETS[depth % 6];
        }
        VulkanicAPI.bindTexture2D(ctx, textureHandle);
        return VulkanicAPI.GL_TEXTURE_2D;
    }

    private void uploadTextureSubImage(
        CommandContext ctx,
        boolean cubemap,
        int uploadTarget,
        int mipLevel,
        int targetX,
        int targetY,
        int width,
        int height,
        int format,
        long pixels
    ) {
        if (cubemap) {
            VulkanicAPI.uploadTexture2DSubImage(ctx, uploadTarget, mipLevel, targetX, targetY, width, height, format, VulkanicAPI.GL_UNSIGNED_BYTE, pixels);
        } else {
            VulkanicAPI.uploadTexture2DSubImage(ctx, mipLevel, targetX, targetY, width, height, format, VulkanicAPI.GL_UNSIGNED_BYTE, pixels);
        }
    }

    private void uploadTextureSubImage(
        CommandContext ctx,
        boolean cubemap,
        int uploadTarget,
        int mipLevel,
        int targetX,
        int targetY,
        int width,
        int height,
        int format,
        ByteBuffer pixels
    ) {
        if (cubemap) {
            VulkanicAPI.uploadTexture2DSubImage(ctx, uploadTarget, mipLevel, targetX, targetY, width, height, format, VulkanicAPI.GL_UNSIGNED_BYTE, pixels);
        } else {
            VulkanicAPI.uploadTexture2DSubImage(ctx, mipLevel, targetX, targetY, width, height, format, VulkanicAPI.GL_UNSIGNED_BYTE, pixels);
        }
    }

    private void verifyTextureCopyToBuffer(GpuTexture texture, GpuBuffer buffer, int mipLevel, int x, int y, int width, int height, int alignment) {
        if (mipLevel < 0 || mipLevel >= texture.getMipLevels()) {
            throw new IllegalArgumentException("Invalid mipLevel " + mipLevel + ", must be >= 0 and < " + texture.getMipLevels());
        }
        if (texture.getWidth(mipLevel) * texture.getHeight(mipLevel) * texture.getFormat().pixelSize() + alignment > buffer.size()) {
            throw new IllegalArgumentException(
                "Buffer of size " + buffer.size() + " is not large enough to hold " + width + "x" + height
                    + " pixels (" + texture.getFormat().pixelSize() + " bytes each) starting from offset " + alignment
            );
        }
        if ((texture.usage() & GpuTexture.USAGE_COPY_SRC) == 0) {
            throw new IllegalArgumentException("Texture needs USAGE_COPY_SRC to be a source for a copy");
        }
        if ((buffer.usage() & GpuBuffer.USAGE_COPY_DST) == 0) {
            throw new IllegalArgumentException("Buffer needs USAGE_COPY_DST to be a destination for a copy");
        }
        if (x + width > texture.getWidth(mipLevel) || y + height > texture.getHeight(mipLevel)) {
            throw new IllegalArgumentException(
                "Copy source texture (" + texture.getWidth(mipLevel) + "x" + texture.getHeight(mipLevel)
                    + ") is not large enough to read a rectangle of " + width + "x" + height + " from " + x + "," + y
            );
        }
        if (texture.isClosed()) {
            throw new IllegalStateException("Source texture is closed");
        }
        if (buffer.isClosed()) {
            throw new IllegalStateException("Destination buffer is closed");
        }
        if (texture.getDepthOrLayers() > 1) {
            throw new UnsupportedOperationException("Textures with multiple depths or layers are not yet supported for copying");
        }
    }

    private void verifyTextureCopyToTexture(GpuTexture source, GpuTexture target, int mipLevel, int targetX, int targetY, int sourceX, int sourceY, int width, int height) {
        if (mipLevel < 0 || mipLevel >= source.getMipLevels() || mipLevel >= target.getMipLevels()) {
            throw new IllegalArgumentException("Invalid mipLevel " + mipLevel + ", must be >= 0 and < " + source.getMipLevels() + " and < " + target.getMipLevels());
        }
        if (targetX + width > target.getWidth(mipLevel) || targetY + height > target.getHeight(mipLevel)) {
            throw new IllegalArgumentException(
                "Dest texture (" + target.getWidth(mipLevel) + "x" + target.getHeight(mipLevel)
                    + ") is not large enough to write a rectangle of " + width + "x" + height + " at " + targetX + "x" + targetY
            );
        }
        if (sourceX + width > source.getWidth(mipLevel) || sourceY + height > source.getHeight(mipLevel)) {
            throw new IllegalArgumentException(
                "Source texture (" + source.getWidth(mipLevel) + "x" + source.getHeight(mipLevel)
                    + ") is not large enough to read a rectangle of " + width + "x" + height + " at " + sourceX + "x" + sourceY
            );
        }
        if (source.isClosed()) {
            throw new IllegalStateException("Source texture is closed");
        }
        if (target.isClosed()) {
            throw new IllegalStateException("Destination texture is closed");
        }
        if ((source.usage() & GpuTexture.USAGE_COPY_SRC) == 0) {
            throw new IllegalArgumentException("Texture needs USAGE_COPY_SRC to be a source for a copy");
        }
        if ((target.usage() & GpuTexture.USAGE_COPY_DST) == 0) {
            throw new IllegalArgumentException("Texture needs USAGE_COPY_DST to be a destination for a copy");
        }
        if (source.getDepthOrLayers() > 1 || target.getDepthOrLayers() > 1) {
            throw new UnsupportedOperationException("Textures with multiple depths or layers are not yet supported for copying");
        }
    }

    private void unsupported(String operation) {
        throw new UnsupportedOperationException(
            "Vulkan native command encoder does not support " + operation
                + "; migrate that callsite through an explicit native Vulkan encoder slice first."
        );
    }

    private static final class NativeFence implements GpuFence {
        private final CommandContext ctx;
        private long handle;

        private NativeFence(CommandContext ctx) {
            this.ctx = ctx;
            this.handle = VulkanicAPI.createGpuCompletionFence(ctx);
        }

        @Override
        public void close() {
            if (this.handle != 0L) {
                VulkanicAPI.destroySync(this.ctx, this.handle);
                this.handle = 0L;
            }
        }

        @Override
        public boolean awaitCompletion(long timeoutNanos) {
            if (this.handle == 0L) {
                return true;
            }

            int result = VulkanicAPI.waitForSync(this.ctx, this.handle, 0, timeoutNanos);
            if (VulkanicAPI.isSyncWaitTimeout(result)) {
                return false;
            }
            if (VulkanicAPI.isSyncWaitFailed(result)) {
                throw new IllegalStateException("Failed to complete gpu fence");
            }
            return true;
        }
    }

    private final class NativeRenderPass implements RenderPass, RenderPassResourceBinder {
        private final CommandContext ctx;
        private final VulkanicRenderPass pass;
        private final int framebuffer;
        private final boolean hasDepthAttachment;
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
        private CustomPass customPass;
        @Nullable
        private GpuBuffer indexBuffer;
        @Nullable
        private GpuBuffer vertexBuffer;
        private VertexFormat.IndexType indexType = VertexFormat.IndexType.INT;
        @Nullable
        private PipelineDescriptor lastSubmittedDescriptor;
        @Nullable
        private PipelineResourcePlanner.Plan lastSubmittedPlan;
        @Nullable
        private PipelineHandle lastPipelineHandle;
        private long resourceStateGeneration;
        @Nullable
        private CachedResourceSubmission cachedResourceSubmission;
        private final List<IrisProgram> irisProgramsToClear = new ArrayList<>();
        private boolean scissorEnabled;
        private int scissorX;
        private int scissorY;
        private int scissorWidth;
        private int scissorHeight;
        private boolean closed;
        private int debugGroups;

        private NativeRenderPass(
            CommandContext ctx,
            VulkanicRenderPass pass,
            int framebuffer,
            boolean hasDepthAttachment,
            @Nullable VulkanicRenderTargetDescriptor renderTargetDescriptor,
            @Nullable VulkanicTextureView colorView,
            @Nullable VulkanicTextureView depthView
        ) {
            this.ctx = ctx;
            this.pass = pass;
            this.framebuffer = framebuffer;
            this.hasDepthAttachment = hasDepthAttachment;
            this.renderTargetDescriptor = renderTargetDescriptor;
            this.colorView = colorView;
            this.depthView = depthView;
        }

        @Override
        public void iris$setCustomPass(CustomPass pass) {
            this.checkOpen();
            if (this.customPass != pass) {
                this.markResourceBindingsDirty();
            }
            this.customPass = pass;
        }

        @Override
        public CustomPass iris$getCustomPass() {
            return this.customPass;
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
            PipelineDescriptor resolvedDescriptor = VulkanNativeCommandEncoder.this.backend.resolvePrecompiledPipelineDescriptor(renderPipeline);
            if (resolvedDescriptor == null) {
                resolvedDescriptor = PipelineDescriptor.fromRenderPipeline(renderPipeline);
            }
            if (this.renderPipeline != renderPipeline || !Objects.equals(this.pipelineDescriptor, resolvedDescriptor)) {
                this.markResourceBindingsDirty();
            }
            this.renderPipeline = renderPipeline;
            this.pipelineDescriptor = resolvedDescriptor;
            VulkanicAPI.traceShaderInputParityOrdering(
                "pipeline-bind",
                "vulkan-native-renderpass-setPipeline",
                "pipeline=" + renderPipeline.getLocation()
            );
        }

        @Override
        public void bindSampler(String name, @Nullable GpuTextureView view) {
            Integer samplerUnit = parseSamplerIndex(name);
            this.bindSampler(name, view, samplerUnit != null ? samplerUnit : -1);
        }

        @Override
        public void bindSampler(String name, @Nullable GpuTextureView view, int textureUnit) {
            this.checkOpen();
            this.markResourceBindingsDirty();
            if (view == null) {
                this.closeSampler(name);
                this.samplerUnits.remove(name);
                return;
            }

            this.ensureBindingCapacity(this.samplers, name, MAX_RENDER_PASS_SAMPLERS, "samplers");
            this.closeSampler(name);

            if (!(view.texture() instanceof VulkanicTexture texture)) {
                throw new IllegalArgumentException("Sampler " + name + " is not backed by a VulkanicTexture");
            }

            if (textureUnit >= 0) {
                this.samplerUnits.put(name, textureUnit);
                IrisRenderSystem.setTextureBinding(
                    textureUnit,
                    VulkanNativeCommandEncoder.this.backend.resolveTextureHandle(this.ctx, texture)
                );
            } else {
                this.samplerUnits.remove(name);
            }
            this.samplers.put(name, VulkanicAPI.createManagedTextureView(texture, view.baseMipLevel(), view.mipLevels()));
            VulkanicAPI.recordScopedCompositeColortex0RenderPassBinding(
                this.renderPipeline,
                name,
                view,
                textureUnit,
                "vulkan-renderpass-bindSampler"
            );
            VulkanicAPI.traceShaderInputParityOrdering(
                "resource-bind",
                "vulkan-native-renderpass-bindSampler",
                "name=" + name + "|present=true|unit=" + textureUnit
            );
        }

        @Override
        public boolean bindLegacySampler(String name, int textureId, int textureUnit) {
            this.checkOpen();
            this.markResourceBindingsDirty();
            if (textureId <= 0) {
                this.closeSampler(name);
                this.samplerUnits.remove(name);
                return false;
            }

            this.ensureBindingCapacity(this.samplers, name, MAX_RENDER_PASS_SAMPLERS, "samplers");
            this.closeSampler(name);

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
            VulkanicAPI.recordScopedCompositeColortex0RenderPassLegacyBinding(
                this.renderPipeline,
                name,
                textureId,
                textureUnit,
                "vulkan-renderpass-bindLegacySampler"
            );
            VulkanicAPI.traceShaderInputParityOrdering(
                "resource-bind",
                "vulkan-native-renderpass-bindLegacySampler",
                "name=" + name + "|present=true|unit=" + textureUnit
            );
            return true;
        }

        @Override
        public void setUniform(String name, GpuBuffer buffer) {
            this.setUniform(name, buffer.slice());
        }

        @Override
        public void setUniform(String name, GpuBufferSlice slice) {
            this.checkOpen();
            this.ensureBindingCapacity(this.uniforms, name, MAX_RENDER_PASS_UNIFORMS, "uniforms");
            VulkanicBufferSlice resolvedSlice = new VulkanicBufferSlice(
                VulkanicAPI.resolveVulkanicBuffer(slice.buffer()),
                slice.offset(),
                slice.length()
            );
            if (!Objects.equals(this.uniforms.get(name), resolvedSlice)) {
                this.markResourceBindingsDirty();
            }
            this.uniforms.put(name, resolvedSlice);
            VulkanicAPI.traceShaderInputParityOrdering(
                "uniform-update",
                "vulkan-native-renderpass-setUniform",
                "name=" + name + "|offset=" + slice.offset() + "|length=" + slice.length()
            );
        }

        @Override
        public void enableScissor(int x, int y, int width, int height) {
            this.checkOpen();
            this.scissorEnabled = true;
            this.scissorX = x;
            this.scissorY = y;
            this.scissorWidth = width;
            this.scissorHeight = height;
            VulkanicAPI.setScissorTestEnabled(this.ctx, true);
            VulkanicAPI.setDynamicScissor(this.ctx, x, y, width, height);
        }

        @Override
        public void disableScissor() {
            this.checkOpen();
            this.scissorEnabled = false;
            VulkanicAPI.setScissorTestEnabled(this.ctx, false);
        }

        @Override
        public void setVertexBuffer(int slot, GpuBuffer buffer) {
            this.checkOpen();
            if (slot == 0) {
                this.vertexBuffer = buffer;
            }
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
            try (VulkanicAPI.ShaderInputParityScope ignored = VulkanicAPI.beginShaderInputParitySemanticDraw(
                "vulkan-native-renderpass-drawIndexed",
                "blaze3d-renderpass",
                this.semanticPassLabel(),
                this.renderPipeline,
                this.pipelineDescriptor,
                this.semanticMaterial(),
                this.semanticOutputTarget(),
                true,
                0,
                0,
                firstIndex,
                indexCount,
                instanceCount,
                baseVertex
            )) {
            if (!this.bindPipelineAndResources()) {
                return;
            }
            VulkanicAPI.traceScopedCompositeColortex0ProducerDraw(
                this.renderTargetDescriptor,
                this.framebuffer,
                this.renderPipeline,
                this.customPass,
                this.lastPipelineHandle,
                this.lastSubmittedDescriptor,
                this.lastSubmittedPlan == null ? null : this.lastSubmittedPlan.bindings(),
                "vulkan-native-draw",
                true,
                0,
                baseVertex,
                firstIndex,
                indexCount,
                0,
                instanceCount,
                this.indexType,
                this.scissorEnabled,
                this.scissorX,
                this.scissorY,
                this.scissorWidth,
                this.scissorHeight
            );
            this.logDrawState(true, 0, baseVertex, firstIndex, indexCount, 0, instanceCount, this.indexType);
            RenderPipeline pipeline = this.requirePipeline();
            VulkanicAPI.traceShaderInputParityGeometry(
                "vulkan-native-renderpass-geometry",
                this.vertexBuffer,
                this.indexBuffer,
                pipeline.getVertexFormat(),
                pipeline.getVertexFormatMode(),
                true,
                0,
                0,
                firstIndex,
                indexCount,
                this.indexType,
                instanceCount,
                baseVertex
            );
            int glPrimitiveMode = GlConst.toGl(this.requirePipeline().getVertexFormatMode());
            VulkanicAPI.traceShaderInputParityDraw(
                "vulkan-native-renderpass-encoded-drawIndexed",
                true,
                glPrimitiveMode,
                0,
                0,
                (long) firstIndex * this.indexType.bytes,
                indexCount,
                GlConst.toGl(this.indexType),
                instanceCount,
                baseVertex
            );
            this.pass.drawIndexed(firstIndex, indexCount, baseVertex, instanceCount);
            }
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
            try (VulkanicAPI.ShaderInputParityScope ignored = VulkanicAPI.beginShaderInputParitySemanticDraw(
                "vulkan-native-renderpass-draw",
                "blaze3d-renderpass",
                this.semanticPassLabel(),
                this.renderPipeline,
                this.pipelineDescriptor,
                this.semanticMaterial(),
                this.semanticOutputTarget(),
                false,
                firstVertex,
                vertexCount,
                0,
                0,
                1,
                0
            )) {
            if (!this.bindPipelineAndResources()) {
                return;
            }
            this.logDrawState(false, firstVertex, 0, 0, 0, vertexCount, 1, null);
            RenderPipeline pipeline = this.requirePipeline();
            VulkanicAPI.traceShaderInputParityGeometry(
                "vulkan-native-renderpass-geometry",
                this.vertexBuffer,
                null,
                pipeline.getVertexFormat(),
                pipeline.getVertexFormatMode(),
                false,
                firstVertex,
                vertexCount,
                0,
                0,
                null,
                1,
                0
            );
            int glPrimitiveMode = GlConst.toGl(this.requirePipeline().getVertexFormatMode());
            VulkanicAPI.traceShaderInputParityDraw(
                "vulkan-native-renderpass-encoded-draw",
                false,
                glPrimitiveMode,
                firstVertex,
                vertexCount,
                0L,
                0,
                0,
                1,
                0
            );
            this.pass.draw(firstVertex, vertexCount);
            }
        }

        private String semanticPassLabel() {
            if (this.customPass != null) {
                return this.customPass.getClass().getName();
            }
            return this.renderTargetDescriptor != null
                ? this.renderTargetDescriptor.debugSignature()
                : "legacy-renderpass";
        }

        private String semanticMaterial() {
            return this.renderPipeline != null ? this.renderPipeline.getLocation().toString() : "unknown";
        }

        private String semanticOutputTarget() {
            return this.renderTargetDescriptor != null
                ? this.renderTargetDescriptor.debugSignature()
                : "legacy-framebuffer";
        }

        @Override
        public void close() {
            if (this.closed) {
                return;
            }
            VulkanNativeCommandEncoder.this.ensureJavaVulkanRenderingAvailable();
            if (this.debugGroups != 0) {
                throw new IllegalStateException("Render pass had debug groups left open");
            }
            this.closed = true;
            try {
                VulkanicAPI.traceShaderInputParityOrdering(
                    "pass-end",
                    "vulkan-native-renderpass-close",
                    "target=" + this.semanticOutputTarget()
                );
                this.pass.close();
                VulkanNativeCommandEncoder.this.backend.submitCommandBuffer(this.ctx);
                VulkanicAPI.traceDeferredScopedCompositeColortex0SamplerReadbacks(
                    "vulkan-native-renderpass-close"
                );
                VulkanicAPI.recordScopedCompositeColortex0ProducerCompletion(
                    this.renderTargetDescriptor,
                    this.framebuffer,
                    this.renderPipeline,
                    this.customPass,
                    "vulkan-native-renderpass-close"
                );
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
                this.clearIrisProgramState();
            }
        }

        private boolean bindPipelineAndResources() {
            if (this.customPass != null) {
                return this.bindCustomPassPipelineAndResources(this.customPass);
            }

            RenderPipeline pipeline = this.requirePipeline();
            PipelineDescriptor baseDescriptor = this.requirePipelineDescriptor();
            PipelineDescriptor selectedDescriptor = this.selectDescriptor(pipeline, baseDescriptor);
            GlProgram irisProgram = resolveIrisOverrideProgram(pipeline);
            if (irisProgram != null) {
                this.setupIrisProgramStateIfNeeded(irisProgram);
                PipelineDescriptor liveDescriptor = createIrisProgramLiveDescriptor(this.ctx, pipeline, selectedDescriptor, irisProgram);
                if (liveDescriptor != null) {
                    selectedDescriptor = liveDescriptor;
                }
            }
            PipelineResourcePlanner.Plan submission = this.buildResourceBindings(selectedDescriptor, irisProgram);
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

            if (this.isCachedResourceSubmission(handle, submission, false)) {
                this.rememberSubmittedResources(handle, submission);
                return true;
            }

            this.pass.setPipeline(handle);
            VulkanNativeCommandEncoder.this.backend.bindPipelineResources(this.ctx, handle, submission.descriptor(), submission.bindings());
            this.cacheSubmittedResources(handle, submission, false);
            return true;
        }

        private void setupIrisProgramStateIfNeeded(@Nullable GlProgram program) {
            if (!(program instanceof IrisProgram irisProgram) || irisProgram.iris$isSetUp()) {
                return;
            }

            VulkanicTextureView sampler0 = this.samplers.get("Sampler0");
            if (sampler0 != null) {
                IrisRenderSystem.setTextureBinding(
                    0,
                    VulkanNativeCommandEncoder.this.backend.resolveTextureHandle(this.ctx, sampler0.texture())
                );
            }
            irisProgram.iris$setupState();
            if (this.irisProgramsToClear.size() >= MAX_RENDER_PASS_IRIS_PROGRAM_STATES) {
                irisProgram.iris$clearState();
                throw new IllegalStateException(
                    "Vulkan native render pass exceeded the bounded Iris program-state limit of "
                        + MAX_RENDER_PASS_IRIS_PROGRAM_STATES
                );
            }
            this.irisProgramsToClear.add(irisProgram);
        }

        private void clearIrisProgramState() {
            for (IrisProgram irisProgram : this.irisProgramsToClear) {
                irisProgram.iris$clearState();
            }
            this.irisProgramsToClear.clear();
        }

        private boolean bindCustomPassPipelineAndResources(CustomPass pass) {
            RenderPipeline pipeline = this.requirePipeline();
            pass.bindRenderPassResources(this);
            pass.setupState();

            PipelineDescriptor customDescriptor = pass.pipelineDescriptor();
            if (customDescriptor == null) {
                throw new IllegalStateException("No Vulkan custom-pass pipeline descriptor is available for " + pipeline.getLocation());
            }

            PipelineResourcePlanner.Plan submission = this.buildCustomPassResourceBindings(customDescriptor, pass.program());
            if (DEBUG_DESCRIPTOR_BINDINGS && debugCustomPassLogs < 160) {
                debugCustomPassLogs++;
                Program program = pass.program();
                LOGGER.info(
                    "Vulkan native customPass plan#{} pipeline={} framebuffer={} descriptorTarget={} programId={} baseBindings={} boundResourceCount={} completeCoverage={} samplerNames={} samplerUnits={} uniformNames={} missingResources={}",
                    debugCustomPassLogs,
                    pipeline.getLocation(),
                    this.framebuffer,
                    this.renderTargetDescriptor != null,
                    program != null ? program.getProgramId() : -1,
                    customDescriptor.getResourceLayout().bindings().stream().map(PipelineDescriptor.ResourceBinding::name).toList(),
                    submission.boundResourceCount(),
                    submission.completeCoverage(),
                    this.describeSamplerTextures(),
                    this.samplerUnits,
                    this.uniforms.keySet(),
                    submission.completeCoverage()
                        ? java.util.List.of()
                        : this.collectMissingCustomPassResources(customDescriptor, program)
                );
            }
            if (!submission.completeCoverage()) {
                throw new IllegalStateException(
                    "Incomplete Vulkan native custom-pass resource coverage for "
                        + pipeline.getLocation()
                        + ": only "
                        + submission.boundResourceCount()
                        + " of "
                        + customDescriptor.getResourceLayout().bindings().size()
                        + " reflected resources were available; missingResources="
                        + this.collectMissingCustomPassResources(customDescriptor, pass.program())
                );
            }

            PipelineHandle handle = pass.pipelineHandle(submission.descriptor());
            if (DEBUG_DESCRIPTOR_BINDINGS && debugCustomPassLogs < 160) {
                debugCustomPassLogs++;
                Program program = pass.program();
                LOGGER.info(
                    "Vulkan native customPass pipeline#{} pipeline={} framebuffer={} descriptorTarget={} programId={} variantLayout={} resolvedPipelineHandle={}",
                    debugCustomPassLogs,
                    pipeline.getLocation(),
                    this.framebuffer,
                    this.renderTargetDescriptor != null,
                    program != null ? program.getProgramId() : -1,
                    !submission.descriptor().getResourceLayout().equals(customDescriptor.getResourceLayout()),
                    handle != null && handle.isValid()
                );
            }
            if (handle == null || !handle.isValid()) {
                throw new IllegalStateException("Unable to resolve native Vulkan custom-pass pipeline handle for " + pipeline.getLocation());
            }

            if (this.isCachedResourceSubmission(handle, submission, true)) {
                this.rememberSubmittedResources(handle, submission);
                return true;
            }

            this.pass.setPipeline(handle);
            VulkanNativeCommandEncoder.this.backend.bindPipelineResources(
                this.ctx,
                handle,
                submission.descriptor(),
                submission.bindings()
            );
            this.cacheSubmittedResources(handle, submission, true);
            return true;
        }

        private boolean isCachedResourceSubmission(
            PipelineHandle handle,
            PipelineResourcePlanner.Plan submission,
            boolean customPass
        ) {
            CachedResourceSubmission cachedSubmission = this.cachedResourceSubmission;
            return cachedSubmission != null
                && cachedSubmission.pipelineHandle() == handle
                && cachedSubmission.resourceStateGeneration() == this.resourceStateGeneration
                && cachedSubmission.customPass() == customPass
                && Objects.equals(cachedSubmission.descriptor(), submission.descriptor())
                && Objects.equals(cachedSubmission.bindings(), submission.bindings());
        }

        private void cacheSubmittedResources(
            PipelineHandle handle,
            PipelineResourcePlanner.Plan submission,
            boolean customPass
        ) {
            this.cachedResourceSubmission = new CachedResourceSubmission(
                handle,
                submission.descriptor(),
                submission.bindings(),
                this.resourceStateGeneration,
                customPass
            );
            this.rememberSubmittedResources(handle, submission);
        }

        private void rememberSubmittedResources(PipelineHandle handle, PipelineResourcePlanner.Plan submission) {
            this.lastPipelineHandle = handle;
            this.lastSubmittedDescriptor = submission.descriptor();
            this.lastSubmittedPlan = submission;
        }

        private void logDrawState(
            boolean indexed,
            int firstVertex,
            int baseVertex,
            int firstIndex,
            int indexCount,
            int vertexCount,
            int instanceCount,
            @Nullable VertexFormat.IndexType drawIndexType
        ) {
            if (!VulkanicDrawStateDiagnostics.enabled()) {
                return;
            }

            RenderPipeline pipeline = this.requirePipeline();
            PipelineDescriptor submittedDescriptor = this.lastSubmittedDescriptor != null
                ? this.lastSubmittedDescriptor
                : this.requirePipelineDescriptor();
            PipelineResourcePlanner.Plan plan = this.lastSubmittedPlan;
            int colorAttachmentCount = this.colorAttachmentCount();
            VulkanicDrawStateSnapshot.TranslatedPipelineState translatedState =
                VulkanNativeCommandEncoder.this.backend.describeTranslatedPipelineState(
                    submittedDescriptor,
                    colorAttachmentCount
                );
            VulkanicDrawStateSnapshot.ScissorStateSnapshot scissor = this.scissorEnabled
                ? new VulkanicDrawStateSnapshot.ScissorStateSnapshot(
                    true,
                    this.scissorX,
                    this.scissorY,
                    this.scissorWidth,
                    this.scissorHeight
                )
                : VulkanicDrawStateSnapshot.ScissorStateSnapshot.disabled();
            VulkanicDrawStateSnapshot.DrawCall draw = new VulkanicDrawStateSnapshot.DrawCall(
                indexed,
                firstVertex,
                baseVertex,
                firstIndex,
                indexCount,
                vertexCount,
                instanceCount,
                drawIndexType
            );
            VulkanicDrawStateSnapshot.ResourceState resources = new VulkanicDrawStateSnapshot.ResourceState(
                submittedDescriptor.getResourceLayout().bindings().size(),
                plan != null ? plan.boundResourceCount() : 0,
                this.samplers.size(),
                this.uniforms.size(),
                plan != null ? plan.missingResources() : java.util.List.of()
            );

            VulkanicDrawStateDiagnostics.log(VulkanicDrawStateSnapshot.create(
                "vulkan",
                VulkanNativeCommandEncoder.this.resourceMode == ResourceMode.TERRAIN
                    ? "VulkanNativeTerrainCommandEncoder"
                    : "VulkanNativeCommandEncoder",
                pipeline,
                this.renderTargetDescriptor != null ? this.renderTargetDescriptor.debugSignature() : "framebuffer-or-texture-view",
                this.framebuffer,
                this.hasDepthAttachment,
                colorAttachmentCount,
                translatedState,
                VulkanicAPI.drawStateParityViewportSnapshot(),
                scissor,
                draw,
                resources
            ));
        }

        private int colorAttachmentCount() {
            if (this.renderTargetDescriptor != null) {
                return this.renderTargetDescriptor.colorAttachments().size();
            }
            if (this.colorView != null) {
                return 1;
            }
            return this.framebuffer != 0 ? 1 : 0;
        }

        private Map<String, String> describeSamplerTextures() {
            Map<String, String> descriptions = new java.util.TreeMap<>();
            for (Map.Entry<String, VulkanicTextureView> entry : this.samplers.entrySet()) {
                VulkanicTextureView view = entry.getValue();
                if (view == null || view.texture() == null) {
                    descriptions.put(entry.getKey(), "null");
                    continue;
                }
                descriptions.put(
                    entry.getKey(),
                    "texId=" + VulkanNativeCommandEncoder.this.backend.resolveTextureHandle(this.ctx, view.texture())
                        + ",label=" + view.texture().getLabel()
                        + ",baseMip=" + view.getBaseMipLevel()
                        + ",mips=" + view.getMipLevelCount()
                );
            }
            return descriptions;
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
                        case UNIFORM_BUFFER, STORAGE_IMAGE, TEXEL_BUFFER -> true;
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
            return this.buildResourceBindings(descriptor, null);
        }

        @Nullable
        private PipelineResourcePlanner.Plan buildResourceBindings(
            PipelineDescriptor descriptor,
            @Nullable GlProgram irisProgram
        ) {
            PipelineResourcePlanner.Plan submission = VulkanicPipelineResourceResolver.buildPlan(
                this.ctx,
                descriptor,
                irisProgram != null ? this.irisProgramResourceLookup(irisProgram) : this.pipelineResourceLookup(),
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

        private PipelineResourcePlanner.Plan buildCustomPassResourceBindings(
            PipelineDescriptor descriptor,
            @Nullable Program program
        ) {
            return VulkanicPipelineResourceResolver.buildPlan(
                this.ctx,
                descriptor,
                this.customPassResourceLookup(program),
                PipelineResourcePlanner.options()
                    .requireAtLeastOneBinding(false)
                    .filterIncompleteLayout(false)
                    .missingResourceDescriber(PipelineResourcePlanner.MissingResourceDescriber.NONE)
            );
        }

        private java.util.List<String> collectMissingCustomPassResources(
            PipelineDescriptor descriptor,
            @Nullable Program program
        ) {
            return VulkanicPipelineResourceResolver.collectMissingResources(
                this.ctx,
                descriptor,
                this.customPassResourceLookup(program)
            );
        }

        private VulkanicPipelineResourceResolver.ResourceLookup pipelineResourceLookup() {
            return new VulkanicPipelineResourceResolver.ResourceLookup() {
                @Override
                @Nullable
                public VulkanicTextureView samplerView(PipelineDescriptor.ResourceBinding binding) {
                    return NativeRenderPass.this.getSamplerView(binding.name());
                }

                @Override
                @Nullable
                public Integer samplerUnit(PipelineDescriptor.ResourceBinding binding) {
                    return NativeRenderPass.this.getSamplerView(binding.name()) != null
                        ? NativeRenderPass.this.resolveSamplerUnit(binding)
                        : null;
                }

                @Override
                @Nullable
                public VulkanicBufferSlice uniformBufferSlice(PipelineDescriptor.ResourceBinding binding) {
                    return NativeRenderPass.this.uniforms.get(binding.name());
                }

                @Override
                @Nullable
                public Integer texelBufferUnit(PipelineDescriptor.ResourceBinding binding) {
                    return null;
                }

                @Override
                @Nullable
                public PipelineResourceBindings.StorageImageBinding storageImageBinding(PipelineDescriptor.ResourceBinding binding) {
                    if (VulkanNativeCommandEncoder.this.resourceMode != ResourceMode.TERRAIN
                        || NativeRenderPass.this.renderPipeline == null) {
                        return null;
                    }
                    int programId = SharedChunkProgramOverrides.activeProgramHandle(NativeRenderPass.this.renderPipeline);
                    return programId > 0
                        ? VulkanNativeCommandEncoder.this.backend.resolveLegacyStorageImageBindingForProgram(programId, binding)
                        : null;
                }

                @Override
                @Nullable
                public Integer standaloneProgramId(PipelineDescriptor.ResourceBinding binding) {
                    if (VulkanNativeCommandEncoder.this.resourceMode != ResourceMode.TERRAIN
                        || !VulkanicAPI.generatedStandaloneUniformBlockName().equals(binding.name())) {
                        return -1;
                    }
                    return NativeRenderPass.this.renderPipeline != null
                        ? SharedChunkProgramOverrides.activeProgramHandle(NativeRenderPass.this.renderPipeline)
                        : -1;
                }

                @Override
                @Nullable
                public Integer samplerObject(int samplerUnit) {
                    return currentBoundSamplerObject(samplerUnit);
                }
            };
        }

        private VulkanicPipelineResourceResolver.ResourceLookup irisProgramResourceLookup(GlProgram program) {
            int programId = program.getProgramId();
            return new VulkanicPipelineResourceResolver.ResourceLookup() {
                @Override
                @Nullable
                public VulkanicTextureView samplerView(PipelineDescriptor.ResourceBinding binding) {
                    return VulkanNativeCommandEncoder.this.backend.resolveLegacySamplerViewForProgram(
                        NativeRenderPass.this.ctx,
                        binding,
                        programId
                    );
                }

                @Override
                @Nullable
                public Integer samplerUnit(PipelineDescriptor.ResourceBinding binding) {
                    return VulkanNativeCommandEncoder.this.backend.resolveLegacySamplerUnitForProgram(programId, binding);
                }

                @Override
                @Nullable
                public VulkanicBufferSlice uniformBufferSlice(PipelineDescriptor.ResourceBinding binding) {
                    return NativeRenderPass.this.uniforms.get(binding.name());
                }

                @Override
                @Nullable
                public Integer texelBufferUnit(PipelineDescriptor.ResourceBinding binding) {
                    return samplerUnit(binding);
                }

                @Override
                @Nullable
                public PipelineResourceBindings.StorageImageBinding storageImageBinding(PipelineDescriptor.ResourceBinding binding) {
                    return VulkanNativeCommandEncoder.this.backend.resolveLegacyStorageImageBindingForProgram(programId, binding);
                }

                @Override
                @Nullable
                public Integer standaloneProgramId(PipelineDescriptor.ResourceBinding binding) {
                    return programId;
                }

                @Override
                @Nullable
                public Integer samplerObject(int samplerUnit) {
                    return currentBoundSamplerObject(samplerUnit);
                }
            };
        }

        private VulkanicPipelineResourceResolver.ResourceLookup customPassResourceLookup(@Nullable Program program) {
            Map<String, Integer> renderPassSamplerUnits =
                program != null ? program.getRenderPassSamplerUnits() : Map.of();

            return new VulkanicPipelineResourceResolver.ResourceLookup() {
                @Override
                @Nullable
                public VulkanicTextureView samplerView(PipelineDescriptor.ResourceBinding binding) {
                    Integer samplerUnit = renderPassSamplerUnits.get(binding.name());
                    return samplerUnit != null
                        ? NativeRenderPass.this.getSamplerView(binding.name(), samplerUnit)
                        : null;
                }

                @Override
                @Nullable
                public Integer samplerUnit(PipelineDescriptor.ResourceBinding binding) {
                    return renderPassSamplerUnits.get(binding.name());
                }

                @Override
                @Nullable
                public VulkanicBufferSlice uniformBufferSlice(PipelineDescriptor.ResourceBinding binding) {
                    return NativeRenderPass.this.uniforms.get(binding.name());
                }

                @Override
                @Nullable
                public Integer texelBufferUnit(PipelineDescriptor.ResourceBinding binding) {
                    return renderPassSamplerUnits.get(binding.name());
                }

                @Override
                @Nullable
                public PipelineResourceBindings.StorageImageBinding storageImageBinding(PipelineDescriptor.ResourceBinding binding) {
                    return program != null
                        ? VulkanNativeCommandEncoder.this.backend.resolveLegacyStorageImageBindingForProgram(program.getProgramId(), binding)
                        : null;
                }

                @Override
                @Nullable
                public Integer standaloneProgramId(PipelineDescriptor.ResourceBinding binding) {
                    return program != null ? program.getProgramId() : -1;
                }

                @Override
                @Nullable
                public Integer samplerObject(int samplerUnit) {
                    return currentBoundSamplerObject(samplerUnit);
                }
            };
        }

        @Nullable
        private VulkanicTextureView getSamplerView(String name) {
            return this.getSamplerView(name, null);
        }

        @Nullable
        private VulkanicTextureView getSamplerView(String name, @Nullable Integer textureUnit) {
            VulkanicTextureView view = this.samplers.get(name);
            if (view != null) {
                return view;
            }

            GpuTextureView recovered = this.recoverSamplerView(name, textureUnit);
            if (recovered == null) {
                return null;
            }

            this.bindSampler(name, recovered, textureUnit != null ? textureUnit : -1);
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
        private GpuTextureView recoverSamplerView(String name, @Nullable Integer textureUnit) {
            if (RustGalVulkanWholeFrameMode.enabled()
                || VulkanicAPI.isVulkanBackendSelected()) {
                // This compatibility encoder is not admitted on the Rust-owned route;
                // never recover bindings from Iris' runtime texture cache there.
                return null;
            }
            Integer samplerIndex = textureUnit != null ? textureUnit : parseSamplerIndex(name);
            if (samplerIndex != null) {
                GpuTextureView shaderTexture = TextureTracker.INSTANCE.getShaderTexture(samplerIndex);
                if (shaderTexture != null) {
                    return shaderTexture;
                }

                int textureId = IrisRenderSystem.getTextureBinding(samplerIndex);
                if (textureId != 0) {
                    return TextureTracker.INSTANCE.getTextureView(textureId);
                }
            }

            return null;
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

        private <K, V> void ensureBindingCapacity(Map<K, V> bindings, K key, int limit, String kind) {
            if (!bindings.containsKey(key) && bindings.size() >= limit) {
                throw new IllegalStateException(
                    "Vulkan native render pass exceeded the bounded " + kind + " limit of " + limit
                );
            }
        }

        private void markResourceBindingsDirty() {
            this.resourceStateGeneration++;
            this.cachedResourceSubmission = null;
        }

        private void unsupported(String operation) {
            throw new UnsupportedOperationException("Vulkan native render pass does not support " + operation);
        }

        private void checkOpen() {
            VulkanNativeCommandEncoder.this.ensureJavaVulkanRenderingAvailable();
            if (this.closed) {
                throw new IllegalStateException("Can't use a closed render pass");
            }
        }
    }

    private PipelineResourcePlanner.Options resourcePlannerOptions() {
        PipelineResourcePlanner.Options options = PipelineResourcePlanner.options();
        if (this.resourceMode == ResourceMode.GENERAL) {
            options = options.requireAtLeastOneBinding(false);
        } else if (this.resourceMode == ResourceMode.TERRAIN) {
            options = options.filterIncompleteLayout(false);
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
        if (RustGalVulkanWholeFrameMode.enabled()
            || VulkanicAPI.isVulkanBackendSelected()) {
            return null;
        }
        int samplerObject = IrisRenderSystem.getBoundSamplerOnUnit(samplerUnit);
        return samplerObject > 0 ? samplerObject : null;
    }

    /**
     * The ImmediateState flag belongs to Iris' borrowed Java compatibility
     * encoder.  Rust-owned Vulkan must not read that live GPU-state shard even
     * for upload bookkeeping; its explicit command stream has its own pass
     * boundaries.
     */
    private static boolean legacyImmediatePassIgnored() {
        return !RustGalVulkanWholeFrameMode.enabled()
            && !VulkanicAPI.isVulkanBackendSelected()
            && net.irisshaders.iris.vertices.ImmediateState.temporarilyIgnorePass;
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

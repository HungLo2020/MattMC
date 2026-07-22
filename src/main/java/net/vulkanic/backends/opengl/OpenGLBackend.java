package net.vulkanic.backends.opengl;

import net.blaze3d.opengl.GlDevice;
import net.blaze3d.pipeline.CompiledRenderPipeline;
import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.shaders.ShaderType;
import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.systems.CommandEncoder;
import net.blaze3d.systems.GpuDevice;
import net.blaze3d.textures.GpuTexture;
import net.blaze3d.textures.GpuTextureView;
import net.blaze3d.textures.TextureFormat;
import net.vulkanic.CommandContext;
import net.vulkanic.GraphicsBackend;
import net.vulkanic.GraphicsBackendType;
import net.vulkanic.GraphicsCapabilities;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicBlendEquation;
import net.vulkanic.VulkanicBlendFactor;
import net.vulkanic.VulkanicBufferTarget;
import net.vulkanic.VulkanicCapability;
import net.vulkanic.VulkanicClearBuffer;
import net.vulkanic.VulkanicCompatibilityState;
import net.vulkanic.VulkanicCullFaceMode;
import net.vulkanic.VulkanicDepthCompareOp;
import net.vulkanic.VulkanicGalExecutionRequest;
import net.vulkanic.VulkanicGalV2;
import net.vulkanic.VulkanicPassResourceModel;
import net.vulkanic.VulkanicGalSnapshotBuilder;
import net.vulkanic.VulkanicIntegerQuery;
import net.vulkanic.VulkanicProgramHandle;
import net.vulkanic.VulkanicShaderHandle;
import net.vulkanic.VulkanicStencilCompareOp;
import net.vulkanic.VulkanicStencilFace;
import net.vulkanic.VulkanicStencilOperation;
import net.vulkanic.VulkanicTextureParameterName;
import net.vulkanic.VulkanicTextureTarget;
import net.vulkanic.VulkanicTextureUploadFormat;
import net.vulkanic.VulkanPerfAudit;
import net.vulkanic.VulkanExecutionContextInfo;
import net.vulkanic.VulkanNativeInitializationInfo;
import net.vulkanic.VulkanSwapchainSurfaceInfo;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.*;

import java.util.function.BiFunction;

/**
 * OpenGL implementation of the Vulkanic Graphics Backend.
 * This is the ONLY place where direct OpenGL calls should be made.
 */
public class OpenGLBackend implements GraphicsBackend {

    private static final int TEXTURE_UNIT_COUNT = 128;
    private final int[] texture2DBindings = new int[TEXTURE_UNIT_COUNT];
    private final java.util.concurrent.ConcurrentMap<Integer, java.util.concurrent.ConcurrentMap<Integer, String>> uniformNamesByProgramLocation =
        new java.util.concurrent.ConcurrentHashMap<>();
    private int activeTextureUnitIndex = 0;
    private long appliedProgramVersion = Long.MIN_VALUE;
    private long appliedFramebufferVersion = Long.MIN_VALUE;
    private long appliedFixedFunctionVersion = Long.MIN_VALUE;
    private long appliedResourceBindingVersion = Long.MIN_VALUE;
    private long appliedVertexInputVersion = Long.MIN_VALUE;
    private long appliedUniformVersion = Long.MIN_VALUE;
    private String appliedGalV2VertexInputKey = "";
    private final java.util.concurrent.ConcurrentMap<VulkanicGalV2.Handle, Integer> galV2VertexArrays =
        new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<Integer> appliedGalV2Attributes = new java.util.HashSet<>();
    private volatile int presentScratchReadFbo;

    /**
     * Reference to the GlDevice, set after device initialization.
     * Used for delegating device-level operations (pipeline compilation, etc.)
     * that require access to the device's shader cache and DirectStateAccess.
     */
    private volatile net.blaze3d.opengl.GlDevice glDevice;
    private volatile String backendVendorName = "unknown";
    private volatile String backendRendererName = "unknown";
    private volatile String backendVersionName = "unknown";

    @Override
    public boolean requiresEagerGraphicsResourceDeclarations() {
        return false;
    }

    private void invalidateAppliedDrawState() {
        appliedProgramVersion = Long.MIN_VALUE;
        appliedFramebufferVersion = Long.MIN_VALUE;
        appliedFixedFunctionVersion = Long.MIN_VALUE;
        appliedResourceBindingVersion = Long.MIN_VALUE;
        appliedVertexInputVersion = Long.MIN_VALUE;
        appliedUniformVersion = Long.MIN_VALUE;
        appliedGalV2VertexInputKey = "";
        appliedGalV2Attributes.clear();
    }

    private void invalidateAppliedFramebufferState() {
        appliedFramebufferVersion = Long.MIN_VALUE;
    }

    private void invalidateAppliedResourceAndVertexState() {
        appliedFramebufferVersion = Long.MIN_VALUE;
        appliedResourceBindingVersion = Long.MIN_VALUE;
        appliedVertexInputVersion = Long.MIN_VALUE;
    }

    private static boolean supportsDepthStencilTextureMode() {
        org.lwjgl.opengl.GLCapabilities capabilities = org.lwjgl.opengl.GL.getCapabilities();
        return capabilities.OpenGL43 || capabilities.GL_ARB_stencil_texturing;
    }

    /**
     * Registers the GlDevice with this backend.
     * Called from GlDevice's constructor after the device is fully initialized.
     *
     * @param device the newly created GlDevice
     */
    public void setGlDevice(net.blaze3d.opengl.GlDevice device) {
        this.glDevice = device;
        if (device != null) {
            this.backendVendorName = normalizeDeviceIdentity(device.getVendor());
            this.backendRendererName = normalizeDeviceIdentity(device.getRenderer());
            this.backendVersionName = normalizeDeviceIdentity(device.getVersion());
        }
    }

    /**
     * Returns the registered GlDevice, or null if not yet initialized.
     */
    public net.blaze3d.opengl.GlDevice getGlDevice() {
        return glDevice;
    }
    
    @Override
    public long getGraphicsContext() {
        // Platform-specific: On Windows, return the WGL context handle
        // On other platforms, this would use different APIs (GLX on Linux, CGL on macOS)
        try {
            return org.lwjgl.opengl.WGL.wglGetCurrentContext();
        } catch (UnsatisfiedLinkError e) {
            // WGL native library not available - occurs on non-Windows platforms
            // or when the native library fails to load
            return 0L;
        }
    }

    @Override
    public CommandContext getCurrentCommandContext() {
        return OpenGLCommandContext.IMMEDIATE;
    }

    @Override
    public GraphicsBackendType getBackendType() {
        return GraphicsBackendType.OPENGL;
    }

    @Override
    public boolean isNativeVulkanReady() {
        return false;
    }

    @Override
    public VulkanExecutionContextInfo getVulkanExecutionContextInfo() {
        return VulkanExecutionContextInfo.unavailable(
            GraphicsBackendType.OPENGL,
            false,
            "OpenGL backend does not own a native Vulkan logical device or graphics queue."
        );
    }

    @Override
    public VulkanSwapchainSurfaceInfo getVulkanSwapchainSurfaceInfo() {
        return VulkanSwapchainSurfaceInfo.unavailable(
            GraphicsBackendType.OPENGL,
            false,
            "OpenGL backend does not own a Vulkan surface or swapchain."
        );
    }

    @Override
    public VulkanNativeInitializationInfo initializeNativeVulkanRuntime() {
        return VulkanNativeInitializationInfo.unsupported(
            GraphicsBackendType.OPENGL,
            "OpenGL backend does not support native Vulkan runtime initialization."
        );
    }

    @Override
    public GpuDevice createRendererDevice(
        long rendererBootstrapWindowHandle,
        int debugVerbosity,
        boolean debugEnabled,
        BiFunction<ResourceLocation, ShaderType, String> defaultShaderSource,
        boolean debugLabelsEnabled
    ) {
        return new GlDevice(rendererBootstrapWindowHandle, debugVerbosity, debugEnabled, defaultShaderSource, debugLabelsEnabled);
    }

    @Override
    public CommandEncoder createCommandEncoder() {
        GlDevice device = this.glDevice;
        if (device == null) {
            throw new IllegalStateException(
                "GlDevice has not been registered with OpenGLBackend. "
                    + "Ensure GlDevice calls VulkanicAPI.registerDevice() during initialization.");
        }

        return device.createCommandEncoder();
    }

    @Override
    public net.blaze3d.systems.RenderPass createRenderPass(
        java.util.function.Supplier<String> supplier,
        GpuTextureView colorTextureView,
        java.util.OptionalInt clearColor
    ) {
        return createCommandEncoder().createRenderPass(supplier, colorTextureView, clearColor);
    }

    @Override
    public net.blaze3d.systems.RenderPass createRenderPass(
        java.util.function.Supplier<String> supplier,
        GpuTextureView colorTextureView,
        java.util.OptionalInt clearColor,
        @Nullable GpuTextureView depthTextureView,
        java.util.OptionalDouble clearDepth
    ) {
        return createCommandEncoder().createRenderPass(supplier, colorTextureView, clearColor, depthTextureView, clearDepth);
    }

    @Override
    public GpuTexture createTexture(
        @Nullable java.util.function.Supplier<String> supplier,
        int usage,
        TextureFormat textureFormat,
        int width,
        int height,
        int depthOrLayers,
        int mipLevels
    ) {
        GlDevice device = this.glDevice;
        if (device == null) {
            throw new IllegalStateException(
                "GlDevice has not been registered with OpenGLBackend. "
                    + "Ensure GlDevice calls VulkanicAPI.registerDevice() during initialization.");
        }
        return device.createTexture(supplier, usage, textureFormat, width, height, depthOrLayers, mipLevels);
    }

    @Override
    public GpuTexture createTexture(
        @Nullable String label,
        int usage,
        TextureFormat textureFormat,
        int width,
        int height,
        int depthOrLayers,
        int mipLevels
    ) {
        GlDevice device = this.glDevice;
        if (device == null) {
            throw new IllegalStateException(
                "GlDevice has not been registered with OpenGLBackend. "
                    + "Ensure GlDevice calls VulkanicAPI.registerDevice() during initialization.");
        }
        return device.createTexture(label, usage, textureFormat, width, height, depthOrLayers, mipLevels);
    }

    @Override
    public GpuBuffer createBuffer(@Nullable java.util.function.Supplier<String> supplier, int usage, int size) {
        GlDevice device = this.glDevice;
        if (device == null) {
            throw new IllegalStateException(
                "GlDevice has not been registered with OpenGLBackend. "
                    + "Ensure GlDevice calls VulkanicAPI.registerDevice() during initialization.");
        }
        return device.createBuffer(supplier, usage, size);
    }

    @Override
    public GpuBuffer createBuffer(@Nullable java.util.function.Supplier<String> supplier, int usage, java.nio.ByteBuffer data) {
        GlDevice device = this.glDevice;
        if (device == null) {
            throw new IllegalStateException(
                "GlDevice has not been registered with OpenGLBackend. "
                    + "Ensure GlDevice calls VulkanicAPI.registerDevice() during initialization.");
        }
        return device.createBuffer(supplier, usage, data);
    }

    @Override
    public GpuTextureView createTextureView(GpuTexture texture) {
        GlDevice device = this.glDevice;
        if (device == null) {
            throw new IllegalStateException(
                "GlDevice has not been registered with OpenGLBackend. "
                    + "Ensure GlDevice calls VulkanicAPI.registerDevice() during initialization.");
        }
        return device.createTextureView(texture);
    }

    @Override
    public GpuTextureView createTextureView(GpuTexture texture, int baseMipLevel, int mipLevelCount) {
        GlDevice device = this.glDevice;
        if (device == null) {
            throw new IllegalStateException(
                "GlDevice has not been registered with OpenGLBackend. "
                    + "Ensure GlDevice calls VulkanicAPI.registerDevice() during initialization.");
        }
        return device.createTextureView(texture, baseMipLevel, mipLevelCount);
    }

    @Override
    public void onRendererDeviceInitialized(long mainWindowHandle, GpuDevice gpuDevice) {
        net.sodium.client.compatibility.environment.GlContextInfo context = net.sodium.client.compatibility.environment.GlContextInfo.create();
        org.slf4j.Logger logger = net.logging.LogUtils.getLogger();
        logger.info("OpenGL Vendor: {}", context.vendor());
        logger.info("OpenGL Renderer: {}", context.renderer());
        logger.info("OpenGL Version: {}", context.version());

        // Cache stable identity strings while a valid context is known to exist.
        this.backendVendorName = normalizeDeviceIdentity(context.vendor());
        this.backendRendererName = normalizeDeviceIdentity(context.renderer());
        this.backendVersionName = normalizeDeviceIdentity(context.version());

        net.sodium.client.platform.NativeWindowHandle handle = () -> org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window(mainWindowHandle);
        net.sodium.client.compatibility.checks.PostLaunchChecks.onContextInitialized(handle, context);
        net.sodium.client.compatibility.checks.ModuleScanner.checkModules(handle);

        net.irisshaders.iris.Iris.duringRenderSystemInit();
        net.irisshaders.iris.gl.GLDebug.reloadDebugState();
        net.irisshaders.iris.gl.IrisRenderSystem.initRenderer();
        net.irisshaders.iris.samplers.IrisSamplers.initRenderer();
        net.irisshaders.iris.Iris.onRenderSystemInit();
    }

    @Override
    public CompiledRenderPipeline precompileRenderPipeline(
        RenderPipeline renderPipeline,
        @Nullable BiFunction<ResourceLocation, ShaderType, String> sourceProvider
    ) {
        if (renderPipeline == null) {
            throw new IllegalArgumentException("renderPipeline must not be null");
        }

        GlDevice device = this.glDevice;
        if (device == null) {
            throw new IllegalStateException(
                "GlDevice has not been registered with OpenGLBackend. "
                    + "Ensure GlDevice calls VulkanicAPI.registerDevice() during initialization.");
        }

        return device.precompilePipeline(renderPipeline, sourceProvider);
    }

    @Override
    public void clearPrecompiledPipelineCache() {
        GlDevice device = this.glDevice;
        if (device != null) {
            device.clearPipelineCache();
        }
    }
    
    /**
     * Sets the dynamic viewport state with explicit command context.
     * This is the Vulkan-compatible implementation for viewport control.
     * 
     * OpenGL implementation: Direct mapping to glViewport() (context is validated but not used)
     * Vulkan implementation: Will map to vkCmdSetViewport(ctx.getHandle(), ...)
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     */
    @Override
    public void setDynamicViewport(CommandContext ctx, int x, int y, int width, int height) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL11.glViewport(x, y, width, height);
    }
    
    private void clearBuffers(CommandContext ctx, int mask) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glClear(mask);
    }

    @Override
    public VulkanicGalExecutionRequest.ExecutionResult executeClear(CommandContext ctx, VulkanicGalExecutionRequest.ClearRequest request) {
        long auditStartNanos = VulkanPerfAudit.isEnabled() ? System.nanoTime() : 0L;
        try {
            consumeResourceUsagePlan(request.resourcePlan());
            clearBuffers(ctx, VulkanicClearBuffer.toLegacyGlMask(request.buffers().toArray(VulkanicClearBuffer[]::new)));
            return VulkanicGalExecutionRequest.success(request.semanticIdentity());
        } catch (RuntimeException exception) {
            invalidateAppliedDrawState();
            return VulkanicGalExecutionRequest.backendFailure(request.semanticIdentity(), exception.getMessage());
        } finally {
            if (auditStartNanos != 0L) {
                VulkanPerfAudit.recordPhase("backend.opengl.clear", System.nanoTime() - auditStartNanos);
            }
        }
    }

    @Override
    public VulkanicGalExecutionRequest.ExecutionResult executeTransfer(CommandContext ctx, VulkanicGalExecutionRequest.TransferRequest request) {
        long auditStartNanos = VulkanPerfAudit.isEnabled() ? System.nanoTime() : 0L;
        try {
            invalidateAppliedResourceAndVertexState();
            java.util.Objects.requireNonNull(request, "request");
            consumeResourceUsagePlan(request.resourcePlan());
            VulkanicGalExecutionRequest.TransferCompatibilitySnapshot transfer = request.requireTransferSnapshot();
            VulkanicGalExecutionRequest.TransferOperation op = request.operation();
            switch (op) {
                case VulkanicGalExecutionRequest.CopyBufferSubData o -> copyBufferSubData(ctx, transfer.sourceLegacyTargetOr(0, o.readTarget()), transfer.destinationLegacyTargetOr(0, o.writeTarget()), o.readOffset(), o.writeOffset(), o.size());
                case VulkanicGalExecutionRequest.CopyNamedBufferSubData o -> copyNamedBufferSubDataDSA(ctx, transfer.sourceLegacyIdOr(0, o.readBuffer()), transfer.destinationLegacyIdOr(0, o.writeBuffer()), o.readOffset(), o.writeOffset(), o.size());
                case VulkanicGalExecutionRequest.CopyImageSubData o -> copyImageSubData(ctx, transfer.sourceLegacyIdOr(0, o.srcName()), transfer.sourceLegacyTargetOr(0, o.srcTarget()), o.srcLevel(), o.srcX(), o.srcY(), o.srcZ(), transfer.destinationLegacyIdOr(0, o.dstName()), transfer.destinationLegacyTargetOr(0, o.dstTarget()), o.dstLevel(), o.dstX(), o.dstY(), o.dstZ(), o.width(), o.height(), o.depth());
                case VulkanicGalExecutionRequest.CopyTextureSubImage2D o -> copyTextureSubImage2D(ctx, transfer.destinationLegacyIdOr(0, o.texture()), o.level(), o.xOffset(), o.yOffset(), o.x(), o.y(), o.width(), o.height());
                case VulkanicGalExecutionRequest.CopyTexImage2D o -> copyTexImage2D(ctx, transfer.destinationLegacyTargetOr(0, o.target()), o.level(), o.internalFormat(), o.x(), o.y(), o.width(), o.height(), o.border());
                case VulkanicGalExecutionRequest.CopyTexSubImage2D o -> copyTexSubImage2D(ctx, transfer.destinationLegacyTargetOr(0, o.target()), o.level(), o.xOffset(), o.yOffset(), o.x(), o.y(), o.width(), o.height());
                case VulkanicGalExecutionRequest.BlitFramebuffer o -> blitFramebuffer(ctx, o.srcX0(), o.srcY0(), o.srcX1(), o.srcY1(), o.dstX0(), o.dstY0(), o.dstX1(), o.dstY1(), o.mask(), o.filter());
                case VulkanicGalExecutionRequest.BlitNamedFramebuffer o -> blitNamedFramebuffer(ctx, transfer.sourceLegacyIdOr(0, o.readFramebuffer()), transfer.destinationLegacyIdOr(0, o.drawFramebuffer()), o.srcX0(), o.srcY0(), o.srcX1(), o.srcY1(), o.dstX0(), o.dstY0(), o.dstX1(), o.dstY1(), o.mask(), o.filter());
                case VulkanicGalExecutionRequest.ReadPixelsPointer o -> readPixels(ctx, o.x(), o.y(), o.width(), o.height(), o.format(), o.type(), o.pixels());
                case VulkanicGalExecutionRequest.ReadPixelsFloatArray o -> readPixels(ctx, o.x(), o.y(), o.width(), o.height(), o.format(), o.type(), o.pixels());
                case VulkanicGalExecutionRequest.BufferSubData o -> bufferSubData(ctx, transfer.destinationLegacyTargetOr(0, o.target()), o.offset(), o.payload());
                case VulkanicGalExecutionRequest.NamedBufferSubData o -> namedBufferSubDataDSA(ctx, transfer.destinationLegacyIdOr(0, o.buffer()), o.offset(), o.payload());
                case VulkanicGalExecutionRequest.UploadTexture1D o -> uploadTexture1D(ctx, transfer.destinationLegacyTargetOr(0, o.target()), o.level(), o.internalFormat(), o.width(), o.border(), o.format(), o.type(), o.payload());
                case VulkanicGalExecutionRequest.UploadTexture2D o -> uploadTexture2D(ctx, transfer.destinationLegacyTargetOr(0, o.target()), o.level(), o.internalFormat(), o.width(), o.height(), o.border(), o.format(), o.type(), o.payload());
                case VulkanicGalExecutionRequest.UploadTexture2DSubImagePointer o -> uploadTexture2DSubImage(ctx, transfer.destinationLegacyTargetOr(0, o.target()), o.level(), o.xOffset(), o.yOffset(), o.width(), o.height(), o.format(), o.type(), o.pixels());
                case VulkanicGalExecutionRequest.UploadTexture2DSubImageBuffer o -> uploadTexture2DSubImage(ctx, transfer.destinationLegacyTargetOr(0, o.target()), o.level(), o.xOffset(), o.yOffset(), o.width(), o.height(), o.format(), o.type(), o.payload());
                case VulkanicGalExecutionRequest.UploadTexture3D o -> uploadTexture3D(ctx, transfer.destinationLegacyTargetOr(0, o.target()), o.level(), o.internalFormat(), o.width(), o.height(), o.depth(), o.border(), o.format(), o.type(), o.payload());
                case VulkanicGalExecutionRequest.ClearTexImageInt o -> clearTexImage(ctx, transfer.destinationLegacyIdOr(0, o.texture()), o.level(), o.format(), o.type(), o.data());
                case VulkanicGalExecutionRequest.ClearBufferSubDataInt o -> clearBufferSubData(ctx, transfer.destinationLegacyTargetOr(0, o.target()), o.internalFormat(), o.offset(), o.size(), o.format(), o.type(), o.data());
                case VulkanicGalExecutionRequest.ClearBufferFloat o -> clearBufferfv(ctx, o.buffer(), o.drawBuffer(), o.values());
                case VulkanicGalExecutionRequest.ClearBufferInt o -> clearBufferiv(ctx, o.buffer(), o.drawBuffer(), o.values());
                case VulkanicGalExecutionRequest.ClearBufferUint o -> clearBufferuiv(ctx, o.buffer(), o.drawBuffer(), o.values());
                case VulkanicGalExecutionRequest.ClearNamedFramebufferFloat o -> clearNamedFramebufferfv(ctx, transfer.destinationLegacyIdOr(0, o.framebuffer()), o.buffer(), o.drawBuffer(), o.values());
                case VulkanicGalExecutionRequest.ClearNamedFramebufferInt o -> clearNamedFramebufferiv(ctx, transfer.destinationLegacyIdOr(0, o.framebuffer()), o.buffer(), o.drawBuffer(), o.values());
                case VulkanicGalExecutionRequest.ClearNamedFramebufferUint o -> clearNamedFramebufferuiv(ctx, transfer.destinationLegacyIdOr(0, o.framebuffer()), o.buffer(), o.drawBuffer(), o.values());
                case VulkanicGalExecutionRequest.GenerateMipmap o -> generateMipmap(ctx, transfer.destinationLegacyTargetOr(0, o.target()));
                case VulkanicGalExecutionRequest.GenerateTextureMipmap o -> generateTextureMipmapDSA(ctx, transfer.destinationLegacyIdOr(0, o.texture()));
            }
            return VulkanicGalExecutionRequest.success(request.semanticIdentity());
        } catch (RuntimeException exception) {
            invalidateAppliedDrawState();
            return VulkanicGalExecutionRequest.backendFailure(request.semanticIdentity(), exception.getMessage());
        } finally {
            if (auditStartNanos != 0L) {
                VulkanPerfAudit.recordPhase("backend.opengl.transfer", System.nanoTime() - auditStartNanos);
            }
        }
    }
    
    @Override
    public void setBlendEnabled(CommandContext ctx, boolean enabled) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        if (enabled) {
            GL11.glEnable(GL11.GL_BLEND);
        } else {
            GL11.glDisable(GL11.GL_BLEND);
        }
    }
    
    @Override
    public void setIndexedEnabled(CommandContext ctx, int capability, int index, boolean enabled) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        if (enabled) {
            GL30.glEnablei(capability, index);
        } else {
            GL30.glDisablei(capability, index);
        }
    }
    
    @Override
    public void setCullFaceMode(CommandContext ctx, int mode) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glCullFace(mode);
    }

    @Override
    public void setCullFaceMode(CommandContext ctx, VulkanicCullFaceMode mode) {
        setCullFaceMode(ctx, toOpenGLCullFaceMode(mode));
    }
    
    @Override
    public void bindShaderProgram(CommandContext ctx, int programId) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glUseProgram(programId);
    }
    
    @Override
    public void setCapabilityEnabled(CommandContext ctx, int cap, boolean enabled) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        if (enabled) {
            GL11.glEnable(cap);
        } else {
            GL11.glDisable(cap);
        }
    }

    @Override
    public void setCapabilityEnabled(CommandContext ctx, VulkanicCapability capability, boolean enabled) {
        setCapabilityEnabled(ctx, toOpenGLCapability(capability), enabled);
    }
    
    @Override
    public void bindTexture2D(CommandContext ctx, int textureId) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }

        if (activeTextureUnitIndex < 0 || activeTextureUnitIndex >= texture2DBindings.length) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
            return;
        }

        if (textureId != texture2DBindings[activeTextureUnitIndex]) {
            texture2DBindings[activeTextureUnitIndex] = textureId;
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        }
    }
    
    @Override
    public void bindTexture(CommandContext ctx, int target, int textureId) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glBindTexture(target, textureId);
    }

    @Override
    public void bindTexture(CommandContext ctx, VulkanicTextureTarget target, int textureId) {
        bindTexture(ctx, toOpenGLTextureTarget(target), textureId);
    }
    
    @Override
    public void bindSampler(CommandContext ctx, int unit, int sampler) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL33.glBindSampler(unit, sampler);
    }
    
    @Override
    public void setDepthTest(CommandContext ctx, int func) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glDepthFunc(func);
    }

    @Override
    public void setDepthTest(CommandContext ctx, VulkanicDepthCompareOp func) {
        setDepthTest(ctx, toOpenGLDepthCompareOp(func));
    }
    
    @Override
    public void setDepthWriteMask(CommandContext ctx, boolean enabled) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glDepthMask(enabled);
    }
    
    @Override
    public void setColorMask(CommandContext ctx, boolean r, boolean g, boolean b, boolean a) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glColorMask(r, g, b, a);
    }
    
    private void generateTextureMipmap(CommandContext ctx, int target) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL30.glGenerateMipmap(target);
    }
    
    @Override
    public void setPixelStore(CommandContext ctx, int pname, int value) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glPixelStorei(pname, value);
    }
    
    @Override
    public void bindFramebuffer(CommandContext ctx, int target, int fbo) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL30.glBindFramebuffer(target, fbo);
    }

    @Override
    public void bindRenderTarget(CommandContext ctx, net.vulkanic.VulkanicTexture colorTexture, net.vulkanic.VulkanicTexture depthTexture) {
        int framebuffer = resolveFramebufferForTextures(ctx, colorTexture, depthTexture);
        bindFramebuffer(ctx, VulkanicAPI.GL_FRAMEBUFFER, framebuffer);
    }
    
    @Override
    public void bindBuffer(CommandContext ctx, int target, int buffer) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL15.glBindBuffer(target, buffer);
    }

    @Override
    public void bindBuffer(CommandContext ctx, VulkanicBufferTarget target, int buffer) {
        bindBuffer(ctx, toOpenGLBufferTarget(target), buffer);
    }
    
    @Override
    public void bindBufferBase(CommandContext ctx, int target, int index, int buffer) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL30.glBindBufferBase(target, index, buffer);
    }
    
    @Override
    public void setActiveTextureUnit(CommandContext ctx, int unit) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }

        int textureUnitIndex = unit - GL13.GL_TEXTURE0;
        if (textureUnitIndex >= 0 && textureUnitIndex < texture2DBindings.length) {
            activeTextureUnitIndex = textureUnitIndex;
        }
        GL13.glActiveTexture(unit);
    }
    
    @Override
    public void setTextureParameter(CommandContext ctx, int target, int pname, int param) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glTexParameteri(target, pname, param);
    }
    
    private void copyTexSubImage2D(CommandContext ctx, int target, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glCopyTexSubImage2D(target, level, xoffset, yoffset, x, y, width, height);
    }
    
    @Override
    public int getTexParameteri(CommandContext ctx, int target, int pname) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return GL11.glGetTexParameteri(target, pname);
    }
    
    /**
     * Sets the dynamic scissor rectangle with explicit command context.
     * This is the Vulkan-compatible implementation for scissor control.
     * 
     * OpenGL implementation: Direct mapping to glScissor() (context is validated but not used)
     * Vulkan implementation: Will map to vkCmdSetScissor(ctx.getHandle(), ...)
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     */
    @Override
    public void setDynamicScissor(CommandContext ctx, int x, int y, int width, int height) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL20.glScissor(x, y, width, height);
    }
    
    // CommandContext versions of DSA buffer operations
    @Override
    public int createBufferDSA(CommandContext ctx) {
        return createBuffer(ctx);
    }
    
    @Override
    public void namedBufferDataDSA(CommandContext ctx, int buffer, long size, int usage) {
        // DSA methods work on named buffers, not targets. For migration, we bind then use the target-based API
        int prevBinding = GL15.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        bufferData(ctx, GL15.GL_ARRAY_BUFFER, size, usage);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevBinding);
    }
    
    @Override
    public void namedBufferDataDSA(CommandContext ctx, int buffer, java.nio.ByteBuffer data, int usage) {
        // DSA methods work on named buffers, not targets. For migration, we bind then use the target-based API
        int prevBinding = GL15.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        bufferData(ctx, GL15.GL_ARRAY_BUFFER, data, usage);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevBinding);
    }
    
    @Override
    public void namedBufferSubDataDSA(CommandContext ctx, int buffer, long offset, java.nio.ByteBuffer data) {
        // DSA methods work on named buffers, not targets. For migration, we bind then use the target-based API
        int prevBinding = GL15.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        bufferSubData(ctx, GL15.GL_ARRAY_BUFFER, offset, data);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevBinding);
    }
    
    @Override
    public void namedBufferStorageDSA(CommandContext ctx, int buffer, long size, int flags) {
        // DSA methods work on named buffers, not targets. For migration, we bind then use the target-based API
        int prevBinding = GL15.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        bufferStorage(ctx, GL15.GL_ARRAY_BUFFER, size, flags);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevBinding);
    }
    
    @Override
    public void namedBufferStorageDSA(CommandContext ctx, int buffer, java.nio.ByteBuffer data, int flags) {
        // DSA methods work on named buffers, not targets. For migration, we bind then use the target-based API
        int prevBinding = GL15.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        bufferStorage(ctx, GL15.GL_ARRAY_BUFFER, data, flags);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevBinding);
    }
    
    @Override
    public java.nio.ByteBuffer mapNamedBufferRangeDSA(CommandContext ctx, int buffer, long offset, long length, int access) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.GL45.glMapNamedBufferRange(buffer, offset, length, access);
    }
    
    // CommandContext versions of DSA buffer operations
    @Override
    public void unmapNamedBufferDSA(CommandContext ctx, int buffer) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL45.glUnmapNamedBuffer(buffer);
    }
    
    @Override
    public void flushMappedNamedBufferRangeDSA(CommandContext ctx, int buffer, long offset, long length) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL45.glFlushMappedNamedBufferRange(buffer, offset, length);
    }
    
    private void copyNamedBufferSubDataDSA(CommandContext ctx, int readBuffer, int writeBuffer, long readOffset, long writeOffset, long size) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL45.glCopyNamedBufferSubData(readBuffer, writeBuffer, readOffset, writeOffset, size);
    }
    
    @Override
    public void namedFramebufferTextureDSA(CommandContext ctx, int framebuffer, int attachment, int texture, int level) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL45.glNamedFramebufferTexture(framebuffer, attachment, texture, level);
    }
    
    private void blitNamedFramebufferDSA(CommandContext ctx, int readFramebuffer, int drawFramebuffer, int srcX0, int srcY0, int srcX1, int srcY1,
                                        int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL45.glBlitNamedFramebuffer(readFramebuffer, drawFramebuffer, srcX0, srcY0, srcX1, srcY1,
                                                     dstX0, dstY0, dstX1, dstY1, mask, filter);
    }
    
    @Override
    public int createTexture2D(CommandContext ctx) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return GL11.glGenTextures();
    }
    
    @Override
    public void deleteTexture(CommandContext ctx, int texture) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glDeleteTextures(texture);
    }
    
    private void drawArrays(CommandContext ctx, int mode, int first, int count) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        VulkanicAPI.traceShaderInputParityDraw("opengl-drawArrays", false, mode, first, count, 0L, 0, 0, 1, 0);
        GL11.glDrawArrays(mode, first, count);
    }
    
    private void drawElements(CommandContext ctx, int mode, int count, int type, long indices) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        VulkanicAPI.traceShaderInputParityDraw("opengl-drawElements", true, mode, 0, 0, indices, count, type, 1, 0);
        GL11.glDrawElements(mode, count, type, indices);
    }

    @Override
    public VulkanicGalExecutionRequest.ExecutionResult executeGraphicsDraw(CommandContext ctx, VulkanicGalExecutionRequest.GraphicsDrawRequest request) {
        long auditStartNanos = VulkanPerfAudit.isEnabled() ? System.nanoTime() : 0L;
        try {
            if (!ctx.isImmediate()) {
                throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
            }
            consumeResourceUsagePlan(request.resourcePlan());
            applyGraphicsRequestState(ctx, request);
            VulkanicGalExecutionRequest.GraphicsDrawCommand command = request.command();
            switch (command.kind()) {
                case ARRAYS -> {
                    VulkanicAPI.traceShaderInputParityDraw(
                        "opengl-" + request.semanticIdentity().phase(),
                        false,
                        command.mode().toGlModeConstant(),
                        command.firstVertex(),
                        command.vertexCount(),
                        0L,
                        0,
                        0,
                        command.instanceCount(),
                        0
                    );
                    if (command.instanceCount() == 1) {
                        GL11.glDrawArrays(command.mode().toGlModeConstant(), command.firstVertex(), command.vertexCount());
                    } else {
                        GL31.glDrawArraysInstanced(
                            command.mode().toGlModeConstant(),
                            command.firstVertex(),
                            command.vertexCount(),
                            command.instanceCount()
                        );
                    }
                }
                case INDEXED -> drawIndexedRequest(request, command);
                case MULTI_INDEXED_BASE_VERTEX -> {
                    for (VulkanicGalExecutionRequest.IndexedDraw draw : command.indexedDraws()) {
                        drawIndexedRequest(
                            request,
                            VulkanicGalExecutionRequest.GraphicsDrawCommand.indexed(
                                command.mode(),
                                draw.indexCount(),
                                command.indexType(),
                                (long) draw.firstIndex() * command.indexType().bytesPerIndex(),
                                1,
                                draw.baseVertex()
                            )
                        );
                    }
                }
            }
            return VulkanicGalExecutionRequest.success(request.semanticIdentity());
        } catch (RuntimeException exception) {
            invalidateAppliedDrawState();
            return VulkanicGalExecutionRequest.backendFailure(request.semanticIdentity(), exception.getMessage());
        } finally {
            if (auditStartNanos != 0L) {
                VulkanPerfAudit.recordPhase("backend.opengl.graphics", System.nanoTime() - auditStartNanos);
            }
        }
    }

    @Override
    public VulkanicGalExecutionRequest.ExecutionResult executeGraphicsDrawV2(
        CommandContext ctx,
        net.vulkanic.VulkanicGalV2.ExplicitGraphicsDrawRequest request
    ) {
        long auditStartNanos = VulkanPerfAudit.isEnabled() ? System.nanoTime() : 0L;
        try {
            if (!ctx.isImmediate()) {
                throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
            }
            consumeResourceUsagePlan(request.resourcePlan());
            VulkanicGalV2.ExplicitGraphicsObjects objects =
                VulkanicGalV2.requireGraphicsObjects(request.graphicsObjects());
            VulkanicCompatibilityState.GraphicsSnapshot snapshot = objects.immutableCompatibilitySeed();
            applyProgramSnapshot(snapshot);
            applyFramebufferSnapshot(snapshot);
            applyFixedFunctionSnapshot(snapshot);
            VulkanicGalV2.ResourceSet resourceSet = VulkanicGalV2.requireResourceSet(request.resourceSet());
            applyResourceBindingsSnapshot(ctx, snapshot, resourceSet, request.resourceSet().equals(objects.resourceSet()));
            applyVertexInputV2(request, objects);
            applyUniformSnapshot(request.programUniforms(), request.programVersion());

            VulkanicGalExecutionRequest.GraphicsDrawCommand command = request.command();
            switch (command.kind()) {
                case ARRAYS -> {
                    VulkanicAPI.traceShaderInputParityDraw(
                        "opengl-gal-v2-" + request.semanticIdentity().phase(),
                        false,
                        command.mode().toGlModeConstant(),
                        command.firstVertex(),
                        command.vertexCount(),
                        0L,
                        0,
                        0,
                        command.instanceCount(),
                        0
                    );
                    if (command.instanceCount() == 1) {
                        GL11.glDrawArrays(command.mode().toGlModeConstant(), command.firstVertex(), command.vertexCount());
                    } else {
                        GL31.glDrawArraysInstanced(
                            command.mode().toGlModeConstant(),
                            command.firstVertex(),
                            command.vertexCount(),
                            command.instanceCount()
                        );
                    }
                }
                case INDEXED -> drawIndexedRequest(request.semanticIdentity().phase(), command);
                case MULTI_INDEXED_BASE_VERTEX -> {
                    for (VulkanicGalExecutionRequest.IndexedDraw draw : command.indexedDraws()) {
                        drawIndexedRequest(
                            request.semanticIdentity().phase(),
                            VulkanicGalExecutionRequest.GraphicsDrawCommand.indexed(
                                command.mode(),
                                draw.indexCount(),
                                command.indexType(),
                                (long) draw.firstIndex() * command.indexType().bytesPerIndex(),
                                1,
                                draw.baseVertex()
                            )
                        );
                    }
                }
            }
            return VulkanicGalExecutionRequest.success(request.semanticIdentity());
        } catch (RuntimeException exception) {
            invalidateAppliedDrawState();
            return VulkanicGalExecutionRequest.backendFailure(request.semanticIdentity(), exception.getMessage());
        } finally {
            if (auditStartNanos != 0L) {
                VulkanPerfAudit.recordPhase("backend.opengl.graphics.v2", System.nanoTime() - auditStartNanos);
            }
        }
    }

    private void applyGraphicsRequestState(CommandContext ctx, VulkanicGalExecutionRequest.GraphicsDrawRequest request) {
        VulkanicCompatibilityState.GraphicsSnapshot snapshot =
            request.compatibilitySnapshot().sharedCompatibilityState()
                .orElseThrow(() -> new IllegalStateException(
                    "OpenGL GAL graphics execution requires a shared immutable compatibility snapshot"));
        applyProgramSnapshot(snapshot);
        applyFramebufferSnapshot(snapshot);
        applyFixedFunctionSnapshot(snapshot);
        applyResourceBindingsSnapshot(ctx, snapshot);
        applyVertexInputSnapshot(snapshot);
        applyUniformSnapshot(snapshot);
    }

    private void applyProgramSnapshot(VulkanicCompatibilityState.GraphicsSnapshot snapshot) {
        if (appliedProgramVersion == snapshot.programVersion()) {
            return;
        }
        GL20.glUseProgram(snapshot.programId());
        appliedProgramVersion = snapshot.programVersion();
    }

    private void applyFramebufferSnapshot(VulkanicCompatibilityState.GraphicsSnapshot snapshot) {
        if (appliedFramebufferVersion == snapshot.framebufferVersion()) {
            return;
        }
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, snapshot.drawFramebuffer());
        if (!snapshot.framebuffer().drawBuffers().isEmpty()) {
            int[] drawBuffers = snapshot.framebuffer().drawBuffers().stream()
                .mapToInt(buffer -> normalizeDrawBufferForFramebuffer(snapshot.drawFramebuffer(), buffer))
                .toArray();
            if (drawBuffers.length == 1) {
                GL11.glDrawBuffer(drawBuffers[0]);
            } else {
                GL20.glDrawBuffers(drawBuffers);
            }
        }
        appliedFramebufferVersion = snapshot.framebufferVersion();
    }

    private static int normalizeDrawBufferForFramebuffer(int framebuffer, int drawBuffer) {
        if (framebuffer == 0 && drawBuffer == VulkanicAPI.GL_COLOR_ATTACHMENT0) {
            return VulkanicAPI.GL_BACK;
        }
        return drawBuffer;
    }

    private void applyFixedFunctionSnapshot(VulkanicCompatibilityState.GraphicsSnapshot snapshot) {
        if (appliedFixedFunctionVersion == snapshot.fixedFunctionVersion()) {
            return;
        }
        VulkanicCompatibilityState.FixedFunctionSnapshot fixed = snapshot.fixedFunction();
        fixed.viewport().ifPresent(viewport ->
            GL11.glViewport(viewport.x(), viewport.y(), viewport.width(), viewport.height()));
        if (fixed.scissorTestEnabled()) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            fixed.scissor().ifPresent(scissor ->
                GL11.glScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height()));
        } else {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }

        setGlCapability(GL11.GL_BLEND, fixed.blendEnabled());
        GL14.glBlendFuncSeparate(
            fixed.blendSrcRgb(),
            fixed.blendDstRgb(),
            fixed.blendSrcAlpha(),
            fixed.blendDstAlpha()
        );
        GL20.glBlendEquationSeparate(fixed.blendEquationRgb(), fixed.blendEquationAlpha());

        setGlCapability(GL11.GL_DEPTH_TEST, fixed.depthTestEnabled());
        GL11.glDepthFunc(fixed.depthFunc());
        GL11.glDepthMask(fixed.depthWriteMask());

        setGlCapability(GL11.GL_CULL_FACE, fixed.cullEnabled());
        GL11.glCullFace(fixed.cullFaceMode());

        setGlCapability(GL11.GL_STENCIL_TEST, fixed.stencilTestEnabled());
        fixed.stencilFuncs().values().forEach(func ->
            GL20.glStencilFuncSeparate(func.face(), func.func(), func.ref(), func.mask()));
        fixed.stencilOps().values().forEach(op ->
            GL20.glStencilOpSeparate(op.face(), op.sfail(), op.dpfail(), op.dppass()));
        fixed.stencilWriteMasks().forEach(GL20::glStencilMaskSeparate);

        setGlCapability(GL11.GL_COLOR_LOGIC_OP, fixed.logicOpEnabled());
        if (fixed.logicOpEnabled()) {
            GL11.glLogicOp(fixed.logicOp());
        }

        GL11.glPolygonMode(fixed.polygonFace(), fixed.polygonMode());
        setGlCapability(GL11.GL_POLYGON_OFFSET_FILL, fixed.polygonOffsetEnabled());
        if (fixed.polygonOffsetEnabled()) {
            GL11.glPolygonOffset(fixed.polygonOffsetFactor(), fixed.polygonOffsetUnits());
        }

        GL11.glColorMask(fixed.colorMaskR(), fixed.colorMaskG(), fixed.colorMaskB(), fixed.colorMaskA());
        appliedFixedFunctionVersion = snapshot.fixedFunctionVersion();
    }

    private static void setGlCapability(int capability, boolean enabled) {
        if (enabled) {
            GL11.glEnable(capability);
        } else {
            GL11.glDisable(capability);
        }
    }

    private void applyResourceBindingsSnapshot(
        CommandContext ctx,
        VulkanicCompatibilityState.GraphicsSnapshot snapshot
    ) {
        applyResourceBindingsSnapshot(ctx, snapshot, null);
    }

    private void applyResourceBindingsSnapshot(
        CommandContext ctx,
        VulkanicCompatibilityState.GraphicsSnapshot snapshot,
        @Nullable VulkanicGalV2.ResourceSet resourceSet
    ) {
        applyResourceBindingsSnapshot(ctx, snapshot, resourceSet, true);
    }

    private void applyResourceBindingsSnapshot(
        CommandContext ctx,
        VulkanicCompatibilityState.GraphicsSnapshot snapshot,
        @Nullable VulkanicGalV2.ResourceSet resourceSet,
        boolean replayLegacyMissingBindings
    ) {
        if (resourceSet == null && appliedResourceBindingVersion == snapshot.resourceBindingVersion()) {
            return;
        }
        AppliedV2ResourceBindings appliedV2 = resourceSet == null
            ? AppliedV2ResourceBindings.empty()
            : applyV2ResourceSetBindings(ctx, resourceSet);
        if (!replayLegacyMissingBindings) {
            appliedResourceBindingVersion = Long.MIN_VALUE;
            return;
        }
        int previousActiveTextureUnit = activeTextureUnitIndex;
        try {
            for (java.util.Map.Entry<Integer, Integer> entry : snapshot.textureUnitBindings().entrySet()) {
                int unit = entry.getKey();
                if (appliedV2.sampledTextureUnits().contains(unit)) {
                    continue;
                }
                int texture = Math.max(0, entry.getValue());
                org.lwjgl.opengl.ARBDirectStateAccess.glBindTextureUnit(unit, texture);
                if (unit >= 0 && unit < texture2DBindings.length) {
                    texture2DBindings[unit] = texture;
                }
            }
            for (java.util.Map.Entry<VulkanicCompatibilityState.TextureBindingKey, Integer> entry
                : snapshot.textureBindingsByKey().entrySet()) {
                VulkanicCompatibilityState.TextureBindingKey key = entry.getKey();
                if (appliedV2.sampledTextureUnits().contains(key.unit())) {
                    continue;
                }
                int texture = Math.max(0, entry.getValue());
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + key.unit());
                GL11.glBindTexture(key.target(), texture);
                if (key.target() == GL11.GL_TEXTURE_2D && key.unit() >= 0 && key.unit() < texture2DBindings.length) {
                    texture2DBindings[key.unit()] = texture;
                }
            }
        } finally {
            restoreActiveTextureUnit(previousActiveTextureUnit);
        }
        for (java.util.Map.Entry<Integer, Integer> entry : snapshot.samplerBindings().entrySet()) {
            if (appliedV2.sampledTextureUnits().contains(entry.getKey())) {
                continue;
            }
            GL33.glBindSampler(entry.getKey(), Math.max(0, entry.getValue()));
        }
        for (VulkanicCompatibilityState.ImageUnitBindingState image : snapshot.imageUnitBindings().values()) {
            if (appliedV2.imageUnits().contains(image.imageUnit())) {
                continue;
            }
            bindImageTexture(
                ctx,
                image.imageUnit(),
                image.texture(),
                image.level(),
                image.layered(),
                image.layer(),
                image.access(),
                image.format()
            );
        }
        for (java.util.Map.Entry<VulkanicCompatibilityState.IndexedBufferKey, VulkanicCompatibilityState.BufferRangeState> entry
            : snapshot.indexedBufferBindings().entrySet()) {
            if (appliedV2.bufferBindings().contains(entry.getKey())) {
                continue;
            }
            VulkanicCompatibilityState.BufferRangeState range = entry.getValue();
            if (range.buffer() <= 0) {
                GL30.glBindBufferBase(entry.getKey().target(), entry.getKey().index(), 0);
            } else if (range.size() == Long.MAX_VALUE) {
                GL30.glBindBufferBase(entry.getKey().target(), entry.getKey().index(), range.buffer());
            } else {
                GL30.glBindBufferRange(
                    entry.getKey().target(),
                    entry.getKey().index(),
                    range.buffer(),
                    Math.max(0L, range.offset()),
                    Math.max(0L, range.size())
                );
            }
        }
        appliedResourceBindingVersion = snapshot.resourceBindingVersion();
    }

    private AppliedV2ResourceBindings applyV2ResourceSetBindings(
        CommandContext ctx,
        VulkanicGalV2.ResourceSet resourceSet
    ) {
        java.util.Set<Integer> sampledTextureUnits = new java.util.HashSet<>();
        java.util.Set<Integer> imageUnits = new java.util.HashSet<>();
        java.util.Set<VulkanicCompatibilityState.IndexedBufferKey> bufferBindings = new java.util.HashSet<>();
        int previousActiveTextureUnit = activeTextureUnitIndex;
        try {
            for (VulkanicGalV2.ResourceBinding binding : resourceSet.bindings()) {
                if (binding.resourceReference().isEmpty()) {
                    continue;
                }
                VulkanicPassResourceModel.CanonicalResourceReference reference =
                    binding.resourceReference().orElseThrow();
                switch (reference.bindingKind()) {
                    case SAMPLED_TEXTURE -> {
                        if (reference.bindingUnit().isEmpty() || reference.legacyId().isEmpty()) {
                            continue;
                        }
                        int unit = reference.bindingUnit().getAsInt();
                        int texture = Math.max(0, reference.legacyId().getAsInt());
                        if (reference.legacyTarget().isPresent()) {
                            int target = reference.legacyTarget().getAsInt();
                            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                            GL11.glBindTexture(target, texture);
                            if (target == GL11.GL_TEXTURE_2D && unit >= 0 && unit < texture2DBindings.length) {
                                texture2DBindings[unit] = texture;
                            }
                        } else {
                            org.lwjgl.opengl.ARBDirectStateAccess.glBindTextureUnit(unit, texture);
                            if (unit >= 0 && unit < texture2DBindings.length) {
                                texture2DBindings[unit] = texture;
                            }
                        }
                        if (reference.samplerObject().isPresent()) {
                            GL33.glBindSampler(unit, Math.max(0, reference.samplerObject().getAsInt()));
                        }
                        sampledTextureUnits.add(unit);
                    }
                    case STORAGE_IMAGE -> {
                        if (reference.bindingUnit().isEmpty() || reference.legacyId().isEmpty()) {
                            continue;
                        }
                        int unit = reference.bindingUnit().getAsInt();
                        bindImageTexture(
                            ctx,
                            unit,
                            reference.legacyId().getAsInt(),
                            reference.subresource().baseMipLevel(),
                            reference.layered(),
                            reference.subresource().baseLayer(),
                            reference.imageAccess().orElse(VulkanicAPI.GL_READ_WRITE),
                            reference.imageFormat().orElse(VulkanicAPI.GL_RGBA8)
                        );
                        imageUnits.add(unit);
                    }
                    case BUFFER_RANGE -> {
                        if (reference.bindingUnit().isEmpty() || reference.legacyId().isEmpty()) {
                            continue;
                        }
                        int target = switch (reference.resource().kind()) {
                            case STORAGE_BUFFER -> VulkanicAPI.GL_SHADER_STORAGE_BUFFER;
                            case UNIFORM_BUFFER -> VulkanicAPI.GL_UNIFORM_BUFFER;
                            default -> 0;
                        };
                        if (target == 0) {
                            continue;
                        }
                        int index = reference.bindingUnit().getAsInt();
                        int buffer = reference.legacyId().getAsInt();
                        long offset = reference.subresource().baseMipLevel();
                        long size = reference.subresource().levelCount();
                        if (buffer <= 0) {
                            GL30.glBindBufferBase(target, index, 0);
                        } else {
                            GL30.glBindBufferRange(target, index, buffer, offset, size);
                        }
                        bufferBindings.add(new VulkanicCompatibilityState.IndexedBufferKey(target, index));
                    }
                    case TEXEL_BUFFER, ATTACHMENT -> {
                        // Not yet materialized by the OpenGL v2 resource-set lowering slice.
                    }
                }
            }
        } finally {
            restoreActiveTextureUnit(previousActiveTextureUnit);
        }
        return new AppliedV2ResourceBindings(
            java.util.Set.copyOf(sampledTextureUnits),
            java.util.Set.copyOf(imageUnits),
            java.util.Set.copyOf(bufferBindings)
        );
    }

    private record AppliedV2ResourceBindings(
        java.util.Set<Integer> sampledTextureUnits,
        java.util.Set<Integer> imageUnits,
        java.util.Set<VulkanicCompatibilityState.IndexedBufferKey> bufferBindings
    ) {
        private static AppliedV2ResourceBindings empty() {
            return new AppliedV2ResourceBindings(java.util.Set.of(), java.util.Set.of(), java.util.Set.of());
        }
    }

    private void applyVertexInputSnapshot(VulkanicCompatibilityState.GraphicsSnapshot snapshot) {
        if (appliedVertexInputVersion == snapshot.vertexInputVersion()) {
            return;
        }
        VulkanicCompatibilityState.VaoSnapshot vao = snapshot.vao();
        GL30.glBindVertexArray(snapshot.vaoId());
        if (vao.elementBuffer() > 0) {
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, vao.elementBuffer());
        }

        java.util.Set<Integer> enabled = new java.util.HashSet<>(vao.enabledAttributes());
        for (java.util.Map.Entry<Integer, float[]> entry : vao.defaultAttributes().entrySet()) {
            float[] values = entry.getValue();
            GL20.glVertexAttrib4f(
                entry.getKey(),
                values.length > 0 ? values[0] : 0.0f,
                values.length > 1 ? values[1] : 0.0f,
                values.length > 2 ? values[2] : 0.0f,
                values.length > 3 ? values[3] : 1.0f
            );
        }
        for (VulkanicCompatibilityState.VertexAttributeState attribute : vao.attributes().values()) {
            VulkanicCompatibilityState.VertexBindingState binding = vao.vertexBindings().get(attribute.binding());
            if (binding == null || !enabled.contains(attribute.index())) {
                GL20.glDisableVertexAttribArray(attribute.index());
                continue;
            }
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, Math.max(0, binding.buffer()));
            long pointer = Math.max(0L, binding.offset()) + Math.max(0, attribute.relativeOffset());
            if (attribute.integer()) {
                GL30.glVertexAttribIPointer(
                    attribute.index(),
                    attribute.size(),
                    attribute.type(),
                    binding.stride(),
                    pointer
                );
            } else {
                GL20.glVertexAttribPointer(
                    attribute.index(),
                    attribute.size(),
                    attribute.type(),
                    attribute.normalized(),
                    binding.stride(),
                    pointer
                );
            }
            GL20.glEnableVertexAttribArray(attribute.index());
            GL33.glVertexAttribDivisor(attribute.index(), attribute.divisor());
        }
        appliedVertexInputVersion = snapshot.vertexInputVersion();
    }

    private void applyVertexInputV2(
        VulkanicGalV2.ExplicitGraphicsDrawRequest request,
        VulkanicGalV2.ExplicitGraphicsObjects objects
    ) {
        String key = objects.vertexLayoutHandle().semanticKey() + "|" + request.vertexStreams();
        if (key.equals(appliedGalV2VertexInputKey)) {
            return;
        }
        int vao = galV2VertexArrays.computeIfAbsent(objects.vertexLayoutHandle(), ignored -> GL30.glGenVertexArrays());
        GL30.glBindVertexArray(vao);
        if (request.vertexStreams().indexStream().isPresent()) {
            GL15.glBindBuffer(
                GL15.GL_ELEMENT_ARRAY_BUFFER,
                Math.max(0, request.vertexStreams().indexStream().orElseThrow().buffer())
            );
        } else {
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        }

        java.util.Map<Integer, VulkanicGalV2.VertexStream> streamsByBinding = new java.util.HashMap<>();
        for (VulkanicGalV2.VertexStream stream : request.vertexStreams().vertexStreams()) {
            streamsByBinding.put(stream.binding(), stream);
        }
        java.util.Map<Integer, VulkanicGalV2.VertexBindingLayout> layoutsByBinding = new java.util.HashMap<>();
        for (VulkanicGalV2.VertexBindingLayout binding : objects.vertexLayout().bindings()) {
            layoutsByBinding.put(binding.binding(), binding);
        }
        java.util.Set<Integer> enabledNow = new java.util.HashSet<>();
        for (java.util.Map.Entry<Integer, float[]> entry : objects.vertexLayout().disabledAttributeDefaults().entrySet()) {
            float[] values = entry.getValue();
            GL20.glVertexAttrib4f(
                entry.getKey(),
                values.length > 0 ? values[0] : 0.0f,
                values.length > 1 ? values[1] : 0.0f,
                values.length > 2 ? values[2] : 0.0f,
                values.length > 3 ? values[3] : 1.0f
            );
        }
        for (VulkanicGalV2.VertexAttributeLayout attribute : objects.vertexLayout().attributes()) {
            VulkanicGalV2.VertexStream stream = streamsByBinding.get(attribute.binding());
            VulkanicGalV2.VertexBindingLayout binding = layoutsByBinding.get(attribute.binding());
            if (stream == null || stream.defaultAttributeBuffer() || stream.buffer() <= 0) {
                GL20.glDisableVertexAttribArray(attribute.location());
                continue;
            }
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, Math.max(0, stream.buffer()));
            long pointer = Math.max(0L, stream.baseOffset()) + Math.max(0, attribute.relativeOffset());
            if (attribute.integer()) {
                GL30.glVertexAttribIPointer(
                    attribute.location(),
                    attribute.size(),
                    attribute.type(),
                    binding == null ? 0 : binding.stride(),
                    pointer
                );
            } else {
                GL20.glVertexAttribPointer(
                    attribute.location(),
                    attribute.size(),
                    attribute.type(),
                    attribute.normalized(),
                    binding == null ? 0 : binding.stride(),
                    pointer
                );
            }
            GL20.glEnableVertexAttribArray(attribute.location());
            GL33.glVertexAttribDivisor(attribute.location(), attribute.divisor());
            enabledNow.add(attribute.location());
        }
        for (Integer previous : appliedGalV2Attributes) {
            if (!enabledNow.contains(previous)) {
                GL20.glDisableVertexAttribArray(previous);
            }
        }
        appliedGalV2Attributes.clear();
        appliedGalV2Attributes.addAll(enabledNow);
        appliedGalV2VertexInputKey = key;
        appliedVertexInputVersion = Long.MIN_VALUE;
    }

    private void applyUniformSnapshot(VulkanicCompatibilityState.GraphicsSnapshot snapshot) {
        applyUniformSnapshot(snapshot.program(), snapshot.programVersion());
    }

    private void applyUniformSnapshot(VulkanicCompatibilityState.ProgramSnapshot program, long programVersion) {
        if (appliedUniformVersion == programVersion) {
            return;
        }
        if (program.programId() <= 0) {
            return;
        }
        for (java.util.Map.Entry<Integer, VulkanicCompatibilityState.UniformValue> entry : program.uniformsByLocation().entrySet()) {
            int location = entry.getKey();
            if (location < 0) {
                continue;
            }
            VulkanicCompatibilityState.UniformValue value = entry.getValue();
            int[] ints = value.ints();
            float[] floats = value.floats();
            if (value.columns() > 1 && value.rows() > 1) {
                applyMatrixUniform(location, value);
            } else if (ints.length > 0) {
                applyIntegerUniform(location, ints);
            } else {
                applyFloatUniform(location, floats);
            }
        }
        appliedUniformVersion = programVersion;
    }

    private static void applyIntegerUniform(int location, int[] values) {
        switch (values.length) {
            case 1 -> GL20.glUniform1i(location, values[0]);
            case 2 -> GL20.glUniform2i(location, values[0], values[1]);
            case 3 -> GL20.glUniform3i(location, values[0], values[1], values[2]);
            default -> GL20.glUniform4i(
                location,
                values.length > 0 ? values[0] : 0,
                values.length > 1 ? values[1] : 0,
                values.length > 2 ? values[2] : 0,
                values.length > 3 ? values[3] : 0
            );
        }
    }

    private static void applyFloatUniform(int location, float[] values) {
        switch (values.length) {
            case 0 -> {
            }
            case 1 -> GL20.glUniform1f(location, values[0]);
            case 2 -> GL20.glUniform2f(location, values[0], values[1]);
            case 3 -> GL20.glUniform3f(location, values[0], values[1], values[2]);
            default -> GL20.glUniform4f(
                location,
                values.length > 0 ? values[0] : 0.0f,
                values.length > 1 ? values[1] : 0.0f,
                values.length > 2 ? values[2] : 0.0f,
                values.length > 3 ? values[3] : 0.0f
            );
        }
    }

    private static void applyMatrixUniform(int location, VulkanicCompatibilityState.UniformValue value) {
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            java.nio.FloatBuffer data = stack.mallocFloat(value.floats().length);
            data.put(value.floats()).flip();
            if (value.columns() == 2 && value.rows() == 2) {
                GL20.glUniformMatrix2fv(location, value.transpose(), data);
            } else if (value.columns() == 3 && value.rows() == 3) {
                GL20.glUniformMatrix3fv(location, value.transpose(), data);
            } else if (value.columns() == 4 && value.rows() == 4) {
                GL20.glUniformMatrix4fv(location, value.transpose(), data);
            } else if (value.columns() == 2 && value.rows() == 3) {
                GL21.glUniformMatrix2x3fv(location, value.transpose(), data);
            } else if (value.columns() == 3 && value.rows() == 2) {
                GL21.glUniformMatrix3x2fv(location, value.transpose(), data);
            } else if (value.columns() == 2 && value.rows() == 4) {
                GL21.glUniformMatrix2x4fv(location, value.transpose(), data);
            } else if (value.columns() == 4 && value.rows() == 2) {
                GL21.glUniformMatrix4x2fv(location, value.transpose(), data);
            } else if (value.columns() == 3 && value.rows() == 4) {
                GL21.glUniformMatrix3x4fv(location, value.transpose(), data);
            } else if (value.columns() == 4 && value.rows() == 3) {
                GL21.glUniformMatrix4x3fv(location, value.transpose(), data);
            }
        }
    }

    private void drawIndexedRequest(
        VulkanicGalExecutionRequest.GraphicsDrawRequest request,
        VulkanicGalExecutionRequest.GraphicsDrawCommand command
    ) {
        drawIndexedRequest(request.semanticIdentity().phase(), command);
    }

    private void drawIndexedRequest(
        String phase,
        VulkanicGalExecutionRequest.GraphicsDrawCommand command
    ) {
        VulkanicAPI.traceShaderInputParityDraw(
            "opengl-" + phase,
            true,
            command.mode().toGlModeConstant(),
            0,
            0,
            command.indexByteOffset(),
            command.indexCount(),
            command.indexType().toGlTypeConstant(),
            command.instanceCount(),
            command.baseVertex()
        );
        if (command.instanceCount() == 1 && command.baseVertex() == 0) {
            GL11.glDrawElements(
                command.mode().toGlModeConstant(),
                command.indexCount(),
                command.indexType().toGlTypeConstant(),
                command.indexByteOffset()
            );
        } else if (command.instanceCount() == 1) {
            GL32.glDrawElementsBaseVertex(
                command.mode().toGlModeConstant(),
                command.indexCount(),
                command.indexType().toGlTypeConstant(),
                command.indexByteOffset(),
                command.baseVertex()
            );
        } else {
            GL32.glDrawElementsInstancedBaseVertex(
                command.mode().toGlModeConstant(),
                command.indexCount(),
                command.indexType().toGlTypeConstant(),
                command.indexByteOffset(),
                command.instanceCount(),
                command.baseVertex()
            );
        }
    }
    
    @Override
    public void setBlendFunction(CommandContext ctx, int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
    }

    @Override
    public void setBlendFunction(
        CommandContext ctx,
        VulkanicBlendFactor srcRgb,
        VulkanicBlendFactor dstRgb,
        VulkanicBlendFactor srcAlpha,
        VulkanicBlendFactor dstAlpha
    ) {
        setBlendFunction(
            ctx,
            toOpenGLBlendFactor(srcRgb),
            toOpenGLBlendFactor(dstRgb),
            toOpenGLBlendFactor(srcAlpha),
            toOpenGLBlendFactor(dstAlpha)
        );
    }
    
    @Override
    public void setBlendEquation(CommandContext ctx, int mode) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL14.glBlendEquation(mode);
    }

    @Override
    public void setBlendEquation(CommandContext ctx, VulkanicBlendEquation mode) {
        setBlendEquation(ctx, toOpenGLBlendEquation(mode));
    }
    
    @Override
    public void setDepthFunc(CommandContext ctx, int func) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glDepthFunc(func);
    }

    @Override
    public void setDepthFunc(CommandContext ctx, VulkanicDepthCompareOp func) {
        setDepthFunc(ctx, toOpenGLDepthCompareOp(func));
    }
    
    @Override
    public void setReadBuffer(CommandContext ctx, int buffer) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glReadBuffer(buffer);
    }
    
    @Override
    public void framebufferTexture(CommandContext ctx, int target, int attachment, int textarget, int texture, int level) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL30.glFramebufferTexture2D(target, attachment, textarget, texture, level);
    }
    
    @Override
    public void framebufferTexture2D(CommandContext ctx, int target, int attachment, int textarget, int texture, int level) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL30.glFramebufferTexture2D(target, attachment, textarget, texture, level);
    }
    
    @Override
    public void drawBuffers(CommandContext ctx, int[] buffers) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glDrawBuffers(buffers);
    }
    
    @Override
    public void blendFunc(CommandContext ctx, int sfactor, int dfactor) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glBlendFunc(sfactor, dfactor);
    }

    @Override
    public void blendFunc(CommandContext ctx, VulkanicBlendFactor sfactor, VulkanicBlendFactor dfactor) {
        blendFunc(ctx, toOpenGLBlendFactor(sfactor), toOpenGLBlendFactor(dfactor));
    }
    
    @Override
    public int getInteger(CommandContext ctx, int pname) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return GL11.glGetInteger(pname);
    }

    @Override
    public int getInteger(CommandContext ctx, VulkanicIntegerQuery query) {
        return getInteger(ctx, toOpenGLIntegerQuery(query));
    }
    
    @Override
    public void setUniform3f(CommandContext ctx, int location, float v0, float v1, float v2) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glUniform3f(location, v0, v1, v2);
    }
    
    @Override
    public void setClearColor(CommandContext ctx, float r, float g, float b, float a) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glClearColor(r, g, b, a);
    }
    
    @Override
    public void setClearDepth(CommandContext ctx, double depth) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glClearDepth(depth);
    }

    @Override
    public void setClearStencil(CommandContext ctx, int stencil) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glClearStencil(stencil);
    }
    
    @Override
    public void setViewport(CommandContext ctx, int x, int y, int width, int height) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glViewport(x, y, width, height);
    }
    
    @Override
    public void setPolygonMode(CommandContext ctx, int face, int mode) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glPolygonMode(face, mode);
    }
    
    @Override
    public void setPolygonOffset(CommandContext ctx, float factor, float units) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glPolygonOffset(factor, units);
    }
    
    @Override
    public void setLogicOp(CommandContext ctx, int opcode) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glLogicOp(opcode);
    }
    
    @Override
    public int createFramebuffer(CommandContext ctx) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.ARBDirectStateAccess.glCreateFramebuffers();
    }
    
    @Override
    public int getError(CommandContext ctx) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return GL11.glGetError();
    }
    
    
    private void uploadTexture2D(CommandContext ctx, int target, int level, int internalFormat, int width, int height,
                                 int border, int format, int type, java.nio.ByteBuffer pixels) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glTexImage2D(target, level, internalFormat, width, height, border, format, type, pixels);
    }

    private void uploadTexture2D(
        CommandContext ctx,
        VulkanicTextureTarget target,
        int level,
        VulkanicTextureUploadFormat uploadFormat,
        int width,
        int height,
        int border,
        java.nio.ByteBuffer pixels
    ) {
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        if (uploadFormat == null) {
            throw new IllegalArgumentException("uploadFormat must not be null");
        }

        uploadTexture2D(
            ctx,
            target.toLegacyGlTarget(),
            level,
            uploadFormat.legacyInternalFormat(),
            width,
            height,
            border,
            uploadFormat.legacyFormat(),
            uploadFormat.legacyType(),
            pixels
        );
    }
    
    private void uploadTexture2DSubImage(CommandContext ctx, int target, int level, int xOffset, int yOffset,
                                         int width, int height, int format, int type, long pixels) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glTexSubImage2D(target, level, xOffset, yOffset, width, height, format, type, pixels);
    }
    
    private void uploadTexture2DSubImage(CommandContext ctx, int target, int level, int xOffset, int yOffset,
                                         int width, int height, int format, int type, java.nio.ByteBuffer pixels) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glTexSubImage2D(target, level, xOffset, yOffset, width, height, format, type, pixels);
    }
    
    
    
    
    @Override
    public int createBuffer(CommandContext ctx) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return GL15.glGenBuffers();
    }
    
    @Override
    public void deleteBuffer(CommandContext ctx, int buffer) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL15.glDeleteBuffers(buffer);
    }
    
    @Override
    public void bufferData(CommandContext ctx, int target, java.nio.ByteBuffer data, int usage) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL15.glBufferData(target, data, usage);
    }
    
    @Override
    public void bufferData(CommandContext ctx, int target, long size, int usage) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL15.glBufferData(target, size, usage);
    }
    
    @Override
    public void bufferData(CommandContext ctx, int target, float[] data, int usage) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL15.glBufferData(target, data, usage);
    }
    
    @Override
    public void bufferData(CommandContext ctx, int target, int[] data, int usage) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL15.glBufferData(target, data, usage);
    }
    
    
    
    
    
    @Override
    public void bufferSubData(CommandContext ctx, int target, long offset, java.nio.ByteBuffer data) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL15.glBufferSubData(target, offset, data);
    }
    
    @Override
    public void bufferStorage(CommandContext ctx, int target, long size, int flags) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL44.glBufferStorage(target, size, flags);
    }
    
    @Override
    public void bufferStorage(CommandContext ctx, int target, java.nio.ByteBuffer data, int flags) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL44.glBufferStorage(target, data, flags);
    }
    
    private void copyBufferSubData(CommandContext ctx, int readTarget, int writeTarget, long readOffset, long writeOffset, long size) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL31.glCopyBufferSubData(readTarget, writeTarget, readOffset, writeOffset, size);
    }
    
    @Override
    public void flushMappedBufferRange(CommandContext ctx, int target, long offset, long length) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL30.glFlushMappedBufferRange(target, offset, length);
    }
    
    
    @Override
    public int createVertexArray(CommandContext ctx) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return GL30.glGenVertexArrays();
    }
    
    @Override
    public void bindVertexArray(CommandContext ctx, int vao) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL30.glBindVertexArray(vao);
    }
    
    
    
    @Override
    public java.nio.ByteBuffer mapBuffer(CommandContext ctx, int target, long offset, long length, int access) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return GL30.glMapBufferRange(target, offset, length, access);
    }
    
    @Override
    public void unmapBuffer(CommandContext ctx, int target) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL15.glUnmapBuffer(target);
    }
    
    
    
    @Override
    public void deleteFramebuffer(CommandContext ctx, int fbo) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL30.glDeleteFramebuffers(fbo);
    }
    
    private void blitFramebuffer(CommandContext ctx, int srcX0, int srcY0, int srcX1, int srcY1,
                                int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL30.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }

    @Override
    public void presentTextureToScreen(CommandContext ctx, GpuTextureView textureView) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        if (textureView == null) {
            throw new IllegalArgumentException("textureView must not be null");
        }

        int textureHandle = resolveTextureHandle(ctx, textureView.texture());
        if (textureHandle == 0) {
            throw new IllegalStateException("OpenGL present requires a valid texture handle");
        }

        int width = textureView.getWidth(0);
        int height = textureView.getHeight(0);
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("OpenGL present requires a non-zero texture extent");
        }

        int readFbo = ensurePresentScratchReadFramebuffer();
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);

        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
            GL32.glFramebufferTexture(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, textureHandle, textureView.baseMipLevel());
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);

            int framebufferStatus = GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER);
            if (framebufferStatus != GL30.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException(
                    "OpenGL present read framebuffer is incomplete (status=0x" + Integer.toHexString(framebufferStatus) + ")"
                );
            }

            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
            GL11.glDrawBuffer(GL11.GL_BACK);

            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            net.irisshaders.iris.gl.blending.DepthColorStorage.setDepthMask(true);
            net.irisshaders.iris.gl.blending.DepthColorStorage.setColorMask(true, true, true, true);

            GL30.glBlitFramebuffer(
                0,
                0,
                width,
                height,
                0,
                0,
                width,
                height,
                GL11.GL_COLOR_BUFFER_BIT,
                GL11.GL_NEAREST
            );
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
        }
    }

    private int ensurePresentScratchReadFramebuffer() {
        int readFbo = presentScratchReadFbo;
        if (readFbo != 0 && GL30.glIsFramebuffer(readFbo)) {
            return readFbo;
        }

        readFbo = GL30.glGenFramebuffers();
        if (readFbo == 0) {
            throw new IllegalStateException("Failed to allocate OpenGL framebuffer for presentTextureToScreen");
        }

        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
        }

        presentScratchReadFbo = readFbo;
        return readFbo;
    }
    
    
    
    @Override
    public int createShader(CommandContext ctx, int shaderType) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return GL20.glCreateShader(shaderType);
    }

    @Override
    public VulkanicShaderHandle createShaderHandle(CommandContext ctx, int shaderType) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return VulkanicShaderHandle.of(GL20.glCreateShader(shaderType));
    }
    
    @Override
    public void compileShader(CommandContext ctx, int shader) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glCompileShader(shader);
    }
    
    @Override
    public int createShaderProgram(CommandContext ctx) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return GL20.glCreateProgram();
    }

    @Override
    public VulkanicProgramHandle createShaderProgramHandle(CommandContext ctx) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return VulkanicProgramHandle.of(GL20.glCreateProgram());
    }
    
    
    @Override
    public void deleteShader(CommandContext ctx, int shader) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glDeleteShader(shader);
    }
    
    
    
    
    @Override
    public void deleteProgram(CommandContext ctx, int program) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glDeleteProgram(program);
    }
    
    
    @Override
    public void attachShader(CommandContext ctx, int program, int shader) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glAttachShader(program, shader);
    }
    
    @Override
    public void detachShader(CommandContext ctx, int program, int shader) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glDetachShader(program, shader);
    }
    
    @Override
    public void linkProgram(CommandContext ctx, int program) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glLinkProgram(program);
    }
    
    @Override
    public int getProgramParameter(CommandContext ctx, int program, int pname) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return GL20.glGetProgrami(program, pname);
    }
    
    @Override
    public int getShaderParameter(CommandContext ctx, int shader, int pname) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return GL20.glGetShaderi(shader, pname);
    }
    
    @Override
    public String getProgramInfoLog(CommandContext ctx, int program) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return GL20.glGetProgramInfoLog(program);
    }
    
    
    
    
    
    
    @Override
    public String getShaderInfoLog(CommandContext ctx, int shader) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return GL20.glGetShaderInfoLog(shader);
    }
    
    @Override
    public int getUniformLocation(CommandContext ctx, int program, CharSequence name) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        int location = GL20.glGetUniformLocation(program, name);
        if (location >= 0 && VulkanicAPI.isShaderInputParityTracingEnabled()) {
            uniformNamesByProgramLocation
                .computeIfAbsent(program, ignored -> new java.util.concurrent.ConcurrentHashMap<>())
                .put(location, name.toString());
        }
        return location;
    }
    
    @Override
    public int getAttributeLocation(CommandContext ctx, int program, CharSequence name) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return GL20.glGetAttribLocation(program, name);
    }
    
    @Override
    public void setUniform1i(CommandContext ctx, int location, int value) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glUniform1i(location, value);
        traceStandaloneUniformInts(location, "int", new int[] {value});
    }
    
    @Override
    public void setUniform1f(CommandContext ctx, int location, float value) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glUniform1f(location, value);
        traceStandaloneUniformFloats(location, "float", false, new float[] {value});
    }
    
    @Override
    public void setUniform2f(CommandContext ctx, int location, float v0, float v1) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glUniform2f(location, v0, v1);
        traceStandaloneUniformFloats(location, "vec2", false, new float[] {v0, v1});
    }
    
    @Override
    public void setUniform2i(CommandContext ctx, int location, int v0, int v1) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glUniform2i(location, v0, v1);
        traceStandaloneUniformInts(location, "ivec2", new int[] {v0, v1});
    }
    
    @Override
    public void setUniform3i(CommandContext ctx, int location, int v0, int v1, int v2) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glUniform3i(location, v0, v1, v2);
        traceStandaloneUniformInts(location, "ivec3", new int[] {v0, v1, v2});
    }
    
    @Override
    public void setUniform4f(CommandContext ctx, int location, float v0, float v1, float v2, float v3) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glUniform4f(location, v0, v1, v2, v3);
        traceStandaloneUniformFloats(location, "vec4", false, new float[] {v0, v1, v2, v3});
    }
    
    @Override
    public void setUniform4i(CommandContext ctx, int location, int v0, int v1, int v2, int v3) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glUniform4i(location, v0, v1, v2, v3);
        traceStandaloneUniformInts(location, "ivec4", new int[] {v0, v1, v2, v3});
    }
    
    @Override
    public void setUniformMatrix3fv(CommandContext ctx, int location, boolean transpose, java.nio.FloatBuffer matrix) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        traceStandaloneUniformFloats(location, "mat3", transpose, copyFloats(matrix, 9));
        GL20.glUniformMatrix3fv(location, transpose, matrix);
    }
    
    @Override
    public void setUniformMatrix3fv(CommandContext ctx, int location, boolean transpose, float[] matrix) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glUniformMatrix3fv(location, transpose, matrix);
        traceStandaloneUniformFloats(location, "mat3", transpose, java.util.Arrays.copyOf(matrix, Math.min(matrix.length, 9)));
    }
    
    @Override
    public void setUniformMatrix4fv(CommandContext ctx, int location, boolean transpose, java.nio.FloatBuffer matrix) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        traceStandaloneUniformFloats(location, "mat4", transpose, copyFloats(matrix, 16));
        GL20.glUniformMatrix4fv(location, transpose, matrix);
    }
    
    @Override
    public void setUniformMatrix4fv(CommandContext ctx, int location, boolean transpose, float[] matrix) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glUniformMatrix4fv(location, transpose, matrix);
        traceStandaloneUniformFloats(location, "mat4", transpose, java.util.Arrays.copyOf(matrix, Math.min(matrix.length, 16)));
    }
    
    @Override
    public void setVertexAttribPointer(CommandContext ctx, int index, int size, int type, boolean normalized, int stride, long pointer) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glVertexAttribPointer(index, size, type, normalized, stride, pointer);
    }
    
    @Override
    public void enableVertexAttribArray(CommandContext ctx, int index) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glEnableVertexAttribArray(index);
    }
    
    @Override
    public void bindVertexBuffer(CommandContext ctx, int bindingindex, int buffer, long offset, int stride) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL43.glBindVertexBuffer(bindingindex, buffer, offset, stride);
    }
    
    
    @Override
    public void setVertexAttribIPointer(CommandContext ctx, int index, int size, int type, int stride, long pointer) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL30.glVertexAttribIPointer(index, size, type, stride, pointer);
    }
    
    
    
    @Override
    public void disableVertexAttribArray(CommandContext ctx, int index) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glDisableVertexAttribArray(index);
    }
    
    
    @Override
    public void setVertexAttribDivisor(CommandContext ctx, int index, int divisor) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL33.glVertexAttribDivisor(index, divisor);
    }
    
    
    
    
    @Override
    public void setAttributeLocation(CommandContext ctx, int program, int index, CharSequence name) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glBindAttribLocation(program, index, name);
    }
    
    
    @Override
    public long createFenceSync(CommandContext ctx, int condition, int flags) {
        return org.lwjgl.opengl.GL32.glFenceSync(condition, flags);
    }
    
    @Override
    public void destroySync(CommandContext ctx, long sync) {
        org.lwjgl.opengl.GL32.glDeleteSync(sync);
    }
    
    @Override
    public int waitForSync(CommandContext ctx, long sync, int flags, long timeout) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.GL32.glClientWaitSync(sync, flags, timeout);
    }
    
    @Override
    public boolean isTexture(CommandContext ctx, int texture) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return GL11.glIsTexture(texture);
    }
    
    @Override
    public int getTextureLevelParameter(CommandContext ctx, int target, int level, int pname) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return GL11.glGetTexLevelParameteri(target, level, pname);
    }

    @Override
    public void uploadShaderSource(CommandContext ctx, int shader, CharSequence source) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }

        final org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackGet();
        final int stackPointer = stack.getPointer();

        try {
            final java.nio.ByteBuffer sourceBuffer = org.lwjgl.system.MemoryUtil.memUTF8(source, true);
            final org.lwjgl.PointerBuffer pointers = stack.mallocPointer(1);
            pointers.put(sourceBuffer);

            org.lwjgl.opengl.GL20C.nglShaderSource(shader, 1, pointers.address0(), 0L);
            org.lwjgl.system.APIUtil.apiArrayFree(pointers.address0(), 1);
        } finally {
            stack.setPointer(stackPointer);
        }
    }
    
    @Override
    public void uploadShaderSource(CommandContext ctx, int shader, long pointerBufferAddress, int stringCount, long lengthsPointer) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL20C.nglShaderSource(shader, stringCount, pointerBufferAddress, lengthsPointer);
    }
    
    @Override
    public void uniformBlockBinding(CommandContext ctx, int program, int uniformBlockIndex, int uniformBlockBinding) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL31.glUniformBlockBinding(program, uniformBlockIndex, uniformBlockBinding);
    }
    
    @Override
    public String retrieveActiveUniformBlockName(CommandContext ctx, int program, int uniformBlockIndex) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.GL31.glGetActiveUniformBlockName(program, uniformBlockIndex);
    }
    
    @Override
    public int generateQueryObject(CommandContext ctx) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.GL32C.glGenQueries();
    }
    
    @Override
    public void initiateQuery(CommandContext ctx, int target, int id) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL32C.glBeginQuery(target, id);
    }
    
    @Override
    public void concludeQuery(CommandContext ctx, int target) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL32C.glEndQuery(target);
    }
    
    @Override
    public void disposeQueryObject(CommandContext ctx, int id) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL32C.glDeleteQueries(id);
    }
    
    @Override
    public int retrieveQueryObjectInt(CommandContext ctx, int id, int pname) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.GL32C.glGetQueryObjecti(id, pname);
    }
    
    @Override
    public long retrieveQueryObjectInt64(CommandContext ctx, int id, int pname) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.ARBTimerQuery.glGetQueryObjecti64(id, pname);
    }
    
    @Override
    public void labelDebugObject(CommandContext ctx, int identifier, int name, String label) {
        org.lwjgl.opengl.KHRDebug.glObjectLabel(identifier, name, label);
    }
    
    @Override
    public void enterDebugGroup(CommandContext ctx, int source, int id, CharSequence message) {
        org.lwjgl.opengl.KHRDebug.glPushDebugGroup(source, id, message);
    }
    
    @Override
    public void exitDebugGroup(CommandContext ctx) {
        org.lwjgl.opengl.KHRDebug.glPopDebugGroup();
    }
    
    @Override
    public void debugMessageControl(CommandContext ctx, int source, int type, int severity, int[] ids, boolean enabled) {
        org.lwjgl.opengl.GL43C.glDebugMessageControl(source, type, severity, ids, enabled);
    }
    
    @Override
    public void debugMessageControlKHR(CommandContext ctx, int source, int type, int severity, int[] ids, boolean enabled) {
        org.lwjgl.opengl.KHRDebug.glDebugMessageControl(source, type, severity, ids, enabled);
    }
    
    @Override
    public void debugMessageControlARB(CommandContext ctx, int source, int type, int severity, int[] ids, boolean enabled) {
        org.lwjgl.opengl.ARBDebugOutput.glDebugMessageControlARB(source, type, severity, ids, enabled);
    }
    
    @Override
    public void debugMessageEnableAMD(CommandContext ctx, int category, int severity, int[] ids, boolean enabled) {
        org.lwjgl.opengl.AMDDebugOutput.glDebugMessageEnableAMD(category, severity, ids, enabled);
    }
    
    @Override
    public void labelObjectExt(CommandContext ctx, int type, int object, String label) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.EXTDebugLabel.glLabelObjectEXT(type, object, label);
    }
    
    @Override
    public boolean supportsKhrDebug() {
        return org.lwjgl.opengl.GL.getCapabilities().GL_KHR_debug;
    }
    
    @Override
    public boolean supportsArbDebugOutput() {
        return org.lwjgl.opengl.GL.getCapabilities().GL_ARB_debug_output;
    }
    
    @Override
    public void setupKhrDebugSystem(int verbosityLevel, boolean synchronous, java.util.function.Consumer<String> messageHandler) {
        org.lwjgl.opengl.GL11.glEnable(37600); // GL_DEBUG_OUTPUT
        if (synchronous) {
            org.lwjgl.opengl.GL11.glEnable(33346); // GL_DEBUG_OUTPUT_SYNCHRONOUS
        }
        
        // Configure message filtering based on verbosity
        java.util.List<Integer> levels = java.util.Arrays.asList(37190, 37191, 37192, 33387);
        for (int i = 0; i < levels.size(); i++) {
            boolean shouldEnable = i < verbosityLevel;
            org.lwjgl.opengl.KHRDebug.glDebugMessageControl(4352, 4352, levels.get(i), (int[])null, shouldEnable);
        }
        
        // Install callback
        org.lwjgl.opengl.GLDebugMessageCallback callback = org.lwjgl.opengl.GLDebugMessageCallback.create(
            (source, type, id, severity, length, message, userParam) -> {
                String msg = org.lwjgl.opengl.GLDebugMessageCallback.getMessage(length, message);
                messageHandler.accept(msg);
            }
        );
        org.lwjgl.opengl.KHRDebug.glDebugMessageCallback(
            net.blaze3d.platform.GLX.make(callback, net.blaze3d.platform.DebugMemoryUntracker::untrack), 
            0L
        );
    }
    
    @Override
    public void setupArbDebugSystem(int verbosityLevel, boolean synchronous, java.util.function.Consumer<String> messageHandler) {
        if (synchronous) {
            org.lwjgl.opengl.GL11.glEnable(33346); // GL_DEBUG_OUTPUT_SYNCHRONOUS
        }
        
        // Configure message filtering based on verbosity  
        java.util.List<Integer> levels = java.util.Arrays.asList(37190, 37191, 37192);
        for (int i = 0; i < levels.size(); i++) {
            boolean shouldEnable = i < verbosityLevel;
            org.lwjgl.opengl.ARBDebugOutput.glDebugMessageControlARB(4352, 4352, levels.get(i), (int[])null, shouldEnable);
        }
        
        // Install callback
        org.lwjgl.opengl.GLDebugMessageARBCallback callback = org.lwjgl.opengl.GLDebugMessageARBCallback.create(
            (source, type, id, severity, length, message, userParam) -> {
                String msg = org.lwjgl.opengl.GLDebugMessageCallback.getMessage(length, message);
                messageHandler.accept(msg);
            }
        );
        org.lwjgl.opengl.ARBDebugOutput.glDebugMessageCallbackARB(
            net.blaze3d.platform.GLX.make(callback, net.blaze3d.platform.DebugMemoryUntracker::untrack),
            0L
        );
    }
    
    @Override
    public boolean hasBufferStorageExtension() {
        return org.lwjgl.opengl.GL.getCapabilities().GL_ARB_buffer_storage;
    }
    
    @Override
    public boolean hasVertexAttribBindingExtension() {
        return org.lwjgl.opengl.GL.getCapabilities().GL_ARB_vertex_attrib_binding;
    }
    
    @Override
    public void setDrawBuffer(CommandContext ctx, int mode) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL11.glDrawBuffer(mode);
    }
    
    private void drawIndexedInstancedBaseVertex(CommandContext ctx, int mode, int count, int type, long indices, int instanceCount, int baseVertex) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        VulkanicAPI.traceShaderInputParityDraw("opengl-drawIndexedInstancedBaseVertex", true, mode, 0, 0, indices, count, type, instanceCount, baseVertex);
        org.lwjgl.opengl.GL32.glDrawElementsInstancedBaseVertex(mode, count, type, indices, instanceCount, baseVertex);
    }
    
    private void drawIndexedBaseVertex(CommandContext ctx, int mode, int count, int type, long indices, int baseVertex) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        VulkanicAPI.traceShaderInputParityDraw("opengl-drawIndexedBaseVertex", true, mode, 0, 0, indices, count, type, 1, baseVertex);
        org.lwjgl.opengl.GL32.glDrawElementsBaseVertex(mode, count, type, indices, baseVertex);
    }
    
    private void drawIndexedInstanced(CommandContext ctx, int mode, int count, int type, long indices, int instanceCount) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        VulkanicAPI.traceShaderInputParityDraw("opengl-drawIndexedInstanced", true, mode, 0, 0, indices, count, type, instanceCount, 0);
        org.lwjgl.opengl.GL31.glDrawElementsInstanced(mode, count, type, indices, instanceCount);
    }
    
    private void drawArraysInstanced(CommandContext ctx, int mode, int first, int count, int instanceCount) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        VulkanicAPI.traceShaderInputParityDraw("opengl-drawArraysInstanced", false, mode, first, count, 0L, 0, 0, instanceCount, 0);
        org.lwjgl.opengl.GL31.glDrawArraysInstanced(mode, first, count, instanceCount);
    }
    
    @Override
    public void bindUniformBufferRange(CommandContext ctx, int target, int index, int buffer, long offset, long size) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL32.glBindBufferRange(target, index, buffer, offset, size);
    }
    
    @Override
    public void texBuffer(CommandContext ctx, int target, int internalFormat, int buffer) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL31.glTexBuffer(target, internalFormat, buffer);
    }
    
    
    @Override
    public void setUniform2fv(CommandContext ctx, int location, float[] value) {
        if (!ctx.isImmediate()) throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        org.lwjgl.opengl.GL20C.glUniform2fv(location, value);
        traceStandaloneUniformFloats(location, "vec2[]", false, value);
    }
    
    @Override
    public void setUniform3fv(CommandContext ctx, int location, float[] value) {
        if (!ctx.isImmediate()) throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        org.lwjgl.opengl.GL20C.glUniform3fv(location, value);
        traceStandaloneUniformFloats(location, "vec3[]", false, value);
    }
    
    @Override
    public void setUniform4fv(CommandContext ctx, int location, float[] value) {
        if (!ctx.isImmediate()) throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        org.lwjgl.opengl.GL20C.glUniform4fv(location, value);
        traceStandaloneUniformFloats(location, "vec4[]", false, value);
    }

    private void traceStandaloneUniformFloats(int location, String valueKind, boolean transpose, float[] values) {
        if (location < 0 || values == null || values.length == 0 || !VulkanicAPI.isShaderInputParityTracingEnabled()) {
            return;
        }
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        VulkanicAPI.traceShaderInputParityStandaloneUniformFloats(
            "opengl-setUniform",
            program,
            location,
            null,
            shaderStagesForProgram(program),
            uniformNameFor(program, location),
            valueKind,
            transpose,
            values
        );
    }

    private void traceStandaloneUniformInts(int location, String valueKind, int[] values) {
        if (location < 0 || values == null || values.length == 0 || !VulkanicAPI.isShaderInputParityTracingEnabled()) {
            return;
        }
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        VulkanicAPI.traceShaderInputParityStandaloneUniformInts(
            "opengl-setUniform",
            program,
            location,
            null,
            shaderStagesForProgram(program),
            uniformNameFor(program, location),
            valueKind,
            values
        );
    }

    private String shaderStagesForProgram(int program) {
        if (program <= 0) {
            return "unknown";
        }
        try {
            int count = GL20.glGetProgrami(program, GL20.GL_ATTACHED_SHADERS);
            if (count <= 0) {
                return "unknown";
            }
            int[] shaders = new int[count];
            int[] actualCount = new int[1];
            GL20.glGetAttachedShaders(program, actualCount, shaders);
            java.util.TreeSet<String> stages = new java.util.TreeSet<>();
            for (int index = 0; index < Math.min(count, actualCount[0]); index++) {
                int type = GL20.glGetShaderi(shaders[index], GL20.GL_SHADER_TYPE);
                if (type == GL20.GL_VERTEX_SHADER) {
                    stages.add("VERTEX");
                } else if (type == GL32.GL_GEOMETRY_SHADER) {
                    stages.add("GEOMETRY");
                } else if (type == GL20.GL_FRAGMENT_SHADER) {
                    stages.add("FRAGMENT");
                } else {
                    stages.add("UNKNOWN_" + Integer.toHexString(type));
                }
            }
            return stages.isEmpty() ? "unknown" : String.join("|", stages);
        } catch (RuntimeException exception) {
            return "unknown";
        }
    }

    @Nullable
    private String uniformNameFor(int program, int location) {
        java.util.concurrent.ConcurrentMap<Integer, String> namesByLocation = uniformNamesByProgramLocation.get(program);
        return namesByLocation == null ? null : namesByLocation.get(location);
    }

    private static float[] copyFloats(java.nio.FloatBuffer buffer, int maxCount) {
        java.nio.FloatBuffer duplicate = buffer.duplicate();
        int count = Math.min(duplicate.remaining(), maxCount);
        float[] values = new float[count];
        duplicate.get(values);
        return values;
    }
    
    @Override
    public void bindUniformBufferBase(CommandContext ctx, int bindingPoint, int bufferId) {
        if (!ctx.isImmediate()) throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        org.lwjgl.opengl.GL32C.glBindBufferBase(org.lwjgl.opengl.GL32C.GL_UNIFORM_BUFFER, bindingPoint, bufferId);
    }
    
    @Override
    public void bindFragDataLocation(CommandContext ctx, int program, int colorNumber, CharSequence name) {
        if (!ctx.isImmediate()) throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        org.lwjgl.opengl.GL30C.glBindFragDataLocation(program, colorNumber, name);
    }
    
    @Override
    public int getSynci(CommandContext ctx, long sync, int pname, java.nio.IntBuffer length) {
        if (!ctx.isImmediate()) throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        return org.lwjgl.opengl.GL32C.glGetSynci(sync, pname, length);
    }
    
    @Override
    public void deleteVertexArrays(CommandContext ctx, int vertexArray) {
        if (!ctx.isImmediate()) throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        org.lwjgl.opengl.GL30C.glDeleteVertexArrays(vertexArray);
    }
    
    private void multiDrawElementsBaseVertex(CommandContext ctx, int mode, long pCount, int type, long pIndices, int drawCount, long pBaseVertex) {
        if (!ctx.isImmediate()) throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        for (int drawIndex = 0; drawIndex < drawCount; drawIndex++) {
            int count = org.lwjgl.system.MemoryUtil.memGetInt(pCount + ((long) drawIndex * Integer.BYTES));
            if (count <= 0) {
                continue;
            }

            long indices = org.lwjgl.system.MemoryUtil.memGetAddress(pIndices + ((long) drawIndex * org.lwjgl.system.Pointer.POINTER_SIZE));
            int baseVertex = org.lwjgl.system.MemoryUtil.memGetInt(pBaseVertex + ((long) drawIndex * Integer.BYTES));
            VulkanicAPI.traceShaderInputParityDraw(
                "opengl-multiDrawElementsBaseVertex",
                true,
                mode,
                0,
                0,
                indices,
                count,
                type,
                1,
                baseVertex
            );
        }
        org.lwjgl.opengl.GL32C.nglMultiDrawElementsBaseVertex(mode, pCount, type, pIndices, drawCount, pBaseVertex);
    }
    
    /**
     * Helper method to convert LWJGL GLCapabilities to Vulkanic GraphicsCapabilities.
     * This extracts all capability flags from the OpenGL-specific object.
     */
    private GraphicsCapabilities convertCapabilities(org.lwjgl.opengl.GLCapabilities glCaps) {
        return new GraphicsCapabilities(
            // OpenGL version flags
            glCaps.OpenGL11, glCaps.OpenGL12, glCaps.OpenGL13, glCaps.OpenGL14, glCaps.OpenGL15,
            glCaps.OpenGL20, glCaps.OpenGL21,
            glCaps.OpenGL30, glCaps.OpenGL31, glCaps.OpenGL32, glCaps.OpenGL33,
            glCaps.OpenGL40, glCaps.OpenGL41, glCaps.OpenGL42, glCaps.OpenGL43, glCaps.OpenGL44, glCaps.OpenGL45, glCaps.OpenGL46,
            // Extension flags - using camelCase for consistency
            glCaps.GL_ARB_buffer_storage, glCaps.GL_ARB_vertex_attrib_binding, glCaps.GL_ARB_direct_state_access,
            glCaps.GL_ARB_debug_output, glCaps.GL_KHR_debug, glCaps.GL_AMD_debug_output,
            glCaps.GL_KHR_no_error, glCaps.GL_EXT_debug_label, glCaps.GL_ARB_timer_query,
            glCaps.GL_KHR_parallel_shader_compile, glCaps.GL_ARB_parallel_shader_compile,
            glCaps.GL_ARB_multi_bind, glCaps.GL_ARB_tessellation_shader,
            glCaps.GL_ARB_shader_storage_buffer_object, glCaps.GL_ARB_shader_image_load_store,
            glCaps.GL_EXT_shader_image_load_store, glCaps.GL_ARB_draw_buffers_blend,
            glCaps.GL_NVX_gpu_memory_info
        );
    }
    
    @Override
    public GraphicsCapabilities initializeGraphicsCapabilities() {
        return convertCapabilities(org.lwjgl.opengl.GL.createCapabilities());
    }
    
    @Override
    public boolean checkFunctionAvailable(String functionName) {
        org.lwjgl.opengl.GLCapabilities caps = org.lwjgl.opengl.GL.getCapabilities();
        try {
            java.lang.reflect.Field field = caps.getClass().getField(functionName);
            long address = field.getLong(caps);
            return address != org.lwjgl.system.MemoryUtil.NULL;
        } catch (Exception e) {
            return false;
        }
    }
    
    
    @Override
    public void clearTexImage(CommandContext ctx, int texture, int level, int format, int type, int[] data) {
        org.lwjgl.opengl.ARBClearTexture.glClearTexImage(texture, level, format, type, data);
    }
    
    
    @Override
    public void setMaxShaderCompilerThreads(int count) {
        org.lwjgl.opengl.GLCapabilities caps = org.lwjgl.opengl.GL.getCapabilities();
        if (caps.GL_KHR_parallel_shader_compile) {
            org.lwjgl.opengl.KHRParallelShaderCompile.glMaxShaderCompilerThreadsKHR(count);
        } else if (caps.GL_ARB_parallel_shader_compile) {
            org.lwjgl.opengl.ARBParallelShaderCompile.glMaxShaderCompilerThreadsARB(count);
        }
    }
    
    @Override
    public GraphicsCapabilities getGraphicsCapabilities() {
        return convertCapabilities(org.lwjgl.opengl.GL.getCapabilities());
    }

    @Override
    public String getBackendVendorName() {
        String cached = this.backendVendorName;
        if (!"unknown".equals(cached)) {
            return cached;
        }

        String value = org.lwjgl.opengl.GL11C.glGetString(VulkanicAPI.GL_VENDOR);
        return normalizeDeviceIdentity(value);
    }

    @Override
    public String getBackendRendererName() {
        String cached = this.backendRendererName;
        if (!"unknown".equals(cached)) {
            return cached;
        }

        String value = org.lwjgl.opengl.GL11C.glGetString(VulkanicAPI.GL_RENDERER);
        return normalizeDeviceIdentity(value);
    }

    @Override
    public String getBackendVersionName() {
        String cached = this.backendVersionName;
        if (!"unknown".equals(cached)) {
            return cached;
        }

        String value = org.lwjgl.opengl.GL11C.glGetString(VulkanicAPI.GL_VERSION);
        return normalizeDeviceIdentity(value);
    }

    private static String normalizeDeviceIdentity(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    @Override
    public java.util.List<String> getBackendEnabledExtensions() {
        net.blaze3d.opengl.GlDevice device = this.glDevice;
        if (device != null) {
            return device.getEnabledExtensions();
        }
        return java.util.List.of();
    }

    @Override
    public java.util.List<String> getBackendOptionalFeatureNames() {
        net.blaze3d.opengl.GlDevice device = this.glDevice;
        if (device != null) {
            return device.getOptionalFeatureNames();
        }
        return java.util.List.of();
    }

    @Override
    public int getBackendMaxTextureSize() {
        GlDevice device = this.glDevice;
        if (device == null) {
            throw new IllegalStateException(
                "GlDevice has not been registered with OpenGLBackend. "
                    + "Ensure GlDevice calls VulkanicAPI.registerDevice() during initialization.");
        }
        return device.getMaxTextureSize();
    }

    @Override
    public int getBackendUniformOffsetAlignment() {
        GlDevice device = this.glDevice;
        if (device == null) {
            throw new IllegalStateException(
                "GlDevice has not been registered with OpenGLBackend. "
                    + "Ensure GlDevice calls VulkanicAPI.registerDevice() during initialization.");
        }
        return device.getUniformOffsetAlignment();
    }

    @Override
    public GpuDevice.GpuDeviceInfo getBackendDeviceInfo() {
        GlDevice device = this.glDevice;
        if (device != null) {
            return device.getDeviceInfo();
        }

        return new GpuDevice.GpuDeviceInfo(
            "OpenGL",
            "OpenGL",
            getBackendVendorName(),
            getBackendRendererName(),
            getBackendVersionName(),
            true,
            getBackendOptionalFeatureNames()
        );
    }

    @Override
    public int resolveTextureHandle(CommandContext ctx, net.vulkanic.VulkanicTexture texture) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }

        if (texture instanceof net.vulkanic.backends.opengl.OpenGLTexture openGLTexture) {
            return openGLTexture.getGlHandle();
        }

        if (texture instanceof net.blaze3d.opengl.GlTexture glTexture) {
            return glTexture.getGlHandle();
        }

        return 0;
    }

    @Override
    public int resolveFramebufferForTextures(CommandContext ctx, net.vulkanic.VulkanicTexture colorTexture, net.vulkanic.VulkanicTexture depthTexture) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }

        if (!(colorTexture instanceof net.blaze3d.opengl.GlTexture glColorTexture)) {
            return 0;
        }

        net.blaze3d.opengl.GlDevice device = this.glDevice;
        if (device == null) {
            return 0;
        }

        net.blaze3d.textures.GpuTexture depthGpuTexture = depthTexture instanceof net.blaze3d.textures.GpuTexture gpuTexture
            ? gpuTexture
            : null;
        return glColorTexture.getFbo(device.directStateAccess(), depthGpuTexture);
    }
    
    
    
    
    // Additional methods for IrisRenderSystem
    
    @Override
    public void getIntegerv(CommandContext ctx, int pname, int[] params) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL32C.glGetIntegerv(pname, params);
    }
    
    
    @Override
    public void getFloatv(CommandContext ctx, int pname, float[] params) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL32C.glGetFloatv(pname, params);
    }
    
    
    private void uploadTexture1D(CommandContext ctx, int target, int level, int internalformat, int width, int border, int format, int type, java.nio.ByteBuffer pixels) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL30C.glTexImage1D(target, level, internalformat, width, border, format, type, pixels);
    }
    
    
    
    private void uploadTexture3D(CommandContext ctx, int target, int level, int internalformat, int width, int height, int depth, int border, int format, int type, java.nio.ByteBuffer pixels) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL30C.glTexImage3D(target, level, internalformat, width, height, depth, border, format, type, pixels);
    }
    
    
    
    
    private void copyTexImage2D(CommandContext ctx, int target, int level, int internalFormat, int x, int y, int width, int height, int border) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL32C.glCopyTexImage2D(target, level, internalFormat, x, y, width, height, border);
    }
    
    
    
    
    
    
    
    
    
    
    
    @Override
    public void texParameterf(CommandContext ctx, int target, int pname, float param) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL32C.glTexParameterf(target, pname, param);
    }
    
    @Override
    public void texParameteri(CommandContext ctx, int target, int pname, int param) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL11.glTexParameteri(target, pname, param);
    }

    @Override
    public void texParameteri(CommandContext ctx, VulkanicTextureTarget target, VulkanicTextureParameterName pname, int param) {
        texParameteri(ctx, toOpenGLTextureTarget(target), toOpenGLTextureParameterName(pname), param);
    }

    private static int toOpenGLTextureTarget(VulkanicTextureTarget target) {
        return switch (target) {
            case TEXTURE_2D -> VulkanicAPI.GL_TEXTURE_2D;
            case TEXTURE_3D -> VulkanicAPI.GL_TEXTURE_3D;
            case TEXTURE_BUFFER -> VulkanicAPI.GL_TEXTURE_BUFFER;
            case TEXTURE_CUBE_MAP -> VulkanicAPI.GL_TEXTURE_CUBE_MAP;
            case TEXTURE_RECTANGLE -> VulkanicAPI.GL_TEXTURE_RECTANGLE;
        };
    }

    private static int toOpenGLBufferTarget(VulkanicBufferTarget target) {
        return switch (target) {
            case VERTEX -> VulkanicAPI.GL_ARRAY_BUFFER;
            case INDEX -> VulkanicAPI.GL_ELEMENT_ARRAY_BUFFER;
            case COPY_READ -> VulkanicAPI.GL_COPY_READ_BUFFER;
            case COPY_WRITE -> VulkanicAPI.GL_COPY_WRITE_BUFFER;
            case PIXEL_PACK -> VulkanicAPI.GL_PIXEL_PACK_BUFFER;
            case SHADER_STORAGE -> VulkanicAPI.GL_SHADER_STORAGE_BUFFER;
            case UNIFORM -> VulkanicAPI.GL_UNIFORM_BUFFER;
        };
    }

    private static int toOpenGLIntegerQuery(VulkanicIntegerQuery query) {
        return switch (query) {
            case CONTEXT_FLAGS -> VulkanicAPI.GL_CONTEXT_FLAGS;
            case CURRENT_PROGRAM -> VulkanicAPI.GL_CURRENT_PROGRAM;
            case VERTEX_ARRAY_BINDING -> VulkanicAPI.GL_VERTEX_ARRAY_BINDING;
            case ARRAY_BUFFER_BINDING -> VulkanicAPI.GL_ARRAY_BUFFER_BINDING;
            case ELEMENT_ARRAY_BUFFER_BINDING -> VulkanicAPI.GL_ELEMENT_ARRAY_BUFFER_BINDING;
            case ACTIVE_TEXTURE -> VulkanicAPI.GL_ACTIVE_TEXTURE;
            case BLEND_EQUATION_RGB -> VulkanicAPI.GL_BLEND_EQUATION_RGB;
            case BLEND_EQUATION_ALPHA -> VulkanicAPI.GL_BLEND_EQUATION_ALPHA;
            case BLEND_SRC_RGB -> VulkanicAPI.GL_BLEND_SRC_RGB;
            case BLEND_SRC_ALPHA -> VulkanicAPI.GL_BLEND_SRC_ALPHA;
            case BLEND_DST_RGB -> VulkanicAPI.GL_BLEND_DST_RGB;
            case BLEND_DST_ALPHA -> VulkanicAPI.GL_BLEND_DST_ALPHA;
            case DEPTH_WRITEMASK -> VulkanicAPI.GL_DEPTH_WRITEMASK;
            case DEPTH_FUNC -> VulkanicAPI.GL_DEPTH_FUNC;
            case STENCIL_FUNC -> VulkanicAPI.GL_STENCIL_FUNC;
            case STENCIL_REF -> VulkanicAPI.GL_STENCIL_REF;
            case STENCIL_VALUE_MASK -> VulkanicAPI.GL_STENCIL_VALUE_MASK;
            case STENCIL_FAIL -> VulkanicAPI.GL_STENCIL_FAIL;
            case STENCIL_PASS_DEPTH_FAIL -> VulkanicAPI.GL_STENCIL_PASS_DEPTH_FAIL;
            case STENCIL_PASS_DEPTH_PASS -> VulkanicAPI.GL_STENCIL_PASS_DEPTH_PASS;
            case STENCIL_WRITEMASK -> VulkanicAPI.GL_STENCIL_WRITEMASK;
            case CULL_FACE_MODE -> VulkanicAPI.GL_CULL_FACE_MODE;
            case POLYGON_MODE -> VulkanicAPI.GL_POLYGON_MODE;
            case MAX_TEXTURE_SIZE -> VulkanicAPI.GL_MAX_TEXTURE_SIZE;
            case MAX_TEXTURE_IMAGE_UNITS -> VulkanicAPI.GL_MAX_TEXTURE_IMAGE_UNITS;
            case MAX_DRAW_BUFFERS -> VulkanicAPI.GL_MAX_DRAW_BUFFERS;
            case MAX_SHADER_STORAGE_BUFFER_BINDINGS -> VulkanicAPI.GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS;
            case UNIFORM_BUFFER_OFFSET_ALIGNMENT -> VulkanicAPI.GL_UNIFORM_BUFFER_OFFSET_ALIGNMENT;
            case TEXTURE_BINDING_2D -> VulkanicAPI.GL_TEXTURE_BINDING_2D;
            case FRAMEBUFFER_BINDING -> VulkanicAPI.GL_FRAMEBUFFER_BINDING;
            case MAX_COLOR_ATTACHMENTS -> VulkanicAPI.GL_MAX_COLOR_ATTACHMENTS;
            case NUM_EXTENSIONS -> VulkanicAPI.GL_NUM_EXTENSIONS;
            case MAX_LABEL_LENGTH -> VulkanicAPI.GL_MAX_LABEL_LENGTH;
            case MAX_TEXTURE_LOD_BIAS -> VulkanicAPI.GL_MAX_TEXTURE_LOD_BIAS;
            case TEXTURE_MAX_LEVEL -> VulkanicAPI.GL_TEXTURE_MAX_LEVEL;
            case GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX -> VulkanicAPI.GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX;
        };
    }

    private static int toOpenGLTextureParameterName(VulkanicTextureParameterName pname) {
        return switch (pname) {
            case MIN_FILTER -> VulkanicAPI.GL_TEXTURE_MIN_FILTER;
            case MAG_FILTER -> VulkanicAPI.GL_TEXTURE_MAG_FILTER;
            case WRAP_S -> VulkanicAPI.GL_TEXTURE_WRAP_S;
            case WRAP_T -> VulkanicAPI.GL_TEXTURE_WRAP_T;
            case WRAP_R -> VulkanicAPI.GL_TEXTURE_WRAP_R;
            case MIN_LOD -> VulkanicAPI.GL_TEXTURE_MIN_LOD;
            case MAX_LOD -> VulkanicAPI.GL_TEXTURE_MAX_LOD;
            case LOD_BIAS -> VulkanicAPI.GL_TEXTURE_LOD_BIAS;
            case BASE_LEVEL -> VulkanicAPI.GL_TEXTURE_BASE_LEVEL;
            case MAX_LEVEL -> VulkanicAPI.GL_TEXTURE_MAX_LEVEL;
            case COMPARE_MODE -> VulkanicAPI.GL_TEXTURE_COMPARE_MODE;
            case DEPTH_STENCIL_TEXTURE_MODE -> VulkanicAPI.GL_DEPTH_STENCIL_TEXTURE_MODE;
            case SWIZZLE_RGBA -> VulkanicAPI.GL_TEXTURE_SWIZZLE_RGBA;
        };
    }

    private static int toOpenGLCapability(VulkanicCapability capability) {
        return switch (capability) {
            case BLEND -> VulkanicAPI.GL_BLEND;
            case CULL_FACE -> VulkanicAPI.GL_CULL_FACE;
            case DEPTH_TEST -> VulkanicAPI.GL_DEPTH_TEST;
            case SCISSOR_TEST -> VulkanicAPI.GL_SCISSOR_TEST;
            case POLYGON_OFFSET_FILL -> VulkanicAPI.GL_POLYGON_OFFSET_FILL;
            case COLOR_LOGIC_OP -> VulkanicAPI.GL_COLOR_LOGIC_OP;
            case PROGRAM_POINT_SIZE -> VulkanicAPI.GL_PROGRAM_POINT_SIZE;
            case DEBUG_OUTPUT -> VulkanicAPI.GL_DEBUG_OUTPUT;
            case DEBUG_OUTPUT_SYNCHRONOUS -> VulkanicAPI.GL_DEBUG_OUTPUT_SYNCHRONOUS;
            case STENCIL_TEST -> VulkanicAPI.GL_STENCIL_TEST;
        };
    }

    private static int toOpenGLCullFaceMode(VulkanicCullFaceMode mode) {
        return switch (mode) {
            case FRONT -> VulkanicAPI.GL_FRONT;
            case BACK -> VulkanicAPI.GL_BACK;
            case FRONT_AND_BACK -> VulkanicAPI.GL_FRONT_AND_BACK;
        };
    }

    private static int toOpenGLDepthCompareOp(VulkanicDepthCompareOp op) {
        return switch (op) {
            case NEVER -> VulkanicAPI.GL_NEVER;
            case LESS -> VulkanicAPI.GL_LESS;
            case EQUAL -> VulkanicAPI.GL_EQUAL;
            case LEQUAL -> VulkanicAPI.GL_LEQUAL;
            case GREATER -> VulkanicAPI.GL_GREATER;
            case NOTEQUAL -> VulkanicAPI.GL_NOTEQUAL;
            case GEQUAL -> VulkanicAPI.GL_GEQUAL;
            case ALWAYS -> VulkanicAPI.GL_ALWAYS;
        };
    }

    private static int toOpenGLStencilCompareOp(VulkanicStencilCompareOp op) {
        return switch (op) {
            case NEVER -> VulkanicAPI.GL_NEVER;
            case LESS -> VulkanicAPI.GL_LESS;
            case EQUAL -> VulkanicAPI.GL_EQUAL;
            case LEQUAL -> VulkanicAPI.GL_LEQUAL;
            case GREATER -> VulkanicAPI.GL_GREATER;
            case NOTEQUAL -> VulkanicAPI.GL_NOTEQUAL;
            case GEQUAL -> VulkanicAPI.GL_GEQUAL;
            case ALWAYS -> VulkanicAPI.GL_ALWAYS;
        };
    }

    private static int toOpenGLStencilFace(VulkanicStencilFace face) {
        return switch (face) {
            case FRONT -> VulkanicAPI.GL_FRONT;
            case BACK -> VulkanicAPI.GL_BACK;
            case FRONT_AND_BACK -> VulkanicAPI.GL_FRONT_AND_BACK;
        };
    }

    private static int toOpenGLStencilOp(VulkanicStencilOperation op) {
        return switch (op) {
            case KEEP -> VulkanicAPI.GL_KEEP;
            case ZERO -> VulkanicAPI.GL_ZERO;
            case REPLACE -> VulkanicAPI.GL_REPLACE;
            case INCREMENT_CLAMP -> VulkanicAPI.GL_INCR;
            case DECREMENT_CLAMP -> VulkanicAPI.GL_DECR;
            case INVERT -> VulkanicAPI.GL_INVERT;
            case INCREMENT_WRAP -> VulkanicAPI.GL_INCR_WRAP;
            case DECREMENT_WRAP -> VulkanicAPI.GL_DECR_WRAP;
        };
    }

    private static int toOpenGLBlendFactor(VulkanicBlendFactor factor) {
        return switch (factor) {
            case ZERO -> VulkanicAPI.GL_ZERO;
            case ONE -> VulkanicAPI.GL_ONE;
            case SRC_COLOR -> VulkanicAPI.GL_SRC_COLOR;
            case ONE_MINUS_SRC_COLOR -> VulkanicAPI.GL_ONE_MINUS_SRC_COLOR;
            case DST_COLOR -> VulkanicAPI.GL_DST_COLOR;
            case ONE_MINUS_DST_COLOR -> VulkanicAPI.GL_ONE_MINUS_DST_COLOR;
            case SRC_ALPHA -> VulkanicAPI.GL_SRC_ALPHA;
            case ONE_MINUS_SRC_ALPHA -> VulkanicAPI.GL_ONE_MINUS_SRC_ALPHA;
            case DST_ALPHA -> VulkanicAPI.GL_DST_ALPHA;
            case ONE_MINUS_DST_ALPHA -> VulkanicAPI.GL_ONE_MINUS_DST_ALPHA;
            case SRC_ALPHA_SATURATE -> VulkanicAPI.GL_SRC_ALPHA_SATURATE;
            case CONSTANT_COLOR -> VulkanicAPI.GL_CONSTANT_COLOR;
            case ONE_MINUS_CONSTANT_COLOR -> VulkanicAPI.GL_ONE_MINUS_CONSTANT_COLOR;
            case CONSTANT_ALPHA -> VulkanicAPI.GL_CONSTANT_ALPHA;
            case ONE_MINUS_CONSTANT_ALPHA -> VulkanicAPI.GL_ONE_MINUS_CONSTANT_ALPHA;
        };
    }

    private static int toOpenGLBlendEquation(VulkanicBlendEquation equation) {
        return switch (equation) {
            case ADD -> VulkanicAPI.GL_FUNC_ADD;
            case SUBTRACT -> VulkanicAPI.GL_FUNC_SUBTRACT;
            case REVERSE_SUBTRACT -> VulkanicAPI.GL_FUNC_REVERSE_SUBTRACT;
            case MIN -> VulkanicAPI.GL_MIN;
            case MAX -> VulkanicAPI.GL_MAX;
        };
    }
    
    
    
    
    
    
    
    
    
    @Override
    public String getActiveUniform(CommandContext ctx, int program, int index, int size, java.nio.IntBuffer type, java.nio.IntBuffer name) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.GL32C.glGetActiveUniform(program, index, size, type, name);
    }
    
    
    private void readPixels(CommandContext ctx, int x, int y, int width, int height, int format, int type, float[] pixels) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL32C.glReadPixels(x, y, width, height, format, type, pixels);
    }
    
    private void readPixels(CommandContext ctx, int x, int y, int width, int height, int format, int type, long pixels) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL32C.nglReadPixels(x, y, width, height, format, type, pixels);
    }
    
    
    
    
    
    
    
    
    
    
    
    
    
    @Override
    public void setVertexAttrib4f(CommandContext ctx, int index, float v0, float v1, float v2, float v3) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL32C.glVertexAttrib4f(index, v0, v1, v2, v3);
    }
    
    
    
    
    
    
    @Override
    public void bindImageTexture(CommandContext ctx, int unit, int texture, int level, boolean layered, int layer, int access, int format) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GLCapabilities caps = org.lwjgl.opengl.GL.getCapabilities();
        if (caps.OpenGL42 || caps.GL_ARB_shader_image_load_store) {
            org.lwjgl.opengl.GL42C.glBindImageTexture(unit, texture, level, layered, layer, access, format);
        } else {
            org.lwjgl.opengl.EXTShaderImageLoadStore.glBindImageTextureEXT(unit, texture, level, layered, layer, access, format);
        }
    }
    
    
    
    @Override
    public void createBuffers(CommandContext ctx, int[] buffers) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL45C.glGenBuffers(buffers);
    }
    
    
    
    @Override
    public void getProgramiv(CommandContext ctx, int program, int pname, int[] params) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL32C.glGetProgramiv(program, pname, params);
    }
    
    
    
    @Override
    public void memoryBarrier(CommandContext ctx, int barriers) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL45C.glMemoryBarrier(barriers);
    }

    @Override
    public void applyResourceBarriers(CommandContext ctx, net.vulkanic.VulkanicResourceBarriers barriers) {
        net.vulkanic.VulkanicResourceBarriers safeBarriers =
            java.util.Objects.requireNonNull(barriers, "barriers must not be null");
        memoryBarrier(ctx, safeBarriers.toOpenGLBarrierBits());
    }
    
    
    
    
    
    @Override
    public void blendFuncSeparatei(CommandContext ctx, int buffer, int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.ARBDrawBuffersBlend.glBlendFuncSeparateiARB(buffer, srcRGB, dstRGB, srcAlpha, dstAlpha);
    }

    @Override
    public void blendFuncSeparatei(
        CommandContext ctx,
        int buffer,
        VulkanicBlendFactor srcRGB,
        VulkanicBlendFactor dstRGB,
        VulkanicBlendFactor srcAlpha,
        VulkanicBlendFactor dstAlpha
    ) {
        blendFuncSeparatei(
            ctx,
            buffer,
            toOpenGLBlendFactor(srcRGB),
            toOpenGLBlendFactor(dstRGB),
            toOpenGLBlendFactor(srcAlpha),
            toOpenGLBlendFactor(dstAlpha)
        );
    }
    
    
    
    
    @Override
    public int createSampler(CommandContext ctx) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.GL33C.glGenSamplers();
    }
    
    
    @Override
    public void deleteSampler(CommandContext ctx, int sampler) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL33C.glDeleteSamplers(sampler);
    }
    
    
    
    @Override
    public void bindSamplers(CommandContext ctx, int first, int[] samplers) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL45C.glBindSamplers(first, samplers);
    }
    
    
    @Override
    public void setSamplerParameteri(CommandContext ctx, int sampler, int pname, int param) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL33C.glSamplerParameteri(sampler, pname, param);
    }
    
    
    @Override
    public void setSamplerParameterf(CommandContext ctx, int sampler, int pname, float param) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL33C.glSamplerParameterf(sampler, pname, param);
    }
    
    
    @Override
    public void setSamplerParameteriv(CommandContext ctx, int sampler, int pname, int[] params) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL33C.glSamplerParameteriv(sampler, pname, params);
    }
    
    
    
    
    
    
    private void dispatchComputeIndirect(CommandContext ctx, long offset) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL43C.glDispatchComputeIndirect(offset);
    }
    
    
    
    @Override
    public String getString(CommandContext ctx, int name, int index) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.GL46C.glGetStringi(name, index);
    }
    
    @Override
    public String getString(CommandContext ctx, int name) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.GL11C.glGetString(name);
    }
    
    
    private void copyImageSubData(CommandContext ctx, int srcName, int srcTarget, int srcLevel, int srcX, int srcY, int srcZ,
                                 int dstName, int dstTarget, int dstLevel, int dstX, int dstY, int dstZ,
                                 int width, int height, int depth) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL46C.glCopyImageSubData(srcName, srcTarget, srcLevel, srcX, srcY, srcZ, 
                                                    dstName, dstTarget, dstLevel, dstX, dstY, dstZ, 
                                                    width, height, depth);
    }
    
    
    @Override
    public int checkFramebufferStatus(CommandContext ctx, int target) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.GL46C.glCheckFramebufferStatus(target);
    }
    
    
    
    
    
    
    private void generateMipmap(CommandContext ctx, int target) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL32C.glGenerateMipmap(target);
    }
    
    
    
    // DSA methods
    
    private void generateTextureMipmapDSA(CommandContext ctx, int texture) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.ARBDirectStateAccess.glGenerateTextureMipmap(texture);
    }
    
    
    @Override
    public void textureParameteri(CommandContext ctx, int texture, int pname, int param) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.ARBDirectStateAccess.glTextureParameteri(texture, pname, param);
    }
    
    
    @Override
    public void textureParameterf(CommandContext ctx, int texture, int pname, float param) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.ARBDirectStateAccess.glTextureParameterf(texture, pname, param);
    }
    
    
    @Override
    public void textureParameteriv(CommandContext ctx, int texture, int pname, int[] params) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.ARBDirectStateAccess.glTextureParameteriv(texture, pname, params);
    }
    
    
    @Override
    public void namedFramebufferReadBuffer(CommandContext ctx, int framebuffer, int mode) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.ARBDirectStateAccess.glNamedFramebufferReadBuffer(framebuffer, mode);
    }
    
    
    @Override
    public void namedFramebufferDrawBuffers(CommandContext ctx, int framebuffer, int[] bufs) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.ARBDirectStateAccess.glNamedFramebufferDrawBuffers(framebuffer, bufs);
    }
    
    
    @Override
    public void clearNamedFramebufferfv(CommandContext ctx, int framebuffer, int buffer, int drawbuffer, float[] value) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.ARBDirectStateAccess.glClearNamedFramebufferfv(framebuffer, buffer, drawbuffer, value);
    }
    
    
    @Override
    public void clearNamedFramebufferiv(CommandContext ctx, int framebuffer, int buffer, int drawbuffer, int[] value) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.ARBDirectStateAccess.glClearNamedFramebufferiv(framebuffer, buffer, drawbuffer, value);
    }
    
    
    @Override
    public void clearNamedFramebufferuiv(CommandContext ctx, int framebuffer, int buffer, int drawbuffer, int[] value) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.ARBDirectStateAccess.glClearNamedFramebufferuiv(framebuffer, buffer, drawbuffer, value);
    }
    
    
    @Override
    public int getFramebufferAttachmentParameteri(CommandContext ctx, int target, int attachment, int pname) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return GL30.glGetFramebufferAttachmentParameteri(target, attachment, pname);
    }
    
    @Override
    public int getTextureParameteri(CommandContext ctx, int texture, int pname) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.ARBDirectStateAccess.glGetTextureParameteri(texture, pname);
    }
    
    
    private void copyTextureSubImage2D(CommandContext ctx, int texture, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.ARBDirectStateAccess.glCopyTextureSubImage2D(texture, level, xoffset, yoffset, x, y, width, height);
    }
    
    
    @Override
    public void bindTextureUnit(CommandContext ctx, int unit, int texture) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.ARBDirectStateAccess.glBindTextureUnit(unit, texture);
    }
    
    
    @Override
    public int createBuffers(CommandContext ctx) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.ARBDirectStateAccess.glCreateBuffers();
    }
    
    
    @Override
    public void namedBufferData(CommandContext ctx, int buffer, float[] data, int usage) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL45C.glNamedBufferData(buffer, data, usage);
    }
    
    
    private void blitNamedFramebuffer(CommandContext ctx, int readFramebuffer, int drawFramebuffer, int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.ARBDirectStateAccess.glBlitNamedFramebuffer(readFramebuffer, drawFramebuffer, srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }
    
    
    @Override
    public void namedFramebufferTexture(CommandContext ctx, int framebuffer, int attachment, int texture, int level) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.ARBDirectStateAccess.glNamedFramebufferTexture(framebuffer, attachment, texture, level);
    }
    
    
    @Override
    public int createFramebuffers(CommandContext ctx) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.ARBDirectStateAccess.glCreateFramebuffers();
    }
    
    
    @Override
    public int createTextures(CommandContext ctx, int target) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.ARBDirectStateAccess.glCreateTextures(target);
    }
    
    
    // Additional rendering operations
    
    
    
    
    
    
    
    
    // High-level debug callback wrapper implementations
    @Override
    public void setupDebugMessageCallback(VulkanicAPI.DebugMessageCallback callback) {
        org.lwjgl.opengl.GLDebugMessageCallback proc = org.lwjgl.opengl.GLDebugMessageCallback.create(
            (source, type, id, severity, length, message, userParam) -> {
                String messageStr = org.lwjgl.opengl.GLDebugMessageCallback.getMessage(length, message);
                callback.invoke(source, type, id, severity, messageStr);
            }
        );
        org.lwjgl.opengl.GL43C.glDebugMessageCallback(proc, 0L);
    }
    
    @Override
    public void setupDebugMessageCallbackKHR(VulkanicAPI.DebugMessageCallback callback) {
        org.lwjgl.opengl.GLDebugMessageCallback proc = org.lwjgl.opengl.GLDebugMessageCallback.create(
            (source, type, id, severity, length, message, userParam) -> {
                String messageStr = org.lwjgl.opengl.GLDebugMessageCallback.getMessage(length, message);
                callback.invoke(source, type, id, severity, messageStr);
            }
        );
        org.lwjgl.opengl.KHRDebug.glDebugMessageCallback(proc, 0L);
    }
    
    @Override
    public void setupDebugMessageCallbackARB(VulkanicAPI.DebugMessageCallback callback) {
        org.lwjgl.opengl.GLDebugMessageARBCallback proc = org.lwjgl.opengl.GLDebugMessageARBCallback.create(
            (source, type, id, severity, length, message, userParam) -> {
                String messageStr = org.lwjgl.opengl.GLDebugMessageARBCallback.getMessage(length, message);
                callback.invoke(source, type, id, severity, messageStr);
            }
        );
        org.lwjgl.opengl.ARBDebugOutput.glDebugMessageCallbackARB(proc, 0L);
    }
    
    @Override
    public void setupDebugMessageCallbackAMD(VulkanicAPI.DebugMessageCallbackAMD callback) {
        org.lwjgl.opengl.GLDebugMessageAMDCallback proc = org.lwjgl.opengl.GLDebugMessageAMDCallback.create(
            (id, category, severity, length, message, userParam) -> {
                String messageStr = org.lwjgl.opengl.GLDebugMessageAMDCallback.getMessage(length, message);
                callback.invoke(id, category, severity, messageStr);
            }
        );
        org.lwjgl.opengl.AMDDebugOutput.glDebugMessageCallbackAMD(proc, 0L);
    }
    
    @Override
    public void clearDebugMessageCallback() {
        org.lwjgl.opengl.GL43C.glDebugMessageCallback(null, 0L);
    }
    
    @Override
    public void clearDebugMessageCallbackKHR() {
        org.lwjgl.opengl.KHRDebug.glDebugMessageCallback(null, 0L);
    }
    
    @Override
    public void clearDebugMessageCallbackARB() {
        org.lwjgl.opengl.ARBDebugOutput.glDebugMessageCallbackARB(null, 0L);
    }
    
    @Override
    public void clearDebugMessageCallbackAMD() {
        org.lwjgl.opengl.AMDDebugOutput.glDebugMessageCallbackAMD(null, 0L);
    }
    
    // GL43+ vertex attribute methods
    
    
    @Override
    public void setVertexAttribFormat(CommandContext ctx, int attribindex, int size, int type, boolean normalized, int relativeoffset) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL43C.glVertexAttribFormat(attribindex, size, type, normalized, relativeoffset);
    }
    
    @Override
    public void setVertexAttribIFormat(CommandContext ctx, int attribindex, int size, int type, int relativeoffset) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL43C.glVertexAttribIFormat(attribindex, size, type, relativeoffset);
    }
    
    @Override
    public void setVertexAttribBinding(CommandContext ctx, int attribindex, int bindingindex) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL43C.glVertexAttribBinding(attribindex, bindingindex);
    }
    
    // VAO methods
    
    
    
    
    // GL context capabilities
    
    @Override
    public Object getGLCapabilities() {
        return org.lwjgl.opengl.GL.getCapabilities();
    }
    
    @Override
    public void setupDebugMessageCallback(java.io.PrintStream stream) {
        org.lwjgl.opengl.GLUtil.setupDebugMessageCallback(stream);
    }
    
    // Capability checking methods
    
    @Override
    public boolean checkOpenGL32Support() {
        org.lwjgl.opengl.GLCapabilities caps = (org.lwjgl.opengl.GLCapabilities) getGLCapabilities();
        return caps.OpenGL32;
    }
    
    @Override
    public boolean checkOpenGL33Support() {
        org.lwjgl.opengl.GLCapabilities caps = (org.lwjgl.opengl.GLCapabilities) getGLCapabilities();
        return caps.OpenGL33;
    }
    
    @Override
    public boolean checkARBInstancedArraysSupport() {
        org.lwjgl.opengl.GLCapabilities caps = (org.lwjgl.opengl.GLCapabilities) getGLCapabilities();
        return caps.GL_ARB_instanced_arrays;
    }
    
    @Override
    public long getNamedBufferDataPointer() {
        org.lwjgl.opengl.GLCapabilities caps = (org.lwjgl.opengl.GLCapabilities) getGLCapabilities();
        return caps.glNamedBufferData;
    }
    
    @Override
    public long getBufferStoragePointer() {
        org.lwjgl.opengl.GLCapabilities caps = (org.lwjgl.opengl.GLCapabilities) getGLCapabilities();
        return caps.glBufferStorage;
    }
    
    @Override
    public long getBindVertexBufferPointer() {
        org.lwjgl.opengl.GLCapabilities caps = (org.lwjgl.opengl.GLCapabilities) getGLCapabilities();
        return caps.glBindVertexBuffer;
    }
    
    
    // Additional GL query and state methods
    
    
    @Override
    public boolean isFramebuffer(CommandContext ctx, int framebuffer) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.GL30.glIsFramebuffer(framebuffer);
    }
    
    
    @Override
    public boolean isVertexArray(CommandContext ctx, int array) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.GL30.glIsVertexArray(array);
    }
    
    @Override
    public boolean isProgram(CommandContext ctx, int program) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.GL20.glIsProgram(program);
    }
    
    // Additional GL state methods
    
    @Override
    public void setBlendEquationSeparate(CommandContext ctx, int modeRGB, int modeAlpha) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL20.glBlendEquationSeparate(modeRGB, modeAlpha);
    }

    @Override
    public void setBlendEquationSeparate(CommandContext ctx, VulkanicBlendEquation modeRGB, VulkanicBlendEquation modeAlpha) {
        setBlendEquationSeparate(ctx, toOpenGLBlendEquation(modeRGB), toOpenGLBlendEquation(modeAlpha));
    }
    
    @Override
    public void setStencilFunc(CommandContext ctx, int func, int ref, int mask) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL11.glStencilFunc(func, ref, mask);
    }

    @Override
    public void setStencilFunc(CommandContext ctx, VulkanicStencilCompareOp func, int ref, int mask) {
        setStencilFunc(ctx, toOpenGLStencilCompareOp(func), ref, mask);
    }

    @Override
    public void setStencilFuncSeparate(CommandContext ctx, int face, int func, int ref, int mask) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL20.glStencilFuncSeparate(face, func, ref, mask);
    }

    @Override
    public void setStencilFuncSeparate(CommandContext ctx, VulkanicStencilFace face, VulkanicStencilCompareOp func, int ref, int mask) {
        setStencilFuncSeparate(ctx, toOpenGLStencilFace(face), toOpenGLStencilCompareOp(func), ref, mask);
    }

    @Override
    public void setStencilOp(CommandContext ctx, int stencilFailOp, int depthFailOp, int depthPassOp) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL11.glStencilOp(stencilFailOp, depthFailOp, depthPassOp);
    }

    @Override
    public void setStencilOp(
        CommandContext ctx,
        VulkanicStencilOperation stencilFailOp,
        VulkanicStencilOperation depthFailOp,
        VulkanicStencilOperation depthPassOp
    ) {
        setStencilOp(
            ctx,
            toOpenGLStencilOp(stencilFailOp),
            toOpenGLStencilOp(depthFailOp),
            toOpenGLStencilOp(depthPassOp)
        );
    }

    @Override
    public void setStencilOpSeparate(CommandContext ctx, int face, int stencilFailOp, int depthFailOp, int depthPassOp) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL20.glStencilOpSeparate(face, stencilFailOp, depthFailOp, depthPassOp);
    }

    @Override
    public void setStencilOpSeparate(
        CommandContext ctx,
        VulkanicStencilFace face,
        VulkanicStencilOperation stencilFailOp,
        VulkanicStencilOperation depthFailOp,
        VulkanicStencilOperation depthPassOp
    ) {
        setStencilOpSeparate(
            ctx,
            toOpenGLStencilFace(face),
            toOpenGLStencilOp(stencilFailOp),
            toOpenGLStencilOp(depthFailOp),
            toOpenGLStencilOp(depthPassOp)
        );
    }

    @Override
    public void setStencilWriteMask(CommandContext ctx, int mask) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL11.glStencilMask(mask);
    }

    @Override
    public void setStencilWriteMaskSeparate(CommandContext ctx, int face, int mask) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL20.glStencilMaskSeparate(face, mask);
    }

    @Override
    public void setStencilWriteMaskSeparate(CommandContext ctx, VulkanicStencilFace face, int mask) {
        setStencilWriteMaskSeparate(ctx, toOpenGLStencilFace(face), mask);
    }
    
    
    // Additional texture methods
    
    
    private void dispatchCompute(CommandContext ctx, int workX, int workY, int workZ) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL43.glDispatchCompute(workX, workY, workZ);
    }

    @Override
    public VulkanicGalExecutionRequest.ExecutionResult executeComputeDispatch(CommandContext ctx, VulkanicGalExecutionRequest.ComputeDispatchRequest request) {
        long auditStartNanos = VulkanPerfAudit.isEnabled() ? System.nanoTime() : 0L;
        try {
            invalidateAppliedDrawState();
            consumeResourceUsagePlan(request.resourcePlan());
            VulkanicGalExecutionRequest.ComputeDispatchCommand command = request.command();
            if (command.indirect()) {
                dispatchComputeIndirect(ctx, command.indirectOffset());
            } else {
                dispatchCompute(ctx, command.workX(), command.workY(), command.workZ());
            }
            return VulkanicGalExecutionRequest.success(request.semanticIdentity());
        } catch (RuntimeException exception) {
            invalidateAppliedDrawState();
            return VulkanicGalExecutionRequest.backendFailure(request.semanticIdentity(), exception.getMessage());
        } finally {
            if (auditStartNanos != 0L) {
                VulkanPerfAudit.recordPhase("backend.opengl.compute", System.nanoTime() - auditStartNanos);
            }
        }
    }
    
    @Override
    public boolean isBuffer(CommandContext ctx, int buffer) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.GL15.glIsBuffer(buffer);
    }
    
    @Override
    public boolean isEnabled(CommandContext ctx, int cap) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.GL11.glIsEnabled(cap);
    }

    @Override
    public boolean isEnabled(CommandContext ctx, VulkanicCapability capability) {
        return isEnabled(ctx, toOpenGLCapability(capability));
    }
    
    @Override
    public void texParameteriv(CommandContext ctx, int target, int pname, int[] params) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL11.glTexParameteriv(target, pname, params);
    }
    
    @Override
    public int getUniformBlockIndex(CommandContext ctx, int program, String uniformBlockName) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.GL31.glGetUniformBlockIndex(program, uniformBlockName);
    }
    
    @Override
    public int getMaxImageUnits(CommandContext ctx) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GLCapabilities caps = org.lwjgl.opengl.GL.getCapabilities();
        if (caps.OpenGL42 || caps.GL_ARB_shader_image_load_store) {
            return org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL42C.GL_MAX_IMAGE_UNITS);
        } else if (caps.GL_EXT_shader_image_load_store) {
            return org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.EXTShaderImageLoadStore.GL_MAX_IMAGE_UNITS_EXT);
        } else {
            return 0;
        }
    }
    
    @Override
    public void clearBufferSubData(CommandContext ctx, int target, int internalformat, long offset, long size, int format, int type, int[] data) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL43C.glClearBufferSubData(target, internalformat, offset, size, format, type, data);
    }
    
    @Override
    public void clearBufferfv(CommandContext ctx, int buffer, int drawbuffer, float[] values) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL32C.glClearBufferfv(buffer, drawbuffer, values);
    }
    
    @Override
    public void clearBufferiv(CommandContext ctx, int buffer, int drawbuffer, int[] values) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL32C.glClearBufferiv(buffer, drawbuffer, values);
    }
    
    @Override
    public void clearBufferuiv(CommandContext ctx, int buffer, int drawbuffer, int[] values) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL32C.glClearBufferuiv(buffer, drawbuffer, values);
    }

    // =========================================================================
    // Phase 3a: Buffer Lifecycle
    // =========================================================================

    @Override
    public net.vulkanic.VulkanicBuffer createManagedBuffer(java.util.function.Supplier<String> label, int usage, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Buffer size must be greater than zero, got: " + size);
        }
        CommandContext ctx = OpenGLCommandContext.IMMEDIATE;
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {
        }
        int handle = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, handle);
        if (hasBufferStorageExtension()) {
            int flags = 0;
            if ((usage & net.vulkanic.VulkanicBuffer.USAGE_MAP_READ) != 0) flags |= org.lwjgl.opengl.GL44.GL_MAP_READ_BIT;
            if ((usage & net.vulkanic.VulkanicBuffer.USAGE_MAP_WRITE) != 0) flags |= org.lwjgl.opengl.GL44.GL_MAP_WRITE_BIT | org.lwjgl.opengl.GL44.GL_DYNAMIC_STORAGE_BIT;
            bufferStorage(ctx, GL15.GL_ARRAY_BUFFER, (long) size, flags);
        } else {
            bufferData(ctx, GL15.GL_ARRAY_BUFFER, (long) size, GL15.GL_DYNAMIC_DRAW);
        }
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        int error = GL11.glGetError();
        if (error == org.lwjgl.opengl.GL11.GL_OUT_OF_MEMORY) {
            throw new net.blaze3d.GpuOutOfMemoryException("Could not allocate buffer of size " + size);
        } else if (error != 0) {
            throw new IllegalStateException("OpenGL error " + error + " while creating managed buffer");
        }
        return new OpenGLBuffer(handle, usage, size);
    }

    @Override
    public net.vulkanic.VulkanicBuffer createManagedBuffer(java.util.function.Supplier<String> label, int usage, java.nio.ByteBuffer initialData) {
        if (initialData == null || !initialData.hasRemaining()) {
            throw new IllegalArgumentException("initialData must be non-null and have remaining bytes");
        }
        int size = initialData.remaining();
        CommandContext ctx = OpenGLCommandContext.IMMEDIATE;
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {
        }
        int handle = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, handle);
        if (hasBufferStorageExtension()) {
            int flags = 0;
            if ((usage & net.vulkanic.VulkanicBuffer.USAGE_MAP_READ) != 0) flags |= org.lwjgl.opengl.GL44.GL_MAP_READ_BIT;
            if ((usage & net.vulkanic.VulkanicBuffer.USAGE_MAP_WRITE) != 0) flags |= org.lwjgl.opengl.GL44.GL_MAP_WRITE_BIT | org.lwjgl.opengl.GL44.GL_DYNAMIC_STORAGE_BIT;
            bufferStorage(ctx, GL15.GL_ARRAY_BUFFER, initialData, flags);
        } else {
            bufferData(ctx, GL15.GL_ARRAY_BUFFER, initialData, GL15.GL_DYNAMIC_DRAW);
        }
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        int error = GL11.glGetError();
        if (error == org.lwjgl.opengl.GL11.GL_OUT_OF_MEMORY) {
            throw new net.blaze3d.GpuOutOfMemoryException("Could not allocate buffer of size " + size);
        } else if (error != 0) {
            throw new IllegalStateException("OpenGL error " + error + " while creating managed buffer");
        }
        return new OpenGLBuffer(handle, usage, size);
    }

    @Override
    public net.vulkanic.VulkanicBuffer.MappedView mapManagedBuffer(net.vulkanic.VulkanicBuffer buffer, boolean read, boolean write) {
        if (!(buffer instanceof OpenGLBuffer openGLBuffer)) {
            throw new IllegalArgumentException("Expected OpenGLBuffer, got: " + buffer.getClass());
        }
        if (openGLBuffer.isClosed()) {
            throw new IllegalStateException("Cannot map a closed buffer");
        }
        int accessFlags = 0;
        if (read)  accessFlags |= org.lwjgl.opengl.GL30.GL_MAP_READ_BIT;
        if (write) accessFlags |= org.lwjgl.opengl.GL30.GL_MAP_WRITE_BIT;
        if (accessFlags == 0) {
            throw new IllegalArgumentException("At least one of read or write must be true");
        }
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {
        }
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, openGLBuffer.getGlHandle());
        java.nio.ByteBuffer mapped = org.lwjgl.opengl.GL30.glMapBufferRange(
            GL15.GL_ARRAY_BUFFER, 0, openGLBuffer.size(), accessFlags);
        if (mapped == null) {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            throw new IllegalStateException("glMapBufferRange returned null, GL error: " + GL11.glGetError());
        }
        int glHandle = openGLBuffer.getGlHandle();
        return new OpenGLBuffer.OpenGLMappedView(
            () -> {
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, glHandle);
                org.lwjgl.opengl.GL15.glUnmapBuffer(GL15.GL_ARRAY_BUFFER);
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            },
            mapped
        );
    }

    // =========================================================================
    // Phase 3b: Texture Lifecycle
    // =========================================================================

    @Override
    public net.vulkanic.VulkanicTexture createManagedTexture(String label, int usage,
            net.vulkanic.VulkanicTextureFormat format, int width, int height, int depthOrLayers, int mipLevels) {
        if (mipLevels < 1) throw new IllegalArgumentException("mipLevels must be at least 1");
        if (depthOrLayers < 1) throw new IllegalArgumentException("depthOrLayers must be at least 1");
        if (depthOrLayers > 1) throw new UnsupportedOperationException("Array/3D textures not yet supported in managed API");
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {
        }
        int handle = GL11.glGenTextures();
        if (label == null) label = String.valueOf(handle);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, handle);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL12.GL_TEXTURE_BASE_LEVEL, 0);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL12.GL_TEXTURE_MAX_LEVEL, mipLevels - 1);
        int[] internalFmt = toGlInternalFormat(format);
        int externalFmt = toGlExternalFormat(format);
        int glType = toGlType(format);
        if (format.hasStencilAspect() && supportsDepthStencilTextureMode()) {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, VulkanicAPI.GL_DEPTH_STENCIL_TEXTURE_MODE, VulkanicAPI.GL_DEPTH_COMPONENT);
        }
        for (int mip = 0; mip < mipLevels; mip++) {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, mip, internalFmt[0], Math.max(1, width >> mip),
                Math.max(1, height >> mip), 0, externalFmt, glType, (java.nio.ByteBuffer) null);
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        int error = GL11.glGetError();
        if (error == org.lwjgl.opengl.GL11.GL_OUT_OF_MEMORY) {
            throw new net.blaze3d.GpuOutOfMemoryException("Could not allocate texture " + width + "x" + height);
        } else if (error != 0) {
            throw new IllegalStateException("OpenGL error " + error + " while creating managed texture");
        }
        return new OpenGLTexture(handle, usage, format, width, height, depthOrLayers, mipLevels, label);
    }

    @Override
    public net.vulkanic.VulkanicTextureView createManagedTextureView(net.vulkanic.VulkanicTexture texture) {
        return createManagedTextureView(texture, 0, texture.getMipLevels());
    }

    @Override
    public net.vulkanic.VulkanicTextureView createManagedTextureView(net.vulkanic.VulkanicTexture texture,
            int baseMipLevel, int mipLevelCount) {
        if (!(texture instanceof OpenGLTexture) && !(texture instanceof net.blaze3d.opengl.GlTexture)) {
            throw new IllegalArgumentException("Expected OpenGLTexture or GlTexture, got: " + texture.getClass());
        }
        if (texture.isClosed()) {
            throw new IllegalArgumentException("Cannot create a view of a closed texture");
        }
        return new OpenGLTextureView(texture, baseMipLevel, mipLevelCount);
    }

    @Override
    @Nullable
    public net.vulkanic.VulkanicTextureView createManagedLegacyTextureView(int legacyTextureHandle) {
        if (legacyTextureHandle <= 0 || !GL11.glIsTexture(legacyTextureHandle)) {
            return null;
        }
        int width = GL45.glGetTextureLevelParameteri(legacyTextureHandle, 0, GL11.GL_TEXTURE_WIDTH);
        int height = GL45.glGetTextureLevelParameteri(legacyTextureHandle, 0, GL11.GL_TEXTURE_HEIGHT);
        int depth = GL45.glGetTextureLevelParameteri(legacyTextureHandle, 0, GL12.GL_TEXTURE_DEPTH);
        int internalFormat = GL45.glGetTextureLevelParameteri(legacyTextureHandle, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT);
        if (width <= 0 || height <= 0) {
            return null;
        }
        net.vulkanic.VulkanicTextureFormat format = legacyInternalFormatToVulkanic(internalFormat);
        if (format == null) {
            return null;
        }
        int mipLevels = 1;
        try {
            int immutableLevels = GL45.glGetTextureParameteri(legacyTextureHandle, GL43.GL_TEXTURE_IMMUTABLE_LEVELS);
            if (immutableLevels > 0) {
                mipLevels = immutableLevels;
            }
        } catch (RuntimeException ignored) {
        }
        OpenGLTexture texture = OpenGLTexture.nonOwning(
            legacyTextureHandle,
            net.vulkanic.VulkanicTexture.USAGE_TEXTURE_BINDING | net.vulkanic.VulkanicTexture.USAGE_RENDER_ATTACHMENT,
            format,
            width,
            height,
            Math.max(1, depth),
            Math.max(1, mipLevels),
            "Legacy_texture_" + legacyTextureHandle
        );
        return new OpenGLTextureView(texture, 0, 1);
    }

    @Nullable
    private static net.vulkanic.VulkanicTextureFormat legacyInternalFormatToVulkanic(int internalFormat) {
        return switch (internalFormat) {
            case VulkanicAPI.GL_RGBA8, GL11.GL_RGBA -> net.vulkanic.VulkanicTextureFormat.RGBA8;
            case VulkanicAPI.GL_RGBA8_SNORM -> net.vulkanic.VulkanicTextureFormat.RGBA8_SNORM;
            case VulkanicAPI.GL_RGBA16F -> net.vulkanic.VulkanicTextureFormat.RGBA16F;
            case VulkanicAPI.GL_R11F_G11F_B10F -> net.vulkanic.VulkanicTextureFormat.R11F_G11F_B10F;
            case GL30.GL_R8 -> net.vulkanic.VulkanicTextureFormat.RED8;
            case VulkanicAPI.GL_R8I -> net.vulkanic.VulkanicTextureFormat.RED8I;
            case VulkanicAPI.GL_R8UI -> net.vulkanic.VulkanicTextureFormat.RED8UI;
            case VulkanicAPI.GL_R16F -> net.vulkanic.VulkanicTextureFormat.RED16F;
            case VulkanicAPI.GL_R32F -> net.vulkanic.VulkanicTextureFormat.RED32F;
            default -> null;
        };
    }

    private static int[] toGlInternalFormat(net.vulkanic.VulkanicTextureFormat format) {
        return switch (format) {
            case RGBA8  -> new int[]{org.lwjgl.opengl.GL11.GL_RGBA8};
            case RGBA16F -> new int[]{net.vulkanic.VulkanicAPI.GL_RGBA16F};
            case RGBA8_SNORM -> new int[]{net.vulkanic.VulkanicAPI.GL_RGBA8_SNORM};
            case R11F_G11F_B10F -> new int[]{net.vulkanic.VulkanicAPI.GL_R11F_G11F_B10F};
            case RED16F -> new int[]{net.vulkanic.VulkanicAPI.GL_R16F};
            case RED32F -> new int[]{net.vulkanic.VulkanicAPI.GL_R32F};
            case BGRA8  -> new int[]{org.lwjgl.opengl.GL11.GL_RGBA8};
            case RED8   -> new int[]{org.lwjgl.opengl.GL30.GL_R8};
            case RED8I  -> new int[]{org.lwjgl.opengl.GL30.GL_R8I};
            case RED8UI -> new int[]{org.lwjgl.opengl.GL30.GL_R8UI};
            case DEPTH32 -> new int[]{org.lwjgl.opengl.GL14.GL_DEPTH_COMPONENT32};
            case DEPTH24_STENCIL8 -> new int[]{net.vulkanic.VulkanicAPI.GL_DEPTH24_STENCIL8};
            case DEPTH32F_STENCIL8 -> new int[]{net.vulkanic.VulkanicAPI.GL_DEPTH32F_STENCIL8};
        };
    }

    private static int toGlExternalFormat(net.vulkanic.VulkanicTextureFormat format) {
        return switch (format) {
            case RGBA8, RGBA16F, RGBA8_SNORM -> org.lwjgl.opengl.GL11.GL_RGBA;
            case R11F_G11F_B10F -> org.lwjgl.opengl.GL11.GL_RGB;
            case BGRA8  -> net.vulkanic.VulkanicAPI.GL_BGRA;
            case RED8, RED16F, RED32F -> org.lwjgl.opengl.GL30.GL_RED;
            case RED8I, RED8UI -> net.vulkanic.VulkanicAPI.GL_RED_INTEGER;
            case DEPTH32 -> org.lwjgl.opengl.GL11.GL_DEPTH_COMPONENT;
            case DEPTH24_STENCIL8, DEPTH32F_STENCIL8 -> net.vulkanic.VulkanicAPI.GL_DEPTH_STENCIL;
        };
    }

    private static int toGlType(net.vulkanic.VulkanicTextureFormat format) {
        return switch (format) {
            case RGBA8, BGRA8, RED8, RED8UI -> org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
            case RGBA16F      -> net.vulkanic.VulkanicAPI.GL_HALF_FLOAT;
            case RGBA8_SNORM  -> org.lwjgl.opengl.GL11.GL_BYTE;
            case R11F_G11F_B10F -> net.vulkanic.VulkanicAPI.GL_UNSIGNED_INT_10F_11F_11F_REV;
            case RED16F       -> net.vulkanic.VulkanicAPI.GL_HALF_FLOAT;
            case RED32F       -> org.lwjgl.opengl.GL11.GL_FLOAT;
            case RED8I        -> org.lwjgl.opengl.GL11.GL_BYTE;
            case DEPTH32      -> org.lwjgl.opengl.GL11.GL_FLOAT;
            case DEPTH24_STENCIL8 -> net.vulkanic.VulkanicAPI.GL_UNSIGNED_INT_24_8;
            case DEPTH32F_STENCIL8 -> net.vulkanic.VulkanicAPI.GL_FLOAT_32_UNSIGNED_INT_24_8_REV;
        };
    }

    // =========================================================================
    // Phase 3c: Pipeline Objects
    // =========================================================================

    @Override
    public net.vulkanic.VulkanicBuffer resolveVulkanicBuffer(net.blaze3d.buffers.GpuBuffer gpuBuffer) {
        if (!(gpuBuffer instanceof net.blaze3d.opengl.GlBuffer glBuffer)) {
            throw new IllegalArgumentException(
                "OpenGL backend resolveVulkanicBuffer requires GlBuffer, got: "
                    + gpuBuffer.getClass().getName());
        }
        return new OpenGLBuffer(glBuffer.getHandle(), glBuffer.usage(), glBuffer.size());
    }

    @Override
    public int resolveBufferHandle(net.vulkanic.CommandContext ctx, net.blaze3d.buffers.GpuBuffer gpuBuffer) {
        if (!(gpuBuffer instanceof net.blaze3d.opengl.GlBuffer glBuffer)) {
            return 0;
        }
        return glBuffer.getHandle();
    }

    @Override
    public net.vulkanic.PipelineHandle createPipeline(net.vulkanic.PipelineDescriptor descriptor) {
        if (glDevice == null) {
            throw new IllegalStateException(
                "GlDevice has not been registered with OpenGLBackend. " +
                "Ensure GlDevice calls VulkanicAPI.registerDevice() during initialization.");
        }
        net.blaze3d.pipeline.RenderPipeline renderPipeline = descriptor.requireRenderPipeline();
        net.blaze3d.opengl.GlRenderPipeline glPipeline = glDevice.precompilePipeline(renderPipeline, null);
        return new OpenGLPipelineHandle(glPipeline);
    }

    @Override
    public net.vulkanic.DescriptorPoolHandle createDescriptorPool(
        net.vulkanic.DescriptorPoolDescriptor descriptor
    ) {
        if (descriptor == null) {
            throw new IllegalArgumentException("descriptor must not be null");
        }
        return new OpenGLDescriptorPoolHandle(descriptor.maxSets());
    }

    @Override
    public net.vulkanic.DescriptorSetHandle allocateDescriptorSet(
        net.vulkanic.DescriptorPoolHandle pool,
        net.vulkanic.PipelineDescriptor descriptor
    ) {
        if (!(pool instanceof OpenGLDescriptorPoolHandle openGLPool)) {
            throw new IllegalArgumentException(
                "OpenGL backend requires OpenGLDescriptorPoolHandle, got: " +
                    (pool == null ? "null" : pool.getClass().getName()));
        }
        if (descriptor == null) {
            throw new IllegalArgumentException("descriptor must not be null");
        }

        return openGLPool.allocate(descriptor.getStableCacheKey(), descriptor.getResourceLayout());
    }

    @Override
    public void updateDescriptorSet(
        net.vulkanic.DescriptorSetHandle descriptorSet,
        net.vulkanic.PipelineResourceBindings bindings
    ) {
        if (!(descriptorSet instanceof OpenGLDescriptorSetHandle openGLDescriptorSet)) {
            throw new IllegalArgumentException(
                "OpenGL backend requires OpenGLDescriptorSetHandle, got: " +
                    (descriptorSet == null ? "null" : descriptorSet.getClass().getName()));
        }

        openGLDescriptorSet.updateBindings(bindings);
    }

    @Override
    public void bindDescriptorSet(
        CommandContext ctx,
        net.vulkanic.PipelineHandle pipeline,
        net.vulkanic.PipelineDescriptor descriptor,
        net.vulkanic.DescriptorSetHandle descriptorSet
    ) {
        if (!(descriptorSet instanceof OpenGLDescriptorSetHandle openGLDescriptorSet)) {
            throw new IllegalArgumentException(
                "OpenGL backend requires OpenGLDescriptorSetHandle, got: " +
                    (descriptorSet == null ? "null" : descriptorSet.getClass().getName()));
        }

        if (descriptor == null) {
            throw new IllegalArgumentException("descriptor must not be null");
        }

        String expectedLayoutKey = descriptor.getStableCacheKey();
        if (!expectedLayoutKey.equals(openGLDescriptorSet.layoutKey())) {
            throw new IllegalArgumentException(
                "Descriptor set layout key mismatch. Expected " + expectedLayoutKey +
                    " but got " + openGLDescriptorSet.layoutKey());
        }

        bindPipelineResources(ctx, pipeline, descriptor, openGLDescriptorSet.requireBindings());
    }

    @Override
    public void resetDescriptorPool(net.vulkanic.DescriptorPoolHandle pool) {
        if (!(pool instanceof OpenGLDescriptorPoolHandle openGLPool)) {
            throw new IllegalArgumentException(
                "OpenGL backend requires OpenGLDescriptorPoolHandle, got: " +
                    (pool == null ? "null" : pool.getClass().getName()));
        }
        openGLPool.reset();
    }

    @Override
    public void bindPipelineResources(
        CommandContext ctx,
        net.vulkanic.PipelineHandle pipeline,
        net.vulkanic.PipelineDescriptor descriptor,
        net.vulkanic.PipelineResourceBindings bindings
    ) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }

        if (!(pipeline instanceof OpenGLPipelineHandle glPipelineHandle)) {
            throw new IllegalArgumentException(
                "OpenGL backend requires OpenGLPipelineHandle, got: " +
                    (pipeline == null ? "null" : pipeline.getClass().getName()));
        }

        if (!glPipelineHandle.isValid()) {
            throw new IllegalArgumentException("Cannot bind resources for an invalid pipeline handle");
        }

        if (descriptor == null) {
            throw new IllegalArgumentException("descriptor must not be null");
        }

        if (bindings == null) {
            throw new IllegalArgumentException("bindings must not be null");
        }

        net.vulkanic.PipelineDescriptor.ResourceLayout layout = descriptor.getResourceLayout();
        bindings.validateAgainst(layout);
        net.vulkanic.VulkanicAPI.traceShaderInputParityResources("opengl-bindPipelineResources", pipeline, descriptor, bindings);

        int program = glPipelineHandle.getGlRenderPipeline().program().getProgramId();
        int previousActiveTextureUnit = activeTextureUnitIndex;

        try {
            for (net.vulkanic.PipelineDescriptor.ResourceBinding resource : layout.bindings()) {
                switch (resource.type()) {
                    case SAMPLER, COMPARISON_SAMPLER -> {
                        net.vulkanic.PipelineResourceBindings.SamplerBinding samplerBinding =
                            bindings.getSamplerBinding(resource.name())
                                .orElseThrow(() -> new IllegalStateException(
                                    "Missing sampler binding for '" + resource.name() + "' after validation"));

                        int location = getUniformLocation(ctx, program, resource.name());
                        if (location >= 0) {
                            setUniform1i(ctx, location, samplerBinding.textureUnit());
                        }

                        net.vulkanic.VulkanicTextureView textureView = samplerBinding.textureView();
                        if (textureView != null) {
                            if (!(textureView instanceof OpenGLTextureView openGLTextureView)) {
                                throw new IllegalArgumentException(
                                    "Sampler binding '" + resource.name() + "' must use OpenGLTextureView in OpenGL backend");
                            }
                            if (openGLTextureView.isClosed()) {
                                throw new IllegalStateException(
                                    "Sampler binding '" + resource.name() + "' uses a closed OpenGLTextureView");
                            }

                            setActiveTextureUnit(ctx, GL13.GL_TEXTURE0 + samplerBinding.textureUnit());
                            int textureHandle = openGLTextureView.glHandle();
                            net.vulkanic.VulkanicTexture texture = openGLTextureView.texture();
                            net.vulkanic.VulkanicTextureTarget textureTarget =
                                samplerBinding.resourceReference() != null
                                    && samplerBinding.resourceReference().legacyTarget().isPresent()
                                    ? net.vulkanic.VulkanicTextureTarget.fromLegacyGlTarget(
                                        samplerBinding.resourceReference().legacyTarget().getAsInt()
                                    ).orElse(net.vulkanic.VulkanicTextureTarget.TEXTURE_2D)
                                    : (texture.usage() & net.vulkanic.VulkanicTexture.USAGE_CUBEMAP_COMPATIBLE) != 0
                                        ? net.vulkanic.VulkanicTextureTarget.TEXTURE_CUBE_MAP
                                        : net.vulkanic.VulkanicTextureTarget.TEXTURE_2D;

                            bindTexture(ctx, textureTarget, textureHandle);
                            if (textureTarget == net.vulkanic.VulkanicTextureTarget.TEXTURE_2D
                                && samplerBinding.textureUnit() >= 0
                                && samplerBinding.textureUnit() < texture2DBindings.length) {
                                texture2DBindings[samplerBinding.textureUnit()] = textureHandle;
                            }

                            setTextureParameter(ctx, textureTarget,
                                net.vulkanic.VulkanicTextureParameterName.BASE_LEVEL,
                                openGLTextureView.getBaseMipLevel());
                            setTextureParameter(ctx, textureTarget,
                                net.vulkanic.VulkanicTextureParameterName.MAX_LEVEL,
                                openGLTextureView.getBaseMipLevel() + openGLTextureView.getMipLevelCount() - 1);

                            if (texture instanceof net.blaze3d.textures.GpuTexture gpuTexture) {
                                gpuTexture.flushModeChanges(textureTarget);
                            }
                        }

                        if (samplerBinding.samplerObject() != null) {
                            bindSampler(ctx, samplerBinding.textureUnit(), samplerBinding.samplerObject());
                        }
                    }
                    case UNIFORM_BUFFER -> {
                        net.vulkanic.VulkanicBufferSlice slice = bindings.getUniformBufferBinding(resource.name())
                            .orElseThrow(() -> new IllegalStateException(
                                "Missing uniform-buffer binding for '" + resource.name() + "' after validation"));

                        if (!(slice.buffer() instanceof OpenGLBuffer glBuffer)) {
                            throw new IllegalArgumentException(
                                "Uniform-buffer binding '" + resource.name() + "' must use OpenGLBuffer in OpenGL backend");
                        }

                        int uniformBlockIndex = getUniformBlockIndex(ctx, program, resource.name());
                        if (uniformBlockIndex >= 0) {
                            uniformBlockBinding(ctx, program, uniformBlockIndex, resource.binding());
                        }

                        bindUniformBufferRange(
                            ctx,
                            VulkanicAPI.GL_UNIFORM_BUFFER,
                            resource.binding(),
                            glBuffer.getGlHandle(),
                            slice.offset(),
                            slice.length());
                    }
                    case STORAGE_IMAGE -> {
                        net.vulkanic.PipelineResourceBindings.StorageImageBinding imageBinding =
                            bindings.getStorageImageBinding(resource.name())
                                .orElseThrow(() -> new IllegalStateException(
                                    "Missing storage-image binding for '" + resource.name() + "' after validation"));

                        int location = getUniformLocation(ctx, program, resource.name());
                        if (location >= 0) {
                            setUniform1i(ctx, location, imageBinding.imageUnit());
                        }
                        bindImageTexture(
                            ctx,
                            imageBinding.imageUnit(),
                            imageBinding.texture(),
                            imageBinding.level(),
                            imageBinding.layered(),
                            imageBinding.layer(),
                            imageBinding.access(),
                            imageBinding.format()
                        );
                    }
                    case TEXEL_BUFFER -> {
                        net.vulkanic.PipelineResourceBindings.TexelBufferBinding texelBufferBinding =
                            bindings.getTexelBufferBinding(resource.name())
                                .orElseThrow(() -> new IllegalStateException(
                                    "Missing texel-buffer binding for '" + resource.name() + "' after validation"));

                        int location = getUniformLocation(ctx, program, resource.name());
                        if (location >= 0) {
                            setUniform1i(ctx, location, texelBufferBinding.textureUnit());
                        }
                    }
                }
            }
        } finally {
            restoreActiveTextureUnit(previousActiveTextureUnit);
        }
    }

    private void restoreActiveTextureUnit(int unitIndex) {
        if (unitIndex >= 0 && unitIndex < texture2DBindings.length) {
            activeTextureUnitIndex = unitIndex;
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unitIndex);
        }
    }

    // =========================================================================
    // Phase 3d: Command Buffer Lifecycle
    // =========================================================================

    @Override
    public CommandContext beginCommandBuffer() {
        // OpenGL is immediate-mode: return the singleton immediate context.
        return OpenGLCommandContext.IMMEDIATE;
    }

    @Override
    public void submitCommandBuffer(CommandContext ctx) {
        // OpenGL is immediate-mode: commands already executed; nothing to submit.
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException(
                "OpenGL backend only supports immediate-mode contexts; got: " + ctx);
        }
    }

    // =========================================================================
    // Phase 3b: Render Pass
    // =========================================================================

    @Override
    public net.vulkanic.VulkanicRenderPass executeRenderPassBegin(
        CommandContext ctx,
        VulkanicGalExecutionRequest.RenderPassBeginRequest request
    ) {
        invalidateAppliedFramebufferState();
        consumeResourceUsagePlan(request.resourcePlan());
        return switch (request.kind()) {
            case DESCRIPTOR -> beginRenderPass(ctx, request.descriptor().orElseThrow());
            case TARGET_DESCRIPTOR -> beginRenderPass(ctx, request.targetDescriptor().orElseThrow());
            case FRAMEBUFFER -> beginRenderPass(
                ctx,
                () -> request.label(),
                request.framebuffer().orElseThrow(),
                request.hasDepthTexture()
            );
        };
    }

    private OpenGLResourceUsageExecutionPlanner.ExecutionPlan consumeResourceUsagePlan(
        net.vulkanic.VulkanicPassResourceModel.PassExecutionPlan resourcePlan
    ) {
        return OpenGLResourceUsageExecutionPlanner.plan(resourcePlan);
    }

    @Override
    public net.vulkanic.VulkanicRenderPass beginRenderPass(CommandContext ctx,
            java.util.function.Supplier<String> label,
            net.vulkanic.VulkanicTextureView colorTarget, java.util.OptionalInt clearColor) {
        return beginRenderPass(ctx, label, colorTarget, clearColor, null, java.util.OptionalDouble.empty());
    }

    @Override
    public net.vulkanic.VulkanicRenderPass beginRenderPass(CommandContext ctx,
            java.util.function.Supplier<String> label,
            net.vulkanic.VulkanicTextureView colorTarget, java.util.OptionalInt clearColor,
            @org.jetbrains.annotations.Nullable net.vulkanic.VulkanicTextureView depthTarget,
            java.util.OptionalDouble clearDepth) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException(
                "OpenGL backend requires immediate-mode CommandContext for beginRenderPass");
        }
        if (colorTarget == null) {
            throw new IllegalArgumentException("colorTarget must not be null");
        }
        if (!(colorTarget instanceof OpenGLTextureView colorView)) {
            throw new IllegalArgumentException(
                "OpenGL backend requires OpenGLTextureView for colorTarget, got: " +
                colorTarget.getClass().getName());
        }
        if (depthTarget != null && !(depthTarget instanceof OpenGLTextureView)) {
            throw new IllegalArgumentException(
                "OpenGL backend requires OpenGLTextureView for depthTarget, got: " +
                depthTarget.getClass().getName());
        }

        // 1. Create a new FBO for this render pass
        int fbo = net.vulkanic.VulkanicAPI.createFramebuffer(ctx);

        // 2. Bind the FBO
        net.vulkanic.VulkanicAPI.bindFramebuffer(ctx, fbo);

        // 3. Attach the color texture at mip level 0 of the view's base mip
        int colorHandle = colorView.glHandle();
        int colorMip = colorView.getBaseMipLevel();
        net.vulkanic.VulkanicAPI.framebufferTexture(ctx,
            net.vulkanic.VulkanicAPI.GL_FRAMEBUFFER,
            net.vulkanic.VulkanicAPI.GL_COLOR_ATTACHMENT0,
            net.vulkanic.VulkanicAPI.GL_TEXTURE_2D,
            colorHandle, colorMip);
        net.vulkanic.VulkanicAPI.setDrawBufferColorAttachment0(ctx);
        net.vulkanic.VulkanicAPI.setReadBufferColorAttachment(ctx, 0);

        // 4. Attach the depth texture if provided
        if (depthTarget != null) {
            OpenGLTextureView depthView = (OpenGLTextureView) depthTarget;
            int depthHandle = depthView.glHandle();
            int depthMip = depthView.getBaseMipLevel();
            int depthAttachment = depthView.texture().getVulkanicFormat().hasStencilAspect()
                ? net.vulkanic.VulkanicAPI.GL_DEPTH_STENCIL_ATTACHMENT
                : net.vulkanic.VulkanicAPI.GL_DEPTH_ATTACHMENT;
            net.vulkanic.VulkanicAPI.framebufferTexture(ctx,
                net.vulkanic.VulkanicAPI.GL_FRAMEBUFFER,
                depthAttachment,
                net.vulkanic.VulkanicAPI.GL_TEXTURE_2D,
                depthHandle, depthMip);
        }

        // 5. Optionally clear color
        boolean shouldClearColor = false;
        if (clearColor.isPresent()) {
            int argb = clearColor.getAsInt();
            float a = ((argb >> 24) & 0xFF) / 255.0f;
            float r = ((argb >> 16) & 0xFF) / 255.0f;
            float g = ((argb >>  8) & 0xFF) / 255.0f;
            float b = ( argb        & 0xFF) / 255.0f;
            net.vulkanic.VulkanicAPI.setClearColor(ctx, r, g, b, a);
            shouldClearColor = true;
        }

        // 6. Optionally clear depth
        boolean shouldClearDepth = false;
        if (depthTarget != null && clearDepth.isPresent()) {
            net.vulkanic.VulkanicAPI.setClearDepth(ctx, clearDepth.getAsDouble());
            shouldClearDepth = true;
        }

        if (shouldClearColor && shouldClearDepth) {
            net.vulkanic.VulkanicAPI.clearBuffers(ctx, VulkanicClearBuffer.COLOR, VulkanicClearBuffer.DEPTH);
        } else if (shouldClearColor) {
            net.vulkanic.VulkanicAPI.clearBuffers(ctx, VulkanicClearBuffer.COLOR);
        } else if (shouldClearDepth) {
            net.vulkanic.VulkanicAPI.clearBuffers(ctx, VulkanicClearBuffer.DEPTH);
        }

        // 7. Set the viewport to the color attachment's dimensions
        int width  = colorTarget.getWidth(0);
        int height = colorTarget.getHeight(0);
        net.vulkanic.VulkanicAPI.setDynamicViewport(ctx, 0, 0, width, height);

        return new OpenGLRenderPass(fbo, ctx);
    }

    @Override
    public net.vulkanic.VulkanicRenderPass beginRenderPass(
        CommandContext ctx,
        net.vulkanic.VulkanicRenderPassDescriptor descriptor
    ) {
        if (descriptor == null) {
            throw new IllegalArgumentException("descriptor must not be null");
        }

        net.vulkanic.VulkanicRenderPassDescriptor.ColorAttachment color = descriptor.colorAttachment();
        net.vulkanic.VulkanicRenderPassDescriptor.DepthAttachment depth = descriptor.depthAttachment();

        java.util.OptionalInt clearColor =
            color.loadOp() == net.vulkanic.VulkanicRenderPassDescriptor.LoadOp.CLEAR
                ? color.clearColor()
                : java.util.OptionalInt.empty();

        net.vulkanic.VulkanicTextureView depthTarget = null;
        java.util.OptionalDouble clearDepth = java.util.OptionalDouble.empty();
        if (depth != null) {
            depthTarget = depth.target();
            if (depth.loadOp() == net.vulkanic.VulkanicRenderPassDescriptor.LoadOp.CLEAR) {
                clearDepth = depth.clearDepth();
            }
        }

        return beginRenderPass(
            ctx,
            descriptor.label(),
            color.target(),
            clearColor,
            depthTarget,
            clearDepth
        );
    }
}

package net.vulkanic.backends.vulkan;

import net.blaze3d.GpuOutOfMemoryException;
import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.pipeline.CompiledRenderPipeline;
import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.platform.PolygonMode;
import net.blaze3d.preprocessor.GlslPreprocessor;
import net.blaze3d.shaders.ShaderType;
import net.blaze3d.systems.CommandEncoder;
import net.blaze3d.systems.GpuDevice;
import net.blaze3d.textures.GpuTexture;
import net.blaze3d.textures.GpuTextureView;
import net.blaze3d.textures.TextureFormat;
import net.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.sodium.client.render.chunk.shader.SharedChunkProgramOverrides;
import net.vulkanic.CommandContext;
import net.vulkanic.GraphicsBackendType;
import net.vulkanic.PipelineDescriptor;
import net.vulkanic.PipelineHandle;
import net.vulkanic.PipelineResourceBindings;
import net.vulkanic.PipelineResourcePlanner;
import net.vulkanic.VulkanReadinessReport;
import net.vulkanic.VulkanicBufferSlice;
import net.vulkanic.VulkanicBuffer;
import net.vulkanic.VulkanicIndexType;
import net.vulkanic.VulkanicPrimitiveMode;
import net.vulkanic.VulkanicRenderPass;
import net.vulkanic.VulkanicRenderPassDescriptor;
import net.vulkanic.VulkanicRenderTargetDescriptor;
import net.vulkanic.VulkanicResourceUsage;
import net.vulkanic.VulkanicTexture;
import net.vulkanic.VulkanicTextureFormat;
import net.vulkanic.VulkanicTextureUploadFormat;
import net.vulkanic.VulkanicTextureView;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanPerfAudit;
import net.vulkanic.VulkanicShaderStage;
import net.vulkanic.VulkanicSpirvModule;
import net.vulkanic.VulkanExecutionContextInfo;
import net.vulkanic.VulkanNativeInitializationInfo;
import net.vulkanic.VulkanSwapchainSurfaceInfo;
import net.vulkanic.VulkanicBufferTarget;
import net.vulkanic.VulkanicResourceBarriers;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.KHRGetPhysicalDeviceProperties2;
import org.lwjgl.vulkan.KHRPresentId;
import org.lwjgl.vulkan.KHRPresentWait;
import org.lwjgl.vulkan.KHRSurface;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK11;
import org.lwjgl.vulkan.VkAttachmentDescription;
import org.lwjgl.vulkan.VkAttachmentReference;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkBufferViewCreateInfo;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkClearColorValue;
import org.lwjgl.vulkan.VkClearDepthStencilValue;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkExtensionProperties;
import org.lwjgl.vulkan.VkImageBlit;
import org.lwjgl.vulkan.VkImageCopy;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageSubresourceRange;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkExtent2D;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryBarrier;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.vulkan.EXTAttachmentFeedbackLoopLayout;
import org.lwjgl.vulkan.VkPhysicalDeviceAttachmentFeedbackLoopLayoutFeaturesEXT;
import org.lwjgl.vulkan.VkPhysicalDevicePresentIdFeaturesKHR;
import org.lwjgl.vulkan.VkPhysicalDevicePresentWaitFeaturesKHR;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.lwjgl.vulkan.VkPresentIdKHR;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkQueueFamilyProperties;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;
import org.lwjgl.vulkan.VkRenderPassCreateInfo;
import org.lwjgl.vulkan.VkSamplerCreateInfo;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkSubpassDependency;
import org.lwjgl.vulkan.VkSubpassDescription;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkStencilOpState;
import org.lwjgl.vulkan.VkViewport;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import net.blaze3d.platform.DestFactor;
import net.blaze3d.platform.PolygonMode;
import net.blaze3d.platform.SourceFactor;
import net.blaze3d.vertex.VertexFormat;
import net.blaze3d.vertex.VertexFormatElement;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.nio.ByteOrder;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.lwjgl.system.MemoryStack.stackPush;
import org.slf4j.Logger;

public class VulkanBackend {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> STANDALONE_SLICE_TRACE_KEYS = ConcurrentHashMap.newKeySet();
    private static final AtomicLong STANDALONE_UNIFORM_CALL_COUNT = new AtomicLong();
    private static final AtomicLong STANDALONE_UNIFORM_TOKEN_HIT_COUNT = new AtomicLong();
    private static final AtomicLong STANDALONE_UNIFORM_FALLBACK_COUNT = new AtomicLong();
    private static final AtomicLong STANDALONE_UNIFORM_WRITE_COUNT = new AtomicLong();
    private static final AtomicInteger STANDALONE_UNIFORM_STATS_LOG_COUNT = new AtomicInteger();
    private static final AtomicInteger STANDALONE_SLICE_TRACE_LOG_COUNT = new AtomicInteger();
    private static final AtomicInteger STANDALONE_LOOKUP_SAMPLE_PROGRAM = new AtomicInteger(-1);
    private static final int MAX_STANDALONE_SLICE_TRACE_LOGS = Integer.getInteger("mattmc.vulkan.traceStandaloneUniforms.maxLogs", 512);
    private static final boolean TRACE_PIPELINE_CREATION = Boolean.getBoolean("mattmc.vulkan.tracePipelineCreation");
    private static final boolean TRACE_RENDER_TARGET_PARITY = Boolean.getBoolean("mattmc.vulkan.traceRenderTargetParity");
    private static final int MAX_RENDER_TARGET_PARITY_LOGS = Integer.getInteger("mattmc.vulkan.traceRenderTargetParity.maxLogs", 160);
    private static final AtomicInteger RENDER_TARGET_PARITY_LOG_COUNT = new AtomicInteger();
    private static final java.util.concurrent.atomic.AtomicLong glslDumpCounter = new java.util.concurrent.atomic.AtomicLong(0);
    private static final Set<Integer> LEGACY_SAMPLER_UNSUPPORTED_FORMAT_LOGS = ConcurrentHashMap.newKeySet();

    private static final int GL_LUMINANCE = 0x1909;
    private static final int GL_LUMINANCE_ALPHA = 0x190A;
    private static final Pattern GLSL_BLOCK_COMMENT_PATTERN = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern GLSL_LINE_COMMENT_PATTERN = Pattern.compile("(?m)//.*$");
    private static final Pattern GLSL_UNIFORM_BLOCK_PATTERN = Pattern.compile("(?m)(?:layout\\s*\\(([^)]*)\\)\\s*)?uniform\\s+(\\w+)\\s*\\{");
    private static final Pattern GLSL_STANDALONE_UNIFORM_PATTERN = Pattern.compile(
        "(?m)^\\s*(?:layout\\s*\\(([^)]*)\\)\\s*)?(?:lowp\\s+|mediump\\s+|highp\\s+)?uniform\\s+(\\w+)\\s+(\\w+)(?:\\s*\\[\\s*(\\d+)\\s*\\])?(?:\\s*=\\s*[^;]+)?\\s*;"
    );
    private static final Pattern GLSL_LAYOUT_SET_PATTERN = Pattern.compile("\\bset\\s*=\\s*(\\d+)\\b");
    private static final Pattern GLSL_LAYOUT_BINDING_PATTERN = Pattern.compile("\\bbinding\\s*=\\s*(\\d+)\\b");
    private static final Pattern GLSL_STANDALONE_UNIFORM_MEMBER_PATTERN = Pattern.compile(
        "^\\s*(\\w+)\\s+(\\w+)(?:\\s*\\[\\s*(\\d+)\\s*\\])?\\s*;\\s*$"
    );
    private static final int GL_FRAMEBUFFER = 0x8D40;
    private static final int GL_READ_FRAMEBUFFER = 0x8CA8;
    private static final int GL_DRAW_FRAMEBUFFER = 0x8CA9;
    private static final int GL_COLOR_ATTACHMENT0 = 0x8CE0;
    private static final int GL_DEPTH_ATTACHMENT = 0x8D00;
    private static final int GL_DEPTH_STENCIL_ATTACHMENT = 0x821A;
    private static final int GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE = 0x8CD0;
    private static final int GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME = 0x8CD1;
    private static final int GL_NONE = 0;
    private static final int GL_TEXTURE = 0x1702;
    private static final int GL_TEXTURE_COMPARE_FUNC = 0x884D;
    private static final int GL_COLOR_BUFFER_BIT = 0x00004000;
    private static final int GL_DEPTH_BUFFER_BIT = 0x00000100;
    private static final int GL_STENCIL_BUFFER_BIT = 0x00000400;
    private static final int GL_COLOR = 0x1800;
    private static final int GL_DEPTH = 0x1801;
    private static final int GL_STENCIL = 0x1802;
    private static final int GL_NEAREST = 0x2600;

    private final Object nativeInitLock = new Object();
    private volatile NativeSpine nativeSpine;
    private volatile boolean nativeBringUpAttempted;
    private volatile String nativeBringUpFailure;

    private volatile VulkanReadinessReport cachedReadinessReport;
    private volatile CommandContext currentCommandContext;
    private volatile long auxiliaryOpenGlContextWindow = MemoryUtil.NULL;
    private volatile net.blaze3d.opengl.GlDevice compatibilityDevice;
    private volatile boolean rendererDebuggingEnabled;

    private final SpirvCompiler spirvCompiler;
    private final Map<RenderPipeline, PrecompiledPipelineState> precompiledPipelineCache = new ConcurrentHashMap<>();
    private final AtomicInteger nextVirtualShaderId = new AtomicInteger(1);
    private final AtomicInteger nextVirtualProgramId = new AtomicInteger(1);
    private final AtomicInteger nextVirtualUniformLocationToken = new AtomicInteger(1);
    private final AtomicInteger presentQueueLogCount = new AtomicInteger();
    private static final AtomicInteger PRESENT_FORMAT_MISMATCH_LOG_COUNT = new AtomicInteger();
    private final Map<Integer, VirtualShader> virtualShaders = new ConcurrentHashMap<>();
    private final Map<Integer, VirtualProgram> virtualPrograms = new ConcurrentHashMap<>();
    private final Map<Integer, UniformLocationRef> uniformLocationRefs = new ConcurrentHashMap<>();
    private final Map<Long, BoundPipelineResources> boundPipelineResourcesByCommandBuffer = new ConcurrentHashMap<>();

    // -----------------------------------------------------------------------
    // Deferred render state (pipeline-baked in Vulkan; cached here so callers
    // that set state before/between draw calls are not broken at the API level)
    // -----------------------------------------------------------------------
    private volatile boolean pendingBlendEnabled = false;
    private volatile int  pendingBlendSrcRgb   = 1 /* GL_ONE */;
    private volatile int  pendingBlendDstRgb   = 0 /* GL_ZERO */;
    private volatile int  pendingBlendSrcAlpha = 1 /* GL_ONE */;
    private volatile int  pendingBlendDstAlpha = 0 /* GL_ZERO */;
    private volatile int  pendingBlendEquation  = 0x8006 /* GL_FUNC_ADD */;
    private volatile int  pendingBlendEquationAlpha = 0x8006 /* GL_FUNC_ADD */;
    private final ConcurrentHashMap<Integer, IndexedBlendState> indexedBlendStates = new ConcurrentHashMap<>();

    private volatile boolean pendingDepthTestEnabled  = false;
    private volatile int     pendingDepthFunc         = 0x0201 /* GL_LESS */;
    private volatile boolean pendingDepthWriteMask    = true;
    private volatile boolean pendingCullFaceEnabled   = false;

    private volatile boolean pendingColorMaskR = true;
    private volatile boolean pendingColorMaskG = true;
    private volatile boolean pendingColorMaskB = true;
    private volatile boolean pendingColorMaskA = true;

    private volatile int pendingCullFaceMode = 0x0405 /* GL_BACK */;

    private volatile int pendingPolygonFace = 0x0408 /* GL_FRONT_AND_BACK */;
    private volatile int pendingPolygonMode = 0x1B02 /* GL_FILL */;
    private volatile float pendingPolygonOffsetFactor = 0.0f;
    private volatile float pendingPolygonOffsetUnits  = 0.0f;

    private volatile float pendingClearR = 0.0f;
    private volatile float pendingClearG = 0.0f;
    private volatile float pendingClearB = 0.0f;
    private volatile float pendingClearA = 0.0f;
    private volatile double pendingClearDepth = 1.0;

    private volatile int pendingLogicOp = 0x1503 /* GL_COPY */;
    private volatile int pendingReadBuffer  = 0x0405 /* GL_BACK */;
    private volatile int pendingDrawBuffer  = 0x0405 /* GL_BACK */;

    // Virtual FBO tracking (mirrors legacy-buffer pattern for GL compat calls)
    private final AtomicInteger nextVirtualFboId = new AtomicInteger(1);
    private final Set<Integer>  virtualFbos      = ConcurrentHashMap.newKeySet();
    private final Map<Integer, VirtualFramebufferState> virtualFramebufferStates = new ConcurrentHashMap<>();
    private final Map<FramebufferTexturePairKey, Integer> implicitFramebufferByTexturePair = new ConcurrentHashMap<>();
    private volatile int        boundReadFbo     = 0;
    private volatile int        boundDrawFbo     = 0;

    // Virtual VAO tracking (Vulkan has no VAO concept; virtual IDs satisfy GL compat calls)
    private final AtomicInteger nextVirtualVaoId     = new AtomicInteger(1);
    private final Set<Integer>  virtualVaos          = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<Integer, VirtualVaoState> virtualVaoStates = new ConcurrentHashMap<>();

    // Virtual sampler tracking (Vulkan samplers created at pipeline init; virtual IDs for GL compat)
    private final AtomicInteger nextVirtualSamplerId = new AtomicInteger(1);
    private final Set<Integer>  virtualSamplers      = ConcurrentHashMap.newKeySet();
    /** Per-sampler-object state cache mirroring GL sampler parameters. */
    private final ConcurrentHashMap<Integer, VirtualSamplerState> virtualSamplerStates = new ConcurrentHashMap<>();
    /** Per-unit sampler binding cache: texture-unit → bound sampler handle */
    private final ConcurrentHashMap<Integer, Integer> boundSamplerPerUnit = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<DescriptorPipelineKey, PipelineHandle> descriptorPipelineCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<RenderTargetPipelineKey, PipelineHandle> renderTargetPipelineCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<LegacyProgramPipelineKey, PipelineHandle> legacyProgramPipelineCache = new ConcurrentHashMap<>();

    // Virtual query/sync tracking for GL-compat control flow on the Vulkan path
    private final AtomicInteger nextVirtualQueryId = new AtomicInteger(1);
    private final Set<Integer> virtualQueries      = ConcurrentHashMap.newKeySet();
    private final AtomicLong nextVirtualSyncId     = new AtomicLong(1L);
    private final Set<Long> virtualSyncs           = ConcurrentHashMap.newKeySet();

    // Lightweight bound-object mirrors for integer state queries
    private volatile int boundVirtualProgram = 0;
    private volatile int boundVirtualVao     = 0;

    // Cached backend capabilities object for non-OpenGL callers that still query capabilities.
    private final net.vulkanic.GraphicsCapabilities graphicsCapabilities = createVulkanGraphicsCapabilities();

    // Deferred stencil state (pipeline-baked in Vulkan — cached here for pipeline construction)
    private volatile boolean pendingStencilTestEnabled = false;
    private volatile int    pendingStencilFunc       = 0x0207 /* GL_ALWAYS */;
    private volatile int    pendingStencilRef        = 0;
    private volatile int    pendingStencilMask       = 0xFF;
    private volatile int    pendingStencilFail       = 0x1E00 /* GL_KEEP */;
    private volatile int    pendingStencilDpFail     = 0x1E00 /* GL_KEEP */;
    private volatile int    pendingStencilDpPass     = 0x1E00 /* GL_KEEP */;
    private volatile int    pendingStencilWriteMask  = 0xFF;
    private volatile int    pendingBackStencilFunc       = 0x0207 /* GL_ALWAYS */;
    private volatile int    pendingBackStencilRef        = 0;
    private volatile int    pendingBackStencilMask       = 0xFF;
    private volatile int    pendingBackStencilFail       = 0x1E00 /* GL_KEEP */;
    private volatile int    pendingBackStencilDpFail     = 0x1E00 /* GL_KEEP */;
    private volatile int    pendingBackStencilDpPass     = 0x1E00 /* GL_KEEP */;
    private volatile int    pendingBackStencilWriteMask  = 0xFF;

    public VulkanBackend() {
        this(new GlslangSpirvCompiler());
    }

    VulkanBackend(SpirvCompiler spirvCompiler) {
        this.spirvCompiler = Objects.requireNonNull(spirvCompiler, "spirvCompiler must not be null");
    }

    private static final class PrecompiledPipelineState implements CompiledRenderPipeline {
        private final PipelineHandle pipelineHandle;
        private final PipelineDescriptor descriptor;
        private final String stableCacheKey;
        private final String resourceLayoutKey;
        private final boolean valid;

        private PrecompiledPipelineState(
            PipelineHandle pipelineHandle,
            PipelineDescriptor descriptor,
            String stableCacheKey,
            String resourceLayoutKey,
            boolean valid
        ) {
            this.pipelineHandle = pipelineHandle;
            this.descriptor = descriptor;
            this.stableCacheKey = stableCacheKey;
            this.resourceLayoutKey = resourceLayoutKey;
            this.valid = valid;
        }

        static PrecompiledPipelineState successful(
            PipelineHandle pipelineHandle,
            PipelineDescriptor descriptor,
            String stableCacheKey,
            String resourceLayoutKey
        ) {
            return new PrecompiledPipelineState(pipelineHandle, descriptor, stableCacheKey, resourceLayoutKey, true);
        }

        static PrecompiledPipelineState failed() {
            return new PrecompiledPipelineState(null, null, null, null, false);
        }

        @Override
        public boolean isValid() {
            return valid && pipelineHandle != null && pipelineHandle.isValid();
        }

        boolean matchesDescriptor(@Nullable PipelineDescriptor descriptor) {
            return descriptor != null
                && stableCacheKey != null
                && resourceLayoutKey != null
                && stableCacheKey.equals(descriptor.getStableCacheKey())
                && resourceLayoutKey.equals(descriptor.getResourceLayoutCacheKey());
        }

        void closeIfNeeded() {
            if (pipelineHandle != null) {
                pipelineHandle.close();
            }
        }
    }

    private record RenderTargetPipelineKey(
        String stableCacheKey,
        String resourceLayoutKey,
        String dynamicStateKey,
        VulkanRenderPassCompatibilityKey renderPassCompatibilityKey
    ) {
        private RenderTargetPipelineKey {
            Objects.requireNonNull(renderPassCompatibilityKey, "renderPassCompatibilityKey must not be null");
        }

        private static RenderTargetPipelineKey from(
            PipelineDescriptor descriptor,
            ResolvedFramebufferTargets targets,
            String dynamicStateKey
        ) {
            return from(
                descriptor,
                VulkanRenderPassCompatibilityKey.framebuffer(
                    targets.colorFormats(),
                    targets.hasDepthTarget() ? targets.depthTexture.vkFormat : VK10.VK_FORMAT_UNDEFINED,
                    targets.hasFeedbackLoopTarget()
                ),
                dynamicStateKey
            );
        }

        private static RenderTargetPipelineKey from(
            PipelineDescriptor descriptor,
            VulkanRenderPassCompatibilityKey renderPassCompatibilityKey,
            String dynamicStateKey
        ) {
            return new RenderTargetPipelineKey(
                descriptor.getStableCacheKey(),
                descriptor.getResourceLayoutCacheKey(),
                dynamicStateKey,
                renderPassCompatibilityKey
            );
        }
    }

    private record DescriptorPipelineKey(
        String stableCacheKey,
        String resourceLayoutKey
    ) {
    }

    private record LegacyProgramPipelineKey(
        int programId,
        int framebuffer,
        String pipelineCompilationKey,
        String resourceLayoutKey,
        VulkanRenderPassCompatibilityKey renderPassCompatibilityKey
    ) {
        private LegacyProgramPipelineKey {
            Objects.requireNonNull(pipelineCompilationKey, "pipelineCompilationKey must not be null");
            Objects.requireNonNull(resourceLayoutKey, "resourceLayoutKey must not be null");
            Objects.requireNonNull(renderPassCompatibilityKey, "renderPassCompatibilityKey must not be null");
        }
    }

    private record IndexedBlendState(
        boolean enabled,
        int srcRgb,
        int dstRgb,
        int srcAlpha,
        int dstAlpha
    ) {
        private String cacheKey() {
            return enabled
                + ":" + srcRgb
                + ":" + dstRgb
                + ":" + srcAlpha
                + ":" + dstAlpha;
        }
    }

    private record FramebufferTexturePairKey(
        int colorTexture,
        int depthTexture
    ) {
    }

    private static final class VirtualFramebufferState {
        private final Map<Integer, Integer> attachments = new ConcurrentHashMap<>();
        private volatile int readBuffer = GL_COLOR_ATTACHMENT0;
        private volatile int[] drawBuffers = new int[]{GL_COLOR_ATTACHMENT0};

        void setAttachment(int attachment, int texture) {
            if (texture == 0) {
                attachments.remove(attachment);
            } else {
                attachments.put(attachment, texture);
            }
        }

        int getAttachment(int attachment) {
            return attachments.getOrDefault(attachment, 0);
        }

        int[] getDrawBuffers() {
            return drawBuffers.clone();
        }

        int getPrimaryDrawBuffer() {
            return drawBuffers.length == 0 ? GL_NONE : drawBuffers[0];
        }

        void setDrawBuffers(int[] buffers) {
            if (buffers == null || buffers.length == 0) {
                this.drawBuffers = new int[]{GL_NONE};
                return;
            }

            this.drawBuffers = buffers.clone();
        }
    }

    private static final class VirtualSamplerState {
        private volatile int minFilter = VulkanicAPI.GL_NEAREST_MIPMAP_LINEAR;
        private volatile int magFilter = VulkanicAPI.GL_LINEAR;
        private volatile int wrapS = VulkanicAPI.GL_REPEAT;
        private volatile int wrapT = VulkanicAPI.GL_REPEAT;
        private volatile int wrapR = VulkanicAPI.GL_REPEAT;
        private volatile int compareMode = 0;
        private volatile int compareFunc = VulkanicAPI.GL_LEQUAL;
        private volatile float maxLod = 1000.0f;

        private void setParameteri(int pname, int param) {
            switch (pname) {
                case VulkanicAPI.GL_TEXTURE_MIN_FILTER -> minFilter = param;
                case VulkanicAPI.GL_TEXTURE_MAG_FILTER -> magFilter = param;
                case VulkanicAPI.GL_TEXTURE_WRAP_S -> wrapS = param;
                case VulkanicAPI.GL_TEXTURE_WRAP_T -> wrapT = param;
                case VulkanicAPI.GL_TEXTURE_WRAP_R -> wrapR = param;
                case VulkanicAPI.GL_TEXTURE_COMPARE_MODE -> compareMode = param;
                case VulkanBackend.GL_TEXTURE_COMPARE_FUNC -> compareFunc = param;
                case VulkanicAPI.GL_TEXTURE_MAX_LOD -> maxLod = param;
                default -> {
                    // Keep compatibility behavior for currently-unused sampler params.
                }
            }
        }

        private void setParameterf(int pname, float param) {
            if (pname == VulkanicAPI.GL_TEXTURE_MAX_LOD) {
                maxLod = param;
                return;
            }

            setParameteri(pname, Math.round(param));
        }

        private int effectiveMaxLod(int textureViewMipCount) {
            int textureMaxLod = Math.max(0, textureViewMipCount - 1);
            int samplerMaxLod = Math.max(0, (int) Math.floor(maxLod));
            return Math.min(textureMaxLod, samplerMaxLod);
        }
    }

    @Nullable
    private VirtualSamplerState getBoundVirtualSamplerStateForUnit(int textureUnit) {
        Integer samplerId = boundSamplerPerUnit.get(textureUnit);
        if (samplerId == null || samplerId == 0) {
            return null;
        }

        return virtualSamplerStates.get(samplerId);
    }

    @Nullable
    private VirtualSamplerState getVirtualSamplerState(int samplerId) {
        return virtualSamplerStates.get(samplerId);
    }

    private static final boolean DEBUG_VULKAN_SOLID_PARTICLE_FRAGMENT = Boolean.getBoolean("mattmc.vulkan.debugParticleSolidColor");
    private static final boolean DEBUG_VULKAN_PARTICLE_COLOR_ONLY = Boolean.getBoolean("mattmc.vulkan.debugParticleColorOnly");
    private static final boolean DEBUG_VULKAN_TEXTURED_PARTICLE_ALPHA_ONE = Boolean.getBoolean("mattmc.vulkan.debugParticleTexturedAlphaOne");
    private static final boolean DEBUG_VULKAN_PARTICLE_ALPHA_MASK = Boolean.getBoolean("mattmc.vulkan.debugParticleAlphaMask");
    private static final boolean DEBUG_VULKAN_DESCRIPTOR_BIND_LOGS = Boolean.getBoolean("mattmc.vulkan.debugDescriptorBindingSeam");
    private static final String DEBUG_VULKAN_SOLID_PARTICLE_FRAGMENT_SOURCE = """
#version 150

layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = vec4(1.0, 0.0, 1.0, 1.0);
}
""";
    private static final String DEBUG_VULKAN_PARTICLE_COLOR_ONLY_SOURCE = """
#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = vertexColor * ColorModulator;
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
""";
    private static final String DEBUG_VULKAN_TEXTURED_PARTICLE_ALPHA_ONE_SOURCE = """
#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    fragColor = apply_fog(vec4(color.rgb, 1.0), sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
""";
    private static final String DEBUG_VULKAN_PARTICLE_ALPHA_MASK_SOURCE = """
#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    float alpha = (texture(Sampler0, texCoord0) * vertexColor * ColorModulator).a;
    fragColor = alpha < 0.1 ? vec4(0.0, 1.0, 0.0, 1.0) : vec4(1.0, 0.0, 1.0, 1.0);
}
""";

    private static boolean isParticlePipeline(RenderPipeline renderPipeline) {
        String pipelineLocation = renderPipeline.getLocation().toString();
        return "minecraft:pipeline/opaque_particle".equals(pipelineLocation)
            || "minecraft:pipeline/translucent_particle".equals(pipelineLocation);
    }

    private static boolean shouldDisableFogForEntityPipeline(RenderPipeline renderPipeline) {
        net.minecraft.resources.ResourceLocation fragmentShader = renderPipeline.getFragmentShader();
        if (fragmentShader == null) {
            return false;
        }

        String shaderPath = fragmentShader.toString();
        return "minecraft:core/entity".equals(shaderPath)
            || "minecraft:core/rendertype_entity_shadow".equals(shaderPath)
            || "minecraft:core/rendertype_item_entity_translucent_cull".equals(shaderPath)
            || "minecraft:core/rendertype_entity_decal".equals(shaderPath);
    }

    public CompiledRenderPipeline precompileRenderPipeline(
        RenderPipeline renderPipeline,
        @Nullable BiFunction<net.minecraft.resources.ResourceLocation, ShaderType, String> sourceProvider
    ) {
        if (renderPipeline == null) {
            throw new IllegalArgumentException("renderPipeline must not be null");
        }

        // Keep behavior explicit: callers that skip source plumbing get a deterministic invalid result.
        if (sourceProvider == null) {
            LOGGER.error("Cannot precompile Vulkan pipeline {} without shader source provider", renderPipeline.getLocation());
            return PrecompiledPipelineState.failed();
        }

        return precompiledPipelineCache.computeIfAbsent(
            renderPipeline,
            pipeline -> compilePrecompiledPipeline(pipeline, sourceProvider)
        );
    }

    public void clearPrecompiledPipelineCache() {
        for (PrecompiledPipelineState state : new ArrayList<>(precompiledPipelineCache.values())) {
            state.closeIfNeeded();
        }
        precompiledPipelineCache.clear();
        for (PipelineHandle pipelineHandle : new ArrayList<>(descriptorPipelineCache.values())) {
            if (pipelineHandle != null) {
                pipelineHandle.close();
            }
        }
        descriptorPipelineCache.clear();
        for (PipelineHandle pipelineHandle : new ArrayList<>(renderTargetPipelineCache.values())) {
            if (pipelineHandle != null) {
                pipelineHandle.close();
            }
        }
        renderTargetPipelineCache.clear();
        for (PipelineHandle pipelineHandle : new ArrayList<>(legacyProgramPipelineCache.values())) {
            if (pipelineHandle != null) {
                pipelineHandle.close();
            }
        }
        legacyProgramPipelineCache.clear();
    }

    private PrecompiledPipelineState compilePrecompiledPipeline(
        RenderPipeline renderPipeline,
        BiFunction<net.minecraft.resources.ResourceLocation, ShaderType, String> sourceProvider
    ) {
        try {
            String vertexSource = sourceProvider.apply(renderPipeline.getVertexShader(), ShaderType.VERTEX);
            String fragmentSource = sourceProvider.apply(renderPipeline.getFragmentShader(), ShaderType.FRAGMENT);

            if (vertexSource == null || fragmentSource == null) {
                LOGGER.error(
                    "Cannot precompile Vulkan pipeline {} because shader source is missing (vertex={}, fragment={})",
                    renderPipeline.getLocation(),
                    renderPipeline.getVertexShader(),
                    renderPipeline.getFragmentShader()
                );
                return PrecompiledPipelineState.failed();
            }

            String vertexWithDefines = injectExplicitVulkanBindings(
                renderPipeline,
                ShaderType.VERTEX,
                GlslPreprocessor.injectDefines(vertexSource, renderPipeline.getShaderDefines())
            );
            String fragmentWithDefines = injectExplicitVulkanBindings(
                renderPipeline,
                ShaderType.FRAGMENT,
                GlslPreprocessor.injectDefines(fragmentSource, renderPipeline.getShaderDefines())
            );

            if (isParticlePipeline(renderPipeline)) {
                if (DEBUG_VULKAN_SOLID_PARTICLE_FRAGMENT) {
                    LOGGER.info("Vulkan particle debug fragment override enabled for {}", renderPipeline.getLocation());
                    fragmentWithDefines = DEBUG_VULKAN_SOLID_PARTICLE_FRAGMENT_SOURCE;
                } else if (DEBUG_VULKAN_PARTICLE_COLOR_ONLY) {
                    LOGGER.info("Vulkan particle color-only debug override enabled for {}", renderPipeline.getLocation());
                    fragmentWithDefines = DEBUG_VULKAN_PARTICLE_COLOR_ONLY_SOURCE;
                } else if (DEBUG_VULKAN_TEXTURED_PARTICLE_ALPHA_ONE) {
                    LOGGER.info("Vulkan particle textured-alpha-one debug override enabled for {}", renderPipeline.getLocation());
                    fragmentWithDefines = DEBUG_VULKAN_TEXTURED_PARTICLE_ALPHA_ONE_SOURCE;
                } else if (DEBUG_VULKAN_PARTICLE_ALPHA_MASK) {
                    LOGGER.info("Vulkan particle alpha-mask debug override enabled for {}", renderPipeline.getLocation());
                    fragmentWithDefines = DEBUG_VULKAN_PARTICLE_ALPHA_MASK_SOURCE;
                }
            }

            if (shouldDisableFogForEntityPipeline(renderPipeline)) {
                fragmentWithDefines = GlslPreprocessor.injectDefines(
                    fragmentWithDefines,
                    net.minecraft.client.renderer.ShaderDefines.builder().define("MATTMC_VULKAN_DISABLE_ENTITY_FOG").build()
                );
            }

            List<String> standaloneUniformDeclarations = collectStandaloneUniformDeclarations(
                List.of(vertexWithDefines, fragmentWithDefines)
            );
            int standaloneUniformBindingIndex = standaloneUniformBlockBindingIndex(renderPipeline);
            VulkanicSpirvModule vertexModule = compileSpirvModuleForBackend(
                VulkanicShaderStage.VERTEX,
                vertexWithDefines,
                renderPipeline.getVertexShader().toString(),
                "main",
                standaloneUniformDeclarations,
                standaloneUniformBindingIndex
            );
            VulkanicSpirvModule fragmentModule = compileSpirvModuleForBackend(
                VulkanicShaderStage.FRAGMENT,
                fragmentWithDefines,
                renderPipeline.getFragmentShader().toString(),
                "main",
                standaloneUniformDeclarations,
                standaloneUniformBindingIndex
            );

            PipelineDescriptor descriptor = PipelineDescriptor.fromRenderPipelineAndSpirvModules(
                renderPipeline,
                List.of(vertexModule, fragmentModule)
            );
            PipelineHandle pipelineHandle = createPipeline(descriptor);

            if (pipelineHandle == null || !pipelineHandle.isValid()) {
                if (pipelineHandle != null) {
                    pipelineHandle.close();
                }
                LOGGER.error("Vulkan precompile produced invalid pipeline handle for {}", renderPipeline.getLocation());
                return PrecompiledPipelineState.failed();
            }

            return PrecompiledPipelineState.successful(
                pipelineHandle,
                descriptor,
                descriptor.getStableCacheKey(),
                descriptor.getResourceLayoutCacheKey()
            );
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to precompile Vulkan pipeline {}", renderPipeline.getLocation(), exception);
            return PrecompiledPipelineState.failed();
        }
    }

    private static String injectExplicitVulkanBindings(RenderPipeline renderPipeline, ShaderType shaderType, String shaderSource) {
        String reboundSource = shaderSource;
        if (shaderType == ShaderType.VERTEX) {
            reboundSource = injectExplicitVertexInputLocations(renderPipeline, reboundSource);
        }
        if (isParticlePipeline(renderPipeline)) {
            reboundSource = injectExplicitParticleStageLocations(shaderType, reboundSource);
        }
        int bindingIndex = 0;

        for (String samplerName : renderPipeline.getSamplers()) {
            reboundSource = injectExplicitNamedUniformBinding(reboundSource, samplerName, bindingIndex++);
        }

        for (RenderPipeline.UniformDescription uniform : renderPipeline.getUniforms()) {
            reboundSource = switch (uniform.type()) {
                case UNIFORM_BUFFER -> injectExplicitUniformBlockBinding(reboundSource, uniform.name(), bindingIndex++);
                case TEXEL_BUFFER -> injectExplicitNamedUniformBinding(reboundSource, uniform.name(), bindingIndex++);
            };
        }

        // The Vulkan shader normalizer can synthesize this block from legacy standalone
        // uniforms; pin it after declared pipeline resources to avoid auto-map collisions.
        reboundSource = injectExplicitUniformBlockBinding(reboundSource, "VulkanicStandaloneUniforms", bindingIndex);

        return reboundSource;
    }

    private static String injectExplicitParticleStageLocations(ShaderType shaderType, String shaderSource) {
        if (shaderType == ShaderType.VERTEX) {
            String reboundSource = shaderSource;
            reboundSource = injectExplicitInterfaceLocation(reboundSource, "out", "sphericalVertexDistance", 0);
            reboundSource = injectExplicitInterfaceLocation(reboundSource, "out", "cylindricalVertexDistance", 1);
            reboundSource = injectExplicitInterfaceLocation(reboundSource, "out", "texCoord0", 2);
            reboundSource = injectExplicitInterfaceLocation(reboundSource, "out", "vertexColor", 3);
            return reboundSource;
        }

        if (shaderType == ShaderType.FRAGMENT) {
            String reboundSource = shaderSource;
            reboundSource = injectExplicitInterfaceLocation(reboundSource, "in", "sphericalVertexDistance", 0);
            reboundSource = injectExplicitInterfaceLocation(reboundSource, "in", "cylindricalVertexDistance", 1);
            reboundSource = injectExplicitInterfaceLocation(reboundSource, "in", "texCoord0", 2);
            reboundSource = injectExplicitInterfaceLocation(reboundSource, "in", "vertexColor", 3);
            return reboundSource;
        }

        return shaderSource;
    }

    private static String injectExplicitInterfaceLocation(String shaderSource, String storageQualifier, String variableName, int location) {
        Pattern layoutPattern = Pattern.compile(
            "(?m)(^\\s*)layout\\s*\\(([^)]*)\\)\\s*((?:flat\\s+|noperspective\\s+|smooth\\s+|centroid\\s+|sample\\s+)*)"
                + Pattern.quote(storageQualifier)
                + "\\s+([A-Za-z0-9_]+)\\s+"
                + Pattern.quote(variableName)
                + "(\\s*\\[[^]]+\\])?\\s*;"
        );
        Matcher layoutMatcher = layoutPattern.matcher(shaderSource);
        if (layoutMatcher.find()) {
            String layoutBody = layoutMatcher.group(2);
            if (layoutBody.contains("location")) {
                return shaderSource;
            }

            return layoutMatcher.replaceFirst(
                Matcher.quoteReplacement(
                    layoutMatcher.group(1)
                        + "layout(" + layoutBody + ", location = " + location + ") "
                        + layoutMatcher.group(3)
                        + storageQualifier
                        + " "
                        + layoutMatcher.group(4)
                        + " "
                        + variableName
                        + (layoutMatcher.group(5) == null ? "" : layoutMatcher.group(5))
                        + ";"
                )
            );
        }

        Pattern plainPattern = Pattern.compile(
            "(?m)(^\\s*)((?:flat\\s+|noperspective\\s+|smooth\\s+|centroid\\s+|sample\\s+)*)"
                + Pattern.quote(storageQualifier)
                + "\\s+([A-Za-z0-9_]+)\\s+"
                + Pattern.quote(variableName)
                + "(\\s*\\[[^]]+\\])?\\s*;"
        );
        Matcher plainMatcher = plainPattern.matcher(shaderSource);
        if (!plainMatcher.find()) {
            return shaderSource;
        }

        return plainMatcher.replaceFirst(
            Matcher.quoteReplacement(
                plainMatcher.group(1)
                    + "layout(location = " + location + ") "
                    + plainMatcher.group(2)
                    + storageQualifier
                    + " "
                    + plainMatcher.group(3)
                    + " "
                    + variableName
                    + (plainMatcher.group(4) == null ? "" : plainMatcher.group(4))
                    + ";"
            )
        );
    }

    private static String injectExplicitVertexInputLocations(RenderPipeline renderPipeline, String shaderSource) {
        String reboundSource = shaderSource;
        List<String> attributeNames = renderPipeline.getVertexFormat().getElementAttributeNames();
        for (int location = 0; location < attributeNames.size(); location++) {
			reboundSource = injectExplicitVertexInputLocation(
				reboundSource,
				attributeNames.get(location),
				renderPipeline.getVertexFormat().getShaderAttributeLocation(location)
			);
        }
        return reboundSource;
    }

    private static String injectExplicitVertexInputLocation(String shaderSource, String attributeName, int location) {
        Pattern layoutPattern = Pattern.compile(
            "(?m)(^\\s*)layout\\s*\\(([^)]*)\\)\\s*((?:[A-Za-z0-9_]+\\s+)*)in\\s+([A-Za-z0-9_]+)\\s+"
                + Pattern.quote(attributeName)
                + "(\\s*\\[[^]]+\\])?\\s*;"
        );
        Matcher layoutMatcher = layoutPattern.matcher(shaderSource);
        if (layoutMatcher.find()) {
            String layoutBody = layoutMatcher.group(2);
            if (layoutBody.contains("location")) {
                return shaderSource;
            }

            return layoutMatcher.replaceFirst(
                Matcher.quoteReplacement(
                    layoutMatcher.group(1)
                        + "layout(" + layoutBody + ", location = " + location + ") "
                        + layoutMatcher.group(3)
                        + "in "
                        + layoutMatcher.group(4)
                        + " "
                        + attributeName
                        + (layoutMatcher.group(5) == null ? "" : layoutMatcher.group(5))
                        + ";"
                )
            );
        }

        Pattern plainPattern = Pattern.compile(
            "(?m)(^\\s*)((?:[A-Za-z0-9_]+\\s+)*)in\\s+([A-Za-z0-9_]+)\\s+"
                + Pattern.quote(attributeName)
                + "(\\s*\\[[^]]+\\])?\\s*;"
        );
        Matcher plainMatcher = plainPattern.matcher(shaderSource);
        if (!plainMatcher.find()) {
            return shaderSource;
        }

        return plainMatcher.replaceFirst(
            Matcher.quoteReplacement(
                plainMatcher.group(1)
                    + "layout(location = " + location + ") "
                    + plainMatcher.group(2)
                    + "in "
                    + plainMatcher.group(3)
                    + " "
                    + attributeName
                    + (plainMatcher.group(4) == null ? "" : plainMatcher.group(4))
                    + ";"
            )
        );
    }

    private static String injectExplicitUniformBlockBinding(String shaderSource, String blockName, int bindingIndex) {
        java.util.regex.Pattern layoutPattern = java.util.regex.Pattern.compile(
            "(?m)(^\\s*)layout\\s*\\(([^)]*)\\)\\s*uniform\\s+" + java.util.regex.Pattern.quote(blockName) + "\\s*\\{"
        );
        java.util.regex.Matcher layoutMatcher = layoutPattern.matcher(shaderSource);
        if (layoutMatcher.find()) {
            String layoutBody = layoutMatcher.group(2);
            if (layoutBody.contains("binding") || layoutBody.contains("set")) {
                return shaderSource;
            }

            return layoutMatcher.replaceFirst(
                java.util.regex.Matcher.quoteReplacement(
                    layoutMatcher.group(1)
                        + "layout(" + layoutBody + ", set = 0, binding = " + bindingIndex + ") uniform " + blockName + " {"
                )
            );
        }

        java.util.regex.Pattern plainPattern = java.util.regex.Pattern.compile(
            "(?m)(^\\s*)uniform\\s+" + java.util.regex.Pattern.quote(blockName) + "\\s*\\{"
        );
        java.util.regex.Matcher plainMatcher = plainPattern.matcher(shaderSource);
        if (!plainMatcher.find()) {
            return shaderSource;
        }

        return plainMatcher.replaceFirst(
            java.util.regex.Matcher.quoteReplacement(
                plainMatcher.group(1)
                    + "layout(set = 0, binding = " + bindingIndex + ") uniform " + blockName + " {"
            )
        );
    }

    private static String injectExplicitNamedUniformBinding(String shaderSource, String uniformName, int bindingIndex) {
        java.util.regex.Pattern layoutPattern = java.util.regex.Pattern.compile(
            "(?m)(^\\s*)layout\\s*\\(([^)]*)\\)\\s*uniform\\s+([A-Za-z0-9_]+)\\s+"
                + java.util.regex.Pattern.quote(uniformName)
                + "\\s*;"
        );
        java.util.regex.Matcher layoutMatcher = layoutPattern.matcher(shaderSource);
        if (layoutMatcher.find()) {
            String layoutBody = layoutMatcher.group(2);
            if (layoutBody.contains("binding") || layoutBody.contains("set")) {
                return shaderSource;
            }

            return layoutMatcher.replaceFirst(
                java.util.regex.Matcher.quoteReplacement(
                    layoutMatcher.group(1)
                        + "layout(" + layoutBody + ", set = 0, binding = " + bindingIndex + ") uniform "
                        + layoutMatcher.group(3)
                        + " "
                        + uniformName
                        + ";"
                )
            );
        }

        java.util.regex.Pattern plainPattern = java.util.regex.Pattern.compile(
            "(?m)(^\\s*)uniform\\s+([A-Za-z0-9_]+)\\s+" + java.util.regex.Pattern.quote(uniformName) + "\\s*;"
        );
        java.util.regex.Matcher plainMatcher = plainPattern.matcher(shaderSource);
        if (!plainMatcher.find()) {
            return shaderSource;
        }

        return plainMatcher.replaceFirst(
            java.util.regex.Matcher.quoteReplacement(
                plainMatcher.group(1)
                    + "layout(set = 0, binding = " + bindingIndex + ") uniform "
                    + plainMatcher.group(2)
                    + " "
                    + uniformName
                    + ";"
            )
        );
    }

    public GraphicsBackendType getBackendType() {
        return GraphicsBackendType.VULKAN;
    }

    public long getGraphicsContext() {
        return MemoryUtil.NULL;
    }

    public boolean isNativeVulkanReady() {
        return nativeSpine != null;
    }

    public VulkanNativeInitializationInfo initializeNativeVulkanRuntime() {
        attemptNativeBringUp();
        VulkanReadinessReport report = refreshReadinessReport();

        if (isNativeVulkanReady()) {
            return VulkanNativeInitializationInfo.attempted(
                GraphicsBackendType.VULKAN,
                true,
                true,
                "Native Vulkan runtime initialized successfully.",
                report.summaryLine()
            );
        }

        String status;
        if (nativeBringUpFailure != null && !nativeBringUpFailure.isBlank()) {
            status = "Native Vulkan runtime initialization failed: " + nativeBringUpFailure;
        } else if (nativeBringUpAttempted) {
            status = "Native Vulkan runtime initialization attempted but did not become ready.";
        } else {
            status = "Native Vulkan runtime initialization was not attempted.";
        }

        return VulkanNativeInitializationInfo.attempted(
            GraphicsBackendType.VULKAN,
            false,
            false,
            status,
            report.summaryLine()
        );
    }

    public long prepareRendererBootstrapWindow(long mainWindowHandle) {
        if (auxiliaryOpenGlContextWindow != MemoryUtil.NULL) {
            return auxiliaryOpenGlContextWindow;
        }

        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_OPENGL_API);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);

        auxiliaryOpenGlContextWindow = GLFW.glfwCreateWindow(1, 1, "MattMC Vulkan Compatibility Bootstrap", 0L, 0L);
        if (auxiliaryOpenGlContextWindow == MemoryUtil.NULL) {
            throw new IllegalStateException("Failed to create Vulkan compatibility bootstrap window for renderer startup");
        }

        LOGGER.info("Created Vulkan compatibility bootstrap window for backend-owned renderer startup");
        return auxiliaryOpenGlContextWindow;
    }

    public GpuDevice createRendererDevice(
        long rendererBootstrapWindowHandle,
        int debugVerbosity,
        boolean debugEnabled,
        BiFunction<net.minecraft.resources.ResourceLocation, ShaderType, String> defaultShaderSource,
        boolean debugLabelsEnabled
    ) {
        net.blaze3d.opengl.GlDevice compatibilityDevice = new net.blaze3d.opengl.GlDevice(
            rendererBootstrapWindowHandle,
            debugVerbosity,
            debugEnabled,
            defaultShaderSource,
            debugLabelsEnabled
        );
        this.compatibilityDevice = compatibilityDevice;
        this.rendererDebuggingEnabled = debugEnabled;
        return new VulkanCompatibilityGpuDevice(this, compatibilityDevice);
    }

    public CommandEncoder createCommandEncoder() {
        return new VulkanNativeCommandEncoder(this);
    }

    CommandEncoder createCompatibilityCommandEncoder() {
        net.blaze3d.opengl.GlDevice device = this.compatibilityDevice;
        if (device == null) {
            throw new IllegalStateException(
                "Vulkan compatibility device has not been created yet. "
                    + "Ensure renderer startup calls createRendererDevice() before requesting a command encoder.");
        }

        return device.createCommandEncoder();
    }

    public CommandEncoder createNativeTerrainCommandEncoder() {
        return new VulkanNativeTerrainCommandEncoder(this);
    }

    @Nullable
    PipelineDescriptor resolvePrecompiledPipelineDescriptor(RenderPipeline renderPipeline) {
        PrecompiledPipelineState state = precompiledPipelineCache.get(renderPipeline);
        return state != null && state.isValid() ? state.descriptor : null;
    }

    void releaseCompatibilityDevice(net.blaze3d.opengl.GlDevice device) {
        if (this.compatibilityDevice == device) {
            this.compatibilityDevice = null;
            this.rendererDebuggingEnabled = false;
        }
    }

    public net.blaze3d.systems.RenderPass createRenderPass(
        java.util.function.Supplier<String> supplier,
        GpuTextureView colorTextureView,
        java.util.OptionalInt clearColor
    ) {
        return new VulkanNativeCommandEncoder(this).createRenderPass(supplier, colorTextureView, clearColor);
    }

    public net.blaze3d.systems.RenderPass createRenderPass(
        java.util.function.Supplier<String> supplier,
        GpuTextureView colorTextureView,
        java.util.OptionalInt clearColor,
        @Nullable GpuTextureView depthTextureView,
        java.util.OptionalDouble clearDepth
    ) {
        return new VulkanNativeCommandEncoder(this).createRenderPass(supplier, colorTextureView, clearColor, depthTextureView, clearDepth);
    }

    public net.blaze3d.systems.RenderPass createRenderPass(
        java.util.function.Supplier<String> supplier,
        int framebuffer,
        boolean hasDepthTexture
    ) {
        // Framebuffer-id callers still need the framebuffer-aware compatibility resource
        // recovery path. Descriptor-backed callers use the native Vulkan render-pass path.
        return createCompatibilityCommandEncoder().createRenderPass(supplier, framebuffer, hasDepthTexture);
    }

    public net.blaze3d.systems.RenderPass createRenderPass(VulkanicRenderTargetDescriptor descriptor) {
        return new VulkanNativeCommandEncoder(this).createRenderPass(descriptor);
    }

    public GpuTexture createTexture(
        @Nullable java.util.function.Supplier<String> supplier,
        int usage,
        TextureFormat textureFormat,
        int width,
        int height,
        int depthOrLayers,
        int mipLevels
    ) {
		return this.createOwnedGpuTexture(supplier == null ? null : supplier.get(), usage, textureFormat, width, height, depthOrLayers, mipLevels);
    }

    public GpuTexture createTexture(
        @Nullable String label,
        int usage,
        TextureFormat textureFormat,
        int width,
        int height,
        int depthOrLayers,
        int mipLevels
    ) {
        return this.createOwnedGpuTexture(label, usage, textureFormat, width, height, depthOrLayers, mipLevels);
    }

    public GpuBuffer createBuffer(@Nullable java.util.function.Supplier<String> supplier, int usage, int size) {
        return this.createOwnedGpuBuffer(supplier, usage, size);
    }

    public GpuBuffer createBuffer(@Nullable java.util.function.Supplier<String> supplier, int usage, java.nio.ByteBuffer data) {
        return this.createOwnedGpuBuffer(supplier, usage, data);
    }

    public GpuTextureView createTextureView(GpuTexture texture) {
        return this.createTextureView(texture, 0, texture.getMipLevels());
    }

    public GpuTextureView createTextureView(GpuTexture texture, int baseMipLevel, int mipLevelCount) {
        if (texture.isClosed()) {
            throw new IllegalArgumentException("Can't create texture view with closed texture");
        }
        if (baseMipLevel < 0 || baseMipLevel + mipLevelCount > texture.getMipLevels()) {
            throw new IllegalArgumentException(
                mipLevelCount + " mip levels starting from " + baseMipLevel + " would be out of range for texture with only " + texture.getMipLevels() + " mip levels"
            );
        }

        Runnable closeAction = () -> {
        };
        if (texture instanceof VulkanGpuTexture vulkanTexture) {
            vulkanTexture.addViews();
            closeAction = vulkanTexture::removeViews;
        }
        return new VulkanGpuTextureView(texture, baseMipLevel, mipLevelCount, closeAction);
    }

    private GpuBuffer createOwnedGpuBuffer(@Nullable java.util.function.Supplier<String> supplier, int usage, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Buffer size must be greater than zero");
        }

        CommandContext ctx = getCurrentCommandContext();
        while (net.vulkanic.VulkanicAPI.getError(ctx) != 0) {
        }

        int handle = 0;
        try {
            handle = net.vulkanic.VulkanicAPI.createBuffer(ctx);
            int target = selectLegacyBufferTarget(usage);
            net.vulkanic.VulkanicAPI.bindBuffer(ctx, target, handle);
            net.vulkanic.VulkanicAPI.namedBufferDataDSA(ctx, handle, size, net.blaze3d.opengl.GlConst.bufferUsageToGlEnum(usage));
            net.vulkanic.VulkanicAPI.bindBuffer(ctx, target, 0);
            int error = net.vulkanic.VulkanicAPI.getError(ctx);
            if (error == 1285) {
                throw new GpuOutOfMemoryException("Could not allocate buffer of " + size + " for " + supplier);
            }
            if (error != 0) {
                throw new IllegalStateException("OpenGL error " + error);
            }
            net.irisshaders.iris.gl.IrisRenderSystem.incrementTrackedBuffers();
            return new VulkanGpuBuffer(supplier, usage, size, handle);
        } catch (RuntimeException exception) {
            if (handle != 0) {
                net.vulkanic.VulkanicAPI.deleteBuffer(ctx, handle);
            }
            throw exception;
        }
    }

    private GpuBuffer createOwnedGpuBuffer(@Nullable java.util.function.Supplier<String> supplier, int usage, java.nio.ByteBuffer data) {
        if (data == null || !data.hasRemaining()) {
            throw new IllegalArgumentException("Buffer source must not be empty");
        }

        CommandContext ctx = getCurrentCommandContext();
        while (net.vulkanic.VulkanicAPI.getError(ctx) != 0) {
        }

        int handle = 0;
        try {
            java.nio.ByteBuffer initialData = data.duplicate();
            int size = initialData.remaining();
            handle = net.vulkanic.VulkanicAPI.createBuffer(ctx);
            int target = selectLegacyBufferTarget(usage);
            net.vulkanic.VulkanicAPI.bindBuffer(ctx, target, handle);
            net.vulkanic.VulkanicAPI.namedBufferDataDSA(ctx, handle, initialData, net.blaze3d.opengl.GlConst.bufferUsageToGlEnum(usage));
            net.vulkanic.VulkanicAPI.bindBuffer(ctx, target, 0);
            int error = net.vulkanic.VulkanicAPI.getError(ctx);
            if (error == 1285) {
                throw new GpuOutOfMemoryException("Could not allocate buffer of " + size + " for " + supplier);
            }
            if (error != 0) {
                throw new IllegalStateException("OpenGL error " + error);
            }
            net.irisshaders.iris.gl.IrisRenderSystem.incrementTrackedBuffers();
            return new VulkanGpuBuffer(supplier, usage, size, handle);
        } catch (RuntimeException exception) {
            if (handle != 0) {
                net.vulkanic.VulkanicAPI.deleteBuffer(ctx, handle);
            }
            throw exception;
        }
    }

    private GpuTexture createOwnedGpuTexture(@Nullable String label, int usage, TextureFormat textureFormat, int width, int height, int depthOrLayers, int mipLevels) {
        if (width < 1) {
            throw new IllegalArgumentException("Texture width must be at least 1");
        }
        if (height < 1) {
            throw new IllegalArgumentException("Texture height must be at least 1");
        }
        if (depthOrLayers < 1) {
            throw new IllegalArgumentException("Texture depth/layers must be at least 1");
        }
        if (mipLevels < 1) {
            throw new IllegalArgumentException("Texture mip level count must be at least 1");
        }

        boolean cubemap = (usage & GpuTexture.USAGE_CUBEMAP_COMPATIBLE) != 0;
        if (cubemap) {
            if (width != height) {
                throw new IllegalArgumentException("Cubemap compatible textures must be square, but size is " + width + "x" + height);
            }
            if (depthOrLayers % 6 != 0) {
                throw new IllegalArgumentException("Cubemap compatible textures must have a layer count with a multiple of 6, was " + depthOrLayers);
            }
            if (depthOrLayers > 6) {
                throw new UnsupportedOperationException("Array textures are not yet supported");
            }
        } else if (depthOrLayers > 1) {
            throw new UnsupportedOperationException("Array or 3D textures are not yet supported");
        }

        CommandContext ctx = getCurrentCommandContext();
        while (net.vulkanic.VulkanicAPI.getError(ctx) != 0) {
        }

        int textureId = net.irisshaders.iris.gl.IrisRenderSystem.createTextureId();
        String resolvedLabel = label == null ? String.valueOf(textureId) : label;
        try {
            net.vulkanic.VulkanicTextureTarget textureTarget;
            int glTarget;
            if (cubemap) {
                net.vulkanic.VulkanicAPI.bindCubemapTexture(ctx, textureId);
                textureTarget = net.vulkanic.VulkanicTextureTarget.TEXTURE_CUBE_MAP;
            } else {
                net.vulkanic.VulkanicAPI.bindTexture2D(ctx, textureId);
                textureTarget = net.vulkanic.VulkanicTextureTarget.TEXTURE_2D;
            }
            glTarget = textureTarget.toLegacyGlTarget();

            net.vulkanic.VulkanicAPI.setTextureMaxLevel(ctx, textureTarget, mipLevels - 1);
            net.vulkanic.VulkanicAPI.setTextureMinLod(ctx, textureTarget, 0);
            net.vulkanic.VulkanicAPI.setTextureMaxLod(ctx, textureTarget, mipLevels - 1);
            if (textureFormat.hasDepthAspect()) {
                net.vulkanic.VulkanicAPI.disableTextureCompareMode(ctx, textureTarget);
            }

            if (cubemap) {
                for (int target : net.blaze3d.opengl.GlConst.CUBEMAP_TARGETS) {
                    for (int level = 0; level < mipLevels; level++) {
                        net.vulkanic.VulkanicAPI.uploadTexture2D(
                            ctx,
                            target,
                            level,
                            net.blaze3d.opengl.GlConst.toGlInternalId(textureFormat),
                            width >> level,
                            height >> level,
                            0,
                            net.blaze3d.opengl.GlConst.toGlExternalId(textureFormat),
                            net.blaze3d.opengl.GlConst.toGlType(textureFormat),
                            (java.nio.ByteBuffer)null
                        );
                    }
                }
            } else {
                for (int level = 0; level < mipLevels; level++) {
                    net.vulkanic.VulkanicAPI.uploadTexture2D(
                        ctx,
                        glTarget,
                        level,
                        net.blaze3d.opengl.GlConst.toGlInternalId(textureFormat),
                        width >> level,
                        height >> level,
                        0,
                        net.blaze3d.opengl.GlConst.toGlExternalId(textureFormat),
                        net.blaze3d.opengl.GlConst.toGlType(textureFormat),
                        (java.nio.ByteBuffer)null
                    );
                }
            }

            int error = net.vulkanic.VulkanicAPI.getError(ctx);
            if (error == 1285) {
                throw new GpuOutOfMemoryException("Could not allocate texture of " + width + "x" + height + " for " + resolvedLabel);
            }
            if (error != 0) {
                throw new IllegalStateException("OpenGL error " + error);
            }

            return new VulkanGpuTexture(usage, resolvedLabel, textureFormat, width, height, depthOrLayers, mipLevels, textureId);
        } catch (RuntimeException exception) {
            net.irisshaders.iris.gl.IrisRenderSystem.deleteTextureId(textureId);
            throw exception;
        }
    }

    private static int selectLegacyBufferTarget(int usage) {
        if ((usage & GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER) != 0) {
            return net.vulkanic.VulkanicAPI.GL_TEXTURE_BUFFER;
        }
        if ((usage & GpuBuffer.USAGE_UNIFORM) != 0) {
            return net.vulkanic.VulkanicAPI.GL_UNIFORM_BUFFER;
        }
        if ((usage & GpuBuffer.USAGE_INDEX) != 0) {
            return net.vulkanic.VulkanicAPI.GL_ELEMENT_ARRAY_BUFFER;
        }
        if ((usage & GpuBuffer.USAGE_VERTEX) != 0) {
            return net.vulkanic.VulkanicAPI.GL_ARRAY_BUFFER;
        }
        if ((usage & GpuBuffer.USAGE_COPY_SRC) != 0) {
            return net.vulkanic.VulkanicAPI.GL_COPY_READ_BUFFER;
        }
        return net.vulkanic.VulkanicAPI.GL_COPY_WRITE_BUFFER;
    }

    public void onRendererDeviceInitialized(long mainWindowHandle, GpuDevice gpuDevice) {
        LOGGER.info("Vulkan renderer startup now uses backend-owned device creation instead of shared GlDevice construction");
        LOGGER.info("Vulkan readiness: {}", getReadinessReport().summaryLine());
        LOGGER.info("Vulkan execution context: {}", getVulkanExecutionContextInfo().summaryLine());
        LOGGER.info("Vulkan surface/swapchain: {}", getVulkanSwapchainSurfaceInfo().summaryLine());

        // Iris subsystems must be initialized on the Vulkan path exactly as they are on the OpenGL path.
        // IrisRenderSystem.dsaState (and several other static fields) is set here; without this call those
        // fields remain null and any Iris shader-pipeline creation will throw a NullPointerException.
        // The Vulkan capabilities object reports all-false for DSA/multibind/etc., so DSAUnsupported is
        // selected — all of its methods route through VulkanicAPI and are fully backend-neutral.
        net.irisshaders.iris.Iris.duringRenderSystemInit();
        net.irisshaders.iris.gl.GLDebug.reloadDebugState();
        net.irisshaders.iris.gl.IrisRenderSystem.initRenderer();
        net.irisshaders.iris.samplers.IrisSamplers.initRenderer();
        net.irisshaders.iris.Iris.onRenderSystemInit();

        if (mainWindowHandle != MemoryUtil.NULL) {
            LOGGER.info("Reasserting GLFW main window visibility/focus after Vulkan renderer startup: 0x{}",
                Long.toHexString(mainWindowHandle));
            GLFW.glfwShowWindow(mainWindowHandle);
            GLFW.glfwFocusWindow(mainWindowHandle);
        }

        cleanupRendererBootstrapResources();
    }

    public void cleanupRendererBootstrapResources() {
        if (auxiliaryOpenGlContextWindow == MemoryUtil.NULL) {
            return;
        }

        try {
            if (GLFW.glfwGetCurrentContext() == auxiliaryOpenGlContextWindow) {
                GLFW.glfwMakeContextCurrent(MemoryUtil.NULL);
            }
            GLFW.glfwDestroyWindow(auxiliaryOpenGlContextWindow);
            LOGGER.info("Destroyed Vulkan compatibility bootstrap window");
        } catch (Throwable throwable) {
            LOGGER.warn("Failed to destroy Vulkan compatibility bootstrap window cleanly", throwable);
        } finally {
            auxiliaryOpenGlContextWindow = MemoryUtil.NULL;
        }
    }

    public VulkanExecutionContextInfo getVulkanExecutionContextInfo() {
        attemptNativeBringUp();

        NativeSpine spine = nativeSpine;
        if (spine == null) {
            String status;
            if (!nativeBringUpAttempted) {
                status = "Native Vulkan bring-up has not been attempted.";
            } else if (nativeBringUpFailure != null && !nativeBringUpFailure.isBlank()) {
                status = "Native Vulkan bring-up failed: " + nativeBringUpFailure;
            } else {
                status = "Native Vulkan spine is unavailable.";
            }

            return VulkanExecutionContextInfo.unavailable(
                GraphicsBackendType.VULKAN,
                false,
                status
            );
        }

        CommandContext context = currentCommandContext;
        long commandBufferHandle = context == null ? spine.primaryCommandBufferHandle() : context.getHandle();
        String commandContextDebugName = context == null ? "Vulkan-PrimaryCommandBuffer" : context.getDebugName();

        return VulkanExecutionContextInfo.available(
            GraphicsBackendType.VULKAN,
            spine.logicalDeviceHandle(),
            spine.graphicsQueueHandle(),
            spine.graphicsQueueFamilyIndex(),
            spine.commandPoolHandle(),
            commandBufferHandle,
            commandContextDebugName,
            "Native Vulkan execution context is available."
        );
    }

    public VulkanSwapchainSurfaceInfo getVulkanSwapchainSurfaceInfo() {
        attemptNativeBringUp();

        NativeSpine spine = nativeSpine;
        if (spine == null) {
            String status;
            if (!nativeBringUpAttempted) {
                status = "Native Vulkan bring-up has not been attempted.";
            } else if (nativeBringUpFailure != null && !nativeBringUpFailure.isBlank()) {
                status = "Native Vulkan bring-up failed: " + nativeBringUpFailure;
            } else {
                status = "Native Vulkan surface/swapchain is unavailable.";
            }

            return VulkanSwapchainSurfaceInfo.unavailable(
                GraphicsBackendType.VULKAN,
                false,
                status
            );
        }

        return VulkanSwapchainSurfaceInfo.available(
            GraphicsBackendType.VULKAN,
            spine.surfaceHandle(),
            spine.swapchainHandle(),
            spine.swapchainImageFormat(),
            spine.swapchainColorSpace(),
            spine.swapchainPresentMode(),
            spine.swapchainImageCount(),
            spine.swapchainWidth(),
            spine.swapchainHeight(),
            "Native Vulkan surface/swapchain is available."
        );
    }

    public void recreateVulkanSwapchain() {
        ensureNativeReady("recreateVulkanSwapchain");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        spine.recreateSwapchain();
    }

    public boolean recreateVulkanSwapchainIfNeeded() {
        ensureNativeReady("recreateVulkanSwapchainIfNeeded");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        return spine.recreateSwapchainIfFramebufferSizeChanged();
    }

    /**
     * Returns the latest cached readiness report (probing once lazily).
     */
    public VulkanReadinessReport getReadinessReport() {
        VulkanReadinessReport report = cachedReadinessReport;
        if (report == null) {
            report = probeReadiness();
            cachedReadinessReport = report;
        }
        return report;
    }

    /**
     * Forces a fresh runtime probe and updates the cached readiness report.
     */
    public VulkanReadinessReport refreshReadinessReport() {
        VulkanReadinessReport report = probeReadiness();
        cachedReadinessReport = report;
        return report;
    }

    private static String compactThrowable(Throwable throwable) {
        return throwable.getClass().getSimpleName() + ": "
            + (throwable.getMessage() == null ? "<no-message>" : throwable.getMessage());
    }

    private VulkanReadinessReport probeReadiness() {
        boolean lwjglBindingsPresent = false;
        String bindingsStatus;
        try {
            Class.forName("org.lwjgl.vulkan.VK10", false, VulkanBackend.class.getClassLoader());
            lwjglBindingsPresent = true;
            bindingsStatus = "available";
        } catch (Throwable throwable) {
            bindingsStatus = "unavailable (" + compactThrowable(throwable) + ")";
        }

        boolean lwjglLoaderReachable = false;
        String loaderStatus;
        if (lwjglBindingsPresent) {
            try {
                int supportedVersion = VK.getInstanceVersionSupported();
                lwjglLoaderReachable = supportedVersion >= VK10.VK_API_VERSION_1_0;
                loaderStatus = lwjglLoaderReachable
                    ? "reachable (instanceVersion=0x" + Integer.toHexString(supportedVersion) + ")"
                    : "unreachable (VK.getInstanceVersionSupported returned 0x"
                        + Integer.toHexString(supportedVersion) + ")";
            } catch (Throwable throwable) {
                loaderStatus = "unreachable (" + compactThrowable(throwable) + ")";
            }
        } else {
            loaderStatus = "skipped (LWJGL Vulkan bindings unavailable)";
        }

        boolean glfwVulkanSupported = false;
        String glfwProbeStatus;
        if (lwjglBindingsPresent) {
            try {
                glfwVulkanSupported = GLFWVulkan.glfwVulkanSupported();
                glfwProbeStatus = glfwVulkanSupported ? "supported" : "unsupported";
            } catch (Throwable throwable) {
                glfwProbeStatus = "probe failed (" + compactThrowable(throwable) + ")";
            }
        } else {
            glfwProbeStatus = "skipped (LWJGL Vulkan bindings unavailable)";
        }

        boolean glfwRequiredExtensionsPresent = false;
        String glfwExtensionsStatus;
        if (lwjglBindingsPresent) {
            try {
                org.lwjgl.PointerBuffer requiredExtensions = GLFWVulkan.glfwGetRequiredInstanceExtensions();
                int requiredExtensionCount = requiredExtensions == null ? 0 : requiredExtensions.remaining();
                glfwRequiredExtensionsPresent = requiredExtensionCount > 0;
                glfwExtensionsStatus = glfwRequiredExtensionsPresent
                    ? "available (count=" + requiredExtensionCount + ")"
                    : "unavailable (glfwGetRequiredInstanceExtensions returned null/empty)";
            } catch (Throwable throwable) {
                glfwExtensionsStatus = "probe failed (" + compactThrowable(throwable) + ")";
            }
        } else {
            glfwExtensionsStatus = "skipped (LWJGL Vulkan bindings unavailable)";
        }

        String bringUpStatus;
        if (isNativeVulkanReady()) {
            bringUpStatus = "native spine initialized";
        } else if (nativeBringUpAttempted) {
            bringUpStatus = nativeBringUpFailure == null
                ? "attempted but unavailable"
                : "failed (" + nativeBringUpFailure + ")";
        } else {
            bringUpStatus = "not attempted";
        }

        List<String> blockers = new ArrayList<>();
        blockers.add("Native Vulkan command/pipeline integration is partial; non-implemented GraphicsBackend methods remain blocked with fail-fast behavior.");

        if (!lwjglBindingsPresent) {
            blockers.add("LWJGL Vulkan bindings are not available: " + bindingsStatus + ".");
        }

        if (!lwjglLoaderReachable) {
            blockers.add("Vulkan loader/API level probe did not pass: " + loaderStatus + ".");
        }

        if (!glfwVulkanSupported) {
            blockers.add("GLFW Vulkan support probe did not pass: " + glfwProbeStatus + ".");
        }

        if (!glfwRequiredExtensionsPresent) {
            blockers.add("GLFW required Vulkan instance extensions are not available: " + glfwExtensionsStatus + ".");
        }

        if (!isNativeVulkanReady()) {
            blockers.add("Native Vulkan spine status: " + bringUpStatus + ".");
        }

        return new VulkanReadinessReport(
            GraphicsBackendType.VULKAN,
            true,
            isNativeVulkanReady(),
            lwjglBindingsPresent,
            glfwVulkanSupported,
            glfwProbeStatus,
            blockers
        );
    }


    private void ensureNativeReady(String operation) {
        attemptNativeBringUp();

        if (isNativeVulkanReady()) {
            return;
        }

        VulkanReadinessReport report = getReadinessReport();

        StringBuilder sb = new StringBuilder();
        sb.append("Vulkan backend cannot perform '").append(operation).append("' because native Vulkan execution is not ready.\n");
        sb.append("isNativeVulkanReady()=").append(isNativeVulkanReady()).append('\n');
        sb.append("Readiness report: ").append(report.summaryLine()).append('\n');
        sb.append(report.toMultilineString());
        sb.append("Suggested actions:\n");
        sb.append(" - Ensure the Vulkan runtime & drivers are available on this system and that GLFW reports Vulkan support.\n");
        sb.append(" - Ensure LWJGL Vulkan bindings are present in the runtime classpath.\n");
        sb.append(" - Ensure the Vulkan backend is correctly initialized before calling Vulkan APIs.\n");
        sb.append(" - If OpenGL is desired, select/initialize the OpenGL backend instead.\n");

        throw new IllegalStateException(sb.toString());
    }

    private void attemptNativeBringUp() {
        if (nativeSpine != null || nativeBringUpAttempted) {
            return;
        }

        synchronized (nativeInitLock) {
            if (nativeSpine != null || nativeBringUpAttempted) {
                return;
            }

            nativeBringUpAttempted = true;
            NativeSpine createdSpine = null;
            try {
                createdSpine = NativeSpine.create(this);
                materializeCompiledShaderModules(createdSpine);

                nativeSpine = createdSpine;
                nativeBringUpFailure = null;
            } catch (Throwable throwable) {
                LOGGER.error("Native Vulkan bring-up failed during renderer startup", throwable);
                if (createdSpine != null) {
                    try {
                        createdSpine.close();
                    } catch (Throwable ignored) {
                    }
                }
                nativeBringUpFailure = compactThrowable(throwable);
                nativeSpine = null;
            } finally {
                cachedReadinessReport = null;
            }
        }
    }

    private void materializeCompiledShaderModules(NativeSpine spine) {
        for (Map.Entry<Integer, VirtualShader> entry : virtualShaders.entrySet()) {
            VirtualShader virtualShader = entry.getValue();
            if (!virtualShader.compileStatus || virtualShader.compiledModule == null) {
                continue;
            }

            try {
                materializeNativeShaderModuleIfNeeded(entry.getKey(), virtualShader, spine);
            } catch (RuntimeException exception) {
                throw new IllegalStateException(
                    "Failed to materialize Vulkan shader module for virtual shader " + entry.getKey(),
                    exception
                );
            }
        }
    }

    private long materializeNativeShaderModuleIfNeeded(int shaderId, VirtualShader virtualShader, NativeSpine spine) {
        synchronized (virtualShader) {
            if (virtualShader.nativeShaderModuleHandle != VK10.VK_NULL_HANDLE) {
                return virtualShader.nativeShaderModuleHandle;
            }

            VulkanicSpirvModule compiledModule = virtualShader.compiledModule;
            if (compiledModule == null) {
                throw new IllegalStateException("Virtual shader " + shaderId + " has no compiled SPIR-V module");
            }

            long shaderModuleHandle = spine.createShaderModule(compiledModule);
            virtualShader.nativeShaderModuleHandle = shaderModuleHandle;
            return shaderModuleHandle;
        }
    }

    private void releaseVirtualShaderNativeModule(VirtualShader virtualShader) {
        long shaderModuleHandle;
        synchronized (virtualShader) {
            shaderModuleHandle = virtualShader.nativeShaderModuleHandle;
            virtualShader.nativeShaderModuleHandle = VK10.VK_NULL_HANDLE;
        }

        if (shaderModuleHandle == VK10.VK_NULL_HANDLE) {
            return;
        }

        NativeSpine spine = nativeSpine;
        if (spine != null) {
            spine.destroyShaderModule(shaderModuleHandle);
        }
    }

    public CommandContext getCurrentCommandContext() {
        ensureNativeReady("getCurrentCommandContext");

        CommandContext context = currentCommandContext;
        if (context == null) {
            NativeSpine spine = nativeSpine;
            if (spine == null) {
                throw new IllegalStateException("Native Vulkan spine disappeared while resolving current command context.");
            }

            context = new VulkanCommandContext(
                spine.primaryCommandBufferHandle(),
                "Vulkan-CurrentCommandBuffer"
            );
            currentCommandContext = context;
        }

        return context;
    }

    public int createShader(CommandContext ctx, int shaderType) {
        VulkanicShaderStage stage = VulkanicShaderStage.fromLegacyGlShaderType(shaderType)
            .orElseThrow(() -> new IllegalArgumentException("Unsupported shader type for Vulkan SPIR-V path: " + shaderType));

        int shaderId = nextVirtualShaderId.getAndIncrement();
        virtualShaders.put(shaderId, new VirtualShader(stage));
        return shaderId;
    }

    public VulkanicSpirvModule compileSpirvModule(
        CommandContext ctx,
        VulkanicShaderStage shaderStage,
        CharSequence glslSource,
        String sourceName,
        String entryPoint
    ) {
        return compileSpirvModuleForBackend(shaderStage, glslSource, sourceName, entryPoint);
    }

    private VulkanicSpirvModule compileSpirvModuleForBackend(
        VulkanicShaderStage shaderStage,
        CharSequence glslSource,
        String sourceName,
        String entryPoint
    ) {
        return compileSpirvModuleForBackend(shaderStage, glslSource, sourceName, entryPoint, null, -1);
    }

    private VulkanicSpirvModule compileSpirvModuleForBackend(
        VulkanicShaderStage shaderStage,
        CharSequence glslSource,
        String sourceName,
        String entryPoint,
        @Nullable List<String> standaloneUniformDeclarations,
        int standaloneUniformBindingIndex
    ) {
        String preprocessedSource = preprocessMojImportsForVulkan(sourceName, glslSource.toString());
        String normalizedSource = GlslangSpirvCompiler.normalizeForVulkan(
            shaderStage,
            preprocessedSource,
            standaloneUniformDeclarations,
            standaloneUniformBindingIndex
        );
        try {
            java.nio.file.Path dbgDir = java.nio.file.Path.of("/tmp/vulkanic-glsl-dump");
            java.nio.file.Files.createDirectories(dbgDir);
            long seq = glslDumpCounter.incrementAndGet();
            String safeName = seq + "_" + sourceName.replaceAll("[^a-zA-Z0-9_\\-.]", "_") + "." + shaderStage.name().toLowerCase() + ".glsl";
            java.nio.file.Files.writeString(dbgDir.resolve(safeName), normalizedSource, java.nio.charset.StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
        } catch (java.io.IOException e) {
            LOGGER.warn("Failed to dump normalized GLSL for {}: {}", sourceName, e.getMessage());
        }
        return spirvCompiler.compile(shaderStage, normalizedSource, sourceName, entryPoint);
    }

    private String preprocessMojImportsForVulkan(String sourceName, String shaderSource) {
        if (!shaderSource.contains("#moj_import")) {
            return shaderSource;
        }

        net.minecraft.resources.ResourceLocation baseSourceLocation = tryParseShaderSourceLocation(sourceName);
        LOGGER.warn(
            "Vulkan backend received raw GLSL imports for {}; applying backend-side moj_import preprocessing.",
            sourceName
        );

        GlslPreprocessor preprocessor = new GlslPreprocessor() {
            private final Set<net.minecraft.resources.ResourceLocation> importedLocations = new HashSet<>();

            @Override
            public String applyImport(boolean quotedImport, String importPath) {
                net.minecraft.resources.ResourceLocation importLocation;

                try {
                    if (quotedImport) {
                        if (baseSourceLocation == null) {
                            String message = "Unable to resolve relative GLSL import '" + importPath + "' for source '" + sourceName + "'";
                            LOGGER.error(message);
                            return "#error " + message;
                        }

                        net.minecraft.resources.ResourceLocation fullBaseLocation = baseSourceLocation.withPath(net.minecraft.FileUtil::getFullResourcePath);
                        importLocation = fullBaseLocation.withPath(path -> net.minecraft.FileUtil.normalizeResourcePath(path + importPath));
                    } else {
                        importLocation = net.minecraft.resources.ResourceLocation.parse(importPath).withPrefix("shaders/include/");
                    }
                } catch (net.minecraft.ResourceLocationException exception) {
                    LOGGER.error("Malformed GLSL import {} while preprocessing Vulkan shader {}: {}", importPath, sourceName, exception.getMessage());
                    return "#error " + exception.getMessage();
                }

                if (!this.importedLocations.add(importLocation)) {
                    return null;
                }

                String includePath = "/assets/" + importLocation.getNamespace() + "/" + importLocation.getPath();
                try (java.io.InputStream includeStream = VulkanBackend.class.getResourceAsStream(includePath)) {
                    if (includeStream == null) {
                        String message = "Missing bundled GLSL include " + importLocation;
                        LOGGER.error(message);
                        return "#error " + message;
                    }

                    return new String(includeStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                } catch (java.io.IOException exception) {
                    LOGGER.error("Could not read bundled GLSL import {} while preprocessing Vulkan shader {}: {}", importLocation, sourceName, exception.getMessage());
                    return "#error " + exception.getMessage();
                }
            }
        };

        return String.join("", preprocessor.process(shaderSource));
    }

    @Nullable
    private static net.minecraft.resources.ResourceLocation tryParseShaderSourceLocation(String sourceName) {
        if (sourceName == null || sourceName.isBlank()) {
            return null;
        }

        try {
            return net.minecraft.resources.ResourceLocation.parse(sourceName);
        } catch (net.minecraft.ResourceLocationException ignored) {
            return null;
        }
    }

    public Optional<VulkanicSpirvModule> getCompiledSpirvModule(CommandContext ctx, int shader) {
        VirtualShader virtualShader = virtualShaders.get(shader);
        if (virtualShader == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(virtualShader.compiledModule);
    }

    public void uploadShaderSource(CommandContext ctx, int shader, CharSequence source) {
        VirtualShader virtualShader = requireVirtualShader(shader);
        releaseVirtualShaderNativeModule(virtualShader);
        virtualShader.source = source == null ? "" : source.toString();
        virtualShader.compiledModule = null;
        virtualShader.compileStatus = false;
        virtualShader.infoLog = "";
    }

    public void uploadShaderSource(CommandContext ctx, int shader, long pointerBufferAddress, int stringCount, long lengthsPointer) {
        uploadShaderSource(ctx, shader, decodeShaderSource(pointerBufferAddress, stringCount, lengthsPointer));
    }

    public void compileShader(CommandContext ctx, int shader) {
        VirtualShader virtualShader = requireVirtualShader(shader);
        releaseVirtualShaderNativeModule(virtualShader);

        if (virtualShader.source == null || virtualShader.source.isBlank()) {
            virtualShader.compileStatus = false;
            virtualShader.compiledModule = null;
            virtualShader.infoLog = "Shader source is empty. uploadShaderSource must be called before compileShader.";
            return;
        }

        try {
            VulkanicSpirvModule compiledModule = compileSpirvModule(
                ctx,
                virtualShader.stage,
                virtualShader.source,
                "shader-" + shader,
                "main"
            );
            virtualShader.compiledModule = compiledModule;
            virtualShader.compileStatus = true;
            virtualShader.infoLog = "";

            NativeSpine spine = nativeSpine;
            if (spine != null) {
                materializeNativeShaderModuleIfNeeded(shader, virtualShader, spine);
            }
        } catch (RuntimeException exception) {
            releaseVirtualShaderNativeModule(virtualShader);
            virtualShader.compileStatus = false;
            virtualShader.compiledModule = null;
            virtualShader.infoLog = exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
        }
    }

    public int getShaderParameter(CommandContext ctx, int shader, int pname) {
        VirtualShader virtualShader = requireVirtualShader(shader);
        if (pname == VulkanicAPI.GL_COMPILE_STATUS) {
            return virtualShader.compileStatus ? VulkanicAPI.GL_TRUE : VulkanicAPI.GL_FALSE;
        }
        return 0;
    }

    public String getShaderInfoLog(CommandContext ctx, int shader) {
        return requireVirtualShader(shader).infoLog;
    }

    public int createShaderProgram(CommandContext ctx) {
        int programId = nextVirtualProgramId.getAndIncrement();
        virtualPrograms.put(programId, new VirtualProgram());
        return programId;
    }

    public void attachShader(CommandContext ctx, int program, int shader) {
        requireVirtualShader(shader);
        VirtualProgram virtualProgram = requireVirtualProgram(program);
        virtualProgram.attachedShaderIds.add(shader);
        virtualProgram.linkStatus = false;
    }

    public void detachShader(CommandContext ctx, int program, int shader) {
        VirtualProgram virtualProgram = requireVirtualProgram(program);
        virtualProgram.attachedShaderIds.remove(shader);
        releaseVirtualShaderIfDeletionPendingAndDetached(shader);
    }

    public void linkProgram(CommandContext ctx, int program) {
        VirtualProgram virtualProgram = requireVirtualProgram(program);
        if (virtualProgram.attachedShaderIds.isEmpty()) {
            virtualProgram.linkStatus = false;
            virtualProgram.infoLog = "Program has no attached shaders.";
            virtualProgram.linkedSpirvModules = List.of();
            return;
        }

        NativeSpine spine = nativeSpine;
        reflectVirtualProgramResources(program, virtualProgram);

        List<String> issues = new ArrayList<>();
        List<VulkanicSpirvModule> linkedSpirvModules = new ArrayList<>();
        Set<VulkanicShaderStage> seenStages = new HashSet<>();
        for (int shaderId : sortedAttachedShaderIds(virtualProgram)) {
            VirtualShader virtualShader = virtualShaders.get(shaderId);
            if (virtualShader == null) {
                issues.add("Attached shader " + shaderId + " does not exist.");
                continue;
            }
            if (!virtualShader.compileStatus || virtualShader.compiledModule == null) {
                issues.add("Attached shader " + shaderId + " failed compilation: " + virtualShader.infoLog);
                continue;
            }

            if (!seenStages.add(virtualShader.stage)) {
                issues.add("Multiple shaders attached for stage " + virtualShader.stage + ".");
            }

            linkedSpirvModules.add(createLinkedProgramModule(ctx, shaderId, virtualShader, virtualProgram));

            if (spine != null) {
                try {
                    materializeNativeShaderModuleIfNeeded(shaderId, virtualShader, spine);
                } catch (RuntimeException exception) {
                    issues.add("Attached shader " + shaderId + " failed Vulkan shader-module materialization: "
                        + (exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()));
                }
            }
        }

        if (!issues.isEmpty()) {
            virtualProgram.linkStatus = false;
            virtualProgram.infoLog = String.join("\n", issues);
            virtualProgram.activeUniformNames = List.of();
            virtualProgram.activeUniforms = List.of();
            virtualProgram.activeUniformBlocks = List.of();
            virtualProgram.activeResourceBindings = List.of();
            virtualProgram.linkedSpirvModules = List.of();
            return;
        }

        virtualProgram.linkedSpirvModules = List.copyOf(linkedSpirvModules);
        virtualProgram.linkStatus = true;
        virtualProgram.infoLog = "";
    }

    private VulkanicSpirvModule createLinkedProgramModule(
        CommandContext ctx,
        int shaderId,
        VirtualShader virtualShader,
        VirtualProgram virtualProgram
    ) {
        String reboundSource = prepareVirtualShaderSourceForVulkanReflection(virtualShader);
        if (virtualShader.stage == VulkanicShaderStage.VERTEX) {
            for (Map.Entry<String, Integer> entry : virtualProgram.attributeLocationsByName.entrySet()) {
                reboundSource = injectExplicitVertexInputLocation(reboundSource, entry.getKey(), entry.getValue());
            }
        }
        reboundSource = injectExplicitReflectedResourceBindings(reboundSource, virtualProgram);

        boolean usesGeneratedStandaloneBlock = shaderHasStandaloneUniformBlockMembers(virtualShader.source);
        if (reboundSource.equals(virtualShader.source)
            && (!usesGeneratedStandaloneBlock || virtualProgram.standaloneUniformDeclarations.isEmpty())) {
            return virtualShader.compiledModule;
        }

        return compileSpirvModuleForBackend(
            virtualShader.stage,
            reboundSource,
            "shader-" + shaderId,
            "main",
            usesGeneratedStandaloneBlock ? virtualProgram.standaloneUniformDeclarations : null,
            usesGeneratedStandaloneBlock ? standaloneUniformBlockBindingIndex(virtualProgram) : -1
        );
    }

    private static String injectExplicitReflectedResourceBindings(String shaderSource, VirtualProgram virtualProgram) {
        String reboundSource = shaderSource;
        for (ReflectedResourceBinding resourceBinding : virtualProgram.activeResourceBindings) {
            if (resourceBinding.type() == PipelineDescriptor.ResourceType.UNIFORM_BUFFER) {
                reboundSource = injectExplicitUniformBlockBinding(
                    reboundSource,
                    resourceBinding.name(),
                    resourceBinding.binding()
                );
            } else if (resourceBinding.type() == PipelineDescriptor.ResourceType.SAMPLER
                || resourceBinding.type() == PipelineDescriptor.ResourceType.COMPARISON_SAMPLER) {
                reboundSource = injectExplicitNamedUniformBinding(
                    reboundSource,
                    resourceBinding.name(),
                    resourceBinding.binding()
                );
            }
        }

        return reboundSource;
    }

    private List<Integer> sortedAttachedShaderIds(VirtualProgram virtualProgram) {
        List<Integer> shaderIds = new ArrayList<>(virtualProgram.attachedShaderIds);
        shaderIds.sort((left, right) -> {
            VirtualShader leftShader = virtualShaders.get(left);
            VirtualShader rightShader = virtualShaders.get(right);
            int leftStage = leftShader == null ? Integer.MAX_VALUE : leftShader.stage.ordinal();
            int rightStage = rightShader == null ? Integer.MAX_VALUE : rightShader.stage.ordinal();
            int stageCompare = Integer.compare(leftStage, rightStage);
            return stageCompare != 0 ? stageCompare : Integer.compare(left, right);
        });
        return shaderIds;
    }

    private static String prepareVirtualShaderSourceForVulkanReflection(VirtualShader virtualShader) {
        String source = virtualShader.source;
        if (source == null || source.isBlank()) {
            return source;
        }
        return GlslangSpirvCompiler.prepareSourceForVulkanResourceReflection(virtualShader.stage, source);
    }

    private List<String> collectStandaloneUniformDeclarations(VirtualProgram virtualProgram) {
        List<String> sources = new ArrayList<>();
        for (int shaderId : sortedAttachedShaderIds(virtualProgram)) {
            VirtualShader virtualShader = virtualShaders.get(shaderId);
            if (virtualShader != null && virtualShader.source != null && !virtualShader.source.isBlank()) {
                sources.add(prepareVirtualShaderSourceForVulkanReflection(virtualShader));
            }
        }
        return collectStandaloneUniformDeclarations(sources);
    }

    private static List<String> collectStandaloneUniformDeclarations(List<String> shaderSources) {
        java.util.LinkedHashMap<String, String> declarationsByName = new java.util.LinkedHashMap<>();
        for (String shaderSource : shaderSources) {
            if (shaderSource == null || shaderSource.isBlank()) {
                continue;
            }

            String normalizedSource = stripGlslComments(shaderSource);
            Matcher uniformMatcher = GLSL_STANDALONE_UNIFORM_PATTERN.matcher(normalizedSource);
            while (uniformMatcher.find()) {
                String uniformTypeName = uniformMatcher.group(2);
                if (isOpaqueStandaloneUniformType(uniformTypeName)) {
                    continue;
                }

                String uniformName = uniformMatcher.group(3);
                int arraySize = uniformMatcher.group(4) == null ? 1 : Integer.parseInt(uniformMatcher.group(4));
                String declaration = uniformTypeName + " " + uniformName + (arraySize > 1 ? "[" + arraySize + "]" : "") + ";";
                declarationsByName.putIfAbsent(uniformName, declaration);
            }
        }
        return List.copyOf(declarationsByName.values());
    }

    private static String stripGlslComments(String shaderSource) {
        return GLSL_LINE_COMMENT_PATTERN.matcher(
            GLSL_BLOCK_COMMENT_PATTERN.matcher(shaderSource).replaceAll("")
        ).replaceAll("");
    }

    private static boolean shaderHasStandaloneUniformBlockMembers(String shaderSource) {
        if (shaderSource == null || shaderSource.isBlank()) {
            return false;
        }
        return GlslangSpirvCompiler.hasStandaloneUniformBlockMembers(stripGlslComments(shaderSource));
    }

    private static boolean isOpaqueStandaloneUniformType(String uniformTypeName) {
        return uniformTypeName.contains("sampler")
            || uniformTypeName.contains("image")
            || uniformTypeName.equals("atomic_uint");
    }

    private static int standaloneUniformBlockBindingIndex(RenderPipeline renderPipeline) {
        return renderPipeline.getSamplers().size() + renderPipeline.getUniforms().size();
    }

    private static int standaloneUniformBlockBindingIndex(VirtualProgram virtualProgram) {
        for (ReflectedResourceBinding resourceBinding : virtualProgram.activeResourceBindings) {
            if (GlslangSpirvCompiler.GENERATED_UNIFORM_BLOCK_NAME.equals(resourceBinding.name())) {
                return resourceBinding.binding();
            }
        }

        Set<DescriptorSlot> usedSlots = new java.util.LinkedHashSet<>();
        for (ReflectedResourceBinding resourceBinding : virtualProgram.activeResourceBindings) {
            usedSlots.add(new DescriptorSlot(resourceBinding.set(), resourceBinding.binding()));
        }
        return nextUnusedBinding(0, usedSlots, 0);
    }

    private static Optional<ExplicitDescriptorBinding> parseExplicitDescriptorBinding(@Nullable String layoutBody) {
        if (layoutBody == null || layoutBody.isBlank()) {
            return Optional.empty();
        }

        Matcher bindingMatcher = GLSL_LAYOUT_BINDING_PATTERN.matcher(layoutBody);
        if (!bindingMatcher.find()) {
            return Optional.empty();
        }

        int set = 0;
        Matcher setMatcher = GLSL_LAYOUT_SET_PATTERN.matcher(layoutBody);
        if (setMatcher.find()) {
            set = Integer.parseInt(setMatcher.group(1));
        }
        int binding = Integer.parseInt(bindingMatcher.group(1));
        return Optional.of(new ExplicitDescriptorBinding(set, binding));
    }

    private static List<ReflectedResourceBinding> buildReflectedResourceBindings(
        List<String> activeUniformBlocks,
        List<ReflectedUniform> activeUniforms,
        Map<String, ExplicitDescriptorBinding> explicitBindings
    ) {
        List<ReflectedResourceRequest> requests = new ArrayList<>();
        Set<String> seenNames = new java.util.LinkedHashSet<>();
        boolean hasGeneratedStandaloneUniformBlock = false;

        for (String blockName : activeUniformBlocks) {
            if (blockName == null || blockName.isBlank() || blockName.startsWith("gl_")) {
                continue;
            }
            if (!seenNames.add(blockName)) {
                continue;
            }
            if (GlslangSpirvCompiler.GENERATED_UNIFORM_BLOCK_NAME.equals(blockName)) {
                hasGeneratedStandaloneUniformBlock = true;
                continue;
            }
            requests.add(new ReflectedResourceRequest(blockName, PipelineDescriptor.ResourceType.UNIFORM_BUFFER));
        }

        for (ReflectedUniform uniform : activeUniforms) {
            Optional<net.vulkanic.VulkanicUniformReflectionType> reflectionType =
                net.vulkanic.VulkanicUniformReflectionType.fromLegacyGlConstant(uniform.legacyType());
            if (reflectionType.isEmpty() || !reflectionType.get().isSampler()) {
                continue;
            }
            String uniformName = uniform.name();
            if (uniformName == null || uniformName.isBlank() || uniformName.startsWith("gl_")) {
                continue;
            }
            if (!seenNames.add(uniformName)) {
                continue;
            }
            boolean comparisonSampler = reflectionType.get() == net.vulkanic.VulkanicUniformReflectionType.SAMPLER_1D_SHADOW
                || reflectionType.get() == net.vulkanic.VulkanicUniformReflectionType.SAMPLER_2D_SHADOW
                || reflectionType.get() == net.vulkanic.VulkanicUniformReflectionType.SAMPLER_CUBE_SHADOW;
            requests.add(new ReflectedResourceRequest(
                uniformName,
                comparisonSampler
                    ? PipelineDescriptor.ResourceType.COMPARISON_SAMPLER
                    : PipelineDescriptor.ResourceType.SAMPLER
            ));
        }

        if (hasGeneratedStandaloneUniformBlock) {
            requests.add(new ReflectedResourceRequest(
                GlslangSpirvCompiler.GENERATED_UNIFORM_BLOCK_NAME,
                PipelineDescriptor.ResourceType.UNIFORM_BUFFER
            ));
        }

        List<ReflectedResourceBinding> bindings = new ArrayList<>(requests.size());
        Set<DescriptorSlot> usedSlots = new java.util.LinkedHashSet<>();
        for (ExplicitDescriptorBinding explicitBinding : explicitBindings.values()) {
            usedSlots.add(new DescriptorSlot(explicitBinding.set(), explicitBinding.binding()));
        }
        int nextBinding = 0;

        for (ReflectedResourceRequest request : requests) {
            ExplicitDescriptorBinding explicitBinding = explicitBindings.get(request.name());
            int set = explicitBinding != null ? explicitBinding.set() : 0;
            int binding;
            if (explicitBinding != null) {
                binding = explicitBinding.binding();
            } else {
                binding = nextUnusedBinding(set, usedSlots, nextBinding);
            }

            usedSlots.add(new DescriptorSlot(set, binding));
            if (set == 0) {
                nextBinding = Math.max(nextBinding, binding + 1);
            }
            bindings.add(new ReflectedResourceBinding(request.name(), request.type(), set, binding));
        }

        return List.copyOf(bindings);
    }

    private static int nextUnusedBinding(int set, Set<DescriptorSlot> usedSlots, int startBinding) {
        int binding = Math.max(0, startBinding);
        while (usedSlots.contains(new DescriptorSlot(set, binding))) {
            binding++;
        }
        return binding;
    }

    public int getProgramParameter(CommandContext ctx, int program, int pname) {
        VirtualProgram virtualProgram = requireVirtualProgram(program);
        if (pname == VulkanicAPI.GL_LINK_STATUS) {
            return virtualProgram.linkStatus ? VulkanicAPI.GL_TRUE : VulkanicAPI.GL_FALSE;
        }
        if (pname == VulkanicAPI.GL_ACTIVE_UNIFORMS) {
            return virtualProgram.activeUniformNames.size();
        }
        if (pname == VulkanicAPI.GL_ACTIVE_UNIFORM_BLOCKS) {
            return virtualProgram.activeUniformBlocks.size();
        }
        return 0;
    }

    public String getProgramInfoLog(CommandContext ctx, int program) {
        return requireVirtualProgram(program).infoLog;
    }

    private void reflectVirtualProgramResources(int programId, VirtualProgram virtualProgram) {
        java.util.LinkedHashMap<String, ReflectedUniform> activeUniforms = new java.util.LinkedHashMap<>();
        Set<String> activeUniformNames = new java.util.LinkedHashSet<>();
        Set<String> activeUniformBlocks = new java.util.LinkedHashSet<>();
        Map<String, ExplicitDescriptorBinding> explicitBindings = new java.util.LinkedHashMap<>();
        List<String> standaloneUniformDeclarations = collectStandaloneUniformDeclarations(virtualProgram);

        for (int shaderId : sortedAttachedShaderIds(virtualProgram)) {
            VirtualShader virtualShader = virtualShaders.get(shaderId);
            if (virtualShader == null || virtualShader.source == null || virtualShader.source.isBlank()) {
                continue;
            }

            String reflectedSource = prepareVirtualShaderSourceForVulkanReflection(virtualShader);
            String normalizedSource = GLSL_LINE_COMMENT_PATTERN.matcher(
                GLSL_BLOCK_COMMENT_PATTERN.matcher(reflectedSource).replaceAll("")
            ).replaceAll("");
            Matcher blockMatcher = GLSL_UNIFORM_BLOCK_PATTERN.matcher(normalizedSource);
            while (blockMatcher.find()) {
                String blockName = blockMatcher.group(2);
                activeUniformBlocks.add(blockName);
                parseExplicitDescriptorBinding(blockMatcher.group(1))
                    .ifPresent(binding -> explicitBindings.putIfAbsent(blockName, binding));
            }
            // If this shader has standalone non-opaque uniforms, the Vulkan normalizer rewrites
            // them into VulkanicStandaloneUniforms; mirror that here so reflection stays aligned.
            if (GlslangSpirvCompiler.hasStandaloneUniformBlockMembers(normalizedSource)) {
                activeUniformBlocks.add(GlslangSpirvCompiler.GENERATED_UNIFORM_BLOCK_NAME);
            }

            Matcher uniformMatcher = GLSL_STANDALONE_UNIFORM_PATTERN.matcher(normalizedSource);
            while (uniformMatcher.find()) {
                String uniformTypeName = uniformMatcher.group(2);
                String uniformName = uniformMatcher.group(3);
                int arraySize = uniformMatcher.group(4) == null ? 1 : Integer.parseInt(uniformMatcher.group(4));

                activeUniformNames.add(uniformName);
                parseExplicitDescriptorBinding(uniformMatcher.group(1))
                    .ifPresent(binding -> explicitBindings.putIfAbsent(uniformName, binding));
                activeUniforms.putIfAbsent(
                    uniformName,
                    new ReflectedUniform(
                        uniformName,
                        arraySize,
                        net.vulkanic.VulkanicUniformReflectionType.fromGlslTypeName(uniformTypeName)
                            .map(net.vulkanic.VulkanicUniformReflectionType::toLegacyGlConstant)
                            .orElse(0)
                    )
                );
            }
        }

        virtualProgram.activeUniformNames = List.copyOf(activeUniformNames);
        virtualProgram.activeUniforms = List.copyOf(activeUniforms.values());
        virtualProgram.activeUniformBlocks = List.copyOf(activeUniformBlocks);
        virtualProgram.activeResourceBindings = buildReflectedResourceBindings(
            virtualProgram.activeUniformBlocks,
            virtualProgram.activeUniforms,
            explicitBindings
        );
        virtualProgram.standaloneUniformDeclarations = standaloneUniformDeclarations;
        logStandaloneSliceTraceInternal(programId, "reflection", null, virtualProgram, null, false, null);
        initializeStandaloneUniformState(programId, virtualProgram, activeUniforms);

    }

    private void initializeStandaloneUniformState(
        int programId,
        VirtualProgram virtualProgram,
        java.util.LinkedHashMap<String, ReflectedUniform> reflectedUniformsByName
    ) {
        Map<String, java.util.List<Integer>> offsetsByName =
            collectStandaloneUniformOffsets(virtualProgram.standaloneUniformDeclarations);
        Map<Integer, StandaloneUniformField> fieldsByLocation = new HashMap<>();
        int backingSize = 0;

        List<String> uniformNames = virtualProgram.activeUniformNames;
        for (int location = 0; location < uniformNames.size(); location++) {
            String name = uniformNames.get(location);
            ReflectedUniform reflectedUniform = reflectedUniformsByName.get(name);
            if (reflectedUniform == null) {
                continue;
            }

            Optional<net.vulkanic.VulkanicUniformReflectionType> reflectionType =
                net.vulkanic.VulkanicUniformReflectionType.fromLegacyGlConstant(reflectedUniform.legacyType());
            if (reflectionType.isEmpty() || reflectionType.get().isSampler() || reflectionType.get().isImage()) {
                continue;
            }

            int typeSize = std140TypeSize(reflectionType.get());
            int arraySize = Math.max(1, reflectedUniform.arraySize());
            int stride = arraySize > 1 ? roundUpTo(typeSize, 16) : typeSize;
            java.util.List<Integer> offsets = offsetsByName.get(name);
            if (offsets == null || offsets.isEmpty()) {
                continue;
            }

            fieldsByLocation.put(
                location,
                new StandaloneUniformField(
                    name,
                    reflectionType.get(),
                    offsets.stream().mapToInt(Integer::intValue).toArray(),
                    arraySize,
                    stride
                )
            );
            for (int offset : offsets) {
                backingSize = Math.max(backingSize, offset + (stride * arraySize));
            }
        }

        backingSize = roundUpTo(backingSize, 16);
        synchronized (virtualProgram) {
            unregisterUniformLocationTokens(virtualProgram);
            virtualProgram.standaloneFieldsByLocation = Map.copyOf(fieldsByLocation);
            virtualProgram.standaloneBackingSize = backingSize;
            virtualProgram.standaloneBackingData =
                backingSize > 0 ? java.nio.ByteBuffer.allocate(backingSize).order(ByteOrder.nativeOrder()) : null;
            if (virtualProgram.standaloneBackingData != null) {
                initializeStandaloneUniformDefaults(
                    virtualProgram.standaloneFieldsByLocation,
                    virtualProgram.standaloneBackingData
                );
            }
            virtualProgram.standaloneDirty = backingSize > 0;
            virtualProgram.closeStandaloneUniformBacking();
        }
        logStandaloneSliceTraceInternal(programId, "backing-init", null, virtualProgram, null, false, null);
    }

    private static void initializeStandaloneUniformDefaults(
        Map<Integer, StandaloneUniformField> fieldsByLocation,
        java.nio.ByteBuffer backingData
    ) {
        float[] identity4 = new float[] {
            1.0F, 0.0F, 0.0F, 0.0F,
            0.0F, 1.0F, 0.0F, 0.0F,
            0.0F, 0.0F, 1.0F, 0.0F,
            0.0F, 0.0F, 0.0F, 1.0F
        };
        float[] identity3 = new float[] {
            1.0F, 0.0F, 0.0F,
            0.0F, 1.0F, 0.0F,
            0.0F, 0.0F, 1.0F
        };

        for (StandaloneUniformField field : fieldsByLocation.values()) {
            for (int offset : field.offsets()) {
                switch (field.type()) {
                    case FLOAT_MAT4 -> writeFloatUniformAtOffset(field, backingData, offset, identity4);
                    case FLOAT_MAT3 -> writeFloatUniformAtOffset(field, backingData, offset, identity3);
                    case FLOAT -> {
                        if (isOneDefaultStandaloneFloat(field.name())) {
                            backingData.putFloat(offset, 1.0F);
                        }
                    }
                    case FLOAT_VEC2 -> {
                        if ("u_TexCoordShrink".equals(field.name())) {
                            backingData.putFloat(offset, 1.0F);
                            backingData.putFloat(offset + 4, 1.0F);
                        }
                    }
                    default -> {
                    }
                }
            }
        }
    }

    private static boolean isOneDefaultStandaloneFloat(String name) {
        return "iris_FogEnd".equals(name)
            || "far".equals(name)
            || "viewWidth".equals(name)
            || "viewHeight".equals(name)
            || "aspectRatio".equals(name);
    }

    static Map<String, java.util.List<Integer>> collectStandaloneUniformOffsets(List<String> standaloneUniformDeclarations) {
        Map<String, java.util.List<Integer>> offsetsByName = new java.util.LinkedHashMap<>();
        int offset = 0;
        for (String declaration : standaloneUniformDeclarations) {
            Matcher uniformMatcher = GLSL_STANDALONE_UNIFORM_MEMBER_PATTERN.matcher(declaration);
            if (!uniformMatcher.matches()) {
                continue;
            }

            String uniformTypeName = uniformMatcher.group(1);
            String uniformName = uniformMatcher.group(2);
            int arraySize = uniformMatcher.group(3) == null ? 1 : Integer.parseInt(uniformMatcher.group(3));
            Optional<net.vulkanic.VulkanicUniformReflectionType> reflectionType =
                net.vulkanic.VulkanicUniformReflectionType.fromGlslTypeName(uniformTypeName);
            if (reflectionType.isEmpty() || reflectionType.get().isSampler() || reflectionType.get().isImage()) {
                continue;
            }

            int baseAlignment = std140BaseAlignment(reflectionType.get());
            int typeSize = std140TypeSize(reflectionType.get());
            int stride = Math.max(1, arraySize) > 1 ? roundUpTo(typeSize, 16) : typeSize;
            offset = roundUpTo(offset, baseAlignment);
            offsetsByName.put(uniformName, List.of(offset));
            offset += stride * Math.max(1, arraySize);
        }

        return offsetsByName;
    }

    public void logStandaloneSliceTrace(
        CommandContext ctx,
        String stage,
        int program,
        @Nullable String programName,
        @Nullable String note
    ) {
        if (!VulkanicAPI.isStandaloneUniformTracingEnabled()) {
            return;
        }
        requireVulkanCommandBufferHandle("logStandaloneSliceTrace", ctx);
        logStandaloneSliceTraceInternal(program, stage, programName, virtualPrograms.get(program), null, true, note);
    }

    private void logStandaloneSliceTraceInternal(
        int programId,
        String stage,
        @Nullable String programName,
        @Nullable VirtualProgram virtualProgram,
        @Nullable Boolean sliceAvailableOverride,
        boolean dedupe,
        @Nullable String note
    ) {
        if (!VulkanicAPI.shouldTraceStandaloneUniforms()) {
            return;
        }
        if (STANDALONE_SLICE_TRACE_LOG_COUNT.get() >= MAX_STANDALONE_SLICE_TRACE_LOGS) {
            return;
        }

        String traceKey = stage + "#" + programId;
        if (dedupe && !STANDALONE_SLICE_TRACE_KEYS.add(traceKey)) {
            return;
        }

        boolean programRecordPresent = virtualProgram != null;
        boolean reflectedStandaloneBlockPresent = programRecordPresent
            && virtualProgram.activeUniformBlocks.contains(GlslangSpirvCompiler.GENERATED_UNIFORM_BLOCK_NAME);
        boolean backingAllocated = programRecordPresent
            && virtualProgram.standaloneBackingSize > 0
            && virtualProgram.standaloneBackingData != null;
        boolean sliceAvailable = sliceAvailableOverride != null ? sliceAvailableOverride : backingAllocated;

        String reason;
        if (!programRecordPresent) {
            reason = "program-record-missing";
        } else if (!reflectedStandaloneBlockPresent) {
            reason = "standalone-block-not-reflected";
        } else if (virtualProgram.standaloneBackingSize <= 0) {
            reason = "backing-size-zero";
        } else if (virtualProgram.standaloneBackingData == null) {
            reason = "backing-data-missing";
        } else if (!sliceAvailable) {
            reason = "slice-lookup-returned-null";
        } else {
            reason = "available";
        }

        if (STANDALONE_SLICE_TRACE_LOG_COUNT.incrementAndGet() <= MAX_STANDALONE_SLICE_TRACE_LOGS) {
            LOGGER.info(
                "StandaloneSliceTrace stage={} programId={} programName={} programRecordPresent={} reflectedStandaloneBlockPresent={} backingAllocated={} sliceAvailable={} activeUniformBlockCount={} standaloneFieldCount={} backingSize={} gpuBufferPresent={} dirty={} reason={}{}",
                stage,
                programId,
                programName == null ? "unknown" : programName,
                yesNo(programRecordPresent),
                yesNo(reflectedStandaloneBlockPresent),
                yesNo(backingAllocated),
                yesNo(sliceAvailable),
                programRecordPresent ? virtualProgram.activeUniformBlocks.size() : 0,
                programRecordPresent ? virtualProgram.standaloneFieldsByLocation.size() : 0,
                programRecordPresent ? virtualProgram.standaloneBackingSize : 0,
                yesNo(programRecordPresent && virtualProgram.standaloneGpuBuffer != null && !virtualProgram.standaloneGpuBuffer.isClosed()),
                yesNo(programRecordPresent && virtualProgram.standaloneDirty),
                reason,
                note == null ? "" : " note=" + note
            );
        }
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private void logStandaloneProgramKeyTrace(String stage, int programId, @Nullable VirtualProgram virtualProgram, @Nullable String note) {
        if (!VulkanicAPI.shouldTraceStandaloneUniforms()) {
            return;
        }
        java.util.ArrayList<Integer> keys = new java.util.ArrayList<>(virtualPrograms.keySet());
        java.util.Collections.sort(keys);
        LOGGER.info(
            "StandaloneLookupKeyTrace stage={} lookupType=program-id key={} keyHash={} mapSize={} containsExactKey={} nearestEntries={} lookupUses=program-id-int storeContainer=virtualPrograms virtualProgramIdentity={} note={}",
            stage,
            programId,
            Integer.toHexString(Integer.hashCode(programId)),
            virtualPrograms.size(),
            yesNo(virtualPrograms.containsKey(programId)),
            describeNearestProgramKeys(keys, programId),
            virtualProgram != null ? System.identityHashCode(virtualProgram) : -1,
            note == null ? "none" : note
        );
    }

    private static String describeNearestProgramKeys(java.util.List<Integer> keys, int target) {
        if (keys.isEmpty()) {
            return "[]";
        }

        int insertionPoint = java.util.Collections.binarySearch(keys, target);
        if (insertionPoint < 0) {
            insertionPoint = -insertionPoint - 1;
        }

        int from = Math.max(0, insertionPoint - 3);
        int to = Math.min(keys.size(), insertionPoint + 4);
        return keys.subList(from, to).toString();
    }

    private void unregisterUniformLocationTokens(VirtualProgram virtualProgram) {
        for (Integer token : virtualProgram.uniformLocationTokensByName.values()) {
            uniformLocationRefs.remove(token);
        }
        virtualProgram.uniformLocationTokensByName.clear();
    }

    private int resolveUniformLocationToken(int programId, VirtualProgram virtualProgram, String uniformName, int uniformIndex) {
        Integer existing = virtualProgram.uniformLocationTokensByName.get(uniformName);
        if (existing != null) {
            return existing;
        }

        int token = nextVirtualUniformLocationToken.getAndIncrement();
        Integer raced = virtualProgram.uniformLocationTokensByName.putIfAbsent(uniformName, token);
        if (raced != null) {
            return raced;
        }

        uniformLocationRefs.put(token, new UniformLocationRef(programId, uniformIndex));
        return token;
    }

    @Nullable
    private UniformLocationRef resolveUniformLocationRef(int location) {
        if (location < 0) {
            return null;
        }
        return uniformLocationRefs.get(location);
    }

    private static int roundUpTo(int value, int alignment) {
        if (alignment <= 0) {
            return value;
        }
        int remainder = value % alignment;
        return remainder == 0 ? value : value + (alignment - remainder);
    }

    private static int std140BaseAlignment(net.vulkanic.VulkanicUniformReflectionType type) {
        return switch (type) {
            case FLOAT, INT, UINT, BOOL -> 4;
            case FLOAT_VEC2, INT_VEC2, UINT_VEC2, BOOL_VEC2 -> 8;
            case FLOAT_VEC3, INT_VEC3, UINT_VEC3, BOOL_VEC3,
                FLOAT_VEC4, INT_VEC4, UINT_VEC4, BOOL_VEC4,
                FLOAT_MAT2, FLOAT_MAT3, FLOAT_MAT4 -> 16;
            default -> 16;
        };
    }

    private static int std140TypeSize(net.vulkanic.VulkanicUniformReflectionType type) {
        return switch (type) {
            case FLOAT, INT, UINT, BOOL -> 4;
            case FLOAT_VEC2, INT_VEC2, UINT_VEC2, BOOL_VEC2 -> 8;
            case FLOAT_VEC3, INT_VEC3, UINT_VEC3, BOOL_VEC3 -> 12;
            case FLOAT_VEC4, INT_VEC4, UINT_VEC4, BOOL_VEC4 -> 16;
            case FLOAT_MAT2 -> 32;
            case FLOAT_MAT3 -> 48;
            case FLOAT_MAT4 -> 64;
            default -> 16;
        };
    }

    public void deleteShader(CommandContext ctx, int shader) {
        VirtualShader virtualShader = virtualShaders.get(shader);
        if (virtualShader == null) {
            return;
        }
        virtualShader.deletionPending = true;
        releaseVirtualShaderIfDeletionPendingAndDetached(shader);
    }

    public void deleteProgram(CommandContext ctx, int program) {
        VirtualProgram removedProgram = virtualPrograms.remove(program);
        if (removedProgram != null) {
            unregisterUniformLocationTokens(removedProgram);
            removedProgram.closeStandaloneUniformBacking();
            for (int shader : removedProgram.attachedShaderIds) {
                releaseVirtualShaderIfDeletionPendingAndDetached(shader);
            }
        }
        if (boundVirtualProgram == program) {
            boundVirtualProgram = 0;
        }
    }

    private boolean isShaderAttachedToAnyProgram(int shader) {
        for (VirtualProgram virtualProgram : virtualPrograms.values()) {
            if (virtualProgram.attachedShaderIds.contains(shader)) {
                return true;
            }
        }
        return false;
    }

    private void releaseVirtualShaderIfDeletionPendingAndDetached(int shader) {
        VirtualShader virtualShader = virtualShaders.get(shader);
        if (virtualShader == null || !virtualShader.deletionPending || isShaderAttachedToAnyProgram(shader)) {
            return;
        }

        VirtualShader removedShader = virtualShaders.remove(shader);
        if (removedShader != null) {
            releaseVirtualShaderNativeModule(removedShader);
        }
    }

    public VulkanicBuffer createManagedBuffer(java.util.function.Supplier<String> label, int usage, int size) {
        ensureNativeReady("createManagedBuffer");
        if (size <= 0) {
            throw new IllegalArgumentException("Buffer size must be greater than zero, got: " + size);
        }

        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }

        return spine.createManagedBuffer(label == null ? null : label.get(), usage, size, null);
    }

    public VulkanicBuffer createManagedBuffer(java.util.function.Supplier<String> label,
                                              int usage,
                                              java.nio.ByteBuffer initialData) {
        ensureNativeReady("createManagedBuffer");
        if (initialData == null || !initialData.hasRemaining()) {
            throw new IllegalArgumentException("initialData must be non-null and have remaining bytes");
        }

        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }

        java.nio.ByteBuffer initialDataCopy = initialData.duplicate();
        return spine.createManagedBuffer(label == null ? null : label.get(), usage, initialDataCopy.remaining(), initialDataCopy);
    }

    public VulkanicBuffer.MappedView mapManagedBuffer(VulkanicBuffer buffer, boolean read, boolean write) {
        ensureNativeReady("mapManagedBuffer");
        if (!read && !write) {
            throw new IllegalArgumentException("At least one of read or write must be true");
        }

        if (!(buffer instanceof VulkanBuffer vulkanBuffer)) {
            throw new IllegalArgumentException("Expected VulkanBuffer, got: " + (buffer == null ? "null" : buffer.getClass().getName()));
        }
        if (vulkanBuffer.isClosed()) {
            throw new IllegalStateException("Cannot map a closed VulkanBuffer");
        }

        if (read && (vulkanBuffer.usage() & VulkanicBuffer.USAGE_MAP_READ) == 0) {
            throw new IllegalArgumentException("Buffer was not created with USAGE_MAP_READ");
        }
        if (write && (vulkanBuffer.usage() & VulkanicBuffer.USAGE_MAP_WRITE) == 0) {
            throw new IllegalArgumentException("Buffer was not created with USAGE_MAP_WRITE");
        }

        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }

        return spine.mapManagedBuffer(vulkanBuffer, read, write);
    }

    public int createBuffer(CommandContext ctx) {
        ensureNativeReady("createBuffer");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        return spine.createLegacyBuffer();
    }

    public void createBuffers(CommandContext ctx, int[] buffers) {
        if (buffers == null) {
            throw new IllegalArgumentException("buffers must not be null");
        }
        ensureNativeReady("createBuffers");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        spine.createLegacyBuffers(buffers);
    }

    public void deleteBuffer(CommandContext ctx, int buffer) {
        ensureNativeReady("deleteBuffer");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        spine.deleteLegacyBuffer(buffer);
    }

    public void bindBuffer(CommandContext ctx, int target, int buffer) {
        ensureNativeReady("bindBuffer");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        spine.bindLegacyBuffer(target, buffer);
    }

    public void bindBuffer(CommandContext ctx, VulkanicBufferTarget target, int buffer) {
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        bindBuffer(ctx, target.toLegacyGlTarget(), buffer);
    }

    public void bufferData(CommandContext ctx, int target, java.nio.ByteBuffer data, int usage) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        ensureNativeReady("bufferData");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        spine.bufferDataByTarget(target, data.duplicate(), usage);
    }

    public void bufferData(CommandContext ctx, int target, long size, int usage) {
        if (size < 0L || size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("size must be in [0, " + Integer.MAX_VALUE + "], got: " + size);
        }
        ensureNativeReady("bufferData");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        spine.bufferDataByTarget(target, (int) size, usage);
    }

    public void bufferData(CommandContext ctx, int target, float[] data, int usage) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        java.nio.ByteBuffer byteBuffer = java.nio.ByteBuffer.allocateDirect(data.length * Float.BYTES)
            .order(ByteOrder.nativeOrder());
        byteBuffer.asFloatBuffer().put(data);
        bufferData(ctx, target, byteBuffer, usage);
    }

    public void bufferData(CommandContext ctx, int target, int[] data, int usage) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        java.nio.ByteBuffer byteBuffer = java.nio.ByteBuffer.allocateDirect(data.length * Integer.BYTES)
            .order(ByteOrder.nativeOrder());
        byteBuffer.asIntBuffer().put(data);
        bufferData(ctx, target, byteBuffer, usage);
    }

    public void bufferSubData(CommandContext ctx, int target, long offset, java.nio.ByteBuffer data) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        ensureNativeReady("bufferSubData");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        spine.bufferSubDataByTarget(target, offset, data.duplicate());
    }

    public void bufferStorage(CommandContext ctx, int target, long size, int flags) {
        if (size < 0L || size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("size must be in [0, " + Integer.MAX_VALUE + "], got: " + size);
        }
        ensureNativeReady("bufferStorage");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        spine.bufferStorageByTarget(target, (int) size, flags);
    }

    public void bufferStorage(CommandContext ctx, int target, java.nio.ByteBuffer data, int flags) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        ensureNativeReady("bufferStorage");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        spine.bufferStorageByTarget(target, data.duplicate(), flags);
    }

    public void copyBufferSubData(CommandContext ctx, int readTarget, int writeTarget, long readOffset, long writeOffset, long size) {
        ensureNativeReady("copyBufferSubData");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        spine.copyBufferSubDataByTarget(readTarget, writeTarget, readOffset, writeOffset, size);
    }

    public java.nio.ByteBuffer mapBuffer(CommandContext ctx, int target, long offset, long length, int access) {
        ensureNativeReady("mapBuffer");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        return spine.mapBufferByTarget(target, offset, length, access);
    }

    public void unmapBuffer(CommandContext ctx, int target) {
        ensureNativeReady("unmapBuffer");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        spine.unmapBufferByTarget(target);
    }

    public void flushMappedBufferRange(CommandContext ctx, int target, long offset, long length) {
        ensureNativeReady("flushMappedBufferRange");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        spine.flushMappedBufferRangeByTarget(target, offset, length);
    }

    public int createBufferDSA(CommandContext ctx) {
        return createBuffer(ctx);
    }

    public void namedBufferDataDSA(CommandContext ctx, int buffer, long size, int usage) {
        if (size < 0L || size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("size must be in [0, " + Integer.MAX_VALUE + "], got: " + size);
        }
        ensureNativeReady("namedBufferDataDSA");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        spine.namedBufferData((int) buffer, (int) size, usage);
    }

    public void namedBufferDataDSA(CommandContext ctx, int buffer, java.nio.ByteBuffer data, int usage) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        ensureNativeReady("namedBufferDataDSA");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        spine.namedBufferData((int) buffer, data.duplicate(), usage);
    }

    public void namedBufferSubDataDSA(CommandContext ctx, int buffer, long offset, java.nio.ByteBuffer data) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        ensureNativeReady("namedBufferSubDataDSA");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        spine.namedBufferSubData(buffer, offset, data.duplicate());
    }

    public void namedBufferStorageDSA(CommandContext ctx, int buffer, long size, int flags) {
        if (size < 0L || size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("size must be in [0, " + Integer.MAX_VALUE + "], got: " + size);
        }
        ensureNativeReady("namedBufferStorageDSA");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        spine.namedBufferStorage(buffer, (int) size, flags);
    }

    public void namedBufferStorageDSA(CommandContext ctx, int buffer, java.nio.ByteBuffer data, int flags) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        ensureNativeReady("namedBufferStorageDSA");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        spine.namedBufferStorage(buffer, data.duplicate(), flags);
    }

    public java.nio.ByteBuffer mapNamedBufferRangeDSA(CommandContext ctx, int buffer, long offset, long length, int access) {
        ensureNativeReady("mapNamedBufferRangeDSA");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        return spine.mapNamedBufferRange(buffer, offset, length, access);
    }

    public void unmapNamedBufferDSA(CommandContext ctx, int buffer) {
        ensureNativeReady("unmapNamedBufferDSA");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        spine.unmapNamedBuffer(buffer);
    }

    public void flushMappedNamedBufferRangeDSA(CommandContext ctx, int buffer, long offset, long length) {
        ensureNativeReady("flushMappedNamedBufferRangeDSA");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        spine.flushMappedNamedBufferRange(buffer, offset, length);
    }

    public void copyNamedBufferSubDataDSA(CommandContext ctx, int readBuffer, int writeBuffer, long readOffset, long writeOffset, long size) {
        ensureNativeReady("copyNamedBufferSubDataDSA");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        spine.copyNamedBufferSubData(readBuffer, writeBuffer, readOffset, writeOffset, size);
    }

    public VulkanicTexture createManagedTexture(String label, int usage, VulkanicTextureFormat format,
                                                 int width, int height, int depthOrLayers, int mipLevels) {
        // Argument validation runs before ensureNativeReady so callers always get
        // IllegalArgumentException for bad parameters regardless of runtime state.
        if (format == null) {
            throw new IllegalArgumentException("format must not be null");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Texture dimensions must be > 0, got " + width + "x" + height);
        }
        if (mipLevels < 1) {
            throw new IllegalArgumentException("mipLevels must be >= 1, got " + mipLevels);
        }
        if (depthOrLayers < 1) {
            throw new IllegalArgumentException("depthOrLayers must be >= 1, got " + depthOrLayers);
        }
        validateCubemapLayerCount(usage, depthOrLayers, "createManagedTexture");
        ensureNativeReady("createManagedTexture");

        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }

        return spine.createManagedTexture(label, usage, format, width, height, depthOrLayers, mipLevels);
    }

    public VulkanicTextureView createManagedTextureView(VulkanicTexture texture) {
        return createManagedTextureView(texture, 0, texture == null ? 1 : texture.getMipLevels());
    }

    public VulkanicTextureView createManagedTextureView(VulkanicTexture texture, int baseMipLevel, int mipLevelCount) {
        ensureNativeReady("createManagedTextureView");
        if (texture == null) {
            throw new IllegalArgumentException("texture must not be null");
        }
        if (baseMipLevel < 0 || mipLevelCount < 1 || baseMipLevel + mipLevelCount > texture.getMipLevels()) {
            throw new IllegalArgumentException(
                "Invalid mip range [" + baseMipLevel + ", " + (baseMipLevel + mipLevelCount)
                    + ") for texture with " + texture.getMipLevels() + " mip levels"
            );
        }
        if (texture.isClosed()) {
            throw new IllegalStateException("Cannot create a view of a closed texture");
        }

        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }

        if (texture instanceof VulkanTexture vulkanTexture) {
            return spine.createManagedTextureView(vulkanTexture, baseMipLevel, mipLevelCount);
        }

        int legacyTextureHandle = resolveTextureHandle(getCurrentCommandContext(), texture);
        if (legacyTextureHandle <= 0) {
            throw new IllegalArgumentException("Cannot resolve legacy texture handle for texture view creation: "
                + texture.getClass().getName());
        }

        return spine.createManagedTextureViewForLegacyTexture(texture, legacyTextureHandle, baseMipLevel, mipLevelCount);
    }

    @Nullable
    public VulkanicTextureView createManagedLegacyTextureView(int legacyTextureHandle) {
        ensureNativeReady("createManagedLegacyTextureView");
        if (legacyTextureHandle <= 0) {
            return null;
        }

        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }

        NativeSpine.LegacyTextureObject legacyTexture = spine.legacyTextures.get(legacyTextureHandle);
        if (legacyTexture == null || legacyTexture.imageHandle == VK10.VK_NULL_HANDLE) {
            return null;
        }

        int mipLevels = Math.max(1, legacyTexture.mipLevels);
        int width = Math.max(1, legacyTexture.width);
        int height = Math.max(1, legacyTexture.height);
        int depthOrLayers = NativeSpine.legacyTextureLayerCount(legacyTexture);
        VulkanicTextureFormat format;
        try {
            format = NativeSpine.wrappedTextureFormatForVkFormat(legacyTexture.vkFormat);
        } catch (IllegalArgumentException exception) {
            int vkFormat = legacyTexture.vkFormat;
            if (LEGACY_SAMPLER_UNSUPPORTED_FORMAT_LOGS.add(vkFormat)) {
                LOGGER.warn(
                    "Skipping Vulkan legacy sampler view recovery for texture {} with unsupported VkFormat 0x{}; leaving the custom-pass sampler unbound.",
                    legacyTextureHandle,
                    Integer.toHexString(vkFormat)
                );
            }
            return null;
        }
        String label = "Legacy texture " + legacyTextureHandle;

        VulkanicTexture legacyTextureWrapper = new VulkanicTexture() {
            @Override
            public int getWidth(int mipLevel) {
                return Math.max(1, width >> Math.max(0, mipLevel));
            }

            @Override
            public int getHeight(int mipLevel) {
                return Math.max(1, height >> Math.max(0, mipLevel));
            }

            @Override
            public int getMipLevels() {
                return mipLevels;
            }

            @Override
            public int getDepthOrLayers() {
                return Math.max(1, depthOrLayers);
            }

            @Override
            public VulkanicTextureFormat getVulkanicFormat() {
                return format;
            }

            @Override
            public int usage() {
                return VulkanicTexture.USAGE_TEXTURE_BINDING | VulkanicTexture.USAGE_RENDER_ATTACHMENT;
            }

            @Override
            public String getLabel() {
                return label;
            }

            @Override
            public boolean isClosed() {
                return false;
            }

            @Override
            public void close() {
            }

            public int getGlHandle() {
                return legacyTextureHandle;
            }
        };

        return spine.createManagedTextureViewForLegacyTexture(legacyTextureWrapper, legacyTextureHandle, 0, mipLevels);
    }

    public void setActiveTextureUnit(CommandContext ctx, int unit) {
        requireVulkanCommandBufferHandle("setActiveTextureUnit", ctx);
        ensureNativeReady("setActiveTextureUnit");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        spine.setActiveTextureUnit(unit);
    }

    private static boolean isLegacyCubemapFaceTarget(int target) {
        return target >= 0x8515 && target <= 0x851A;
    }

    private static boolean isCubemapCompatibleUsage(int usage) {
        return (usage & VulkanicTexture.USAGE_CUBEMAP_COMPATIBLE) != 0;
    }

    private static boolean isLegacyCubemapTarget(int target) {
        return target == VulkanicAPI.GL_TEXTURE_CUBE_MAP || isLegacyCubemapFaceTarget(target);
    }

    private static void validateCubemapLayerCount(int usage, int depthOrLayers, String operation) {
        if (!isCubemapCompatibleUsage(usage)) {
            return;
        }
        if (depthOrLayers < 6 || depthOrLayers % 6 != 0) {
            throw new IllegalArgumentException(
                operation + " requires cubemap-compatible textures to use a positive multiple of 6 layers, got: "
                    + depthOrLayers
            );
        }
    }

    private static boolean isSupportedLegacyTextureTarget(int target) {
        return target == VulkanicAPI.GL_TEXTURE_1D
            || target == VulkanicAPI.GL_TEXTURE_2D
            || target == VulkanicAPI.GL_TEXTURE_3D
            || target == VulkanicAPI.GL_TEXTURE_RECTANGLE
            || target == VulkanicAPI.GL_PROXY_TEXTURE_2D
            || target == VulkanicAPI.GL_TEXTURE_CUBE_MAP
            || isLegacyCubemapFaceTarget(target);
    }

    private static boolean isSupportedLegacyTextureBindTarget(int target) {
        return target == VulkanicAPI.GL_TEXTURE_1D
            || target == VulkanicAPI.GL_TEXTURE_2D
            || target == VulkanicAPI.GL_TEXTURE_3D
            || target == VulkanicAPI.GL_TEXTURE_BUFFER
            || target == VulkanicAPI.GL_TEXTURE_RECTANGLE
            || target == VulkanicAPI.GL_PROXY_TEXTURE_2D
            || target == VulkanicAPI.GL_TEXTURE_CUBE_MAP;
    }

    private static boolean isSupportedLegacyTextureCreateTarget(int target) {
        return target == VulkanicAPI.GL_TEXTURE_1D
            || target == VulkanicAPI.GL_TEXTURE_2D
            || target == VulkanicAPI.GL_TEXTURE_3D
            || target == VulkanicAPI.GL_TEXTURE_RECTANGLE
            || target == VulkanicAPI.GL_TEXTURE_CUBE_MAP;
    }

    public void bindTexture2D(CommandContext ctx, int textureId) {
        bindTexture(ctx, VulkanicAPI.GL_TEXTURE_2D, textureId);
    }

    public void bindTexture(CommandContext ctx, int target, int textureId) {
        requireVulkanCommandBufferHandle("bindTexture", ctx);
        if (!isSupportedLegacyTextureBindTarget(target)) {
            throw new IllegalArgumentException(
                "Vulkan legacy texture path currently supports GL_TEXTURE_1D/GL_TEXTURE_2D/GL_TEXTURE_3D/"
                    + "GL_TEXTURE_BUFFER/GL_TEXTURE_RECTANGLE/GL_PROXY_TEXTURE_2D/GL_TEXTURE_CUBE_MAP bind targets, got: "
                    + target
            );
        }
        if (textureId < 0) {
            throw new IllegalArgumentException("textureId must be >= 0, got: " + textureId);
        }

        ensureNativeReady("bindTexture");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        spine.bindLegacyTexture(target, textureId);
    }

    public void bindTexture(CommandContext ctx, net.vulkanic.VulkanicTextureTarget target, int textureId) {
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        bindTexture(ctx, target.toLegacyGlTarget(), textureId);
    }

    public void bindTextureUnit(CommandContext ctx, int unit, int texture) {
        requireVulkanCommandBufferHandle("bindTextureUnit", ctx);
        if (unit < 0) {
            throw new IllegalArgumentException("unit must be >= 0, got: " + unit);
        }
        if (texture < 0) {
            throw new IllegalArgumentException("texture must be >= 0, got: " + texture);
        }

        ensureNativeReady("bindTextureUnit");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        spine.bindLegacyTextureUnit(unit, texture);
    }

    public int createTexture2D(CommandContext ctx) {
        return createTextures(ctx, VulkanicAPI.GL_TEXTURE_2D);
    }

    public int createTextures(CommandContext ctx, int target) {
        requireVulkanCommandBufferHandle("createTextures", ctx);
        if (!isSupportedLegacyTextureCreateTarget(target)) {
            throw new IllegalArgumentException(
                "Vulkan legacy texture path currently supports GL_TEXTURE_1D/GL_TEXTURE_2D/GL_TEXTURE_3D/"
                    + "GL_TEXTURE_RECTANGLE/GL_TEXTURE_CUBE_MAP createTextures targets, got: "
                    + target
            );
        }

        ensureNativeReady("createTextures");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        return spine.createLegacyTexture(target);
    }

    public void deleteTexture(CommandContext ctx, int texture) {
        requireVulkanCommandBufferHandle("deleteTexture", ctx);
        if (texture < 0) {
            throw new IllegalArgumentException("texture must be >= 0, got: " + texture);
        }

        releaseImplicitFramebuffersForTexture(texture);

        ensureNativeReady("deleteTexture");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        spine.deleteLegacyTexture(texture);
    }

    public void setTextureParameter(CommandContext ctx, int target, int pname, int param) {
        texParameteri(ctx, target, pname, param);
    }

    public int getTexParameteri(CommandContext ctx, int target, int pname) {
        requireVulkanCommandBufferHandle("getTexParameteri", ctx);
        if (!isSupportedLegacyTextureTarget(target)) {
            throw new IllegalArgumentException("Unsupported legacy Vulkan texture target for getTexParameteri: " + target);
        }

        ensureNativeReady("getTexParameteri");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        return spine.getLegacyTextureParameter(target, pname);
    }

    public void texParameterf(CommandContext ctx, int target, int pname, float param) {
        texParameteri(ctx, target, pname, Math.round(param));
    }

    public void texParameteri(CommandContext ctx, int target, int pname, int param) {
        requireVulkanCommandBufferHandle("texParameteri", ctx);
        if (!isSupportedLegacyTextureTarget(target)) {
            throw new IllegalArgumentException("Unsupported legacy Vulkan texture target for texParameteri: " + target);
        }

        ensureNativeReady("texParameteri");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        spine.setLegacyTextureParameter(target, pname, param);
    }

    public void texParameteri(CommandContext ctx,
                              net.vulkanic.VulkanicTextureTarget target,
                              net.vulkanic.VulkanicTextureParameterName pname,
                              int param) {
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        if (pname == null) {
            throw new IllegalArgumentException("pname must not be null");
        }
        texParameteri(ctx, target.toLegacyGlTarget(), pname.toLegacyGlPName(), param);
    }

    public void setPixelStore(CommandContext ctx, int pname, int value) {
        requireVulkanCommandBufferHandle("setPixelStore", ctx);
        ensureNativeReady("setPixelStore");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        spine.setPixelStore(pname, value);
    }

    public int getTextureLevelParameter(CommandContext ctx, int target, int level, int pname) {
        requireVulkanCommandBufferHandle("getTextureLevelParameter", ctx);
        if (!isSupportedLegacyTextureTarget(target)) {
            throw new IllegalArgumentException("Unsupported legacy Vulkan texture target for getTextureLevelParameter: " + target);
        }
        if (level < 0) {
            throw new IllegalArgumentException("level must be >= 0, got: " + level);
        }

        ensureNativeReady("getTextureLevelParameter");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        return spine.getTextureLevelParameter(target, level, pname);
    }

    public void uploadTexture2D(CommandContext ctx,
                                int target,
                                int level,
                                int internalFormat,
                                int width,
                                int height,
                                int border,
                                int format,
                                int type,
                                java.nio.ByteBuffer pixels) {
        long commandBufferHandle = requireVulkanCommandBufferHandle("uploadTexture2D", ctx);
        if (!isSupportedLegacyTextureTarget(target)) {
            throw new IllegalArgumentException(
                "Vulkan legacy texture path currently supports only GL_TEXTURE_2D/GL_PROXY_TEXTURE_2D"
                    + " (plus cubemap targets), got: " + target);
        }
        if (level < 0) {
            throw new IllegalArgumentException("level must be >= 0, got: " + level);
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("uploadTexture2D requires width/height > 0, got " + width + "x" + height);
        }
        if (border != 0) {
            throw new IllegalArgumentException("uploadTexture2D requires border == 0, got: " + border);
        }

        if (pixels == null) {
            LegacyTextureFormatInfo.resolveStorage(internalFormat, format, type);
        } else {
            LegacyTextureFormatInfo.resolve(internalFormat, format, type);
        }

        ensureNativeReady("uploadTexture2D");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        spine.uploadLegacyTexture2D(commandBufferHandle,
            target,
            level,
            internalFormat,
            width,
            height,
            format,
            type,
            pixels == null ? null : pixels.duplicate());
    }

    public void uploadTexture2D(
        CommandContext ctx,
        net.vulkanic.VulkanicTextureTarget target,
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

    public void uploadTexture2DSubImage(CommandContext ctx,
                                        int target,
                                        int level,
                                        int xOffset,
                                        int yOffset,
                                        int width,
                                        int height,
                                        int format,
                                        int type,
                                        long pixels) {
        long commandBufferHandle = requireVulkanCommandBufferHandle("uploadTexture2DSubImage", ctx);
        if (!isSupportedLegacyTextureTarget(target)) {
            throw new IllegalArgumentException("Unsupported legacy Vulkan texture upload target: " + target);
        }
        if (level < 0 || xOffset < 0 || yOffset < 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid uploadTexture2DSubImage region/level arguments");
        }
        if (pixels == 0L) {
            throw new IllegalArgumentException("pixels pointer must not be null");
        }

        ensureNativeReady("uploadTexture2DSubImage");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        spine.uploadLegacyTexture2DSubImage(commandBufferHandle,
            target,
            level,
            xOffset,
            yOffset,
            width,
            height,
            format,
            type,
            pixels);
    }

    public void uploadTexture2DSubImage(CommandContext ctx,
                                        int target,
                                        int level,
                                        int xOffset,
                                        int yOffset,
                                        int width,
                                        int height,
                                        int format,
                                        int type,
                                        java.nio.ByteBuffer pixels) {
        long commandBufferHandle = requireVulkanCommandBufferHandle("uploadTexture2DSubImage", ctx);
        if (!isSupportedLegacyTextureTarget(target)) {
            throw new IllegalArgumentException("Unsupported legacy Vulkan texture upload target: " + target);
        }
        if (level < 0 || xOffset < 0 || yOffset < 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid uploadTexture2DSubImage region/level arguments");
        }
        if (pixels == null) {
            throw new IllegalArgumentException("pixels must not be null");
        }

        ensureNativeReady("uploadTexture2DSubImage");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        spine.uploadLegacyTexture2DSubImage(commandBufferHandle,
            target,
            level,
            xOffset,
            yOffset,
            width,
            height,
            format,
            type,
            pixels.duplicate());
    }

    private static final class LegacyTextureFormatInfo {
        private final int vkFormat;
        private final int pixelBytes;
        private final int unpackPixelBytes;
        private final int aspectMask;
        private final boolean expandRgbToRgba;

        private LegacyTextureFormatInfo(int vkFormat, int pixelBytes, int aspectMask) {
            this(vkFormat, pixelBytes, pixelBytes, aspectMask, false);
        }

        private LegacyTextureFormatInfo(
            int vkFormat,
            int pixelBytes,
            int unpackPixelBytes,
            int aspectMask,
            boolean expandRgbToRgba
        ) {
            this.vkFormat = vkFormat;
            this.pixelBytes = pixelBytes;
            this.unpackPixelBytes = unpackPixelBytes;
            this.aspectMask = aspectMask;
            this.expandRgbToRgba = expandRgbToRgba;
        }

        private static LegacyTextureFormatInfo resolve(int internalFormat, int format, int type) {
            if (internalFormat == VulkanicAPI.GL_DEPTH_STENCIL
                || internalFormat == VulkanicAPI.GL_DEPTH24_STENCIL8
                || (format == VulkanicAPI.GL_DEPTH_STENCIL && type == VulkanicAPI.GL_UNSIGNED_INT_24_8)) {
                return new LegacyTextureFormatInfo(
                    VK10.VK_FORMAT_D24_UNORM_S8_UINT,
                    4,
                    VK10.VK_IMAGE_ASPECT_DEPTH_BIT | VK10.VK_IMAGE_ASPECT_STENCIL_BIT
                );
            }

            if (internalFormat == VulkanicAPI.GL_DEPTH32F_STENCIL8
                || (format == VulkanicAPI.GL_DEPTH_STENCIL
                    && type == VulkanicAPI.GL_FLOAT_32_UNSIGNED_INT_24_8_REV)) {
                return new LegacyTextureFormatInfo(
                    VK10.VK_FORMAT_D32_SFLOAT_S8_UINT,
                    8,
                    VK10.VK_IMAGE_ASPECT_DEPTH_BIT | VK10.VK_IMAGE_ASPECT_STENCIL_BIT
                );
            }

            if (internalFormat == VulkanicAPI.GL_DEPTH_COMPONENT
                || internalFormat == VulkanicAPI.GL_DEPTH_COMPONENT16
                || internalFormat == VulkanicAPI.GL_DEPTH_COMPONENT24
                || internalFormat == VulkanicAPI.GL_DEPTH_COMPONENT32
                || internalFormat == VulkanicAPI.GL_DEPTH_COMPONENT32F
                || format == VulkanicAPI.GL_DEPTH_COMPONENT) {
                return new LegacyTextureFormatInfo(VK10.VK_FORMAT_D32_SFLOAT, 4, VK10.VK_IMAGE_ASPECT_DEPTH_BIT);
            }

            if ((internalFormat == VulkanicAPI.GL_R8I || format == VulkanicAPI.GL_RED_INTEGER)
                && type == VulkanicAPI.GL_BYTE) {
                return new LegacyTextureFormatInfo(VK10.VK_FORMAT_R8_SINT, 1, VK10.VK_IMAGE_ASPECT_COLOR_BIT);
            }

            if (internalFormat == VulkanicAPI.GL_R16F
                || (format == VulkanicAPI.GL_RED && type == VulkanicAPI.GL_HALF_FLOAT)) {
                return new LegacyTextureFormatInfo(VK10.VK_FORMAT_R16_SFLOAT, 2, VK10.VK_IMAGE_ASPECT_COLOR_BIT);
            }

            if (internalFormat == VulkanicAPI.GL_R32F
                && format == VulkanicAPI.GL_RED
                && type == VulkanicAPI.GL_FLOAT) {
                return new LegacyTextureFormatInfo(VK10.VK_FORMAT_R32_SFLOAT, 4, VK10.VK_IMAGE_ASPECT_COLOR_BIT);
            }

            if (internalFormat == VulkanicAPI.GL_RGBA16F
                || (format == VulkanicAPI.GL_RGBA && type == VulkanicAPI.GL_HALF_FLOAT)) {
                return new LegacyTextureFormatInfo(VK10.VK_FORMAT_R16G16B16A16_SFLOAT, 8, VK10.VK_IMAGE_ASPECT_COLOR_BIT);
            }

            if ((internalFormat == VulkanicAPI.GL_RGBA16 || internalFormat == VulkanicAPI.GL_RGBA)
                && (type == VulkanicAPI.GL_UNSIGNED_SHORT_4_4_4_4
                || type == VulkanicAPI.GL_UNSIGNED_SHORT_4_4_4_4_REV)) {
                return new LegacyTextureFormatInfo(VK10.VK_FORMAT_R4G4B4A4_UNORM_PACK16, 2, VK10.VK_IMAGE_ASPECT_COLOR_BIT);
            }

            if ((format == VulkanicAPI.GL_BGRA || internalFormat == VulkanicAPI.GL_BGRA)
                && type == VulkanicAPI.GL_UNSIGNED_BYTE) {
                return new LegacyTextureFormatInfo(VK10.VK_FORMAT_B8G8R8A8_UNORM, 4, VK10.VK_IMAGE_ASPECT_COLOR_BIT);
            }

            if ((format == VulkanicAPI.GL_RGBA || internalFormat == VulkanicAPI.GL_RGBA8 || internalFormat == VulkanicAPI.GL_RGBA16)
                && type == VulkanicAPI.GL_UNSIGNED_BYTE) {
                return new LegacyTextureFormatInfo(VK10.VK_FORMAT_R8G8B8A8_UNORM, 4, VK10.VK_IMAGE_ASPECT_COLOR_BIT);
            }

            if (format == VulkanicAPI.GL_RED && type == VulkanicAPI.GL_UNSIGNED_BYTE) {
                return new LegacyTextureFormatInfo(VK10.VK_FORMAT_R8_UNORM, 1, VK10.VK_IMAGE_ASPECT_COLOR_BIT);
            }

            if (format == GL_LUMINANCE && type == VulkanicAPI.GL_UNSIGNED_BYTE) {
                return new LegacyTextureFormatInfo(VK10.VK_FORMAT_R8_UNORM, 1, VK10.VK_IMAGE_ASPECT_COLOR_BIT);
            }

            if (format == GL_LUMINANCE_ALPHA && type == VulkanicAPI.GL_UNSIGNED_BYTE) {
                return new LegacyTextureFormatInfo(VK10.VK_FORMAT_R8G8_UNORM, 2, VK10.VK_IMAGE_ASPECT_COLOR_BIT);
            }

            if (format == VulkanicAPI.GL_RGB && type == VulkanicAPI.GL_UNSIGNED_BYTE) {
                // Normalize RGB8 uploads to RGBA8 to avoid fragile 24-bit legacy image allocations.
                return new LegacyTextureFormatInfo(
                    VK10.VK_FORMAT_R8G8B8A8_UNORM,
                    4,
                    3,
                    VK10.VK_IMAGE_ASPECT_COLOR_BIT,
                    true
                );
            }

            throw new IllegalArgumentException(
                "Unsupported legacy texture upload format combination: internalFormat=" + internalFormat
                    + ", format=" + format + ", type=" + type);
        }

        private static LegacyTextureFormatInfo resolveStorage(int internalFormat, int format, int type) {
            if (internalFormat == VulkanicAPI.GL_DEPTH_STENCIL
                || internalFormat == VulkanicAPI.GL_DEPTH24_STENCIL8
                || internalFormat == VulkanicAPI.GL_DEPTH32F_STENCIL8) {
                return resolve(internalFormat, format, type);
            }

            if ((format == VulkanicAPI.GL_BGRA || internalFormat == VulkanicAPI.GL_BGRA)
                && type == VulkanicAPI.GL_UNSIGNED_BYTE) {
                return new LegacyTextureFormatInfo(
                    VK10.VK_FORMAT_B8G8R8A8_UNORM,
                    4,
                    VK10.VK_IMAGE_ASPECT_COLOR_BIT
                );
            }

            LegacyTextureFormatInfo sizedFormat = switch (internalFormat) {
                case VulkanicAPI.GL_R8 -> new LegacyTextureFormatInfo(
                    VK10.VK_FORMAT_R8_UNORM,
                    1,
                    VK10.VK_IMAGE_ASPECT_COLOR_BIT
                );
                case VulkanicAPI.GL_RGBA8 -> new LegacyTextureFormatInfo(
                    VK10.VK_FORMAT_R8G8B8A8_UNORM,
                    4,
                    VK10.VK_IMAGE_ASPECT_COLOR_BIT
                );
                case VulkanicAPI.GL_RGBA8_SNORM -> new LegacyTextureFormatInfo(
                    VK10.VK_FORMAT_R8G8B8A8_SNORM,
                    4,
                    VK10.VK_IMAGE_ASPECT_COLOR_BIT
                );
                case VulkanicAPI.GL_R32F -> new LegacyTextureFormatInfo(
                    VK10.VK_FORMAT_R32_SFLOAT,
                    4,
                    VK10.VK_IMAGE_ASPECT_COLOR_BIT
                );
                case VulkanicAPI.GL_RGBA16F -> new LegacyTextureFormatInfo(
                    VK10.VK_FORMAT_R16G16B16A16_SFLOAT,
                    8,
                    VK10.VK_IMAGE_ASPECT_COLOR_BIT
                );
                case VulkanicAPI.GL_R11F_G11F_B10F -> new LegacyTextureFormatInfo(
                    VK10.VK_FORMAT_B10G11R11_UFLOAT_PACK32,
                    4,
                    VK10.VK_IMAGE_ASPECT_COLOR_BIT
                );
                default -> null;
            };

            return sizedFormat != null ? sizedFormat : resolve(internalFormat, format, type);
        }
    }

    /**
     * Vulkan-native handle for a compiled graphics pipeline.
     *
     * <p>Wraps a {@code VkPipeline}, its {@code VkPipelineLayout}, and an associated
     * {@code VkDescriptorSetLayout}.  All three are destroyed deterministically when
     * {@link #close()} is called.</p>
     */
    private static final class VulkanPipelineHandle implements PipelineHandle {
        private final long vkPipelineHandle;
        private final long vkPipelineLayoutHandle;
        private final long vkDescriptorSetLayoutHandle;
        private final VulkanRenderPassCompatibilityKey renderPassCompatibilityKey;
        /** Number of resource bindings in the DSL this pipeline was compiled with. */
        private final int resourceBindingCount;
        private final NativeSpine spine;
        private volatile boolean closed;

        private VulkanPipelineHandle(
            long vkPipelineHandle,
            long vkPipelineLayoutHandle,
            long vkDescriptorSetLayoutHandle,
            VulkanRenderPassCompatibilityKey renderPassCompatibilityKey,
            int resourceBindingCount,
            NativeSpine spine
        ) {
            this.vkPipelineHandle = vkPipelineHandle;
            this.vkPipelineLayoutHandle = vkPipelineLayoutHandle;
            this.vkDescriptorSetLayoutHandle = vkDescriptorSetLayoutHandle;
            this.renderPassCompatibilityKey = Objects.requireNonNull(
                renderPassCompatibilityKey,
                "renderPassCompatibilityKey must not be null"
            );
            this.resourceBindingCount = resourceBindingCount;
            this.spine = Objects.requireNonNull(spine, "spine must not be null");
        }

        /** Returns the native {@code VkPipeline} handle for command-buffer binding. */
        long getVkPipelineHandle() {
            return vkPipelineHandle;
        }

        /** Returns the native {@code VkPipelineLayout} handle for descriptor set binding. */
        long getVkPipelineLayoutHandle() {
            return vkPipelineLayoutHandle;
        }

        /** Returns the native {@code VkDescriptorSetLayout} handle used by this pipeline. */
        long getVkDescriptorSetLayoutHandle() {
            return vkDescriptorSetLayoutHandle;
        }

        VulkanRenderPassCompatibilityKey getRenderPassCompatibilityKey() {
            return renderPassCompatibilityKey;
        }

        /**
         * Returns the number of resource bindings in the descriptor set layout this pipeline
         * was compiled with. Used to detect partial-write mismatches at draw time.
         */
        int getResourceBindingCount() {
            return resourceBindingCount;
        }

        @Override
        public boolean isValid() {
            return !closed && vkPipelineHandle != VK10.VK_NULL_HANDLE;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            spine.destroyVulkanPipeline(vkPipelineHandle, vkPipelineLayoutHandle, vkDescriptorSetLayoutHandle);
        }

        @Override
        public String toString() {
            return "VulkanPipelineHandle{valid=" + isValid() + ", pipeline=0x"
                + Long.toHexString(vkPipelineHandle)
                + ", renderPassCompatibility=" + renderPassCompatibilityKey
                + "}";
        }
    }

    public PipelineHandle createPipeline(PipelineDescriptor descriptor) {
        if (descriptor == null) {
            throw new IllegalArgumentException("descriptor must not be null");
        }
        ensureNativeReady("createPipeline");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }

        // Vulkan pipeline creation requires precompiled SPIR-V shader modules.
        List<VulkanicSpirvModule> spirvModules = descriptor.getSpirvModules();
        if (spirvModules.isEmpty()) {
            throw new IllegalArgumentException(
                "Vulkan pipeline creation requires precompiled SPIR-V shader modules attached to the "
                + "PipelineDescriptor. Use PipelineDescriptor.fromPortableStateAndSpirvModules(...) "
                + "or PipelineDescriptor.fromRenderPipelineAndSpirvModules(...) to provide SPIR-V "
                + "bytecode for both vertex and fragment stages.");
        }

        VulkanicSpirvModule vertModule = null;
        VulkanicSpirvModule fragModule = null;
        for (VulkanicSpirvModule module : spirvModules) {
            if (module.stage() == VulkanicShaderStage.VERTEX) {
                vertModule = module;
            } else if (module.stage() == VulkanicShaderStage.FRAGMENT) {
                fragModule = module;
            }
        }
        if (vertModule == null) {
            throw new IllegalArgumentException(
                "Vulkan pipeline creation requires a VERTEX stage SPIR-V module in the descriptor.");
        }
        if (fragModule == null) {
            throw new IllegalArgumentException(
                "Vulkan pipeline creation requires a FRAGMENT stage SPIR-V module in the descriptor.");
        }

        // Create transient shader modules, create the pipeline, then immediately destroy the modules
        // (valid per Vulkan spec: VkPipeline owns its compiled shader code after creation).
        long vertModuleHandle = spine.createShaderModule(vertModule);
        long fragModuleHandle = spine.createShaderModule(fragModule);
        try {
            return spine.createVulkanPipeline(
                descriptor,
                vertModuleHandle,
                fragModuleHandle,
                defaultRenderPassCompatibilityKey()
            );
        } finally {
            spine.destroyShaderModule(vertModuleHandle);
            spine.destroyShaderModule(fragModuleHandle);
        }
    }

    public PipelineHandle createPipeline(PipelineDescriptor descriptor, int framebuffer) {
        if (descriptor == null) {
            throw new IllegalArgumentException("descriptor must not be null");
        }
        return createPipeline(descriptor, resolveFramebufferTargets(framebuffer));
    }

    public PipelineHandle createPipeline(PipelineDescriptor descriptor, VulkanicRenderTargetDescriptor renderTarget) {
        if (descriptor == null) {
            throw new IllegalArgumentException("descriptor must not be null");
        }
        return createPipeline(descriptor, resolveRenderTargetDescriptor(renderTarget));
    }

    private PipelineHandle createPipeline(PipelineDescriptor descriptor, ResolvedFramebufferTargets targets) {
        return createPipeline(
            descriptor,
            VulkanRenderPassCompatibilityKey.framebuffer(
                targets.colorFormats(),
                targets.hasDepthTarget() ? targets.depthTexture.vkFormat : VK10.VK_FORMAT_UNDEFINED,
                targets.hasFeedbackLoopTarget()
            )
        );
    }

    private PipelineHandle createPipeline(
        PipelineDescriptor descriptor,
        VulkanRenderPassCompatibilityKey renderPassCompatibilityKey
    ) {
        ensureNativeReady("createPipeline(framebuffer)");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }

        List<VulkanicSpirvModule> spirvModules = descriptor.getSpirvModules();
        if (spirvModules.isEmpty()) {
            throw new IllegalArgumentException(
                "Vulkan pipeline creation requires precompiled SPIR-V shader modules attached to the "
                    + "PipelineDescriptor. Use PipelineDescriptor.fromPortableStateAndSpirvModules(...) "
                    + "or PipelineDescriptor.fromRenderPipelineAndSpirvModules(...) to provide SPIR-V "
                    + "bytecode for both vertex and fragment stages."
            );
        }

        VulkanicSpirvModule vertModule = null;
        VulkanicSpirvModule geomModule = null;
        VulkanicSpirvModule fragModule = null;
        for (VulkanicSpirvModule module : spirvModules) {
            if (module.stage() == VulkanicShaderStage.VERTEX) {
                vertModule = module;
            } else if (module.stage() == VulkanicShaderStage.GEOMETRY) {
                geomModule = module;
            } else if (module.stage() == VulkanicShaderStage.FRAGMENT) {
                fragModule = module;
            }
        }
        if (vertModule == null) {
            throw new IllegalArgumentException(
                "Vulkan pipeline creation requires a VERTEX stage SPIR-V module in the descriptor.");
        }
        if (fragModule == null) {
            throw new IllegalArgumentException(
                "Vulkan pipeline creation requires a FRAGMENT stage SPIR-V module in the descriptor.");
        }

        long vertModuleHandle = spine.createShaderModule(vertModule);
        long geomModuleHandle = geomModule != null ? spine.createShaderModule(geomModule) : VK10.VK_NULL_HANDLE;
        long fragModuleHandle = spine.createShaderModule(fragModule);
        try {
            return spine.createVulkanPipeline(
                descriptor,
                vertModuleHandle,
                geomModuleHandle,
                fragModuleHandle,
                renderPassCompatibilityKey
            );
        } finally {
            spine.destroyShaderModule(vertModuleHandle);
            if (geomModuleHandle != VK10.VK_NULL_HANDLE) {
                spine.destroyShaderModule(geomModuleHandle);
            }
            spine.destroyShaderModule(fragModuleHandle);
        }
    }

    public List<VulkanicSpirvModule> getLinkedProgramSpirvModules(CommandContext ctx, int program) {
        requireVulkanCommandBufferHandle("getLinkedProgramSpirvModules", ctx);
        VirtualProgram virtualProgram = virtualPrograms.get(program);
        return virtualProgram == null ? List.of() : virtualProgram.linkedSpirvModules;
    }

    @Nullable
    public PipelineDescriptor.ResourceLayout getLinkedProgramResourceLayout(
        CommandContext ctx,
        int program,
        java.util.Set<VulkanicShaderStage> stages
    ) {
        requireVulkanCommandBufferHandle("getLinkedProgramResourceLayout", ctx);
        VirtualProgram virtualProgram = virtualPrograms.get(program);
        if (virtualProgram == null || !virtualProgram.linkStatus) {
            return null;
        }

        java.util.Set<VulkanicShaderStage> normalizedStages = java.util.Set.copyOf(
            Objects.requireNonNull(stages, "stages must not be null")
        );
        if (normalizedStages.isEmpty()) {
            throw new IllegalArgumentException("stages must not be empty");
        }

        List<PipelineDescriptor.ResourceBinding> bindings =
            new ArrayList<>(virtualProgram.activeResourceBindings.size());
        for (ReflectedResourceBinding resourceBinding : virtualProgram.activeResourceBindings) {
            bindings.add(new PipelineDescriptor.ResourceBinding(
                resourceBinding.set(),
                resourceBinding.binding(),
                resourceBinding.name(),
                resourceBinding.type(),
                null,
                normalizedStages
            ));
        }
        return new PipelineDescriptor.ResourceLayout(bindings);
    }

    @Nullable
    private PipelineDescriptor createLegacyProgramPipelineDescriptor(int programId, int mode) {
        VirtualProgram virtualProgram = virtualPrograms.get(programId);
        if (virtualProgram == null || !virtualProgram.linkStatus || virtualProgram.linkedSpirvModules.isEmpty()) {
            return null;
        }

        PipelineDescriptor.VertexInputState vertexInputState = createLegacyVertexInputState();
        if (vertexInputState == null) {
            return null;
        }

        Set<VulkanicShaderStage> stages = virtualProgram.linkedSpirvModules.stream()
            .map(VulkanicSpirvModule::stage)
            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        PipelineDescriptor.ResourceLayout resourceLayout = getLinkedProgramResourceLayout(
            getCurrentCommandContext(),
            programId,
            stages
        );
        if (resourceLayout == null) {
            resourceLayout = new PipelineDescriptor.ResourceLayout(List.of());
        }

        java.util.Optional<PipelineDescriptor.BlendState> blendState = java.util.Optional.empty();
        if (pendingBlendEnabled) {
            SourceFactor srcRgb = sourceFactorFromLegacyGl(pendingBlendSrcRgb);
            DestFactor dstRgb = destFactorFromLegacyGl(pendingBlendDstRgb);
            SourceFactor srcAlpha = sourceFactorFromLegacyGl(pendingBlendSrcAlpha);
            DestFactor dstAlpha = destFactorFromLegacyGl(pendingBlendDstAlpha);
            if (srcRgb != null && dstRgb != null && srcAlpha != null && dstAlpha != null) {
                blendState = java.util.Optional.of(new PipelineDescriptor.BlendState(srcRgb, dstRgb, srcAlpha, dstAlpha));
            }
        }

        PipelineDescriptor.PortableState portableState = new PipelineDescriptor.PortableState(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("vulkanic", "legacy_program/" + programId),
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("vulkanic", "legacy_program/" + programId + "/vertex"),
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("vulkanic", "legacy_program/" + programId + "/fragment"),
            Map.of(),
            Set.of(),
            List.of(),
            List.of(),
            blendState,
            pendingDepthTestEnabled ? legacyDepthFunction(pendingDepthFunc) : net.blaze3d.platform.DepthTestFunction.NO_DEPTH_TEST,
            legacyPolygonMode(),
            pendingCullFaceEnabled,
            pendingColorMaskR || pendingColorMaskG || pendingColorMaskB,
            pendingColorMaskA,
            pendingDepthWriteMask,
            pendingLogicOp == 0x150B /* GL_OR_REVERSE */
                ? net.blaze3d.platform.LogicOp.OR_REVERSE
                : net.blaze3d.platform.LogicOp.NONE,
            net.blaze3d.vertex.DefaultVertexFormat.EMPTY,
            legacyVertexMode(mode),
            pendingPolygonOffsetFactor,
            pendingPolygonOffsetUnits
        );

        return PipelineDescriptor.fromPortableStateAndSpirvModules(portableState, virtualProgram.linkedSpirvModules)
            .withResourceLayout(resourceLayout)
            .withVertexInputState(vertexInputState);
    }

    @Nullable
    private PipelineDescriptor.VertexInputState createLegacyVertexInputState() {
        VirtualVaoState vaoState = currentVirtualVaoState();
        List<LegacyVertexAttribute> attributes = vaoState.enabledAttributes.stream()
            .map(vaoState.attributes::get)
            .filter(Objects::nonNull)
            .sorted(java.util.Comparator.comparingInt(LegacyVertexAttribute::index))
            .toList();
        if (attributes.isEmpty()) {
            return null;
        }

        java.util.LinkedHashMap<Integer, PipelineDescriptor.VertexInputBinding> bindings = new java.util.LinkedHashMap<>();
        List<PipelineDescriptor.VertexInputAttribute> vertexAttributes = new ArrayList<>(attributes.size());
        for (LegacyVertexAttribute attribute : attributes) {
            LegacyVertexBinding binding = vaoState.bindings.get(attribute.binding());
            int stride = binding != null && binding.stride() > 0
                ? binding.stride()
                : attributeByteSize(attribute.size(), attribute.type());
            bindings.putIfAbsent(attribute.binding(), new PipelineDescriptor.VertexInputBinding(
                attribute.binding(),
                stride,
                attribute.divisor() > 0
                    ? PipelineDescriptor.VertexInputRate.INSTANCE
                    : PipelineDescriptor.VertexInputRate.VERTEX
            ));
            vertexAttributes.add(new PipelineDescriptor.VertexInputAttribute(
                attribute.index(),
                attribute.binding(),
                toVertexAttributeFormat(attribute.type(), attribute.size(), attribute.normalized(), attribute.integer()),
                attribute.offset()
            ));
        }

        return new PipelineDescriptor.VertexInputState(new ArrayList<>(bindings.values()), vertexAttributes);
    }

    private PipelineResourcePlanner.Plan buildLegacyProgramResourcePlan(
        CommandContext ctx,
        PipelineDescriptor descriptor,
        int programId
    ) {
        VirtualProgram virtualProgram = virtualPrograms.get(programId);
        if (virtualProgram == null) {
            return null;
        }
        return net.vulkanic.VulkanicPipelineResourceResolver.buildPlan(
            ctx,
            descriptor,
            new net.vulkanic.VulkanicPipelineResourceResolver.ResourceLookup() {
                @Override
                @Nullable
                public VulkanicTextureView samplerView(PipelineDescriptor.ResourceBinding binding) {
                    Integer unit = samplerUnit(binding);
                    if (unit == null) {
                        return null;
                    }
                    NativeSpine spine = nativeSpine;
                    int textureId = 0;
                    if (spine != null) {
                        textureId = spine.legacyTexture2DBindingsByUnit.getOrDefault(unit, 0);
                    }
                    if (textureId <= 0) {
                        textureId = net.irisshaders.iris.gl.IrisRenderSystem.getTextureBinding(unit);
                    }
                    return textureId > 0 ? createManagedLegacyTextureView(textureId) : null;
                }

                @Override
                @Nullable
                public Integer samplerUnit(PipelineDescriptor.ResourceBinding binding) {
                    int uniformIndex = virtualProgram.activeUniformNames.indexOf(binding.name());
                    if (uniformIndex < 0) {
                        return binding.binding();
                    }
                    return virtualProgram.samplerUniformValuesByIndex.getOrDefault(uniformIndex, binding.binding());
                }

                @Override
                @Nullable
                public VulkanicBufferSlice uniformBufferSlice(PipelineDescriptor.ResourceBinding binding) {
                    return null;
                }

                @Override
                @Nullable
                public Integer texelBufferUnit(PipelineDescriptor.ResourceBinding binding) {
                    return samplerUnit(binding);
                }

                @Override
                @Nullable
                public Integer standaloneProgramId(PipelineDescriptor.ResourceBinding binding) {
                    return programId;
                }

                @Override
                @Nullable
                public Integer samplerObject(int samplerUnit) {
                    Integer samplerObject = boundSamplerPerUnit.get(samplerUnit);
                    return samplerObject != null && samplerObject > 0 ? samplerObject : null;
                }
            },
            PipelineResourcePlanner.options().requireAtLeastOneBinding(false)
        );
    }

    private void bindLegacyProgramPipelineForDraw(long commandBufferHandle, int mode) {
        int programId = boundVirtualProgram;
        if (programId <= 0) {
            return;
        }
        PipelineDescriptor descriptor = createLegacyProgramPipelineDescriptor(programId, mode);
        if (descriptor == null) {
            return;
        }

        CommandContext ctx = getCurrentCommandContext();
        PipelineResourcePlanner.Plan resourcePlan = buildLegacyProgramResourcePlan(ctx, descriptor, programId);
        if (resourcePlan == null) {
            return;
        }

        ensureNativeReady("bindLegacyProgramPipelineForDraw");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }

        int framebuffer = boundDrawFbo;
        VulkanRenderPassCompatibilityKey compatibilityKey = spine.activeRenderPassCompatibilityKey();
        if (compatibilityKey == null && framebuffer != 0) {
            ResolvedFramebufferTargets targets = resolveFramebufferTargets(framebuffer);
            compatibilityKey = VulkanRenderPassCompatibilityKey.framebuffer(
                targets.colorFormats(),
                targets.hasDepthTarget() ? targets.depthTexture.vkFormat : VK10.VK_FORMAT_UNDEFINED,
                targets.hasFeedbackLoopTarget()
            );
        } else if (compatibilityKey == null) {
            compatibilityKey = defaultRenderPassCompatibilityKey();
        }

        PipelineDescriptor submissionDescriptor = resourcePlan.descriptor();
        LegacyProgramPipelineKey key = new LegacyProgramPipelineKey(
            programId,
            framebuffer,
            submissionDescriptor.getPipelineCompilationKey()
                + '|'
                + dynamicPipelineStateCacheKey(submissionDescriptor.getPortableState(), compatibilityKey),
            submissionDescriptor.getResourceLayoutCacheKey(),
            compatibilityKey
        );
        PipelineHandle pipelineHandle = legacyProgramPipelineCache.get(key);
        if (pipelineHandle != null && !pipelineHandle.isValid()) {
            legacyProgramPipelineCache.remove(key, pipelineHandle);
            pipelineHandle = null;
        }
        if (pipelineHandle == null) {
            PipelineHandle created = createPipeline(submissionDescriptor, compatibilityKey);
            PipelineHandle raced = legacyProgramPipelineCache.putIfAbsent(key, created);
            if (raced != null) {
                created.close();
                pipelineHandle = raced;
            } else {
                pipelineHandle = created;
            }
        }
        if (pipelineHandle == null || !pipelineHandle.isValid()) {
            return;
        }

        if (pipelineHandle instanceof VulkanPipelineHandle vulkanPipeline) {
            spine.bindPipeline(commandBufferHandle, vulkanPipeline.getVkPipelineHandle());
        }
        bindPipelineResources(ctx, pipelineHandle, submissionDescriptor, resourcePlan.bindings());
    }

    private static final class BoundPipelineResources {
        private final PipelineHandle pipeline;
        private final PipelineDescriptor descriptor;
        private final PipelineResourceBindings bindings;

        private BoundPipelineResources(
            PipelineHandle pipeline,
            PipelineDescriptor descriptor,
            PipelineResourceBindings bindings
        ) {
            this.pipeline = pipeline;
            this.descriptor = descriptor;
            this.bindings = bindings;
        }
    }

    public net.vulkanic.DescriptorPoolHandle createDescriptorPool(
            net.vulkanic.DescriptorPoolDescriptor descriptor) {
        if (descriptor == null) {
            throw new IllegalArgumentException("descriptor must not be null");
        }
        return new VulkanDescriptorPoolHandle(descriptor.maxSets());
    }

    public net.vulkanic.DescriptorSetHandle allocateDescriptorSet(
            net.vulkanic.DescriptorPoolHandle pool,
            PipelineDescriptor descriptor) {
        if (!(pool instanceof VulkanDescriptorPoolHandle vulkanPool)) {
            throw new IllegalArgumentException(
                "Vulkan backend requires VulkanDescriptorPoolHandle, got: "
                    + (pool == null ? "null" : pool.getClass().getName()));
        }
        if (descriptor == null) {
            throw new IllegalArgumentException("descriptor must not be null");
        }

        return vulkanPool.allocate(descriptor.getStableCacheKey(), descriptor.getResourceLayout());
    }

    public void updateDescriptorSet(net.vulkanic.DescriptorSetHandle descriptorSet,
            net.vulkanic.PipelineResourceBindings bindings) {
        if (!(descriptorSet instanceof VulkanDescriptorSetHandle vulkanDescriptorSet)) {
            throw new IllegalArgumentException(
                "Vulkan backend requires VulkanDescriptorSetHandle, got: "
                    + (descriptorSet == null ? "null" : descriptorSet.getClass().getName()));
        }

        vulkanDescriptorSet.updateBindings(bindings);
    }

    public void bindDescriptorSet(CommandContext ctx,
            PipelineHandle pipeline,
            PipelineDescriptor descriptor,
            net.vulkanic.DescriptorSetHandle descriptorSet) {
        if (!(descriptorSet instanceof VulkanDescriptorSetHandle vulkanDescriptorSet)) {
            throw new IllegalArgumentException(
                "Vulkan backend requires VulkanDescriptorSetHandle, got: "
                    + (descriptorSet == null ? "null" : descriptorSet.getClass().getName()));
        }
        if (descriptor == null) {
            throw new IllegalArgumentException("descriptor must not be null");
        }

        String expectedLayoutKey = descriptor.getStableCacheKey();
        if (!expectedLayoutKey.equals(vulkanDescriptorSet.layoutKey())) {
            throw new IllegalArgumentException(
                "Descriptor set layout key mismatch. Expected " + expectedLayoutKey
                    + " but got " + vulkanDescriptorSet.layoutKey());
        }

        bindPipelineResources(ctx, pipeline, descriptor, vulkanDescriptorSet.requireBindings());
    }

    public void resetDescriptorPool(net.vulkanic.DescriptorPoolHandle pool) {
        if (!(pool instanceof VulkanDescriptorPoolHandle vulkanPool)) {
            throw new IllegalArgumentException(
                "Vulkan backend requires VulkanDescriptorPoolHandle, got: "
                    + (pool == null ? "null" : pool.getClass().getName()));
        }

        vulkanPool.reset();
    }

    public net.vulkanic.VulkanicBuffer resolveVulkanicBuffer(net.blaze3d.buffers.GpuBuffer gpuBuffer) {
        int handle = resolveBufferHandle(getCurrentCommandContext(), gpuBuffer);
        if (handle == 0) {
            throw new IllegalArgumentException(
                "Vulkan backend resolveVulkanicBuffer requires a resolvable legacy buffer handle, got: "
                    + gpuBuffer.getClass().getName());
        }
        ensureNativeReady("resolveVulkanicBuffer");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        return spine.resolveLegacyVulkanBuffer(handle);
    }

    public int resolveBufferHandle(CommandContext ctx, net.blaze3d.buffers.GpuBuffer gpuBuffer) {
        requireVulkanCommandBufferHandle("resolveBufferHandle", ctx);
        if (gpuBuffer == null) {
            return 0;
        }
        if (gpuBuffer instanceof net.blaze3d.opengl.GlBuffer glBuffer) {
            return glBuffer.getHandle();
        }
        return resolveGpuBufferLegacyHandle(gpuBuffer);
    }

    public net.vulkanic.PipelineHandle resolvePipelineHandle(
            net.blaze3d.pipeline.RenderPipeline renderPipeline,
            net.vulkanic.PipelineDescriptor descriptor) {
        if (renderPipeline == null) {
            return null;
        }
        PrecompiledPipelineState state = precompiledPipelineCache.get(renderPipeline);
        if (state == null || !state.isValid()) {
            return null;
        }
        if (descriptor == null) {
            return state.pipelineHandle;
        }
        if (state.matchesDescriptor(descriptor)) {
            return state.pipelineHandle;
        }
        if (!matchesStableDescriptor(state, descriptor)) {
            return null;
        }

        PipelineDescriptor variantDescriptor = descriptorVariantDescriptor(state, descriptor);
        if (variantDescriptor == null) {
            return null;
        }

        DescriptorPipelineKey key = new DescriptorPipelineKey(
            variantDescriptor.getStableCacheKey(),
            variantDescriptor.getResourceLayoutCacheKey()
        );
        PipelineHandle cached = descriptorPipelineCache.get(key);
        if (cached != null) {
            if (cached.isValid()) {
                return cached;
            }
            descriptorPipelineCache.remove(key, cached);
        }

        PipelineHandle descriptorVariant = createPipeline(variantDescriptor);
        if (descriptorVariant == null || !descriptorVariant.isValid()) {
            if (descriptorVariant != null) {
                descriptorVariant.close();
            }
            return null;
        }

        PipelineHandle raced = descriptorPipelineCache.putIfAbsent(key, descriptorVariant);
        if (raced != null) {
            if (raced.isValid()) {
                descriptorVariant.close();
                return raced;
            }
            descriptorPipelineCache.put(key, descriptorVariant);
        }
        return descriptorVariant;
    }

    public net.vulkanic.PipelineHandle resolvePipelineHandle(
            net.blaze3d.pipeline.RenderPipeline renderPipeline,
            net.vulkanic.PipelineDescriptor descriptor,
            int framebuffer) {
        if (framebuffer == 0) {
            return resolvePipelineHandle(renderPipeline, descriptor);
        }
        if (renderPipeline == null) {
            return null;
        }
        VulkanRenderTargetPlan plan = resolveFramebufferRenderTargetPlan(() -> "Pipeline-compatible framebuffer target", framebuffer);
        return resolvePipelineHandle(renderPipeline, descriptor, plan);
    }

    public net.vulkanic.PipelineHandle resolvePipelineHandle(
            net.blaze3d.pipeline.RenderPipeline renderPipeline,
            net.vulkanic.PipelineDescriptor descriptor,
            VulkanicRenderTargetDescriptor renderTarget) {
        if (renderPipeline == null) {
            return null;
        }
        VulkanRenderTargetPlan plan = resolveRenderTargetDescriptorPlan(renderTarget);
        return resolvePipelineHandle(renderPipeline, descriptor, plan);
    }

    public net.vulkanic.PipelineHandle resolvePipelineHandle(
            net.blaze3d.pipeline.RenderPipeline renderPipeline,
            net.vulkanic.PipelineDescriptor descriptor,
            VulkanicTextureView colorTarget,
            @Nullable VulkanicTextureView depthTarget) {
        if (renderPipeline == null) {
            return null;
        }

        VulkanRenderTargetPlan plan = resolveRenderPassDescriptorPlan(
            VulkanicRenderPassDescriptor.colorAndDepth(
                () -> "Pipeline-compatible texture-view target",
                colorTarget,
                OptionalInt.empty(),
                depthTarget,
                OptionalDouble.empty()
            )
        );
        return resolvePipelineHandle(renderPipeline, descriptor, plan);
    }

    private net.vulkanic.PipelineHandle resolvePipelineHandle(
            net.blaze3d.pipeline.RenderPipeline renderPipeline,
            net.vulkanic.PipelineDescriptor descriptor,
            ResolvedFramebufferTargets targets) {
        return resolvePipelineHandle(
            renderPipeline,
            descriptor,
            VulkanRenderTargetPlan.framebuffer(() -> "Pipeline-compatible framebuffer target", targets)
        );
    }

    private net.vulkanic.PipelineHandle resolvePipelineHandle(
            net.blaze3d.pipeline.RenderPipeline renderPipeline,
            net.vulkanic.PipelineDescriptor descriptor,
            VulkanRenderTargetPlan plan) {
        return resolvePipelineHandle(
            renderPipeline,
            descriptor,
            plan.compatibilityKey()
        );
    }

    private net.vulkanic.PipelineHandle resolvePipelineHandle(
            net.blaze3d.pipeline.RenderPipeline renderPipeline,
            net.vulkanic.PipelineDescriptor descriptor,
            VulkanRenderPassCompatibilityKey renderPassCompatibilityKey) {
        PrecompiledPipelineState state = precompiledPipelineCache.get(renderPipeline);
        if (state == null || !state.isValid()) {
            return null;
        }
        PipelineDescriptor pipelineDescriptor = descriptor != null ? descriptorVariantDescriptor(state, descriptor) : state.descriptor;
        if (pipelineDescriptor == null) {
            return null;
        }
        if (!state.matchesDescriptor(pipelineDescriptor) && !matchesStableDescriptor(state, pipelineDescriptor)) {
            return null;
        }

        RenderTargetPipelineKey key = RenderTargetPipelineKey.from(
            pipelineDescriptor,
            renderPassCompatibilityKey,
            dynamicPipelineStateCacheKey(pipelineDescriptor.getPortableState(), renderPassCompatibilityKey)
        );
        PipelineHandle cached = renderTargetPipelineCache.get(key);
        if (cached != null) {
            if (cached.isValid()) {
                return cached;
            }
            renderTargetPipelineCache.remove(key, cached);
        }

        PipelineHandle framebufferCompatible = createPipeline(pipelineDescriptor, renderPassCompatibilityKey);
        if (framebufferCompatible == null || !framebufferCompatible.isValid()) {
            if (framebufferCompatible != null) {
                framebufferCompatible.close();
            }
            return null;
        }

        PipelineHandle raced = renderTargetPipelineCache.putIfAbsent(key, framebufferCompatible);
        if (raced != null) {
            if (raced.isValid()) {
                framebufferCompatible.close();
                return raced;
            }
            renderTargetPipelineCache.put(key, framebufferCompatible);
        }
        return framebufferCompatible;
    }

    private String indexedBlendStateCacheKey(PipelineDescriptor.PortableState portableState, int colorAttachmentCount) {
        if (colorAttachmentCount <= 0) {
            return "";
        }

        StringBuilder key = new StringBuilder(colorAttachmentCount * 24);
        for (int index = 0; index < colorAttachmentCount; index++) {
            java.util.Optional<java.util.Optional<PipelineDescriptor.BlendState>> sharedOverride =
                SharedChunkProgramOverrides.indexedBlendState(portableState.location(), index);
            if (sharedOverride.isPresent()) {
                key.append("shared:");
                appendBlendStateCacheKey(key, sharedOverride.get());
            } else if (portableState.blendState().isPresent()) {
                key.append("portable:");
                appendBlendStateCacheKey(key, portableState.blendState());
            } else {
                IndexedBlendState state = effectiveBlendStateForAttachment(index);
                if (state == null) {
                    key.append("default");
                } else {
                    key.append(state.cacheKey());
                }
            }
            key.append(';');
        }
        return key.toString();
    }

    private static void appendBlendStateCacheKey(
        StringBuilder key,
        java.util.Optional<PipelineDescriptor.BlendState> blendState
    ) {
        if (blendState.isEmpty()) {
            key.append("off");
            return;
        }
        PipelineDescriptor.BlendState blend = blendState.get();
        key.append(blend.sourceColor().name())
            .append(',')
            .append(blend.destColor().name())
            .append(',')
            .append(blend.sourceAlpha().name())
            .append(',')
            .append(blend.destAlpha().name());
    }

    private String dynamicPipelineStateCacheKey(
        PipelineDescriptor.PortableState portableState,
        VulkanRenderPassCompatibilityKey renderPassCompatibilityKey
    ) {
        return indexedBlendStateCacheKey(portableState, renderPassCompatibilityKey.colorAttachmentCount())
            + '|'
            + currentStencilState().cacheKey(renderPassCompatibilityKey.hasStencilAttachment());
    }

    private static VulkanRenderPassCompatibilityKey defaultRenderPassCompatibilityKey() {
        return VulkanRenderPassCompatibilityKey.framebuffer(
            List.of(VK10.VK_FORMAT_R8G8B8A8_UNORM),
            VK10.VK_FORMAT_UNDEFINED,
            false
        );
    }

    private java.util.Optional<PipelineDescriptor.BlendState> blendStateForAttachment(
        PipelineDescriptor.PortableState portableState,
        int colorAttachmentIndex
    ) {
        java.util.Optional<java.util.Optional<PipelineDescriptor.BlendState>> sharedOverride =
            SharedChunkProgramOverrides.indexedBlendState(portableState.location(), colorAttachmentIndex);
        if (sharedOverride.isPresent()) {
            return sharedOverride.get();
        }

        if (portableState.blendState().isPresent()) {
            return portableState.blendState();
        }

        IndexedBlendState indexedState = effectiveBlendStateForAttachment(colorAttachmentIndex);
        if (indexedState == null) {
            return java.util.Optional.empty();
        }
        if (!indexedState.enabled()) {
            return java.util.Optional.empty();
        }

        SourceFactor srcRgb = sourceFactorFromLegacyGl(indexedState.srcRgb());
        DestFactor dstRgb = destFactorFromLegacyGl(indexedState.dstRgb());
        SourceFactor srcAlpha = sourceFactorFromLegacyGl(indexedState.srcAlpha());
        DestFactor dstAlpha = destFactorFromLegacyGl(indexedState.dstAlpha());
        if (srcRgb == null || dstRgb == null || srcAlpha == null || dstAlpha == null) {
            return java.util.Optional.empty();
        }

        return java.util.Optional.of(new PipelineDescriptor.BlendState(srcRgb, dstRgb, srcAlpha, dstAlpha));
    }

    private IndexedBlendState effectiveBlendStateForAttachment(int colorAttachmentIndex) {
        IndexedBlendState indexedState = indexedBlendStates.get(colorAttachmentIndex);
        if (indexedState != null) {
            return indexedState;
        }

        if (!pendingBlendEnabled) {
            return null;
        }

        return new IndexedBlendState(
            true,
            pendingBlendSrcRgb,
            pendingBlendDstRgb,
            pendingBlendSrcAlpha,
            pendingBlendDstAlpha
        );
    }

    public net.vulkanic.VulkanicDrawStateSnapshot.TranslatedPipelineState describeTranslatedPipelineState(
        PipelineDescriptor descriptor,
        int colorAttachmentCount
    ) {
        if (descriptor == null) {
            throw new IllegalArgumentException("descriptor must not be null");
        }

        VulkanPipelineState pipelineState = VulkanPipelineState.from(
            descriptor.getPortableState(),
            Math.max(0, colorAttachmentCount),
            VulkanBackend::toDiagnosticVkPolygonMode,
            this::blendStateForAttachment
        );

        return new net.vulkanic.VulkanicDrawStateSnapshot.TranslatedPipelineState(
            vkCullModeName(pipelineState.cullMode()),
            vkFrontFaceName(pipelineState.frontFace()),
            pipelineState.depthTestEnabled(),
            pipelineState.depthWriteEnabled(),
            vkCompareOpName(pipelineState.depthCompareOp()),
            vkColorWriteMaskName(pipelineState.colorWriteMask()),
            pipelineState.colorBlendAttachments().size()
        );
    }

    private static String vkCullModeName(int cullMode) {
        return switch (cullMode) {
            case VK10.VK_CULL_MODE_NONE -> "VK_CULL_MODE_NONE";
            case VK10.VK_CULL_MODE_FRONT_BIT -> "VK_CULL_MODE_FRONT_BIT";
            case VK10.VK_CULL_MODE_BACK_BIT -> "VK_CULL_MODE_BACK_BIT";
            case VK10.VK_CULL_MODE_FRONT_AND_BACK -> "VK_CULL_MODE_FRONT_AND_BACK";
            default -> "VK_CULL_MODE_UNKNOWN(" + cullMode + ")";
        };
    }

    private static String vkFrontFaceName(int frontFace) {
        return switch (frontFace) {
            case VK10.VK_FRONT_FACE_COUNTER_CLOCKWISE -> "VK_FRONT_FACE_COUNTER_CLOCKWISE";
            case VK10.VK_FRONT_FACE_CLOCKWISE -> "VK_FRONT_FACE_CLOCKWISE";
            default -> "VK_FRONT_FACE_UNKNOWN(" + frontFace + ")";
        };
    }

    private static String vkCompareOpName(int compareOp) {
        return switch (compareOp) {
            case VK10.VK_COMPARE_OP_NEVER -> "VK_COMPARE_OP_NEVER";
            case VK10.VK_COMPARE_OP_LESS -> "VK_COMPARE_OP_LESS";
            case VK10.VK_COMPARE_OP_EQUAL -> "VK_COMPARE_OP_EQUAL";
            case VK10.VK_COMPARE_OP_LESS_OR_EQUAL -> "VK_COMPARE_OP_LESS_OR_EQUAL";
            case VK10.VK_COMPARE_OP_GREATER -> "VK_COMPARE_OP_GREATER";
            case VK10.VK_COMPARE_OP_NOT_EQUAL -> "VK_COMPARE_OP_NOT_EQUAL";
            case VK10.VK_COMPARE_OP_GREATER_OR_EQUAL -> "VK_COMPARE_OP_GREATER_OR_EQUAL";
            case VK10.VK_COMPARE_OP_ALWAYS -> "VK_COMPARE_OP_ALWAYS";
            default -> "VK_COMPARE_OP_UNKNOWN(" + compareOp + ")";
        };
    }

    private static String vkColorWriteMaskName(int mask) {
        StringBuilder builder = new StringBuilder(4);
        if ((mask & VK10.VK_COLOR_COMPONENT_R_BIT) != 0) {
            builder.append('R');
        }
        if ((mask & VK10.VK_COLOR_COMPONENT_G_BIT) != 0) {
            builder.append('G');
        }
        if ((mask & VK10.VK_COLOR_COMPONENT_B_BIT) != 0) {
            builder.append('B');
        }
        if ((mask & VK10.VK_COLOR_COMPONENT_A_BIT) != 0) {
            builder.append('A');
        }
        return builder.isEmpty() ? "NONE" : builder.toString();
    }

    private static int toDiagnosticVkPolygonMode(PolygonMode mode) {
        return switch (mode) {
            case FILL -> VK10.VK_POLYGON_MODE_FILL;
            case WIREFRAME -> VK10.VK_POLYGON_MODE_LINE;
        };
    }

    private static SourceFactor sourceFactorFromLegacyGl(int factor) {
        return net.vulkanic.VulkanicBlendFactor.fromLegacyGlConstant(factor)
            .map(VulkanBackend::toSourceFactor)
            .orElse(null);
    }

    private static DestFactor destFactorFromLegacyGl(int factor) {
        return net.vulkanic.VulkanicBlendFactor.fromLegacyGlConstant(factor)
            .flatMap(VulkanBackend::toDestFactor)
            .orElse(null);
    }

    private static net.blaze3d.platform.DepthTestFunction legacyDepthFunction(int func) {
        return switch (func) {
            case 0x0202 -> net.blaze3d.platform.DepthTestFunction.EQUAL_DEPTH_TEST; // GL_EQUAL
            case 0x0203 -> net.blaze3d.platform.DepthTestFunction.LEQUAL_DEPTH_TEST; // GL_LEQUAL
            case 0x0204 -> net.blaze3d.platform.DepthTestFunction.GREATER_DEPTH_TEST; // GL_GREATER
            case 0x0201 -> net.blaze3d.platform.DepthTestFunction.LESS_DEPTH_TEST; // GL_LESS
            default -> net.blaze3d.platform.DepthTestFunction.LESS_DEPTH_TEST;
        };
    }

    private PolygonMode legacyPolygonMode() {
        return pendingPolygonMode == 0x1B01 /* GL_LINE */
            ? PolygonMode.WIREFRAME
            : PolygonMode.FILL;
    }

    private static VertexFormat.Mode legacyVertexMode(int mode) {
        return switch (mode) {
            case VulkanicAPI.GL_LINES -> VertexFormat.Mode.LINES;
            case 0x0003 -> VertexFormat.Mode.LINE_STRIP; // GL_LINE_STRIP
            case 0x0005 -> VertexFormat.Mode.TRIANGLE_STRIP; // GL_TRIANGLE_STRIP
            case VulkanicAPI.GL_TRIANGLE_FAN -> VertexFormat.Mode.TRIANGLE_FAN;
            case 0x0007 -> VertexFormat.Mode.QUADS; // GL_QUADS
            default -> VertexFormat.Mode.TRIANGLES;
        };
    }

    private static int attributeByteSize(int size, int type) {
        if (size <= 0) {
            throw new IllegalArgumentException("attribute size must be > 0");
        }
        int bytesPerComponent = switch (type) {
            case VulkanicAPI.GL_BYTE, VulkanicAPI.GL_UNSIGNED_BYTE -> 1;
            case VulkanicAPI.GL_SHORT, VulkanicAPI.GL_UNSIGNED_SHORT, VulkanicAPI.GL_HALF_FLOAT -> 2;
            case VulkanicAPI.GL_INT, VulkanicAPI.GL_UNSIGNED_INT, VulkanicAPI.GL_FLOAT -> 4;
            case VulkanicAPI.GL_DOUBLE -> 8;
            default -> throw new IllegalArgumentException("Unsupported vertex attribute GL type: " + type);
        };
        return size * bytesPerComponent;
    }

    private static PipelineDescriptor.VertexAttributeFormat toVertexAttributeFormat(
        int type,
        int size,
        boolean normalized,
        boolean integer
    ) {
        return switch (type) {
            case VulkanicAPI.GL_UNSIGNED_BYTE -> switch (size) {
                case 1 -> integer ? PipelineDescriptor.VertexAttributeFormat.R8_UINT : PipelineDescriptor.VertexAttributeFormat.R8_UNORM;
                case 2 -> integer ? PipelineDescriptor.VertexAttributeFormat.R8G8_UINT : PipelineDescriptor.VertexAttributeFormat.R8G8_UNORM;
                case 3 -> integer ? PipelineDescriptor.VertexAttributeFormat.R8G8B8_UINT : PipelineDescriptor.VertexAttributeFormat.R8G8B8_UNORM;
                case 4 -> integer ? PipelineDescriptor.VertexAttributeFormat.R8G8B8A8_UINT : PipelineDescriptor.VertexAttributeFormat.R8G8B8A8_UNORM;
                default -> throw new IllegalArgumentException("Unsupported unsigned-byte attribute size: " + size);
            };
            case VulkanicAPI.GL_BYTE -> switch (size) {
                case 1 -> integer ? PipelineDescriptor.VertexAttributeFormat.R8_SINT : PipelineDescriptor.VertexAttributeFormat.R8_SNORM;
                case 2 -> integer ? PipelineDescriptor.VertexAttributeFormat.R8G8_SINT : PipelineDescriptor.VertexAttributeFormat.R8G8_SNORM;
                case 3 -> integer ? PipelineDescriptor.VertexAttributeFormat.R8G8B8_SINT : PipelineDescriptor.VertexAttributeFormat.R8G8B8_SNORM;
                case 4 -> integer ? PipelineDescriptor.VertexAttributeFormat.R8G8B8A8_SINT : PipelineDescriptor.VertexAttributeFormat.R8G8B8A8_SNORM;
                default -> throw new IllegalArgumentException("Unsupported byte attribute size: " + size);
            };
            case VulkanicAPI.GL_UNSIGNED_SHORT -> switch (size) {
                case 1 -> integer ? PipelineDescriptor.VertexAttributeFormat.R16_UINT : PipelineDescriptor.VertexAttributeFormat.R16_USCALED;
                case 2 -> integer ? PipelineDescriptor.VertexAttributeFormat.R16G16_UINT : PipelineDescriptor.VertexAttributeFormat.R16G16_USCALED;
                case 3 -> integer ? PipelineDescriptor.VertexAttributeFormat.R16G16B16_UINT : PipelineDescriptor.VertexAttributeFormat.R16G16B16_USCALED;
                case 4 -> integer ? PipelineDescriptor.VertexAttributeFormat.R16G16B16A16_UINT : PipelineDescriptor.VertexAttributeFormat.R16G16B16A16_USCALED;
                default -> throw new IllegalArgumentException("Unsupported unsigned-short attribute size: " + size);
            };
            case VulkanicAPI.GL_SHORT -> switch (size) {
                case 1 -> integer ? PipelineDescriptor.VertexAttributeFormat.R16_SINT : PipelineDescriptor.VertexAttributeFormat.R16_SSCALED;
                case 2 -> integer ? PipelineDescriptor.VertexAttributeFormat.R16G16_SINT : PipelineDescriptor.VertexAttributeFormat.R16G16_SSCALED;
                case 3 -> integer ? PipelineDescriptor.VertexAttributeFormat.R16G16B16_SINT : PipelineDescriptor.VertexAttributeFormat.R16G16B16_SSCALED;
                case 4 -> integer ? PipelineDescriptor.VertexAttributeFormat.R16G16B16A16_SINT : PipelineDescriptor.VertexAttributeFormat.R16G16B16A16_SSCALED;
                default -> throw new IllegalArgumentException("Unsupported short attribute size: " + size);
            };
            case VulkanicAPI.GL_INT -> switch (size) {
                case 1 -> PipelineDescriptor.VertexAttributeFormat.R32_SINT;
                case 2 -> PipelineDescriptor.VertexAttributeFormat.R32G32_SINT;
                case 3 -> PipelineDescriptor.VertexAttributeFormat.R32G32B32_SINT;
                case 4 -> PipelineDescriptor.VertexAttributeFormat.R32G32B32A32_SINT;
                default -> throw new IllegalArgumentException("Unsupported int attribute size: " + size);
            };
            case VulkanicAPI.GL_UNSIGNED_INT -> switch (size) {
                case 1 -> PipelineDescriptor.VertexAttributeFormat.R32_UINT;
                case 2 -> PipelineDescriptor.VertexAttributeFormat.R32G32_UINT;
                case 3 -> PipelineDescriptor.VertexAttributeFormat.R32G32B32_UINT;
                case 4 -> PipelineDescriptor.VertexAttributeFormat.R32G32B32A32_UINT;
                default -> throw new IllegalArgumentException("Unsupported unsigned-int attribute size: " + size);
            };
            case VulkanicAPI.GL_FLOAT -> switch (size) {
                case 1 -> PipelineDescriptor.VertexAttributeFormat.R32_SFLOAT;
                case 2 -> PipelineDescriptor.VertexAttributeFormat.R32G32_SFLOAT;
                case 3 -> PipelineDescriptor.VertexAttributeFormat.R32G32B32_SFLOAT;
                case 4 -> PipelineDescriptor.VertexAttributeFormat.R32G32B32A32_SFLOAT;
                default -> throw new IllegalArgumentException("Unsupported float attribute size: " + size);
            };
            default -> throw new IllegalArgumentException("Unsupported vertex attribute GL type: " + type);
        };
    }

    private static SourceFactor toSourceFactor(net.vulkanic.VulkanicBlendFactor factor) {
        return switch (factor) {
            case ZERO -> SourceFactor.ZERO;
            case ONE -> SourceFactor.ONE;
            case SRC_COLOR -> SourceFactor.SRC_COLOR;
            case ONE_MINUS_SRC_COLOR -> SourceFactor.ONE_MINUS_SRC_COLOR;
            case DST_COLOR -> SourceFactor.DST_COLOR;
            case ONE_MINUS_DST_COLOR -> SourceFactor.ONE_MINUS_DST_COLOR;
            case SRC_ALPHA -> SourceFactor.SRC_ALPHA;
            case ONE_MINUS_SRC_ALPHA -> SourceFactor.ONE_MINUS_SRC_ALPHA;
            case DST_ALPHA -> SourceFactor.DST_ALPHA;
            case ONE_MINUS_DST_ALPHA -> SourceFactor.ONE_MINUS_DST_ALPHA;
            case SRC_ALPHA_SATURATE -> SourceFactor.SRC_ALPHA_SATURATE;
            case CONSTANT_COLOR -> SourceFactor.CONSTANT_COLOR;
            case ONE_MINUS_CONSTANT_COLOR -> SourceFactor.ONE_MINUS_CONSTANT_COLOR;
            case CONSTANT_ALPHA -> SourceFactor.CONSTANT_ALPHA;
            case ONE_MINUS_CONSTANT_ALPHA -> SourceFactor.ONE_MINUS_CONSTANT_ALPHA;
        };
    }

    private static java.util.Optional<DestFactor> toDestFactor(net.vulkanic.VulkanicBlendFactor factor) {
        return switch (factor) {
            case ZERO -> java.util.Optional.of(DestFactor.ZERO);
            case ONE -> java.util.Optional.of(DestFactor.ONE);
            case SRC_COLOR -> java.util.Optional.of(DestFactor.SRC_COLOR);
            case ONE_MINUS_SRC_COLOR -> java.util.Optional.of(DestFactor.ONE_MINUS_SRC_COLOR);
            case DST_COLOR -> java.util.Optional.of(DestFactor.DST_COLOR);
            case ONE_MINUS_DST_COLOR -> java.util.Optional.of(DestFactor.ONE_MINUS_DST_COLOR);
            case SRC_ALPHA -> java.util.Optional.of(DestFactor.SRC_ALPHA);
            case ONE_MINUS_SRC_ALPHA -> java.util.Optional.of(DestFactor.ONE_MINUS_SRC_ALPHA);
            case DST_ALPHA -> java.util.Optional.of(DestFactor.DST_ALPHA);
            case ONE_MINUS_DST_ALPHA -> java.util.Optional.of(DestFactor.ONE_MINUS_DST_ALPHA);
            case CONSTANT_COLOR -> java.util.Optional.of(DestFactor.CONSTANT_COLOR);
            case ONE_MINUS_CONSTANT_COLOR -> java.util.Optional.of(DestFactor.ONE_MINUS_CONSTANT_COLOR);
            case CONSTANT_ALPHA -> java.util.Optional.of(DestFactor.CONSTANT_ALPHA);
            case ONE_MINUS_CONSTANT_ALPHA -> java.util.Optional.of(DestFactor.ONE_MINUS_CONSTANT_ALPHA);
            case SRC_ALPHA_SATURATE -> java.util.Optional.empty();
        };
    }

    private static int toLegacyGlBlendFactor(net.vulkanic.VulkanicBlendFactor factor) {
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

    private static @Nullable PipelineDescriptor descriptorVariantDescriptor(PrecompiledPipelineState state, PipelineDescriptor descriptor) {
        if (descriptor.hasSpirvModules()) {
            return descriptor;
        }
        if (state.descriptor == null) {
            return null;
        }
        return state.descriptor.withResourceLayout(descriptor.getResourceLayout());
    }

    private static boolean matchesStableDescriptor(PrecompiledPipelineState state, @Nullable PipelineDescriptor descriptor) {
        return descriptor != null
            && state.stableCacheKey != null
            && state.stableCacheKey.equals(descriptor.getStableCacheKey());
    }

    public void bindPipelineResources(CommandContext ctx,
            PipelineHandle pipeline,
            PipelineDescriptor descriptor,
            net.vulkanic.PipelineResourceBindings bindings) {
        long commandBufferHandle = requireVulkanCommandBufferHandle("bindPipelineResources", ctx);

        if (pipeline != null && !pipeline.isValid()) {
            throw new IllegalArgumentException("Cannot bind resources for an invalid pipeline handle");
        }
        if (descriptor == null) {
            throw new IllegalArgumentException("descriptor must not be null");
        }
        if (bindings == null) {
            throw new IllegalArgumentException("bindings must not be null");
        }

        PipelineDescriptor.ResourceLayout layout = descriptor.getResourceLayout();
        bindings.validateAgainst(layout);

        for (PipelineDescriptor.ResourceBinding resourceBinding : layout.bindings()) {
            switch (resourceBinding.type()) {
                case SAMPLER, COMPARISON_SAMPLER -> {
                    net.vulkanic.PipelineResourceBindings.SamplerBinding samplerBinding =
                        bindings.getSamplerBinding(resourceBinding.name())
                            .orElseThrow(() -> new IllegalStateException(
                                "Missing sampler binding for '" + resourceBinding.name() + "' after validation"));

                    net.vulkanic.VulkanicTextureView textureView = samplerBinding.textureView();
                    if (textureView == null) {
                        throw new IllegalArgumentException(
                            "Sampler binding '" + resourceBinding.name() + "' must provide a VulkanicTextureView in Vulkan backend");
                    }
                    if (textureView.isClosed()) {
                        throw new IllegalStateException(
                            "Sampler binding '" + resourceBinding.name() + "' uses a closed texture view");
                    }

                    net.vulkanic.VulkanicTexture texture = textureView.texture();
                    if (texture.isClosed()) {
                        throw new IllegalStateException(
                            "Sampler binding '" + resourceBinding.name() + "' uses a closed texture");
                    }
                    if ((texture.usage() & net.vulkanic.VulkanicTexture.USAGE_TEXTURE_BINDING) == 0) {
                        throw new IllegalArgumentException(
                            "Sampler binding '" + resourceBinding.name() + "' texture must include USAGE_TEXTURE_BINDING");
                    }
                }
                case UNIFORM_BUFFER -> {
                    VulkanicBufferSlice slice = bindings.getUniformBufferBinding(resourceBinding.name())
                        .orElseThrow(() -> new IllegalStateException(
                            "Missing uniform-buffer binding for '" + resourceBinding.name() + "' after validation"));

                    if (!(slice.buffer() instanceof VulkanBuffer vulkanBuffer)) {
                        throw new IllegalArgumentException(
                            "Uniform-buffer binding '" + resourceBinding.name()
                                + "' must use VulkanBuffer in Vulkan backend");
                    }
                    if (vulkanBuffer.isClosed()) {
                        throw new IllegalStateException(
                            "Uniform-buffer binding '" + resourceBinding.name() + "' uses a closed VulkanBuffer");
                    }
                    if (slice.offset() < 0 || slice.length() <= 0
                        || ((long) slice.offset() + slice.length()) > vulkanBuffer.size()) {
                        throw new IllegalArgumentException(
                            "Uniform-buffer binding '" + resourceBinding.name()
                                + "' slice [offset=" + slice.offset() + ", length=" + slice.length()
                                + "] is outside buffer size " + vulkanBuffer.size());
                    }
                }
                case TEXEL_BUFFER -> bindings.getTexelBufferBinding(resourceBinding.name())
                    .orElseThrow(() -> new IllegalStateException(
                        "Missing texel-buffer binding for '" + resourceBinding.name() + "' after validation"));
            }
        }

        VulkanPipelineHandle vulkanPipeline = null;
        if (pipeline != null) {
            if (!(pipeline instanceof VulkanPipelineHandle typedPipeline)) {
                throw new IllegalArgumentException(
                    "Vulkan backend requires VulkanPipelineHandle for descriptor binding, got: "
                        + pipeline.getClass().getName());
            }
            vulkanPipeline = typedPipeline;
        }

        boundPipelineResourcesByCommandBuffer.put(
            commandBufferHandle,
            new BoundPipelineResources(pipeline, descriptor, bindings));

        if (vulkanPipeline != null) {
            ensureNativeReady("bindPipelineResources");
            NativeSpine spine = nativeSpine;
            if (spine == null) {
                throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
            }
            spine.updateAndBindDescriptorSet(commandBufferHandle, vulkanPipeline, descriptor, bindings);
        }
    }

    public void applyResourceBarriers(CommandContext ctx,
            VulkanicResourceBarriers barriers) {
        long commandBufferHandle = requireVulkanCommandBufferHandle("applyResourceBarriers", ctx);

        VulkanicResourceBarriers safeBarriers = Objects.requireNonNull(barriers, "barriers must not be null");

        ensureNativeReady("applyResourceBarriers");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }

        spine.applyResourceBarriers(commandBufferHandle, safeBarriers);
    }

    private static long requireVulkanCommandBufferHandle(String operation, CommandContext ctx) {
        if (!(ctx instanceof VulkanCommandContext)) {
            throw new IllegalArgumentException(
                operation + " requires VulkanCommandContext when Vulkan backend is selected; got: "
                    + (ctx == null ? "null" : ctx.getClass().getName()));
        }
        return ctx.getHandle();
    }

    private static BarrierMasks toVkBarrierMasks(VulkanicResourceBarriers barriers) {
        int srcStageMask = 0;
        int dstStageMask = 0;
        int srcAccessMask = 0;
        int dstAccessMask = 0;

        int shaderStages = VK10.VK_PIPELINE_STAGE_VERTEX_SHADER_BIT
            | VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT
            | VK10.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT;

        for (VulkanicResourceBarriers.Barrier barrier : barriers.barriers()) {
            switch (barrier) {
                case SHADER_IMAGE_ACCESS -> {
                    srcStageMask |= shaderStages;
                    dstStageMask |= shaderStages;
                    srcAccessMask |= VK10.VK_ACCESS_SHADER_WRITE_BIT;
                    dstAccessMask |= VK10.VK_ACCESS_SHADER_READ_BIT | VK10.VK_ACCESS_SHADER_WRITE_BIT;
                }
                case TEXTURE_FETCH -> {
                    srcStageMask |= shaderStages | VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;
                    dstStageMask |= shaderStages;
                    srcAccessMask |= VK10.VK_ACCESS_SHADER_WRITE_BIT | VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT | VK10.VK_ACCESS_TRANSFER_WRITE_BIT;
                    dstAccessMask |= VK10.VK_ACCESS_SHADER_READ_BIT;
                }
                case SHADER_STORAGE -> {
                    srcStageMask |= shaderStages;
                    dstStageMask |= shaderStages;
                    srcAccessMask |= VK10.VK_ACCESS_SHADER_WRITE_BIT;
                    dstAccessMask |= VK10.VK_ACCESS_SHADER_READ_BIT | VK10.VK_ACCESS_SHADER_WRITE_BIT;
                }
                default -> throw new IllegalArgumentException("Unhandled VulkanicResourceBarriers.Barrier: " + barrier);
            }
        }

        if (srcStageMask == 0) {
            srcStageMask = VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
        }
        if (dstStageMask == 0) {
            dstStageMask = VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
        }
        if (srcAccessMask == 0) {
            srcAccessMask = VK10.VK_ACCESS_MEMORY_WRITE_BIT;
        }
        if (dstAccessMask == 0) {
            dstAccessMask = VK10.VK_ACCESS_MEMORY_READ_BIT;
        }

        return new BarrierMasks(srcStageMask, dstStageMask, srcAccessMask, dstAccessMask);
    }

    private static final class BarrierMasks {
        private final int srcStageMask;
        private final int dstStageMask;
        private final int srcAccessMask;
        private final int dstAccessMask;

        private BarrierMasks(int srcStageMask, int dstStageMask, int srcAccessMask, int dstAccessMask) {
            this.srcStageMask = srcStageMask;
            this.dstStageMask = dstStageMask;
            this.srcAccessMask = srcAccessMask;
            this.dstAccessMask = dstAccessMask;
        }

        private int srcStageMask() {
            return srcStageMask;
        }

        private int dstStageMask() {
            return dstStageMask;
        }

        private int srcAccessMask() {
            return srcAccessMask;
        }

        private int dstAccessMask() {
            return dstAccessMask;
        }
    }

    private static final class ResolvedRenderTargets {
        private final VulkanTextureView colorView;
        private final VulkanicTexture colorTexture;
        private final VulkanTextureView depthView;
        private final VulkanicTexture depthTexture;
        private final int width;
        private final int height;
        private final boolean colorFeedbackLoopCapable;
        private final boolean depthFeedbackLoopCapable;

        private ResolvedRenderTargets(
            VulkanTextureView colorView,
            VulkanicTexture colorTexture,
            VulkanTextureView depthView,
            VulkanicTexture depthTexture,
            int width,
            int height
        ) {
            this(colorView, colorTexture, depthView, depthTexture, width, height, false, false);
        }

        private ResolvedRenderTargets(
            VulkanTextureView colorView,
            VulkanicTexture colorTexture,
            VulkanTextureView depthView,
            VulkanicTexture depthTexture,
            int width,
            int height,
            boolean colorFeedbackLoopCapable,
            boolean depthFeedbackLoopCapable
        ) {
            this.colorView = colorView;
            this.colorTexture = colorTexture;
            this.depthView = depthView;
            this.depthTexture = depthTexture;
            this.width = width;
            this.height = height;
            this.colorFeedbackLoopCapable = colorFeedbackLoopCapable;
            this.depthFeedbackLoopCapable = depthFeedbackLoopCapable;
        }

        private boolean hasDepthTarget() {
            return depthView != null;
        }

        private boolean hasFeedbackLoopTarget() {
            return colorFeedbackLoopCapable || (hasDepthTarget() && depthFeedbackLoopCapable);
        }
    }

    private enum VulkanRenderTargetPlanSource {
        TEXTURE_VIEW,
        FRAMEBUFFER,
        RENDER_TARGET_DESCRIPTOR
    }

    /**
     * Normalized Vulkan render-target contract for every render-pass entrypoint.
     *
     * <p>The source-specific resolved targets are retained so existing native
     * behavior stays stable, but callers must pass through this object before
     * creating a render pass or a target-specific pipeline. That gives Vulkan a
     * single place to compare attachment order, formats, usage intent, and
     * feedback-loop requirements.</p>
     */
    private static final class VulkanRenderTargetPlan {
        private final VulkanRenderTargetPlanSource source;
        private final String label;
        @Nullable
        private final VulkanicRenderPassDescriptor renderPassDescriptor;
        @Nullable
        private final ResolvedRenderTargets textureViewTargets;
        @Nullable
        private final ResolvedFramebufferTargets framebufferTargets;

        private VulkanRenderTargetPlan(
            VulkanRenderTargetPlanSource source,
            String label,
            @Nullable VulkanicRenderPassDescriptor renderPassDescriptor,
            @Nullable ResolvedRenderTargets textureViewTargets,
            @Nullable ResolvedFramebufferTargets framebufferTargets
        ) {
            this.source = Objects.requireNonNull(source, "source must not be null");
            this.label = Objects.requireNonNull(label, "label must not be null");
            this.renderPassDescriptor = renderPassDescriptor;
            this.textureViewTargets = textureViewTargets;
            this.framebufferTargets = framebufferTargets;

            boolean textureViewPlan = renderPassDescriptor != null && textureViewTargets != null && framebufferTargets == null;
            boolean framebufferPlan = renderPassDescriptor == null && textureViewTargets == null && framebufferTargets != null;
            if (source == VulkanRenderTargetPlanSource.TEXTURE_VIEW) {
                if (!textureViewPlan) {
                    throw new IllegalArgumentException("Texture-view render target plans require texture-view targets");
                }
            } else if (!framebufferPlan) {
                throw new IllegalArgumentException("Framebuffer-style render target plans require framebuffer targets");
            }
        }

        private static VulkanRenderTargetPlan textureView(
            VulkanicRenderPassDescriptor descriptor,
            ResolvedRenderTargets targets
        ) {
            return new VulkanRenderTargetPlan(
                VulkanRenderTargetPlanSource.TEXTURE_VIEW,
                descriptor.label().get(),
                descriptor,
                targets,
                null
            );
        }

        private static VulkanRenderTargetPlan framebuffer(Supplier<String> label, ResolvedFramebufferTargets targets) {
            return new VulkanRenderTargetPlan(
                VulkanRenderTargetPlanSource.FRAMEBUFFER,
                label.get(),
                null,
                null,
                targets
            );
        }

        private static VulkanRenderTargetPlan renderTargetDescriptor(
            VulkanicRenderTargetDescriptor descriptor,
            ResolvedFramebufferTargets targets
        ) {
            return new VulkanRenderTargetPlan(
                VulkanRenderTargetPlanSource.RENDER_TARGET_DESCRIPTOR,
                descriptor.label().get(),
                null,
                null,
                targets
            );
        }

        private VulkanicRenderPassDescriptor requireRenderPassDescriptor() {
            if (renderPassDescriptor == null) {
                throw new IllegalStateException("Render target plan " + source + " has no texture-view render pass descriptor");
            }
            return renderPassDescriptor;
        }

        private ResolvedRenderTargets requireTextureViewTargets() {
            if (textureViewTargets == null) {
                throw new IllegalStateException("Render target plan " + source + " has no texture-view targets");
            }
            return textureViewTargets;
        }

        private ResolvedFramebufferTargets requireFramebufferTargets() {
            if (framebufferTargets == null) {
                throw new IllegalStateException("Render target plan " + source + " has no framebuffer targets");
            }
            return framebufferTargets;
        }

        private List<Integer> colorFormats() {
            if (framebufferTargets != null) {
                return framebufferTargets.colorFormats();
            }
            return List.of(NativeSpine.toVkFormat(requireTextureViewTargets().colorTexture.getVulkanicFormat()));
        }

        private int depthFormat() {
            if (framebufferTargets != null) {
                return framebufferTargets.hasDepthTarget() ? framebufferTargets.depthTexture.vkFormat : VK10.VK_FORMAT_UNDEFINED;
            }
            ResolvedRenderTargets targets = requireTextureViewTargets();
            return targets.hasDepthTarget()
                ? NativeSpine.toVkFormat(targets.depthTexture.getVulkanicFormat())
                : VK10.VK_FORMAT_UNDEFINED;
        }

        private boolean feedbackLoopCompatible() {
            return framebufferTargets != null && framebufferTargets.hasFeedbackLoopTarget();
        }

        private VulkanRenderPassCompatibilityKey compatibilityKey() {
            if (framebufferTargets != null) {
                return VulkanRenderPassCompatibilityKey.framebuffer(
                    framebufferTargets.colorFormats(),
                    framebufferTargets.hasDepthTarget() ? framebufferTargets.depthTexture.vkFormat : VK10.VK_FORMAT_UNDEFINED,
                    framebufferTargets.hasFeedbackLoopTarget()
                );
            }

            ResolvedRenderTargets targets = requireTextureViewTargets();
            return VulkanRenderPassCompatibilityKey.textureView(
                colorFormats(),
                targets.hasDepthTarget()
                    ? NativeSpine.toVkFormat(targets.depthTexture.getVulkanicFormat())
                    : VK10.VK_FORMAT_UNDEFINED,
                targets.hasFeedbackLoopTarget()
            );
        }

        private String compatibilitySignature() {
            if (framebufferTargets != null) {
                return source + ":" + framebufferTargets.compatibilitySignature();
            }

            ResolvedRenderTargets targets = requireTextureViewTargets();
            VulkanicRenderPassDescriptor descriptor = requireRenderPassDescriptor();
            StringBuilder builder = new StringBuilder(160);
            builder.append(source)
                .append(":extent=").append(targets.width).append('x').append(targets.height)
                .append(";colors=1")
                .append(";c0{format=0x")
                .append(Integer.toHexString(NativeSpine.toVkFormat(targets.colorTexture.getVulkanicFormat())))
                .append(",load=").append(descriptor.colorAttachment().loadOp())
                .append(",store=").append(descriptor.colorAttachment().storeOp())
                .append(",initialUsage=").append(descriptor.colorAttachment().initialUsage())
                .append(",passUsage=").append(descriptor.colorAttachment().passUsage())
                .append(",finalUsage=").append(descriptor.colorAttachment().finalUsage())
                .append(",clear=").append(optionalIntToString(descriptor.colorAttachment().clearColor()))
                .append('}')
                .append(";depth=");
            if (targets.hasDepthTarget()) {
                VulkanicRenderPassDescriptor.DepthAttachment depth = descriptor.depthAttachment();
                builder.append("{format=0x")
                    .append(Integer.toHexString(NativeSpine.toVkFormat(targets.depthTexture.getVulkanicFormat())))
                    .append(",load=").append(depth.loadOp())
                    .append(",store=").append(depth.storeOp())
                    .append(",initialUsage=").append(depth.initialUsage())
                    .append(",passUsage=").append(depth.passUsage())
                    .append(",finalUsage=").append(depth.finalUsage())
                    .append(",clear=").append(optionalDoubleToString(depth.clearDepth()))
                    .append('}');
            } else {
                builder.append("none");
            }
            return builder.toString();
        }

        private static String optionalIntToString(OptionalInt value) {
            return value.isPresent() ? "0x" + Integer.toHexString(value.getAsInt()) : "empty";
        }

        private static String optionalDoubleToString(OptionalDouble value) {
            return value.isPresent() ? Double.toString(value.getAsDouble()) : "empty";
        }
    }

    private static final class ResolvedFramebufferTargets {
        private final List<NativeSpine.LegacyTextureObject> colorTextures;
        private final List<Long> colorViewHandles;
        private final NativeSpine.LegacyTextureObject depthTexture;
        private final long depthViewHandle;
        private final int width;
        private final int height;
        private final List<VulkanicRenderPassDescriptor.LoadOp> colorLoadOps;
        private final List<VulkanicRenderPassDescriptor.StoreOp> colorStoreOps;
        private final List<OptionalInt> colorClearColors;
        private final List<VulkanicResourceUsage> colorInitialUsages;
        private final List<VulkanicResourceUsage> colorPassUsages;
        private final List<VulkanicResourceUsage> colorFinalUsages;
        private final VulkanicRenderPassDescriptor.LoadOp depthLoadOp;
        private final VulkanicRenderPassDescriptor.StoreOp depthStoreOp;
        private final OptionalDouble depthClearValue;
        private final VulkanicResourceUsage depthInitialUsage;
        private final VulkanicResourceUsage depthPassUsage;
        private final VulkanicResourceUsage depthFinalUsage;

        private ResolvedFramebufferTargets(
            List<NativeSpine.LegacyTextureObject> colorTextures,
            List<Long> colorViewHandles,
            @Nullable NativeSpine.LegacyTextureObject depthTexture,
            long depthViewHandle,
            int width,
            int height
        ) {
            this(
                colorTextures,
                colorViewHandles,
                depthTexture,
                depthViewHandle,
                width,
                height,
                java.util.Collections.nCopies(colorTextures.size(), VulkanicRenderPassDescriptor.LoadOp.LOAD),
                java.util.Collections.nCopies(colorTextures.size(), VulkanicRenderPassDescriptor.StoreOp.STORE),
                java.util.Collections.nCopies(colorTextures.size(), OptionalInt.empty()),
                java.util.Collections.nCopies(colorTextures.size(), VulkanicResourceUsage.INFERRED),
                java.util.Collections.nCopies(colorTextures.size(), VulkanicResourceUsage.INFERRED),
                java.util.Collections.nCopies(colorTextures.size(), VulkanicResourceUsage.INFERRED),
                VulkanicRenderPassDescriptor.LoadOp.LOAD,
                VulkanicRenderPassDescriptor.StoreOp.STORE,
                OptionalDouble.empty(),
                VulkanicResourceUsage.INFERRED,
                VulkanicResourceUsage.INFERRED,
                VulkanicResourceUsage.INFERRED
            );
        }

        private ResolvedFramebufferTargets(
            List<NativeSpine.LegacyTextureObject> colorTextures,
            List<Long> colorViewHandles,
            @Nullable NativeSpine.LegacyTextureObject depthTexture,
            long depthViewHandle,
            int width,
            int height,
            List<VulkanicRenderPassDescriptor.LoadOp> colorLoadOps,
            List<VulkanicRenderPassDescriptor.StoreOp> colorStoreOps,
            List<OptionalInt> colorClearColors,
            List<VulkanicResourceUsage> colorInitialUsages,
            List<VulkanicResourceUsage> colorPassUsages,
            List<VulkanicResourceUsage> colorFinalUsages,
            VulkanicRenderPassDescriptor.LoadOp depthLoadOp,
            VulkanicRenderPassDescriptor.StoreOp depthStoreOp,
            OptionalDouble depthClearValue,
            VulkanicResourceUsage depthInitialUsage,
            VulkanicResourceUsage depthPassUsage,
            VulkanicResourceUsage depthFinalUsage
        ) {
            this.colorTextures = List.copyOf(colorTextures);
            this.colorViewHandles = List.copyOf(colorViewHandles);
            this.depthTexture = depthTexture;
            this.depthViewHandle = depthViewHandle;
            this.width = width;
            this.height = height;
            this.colorLoadOps = List.copyOf(colorLoadOps);
            this.colorStoreOps = List.copyOf(colorStoreOps);
            this.colorClearColors = List.copyOf(colorClearColors);
            this.colorInitialUsages = List.copyOf(colorInitialUsages);
            this.colorPassUsages = List.copyOf(colorPassUsages);
            this.colorFinalUsages = List.copyOf(colorFinalUsages);
            this.depthLoadOp = Objects.requireNonNull(depthLoadOp, "depthLoadOp must not be null");
            this.depthStoreOp = Objects.requireNonNull(depthStoreOp, "depthStoreOp must not be null");
            this.depthClearValue = Objects.requireNonNull(depthClearValue, "depthClearValue must not be null");
            this.depthInitialUsage = Objects.requireNonNull(depthInitialUsage, "depthInitialUsage must not be null");
            this.depthPassUsage = Objects.requireNonNull(depthPassUsage, "depthPassUsage must not be null");
            this.depthFinalUsage = Objects.requireNonNull(depthFinalUsage, "depthFinalUsage must not be null");
            if (this.colorLoadOps.size() != this.colorTextures.size()
                || this.colorStoreOps.size() != this.colorTextures.size()
                || this.colorClearColors.size() != this.colorTextures.size()
                || this.colorInitialUsages.size() != this.colorTextures.size()
                || this.colorPassUsages.size() != this.colorTextures.size()
                || this.colorFinalUsages.size() != this.colorTextures.size()) {
                throw new IllegalArgumentException("Color attachment metadata must match color attachment count");
            }
        }

        private int colorAttachmentCount() {
            return colorTextures.size();
        }

        private boolean hasDepthTarget() {
            return depthTexture != null && depthViewHandle != VK10.VK_NULL_HANDLE;
        }

        private boolean hasFeedbackLoopTarget() {
            return colorTextures.stream().anyMatch(texture -> texture.feedbackLoopCapable)
                || (hasDepthTarget() && depthTexture.feedbackLoopCapable);
        }

        private List<Integer> colorFormats() {
            List<Integer> formats = new ArrayList<>(colorTextures.size());
            for (NativeSpine.LegacyTextureObject colorTexture : colorTextures) {
                formats.add(colorTexture.vkFormat);
            }
            return List.copyOf(formats);
        }

        private VulkanicRenderPassDescriptor.LoadOp colorLoadOp(int index) {
            return colorLoadOps.get(index);
        }

        private VulkanicRenderPassDescriptor.StoreOp colorStoreOp(int index) {
            return colorStoreOps.get(index);
        }

        private OptionalInt colorClearColor(int index) {
            return colorClearColors.get(index);
        }

        private boolean colorAttachmentMatches(ResolvedFramebufferTargets other, int thisIndex, int otherIndex) {
            NativeSpine.LegacyTextureObject thisTexture = colorTextures.get(thisIndex);
            NativeSpine.LegacyTextureObject otherTexture = other.colorTextures.get(otherIndex);
            return thisTexture.id == otherTexture.id
                && colorViewHandles.get(thisIndex).equals(other.colorViewHandles.get(otherIndex))
                && thisTexture.vkFormat == otherTexture.vkFormat
                && thisTexture.feedbackLoopCapable == otherTexture.feedbackLoopCapable
                && colorLoadOps.get(thisIndex) == other.colorLoadOps.get(otherIndex)
                && colorStoreOps.get(thisIndex) == other.colorStoreOps.get(otherIndex)
                && colorClearColors.get(thisIndex).equals(other.colorClearColors.get(otherIndex));
        }

        private boolean depthAttachmentMatches(ResolvedFramebufferTargets other) {
            if (hasDepthTarget() != other.hasDepthTarget()) {
                return false;
            }
            if (!hasDepthTarget()) {
                return true;
            }

            return depthTexture.id == other.depthTexture.id
                && depthViewHandle == other.depthViewHandle
                && depthTexture.vkFormat == other.depthTexture.vkFormat
                && depthTexture.feedbackLoopCapable == other.depthTexture.feedbackLoopCapable
                && depthLoadOp == other.depthLoadOp
                && depthStoreOp == other.depthStoreOp
                && depthClearValue.equals(other.depthClearValue);
        }

        private VulkanicRenderPassDescriptor.LoadOp depthLoadOp() {
            return depthLoadOp;
        }

        private VulkanicRenderPassDescriptor.StoreOp depthStoreOp() {
            return depthStoreOp;
        }

        private OptionalDouble depthClearValue() {
            return depthClearValue;
        }

        private VulkanicResourceUsage colorInitialUsage(int index) {
            return colorInitialUsages.get(index);
        }

        private VulkanicResourceUsage colorPassUsage(int index) {
            return colorPassUsages.get(index);
        }

        private VulkanicResourceUsage colorFinalUsage(int index) {
            return colorFinalUsages.get(index);
        }

        private VulkanicResourceUsage depthInitialUsage() {
            return depthInitialUsage;
        }

        private VulkanicResourceUsage depthPassUsage() {
            return depthPassUsage;
        }

        private VulkanicResourceUsage depthFinalUsage() {
            return depthFinalUsage;
        }

        private String compatibilitySignature() {
            StringBuilder builder = new StringBuilder(160 + colorTextures.size() * 80);
            builder.append("extent=").append(width).append('x').append(height);
            builder.append(";colors=").append(colorTextures.size());
            for (int index = 0; index < colorTextures.size(); index++) {
                NativeSpine.LegacyTextureObject texture = colorTextures.get(index);
                builder.append(";c").append(index)
                    .append("{id=").append(texture.id)
                    .append(",view=0x").append(Long.toHexString(colorViewHandles.get(index)))
                    .append(",format=0x").append(Integer.toHexString(texture.vkFormat))
                    .append(",feedback=").append(texture.feedbackLoopCapable)
                    .append(",load=").append(colorLoadOps.get(index))
                    .append(",store=").append(colorStoreOps.get(index))
                    .append(",initialUsage=").append(colorInitialUsages.get(index))
                    .append(",passUsage=").append(colorPassUsages.get(index))
                    .append(",finalUsage=").append(colorFinalUsages.get(index))
                    .append(",clear=").append(optionalIntToString(colorClearColors.get(index)))
                    .append('}');
            }
            builder.append(";depth=");
            if (hasDepthTarget()) {
                builder.append("{id=").append(depthTexture.id)
                    .append(",view=0x").append(Long.toHexString(depthViewHandle))
                    .append(",format=0x").append(Integer.toHexString(depthTexture.vkFormat))
                    .append(",feedback=").append(depthTexture.feedbackLoopCapable)
                    .append(",load=").append(depthLoadOp)
                    .append(",store=").append(depthStoreOp)
                    .append(",initialUsage=").append(depthInitialUsage)
                    .append(",passUsage=").append(depthPassUsage)
                    .append(",finalUsage=").append(depthFinalUsage)
                    .append(",clear=").append(optionalDoubleToString(depthClearValue))
                    .append('}');
            } else {
                builder.append("none");
            }
            return builder.toString();
        }

        private static String optionalIntToString(OptionalInt value) {
            return value.isPresent() ? "0x" + Integer.toHexString(value.getAsInt()) : "empty";
        }

        private static String optionalDoubleToString(OptionalDouble value) {
            return value.isPresent() ? Double.toString(value.getAsDouble()) : "empty";
        }
    }

    private enum RenderTargetCompatibility {
        EXACT,
        DESCRIPTOR_SUFFIX,
        DESCRIPTOR_ATTACHMENTLESS,
        MISMATCH
    }

    private VulkanRenderTargetPlan resolveRenderPassDescriptorPlan(VulkanicRenderPassDescriptor descriptor) {
        VulkanicRenderPassDescriptor safeDescriptor =
            Objects.requireNonNull(descriptor, "descriptor must not be null");
        return VulkanRenderTargetPlan.textureView(safeDescriptor, resolveRenderTargets(safeDescriptor));
    }

    private ResolvedRenderTargets resolveRenderTargets(VulkanicRenderPassDescriptor descriptor) {
        VulkanTextureView colorView = requireVulkanTextureView(descriptor.colorAttachment().target(), "colorAttachment.target");
        VulkanicTexture colorTexture = requireRenderPassTexture(colorView.texture(), "colorAttachment.texture");
        if (!colorTexture.getVulkanicFormat().hasColorAspect()) {
            throw new IllegalArgumentException("Color attachment must use a color-capable texture format");
        }
        if ((colorTexture.usage() & VulkanicTexture.USAGE_RENDER_ATTACHMENT) == 0) {
            throw new IllegalArgumentException("Color attachment texture must include USAGE_RENDER_ATTACHMENT");
        }

        int width = colorView.getWidth(0);
        int height = colorView.getHeight(0);
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Render pass attachments must have non-zero dimensions");
        }
        boolean colorFeedbackLoopCapable = isFeedbackLoopCapable(colorTexture);

        VulkanicRenderPassDescriptor.DepthAttachment depthAttachment = descriptor.depthAttachment();
        if (depthAttachment == null) {
            return new ResolvedRenderTargets(
                colorView,
                colorTexture,
                null,
                null,
                width,
                height,
                colorFeedbackLoopCapable,
                false
            );
        }

        VulkanTextureView depthView = requireVulkanTextureView(depthAttachment.target(), "depthAttachment.target");
        VulkanicTexture depthTexture = requireRenderPassTexture(depthView.texture(), "depthAttachment.texture");
        if (!depthTexture.getVulkanicFormat().hasDepthAspect()) {
            throw new IllegalArgumentException("Depth attachment must use a depth-capable texture format");
        }
        if ((depthTexture.usage() & VulkanicTexture.USAGE_RENDER_ATTACHMENT) == 0) {
            throw new IllegalArgumentException("Depth attachment texture must include USAGE_RENDER_ATTACHMENT");
        }
        if (depthView.getWidth(0) != width || depthView.getHeight(0) != height) {
            throw new IllegalArgumentException("Depth attachment dimensions must match color attachment dimensions");
        }
        boolean depthFeedbackLoopCapable = isFeedbackLoopCapable(depthTexture);

        return new ResolvedRenderTargets(
            colorView,
            colorTexture,
            depthView,
            depthTexture,
            width,
            height,
            colorFeedbackLoopCapable,
            depthFeedbackLoopCapable
        );
    }

    private boolean isFeedbackLoopCapable(VulkanicTexture texture) {
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            return false;
        }
        NativeSpine.LegacyTextureObject legacyTexture = spine.tryResolveLegacyTexture(texture);
        return legacyTexture != null && legacyTexture.feedbackLoopCapable;
    }

    private VulkanRenderTargetPlan resolveFramebufferRenderTargetPlan(Supplier<String> label, int framebuffer) {
        return resolveFramebufferRenderTargetPlan(label, framebuffer, true);
    }

    private VulkanRenderTargetPlan resolveFramebufferRenderTargetPlan(
        Supplier<String> label,
        int framebuffer,
        boolean includeDepthAttachment
    ) {
        Objects.requireNonNull(label, "label must not be null");
        return VulkanRenderTargetPlan.framebuffer(label, resolveFramebufferTargets(framebuffer, includeDepthAttachment));
    }

    private ResolvedFramebufferTargets resolveFramebufferTargets(int framebuffer) {
        return resolveFramebufferTargets(framebuffer, true);
    }

    private ResolvedFramebufferTargets resolveFramebufferTargets(int framebuffer, boolean includeDepthAttachment) {
        ensureNativeReady("resolveFramebufferTargets");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        if (framebuffer == 0) {
            throw new IllegalArgumentException("Framebuffer-backed Vulkan render passes require a non-default framebuffer id");
        }

        VirtualFramebufferState state = virtualFramebufferStates.get(framebuffer);
        if (state == null) {
            throw new IllegalArgumentException("Unknown Vulkan virtual framebuffer handle: " + framebuffer);
        }

        int[] drawBuffers = framebufferDrawBuffers(framebuffer);
        List<NativeSpine.LegacyTextureObject> colorTextures = new ArrayList<>(drawBuffers.length);
        List<Long> colorViewHandles = new ArrayList<>(drawBuffers.length);
        int width = -1;
        int height = -1;

        for (int drawBuffer : drawBuffers) {
            if (drawBuffer == GL_NONE) {
                continue;
            }

            int attachment = drawBuffer == VulkanicAPI.GL_BACK ? GL_COLOR_ATTACHMENT0 : drawBuffer;
            int textureId = state.getAttachment(attachment);
            if (textureId == 0) {
                throw new IllegalStateException(
                    "Framebuffer " + framebuffer + " draw buffer 0x" + Integer.toHexString(drawBuffer)
                        + " has no attached texture"
                );
            }

            NativeSpine.LegacyTextureObject legacyTexture = spine.requireLegacyTexture(textureId);
            if (legacyTexture.imageHandle == VK10.VK_NULL_HANDLE || legacyTexture.defaultViewHandle == VK10.VK_NULL_HANDLE) {
                throw new IllegalStateException(
                    "Framebuffer color attachment texId=" + textureId + " is missing Vulkan image/view state"
                );
            }

            if (width < 0) {
                width = legacyTexture.width;
                height = legacyTexture.height;
            } else if (legacyTexture.width != width || legacyTexture.height != height) {
                throw new IllegalStateException(
                    "Framebuffer " + framebuffer + " has mismatched color attachment dimensions"
                );
            }

            colorTextures.add(legacyTexture);
            colorViewHandles.add(legacyTexture.defaultViewHandle);
        }

        if (colorTextures.isEmpty()) {
            throw new IllegalStateException(
                "Framebuffer " + framebuffer + " has no color attachments for draw buffers " + java.util.Arrays.toString(drawBuffers)
            );
        }

        int depthTextureId = state.getAttachment(GL_DEPTH_ATTACHMENT);
        if (depthTextureId == 0) {
            depthTextureId = state.getAttachment(GL_DEPTH_STENCIL_ATTACHMENT);
        }

        NativeSpine.LegacyTextureObject depthTexture = null;
        long depthViewHandle = VK10.VK_NULL_HANDLE;
        if (includeDepthAttachment && depthTextureId != 0) {
            depthTexture = spine.requireLegacyTexture(depthTextureId);
            if (depthTexture.imageHandle == VK10.VK_NULL_HANDLE || depthTexture.defaultViewHandle == VK10.VK_NULL_HANDLE) {
                throw new IllegalStateException(
                    "Framebuffer depth attachment texId=" + depthTextureId + " is missing Vulkan image/view state"
                );
            }
            if (depthTexture.width != width || depthTexture.height != height) {
                throw new IllegalStateException(
                    "Framebuffer " + framebuffer + " depth attachment dimensions must match color attachments"
                );
            }
            depthViewHandle = depthTexture.defaultViewHandle;
        }

        return new ResolvedFramebufferTargets(colorTextures, colorViewHandles, depthTexture, depthViewHandle, width, height);
    }

    private VulkanRenderTargetPlan resolveRenderTargetDescriptorPlan(VulkanicRenderTargetDescriptor descriptor) {
        VulkanicRenderTargetDescriptor safeDescriptor =
            Objects.requireNonNull(descriptor, "descriptor must not be null");
        return VulkanRenderTargetPlan.renderTargetDescriptor(safeDescriptor, resolveRenderTargetDescriptor(safeDescriptor));
    }

    private ResolvedFramebufferTargets resolveRenderTargetDescriptor(VulkanicRenderTargetDescriptor descriptor) {
        ensureNativeReady("resolveRenderTargetDescriptor");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        VulkanicRenderTargetDescriptor safeDescriptor =
            Objects.requireNonNull(descriptor, "descriptor must not be null");

        List<VulkanicRenderTargetDescriptor.ColorAttachment> colorAttachments = safeDescriptor.colorAttachments();
	        List<NativeSpine.LegacyTextureObject> colorTextures = new ArrayList<>(colorAttachments.size());
	        List<Long> colorViewHandles = new ArrayList<>(colorAttachments.size());
	        List<VulkanicRenderPassDescriptor.LoadOp> colorLoadOps = new ArrayList<>(colorAttachments.size());
	        List<VulkanicRenderPassDescriptor.StoreOp> colorStoreOps = new ArrayList<>(colorAttachments.size());
	        List<OptionalInt> colorClearColors = new ArrayList<>(colorAttachments.size());
	        List<VulkanicResourceUsage> colorInitialUsages = new ArrayList<>(colorAttachments.size());
	        List<VulkanicResourceUsage> colorPassUsages = new ArrayList<>(colorAttachments.size());
	        List<VulkanicResourceUsage> colorFinalUsages = new ArrayList<>(colorAttachments.size());
	        int width = safeDescriptor.hasExplicitExtent() ? safeDescriptor.width() : -1;
	        int height = safeDescriptor.hasExplicitExtent() ? safeDescriptor.height() : -1;

        for (int colorIndex = 0; colorIndex < colorAttachments.size(); colorIndex++) {
            VulkanicRenderTargetDescriptor.ColorAttachment attachment = colorAttachments.get(colorIndex);
            NativeSpine.LegacyTextureObject legacyTexture = spine.requireLegacyTexture(attachment.textureId());
            if (legacyTexture.imageHandle == VK10.VK_NULL_HANDLE || legacyTexture.defaultViewHandle == VK10.VK_NULL_HANDLE) {
                throw new IllegalStateException(
                    "Render-target color attachment texId=" + attachment.textureId()
                        + " is missing Vulkan image/view state"
                );
            }
            if (width < 0) {
                width = legacyTexture.width;
                height = legacyTexture.height;
            } else if (legacyTexture.width != width || legacyTexture.height != height) {
                throw new IllegalStateException("Render-target descriptor has mismatched color attachment dimensions");
            }

            colorTextures.add(legacyTexture);
	            colorViewHandles.add(legacyTexture.defaultViewHandle);
	            colorLoadOps.add(attachment.loadOp());
	            colorStoreOps.add(attachment.storeOp());
	            colorClearColors.add(attachment.clearColor());
	            colorInitialUsages.add(attachment.initialUsage());
	            colorPassUsages.add(attachment.passUsage());
	            colorFinalUsages.add(attachment.finalUsage());
	        }

        VulkanicRenderTargetDescriptor.DepthAttachment depthAttachment = safeDescriptor.depthAttachment();
        NativeSpine.LegacyTextureObject depthTexture = null;
        long depthViewHandle = VK10.VK_NULL_HANDLE;
        VulkanicRenderPassDescriptor.LoadOp depthLoadOp = VulkanicRenderPassDescriptor.LoadOp.LOAD;
        VulkanicRenderPassDescriptor.StoreOp depthStoreOp = VulkanicRenderPassDescriptor.StoreOp.STORE;
        OptionalDouble depthClearValue = OptionalDouble.empty();
        VulkanicResourceUsage depthInitialUsage = VulkanicResourceUsage.INFERRED;
        VulkanicResourceUsage depthPassUsage = VulkanicResourceUsage.INFERRED;
        VulkanicResourceUsage depthFinalUsage = VulkanicResourceUsage.INFERRED;
	        if (depthAttachment != null) {
	            depthTexture = spine.requireLegacyTexture(depthAttachment.textureId());
	            if (depthTexture.imageHandle == VK10.VK_NULL_HANDLE || depthTexture.defaultViewHandle == VK10.VK_NULL_HANDLE) {
	                throw new IllegalStateException(
	                    "Render-target depth attachment texId=" + depthAttachment.textureId()
	                        + " is missing Vulkan image/view state"
	                );
	            }
	            if (width < 0) {
	                width = depthTexture.width;
	                height = depthTexture.height;
	            } else if (depthTexture.width != width || depthTexture.height != height) {
	                throw new IllegalStateException("Render-target descriptor depth dimensions must match color attachments");
	            }
		            depthViewHandle = depthTexture.defaultViewHandle;
		            depthLoadOp = depthAttachment.loadOp();
		            depthStoreOp = depthAttachment.storeOp();
            depthClearValue = depthAttachment.clearDepth();
            depthInitialUsage = depthAttachment.initialUsage();
            depthPassUsage = depthAttachment.passUsage();
            depthFinalUsage = depthAttachment.finalUsage();
        }

        return new ResolvedFramebufferTargets(
            colorTextures,
            colorViewHandles,
            depthTexture,
            depthViewHandle,
            width,
            height,
            colorLoadOps,
            colorStoreOps,
            colorClearColors,
            colorInitialUsages,
            colorPassUsages,
            colorFinalUsages,
            depthLoadOp,
            depthStoreOp,
            depthClearValue,
            depthInitialUsage,
            depthPassUsage,
            depthFinalUsage
        );
    }

    public boolean isRenderTargetDescriptorEquivalentToFramebuffer(
        int framebuffer,
        VulkanicRenderTargetDescriptor descriptor
    ) {
        Objects.requireNonNull(descriptor, "descriptor must not be null");

        try {
            VulkanRenderTargetPlan framebufferPlan =
                resolveFramebufferRenderTargetPlan(() -> "framebuffer:" + framebuffer, framebuffer);
            VulkanRenderTargetPlan descriptorPlan = resolveRenderTargetDescriptorPlan(descriptor);
            ResolvedFramebufferTargets framebufferTargets = framebufferPlan.requireFramebufferTargets();
            ResolvedFramebufferTargets descriptorTargets = descriptorPlan.requireFramebufferTargets();
            RenderTargetCompatibility compatibility =
                classifyRenderTargetCompatibility(framebufferTargets, descriptorTargets);
            boolean equivalent = compatibility == RenderTargetCompatibility.EXACT;
            if (TRACE_RENDER_TARGET_PARITY) {
                logRenderTargetParity(
                    equivalent,
                    framebuffer,
                    descriptor.label().get(),
                    framebufferTargets.compatibilitySignature(),
                    descriptorTargets.compatibilitySignature(),
                    null
                );
            }
            return equivalent;
        } catch (RuntimeException exception) {
            if (TRACE_RENDER_TARGET_PARITY) {
                logRenderTargetParity(
                    false,
                    framebuffer,
                    descriptor.label().get(),
                    "unresolved",
                    "unresolved",
                    exception
                );
            }
            return false;
        }
    }

    public boolean isRenderTargetDescriptorCompatibleWithFramebuffer(
        int framebuffer,
        VulkanicRenderTargetDescriptor descriptor
    ) {
        Objects.requireNonNull(descriptor, "descriptor must not be null");

        try {
            VulkanRenderTargetPlan framebufferPlan =
                resolveFramebufferRenderTargetPlan(() -> "framebuffer:" + framebuffer, framebuffer);
            VulkanRenderTargetPlan descriptorPlan = resolveRenderTargetDescriptorPlan(descriptor);
            ResolvedFramebufferTargets framebufferTargets = framebufferPlan.requireFramebufferTargets();
            ResolvedFramebufferTargets descriptorTargets = descriptorPlan.requireFramebufferTargets();
            RenderTargetCompatibility compatibility =
                classifyRenderTargetCompatibility(framebufferTargets, descriptorTargets);
            boolean compatible = compatibility != RenderTargetCompatibility.MISMATCH;
            if (TRACE_RENDER_TARGET_PARITY) {
                logRenderTargetCompatibility(
                    compatible,
                    compatibility,
                    framebuffer,
                    descriptor.label().get(),
                    framebufferTargets.compatibilitySignature(),
                    descriptorTargets.compatibilitySignature(),
                    null
                );
            }
            return compatible;
        } catch (RuntimeException exception) {
            if (TRACE_RENDER_TARGET_PARITY) {
                logRenderTargetCompatibility(
                    false,
                    RenderTargetCompatibility.MISMATCH,
                    framebuffer,
                    descriptor.label().get(),
                    "unresolved",
                    "unresolved",
                    exception
                );
            }
            return false;
        }
    }

    private static RenderTargetCompatibility classifyRenderTargetCompatibility(
        ResolvedFramebufferTargets framebufferTargets,
        ResolvedFramebufferTargets descriptorTargets
    ) {
        if (framebufferTargets.width != descriptorTargets.width || framebufferTargets.height != descriptorTargets.height) {
            return RenderTargetCompatibility.MISMATCH;
        }
        if (!descriptorTargets.depthAttachmentMatches(framebufferTargets)) {
            return RenderTargetCompatibility.MISMATCH;
        }

        int framebufferColorCount = framebufferTargets.colorAttachmentCount();
        int descriptorColorCount = descriptorTargets.colorAttachmentCount();
        if (descriptorColorCount > framebufferColorCount) {
            return RenderTargetCompatibility.MISMATCH;
        }

        boolean exactColorMatch = framebufferColorCount == descriptorColorCount;
        if (exactColorMatch) {
            for (int colorIndex = 0; colorIndex < descriptorColorCount; colorIndex++) {
                if (!descriptorTargets.colorAttachmentMatches(framebufferTargets, colorIndex, colorIndex)) {
                    exactColorMatch = false;
                    break;
                }
            }
            if (exactColorMatch) {
                return RenderTargetCompatibility.EXACT;
            }
        }

        if (descriptorColorCount == 0) {
            return RenderTargetCompatibility.DESCRIPTOR_ATTACHMENTLESS;
        }

        int suffixStart = framebufferColorCount - descriptorColorCount;
        for (int descriptorIndex = 0; descriptorIndex < descriptorColorCount; descriptorIndex++) {
            if (!descriptorTargets.colorAttachmentMatches(framebufferTargets, descriptorIndex, suffixStart + descriptorIndex)) {
                return RenderTargetCompatibility.MISMATCH;
            }
        }
        return RenderTargetCompatibility.DESCRIPTOR_SUFFIX;
    }

    private static void logRenderTargetParity(
        boolean equivalent,
        int framebuffer,
        String label,
        String framebufferSignature,
        String descriptorSignature,
        @Nullable RuntimeException exception
    ) {
        int logIndex = RENDER_TARGET_PARITY_LOG_COUNT.incrementAndGet();
        if (logIndex > MAX_RENDER_TARGET_PARITY_LOGS) {
            return;
        }

        if (equivalent) {
            LOGGER.info(
                "Vulkan render-target parity#{} label={} framebuffer={} equivalent=true signature={}",
                logIndex,
                label,
                framebuffer,
                framebufferSignature
            );
            return;
        }

        if (exception != null) {
            LOGGER.warn(
                "Vulkan render-target parity#{} label={} framebuffer={} equivalent=false reason={} framebufferSignature={} descriptorSignature={}",
                logIndex,
                label,
                framebuffer,
                exception.getClass().getSimpleName() + ": " + exception.getMessage(),
                framebufferSignature,
                descriptorSignature
            );
            return;
        }

        LOGGER.warn(
            "Vulkan render-target parity#{} label={} framebuffer={} equivalent=false framebufferSignature={} descriptorSignature={}",
            logIndex,
            label,
            framebuffer,
            framebufferSignature,
            descriptorSignature
        );
    }

    private static void logRenderTargetCompatibility(
        boolean compatible,
        RenderTargetCompatibility compatibility,
        int framebuffer,
        String label,
        String framebufferSignature,
        String descriptorSignature,
        @Nullable RuntimeException exception
    ) {
        int logIndex = RENDER_TARGET_PARITY_LOG_COUNT.incrementAndGet();
        if (logIndex > MAX_RENDER_TARGET_PARITY_LOGS) {
            return;
        }

        if (exception != null) {
            LOGGER.warn(
                "Vulkan render-target compatibility#{} label={} framebuffer={} compatible=false relation={} reason={} framebufferSignature={} descriptorSignature={}",
                logIndex,
                label,
                framebuffer,
                compatibility,
                exception.getClass().getSimpleName() + ": " + exception.getMessage(),
                framebufferSignature,
                descriptorSignature
            );
            return;
        }

        LOGGER.info(
            "Vulkan render-target compatibility#{} label={} framebuffer={} compatible={} relation={} framebufferSignature={} descriptorSignature={}",
            logIndex,
            label,
            framebuffer,
            compatible,
            compatibility,
            framebufferSignature,
            descriptorSignature
        );
    }

    private static VulkanTextureView requireVulkanTextureView(VulkanicTextureView view, String fieldName) {
        if (!(view instanceof VulkanTextureView vulkanView)) {
            throw new IllegalArgumentException("Vulkan render pass requires VulkanTextureView for " + fieldName
                + ", got: " + (view == null ? "null" : view.getClass().getName()));
        }
        if (vulkanView.isClosed()) {
            throw new IllegalStateException("Cannot use closed VulkanTextureView for " + fieldName);
        }
        return vulkanView;
    }

    private static VulkanicTexture requireRenderPassTexture(VulkanicTexture texture, String fieldName) {
        if (texture == null) {
            throw new IllegalArgumentException("Vulkan render pass requires non-null texture for " + fieldName);
        }
        if (texture.isClosed()) {
            throw new IllegalStateException("Cannot use closed texture for " + fieldName);
        }
        return texture;
    }

    private static final class VulkanBackedRenderPass implements VulkanicRenderPass {
        private final NativeSpine spine;
        private final long commandBufferHandle;
        private final VulkanRenderPassCompatibilityKey compatibilityKey;
        private volatile boolean closed;

        private VulkanBackedRenderPass(
            NativeSpine spine,
            long commandBufferHandle,
            VulkanRenderPassCompatibilityKey compatibilityKey
        ) {
            this.spine = Objects.requireNonNull(spine, "spine must not be null");
            this.commandBufferHandle = commandBufferHandle;
            this.compatibilityKey = Objects.requireNonNull(compatibilityKey, "compatibilityKey must not be null");
        }

        private void ensureOpen(String operation) {
            if (closed) {
                throw new IllegalStateException(operation + " called after render pass was closed");
            }
        }

        @Override
        public void setPipeline(PipelineHandle pipeline) {
            ensureOpen("setPipeline");
            if (!(pipeline instanceof VulkanPipelineHandle vulkanPipeline)) {
                throw new IllegalArgumentException(
                    "Vulkan render pass requires a VulkanPipelineHandle, got: "
                        + (pipeline == null ? "null" : pipeline.getClass().getName()));
            }
            if (!vulkanPipeline.isValid()) {
                throw new IllegalStateException("Cannot bind a closed or invalid VulkanPipelineHandle");
            }
            if (!compatibilityKey.equals(vulkanPipeline.getRenderPassCompatibilityKey())) {
                throw new IllegalStateException(
                    "Cannot bind Vulkan pipeline compiled for render-pass compatibility "
                        + vulkanPipeline.getRenderPassCompatibilityKey()
                        + " inside active render pass "
                        + compatibilityKey
                );
            }
            spine.bindPipeline(commandBufferHandle, vulkanPipeline.getVkPipelineHandle());
        }

        @Override
        public void setVertexBuffer(int slot, VulkanicBuffer buffer) {
            ensureOpen("setVertexBuffer");
            if (!(buffer instanceof VulkanBuffer vulkanBuffer)) {
                throw new IllegalArgumentException(
                    "Vulkan render pass requires VulkanBuffer for vertex buffer, got: "
                        + (buffer == null ? "null" : buffer.getClass().getName()));
            }
            if (vulkanBuffer.isClosed()) {
                throw new IllegalStateException("Cannot bind closed VulkanBuffer as vertex buffer");
            }
            spine.bindVertexBuffer(commandBufferHandle, slot, vulkanBuffer.getVkBufferHandle());
        }

        @Override
        public void setIndexBuffer(VulkanicBuffer buffer, VulkanicIndexType indexType) {
            ensureOpen("setIndexBuffer");
            if (!(buffer instanceof VulkanBuffer vulkanBuffer)) {
                throw new IllegalArgumentException(
                    "Vulkan render pass requires VulkanBuffer for index buffer, got: "
                        + (buffer == null ? "null" : buffer.getClass().getName()));
            }
            if (vulkanBuffer.isClosed()) {
                throw new IllegalStateException("Cannot bind closed VulkanBuffer as index buffer");
            }
            spine.bindIndexBuffer(commandBufferHandle, vulkanBuffer.getVkBufferHandle(), indexType);
        }

        @Override
        public void drawIndexed(int firstIndex, int indexCount, int baseVertex, int instanceCount) {
            ensureOpen("drawIndexed");
            spine.drawIndexed(commandBufferHandle, firstIndex, indexCount, baseVertex, instanceCount);
        }

        @Override
        public void draw(int firstVertex, int vertexCount) {
            ensureOpen("draw");
            spine.draw(commandBufferHandle, firstVertex, vertexCount);
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            spine.endRenderPass(commandBufferHandle);
        }
    }

    public CommandContext beginCommandBuffer() {
        ensureNativeReady("beginCommandBuffer");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }

        spine.recreateSwapchainIfFramebufferSizeChanged();

        long commandBufferHandle;
        if (spine.isFrameInProgress()) {
            if (spine.isRenderPassRecording()) {
                throw new IllegalStateException("Cannot begin a new Vulkan command buffer while a render pass is active.");
            }
            commandBufferHandle = spine.currentFrameCommandBufferHandle();
        } else if (spine.isPrimaryCommandBufferRecording()) {
            if (spine.isRenderPassRecording()) {
                throw new IllegalStateException("Cannot begin a new Vulkan command buffer while a render pass is active.");
            }
            commandBufferHandle = spine.primaryCommandBufferHandle();
        } else {
            commandBufferHandle = spine.beginPrimaryCommandBuffer();
            boundPipelineResourcesByCommandBuffer.remove(commandBufferHandle);
        }

        CommandContext existingContext = currentCommandContext;
        if (existingContext != null && existingContext.getHandle() == commandBufferHandle) {
            return existingContext;
        }

        CommandContext context = new VulkanCommandContext(commandBufferHandle, "Vulkan-PrimaryCommandBuffer");
        currentCommandContext = context;
        return context;
    }

    public void submitCommandBuffer(CommandContext ctx) {
        long commandBufferHandle = requireVulkanCommandBufferHandle("submitCommandBuffer", ctx);

        ensureNativeReady("submitCommandBuffer");

        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }

        if (spine.isCurrentFrameCommandBufferHandle(commandBufferHandle)) {
            currentCommandContext = null;
            return;
        }

        try {
            spine.submitPrimaryCommandBuffer(commandBufferHandle);
        } catch (IllegalStateException exception) {
            String message = exception.getMessage();
            if (message != null
                && message.contains("vkWaitForFences(immediateSubmit) timed out while a prior immediate submit was still in flight")) {
                LOGGER.warn(
                    "Deferring primary command buffer submit until the prior immediate submit completes: {}",
                    message
                );
                return;
            }
            throw exception;
        }
        boundPipelineResourcesByCommandBuffer.remove(commandBufferHandle);
        currentCommandContext = null;
    }

    public int beginFrame() {
        ensureNativeReady("beginFrame");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        return spine.beginFrame();
    }

    public void endFrame() {
        ensureNativeReady("endFrame");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        spine.endFrame();
    }

    public void presentTextureToScreen(CommandContext ctx, GpuTextureView textureView) {
        requireVulkanCommandBufferHandle("presentTextureToScreen", ctx);
        if (textureView == null) {
            throw new IllegalArgumentException("textureView must not be null");
        }
        if (!textureView.texture().getFormat().hasColorAspect()) {
            throw new IllegalStateException("Cannot present a non-color texture");
        }
        if ((textureView.texture().usage() & 8) == 0) {
            throw new IllegalStateException("Presented texture must include USAGE_RENDER_ATTACHMENT");
        }
        if (textureView.texture().getDepthOrLayers() > 1) {
            throw new UnsupportedOperationException("Textures with multiple depths/layers are not supported for Vulkan presentation");
        }

        ensureNativeReady("presentTextureToScreen");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }

        int legacyTextureHandle = resolveTextureHandle(ctx, textureView.texture());
        if (legacyTextureHandle == 0) {
            throw new IllegalStateException("Unable to resolve backend texture handle for presentation target");
        }

        int queuedLogIndex = presentQueueLogCount.getAndIncrement();
        if (queuedLogIndex < 8) {
            LOGGER.info(
                "Queueing Vulkan present source '{}' (legacyHandle={}, mip={}, extent={}x{}, format={}, usage=0x{})",
                textureView.texture().getLabel(),
                legacyTextureHandle,
                textureView.baseMipLevel(),
                textureView.getWidth(0),
                textureView.getHeight(0),
                textureView.texture().getFormat(),
                textureView.texture().usage()
            );
        }

        spine.queuePresentTextureRequest(
            legacyTextureHandle,
            textureView.baseMipLevel(),
            textureView.getWidth(0),
            textureView.getHeight(0)
        );
    }

    public void drawArrays(CommandContext ctx, int mode, int first, int count) {
        long commandBufferHandle = requireVulkanCommandBufferHandle("drawArrays", ctx);
        if (first < 0 || count < 0) {
            throw new IllegalArgumentException("drawArrays requires first/count >= 0, got first=" + first + ", count=" + count);
        }
        if (count == 0) {
            return;
        }

        ensureNativeReady("drawArrays");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }

        spine.drawLegacyArrays(commandBufferHandle, mode, first, count, 1);
    }

    public void drawElements(CommandContext ctx, int mode, int count, int type, long indices) {
        long commandBufferHandle = requireVulkanCommandBufferHandle("drawElements", ctx);
        VulkanicIndexType indexType = VulkanicIndexType.fromLegacyGlConstant(type)
            .orElseThrow(() -> new IllegalArgumentException("Unsupported drawElements index type constant: " + type));

        if (count < 0 || indices < 0L) {
            throw new IllegalArgumentException("drawElements requires count >= 0 and indices >= 0, got count="
                + count + ", indices=" + indices);
        }
        if ((indices % indexType.bytesPerIndex()) != 0L) {
            throw new IllegalArgumentException("Index offset must align to index type size. offset="
                + indices + ", bytesPerIndex=" + indexType.bytesPerIndex());
        }
        if (count == 0) {
            return;
        }

        ensureNativeReady("drawElements");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }

        spine.drawLegacyElements(commandBufferHandle, mode, count, indexType, indices, 1, 0);
    }

    public void drawIndexedInstancedBaseVertex(CommandContext ctx,
                                                int mode,
                                                int count,
                                                int type,
                                                long indices,
                                                int instanceCount,
                                                int baseVertex) {
        long commandBufferHandle = requireVulkanCommandBufferHandle("drawIndexedInstancedBaseVertex", ctx);
        VulkanicIndexType indexType = VulkanicIndexType.fromLegacyGlConstant(type)
            .orElseThrow(() -> new IllegalArgumentException(
                "Unsupported drawIndexedInstancedBaseVertex index type constant: " + type));

        if (count < 0 || indices < 0L || instanceCount < 1) {
            throw new IllegalArgumentException(
                "drawIndexedInstancedBaseVertex requires count >= 0, indices >= 0, instanceCount >= 1");
        }
        if ((indices % indexType.bytesPerIndex()) != 0L) {
            throw new IllegalArgumentException("Index offset must align to index type size. offset="
                + indices + ", bytesPerIndex=" + indexType.bytesPerIndex());
        }
        if (count == 0) {
            return;
        }

        ensureNativeReady("drawIndexedInstancedBaseVertex");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }

        spine.drawLegacyElements(commandBufferHandle, mode, count, indexType, indices, instanceCount, baseVertex);
    }

    public void drawIndexedBaseVertex(CommandContext ctx,
                                      int mode,
                                      int count,
                                      int type,
                                      long indices,
                                      int baseVertex) {
        drawIndexedInstancedBaseVertex(ctx, mode, count, type, indices, 1, baseVertex);
    }

    public void drawIndexedInstanced(CommandContext ctx,
                                     int mode,
                                     int count,
                                     int type,
                                     long indices,
                                     int instanceCount) {
        drawIndexedInstancedBaseVertex(ctx, mode, count, type, indices, instanceCount, 0);
    }

    public void drawArraysInstanced(CommandContext ctx, int mode, int first, int count, int instanceCount) {
        long commandBufferHandle = requireVulkanCommandBufferHandle("drawArraysInstanced", ctx);
        if (first < 0 || count < 0 || instanceCount < 1) {
            throw new IllegalArgumentException(
                "drawArraysInstanced requires first/count >= 0 and instanceCount >= 1");
        }
        if (count == 0) {
            return;
        }

        ensureNativeReady("drawArraysInstanced");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }

        spine.drawLegacyArrays(commandBufferHandle, mode, first, count, instanceCount);
    }

    public net.vulkanic.VulkanicRenderPass beginRenderPass(CommandContext ctx,
            java.util.function.Supplier<String> label,
            net.vulkanic.VulkanicTextureView colorTarget, java.util.OptionalInt clearColor) {
        return beginRenderPass(ctx,
            VulkanicRenderPassDescriptor.color(label, colorTarget, clearColor));
    }

    public net.vulkanic.VulkanicRenderPass beginRenderPass(CommandContext ctx,
            java.util.function.Supplier<String> label,
            net.vulkanic.VulkanicTextureView colorTarget, java.util.OptionalInt clearColor,
            @org.jetbrains.annotations.Nullable net.vulkanic.VulkanicTextureView depthTarget,
            java.util.OptionalDouble clearDepth) {
        return beginRenderPass(ctx,
            VulkanicRenderPassDescriptor.colorAndDepth(
                label, colorTarget, clearColor, depthTarget, clearDepth));
    }

    public net.vulkanic.VulkanicRenderPass beginRenderPass(CommandContext ctx,
            net.vulkanic.VulkanicRenderPassDescriptor descriptor) {
        long commandBufferHandle = requireVulkanCommandBufferHandle("beginRenderPass", ctx);
        VulkanRenderTargetPlan plan = resolveRenderPassDescriptorPlan(descriptor);

        ensureNativeReady("beginRenderPass");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }

        VulkanRenderPassCompatibilityKey compatibilityKey = spine.beginRenderPass(commandBufferHandle, plan);
        return new VulkanBackedRenderPass(spine, commandBufferHandle, compatibilityKey);
    }

    public net.vulkanic.VulkanicRenderPass beginRenderPass(
        CommandContext ctx,
        java.util.function.Supplier<String> label,
        int framebuffer
    ) {
        return beginRenderPass(ctx, label, framebuffer, true);
    }

    public net.vulkanic.VulkanicRenderPass beginRenderPass(
        CommandContext ctx,
        java.util.function.Supplier<String> label,
        int framebuffer,
        boolean hasDepthTexture
    ) {
        long commandBufferHandle = requireVulkanCommandBufferHandle("beginRenderPass(framebuffer)", ctx);
        VulkanRenderTargetPlan plan = resolveFramebufferRenderTargetPlan(label, framebuffer, hasDepthTexture);

        ensureNativeReady("beginRenderPass(framebuffer)");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }

        VulkanRenderPassCompatibilityKey compatibilityKey = spine.beginFramebufferRenderPass(commandBufferHandle, plan);
        return new VulkanBackedRenderPass(spine, commandBufferHandle, compatibilityKey);
    }

    public net.vulkanic.VulkanicRenderPass beginRenderPass(
        CommandContext ctx,
        VulkanicRenderTargetDescriptor descriptor
    ) {
        long commandBufferHandle = requireVulkanCommandBufferHandle("beginRenderPass(renderTargetDescriptor)", ctx);
        VulkanRenderTargetPlan plan = resolveRenderTargetDescriptorPlan(descriptor);

        ensureNativeReady("beginRenderPass(renderTargetDescriptor)");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }

        VulkanRenderPassCompatibilityKey compatibilityKey = spine.beginFramebufferRenderPass(commandBufferHandle, plan);
        return new VulkanBackedRenderPass(spine, commandBufferHandle, compatibilityKey);
    }

    public boolean isFallbackMode() {
        return !isNativeVulkanReady();
    }

    // =====================================================================
    //  GraphicsBackend contract: dynamic-state operations
    // =====================================================================

    /**
     * Sets the viewport via {@code vkCmdSetViewport} when a command buffer is
     * actively recording; caches the values for the next recording window when
     * called outside a command buffer.  Equivalent to {@link #setViewport}.
     */
    public void setDynamicViewport(CommandContext ctx, int x, int y, int width, int height) {
        long commandBufferHandle = requireVulkanCommandBufferHandle("setDynamicViewport", ctx);
        ensureNativeReady("setDynamicViewport");
        NativeSpine spine = requireNativeSpineForCommandOp("setDynamicViewport");
        spine.cmdSetViewport(commandBufferHandle, x, y, width, height);
    }

    /** Sets the viewport — delegates to {@link #setDynamicViewport}. */
    public void setViewport(CommandContext ctx, int x, int y, int width, int height) {
        setDynamicViewport(ctx, x, y, width, height);
    }

    /**
     * Sets the scissor rectangle via {@code vkCmdSetScissor} when a command
     * buffer is actively recording.
     */
    public void setDynamicScissor(CommandContext ctx, int x, int y, int width, int height) {
        long commandBufferHandle = requireVulkanCommandBufferHandle("setDynamicScissor", ctx);
        ensureNativeReady("setDynamicScissor");
        NativeSpine spine = requireNativeSpineForCommandOp("setDynamicScissor");
        spine.cmdSetScissor(commandBufferHandle, x, y, width, height);
    }

    /**
     * Records a clear-attachments command for the buffers indicated by {@code mask}.
     * In Vulkan, clears must be issued inside an active render pass; if none is
     * active the call is silently deferred (the pending clear colour/depth values
     * set via {@link #setClearColor}/{@link #setClearDepth} are picked up at
     * render-pass begin via the load-op).
     */
    public void clearBuffers(CommandContext ctx, int mask) {
        long commandBufferHandle = requireVulkanCommandBufferHandle("clearBuffers", ctx);
        NativeSpine spine = nativeSpine;
        if (spine == null || !spine.isRenderPassActive()) {
            if ((mask & GL_COLOR_BUFFER_BIT) != 0) {
                for (int drawBuffer : framebufferDrawBuffers(boundDrawFbo)) {
                    clearFramebufferColorAttachment(ctx, boundDrawFbo, drawBuffer, new float[] {
                        pendingClearR, pendingClearG, pendingClearB, pendingClearA
                    });
                }
            }
            if ((mask & GL_DEPTH_BUFFER_BIT) != 0) {
                clearFramebufferDepthAttachment(ctx, boundDrawFbo, (float) pendingClearDepth);
            }
            return;
        }

        final int GL_COLOR_BUFFER_BIT = 0x00004000;
        final int GL_DEPTH_BUFFER_BIT = 0x00000100;
        boolean clearColor = (mask & GL_COLOR_BUFFER_BIT) != 0;
        boolean clearDepth = (mask & GL_DEPTH_BUFFER_BIT) != 0;

        spine.cmdClearAttachments(commandBufferHandle,
            clearColor, pendingClearR, pendingClearG, pendingClearB, pendingClearA,
            clearDepth, (float) pendingClearDepth);
    }

    // =====================================================================
    //  GraphicsBackend contract: blend state
    // =====================================================================

    /** Caches blend-enabled flag (applied at pipeline creation). */
    public void setBlendEnabled(CommandContext ctx, boolean enabled) {
        requireVulkanCommandBufferHandle("setBlendEnabled", ctx);
        this.pendingBlendEnabled = enabled;
        updateIndexedBlendEnabled(0, enabled);
    }

    /** Caches blend function (applied at pipeline creation). */
    public void setBlendFunction(CommandContext ctx, int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        requireVulkanCommandBufferHandle("setBlendFunction", ctx);
        this.pendingBlendSrcRgb   = srcRgb;
        this.pendingBlendDstRgb   = dstRgb;
        this.pendingBlendSrcAlpha = srcAlpha;
        this.pendingBlendDstAlpha = dstAlpha;
        indexedBlendStates.put(0, new IndexedBlendState(pendingBlendEnabled, srcRgb, dstRgb, srcAlpha, dstAlpha));
    }

    /** Caches blend function — typed overload (applied at pipeline creation). */
    public void setBlendFunction(CommandContext ctx,
                                 net.vulkanic.VulkanicBlendFactor srcRgb,
                                 net.vulkanic.VulkanicBlendFactor dstRgb,
                                 net.vulkanic.VulkanicBlendFactor srcAlpha,
                                 net.vulkanic.VulkanicBlendFactor dstAlpha) {
        setBlendFunction(
            ctx,
            toLegacyGlBlendFactor(srcRgb),
            toLegacyGlBlendFactor(dstRgb),
            toLegacyGlBlendFactor(srcAlpha),
            toLegacyGlBlendFactor(dstAlpha)
        );
    }

    /** Caches blend equation (applied at pipeline creation). */
    public void setBlendEquation(CommandContext ctx, int mode) {
        requireVulkanCommandBufferHandle("setBlendEquation", ctx);
        this.pendingBlendEquation = mode;
        this.pendingBlendEquationAlpha = mode;
    }

    /** Caches blend equation — typed overload. */
    public void setBlendEquation(CommandContext ctx, net.vulkanic.VulkanicBlendEquation mode) {
        requireVulkanCommandBufferHandle("setBlendEquation", ctx);
        this.pendingBlendEquation = mode.ordinal();
        this.pendingBlendEquationAlpha = mode.ordinal();
    }

    /** Caches separate blend equations (applied at pipeline creation). */
    public void setBlendEquationSeparate(CommandContext ctx, int modeRGB, int modeAlpha) {
        requireVulkanCommandBufferHandle("setBlendEquationSeparate", ctx);
        this.pendingBlendEquation      = modeRGB;
        this.pendingBlendEquationAlpha = modeAlpha;
    }

    /** Caches separate blend equations — typed overload. */
    public void setBlendEquationSeparate(CommandContext ctx,
                                         net.vulkanic.VulkanicBlendEquation modeRGB,
                                         net.vulkanic.VulkanicBlendEquation modeAlpha) {
        requireVulkanCommandBufferHandle("setBlendEquationSeparate", ctx);
        this.pendingBlendEquation      = modeRGB.ordinal();
        this.pendingBlendEquationAlpha = modeAlpha.ordinal();
    }

    // =====================================================================
    //  GraphicsBackend contract: depth state
    // =====================================================================

    /** Caches depth test function (applied at pipeline creation). */
    public void setDepthTest(CommandContext ctx, int func) {
        requireVulkanCommandBufferHandle("setDepthTest", ctx);
        this.pendingDepthTestEnabled = true;
        this.pendingDepthFunc = func;
    }

    /** Caches depth test function — typed overload. */
    public void setDepthTest(CommandContext ctx, net.vulkanic.VulkanicDepthCompareOp func) {
        requireVulkanCommandBufferHandle("setDepthTest", ctx);
        this.pendingDepthTestEnabled = true;
        this.pendingDepthFunc = switch (func) {
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

    /** Caches depth function (alternative entry point identical to {@link #setDepthTest(CommandContext, int)}). */
    public void setDepthFunc(CommandContext ctx, int func) {
        setDepthTest(ctx, func);
    }

    /** Caches depth function — typed overload. */
    public void setDepthFunc(CommandContext ctx, net.vulkanic.VulkanicDepthCompareOp func) {
        setDepthTest(ctx, func);
    }

    /** Caches depth write mask (applied at pipeline creation). */
    public void setDepthWriteMask(CommandContext ctx, boolean enabled) {
        requireVulkanCommandBufferHandle("setDepthWriteMask", ctx);
        this.pendingDepthWriteMask = enabled;
    }

    // =====================================================================
    //  GraphicsBackend contract: color mask
    // =====================================================================

    /** Caches color write mask (applied at pipeline creation). */
    public void setColorMask(CommandContext ctx, boolean r, boolean g, boolean b, boolean a) {
        requireVulkanCommandBufferHandle("setColorMask", ctx);
        this.pendingColorMaskR = r;
        this.pendingColorMaskG = g;
        this.pendingColorMaskB = b;
        this.pendingColorMaskA = a;
    }

    // =====================================================================
    //  GraphicsBackend contract: rasterization state
    // =====================================================================

    /** Caches cull face mode — raw GL constant (applied at pipeline creation). */
    public void setCullFaceMode(CommandContext ctx, int mode) {
        requireVulkanCommandBufferHandle("setCullFaceMode", ctx);
        this.pendingCullFaceMode = mode;
    }

    /** Caches cull face mode — typed overload. */
    public void setCullFaceMode(CommandContext ctx, net.vulkanic.VulkanicCullFaceMode mode) {
        requireVulkanCommandBufferHandle("setCullFaceMode", ctx);
        this.pendingCullFaceMode = switch (mode) {
            case FRONT -> VulkanicAPI.GL_FRONT;
            case BACK -> VulkanicAPI.GL_BACK;
            case FRONT_AND_BACK -> VulkanicAPI.GL_FRONT_AND_BACK;
        };
    }

    /** Caches polygon rasterization mode (applied at pipeline creation). */
    public void setPolygonMode(CommandContext ctx, int face, int mode) {
        requireVulkanCommandBufferHandle("setPolygonMode", ctx);
        this.pendingPolygonFace = face;
        this.pendingPolygonMode = mode;
    }

    /** Caches polygon offset parameters (applied at pipeline creation). */
    public void setPolygonOffset(CommandContext ctx, float factor, float units) {
        requireVulkanCommandBufferHandle("setPolygonOffset", ctx);
        this.pendingPolygonOffsetFactor = factor;
        this.pendingPolygonOffsetUnits  = units;
    }

    // =====================================================================
    //  GraphicsBackend contract: shader binding (virtual no-op in Vulkan mode)
    // =====================================================================

    /**
     * No-op in Vulkan — pipeline binding replaces shader program binding.
     * The virtual shader graph is still consulted at pipeline creation time, not here.
     */
    public void bindShaderProgram(CommandContext ctx, int programId) {
        requireVulkanCommandBufferHandle("bindShaderProgram", ctx);
        this.boundVirtualProgram = programId;
    }

    // =====================================================================
    //  GraphicsBackend contract: capability enable/disable
    // =====================================================================

    /**
     * Caches capability state; only well-known GPU capabilities (BLEND, DEPTH_TEST,
     * CULL_FACE, POLYGON_OFFSET_FILL) are mapped to Vulkan pipeline state.
     */
    public void setCapabilityEnabled(CommandContext ctx, int cap, boolean enabled) {
        long commandBufferHandle = requireVulkanCommandBufferHandle("setCapabilityEnabled", ctx);
        final int GL_BLEND        = 0x0BE2;
        final int GL_DEPTH_TEST   = 0x0B71;
        final int GL_SCISSOR_TEST = 0x0C11;
        final int GL_STENCIL_TEST = 0x0B90;
        if (cap == GL_BLEND) {
            this.pendingBlendEnabled = enabled;
            updateIndexedBlendEnabled(0, enabled);
        } else if (cap == GL_DEPTH_TEST) {
            this.pendingDepthTestEnabled = enabled;
        } else if (cap == VulkanicAPI.GL_CULL_FACE) {
            this.pendingCullFaceEnabled = enabled;
        } else if (cap == GL_STENCIL_TEST) {
            this.pendingStencilTestEnabled = enabled;
        } else if (cap == GL_SCISSOR_TEST) {
            ensureNativeReady("setCapabilityEnabled(scissor)");
            NativeSpine spine = requireNativeSpineForCommandOp("setCapabilityEnabled(scissor)");
            spine.setScissorTestEnabled(commandBufferHandle, enabled);
        }
        // Other capabilities (GL_POLYGON_OFFSET_FILL, etc.) are stored
        // implicitly via their own dedicated setXxx methods or are no-ops in Vulkan.
    }

    /** Typed overload — delegates to raw-constant version via {@code toLegacyGlConstant()}. */
    public void setCapabilityEnabled(CommandContext ctx, net.vulkanic.VulkanicCapability capability, boolean enabled) {
        setCapabilityEnabled(ctx, capability.toLegacyGlConstant(), enabled);
    }

    /** Indexed capability enable/disable — caches per-buffer blend enable state. */
    public void setIndexedEnabled(CommandContext ctx, int capability, int index, boolean enabled) {
        requireVulkanCommandBufferHandle("setIndexedEnabled", ctx);
        final int GL_BLEND = 0x0BE2;
        if (capability == GL_BLEND) {
            if (index == 0) {
                this.pendingBlendEnabled = enabled;
            }
            updateIndexedBlendEnabled(index, enabled);
        }
    }

    private void updateIndexedBlendEnabled(int index, boolean enabled) {
        IndexedBlendState previous = indexedBlendStates.get(index);
        indexedBlendStates.put(index, new IndexedBlendState(
            enabled,
            index == 0 ? pendingBlendSrcRgb : (previous != null ? previous.srcRgb() : VulkanicAPI.GL_ONE),
            index == 0 ? pendingBlendDstRgb : (previous != null ? previous.dstRgb() : VulkanicAPI.GL_ZERO),
            index == 0 ? pendingBlendSrcAlpha : (previous != null ? previous.srcAlpha() : VulkanicAPI.GL_ONE),
            index == 0 ? pendingBlendDstAlpha : (previous != null ? previous.dstAlpha() : VulkanicAPI.GL_ZERO)
        ));
    }

    // =====================================================================
    //  GraphicsBackend contract: clear state
    // =====================================================================

    /** Caches clear colour (used at render-pass begin / vkCmdClearAttachments). */
    public void setClearColor(CommandContext ctx, float r, float g, float b, float a) {
        requireVulkanCommandBufferHandle("setClearColor", ctx);
        this.pendingClearR = r;
        this.pendingClearG = g;
        this.pendingClearB = b;
        this.pendingClearA = a;
    }

    /** Caches clear depth value (used at render-pass begin). */
    public void setClearDepth(CommandContext ctx, double depth) {
        requireVulkanCommandBufferHandle("setClearDepth", ctx);
        this.pendingClearDepth = depth;
    }

    // =====================================================================
    //  GraphicsBackend contract: logic op
    // =====================================================================

    /** Caches logic op (applied at pipeline creation; no direct Vulkan dynamic equivalent). */
    public void setLogicOp(CommandContext ctx, int opcode) {
        requireVulkanCommandBufferHandle("setLogicOp", ctx);
        this.pendingLogicOp = opcode;
    }

    // =====================================================================
    //  GraphicsBackend contract: read/draw buffer routing
    // =====================================================================

    /**
     * Caches read-buffer selection.  In Vulkan there is no glReadBuffer equivalent —
     * buffer routing is handled at render-pass / blit level.
     */
    public void setReadBuffer(CommandContext ctx, int buffer) {
        requireVulkanCommandBufferHandle("setReadBuffer", ctx);
        this.pendingReadBuffer = buffer;
    }

    /**
     * Caches draw-buffer selection.  In Vulkan attachment routing is specified
     * at render-pass / pipeline creation time; this cache is consulted there.
     */
    public void setDrawBuffer(CommandContext ctx, int mode) {
        requireVulkanCommandBufferHandle("setDrawBuffer", ctx);
        this.pendingDrawBuffer = mode;
    }

    // =====================================================================
    //  GraphicsBackend contract: VAO binding (no-op in Vulkan)
    // =====================================================================

    /**
     * No-op in Vulkan — vertex attribute state is encoded into the pipeline
     * descriptor and there is no equivalent to a VAO object.
     */
    public void bindVertexArray(CommandContext ctx, int vao) {
        requireVulkanCommandBufferHandle("bindVertexArray", ctx);
        this.boundVirtualVao = vao;
    }

    // =====================================================================
    //  GraphicsBackend contract: error query
    // =====================================================================

    /**
     * Returns 0 (GL_NO_ERROR equivalent) — Vulkan uses result codes per-call,
     * not a global error state like glGetError.
     */
    public int getError(CommandContext ctx) {
        requireVulkanCommandBufferHandle("getError", ctx);
        return 0; // VK_SUCCESS analogue: no deferred error queue in Vulkan
    }

    // =====================================================================
    //  GraphicsBackend contract: virtual framebuffer objects
    // =====================================================================

    /**
     * Allocates a virtual FBO handle.  In Vulkan, real framebuffer objects are
     * created transiently inside render passes and are not user-managed the
     * way they are in OpenGL.  Virtual handles are tracked to satisfy the
     * GL-style allocate/bind/delete API contract without actually creating
     * Vulkan framebuffer resources at this point.
     */
    public int createFramebuffer(CommandContext ctx) {
        requireVulkanCommandBufferHandle("createFramebuffer", ctx);
        int id = nextVirtualFboId.getAndIncrement();
        virtualFbos.add(id);
        virtualFramebufferStates.put(id, new VirtualFramebufferState());
        return id;
    }

    /** Allocates multiple virtual FBO handles. */
    public int createFramebuffers(CommandContext ctx) {
        return createFramebuffer(ctx);
    }

    /**
     * Binds a virtual FBO for subsequent operations.  In Vulkan, the actual
     * render target is specified declaratively in the render-pass descriptor —
     * this call only updates the cached binding for diagnostic / compatibility use.
     */
    public void bindFramebuffer(CommandContext ctx, int target, int fbo) {
        requireVulkanCommandBufferHandle("bindFramebuffer", ctx);
        if (target == GL_READ_FRAMEBUFFER) {
            this.boundReadFbo = fbo;
            applyFramebufferReadState(fbo);
        } else if (target == GL_DRAW_FRAMEBUFFER) {
            this.boundDrawFbo = fbo;
            applyFramebufferDrawState(fbo);
        } else if (target == GL_FRAMEBUFFER) {
            this.boundReadFbo = fbo;
            this.boundDrawFbo = fbo;
            applyFramebufferReadState(fbo);
            applyFramebufferDrawState(fbo);
        }
    }

    public void bindRenderTarget(CommandContext ctx, net.vulkanic.VulkanicTexture colorTexture, net.vulkanic.VulkanicTexture depthTexture) {
        requireVulkanCommandBufferHandle("bindRenderTarget", ctx);
        int framebuffer = resolveFramebufferForTextures(ctx, colorTexture, depthTexture);
        bindFramebuffer(ctx, VulkanicAPI.GL_FRAMEBUFFER, framebuffer);
    }

    /**
     * Releases the virtual FBO handle.  Any associated Vulkan resources
     * are already tracked transiently within render passes and cleaned up there.
     */
    public void deleteFramebuffer(CommandContext ctx, int fbo) {
        requireVulkanCommandBufferHandle("deleteFramebuffer", ctx);
        virtualFbos.remove(fbo);
        virtualFramebufferStates.remove(fbo);
        releaseImplicitFramebufferBinding(fbo);
        if (boundReadFbo == fbo) boundReadFbo = 0;
        if (boundDrawFbo == fbo) boundDrawFbo = 0;
    }

    /**
     * Returns {@code GL_FRAMEBUFFER_COMPLETE} (0x8CD5) for virtual FBO 0
     * (the default framebuffer); returns {@code GL_FRAMEBUFFER_COMPLETE} for
     * any known virtual FBO.  Returns {@code GL_FRAMEBUFFER_UNDEFINED} (0x8219)
     * for unknown/unregistered handles.
     */
    public int checkFramebufferStatus(CommandContext ctx, int target) {
        requireVulkanCommandBufferHandle("checkFramebufferStatus", ctx);
        final int GL_FRAMEBUFFER_COMPLETE   = 0x8CD5;
        final int GL_FRAMEBUFFER_UNDEFINED  = 0x8219;
        int fbo = (target == 0x8CA8 /* GL_READ_FRAMEBUFFER */) ? boundReadFbo : boundDrawFbo;
        if (fbo == 0 || virtualFbos.contains(fbo)) {
            return GL_FRAMEBUFFER_COMPLETE;
        }
        return GL_FRAMEBUFFER_UNDEFINED;
    }

    // =====================================================================
    //  Shader uniform setters
    // =====================================================================

    private void captureStandaloneUniformInts(int location, int[] values) {
        if (values == null || values.length == 0) {
            return;
        }
        STANDALONE_UNIFORM_CALL_COUNT.incrementAndGet();

        UniformLocationRef locationRef = resolveUniformLocationRef(location);
        if (locationRef != null) {
            STANDALONE_UNIFORM_TOKEN_HIT_COUNT.incrementAndGet();
            VirtualProgram program = virtualPrograms.get(locationRef.programId());
            if (program != null) {
                captureSamplerUniformInt(program, locationRef.uniformIndex(), values[0]);
                writeStandaloneUniformInts(program, locationRef.uniformIndex(), values);
            }
            maybeLogStandaloneUniformStats();
            return;
        }

        VirtualProgram boundProgram = virtualPrograms.get(boundVirtualProgram);
        if (boundProgram != null) {
            STANDALONE_UNIFORM_FALLBACK_COUNT.incrementAndGet();
            captureSamplerUniformInt(boundProgram, location, values[0]);
            writeStandaloneUniformInts(boundProgram, location, values);
        }
        maybeLogStandaloneUniformStats();
    }

    private void captureSamplerUniformInt(VirtualProgram program, int uniformIndex, int value) {
        if (uniformIndex < 0 || uniformIndex >= program.activeUniforms.size()) {
            return;
        }
        ReflectedUniform uniform = program.activeUniforms.get(uniformIndex);
        Optional<net.vulkanic.VulkanicUniformReflectionType> reflectionType =
            net.vulkanic.VulkanicUniformReflectionType.fromLegacyGlConstant(uniform.legacyType());
        if (reflectionType.isPresent() && reflectionType.get().isSampler()) {
            program.samplerUniformValuesByIndex.put(uniformIndex, value);
        }
    }

    private void captureStandaloneUniformFloats(int location, float[] values) {
        if (values == null || values.length == 0) {
            return;
        }
        STANDALONE_UNIFORM_CALL_COUNT.incrementAndGet();

        UniformLocationRef locationRef = resolveUniformLocationRef(location);
        if (locationRef != null) {
            STANDALONE_UNIFORM_TOKEN_HIT_COUNT.incrementAndGet();
            VirtualProgram program = virtualPrograms.get(locationRef.programId());
            if (program != null) {
                writeStandaloneUniformFloats(program, locationRef.uniformIndex(), values);
            }
            maybeLogStandaloneUniformStats();
            return;
        }

        VirtualProgram boundProgram = virtualPrograms.get(boundVirtualProgram);
        if (boundProgram != null) {
            STANDALONE_UNIFORM_FALLBACK_COUNT.incrementAndGet();
            writeStandaloneUniformFloats(boundProgram, location, values);
        }
        maybeLogStandaloneUniformStats();
    }

    private void maybeLogStandaloneUniformStats() {
        if (STANDALONE_UNIFORM_STATS_LOG_COUNT.get() >= 16) {
            return;
        }
        long callCount = STANDALONE_UNIFORM_CALL_COUNT.get();
        if (callCount % 200 != 0) {
            return;
        }
        if (STANDALONE_UNIFORM_STATS_LOG_COUNT.incrementAndGet() <= 16) {
            LOGGER.info(
                "Standalone uniform stats calls={} tokenHits={} fallbacks={} writes={}",
                callCount,
                STANDALONE_UNIFORM_TOKEN_HIT_COUNT.get(),
                STANDALONE_UNIFORM_FALLBACK_COUNT.get(),
                STANDALONE_UNIFORM_WRITE_COUNT.get()
            );
        }
    }

    private boolean writeStandaloneUniformInts(VirtualProgram virtualProgram, int location, int[] values) {
        StandaloneUniformField field = virtualProgram.standaloneFieldsByLocation.get(location);
        if (field == null) {
            return false;
        }

        synchronized (virtualProgram) {
            java.nio.ByteBuffer backingData = virtualProgram.standaloneBackingData;
            if (backingData == null) {
                return false;
            }

            writeIntUniform(field, backingData, values);
            virtualProgram.standaloneDirty = true;
            STANDALONE_UNIFORM_WRITE_COUNT.incrementAndGet();
            return true;
        }
    }

    private boolean writeStandaloneUniformFloats(VirtualProgram virtualProgram, int location, float[] values) {
        StandaloneUniformField field = virtualProgram.standaloneFieldsByLocation.get(location);
        if (field == null) {
            return false;
        }

        synchronized (virtualProgram) {
            java.nio.ByteBuffer backingData = virtualProgram.standaloneBackingData;
            if (backingData == null) {
                return false;
            }

            writeFloatUniform(field, backingData, values);
            virtualProgram.standaloneDirty = true;
            STANDALONE_UNIFORM_WRITE_COUNT.incrementAndGet();
            return true;
        }
    }

    private static void writeIntUniform(StandaloneUniformField field, java.nio.ByteBuffer backingData, int[] values) {
        int[] padded = new int[4];
        int copyLength = Math.min(values.length, 4);
        System.arraycopy(values, 0, padded, 0, copyLength);

        for (int offset : field.offsets()) {
            writeIntUniformAtOffset(field, backingData, offset, padded);
        }
    }

    private static void writeIntUniformAtOffset(
        StandaloneUniformField field,
        java.nio.ByteBuffer backingData,
        int offset,
        int[] padded
    ) {
        switch (field.type()) {
            case INT, UINT, BOOL -> backingData.putInt(offset, padded[0]);
            case INT_VEC2, UINT_VEC2, BOOL_VEC2 -> {
                backingData.putInt(offset, padded[0]);
                backingData.putInt(offset + 4, padded[1]);
            }
            case INT_VEC3, UINT_VEC3, BOOL_VEC3 -> {
                backingData.putInt(offset, padded[0]);
                backingData.putInt(offset + 4, padded[1]);
                backingData.putInt(offset + 8, padded[2]);
            }
            case INT_VEC4, UINT_VEC4, BOOL_VEC4 -> {
                backingData.putInt(offset, padded[0]);
                backingData.putInt(offset + 4, padded[1]);
                backingData.putInt(offset + 8, padded[2]);
                backingData.putInt(offset + 12, padded[3]);
            }
            default -> {
                // Ignore mismatched updates for non-int field types.
            }
        }
    }

    private static void writeFloatUniform(StandaloneUniformField field, java.nio.ByteBuffer backingData, float[] values) {
        for (int offset : field.offsets()) {
            writeFloatUniformAtOffset(field, backingData, offset, values);
        }
    }

    private static void writeFloatUniformAtOffset(
        StandaloneUniformField field,
        java.nio.ByteBuffer backingData,
        int offset,
        float[] values
    ) {
        switch (field.type()) {
            case FLOAT -> backingData.putFloat(offset, values[0]);
            case FLOAT_VEC2 -> {
                backingData.putFloat(offset, values[0]);
                backingData.putFloat(offset + 4, values.length > 1 ? values[1] : 0.0F);
            }
            case FLOAT_VEC3 -> {
                backingData.putFloat(offset, values[0]);
                backingData.putFloat(offset + 4, values.length > 1 ? values[1] : 0.0F);
                backingData.putFloat(offset + 8, values.length > 2 ? values[2] : 0.0F);
            }
            case FLOAT_VEC4 -> {
                backingData.putFloat(offset, values[0]);
                backingData.putFloat(offset + 4, values.length > 1 ? values[1] : 0.0F);
                backingData.putFloat(offset + 8, values.length > 2 ? values[2] : 0.0F);
                backingData.putFloat(offset + 12, values.length > 3 ? values[3] : 0.0F);
            }
            case FLOAT_MAT2 -> writeStd140MatrixColumns(backingData, offset, values, 2, 2);
            case FLOAT_MAT3 -> writeStd140MatrixColumns(backingData, offset, values, 3, 3);
            case FLOAT_MAT4 -> writeStd140MatrixColumns(backingData, offset, values, 4, 4);
            default -> {
                // Ignore mismatched updates for non-float field types.
            }
        }
    }

    private static void writeStd140MatrixColumns(
        java.nio.ByteBuffer backingData,
        int baseOffset,
        float[] values,
        int columns,
        int rows
    ) {
        for (int col = 0; col < columns; col++) {
            int columnOffset = baseOffset + (col * 16);
            for (int row = 0; row < rows; row++) {
                int sourceIndex = (col * rows) + row;
                float component = sourceIndex < values.length ? values[sourceIndex] : 0.0F;
                backingData.putFloat(columnOffset + (row * 4), component);
            }
        }
    }

    private static float[] transpose3(float[] values) {
        return new float[] {
            values[0], values[3], values[6],
            values[1], values[4], values[7],
            values[2], values[5], values[8]
        };
    }

    private static float[] transpose4(float[] values) {
        return new float[] {
            values[0], values[4], values[8], values[12],
            values[1], values[5], values[9], values[13],
            values[2], values[6], values[10], values[14],
            values[3], values[7], values[11], values[15]
        };
    }

    @Nullable
    public VulkanicBufferSlice getStandaloneUniformBufferSlice(CommandContext ctx, int program) {
        int sampledProgram = STANDALONE_LOOKUP_SAMPLE_PROGRAM.get();
        boolean traceLookup = VulkanicAPI.isStandaloneUniformTracingEnabled()
            && (program == sampledProgram || (sampledProgram < 0 && STANDALONE_LOOKUP_SAMPLE_PROGRAM.compareAndSet(-1, program)));
        if (traceLookup) {
            boolean ctxIsVulkan = ctx instanceof VulkanCommandContext;
            long ctxHandle = ctx != null ? ctx.getHandle() : 0L;
            LOGGER.info(
                "StandaloneLookupDecisionTrace stage=enter programId={} sampledProgram={} commandBufferContextIsVulkan={} commandBufferHandle={} commandBufferExists={}",
                program,
                STANDALONE_LOOKUP_SAMPLE_PROGRAM.get(),
                yesNo(ctxIsVulkan),
                ctxHandle,
                yesNo(ctxIsVulkan && ctxHandle != 0L)
            );
        }

        requireVulkanCommandBufferHandle("getStandaloneUniformBufferSlice", ctx);

        VirtualProgram virtualProgram = virtualPrograms.get(program);
        if (traceLookup) {
            logStandaloneProgramKeyTrace("lookup-request", program, virtualProgram, "getStandaloneUniformBufferSlice-enter");
            java.util.ArrayList<Integer> keys = new java.util.ArrayList<>(virtualPrograms.keySet());
            java.util.Collections.sort(keys);
            LOGGER.info(
                "StandaloneLookupDecisionTrace stage=virtual-program programId={} containsExactKey={} mapSize={} nearestEntries={} virtualProgramPresent={} reflectedStandaloneBlockPresent={} backingSize={} backingDataPresent={} backingBufferPresent={}",
                program,
                yesNo(virtualPrograms.containsKey(program)),
                virtualPrograms.size(),
                describeNearestProgramKeys(keys, program),
                yesNo(virtualProgram != null),
                yesNo(virtualProgram != null
                    && virtualProgram.activeUniformBlocks.contains(VulkanicAPI.generatedStandaloneUniformBlockName())
                    && !virtualProgram.standaloneFieldsByLocation.isEmpty()),
                virtualProgram != null ? virtualProgram.standaloneBackingSize : 0,
                yesNo(virtualProgram != null && virtualProgram.standaloneBackingData != null),
                yesNo(virtualProgram != null
                    && virtualProgram.standaloneGpuBuffer != null
                    && !virtualProgram.standaloneGpuBuffer.isClosed())
            );
        }

        if (virtualProgram == null || virtualProgram.standaloneBackingSize <= 0) {
            if (traceLookup) {
                String nullReason = virtualProgram == null
                    ? "virtual-program-missing"
                    : "standalone-backing-size-zero-or-negative";
                logStandaloneProgramKeyTrace("lookup-miss", program, virtualProgram, nullReason);
                LOGGER.info(
                    "StandaloneLookupDecisionTrace stage=return-null programId={} reason={} sliceCreationAttempted=no",
                    program,
                    nullReason
                );
            }
            logStandaloneSliceTraceInternal(program, "slice-lookup", null, virtualProgram, false, true, null);
            return null;
        }

        synchronized (virtualProgram) {
            java.nio.ByteBuffer backingData = virtualProgram.standaloneBackingData;
            if (backingData == null || virtualProgram.standaloneBackingSize <= 0) {
                if (traceLookup) {
                    String nullReason = backingData == null
                        ? "standalone-backing-data-null"
                        : "standalone-backing-size-zero-or-negative";
                    logStandaloneProgramKeyTrace("lookup-miss", program, virtualProgram, nullReason);
                    LOGGER.info(
                        "StandaloneLookupDecisionTrace stage=return-null programId={} reason={} sliceCreationAttempted=no backingBufferPresent={} dirty={}",
                        program,
                        nullReason,
                        yesNo(virtualProgram.standaloneGpuBuffer != null && !virtualProgram.standaloneGpuBuffer.isClosed()),
                        yesNo(virtualProgram.standaloneDirty)
                    );
                }
                logStandaloneSliceTraceInternal(program, "slice-lookup", null, virtualProgram, false, true, null);
                return null;
            }

            if (traceLookup) {
                LOGGER.info(
                    "StandaloneLookupDecisionTrace stage=slice-creation-check programId={} sliceCreationAttempted=yes allocationMode=dynamic-ring dirty={} backingSize={} backingDataPresent=yes",
                    program,
                    yesNo(virtualProgram.standaloneDirty),
                    virtualProgram.standaloneBackingSize
                );
            }

            NativeSpine spine = nativeSpine;
            if (spine == null) {
                throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
            }

            VulkanicBufferSlice slice = spine.materializeStandaloneUniformBuffer(
                program,
                backingData,
                virtualProgram.standaloneBackingSize
            );
            virtualProgram.standaloneDirty = false;
            int sampledAfterCreate = STANDALONE_LOOKUP_SAMPLE_PROGRAM.get();
            if (traceLookup && program == sampledAfterCreate) {
                logStandaloneProgramKeyTrace("slice-created", program, virtualProgram, "new VulkanicBufferSlice from virtualPrograms map entry");
                LOGGER.info(
                    "StandaloneLookupDecisionTrace stage=return-slice programId={} sampledProgram={} sliceAvailable=yes sliceId={} sliceLength={} reason=success",
                    program,
                    sampledAfterCreate,
                    System.identityHashCode(slice),
                    virtualProgram.standaloneBackingSize
                );
            }
            logStandaloneSliceTraceInternal(program, "slice-lookup", null, virtualProgram, true, true, null);
            return slice;
        }
    }

    public void setUniform1i(CommandContext ctx, int location, int value) {
        requireVulkanCommandBufferHandle("setUniform1i", ctx);
        captureStandaloneUniformInts(location, new int[] {value});
    }

    public void setUniform1f(CommandContext ctx, int location, float value) {
        requireVulkanCommandBufferHandle("setUniform1f", ctx);
        captureStandaloneUniformFloats(location, new float[] {value});
    }

    public void setUniform2f(CommandContext ctx, int location, float v0, float v1) {
        requireVulkanCommandBufferHandle("setUniform2f", ctx);
        captureStandaloneUniformFloats(location, new float[] {v0, v1});
    }

    public void setUniform2i(CommandContext ctx, int location, int v0, int v1) {
        requireVulkanCommandBufferHandle("setUniform2i", ctx);
        captureStandaloneUniformInts(location, new int[] {v0, v1});
    }

    public void setUniform3f(CommandContext ctx, int location, float v0, float v1, float v2) {
        requireVulkanCommandBufferHandle("setUniform3f", ctx);
        captureStandaloneUniformFloats(location, new float[] {v0, v1, v2});
    }

    public void setUniform3i(CommandContext ctx, int location, int v0, int v1, int v2) {
        requireVulkanCommandBufferHandle("setUniform3i", ctx);
        captureStandaloneUniformInts(location, new int[] {v0, v1, v2});
    }

    public void setUniform4f(CommandContext ctx, int location, float v0, float v1, float v2, float v3) {
        requireVulkanCommandBufferHandle("setUniform4f", ctx);
        captureStandaloneUniformFloats(location, new float[] {v0, v1, v2, v3});
    }

    public void setUniform4i(CommandContext ctx, int location, int v0, int v1, int v2, int v3) {
        requireVulkanCommandBufferHandle("setUniform4i", ctx);
        captureStandaloneUniformInts(location, new int[] {v0, v1, v2, v3});
    }

    public void setUniformMatrix3fv(CommandContext ctx, int location, boolean transpose, java.nio.FloatBuffer matrix) {
        requireVulkanCommandBufferHandle("setUniformMatrix3fv", ctx);
        java.nio.FloatBuffer duplicate = matrix.duplicate();
        float[] values = new float[Math.min(duplicate.remaining(), 9)];
        duplicate.get(values);
        if (transpose && values.length >= 9) {
            values = transpose3(values);
        }
        if (values.length > 0) {
            captureStandaloneUniformFloats(location, values);
        }
    }

    public void setUniformMatrix3fv(CommandContext ctx, int location, boolean transpose, float[] matrix) {
        requireVulkanCommandBufferHandle("setUniformMatrix3fv", ctx);
        if (matrix == null || matrix.length == 0) {
            return;
        }
        float[] values = java.util.Arrays.copyOf(matrix, Math.min(matrix.length, 9));
        if (transpose && values.length >= 9) {
            values = transpose3(values);
        }
        captureStandaloneUniformFloats(location, values);
    }

    public void setUniformMatrix4fv(CommandContext ctx, int location, boolean transpose, java.nio.FloatBuffer matrix) {
        requireVulkanCommandBufferHandle("setUniformMatrix4fv", ctx);
        java.nio.FloatBuffer duplicate = matrix.duplicate();
        float[] values = new float[Math.min(duplicate.remaining(), 16)];
        duplicate.get(values);
        if (transpose && values.length >= 16) {
            values = transpose4(values);
        }
        if (values.length > 0) {
            captureStandaloneUniformFloats(location, values);
        }
    }

    public void setUniformMatrix4fv(CommandContext ctx, int location, boolean transpose, float[] matrix) {
        requireVulkanCommandBufferHandle("setUniformMatrix4fv", ctx);
        if (matrix == null || matrix.length == 0) {
            return;
        }
        float[] values = java.util.Arrays.copyOf(matrix, Math.min(matrix.length, 16));
        if (transpose && values.length >= 16) {
            values = transpose4(values);
        }
        captureStandaloneUniformFloats(location, values);
    }

    public void setUniform2fv(CommandContext ctx, int location, float[] value) {
        requireVulkanCommandBufferHandle("setUniform2fv", ctx);
        if (value != null && value.length > 0) {
            captureStandaloneUniformFloats(location, value.length >= 2 ? new float[] {value[0], value[1]} : new float[] {value[0], 0.0F});
        }
    }

    public void setUniform3fv(CommandContext ctx, int location, float[] value) {
        requireVulkanCommandBufferHandle("setUniform3fv", ctx);
        if (value != null && value.length > 0) {
            captureStandaloneUniformFloats(
                location,
                new float[] {value[0], value.length > 1 ? value[1] : 0.0F, value.length > 2 ? value[2] : 0.0F}
            );
        }
    }

    public void setUniform4fv(CommandContext ctx, int location, float[] value) {
        requireVulkanCommandBufferHandle("setUniform4fv", ctx);
        if (value != null && value.length > 0) {
            captureStandaloneUniformFloats(
                location,
                new float[] {
                    value[0],
                    value.length > 1 ? value[1] : 0.0F,
                    value.length > 2 ? value[2] : 0.0F,
                    value.length > 3 ? value[3] : 0.0F
                }
            );
        }
    }

    // =====================================================================
    //  Uniform / attribute location resolution
    // =====================================================================

    /**
     * Returns a stable virtual index for linked GLSL uniforms so the legacy
     * compatibility layer can keep sampler and texel-buffer bindings alive on
     * the Vulkan path.
     */
    public int getUniformLocation(CommandContext ctx, int program, CharSequence name) {
        requireVulkanCommandBufferHandle("getUniformLocation", ctx);
        VirtualProgram virtualProgram = virtualPrograms.get(program);
        if (virtualProgram == null) {
            return -1;
        }

        String uniformName = name.toString();
        int uniformIndex = virtualProgram.activeUniformNames.indexOf(uniformName);
        if (uniformIndex < 0) {
            return -1;
        }

        return resolveUniformLocationToken(program, virtualProgram, uniformName, uniformIndex);
    }

    // =====================================================================
    //  Remaining GraphicsBackend contract coverage
    // =====================================================================

    public void blendFunc(CommandContext ctx, int sfactor, int dfactor) {
        requireVulkanCommandBufferHandle("blendFunc", ctx);
        setBlendFunction(ctx, sfactor, dfactor, sfactor, dfactor);
    }

    public void blendFunc(CommandContext ctx, net.vulkanic.VulkanicBlendFactor sfactor,
                          net.vulkanic.VulkanicBlendFactor dfactor) {
        requireVulkanCommandBufferHandle("blendFunc", ctx);
        blendFunc(ctx, toLegacyGlBlendFactor(sfactor), toLegacyGlBlendFactor(dfactor));
    }

    public void blendFuncSeparatei(CommandContext ctx, int buffer, int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        requireVulkanCommandBufferHandle("blendFuncSeparatei", ctx);
        if (buffer == 0) {
            setBlendFunction(ctx, srcRGB, dstRGB, srcAlpha, dstAlpha);
            return;
        }
        IndexedBlendState previous = indexedBlendStates.get(buffer);
        boolean enabled = previous == null || previous.enabled();
        indexedBlendStates.put(buffer, new IndexedBlendState(enabled, srcRGB, dstRGB, srcAlpha, dstAlpha));
    }

    public void blendFuncSeparatei(CommandContext ctx, int buffer,
                                   net.vulkanic.VulkanicBlendFactor srcRGB,
                                   net.vulkanic.VulkanicBlendFactor dstRGB,
                                   net.vulkanic.VulkanicBlendFactor srcAlpha,
                                   net.vulkanic.VulkanicBlendFactor dstAlpha) {
        requireVulkanCommandBufferHandle("blendFuncSeparatei", ctx);
        blendFuncSeparatei(
            ctx,
            buffer,
            toLegacyGlBlendFactor(srcRGB),
            toLegacyGlBlendFactor(dstRGB),
            toLegacyGlBlendFactor(srcAlpha),
            toLegacyGlBlendFactor(dstAlpha)
        );
    }

    public void blitFramebuffer(CommandContext ctx, int srcX0, int srcY0, int srcX1, int srcY1,
                                int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        long commandBufferHandle = requireVulkanCommandBufferHandle("blitFramebuffer", ctx);
        if (filter != GL_NEAREST) {
            return;
        }

        Integer sourceTexture = resolveFramebufferTextureForBlit(boundReadFbo, mask, pendingReadBuffer);
        Integer destTexture = resolveFramebufferTextureForBlit(boundDrawFbo, mask, pendingDrawBuffer);
        if (sourceTexture == null || destTexture == null) {
            return;
        }

        ensureNativeReady("blitFramebuffer");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }

        spine.blitLegacyTextureRegion(
            commandBufferHandle,
            "blitFramebuffer",
            sourceTexture,
            0,
            srcX0,
            srcY0,
            srcX1,
            srcY1,
            destTexture,
            0,
            dstX0,
            dstY0,
            dstX1,
            dstY1,
            mask,
            filter
        );
    }

    public void blitNamedFramebuffer(CommandContext ctx, int readFramebuffer, int drawFramebuffer,
                                     int srcX0, int srcY0, int srcX1, int srcY1,
                                     int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        long commandBufferHandle = requireVulkanCommandBufferHandle("blitNamedFramebuffer", ctx);
        if (filter != GL_NEAREST) {
            return;
        }

        Integer sourceTexture = resolveFramebufferTextureForBlit(readFramebuffer, mask, framebufferReadBuffer(readFramebuffer));
        Integer destTexture = resolveFramebufferTextureForBlit(drawFramebuffer, mask, framebufferDrawBuffer(drawFramebuffer));
        if (sourceTexture == null || destTexture == null) {
            return;
        }

        ensureNativeReady("blitNamedFramebuffer");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }

        spine.blitLegacyTextureRegion(
            commandBufferHandle,
            "blitNamedFramebuffer",
            sourceTexture,
            0,
            srcX0,
            srcY0,
            srcX1,
            srcY1,
            destTexture,
            0,
            dstX0,
            dstY0,
            dstX1,
            dstY1,
            mask,
            filter
        );
    }

    public void blitNamedFramebufferDSA(CommandContext ctx, int readFramebuffer, int drawFramebuffer,
                                        int srcX0, int srcY0, int srcX1, int srcY1,
                                        int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        requireVulkanCommandBufferHandle("blitNamedFramebufferDSA", ctx);
        blitNamedFramebuffer(ctx, readFramebuffer, drawFramebuffer,
            srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }

    public boolean checkARBInstancedArraysSupport() {
        return false;
    }

    public boolean checkFunctionAvailable(String functionName) {
        return false;
    }

    public boolean checkOpenGL32Support() {
        return false;
    }

    public boolean checkOpenGL33Support() {
        return false;
    }

    public void clearBufferSubData(CommandContext ctx, int target, int internalformat,
                                   long offset, long size, int format, int type, int[] data) {
        requireVulkanCommandBufferHandle("clearBufferSubData", ctx);
    }

    public void clearBufferfv(CommandContext ctx, int buffer, int drawbuffer, float[] values) {
        requireVulkanCommandBufferHandle("clearBufferfv", ctx);
        if (buffer == GL_COLOR) {
            clearNamedFramebufferfv(ctx, boundDrawFbo, buffer, drawbuffer, values);
        } else if (buffer == GL_DEPTH && values != null && values.length > 0) {
            clearFramebufferDepthAttachment(ctx, boundDrawFbo, values[0]);
        }
    }

    public void clearBufferiv(CommandContext ctx, int buffer, int drawbuffer, int[] values) {
        requireVulkanCommandBufferHandle("clearBufferiv", ctx);
        if (buffer == GL_COLOR && values != null && values.length >= 4) {
            clearNamedFramebufferfv(ctx, boundDrawFbo, buffer, drawbuffer, new float[] {
                values[0], values[1], values[2], values[3]
            });
        }
    }

    public void clearBufferuiv(CommandContext ctx, int buffer, int drawbuffer, int[] values) {
        requireVulkanCommandBufferHandle("clearBufferuiv", ctx);
        if (buffer == GL_COLOR && values != null && values.length >= 4) {
            clearNamedFramebufferfv(ctx, boundDrawFbo, buffer, drawbuffer, new float[] {
                values[0], values[1], values[2], values[3]
            });
        }
    }

    public void clearDebugMessageCallback() {
    }

    public void clearDebugMessageCallbackAMD() {
    }

    public void clearDebugMessageCallbackARB() {
    }

    public void clearDebugMessageCallbackKHR() {
    }

    public void clearNamedFramebufferfv(CommandContext ctx, int framebuffer, int buffer, int drawbuffer, float[] value) {
        requireVulkanCommandBufferHandle("clearNamedFramebufferfv", ctx);
        if (buffer == GL_COLOR) {
            clearFramebufferColorAttachment(ctx, framebuffer, GL_COLOR_ATTACHMENT0 + drawbuffer, value);
        } else if (buffer == GL_DEPTH && value != null && value.length > 0) {
            clearFramebufferDepthAttachment(ctx, framebuffer, value[0]);
        }
    }

    public void clearNamedFramebufferiv(CommandContext ctx, int framebuffer, int buffer, int drawbuffer, int[] value) {
        requireVulkanCommandBufferHandle("clearNamedFramebufferiv", ctx);
        if (buffer == GL_COLOR && value != null && value.length >= 4) {
            clearFramebufferColorAttachment(ctx, framebuffer, GL_COLOR_ATTACHMENT0 + drawbuffer, new float[] {
                value[0], value[1], value[2], value[3]
            });
        }
    }

    public void clearNamedFramebufferuiv(CommandContext ctx, int framebuffer, int buffer, int drawbuffer, int[] value) {
        requireVulkanCommandBufferHandle("clearNamedFramebufferuiv", ctx);
        if (buffer == GL_COLOR && value != null && value.length >= 4) {
            clearFramebufferColorAttachment(ctx, framebuffer, GL_COLOR_ATTACHMENT0 + drawbuffer, new float[] {
                value[0], value[1], value[2], value[3]
            });
        }
    }

    private void clearFramebufferColorAttachment(CommandContext ctx, int framebuffer, int attachment, float[] value) {
        long commandBufferHandle = requireVulkanCommandBufferHandle("clearFramebufferColorAttachment", ctx);
        if (attachment == GL_NONE || value == null || value.length < 4) {
            return;
        }

        VirtualFramebufferState state = virtualFramebufferStates.get(framebuffer);
        if (state == null) {
            return;
        }

        int texture = state.getAttachment(attachment);
        if (texture == 0) {
            return;
        }

        ensureNativeReady("clearFramebufferColorAttachment");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }

        spine.clearLegacyColorTexture(commandBufferHandle, "clearFramebufferColorAttachment", texture, value[0], value[1], value[2], value[3]);
    }

    private void clearFramebufferDepthAttachment(CommandContext ctx, int framebuffer, float depth) {
        long commandBufferHandle = requireVulkanCommandBufferHandle("clearFramebufferDepthAttachment", ctx);
        VirtualFramebufferState state = virtualFramebufferStates.get(framebuffer);
        if (state == null) {
            return;
        }

        int texture = framebufferDepthAttachmentTexture(state);
        if (texture == 0) {
            return;
        }

        ensureNativeReady("clearFramebufferDepthAttachment");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }

        spine.clearLegacyDepthTexture(commandBufferHandle, "clearFramebufferDepthAttachment", texture, depth);
    }

    public void clearTexImage(CommandContext ctx, int texture, int level, int format, int type, int[] data) {
        requireVulkanCommandBufferHandle("clearTexImage", ctx);
    }

    public void concludeQuery(CommandContext ctx, int target) {
        requireVulkanCommandBufferHandle("concludeQuery", ctx);
    }

    public void copyImageSubData(CommandContext ctx, int srcName, int srcTarget, int srcLevel, int srcX, int srcY, int srcZ,
                                 int dstName, int dstTarget, int dstLevel, int dstX, int dstY, int dstZ,
                                 int width, int height, int depth) {
        long commandBufferHandle = requireVulkanCommandBufferHandle("copyImageSubData", ctx);
        ensureNativeReady("copyImageSubData");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }

        spine.copyLegacyTextureRegion(
            commandBufferHandle,
            "copyImageSubData",
            srcName,
            srcLevel,
            srcX,
            srcY,
            srcZ,
            dstName,
            dstLevel,
            dstX,
            dstY,
            dstZ,
            width,
            height,
            depth
        );
    }

    public void copyTexImage2D(CommandContext ctx, int target, int level, int internalFormat,
                               int x, int y, int width, int height, int border) {
        long commandBufferHandle = requireVulkanCommandBufferHandle("copyTexImage2D", ctx);
        if (border != 0) {
            throw new IllegalArgumentException("copyTexImage2D does not support non-zero border on Vulkan backend");
        }

        Integer sourceTexture = resolveFramebufferTextureForCopy(boundReadFbo, pendingReadBuffer);
        if (sourceTexture == null) {
            return;
        }

        ensureNativeReady("copyTexImage2D");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }

        spine.copyToBoundLegacyTexture2D(
            commandBufferHandle,
            "copyTexImage2D",
            target,
            level,
            internalFormat,
            sourceTexture,
            x,
            y,
            width,
            height
        );
    }

    public void copyTexSubImage2D(CommandContext ctx, int target, int level,
                                  int xoffset, int yoffset, int x, int y, int width, int height) {
        long commandBufferHandle = requireVulkanCommandBufferHandle("copyTexSubImage2D", ctx);
        Integer sourceTexture = resolveFramebufferTextureForCopy(boundReadFbo, pendingReadBuffer);
        if (sourceTexture == null) {
            return;
        }

        ensureNativeReady("copyTexSubImage2D");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }

        spine.copyToBoundLegacyTextureSubImage2D(
            commandBufferHandle,
            "copyTexSubImage2D",
            target,
            level,
            xoffset,
            yoffset,
            sourceTexture,
            x,
            y,
            width,
            height
        );
    }

    public void copyTextureSubImage2D(CommandContext ctx, int texture, int level,
                                      int xoffset, int yoffset, int x, int y, int width, int height) {
        long commandBufferHandle = requireVulkanCommandBufferHandle("copyTextureSubImage2D", ctx);
        Integer sourceTexture = resolveFramebufferTextureForCopy(boundReadFbo, pendingReadBuffer);
        if (sourceTexture == null || texture == 0) {
            return;
        }

        ensureNativeReady("copyTextureSubImage2D");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }

        spine.copyLegacyTextureRegion(
            commandBufferHandle,
            "copyTextureSubImage2D",
            sourceTexture,
            0,
            x,
            y,
            0,
            texture,
            level,
            xoffset,
            yoffset,
            0,
            width,
            height,
            1
        );
    }

    public long createFenceSync(CommandContext ctx, int condition, int flags) {
        requireVulkanCommandBufferHandle("createFenceSync", ctx);
        long id = nextVirtualSyncId.getAndIncrement();
        virtualSyncs.add(id);
        return id;
    }

    public net.vulkanic.VulkanicShaderHandle createShaderHandle(CommandContext ctx, int shaderType) {
        return net.vulkanic.VulkanicShaderHandle.of(createShader(ctx, shaderType));
    }

    public net.vulkanic.VulkanicShaderHandle createShaderHandle(CommandContext ctx, net.vulkanic.VulkanicShaderStage shaderStage) {
        if (shaderStage == null) {
            throw new IllegalArgumentException("shaderStage must not be null");
        }
        return createShaderHandle(ctx, shaderStage.toLegacyGlShaderType());
    }

    public net.vulkanic.VulkanicProgramHandle createShaderProgramHandle(CommandContext ctx) {
        return net.vulkanic.VulkanicProgramHandle.of(createShaderProgram(ctx));
    }

    public void debugMessageControl(CommandContext ctx, int source, int type, int severity, int[] ids, boolean enabled) {
        requireVulkanCommandBufferHandle("debugMessageControl", ctx);
    }

    public void debugMessageControlARB(CommandContext ctx, int source, int type, int severity, int[] ids, boolean enabled) {
        requireVulkanCommandBufferHandle("debugMessageControlARB", ctx);
    }

    public void debugMessageControlKHR(CommandContext ctx, int source, int type, int severity, int[] ids, boolean enabled) {
        requireVulkanCommandBufferHandle("debugMessageControlKHR", ctx);
    }

    public void debugMessageEnableAMD(CommandContext ctx, int category, int severity, int[] ids, boolean enabled) {
        requireVulkanCommandBufferHandle("debugMessageEnableAMD", ctx);
    }

    public void destroySync(CommandContext ctx, long sync) {
        requireVulkanCommandBufferHandle("destroySync", ctx);
        virtualSyncs.remove(sync);
    }

    public void dispatchCompute(CommandContext ctx, int workX, int workY, int workZ) {
        requireVulkanCommandBufferHandle("dispatchCompute", ctx);
    }

    public void dispatchComputeIndirect(CommandContext ctx, long offset) {
        requireVulkanCommandBufferHandle("dispatchComputeIndirect", ctx);
    }

    public void disposeQueryObject(CommandContext ctx, int id) {
        requireVulkanCommandBufferHandle("disposeQueryObject", ctx);
        virtualQueries.remove(id);
    }

    public void enterDebugGroup(CommandContext ctx, int source, int id, CharSequence message) {
        requireVulkanCommandBufferHandle("enterDebugGroup", ctx);
    }

    public void exitDebugGroup(CommandContext ctx) {
        requireVulkanCommandBufferHandle("exitDebugGroup", ctx);
    }

    public void framebufferTexture(CommandContext ctx, int target, int attachment, int textarget, int texture, int level) {
        requireVulkanCommandBufferHandle("framebufferTexture", ctx);
        int framebuffer = resolveFramebufferBinding(target);
        if (framebuffer != 0) {
            recordFramebufferAttachment(framebuffer, attachment, texture);
        }
    }

    public void framebufferTexture2D(CommandContext ctx, int target, int attachment, int textarget, int texture, int level) {
        requireVulkanCommandBufferHandle("framebufferTexture2D", ctx);
        int framebuffer = resolveFramebufferBinding(target);
        if (framebuffer != 0) {
            recordFramebufferAttachment(framebuffer, attachment, texture);
        }
    }

    public void generateMipmap(CommandContext ctx, int target) {
        long commandBufferHandle = requireVulkanCommandBufferHandle("generateMipmap", ctx);
        if (!isSupportedLegacyTextureTarget(target)) {
            throw new IllegalArgumentException("Unsupported legacy Vulkan texture target for generateMipmap: " + target);
        }

        ensureNativeReady("generateMipmap");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }

        spine.generateLegacyTextureMipmap(commandBufferHandle, target);
    }

    public int generateQueryObject(CommandContext ctx) {
        requireVulkanCommandBufferHandle("generateQueryObject", ctx);
        int id = nextVirtualQueryId.getAndIncrement();
        virtualQueries.add(id);
        return id;
    }

    public void generateTextureMipmap(CommandContext ctx, int target) {
        requireVulkanCommandBufferHandle("generateTextureMipmap", ctx);
        generateMipmap(ctx, target);
    }

    public void generateTextureMipmapDSA(CommandContext ctx, int texture) {
        requireVulkanCommandBufferHandle("generateTextureMipmapDSA", ctx);

        ensureNativeReady("generateTextureMipmapDSA");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }

        int legacyTextureHandle = texture;
        spine.generateLegacyTextureMipmapForTextureId(ctx.getHandle(), legacyTextureHandle);
    }

    public long getBindVertexBufferPointer() {
        return 0L;
    }

    public long getBufferStoragePointer() {
        return 0L;
    }

    public void getFloatv(CommandContext ctx, int pname, float[] params) {
        requireVulkanCommandBufferHandle("getFloatv", ctx);
        if (params != null && params.length > 0) {
            params[0] = (float) getInteger(ctx, pname);
            for (int i = 1; i < params.length; i++) {
                params[i] = 0.0f;
            }
        }
    }

    public int getFramebufferAttachmentParameteri(CommandContext ctx, int target, int attachment, int pname) {
        requireVulkanCommandBufferHandle("getFramebufferAttachmentParameteri", ctx);
        int framebuffer = resolveFramebufferBinding(target);
        VirtualFramebufferState state = virtualFramebufferStates.get(framebuffer);
        if (state == null) {
            return 0;
        }

        int texture = state.getAttachment(attachment);
        return switch (pname) {
            case GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME -> texture;
            case GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE -> texture == 0 ? GL_NONE : GL_TEXTURE;
            default -> 0;
        };
    }

    public Object getGLCapabilities() {
        return graphicsCapabilities;
    }

    public net.vulkanic.GraphicsCapabilities getGraphicsCapabilities() {
        return graphicsCapabilities;
    }

    public String getBackendVendorName() {
        NativeSpine spine = nativeSpine;
        if (spine != null && spine.physicalDeviceVendorId != 0) {
            return mapPciVendorName(spine.physicalDeviceVendorId);
        }
        return "Vulkanic";
    }

    public String getBackendRendererName() {
        NativeSpine spine = nativeSpine;
        if (spine != null && spine.physicalDeviceName != null && !spine.physicalDeviceName.isBlank()) {
            return spine.physicalDeviceName;
        }
        return "VulkanBackend";
    }

    public String getBackendVersionName() {
        NativeSpine spine = nativeSpine;
        if (spine != null) {
            int api = spine.physicalDeviceApiVersion;
            int major = VK10.VK_API_VERSION_MAJOR(api);
            int minor = VK10.VK_API_VERSION_MINOR(api);
            int patch = VK10.VK_API_VERSION_PATCH(api);
            return "Vulkan " + major + "." + minor + "." + patch;
        }
        return "Vulkan";
    }

    public java.util.List<String> getBackendEnabledExtensions() {
        // Vulkan path does not expose OpenGL extension strings.
        return java.util.List.of();
    }

    public java.util.List<String> getBackendOptionalFeatureNames() {
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            return java.util.List.of("vulkan-bootstrap-compatibility");
        }
        return java.util.List.of(
            "native-vulkan-runtime",
            "vulkan-swapchain",
            "vulkan-command-buffer",
            "vulkan-render-pass"
        );
    }

    public java.util.List<String> getBackendLastDebugMessages() {
        // Vulkan validation/debug callbacks are backend-owned; until they are
        // plumbed into this diagnostic surface, do not borrow OpenGL messages.
        return java.util.List.of();
    }

    public boolean isBackendDebuggingEnabled() {
        return rendererDebuggingEnabled;
    }

    public int getBackendMaxTextureSize() {
        NativeSpine spine = nativeSpine;
        return spine != null ? spine.maxImageDimension2D : 16384;
    }

    public int getBackendUniformOffsetAlignment() {
        NativeSpine spine = nativeSpine;
        long alignment = spine != null ? spine.minUniformBufferOffsetAlignment : 256L;
        return alignment > Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(1, (int) alignment);
    }

    public net.blaze3d.systems.GpuDevice.GpuDeviceInfo getBackendDeviceInfo() {
        return new net.blaze3d.systems.GpuDevice.GpuDeviceInfo(
            "Vulkan",
            "Vulkan",
            getBackendVendorName(),
            getBackendRendererName(),
            getBackendVersionName(),
            false,
            getBackendOptionalFeatureNames()
        );
    }

    private static String mapPciVendorName(int vendorId) {
        return switch (vendorId) {
            case 0x10DE -> "NVIDIA";
            case 0x1002, 0x1022 -> "AMD";
            case 0x8086 -> "Intel";
            case 0x13B5 -> "ARM";
            case 0x5143 -> "Qualcomm";
            case 0x1010 -> "Imagination";
            case 0x106B -> "Apple";
            default -> "Vulkanic";
        };
    }

    public int getInteger(CommandContext ctx, int pname) {
        requireVulkanCommandBufferHandle("getInteger", ctx);
        return net.vulkanic.VulkanicIntegerQuery.fromLegacyGlPName(pname)
            .map(this::queryIntegerValue)
            .orElse(0);
    }

    public int getInteger(CommandContext ctx, net.vulkanic.VulkanicIntegerQuery query) {
        requireVulkanCommandBufferHandle("getInteger", ctx);
        if (query == null) {
            throw new IllegalArgumentException("query must not be null");
        }
        return queryIntegerValue(query);
    }

    public void getIntegerv(CommandContext ctx, int pname, int[] params) {
        requireVulkanCommandBufferHandle("getIntegerv", ctx);
        if (params != null && params.length > 0) {
            params[0] = getInteger(ctx, pname);
            for (int i = 1; i < params.length; i++) {
                params[i] = 0;
            }
        }
    }

    public int getMaxImageUnits(CommandContext ctx) {
        requireVulkanCommandBufferHandle("getMaxImageUnits", ctx);
        return queryIntegerValue(net.vulkanic.VulkanicIntegerQuery.MAX_TEXTURE_IMAGE_UNITS);
    }

    public long getNamedBufferDataPointer() {
        return 0L;
    }

    public void getProgramiv(CommandContext ctx, int program, int pname, int[] params) {
        requireVulkanCommandBufferHandle("getProgramiv", ctx);
        if (params != null && params.length > 0) {
            params[0] = getProgramParameter(ctx, program, pname);
            for (int i = 1; i < params.length; i++) {
                params[i] = 0;
            }
        }
    }

    public String getString(CommandContext ctx, int name, int index) {
        requireVulkanCommandBufferHandle("getString", ctx);
        return "";
    }

    public String getString(CommandContext ctx, int name) {
        requireVulkanCommandBufferHandle("getString", ctx);
        return switch (name) {
            case VulkanicAPI.GL_VENDOR -> "Vulkanic";
            case VulkanicAPI.GL_RENDERER -> "VulkanBackend";
            case VulkanicAPI.GL_VERSION -> "4.6.0 Vulkanic";
            case VulkanicAPI.GL_SHADING_LANGUAGE_VERSION -> "4.60 Vulkanic";
            default -> "";
        };
    }

    public int getSynci(CommandContext ctx, long sync, int pname, java.nio.IntBuffer length) {
        requireVulkanCommandBufferHandle("getSynci", ctx);
        if (length != null && length.remaining() > 0) {
            length.put(length.position(), 1);
        }
        return virtualSyncs.contains(sync) ? 1 : 0;
    }

    public int getTextureParameteri(CommandContext ctx, int texture, int pname) {
        requireVulkanCommandBufferHandle("getTextureParameteri", ctx);
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            return 0;
        }
        NativeSpine.LegacyTextureObject legacyTexture = spine.legacyTextures.get(texture);
        if (legacyTexture == null) {
            return 0;
        }
        return legacyTexture.integerParameters.getOrDefault(pname, 0);
    }

    public boolean hasBufferStorageExtension() {
        return false;
    }

    public boolean hasVertexAttribBindingExtension() {
        return false;
    }

    public net.vulkanic.GraphicsCapabilities initializeGraphicsCapabilities() {
        return graphicsCapabilities;
    }

    public void initiateQuery(CommandContext ctx, int target, int id) {
        requireVulkanCommandBufferHandle("initiateQuery", ctx);
        if (!virtualQueries.contains(id)) {
            throw new IllegalArgumentException("Unknown Vulkan virtual query handle: " + id);
        }
    }

    public boolean isBuffer(CommandContext ctx, int buffer) {
        requireVulkanCommandBufferHandle("isBuffer", ctx);
        NativeSpine spine = nativeSpine;
        return buffer != 0 && spine != null && spine.legacyBuffers.containsKey(buffer);
    }

    public boolean isEnabled(CommandContext ctx, int cap) {
        requireVulkanCommandBufferHandle("isEnabled", ctx);
        return switch (cap) {
            case VulkanicAPI.GL_BLEND -> pendingBlendEnabled;
            case VulkanicAPI.GL_DEPTH_TEST -> pendingDepthTestEnabled;
            case VulkanicAPI.GL_STENCIL_TEST -> pendingStencilTestEnabled;
            case VulkanicAPI.GL_CULL_FACE -> pendingCullFaceEnabled;
            case VulkanicAPI.GL_POLYGON_OFFSET_FILL -> pendingPolygonOffsetFactor != 0.0f || pendingPolygonOffsetUnits != 0.0f;
            default -> false;
        };
    }

    public boolean isEnabled(CommandContext ctx, net.vulkanic.VulkanicCapability capability) {
        requireVulkanCommandBufferHandle("isEnabled", ctx);
        if (capability == null) {
            throw new IllegalArgumentException("capability must not be null");
        }
        return isEnabled(ctx, capability.toLegacyGlConstant());
    }

    public boolean isFramebuffer(CommandContext ctx, int framebuffer) {
        requireVulkanCommandBufferHandle("isFramebuffer", ctx);
        return framebuffer != 0 && virtualFbos.contains(framebuffer);
    }

    public boolean isProgram(CommandContext ctx, int program) {
        requireVulkanCommandBufferHandle("isProgram", ctx);
        return program != 0 && virtualPrograms.containsKey(program);
    }

    public boolean isTexture(CommandContext ctx, int texture) {
        requireVulkanCommandBufferHandle("isTexture", ctx);
        NativeSpine spine = nativeSpine;
        return texture != 0 && spine != null && spine.legacyTextures.containsKey(texture);
    }

    public boolean isVertexArray(CommandContext ctx, int array) {
        requireVulkanCommandBufferHandle("isVertexArray", ctx);
        return array != 0 && virtualVaos.contains(array);
    }

    public void labelDebugObject(CommandContext ctx, int identifier, int name, String label) {
        requireVulkanCommandBufferHandle("labelDebugObject", ctx);
    }

    public void labelObjectExt(CommandContext ctx, int type, int object, String label) {
        requireVulkanCommandBufferHandle("labelObjectExt", ctx);
    }

        public void memoryBarrier(CommandContext ctx, int barriers) {
            long commandBufferHandle = requireVulkanCommandBufferHandle("memoryBarrier", ctx);
            if (barriers == 0) {
                return;
            }

            java.util.Optional<VulkanicResourceBarriers> typedBarriers =
                VulkanicResourceBarriers.fromOpenGLBits(barriers);
            if (typedBarriers.isPresent()) {
                applyResourceBarriers(ctx, typedBarriers.get());
                return;
            }

            ensureNativeReady("memoryBarrier");
            NativeSpine spine = nativeSpine;
            if (spine == null) {
                throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
            }
            spine.applyConservativeMemoryBarrier(commandBufferHandle);
        }

    public void multiDrawElementsBaseVertex(CommandContext ctx, int mode, long pCount, int type,
                                            long pIndices, int drawCount, long pBaseVertex) {
        VulkanicIndexType indexType = VulkanicIndexType.fromLegacyGlConstant(type)
            .orElseThrow(() -> new IllegalArgumentException(
                "Unsupported multiDrawElementsBaseVertex index type constant: " + type));

        multiDrawElementsBaseVertex(
            ctx,
            VulkanicPrimitiveMode.fromLegacyGlConstant(mode)
                .orElseThrow(() -> new IllegalArgumentException(
                    "Unsupported multiDrawElementsBaseVertex primitive mode constant: " + mode)),
            pCount,
            indexType,
            pIndices,
            drawCount,
            pBaseVertex
        );
    }

    public void multiDrawElementsBaseVertex(CommandContext ctx, VulkanicPrimitiveMode mode, long pCount,
                                            VulkanicIndexType indexType, long pIndices, int drawCount,
                                            long pBaseVertex) {
        long commandBufferHandle = requireVulkanCommandBufferHandle("multiDrawElementsBaseVertex", ctx);

        if (drawCount < 0 || pCount == 0L || pIndices == 0L || pBaseVertex == 0L) {
            throw new IllegalArgumentException(
                "multiDrawElementsBaseVertex requires drawCount >= 0 and non-null count/index/baseVertex arrays");
        }
        if (drawCount == 0) {
            return;
        }

        ensureNativeReady("multiDrawElementsBaseVertex");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }

        for (int drawIndex = 0; drawIndex < drawCount; drawIndex++) {
            int count = MemoryUtil.memGetInt(pCount + ((long) drawIndex * Integer.BYTES));
            long indices = MemoryUtil.memGetAddress(pIndices + ((long) drawIndex * org.lwjgl.system.Pointer.POINTER_SIZE));
            int baseVertex = MemoryUtil.memGetInt(pBaseVertex + ((long) drawIndex * Integer.BYTES));

            if (count <= 0) {
                continue;
            }
            if (indices < 0L) {
                throw new IllegalArgumentException("Index offset must be >= 0, got: " + indices);
            }
            if ((indices % indexType.bytesPerIndex()) != 0L) {
                throw new IllegalArgumentException(
                    "Index offset must align to index type size. offset=" + indices + ", bytesPerIndex=" + indexType.bytesPerIndex());
            }

            spine.drawLegacyElements(commandBufferHandle, mode.toGlModeConstant(), count, indexType, indices, 1, baseVertex);
        }
    }

    public void namedFramebufferDrawBuffers(CommandContext ctx, int framebuffer, int[] bufs) {
        requireVulkanCommandBufferHandle("namedFramebufferDrawBuffers", ctx);
        VirtualFramebufferState state = virtualFramebufferStates.get(framebuffer);
        if (state != null) {
            state.setDrawBuffers(bufs);
            if (framebuffer == boundDrawFbo) {
                pendingDrawBuffer = state.getPrimaryDrawBuffer();
            }
        }
    }

    public void namedFramebufferReadBuffer(CommandContext ctx, int framebuffer, int mode) {
        requireVulkanCommandBufferHandle("namedFramebufferReadBuffer", ctx);
        VirtualFramebufferState state = virtualFramebufferStates.get(framebuffer);
        if (state != null) {
            state.readBuffer = mode;
        }
        if (framebuffer == boundReadFbo) {
            pendingReadBuffer = mode;
        }
    }

    public void namedFramebufferTexture(CommandContext ctx, int framebuffer, int attachment, int texture, int level) {
        requireVulkanCommandBufferHandle("namedFramebufferTexture", ctx);
        recordFramebufferAttachment(framebuffer, attachment, texture);
    }

    public void namedFramebufferTextureDSA(CommandContext ctx, int framebuffer, int attachment, int texture, int level) {
        requireVulkanCommandBufferHandle("namedFramebufferTextureDSA", ctx);
        recordFramebufferAttachment(framebuffer, attachment, texture);
    }

    public void readPixels(CommandContext ctx, int x, int y, int width, int height, int format, int type, float[] pixels) {
        requireVulkanCommandBufferHandle("readPixels", ctx);
        if (pixels != null) {
            java.util.Arrays.fill(pixels, 0.0f);
        }
    }

    public void readPixels(CommandContext ctx, int x, int y, int width, int height, int format, int type, long pixels) {
        long commandBufferHandle = requireVulkanCommandBufferHandle("readPixels", ctx);
        if (width <= 0 || height <= 0) {
            return;
        }

        Integer sourceTexture = resolveFramebufferTextureForCopy(boundReadFbo, pendingReadBuffer);
        if (sourceTexture == null) {
            return;
        }

        ensureNativeReady("readPixels");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }

        spine.readLegacyTextureRegionToBoundPixelPackBuffer(commandBufferHandle, "readPixels", sourceTexture, x, y, width, height, pixels);
    }

    public int resolveFramebufferForTextures(CommandContext ctx, net.vulkanic.VulkanicTexture colorTexture,
                                             net.vulkanic.VulkanicTexture depthTexture) {
        requireVulkanCommandBufferHandle("resolveFramebufferForTextures", ctx);
        int colorHandle = resolveTextureHandle(ctx, colorTexture);
        if (colorHandle == 0) {
            return 0;
        }

        int depthHandle = resolveTextureHandle(ctx, depthTexture);
        if (depthTexture != null && depthHandle == 0) {
            return 0;
        }

        int matchedFramebuffer = 0;
        for (Map.Entry<Integer, VirtualFramebufferState> entry : virtualFramebufferStates.entrySet()) {
            int framebuffer = entry.getKey();
            if (framebuffer == 0) {
                continue;
            }

            if (!matchesSingleColorFramebufferContract(entry.getValue(), colorHandle, depthHandle)) {
                continue;
            }

            if (matchedFramebuffer == 0 || framebuffer < matchedFramebuffer) {
                matchedFramebuffer = framebuffer;
            }
        }

        return matchedFramebuffer != 0 ? matchedFramebuffer : resolveOrCreateImplicitFramebuffer(colorHandle, depthHandle);
    }

    private static boolean matchesSingleColorFramebufferContract(VirtualFramebufferState state, int colorHandle, int depthHandle) {
        int colorAttachmentCount = 0;
        for (int drawBuffer : state.getDrawBuffers()) {
            if (drawBuffer == GL_NONE) {
                continue;
            }

            int attachment = drawBuffer == VulkanicAPI.GL_BACK ? GL_COLOR_ATTACHMENT0 : drawBuffer;
            if (state.getAttachment(attachment) != colorHandle) {
                return false;
            }

            colorAttachmentCount++;
            if (colorAttachmentCount > 1) {
                return false;
            }
        }

        return colorAttachmentCount == 1 && framebufferDepthAttachmentTexture(state) == depthHandle;
    }

    private static int framebufferDepthAttachmentTexture(VirtualFramebufferState state) {
        int depthTexture = state.getAttachment(GL_DEPTH_ATTACHMENT);
        if (depthTexture != 0) {
            return depthTexture;
        }
        return state.getAttachment(GL_DEPTH_STENCIL_ATTACHMENT);
    }

    private int resolveOrCreateImplicitFramebuffer(int colorHandle, int depthHandle) {
        FramebufferTexturePairKey key = new FramebufferTexturePairKey(colorHandle, depthHandle);
        while (true) {
            Integer cachedFramebuffer = implicitFramebufferByTexturePair.get(key);
            if (cachedFramebuffer != null) {
                if (virtualFramebufferStates.containsKey(cachedFramebuffer)) {
                    return cachedFramebuffer;
                }

                implicitFramebufferByTexturePair.remove(key, cachedFramebuffer);
            }

            int framebuffer = nextVirtualFboId.getAndIncrement();
            VirtualFramebufferState state = new VirtualFramebufferState();
            state.setAttachment(GL_COLOR_ATTACHMENT0, colorHandle);
            if (depthHandle != 0) {
                state.setAttachment(GL_DEPTH_ATTACHMENT, depthHandle);
            }

            virtualFbos.add(framebuffer);
            virtualFramebufferStates.put(framebuffer, state);

            Integer racedFramebuffer = implicitFramebufferByTexturePair.putIfAbsent(key, framebuffer);
            if (racedFramebuffer == null) {
                return framebuffer;
            }

            virtualFbos.remove(framebuffer);
            virtualFramebufferStates.remove(framebuffer);
            if (virtualFramebufferStates.containsKey(racedFramebuffer)) {
                return racedFramebuffer;
            }

            implicitFramebufferByTexturePair.remove(key, racedFramebuffer);
        }
    }

    private void releaseImplicitFramebuffersForTexture(int texture) {
        for (Map.Entry<FramebufferTexturePairKey, Integer> entry : new ArrayList<>(implicitFramebufferByTexturePair.entrySet())) {
            FramebufferTexturePairKey key = entry.getKey();
            if (key.colorTexture() != texture && key.depthTexture() != texture) {
                continue;
            }

            Integer framebuffer = entry.getValue();
            if (!implicitFramebufferByTexturePair.remove(key, framebuffer)) {
                continue;
            }

            if (framebuffer != null) {
                virtualFbos.remove(framebuffer);
                virtualFramebufferStates.remove(framebuffer);
            }
        }
    }

    private void releaseImplicitFramebufferBinding(int framebuffer) {
        for (Map.Entry<FramebufferTexturePairKey, Integer> entry : new ArrayList<>(implicitFramebufferByTexturePair.entrySet())) {
            Integer mappedFramebuffer = entry.getValue();
            if (mappedFramebuffer != null && mappedFramebuffer == framebuffer) {
                implicitFramebufferByTexturePair.remove(entry.getKey(), mappedFramebuffer);
            }
        }
    }

    public int resolveTextureHandle(CommandContext ctx, net.vulkanic.VulkanicTexture texture) {
        requireVulkanCommandBufferHandle("resolveTextureHandle", ctx);
        if (texture == null) {
            return 0;
        }

        if (texture instanceof net.blaze3d.textures.GpuTexture gpuTexture) {
            return resolveGpuTextureLegacyHandle(gpuTexture);
        }

        int legacyHandle = resolveLegacyGlHandleViaAccessor(texture);
        if (legacyHandle != 0) {
            return legacyHandle;
        }

        return 0;
    }

    private static int resolveLegacyGlHandleViaAccessor(net.vulkanic.VulkanicTexture texture) {
        try {
            java.lang.reflect.Method accessor = texture.getClass().getMethod("getGlHandle");
            if (accessor.getReturnType() != int.class) {
                return 0;
            }

            Object result = accessor.invoke(texture);
            return result instanceof Integer handle ? handle : 0;
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return 0;
        }
    }

    private static int resolveGpuTextureLegacyHandle(net.blaze3d.textures.GpuTexture texture) {
        Class<?> currentType = texture.getClass();
        while (currentType != null) {
            try {
                java.lang.reflect.Field idField = currentType.getDeclaredField("id");
                if (idField.getType() != int.class) {
                    return 0;
                }

                idField.setAccessible(true);
                return idField.getInt(texture);
            } catch (NoSuchFieldException ignored) {
                currentType = currentType.getSuperclass();
            } catch (IllegalAccessException | SecurityException ignored) {
                return 0;
            }
        }

        return 0;
    }

    private static int resolveGpuBufferLegacyHandle(net.blaze3d.buffers.GpuBuffer buffer) {
        try {
            java.lang.reflect.Method accessor = buffer.getClass().getMethod("getHandle");
            if (accessor.getReturnType() == int.class) {
                Object result = accessor.invoke(buffer);
                if (result instanceof Integer handle) {
                    return handle;
                }
            }
        } catch (ReflectiveOperationException | SecurityException ignored) {
        }

        Class<?> currentType = buffer.getClass();
        while (currentType != null) {
            try {
                java.lang.reflect.Field handleField = currentType.getDeclaredField("handle");
                if (handleField.getType() != int.class) {
                    return 0;
                }

                handleField.setAccessible(true);
                return handleField.getInt(buffer);
            } catch (NoSuchFieldException ignored) {
                currentType = currentType.getSuperclass();
            } catch (IllegalAccessException | SecurityException ignored) {
                return 0;
            }
        }

        return 0;
    }

    public net.vulkanic.VulkanicUniformLocation resolveUniformLocation(CommandContext ctx, int program, CharSequence name) {
        requireVulkanCommandBufferHandle("resolveUniformLocation", ctx);
        return net.vulkanic.VulkanicUniformLocation.of(getUniformLocation(ctx, program, name));
    }

    public String retrieveActiveUniformBlockName(CommandContext ctx, int program, int uniformBlockIndex) {
        requireVulkanCommandBufferHandle("retrieveActiveUniformBlockName", ctx);
        VirtualProgram virtualProgram = virtualPrograms.get(program);
        if (virtualProgram == null || uniformBlockIndex < 0 || uniformBlockIndex >= virtualProgram.activeUniformBlocks.size()) {
            return "";
        }
        return virtualProgram.activeUniformBlocks.get(uniformBlockIndex);
    }

    public int retrieveQueryObjectInt(CommandContext ctx, int id, int pname) {
        requireVulkanCommandBufferHandle("retrieveQueryObjectInt", ctx);
        return virtualQueries.contains(id) ? 0 : 0;
    }

    public long retrieveQueryObjectInt64(CommandContext ctx, int id, int pname) {
        requireVulkanCommandBufferHandle("retrieveQueryObjectInt64", ctx);
        return 0L;
    }

    public void setMaxShaderCompilerThreads(int count) {
    }

    public void setupArbDebugSystem(int verbosityLevel, boolean synchronous,
                                    java.util.function.Consumer<String> messageHandler) {
    }

    public void setupDebugMessageCallback(VulkanicAPI.DebugMessageCallback callback) {
    }

    public void setupDebugMessageCallback(VulkanicAPI.DebugMessageCallbackAMD callback) {
    }

    public void setupDebugMessageCallbackKHR(VulkanicAPI.DebugMessageCallback callback) {
    }

    public void setupDebugMessageCallbackARB(VulkanicAPI.DebugMessageCallback callback) {
    }

    public void setupDebugMessageCallbackAMD(VulkanicAPI.DebugMessageCallbackAMD callback) {
    }

    public void setupDebugMessageCallback(java.io.PrintStream stream) {
    }

    public void setupKhrDebugSystem(int verbosityLevel, boolean synchronous,
                                    java.util.function.Consumer<String> messageHandler) {
    }

    public boolean supportsArbDebugOutput() {
        return false;
    }

    public boolean supportsKhrDebug() {
        return false;
    }

    public void texBuffer(CommandContext ctx, int target, int internalFormat, int buffer) {
        requireVulkanCommandBufferHandle("texBuffer", ctx);

        if (target != VulkanicAPI.GL_TEXTURE_BUFFER) {
            throw new IllegalArgumentException(
                "Vulkan texBuffer currently supports only GL_TEXTURE_BUFFER target, got: " + target);
        }
        if (buffer < 0) {
            throw new IllegalArgumentException("buffer must be >= 0, got: " + buffer);
        }

        NativeSpine spine = nativeSpine;
        if (spine == null) {
            return; // Native spine not yet ready; silently defer (matches other compatibility paths)
        }
        spine.bindLegacyTexelBufferForActiveUnit(internalFormat, buffer);
    }

    public void texParameteriv(CommandContext ctx, int target, int pname, int[] params) {
        requireVulkanCommandBufferHandle("texParameteriv", ctx);
        if (params != null && params.length > 0) {
            texParameteri(ctx, target, pname, params[0]);
        }
    }

    public void textureParameterf(CommandContext ctx, int texture, int pname, float param) {
        requireVulkanCommandBufferHandle("textureParameterf", ctx);
        textureParameteri(ctx, texture, pname, Math.round(param));
    }

    public void textureParameteri(CommandContext ctx, int texture, int pname, int param) {
        requireVulkanCommandBufferHandle("textureParameteri", ctx);
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            return;
        }
        NativeSpine.LegacyTextureObject legacyTexture = spine.legacyTextures.get(texture);
        if (legacyTexture != null) {
            legacyTexture.integerParameters.put(pname, param);
        }
    }

    public void textureParameteriv(CommandContext ctx, int texture, int pname, int[] params) {
        requireVulkanCommandBufferHandle("textureParameteriv", ctx);
        if (params != null && params.length > 0) {
            textureParameteri(ctx, texture, pname, params[0]);
        }
    }

    public void uploadTexture1D(CommandContext ctx, int target, int level, int internalformat,
                                int width, int border, int format, int type, java.nio.ByteBuffer pixels) {
        requireVulkanCommandBufferHandle("uploadTexture1D", ctx);
    }

    public void uploadTexture3D(CommandContext ctx, int target, int level, int internalformat,
                                int width, int height, int depth, int border,
                                int format, int type, java.nio.ByteBuffer pixels) {
        requireVulkanCommandBufferHandle("uploadTexture3D", ctx);
    }

    public int waitForSync(CommandContext ctx, long sync, int flags, long timeout) {
        requireVulkanCommandBufferHandle("waitForSync", ctx);
        return virtualSyncs.contains(sync) ? 0x911A /* GL_ALREADY_SIGNALED */ : 0x911B /* GL_TIMEOUT_EXPIRED */;
    }

    public int getAttributeLocation(CommandContext ctx, int program, CharSequence name) {
        requireVulkanCommandBufferHandle("getAttributeLocation", ctx);
        VirtualProgram virtualProgram = virtualPrograms.get(program);
        if (virtualProgram == null || name == null) {
            return -1;
        }

        return virtualProgram.attributeLocationsByName.getOrDefault(name.toString(), -1);
    }

    public void setAttributeLocation(CommandContext ctx, int program, int index, CharSequence name) {
        requireVulkanCommandBufferHandle("setAttributeLocation", ctx);
        VirtualProgram virtualProgram = virtualPrograms.get(program);
        if (virtualProgram == null || name == null) {
            return;
        }

        virtualProgram.attributeLocationsByName.put(name.toString(), index);
    }

    /**
     * Returns a stable virtual block index for linked GLSL uniform blocks so the
     * compatibility layer can preserve UBO wiring on the Vulkan path.
     */
    public int getUniformBlockIndex(CommandContext ctx, int program, String uniformBlockName) {
        requireVulkanCommandBufferHandle("getUniformBlockIndex", ctx);
        VirtualProgram virtualProgram = virtualPrograms.get(program);
        return virtualProgram == null ? -1 : virtualProgram.activeUniformBlocks.indexOf(uniformBlockName);
    }

    /**
     * No-op. UBO binding points are encoded in the Vulkan pipeline layout;
     * runtime rebinding is handled through {@code bindPipelineResources}.
     */
    public void uniformBlockBinding(CommandContext ctx, int program,
                                    int uniformBlockIndex, int uniformBlockBindingPoint) {
        requireVulkanCommandBufferHandle("uniformBlockBinding", ctx);
    }

    /**
     * Returns reflected uniform names from the linked GLSL sources so legacy
     * compatibility code can enumerate active sampler-style uniforms on Vulkan.
     */
    public String getActiveUniform(CommandContext ctx, int program, int index, int size,
                                   java.nio.IntBuffer type, java.nio.IntBuffer name) {
        requireVulkanCommandBufferHandle("getActiveUniform", ctx);
        VirtualProgram virtualProgram = virtualPrograms.get(program);
        if (virtualProgram == null || index < 0 || index >= virtualProgram.activeUniforms.size()) {
            return "";
        }

        ReflectedUniform reflectedUniform = virtualProgram.activeUniforms.get(index);
        if (type != null && type.hasRemaining()) {
            type.put(type.position(), reflectedUniform.arraySize());
        }
        if (name != null && name.hasRemaining()) {
            name.put(name.position(), reflectedUniform.legacyType());
        }
        return reflectedUniform.name();
    }

    // =====================================================================
    //  Buffer binding extras
    //  Vulkan equivalent: vkCmdBindDescriptorSets (handled by bindPipelineResources)
    // =====================================================================

    public void bindBufferBase(CommandContext ctx, int target, int index, int buffer) {
        requireVulkanCommandBufferHandle("bindBufferBase", ctx);
    }

    public void bindUniformBufferBase(CommandContext ctx, int bindingPoint, int bufferId) {
        requireVulkanCommandBufferHandle("bindUniformBufferBase", ctx);
    }

    public void bindUniformBufferRange(CommandContext ctx, int target, int index,
                                       int buffer, long offset, long size) {
        requireVulkanCommandBufferHandle("bindUniformBufferRange", ctx);
    }

    /**
     * No-op. Fragment output locations are fixed in SPIR-V; runtime binding
     * is not supported in Vulkan.
     */
    public void bindFragDataLocation(CommandContext ctx, int program,
                                     int colorNumber, CharSequence name) {
        requireVulkanCommandBufferHandle("bindFragDataLocation", ctx);
    }

    // =====================================================================
    //  Image texture binding
    // =====================================================================

    /**
     * No-op. Storage images in Vulkan are bound via descriptor sets.
     */
    public void bindImageTexture(CommandContext ctx, int unit, int texture, int level,
                                 boolean layered, int layer, int access, int format) {
        requireVulkanCommandBufferHandle("bindImageTexture", ctx);
    }

    // =====================================================================
    //  Draw buffers
    // =====================================================================

    /**
     * Records draw-buffer selection on the currently bound draw framebuffer so
     * framebuffer-backed render passes can resolve the intended color outputs.
     */
    public void drawBuffers(CommandContext ctx, int[] buffers) {
        requireVulkanCommandBufferHandle("drawBuffers", ctx);
        VirtualFramebufferState state = virtualFramebufferStates.get(boundDrawFbo);
        if (state != null) {
            state.setDrawBuffers(buffers);
            pendingDrawBuffer = state.getPrimaryDrawBuffer();
        }
    }

    // =====================================================================
    //  Vertex array objects
    //  Vulkan has no VAO concept; virtual IDs satisfy GL compat callers.
    // =====================================================================

    /**
     * Creates and returns a virtual VAO handle. Vertex buffer binding in Vulkan
     * is command-buffer-time ({@code vkCmdBindVertexBuffers}); the handle is a
     * lightweight token for callers that follow the GL bind-then-draw pattern.
     */
    public int createVertexArray(CommandContext ctx) {
        requireVulkanCommandBufferHandle("createVertexArray", ctx);
        int id = nextVirtualVaoId.getAndIncrement();
        virtualVaos.add(id);
        virtualVaoStates.put(id, new VirtualVaoState());
        return id;
    }

    /**
     * Releases a virtual VAO handle. If the handle does not correspond to a
     * previously created virtual VAO the call is silently ignored.
     */
    public void deleteVertexArrays(CommandContext ctx, int vertexArray) {
        requireVulkanCommandBufferHandle("deleteVertexArrays", ctx);
        virtualVaos.remove(vertexArray);
        virtualVaoStates.remove(vertexArray);
        if (boundVirtualVao == vertexArray) {
            boundVirtualVao = 0;
        }
    }

    private VirtualVaoState currentVirtualVaoState() {
        int vao = boundVirtualVao;
        if (vao == 0) {
            return virtualVaoStates.computeIfAbsent(0, ignored -> new VirtualVaoState());
        }
        if (!virtualVaos.contains(vao)) {
            virtualVaos.add(vao);
        }
        return virtualVaoStates.computeIfAbsent(vao, ignored -> new VirtualVaoState());
    }

    /**
     * Marks vertex attribute {@code index} as enabled. In Vulkan this state is
     * baked into {@code VkPipelineVertexInputStateCreateInfo} at pipeline
     * creation time; this call is accepted as a no-op and the state is available
     * at the next pipeline build.
     */
    public void enableVertexAttribArray(CommandContext ctx, int index) {
        requireVulkanCommandBufferHandle("enableVertexAttribArray", ctx);
        currentVirtualVaoState().enableAttribute(index);
    }

    /** Marks vertex attribute {@code index} as disabled. */
    public void disableVertexAttribArray(CommandContext ctx, int index) {
        requireVulkanCommandBufferHandle("disableVertexAttribArray", ctx);
        currentVirtualVaoState().disableAttribute(index);
    }

    /**
     * Specifies vertex attribute format for {@code index}. Maps to
     * {@code VkVertexInputAttributeDescription}; cached for pipeline construction.
     */
    public void setVertexAttribPointer(CommandContext ctx, int index, int size, int type,
                                       boolean normalized, int stride, long pointer) {
        requireVulkanCommandBufferHandle("setVertexAttribPointer", ctx);
        currentVirtualVaoState().setAttributePointer(index, size, type, normalized, false, stride, pointer, currentLegacyArrayBufferBinding());
    }

    public void setVertexAttribIPointer(CommandContext ctx, int index, int size, int type,
                                        int stride, long pointer) {
        requireVulkanCommandBufferHandle("setVertexAttribIPointer", ctx);
        currentVirtualVaoState().setAttributePointer(index, size, type, false, true, stride, pointer, currentLegacyArrayBufferBinding());
    }

    /** Sets the per-instance divisor for vertex attribute {@code index}. */
    public void setVertexAttribDivisor(CommandContext ctx, int index, int divisor) {
        requireVulkanCommandBufferHandle("setVertexAttribDivisor", ctx);
        currentVirtualVaoState().setDivisor(index, divisor);
    }

    /**
     * Sets a constant (non-array) vertex attribute. In Vulkan constant vertex
     * data is passed via push constants or per-instance UBOs; accepted here as
     * a no-op.
     */
    public void setVertexAttrib4f(CommandContext ctx, int index,
                                  float v0, float v1, float v2, float v3) {
        requireVulkanCommandBufferHandle("setVertexAttrib4f", ctx);
    }

    public void setVertexAttribFormat(CommandContext ctx, int attribindex, int size, int type,
                                      boolean normalized, int relativeoffset) {
        requireVulkanCommandBufferHandle("setVertexAttribFormat", ctx);
        currentVirtualVaoState().setAttributeFormat(attribindex, size, type, normalized, false, relativeoffset);
    }

    public void setVertexAttribIFormat(CommandContext ctx, int attribindex, int size, int type,
                                       int relativeoffset) {
        requireVulkanCommandBufferHandle("setVertexAttribIFormat", ctx);
        currentVirtualVaoState().setAttributeFormat(attribindex, size, type, false, true, relativeoffset);
    }

    public void setVertexAttribBinding(CommandContext ctx, int attribindex, int bindingindex) {
        requireVulkanCommandBufferHandle("setVertexAttribBinding", ctx);
        currentVirtualVaoState().setAttributeBinding(attribindex, bindingindex);
    }

    public void bindVertexBuffer(CommandContext ctx, int bindingindex, int buffer, long offset, int stride) {
        long commandBufferHandle = requireVulkanCommandBufferHandle("bindVertexBuffer", ctx);
        currentVirtualVaoState().setBinding(bindingindex, stride, offset, buffer);
        if (buffer <= 0) {
            return;
        }
        ensureNativeReady("bindVertexBuffer");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }
        NativeSpine.LegacyBufferObject legacy = spine.requireLegacyBuffer(buffer);
        VulkanBuffer vulkanBuffer = legacy.buffer;
        if (vulkanBuffer == null || vulkanBuffer.isClosed()) {
            return;
        }
        spine.bindVertexBuffer(commandBufferHandle, bindingindex, vulkanBuffer.getVkBufferHandle(), offset);
    }

    private int currentLegacyArrayBufferBinding() {
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            return 0;
        }
        return spine.legacyBufferBindings.getOrDefault(VulkanicAPI.GL_ARRAY_BUFFER, 0);
    }

    // =====================================================================
    //  Sampler objects
    // =====================================================================

    /**
     * Creates a virtual sampler handle. In Vulkan samplers are {@code VkSampler}
     * objects created at pipeline initialisation time through the descriptor
     * system. Virtual IDs are returned to satisfy GL compat callers.
     */
    public int createSampler(CommandContext ctx) {
        requireVulkanCommandBufferHandle("createSampler", ctx);
        int id = nextVirtualSamplerId.getAndIncrement();
        virtualSamplers.add(id);
        virtualSamplerStates.put(id, new VirtualSamplerState());
        return id;
    }

    /** Releases a virtual sampler handle and removes any per-unit bindings. */
    public void deleteSampler(CommandContext ctx, int sampler) {
        requireVulkanCommandBufferHandle("deleteSampler", ctx);
        virtualSamplers.remove(sampler);
        virtualSamplerStates.remove(sampler);
        boundSamplerPerUnit.values().removeIf(bound -> bound.equals(sampler));
    }

    /**
     * Binds {@code sampler} to texture unit {@code unit}. Pass {@code 0} to
     * unbind. The binding is cached so that pipeline resource resolution can
     * interrogate it when building a Vulkan descriptor set.
     */
    public void bindSampler(CommandContext ctx, int unit, int sampler) {
        requireVulkanCommandBufferHandle("bindSampler", ctx);
        if (sampler == 0) {
            boundSamplerPerUnit.remove(unit);
        } else {
            boundSamplerPerUnit.put(unit, sampler);
        }
    }

    /** Bulk-binds an array of samplers starting from texture unit {@code first}. */
    public void bindSamplers(CommandContext ctx, int first, int[] samplers) {
        requireVulkanCommandBufferHandle("bindSamplers", ctx);
        for (int i = 0; i < samplers.length; i++) {
            int unit = first + i;
            if (samplers[i] == 0) {
                boundSamplerPerUnit.remove(unit);
            } else {
                boundSamplerPerUnit.put(unit, samplers[i]);
            }
        }
    }

    /**
     * No-op. Sampler parameters in Vulkan are baked into
     * {@code VkSamplerCreateInfo} at object creation time; post-creation
     * mutation is not supported.
     */
    public void setSamplerParameteri(CommandContext ctx, int sampler, int pname, int param) {
        requireVulkanCommandBufferHandle("setSamplerParameteri", ctx);
        VirtualSamplerState samplerState = getVirtualSamplerState(sampler);
        if (samplerState == null) {
            return;
        }

        samplerState.setParameteri(pname, param);
    }

    public void setSamplerParameterf(CommandContext ctx, int sampler, int pname, float param) {
        requireVulkanCommandBufferHandle("setSamplerParameterf", ctx);
        VirtualSamplerState samplerState = getVirtualSamplerState(sampler);
        if (samplerState == null) {
            return;
        }

        samplerState.setParameterf(pname, param);
    }

    public void setSamplerParameteriv(CommandContext ctx, int sampler, int pname, int[] params) {
        requireVulkanCommandBufferHandle("setSamplerParameteriv", ctx);
        if (params == null || params.length == 0) {
            return;
        }

        setSamplerParameteri(ctx, sampler, pname, params[0]);
    }

    // =====================================================================
    //  Stencil state (pipeline-baked in Vulkan — cached for pipeline build)
    // =====================================================================

    /**
     * Sets the stencil test function, reference value and comparison mask.
     * In Vulkan these values are encoded in {@code VkPipelineDepthStencilStateCreateInfo};
     * the values are cached here and applied at the next pipeline creation.
     */
    public void setStencilFunc(CommandContext ctx, int func, int ref, int mask) {
        requireVulkanCommandBufferHandle("setStencilFunc", ctx);
        pendingStencilFunc = func;
        pendingStencilRef  = ref;
        pendingStencilMask = mask;
        pendingBackStencilFunc = func;
        pendingBackStencilRef  = ref;
        pendingBackStencilMask = mask;
    }

    public void setStencilFuncSeparate(CommandContext ctx, int face, int func, int ref, int mask) {
        requireVulkanCommandBufferHandle("setStencilFuncSeparate", ctx);
        if (appliesToFrontFace(face)) {
            pendingStencilFunc = func;
            pendingStencilRef  = ref;
            pendingStencilMask = mask;
        }
        if (appliesToBackFace(face)) {
            pendingBackStencilFunc = func;
            pendingBackStencilRef  = ref;
            pendingBackStencilMask = mask;
        }
    }

    /**
     * Sets the stencil operation for the three state transitions (stencil fail,
     * depth fail, depth pass).
     */
    public void setStencilOp(CommandContext ctx, int sfail, int dpfail, int dppass) {
        requireVulkanCommandBufferHandle("setStencilOp", ctx);
        pendingStencilFail   = sfail;
        pendingStencilDpFail = dpfail;
        pendingStencilDpPass = dppass;
        pendingBackStencilFail   = sfail;
        pendingBackStencilDpFail = dpfail;
        pendingBackStencilDpPass = dppass;
    }

    public void setStencilOpSeparate(CommandContext ctx, int face, int sfail, int dpfail, int dppass) {
        requireVulkanCommandBufferHandle("setStencilOpSeparate", ctx);
        if (appliesToFrontFace(face)) {
            pendingStencilFail   = sfail;
            pendingStencilDpFail = dpfail;
            pendingStencilDpPass = dppass;
        }
        if (appliesToBackFace(face)) {
            pendingBackStencilFail   = sfail;
            pendingBackStencilDpFail = dpfail;
            pendingBackStencilDpPass = dppass;
        }
    }

    /** Sets the stencil write mask. */
    public void setStencilWriteMask(CommandContext ctx, int mask) {
        requireVulkanCommandBufferHandle("setStencilWriteMask", ctx);
        pendingStencilWriteMask = mask;
        pendingBackStencilWriteMask = mask;
    }

    public void setStencilWriteMaskSeparate(CommandContext ctx, int face, int mask) {
        requireVulkanCommandBufferHandle("setStencilWriteMaskSeparate", ctx);
        if (appliesToFrontFace(face)) {
            pendingStencilWriteMask = mask;
        }
        if (appliesToBackFace(face)) {
            pendingBackStencilWriteMask = mask;
        }
    }

    private static boolean appliesToFrontFace(int face) {
        return face == VulkanicAPI.GL_FRONT || face == VulkanicAPI.GL_FRONT_AND_BACK;
    }

    private static boolean appliesToBackFace(int face) {
        return face == VulkanicAPI.GL_BACK || face == VulkanicAPI.GL_FRONT_AND_BACK;
    }

    private VulkanPipelineState.StencilState currentStencilState() {
        return new VulkanPipelineState.StencilState(
            pendingStencilTestEnabled,
            VulkanPipelineState.StencilFaceState.fromLegacyGl(
                pendingStencilFail,
                pendingStencilDpFail,
                pendingStencilDpPass,
                pendingStencilFunc,
                pendingStencilMask,
                pendingStencilWriteMask,
                pendingStencilRef
            ),
            VulkanPipelineState.StencilFaceState.fromLegacyGl(
                pendingBackStencilFail,
                pendingBackStencilDpFail,
                pendingBackStencilDpPass,
                pendingBackStencilFunc,
                pendingBackStencilMask,
                pendingBackStencilWriteMask,
                pendingBackStencilRef
            )
        );
    }

    // =====================================================================
    //  Helper — get NativeSpine for methods that require a live spine
    // =====================================================================

    private NativeSpine requireNativeSpineForCommandOp(String operation) {
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException(
                "VulkanBackend." + operation + " requires an active native Vulkan spine.");
        }
        return spine;
    }

    private VirtualShader requireVirtualShader(int shaderId) {
        VirtualShader virtualShader = virtualShaders.get(shaderId);
        if (virtualShader == null) {
            throw new IllegalArgumentException("Unknown Vulkan virtual shader handle: " + shaderId);
        }
        return virtualShader;
    }

    private VirtualProgram requireVirtualProgram(int programId) {
        VirtualProgram virtualProgram = virtualPrograms.get(programId);
        if (virtualProgram == null) {
            throw new IllegalArgumentException("Unknown Vulkan virtual program handle: " + programId);
        }
        return virtualProgram;
    }

    private void applyFramebufferReadState(int framebuffer) {
        VirtualFramebufferState state = virtualFramebufferStates.get(framebuffer);
        if (state != null) {
            pendingReadBuffer = state.readBuffer;
        }
    }

    private void applyFramebufferDrawState(int framebuffer) {
        VirtualFramebufferState state = virtualFramebufferStates.get(framebuffer);
        if (state != null) {
            pendingDrawBuffer = state.getPrimaryDrawBuffer();
        }
    }

    private int resolveFramebufferBinding(int target) {
        if (target == GL_READ_FRAMEBUFFER) {
            return boundReadFbo;
        }
        if (target == GL_DRAW_FRAMEBUFFER || target == GL_FRAMEBUFFER) {
            return boundDrawFbo;
        }
        return 0;
    }

    private void recordFramebufferAttachment(int framebuffer, int attachment, int texture) {
        VirtualFramebufferState state = virtualFramebufferStates.get(framebuffer);
        if (state == null) {
            return;
        }

        state.setAttachment(attachment, texture);
        if (attachment == GL_DEPTH_STENCIL_ATTACHMENT) {
            state.setAttachment(GL_DEPTH_ATTACHMENT, texture);
        }
    }

    private int framebufferReadBuffer(int framebuffer) {
        VirtualFramebufferState state = virtualFramebufferStates.get(framebuffer);
        return state != null ? state.readBuffer : pendingReadBuffer;
    }

    private int framebufferDrawBuffer(int framebuffer) {
        VirtualFramebufferState state = virtualFramebufferStates.get(framebuffer);
        return state != null ? state.getPrimaryDrawBuffer() : pendingDrawBuffer;
    }

    private int[] framebufferDrawBuffers(int framebuffer) {
        VirtualFramebufferState state = virtualFramebufferStates.get(framebuffer);
        return state != null ? state.getDrawBuffers() : new int[]{pendingDrawBuffer};
    }

    private Integer resolveFramebufferTextureForCopy(int framebuffer, int selectedBuffer) {
        return resolveFramebufferTextureForBlit(framebuffer, GL_COLOR_BUFFER_BIT, selectedBuffer);
    }

    private Integer resolveFramebufferTextureForBlit(int framebuffer, int mask, int selectedBuffer) {
        VirtualFramebufferState state = virtualFramebufferStates.get(framebuffer);
        if (state == null) {
            return null;
        }

        if ((mask & (GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT)) != 0) {
            int depthTexture = state.getAttachment(GL_DEPTH_ATTACHMENT);
            if (depthTexture == 0) {
                depthTexture = state.getAttachment(GL_DEPTH_STENCIL_ATTACHMENT);
            }
            return depthTexture == 0 ? null : depthTexture;
        }

        int attachment = selectedBuffer == GL_NONE ? GL_COLOR_ATTACHMENT0 : selectedBuffer;
        int colorTexture = state.getAttachment(attachment);
        return colorTexture == 0 ? null : colorTexture;
    }

    private static net.vulkanic.GraphicsCapabilities createVulkanGraphicsCapabilities() {
        return new net.vulkanic.GraphicsCapabilities(
            GraphicsBackendType.VULKAN,
            false, false, false, false, false,
            false, false,
            false, false, false, false,
            false, false, false, false, false, false, false,
            false, false, false,
            false, false, false,
            false, false, false,
            false, false,
            false, false,
            false, false,
            false, false,
            false
        );
    }

    private int queryIntegerValue(net.vulkanic.VulkanicIntegerQuery query) {
        NativeSpine spine = nativeSpine;
        return switch (query) {
            case CONTEXT_FLAGS -> 0;
            case CURRENT_PROGRAM -> boundVirtualProgram;
            case VERTEX_ARRAY_BINDING -> boundVirtualVao;
            case ARRAY_BUFFER_BINDING -> spine != null
                ? spine.legacyBufferBindings.getOrDefault(VulkanicAPI.GL_ARRAY_BUFFER, 0)
                : 0;
            case ELEMENT_ARRAY_BUFFER_BINDING -> spine != null
                ? spine.legacyBufferBindings.getOrDefault(VulkanicAPI.GL_ELEMENT_ARRAY_BUFFER, 0)
                : 0;
            case ACTIVE_TEXTURE -> spine != null
                ? VulkanicAPI.GL_TEXTURE0 + spine.activeTextureUnitIndex
                : VulkanicAPI.GL_TEXTURE0;
            case BLEND_EQUATION_RGB -> pendingBlendEquation;
            case BLEND_EQUATION_ALPHA -> pendingBlendEquationAlpha;
            case BLEND_SRC_RGB -> pendingBlendSrcRgb;
            case BLEND_SRC_ALPHA -> pendingBlendSrcAlpha;
            case BLEND_DST_RGB -> pendingBlendDstRgb;
            case BLEND_DST_ALPHA -> pendingBlendDstAlpha;
            case DEPTH_WRITEMASK -> pendingDepthWriteMask ? 1 : 0;
            case DEPTH_FUNC -> pendingDepthFunc;
            case STENCIL_FUNC -> pendingStencilFunc;
            case STENCIL_REF -> pendingStencilRef;
            case STENCIL_VALUE_MASK -> pendingStencilMask;
            case STENCIL_FAIL -> pendingStencilFail;
            case STENCIL_PASS_DEPTH_FAIL -> pendingStencilDpFail;
            case STENCIL_PASS_DEPTH_PASS -> pendingStencilDpPass;
            case STENCIL_WRITEMASK -> pendingStencilWriteMask;
            case CULL_FACE_MODE -> pendingCullFaceMode;
            case POLYGON_MODE -> pendingPolygonMode;
            case MAX_TEXTURE_SIZE -> spine != null ? spine.maxImageDimension2D : 16384;
            case MAX_TEXTURE_IMAGE_UNITS -> spine != null ? spine.maxTextureImageUnits : 32;
            case MAX_COLOR_ATTACHMENTS -> 16;
            case MAX_DRAW_BUFFERS -> 8;
            case MAX_SHADER_STORAGE_BUFFER_BINDINGS -> 8;
            case UNIFORM_BUFFER_OFFSET_ALIGNMENT -> {
                long alignment = spine != null ? spine.minUniformBufferOffsetAlignment : 256L;
                yield alignment > Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(1, (int) alignment);
            }
            case TEXTURE_BINDING_2D -> spine != null
                ? spine.legacyTexture2DBindingsByUnit.getOrDefault(spine.activeTextureUnitIndex, 0)
                : 0;
            case FRAMEBUFFER_BINDING -> boundDrawFbo;
            case NUM_EXTENSIONS -> 0;
            case MAX_LABEL_LENGTH -> 256;
            case MAX_TEXTURE_LOD_BIAS -> 0;
            case TEXTURE_MAX_LEVEL -> 0;
            case GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX -> 0;
        };
    }

    private static String decodeShaderSource(long pointerBufferAddress, int stringCount, long lengthsPointer) {
        if (pointerBufferAddress == 0L || stringCount <= 0) {
            return "";
        }

        org.lwjgl.PointerBuffer sourcePointers = MemoryUtil.memPointerBuffer(pointerBufferAddress, stringCount);
        java.nio.IntBuffer lengths = lengthsPointer != 0L
            ? MemoryUtil.memIntBuffer(lengthsPointer, stringCount)
            : null;

        StringBuilder sourceBuilder = new StringBuilder();
        for (int i = 0; i < stringCount; i++) {
            long sourceAddress = sourcePointers.get(i);
            if (sourceAddress == 0L) {
                continue;
            }

            int sourceLength = lengths == null ? -1 : lengths.get(i);
            if (sourceLength >= 0) {
                java.nio.ByteBuffer sourceBuffer = MemoryUtil.memByteBuffer(sourceAddress, sourceLength);
                byte[] sourceBytes = new byte[sourceLength];
                sourceBuffer.get(sourceBytes);
                sourceBuilder.append(new String(sourceBytes, java.nio.charset.StandardCharsets.UTF_8));
            } else {
                sourceBuilder.append(MemoryUtil.memUTF8(sourceAddress));
            }
        }

        return sourceBuilder.toString();
    }

    private static final class VirtualShader {
        private final VulkanicShaderStage stage;
        private volatile String source;
        private volatile VulkanicSpirvModule compiledModule;
        private volatile long nativeShaderModuleHandle = VK10.VK_NULL_HANDLE;
        private volatile boolean compileStatus;
        private volatile boolean deletionPending;
        private volatile String infoLog = "";

        private VirtualShader(VulkanicShaderStage stage) {
            this.stage = stage;
        }
    }

    private static final class VirtualProgram {
        private final Set<Integer> attachedShaderIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
        private final Map<String, Integer> attributeLocationsByName = new ConcurrentHashMap<>();
        private final Map<String, Integer> uniformLocationTokensByName = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<Integer, Integer> samplerUniformValuesByIndex = new ConcurrentHashMap<>();
        private volatile List<String> activeUniformNames = List.of();
        private volatile List<ReflectedUniform> activeUniforms = List.of();
        private volatile List<String> activeUniformBlocks = List.of();
        private volatile List<ReflectedResourceBinding> activeResourceBindings = List.of();
        private volatile List<String> standaloneUniformDeclarations = List.of();
        private volatile List<VulkanicSpirvModule> linkedSpirvModules = List.of();
        private volatile Map<Integer, StandaloneUniformField> standaloneFieldsByLocation = Map.of();
        private volatile int standaloneBackingSize;
        @Nullable
        private volatile java.nio.ByteBuffer standaloneBackingData;
        @Nullable
        private volatile VulkanBuffer standaloneGpuBuffer;
        private volatile boolean standaloneDirty;
        private volatile boolean linkStatus;
        private volatile String infoLog = "";

        private void closeStandaloneUniformBacking() {
            VulkanBuffer buffer = standaloneGpuBuffer;
            standaloneGpuBuffer = null;
            if (buffer != null && !buffer.isClosed()) {
                buffer.close();
            }
        }
    }

    private static final class VirtualVaoState {
        private final ConcurrentHashMap<Integer, LegacyVertexAttribute> attributes = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<Integer, LegacyVertexBinding> bindings = new ConcurrentHashMap<>();
        private final Set<Integer> enabledAttributes = ConcurrentHashMap.newKeySet();

        private void enableAttribute(int index) {
            if (index < 0) {
                throw new IllegalArgumentException("attribute index must be >= 0");
            }
            enabledAttributes.add(index);
        }

        private void disableAttribute(int index) {
            enabledAttributes.remove(index);
        }

        private void setAttributePointer(int index, int size, int type, boolean normalized, boolean integer, int stride, long pointer, int buffer) {
            int effectiveStride = stride > 0 ? stride : attributeByteSize(size, type);
            attributes.put(index, new LegacyVertexAttribute(index, index, size, type, normalized, integer, Math.toIntExact(pointer), 0));
            bindings.put(index, new LegacyVertexBinding(index, effectiveStride, 0, 0, buffer));
        }

        private void setAttributeFormat(int index, int size, int type, boolean normalized, boolean integer, int relativeOffset) {
            LegacyVertexAttribute previous = attributes.get(index);
            int binding = previous != null ? previous.binding() : 0;
            int divisor = previous != null ? previous.divisor() : 0;
            attributes.put(index, new LegacyVertexAttribute(index, binding, size, type, normalized, integer, relativeOffset, divisor));
        }

        private void setAttributeBinding(int index, int binding) {
            if (binding < 0) {
                throw new IllegalArgumentException("attribute binding must be >= 0");
            }
            LegacyVertexAttribute previous = attributes.get(index);
            if (previous == null) {
                attributes.put(index, new LegacyVertexAttribute(index, binding, 4, VulkanicAPI.GL_FLOAT, false, false, 0, 0));
                return;
            }
            attributes.put(index, new LegacyVertexAttribute(
                previous.index(),
                binding,
                previous.size(),
                previous.type(),
                previous.normalized(),
                previous.integer(),
                previous.offset(),
                previous.divisor()
            ));
        }

        private void setBinding(int binding, int stride, long offset) {
            if (binding < 0) {
                throw new IllegalArgumentException("binding index must be >= 0");
            }
            if (stride < 0) {
                throw new IllegalArgumentException("binding stride must be >= 0");
            }
            LegacyVertexBinding previous = bindings.get(binding);
            int divisor = previous != null ? previous.divisor() : 0;
            int buffer = previous != null ? previous.buffer() : 0;
            bindings.put(binding, new LegacyVertexBinding(binding, stride, offset, divisor, buffer));
        }

        private void setBinding(int binding, int stride, long offset, int buffer) {
            if (binding < 0) {
                throw new IllegalArgumentException("binding index must be >= 0");
            }
            if (stride < 0) {
                throw new IllegalArgumentException("binding stride must be >= 0");
            }
            LegacyVertexBinding previous = bindings.get(binding);
            int divisor = previous != null ? previous.divisor() : 0;
            bindings.put(binding, new LegacyVertexBinding(binding, stride, offset, divisor, buffer));
        }

        private void setDivisor(int index, int divisor) {
            LegacyVertexAttribute previous = attributes.get(index);
            if (previous == null) {
                attributes.put(index, new LegacyVertexAttribute(index, 0, 4, VulkanicAPI.GL_FLOAT, false, false, 0, divisor));
                return;
            }
            attributes.put(index, new LegacyVertexAttribute(
                previous.index(),
                previous.binding(),
                previous.size(),
                previous.type(),
                previous.normalized(),
                previous.integer(),
                previous.offset(),
                divisor
            ));
            LegacyVertexBinding previousBinding = bindings.get(previous.binding());
            if (previousBinding != null) {
                bindings.put(previous.binding(), new LegacyVertexBinding(
                    previousBinding.binding(),
                    previousBinding.stride(),
                    previousBinding.offset(),
                    divisor,
                    previousBinding.buffer()
                ));
            }
        }
    }

    private record LegacyVertexAttribute(
        int index,
        int binding,
        int size,
        int type,
        boolean normalized,
        boolean integer,
        int offset,
        int divisor
    ) {
    }

    private record LegacyVertexBinding(int binding, int stride, long offset, int divisor, int buffer) {
    }

    private record ReflectedUniform(String name, int arraySize, int legacyType) {
    }

    private record ReflectedResourceRequest(String name, PipelineDescriptor.ResourceType type) {
    }

    private record ReflectedResourceBinding(String name, PipelineDescriptor.ResourceType type, int set, int binding) {
    }

    private record ExplicitDescriptorBinding(int set, int binding) {
    }

    private record DescriptorSlot(int set, int binding) {
    }

    private record StandaloneUniformField(
        String name,
        net.vulkanic.VulkanicUniformReflectionType type,
        int[] offsets,
        int arraySize,
        int stride
    ) {
    }

    private record UniformLocationRef(int programId, int uniformIndex) {
    }

    private static final class NativeSpine {
        private record DescriptorSamplerKey(
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

        private sealed interface ResolvedDescriptorBinding permits ResolvedSamplerDescriptorBinding,
            ResolvedUniformBufferDescriptorBinding, ResolvedTexelBufferDescriptorBinding {

            int bindingIndex();

            int descriptorType();

            DescriptorBindingCacheKey cacheKey();

            void populateWrite(VkWriteDescriptorSet write, MemoryStack stack);
        }

        private record DescriptorBindingCacheKey(
            int bindingIndex,
            int descriptorType,
            long primaryHandle,
            long secondaryHandle,
            long tertiaryHandle,
            long quaternaryHandle
        ) {
        }

        private record DescriptorSetCacheKey(
            long descriptorSetLayoutHandle,
            List<DescriptorBindingCacheKey> bindings
        ) {
            private DescriptorSetCacheKey {
                bindings = List.copyOf(bindings);
            }
        }

        private record DescriptorWritePlan(
            List<ResolvedDescriptorBinding> bindings,
            DescriptorSetCacheKey cacheKey
        ) {
            private DescriptorWritePlan {
                bindings = List.copyOf(bindings);
            }

            private boolean cacheable() {
                return cacheKey != null;
            }
        }

        private static final class DescriptorValidationException extends IllegalStateException {
            private DescriptorValidationException(String message) {
                super(message);
            }
        }

        private record ResolvedUniformBufferDescriptorPlan(
            ResolvedUniformBufferDescriptorBinding binding,
            boolean cacheable
        ) {
        }

        private record ResolvedSamplerDescriptorBinding(
            int bindingIndex,
            int descriptorType,
            long samplerHandle,
            long imageViewHandle,
            int imageLayout
        ) implements ResolvedDescriptorBinding {

            @Override
            public DescriptorBindingCacheKey cacheKey() {
                return new DescriptorBindingCacheKey(
                    bindingIndex,
                    descriptorType,
                    samplerHandle,
                    imageViewHandle,
                    imageLayout,
                    0L
                );
            }

            @Override
            public void populateWrite(VkWriteDescriptorSet write, MemoryStack stack) {
                VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack);
                imageInfo.get(0)
                    .sampler(samplerHandle)
                    .imageView(imageViewHandle)
                    .imageLayout(imageLayout);
                write.pImageInfo(imageInfo);
            }
        }

        private record ResolvedUniformBufferDescriptorBinding(
            int bindingIndex,
            int descriptorType,
            long bufferHandle,
            long offset,
            long range
        ) implements ResolvedDescriptorBinding {

            @Override
            public DescriptorBindingCacheKey cacheKey() {
                return new DescriptorBindingCacheKey(
                    bindingIndex,
                    descriptorType,
                    bufferHandle,
                    offset,
                    range,
                    0L
                );
            }

            @Override
            public void populateWrite(VkWriteDescriptorSet write, MemoryStack stack) {
                VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.calloc(1, stack);
                bufferInfo.get(0)
                    .buffer(bufferHandle)
                    .offset(offset)
                    .range(range);
                write.pBufferInfo(bufferInfo);
            }
        }

        private record ResolvedTexelBufferDescriptorBinding(
            int bindingIndex,
            int descriptorType,
            long bufferViewHandle
        ) implements ResolvedDescriptorBinding {

            @Override
            public DescriptorBindingCacheKey cacheKey() {
                return new DescriptorBindingCacheKey(
                    bindingIndex,
                    descriptorType,
                    bufferViewHandle,
                    0L,
                    0L,
                    0L
                );
            }

            @Override
            public void populateWrite(VkWriteDescriptorSet write, MemoryStack stack) {
                write.pTexelBufferView(stack.longs(bufferViewHandle));
            }
        }

        private record BoundDescriptorSetState(long pipelineHandle, long descriptorSetHandle) {
        }

        private static final int MAX_FRAMES_IN_FLIGHT = 2;
        private static final int IMMEDIATE_SUBMIT_SLOTS = 3;

        private final VulkanBackend backend;

        private VkInstance instance;
        private VkPhysicalDevice physicalDevice;
        private VkDevice logicalDevice;
        private VkQueue graphicsQueue;
        private VkQueue presentQueue;

        private long surface;
        private long swapchain;
        private long commandPool;
        private final long[] immediateCommandPools = new long[IMMEDIATE_SUBMIT_SLOTS];
        private final long[] frameCommandPools = new long[MAX_FRAMES_IN_FLIGHT];
        private long descriptorPool;
        private final long[] immediateDescriptorPools = new long[IMMEDIATE_SUBMIT_SLOTS];
        private long defaultDescriptorSampler;
        private final long[] swapchainImageAvailableSemaphores = new long[MAX_FRAMES_IN_FLIGHT];
        private final long[] swapchainFrameFences = new long[MAX_FRAMES_IN_FLIGHT];
        private long[] swapchainRenderFinishedSemaphoresByImage = new long[0];
        private long[] swapchainImagesInFlight = new long[0];
        private int currentFrameSyncIndex;
        private long immediateSubmitFence;
        private final long[] immediateSubmitFences = new long[IMMEDIATE_SUBMIT_SLOTS];
        private final boolean[] immediateSubmitSlotsInFlight = new boolean[IMMEDIATE_SUBMIT_SLOTS];
        private int currentImmediateSubmitSlot;
        private int recordingImmediateSubmitSlot = -1;
        private long minUniformBufferOffsetAlignment = 1L;
        private int maxImageDimension2D = 16384;
        private int maxTextureImageUnits = 32;

        private final Map<Long, Long> managedBufferAllocations = new ConcurrentHashMap<>();
        private final AtomicInteger nextLegacyBufferId = new AtomicInteger(1);
        private final Map<Integer, LegacyBufferObject> legacyBuffers = new ConcurrentHashMap<>();
        private final Map<Integer, Integer> legacyBufferBindings = new ConcurrentHashMap<>();
        private final Map<Integer, VulkanicBuffer.MappedView> legacyBufferMappedViews = new ConcurrentHashMap<>();
        private final AtomicInteger nextLegacyTextureId = new AtomicInteger(1);
        private final Map<Integer, LegacyTextureObject> legacyTextures = new ConcurrentHashMap<>();
        private final VulkanImageStateTracker imageStateTracker = new VulkanImageStateTracker();
        private final Map<Integer, Integer> legacyTexture2DBindingsByUnit = new ConcurrentHashMap<>();
        private final Map<Integer, LegacyTexelBufferBinding> legacyTexelBufferBindingsByTextureId = new ConcurrentHashMap<>();
        private final Map<Integer, TextureLevelInfo> proxyTexture2DLevels = new ConcurrentHashMap<>();
        private final Map<DescriptorSamplerKey, Long> descriptorSamplerCache = new ConcurrentHashMap<>();
        private final Map<DescriptorSetCacheKey, Long> descriptorSetCache = new HashMap<>();
        private final Map<Long, Long> lastBoundGraphicsPipelineByCommandBuffer = new HashMap<>();
        private final Map<Long, BoundDescriptorSetState> lastBoundDescriptorSetByCommandBuffer = new HashMap<>();
        private final Set<Long> managedShaderModules = ConcurrentHashMap.newKeySet();
        private final Set<Long> transientRenderPassHandles = ConcurrentHashMap.newKeySet();
        private final List<Set<Long>> transientRenderPassHandlesByImmediateSlot = createTransientHandleBuckets(IMMEDIATE_SUBMIT_SLOTS);
        /** Permanently-cached VkRenderPass handles keyed by typed attachment compatibility state. */
        private final Map<VulkanRenderPassKey, Long> permanentRenderPassCache = new HashMap<>();
        private final Set<Long> transientFramebufferHandles = ConcurrentHashMap.newKeySet();
        private final List<Set<Long>> transientFramebufferHandlesByImmediateSlot = createTransientHandleBuckets(IMMEDIATE_SUBMIT_SLOTS);
        private final List<StagingBuffer> transientStagingBuffers = Collections.synchronizedList(new ArrayList<>());
        private final List<List<StagingBuffer>> transientStagingBuffersByImmediateSlot = createTransientListBuckets(IMMEDIATE_SUBMIT_SLOTS);
        private final List<VulkanBuffer> transientDescriptorBuffers = Collections.synchronizedList(new ArrayList<>());
        private final List<List<VulkanBuffer>> transientDescriptorBuffersByImmediateSlot = createTransientListBuckets(IMMEDIATE_SUBMIT_SLOTS);
        private final List<List<VulkanBuffer>> transientFrameDescriptorBuffers = createTransientDescriptorBufferBuckets();
        private final Map<Integer, Deque<VulkanBuffer>> recycledDescriptorUniformBuffers = new HashMap<>();
        private int recycledDescriptorUniformBufferCount;
        private static final int MAX_RECYCLED_DESCRIPTOR_UNIFORM_BUFFERS = 4096;
        private final Map<Long, Long> submittedWorkGenerationByFence = new ConcurrentHashMap<>();
        private final List<PendingVulkanResourceDestroy> pendingVulkanResourceDestroys = Collections.synchronizedList(new ArrayList<>());
        private final long[] reservedFrameWorkGenerations = new long[MAX_FRAMES_IN_FLIGHT];
        private final long[] reservedImmediateWorkGenerations = new long[IMMEDIATE_SUBMIT_SLOTS];
        private long submittedWorkGeneration;
        private long completedWorkGeneration;
    /** Tracks {@code VkPipeline} handles owned by live {@link VulkanPipelineHandle} objects. */
    private final Set<Long> managedVkPipelineHandles = ConcurrentHashMap.newKeySet();
    /** Tracks {@code VkPipelineLayout} handles owned by live {@link VulkanPipelineHandle} objects. */
    private final Set<Long> managedVkPipelineLayoutHandles = ConcurrentHashMap.newKeySet();
    /** Tracks {@code VkDescriptorSetLayout} handles owned by live {@link VulkanPipelineHandle} objects. */
    private final Set<Long> managedVkDescriptorSetLayoutHandles = ConcurrentHashMap.newKeySet();


        /** Maps {@code VkImage} handle → {@code VkDeviceMemory} handle for all live managed textures. */
        private final Map<Long, Long> managedImageAllocations = new ConcurrentHashMap<>();
        /** Maps {@code VkImage} handle → default {@code VkImageView} handle (covering all mip levels). */
        private final Map<Long, Long> managedImageDefaultViews = new ConcurrentHashMap<>();
        /** Tracks extra {@code VkImageView} handles created by {@code createManagedTextureView}. */
        private final Set<Long> managedExtraImageViews = ConcurrentHashMap.newKeySet();

        private int swapchainImageFormat = VK10.VK_FORMAT_UNDEFINED;
        private int swapchainColorSpace = -1;
        private int swapchainPresentMode = -1;
        private int swapchainImageCount = 0;
        private int swapchainWidth = 0;
        private int swapchainHeight = 0;
        private final List<Long> swapchainImageHandles = new ArrayList<>();
        private final List<Long> swapchainImageViewHandles = new ArrayList<>();
        private final List<Long> swapchainPresentFramebufferHandles = new ArrayList<>();
        private final List<Integer> swapchainImageLayouts = new ArrayList<>();
        private long swapchainPresentRenderPass = VK10.VK_NULL_HANDLE;
        private VulkanPipelineHandle swapchainPresentComposePipeline;
        private PipelineDescriptor swapchainPresentComposeDescriptor;
        private int swapchainPresentComposePipelineFormat = VK10.VK_FORMAT_UNDEFINED;

        private VkCommandBuffer primaryCommandBuffer;
        private final VkCommandBuffer[] immediateCommandBuffers = new VkCommandBuffer[IMMEDIATE_SUBMIT_SLOTS];
    private final VkCommandBuffer[] frameCommandBuffers = new VkCommandBuffer[MAX_FRAMES_IN_FLIGHT];
        private int graphicsQueueFamilyIndex;
        private int graphicsQueueFamilyQueueCount = 1;
        private int physicalDeviceVendorId;
        private int physicalDeviceApiVersion = VK10.VK_API_VERSION_1_0;
        private String physicalDeviceName = "Vulkan GPU";
        private boolean fillModeNonSolidSupported;
        private boolean fillModeNonSolidEnabled;
        private boolean warnedWireframeFallback;
        private boolean instanceProperties2ExtensionEnabled;
        private boolean presentIdExtensionEnabled;
        private boolean presentWaitExtensionEnabled;
        private boolean attachmentFeedbackLoopLayoutEnabled;
        private long nextPresentId = 1L;
        private long windowHandle;
        private boolean commandBufferRecording;
        private final boolean[] frameCommandBufferRecording = new boolean[MAX_FRAMES_IN_FLIGHT];
        private boolean renderPassRecording;
        private final java.util.ArrayList<LegacyTextureObject> activeRenderPassColorTextures = new java.util.ArrayList<>();
        private final java.util.ArrayList<Integer> activeRenderPassColorFinalLayouts = new java.util.ArrayList<>();
        @Nullable private LegacyTextureObject activeRenderPassDepthTexture;
        private int activeRenderPassDepthFinalLayout = VK10.VK_IMAGE_LAYOUT_UNDEFINED;
        @Nullable private VulkanRenderPassCompatibilityKey activeRenderPassCompatibilityKey;
        private boolean frameInProgress;
        private int acquiredSwapchainImageIndex = -1;
        private int renderPassSwapchainImageIndex = -1;
        private int activeRenderPassWidth;
        private int activeRenderPassHeight;
        private boolean activeRenderPassTargetsSwapchain;
        private boolean scissorTestEnabled;
        private boolean hasCachedScissorRect;
        private int cachedScissorX;
        private int cachedScissorY;
        private int cachedScissorWidth;
        private int cachedScissorHeight;
        private volatile PendingPresentTextureRequest pendingPresentTextureRequest;
        private boolean immediateSubmitInFlight;
        private int activeTextureUnitIndex;
        private int consecutiveAcquireTimeouts;
        private long lastAcquireTimeoutLogNanos;
        private int consecutiveFrameFenceTimeouts;
        private long lastFrameFenceTimeoutLogNanos;
        private int consecutivePrimaryCommandFenceTimeouts;
        private long lastPrimaryCommandFenceTimeoutLogNanos;
        private int consecutiveImmediateSubmitTimeouts;
        private long lastImmediateSubmitTimeoutLogNanos;
        private int successfulFrameAcquireCount;
        private int successfulFramePresentCount;
        private int debugColorAttachmentLogCount;
        private int debugLegacyDrawLogCount;

        private int debugDescriptorSamplerViewMismatchLogCount;
        private int debugSodiumChunkDescriptorSamplerLogCount;
        private int debugParticleDescriptorSamplerLogCount;
        private int debugDescriptorUboLogCount;
        private int debugComparisonSamplerFallbackLogCount;
        private long descriptorSetCacheHitCount;
        private long descriptorSetCacheStoreCount;
        private long skippedRedundantPipelineBindCount;
        private long skippedRedundantDescriptorSetBindCount;

        private final PixelStoreState pixelStoreState = new PixelStoreState();

        private static final boolean DEBUG_CLEAR_SWAPCHAIN_PRESENT_EXPERIMENT = false;
        private static final boolean DEBUG_WAIT_FOR_PRESENT_COMPLETION_EXPERIMENT = false;
        private static final boolean DEBUG_WAIT_FOR_PRESENT_QUEUE_IDLE_EXPERIMENT = false;
        private static final boolean DEBUG_FORCE_FIFO_PRESENT_MODE_EXPERIMENT = true;
        private static final int GL_MAP_READ_BIT = 0x0001;
        private static final long SWAPCHAIN_ACQUIRE_TIMEOUT_NANOS = 16_000_000L;
        private static final long SWAPCHAIN_FRAME_FENCE_WAIT_TIMEOUT_NANOS = 16_000_000L;
        private static final long PRIMARY_COMMAND_FENCE_WAIT_TIMEOUT_NANOS = Long.MAX_VALUE;
        private static final long IMMEDIATE_SUBMIT_FENCE_WAIT_TIMEOUT_NANOS = Long.MAX_VALUE;
        private static final long SWAPCHAIN_PRESENT_WAIT_POLL_TIMEOUT_NANOS = 5_000_000L;
        private static final int ACQUIRE_TIMEOUTS_BEFORE_SWAPCHAIN_RECREATE = 180;
        private static final int FRAME_FENCE_TIMEOUTS_BEFORE_SWAPCHAIN_RECREATE = 180;
        private static final int PRIMARY_COMMAND_TIMEOUTS_BEFORE_SWAPCHAIN_RECREATE = 180;
        private static final long ACQUIRE_TIMEOUT_LOG_INTERVAL_NANOS = 5_000_000_000L;

        private static List<List<VulkanBuffer>> createTransientDescriptorBufferBuckets() {
            List<List<VulkanBuffer>> buckets = new ArrayList<>(MAX_FRAMES_IN_FLIGHT);
            for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
                buckets.add(Collections.synchronizedList(new ArrayList<>()));
            }
            return buckets;
        }

        private static List<Set<Long>> createTransientHandleBuckets(int bucketCount) {
            List<Set<Long>> buckets = new ArrayList<>(bucketCount);
            for (int i = 0; i < bucketCount; i++) {
                buckets.add(ConcurrentHashMap.newKeySet());
            }
            return buckets;
        }

        private static <T> List<List<T>> createTransientListBuckets(int bucketCount) {
            List<List<T>> buckets = new ArrayList<>(bucketCount);
            for (int i = 0; i < bucketCount; i++) {
                buckets.add(Collections.synchronizedList(new ArrayList<>()));
            }
            return buckets;
        }

        private NativeSpine(VulkanBackend backend) {
            this.backend = Objects.requireNonNull(backend, "backend must not be null");
        }

        private static final class LegacyBufferObject {
            private final int id;
            private volatile VulkanBuffer buffer;
            private volatile int logicalSizeBytes;
            private volatile int lastTarget;

            private LegacyBufferObject(int id) {
                this.id = id;
                this.lastTarget = VulkanicAPI.GL_ARRAY_BUFFER;
            }
        }

        private static final class TextureLevelInfo {
            private final int width;
            private final int height;
            private final int internalFormat;

            private TextureLevelInfo(int width, int height, int internalFormat) {
                this.width = width;
                this.height = height;
                this.internalFormat = internalFormat;
            }
        }

        private static final class PixelStoreState {
            private int unpackRowLength;
            private int unpackSkipRows;
            private int unpackSkipPixels;
            private int unpackAlignment = 4;
        }

        private static final class SwapchainImageResources {
            private final List<Long> imageHandles;
            private final List<Long> imageViewHandles;

            private SwapchainImageResources(List<Long> imageHandles, List<Long> imageViewHandles) {
                this.imageHandles = imageHandles;
                this.imageViewHandles = imageViewHandles;
            }
        }

        private static final class LegacyTextureObject {
            private final int id;
            private volatile int target;
            private final Map<Integer, Integer> integerParameters = new ConcurrentHashMap<>();
            private final Map<Integer, TextureLevelInfo> levels = new ConcurrentHashMap<>();
            private final Map<Integer, Integer> levelLayouts = new ConcurrentHashMap<>();
            private final Set<Long> managedViewHandles = ConcurrentHashMap.newKeySet();

            private volatile long imageHandle;
            private volatile long memoryHandle;
            private volatile long defaultViewHandle;
            private volatile int vkFormat = VK10.VK_FORMAT_UNDEFINED;
            private volatile int aspectMask = VK10.VK_IMAGE_ASPECT_COLOR_BIT;
            private volatile boolean feedbackLoopCapable;
            private volatile int pixelBytes;
            private volatile int currentLayout = VK10.VK_IMAGE_LAYOUT_UNDEFINED;
            private volatile int mipLevels = 1;
            private volatile int width;
            private volatile int height;
            private volatile int sourceFormat;
            private volatile int sourceType;

            private LegacyTextureObject(int id, int target) {
                this.id = id;
                this.target = target;
            }
        }

        private static final class LegacyTexelBufferBinding {
            private final int internalFormat;
            private final int legacyBufferId;
            private final long vkBufferViewHandle;

            private LegacyTexelBufferBinding(int internalFormat, int legacyBufferId, long vkBufferViewHandle) {
                this.internalFormat = internalFormat;
                this.legacyBufferId = legacyBufferId;
                this.vkBufferViewHandle = vkBufferViewHandle;
            }
        }

        private static final class PendingPresentTextureRequest {
            private final int legacyTextureHandle;
            private final int mipLevel;
            private final int width;
            private final int height;

            private PendingPresentTextureRequest(int legacyTextureHandle, int mipLevel, int width, int height) {
                this.legacyTextureHandle = legacyTextureHandle;
                this.mipLevel = mipLevel;
                this.width = width;
                this.height = height;
            }
        }

        private static final class PresentCompletionSupport {
            private final boolean presentId;
            private final boolean presentWait;

            private PresentCompletionSupport(boolean presentId, boolean presentWait) {
                this.presentId = presentId;
                this.presentWait = presentWait;
            }
        }

        private static NativeSpine create(VulkanBackend backend) {
            NativeSpine spine = new NativeSpine(backend);
            try {
                spine.initialize();
                return spine;
            } catch (Throwable throwable) {
                try {
                    spine.close();
                } catch (Throwable closeFailure) {
                    throwable.addSuppressed(closeFailure);
                }
                throw throwable;
            }
        }

        private void initialize() {
            String startupPhase = "resolveWindowHandle";
            try {
                long registeredWindowHandle = net.vulkanic.VulkanicAPI.getRegisteredGlfwWindowHandleForVulkanSurface();
                long currentContextWindowHandle = GLFW.glfwGetCurrentContext();
                windowHandle = registeredWindowHandle != 0L ? registeredWindowHandle : currentContextWindowHandle;
                if (windowHandle == 0L) {
                    throw new IllegalStateException(
                        "No current or registered GLFW window handle. Vulkan native spine requires a valid GLFW window for surface/swapchain bring-up.");
                }

                if (registeredWindowHandle != 0L && currentContextWindowHandle != 0L && registeredWindowHandle != currentContextWindowHandle) {
                    LOGGER.info(
                        "Using registered GLFW main window handle 0x{} for Vulkan surface (current context window is 0x{}).",
                        Long.toHexString(registeredWindowHandle),
                        Long.toHexString(currentContextWindowHandle)
                    );
                }

                startupPhase = "createInstance";
                createInstance();
                startupPhase = "createSurface";
                createSurface();
                startupPhase = "pickPhysicalDeviceAndQueueFamily";
                pickPhysicalDeviceAndQueueFamily();
                startupPhase = "createLogicalDeviceAndQueue";
                createLogicalDeviceAndQueue();
                startupPhase = "createSharedDescriptorResources";
                createSharedDescriptorResources();
                startupPhase = "createSwapchain";
                createSwapchain();
                startupPhase = "createCommandPoolAndPrimaryBuffer";
                createCommandPoolAndPrimaryBuffer();
            } catch (Throwable throwable) {
                throw new IllegalStateException("Failed to initialize Vulkan native spine during phase " + startupPhase, throwable);
            }
        }

        private void createInstance() {
            try (MemoryStack stack = stackPush()) {
                org.lwjgl.PointerBuffer requiredExtensions = GLFWVulkan.glfwGetRequiredInstanceExtensions();
                if (requiredExtensions == null || requiredExtensions.remaining() == 0) {
                    throw new IllegalStateException(
                        "GLFW did not provide Vulkan required instance extensions (null/empty result).");
                }

                Set<String> availableInstanceExtensions = enumerateInstanceExtensionNames();
                boolean enableProperties2Extension = availableInstanceExtensions.contains(
                    KHRGetPhysicalDeviceProperties2.VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME
                );

                boolean properties2AlreadyRequested = false;
                for (int index = requiredExtensions.position(); index < requiredExtensions.limit(); index++) {
                    if (KHRGetPhysicalDeviceProperties2.VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME
                        .equals(requiredExtensions.getStringUTF8(index))) {
                        properties2AlreadyRequested = true;
                        break;
                    }
                }

                org.lwjgl.PointerBuffer enabledExtensions = requiredExtensions;
                if (enableProperties2Extension && !properties2AlreadyRequested) {
                    enabledExtensions = stack.mallocPointer(requiredExtensions.remaining() + 1);
                    for (int index = requiredExtensions.position(); index < requiredExtensions.limit(); index++) {
                        enabledExtensions.put(requiredExtensions.get(index));
                    }
                    enabledExtensions.put(stack.UTF8(KHRGetPhysicalDeviceProperties2.VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME));
                    enabledExtensions.flip();
                }

                StringBuilder requiredExtensionSummary = new StringBuilder();
                for (int index = enabledExtensions.position(); index < enabledExtensions.limit(); index++) {
                    if (requiredExtensionSummary.length() > 0) {
                        requiredExtensionSummary.append(", ");
                    }
                    requiredExtensionSummary.append(enabledExtensions.getStringUTF8(index));
                }
                LOGGER.info("GLFW required Vulkan instance extensions: [{}]", requiredExtensionSummary);

                VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack)
                    .sType$Default()
                    .pApplicationName(stack.UTF8("Vulkanic"))
                    .applicationVersion(VK10.VK_MAKE_API_VERSION(0, 0, 1, 0))
                    .pEngineName(stack.UTF8("Vulkanic"))
                    .engineVersion(VK10.VK_MAKE_API_VERSION(0, 0, 1, 0))
                    // Vulkanic uses negative-height viewports for GL-style offscreen rendering.
                    // That is core in Vulkan 1.1; requesting 1.0 makes validation reject those draws.
                    .apiVersion(VK11.VK_API_VERSION_1_1);

                VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack)
                    .sType$Default()
                    .pApplicationInfo(appInfo)
                    .ppEnabledExtensionNames(enabledExtensions);

                org.lwjgl.PointerBuffer pInstance = stack.mallocPointer(1);
                checkVk("vkCreateInstance", VK10.vkCreateInstance(createInfo, null, pInstance));
                instance = new VkInstance(pInstance.get(0), createInfo);
                instanceProperties2ExtensionEnabled = enableProperties2Extension || properties2AlreadyRequested;
            }
        }

        private Set<String> enumerateInstanceExtensionNames() {
            try (MemoryStack stack = stackPush()) {
                java.nio.IntBuffer extensionCount = stack.ints(0);
                checkVk(
                    "vkEnumerateInstanceExtensionProperties(count)",
                    VK10.vkEnumerateInstanceExtensionProperties((java.nio.ByteBuffer) null, extensionCount, null)
                );

                int count = extensionCount.get(0);
                if (count <= 0) {
                    return Collections.emptySet();
                }

                VkExtensionProperties.Buffer extensionProperties = VkExtensionProperties.malloc(count, stack);
                checkVk(
                    "vkEnumerateInstanceExtensionProperties(list)",
                    VK10.vkEnumerateInstanceExtensionProperties((java.nio.ByteBuffer) null, extensionCount, extensionProperties)
                );

                Set<String> extensionNames = new HashSet<>();
                for (int extensionIndex = 0; extensionIndex < extensionCount.get(0); extensionIndex++) {
                    extensionNames.add(extensionProperties.get(extensionIndex).extensionNameString());
                }
                return extensionNames;
            }
        }

        private void createSurface() {
            try (MemoryStack stack = stackPush()) {
                java.nio.LongBuffer pSurface = stack.mallocLong(1);
                checkVk("glfwCreateWindowSurface",
                    GLFWVulkan.glfwCreateWindowSurface(instance, windowHandle, null, pSurface));
                surface = pSurface.get(0);
            }
        }

        private void pickPhysicalDeviceAndQueueFamily() {
            try (MemoryStack stack = stackPush()) {
                java.nio.IntBuffer count = stack.ints(0);
                checkVk("vkEnumeratePhysicalDevices(count)",
                    VK10.vkEnumeratePhysicalDevices(instance, count, null));

                int deviceCount = count.get(0);
                if (deviceCount <= 0) {
                    throw new IllegalStateException("No Vulkan physical devices were found.");
                }

                org.lwjgl.PointerBuffer physicalDevices = stack.mallocPointer(deviceCount);
                checkVk("vkEnumeratePhysicalDevices(list)",
                    VK10.vkEnumeratePhysicalDevices(instance, count, physicalDevices));

                for (int index = 0; index < deviceCount; index++) {
                    VkPhysicalDevice candidate = new VkPhysicalDevice(physicalDevices.get(index), instance);
                    OptionalInt queueFamily = findGraphicsPresentQueueFamily(candidate);
                    if (queueFamily.isPresent()) {
                        physicalDevice = candidate;
                        graphicsQueueFamilyIndex = queueFamily.getAsInt();
                        capturePhysicalDeviceProperties(candidate);
                        return;
                    }
                }

                throw new IllegalStateException(
                    "No physical device with combined graphics+present queue support for GLFW surface was found.");
            }
        }

        private void capturePhysicalDeviceProperties(VkPhysicalDevice device) {
            try (MemoryStack stack = stackPush()) {
                VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.malloc(stack);
                VK10.vkGetPhysicalDeviceProperties(device, properties);
                VkPhysicalDeviceFeatures features = VkPhysicalDeviceFeatures.malloc(stack);
                VK10.vkGetPhysicalDeviceFeatures(device, features);
                physicalDeviceVendorId = properties.vendorID();
                physicalDeviceApiVersion = properties.apiVersion();
                fillModeNonSolidSupported = features.fillModeNonSolid();
                minUniformBufferOffsetAlignment = Math.max(1L, properties.limits().minUniformBufferOffsetAlignment());
                maxImageDimension2D = Math.max(1024, properties.limits().maxImageDimension2D());
                int maxPerStageSamplers = properties.limits().maxPerStageDescriptorSamplers();
                int maxPerStageSampledImages = properties.limits().maxPerStageDescriptorSampledImages();
                int combinedImageSamplerBudget = Math.min(maxPerStageSamplers, maxPerStageSampledImages);
                maxTextureImageUnits = Math.max(16, Math.min(32, combinedImageSamplerBudget));
                String name = properties.deviceNameString();
                if (name != null && !name.isBlank()) {
                    physicalDeviceName = name;
                }
            }
        }

        private OptionalInt findGraphicsPresentQueueFamily(VkPhysicalDevice device) {
            try (MemoryStack stack = stackPush()) {
                java.nio.IntBuffer queueCount = stack.ints(0);
                VK10.vkGetPhysicalDeviceQueueFamilyProperties(device, queueCount, null);

                int count = queueCount.get(0);
                if (count <= 0) {
                    return OptionalInt.empty();
                }

                VkQueueFamilyProperties.Buffer queueFamilies = VkQueueFamilyProperties.malloc(count, stack);
                VK10.vkGetPhysicalDeviceQueueFamilyProperties(device, queueCount, queueFamilies);

                for (int familyIndex = 0; familyIndex < count; familyIndex++) {
                    VkQueueFamilyProperties properties = queueFamilies.get(familyIndex);
                    boolean graphicsSupported = (properties.queueFlags() & VK10.VK_QUEUE_GRAPHICS_BIT) != 0;
                    if (!graphicsSupported) {
                        continue;
                    }

                    java.nio.IntBuffer supported = stack.ints(VK10.VK_FALSE);
                    checkVk("vkGetPhysicalDeviceSurfaceSupportKHR",
                        KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(device, familyIndex, surface, supported));
                    if (supported.get(0) == VK10.VK_TRUE) {
                        graphicsQueueFamilyQueueCount = Math.max(1, properties.queueCount());
                        return OptionalInt.of(familyIndex);
                    }
                }

                return OptionalInt.empty();
            }
        }

        private void createLogicalDeviceAndQueue() {
            try (MemoryStack stack = stackPush()) {
                int queueCount = Math.min(2, Math.max(1, graphicsQueueFamilyQueueCount));
                java.nio.FloatBuffer priorities = stack.mallocFloat(queueCount);
                for (int queueIndex = 0; queueIndex < queueCount; queueIndex++) {
                    priorities.put(queueIndex, 1.0f);
                }

                VkDeviceQueueCreateInfo.Buffer queueCreateInfos = VkDeviceQueueCreateInfo.calloc(1, stack);
                queueCreateInfos.get(0)
                    .sType$Default()
                    .queueFamilyIndex(graphicsQueueFamilyIndex)
                    .pQueuePriorities(priorities);

                Set<String> supportedExtensions = enumerateDeviceExtensionNames(physicalDevice);
                PresentCompletionSupport presentCompletionSupport = queryPresentCompletionSupport(physicalDevice, supportedExtensions);
                boolean hasFeedbackLoopLayout = supportedExtensions.contains(
                    EXTAttachmentFeedbackLoopLayout.VK_EXT_ATTACHMENT_FEEDBACK_LOOP_LAYOUT_EXTENSION_NAME);

                int enabledExtensionCount = 1
                    + (presentCompletionSupport.presentId ? 1 : 0)
                    + (presentCompletionSupport.presentWait ? 1 : 0)
                    + (hasFeedbackLoopLayout ? 1 : 0);
                org.lwjgl.PointerBuffer enabledExtensions = stack.mallocPointer(enabledExtensionCount);
                enabledExtensions.put(stack.UTF8(KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME));
                if (presentCompletionSupport.presentId) {
                    enabledExtensions.put(stack.UTF8(KHRPresentId.VK_KHR_PRESENT_ID_EXTENSION_NAME));
                }
                if (presentCompletionSupport.presentWait) {
                    enabledExtensions.put(stack.UTF8(KHRPresentWait.VK_KHR_PRESENT_WAIT_EXTENSION_NAME));
                }
                if (hasFeedbackLoopLayout) {
                    enabledExtensions.put(stack.UTF8(EXTAttachmentFeedbackLoopLayout.VK_EXT_ATTACHMENT_FEEDBACK_LOOP_LAYOUT_EXTENSION_NAME));
                }
                enabledExtensions.flip();

                long featureChainHead = MemoryUtil.NULL;
                VkPhysicalDevicePresentIdFeaturesKHR presentIdFeatures = null;
                if (presentCompletionSupport.presentId) {
                    presentIdFeatures = VkPhysicalDevicePresentIdFeaturesKHR.calloc(stack)
                        .sType$Default()
                        .presentId(true);
                    featureChainHead = presentIdFeatures.address();
                }
                if (presentCompletionSupport.presentWait) {
                    VkPhysicalDevicePresentWaitFeaturesKHR presentWaitFeatures = VkPhysicalDevicePresentWaitFeaturesKHR.calloc(stack)
                        .sType$Default()
                        .presentWait(true);
                    if (presentIdFeatures != null) {
                        presentIdFeatures.pNext(presentWaitFeatures.address());
                    } else {
                        featureChainHead = presentWaitFeatures.address();
                    }
                }
                VkPhysicalDeviceAttachmentFeedbackLoopLayoutFeaturesEXT feedbackLoopFeatures = null;
                if (hasFeedbackLoopLayout) {
                    feedbackLoopFeatures = VkPhysicalDeviceAttachmentFeedbackLoopLayoutFeaturesEXT.calloc(stack)
                        .sType$Default()
                        .attachmentFeedbackLoopLayout(true);
                    if (featureChainHead == MemoryUtil.NULL) {
                        featureChainHead = feedbackLoopFeatures.address();
                    } else {
                        // Append by chaining onto presentId or presentWait if present, else set as head
                        // Since chain building above is linear, the last-added item has pNext=0;
                        // we re-chain by just setting featureChainHead to feedbackLoopFeatures
                        // and pointing it to the previous head.
                        feedbackLoopFeatures.pNext(featureChainHead);
                        featureChainHead = feedbackLoopFeatures.address();
                    }
                }

                VkPhysicalDeviceFeatures enabledFeatures = VkPhysicalDeviceFeatures.calloc(stack);
                if (fillModeNonSolidSupported) {
                    enabledFeatures.fillModeNonSolid(true);
                }

                VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.calloc(stack)
                    .sType$Default()
                    .pQueueCreateInfos(queueCreateInfos)
                    .ppEnabledExtensionNames(enabledExtensions)
                    .pEnabledFeatures(enabledFeatures);
                if (featureChainHead != MemoryUtil.NULL) {
                    createInfo.pNext(featureChainHead);
                }

                org.lwjgl.PointerBuffer pDevice = stack.mallocPointer(1);
                checkVk("vkCreateDevice", VK10.vkCreateDevice(physicalDevice, createInfo, null, pDevice));
                logicalDevice = new VkDevice(pDevice.get(0), physicalDevice, createInfo);
                fillModeNonSolidEnabled = fillModeNonSolidSupported;
                presentIdExtensionEnabled = presentCompletionSupport.presentId;
                presentWaitExtensionEnabled = presentCompletionSupport.presentWait;
                attachmentFeedbackLoopLayoutEnabled = hasFeedbackLoopLayout;

                org.lwjgl.PointerBuffer pQueue = stack.mallocPointer(1);
                VK10.vkGetDeviceQueue(logicalDevice, graphicsQueueFamilyIndex, 0, pQueue);
                graphicsQueue = new VkQueue(pQueue.get(0), logicalDevice);

                if (queueCount > 1) {
                    VK10.vkGetDeviceQueue(logicalDevice, graphicsQueueFamilyIndex, 1, pQueue);
                    presentQueue = new VkQueue(pQueue.get(0), logicalDevice);
                } else {
                    presentQueue = graphicsQueue;
                }

                LOGGER.info(
                    "Enabled Vulkan present completion extensions: presentId={}, presentWait={}",
                    presentIdExtensionEnabled,
                    presentWaitExtensionEnabled
                );
            }
        }

        private Set<String> enumerateDeviceExtensionNames(VkPhysicalDevice device) {
            try (MemoryStack stack = stackPush()) {
                java.nio.IntBuffer extensionCount = stack.ints(0);
                checkVk(
                    "vkEnumerateDeviceExtensionProperties(count)",
                    VK10.vkEnumerateDeviceExtensionProperties(device, (java.nio.ByteBuffer) null, extensionCount, null)
                );

                int count = extensionCount.get(0);
                if (count <= 0) {
                    return Collections.emptySet();
                }

                VkExtensionProperties.Buffer extensionProperties = VkExtensionProperties.malloc(count, stack);
                checkVk(
                    "vkEnumerateDeviceExtensionProperties(list)",
                    VK10.vkEnumerateDeviceExtensionProperties(device, (java.nio.ByteBuffer) null, extensionCount, extensionProperties)
                );

                Set<String> extensionNames = new HashSet<>();
                for (int extensionIndex = 0; extensionIndex < extensionCount.get(0); extensionIndex++) {
                    extensionNames.add(extensionProperties.get(extensionIndex).extensionNameString());
                }
                return extensionNames;
            }
        }

        private PresentCompletionSupport queryPresentCompletionSupport(VkPhysicalDevice device, Set<String> supportedExtensions) {
            // Keep present completion extensions disabled on this startup path.
            // They are optional and can destabilize acquire/present cadence on some Linux/NVIDIA stacks.
            return new PresentCompletionSupport(false, false);
        }

        private void createSharedDescriptorResources() {
            try (MemoryStack stack = stackPush()) {
                VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(3, stack);
                poolSizes.get(0)
                    .type(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(2048);
                poolSizes.get(1)
                    .type(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .descriptorCount(2048);
                poolSizes.get(2)
                    .type(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_TEXEL_BUFFER)
                    .descriptorCount(1024);

                VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(VK10.VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT)
                    .maxSets(2048)
                    .pPoolSizes(poolSizes);

                java.nio.LongBuffer pPool = stack.mallocLong(1);
                for (int slot = 0; slot < IMMEDIATE_SUBMIT_SLOTS; slot++) {
                    checkVk(
                        "vkCreateDescriptorPool(immediate[" + slot + "])",
                        VK10.vkCreateDescriptorPool(logicalDevice, poolInfo, null, pPool)
                    );
                    immediateDescriptorPools[slot] = pPool.get(0);
                }
                descriptorPool = immediateDescriptorPools[currentImmediateSubmitSlot];

                VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.calloc(stack)
                    .sType$Default()
                    .magFilter(VK10.VK_FILTER_LINEAR)
                    .minFilter(VK10.VK_FILTER_LINEAR)
                    .mipmapMode(VK10.VK_SAMPLER_MIPMAP_MODE_LINEAR)
                    .addressModeU(VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT)
                    .addressModeV(VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT)
                    .addressModeW(VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT)
                    .anisotropyEnable(false)
                    .maxAnisotropy(1.0f)
                    .compareEnable(false)
                    .compareOp(VK10.VK_COMPARE_OP_ALWAYS)
                    .minLod(0.0f)
                    .maxLod(1000.0f)
                    .borderColor(VK10.VK_BORDER_COLOR_INT_OPAQUE_BLACK)
                    .unnormalizedCoordinates(false);

                java.nio.LongBuffer pSampler = stack.mallocLong(1);
                checkVk("vkCreateSampler(default)",
                    VK10.vkCreateSampler(logicalDevice, samplerInfo, null, pSampler));
                defaultDescriptorSampler = pSampler.get(0);

                VkFenceCreateInfo frameFenceInfo = VkFenceCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(VK10.VK_FENCE_CREATE_SIGNALED_BIT);
                VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.calloc(stack).sType$Default();
                java.nio.LongBuffer pSemaphore = stack.mallocLong(1);
                java.nio.LongBuffer pFrameFence = stack.mallocLong(1);
                for (int frameIndex = 0; frameIndex < MAX_FRAMES_IN_FLIGHT; frameIndex++) {
                    checkVk(
                        "vkCreateSemaphore(swapchainImageAvailable[" + frameIndex + "])",
                        VK10.vkCreateSemaphore(logicalDevice, semaphoreInfo, null, pSemaphore)
                    );
                    swapchainImageAvailableSemaphores[frameIndex] = pSemaphore.get(0);

                    checkVk(
                        "vkCreateFence(swapchainFrame[" + frameIndex + "])",
                        VK10.vkCreateFence(logicalDevice, frameFenceInfo, null, pFrameFence)
                    );
                    swapchainFrameFences[frameIndex] = pFrameFence.get(0);
                }
                currentFrameSyncIndex = 0;

                for (int slot = 0; slot < IMMEDIATE_SUBMIT_SLOTS; slot++) {
                    checkVk(
                        "vkCreateFence(immediateSubmit[" + slot + "])",
                        VK10.vkCreateFence(logicalDevice, frameFenceInfo, null, pFrameFence)
                    );
                    immediateSubmitFences[slot] = pFrameFence.get(0);
                }
                immediateSubmitFence = immediateSubmitFences[currentImmediateSubmitSlot];
            }
        }

        private void resetSharedDescriptorPool() {
            if (descriptorPool != VK10.VK_NULL_HANDLE) {
                checkVk("vkResetDescriptorPool",
                    VK10.vkResetDescriptorPool(logicalDevice, descriptorPool, 0));
            }
            descriptorSetCache.clear();
            lastBoundGraphicsPipelineByCommandBuffer.clear();
            lastBoundDescriptorSetByCommandBuffer.clear();
        }

        private void clearTrackedCommandBufferState(long commandBufferHandle) {
            lastBoundGraphicsPipelineByCommandBuffer.remove(commandBufferHandle);
            lastBoundDescriptorSetByCommandBuffer.remove(commandBufferHandle);
        }

        private void bindDescriptorSetIfNeeded(
            long commandBufferHandle,
            VkCommandBuffer activeCommandBuffer,
            VulkanPipelineHandle pipeline,
            long descriptorSetHandle
        ) {
            BoundDescriptorSetState currentState = lastBoundDescriptorSetByCommandBuffer.get(commandBufferHandle);
            if (currentState != null
                && currentState.pipelineHandle() == pipeline.getVkPipelineHandle()
                && currentState.descriptorSetHandle() == descriptorSetHandle) {
                skippedRedundantDescriptorSetBindCount++;
                return;
            }

            try (MemoryStack stack = stackPush()) {
                VK10.vkCmdBindDescriptorSets(
                    activeCommandBuffer,
                    VK10.VK_PIPELINE_BIND_POINT_GRAPHICS,
                    pipeline.getVkPipelineLayoutHandle(),
                    0,
                    stack.longs(descriptorSetHandle),
                    null
                );
            }
            lastBoundDescriptorSetByCommandBuffer.put(
                commandBufferHandle,
                new BoundDescriptorSetState(pipeline.getVkPipelineHandle(), descriptorSetHandle)
            );
        }

        private int descriptorImageLayoutFor(@Nullable LegacyTextureObject texture) {
            if (texture == null) return VulkanImageUse.SAMPLED_COLOR.vkLayout();
            if (texture.feedbackLoopCapable) {
                return VulkanImageUse.FEEDBACK_LOOP.vkLayout();
            }
            if (texture.aspectMask == VK10.VK_IMAGE_ASPECT_DEPTH_BIT)
                return VulkanImageUse.SAMPLED_DEPTH.vkLayout();
            return VulkanImageUse.SAMPLED_COLOR.vkLayout();
        }

        private DescriptorWritePlan buildDescriptorWritePlan(
            List<PipelineDescriptor.ResourceBinding> layoutBindings,
            PipelineResourceBindings bindings,
            boolean sodiumChunkDescriptor,
            boolean particleDescriptor,
            String pipelineLocation,
            long pipelineHandle,
            long descriptorSetLayoutHandle
        ) {
            List<ResolvedDescriptorBinding> resolvedBindings = new ArrayList<>(layoutBindings.size());
            List<DescriptorBindingCacheKey> cacheKeys = new ArrayList<>(layoutBindings.size());
            boolean cacheable = true;

            for (PipelineDescriptor.ResourceBinding binding : layoutBindings) {
                PipelineDescriptor.ResourceBinding effectiveBinding = withEffectiveBindingType(binding, bindings);
                ResolvedDescriptorBinding resolvedBinding = switch (effectiveBinding.type()) {
                    case SAMPLER, COMPARISON_SAMPLER -> resolveSamplerDescriptorBinding(
                        effectiveBinding,
                        bindings,
                        sodiumChunkDescriptor,
                        particleDescriptor,
                        pipelineLocation,
                        pipelineHandle
                    );
                    case UNIFORM_BUFFER -> {
                        ResolvedUniformBufferDescriptorPlan resolvedPlan = resolveUniformBufferDescriptorBinding(effectiveBinding, bindings);
                        if (!resolvedPlan.cacheable()) {
                            cacheable = false;
                        }
                        yield resolvedPlan.binding();
                    }
                    case TEXEL_BUFFER -> resolveTexelBufferDescriptorBinding(effectiveBinding, bindings);
                };

                resolvedBindings.add(resolvedBinding);
                if (cacheable) {
                    cacheKeys.add(resolvedBinding.cacheKey());
                }
            }

            return new DescriptorWritePlan(
                resolvedBindings,
                cacheable ? new DescriptorSetCacheKey(descriptorSetLayoutHandle, cacheKeys) : null
            );
        }

        private List<PipelineDescriptor.ResourceBinding> normalizeDescriptorLayoutBindings(
            List<PipelineDescriptor.ResourceBinding> bindings
        ) {
            return bindings;
        }

        private PipelineDescriptor.ResourceBinding withEffectiveBindingType(
            PipelineDescriptor.ResourceBinding binding,
            @Nullable PipelineResourceBindings bindings
        ) {
            return binding;
        }

        private boolean shouldLogSodiumDescriptorBinding(String name) {
            if ("Sampler0".contentEquals(name) || "Sampler2".contentEquals(name)) {
                return true;
            }
            if (!DEBUG_VULKAN_DESCRIPTOR_BIND_LOGS) {
                return false;
            }
            return name.startsWith("customtex")
                || name.startsWith("colortex")
                || name.startsWith("gaux")
                || name.startsWith("depthtex")
                || "noisetex".contentEquals(name)
                || "normals".contentEquals(name)
                || "specular".contentEquals(name);
        }

        @SuppressWarnings("resource")
        private ResolvedSamplerDescriptorBinding resolveSamplerDescriptorBinding(
            PipelineDescriptor.ResourceBinding binding,
            PipelineResourceBindings bindings,
            boolean sodiumChunkDescriptor,
            boolean particleDescriptor,
            String pipelineLocation,
            long pipelineHandle
        ) {
            PipelineResourceBindings.SamplerBinding samplerBinding = bindings.getSamplerBindingOrNull(binding.name());
            if (samplerBinding == null) {
                throw new DescriptorValidationException("Missing sampler binding for '" + binding.name() + "'");
            }

            if (!(samplerBinding.textureView() instanceof VulkanTextureView vulkanTextureView)) {
                throw new IllegalArgumentException(
                    "Sampler binding '" + binding.name() + "' requires VulkanTextureView on Vulkan backend");
            }

            VulkanTextureView resolvedTextureView = vulkanTextureView;
            LegacyTextureObject sampledLegacyTexture = tryResolveLegacyTexture(resolvedTextureView);

            boolean wantsComparisonSampler = binding.type() == PipelineDescriptor.ResourceType.COMPARISON_SAMPLER;
            boolean canUseComparisonSampler = sampledLegacyTexture != null
                && sampledLegacyTexture.aspectMask == VK10.VK_IMAGE_ASPECT_DEPTH_BIT;
            if (wantsComparisonSampler && !canUseComparisonSampler) {
                String[] depthFallbackBindings;
                if ("shadowtex0".equals(binding.name())) {
                    depthFallbackBindings = new String[]{"depthtex0", "dhDepthTex", "dhDepthTex1", "depthtex1", "depthtex2"};
                } else if ("shadowtex1".equals(binding.name())) {
                    depthFallbackBindings = new String[]{"depthtex1", "dhDepthTex1", "dhDepthTex", "depthtex0", "depthtex2"};
                } else {
                    depthFallbackBindings = new String[]{"depthtex0", "depthtex1", "depthtex2", "dhDepthTex", "dhDepthTex1"};
                }
                for (String fallbackBindingName : depthFallbackBindings) {
                    PipelineResourceBindings.SamplerBinding fallbackSamplerBinding = bindings.getSamplerBindingOrNull(fallbackBindingName);
                    if (fallbackSamplerBinding == null) {
                        continue;
                    }
                    if (!(fallbackSamplerBinding.textureView() instanceof VulkanTextureView)) {
                        continue;
                    }
                    VulkanTextureView fallbackView = (VulkanTextureView) fallbackSamplerBinding.textureView();
                    LegacyTextureObject fallbackTexture = tryResolveLegacyTexture(fallbackView);
                    if (fallbackTexture == null || fallbackTexture.aspectMask != VK10.VK_IMAGE_ASPECT_DEPTH_BIT) {
                        continue;
                    }
                    resolvedTextureView = fallbackView;
                    sampledLegacyTexture = fallbackTexture;
                    canUseComparisonSampler = true;
                    if (debugComparisonSamplerFallbackLogCount < 40) {
                        debugComparisonSamplerFallbackLogCount++;
                        LOGGER.warn(
                            "Rebinding comparison sampler '{}' to depth fallback '{}' texId={}.",
                            binding.name(),
                            fallbackBindingName,
                            fallbackTexture.id
                        );
                    }
                    break;
                }
            }

            long requestedImageViewHandle = resolvedTextureView.getVkImageViewHandle();
            long descriptorImageViewHandle = requestedImageViewHandle;
            long sampledDefaultViewHandle = sampledLegacyTexture != null
                ? sampledLegacyTexture.defaultViewHandle
                : VK10.VK_NULL_HANDLE;
            int descriptorBaseMipLevel = Math.max(0, resolvedTextureView.getBaseMipLevel());
            int descriptorMipLevelCount = Math.max(1, resolvedTextureView.getMipLevelCount());

            if (sampledLegacyTexture != null
                && sampledDefaultViewHandle != VK10.VK_NULL_HANDLE
                && resolvedTextureView.getBaseMipLevel() == 0
                && resolvedTextureView.getMipLevelCount() >= sampledLegacyTexture.mipLevels) {
                descriptorImageViewHandle = sampledDefaultViewHandle;
                descriptorBaseMipLevel = 0;
                descriptorMipLevelCount = Math.max(1, sampledLegacyTexture.mipLevels);
            }

            transitionLegacyTextureToSampleLayout(
                sampledLegacyTexture,
                descriptorBaseMipLevel,
                descriptorMipLevelCount
            );

            String sampledTextureLabel = resolvedTextureView.texture().getLabel();
            int sampledUsage = resolvedTextureView.texture().usage();

            if (sampledLegacyTexture != null
                && sampledDefaultViewHandle != VK10.VK_NULL_HANDLE
                && requestedImageViewHandle != descriptorImageViewHandle
                && debugDescriptorSamplerViewMismatchLogCount < 40) {
                debugDescriptorSamplerViewMismatchLogCount++;
                LOGGER.info(
                    "Vulkan sampler view remap#{} binding={} texId={} label={} usage=0x{} requestedView=0x{} remappedView=0x{} image=0x{} baseMip={} mipCount={} textureMipLevels={}",
                    debugDescriptorSamplerViewMismatchLogCount,
                    binding.name(),
                    sampledLegacyTexture.id,
                    sampledTextureLabel,
                    Integer.toHexString(sampledUsage),
                    Long.toHexString(requestedImageViewHandle),
                    Long.toHexString(descriptorImageViewHandle),
                    Long.toHexString(sampledLegacyTexture.imageHandle),
                    resolvedTextureView.getBaseMipLevel(),
                    resolvedTextureView.getMipLevelCount(),
                    sampledLegacyTexture.mipLevels
                );
            }

            if (wantsComparisonSampler && !canUseComparisonSampler && debugComparisonSamplerFallbackLogCount < 40) {
                debugComparisonSamplerFallbackLogCount++;
                LOGGER.warn(
                    "Downgrading comparison sampler binding '{}' to a non-comparison sampler because it is backed by non-depth texId={} vkFormat=0x{} aspectMask=0x{}.",
                    binding.name(),
                    sampledLegacyTexture != null ? sampledLegacyTexture.id : 0,
                    Integer.toHexString(sampledLegacyTexture != null ? sampledLegacyTexture.vkFormat : VK10.VK_FORMAT_UNDEFINED),
                    Integer.toHexString(sampledLegacyTexture != null ? sampledLegacyTexture.aspectMask : 0)
                );
            }
            boolean useComparisonSampler = wantsComparisonSampler && canUseComparisonSampler;

            DescriptorSamplerKey samplerKey = descriptorSamplerKey(
                resolvedTextureView,
                sampledLegacyTexture,
                samplerBinding.samplerObject(),
                samplerBinding.textureUnit(),
                binding.type() == PipelineDescriptor.ResourceType.COMPARISON_SAMPLER
                    ? useComparisonSampler
                    : binding.type() == PipelineDescriptor.ResourceType.SAMPLER ? Boolean.FALSE
                    : null
            );
            long samplerHandle = resolveDescriptorSamplerHandle(samplerKey);
            int descriptorImageLayout = descriptorImageLayoutFor(sampledLegacyTexture);
            if (sodiumChunkDescriptor
                && shouldLogSodiumDescriptorBinding(binding.name())
                && debugSodiumChunkDescriptorSamplerLogCount < 160) {
                debugSodiumChunkDescriptorSamplerLogCount++;
                int sampledLegacyId = sampledLegacyTexture != null ? sampledLegacyTexture.id : 0;
                int sampledLayout = sampledLegacyTexture != null
                    ? trackedLayoutForLevel(sampledLegacyTexture, resolvedTextureView.getBaseMipLevel())
                    : VK10.VK_IMAGE_LAYOUT_UNDEFINED;
                int sampledWidth = sampledLegacyTexture != null ? sampledLegacyTexture.width : 0;
                int sampledHeight = sampledLegacyTexture != null ? sampledLegacyTexture.height : 0;
                int sampledVkFormat = sampledLegacyTexture != null ? sampledLegacyTexture.vkFormat : VK10.VK_FORMAT_UNDEFINED;
                long sampledImageHandle = sampledLegacyTexture != null ? sampledLegacyTexture.imageHandle : VK10.VK_NULL_HANDLE;
                LOGGER.info(
                    "Sodium Vulkan descriptor write#{} pipelineLocation={} binding={} texId={} label={} usage=0x{} sampler=0x{} view=0x{} image=0x{} texExtent={}x{} vkFormat=0x{} baseMip={} mipCount={} trackedLayout=0x{} requestedView=0x{} remappedDefaultView={} pipeline=0x{}",
                    debugSodiumChunkDescriptorSamplerLogCount,
                    pipelineLocation,
                    binding.name(),
                    sampledLegacyId,
                    sampledTextureLabel,
                    Integer.toHexString(sampledUsage),
                    Long.toHexString(samplerHandle),
                    Long.toHexString(descriptorImageViewHandle),
                    Long.toHexString(sampledImageHandle),
                    sampledWidth,
                    sampledHeight,
                    Integer.toHexString(sampledVkFormat),
                    resolvedTextureView.getBaseMipLevel(),
                    resolvedTextureView.getMipLevelCount(),
                    Integer.toHexString(sampledLayout),
                    Long.toHexString(requestedImageViewHandle),
                    requestedImageViewHandle != descriptorImageViewHandle,
                    Long.toHexString(pipelineHandle)
                );
            }

            if (DEBUG_VULKAN_DESCRIPTOR_BIND_LOGS
                && particleDescriptor
                && ("Sampler0".contentEquals(binding.name()) || "Sampler2".contentEquals(binding.name()))
                && debugParticleDescriptorSamplerLogCount < 120) {
                debugParticleDescriptorSamplerLogCount++;
                int sampledLegacyId = sampledLegacyTexture != null ? sampledLegacyTexture.id : 0;
                int sampledLayout = sampledLegacyTexture != null
                    ? trackedLayoutForLevel(sampledLegacyTexture, resolvedTextureView.getBaseMipLevel())
                    : VK10.VK_IMAGE_LAYOUT_UNDEFINED;
                int sampledWidth = sampledLegacyTexture != null ? sampledLegacyTexture.width : 0;
                int sampledHeight = sampledLegacyTexture != null ? sampledLegacyTexture.height : 0;
                int sampledVkFormat = sampledLegacyTexture != null ? sampledLegacyTexture.vkFormat : VK10.VK_FORMAT_UNDEFINED;
                long sampledImageHandle = sampledLegacyTexture != null ? sampledLegacyTexture.imageHandle : VK10.VK_NULL_HANDLE;
                int sampledSourceFormat = sampledLegacyTexture != null ? sampledLegacyTexture.sourceFormat : 0;
                int sampledSourceType = sampledLegacyTexture != null ? sampledLegacyTexture.sourceType : 0;
                int sampledInternalFormat = sampledLegacyTexture != null
                    ? sampledLegacyTexture.levels.getOrDefault(0, new TextureLevelInfo(0, 0, 0)).internalFormat
                    : 0;
                int sampledMaxLevel = sampledLegacyTexture != null
                    ? sampledLegacyTexture.integerParameters.getOrDefault(VulkanicAPI.GL_TEXTURE_MAX_LEVEL, 0)
                    : 0;
                LOGGER.info(
                    "Particle Vulkan descriptor write#{} pipeline={} binding={} texId={} label={} usage=0x{} sampler=0x{} view=0x{} image=0x{} texExtent={}x{} vkFormat=0x{} sourceFormat=0x{} sourceType=0x{} internalFormat0=0x{} maxLevel={} baseMip={} mipCount={} trackedLayout=0x{} requestedView=0x{} remappedDefaultView={} legacyResolved={} pipelineHandle=0x{}",
                    debugParticleDescriptorSamplerLogCount,
                    pipelineLocation,
                    binding.name(),
                    sampledLegacyId,
                    sampledTextureLabel,
                    Integer.toHexString(sampledUsage),
                    Long.toHexString(samplerHandle),
                    Long.toHexString(descriptorImageViewHandle),
                    Long.toHexString(sampledImageHandle),
                    sampledWidth,
                    sampledHeight,
                    Integer.toHexString(sampledVkFormat),
                    Integer.toHexString(sampledSourceFormat),
                    Integer.toHexString(sampledSourceType),
                    Integer.toHexString(sampledInternalFormat),
                    sampledMaxLevel,
                    vulkanTextureView.getBaseMipLevel(),
                    vulkanTextureView.getMipLevelCount(),
                    Integer.toHexString(sampledLayout),
                    Long.toHexString(requestedImageViewHandle),
                    requestedImageViewHandle != descriptorImageViewHandle,
                    sampledLegacyTexture != null,
                    Long.toHexString(pipelineHandle)
                );
            }

            return new ResolvedSamplerDescriptorBinding(
                binding.binding(),
                toVkDescriptorType(binding.type()),
                samplerHandle,
                descriptorImageViewHandle,
                descriptorImageLayout
            );
        }

        private ResolvedUniformBufferDescriptorPlan resolveUniformBufferDescriptorBinding(
            PipelineDescriptor.ResourceBinding binding,
            PipelineResourceBindings bindings
        ) {
            VulkanicBufferSlice slice = bindings.getUniformBufferBindingOrNull(binding.name());
            if (slice == null) {
                throw new DescriptorValidationException("Missing uniform-buffer binding for '" + binding.name() + "'");
            }

            if (!(slice.buffer() instanceof VulkanBuffer vulkanBuffer)) {
                throw new IllegalArgumentException(
                    "Uniform-buffer binding '" + binding.name() + "' requires VulkanBuffer on Vulkan backend");
            }

            VulkanBuffer descriptorBuffer = vulkanBuffer;
            long descriptorOffset = slice.offset();
            long descriptorRange = slice.length();
            boolean requiresTransientUniformCopy =
                (descriptorBuffer.usage() & VulkanicBuffer.USAGE_UNIFORM) == 0
                    || (descriptorOffset % minUniformBufferOffsetAlignment) != 0;

            if (requiresTransientUniformCopy) {
                descriptorBuffer = materializeDescriptorUniformBuffer(binding.name(), slice, vulkanBuffer);
                descriptorOffset = 0;
                descriptorRange = slice.length();
            }

            if (debugDescriptorUboLogCount < 200) {
                debugDescriptorUboLogCount++;
                LOGGER.info(
                    "Vulkan uboDescriptor#{} binding={} sourceBuffer=0x{} sourceOffset={} sourceLength={} transientCopy={} descriptorBuffer=0x{} descriptorOffset={} descriptorRange={}",
                    debugDescriptorUboLogCount,
                    binding.name(),
                    Long.toHexString(vulkanBuffer.getVkBufferHandle()),
                    slice.offset(),
                    slice.length(),
                    requiresTransientUniformCopy,
                    Long.toHexString(descriptorBuffer.getVkBufferHandle()),
                    descriptorOffset,
                    descriptorRange
                );
            }

            return new ResolvedUniformBufferDescriptorPlan(
                new ResolvedUniformBufferDescriptorBinding(
                    binding.binding(),
                    toVkDescriptorType(binding.type()),
                    descriptorBuffer.getVkBufferHandle(),
                    descriptorOffset,
                    descriptorRange
                ),
                !requiresTransientUniformCopy
            );
        }

        private ResolvedTexelBufferDescriptorBinding resolveTexelBufferDescriptorBinding(
            PipelineDescriptor.ResourceBinding binding,
            PipelineResourceBindings bindings
        ) {
            PipelineResourceBindings.TexelBufferBinding texelBinding = bindings.getTexelBufferBindingOrNull(binding.name());
            if (texelBinding == null) {
                throw new DescriptorValidationException("Missing texel-buffer binding for '" + binding.name() + "'");
            }

            int unit = texelBinding.textureUnit();
            Integer textureId = legacyTexture2DBindingsByUnit.get(unit);
            if (textureId == null || textureId == 0) {
                throw new IllegalStateException(
                    "Texel-buffer binding '" + binding.name() + "' requires a texture-buffer object bound on unit "
                        + unit + " before descriptor binding");
            }

            LegacyTexelBufferBinding legacyTexelBinding = legacyTexelBufferBindingsByTextureId.get(textureId);
            if (legacyTexelBinding == null
                || legacyTexelBinding.vkBufferViewHandle == VK10.VK_NULL_HANDLE) {
                throw new IllegalStateException(
                    "Texel-buffer binding '" + binding.name() + "' on unit "
                        + unit
                        + " has no uploaded buffer-view. Ensure bindTextureBufferData/texBuffer was called");
            }

            return new ResolvedTexelBufferDescriptorBinding(
                binding.binding(),
                toVkDescriptorType(binding.type()),
                legacyTexelBinding.vkBufferViewHandle
            );
        }

        private void updateAndBindDescriptorSet(long commandBufferHandle,
                                                VulkanPipelineHandle pipeline,
                                                PipelineDescriptor descriptor,
                                                PipelineResourceBindings bindings) {
            long auditStartNanos = VulkanPerfAudit.isEnabled() ? System.nanoTime() : 0L;
            VkCommandBuffer activeCommandBuffer = requireRecordingCommandBuffer(commandBufferHandle, "bindPipelineResources");

            if (descriptorPool == VK10.VK_NULL_HANDLE) {
                throw new IllegalStateException("Descriptor pool is unavailable for Vulkan descriptor updates");
            }
            if (defaultDescriptorSampler == VK10.VK_NULL_HANDLE) {
                throw new IllegalStateException("Default Vulkan sampler is unavailable for descriptor updates");
            }

            List<PipelineDescriptor.ResourceBinding> layoutBindings =
                normalizeDescriptorLayoutBindings(descriptor.getResourceLayout().bindings());
            boolean sodiumChunkDescriptor = layoutBindings.stream()
                .anyMatch(layoutBinding -> "SodiumChunkParams".contentEquals(layoutBinding.name()));
            String pipelineLocation = descriptor.getPortableState().location().toString();
            boolean particleDescriptor = "minecraft:pipeline/opaque_particle".equals(pipelineLocation)
                || "minecraft:pipeline/translucent_particle".equals(pipelineLocation);

            // If the pipeline has no descriptors at all, nothing more to do.
            if (pipeline.getResourceBindingCount() == 0) {
                // Even with no descriptors, bind the pipeline
                bindPipeline(commandBufferHandle, pipeline.getVkPipelineHandle());
                return;
            }

            if (layoutBindings.isEmpty()) {
                // Even with empty descriptors, bind the pipeline
                bindPipeline(commandBufferHandle, pipeline.getVkPipelineHandle());
                return;
            }

            // Always bind the pipeline first so the GPU knows which shader/layout to use
            // for subsequent draw calls. vkCmdBindPipeline is valid both inside and
            // outside a render pass (Vulkan spec §19.3).
            bindPipeline(commandBufferHandle, pipeline.getVkPipelineHandle());

            try (MemoryStack stack = stackPush()) {
                DescriptorWritePlan writePlan = buildDescriptorWritePlan(
                    layoutBindings,
                    bindings,
                    sodiumChunkDescriptor,
                    particleDescriptor,
                    pipelineLocation,
                    pipeline.getVkPipelineHandle(),
                    pipeline.getVkDescriptorSetLayoutHandle()
                );

                if (writePlan.cacheable()) {
                    Long cachedDescriptorSetHandle = descriptorSetCache.get(writePlan.cacheKey());
                    if (cachedDescriptorSetHandle != null) {
                        descriptorSetCacheHitCount++;
                        bindDescriptorSetIfNeeded(
                            commandBufferHandle,
                            activeCommandBuffer,
                            pipeline,
                            cachedDescriptorSetHandle
                        );
                        return;
                    }
                }

                VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType$Default()
                    .descriptorPool(descriptorPool)
                    .pSetLayouts(stack.longs(pipeline.getVkDescriptorSetLayoutHandle()));

                java.nio.LongBuffer pDescriptorSet = stack.mallocLong(1);
                checkVk("vkAllocateDescriptorSets(bindPipelineResources)",
                    VK10.vkAllocateDescriptorSets(logicalDevice, allocInfo, pDescriptorSet));
                long descriptorSetHandle = pDescriptorSet.get(0);

                VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(writePlan.bindings().size(), stack);
                for (int i = 0; i < writePlan.bindings().size(); i++) {
                    ResolvedDescriptorBinding resolvedBinding = writePlan.bindings().get(i);
                    VkWriteDescriptorSet write = writes.get(i)
                        .sType$Default()
                        .dstSet(descriptorSetHandle)
                        .dstBinding(resolvedBinding.bindingIndex())
                        .dstArrayElement(0)
                        .descriptorCount(1)
                        .descriptorType(resolvedBinding.descriptorType());
                    resolvedBinding.populateWrite(write, stack);
                }

                VK10.vkUpdateDescriptorSets(logicalDevice, writes, null);
                if (writePlan.cacheable()) {
                    descriptorSetCache.put(writePlan.cacheKey(), descriptorSetHandle);
                    descriptorSetCacheStoreCount++;
                }
                bindDescriptorSetIfNeeded(commandBufferHandle, activeCommandBuffer, pipeline, descriptorSetHandle);
            } finally {
                if (auditStartNanos != 0L) {
                    VulkanPerfAudit.recordDescriptorBind(System.nanoTime() - auditStartNanos);
                }
            }
        }

        private long resolveDescriptorSamplerHandle(@Nullable DescriptorSamplerKey key) {
            if (key == null) {
                return defaultDescriptorSampler;
            }

            Long cachedSampler = descriptorSamplerCache.get(key);
            if (cachedSampler != null) {
                return cachedSampler;
            }

            long createdSampler = createDescriptorSampler(key);
            Long existingSampler = descriptorSamplerCache.putIfAbsent(key, createdSampler);
            if (existingSampler != null) {
                VK10.vkDestroySampler(logicalDevice, createdSampler, null);
                return existingSampler;
            }

            return createdSampler;
        }

        private long resolveDescriptorSamplerHandle(VulkanTextureView textureView) {
            return resolveDescriptorSamplerHandle(descriptorSamplerKey(textureView));
        }

        private LegacyTextureObject tryResolveLegacyTexture(net.vulkanic.VulkanicTexture texture) {
            if (texture == null) {
                return null;
            }

            int legacyTextureHandle = 0;
            if (texture instanceof net.blaze3d.textures.GpuTexture gpuTexture) {
                legacyTextureHandle = resolveGpuTextureLegacyHandle(gpuTexture);
            }
            if (legacyTextureHandle == 0) {
                legacyTextureHandle = resolveLegacyGlHandleViaAccessor(texture);
            }
            if (legacyTextureHandle == 0) {
                return null;
            }

            return legacyTextures.get(legacyTextureHandle);
        }

        private LegacyTextureObject tryResolveLegacyTexture(VulkanTextureView textureView) {
            int legacyTextureHandle = textureView.getLegacyTextureHandle();
            if (legacyTextureHandle > 0) {
                return legacyTextures.get(legacyTextureHandle);
            }
            return tryResolveLegacyTexture(textureView.texture());
        }

        private void transitionLegacyTextureToSampleLayout(@Nullable LegacyTextureObject texture,
                                                           VulkanTextureView view) {
            transitionLegacyTextureToSampleLayout(
                texture,
                Math.max(0, view.getBaseMipLevel()),
                Math.max(1, view.getMipLevelCount())
            );
        }

        private void transitionLegacyTextureToSampleLayout(@Nullable LegacyTextureObject texture,
                                                           int baseMip,
                                                           int mipCount) {
            if (texture == null || texture.imageHandle == VK10.VK_NULL_HANDLE) {
                return;
            }

            int safeBaseMip = Math.max(0, baseMip);
            int safeMipCount = Math.max(1, mipCount);
            int maxMipLevels = Math.max(1, texture.mipLevels);
            if (safeBaseMip >= maxMipLevels) {
                return;
            }
            int endMipExclusive = Math.min(maxMipLevels, safeBaseMip + safeMipCount);

            // Feedback-loop-capable textures live permanently in ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT
            // so they can be sampled without transition from any stage (including inside a render pass
            // where the same image may simultaneously be a color attachment).
            int targetLayout = texture.feedbackLoopCapable
                ? EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT
                : (texture.aspectMask == VK10.VK_IMAGE_ASPECT_DEPTH_BIT
                    ? VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL
                    : VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

            List<VulkanImageStateTracker.VulkanImageTransition> transitions =
                imageStateTracker.planTransitions(texture.id, safeBaseMip, endMipExclusive - safeBaseMip, targetLayout);
            for (VulkanImageStateTracker.VulkanImageTransition transition : transitions) {
                int level = transition.mipLevel();
                int trackedLayout = transition.oldLayout();

                if (renderPassRecording) {
                    // Emitting a vkCmdPipelineBarrier inside a render pass is illegal.
                    // The finalLayout transitions on all render passes should pre-transition
                    // images to the correct layout before they are sampled. If this fires,
                    // there is a layout tracking gap that needs to be fixed.
                    LOGGER.warn(
                        "Illegal sampler layout transition inside render pass: texId={} trackedLayout=0x{} targetLayout=0x{} level={}",
                        texture.id,
                        Integer.toHexString(trackedLayout),
                        Integer.toHexString(targetLayout),
                        level
                    );
                    // Update tracking anyway so we log once, not every frame.
                    trackLayoutForLevel(texture, level, targetLayout);
                    continue;
                }

                transitionImageLayout(texture, trackedLayout, targetLayout, level, 1);
                trackLayoutForLevel(texture, level, targetLayout);
            }
        }

        private DescriptorSamplerKey descriptorSamplerKey(VulkanTextureView textureView) {
            return descriptorSamplerKey(textureView, tryResolveLegacyTexture(textureView), null, -1, null);
        }

        /**
         * Builds a {@link DescriptorSamplerKey} for {@code textureView}.
         *
         * <p>When {@code compareOverride} is non-null it replaces the texture-level
         * {@code GL_TEXTURE_COMPARE_MODE} in the key.  Pass {@code Boolean.TRUE} for a
         * {@code sampler2DShadow} binding (needs {@code compareEnable=true}) and
         * {@code Boolean.FALSE} for a plain {@code sampler2D} binding of a depth texture
         * (needs {@code compareEnable=false} regardless of how the texture was configured).
         */
        private DescriptorSamplerKey descriptorSamplerKey(
            VulkanTextureView textureView,
            @Nullable LegacyTextureObject legacyTexture,
            @Nullable Integer samplerObject,
            int textureUnit,
            @Nullable Boolean compareOverride
        ) {
            if (legacyTexture == null) {
                return null;
            }

            VirtualSamplerState samplerState = samplerObject != null
                ? backend.getVirtualSamplerState(samplerObject)
                : textureUnit >= 0
                ? backend.getBoundVirtualSamplerStateForUnit(textureUnit)
                : null;

            net.vulkanic.VulkanicTexture boundTexture = textureView.texture();
            GpuTexture gpuTexture = boundTexture instanceof GpuTexture blazeTexture ? blazeTexture : null;
            int minFilter = samplerState != null
                ? samplerState.minFilter
                : gpuTexture != null
                ? toLegacyMinFilter(gpuTexture.getMinFilter(), gpuTexture.usesMipmaps())
                : legacyTexture.integerParameters.getOrDefault(
                    VulkanicAPI.GL_TEXTURE_MIN_FILTER,
                    VulkanicAPI.GL_NEAREST
                );
            int magFilter = samplerState != null
                ? samplerState.magFilter
                : gpuTexture != null
                ? toLegacyMagFilter(gpuTexture.getMagFilter())
                : legacyTexture.integerParameters.getOrDefault(
                    VulkanicAPI.GL_TEXTURE_MAG_FILTER,
                    VulkanicAPI.GL_LINEAR
                );
            int wrapS = samplerState != null
                ? samplerState.wrapS
                : gpuTexture != null
                ? toLegacyWrapMode(gpuTexture.getAddressModeU())
                : legacyTexture.integerParameters.getOrDefault(
                    VulkanicAPI.GL_TEXTURE_WRAP_S,
                    VulkanicAPI.GL_REPEAT
                );
            int wrapT = samplerState != null
                ? samplerState.wrapT
                : gpuTexture != null
                ? toLegacyWrapMode(gpuTexture.getAddressModeV())
                : legacyTexture.integerParameters.getOrDefault(
                    VulkanicAPI.GL_TEXTURE_WRAP_T,
                    VulkanicAPI.GL_REPEAT
                );
            int wrapR = samplerState != null
                ? samplerState.wrapR
                : legacyTexture.integerParameters.getOrDefault(
                    VulkanicAPI.GL_TEXTURE_WRAP_R,
                    wrapT
                );
            int maxLod = usesMipmappedMinFilter(minFilter)
                ? (samplerState != null
                    ? samplerState.effectiveMaxLod(textureView.getMipLevelCount())
                    : Math.max(0, textureView.getMipLevelCount() - 1))
                : 0;
            int compareMode;
            if (compareOverride != null) {
                compareMode = compareOverride ? VulkanicAPI.GL_COMPARE_REF_TO_TEXTURE : 0;
            } else {
                compareMode = samplerState != null
                    ? samplerState.compareMode
                    : legacyTexture.integerParameters.getOrDefault(
                        VulkanicAPI.GL_TEXTURE_COMPARE_MODE, 0
                    );
            }

            int compareFunc = samplerState != null
                ? samplerState.compareFunc
                : legacyTexture.integerParameters.getOrDefault(
                    VulkanBackend.GL_TEXTURE_COMPARE_FUNC,
                    VulkanicAPI.GL_LEQUAL
                );

            return new DescriptorSamplerKey(minFilter, magFilter, wrapS, wrapT, wrapR, maxLod, compareMode, compareFunc);
        }

        private static int toLegacyMinFilter(net.blaze3d.textures.FilterMode minFilter, boolean useMipmaps) {
            return switch (minFilter) {
                case NEAREST -> useMipmaps ? VulkanicAPI.GL_NEAREST_MIPMAP_LINEAR : VulkanicAPI.GL_NEAREST;
                case LINEAR -> useMipmaps ? VulkanicAPI.GL_LINEAR_MIPMAP_LINEAR : VulkanicAPI.GL_LINEAR;
            };
        }

        private static int toLegacyMagFilter(net.blaze3d.textures.FilterMode magFilter) {
            return switch (magFilter) {
                case NEAREST -> VulkanicAPI.GL_NEAREST;
                case LINEAR -> VulkanicAPI.GL_LINEAR;
            };
        }

        private static int toLegacyWrapMode(net.blaze3d.textures.AddressMode addressMode) {
            return switch (addressMode) {
                case REPEAT -> VulkanicAPI.GL_REPEAT;
                case CLAMP_TO_EDGE -> VulkanicAPI.GL_CLAMP_TO_EDGE;
            };
        }

        private long createDescriptorSampler(DescriptorSamplerKey key) {
            try (MemoryStack stack = stackPush()) {
                VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.calloc(stack)
                    .sType$Default()
                    .magFilter(toVkMagFilter(key.magFilter()))
                    .minFilter(toVkMinFilter(key.minFilter()))
                    .mipmapMode(toVkMipmapMode(key.minFilter()))
                    .addressModeU(toVkSamplerAddressMode(key.wrapS()))
                    .addressModeV(toVkSamplerAddressMode(key.wrapT()))
                    .addressModeW(toVkSamplerAddressMode(key.wrapR()))
                    .anisotropyEnable(false)
                    .maxAnisotropy(1.0f)
                    .compareEnable(key.compareMode() == VulkanicAPI.GL_COMPARE_REF_TO_TEXTURE)
                    .compareOp(key.compareMode() == VulkanicAPI.GL_COMPARE_REF_TO_TEXTURE
                        ? toVkSamplerCompareOp(key.compareFunc())
                        : VK10.VK_COMPARE_OP_ALWAYS)
                    .minLod(0.0f)
                    .maxLod((float) key.maxLod())
                    .borderColor(VK10.VK_BORDER_COLOR_INT_OPAQUE_BLACK)
                    .unnormalizedCoordinates(false);

                java.nio.LongBuffer pSampler = stack.mallocLong(1);
                checkVk("vkCreateSampler(descriptor)",
                    VK10.vkCreateSampler(logicalDevice, samplerInfo, null, pSampler));
                return pSampler.get(0);
            }
        }

        private static boolean usesMipmappedMinFilter(int minFilter) {
            return switch (minFilter) {
                case VulkanicAPI.GL_NEAREST_MIPMAP_NEAREST,
                    VulkanicAPI.GL_LINEAR_MIPMAP_NEAREST,
                    VulkanicAPI.GL_NEAREST_MIPMAP_LINEAR,
                    VulkanicAPI.GL_LINEAR_MIPMAP_LINEAR -> true;
                case VulkanicAPI.GL_NEAREST,
                    VulkanicAPI.GL_LINEAR -> false;
                default -> false;
            };
        }

        private static int toVkMinFilter(int minFilter) {
            return switch (minFilter) {
                case VulkanicAPI.GL_NEAREST,
                    VulkanicAPI.GL_NEAREST_MIPMAP_NEAREST,
                    VulkanicAPI.GL_NEAREST_MIPMAP_LINEAR -> VK10.VK_FILTER_NEAREST;
                case VulkanicAPI.GL_LINEAR,
                    VulkanicAPI.GL_LINEAR_MIPMAP_NEAREST,
                    VulkanicAPI.GL_LINEAR_MIPMAP_LINEAR -> VK10.VK_FILTER_LINEAR;
                default -> throw new IllegalArgumentException(
                    "Unsupported descriptor sampler min filter: " + minFilter);
            };
        }

        private static int toVkMagFilter(int magFilter) {
            return switch (magFilter) {
                case VulkanicAPI.GL_NEAREST -> VK10.VK_FILTER_NEAREST;
                case VulkanicAPI.GL_LINEAR -> VK10.VK_FILTER_LINEAR;
                default -> throw new IllegalArgumentException(
                    "Unsupported descriptor sampler mag filter: " + magFilter);
            };
        }

        private static int toVkMipmapMode(int minFilter) {
            return switch (minFilter) {
                case VulkanicAPI.GL_NEAREST,
                    VulkanicAPI.GL_LINEAR,
                    VulkanicAPI.GL_NEAREST_MIPMAP_NEAREST,
                    VulkanicAPI.GL_LINEAR_MIPMAP_NEAREST -> VK10.VK_SAMPLER_MIPMAP_MODE_NEAREST;
                case VulkanicAPI.GL_NEAREST_MIPMAP_LINEAR,
                    VulkanicAPI.GL_LINEAR_MIPMAP_LINEAR -> VK10.VK_SAMPLER_MIPMAP_MODE_LINEAR;
                default -> throw new IllegalArgumentException(
                    "Unsupported descriptor sampler mipmap mode for filter: " + minFilter);
            };
        }

        private static int toVkSamplerAddressMode(int wrapMode) {
            return switch (wrapMode) {
                case VulkanicAPI.GL_REPEAT -> VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT;
                case VulkanicAPI.GL_CLAMP_TO_EDGE -> VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
                default -> throw new IllegalArgumentException(
                    "Unsupported descriptor sampler wrap mode: " + wrapMode);
            };
        }

        private static int toVkSamplerCompareOp(int compareFunc) {
            return switch (compareFunc) {
                case VulkanicAPI.GL_NEVER -> VK10.VK_COMPARE_OP_NEVER;
                case VulkanicAPI.GL_LESS -> VK10.VK_COMPARE_OP_LESS;
                case VulkanicAPI.GL_EQUAL -> VK10.VK_COMPARE_OP_EQUAL;
                case VulkanicAPI.GL_LEQUAL -> VK10.VK_COMPARE_OP_LESS_OR_EQUAL;
                case VulkanicAPI.GL_GREATER -> VK10.VK_COMPARE_OP_GREATER;
                case VulkanicAPI.GL_NOTEQUAL -> VK10.VK_COMPARE_OP_NOT_EQUAL;
                case VulkanicAPI.GL_GEQUAL -> VK10.VK_COMPARE_OP_GREATER_OR_EQUAL;
                case VulkanicAPI.GL_ALWAYS -> VK10.VK_COMPARE_OP_ALWAYS;
                default -> VK10.VK_COMPARE_OP_LESS_OR_EQUAL;
            };
        }

        private int createLegacyBuffer() {
            int id = nextLegacyBufferId.getAndIncrement();
            legacyBuffers.put(id, new LegacyBufferObject(id));
            return id;
        }

        private void createLegacyBuffers(int[] buffers) {
            for (int i = 0; i < buffers.length; i++) {
                buffers[i] = createLegacyBuffer();
            }
        }

        private void deleteLegacyBuffer(int bufferId) {
            if (bufferId == 0) {
                return;
            }

            unmapNamedBuffer(bufferId);

            LegacyBufferObject legacy = legacyBuffers.remove(bufferId);
            if (legacy != null) {
                closeLegacyBufferStorage(legacy);
            }

            for (Map.Entry<Integer, Integer> entry : new ArrayList<>(legacyBufferBindings.entrySet())) {
                if (entry.getValue() == bufferId) {
                    legacyBufferBindings.remove(entry.getKey());
                }
            }

            for (Map.Entry<Integer, LegacyTexelBufferBinding> entry :
                new ArrayList<>(legacyTexelBufferBindingsByTextureId.entrySet())) {
                LegacyTexelBufferBinding texelBinding = entry.getValue();
                if (texelBinding.legacyBufferId == bufferId) {
                    if (texelBinding.vkBufferViewHandle != VK10.VK_NULL_HANDLE && logicalDevice != null) {
                        destroyBufferView(texelBinding.vkBufferViewHandle);
                    }
                    legacyTexelBufferBindingsByTextureId.remove(entry.getKey());
                }
            }
        }

        private void bindLegacyBuffer(int target, int bufferId) {
            if (bufferId == 0) {
                legacyBufferBindings.remove(target);
                return;
            }

            LegacyBufferObject legacy = legacyBuffers.computeIfAbsent(bufferId, id -> new LegacyBufferObject(id));
            legacy.lastTarget = target;
            legacyBufferBindings.put(target, bufferId);
        }

        private int createLegacyTexture(int target) {
            int id = nextLegacyTextureId.getAndIncrement();
            legacyTextures.put(id, new LegacyTextureObject(id, target));
            return id;
        }

        private void deleteLegacyTexture(int textureId) {
            if (textureId == 0) {
                return;
            }

            LegacyTexelBufferBinding texelBinding = legacyTexelBufferBindingsByTextureId.remove(textureId);
            if (texelBinding != null && texelBinding.vkBufferViewHandle != VK10.VK_NULL_HANDLE && logicalDevice != null) {
                destroyBufferView(texelBinding.vkBufferViewHandle);
            }

            LegacyTextureObject texture = legacyTextures.remove(textureId);
            if (texture != null) {
                destroyLegacyTextureStorage(texture);
            }
            imageStateTracker.unregisterTexture(textureId);
            for (Map.Entry<Integer, Integer> entry : new ArrayList<>(legacyTexture2DBindingsByUnit.entrySet())) {
                if (entry.getValue() == textureId) {
                    legacyTexture2DBindingsByUnit.remove(entry.getKey());
                }
            }
        }

        private void bindLegacyTexture(int target, int textureId) {
            if (target == VulkanicAPI.GL_PROXY_TEXTURE_2D) {
                return;
            }

            if (textureId == 0) {
                legacyTexture2DBindingsByUnit.remove(activeTextureUnitIndex);
                return;
            }

            LegacyTextureObject legacyTexture = legacyTextures.computeIfAbsent(textureId, id -> new LegacyTextureObject(id, target));
            maybeUpgradeLegacyTextureTarget(legacyTexture, target);
            legacyTexture2DBindingsByUnit.put(activeTextureUnitIndex, textureId);
        }

        private void bindLegacyTextureUnit(int unit, int textureId) {
            if (textureId == 0) {
                legacyTexture2DBindingsByUnit.remove(unit);
                return;
            }

            legacyTextures.computeIfAbsent(textureId, id -> new LegacyTextureObject(id, VulkanicAPI.GL_TEXTURE_2D));
            legacyTexture2DBindingsByUnit.put(unit, textureId);
        }

        private void maybeUpgradeLegacyTextureTarget(LegacyTextureObject texture, int requestedTarget) {
            if (texture == null) {
                return;
            }

            boolean existingCubemap = isLegacyCubemapTarget(texture.target);
            boolean requestedCubemap = isLegacyCubemapTarget(requestedTarget);
            if (existingCubemap || !requestedCubemap) {
                return;
            }

            texture.target = VulkanicAPI.GL_TEXTURE_CUBE_MAP;

            // Texture names are created as 2D by IrisRenderSystem.createTextureId().
            // If the name is later first used for a cubemap, any 2D Vulkan image/view
            // allocation tied to that placeholder target is invalid and must be dropped.
            if (texture.imageHandle != VK10.VK_NULL_HANDLE) {
                destroyLegacyTextureStorage(texture);
            }
        }

        private void setActiveTextureUnit(int unit) {
            int normalized = unit >= VulkanicAPI.GL_TEXTURE0
                ? unit - VulkanicAPI.GL_TEXTURE0
                : unit;
            if (normalized < 0) {
                throw new IllegalArgumentException("Texture unit must be >= 0, got: " + unit);
            }
            activeTextureUnitIndex = normalized;
        }

        private void bindLegacyTexelBufferForActiveUnit(int internalFormat, int bufferId) {
            Integer textureId = legacyTexture2DBindingsByUnit.get(activeTextureUnitIndex);
            if (textureId == null || textureId == 0) {
                throw new IllegalStateException(
                    "texBuffer requires a texture-buffer object bound on active texture unit "
                        + activeTextureUnitIndex + " (bindTexture(GL_TEXTURE_BUFFER, texture) first)");
            }

            LegacyTexelBufferBinding previous = legacyTexelBufferBindingsByTextureId.remove(textureId);
            if (previous != null && previous.vkBufferViewHandle != VK10.VK_NULL_HANDLE) {
                destroyBufferView(previous.vkBufferViewHandle);
            }

            if (bufferId == 0) {
                return;
            }

            LegacyBufferObject legacyBuffer = requireLegacyBuffer(bufferId);
            VulkanBuffer vulkanBuffer = ensureLegacyBufferUsage(
                legacyBuffer,
                VulkanicBuffer.USAGE_UNIFORM_TEXEL_BUFFER,
                "texBuffer"
            );

            try (MemoryStack stack = stackPush()) {
                int vkFormat = toVkTexelBufferFormat(internalFormat);
                VkBufferViewCreateInfo viewInfo = VkBufferViewCreateInfo.calloc(stack)
                    .sType$Default()
                    .buffer(vulkanBuffer.getVkBufferHandle())
                    .format(vkFormat)
                    .offset(0)
                    .range(VK10.VK_WHOLE_SIZE);

                java.nio.LongBuffer pView = stack.mallocLong(1);
                checkVk("vkCreateBufferView(texBuffer)",
                    VK10.vkCreateBufferView(logicalDevice, viewInfo, null, pView));

                legacyTexelBufferBindingsByTextureId.put(
                    textureId,
                    new LegacyTexelBufferBinding(internalFormat, bufferId, pView.get(0))
                );
            }
        }

        private VulkanBuffer ensureLegacyBufferUsage(LegacyBufferObject legacy, int requiredUsage, String operation) {
            VulkanBuffer buffer = requireAllocatedLegacyBuffer(legacy, operation);
            if ((buffer.usage() & requiredUsage) == requiredUsage) {
                return buffer;
            }

            if (legacyBufferMappedViews.containsKey(legacy.id)) {
                throw new IllegalStateException(
                    operation + " cannot upgrade legacy buffer " + legacy.id + " while it is mapped"
                );
            }

            java.nio.ByteBuffer existingData = null;
            if (legacy.logicalSizeBytes > 0) {
                existingData = org.lwjgl.BufferUtils.createByteBuffer(legacy.logicalSizeBytes);
                try (VulkanicBuffer.MappedView mappedView = mapManagedBuffer(buffer, true, false)) {
                    java.nio.ByteBuffer source = mappedView.data().duplicate();
                    source.position(0).limit(legacy.logicalSizeBytes);
                    existingData.put(source);
                    existingData.flip();
                }
            }

            VulkanBuffer upgradedBuffer = (VulkanBuffer) createManagedBuffer(
                "LegacyBuffer-" + legacy.id,
                buffer.usage() | requiredUsage,
                legacy.logicalSizeBytes,
                existingData
            );

            buffer.close();
            legacy.buffer = upgradedBuffer;
            return upgradedBuffer;
        }

        private void setLegacyTextureParameter(int target, int pname, int param) {
            if (target == VulkanicAPI.GL_PROXY_TEXTURE_2D) {
                return;
            }
            LegacyTextureObject texture = requireBoundLegacyTexture2D(target, "texParameteri");
            texture.integerParameters.put(pname, param);
        }

        private int getLegacyTextureParameter(int target, int pname) {
            if (target == VulkanicAPI.GL_PROXY_TEXTURE_2D) {
                return 0;
            }
            LegacyTextureObject texture = requireBoundLegacyTexture2D(target, "getTexParameteri");
            return texture.integerParameters.getOrDefault(pname, 0);
        }

        private void setPixelStore(int pname, int value) {
            if (value < 0) {
                throw new IllegalArgumentException("Pixel-store value must be >= 0, got: " + value);
            }

            switch (pname) {
                case VulkanicAPI.GL_UNPACK_ROW_LENGTH -> pixelStoreState.unpackRowLength = value;
                case VulkanicAPI.GL_UNPACK_SKIP_ROWS -> pixelStoreState.unpackSkipRows = value;
                case VulkanicAPI.GL_UNPACK_SKIP_PIXELS -> pixelStoreState.unpackSkipPixels = value;
                case VulkanicAPI.GL_UNPACK_ALIGNMENT -> {
                    if (value != 1 && value != 2 && value != 4 && value != 8) {
                        throw new IllegalArgumentException(
                            "GL_UNPACK_ALIGNMENT must be one of {1,2,4,8}, got: " + value);
                    }
                    pixelStoreState.unpackAlignment = value;
                }
                default -> {
                }
            }
        }

        private int getTextureLevelParameter(int target, int level, int pname) {
            TextureLevelInfo info;
            if (target == VulkanicAPI.GL_PROXY_TEXTURE_2D) {
                info = proxyTexture2DLevels.get(level);
            } else {
                LegacyTextureObject texture = requireBoundLegacyTexture2D(target, "getTextureLevelParameter");
                info = texture.levels.get(level);
            }

            if (info == null) {
                return 0;
            }

            return switch (pname) {
                case VulkanicAPI.GL_TEXTURE_WIDTH -> info.width;
                case VulkanicAPI.GL_TEXTURE_HEIGHT -> info.height;
                case VulkanicAPI.GL_TEXTURE_INTERNAL_FORMAT -> info.internalFormat;
                default -> 0;
            };
        }

        private LegacyTextureObject requireBoundLegacyTexture2D(int target, String operation) {
            if (target != VulkanicAPI.GL_TEXTURE_1D
                && target != VulkanicAPI.GL_TEXTURE_2D
                && target != VulkanicAPI.GL_TEXTURE_3D
                && target != VulkanicAPI.GL_TEXTURE_RECTANGLE
                && target != VulkanicAPI.GL_TEXTURE_CUBE_MAP
                && !isLegacyCubemapFaceTarget(target)) {
                throw new IllegalArgumentException(
                    operation + " currently supports GL_TEXTURE_1D/GL_TEXTURE_2D/GL_TEXTURE_3D/"
                        + "GL_TEXTURE_RECTANGLE/GL_TEXTURE_CUBE_MAP targets, got: " + target);
            }

            Integer textureId = legacyTexture2DBindingsByUnit.get(activeTextureUnitIndex);
            if (textureId == null || textureId == 0) {
                throw new IllegalStateException(
                    "No legacy Vulkan texture is bound to GL_TEXTURE_2D on active texture unit " + activeTextureUnitIndex);
            }

            LegacyTextureObject texture = legacyTextures.get(textureId);
            if (texture == null) {
                throw new IllegalStateException("Bound legacy Vulkan texture handle is unknown: " + textureId);
            }
            return texture;
        }

        private static int align(int value, int alignment) {
            if (alignment <= 1) {
                return value;
            }
            int mask = alignment - 1;
            return (value + mask) & ~mask;
        }

        private static int maxMipLevelsForExtent(int width, int height) {
            int maxDimension = Math.max(width, height);
            if (maxDimension <= 0) {
                return 1;
            }
            return 32 - Integer.numberOfLeadingZeros(maxDimension);
        }

        private java.nio.ByteBuffer normalizePixelData(java.nio.ByteBuffer pixels,
                                                       LegacyTextureFormatInfo formatInfo,
                                                       int width,
                                                       int height) {
            if (pixels == null) {
                throw new IllegalArgumentException("pixels must not be null");
            }
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("width/height must be > 0");
            }

            int rowLength = pixelStoreState.unpackRowLength > 0
                ? pixelStoreState.unpackRowLength
                : width;
            int rowBytes = rowLength * formatInfo.unpackPixelBytes;
            int stride = align(rowBytes, pixelStoreState.unpackAlignment);
            int startOffset = pixelStoreState.unpackSkipRows * stride
                + pixelStoreState.unpackSkipPixels * formatInfo.unpackPixelBytes;

            long requiredLong = (long) startOffset
                + (long) (height - 1) * stride
                + (long) width * formatInfo.unpackPixelBytes;
            if (requiredLong > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Pixel upload source size exceeds int range: " + requiredLong);
            }
            int required = (int) requiredLong;
            if (pixels.remaining() < required) {
                throw new IllegalArgumentException(
                    "Pixel upload buffer too small. Required=" + required + ", remaining=" + pixels.remaining());
            }

            int tightlyPackedSourceRowBytes = width * formatInfo.unpackPixelBytes;
            int tightlyPackedDestRowBytes = width * formatInfo.pixelBytes;
            if (!formatInfo.expandRgbToRgba && stride == tightlyPackedSourceRowBytes) {
                java.nio.ByteBuffer source = pixels.duplicate();
                source.position(source.position() + startOffset);
                source.limit(source.position() + tightlyPackedSourceRowBytes * height);
                return source.slice();
            }

            java.nio.ByteBuffer packed = java.nio.ByteBuffer.allocateDirect(tightlyPackedDestRowBytes * height)
                .order(ByteOrder.nativeOrder());

            java.nio.ByteBuffer source = pixels.duplicate();
            int sourceBase = source.position() + startOffset;
            if (formatInfo.expandRgbToRgba) {
                for (int row = 0; row < height; row++) {
                    int rowStart = sourceBase + row * stride;
                    for (int column = 0; column < width; column++) {
                        int sourcePixelStart = rowStart + column * formatInfo.unpackPixelBytes;
                        packed.put(source.get(sourcePixelStart));
                        packed.put(source.get(sourcePixelStart + 1));
                        packed.put(source.get(sourcePixelStart + 2));
                        packed.put((byte) 0xFF);
                    }
                }
            } else {
                for (int row = 0; row < height; row++) {
                    int rowStart = sourceBase + row * stride;
                    java.nio.ByteBuffer rowSlice = source.duplicate();
                    rowSlice.position(rowStart);
                    rowSlice.limit(rowStart + tightlyPackedSourceRowBytes);
                    packed.put(rowSlice);
                }
            }

            packed.flip();
            return packed;
        }

        private void uploadLegacyTexture2D(long commandBufferHandle,
                                           int target,
                                           int level,
                                           int internalFormat,
                                           int width,
                                           int height,
                                           int format,
                                           int type,
                                           java.nio.ByteBuffer pixels) {
            ensureRecordingCommandBuffer(commandBufferHandle, "uploadTexture2D");
            if (renderPassRecording) {
                throw new IllegalStateException("uploadTexture2D requires command recording outside an active render pass");
            }

            LegacyTextureFormatInfo formatInfo = pixels == null
                ? LegacyTextureFormatInfo.resolveStorage(internalFormat, format, type)
                : LegacyTextureFormatInfo.resolve(internalFormat, format, type);

            if (target == VulkanicAPI.GL_PROXY_TEXTURE_2D) {
                proxyTexture2DLevels.put(level, new TextureLevelInfo(width, height, internalFormat));
                return;
            }

            LegacyTextureObject texture = requireBoundLegacyTexture2D(target, "uploadTexture2D");
            proxyTexture2DLevels.remove(level);

            int inferredBaseWidth = level == 0 ? width : Math.max(1, width << level);
            int inferredBaseHeight = level == 0 ? height : Math.max(1, height << level);
            int maxConfiguredLevel = Math.max(0, texture.integerParameters.getOrDefault(VulkanicAPI.GL_TEXTURE_MAX_LEVEL, level));
            int configuredMipLevels = Math.max(1, maxConfiguredLevel + 1);
            int maxPossibleMipLevels = maxMipLevelsForExtent(inferredBaseWidth, inferredBaseHeight);
            int requiredMipLevels = Math.max(1, Math.max(level + 1, Math.min(configuredMipLevels, maxPossibleMipLevels)));

            Map<Integer, TextureLevelInfo> preservedLevels = null;
            boolean needsRecreate = texture.imageHandle == VK10.VK_NULL_HANDLE
                || texture.vkFormat != formatInfo.vkFormat
                || texture.width != inferredBaseWidth
                || texture.height != inferredBaseHeight
                || texture.mipLevels < requiredMipLevels;

            boolean preserveExistingLevels = texture.imageHandle != VK10.VK_NULL_HANDLE
                && texture.vkFormat == formatInfo.vkFormat
                && texture.width == inferredBaseWidth
                && texture.height == inferredBaseHeight
                && texture.mipLevels < requiredMipLevels;

            if (preserveExistingLevels) {
                preservedLevels = new java.util.HashMap<>(texture.levels);
            }

            if (needsRecreate) {
                recreateLegacyTextureStorage(texture, formatInfo, inferredBaseWidth, inferredBaseHeight, requiredMipLevels);
                if (preservedLevels != null && !preservedLevels.isEmpty()) {
                    texture.levels.putAll(preservedLevels);
                }
            }

            texture.sourceFormat = format;
            texture.sourceType = type;
            texture.levels.put(level, new TextureLevelInfo(width, height, internalFormat));

            if (pixels == null) {
                int finalLayout = preferredIdleLayout(texture);
                int oldLayout = trackedLayoutForLevel(texture, level);
                transitionImageLayout(texture, oldLayout, finalLayout, level, 1);
                trackLayoutForLevel(texture, level, finalLayout);
                return;
            }

            java.nio.ByteBuffer packedPixels = normalizePixelData(pixels, formatInfo, width, height);
            uploadToLegacyTextureRegion(commandBufferHandle, "uploadTexture2D", texture, target, level, 0, 0, width, height, packedPixels);
        }

        private void uploadLegacyTexture2DSubImage(long commandBufferHandle,
                                                   int target,
                                                   int level,
                                                   int xOffset,
                                                   int yOffset,
                                                   int width,
                                                   int height,
                                                   int format,
                                                   int type,
                                                   long pixelsPointer) {
            LegacyTextureObject texture = uploadLegacyTexture2DSubImageCommon(
                commandBufferHandle,
                target,
                level,
                xOffset,
                yOffset,
                width,
                height,
                format,
                type
            );

            LegacyTextureFormatInfo formatInfo = LegacyTextureFormatInfo.resolve(
                texture.levels.get(level).internalFormat,
                format,
                type
            );
            int rowLength = pixelStoreState.unpackRowLength > 0 ? pixelStoreState.unpackRowLength : width;
            int stride = align(rowLength * formatInfo.unpackPixelBytes, pixelStoreState.unpackAlignment);
            int startOffset = pixelStoreState.unpackSkipRows * stride
                + pixelStoreState.unpackSkipPixels * formatInfo.unpackPixelBytes;
            int required = startOffset + (height - 1) * stride + width * formatInfo.unpackPixelBytes;

            java.nio.ByteBuffer source = MemoryUtil.memByteBuffer(pixelsPointer, required);
            java.nio.ByteBuffer packedPixels = normalizePixelData(source, formatInfo, width, height);
            uploadToLegacyTextureRegion(commandBufferHandle, "uploadTexture2DSubImage", texture, target, level, xOffset, yOffset, width, height, packedPixels);
        }

        private void uploadLegacyTexture2DSubImage(long commandBufferHandle,
                                                   int target,
                                                   int level,
                                                   int xOffset,
                                                   int yOffset,
                                                   int width,
                                                   int height,
                                                   int format,
                                                   int type,
                                                   java.nio.ByteBuffer pixels) {
            LegacyTextureObject texture = uploadLegacyTexture2DSubImageCommon(
                commandBufferHandle,
                target,
                level,
                xOffset,
                yOffset,
                width,
                height,
                format,
                type
            );

            LegacyTextureFormatInfo formatInfo = LegacyTextureFormatInfo.resolve(
                texture.levels.get(level).internalFormat,
                format,
                type
            );
            java.nio.ByteBuffer packedPixels = normalizePixelData(pixels, formatInfo, width, height);
            uploadToLegacyTextureRegion(commandBufferHandle, "uploadTexture2DSubImage", texture, target, level, xOffset, yOffset, width, height, packedPixels);
        }

        private LegacyTextureObject uploadLegacyTexture2DSubImageCommon(long commandBufferHandle,
                                                                         int target,
                                                                         int level,
                                                                         int xOffset,
                                                                         int yOffset,
                                                                         int width,
                                                                         int height,
                                                                         int format,
                                                                         int type) {
            ensureRecordingCommandBuffer(commandBufferHandle, "uploadTexture2DSubImage");
            if (renderPassRecording) {
                throw new IllegalStateException("uploadTexture2DSubImage requires command recording outside an active render pass");
            }

            if (target == VulkanicAPI.GL_PROXY_TEXTURE_2D) {
                throw new IllegalArgumentException("uploadTexture2DSubImage does not support GL_PROXY_TEXTURE_2D target");
            }

            LegacyTextureObject texture = requireBoundLegacyTexture2D(target, "uploadTexture2DSubImage");
            if (texture.imageHandle == VK10.VK_NULL_HANDLE) {
                throw new IllegalStateException("uploadTexture2DSubImage requires existing texture storage (call uploadTexture2D first)");
            }

            TextureLevelInfo levelInfo = texture.levels.get(level);
            if (levelInfo == null) {
                throw new IllegalArgumentException("No texture storage defined for mip level " + level);
            }

            LegacyTextureFormatInfo expected = LegacyTextureFormatInfo.resolve(levelInfo.internalFormat, format, type);
            if (expected.vkFormat != texture.vkFormat) {
                throw new IllegalArgumentException("uploadTexture2DSubImage format/type does not match existing texture format");
            }

            if (xOffset + width > levelInfo.width || yOffset + height > levelInfo.height) {
                throw new IllegalArgumentException("Sub-image upload exceeds texture bounds at mip level " + level);
            }

            return texture;
        }

        private static final class StagingBuffer {
            private final long bufferHandle;
            private final long memoryHandle;

            private StagingBuffer(long bufferHandle, long memoryHandle) {
                this.bufferHandle = bufferHandle;
                this.memoryHandle = memoryHandle;
            }
        }

        private static final class PendingVulkanResourceDestroy {
            private final long retireAfterGeneration;
            private final Runnable destroyAction;

            private PendingVulkanResourceDestroy(long retireAfterGeneration, Runnable destroyAction) {
                this.retireAfterGeneration = retireAfterGeneration;
                this.destroyAction = destroyAction;
            }
        }

        private void recreateLegacyTextureStorage(LegacyTextureObject texture,
                                                  LegacyTextureFormatInfo formatInfo,
                                                  int width,
                                                  int height,
                                                  int mipLevels) {
            destroyLegacyTextureStorage(texture);

            long imageHandle = VK10.VK_NULL_HANDLE;
            long memoryHandle = VK10.VK_NULL_HANDLE;
            long defaultViewHandle = VK10.VK_NULL_HANDLE;

            try (MemoryStack stack = stackPush()) {
                int arrayLayers = legacyTextureLayerCount(texture);
                boolean cubemapTexture = isLegacyCubemapTarget(texture.target);
                boolean isColorTexture = formatInfo.aspectMask == VK10.VK_IMAGE_ASPECT_COLOR_BIT;
                int legacyImageUsage = VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT
                    | VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT
                    | VK10.VK_IMAGE_USAGE_SAMPLED_BIT
                    | (isColorTexture ? VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT : VK10.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT);
                if (attachmentFeedbackLoopLayoutEnabled) {
                    legacyImageUsage |= EXTAttachmentFeedbackLoopLayout.VK_IMAGE_USAGE_ATTACHMENT_FEEDBACK_LOOP_BIT_EXT;
                }
                VkImageCreateInfo imageCreateInfo = VkImageCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(cubemapTexture ? VK10.VK_IMAGE_CREATE_CUBE_COMPATIBLE_BIT : 0)
                    .imageType(VK10.VK_IMAGE_TYPE_2D)
                    .format(formatInfo.vkFormat)
                    .mipLevels(mipLevels)
                    .arrayLayers(arrayLayers)
                    .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK10.VK_IMAGE_TILING_OPTIMAL)
                    .usage(legacyImageUsage)
                    .sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE)
                    .initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED);
                imageCreateInfo.extent()
                    .width(width)
                    .height(height)
                    .depth(1);

                java.nio.LongBuffer pImage = stack.mallocLong(1);
                checkVk("vkCreateImage(legacy texture)", VK10.vkCreateImage(logicalDevice, imageCreateInfo, null, pImage));
                imageHandle = pImage.get(0);

                VkMemoryRequirements memoryRequirements = VkMemoryRequirements.malloc(stack);
                VK10.vkGetImageMemoryRequirements(logicalDevice, imageHandle, memoryRequirements);

                int preferredMemoryTypeIndex = findMemoryTypeIndex(
                    memoryRequirements.memoryTypeBits(),
                    VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
                );
                int fallbackMemoryTypeIndex = findMemoryTypeIndex(memoryRequirements.memoryTypeBits(), 0);

                if (preferredMemoryTypeIndex < 0 && fallbackMemoryTypeIndex < 0) {
                    throw new IllegalStateException("No device-local memory type available for legacy Vulkan texture allocation");
                }

                int memoryTypeIndex = preferredMemoryTypeIndex >= 0
                    ? preferredMemoryTypeIndex
                    : fallbackMemoryTypeIndex;

                VkMemoryAllocateInfo memoryAllocateInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default()
                    .allocationSize(memoryRequirements.size())
                    .memoryTypeIndex(memoryTypeIndex);

                java.nio.LongBuffer pMemory = stack.mallocLong(1);
                int allocationResult = VK10.vkAllocateMemory(logicalDevice, memoryAllocateInfo, null, pMemory);
                if (allocationResult == VK10.VK_ERROR_OUT_OF_DEVICE_MEMORY
                    && preferredMemoryTypeIndex >= 0
                    && fallbackMemoryTypeIndex >= 0
                    && fallbackMemoryTypeIndex != preferredMemoryTypeIndex) {
                    LOGGER.warn(
                        "Device-local legacy texture allocation failed for id={} {}x{} mipLevels={} vkFormat={} (size={} bytes); retrying with memoryTypeIndex={}",
                        texture.id,
                        width,
                        height,
                        mipLevels,
                        formatInfo.vkFormat,
                        memoryRequirements.size(),
                        fallbackMemoryTypeIndex
                    );
                    memoryAllocateInfo.memoryTypeIndex(fallbackMemoryTypeIndex);
                    allocationResult = VK10.vkAllocateMemory(logicalDevice, memoryAllocateInfo, null, pMemory);
                }
                if (allocationResult != VK10.VK_SUCCESS) {
                    throw new IllegalStateException(
                        "vkAllocateMemory(legacy texture) failed with VkResult=" + allocationResult
                            + " id=" + texture.id
                            + " width=" + width
                            + " height=" + height
                            + " mipLevels=" + mipLevels
                            + " vkFormat=" + formatInfo.vkFormat
                            + " sizeBytes=" + memoryRequirements.size()
                            + " memoryTypeBits=0x" + Integer.toHexString(memoryRequirements.memoryTypeBits())
                            + " preferredMemoryTypeIndex=" + preferredMemoryTypeIndex
                            + " fallbackMemoryTypeIndex=" + fallbackMemoryTypeIndex
                    );
                }
                memoryHandle = pMemory.get(0);

                checkVk("vkBindImageMemory(legacy texture)",
                    VK10.vkBindImageMemory(logicalDevice, imageHandle, memoryHandle, 0));

                defaultViewHandle = createVkImageView(
                    stack,
                    imageHandle,
                    formatInfo.vkFormat,
                    formatInfo.aspectMask,
                    0,
                    mipLevels,
                    arrayLayers,
                    cubemapTexture
                );

                texture.imageHandle = imageHandle;
                texture.memoryHandle = memoryHandle;
                texture.defaultViewHandle = defaultViewHandle;
                texture.vkFormat = formatInfo.vkFormat;
                texture.aspectMask = formatInfo.aspectMask;
                texture.feedbackLoopCapable = attachmentFeedbackLoopLayoutEnabled;
                texture.pixelBytes = formatInfo.pixelBytes;
                texture.currentLayout = VK10.VK_IMAGE_LAYOUT_UNDEFINED;
                texture.mipLevels = mipLevels;
                texture.width = width;
                texture.height = height;
                texture.levels.clear();
                texture.levelLayouts.clear();
                imageStateTracker.registerTexture(
                    texture.id,
                    imageHandle,
                    formatInfo.aspectMask,
                    mipLevels,
                    arrayLayers,
                    texture.feedbackLoopCapable,
                    VK10.VK_IMAGE_LAYOUT_UNDEFINED
                );
            } catch (RuntimeException exception) {
                if (logicalDevice != null) {
                    if (defaultViewHandle != VK10.VK_NULL_HANDLE) {
                        VK10.vkDestroyImageView(logicalDevice, defaultViewHandle, null);
                    }
                    if (imageHandle != VK10.VK_NULL_HANDLE) {
                        VK10.vkDestroyImage(logicalDevice, imageHandle, null);
                    }
                    if (memoryHandle != VK10.VK_NULL_HANDLE) {
                        VK10.vkFreeMemory(logicalDevice, memoryHandle, null);
                    }
                }
                throw exception;
            }
        }

        private void destroyLegacyManagedImageViews(LegacyTextureObject texture) {
            if (texture.managedViewHandles.isEmpty()) {
                return;
            }

            java.util.List<Long> managedViews = new ArrayList<>(texture.managedViewHandles);
            texture.managedViewHandles.clear();
            LOGGER.info(
                "Invalidating {} legacy managed image view(s) for texId={} before storage teardown.",
                managedViews.size(),
                texture.id
            );
            managedViews.forEach(this::destroyManagedImageView);
        }

        private Runnable createLegacyManagedImageViewCloseAction(LegacyTextureObject texture, long viewHandle) {
            return () -> {
                texture.managedViewHandles.remove(viewHandle);
                destroyManagedImageView(viewHandle);
            };
        }

        private void destroyLegacyTextureStorage(LegacyTextureObject texture) {
            destroyLegacyManagedImageViews(texture);
            if (logicalDevice != null) {
                long defaultViewHandle = texture.defaultViewHandle;
                long imageHandle = texture.imageHandle;
                long memoryHandle = texture.memoryHandle;
                enqueueVulkanResourceDestroy(
                    () -> destroyLegacyTextureStorageHandles(defaultViewHandle, imageHandle, memoryHandle)
                );
            }

            texture.imageHandle = VK10.VK_NULL_HANDLE;
            texture.memoryHandle = VK10.VK_NULL_HANDLE;
            texture.defaultViewHandle = VK10.VK_NULL_HANDLE;
            texture.currentLayout = VK10.VK_IMAGE_LAYOUT_UNDEFINED;
            texture.levels.clear();
            texture.levelLayouts.clear();
            imageStateTracker.clearTextureStorage(texture.id);
        }

        private boolean hasPotentiallyPendingGpuWork() {
            if (submittedWorkGeneration > completedWorkGeneration) {
                return true;
            }
            if (renderPassRecording || commandBufferRecording || immediateSubmitInFlight || frameInProgress) {
                return true;
            }
            for (boolean inFlight : immediateSubmitSlotsInFlight) {
                if (inFlight) {
                    return true;
                }
            }
            for (boolean recording : frameCommandBufferRecording) {
                if (recording) {
                    return true;
                }
            }
            return false;
        }

        private long retireAfterGeneration() {
            long retireAfterGeneration = submittedWorkGeneration;
            if (frameInProgress) {
                retireAfterGeneration = Math.max(retireAfterGeneration, reservedFrameWorkGenerations[currentFrameSyncIndex]);
            }
            if (commandBufferRecording && recordingImmediateSubmitSlot >= 0) {
                retireAfterGeneration = Math.max(retireAfterGeneration, reservedImmediateWorkGenerations[recordingImmediateSubmitSlot]);
            }
            return retireAfterGeneration;
        }

        private void enqueueVulkanResourceDestroy(Runnable destroyAction) {
            if (logicalDevice == null) {
                return;
            }
            if (!hasPotentiallyPendingGpuWork()) {
                destroyAction.run();
                return;
            }
            pendingVulkanResourceDestroys.add(new PendingVulkanResourceDestroy(retireAfterGeneration(), destroyAction));
        }

        private long reserveWorkGeneration() {
            return ++submittedWorkGeneration;
        }

        private void registerSubmittedWork(long fenceHandle, long generation) {
            if (fenceHandle == VK10.VK_NULL_HANDLE) {
                return;
            }
            if (generation <= 0) {
                generation = reserveWorkGeneration();
            }
            submittedWorkGenerationByFence.put(fenceHandle, generation);
        }

        private void markFenceComplete(long fenceHandle) {
            if (fenceHandle == VK10.VK_NULL_HANDLE) {
                return;
            }
            Long generation = submittedWorkGenerationByFence.remove(fenceHandle);
            if (generation != null && generation > completedWorkGeneration) {
                completedWorkGeneration = generation;
            }
            flushPendingVulkanResourceDestroys(false);
        }

        private void markAllSubmittedWorkComplete() {
            completedWorkGeneration = submittedWorkGeneration;
            submittedWorkGenerationByFence.clear();
            java.util.Arrays.fill(reservedFrameWorkGenerations, 0L);
            java.util.Arrays.fill(reservedImmediateWorkGenerations, 0L);
            flushPendingVulkanResourceDestroys(false);
        }

        private void destroyLegacyTextureStorageHandles(long viewHandle, long imageHandle, long memoryHandle) {
            if (logicalDevice == null) {
                return;
            }
            if (viewHandle != VK10.VK_NULL_HANDLE) {
                VK10.vkDestroyImageView(logicalDevice, viewHandle, null);
            }
            if (imageHandle != VK10.VK_NULL_HANDLE) {
                VK10.vkDestroyImage(logicalDevice, imageHandle, null);
            }
            if (memoryHandle != VK10.VK_NULL_HANDLE) {
                VK10.vkFreeMemory(logicalDevice, memoryHandle, null);
            }
        }

        private void flushPendingVulkanResourceDestroys(boolean force) {
            if (logicalDevice == null) {
                pendingVulkanResourceDestroys.clear();
                return;
            }
            if (force) {
                completedWorkGeneration = submittedWorkGeneration;
                submittedWorkGenerationByFence.clear();
            }

            java.util.List<PendingVulkanResourceDestroy> ready = new ArrayList<>();
            synchronized (pendingVulkanResourceDestroys) {
                if (pendingVulkanResourceDestroys.isEmpty()) {
                    return;
                }
                java.util.Iterator<PendingVulkanResourceDestroy> iterator = pendingVulkanResourceDestroys.iterator();
                while (iterator.hasNext()) {
                    PendingVulkanResourceDestroy pending = iterator.next();
                    if (force || pending.retireAfterGeneration <= completedWorkGeneration) {
                        ready.add(pending);
                        iterator.remove();
                    }
                }
            }

            for (PendingVulkanResourceDestroy pending : ready) {
                pending.destroyAction.run();
            }
        }

        private int trackedLayoutForLevel(LegacyTextureObject texture, int level) {
            int mirrorLayout = texture.levelLayouts.getOrDefault(level, VK10.VK_IMAGE_LAYOUT_UNDEFINED);
            int fallback = mirrorLayout != VK10.VK_IMAGE_LAYOUT_UNDEFINED
                ? mirrorLayout
                : (level == 0 ? texture.currentLayout : VK10.VK_IMAGE_LAYOUT_UNDEFINED);
            return imageStateTracker.layoutFor(texture.id, level, fallback);
        }

        private void trackLayoutForLevel(LegacyTextureObject texture, int level, int layout) {
            imageStateTracker.recordLayout(texture.id, level, layout);
            texture.levelLayouts.put(level, layout);
            if (level == 0) {
                texture.currentLayout = layout;
            }
        }

        private static int legacyTextureLayerCount(LegacyTextureObject texture) {
            return isLegacyCubemapTarget(texture.target) ? 6 : 1;
        }

        private static int cubemapLayerIndexForTarget(int target) {
            return isLegacyCubemapFaceTarget(target) ? target - 0x8515 : 0;
        }

        private StagingBuffer createStagingBuffer(java.nio.ByteBuffer data) {
            long bufferHandle = VK10.VK_NULL_HANDLE;
            long memoryHandle = VK10.VK_NULL_HANDLE;

            int size = data.remaining();
            if (size <= 0) {
                throw new IllegalArgumentException("Staging upload requires non-empty pixel data");
            }

            try (MemoryStack stack = stackPush()) {
                VkBufferCreateInfo bufferCreateInfo = VkBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .size(size)
                    .usage(VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT)
                    .sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);

                java.nio.LongBuffer pBuffer = stack.mallocLong(1);
                checkVk("vkCreateBuffer(staging)", VK10.vkCreateBuffer(logicalDevice, bufferCreateInfo, null, pBuffer));
                bufferHandle = pBuffer.get(0);

                VkMemoryRequirements memoryRequirements = VkMemoryRequirements.malloc(stack);
                VK10.vkGetBufferMemoryRequirements(logicalDevice, bufferHandle, memoryRequirements);

                int memoryTypeIndex = findMemoryTypeIndex(
                    memoryRequirements.memoryTypeBits(),
                    VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
                );
                if (memoryTypeIndex < 0) {
                    throw new IllegalStateException("No host-visible/coherent memory type for staging upload buffer");
                }

                VkMemoryAllocateInfo memoryAllocateInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default()
                    .allocationSize(memoryRequirements.size())
                    .memoryTypeIndex(memoryTypeIndex);

                java.nio.LongBuffer pMemory = stack.mallocLong(1);
                checkVk("vkAllocateMemory(staging)", VK10.vkAllocateMemory(logicalDevice, memoryAllocateInfo, null, pMemory));
                memoryHandle = pMemory.get(0);

                checkVk("vkBindBufferMemory(staging)",
                    VK10.vkBindBufferMemory(logicalDevice, bufferHandle, memoryHandle, 0));

                org.lwjgl.PointerBuffer mappedPointer = stack.mallocPointer(1);
                checkVk("vkMapMemory(staging)",
                    VK10.vkMapMemory(logicalDevice, memoryHandle, 0, size, 0, mappedPointer));

                java.nio.ByteBuffer mapped = MemoryUtil.memByteBuffer(mappedPointer.get(0), size);
                mapped.put(data.duplicate());
                VK10.vkUnmapMemory(logicalDevice, memoryHandle);

                return new StagingBuffer(bufferHandle, memoryHandle);
            } catch (RuntimeException exception) {
                if (logicalDevice != null) {
                    if (bufferHandle != VK10.VK_NULL_HANDLE) {
                        VK10.vkDestroyBuffer(logicalDevice, bufferHandle, null);
                    }
                    if (memoryHandle != VK10.VK_NULL_HANDLE) {
                        VK10.vkFreeMemory(logicalDevice, memoryHandle, null);
                    }
                }
                throw exception;
            }
        }

        private void destroyStagingBuffer(StagingBuffer stagingBuffer) {
            if (logicalDevice == null || stagingBuffer == null) {
                return;
            }
            if (stagingBuffer.bufferHandle != VK10.VK_NULL_HANDLE) {
                VK10.vkDestroyBuffer(logicalDevice, stagingBuffer.bufferHandle, null);
            }
            if (stagingBuffer.memoryHandle != VK10.VK_NULL_HANDLE) {
                VK10.vkFreeMemory(logicalDevice, stagingBuffer.memoryHandle, null);
            }
        }

        private void deferStagingBufferDestroy(StagingBuffer stagingBuffer) {
            if (stagingBuffer != null) {
                trackTransientStagingBuffer(stagingBuffer);
            }
        }

        private void transitionImageLayout(LegacyTextureObject texture,
                                           int oldLayout,
                                           int newLayout,
                                           int baseMipLevel,
                                           int levelCount) {
            VkCommandBuffer recordingCommandBuffer = requireRecordingCommandBuffer(
                primaryCommandBuffer.address(),
                "transitionImageLayout"
            );
            transitionImageLayout(
                recordingCommandBuffer,
                texture.imageHandle,
                texture.aspectMask,
                oldLayout,
                newLayout,
                baseMipLevel,
                levelCount,
                legacyTextureLayerCount(texture)
            );
        }

        private void transitionImageLayout(VkCommandBuffer commandBuffer,
                                            long imageHandle,
                                            int aspectMask,
                                            int oldLayout,
                                            int newLayout,
                                            int baseMipLevel,
                                            int levelCount,
                                            int layerCount) {
            if (imageHandle == VK10.VK_NULL_HANDLE) {
                throw new IllegalStateException("transitionImageLayout requires a valid VkImage handle");
            }
            if (commandBuffer == null) {
                throw new IllegalStateException("transitionImageLayout requires a non-null recording command buffer");
            }
            if (baseMipLevel < 0 || levelCount <= 0 || layerCount <= 0) {
                throw new IllegalArgumentException(
                    "transitionImageLayout requires non-negative baseMipLevel and positive levelCount/layerCount"
                );
            }
            if (oldLayout == newLayout) {
                return;
            }

            try (MemoryStack stack = stackPush()) {
                VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
                barrier.get(0)
                    .sType$Default()
                    .oldLayout(oldLayout)
                    .newLayout(newLayout)
                    .srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                    .image(imageHandle);
                barrier.get(0).subresourceRange()
                    .aspectMask(aspectMask)
                    .baseMipLevel(baseMipLevel)
                    .levelCount(levelCount)
                    .baseArrayLayer(0)
                    .layerCount(layerCount);

                barrier.get(0)
                    .srcAccessMask(accessMaskForLayout(oldLayout))
                    .dstAccessMask(accessMaskForLayout(newLayout));

                VK10.vkCmdPipelineBarrier(
                    commandBuffer,
                    stageMaskForLayout(oldLayout),
                    stageMaskForLayout(newLayout),
                    0,
                    null,
                    null,
                    barrier
                );
            }
        }

        private static int accessMaskForLayout(int layout) {
            return switch (layout) {
                case VK10.VK_IMAGE_LAYOUT_UNDEFINED -> 0;
                case VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL -> VK10.VK_ACCESS_TRANSFER_READ_BIT;
                case VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL -> VK10.VK_ACCESS_TRANSFER_WRITE_BIT;
                case VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL ->
                    VK10.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
                case VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL ->
                    VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
                case VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL -> VK10.VK_ACCESS_SHADER_READ_BIT;
                case VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL -> VK10.VK_ACCESS_SHADER_READ_BIT;
                case VK10.VK_IMAGE_LAYOUT_GENERAL -> VK10.VK_ACCESS_MEMORY_READ_BIT | VK10.VK_ACCESS_MEMORY_WRITE_BIT;
                case KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR -> 0;
                default -> {
                    if (layout == EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT) {
                        yield VK10.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                            | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT
                            | VK10.VK_ACCESS_SHADER_READ_BIT;
                    }
                    yield VK10.VK_ACCESS_MEMORY_READ_BIT | VK10.VK_ACCESS_MEMORY_WRITE_BIT;
                }
            };
        }

        private static int stageMaskForLayout(int layout) {
            return switch (layout) {
                case VK10.VK_IMAGE_LAYOUT_UNDEFINED -> VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
                case VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL -> VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;
                case VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL -> VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
                case VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL,
                    VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL ->
                    VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT | VK10.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT;
                case VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL ->
                    VK10.VK_PIPELINE_STAGE_ALL_GRAPHICS_BIT | VK10.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT;
                case KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR -> VK10.VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT;
                default -> {
                    if (layout == EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT) {
                        yield VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
                            | VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT
                            | VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT
                            | VK10.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT;
                    }
                    yield VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
                }
            };
        }

        private static int imageLayoutForUsage(
            VulkanicResourceUsage usage,
            boolean depth,
            boolean feedbackLoopCapable,
            int inferredLayout
        ) {
            Objects.requireNonNull(usage, "usage must not be null");
            if (usage == VulkanicResourceUsage.INFERRED) {
                return inferredLayout;
            }

            if (feedbackLoopCapable
                && (usage == VulkanicResourceUsage.COLOR_ATTACHMENT_WRITE
                    || usage == VulkanicResourceUsage.DEPTH_ATTACHMENT_WRITE
                    || usage == VulkanicResourceUsage.SAMPLED_READ
                    || usage == VulkanicResourceUsage.ATTACHMENT_FEEDBACK_LOOP)) {
                return EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT;
            }

            return switch (usage) {
                case COLOR_ATTACHMENT_WRITE -> VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
                case DEPTH_ATTACHMENT_WRITE -> VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
                case SAMPLED_READ -> depth
                    ? VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL
                    : VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
                case TRANSFER_SRC -> VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
                case TRANSFER_DST -> VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
                case STORAGE_READ_WRITE -> VK10.VK_IMAGE_LAYOUT_GENERAL;
                case PRESENT -> KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
                case ATTACHMENT_FEEDBACK_LOOP -> EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT;
                case INFERRED -> inferredLayout;
            };
        }

        private void ensureTextureLayoutBeforeRenderPass(
            LegacyTextureObject texture,
            int targetLayout,
            VulkanicResourceUsage usage
        ) {
            if (usage == VulkanicResourceUsage.INFERRED
                || texture == null
                || targetLayout == VK10.VK_IMAGE_LAYOUT_UNDEFINED) {
                return;
            }

            int trackedLayout = trackedLayoutForLevel(texture, 0);
            if (trackedLayout == VK10.VK_IMAGE_LAYOUT_UNDEFINED || trackedLayout == targetLayout) {
                return;
            }

            transitionImageLayout(texture, trackedLayout, targetLayout, 0, 1);
            trackLayoutForLevel(texture, 0, targetLayout);
        }

        private void uploadToLegacyTextureRegion(long commandBufferHandle,
                                                 String operation,
                                                 LegacyTextureObject texture,
                                                 int target,
                                                 int level,
                                                 int xOffset,
                                                 int yOffset,
                                                 int width,
                                                 int height,
                                                 java.nio.ByteBuffer pixels) {
            VkCommandBuffer commandBuffer = requireRecordingCommandBuffer(commandBufferHandle, operation);
            StagingBuffer stagingBuffer = createStagingBuffer(pixels);
            try {
                int oldLayout = trackedLayoutForLevel(texture, level);
                transitionImageLayout(
                    commandBuffer,
                    texture.imageHandle,
                    texture.aspectMask,
                    oldLayout,
                    VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    level,
                    1,
                    legacyTextureLayerCount(texture)
                );

                try (MemoryStack stack = stackPush()) {
                    VkBufferImageCopy.Buffer regions = VkBufferImageCopy.calloc(1, stack);
                    regions.get(0)
                        .bufferOffset(0L)
                        .bufferRowLength(0)
                        .bufferImageHeight(0);
                    regions.get(0).imageSubresource()
                        .aspectMask(texture.aspectMask)
                        .mipLevel(level)
                        .baseArrayLayer(cubemapLayerIndexForTarget(target))
                        .layerCount(1);
                    regions.get(0).imageOffset().set(xOffset, yOffset, 0);
                    regions.get(0).imageExtent().set(width, height, 1);

                    VK10.vkCmdCopyBufferToImage(
                        commandBuffer,
                        stagingBuffer.bufferHandle,
                        texture.imageHandle,
                        VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        regions
                    );
                }

                int finalLayout = preferredIdleLayout(texture);
                transitionImageLayout(
                    commandBuffer,
                    texture.imageHandle,
                    texture.aspectMask,
                    VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    finalLayout,
                    level,
                    1,
                    legacyTextureLayerCount(texture)
                );
                trackLayoutForLevel(texture, level, finalLayout);
            } finally {
                deferStagingBufferDestroy(stagingBuffer);
            }
        }

        private int preferredIdleLayout(LegacyTextureObject texture) {
            if (texture.feedbackLoopCapable) {
                return VulkanImageUse.FEEDBACK_LOOP.vkLayout();
            }
            return texture.aspectMask == VK10.VK_IMAGE_ASPECT_COLOR_BIT
                ? VulkanImageUse.SAMPLED_COLOR.vkLayout()
                : VulkanImageUse.SAMPLED_DEPTH.vkLayout();
        }

        private void clearLegacyColorTexture(
            long commandBufferHandle,
            String operation,
            int textureId,
            float r,
            float g,
            float b,
            float a
        ) {
            ensureRecordingCommandBuffer(commandBufferHandle, operation);
            if (renderPassRecording) {
                throw new IllegalStateException(operation + " requires command recording outside an active render pass");
            }

            LegacyTextureObject texture = requireLegacyTexture(textureId);
            if (texture.imageHandle == VK10.VK_NULL_HANDLE || texture.aspectMask != VK10.VK_IMAGE_ASPECT_COLOR_BIT) {
                return;
            }

            VkCommandBuffer commandBuffer = requireRecordingCommandBuffer(commandBufferHandle, operation);
            int oldLayout = trackedLayoutForLevel(texture, 0);
            if (oldLayout == VK10.VK_IMAGE_LAYOUT_UNDEFINED) {
                oldLayout = preferredIdleLayout(texture);
            }

            transitionImageLayout(
                commandBuffer,
                texture.imageHandle,
                texture.aspectMask,
                oldLayout,
                VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                0,
                1,
                legacyTextureLayerCount(texture)
            );

            try (MemoryStack stack = stackPush()) {
                VkClearColorValue clear = VkClearColorValue.calloc(stack);
                clear.float32(0, r).float32(1, g).float32(2, b).float32(3, a);

                VkImageSubresourceRange.Buffer range = VkImageSubresourceRange.calloc(1, stack);
                range.get(0)
                    .aspectMask(texture.aspectMask)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(legacyTextureLayerCount(texture));

                VK10.vkCmdClearColorImage(
                    commandBuffer,
                    texture.imageHandle,
                    VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    clear,
                    range
                );
            }

            int finalLayout = preferredIdleLayout(texture);
            transitionImageLayout(
                commandBuffer,
                texture.imageHandle,
                texture.aspectMask,
                VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                finalLayout,
                0,
                1,
                legacyTextureLayerCount(texture)
            );
            trackLayoutForLevel(texture, 0, finalLayout);
        }

        private void clearLegacyDepthTexture(
            long commandBufferHandle,
            String operation,
            int textureId,
            float depth
        ) {
            ensureRecordingCommandBuffer(commandBufferHandle, operation);
            if (renderPassRecording) {
                throw new IllegalStateException(operation + " requires command recording outside an active render pass");
            }

            LegacyTextureObject texture = requireLegacyTexture(textureId);
            if (texture.imageHandle == VK10.VK_NULL_HANDLE
                || (texture.aspectMask & VK10.VK_IMAGE_ASPECT_DEPTH_BIT) == 0) {
                return;
            }

            VkCommandBuffer commandBuffer = requireRecordingCommandBuffer(commandBufferHandle, operation);
            int oldLayout = trackedLayoutForLevel(texture, 0);
            if (oldLayout == VK10.VK_IMAGE_LAYOUT_UNDEFINED) {
                oldLayout = preferredIdleLayout(texture);
            }

            transitionImageLayout(
                commandBuffer,
                texture.imageHandle,
                texture.aspectMask,
                oldLayout,
                VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                0,
                1,
                legacyTextureLayerCount(texture)
            );

            try (MemoryStack stack = stackPush()) {
                VkClearDepthStencilValue clear = VkClearDepthStencilValue.calloc(stack)
                    .depth(depth)
                    .stencil(0);

                VkImageSubresourceRange.Buffer range = VkImageSubresourceRange.calloc(1, stack);
                range.get(0)
                    .aspectMask(texture.aspectMask & (VK10.VK_IMAGE_ASPECT_DEPTH_BIT | VK10.VK_IMAGE_ASPECT_STENCIL_BIT))
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(legacyTextureLayerCount(texture));

                VK10.vkCmdClearDepthStencilImage(
                    commandBuffer,
                    texture.imageHandle,
                    VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    clear,
                    range
                );
            }

            int finalLayout = preferredIdleLayout(texture);
            transitionImageLayout(
                commandBuffer,
                texture.imageHandle,
                texture.aspectMask,
                VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                finalLayout,
                0,
                1,
                legacyTextureLayerCount(texture)
            );
            trackLayoutForLevel(texture, 0, finalLayout);
        }

        private void copyLegacyTextureRegion(long commandBufferHandle,
                                             String operation,
                                             int sourceTextureId,
                                             int sourceLevel,
                                             int sourceX,
                                             int sourceY,
                                             int sourceZ,
                                             int destTextureId,
                                             int destLevel,
                                             int destX,
                                             int destY,
                                             int destZ,
                                             int width,
                                             int height,
                                             int depth) {
            ensureRecordingCommandBuffer(commandBufferHandle, operation);
            if (renderPassRecording) {
                throw new IllegalStateException(operation + " requires command recording outside an active render pass");
            }
            if (width <= 0 || height <= 0 || depth <= 0) {
                return;
            }

            LegacyTextureObject sourceTexture = requireLegacyTexture(sourceTextureId);
            LegacyTextureObject destTexture = requireLegacyTexture(destTextureId);
            if (sourceTexture.imageHandle == VK10.VK_NULL_HANDLE || destTexture.imageHandle == VK10.VK_NULL_HANDLE) {
                return;
            }
            if (sourceTexture.aspectMask != destTexture.aspectMask) {
                throw new IllegalArgumentException(operation + " requires matching source and destination texture aspects");
            }

            VkCommandBuffer commandBuffer = requireRecordingCommandBuffer(commandBufferHandle, operation);
            int layerCount = Math.min(legacyTextureLayerCount(sourceTexture), legacyTextureLayerCount(destTexture));

            int sourceOriginalLayout = trackedLayoutForLevel(sourceTexture, sourceLevel);
            if (sourceOriginalLayout == VK10.VK_IMAGE_LAYOUT_UNDEFINED) {
                sourceOriginalLayout = preferredIdleLayout(sourceTexture);
            }
            if (sourceOriginalLayout != VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL) {
                transitionImageLayout(
                    commandBuffer,
                    sourceTexture.imageHandle,
                    sourceTexture.aspectMask,
                    sourceOriginalLayout,
                    VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    sourceLevel,
                    1,
                    layerCount
                );
            }

            int destOriginalLayout = trackedLayoutForLevel(destTexture, destLevel);
            if (destOriginalLayout == VK10.VK_IMAGE_LAYOUT_UNDEFINED) {
                destOriginalLayout = preferredIdleLayout(destTexture);
            }
            if (destOriginalLayout != VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
                transitionImageLayout(
                    commandBuffer,
                    destTexture.imageHandle,
                    destTexture.aspectMask,
                    destOriginalLayout,
                    VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    destLevel,
                    1,
                    layerCount
                );
            }

            try (MemoryStack stack = stackPush()) {
                VkImageCopy.Buffer region = VkImageCopy.calloc(1, stack);
                region.get(0).srcSubresource()
                    .aspectMask(sourceTexture.aspectMask)
                    .mipLevel(sourceLevel)
                    .baseArrayLayer(0)
                    .layerCount(layerCount);
                region.get(0).srcOffset().set(
                    sourceX,
                    toVulkanImageRegionY(sourceTexture, sourceLevel, sourceY, height),
                    sourceZ
                );
                region.get(0).dstSubresource()
                    .aspectMask(destTexture.aspectMask)
                    .mipLevel(destLevel)
                    .baseArrayLayer(0)
                    .layerCount(layerCount);
                region.get(0).dstOffset().set(
                    destX,
                    toVulkanImageRegionY(destTexture, destLevel, destY, height),
                    destZ
                );
                region.get(0).extent().set(width, height, depth);

                VK10.vkCmdCopyImage(
                    commandBuffer,
                    sourceTexture.imageHandle,
                    VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    destTexture.imageHandle,
                    VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    region
                );
            }

            if (sourceOriginalLayout != VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL) {
                transitionImageLayout(
                    commandBuffer,
                    sourceTexture.imageHandle,
                    sourceTexture.aspectMask,
                    VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    sourceOriginalLayout,
                    sourceLevel,
                    1,
                    layerCount
                );
            }
            if (destOriginalLayout != VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
                transitionImageLayout(
                    commandBuffer,
                    destTexture.imageHandle,
                    destTexture.aspectMask,
                    VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    destOriginalLayout,
                    destLevel,
                    1,
                    layerCount
                );
            }

            trackLayoutForLevel(sourceTexture, sourceLevel, sourceOriginalLayout);
            trackLayoutForLevel(destTexture, destLevel, destOriginalLayout);
        }

        private void readLegacyTextureRegionToBoundPixelPackBuffer(long commandBufferHandle,
                                                                   String operation,
                                                                   int sourceTextureId,
                                                                   int sourceX,
                                                                   int sourceY,
                                                                   int width,
                                                                   int height,
                                                                   long bufferOffset) {
            ensureRecordingCommandBuffer(commandBufferHandle, operation);
            if (renderPassRecording) {
                throw new IllegalStateException(operation + " requires command recording outside an active render pass");
            }
            if (width <= 0 || height <= 0) {
                return;
            }

            LegacyTextureObject sourceTexture = requireLegacyTexture(sourceTextureId);
            if (sourceTexture.imageHandle == VK10.VK_NULL_HANDLE) {
                return;
            }
            if (sourceTexture.aspectMask != VK10.VK_IMAGE_ASPECT_COLOR_BIT) {
                throw new UnsupportedOperationException(operation + " currently supports only color textures");
            }

            VulkanBuffer destinationBuffer = requireAllocatedLegacyBuffer(requireBoundLegacyBuffer(VulkanicAPI.GL_PIXEL_PACK_BUFFER), operation);
            long requiredBytes = (long) width * height * Math.max(1, sourceTexture.pixelBytes);
            if (bufferOffset < 0L || bufferOffset + requiredBytes > destinationBuffer.size()) {
                throw new IllegalArgumentException(
                    operation + " destination range [" + bufferOffset + ", " + (bufferOffset + requiredBytes)
                        + ") exceeds pixel-pack buffer size " + destinationBuffer.size()
                );
            }

            VkCommandBuffer commandBuffer = requireRecordingCommandBuffer(commandBufferHandle, operation);
            int sourceOriginalLayout = trackedLayoutForLevel(sourceTexture, 0);
            if (sourceOriginalLayout == VK10.VK_IMAGE_LAYOUT_UNDEFINED) {
                sourceOriginalLayout = preferredIdleLayout(sourceTexture);
            }
            if (sourceOriginalLayout != VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL) {
                transitionImageLayout(
                    commandBuffer,
                    sourceTexture.imageHandle,
                    sourceTexture.aspectMask,
                    sourceOriginalLayout,
                    VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    0,
                    1,
                    legacyTextureLayerCount(sourceTexture)
                );
            }

            try (MemoryStack stack = stackPush()) {
                VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1, stack);
                region.get(0)
                    .bufferOffset(bufferOffset)
                    .bufferRowLength(0)
                    .bufferImageHeight(0);
                region.get(0).imageSubresource()
                    .aspectMask(sourceTexture.aspectMask)
                    .mipLevel(0)
                    .baseArrayLayer(0)
                    .layerCount(1);
                region.get(0).imageOffset().set(sourceX, sourceY, 0);
                region.get(0).imageExtent().set(width, height, 1);

                VK10.vkCmdCopyImageToBuffer(
                    commandBuffer,
                    sourceTexture.imageHandle,
                    VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    destinationBuffer.getVkBufferHandle(),
                    region
                );
            }

            if (sourceOriginalLayout != VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL) {
                transitionImageLayout(
                    commandBuffer,
                    sourceTexture.imageHandle,
                    sourceTexture.aspectMask,
                    VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    sourceOriginalLayout,
                    0,
                    1,
                    legacyTextureLayerCount(sourceTexture)
                );
            }

            trackLayoutForLevel(sourceTexture, 0, sourceOriginalLayout);
        }

        private void copyToBoundLegacyTexture2D(long commandBufferHandle,
                                                String operation,
                                                int target,
                                                int level,
                                                int internalFormat,
                                                int sourceTextureId,
                                                int sourceX,
                                                int sourceY,
                                                int width,
                                                int height) {
            LegacyTextureObject destinationTexture = requireBoundLegacyTexture2D(target, operation);
            destinationTexture.levels.put(level, new TextureLevelInfo(width, height, internalFormat));
            copyLegacyTextureRegion(
                commandBufferHandle,
                operation,
                sourceTextureId,
                0,
                sourceX,
                sourceY,
                0,
                destinationTexture.id,
                level,
                0,
                0,
                0,
                width,
                height,
                1
            );
        }

        private void copyToBoundLegacyTextureSubImage2D(long commandBufferHandle,
                                                        String operation,
                                                        int target,
                                                        int level,
                                                        int xOffset,
                                                        int yOffset,
                                                        int sourceTextureId,
                                                        int sourceX,
                                                        int sourceY,
                                                        int width,
                                                        int height) {
            LegacyTextureObject destinationTexture = requireBoundLegacyTexture2D(target, operation);
            copyLegacyTextureRegion(
                commandBufferHandle,
                operation,
                sourceTextureId,
                0,
                sourceX,
                sourceY,
                0,
                destinationTexture.id,
                level,
                xOffset,
                yOffset,
                0,
                width,
                height,
                1
            );
        }

        private void blitLegacyTextureRegion(long commandBufferHandle,
                                             String operation,
                                             int sourceTextureId,
                                             int sourceLevel,
                                             int srcX0,
                                             int srcY0,
                                             int srcX1,
                                             int srcY1,
                                             int destTextureId,
                                             int destLevel,
                                             int dstX0,
                                             int dstY0,
                                             int dstX1,
                                             int dstY1,
                                             int mask,
                                             int filter) {
            ensureRecordingCommandBuffer(commandBufferHandle, operation);
            if (renderPassRecording) {
                throw new IllegalStateException(operation + " requires command recording outside an active render pass");
            }
            if (filter != GL_NEAREST) {
                return;
            }

            LegacyTextureObject sourceTexture = requireLegacyTexture(sourceTextureId);
            LegacyTextureObject destTexture = requireLegacyTexture(destTextureId);
            if (sourceTexture.imageHandle == VK10.VK_NULL_HANDLE || destTexture.imageHandle == VK10.VK_NULL_HANDLE) {
                return;
            }
            if (sourceTexture.aspectMask != destTexture.aspectMask) {
                throw new IllegalArgumentException(operation + " requires matching source and destination texture aspects");
            }

            VkCommandBuffer commandBuffer = requireRecordingCommandBuffer(commandBufferHandle, operation);
            int layerCount = Math.min(legacyTextureLayerCount(sourceTexture), legacyTextureLayerCount(destTexture));

            int sourceOriginalLayout = trackedLayoutForLevel(sourceTexture, sourceLevel);
            if (sourceOriginalLayout == VK10.VK_IMAGE_LAYOUT_UNDEFINED) {
                sourceOriginalLayout = preferredIdleLayout(sourceTexture);
            }
            if (sourceOriginalLayout != VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL) {
                transitionImageLayout(
                    commandBuffer,
                    sourceTexture.imageHandle,
                    sourceTexture.aspectMask,
                    sourceOriginalLayout,
                    VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    sourceLevel,
                    1,
                    layerCount
                );
            }

            int destOriginalLayout = trackedLayoutForLevel(destTexture, destLevel);
            if (destOriginalLayout == VK10.VK_IMAGE_LAYOUT_UNDEFINED) {
                destOriginalLayout = preferredIdleLayout(destTexture);
            }
            if (destOriginalLayout != VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
                transitionImageLayout(
                    commandBuffer,
                    destTexture.imageHandle,
                    destTexture.aspectMask,
                    destOriginalLayout,
                    VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    destLevel,
                    1,
                    layerCount
                );
            }

            try (MemoryStack stack = stackPush()) {
                VkImageBlit.Buffer blit = VkImageBlit.calloc(1, stack);
                blit.get(0).srcSubresource()
                    .aspectMask(sourceTexture.aspectMask)
                    .mipLevel(sourceLevel)
                    .baseArrayLayer(0)
                    .layerCount(layerCount);
                blit.get(0).srcOffsets(0).set(srcX0, toVulkanImageY(sourceTexture, sourceLevel, srcY0), 0);
                blit.get(0).srcOffsets(1).set(srcX1, toVulkanImageY(sourceTexture, sourceLevel, srcY1), 1);
                blit.get(0).dstSubresource()
                    .aspectMask(destTexture.aspectMask)
                    .mipLevel(destLevel)
                    .baseArrayLayer(0)
                    .layerCount(layerCount);
                blit.get(0).dstOffsets(0).set(dstX0, toVulkanImageY(destTexture, destLevel, dstY0), 0);
                blit.get(0).dstOffsets(1).set(dstX1, toVulkanImageY(destTexture, destLevel, dstY1), 1);

                VK10.vkCmdBlitImage(
                    commandBuffer,
                    sourceTexture.imageHandle,
                    VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    destTexture.imageHandle,
                    VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    blit,
                    VK10.VK_FILTER_NEAREST
                );
            }

            if (sourceOriginalLayout != VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL) {
                transitionImageLayout(
                    commandBuffer,
                    sourceTexture.imageHandle,
                    sourceTexture.aspectMask,
                    VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    sourceOriginalLayout,
                    sourceLevel,
                    1,
                    layerCount
                );
            }
            if (destOriginalLayout != VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
                transitionImageLayout(
                    commandBuffer,
                    destTexture.imageHandle,
                    destTexture.aspectMask,
                    VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    destOriginalLayout,
                    destLevel,
                    1,
                    layerCount
                );
            }

            trackLayoutForLevel(sourceTexture, sourceLevel, sourceOriginalLayout);
            trackLayoutForLevel(destTexture, destLevel, destOriginalLayout);
        }

        private static int toVulkanImageY(LegacyTextureObject texture, int level, int glY) {
            return legacyTextureLevelHeight(texture, level) - glY;
        }

        private static int toVulkanImageRegionY(LegacyTextureObject texture, int level, int glY, int regionHeight) {
            return legacyTextureLevelHeight(texture, level) - glY - regionHeight;
        }

        private static int legacyTextureLevelHeight(LegacyTextureObject texture, int level) {
            TextureLevelInfo levelInfo = texture.levels.get(level);
            if (levelInfo != null) {
                return Math.max(1, levelInfo.height);
            }
            return Math.max(1, texture.height >> Math.max(0, level));
        }

        private void generateLegacyTextureMipmap(long commandBufferHandle, int target) {
            ensureRecordingCommandBuffer(commandBufferHandle, "generateMipmap");
            if (renderPassRecording) {
                throw new IllegalStateException("generateMipmap requires command recording outside an active render pass");
            }
            if (target == VulkanicAPI.GL_PROXY_TEXTURE_2D) {
                throw new IllegalArgumentException("generateMipmap does not support GL_PROXY_TEXTURE_2D target");
            }

            LegacyTextureObject texture = requireBoundLegacyTexture2D(target, "generateMipmap");
            generateLegacyTextureMipmap(commandBufferHandle, texture, "generateMipmap");
        }

        private void generateLegacyTextureMipmapForTextureId(long commandBufferHandle, int textureId) {
            ensureRecordingCommandBuffer(commandBufferHandle, "generateTextureMipmapDSA");
            if (renderPassRecording) {
                throw new IllegalStateException("generateTextureMipmapDSA requires command recording outside an active render pass");
            }

            LegacyTextureObject texture = requireLegacyTexture(textureId);
            generateLegacyTextureMipmap(commandBufferHandle, texture, "generateTextureMipmapDSA");
        }

        private void generateLegacyTextureMipmap(long commandBufferHandle,
                                                 LegacyTextureObject texture,
                                                 String operation) {
            if (texture.imageHandle == VK10.VK_NULL_HANDLE || texture.mipLevels <= 1) {
                return;
            }
            if (texture.aspectMask != VK10.VK_IMAGE_ASPECT_COLOR_BIT) {
                throw new UnsupportedOperationException(operation + " currently supports only color textures");
            }

            VkCommandBuffer commandBuffer = requireRecordingCommandBuffer(commandBufferHandle, operation);
            int layerCount = legacyTextureLayerCount(texture);
            int finalLayout = preferredIdleLayout(texture);

            for (int level = 1; level < texture.mipLevels; level++) {
                int sourceLevel = level - 1;
                int sourceLayout = trackedLayoutForLevel(texture, sourceLevel);
                if (sourceLayout != VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL) {
                    transitionImageLayout(
                        commandBuffer,
                        texture.imageHandle,
                        texture.aspectMask,
                        sourceLayout,
                        VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                        sourceLevel,
                        1,
                        layerCount
                    );
                    trackLayoutForLevel(texture, sourceLevel, VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
                }

                int destinationLayout = trackedLayoutForLevel(texture, level);
                if (destinationLayout != VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
                    transitionImageLayout(
                        commandBuffer,
                        texture.imageHandle,
                        texture.aspectMask,
                        destinationLayout,
                        VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        level,
                        1,
                        layerCount
                    );
                    trackLayoutForLevel(texture, level, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
                }

                try (MemoryStack stack = stackPush()) {
                    VkImageBlit.Buffer blit = VkImageBlit.calloc(1, stack);
                    blit.get(0).srcSubresource()
                        .aspectMask(texture.aspectMask)
                        .mipLevel(sourceLevel)
                        .baseArrayLayer(0)
                        .layerCount(layerCount);
                    blit.get(0).srcOffsets(0).set(0, 0, 0);
                    blit.get(0).srcOffsets(1).set(
                        Math.max(1, texture.width >> sourceLevel),
                        Math.max(1, texture.height >> sourceLevel),
                        1
                    );
                    blit.get(0).dstSubresource()
                        .aspectMask(texture.aspectMask)
                        .mipLevel(level)
                        .baseArrayLayer(0)
                        .layerCount(layerCount);
                    blit.get(0).dstOffsets(0).set(0, 0, 0);
                    blit.get(0).dstOffsets(1).set(
                        Math.max(1, texture.width >> level),
                        Math.max(1, texture.height >> level),
                        1
                    );

                    VK10.vkCmdBlitImage(
                        commandBuffer,
                        texture.imageHandle,
                        VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                        texture.imageHandle,
                        VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        blit,
                        VK10.VK_FILTER_LINEAR
                    );
                }
            }

            for (int level = 0; level < texture.mipLevels; level++) {
                int trackedLayout = trackedLayoutForLevel(texture, level);
                if (trackedLayout == finalLayout) {
                    continue;
                }

                transitionImageLayout(
                    commandBuffer,
                    texture.imageHandle,
                    texture.aspectMask,
                    trackedLayout,
                    finalLayout,
                    level,
                    1,
                    layerCount
                );
                trackLayoutForLevel(texture, level, finalLayout);
            }
        }

        private void bufferDataByTarget(int target, java.nio.ByteBuffer data, int usageHint) {
            LegacyBufferObject legacy = requireBoundLegacyBuffer(target);
            namedBufferData(legacy.id, data, usageHint);
        }

        private void bufferDataByTarget(int target, int size, int usageHint) {
            LegacyBufferObject legacy = requireBoundLegacyBuffer(target);
            namedBufferData(legacy.id, size, usageHint);
        }

        private void bufferStorageByTarget(int target, java.nio.ByteBuffer data, int flags) {
            LegacyBufferObject legacy = requireBoundLegacyBuffer(target);
            namedBufferStorage(legacy.id, data, flags);
        }

        private void bufferStorageByTarget(int target, int size, int flags) {
            LegacyBufferObject legacy = requireBoundLegacyBuffer(target);
            namedBufferStorage(legacy.id, size, flags);
        }

        private void namedBufferData(int bufferId, java.nio.ByteBuffer data, int usageHint) {
            if (data == null) {
                throw new IllegalArgumentException("data must not be null");
            }

            LegacyBufferObject legacy = requireLegacyBuffer(bufferId);
            int size = data.remaining();
            configureLegacyBufferStorage(legacy, legacy.lastTarget, size, usageHint,
                size == 0 ? null : data.duplicate());
        }

        private void namedBufferData(int bufferId, int size, int usageHint) {
            if (size < 0) {
                throw new IllegalArgumentException("size must be >= 0, got: " + size);
            }

            LegacyBufferObject legacy = requireLegacyBuffer(bufferId);
            configureLegacyBufferStorage(legacy, legacy.lastTarget, size, usageHint, null);
        }

        private void namedBufferStorage(int bufferId, java.nio.ByteBuffer data, int flags) {
            if (data == null) {
                throw new IllegalArgumentException("data must not be null");
            }

            LegacyBufferObject legacy = requireLegacyBuffer(bufferId);
            int size = data.remaining();
            configureLegacyBufferStorage(legacy, legacy.lastTarget, size, flags,
                size == 0 ? null : data.duplicate());
        }

        private void namedBufferStorage(int bufferId, int size, int flags) {
            if (size < 0) {
                throw new IllegalArgumentException("size must be >= 0, got: " + size);
            }

            LegacyBufferObject legacy = requireLegacyBuffer(bufferId);
            configureLegacyBufferStorage(legacy, legacy.lastTarget, size, flags, null);
        }

        private void configureLegacyBufferStorage(LegacyBufferObject legacy,
                                                  int target,
                                                  int size,
                                                  int usageHint,
                                                  java.nio.ByteBuffer initialData) {
            unmapNamedBuffer(legacy.id);
            closeLegacyBufferStorage(legacy);

            legacy.logicalSizeBytes = size;
            legacy.lastTarget = target;

            if (size == 0) {
                legacy.buffer = null;
                return;
            }

            int usage = toLegacyBufferUsage(target);
            legacy.buffer = (VulkanBuffer) createManagedBuffer(
                "LegacyBuffer-" + legacy.id,
                usage,
                size,
                initialData == null ? null : initialData.duplicate()
            );
        }

        private void closeLegacyBufferStorage(LegacyBufferObject legacy) {
            VulkanBuffer buffer = legacy.buffer;
            legacy.buffer = null;
            if (buffer != null) {
                buffer.close();
            }
        }

        private int toLegacyBufferUsage(int target) {
            int usage = VulkanicBuffer.USAGE_MAP_READ
                | VulkanicBuffer.USAGE_MAP_WRITE
                | VulkanicBuffer.USAGE_COPY_SRC
                | VulkanicBuffer.USAGE_COPY_DST;

            if (target == VulkanicAPI.GL_ARRAY_BUFFER) {
                // GL allows rebinding the same buffer name between ARRAY/ELEMENT targets.
                // Allocate both roles up front so draw-time binds stay valid regardless of
                // whichever target last configured storage.
                usage |= VulkanicBuffer.USAGE_VERTEX | VulkanicBuffer.USAGE_INDEX;
            } else if (target == VulkanicAPI.GL_ELEMENT_ARRAY_BUFFER) {
                usage |= VulkanicBuffer.USAGE_VERTEX | VulkanicBuffer.USAGE_INDEX;
            } else if (target == VulkanicAPI.GL_UNIFORM_BUFFER) {
                usage |= VulkanicBuffer.USAGE_UNIFORM;
            } else if (target == VulkanicAPI.GL_TEXTURE_BUFFER) {
                usage |= VulkanicBuffer.USAGE_UNIFORM_TEXEL_BUFFER;
            }

            return usage;
        }

        private LegacyBufferObject requireLegacyBuffer(int bufferId) {
            LegacyBufferObject legacy = legacyBuffers.get(bufferId);
            if (legacy == null) {
                throw new IllegalArgumentException("Unknown Vulkan legacy buffer handle: " + bufferId);
            }
            return legacy;
        }

        /**
         * Resolves a legacy buffer ID to the backing {@link VulkanBuffer}.
         *
         * <p>Called by {@link VulkanBackend#resolveVulkanicBuffer} to provide a
         * backend-neutral buffer reference for descriptor binding without exposing
         * {@code NativeSpine} internals to upper layers.
         *
         * @param bufferId the legacy integer buffer handle (from {@link #createLegacyBuffer})
         * @return the backing {@code VulkanBuffer} for the given ID
         * @throws IllegalArgumentException if the ID is unknown
         * @throws IllegalStateException    if the buffer has not had data uploaded yet
         */
        private VulkanBuffer resolveLegacyVulkanBuffer(int bufferId) {
            LegacyBufferObject legacy = requireLegacyBuffer(bufferId);
            VulkanBuffer buffer = legacy.buffer;
            if (buffer == null || buffer.isClosed()) {
                throw new IllegalStateException(
                    "Legacy buffer " + bufferId
                        + " has no backing VulkanBuffer – upload buffer data before binding "
                        + "it as a descriptor resource (bufferData / bufferSubData must be called first)");
            }
            return buffer;
        }

        private LegacyTextureObject requireLegacyTexture(int textureId) {
            LegacyTextureObject texture = legacyTextures.get(textureId);
            if (texture == null) {
                throw new IllegalArgumentException("Unknown Vulkan legacy texture handle: " + textureId);
            }
            return texture;
        }

        private LegacyBufferObject requireBoundLegacyBuffer(int target) {
            Integer bufferId = legacyBufferBindings.get(target);
            if (bufferId == null || bufferId == 0) {
                throw new IllegalStateException("No Vulkan legacy buffer bound for target " + target);
            }
            return requireLegacyBuffer(bufferId);
        }

        private VulkanBuffer requireAllocatedLegacyBuffer(LegacyBufferObject legacy, String operation) {
            VulkanBuffer buffer = legacy.buffer;
            if (buffer == null || legacy.logicalSizeBytes <= 0) {
                throw new IllegalStateException(operation + " requires allocated legacy buffer storage for handle " + legacy.id);
            }
            return buffer;
        }

        private void namedBufferSubData(int bufferId, long offset, java.nio.ByteBuffer data) {
            if (data == null) {
                throw new IllegalArgumentException("data must not be null");
            }

            LegacyBufferObject legacy = requireLegacyBuffer(bufferId);
            if (offset < 0L) {
                throw new IllegalArgumentException("offset must be >= 0, got: " + offset);
            }

            int length = data.remaining();
            if (length == 0) {
                return;
            }
            if (offset + length > legacy.logicalSizeBytes) {
                throw new IllegalArgumentException("SubData range exceeds legacy buffer size (offset="
                    + offset + ", length=" + length + ", size=" + legacy.logicalSizeBytes + ")");
            }
            if (legacyBufferMappedViews.containsKey(bufferId)) {
                throw new IllegalStateException("Legacy buffer is currently mapped: " + bufferId);
            }

            VulkanBuffer buffer = requireAllocatedLegacyBuffer(legacy, "namedBufferSubDataDSA");
            try (VulkanicBuffer.MappedView mapped = mapManagedBuffer(buffer, false, true)) {
                java.nio.ByteBuffer mappedData = mapped.data();
                java.nio.ByteBuffer source = data.duplicate();
                mappedData.position((int) offset);
                mappedData.put(source);
                mappedData.position(0);
            }
        }

        private void bufferSubDataByTarget(int target, long offset, java.nio.ByteBuffer data) {
            LegacyBufferObject legacy = requireBoundLegacyBuffer(target);
            namedBufferSubData(legacy.id, offset, data);
        }

        private void copyNamedBufferSubData(int readBufferId,
                                            int writeBufferId,
                                            long readOffset,
                                            long writeOffset,
                                            long size) {
            if (readOffset < 0L || writeOffset < 0L || size < 0L || size > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Invalid copy range: readOffset=" + readOffset
                    + ", writeOffset=" + writeOffset + ", size=" + size);
            }
            if (size == 0L) {
                return;
            }

            LegacyBufferObject readLegacy = requireLegacyBuffer(readBufferId);
            LegacyBufferObject writeLegacy = requireLegacyBuffer(writeBufferId);

            if (readOffset + size > readLegacy.logicalSizeBytes) {
                throw new IllegalArgumentException("Read range exceeds source buffer size");
            }
            if (writeOffset + size > writeLegacy.logicalSizeBytes) {
                throw new IllegalArgumentException("Write range exceeds destination buffer size");
            }
            if (legacyBufferMappedViews.containsKey(readBufferId) || legacyBufferMappedViews.containsKey(writeBufferId)) {
                throw new IllegalStateException("Cannot copy while legacy source/destination buffer is mapped");
            }

            VulkanBuffer readBuffer = requireAllocatedLegacyBuffer(readLegacy, "copyNamedBufferSubDataDSA(read)");
            VulkanBuffer writeBuffer = requireAllocatedLegacyBuffer(writeLegacy, "copyNamedBufferSubDataDSA(write)");

            int copySize = (int) size;
            int readPos = (int) readOffset;
            int writePos = (int) writeOffset;

            if (readBufferId == writeBufferId) {
                try (VulkanicBuffer.MappedView mapped = mapManagedBuffer(readBuffer, true, true)) {
                    java.nio.ByteBuffer mappedData = mapped.data();
                    byte[] temp = new byte[copySize];
                    java.nio.ByteBuffer src = mappedData.duplicate();
                    src.position(readPos).limit(readPos + copySize);
                    src.get(temp);
                    java.nio.ByteBuffer dst = mappedData.duplicate();
                    dst.position(writePos);
                    dst.put(temp);
                }
                return;
            }

            try (VulkanicBuffer.MappedView srcView = mapManagedBuffer(readBuffer, true, false);
                 VulkanicBuffer.MappedView dstView = mapManagedBuffer(writeBuffer, false, true)) {
                java.nio.ByteBuffer src = srcView.data().duplicate();
                java.nio.ByteBuffer dst = dstView.data().duplicate();
                src.position(readPos).limit(readPos + copySize);
                dst.position(writePos);
                dst.put(src);
            }
        }

        private void copyBufferSubDataByTarget(int readTarget,
                                               int writeTarget,
                                               long readOffset,
                                               long writeOffset,
                                               long size) {
            LegacyBufferObject readLegacy = requireBoundLegacyBuffer(readTarget);
            LegacyBufferObject writeLegacy = requireBoundLegacyBuffer(writeTarget);
            copyNamedBufferSubData(readLegacy.id, writeLegacy.id, readOffset, writeOffset, size);
        }

        private java.nio.ByteBuffer mapNamedBufferRange(int bufferId, long offset, long length, int access) {
            LegacyBufferObject legacy = requireLegacyBuffer(bufferId);

            if (offset < 0L || length < 0L || length > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Invalid map range offset=" + offset + ", length=" + length);
            }
            if (offset + length > legacy.logicalSizeBytes) {
                throw new IllegalArgumentException("Map range exceeds legacy buffer size");
            }
            if (length == 0L) {
                return java.nio.ByteBuffer.allocate(0);
            }
            if (legacyBufferMappedViews.containsKey(bufferId)) {
                throw new IllegalStateException("Legacy buffer is already mapped: " + bufferId);
            }

            boolean write = (access & VulkanicAPI.GL_MAP_WRITE_BIT) != 0;
            boolean read = (access & GL_MAP_READ_BIT) != 0 || !write;

            VulkanBuffer buffer = requireAllocatedLegacyBuffer(legacy, "mapNamedBufferRangeDSA");
            VulkanicBuffer.MappedView view = mapManagedBuffer(buffer, read, write);
            legacyBufferMappedViews.put(bufferId, view);

            java.nio.ByteBuffer mapped = view.data().duplicate();
            mapped.position((int) offset);
            mapped.limit((int) (offset + length));
            return mapped.slice();
        }

        private java.nio.ByteBuffer mapBufferByTarget(int target, long offset, long length, int access) {
            LegacyBufferObject legacy = requireBoundLegacyBuffer(target);
            return mapNamedBufferRange(legacy.id, offset, length, access);
        }

        private void unmapNamedBuffer(int bufferId) {
            VulkanicBuffer.MappedView mappedView = legacyBufferMappedViews.remove(bufferId);
            if (mappedView != null) {
                mappedView.close();
            }
        }

        private void unmapBufferByTarget(int target) {
            LegacyBufferObject legacy = requireBoundLegacyBuffer(target);
            unmapNamedBuffer(legacy.id);
        }

        private void flushMappedNamedBufferRange(int bufferId, long offset, long length) {
            if (offset < 0L || length < 0L) {
                throw new IllegalArgumentException("offset/length must be >= 0");
            }

            LegacyBufferObject legacy = requireLegacyBuffer(bufferId);
            if (offset + length > legacy.logicalSizeBytes) {
                throw new IllegalArgumentException("Flush range exceeds legacy buffer size");
            }
            if (!legacyBufferMappedViews.containsKey(bufferId)) {
                throw new IllegalStateException("Cannot flush unmapped legacy buffer: " + bufferId);
            }
        }

        private void flushMappedBufferRangeByTarget(int target, long offset, long length) {
            LegacyBufferObject legacy = requireBoundLegacyBuffer(target);
            flushMappedNamedBufferRange(legacy.id, offset, length);
        }

        private VulkanBuffer requireLegacyDrawBuffer(int target, String operation) {
            LegacyBufferObject legacy = requireBoundLegacyBuffer(target);
            return requireAllocatedLegacyBuffer(legacy, operation);
        }

        private VulkanBuffer resolveOptionalLegacyDrawBuffer(int target) {
            Integer bufferId = legacyBufferBindings.get(target);
            if (bufferId == null || bufferId == 0) {
                return null;
            }

            LegacyBufferObject legacy = requireLegacyBuffer(bufferId);
            VulkanBuffer buffer = legacy.buffer;
            if (buffer == null || buffer.isClosed() || legacy.logicalSizeBytes <= 0) {
                return null;
            }

            return buffer;
        }

        private void drawLegacyArrays(long commandBufferHandle,
                                      int mode,
                                      int first,
                                      int count,
                                      int instanceCount) {
            if (debugLegacyDrawLogCount < 120) {
                debugLegacyDrawLogCount++;
                LOGGER.info(
                    "Vulkan draw#{} kind=arrays mode={} first={} count={} instances={} renderPassRecording={}",
                    debugLegacyDrawLogCount,
                    mode,
                    first,
                    count,
                    instanceCount,
                    renderPassRecording
                );
            }
            backend.bindLegacyProgramPipelineForDraw(commandBufferHandle, mode);
            bindLegacyVertexBuffersForDraw(commandBufferHandle);
            drawInstanced(commandBufferHandle, first, count, instanceCount);
        }

        private void drawLegacyElements(long commandBufferHandle,
                                        int mode,
                                        int count,
                                        VulkanicIndexType indexType,
                                        long indices,
                                        int instanceCount,
                                        int baseVertex) {
            if (debugLegacyDrawLogCount < 120) {
                debugLegacyDrawLogCount++;
                LOGGER.info(
                    "Vulkan draw#{} kind=indexed mode={} count={} indexType={} indexOffset={} instances={} baseVertex={} renderPassRecording={}",
                    debugLegacyDrawLogCount,
                    mode,
                    count,
                    indexType,
                    indices,
                    instanceCount,
                    baseVertex,
                    renderPassRecording
                );
            }
            backend.bindLegacyProgramPipelineForDraw(commandBufferHandle, mode);
            VulkanBuffer indexBuffer = requireLegacyDrawBuffer(VulkanicAPI.GL_ELEMENT_ARRAY_BUFFER, "drawElements(index)");

            int bytesPerIndex = indexType.bytesPerIndex();
            if ((indices % bytesPerIndex) != 0L) {
                throw new IllegalArgumentException(
                    "Index offset must align to index type size. offset=" + indices + ", bytesPerIndex=" + bytesPerIndex);
            }

            long firstIndexLong = indices / bytesPerIndex;
            if (firstIndexLong > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Computed firstIndex exceeds int range: " + firstIndexLong);
            }

            bindLegacyVertexBuffersForDraw(commandBufferHandle);
            bindIndexBuffer(commandBufferHandle, indexBuffer.getVkBufferHandle(), indexType);
            drawIndexed(commandBufferHandle, (int) firstIndexLong, count, baseVertex, instanceCount);
        }

        private void bindLegacyVertexBuffersForDraw(long commandBufferHandle) {
            VirtualVaoState vaoState = backend.currentVirtualVaoState();
            java.util.List<LegacyVertexBinding> bindings = vaoState.bindings.values().stream()
                .filter(binding -> binding.buffer() > 0)
                .sorted(java.util.Comparator.comparingInt(LegacyVertexBinding::binding))
                .toList();

            if (bindings.isEmpty()) {
                VulkanBuffer fallbackVertexBuffer = resolveOptionalLegacyDrawBuffer(VulkanicAPI.GL_ARRAY_BUFFER);
                if (fallbackVertexBuffer != null) {
                    bindVertexBuffer(commandBufferHandle, 0, fallbackVertexBuffer.getVkBufferHandle());
                }
                return;
            }

            for (LegacyVertexBinding binding : bindings) {
                LegacyBufferObject legacy = requireLegacyBuffer(binding.buffer());
                VulkanBuffer vertexBuffer = legacy.buffer;
                if (vertexBuffer == null || vertexBuffer.isClosed() || legacy.logicalSizeBytes <= 0) {
                    continue;
                }
                bindVertexBuffer(commandBufferHandle, binding.binding(), vertexBuffer.getVkBufferHandle(), binding.offset());
            }
        }

        private VulkanicBuffer createManagedBuffer(String label,
                                                   int usage,
                                                   int size,
                                                   java.nio.ByteBuffer initialData) {
            long bufferHandle = VK10.VK_NULL_HANDLE;
            long memoryHandle = VK10.VK_NULL_HANDLE;

            try (MemoryStack stack = stackPush()) {
                int bufferUsageFlags = toVkBufferUsageFlags(usage);

                VkBufferCreateInfo bufferCreateInfo = VkBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .size(size)
                    .usage(bufferUsageFlags)
                    .sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);

                java.nio.LongBuffer pBuffer = stack.mallocLong(1);
                int createBufferResult = VK10.vkCreateBuffer(logicalDevice, bufferCreateInfo, null, pBuffer);
                checkVkAllocation("vkCreateBuffer", createBufferResult, size, label);
                bufferHandle = pBuffer.get(0);

                VkMemoryRequirements memoryRequirements = VkMemoryRequirements.malloc(stack);
                VK10.vkGetBufferMemoryRequirements(logicalDevice, bufferHandle, memoryRequirements);

                int memoryTypeIndex = findMemoryTypeIndex(
                    memoryRequirements.memoryTypeBits(),
                    VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
                );
                if (memoryTypeIndex < 0) {
                    throw new IllegalStateException(
                        "No host-visible/coherent memory type available for managed Vulkan buffer allocation.");
                }

                VkMemoryAllocateInfo memoryAllocateInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default()
                    .allocationSize(memoryRequirements.size())
                    .memoryTypeIndex(memoryTypeIndex);

                java.nio.LongBuffer pMemory = stack.mallocLong(1);
                int allocateMemoryResult = VK10.vkAllocateMemory(logicalDevice, memoryAllocateInfo, null, pMemory);
                checkVkAllocation("vkAllocateMemory", allocateMemoryResult, size, label);
                memoryHandle = pMemory.get(0);

                checkVk("vkBindBufferMemory", VK10.vkBindBufferMemory(logicalDevice, bufferHandle, memoryHandle, 0));

                if (initialData != null) {
                    uploadInitialBufferData(memoryHandle, size, initialData);
                }

                managedBufferAllocations.put(bufferHandle, memoryHandle);

                String debugLabel = (label == null || label.isBlank())
                    ? "VulkanBuffer-0x" + Long.toHexString(bufferHandle)
                    : label;
                long finalBufferHandle = bufferHandle;
                long finalMemoryHandle = memoryHandle;

                return new VulkanBuffer(
                    finalBufferHandle,
                    finalMemoryHandle,
                    usage,
                    size,
                    debugLabel,
                    () -> destroyManagedBuffer(finalBufferHandle, finalMemoryHandle)
                );
            } catch (RuntimeException exception) {
                if (logicalDevice != null) {
                    if (bufferHandle != VK10.VK_NULL_HANDLE) {
                        VK10.vkDestroyBuffer(logicalDevice, bufferHandle, null);
                    }
                    if (memoryHandle != VK10.VK_NULL_HANDLE) {
                        VK10.vkFreeMemory(logicalDevice, memoryHandle, null);
                    }
                }
                throw exception;
            }
        }

        private VulkanicBuffer.MappedView mapManagedBuffer(VulkanBuffer buffer, boolean read, boolean write) {
            buffer.beginMappedScope();
            try (MemoryStack stack = stackPush()) {
                org.lwjgl.PointerBuffer mappedPointer = stack.mallocPointer(1);
                int mapResult = VK10.vkMapMemory(
                    logicalDevice,
                    buffer.getVkMemoryHandle(),
                    0,
                    buffer.size(),
                    0,
                    mappedPointer
                );

                checkVkAllocation("vkMapMemory", mapResult, buffer.size(), buffer.toString());

                java.nio.ByteBuffer mappedData = MemoryUtil.memByteBuffer(mappedPointer.get(0), buffer.size());
                return new VulkanBuffer.VulkanMappedView(
                    mappedData,
                    () -> {
                        VK10.vkUnmapMemory(logicalDevice, buffer.getVkMemoryHandle());
                        buffer.endMappedScope();
                    }
                );
            } catch (RuntimeException exception) {
                buffer.endMappedScope();
                throw exception;
            }
        }

        private void uploadInitialBufferData(long memoryHandle, int size, java.nio.ByteBuffer initialData) {
            try (MemoryStack stack = stackPush()) {
                org.lwjgl.PointerBuffer mappedPointer = stack.mallocPointer(1);
                int mapResult = VK10.vkMapMemory(logicalDevice, memoryHandle, 0, size, 0, mappedPointer);
                checkVkAllocation("vkMapMemory(initial upload)", mapResult, size, "managed buffer initial upload");

                java.nio.ByteBuffer mappedData = MemoryUtil.memByteBuffer(mappedPointer.get(0), size);
                java.nio.ByteBuffer source = initialData.duplicate();
                mappedData.put(source);
                VK10.vkUnmapMemory(logicalDevice, memoryHandle);
            }
        }

        private int toVkBufferUsageFlags(int usage) {
            int flags = 0;

            if ((usage & VulkanicBuffer.USAGE_COPY_SRC) != 0) {
                flags |= VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
            }
            if ((usage & VulkanicBuffer.USAGE_COPY_DST) != 0) {
                flags |= VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT;
            }
            if ((usage & VulkanicBuffer.USAGE_VERTEX) != 0) {
                flags |= VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
            }
            if ((usage & VulkanicBuffer.USAGE_INDEX) != 0) {
                flags |= VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
            }
            if ((usage & VulkanicBuffer.USAGE_UNIFORM) != 0) {
                flags |= VK10.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT;
            }
            if ((usage & VulkanicBuffer.USAGE_UNIFORM_TEXEL_BUFFER) != 0) {
                flags |= VK10.VK_BUFFER_USAGE_UNIFORM_TEXEL_BUFFER_BIT;
            }

            if (flags == 0) {
                flags = VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT;
            }

            return flags;
        }

        private int findMemoryTypeIndex(int memoryTypeBits, int requiredProperties) {
            try (MemoryStack stack = stackPush()) {
                VkPhysicalDeviceMemoryProperties memoryProperties = VkPhysicalDeviceMemoryProperties.malloc(stack);
                VK10.vkGetPhysicalDeviceMemoryProperties(physicalDevice, memoryProperties);

                for (int typeIndex = 0; typeIndex < memoryProperties.memoryTypeCount(); typeIndex++) {
                    boolean typeSupported = (memoryTypeBits & (1 << typeIndex)) != 0;
                    if (!typeSupported) {
                        continue;
                    }

                    int propertyFlags = memoryProperties.memoryTypes(typeIndex).propertyFlags();
                    if ((propertyFlags & requiredProperties) == requiredProperties) {
                        return typeIndex;
                    }
                }
            }

            return -1;
        }

        private void destroyManagedBuffer(long bufferHandle, long memoryHandle) {
            Long trackedMemoryHandle = managedBufferAllocations.remove(bufferHandle);
            long effectiveMemoryHandle = trackedMemoryHandle == null ? memoryHandle : trackedMemoryHandle;

            if (logicalDevice == null) {
                return;
            }

            if (bufferHandle != VK10.VK_NULL_HANDLE) {
                VK10.vkDestroyBuffer(logicalDevice, bufferHandle, null);
            }
            if (effectiveMemoryHandle != VK10.VK_NULL_HANDLE) {
                VK10.vkFreeMemory(logicalDevice, effectiveMemoryHandle, null);
            }
        }

        private static void checkVkAllocation(String operation, int result, int size, String label) {
            if (result == VK10.VK_ERROR_OUT_OF_HOST_MEMORY || result == VK10.VK_ERROR_OUT_OF_DEVICE_MEMORY) {
                throw new GpuOutOfMemoryException(
                    operation + " failed with VkResult=" + result + " while allocating managed buffer (size="
                        + size + ", label=" + label + ")");
            }
            checkVk(operation, result);
        }

        // ===================================================================
        // Managed Texture Lifecycle
        // ===================================================================

        private VulkanicTexture createManagedTexture(String label, int usage,
                                                     VulkanicTextureFormat format,
                                                     int width, int height,
                                                     int depthOrLayers, int mipLevels) {
            long imageHandle = VK10.VK_NULL_HANDLE;
            long memoryHandle = VK10.VK_NULL_HANDLE;
            long defaultViewHandle = VK10.VK_NULL_HANDLE;

            try (MemoryStack stack = stackPush()) {
                int vkFormat = toVkFormat(format);
                int imageUsageFlags = toVkImageUsageFlags(usage, format);
                boolean colorWithSampling = (imageUsageFlags & VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT) != 0
                    && (imageUsageFlags & VK10.VK_IMAGE_USAGE_SAMPLED_BIT) != 0;
                boolean depthWithSampling = (imageUsageFlags & VK10.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT) != 0
                    && (imageUsageFlags & VK10.VK_IMAGE_USAGE_SAMPLED_BIT) != 0;
                if (attachmentFeedbackLoopLayoutEnabled && (colorWithSampling || depthWithSampling)) {
                    imageUsageFlags |= EXTAttachmentFeedbackLoopLayout.VK_IMAGE_USAGE_ATTACHMENT_FEEDBACK_LOOP_BIT_EXT;
                }
                boolean cubemapCompatible = isCubemapCompatibleUsage(usage);

                VkImageCreateInfo imageCreateInfo = VkImageCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(cubemapCompatible ? VK10.VK_IMAGE_CREATE_CUBE_COMPATIBLE_BIT : 0)
                    .imageType(VK10.VK_IMAGE_TYPE_2D)
                    .format(vkFormat)
                    .mipLevels(mipLevels)
                    .arrayLayers(depthOrLayers)
                    .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK10.VK_IMAGE_TILING_OPTIMAL)
                    .usage(imageUsageFlags)
                    .sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE)
                    .initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED);
                imageCreateInfo.extent()
                    .width(width)
                    .height(height)
                    .depth(1);

                java.nio.LongBuffer pImage = stack.mallocLong(1);
                int createImageResult = VK10.vkCreateImage(logicalDevice, imageCreateInfo, null, pImage);
                checkVkAllocation("vkCreateImage", createImageResult, width * height * format.pixelSize(), label);
                imageHandle = pImage.get(0);

                VkMemoryRequirements memoryRequirements = VkMemoryRequirements.malloc(stack);
                VK10.vkGetImageMemoryRequirements(logicalDevice, imageHandle, memoryRequirements);

                int memoryTypeIndex = findMemoryTypeIndex(
                    memoryRequirements.memoryTypeBits(),
                    VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
                );
                if (memoryTypeIndex < 0) {
                    throw new IllegalStateException(
                        "No device-local memory type available for managed Vulkan texture allocation.");
                }

                VkMemoryAllocateInfo memoryAllocateInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default()
                    .allocationSize(memoryRequirements.size())
                    .memoryTypeIndex(memoryTypeIndex);

                java.nio.LongBuffer pMemory = stack.mallocLong(1);
                int allocateResult = VK10.vkAllocateMemory(logicalDevice, memoryAllocateInfo, null, pMemory);
                checkVkAllocation("vkAllocateMemory(texture)", allocateResult,
                    width * height * format.pixelSize(), label);
                memoryHandle = pMemory.get(0);

                checkVk("vkBindImageMemory",
                    VK10.vkBindImageMemory(logicalDevice, imageHandle, memoryHandle, 0));

                int aspectMask = toVkImageAspectMask(format);
                defaultViewHandle = createVkImageView(stack, imageHandle, vkFormat,
                    aspectMask, 0, mipLevels, depthOrLayers, cubemapCompatible);

                managedImageAllocations.put(imageHandle, memoryHandle);
                managedImageDefaultViews.put(imageHandle, defaultViewHandle);

                String debugLabel = (label == null || label.isBlank())
                    ? "VulkanTexture-0x" + Long.toHexString(imageHandle)
                    : label;
                long finalImageHandle = imageHandle;
                long finalMemoryHandle = memoryHandle;
                long finalDefaultViewHandle = defaultViewHandle;

                return new VulkanTexture(
                    finalImageHandle,
                    finalMemoryHandle,
                    finalDefaultViewHandle,
                    usage,
                    format,
                    width, height, depthOrLayers, mipLevels,
                    debugLabel,
                    () -> destroyManagedTexture(finalImageHandle, finalMemoryHandle, finalDefaultViewHandle)
                );
            } catch (RuntimeException exception) {
                if (logicalDevice != null) {
                    if (defaultViewHandle != VK10.VK_NULL_HANDLE) {
                        VK10.vkDestroyImageView(logicalDevice, defaultViewHandle, null);
                    }
                    if (imageHandle != VK10.VK_NULL_HANDLE) {
                        VK10.vkDestroyImage(logicalDevice, imageHandle, null);
                    }
                    if (memoryHandle != VK10.VK_NULL_HANDLE) {
                        VK10.vkFreeMemory(logicalDevice, memoryHandle, null);
                    }
                }
                throw exception;
            }
        }

        private VulkanicTextureView createManagedTextureView(VulkanTexture texture,
                                                              int baseMipLevel,
                                                              int mipLevelCount) {
            try (MemoryStack stack = stackPush()) {
                int vkFormat = toVkFormat(texture.getVulkanicFormat());
                int aspectMask = toVkImageAspectMask(texture.getVulkanicFormat());
                boolean cubemapCompatible = isCubemapCompatibleUsage(texture.usage());
                long viewHandle = createVkImageView(stack, texture.getVkImageHandle(), vkFormat,
                    aspectMask, baseMipLevel, mipLevelCount, texture.getDepthOrLayers(), cubemapCompatible);

                managedExtraImageViews.add(viewHandle);
                long finalViewHandle = viewHandle;

                return new VulkanTextureView(
                    texture,
                    finalViewHandle,
                    baseMipLevel,
                    mipLevelCount,
                    () -> destroyManagedImageView(finalViewHandle)
                );
            }
        }

        private VulkanicTextureView createManagedTextureViewForLegacyTexture(VulkanicTexture texture,
                                                                              int legacyTextureHandle,
                                                                              int baseMipLevel,
                                                                              int mipLevelCount) {
            LegacyTextureObject legacyTexture = legacyTextures.get(legacyTextureHandle);
            if (legacyTexture == null) {
                throw new IllegalStateException("Legacy texture " + legacyTextureHandle + " is not registered.");
            }
            if (legacyTexture.imageHandle == VK10.VK_NULL_HANDLE || legacyTexture.defaultViewHandle == VK10.VK_NULL_HANDLE) {
                throw new IllegalStateException(
                    "Legacy texture " + legacyTextureHandle + " has no Vulkan image/view storage for render-pass usage.");
            }

            boolean forceOwnedView = (texture.usage() & VulkanicTexture.USAGE_RENDER_ATTACHMENT) == 0;
            boolean canUseDefaultView = !forceOwnedView && baseMipLevel == 0 && mipLevelCount == texture.getMipLevels();
            if (canUseDefaultView) {
                return new VulkanTextureView(
                    texture,
                    legacyTexture.defaultViewHandle,
                    baseMipLevel,
                    mipLevelCount,
                    legacyTextureHandle,
                    () -> {
                    }
                );
            }

            try (MemoryStack stack = stackPush()) {
                int layerCount = legacyTextureLayerCount(legacyTexture);
                boolean cubemapTexture = isLegacyCubemapTarget(legacyTexture.target);
                long viewHandle = createVkImageView(
                    stack,
                    legacyTexture.imageHandle,
                    legacyTexture.vkFormat,
                    legacyTexture.aspectMask,
                    baseMipLevel,
                    mipLevelCount,
                    layerCount,
                    cubemapTexture
                );

                managedExtraImageViews.add(viewHandle);
                legacyTexture.managedViewHandles.add(viewHandle);
                long finalViewHandle = viewHandle;
                return new VulkanTextureView(
                    texture,
                    finalViewHandle,
                    baseMipLevel,
                    mipLevelCount,
                    legacyTextureHandle,
                    createLegacyManagedImageViewCloseAction(legacyTexture, finalViewHandle)
                );
            }
        }

        private long createVkImageView(MemoryStack stack, long imageHandle, int vkFormat,
                                       int aspectMask, int baseMipLevel, int mipLevelCount,
                                       int layerCount, boolean cubemapCompatible) {
            VkImageViewCreateInfo viewCreateInfo = VkImageViewCreateInfo.calloc(stack)
                .sType$Default()
                .image(imageHandle)
                .viewType(determineVkImageViewType(layerCount, cubemapCompatible))
                .format(vkFormat);
            viewCreateInfo.components()
                .r(VK10.VK_COMPONENT_SWIZZLE_IDENTITY)
                .g(VK10.VK_COMPONENT_SWIZZLE_IDENTITY)
                .b(VK10.VK_COMPONENT_SWIZZLE_IDENTITY)
                .a(VK10.VK_COMPONENT_SWIZZLE_IDENTITY);
            viewCreateInfo.subresourceRange()
                .aspectMask(aspectMask)
                .baseMipLevel(baseMipLevel)
                .levelCount(mipLevelCount)
                .baseArrayLayer(0)
                .layerCount(layerCount);

            java.nio.LongBuffer pView = stack.mallocLong(1);
            checkVk("vkCreateImageView", VK10.vkCreateImageView(logicalDevice, viewCreateInfo, null, pView));
            return pView.get(0);
        }

        private static int determineVkImageViewType(int layerCount, boolean cubemapCompatible) {
            if (cubemapCompatible) {
                return layerCount > 6 ? VK10.VK_IMAGE_VIEW_TYPE_CUBE_ARRAY : VK10.VK_IMAGE_VIEW_TYPE_CUBE;
            }
            return layerCount > 1 ? VK10.VK_IMAGE_VIEW_TYPE_2D_ARRAY : VK10.VK_IMAGE_VIEW_TYPE_2D;
        }

        private static int toVkFormat(VulkanicTextureFormat format) {
            return switch (format) {
                case RGBA8          -> VK10.VK_FORMAT_R8G8B8A8_UNORM;
                case RGBA16F        -> VK10.VK_FORMAT_R16G16B16A16_SFLOAT;
                case RGBA8_SNORM    -> VK10.VK_FORMAT_R8G8B8A8_SNORM;
                case R11F_G11F_B10F -> VK10.VK_FORMAT_B10G11R11_UFLOAT_PACK32;
                case RED16F         -> VK10.VK_FORMAT_R16_SFLOAT;
                case RED32F         -> VK10.VK_FORMAT_R32_SFLOAT;
                case BGRA8   -> VK10.VK_FORMAT_B8G8R8A8_UNORM;
                case RED8           -> VK10.VK_FORMAT_R8_UNORM;
                case RED8I          -> VK10.VK_FORMAT_R8_SINT;
                case DEPTH32        -> VK10.VK_FORMAT_D32_SFLOAT;
                case DEPTH24_STENCIL8 -> VK10.VK_FORMAT_D24_UNORM_S8_UINT;
                case DEPTH32F_STENCIL8 -> VK10.VK_FORMAT_D32_SFLOAT_S8_UINT;
            };
        }

        private static int toVkImageUsageFlags(int usage, VulkanicTextureFormat format) {
            int flags = 0;
            if ((usage & VulkanicTexture.USAGE_COPY_SRC) != 0) {
                flags |= VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
            }
            if ((usage & VulkanicTexture.USAGE_COPY_DST) != 0) {
                flags |= VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT;
            }
            if ((usage & VulkanicTexture.USAGE_TEXTURE_BINDING) != 0) {
                flags |= VK10.VK_IMAGE_USAGE_SAMPLED_BIT;
            }
            if ((usage & VulkanicTexture.USAGE_RENDER_ATTACHMENT) != 0) {
                if (format.hasDepthAspect()) {
                    flags |= VK10.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT;
                } else {
                    flags |= VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
                }
            }
            if (flags == 0) {
                flags = VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT;
            }
            return flags;
        }

        private static int toVkImageAspectMask(VulkanicTextureFormat format) {
            if (format.hasStencilAspect()) {
                return VK10.VK_IMAGE_ASPECT_DEPTH_BIT | VK10.VK_IMAGE_ASPECT_STENCIL_BIT;
            }
            return format.hasDepthAspect()
                ? VK10.VK_IMAGE_ASPECT_DEPTH_BIT
                : VK10.VK_IMAGE_ASPECT_COLOR_BIT;
        }

        private void destroyManagedTexture(long imageHandle, long memoryHandle, long defaultViewHandle) {
            managedImageAllocations.remove(imageHandle);
            managedImageDefaultViews.remove(imageHandle);

            if (logicalDevice == null) {
                return;
            }
            enqueueVulkanResourceDestroy(() -> destroyManagedTextureHandles(imageHandle, memoryHandle, defaultViewHandle));
        }

        private void destroyManagedTextureHandles(long imageHandle, long memoryHandle, long defaultViewHandle) {
            if (logicalDevice == null) {
                return;
            }
            if (defaultViewHandle != VK10.VK_NULL_HANDLE) {
                VK10.vkDestroyImageView(logicalDevice, defaultViewHandle, null);
            }
            if (imageHandle != VK10.VK_NULL_HANDLE) {
                VK10.vkDestroyImage(logicalDevice, imageHandle, null);
            }
            if (memoryHandle != VK10.VK_NULL_HANDLE) {
                VK10.vkFreeMemory(logicalDevice, memoryHandle, null);
            }
        }

        private void destroyManagedImageView(long viewHandle) {
            if (!managedExtraImageViews.remove(viewHandle)) {
                return;
            }
            destroyImageView(viewHandle);
        }

        private void destroyImageView(long viewHandle) {
            if (logicalDevice == null || viewHandle == VK10.VK_NULL_HANDLE) {
                return;
            }
            enqueueVulkanResourceDestroy(() -> {
                if (logicalDevice != null) {
                    VK10.vkDestroyImageView(logicalDevice, viewHandle, null);
                }
            });
        }

        private void destroyBufferView(long bufferViewHandle) {
            if (logicalDevice == null || bufferViewHandle == VK10.VK_NULL_HANDLE) {
                return;
            }
            enqueueVulkanResourceDestroy(() -> {
                if (logicalDevice != null) {
                    VK10.vkDestroyBufferView(logicalDevice, bufferViewHandle, null);
                }
            });
        }

        private void createSwapchain() {
            createSwapchain(VK10.VK_NULL_HANDLE);
        }

        private void createSwapchain(long oldSwapchainHandle) {
            long newSwapchainHandle = VK10.VK_NULL_HANDLE;
            long[] newRenderFinishedSemaphores = new long[0];
            try (MemoryStack stack = stackPush()) {
                VkSurfaceCapabilitiesKHR capabilities = VkSurfaceCapabilitiesKHR.malloc(stack);
                checkVk("vkGetPhysicalDeviceSurfaceCapabilitiesKHR",
                    KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice, surface, capabilities));

                java.nio.IntBuffer formatCount = stack.ints(0);
                checkVk("vkGetPhysicalDeviceSurfaceFormatsKHR(count)",
                    KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, formatCount, null));
                if (formatCount.get(0) <= 0) {
                    throw new IllegalStateException("No Vulkan surface formats were reported for swapchain creation.");
                }

                VkSurfaceFormatKHR.Buffer surfaceFormats = VkSurfaceFormatKHR.malloc(formatCount.get(0), stack);
                checkVk("vkGetPhysicalDeviceSurfaceFormatsKHR(list)",
                    KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, formatCount, surfaceFormats));

                VkSurfaceFormatKHR chosenFormat = chooseSurfaceFormat(surfaceFormats);

                java.nio.IntBuffer presentModeCount = stack.ints(0);
                checkVk("vkGetPhysicalDeviceSurfacePresentModesKHR(count)",
                    KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, presentModeCount, null));
                if (presentModeCount.get(0) <= 0) {
                    throw new IllegalStateException("No Vulkan present modes were reported for swapchain creation.");
                }

                java.nio.IntBuffer presentModes = stack.mallocInt(presentModeCount.get(0));
                checkVk("vkGetPhysicalDeviceSurfacePresentModesKHR(list)",
                    KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, presentModeCount, presentModes));

                StringBuilder presentModeSummary = new StringBuilder();
                for (int index = 0; index < presentModeCount.get(0); index++) {
                    if (index > 0) {
                        presentModeSummary.append(", ");
                    }
                    presentModeSummary.append("0x").append(Integer.toHexString(presentModes.get(index)));
                }

                int presentMode = choosePresentMode(presentModes);
                VkExtent2D extent = chooseSwapExtent(capabilities, stack);

                LOGGER.info(
                    "Vulkan surface formats: [{}]; selected=format=0x{}, colorSpace=0x{}",
                    describeSurfaceFormats(surfaceFormats),
                    Integer.toHexString(chosenFormat.format()),
                    Integer.toHexString(chosenFormat.colorSpace())
                );

                LOGGER.info(
                    "Vulkan surface present modes: [{}]; selected=0x{}",
                    presentModeSummary,
                    Integer.toHexString(presentMode)
                );

                LOGGER.info(
                    "Vulkan surface capabilities: minImages={}, maxImages={}, currentExtent={}x{}, minExtent={}x{}, maxExtent={}x{}, supportedTransforms=0x{}, currentTransform=0x{}, supportedCompositeAlpha=0x{}",
                    capabilities.minImageCount(),
                    capabilities.maxImageCount(),
                    capabilities.currentExtent().width(),
                    capabilities.currentExtent().height(),
                    capabilities.minImageExtent().width(),
                    capabilities.minImageExtent().height(),
                    capabilities.maxImageExtent().width(),
                    capabilities.maxImageExtent().height(),
                    Integer.toHexString(capabilities.supportedTransforms()),
                    Integer.toHexString(capabilities.currentTransform()),
                    Integer.toHexString(capabilities.supportedCompositeAlpha())
                );

                int minImageCount = Math.max(1, capabilities.minImageCount() + 1);
                if (capabilities.maxImageCount() > 0 && minImageCount > capabilities.maxImageCount()) {
                    minImageCount = capabilities.maxImageCount();
                }

                int swapchainImageUsage = VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
                if ((capabilities.supportedUsageFlags() & VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT) != 0) {
                    swapchainImageUsage |= VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT;
                } else {
                    LOGGER.warn(
                        "Vulkan surface does not report VK_IMAGE_USAGE_TRANSFER_DST_BIT support for swapchain images; present blit path may be unavailable. supportedUsageFlags=0x{}",
                        Integer.toHexString(capabilities.supportedUsageFlags())
                    );
                }

                VkSwapchainCreateInfoKHR createInfo = VkSwapchainCreateInfoKHR.calloc(stack)
                    .sType$Default()
                    .surface(surface)
                    .minImageCount(minImageCount)
                    .imageFormat(chosenFormat.format())
                    .imageColorSpace(chosenFormat.colorSpace())
                    .imageExtent(extent)
                    .imageArrayLayers(1)
                    .imageUsage(swapchainImageUsage)
                    .imageSharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE)
                    .preTransform(capabilities.currentTransform())
                    .compositeAlpha(KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                    .presentMode(presentMode)
                    .clipped(true)
                    .oldSwapchain(oldSwapchainHandle);

                java.nio.LongBuffer pSwapchain = stack.mallocLong(1);
                checkVk("vkCreateSwapchainKHR",
                    KHRSwapchain.vkCreateSwapchainKHR(logicalDevice, createInfo, null, pSwapchain));
                newSwapchainHandle = pSwapchain.get(0);

                SwapchainImageResources imageResources = createSwapchainImageResources(
                    stack,
                    newSwapchainHandle,
                    chosenFormat.format()
                );
                newRenderFinishedSemaphores = createSwapchainRenderFinishedSemaphores(imageResources.imageHandles.size());

                List<Long> previousImageViewHandles = new ArrayList<>(swapchainImageViewHandles);

                destroySwapchainPresentTargets();

                destroySwapchainImageViews(previousImageViewHandles);

                swapchain = newSwapchainHandle;
                swapchainImageFormat = chosenFormat.format();
                swapchainColorSpace = chosenFormat.colorSpace();
                swapchainPresentMode = presentMode;
                swapchainWidth = extent.width();
                swapchainHeight = extent.height();
                swapchainImageCount = imageResources.imageHandles.size();

                swapchainImageHandles.clear();
                swapchainImageHandles.addAll(imageResources.imageHandles);

                swapchainImageViewHandles.clear();
                swapchainImageViewHandles.addAll(imageResources.imageViewHandles);
                createSwapchainPresentTargets(imageResources.imageViewHandles, chosenFormat.format(), extent.width(), extent.height());
                nextPresentId = 1L;

                swapchainImageLayouts.clear();
                for (int i = 0; i < swapchainImageCount; i++) {
                    swapchainImageLayouts.add(VK10.VK_IMAGE_LAYOUT_UNDEFINED);
                }
                swapchainImagesInFlight = new long[swapchainImageCount];
                destroySwapchainRenderFinishedSemaphores();
                swapchainRenderFinishedSemaphoresByImage = newRenderFinishedSemaphores;
                newRenderFinishedSemaphores = new long[0];

                LOGGER.info(
                    "Created Vulkan swapchain: extent={}x{}, images={}, format=0x{}, presentMode=0x{}, usage=0x{}, windowHandle=0x{}",
                    swapchainWidth,
                    swapchainHeight,
                    swapchainImageCount,
                    Integer.toHexString(swapchainImageFormat),
                    Integer.toHexString(swapchainPresentMode),
                    Integer.toHexString(swapchainImageUsage),
                    Long.toHexString(windowHandle)
                );
            } catch (RuntimeException exception) {
                if (newSwapchainHandle != VK10.VK_NULL_HANDLE) {
                    KHRSwapchain.vkDestroySwapchainKHR(logicalDevice, newSwapchainHandle, null);
                }
                destroySemaphores(newRenderFinishedSemaphores);
                throw exception;
            }
        }

        private long[] createSwapchainRenderFinishedSemaphores(int imageCount) {
            if (imageCount <= 0) {
                throw new IllegalStateException("Cannot create render-finished semaphores without swapchain images.");
            }

            long[] semaphores = new long[imageCount];
            try (MemoryStack stack = stackPush()) {
                VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.calloc(stack).sType$Default();
                java.nio.LongBuffer pSemaphore = stack.mallocLong(1);
                for (int imageIndex = 0; imageIndex < imageCount; imageIndex++) {
                    checkVk(
                        "vkCreateSemaphore(swapchainRenderFinishedByImage[" + imageIndex + "])",
                        VK10.vkCreateSemaphore(logicalDevice, semaphoreInfo, null, pSemaphore)
                    );
                    semaphores[imageIndex] = pSemaphore.get(0);
                }
            } catch (RuntimeException exception) {
                destroySemaphores(semaphores);
                throw exception;
            }
            return semaphores;
        }

        private SwapchainImageResources createSwapchainImageResources(
            MemoryStack stack,
            long swapchainHandle,
            int imageFormat
        ) {
            java.nio.IntBuffer imageCount = stack.ints(0);
            checkVk("vkGetSwapchainImagesKHR(count)",
                KHRSwapchain.vkGetSwapchainImagesKHR(logicalDevice, swapchainHandle, imageCount, null));
            int count = imageCount.get(0);
            if (count <= 0) {
                throw new IllegalStateException("vkGetSwapchainImagesKHR returned no swapchain images");
            }

            java.nio.LongBuffer images = stack.mallocLong(count);
            checkVk("vkGetSwapchainImagesKHR(list)",
                KHRSwapchain.vkGetSwapchainImagesKHR(logicalDevice, swapchainHandle, imageCount, images));

            List<Long> imageHandles = new ArrayList<>(count);
            List<Long> imageViewHandles = new ArrayList<>(count);
            try {
                for (int index = 0; index < count; index++) {
                    long imageHandle = images.get(index);
                    imageHandles.add(imageHandle);
                    imageViewHandles.add(createSwapchainImageView(stack, imageHandle, imageFormat));
                }
            } catch (RuntimeException exception) {
                destroySwapchainImageViews(imageViewHandles);
                throw exception;
            }

            return new SwapchainImageResources(imageHandles, imageViewHandles);
        }

        private long createSwapchainImageView(MemoryStack stack, long imageHandle, int imageFormat) {
            VkImageViewCreateInfo viewCreateInfo = VkImageViewCreateInfo.calloc(stack)
                .sType$Default()
                .image(imageHandle)
                .viewType(VK10.VK_IMAGE_VIEW_TYPE_2D)
                .format(imageFormat);
            viewCreateInfo.components()
                .r(VK10.VK_COMPONENT_SWIZZLE_IDENTITY)
                .g(VK10.VK_COMPONENT_SWIZZLE_IDENTITY)
                .b(VK10.VK_COMPONENT_SWIZZLE_IDENTITY)
                .a(VK10.VK_COMPONENT_SWIZZLE_IDENTITY);
            viewCreateInfo.subresourceRange()
                .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                .baseMipLevel(0)
                .levelCount(1)
                .baseArrayLayer(0)
                .layerCount(1);

            java.nio.LongBuffer pImageView = stack.mallocLong(1);
            checkVk("vkCreateImageView(swapchain)",
                VK10.vkCreateImageView(logicalDevice, viewCreateInfo, null, pImageView));
            return pImageView.get(0);
        }

        private void destroySwapchainImageViews(List<Long> imageViewHandles) {
            if (logicalDevice == null || imageViewHandles == null || imageViewHandles.isEmpty()) {
                return;
            }

            for (Long imageViewHandle : imageViewHandles) {
                if (imageViewHandle != null && imageViewHandle != VK10.VK_NULL_HANDLE) {
                    VK10.vkDestroyImageView(logicalDevice, imageViewHandle, null);
                }
            }
        }

        private void destroySemaphores(long[] semaphores) {
            if (logicalDevice == null || semaphores == null) {
                return;
            }

            for (int i = 0; i < semaphores.length; i++) {
                long semaphore = semaphores[i];
                if (semaphore != VK10.VK_NULL_HANDLE) {
                    VK10.vkDestroySemaphore(logicalDevice, semaphore, null);
                    semaphores[i] = VK10.VK_NULL_HANDLE;
                }
            }
        }

        private void destroySwapchainRenderFinishedSemaphores() {
            destroySemaphores(swapchainRenderFinishedSemaphoresByImage);
            swapchainRenderFinishedSemaphoresByImage = new long[0];
        }

        private void createSwapchainPresentTargets(List<Long> imageViewHandles,
                                                   int imageFormat,
                                                   int width,
                                                   int height) {
            destroySwapchainPresentTargets();
            if (logicalDevice == null || imageViewHandles == null || imageViewHandles.isEmpty()) {
                return;
            }

            long renderPassHandle = VK10.VK_NULL_HANDLE;
            List<Long> framebufferHandles = new ArrayList<>(imageViewHandles.size());
            try (MemoryStack stack = stackPush()) {
                VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(1, stack);
                attachments.get(0)
                    .format(imageFormat)
                    .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(VK10.VK_ATTACHMENT_LOAD_OP_CLEAR)
                    .storeOp(VK10.VK_ATTACHMENT_STORE_OP_STORE)
                    .stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED)
                    .finalLayout(KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);

                VkAttachmentReference.Buffer colorReference = VkAttachmentReference.calloc(1, stack);
                colorReference.get(0)
                    .attachment(0)
                    .layout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

                VkSubpassDescription.Buffer subpasses = VkSubpassDescription.calloc(1, stack);
                subpasses.get(0)
                    .pipelineBindPoint(VK10.VK_PIPELINE_BIND_POINT_GRAPHICS)
                    .colorAttachmentCount(1)
                    .pColorAttachments(colorReference);

                VkSubpassDependency.Buffer dependencies = VkSubpassDependency.calloc(2, stack);
                dependencies.get(0)
                    .srcSubpass(VK10.VK_SUBPASS_EXTERNAL)
                    .dstSubpass(0)
                    .srcStageMask(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .dstStageMask(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .srcAccessMask(0)
                    .dstAccessMask(VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);
                dependencies.get(1)
                    .srcSubpass(0)
                    .dstSubpass(VK10.VK_SUBPASS_EXTERNAL)
                    .srcStageMask(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .dstStageMask(VK10.VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT)
                    .srcAccessMask(VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                    .dstAccessMask(0);

                VkRenderPassCreateInfo renderPassCreateInfo = VkRenderPassCreateInfo.calloc(stack)
                    .sType$Default()
                    .pAttachments(attachments)
                    .pSubpasses(subpasses)
                    .pDependencies(dependencies);

                java.nio.LongBuffer pRenderPass = stack.mallocLong(1);
                checkVk("vkCreateRenderPass(swapchainPresent)",
                    VK10.vkCreateRenderPass(logicalDevice, renderPassCreateInfo, null, pRenderPass));
                renderPassHandle = pRenderPass.get(0);

                for (Long imageViewHandle : imageViewHandles) {
                    java.nio.LongBuffer pAttachments = stack.longs(imageViewHandle);
                    VkFramebufferCreateInfo framebufferCreateInfo = VkFramebufferCreateInfo.calloc(stack)
                        .sType$Default()
                        .renderPass(renderPassHandle)
                        .pAttachments(pAttachments)
                        .width(width)
                        .height(height)
                        .layers(1);

                    java.nio.LongBuffer pFramebuffer = stack.mallocLong(1);
                    checkVk("vkCreateFramebuffer(swapchainPresent)",
                        VK10.vkCreateFramebuffer(logicalDevice, framebufferCreateInfo, null, pFramebuffer));
                    framebufferHandles.add(pFramebuffer.get(0));
                }
            } catch (RuntimeException exception) {
                for (Long framebufferHandle : framebufferHandles) {
                    if (framebufferHandle != null && framebufferHandle != VK10.VK_NULL_HANDLE) {
                        VK10.vkDestroyFramebuffer(logicalDevice, framebufferHandle, null);
                    }
                }
                if (renderPassHandle != VK10.VK_NULL_HANDLE) {
                    VK10.vkDestroyRenderPass(logicalDevice, renderPassHandle, null);
                }
                throw exception;
            }

            swapchainPresentRenderPass = renderPassHandle;
            swapchainPresentFramebufferHandles.clear();
            swapchainPresentFramebufferHandles.addAll(framebufferHandles);
        }

        private void destroySwapchainPresentTargets() {
            if (logicalDevice == null) {
                swapchainPresentFramebufferHandles.clear();
                swapchainPresentRenderPass = VK10.VK_NULL_HANDLE;
                return;
            }

            for (Long framebufferHandle : new ArrayList<>(swapchainPresentFramebufferHandles)) {
                if (framebufferHandle != null && framebufferHandle != VK10.VK_NULL_HANDLE) {
                    VK10.vkDestroyFramebuffer(logicalDevice, framebufferHandle, null);
                }
            }
            swapchainPresentFramebufferHandles.clear();

            if (swapchainPresentRenderPass != VK10.VK_NULL_HANDLE) {
                VK10.vkDestroyRenderPass(logicalDevice, swapchainPresentRenderPass, null);
                swapchainPresentRenderPass = VK10.VK_NULL_HANDLE;
            }
        }

        private void destroyTrackedSwapchainImageViews() {
            destroySwapchainPresentTargets();
            destroySwapchainImageViews(new ArrayList<>(swapchainImageViewHandles));
            destroySwapchainRenderFinishedSemaphores();
            swapchainImageViewHandles.clear();
            swapchainImageHandles.clear();
            swapchainImageLayouts.clear();
            swapchainImagesInFlight = new long[0];
            swapchainImageCount = 0;
        }

        private int trackedSwapchainImageLayout(int imageIndex) {
            if (imageIndex < 0 || imageIndex >= swapchainImageLayouts.size()) {
                return VK10.VK_IMAGE_LAYOUT_UNDEFINED;
            }
            return swapchainImageLayouts.get(imageIndex);
        }

        private void trackSwapchainImageLayout(int imageIndex, int layout) {
            if (imageIndex < 0 || imageIndex >= swapchainImageLayouts.size()) {
                return;
            }
            swapchainImageLayouts.set(imageIndex, layout);
        }

        private int swapchainImageIndexForViewHandle(long imageViewHandle) {
            if (imageViewHandle == VK10.VK_NULL_HANDLE) {
                return -1;
            }
            return swapchainImageViewHandles.indexOf(imageViewHandle);
        }

        private void recreateSwapchain() {
            if (logicalDevice == null) {
                throw new IllegalStateException("Logical Vulkan device is unavailable for swapchain recreation.");
            }

            checkVk("vkDeviceWaitIdle", VK10.vkDeviceWaitIdle(logicalDevice));

            long oldSwapchainHandle = swapchain;
            createSwapchain(oldSwapchainHandle);

            if (oldSwapchainHandle != VK10.VK_NULL_HANDLE) {
                KHRSwapchain.vkDestroySwapchainKHR(logicalDevice, oldSwapchainHandle, null);
            }

            acquiredSwapchainImageIndex = -1;
            frameInProgress = false;
            currentFrameSyncIndex = 0;
            clearFrameCommandBufferRecordingState();
        }

        private void refreshSurfaceAndSwapchain() {
            if (logicalDevice == null || instance == null) {
                throw new IllegalStateException("Vulkan device/instance is unavailable for surface refresh.");
            }

            long registeredWindowHandle = net.vulkanic.VulkanicAPI.getRegisteredGlfwWindowHandleForVulkanSurface();
            if (registeredWindowHandle != VK10.VK_NULL_HANDLE) {
                windowHandle = registeredWindowHandle;
            }

            checkVk("vkDeviceWaitIdle(surfaceRefresh)", VK10.vkDeviceWaitIdle(logicalDevice));

            destroyTrackedSwapchainImageViews();
            if (swapchain != VK10.VK_NULL_HANDLE) {
                KHRSwapchain.vkDestroySwapchainKHR(logicalDevice, swapchain, null);
                swapchain = VK10.VK_NULL_HANDLE;
            }
            if (surface != VK10.VK_NULL_HANDLE) {
                KHRSurface.vkDestroySurfaceKHR(instance, surface, null);
                surface = VK10.VK_NULL_HANDLE;
            }

            createSurface();
            createSwapchain();

            acquiredSwapchainImageIndex = -1;
            frameInProgress = false;
            currentFrameSyncIndex = 0;
            consecutiveAcquireTimeouts = 0;
            lastAcquireTimeoutLogNanos = 0L;
            clearFrameCommandBufferRecordingState();
        }

        private void clearFrameCommandBufferRecordingState() {
            for (int frameIndex = 0; frameIndex < MAX_FRAMES_IN_FLIGHT; frameIndex++) {
                frameCommandBufferRecording[frameIndex] = false;
            }
        }

        private String describeWindowState() {
            if (windowHandle == VK10.VK_NULL_HANDLE) {
                return "windowHandle=null";
            }

            try (MemoryStack stack = stackPush()) {
                java.nio.IntBuffer width = stack.ints(0);
                java.nio.IntBuffer height = stack.ints(0);
                GLFW.glfwGetFramebufferSize(windowHandle, width, height);
                return "windowHandle=0x" + Long.toHexString(windowHandle)
                    + ", visible=" + (GLFW.glfwGetWindowAttrib(windowHandle, GLFW.GLFW_VISIBLE) == GLFW.GLFW_TRUE)
                    + ", iconified=" + (GLFW.glfwGetWindowAttrib(windowHandle, GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE)
                    + ", focused=" + (GLFW.glfwGetWindowAttrib(windowHandle, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE)
                    + ", framebuffer=" + width.get(0) + "x" + height.get(0);
            }
        }

        private boolean recreateSwapchainIfFramebufferSizeChanged() {
            if (!isFramebufferResizeMismatch()) {
                return false;
            }

            recreateSwapchain();
            return true;
        }

        private boolean refreshSurfaceIfRegisteredWindowChanged() {
            long registeredWindowHandle = net.vulkanic.VulkanicAPI.getRegisteredGlfwWindowHandleForVulkanSurface();
            if (registeredWindowHandle == VK10.VK_NULL_HANDLE || registeredWindowHandle == windowHandle) {
                return false;
            }

            LOGGER.info(
                "Refreshing Vulkan surface/swapchain because registered GLFW window changed from 0x{} to 0x{}.",
                Long.toHexString(windowHandle),
                Long.toHexString(registeredWindowHandle)
            );

            refreshSurfaceAndSwapchain();
            return true;
        }

        private boolean isFramebufferResizeMismatch() {
            if (windowHandle == 0L) {
                return false;
            }

            try (MemoryStack stack = stackPush()) {
                java.nio.IntBuffer width = stack.ints(0);
                java.nio.IntBuffer height = stack.ints(0);
                GLFW.glfwGetFramebufferSize(windowHandle, width, height);

                int currentWidth = width.get(0);
                int currentHeight = height.get(0);
                if (currentWidth <= 0 || currentHeight <= 0) {
                    return false;
                }

                return currentWidth != swapchainWidth || currentHeight != swapchainHeight;
            }
        }

        private static String describeSurfaceFormats(VkSurfaceFormatKHR.Buffer formats) {
            StringBuilder summary = new StringBuilder();
            for (int index = 0; index < formats.remaining(); index++) {
                if (index > 0) {
                    summary.append(", ");
                }

                VkSurfaceFormatKHR format = formats.get(index);
                summary.append("format=0x")
                    .append(Integer.toHexString(format.format()))
                    .append("/colorSpace=0x")
                    .append(Integer.toHexString(format.colorSpace()));
            }
            return summary.toString();
        }

        private static VkSurfaceFormatKHR chooseSurfaceFormat(VkSurfaceFormatKHR.Buffer formats) {
            for (int index = 0; index < formats.remaining(); index++) {
                VkSurfaceFormatKHR format = formats.get(index);
                if (isPreferredRgba8SurfaceFormat(format)) {
                    return format;
                }
            }

            for (int index = 0; index < formats.remaining(); index++) {
                VkSurfaceFormatKHR format = formats.get(index);
                if (format.colorSpace() == KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR
                    && (format.format() == VK10.VK_FORMAT_B8G8R8A8_SRGB
                        || format.format() == VK10.VK_FORMAT_B8G8R8A8_UNORM)) {
                    return format;
                }
            }

            return formats.get(0);
        }

        private static boolean isPreferredRgba8SurfaceFormat(VkSurfaceFormatKHR format) {
            if (format.colorSpace() != KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                return false;
            }

            return format.format() == VK10.VK_FORMAT_R8G8B8A8_UNORM
                || format.format() == VK10.VK_FORMAT_R8G8B8A8_SRGB;
        }

        private static int choosePresentMode(java.nio.IntBuffer presentModes) {
            if (DEBUG_FORCE_FIFO_PRESENT_MODE_EXPERIMENT) {
                for (int index = 0; index < presentModes.remaining(); index++) {
                    int mode = presentModes.get(index);
                    if (mode == KHRSurface.VK_PRESENT_MODE_FIFO_KHR) {
                        return mode;
                    }
                }
            }
            for (int index = 0; index < presentModes.remaining(); index++) {
                int mode = presentModes.get(index);
                if (mode == KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR) {
                    return mode;
                }
            }
            if (GLFW.glfwGetPlatform() == GLFW.GLFW_PLATFORM_X11) {
                for (int index = 0; index < presentModes.remaining(); index++) {
                    int mode = presentModes.get(index);
                    if (mode == KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR) {
                        return mode;
                    }
                }
            }
            for (int index = 0; index < presentModes.remaining(); index++) {
                int mode = presentModes.get(index);
                if (mode == KHRSurface.VK_PRESENT_MODE_FIFO_KHR) {
                    return mode;
                }
            }
            for (int index = 0; index < presentModes.remaining(); index++) {
                int mode = presentModes.get(index);
                if (mode == KHRSurface.VK_PRESENT_MODE_FIFO_RELAXED_KHR) {
                    return mode;
                }
            }
            for (int index = 0; index < presentModes.remaining(); index++) {
                int mode = presentModes.get(index);
                if (mode == KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR) {
                    return mode;
                }
            }
            return KHRSurface.VK_PRESENT_MODE_FIFO_KHR;
        }

        private VkExtent2D chooseSwapExtent(VkSurfaceCapabilitiesKHR capabilities, MemoryStack stack) {
            if (capabilities.currentExtent().width() != 0xFFFFFFFF) {
                return VkExtent2D.malloc(stack)
                    .set(capabilities.currentExtent().width(), capabilities.currentExtent().height());
            }

            java.nio.IntBuffer width = stack.ints(0);
            java.nio.IntBuffer height = stack.ints(0);
            GLFW.glfwGetFramebufferSize(windowHandle, width, height);

            int clampedWidth = Math.max(
                capabilities.minImageExtent().width(),
                Math.min(capabilities.maxImageExtent().width(), width.get(0))
            );
            int clampedHeight = Math.max(
                capabilities.minImageExtent().height(),
                Math.min(capabilities.maxImageExtent().height(), height.get(0))
            );

            return VkExtent2D.malloc(stack).set(clampedWidth, clampedHeight);
        }

        private void createCommandPoolAndPrimaryBuffer() {
            try (MemoryStack stack = stackPush()) {
                VkCommandPoolCreateInfo poolCreateInfo = VkCommandPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .queueFamilyIndex(graphicsQueueFamilyIndex)
                    .flags(VK10.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);

                VkCommandBufferAllocateInfo allocateInfo = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType$Default()
                    .level(VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(1);

                java.nio.LongBuffer pCommandPool = stack.mallocLong(1);
                org.lwjgl.PointerBuffer pCommandBuffer = stack.mallocPointer(1);
                for (int slot = 0; slot < IMMEDIATE_SUBMIT_SLOTS; slot++) {
                    checkVk(
                        "vkCreateCommandPool(immediate[" + slot + "])",
                        VK10.vkCreateCommandPool(logicalDevice, poolCreateInfo, null, pCommandPool)
                    );
                    immediateCommandPools[slot] = pCommandPool.get(0);

                    allocateInfo.commandPool(immediateCommandPools[slot]);
                    checkVk(
                        "vkAllocateCommandBuffers(immediate[" + slot + "])",
                        VK10.vkAllocateCommandBuffers(logicalDevice, allocateInfo, pCommandBuffer)
                    );
                    immediateCommandBuffers[slot] = new VkCommandBuffer(pCommandBuffer.get(0), logicalDevice);
                }
                currentImmediateSubmitSlot = 0;
                commandPool = immediateCommandPools[currentImmediateSubmitSlot];
                primaryCommandBuffer = immediateCommandBuffers[currentImmediateSubmitSlot];

                for (int frameIndex = 0; frameIndex < MAX_FRAMES_IN_FLIGHT; frameIndex++) {
                    checkVk(
                        "vkCreateCommandPool(frame[" + frameIndex + "])",
                        VK10.vkCreateCommandPool(logicalDevice, poolCreateInfo, null, pCommandPool)
                    );
                    frameCommandPools[frameIndex] = pCommandPool.get(0);

                    allocateInfo.commandPool(frameCommandPools[frameIndex]);
                    checkVk(
                        "vkAllocateCommandBuffers(frame[" + frameIndex + "])",
                        VK10.vkAllocateCommandBuffers(logicalDevice, allocateInfo, pCommandBuffer)
                    );
                    frameCommandBuffers[frameIndex] = new VkCommandBuffer(pCommandBuffer.get(0), logicalDevice);
                    frameCommandBufferRecording[frameIndex] = false;
                }
            }
        }

        private boolean hasValidFrameSyncPrimitives() {
            for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
                if (swapchainImageAvailableSemaphores[i] == VK10.VK_NULL_HANDLE
                    || swapchainFrameFences[i] == VK10.VK_NULL_HANDLE) {
                    return false;
                }
            }
            if (swapchainImageCount <= 0) {
                return false;
            }
            if (swapchainImagesInFlight.length != swapchainImageCount) {
                return false;
            }
            if (swapchainRenderFinishedSemaphoresByImage.length != swapchainImageCount) {
                return false;
            }
            for (long semaphore : swapchainRenderFinishedSemaphoresByImage) {
                if (semaphore == VK10.VK_NULL_HANDLE) {
                    return false;
                }
            }
            return true;
        }

        private long currentSwapchainImageAvailableSemaphore() {
            return swapchainImageAvailableSemaphores[currentFrameSyncIndex];
        }

        private long acquiredSwapchainRenderFinishedSemaphore() {
            if (acquiredSwapchainImageIndex < 0
                || acquiredSwapchainImageIndex >= swapchainRenderFinishedSemaphoresByImage.length) {
                throw new IllegalStateException(
                    "Render-finished semaphore is unavailable for acquired swapchain image "
                        + acquiredSwapchainImageIndex + " (semaphores="
                        + swapchainRenderFinishedSemaphoresByImage.length + ")."
                );
            }
            long semaphoreHandle = swapchainRenderFinishedSemaphoresByImage[acquiredSwapchainImageIndex];
            if (semaphoreHandle == VK10.VK_NULL_HANDLE) {
                throw new IllegalStateException(
                    "Render-finished semaphore for acquired swapchain image "
                        + acquiredSwapchainImageIndex + " is unavailable."
                );
            }
            return semaphoreHandle;
        }

        private long currentSwapchainFrameFence() {
            return swapchainFrameFences[currentFrameSyncIndex];
        }

        private int swapchainAcquireWaitStageMask() {
            return VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
        }

        private void waitForAllSwapchainFrameFences() {
            if (logicalDevice == null || !hasValidFrameSyncPrimitives()) {
                return;
            }

            try (MemoryStack stack = stackPush()) {
                java.nio.LongBuffer fenceBuffer = stack.mallocLong(MAX_FRAMES_IN_FLIGHT);
                for (int frameIndex = 0; frameIndex < MAX_FRAMES_IN_FLIGHT; frameIndex++) {
                    fenceBuffer.put(frameIndex, swapchainFrameFences[frameIndex]);
                }
                int waitResult = VK10.vkWaitForFences(
                    logicalDevice,
                    fenceBuffer,
                    true,
                    PRIMARY_COMMAND_FENCE_WAIT_TIMEOUT_NANOS
                );
                if (waitResult == VK10.VK_TIMEOUT) {
                    consecutivePrimaryCommandFenceTimeouts++;
                    long nowNanos = System.nanoTime();
                    if (lastPrimaryCommandFenceTimeoutLogNanos == 0L
                        || nowNanos - lastPrimaryCommandFenceTimeoutLogNanos >= ACQUIRE_TIMEOUT_LOG_INTERVAL_NANOS) {
                        LOGGER.warn(
                            "vkWaitForFences(allSwapchainFrames) timed out ({} consecutive); deferring primary command buffer reset. {}",
                            consecutivePrimaryCommandFenceTimeouts,
                            describeWindowState()
                        );
                        lastPrimaryCommandFenceTimeoutLogNanos = nowNanos;
                    }
                    if (consecutivePrimaryCommandFenceTimeouts >= PRIMARY_COMMAND_TIMEOUTS_BEFORE_SWAPCHAIN_RECREATE) {
                        LOGGER.warn(
                            "Refreshing Vulkan surface/swapchain after {} consecutive all-frame-fence timeouts.",
                            consecutivePrimaryCommandFenceTimeouts
                        );
                        try {
                            refreshSurfaceAndSwapchain();
                        } catch (RuntimeException refreshFailure) {
                            LOGGER.warn(
                                "Surface refresh failed during all-frame-fence-timeout recovery; falling back to swapchain-only recreate.",
                                refreshFailure
                            );
                            recreateSwapchain();
                        }
                        consecutivePrimaryCommandFenceTimeouts = 0;
                        lastPrimaryCommandFenceTimeoutLogNanos = 0L;
                    }
                    throw new IllegalStateException(
                        "vkWaitForFences(allSwapchainFrames) timed out before primary command buffer reset. "
                            + describeWindowState()
                    );
                }
                checkVk("vkWaitForFences(allSwapchainFrames)", waitResult);
                markAllSubmittedWorkComplete();
                destroyAllTransientFrameDescriptorBuffers();
                consecutivePrimaryCommandFenceTimeouts = 0;
            }
        }

        private void awaitDeferredImmediateSubmitCompletion() {
            if (!immediateSubmitInFlight || logicalDevice == null) {
                return;
            }

            for (int slot = 0; slot < IMMEDIATE_SUBMIT_SLOTS; slot++) {
                if (immediateSubmitSlotsInFlight[slot]) {
                    awaitImmediateSubmitSlotCompletion(slot);
                }
            }

            immediateSubmitInFlight = anyImmediateSubmitSlotInFlight();
            if (!immediateSubmitInFlight) {
                consecutiveImmediateSubmitTimeouts = 0;
                lastImmediateSubmitTimeoutLogNanos = 0L;
                flushPendingVulkanResourceDestroys(false);
            }
        }

        private boolean anyImmediateSubmitSlotInFlight() {
            for (boolean inFlight : immediateSubmitSlotsInFlight) {
                if (inFlight) {
                    return true;
                }
            }
            return false;
        }

        private boolean shouldSynchronizeImmediateSubmitCompletion() {
            return true;
        }

        private void awaitImmediateSubmitSlotCompletion(int slot) {
            long fence = immediateSubmitFences[slot];
            if (!immediateSubmitSlotsInFlight[slot] || logicalDevice == null || fence == VK10.VK_NULL_HANDLE) {
                return;
            }

            try (MemoryStack stack = stackPush()) {
                java.nio.LongBuffer immediateFenceBuffer = stack.longs(fence);
                int waitResult = VK10.vkWaitForFences(
                    logicalDevice,
                    immediateFenceBuffer,
                    true,
                    IMMEDIATE_SUBMIT_FENCE_WAIT_TIMEOUT_NANOS
                );
                if (waitResult == VK10.VK_TIMEOUT) {
                    consecutiveImmediateSubmitTimeouts++;
                    long nowNanos = System.nanoTime();
                    if (lastImmediateSubmitTimeoutLogNanos == 0L
                        || nowNanos - lastImmediateSubmitTimeoutLogNanos >= ACQUIRE_TIMEOUT_LOG_INTERVAL_NANOS) {
                        LOGGER.warn(
                            "vkWaitForFences(immediateSubmit) timed out ({} consecutive); deferring primary command pool reuse. {}",
                            consecutiveImmediateSubmitTimeouts,
                            describeWindowState()
                        );
                        lastImmediateSubmitTimeoutLogNanos = nowNanos;
                    }
                    throw new IllegalStateException(
                        "vkWaitForFences(immediateSubmit) timed out while a prior immediate submit was still in flight. "
                            + describeWindowState()
                    );
                }
                checkVk("vkWaitForFences(immediateSubmit)", waitResult);
            }

            markFenceComplete(fence);
            reservedImmediateWorkGenerations[slot] = 0L;
            immediateSubmitSlotsInFlight[slot] = false;
            immediateSubmitInFlight = anyImmediateSubmitSlotInFlight();
            consecutiveImmediateSubmitTimeouts = 0;
            lastImmediateSubmitTimeoutLogNanos = 0L;
            destroyTransientRenderPassResources(slot);
        }

        private boolean shouldLogFrameSyncDetails() {
            return successfulFrameAcquireCount < 8 || successfulFramePresentCount < 8;
        }

        private int beginFrame() {
            VulkanPerfAudit.recordBeginFrameCall();
            if (logicalDevice == null) {
                throw new IllegalStateException("Cannot begin frame: Vulkan logical device is unavailable.");
            }
            if (graphicsQueue == null) {
                throw new IllegalStateException("Cannot begin frame: Vulkan graphics queue is unavailable.");
            }
            if (swapchain == VK10.VK_NULL_HANDLE) {
                throw new IllegalStateException("Cannot begin frame: Vulkan swapchain is unavailable.");
            }
            if (!hasValidFrameSyncPrimitives()) {
                throw new IllegalStateException("Cannot begin frame: Vulkan swapchain frame sync primitives are unavailable.");
            }
            if (frameInProgress) {
                throw new IllegalStateException("beginFrame called while a Vulkan frame is already in progress.");
            }

            if (commandBufferRecording) {
                if (renderPassRecording) {
                    throw new IllegalStateException("beginFrame cannot proceed while a render pass is active.");
                }
                submitPrimaryCommandBuffer(primaryCommandBuffer.address());
            }

            refreshSurfaceIfRegisteredWindowChanged();
            recreateSwapchainIfFramebufferSizeChanged();

            try (MemoryStack stack = stackPush()) {
                long frameFence = currentSwapchainFrameFence();
                long imageAvailableSemaphore = currentSwapchainImageAvailableSemaphore();

                if (shouldLogFrameSyncDetails()) {
                    LOGGER.info(
                        "Beginning Vulkan frame on sync slot {} (imageAvailable=0x{}, frameFence=0x{}).",
                        currentFrameSyncIndex,
                        Long.toHexString(imageAvailableSemaphore),
                        Long.toHexString(frameFence)
                    );
                }

                java.nio.LongBuffer frameFenceBuffer = stack.longs(frameFence);
                long fenceWaitStartNanos = VulkanPerfAudit.isEnabled() ? System.nanoTime() : 0L;
                int frameFenceWaitResult = VK10.vkWaitForFences(
                    logicalDevice,
                    frameFenceBuffer,
                    true,
                    SWAPCHAIN_FRAME_FENCE_WAIT_TIMEOUT_NANOS
                );
                if (fenceWaitStartNanos != 0L) {
                    VulkanPerfAudit.recordBeginFrameFenceWait(System.nanoTime() - fenceWaitStartNanos);
                }
                if (frameFenceWaitResult == VK10.VK_TIMEOUT) {
                    consecutiveFrameFenceTimeouts++;

                    long nowNanos = System.nanoTime();
                    if (lastFrameFenceTimeoutLogNanos == 0L
                        || nowNanos - lastFrameFenceTimeoutLogNanos >= ACQUIRE_TIMEOUT_LOG_INTERVAL_NANOS) {
                        LOGGER.warn(
                            "vkWaitForFences(swapchainFrame) timed out ({} consecutive); skipping frame acquire. {}",
                            consecutiveFrameFenceTimeouts,
                            describeWindowState()
                        );
                        lastFrameFenceTimeoutLogNanos = nowNanos;
                    }

                    if (consecutiveFrameFenceTimeouts >= FRAME_FENCE_TIMEOUTS_BEFORE_SWAPCHAIN_RECREATE) {
                        LOGGER.warn(
                            "Refreshing Vulkan surface/swapchain after {} consecutive frame-fence timeouts.",
                            consecutiveFrameFenceTimeouts
                        );
                        try {
                            refreshSurfaceAndSwapchain();
                        } catch (RuntimeException refreshFailure) {
                            LOGGER.warn(
                                "Surface refresh failed during frame-fence-timeout recovery; falling back to swapchain-only recreate.",
                                refreshFailure
                            );
                            recreateSwapchain();
                        }
                        consecutiveFrameFenceTimeouts = 0;
                        lastFrameFenceTimeoutLogNanos = 0L;
                    }

                    acquiredSwapchainImageIndex = -1;
                    frameInProgress = false;
                    currentFrameSyncIndex = (currentFrameSyncIndex + 1) % MAX_FRAMES_IN_FLIGHT;
                    return -1;
                }

                checkVk("vkWaitForFences(swapchainFrame)", frameFenceWaitResult);
                markFenceComplete(frameFence);
                reservedFrameWorkGenerations[currentFrameSyncIndex] = 0L;
                destroyTransientFrameDescriptorBuffers(currentFrameSyncIndex);
                consecutiveFrameFenceTimeouts = 0;

                java.nio.IntBuffer pImageIndex = stack.ints(0);
                int acquireResult = KHRSwapchain.vkAcquireNextImageKHR(
                    logicalDevice,
                    swapchain,
                    SWAPCHAIN_ACQUIRE_TIMEOUT_NANOS,
                    imageAvailableSemaphore,
                    VK10.VK_NULL_HANDLE,
                    pImageIndex
                );

                if (acquireResult == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR) {
                    recreateSwapchain();
                    acquireResult = KHRSwapchain.vkAcquireNextImageKHR(
                        logicalDevice,
                        swapchain,
                        SWAPCHAIN_ACQUIRE_TIMEOUT_NANOS,
                        imageAvailableSemaphore,
                        VK10.VK_NULL_HANDLE,
                        pImageIndex
                    );
                }

                if (acquireResult == VK10.VK_TIMEOUT || acquireResult == VK10.VK_NOT_READY) {
                    consecutiveAcquireTimeouts++;

                    LOGGER.warn(
                        "vkAcquireNextImageKHR timed out ({} consecutive); retrying with another bounded wait. {}",
                        consecutiveAcquireTimeouts,
                        describeWindowState()
                    );

                    GLFW.glfwPollEvents();
                    acquireResult = KHRSwapchain.vkAcquireNextImageKHR(
                        logicalDevice,
                        swapchain,
                        SWAPCHAIN_ACQUIRE_TIMEOUT_NANOS,
                        imageAvailableSemaphore,
                        VK10.VK_NULL_HANDLE,
                        pImageIndex
                    );

                    if (acquireResult == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR) {
                        recreateSwapchain();
                        acquireResult = KHRSwapchain.vkAcquireNextImageKHR(
                            logicalDevice,
                            swapchain,
                            SWAPCHAIN_ACQUIRE_TIMEOUT_NANOS,
                            imageAvailableSemaphore,
                            VK10.VK_NULL_HANDLE,
                            pImageIndex
                        );
                    }

                    if (acquireResult != VK10.VK_TIMEOUT && acquireResult != VK10.VK_NOT_READY) {
                        consecutiveAcquireTimeouts = 0;
                    }
                }

                if (acquireResult == VK10.VK_TIMEOUT || acquireResult == VK10.VK_NOT_READY) {

                    long nowNanos = System.nanoTime();
                    if (lastAcquireTimeoutLogNanos == 0L
                        || nowNanos - lastAcquireTimeoutLogNanos >= ACQUIRE_TIMEOUT_LOG_INTERVAL_NANOS) {
                        LOGGER.warn(
                            "vkAcquireNextImageKHR still has no swapchain image ready after bounded retries ({} consecutive); skipping present for this frame. {}",
                            consecutiveAcquireTimeouts,
                            describeWindowState()
                        );
                        lastAcquireTimeoutLogNanos = nowNanos;
                    }

                    if (consecutiveAcquireTimeouts >= ACQUIRE_TIMEOUTS_BEFORE_SWAPCHAIN_RECREATE) {
                        LOGGER.warn(
                            "Refreshing Vulkan surface/swapchain after {} consecutive acquire timeouts.",
                            consecutiveAcquireTimeouts
                        );
                        try {
                            refreshSurfaceAndSwapchain();
                        } catch (RuntimeException refreshFailure) {
                            LOGGER.warn(
                                "Surface refresh failed during acquire-timeout recovery; falling back to swapchain-only recreate.",
                                refreshFailure
                            );
                            recreateSwapchain();
                        }
                        consecutiveAcquireTimeouts = 0;
                        lastAcquireTimeoutLogNanos = 0L;
                    }

                    acquiredSwapchainImageIndex = -1;
                    frameInProgress = false;
                    currentFrameSyncIndex = (currentFrameSyncIndex + 1) % MAX_FRAMES_IN_FLIGHT;
                    return -1;
                }

                if (acquireResult != VK10.VK_SUCCESS && acquireResult != KHRSwapchain.VK_SUBOPTIMAL_KHR) {
                    throw new IllegalStateException(
                        "vkAcquireNextImageKHR failed with VkResult=" + acquireResult);
                }

                consecutiveAcquireTimeouts = 0;

                int imageIndex = pImageIndex.get(0);
                if (imageIndex < 0) {
                    throw new IllegalStateException("vkAcquireNextImageKHR returned invalid image index: " + imageIndex);
                }
                if (imageIndex >= swapchainImageHandles.size() || imageIndex >= swapchainImageViewHandles.size()) {
                    throw new IllegalStateException(
                        "vkAcquireNextImageKHR returned image index " + imageIndex
                            + " outside tracked swapchain image/view range (images="
                            + swapchainImageHandles.size() + ", views=" + swapchainImageViewHandles.size() + ").");
                }

                long imageInFlightFence = swapchainImagesInFlight[imageIndex];
                if (imageInFlightFence != VK10.VK_NULL_HANDLE && imageInFlightFence != frameFence) {
                    checkVk(
                        "vkWaitForFences(imageInFlight[" + imageIndex + "])",
                        VK10.vkWaitForFences(logicalDevice, stack.longs(imageInFlightFence), true, Long.MAX_VALUE)
                    );
                    markFenceComplete(imageInFlightFence);
                }
                swapchainImagesInFlight[imageIndex] = frameFence;

                acquiredSwapchainImageIndex = imageIndex;
                frameInProgress = true;
                reservedFrameWorkGenerations[currentFrameSyncIndex] = reserveWorkGeneration();
                successfulFrameAcquireCount++;
                VulkanPerfAudit.recordBeginFrameAcquireSuccess();
                if (successfulFrameAcquireCount <= 5) {
                    LOGGER.info(
                        "vkAcquireNextImageKHR succeeded for image {} (frame acquire #{}, sync slot {})",
                        imageIndex,
                        successfulFrameAcquireCount,
                        currentFrameSyncIndex
                    );
                }
                return imageIndex;
            }
        }

        private void endFrame() {
            if (!frameInProgress) {
                throw new IllegalStateException("endFrame called without an active Vulkan frame.");
            }
            if (renderPassRecording) {
                throw new IllegalStateException("endFrame cannot run while a render pass is active.");
            }

            if (commandBufferRecording) {
                // Ensure current-frame render work is visible before present composition.
                submitPrimaryCommandBuffer(primaryCommandBuffer.address());
            }

            if (pendingPresentTextureRequest == null && successfulFramePresentCount < 6) {
                LOGGER.warn("Ending Vulkan frame without a queued present texture; swapchain image will contain only its current contents.");
            }

            composePendingPresentTexture();

            if (isCurrentFrameCommandBufferRecording()) {
                submitCurrentFrameCommandBuffer();
            } else {
                submitFrameSemaphoreBridge();
            }

            try (MemoryStack stack = stackPush()) {
                java.nio.LongBuffer pSwapchains = stack.longs(swapchain);
                java.nio.IntBuffer pImageIndices = stack.ints(acquiredSwapchainImageIndex);
                long presentId = 0L;

                VkPresentInfoKHR presentInfo = VkPresentInfoKHR.calloc(stack)
                    .sType$Default()
                    .pWaitSemaphores(stack.longs(acquiredSwapchainRenderFinishedSemaphore()))
                    .swapchainCount(1)
                    .pSwapchains(pSwapchains)
                    .pImageIndices(pImageIndices);
                if (presentIdExtensionEnabled) {
                    presentId = nextPresentId++;
                    VkPresentIdKHR presentIdInfo = VkPresentIdKHR.calloc(stack)
                        .sType$Default()
                        .swapchainCount(1)
                        .pPresentIds(stack.longs(presentId));
                    presentInfo.pNext(presentIdInfo.address());
                }

                VkQueue queueForPresent = presentQueue != null ? presentQueue : graphicsQueue;
                
                // PHASE 1: Validate swapchain layout before presentation
                validateSwapchainLayoutBeforePresent(acquiredSwapchainImageIndex);
                
                int presentResult = KHRSwapchain.vkQueuePresentKHR(queueForPresent, presentInfo);
                if (presentResult == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR
                    || presentResult == KHRSwapchain.VK_SUBOPTIMAL_KHR) {
                    recreateSwapchain();
                } else {
                    checkVk("vkQueuePresentKHR", presentResult);
                    if (DEBUG_WAIT_FOR_PRESENT_COMPLETION_EXPERIMENT && presentWaitExtensionEnabled && presentId != 0L) {
                        waitForPresentCompletion(presentId);
                    } else if (DEBUG_WAIT_FOR_PRESENT_QUEUE_IDLE_EXPERIMENT
                        && GLFW.glfwGetPlatform() == GLFW.GLFW_PLATFORM_X11) {
                        checkVk("vkQueueWaitIdle(presentQueue)", VK10.vkQueueWaitIdle(queueForPresent));
                    }
                    successfulFramePresentCount++;
                    VulkanPerfAudit.recordPresentedFrame();
                    if (successfulFramePresentCount <= 5) {
                        LOGGER.info(
                            "vkQueuePresentKHR succeeded for image {} (frame present #{}, sync slot {})",
                            acquiredSwapchainImageIndex,
                            successfulFramePresentCount,
                            currentFrameSyncIndex
                        );
                    }
                }

            } finally {
                pendingPresentTextureRequest = null;
                acquiredSwapchainImageIndex = -1;
                frameInProgress = false;
                currentFrameSyncIndex = (currentFrameSyncIndex + 1) % MAX_FRAMES_IN_FLIGHT;
            }
        }

        private void waitForPresentCompletion(long presentId) {
            while (true) {
                int waitResult = KHRPresentWait.vkWaitForPresentKHR(
                    logicalDevice,
                    swapchain,
                    presentId,
                    SWAPCHAIN_PRESENT_WAIT_POLL_TIMEOUT_NANOS
                );
                if (waitResult == VK10.VK_SUCCESS) {
                    return;
                }
                if (waitResult == VK10.VK_TIMEOUT) {
                    GLFW.glfwPollEvents();
                    continue;
                }
                checkVk("vkWaitForPresentKHR", waitResult);
            }
        }

        private void queuePresentTextureRequest(int legacyTextureHandle,
                                                int mipLevel,
                                                int width,
                                                int height) {
            LegacyTextureObject legacyTexture = requireLegacyTexture(legacyTextureHandle);
            if (legacyTexture.imageHandle == VK10.VK_NULL_HANDLE) {
                throw new IllegalStateException("Texture " + legacyTextureHandle + " has no Vulkan image storage for presentation.");
            }
            if (mipLevel < 0 || mipLevel >= legacyTexture.mipLevels) {
                throw new IllegalArgumentException(
                    "Requested present mip level " + mipLevel + " is outside texture mip range [0, "
                        + legacyTexture.mipLevels + ")"
                );
            }
            pendingPresentTextureRequest = new PendingPresentTextureRequest(
                legacyTextureHandle,
                mipLevel,
                Math.max(1, width),
                Math.max(1, height)
            );
        }

        private void composePendingPresentTexture() {
            PendingPresentTextureRequest request = pendingPresentTextureRequest;
            if (request == null) {
                ensureAcquiredSwapchainImagePresentLayout();
                return;
            }

            if (!frameInProgress || acquiredSwapchainImageIndex < 0) {
                throw new IllegalStateException("Cannot compose present texture without an acquired swapchain image.");
            }
            if (acquiredSwapchainImageIndex >= swapchainImageHandles.size()) {
                throw new IllegalStateException(
                    "Acquired swapchain image index " + acquiredSwapchainImageIndex
                        + " is outside swapchain image range " + swapchainImageHandles.size()
                );
            }

            LegacyTextureObject sourceTexture = requireLegacyTexture(request.legacyTextureHandle);
            if (sourceTexture.imageHandle == VK10.VK_NULL_HANDLE) {
                throw new IllegalStateException(
                    "Queued present texture " + request.legacyTextureHandle + " has no Vulkan image storage."
                );
            }

            long swapchainImageHandle = swapchainImageHandles.get(acquiredSwapchainImageIndex);
            if (swapchainImageHandle == VK10.VK_NULL_HANDLE) {
                throw new IllegalStateException("Acquired swapchain image handle is null");
            }

            int dstWidth = Math.max(1, swapchainWidth);
            int dstHeight = Math.max(1, swapchainHeight);
            int srcWidth = Math.max(1, request.width);
            int srcHeight = Math.max(1, request.height);
            if (srcWidth <= 0 || srcHeight <= 0 || dstWidth <= 0 || dstHeight <= 0) {
                ensureAcquiredSwapchainImagePresentLayout();
                pendingPresentTextureRequest = null;
                return;
            }

            VkCommandBuffer frameCommandBuffer = ensureCurrentFrameCommandBufferRecording("composePendingPresentTexture");

            if (successfulFramePresentCount < 6) {
                LOGGER.info(
                    "Composing Vulkan present source handle {} format=0x{} {}x{} into swapchain format=0x{} {}x{}",
                    request.legacyTextureHandle,
                    Integer.toHexString(sourceTexture.vkFormat),
                    srcWidth,
                    srcHeight,
                    Integer.toHexString(swapchainImageFormat),
                    dstWidth,
                    dstHeight
                );
            }

            boolean requiresShaderCompose = sourceTexture.vkFormat != swapchainImageFormat
                || srcWidth != dstWidth
                || srcHeight != dstHeight;
            if (requiresShaderCompose && PRESENT_FORMAT_MISMATCH_LOG_COUNT.getAndIncrement() < 12) {
                LOGGER.warn(
                    "Vulkan present source requires shader compose into swapchain (srcFormat=0x{}, dstFormat=0x{}, src={}x{}, dst={}x{}).",
                    Integer.toHexString(sourceTexture.vkFormat),
                    Integer.toHexString(swapchainImageFormat),
                    srcWidth,
                    srcHeight,
                    dstWidth,
                    dstHeight
                );
            }

            int trackedSourceLayout = trackedLayoutForLevel(sourceTexture, request.mipLevel);
            int originalSourceLayout = trackedSourceLayout == VK10.VK_IMAGE_LAYOUT_UNDEFINED
                ? preferredIdleLayout(sourceTexture)
                : trackedSourceLayout;
            int composeSourceLayout = requiresShaderCompose
                ? VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
                : VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;

            if (!DEBUG_CLEAR_SWAPCHAIN_PRESENT_EXPERIMENT) {
                transitionImageLayout(
                    frameCommandBuffer,
                    sourceTexture.imageHandle,
                    sourceTexture.aspectMask,
                    originalSourceLayout,
                    composeSourceLayout,
                    request.mipLevel,
                    1,
                    1
                );
                trackLayoutForLevel(sourceTexture, request.mipLevel, composeSourceLayout);
            }

            if (DEBUG_CLEAR_SWAPCHAIN_PRESENT_EXPERIMENT) {
                clearSwapchainImageWithRenderPass(frameCommandBuffer, acquiredSwapchainImageIndex, request.legacyTextureHandle);
            } else if (requiresShaderCompose) {
                composePendingPresentTextureWithFullscreenPass(
                    frameCommandBuffer,
                    sourceTexture,
                    request.mipLevel,
                    acquiredSwapchainImageIndex,
                    dstWidth,
                    dstHeight
                );
            } else {
                int swapchainImageLayout = trackedSwapchainImageLayout(acquiredSwapchainImageIndex);
                transitionImageLayout(
                    frameCommandBuffer,
                    swapchainImageHandle,
                    VK10.VK_IMAGE_ASPECT_COLOR_BIT,
                    swapchainImageLayout,
                    VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    0,
                    1,
                    1
                );
                trackSwapchainImageLayout(acquiredSwapchainImageIndex, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);

                try (MemoryStack stack = stackPush()) {
                    VkImageCopy.Buffer region = VkImageCopy.calloc(1, stack);
                    region.get(0).srcSubresource()
                        .aspectMask(sourceTexture.aspectMask)
                        .mipLevel(request.mipLevel)
                        .baseArrayLayer(0)
                        .layerCount(1);
                    region.get(0).srcOffset().set(0, 0, 0);
                    region.get(0).dstSubresource()
                        .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                        .mipLevel(0)
                        .baseArrayLayer(0)
                        .layerCount(1);
                    region.get(0).dstOffset().set(0, 0, 0);
                    region.get(0).extent().set(dstWidth, dstHeight, 1);

                    VK10.vkCmdCopyImage(
                        frameCommandBuffer,
                        sourceTexture.imageHandle,
                        VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                        swapchainImageHandle,
                        VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        region
                    );
                }

                transitionImageLayout(
                    frameCommandBuffer,
                    swapchainImageHandle,
                    VK10.VK_IMAGE_ASPECT_COLOR_BIT,
                    VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                    0,
                    1,
                    1
                );
                trackSwapchainImageLayout(acquiredSwapchainImageIndex, KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
            }

            if (!DEBUG_CLEAR_SWAPCHAIN_PRESENT_EXPERIMENT) {
                transitionImageLayout(
                    frameCommandBuffer,
                    sourceTexture.imageHandle,
                    sourceTexture.aspectMask,
                    composeSourceLayout,
                    originalSourceLayout,
                    request.mipLevel,
                    1,
                    1
                );
                trackLayoutForLevel(sourceTexture, request.mipLevel, originalSourceLayout);
            }

            pendingPresentTextureRequest = null;
        }

            // ===== PHASE 1 VALIDATION FUNCTIONS =====
            /**
             * Validates that the swapchain image is in PRESENT_SRC layout before calling vkQueuePresentKHR
             */
            private void validateSwapchainLayoutBeforePresent(int swapchainImageIndex) {
                int currentLayout = trackedSwapchainImageLayout(swapchainImageIndex);
                int expectedLayout = KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
            
                if (currentLayout != expectedLayout) {
                    String layoutName = switch (currentLayout) {
                        case VK10.VK_IMAGE_LAYOUT_UNDEFINED -> "UNDEFINED";
                        case VK10.VK_IMAGE_LAYOUT_GENERAL -> "GENERAL";
                        case VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL -> "COLOR_ATTACHMENT_OPTIMAL";
                        case VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL -> "SHADER_READ_ONLY_OPTIMAL";
                        case VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL -> "TRANSFER_SRC_OPTIMAL";
                        case VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL -> "TRANSFER_DST_OPTIMAL";
                        case EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT -> "ATTACHMENT_FEEDBACK_LOOP_OPTIMAL";
                        default -> "0x" + Integer.toHexString(currentLayout);
                    };
                    throw new IllegalStateException(
                        "PHASE 1 VALIDATION: Swapchain image " + swapchainImageIndex + " not in PRESENT_SRC layout. " +
                        "Current: " + layoutName + " Expected: PRESENT_SRC_KHR"
                    );
                }
            }

        private void composePendingPresentTextureWithFullscreenPass(
            VkCommandBuffer commandBuffer,
            LegacyTextureObject sourceTexture,
            int sourceMipLevel,
            int swapchainImageIndex,
            int dstWidth,
            int dstHeight
        ) {
            VulkanPipelineHandle composePipeline = ensureSwapchainPresentComposePipeline();
            PipelineDescriptor composeDescriptor = swapchainPresentComposeDescriptor;
            if (composeDescriptor == null) {
                throw new IllegalStateException("Swapchain present compose descriptor is unavailable");
            }

            VulkanTextureView sourceView = createPresentSourceTextureView(sourceTexture, sourceMipLevel);
            boolean passStarted = false;
            try {
                ResolvedRenderTargets swapchainTargets = createSwapchainPresentResolvedTargets(swapchainImageIndex, dstWidth, dstHeight);
                VulkanicRenderPassDescriptor descriptor = VulkanicRenderPassDescriptor.color(
                    () -> "Vulkan-SwapchainPresentCompose",
                    swapchainTargets.colorView,
                    java.util.OptionalInt.of(0xFF000000)
                );

                beginRenderPass(commandBuffer.address(), VulkanRenderTargetPlan.textureView(descriptor, swapchainTargets));
                passStarted = true;
                bindPipeline(commandBuffer.address(), composePipeline.getVkPipelineHandle());

                PipelineResourceBindings bindings = PipelineResourceBindings.builder()
                    .bindSampler("InSampler", sourceView, 0)
                    .build();
                try {
                    updateAndBindDescriptorSet(commandBuffer.address(), composePipeline, composeDescriptor, bindings);
                } catch (DescriptorValidationException validationFailure) {
                    LOGGER.warn(
                        "Skipping Vulkan swapchain compose draw for this frame after descriptor validation failure: {}",
                        validationFailure.getMessage()
                    );
                    return;
                }
                draw(commandBuffer.address(), 0, 3);
            } finally {
                if (passStarted && renderPassRecording) {
                    endRenderPass(commandBuffer.address());
                }
                sourceView.close();
            }
        }

        private synchronized VulkanPipelineHandle ensureSwapchainPresentComposePipeline() {
            if (swapchainPresentComposePipeline != null
                && swapchainPresentComposePipeline.isValid()
                && swapchainPresentComposePipelineFormat == swapchainImageFormat) {
                return swapchainPresentComposePipeline;
            }

            releaseSwapchainPresentComposePipeline();

            RenderPipeline renderPipeline = RenderPipelines.TRACY_BLIT;
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || minecraft.getShaderManager() == null) {
                throw new IllegalStateException("Minecraft shader manager is unavailable for Vulkan swapchain present compose pipeline");
            }

            String vertexSource = loadSwapchainComposeShaderSource(minecraft, renderPipeline.getVertexShader(), ShaderType.VERTEX);
            String fragmentSource = loadSwapchainComposeShaderSource(minecraft, renderPipeline.getFragmentShader(), ShaderType.FRAGMENT);
            if (vertexSource == null || fragmentSource == null) {
                throw new IllegalStateException(
                    "Missing shader source for Vulkan swapchain present compose pipeline (vertex="
                        + renderPipeline.getVertexShader() + ", fragment=" + renderPipeline.getFragmentShader() + ")"
                );
            }

            String vertexWithDefines = GlslPreprocessor.injectDefines(vertexSource, renderPipeline.getShaderDefines());
            String fragmentWithDefines = GlslPreprocessor.injectDefines(fragmentSource, renderPipeline.getShaderDefines());
            VulkanicSpirvModule vertexModule = backend.compileSpirvModuleForBackend(
                VulkanicShaderStage.VERTEX,
                vertexWithDefines,
                renderPipeline.getVertexShader().toString(),
                "main"
            );
            VulkanicSpirvModule fragmentModule = backend.compileSpirvModuleForBackend(
                VulkanicShaderStage.FRAGMENT,
                fragmentWithDefines,
                renderPipeline.getFragmentShader().toString(),
                "main"
            );

            PipelineDescriptor descriptor = PipelineDescriptor.fromRenderPipelineAndSpirvModules(
                renderPipeline,
                List.of(vertexModule, fragmentModule)
            );

            long vertShaderModuleHandle = createShaderModule(vertexModule);
            long fragShaderModuleHandle = createShaderModule(fragmentModule);
            try {
                swapchainPresentComposePipeline = createVulkanPipeline(
                    descriptor,
                    vertShaderModuleHandle,
                    fragShaderModuleHandle,
                    swapchainImageFormat,
                    true
                );
                swapchainPresentComposeDescriptor = descriptor;
                swapchainPresentComposePipelineFormat = swapchainImageFormat;
                return swapchainPresentComposePipeline;
            } finally {
                destroyShaderModule(vertShaderModuleHandle);
                destroyShaderModule(fragShaderModuleHandle);
            }
        }

        private void releaseSwapchainPresentComposePipeline() {
            if (swapchainPresentComposePipeline != null) {
                swapchainPresentComposePipeline.close();
                swapchainPresentComposePipeline = null;
            }
            swapchainPresentComposeDescriptor = null;
            swapchainPresentComposePipelineFormat = VK10.VK_FORMAT_UNDEFINED;
        }

        private VulkanTextureView createPresentSourceTextureView(LegacyTextureObject sourceTexture, int mipLevel) {
            VulkanTexture wrapperTexture = new VulkanTexture(
                sourceTexture.imageHandle,
                sourceTexture.memoryHandle,
                sourceTexture.defaultViewHandle,
                VulkanicTexture.USAGE_TEXTURE_BINDING | VulkanicTexture.USAGE_COPY_SRC,
                wrappedTextureFormatForVkFormat(sourceTexture.vkFormat),
                Math.max(1, sourceTexture.width),
                Math.max(1, sourceTexture.height),
                1,
                Math.max(1, sourceTexture.mipLevels),
                "PresentSource-" + sourceTexture.id,
                () -> {}
            );

            long imageViewHandle;
            Runnable closeAction;
            if (mipLevel == 0 && sourceTexture.mipLevels <= 1 && sourceTexture.defaultViewHandle != VK10.VK_NULL_HANDLE) {
                imageViewHandle = sourceTexture.defaultViewHandle;
                closeAction = () -> {};
            } else {
                try (MemoryStack stack = stackPush()) {
                    imageViewHandle = createVkImageView(
                        stack,
                        sourceTexture.imageHandle,
                        sourceTexture.vkFormat,
                        sourceTexture.aspectMask,
                        mipLevel,
                        1,
                        1,
                        false
                    );
                }
                long finalImageViewHandle = imageViewHandle;
                closeAction = () -> destroyImageView(finalImageViewHandle);
            }

            return new VulkanTextureView(wrapperTexture, imageViewHandle, mipLevel, 1, closeAction);
        }

        private ResolvedRenderTargets createSwapchainPresentResolvedTargets(int swapchainImageIndex, int width, int height) {
            if (swapchainImageIndex < 0 || swapchainImageIndex >= swapchainImageHandles.size()) {
                throw new IllegalArgumentException("Invalid swapchain image index for present compose: " + swapchainImageIndex);
            }

            long swapchainImageHandle = swapchainImageHandles.get(swapchainImageIndex);
            long swapchainImageViewHandle = swapchainImageViewHandles.get(swapchainImageIndex);
            VulkanTexture swapchainTexture = new VulkanTexture(
                swapchainImageHandle,
                VK10.VK_NULL_HANDLE,
                swapchainImageViewHandle,
                VulkanicTexture.USAGE_RENDER_ATTACHMENT,
                VulkanicTextureFormat.BGRA8,
                width,
                height,
                1,
                1,
                "SwapchainImage-" + swapchainImageIndex,
                () -> {}
            );
            VulkanTextureView swapchainView = new VulkanTextureView(swapchainTexture, swapchainImageViewHandle, 0, 1, () -> {});
            return new ResolvedRenderTargets(swapchainView, swapchainTexture, null, null, width, height);
        }

        private static VulkanicTextureFormat wrappedTextureFormatForVkFormat(int vkFormat) {
            return switch (vkFormat) {
                case VK10.VK_FORMAT_R8G8B8A8_UNORM, VK10.VK_FORMAT_R8G8B8A8_SRGB -> VulkanicTextureFormat.RGBA8;
                case VK10.VK_FORMAT_R16G16B16A16_SFLOAT -> VulkanicTextureFormat.RGBA16F;
                case VK10.VK_FORMAT_R8G8B8A8_SNORM -> VulkanicTextureFormat.RGBA8_SNORM;
                case VK10.VK_FORMAT_B10G11R11_UFLOAT_PACK32 -> VulkanicTextureFormat.R11F_G11F_B10F;
                case VK10.VK_FORMAT_R16_SFLOAT -> VulkanicTextureFormat.RED16F;
                case VK10.VK_FORMAT_R32_SFLOAT -> VulkanicTextureFormat.RED32F;
                case VK10.VK_FORMAT_B8G8R8A8_UNORM, VK10.VK_FORMAT_B8G8R8A8_SRGB -> VulkanicTextureFormat.BGRA8;
                case VK10.VK_FORMAT_R8_UNORM -> VulkanicTextureFormat.RED8;
                case VK10.VK_FORMAT_R8_SINT -> VulkanicTextureFormat.RED8I;
                case VK10.VK_FORMAT_D32_SFLOAT -> VulkanicTextureFormat.DEPTH32;
                case VK10.VK_FORMAT_D24_UNORM_S8_UINT -> VulkanicTextureFormat.DEPTH24_STENCIL8;
                case VK10.VK_FORMAT_D32_SFLOAT_S8_UINT -> VulkanicTextureFormat.DEPTH32F_STENCIL8;
                default -> throw new IllegalArgumentException(
                    "Unsupported VkFormat for temporary Vulkanic texture wrapper: 0x" + Integer.toHexString(vkFormat)
                );
            };
        }

        @Nullable
        private String loadSwapchainComposeShaderSource(Minecraft minecraft, net.minecraft.resources.ResourceLocation shaderId, ShaderType shaderType) {
            String shaderSource = minecraft.getShaderManager().getShader(shaderId, shaderType);
            if (shaderSource != null) {
                return shaderSource;
            }

            net.minecraft.resources.ResourceLocation shaderFile = shaderType.idConverter().idToFile(shaderId);
            return minecraft.getResourceManager().getResource(shaderFile)
                .map(resource -> {
                    try (java.io.Reader reader = resource.openAsReader()) {
                        return org.apache.commons.io.IOUtils.toString(reader);
                    } catch (java.io.IOException exception) {
                        LOGGER.error("Failed to read fallback shader source {} for Vulkan swapchain compose", shaderFile, exception);
                        return null;
                    }
                })
                .orElse(null);
        }

        private void clearSwapchainImageWithRenderPass(VkCommandBuffer commandBuffer,
                                                       int swapchainImageIndex,
                                                       int sourceTextureHandle) {
            if (swapchainPresentRenderPass == VK10.VK_NULL_HANDLE) {
                throw new IllegalStateException("Swapchain present render pass is unavailable for debug clear composition.");
            }
            if (swapchainImageIndex < 0 || swapchainImageIndex >= swapchainPresentFramebufferHandles.size()) {
                throw new IllegalStateException(
                    "Swapchain present framebuffer is unavailable for image index " + swapchainImageIndex
                );
            }

            if (successfulFramePresentCount < 12) {
                LOGGER.warn(
                    "DEBUG experiment active: clearing acquired swapchain image {} through a color-attachment render pass instead of blitting source texture {}.",
                    swapchainImageIndex,
                    sourceTextureHandle
                );
            }

            try (MemoryStack stack = stackPush()) {
                VkClearValue.Buffer clearValues = VkClearValue.calloc(1, stack);
                float red = ((successfulFramePresentCount + 1) & 1) == 0 ? 0.85f : 0.10f;
                float green = ((successfulFramePresentCount + 1) & 2) == 0 ? 0.15f : 0.80f;
                float blue = ((successfulFramePresentCount + 1) & 4) == 0 ? 0.20f : 0.90f;
                clearValues.get(0).color()
                    .float32(0, red)
                    .float32(1, green)
                    .float32(2, blue)
                    .float32(3, 1.0f);

                VkRenderPassBeginInfo beginInfo = VkRenderPassBeginInfo.calloc(stack)
                    .sType$Default()
                    .renderPass(swapchainPresentRenderPass)
                    .framebuffer(swapchainPresentFramebufferHandles.get(swapchainImageIndex))
                    .pClearValues(clearValues);
                beginInfo.renderArea()
                    .offset(it -> it.x(0).y(0))
                    .extent(it -> it.width(swapchainWidth).height(swapchainHeight));

                VK10.vkCmdBeginRenderPass(commandBuffer, beginInfo, VK10.VK_SUBPASS_CONTENTS_INLINE);
                VK10.vkCmdEndRenderPass(commandBuffer);
            }

            trackSwapchainImageLayout(swapchainImageIndex, KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
        }

        private void ensureAcquiredSwapchainImagePresentLayout() {
            if (!frameInProgress || acquiredSwapchainImageIndex < 0) {
                throw new IllegalStateException("Cannot prepare swapchain image for present without an acquired frame image.");
            }
            if (acquiredSwapchainImageIndex >= swapchainImageHandles.size()) {
                throw new IllegalStateException(
                    "Acquired swapchain image index " + acquiredSwapchainImageIndex
                        + " is outside swapchain image range " + swapchainImageHandles.size()
                );
            }

            long swapchainImageHandle = swapchainImageHandles.get(acquiredSwapchainImageIndex);
            if (swapchainImageHandle == VK10.VK_NULL_HANDLE) {
                throw new IllegalStateException("Acquired swapchain image handle is null");
            }

            int swapchainImageLayout = trackedSwapchainImageLayout(acquiredSwapchainImageIndex);
            VkCommandBuffer frameCommandBuffer = ensureCurrentFrameCommandBufferRecording("ensureAcquiredSwapchainImagePresentLayout");

            if (swapchainImageLayout == KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR) {
                VK10.vkCmdPipelineBarrier(
                    frameCommandBuffer,
                    VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                    VK10.VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
                    0,
                    null,
                    null,
                    null
                );
                return;
            }

            transitionImageLayout(
                frameCommandBuffer,
                swapchainImageHandle,
                VK10.VK_IMAGE_ASPECT_COLOR_BIT,
                swapchainImageLayout,
                KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                0,
                1,
                1
            );
            trackSwapchainImageLayout(acquiredSwapchainImageIndex, KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
        }

        private long beginPrimaryCommandBuffer() {
            VkCommandBuffer nextCommandBuffer = immediateCommandBuffers[currentImmediateSubmitSlot];
            if (nextCommandBuffer == null) {
                throw new IllegalStateException("Primary Vulkan command buffer has not been allocated.");
            }

            if (commandBufferRecording) {
                throw new IllegalStateException("Primary command buffer is already recording.");
            }

            try (MemoryStack stack = stackPush()) {
                if (shouldSynchronizeImmediateSubmitCompletion()) {
                    awaitDeferredImmediateSubmitCompletion();
                    waitForAllSwapchainFrameFences();
                } else {
                    awaitImmediateSubmitSlotCompletion(currentImmediateSubmitSlot);
                }
                commandPool = immediateCommandPools[currentImmediateSubmitSlot];
                descriptorPool = immediateDescriptorPools[currentImmediateSubmitSlot];
                immediateSubmitFence = immediateSubmitFences[currentImmediateSubmitSlot];
                primaryCommandBuffer = nextCommandBuffer;
                flushPendingVulkanResourceDestroys(false);
                checkVk("vkResetCommandPool", VK10.vkResetCommandPool(logicalDevice, commandPool, 0));
                resetSharedDescriptorPool();

                VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType$Default()
                    .flags(VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);

                checkVk("vkBeginCommandBuffer", VK10.vkBeginCommandBuffer(primaryCommandBuffer, beginInfo));
                clearTrackedCommandBufferState(primaryCommandBuffer.address());
                commandBufferRecording = true;
                recordingImmediateSubmitSlot = currentImmediateSubmitSlot;
                reservedImmediateWorkGenerations[currentImmediateSubmitSlot] = reserveWorkGeneration();
                renderPassRecording = false;
                return primaryCommandBuffer.address();
            }
        }

        private VkCommandBuffer currentFrameCommandBuffer() {
            VkCommandBuffer commandBuffer = frameCommandBuffers[currentFrameSyncIndex];
            if (commandBuffer == null) {
                throw new IllegalStateException(
                    "Frame Vulkan command buffer for sync slot " + currentFrameSyncIndex + " has not been allocated."
                );
            }
            return commandBuffer;
        }

        private long currentFrameCommandBufferHandle() {
            return currentFrameCommandBuffer().address();
        }

        private boolean isFrameInProgress() {
            return frameInProgress;
        }

        private boolean isCurrentFrameCommandBufferHandle(long commandBufferHandle) {
            return frameInProgress && currentFrameCommandBufferHandle() == commandBufferHandle;
        }

        private boolean isCurrentFrameCommandBufferRecording() {
            return frameCommandBufferRecording[currentFrameSyncIndex];
        }

        private VkCommandBuffer ensureCurrentFrameCommandBufferRecording(String operation) {
            VkCommandBuffer commandBuffer = currentFrameCommandBuffer();
            if (isCurrentFrameCommandBufferRecording()) {
                return commandBuffer;
            }

            try (MemoryStack stack = stackPush()) {
                java.nio.LongBuffer frameFenceBuffer = stack.longs(currentSwapchainFrameFence());
                checkVk(
                    "vkWaitForFences(frameCommandBuffer[" + currentFrameSyncIndex + "])",
                    VK10.vkWaitForFences(logicalDevice, frameFenceBuffer, true, Long.MAX_VALUE)
                );
                markFenceComplete(currentSwapchainFrameFence());
                checkVk(
                    "vkResetCommandPool(frame[" + currentFrameSyncIndex + "])",
                    VK10.vkResetCommandPool(logicalDevice, frameCommandPools[currentFrameSyncIndex], 0)
                );

                VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType$Default()
                    .flags(VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
                checkVk(
                    "vkBeginCommandBuffer(frame[" + currentFrameSyncIndex + "])",
                    VK10.vkBeginCommandBuffer(commandBuffer, beginInfo)
                );
                clearTrackedCommandBufferState(commandBuffer.address());
                frameCommandBufferRecording[currentFrameSyncIndex] = true;
                return commandBuffer;
            } catch (RuntimeException exception) {
                throw new IllegalStateException(
                    "Failed to begin Vulkan frame command buffer for sync slot " + currentFrameSyncIndex
                        + " during " + operation,
                    exception
                );
            }
        }

        private boolean isPrimaryCommandBufferRecording() {
            return commandBufferRecording;
        }

        private boolean isRenderPassRecording() {
            return renderPassRecording;
        }

        private void submitPrimaryCommandBuffer(long commandBufferHandle) {
            if (renderPassRecording) {
                throw new IllegalStateException("Cannot submit command buffer while a render pass is still active.");
            }
            if (!commandBufferRecording) {
                return;
            }
            int submitSlot = immediateSubmitSlotForKnownCommandBuffer(commandBufferHandle);
            if (submitSlot != recordingImmediateSubmitSlot || submitSlot < 0) {
                return;
            }
            VkCommandBuffer submitCommandBuffer = immediateCommandBuffers[submitSlot];
            if (submitCommandBuffer == null) {
                throw new IllegalStateException("Immediate Vulkan command buffer is unavailable for slot " + submitSlot + ".");
            }
            long submitFence = immediateSubmitFences[submitSlot];
            if (submitFence == VK10.VK_NULL_HANDLE) {
                throw new IllegalStateException("Immediate Vulkan submit fence is unavailable.");
            }

            long totalStartNanos = VulkanPerfAudit.isEnabled() ? System.nanoTime() : 0L;
            long waitBeforeNanos = 0L;
            long queueSubmitNanos = 0L;
            long waitAfterNanos = 0L;
            boolean synchronizeCompletion = shouldSynchronizeImmediateSubmitCompletion();
            try (MemoryStack stack = stackPush()) {
                java.nio.LongBuffer immediateFenceBuffer = stack.longs(submitFence);
                awaitImmediateSubmitSlotCompletion(submitSlot);
                long waitBeforeStartNanos = totalStartNanos != 0L ? System.nanoTime() : 0L;
                checkVk(
                    "vkWaitForFences(immediateSubmit[" + submitSlot + "])",
                    VK10.vkWaitForFences(logicalDevice, immediateFenceBuffer, true, IMMEDIATE_SUBMIT_FENCE_WAIT_TIMEOUT_NANOS)
                );
                if (waitBeforeStartNanos != 0L) {
                    waitBeforeNanos = System.nanoTime() - waitBeforeStartNanos;
                }
                checkVk("vkResetFences(immediateSubmit[" + submitSlot + "])", VK10.vkResetFences(logicalDevice, immediateFenceBuffer));
                checkVk("vkEndCommandBuffer", VK10.vkEndCommandBuffer(primaryCommandBuffer));

                VkSubmitInfo.Buffer submitInfos = VkSubmitInfo.calloc(1, stack)
                    .sType$Default();
                org.lwjgl.PointerBuffer commandBuffers = stack.mallocPointer(1);
                commandBuffers.put(0, submitCommandBuffer.address());
                submitInfos.pCommandBuffers(commandBuffers);

                long queueSubmitStartNanos = totalStartNanos != 0L ? System.nanoTime() : 0L;
                checkVk("vkQueueSubmit(immediate)",
                    VK10.vkQueueSubmit(graphicsQueue, submitInfos, submitFence));
                registerSubmittedWork(submitFence, reservedImmediateWorkGenerations[submitSlot]);
                immediateSubmitSlotsInFlight[submitSlot] = true;
                immediateSubmitInFlight = true;
                commandBufferRecording = false;
                recordingImmediateSubmitSlot = -1;
                clearTrackedCommandBufferState(submitCommandBuffer.address());
                if (queueSubmitStartNanos != 0L) {
                    queueSubmitNanos = System.nanoTime() - queueSubmitStartNanos;
                }

                if (synchronizeCompletion) {
                    long waitAfterStartNanos = totalStartNanos != 0L ? System.nanoTime() : 0L;
                    int waitAfterResult = VK10.vkWaitForFences(
                        logicalDevice,
                        immediateFenceBuffer,
                        true,
                        IMMEDIATE_SUBMIT_FENCE_WAIT_TIMEOUT_NANOS
                    );
                    if (waitAfterStartNanos != 0L) {
                        waitAfterNanos = System.nanoTime() - waitAfterStartNanos;
                    }
                    if (waitAfterResult == VK10.VK_TIMEOUT) {
                        consecutiveImmediateSubmitTimeouts++;
                        long nowNanos = System.nanoTime();
                        if (lastImmediateSubmitTimeoutLogNanos == 0L
                            || nowNanos - lastImmediateSubmitTimeoutLogNanos >= ACQUIRE_TIMEOUT_LOG_INTERVAL_NANOS) {
                            LOGGER.warn(
                                "vkWaitForFences(immediateSubmitComplete[{}]) timed out ({} consecutive); keeping immediate submit in flight and deferring cleanup. {}",
                                submitSlot,
                                consecutiveImmediateSubmitTimeouts,
                                describeWindowState()
                            );
                            lastImmediateSubmitTimeoutLogNanos = nowNanos;
                        }
                        return;
                    }
                    checkVk("vkWaitForFences(immediateSubmitComplete[" + submitSlot + "])", waitAfterResult);
                    markFenceComplete(submitFence);
                    reservedImmediateWorkGenerations[submitSlot] = 0L;
                    immediateSubmitSlotsInFlight[submitSlot] = false;
                    immediateSubmitInFlight = anyImmediateSubmitSlotInFlight();
                    consecutiveImmediateSubmitTimeouts = 0;
                    lastImmediateSubmitTimeoutLogNanos = 0L;
                    destroyTransientRenderPassResources(submitSlot);
                    flushPendingVulkanResourceDestroys(false);
                }

                currentImmediateSubmitSlot = (submitSlot + 1) % IMMEDIATE_SUBMIT_SLOTS;
                commandPool = immediateCommandPools[currentImmediateSubmitSlot];
                descriptorPool = immediateDescriptorPools[currentImmediateSubmitSlot];
                immediateSubmitFence = immediateSubmitFences[currentImmediateSubmitSlot];
                primaryCommandBuffer = immediateCommandBuffers[currentImmediateSubmitSlot];
                if (totalStartNanos != 0L) {
                    long totalNanos = System.nanoTime() - totalStartNanos;
                    long otherNanos = totalNanos - waitBeforeNanos - queueSubmitNanos - waitAfterNanos;
                    VulkanPerfAudit.recordPrimarySubmit(totalNanos, waitBeforeNanos, queueSubmitNanos, waitAfterNanos, otherNanos);
                }
            }
        }

        private int immediateSubmitSlotForKnownCommandBuffer(long commandBufferHandle) {
            for (int slot = 0; slot < IMMEDIATE_SUBMIT_SLOTS; slot++) {
                VkCommandBuffer commandBuffer = immediateCommandBuffers[slot];
                if (commandBuffer != null && commandBuffer.address() == commandBufferHandle) {
                    return slot;
                }
            }
            return -1;
        }

        private void submitFrameSemaphoreBridge() {
            try (MemoryStack stack = stackPush()) {
                long frameFence = currentSwapchainFrameFence();
                long imageAvailableSemaphore = currentSwapchainImageAvailableSemaphore();
                long renderFinishedSemaphore = acquiredSwapchainRenderFinishedSemaphore();

                VkSubmitInfo.Buffer submitInfos = VkSubmitInfo.calloc(1, stack)
                    .sType$Default()
                    .waitSemaphoreCount(1)
                    .pWaitSemaphores(stack.longs(imageAvailableSemaphore))
                    .pWaitDstStageMask(stack.ints(swapchainAcquireWaitStageMask()))
                    .pSignalSemaphores(stack.longs(renderFinishedSemaphore));

                java.nio.LongBuffer frameFenceBuffer = stack.longs(frameFence);
                checkVk("vkResetFences(swapchainFrame)", VK10.vkResetFences(logicalDevice, frameFenceBuffer));
                checkVk("vkQueueSubmit(frameBridge)", VK10.vkQueueSubmit(graphicsQueue, submitInfos, frameFence));
                registerSubmittedWork(frameFence, reservedFrameWorkGenerations[currentFrameSyncIndex]);
            }
        }

        private void submitCurrentFrameCommandBuffer() {
            VkCommandBuffer frameCommandBuffer = currentFrameCommandBuffer();
            if (!isCurrentFrameCommandBufferRecording()) {
                throw new IllegalStateException(
                    "Cannot submit Vulkan frame command buffer for sync slot " + currentFrameSyncIndex + " because it is not recording."
                );
            }

            try (MemoryStack stack = stackPush()) {
                long frameFence = currentSwapchainFrameFence();
                long imageAvailableSemaphore = currentSwapchainImageAvailableSemaphore();
                long renderFinishedSemaphore = acquiredSwapchainRenderFinishedSemaphore();

                checkVk(
                    "vkEndCommandBuffer(frame[" + currentFrameSyncIndex + "])",
                    VK10.vkEndCommandBuffer(frameCommandBuffer)
                );

                VkSubmitInfo.Buffer submitInfos = VkSubmitInfo.calloc(1, stack)
                    .sType$Default()
                    .waitSemaphoreCount(1)
                    .pWaitSemaphores(stack.longs(imageAvailableSemaphore))
                    .pWaitDstStageMask(stack.ints(swapchainAcquireWaitStageMask()))
                    .pSignalSemaphores(stack.longs(renderFinishedSemaphore));
                org.lwjgl.PointerBuffer commandBuffers = stack.mallocPointer(1);
                commandBuffers.put(0, frameCommandBuffer.address());
                submitInfos.pCommandBuffers(commandBuffers);

                java.nio.LongBuffer frameFenceBuffer = stack.longs(frameFence);
                checkVk("vkResetFences(swapchainFrame)", VK10.vkResetFences(logicalDevice, frameFenceBuffer));
                checkVk("vkQueueSubmit(frame)", VK10.vkQueueSubmit(graphicsQueue, submitInfos, frameFence));
                registerSubmittedWork(frameFence, reservedFrameWorkGenerations[currentFrameSyncIndex]);

                frameCommandBufferRecording[currentFrameSyncIndex] = false;
                clearTrackedCommandBufferState(frameCommandBuffer.address());
                backend.boundPipelineResourcesByCommandBuffer.remove(frameCommandBuffer.address());
            }
        }

	        private void applyResourceBarriers(long commandBufferHandle, VulkanicResourceBarriers barriers) {
	            VkCommandBuffer activeCommandBuffer = requireRecordingCommandBuffer(commandBufferHandle, "applyResourceBarriers");

            BarrierMasks masks = toVkBarrierMasks(barriers);
            try (MemoryStack stack = stackPush()) {
                VkMemoryBarrier.Buffer memoryBarriers = VkMemoryBarrier.calloc(1, stack);
                memoryBarriers.get(0)
                    .sType$Default()
                    .srcAccessMask(masks.srcAccessMask())
                    .dstAccessMask(masks.dstAccessMask());

                VK10.vkCmdPipelineBarrier(
                    activeCommandBuffer,
                    masks.srcStageMask(),
                    masks.dstStageMask(),
                    0,
                    memoryBarriers,
                    null,
                    null
	                );
	            }
	        }

	        private void applyConservativeMemoryBarrier(long commandBufferHandle) {
	            VkCommandBuffer activeCommandBuffer = requireRecordingCommandBuffer(commandBufferHandle, "memoryBarrier");
	            try (MemoryStack stack = stackPush()) {
	                VkMemoryBarrier.Buffer memoryBarriers = VkMemoryBarrier.calloc(1, stack);
	                memoryBarriers.get(0)
	                    .sType$Default()
	                    .srcAccessMask(VK10.VK_ACCESS_MEMORY_WRITE_BIT)
	                    .dstAccessMask(VK10.VK_ACCESS_MEMORY_READ_BIT | VK10.VK_ACCESS_MEMORY_WRITE_BIT);

	                VK10.vkCmdPipelineBarrier(
	                    activeCommandBuffer,
	                    VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
	                    VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
	                    0,
	                    memoryBarriers,
	                    null,
	                    null
	                );
	            }
	        }

	        private VkCommandBuffer requireRecordingCommandBuffer(long commandBufferHandle, String operation) {
            if (primaryCommandBuffer == null) {
                throw new IllegalStateException("Primary Vulkan command buffer has not been allocated.");
            }

            if (commandBufferHandle == primaryCommandBuffer.address()) {
                if (!commandBufferRecording) {
                    if (immediateSubmitInFlight && frameInProgress) {
                        return ensureCurrentFrameCommandBufferRecording(operation + " (rerouted from deferred primary submit)");
                    }
                    beginPrimaryCommandBuffer();
                }
                return primaryCommandBuffer;
            }

            int immediateSlot = immediateSubmitSlotForKnownCommandBuffer(commandBufferHandle);
            if (immediateSlot >= 0) {
                if (commandBufferRecording) {
                    if (immediateSlot == recordingImmediateSubmitSlot) {
                        return immediateCommandBuffers[immediateSlot];
                    }
                    return primaryCommandBuffer;
                }
                if (immediateSubmitInFlight && frameInProgress && immediateSubmitSlotsInFlight[immediateSlot]) {
                    return ensureCurrentFrameCommandBufferRecording(operation + " (rerouted from deferred primary submit)");
                }
                currentImmediateSubmitSlot = immediateSlot;
                beginPrimaryCommandBuffer();
                return primaryCommandBuffer;
            }

            VkCommandBuffer frameCommandBuffer = currentFrameCommandBuffer();
            if (frameCommandBuffer != null && commandBufferHandle == frameCommandBuffer.address()) {
                if (!isCurrentFrameCommandBufferRecording()) {
                    frameCommandBuffer = ensureCurrentFrameCommandBufferRecording(operation);
                }
                return frameCommandBuffer;
            }

            throw new IllegalArgumentException(
                operation + " received unknown VkCommandBuffer handle. Expected primary 0x"
                    + Long.toHexString(primaryCommandBuffer.address())
                    + (frameCommandBuffer == null ? "" : " or frame 0x" + Long.toHexString(frameCommandBuffer.address()))
                    + " but got 0x" + Long.toHexString(commandBufferHandle)
            );
        }

        private void ensureRecordingCommandBuffer(long commandBufferHandle, String operation) {
            requireRecordingCommandBuffer(commandBufferHandle, operation);
        }

        private VulkanRenderPassCompatibilityKey beginRenderPass(long commandBufferHandle, VulkanRenderTargetPlan plan) {
            VulkanicRenderPassDescriptor descriptor = plan.requireRenderPassDescriptor();
            ResolvedRenderTargets targets = plan.requireTextureViewTargets();
            VulkanPerfAudit.recordRenderPassBegin();
            VkCommandBuffer activeCommandBuffer = requireRecordingCommandBuffer(commandBufferHandle, "beginRenderPass");
            if (renderPassRecording) {
                throw new IllegalStateException("Nested Vulkan render passes are not supported yet.");
            }

            VulkanTextureView colorView = targets.colorView;
            VulkanicTexture colorTexture = targets.colorTexture;
            VulkanTextureView depthView = targets.depthView;
            VulkanicTexture depthTexture = targets.depthTexture;
            int width = targets.width;
            int height = targets.height;
            VulkanicRenderPassDescriptor.DepthAttachment depthAttachment = descriptor.depthAttachment();
            LegacyTextureObject legacyColorTexture = tryResolveLegacyTexture(colorTexture);
            LegacyTextureObject legacyDepthTexture = targets.hasDepthTarget()
                ? tryResolveLegacyTexture(depthTexture)
                : null;

            long renderPassHandle = VK10.VK_NULL_HANDLE;
            long framebufferHandle = VK10.VK_NULL_HANDLE;
            try (MemoryStack stack = stackPush()) {
                int attachmentCount = targets.hasDepthTarget() ? 2 : 1;
	                VkAttachmentDescription.Buffer attachments = attachmentCount == 0
	                    ? null
	                    : VkAttachmentDescription.calloc(attachmentCount, stack);
                boolean swapchainColorAttachment = isSwapchainImageViewHandle(colorView.getVkImageViewHandle());
                int swapchainColorImageIndex = swapchainColorAttachment
                    ? swapchainImageIndexForViewHandle(colorView.getVkImageViewHandle())
                    : -1;
                boolean usePersistentSwapchainPass = swapchainColorAttachment
                    && !targets.hasDepthTarget()
                    && descriptor.colorAttachment().loadOp() == VulkanicRenderPassDescriptor.LoadOp.CLEAR
                    && descriptor.colorAttachment().storeOp() == VulkanicRenderPassDescriptor.StoreOp.STORE;
                if (legacyColorTexture != null && debugColorAttachmentLogCount < 120) {
                    debugColorAttachmentLogCount++;
                    int clearColor = descriptor.colorAttachment().clearColor().orElse(0);
                    LOGGER.info(
                        "Vulkan beginRenderPass color#{} texId={} loadOp={} clearColorPresent={} clearColor=0x{} extent={}x{} swapchainAttachment={}",
                        debugColorAttachmentLogCount,
                        legacyColorTexture.id,
                        descriptor.colorAttachment().loadOp(),
                        descriptor.colorAttachment().clearColor().isPresent(),
                        Integer.toHexString(clearColor),
                        width,
                        height,
                        swapchainColorAttachment
                    );
                }
                if (usePersistentSwapchainPass) {
                    if (swapchainPresentRenderPass == VK10.VK_NULL_HANDLE) {
                        throw new IllegalStateException("Swapchain present render pass is unavailable for swapchain-targeted compose pass.");
                    }
                    if (swapchainColorImageIndex < 0 || swapchainColorImageIndex >= swapchainPresentFramebufferHandles.size()) {
                        throw new IllegalStateException(
                            "Swapchain present framebuffer is unavailable for image index " + swapchainColorImageIndex
                        );
                    }

                    VkClearValue.Buffer clearValues = VkClearValue.calloc(1, stack);
                    int argb = descriptor.colorAttachment().clearColor().orElse(0);
                    float a = ((argb >> 24) & 0xFF) / 255.0f;
                    float r = ((argb >> 16) & 0xFF) / 255.0f;
                    float g = ((argb >> 8) & 0xFF) / 255.0f;
                    float b = (argb & 0xFF) / 255.0f;
                    clearValues.get(0).color()
                        .float32(0, r)
                        .float32(1, g)
                        .float32(2, b)
                        .float32(3, a);

                    VkRenderPassBeginInfo beginInfo = VkRenderPassBeginInfo.calloc(stack)
                        .sType$Default()
                        .renderPass(swapchainPresentRenderPass)
                        .framebuffer(swapchainPresentFramebufferHandles.get(swapchainColorImageIndex))
                        .pClearValues(clearValues);
                    beginInfo.renderArea()
                        .offset(it -> it.x(0).y(0))
                        .extent(it -> it.width(width).height(height));

                    VK10.vkCmdBeginRenderPass(activeCommandBuffer, beginInfo, VK10.VK_SUBPASS_CONTENTS_INLINE);

                    VkViewport.Buffer defaultViewport = VkViewport.calloc(1, stack);
                    defaultViewport.get(0)
                        .x(0.0f)
                        .y(0.0f)
                        .width((float) width)
                        .height((float) height)
                        .minDepth(0.0f)
                        .maxDepth(1.0f);
                    VK10.vkCmdSetViewport(activeCommandBuffer, 0, defaultViewport);

                    VkRect2D.Buffer defaultScissor = VkRect2D.calloc(1, stack);
                    defaultScissor.get(0)
                        .offset(it -> it.x(0).y(0))
                        .extent(it -> it.width(width).height(height));
                    VK10.vkCmdSetScissor(activeCommandBuffer, 0, defaultScissor);

                    scissorTestEnabled = false;
                    hasCachedScissorRect = false;
                    cachedScissorX = 0;
                    cachedScissorY = 0;
                    cachedScissorWidth = width;
                    cachedScissorHeight = height;

	                    if (legacyColorTexture != null) {
	                        trackLayoutForLevel(legacyColorTexture, 0, VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
	                    }
	                    activeRenderPassColorTextures.clear();
	                    activeRenderPassColorFinalLayouts.clear();
	                    trackSwapchainImageLayout(swapchainColorImageIndex, VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
                    renderPassSwapchainImageIndex = swapchainColorImageIndex;
                    activeRenderPassTargetsSwapchain = true;
                    renderPassRecording = true;
                    activeRenderPassCompatibilityKey = VulkanRenderPassCompatibilityKey.swapchainPresent(swapchainImageFormat);
                    activeRenderPassWidth = width;
                    activeRenderPassHeight = height;
                    return VulkanRenderPassCompatibilityKey.swapchainPresent(swapchainImageFormat);
	                }

                int colorInitialLayout = swapchainColorAttachment
                    ? trackedSwapchainImageLayout(swapchainColorImageIndex)
                    : legacyColorTexture != null && trackedLayoutForLevel(legacyColorTexture, 0) != VK10.VK_IMAGE_LAYOUT_UNDEFINED
                        ? trackedLayoutForLevel(legacyColorTexture, 0)
                        : VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
                if (!swapchainColorAttachment && legacyColorTexture != null) {
                    colorInitialLayout = imageLayoutForUsage(
                        descriptor.colorAttachment().initialUsage(),
                        false,
                        legacyColorTexture.feedbackLoopCapable,
                        colorInitialLayout
                    );
                    ensureTextureLayoutBeforeRenderPass(
                        legacyColorTexture,
                        colorInitialLayout,
                        descriptor.colorAttachment().initialUsage()
                    );
                }
                int colorFinalLayout = swapchainColorAttachment
                    ? KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR
                    : imageLayoutForUsage(
                        descriptor.colorAttachment().finalUsage(),
                        false,
                        legacyColorTexture != null && legacyColorTexture.feedbackLoopCapable,
                        legacyColorTexture != null && legacyColorTexture.feedbackLoopCapable
                            ? EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT
                            : VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
                    );

                attachments.get(0)
                    .format(swapchainColorAttachment
                        ? swapchainImageFormat
                        : toVkFormat(colorTexture.getVulkanicFormat()))
                    .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(toVkLoadOp(descriptor.colorAttachment().loadOp()))
                    .storeOp(toVkStoreOp(descriptor.colorAttachment().storeOp()))
                    .stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(colorInitialLayout)
                    .finalLayout(colorFinalLayout);

                VkAttachmentReference.Buffer colorReference = VkAttachmentReference.calloc(1, stack);
                int colorSubpassLayoutSingle = (!swapchainColorAttachment && legacyColorTexture != null && legacyColorTexture.feedbackLoopCapable)
                    ? EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT
                    : VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
                if (!swapchainColorAttachment) {
                    colorSubpassLayoutSingle = imageLayoutForUsage(
                        descriptor.colorAttachment().passUsage(),
                        false,
                        legacyColorTexture != null && legacyColorTexture.feedbackLoopCapable,
                        colorSubpassLayoutSingle
                    );
                }
                colorReference.get(0)
                    .attachment(0)
                    .layout(colorSubpassLayoutSingle);

	                VkAttachmentReference depthReference = null;
	                int depthFinalLayout = VK10.VK_IMAGE_LAYOUT_UNDEFINED;
	                if (targets.hasDepthTarget()) {
                    boolean depthFeedback = legacyDepthTexture != null && legacyDepthTexture.feedbackLoopCapable;
                    int depthSubpassLayout = depthFeedback
                        ? EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT
                        : VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
                    int depthInitialLayout = legacyDepthTexture != null
                        && trackedLayoutForLevel(legacyDepthTexture, 0) != VK10.VK_IMAGE_LAYOUT_UNDEFINED
                            ? trackedLayoutForLevel(legacyDepthTexture, 0)
                            : depthSubpassLayout;
                    if (legacyDepthTexture != null) {
                        depthInitialLayout = imageLayoutForUsage(
                            depthAttachment.initialUsage(),
                            true,
                            legacyDepthTexture.feedbackLoopCapable,
                            depthInitialLayout
                        );
                        ensureTextureLayoutBeforeRenderPass(
                            legacyDepthTexture,
                            depthInitialLayout,
                            depthAttachment.initialUsage()
                        );
                    }
	                    depthFinalLayout = depthFeedback
	                        ? EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT
	                        : VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL;
                    depthFinalLayout = imageLayoutForUsage(
                        depthAttachment.finalUsage(),
                        true,
                        depthFeedback,
                        depthFinalLayout
                    );
                    boolean depthHasStencil = depthTexture.getVulkanicFormat().hasStencilAspect();
                    attachments.get(1)
                        .format(toVkFormat(depthTexture.getVulkanicFormat()))
                        .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                        .loadOp(toVkLoadOp(depthAttachment.loadOp()))
                        .storeOp(toVkStoreOp(depthAttachment.storeOp()))
                        .stencilLoadOp(depthHasStencil
                            ? toVkLoadOp(depthAttachment.loadOp())
                            : VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                        .stencilStoreOp(depthHasStencil
                            ? toVkStoreOp(depthAttachment.storeOp())
                            : VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                        .initialLayout(depthInitialLayout)
                        .finalLayout(depthFinalLayout);

                    depthReference = VkAttachmentReference.calloc(stack)
                        .attachment(1)
                        .layout(imageLayoutForUsage(
                            depthAttachment.passUsage(),
                            true,
                            depthFeedback,
                            depthSubpassLayout
                        ));
                }

                VkSubpassDescription.Buffer subpasses = VkSubpassDescription.calloc(1, stack);
                subpasses.get(0)
                    .pipelineBindPoint(VK10.VK_PIPELINE_BIND_POINT_GRAPHICS)
                    .colorAttachmentCount(1)
                    .pColorAttachments(colorReference)
                    .pDepthStencilAttachment(depthReference);

                // Base external→subpass dependency for layout transition ordering.
                // When using attachment feedback loop, also add a self-dependency with
                // VK_DEPENDENCY_FEEDBACK_LOOP_BIT_EXT so the driver knows reads
                // in the same subpass may sample from the current attachment.
                boolean needsFeedbackDep = (!swapchainColorAttachment && legacyColorTexture != null && legacyColorTexture.feedbackLoopCapable)
                    || (legacyDepthTexture != null && legacyDepthTexture.feedbackLoopCapable);
                VulkanRenderPassCompatibilityKey compatibilityKey = VulkanRenderPassCompatibilityKey.textureView(
                    List.of(attachments.get(0).format()),
                    targets.hasDepthTarget() ? attachments.get(1).format() : VK10.VK_FORMAT_UNDEFINED,
                    needsFeedbackDep
                );
                VkSubpassDependency.Buffer dependencies = allocateCompatibleSubpassDependencies(stack, compatibilityKey);

                VkRenderPassCreateInfo renderPassCreateInfo = VkRenderPassCreateInfo.calloc(stack)
                    .sType$Default()
                    .pAttachments(attachments)
                    .pSubpasses(subpasses)
                    .pDependencies(dependencies);
                java.nio.LongBuffer pRenderPass = stack.mallocLong(1);
                checkVk("vkCreateRenderPass(legacy)",
                    VK10.vkCreateRenderPass(logicalDevice, renderPassCreateInfo, null, pRenderPass));
                renderPassHandle = pRenderPass.get(0);

                java.nio.LongBuffer pAttachments = stack.mallocLong(attachmentCount);
                pAttachments.put(0, colorView.getVkImageViewHandle());
                if (targets.hasDepthTarget()) {
                    pAttachments.put(1, depthView.getVkImageViewHandle());
                }

                VkFramebufferCreateInfo framebufferCreateInfo = VkFramebufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .renderPass(renderPassHandle)
                    .pAttachments(pAttachments)
                    .width(width)
                    .height(height)
                    .layers(1);

                java.nio.LongBuffer pFramebuffer = stack.mallocLong(1);
                checkVk("vkCreateFramebuffer",
                    VK10.vkCreateFramebuffer(logicalDevice, framebufferCreateInfo, null, pFramebuffer));
                framebufferHandle = pFramebuffer.get(0);

                VkClearValue.Buffer clearValues = VkClearValue.calloc(attachmentCount, stack);
                if (descriptor.colorAttachment().loadOp() == VulkanicRenderPassDescriptor.LoadOp.CLEAR) {
                    int argb = descriptor.colorAttachment().clearColor().orElse(0);
                    float a = ((argb >> 24) & 0xFF) / 255.0f;
                    float r = ((argb >> 16) & 0xFF) / 255.0f;
                    float g = ((argb >> 8) & 0xFF) / 255.0f;
                    float b = (argb & 0xFF) / 255.0f;
                    clearValues.get(0).color()
                        .float32(0, r)
                        .float32(1, g)
                        .float32(2, b)
                        .float32(3, a);
                }

                if (targets.hasDepthTarget() && depthAttachment.loadOp() == VulkanicRenderPassDescriptor.LoadOp.CLEAR) {
                    float clearDepth = (float) depthAttachment.clearDepth().orElse(1.0);
                    clearValues.get(1).depthStencil().depth(clearDepth).stencil(0);
                }

                VkRenderPassBeginInfo beginInfo = VkRenderPassBeginInfo.calloc(stack)
                    .sType$Default()
                    .renderPass(renderPassHandle)
                    .framebuffer(framebufferHandle)
                    .pClearValues(clearValues);
                beginInfo.renderArea()
                    .offset(it -> it.x(0).y(0))
                    .extent(it -> it.width(width).height(height));


                VK10.vkCmdBeginRenderPass(activeCommandBuffer, beginInfo, VK10.VK_SUBPASS_CONTENTS_INLINE);

                VkViewport.Buffer defaultViewport = VkViewport.calloc(1, stack);
                defaultViewport.get(0)
                    .x(0.0f)
					.y(swapchainColorAttachment ? 0.0f : (float) height)
                    .width((float) width)
					.height(swapchainColorAttachment ? (float) height : -(float) height)
                    .minDepth(0.0f)
                    .maxDepth(1.0f);
                VK10.vkCmdSetViewport(activeCommandBuffer, 0, defaultViewport);

                VkRect2D.Buffer defaultScissor = VkRect2D.calloc(1, stack);
                defaultScissor.get(0)
                    .offset(it -> it.x(0).y(0))
                    .extent(it -> it.width(width).height(height));
                VK10.vkCmdSetScissor(activeCommandBuffer, 0, defaultScissor);

                // Start each render pass with an unclipped scissor baseline.
                scissorTestEnabled = false;
                hasCachedScissorRect = false;
                cachedScissorX = 0;
                cachedScissorY = 0;
                cachedScissorWidth = width;
                cachedScissorHeight = height;

                if (legacyColorTexture != null) {
                    int colorActiveLayout = legacyColorTexture.feedbackLoopCapable
                        ? EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT
                        : VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
                    trackLayoutForLevel(legacyColorTexture, 0, colorActiveLayout);
                    activeRenderPassColorTextures.clear();
                    activeRenderPassColorTextures.add(legacyColorTexture);
                    activeRenderPassColorFinalLayouts.clear();
                    activeRenderPassColorFinalLayouts.add(colorFinalLayout);
                } else {
                    activeRenderPassColorTextures.clear();
                    activeRenderPassColorFinalLayouts.clear();
                }
                if (swapchainColorAttachment && swapchainColorImageIndex >= 0) {
                    trackSwapchainImageLayout(swapchainColorImageIndex, VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
                    renderPassSwapchainImageIndex = swapchainColorImageIndex;
                } else {
                    renderPassSwapchainImageIndex = -1;
                }
                activeRenderPassTargetsSwapchain = swapchainColorAttachment;
                if (legacyDepthTexture != null) {
                    int depthActiveLayout = legacyDepthTexture.feedbackLoopCapable
                        ? EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT
                        : VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
                    trackLayoutForLevel(legacyDepthTexture, 0, depthActiveLayout);
                    activeRenderPassDepthTexture = legacyDepthTexture;
                    activeRenderPassDepthFinalLayout = depthFinalLayout;
                } else {
                    activeRenderPassDepthTexture = null;
                    activeRenderPassDepthFinalLayout = VK10.VK_IMAGE_LAYOUT_UNDEFINED;
                }

                renderPassRecording = true;
                activeRenderPassCompatibilityKey = compatibilityKey;
                activeRenderPassWidth = width;
                activeRenderPassHeight = height;
                trackTransientRenderPassHandle(renderPassHandle);
	                trackTransientFramebufferHandle(framebufferHandle);
                return compatibilityKey;
	            } catch (RuntimeException exception) {
                if (framebufferHandle != VK10.VK_NULL_HANDLE) {
                    VK10.vkDestroyFramebuffer(logicalDevice, framebufferHandle, null);
                }
                if (renderPassHandle != VK10.VK_NULL_HANDLE) {
                    VK10.vkDestroyRenderPass(logicalDevice, renderPassHandle, null);
                }
	                throw exception;
	            }
	        }

	        private VulkanRenderPassCompatibilityKey beginFramebufferRenderPass(long commandBufferHandle, VulkanRenderTargetPlan plan) {
            ResolvedFramebufferTargets targets = plan.requireFramebufferTargets();
            VulkanPerfAudit.recordRenderPassBegin();
            VkCommandBuffer activeCommandBuffer = requireRecordingCommandBuffer(commandBufferHandle, "beginFramebufferRenderPass");
            if (renderPassRecording) {
                throw new IllegalStateException("Nested Vulkan render passes are not supported yet.");
            }

            int colorAttachmentCount = targets.colorAttachmentCount();
            int attachmentCount = colorAttachmentCount + (targets.hasDepthTarget() ? 1 : 0);
            long renderPassHandle = VK10.VK_NULL_HANDLE;
            long framebufferHandle = VK10.VK_NULL_HANDLE;
            try (MemoryStack stack = stackPush()) {
                VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(attachmentCount, stack);
	                for (int colorIndex = 0; colorIndex < colorAttachmentCount; colorIndex++) {
	                    LegacyTextureObject colorTexture = targets.colorTextures.get(colorIndex);
	                    boolean colorFeedback = colorTexture.feedbackLoopCapable;
	                    int colorSubpassLayout = colorFeedback
	                        ? EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT
	                        : VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
	                    colorSubpassLayout = imageLayoutForUsage(
	                        targets.colorPassUsage(colorIndex),
	                        false,
	                        colorFeedback,
	                        colorSubpassLayout
	                    );
	                    int colorFinalLayout = colorFeedback
	                        ? EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT
	                        : VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
	                    colorFinalLayout = imageLayoutForUsage(
	                        targets.colorFinalUsage(colorIndex),
	                        false,
	                        colorFeedback,
	                        colorFinalLayout
	                    );
                    // For feedbackLoopCapable textures, always use the subpass layout as
                    // initialLayout so the render pass structure is identical each frame and
                    // can be permanently cached. An explicit pre-barrier (below) ensures the
                    // image is already in this layout before the render pass begins.
	                    int initialLayout = colorFeedback
	                        ? colorSubpassLayout
	                        : (trackedLayoutForLevel(colorTexture, 0) != VK10.VK_IMAGE_LAYOUT_UNDEFINED
	                            ? trackedLayoutForLevel(colorTexture, 0)
	                            : colorSubpassLayout);
	                    initialLayout = imageLayoutForUsage(
	                        targets.colorInitialUsage(colorIndex),
	                        false,
	                        colorFeedback,
	                        initialLayout
	                    );
	                    ensureTextureLayoutBeforeRenderPass(
	                        colorTexture,
	                        initialLayout,
	                        targets.colorInitialUsage(colorIndex)
	                    );
	                    attachments.get(colorIndex)
                        .format(colorTexture.vkFormat)
                        .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                        .loadOp(toVkLoadOp(targets.colorLoadOp(colorIndex)))
                        .storeOp(toVkStoreOp(targets.colorStoreOp(colorIndex)))
                        .stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                        .stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                        .initialLayout(initialLayout)
                        .finalLayout(colorFinalLayout);
                }

	                VkAttachmentReference.Buffer colorReferences = colorAttachmentCount == 0
	                    ? null
	                    : VkAttachmentReference.calloc(colorAttachmentCount, stack);
	                for (int colorIndex = 0; colorIndex < colorAttachmentCount; colorIndex++) {
	                    LegacyTextureObject colorTexture = targets.colorTextures.get(colorIndex);
	                    int colorSubpassLayout = colorTexture.feedbackLoopCapable
	                        ? EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT
	                        : VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
	                    colorSubpassLayout = imageLayoutForUsage(
	                        targets.colorPassUsage(colorIndex),
	                        false,
	                        colorTexture.feedbackLoopCapable,
	                        colorSubpassLayout
	                    );
	                    colorReferences.get(colorIndex)
                        .attachment(colorIndex)
                        .layout(colorSubpassLayout);
                }

                VkAttachmentReference depthReference = null;
                if (targets.hasDepthTarget()) {
                    int depthAttachmentIndex = colorAttachmentCount;
                    LegacyTextureObject depthTexture = targets.depthTexture;
                    boolean depthFeedback2 = depthTexture.feedbackLoopCapable;
	                    int depthSubpassLayout2 = depthFeedback2
	                        ? EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT
	                        : VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
	                    depthSubpassLayout2 = imageLayoutForUsage(
	                        targets.depthPassUsage(),
	                        true,
	                        depthFeedback2,
	                        depthSubpassLayout2
	                    );
	                    int depthInitialLayout2 = depthFeedback2
	                        ? depthSubpassLayout2
	                        : (trackedLayoutForLevel(depthTexture, 0) != VK10.VK_IMAGE_LAYOUT_UNDEFINED
	                            ? trackedLayoutForLevel(depthTexture, 0)
	                            : depthSubpassLayout2);
	                    depthInitialLayout2 = imageLayoutForUsage(
	                        targets.depthInitialUsage(),
	                        true,
	                        depthFeedback2,
	                        depthInitialLayout2
	                    );
	                    ensureTextureLayoutBeforeRenderPass(
	                        depthTexture,
	                        depthInitialLayout2,
	                        targets.depthInitialUsage()
	                    );
	                    int depthFinalLayout2 = depthFeedback2
	                        ? EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT
	                        : VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL;
	                    depthFinalLayout2 = imageLayoutForUsage(
	                        targets.depthFinalUsage(),
	                        true,
	                        depthFeedback2,
	                        depthFinalLayout2
	                    );
                    boolean depthHasStencil = hasStencilAspect(depthTexture.vkFormat);
                    attachments.get(depthAttachmentIndex)
                        .format(depthTexture.vkFormat)
                        .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                        .loadOp(toVkLoadOp(targets.depthLoadOp()))
                        .storeOp(toVkStoreOp(targets.depthStoreOp()))
                        .stencilLoadOp(depthHasStencil
                            ? toVkLoadOp(targets.depthLoadOp())
                            : VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                        .stencilStoreOp(depthHasStencil
                            ? toVkStoreOp(targets.depthStoreOp())
                            : VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                        .initialLayout(depthInitialLayout2)
                        .finalLayout(depthFinalLayout2);
                    depthReference = VkAttachmentReference.calloc(stack)
                        .attachment(depthAttachmentIndex)
                        .layout(depthSubpassLayout2);
                }

                VkSubpassDescription.Buffer subpasses = VkSubpassDescription.calloc(1, stack);
                subpasses.get(0)
                    .pipelineBindPoint(VK10.VK_PIPELINE_BIND_POINT_GRAPHICS)
                    .colorAttachmentCount(colorAttachmentCount)
                    .pColorAttachments(colorReferences)
                    .pDepthStencilAttachment(depthReference);

                // Base external→subpass and subpass→external dependencies plus optional feedback loop self-dependency.
                boolean hasFeedbackLoop = targets.colorTextures.stream().anyMatch(t -> t.feedbackLoopCapable)
                    || (targets.hasDepthTarget() && targets.depthTexture.feedbackLoopCapable);
                VulkanRenderPassCompatibilityKey compatibilityKey = VulkanRenderPassCompatibilityKey.framebuffer(
                    targets.colorFormats(),
                    targets.hasDepthTarget() ? targets.depthTexture.vkFormat : VK10.VK_FORMAT_UNDEFINED,
                    hasFeedbackLoop
                );
                VkSubpassDependency.Buffer dependencies = allocateCompatibleSubpassDependencies(stack, compatibilityKey);

                // Build a stable typed cache key from the render pass configuration. For
                // feedback-loop passes the initialLayout is now constant, so the key
                // is fully deterministic and we can reuse the same VkRenderPass handle
                // indefinitely, avoiding repeated create/destroy cycles that trigger an
                // NVIDIA driver bug when the same opaque handle is recycled.
                List<VulkanRenderPassKey.Attachment> colorAttachmentKeys = new ArrayList<>(colorAttachmentCount);
                for (int ci = 0; ci < colorAttachmentCount; ci++) {
                    colorAttachmentKeys.add(new VulkanRenderPassKey.Attachment(
                        attachments.get(ci).format(),
                        attachments.get(ci).loadOp(),
                        attachments.get(ci).storeOp(),
                        attachments.get(ci).initialLayout(),
                        attachments.get(ci).finalLayout(),
                        colorReferences.get(ci).layout()
                    ));
                }
                VulkanRenderPassKey.Attachment depthAttachmentKey = null;
                if (targets.hasDepthTarget()) {
                    depthAttachmentKey = new VulkanRenderPassKey.Attachment(
                        attachments.get(colorAttachmentCount).format(),
                        attachments.get(colorAttachmentCount).loadOp(),
                        attachments.get(colorAttachmentCount).storeOp(),
                        attachments.get(colorAttachmentCount).initialLayout(),
                        attachments.get(colorAttachmentCount).finalLayout(),
                        depthReference.layout()
                    );
                }
                VulkanRenderPassKey renderPassKey =
                    VulkanRenderPassKey.framebuffer(colorAttachmentKeys, depthAttachmentKey, hasFeedbackLoop);

                Long cachedRenderPass2 = permanentRenderPassCache.get(renderPassKey);
                if (cachedRenderPass2 != null) {
                    renderPassHandle = cachedRenderPass2;
                } else {
                    VkRenderPassCreateInfo renderPassCreateInfo2 = VkRenderPassCreateInfo.calloc(stack)
                        .sType$Default()
                        .pAttachments(attachments)
                        .pSubpasses(subpasses)
                        .pDependencies(dependencies);
                    java.nio.LongBuffer pRenderPass2 = stack.mallocLong(1);
                    checkVk("vkCreateRenderPass(framebuffer)",
                        VK10.vkCreateRenderPass(logicalDevice, renderPassCreateInfo2, null, pRenderPass2));
                    renderPassHandle = pRenderPass2.get(0);
                    permanentRenderPassCache.put(renderPassKey, renderPassHandle);
                }

                if (debugColorAttachmentLogCount < 200) {
                    debugColorAttachmentLogCount++;
                    LOGGER.info(
                        "Vulkan beginFramebufferRenderPass colorCount={} depthPresent={} hasFeedbackLoop={} renderPass=0x{} colorTargets={} depthTarget={}",
                        colorAttachmentCount,
                        targets.hasDepthTarget(),
                        hasFeedbackLoop,
                        Long.toHexString(renderPassHandle),
                        targets.colorTextures.stream()
                            .map(texture -> texture.id + ":" + texture.width + "x" + texture.height + ":feedback=" + texture.feedbackLoopCapable)
                            .toList(),
                        targets.hasDepthTarget()
                            ? targets.depthTexture.id + ":" + targets.depthTexture.width + "x" + targets.depthTexture.height + ":feedback=" + targets.depthTexture.feedbackLoopCapable
                            : "none"
                    );
                }

	                java.nio.LongBuffer pAttachments = attachmentCount == 0
	                    ? null
	                    : stack.mallocLong(attachmentCount);
	                for (int colorIndex = 0; colorIndex < colorAttachmentCount; colorIndex++) {
	                    pAttachments.put(colorIndex, targets.colorViewHandles.get(colorIndex));
	                }
	                if (targets.hasDepthTarget()) {
	                    pAttachments.put(colorAttachmentCount, targets.depthViewHandle);
                }

                VkFramebufferCreateInfo framebufferCreateInfo = VkFramebufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .renderPass(renderPassHandle)
                    .pAttachments(pAttachments)
                    .width(targets.width)
                    .height(targets.height)
                    .layers(1);

                java.nio.LongBuffer pFramebuffer = stack.mallocLong(1);
                checkVk(
                    "vkCreateFramebuffer(framebuffer)",
                    VK10.vkCreateFramebuffer(logicalDevice, framebufferCreateInfo, null, pFramebuffer)
                );
                framebufferHandle = pFramebuffer.get(0);

	                VkClearValue.Buffer clearValues = attachmentCount == 0
	                    ? null
	                    : VkClearValue.calloc(attachmentCount, stack);
	                for (int colorIndex = 0; colorIndex < colorAttachmentCount; colorIndex++) {
	                    OptionalInt clearColor = targets.colorClearColor(colorIndex);
	                    if (clearColor.isPresent()) {
                        int argb = clearColor.getAsInt();
                        clearValues.get(colorIndex).color().float32(0, ((argb >> 16) & 0xFF) / 255.0f);
                        clearValues.get(colorIndex).color().float32(1, ((argb >> 8) & 0xFF) / 255.0f);
                        clearValues.get(colorIndex).color().float32(2, (argb & 0xFF) / 255.0f);
                        clearValues.get(colorIndex).color().float32(3, ((argb >>> 24) & 0xFF) / 255.0f);
                    }
                }
                if (targets.hasDepthTarget() && targets.depthClearValue().isPresent()) {
                    clearValues.get(colorAttachmentCount)
                        .depthStencil()
                        .depth((float) targets.depthClearValue().getAsDouble())
                        .stencil(0);
                }
                VkRenderPassBeginInfo beginInfo = VkRenderPassBeginInfo.calloc(stack)
                    .sType$Default()
                    .renderPass(renderPassHandle)
                    .framebuffer(framebufferHandle)
                    .pClearValues(clearValues);
                beginInfo.renderArea()
                    .offset(it -> it.x(0).y(0))
                    .extent(it -> it.width(targets.width).height(targets.height));

                VK10.vkCmdBeginRenderPass(activeCommandBuffer, beginInfo, VK10.VK_SUBPASS_CONTENTS_INLINE);

                VkViewport.Buffer defaultViewport = VkViewport.calloc(1, stack);
                defaultViewport.get(0)
                    .x(0.0f)
                    .y((float) targets.height)
                    .width((float) targets.width)
                    .height(-(float) targets.height)
                    .minDepth(0.0f)
                    .maxDepth(1.0f);
                VK10.vkCmdSetViewport(activeCommandBuffer, 0, defaultViewport);

                VkRect2D.Buffer defaultScissor = VkRect2D.calloc(1, stack);
                defaultScissor.get(0)
                    .offset(it -> it.x(0).y(0))
                    .extent(it -> it.width(targets.width).height(targets.height));
                VK10.vkCmdSetScissor(activeCommandBuffer, 0, defaultScissor);

                scissorTestEnabled = false;
                hasCachedScissorRect = false;
                cachedScissorX = 0;
                cachedScissorY = 0;
                cachedScissorWidth = targets.width;
                cachedScissorHeight = targets.height;

	                for (int colorIndex = 0; colorIndex < targets.colorTextures.size(); colorIndex++) {
	                    LegacyTextureObject colorTexture = targets.colorTextures.get(colorIndex);
	                    int colorActiveLayout2 = colorTexture.feedbackLoopCapable
	                        ? EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT
	                        : VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
	                    colorActiveLayout2 = imageLayoutForUsage(
	                        targets.colorPassUsage(colorIndex),
	                        false,
	                        colorTexture.feedbackLoopCapable,
	                        colorActiveLayout2
	                    );
	                    trackLayoutForLevel(colorTexture, 0, colorActiveLayout2);
	                }
	                activeRenderPassColorTextures.clear();
	                activeRenderPassColorTextures.addAll(targets.colorTextures);
	                activeRenderPassColorFinalLayouts.clear();
	                for (int colorIndex = 0; colorIndex < targets.colorTextures.size(); colorIndex++) {
	                    LegacyTextureObject colorTexture = targets.colorTextures.get(colorIndex);
	                    int postLayout = colorTexture.feedbackLoopCapable
	                        ? EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT
	                        : VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
	                    activeRenderPassColorFinalLayouts.add(imageLayoutForUsage(
	                        targets.colorFinalUsage(colorIndex),
	                        false,
	                        colorTexture.feedbackLoopCapable,
	                        postLayout
	                    ));
	                }
                activeRenderPassTargetsSwapchain = false;
                renderPassSwapchainImageIndex = -1;
                if (targets.hasDepthTarget()) {
	                    int depthActiveLayout2 = targets.depthTexture.feedbackLoopCapable
	                        ? EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT
	                        : VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
	                    depthActiveLayout2 = imageLayoutForUsage(
	                        targets.depthPassUsage(),
	                        true,
	                        targets.depthTexture.feedbackLoopCapable,
	                        depthActiveLayout2
	                    );
	                    trackLayoutForLevel(targets.depthTexture, 0, depthActiveLayout2);
	                    activeRenderPassDepthTexture = targets.depthTexture;
	                    int depthPostLayout = targets.depthTexture.feedbackLoopCapable
	                        ? EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT
	                        : VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL;
	                    activeRenderPassDepthFinalLayout = imageLayoutForUsage(
	                        targets.depthFinalUsage(),
	                        true,
	                        targets.depthTexture.feedbackLoopCapable,
	                        depthPostLayout
	                    );
	                } else {
	                    activeRenderPassDepthTexture = null;
	                    activeRenderPassDepthFinalLayout = VK10.VK_IMAGE_LAYOUT_UNDEFINED;
	                }

                renderPassRecording = true;
                activeRenderPassCompatibilityKey = compatibilityKey;
                activeRenderPassWidth = targets.width;
                activeRenderPassHeight = targets.height;
                // Permanently-cached render passes must NOT be added to transient handles.
                // Only add to transient if this was a newly-created (non-cached) handle.
                if (!permanentRenderPassCache.containsValue(renderPassHandle)) {
                    trackTransientRenderPassHandle(renderPassHandle);
                }
	                trackTransientFramebufferHandle(framebufferHandle);
                return compatibilityKey;
	            } catch (RuntimeException exception) {
                if (framebufferHandle != VK10.VK_NULL_HANDLE) {
                    VK10.vkDestroyFramebuffer(logicalDevice, framebufferHandle, null);
                }
                // Only destroy the render pass on exception if it was just created (not a cached one).
                if (renderPassHandle != VK10.VK_NULL_HANDLE
                    && !permanentRenderPassCache.containsValue(renderPassHandle)) {
                    VK10.vkDestroyRenderPass(logicalDevice, renderPassHandle, null);
                }
                throw exception;
            }
        }

        private boolean isSwapchainImageViewHandle(long imageViewHandle) {
            return imageViewHandle != VK10.VK_NULL_HANDLE && swapchainImageViewHandles.contains(imageViewHandle);
        }

        private void endRenderPass(long commandBufferHandle) {
            VkCommandBuffer activeCommandBuffer = requireRecordingCommandBuffer(commandBufferHandle, "endRenderPass");
            if (!renderPassRecording) {
                throw new IllegalStateException("No active Vulkan render pass to end");
            }

            VK10.vkCmdEndRenderPass(activeCommandBuffer);
            renderPassRecording = false;
            activeRenderPassCompatibilityKey = null;
            activeRenderPassWidth = 0;
            activeRenderPassHeight = 0;
            activeRenderPassTargetsSwapchain = false;
	            for (int colorIndex = 0; colorIndex < activeRenderPassColorTextures.size(); colorIndex++) {
	                LegacyTextureObject colorTexture = activeRenderPassColorTextures.get(colorIndex);
	                int postLayout = colorIndex >= 0 && colorIndex < activeRenderPassColorFinalLayouts.size()
	                    ? activeRenderPassColorFinalLayouts.get(colorIndex)
	                    : (colorTexture.feedbackLoopCapable
	                        ? EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT
	                        : VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
	                trackLayoutForLevel(colorTexture, 0, postLayout);
	            }
	            activeRenderPassColorTextures.clear();
	            activeRenderPassColorFinalLayouts.clear();
	            if (activeRenderPassDepthTexture != null) {
	                int depthPostLayout = activeRenderPassDepthFinalLayout != VK10.VK_IMAGE_LAYOUT_UNDEFINED
	                    ? activeRenderPassDepthFinalLayout
	                    : (activeRenderPassDepthTexture.feedbackLoopCapable
	                        ? EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT
	                        : VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL);
	                trackLayoutForLevel(activeRenderPassDepthTexture, 0, depthPostLayout);
	                activeRenderPassDepthTexture = null;
	            }
	            activeRenderPassDepthFinalLayout = VK10.VK_IMAGE_LAYOUT_UNDEFINED;
            if (renderPassSwapchainImageIndex >= 0) {
                trackSwapchainImageLayout(renderPassSwapchainImageIndex, KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
                renderPassSwapchainImageIndex = -1;
            }
        }

        private void bindVertexBuffer(long commandBufferHandle, int slot, long bufferHandle) {
            bindVertexBuffer(commandBufferHandle, slot, bufferHandle, 0L);
        }

        private void bindVertexBuffer(long commandBufferHandle, int slot, long bufferHandle, long offset) {
            VkCommandBuffer activeCommandBuffer = requireRecordingCommandBuffer(commandBufferHandle, "bindVertexBuffer");
            if (!renderPassRecording) {
                throw new IllegalStateException("bindVertexBuffer requires an active render pass");
            }
            if (slot < 0) {
                throw new IllegalArgumentException("slot must be >= 0, got: " + slot);
            }
            if (offset < 0L) {
                throw new IllegalArgumentException("vertex buffer offset must be >= 0, got: " + offset);
            }

            try (MemoryStack stack = stackPush()) {
                java.nio.LongBuffer buffers = stack.longs(bufferHandle);
                java.nio.LongBuffer offsets = stack.longs(offset);
                VK10.vkCmdBindVertexBuffers(activeCommandBuffer, slot, buffers, offsets);
            }
        }

        private void bindIndexBuffer(long commandBufferHandle, long bufferHandle, VulkanicIndexType indexType) {
            VkCommandBuffer activeCommandBuffer = requireRecordingCommandBuffer(commandBufferHandle, "bindIndexBuffer");
            if (!renderPassRecording) {
                throw new IllegalStateException("bindIndexBuffer requires an active render pass");
            }
            Objects.requireNonNull(indexType, "indexType must not be null");

            int vkIndexType;
            switch (indexType) {
                case SHORT -> vkIndexType = VK10.VK_INDEX_TYPE_UINT16;
                case INT -> vkIndexType = VK10.VK_INDEX_TYPE_UINT32;
                case BYTE -> throw new UnsupportedOperationException(
                    "Vulkan index type BYTE requires VK_EXT_index_type_uint8 and is not supported yet.");
                default -> throw new IllegalArgumentException("Unsupported VulkanicIndexType: " + indexType);
            }

            VK10.vkCmdBindIndexBuffer(activeCommandBuffer, bufferHandle, 0L, vkIndexType);
        }

        private void drawIndexed(long commandBufferHandle, int firstIndex, int indexCount, int baseVertex, int instanceCount) {
            VkCommandBuffer activeCommandBuffer = requireRecordingCommandBuffer(commandBufferHandle, "drawIndexed");
            if (!renderPassRecording) {
                throw new IllegalStateException("drawIndexed requires an active render pass");
            }
            if (firstIndex < 0 || indexCount < 0 || instanceCount < 1) {
                throw new IllegalArgumentException("Invalid indexed draw arguments");
            }

            VK10.vkCmdDrawIndexed(activeCommandBuffer, indexCount, instanceCount, firstIndex, baseVertex, 0);
        }

        private void draw(long commandBufferHandle, int firstVertex, int vertexCount) {
            VkCommandBuffer activeCommandBuffer = requireRecordingCommandBuffer(commandBufferHandle, "draw");
            if (!renderPassRecording) {
                throw new IllegalStateException("draw requires an active render pass");
            }
            if (firstVertex < 0 || vertexCount < 0) {
                throw new IllegalArgumentException("Invalid draw arguments");
            }

            VK10.vkCmdDraw(activeCommandBuffer, vertexCount, 1, firstVertex, 0);
        }

        private void drawInstanced(long commandBufferHandle, int firstVertex, int vertexCount, int instanceCount) {
            VkCommandBuffer activeCommandBuffer = requireRecordingCommandBuffer(commandBufferHandle, "drawInstanced");
            if (!renderPassRecording) {
                throw new IllegalStateException("drawInstanced requires an active render pass");
            }
            if (firstVertex < 0 || vertexCount < 0 || instanceCount < 1) {
                throw new IllegalArgumentException("Invalid instanced draw arguments");
            }

            VK10.vkCmdDraw(activeCommandBuffer, vertexCount, instanceCount, firstVertex, 0);
        }

        private static int toVkLoadOp(VulkanicRenderPassDescriptor.LoadOp loadOp) {
            Objects.requireNonNull(loadOp, "loadOp must not be null");
            return switch (loadOp) {
                case LOAD -> VK10.VK_ATTACHMENT_LOAD_OP_LOAD;
                case CLEAR -> VK10.VK_ATTACHMENT_LOAD_OP_CLEAR;
                case DONT_CARE -> VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE;
            };
        }

        private static int toVkStoreOp(VulkanicRenderPassDescriptor.StoreOp storeOp) {
            Objects.requireNonNull(storeOp, "storeOp must not be null");
            return switch (storeOp) {
                case STORE -> VK10.VK_ATTACHMENT_STORE_OP_STORE;
                case DONT_CARE -> VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE;
            };
        }

        private int activeImmediateResourceSlot() {
            if (recordingImmediateSubmitSlot >= 0 && recordingImmediateSubmitSlot < IMMEDIATE_SUBMIT_SLOTS) {
                return recordingImmediateSubmitSlot;
            }
            return -1;
        }

        private void trackTransientRenderPassHandle(long renderPassHandle) {
            if (renderPassHandle == VK10.VK_NULL_HANDLE) {
                return;
            }
            int slot = activeImmediateResourceSlot();
            if (slot >= 0) {
                transientRenderPassHandlesByImmediateSlot.get(slot).add(renderPassHandle);
            } else {
                transientRenderPassHandles.add(renderPassHandle);
            }
        }

        private void trackTransientFramebufferHandle(long framebufferHandle) {
            if (framebufferHandle == VK10.VK_NULL_HANDLE) {
                return;
            }
            int slot = activeImmediateResourceSlot();
            if (slot >= 0) {
                transientFramebufferHandlesByImmediateSlot.get(slot).add(framebufferHandle);
            } else {
                transientFramebufferHandles.add(framebufferHandle);
            }
        }

        private void trackTransientStagingBuffer(StagingBuffer stagingBuffer) {
            int slot = activeImmediateResourceSlot();
            if (slot >= 0) {
                transientStagingBuffersByImmediateSlot.get(slot).add(stagingBuffer);
            } else {
                transientStagingBuffers.add(stagingBuffer);
            }
        }

        private void trackTransientDescriptorBuffer(VulkanBuffer transientBuffer) {
            int slot = activeImmediateResourceSlot();
            if (slot >= 0) {
                transientDescriptorBuffersByImmediateSlot.get(slot).add(transientBuffer);
            } else {
                transientDescriptorBuffers.add(transientBuffer);
            }
        }

        private void destroyTransientRenderPassResources() {
            synchronized (transientStagingBuffers) {
                if (!transientStagingBuffers.isEmpty()) {
                    new ArrayList<>(transientStagingBuffers).forEach(this::destroyStagingBuffer);
                    transientStagingBuffers.clear();
                }
            }
            synchronized (transientDescriptorBuffers) {
                if (!transientDescriptorBuffers.isEmpty()) {
                    new ArrayList<>(transientDescriptorBuffers).forEach(this::recycleDescriptorUniformBuffer);
                    transientDescriptorBuffers.clear();
                }
            }
            if (!transientFramebufferHandles.isEmpty()) {
                new ArrayList<>(transientFramebufferHandles).forEach(framebufferHandle -> {
                    transientFramebufferHandles.remove(framebufferHandle);
                    if (logicalDevice != null && framebufferHandle != VK10.VK_NULL_HANDLE) {
                        VK10.vkDestroyFramebuffer(logicalDevice, framebufferHandle, null);
                    }
                });
            }
            if (!transientRenderPassHandles.isEmpty()) {
                new ArrayList<>(transientRenderPassHandles).forEach(renderPassHandle -> {
                    transientRenderPassHandles.remove(renderPassHandle);
                    if (logicalDevice != null && renderPassHandle != VK10.VK_NULL_HANDLE) {
                        VK10.vkDestroyRenderPass(logicalDevice, renderPassHandle, null);
                    }
                });
            }
        }

        private void destroyTransientRenderPassResources(int slot) {
            if (slot < 0 || slot >= IMMEDIATE_SUBMIT_SLOTS) {
                return;
            }

            List<StagingBuffer> stagingBuffers = transientStagingBuffersByImmediateSlot.get(slot);
            synchronized (stagingBuffers) {
                if (!stagingBuffers.isEmpty()) {
                    new ArrayList<>(stagingBuffers).forEach(this::destroyStagingBuffer);
                    stagingBuffers.clear();
                }
            }

            List<VulkanBuffer> descriptorBuffers = transientDescriptorBuffersByImmediateSlot.get(slot);
            synchronized (descriptorBuffers) {
                if (!descriptorBuffers.isEmpty()) {
                    new ArrayList<>(descriptorBuffers).forEach(this::recycleDescriptorUniformBuffer);
                    descriptorBuffers.clear();
                }
            }

            Set<Long> framebufferHandles = transientFramebufferHandlesByImmediateSlot.get(slot);
            if (!framebufferHandles.isEmpty()) {
                new ArrayList<>(framebufferHandles).forEach(framebufferHandle -> {
                    framebufferHandles.remove(framebufferHandle);
                    if (logicalDevice != null && framebufferHandle != VK10.VK_NULL_HANDLE) {
                        VK10.vkDestroyFramebuffer(logicalDevice, framebufferHandle, null);
                    }
                });
            }

            Set<Long> renderPassHandles = transientRenderPassHandlesByImmediateSlot.get(slot);
            if (!renderPassHandles.isEmpty()) {
                new ArrayList<>(renderPassHandles).forEach(renderPassHandle -> {
                    renderPassHandles.remove(renderPassHandle);
                    if (logicalDevice != null && renderPassHandle != VK10.VK_NULL_HANDLE) {
                        VK10.vkDestroyRenderPass(logicalDevice, renderPassHandle, null);
                    }
                });
            }
        }

        private void destroyTransientFrameDescriptorBuffers(int frameIndex) {
            if (frameIndex < 0 || frameIndex >= transientFrameDescriptorBuffers.size()) {
                return;
            }

            List<VulkanBuffer> frameBuffers = transientFrameDescriptorBuffers.get(frameIndex);
            synchronized (frameBuffers) {
                if (!frameBuffers.isEmpty()) {
                    new ArrayList<>(frameBuffers).forEach(this::recycleDescriptorUniformBuffer);
                    frameBuffers.clear();
                }
            }
        }

        private void destroyAllTransientFrameDescriptorBuffers() {
            for (int frameIndex = 0; frameIndex < transientFrameDescriptorBuffers.size(); frameIndex++) {
                destroyTransientFrameDescriptorBuffers(frameIndex);
            }
        }

        private static int descriptorUniformBufferBucketSize(int requestedSize) {
            int size = Math.max(1, requestedSize);
            int bucketSize = 1;
            while (bucketSize < size && bucketSize < (1 << 30)) {
                bucketSize <<= 1;
            }
            return bucketSize;
        }

        private VulkanBuffer acquireDescriptorUniformBuffer(String label, int requestedSize, java.nio.ByteBuffer initialData) {
            int bucketSize = descriptorUniformBufferBucketSize(requestedSize);
            VulkanBuffer buffer = null;
            synchronized (recycledDescriptorUniformBuffers) {
                Deque<VulkanBuffer> bucket = recycledDescriptorUniformBuffers.get(bucketSize);
                while (bucket != null && !bucket.isEmpty()) {
                    VulkanBuffer candidate = bucket.removeFirst();
                    recycledDescriptorUniformBufferCount--;
                    if (!candidate.isClosed()) {
                        buffer = candidate;
                        break;
                    }
                }
            }

            if (buffer == null) {
                buffer = (VulkanBuffer) createManagedBuffer(
                    label,
                    VulkanicBuffer.USAGE_UNIFORM
                        | VulkanicBuffer.USAGE_COPY_DST
                        | VulkanicBuffer.USAGE_MAP_READ
                        | VulkanicBuffer.USAGE_MAP_WRITE,
                    bucketSize,
                    null
                );
            }

            writeDescriptorUniformBuffer(buffer, initialData, requestedSize);
            return buffer;
        }

        private void writeDescriptorUniformBuffer(VulkanBuffer buffer, java.nio.ByteBuffer initialData, int requestedSize) {
            if (initialData == null) {
                return;
            }
            if (requestedSize > buffer.size()) {
                throw new IllegalArgumentException(
                    "Descriptor uniform upload size " + requestedSize + " exceeds pooled buffer size " + buffer.size());
            }
            try (VulkanicBuffer.MappedView mappedView = mapManagedBuffer(buffer, false, true)) {
                java.nio.ByteBuffer destination = mappedView.data().duplicate();
                java.nio.ByteBuffer source = initialData.duplicate();
                source.clear();
                source.limit(requestedSize);
                destination.position(0);
                destination.put(source);
            }
        }

        private void recycleDescriptorUniformBuffer(VulkanBuffer buffer) {
            if (buffer == null || buffer.isClosed()) {
                return;
            }

            int bucketSize = descriptorUniformBufferBucketSize(buffer.size());
            synchronized (recycledDescriptorUniformBuffers) {
                if (recycledDescriptorUniformBufferCount >= MAX_RECYCLED_DESCRIPTOR_UNIFORM_BUFFERS) {
                    buffer.close();
                    return;
                }
                recycledDescriptorUniformBuffers
                    .computeIfAbsent(bucketSize, ignored -> new ArrayDeque<>())
                    .addLast(buffer);
                recycledDescriptorUniformBufferCount++;
            }
        }

        private void destroyRecycledDescriptorUniformBuffers() {
            synchronized (recycledDescriptorUniformBuffers) {
                for (Deque<VulkanBuffer> bucket : recycledDescriptorUniformBuffers.values()) {
                    while (!bucket.isEmpty()) {
                        bucket.removeFirst().close();
                    }
                }
                recycledDescriptorUniformBuffers.clear();
                recycledDescriptorUniformBufferCount = 0;
            }
        }

        private VulkanBuffer materializeDescriptorUniformBuffer(
            String bindingName,
            VulkanicBufferSlice slice,
            VulkanBuffer sourceBuffer
        ) {
            java.nio.ByteBuffer initialData = org.lwjgl.BufferUtils.createByteBuffer(slice.length());
            try (VulkanicBuffer.MappedView mappedView = mapManagedBuffer(sourceBuffer, true, false)) {
                java.nio.ByteBuffer source = mappedView.data().duplicate();
                source.position(slice.offset()).limit(slice.offset() + slice.length());
                initialData.put(source);
                initialData.flip();
            }

            VulkanBuffer transientBuffer = acquireDescriptorUniformBuffer(
                "DescriptorUniform-" + bindingName,
                slice.length(),
                initialData
            );
            if (frameInProgress) {
                transientFrameDescriptorBuffers.get(currentFrameSyncIndex).add(transientBuffer);
            } else {
                trackTransientDescriptorBuffer(transientBuffer);
            }
            return transientBuffer;
        }

        private VulkanicBufferSlice materializeStandaloneUniformBuffer(
            int program,
            java.nio.ByteBuffer backingData,
            int size
        ) {
            java.nio.ByteBuffer initialData = org.lwjgl.BufferUtils.createByteBuffer(size);
            java.nio.ByteBuffer source = backingData.duplicate();
            source.clear();
            source.limit(size);
            initialData.put(source);
            initialData.flip();

            VulkanBuffer transientBuffer = acquireDescriptorUniformBuffer(
                "StandaloneUniform-" + program,
                size,
                initialData
            );
            if (frameInProgress) {
                transientFrameDescriptorBuffers.get(currentFrameSyncIndex).add(transientBuffer);
            } else {
                trackTransientDescriptorBuffer(transientBuffer);
            }
            return new VulkanicBufferSlice(transientBuffer, 0, size);
        }

        // =====================================================================
        // Pipeline creation
        // =====================================================================

        /**
         * Creates a complete Vulkan graphics pipeline from a {@link PipelineDescriptor}
         * and pre-created transient shader module handles.
         *
         * <p>The returned {@link VulkanPipelineHandle} owns the {@code VkPipeline},
         * {@code VkPipelineLayout}, and {@code VkDescriptorSetLayout}.  The caller is
         * responsible for closing the handle to release those resources.</p>
         *
         * <p>The pipeline is compiled against a placeholder render pass whose formats are
         * derived from the swapchain surface.  The actual render pass used at draw time
         * must be compatible (same attachment formats and sample counts).</p>
         */
        private VulkanPipelineHandle createVulkanPipeline(
            PipelineDescriptor descriptor,
            long vertShaderModuleHandle,
            long fragShaderModuleHandle
        ) {
            return createVulkanPipeline(
                descriptor,
                vertShaderModuleHandle,
                fragShaderModuleHandle,
                defaultRenderPassCompatibilityKey()
            );
        }

        private VulkanPipelineHandle createVulkanPipeline(
            PipelineDescriptor descriptor,
            long vertShaderModuleHandle,
            long fragShaderModuleHandle,
            VulkanRenderPassCompatibilityKey renderPassCompatibilityKey
        ) {
            return createVulkanPipeline(
                descriptor,
                vertShaderModuleHandle,
                VK10.VK_NULL_HANDLE,
                fragShaderModuleHandle,
                renderPassCompatibilityKey
            );
        }

        private VulkanPipelineHandle createVulkanPipeline(
            PipelineDescriptor descriptor,
            long vertShaderModuleHandle,
            long fragShaderModuleHandle,
            int colorFormat
        ) {
            return createVulkanPipeline(
                descriptor,
                vertShaderModuleHandle,
                VK10.VK_NULL_HANDLE,
                fragShaderModuleHandle,
                VulkanRenderPassCompatibilityKey.framebuffer(
                    List.of(colorFormat),
                    VK10.VK_FORMAT_UNDEFINED,
                    false
                )
            );
        }

        private VulkanPipelineHandle createVulkanPipeline(
            PipelineDescriptor descriptor,
            long vertShaderModuleHandle,
            long fragShaderModuleHandle,
            int colorFormat,
            boolean swapchainPresentCompatible
        ) {
            return createVulkanPipeline(
                descriptor,
                vertShaderModuleHandle,
                VK10.VK_NULL_HANDLE,
                fragShaderModuleHandle,
                swapchainPresentCompatible
                    ? VulkanRenderPassCompatibilityKey.swapchainPresent(colorFormat)
                    : VulkanRenderPassCompatibilityKey.framebuffer(
                        List.of(colorFormat),
                        VK10.VK_FORMAT_UNDEFINED,
                        false
                    )
            );
        }

        private VulkanPipelineHandle createVulkanPipeline(
            PipelineDescriptor descriptor,
            long vertShaderModuleHandle,
            long geomShaderModuleHandle,
            long fragShaderModuleHandle,
            VulkanRenderPassCompatibilityKey renderPassCompatibilityKey
        ) {
            Objects.requireNonNull(descriptor, "descriptor must not be null");
            Objects.requireNonNull(renderPassCompatibilityKey, "renderPassCompatibilityKey must not be null");
            if (logicalDevice == null) {
                throw new IllegalStateException("Cannot create pipeline: Vulkan logical device is unavailable.");
	            }

            PipelineDescriptor.PortableState portableState = descriptor.getPortableState();
            List<Integer> colorFormats = renderPassCompatibilityKey.colorFormats();

            try (MemoryStack stack = stackPush()) {

                // --- 1. VkDescriptorSetLayout ---
                java.util.List<PipelineDescriptor.ResourceBinding> bindings =
                    normalizeDescriptorLayoutBindings(descriptor.getResourceLayout().bindings());

                if (TRACE_PIPELINE_CREATION) {
                    PipelineDescriptor.VertexInputState vertexInput = descriptor.getVertexInputState();
                    LOGGER.info("createVulkanPipeline location={} hasSpirvModules={} bindingCount={} bindings={} vertexBindings={} vertexAttributes={}",
                        portableState != null ? portableState.location() : "null",
                        descriptor.hasSpirvModules(),
                        bindings.size(),
                        bindings.stream().map(b -> b.binding() + ":" + b.name() + ":" + b.type()).toList(),
                        vertexInput == null
                            ? "default"
                            : vertexInput.bindings().stream()
                                .map(b -> b.binding() + ":stride=" + b.stride() + ":rate=" + b.inputRate())
                                .toList(),
                        vertexInput == null
                            ? "default"
                            : vertexInput.attributes().stream()
                                .map(a -> a.location() + ":binding=" + a.binding() + ":format=" + a.format() + ":offset=" + a.offset())
                                .toList());
                }

                long descriptorSetLayoutHandle;
                if (bindings.isEmpty()) {
                    VkDescriptorSetLayoutCreateInfo emptyDslInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                        .sType$Default()
                        .pBindings(null);
                    java.nio.LongBuffer pDsl0 = stack.mallocLong(1);
                    checkVk("vkCreateDescriptorSetLayout (empty)",
                        VK10.vkCreateDescriptorSetLayout(logicalDevice, emptyDslInfo, null, pDsl0));
                    descriptorSetLayoutHandle = pDsl0.get(0);
                } else {
                    VkDescriptorSetLayoutBinding.Buffer dslBindings =
                        VkDescriptorSetLayoutBinding.calloc(bindings.size(), stack);
                    for (int i = 0; i < bindings.size(); i++) {
                        PipelineDescriptor.ResourceBinding b = bindings.get(i);
                        PipelineDescriptor.ResourceBinding effectiveBinding = withEffectiveBindingType(b, null);
                        dslBindings.get(i)
                            .binding(b.binding())
                            .descriptorType(toVkDescriptorType(effectiveBinding.type()))
                            .descriptorCount(1)
                            .stageFlags(toVkShaderStageFlags(b.stages()));
                    }
                    VkDescriptorSetLayoutCreateInfo dslInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                        .sType$Default()
                        .pBindings(dslBindings);
                    java.nio.LongBuffer pDsl = stack.mallocLong(1);
                    checkVk("vkCreateDescriptorSetLayout",
                        VK10.vkCreateDescriptorSetLayout(logicalDevice, dslInfo, null, pDsl));
                    descriptorSetLayoutHandle = pDsl.get(0);
                }
                managedVkDescriptorSetLayoutHandles.add(descriptorSetLayoutHandle);

                // --- 2. VkPipelineLayout ---
                java.nio.LongBuffer dslBuf = stack.longs(descriptorSetLayoutHandle);

                java.util.List<PipelineDescriptor.PushConstantRange> pushRanges =
                    descriptor.getPushConstantRanges();
                VkPushConstantRange.Buffer vkPushRanges = null;
                if (!pushRanges.isEmpty()) {
                    vkPushRanges = VkPushConstantRange.calloc(pushRanges.size(), stack);
                    for (int i = 0; i < pushRanges.size(); i++) {
                        PipelineDescriptor.PushConstantRange r = pushRanges.get(i);
                        vkPushRanges.get(i)
                            .stageFlags(toVkShaderStageFlags(r.stages()))
                            .offset(r.offset())
                            .size(r.size());
                    }
                }

                VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType$Default()
                    .pSetLayouts(dslBuf)
                    .pPushConstantRanges(vkPushRanges);
                java.nio.LongBuffer pPipelineLayout = stack.mallocLong(1);
                checkVk("vkCreatePipelineLayout",
                    VK10.vkCreatePipelineLayout(logicalDevice, pipelineLayoutInfo, null, pPipelineLayout));
                long pipelineLayoutHandle = pPipelineLayout.get(0);
                managedVkPipelineLayoutHandles.add(pipelineLayoutHandle);

                // --- 3. Shader stages ---
                java.nio.ByteBuffer mainEntry = stack.UTF8("main");
                int shaderStageCount = geomShaderModuleHandle != VK10.VK_NULL_HANDLE ? 3 : 2;
                VkPipelineShaderStageCreateInfo.Buffer shaderStages =
                    VkPipelineShaderStageCreateInfo.calloc(shaderStageCount, stack);
                shaderStages.get(0)
                    .sType$Default()
                    .stage(VK10.VK_SHADER_STAGE_VERTEX_BIT)
                    .module(vertShaderModuleHandle)
                    .pName(mainEntry);

                int fragmentStageIndex = 1;
                if (geomShaderModuleHandle != VK10.VK_NULL_HANDLE) {
                    shaderStages.get(1)
                        .sType$Default()
                        .stage(VK10.VK_SHADER_STAGE_GEOMETRY_BIT)
                        .module(geomShaderModuleHandle)
                        .pName(mainEntry);
                    fragmentStageIndex = 2;
                }

                shaderStages.get(fragmentStageIndex)
                    .sType$Default()
                    .stage(VK10.VK_SHADER_STAGE_FRAGMENT_BIT)
                    .module(fragShaderModuleHandle)
                    .pName(mainEntry);

                // --- 4. Vertex input state ---
                PipelineDescriptor.VertexInputState explicitVertexInput = descriptor.getVertexInputState();
                VkVertexInputBindingDescription.Buffer vBindings;
                VkVertexInputAttributeDescription.Buffer vAttribs;
                if (explicitVertexInput != null) {
                    vBindings = VkVertexInputBindingDescription.calloc(explicitVertexInput.bindings().size(), stack);
                    for (int i = 0; i < explicitVertexInput.bindings().size(); i++) {
                        PipelineDescriptor.VertexInputBinding binding = explicitVertexInput.bindings().get(i);
                        vBindings.get(i)
                            .binding(binding.binding())
                            .stride(binding.stride())
                            .inputRate(binding.inputRate() == PipelineDescriptor.VertexInputRate.INSTANCE
                                ? VK10.VK_VERTEX_INPUT_RATE_INSTANCE
                                : VK10.VK_VERTEX_INPUT_RATE_VERTEX);
                    }

                    vAttribs = VkVertexInputAttributeDescription.calloc(explicitVertexInput.attributes().size(), stack);
                    for (int i = 0; i < explicitVertexInput.attributes().size(); i++) {
                        PipelineDescriptor.VertexInputAttribute attribute = explicitVertexInput.attributes().get(i);
                        vAttribs.get(i)
                            .location(attribute.location())
                            .binding(attribute.binding())
                            .format(toVkVertexAttributeFormat(attribute.format()))
                            .offset(attribute.offset());
                    }
                } else {
                    VertexFormat vertexFormat = portableState.vertexFormat();
                    java.util.List<VertexFormatElement> vfElements = vertexFormat.getElements();

                    vBindings = VkVertexInputBindingDescription.calloc(1, stack);
                    vBindings.get(0)
                        .binding(0)
                        .stride(vertexFormat.getVertexSize())
                        .inputRate(VK10.VK_VERTEX_INPUT_RATE_VERTEX);

                    vAttribs = VkVertexInputAttributeDescription.calloc(vfElements.size(), stack);
                    for (int i = 0; i < vfElements.size(); i++) {
                        VertexFormatElement elem = vfElements.get(i);
                        vAttribs.get(i)
							.location(vertexFormat.getShaderAttributeLocation(i))
                            .binding(0)
                            .format(toVkVertexElementFormat(elem))
                            .offset(vertexFormat.getOffset(elem));
                    }
                }

                VkPipelineVertexInputStateCreateInfo vertexInputInfo =
                    VkPipelineVertexInputStateCreateInfo.calloc(stack)
                        .sType$Default()
                        .pVertexBindingDescriptions(vBindings)
                        .pVertexAttributeDescriptions(vAttribs);

                // --- 5. Input assembly ---
                VkPipelineInputAssemblyStateCreateInfo inputAssembly =
                    VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                        .sType$Default()
                        .topology(toVkPrimitiveTopology(portableState.vertexFormatMode()))
                        .primitiveRestartEnable(false);

                // --- 6. Viewport state (dynamic) ---
                VkPipelineViewportStateCreateInfo viewportState =
                    VkPipelineViewportStateCreateInfo.calloc(stack)
                        .sType$Default()
                        .viewportCount(1)
                        .scissorCount(1);

                VulkanPipelineState pipelineState = VulkanPipelineState.from(
                    portableState,
                    colorFormats.size(),
                    mode -> toVkPolygonMode(mode, portableState.location().toString()),
                    backend::blendStateForAttachment,
                    backend.currentStencilState(),
                    renderPassCompatibilityKey.hasStencilAttachment()
                );

                // --- 7. Rasterization ---
                VkPipelineRasterizationStateCreateInfo rasterizer =
                    VkPipelineRasterizationStateCreateInfo.calloc(stack)
                        .sType$Default()
                        .depthClampEnable(false)
                        .rasterizerDiscardEnable(false)
                        .polygonMode(pipelineState.polygonMode())
                        .lineWidth(1.0f)
                        .cullMode(pipelineState.cullMode())
                        .frontFace(pipelineState.frontFace())
                        .depthBiasEnable(pipelineState.depthBiasEnabled())
                        .depthBiasConstantFactor(pipelineState.depthBiasConstantFactor())
                        .depthBiasSlopeFactor(pipelineState.depthBiasSlopeFactor())
                        .depthBiasClamp(0.0f);

                // --- 8. Multisample ---
                VkPipelineMultisampleStateCreateInfo multisampling =
                    VkPipelineMultisampleStateCreateInfo.calloc(stack)
                        .sType$Default()
                        .sampleShadingEnable(false)
                        .rasterizationSamples(VK10.VK_SAMPLE_COUNT_1_BIT);

                // --- 9. Depth/stencil ---
                VkPipelineDepthStencilStateCreateInfo depthStencil =
                    VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                        .sType$Default()
                        .depthTestEnable(pipelineState.depthTestEnabled())
                        .depthWriteEnable(pipelineState.depthWriteEnabled())
                        .depthCompareOp(pipelineState.depthCompareOp())
                        .depthBoundsTestEnable(false)
                        .stencilTestEnable(pipelineState.stencilTestEnabled());
                applyStencilFaceState(depthStencil.front(), pipelineState.frontStencil());
                applyStencilFaceState(depthStencil.back(), pipelineState.backStencil());

                // --- 10. Color blend ---
                VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachment = colorFormats.isEmpty()
                    ? null
                    : VkPipelineColorBlendAttachmentState.calloc(colorFormats.size(), stack);
                for (int colorIndex = 0; colorIndex < colorFormats.size(); colorIndex++) {
                    VulkanPipelineState.ColorBlendAttachment blendAttachment =
                        pipelineState.colorBlendAttachments().get(colorIndex);
                    colorBlendAttachment.get(colorIndex)
                        .colorWriteMask(blendAttachment.colorWriteMask())
                        .blendEnable(blendAttachment.blendEnabled())
                        .srcColorBlendFactor(blendAttachment.sourceColorBlendFactor())
                        .dstColorBlendFactor(blendAttachment.destColorBlendFactor())
                        .colorBlendOp(blendAttachment.colorBlendOp())
                        .srcAlphaBlendFactor(blendAttachment.sourceAlphaBlendFactor())
                        .dstAlphaBlendFactor(blendAttachment.destAlphaBlendFactor())
                        .alphaBlendOp(blendAttachment.alphaBlendOp());
                }

                VkPipelineColorBlendStateCreateInfo colorBlending =
                    VkPipelineColorBlendStateCreateInfo.calloc(stack)
                        .sType$Default()
                        .logicOpEnable(pipelineState.logicOpEnabled())
                        .logicOp(pipelineState.logicOp())
                        .pAttachments(colorBlendAttachment)
                        .blendConstants(stack.floats(0f, 0f, 0f, 0f));

                // --- 11. Dynamic state ---
                java.nio.IntBuffer dynamicStates = stack.ints(
                    VK10.VK_DYNAMIC_STATE_VIEWPORT,
                    VK10.VK_DYNAMIC_STATE_SCISSOR);
                VkPipelineDynamicStateCreateInfo dynamicState =
                    VkPipelineDynamicStateCreateInfo.calloc(stack)
                        .sType$Default()
                        .pDynamicStates(dynamicStates);

                // --- 12. Compatible placeholder render pass ---
                // The pipeline render pass only needs to be *compatible* (same formats/samples)
                // with the render pass used at draw time.  We build one against the swapchain
                // color format + D32_SFLOAT depth format so it matches the transient render
                // passes created by beginRenderPass() for standard Minecraft draw calls.
                long placeholderRenderPass = createPipelineCompatibleRenderPass(
                    stack,
                    renderPassCompatibilityKey
                );

                // --- 13. VkGraphicsPipelineCreateInfo ---
                VkGraphicsPipelineCreateInfo.Buffer pipelineInfo =
                    VkGraphicsPipelineCreateInfo.calloc(1, stack);
                int pipelineCreateFlags = 0;
                if (attachmentFeedbackLoopLayoutEnabled) {
                    pipelineCreateFlags |= EXTAttachmentFeedbackLoopLayout.VK_PIPELINE_CREATE_COLOR_ATTACHMENT_FEEDBACK_LOOP_BIT_EXT;
                    pipelineCreateFlags |= EXTAttachmentFeedbackLoopLayout.VK_PIPELINE_CREATE_DEPTH_STENCIL_ATTACHMENT_FEEDBACK_LOOP_BIT_EXT;
                }
                pipelineInfo.get(0)
                    .sType$Default()
                    .flags(pipelineCreateFlags)
                    .pStages(shaderStages)
                    .pVertexInputState(vertexInputInfo)
                    .pInputAssemblyState(inputAssembly)
                    .pViewportState(viewportState)
                    .pRasterizationState(rasterizer)
                    .pMultisampleState(multisampling)
                    .pDepthStencilState(depthStencil)
                    .pColorBlendState(colorBlending)
                    .pDynamicState(dynamicState)
                    .layout(pipelineLayoutHandle)
                    .renderPass(placeholderRenderPass)
                    .subpass(0)
                    .basePipelineHandle(VK10.VK_NULL_HANDLE)
                    .basePipelineIndex(-1);

                java.nio.LongBuffer pPipeline = stack.mallocLong(1);
                checkVk("vkCreateGraphicsPipelines",
                    VK10.vkCreateGraphicsPipelines(
                        logicalDevice, VK10.VK_NULL_HANDLE, pipelineInfo, null, pPipeline));
                long pipelineHandle = pPipeline.get(0);
                managedVkPipelineHandles.add(pipelineHandle);

                // Destroy the transient placeholder render pass immediately; it was only needed
                // during pipeline compilation and is not required at draw time.
                VK10.vkDestroyRenderPass(logicalDevice, placeholderRenderPass, null);

                return new VulkanPipelineHandle(
                    pipelineHandle, pipelineLayoutHandle, descriptorSetLayoutHandle,
                    renderPassCompatibilityKey,
                    bindings.size(),
                    this);
            }
        }

        /**
         * Destroys a {@code VkPipeline}, its {@code VkPipelineLayout}, and its
         * {@code VkDescriptorSetLayout} when a {@link VulkanPipelineHandle} is closed.
         */
        private void destroyVulkanPipeline(
            long pipelineHandle,
            long pipelineLayoutHandle,
            long descriptorSetLayoutHandle
        ) {
            if (logicalDevice == null) return;
            if (pipelineHandle != VK10.VK_NULL_HANDLE) {
                managedVkPipelineHandles.remove(pipelineHandle);
                VK10.vkDestroyPipeline(logicalDevice, pipelineHandle, null);
            }
            if (pipelineLayoutHandle != VK10.VK_NULL_HANDLE) {
                managedVkPipelineLayoutHandles.remove(pipelineLayoutHandle);
                VK10.vkDestroyPipelineLayout(logicalDevice, pipelineLayoutHandle, null);
            }
            if (descriptorSetLayoutHandle != VK10.VK_NULL_HANDLE) {
                managedVkDescriptorSetLayoutHandles.remove(descriptorSetLayoutHandle);
                VK10.vkDestroyDescriptorSetLayout(logicalDevice, descriptorSetLayoutHandle, null);
            }
        }

        /**
         * Records a {@code vkCmdBindPipeline} command for {@code VK_PIPELINE_BIND_POINT_GRAPHICS}.
         */
        private void bindPipeline(long commandBufferHandle, long pipelineHandle) {
            VkCommandBuffer activeCommandBuffer = requireRecordingCommandBuffer(commandBufferHandle, "bindPipeline");
            Long currentlyBoundPipeline = lastBoundGraphicsPipelineByCommandBuffer.get(commandBufferHandle);
            if (currentlyBoundPipeline != null && currentlyBoundPipeline == pipelineHandle) {
                skippedRedundantPipelineBindCount++;
                return;
            }
            VK10.vkCmdBindPipeline(
                activeCommandBuffer,
                VK10.VK_PIPELINE_BIND_POINT_GRAPHICS,
                pipelineHandle);
            lastBoundGraphicsPipelineByCommandBuffer.put(commandBufferHandle, pipelineHandle);
            lastBoundDescriptorSetByCommandBuffer.remove(commandBufferHandle);
        }

        private static void applyStencilFaceState(
            VkStencilOpState target,
            VulkanPipelineState.StencilFaceState source
        ) {
            target.failOp(source.failOp())
                .passOp(source.passOp())
                .depthFailOp(source.depthFailOp())
                .compareOp(source.compareOp())
                .compareMask(source.compareMask())
                .writeMask(source.writeMask())
                .reference(source.reference());
        }

        private static boolean hasStencilAspect(int vkFormat) {
            return vkFormat == VK10.VK_FORMAT_D16_UNORM_S8_UINT
                || vkFormat == VK10.VK_FORMAT_D24_UNORM_S8_UINT
                || vkFormat == VK10.VK_FORMAT_D32_SFLOAT_S8_UINT;
        }

        private static VkSubpassDependency.Buffer allocateCompatibleSubpassDependencies(
            MemoryStack stack,
            VulkanRenderPassCompatibilityKey compatibilityKey
        ) {
            return switch (compatibilityKey.dependencyProfile()) {
                case SWAPCHAIN_PRESENT -> allocateSwapchainPresentDependencies(stack);
                case TEXTURE_VIEW -> allocateTextureViewDependencies(stack, compatibilityKey);
                case FRAMEBUFFER -> allocateFramebufferDependencies(stack, compatibilityKey);
            };
        }

        private static VkSubpassDependency.Buffer allocateSwapchainPresentDependencies(MemoryStack stack) {
            VkSubpassDependency.Buffer dependencies = VkSubpassDependency.calloc(2, stack);
            dependencies.get(0)
                .srcSubpass(VK10.VK_SUBPASS_EXTERNAL)
                .dstSubpass(0)
                .srcStageMask(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                .dstStageMask(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                .srcAccessMask(0)
                .dstAccessMask(VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);
            dependencies.get(1)
                .srcSubpass(0)
                .dstSubpass(VK10.VK_SUBPASS_EXTERNAL)
                .srcStageMask(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                .dstStageMask(VK10.VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT)
                .srcAccessMask(VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                .dstAccessMask(0);
            return dependencies;
        }

        private static VkSubpassDependency.Buffer allocateTextureViewDependencies(
            MemoryStack stack,
            VulkanRenderPassCompatibilityKey compatibilityKey
        ) {
            VkSubpassDependency.Buffer dependencies = VkSubpassDependency.calloc(
                compatibilityKey.feedbackLoop() ? 3 : 2,
                stack
            );
            int entryStageMask = VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
                | VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
            int entryAccessMask = VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                | VK10.VK_ACCESS_SHADER_READ_BIT;
            if (compatibilityKey.hasDepthAttachment()) {
                entryStageMask |= VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT
                    | VK10.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT;
                entryAccessMask |= VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT
                    | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
            }
            dependencies.get(0)
                .srcSubpass(VK10.VK_SUBPASS_EXTERNAL)
                .dstSubpass(0)
                .srcStageMask(entryStageMask)
                .dstStageMask(entryStageMask)
                .srcAccessMask(entryAccessMask)
                .dstAccessMask(entryAccessMask);

            int exitSrcStageMask = VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
            int exitSrcAccessMask = VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
            if (compatibilityKey.hasDepthAttachment()) {
                exitSrcStageMask |= VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT
                    | VK10.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT;
                exitSrcAccessMask |= VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT
                    | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
            }
            dependencies.get(1)
                .srcSubpass(0)
                .dstSubpass(VK10.VK_SUBPASS_EXTERNAL)
                .srcStageMask(exitSrcStageMask)
                .dstStageMask(VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT)
                .srcAccessMask(exitSrcAccessMask)
                .dstAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT);

            if (compatibilityKey.feedbackLoop()) {
                dependencies.get(2)
                    .srcSubpass(0)
                    .dstSubpass(0)
                    .srcStageMask(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .dstStageMask(VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT)
                    .srcAccessMask(VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                    .dstAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT)
                    .dependencyFlags(EXTAttachmentFeedbackLoopLayout.VK_DEPENDENCY_FEEDBACK_LOOP_BIT_EXT
                        | VK10.VK_DEPENDENCY_BY_REGION_BIT);
            }
            return dependencies;
        }

        private static VkSubpassDependency.Buffer allocateFramebufferDependencies(
            MemoryStack stack,
            VulkanRenderPassCompatibilityKey compatibilityKey
        ) {
            VkSubpassDependency.Buffer dependencies = VkSubpassDependency.calloc(
                compatibilityKey.feedbackLoop() ? 3 : 2,
                stack
            );
            int entryStageMask = VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
            int entryAccessMask = VK10.VK_ACCESS_SHADER_READ_BIT;
            if (compatibilityKey.colorAttachmentCount() > 0) {
                entryStageMask |= VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
                entryAccessMask |= VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
            }
            if (compatibilityKey.hasDepthAttachment()) {
                entryStageMask |= VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT
                    | VK10.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT;
                entryAccessMask |= VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
            }
            dependencies.get(0)
                .srcSubpass(VK10.VK_SUBPASS_EXTERNAL)
                .dstSubpass(0)
                .srcStageMask(entryStageMask)
                .dstStageMask(entryStageMask)
                .srcAccessMask(entryAccessMask)
                .dstAccessMask(entryAccessMask);

            int exitSrcStageMask = 0;
            int exitSrcAccessMask = 0;
            if (compatibilityKey.colorAttachmentCount() > 0) {
                exitSrcStageMask |= VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
                exitSrcAccessMask |= VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
            }
            if (compatibilityKey.hasDepthAttachment()) {
                exitSrcStageMask |= VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT
                    | VK10.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT;
                exitSrcAccessMask |= VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
            }
            dependencies.get(1)
                .srcSubpass(0)
                .dstSubpass(VK10.VK_SUBPASS_EXTERNAL)
                .srcStageMask(exitSrcStageMask)
                .dstStageMask(VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT)
                .srcAccessMask(exitSrcAccessMask)
                .dstAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT);

            if (compatibilityKey.feedbackLoop()) {
                int feedbackSrcStageMask = 0;
                int feedbackSrcAccessMask = 0;
                if (compatibilityKey.colorAttachmentCount() > 0) {
                    feedbackSrcStageMask |= VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
                    feedbackSrcAccessMask |= VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
                }
                if (compatibilityKey.hasDepthAttachment()) {
                    feedbackSrcStageMask |= VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT
                        | VK10.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT;
                    feedbackSrcAccessMask |= VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
                }
                dependencies.get(2)
                    .srcSubpass(0)
                    .dstSubpass(0)
                    .srcStageMask(feedbackSrcStageMask)
                    .dstStageMask(VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT)
                    .srcAccessMask(feedbackSrcAccessMask)
                    .dstAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT)
                    .dependencyFlags(EXTAttachmentFeedbackLoopLayout.VK_DEPENDENCY_FEEDBACK_LOOP_BIT_EXT
                        | VK10.VK_DEPENDENCY_BY_REGION_BIT);
            }
            return dependencies;
        }

        /**
         * Creates a transient render-pass object suitable for pipeline compilation.
         *
         * <p>This render pass is immediately destroyed after pipeline creation; it only
         * needs to be <em>compatible</em> with the transient render passes created by
         * {@link #beginRenderPass} at draw time (same formats, same sample count).</p>
         *
         * @param includeDepth whether to include a D32_SFLOAT depth/stencil attachment
         */
        private long createPipelineCompatibleRenderPass(MemoryStack stack, boolean includeDepth) {
            return createPipelineCompatibleRenderPass(
                stack,
                VulkanRenderPassCompatibilityKey.framebuffer(
                    List.of(VK10.VK_FORMAT_R8G8B8A8_UNORM),
                    includeDepth ? VK10.VK_FORMAT_D32_SFLOAT : VK10.VK_FORMAT_UNDEFINED,
                    false
                )
            );
        }

        private long createPipelineCompatibleRenderPass(MemoryStack stack, boolean includeDepth, int colorFormat) {
            return createPipelineCompatibleRenderPass(
                stack,
                VulkanRenderPassCompatibilityKey.framebuffer(
                    List.of(colorFormat),
                    includeDepth ? VK10.VK_FORMAT_D32_SFLOAT : VK10.VK_FORMAT_UNDEFINED,
                    false
                )
            );
        }

        private long createPipelineCompatibleRenderPass(
            MemoryStack stack,
            VulkanRenderPassCompatibilityKey compatibilityKey
        ) {
            Objects.requireNonNull(compatibilityKey, "compatibilityKey must not be null");
            List<Integer> colorFormats = compatibilityKey.colorFormats();
            boolean includeDepth = compatibilityKey.hasDepthAttachment();

            int colorAttachmentImageLayout = compatibilityKey.feedbackLoop()
                ? EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT
                : VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
            int depthAttachmentImageLayout = compatibilityKey.feedbackLoop()
                ? EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT
                : VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
            int colorAttachmentRefLayout = compatibilityKey.feedbackLoop()
                ? EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT
                : VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
            int depthAttachmentRefLayout = compatibilityKey.feedbackLoop()
                ? EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT
                : VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;

            int colorAttachmentCount = colorFormats.size();
            int attachmentCount = colorAttachmentCount + (includeDepth ? 1 : 0);

	            VkAttachmentDescription.Buffer attachments = attachmentCount == 0
	                ? null
	                : VkAttachmentDescription.calloc(attachmentCount, stack);
            for (int colorIndex = 0; colorIndex < colorAttachmentCount; colorIndex++) {
                attachments.get(colorIndex)
                    .format(colorFormats.get(colorIndex))
                    .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .storeOp(VK10.VK_ATTACHMENT_STORE_OP_STORE)
                    .stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(colorAttachmentImageLayout)
                    .finalLayout(colorAttachmentImageLayout);
            }

	            VkAttachmentReference.Buffer colorRef = colorAttachmentCount == 0
	                ? null
	                : VkAttachmentReference.calloc(colorAttachmentCount, stack);
	            for (int colorIndex = 0; colorIndex < colorAttachmentCount; colorIndex++) {
	                colorRef.get(colorIndex)
	                    .attachment(colorIndex)
	                    .layout(colorAttachmentRefLayout);
	            }

            VkAttachmentReference depthRef = null;
            if (includeDepth) {
                attachments.get(colorAttachmentCount)
                    .format(compatibilityKey.depthFormat())
                    .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .storeOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(depthAttachmentImageLayout)
                    .finalLayout(depthAttachmentImageLayout);
                depthRef = VkAttachmentReference.calloc(stack)
                    .attachment(colorAttachmentCount)
                    .layout(depthAttachmentRefLayout);
            }

            VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack);
            subpass.get(0)
                .pipelineBindPoint(VK10.VK_PIPELINE_BIND_POINT_GRAPHICS)
	                .colorAttachmentCount(colorAttachmentCount)
	                .pColorAttachments(colorRef)
	                .pDepthStencilAttachment(depthRef);

            VkSubpassDependency.Buffer dependencies = allocateCompatibleSubpassDependencies(stack, compatibilityKey);

            VkRenderPassCreateInfo rpInfo = VkRenderPassCreateInfo.calloc(stack)
                .sType$Default()
                .pAttachments(attachments)
                .pSubpasses(subpass)
                .pDependencies(dependencies);

            java.nio.LongBuffer pRp = stack.mallocLong(1);
            checkVk("vkCreateRenderPass (pipeline-compatible)",
                VK10.vkCreateRenderPass(logicalDevice, rpInfo, null, pRp));
            return pRp.get(0);
        }

        // =====================================================================
        // Pipeline state mapping helpers
        // =====================================================================

        private static int toVkTexelBufferFormat(int internalFormat) {
            return switch (internalFormat) {
                case VulkanicAPI.GL_R8I -> VK10.VK_FORMAT_R8_SINT;
                case VulkanicAPI.GL_R8UI -> VK10.VK_FORMAT_R8_UINT;
                case VulkanicAPI.GL_R16I -> VK10.VK_FORMAT_R16_SINT;
                case VulkanicAPI.GL_R16UI -> VK10.VK_FORMAT_R16_UINT;
                case VulkanicAPI.GL_R32I -> VK10.VK_FORMAT_R32_SINT;
                case VulkanicAPI.GL_R32UI -> VK10.VK_FORMAT_R32_UINT;
                case VulkanicAPI.GL_R32F -> VK10.VK_FORMAT_R32_SFLOAT;
                case VulkanicAPI.GL_RG32I -> VK10.VK_FORMAT_R32G32_SINT;
                case VulkanicAPI.GL_RG32UI -> VK10.VK_FORMAT_R32G32_UINT;
                case VulkanicAPI.GL_RG32F -> VK10.VK_FORMAT_R32G32_SFLOAT;
                default -> throw new IllegalArgumentException(
                    "Unsupported texel-buffer internal format for Vulkan: " + internalFormat);
            };
        }

        private static int toVkDescriptorType(PipelineDescriptor.ResourceType type) {
            return switch (type) {
                case SAMPLER, COMPARISON_SAMPLER -> VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
                case UNIFORM_BUFFER -> VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
                case TEXEL_BUFFER -> VK10.VK_DESCRIPTOR_TYPE_UNIFORM_TEXEL_BUFFER;
            };
        }

        private static int toVkShaderStageFlags(java.util.Set<VulkanicShaderStage> stages) {
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

        /**
         * Maps {@link VertexFormatElement.Type} + component count to a {@code VkFormat}.
         *
         * <p>Vanilla feeds overlay and light UV channels as integral short pairs and most shaders
         * consume them as {@code ivec2}. Those must use integer vertex formats on Vulkan or the
         * shader sees the wrong values for texelFetch-based overlay/lightmap sampling.</p>
         */
        private static int toVkVertexElementFormat(VertexFormatElement element) {
            VertexFormatElement.Type type = element.type();
            int count = element.count();
            boolean useIntegerFormat = (element.usage() == VertexFormatElement.Usage.GENERIC || element.usage() == VertexFormatElement.Usage.UV)
                && !isSodiumIrisFloatAttribute(element);

            return switch (type) {
                case FLOAT -> switch (count) {
                    case 1 -> VK10.VK_FORMAT_R32_SFLOAT;
                    case 2 -> VK10.VK_FORMAT_R32G32_SFLOAT;
                    case 3 -> VK10.VK_FORMAT_R32G32B32_SFLOAT;
                    case 4 -> VK10.VK_FORMAT_R32G32B32A32_SFLOAT;
                    default -> throw new IllegalArgumentException(
                        "Unsupported FLOAT vertex component count: " + count);
                };
                case UBYTE -> switch (count) {
                    case 1 -> useIntegerFormat ? VK10.VK_FORMAT_R8_UINT : VK10.VK_FORMAT_R8_UNORM;
                    case 2 -> useIntegerFormat ? VK10.VK_FORMAT_R8G8_UINT : VK10.VK_FORMAT_R8G8_UNORM;
                    case 3 -> useIntegerFormat ? VK10.VK_FORMAT_R8G8B8_UINT : VK10.VK_FORMAT_R8G8B8_UNORM;
                    case 4 -> useIntegerFormat ? VK10.VK_FORMAT_R8G8B8A8_UINT : VK10.VK_FORMAT_R8G8B8A8_UNORM;
                    default -> throw new IllegalArgumentException(
                        "Unsupported UBYTE vertex component count: " + count);
                };
                case BYTE -> switch (count) {
                    case 1 -> useIntegerFormat ? VK10.VK_FORMAT_R8_SINT : VK10.VK_FORMAT_R8_SNORM;
                    case 2 -> useIntegerFormat ? VK10.VK_FORMAT_R8G8_SINT : VK10.VK_FORMAT_R8G8_SNORM;
                    case 3 -> useIntegerFormat ? VK10.VK_FORMAT_R8G8B8_SINT : VK10.VK_FORMAT_R8G8B8_SNORM;
                    case 4 -> useIntegerFormat ? VK10.VK_FORMAT_R8G8B8A8_SINT : VK10.VK_FORMAT_R8G8B8A8_SNORM;
                    default -> throw new IllegalArgumentException(
                        "Unsupported BYTE vertex component count: " + count);
                };
                case SHORT -> switch (count) {
                    case 1 -> useIntegerFormat ? VK10.VK_FORMAT_R16_SINT : VK10.VK_FORMAT_R16_SSCALED;
                    case 2 -> useIntegerFormat ? VK10.VK_FORMAT_R16G16_SINT : VK10.VK_FORMAT_R16G16_SSCALED;
                    case 3 -> useIntegerFormat ? VK10.VK_FORMAT_R16G16B16_SINT : VK10.VK_FORMAT_R16G16B16_SSCALED;
                    case 4 -> useIntegerFormat ? VK10.VK_FORMAT_R16G16B16A16_SINT : VK10.VK_FORMAT_R16G16B16A16_SSCALED;
                    default -> throw new IllegalArgumentException(
                        "Unsupported SHORT vertex component count: " + count);
                };
                case USHORT -> switch (count) {
                    case 1 -> useIntegerFormat ? VK10.VK_FORMAT_R16_UINT : VK10.VK_FORMAT_R16_USCALED;
                    case 2 -> useIntegerFormat ? VK10.VK_FORMAT_R16G16_UINT : VK10.VK_FORMAT_R16G16_USCALED;
                    case 3 -> useIntegerFormat ? VK10.VK_FORMAT_R16G16B16_UINT : VK10.VK_FORMAT_R16G16B16_USCALED;
                    case 4 -> useIntegerFormat ? VK10.VK_FORMAT_R16G16B16A16_UINT : VK10.VK_FORMAT_R16G16B16A16_USCALED;
                    default -> throw new IllegalArgumentException(
                        "Unsupported USHORT vertex component count: " + count);
                };
                case INT -> switch (count) {
                    case 1 -> VK10.VK_FORMAT_R32_SINT;
                    case 2 -> VK10.VK_FORMAT_R32G32_SINT;
                    case 3 -> VK10.VK_FORMAT_R32G32B32_SINT;
                    case 4 -> VK10.VK_FORMAT_R32G32B32A32_SINT;
                    default -> throw new IllegalArgumentException(
                        "Unsupported INT vertex component count: " + count);
                };
                case UINT -> switch (count) {
                    case 1 -> VK10.VK_FORMAT_R32_UINT;
                    case 2 -> VK10.VK_FORMAT_R32G32_UINT;
                    case 3 -> VK10.VK_FORMAT_R32G32B32_UINT;
                    case 4 -> VK10.VK_FORMAT_R32G32B32A32_UINT;
                    default -> throw new IllegalArgumentException(
                        "Unsupported UINT vertex component count: " + count);
                };
            };
        }

        private static int toVkVertexAttributeFormat(PipelineDescriptor.VertexAttributeFormat format) {
            return switch (format) {
                case R8_UNORM -> VK10.VK_FORMAT_R8_UNORM;
                case R8G8_UNORM -> VK10.VK_FORMAT_R8G8_UNORM;
                case R8G8B8_UNORM -> VK10.VK_FORMAT_R8G8B8_UNORM;
                case R8G8B8A8_UNORM -> VK10.VK_FORMAT_R8G8B8A8_UNORM;
                case R8_UINT -> VK10.VK_FORMAT_R8_UINT;
                case R8G8_UINT -> VK10.VK_FORMAT_R8G8_UINT;
                case R8G8B8_UINT -> VK10.VK_FORMAT_R8G8B8_UINT;
                case R8G8B8A8_UINT -> VK10.VK_FORMAT_R8G8B8A8_UINT;
                case R8_SNORM -> VK10.VK_FORMAT_R8_SNORM;
                case R8G8_SNORM -> VK10.VK_FORMAT_R8G8_SNORM;
                case R8G8B8_SNORM -> VK10.VK_FORMAT_R8G8B8_SNORM;
                case R8G8B8A8_SNORM -> VK10.VK_FORMAT_R8G8B8A8_SNORM;
                case R8_SINT -> VK10.VK_FORMAT_R8_SINT;
                case R8G8_SINT -> VK10.VK_FORMAT_R8G8_SINT;
                case R8G8B8_SINT -> VK10.VK_FORMAT_R8G8B8_SINT;
                case R8G8B8A8_SINT -> VK10.VK_FORMAT_R8G8B8A8_SINT;
                case R16_USCALED -> VK10.VK_FORMAT_R16_USCALED;
                case R16G16_USCALED -> VK10.VK_FORMAT_R16G16_USCALED;
                case R16G16B16_USCALED -> VK10.VK_FORMAT_R16G16B16_USCALED;
                case R16G16B16A16_USCALED -> VK10.VK_FORMAT_R16G16B16A16_USCALED;
                case R16_UINT -> VK10.VK_FORMAT_R16_UINT;
                case R16G16_UINT -> VK10.VK_FORMAT_R16G16_UINT;
                case R16G16B16_UINT -> VK10.VK_FORMAT_R16G16B16_UINT;
                case R16G16B16A16_UINT -> VK10.VK_FORMAT_R16G16B16A16_UINT;
                case R16_SSCALED -> VK10.VK_FORMAT_R16_SSCALED;
                case R16G16_SSCALED -> VK10.VK_FORMAT_R16G16_SSCALED;
                case R16G16B16_SSCALED -> VK10.VK_FORMAT_R16G16B16_SSCALED;
                case R16G16B16A16_SSCALED -> VK10.VK_FORMAT_R16G16B16A16_SSCALED;
                case R16_SINT -> VK10.VK_FORMAT_R16_SINT;
                case R16G16_SINT -> VK10.VK_FORMAT_R16G16_SINT;
                case R16G16B16_SINT -> VK10.VK_FORMAT_R16G16B16_SINT;
                case R16G16B16A16_SINT -> VK10.VK_FORMAT_R16G16B16A16_SINT;
                case R32_SFLOAT -> VK10.VK_FORMAT_R32_SFLOAT;
                case R32G32_SFLOAT -> VK10.VK_FORMAT_R32G32_SFLOAT;
                case R32G32B32_SFLOAT -> VK10.VK_FORMAT_R32G32B32_SFLOAT;
                case R32G32B32A32_SFLOAT -> VK10.VK_FORMAT_R32G32B32A32_SFLOAT;
                case R32_SINT -> VK10.VK_FORMAT_R32_SINT;
                case R32G32_SINT -> VK10.VK_FORMAT_R32G32_SINT;
                case R32G32B32_SINT -> VK10.VK_FORMAT_R32G32B32_SINT;
                case R32G32B32A32_SINT -> VK10.VK_FORMAT_R32G32B32A32_SINT;
                case R32_UINT -> VK10.VK_FORMAT_R32_UINT;
                case R32G32_UINT -> VK10.VK_FORMAT_R32G32_UINT;
                case R32G32B32_UINT -> VK10.VK_FORMAT_R32G32B32_UINT;
                case R32G32B32A32_UINT -> VK10.VK_FORMAT_R32G32B32A32_UINT;
            };
        }

        private static boolean isSodiumIrisFloatAttribute(VertexFormatElement element) {
            return (element.index() == 10 && element.type() == VertexFormatElement.Type.BYTE && element.count() == 4)
                || (element.index() == 12 && element.type() == VertexFormatElement.Type.USHORT && element.count() == 2)
                || (element.index() == 13 && element.type() == VertexFormatElement.Type.BYTE && element.count() == 4)
                || (element.index() == 14 && element.type() == VertexFormatElement.Type.BYTE && element.count() == 4);
        }

        private static int toVkPrimitiveTopology(VertexFormat.Mode mode) {
            return switch (mode) {
                case LINES, DEBUG_LINES -> VK10.VK_PRIMITIVE_TOPOLOGY_LINE_LIST;
                case LINE_STRIP, DEBUG_LINE_STRIP -> VK10.VK_PRIMITIVE_TOPOLOGY_LINE_STRIP;
                case TRIANGLES -> VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
                case TRIANGLE_STRIP -> VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP;
                case TRIANGLE_FAN -> VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_FAN;
                // QUADS are emulated via indexed TRIANGLE_LIST in Minecraft's rendering pipeline.
                case QUADS -> VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
            };
        }

        private int toVkPolygonMode(PolygonMode mode, String pipelineLocation) {
            return switch (mode) {
                case FILL -> VK10.VK_POLYGON_MODE_FILL;
                case WIREFRAME -> {
                    if (fillModeNonSolidEnabled) {
                        yield VK10.VK_POLYGON_MODE_LINE;
                    }
                    if (!warnedWireframeFallback) {
                        warnedWireframeFallback = true;
                        LOGGER.warn(
                            "Vulkan physical device '{}' does not have fillModeNonSolid enabled; "
                                + "falling back to filled polygon mode for wireframe pipelines. First pipeline: {}",
                            physicalDeviceName,
                            pipelineLocation
                        );
                    }
                    yield VK10.VK_POLYGON_MODE_FILL;
                }
            };
        }

        private long createShaderModule(VulkanicSpirvModule spirvModule) {
            Objects.requireNonNull(spirvModule, "spirvModule must not be null");

            byte[] spirvBytes = spirvModule.spirvBytes();
            if (spirvBytes.length == 0 || (spirvBytes.length % Integer.BYTES) != 0) {
                throw new IllegalArgumentException(
                    "SPIR-V module byte size must be > 0 and aligned to 4 bytes, got: " + spirvBytes.length);
            }

            try (MemoryStack stack = stackPush()) {
                java.nio.ByteBuffer code = stack.malloc(spirvBytes.length);
                code.put(spirvBytes);
                code.flip();

                VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.calloc(stack)
                    .sType$Default()
                    .pCode(code);

                java.nio.LongBuffer pShaderModule = stack.mallocLong(1);
                checkVk("vkCreateShaderModule",
                    VK10.vkCreateShaderModule(logicalDevice, createInfo, null, pShaderModule));

                long shaderModuleHandle = pShaderModule.get(0);
                managedShaderModules.add(shaderModuleHandle);
                return shaderModuleHandle;
            }
        }

        private void destroyShaderModule(long shaderModuleHandle) {
            if (shaderModuleHandle == VK10.VK_NULL_HANDLE) {
                return;
            }

            managedShaderModules.remove(shaderModuleHandle);
            if (logicalDevice != null) {
                VK10.vkDestroyShaderModule(logicalDevice, shaderModuleHandle, null);
            }
        }

        private long primaryCommandBufferHandle() {
            if (primaryCommandBuffer == null) {
                return 0L;
            }
            return primaryCommandBuffer.address();
        }

        private long logicalDeviceHandle() {
            if (logicalDevice == null) {
                return 0L;
            }
            return logicalDevice.address();
        }

        private long graphicsQueueHandle() {
            if (graphicsQueue == null) {
                return 0L;
            }
            return graphicsQueue.address();
        }

        private int graphicsQueueFamilyIndex() {
            return graphicsQueueFamilyIndex;
        }

        private long commandPoolHandle() {
            return commandPool;
        }

        private long surfaceHandle() {
            return surface;
        }

        private long swapchainHandle() {
            return swapchain;
        }

        private int swapchainImageFormat() {
            return swapchainImageFormat;
        }

        private int swapchainColorSpace() {
            return swapchainColorSpace;
        }

        private int swapchainPresentMode() {
            return swapchainPresentMode;
        }

        private int swapchainImageCount() {
            return swapchainImageCount;
        }

        private int swapchainWidth() {
            return swapchainWidth;
        }

        private int swapchainHeight() {
            return swapchainHeight;
        }

        private boolean isRenderPassActive() {
            return renderPassRecording;
        }

        @Nullable
        private VulkanRenderPassCompatibilityKey activeRenderPassCompatibilityKey() {
            return renderPassRecording ? activeRenderPassCompatibilityKey : null;
        }

        private void cmdSetViewport(long commandBufferHandle, int x, int y, int width, int height) {
            VkCommandBuffer activeCommandBuffer = requireRecordingCommandBuffer(commandBufferHandle, "cmdSetViewport");
            if (renderPassRecording && activeRenderPassTargetsSwapchain && activeRenderPassWidth > 0 && activeRenderPassHeight > 0) {
                // Keep swapchain passes full-frame; shrinking viewport here clips title/loading output into a strip.
                x = 0;
                y = 0;
                width = activeRenderPassWidth;
                height = activeRenderPassHeight;
            }
            int viewportWidth = Math.max(width, 1);
            int viewportHeight = Math.max(height, 1);
            int framebufferHeight = activeRenderPassHeight > 0
                ? activeRenderPassHeight
                : Math.max(swapchainHeight, viewportHeight);
            try (MemoryStack stack = stackPush()) {
                org.lwjgl.vulkan.VkViewport.Buffer viewport = org.lwjgl.vulkan.VkViewport.calloc(1, stack)
                    .x((float) x)
                    .width((float) viewportWidth)
                    .minDepth(0.0f)
                    .maxDepth(1.0f);
                if (renderPassRecording && activeRenderPassTargetsSwapchain) {
                    viewport.y((float) y)
                        .height((float) viewportHeight);
                } else {
                    viewport.y((float) (framebufferHeight - y))
                        .height(-(float) viewportHeight);
                }
                VK10.vkCmdSetViewport(activeCommandBuffer, 0, viewport);
            }
        }

        private void cmdSetScissor(long commandBufferHandle, int x, int y, int width, int height) {
            VkCommandBuffer activeCommandBuffer = requireRecordingCommandBuffer(commandBufferHandle, "cmdSetScissor");
            cachedScissorX = x;
            cachedScissorY = y;
            cachedScissorWidth = width;
            cachedScissorHeight = height;
            hasCachedScissorRect = true;

            if (renderPassRecording && activeRenderPassTargetsSwapchain) {
                applyFullRenderAreaScissor(activeCommandBuffer);
                return;
            }

            if (!renderPassRecording || !scissorTestEnabled) {
                return;
            }

            applyScissorRect(activeCommandBuffer, x, y, width, height);
        }

        private void setScissorTestEnabled(long commandBufferHandle, boolean enabled) {
            VkCommandBuffer activeCommandBuffer = requireRecordingCommandBuffer(commandBufferHandle, "setScissorTestEnabled");
            scissorTestEnabled = enabled;
            if (!renderPassRecording) {
                return;
            }

            if (!enabled) {
                applyFullRenderAreaScissor(activeCommandBuffer);
                return;
            }

            if (hasCachedScissorRect) {
                applyScissorRect(activeCommandBuffer, cachedScissorX, cachedScissorY, cachedScissorWidth, cachedScissorHeight);
            } else {
                applyFullRenderAreaScissor(activeCommandBuffer);
            }
        }

        private void applyScissorRect(VkCommandBuffer activeCommandBuffer, int x, int y, int width, int height) {
            int scissorWidth = Math.max(width, 0);
            int scissorHeight = Math.max(height, 0);
            int framebufferWidth = activeRenderPassWidth > 0
                ? activeRenderPassWidth
                : Math.max(swapchainWidth, scissorWidth);
            int framebufferHeight = activeRenderPassHeight > 0
                ? activeRenderPassHeight
                : Math.max(swapchainHeight, scissorHeight);
            int translatedY = framebufferHeight - (y + scissorHeight);
            int clampedX = Math.max(0, Math.min(x, framebufferWidth));
            int clampedY = Math.max(0, Math.min(translatedY, framebufferHeight));
            int maxWidth = Math.max(0, framebufferWidth - clampedX);
            int maxHeight = Math.max(0, framebufferHeight - clampedY);
            int clampedWidth = Math.min(scissorWidth, maxWidth);
            int clampedHeight = Math.min(scissorHeight, maxHeight);
            try (MemoryStack stack = stackPush()) {
                org.lwjgl.vulkan.VkRect2D.Buffer scissor = org.lwjgl.vulkan.VkRect2D.calloc(1, stack);
                scissor.get(0).offset().x(clampedX).y(clampedY);
                scissor.get(0).extent().width(clampedWidth).height(clampedHeight);
                VK10.vkCmdSetScissor(activeCommandBuffer, 0, scissor);
            }
        }

        private void resetScissorToRenderArea(long commandBufferHandle) {
            VkCommandBuffer activeCommandBuffer = requireRecordingCommandBuffer(commandBufferHandle, "resetScissorToRenderArea");
            if (!renderPassRecording) {
                return;
            }

            applyFullRenderAreaScissor(activeCommandBuffer);
        }

        private void applyFullRenderAreaScissor(VkCommandBuffer activeCommandBuffer) {
            int fullWidth = activeRenderPassWidth > 0 ? activeRenderPassWidth : swapchainWidth;
            int fullHeight = activeRenderPassHeight > 0 ? activeRenderPassHeight : swapchainHeight;
            if (fullWidth <= 0 || fullHeight <= 0) {
                return;
            }
            applyScissorRect(activeCommandBuffer, 0, 0, fullWidth, fullHeight);
        }

        private void cmdClearAttachments(long commandBufferHandle,
                                         boolean clearColor,
                                         float cr, float cg, float cb, float ca,
                                         boolean clearDepth, float depth) {
            VkCommandBuffer activeCommandBuffer = requireRecordingCommandBuffer(commandBufferHandle, "cmdClearAttachments");
            if (!renderPassRecording) {
                // Cannot issue ClearAttachments outside a render pass; silently defer.
                return;
            }
            int colorAttachmentCount = clearColor ? activeRenderPassColorAttachmentCount() : 0;
            boolean clearActiveDepth = clearDepth && activeRenderPassDepthTexture != null;
            int attachmentCount = colorAttachmentCount + (clearActiveDepth ? 1 : 0);
            if (attachmentCount == 0) return;

            try (MemoryStack stack = stackPush()) {
                org.lwjgl.vulkan.VkClearAttachment.Buffer attachments =
                    org.lwjgl.vulkan.VkClearAttachment.calloc(attachmentCount, stack);
                org.lwjgl.vulkan.VkClearRect.Buffer rects =
                    org.lwjgl.vulkan.VkClearRect.calloc(1, stack);

                int idx = 0;
                for (int colorAttachment = 0; colorAttachment < colorAttachmentCount; colorAttachment++) {
                    attachments.get(idx)
                        .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                        .colorAttachment(colorAttachment);
                    attachments.get(idx).clearValue().color()
                        .float32(0, cr).float32(1, cg).float32(2, cb).float32(3, ca);
                    idx++;
                }
                if (clearActiveDepth) {
                    attachments.get(idx)
                        .aspectMask(VK10.VK_IMAGE_ASPECT_DEPTH_BIT);
                    attachments.get(idx).clearValue().depthStencil().depth(depth).stencil(0);
                }

                int w = Math.max(1, activeRenderPassWidth > 0 ? activeRenderPassWidth : swapchainWidth);
                int h = Math.max(1, activeRenderPassHeight > 0 ? activeRenderPassHeight : swapchainHeight);
                rects.get(0)
                    .rect(r -> r.offset(o -> o.x(0).y(0)).extent(e -> e.width(w).height(h)))
                    .baseArrayLayer(0)
                    .layerCount(1);

                VK10.vkCmdClearAttachments(activeCommandBuffer, attachments, rects);
            }
        }

        private int activeRenderPassColorAttachmentCount() {
            if (!activeRenderPassColorTextures.isEmpty()) {
                return activeRenderPassColorTextures.size();
            }
            return activeRenderPassTargetsSwapchain ? 1 : 0;
        }

        private void close() {
            if (logicalDevice != null) {
                try {
                    VK10.vkDeviceWaitIdle(logicalDevice);
                } catch (Throwable ignored) {
                }

                flushPendingVulkanResourceDestroys(true);

                if (!managedBufferAllocations.isEmpty()) {
                    java.util.List<Map.Entry<Long, Long>> allocations = new ArrayList<>(managedBufferAllocations.entrySet());
                    for (Map.Entry<Long, Long> allocation : allocations) {
                        destroyManagedBuffer(allocation.getKey(), allocation.getValue());
                    }
                    managedBufferAllocations.clear();
                }

                if (!legacyTextures.isEmpty()) {
                    new ArrayList<>(legacyTextures.values()).forEach(this::destroyLegacyTextureStorage);
                    legacyTextures.clear();
                }
                imageStateTracker.reset();
                flushPendingVulkanResourceDestroys(true);
                legacyTexture2DBindingsByUnit.clear();
                if (!legacyTexelBufferBindingsByTextureId.isEmpty()) {
                    new ArrayList<>(legacyTexelBufferBindingsByTextureId.values()).forEach(texelBinding -> {
                        if (texelBinding.vkBufferViewHandle != VK10.VK_NULL_HANDLE) {
                            VK10.vkDestroyBufferView(logicalDevice, texelBinding.vkBufferViewHandle, null);
                        }
                    });
                    legacyTexelBufferBindingsByTextureId.clear();
                }
                proxyTexture2DLevels.clear();

                if (!managedExtraImageViews.isEmpty()) {
                    new ArrayList<>(managedExtraImageViews).forEach(viewHandle -> destroyManagedImageView(viewHandle));
                }

                if (!managedImageAllocations.isEmpty()) {
                    new ArrayList<>(managedImageAllocations.entrySet()).forEach(entry -> {
                        Long defView = managedImageDefaultViews.get(entry.getKey());
                        destroyManagedTexture(entry.getKey(), entry.getValue(),
                            defView != null ? defView : VK10.VK_NULL_HANDLE);
                    });
                }

                releaseSwapchainPresentComposePipeline();

                // Destroy pipelines before their layouts and descriptor set layouts.
                if (!managedVkPipelineHandles.isEmpty()) {
                    new ArrayList<>(managedVkPipelineHandles).forEach(pipelineHandle -> {
                        managedVkPipelineHandles.remove(pipelineHandle);
                        if (pipelineHandle != VK10.VK_NULL_HANDLE) {
                            VK10.vkDestroyPipeline(logicalDevice, pipelineHandle, null);
                        }
                    });
                }
                if (!managedVkPipelineLayoutHandles.isEmpty()) {
                    new ArrayList<>(managedVkPipelineLayoutHandles).forEach(layoutHandle -> {
                        managedVkPipelineLayoutHandles.remove(layoutHandle);
                        if (layoutHandle != VK10.VK_NULL_HANDLE) {
                            VK10.vkDestroyPipelineLayout(logicalDevice, layoutHandle, null);
                        }
                    });
                }
                if (!managedVkDescriptorSetLayoutHandles.isEmpty()) {
                    new ArrayList<>(managedVkDescriptorSetLayoutHandles).forEach(dslHandle -> {
                        managedVkDescriptorSetLayoutHandles.remove(dslHandle);
                        if (dslHandle != VK10.VK_NULL_HANDLE) {
                            VK10.vkDestroyDescriptorSetLayout(logicalDevice, dslHandle, null);
                        }
                    });
                }

                if (!managedShaderModules.isEmpty()) {
                    new ArrayList<>(managedShaderModules).forEach(shaderModuleHandle -> destroyShaderModule(shaderModuleHandle));
                }

                destroyTransientRenderPassResources();
                for (int slot = 0; slot < IMMEDIATE_SUBMIT_SLOTS; slot++) {
                    destroyTransientRenderPassResources(slot);
                }
                destroyAllTransientFrameDescriptorBuffers();
                destroyRecycledDescriptorUniformBuffers();

                if (!permanentRenderPassCache.isEmpty()) {
                    new ArrayList<>(permanentRenderPassCache.values()).forEach(rpHandle -> {
                        if (rpHandle != null && rpHandle != VK10.VK_NULL_HANDLE) {
                            VK10.vkDestroyRenderPass(logicalDevice, rpHandle, null);
                        }
                    });
                    permanentRenderPassCache.clear();
                }

                destroyTrackedSwapchainImageViews();

                if (swapchain != VK10.VK_NULL_HANDLE) {
                    KHRSwapchain.vkDestroySwapchainKHR(logicalDevice, swapchain, null);
                    swapchain = VK10.VK_NULL_HANDLE;
                }

                for (int slot = 0; slot < IMMEDIATE_SUBMIT_SLOTS; slot++) {
                    if (immediateCommandPools[slot] != VK10.VK_NULL_HANDLE) {
                        VK10.vkDestroyCommandPool(logicalDevice, immediateCommandPools[slot], null);
                        immediateCommandPools[slot] = VK10.VK_NULL_HANDLE;
                    }
                    immediateCommandBuffers[slot] = null;
                    immediateSubmitSlotsInFlight[slot] = false;
                }
                commandPool = VK10.VK_NULL_HANDLE;
                primaryCommandBuffer = null;
                currentImmediateSubmitSlot = 0;
                recordingImmediateSubmitSlot = -1;

                for (int frameIndex = 0; frameIndex < MAX_FRAMES_IN_FLIGHT; frameIndex++) {
                    if (frameCommandPools[frameIndex] != VK10.VK_NULL_HANDLE) {
                        VK10.vkDestroyCommandPool(logicalDevice, frameCommandPools[frameIndex], null);
                        frameCommandPools[frameIndex] = VK10.VK_NULL_HANDLE;
                    }
                    frameCommandBuffers[frameIndex] = null;
                    frameCommandBufferRecording[frameIndex] = false;
                }

                for (int slot = 0; slot < IMMEDIATE_SUBMIT_SLOTS; slot++) {
                    if (immediateDescriptorPools[slot] != VK10.VK_NULL_HANDLE) {
                        VK10.vkDestroyDescriptorPool(logicalDevice, immediateDescriptorPools[slot], null);
                        immediateDescriptorPools[slot] = VK10.VK_NULL_HANDLE;
                    }
                }
                descriptorPool = VK10.VK_NULL_HANDLE;
                if (descriptorSetCacheHitCount > 0
                    || descriptorSetCacheStoreCount > 0
                    || skippedRedundantPipelineBindCount > 0
                    || skippedRedundantDescriptorSetBindCount > 0) {
                    LOGGER.info(
                        "Vulkan descriptor reuse summary: cacheHits={} cacheStores={} skippedPipelineBinds={} skippedDescriptorSetBinds={}",
                        descriptorSetCacheHitCount,
                        descriptorSetCacheStoreCount,
                        skippedRedundantPipelineBindCount,
                        skippedRedundantDescriptorSetBindCount
                    );
                }
                descriptorSetCache.clear();
                lastBoundGraphicsPipelineByCommandBuffer.clear();
                lastBoundDescriptorSetByCommandBuffer.clear();

                for (int slot = 0; slot < IMMEDIATE_SUBMIT_SLOTS; slot++) {
                    if (immediateSubmitFences[slot] != VK10.VK_NULL_HANDLE) {
                        VK10.vkDestroyFence(logicalDevice, immediateSubmitFences[slot], null);
                        immediateSubmitFences[slot] = VK10.VK_NULL_HANDLE;
                    }
                }
                immediateSubmitFence = VK10.VK_NULL_HANDLE;
                immediateSubmitInFlight = false;

                for (int frameIndex = 0; frameIndex < MAX_FRAMES_IN_FLIGHT; frameIndex++) {
                    if (swapchainImageAvailableSemaphores[frameIndex] != VK10.VK_NULL_HANDLE) {
                        VK10.vkDestroySemaphore(logicalDevice, swapchainImageAvailableSemaphores[frameIndex], null);
                        swapchainImageAvailableSemaphores[frameIndex] = VK10.VK_NULL_HANDLE;
                    }
                    if (swapchainFrameFences[frameIndex] != VK10.VK_NULL_HANDLE) {
                        VK10.vkDestroyFence(logicalDevice, swapchainFrameFences[frameIndex], null);
                        swapchainFrameFences[frameIndex] = VK10.VK_NULL_HANDLE;
                    }
                }
                swapchainImagesInFlight = new long[0];
                currentFrameSyncIndex = 0;

                if (!descriptorSamplerCache.isEmpty()) {
                    new ArrayList<>(descriptorSamplerCache.values()).forEach(samplerHandle -> {
                        if (samplerHandle != null && samplerHandle != VK10.VK_NULL_HANDLE) {
                            VK10.vkDestroySampler(logicalDevice, samplerHandle, null);
                        }
                    });
                    descriptorSamplerCache.clear();
                }

                if (defaultDescriptorSampler != VK10.VK_NULL_HANDLE) {
                    VK10.vkDestroySampler(logicalDevice, defaultDescriptorSampler, null);
                    defaultDescriptorSampler = VK10.VK_NULL_HANDLE;
                }

                VK10.vkDestroyDevice(logicalDevice, null);
                logicalDevice = null;
                graphicsQueue = null;
                primaryCommandBuffer = null;
                commandBufferRecording = false;
                instanceProperties2ExtensionEnabled = false;
                presentIdExtensionEnabled = false;
                presentWaitExtensionEnabled = false;
                nextPresentId = 1L;
                frameInProgress = false;
                acquiredSwapchainImageIndex = -1;
                pendingPresentTextureRequest = null;
            }

            if (instance != null) {
                if (surface != VK10.VK_NULL_HANDLE) {
                    KHRSurface.vkDestroySurfaceKHR(instance, surface, null);
                    surface = VK10.VK_NULL_HANDLE;
                }

                VK10.vkDestroyInstance(instance, null);
                instance = null;
                physicalDevice = null;
                fillModeNonSolidSupported = false;
                fillModeNonSolidEnabled = false;
                warnedWireframeFallback = false;
            }
        }

        private static void checkVk(String operation, int result) {
            if (result != VK10.VK_SUCCESS) {
                throw new IllegalStateException(operation + " failed with VkResult=" + result);
            }
        }
    }
}

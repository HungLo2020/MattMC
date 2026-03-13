package net.vulkanic.backends.vulkan;

import net.blaze3d.GpuOutOfMemoryException;
import net.vulkanic.CommandContext;
import net.vulkanic.GraphicsBackendType;
import net.vulkanic.PipelineDescriptor;
import net.vulkanic.PipelineHandle;
import net.vulkanic.PipelineResourceBindings;
import net.vulkanic.VulkanReadinessReport;
import net.vulkanic.VulkanicBufferSlice;
import net.vulkanic.VulkanicBuffer;
import net.vulkanic.VulkanicIndexType;
import net.vulkanic.VulkanicRenderPass;
import net.vulkanic.VulkanicRenderPassDescriptor;
import net.vulkanic.VulkanicTexture;
import net.vulkanic.VulkanicTextureFormat;
import net.vulkanic.VulkanicTextureView;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicShaderStage;
import net.vulkanic.VulkanicSpirvModule;
import net.vulkanic.VulkanExecutionContextInfo;
import net.vulkanic.VulkanNativeInitializationInfo;
import net.vulkanic.VulkanSwapchainSurfaceInfo;
import net.vulkanic.VulkanicBufferTarget;
import net.vulkanic.VulkanicResourceBarriers;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.KHRSurface;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkAttachmentDescription;
import org.lwjgl.vulkan.VkAttachmentReference;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkExtent2D;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryBarrier;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkQueueFamilyProperties;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;
import org.lwjgl.vulkan.VkRenderPassCreateInfo;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkSubpassDependency;
import org.lwjgl.vulkan.VkSubpassDescription;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR;
import net.blaze3d.platform.DepthTestFunction;
import net.blaze3d.platform.DestFactor;
import net.blaze3d.platform.LogicOp;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.nio.ByteOrder;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.lwjgl.system.MemoryStack.stackPush;

public class VulkanBackend {

    private static final int GL_LUMINANCE = 0x1909;
    private static final int GL_LUMINANCE_ALPHA = 0x190A;

    private final Object nativeInitLock = new Object();
    private volatile NativeSpine nativeSpine;
    private volatile boolean nativeBringUpAttempted;
    private volatile String nativeBringUpFailure;

    private volatile VulkanReadinessReport cachedReadinessReport;
    private volatile CommandContext currentCommandContext;

    private final SpirvCompiler spirvCompiler;
    private final AtomicInteger nextVirtualShaderId = new AtomicInteger(1);
    private final AtomicInteger nextVirtualProgramId = new AtomicInteger(1);
    private final Map<Integer, VirtualShader> virtualShaders = new ConcurrentHashMap<>();
    private final Map<Integer, VirtualProgram> virtualPrograms = new ConcurrentHashMap<>();
    private final Map<Long, BoundPipelineResources> boundPipelineResourcesByCommandBuffer = new ConcurrentHashMap<>();

    public VulkanBackend() {
        this(new GlslangSpirvCompiler());
    }

    VulkanBackend(SpirvCompiler spirvCompiler) {
        this.spirvCompiler = Objects.requireNonNull(spirvCompiler, "spirvCompiler must not be null");
    }

    public GraphicsBackendType getBackendType() {
        return GraphicsBackendType.VULKAN;
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
                createdSpine = NativeSpine.create();
                materializeCompiledShaderModules(createdSpine);

                nativeSpine = createdSpine;
                nativeBringUpFailure = null;
            } catch (Throwable throwable) {
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
        return spirvCompiler.compile(shaderStage, glslSource, sourceName, entryPoint);
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
        virtualProgram.linkStatus = false;
    }

    public void linkProgram(CommandContext ctx, int program) {
        VirtualProgram virtualProgram = requireVirtualProgram(program);
        if (virtualProgram.attachedShaderIds.isEmpty()) {
            virtualProgram.linkStatus = false;
            virtualProgram.infoLog = "Program has no attached shaders.";
            return;
        }

        NativeSpine spine = nativeSpine;
        List<String> issues = new ArrayList<>();
        Set<VulkanicShaderStage> seenStages = new HashSet<>();
        for (int shaderId : virtualProgram.attachedShaderIds) {
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
            return;
        }

        virtualProgram.linkStatus = true;
        virtualProgram.infoLog = "";
    }

    public int getProgramParameter(CommandContext ctx, int program, int pname) {
        VirtualProgram virtualProgram = requireVirtualProgram(program);
        if (pname == VulkanicAPI.GL_LINK_STATUS) {
            return virtualProgram.linkStatus ? VulkanicAPI.GL_TRUE : VulkanicAPI.GL_FALSE;
        }
        if (pname == VulkanicAPI.GL_ACTIVE_UNIFORMS) {
            return 0;
        }
        if (pname == VulkanicAPI.GL_ACTIVE_UNIFORM_BLOCKS) {
            return 0;
        }
        return 0;
    }

    public String getProgramInfoLog(CommandContext ctx, int program) {
        return requireVirtualProgram(program).infoLog;
    }

    public void deleteShader(CommandContext ctx, int shader) {
        VirtualShader removedShader = virtualShaders.remove(shader);
        if (removedShader != null) {
            releaseVirtualShaderNativeModule(removedShader);
        }
        for (VirtualProgram virtualProgram : virtualPrograms.values()) {
            virtualProgram.attachedShaderIds.remove(shader);
        }
    }

    public void deleteProgram(CommandContext ctx, int program) {
        virtualPrograms.remove(program);
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
        ensureNativeReady("createManagedTexture");

        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }

        return spine.createManagedTexture(label, usage, format, width, height, depthOrLayers, mipLevels);
    }

    public VulkanicTextureView createManagedTextureView(VulkanicTexture texture) {
        ensureNativeReady("createManagedTextureView");
        if (!(texture instanceof VulkanTexture vulkanTexture)) {
            throw new IllegalArgumentException("Expected VulkanTexture, got: "
                + (texture == null ? "null" : texture.getClass().getName()));
        }
        if (vulkanTexture.isClosed()) {
            throw new IllegalStateException("Cannot create a view of a closed texture");
        }

        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }

        return spine.createManagedTextureView(vulkanTexture, 0, texture.getMipLevels());
    }

    public VulkanicTextureView createManagedTextureView(VulkanicTexture texture, int baseMipLevel, int mipLevelCount) {
        ensureNativeReady("createManagedTextureView");
        if (!(texture instanceof VulkanTexture vulkanTexture)) {
            throw new IllegalArgumentException("Expected VulkanTexture, got: "
                + (texture == null ? "null" : texture.getClass().getName()));
        }
        if (vulkanTexture.isClosed()) {
            throw new IllegalStateException("Cannot create a view of a closed texture");
        }

        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }

        return spine.createManagedTextureView(vulkanTexture, baseMipLevel, mipLevelCount);
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

    public void bindTexture2D(CommandContext ctx, int textureId) {
        bindTexture(ctx, VulkanicAPI.GL_TEXTURE_2D, textureId);
    }

    public void bindTexture(CommandContext ctx, int target, int textureId) {
        requireVulkanCommandBufferHandle("bindTexture", ctx);
        if (target != VulkanicAPI.GL_TEXTURE_2D && target != VulkanicAPI.GL_PROXY_TEXTURE_2D) {
            throw new IllegalArgumentException("Vulkan legacy texture path currently supports only GL_TEXTURE_2D/GL_PROXY_TEXTURE_2D targets, got: " + target);
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
        if (target != VulkanicAPI.GL_TEXTURE_2D) {
            throw new IllegalArgumentException("Vulkan legacy texture path currently supports only GL_TEXTURE_2D createTextures target, got: " + target);
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
        if (target != VulkanicAPI.GL_TEXTURE_2D && target != VulkanicAPI.GL_PROXY_TEXTURE_2D) {
            throw new IllegalArgumentException("Vulkan legacy texture path currently supports only GL_TEXTURE_2D/GL_PROXY_TEXTURE_2D targets, got: " + target);
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
        if (target != VulkanicAPI.GL_TEXTURE_2D && target != VulkanicAPI.GL_PROXY_TEXTURE_2D) {
            throw new IllegalArgumentException("Vulkan legacy texture path currently supports only GL_TEXTURE_2D/GL_PROXY_TEXTURE_2D targets, got: " + target);
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
        if (target != VulkanicAPI.GL_TEXTURE_2D && target != VulkanicAPI.GL_PROXY_TEXTURE_2D) {
            throw new IllegalArgumentException("Vulkan legacy texture path currently supports only GL_TEXTURE_2D/GL_PROXY_TEXTURE_2D targets, got: " + target);
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
        if (target != VulkanicAPI.GL_TEXTURE_2D && target != VulkanicAPI.GL_PROXY_TEXTURE_2D) {
            throw new IllegalArgumentException("Vulkan legacy texture path currently supports only GL_TEXTURE_2D/GL_PROXY_TEXTURE_2D upload targets, got: " + target);
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

        LegacyTextureFormatInfo.resolve(internalFormat, format, type);

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
        if (target != VulkanicAPI.GL_TEXTURE_2D && target != VulkanicAPI.GL_PROXY_TEXTURE_2D) {
            throw new IllegalArgumentException("Vulkan legacy texture path currently supports only GL_TEXTURE_2D/GL_PROXY_TEXTURE_2D upload targets, got: " + target);
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
        if (target != VulkanicAPI.GL_TEXTURE_2D && target != VulkanicAPI.GL_PROXY_TEXTURE_2D) {
            throw new IllegalArgumentException("Vulkan legacy texture path currently supports only GL_TEXTURE_2D/GL_PROXY_TEXTURE_2D upload targets, got: " + target);
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
        private final int aspectMask;

        private LegacyTextureFormatInfo(int vkFormat, int pixelBytes, int aspectMask) {
            this.vkFormat = vkFormat;
            this.pixelBytes = pixelBytes;
            this.aspectMask = aspectMask;
        }

        private static LegacyTextureFormatInfo resolve(int internalFormat, int format, int type) {
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

            if (internalFormat == VulkanicAPI.GL_RGBA16F
                || (format == VulkanicAPI.GL_RGBA && type == VulkanicAPI.GL_HALF_FLOAT)) {
                return new LegacyTextureFormatInfo(VK10.VK_FORMAT_R16G16B16A16_SFLOAT, 8, VK10.VK_IMAGE_ASPECT_COLOR_BIT);
            }

            if ((internalFormat == VulkanicAPI.GL_RGBA16 || internalFormat == VulkanicAPI.GL_RGBA)
                && (type == VulkanicAPI.GL_UNSIGNED_SHORT_4_4_4_4
                || type == VulkanicAPI.GL_UNSIGNED_SHORT_4_4_4_4_REV)) {
                return new LegacyTextureFormatInfo(VK10.VK_FORMAT_R4G4B4A4_UNORM_PACK16, 2, VK10.VK_IMAGE_ASPECT_COLOR_BIT);
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
                return new LegacyTextureFormatInfo(VK10.VK_FORMAT_R8G8B8_UNORM, 3, VK10.VK_IMAGE_ASPECT_COLOR_BIT);
            }

            throw new IllegalArgumentException(
                "Unsupported legacy texture upload format combination: internalFormat=" + internalFormat
                    + ", format=" + format + ", type=" + type);
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
        private final NativeSpine spine;
        private volatile boolean closed;

        private VulkanPipelineHandle(
            long vkPipelineHandle,
            long vkPipelineLayoutHandle,
            long vkDescriptorSetLayoutHandle,
            NativeSpine spine
        ) {
            this.vkPipelineHandle = vkPipelineHandle;
            this.vkPipelineLayoutHandle = vkPipelineLayoutHandle;
            this.vkDescriptorSetLayoutHandle = vkDescriptorSetLayoutHandle;
            this.spine = Objects.requireNonNull(spine, "spine must not be null");
        }

        /** Returns the native {@code VkPipeline} handle for command-buffer binding. */
        long getVkPipelineHandle() {
            return vkPipelineHandle;
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
                + Long.toHexString(vkPipelineHandle) + "}";
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
            return spine.createVulkanPipeline(descriptor, vertModuleHandle, fragModuleHandle);
        } finally {
            spine.destroyShaderModule(vertModuleHandle);
            spine.destroyShaderModule(fragModuleHandle);
        }
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
            if (resourceBinding.type() != PipelineDescriptor.ResourceType.UNIFORM_BUFFER) {
                continue;
            }

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

        boundPipelineResourcesByCommandBuffer.put(
            commandBufferHandle,
            new BoundPipelineResources(pipeline, descriptor, bindings));
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
                    srcStageMask |= shaderStages;
                    dstStageMask |= shaderStages;
                    srcAccessMask |= VK10.VK_ACCESS_SHADER_WRITE_BIT;
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
        private final VulkanTexture colorTexture;
        private final VulkanTextureView depthView;
        private final VulkanTexture depthTexture;
        private final int width;
        private final int height;

        private ResolvedRenderTargets(
            VulkanTextureView colorView,
            VulkanTexture colorTexture,
            VulkanTextureView depthView,
            VulkanTexture depthTexture,
            int width,
            int height
        ) {
            this.colorView = colorView;
            this.colorTexture = colorTexture;
            this.depthView = depthView;
            this.depthTexture = depthTexture;
            this.width = width;
            this.height = height;
        }

        private boolean hasDepthTarget() {
            return depthView != null;
        }
    }

    private static ResolvedRenderTargets resolveRenderTargets(VulkanicRenderPassDescriptor descriptor) {
        VulkanTextureView colorView = requireVulkanTextureView(descriptor.colorAttachment().target(), "colorAttachment.target");
        VulkanTexture colorTexture = requireVulkanTexture(colorView.texture(), "colorAttachment.texture");
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

        VulkanicRenderPassDescriptor.DepthAttachment depthAttachment = descriptor.depthAttachment();
        if (depthAttachment == null) {
            return new ResolvedRenderTargets(colorView, colorTexture, null, null, width, height);
        }

        VulkanTextureView depthView = requireVulkanTextureView(depthAttachment.target(), "depthAttachment.target");
        VulkanTexture depthTexture = requireVulkanTexture(depthView.texture(), "depthAttachment.texture");
        if (!depthTexture.getVulkanicFormat().hasDepthAspect()) {
            throw new IllegalArgumentException("Depth attachment must use a depth-capable texture format");
        }
        if ((depthTexture.usage() & VulkanicTexture.USAGE_RENDER_ATTACHMENT) == 0) {
            throw new IllegalArgumentException("Depth attachment texture must include USAGE_RENDER_ATTACHMENT");
        }
        if (depthView.getWidth(0) != width || depthView.getHeight(0) != height) {
            throw new IllegalArgumentException("Depth attachment dimensions must match color attachment dimensions");
        }

        return new ResolvedRenderTargets(colorView, colorTexture, depthView, depthTexture, width, height);
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

    private static VulkanTexture requireVulkanTexture(VulkanicTexture texture, String fieldName) {
        if (!(texture instanceof VulkanTexture vulkanTexture)) {
            throw new IllegalArgumentException("Vulkan render pass requires VulkanTexture for " + fieldName
                + ", got: " + (texture == null ? "null" : texture.getClass().getName()));
        }
        if (vulkanTexture.isClosed()) {
            throw new IllegalStateException("Cannot use closed VulkanTexture for " + fieldName);
        }
        return vulkanTexture;
    }

    private static final class VulkanBackedRenderPass implements VulkanicRenderPass {
        private final NativeSpine spine;
        private final long commandBufferHandle;
        private volatile boolean closed;

        private VulkanBackedRenderPass(NativeSpine spine, long commandBufferHandle) {
            this.spine = Objects.requireNonNull(spine, "spine must not be null");
            this.commandBufferHandle = commandBufferHandle;
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

        long commandBufferHandle = spine.beginPrimaryCommandBuffer();
        boundPipelineResourcesByCommandBuffer.remove(commandBufferHandle);
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

        spine.submitPrimaryCommandBuffer(commandBufferHandle);
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
        VulkanicRenderPassDescriptor safeDescriptor = Objects.requireNonNull(descriptor, "descriptor must not be null");
        ResolvedRenderTargets resolvedTargets = resolveRenderTargets(safeDescriptor);

        ensureNativeReady("beginRenderPass");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }

        spine.beginRenderPass(commandBufferHandle, safeDescriptor, resolvedTargets);
        return new VulkanBackedRenderPass(spine, commandBufferHandle);
    }

    public boolean isFallbackMode() {
        return !isNativeVulkanReady();
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
        private volatile String infoLog = "";

        private VirtualShader(VulkanicShaderStage stage) {
            this.stage = stage;
        }
    }

    private static final class VirtualProgram {
        private final Set<Integer> attachedShaderIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
        private volatile boolean linkStatus;
        private volatile String infoLog = "";
    }

    private static final class NativeSpine {
        private VkInstance instance;
        private VkPhysicalDevice physicalDevice;
        private VkDevice logicalDevice;
        private VkQueue graphicsQueue;

        private long surface;
        private long swapchain;
        private long commandPool;

        private final Map<Long, Long> managedBufferAllocations = new ConcurrentHashMap<>();
        private final AtomicInteger nextLegacyBufferId = new AtomicInteger(1);
        private final Map<Integer, LegacyBufferObject> legacyBuffers = new ConcurrentHashMap<>();
        private final Map<Integer, Integer> legacyBufferBindings = new ConcurrentHashMap<>();
        private final Map<Integer, VulkanicBuffer.MappedView> legacyBufferMappedViews = new ConcurrentHashMap<>();
        private final AtomicInteger nextLegacyTextureId = new AtomicInteger(1);
        private final Map<Integer, LegacyTextureObject> legacyTextures = new ConcurrentHashMap<>();
        private final Map<Integer, Integer> legacyTexture2DBindingsByUnit = new ConcurrentHashMap<>();
        private final Map<Integer, TextureLevelInfo> proxyTexture2DLevels = new ConcurrentHashMap<>();
        private final Set<Long> managedShaderModules = ConcurrentHashMap.newKeySet();
        private final Set<Long> transientRenderPassHandles = ConcurrentHashMap.newKeySet();
        private final Set<Long> transientFramebufferHandles = ConcurrentHashMap.newKeySet();
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

        private VkCommandBuffer primaryCommandBuffer;
        private int graphicsQueueFamilyIndex;
        private long windowHandle;
        private boolean commandBufferRecording;
        private boolean renderPassRecording;
        private boolean frameInProgress;
        private int acquiredSwapchainImageIndex = -1;
        private int activeTextureUnitIndex;

        private final PixelStoreState pixelStoreState = new PixelStoreState();

        private static final int GL_MAP_READ_BIT = 0x0001;

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

        private static final class LegacyTextureObject {
            private final int id;
            private final int target;
            private final Map<Integer, Integer> integerParameters = new ConcurrentHashMap<>();
            private final Map<Integer, TextureLevelInfo> levels = new ConcurrentHashMap<>();

            private volatile long imageHandle;
            private volatile long memoryHandle;
            private volatile long defaultViewHandle;
            private volatile int vkFormat = VK10.VK_FORMAT_UNDEFINED;
            private volatile int aspectMask = VK10.VK_IMAGE_ASPECT_COLOR_BIT;
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

        private static NativeSpine create() {
            NativeSpine spine = new NativeSpine();
            try {
                spine.initialize();
                return spine;
            } catch (Throwable throwable) {
                spine.close();
                throw throwable;
            }
        }

        private void initialize() {
            windowHandle = GLFW.glfwGetCurrentContext();
            if (windowHandle == 0L) {
                throw new IllegalStateException(
                    "No current GLFW window/context. Vulkan native spine requires an active GLFW context for surface/swapchain bring-up.");
            }

            createInstance();
            createSurface();
            pickPhysicalDeviceAndQueueFamily();
            createLogicalDeviceAndQueue();
            createSwapchain();
            createCommandPoolAndPrimaryBuffer();
        }

        private void createInstance() {
            try (MemoryStack stack = stackPush()) {
                org.lwjgl.PointerBuffer requiredExtensions = GLFWVulkan.glfwGetRequiredInstanceExtensions();
                if (requiredExtensions == null || requiredExtensions.remaining() == 0) {
                    throw new IllegalStateException(
                        "GLFW did not provide Vulkan required instance extensions (null/empty result).");
                }

                VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack)
                    .sType$Default()
                    .pApplicationName(stack.UTF8("Vulkanic"))
                    .applicationVersion(VK10.VK_MAKE_API_VERSION(0, 0, 1, 0))
                    .pEngineName(stack.UTF8("Vulkanic"))
                    .engineVersion(VK10.VK_MAKE_API_VERSION(0, 0, 1, 0))
                    .apiVersion(Math.max(VK.getInstanceVersionSupported(), VK10.VK_API_VERSION_1_0));

                VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack)
                    .sType$Default()
                    .pApplicationInfo(appInfo)
                    .ppEnabledExtensionNames(requiredExtensions);

                org.lwjgl.PointerBuffer pInstance = stack.mallocPointer(1);
                checkVk("vkCreateInstance", VK10.vkCreateInstance(createInfo, null, pInstance));
                instance = new VkInstance(pInstance.get(0), createInfo);
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
                        return;
                    }
                }

                throw new IllegalStateException(
                    "No physical device with combined graphics+present queue support for GLFW surface was found.");
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
                        return OptionalInt.of(familyIndex);
                    }
                }

                return OptionalInt.empty();
            }
        }

        private void createLogicalDeviceAndQueue() {
            try (MemoryStack stack = stackPush()) {
                java.nio.FloatBuffer priorities = stack.floats(1.0f);

                VkDeviceQueueCreateInfo.Buffer queueCreateInfos = VkDeviceQueueCreateInfo.calloc(1, stack);
                queueCreateInfos.get(0)
                    .sType$Default()
                    .queueFamilyIndex(graphicsQueueFamilyIndex)
                    .pQueuePriorities(priorities);

                org.lwjgl.PointerBuffer enabledExtensions = stack.pointers(
                    stack.UTF8(KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME)
                );

                VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.calloc(stack)
                    .sType$Default()
                    .pQueueCreateInfos(queueCreateInfos)
                    .ppEnabledExtensionNames(enabledExtensions);

                org.lwjgl.PointerBuffer pDevice = stack.mallocPointer(1);
                checkVk("vkCreateDevice", VK10.vkCreateDevice(physicalDevice, createInfo, null, pDevice));
                logicalDevice = new VkDevice(pDevice.get(0), physicalDevice, createInfo);

                org.lwjgl.PointerBuffer pQueue = stack.mallocPointer(1);
                VK10.vkGetDeviceQueue(logicalDevice, graphicsQueueFamilyIndex, 0, pQueue);
                graphicsQueue = new VkQueue(pQueue.get(0), logicalDevice);
            }
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

            LegacyTextureObject texture = legacyTextures.remove(textureId);
            if (texture != null) {
                destroyLegacyTextureStorage(texture);
            }

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

            legacyTextures.computeIfAbsent(textureId, id -> new LegacyTextureObject(id, target));
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

        private void setActiveTextureUnit(int unit) {
            int normalized = unit >= VulkanicAPI.GL_TEXTURE0
                ? unit - VulkanicAPI.GL_TEXTURE0
                : unit;
            if (normalized < 0) {
                throw new IllegalArgumentException("Texture unit must be >= 0, got: " + unit);
            }
            activeTextureUnitIndex = normalized;
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
            if (target != VulkanicAPI.GL_TEXTURE_2D) {
                throw new IllegalArgumentException(
                    operation + " currently supports only GL_TEXTURE_2D target, got: " + target);
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
            int rowBytes = rowLength * formatInfo.pixelBytes;
            int stride = align(rowBytes, pixelStoreState.unpackAlignment);
            int startOffset = pixelStoreState.unpackSkipRows * stride
                + pixelStoreState.unpackSkipPixels * formatInfo.pixelBytes;

            long requiredLong = (long) startOffset
                + (long) (height - 1) * stride
                + (long) width * formatInfo.pixelBytes;
            if (requiredLong > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Pixel upload source size exceeds int range: " + requiredLong);
            }
            int required = (int) requiredLong;
            if (pixels.remaining() < required) {
                throw new IllegalArgumentException(
                    "Pixel upload buffer too small. Required=" + required + ", remaining=" + pixels.remaining());
            }

            int tightlyPackedRowBytes = width * formatInfo.pixelBytes;
            if (stride == tightlyPackedRowBytes) {
                java.nio.ByteBuffer source = pixels.duplicate();
                source.position(source.position() + startOffset);
                source.limit(source.position() + tightlyPackedRowBytes * height);
                return source.slice();
            }

            java.nio.ByteBuffer packed = java.nio.ByteBuffer.allocateDirect(tightlyPackedRowBytes * height)
                .order(ByteOrder.nativeOrder());

            java.nio.ByteBuffer source = pixels.duplicate();
            int sourceBase = source.position() + startOffset;
            for (int row = 0; row < height; row++) {
                int rowStart = sourceBase + row * stride;
                java.nio.ByteBuffer rowSlice = source.duplicate();
                rowSlice.position(rowStart);
                rowSlice.limit(rowStart + tightlyPackedRowBytes);
                packed.put(rowSlice);
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

            LegacyTextureFormatInfo formatInfo = LegacyTextureFormatInfo.resolve(internalFormat, format, type);

            if (target == VulkanicAPI.GL_PROXY_TEXTURE_2D) {
                proxyTexture2DLevels.put(level, new TextureLevelInfo(width, height, internalFormat));
                return;
            }

            LegacyTextureObject texture = requireBoundLegacyTexture2D(target, "uploadTexture2D");
            proxyTexture2DLevels.remove(level);

            int inferredBaseWidth = level == 0 ? width : Math.max(1, width << level);
            int inferredBaseHeight = level == 0 ? height : Math.max(1, height << level);
            int requiredMipLevels = Math.max(1, level + 1);

            boolean needsRecreate = texture.imageHandle == VK10.VK_NULL_HANDLE
                || texture.vkFormat != formatInfo.vkFormat
                || texture.width != inferredBaseWidth
                || texture.height != inferredBaseHeight
                || texture.mipLevels < requiredMipLevels;

            if (needsRecreate) {
                recreateLegacyTextureStorage(texture, formatInfo, inferredBaseWidth, inferredBaseHeight, requiredMipLevels);
            }

            texture.sourceFormat = format;
            texture.sourceType = type;
            texture.levels.put(level, new TextureLevelInfo(width, height, internalFormat));

            if (pixels == null) {
                int finalLayout = texture.aspectMask == VK10.VK_IMAGE_ASPECT_DEPTH_BIT
                    ? VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL
                    : VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
                transitionImageLayout(texture, texture.currentLayout, finalLayout, level, 1);
                texture.currentLayout = finalLayout;
                return;
            }

            java.nio.ByteBuffer packedPixels = normalizePixelData(pixels, formatInfo, width, height);
            uploadToLegacyTextureRegion(texture, level, 0, 0, width, height, packedPixels);
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
            int stride = align(rowLength * formatInfo.pixelBytes, pixelStoreState.unpackAlignment);
            int startOffset = pixelStoreState.unpackSkipRows * stride
                + pixelStoreState.unpackSkipPixels * formatInfo.pixelBytes;
            int required = startOffset + (height - 1) * stride + width * formatInfo.pixelBytes;

            java.nio.ByteBuffer source = MemoryUtil.memByteBuffer(pixelsPointer, required);
            java.nio.ByteBuffer packedPixels = normalizePixelData(source, formatInfo, width, height);
            uploadToLegacyTextureRegion(texture, level, xOffset, yOffset, width, height, packedPixels);
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
            uploadToLegacyTextureRegion(texture, level, xOffset, yOffset, width, height, packedPixels);
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
                VkImageCreateInfo imageCreateInfo = VkImageCreateInfo.calloc(stack)
                    .sType$Default()
                    .imageType(VK10.VK_IMAGE_TYPE_2D)
                    .format(formatInfo.vkFormat)
                    .mipLevels(mipLevels)
                    .arrayLayers(1)
                    .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK10.VK_IMAGE_TILING_OPTIMAL)
                    .usage(VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT
                        | VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT
                        | VK10.VK_IMAGE_USAGE_SAMPLED_BIT)
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

                int memoryTypeIndex = findMemoryTypeIndex(
                    memoryRequirements.memoryTypeBits(),
                    VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
                );
                if (memoryTypeIndex < 0) {
                    throw new IllegalStateException("No device-local memory type available for legacy Vulkan texture allocation");
                }

                VkMemoryAllocateInfo memoryAllocateInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default()
                    .allocationSize(memoryRequirements.size())
                    .memoryTypeIndex(memoryTypeIndex);

                java.nio.LongBuffer pMemory = stack.mallocLong(1);
                checkVk("vkAllocateMemory(legacy texture)", VK10.vkAllocateMemory(logicalDevice, memoryAllocateInfo, null, pMemory));
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
                    1
                );

                texture.imageHandle = imageHandle;
                texture.memoryHandle = memoryHandle;
                texture.defaultViewHandle = defaultViewHandle;
                texture.vkFormat = formatInfo.vkFormat;
                texture.aspectMask = formatInfo.aspectMask;
                texture.pixelBytes = formatInfo.pixelBytes;
                texture.currentLayout = VK10.VK_IMAGE_LAYOUT_UNDEFINED;
                texture.mipLevels = mipLevels;
                texture.width = width;
                texture.height = height;
                texture.levels.clear();
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

        private void destroyLegacyTextureStorage(LegacyTextureObject texture) {
            if (logicalDevice != null) {
                if (texture.defaultViewHandle != VK10.VK_NULL_HANDLE) {
                    VK10.vkDestroyImageView(logicalDevice, texture.defaultViewHandle, null);
                }
                if (texture.imageHandle != VK10.VK_NULL_HANDLE) {
                    VK10.vkDestroyImage(logicalDevice, texture.imageHandle, null);
                }
                if (texture.memoryHandle != VK10.VK_NULL_HANDLE) {
                    VK10.vkFreeMemory(logicalDevice, texture.memoryHandle, null);
                }
            }

            texture.imageHandle = VK10.VK_NULL_HANDLE;
            texture.memoryHandle = VK10.VK_NULL_HANDLE;
            texture.defaultViewHandle = VK10.VK_NULL_HANDLE;
            texture.currentLayout = VK10.VK_IMAGE_LAYOUT_UNDEFINED;
            texture.levels.clear();
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

        private void transitionImageLayout(LegacyTextureObject texture,
                                           int oldLayout,
                                           int newLayout,
                                           int baseMipLevel,
                                           int levelCount) {
            try (MemoryStack stack = stackPush()) {
                VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
                barrier.get(0)
                    .sType$Default()
                    .oldLayout(oldLayout)
                    .newLayout(newLayout)
                    .srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                    .image(texture.imageHandle);
                barrier.get(0).subresourceRange()
                    .aspectMask(texture.aspectMask)
                    .baseMipLevel(baseMipLevel)
                    .levelCount(levelCount)
                    .baseArrayLayer(0)
                    .layerCount(1);

                int srcStage;
                int dstStage;
                int srcAccessMask;
                int dstAccessMask;

                if (oldLayout == VK10.VK_IMAGE_LAYOUT_UNDEFINED) {
                    srcStage = VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
                    srcAccessMask = 0;
                } else if (oldLayout == VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
                    srcStage = VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;
                    srcAccessMask = VK10.VK_ACCESS_TRANSFER_WRITE_BIT;
                } else {
                    srcStage = VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
                    srcAccessMask = VK10.VK_ACCESS_SHADER_READ_BIT;
                }

                if (newLayout == VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
                    dstStage = VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;
                    dstAccessMask = VK10.VK_ACCESS_TRANSFER_WRITE_BIT;
                } else {
                    dstStage = VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
                    dstAccessMask = VK10.VK_ACCESS_SHADER_READ_BIT;
                }

                barrier.get(0).srcAccessMask(srcAccessMask).dstAccessMask(dstAccessMask);

                VK10.vkCmdPipelineBarrier(
                    primaryCommandBuffer,
                    srcStage,
                    dstStage,
                    0,
                    null,
                    null,
                    barrier
                );
            }
        }

        private void uploadToLegacyTextureRegion(LegacyTextureObject texture,
                                                 int level,
                                                 int xOffset,
                                                 int yOffset,
                                                 int width,
                                                 int height,
                                                 java.nio.ByteBuffer pixels) {
            StagingBuffer stagingBuffer = createStagingBuffer(pixels);
            try {
                transitionImageLayout(texture, texture.currentLayout, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, level, 1);

                try (MemoryStack stack = stackPush()) {
                    VkBufferImageCopy.Buffer regions = VkBufferImageCopy.calloc(1, stack);
                    regions.get(0)
                        .bufferOffset(0L)
                        .bufferRowLength(0)
                        .bufferImageHeight(0);
                    regions.get(0).imageSubresource()
                        .aspectMask(texture.aspectMask)
                        .mipLevel(level)
                        .baseArrayLayer(0)
                        .layerCount(1);
                    regions.get(0).imageOffset().set(xOffset, yOffset, 0);
                    regions.get(0).imageExtent().set(width, height, 1);

                    VK10.vkCmdCopyBufferToImage(
                        primaryCommandBuffer,
                        stagingBuffer.bufferHandle,
                        texture.imageHandle,
                        VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        regions
                    );
                }

                int finalLayout = texture.aspectMask == VK10.VK_IMAGE_ASPECT_DEPTH_BIT
                    ? VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL
                    : VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
                transitionImageLayout(texture, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, finalLayout, level, 1);
                texture.currentLayout = finalLayout;
            } finally {
                destroyStagingBuffer(stagingBuffer);
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
                usage |= VulkanicBuffer.USAGE_VERTEX;
            } else if (target == VulkanicAPI.GL_ELEMENT_ARRAY_BUFFER) {
                usage |= VulkanicBuffer.USAGE_INDEX;
            } else if (target == VulkanicAPI.GL_UNIFORM_BUFFER) {
                usage |= VulkanicBuffer.USAGE_UNIFORM;
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

        private void drawLegacyArrays(long commandBufferHandle,
                                      int mode,
                                      int first,
                                      int count,
                                      int instanceCount) {
            VulkanBuffer vertexBuffer = requireLegacyDrawBuffer(VulkanicAPI.GL_ARRAY_BUFFER, "drawArrays");
            bindVertexBuffer(commandBufferHandle, 0, vertexBuffer.getVkBufferHandle());
            drawInstanced(commandBufferHandle, first, count, instanceCount);
        }

        private void drawLegacyElements(long commandBufferHandle,
                                        int mode,
                                        int count,
                                        VulkanicIndexType indexType,
                                        long indices,
                                        int instanceCount,
                                        int baseVertex) {
            VulkanBuffer vertexBuffer = requireLegacyDrawBuffer(VulkanicAPI.GL_ARRAY_BUFFER, "drawElements(vertex)");
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

            bindVertexBuffer(commandBufferHandle, 0, vertexBuffer.getVkBufferHandle());
            bindIndexBuffer(commandBufferHandle, indexBuffer.getVkBufferHandle(), indexType);
            drawIndexed(commandBufferHandle, (int) firstIndexLong, count, baseVertex, instanceCount);
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

                VkImageCreateInfo imageCreateInfo = VkImageCreateInfo.calloc(stack)
                    .sType$Default()
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
                    aspectMask, 0, mipLevels, depthOrLayers);

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
                long viewHandle = createVkImageView(stack, texture.getVkImageHandle(), vkFormat,
                    aspectMask, baseMipLevel, mipLevelCount, texture.getDepthOrLayers());

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

        private long createVkImageView(MemoryStack stack, long imageHandle, int vkFormat,
                                       int aspectMask, int baseMipLevel, int mipLevelCount,
                                       int layerCount) {
            VkImageViewCreateInfo viewCreateInfo = VkImageViewCreateInfo.calloc(stack)
                .sType$Default()
                .image(imageHandle)
                .viewType(VK10.VK_IMAGE_VIEW_TYPE_2D)
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

        private static int toVkFormat(VulkanicTextureFormat format) {
            return switch (format) {
                case RGBA8   -> VK10.VK_FORMAT_R8G8B8A8_UNORM;
                case RED8    -> VK10.VK_FORMAT_R8_UNORM;
                case RED8I   -> VK10.VK_FORMAT_R8_SINT;
                case DEPTH32 -> VK10.VK_FORMAT_D32_SFLOAT;
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
            managedExtraImageViews.remove(viewHandle);
            if (logicalDevice != null && viewHandle != VK10.VK_NULL_HANDLE) {
                VK10.vkDestroyImageView(logicalDevice, viewHandle, null);
            }
        }

        private void createSwapchain() {
            createSwapchain(VK10.VK_NULL_HANDLE);
        }

        private void createSwapchain(long oldSwapchainHandle) {
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

                int presentMode = choosePresentMode(presentModes);
                VkExtent2D extent = chooseSwapExtent(capabilities, stack);

                int minImageCount = capabilities.minImageCount() + 1;
                if (capabilities.maxImageCount() > 0 && minImageCount > capabilities.maxImageCount()) {
                    minImageCount = capabilities.maxImageCount();
                }

                VkSwapchainCreateInfoKHR createInfo = VkSwapchainCreateInfoKHR.calloc(stack)
                    .sType$Default()
                    .surface(surface)
                    .minImageCount(minImageCount)
                    .imageFormat(chosenFormat.format())
                    .imageColorSpace(chosenFormat.colorSpace())
                    .imageExtent(extent)
                    .imageArrayLayers(1)
                    .imageUsage(VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                    .imageSharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE)
                    .preTransform(capabilities.currentTransform())
                    .compositeAlpha(KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                    .presentMode(presentMode)
                    .clipped(true)
                    .oldSwapchain(oldSwapchainHandle);

                java.nio.LongBuffer pSwapchain = stack.mallocLong(1);
                checkVk("vkCreateSwapchainKHR",
                    KHRSwapchain.vkCreateSwapchainKHR(logicalDevice, createInfo, null, pSwapchain));
                swapchain = pSwapchain.get(0);

                swapchainImageFormat = chosenFormat.format();
                swapchainColorSpace = chosenFormat.colorSpace();
                swapchainPresentMode = presentMode;
                swapchainWidth = extent.width();
                swapchainHeight = extent.height();
                swapchainImageCount = querySwapchainImageCount(stack, swapchain);
            }
        }

        private int querySwapchainImageCount(MemoryStack stack, long swapchainHandle) {
            java.nio.IntBuffer imageCount = stack.ints(0);
            checkVk("vkGetSwapchainImagesKHR(count)",
                KHRSwapchain.vkGetSwapchainImagesKHR(logicalDevice, swapchainHandle, imageCount, null));
            return imageCount.get(0);
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
        }

        private boolean recreateSwapchainIfFramebufferSizeChanged() {
            if (!isFramebufferResizeMismatch()) {
                return false;
            }

            recreateSwapchain();
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

        private static VkSurfaceFormatKHR chooseSurfaceFormat(VkSurfaceFormatKHR.Buffer formats) {
            for (int index = 0; index < formats.remaining(); index++) {
                VkSurfaceFormatKHR format = formats.get(index);
                if (format.format() == VK10.VK_FORMAT_B8G8R8A8_SRGB
                    && format.colorSpace() == KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                    return format;
                }
            }
            return formats.get(0);
        }

        private static int choosePresentMode(java.nio.IntBuffer presentModes) {
            for (int index = 0; index < presentModes.remaining(); index++) {
                int mode = presentModes.get(index);
                if (mode == KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR) {
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

                java.nio.LongBuffer pCommandPool = stack.mallocLong(1);
                checkVk("vkCreateCommandPool",
                    VK10.vkCreateCommandPool(logicalDevice, poolCreateInfo, null, pCommandPool));
                commandPool = pCommandPool.get(0);

                VkCommandBufferAllocateInfo allocateInfo = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType$Default()
                    .commandPool(commandPool)
                    .level(VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(1);

                org.lwjgl.PointerBuffer pCommandBuffer = stack.mallocPointer(1);
                checkVk("vkAllocateCommandBuffers",
                    VK10.vkAllocateCommandBuffers(logicalDevice, allocateInfo, pCommandBuffer));
                primaryCommandBuffer = new VkCommandBuffer(pCommandBuffer.get(0), logicalDevice);
            }
        }

        private int beginFrame() {
            if (logicalDevice == null) {
                throw new IllegalStateException("Cannot begin frame: Vulkan logical device is unavailable.");
            }
            if (graphicsQueue == null) {
                throw new IllegalStateException("Cannot begin frame: Vulkan graphics queue is unavailable.");
            }
            if (swapchain == VK10.VK_NULL_HANDLE) {
                throw new IllegalStateException("Cannot begin frame: Vulkan swapchain is unavailable.");
            }
            if (frameInProgress) {
                throw new IllegalStateException("beginFrame called while a Vulkan frame is already in progress.");
            }

            recreateSwapchainIfFramebufferSizeChanged();

            try (MemoryStack stack = stackPush()) {
                java.nio.IntBuffer pImageIndex = stack.ints(0);
                int acquireResult = KHRSwapchain.vkAcquireNextImageKHR(
                    logicalDevice,
                    swapchain,
                    Long.MAX_VALUE,
                    VK10.VK_NULL_HANDLE,
                    VK10.VK_NULL_HANDLE,
                    pImageIndex
                );

                if (acquireResult == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR) {
                    recreateSwapchain();
                    acquireResult = KHRSwapchain.vkAcquireNextImageKHR(
                        logicalDevice,
                        swapchain,
                        Long.MAX_VALUE,
                        VK10.VK_NULL_HANDLE,
                        VK10.VK_NULL_HANDLE,
                        pImageIndex
                    );
                }

                if (acquireResult != VK10.VK_SUCCESS && acquireResult != KHRSwapchain.VK_SUBOPTIMAL_KHR) {
                    throw new IllegalStateException(
                        "vkAcquireNextImageKHR failed with VkResult=" + acquireResult);
                }

                int imageIndex = pImageIndex.get(0);
                if (imageIndex < 0) {
                    throw new IllegalStateException("vkAcquireNextImageKHR returned invalid image index: " + imageIndex);
                }

                acquiredSwapchainImageIndex = imageIndex;
                frameInProgress = true;
                return imageIndex;
            }
        }

        private void endFrame() {
            if (!frameInProgress) {
                throw new IllegalStateException("endFrame called without an active Vulkan frame.");
            }
            if (commandBufferRecording) {
                throw new IllegalStateException("endFrame requires submitted command buffers; active recording command buffer detected.");
            }
            if (renderPassRecording) {
                throw new IllegalStateException("endFrame cannot run while a render pass is active.");
            }

            try (MemoryStack stack = stackPush()) {
                java.nio.LongBuffer pSwapchains = stack.longs(swapchain);
                java.nio.IntBuffer pImageIndices = stack.ints(acquiredSwapchainImageIndex);

                VkPresentInfoKHR presentInfo = VkPresentInfoKHR.calloc(stack)
                    .sType$Default()
                    .pSwapchains(pSwapchains)
                    .pImageIndices(pImageIndices);

                int presentResult = KHRSwapchain.vkQueuePresentKHR(graphicsQueue, presentInfo);
                if (presentResult == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR
                    || presentResult == KHRSwapchain.VK_SUBOPTIMAL_KHR) {
                    recreateSwapchain();
                } else {
                    checkVk("vkQueuePresentKHR", presentResult);
                }

                checkVk("vkQueueWaitIdle", VK10.vkQueueWaitIdle(graphicsQueue));
            } finally {
                acquiredSwapchainImageIndex = -1;
                frameInProgress = false;
            }
        }

        private long beginPrimaryCommandBuffer() {
            if (primaryCommandBuffer == null) {
                throw new IllegalStateException("Primary Vulkan command buffer has not been allocated.");
            }

            if (commandBufferRecording) {
                throw new IllegalStateException("Primary command buffer is already recording.");
            }

            try (MemoryStack stack = stackPush()) {
                checkVk("vkResetCommandPool", VK10.vkResetCommandPool(logicalDevice, commandPool, 0));

                VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType$Default()
                    .flags(VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);

                checkVk("vkBeginCommandBuffer", VK10.vkBeginCommandBuffer(primaryCommandBuffer, beginInfo));
                commandBufferRecording = true;
                renderPassRecording = false;
                return primaryCommandBuffer.address();
            }
        }

        private void submitPrimaryCommandBuffer(long commandBufferHandle) {
            ensureRecordingCommandBuffer(commandBufferHandle, "submitCommandBuffer");
            if (renderPassRecording) {
                throw new IllegalStateException("Cannot submit command buffer while a render pass is still active.");
            }

            try (MemoryStack stack = stackPush()) {
                checkVk("vkEndCommandBuffer", VK10.vkEndCommandBuffer(primaryCommandBuffer));

                VkSubmitInfo.Buffer submitInfos = VkSubmitInfo.calloc(1, stack)
                    .sType$Default();
                org.lwjgl.PointerBuffer commandBuffers = stack.mallocPointer(1);
                commandBuffers.put(0, primaryCommandBuffer.address());
                submitInfos.pCommandBuffers(commandBuffers);

                checkVk("vkQueueSubmit",
                    VK10.vkQueueSubmit(graphicsQueue, submitInfos, VK10.VK_NULL_HANDLE));
                checkVk("vkQueueWaitIdle", VK10.vkQueueWaitIdle(graphicsQueue));
                destroyTransientRenderPassResources();
                commandBufferRecording = false;
            }
        }

        private void applyResourceBarriers(long commandBufferHandle, VulkanicResourceBarriers barriers) {
            ensureRecordingCommandBuffer(commandBufferHandle, "applyResourceBarriers");

            BarrierMasks masks = toVkBarrierMasks(barriers);
            try (MemoryStack stack = stackPush()) {
                VkMemoryBarrier.Buffer memoryBarriers = VkMemoryBarrier.calloc(1, stack);
                memoryBarriers.get(0)
                    .sType$Default()
                    .srcAccessMask(masks.srcAccessMask())
                    .dstAccessMask(masks.dstAccessMask());

                VK10.vkCmdPipelineBarrier(
                    primaryCommandBuffer,
                    masks.srcStageMask(),
                    masks.dstStageMask(),
                    0,
                    memoryBarriers,
                    null,
                    null
                );
            }
        }

        private void ensureRecordingCommandBuffer(long commandBufferHandle, String operation) {
            if (primaryCommandBuffer == null) {
                throw new IllegalStateException("Primary Vulkan command buffer has not been allocated.");
            }
            if (commandBufferHandle != primaryCommandBuffer.address()) {
                throw new IllegalArgumentException(
                    operation + " received unknown VkCommandBuffer handle. Expected 0x"
                        + Long.toHexString(primaryCommandBuffer.address())
                        + " but got 0x" + Long.toHexString(commandBufferHandle));
            }
            if (!commandBufferRecording) {
                throw new IllegalStateException(operation + " requires an active recording command buffer.");
            }
        }

        private void beginRenderPass(long commandBufferHandle,
                                     VulkanicRenderPassDescriptor descriptor,
                                     ResolvedRenderTargets targets) {
            ensureRecordingCommandBuffer(commandBufferHandle, "beginRenderPass");
            if (renderPassRecording) {
                throw new IllegalStateException("Nested Vulkan render passes are not supported yet.");
            }

            VulkanTextureView colorView = targets.colorView;
            VulkanTexture colorTexture = targets.colorTexture;
            VulkanTextureView depthView = targets.depthView;
            VulkanTexture depthTexture = targets.depthTexture;
            int width = targets.width;
            int height = targets.height;
            VulkanicRenderPassDescriptor.DepthAttachment depthAttachment = descriptor.depthAttachment();

            long renderPassHandle = VK10.VK_NULL_HANDLE;
            long framebufferHandle = VK10.VK_NULL_HANDLE;
            try (MemoryStack stack = stackPush()) {
                int attachmentCount = targets.hasDepthTarget() ? 2 : 1;
                VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(attachmentCount, stack);

                attachments.get(0)
                    .format(toVkFormat(colorTexture.getVulkanicFormat()))
                    .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(toVkLoadOp(descriptor.colorAttachment().loadOp()))
                    .storeOp(toVkStoreOp(descriptor.colorAttachment().storeOp()))
                    .stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                    .finalLayout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

                VkAttachmentReference.Buffer colorReference = VkAttachmentReference.calloc(1, stack);
                colorReference.get(0)
                    .attachment(0)
                    .layout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

                VkAttachmentReference depthReference = null;
                if (targets.hasDepthTarget()) {
                    attachments.get(1)
                        .format(toVkFormat(depthTexture.getVulkanicFormat()))
                        .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                        .loadOp(toVkLoadOp(depthAttachment.loadOp()))
                        .storeOp(toVkStoreOp(depthAttachment.storeOp()))
                        .stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                        .stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                        .initialLayout(VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
                        .finalLayout(VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

                    depthReference = VkAttachmentReference.calloc(stack)
                        .attachment(1)
                        .layout(VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
                }

                VkSubpassDescription.Buffer subpasses = VkSubpassDescription.calloc(1, stack);
                subpasses.get(0)
                    .pipelineBindPoint(VK10.VK_PIPELINE_BIND_POINT_GRAPHICS)
                    .colorAttachmentCount(1)
                    .pColorAttachments(colorReference)
                    .pDepthStencilAttachment(depthReference);

                VkSubpassDependency.Buffer dependencies = VkSubpassDependency.calloc(1, stack);
                dependencies.get(0)
                    .srcSubpass(VK10.VK_SUBPASS_EXTERNAL)
                    .dstSubpass(0)
                    .srcStageMask(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
                        | VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT)
                    .dstStageMask(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
                        | VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT)
                    .srcAccessMask(0)
                    .dstAccessMask(VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                        | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);

                VkRenderPassCreateInfo renderPassCreateInfo = VkRenderPassCreateInfo.calloc(stack)
                    .sType$Default()
                    .pAttachments(attachments)
                    .pSubpasses(subpasses)
                    .pDependencies(dependencies);

                java.nio.LongBuffer pRenderPass = stack.mallocLong(1);
                checkVk("vkCreateRenderPass",
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

                VK10.vkCmdBeginRenderPass(primaryCommandBuffer, beginInfo, VK10.VK_SUBPASS_CONTENTS_INLINE);

                renderPassRecording = true;
                transientRenderPassHandles.add(renderPassHandle);
                transientFramebufferHandles.add(framebufferHandle);
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

        private void endRenderPass(long commandBufferHandle) {
            ensureRecordingCommandBuffer(commandBufferHandle, "endRenderPass");
            if (!renderPassRecording) {
                throw new IllegalStateException("No active Vulkan render pass to end");
            }

            VK10.vkCmdEndRenderPass(primaryCommandBuffer);
            renderPassRecording = false;
        }

        private void bindVertexBuffer(long commandBufferHandle, int slot, long bufferHandle) {
            ensureRecordingCommandBuffer(commandBufferHandle, "bindVertexBuffer");
            if (!renderPassRecording) {
                throw new IllegalStateException("bindVertexBuffer requires an active render pass");
            }
            if (slot < 0) {
                throw new IllegalArgumentException("slot must be >= 0, got: " + slot);
            }

            try (MemoryStack stack = stackPush()) {
                java.nio.LongBuffer buffers = stack.longs(bufferHandle);
                java.nio.LongBuffer offsets = stack.longs(0L);
                VK10.vkCmdBindVertexBuffers(primaryCommandBuffer, slot, buffers, offsets);
            }
        }

        private void bindIndexBuffer(long commandBufferHandle, long bufferHandle, VulkanicIndexType indexType) {
            ensureRecordingCommandBuffer(commandBufferHandle, "bindIndexBuffer");
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

            VK10.vkCmdBindIndexBuffer(primaryCommandBuffer, bufferHandle, 0L, vkIndexType);
        }

        private void drawIndexed(long commandBufferHandle, int firstIndex, int indexCount, int baseVertex, int instanceCount) {
            ensureRecordingCommandBuffer(commandBufferHandle, "drawIndexed");
            if (!renderPassRecording) {
                throw new IllegalStateException("drawIndexed requires an active render pass");
            }
            if (firstIndex < 0 || indexCount < 0 || instanceCount < 1) {
                throw new IllegalArgumentException("Invalid indexed draw arguments");
            }

            VK10.vkCmdDrawIndexed(primaryCommandBuffer, indexCount, instanceCount, firstIndex, baseVertex, 0);
        }

        private void draw(long commandBufferHandle, int firstVertex, int vertexCount) {
            ensureRecordingCommandBuffer(commandBufferHandle, "draw");
            if (!renderPassRecording) {
                throw new IllegalStateException("draw requires an active render pass");
            }
            if (firstVertex < 0 || vertexCount < 0) {
                throw new IllegalArgumentException("Invalid draw arguments");
            }

            VK10.vkCmdDraw(primaryCommandBuffer, vertexCount, 1, firstVertex, 0);
        }

        private void drawInstanced(long commandBufferHandle, int firstVertex, int vertexCount, int instanceCount) {
            ensureRecordingCommandBuffer(commandBufferHandle, "drawInstanced");
            if (!renderPassRecording) {
                throw new IllegalStateException("drawInstanced requires an active render pass");
            }
            if (firstVertex < 0 || vertexCount < 0 || instanceCount < 1) {
                throw new IllegalArgumentException("Invalid instanced draw arguments");
            }

            VK10.vkCmdDraw(primaryCommandBuffer, vertexCount, instanceCount, firstVertex, 0);
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

        private void destroyTransientRenderPassResources() {
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
            Objects.requireNonNull(descriptor, "descriptor must not be null");
            if (logicalDevice == null) {
                throw new IllegalStateException("Cannot create pipeline: Vulkan logical device is unavailable.");
            }

            PipelineDescriptor.PortableState portableState = descriptor.getPortableState();

            try (MemoryStack stack = stackPush()) {

                // --- 1. VkDescriptorSetLayout ---
                java.util.List<PipelineDescriptor.ResourceBinding> bindings =
                    descriptor.getResourceLayout().bindings();

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
                        dslBindings.get(i)
                            .binding(b.binding())
                            .descriptorType(toVkDescriptorType(b.type()))
                            .descriptorCount(1)
                            .stageFlags(VK10.VK_SHADER_STAGE_VERTEX_BIT
                                | VK10.VK_SHADER_STAGE_FRAGMENT_BIT);
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
                VkPipelineShaderStageCreateInfo.Buffer shaderStages =
                    VkPipelineShaderStageCreateInfo.calloc(2, stack);
                shaderStages.get(0)
                    .sType$Default()
                    .stage(VK10.VK_SHADER_STAGE_VERTEX_BIT)
                    .module(vertShaderModuleHandle)
                    .pName(mainEntry);
                shaderStages.get(1)
                    .sType$Default()
                    .stage(VK10.VK_SHADER_STAGE_FRAGMENT_BIT)
                    .module(fragShaderModuleHandle)
                    .pName(mainEntry);

                // --- 4. Vertex input state ---
                VertexFormat vertexFormat = portableState.vertexFormat();
                java.util.List<VertexFormatElement> vfElements = vertexFormat.getElements();

                VkVertexInputBindingDescription.Buffer vBindings =
                    VkVertexInputBindingDescription.calloc(1, stack);
                vBindings.get(0)
                    .binding(0)
                    .stride(vertexFormat.getVertexSize())
                    .inputRate(VK10.VK_VERTEX_INPUT_RATE_VERTEX);

                VkVertexInputAttributeDescription.Buffer vAttribs =
                    VkVertexInputAttributeDescription.calloc(vfElements.size(), stack);
                for (int i = 0; i < vfElements.size(); i++) {
                    VertexFormatElement elem = vfElements.get(i);
                    vAttribs.get(i)
                        .location(elem.id())
                        .binding(0)
                        .format(toVkVertexElementFormat(elem.type(), elem.count()))
                        .offset(vertexFormat.getOffset(elem));
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

                // --- 7. Rasterization ---
                boolean hasBias = portableState.depthBiasConstant() != 0.0f
                    || portableState.depthBiasScaleFactor() != 0.0f;
                VkPipelineRasterizationStateCreateInfo rasterizer =
                    VkPipelineRasterizationStateCreateInfo.calloc(stack)
                        .sType$Default()
                        .depthClampEnable(false)
                        .rasterizerDiscardEnable(false)
                        .polygonMode(toVkPolygonMode(portableState.polygonMode()))
                        .lineWidth(1.0f)
                        .cullMode(portableState.cull()
                            ? VK10.VK_CULL_MODE_BACK_BIT
                            : VK10.VK_CULL_MODE_NONE)
                        .frontFace(VK10.VK_FRONT_FACE_COUNTER_CLOCKWISE)
                        .depthBiasEnable(hasBias)
                        .depthBiasConstantFactor(portableState.depthBiasConstant())
                        .depthBiasSlopeFactor(portableState.depthBiasScaleFactor())
                        .depthBiasClamp(0.0f);

                // --- 8. Multisample ---
                VkPipelineMultisampleStateCreateInfo multisampling =
                    VkPipelineMultisampleStateCreateInfo.calloc(stack)
                        .sType$Default()
                        .sampleShadingEnable(false)
                        .rasterizationSamples(VK10.VK_SAMPLE_COUNT_1_BIT);

                // --- 9. Depth/stencil ---
                boolean depthTestEnabled =
                    portableState.depthTestFunction() != DepthTestFunction.NO_DEPTH_TEST;
                VkPipelineDepthStencilStateCreateInfo depthStencil =
                    VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                        .sType$Default()
                        .depthTestEnable(depthTestEnabled)
                        .depthWriteEnable(portableState.writeDepth())
                        .depthCompareOp(toVkDepthCompareOp(portableState.depthTestFunction()))
                        .depthBoundsTestEnable(false)
                        .stencilTestEnable(false);

                // --- 10. Color blend ---
                int colorWriteMask = 0;
                if (portableState.writeColor()) {
                    colorWriteMask |= VK10.VK_COLOR_COMPONENT_R_BIT
                        | VK10.VK_COLOR_COMPONENT_G_BIT
                        | VK10.VK_COLOR_COMPONENT_B_BIT;
                }
                if (portableState.writeAlpha()) {
                    colorWriteMask |= VK10.VK_COLOR_COMPONENT_A_BIT;
                }

                java.util.Optional<PipelineDescriptor.BlendState> blendState =
                    portableState.blendState();
                VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachment =
                    VkPipelineColorBlendAttachmentState.calloc(1, stack);
                colorBlendAttachment.get(0)
                    .colorWriteMask(colorWriteMask)
                    .blendEnable(blendState.isPresent());
                if (blendState.isPresent()) {
                    PipelineDescriptor.BlendState blend = blendState.get();
                    colorBlendAttachment.get(0)
                        .srcColorBlendFactor(toVkBlendFactor(blend.sourceColor()))
                        .dstColorBlendFactor(toVkBlendFactor(blend.destColor()))
                        .colorBlendOp(VK10.VK_BLEND_OP_ADD)
                        .srcAlphaBlendFactor(toVkBlendFactor(blend.sourceAlpha()))
                        .dstAlphaBlendFactor(toVkBlendFactor(blend.destAlpha()))
                        .alphaBlendOp(VK10.VK_BLEND_OP_ADD);
                }

                boolean logicOpEnabled = portableState.colorLogic() != LogicOp.NONE;
                VkPipelineColorBlendStateCreateInfo colorBlending =
                    VkPipelineColorBlendStateCreateInfo.calloc(stack)
                        .sType$Default()
                        .logicOpEnable(logicOpEnabled)
                        .logicOp(toVkLogicOp(portableState.colorLogic()))
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
                long placeholderRenderPass = createPipelineCompatibleRenderPass(stack,
                    depthTestEnabled || portableState.writeDepth());

                // --- 13. VkGraphicsPipelineCreateInfo ---
                VkGraphicsPipelineCreateInfo.Buffer pipelineInfo =
                    VkGraphicsPipelineCreateInfo.calloc(1, stack);
                pipelineInfo.get(0)
                    .sType$Default()
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
            ensureRecordingCommandBuffer(commandBufferHandle, "bindPipeline");
            if (!renderPassRecording) {
                throw new IllegalStateException("bindPipeline requires an active render pass");
            }
            VK10.vkCmdBindPipeline(
                primaryCommandBuffer,
                VK10.VK_PIPELINE_BIND_POINT_GRAPHICS,
                pipelineHandle);
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
            int colorFormat = swapchainImageFormat != VK10.VK_FORMAT_UNDEFINED
                ? swapchainImageFormat
                : VK10.VK_FORMAT_B8G8R8A8_UNORM;   // safe fallback
            int attachmentCount = includeDepth ? 2 : 1;

            VkAttachmentDescription.Buffer attachments =
                VkAttachmentDescription.calloc(attachmentCount, stack);
            attachments.get(0)
                .format(colorFormat)
                .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                .loadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                .storeOp(VK10.VK_ATTACHMENT_STORE_OP_STORE)
                .stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                .stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .initialLayout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                .finalLayout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

            VkAttachmentReference.Buffer colorRef = VkAttachmentReference.calloc(1, stack);
            colorRef.get(0)
                .attachment(0)
                .layout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

            VkAttachmentReference depthRef = null;
            if (includeDepth) {
                attachments.get(1)
                    .format(VK10.VK_FORMAT_D32_SFLOAT)
                    .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .storeOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
                    .finalLayout(VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
                depthRef = VkAttachmentReference.calloc(stack)
                    .attachment(1)
                    .layout(VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
            }

            VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack);
            subpass.get(0)
                .pipelineBindPoint(VK10.VK_PIPELINE_BIND_POINT_GRAPHICS)
                .colorAttachmentCount(1)
                .pColorAttachments(colorRef)
                .pDepthStencilAttachment(depthRef);

            VkRenderPassCreateInfo rpInfo = VkRenderPassCreateInfo.calloc(stack)
                .sType$Default()
                .pAttachments(attachments)
                .pSubpasses(subpass);

            java.nio.LongBuffer pRp = stack.mallocLong(1);
            checkVk("vkCreateRenderPass (pipeline-compatible)",
                VK10.vkCreateRenderPass(logicalDevice, rpInfo, null, pRp));
            return pRp.get(0);
        }

        // =====================================================================
        // Pipeline state mapping helpers
        // =====================================================================

        private static int toVkDescriptorType(PipelineDescriptor.ResourceType type) {
            return switch (type) {
                case SAMPLER -> VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
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
         * <p>UBYTE components are mapped to UNORM (normalized [0..1]) since they are typically
         * used for color data.  SHORT components used for UV coordinates are mapped to SSCALED
         * (denormalized signed integer scaled to float) since Minecraft's UV coordinates span
         * full short ranges and are not pre-normalized to [0..1].</p>
         */
        private static int toVkVertexElementFormat(VertexFormatElement.Type type, int count) {
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
                    case 1 -> VK10.VK_FORMAT_R8_UNORM;
                    case 2 -> VK10.VK_FORMAT_R8G8_UNORM;
                    case 3 -> VK10.VK_FORMAT_R8G8B8_UNORM;
                    case 4 -> VK10.VK_FORMAT_R8G8B8A8_UNORM;
                    default -> throw new IllegalArgumentException(
                        "Unsupported UBYTE vertex component count: " + count);
                };
                case BYTE -> switch (count) {
                    case 1 -> VK10.VK_FORMAT_R8_SNORM;
                    case 2 -> VK10.VK_FORMAT_R8G8_SNORM;
                    case 3 -> VK10.VK_FORMAT_R8G8B8_SNORM;
                    case 4 -> VK10.VK_FORMAT_R8G8B8A8_SNORM;
                    default -> throw new IllegalArgumentException(
                        "Unsupported BYTE vertex component count: " + count);
                };
                case SHORT -> switch (count) {
                    case 1 -> VK10.VK_FORMAT_R16_SSCALED;
                    case 2 -> VK10.VK_FORMAT_R16G16_SSCALED;
                    case 3 -> VK10.VK_FORMAT_R16G16B16_SSCALED;
                    case 4 -> VK10.VK_FORMAT_R16G16B16A16_SSCALED;
                    default -> throw new IllegalArgumentException(
                        "Unsupported SHORT vertex component count: " + count);
                };
                case USHORT -> switch (count) {
                    case 1 -> VK10.VK_FORMAT_R16_USCALED;
                    case 2 -> VK10.VK_FORMAT_R16G16_USCALED;
                    case 3 -> VK10.VK_FORMAT_R16G16B16_USCALED;
                    case 4 -> VK10.VK_FORMAT_R16G16B16A16_USCALED;
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

        private static int toVkPolygonMode(PolygonMode mode) {
            return switch (mode) {
                case FILL -> VK10.VK_POLYGON_MODE_FILL;
                case WIREFRAME -> VK10.VK_POLYGON_MODE_LINE;
            };
        }

        private static int toVkDepthCompareOp(DepthTestFunction func) {
            return switch (func) {
                case NO_DEPTH_TEST -> VK10.VK_COMPARE_OP_ALWAYS;
                case EQUAL_DEPTH_TEST -> VK10.VK_COMPARE_OP_EQUAL;
                case LEQUAL_DEPTH_TEST -> VK10.VK_COMPARE_OP_LESS_OR_EQUAL;
                case LESS_DEPTH_TEST -> VK10.VK_COMPARE_OP_LESS;
                case GREATER_DEPTH_TEST -> VK10.VK_COMPARE_OP_GREATER;
            };
        }

        private static int toVkBlendFactor(SourceFactor factor) {
            return switch (factor) {
                case ZERO -> VK10.VK_BLEND_FACTOR_ZERO;
                case ONE -> VK10.VK_BLEND_FACTOR_ONE;
                case SRC_COLOR -> VK10.VK_BLEND_FACTOR_SRC_COLOR;
                case ONE_MINUS_SRC_COLOR -> VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR;
                case DST_COLOR -> VK10.VK_BLEND_FACTOR_DST_COLOR;
                case ONE_MINUS_DST_COLOR -> VK10.VK_BLEND_FACTOR_ONE_MINUS_DST_COLOR;
                case SRC_ALPHA -> VK10.VK_BLEND_FACTOR_SRC_ALPHA;
                case ONE_MINUS_SRC_ALPHA -> VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
                case DST_ALPHA -> VK10.VK_BLEND_FACTOR_DST_ALPHA;
                case ONE_MINUS_DST_ALPHA -> VK10.VK_BLEND_FACTOR_ONE_MINUS_DST_ALPHA;
                case CONSTANT_COLOR -> VK10.VK_BLEND_FACTOR_CONSTANT_COLOR;
                case ONE_MINUS_CONSTANT_COLOR -> VK10.VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_COLOR;
                case CONSTANT_ALPHA -> VK10.VK_BLEND_FACTOR_CONSTANT_ALPHA;
                case ONE_MINUS_CONSTANT_ALPHA -> VK10.VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_ALPHA;
                case SRC_ALPHA_SATURATE -> VK10.VK_BLEND_FACTOR_SRC_ALPHA_SATURATE;
            };
        }

        private static int toVkBlendFactor(DestFactor factor) {
            return switch (factor) {
                case ZERO -> VK10.VK_BLEND_FACTOR_ZERO;
                case ONE -> VK10.VK_BLEND_FACTOR_ONE;
                case SRC_COLOR -> VK10.VK_BLEND_FACTOR_SRC_COLOR;
                case ONE_MINUS_SRC_COLOR -> VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR;
                case DST_COLOR -> VK10.VK_BLEND_FACTOR_DST_COLOR;
                case ONE_MINUS_DST_COLOR -> VK10.VK_BLEND_FACTOR_ONE_MINUS_DST_COLOR;
                case SRC_ALPHA -> VK10.VK_BLEND_FACTOR_SRC_ALPHA;
                case ONE_MINUS_SRC_ALPHA -> VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
                case DST_ALPHA -> VK10.VK_BLEND_FACTOR_DST_ALPHA;
                case ONE_MINUS_DST_ALPHA -> VK10.VK_BLEND_FACTOR_ONE_MINUS_DST_ALPHA;
                case CONSTANT_COLOR -> VK10.VK_BLEND_FACTOR_CONSTANT_COLOR;
                case ONE_MINUS_CONSTANT_COLOR -> VK10.VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_COLOR;
                case CONSTANT_ALPHA -> VK10.VK_BLEND_FACTOR_CONSTANT_ALPHA;
                case ONE_MINUS_CONSTANT_ALPHA -> VK10.VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_ALPHA;
            };
        }

        private static int toVkLogicOp(LogicOp op) {
            return switch (op) {
                case NONE -> VK10.VK_LOGIC_OP_NO_OP;   // logicOpEnable=false when NONE; safe default
                case OR_REVERSE -> VK10.VK_LOGIC_OP_OR_REVERSE;
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

        private void close() {
            if (logicalDevice != null) {
                try {
                    VK10.vkDeviceWaitIdle(logicalDevice);
                } catch (Throwable ignored) {
                }

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
                legacyTexture2DBindingsByUnit.clear();
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

                if (swapchain != VK10.VK_NULL_HANDLE) {
                    KHRSwapchain.vkDestroySwapchainKHR(logicalDevice, swapchain, null);
                    swapchain = VK10.VK_NULL_HANDLE;
                }

                if (commandPool != VK10.VK_NULL_HANDLE) {
                    VK10.vkDestroyCommandPool(logicalDevice, commandPool, null);
                    commandPool = VK10.VK_NULL_HANDLE;
                }

                VK10.vkDestroyDevice(logicalDevice, null);
                logicalDevice = null;
                graphicsQueue = null;
                primaryCommandBuffer = null;
                commandBufferRecording = false;
                frameInProgress = false;
                acquiredSwapchainImageIndex = -1;
            }

            if (instance != null) {
                if (surface != VK10.VK_NULL_HANDLE) {
                    KHRSurface.vkDestroySurfaceKHR(instance, surface, null);
                    surface = VK10.VK_NULL_HANDLE;
                }

                VK10.vkDestroyInstance(instance, null);
                instance = null;
                physicalDevice = null;
            }
        }

        private static void checkVk(String operation, int result) {
            if (result != VK10.VK_SUCCESS) {
                throw new IllegalStateException(operation + " failed with VkResult=" + result);
            }
        }
    }
}


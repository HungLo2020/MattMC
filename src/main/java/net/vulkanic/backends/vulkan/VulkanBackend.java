package net.vulkanic.backends.vulkan;

import net.blaze3d.GpuOutOfMemoryException;
import net.vulkanic.CommandContext;
import net.vulkanic.GraphicsBackendType;
import net.vulkanic.PipelineDescriptor;
import net.vulkanic.PipelineHandle;
import net.vulkanic.VulkanReadinessReport;
import net.vulkanic.VulkanicBuffer;
import net.vulkanic.VulkanicTexture;
import net.vulkanic.VulkanicTextureFormat;
import net.vulkanic.VulkanicTextureView;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicShaderStage;
import net.vulkanic.VulkanicSpirvModule;
import net.vulkanic.VulkanExecutionContextInfo;
import net.vulkanic.VulkanNativeInitializationInfo;
import net.vulkanic.VulkanSwapchainSurfaceInfo;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.KHRSurface;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkExtent2D;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkQueueFamilyProperties;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.lwjgl.system.MemoryStack.stackPush;

public class VulkanBackend {

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
                glfwRequiredExtensionsPresent = requiredExtensions != null && requiredExtensions.remaining() > 0;
                glfwExtensionsStatus = glfwRequiredExtensionsPresent
                    ? "available (count=" + requiredExtensions.remaining() + ")"
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
            try {
                nativeSpine = NativeSpine.create();
                nativeBringUpFailure = null;
            } catch (Throwable throwable) {
                nativeBringUpFailure = compactThrowable(throwable);
                nativeSpine = null;
            } finally {
                cachedReadinessReport = null;
            }
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
        } catch (RuntimeException exception) {
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
        virtualShaders.remove(shader);
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

    public PipelineHandle createPipeline(PipelineDescriptor descriptor) {
        ensureNativeReady("createPipeline");
        throw new UnsupportedOperationException("Vulkan-native pipeline creation is not implemented yet.");
    }

    public net.vulkanic.DescriptorPoolHandle createDescriptorPool(
            net.vulkanic.DescriptorPoolDescriptor descriptor) {
        ensureNativeReady("createDescriptorPool");
        throw new UnsupportedOperationException("Vulkan-native descriptor pool lifecycle is not implemented yet.");
    }

    public net.vulkanic.DescriptorSetHandle allocateDescriptorSet(
            net.vulkanic.DescriptorPoolHandle pool,
            PipelineDescriptor descriptor) {
        ensureNativeReady("allocateDescriptorSet");
        throw new UnsupportedOperationException("Vulkan-native descriptor set allocation is not implemented yet.");
    }

    public void updateDescriptorSet(net.vulkanic.DescriptorSetHandle descriptorSet,
            net.vulkanic.PipelineResourceBindings bindings) {
        ensureNativeReady("updateDescriptorSet");
        throw new UnsupportedOperationException("Vulkan-native descriptor set updates are not implemented yet.");
    }

    public void bindDescriptorSet(CommandContext ctx,
            PipelineHandle pipeline,
            PipelineDescriptor descriptor,
            net.vulkanic.DescriptorSetHandle descriptorSet) {
        ensureNativeReady("bindDescriptorSet");
        throw new UnsupportedOperationException("Vulkan-native descriptor set binding is not implemented yet.");
    }

    public void resetDescriptorPool(net.vulkanic.DescriptorPoolHandle pool) {
        ensureNativeReady("resetDescriptorPool");
        throw new UnsupportedOperationException("Vulkan-native descriptor pool reset is not implemented yet.");
    }

    public void bindPipelineResources(CommandContext ctx,
            PipelineHandle pipeline,
            PipelineDescriptor descriptor,
            net.vulkanic.PipelineResourceBindings bindings) {
        ensureNativeReady("bindPipelineResources");
        throw new UnsupportedOperationException("Vulkan-native descriptor set updates are not implemented yet.");
    }

    public void applyResourceBarriers(CommandContext ctx,
            net.vulkanic.VulkanicResourceBarriers barriers) {
        ensureNativeReady("applyResourceBarriers");
        throw new UnsupportedOperationException("Vulkan-native resource barrier mapping is not implemented yet.");
    }

    public CommandContext beginCommandBuffer() {
        ensureNativeReady("beginCommandBuffer");
        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }

        spine.recreateSwapchainIfFramebufferSizeChanged();

        long commandBufferHandle = spine.beginPrimaryCommandBuffer();
        CommandContext context = new VulkanCommandContext(commandBufferHandle, "Vulkan-PrimaryCommandBuffer");
        currentCommandContext = context;
        return context;
    }

    public void submitCommandBuffer(CommandContext ctx) {
        ensureNativeReady("submitCommandBuffer");

        if (!(ctx instanceof VulkanCommandContext)) {
            throw new IllegalArgumentException(
                "submitCommandBuffer requires VulkanCommandContext when Vulkan backend is selected; got: "
                    + (ctx == null ? "null" : ctx.getClass().getName()));
        }

        NativeSpine spine = nativeSpine;
        if (spine == null) {
            throw new IllegalStateException("Native Vulkan spine is unavailable after readiness check.");
        }

        spine.submitPrimaryCommandBuffer(ctx.getHandle());
        currentCommandContext = null;
    }

    public net.vulkanic.VulkanicRenderPass beginRenderPass(CommandContext ctx,
            java.util.function.Supplier<String> label,
            net.vulkanic.VulkanicTextureView colorTarget, java.util.OptionalInt clearColor) {
        ensureNativeReady("beginRenderPass");
        throw new UnsupportedOperationException("Vulkan-native render pass lifecycle is not implemented yet.");
    }

    public net.vulkanic.VulkanicRenderPass beginRenderPass(CommandContext ctx,
            java.util.function.Supplier<String> label,
            net.vulkanic.VulkanicTextureView colorTarget, java.util.OptionalInt clearColor,
            @org.jetbrains.annotations.Nullable net.vulkanic.VulkanicTextureView depthTarget,
            java.util.OptionalDouble clearDepth) {
        ensureNativeReady("beginRenderPass");
        throw new UnsupportedOperationException("Vulkan-native render pass lifecycle is not implemented yet.");
    }

    public net.vulkanic.VulkanicRenderPass beginRenderPass(CommandContext ctx,
            net.vulkanic.VulkanicRenderPassDescriptor descriptor) {
        ensureNativeReady("beginRenderPass");
        throw new UnsupportedOperationException("Vulkan-native render pass lifecycle is not implemented yet.");
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
                return primaryCommandBuffer.address();
            }
        }

        private void submitPrimaryCommandBuffer(long commandBufferHandle) {
            if (primaryCommandBuffer == null) {
                throw new IllegalStateException("Primary Vulkan command buffer has not been allocated.");
            }

            if (commandBufferHandle != primaryCommandBuffer.address()) {
                throw new IllegalArgumentException(
                    "submitCommandBuffer received unknown VkCommandBuffer handle. Expected 0x"
                        + Long.toHexString(primaryCommandBuffer.address())
                        + " but got 0x" + Long.toHexString(commandBufferHandle));
            }

            if (!commandBufferRecording) {
                throw new IllegalStateException("Primary command buffer is not in recording state.");
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
                commandBufferRecording = false;
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

                if (!managedExtraImageViews.isEmpty()) {
                    new ArrayList<>(managedExtraImageViews).forEach(this::destroyManagedImageView);
                }

                if (!managedImageAllocations.isEmpty()) {
                    new ArrayList<>(managedImageAllocations.entrySet()).forEach(entry -> {
                        Long defView = managedImageDefaultViews.get(entry.getKey());
                        destroyManagedTexture(entry.getKey(), entry.getValue(),
                            defView != null ? defView : VK10.VK_NULL_HANDLE);
                    });
                }

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

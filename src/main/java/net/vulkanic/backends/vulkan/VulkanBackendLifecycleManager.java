package net.vulkanic.backends.vulkan;

import net.logging.LogUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.EXTAttachmentFeedbackLoopLayout;
import org.lwjgl.vulkan.KHRGetPhysicalDeviceProperties2;
import org.lwjgl.vulkan.KHRPresentId;
import org.lwjgl.vulkan.KHRPresentWait;
import org.lwjgl.vulkan.KHRSurface;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK11;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkExtent2D;
import org.lwjgl.vulkan.VkExtensionProperties;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceAttachmentFeedbackLoopLayoutFeaturesEXT;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures;
import org.lwjgl.vulkan.VkPhysicalDevicePresentIdFeaturesKHR;
import org.lwjgl.vulkan.VkPhysicalDevicePresentWaitFeaturesKHR;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkQueueFamilyProperties;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR;
import org.slf4j.Logger;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * Owns Vulkan backend lifecycle planning and native resources created during startup
 * and swapchain recreation. Active command recording, queue submission, and present
 * calls remain in {@link VulkanBackend.NativeSpine}.
 */
final class VulkanBackendLifecycleManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final boolean FORCE_FIFO_PRESENT_MODE = true;

    private State state = State.NEW;
    private VkInstance instance;
    private VkPhysicalDevice physicalDevice;
    private VkDevice logicalDevice;
    private VkQueue graphicsQueue;
    private VkQueue presentQueue;
    private long surface;
    private long swapchain;
    private long windowHandle;
    private long lifecycleGeneration;
    private int swapchainImageFormat = VK10.VK_FORMAT_UNDEFINED;
    private int swapchainColorSpace = -1;
    private int swapchainPresentMode = -1;
    private int swapchainWidth;
    private int swapchainHeight;
    private List<Long> swapchainImageHandles = List.of();
    private List<Long> swapchainImageViewHandles = List.of();
    private RuntimeSnapshot runtimeSnapshot = RuntimeSnapshot.empty();
    private CommandRuntimeSnapshot commandRuntimeSnapshot = CommandRuntimeSnapshot.empty();

    enum State {
        NEW,
        INSTANCE_CREATED,
        SURFACE_CREATED,
        DEVICE_SELECTED,
        DEVICE_CREATED,
        SWAPCHAIN_READY,
        DEVICE_LOST,
        SHUTDOWN
    }

    @FunctionalInterface
    interface VkResultChecker {
        void check(String operation, int result);
    }

    record WindowSelection(
        long registeredWindowHandle,
        long currentContextWindowHandle,
        long selectedWindowHandle,
        boolean registeredWindowPreferred
    ) {
    }

    record DeviceCapabilitySnapshot(
        int vendorId,
        int apiVersion,
        String deviceName,
        boolean fillModeNonSolidSupported,
        boolean vertexPipelineStoresAndAtomicsSupported,
        boolean fragmentStoresAndAtomicsSupported,
        boolean shaderStorageImageReadWithoutFormatSupported,
        boolean shaderStorageImageWriteWithoutFormatSupported,
        long minUniformBufferOffsetAlignment,
        int maxImageDimension2D,
        int maxTextureImageUnits
    ) {
    }

    record QueueFamilyPlan(int graphicsFamilyIndex, int graphicsQueueCount, int graphicsQueueIndex, int presentQueueIndex) {
        QueueFamilyPlan {
            if (graphicsFamilyIndex < 0) {
                throw new IllegalArgumentException("graphicsFamilyIndex must be non-negative.");
            }
            if (graphicsQueueCount <= 0) {
                throw new IllegalArgumentException("graphicsQueueCount must be positive.");
            }
            if (graphicsQueueIndex < 0 || graphicsQueueIndex >= graphicsQueueCount) {
                throw new IllegalArgumentException("graphicsQueueIndex is outside the available queue count.");
            }
            if (presentQueueIndex < 0 || presentQueueIndex >= graphicsQueueCount) {
                throw new IllegalArgumentException("presentQueueIndex is outside the available queue count.");
            }
        }

        int requestedQueueCount() {
            return Math.min(2, Math.max(1, graphicsQueueCount));
        }

        boolean usesSeparatePresentQueueHandle() {
            return presentQueueIndex != graphicsQueueIndex;
        }
    }

    record PhysicalDeviceSelection(
        VkPhysicalDevice physicalDevice,
        QueueFamilyPlan queueFamilyPlan,
        DeviceCapabilitySnapshot capabilities
    ) {
        PhysicalDeviceSelection {
            Objects.requireNonNull(physicalDevice, "physicalDevice must not be null");
            Objects.requireNonNull(queueFamilyPlan, "queueFamilyPlan must not be null");
            Objects.requireNonNull(capabilities, "capabilities must not be null");
        }
    }

    record DeviceExtensionPlan(
        boolean presentId,
        boolean presentWait,
        boolean attachmentFeedbackLoopLayout
    ) {
    }

    record LogicalDeviceResult(
        VkDevice logicalDevice,
        VkQueue graphicsQueue,
        VkQueue presentQueue,
        boolean fillModeNonSolidEnabled,
        boolean vertexPipelineStoresAndAtomicsEnabled,
        boolean fragmentStoresAndAtomicsEnabled,
        boolean shaderStorageImageReadWithoutFormatEnabled,
        boolean shaderStorageImageWriteWithoutFormatEnabled,
        boolean presentIdExtensionEnabled,
        boolean presentWaitExtensionEnabled,
        boolean attachmentFeedbackLoopLayoutEnabled
    ) {
    }

    record SurfaceSupportSnapshot(
        int minImageCount,
        int maxImageCount,
        int currentWidth,
        int currentHeight,
        int minWidth,
        int minHeight,
        int maxWidth,
        int maxHeight,
        int supportedTransforms,
        int currentTransform,
        int supportedCompositeAlpha,
        int supportedUsageFlags
    ) {
    }

    record SwapchainCreationPlan(
        int imageFormat,
        int colorSpace,
        int presentMode,
        int width,
        int height,
        int minImageCount,
        int imageUsage,
        int imageSharingMode,
        int preTransform,
        int compositeAlpha,
        long oldSwapchainHandle,
        SurfaceSupportSnapshot surfaceSupport
    ) {
    }

    record SwapchainResourceSnapshot(
        long swapchainHandle,
        int imageFormat,
        int colorSpace,
        int presentMode,
        int width,
        int height,
        int imageUsage,
        List<Long> imageHandles,
        List<Long> imageViewHandles,
        long[] renderFinishedSemaphores
    ) {
        SwapchainResourceSnapshot {
            imageHandles = List.copyOf(imageHandles);
            imageViewHandles = List.copyOf(imageViewHandles);
            renderFinishedSemaphores = renderFinishedSemaphores.clone();
        }

        @Override
        public long[] renderFinishedSemaphores() {
            return renderFinishedSemaphores.clone();
        }
    }

    record CommandRuntimeSnapshot(
        long generation,
        State state,
        VkInstance instance,
        VkPhysicalDevice physicalDevice,
        VkDevice logicalDevice,
        VkQueue graphicsQueue,
        VkQueue presentQueue,
        long surface,
        long swapchain,
        long windowHandle,
        boolean instanceProperties2ExtensionEnabled,
        DeviceCapabilitySnapshot capabilities,
        QueueFamilyPlan queueFamilyPlan,
        boolean fillModeNonSolidEnabled,
        boolean vertexPipelineStoresAndAtomicsEnabled,
        boolean fragmentStoresAndAtomicsEnabled,
        boolean shaderStorageImageReadWithoutFormatEnabled,
        boolean shaderStorageImageWriteWithoutFormatEnabled,
        boolean presentIdExtensionEnabled,
        boolean presentWaitExtensionEnabled,
        boolean attachmentFeedbackLoopLayoutEnabled,
        int swapchainImageFormat,
        int swapchainColorSpace,
        int swapchainPresentMode,
        int swapchainWidth,
        int swapchainHeight,
        List<Long> swapchainImageHandles,
        List<Long> swapchainImageViewHandles
    ) {
        CommandRuntimeSnapshot {
            swapchainImageHandles = List.copyOf(swapchainImageHandles);
            swapchainImageViewHandles = List.copyOf(swapchainImageViewHandles);
        }

        private static CommandRuntimeSnapshot empty() {
            RuntimeSnapshot emptyRuntime = RuntimeSnapshot.empty();
            return new CommandRuntimeSnapshot(
                0L,
                State.NEW,
                null,
                null,
                null,
                null,
                null,
                VK10.VK_NULL_HANDLE,
                VK10.VK_NULL_HANDLE,
                VK10.VK_NULL_HANDLE,
                false,
                emptyRuntime.capabilities(),
                emptyRuntime.queueFamilyPlan(),
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                VK10.VK_FORMAT_UNDEFINED,
                -1,
                -1,
                0,
                0,
                List.of(),
                List.of()
            );
        }

        boolean commandExecutionAllowed() {
            return logicalDevice != null
                && state != State.NEW
                && state != State.DEVICE_LOST
                && state != State.SHUTDOWN;
        }

        boolean swapchainAvailable() {
            return commandExecutionAllowed()
                && swapchain != VK10.VK_NULL_HANDLE
                && swapchainWidth > 0
                && swapchainHeight > 0;
        }
    }

    record RuntimeSnapshot(
        State state,
        boolean instanceProperties2ExtensionEnabled,
        DeviceCapabilitySnapshot capabilities,
        QueueFamilyPlan queueFamilyPlan,
        boolean fillModeNonSolidEnabled,
        boolean vertexPipelineStoresAndAtomicsEnabled,
        boolean fragmentStoresAndAtomicsEnabled,
        boolean shaderStorageImageReadWithoutFormatEnabled,
        boolean shaderStorageImageWriteWithoutFormatEnabled,
        boolean presentIdExtensionEnabled,
        boolean presentWaitExtensionEnabled,
        boolean attachmentFeedbackLoopLayoutEnabled
    ) {
        private static RuntimeSnapshot empty() {
            return new RuntimeSnapshot(
                State.NEW,
                false,
                new DeviceCapabilitySnapshot(
                    0,
                    VK10.VK_API_VERSION_1_0,
                    "Vulkan GPU",
                    false,
                    false,
                    false,
                    false,
                    false,
                    1L,
                    16384,
                    32
                ),
                new QueueFamilyPlan(0, 1, 0, 0),
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false
            );
        }
    }

    State state() {
        return state;
    }

    VkInstance instance() {
        return instance;
    }

    VkPhysicalDevice physicalDevice() {
        return physicalDevice;
    }

    VkDevice logicalDevice() {
        return logicalDevice;
    }

    VkQueue graphicsQueue() {
        return graphicsQueue;
    }

    VkQueue presentQueue() {
        return presentQueue;
    }

    long surface() {
        return surface;
    }

    long swapchain() {
        return swapchain;
    }

    long windowHandle() {
        return windowHandle;
    }

    RuntimeSnapshot runtimeSnapshot() {
        return runtimeSnapshot;
    }

    CommandRuntimeSnapshot commandRuntimeSnapshot() {
        return commandRuntimeSnapshot;
    }

    CommandRuntimeSnapshot requireCommandRuntimeSnapshot(String operation) {
        CommandRuntimeSnapshot snapshot = commandRuntimeSnapshot;
        if (!snapshot.commandExecutionAllowed()) {
            throw new IllegalStateException(
                operation + " cannot execute because Vulkan lifecycle state is " + snapshot.state()
                    + " (generation=" + snapshot.generation() + ")."
            );
        }
        return snapshot;
    }

    void validateSnapshotCurrent(CommandRuntimeSnapshot snapshot, String operation) {
        if (snapshot == null || snapshot.generation() != commandRuntimeSnapshot.generation()) {
            throw new IllegalStateException(operation + " attempted to use a stale Vulkan lifecycle snapshot.");
        }
        if (snapshot.state() == State.DEVICE_LOST || snapshot.state() == State.SHUTDOWN) {
            throw new IllegalStateException(operation + " attempted to use an inactive Vulkan lifecycle snapshot: " + snapshot.state());
        }
    }

    private void publishSnapshot() {
        lifecycleGeneration++;
        commandRuntimeSnapshot = new CommandRuntimeSnapshot(
            lifecycleGeneration,
            state,
            instance,
            physicalDevice,
            logicalDevice,
            graphicsQueue,
            presentQueue,
            surface,
            swapchain,
            windowHandle,
            runtimeSnapshot.instanceProperties2ExtensionEnabled(),
            runtimeSnapshot.capabilities(),
            runtimeSnapshot.queueFamilyPlan(),
            runtimeSnapshot.fillModeNonSolidEnabled(),
            runtimeSnapshot.vertexPipelineStoresAndAtomicsEnabled(),
            runtimeSnapshot.fragmentStoresAndAtomicsEnabled(),
            runtimeSnapshot.shaderStorageImageReadWithoutFormatEnabled(),
            runtimeSnapshot.shaderStorageImageWriteWithoutFormatEnabled(),
            runtimeSnapshot.presentIdExtensionEnabled(),
            runtimeSnapshot.presentWaitExtensionEnabled(),
            runtimeSnapshot.attachmentFeedbackLoopLayoutEnabled(),
            swapchainImageFormat,
            swapchainColorSpace,
            swapchainPresentMode,
            swapchainWidth,
            swapchainHeight,
            swapchainImageHandles,
            swapchainImageViewHandles
        );
    }

    private void clearSwapchainSnapshotState() {
        swapchainImageFormat = VK10.VK_FORMAT_UNDEFINED;
        swapchainColorSpace = -1;
        swapchainPresentMode = -1;
        swapchainWidth = 0;
        swapchainHeight = 0;
        swapchainImageHandles = List.of();
        swapchainImageViewHandles = List.of();
    }

    WindowSelection selectWindow(long registeredWindowHandle, long currentContextWindowHandle) {
        long selectedWindowHandle = registeredWindowHandle != 0L ? registeredWindowHandle : currentContextWindowHandle;
        if (selectedWindowHandle == 0L) {
            throw new IllegalStateException(
                "No current or registered GLFW window handle. Vulkan native spine requires a valid GLFW window for surface/swapchain bring-up.");
        }
        return new WindowSelection(
            registeredWindowHandle,
            currentContextWindowHandle,
            selectedWindowHandle,
            registeredWindowHandle != 0L
        );
    }

    void recordWindowSelection(WindowSelection selection) {
        this.windowHandle = selection.selectedWindowHandle();
        publishSnapshot();
    }

    boolean createInstance(VkResultChecker checker) {
        try (MemoryStack stack = stackPush()) {
            PointerBuffer requiredExtensions = GLFWVulkan.glfwGetRequiredInstanceExtensions();
            if (requiredExtensions == null || requiredExtensions.remaining() == 0) {
                throw new IllegalStateException(
                    "GLFW did not provide Vulkan required instance extensions (null/empty result).");
            }

            Set<String> availableInstanceExtensions = enumerateInstanceExtensionNames(checker);
            boolean enableProperties2Extension = availableInstanceExtensions.contains(
                KHRGetPhysicalDeviceProperties2.VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME
            );

            boolean properties2AlreadyRequested = pointerBufferContains(
                requiredExtensions,
                KHRGetPhysicalDeviceProperties2.VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME
            );

            PointerBuffer enabledExtensions = requiredExtensions;
            if (enableProperties2Extension && !properties2AlreadyRequested) {
                enabledExtensions = stack.mallocPointer(requiredExtensions.remaining() + 1);
                for (int index = requiredExtensions.position(); index < requiredExtensions.limit(); index++) {
                    enabledExtensions.put(requiredExtensions.get(index));
                }
                enabledExtensions.put(stack.UTF8(KHRGetPhysicalDeviceProperties2.VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME));
                enabledExtensions.flip();
            }

            LOGGER.info("GLFW required Vulkan instance extensions: [{}]", describePointerBufferStrings(enabledExtensions));

            VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack)
                .sType$Default()
                .pApplicationName(stack.UTF8("Vulkanic"))
                .applicationVersion(VK10.VK_MAKE_API_VERSION(0, 0, 1, 0))
                .pEngineName(stack.UTF8("Vulkanic"))
                .engineVersion(VK10.VK_MAKE_API_VERSION(0, 0, 1, 0))
                .apiVersion(VK11.VK_API_VERSION_1_1);

            VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack)
                .sType$Default()
                .pApplicationInfo(appInfo)
                .ppEnabledExtensionNames(enabledExtensions);

            PointerBuffer pInstance = stack.mallocPointer(1);
            checker.check("vkCreateInstance", VK10.vkCreateInstance(createInfo, null, pInstance));
            instance = new VkInstance(pInstance.get(0), createInfo);
            runtimeSnapshot = new RuntimeSnapshot(
                State.INSTANCE_CREATED,
                enableProperties2Extension || properties2AlreadyRequested,
                runtimeSnapshot.capabilities(),
                runtimeSnapshot.queueFamilyPlan(),
                runtimeSnapshot.fillModeNonSolidEnabled(),
                runtimeSnapshot.vertexPipelineStoresAndAtomicsEnabled(),
                runtimeSnapshot.fragmentStoresAndAtomicsEnabled(),
                runtimeSnapshot.shaderStorageImageReadWithoutFormatEnabled(),
                runtimeSnapshot.shaderStorageImageWriteWithoutFormatEnabled(),
                runtimeSnapshot.presentIdExtensionEnabled(),
                runtimeSnapshot.presentWaitExtensionEnabled(),
                runtimeSnapshot.attachmentFeedbackLoopLayoutEnabled()
            );
            state = State.INSTANCE_CREATED;
            publishSnapshot();
            return runtimeSnapshot.instanceProperties2ExtensionEnabled();
        }
    }

    long createSurface(VkResultChecker checker) {
        requireInstance();
        if (windowHandle == VK10.VK_NULL_HANDLE) {
            throw new IllegalStateException("Cannot create Vulkan surface without a selected GLFW window handle.");
        }
        try (MemoryStack stack = stackPush()) {
            java.nio.LongBuffer pSurface = stack.mallocLong(1);
            checker.check(
                "glfwCreateWindowSurface",
                GLFWVulkan.glfwCreateWindowSurface(instance, windowHandle, null, pSurface)
            );
            surface = pSurface.get(0);
            state = State.SURFACE_CREATED;
            publishSnapshot();
            return surface;
        }
    }

    PhysicalDeviceSelection selectPhysicalDevice(VkResultChecker checker) {
        requireInstance();
        requireSurface();
        try (MemoryStack stack = stackPush()) {
            IntBuffer count = stack.ints(0);
            checker.check("vkEnumeratePhysicalDevices(count)",
                VK10.vkEnumeratePhysicalDevices(instance, count, null));

            int deviceCount = count.get(0);
            if (deviceCount <= 0) {
                throw new IllegalStateException("No Vulkan physical devices were found.");
            }

            PointerBuffer physicalDevices = stack.mallocPointer(deviceCount);
            checker.check("vkEnumeratePhysicalDevices(list)",
                VK10.vkEnumeratePhysicalDevices(instance, count, physicalDevices));

            for (int index = 0; index < deviceCount; index++) {
                VkPhysicalDevice candidate = new VkPhysicalDevice(physicalDevices.get(index), instance);
                OptionalInt queueFamily = findGraphicsPresentQueueFamily(candidate, checker);
                if (queueFamily.isPresent()) {
                    DeviceCapabilitySnapshot capabilities = capturePhysicalDeviceProperties(candidate);
                    physicalDevice = candidate;
                    QueueFamilyPlan queueFamilyPlan = runtimeSnapshot.queueFamilyPlan();
                    runtimeSnapshot = new RuntimeSnapshot(
                        State.DEVICE_SELECTED,
                        runtimeSnapshot.instanceProperties2ExtensionEnabled(),
                        capabilities,
                        queueFamilyPlan,
                        runtimeSnapshot.fillModeNonSolidEnabled(),
                        runtimeSnapshot.vertexPipelineStoresAndAtomicsEnabled(),
                        runtimeSnapshot.fragmentStoresAndAtomicsEnabled(),
                        runtimeSnapshot.shaderStorageImageReadWithoutFormatEnabled(),
                        runtimeSnapshot.shaderStorageImageWriteWithoutFormatEnabled(),
                        runtimeSnapshot.presentIdExtensionEnabled(),
                        runtimeSnapshot.presentWaitExtensionEnabled(),
                        runtimeSnapshot.attachmentFeedbackLoopLayoutEnabled()
                    );
                    state = State.DEVICE_SELECTED;
                    publishSnapshot();
                    return new PhysicalDeviceSelection(candidate, queueFamilyPlan, capabilities);
                }
            }

            throw new IllegalStateException(
                "No physical device with combined graphics+present queue support for GLFW surface was found.");
        }
    }

    LogicalDeviceResult createLogicalDeviceAndQueues(VkResultChecker checker) {
        requirePhysicalDevice();
        QueueFamilyPlan queuePlan = runtimeSnapshot.queueFamilyPlan();
        DeviceCapabilitySnapshot capabilities = runtimeSnapshot.capabilities();
        try (MemoryStack stack = stackPush()) {
            int queueCount = queuePlan.requestedQueueCount();
            java.nio.FloatBuffer priorities = stack.mallocFloat(queueCount);
            for (int queueIndex = 0; queueIndex < queueCount; queueIndex++) {
                priorities.put(queueIndex, 1.0f);
            }

            VkDeviceQueueCreateInfo.Buffer queueCreateInfos = VkDeviceQueueCreateInfo.calloc(1, stack);
            queueCreateInfos.get(0)
                .sType$Default()
                .queueFamilyIndex(queuePlan.graphicsFamilyIndex())
                .pQueuePriorities(priorities);

            Set<String> supportedExtensions = enumerateDeviceExtensionNames(physicalDevice, checker);
            DeviceExtensionPlan extensionPlan = planDeviceExtensions(supportedExtensions);

            int enabledExtensionCount = 1
                + (extensionPlan.presentId() ? 1 : 0)
                + (extensionPlan.presentWait() ? 1 : 0)
                + (extensionPlan.attachmentFeedbackLoopLayout() ? 1 : 0);
            PointerBuffer enabledExtensions = stack.mallocPointer(enabledExtensionCount);
            enabledExtensions.put(stack.UTF8(KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME));
            if (extensionPlan.presentId()) {
                enabledExtensions.put(stack.UTF8(KHRPresentId.VK_KHR_PRESENT_ID_EXTENSION_NAME));
            }
            if (extensionPlan.presentWait()) {
                enabledExtensions.put(stack.UTF8(KHRPresentWait.VK_KHR_PRESENT_WAIT_EXTENSION_NAME));
            }
            if (extensionPlan.attachmentFeedbackLoopLayout()) {
                enabledExtensions.put(stack.UTF8(EXTAttachmentFeedbackLoopLayout.VK_EXT_ATTACHMENT_FEEDBACK_LOOP_LAYOUT_EXTENSION_NAME));
            }
            enabledExtensions.flip();

            long featureChainHead = MemoryUtil.NULL;
            VkPhysicalDevicePresentIdFeaturesKHR presentIdFeatures = null;
            if (extensionPlan.presentId()) {
                presentIdFeatures = VkPhysicalDevicePresentIdFeaturesKHR.calloc(stack)
                    .sType$Default()
                    .presentId(true);
                featureChainHead = presentIdFeatures.address();
            }
            if (extensionPlan.presentWait()) {
                VkPhysicalDevicePresentWaitFeaturesKHR presentWaitFeatures = VkPhysicalDevicePresentWaitFeaturesKHR.calloc(stack)
                    .sType$Default()
                    .presentWait(true);
                if (presentIdFeatures != null) {
                    presentIdFeatures.pNext(presentWaitFeatures.address());
                } else {
                    featureChainHead = presentWaitFeatures.address();
                }
            }
            if (extensionPlan.attachmentFeedbackLoopLayout()) {
                VkPhysicalDeviceAttachmentFeedbackLoopLayoutFeaturesEXT feedbackLoopFeatures =
                    VkPhysicalDeviceAttachmentFeedbackLoopLayoutFeaturesEXT.calloc(stack)
                        .sType$Default()
                        .attachmentFeedbackLoopLayout(true);
                if (featureChainHead == MemoryUtil.NULL) {
                    featureChainHead = feedbackLoopFeatures.address();
                } else {
                    feedbackLoopFeatures.pNext(featureChainHead);
                    featureChainHead = feedbackLoopFeatures.address();
                }
            }

            VkPhysicalDeviceFeatures enabledFeatures = VkPhysicalDeviceFeatures.calloc(stack);
            if (capabilities.fillModeNonSolidSupported()) {
                enabledFeatures.fillModeNonSolid(true);
            }
            if (capabilities.vertexPipelineStoresAndAtomicsSupported()) {
                enabledFeatures.vertexPipelineStoresAndAtomics(true);
            }
            if (capabilities.fragmentStoresAndAtomicsSupported()) {
                enabledFeatures.fragmentStoresAndAtomics(true);
            }
            if (capabilities.shaderStorageImageReadWithoutFormatSupported()) {
                enabledFeatures.shaderStorageImageReadWithoutFormat(true);
            }
            if (capabilities.shaderStorageImageWriteWithoutFormatSupported()) {
                enabledFeatures.shaderStorageImageWriteWithoutFormat(true);
            }

            VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.calloc(stack)
                .sType$Default()
                .pQueueCreateInfos(queueCreateInfos)
                .ppEnabledExtensionNames(enabledExtensions)
                .pEnabledFeatures(enabledFeatures);
            if (featureChainHead != MemoryUtil.NULL) {
                createInfo.pNext(featureChainHead);
            }

            PointerBuffer pDevice = stack.mallocPointer(1);
            checker.check("vkCreateDevice", VK10.vkCreateDevice(physicalDevice, createInfo, null, pDevice));
            logicalDevice = new VkDevice(pDevice.get(0), physicalDevice, createInfo);

            PointerBuffer pQueue = stack.mallocPointer(1);
            VK10.vkGetDeviceQueue(logicalDevice, queuePlan.graphicsFamilyIndex(), queuePlan.graphicsQueueIndex(), pQueue);
            graphicsQueue = new VkQueue(pQueue.get(0), logicalDevice);

            if (queuePlan.usesSeparatePresentQueueHandle()) {
                VK10.vkGetDeviceQueue(logicalDevice, queuePlan.graphicsFamilyIndex(), queuePlan.presentQueueIndex(), pQueue);
                presentQueue = new VkQueue(pQueue.get(0), logicalDevice);
            } else {
                presentQueue = graphicsQueue;
            }

            runtimeSnapshot = new RuntimeSnapshot(
                State.DEVICE_CREATED,
                runtimeSnapshot.instanceProperties2ExtensionEnabled(),
                capabilities,
                queuePlan,
                capabilities.fillModeNonSolidSupported(),
                capabilities.vertexPipelineStoresAndAtomicsSupported(),
                capabilities.fragmentStoresAndAtomicsSupported(),
                capabilities.shaderStorageImageReadWithoutFormatSupported(),
                capabilities.shaderStorageImageWriteWithoutFormatSupported(),
                extensionPlan.presentId(),
                extensionPlan.presentWait(),
                extensionPlan.attachmentFeedbackLoopLayout()
            );
            state = State.DEVICE_CREATED;
            publishSnapshot();

            LOGGER.info(
                "Enabled Vulkan present completion extensions: presentId={}, presentWait={}",
                extensionPlan.presentId(),
                extensionPlan.presentWait()
            );

            return new LogicalDeviceResult(
                logicalDevice,
                graphicsQueue,
                presentQueue,
                runtimeSnapshot.fillModeNonSolidEnabled(),
                runtimeSnapshot.vertexPipelineStoresAndAtomicsEnabled(),
                runtimeSnapshot.fragmentStoresAndAtomicsEnabled(),
                runtimeSnapshot.shaderStorageImageReadWithoutFormatEnabled(),
                runtimeSnapshot.shaderStorageImageWriteWithoutFormatEnabled(),
                runtimeSnapshot.presentIdExtensionEnabled(),
                runtimeSnapshot.presentWaitExtensionEnabled(),
                runtimeSnapshot.attachmentFeedbackLoopLayoutEnabled()
            );
        }
    }

    SwapchainResourceSnapshot createSwapchain(long oldSwapchainHandle, VkResultChecker checker) {
        requireLogicalDevice();
        requireSurface();

        long newSwapchainHandle = VK10.VK_NULL_HANDLE;
        List<Long> newImageViewHandles = List.of();
        long[] newRenderFinishedSemaphores = new long[0];
        try (MemoryStack stack = stackPush()) {
            SwapchainCreationPlan plan = buildSwapchainCreationPlan(oldSwapchainHandle, stack, checker);

            VkSwapchainCreateInfoKHR createInfo = VkSwapchainCreateInfoKHR.calloc(stack)
                .sType$Default()
                .surface(surface)
                .minImageCount(plan.minImageCount())
                .imageFormat(plan.imageFormat())
                .imageColorSpace(plan.colorSpace())
                .imageExtent(VkExtent2D.malloc(stack).set(plan.width(), plan.height()))
                .imageArrayLayers(1)
                .imageUsage(plan.imageUsage())
                .imageSharingMode(plan.imageSharingMode())
                .preTransform(plan.preTransform())
                .compositeAlpha(plan.compositeAlpha())
                .presentMode(plan.presentMode())
                .clipped(true)
                .oldSwapchain(plan.oldSwapchainHandle());

            java.nio.LongBuffer pSwapchain = stack.mallocLong(1);
            checker.check("vkCreateSwapchainKHR",
                KHRSwapchain.vkCreateSwapchainKHR(logicalDevice, createInfo, null, pSwapchain));
            newSwapchainHandle = pSwapchain.get(0);

            SwapchainImageResources imageResources = createSwapchainImageResources(
                stack,
                newSwapchainHandle,
                plan.imageFormat(),
                checker
            );
            newImageViewHandles = imageResources.imageViewHandles();
            newRenderFinishedSemaphores = createSwapchainRenderFinishedSemaphores(imageResources.imageHandles().size(), checker);

            swapchain = newSwapchainHandle;
            state = State.SWAPCHAIN_READY;
            swapchainImageFormat = plan.imageFormat();
            swapchainColorSpace = plan.colorSpace();
            swapchainPresentMode = plan.presentMode();
            swapchainWidth = plan.width();
            swapchainHeight = plan.height();
            swapchainImageHandles = imageResources.imageHandles();
            swapchainImageViewHandles = imageResources.imageViewHandles();
            publishSnapshot();
            SwapchainResourceSnapshot snapshot = new SwapchainResourceSnapshot(
                newSwapchainHandle,
                plan.imageFormat(),
                plan.colorSpace(),
                plan.presentMode(),
                plan.width(),
                plan.height(),
                plan.imageUsage(),
                imageResources.imageHandles(),
                imageResources.imageViewHandles(),
                newRenderFinishedSemaphores
            );

            LOGGER.info(
                "Created Vulkan swapchain: extent={}x{}, images={}, format=0x{}, presentMode=0x{}, usage=0x{}, windowHandle=0x{}",
                snapshot.width(),
                snapshot.height(),
                snapshot.imageHandles().size(),
                Integer.toHexString(snapshot.imageFormat()),
                Integer.toHexString(snapshot.presentMode()),
                Integer.toHexString(snapshot.imageUsage()),
                Long.toHexString(windowHandle)
            );
            return snapshot;
        } catch (RuntimeException exception) {
            if (!newImageViewHandles.isEmpty()) {
                destroySwapchainImageViews(newImageViewHandles);
            }
            destroySemaphores(newRenderFinishedSemaphores);
            if (newSwapchainHandle != VK10.VK_NULL_HANDLE && logicalDevice != null) {
                KHRSwapchain.vkDestroySwapchainKHR(logicalDevice, newSwapchainHandle, null);
                if (swapchain == newSwapchainHandle) {
                    swapchain = VK10.VK_NULL_HANDLE;
                    clearSwapchainSnapshotState();
                    state = logicalDevice != null ? State.DEVICE_CREATED : state;
                    publishSnapshot();
                }
            }
            throw exception;
        }
    }

    SwapchainCreationPlan buildSwapchainCreationPlan(
        long oldSwapchainHandle,
        MemoryStack stack,
        VkResultChecker checker
    ) {
        VkSurfaceCapabilitiesKHR capabilities = VkSurfaceCapabilitiesKHR.malloc(stack);
        checker.check("vkGetPhysicalDeviceSurfaceCapabilitiesKHR",
            KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice, surface, capabilities));

        IntBuffer formatCount = stack.ints(0);
        checker.check("vkGetPhysicalDeviceSurfaceFormatsKHR(count)",
            KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, formatCount, null));
        if (formatCount.get(0) <= 0) {
            throw new IllegalStateException("No Vulkan surface formats were reported for swapchain creation.");
        }

        VkSurfaceFormatKHR.Buffer surfaceFormats = VkSurfaceFormatKHR.malloc(formatCount.get(0), stack);
        checker.check("vkGetPhysicalDeviceSurfaceFormatsKHR(list)",
            KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, formatCount, surfaceFormats));

        VkSurfaceFormatKHR chosenFormat = chooseSurfaceFormat(surfaceFormats);

        IntBuffer presentModeCount = stack.ints(0);
        checker.check("vkGetPhysicalDeviceSurfacePresentModesKHR(count)",
            KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, presentModeCount, null));
        if (presentModeCount.get(0) <= 0) {
            throw new IllegalStateException("No Vulkan present modes were reported for swapchain creation.");
        }

        IntBuffer presentModes = stack.mallocInt(presentModeCount.get(0));
        checker.check("vkGetPhysicalDeviceSurfacePresentModesKHR(list)",
            KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, presentModeCount, presentModes));

        int presentMode = choosePresentMode(presentModes, FORCE_FIFO_PRESENT_MODE, GLFW.glfwGetPlatform());
        VkExtent2D extent = chooseSwapExtent(capabilities, windowHandle, stack);
        int minImageCount = chooseImageCount(capabilities.minImageCount(), capabilities.maxImageCount());
        int swapchainImageUsage = chooseSwapchainImageUsage(capabilities.supportedUsageFlags());

        LOGGER.info(
            "Vulkan surface formats: [{}]; selected=format=0x{}, colorSpace=0x{}",
            describeSurfaceFormats(surfaceFormats),
            Integer.toHexString(chosenFormat.format()),
            Integer.toHexString(chosenFormat.colorSpace())
        );

        LOGGER.info(
            "Vulkan surface present modes: [{}]; selected=0x{}",
            describePresentModes(presentModes),
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

        if ((capabilities.supportedUsageFlags() & VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT) == 0) {
            LOGGER.warn(
                "Vulkan surface does not report VK_IMAGE_USAGE_TRANSFER_DST_BIT support for swapchain images; present blit path may be unavailable. supportedUsageFlags=0x{}",
                Integer.toHexString(capabilities.supportedUsageFlags())
            );
        }

        return new SwapchainCreationPlan(
            chosenFormat.format(),
            chosenFormat.colorSpace(),
            presentMode,
            extent.width(),
            extent.height(),
            minImageCount,
            swapchainImageUsage,
            VK10.VK_SHARING_MODE_EXCLUSIVE,
            capabilities.currentTransform(),
            KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR,
            oldSwapchainHandle,
            new SurfaceSupportSnapshot(
                capabilities.minImageCount(),
                capabilities.maxImageCount(),
                capabilities.currentExtent().width(),
                capabilities.currentExtent().height(),
                capabilities.minImageExtent().width(),
                capabilities.minImageExtent().height(),
                capabilities.maxImageExtent().width(),
                capabilities.maxImageExtent().height(),
                capabilities.supportedTransforms(),
                capabilities.currentTransform(),
                capabilities.supportedCompositeAlpha(),
                capabilities.supportedUsageFlags()
            )
        );
    }

    boolean isFramebufferResizeMismatch(int swapchainWidth, int swapchainHeight) {
        if (windowHandle == 0L) {
            return false;
        }

        try (MemoryStack stack = stackPush()) {
            IntBuffer width = stack.ints(0);
            IntBuffer height = stack.ints(0);
            GLFW.glfwGetFramebufferSize(windowHandle, width, height);

            int currentWidth = width.get(0);
            int currentHeight = height.get(0);
            if (currentWidth <= 0 || currentHeight <= 0) {
                return false;
            }

            return currentWidth != swapchainWidth || currentHeight != swapchainHeight;
        }
    }

    void updateWindowHandle(long windowHandle) {
        if (windowHandle == VK10.VK_NULL_HANDLE) {
            throw new IllegalArgumentException("windowHandle must not be null.");
        }
        this.windowHandle = windowHandle;
        publishSnapshot();
    }

    void destroySwapchain() {
        destroySwapchain(swapchain);
        swapchain = VK10.VK_NULL_HANDLE;
    }

    void destroySwapchain(long swapchainHandle) {
        if (logicalDevice != null && swapchainHandle != VK10.VK_NULL_HANDLE) {
            KHRSwapchain.vkDestroySwapchainKHR(logicalDevice, swapchainHandle, null);
            if (swapchain == swapchainHandle) {
                swapchain = VK10.VK_NULL_HANDLE;
                clearSwapchainSnapshotState();
                if (state == State.SWAPCHAIN_READY) {
                    state = State.DEVICE_CREATED;
                }
                publishSnapshot();
            }
        }
    }

    void destroySurface() {
        if (instance != null && surface != VK10.VK_NULL_HANDLE) {
            KHRSurface.vkDestroySurfaceKHR(instance, surface, null);
            surface = VK10.VK_NULL_HANDLE;
            if (state == State.SURFACE_CREATED || state == State.DEVICE_SELECTED || state == State.DEVICE_CREATED) {
                state = State.INSTANCE_CREATED;
            }
            publishSnapshot();
        }
    }

    void destroyLogicalDevice() {
        if (logicalDevice != null) {
            VK10.vkDestroyDevice(logicalDevice, null);
            logicalDevice = null;
            graphicsQueue = null;
            presentQueue = null;
            swapchain = VK10.VK_NULL_HANDLE;
            clearSwapchainSnapshotState();
            if (state == State.DEVICE_CREATED || state == State.SWAPCHAIN_READY || state == State.DEVICE_LOST) {
                state = surface != VK10.VK_NULL_HANDLE ? State.SURFACE_CREATED : State.INSTANCE_CREATED;
            }
            publishSnapshot();
        }
    }

    void destroyInstance() {
        if (instance != null) {
            VK10.vkDestroyInstance(instance, null);
            instance = null;
            physicalDevice = null;
            state = State.SHUTDOWN;
            publishSnapshot();
        }
    }

    void destroySwapchainImageViews(List<Long> imageViewHandles) {
        if (logicalDevice == null || imageViewHandles == null || imageViewHandles.isEmpty()) {
            return;
        }

        for (Long imageViewHandle : imageViewHandles) {
            if (imageViewHandle != null && imageViewHandle != VK10.VK_NULL_HANDLE) {
                VK10.vkDestroyImageView(logicalDevice, imageViewHandle, null);
            }
        }
    }

    void destroySemaphores(long[] semaphores) {
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

    void markDeviceLost() {
        state = State.DEVICE_LOST;
        publishSnapshot();
    }

    void markShutdownComplete() {
        runtimeSnapshot = RuntimeSnapshot.empty();
        clearSwapchainSnapshotState();
        state = State.SHUTDOWN;
        publishSnapshot();
    }

    private Set<String> enumerateInstanceExtensionNames(VkResultChecker checker) {
        try (MemoryStack stack = stackPush()) {
            IntBuffer extensionCount = stack.ints(0);
            checker.check(
                "vkEnumerateInstanceExtensionProperties(count)",
                VK10.vkEnumerateInstanceExtensionProperties((java.nio.ByteBuffer) null, extensionCount, null)
            );

            int count = extensionCount.get(0);
            if (count <= 0) {
                return Collections.emptySet();
            }

            VkExtensionProperties.Buffer extensionProperties = VkExtensionProperties.malloc(count, stack);
            checker.check(
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

    private Set<String> enumerateDeviceExtensionNames(VkPhysicalDevice device, VkResultChecker checker) {
        try (MemoryStack stack = stackPush()) {
            IntBuffer extensionCount = stack.ints(0);
            checker.check(
                "vkEnumerateDeviceExtensionProperties(count)",
                VK10.vkEnumerateDeviceExtensionProperties(device, (java.nio.ByteBuffer) null, extensionCount, null)
            );

            int count = extensionCount.get(0);
            if (count <= 0) {
                return Collections.emptySet();
            }

            VkExtensionProperties.Buffer extensionProperties = VkExtensionProperties.malloc(count, stack);
            checker.check(
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

    private OptionalInt findGraphicsPresentQueueFamily(VkPhysicalDevice device, VkResultChecker checker) {
        try (MemoryStack stack = stackPush()) {
            IntBuffer queueCount = stack.ints(0);
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

                IntBuffer supported = stack.ints(VK10.VK_FALSE);
                checker.check("vkGetPhysicalDeviceSurfaceSupportKHR",
                    KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(device, familyIndex, surface, supported));
                if (supported.get(0) == VK10.VK_TRUE) {
                    QueueFamilyPlan queuePlan = planCombinedGraphicsPresentQueue(familyIndex, properties.queueCount());
                    runtimeSnapshot = new RuntimeSnapshot(
                        runtimeSnapshot.state(),
                        runtimeSnapshot.instanceProperties2ExtensionEnabled(),
                        runtimeSnapshot.capabilities(),
                        queuePlan,
                        runtimeSnapshot.fillModeNonSolidEnabled(),
                        runtimeSnapshot.vertexPipelineStoresAndAtomicsEnabled(),
                        runtimeSnapshot.fragmentStoresAndAtomicsEnabled(),
                        runtimeSnapshot.shaderStorageImageReadWithoutFormatEnabled(),
                        runtimeSnapshot.shaderStorageImageWriteWithoutFormatEnabled(),
                        runtimeSnapshot.presentIdExtensionEnabled(),
                        runtimeSnapshot.presentWaitExtensionEnabled(),
                        runtimeSnapshot.attachmentFeedbackLoopLayoutEnabled()
                    );
                    return OptionalInt.of(familyIndex);
                }
            }

            return OptionalInt.empty();
        }
    }

    private DeviceCapabilitySnapshot capturePhysicalDeviceProperties(VkPhysicalDevice device) {
        try (MemoryStack stack = stackPush()) {
            VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.malloc(stack);
            VK10.vkGetPhysicalDeviceProperties(device, properties);
            VkPhysicalDeviceFeatures features = VkPhysicalDeviceFeatures.malloc(stack);
            VK10.vkGetPhysicalDeviceFeatures(device, features);
            int maxPerStageSamplers = properties.limits().maxPerStageDescriptorSamplers();
            int maxPerStageSampledImages = properties.limits().maxPerStageDescriptorSampledImages();
            int combinedImageSamplerBudget = Math.min(maxPerStageSamplers, maxPerStageSampledImages);
            String name = properties.deviceNameString();
            return new DeviceCapabilitySnapshot(
                properties.vendorID(),
                properties.apiVersion(),
                name == null || name.isBlank() ? "Vulkan GPU" : name,
                features.fillModeNonSolid(),
                features.vertexPipelineStoresAndAtomics(),
                features.fragmentStoresAndAtomics(),
                features.shaderStorageImageReadWithoutFormat(),
                features.shaderStorageImageWriteWithoutFormat(),
                Math.max(1L, properties.limits().minUniformBufferOffsetAlignment()),
                Math.max(1024, properties.limits().maxImageDimension2D()),
                Math.max(16, Math.min(32, combinedImageSamplerBudget))
            );
        }
    }

    private SwapchainImageResources createSwapchainImageResources(
        MemoryStack stack,
        long swapchainHandle,
        int imageFormat,
        VkResultChecker checker
    ) {
        IntBuffer imageCount = stack.ints(0);
        checker.check("vkGetSwapchainImagesKHR(count)",
            KHRSwapchain.vkGetSwapchainImagesKHR(logicalDevice, swapchainHandle, imageCount, null));
        int count = imageCount.get(0);
        if (count <= 0) {
            throw new IllegalStateException("vkGetSwapchainImagesKHR returned no swapchain images");
        }

        java.nio.LongBuffer images = stack.mallocLong(count);
        checker.check("vkGetSwapchainImagesKHR(list)",
            KHRSwapchain.vkGetSwapchainImagesKHR(logicalDevice, swapchainHandle, imageCount, images));

        List<Long> imageHandles = new ArrayList<>(count);
        List<Long> imageViewHandles = new ArrayList<>(count);
        try {
            for (int index = 0; index < count; index++) {
                long imageHandle = images.get(index);
                imageHandles.add(imageHandle);
                imageViewHandles.add(createSwapchainImageView(stack, imageHandle, imageFormat, checker));
            }
        } catch (RuntimeException exception) {
            destroySwapchainImageViews(imageViewHandles);
            throw exception;
        }

        return new SwapchainImageResources(imageHandles, imageViewHandles);
    }

    private long createSwapchainImageView(MemoryStack stack, long imageHandle, int imageFormat, VkResultChecker checker) {
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
        checker.check("vkCreateImageView(swapchain)",
            VK10.vkCreateImageView(logicalDevice, viewCreateInfo, null, pImageView));
        return pImageView.get(0);
    }

    private long[] createSwapchainRenderFinishedSemaphores(int imageCount, VkResultChecker checker) {
        if (imageCount <= 0) {
            throw new IllegalStateException("Cannot create render-finished semaphores without swapchain images.");
        }

        long[] semaphores = new long[imageCount];
        try (MemoryStack stack = stackPush()) {
            VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.calloc(stack).sType$Default();
            java.nio.LongBuffer pSemaphore = stack.mallocLong(1);
            for (int imageIndex = 0; imageIndex < imageCount; imageIndex++) {
                checker.check(
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

    static QueueFamilyPlan planCombinedGraphicsPresentQueue(int familyIndex, int queueCount) {
        int requestedQueueCount = Math.min(2, Math.max(1, queueCount));
        int presentQueueIndex = requestedQueueCount > 1 ? 1 : 0;
        return new QueueFamilyPlan(familyIndex, queueCount, 0, presentQueueIndex);
    }

    static DeviceExtensionPlan planDeviceExtensions(Set<String> supportedExtensions) {
        boolean hasFeedbackLoopLayout = supportedExtensions.contains(
            EXTAttachmentFeedbackLoopLayout.VK_EXT_ATTACHMENT_FEEDBACK_LOOP_LAYOUT_EXTENSION_NAME);
        return new DeviceExtensionPlan(false, false, hasFeedbackLoopLayout);
    }

    static int chooseImageCount(int minImageCount, int maxImageCount) {
        int chosen = Math.max(1, minImageCount + 1);
        if (maxImageCount > 0 && chosen > maxImageCount) {
            chosen = maxImageCount;
        }
        return chosen;
    }

    static int chooseSwapchainImageUsage(int supportedUsageFlags) {
        int usage = VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
        if ((supportedUsageFlags & VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT) != 0) {
            usage |= VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT;
        }
        return usage;
    }

    static int choosePresentMode(IntBuffer presentModes, boolean forceFifo, int glfwPlatform) {
        if (forceFifo && containsPresentMode(presentModes, KHRSurface.VK_PRESENT_MODE_FIFO_KHR)) {
            return KHRSurface.VK_PRESENT_MODE_FIFO_KHR;
        }
        if (containsPresentMode(presentModes, KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR)) {
            return KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR;
        }
        if (glfwPlatform == GLFW.GLFW_PLATFORM_X11
            && containsPresentMode(presentModes, KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR)) {
            return KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR;
        }
        if (containsPresentMode(presentModes, KHRSurface.VK_PRESENT_MODE_FIFO_KHR)) {
            return KHRSurface.VK_PRESENT_MODE_FIFO_KHR;
        }
        if (containsPresentMode(presentModes, KHRSurface.VK_PRESENT_MODE_FIFO_RELAXED_KHR)) {
            return KHRSurface.VK_PRESENT_MODE_FIFO_RELAXED_KHR;
        }
        if (containsPresentMode(presentModes, KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR)) {
            return KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR;
        }
        return KHRSurface.VK_PRESENT_MODE_FIFO_KHR;
    }

    static VkSurfaceFormatKHR chooseSurfaceFormat(VkSurfaceFormatKHR.Buffer formats) {
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

    static boolean isPreferredRgba8SurfaceFormat(VkSurfaceFormatKHR format) {
        if (format.colorSpace() != KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
            return false;
        }

        return format.format() == VK10.VK_FORMAT_R8G8B8A8_UNORM
            || format.format() == VK10.VK_FORMAT_R8G8B8A8_SRGB;
    }

    static VkExtent2D chooseSwapExtent(VkSurfaceCapabilitiesKHR capabilities, long windowHandle, MemoryStack stack) {
        if (capabilities.currentExtent().width() != 0xFFFFFFFF) {
            return VkExtent2D.malloc(stack)
                .set(capabilities.currentExtent().width(), capabilities.currentExtent().height());
        }

        IntBuffer width = stack.ints(0);
        IntBuffer height = stack.ints(0);
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

    static String describeSurfaceFormats(VkSurfaceFormatKHR.Buffer formats) {
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

    private static boolean containsPresentMode(IntBuffer presentModes, int expectedMode) {
        for (int index = 0; index < presentModes.remaining(); index++) {
            if (presentModes.get(index) == expectedMode) {
                return true;
            }
        }
        return false;
    }

    private static String describePresentModes(IntBuffer presentModes) {
        StringBuilder presentModeSummary = new StringBuilder();
        for (int index = 0; index < presentModes.remaining(); index++) {
            if (index > 0) {
                presentModeSummary.append(", ");
            }
            presentModeSummary.append("0x").append(Integer.toHexString(presentModes.get(index)));
        }
        return presentModeSummary.toString();
    }

    private static boolean pointerBufferContains(PointerBuffer buffer, String needle) {
        for (int index = buffer.position(); index < buffer.limit(); index++) {
            if (needle.equals(buffer.getStringUTF8(index))) {
                return true;
            }
        }
        return false;
    }

    private static String describePointerBufferStrings(PointerBuffer buffer) {
        StringBuilder summary = new StringBuilder();
        for (int index = buffer.position(); index < buffer.limit(); index++) {
            if (summary.length() > 0) {
                summary.append(", ");
            }
            summary.append(buffer.getStringUTF8(index));
        }
        return summary.toString();
    }

    private void requireInstance() {
        if (instance == null) {
            throw new IllegalStateException("Vulkan instance has not been created.");
        }
    }

    private void requireSurface() {
        if (surface == VK10.VK_NULL_HANDLE) {
            throw new IllegalStateException("Vulkan surface has not been created.");
        }
    }

    private void requirePhysicalDevice() {
        if (physicalDevice == null) {
            throw new IllegalStateException("Vulkan physical device has not been selected.");
        }
    }

    private void requireLogicalDevice() {
        if (logicalDevice == null) {
            throw new IllegalStateException("Vulkan logical device has not been created.");
        }
    }

    private record SwapchainImageResources(List<Long> imageHandles, List<Long> imageViewHandles) {
        private SwapchainImageResources {
            imageHandles = List.copyOf(imageHandles);
            imageViewHandles = List.copyOf(imageViewHandles);
        }
    }
}

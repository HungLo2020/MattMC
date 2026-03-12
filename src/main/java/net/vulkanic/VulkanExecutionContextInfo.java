package net.vulkanic;

/**
 * Immutable snapshot of backend execution-context ownership relevant to
 * Vulkan-native command submission.
 *
 * <p>This class is intentionally backend-neutral at the API boundary: on
 * non-Vulkan backends, Vulkan-native handles are reported as unavailable.
 * For Vulkan backend routing, this snapshot provides logical-device/queue
 * ownership information and command-buffer context handles when available.</p>
 */
public final class VulkanExecutionContextInfo {

    private final GraphicsBackendType backendType;
    private final boolean nativeVulkanReady;
    private final long logicalDeviceHandle;
    private final long graphicsQueueHandle;
    private final int graphicsQueueFamilyIndex;
    private final long commandPoolHandle;
    private final long commandBufferHandle;
    private final String commandContextDebugName;
    private final String status;

    public VulkanExecutionContextInfo(
        GraphicsBackendType backendType,
        boolean nativeVulkanReady,
        long logicalDeviceHandle,
        long graphicsQueueHandle,
        int graphicsQueueFamilyIndex,
        long commandPoolHandle,
        long commandBufferHandle,
        String commandContextDebugName,
        String status
    ) {
        this.backendType = backendType == null ? GraphicsBackendType.OPENGL : backendType;
        this.nativeVulkanReady = nativeVulkanReady;
        this.logicalDeviceHandle = logicalDeviceHandle;
        this.graphicsQueueHandle = graphicsQueueHandle;
        this.graphicsQueueFamilyIndex = graphicsQueueFamilyIndex;
        this.commandPoolHandle = commandPoolHandle;
        this.commandBufferHandle = commandBufferHandle;
        this.commandContextDebugName = commandContextDebugName == null ? "" : commandContextDebugName;
        this.status = status == null ? "unknown" : status;
    }

    public static VulkanExecutionContextInfo unavailable(
        GraphicsBackendType backendType,
        boolean nativeVulkanReady,
        String status
    ) {
        return new VulkanExecutionContextInfo(
            backendType,
            nativeVulkanReady,
            0L,
            0L,
            -1,
            0L,
            0L,
            "",
            status
        );
    }

    public static VulkanExecutionContextInfo available(
        GraphicsBackendType backendType,
        long logicalDeviceHandle,
        long graphicsQueueHandle,
        int graphicsQueueFamilyIndex,
        long commandPoolHandle,
        long commandBufferHandle,
        String commandContextDebugName,
        String status
    ) {
        return new VulkanExecutionContextInfo(
            backendType,
            true,
            logicalDeviceHandle,
            graphicsQueueHandle,
            graphicsQueueFamilyIndex,
            commandPoolHandle,
            commandBufferHandle,
            commandContextDebugName,
            status
        );
    }

    public GraphicsBackendType getBackendType() {
        return backendType;
    }

    public boolean isNativeVulkanReady() {
        return nativeVulkanReady;
    }

    public long getLogicalDeviceHandle() {
        return logicalDeviceHandle;
    }

    public long getGraphicsQueueHandle() {
        return graphicsQueueHandle;
    }

    public int getGraphicsQueueFamilyIndex() {
        return graphicsQueueFamilyIndex;
    }

    public long getCommandPoolHandle() {
        return commandPoolHandle;
    }

    public long getCommandBufferHandle() {
        return commandBufferHandle;
    }

    public String getCommandContextDebugName() {
        return commandContextDebugName;
    }

    public String getStatus() {
        return status;
    }

    public boolean isAvailable() {
        return nativeVulkanReady
            && logicalDeviceHandle != 0L
            && graphicsQueueHandle != 0L
            && commandPoolHandle != 0L
            && commandBufferHandle != 0L
            && graphicsQueueFamilyIndex >= 0;
    }

    public String summaryLine() {
        return "backendType=" + backendType
            + ", nativeVulkanReady=" + nativeVulkanReady
            + ", available=" + isAvailable();
    }

    public String toMultilineString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Vulkan execution context info\n");
        sb.append(" - backendType=").append(backendType).append('\n');
        sb.append(" - nativeVulkanReady=").append(nativeVulkanReady).append('\n');
        sb.append(" - available=").append(isAvailable()).append('\n');
        sb.append(" - logicalDeviceHandle=0x").append(Long.toHexString(logicalDeviceHandle)).append('\n');
        sb.append(" - graphicsQueueHandle=0x").append(Long.toHexString(graphicsQueueHandle)).append('\n');
        sb.append(" - graphicsQueueFamilyIndex=").append(graphicsQueueFamilyIndex).append('\n');
        sb.append(" - commandPoolHandle=0x").append(Long.toHexString(commandPoolHandle)).append('\n');
        sb.append(" - commandBufferHandle=0x").append(Long.toHexString(commandBufferHandle)).append('\n');
        sb.append(" - commandContextDebugName=").append(commandContextDebugName).append('\n');
        sb.append(" - status=").append(status).append('\n');
        return sb.toString();
    }

    @Override
    public String toString() {
        return summaryLine();
    }
}

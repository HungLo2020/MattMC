package net.vulkanic;

/**
 * Immutable snapshot of Vulkan surface/swapchain ownership and metadata.
 *
 * <p>OpenGL backends return an unavailable snapshot. Vulkan backends populate
 * native handles and swapchain metadata when native bring-up succeeds.</p>
 */
public final class VulkanSwapchainSurfaceInfo {

    private final GraphicsBackendType backendType;
    private final boolean nativeVulkanReady;
    private final long surfaceHandle;
    private final long swapchainHandle;
    private final int swapchainImageFormat;
    private final int swapchainColorSpace;
    private final int swapchainPresentMode;
    private final int swapchainImageCount;
    private final int swapchainWidth;
    private final int swapchainHeight;
    private final String status;

    public VulkanSwapchainSurfaceInfo(
        GraphicsBackendType backendType,
        boolean nativeVulkanReady,
        long surfaceHandle,
        long swapchainHandle,
        int swapchainImageFormat,
        int swapchainColorSpace,
        int swapchainPresentMode,
        int swapchainImageCount,
        int swapchainWidth,
        int swapchainHeight,
        String status
    ) {
        this.backendType = backendType == null ? GraphicsBackendType.OPENGL : backendType;
        this.nativeVulkanReady = nativeVulkanReady;
        this.surfaceHandle = surfaceHandle;
        this.swapchainHandle = swapchainHandle;
        this.swapchainImageFormat = swapchainImageFormat;
        this.swapchainColorSpace = swapchainColorSpace;
        this.swapchainPresentMode = swapchainPresentMode;
        this.swapchainImageCount = swapchainImageCount;
        this.swapchainWidth = swapchainWidth;
        this.swapchainHeight = swapchainHeight;
        this.status = status == null ? "unknown" : status;
    }

    public static VulkanSwapchainSurfaceInfo unavailable(
        GraphicsBackendType backendType,
        boolean nativeVulkanReady,
        String status
    ) {
        return new VulkanSwapchainSurfaceInfo(
            backendType,
            nativeVulkanReady,
            0L,
            0L,
            -1,
            -1,
            -1,
            0,
            0,
            0,
            status
        );
    }

    public static VulkanSwapchainSurfaceInfo available(
        GraphicsBackendType backendType,
        long surfaceHandle,
        long swapchainHandle,
        int swapchainImageFormat,
        int swapchainColorSpace,
        int swapchainPresentMode,
        int swapchainImageCount,
        int swapchainWidth,
        int swapchainHeight,
        String status
    ) {
        return new VulkanSwapchainSurfaceInfo(
            backendType,
            true,
            surfaceHandle,
            swapchainHandle,
            swapchainImageFormat,
            swapchainColorSpace,
            swapchainPresentMode,
            swapchainImageCount,
            swapchainWidth,
            swapchainHeight,
            status
        );
    }

    public GraphicsBackendType getBackendType() {
        return backendType;
    }

    public boolean isNativeVulkanReady() {
        return nativeVulkanReady;
    }

    public long getSurfaceHandle() {
        return surfaceHandle;
    }

    public long getSwapchainHandle() {
        return swapchainHandle;
    }

    public int getSwapchainImageFormat() {
        return swapchainImageFormat;
    }

    public int getSwapchainColorSpace() {
        return swapchainColorSpace;
    }

    public int getSwapchainPresentMode() {
        return swapchainPresentMode;
    }

    public int getSwapchainImageCount() {
        return swapchainImageCount;
    }

    public int getSwapchainWidth() {
        return swapchainWidth;
    }

    public int getSwapchainHeight() {
        return swapchainHeight;
    }

    public String getStatus() {
        return status;
    }

    public boolean isAvailable() {
        return nativeVulkanReady
            && surfaceHandle != 0L
            && swapchainHandle != 0L
            && swapchainImageCount > 0
            && swapchainWidth > 0
            && swapchainHeight > 0;
    }

    public String summaryLine() {
        return "backendType=" + backendType
            + ", nativeVulkanReady=" + nativeVulkanReady
            + ", swapchainAvailable=" + isAvailable();
    }

    public String toMultilineString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Vulkan swapchain/surface info\n");
        sb.append(" - backendType=").append(backendType).append('\n');
        sb.append(" - nativeVulkanReady=").append(nativeVulkanReady).append('\n');
        sb.append(" - swapchainAvailable=").append(isAvailable()).append('\n');
        sb.append(" - surfaceHandle=0x").append(Long.toHexString(surfaceHandle)).append('\n');
        sb.append(" - swapchainHandle=0x").append(Long.toHexString(swapchainHandle)).append('\n');
        sb.append(" - swapchainImageFormat=").append(swapchainImageFormat).append('\n');
        sb.append(" - swapchainColorSpace=").append(swapchainColorSpace).append('\n');
        sb.append(" - swapchainPresentMode=").append(swapchainPresentMode).append('\n');
        sb.append(" - swapchainImageCount=").append(swapchainImageCount).append('\n');
        sb.append(" - swapchainExtent=").append(swapchainWidth).append("x").append(swapchainHeight).append('\n');
        sb.append(" - status=").append(status).append('\n');
        return sb.toString();
    }

    @Override
    public String toString() {
        return summaryLine();
    }
}

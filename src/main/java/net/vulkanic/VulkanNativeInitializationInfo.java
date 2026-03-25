package net.vulkanic;

/**
 * Immutable result of an explicit native Vulkan runtime initialization attempt.
 */
public final class VulkanNativeInitializationInfo {

    private final GraphicsBackendType backendType;
    private final boolean initializationAttempted;
    private final boolean nativeVulkanReady;
    private final boolean initializationSuccessful;
    private final String status;
    private final String readinessSummary;

    public VulkanNativeInitializationInfo(
        GraphicsBackendType backendType,
        boolean initializationAttempted,
        boolean nativeVulkanReady,
        boolean initializationSuccessful,
        String status,
        String readinessSummary
    ) {
        this.backendType = backendType == null ? GraphicsBackendType.OPENGL : backendType;
        this.initializationAttempted = initializationAttempted;
        this.nativeVulkanReady = nativeVulkanReady;
        this.initializationSuccessful = initializationSuccessful;
        this.status = status == null ? "unknown" : status;
        this.readinessSummary = readinessSummary == null ? "" : readinessSummary;
    }

    public static VulkanNativeInitializationInfo unsupported(GraphicsBackendType backendType, String status) {
        return new VulkanNativeInitializationInfo(
            backendType,
            false,
            false,
            false,
            status,
            ""
        );
    }

    public static VulkanNativeInitializationInfo attempted(
        GraphicsBackendType backendType,
        boolean nativeVulkanReady,
        boolean initializationSuccessful,
        String status,
        String readinessSummary
    ) {
        return new VulkanNativeInitializationInfo(
            backendType,
            true,
            nativeVulkanReady,
            initializationSuccessful,
            status,
            readinessSummary
        );
    }

    public GraphicsBackendType getBackendType() {
        return backendType;
    }

    public boolean isInitializationAttempted() {
        return initializationAttempted;
    }

    public boolean isNativeVulkanReady() {
        return nativeVulkanReady;
    }

    public boolean isInitializationSuccessful() {
        return initializationSuccessful;
    }

    public String getStatus() {
        return status;
    }

    public String getReadinessSummary() {
        return readinessSummary;
    }

    public String summaryLine() {
        return "backendType=" + backendType
            + ", initializationAttempted=" + initializationAttempted
            + ", nativeVulkanReady=" + nativeVulkanReady
            + ", initializationSuccessful=" + initializationSuccessful;
    }

    public String toMultilineString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Vulkan native initialization info\n");
        sb.append(" - backendType=").append(backendType).append('\n');
        sb.append(" - initializationAttempted=").append(initializationAttempted).append('\n');
        sb.append(" - nativeVulkanReady=").append(nativeVulkanReady).append('\n');
        sb.append(" - initializationSuccessful=").append(initializationSuccessful).append('\n');
        sb.append(" - status=").append(status).append('\n');
        if (!readinessSummary.isBlank()) {
            sb.append(" - readinessSummary=").append(readinessSummary).append('\n');
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return summaryLine();
    }
}

package net.vulkanic;

import java.util.List;

/**
 * Immutable diagnostics report describing how close the current runtime is to
 * being able to bring up a native Vulkan backend.
 *
 * <p>This report does <b>not</b> imply that Vulkan rendering is implemented.
 * It captures environment and bootstrap readiness signals so migration work can
 * make data-driven decisions.
 */
public final class VulkanReadinessReport {

    private final GraphicsBackendType activeBackendType;
    private final boolean vulkanBackendSelected;
    private final boolean nativeVulkanReady;
    private final boolean lwjglVulkanBindingsPresent;
    private final boolean glfwVulkanSupported;
    private final String glfwProbeStatus;
    private final List<String> blockers;

    public VulkanReadinessReport(
        GraphicsBackendType activeBackendType,
        boolean vulkanBackendSelected,
        boolean nativeVulkanReady,
        boolean lwjglVulkanBindingsPresent,
        boolean glfwVulkanSupported,
        String glfwProbeStatus,
        List<String> blockers
    ) {
        this.activeBackendType = activeBackendType;
        this.vulkanBackendSelected = vulkanBackendSelected;
        this.nativeVulkanReady = nativeVulkanReady;
        this.lwjglVulkanBindingsPresent = lwjglVulkanBindingsPresent;
        this.glfwVulkanSupported = glfwVulkanSupported;
        this.glfwProbeStatus = glfwProbeStatus == null ? "unknown" : glfwProbeStatus;
        this.blockers = List.copyOf(blockers == null ? List.of() : blockers);
    }

    public static VulkanReadinessReport forNonVulkanBackend(GraphicsBackendType activeBackendType,
            boolean nativeVulkanReady) {
        return new VulkanReadinessReport(
            activeBackendType,
            false,
            nativeVulkanReady,
            false,
            false,
            "skipped (Vulkan backend not selected)",
            List.of("Vulkan backend is not currently selected. Initialize with GraphicsBackendType.VULKAN to run Vulkan diagnostics.")
        );
    }

    /**
     * Diagnostics exposed while the Rust Vulkan presenter owns the route.
     * Java Vulkan bootstrap probes are deliberately not consulted in this
     * state; the report describes the Rust ownership boundary instead.
     */
    public static VulkanReadinessReport forRustWholeFrameVulkan() {
        return new VulkanReadinessReport(
            GraphicsBackendType.VULKAN,
            true,
            false,
            false,
            false,
            "Rust VulkanicGAL owns device, surface, and presentation",
            List.of("Java Vulkan runtime diagnostics are unavailable while Rust owns whole-frame presentation")
        );
    }

    public GraphicsBackendType getActiveBackendType() {
        return activeBackendType;
    }

    public boolean isVulkanBackendSelected() {
        return vulkanBackendSelected;
    }

    public boolean isNativeVulkanReady() {
        return nativeVulkanReady;
    }

    public boolean isLwjglVulkanBindingsPresent() {
        return lwjglVulkanBindingsPresent;
    }

    public boolean isGlfwVulkanSupported() {
        return glfwVulkanSupported;
    }

    public String getGlfwProbeStatus() {
        return glfwProbeStatus;
    }

    public List<String> getBlockers() {
        return blockers;
    }

    public boolean canAttemptNativeBringUp() {
        return vulkanBackendSelected && lwjglVulkanBindingsPresent && glfwVulkanSupported;
    }

    public String summaryLine() {
        return "backend=" + activeBackendType
            + ", vulkanSelected=" + vulkanBackendSelected
            + ", nativeVulkanReady=" + nativeVulkanReady
            + ", lwjglBindings=" + lwjglVulkanBindingsPresent
            + ", glfwVulkanSupported=" + glfwVulkanSupported;
    }

    public String toMultilineString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Vulkan readiness diagnostics\n");
        sb.append(" - activeBackendType=").append(activeBackendType).append('\n');
        sb.append(" - vulkanBackendSelected=").append(vulkanBackendSelected).append('\n');
        sb.append(" - nativeVulkanReady=").append(nativeVulkanReady).append('\n');
        sb.append(" - lwjglVulkanBindingsPresent=").append(lwjglVulkanBindingsPresent).append('\n');
        sb.append(" - glfwVulkanSupported=").append(glfwVulkanSupported).append('\n');
        sb.append(" - glfwProbeStatus=").append(glfwProbeStatus).append('\n');
        if (blockers.isEmpty()) {
            sb.append(" - blockers=<none>\n");
        } else {
            sb.append(" - blockers:\n");
            for (String blocker : blockers) {
                sb.append("   • ").append(blocker).append('\n');
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return summaryLine();
    }
}

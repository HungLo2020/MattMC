package net.vulkanic;

import net.logging.LogUtils;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Opt-in draw-state parity diagnostics for comparing OpenGL and Vulkan submissions.
 */
public final class VulkanicDrawStateDiagnostics {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean ENABLED = Boolean.parseBoolean(
        System.getProperty(
            "mattmc.vulkan.drawStateParity",
            System.getenv().getOrDefault("MATTMC_VULKAN_DRAW_STATE_PARITY", "false")
        )
    ) || Boolean.parseBoolean(
        System.getProperty(
            "mattmc.vulkanTerrainParity",
            System.getenv().getOrDefault("MATTMC_VULKAN_TERRAIN_PARITY", "false")
        )
    );
    private static final int MAX_LOGS = Integer.getInteger("mattmc.vulkan.drawStateParity.maxLogs", 256);
    private static final String PIPELINE_FILTER = System.getProperty("mattmc.vulkan.drawStateParity.pipelineFilter", "").trim();
    private static final AtomicInteger LOG_COUNT = new AtomicInteger();

    private VulkanicDrawStateDiagnostics() {
    }

    public static boolean enabled() {
        return ENABLED;
    }

    public static void log(VulkanicDrawStateSnapshot snapshot) {
        if (!ENABLED || snapshot == null || !passesPipelineFilter(snapshot) || LOG_COUNT.getAndIncrement() >= MAX_LOGS) {
            return;
        }

        LOGGER.info("VulkanicDrawStateParity {}", snapshot.toLogFields());
    }

    private static boolean passesPipelineFilter(VulkanicDrawStateSnapshot snapshot) {
        if (PIPELINE_FILTER.isEmpty()) {
            return true;
        }

        for (String token : PIPELINE_FILTER.split(",")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty() && snapshot.pipeline().contains(trimmed)) {
                return true;
            }
        }
        return false;
    }
}

package net.vulkanic.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeRenderLaunchConfigurationTest {
    @Test void native_snapshot_matches_original_java_launch_predicates() {
        String source = System.getenv("MATTMC_RUST_SELECTED_SOURCE_EXECUTION");
        String audit = System.getenv("MATTMC_GRAPHICS_AUDIT");
        assertEquals(source != null, NativeRenderLaunchConfiguration.hasSelectedSourceOverride());
        assertEquals(source != null && (source.equals("1") || source.equalsIgnoreCase("true") || source.equalsIgnoreCase("yes")),
            NativeRenderLaunchConfiguration.selectedSourceExecutionRequested());
        assertEquals(audit != null && (audit.equals("1") || audit.equalsIgnoreCase("true")),
            NativeRenderLaunchConfiguration.graphicsAuditEnabled());
    }
}

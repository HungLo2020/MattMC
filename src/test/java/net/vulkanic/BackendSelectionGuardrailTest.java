package net.vulkanic;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guardrails to ensure Vulkan readiness work does not accidentally introduce
 * production backend-selection callsites outside Vulkanic internals.
 */
public class BackendSelectionGuardrailTest {

    private static final Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir"));
    private static final Path SRC_MAIN_JAVA = PROJECT_ROOT.resolve("src/main/java");
    private static final Path VULKANIC_PACKAGE_ROOT = SRC_MAIN_JAVA.resolve("net/vulkanic");

    @Test
    public void testDefaultInitializationRemainsOpenGL() {
        VulkanicAPI.initialize();
        assertEquals(GraphicsBackendType.OPENGL, VulkanicAPI.getActiveBackendType(),
            "Default initialization must remain OPENGL while Vulkan selection is intentionally disabled");
    }

    @Test
    public void testNoProductionCallsitesSelectVulkanBackendOutsideVulkanicPackage() throws IOException {
        List<String> offenders = new ArrayList<>();

        try (var paths = Files.walk(SRC_MAIN_JAVA)) {
            for (Path file : (Iterable<Path>) paths::iterator) {
                if (!Files.isRegularFile(file) || !file.toString().endsWith(".java")) {
                    continue;
                }

                if (file.startsWith(VULKANIC_PACKAGE_ROOT)) {
                    continue;
                }

                String source = Files.readString(file);
                String scrubbed = stripComments(source);

                if (scrubbed.contains("GraphicsBackendType.VULKAN")
                    || scrubbed.contains("initialize(GraphicsBackendType.VULKAN")) {
                    offenders.add(SRC_MAIN_JAVA.relativize(file).toString());
                }
            }
        }

        assertTrue(offenders.isEmpty(),
            "Production code outside net.vulkanic must not select Vulkan backend yet. Offenders: " + offenders);
    }

    private static String stripComments(String source) {
        return source
            .replaceAll("(?s)/\\*.*?\\*/", "")
            .replaceAll("(?m)//.*$", "");
    }
}

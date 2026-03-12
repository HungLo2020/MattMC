package net.vulkanic;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GraphicsBackendOptionSelectionTest {

    private static final Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir"));

    @AfterEach
    public void tearDown() throws Exception {
        resetBackendState();
    }

    @Test
    public void testNormalizeBackendOptionValue() {
        assertEquals("opengl", VulkanicAPI.normalizeBackendOptionValue(null));
        assertEquals("opengl", VulkanicAPI.normalizeBackendOptionValue(""));
        assertEquals("opengl", VulkanicAPI.normalizeBackendOptionValue("opengl"));
        assertEquals("opengl", VulkanicAPI.normalizeBackendOptionValue("OpenGL"));
        assertEquals("vulkan", VulkanicAPI.normalizeBackendOptionValue("vulkan"));
        assertEquals("vulkan", VulkanicAPI.normalizeBackendOptionValue("  VULKAN  "));
        assertEquals("opengl", VulkanicAPI.normalizeBackendOptionValue("dx12"));
    }

    @Test
    public void testInitializeFromOptionsValueSelectsOpenGLForMissingOrInvalidValues() {
        VulkanicAPI.initializeFromOptionsValue(null);
        assertEquals(GraphicsBackendType.OPENGL, VulkanicAPI.getActiveBackendType());

        resetBackendStateUnchecked();
        VulkanicAPI.initializeFromOptionsValue("garbage-value");
        assertEquals(GraphicsBackendType.OPENGL, VulkanicAPI.getActiveBackendType());

        resetBackendStateUnchecked();
        VulkanicAPI.initializeFromOptionsValue("opengl");
        assertEquals(GraphicsBackendType.OPENGL, VulkanicAPI.getActiveBackendType());
    }

    @Test
    public void testInitializeFromOptionsValueSelectsVulkanWhenRequested() {
        VulkanicAPI.initializeFromOptionsValue("vulkan");
        assertEquals(GraphicsBackendType.VULKAN, VulkanicAPI.getActiveBackendType());
    }

    @Test
    public void testOptionsSourceWiresHiddenGraphicsBackendSetting() throws IOException {
        Path optionsFile = PROJECT_ROOT.resolve("src/main/java/net/minecraft/client/Options.java");
        String source = Files.readString(optionsFile);

        assertTrue(source.contains("private static final String GRAPHICS_BACKEND_OPTION_KEY = \"graphics_backend\";"),
            "Options should define hidden graphics backend key name");
        assertTrue(source.contains("if (string.startsWith(GRAPHICS_BACKEND_OPTION_KEY + \"=\"))"),
            "Options.load should parse graphics_backend=value lines from options.txt");
        assertTrue(source.contains("VulkanicAPI.initializeFromOptionsValue(this.graphicsBackendOptionValue);"),
            "Options.load should initialize backend routing from the hidden option at launch");
        assertTrue(source.contains("printWriter.println(GRAPHICS_BACKEND_OPTION_KEY + \"=\" + this.graphicsBackendOptionValue);"),
            "Options.save should persist the hidden graphics_backend option for manual editing");
    }

    private static void resetBackendState() throws Exception {
        resetBackendStateUnchecked();
    }

    private static void resetBackendStateUnchecked() {
        try {
            for (String fieldName : new String[]{"backend", "rawVulkanBackend"}) {
                Field field = VulkanicAPI.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(null, null);
            }
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException("Failed to reset VulkanicAPI backend state", exception);
        }
    }
}

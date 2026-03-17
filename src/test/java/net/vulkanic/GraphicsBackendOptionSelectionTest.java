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
    private static final String LWJGL_STACK_SIZE_PROPERTY = "org.lwjgl.system.stackSize";
    private final String originalLwjglStackSize = System.getProperty(LWJGL_STACK_SIZE_PROPERTY);

    @AfterEach
    public void tearDown() throws Exception {
        resetBackendState();
        restoreLwjglStackSizeProperty();
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
    public void testInitializeFromOptionsValueRaisesLwjglStackSizeForVulkan() {
        System.setProperty(LWJGL_STACK_SIZE_PROPERTY, "64");

        VulkanicAPI.initializeFromOptionsValue("vulkan");

        assertTrue(
            Integer.parseInt(System.getProperty(LWJGL_STACK_SIZE_PROPERTY)) >= 512,
            "Vulkan selection should raise LWJGL MemoryStack size to avoid native stack exhaustion during VkInstance startup"
        );
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

    @Test
    public void testMainSourceBootstrapsMinimumLwjglStackSize() throws IOException {
        Path mainFile = PROJECT_ROOT.resolve("src/main/java/net/minecraft/client/main/Main.java");
        String source = Files.readString(mainFile);

        assertTrue(source.contains("private static final String LWJGL_STACK_SIZE_PROPERTY = \"org.lwjgl.system.stackSize\";"),
            "Main should define the LWJGL stack-size property key");
        assertTrue(source.contains("ensureMinimumLwjglStackSize();"),
            "Main should raise the LWJGL stack size before client bootstrap begins");
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

    private void restoreLwjglStackSizeProperty() {
        if (this.originalLwjglStackSize == null) {
            System.clearProperty(LWJGL_STACK_SIZE_PROPERTY);
            return;
        }

        System.setProperty(LWJGL_STACK_SIZE_PROPERTY, this.originalLwjglStackSize);
    }
}

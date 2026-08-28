package net.vulkanic;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        String defaultBackend = net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled() ? "vulkan" : "opengl";
        assertEquals(defaultBackend, VulkanicAPI.normalizeBackendOptionValue(null));
        assertEquals(defaultBackend, VulkanicAPI.normalizeBackendOptionValue(""));
        assertEquals(defaultBackend, VulkanicAPI.normalizeBackendOptionValue("opengl"));
        assertEquals(defaultBackend, VulkanicAPI.normalizeBackendOptionValue("OpenGL"));
        assertEquals("vulkan", VulkanicAPI.normalizeBackendOptionValue("vulkan"));
        assertEquals("vulkan", VulkanicAPI.normalizeBackendOptionValue("  VULKAN  "));
        assertEquals(defaultBackend, VulkanicAPI.normalizeBackendOptionValue("dx12"));
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
    public void testVulkanSelectionAdmissionPrecedesCompatibilityBackendConstruction() throws IOException {
        Path apiFile = PROJECT_ROOT.resolve("src/main/java/net/vulkanic/VulkanicAPI.java");
        String source = Files.readString(apiFile);
        int vulkanCase = source.indexOf("case VULKAN:");
        int marker = source.indexOf("RustGalVulkanWholeFrameMode.markVulkanBackendSelected();", vulkanCase);
        int backendConstruction = source.indexOf("rawVulkanBackend = new VulkanBackend();", vulkanCase);

        assertTrue(vulkanCase >= 0, "Vulkan backend initialization branch should remain explicit");
        assertTrue(marker > vulkanCase, "Vulkan selection should admit the Rust whole-frame route");
        assertTrue(backendConstruction > marker,
            "Rust whole-frame admission must be recorded before any Java compatibility backend is constructed");
    }

    @Test
    public void testOptionsSourceWiresHiddenGraphicsBackendSetting() throws IOException {
        Path optionsFile = PROJECT_ROOT.resolve("src/main/java/net/minecraft/client/Options.java");
        String source = Files.readString(optionsFile);

        assertTrue(source.contains("private static final String GRAPHICS_BACKEND_OPTION_KEY = \"graphics_backend\";"),
            "Options should define hidden graphics backend key name");
        assertTrue(source.contains("private final OptionInstance<GraphicsBackendType> graphicsBackend"),
            "Options should expose graphics backend as a normal UI option while keeping the hidden options.txt key");
        assertTrue(source.contains("public OptionInstance<GraphicsBackendType> graphicsBackend()"),
            "Options should expose the graphics backend option for video settings screens");
        assertTrue(source.contains("if (string.startsWith(GRAPHICS_BACKEND_OPTION_KEY + \"=\"))"),
            "Options.load should parse graphics_backend=value lines from options.txt");
        assertTrue(source.contains("VulkanicAPI.initializeFromOptionsValue(this.graphicsBackendOptionValue);"),
            "Options.load should initialize backend routing from the hidden option at launch");
        assertTrue(source.contains("private void setPendingGraphicsBackend(GraphicsBackendType graphicsBackendType)"),
            "Options should provide a pending-value setter for UI changes that does not reinitialize the renderer live");
        assertTrue(source.contains("printWriter.println(GRAPHICS_BACKEND_OPTION_KEY + \"=\" + this.graphicsBackendOptionValue);"),
            "Options.save should persist the hidden graphics_backend option for manual editing");
    }

    @Test
    public void testSodiumVideoSettingsExposeGraphicsBackendRestartOption() throws IOException {
        Path sodiumOptionsFile = PROJECT_ROOT.resolve("src/main/java/net/sodium/client/gui/SodiumGameOptionPages.java");
        String sodiumSource = Files.readString(sodiumOptionsFile);
        assertTrue(sodiumSource.contains("OptionImpl.createBuilder(GraphicsBackendType.class, vanillaOpts)"),
            "Sodium video settings should expose the graphics backend selector");
        assertTrue(sodiumSource.contains(".setBinding((opts, value) -> opts.graphicsBackend().set(value), opts -> opts.graphicsBackend().get())"),
            "Sodium graphics backend selector should write through Minecraft Options so graphics_backend remains authoritative");
        assertTrue(sodiumSource.contains(".setFlags(OptionFlag.REQUIRES_GAME_RESTART)"),
            "Sodium graphics backend selector should warn that a restart is required");

        Path minecraftLangFile = PROJECT_ROOT.resolve("src/main/resources/assets/minecraft/lang/en_us.json");
        String minecraftLangSource = Files.readString(minecraftLangFile);
        assertTrue(minecraftLangSource.contains("\"options.graphicsBackend\": \"Graphics Backend\""),
            "Minecraft lang should define the graphics backend option label");
        assertTrue(minecraftLangSource.contains("Changing this requires restarting the game."),
            "Graphics backend tooltip should communicate the restart requirement");
    }

    @Test
    public void testVideoSettingsMenuUsesBundledSodiumScreen() throws IOException {
        Path optionsScreenFile = PROJECT_ROOT.resolve("src/main/java/net/minecraft/client/gui/screens/options/OptionsScreen.java");
        String optionsScreenSource = Files.readString(optionsScreenFile);
        assertTrue(optionsScreenSource.contains("SodiumOptionsGUI.createScreen(this)"),
            "OptionsScreen should route the video settings button to the bundled Sodium options screen");
        assertFalse(optionsScreenSource.contains("new VideoSettingsScreen"),
            "OptionsScreen should not retain the removed vanilla video settings fallback");

        Path sodiumGuiFile = PROJECT_ROOT.resolve("src/main/java/net/sodium/client/gui/SodiumOptionsGUI.java");
        String sodiumGuiSource = Files.readString(sodiumGuiFile);
        assertFalse(sodiumGuiSource.contains("VideoSettingsScreen"),
            "SodiumOptionsGUI should not expose an escape hatch back to the removed vanilla video settings screen");
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
            net.vulkanic.bridge.RustGalVulkanWholeFrameMode.deactivateRustPresentation();
            net.vulkanic.bridge.RustGalVulkanWholeFrameMode.clearVulkanBackendSelection();
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

package net.vulkanic;

import net.vulkanic.backends.vulkan.VulkanTexture;
import net.vulkanic.backends.vulkan.VulkanTextureView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lifecycle and regression tests for the Vulkan managed texture/image/view
 * implementation ({@link VulkanTexture}, {@link VulkanTextureView}).
 *
 * <p>Tests run entirely in a headless / no-native-GPU environment. Tests that
 * exercise the public VulkanicAPI surface verify fail-hard behaviour via the
 * {@code IllegalStateException} that the Vulkan backend raises when native
 * Vulkan is unavailable. Pure unit tests verify object lifecycle directly
 * without any native Vulkan involvement.
 */
public class VulkanManagedTextureLifecycleTest {

    private static final Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir"));

    @BeforeEach
    public void beforeEach() throws Exception {
        resetBackendState();
    }

    @AfterEach
    public void afterEach() throws Exception {
        resetBackendState();
    }

    // =========================================================================
    // Pure unit tests — no native Vulkan runtime involved
    // =========================================================================

    @Test
    public void testVulkanTextureCloseIsIdempotent() {
        AtomicInteger closeCount = new AtomicInteger(0);
        VulkanTexture texture = new VulkanTexture(
            0xDEAD_BEEFL,   // fake VkImage handle
            0xCAFE_0000L,   // fake VkDeviceMemory handle
            0x1234_5678L,   // fake default VkImageView handle
            VulkanicTexture.USAGE_TEXTURE_BINDING | VulkanicTexture.USAGE_COPY_DST,
            VulkanicTextureFormat.RGBA8,
            256, 256,        // width, height
            1,               // depthOrLayers
            4,               // mipLevels
            "unit-test-texture",
            () -> closeCount.incrementAndGet()
        );

        assertFalse(texture.isClosed(), "New VulkanTexture should not be closed");
        assertEquals("unit-test-texture", texture.getLabel());
        assertEquals(256, texture.getWidth(0));
        assertEquals(128, texture.getWidth(1));
        assertEquals(64,  texture.getWidth(2));
        assertEquals(256, texture.getHeight(0));
        assertEquals(4, texture.getMipLevels());
        assertEquals(1, texture.getDepthOrLayers());
        assertEquals(VulkanicTextureFormat.RGBA8, texture.getVulkanicFormat());

        texture.close();
        texture.close();
        texture.close();

        assertTrue(texture.isClosed(), "VulkanTexture should report closed after close()");
        assertEquals(1, closeCount.get(), "VulkanTexture close callback must run exactly once");
    }

    @Test
    public void testVulkanTextureViewCloseIsIdempotent() {
        AtomicInteger textureCloseCount = new AtomicInteger(0);
        VulkanTexture parentTexture = new VulkanTexture(
            0xAAAA_0001L,
            0xBBBB_0002L,
            0xCCCC_0003L,
            VulkanicTexture.USAGE_TEXTURE_BINDING,
            VulkanicTextureFormat.RED8,
            64, 64, 1, 3,   // 3 mip levels
            "parent-texture",
            () -> textureCloseCount.incrementAndGet()
        );

        AtomicInteger viewCloseCount = new AtomicInteger(0);
        VulkanTextureView view = new VulkanTextureView(
            parentTexture,
            0xDDDD_0004L,   // fake VkImageView handle
            1, 2,            // baseMipLevel=1, mipLevelCount=2
            () -> viewCloseCount.incrementAndGet()
        );

        assertFalse(view.isClosed(), "New VulkanTextureView should not be closed");
        assertEquals(1, view.getBaseMipLevel());
        assertEquals(2, view.getMipLevelCount());
        assertSame(parentTexture, view.texture());
        // getWidth/getHeight delegates to parent texture, offset by baseMipLevel
        assertEquals(32, view.getWidth(0)); // parent.getWidth(1) = 64 >> 1 = 32
        assertEquals(16, view.getHeight(1)); // parent.getHeight(2) = 64 >> 2 = 16

        view.close();
        view.close();
        view.close();

        assertTrue(view.isClosed(), "VulkanTextureView should report closed after close()");
        assertEquals(1, viewCloseCount.get(), "VulkanTextureView close callback must run exactly once");
        // Closing a view must NOT affect the parent texture
        assertFalse(parentTexture.isClosed(), "Closing a VulkanTextureView must not close the parent texture");
        assertEquals(0, textureCloseCount.get(), "Parent texture close callback must not be triggered by view close");
    }

    @Test
    public void testVulkanTextureViewValidatesMipRange() {
        VulkanTexture parentTexture = new VulkanTexture(
            1L, 2L, 3L,
            VulkanicTexture.USAGE_TEXTURE_BINDING,
            VulkanicTextureFormat.RGBA8,
            128, 128, 1, 3,  // 3 mip levels
            "range-test-texture",
            () -> {}
        );

        // Valid: all 3 mip levels
        VulkanTextureView fullView = new VulkanTextureView(parentTexture, 4L, 0, 3, () -> {});
        assertEquals(0, fullView.getBaseMipLevel());
        assertEquals(3, fullView.getMipLevelCount());

        // Invalid: exceeds mip count
        assertThrows(IllegalArgumentException.class,
            () -> new VulkanTextureView(parentTexture, 5L, 0, 4, () -> {}),
            "Should reject mip range [0,4) for a 3-mip texture");

        // Invalid: negative base level
        assertThrows(IllegalArgumentException.class,
            () -> new VulkanTextureView(parentTexture, 6L, -1, 1, () -> {}),
            "Should reject negative baseMipLevel");

        // Invalid: zero mip count
        assertThrows(IllegalArgumentException.class,
            () -> new VulkanTextureView(parentTexture, 7L, 0, 0, () -> {}),
            "Should reject mipLevelCount=0");
    }

    @Test
    public void testVulkanTextureMipDimensionScaling() {
        VulkanTexture texture = new VulkanTexture(
            1L, 2L, 3L,
            VulkanicTexture.USAGE_TEXTURE_BINDING,
            VulkanicTextureFormat.RGBA8,
            512, 256, 1, 5,  // 5 mip levels
            "mip-test",
            () -> {}
        );

        // Each level halves both dimensions; floor at 1
        assertEquals(512, texture.getWidth(0));
        assertEquals(256, texture.getWidth(1));
        assertEquals(128, texture.getWidth(2));
        assertEquals(64,  texture.getWidth(3));
        assertEquals(32,  texture.getWidth(4));

        assertEquals(256, texture.getHeight(0));
        assertEquals(128, texture.getHeight(1));
        assertEquals(64,  texture.getHeight(2));
        assertEquals(32,  texture.getHeight(3));
        assertEquals(16,  texture.getHeight(4));
    }

    // =========================================================================
    // Fail-hard tests — Vulkan backend selected but no native GPU available
    // =========================================================================

    @Test
    public void testVulkanManagedTextureCreationFailsHardWhenNativeVulkanUnavailable() {
        VulkanicAPI.initialize(GraphicsBackendType.VULKAN);

        IllegalStateException failure = assertThrows(
            IllegalStateException.class,
            () -> VulkanicAPI.createManagedTexture(
                "vulkan-texture",
                VulkanicTexture.USAGE_TEXTURE_BINDING | VulkanicTexture.USAGE_COPY_DST,
                VulkanicTextureFormat.RGBA8,
                128, 128, 1, 4
            ),
            "Vulkan-selected backend should fail hard when native Vulkan runtime is unavailable"
        );
        assertTrue(failure.getMessage().contains("Readiness report:"),
            "Error message should contain 'Readiness report:' — got: " + failure.getMessage());
    }

    @Test
    public void testVulkanManagedTextureViewCreationFailsHardWhenNativeVulkanUnavailable() {
        VulkanicAPI.initialize(GraphicsBackendType.VULKAN);

        // Build a standalone VulkanTexture with fake handles — no native Vulkan needed
        VulkanTexture fakeTexture = new VulkanTexture(
            0x1111L, 0x2222L, 0x3333L,
            VulkanicTexture.USAGE_TEXTURE_BINDING,
            VulkanicTextureFormat.RGBA8,
            64, 64, 1, 2,
            "fake-texture",
            () -> {}
        );

        // Full-range variant
        IllegalStateException fullRangeFailure = assertThrows(
            IllegalStateException.class,
            () -> VulkanicAPI.createManagedTextureView(fakeTexture),
            "createManagedTextureView(texture) should fail hard when native Vulkan is unavailable"
        );
        assertTrue(fullRangeFailure.getMessage().contains("Readiness report:"),
            "Full-range view error should contain 'Readiness report:' — got: " + fullRangeFailure.getMessage());

        // Mip-range variant
        IllegalStateException rangedFailure = assertThrows(
            IllegalStateException.class,
            () -> VulkanicAPI.createManagedTextureView(fakeTexture, 0, 1),
            "createManagedTextureView(texture, base, count) should fail hard when native Vulkan is unavailable"
        );
        assertTrue(rangedFailure.getMessage().contains("Readiness report:"),
            "Ranged view error should contain 'Readiness report:' — got: " + rangedFailure.getMessage());
    }

    @Test
    public void testVulkanTextureCreationRejectsInvalidArguments() {
        VulkanicAPI.initialize(GraphicsBackendType.VULKAN);

        // Zero width — should fail with IllegalArgumentException before even hitting ensureNativeReady
        assertThrows(IllegalArgumentException.class,
            () -> VulkanicAPI.createManagedTexture("bad", VulkanicTexture.USAGE_TEXTURE_BINDING,
                VulkanicTextureFormat.RGBA8, 0, 64, 1, 1),
            "Zero width must be rejected");

        assertThrows(IllegalArgumentException.class,
            () -> VulkanicAPI.createManagedTexture("bad", VulkanicTexture.USAGE_TEXTURE_BINDING,
                VulkanicTextureFormat.RGBA8, 64, 0, 1, 1),
            "Zero height must be rejected");

        assertThrows(IllegalArgumentException.class,
            () -> VulkanicAPI.createManagedTexture("bad", VulkanicTexture.USAGE_TEXTURE_BINDING,
                VulkanicTextureFormat.RGBA8, 64, 64, 1, 0),
            "Zero mipLevels must be rejected");

        assertThrows(IllegalArgumentException.class,
            () -> VulkanicAPI.createManagedTexture("bad", VulkanicTexture.USAGE_TEXTURE_BINDING,
                null, 64, 64, 1, 1),
            "Null format must be rejected");
    }

    @Test
    public void testVulkanTextureCreationRejectsInvalidCubemapLayerCounts() {
        VulkanicAPI.initialize(GraphicsBackendType.VULKAN);

        int cubemapUsage = VulkanicTexture.USAGE_TEXTURE_BINDING | VulkanicTexture.USAGE_CUBEMAP_COMPATIBLE;

        assertThrows(IllegalArgumentException.class,
            () -> VulkanicAPI.createManagedTexture("bad-cubemap", cubemapUsage,
                VulkanicTextureFormat.RGBA8, 64, 64, 1, 1),
            "Cubemap textures must reject layer counts smaller than 6");

        assertThrows(IllegalArgumentException.class,
            () -> VulkanicAPI.createManagedTexture("bad-cubemap", cubemapUsage,
                VulkanicTextureFormat.RGBA8, 64, 64, 7, 1),
            "Cubemap textures must reject layer counts that are not a multiple of 6");
    }

    @Test
    public void testVulkanBackendSourceCreatesCubeCompatibleImagesAndViews() throws Exception {
        Path backendFile = PROJECT_ROOT.resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java");
        String source = Files.readString(backendFile);

        assertTrue(source.contains("VK10.VK_IMAGE_CREATE_CUBE_COMPATIBLE_BIT"),
            "Vulkan backend should mark cubemap-compatible textures with VK_IMAGE_CREATE_CUBE_COMPATIBLE_BIT");
        assertTrue(source.contains("VK10.VK_IMAGE_VIEW_TYPE_CUBE"),
            "Vulkan backend should create cube image views for cubemap-compatible textures");
        assertTrue(source.contains("legacyTextureLayerCount"),
            "Vulkan backend should preserve six-layer legacy cubemap storage instead of collapsing it to a single 2D layer");
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private static void resetBackendState() throws Exception {
        for (String fieldName : new String[]{"backend", "rawVulkanBackend"}) {
            Field field;
            try {
                field = VulkanicAPI.class.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                continue;
            }
            field.setAccessible(true);
            field.set(null, null);
        }
    }
}

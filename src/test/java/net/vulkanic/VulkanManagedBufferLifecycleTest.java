package net.vulkanic;

import net.vulkanic.backends.vulkan.VulkanBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VulkanManagedBufferLifecycleTest {

    @BeforeEach
    public void beforeEach() throws Exception {
        resetBackendState();
    }

    @AfterEach
    public void afterEach() throws Exception {
        resetBackendState();
    }

    @Test
    public void testVulkanBufferCloseAndMappedViewCloseAreIdempotent() {
        AtomicInteger closeCount = new AtomicInteger(0);
        VulkanBuffer buffer = new VulkanBuffer(
            0xABCDL,
            0x1234L,
            VulkanicBuffer.USAGE_MAP_READ | VulkanicBuffer.USAGE_MAP_WRITE,
            64,
            "unit-buffer",
            () -> closeCount.incrementAndGet()
        );

        assertFalse(buffer.isClosed());
        buffer.close();
        buffer.close();
        assertTrue(buffer.isClosed());
        assertEquals(1, closeCount.get(), "VulkanBuffer close callback should run exactly once");

        AtomicInteger unmapCount = new AtomicInteger(0);
        VulkanBuffer.VulkanMappedView mappedView = new VulkanBuffer.VulkanMappedView(
            ByteBuffer.allocateDirect(16),
            () -> unmapCount.incrementAndGet()
        );

        mappedView.close();
        mappedView.close();
        assertEquals(1, unmapCount.get(), "Mapped view unmap callback should run exactly once");
    }

    @Test
    public void testVulkanManagedBufferCreationFailsHardWhenNativeVulkanUnavailable() {
        VulkanicAPI.initialize(GraphicsBackendType.VULKAN);

        IllegalStateException sizeVariantFailure = assertThrows(
            IllegalStateException.class,
            () -> VulkanicAPI.createManagedBuffer(() -> "vulkan-buffer", VulkanicBuffer.USAGE_VERTEX, 64),
            "Vulkan-selected backend should fail hard when native Vulkan runtime is unavailable"
        );
        assertTrue(sizeVariantFailure.getMessage().contains("Readiness report:"));

        ByteBuffer initialData = ByteBuffer.allocateDirect(8);
        initialData.putLong(42L).flip();
        IllegalStateException dataVariantFailure = assertThrows(
            IllegalStateException.class,
            () -> VulkanicAPI.createManagedBuffer(() -> "vulkan-buffer-data", VulkanicBuffer.USAGE_VERTEX, initialData),
            "Data-initialized managed buffer creation should also fail hard when native Vulkan runtime is unavailable"
        );
        assertTrue(dataVariantFailure.getMessage().contains("Readiness report:"));
    }

    @Test
    public void testVulkanManagedBufferMappingFailsHardWhenNativeVulkanUnavailable() {
        VulkanicAPI.initialize(GraphicsBackendType.VULKAN);

        VulkanBuffer standaloneBuffer = new VulkanBuffer(
            0x55L,
            0x66L,
            VulkanicBuffer.USAGE_MAP_READ | VulkanicBuffer.USAGE_MAP_WRITE,
            32,
            "standalone",
            () -> {
            }
        );

        IllegalStateException mappingFailure = assertThrows(
            IllegalStateException.class,
            () -> VulkanicAPI.mapManagedBuffer(standaloneBuffer, true, false),
            "Vulkan-selected backend should fail hard on mapManagedBuffer when native runtime is unavailable"
        );
        assertTrue(mappingFailure.getMessage().contains("Readiness report:"));
    }

    private static void resetBackendState() throws Exception {
        Field backendField = VulkanicAPI.class.getDeclaredField("backend");
        backendField.setAccessible(true);
        backendField.set(null, null);

        Field rawVulkanBackendField = VulkanicAPI.class.getDeclaredField("rawVulkanBackend");
        rawVulkanBackendField.setAccessible(true);
        rawVulkanBackendField.set(null, null);
    }
}

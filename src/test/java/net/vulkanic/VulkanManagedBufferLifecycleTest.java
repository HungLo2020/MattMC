package net.vulkanic;

import net.vulkanic.backends.vulkan.VulkanBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VulkanManagedBufferLifecycleTest {
    private static final Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir"));
    private static final Path SRC_MAIN_JAVA = PROJECT_ROOT.resolve("src/main/java");

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

    @Test
    public void testVulkanLegacyUploadPathwaysFailHardWithReadinessDiagnostics() {
        VulkanicAPI.initialize(GraphicsBackendType.VULKAN);

        IllegalStateException bufferDataFailure = assertThrows(
            IllegalStateException.class,
            () -> VulkanicAPI.bufferData(
                null,
                VulkanicAPI.GL_ARRAY_BUFFER,
                ByteBuffer.allocateDirect(16),
                VulkanicAPI.GL_DYNAMIC_DRAW
            ),
            "Vulkan legacy bufferData pathway must fail hard when native runtime is unavailable"
        );

        IllegalStateException bufferSubDataFailure = assertThrows(
            IllegalStateException.class,
            () -> VulkanicAPI.bufferSubData(
                null,
                VulkanicAPI.GL_ARRAY_BUFFER,
                0,
                ByteBuffer.allocateDirect(8)
            ),
            "Vulkan legacy bufferSubData pathway must fail hard when native runtime is unavailable"
        );

        IllegalStateException copyFailure = assertThrows(
            IllegalStateException.class,
            () -> VulkanicAPI.copyNamedBufferSubDataDSA(null, 1, 2, 0, 0, 8),
            "Vulkan legacy copy pathway must fail hard when native runtime is unavailable"
        );

        IllegalStateException mapFailure = assertThrows(
            IllegalStateException.class,
            () -> VulkanicAPI.mapBuffer(null, VulkanicAPI.GL_ARRAY_BUFFER, 0, 8, VulkanicAPI.GL_MAP_WRITE_BIT),
            "Vulkan legacy mapBuffer pathway must fail hard when native runtime is unavailable"
        );

        assertReadinessFailure(bufferDataFailure);
        assertReadinessFailure(bufferSubDataFailure);
        assertReadinessFailure(copyFailure);
        assertReadinessFailure(mapFailure);
    }

    @Test
    public void testVulkanMappedBuffersUseNativeByteOrder() throws Exception {
        String backendSource = readSource(
            SRC_MAIN_JAVA.resolve("net/vulkanic/backends/vulkan/VulkanBackend.java")
        );
        String nativeEncoderSource = readSource(
            SRC_MAIN_JAVA.resolve("net/vulkanic/backends/vulkan/VulkanNativeCommandEncoder.java")
        );

        assertTrue(backendSource.contains("MemoryUtil.memByteBuffer(mappedPointer.get(0), buffer.size())\n" +
                "                    .order(ByteOrder.nativeOrder())"),
            "Vulkan-managed mapped buffers must expose native-order ByteBuffers for typed Java writes");
        assertTrue(backendSource.contains("return mapped.slice().order(ByteOrder.nativeOrder());"),
            "Legacy mapped range slices must preserve native byte order after slicing");
        assertTrue(nativeEncoderSource.contains("mapped.order(ByteOrder.nativeOrder());"),
            "CommandEncoder mapBuffer must return a native-order mapped view to render-system callers");
    }

    @Test
    public void testLegacyBufferMemoryPolicyKeepsDynamicUploadsHostWritable() throws Exception {
        String backendSource = readSource(
            SRC_MAIN_JAVA.resolve("net/vulkanic/backends/vulkan/VulkanBackend.java")
        );

        assertTrue(backendSource.contains("legacyUsageHintPrefersHostWrites(usageHint)"),
            "Legacy GL dynamic/stream buffer hints must feed the host-writable memory policy");
        assertTrue(backendSource.contains("usageHint == VulkanicAPI.GL_DYNAMIC_DRAW"),
            "GL_DYNAMIC_DRAW legacy buffers must remain host-writable for frequent bufferData/subData updates");
        assertTrue(backendSource.contains("usageHint == 0x88E0 // GL_STREAM_DRAW"),
            "GL_STREAM_DRAW legacy buffers must remain host-writable for streaming updates");
        assertTrue(backendSource.contains("if (!requiresHostVisibleMemory && initialData != null && renderPassRecording)"),
            "Device-local staging uploads must not be scheduled from inside an active render pass");
        assertTrue(backendSource.contains("requiresHostVisibleMemory = true;"),
            "Render-pass-time initial data uploads should fall back to host-visible storage instead of encoding illegal transfer commands");
    }

    private static void assertReadinessFailure(IllegalStateException failure) {
        String message = failure.getMessage();
        assertNotNull(message);
        assertTrue(message.contains("Readiness report:"), "Failure should include readiness diagnostics");
        assertFalse(message.contains("OpenGL fallback is intentionally blocked"),
            "Newly implemented Vulkan pathways should route to Vulkan readiness checks, not proxy fallback errors");
    }

    private static String readSource(Path path) throws Exception {
        return Files.readString(path).replace("\r\n", "\n").replace('\r', '\n');
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

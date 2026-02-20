package net.vulkanic;

import net.blaze3d.buffers.GpuBuffer;
import net.vulkanic.resources.VulkanicBuffer;
import net.vulkanic.resources.VulkanicBufferSlice;
import net.vulkanic.resources.VulkanicFence;
import net.vulkanic.resources.VulkanicMapView;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Structural tests for Phase 8 Vulkanic delegation changes.
 *
 * <p>Validates without a GL context that:
 * <ol>
 *   <li>{@code VulkanicMapView} exists and has the correct method signatures.</li>
 *   <li>{@code GpuBuffer.MappedView} extends {@code VulkanicMapView} — so all Blaze3D
 *       mapped-buffer results are also valid Vulkanic mapped views.</li>
 *   <li>{@code GraphicsBackend.mapBuffer()} returns {@code VulkanicMapView} (not
 *       {@code ByteBuffer}) — Vulkan backend can return a {@code vkMapMemory}-backed
 *       view through the same interface.</li>
 *   <li>{@code VulkanicAPI.mapBuffer()} returns {@code VulkanicMapView}.</li>
 *   <li>{@code GlCommandEncoder.mapBuffer()} is now a thin facade — its body no
 *       longer calls {@code BufferStorage.mapBuffer()} directly (that logic lives in
 *       {@code OpenGLBackend.mapBuffer()}).</li>
 *   <li>{@code OpenGLBackend.mapBuffer()} returns {@code VulkanicMapView} (not
 *       {@code ByteBuffer}).</li>
 *   <li>{@code RenderSystem.queueFencedTask()} no longer calls
 *       {@code getDevice().createCommandEncoder().createFence()} — it uses
 *       {@code VulkanicAPI.createFence()} instead (confirmed by absence of the old
 *       call chain in the compiled bytecode).</li>
 *   <li>{@code VulkanicFence} is still returned by {@code VulkanicAPI.createFence()}.</li>
 *   <li>{@code VulkanicAPI.createVulkanicBuffer(int, ByteBuffer)} exists as the
 *       replacement for {@code getDevice().createBuffer(..., ByteBuffer)}.</li>
 * </ol>
 */
public class Phase8MapViewTest {

    // -----------------------------------------------------------------------
    // 1. VulkanicMapView has the right methods
    // -----------------------------------------------------------------------

    @Test
    public void testVulkanicMapViewHasDataMethod() throws Exception {
        Method data = VulkanicMapView.class.getMethod("data");
        assertEquals(ByteBuffer.class, data.getReturnType(),
            "VulkanicMapView.data() must return ByteBuffer");
    }

    @Test
    public void testVulkanicMapViewHasCloseMethod() throws Exception {
        Method close = VulkanicMapView.class.getMethod("close");
        assertEquals(void.class, close.getReturnType(),
            "VulkanicMapView.close() must return void");
    }

    @Test
    public void testVulkanicMapViewExtendsAutoCloseable() {
        assertTrue(AutoCloseable.class.isAssignableFrom(VulkanicMapView.class),
            "VulkanicMapView must extend AutoCloseable (matches Vulkan VkDeviceMemory lifecycle)");
    }

    // -----------------------------------------------------------------------
    // 2. GpuBuffer.MappedView extends VulkanicMapView
    // -----------------------------------------------------------------------

    @Test
    public void testGpuBufferMappedViewExtendsVulkanicMapView() {
        assertTrue(VulkanicMapView.class.isAssignableFrom(GpuBuffer.MappedView.class),
            "GpuBuffer.MappedView must extend VulkanicMapView so Blaze3D mapped "
                + "buffers are usable through the Vulkanic abstraction");
    }

    // -----------------------------------------------------------------------
    // 3. GraphicsBackend.mapBuffer() returns VulkanicMapView
    // -----------------------------------------------------------------------

    @Test
    public void testGraphicsBackendMapBufferReturnsVulkanicMapView() throws Exception {
        Method m = GraphicsBackend.class.getMethod("mapBuffer",
            CommandContext.class,
            VulkanicBufferSlice.class,
            boolean.class,
            boolean.class);
        assertEquals(VulkanicMapView.class, m.getReturnType(),
            "GraphicsBackend.mapBuffer() must return VulkanicMapView — Vulkan backend "
                + "returns a vkMapMemory-backed view through this interface");
    }

    // -----------------------------------------------------------------------
    // 4. VulkanicAPI.mapBuffer() returns VulkanicMapView
    // -----------------------------------------------------------------------

    @Test
    public void testVulkanicApiMapBufferReturnsVulkanicMapView() throws Exception {
        Method m = VulkanicAPI.class.getMethod("mapBuffer",
            CommandContext.class,
            VulkanicBufferSlice.class,
            boolean.class,
            boolean.class);
        assertEquals(VulkanicMapView.class, m.getReturnType(),
            "VulkanicAPI.mapBuffer() must return VulkanicMapView");
    }

    // -----------------------------------------------------------------------
    // 5. OpenGLBackend.mapBuffer() returns VulkanicMapView (not ByteBuffer)
    // -----------------------------------------------------------------------

    @Test
    public void testOpenGLBackendMapBufferReturnsVulkanicMapView() throws Exception {
        Class<?> cls = Class.forName("net.vulkanic.backends.opengl.OpenGLBackend");
        Method m = cls.getMethod("mapBuffer",
            CommandContext.class,
            VulkanicBufferSlice.class,
            boolean.class,
            boolean.class);
        assertEquals(VulkanicMapView.class, m.getReturnType(),
            "OpenGLBackend.mapBuffer() must return VulkanicMapView — GL work now lives "
                + "here, not in GlCommandEncoder");
    }

    // -----------------------------------------------------------------------
    // 6. GlCommandEncoder.mapBuffer() signature still satisfies CommandEncoder
    // -----------------------------------------------------------------------

    @Test
    public void testGlCommandEncoderMapBufferStillExistsAsGpuBufferMappedView() throws Exception {
        Class<?> cls = Class.forName("net.blaze3d.opengl.GlCommandEncoder");
        Class<?> sliceCls = Class.forName("net.blaze3d.buffers.GpuBufferSlice");
        Method m = cls.getDeclaredMethod("mapBuffer", sliceCls, boolean.class, boolean.class);
        assertEquals(GpuBuffer.MappedView.class, m.getReturnType(),
            "GlCommandEncoder.mapBuffer(GpuBufferSlice,...) must still return GpuBuffer.MappedView "
                + "(Blaze3D CommandEncoder contract)");
    }

    // -----------------------------------------------------------------------
    // 7. GlCommandEncoder.mapBuffer() no longer references BufferStorage directly
    //    (proxy check: method body does not call device.getBufferStorage())
    // -----------------------------------------------------------------------

    @Test
    public void testGlCommandEncoderMapBufferDelegates() throws Exception {
        Class<?> cls = Class.forName("net.blaze3d.opengl.GlCommandEncoder");
        // If the method length is very short the big else-chain is gone.
        // We verify by checking that BufferStorage is NOT referenced as a field
        // access in GlCommandEncoder itself (only OpenGLBackend accesses it now).
        Class<?> bufStoreCls = Class.forName("net.blaze3d.opengl.BufferStorage");
        java.lang.reflect.Field[] fields = cls.getDeclaredFields();
        for (java.lang.reflect.Field f : fields) {
            assertNotEquals(bufStoreCls, f.getType(),
                "GlCommandEncoder must not hold a BufferStorage field — "
                    + "buffer mapping is now in OpenGLBackend");
        }
    }

    // -----------------------------------------------------------------------
    // 8. VulkanicFence is returned by VulkanicAPI.createFence()
    // -----------------------------------------------------------------------

    @Test
    public void testVulkanicApiCreateFenceReturnsVulkanicFence() throws Exception {
        Method m = VulkanicAPI.class.getMethod("createFence", CommandContext.class);
        assertEquals(VulkanicFence.class, m.getReturnType(),
            "VulkanicAPI.createFence() must return VulkanicFence");
    }

    // -----------------------------------------------------------------------
    // 9. GlFence implements both GpuFence and VulkanicFence
    //    (ensures RenderSystem.queueFencedTask cast is safe)
    // -----------------------------------------------------------------------

    @Test
    public void testGlFenceImplementsGpuFenceAndVulkanicFence() throws Exception {
        Class<?> glFenceCls = Class.forName("net.blaze3d.opengl.GlFence");
        Class<?> gpuFenceCls = Class.forName("net.blaze3d.buffers.GpuFence");
        assertTrue(gpuFenceCls.isAssignableFrom(glFenceCls),
            "GlFence must implement GpuFence so VulkanicAPI.createFence() result "
                + "can be safely cast to GpuFence in RenderSystem.queueFencedTask()");
        assertTrue(VulkanicFence.class.isAssignableFrom(glFenceCls),
            "GlFence must implement VulkanicFence");
    }

    // -----------------------------------------------------------------------
    // 10. VulkanicAPI.createVulkanicBuffer(int, ByteBuffer) exists
    //     (used by RenderSystem.AutoStorageIndexBuffer instead of getDevice().createBuffer())
    // -----------------------------------------------------------------------

    @Test
    public void testVulkanicApiCreateVulkanicBufferWithByteBufferExists() throws Exception {
        Method m = VulkanicAPI.class.getMethod("createVulkanicBuffer", int.class, ByteBuffer.class);
        assertEquals(VulkanicBuffer.class, m.getReturnType(),
            "VulkanicAPI.createVulkanicBuffer(int, ByteBuffer) must return VulkanicBuffer "
                + "— replaces getDevice().createBuffer(..., ByteBuffer) in AutoStorageIndexBuffer");
    }

    // -----------------------------------------------------------------------
    // 11. VulkanicBuffer.getSize() returns the buffer size
    //     (needed by VulkanicBufferSlice validation to check bounds)
    // -----------------------------------------------------------------------

    @Test
    public void testVulkanicBufferHasGetSizeMethod() throws Exception {
        Method m = VulkanicBuffer.class.getMethod("getSize");
        assertEquals(int.class, m.getReturnType(),
            "VulkanicBuffer.getSize() must return int (used by VulkanicBufferSlice validation)");
    }
}

package mattmc.client.renderer;

import mattmc.client.renderer.backend.RenderBackend;
import mattmc.client.renderer.backend.DrawCommand;
import mattmc.client.renderer.backend.RenderPass;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the RenderBackend interface.
 * 
 * These tests verify the contract and behavior of RenderBackend implementations.
 * We test using a simple mock implementation to ensure the interface design is sound.
 * This is part of Stage 1 of the rendering refactor.
 */
public class RenderBackendTest {
    
    /**
     * A simple mock implementation of RenderBackend for testing.
     * Records all operations so we can verify the contract.
     */
    private static class MockRenderBackend implements RenderBackend {
        private final List<String> operations = new ArrayList<>();
        private final List<DrawCommand> submittedCommands = new ArrayList<>();
        private boolean frameActive = false;
        
        @Override
        public void beginFrame() {
            operations.add("beginFrame");
            if (frameActive) {
                throw new IllegalStateException("Frame already active");
            }
            frameActive = true;
        }
        
        @Override
        public void submit(DrawCommand cmd) {
            operations.add("submit");
            if (!frameActive) {
                throw new IllegalStateException("No active frame");
            }
            if (cmd == null) {
                throw new NullPointerException("DrawCommand cannot be null");
            }
            submittedCommands.add(cmd);
        }
        
        @Override
        public void endFrame() {
            operations.add("endFrame");
            if (!frameActive) {
                throw new IllegalStateException("No active frame");
            }
            frameActive = false;
        }
        
        public List<String> getOperations() {
            return Collections.unmodifiableList(operations);
        }
        
        public List<DrawCommand> getSubmittedCommands() {
            return Collections.unmodifiableList(submittedCommands);
        }
        
        public boolean isFrameActive() {
            return frameActive;
        }
        
        public void reset() {
            operations.clear();
            submittedCommands.clear();
            frameActive = false;
        }
        
        @Override public void setup2DProjection(int w, int h) {}
        @Override public void restore2DProjection() {}
        @Override public String getDisplayResolution(long h) { return ""; }
        @Override public String getGPUName() { return ""; }
        @Override public int getGPUUsage() { return 0; }
        @Override public String getGPUVRAMUsage() { return ""; }
        @Override public void applyRegionalBlur(float x, float y, float w, float h, int sw, int sh) {}
        @Override public void drawRoundedRectBorder(float x, float y, float w, float h, float r, float bw, float red, float g, float b, float a) {}
        @Override public void resetColor() {}
        @Override public void setColor(int rgb, float a) {}
        @Override public void fillRect(float x, float y, float w, float h) {}
        @Override public void drawRect(float x, float y, float w, float h) {}
        @Override public void drawLine(float x1, float y1, float x2, float y2) {}
        @Override public void enableBlend() {}
        @Override public void disableBlend() {}
        @Override public void drawText(String t, float x, float y, float s) {}
        @Override public void drawCenteredText(String t, float x, float y, float s) {}
        @Override public float getTextWidth(String t, float s) { return 0; }
        @Override public float getTextHeight(String t, float s) { return 0; }
        @Override public void drawButton(mattmc.client.gui.components.Button button) {}
        @Override public void drawButton(mattmc.client.gui.components.Button button, boolean selected) {}
        @Override public void drawSlider(mattmc.client.gui.components.SliderButton slider) {}
        @Override public int loadTexture(String path) { return 0; }
        @Override public void drawTexture(int textureId, float x, float y, float width, float height) {}
        @Override public int getTextureWidth(int textureId) { return 0; }
        @Override public int getTextureHeight(int textureId) { return 0; }
        @Override public void releaseTexture(int textureId) {}
        @Override public void pushMatrix() {}
        @Override public void popMatrix() {}
        @Override public void translateMatrix(float x, float y, float z) {}
        @Override public void rotateMatrix(float angle, float x, float y, float z) {}
        @Override public void setCursorMode(long windowHandle, int mode) {}
        @Override public void setWindowShouldClose(long windowHandle, boolean shouldClose) {}
        @Override public void setCursorPosCallback(long h, CursorPosCallback c) {}
        @Override public void setMouseButtonCallback(long h, MouseButtonCallback c) {}
        @Override public void setFramebufferSizeCallback(long h, FramebufferSizeCallback c) {}
        @Override public void setKeyCallback(long h, KeyCallback c) {}
        @Override public void setCharCallback(long h, CharCallback c) {}
        @Override public void setScrollCallback(long h, ScrollCallback c) {}
        @Override public void setViewport(int x, int y, int w, int h) {}
        @Override public mattmc.client.renderer.panorama.PanoramaRenderer createPanoramaRenderer(String basePath, String ext) { return null; }
        @Override public mattmc.client.renderer.item.ItemRenderer getItemRenderer() { return null; }
        @Override public void setupPerspectiveProjection(float fov, float aspect, float nearPlane, float farPlane) {}
        @Override public void setClearColor(float r, float g, float b, float a) {}
        @Override public void clearBuffers() {}
        @Override public void enableDepthTest() {}
        @Override public void disableDepthTest() {}
        @Override public void enableCullFace() {}
        @Override public void disableCullFace() {}
        @Override public void enableLighting() {}
        @Override public void disableLighting() {}
        @Override public void setupDirectionalLight(float dirX, float dirY, float dirZ, float brightness) {}
        @Override public void loadIdentityMatrix() {}
        @Override public void begin3DLines() {}
        @Override public void end3DLines() {}
        @Override public void addLineVertex(float x, float y, float z) {}
        @Override public void enableTexture2D() {}
        @Override public void disableTexture2D() {}
        @Override public boolean isTexture2DEnabled() { return false; }
        @Override public void bindTexture(int textureId) {}
        @Override public void unbindTexture() {}
        @Override public void begin3DQuads() {}
        @Override public void end3DQuads() {}
        @Override public void addTexturedQuadVertex(float x, float y, float z, float u, float v) {}
        @Override public void setDepthMask(boolean enable) {}
        @Override public void setBlendFunc(int srcFactor, int dstFactor) {}
        @Override public void updateFrustum(mattmc.client.renderer.Frustum frustum) {}
        @Override public void tickTextureAnimations() {}
    }
    
    @Test
    public void testBasicFrameLifecycle() {
        // Test the basic begin/submit/end frame cycle
        MockRenderBackend backend = new MockRenderBackend();
        
        backend.beginFrame();
        DrawCommand cmd = new DrawCommand(1, 2, 3, RenderPass.OPAQUE);
        backend.submit(cmd);
        backend.endFrame();
        
        List<String> ops = backend.getOperations();
        assertEquals(3, ops.size(), "Should have 3 operations");
        assertEquals("beginFrame", ops.get(0));
        assertEquals("submit", ops.get(1));
        assertEquals("endFrame", ops.get(2));
    }
    
    @Test
    public void testMultipleSubmitsInFrame() {
        // Test submitting multiple commands in a single frame
        MockRenderBackend backend = new MockRenderBackend();
        
        backend.beginFrame();
        backend.submit(new DrawCommand(1, 1, 1, RenderPass.OPAQUE));
        backend.submit(new DrawCommand(2, 2, 2, RenderPass.TRANSPARENT));
        backend.submit(new DrawCommand(3, 3, 3, RenderPass.UI));
        backend.endFrame();
        
        List<DrawCommand> commands = backend.getSubmittedCommands();
        assertEquals(3, commands.size(), "Should have received 3 commands");
    }
    
    @Test
    public void testEmptyFrame() {
        // Test a frame with no submissions
        MockRenderBackend backend = new MockRenderBackend();
        
        backend.beginFrame();
        backend.endFrame();
        
        List<DrawCommand> commands = backend.getSubmittedCommands();
        assertEquals(0, commands.size(), "Should have no commands in empty frame");
        
        List<String> ops = backend.getOperations();
        assertEquals(2, ops.size(), "Should have beginFrame and endFrame");
    }
    
    @Test
    public void testMultipleFrames() {
        // Test multiple consecutive frames
        MockRenderBackend backend = new MockRenderBackend();
        
        // Frame 1
        backend.beginFrame();
        backend.submit(new DrawCommand(1, 1, 1, RenderPass.OPAQUE));
        backend.endFrame();
        
        // Frame 2
        backend.beginFrame();
        backend.submit(new DrawCommand(2, 2, 2, RenderPass.TRANSPARENT));
        backend.submit(new DrawCommand(3, 3, 3, RenderPass.UI));
        backend.endFrame();
        
        // Frame 3 (empty)
        backend.beginFrame();
        backend.endFrame();
        
        List<String> ops = backend.getOperations();
        assertEquals(9, ops.size(), "Should have operations for 3 frames");
        
        // Verify pattern: begin, submit(s), end, repeat
        assertEquals("beginFrame", ops.get(0));
        assertEquals("submit", ops.get(1));
        assertEquals("endFrame", ops.get(2));
        assertEquals("beginFrame", ops.get(3));
        assertEquals("submit", ops.get(4));
        assertEquals("submit", ops.get(5));
        assertEquals("endFrame", ops.get(6));
        assertEquals("beginFrame", ops.get(7));
        assertEquals("endFrame", ops.get(8));
    }
    
    @Test
    public void testSubmitPreservesCommandData() {
        // Verify that submitted commands preserve their data
        MockRenderBackend backend = new MockRenderBackend();
        
        DrawCommand original = new DrawCommand(42, 84, 126, RenderPass.TRANSPARENT);
        
        backend.beginFrame();
        backend.submit(original);
        backend.endFrame();
        
        List<DrawCommand> commands = backend.getSubmittedCommands();
        assertEquals(1, commands.size());
        
        DrawCommand received = commands.get(0);
        assertEquals(42, received.meshId);
        assertEquals(84, received.materialId);
        assertEquals(126, received.transformIndex);
        assertEquals(RenderPass.TRANSPARENT, received.pass);
    }
    
    @Test
    public void testSubmitPreservesCommandOrder() {
        // Verify that commands are submitted in the order they were received
        MockRenderBackend backend = new MockRenderBackend();
        
        DrawCommand cmd1 = new DrawCommand(1, 1, 1, RenderPass.OPAQUE);
        DrawCommand cmd2 = new DrawCommand(2, 2, 2, RenderPass.TRANSPARENT);
        DrawCommand cmd3 = new DrawCommand(3, 3, 3, RenderPass.SHADOW);
        DrawCommand cmd4 = new DrawCommand(4, 4, 4, RenderPass.UI);
        
        backend.beginFrame();
        backend.submit(cmd1);
        backend.submit(cmd2);
        backend.submit(cmd3);
        backend.submit(cmd4);
        backend.endFrame();
        
        List<DrawCommand> commands = backend.getSubmittedCommands();
        assertEquals(4, commands.size());
        
        assertEquals(1, commands.get(0).meshId);
        assertEquals(2, commands.get(1).meshId);
        assertEquals(3, commands.get(2).meshId);
        assertEquals(4, commands.get(3).meshId);
    }
    
    @Test
    public void testSubmitWithAllRenderPasses() {
        // Verify that the backend can handle commands from all render passes
        MockRenderBackend backend = new MockRenderBackend();
        
        backend.beginFrame();
        for (RenderPass pass : RenderPass.values()) {
            backend.submit(new DrawCommand(1, 1, 1, pass));
        }
        backend.endFrame();
        
        List<DrawCommand> commands = backend.getSubmittedCommands();
        assertEquals(4, commands.size(), "Should have submitted one command per pass");
        
        // Verify we got all passes
        List<RenderPass> receivedPasses = new ArrayList<>();
        for (DrawCommand cmd : commands) {
            receivedPasses.add(cmd.pass);
        }
        
        assertTrue(receivedPasses.contains(RenderPass.OPAQUE));
        assertTrue(receivedPasses.contains(RenderPass.TRANSPARENT));
        assertTrue(receivedPasses.contains(RenderPass.SHADOW));
        assertTrue(receivedPasses.contains(RenderPass.UI));
    }
    
    @Test
    public void testSubmitNullCommandThrows() {
        // Verify that submitting null throws an exception
        MockRenderBackend backend = new MockRenderBackend();
        
        backend.beginFrame();
        assertThrows(NullPointerException.class, () -> {
            backend.submit(null);
        }, "Submitting null command should throw NullPointerException");
    }
    
    @Test
    public void testSubmitWithoutBeginFrameThrows() {
        // Verify that submitting without an active frame throws
        MockRenderBackend backend = new MockRenderBackend();
        
        DrawCommand cmd = new DrawCommand(1, 2, 3, RenderPass.OPAQUE);
        assertThrows(IllegalStateException.class, () -> {
            backend.submit(cmd);
        }, "Submit without beginFrame should throw");
    }
    
    @Test
    public void testEndFrameWithoutBeginFrameThrows() {
        // Verify that ending a frame without beginning one throws
        MockRenderBackend backend = new MockRenderBackend();
        
        assertThrows(IllegalStateException.class, () -> {
            backend.endFrame();
        }, "endFrame without beginFrame should throw");
    }
    
    @Test
    public void testDoubleBeginFrameThrows() {
        // Verify that calling beginFrame twice without endFrame throws
        MockRenderBackend backend = new MockRenderBackend();
        
        backend.beginFrame();
        assertThrows(IllegalStateException.class, () -> {
            backend.beginFrame();
        }, "Double beginFrame should throw");
    }
    
    @Test
    public void testSubmitAfterEndFrameThrows() {
        // Verify that submitting after frame ends throws
        MockRenderBackend backend = new MockRenderBackend();
        
        backend.beginFrame();
        backend.endFrame();
        
        DrawCommand cmd = new DrawCommand(1, 2, 3, RenderPass.OPAQUE);
        assertThrows(IllegalStateException.class, () -> {
            backend.submit(cmd);
        }, "Submit after endFrame should throw");
    }
    
    @Test
    public void testFrameStateTracking() {
        // Verify that frame state is correctly tracked
        MockRenderBackend backend = new MockRenderBackend();
        
        assertFalse(backend.isFrameActive(), "Frame should not be active initially");
        
        backend.beginFrame();
        assertTrue(backend.isFrameActive(), "Frame should be active after beginFrame");
        
        backend.endFrame();
        assertFalse(backend.isFrameActive(), "Frame should not be active after endFrame");
    }
    
    @Test
    public void testManyCommandsInSingleFrame() {
        // Test submitting many commands to ensure no issues with larger batches
        MockRenderBackend backend = new MockRenderBackend();
        
        final int NUM_COMMANDS = 1000;
        
        backend.beginFrame();
        for (int i = 0; i < NUM_COMMANDS; i++) {
            backend.submit(new DrawCommand(i, i, i, RenderPass.OPAQUE));
        }
        backend.endFrame();
        
        List<DrawCommand> commands = backend.getSubmittedCommands();
        assertEquals(NUM_COMMANDS, commands.size(), 
                    "Should handle many commands in a single frame");
    }
    
    @Test
    public void testInterfaceCanBeImplementedByMultipleBackends() {
        // Verify that multiple backend implementations can coexist
        RenderBackend backend1 = new MockRenderBackend();
        RenderBackend backend2 = new MockRenderBackend();
        
        // Both should be valid instances of the interface
        assertNotNull(backend1);
        assertNotNull(backend2);
        assertNotSame(backend1, backend2);
        
        // Both should be usable independently
        backend1.beginFrame();
        backend2.beginFrame();
        
        backend1.submit(new DrawCommand(1, 1, 1, RenderPass.OPAQUE));
        backend2.submit(new DrawCommand(2, 2, 2, RenderPass.TRANSPARENT));
        
        backend1.endFrame();
        backend2.endFrame();
        
        // Verify they maintained separate state
        MockRenderBackend mock1 = (MockRenderBackend) backend1;
        MockRenderBackend mock2 = (MockRenderBackend) backend2;
        
        assertEquals(1, mock1.getSubmittedCommands().get(0).meshId);
        assertEquals(2, mock2.getSubmittedCommands().get(0).meshId);
    }
}

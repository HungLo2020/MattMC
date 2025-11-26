package mattmc.client.renderer;

import mattmc.client.renderer.backend.RenderBackend;
import mattmc.client.renderer.backend.DrawCommand;
import mattmc.client.renderer.backend.RenderPass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SystemInfoRenderer with Stage 4 backend integration.
 */
public class SystemInfoRendererTest {
    
    private SystemInfoRenderer renderer;
    private MockRenderBackend backend;
    private CommandBuffer capturedCommands;
    
    @BeforeEach
    public void setUp() {
        renderer = new SystemInfoRenderer();
        capturedCommands = new CommandBuffer();
        backend = new MockRenderBackend() {
            @Override
            public void submit(DrawCommand cmd) {
                capturedCommands.add(cmd);
            }
        };
    }
    
    @Test
    public void testSystemInfoRendererCreation() {
        assertNotNull(renderer);
    }
    
    @Test
    public void testSystemInfoLogicBuildsSingleCommand() {
        // System info with 6 lines should produce 1 command
        UIRenderLogic logic = new UIRenderLogic();
        CommandBuffer buffer = new CommandBuffer();
        
        String[] systemInfo = {
            "Java: 17.0.1",
            "Memory: 512/1024 MB (50%)",
            "CPU: Intel i7 (8 cores, 25.0%)",
            "Display: 1920x1080",
            "GPU: NVIDIA GeForce GTX 1080",
            "GPU Usage: 45%, VRAM: 2.1/8.0 GB"
        };
        
        logic.buildSystemInfoCommands(1920, 1080, systemInfo, buffer);
        
        // Should create 1 command encoding all lines
        assertEquals(1, buffer.size());
    }
    
    @Test
    public void testSystemInfoCommandsUseUIPass() {
        UIRenderLogic logic = new UIRenderLogic();
        CommandBuffer buffer = new CommandBuffer();
        
        String[] systemInfo = {"Line 1", "Line 2", "Line 3"};
        logic.buildSystemInfoCommands(1920, 1080, systemInfo, buffer);
        
        // Command should use UI render pass
        for (DrawCommand cmd : buffer.getCommands()) {
            assertEquals(RenderPass.UI, cmd.pass);
        }
    }
    
    @Test
    public void testSystemInfoCommandUsesSystemInfoMarker() {
        UIRenderLogic logic = new UIRenderLogic();
        CommandBuffer buffer = new CommandBuffer();
        
        String[] systemInfo = {"Java: 17", "Memory: 512 MB"};
        logic.buildSystemInfoCommands(1920, 1080, systemInfo, buffer);
        
        // Command should use meshId -9 (system info marker)
        DrawCommand cmd = buffer.getCommands().get(0);
        assertEquals(-9, cmd.meshId);
    }
    
    @Test
    public void testSystemInfoHandlesEmptyArray() {
        UIRenderLogic logic = new UIRenderLogic();
        CommandBuffer buffer = new CommandBuffer();
        
        String[] systemInfo = {};
        logic.buildSystemInfoCommands(1920, 1080, systemInfo, buffer);
        
        // Should not create commands for empty array
        assertEquals(0, buffer.size());
    }
    
    /**
     * Mock backend that captures commands for testing.
     */
    private static abstract class MockRenderBackend implements RenderBackend {
        private boolean frameActive = false;
        
        @Override
        public void beginFrame() {
            frameActive = true;
        }
        
        @Override
        public void endFrame() {
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
        @Override public void updateFrustum(mattmc.client.renderer.Frustum frustum) {}
    }
}

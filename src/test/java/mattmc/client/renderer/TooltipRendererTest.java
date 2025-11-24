package mattmc.client.renderer;

import mattmc.client.renderer.backend.RenderBackend;
import mattmc.client.renderer.backend.DrawCommand;
import mattmc.client.renderer.backend.RenderPass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TooltipRenderer with Stage 4 backend integration.
 */
public class TooltipRendererTest {
    
    private TooltipRenderer renderer;
    private MockRenderBackend backend;
    private CommandBuffer capturedCommands;
    
    @BeforeEach
    public void setUp() {
        renderer = new TooltipRenderer();
        capturedCommands = new CommandBuffer();
        backend = new MockRenderBackend() {
            @Override
            public void submit(DrawCommand cmd) {
                capturedCommands.add(cmd);
            }
        };
    }
    
    @Test
    public void testTooltipRendererCreation() {
        assertNotNull(renderer);
    }
    
    @Test
    public void testTooltipLogicBuildsTwoCommands() {
        // Tooltips produce 2 commands (position + size)
        UIRenderLogic logic = new UIRenderLogic();
        CommandBuffer buffer = new CommandBuffer();
        
        logic.buildTooltipCommands("Diamond Pickaxe", 100f, 100f, 1920, 1080, buffer);
        
        // Should create 2 commands (position and size info)
        assertEquals(2, buffer.size());
    }
    
    @Test
    public void testTooltipCommandsUseUIPass() {
        UIRenderLogic logic = new UIRenderLogic();
        CommandBuffer buffer = new CommandBuffer();
        
        logic.buildTooltipCommands("Stone", 50f, 50f, 1920, 1080, buffer);
        
        // All commands should use UI render pass
        for (DrawCommand cmd : buffer.getCommands()) {
            assertEquals(RenderPass.UI, cmd.pass);
        }
    }
    
    @Test
    public void testTooltipCommandUsesTooltipMarker() {
        UIRenderLogic logic = new UIRenderLogic();
        CommandBuffer buffer = new CommandBuffer();
        
        logic.buildTooltipCommands("Iron Sword", 200f, 200f, 1920, 1080, buffer);
        
        // Commands should use meshId -10 (tooltip marker)
        for (DrawCommand cmd : buffer.getCommands()) {
            assertEquals(-10, cmd.meshId);
        }
    }
    
    @Test
    public void testTooltipHandlesEmptyText() {
        UIRenderLogic logic = new UIRenderLogic();
        CommandBuffer buffer = new CommandBuffer();
        
        logic.buildTooltipCommands("", 100f, 100f, 1920, 1080, buffer);
        
        // Should not create commands for empty text
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
        @Override public void drawText(String t, float x, float y, float s) {}
        @Override public void drawCenteredText(String t, float x, float y, float s) {}
        @Override public float getTextWidth(String t, float s) { return 0; }
        @Override public float getTextHeight(String t, float s) { return 0; }
        @Override public void setCursorPosCallback(long h, CursorPosCallback c) {}
        @Override public void setMouseButtonCallback(long h, MouseButtonCallback c) {}
        @Override public void setFramebufferSizeCallback(long h, FramebufferSizeCallback c) {}
        @Override public void setKeyCallback(long h, KeyCallback c) {}
        @Override public void setCharCallback(long h, CharCallback c) {}
        @Override public void setScrollCallback(long h, ScrollCallback c) {}
        @Override public void setViewport(int x, int y, int w, int h) {}
        @Override public mattmc.client.renderer.panorama.PanoramaRenderer createPanoramaRenderer(String basePath, String ext) { return null; }
    }
}

package mattmc.client.renderer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CrosshairRenderer with Stage 4 backend integration.
 */
public class CrosshairRendererTest {
    
    private CrosshairRenderer renderer;
    private MockRenderBackend backend;
    private CommandBuffer capturedCommands;
    
    @BeforeEach
    public void setUp() {
        renderer = new CrosshairRenderer();
        capturedCommands = new CommandBuffer();
        backend = new MockRenderBackend() {
            @Override
            public void submit(DrawCommand cmd) {
                capturedCommands.add(cmd);
            }
        };
    }
    
    @Test
    public void testCrosshairRendererCreation() {
        assertNotNull(renderer);
    }
    
    // Note: Actual rendering tests are skipped as they require OpenGL context
    // The important tests are the logic layer tests below which don't need GL
    
    @Test
    public void testCrosshairLogicBuildsTwoCommands() {
        // The crosshair consists of two quads (horizontal + vertical lines)
        UIRenderLogic logic = new UIRenderLogic();
        CommandBuffer buffer = new CommandBuffer();
        
        logic.buildCrosshairCommands(1920, 1080, buffer);
        
        // Should create 2 commands (horizontal and vertical lines)
        assertEquals(2, buffer.size());
    }
    
    @Test
    public void testCrosshairCommandsUseUIPass() {
        UIRenderLogic logic = new UIRenderLogic();
        CommandBuffer buffer = new CommandBuffer();
        
        logic.buildCrosshairCommands(1920, 1080, buffer);
        
        // All commands should use UI render pass
        for (DrawCommand cmd : buffer.getCommands()) {
            assertEquals(RenderPass.UI, cmd.pass);
        }
    }
    
    @Test
    public void testCrosshairCommandsHaveCorrectMarker() {
        UIRenderLogic logic = new UIRenderLogic();
        CommandBuffer buffer = new CommandBuffer();
        
        logic.buildCrosshairCommands(1920, 1080, buffer);
        
        // Crosshair uses meshId = -1 as UI quad marker
        for (DrawCommand cmd : buffer.getCommands()) {
            assertEquals(-1, cmd.meshId);
        }
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
    }
}

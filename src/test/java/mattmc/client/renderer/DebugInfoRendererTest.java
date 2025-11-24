package mattmc.client.renderer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DebugInfoRenderer backend integration.
 * Verifies that debug info rendering can build DrawCommands without requiring OpenGL context.
 */
public class DebugInfoRendererTest {
    
    @Test
    public void testDebugInfoCommandsBuilt() {
        // Test that debug info commands can be built
        UIRenderLogic logic = new UIRenderLogic();
        CommandBuffer buffer = new CommandBuffer();
        
        UIRenderLogic.clearTextRegistry();
        
        logic.buildDebugInfoCommands(800, 600, 100f, 64f, 200f, 
            45f, 30f, 0f, 60.0, 10, 2, 4, 8, 2, buffer);
        
        // Should create commands for all debug text lines (9 lines)
        assertFalse(buffer.getCommands().isEmpty(), "Should build debug info commands");
        assertTrue(buffer.getCommands().size() >= 9, "Should have commands for all 9 debug lines");
    }
    
    @Test
    public void testDebugInfoCommandsUseUIPass() {
        // Test that debug info commands use UI render pass
        UIRenderLogic logic = new UIRenderLogic();
        CommandBuffer buffer = new CommandBuffer();
        
        UIRenderLogic.clearTextRegistry();
        
        logic.buildDebugInfoCommands(800, 600, 100f, 64f, 200f,
            45f, 30f, 0f, 60.0, 10, 2, 4, 8, 2, buffer);
        
        for (DrawCommand cmd : buffer.getCommands()) {
            assertEquals(RenderPass.UI, cmd.pass, "Debug info commands should use UI render pass");
        }
    }
    
    @Test
    public void testDebugInfoCommandsUseMeshIdMarker() {
        // Test that debug info commands use meshId -7 marker
        UIRenderLogic logic = new UIRenderLogic();
        CommandBuffer buffer = new CommandBuffer();
        
        UIRenderLogic.clearTextRegistry();
        
        logic.buildDebugInfoCommands(800, 600, 100f, 64f, 200f,
            45f, 30f, 0f, 60.0, 10, 2, 4, 8, 2, buffer);
        
        for (DrawCommand cmd : buffer.getCommands()) {
            assertEquals(-7, cmd.meshId, "Debug info commands should use meshId -7");
        }
    }
    
    @Test
    public void testTextRegistryPopulated() {
        // Test that text registry is populated correctly
        UIRenderLogic logic = new UIRenderLogic();
        CommandBuffer buffer = new CommandBuffer();
        
        UIRenderLogic.clearTextRegistry();
        
        logic.buildDebugInfoCommands(800, 600, 100f, 64f, 200f,
            45f, 30f, 0f, 60.0, 10, 2, 4, 8, 2, buffer);
        
        // Each command should have a valid text ID in transformIndex
        for (DrawCommand cmd : buffer.getCommands()) {
            UIRenderLogic.TextRenderInfo textInfo = UIRenderLogic.getTextInfo(cmd.transformIndex);
            assertNotNull(textInfo, "Text info should be in registry for command");
            assertNotNull(textInfo.text, "Text should not be null");
        }
    }
}

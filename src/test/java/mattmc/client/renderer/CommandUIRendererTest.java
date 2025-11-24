package mattmc.client.renderer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CommandUIRenderer backend integration.
 * Verifies that command UI rendering can build DrawCommands without requiring OpenGL context.
 */
public class CommandUIRendererTest {
    
    @Test
    public void testCommandOverlayCommandsBuilt() {
        // Test that command overlay commands can be built
        UIRenderLogic logic = new UIRenderLogic();
        CommandBuffer buffer = new CommandBuffer();
        
        UIRenderLogic.clearTextRegistry();
        
        logic.buildCommandOverlayCommands(800, 600, "/test command", buffer);
        
        assertFalse(buffer.getCommands().isEmpty(), "Should build command overlay commands");
    }
    
    @Test
    public void testCommandFeedbackCommandsBuilt() {
        // Test that command feedback commands can be built
        UIRenderLogic logic = new UIRenderLogic();
        CommandBuffer buffer = new CommandBuffer();
        
        UIRenderLogic.clearTextRegistry();
        
        logic.buildCommandFeedbackCommands(800, 600, "Command executed successfully!", buffer);
        
        assertFalse(buffer.getCommands().isEmpty(), "Should build command feedback commands");
    }
    
    @Test
    public void testCommandUICommandsUseUIPass() {
        // Test that command UI commands use UI render pass
        UIRenderLogic logic = new UIRenderLogic();
        CommandBuffer buffer = new CommandBuffer();
        
        UIRenderLogic.clearTextRegistry();
        
        logic.buildCommandOverlayCommands(800, 600, "/test", buffer);
        logic.buildCommandFeedbackCommands(800, 600, "Success", buffer);
        
        for (DrawCommand cmd : buffer.getCommands()) {
            assertEquals(RenderPass.UI, cmd.pass, "Command UI commands should use UI render pass");
        }
    }
    
    @Test
    public void testCommandUICommandsUseMeshIdMarker() {
        // Test that command UI commands use meshId -8 marker
        UIRenderLogic logic = new UIRenderLogic();
        CommandBuffer buffer = new CommandBuffer();
        
        UIRenderLogic.clearTextRegistry();
        
        logic.buildCommandOverlayCommands(800, 600, "/test", buffer);
        
        for (DrawCommand cmd : buffer.getCommands()) {
            assertEquals(-8, cmd.meshId, "Command UI commands should use meshId -8");
        }
    }
}

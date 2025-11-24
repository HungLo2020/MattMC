package mattmc.client.renderer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for HotbarRenderer backend integration.
 * Verifies that hotbar rendering can build DrawCommands without requiring OpenGL context.
 */
public class HotbarRendererTest {
    
    @Test
    public void testHotbarCommandsBuilt() {
        // Test that hotbar commands can be built
        UIRenderLogic logic = new UIRenderLogic();
        CommandBuffer buffer = new CommandBuffer();
        
        int screenWidth = 800;
        int screenHeight = 600;
        int selectedSlot = 3;
        
        logic.buildHotbarCommands(screenWidth, screenHeight, selectedSlot, buffer);
        
        // Should create commands for hotbar background and selection
        assertFalse(buffer.getCommands().isEmpty(), "Should build hotbar commands");
        assertTrue(buffer.getCommands().size() >= 2, "Should have at least background + selection");
    }
    
    @Test
    public void testHotbarCommandsUseUIPass() {
        // Test that hotbar commands use UI render pass
        UIRenderLogic logic = new UIRenderLogic();
        CommandBuffer buffer = new CommandBuffer();
        
        logic.buildHotbarCommands(800, 600, 0, buffer);
        
        for (DrawCommand cmd : buffer.getCommands()) {
            assertEquals(RenderPass.UI, cmd.pass, "Hotbar commands should use UI render pass");
        }
    }
    
    @Test
    public void testHotbarCommandsUseMeshIdMarker() {
        // Test that hotbar commands use meshId -6 marker
        UIRenderLogic logic = new UIRenderLogic();
        CommandBuffer buffer = new CommandBuffer();
        
        logic.buildHotbarCommands(800, 600, 0, buffer);
        
        for (DrawCommand cmd : buffer.getCommands()) {
            assertEquals(-6, cmd.meshId, "Hotbar commands should use meshId -6");
        }
    }
    
    @Test
    public void testSelectionCommandsForDifferentSlots() {
        // Test that selection commands work for different slots
        UIRenderLogic logic = new UIRenderLogic();
        
        for (int slot = 0; slot <= 8; slot++) {
            CommandBuffer buffer = new CommandBuffer();
            logic.buildSelectionCommands(800, 600, slot, buffer);
            
            assertFalse(buffer.getCommands().isEmpty(), 
                "Should build selection command for slot " + slot);
        }
    }
}

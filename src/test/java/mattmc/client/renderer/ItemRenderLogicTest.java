package mattmc.client.renderer;

import mattmc.world.item.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ItemRenderLogic class.
 * 
 * <p>These tests verify that item rendering logic can build commands without
 * requiring an OpenGL context.
 * 
 * This is part of Stage 4 of the rendering refactor.
 */
public class ItemRenderLogicTest {
    
    private ItemRenderLogic logic;
    private CommandBuffer buffer;
    
    @BeforeEach
    public void setUp() {
        logic = new ItemRenderLogic();
        buffer = new CommandBuffer();
    }
    
    @Test
    public void testCanCreateLogic() {
        assertNotNull(logic);
    }
    
    @Test
    public void testBuildItemCommandWithNull() {
        // Should handle null gracefully
        assertDoesNotThrow(() -> {
            logic.buildItemCommand(null, 0, 0, 16, buffer);
        });
        
        // Should not add any commands for null
        assertEquals(0, buffer.size());
    }
    
    @Test
    public void testBuildInventoryItemCommandsWithEmptyArray() {
        ItemStack[] empty = new ItemStack[9];
        
        assertDoesNotThrow(() -> {
            logic.buildInventoryItemCommands(empty, 0, 0, 16, 2, 9, buffer);
        });
    }
    
    @Test
    public void testBuildInventoryItemCommandsWithNullItems() {
        ItemStack[] items = new ItemStack[9];
        // All null items
        
        logic.buildInventoryItemCommands(items, 0, 0, 16, 2, 9, buffer);
        
        // Should not add commands for null items
        assertEquals(0, buffer.size());
    }
    
    @Test
    public void testBufferNotClearedByBuildCommand() {
        // Add a command first
        buffer.add(new DrawCommand(1, 1, 1, RenderPass.UI));
        
        // Build item command (null, so nothing added)
        logic.buildItemCommand(null, 0, 0, 16, buffer);
        
        // Original command should still be there
        assertEquals(1, buffer.size());
    }
}

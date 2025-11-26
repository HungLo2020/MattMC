package mattmc.client.renderer;

import mattmc.client.renderer.backend.DrawCommand;
import mattmc.client.renderer.backend.RenderPass;
import mattmc.client.renderer.chunk.ChunkMeshRegistry;
import mattmc.client.renderer.chunk.MockChunkMeshRegistry;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for rendering system components that don't require OpenGL context.
 * 
 * These tests verify:
 * - DrawCommand construction and properties
 * - CommandBuffer operations
 * - Frustum culling logic
 * - UIRenderLogic command generation
 * - ItemRenderLogic command generation
 */
public class RenderingSystemTest {
    
    private CommandBuffer commandBuffer;
    private Frustum frustum;
    
    @BeforeEach
    public void setup() {
        commandBuffer = new CommandBuffer();
        frustum = new Frustum();
    }
    
    // ========== DrawCommand Tests ==========
    
    @Test
    @DisplayName("DrawCommand stores all parameters correctly")
    public void testDrawCommandConstruction() {
        DrawCommand cmd = new DrawCommand(42, 7, 123, RenderPass.OPAQUE);
        
        assertEquals(42, cmd.meshId, "Mesh ID should be 42");
        assertEquals(7, cmd.materialId, "Material ID should be 7");
        assertEquals(123, cmd.transformIndex, "Transform index should be 123");
        assertEquals(RenderPass.OPAQUE, cmd.pass, "Pass should be OPAQUE");
    }
    
    @Test
    @DisplayName("DrawCommand supports negative mesh IDs (UI elements)")
    public void testNegativeMeshIds() {
        DrawCommand crosshair = new DrawCommand(-1, 0, 0, RenderPass.UI);
        DrawCommand hotbar = new DrawCommand(-6, 0, 0, RenderPass.UI);
        
        assertEquals(-1, crosshair.meshId, "Crosshair mesh ID");
        assertEquals(-6, hotbar.meshId, "Hotbar mesh ID");
    }
    
    @Test
    @DisplayName("DrawCommand toString() is informative")
    public void testDrawCommandToString() {
        DrawCommand cmd = new DrawCommand(1, 2, 3, RenderPass.TRANSPARENT);
        String str = cmd.toString();
        
        assertTrue(str.contains("meshId=1"), "Should contain meshId");
        assertTrue(str.contains("materialId=2"), "Should contain materialId");
        assertTrue(str.contains("transformIndex=3"), "Should contain transformIndex");
        assertTrue(str.contains("TRANSPARENT"), "Should contain render pass");
    }
    
    // ========== CommandBuffer Tests ==========
    
    @Test
    @DisplayName("CommandBuffer starts empty")
    public void testCommandBufferInitiallyEmpty() {
        assertTrue(commandBuffer.getCommands().isEmpty(), "Buffer should start empty");
    }
    
    @Test
    @DisplayName("CommandBuffer stores added commands")
    public void testCommandBufferAdd() {
        DrawCommand cmd1 = new DrawCommand(1, 0, 0, RenderPass.OPAQUE);
        DrawCommand cmd2 = new DrawCommand(2, 0, 0, RenderPass.TRANSPARENT);
        
        commandBuffer.add(cmd1);
        commandBuffer.add(cmd2);
        
        List<DrawCommand> commands = commandBuffer.getCommands();
        assertEquals(2, commands.size(), "Should have 2 commands");
        assertSame(cmd1, commands.get(0), "First command should be cmd1");
        assertSame(cmd2, commands.get(1), "Second command should be cmd2");
    }
    
    @Test
    @DisplayName("CommandBuffer clear removes all commands")
    public void testCommandBufferClear() {
        commandBuffer.add(new DrawCommand(1, 0, 0, RenderPass.OPAQUE));
        commandBuffer.add(new DrawCommand(2, 0, 0, RenderPass.OPAQUE));
        
        commandBuffer.clear();
        
        assertTrue(commandBuffer.getCommands().isEmpty(), "Buffer should be empty after clear");
    }
    
    @Test
    @DisplayName("CommandBuffer can be reused after clear")
    public void testCommandBufferReuse() {
        commandBuffer.add(new DrawCommand(1, 0, 0, RenderPass.OPAQUE));
        commandBuffer.clear();
        commandBuffer.add(new DrawCommand(2, 0, 0, RenderPass.TRANSPARENT));
        
        assertEquals(1, commandBuffer.getCommands().size(), "Should have 1 command");
        assertEquals(2, commandBuffer.getCommands().get(0).meshId, "Should be the new command");
    }
    
    // ========== RenderPass Tests ==========
    
    @Test
    @DisplayName("All render passes exist")
    public void testRenderPassValues() {
        assertNotNull(RenderPass.OPAQUE, "OPAQUE should exist");
        assertNotNull(RenderPass.TRANSPARENT, "TRANSPARENT should exist");
        assertNotNull(RenderPass.SHADOW, "SHADOW should exist");
        assertNotNull(RenderPass.UI, "UI should exist");
    }
    
    @Test
    @DisplayName("Render passes are distinct")
    public void testRenderPassDistinct() {
        RenderPass[] passes = RenderPass.values();
        for (int i = 0; i < passes.length; i++) {
            for (int j = i + 1; j < passes.length; j++) {
                assertNotEquals(passes[i], passes[j], 
                    "Pass " + passes[i] + " should be different from " + passes[j]);
            }
        }
    }
    
    // ========== Frustum Tests ==========
    
    @Test
    @DisplayName("Frustum exists and is instantiable")
    public void testFrustumExists() {
        assertNotNull(frustum, "Frustum should be instantiable");
    }
    
    @Test
    @DisplayName("Frustum has visibility check method")
    public void testFrustumHasVisibilityMethod() {
        // Without matrices set, frustum should still handle queries gracefully
        // The exact behavior depends on implementation - we're just checking it doesn't crash
        boolean result = frustum.isChunkVisible(0, 0, 0, 0, 256, 0);
        // Result could be true or false depending on default state
        // We just verify it doesn't throw
    }
    
    // ========== ChunkRenderLogic Tests ==========
    
    @Test
    @DisplayName("ChunkRenderLogic tracks statistics")
    public void testChunkRenderLogicStatistics() {
        ChunkMeshRegistry registry = new MockChunkMeshRegistry();
        ChunkRenderLogic logic = new ChunkRenderLogic(registry, frustum);
        
        // Initially, all counts should be 0
        assertEquals(0, logic.getTotalChunkCount(), "Total count starts at 0");
        assertEquals(0, logic.getVisibleChunkCount(), "Visible count starts at 0");
        assertEquals(0, logic.getCulledChunkCount(), "Culled count starts at 0");
    }
    
    // ========== UIRenderLogic Tests ==========
    
    @Test
    @DisplayName("UIRenderLogic generates crosshair commands")
    public void testUIRenderLogicCrosshair() {
        UIRenderLogic logic = new UIRenderLogic();
        logic.buildCrosshairCommands(800, 600, commandBuffer);
        
        assertEquals(2, commandBuffer.getCommands().size(), "Should generate 2 commands (H and V lines)");
        DrawCommand cmd = commandBuffer.getCommands().get(0);
        assertEquals(RenderPass.UI, cmd.pass, "Should be UI pass");
        assertEquals(-1, cmd.meshId, "Crosshair meshId is -1 (UIMeshIds.CROSSHAIR)");
    }
    
    @Test
    @DisplayName("UIRenderLogic generates hotbar commands")
    public void testUIRenderLogicHotbar() {
        UIRenderLogic logic = new UIRenderLogic();
        logic.buildHotbarCommands(800, 600, 0, commandBuffer);
        
        assertFalse(commandBuffer.getCommands().isEmpty(), "Should generate hotbar commands");
        DrawCommand cmd = commandBuffer.getCommands().get(0);
        assertEquals(RenderPass.UI, cmd.pass, "Should be UI pass");
        assertEquals(-6, cmd.meshId, "Hotbar meshId is -6");
    }
    
    @Test
    @DisplayName("UIRenderLogic generates debug text commands")
    public void testUIRenderLogicDebugInfo() {
        UIRenderLogic logic = new UIRenderLogic();
        logic.buildDebugInfoCommands(800, 600, 0, 0, 64, 0, 0, 0, 60.0, 
            10, 2, 4, 8, 2, commandBuffer);
        
        // Debug info should generate text commands
        assertFalse(commandBuffer.getCommands().isEmpty(), "Should generate debug text commands");
    }
    
    // ========== ItemRenderLogic Tests ==========
    
    @Test
    @DisplayName("ItemRenderLogic handles null item stack")
    public void testItemRenderLogicNullStack() {
        ItemRenderLogic logic = new ItemRenderLogic();
        logic.buildItemCommand(null, 100, 100, 24, commandBuffer);
        
        assertTrue(commandBuffer.getCommands().isEmpty(), "No command for null stack");
    }
    
    // ========== Integration Tests ==========
    
    @Test
    @DisplayName("Multiple UI elements can share CommandBuffer")
    public void testMultipleUIElements() {
        UIRenderLogic logic = new UIRenderLogic();
        
        logic.buildCrosshairCommands(800, 600, commandBuffer);
        logic.buildHotbarCommands(800, 600, 0, commandBuffer);
        
        assertTrue(commandBuffer.getCommands().size() >= 2, 
            "Should have commands from both crosshair and hotbar");
    }
    
    @Test
    @DisplayName("Commands can be sorted by render pass")
    public void testCommandSortingByPass() {
        // Add commands in random order
        commandBuffer.add(new DrawCommand(1, 0, 0, RenderPass.UI));
        commandBuffer.add(new DrawCommand(2, 0, 0, RenderPass.OPAQUE));
        commandBuffer.add(new DrawCommand(3, 0, 0, RenderPass.TRANSPARENT));
        commandBuffer.add(new DrawCommand(4, 0, 0, RenderPass.OPAQUE));
        
        // Get mutable copy of commands for sorting
        List<DrawCommand> commands = new ArrayList<>(commandBuffer.getCommands());
        
        // Sort by pass ordinal
        commands.sort((a, b) -> a.pass.ordinal() - b.pass.ordinal());
        
        // Verify order: OPAQUE, TRANSPARENT, SHADOW, UI
        assertEquals(RenderPass.OPAQUE, commands.get(0).pass, "First should be OPAQUE");
        assertEquals(RenderPass.OPAQUE, commands.get(1).pass, "Second should be OPAQUE");
        assertEquals(RenderPass.TRANSPARENT, commands.get(2).pass, "Third should be TRANSPARENT");
        assertEquals(RenderPass.UI, commands.get(3).pass, "Last should be UI");
    }
}

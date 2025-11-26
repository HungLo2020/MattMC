package mattmc.client.renderer;

import mattmc.client.renderer.backend.RenderBackend;
import mattmc.client.renderer.backend.DrawCommand;
import mattmc.client.renderer.backend.RenderPass;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the CommandBuffer class.
 * 
 * This is part of Stage 3 of the rendering refactor.
 */
public class CommandBufferTest {
    
    private CommandBuffer buffer;
    
    @BeforeEach
    public void setUp() {
        buffer = new CommandBuffer();
    }
    
    @Test
    public void testInitiallyEmpty() {
        assertTrue(buffer.isEmpty());
        assertEquals(0, buffer.size());
    }
    
    @Test
    public void testAddCommand() {
        DrawCommand cmd = new DrawCommand(1, 2, 3, RenderPass.OPAQUE);
        buffer.add(cmd);
        
        assertEquals(1, buffer.size());
        assertFalse(buffer.isEmpty());
        assertEquals(cmd, buffer.get(0));
    }
    
    @Test
    public void testAddMultipleCommands() {
        DrawCommand cmd1 = new DrawCommand(1, 1, 1, RenderPass.OPAQUE);
        DrawCommand cmd2 = new DrawCommand(2, 2, 2, RenderPass.TRANSPARENT);
        DrawCommand cmd3 = new DrawCommand(3, 3, 3, RenderPass.UI);
        
        buffer.add(cmd1);
        buffer.add(cmd2);
        buffer.add(cmd3);
        
        assertEquals(3, buffer.size());
        assertEquals(cmd1, buffer.get(0));
        assertEquals(cmd2, buffer.get(1));
        assertEquals(cmd3, buffer.get(2));
    }
    
    @Test
    public void testAddNullThrows() {
        assertThrows(NullPointerException.class, () -> {
            buffer.add(null);
        });
    }
    
    @Test
    public void testGetCommands() {
        DrawCommand cmd1 = new DrawCommand(1, 1, 1, RenderPass.OPAQUE);
        DrawCommand cmd2 = new DrawCommand(2, 2, 2, RenderPass.TRANSPARENT);
        
        buffer.add(cmd1);
        buffer.add(cmd2);
        
        var commands = buffer.getCommands();
        assertEquals(2, commands.size());
        assertEquals(cmd1, commands.get(0));
        assertEquals(cmd2, commands.get(1));
    }
    
    @Test
    public void testGetCommandsIsUnmodifiable() {
        DrawCommand cmd = new DrawCommand(1, 1, 1, RenderPass.OPAQUE);
        buffer.add(cmd);
        
        var commands = buffer.getCommands();
        
        assertThrows(UnsupportedOperationException.class, () -> {
            commands.add(new DrawCommand(2, 2, 2, RenderPass.OPAQUE));
        });
    }
    
    @Test
    public void testClear() {
        buffer.add(new DrawCommand(1, 1, 1, RenderPass.OPAQUE));
        buffer.add(new DrawCommand(2, 2, 2, RenderPass.OPAQUE));
        
        assertEquals(2, buffer.size());
        
        buffer.clear();
        
        assertEquals(0, buffer.size());
        assertTrue(buffer.isEmpty());
    }
    
    @Test
    public void testClearAndReuse() {
        buffer.add(new DrawCommand(1, 1, 1, RenderPass.OPAQUE));
        buffer.clear();
        
        buffer.add(new DrawCommand(2, 2, 2, RenderPass.OPAQUE));
        
        assertEquals(1, buffer.size());
        assertEquals(2, buffer.get(0).meshId);
    }
    
    @Test
    public void testAddAll() {
        CommandBuffer other = new CommandBuffer();
        other.add(new DrawCommand(1, 1, 1, RenderPass.OPAQUE));
        other.add(new DrawCommand(2, 2, 2, RenderPass.TRANSPARENT));
        
        buffer.add(new DrawCommand(0, 0, 0, RenderPass.OPAQUE));
        buffer.addAll(other);
        
        assertEquals(3, buffer.size());
        assertEquals(0, buffer.get(0).meshId);
        assertEquals(1, buffer.get(1).meshId);
        assertEquals(2, buffer.get(2).meshId);
    }
    
    @Test
    public void testAddAllNullThrows() {
        assertThrows(NullPointerException.class, () -> {
            buffer.addAll(null);
        });
    }
    
    @Test
    public void testGetOutOfBoundsThrows() {
        buffer.add(new DrawCommand(1, 1, 1, RenderPass.OPAQUE));
        
        assertThrows(IndexOutOfBoundsException.class, () -> {
            buffer.get(1);
        });
        
        assertThrows(IndexOutOfBoundsException.class, () -> {
            buffer.get(-1);
        });
    }
    
    @Test
    public void testInitialCapacity() {
        CommandBuffer largeBuffer = new CommandBuffer(1000);
        
        assertTrue(largeBuffer.isEmpty());
        
        // Should be able to add 1000 commands without resizing
        for (int i = 0; i < 1000; i++) {
            largeBuffer.add(new DrawCommand(i, i, i, RenderPass.OPAQUE));
        }
        
        assertEquals(1000, largeBuffer.size());
    }
    
    @Test
    public void testToString() {
        String str = buffer.toString();
        assertTrue(str.contains("CommandBuffer"));
        assertTrue(str.contains("empty"));
        
        buffer.add(new DrawCommand(1, 1, 1, RenderPass.OPAQUE));
        str = buffer.toString();
        assertTrue(str.contains("1 commands"));
    }
    
    @Test
    public void testManyCommands() {
        for (int i = 0; i < 10000; i++) {
            buffer.add(new DrawCommand(i, i, i, RenderPass.OPAQUE));
        }
        
        assertEquals(10000, buffer.size());
        assertEquals(0, buffer.get(0).meshId);
        assertEquals(9999, buffer.get(9999).meshId);
    }
    
    // ===== Sorting and Batching Tests =====
    
    @Test
    public void testSortByMaterial() {
        // Add commands with different materials in random order
        buffer.add(new DrawCommand(1, 300, 1, RenderPass.OPAQUE));
        buffer.add(new DrawCommand(2, 100, 2, RenderPass.OPAQUE));
        buffer.add(new DrawCommand(3, 200, 3, RenderPass.OPAQUE));
        buffer.add(new DrawCommand(4, 100, 4, RenderPass.OPAQUE));
        
        buffer.sortByMaterial();
        
        // Verify sorted by materialId
        assertEquals(100, buffer.get(0).materialId);
        assertEquals(100, buffer.get(1).materialId);
        assertEquals(200, buffer.get(2).materialId);
        assertEquals(300, buffer.get(3).materialId);
    }
    
    @Test
    public void testSortByRenderPass() {
        // Add commands in wrong order
        buffer.add(new DrawCommand(1, 1, 1, RenderPass.UI));
        buffer.add(new DrawCommand(2, 2, 2, RenderPass.OPAQUE));
        buffer.add(new DrawCommand(3, 3, 3, RenderPass.TRANSPARENT));
        buffer.add(new DrawCommand(4, 4, 4, RenderPass.SHADOW));
        
        buffer.sortByRenderPass();
        
        // Verify sorted by pass order (OPAQUE < TRANSPARENT < SHADOW < UI)
        assertEquals(RenderPass.OPAQUE, buffer.get(0).pass);
        assertEquals(RenderPass.TRANSPARENT, buffer.get(1).pass);
        assertEquals(RenderPass.SHADOW, buffer.get(2).pass);
        assertEquals(RenderPass.UI, buffer.get(3).pass);
    }
    
    @Test
    public void testSortForRendering() {
        // Add commands with mixed passes and materials
        buffer.add(new DrawCommand(1, 200, 1, RenderPass.TRANSPARENT));
        buffer.add(new DrawCommand(2, 100, 2, RenderPass.OPAQUE));
        buffer.add(new DrawCommand(3, 300, 3, RenderPass.OPAQUE));
        buffer.add(new DrawCommand(4, 100, 4, RenderPass.TRANSPARENT));
        buffer.add(new DrawCommand(5, 50, 5, RenderPass.UI));
        
        buffer.sortForRendering();
        
        // Verify sorted by pass first, then by material within each pass
        // OPAQUE commands: material 100 should come before 300
        assertEquals(RenderPass.OPAQUE, buffer.get(0).pass);
        assertEquals(100, buffer.get(0).materialId);
        assertEquals(RenderPass.OPAQUE, buffer.get(1).pass);
        assertEquals(300, buffer.get(1).materialId);
        
        // TRANSPARENT commands: material 100 should come before 200
        assertEquals(RenderPass.TRANSPARENT, buffer.get(2).pass);
        assertEquals(100, buffer.get(2).materialId);
        assertEquals(RenderPass.TRANSPARENT, buffer.get(3).pass);
        assertEquals(200, buffer.get(3).materialId);
        
        // UI command last
        assertEquals(RenderPass.UI, buffer.get(4).pass);
    }
    
    @Test
    public void testSortByTransformIndexBackToFront() {
        buffer.add(new DrawCommand(1, 1, 10, RenderPass.TRANSPARENT));
        buffer.add(new DrawCommand(2, 1, 50, RenderPass.TRANSPARENT));
        buffer.add(new DrawCommand(3, 1, 30, RenderPass.TRANSPARENT));
        
        buffer.sortByTransformIndex(true); // back to front (highest first)
        
        assertEquals(50, buffer.get(0).transformIndex);
        assertEquals(30, buffer.get(1).transformIndex);
        assertEquals(10, buffer.get(2).transformIndex);
    }
    
    @Test
    public void testSortByTransformIndexFrontToBack() {
        buffer.add(new DrawCommand(1, 1, 50, RenderPass.OPAQUE));
        buffer.add(new DrawCommand(2, 1, 10, RenderPass.OPAQUE));
        buffer.add(new DrawCommand(3, 1, 30, RenderPass.OPAQUE));
        
        buffer.sortByTransformIndex(false); // front to back (lowest first)
        
        assertEquals(10, buffer.get(0).transformIndex);
        assertEquals(30, buffer.get(1).transformIndex);
        assertEquals(50, buffer.get(2).transformIndex);
    }
    
    @Test
    public void testSortEmptyBuffer() {
        // Should not throw
        assertDoesNotThrow(() -> {
            buffer.sortByMaterial();
            buffer.sortByRenderPass();
            buffer.sortForRendering();
            buffer.sortByTransformIndex(true);
        });
    }
    
    @Test
    public void testSortSingleCommand() {
        DrawCommand cmd = new DrawCommand(1, 1, 1, RenderPass.OPAQUE);
        buffer.add(cmd);
        
        buffer.sortForRendering();
        
        assertEquals(1, buffer.size());
        assertEquals(cmd, buffer.get(0));
    }
}

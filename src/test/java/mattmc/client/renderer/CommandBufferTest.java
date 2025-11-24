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
}

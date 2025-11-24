package mattmc.client.renderer;

import mattmc.client.renderer.backend.RenderBackend;
import mattmc.client.renderer.backend.DrawCommand;
import mattmc.client.renderer.backend.RenderPass;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the DrawCommand class.
 * 
 * These tests verify the behavior of DrawCommand objects, including creation,
 * equality, hashing, and string representation. DrawCommand is a core data
 * structure introduced in Stage 1 of the rendering refactor.
 */
public class DrawCommandTest {
    
    @Test
    public void testDrawCommandCreation() {
        // Test basic creation of a draw command
        DrawCommand cmd = new DrawCommand(1, 2, 3, RenderPass.OPAQUE);
        
        assertEquals(1, cmd.meshId);
        assertEquals(2, cmd.materialId);
        assertEquals(3, cmd.transformIndex);
        assertEquals(RenderPass.OPAQUE, cmd.pass);
    }
    
    @Test
    public void testDrawCommandWithDifferentPasses() {
        // Test that commands can be created with each render pass type
        DrawCommand opaque = new DrawCommand(1, 1, 1, RenderPass.OPAQUE);
        DrawCommand transparent = new DrawCommand(2, 2, 2, RenderPass.TRANSPARENT);
        DrawCommand shadow = new DrawCommand(3, 3, 3, RenderPass.SHADOW);
        DrawCommand ui = new DrawCommand(4, 4, 4, RenderPass.UI);
        
        assertEquals(RenderPass.OPAQUE, opaque.pass);
        assertEquals(RenderPass.TRANSPARENT, transparent.pass);
        assertEquals(RenderPass.SHADOW, shadow.pass);
        assertEquals(RenderPass.UI, ui.pass);
    }
    
    @Test
    public void testDrawCommandWithZeroValues() {
        // Test that zero values are valid (might represent default/null resources)
        DrawCommand cmd = new DrawCommand(0, 0, 0, RenderPass.OPAQUE);
        
        assertEquals(0, cmd.meshId);
        assertEquals(0, cmd.materialId);
        assertEquals(0, cmd.transformIndex);
    }
    
    @Test
    public void testDrawCommandWithNegativeValues() {
        // Test that negative values can be stored (might be used as special flags)
        DrawCommand cmd = new DrawCommand(-1, -2, -3, RenderPass.OPAQUE);
        
        assertEquals(-1, cmd.meshId);
        assertEquals(-2, cmd.materialId);
        assertEquals(-3, cmd.transformIndex);
    }
    
    @Test
    public void testDrawCommandWithLargeValues() {
        // Test with large ID values to ensure no overflow issues
        int largeId = Integer.MAX_VALUE;
        DrawCommand cmd = new DrawCommand(largeId, largeId - 1, largeId - 2, RenderPass.UI);
        
        assertEquals(largeId, cmd.meshId);
        assertEquals(largeId - 1, cmd.materialId);
        assertEquals(largeId - 2, cmd.transformIndex);
    }
    
    @Test
    public void testDrawCommandEquality() {
        // Test equality of identical commands
        DrawCommand cmd1 = new DrawCommand(10, 20, 30, RenderPass.OPAQUE);
        DrawCommand cmd2 = new DrawCommand(10, 20, 30, RenderPass.OPAQUE);
        
        assertEquals(cmd1, cmd2, "Identical commands should be equal");
        assertEquals(cmd2, cmd1, "Equality should be symmetric");
    }
    
    @Test
    public void testDrawCommandEqualityReflexive() {
        // Test reflexive property: x.equals(x) should be true
        DrawCommand cmd = new DrawCommand(1, 2, 3, RenderPass.OPAQUE);
        assertEquals(cmd, cmd, "Command should equal itself");
    }
    
    @Test
    public void testDrawCommandInequalityDifferentMesh() {
        // Test inequality when meshId differs
        DrawCommand cmd1 = new DrawCommand(10, 20, 30, RenderPass.OPAQUE);
        DrawCommand cmd2 = new DrawCommand(11, 20, 30, RenderPass.OPAQUE);
        
        assertNotEquals(cmd1, cmd2, "Commands with different meshId should not be equal");
    }
    
    @Test
    public void testDrawCommandInequalityDifferentMaterial() {
        // Test inequality when materialId differs
        DrawCommand cmd1 = new DrawCommand(10, 20, 30, RenderPass.OPAQUE);
        DrawCommand cmd2 = new DrawCommand(10, 21, 30, RenderPass.OPAQUE);
        
        assertNotEquals(cmd1, cmd2, "Commands with different materialId should not be equal");
    }
    
    @Test
    public void testDrawCommandInequalityDifferentTransform() {
        // Test inequality when transformIndex differs
        DrawCommand cmd1 = new DrawCommand(10, 20, 30, RenderPass.OPAQUE);
        DrawCommand cmd2 = new DrawCommand(10, 20, 31, RenderPass.OPAQUE);
        
        assertNotEquals(cmd1, cmd2, "Commands with different transformIndex should not be equal");
    }
    
    @Test
    public void testDrawCommandInequalityDifferentPass() {
        // Test inequality when pass differs
        DrawCommand cmd1 = new DrawCommand(10, 20, 30, RenderPass.OPAQUE);
        DrawCommand cmd2 = new DrawCommand(10, 20, 30, RenderPass.TRANSPARENT);
        
        assertNotEquals(cmd1, cmd2, "Commands with different pass should not be equal");
    }
    
    @Test
    public void testDrawCommandEqualityWithNull() {
        // Test that equals returns false for null
        DrawCommand cmd = new DrawCommand(1, 2, 3, RenderPass.OPAQUE);
        
        assertNotEquals(null, cmd, "Command should not equal null");
        assertNotEquals(cmd, null, "Command should not equal null (symmetric)");
    }
    
    @Test
    public void testDrawCommandEqualityWithDifferentType() {
        // Test that equals returns false for different types
        DrawCommand cmd = new DrawCommand(1, 2, 3, RenderPass.OPAQUE);
        String notACommand = "Not a DrawCommand";
        
        assertNotEquals(cmd, notACommand, "Command should not equal a String");
    }
    
    @Test
    public void testDrawCommandHashCode() {
        // Test that equal commands have equal hash codes
        DrawCommand cmd1 = new DrawCommand(10, 20, 30, RenderPass.OPAQUE);
        DrawCommand cmd2 = new DrawCommand(10, 20, 30, RenderPass.OPAQUE);
        
        assertEquals(cmd1.hashCode(), cmd2.hashCode(), 
                    "Equal commands should have equal hash codes");
    }
    
    @Test
    public void testDrawCommandHashCodeConsistency() {
        // Test that hash code is consistent across multiple calls
        DrawCommand cmd = new DrawCommand(10, 20, 30, RenderPass.OPAQUE);
        
        int hash1 = cmd.hashCode();
        int hash2 = cmd.hashCode();
        int hash3 = cmd.hashCode();
        
        assertEquals(hash1, hash2, "Hash code should be consistent");
        assertEquals(hash2, hash3, "Hash code should be consistent");
    }
    
    @Test
    public void testDrawCommandInHashSet() {
        // Test that commands work correctly in hash-based collections
        Set<DrawCommand> commandSet = new HashSet<>();
        
        DrawCommand cmd1 = new DrawCommand(1, 2, 3, RenderPass.OPAQUE);
        DrawCommand cmd2 = new DrawCommand(1, 2, 3, RenderPass.OPAQUE); // Equal to cmd1
        DrawCommand cmd3 = new DrawCommand(4, 5, 6, RenderPass.TRANSPARENT);
        
        commandSet.add(cmd1);
        commandSet.add(cmd2); // Should not add duplicate
        commandSet.add(cmd3);
        
        assertEquals(2, commandSet.size(), "Set should contain only 2 unique commands");
        assertTrue(commandSet.contains(cmd1), "Set should contain cmd1");
        assertTrue(commandSet.contains(cmd2), "Set should contain equivalent of cmd2");
        assertTrue(commandSet.contains(cmd3), "Set should contain cmd3");
    }
    
    @Test
    public void testDrawCommandToString() {
        // Test string representation for debugging
        DrawCommand cmd = new DrawCommand(100, 200, 300, RenderPass.OPAQUE);
        String str = cmd.toString();
        
        assertNotNull(str, "toString should not return null");
        assertTrue(str.contains("100"), "String should contain meshId");
        assertTrue(str.contains("200"), "String should contain materialId");
        assertTrue(str.contains("300"), "String should contain transformIndex");
        assertTrue(str.contains("OPAQUE"), "String should contain pass name");
    }
    
    @Test
    public void testDrawCommandToStringAllPasses() {
        // Test that toString works for all render passes
        DrawCommand[] commands = {
            new DrawCommand(1, 2, 3, RenderPass.OPAQUE),
            new DrawCommand(1, 2, 3, RenderPass.TRANSPARENT),
            new DrawCommand(1, 2, 3, RenderPass.SHADOW),
            new DrawCommand(1, 2, 3, RenderPass.UI)
        };
        
        for (DrawCommand cmd : commands) {
            String str = cmd.toString();
            assertNotNull(str, "toString should not return null for " + cmd.pass);
            assertTrue(str.contains(cmd.pass.name()), 
                      "toString should contain pass name for " + cmd.pass);
        }
    }
    
    @Test
    public void testDrawCommandImmutability() {
        // Test that DrawCommand fields are final and cannot be changed
        // This is a compile-time check, but we verify at runtime that the fields exist
        DrawCommand cmd = new DrawCommand(1, 2, 3, RenderPass.OPAQUE);
        
        // Attempting to access fields should work
        assertEquals(1, cmd.meshId);
        assertEquals(2, cmd.materialId);
        assertEquals(3, cmd.transformIndex);
        assertEquals(RenderPass.OPAQUE, cmd.pass);
        
        // Note: Since fields are final, there's no way to modify them at runtime
        // This test documents the intended immutability
    }
    
    @Test
    public void testMultipleCommandsIndependent() {
        // Test that creating multiple commands doesn't cause interference
        DrawCommand cmd1 = new DrawCommand(1, 2, 3, RenderPass.OPAQUE);
        DrawCommand cmd2 = new DrawCommand(4, 5, 6, RenderPass.TRANSPARENT);
        DrawCommand cmd3 = new DrawCommand(7, 8, 9, RenderPass.UI);
        
        // Verify each command retains its own values
        assertEquals(1, cmd1.meshId);
        assertEquals(4, cmd2.meshId);
        assertEquals(7, cmd3.meshId);
        
        assertEquals(RenderPass.OPAQUE, cmd1.pass);
        assertEquals(RenderPass.TRANSPARENT, cmd2.pass);
        assertEquals(RenderPass.UI, cmd3.pass);
    }
}

package mattmc.world.item;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the ItemStack class.
 */
public class ItemStackTest {
    
    @Test
    public void testCreateItemStackWithDefaultCount() {
        ItemStack stack = new ItemStack(Items.STONE);
        assertEquals(Items.STONE, stack.getItem());
        assertEquals(1, stack.getCount());
    }
    
    @Test
    public void testCreateItemStackWithCustomCount() {
        ItemStack stack = new ItemStack(Items.STONE, 32);
        assertEquals(Items.STONE, stack.getItem());
        assertEquals(32, stack.getCount());
    }
    
    @Test
    public void testCreateItemStackWithMaxCount() {
        ItemStack stack = new ItemStack(Items.STONE, 64);
        assertEquals(64, stack.getCount());
    }
    
    @Test
    public void testCreateItemStackWithNullItemThrows() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ItemStack(null);
        });
    }
    
    @Test
    public void testCreateItemStackWithZeroCountThrows() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ItemStack(Items.STONE, 0);
        });
    }
    
    @Test
    public void testCreateItemStackWithNegativeCountThrows() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ItemStack(Items.STONE, -1);
        });
    }
    
    @Test
    public void testCreateItemStackExceedingMaxStackSizeThrows() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ItemStack(Items.STONE, 65);
        });
    }
    
    @Test
    public void testSetCount() {
        ItemStack stack = new ItemStack(Items.STONE, 10);
        stack.setCount(20);
        assertEquals(20, stack.getCount());
    }
    
    @Test
    public void testSetCountToZeroThrows() {
        ItemStack stack = new ItemStack(Items.STONE, 10);
        assertThrows(IllegalArgumentException.class, () -> {
            stack.setCount(0);
        });
    }
    
    @Test
    public void testSetCountExceedingMaxThrows() {
        ItemStack stack = new ItemStack(Items.STONE, 10);
        assertThrows(IllegalArgumentException.class, () -> {
            stack.setCount(65);
        });
    }
    
    @Test
    public void testGrow() {
        ItemStack stack = new ItemStack(Items.STONE, 10);
        int added = stack.grow(20);
        assertEquals(20, added);
        assertEquals(30, stack.getCount());
    }
    
    @Test
    public void testGrowHitsMaxStackSize() {
        ItemStack stack = new ItemStack(Items.STONE, 60);
        int added = stack.grow(10);
        assertEquals(4, added); // Can only add 4 to reach 64
        assertEquals(64, stack.getCount());
    }
    
    @Test
    public void testShrink() {
        ItemStack stack = new ItemStack(Items.STONE, 10);
        stack.shrink(5);
        assertEquals(5, stack.getCount());
    }
    
    @Test
    public void testShrinkToZeroThrows() {
        ItemStack stack = new ItemStack(Items.STONE, 5);
        assertThrows(IllegalArgumentException.class, () -> {
            stack.shrink(5);
        });
    }
    
    @Test
    public void testShrinkBelowZeroThrows() {
        ItemStack stack = new ItemStack(Items.STONE, 5);
        assertThrows(IllegalArgumentException.class, () -> {
            stack.shrink(10);
        });
    }
    
    @Test
    public void testIsEmpty() {
        ItemStack stack = new ItemStack(Items.STONE, 1);
        assertFalse(stack.isEmpty());
    }
    
    @Test
    public void testIsFull() {
        ItemStack notFull = new ItemStack(Items.STONE, 32);
        assertFalse(notFull.isFull());
        
        ItemStack full = new ItemStack(Items.STONE, 64);
        assertTrue(full.isFull());
    }
    
    @Test
    public void testCanMergeWith() {
        ItemStack stack1 = new ItemStack(Items.STONE, 32);
        ItemStack stack2 = new ItemStack(Items.STONE, 10);
        ItemStack stack3 = new ItemStack(Items.COBBLESTONE, 10);
        ItemStack full = new ItemStack(Items.STONE, 64);
        
        assertTrue(stack1.canMergeWith(stack2), "Same item, not full should merge");
        assertFalse(stack1.canMergeWith(stack3), "Different items should not merge");
        assertFalse(full.canMergeWith(stack2), "Full stack should not merge");
        assertFalse(stack1.canMergeWith(null), "Null stack should not merge");
    }
    
    @Test
    public void testCopy() {
        ItemStack original = new ItemStack(Items.STONE, 32);
        ItemStack copy = original.copy();
        
        assertNotSame(original, copy);
        assertEquals(original.getItem(), copy.getItem());
        assertEquals(original.getCount(), copy.getCount());
        
        // Modifying copy should not affect original
        copy.setCount(10);
        assertEquals(32, original.getCount());
        assertEquals(10, copy.getCount());
    }
    
    @Test
    public void testCopyWithCount() {
        ItemStack original = new ItemStack(Items.STONE, 32);
        ItemStack copy = original.copyWithCount(16);
        
        assertNotSame(original, copy);
        assertEquals(original.getItem(), copy.getItem());
        assertEquals(16, copy.getCount());
        assertEquals(32, original.getCount());
    }
    
    @Test
    public void testToString() {
        ItemStack stack = new ItemStack(Items.STONE, 32);
        String str = stack.toString();
        assertTrue(str.contains("32"));
        assertTrue(str.contains("mattmc:stone"));
    }
}

package mattmc.world.item;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for hotbar scrolling behavior.
 * Simulates the scroll logic from DevplayInputHandler.
 */
public class HotbarScrollingTest {
    
    private Inventory inventory;
    
    @BeforeEach
    public void setUp() {
        inventory = new Inventory();
    }
    
    /**
     * Simulates scrolling down (next slot).
     */
    private void scrollDown() {
        int currentSlot = inventory.getSelectedSlot();
        int newSlot = (currentSlot + 1) % 9;
        inventory.setSelectedSlot(newSlot);
    }
    
    /**
     * Simulates scrolling up (previous slot).
     */
    private void scrollUp() {
        int currentSlot = inventory.getSelectedSlot();
        int newSlot = (currentSlot - 1 + 9) % 9;
        inventory.setSelectedSlot(newSlot);
    }
    
    @Test
    public void testScrollDownFromSlot0() {
        inventory.setSelectedSlot(0);
        scrollDown();
        assertEquals(1, inventory.getSelectedSlot());
    }
    
    @Test
    public void testScrollDownFromSlot8WrapsToSlot0() {
        inventory.setSelectedSlot(8);
        scrollDown();
        assertEquals(0, inventory.getSelectedSlot());
    }
    
    @Test
    public void testScrollUpFromSlot1() {
        inventory.setSelectedSlot(1);
        scrollUp();
        assertEquals(0, inventory.getSelectedSlot());
    }
    
    @Test
    public void testScrollUpFromSlot0WrapsToSlot8() {
        inventory.setSelectedSlot(0);
        scrollUp();
        assertEquals(8, inventory.getSelectedSlot());
    }
    
    @Test
    public void testMultipleScrollsDown() {
        inventory.setSelectedSlot(0);
        scrollDown();
        scrollDown();
        scrollDown();
        assertEquals(3, inventory.getSelectedSlot());
    }
    
    @Test
    public void testMultipleScrollsUp() {
        inventory.setSelectedSlot(5);
        scrollUp();
        scrollUp();
        scrollUp();
        assertEquals(2, inventory.getSelectedSlot());
    }
    
    @Test
    public void testScrollDownAndUpCancelsOut() {
        inventory.setSelectedSlot(4);
        scrollDown();
        scrollUp();
        assertEquals(4, inventory.getSelectedSlot());
    }
    
    @Test
    public void testScrollFullCycleDown() {
        inventory.setSelectedSlot(0);
        for (int i = 0; i < 9; i++) {
            scrollDown();
        }
        assertEquals(0, inventory.getSelectedSlot());
    }
    
    @Test
    public void testScrollFullCycleUp() {
        inventory.setSelectedSlot(0);
        for (int i = 0; i < 9; i++) {
            scrollUp();
        }
        assertEquals(0, inventory.getSelectedSlot());
    }
}

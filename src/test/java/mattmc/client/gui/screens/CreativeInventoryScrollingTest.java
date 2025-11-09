package mattmc.client.gui.screens;

import mattmc.world.item.Item;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for creative inventory scrolling behavior.
 * 
 * Tests verify that:
 * 1. Scrolling only works when items exceed available slots
 * 2. Scroll direction is correct (down shows next items, up shows previous)
 * 3. Scroll bounds are properly enforced
 */
public class CreativeInventoryScrollingTest {
    
    // Constants matching InventoryScreen
    private static final int CREATIVE_COLS = 9;
    private static final int CREATIVE_ROWS = 15;
    private static final int AVAILABLE_SLOTS = CREATIVE_COLS * CREATIVE_ROWS; // 135
    
    /**
     * Simulates the handleCreativeScroll logic for testing.
     */
    private int simulateScroll(int itemCount, int currentScrollRow, double scrollYOffset) {
        int totalRows = (itemCount + CREATIVE_COLS - 1) / CREATIVE_COLS;
        int maxScrollRow = Math.max(0, totalRows - CREATIVE_ROWS);
        
        int newScrollRow = currentScrollRow;
        
        // Only allow scrolling if there are more items than can fit
        if (maxScrollRow > 0) {
            // Scroll down (negative yoffset) = increase row to show later items
            // Scroll up (positive yoffset) = decrease row to show earlier items
            newScrollRow += (int) -scrollYOffset;
            newScrollRow = Math.max(0, Math.min(newScrollRow, maxScrollRow));
        }
        
        return newScrollRow;
    }
    
    @Test
    public void testNoScrollingWhenItemsFitInAvailableSlots() {
        // Test with items that exactly fit (135 items = 15 rows)
        int itemCount = AVAILABLE_SLOTS;
        int scrollRow = 0;
        
        // Try scrolling down
        int newScrollRow = simulateScroll(itemCount, scrollRow, -1.0);
        assertEquals(0, newScrollRow, "Should not scroll when items fit exactly");
        
        // Try scrolling up
        newScrollRow = simulateScroll(itemCount, scrollRow, 1.0);
        assertEquals(0, newScrollRow, "Should not scroll when items fit exactly");
    }
    
    @Test
    public void testNoScrollingWhenFewerItemsThanSlots() {
        // Test with fewer items than available slots (100 items < 135 slots)
        int itemCount = 100;
        int scrollRow = 0;
        
        // Try scrolling down
        int newScrollRow = simulateScroll(itemCount, scrollRow, -1.0);
        assertEquals(0, newScrollRow, "Should not scroll when items < available slots");
        
        // Try scrolling up
        newScrollRow = simulateScroll(itemCount, scrollRow, 1.0);
        assertEquals(0, newScrollRow, "Should not scroll when items < available slots");
    }
    
    @Test
    public void testScrollingEnabledWhenMoreItemsThanSlots() {
        // Test with more items than available slots (150 items > 135 slots)
        int itemCount = 150; // 17 rows (150/9 rounded up)
        int scrollRow = 0;
        
        // Scroll down should increase scroll row
        int newScrollRow = simulateScroll(itemCount, scrollRow, -1.0);
        assertEquals(1, newScrollRow, "Scrolling down should increase scroll row");
        
        // Scroll up should decrease scroll row (but can't go below 0)
        newScrollRow = simulateScroll(itemCount, scrollRow, 1.0);
        assertEquals(0, newScrollRow, "Scrolling up from row 0 should stay at 0");
    }
    
    @Test
    public void testScrollDownShowsNextRow() {
        // Test with 200 items (23 rows)
        int itemCount = 200;
        int scrollRow = 0;
        
        // Scroll down once (yoffset = -1.0)
        int newScrollRow = simulateScroll(itemCount, scrollRow, -1.0);
        assertEquals(1, newScrollRow, "First scroll down should show row 1");
        
        // Scroll down again
        newScrollRow = simulateScroll(itemCount, newScrollRow, -1.0);
        assertEquals(2, newScrollRow, "Second scroll down should show row 2");
        
        // Scroll down multiple times
        newScrollRow = simulateScroll(itemCount, newScrollRow, -5.0);
        assertEquals(7, newScrollRow, "Large scroll should increase by 5");
    }
    
    @Test
    public void testScrollUpShowsPreviousRow() {
        // Test with 300 items (34 rows, max scroll = 19)
        int itemCount = 300;
        int scrollRow = 10;
        
        // Scroll up once (yoffset = 1.0)
        int newScrollRow = simulateScroll(itemCount, scrollRow, 1.0);
        assertEquals(9, newScrollRow, "First scroll up should show row 9");
        
        // Scroll up again
        newScrollRow = simulateScroll(itemCount, newScrollRow, 1.0);
        assertEquals(8, newScrollRow, "Second scroll up should show row 8");
        
        // Scroll up multiple times
        newScrollRow = simulateScroll(itemCount, newScrollRow, 5.0);
        assertEquals(3, newScrollRow, "Large scroll should decrease by 5");
    }
    
    @Test
    public void testScrollBoundsAtTop() {
        // Test that scrolling up at row 0 stays at row 0
        int itemCount = 200;
        int scrollRow = 0;
        
        // Try scrolling up multiple times
        int newScrollRow = simulateScroll(itemCount, scrollRow, 10.0);
        assertEquals(0, newScrollRow, "Should not scroll above row 0");
    }
    
    @Test
    public void testScrollBoundsAtBottom() {
        // Test with 200 items (23 rows total)
        // Max scroll row = 23 - 15 = 8
        int itemCount = 200;
        int totalRows = (itemCount + CREATIVE_COLS - 1) / CREATIVE_COLS; // 23
        int maxScrollRow = totalRows - CREATIVE_ROWS; // 8
        int scrollRow = maxScrollRow;
        
        // Try scrolling down multiple times
        int newScrollRow = simulateScroll(itemCount, scrollRow, -10.0);
        assertEquals(maxScrollRow, newScrollRow, "Should not scroll beyond max scroll row");
    }
    
    @Test
    public void testScrollWithExactlyOneExtraRow() {
        // Test with 136 items (16 rows, just 1 more than fits)
        int itemCount = 136;
        int scrollRow = 0;
        
        // Max scroll should be 1 (16 - 15 = 1)
        int totalRows = (itemCount + CREATIVE_COLS - 1) / CREATIVE_COLS; // 16
        int expectedMaxScroll = totalRows - CREATIVE_ROWS; // 1
        
        // Scroll down once
        int newScrollRow = simulateScroll(itemCount, scrollRow, -1.0);
        assertEquals(1, newScrollRow, "Should scroll to row 1");
        
        // Try scrolling down again - should stay at max
        newScrollRow = simulateScroll(itemCount, newScrollRow, -1.0);
        assertEquals(expectedMaxScroll, newScrollRow, "Should not exceed max scroll row");
    }
    
    @Test
    public void testScrollingWithLargeItemCount() {
        // Test with 500 items (56 rows)
        int itemCount = 500;
        int totalRows = (itemCount + CREATIVE_COLS - 1) / CREATIVE_COLS; // 56
        int maxScrollRow = totalRows - CREATIVE_ROWS; // 41
        int scrollRow = 0;
        
        // Scroll down to middle
        int newScrollRow = simulateScroll(itemCount, scrollRow, -20.0);
        assertEquals(20, newScrollRow, "Should scroll to row 20");
        
        // Scroll to max
        newScrollRow = simulateScroll(itemCount, newScrollRow, -30.0);
        assertEquals(maxScrollRow, newScrollRow, "Should clamp at max scroll row");
        
        // Scroll back up
        newScrollRow = simulateScroll(itemCount, newScrollRow, 10.0);
        assertEquals(maxScrollRow - 10, newScrollRow, "Should scroll up by 10 rows");
    }
    
    @Test
    public void testScrollCalculatesCorrectItemIndex() {
        // Test that scroll row correctly maps to item indices
        // With 200 items and scroll row 5, first visible item should be at index 45 (5 * 9)
        int itemCount = 200;
        int scrollRow = 5;
        
        // First visible row
        int firstVisibleRow = scrollRow;
        int firstVisibleIndex = firstVisibleRow * CREATIVE_COLS;
        assertEquals(45, firstVisibleIndex, "First visible item at row 5 should be index 45");
        
        // Last visible row (row 5 + 14 = row 19)
        int lastVisibleRow = scrollRow + CREATIVE_ROWS - 1;
        int lastVisibleIndex = lastVisibleRow * CREATIVE_COLS + CREATIVE_COLS - 1;
        assertEquals(179, lastVisibleIndex, "Last visible item should be at index 179");
    }
}

package mattmc.world.item;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the CreativeTab class.
 */
public class CreativeTabTest {
    
    private CreativeTab tab;
    private Item testItem1;
    private Item testItem2;
    
    @BeforeEach
    public void setUp() {
        testItem1 = new Item(64, "test:item1");
        testItem2 = new Item(1, "test:item2");
        tab = new CreativeTab("test_tab", testItem1);
    }
    
    @Test
    public void testCreation() {
        assertEquals("test_tab", tab.getIdentifier());
        assertEquals(testItem1, tab.getIcon());
        assertEquals("Test Tab", tab.getDisplayName());
        assertEquals(0, tab.getItemCount());
    }
    
    @Test
    public void testCreationWithCustomDisplayName() {
        CreativeTab customTab = new CreativeTab("custom_tab", testItem1, "My Custom Tab");
        assertEquals("My Custom Tab", customTab.getDisplayName());
    }
    
    @Test
    public void testAddItem() {
        tab.addItem(testItem1);
        assertEquals(1, tab.getItemCount());
        assertTrue(tab.containsItem(testItem1));
    }
    
    @Test
    public void testAddMultipleItems() {
        tab.addItem(testItem1);
        tab.addItem(testItem2);
        assertEquals(2, tab.getItemCount());
        assertTrue(tab.containsItem(testItem1));
        assertTrue(tab.containsItem(testItem2));
    }
    
    @Test
    public void testAddDuplicateItem() {
        tab.addItem(testItem1);
        tab.addItem(testItem1);
        // Should not add duplicates
        assertEquals(1, tab.getItemCount());
    }
    
    @Test
    public void testAddNullItem() {
        tab.addItem(null);
        // Should ignore null
        assertEquals(0, tab.getItemCount());
    }
    
    @Test
    public void testRemoveItem() {
        tab.addItem(testItem1);
        tab.addItem(testItem2);
        tab.removeItem(testItem1);
        assertEquals(1, tab.getItemCount());
        assertFalse(tab.containsItem(testItem1));
        assertTrue(tab.containsItem(testItem2));
    }
    
    @Test
    public void testGetItems() {
        tab.addItem(testItem1);
        tab.addItem(testItem2);
        var items = tab.getItems();
        assertEquals(2, items.size());
        assertTrue(items.contains(testItem1));
        assertTrue(items.contains(testItem2));
    }
    
    @Test
    public void testGetItemsIsUnmodifiable() {
        tab.addItem(testItem1);
        var items = tab.getItems();
        assertThrows(UnsupportedOperationException.class, () -> {
            items.add(testItem2);
        });
    }
    
    @Test
    public void testClear() {
        tab.addItem(testItem1);
        tab.addItem(testItem2);
        tab.clear();
        assertEquals(0, tab.getItemCount());
        assertFalse(tab.containsItem(testItem1));
        assertFalse(tab.containsItem(testItem2));
    }
    
    @Test
    public void testMethodChaining() {
        CreativeTab result = tab.addItem(testItem1).addItem(testItem2);
        assertSame(tab, result);
        assertEquals(2, tab.getItemCount());
    }
    
    @Test
    public void testNullIdentifierThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            new CreativeTab(null, testItem1);
        });
    }
    
    @Test
    public void testNullIconThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            new CreativeTab("test", null);
        });
    }
    
    @Test
    public void testDisplayNameFormatting() {
        CreativeTab tab1 = new CreativeTab("single", testItem1);
        assertEquals("Single", tab1.getDisplayName());
        
        CreativeTab tab2 = new CreativeTab("two_words", testItem1);
        assertEquals("Two Words", tab2.getDisplayName());
        
        CreativeTab tab3 = new CreativeTab("three_word_name", testItem1);
        assertEquals("Three Word Name", tab3.getDisplayName());
    }
}

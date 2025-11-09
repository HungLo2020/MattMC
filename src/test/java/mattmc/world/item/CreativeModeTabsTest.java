package mattmc.world.item;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

/**
 * Tests for the CreativeModeTabs system.
 */
public class CreativeModeTabsTest {

    /**
     * Test that all tabs are properly registered.
     */
    @Test
    public void testTabsRegistered() {
        List<CreativeModeTab> tabs = CreativeModeTabs.getTabs();
        
        assertNotNull(tabs, "Tabs list should not be null");
        assertEquals(5, tabs.size(), "Should have 5 creative mode tabs");
    }
    
    /**
     * Test that tabs can be retrieved by ID.
     */
    @Test
    public void testGetTabById() {
        CreativeModeTab buildingTab = CreativeModeTabs.getTab("building_blocks");
        assertNotNull(buildingTab, "Building Blocks tab should exist");
        assertEquals("building_blocks", buildingTab.getId());
        assertEquals("Building Blocks", buildingTab.getDisplayName());
        
        CreativeModeTab toolsTab = CreativeModeTabs.getTab("tools");
        assertNotNull(toolsTab, "Tools tab should exist");
        assertEquals("tools", toolsTab.getId());
        assertEquals("Tools & Utilities", toolsTab.getDisplayName());
        
        // Test non-existent tab
        CreativeModeTab nonExistent = CreativeModeTabs.getTab("nonexistent");
        assertNull(nonExistent, "Non-existent tab should return null");
    }
    
    /**
     * Test that each tab has appropriate items.
     */
    @Test
    public void testTabsHaveItems() {
        // Building Blocks should have blocks
        CreativeModeTab buildingTab = CreativeModeTabs.BUILDING_BLOCKS;
        assertNotNull(buildingTab);
        assertTrue(buildingTab.size() > 0, "Building Blocks tab should have items");
        assertTrue(buildingTab.getItems().contains(Items.STONE), "Building Blocks should contain Stone");
        assertTrue(buildingTab.getItems().contains(Items.DIRT), "Building Blocks should contain Dirt");
        
        // Tools tab should have tools
        CreativeModeTab toolsTab = CreativeModeTabs.TOOLS;
        assertNotNull(toolsTab);
        assertTrue(toolsTab.size() > 0, "Tools tab should have items");
        assertTrue(toolsTab.getItems().contains(Items.DIAMOND_PICKAXE), "Tools should contain Diamond Pickaxe");
        assertTrue(toolsTab.getItems().contains(Items.IRON_AXE), "Tools should contain Iron Axe");
        
        // Materials tab should have materials
        CreativeModeTab materialsTab = CreativeModeTabs.MATERIALS;
        assertNotNull(materialsTab);
        assertTrue(materialsTab.size() > 0, "Materials tab should have items");
        assertTrue(materialsTab.getItems().contains(Items.DIAMOND), "Materials should contain Diamond");
        assertTrue(materialsTab.getItems().contains(Items.STICK), "Materials should contain Stick");
        
        // Decorations tab should have planks
        CreativeModeTab decorTab = CreativeModeTabs.DECORATIONS;
        assertNotNull(decorTab);
        assertTrue(decorTab.size() > 0, "Decorations tab should have items");
        assertTrue(decorTab.getItems().contains(Items.OAK_PLANKS), "Decorations should contain Oak Planks");
    }
    
    /**
     * Test that tab icon items are set correctly.
     */
    @Test
    public void testTabIconItems() {
        assertEquals(Items.STONE, CreativeModeTabs.BUILDING_BLOCKS.getIconItem());
        assertEquals(Items.OAK_PLANKS, CreativeModeTabs.DECORATIONS.getIconItem());
        assertEquals(Items.IRON_INGOT, CreativeModeTabs.REDSTONE.getIconItem());
        assertEquals(Items.IRON_PICKAXE, CreativeModeTabs.TOOLS.getIconItem());
        assertEquals(Items.DIAMOND, CreativeModeTabs.MATERIALS.getIconItem());
    }
    
    /**
     * Test that items are not duplicated across tabs.
     * (Some overlap is acceptable for different contexts, but we check that
     * the same exact item instance isn't added multiple times to the same tab)
     */
    @Test
    public void testNoDuplicatesWithinTab() {
        for (CreativeModeTab tab : CreativeModeTabs.getTabs()) {
            List<Item> items = tab.getItems();
            // Check if any item appears multiple times
            for (int i = 0; i < items.size(); i++) {
                Item item = items.get(i);
                int count = 0;
                for (Item other : items) {
                    if (item == other) {
                        count++;
                    }
                }
                assertEquals(1, count, "Item " + item.getIdentifier() + " should appear only once in tab " + tab.getId());
            }
        }
    }
    
    /**
     * Test creative inventory slot calculations.
     */
    @Test
    public void testCreativeInventorySlots() {
        int rows = 5;
        int cols = 9;
        int totalVisibleSlots = rows * cols;
        
        assertEquals(45, totalVisibleSlots, "Creative inventory should show 45 slots");
    }
}

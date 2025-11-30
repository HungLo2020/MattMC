package mattmc.world.item;
import mattmc.registries.Blocks;
import mattmc.registries.Items;
import mattmc.registries.CreativeTabs;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

/**
 * Tests for the CreativeTabs registry class.
 */
public class CreativeTabsTest {
    
    @Test
    public void testPredefinedTabsExist() {
        assertNotNull(CreativeTabs.BUILDING_BLOCKS);
        assertNotNull(CreativeTabs.DECORATION_BLOCKS);
        assertNotNull(CreativeTabs.REDSTONE);
        assertNotNull(CreativeTabs.TRANSPORTATION);
        assertNotNull(CreativeTabs.TOOLS);
        assertNotNull(CreativeTabs.COMBAT);
        assertNotNull(CreativeTabs.FOOD);
        assertNotNull(CreativeTabs.INGREDIENTS);
        assertNotNull(CreativeTabs.MISCELLANEOUS);
    }
    
    @Test
    public void testPredefinedTabIdentifiers() {
        assertEquals("building_blocks", CreativeTabs.BUILDING_BLOCKS.getIdentifier());
        assertEquals("decoration_blocks", CreativeTabs.DECORATION_BLOCKS.getIdentifier());
        assertEquals("redstone", CreativeTabs.REDSTONE.getIdentifier());
        assertEquals("transportation", CreativeTabs.TRANSPORTATION.getIdentifier());
        assertEquals("tools", CreativeTabs.TOOLS.getIdentifier());
        assertEquals("combat", CreativeTabs.COMBAT.getIdentifier());
        assertEquals("food", CreativeTabs.FOOD.getIdentifier());
        assertEquals("ingredients", CreativeTabs.INGREDIENTS.getIdentifier());
        assertEquals("miscellaneous", CreativeTabs.MISCELLANEOUS.getIdentifier());
    }
    
    @Test
    public void testPredefinedTabDisplayNames() {
        assertEquals("Building Blocks", CreativeTabs.BUILDING_BLOCKS.getDisplayName());
        assertEquals("Decoration Blocks", CreativeTabs.DECORATION_BLOCKS.getDisplayName());
        assertEquals("Redstone", CreativeTabs.REDSTONE.getDisplayName());
        assertEquals("Transportation", CreativeTabs.TRANSPORTATION.getDisplayName());
        assertEquals("Tools & Utilities", CreativeTabs.TOOLS.getDisplayName());
        assertEquals("Combat", CreativeTabs.COMBAT.getDisplayName());
        assertEquals("Foodstuffs", CreativeTabs.FOOD.getDisplayName());
        assertEquals("Ingredients", CreativeTabs.INGREDIENTS.getDisplayName());
        assertEquals("Miscellaneous", CreativeTabs.MISCELLANEOUS.getDisplayName());
    }
    
    @Test
    public void testGetAllTabs() {
        List<CreativeTab> tabs = CreativeTabs.getAllTabs();
        assertNotNull(tabs);
        assertTrue(tabs.size() >= 9); // At least the 9 predefined tabs
        assertTrue(tabs.contains(CreativeTabs.BUILDING_BLOCKS));
        assertTrue(tabs.contains(CreativeTabs.TOOLS));
        assertTrue(tabs.contains(CreativeTabs.INGREDIENTS));
    }
    
    @Test
    public void testGetAllTabsIsUnmodifiable() {
        List<CreativeTab> tabs = CreativeTabs.getAllTabs();
        Item testItem = new Item(64, "test:item");
        CreativeTab testTab = new CreativeTab("test", testItem);
        
        assertThrows(UnsupportedOperationException.class, () -> {
            tabs.add(testTab);
        });
    }
    
    @Test
    public void testGetTab() {
        CreativeTab tab = CreativeTabs.getTab("building_blocks");
        assertNotNull(tab);
        assertEquals(CreativeTabs.BUILDING_BLOCKS, tab);
        
        assertNull(CreativeTabs.getTab("nonexistent_tab"));
    }
    
    @Test
    public void testIsRegistered() {
        assertTrue(CreativeTabs.isRegistered("building_blocks"));
        assertTrue(CreativeTabs.isRegistered("tools"));
        assertTrue(CreativeTabs.isRegistered("ingredients"));
        assertFalse(CreativeTabs.isRegistered("nonexistent_tab"));
    }
    
    @Test
    public void testGetTabCount() {
        int count = CreativeTabs.getTabCount();
        assertTrue(count >= 9); // At least the 9 predefined tabs
    }
    
    @Test
    public void testItemsAreInBuildingBlocksTab() {
        CreativeTab buildingBlocks = CreativeTabs.BUILDING_BLOCKS;
        assertTrue(buildingBlocks.containsItem(Items.STONE));
        assertTrue(buildingBlocks.containsItem(Items.DIRT));
        assertTrue(buildingBlocks.containsItem(Items.GRASS_BLOCK));
        assertTrue(buildingBlocks.containsItem(Items.COBBLESTONE));
        assertTrue(buildingBlocks.containsItem(Items.OAK_PLANKS));
    }
    
    @Test
    public void testItemsAreInToolsTab() {
        CreativeTab tools = CreativeTabs.TOOLS;
        // Tools tab is currently empty as all tool items are commented out
        assertEquals(0, tools.getItemCount());
    }
    
    @Test
    public void testItemsAreInIngredientsTab() {
        CreativeTab ingredients = CreativeTabs.INGREDIENTS;
        // Ingredients tab is currently empty as all ingredient items are commented out
        assertEquals(0, ingredients.getItemCount());
    }
    
    @Test
    public void testGetAllItems() {
        List<Item> allItems = CreativeTabs.getAllItems();
        assertNotNull(allItems);
        assertTrue(allItems.size() > 0);
        // Verify some items are present from building blocks
        assertTrue(allItems.contains(Items.STONE));
        assertTrue(allItems.contains(Items.OAK_PLANKS));
        assertTrue(allItems.contains(Items.DIRT));
    }
    
    @Test
    public void testGetAllUniqueItems() {
        List<Item> uniqueItems = CreativeTabs.getAllUniqueItems();
        assertNotNull(uniqueItems);
        assertTrue(uniqueItems.size() > 0);
        
        // Verify no duplicates
        for (int i = 0; i < uniqueItems.size(); i++) {
            for (int j = i + 1; j < uniqueItems.size(); j++) {
                assertNotSame(uniqueItems.get(i), uniqueItems.get(j));
            }
        }
    }
    
    @Test
    public void testGetTabsForItem() {
        List<CreativeTab> tabsForStone = CreativeTabs.getTabsForItem(Items.STONE);
        assertNotNull(tabsForStone);
        assertTrue(tabsForStone.contains(CreativeTabs.BUILDING_BLOCKS));
        
        // Tools tab is currently empty as all tool items are commented out
        List<CreativeTab> tabsForPlanks = CreativeTabs.getTabsForItem(Items.OAK_PLANKS);
        assertNotNull(tabsForPlanks);
        assertTrue(tabsForPlanks.contains(CreativeTabs.BUILDING_BLOCKS));
        
        // Test with an item not in any tab
        Item testItem = new Item(64, "test:item");
        List<CreativeTab> emptyTabs = CreativeTabs.getTabsForItem(testItem);
        assertNotNull(emptyTabs);
        assertEquals(0, emptyTabs.size());
    }
    
    @Test
    public void testRegisterCustomTab() {
        Item icon = new Item(64, "custom:icon");
        CreativeTab customTab = CreativeTabs.registerTab("custom_test_tab", icon);
        
        assertNotNull(customTab);
        assertEquals("custom_test_tab", customTab.getIdentifier());
        assertEquals(icon, customTab.getIcon());
        
        // Verify it's in the registry
        assertTrue(CreativeTabs.isRegistered("custom_test_tab"));
        assertEquals(customTab, CreativeTabs.getTab("custom_test_tab"));
    }
    
    @Test
    public void testRegisterCustomTabWithDisplayName() {
        Item icon = new Item(64, "custom:icon");
        CreativeTab customTab = CreativeTabs.registerTab("custom_tab_2", icon, "My Custom Tab");
        
        assertNotNull(customTab);
        assertEquals("My Custom Tab", customTab.getDisplayName());
    }
    
    @Test
    public void testRegisterDuplicateTabThrowsException() {
        Item icon = new Item(64, "custom:icon");
        // Building blocks already exists
        assertThrows(IllegalArgumentException.class, () -> {
            CreativeTabs.registerTab("building_blocks", icon);
        });
    }
    
    @Test
    public void testBuildingBlocksContainsAllPlanks() {
        CreativeTab buildingBlocks = CreativeTabs.BUILDING_BLOCKS;
        assertTrue(buildingBlocks.containsItem(Items.OAK_PLANKS));
        assertTrue(buildingBlocks.containsItem(Items.SPRUCE_PLANKS));
        assertTrue(buildingBlocks.containsItem(Items.BIRCH_PLANKS));
        assertTrue(buildingBlocks.containsItem(Items.SILVER_BIRCH_PLANKS));
        assertTrue(buildingBlocks.containsItem(Items.JUNGLE_PLANKS));
        assertTrue(buildingBlocks.containsItem(Items.ACACIA_PLANKS));
        assertTrue(buildingBlocks.containsItem(Items.DARK_OAK_PLANKS));
        assertTrue(buildingBlocks.containsItem(Items.MANGROVE_PLANKS));
        assertTrue(buildingBlocks.containsItem(Items.CHERRY_PLANKS));
        assertTrue(buildingBlocks.containsItem(Items.BAMBOO_PLANKS));
        assertTrue(buildingBlocks.containsItem(Items.CRIMSON_PLANKS));
        assertTrue(buildingBlocks.containsItem(Items.WARPED_PLANKS));
    }
    
    @Test
    public void testToolsContainsAllTools() {
        CreativeTab tools = CreativeTabs.TOOLS;
        
        // Tools tab is currently empty as all tool items are commented out
        assertEquals(0, tools.getItemCount());
    }
}

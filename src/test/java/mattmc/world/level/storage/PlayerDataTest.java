package mattmc.world.level.storage;

import mattmc.world.item.Inventory;
import mattmc.world.item.ItemStack;
import mattmc.world.item.Items;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for PlayerData save/load functionality.
 */
public class PlayerDataTest {
    
    @Test
    public void testInventoryToNBTAndBack() {
        // Create inventory with some items
        Inventory inventory = new Inventory();
        inventory.setStack(0, new ItemStack(Items.DIAMOND, 10));
        inventory.setStack(1, new ItemStack(Items.STONE, 64));
        inventory.setStack(9, new ItemStack(Items.IRON_PICKAXE, 1));
        inventory.setSelectedSlot(2);
        
        // Convert to NBT
        Map<String, Object> nbt = PlayerData.inventoryToNBT(inventory);
        
        // Load into new inventory
        Inventory loaded = new Inventory();
        PlayerData.inventoryFromNBT(loaded, nbt);
        
        // Verify items
        assertNotNull(loaded.getStack(0));
        assertEquals(Items.DIAMOND, loaded.getStack(0).getItem());
        assertEquals(10, loaded.getStack(0).getCount());
        
        assertNotNull(loaded.getStack(1));
        assertEquals(Items.STONE, loaded.getStack(1).getItem());
        assertEquals(64, loaded.getStack(1).getCount());
        
        assertNotNull(loaded.getStack(9));
        assertEquals(Items.IRON_PICKAXE, loaded.getStack(9).getItem());
        assertEquals(1, loaded.getStack(9).getCount());
        
        // Verify selected slot
        assertEquals(2, loaded.getSelectedSlot());
        
        // Verify empty slots remain empty
        assertNull(loaded.getStack(2));
        assertNull(loaded.getStack(35));
    }
    
    @Test
    public void testEmptyInventory() {
        Inventory inventory = new Inventory();
        
        Map<String, Object> nbt = PlayerData.inventoryToNBT(inventory);
        
        Inventory loaded = new Inventory();
        PlayerData.inventoryFromNBT(loaded, nbt);
        
        // All slots should be empty
        for (int i = 0; i < inventory.getSize(); i++) {
            assertNull(loaded.getStack(i));
        }
    }
    
    @Test
    public void testSaveAndLoadPlayerData(@TempDir Path tempDir) throws IOException {
        Path playerDatFile = tempDir.resolve("player.dat");
        
        // Create inventory with items
        Inventory inventory = new Inventory();
        inventory.setStack(0, new ItemStack(Items.DIAMOND, 5));
        inventory.setStack(8, new ItemStack(Items.GOLD_INGOT, 32));
        inventory.setSelectedSlot(3);
        
        // Save to file
        PlayerData.save(playerDatFile, inventory);
        
        assertTrue(Files.exists(playerDatFile));
        
        // Load from file
        Inventory loaded = new Inventory();
        PlayerData.load(playerDatFile, loaded);
        
        // Verify items
        assertNotNull(loaded.getStack(0));
        assertEquals(Items.DIAMOND, loaded.getStack(0).getItem());
        assertEquals(5, loaded.getStack(0).getCount());
        
        assertNotNull(loaded.getStack(8));
        assertEquals(Items.GOLD_INGOT, loaded.getStack(8).getItem());
        assertEquals(32, loaded.getStack(8).getCount());
        
        assertEquals(3, loaded.getSelectedSlot());
    }
    
    @Test
    public void testSaveAndLoadFullInventory(@TempDir Path tempDir) throws IOException {
        Path playerDatFile = tempDir.resolve("player.dat");
        
        // Fill inventory completely
        Inventory inventory = new Inventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setStack(i, new ItemStack(Items.DIRT, (i % 64) + 1));
        }
        inventory.setSelectedSlot(5);
        
        // Save and load
        PlayerData.save(playerDatFile, inventory);
        Inventory loaded = new Inventory();
        PlayerData.load(playerDatFile, loaded);
        
        // Verify all items
        for (int i = 0; i < inventory.getSize(); i++) {
            assertNotNull(loaded.getStack(i), "Slot " + i + " should not be null");
            assertEquals(Items.DIRT, loaded.getStack(i).getItem());
            assertEquals((i % 64) + 1, loaded.getStack(i).getCount());
        }
        
        assertEquals(5, loaded.getSelectedSlot());
    }
    
    @Test
    public void testPlayerDataDirectoryCreation(@TempDir Path tempDir) throws IOException {
        Path playerdataDir = tempDir.resolve("playerdata");
        Path playerDatFile = playerdataDir.resolve("player.dat");
        
        assertFalse(Files.exists(playerdataDir));
        
        Inventory inventory = new Inventory();
        inventory.setStack(0, new ItemStack(Items.DIAMOND, 1));
        
        // Save should create the directory
        PlayerData.save(playerDatFile, inventory);
        
        assertTrue(Files.exists(playerdataDir));
        assertTrue(Files.isDirectory(playerdataDir));
        assertTrue(Files.exists(playerDatFile));
    }
}

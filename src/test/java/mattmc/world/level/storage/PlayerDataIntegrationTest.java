package mattmc.world.level.storage;

import mattmc.world.item.Inventory;
import mattmc.world.item.ItemStack;
import mattmc.world.item.Items;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for player data save/load with world directory structure.
 */
public class PlayerDataIntegrationTest {
    
    @Test
    public void testPlayerDataInWorldDirectory(@TempDir Path tempDir) throws IOException {
        // Simulate world directory structure
        Path worldDir = tempDir.resolve("TestWorld");
        Path playerdataDir = worldDir.resolve("playerdata");
        Files.createDirectories(playerdataDir);
        Path playerDatFile = playerdataDir.resolve("player.dat");
        
        // Create and save inventory
        Inventory inventory = new Inventory();
        inventory.setStack(0, new ItemStack(Items.DIAMOND, 15));
        inventory.setStack(1, new ItemStack(Items.IRON_PICKAXE, 1));
        inventory.setStack(9, new ItemStack(Items.STONE, 64));
        inventory.setSelectedSlot(1);
        
        PlayerData.save(playerDatFile, inventory);
        
        // Verify file structure
        assertTrue(Files.exists(playerdataDir), "playerdata directory should exist");
        assertTrue(Files.isDirectory(playerdataDir), "playerdata should be a directory");
        assertTrue(Files.exists(playerDatFile), "player.dat file should exist");
        
        // Load and verify
        Inventory loaded = new Inventory();
        PlayerData.load(playerDatFile, loaded);
        
        assertNotNull(loaded.getStack(0));
        assertEquals(Items.DIAMOND, loaded.getStack(0).getItem());
        assertEquals(15, loaded.getStack(0).getCount());
        
        assertNotNull(loaded.getStack(1));
        assertEquals(Items.IRON_PICKAXE, loaded.getStack(1).getItem());
        assertEquals(1, loaded.getStack(1).getCount());
        
        assertNotNull(loaded.getStack(9));
        assertEquals(Items.STONE, loaded.getStack(9).getItem());
        assertEquals(64, loaded.getStack(9).getCount());
        
        assertEquals(1, loaded.getSelectedSlot());
    }
    
    @Test
    public void testPlayerDataDirectoryCreation(@TempDir Path tempDir) throws IOException {
        Path worldDir = tempDir.resolve("NewWorld");
        Path playerdataDir = worldDir.resolve("playerdata");
        Path playerDatFile = playerdataDir.resolve("player.dat");
        
        // Directory shouldn't exist yet
        assertFalse(Files.exists(playerdataDir));
        
        // Save should create directory
        Inventory inventory = new Inventory();
        inventory.setStack(0, new ItemStack(Items.DIAMOND, 1));
        
        PlayerData.save(playerDatFile, inventory);
        
        assertTrue(Files.exists(playerdataDir));
        assertTrue(Files.isDirectory(playerdataDir));
        assertTrue(Files.exists(playerDatFile));
    }
    
    @Test
    public void testPlayerDataPersistence(@TempDir Path tempDir) throws IOException {
        Path worldDir = tempDir.resolve("PersistenceTest");
        Path playerDatFile = worldDir.resolve("playerdata").resolve("player.dat");
        
        // First save
        Inventory inv1 = new Inventory();
        inv1.setStack(0, new ItemStack(Items.DIAMOND, 5));
        PlayerData.save(playerDatFile, inv1);
        
        // Load and modify
        Inventory inv2 = new Inventory();
        PlayerData.load(playerDatFile, inv2);
        inv2.setStack(1, new ItemStack(Items.GOLD_INGOT, 10));
        
        // Save again
        PlayerData.save(playerDatFile, inv2);
        
        // Load and verify both items
        Inventory inv3 = new Inventory();
        PlayerData.load(playerDatFile, inv3);
        
        assertNotNull(inv3.getStack(0));
        assertEquals(Items.DIAMOND, inv3.getStack(0).getItem());
        assertEquals(5, inv3.getStack(0).getCount());
        
        assertNotNull(inv3.getStack(1));
        assertEquals(Items.GOLD_INGOT, inv3.getStack(1).getItem());
        assertEquals(10, inv3.getStack(1).getCount());
    }
    
    @Test
    public void testEmptyInventorySaveLoad(@TempDir Path tempDir) throws IOException {
        Path playerDatFile = tempDir.resolve("empty_player.dat");
        
        // Save empty inventory
        Inventory empty = new Inventory();
        PlayerData.save(playerDatFile, empty);
        
        // Load and verify it's empty
        Inventory loaded = new Inventory();
        PlayerData.load(playerDatFile, loaded);
        
        for (int i = 0; i < loaded.getSize(); i++) {
            assertNull(loaded.getStack(i), "Slot " + i + " should be empty");
        }
    }
}


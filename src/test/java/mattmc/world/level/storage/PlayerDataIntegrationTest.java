package mattmc.world.level.storage;

import mattmc.world.Gamemode;
import mattmc.world.item.Inventory;
import mattmc.world.item.ItemStack;
import mattmc.registries.Items;
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
        
        // Create and save inventory with gamemode
        Inventory inventory = new Inventory();
        inventory.setStack(0, new ItemStack(Items.STONE, 15));
        inventory.setStack(1, new ItemStack(Items.BIRCH_PLANKS, 1));
        inventory.setStack(9, new ItemStack(Items.STONE, 64));
        inventory.setSelectedSlot(1);
        
        PlayerData.save(playerDatFile, inventory, Gamemode.SURVIVAL);
        
        // Verify file structure
        assertTrue(Files.exists(playerdataDir), "playerdata directory should exist");
        assertTrue(Files.isDirectory(playerdataDir), "playerdata should be a directory");
        assertTrue(Files.exists(playerDatFile), "player.dat file should exist");
        
        // Load and verify
        Inventory loaded = new Inventory();
        Gamemode loadedGamemode = PlayerData.load(playerDatFile, loaded);
        
        assertNotNull(loaded.getStack(0));
        assertEquals(Items.STONE, loaded.getStack(0).getItem());
        assertEquals(15, loaded.getStack(0).getCount());
        
        assertNotNull(loaded.getStack(1));
        assertEquals(Items.BIRCH_PLANKS, loaded.getStack(1).getItem());
        assertEquals(1, loaded.getStack(1).getCount());
        
        assertNotNull(loaded.getStack(9));
        assertEquals(Items.STONE, loaded.getStack(9).getItem());
        assertEquals(64, loaded.getStack(9).getCount());
        
        assertEquals(1, loaded.getSelectedSlot());
        assertEquals(Gamemode.SURVIVAL, loadedGamemode);
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
        inventory.setStack(0, new ItemStack(Items.STONE, 1));
        
        PlayerData.save(playerDatFile, inventory, Gamemode.CREATIVE);
        
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
        inv1.setStack(0, new ItemStack(Items.STONE, 5));
        PlayerData.save(playerDatFile, inv1, Gamemode.CREATIVE);
        
        // Load and modify
        Inventory inv2 = new Inventory();
        Gamemode gamemode = PlayerData.load(playerDatFile, inv2);
        assertEquals(Gamemode.CREATIVE, gamemode);
        inv2.setStack(1, new ItemStack(Items.DARK_OAK_PLANKS, 10));
        
        // Save again with different gamemode
        PlayerData.save(playerDatFile, inv2, Gamemode.SURVIVAL);
        
        // Load and verify both items and gamemode
        Inventory inv3 = new Inventory();
        Gamemode loadedGamemode = PlayerData.load(playerDatFile, inv3);
        
        assertNotNull(inv3.getStack(0));
        assertEquals(Items.STONE, inv3.getStack(0).getItem());
        assertEquals(5, inv3.getStack(0).getCount());
        
        assertNotNull(inv3.getStack(1));
        assertEquals(Items.DARK_OAK_PLANKS, inv3.getStack(1).getItem());
        assertEquals(10, inv3.getStack(1).getCount());
        
        assertEquals(Gamemode.SURVIVAL, loadedGamemode);
    }
    
    @Test
    public void testEmptyInventorySaveLoad(@TempDir Path tempDir) throws IOException {
        Path playerDatFile = tempDir.resolve("empty_player.dat");
        
        // Save empty inventory
        Inventory empty = new Inventory();
        PlayerData.save(playerDatFile, empty, Gamemode.CREATIVE);
        
        // Load and verify it's empty
        Inventory loaded = new Inventory();
        Gamemode loadedGamemode = PlayerData.load(playerDatFile, loaded);
        
        for (int i = 0; i < loaded.getSize(); i++) {
            assertNull(loaded.getStack(i), "Slot " + i + " should be empty");
        }
        
        assertEquals(Gamemode.CREATIVE, loadedGamemode);
    }
}


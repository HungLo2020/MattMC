package mattmc.world.level.storage;

import mattmc.nbt.NBTUtil;
import mattmc.world.Gamemode;
import mattmc.world.item.Inventory;
import mattmc.world.item.Item;
import mattmc.world.item.ItemStack;
import mattmc.world.item.Items;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Represents player data stored in player.dat file.
 * Stores inventory, gamemode, and other player-specific information.
 */
public class PlayerData {
    
    public PlayerData() {
    }
    
    /**
     * Convert inventory and gamemode to NBT format (Map-based).
     */
    public static Map<String, Object> toNBT(Inventory inventory, Gamemode gamemode) {
        // Pre-allocate with exact size (Inventory, SelectedItemSlot, Gamemode)
        Map<String, Object> data = new HashMap<>(3);
        
        // Pre-allocate with exact capacity (inventory size) for worst-case scenario
        List<Map<String, Object>> items = new ArrayList<>(inventory.getSize());
        
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack != null) {
                // Pre-allocate with 3 fields: Slot, id, Count
                Map<String, Object> itemData = new HashMap<>(3);
                
                // Store slot index
                itemData.put("Slot", (byte) i);
                
                // Store item identifier
                String identifier = stack.getItem().getIdentifier();
                if (identifier != null) {
                    itemData.put("id", identifier);
                }
                
                // Store count
                itemData.put("Count", (byte) stack.getCount());
                
                items.add(itemData);
            }
        }
        
        data.put("Inventory", items);
        data.put("SelectedItemSlot", inventory.getSelectedSlot());
        data.put("Gamemode", gamemode.getId());
        
        return data;
    }
    
    /**
     * Convert inventory to NBT format (Map-based).
     * @deprecated Use {@link #toNBT(Inventory, Gamemode)} instead
     */
    @Deprecated
    public static Map<String, Object> inventoryToNBT(Inventory inventory) {
        return toNBT(inventory, Gamemode.CREATIVE);
    }
    
    /**
     * Load inventory from NBT format (Map-based).
     * @deprecated Use {@link #fromNBT(Inventory, Map)} instead
     */
    @Deprecated
    public static void inventoryFromNBT(Inventory inventory, Map<String, Object> data) {
        fromNBT(inventory, data);
    }
    
    /**
     * Load inventory and gamemode from NBT format (Map-based).
     * Returns the loaded gamemode, defaults to CREATIVE for legacy support.
     */
    public static Gamemode fromNBT(Inventory inventory, Map<String, Object> data) {
        // Clear existing inventory
        inventory.clear();
        
        // Load inventory items
        Object inventoryObj = data.get("Inventory");
        if (inventoryObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) inventoryObj;
            
            for (Map<String, Object> itemData : items) {
                try {
                    // Get slot
                    int slot = 0;
                    if (itemData.get("Slot") instanceof Byte) {
                        slot = ((Byte) itemData.get("Slot")).intValue();
                    } else if (itemData.get("Slot") instanceof Integer) {
                        slot = (Integer) itemData.get("Slot");
                    }
                    
                    // Get item by identifier
                    String identifier = null;
                    if (itemData.get("id") instanceof String) {
                        identifier = (String) itemData.get("id");
                    }
                    
                    if (identifier == null) {
                        continue;
                    }
                    
                    Item item = Items.getItem(identifier);
                    if (item == null) {
                        continue;
                    }
                    
                    // Get count
                    int count = 1;
                    if (itemData.get("Count") instanceof Byte) {
                        count = ((Byte) itemData.get("Count")).intValue();
                    } else if (itemData.get("Count") instanceof Integer) {
                        count = (Integer) itemData.get("Count");
                    }
                    
                    // Create item stack and add to inventory
                    if (count > 0 && count <= item.getMaxStackSize()) {
                        ItemStack stack = new ItemStack(item, count);
                        inventory.setStack(slot, stack);
                    }
                } catch (ClassCastException | IllegalArgumentException e) {
                    // Skip invalid items - can happen if item IDs changed or data is corrupted
                }
            }
        }
        
        // Load selected slot
        if (data.get("SelectedItemSlot") instanceof Integer) {
            inventory.setSelectedSlot((Integer) data.get("SelectedItemSlot"));
        } else if (data.get("SelectedItemSlot") instanceof Byte) {
            inventory.setSelectedSlot(((Byte) data.get("SelectedItemSlot")).intValue());
        }
        
        // Load gamemode with legacy support (defaults to CREATIVE if not present)
        if (data.get("Gamemode") instanceof Integer) {
            return Gamemode.fromId((Integer) data.get("Gamemode"));
        }
        // Legacy world without Gamemode field - default to CREATIVE
        return Gamemode.CREATIVE;
    }
    
    /**
     * Save player data to a file.
     * @deprecated Use {@link #save(Path, Inventory, Gamemode)} instead
     */
    @Deprecated
    public static void save(Path filePath, Inventory inventory) throws IOException {
        save(filePath, inventory, Gamemode.CREATIVE);
    }
    
    /**
     * Save player data (inventory and gamemode) to a file.
     */
    public static void save(Path filePath, Inventory inventory, Gamemode gamemode) throws IOException {
        Files.createDirectories(filePath.getParent());
        
        Map<String, Object> data = toNBT(inventory, gamemode);
        
        try (OutputStream out = Files.newOutputStream(filePath)) {
            NBTUtil.writeCompressed(data, out);
        }
    }
    
    /**
     * Load player data from a file.
     * @deprecated Use {@link #load(Path, Inventory)} which returns the Gamemode instead
     */
    @Deprecated
    public static void loadLegacy(Path filePath, Inventory inventory) throws IOException {
        try (InputStream in = Files.newInputStream(filePath)) {
            Map<String, Object> data = NBTUtil.readCompressed(in);
            fromNBT(inventory, data);
        }
    }
    
    /**
     * Load player data from a file.
     * Returns the loaded gamemode (or CREATIVE for legacy worlds).
     */
    public static Gamemode load(Path filePath, Inventory inventory) throws IOException {
        try (InputStream in = Files.newInputStream(filePath)) {
            Map<String, Object> data = NBTUtil.readCompressed(in);
            return fromNBT(inventory, data);
        }
    }
}

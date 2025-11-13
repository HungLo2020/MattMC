package mattmc.world.level.storage;

import mattmc.nbt.NBTUtil;
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
 * Stores inventory and other player-specific information.
 */
public class PlayerData {
    
    public PlayerData() {
    }
    
    /**
     * Convert inventory to NBT format (Map-based).
     */
    public static Map<String, Object> inventoryToNBT(Inventory inventory) {
        Map<String, Object> data = new HashMap<>();
        
        // Pre-allocate with exact capacity (inventory size) for worst-case scenario
        List<Map<String, Object>> items = new ArrayList<>(inventory.getSize());
        
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack != null) {
                Map<String, Object> itemData = new HashMap<>();
                
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
        
        return data;
    }
    
    /**
     * Load inventory from NBT format (Map-based).
     */
    public static void inventoryFromNBT(Inventory inventory, Map<String, Object> data) {
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
                } catch (Exception e) {
                    // Skip invalid items
                }
            }
        }
        
        // Load selected slot
        if (data.get("SelectedItemSlot") instanceof Integer) {
            inventory.setSelectedSlot((Integer) data.get("SelectedItemSlot"));
        } else if (data.get("SelectedItemSlot") instanceof Byte) {
            inventory.setSelectedSlot(((Byte) data.get("SelectedItemSlot")).intValue());
        }
    }
    
    /**
     * Save player data to a file.
     */
    public static void save(Path filePath, Inventory inventory) throws IOException {
        Files.createDirectories(filePath.getParent());
        
        Map<String, Object> data = inventoryToNBT(inventory);
        
        try (OutputStream out = Files.newOutputStream(filePath)) {
            NBTUtil.writeCompressed(data, out);
        }
    }
    
    /**
     * Load player data from a file.
     */
    public static void load(Path filePath, Inventory inventory) throws IOException {
        try (InputStream in = Files.newInputStream(filePath)) {
            Map<String, Object> data = NBTUtil.readCompressed(in);
            inventoryFromNBT(inventory, data);
        }
    }
}

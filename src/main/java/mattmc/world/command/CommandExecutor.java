package mattmc.world.command;

import mattmc.world.entity.player.LocalPlayer;
import mattmc.world.item.Inventory;
import mattmc.world.item.Item;
import mattmc.world.item.Items;
import mattmc.world.item.ItemStack;
import mattmc.world.level.Level;
import mattmc.world.level.block.Block;
import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import mattmc.world.region.RegionSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes in-game commands for the player.
 * Handles command parsing and execution, returning results with feedback messages.
 */
public class CommandExecutor {
    private static final Logger logger = LoggerFactory.getLogger(CommandExecutor.class);
    private static final long MAX_REGION_SIZE = 100_000;
    
    private final Level world;
    private final LocalPlayer player;
    private final RegionSelector regionSelector;
    
    public CommandExecutor(Level world, LocalPlayer player, RegionSelector regionSelector) {
        this.world = world;
        this.player = player;
        this.regionSelector = regionSelector;
    }
    
    /**
     * Execute a command string and return the result.
     */
    public CommandResult executeCommand(String commandString) {
        String cmd = commandString.trim();
        
        // Empty command
        if (cmd.isEmpty() || cmd.equals("/")) {
            return new CommandResult(true, "", 0);
        }
        
        // Ensure command starts with /
        if (!cmd.startsWith("/")) {
            return new CommandResult(false, "Commands must start with /", 3.0);
        }
        
        // Parse and execute command
        if (cmd.startsWith("/tp ")) {
            return executeTeleport(cmd);
        } else if (cmd.equals("/pos1")) {
            return executePos1();
        } else if (cmd.equals("/pos2")) {
            return executePos2();
        } else if (cmd.startsWith("/set ")) {
            return executeSet(cmd);
        } else if (cmd.startsWith("/give ")) {
            return executeGive(cmd);
        } else {
            return new CommandResult(false, "Unknown command: " + cmd, 3.0);
        }
    }
    
    /**
     * Execute /tp command - teleport player to coordinates.
     */
    private CommandResult executeTeleport(String cmd) {
        try {
            // Parse: /tp x y z
            String[] parts = cmd.substring(4).trim().split("\\s+");
            
            if (parts.length != 3) {
                return new CommandResult(false, "Usage: /tp x y z", 3.0);
            }
            
            float x = Float.parseFloat(parts[0]);
            float y = Float.parseFloat(parts[1]);
            float z = Float.parseFloat(parts[2]);
            
            // Teleport the player
            player.setX(x);
            player.setY(y);
            player.setZ(z);
            
            logger.info("Teleported to: {}, {}, {}", x, y, z);
            return new CommandResult(true, "", 0);
            
        } catch (NumberFormatException e) {
            return new CommandResult(false, "Invalid coordinates. Usage: /tp x y z", 3.0);
        }
    }
    
    /**
     * Execute /pos1 command - set first position for region selection.
     */
    private CommandResult executePos1() {
        // Get player's current block position (floor of the feet position)
        int x = (int) Math.floor(player.getX());
        int y = (int) Math.floor(player.getY());
        int z = (int) Math.floor(player.getZ());
        
        regionSelector.setPos1(x, y, z);
        logger.info("Position 1 set to: {}, {}, {}", x, y, z);
        
        String message = "Position 1 set to: " + x + ", " + y + ", " + z;
        return new CommandResult(true, message, 3.0);
    }
    
    /**
     * Execute /pos2 command - set second position for region selection.
     */
    private CommandResult executePos2() {
        // Get player's current block position (floor of the feet position)
        int x = (int) Math.floor(player.getX());
        int y = (int) Math.floor(player.getY());
        int z = (int) Math.floor(player.getZ());
        
        regionSelector.setPos2(x, y, z);
        logger.info("Position 2 set to: {}, {}, {}", x, y, z);
        
        String message = "Position 2 set to: " + x + ", " + y + ", " + z;
        return new CommandResult(true, message, 3.0);
    }
    
    /**
     * Execute /set command - fill region with specified block.
     */
    private CommandResult executeSet(String cmd) {
        // Check if both positions are set
        if (!regionSelector.hasSelection()) {
            return new CommandResult(false, "Please set both positions first with /pos1 and /pos2", 3.0);
        }
        
        // Parse the block name from the command
        String blockName = cmd.substring(5).trim(); // Remove "/set "
        
        if (blockName.isEmpty()) {
            return new CommandResult(false, "Usage: /set <block>", 3.0);
        }
        
        // Look up the block - try with namespace first, then without
        Block block = null;
        if (blockName.contains(":")) {
            block = Blocks.getBlock(blockName);
        } else {
            block = Blocks.getBlock("mattmc:" + blockName);
        }
        
        if (block == null) {
            return new CommandResult(false, "Unknown block: " + blockName, 3.0);
        }
        
        // Get region bounds
        RegionSelector.RegionBounds bounds = regionSelector.getRegionBounds();
        
        // Check region size
        long totalBlocks = regionSelector.getRegionSize();
        if (totalBlocks > MAX_REGION_SIZE) {
            String message = "Region too large (" + totalBlocks + " blocks). Maximum is " + MAX_REGION_SIZE;
            return new CommandResult(false, message, 3.0);
        }
        
        // Fill the region with the specified block
        int blocksSet = 0;
        for (int x = bounds.minX; x <= bounds.maxX; x++) {
            for (int worldY = bounds.minY; worldY <= bounds.maxY; worldY++) {
                // Convert world Y to chunk Y for setBlock call
                int chunkY = LevelChunk.worldYToChunkY(worldY);
                for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
                    world.setBlock(x, chunkY, z, block);
                    blocksSet++;
                }
            }
        }
        
        logger.info("Filled region ({}, {}, {}) to ({}, {}, {}) with {} - {} blocks set",
                    bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ, 
                    block.getIdentifier(), blocksSet);
        
        String message = "Filled " + blocksSet + " blocks with " + blockName;
        return new CommandResult(true, message, 3.0);
    }
    
    /**
     * Execute /give command - give items to player.
     */
    private CommandResult executeGive(String cmd) {
        // Parse the command: /give <item> [count]
        String[] parts = cmd.substring(6).trim().split("\\s+");
        
        if (parts.length < 1 || parts[0].isEmpty()) {
            return new CommandResult(false, "Usage: /give <item> [count]", 3.0);
        }
        
        String itemName = parts[0];
        int count = 1; // Default to 1 if not specified
        
        // Parse count if provided
        if (parts.length >= 2) {
            try {
                count = Integer.parseInt(parts[1]);
                if (count <= 0) {
                    return new CommandResult(false, "Count must be positive", 3.0);
                }
            } catch (NumberFormatException e) {
                return new CommandResult(false, "Invalid count: " + parts[1], 3.0);
            }
        }
        
        // Look up the item - try with namespace first, then without
        Item item = null;
        if (itemName.contains(":")) {
            item = Items.getItem(itemName);
        } else {
            item = Items.getItem("mattmc:" + itemName);
        }
        
        if (item == null) {
            return new CommandResult(false, "Unknown item: " + itemName, 3.0);
        }
        
        // Add items to inventory, respecting max stack size
        Inventory inventory = player.getInventory();
        int itemsGiven = 0;
        int remainingCount = count;
        
        while (remainingCount > 0) {
            int stackSize = Math.min(remainingCount, item.getMaxStackSize());
            ItemStack stack = new ItemStack(item, stackSize);
            
            if (inventory.addItem(stack)) {
                itemsGiven += stackSize;
                remainingCount -= stackSize;
            } else {
                // Inventory is full
                break;
            }
        }
        
        logger.info("Gave {} {} to player", itemsGiven, itemName);
        
        String message;
        if (itemsGiven == count) {
            message = "Gave " + itemsGiven + " " + itemName;
        } else {
            message = "Gave " + itemsGiven + " " + itemName + " (inventory full, " + (count - itemsGiven) + " remaining)";
        }
        
        return new CommandResult(true, message, 3.0);
    }
    
    /**
     * Result of command execution.
     */
    public static class CommandResult {
        public final boolean success;
        public final String message;
        public final double displayTime;
        
        public CommandResult(boolean success, String message, double displayTime) {
            this.success = success;
            this.message = message;
            this.displayTime = displayTime;
        }
    }
}

package mattmc.client.gui.screens;

import mattmc.world.entity.player.LocalPlayer;
import mattmc.world.item.Inventory;
import mattmc.world.item.Item;
import mattmc.world.item.Items;
import mattmc.world.item.ItemStack;
import mattmc.world.level.block.Block;
import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.ChunkUtils;
import mattmc.world.level.chunk.LevelChunk;
import mattmc.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles command parsing and execution for the game.
 * Supports commands: /tp, /pos1, /pos2, /set, /give
 */
public class CommandSystem {
    private static final Logger logger = LoggerFactory.getLogger(CommandSystem.class);
    private static final long MAX_REGION_SIZE = 100_000;
    
    private final LocalPlayer player;
    private final Level world;
    
    // Region selection state for /pos1, /pos2, and /set commands
    private int[] regionPos1 = null; // [x, y, z]
    private int[] regionPos2 = null; // [x, y, z]
    
    public CommandSystem(LocalPlayer player, Level world) {
        this.player = player;
        this.world = world;
    }
    
    /**
     * Execute a command and return a feedback message.
     * @param commandText The command text (must start with /)
     * @return Feedback message to display, or null if no feedback
     */
    public String executeCommand(String commandText) {
        String cmd = commandText.trim();
        
        // Empty command, just close
        if (cmd.isEmpty() || cmd.equals("/")) {
            return null;
        }
        
        // Ensure command starts with /
        if (!cmd.startsWith("/")) {
            return "Commands must start with /";
        }
        
        // Parse and execute command
        if (cmd.startsWith("/tp ")) {
            return executeTeleportCommand(cmd);
        } else if (cmd.equals("/pos1")) {
            return executePos1Command();
        } else if (cmd.equals("/pos2")) {
            return executePos2Command();
        } else if (cmd.startsWith("/set ")) {
            return executeSetCommand(cmd);
        } else if (cmd.startsWith("/give ")) {
            return executeGiveCommand(cmd);
        } else if (cmd.startsWith("/time ")) {
            return executeTimeCommand(cmd);
        } else {
            return "Unknown command: " + cmd;
        }
    }
    
    private String executeTeleportCommand(String cmd) {
        try {
            // Parse: /tp x y z
            String[] parts = cmd.substring(4).trim().split("\\s+");
            
            if (parts.length != 3) {
                return "Usage: /tp x y z";
            }
            
            float x = Float.parseFloat(parts[0]);
            float y = Float.parseFloat(parts[1]);
            float z = Float.parseFloat(parts[2]);
            
            // Teleport the player
            player.setX(x);
            player.setY(y);
            player.setZ(z);
            
            logger.info("Teleported to: {}, {}, {}", x, y, z);
            return null; // No feedback message needed for successful teleport
            
        } catch (NumberFormatException e) {
            return "Invalid coordinates. Usage: /tp x y z";
        }
    }
    
    /**
     * Execute /pos1 command - sets the first position of the region to the player's current position.
     */
    private String executePos1Command() {
        // Get player's current block position (floor of the feet position)
        int x = (int) Math.floor(player.getX());
        int y = (int) Math.floor(player.getY());
        int z = (int) Math.floor(player.getZ());
        
        regionPos1 = new int[]{x, y, z};
        logger.info("Position 1 set to: {}, {}, {}", x, y, z);
        
        return "Position 1 set to: " + x + ", " + y + ", " + z;
    }
    
    /**
     * Execute /pos2 command - sets the second position of the region to the player's current position.
     */
    private String executePos2Command() {
        // Get player's current block position (floor of the feet position)
        int x = (int) Math.floor(player.getX());
        int y = (int) Math.floor(player.getY());
        int z = (int) Math.floor(player.getZ());
        
        regionPos2 = new int[]{x, y, z};
        logger.info("Position 2 set to: {}, {}, {}", x, y, z);
        
        return "Position 2 set to: " + x + ", " + y + ", " + z;
    }
    
    /**
     * Execute /set command - fills the region defined by pos1 and pos2 with the specified block.
     * @param cmd The full command string (e.g., "/set stone" or "/set mattmc:stone")
     */
    private String executeSetCommand(String cmd) {
        // Check if both positions are set
        if (regionPos1 == null || regionPos2 == null) {
            return "Please set both positions first with /pos1 and /pos2";
        }
        
        // Parse the block name from the command
        String blockName = cmd.substring(5).trim(); // Remove "/set "
        
        if (blockName.isEmpty()) {
            return "Usage: /set <block>";
        }
        
        // Look up the block - try with namespace first, then without
        Block block = null;
        if (blockName.contains(":")) {
            // Already has namespace (e.g., "mattmc:stone")
            block = Blocks.getBlock(blockName);
        } else {
            // Try adding default namespace (e.g., "stone" -> "mattmc:stone")
            block = Blocks.getBlock("mattmc:" + blockName);
        }
        
        if (block == null) {
            return "Unknown block: " + blockName;
        }
        
        // Calculate the bounds of the region
        int minX = Math.min(regionPos1[0], regionPos2[0]);
        int maxX = Math.max(regionPos1[0], regionPos2[0]);
        int minY = Math.min(regionPos1[1], regionPos2[1]);
        int maxY = Math.max(regionPos1[1], regionPos2[1]);
        int minZ = Math.min(regionPos1[2], regionPos2[2]);
        int maxZ = Math.max(regionPos1[2], regionPos2[2]);
        
        // Calculate region size and check for maximum limit to prevent UI freezing
        long sizeX = (long)(maxX - minX + 1);
        long sizeY = (long)(maxY - minY + 1);
        long sizeZ = (long)(maxZ - minZ + 1);
        long totalBlocks = sizeX * sizeY * sizeZ;
        
        if (totalBlocks > MAX_REGION_SIZE) {
            return "Region too large (" + totalBlocks + " blocks). Maximum is " + MAX_REGION_SIZE;
        }
        
        // Calculate the number of blocks to set
        int blocksSet = 0;
        
        // Fill the region with the specified block
        // Note: Level.setBlock expects chunk-local Y coordinates (0-383)
        for (int x = minX; x <= maxX; x++) {
            for (int worldY = minY; worldY <= maxY; worldY++) {
                // Convert world Y to chunk Y for setBlock call
                int chunkY = ChunkUtils.worldToLocalY(worldY);
                for (int z = minZ; z <= maxZ; z++) {
                    world.setBlock(x, chunkY, z, block);
                    blocksSet++;
                }
            }
        }
        
        logger.info("Filled region ({}, {}, {}) to ({}, {}, {}) with {} - {} blocks set",
                    minX, minY, minZ, maxX, maxY, maxZ, block.getIdentifier(), blocksSet);
        
        return "Filled " + blocksSet + " blocks with " + blockName;
    }
    
    /**
     * Execute /give command - gives the player items and adds them to their inventory.
     * @param cmd The full command string (e.g., "/give stone 64" or "/give mattmc:dirt 32" or "/give stone")
     */
    private String executeGiveCommand(String cmd) {
        // Parse the command: /give <item> [count]
        String[] parts = cmd.substring(6).trim().split("\\s+");
        
        if (parts.length < 1 || parts[0].isEmpty()) {
            return "Usage: /give <item> [count]";
        }
        
        String itemName = parts[0];
        int count = 1; // Default to 1 if not specified
        
        // Parse count if provided
        if (parts.length >= 2) {
            try {
                count = Integer.parseInt(parts[1]);
                if (count <= 0) {
                    return "Count must be positive";
                }
            } catch (NumberFormatException e) {
                return "Invalid count: " + parts[1];
            }
        }
        
        // Look up the item - try with namespace first, then without
        Item item = null;
        if (itemName.contains(":")) {
            // Already has namespace (e.g., "mattmc:stone")
            item = Items.getItem(itemName);
        } else {
            // Try adding default namespace (e.g., "stone" -> "mattmc:stone")
            item = Items.getItem("mattmc:" + itemName);
        }
        
        if (item == null) {
            return "Unknown item: " + itemName;
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
        
        if (itemsGiven > 0) {
            logger.info("Gave player {} x{}", itemName, itemsGiven);
            if (itemsGiven < count) {
                return "Gave " + itemsGiven + " " + itemName + " (inventory full)";
            } else {
                return "Gave " + itemsGiven + " " + itemName;
            }
        } else {
            return "Inventory is full";
        }
    }
    
    /**
     * Execute /time command - sets or queries the world time.
     * Usage: /time set <time> or /time query
     * Shortcuts: /time set day (1000), /time set noon (6000), /time set night (13000), /time set midnight (18000)
     */
    private String executeTimeCommand(String cmd) {
        try {
            String[] parts = cmd.substring(6).trim().split("\\s+");
            
            if (parts.length == 0 || parts[0].isEmpty()) {
                return "Usage: /time set <time|day|noon|night|midnight> or /time query";
            }
            
            String subCommand = parts[0];
            
            if (subCommand.equals("query")) {
                long timeOfDay = world.getDayCycle().getTimeOfDay();
                long worldTime = world.getDayCycle().getWorldTime();
                return "Time of day: " + timeOfDay + " (World time: " + worldTime + ")";
            } else if (subCommand.equals("set")) {
                if (parts.length < 2) {
                    return "Usage: /time set <time|day|noon|night|midnight>";
                }
                
                String timeArg = parts[1];
                long newTime;
                
                // Parse named times
                switch (timeArg.toLowerCase()) {
                    case "day":
                        newTime = 1000L; // Shortly after sunrise
                        break;
                    case "noon":
                        newTime = 6000L; // Midday
                        break;
                    case "night":
                        newTime = 13000L; // Shortly after sunset
                        break;
                    case "midnight":
                        newTime = 18000L; // Middle of the night
                        break;
                    default:
                        // Try to parse as a number
                        try {
                            newTime = Long.parseLong(timeArg);
                        } catch (NumberFormatException e) {
                            return "Invalid time value. Use a number or: day, noon, night, midnight";
                        }
                }
                
                world.getDayCycle().setWorldTime(newTime);
                logger.info("Set world time to: {}", newTime);
                return "Time set to " + newTime;
            } else {
                return "Unknown time subcommand. Usage: /time set <time> or /time query";
            }
        } catch (NumberFormatException e) {
            return "Error executing time command: " + e.getMessage();
        }
    }
}

package MattMC.command;

import MattMC.player.Player;

/**
 * Handles command parsing and execution.
 * Similar to Minecraft's command system.
 */
public class CommandHandler {
    private final Player player;
    
    public CommandHandler(Player player) {
        this.player = player;
    }
    
    /**
     * Execute a command string.
     * @param command Command string (with or without leading "/")
     * @return Result message
     */
    public String executeCommand(String command) {
        // Remove leading "/" if present
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        
        // Split into parts
        String[] parts = command.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return "Invalid command";
        }
        
        String cmd = parts[0].toLowerCase();
        
        // Handle commands
        switch (cmd) {
            case "tp":
            case "teleport":
                return handleTeleport(parts);
            default:
                return "Unknown command: /" + cmd;
        }
    }
    
    /**
     * Handle teleport command: /tp x y z
     */
    private String handleTeleport(String[] parts) {
        if (parts.length != 4) {
            return "Usage: /tp <x> <y> <z>";
        }
        
        try {
            float x = Float.parseFloat(parts[1]);
            float y = Float.parseFloat(parts[2]);
            float z = Float.parseFloat(parts[3]);
            
            player.setPosition(x, y, z);
            return String.format("Teleported to %.2f, %.2f, %.2f", x, y, z);
        } catch (NumberFormatException e) {
            return "Invalid coordinates. Usage: /tp <x> <y> <z>";
        }
    }
}

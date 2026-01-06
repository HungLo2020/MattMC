package net.minecraft.worldedit.platform;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.worldedit.core.WorldEdit;

/**
 * MattMC platform implementation for WorldEdit.
 * This class bridges WorldEdit functionality with Minecraft internals.
 */
public class MattMCPlatform {
    private final MinecraftServer server;
    
    public MattMCPlatform(MinecraftServer server) {
        this.server = server;
    }
    
    /**
     * Get the Minecraft server instance.
     */
    public MinecraftServer getServer() {
        return server;
    }
    
    /**
     * Get a world by name.
     */
    public ServerLevel getWorld(String name) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(name)) {
                return level;
            }
        }
        return null;
    }
    
    /**
     * Get a player by UUID.
     */
    public ServerPlayer getPlayer(java.util.UUID uuid) {
        return server.getPlayerList().getPlayer(uuid);
    }
    
    /**
     * Get all online players.
     */
    public java.util.List<ServerPlayer> getPlayers() {
        return server.getPlayerList().getPlayers();
    }
    
    /**
     * Check if a player has a permission.
     */
    public boolean hasPermission(ServerPlayer player, String permission) {
        // For now, only ops have all permissions
        // TODO: Implement proper permission system
        return server.getPlayerList().isOp(player.nameAndId());
    }
}

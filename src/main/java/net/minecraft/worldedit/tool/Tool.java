package net.minecraft.worldedit.tool;

import net.minecraft.server.level.ServerPlayer;

/**
 * Represents a WorldEdit tool that can be bound to an item.
 */
public interface Tool {
    /**
     * Called when the tool is used with a primary action (left-click).
     */
    boolean actPrimary(ServerPlayer player);
    
    /**
     * Called when the tool is used with a secondary action (right-click).
     */
    boolean actSecondary(ServerPlayer player);
    
    /**
     * Check if a player can use this tool.
     */
    boolean canUse(ServerPlayer player);
}

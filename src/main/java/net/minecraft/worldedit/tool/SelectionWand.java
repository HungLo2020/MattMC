package net.minecraft.worldedit.tool;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.worldedit.core.WorldEdit;
import net.minecraft.worldedit.math.BlockVector3;
import net.minecraft.worldedit.platform.MattMCPlatform;
import net.minecraft.worldedit.session.LocalSession;

/**
 * The selection wand tool for WorldEdit.
 * Left-click (primary) sets pos2, right-click (secondary) sets pos1.
 */
public class SelectionWand implements Tool {
    
    @Override
    public boolean actPrimary(ServerPlayer player) {
        // Left-click sets secondary position (pos2)
        // This is called when canDestroyBlock triggers
        return true; // Handled in canDestroyBlock
    }
    
    @Override
    public boolean actSecondary(ServerPlayer player) {
        // Right-click sets primary position (pos1)
        // This is called from useOn
        return true; // Handled in useOn
    }
    
    @Override
    public boolean canUse(ServerPlayer player) {
        MattMCPlatform platform = WorldEdit.getInstance().getPlatform();
        return platform.hasPermission(player, "worldedit.selection.pos");
    }
    
    /**
     * Handle setting the primary position (pos1).
     */
    public void setPrimaryPosition(ServerPlayer player, BlockPos pos) {
        if (!canUse(player)) {
            return;
        }
        
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        BlockVector3 blockPos = BlockVector3.from(pos);
        
        System.out.println("SelectionWand: Setting primary position (pos1) to: " + blockPos);
        
        if (session.getCurrentSelector().selectPrimary(blockPos)) {
            session.getCurrentSelector().explainPrimarySelection(player, blockPos);
        }
    }
    
    /**
     * Handle setting the secondary position (pos2).
     */
    public void setSecondaryPosition(ServerPlayer player, BlockPos pos) {
        if (!canUse(player)) {
            return;
        }
        
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        BlockVector3 blockPos = BlockVector3.from(pos);
        
        System.out.println("SelectionWand: Setting secondary position (pos2) to: " + blockPos);
        
        if (session.getCurrentSelector().selectSecondary(blockPos)) {
            session.getCurrentSelector().explainSecondarySelection(player, blockPos);
        }
    }
}

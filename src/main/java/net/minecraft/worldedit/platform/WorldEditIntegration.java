package net.minecraft.worldedit.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.worldedit.core.WorldEdit;
import net.minecraft.worldedit.math.BlockVector3;
import net.minecraft.worldedit.session.LocalSession;
import net.minecraft.worldedit.tool.Tool;
import net.minecraft.worldedit.tool.SuperPickaxeTool;
import net.minecraft.worldedit.tool.BrushTool;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Central integration point between vanilla Minecraft and WorldEdit.
 * This class coordinates all interactions.
 */
public class WorldEditIntegration {
    
    /**
     * Handle wand left-click (set secondary position).
     */
    public static void handleWandLeftClick(ServerPlayer player, BlockPos pos) {
        if (!WorldEdit.isInitialized()) {
            return;
        }
        
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        BlockVector3 blockPos = BlockVector3.from(pos);
        
        if (session.getCurrentSelector().selectSecondary(blockPos)) {
            session.getCurrentSelector().explainSecondarySelection(player, blockPos);
        }
    }
    
    /**
     * Handle wand right-click (set primary position).
     */
    public static void handleWandRightClick(ServerPlayer player, BlockPos pos) {
        if (!WorldEdit.isInitialized()) {
            return;
        }
        
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        BlockVector3 blockPos = BlockVector3.from(pos);
        
        if (session.getCurrentSelector().selectPrimary(blockPos)) {
            session.getCurrentSelector().explainPrimarySelection(player, blockPos);
        }
    }
    
    /**
     * Handle left-click in air (for tools).
     */
    public static void onLeftClickAir(ServerPlayer player, InteractionHand hand) {
        if (!WorldEdit.isInitialized()) {
            return;
        }
        
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) {
            return;
        }
        
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        Tool tool = session.getTool(stack.getItem());
        
        if (tool != null && tool.canUse(player)) {
            tool.actPrimary(player);
        }
    }
    
    /**
     * Handle block break (for super pickaxe).
     */
    public static boolean onBlockBreak(ServerPlayer player, BlockPos pos) {
        if (!WorldEdit.isInitialized()) {
            return false;
        }
        
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            return false;
        }
        
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        Tool tool = session.getTool(stack.getItem());
        
        if (tool instanceof SuperPickaxeTool superPickaxe) {
            if (tool.canUse(player)) {
                superPickaxe.breakBlock(player, pos);
                return true; // Cancel default break behavior
            }
        }
        
        return false;
    }
    
    /**
     * Handle right-click on block (for brushes).
     */
    public static boolean onRightClickBlock(ServerPlayer player, BlockPos pos, InteractionHand hand) {
        if (!WorldEdit.isInitialized()) {
            return false;
        }
        
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) {
            return false;
        }
        
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        Tool tool = session.getTool(stack.getItem());
        
        if (tool instanceof BrushTool) {
            if (tool.canUse(player)) {
                return tool.actSecondary(player);
            }
        }
        
        return false;
    }
    
    /**
     * Handle player join.
     */
    public static void onPlayerJoin(ServerPlayer player) {
        if (!WorldEdit.isInitialized()) {
            return;
        }
        
        // Initialize session for player
        WorldEdit.getInstance().getSessionManager().get(player);
    }
    
    /**
     * Handle player disconnect.
     */
    public static void onPlayerDisconnect(ServerPlayer player) {
        if (!WorldEdit.isInitialized()) {
            return;
        }
        
        // Save and remove session
        WorldEdit.getInstance().getSessionManager().remove(player.getUUID());
    }
}

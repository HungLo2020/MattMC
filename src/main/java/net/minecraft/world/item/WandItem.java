package net.minecraft.world.item;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.worldedit.core.WorldEdit;
import net.minecraft.worldedit.session.LocalSession;
import net.minecraft.worldedit.tool.SelectionWand;
import net.minecraft.worldedit.tool.Tool;

/**
 * The WorldEdit selection wand item.
 * This item is hardcoded to always use the SelectionWand tool.
 */
public class WandItem extends Item {
    
    public WandItem(Properties properties) {
        super(properties);
    }
    
    /**
     * Ensure the SelectionWand tool is bound to this item for the player.
     */
    private void ensureToolBound(ServerPlayer player) {
        if (!WorldEdit.isInitialized()) {
            return;
        }
        
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        Tool tool = session.getTool(this);
        
        // If no tool is bound or wrong tool, bind SelectionWand
        if (!(tool instanceof SelectionWand)) {
            System.out.println("Binding SelectionWand tool to wand item for " + player.getName().getString());
            session.setTool(this, new SelectionWand());
        }
    }
    
    @Override
    public boolean canDestroyBlock(ItemStack itemStack, BlockState blockState, Level level, BlockPos blockPos, LivingEntity livingEntity) {
        // Handle left-click - sets secondary position (pos2)
        if (!level.isClientSide() && livingEntity instanceof ServerPlayer serverPlayer) {
            System.out.println("WandItem.canDestroyBlock called - Left-click at " + blockPos);
            
            ensureToolBound(serverPlayer);
            
            if (WorldEdit.isInitialized()) {
                LocalSession session = WorldEdit.getInstance().getSessionManager().get(serverPlayer);
                Tool tool = session.getTool(this);
                
                if (tool instanceof SelectionWand selectionWand) {
                    System.out.println("Found SelectionWand tool, calling setSecondaryPosition");
                    selectionWand.setSecondaryPosition(serverPlayer, blockPos);
                } else {
                    System.out.println("WARNING: No SelectionWand tool bound to wand item!");
                }
            }
        }
        // Prevent block from being destroyed
        return false;
    }
    
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        
        Player player = context.getPlayer();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        
        BlockPos pos = context.getClickedPos();
        
        System.out.println("WandItem.useOn called - Right-click at " + pos);
        
        ensureToolBound(serverPlayer);
        
        if (WorldEdit.isInitialized()) {
            LocalSession session = WorldEdit.getInstance().getSessionManager().get(serverPlayer);
            Tool tool = session.getTool(this);
            
            if (tool instanceof SelectionWand selectionWand) {
                System.out.println("Found SelectionWand tool, calling setPrimaryPosition");
                selectionWand.setPrimaryPosition(serverPlayer, pos);
            } else {
                System.out.println("WARNING: No SelectionWand tool bound to wand item!");
            }
        }
        
        return InteractionResult.SUCCESS;
    }
}

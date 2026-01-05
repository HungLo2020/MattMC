package net.minecraft.world.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.worldedit.platform.WorldEditIntegration;

/**
 * The WorldEdit selection wand item.
 * Left-click sets primary position (pos1), right-click sets secondary position (pos2).
 */
public class WandItem extends Item {
    
    public WandItem(Properties properties) {
        super(properties);
    }
    
    @Override
    public boolean canDestroyBlock(ItemStack itemStack, BlockState blockState, Level level, BlockPos blockPos, LivingEntity livingEntity) {
        // Handle left-click (set primary position)
        if (!level.isClientSide() && livingEntity instanceof ServerPlayer serverPlayer) {
            System.out.println("WandItem.canDestroyBlock called - Left-click at " + blockPos);
            WorldEditIntegration.handleWandLeftClick(serverPlayer, blockPos);
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
        
        // Debug logging
        System.out.println("WandItem.useOn called - Right-click at " + pos);
        
        // Right-click sets secondary position
        WorldEditIntegration.handleWandRightClick(serverPlayer, pos);
        
        return InteractionResult.SUCCESS;
    }
    
    // Left-click is handled in canDestroyBlock method above
    // Right-click is handled in useOn method above
}

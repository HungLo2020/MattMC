package net.minecraft.world.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.worldedit.platform.WorldEditIntegration;

/**
 * The WorldEdit selection wand item.
 * Right-click sets primary position, left-click sets secondary position.
 */
public class WandItem extends Item {
    
    public WandItem(Properties properties) {
        super(properties);
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
        
        // Right-click sets primary position
        WorldEditIntegration.handleWandRightClick(serverPlayer, pos);
        
        return InteractionResult.SUCCESS;
    }
    
    // Left-click is handled in ServerGamePacketListenerImpl
    // via the WorldEditIntegration.handleWandLeftClick method
}

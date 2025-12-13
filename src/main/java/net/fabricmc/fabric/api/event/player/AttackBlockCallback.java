package net.fabricmc.fabric.api.event.player;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Fabric API stub for AttackBlockCallback
 */
@FunctionalInterface
public interface AttackBlockCallback {
    Event<AttackBlockCallback> EVENT = Event.create(AttackBlockCallback.class, callbacks -> (player, world, hand, pos, direction) -> {
        for (AttackBlockCallback callback : callbacks) {
            InteractionResult result = callback.interact(player, world, hand, pos, direction);
            if (result != InteractionResult.PASS) {
                return result;
            }
        }
        return InteractionResult.PASS;
    });
    
    InteractionResult interact(Player player, Level world, InteractionHand hand, BlockPos pos, Direction direction);
}

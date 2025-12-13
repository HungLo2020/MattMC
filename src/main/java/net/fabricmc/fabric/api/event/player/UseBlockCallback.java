package net.fabricmc.fabric.api.event.player;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Fabric API stub for UseBlockCallback
 */
@FunctionalInterface
public interface UseBlockCallback {
    Event<UseBlockCallback> EVENT = Event.create(UseBlockCallback.class, callbacks -> (player, world, hand, hitResult) -> {
        for (UseBlockCallback callback : callbacks) {
            InteractionResult result = callback.interact(player, world, hand, hitResult);
            if (result != InteractionResult.PASS) {
                return result;
            }
        }
        return InteractionResult.PASS;
    });
    
    InteractionResult interact(Player player, Level world, InteractionHand hand, BlockHitResult hitResult);
}

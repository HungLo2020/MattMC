package net.fabricmc.fabric.api.event.player;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

@FunctionalInterface
public interface AttackBlockCallback {
    Event<AttackBlockCallback> EVENT = EventFactory.createArrayBacked(AttackBlockCallback.class,
        (listeners) -> (player, world, hand, pos, direction) -> {
            for (AttackBlockCallback listener : listeners) {
                InteractionResult result = listener.interact(player, world, hand, pos, direction);

                if (result != InteractionResult.PASS) {
                    return result;
                }
            }

            return InteractionResult.PASS;
        }
    );

    InteractionResult interact(Player player, Level world, net.minecraft.world.InteractionHand hand, BlockPos pos, Direction direction);
}

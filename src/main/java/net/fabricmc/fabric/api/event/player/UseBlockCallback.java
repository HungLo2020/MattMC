package net.fabricmc.fabric.api.event.player;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.Level;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/**
 * Callback for right-clicking ("using") a block.
 * Is hooked in before the spectator check, so make sure to check for the player's game mode as well!
 *
 * <p>Upon return:
 * <ul><li>SUCCESS cancels further processing and, on the client, sends a packet to the server.
 * <li>PASS falls back to further processing.
 * <li>FAIL cancels further processing and does not send a packet to the server.</ul>
 */
public interface UseBlockCallback {
	Event<UseBlockCallback> EVENT = EventFactory.createArrayBacked(UseBlockCallback.class,
			(listeners) -> (player, world, hand, hitResult) -> {
				for (UseBlockCallback event : listeners) {
					InteractionResult result = event.interact(player, world, hand, hitResult);

					if (result != InteractionResult.PASS) {
						return result;
					}
				}

				return InteractionResult.PASS;
			}
	);

	InteractionResult interact(Player player, Level world, InteractionHand hand, BlockHitResult hitResult);
}

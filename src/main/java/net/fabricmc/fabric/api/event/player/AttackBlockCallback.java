package net.fabricmc.fabric.api.event.player;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/**
 * Callback for left-clicking ("attacking") a block.
 * Is hooked in before the spectator check, so make sure to check for the player's game mode as well!
 *
 * <p>On the logical client, the return values have the following meaning:
 * <ul>
 *     <li>SUCCESS cancels further processing, causes a hand swing, and sends a packet to the server.</li>
 *     <li>CONSUME cancels further processing, and sends a packet to the server. It does NOT cause a hand swing.</li>
 *     <li>PASS falls back to further processing.</li>
 *     <li>FAIL cancels further processing and does not send a packet to the server.</li>
 * </ul>
 *
 * <p>On the logical server, the return values have the following meaning:
 * <ul>
 *     <li>PASS falls back to further processing.</li>
 *     <li>Any other value cancels further processing.</li>
 * </ul>
 */
public interface AttackBlockCallback {
	Event<AttackBlockCallback> EVENT = EventFactory.createArrayBacked(AttackBlockCallback.class,
			(listeners) -> (player, world, hand, pos, direction) -> {
				for (AttackBlockCallback event : listeners) {
					InteractionResult result = event.interact(player, world, hand, pos, direction);

					if (result != InteractionResult.PASS) {
						return result;
					}
				}

				return InteractionResult.PASS;
			}
	);

	InteractionResult interact(Player player, Level world, InteractionHand hand, BlockPos pos, Direction direction);
}

package net.minecraft.client.tacz;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.TaczGunInputC2SPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TaczMvpGunItem;

public final class TaczClientInputHandler {
	private TaczClientInputHandler() {
	}

	public static boolean handleKeybinds(Minecraft minecraft) {
		if (minecraft.screen != null || minecraft.player == null || minecraft.level == null || minecraft.gameMode == null) {
			return false;
		}

		ItemStack itemStack = minecraft.player.getMainHandItem();
		if (!itemStack.is(Items.TACZ_GLOCK_17) || !(itemStack.getItem() instanceof TaczMvpGunItem gunItem) || minecraft.player.isSpectator()) {
			return false;
		}

		while (minecraft.options.keyAttack.consumeClick()) {
		}

		while (minecraft.options.keyUse.consumeClick()) {
		}

		while (TaczKeyMappings.SHOOT.consumeClick()) {
			if (!minecraft.player.isUsingItem() && !minecraft.player.getCooldowns().isOnCooldown(itemStack)) {
				InteractionResult interactionResult = gunItem.tryFire(minecraft.level, minecraft.player, InteractionHand.MAIN_HAND, itemStack);
				if (interactionResult.consumesAction()) {
					minecraft.gameRenderer.itemInHandRenderer.itemUsed(InteractionHand.MAIN_HAND);
					ClientPlayNetworking.send(new TaczGunInputC2SPayload(TaczGunInputC2SPayload.Action.SHOOT));
				}
			}
		}

		while (TaczKeyMappings.RELOAD.consumeClick()) {
			if (!minecraft.player.isUsingItem()) {
				ClientPlayNetworking.send(new TaczGunInputC2SPayload(TaczGunInputC2SPayload.Action.RELOAD));
			}
		}

		return true;
	}
}

package net.minecraft.client.tacz;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.special.TaczGlock17SpecialRenderer;
import net.minecraft.network.protocol.common.custom.TaczGunInputC2SPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TaczFireMode;
import net.minecraft.world.item.TaczGunBurstData;
import net.minecraft.world.item.TaczMvpGunItem;
import net.minecraft.world.item.TaczRefitGun;
import net.minecraft.world.entity.projectile.TaczBulletEffectHooks;

public final class TaczClientInputHandler {
	private static final ScheduledExecutorService BURST_FEEDBACK_EXECUTOR = Executors.newSingleThreadScheduledExecutor(runnable -> {
		Thread thread = new Thread(runnable, "TACZ Burst Feedback");
		thread.setDaemon(true);
		return thread;
	});

	static {
		TaczBulletEffectHooks.setAmmoParticleSpawner(TaczAmmoParticleSpawner::addParticle);
	}

	private TaczClientInputHandler() {
	}

	public static boolean handleKeybinds(Minecraft minecraft) {
		if (minecraft.screen != null || minecraft.player == null || minecraft.level == null || minecraft.gameMode == null) {
			return false;
		}

		ItemStack itemStack = minecraft.player.getMainHandItem();
		TaczGlock17AnimationController.updateHeld(itemStack);
		while (TaczKeyMappings.REFIT.consumeClick()) {
			if (!minecraft.player.isSpectator() && itemStack.getItem() instanceof TaczRefitGun) {
				minecraft.setScreen(new TaczGunRefitScreen());
				return true;
			}
		}

		if (!(itemStack.getItem() instanceof TaczMvpGunItem gunItem) || minecraft.player.isSpectator()) {
			return false;
		}

		while (minecraft.options.keyAttack.consumeClick()) {
		}

		while (minecraft.options.keyUse.consumeClick()) {
		}

		while (TaczKeyMappings.SHOOT.consumeClick()) {
			tryShoot(minecraft, itemStack, gunItem);
		}

		TaczFireMode fireMode = TaczMvpGunItem.getFireMode(itemStack);
		if ((fireMode == TaczFireMode.AUTO || fireMode == TaczFireMode.BURST && TaczGunBurstData.burst(gunItem.gunId()).continuousShoot()) && TaczKeyMappings.SHOOT.isDown()) {
			tryShoot(minecraft, itemStack, gunItem);
		}

		while (TaczKeyMappings.RELOAD.consumeClick()) {
			if (!minecraft.player.isUsingItem()) {
				if (TaczMvpGunItem.canStartReload(minecraft.player, itemStack)) {
					TaczGlock17AnimationController.triggerReload(itemStack);
				}
				ClientPlayNetworking.send(new TaczGunInputC2SPayload(TaczGunInputC2SPayload.Action.RELOAD));
			}
		}

		while (TaczKeyMappings.FIRE_SELECT.consumeClick()) {
			if (!minecraft.player.isUsingItem()) {
				gunItem.cycleFireMode(itemStack);
				TaczGlock17AnimationController.triggerFireSelect(itemStack);
				minecraft.level
					.playSound(
						null,
						minecraft.player.getX(),
						minecraft.player.getY(),
						minecraft.player.getZ(),
						SoundEvent.createVariableRangeEvent(ResourceLocation.withDefaultNamespace("fire_select")),
						SoundSource.PLAYERS,
						0.8F,
						1.0F
					);
				ClientPlayNetworking.send(new TaczGunInputC2SPayload(TaczGunInputC2SPayload.Action.FIRE_SELECT));
			}
		}

		while (TaczKeyMappings.INSPECT.consumeClick()) {
			if (!minecraft.player.isUsingItem()) {
				TaczGlock17AnimationController.triggerInspect(itemStack);
			}
		}

		return true;
	}

	private static void tryShoot(Minecraft minecraft, ItemStack itemStack, TaczMvpGunItem gunItem) {
		if (minecraft.player == null || minecraft.level == null || minecraft.player.isUsingItem() || minecraft.player.getCooldowns().isOnCooldown(itemStack)) {
			return;
		}

		boolean precisionAiming = isPrecisionAiming();
		int ammoBeforeShot = TaczMvpGunItem.getAmmo(itemStack);
		InteractionResult interactionResult = gunItem.tryFire(minecraft.level, minecraft.player, InteractionHand.MAIN_HAND, itemStack, precisionAiming);
		if (interactionResult.consumesAction()) {
			minecraft.gameRenderer.itemInHandRenderer.itemUsed(InteractionHand.MAIN_HAND);
			if (ammoBeforeShot > 0) {
				scheduleClientShotFeedback(minecraft, itemStack, gunItem, ammoBeforeShot);
			}
			ClientPlayNetworking.send(new TaczGunInputC2SPayload(TaczGunInputC2SPayload.Action.SHOOT, precisionAiming));
		}
	}

	private static boolean isPrecisionAiming() {
		return TaczKeyMappings.AIM.isDown() && TaczGlock17AnimationController.aimProgress(1.0F) >= 1.0F;
	}

	private static void scheduleClientShotFeedback(Minecraft minecraft, ItemStack itemStack, TaczMvpGunItem gunItem, int ammoBeforeShot) {
		int shots = gunItem.roundsPerTrigger(itemStack, ammoBeforeShot);
		long intervalMillis = gunItem.burstIntervalMillis(itemStack);
		for (int shot = 0; shot < shots; shot++) {
			int scheduledShot = shot;
			if (scheduledShot == 0) {
				playClientShotFeedback(minecraft, itemStack, gunItem);
				continue;
			}
			BURST_FEEDBACK_EXECUTOR.schedule(() -> minecraft.execute(() -> {
				playClientShotFeedback(minecraft, itemStack, gunItem);
			}), scheduledShot * intervalMillis, TimeUnit.MILLISECONDS);
		}
	}

	private static void playClientShotFeedback(Minecraft minecraft, ItemStack itemStack, TaczMvpGunItem gunItem) {
		if (minecraft.player == null || minecraft.level == null || !ItemStack.isSameItem(minecraft.player.getMainHandItem(), itemStack)) {
			return;
		}

		TaczGlock17AnimationController.triggerShoot(itemStack);
		TaczCameraRecoil.trigger(itemStack);
		TaczGlock17SpecialRenderer.triggerMuzzleFlash();
		minecraft.level
			.playSound(
				minecraft.player,
				minecraft.player.getX(),
				minecraft.player.getY(),
				minecraft.player.getZ(),
				gunItem.shootSound(),
				SoundSource.PLAYERS,
				1.25F,
				0.96F + minecraft.level.random.nextFloat() * 0.08F
			);
	}
}

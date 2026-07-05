package net.minecraft.client.tacz;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TaczMvpGunItem;

public final class TaczGlock17AnimationController {
	private static final float AIM_SECONDS = 0.16F;
	private static final float DRAW_SECONDS = 0.7083F;
	private static final float PUT_AWAY_SECONDS = 0.4F;
	private static final float SHOOT_SECONDS = 0.6F;
	private static final float RELOAD_TACTICAL_SECONDS = 1.9333F;
	private static final float RELOAD_EMPTY_SECONDS = 2.2833F;
	private static final float INSPECT_SECONDS = 11.25F;
	private static final float INSPECT_EMPTY_SECONDS = 12.2833F;
	private static boolean wasHolding;
	private static long lastUpdateNanos = System.nanoTime();
	private static float aimProgress;
	private static float previousAimProgress;
	private static ActiveSequence mainAnimation;
	private static ActiveAnimation shootAnimation;
	private static String heldGunId = "glock_17";

	private TaczGlock17AnimationController() {
	}

	public static void updateHeld(ItemStack itemStack) {
		long now = System.nanoTime();
		float deltaSeconds = Math.min((now - lastUpdateNanos) / 1.0E9F, 0.1F);
		lastUpdateNanos = now;
		boolean holding = itemStack.getItem() instanceof TaczMvpGunItem;
		heldGunId = itemStack.getItem() instanceof TaczMvpGunItem gunItem ? gunItem.gunId() : "glock_17";
		if (holding && !wasHolding) {
			triggerMain(itemStack, "draw");
		} else if (!holding && wasHolding) {
			triggerMain(itemStack, "put_away");
		}

		wasHolding = holding;
		float targetAim = holding && TaczKeyMappings.AIM.isDown() ? 1.0F : 0.0F;
		float step = deltaSeconds / AIM_SECONDS;
		previousAimProgress = aimProgress;
		aimProgress = Mth.clamp(aimProgress + Math.signum(targetAim - aimProgress) * step, 0.0F, 1.0F);
		if (Math.abs(targetAim - aimProgress) <= step) {
			aimProgress = targetAim;
		}
	}

	public static float aimProgress(float partialTick) {
		return Mth.lerp(partialTick, previousAimProgress, aimProgress);
	}

	public static void triggerShoot() {
		shootAnimation = new ActiveAnimation("shoot", System.nanoTime(), true);
	}

	public static void triggerShoot(ItemStack itemStack) {
		shootAnimation = new ActiveAnimation(TaczGunAnimationTimings.shootAnimation(itemStack), System.nanoTime(), true);
	}

	public static void triggerReload(ItemStack itemStack) {
		triggerMain(itemStack, TaczGunAnimationTimings.reloadSequence(itemStack));
	}

	public static void triggerInspect(ItemStack itemStack) {
		triggerMain(itemStack, TaczMvpGunItem.getAmmo(itemStack) <= 0 ? "inspect_empty" : "inspect");
	}

	public static Snapshot snapshot(ItemStack itemStack) {
		List<ActiveAnimation> animations = new ArrayList<>();
		clearExpiredAnimations();
		animations.add(new ActiveAnimation("static_idle", 0L, false));
		if (itemStack.getItem() instanceof TaczMvpGunItem && TaczMvpGunItem.getAmmo(itemStack) <= 0 && !isReloading()) {
			animations.add(new ActiveAnimation("static_bolt_caught", 0L, false));
		}
		if (mainAnimation != null) {
			animations.add(mainAnimation.currentAnimation());
		}
		if (shootAnimation != null) {
			animations.add(shootAnimation);
		}
		return new Snapshot(List.copyOf(animations), aimProgress);
	}

	private static void triggerMain(ItemStack itemStack, String name) {
		triggerMain(itemStack, List.of(name));
	}

	private static void triggerMain(ItemStack itemStack, List<String> names) {
		mainAnimation = new ActiveSequence(itemStack.getItem() instanceof TaczMvpGunItem gunItem ? gunItem.gunId() : "glock_17", List.copyOf(names), System.nanoTime());
	}

	private static void clearExpiredAnimations() {
		if (mainAnimation != null && mainAnimation.ageSeconds() > mainAnimation.totalDurationSeconds()) {
			mainAnimation = null;
		}
		if (shootAnimation != null && shootAnimation.ageSeconds() > TaczGunAnimationTimings.duration(heldGunId, shootAnimation.name())) {
			shootAnimation = null;
		}
	}

	private static boolean isReloading() {
		return mainAnimation != null && mainAnimation.currentAnimation().name().startsWith("reload_") && mainAnimation.ageSeconds() <= mainAnimation.totalDurationSeconds();
	}

	public record Snapshot(List<ActiveAnimation> animations, float aimProgress) {
	}

	public record ActiveAnimation(String name, long startNanos, boolean additive) {
		public float ageSeconds() {
			return this.startNanos == 0L ? 0.0F : (System.nanoTime() - this.startNanos) / 1.0E9F;
		}
	}

	private record ActiveSequence(String gunId, List<String> names, long startNanos) {
		private ActiveAnimation currentAnimation() {
			float age = this.ageSeconds();
			float elapsed = 0.0F;
			for (String name : this.names) {
				float duration = TaczGunAnimationTimings.duration(this.gunId, name);
				if (age <= elapsed + duration || name.equals(this.names.get(this.names.size() - 1))) {
					long segmentStart = this.startNanos + (long)(elapsed * 1.0E9F);
					return new ActiveAnimation(name, segmentStart, false);
				}
				elapsed += duration;
			}
			return new ActiveAnimation(this.names.get(this.names.size() - 1), this.startNanos, false);
		}

		private float ageSeconds() {
			return (System.nanoTime() - this.startNanos) / 1.0E9F;
		}

		private float totalDurationSeconds() {
			float total = 0.0F;
			for (String name : this.names) {
				total += TaczGunAnimationTimings.duration(this.gunId, name);
			}
			return total;
		}
	}
}

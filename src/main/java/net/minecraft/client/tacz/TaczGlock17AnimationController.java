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
	private static ActiveAnimation mainAnimation;
	private static ActiveAnimation shootAnimation;

	private TaczGlock17AnimationController() {
	}

	public static void updateHeld(ItemStack itemStack) {
		long now = System.nanoTime();
		float deltaSeconds = Math.min((now - lastUpdateNanos) / 1.0E9F, 0.1F);
		lastUpdateNanos = now;
		boolean holding = itemStack.getItem() instanceof TaczMvpGunItem;
		if (holding && !wasHolding) {
			triggerMain("draw");
		} else if (!holding && wasHolding) {
			triggerMain("put_away");
		}

		wasHolding = holding;
		float targetAim = holding && TaczKeyMappings.AIM.isDown() ? 1.0F : 0.0F;
		float step = deltaSeconds / AIM_SECONDS;
		aimProgress = Mth.clamp(aimProgress + Math.signum(targetAim - aimProgress) * step, 0.0F, 1.0F);
		if (Math.abs(targetAim - aimProgress) <= step) {
			aimProgress = targetAim;
		}
	}

	public static void triggerShoot() {
		shootAnimation = new ActiveAnimation("shoot", System.nanoTime(), true);
	}

	public static void triggerReload(ItemStack itemStack) {
		triggerMain(TaczMvpGunItem.getAmmo(itemStack) <= 0 ? "reload_empty" : "reload_tactical");
	}

	public static void triggerInspect(ItemStack itemStack) {
		triggerMain(TaczMvpGunItem.getAmmo(itemStack) <= 0 ? "inspect_empty" : "inspect");
	}

	public static Snapshot snapshot(ItemStack itemStack) {
		List<ActiveAnimation> animations = new ArrayList<>();
		clearExpiredAnimations();
		animations.add(new ActiveAnimation("static_idle", 0L, false));
		if (itemStack.getItem() instanceof TaczMvpGunItem && TaczMvpGunItem.getAmmo(itemStack) <= 0 && !isReloading()) {
			animations.add(new ActiveAnimation("static_bolt_caught", 0L, false));
		}
		if (mainAnimation != null) {
			animations.add(mainAnimation);
		}
		if (shootAnimation != null) {
			animations.add(shootAnimation);
		}
		return new Snapshot(List.copyOf(animations), aimProgress);
	}

	private static void triggerMain(String name) {
		mainAnimation = new ActiveAnimation(name, System.nanoTime(), false);
	}

	private static void clearExpiredAnimations() {
		if (mainAnimation != null && mainAnimation.ageSeconds() > duration(mainAnimation.name())) {
			mainAnimation = null;
		}
		if (shootAnimation != null && shootAnimation.ageSeconds() > SHOOT_SECONDS) {
			shootAnimation = null;
		}
	}

	private static boolean isReloading() {
		return mainAnimation != null && mainAnimation.name().startsWith("reload_") && mainAnimation.ageSeconds() <= duration(mainAnimation.name());
	}

	private static float duration(String name) {
		return switch (name) {
			case "draw" -> DRAW_SECONDS;
			case "put_away" -> PUT_AWAY_SECONDS;
			case "reload_tactical" -> RELOAD_TACTICAL_SECONDS;
			case "reload_empty" -> RELOAD_EMPTY_SECONDS;
			case "inspect" -> INSPECT_SECONDS;
			case "inspect_empty" -> INSPECT_EMPTY_SECONDS;
			default -> 0.0F;
		};
	}

	public record Snapshot(List<ActiveAnimation> animations, float aimProgress) {
	}

	public record ActiveAnimation(String name, long startNanos, boolean additive) {
		public float ageSeconds() {
			return this.startNanos == 0L ? 0.0F : (System.nanoTime() - this.startNanos) / 1.0E9F;
		}
	}
}

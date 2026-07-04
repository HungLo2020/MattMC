package net.minecraft.client.tacz;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TaczMvpGunItem;

public final class TaczGlock17AnimationController {
	private static final float AIM_SECONDS = 0.16F;
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
		boolean holding = itemStack.is(Items.TACZ_GLOCK_17);
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
		animations.add(new ActiveAnimation("static_idle", 0L, false));
		if (itemStack.is(Items.TACZ_GLOCK_17) && TaczMvpGunItem.getAmmo(itemStack) <= 0 && (mainAnimation == null || !mainAnimation.name().startsWith("reload_"))) {
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

	public record Snapshot(List<ActiveAnimation> animations, float aimProgress) {
	}

	public record ActiveAnimation(String name, long startNanos, boolean additive) {
		public float ageSeconds() {
			return this.startNanos == 0L ? 0.0F : (System.nanoTime() - this.startNanos) / 1.0E9F;
		}
	}
}

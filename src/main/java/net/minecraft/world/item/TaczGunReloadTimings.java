package net.minecraft.world.item;

public final class TaczGunReloadTimings {
	private TaczGunReloadTimings() {
	}

	public static int reloadTicks(ItemStack itemStack, int defaultTicks) {
		if (!(itemStack.getItem() instanceof TaczMvpGunItem gunItem)) {
			return defaultTicks;
		}

		int currentAmmo = TaczMvpGunItem.getAmmo(itemStack);
		int neededAmmo = Math.max(0, TaczMvpGunItem.getMagazineSize(itemStack) - currentAmmo);
		if (neededAmmo <= 0) {
			return defaultTicks;
		}

		return switch (gunItem.gunId()) {
			case "m1897" -> shotgunLoopReloadTicks(currentAmmo <= 0, neededAmmo, 2.13F, 0.37F, 0.67F, 0.17F);
			case "stg44" -> stg44ReloadTicks(itemStack, currentAmmo <= 0);
			case "trs_bull" -> secondsToTicks(currentAmmo <= 0 ? 3.06F : ragingBullTacticalSeconds(currentAmmo));
			default -> defaultTicks;
		};
	}

	private static int shotgunLoopReloadTicks(boolean empty, int neededAmmo, float introEmpty, float introTactical, float loop, float ending) {
		float seconds = (empty ? introEmpty : introTactical) + neededAmmo * loop + ending;
		return secondsToTicks(seconds);
	}

	private static int stg44ReloadTicks(ItemStack itemStack, boolean empty) {
		ItemStack extendedMag = TaczRefitGun.getStoredAttachment(itemStack, TaczAttachmentType.EXTENDED_MAG);
		int level = extendedMag.getItem() instanceof TaczAttachmentItem attachment ? attachment.getAttachmentLevel() : 0;
		float seconds = switch (Math.max(0, Math.min(level, 3))) {
			case 1, 2, 3 -> empty ? 2.85F : 2.29F;
			default -> empty ? 2.60F : 2.00F;
		};
		return secondsToTicks(seconds);
	}

	private static float ragingBullTacticalSeconds(int currentAmmo) {
		return switch (currentAmmo) {
			case 1 -> 3.94F;
			case 2 -> 4.86F;
			case 3 -> 3.60F;
			case 4 -> 3.72F;
			default -> 3.46F;
		};
	}

	private static int secondsToTicks(float seconds) {
		return Math.max(1, Math.round(seconds * 20.0F));
	}
}

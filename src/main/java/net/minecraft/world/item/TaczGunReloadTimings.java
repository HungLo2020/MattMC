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
			case "m1897" -> shotgunLoopReloadTicks(currentAmmo <= 0, neededAmmo, 2.1667F, 0.4667F, 0.7F, 0.7333F);
			case "m870" -> shotgunLoopReloadTicks(currentAmmo <= 0, neededAmmo, 2.1667F, 0.4667F, 0.7F, 0.7333F);
			case "m1014" -> m1014ReloadTicks(currentAmmo <= 0, neededAmmo);
			case "kar98" -> kar98ReloadTicks(itemStack, currentAmmo, neededAmmo);
			case "m320" -> secondsToTicks(3.04F);
			case "rpg7" -> secondsToTicks(3.53333F);
			case "spas_12" -> spas12ReloadTicks(itemStack, currentAmmo <= 0, neededAmmo);
			case "springfield1873" -> secondsToTicks(2.95F);
			case "stg44" -> stg44ReloadTicks(itemStack, currentAmmo <= 0);
			case "trs_bull" -> secondsToTicks(currentAmmo <= 0 ? 3.06F : ragingBullTacticalSeconds(currentAmmo));
			default -> defaultTicks;
		};
	}

	private static int kar98ReloadTicks(ItemStack itemStack, int currentAmmo, int neededAmmo) {
		if (currentAmmo <= 0 && !(TaczRefitGun.getStoredAttachment(itemStack, TaczAttachmentType.SCOPE).getItem() instanceof TaczAttachmentItem)) {
			return secondsToTicks(3.45F);
		}
		float intro = currentAmmo <= 0 ? 1.6F : 0.75F;
		return secondsToTicks(intro + neededAmmo * 0.6833F + 0.9833F);
	}

	private static int shotgunLoopReloadTicks(boolean empty, int neededAmmo, float introEmpty, float introTactical, float loop, float ending) {
		float seconds = (empty ? introEmpty : introTactical) + neededAmmo * loop + ending;
		return secondsToTicks(seconds);
	}

	private static int m1014ReloadTicks(boolean empty, int neededAmmo) {
		float seconds = empty ? 1.8F : 0.5F;
		int remaining = neededAmmo;
		while (remaining > 0) {
			if (remaining > 1) {
				seconds += 0.7F;
				remaining -= 2;
			} else {
				seconds += 0.6667F;
				remaining--;
			}
		}
		return secondsToTicks(seconds + 0.7167F);
	}

	private static int spas12ReloadTicks(ItemStack itemStack, boolean empty, int neededAmmo) {
		boolean semi = TaczMvpGunItem.getFireMode(itemStack) == TaczFireMode.BURST;
		if (semi) {
			return shotgunLoopReloadTicks(empty, neededAmmo, 1.8667F, 0.7F, 0.8667F, 0.8667F);
		}
		return shotgunLoopReloadTicks(empty, neededAmmo, 2.4333F, 0.7F, 0.6667F, 0.8667F);
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

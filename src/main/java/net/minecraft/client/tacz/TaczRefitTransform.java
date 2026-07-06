package net.minecraft.client.tacz;

import net.minecraft.util.Mth;
import net.minecraft.world.item.TaczAttachmentType;

public final class TaczRefitTransform {
	private static final float OPEN_SECONDS = 0.25F;
	private static final float VIEW_SECONDS = 0.18F;
	private static long lastUpdateNanos = System.nanoTime();
	private static float openingProgress;
	private static float viewProgress = 1.0F;
	private static boolean open;
	private static TaczAttachmentType currentType = TaczAttachmentType.NONE;
	private static TaczAttachmentType previousType = TaczAttachmentType.NONE;

	private TaczRefitTransform() {
	}

	public static void open() {
		open = true;
		lastUpdateNanos = System.nanoTime();
	}

	public static void close() {
		open = false;
		lastUpdateNanos = System.nanoTime();
		changeView(TaczAttachmentType.NONE);
	}

	public static boolean isOpen() {
		return open || openingProgress > 0.0F;
	}

	public static float openingProgress() {
		update();
		return smooth(openingProgress);
	}

	public static float viewProgress() {
		update();
		return smooth(viewProgress);
	}

	public static TaczAttachmentType currentType() {
		return currentType;
	}

	public static TaczAttachmentType previousType() {
		return previousType;
	}

	public static boolean changeView(TaczAttachmentType type) {
		if (type == currentType) {
			return false;
		}

		previousType = currentType;
		currentType = type;
		viewProgress = 0.0F;
		lastUpdateNanos = System.nanoTime();
		return true;
	}

	private static void update() {
		long now = System.nanoTime();
		float deltaSeconds = Math.min((now - lastUpdateNanos) / 1.0E9F, 0.1F);
		lastUpdateNanos = now;
		float openStep = deltaSeconds / OPEN_SECONDS;
		float viewStep = deltaSeconds / VIEW_SECONDS;
		openingProgress = Mth.clamp(openingProgress + (open ? openStep : -openStep), 0.0F, 1.0F);
		viewProgress = Mth.clamp(viewProgress + viewStep, 0.0F, 1.0F);
	}

	private static float smooth(float value) {
		return 1.0F - (float)Math.pow(1.0F - Mth.clamp(value, 0.0F, 1.0F), 3.0);
	}
}

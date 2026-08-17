package net.vulkanic.bridge;

import net.minecraft.client.DeltaTracker;

/**
 * Renderer-neutral deterministic timing used by the Rust whole-frame route.
 * Property names intentionally match the cross-repository parity fixtures;
 * this class has no Iris renderer or uniform-system dependency.
 */
public final class RustGalDeterministicTiming {
	private static final boolean ENABLED = Boolean.getBoolean("mattmc.vulkan.deterministicTemporalParity");
	private static final float PARTIAL_TICK = Float.parseFloat(
		System.getProperty("mattmc.vulkan.deterministicTemporalParity.partialTick", "1.0")
	);
	private static final float FOV_MODIFIER = Float.parseFloat(
		System.getProperty("mattmc.vulkan.deterministicTemporalParity.fovModifier", "1.0")
	);

	private RustGalDeterministicTiming() {
	}

	public static boolean enabled() {
		return ENABLED;
	}

	public static float partialTick(DeltaTracker deltaTracker) {
		return ENABLED ? PARTIAL_TICK : deltaTracker.getGameTimeDeltaPartialTick(true);
	}

	public static float deterministicPartialTick() {
		return PARTIAL_TICK;
	}

	public static float fovModifier() {
		return FOV_MODIFIER;
	}
}

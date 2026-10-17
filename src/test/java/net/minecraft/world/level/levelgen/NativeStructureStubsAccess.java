package net.minecraft.world.level.levelgen;

import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;

/** Test access to {@link NativeStructureStubs}' monument regeneration switch and counters. */
public final class NativeStructureStubsAccess {
	private NativeStructureStubsAccess() {
	}

	public static void setMonumentRegeneration(boolean value) {
		NativeStructureStubs.monumentRegeneration = value;
	}

	public static long monumentsRegenerated() {
		return NativeStructureStubs.MONUMENTS_REGENERATED.get();
	}

	public static int[] regenerate(long seed, ChunkPos chunk, int minX, int minZ, Direction orientation, long[] state) {
		return NativeStructureStubs.regenerateMonument(seed, chunk, minX, minZ, orientation, state);
	}
}

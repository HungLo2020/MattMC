package net.minecraft.world.level.levelgen.blending;

import net.minecraft.core.QuartPos;

/** Literal original NoiseChunk initialization loop, with field names as arguments. */
final class JavaBlendingGrid {
    static void fill(JavaBlender blender, int firstNoiseX, int firstNoiseZ, int noiseSizeXZ, double[] alpha, double[] offset) {
			for (int l = 0; l <= noiseSizeXZ; l++) {
				int m = firstNoiseX + l;
				int n = QuartPos.toBlock(m);

				for (int o = 0; o <= noiseSizeXZ; o++) {
					int p = firstNoiseZ + o;
					int q = QuartPos.toBlock(p);
					JavaBlender.BlendingOutput blendingOutput = blender.blendOffsetAndFactor(n, q);
					alpha[l + o * (noiseSizeXZ + 1)] = blendingOutput.alpha();
					offset[l + o * (noiseSizeXZ + 1)] = blendingOutput.blendingOffset();
				}
			}
    }
}

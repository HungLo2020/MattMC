package net.fabricmc.loader.impl.transformer;

import net.fabricmc.api.EnvType;

/**
 * Simplified transformer for integrated mod approach.
 * No transformation needed - all code is compiled together with correct access modifiers
 * and namespace mappings.
 */
public final class FabricTransformer {
	public static byte[] transform(boolean isDevelopment, EnvType envType, String name, byte[] bytes) {
		// No transformation needed for integrated mod approach
		// - Access modifiers already correct (no package access hack needed)
		// - Environment stripping not needed (compilation handles this)
		// - No mixins to apply (using hook system instead)
		return bytes;
	}
}

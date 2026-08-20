package net.vulkanic.world;

import net.blaze3d.pipeline.BlendFunction;
import net.minecraft.client.renderer.RenderType;

/**
 * Explicit capability checks for the current copied indexed-mesh material
 * contract. These checks describe what the Rust material registry can preserve
 * today; they do not infer semantics from entity names, textures, or producers.
 */
public final class IndexedMeshMaterialCapabilities {
	private IndexedMeshMaterialCapabilities() {
	}

	/**
	 * Returns whether the current coarse indexed-mesh material mapping preserves
	 * the Java pipeline's alpha-discard threshold exactly.
	 *
	 * <p>The existing mesh mapper lowers every non-blended pipeline to the 0.5
	 * cutout material and every translucent-blended pipeline to a material with
	 * no alpha discard. Until the material identity is split explicitly, 0.1
	 * entity cutout/translucent pipelines must remain unavailable under strict
	 * Rust ownership instead of being rendered approximately.</p>
	 */
	public static boolean preservesAlphaCutout(RenderType renderType) {
		if (renderType == null) {
			return false;
		}
		var pipeline = renderType.pipeline();
		var blend = pipeline.getBlendFunction();
		if (blend.isPresent() && !BlendFunction.TRANSLUCENT.equals(blend.get())) {
			return false;
		}
		String declared = pipeline.getShaderDefines().values().get("ALPHA_CUTOUT");
		float declaredThreshold;
		if (declared == null) {
			declaredThreshold = 0.0F;
		} else {
			try {
				declaredThreshold = Float.parseFloat(declared);
			} catch (NumberFormatException exception) {
				return false;
			}
		}
		float currentRustThreshold = blend.isPresent() ? 0.0F : 0.5F;
		return Float.floatToIntBits(declaredThreshold) == Float.floatToIntBits(currentRustThreshold);
	}
}

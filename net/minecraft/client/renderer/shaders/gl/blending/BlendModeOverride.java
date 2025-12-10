package net.minecraft.client.renderer.shaders.gl.blending;

/**
 * Provides blend mode override capability for shader passes.
 * IRIS 1.21.9 VERBATIM - from net.irisshaders.iris.gl.blending.BlendModeOverride
 */
public class BlendModeOverride {
	public static final BlendModeOverride OFF = new BlendModeOverride(null);

	private final BlendMode blendMode;

	public BlendModeOverride(BlendMode blendMode) {
		this.blendMode = blendMode;
	}

	public static void restore() {
		BlendModeStorage.restoreBlend();
	}

	public void apply() {
		BlendModeStorage.overrideBlend(this.blendMode);
	}
}

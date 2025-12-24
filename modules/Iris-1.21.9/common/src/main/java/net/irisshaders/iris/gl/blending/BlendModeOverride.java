package net.irisshaders.iris.gl.blending;

import net.iris.gl.blending.BlendMode;

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

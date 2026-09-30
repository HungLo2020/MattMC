package net.irisshaders.iris.gl.blending;

import org.jetbrains.annotations.Nullable;

public class BlendModeOverride {
	public static final BlendModeOverride OFF = new BlendModeOverride(null);

	private final BlendMode blendMode;

	public BlendModeOverride(BlendMode blendMode) {
		this.blendMode = blendMode;
	}

	@Nullable
	public BlendMode blendMode() {
		return this.blendMode;
	}
}

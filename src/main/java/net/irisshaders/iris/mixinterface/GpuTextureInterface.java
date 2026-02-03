package net.irisshaders.iris.mixinterface;

import net.blaze3d.textures.GpuTexture;

public interface GpuTextureInterface {
	default int iris$getGlId() {
		throw new AssertionError("Not accessible.");
	}

    default void iris$markMipmapNonLinear() {
		throw new AssertionError("Not accessible.");
	}

	default void iris$copyStateTo(GpuTexture texture) {
		throw new AssertionError("Not accessible.");
	}
}

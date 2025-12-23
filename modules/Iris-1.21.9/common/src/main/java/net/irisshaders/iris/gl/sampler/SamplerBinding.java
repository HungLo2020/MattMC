package net.irisshaders.iris.gl.sampler;

import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.state.ValueUpdateNotifier;
import net.irisshaders.iris.gl.texture.TextureType;

import java.util.function.IntSupplier;

public class SamplerBinding {
	private final int textureUnit;
	private final IntSupplier texture;
	private final ValueUpdateNotifier notifier;
	private final TextureType textureType;
	private final int sampler;

	public SamplerBinding(TextureType type, int textureUnit, IntSupplier texture, GlSampler sampler, ValueUpdateNotifier notifier) {
		this.textureType = type;
		this.textureUnit = textureUnit;
		this.texture = texture;
		this.sampler = sampler == null ? 0 : sampler.getId();
		this.notifier = notifier;
	}

	public void update() {
		updateSampler();

		if (notifier != null) {
			notifier.setListener(this::updateSampler);
		}
	}

	private void updateSampler() {
		int textureId = texture.getAsInt();
		// Skip binding if texture ID is invalid (-1 or 0)
		// This prevents GL_INVALID_OPERATION errors when DH textures aren't ready yet
		if (textureId <= 0) {
			return;
		}
		IrisRenderSystem.bindSamplerToUnit(textureUnit, sampler);
		IrisRenderSystem.bindTextureToUnit(textureType.getGlType(), textureUnit, textureId);
	}
}

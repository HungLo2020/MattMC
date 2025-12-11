// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.gl.sampler;

import net.minecraft.client.renderer.shaders.gl.IrisRenderSystem;
import net.minecraft.client.renderer.shaders.texture.TextureType;
import net.minecraft.client.renderer.shaders.uniform.ValueUpdateNotifier;

import java.util.function.IntSupplier;

/**
 * Represents a binding of a texture to a sampler uniform.
 * 
 * Based on IRIS's SamplerBinding class
 * Reference: frnsrc/Iris-1.21.9/.../gl/sampler/SamplerBinding.java
 */
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
		IrisRenderSystem.bindSamplerToUnit(textureUnit, sampler);
		IrisRenderSystem.bindTextureToUnit(textureType.getGlType(), textureUnit, texture.getAsInt());
	}
}

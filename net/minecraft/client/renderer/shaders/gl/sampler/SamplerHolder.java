// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.gl.sampler;

import net.minecraft.client.renderer.shaders.texture.TextureType;
import net.minecraft.client.renderer.shaders.uniform.ValueUpdateNotifier;

import java.util.function.IntSupplier;

/**
 * Interface for managing sampler bindings in shader programs.
 * 
 * Based on IRIS's SamplerHolder interface
 * Reference: frnsrc/Iris-1.21.9/.../gl/sampler/SamplerHolder.java
 */
public interface SamplerHolder {
	void addExternalSampler(int textureUnit, String... names);

	boolean hasSampler(String name);

	/**
	 * Like addDynamicSampler, but also ensures that any unrecognized / unbound samplers sample from this
	 * sampler.
	 * <p>
	 * Throws an exception if texture unit 0 is already allocated or reserved in some way. Do not call this
	 * function after calls to addDynamicSampler, it must be called before any calls to addDynamicSampler.
	 */
	default boolean addDefaultSampler(IntSupplier sampler, String... names) {
		return addDefaultSampler(TextureType.TEXTURE_2D, sampler, null, null, names);
	}

	boolean addDefaultSampler(TextureType type, IntSupplier texture, ValueUpdateNotifier notifier, GlSampler sampler, String... names);


	default boolean addDynamicSampler(IntSupplier texture, String... names) {
		return addDynamicSampler(TextureType.TEXTURE_2D, texture, null, names);
	}

	boolean addDynamicSampler(TextureType type, IntSupplier texture, GlSampler sampler, String... names);

	default boolean addDynamicSampler(IntSupplier texture, ValueUpdateNotifier notifier, String... names) {
		return addDynamicSampler(TextureType.TEXTURE_2D, texture, notifier, null, names);
	}

	boolean addDynamicSampler(TextureType type, IntSupplier texture, ValueUpdateNotifier notifier, GlSampler sampler, String... names);
}

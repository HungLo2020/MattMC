// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.samplers;

import com.google.common.collect.ImmutableSet;
import net.minecraft.client.renderer.shaders.gl.sampler.GlSampler;

/**
 * Sampler constants and utilities for Iris shader programs.
 * 
 * Based on IRIS's IrisSamplers class
 * Reference: frnsrc/Iris-1.21.9/.../samplers/IrisSamplers.java
 */
public class IrisSamplers {
	public static final int ALBEDO_TEXTURE_UNIT = 0;
	public static final int OVERLAY_TEXTURE_UNIT = 1;
	public static final int LIGHTMAP_TEXTURE_UNIT = 2;

	public static final ImmutableSet<Integer> WORLD_RESERVED_TEXTURE_UNITS = ImmutableSet.of(0, 1, 2);
	public static final ImmutableSet<Integer> SODIUM_RESERVED_TEXTURE_UNITS = ImmutableSet.of(0, 2);
	public static final ImmutableSet<Integer> COMPOSITE_RESERVED_TEXTURE_UNITS = ImmutableSet.of(1, 2);

	private static GlSampler SHADOW_SAMPLER_NEAREST;
	private static GlSampler SHADOW_SAMPLER_LINEAR;
	private static boolean initialized = false;

	private IrisSamplers() {
		// no construction allowed
	}

	public static void initRenderer() {
		if (initialized) return;
		
		SHADOW_SAMPLER_NEAREST = new GlSampler(false, false, true, true);
		SHADOW_SAMPLER_LINEAR = new GlSampler(true, false, true, true);
		initialized = true;
	}

	public static GlSampler getShadowSamplerNearest() {
		return SHADOW_SAMPLER_NEAREST;
	}

	public static GlSampler getShadowSamplerLinear() {
		return SHADOW_SAMPLER_LINEAR;
	}
}

// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.program;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.client.renderer.shaders.gl.IrisRenderSystem;
import net.minecraft.client.renderer.shaders.gl.sampler.GlSampler;
import net.minecraft.client.renderer.shaders.gl.sampler.SamplerBinding;
import net.minecraft.client.renderer.shaders.gl.sampler.SamplerHolder;
import net.minecraft.client.renderer.shaders.gl.sampler.SamplerLimits;
import net.minecraft.client.renderer.shaders.texture.TextureType;
import net.minecraft.client.renderer.shaders.uniform.ValueUpdateNotifier;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20C;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.IntSupplier;

/**
 * Manages sampler bindings for shader programs.
 * 
 * Based on IRIS's ProgramSamplers class
 * Reference: frnsrc/Iris-1.21.9/.../gl/program/ProgramSamplers.java
 */
public class ProgramSamplers {
	private static ProgramSamplers active;
	private final ImmutableList<SamplerBinding> samplerBindings;
	private final ImmutableList<ValueUpdateNotifier> notifiersToReset;
	private List<GlUniform1iCall> initializer;

	private ProgramSamplers(ImmutableList<SamplerBinding> samplerBindings, ImmutableList<ValueUpdateNotifier> notifiersToReset, List<GlUniform1iCall> initializer) {
		this.samplerBindings = samplerBindings;
		this.notifiersToReset = notifiersToReset;
		this.initializer = initializer;
	}

	public static void clearActiveSamplers() {
		if (active != null) {
			active.removeListeners();
		}

		IrisRenderSystem.unbindAllSamplers();
	}

	public static Builder builder(int program, Set<Integer> reservedTextureUnits) {
		return new Builder(program, reservedTextureUnits);
	}

	public void update() {
		if (active != null) {
			active.removeListeners();
		}

		active = this;

		if (initializer != null) {
			for (GlUniform1iCall call : initializer) {
				GlStateManager._glUniform1i(call.location(), call.value());
			}

			initializer = null;
		}

		// We need to keep the active texture intact, since if we mess it up
		// in the middle of RenderType setup, bad things will happen.
		// Note: We track our own active texture unit since GlStateManager doesn't expose it publicly

		for (SamplerBinding samplerBinding : samplerBindings) {
			samplerBinding.update();
		}

		// Restore texture unit 0 as default
		GlStateManager._activeTexture(GL13.GL_TEXTURE0);
	}

	public void removeListeners() {
		active = null;

		for (ValueUpdateNotifier notifier : notifiersToReset) {
			notifier.setListener(null);
		}
	}

	/**
	 * Simple record for storing uniform1i calls to be executed later.
	 */
	public record GlUniform1iCall(int location, int value) {}

	public static final class Builder implements SamplerHolder {
		private final int program;
		private final ImmutableSet<Integer> reservedTextureUnits;
		private final ImmutableList.Builder<SamplerBinding> samplers;
		private final ImmutableList.Builder<ValueUpdateNotifier> notifiersToReset;
		private final List<GlUniform1iCall> calls;
		private int remainingUnits;
		private int nextUnit;

		private Builder(int program, Set<Integer> reservedTextureUnits) {
			this.program = program;
			this.reservedTextureUnits = ImmutableSet.copyOf(reservedTextureUnits);
			this.samplers = ImmutableList.builder();
			this.notifiersToReset = ImmutableList.builder();
			this.calls = new ArrayList<>();

			int maxTextureUnits = SamplerLimits.get().getMaxTextureUnits();

			for (int unit : reservedTextureUnits) {
				if (unit >= maxTextureUnits) {
					throw new IllegalStateException("Cannot mark texture unit " + unit + " as reserved because that " +
						"texture unit isn't available on this system! Only " + maxTextureUnits +
						" texture units are available.");
				}
			}

			this.remainingUnits = maxTextureUnits - reservedTextureUnits.size();

			while (reservedTextureUnits.contains(nextUnit)) {
				this.nextUnit++;
			}
		}

		@Override
		public void addExternalSampler(int textureUnit, String... names) {
			if (!reservedTextureUnits.contains(textureUnit)) {
				throw new IllegalArgumentException("Cannot add an externally-managed sampler for texture unit " +
					textureUnit + " since it isn't in the set of reserved texture units.");
			}

			for (String name : names) {
				int location = GlStateManager._glGetUniformLocation(program, name);

				if (location == -1) {
					// There's no active sampler with this particular name in the program.
					continue;
				}

				// Set up this sampler uniform to use this particular texture unit.
				calls.add(new GlUniform1iCall(location, textureUnit));
			}
		}

		@Override
		public boolean hasSampler(String name) {
			return GlStateManager._glGetUniformLocation(program, name) != -1;
		}

		@Override
		public boolean addDefaultSampler(TextureType type, IntSupplier texture, ValueUpdateNotifier notifier, GlSampler sampler, String... names) {
			if (nextUnit != 0) {
				throw new IllegalStateException("Texture unit 0 is already used.");
			}

			return addDynamicSampler(TextureType.TEXTURE_2D, texture, sampler, true, notifier, names);
		}

		@Override
		public boolean addDynamicSampler(TextureType type, IntSupplier texture, GlSampler sampler, String... names) {
			return addDynamicSampler(type, texture, sampler, false, null, names);
		}

		@Override
		public boolean addDynamicSampler(TextureType type, IntSupplier texture, ValueUpdateNotifier notifier, GlSampler sampler, String... names) {
			return addDynamicSampler(type, texture, sampler, false, notifier, names);
		}

		private boolean addDynamicSampler(TextureType type, IntSupplier texture, GlSampler sampler, boolean used, ValueUpdateNotifier notifier, String... names) {
			if (notifier != null) {
				notifiersToReset.add(notifier);
			}

			for (String name : names) {
				int location = GlStateManager._glGetUniformLocation(program, name);

				if (location == -1) {
					// There's no active sampler with this particular name in the program.
					continue;
				}

				// Make sure that we aren't out of texture units.
				if (remainingUnits <= 0) {
					throw new IllegalStateException("No more available texture units while activating sampler " + name);
				}

				// Set up this sampler uniform to use this particular texture unit.
				calls.add(new GlUniform1iCall(location, nextUnit));

				// And mark this texture unit as used.
				used = true;
			}

			if (!used) {
				return false;
			}

			samplers.add(new SamplerBinding(type, nextUnit, texture, sampler, notifier));

			remainingUnits--;
			nextUnit++;

			while (remainingUnits > 0 && reservedTextureUnits.contains(nextUnit)) {
				nextUnit += 1;
			}

			return true;
		}

		public ProgramSamplers build() {
			return new ProgramSamplers(samplers.build(), notifiersToReset.build(), calls);
		}
	}
}

package net.irisshaders.iris.gl.program;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.blaze3d.opengl.GlRenderPass;
import net.blaze3d.systems.RenderPass;
import net.blaze3d.textures.GpuTextureView;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.sampler.GlSampler;
import net.irisshaders.iris.gl.sampler.SamplerBinding;
import net.irisshaders.iris.gl.sampler.SamplerHolder;
import net.irisshaders.iris.gl.sampler.SamplerLimits;
import net.irisshaders.iris.gl.state.ValueUpdateNotifier;
import net.irisshaders.iris.gl.texture.TextureAccess;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.pbr.TextureTracker;
import net.irisshaders.iris.shaderpack.properties.PackRenderTargetDirectives;
import net.vulkanic.VulkanicAPI;
import javax.annotation.Nonnull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.IntSupplier;

public class ProgramSamplers {
	private static ProgramSamplers active;
	private final ImmutableList<SamplerBinding> samplerBindings;
	private final ImmutableList<NamedSamplerBinding> namedSamplerBindings;
	private final ImmutableList<ValueUpdateNotifier> notifiersToReset;
	private List<GlUniform1iCall> initializer;

	private ProgramSamplers(
		ImmutableList<SamplerBinding> samplerBindings,
		ImmutableList<NamedSamplerBinding> namedSamplerBindings,
		ImmutableList<ValueUpdateNotifier> notifiersToReset,
		List<GlUniform1iCall> initializer
	) {
		this.samplerBindings = samplerBindings;
		this.namedSamplerBindings = namedSamplerBindings;
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

	public static CustomTextureSamplerInterceptor customTextureSamplerInterceptor(SamplerHolder samplerHolder, Object2ObjectMap<String, TextureAccess> customTextureIds) {
		return customTextureSamplerInterceptor(samplerHolder, customTextureIds, ImmutableSet.of());
	}

	public static CustomTextureSamplerInterceptor customTextureSamplerInterceptor(SamplerHolder samplerHolder, Object2ObjectMap<String, TextureAccess> customTextureIds, ImmutableSet<Integer> flippedAtLeastOnceSnapshot) {
		return new CustomTextureSamplerInterceptor(samplerHolder, customTextureIds, flippedAtLeastOnceSnapshot);
	}

	public void update() {
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			// Rust Vulkan receives copied semantic sampler bindings; Java/Iris sampler
			// initialization must not mutate or inspect compatibility texture units.
			return;
		}
		if (active != null) {
			active.removeListeners();
		}

		active = this;

		if (initializer != null) {
			for (GlUniform1iCall call : initializer) {
				VulkanicAPI.setUniform1i(VulkanicAPI.getCommandContext(), call.location(), call.value());
			}

			initializer = null;
		}

		// We need to keep the active texture intact, since if we mess it up
		// in the middle of RenderType setup, bad things will happen.
		int activeTexture = IrisRenderSystem.getActiveTextureUnitIndex();

		for (SamplerBinding samplerBinding : samplerBindings) {
			samplerBinding.update();
		}

		net.irisshaders.iris.gl.IrisRenderSystem.setActiveTextureUnitIndex(activeTexture);
	}

	public void removeListeners() {
		active = null;

		for (ValueUpdateNotifier notifier : notifiersToReset) {
			notifier.setListener(null);
		}
	}

	@SuppressWarnings("null")
	public ImmutableList<String> getRenderPassSamplerNames() {
		ImmutableList.Builder<String> names = ImmutableList.builder();
		for (NamedSamplerBinding binding : namedSamplerBindings) {
			names.add(Objects.requireNonNull(binding.name(), "sampler name"));
		}
		return names.build();
	}

	public OptionalInt getRenderPassSamplerUnit(String samplerName) {
		for (NamedSamplerBinding binding : namedSamplerBindings) {
			if (binding.name().equals(samplerName)) {
				return OptionalInt.of(binding.textureUnit());
			}
		}

		return OptionalInt.empty();
	}

	@SuppressWarnings("null")
	public java.util.Map<String, Integer> getRenderPassSamplerUnits() {
		java.util.Map<String, Integer> units = new LinkedHashMap<>();
		for (NamedSamplerBinding binding : namedSamplerBindings) {
			units.put(Objects.requireNonNull(binding.name(), "sampler name"), binding.textureUnit());
		}
		return java.util.Map.copyOf(units);
	}

	@SuppressWarnings("null")
	public void bindToRenderPass(RenderPass renderPass) {
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			return;
		}
		for (NamedSamplerBinding binding : namedSamplerBindings) {
			int suppliedTextureId = binding.texture() != null ? binding.texture().getAsInt() : 0;
			int textureId = suppliedTextureId;
			if (textureId <= 0) {
				textureId = IrisRenderSystem.getTextureBinding(binding.textureUnit());
			}

			GpuTextureView textureView = textureId > 0 ? TextureTracker.INSTANCE.getTextureView(textureId) : null;
			if (textureView == null && suppliedTextureId <= 0) {
				textureView = TextureTracker.INSTANCE.getShaderTexture(binding.textureUnit());
				if (textureView != null) {
					textureId = VulkanicAPI.isVulkanBackendSelected() ? net.vulkanic.VulkanicCoreAPI.textureId(textureView) : textureId;
				}
			}

			if (textureView != null) {
				String name = Objects.requireNonNull(binding.name(), "sampler name");
				int diagnosticTextureId = textureId > 0
					? textureId
					: net.vulkanic.VulkanicCoreAPI.textureId(textureView);
				VulkanicAPI.traceScopedCompositeColortex0SamplerBinding(
					renderPass,
					name,
					binding.textureUnit(),
					diagnosticTextureId,
					"program-samplers-texture-view"
				);
				if (renderPass instanceof net.vulkanic.RenderPassResourceBinder resourceBinder) {
					resourceBinder.bindSampler(
						name,
						Objects.requireNonNull(textureView, "sampler texture view"),
						binding.textureUnit()
					);
				} else {
					renderPass.bindSampler(name, Objects.requireNonNull(textureView, "sampler texture view"));
				}
				continue;
			}

			if (textureId > 0 && VulkanicAPI.isVulkanBackendSelected()) {
				String name = Objects.requireNonNull(binding.name(), "sampler name");
				VulkanicAPI.traceScopedCompositeColortex0SamplerBinding(
					renderPass,
					name,
					binding.textureUnit(),
					textureId,
					"program-samplers-vulkan-legacy"
				);
				if (renderPass instanceof GlRenderPass glRenderPass) {
					glRenderPass.bindLegacySampler(name, textureId);
				} else if (renderPass instanceof net.vulkanic.RenderPassResourceBinder resourceBinder) {
					resourceBinder.bindLegacySampler(name, textureId, binding.textureUnit());
				}
			}
			if (textureId > 0 && renderPass instanceof GlRenderPass glRenderPass) {
				String name = Objects.requireNonNull(binding.name(), "sampler name");
				VulkanicAPI.traceScopedCompositeColortex0SamplerBinding(
					renderPass,
					name,
					binding.textureUnit(),
					textureId,
					"program-samplers-opengl-legacy"
				);
				glRenderPass.bindLegacySampler(name, textureId);
			}
		}
	}

	public static final class Builder implements SamplerHolder {
		private final int program;
		private final ImmutableSet<Integer> reservedTextureUnits;
		private final ImmutableList.Builder<SamplerBinding> samplers;
		private final ImmutableList.Builder<NamedSamplerBinding> namedSamplers;
		private final ImmutableList.Builder<ValueUpdateNotifier> notifiersToReset;
		private final List<GlUniform1iCall> calls;
		private int remainingUnits;
		private int nextUnit;

		@SuppressWarnings("null")
		private Builder(int program, Set<Integer> reservedTextureUnits) {
			this.program = program;
			this.reservedTextureUnits = ImmutableSet.copyOf(Objects.requireNonNull(reservedTextureUnits, "reservedTextureUnits"));
			this.samplers = ImmutableList.builder();
			this.namedSamplers = ImmutableList.builder();
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

			//System.out.println("Begin building samplers. Reserved texture units are " + reservedTextureUnits +
			//		", next texture unit is " + nextUnit + ", there are " + remainingUnits + " units remaining.");
		}

		@Override
		public void addExternalSampler(int textureUnit, String... names) {
			if (!reservedTextureUnits.contains(textureUnit)) {
				throw new IllegalArgumentException("Cannot add an externally-managed sampler for texture unit " +
					textureUnit + " since it isn't in the set of reserved texture units.");
			}

			for (String name : names) {
				String samplerName = java.util.Objects.requireNonNull(name, "sampler name");
				int location = VulkanicAPI.getUniformLocationWithLegacySamplerFallback(VulkanicAPI.getCommandContext(), program, samplerName);

				if (location == -1) {
					// There's no active sampler with this particular name in the program.
					continue;
				}

				// Set up this sampler uniform to use this particular texture unit.
				//System.out.println("Binding external sampler " + name + " to texture unit " + textureUnit);
				calls.add(new GlUniform1iCall(location, textureUnit));
				namedSamplers.add(new NamedSamplerBinding(samplerName, textureUnit, null));
			}
		}

		@Override
		public boolean hasSampler(String name) {
			return VulkanicAPI.getUniformLocationWithLegacySamplerFallback(VulkanicAPI.getCommandContext(), program, name) != -1;
		}

		@Override
		public boolean addDefaultSampler(TextureType type, IntSupplier texture, ValueUpdateNotifier notifier, GlSampler sampler, String... names) {
			if (nextUnit != 0) {
				// TODO: Relax this restriction!
				throw new IllegalStateException("Texture unit 0 is already used.");
			}

			return addDynamicSampler(TextureType.TEXTURE_2D, texture, sampler, true, notifier, names);
		}

		/**
		 * Adds a sampler
		 *
		 * @return false if this sampler is not active, true if at least one of the names referred to an active sampler
		 */
		@Override
		public boolean addDynamicSampler(TextureType type, IntSupplier texture, GlSampler sampler, String... names) {
			return addDynamicSampler(type, texture, sampler, false, null, names);
		}

		@Override
		public boolean addDynamicSampler(TextureType type, IntSupplier texture, ValueUpdateNotifier notifier, GlSampler sampler, String... names) {
			return addDynamicSampler(type, texture, sampler, false, notifier, names);
		}

		/**
		 * Adds a sampler
		 *
		 * @return false if this sampler is not active, true if at least one of the names referred to an active sampler
		 */
		private boolean addDynamicSampler(TextureType type, IntSupplier texture, GlSampler sampler, boolean used, ValueUpdateNotifier notifier, String... names) {
			if (notifier != null) {
				notifiersToReset.add(notifier);
			}

			for (String name : names) {
				String samplerName = java.util.Objects.requireNonNull(name, "sampler name");
				int location = VulkanicAPI.getUniformLocationWithLegacySamplerFallback(VulkanicAPI.getCommandContext(), program, samplerName);

				if (location == -1) {
					// There's no active sampler with this particular name in the program.
					continue;
				}

				// Make sure that we aren't out of texture units.
				if (remainingUnits <= 0) {
					throw new IllegalStateException("No more available texture units while activating sampler " + samplerName);
				}

				//System.out.println("Binding dynamic sampler " + samplerName + " with type " + type.name() + " to texture unit " + nextUnit);

				// Set up this sampler uniform to use this particular texture unit.
				calls.add(new GlUniform1iCall(location, nextUnit));
				namedSamplers.add(new NamedSamplerBinding(samplerName, nextUnit, texture));

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

			//System.out.println("The next unit is " + nextUnit + ", there are " + remainingUnits + " units remaining.");

			return true;
		}

		public ProgramSamplers build() {
			return new ProgramSamplers(samplers.build(), namedSamplers.build(), notifiersToReset.build(), calls);
		}
	}

	private record NamedSamplerBinding(@Nonnull String name, int textureUnit, IntSupplier texture) {
	}

	public static final class CustomTextureSamplerInterceptor implements SamplerHolder {
		private final SamplerHolder samplerHolder;
		private final Object2ObjectMap<String, TextureAccess> customTextureIds;
		private final ImmutableSet<String> deactivatedOverrides;

		@SuppressWarnings("null")
		private CustomTextureSamplerInterceptor(SamplerHolder samplerHolder, Object2ObjectMap<String, TextureAccess> customTextureIds, ImmutableSet<Integer> flippedAtLeastOnceSnapshot) {
			this.samplerHolder = samplerHolder;
			this.customTextureIds = customTextureIds;

			ImmutableSet.Builder<String> deactivatedOverrides = new ImmutableSet.Builder<>();

			for (int deactivatedOverride : flippedAtLeastOnceSnapshot) {
				deactivatedOverrides.add("colortex" + deactivatedOverride);

				if (deactivatedOverride < PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.size()) {
					deactivatedOverrides.add(Objects.requireNonNull(
						PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.get(deactivatedOverride),
						"legacy render target"
					));
				}
			}

			this.deactivatedOverrides = deactivatedOverrides.build();
		}

		private IntSupplier getOverride(IntSupplier existing, String... names) {
			for (String name : names) {
				if (customTextureIds.containsKey(name) && !deactivatedOverrides.contains(name)) {
					return customTextureIds.get(name).getTextureId();
				}
			}

			return existing;
		}

		@Override
		public void addExternalSampler(int textureUnit, String... names) {
			IntSupplier override = getOverride(null, names);

			if (override != null) {
				if (textureUnit == 0) {
					samplerHolder.addDefaultSampler(override, names);
				} else {
					samplerHolder.addDynamicSampler(override, names);
				}
			} else {
				samplerHolder.addExternalSampler(textureUnit, names);
			}
		}

		@Override
		public boolean hasSampler(String name) {
			return samplerHolder.hasSampler(name);
		}

		@Override
		public boolean addDefaultSampler(IntSupplier sampler, String... names) {
			sampler = getOverride(sampler, names);

			return samplerHolder.addDefaultSampler(sampler, names);
		}

		@Override
		public boolean addDefaultSampler(TextureType type, IntSupplier texture, ValueUpdateNotifier notifier, GlSampler sampler, String... names) {
			texture = getOverride(texture, names);

			return samplerHolder.addDefaultSampler(type, texture, notifier, sampler, names);
		}

		@Override
		public boolean addDynamicSampler(IntSupplier sampler, String... names) {
			sampler = getOverride(sampler, names);

			return samplerHolder.addDynamicSampler(sampler, names);
		}

		@Override
		public boolean addDynamicSampler(TextureType type, IntSupplier texture, GlSampler sampler, String... names) {
			texture = getOverride(texture, names);

			return samplerHolder.addDynamicSampler(type, texture, sampler, names);
		}

		@Override
		public boolean addDynamicSampler(IntSupplier sampler, ValueUpdateNotifier notifier, String... names) {
			sampler = getOverride(sampler, names);

			return samplerHolder.addDynamicSampler(sampler, notifier, names);
		}

		@Override
		public boolean addDynamicSampler(TextureType type, IntSupplier texture, ValueUpdateNotifier notifier, GlSampler sampler, String... names) {
			texture = getOverride(texture, names);

			return samplerHolder.addDynamicSampler(type, texture, notifier, sampler, names);
		}
	}
}

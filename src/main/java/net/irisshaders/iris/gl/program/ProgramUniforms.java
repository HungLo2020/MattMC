package net.irisshaders.iris.gl.program;

import com.google.common.collect.ImmutableList;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.state.ValueUpdateNotifier;
import net.irisshaders.iris.gl.uniform.DynamicLocationalUniformHolder;
import net.irisshaders.iris.gl.uniform.Uniform;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformType;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.Minecraft;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicUniformReflectionType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

public class ProgramUniforms {
	private static ProgramUniforms active;
	private final ImmutableList<Uniform> perTick;
	private final ImmutableList<Uniform> perFrame;
	private final ImmutableList<Uniform> dynamic;
	private final ImmutableList<ValueUpdateNotifier> notifiersToReset;
	long lastTick = -1;
	int lastFrame = -1;
	private ImmutableList<Uniform> once;

	public ProgramUniforms(ImmutableList<Uniform> once, ImmutableList<Uniform> perTick, ImmutableList<Uniform> perFrame,
						   ImmutableList<Uniform> dynamic, ImmutableList<ValueUpdateNotifier> notifiersToReset) {
		this.once = once;
		this.perTick = perTick;
		this.perFrame = perFrame;
		this.dynamic = dynamic;
		this.notifiersToReset = notifiersToReset;
	}

	private static long getCurrentTick() {
		if (Minecraft.getInstance().level == null) {
			return 0L;
		} else {
			return Minecraft.getInstance().level.getGameTime();
		}
	}

	public static void clearActiveUniforms() {
		if (active != null) {
			active.removeListeners();
		}
	}

	public static Builder builder(String name, int program) {
		return new Builder(name, program);
	}

	private static String getTypeName(VulkanicAPI.ActiveUniformInfo activeUniformInfo) {
		return activeUniformInfo.reflectionTypeName();
	}

	private static UniformType getExpectedType(VulkanicAPI.ActiveUniformInfo activeUniformInfo) {
		return activeUniformInfo.reflectionType()
			.map(ProgramUniforms::getExpectedType)
			.orElse(null);
	}

	private static UniformType getExpectedType(VulkanicUniformReflectionType type) {
		if (type.isSampler()) {
			return UniformType.INT;
		}

		if (type.isImage()) {
			return null;
		}

		return switch (type) {
			case FLOAT -> UniformType.FLOAT;
			case INT, UINT, BOOL -> UniformType.INT;
			case FLOAT_MAT4 -> UniformType.MAT4;
			case FLOAT_VEC4 -> UniformType.VEC4;
			case INT_VEC4, UINT_VEC4, BOOL_VEC4 -> UniformType.VEC4I;
			case FLOAT_MAT3 -> UniformType.MAT3;
			case FLOAT_VEC3 -> UniformType.VEC3;
			case INT_VEC3, UINT_VEC3, BOOL_VEC3 -> UniformType.VEC3I;
			case FLOAT_MAT2 -> null;
			case FLOAT_VEC2 -> UniformType.VEC2;
			case INT_VEC2, UINT_VEC2, BOOL_VEC2 -> UniformType.VEC2I;
			default -> null;
		};
	}

	private void updateStage(ImmutableList<Uniform> uniforms) {
		for (Uniform uniform : uniforms) {
			uniform.update();
		}
	}

	public void update() {
		if (active != null) {
			active.removeListeners();
		}

		active = this;

		updateStage(dynamic);

		if (once != null) {
			updateStage(once);
			updateStage(perTick);
			updateStage(perFrame);
			lastTick = getCurrentTick();

			once = null;
			return;
		}

		long currentTick = getCurrentTick();

		if (lastTick != currentTick) {
			lastTick = currentTick;

			updateStage(perTick);
		}

		// TODO: Move the frame counter to a different place?
		int currentFrame = SystemTimeUniforms.COUNTER.getAsInt();

		if (lastFrame != currentFrame) {
			lastFrame = currentFrame;

			updateStage(perFrame);
		}
	}

	public void removeListeners() {
		active = null;

		for (ValueUpdateNotifier notifier : notifiersToReset) {
			notifier.setListener(null);
		}
	}

	public static class Builder implements DynamicLocationalUniformHolder {
		private final String name;
		private final int program;

		private final Map<Integer, String> locations;
		private final Map<String, Uniform> once;
		private final Map<String, Uniform> perTick;
		private final Map<String, Uniform> perFrame;
		private final Map<String, Uniform> dynamic;
		private final Map<String, UniformType> uniformNames;
		private final Map<String, UniformType> externalUniformNames;
		private final List<ValueUpdateNotifier> notifiersToReset;

		protected Builder(String name, int program) {
			this.name = name;
			this.program = program;

			locations = new HashMap<>();
			once = new HashMap<>();
			perTick = new HashMap<>();
			perFrame = new HashMap<>();
			dynamic = new HashMap<>();
			uniformNames = new HashMap<>();
			externalUniformNames = new HashMap<>();
			notifiersToReset = new ArrayList<>();
		}

		@Override
		public Builder addUniform(UniformUpdateFrequency updateFrequency, Uniform uniform) {
			Objects.requireNonNull(uniform);

			switch (updateFrequency) {
				case ONCE:
					once.put(locations.get(uniform.getLocation()), uniform);
					break;
				case PER_TICK:
					perTick.put(locations.get(uniform.getLocation()), uniform);
					break;
				case PER_FRAME:
					perFrame.put(locations.get(uniform.getLocation()), uniform);
					break;
			}

			return this;
		}

		@Override
		public OptionalInt location(String name, UniformType type) {
			int id = VulkanicAPI.getUniformLocationWithLegacySamplerFallback(VulkanicAPI.getCommandContext(), program, name);

			if (id == -1) {
				return OptionalInt.empty();
			}

			if ((!locations.containsKey(id) && !uniformNames.containsKey(name))) {
				locations.put(id, name);
				uniformNames.put(name, type);
			} else {
				Iris.logger.warn("[" + this.name + "] Duplicate uniform: " + type.toString().toLowerCase() + " " + name);

				return OptionalInt.empty();
			}

			return OptionalInt.of(id);
		}

			public ProgramUniforms buildUniforms() {
				VulkanicAPI.registerShaderInputParityProgramName(program, name);

				// Check for any unsupported uniforms and warn about them so that we can easily figure out what uniforms we
				// need to add.
				for (VulkanicAPI.ActiveUniformInfo activeUniformInfo : VulkanicAPI.getActiveUniforms(VulkanicAPI.getCommandContext(), program, 128)) {
				String name = activeUniformInfo.name();

				if (name.isEmpty()) {
					// No further information available.
					continue;
				}

				UniformType provided = uniformNames.get(name);
				UniformType expected = getExpectedType(activeUniformInfo);

				if (provided != null && provided != expected) {
					String expectedName;

					if (expected != null) {
						expectedName = expected.toString();
					} else {
						expectedName = "(unsupported type: " + getTypeName(activeUniformInfo) + ")";
					}

					Iris.logger.error("[" + this.name + "] Wrong uniform type for " + name + ": Iris is providing " + provided + " but the program expects " + expectedName + ". Disabling that uniform.");

					once.remove(name);
					perTick.remove(name);
					perFrame.remove(name);
					dynamic.remove(name);
				}
			}

			return new ProgramUniforms(ImmutableList.copyOf(once.values()), ImmutableList.copyOf(perTick.values()), ImmutableList.copyOf(perFrame.values()),
				ImmutableList.copyOf(dynamic.values()), ImmutableList.copyOf(notifiersToReset));
		}

		@Override
		public Builder addDynamicUniform(Uniform uniform, ValueUpdateNotifier notifier) {
			Objects.requireNonNull(uniform);
			Objects.requireNonNull(notifier);

			dynamic.put(locations.get(uniform.getLocation()), uniform);
			notifiersToReset.add(notifier);

			return this;
		}

		@Override
		public UniformHolder externallyManagedUniform(String name, UniformType type) {
			externalUniformNames.put(name, type);

			return this;
		}
	}
}

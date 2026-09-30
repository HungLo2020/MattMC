package net.minecraft.client.renderer;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public record ShaderDefines(Map<String, String> values, Set<String> flags) {
	public static final ShaderDefines EMPTY = new ShaderDefines(Map.of(), Set.of());
	public static final Codec<ShaderDefines> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
				Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("values", Map.of()).forGetter(ShaderDefines::values),
				Codec.STRING.listOf().<Set<String>>xmap(Set::copyOf, List::copyOf).optionalFieldOf("flags", Set.of()).forGetter(shaderDefines -> shaderDefines.flags())
			)
			.apply(instance, ShaderDefines::new)
	);

	public static ShaderDefines.Builder builder() {
		return new ShaderDefines.Builder();
	}

	public boolean isEmpty() {
		return this.values.isEmpty() && this.flags.isEmpty();
	}

	@Environment(EnvType.CLIENT)
	public static class Builder {
		private final ImmutableMap.Builder<String, String> values = ImmutableMap.builder();
		private final ImmutableSet.Builder<String> flags = ImmutableSet.builder();

		Builder() {
		}

		public ShaderDefines.Builder define(String string, String string2) {
			if (string2.isBlank()) {
				throw new IllegalArgumentException("Cannot define empty string");
			} else {
				this.values.put(string, escapeNewLines(string2));
				return this;
			}
		}

		private static String escapeNewLines(String string) {
			return string.replaceAll("\n", "\\\\\n");
		}

		public ShaderDefines.Builder define(String string, float f) {
			this.values.put(string, String.valueOf(f));
			return this;
		}

		public ShaderDefines.Builder define(String string, int i) {
			this.values.put(string, String.valueOf(i));
			return this;
		}

		public ShaderDefines.Builder define(String string) {
			this.flags.add(string);
			return this;
		}

		public ShaderDefines build() {
			return new ShaderDefines(this.values.build(), this.flags.build());
		}
	}
}

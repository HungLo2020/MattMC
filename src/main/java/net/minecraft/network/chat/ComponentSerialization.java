package net.minecraft.network.chat;

import net.minecraft.resources.NativeValues;
import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.GsonHelper;

public class ComponentSerialization {
	/** Rust decodes, validates and encodes components (`content/data/text.rs`). */
	public static final NativeValues.ValueCodec<Component> CODEC = NativeValues.codec(NativeValues.Kind.COMPONENT, NativeText::project, NativeText::unproject);
	public static final StreamCodec<RegistryFriendlyByteBuf, Component> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);
	public static final StreamCodec<RegistryFriendlyByteBuf, Optional<Component>> OPTIONAL_STREAM_CODEC = STREAM_CODEC.apply(ByteBufCodecs::optional);
	public static final StreamCodec<RegistryFriendlyByteBuf, Component> TRUSTED_STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistriesTrusted(CODEC);
	public static final StreamCodec<RegistryFriendlyByteBuf, Optional<Component>> TRUSTED_OPTIONAL_STREAM_CODEC = TRUSTED_STREAM_CODEC.apply(
		ByteBufCodecs::optional
	);
	public static final StreamCodec<ByteBuf, Component> TRUSTED_CONTEXT_FREE_STREAM_CODEC = ByteBufCodecs.fromCodecTrusted(CODEC);

	public static Codec<Component> flatRestrictedCodec(int i) {
		return new Codec<Component>() {
			@Override
			public <T> DataResult<Pair<Component, T>> decode(DynamicOps<T> dynamicOps, T object) {
				return ComponentSerialization.CODEC
					.decode(dynamicOps, object)
					.flatMap(
						pair -> this.isTooLarge(dynamicOps, (Component)pair.getFirst())
							? DataResult.error(() -> "Component was too large: greater than max size " + i)
							: DataResult.success(pair)
					);
			}

			public <T> DataResult<T> encode(Component component, DynamicOps<T> dynamicOps, T object) {
				return ComponentSerialization.CODEC.encodeStart(dynamicOps, component);
			}

			private <T> boolean isTooLarge(DynamicOps<T> dynamicOps, Component component) {
				DataResult<JsonElement> dataResult = ComponentSerialization.CODEC.encodeStart(asJsonOps(dynamicOps), component);
				return dataResult.isSuccess() && GsonHelper.encodesLongerThan(dataResult.getOrThrow(), i);
			}

			private static <T> DynamicOps<JsonElement> asJsonOps(DynamicOps<T> dynamicOps) {
				return (DynamicOps<JsonElement>)(dynamicOps instanceof RegistryOps<T> registryOps ? registryOps.withParent(JsonOps.INSTANCE) : JsonOps.INSTANCE);
			}
		};
	}

}

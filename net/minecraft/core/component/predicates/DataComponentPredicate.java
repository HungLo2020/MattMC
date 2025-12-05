package net.minecraft.core.component.predicates;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public interface DataComponentPredicate {
	Codec<Map<DataComponentPredicate.Type<?>, DataComponentPredicate>> CODEC = Codec.dispatchedMap(
		BuiltInRegistries.DATA_COMPONENT_PREDICATE_TYPE.byNameCodec(), DataComponentPredicate.Type::codec
	);
	StreamCodec<RegistryFriendlyByteBuf, DataComponentPredicate.Single<?>> SINGLE_STREAM_CODEC = ByteBufCodecs.registry(Registries.DATA_COMPONENT_PREDICATE_TYPE)
		.dispatch(DataComponentPredicate.Single::type, DataComponentPredicate.Type::singleStreamCodec);
	StreamCodec<RegistryFriendlyByteBuf, Map<DataComponentPredicate.Type<?>, DataComponentPredicate>> STREAM_CODEC = SINGLE_STREAM_CODEC.apply(
			ByteBufCodecs.list(64)
		)
		.map(
			list -> (Map)list.stream().collect(Collectors.toMap(DataComponentPredicate.Single::type, DataComponentPredicate.Single::predicate)),
			map -> map.entrySet().stream().map(DataComponentPredicate.Single::fromEntry).toList()
		);

	static MapCodec<DataComponentPredicate.Single<?>> singleCodec(String string) {
		return BuiltInRegistries.DATA_COMPONENT_PREDICATE_TYPE
			.byNameCodec()
			.dispatchMap(string, DataComponentPredicate.Single::type, DataComponentPredicate.Type::wrappedCodec);
	}

	boolean matches(DataComponentGetter dataComponentGetter);

	public record Single<T extends DataComponentPredicate>(DataComponentPredicate.Type<T> type, T predicate) {
		private static <T extends DataComponentPredicate> DataComponentPredicate.Single<T> fromEntry(Entry<DataComponentPredicate.Type<?>, T> entry) {
			return new DataComponentPredicate.Single<>((DataComponentPredicate.Type<T>)entry.getKey(), (T)entry.getValue());
		}
	}

	public static final class Type<T extends DataComponentPredicate> {
		private final Codec<T> codec;
		private final MapCodec<DataComponentPredicate.Single<T>> wrappedCodec;
		private final StreamCodec<RegistryFriendlyByteBuf, DataComponentPredicate.Single<T>> singleStreamCodec;

		public Type(Codec<T> codec) {
			this.codec = codec;
			this.wrappedCodec = RecordCodecBuilder.mapCodec(
				instance -> instance.group(codec.fieldOf("value").forGetter(DataComponentPredicate.Single::predicate))
					.apply(instance, dataComponentPredicate -> new DataComponentPredicate.Single<>(this, (T)dataComponentPredicate))
			);
			this.singleStreamCodec = ByteBufCodecs.fromCodecWithRegistries(codec)
				.map(dataComponentPredicate -> new DataComponentPredicate.Single<>(this, (T)dataComponentPredicate), DataComponentPredicate.Single::predicate);
		}

		public Codec<T> codec() {
			return this.codec;
		}

		public MapCodec<DataComponentPredicate.Single<T>> wrappedCodec() {
			return this.wrappedCodec;
		}

		public StreamCodec<RegistryFriendlyByteBuf, DataComponentPredicate.Single<T>> singleStreamCodec() {
			return this.singleStreamCodec;
		}
	}
}

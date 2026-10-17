package net.minecraft.core.component;

import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Unit;
import org.jetbrains.annotations.Nullable;

public final class DataComponentPatch {
	public static final DataComponentPatch EMPTY = new DataComponentPatch(Reference2ObjectMaps.emptyMap());
	/** Rust decodes, validates and encodes patches (`content/data/items.rs`). */
	public static final Codec<DataComponentPatch> CODEC = net.minecraft.resources.NativeValues.codec(
		net.minecraft.resources.NativeValues.Kind.COMPONENT_PATCH, net.minecraft.world.item.NativeItemStacks::patch, net.minecraft.world.item.NativeItemStacks::patch
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, DataComponentPatch> STREAM_CODEC = createStreamCodec(new DataComponentPatch.CodecGetter() {
		@Override
		public <T> StreamCodec<RegistryFriendlyByteBuf, T> apply(DataComponentType<T> dataComponentType) {
			return dataComponentType.streamCodec().cast();
		}
	});
	public static final StreamCodec<RegistryFriendlyByteBuf, DataComponentPatch> DELIMITED_STREAM_CODEC = createStreamCodec(new DataComponentPatch.CodecGetter() {
		@Override
		public <T> StreamCodec<RegistryFriendlyByteBuf, T> apply(DataComponentType<T> dataComponentType) {
			StreamCodec<RegistryFriendlyByteBuf, T> streamCodec = dataComponentType.streamCodec().cast();
			return streamCodec.apply(ByteBufCodecs.registryFriendlyLengthPrefixed(Integer.MAX_VALUE));
		}
	});
	private static final String REMOVED_PREFIX = "!";
	final Reference2ObjectMap<DataComponentType<?>, Optional<?>> map;

	private static StreamCodec<RegistryFriendlyByteBuf, DataComponentPatch> createStreamCodec(DataComponentPatch.CodecGetter codecGetter) {
		return new StreamCodec<RegistryFriendlyByteBuf, DataComponentPatch>() {
			public DataComponentPatch decode(RegistryFriendlyByteBuf registryFriendlyByteBuf) {
				int i = registryFriendlyByteBuf.readVarInt();
				int j = registryFriendlyByteBuf.readVarInt();
				if (i == 0 && j == 0) {
					return DataComponentPatch.EMPTY;
				} else {
					int capacity = Math.min(i + j, 65536);
					if (capacity < 0) throw new NegativeArraySizeException(Integer.toString(capacity));
					Reference2ObjectMap<DataComponentType<?>, Optional<?>> reference2ObjectMap = net.minecraft.world.item.NativeItemState.newPatchMap();

					for (int l = 0; l < i; l++) {
						DataComponentType<?> dataComponentType = DataComponentType.STREAM_CODEC.decode(registryFriendlyByteBuf);
						Object object = codecGetter.apply(dataComponentType).decode(registryFriendlyByteBuf);
						reference2ObjectMap.put(dataComponentType, Optional.of(object));
					}

					for (int l = 0; l < j; l++) {
						DataComponentType<?> dataComponentType = DataComponentType.STREAM_CODEC.decode(registryFriendlyByteBuf);
						reference2ObjectMap.put(dataComponentType, Optional.empty());
					}

					return new DataComponentPatch(reference2ObjectMap);
				}
			}

			public void encode(RegistryFriendlyByteBuf registryFriendlyByteBuf, DataComponentPatch dataComponentPatch) {
				if (dataComponentPatch.isEmpty()) {
					registryFriendlyByteBuf.writeVarInt(0);
					registryFriendlyByteBuf.writeVarInt(0);
				} else {
					int i = 0;
					int j = 0;

					for (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(
						dataComponentPatch.map
					)) {
						if (((Optional)entry.getValue()).isPresent()) {
							i++;
						} else {
							j++;
						}
					}

					registryFriendlyByteBuf.writeVarInt(i);
					registryFriendlyByteBuf.writeVarInt(j);

					for (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> entryx : Reference2ObjectMaps.fastIterable(
						dataComponentPatch.map
					)) {
						Optional<?> optional = (Optional<?>)entryx.getValue();
						if (optional.isPresent()) {
							DataComponentType<?> dataComponentType = (DataComponentType<?>)entryx.getKey();
							DataComponentType.STREAM_CODEC.encode(registryFriendlyByteBuf, dataComponentType);
							this.encodeComponent(registryFriendlyByteBuf, dataComponentType, optional.get());
						}
					}

					for (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> entryxx : Reference2ObjectMaps.fastIterable(
						dataComponentPatch.map
					)) {
						if (((Optional)entryxx.getValue()).isEmpty()) {
							DataComponentType<?> dataComponentType2 = (DataComponentType<?>)entryxx.getKey();
							DataComponentType.STREAM_CODEC.encode(registryFriendlyByteBuf, dataComponentType2);
						}
					}
				}
			}

			private <T> void encodeComponent(RegistryFriendlyByteBuf registryFriendlyByteBuf, DataComponentType<T> dataComponentType, Object object) {
				codecGetter.apply(dataComponentType).encode(registryFriendlyByteBuf, (T)object);
			}
		};
	}

	DataComponentPatch(Reference2ObjectMap<DataComponentType<?>, Optional<?>> reference2ObjectMap) {
		this.map = reference2ObjectMap;
	}

	public static DataComponentPatch.Builder builder() {
		return new DataComponentPatch.Builder();
	}

	@Nullable
	public <T> Optional<? extends T> get(DataComponentType<? extends T> dataComponentType) {
		return (Optional<? extends T>)this.map.get(dataComponentType);
	}

	public Set<Entry<DataComponentType<?>, Optional<?>>> entrySet() {
		return this.map.entrySet();
	}

	public int size() {
		return this.map.size();
	}

	public DataComponentPatch forget(Predicate<DataComponentType<?>> predicate) {
		if (this.isEmpty()) {
			return EMPTY;
		} else {
			Reference2ObjectMap<DataComponentType<?>, Optional<?>> reference2ObjectMap = net.minecraft.world.item.NativeItemState.copyPatchMap(this.map);
			reference2ObjectMap.keySet().removeIf(predicate);
			return reference2ObjectMap.isEmpty() ? EMPTY : new DataComponentPatch(reference2ObjectMap);
		}
	}

	public boolean isEmpty() {
		return this.map.isEmpty();
	}

	public DataComponentPatch.SplitResult split() {
		if (this.isEmpty()) {
			return DataComponentPatch.SplitResult.EMPTY;
		} else {
			DataComponentMap.Builder builder = DataComponentMap.builder();
			Set<DataComponentType<?>> set = Sets.newIdentityHashSet();
			this.map.forEach((dataComponentType, optional) -> {
				if (optional.isPresent()) {
					builder.setUnchecked(dataComponentType, optional.get());
				} else {
					set.add(dataComponentType);
				}
			});
			return new DataComponentPatch.SplitResult(builder.build(), set);
		}
	}

	public boolean equals(Object object) {
		return this == object ? true : object instanceof DataComponentPatch dataComponentPatch && this.map.equals(dataComponentPatch.map);
	}

	public int hashCode() {
		return this.map.hashCode();
	}

	public String toString() {
		return toString(this.map);
	}

	static String toString(Reference2ObjectMap<DataComponentType<?>, Optional<?>> reference2ObjectMap) {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append('{');
		boolean bl = true;

		for (Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(reference2ObjectMap)) {
			if (bl) {
				bl = false;
			} else {
				stringBuilder.append(", ");
			}

			Optional<?> optional = (Optional<?>)entry.getValue();
			if (optional.isPresent()) {
				stringBuilder.append(entry.getKey());
				stringBuilder.append("=>");
				stringBuilder.append(optional.get());
			} else {
				stringBuilder.append("!");
				stringBuilder.append(entry.getKey());
			}
		}

		stringBuilder.append('}');
		return stringBuilder.toString();
	}

	public static class Builder {
		private final Reference2ObjectMap<DataComponentType<?>, Optional<?>> map = net.minecraft.world.item.NativeItemState.newPatchMap();

		Builder() {
		}

		public <T> DataComponentPatch.Builder set(DataComponentType<T> dataComponentType, T object) {
			this.map.put(dataComponentType, Optional.of(object));
			return this;
		}

		public <T> DataComponentPatch.Builder remove(DataComponentType<T> dataComponentType) {
			this.map.put(dataComponentType, Optional.empty());
			return this;
		}

		public <T> DataComponentPatch.Builder set(TypedDataComponent<T> typedDataComponent) {
			return this.set(typedDataComponent.type(), typedDataComponent.value());
		}

		public DataComponentPatch build() {
			return this.map.isEmpty() ? DataComponentPatch.EMPTY : new DataComponentPatch(this.map);
		}
	}

	@FunctionalInterface
	interface CodecGetter {
		<T> StreamCodec<? super RegistryFriendlyByteBuf, T> apply(DataComponentType<T> dataComponentType);
	}

	record PatchKey(DataComponentType<?> type, boolean removed) {
		public static final Codec<DataComponentPatch.PatchKey> CODEC = Codec.STRING
			.flatXmap(
				string -> {
					boolean bl = string.startsWith("!");
					if (bl) {
						string = string.substring("!".length());
					}

					ResourceLocation resourceLocation = ResourceLocation.tryParse(string);
					DataComponentType<?> dataComponentType = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(resourceLocation);
					if (dataComponentType == null) {
						return DataResult.error(() -> "No component with type: '" + resourceLocation + "'");
					} else {
						return dataComponentType.isTransient()
							? DataResult.error(() -> "'" + resourceLocation + "' is not a persistent component")
							: DataResult.success(new DataComponentPatch.PatchKey(dataComponentType, bl));
					}
				},
				patchKey -> {
					DataComponentType<?> dataComponentType = patchKey.type();
					ResourceLocation resourceLocation = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(dataComponentType);
					return resourceLocation == null
						? DataResult.error(() -> "Unregistered component: " + dataComponentType)
						: DataResult.success(patchKey.removed() ? "!" + resourceLocation : resourceLocation.toString());
				}
			);

		public Codec<?> valueCodec() {
			return this.removed ? Codec.EMPTY.codec() : this.type.codecOrThrow();
		}
	}

	public record SplitResult(DataComponentMap added, Set<DataComponentType<?>> removed) {
		public static final DataComponentPatch.SplitResult EMPTY = new DataComponentPatch.SplitResult(DataComponentMap.EMPTY, Set.of());
	}
}

package net.minecraft.core.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.util.Map.Entry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record TypedDataComponent<T>(DataComponentType<T> type, T value) {
	public static final StreamCodec<RegistryFriendlyByteBuf, TypedDataComponent<?>> STREAM_CODEC = new StreamCodec<RegistryFriendlyByteBuf, TypedDataComponent<?>>() {
		public TypedDataComponent<?> decode(RegistryFriendlyByteBuf buf) {
			DataComponentType<?> type = DataComponentType.STREAM_CODEC.decode(buf);
			return decodeAny(buf, (DataComponentType) type);
		}

		private static <X> TypedDataComponent<X> decodeAny(RegistryFriendlyByteBuf buf, DataComponentType<X> type) {
			return new TypedDataComponent<>(type, type.streamCodec().decode(buf));
		}

		public void encode(RegistryFriendlyByteBuf buf, TypedDataComponent<?> tdc) {
			encodeAny(buf, (TypedDataComponent) tdc);
		}

		private static <X> void encodeAny(RegistryFriendlyByteBuf buf, TypedDataComponent<X> tdc) {
			DataComponentType.STREAM_CODEC.encode(buf, tdc.type());
			tdc.type().streamCodec().encode(buf, tdc.value());
		}
	};

	static TypedDataComponent<?> fromEntryUnchecked(Entry<DataComponentType<?>, Object> entry) {
		return createUnchecked((DataComponentType) entry.getKey(), entry.getValue());
	}

	public static <X> TypedDataComponent<X> createUnchecked(DataComponentType<X> dataComponentType, Object object) {
		return new TypedDataComponent<>(dataComponentType, (X)object);
	}

	public void applyTo(PatchedDataComponentMap patchedDataComponentMap) {
		patchedDataComponentMap.set(this.type, this.value);
	}

	public <D> DataResult<D> encodeValue(DynamicOps<D> dynamicOps) {
		Codec<T> codec = this.type.codec();
		return codec == null ? DataResult.error(() -> "Component of type " + this.type + " is not encodable") : codec.encodeStart(dynamicOps, this.value);
	}

	public String toString() {
		return this.type + "=>" + this.value;
	}
}

package net.minecraft.world.level.saveddata;

import com.mojang.serialization.Codec;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.util.datafix.DataFixTypes;

public record SavedDataType<T extends SavedData>(
	String id, Function<SavedData.Context, T> constructor, Function<SavedData.Context, Codec<T>> codec, DataFixTypes dataFixType
) {
	public SavedDataType(String string, Supplier<T> supplier, Codec<T> codec, DataFixTypes dataFixTypes) {
		this(string, context -> (T)supplier.get(), context -> codec, dataFixTypes);
	}

	public boolean equals(Object object) {
		return object instanceof SavedDataType<?> savedDataType && this.id.equals(savedDataType.id);
	}

	public int hashCode() {
		return this.id.hashCode();
	}

	public String toString() {
		return "SavedDataType[" + this.id + "]";
	}
}

package net.minecraft.world.level.chunk;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.VarInt;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;

public class SingleValuePalette<T> implements Palette<T> {
	@Nullable
	private T value;

	public SingleValuePalette(List<T> list) {
		if (list.size() > 0) {
			Validate.isTrue(list.size() <= 1, "Can't initialize SingleValuePalette with %d values.", (long)list.size());
			this.value = (T)list.get(0);
		}
	}

	public static <A> Palette<A> create(int i, List<A> list) {
		return new SingleValuePalette((List<T>)list);
	}

	@Override
	public int idFor(T object, PaletteResize<T> paletteResize) {
		if (this.value != null && this.value != object) {
			return paletteResize.onResize(1, object);
		} else {
			this.value = object;
			return 0;
		}
	}

	@Override
	public boolean maybeHas(Predicate<T> predicate) {
		if (this.value == null) {
			throw new IllegalStateException("Use of an uninitialized palette");
		} else {
			return predicate.test(this.value);
		}
	}

	@Override
	public T valueFor(int i) {
		if (this.value != null && i == 0) {
			return this.value;
		} else {
			throw new IllegalStateException("Missing Palette entry for id " + i + ".");
		}
	}

	@Override
	public void read(FriendlyByteBuf friendlyByteBuf, IdMap<T> idMap) {
		this.value = idMap.byIdOrThrow(friendlyByteBuf.readVarInt());
	}

	@Override
	public void write(FriendlyByteBuf friendlyByteBuf, IdMap<T> idMap) {
		if (this.value == null) {
			throw new IllegalStateException("Use of an uninitialized palette");
		} else {
			friendlyByteBuf.writeVarInt(idMap.getId(this.value));
		}
	}

	@Override
	public int getSerializedSize(IdMap<T> idMap) {
		if (this.value == null) {
			throw new IllegalStateException("Use of an uninitialized palette");
		} else {
			return VarInt.getByteSize(idMap.getId(this.value));
		}
	}

	@Override
	public int getSize() {
		return 1;
	}

	@Override
	public Palette<T> copy() {
		if (this.value == null) {
			throw new IllegalStateException("Use of an uninitialized palette");
		} else {
			return this;
		}
	}
}

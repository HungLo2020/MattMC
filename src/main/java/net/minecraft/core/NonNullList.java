package net.minecraft.core;

import com.google.common.collect.Lists;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NonNullList<E> extends AbstractList<E> {
	private final List<E> list;
	@Nullable
	private final E defaultValue;

	public static <E> NonNullList<E> create() {
		return new NonNullList<>(Lists.<E>newArrayList(), null);
	}

	public static <E> NonNullList<E> createWithCapacity(int i) {
		return new NonNullList<>(Lists.<E>newArrayListWithCapacity(i), null);
	}

	public static <E> NonNullList<E> withSize(int i, E object) {
		Objects.requireNonNull(object);
		E[] objects = (E[])new Object[i];
		Arrays.fill(objects, object);
		return new NonNullList<>(Arrays.asList(objects), object);
	}

	@SafeVarargs
	public static <E> NonNullList<E> of(E object, E... objects) {
		return new NonNullList<>(Arrays.asList(objects), object);
	}

	protected NonNullList(List<E> list, @Nullable E object) {
		this.list = list;
		this.defaultValue = object;
	}

	@NotNull
	public E get(int i) {
		return (E)this.list.get(i);
	}

	public E set(int i, E object) {
		Objects.requireNonNull(object);
		return (E)this.list.set(i, object);
	}

	public void add(int i, E object) {
		Objects.requireNonNull(object);
		this.list.add(i, object);
	}

	public E remove(int i) {
		return (E)this.list.remove(i);
	}

	public int size() {
		return this.list.size();
	}

	public void clear() {
		if (this.defaultValue == null) {
			super.clear();
		} else {
			for (int i = 0; i < this.size(); i++) {
				this.set(i, this.defaultValue);
			}
		}
	}
}

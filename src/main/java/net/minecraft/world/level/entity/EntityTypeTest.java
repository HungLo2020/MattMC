package net.minecraft.world.level.entity;

import org.jetbrains.annotations.Nullable;

public interface EntityTypeTest<B, T extends B> {
    static <B,T extends B> EntityTypeTest<B,T> forClass(Class<T> type) { return new ClassTest<>(type); }

    /** Final stock test: class groups already enforce this pure predicate. */
    final class ClassTest<B,T extends B> implements EntityTypeTest<B,T> {
        private final Class<T> type;
        private ClassTest(Class<T> type) { this.type=type; }
        @Override @Nullable public T tryCast(B object) { return type.isInstance(object)?type.cast(object):null; }
        @Override public Class<T> getBaseClass() { return type; }
    }

	static <B, T extends B> EntityTypeTest<B, T> forExactClass(Class<T> class_) {
		return new EntityTypeTest<B, T>() {
			@Nullable
			@Override
			public T tryCast(B object) {
				return (T)(class_.equals(object.getClass()) ? object : null);
			}

			@Override
			public Class<? extends B> getBaseClass() {
				return class_;
			}
		};
	}

	@Nullable
	T tryCast(B object);

	Class<? extends B> getBaseClass();
}

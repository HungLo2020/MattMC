package net.minecraft.world.level.entity;

import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

public interface LevelEntityGetter<T extends EntityAccess> {
	@Nullable
	T get(int i);

	@Nullable
	T get(UUID uUID);

	Iterable<T> getAll();

	<U extends T> void get(EntityTypeTest<T, U> entityTypeTest, AbortableIterationConsumer<U> abortableIterationConsumer);

	void get(AABB aABB, Consumer<T> consumer);

    default void collectEntities(AABB query,T excluded,java.util.function.Predicate<? super T> predicate,java.util.ArrayList<T> result) {
        get(query,entity->{if(entity!=excluded && predicate.test(entity))result.add(entity);});
    }

    default void collectScreenShakeSources(AABB query,T excluded,java.util.ArrayList<T> result) {
        var all=new java.util.ArrayList<T>();
        @SuppressWarnings({"unchecked","rawtypes"})
        java.util.function.Predicate<? super T> predicate=(java.util.function.Predicate)net.minecraft.world.entity.EntitySelector.NO_SPECTATORS;
        collectEntities(query,excluded,predicate,all);
        for(T entity:all)if(entity instanceof net.alexscaves.server.entity.util.ShakesScreen)result.add(entity);
    }

	<U extends T> void get(EntityTypeTest<T, U> entityTypeTest, AABB aABB, AbortableIterationConsumer<U> abortableIterationConsumer);
}

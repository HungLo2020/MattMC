package net.minecraft.world.level.entity;

import net.logging.LogUtils;
import java.util.UUID;
import net.minecraft.util.AbortableIterationConsumer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class EntityLookup<T extends EntityAccess> {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final NativeEntityLookup<T> registry = new NativeEntityLookup<>();

	public <U extends T> void getEntities(EntityTypeTest<T, U> entityTypeTest, AbortableIterationConsumer<U> abortableIterationConsumer) {
		for (T entityAccess : this.registry.values()) {
			U entityAccess2 = (U)entityTypeTest.tryCast(entityAccess);
			if (entityAccess2 != null && abortableIterationConsumer.accept(entityAccess2).shouldAbort()) {
				return;
			}
		}
	}

	public Iterable<T> getAllEntities() {
		return this.registry.values();
	}

	public void add(T entityAccess) {
		UUID uUID = entityAccess.getUUID();
		if (!this.registry.add(entityAccess, uUID)) {
			LOGGER.warn("Duplicate entity UUID {}: {}", uUID, entityAccess);
		}
	}

	public void remove(T entityAccess) {
		this.registry.remove(entityAccess);
	}

	@Nullable
	public T getEntity(int i) {
		return this.registry.get(i);
	}

	@Nullable
	public T getEntity(UUID uUID) {
		return this.registry.get(uUID);
	}

	public int count() {
		return this.registry.count();
	}
}

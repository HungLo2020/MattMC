package net.minecraft.world.level.entity;

import net.logging.LogUtils;
import java.util.Collection;
import java.util.stream.Stream;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

public class EntitySection<T extends EntityAccess> {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final ClassInstanceMultiMap<T> storage;
	private Visibility chunkStatus;

	public EntitySection(Class<T> class_, Visibility visibility) {
		this.chunkStatus = visibility;
		this.storage = new ClassInstanceMultiMap<>(class_);
	}

	public void add(T entityAccess) {
		this.storage.add(entityAccess, entityAccess instanceof net.minecraft.world.entity.Entity entity ? entity.nativeCullingGeometry() : null);
	}

	public boolean remove(T entityAccess) {
		return this.storage.remove(entityAccess);
	}

	public AbortableIterationConsumer.Continuation getEntities(AABB aABB, AbortableIterationConsumer<T> abortableIterationConsumer) {
        return this.storage.forEachIntersecting(aABB,EntitySection::intersectsBounds,abortableIterationConsumer);
	}

	public <U extends T> AbortableIterationConsumer.Continuation getEntities(
		EntityTypeTest<T, U> entityTypeTest, AABB aABB, AbortableIterationConsumer<? super U> abortableIterationConsumer
	) {
        if(entityTypeTest instanceof EntityTypeTest.ClassTest<T,U> stock) {
            return this.storage.forEachIntersecting(stock.getBaseClass(),aABB,EntitySection::intersectsBounds,abortableIterationConsumer);
        }
		Collection<? extends T> collection = this.storage.find(entityTypeTest.getBaseClass());
		if (collection.isEmpty()) {
			return AbortableIterationConsumer.Continuation.CONTINUE;
		} else {
			for (T entityAccess : collection) {
				U entityAccess2 = (U)entityTypeTest.tryCast(entityAccess);
				if (entityAccess2 != null && entityAccess.getBoundingBox().intersects(aABB) && abortableIterationConsumer.accept(entityAccess2).shouldAbort()) {
					return AbortableIterationConsumer.Continuation.ABORT;
				}
			}

			return AbortableIterationConsumer.Continuation.CONTINUE;
		}
	}

    private static boolean intersectsBounds(EntityAccess entityAccess,AABB aABB) {
        return entityAccess.getBoundingBox().intersects(aABB);
    }

	public boolean isEmpty() {
		return this.storage.isEmpty();
	}

	public Stream<T> getEntities() {
		return this.storage.stream();
	}

	public Visibility getStatus() {
		return this.chunkStatus;
	}

	public Visibility updateChunkStatus(Visibility visibility) {
		Visibility visibility2 = this.chunkStatus;
		this.chunkStatus = visibility;
		return visibility2;
	}

	@VisibleForDebug
	public int size() {
		return this.storage.size();
	}
}

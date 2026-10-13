package net.minecraft.world.level.entity;

import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Objects;
import java.util.Spliterators;
import java.util.PrimitiveIterator.OfLong;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

public class EntitySectionStorage<T extends EntityAccess> {
	public static final int CHONKY_ENTITY_SEARCH_GRACE = 2;
	public static final int MAX_NON_CHONKY_ENTITY_SIZE = 4;
	private final Class<T> entityClass;
	private final Long2ObjectFunction<Visibility> intialSectionVisibility;
	private final NativeEntitySectionIndex<T> sections = new NativeEntitySectionIndex<>();

	public EntitySectionStorage(Class<T> class_, Long2ObjectFunction<Visibility> long2ObjectFunction) {
		this.entityClass = class_;
		this.intialSectionVisibility = long2ObjectFunction;
	}

	public void forEachAccessibleNonEmptySection(AABB aABB, AbortableIterationConsumer<EntitySection<T>> abortableIterationConsumer) {
        try (var cursor=this.sections.spatialCursor(aABB.minX,aABB.minY,aABB.minZ,aABB.maxX,aABB.maxY,aABB.maxZ)) {
            while(cursor.hasNext()) {
                if(!cursor.advanceQuery(false))break;
                EntitySection<T> entitySection=cursor.section();
                if(cursor.accessible(entitySection)
                    && abortableIterationConsumer.accept(entitySection).shouldAbort())return;
            }
        }
    }

    public LongStream getExistingSectionPositionsInChunk(long chunkKey) {
        var cursor=this.sections.chunkCursor(chunkKey);
        if(!cursor.hasNext()){cursor.close();return LongStream.empty();}
        OfLong iterator=cursor;
        return StreamSupport.longStream(Spliterators.spliteratorUnknownSize(iterator,1301),false).onClose(cursor::close);
    }

	public Stream<EntitySection<T>> getExistingSectionsInChunk(long l) {
		return this.getExistingSectionPositionsInChunk(l).mapToObj(this.sections::get).filter(Objects::nonNull);
	}

	public EntitySection<T> getOrCreateSection(long key) {
        int slot=this.sections.prepare(key);
        if(slot<0)return this.sections.prepared();
        Visibility visibility=this.intialSectionVisibility.get(this.sections.chunk(key));
        this.sections.addKey(key);
        EntitySection<T> section=new EntitySection<>(this.entityClass,visibility);
        this.sections.insert(key,slot,section);
        return section;
    }

	@Nullable
	public EntitySection<T> getSection(long l) {
		return this.sections.get(l);
	}

	public LongSet getAllChunksWithExistingSections() {
        return this.sections.chunks();
    }

	public void getEntities(AABB aABB, AbortableIterationConsumer<T> abortableIterationConsumer) {
        // Subclasses may override the public section traversal.
        if(getClass()!=EntitySectionStorage.class) {
            this.forEachAccessibleNonEmptySection(aABB, section -> section.getEntities(aABB,abortableIterationConsumer));
            return;
        }
        try(var cursor=this.sections.spatialCursor(aABB.minX,aABB.minY,aABB.minZ,aABB.maxX,aABB.maxY,aABB.maxZ)) {
            while(cursor.hasNext() && cursor.advanceQuery(true)) {
                if(cursor.consume(aABB,abortableIterationConsumer).shouldAbort())return;
            }
        }
	}

    /** Stock collection owns no arbitrary result callbacks; custom selectors retain ordered dispatch. */
    public void collectEntities(AABB query,T excluded,java.util.function.Predicate<? super T> predicate,java.util.ArrayList<T> result) {
        if(getClass()!=EntitySectionStorage.class || predicate!=net.minecraft.world.entity.EntitySelector.NO_SPECTATORS
            || result.getClass()!=java.util.ArrayList.class) {
            getEntities(query,entity->{
                if(entity!=excluded && predicate.test(entity))result.add(entity);
                return AbortableIterationConsumer.Continuation.CONTINUE;
            });
            return;
        }
        try(var cursor=this.sections.spatialCursor(query.minX,query.minY,query.minZ,query.maxX,query.maxY,query.maxZ)) {
            cursor.collectNonSpectators(query,excluded,predicate,result);
        }
    }

	public <U extends T> void getEntities(EntityTypeTest<T, U> entityTypeTest, AABB aABB, AbortableIterationConsumer<U> abortableIterationConsumer) {
        if(getClass()==EntitySectionStorage.class && entityTypeTest instanceof EntityTypeTest.ClassTest<T,U> stock && stock.getBaseClass()!=null) {
            long identity=net.minecraft.util.ClassInstanceMultiMap.queryTypeIdentity(stock.getBaseClass());
            try(var cursor=this.sections.spatialCursor(aABB.minX,aABB.minY,aABB.minZ,aABB.maxX,aABB.maxY,aABB.maxZ)) {
                while(cursor.hasNext() && cursor.advanceQuery(true,identity)) {
                    if(cursor.consumeTyped(stock,aABB,abortableIterationConsumer).shouldAbort())return;
                }
            }
            return;
        }
		this.forEachAccessibleNonEmptySection(aABB, entitySection -> entitySection.getEntities(entityTypeTest, aABB, abortableIterationConsumer));
	}

	public void remove(long l) {
		this.sections.remove(l);
	}

	@VisibleForDebug
	public int count() {
		return this.sections.count();
	}
}

package net.minecraft.world.level.entity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.SectionPos;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

class EntitySectionStorageNativeTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
    @Test void reentrantCreationKeepsOuterCapturedSlotAndSingleOrderedKey() {
        AtomicReference<EntitySectionStorage<EntityAccess>> owner=new AtomicReference<>();
        AtomicReference<EntitySection<EntityAccess>> nested=new AtomicReference<>();
        long key=SectionPos.asLong(-2,3,4);
        var storage=new EntitySectionStorage<>(EntityAccess.class,chunk->{
            if(nested.get()==null) {
                nested.set(new EntitySection<>(EntityAccess.class,Visibility.HIDDEN));
                nested.set(owner.get().getOrCreateSection(key));
            }
            return Visibility.TRACKED;
        });
        owner.set(storage);
        var outer=storage.getOrCreateSection(key);
        assertNotSame(nested.get(),outer);
        assertSame(outer,storage.getSection(key));
        assertEquals(1,storage.count());
        assertArrayEquals(new long[]{key},storage.getExistingSectionPositionsInChunk(ChunkPos.asLong(-2,4)).toArray());
    }
    @Test void positionStreamKeepsRemovedPrefetchedKeyWithoutRetainingItsBinding() {
        var storage=new EntitySectionStorage<>(EntityAccess.class,chunk->Visibility.TICKING);
        long first=SectionPos.asLong(0,0,0),second=SectionPos.asLong(0,1,0);
        storage.getOrCreateSection(first);storage.getOrCreateSection(second);
        var positions=storage.getExistingSectionPositionsInChunk(0).iterator();
        storage.remove(first);
        assertEquals(first,positions.nextLong());assertNull(storage.getSection(first));
        assertEquals(second,positions.nextLong());assertFalse(positions.hasNext());
    }
    @Test void spatialCallbackCanPopulateFutureColumnBeforeItsRangeOpens() {
        var storage=new EntitySectionStorage<>(EntityAccess.class,chunk->Visibility.TICKING);
        long first=SectionPos.asLong(0,0,0),second=SectionPos.asLong(1,0,0);
        var initial=storage.getOrCreateSection(first);initial.add(mock(EntityAccess.class));
        var seen=new ArrayList<EntitySection<EntityAccess>>();
        storage.forEachAccessibleNonEmptySection(new AABB(0,0,0,17,16,0),section->{
            seen.add(section);
            if(section==initial)storage.getOrCreateSection(second).add(mock(EntityAccess.class));
            return AbortableIterationConsumer.Continuation.CONTINUE;
        });
        assertEquals(java.util.List.of(initial,storage.getSection(second)),seen);
    }
    @Test void nullVisibilityStillFailsOnlyForNonEmptySectionQuery() {
        var storage=new EntitySectionStorage<>(EntityAccess.class,chunk->null);
        var section=storage.getOrCreateSection(0);
        assertDoesNotThrow(()->storage.forEachAccessibleNonEmptySection(new AABB(0,0,0,1,1,1),null));
        section.add(mock(EntityAccess.class));
        assertThrows(NullPointerException.class,()->storage.forEachAccessibleNonEmptySection(new AABB(0,0,0,1,1,1),s->AbortableIterationConsumer.Continuation.CONTINUE));
    }
    @Test void rangeQueriesPreserveOverriddenSectionTraversal() {
        var calls=new ArrayList<String>();
        var storage=new EntitySectionStorage<EntityAccess>(EntityAccess.class,chunk->Visibility.TRACKED) {
            @Override public void forEachAccessibleNonEmptySection(AABB box,AbortableIterationConsumer<EntitySection<EntityAccess>> consumer) {
                calls.add("override");
            }
        };
        storage.getEntities(new AABB(0,0,0,1,1,1),entity->{fail("Override supplies no entities");return AbortableIterationConsumer.Continuation.CONTINUE;});
        assertEquals(java.util.List.of("override"),calls);
    }
    @Test void retainedSectionVisibilityChangesAfterRemovalRemainLocalToThatSection() {
        var storage=new EntitySectionStorage<EntityAccess>(EntityAccess.class,chunk->Visibility.TRACKED);
        var first=storage.getOrCreateSection(0);
        assertEquals(Visibility.TRACKED,first.updateChunkStatus(Visibility.HIDDEN));
        storage.remove(0);
        var replacement=storage.getOrCreateSection(0);
        assertEquals(Visibility.HIDDEN,first.updateChunkStatus(null));
        assertNull(first.getStatus());
        assertEquals(Visibility.TRACKED,replacement.getStatus());
        assertNull(first.updateChunkStatus(Visibility.TICKING));
        assertEquals(Visibility.TICKING,first.getStatus());
    }
}

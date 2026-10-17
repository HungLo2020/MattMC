package net.minecraft.world.level.entity;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EntityResidencyIntegrationTest {
    @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
    @Test void nullableUuidAndZeroUuidStayIndependentWithRetainedNativeCounts(){
        var state=new NativeEntityResidency();var zero=new UUID(0,0);
        assertTrue(state.knownUuids.add(null));assertTrue(state.knownUuids.add(zero));assertFalse(state.knownUuids.add(null));
        assertEquals(2,state.knownUuids.size());assertTrue(state.knownUuids.remove(null));assertTrue(state.knownUuids.contains(zero));assertFalse(state.knownUuids.contains(null));
        assertEquals(1,state.knownUuids.size());
    }
    @Test void pendingLoadDefersUnloadAndCompletionPersistsBeforeRetiringMembership(){
        var pending=new CompletableFuture<ChunkEntities<EntityLookupIntegrationTest.Member>>();
        var stored=new ArrayList<List<Integer>>();
        EntityPersistentStorage<EntityLookupIntegrationTest.Member> storage=new EntityPersistentStorage<>(){
            public CompletableFuture<ChunkEntities<EntityLookupIntegrationTest.Member>> loadEntities(ChunkPos p){return pending;}
            public void storeEntities(ChunkEntities<EntityLookupIntegrationTest.Member> values){stored.add(values.getEntities().map(m->m.id).toList());}
            public void flush(boolean sync){}
        };
        var events=new ArrayList<String>();var manager=new PersistentEntitySectionManager<>(EntityLookupIntegrationTest.Member.class,EntityLookupIntegrationTest.callbacks(events),storage);var member=new EntityLookupIntegrationTest.Member(1);
        manager.addNewEntity(member);manager.updateChunkStatus(new ChunkPos(0,0),Visibility.TICKING);manager.updateChunkStatus(new ChunkPos(0,0),Visibility.HIDDEN);
        manager.tick();assertTrue(stored.isEmpty());assertTrue(manager.isLoaded(member.uuid));assertFalse(manager.areEntitiesLoaded(0));
        pending.complete(new ChunkEntities<>(new ChunkPos(0,0),List.of()));manager.tick();
        assertEquals(List.of(List.of(1)),stored);assertFalse(manager.isLoaded(member.uuid));assertEquals("0,0,0,0,0,0,0",manager.gatherStats());
    }
    @Test void nativeEnumViewsPreservePresenceNullAndDefaultsWithoutJavaMaps(){
        var state=new NativeEntityResidency();var map=state.visibility();
        assertNull(map.get(17));map.defaultReturnValue(Visibility.HIDDEN);assertEquals(Visibility.HIDDEN,map.get(17));
        assertFalse(map.containsKey(17));assertEquals(Visibility.HIDDEN,map.put(17,null));assertTrue(map.containsKey(17));assertNull(map.get(17));assertEquals(1,map.size());
        assertNull(map.remove(17));assertFalse(map.containsKey(17));assertEquals(Visibility.HIDDEN,map.get(17));assertEquals(0,map.size());
    }
    @Test void nativeSetIteratorKeepsClearDuringPredicateFailurePrefix(){
        var state=new NativeEntityResidency();var set=state.unloads();set.add(0);
        assertTrue(set.removeIf((java.util.function.LongPredicate)key->{set.clear();return true;}));
        assertEquals(-1,set.size());assertTrue(set.add(17));assertEquals(0,set.size());assertTrue(set.contains(17));
    }
}

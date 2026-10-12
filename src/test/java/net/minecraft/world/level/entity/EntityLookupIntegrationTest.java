package net.minecraft.world.level.entity;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

class EntityLookupIntegrationTest {
    @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
    static final class Member implements EntityAccess {
        final int id;
        final UUID uuid;
        int uuidReads,idReads;
        Runnable idPrefix;
        BlockPos position=BlockPos.ZERO;
        EntityInLevelCallback callback=EntityInLevelCallback.NULL;
        Member(int id){this.id=id;uuid=new UUID(0,id);}
        @Override public int getId(){idReads++;var hook=idPrefix;idPrefix=null;if(hook!=null)hook.run();return id;}
        @Override public UUID getUUID(){uuidReads++;return uuid;}
        @Override public boolean isRemoved(){return false;}
        @Override public BlockPos blockPosition(){return position;}
        @Override public AABB getBoundingBox(){return new AABB(position);}
        @Override public void setLevelCallback(EntityInLevelCallback value){callback=value;}
        @Override public Stream<Member> getSelfAndPassengers(){return Stream.of(this);}
        @Override public Stream<Member> getPassengersAndSelf(){return Stream.of(this);}
        @Override public void setRemoved(Entity.RemovalReason reason){callback.onRemove(reason);}
        @Override public boolean shouldBeSaved(){return true;}
        @Override public boolean isAlwaysTicking(){return false;}
    }
    @SuppressWarnings("unchecked") static <E extends Throwable> void sneaky(Throwable failure)throws E{throw (E)failure;}
    @Test void getterExceptionRetainsCommittedUuidAndOriginalException() {
        var lookup=new EntityLookup<Member>();var member=new Member(7);var failure=new IOException("checked getter");
        member.idPrefix=() -> EntityLookupIntegrationTest.<RuntimeException>sneaky(failure);
        assertSame(failure,assertThrows(IOException.class,() -> lookup.add(member)));
        assertEquals(1,lookup.count());assertSame(member,lookup.getEntity(member.uuid));assertNull(lookup.getEntity(7));
        lookup.remove(member);assertEquals(0,lookup.count());
    }
    @Test void duplicateUuidReadsGetterOnceAndSkipsIdGetter() {
        var lookup=new EntityLookup<Member>();var first=new Member(9);var duplicate=new Member(9);
        lookup.add(first);duplicate.idPrefix=() -> {throw new AssertionError("Duplicate ID getter ran");};lookup.add(duplicate);
        assertEquals(1,duplicate.uuidReads);assertEquals(0,duplicate.idReads);
        assertSame(first,lookup.getEntity(9));assertSame(first,lookup.getEntity(first.uuid));assertEquals(1,lookup.count());
    }
    @Test void reentrantRemovalKeepsOuterIdReceiverAndIndependentUuidBinding() {
        var lookup=new EntityLookup<Member>();var member=new Member(0);var nested=new Member(0);
        member.idPrefix=() -> {lookup.remove(member);lookup.add(nested);};lookup.add(member);
        assertSame(member,lookup.getEntity(0));assertSame(nested,lookup.getEntity(nested.uuid));assertEquals(1,lookup.count());
        lookup.remove(member);assertEquals(0,lookup.count());assertNull(lookup.getEntity(0));
    }
    static LevelCallback<Member> callbacks(List<String> events) {
        return new LevelCallback<>() {
            public void onCreated(Member m){events.add("created");}
            public void onDestroyed(Member m){events.add("destroyed");}
            public void onTickingStart(Member m){events.add("tick-start");}
            public void onTickingEnd(Member m){events.add("tick-end");}
            public void onTrackingStart(Member m){events.add("track-start");}
            public void onTrackingEnd(Member m){events.add("track-end");}
            public void onSectionChange(Member m){events.add("section");}
        };
    }
    @Test void clientManagerUsesNativeTrackingAcrossMoveTickingAndRemoval() {
        var events=new ArrayList<String>();var manager=new TransientEntitySectionManager<>(Member.class,callbacks(events));var member=new Member(1);
        manager.addEntity(member);assertSame(member,manager.getEntityGetter().get(1));
        manager.startTicking(new ChunkPos(0,0));member.position=new BlockPos(32,0,0);member.callback.onMove();
        member.setRemoved(Entity.RemovalReason.DISCARDED);assertEquals(0,manager.count());assertNull(manager.getEntityGetter().get(1));
        assertEquals(List.of("created","track-start","tick-start","section","tick-end","track-end","destroyed"),events);
    }
    @Test void serverManagerUsesNativeTrackingAcrossVisibilityTransitions() {
        var events=new ArrayList<String>();EntityPersistentStorage<Member> storage=new EntityPersistentStorage<>() {
            public CompletableFuture<ChunkEntities<Member>> loadEntities(ChunkPos p){return CompletableFuture.completedFuture(new ChunkEntities<>(p,List.of()));}
            public void storeEntities(ChunkEntities<Member> chunk){}
            public void flush(boolean sync){}
        };
        var manager=new PersistentEntitySectionManager<>(Member.class,callbacks(events),storage);var member=new Member(2);var chunk=new ChunkPos(0,0);
        assertTrue(manager.addNewEntity(member));assertNull(manager.getEntityGetter().get(2));
        manager.updateChunkStatus(chunk,Visibility.TICKING);assertSame(member,manager.getEntityGetter().get(2));
        manager.updateChunkStatus(chunk,Visibility.HIDDEN);assertNull(manager.getEntityGetter().get(2));
        member.setRemoved(Entity.RemovalReason.DISCARDED);assertFalse(manager.isLoaded(member.uuid));
        assertEquals(List.of("created","track-start","tick-start","tick-end","track-end","destroyed"),events);
    }
}

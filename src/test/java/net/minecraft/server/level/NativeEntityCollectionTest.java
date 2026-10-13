package net.minecraft.server.level;

import java.lang.foreign.Arena;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.SectionPos;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.entity.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NativeEntityCollectionTest {
    static class Fixture extends Entity {
        Fixture(){super(EntityType.PIG,null);}
        @Override protected void defineSynchedData(SynchedEntityData.Builder builder){}
        @Override protected void readAdditionalSaveData(ValueInput input){}
        @Override protected void addAdditionalSaveData(ValueOutput output){}
        @Override public boolean hurtServer(ServerLevel level,DamageSource source,float damage){return false;}
    }
    static class Callback extends Fixture {
        Runnable hook;
        boolean spectator;
        @Override public boolean isSpectator(){if(hook!=null){var call=hook;hook=null;call.run();}return spectator;}
    }
    private static final AABB BOX=new AABB(-1,-1,-1,4097,2,2);
    @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}

    static Fixture fixture(Arena scope,Class<? extends Fixture> type) throws Exception {
        var unsafe=Class.forName("sun.misc.Unsafe");var field=unsafe.getDeclaredField("theUnsafe");field.setAccessible(true);
        Fixture entity=(Fixture)unsafe.getMethod("allocateInstance",Class.class).invoke(field.get(null),type);
        var geometry=Entity.class.getDeclaredField("nativeGeometry");geometry.setAccessible(true);
        var box=new AABB(0,0,0,1,1,1);
        geometry.set(entity,new NativeEntityGeometry().create(scope,0,0,0,box));
        entity.setBoundingBox(box);
        return entity;
    }
    static LevelEntityGetter<Entity> getter(EntitySectionStorage<Entity> storage){return new LevelEntityGetterAdapter<>(new EntityLookup<>(),storage);}
    static ArrayList<Entity> collect(LevelEntityGetter<Entity> getter,Entity excluded){
        var result=new ArrayList<Entity>();getter.collectEntities(BOX,excluded,EntitySelector.NO_SPECTATORS,result);return result;
    }

    @Test void productionLevelQueryCollectsAcrossBatchesAndPreservesExclusion() throws Exception {
        try(var scope=Arena.ofConfined()) {
            var storage=new EntitySectionStorage<>(Entity.class,k->Visibility.TRACKED);
            var expected=new ArrayList<Entity>();
            for(int i=0;i<257;i++) {
                var entity=fixture(scope,Fixture.class);
                storage.getOrCreateSection(SectionPos.asLong(i/2,0,0)).add(entity);expected.add(entity);
            }
            var excluded=expected.remove(127);
            var level=mock(ServerLevel.class,CALLS_REAL_METHODS);
            doReturn(getter(storage)).when(level).getEntities();
            doReturn(List.of()).when(level).dragonParts();
            assertEquals(expected,level.getEntities(excluded,BOX,EntitySelector.NO_SPECTATORS));
            assertEquals(expected,level.getEntities(excluded,BOX,EntitySelector.NO_SPECTATORS));
        }
    }

    @Test void callbackTailPreservesIteratorMutationAndDoesNotOpenFutureColumnsEarly() throws Exception {
        try(var scope=Arena.ofConfined()) {
            var storage=new EntitySectionStorage<>(Entity.class,k->Visibility.TRACKED);
            var first=storage.getOrCreateSection(0);
            var ordinary=fixture(scope,Fixture.class);
            var callback=(Callback)fixture(scope,Callback.class);
            var later=fixture(scope,Fixture.class);
            first.add(ordinary);first.add(callback);
            callback.hook=()->storage.getOrCreateSection(SectionPos.asLong(1,0,0)).add(later);
            assertEquals(List.of(ordinary,callback,later),collect(getter(storage),null));
            callback.hook=()->first.add(later);
            var partial=new ArrayList<Entity>();
            assertThrows(java.util.ConcurrentModificationException.class,()->getter(storage).collectEntities(BOX,null,EntitySelector.NO_SPECTATORS,partial));
            assertEquals(List.of(ordinary,callback),partial);
        }
    }

    @Test void excludedCallbackStillChecksCustomBoundsButNeverSpectator() throws Exception {
        try(var scope=Arena.ofConfined()) {
            var storage=new EntitySectionStorage<>(Entity.class,k->Visibility.TRACKED);
            var callback=(Callback)fixture(scope,Callback.class);
            var calls=new ArrayList<String>();
            storage.getOrCreateSection(0).add(callback);
            callback.setBoundingBox(new AABB(0,0,0,1,1,1){
                @Override public boolean intersects(AABB box){calls.add("bounds");return true;}
            });
            callback.hook=()->fail("Excluded entity must not run spectator behavior");
            assertTrue(collect(getter(storage),callback).isEmpty());
            assertEquals(List.of("bounds"),calls);
        }
    }

    @Test void allExcludedBatchesDoNotMutateExistingResultOrItsIterator() throws Exception {
        try(var scope=Arena.ofConfined()) {
            var storage=new EntitySectionStorage<>(Entity.class,k->Visibility.TRACKED);
            var excluded=fixture(scope,Fixture.class);
            var existing=fixture(scope,Fixture.class);
            for(int i=0;i<257;i++)storage.getOrCreateSection(0).add(excluded);
            var result=new ArrayList<Entity>();result.add(existing);
            var iterator=result.iterator();
            getter(storage).collectEntities(BOX,excluded,EntitySelector.NO_SPECTATORS,result);
            assertEquals(List.of(existing),result);
            assertSame(existing,iterator.next());
            assertFalse(iterator.hasNext());
        }
    }

    @Test void resultSubclassAndGetterOverridesRemainCallbackBoundaries() throws Exception {
        try(var scope=Arena.ofConfined()) {
            var storage=new EntitySectionStorage<>(Entity.class,k->Visibility.TRACKED);
            var first=fixture(scope,Fixture.class);var second=fixture(scope,Fixture.class);
            var section=storage.getOrCreateSection(0);section.add(first);section.add(second);
            var result=new ArrayList<Entity>() {
                @Override public boolean add(Entity value){if(value==first)second.setBoundingBox(new AABB(-20,0,0,-19,1,1));return super.add(value);}
            };
            getter(storage).collectEntities(BOX,null,EntitySelector.NO_SPECTATORS,result);
            assertEquals(List.of(first),result);
            var custom=new LevelEntityGetterAdapter<Entity>(new EntityLookup<>(),storage) {
                @Override public void get(AABB box,java.util.function.Consumer<Entity> consumer){consumer.accept(second);}
            };
            assertEquals(List.of(second),collect(custom,null));
        }
    }
}

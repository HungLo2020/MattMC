package net.minecraft.world.entity;

import java.lang.foreign.Arena;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.core.BlockPos;
import net.minecraft.hooks.*;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.item.*;
import net.sodium.client.SodiumClientMod;
import net.sodium.client.gui.SodiumGameOptions;
import net.vulkanic.world.*;
import org.joml.Matrix4f;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntityGeometryIntegrationTest {
    static class Fixture extends Entity {
        List<String> events;
        Fixture(){super(EntityType.PIG,null);}
        @Override protected void defineSynchedData(SynchedEntityData.Builder b){}
        @Override protected void readAdditionalSaveData(ValueInput in){}
        @Override protected void addAdditionalSaveData(ValueOutput out){}
        @Override public boolean hurtServer(ServerLevel level,DamageSource source,float damage){return false;}
        @Override public boolean shouldRender(double x,double y,double z){events.add("distance");return true;}
        void transfer(Level level){setLevel(level);}
        void revive(){unsetRemoved();}
    }
    static class Renderer extends EntityRenderer<Fixture,EntityRenderState> {
        Renderer(){super(mock(EntityRendererProvider.Context.class));}
        @Override public EntityRenderState createRenderState(){return new EntityRenderState();}
        @Override protected boolean affectedByCulling(Fixture entity){entity.events.add("affected");return true;}
    }
    static class LeashedFixture extends Fixture implements Leashable {
        Entity holder;
        @Override public LeashData getLeashData(){return null;}
        @Override public void setLeashData(LeashData data){}
        @Override public Entity getLeashHolder(){events.add("holder");setBoundingBox(new AABB(-200,0,0,-199,1,1));return holder;}
    }
    static class CustomRenderer extends Renderer {
        @Override public AABB getBoundingBoxForCulling(Fixture entity){entity.events.add("custom bounds");return entity.getBoundingBox();}
    }
    static class MutatingBoundsRenderer extends Renderer {
        @Override public AABB getBoundingBoxForCulling(Fixture entity) {
            entity.events.add("bounds mutation");var captured=entity.getBoundingBox();
            entity.setBoundingBox(new AABB(100,100,100,101,101,101));return captured;
        }
    }
    static class DerivedBoundsRenderer extends Renderer {
        @Override public AABB getBoundingBoxForCulling(Fixture entity) {
            entity.events.add("derived bounds");return new AABB(100,100,100,101,101,101);
        }
    }
    static class LivingRenderer extends LivingEntityRenderer<LivingEntity,LivingEntityRenderState,EntityModel<LivingEntityRenderState>> {
        @SuppressWarnings("unchecked") LivingRenderer(){super(mock(EntityRendererProvider.Context.class),mock(EntityModel.class),0.0F);}
        @Override public LivingEntityRenderState createRenderState(){return new LivingEntityRenderState();}
        @Override public ResourceLocation getTextureLocation(LivingEntityRenderState state){return null;}
    }
    static void field(Object target,String name,Object value) throws Exception {
        Field f=Entity.class.getDeclaredField(name);f.setAccessible(true);f.set(target,value);
    }
    static Fixture fixture(NativeEntityGeometry world,Arena scope) throws Exception {
        return fixture(world,scope,Fixture.class);
    }
    static Fixture fixture(NativeEntityGeometry world,Arena scope,Class<? extends Fixture> type) throws Exception {
        Class<?> unsafe=Class.forName("sun.misc.Unsafe");Field f=unsafe.getDeclaredField("theUnsafe");f.setAccessible(true);
        var entity=(Fixture)unsafe.getMethod("allocateInstance",Class.class).invoke(f.get(null),type);
        entity.events=new ArrayList<>();field(entity,"position",Vec3.ZERO);field(entity,"blockPosition",BlockPos.ZERO);
        field(entity,"chunkPosition",ChunkPos.ZERO);field(entity,"levelCallback",EntityInLevelCallback.NULL);
        entity.firstTick=true;
        field(entity,"nativeGeometry",world.create(scope,0,0,0,new AABB(-.2,-.2,-.2,.2,.2,.2)));
        entity.setBoundingBox(new AABB(-.2,-.2,-.2,.2,.2,.2));return entity;
    }
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    @Test void removalRetiresCameraMembershipWhileRetainedEntityStaysQueryable() {
        var entity=new Fixture();entity.events=new ArrayList<>();
        var body=entity.nativeCullingGeometry();var world=body.world();
        entity.setBoundingBox(new AABB(-.2,-.2,-.2,.2,.2,.2));
        var frustum=new Frustum(new Matrix4f(),new Matrix4f());
        assertTrue(frustum.isVisibleEntity(body));assertEquals(1,world.count());
        var reason=Entity.RemovalReason.UNLOADED_TO_CHUNK;
        entity.setRemoved(reason);assertTrue(entity.isRemoved());assertSame(reason,entity.getRemovalReason());
        assertEquals(0,world.count());assertFalse(body.indexed());assertTrue(frustum.isVisibleEntity(body));
        long revision=world.revision();
        entity.setPosRaw(100,0,0);entity.setBoundingBox(new AABB(99,0,0,101,1,1));
        assertEquals(revision,world.revision());assertFalse(frustum.isVisibleEntity(body));
        entity.setRemoved(Entity.RemovalReason.KILLED);assertSame(reason,entity.getRemovalReason());
        var next=new NativeEntityGeometry();var level=mock(Level.class);when(level.nativeEntityGeometry()).thenReturn(next);
        entity.transfer(level);assertEquals(0,next.count());assertFalse(body.indexed());
        entity.revive();assertFalse(entity.isRemoved());assertNull(entity.getRemovalReason());
        assertTrue(body.indexed());assertEquals(1,next.count());assertSame(body,entity.nativeCullingGeometry());
        assertFalse(frustum.isVisibleEntity(body));
        entity.setRemoved(Entity.RemovalReason.DISCARDED);assertEquals(0,next.count());
    }
    @Test void nullRemovalKeepsOriginalFailureAndDoesNotRetireMembership() {
        var entity=new Fixture();var body=entity.nativeCullingGeometry();
        assertThrows(NullPointerException.class,()->entity.setRemoved(null));
        assertFalse(entity.isRemoved());assertNull(entity.getRemovalReason());assertEquals(1,body.world().count());
        entity.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        assertDoesNotThrow(()->entity.setRemoved(null));
        assertSame(Entity.RemovalReason.UNLOADED_TO_CHUNK,entity.getRemovalReason());assertEquals(0,body.world().count());
    }
    @Test void actualConstructionWithoutLevelStillHasPrimaryComponents() {
        var entity=new Fixture();var body=entity.nativeCullingGeometry();assertNotNull(body);
        assertEquals(1,body.world().count());var oldWorld=body.world();
        entity.transfer(null);assertNull(entity.level());assertEquals(0,oldWorld.count());
        assertEquals(1,body.world().count());
    }
    @Test void movementPublishesBeforeNestedOrThrowingCallbacksAndTransfer() throws Exception {
        var a=new NativeEntityGeometry();var b=new NativeEntityGeometry();
        try(var scope=Arena.ofConfined()) {
            var entity=fixture(a,scope);var body=entity.nativeCullingGeometry();
            var callback=mock(EntityInLevelCallback.class);
            doAnswer(call->{
                entity.events.add("move");assertEquals(body.position(0),entity.getX());
                assertEquals(body.blockX(),entity.blockPosition().getX());
                if(entity.getX()==17.0)entity.setPosRaw(-17.0,65.0,32.0);
                return null;
            }).when(callback).onMove();field(entity,"levelCallback",callback);
            entity.setPosRaw(17,64,32);assertEquals(List.of("move","move"),entity.events);assertEquals(-2,entity.chunkPosition().x);
            var stop=new IllegalStateException("move callback");doThrow(stop).when(callback).onMove();
            assertSame(stop,assertThrows(IllegalStateException.class,()->entity.setPosRaw(33,64,32)));
            assertEquals(33,body.position(0));assertEquals(33,entity.getX());assertEquals(2,entity.chunkPosition().x);
            var level=mock(Level.class);when(level.nativeEntityGeometry()).thenReturn(b);entity.transfer(level);
            assertSame(level,entity.level());assertSame(b,body.world());assertEquals(0,a.count());assertEquals(1,b.count());
        }
        assertEquals(0,b.count());
    }
    @Test void stockHookMutationUsesBoundsCapturedBeforeHook() throws Exception {
        var world=new NativeEntityGeometry();
        try(var scope=Arena.ofConfined();var registry=mockStatic(HookRegistry.class);
            var sodium=mockStatic(SodiumClientMod.class);var terrain=mockStatic(RustGalWholeFrameTerrainSource.class)) {
            var entity=fixture(world,scope);var renderer=new Renderer();var frustum=new Frustum(new Matrix4f(),new Matrix4f());
            registry.when(HookRegistry::getEntityRendererHooks).thenReturn(List.of(new RustGalEntityCullingHook()));
            sodium.when(SodiumClientMod::options).thenReturn(SodiumGameOptions.defaults());
            terrain.when(()->RustGalWholeFrameTerrainSource.isEntityVisible(renderer,entity)).thenAnswer(call->{
                entity.events.add("hook");entity.setBoundingBox(new AABB(100,100,100,101,101,101));return null;
            });
            assertTrue(renderer.shouldRender(entity,frustum,0,0,0));
            assertEquals(List.of("distance","affected","hook"),entity.events);
            assertFalse(frustum.isVisibleEntity(entity.nativeCullingGeometry()),"Next query observes mutated bounds");
        }
    }
    @Test void rendererCallbacksReturningPrimaryBoundsUseTheRetainedQuery() throws Exception {
        var world=new NativeEntityGeometry();
        try(var scope=Arena.ofConfined();var registry=mockStatic(HookRegistry.class)) {
            registry.when(HookRegistry::getEntityRendererHooks).thenReturn(List.of());
            var entity=fixture(world,scope);var renderer=new CustomRenderer();
            assertTrue(renderer.shouldRender(entity,new Frustum(new Matrix4f(),new Matrix4f()),0,0,0));
            assertEquals(List.of("distance","affected","custom bounds"),entity.events);
            assertNotEquals(0,world.queryStamp(),"Returned identity, not declaring class, admits the primary consumer");
        }
    }
    @Test void boundsMutationAndDerivedResultsUseTheOriginalCapturedValues() throws Exception {
        var world=new NativeEntityGeometry();
        try(var scope=Arena.ofConfined();var registry=mockStatic(HookRegistry.class)) {
            registry.when(HookRegistry::getEntityRendererHooks).thenReturn(List.of());
            var entity=fixture(world,scope);var frustum=new Frustum(new Matrix4f(),new Matrix4f());
            assertTrue(new MutatingBoundsRenderer().shouldRender(entity,frustum,0,0,0));
            assertEquals(List.of("distance","affected","bounds mutation"),entity.events);
            assertEquals(0,world.queryStamp());
            entity.setBoundingBox(new AABB(-.2,-.2,-.2,.2,.2,.2));entity.events.clear();
            assertFalse(new DerivedBoundsRenderer().shouldRender(entity,frustum,0,0,0));
            assertEquals(List.of("distance","affected","derived bounds"),entity.events);
            assertEquals(0,world.queryStamp());
        }
    }
    @Test void livingEquipmentCallbacksKeepBothInvocationsAndPreHookBounds() throws Exception {
        var world=new NativeEntityGeometry();
        try(var scope=Arena.ofConfined();var registry=mockStatic(HookRegistry.class);
            var sodium=mockStatic(SodiumClientMod.class);var terrain=mockStatic(RustGalWholeFrameTerrainSource.class)) {
            var entity=mock(LivingEntity.class,CALLS_REAL_METHODS);var renderer=new LivingRenderer();
            field(entity,"nativeGeometry",world.create(scope,0,0,0,new AABB(-.2,-.2,-.2,.2,.2,.2)));
            entity.setBoundingBox(new AABB(-.2,-.2,-.2,.2,.2,.2));
            doReturn(true).when(entity).shouldRender(0,0,0);int[] equipmentCalls={0};
            doAnswer(call->{
                if(++equipmentCalls[0]==2)entity.setBoundingBox(new AABB(100,100,100,101,101,101));
                return ItemStack.EMPTY;
            }).when(entity).getItemBySlot(EquipmentSlot.HEAD);
            registry.when(HookRegistry::getEntityRendererHooks).thenReturn(List.of(new RustGalEntityCullingHook()));
            sodium.when(SodiumClientMod::options).thenReturn(SodiumGameOptions.defaults());
            terrain.when(()->RustGalWholeFrameTerrainSource.isEntityVisible(renderer,entity)).thenAnswer(call->{renderer.getBoundingBoxForCulling(entity);return null;});
            assertTrue(renderer.shouldRender(entity,new Frustum(new Matrix4f(),new Matrix4f()),0,0,0));
            assertEquals(2,equipmentCalls[0]);
            entity.setBoundingBox(new AABB(-.2,-.2,-.2,.2,.2,.2));
            registry.when(HookRegistry::getEntityRendererHooks).thenReturn(List.of());
            assertTrue(renderer.shouldRender(entity,new Frustum(new Matrix4f(),new Matrix4f()),0,0,0));
            assertNotEquals(0,world.queryStamp(),"Ordinary living renderer now consumes the retained components");
            long stamp=world.queryStamp();doReturn(new ItemStack(Items.DRAGON_HEAD)).when(entity).getItemBySlot(EquipmentSlot.HEAD);
            assertTrue(renderer.shouldRender(entity,new Frustum(new Matrix4f(),new Matrix4f()),0,0,0));
            assertEquals(stamp,world.queryStamp(),"Derived dragon-head bounds retain their exact original preparation");
        }
    }
    @Test void customBoundsHooksAndFrustumsKeepOriginalCallbacks() throws Exception {
        var world=new NativeEntityGeometry();
        try(var scope=Arena.ofConfined();var registry=mockStatic(HookRegistry.class)) {
            var entity=fixture(world,scope);var renderer=new CustomRenderer();var frustum=mock(Frustum.class);
            var hook=new EntityRendererHooks() {
                @Override public <T extends Entity,S extends EntityRenderState> Boolean onEntityFrustumCheck(EntityRenderer<T,S> r,T e,Frustum f,AABB bounds) {
                    entity.events.add("hook");NativeEntityGeometryTest.sameBounds(new AABB(-.7,-.7,-.7,.7,.7,.7),bounds);return null;
                }
            };
            registry.when(HookRegistry::getEntityRendererHooks).thenReturn(List.of(hook));
            when(frustum.isVisible(any())).thenAnswer(call->{entity.events.add("frustum");return true;});
            assertTrue(renderer.shouldRender(entity,frustum,0,0,0));
            assertEquals(List.of("distance","affected","custom bounds","hook","frustum"),entity.events);
            entity.setBoundingBox(null);assertNull(entity.getBoundingBox());assertNull(entity.nativeCullingGeometry());
        }
    }
    @Test void leashCallbacksUsePrecedingCaptureEvenWhenHolderLookupMutatesBounds() throws Exception {
        var world=new NativeEntityGeometry();
        try(var scope=Arena.ofConfined();var registry=mockStatic(HookRegistry.class)) {
            registry.when(HookRegistry::getEntityRendererHooks).thenReturn(List.of());
            var entity=(LeashedFixture)fixture(world,scope,LeashedFixture.class);var holder=fixture(world,scope);entity.holder=holder;
            entity.setBoundingBox(new AABB(100,0,0,101,1,1));holder.setBoundingBox(new AABB(200,0,0,201,1,1));
            var dispatcher=mock(EntityRenderDispatcher.class);var renderer=new Renderer();
            var holderRenderer=new CustomRenderer();doReturn(holderRenderer).when(dispatcher).getRenderer(holder);
            var dispatcherField=EntityRenderer.class.getDeclaredField("entityRenderDispatcher");dispatcherField.setAccessible(true);dispatcherField.set(renderer,dispatcher);
            assertFalse(renderer.shouldRender(entity,new Frustum(new Matrix4f(),new Matrix4f()),0,0,0));
            assertEquals(List.of("distance","affected","holder"),entity.events);
            assertEquals(List.of("custom bounds"),holder.events);
        }
    }
}

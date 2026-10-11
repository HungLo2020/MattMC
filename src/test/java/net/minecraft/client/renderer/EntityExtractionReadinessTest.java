package net.minecraft.client.renderer;

import java.lang.reflect.*;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.client.*;
import net.minecraft.client.dev.DeterministicCameraCapture;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntityExtractionReadinessTest {
    static class CustomRenderer extends LevelRenderer {
        CustomRenderer() { super(null,null,null,null,null,null); }
    }
    static class CustomWorld extends ClientLevel {
        CustomWorld() { super(null,null,null,null,0,0,null,false,0L,0); }
    }
    static void field(Object target,Class<?> type,String name,Object value) throws Exception {
        Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(target,value);
    }
    @BeforeAll static void bootstrap() {SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    @SuppressWarnings("unchecked")
    private void extract(Class<? extends LevelRenderer> rendererClass,Class<? extends ClientLevel> worldClass,
        boolean probe,boolean query,boolean throwing) throws Exception {
        var world=mock(worldClass);var minecraft=mock(Minecraft.class);var options=mock(Options.class);
        when(world.tickRateManager()).thenReturn(mock(TickRateManager.class));
        var scaling=mock(OptionInstance.class);when(scaling.get()).thenReturn(1.0);
        when(options.entityDistanceScaling()).thenReturn(scaling);
        field(minecraft,Minecraft.class,"level",world);field(minecraft,Minecraft.class,"options",options);
        var entity=probe ? mock(FallingBlockEntity.class) : mock(Entity.class);entity.tickCount=1;
        when(entity.blockPosition()).thenReturn(new BlockPos(0,64,0));
        when(world.entitiesForRendering()).thenReturn(List.of(entity));
        var dispatcher=mock(EntityRenderDispatcher.class);var camera=mock(Camera.class);var frustum=mock(Frustum.class);
        when(camera.getPosition()).thenReturn(Vec3.ZERO);
        when(dispatcher.shouldRender(entity,frustum,0.0,0.0,0.0)).thenReturn(true);
        var state=new EntityRenderState();when(dispatcher.extractEntity(entity,0.0F)).thenReturn(state);
        var renderer=mock(rendererClass,CALLS_REAL_METHODS);doReturn(false).when(renderer).shouldShowEntityOutlines();
        field(renderer,LevelRenderer.class,"minecraft",minecraft);field(renderer,LevelRenderer.class,"level",world);
        field(renderer,LevelRenderer.class,"entityRenderDispatcher",dispatcher);
        var stop=new IllegalStateException("readiness callback");
        if(throwing)doThrow(stop).when(renderer).isSectionCompiled(any());
        else doReturn(true).when(renderer).isSectionCompiled(any());
        var frame=new LevelRenderState();var delta=mock(DeltaTracker.class);
        var method=LevelRenderer.class.getDeclaredMethod("extractVisibleEntities",Camera.class,Frustum.class,DeltaTracker.class,LevelRenderState.class);method.setAccessible(true);
        double scale=Entity.getViewScale();
        try(var capture=mockStatic(DeterministicCameraCapture.class)) {
            capture.when(DeterministicCameraCapture::isFallingBlockSequenceActive).thenReturn(probe);
            if(throwing) {
                var failure=assertThrows(InvocationTargetException.class,()->method.invoke(renderer,camera,frustum,delta,frame));
                assertSame(stop,failure.getCause());assertTrue(frame.entityRenderStates.isEmpty());
                verify(dispatcher,never()).extractEntity(any(Entity.class),anyFloat());
            } else {
                method.invoke(renderer,camera,frustum,delta,frame);
                assertEquals(List.of(state),frame.entityRenderStates);
                verify(dispatcher).extractEntity(entity,0.0F);
                if(probe)capture.verify(()->DeterministicCameraCapture.recordFallingBlockExtractionProbe(true,true,true));
            }
            verify(renderer,query ? times(1) : never()).isSectionCompiled(any());
            verify(world,query ? times(1) : never()).isOutsideBuildHeight(64);
            verify(entity).blockPosition(); // Keep existing custom entity getters.
        } finally {Entity.setViewScale(scale);}
    }
    @Test void ordinarySemanticExtractionDoesNotQueryUnusedReadiness() throws Exception {
        extract(LevelRenderer.class,ClientLevel.class,false,false,false);
    }
    @Test void activeFallingBlockProbeStillObservesCompiledSectionAndExtractedState() throws Exception {
        extract(LevelRenderer.class,ClientLevel.class,true,true,false);
    }
    @Test void customRendererAndWorldKeepTheirReadinessCallbacks() throws Exception {
        extract(CustomRenderer.class,ClientLevel.class,false,true,false);
        extract(LevelRenderer.class,CustomWorld.class,false,true,false);
    }
    @Test void customCallbackFailureKeepsOriginalUnextractedPrefix() throws Exception {
        extract(CustomRenderer.class,ClientLevel.class,false,true,true);
    }
}

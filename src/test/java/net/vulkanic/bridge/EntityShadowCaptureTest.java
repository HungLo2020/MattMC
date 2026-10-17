package net.vulkanic.bridge;

import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.hooks.HookRegistry;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntityShadowCaptureTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }
    static final class Renderer extends EntityRenderer<Entity,EntityRenderState> {
        final List<String> events; final Entity holder;
        AABB box=new AABB(-1,-2,-3,1,2,3);
        Renderer(EntityRendererProvider.Context context,List<String> events,Entity holder) {super(context);this.events=events;this.holder=holder;}
        @Override public EntityRenderState createRenderState(){return new EntityRenderState();}
        @Override protected boolean affectedByCulling(Entity e){events.add("affected");return true;}
        @Override public AABB getBoundingBoxForCulling(Entity e){events.add(e==holder?"holder bounds":"bounds");return e==holder?new AABB(10,11,12,13,14,15):box;}
    }
    @Test void nativeCapturePreservesCallbackOrderAndCapturedBoundsThroughMutation() {
        var events=new ArrayList<String>();
        var entity=mock(Entity.class,withSettings().extraInterfaces(Leashable.class));var holder=mock(Entity.class);
        var dispatcher=mock(EntityRenderDispatcher.class);var context=mock(EntityRendererProvider.Context.class);
        when(context.getEntityRenderDispatcher()).thenReturn(dispatcher);
        var renderer=new Renderer(context,events,holder);doReturn(renderer).when(dispatcher).getRenderer(holder);
        var level=mock(Level.class);when(entity.level()).thenReturn(level);when(level.isOutsideBuildHeight(anyInt())).thenAnswer(i->{events.add("height");return true;});
        when(entity.hasIndirectPassenger(any())).thenAnswer(i->{events.add("passenger");return false;});
        when(entity.blockPosition()).thenAnswer(i->{events.add("block");return BlockPos.ZERO;});
        when(entity.shouldRender(anyDouble(),anyDouble(),anyDouble())).thenAnswer(i->{events.add("distance");renderer.box=new AABB(100,100,100,101,101,101);return true;});
        when(((Leashable)entity).getLeashHolder()).thenAnswer(i->{events.add("holder");return holder;});
        try(var client=mockStatic(Minecraft.class);var hooks=mockStatic(HookRegistry.class);var candidates=new EntityShadowCandidates()) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            hooks.when(HookRegistry::entityRendererHooksAffectShadowPass).thenAnswer(i->{events.add("hooks");return false;});
            var expected=renderer.copyEntityCulling(entity,Vec3.ZERO);var sequence=List.copyOf(events);
            events.clear();renderer.box=new AABB(-1,-2,-3,1,2,3);
            var query=mock(EntityShadowQuery.class);
            doAnswer(i->{candidates.add(i.getArgument(0),i.getArgument(1),i.getArgument(2));return null;}).when(query).add(anyInt(),any(),any());
            renderer.captureEntityShadowCandidate(entity,Vec3.ZERO,query);
            assertEquals(List.of("passenger","affected","bounds","block","distance","height","hooks","holder","holder","holder bounds"),sequence);
            assertEquals(sequence,events);assertEquals(1,candidates.size());
            var data=EntityShadowCandidatesTest.slots(candidates);
            assertEquals(expected.flags(),data.get(ValueLayout.JAVA_INT,0));
            assertEquals(expected.bounds().minX(),data.get(ValueLayout.JAVA_DOUBLE,8));
            assertEquals(-1.5,data.get(ValueLayout.JAVA_DOUBLE,8));
            assertEquals(expected.leashHolderBounds().maxZ(),data.get(ValueLayout.JAVA_DOUBLE,96));
            events.clear();when(((Leashable)entity).getLeashHolder()).thenThrow(new IllegalStateException("holder callback"));
            assertThrows(IllegalStateException.class,()->renderer.captureEntityShadowCandidate(entity,Vec3.ZERO,query));
            assertEquals(1,candidates.size());
        }
    }
}

package com.seibel.distanthorizons.core.render.renderer.generic;

import com.seibel.distanthorizons.api.objects.math.DhApiVec3d;
import java.util.List;
import java.util.ArrayList;
import net.vulkanic.world.NativeDhCloudGroupState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeCloudApiProjectionTest {
    @Test void apiOverridesAndReplacedCallbacksPreserveTheOriginalOriginPath() {
        var state=new NativeDhCloudGroupState(2048,0,0,0);
        state.prepare(1000,6,0,70,0,0,0,1,128,320);
        var group=new RenderableBoxGroup("test:Clouds",new DhApiVec3d(0,0,0),List.of(),true);
        var calls=new ArrayList<String>();
        group.setPreRenderFunc(p->{calls.add("pre");group.publishNativeCloud(state);});
        group.setPostRenderFunc(p->calls.add("post"));
        group.preRender(null);
        assertSame(state,group.nativeCloudState());
        assertEquals(state.originX(),group.getOriginBlockPos().x);
        group.setOriginBlockPos(new DhApiVec3d(1,2,3));
        assertNull(group.nativeCloudState());
        assertEquals(1,group.getOriginBlockPos().x);
        group.postRender(null);
        assertEquals(List.of("pre","post"),calls);
        group.preRender(null);
        assertSame(state,group.nativeCloudState());
        group.setPreRenderFunc(p->calls.add("replacement"));
        group.preRender(null);
        assertNull(group.nativeCloudState(),"a replacement callback must not reuse an old native pose");
        assertEquals(state.originX(),group.getOriginBlockPos().x,"replacing a hook keeps the public origin");
    }
}

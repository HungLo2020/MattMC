package net.minecraft.client.renderer.item;
import net.sodium.client.render.frapi.mesh.MutableMeshImpl;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemLayerLazyMeshTest {
    @Test void unusedLayersAndExtentTraversalDoNotAllocateMeshesButApiIdentitySurvivesClear() throws Exception {
        var state=new ItemStackRenderState();var layer=state.newLayer();
        var field=layer.getClass().getDeclaredField("mutableMesh");field.setAccessible(true);
        assertNull(field.get(layer));
        state.visitExtents(v->fail("no extents"));
        assertNull(field.get(layer));
        state.clear();assertNull(field.get(layer));
        state.newLayer();MutableMeshImpl mesh=layer.fabric_getMutableMesh();
        mesh.emitter().emit();assertEquals(1,mesh.size());
        state.clear();assertSame(mesh,layer.fabric_getMutableMesh());assertEquals(0,mesh.size());
        assertSame(layer,state.newLayer());assertSame(mesh,layer.fabric_getMutableMesh());
    }
    @Test void subclassGetterCallbacksAndNullFailuresKeepTheirOriginalOrder() throws Exception {
        var state=new ItemStackRenderState();state.newLayer();int[] calls={0};
        var custom=state.new LayerRenderState() {
            @Override public MutableMeshImpl fabric_getMutableMesh(){calls[0]++;return null;}
        };
        var field=ItemStackRenderState.class.getDeclaredField("layers");field.setAccessible(true);
        field.set(state,new ItemStackRenderState.LayerRenderState[]{custom});
        assertThrows(NullPointerException.class,()->state.visitExtents(v->fail("no extents")));
        assertEquals(1,calls[0]);
    }

}

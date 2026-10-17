package net.vulkanic.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.render.state.GuiItemRenderState;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.vulkanic.bridge.VulkanicGalBridge.*;
import org.joml.Matrix3x2f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuiFlatItemMeshCollectorTest {
    @Test void cachedFlatLayersTransferOwnershipAndRefreshOnlyFrameFacts() throws Exception {
        var layerType = Class.forName("net.vulkanic.gui.GuiFlatItemMeshCollector$FlatLayer");
        var constructor = layerType.getDeclaredConstructors()[0]; constructor.setAccessible(true);
        var vertex = new GuiMeshVertexRecord(new float[]{0,0,0}, new float[]{0,0}, new float[]{0,0}, -1, 0);
        var vertices = List.of(vertex, vertex, vertex, vertex);
        var indices = List.of(0,1,2,2,3,0);
        float[] transform = {1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};
        Object base = constructor.newInstance(2,17L,transform,vertices,indices,false,false,null);
        Object glint = constructor.newInstance(4,18L,transform,vertices,indices,true,true,null);
        var topologyType = Class.forName("net.vulkanic.gui.GuiFlatItemMeshCollector$FlatTopology");
        var topologyConstructor = topologyType.getDeclaredConstructor(List.class,List.class);
        topologyConstructor.setAccessible(true);
        Object topology = topologyConstructor.newInstance(List.of(base,glint),List.of());
        var instantiate = GuiFlatItemMeshCollector.class.getDeclaredMethod("instantiate",GuiItemRenderState.class,
            int.class,int.class,int.class,int.class,topologyType,StandardItemFoilRecord.class,GuiItemCacheRecord.class);
        instantiate.setAccessible(true);
        var item = new GuiItemRenderState("flat",new Matrix3x2f(),new TrackingItemStackRenderState(),12,34,null);
        var cache = new GuiItemCacheRecord(19,true,true);
        var first = (GuiFlatItemMeshCollector.Snapshot)instantiate.invoke(null,item,320,180,2,420,topology,
            new StandardItemFoilRecord(17,0.25,0.5F),cache);
        assertEquals(1,first.batches().size());
        var owner = first.batches().getFirst().nativeTemplate(); assertNotNull(owner);
        try {
            assertEquals(2,owner.batchCount()); assertEquals(8,owner.vertexCount());
            assertArrayEquals(new long[]{17,18},owner.assetIds());
            var layers = topologyType.getDeclaredField("layers"); layers.setAccessible(true);
            assertTrue(((List<?>)layers.get(topology)).isEmpty(),"Java geometry must be released after native retention");
            var moved = new GuiItemRenderState("moved",new Matrix3x2f().translation(3,5),
                new TrackingItemStackRenderState(),24,48,new net.minecraft.client.gui.navigation.ScreenRectangle(1,2,100,120));
            var next = (GuiFlatItemMeshCollector.Snapshot)instantiate.invoke(null,moved,640,360,2,430,topology,
                new StandardItemFoilRecord(99,0.5,0.75F),cache);
            var batch = next.batches().getFirst();
            assertSame(owner,batch.nativeTemplate()); assertEquals(99,batch.layerFoils()[0].clockMillis());
            assertEquals(17,first.batches().getFirst().layerFoils()[0].clockMillis());
            assertEquals(24,batch.left()); assertEquals(48,batch.top()); assertEquals(640,batch.guiWidth());
            assertArrayEquals(new float[]{1,0,0,1,3,5},batch.guiPose());
            assertEquals(1,batch.clipMode()); assertEquals(100,batch.clipWidth());
            assertTrue(batch.vertices().isEmpty()); assertTrue(batch.indices().isEmpty());
        } finally { owner.close(); }
    }

    @Test void foilPresenceTracksResolvedLayersWithoutCreatingSemanticSnapshots() {
        var state = new TrackingItemStackRenderState();
        assertFalse(state.hasFoil());
        state.newLayer().setFoilType(net.minecraft.client.renderer.item.ItemStackRenderState.FoilType.STANDARD);
        assertTrue(state.hasFoil());
        state.clear();
        assertFalse(state.hasFoil());
    }

    @Test void modelIdentitySnapshotIsReusedUntilTheTrackedStateChanges() {
        var state = new TrackingItemStackRenderState();
        Object firstElement = new Object();
        state.appendModelIdentityElement(firstElement);
        Object first = state.getModelIdentity();
        assertSame(first, state.getModelIdentity());
        assertEquals(List.of(firstElement), first);

        Object secondElement = new Object();
        state.appendModelIdentityElement(secondElement);
        Object second = state.getModelIdentity();
        assertNotSame(first, second);
        assertEquals(List.of(firstElement, secondElement), second);

        state.clear();
        assertEquals(List.of(), state.getModelIdentity());
        assertNotSame(second, state.getModelIdentity());
    }

    @Test void nonFoilMeshPreservesAllFourAuthoredCornersAndRequestsNativeItemLighting() {
        var item=new GuiItemRenderState("non-affine",new Matrix3x2f(),new TrackingItemStackRenderState(),0,0,null);
        float[] transform={1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};
        var vertices=new ArrayList<GuiMeshVertexRecord>();
        for (float[] point:List.of(new float[]{0,0,0.5F},new float[]{1,0,0.5F},
                new float[]{0.6F,1,0.5F},new float[]{0,1,0.5F})) {
            vertices.add(new GuiMeshVertexRecord(point,new float[]{0.4F,0.6F},
                new float[]{0.2F,0.8F},0x8040ff20,0x007f0000));
        }
        var batch=GuiFlatItemMeshCollector.batch(item,320,180,3,420,0,2,99,transform,vertices,null);
        assertEquals(vertices,batch.vertices(),"never reconstruct the fourth corner as an affine rectangle");
        assertEquals(List.of(0,1,2,2,3,0),batch.indices());
        assertNull(batch.itemFoil());
        assertEquals(1,batch.lightingMode());
        assertEquals(3,batch.itemRasterScale(),"native item raster requires the explicit frame lightmap");
        assertEquals(0,batch.renderWidth(),"Rust chooses the raster extent");
        assertEquals(0x8040ff20,batch.vertices().getFirst().colorArgb(),"Java must not compensate RGB");
    }

    @Test void projectedFoilStillRejectsMissingGeometryBeforeResourceAccess() throws Exception {
        var state=new TrackingItemStackRenderState();
        var context=net.minecraft.client.renderer.item.ItemStackRenderState.class.getDeclaredField("displayContext");
        context.setAccessible(true);
        context.set(state,net.minecraft.world.item.ItemDisplayContext.GUI);
        state.newLayer().setFoilType(net.minecraft.client.renderer.item.ItemStackRenderState.FoilType.SPECIAL);
        var item=new GuiItemRenderState("projected",new Matrix3x2f(),state,0,0,null);
        var error=assertThrows(IllegalArgumentException.class,()->GuiFlatItemMeshCollector.collect(
            item,320,180,2,420,new StandardItemFoilRecord(0,0,0.5F)));
        assertEquals("unsupported flat mesh layer",error.getMessage());
    }

    @Test void batchCopiesRawGeometryScalePoseAndFoilWithoutJavaRasterSetup() {
        var item=new GuiItemRenderState("test",new Matrix3x2f().translation(3,5),new TrackingItemStackRenderState(),12,34,null);
        float[] transform={1,0,0,0, 0,1,0,0, 0,0,1,0, -0.5F,-0.5F,-0.5F,1};
        var vertex=new GuiMeshVertexRecord(new float[]{0.25F,0.75F,0.5F},new float[]{0.4F,0.6F},
            new float[]{0.1F,0.9F},0x80123456,0x007f0000);
        var vertices=new ArrayList<>(List.of(vertex,vertex,vertex,vertex));
        var foil=new StandardItemFoilRecord(12345,0.5,0.5F);
        var batch=GuiFlatItemMeshCollector.batch(item,320,180,2,420,1,4,99,transform,vertices,foil);
        transform[12]=99; vertices.clear();
        assertEquals(2,batch.itemRasterScale());
        assertEquals(0,batch.renderWidth()); assertEquals(0,batch.renderHeight()); assertEquals(0,batch.guardPixels());
        assertArrayEquals(new float[]{1,0,0,1,3,5},batch.guiPose());
        assertEquals(12,batch.left()); assertEquals(34,batch.top());
        assertEquals(28,batch.right()); assertEquals(50,batch.bottom());
        assertEquals(-0.5F,batch.modelTransform()[12]);
        assertEquals(vertex,batch.vertices().getFirst()); assertEquals(foil,batch.itemFoil());
        assertEquals(0x80123456,batch.vertices().getFirst().colorArgb());
        assertEquals(0x007f0000,batch.vertices().getFirst().normalPacked());
    }

    @Test void incompleteOrNonGuiItemsRejectWithoutAccessingRendererState() {
        var item=new GuiItemRenderState("empty",new Matrix3x2f(),new TrackingItemStackRenderState(),0,0,null);
        assertThrows(IllegalArgumentException.class,()->GuiFlatItemMeshCollector.collect(item,320,180,2,420,new StandardItemFoilRecord(0,0,0.5F)));
    }

    @Test void manyFacesUseOneIndexedMeshWithoutDroppingOrReorderingQuads() {
        var item=new GuiItemRenderState("many-faces",new Matrix3x2f(),new TrackingItemStackRenderState(),0,0,null);
        float[] transform={1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};
        var vertices=new ArrayList<GuiMeshVertexRecord>();
        for(int i=0;i<320;i++) vertices.add(new GuiMeshVertexRecord(new float[]{i,0,0},
            new float[]{0,0},new float[]{0,0},-1,0));
        var batch=GuiFlatItemMeshCollector.batch(item,320,180,2,420,0,2,99,transform,vertices,null);
        assertEquals(320,batch.vertices().size());
        assertEquals(480,batch.indices().size());
        for(int face=0;face<80;face++) {
            int i=face*4;
            assertEquals(List.of(i,i+1,i+2,i+2,i+3,i),batch.indices().subList(face*6,face*6+6));
            assertEquals(i,batch.vertices().get(i).position()[0]);
        }
        assertThrows(IllegalArgumentException.class,()->GuiFlatItemMeshCollector.batch(item,320,180,2,420,0,2,99,
            transform,vertices.subList(0,319),null));
        assertThrows(IllegalArgumentException.class,()->GuiFlatItemMeshCollector.batch(item,320,180,2,420,0,2,99,
            transform,java.util.Collections.nCopies(65_540,vertices.getFirst()),null));
    }
}

package net.vulkanic.bridge;

import java.lang.foreign.*;
import java.util.List;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.vulkanic.world.NativeItemLayerTransform;
import org.junit.jupiter.api.Test;
import static net.vulkanic.bridge.VulkanicGalBridge.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeGuiItemTemplateTest {
    static GuiMeshBatchRecord prototype(int layer, boolean foil) {
        var vertices = List.of(
            new GuiMeshVertexRecord(new float[]{0,0,0}, new float[]{0,0}, new float[]{0,0}, -1, 0, 6, 0),
            new GuiMeshVertexRecord(new float[]{1,0,0}, new float[]{1,0}, new float[]{1,0}, -1, 0, 6, 0),
            new GuiMeshVertexRecord(new float[]{0,1,0}, new float[]{0,1}, new float[]{0,1}, -1, 0, 6, 0));
        return new GuiMeshBatchRecord(420, layer, foil ? 4 : 1, foil ? 1 : GUI_MESH_LIGHTING_INVENTORY_BLOCK,
            7, 9, foil ? 0.1F : 0, null, new float[]{1,0,0,1,0,0},
            0,0,16,16,320,180,0,0,0,0,0,0,0,0,vertices,List.of(0,1,2),
            foil ? new StandardItemFoilRecord(3,1,1) : null, 0, null,
            new GuiBlockItemRasterRecord(2, new double[]{0,0,0,1,1,1}), null,
            NativeItemLayerTransform.capture(ItemTransform.NO_TRANSFORM, false));
    }
    static GuiMeshBatchRecord instance(NativeGuiItemTemplate owner, StandardItemFoilRecord[] foils) {
        return GuiMeshBatchRecord.nativeItem(owner, foils, 430, 91, new float[]{1,0,0,1,12,14},
            4,8,20,24,640,360,1,2,3,120,130,new GuiItemCacheRecord(17, foils.length > 1, true));
    }

    @Test void retainedLayersEncodeOneInstanceAndIndependentFoilFacts() {
        try (var owner = createGuiItemTemplate(List.of(prototype(0,false),prototype(1,true),prototype(2,true)),
                new int[]{-1,1,2},3); var input = Arena.ofConfined()) {
            assertNotNull(owner);
            var foils = new StandardItemFoilRecord[]{null, new StandardItemFoilRecord(17,0.25F,0.5F),
                new StandardItemFoilRecord(19,0.75F,1)};
            var item = instance(owner,foils).withSequence(92).withItemFoil(null).withDecalFoil(null);
            foils[1] = null;
            assertSame(owner,item.nativeTemplate());
            assertEquals(17,item.layerFoils()[1].clockMillis());
            item.layerFoils()[1] = null;
            assertEquals(17,item.layerFoils()[1].clockMillis());
            assertEquals(3,owner.batchCount()); assertEquals(9,owner.vertexCount()); assertEquals(9,owner.indexCount());
            assertArrayEquals(new long[]{7},owner.assetIds());
            assertTrue(item.vertices().isEmpty()); assertTrue(item.indices().isEmpty()); assertNull(item.modelTransform());
            var wire = encodeGuiMeshBatches(input,List.of(item),null);
            var layout = Struct.GUI_MESH_BATCH_REQUEST;
            assertEquals(owner.identity(),wire.get(ValueLayout.JAVA_LONG,layout.offset(42)));
            assertEquals(92,wire.get(ValueLayout.JAVA_LONG,layout.offset(6)));
            assertEquals(0,wire.get(ValueLayout.ADDRESS,layout.offset(25)).address());
            assertEquals(0,wire.get(ValueLayout.ADDRESS,layout.offset(26)).address());
            assertEquals(0,wire.get(ValueLayout.JAVA_LONG,layout.offset(40)));
            assertArrayEquals(new float[16],wire.asSlice(layout.offset(9),64).toArray(ValueLayout.JAVA_FLOAT));
            var factLayout = Struct.GUI_ITEM_LAYER_FOIL;
            var facts = wire.get(ValueLayout.ADDRESS,layout.offset(43)).reinterpret(factLayout.byteSize()*3L);
            assertEquals(3,wire.get(ValueLayout.JAVA_LONG,layout.offset(43)+8));
            assertEquals(0,facts.get(ValueLayout.JAVA_INT,factLayout.offset(0)));
            assertEquals(17,facts.get(ValueLayout.JAVA_LONG,factLayout.byteSize()+factLayout.offset(1)));
            assertEquals(19,facts.get(ValueLayout.JAVA_LONG,2L*factLayout.byteSize()+factLayout.offset(1)));
            assertEquals(0.25,facts.get(ValueLayout.JAVA_DOUBLE,factLayout.byteSize()+factLayout.offset(2)));
        }
    }

    @Test void registrationRejectsMalformedSelectorsAndFramePlacement() {
        var source = List.of(prototype(0,false));
        assertThrows(IllegalArgumentException.class, () -> createGuiItemTemplate(source,new int[]{0},1));
        assertThrows(IllegalArgumentException.class, () -> createGuiItemTemplate(source,new int[0],1));
        try (var owner = createGuiItemTemplate(source,new int[]{-1},1)) {
            assertThrows(IllegalArgumentException.class, () -> instance(owner,new StandardItemFoilRecord[0]));
            assertThrows(IllegalArgumentException.class, () -> GuiMeshBatchRecord.nativeItem(owner,
                new StandardItemFoilRecord[1],430,91,new float[]{Float.NaN,0,0,1,0,0},0,0,16,16,320,180,0,0,0,0,0,null));
            assertThrows(IllegalArgumentException.class, () -> GuiMeshBatchRecord.nativeItem(owner,
                new StandardItemFoilRecord[1],430,91,new float[]{1,0,0,1,0,0},0,0,16,16,320,180,1,0,0,321,1,null));
        }
    }

    @Test void releasedIdsAreNotReusedAndClosedLeasesCannotBeEncoded() {
        var source = List.of(prototype(0,false));
        var first = createGuiItemTemplate(source,new int[]{-1},1);
        long id = first.identity();
        var item = instance(first,new StandardItemFoilRecord[1]);
        first.close(); first.close();
        assertThrows(IllegalStateException.class,first::identity);
        try (var input = Arena.ofConfined(); var next = createGuiItemTemplate(source,new int[]{-1},1)) {
            assertNotEquals(id,next.identity());
            assertThrows(IllegalStateException.class, () -> encodeGuiMeshBatches(input,List.of(item),null));
        }
    }

    @Test void workerDecodePinsTemplateLeasesIndependentlyOfMutableCallerLists() throws Exception {
        try (var owner = createGuiItemTemplate(List.of(prototype(0,false)),new int[]{-1},1);
             var bridge = VulkanicGalBridge.create("rust-vulkan")) {
            var records = new java.util.ArrayList<>(List.of(instance(owner,new StandardItemFoilRecord[1])));
            var pin = VulkanicGalBridge.class.getDeclaredMethod("pinItemTransforms",List.class);
            pin.setAccessible(true); pin.invoke(bridge,records);
            records.clear(); System.gc();
            var field = VulkanicGalBridge.class.getDeclaredField("pipelinedItemTemplates"); field.setAccessible(true);
            assertSame(owner,((NativeGuiItemTemplate[])field.get(bridge))[0]);
            pin.invoke(bridge,List.of());
            assertNull(((NativeGuiItemTemplate[])field.get(bridge))[0]);
        }
    }
}

package net.vulkanic.bridge;

import java.lang.foreign.*;
import java.util.List;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.vulkanic.world.NativeItemLayerTransform;
import org.junit.jupiter.api.Test;
import static net.vulkanic.bridge.VulkanicGalBridge.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeGuiItemTransformEncodingTest {
    @Test void nativePoseStaysOwnedThroughRecordCopiesAndEncodesNoJavaMatrix() throws Exception {
        var pose=NativeItemLayerTransform.capture(ItemTransform.NO_TRANSFORM,true);
        var vertices=List.of(new GuiMeshVertexRecord(new float[]{0,0,0},new float[]{0,0},new float[]{0,0},0xffffffff,0,6,0),
            new GuiMeshVertexRecord(new float[]{1,0,0},new float[]{1,0},new float[]{1,0},0xffffffff,0,6,0),
            new GuiMeshVertexRecord(new float[]{0,1,0},new float[]{0,1},new float[]{0,1},0xffffffff,0,6,0));
        var request=new GuiMeshBatchRecord(420,0,1,2,7,9,0,null,new float[]{1,0,0,1,0,0},
            0,0,16,16,320,180,34,34,1,0,0,0,0,0,vertices,List.of(0,1,2),null,0,null,null,null,pose);
        var copy=request.withSequence(11).withItemFoil(null).withDecalFoil(null);
        assertSame(pose,copy.nativeTransform());assertArrayEquals(pose.modelTransform(),copy.modelTransform());
        try(var bridge=VulkanicGalBridge.create("rust-vulkan")) {
            var encode=VulkanicGalBridge.class.getDeclaredMethod("encodeGuiMeshBatches",List.class);encode.setAccessible(true);
            var wire=(MemorySegment)encode.invoke(bridge,List.of(copy));var layout=Struct.GUI_MESH_BATCH_REQUEST;
            assertEquals(pose.ownerAddress(),wire.get(ValueLayout.JAVA_LONG,layout.offset(40)));
            assertEquals(2,wire.get(ValueLayout.JAVA_INT,layout.offset(41)));
            assertArrayEquals(new float[16],wire.asSlice(layout.offset(9),64).toArray(ValueLayout.JAVA_FLOAT));
            System.gc();assertArrayEquals(pose.modelTransform(),copy.modelTransform());
        }
    }
}

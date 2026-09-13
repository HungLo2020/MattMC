package net.vulkanic.bridge;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import org.junit.jupiter.api.Test;
import static net.vulkanic.bridge.VulkanicGalBridge.*;
import static org.junit.jupiter.api.Assertions.*;

class ModelSubmissionOrderTest {
    private static WorldMeshInstanceRecord instance(int stratum) {
        return new WorldMeshInstanceRecord(stratum,17,1,0,1,1,0,-1,
            new float[]{1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1},128,128,0,0,0,0,-1);
    }
    @Test void nestedExtractionScopesRestoreAndRecordsKeepTheirCopiedOrder() {
        assertNull(instance(WORLD_MESH_ENTITY_STRATUM).modelSubmissionOrder());
        beginSemanticModelOrder(-3);
        WorldMeshInstanceRecord outer;
        try {
            outer=instance(WORLD_MESH_ENTITY_STRATUM);
            beginSemanticModelOrder(7);
            try {
                assertEquals(7,instance(WORLD_MESH_ENTITY_STRATUM).modelSubmissionOrder());
                assertNull(instance(60).modelSubmissionOrder());
            } finally { endSemanticModelOrder(); }
            assertEquals(-3,instance(WORLD_MESH_ENTITY_STRATUM).modelSubmissionOrder());
        } finally { endSemanticModelOrder(); }
        assertEquals(-3,outer.modelSubmissionOrder());
        assertNull(instance(WORLD_MESH_ENTITY_STRATUM).modelSubmissionOrder());
        var foil=new StandardItemFoilRecord(1,0.5,0.5F,StandardFoilKind.ARMOR);
        assertEquals(-3,outer.withItemFoil(foil).modelSubmissionOrder());
        assertThrows(IllegalArgumentException.class,()->instance(60).withModelSubmissionOrder(1));
        assertThrows(IllegalStateException.class,VulkanicGalBridge::endSemanticModelOrder);
    }
    @Test void nativeLayoutPreservesFullSignedOrderAndClearsAbsentValues() throws Exception {
        try(var bridge=VulkanicGalBridge.create("rust-vulkan");var arena=Arena.ofConfined()) {
            var layout=Struct.WORLD_MESH_INSTANCE_RECORD;
            var item=arena.allocate(layout.byteSize(),8);
            var encode=VulkanicGalBridge.class.getDeclaredMethod("encodeModelSubmissionOrder",MemorySegment.class,Integer.class);
            encode.setAccessible(true);
            for(int order:new int[]{Integer.MIN_VALUE,-1,0,1,Integer.MAX_VALUE}) {
                encode.invoke(null,item,Integer.valueOf(order));
                assertEquals(1,item.get(ValueLayout.JAVA_INT,layout.offset(28)));
                assertEquals(order,item.get(ValueLayout.JAVA_INT,layout.offset(29)));
            }
            encode.invoke(null,item,null);
            assertEquals(0,item.get(ValueLayout.JAVA_INT,layout.offset(28)));
            assertEquals(0,item.get(ValueLayout.JAVA_INT,layout.offset(29)));
        }
    }
}

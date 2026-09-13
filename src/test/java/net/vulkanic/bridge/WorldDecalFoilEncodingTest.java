package net.vulkanic.bridge;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import org.junit.jupiter.api.Test;
import static net.vulkanic.bridge.VulkanicGalBridge.*;
import static org.junit.jupiter.api.Assertions.*;

class WorldDecalFoilEncodingTest {
    private static float[] model() { return new float[]{1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1}; }
    private static float[] normal() { return new float[]{2,0,0,0,3,0,0,0,-4}; }
    private static WorldMeshInstanceRecord instance() {
        return new WorldMeshInstanceRecord(WORLD_MESH_ENTITY_STRATUM,17,1,0,2,1,0,-1,
            model(),128,128,0,0,0,0,-1);
    }

    @Test void copiedDecalRetainsIndependentPosesAndSurvivesFoilUpdates() {
        var positions = model(); var normals = normal();
        var decal = new WorldDecalFoilRecord(true,false,positions,normals);
        positions[0] = 99; normals[4] = 99;
        decal.modelPose()[5] = 99; decal.normalPose()[0] = 99;
        assertArrayEquals(model(),decal.modelPose());
        assertArrayEquals(normal(),decal.normalPose());
        var foil = new StandardItemFoilRecord(12345,.125,.25F);
        assertThrows(IllegalArgumentException.class, () -> instance().withDecalFoil(decal));
        var copied = instance().withItemFoil(foil).withDecalFoil(decal);
        assertSame(decal,copied.withItemFoil(new StandardItemFoilRecord(12346,.25,.5F)).decalFoil());
        assertArrayEquals(model(),copied.transform());
        assertThrows(IllegalArgumentException.class, () -> copied.withItemFoil(
            new StandardItemFoilRecord(12345,.125,.25F,StandardFoilKind.ENTITY)));
        positions = model(); positions[12] = 1;
        var mismatched = new WorldDecalFoilRecord(false,true,positions,normal());
        assertThrows(IllegalArgumentException.class, () -> instance().withItemFoil(foil).withDecalFoil(mismatched));
    }

    @Test void semanticPosesRejectMalformedPayloadsBeforeEncoding() {
        assertThrows(IllegalArgumentException.class, () -> new WorldDecalFoilRecord(false,true,new float[15],normal()));
        assertThrows(IllegalArgumentException.class, () -> new WorldDecalFoilRecord(false,true,model(),new float[8]));
        var nonAffine = model(); nonAffine[15] = .75F;
        assertThrows(IllegalArgumentException.class, () -> new WorldDecalFoilRecord(false,true,nonAffine,normal()));
        var nonFinite = normal(); nonFinite[4] = Float.NaN;
        assertThrows(IllegalArgumentException.class, () -> new WorldDecalFoilRecord(false,true,model(),nonFinite));
    }

    @Test void nativeLayoutEncodesBothContextsAndActivelyClearsAbsentFields() throws Exception {
        try (var bridge = VulkanicGalBridge.create("rust-vulkan"); var arena = Arena.ofConfined()) {
            var layout = Struct.WORLD_MESH_INSTANCE_RECORD;
            var item = arena.allocate(layout.byteSize(),8);
            var encode = VulkanicGalBridge.class.getDeclaredMethod("encodeWorldDecalFoil",MemorySegment.class,WorldDecalFoilRecord.class);
            encode.setAccessible(true);
            for (boolean hand : new boolean[]{false,true}) for (boolean trusted : new boolean[]{false,true}) {
                encode.invoke(null,item,new WorldDecalFoilRecord(hand,trusted,model(),normal()));
                assertEquals(hand?2:1,item.get(ValueLayout.JAVA_INT,layout.offset(24)));
                assertEquals(trusted?0:1,item.get(ValueLayout.JAVA_INT,layout.offset(25)));
                for (int i=0;i<16;i++) assertEquals(model()[i],item.get(ValueLayout.JAVA_FLOAT,layout.offset(26)+4L*i));
                for (int i=0;i<9;i++) assertEquals(normal()[i],item.get(ValueLayout.JAVA_FLOAT,layout.offset(27)+4L*i));
            }
            encode.invoke(null,item,null);
            assertEquals(0,item.get(ValueLayout.JAVA_INT,layout.offset(24)));
            assertEquals(0,item.get(ValueLayout.JAVA_INT,layout.offset(25)));
            for (int i=0;i<16;i++) assertEquals(0,item.get(ValueLayout.JAVA_INT,layout.offset(26)+4L*i));
            for (int i=0;i<9;i++) assertEquals(0,item.get(ValueLayout.JAVA_INT,layout.offset(27)+4L*i));
        }
    }
}

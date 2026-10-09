package net.vulkanic.bridge;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.List;
import org.junit.jupiter.api.Test;
import static net.vulkanic.bridge.VulkanicGalBridge.*;
import static org.junit.jupiter.api.Assertions.*;

class WorldEntityCullingEncodingTest {
    @org.junit.jupiter.api.BeforeAll
    static void bootstrapRegistries() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    static WorldEntityCullingRecord inputs() {
        return new WorldEntityCullingRecord(1|16,
            new WorldAabbRecord(40.0+1e-10,-1,-2,41,2,3),
            new WorldAabbRecord(-10,-11,-12,10,11,12));
    }
    static WorldMeshInstanceRecord instance() {
        return new WorldMeshInstanceRecord(WORLD_MESH_ENTITY_STRATUM,17,1,-1,1,0,0,-1,
            new float[]{1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1},128,128);
    }
    @Test void nestedCpuScopesAndCanonicalCopiesPreserveImmutableBounds() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var expected=inputs();
        beginSemanticEntityCulling(expected);
        WorldMeshInstanceRecord copied;
        try {
            copied=instance();
            beginSemanticEntityCulling(null);
            try {assertNull(instance().entityCulling());}
            finally {endSemanticEntityCulling();}
            assertSame(expected,instance().entityCulling());
        } finally {endSemanticEntityCulling();}
        assertNull(instance().entityCulling());
        assertSame(expected,copied.withPackedLight(0x00f000f0).entityCulling());
        assertSame(expected,copied.withModelSubmissionOrder(7).entityCulling());
        assertSame(expected,copied.asShadowOnlyEntityCaster().entityCulling());
        var frame=new net.vulkanic.world.RustGalWorldPrimitiveRenderer.PrimitiveFrame(128,128,
            copied.transform(),copied.transform(),new WorldBackgroundRecord(false,0,0,0,0,128,128),
            List.of(),List.of(),List.of(),List.of(),List.of(copied));
        assertSame(expected,net.vulkanic.world.RustGalWorldPrimitiveRenderer.withViewport(frame,256,256)
            .meshInstances().getFirst().entityCulling());
        assertThrows(IllegalStateException.class,VulkanicGalBridge::endSemanticEntityCulling);
        assertThrows(IllegalArgumentException.class,()->new WorldEntityCullingRecord(32,expected.bounds(),null));
        assertThrows(IllegalArgumentException.class,()->new WorldAabbRecord(Double.NaN,0,0,1,1,1));
    }
    @Test void nativeEncoderPreservesDoubleBoundsAndClearsAbsentDirtyStorage() throws Exception {
        try(var bridge=VulkanicGalBridge.create("rust-vulkan");var arena=Arena.ofConfined()) {
            assertEquals(76,ABI_VERSION);
            var layout=Struct.WORLD_MESH_INSTANCE_RECORD;
            var item=arena.allocate(layout.byteSize(),8);
            var encode=VulkanicGalBridge.class.getDeclaredMethod("encodeEntityCulling",MemorySegment.class,WorldEntityCullingRecord.class);
            encode.setAccessible(true);
            item.fill((byte)0x7f);
            var worldInputs=inputs().withCamera(29_999_999.375,-23.125,1_000_000.25);
            encode.invoke(null,item,worldInputs);
            assertEquals(1,item.get(ValueLayout.JAVA_INT,layout.offset(31)));
            assertEquals(17,item.get(ValueLayout.JAVA_INT,layout.offset(32)));
            assertEquals(40.0+1e-10,item.get(ValueLayout.JAVA_DOUBLE,layout.offset(33)));
            assertEquals(-10.0,item.get(ValueLayout.JAVA_DOUBLE,layout.offset(34)));
            assertEquals(29_999_999.375,item.get(ValueLayout.JAVA_DOUBLE,layout.offset(35)));
            assertEquals(-23.125,item.get(ValueLayout.JAVA_DOUBLE,layout.offset(35)+8));
            assertEquals(1_000_000.25,item.get(ValueLayout.JAVA_DOUBLE,layout.offset(35)+16));
            encode.invoke(null,item,null);
            assertEquals(0,item.get(ValueLayout.JAVA_INT,layout.offset(31)));
            assertEquals(0,item.get(ValueLayout.JAVA_INT,layout.offset(32)));
            for(int i=0;i<6;i++) {
                assertEquals(0,item.get(ValueLayout.JAVA_DOUBLE,layout.offset(33)+i*8L));
                assertEquals(0,item.get(ValueLayout.JAVA_DOUBLE,layout.offset(34)+i*8L));
            }
            for(int i=0;i<3;i++) assertEquals(0,item.get(ValueLayout.JAVA_DOUBLE,layout.offset(35)+i*8L));
        }
    }
    @Test void typedOrbEncoderPreservesShadowOnlyRoleAndCulling() {
        try(var bridge=VulkanicGalBridge.create("rust-vulkan");var arena=Arena.ofConfined()) {
            var orb=new WorldExperienceOrbInstanceRecord(17,1,0,new float[16],new float[]{0,0,0,1},0,inputs().withCamera(1_000_000.125,2,3),true);
            var data=encodeExperienceOrbInstances(arena,List.of(orb),0);
            var layout=Struct.WORLD_EXPERIENCE_ORB_INSTANCE;
            assertEquals(1,data.get(ValueLayout.JAVA_INT,layout.offset(8)));
            assertEquals(17,data.get(ValueLayout.JAVA_INT,layout.offset(9)));
            assertEquals(40.0+1e-10,data.get(ValueLayout.JAVA_DOUBLE,layout.offset(10)));
            assertEquals(1,data.get(ValueLayout.JAVA_INT,layout.offset(12)));
            assertEquals(1_000_000.125,data.get(ValueLayout.JAVA_DOUBLE,layout.offset(13)));
        }
    }
}

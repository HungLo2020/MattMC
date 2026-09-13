package net.vulkanic.bridge;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import org.junit.jupiter.api.Test;
import static net.vulkanic.bridge.VulkanicGalBridge.*;
import static org.junit.jupiter.api.Assertions.*;

class WorldItemFoilEncodingTest {
    private static WorldMeshInstanceRecord instance(int stratum) {
        return new WorldMeshInstanceRecord(stratum, 17, 1, 0, 2, 1, 0, -1,
            new float[]{1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1}, 128,128,0,0,0,0,-1);
    }

    private static WorldMeshInstanceRecord sourceArmor(StandardItemFoilRecord foil) {
        return instance(WORLD_MESH_ENTITY_STRATUM).withItemFoil(foil);
    }

    private static WorldMeshInstanceRecord layered(int stratum, int flags) {
        var source = instance(stratum);
        return new WorldMeshInstanceRecord(stratum, 17, 1, 0, 2, 1, 0, -1,
            source.transform(),128,128,0,0,0,flags,-1);
    }

    @Test void viewLayeringFlagsPreserveGeometryAndRejectConflictingSemantics() {
        for (int flags : new int[]{WORLD_MESH_VIEW_LAYER_PERSPECTIVE, WORLD_MESH_VIEW_LAYER_ORTHOGRAPHIC}) {
            var value = layered(WORLD_MESH_ENTITY_STRATUM, flags);
            assertEquals(flags, value.flags());
            assertArrayEquals(instance(WORLD_MESH_ENTITY_STRATUM).transform(), value.transform());
            assertThrows(IllegalArgumentException.class, () -> layered(60, flags));
            assertThrows(IllegalArgumentException.class, () -> value.withItemFoil(
                new StandardItemFoilRecord(0,0,0.5F,StandardFoilKind.ARMOR)));
        }
        for (int flags : new int[]{5,6,9,10,12,16}) {
            assertThrows(IllegalArgumentException.class, () -> layered(WORLD_MESH_ENTITY_STRATUM, flags));
        }
    }

    @Test void semanticCopyDoesNotTransformGeometryOrQuantizeStrength() {
        var source = instance(WORLD_MESH_ENTITY_STRATUM);
        var foil = new StandardItemFoilRecord(12345, 0.125, 0.1234567F);
        var copied = source.withItemFoil(foil);
        assertNull(source.itemFoil());
        assertEquals(foil, copied.itemFoil());
        assertArrayEquals(source.transform(), copied.transform());
        assertEquals(source.colorArgb(), copied.colorArgb());
        assertThrows(IllegalArgumentException.class, () -> instance(60).withItemFoil(foil));
    }

    @Test void exportedNativeLayoutCarriesExactAndCanonicalAbsentFields() throws Exception {
        try (var bridge = VulkanicGalBridge.create("rust-vulkan"); var arena = Arena.ofConfined()) {
			assertEquals(63, ABI_VERSION);
            var layout = Struct.WORLD_MESH_INSTANCE_RECORD;
            var item = arena.allocate(layout.byteSize(), 8);
            var encode = VulkanicGalBridge.class.getDeclaredMethod("encodeWorldItemFoil", MemorySegment.class, StandardItemFoilRecord.class);
            encode.setAccessible(true);
            encode.invoke(null, item, new StandardItemFoilRecord(12345, 0.125, 0.1234567F));
            assertEquals(1, item.get(ValueLayout.JAVA_INT, layout.offset(20)));
            assertEquals(12345, item.get(ValueLayout.JAVA_LONG, layout.offset(21)));
            assertEquals(0.125, item.get(ValueLayout.JAVA_DOUBLE, layout.offset(22)));
            assertEquals(0.1234567F, item.get(ValueLayout.JAVA_FLOAT, layout.offset(23)));
            var entity = new StandardItemFoilRecord(12345, 0.125, 0.1234567F, StandardFoilKind.ENTITY);
            encode.invoke(null, item, entity);
            assertEquals(2, item.get(ValueLayout.JAVA_INT, layout.offset(20)));
            assertEquals(12345, item.get(ValueLayout.JAVA_LONG, layout.offset(21)));
            assertEquals(0.125, item.get(ValueLayout.JAVA_DOUBLE, layout.offset(22)));
            assertEquals(0.1234567F, item.get(ValueLayout.JAVA_FLOAT, layout.offset(23)));
            assertEquals(entity, instance(WORLD_MESH_ENTITY_STRATUM).withItemFoil(entity).itemFoil());
            for (var kind : new StandardFoilKind[]{StandardFoilKind.ARMOR, StandardFoilKind.ARMOR_ORTHOGRAPHIC}) {
                var armor = new StandardItemFoilRecord(12345, 0.125, 0.1234567F, kind);
                encode.invoke(null, item, armor);
                assertEquals(kind.wireValue(), item.get(ValueLayout.JAVA_INT, layout.offset(20)));
                assertEquals(armor, sourceArmor(armor).itemFoil());
                assertArrayEquals(instance(WORLD_MESH_ENTITY_STRATUM).transform(), sourceArmor(armor).transform());
            }
            // Reuse dirty storage: absent data must be actively zeroed.
            encode.invoke(null, item, null);
            assertEquals(0, item.get(ValueLayout.JAVA_INT, layout.offset(20)));
            assertEquals(0, item.get(ValueLayout.JAVA_LONG, layout.offset(21)));
            assertEquals(0, item.get(ValueLayout.JAVA_LONG, layout.offset(22)));
            assertEquals(0, item.get(ValueLayout.JAVA_INT, layout.offset(23)));
        }
    }
}

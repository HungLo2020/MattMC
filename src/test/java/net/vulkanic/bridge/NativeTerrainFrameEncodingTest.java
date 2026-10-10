package net.vulkanic.bridge;

import java.lang.foreign.*;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import static net.vulkanic.bridge.VulkanicGalBridge.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeTerrainFrameEncodingTest {
    @Test void nativeCountsStaySemanticWhileRequestSlicesAreEmptyAndDirtyIdsClearForLegacy() throws Exception {
        try (var bridge = VulkanicGalBridge.create("rust-vulkan"); var arena = Arena.ofConfined()) {
            var request = arena.allocate(Struct.WHOLE_FRAME_SUBMIT.byteSize(), 8);
            request.fill((byte)0x7f);
            var reference = new StaticTerrainFrameReference(41, 17, 2, 1);
            var sections = StaticTerrainSections.nativeFrame(reference);
            var casters = StaticTerrainShadowCasters.nativeFrame(reference);
            var camera = new TerrainFrameCamera(150.5, 100, 530.5);
            Method identity = VulkanicGalBridge.class.getDeclaredMethod("writeStaticTerrainFrameReference",
                MemorySegment.class, StaticTerrainSections.class, StaticTerrainShadowCasters.class, TerrainFrameCamera.class);
            identity.setAccessible(true);
            Method sectionWriter = VulkanicGalBridge.class.getDeclaredMethod("writeStaticTerrainSections",
                Arena.class, MemorySegment.class, StaticTerrainSections.class, TerrainFrameCamera.class);
            Method casterWriter = VulkanicGalBridge.class.getDeclaredMethod("writeStaticTerrainShadowCasters",
                Arena.class, MemorySegment.class, StaticTerrainShadowCasters.class, TerrainFrameCamera.class);
            sectionWriter.setAccessible(true); casterWriter.setAccessible(true);
            identity.invoke(null, request, sections, casters, camera);
            sectionWriter.invoke(null, arena, request, sections, camera);
            casterWriter.invoke(null, arena, request, casters, camera);
            assertEquals(2, sections.count()); assertEquals(1, casters.count());
            assertEquals(41, Struct.WHOLE_FRAME_SUBMIT.getLong(request, 56));
            assertEquals(17, Struct.WHOLE_FRAME_SUBMIT.getLong(request, 57));
            for (int field : new int[]{47,49}) {
                assertEquals(0, request.get(ValueLayout.JAVA_LONG, Struct.WHOLE_FRAME_SUBMIT.offset(field)+8));
            }
            identity.invoke(null, request, StaticTerrainSections.EMPTY, StaticTerrainShadowCasters.EMPTY, null);
            assertEquals(0, Struct.WHOLE_FRAME_SUBMIT.getLong(request, 56));
            assertEquals(0, Struct.WHOLE_FRAME_SUBMIT.getLong(request, 57));
            var mismatch = assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> identity.invoke(null, request, sections, StaticTerrainShadowCasters.EMPTY, camera));
            assertInstanceOf(IllegalArgumentException.class, mismatch.getCause());
            var absentCamera = assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> identity.invoke(null, request, sections, casters, null));
            assertInstanceOf(IllegalArgumentException.class, absentCamera.getCause());
        }
    }
}

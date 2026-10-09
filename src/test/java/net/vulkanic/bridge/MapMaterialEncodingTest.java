package net.vulkanic.bridge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.List;
import net.minecraft.client.dev.DeterministicCameraCapture;
import net.vulkanic.bridge.VulkanicGalBridge.Struct;
import net.vulkanic.bridge.VulkanicGalBridge.WorldMaterialQuadRecord;
import net.vulkanic.world.RustGalWorldPrimitiveRenderer;
import org.junit.jupiter.api.Test;

class MapMaterialEncodingTest {
    @Test
    void mapTextKeepsItsSemanticKeyAndCopiedGeometryThroughCompactEncoding() throws Exception {
        var quad = mapQuad(0xf0001002);
        var keyType = Class.forName("net.vulkanic.bridge.VulkanicGalBridge$WorldMaterialKeyRecord");
        var from = keyType.getDeclaredMethod("from", WorldMaterialQuadRecord.class);
        from.setAccessible(true);
        var key = from.invoke(null, quad);
        for (String field : new String[]{"materialId", "textureId", "materialMode", "sourceProgram"}) {
            var getter = keyType.getDeclaredMethod(field);
            getter.setAccessible(true);
            assertEquals(WorldMaterialQuadRecord.class.getDeclaredMethod(field).invoke(quad), getter.invoke(key));
        }
        var encode = VulkanicGalBridge.class.getDeclaredMethod(
            "encodeCompactMaterialQuad", MemorySegment.class, WorldMaterialQuadRecord.class, int.class);
        encode.setAccessible(true);
        try (var arena = Arena.ofConfined()) {
            var layout = Struct.WORLD_MATERIAL_COMPACT_QUAD_REQUEST;
            var bytes = layout.array(arena, 1);
            encode.invoke(null, bytes, quad, 3);
            assertEquals(layout.byteSize(), bytes.get(ValueLayout.JAVA_INT, layout.offset(0)));
            assertEquals(3, bytes.get(ValueLayout.JAVA_INT, layout.offset(1)));
            assertEquals(-0.01F, bytes.get(ValueLayout.JAVA_FLOAT, layout.offset(6)));
            assertEquals(15728640, bytes.get(ValueLayout.JAVA_INT, layout.offset(25)));
        }
    }

    @Test
    void captureReceiptsRecognizeTypedImagesAndDecorationsAndKeepDuplicateCounts() throws Exception {
        List<RustGalWorldPrimitiveRenderer.ItemFrameMapDiagnostic> images = diagnostics("ITEM_FRAME_MAP_DIAGNOSTICS");
        List<RustGalWorldPrimitiveRenderer.ItemFrameMapExecutionDiagnostic> imageExecutions = diagnostics("ITEM_FRAME_MAP_EXECUTION_DIAGNOSTICS");
        List<RustGalWorldPrimitiveRenderer.ItemFrameMapDecorationDiagnostic> decorations = diagnostics("ITEM_FRAME_MAP_DECORATION_DIAGNOSTICS");
        List<RustGalWorldPrimitiveRenderer.ItemFrameMapDecorationExecutionDiagnostic> decorationExecutions = diagnostics("ITEM_FRAME_MAP_DECORATION_EXECUTION_DIAGNOSTICS");
        assertTrue(images.isEmpty() && imageExecutions.isEmpty() && decorations.isEmpty() && decorationExecutions.isEmpty());
        try (var capture = mockStatic(DeterministicCameraCapture.class)) {
            capture.when(DeterministicCameraCapture::isActiveForDiagnostics).thenReturn(true);
            capture.when(DeterministicCameraCapture::currentInProgressRenderedFrameIndex).thenReturn(4L);
            images.add(new RustGalWorldPrimitiveRenderer.ItemFrameMapDiagnostic(
                3, "rust-vulkan-whole-frame", 1, 2, 0, false, 0, "minecraft:map/2", 0xf0001002,
                15728640, true, 0, 0, 128, 128));
            decorations.add(new RustGalWorldPrimitiveRenderer.ItemFrameMapDecorationDiagnostic(
                3, "rust-vulkan-whole-frame", 1, 2, 0, "minecraft:red_x", (byte)0, (byte)0, (byte)0,
                "minecraft:map_decorations", 0xf0001003, 15728640, true, 0, 0, 8, 8));
            RustGalWorldPrimitiveRenderer.recordWholeFrameItemFrameMapExecution(5, 6, List.of());
            assertTrue(imageExecutions.isEmpty() && decorationExecutions.isEmpty());
            var generic = quad(0xf0001002, RustGalWorldPrimitiveRenderer.MATERIAL_ID_TRANSLUCENT_TEXTURED,
                RustGalWorldPrimitiveRenderer.MATERIAL_MODE_TRANSLUCENT);
            RustGalWorldPrimitiveRenderer.recordWholeFrameItemFrameMapExecution(5, 6, List.of(generic));
            assertTrue(imageExecutions.isEmpty(), "the old generic material is not proof of the native map route");
            var image = mapQuad(0xf0001002);
            var decoration = mapQuad(0xf0001003);
            RustGalWorldPrimitiveRenderer.recordWholeFrameItemFrameMapExecution(5, 6, List.of(image, decoration));
            assertEquals(1, imageExecutions.getLast().quads());
            assertEquals(1, decorationExecutions.getLast().quads());
            RustGalWorldPrimitiveRenderer.recordWholeFrameItemFrameMapExecution(7, 8, List.of(image, image, decoration, decoration));
            assertEquals(2, imageExecutions.getLast().quads(), "duplicate geometry must remain visible to the gate");
            assertEquals(2, decorationExecutions.getLast().quads());
            assertEquals(7, imageExecutions.getLast().gameplayFrameId());
            assertEquals(8, imageExecutions.getLast().submissionId());
        } finally {
            images.clear(); imageExecutions.clear(); decorations.clear(); decorationExecutions.clear();
        }
    }

    @Test
    void fabulousReadinessIncludesBlendedCutoutMapInputs() throws Exception {
        List<WorldMaterialQuadRecord> pending = diagnostics("PENDING_MATERIAL_QUADS");
        assertTrue(pending.isEmpty());
        assertFalse(RustGalWorldPrimitiveRenderer.hasPendingFabulousTransparencyWork());
        var image = mapQuad(0xf0001002);
        try {
            pending.add(image);
            assertTrue(RustGalWorldPrimitiveRenderer.hasPendingFabulousTransparencyWork());
        } finally {
            pending.remove(image);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> diagnostics(String name) throws Exception {
        var field = RustGalWorldPrimitiveRenderer.class.getDeclaredField(name);
        field.setAccessible(true);
        return (List<T>) field.get(null);
    }

    private static WorldMaterialQuadRecord mapQuad(int textureId) {
        return quad(textureId, RustGalWorldPrimitiveRenderer.MATERIAL_ID_MAP_TEXT,
            RustGalWorldPrimitiveRenderer.MATERIAL_MODE_TRANSLUCENT_CUTOUT);
    }

    private static WorldMaterialQuadRecord quad(int textureId, int materialId, int materialMode) {
        return new WorldMaterialQuadRecord(
            RustGalWorldPrimitiveRenderer.STRATUM_WORLD_MATERIAL,
            materialId, textureId, materialMode,
            RustGalWorldPrimitiveRenderer.DEPTH_POLICY_TEST_NO_WRITE,
            RustGalWorldPrimitiveRenderer.CULL_NONE,
            RustGalWorldPrimitiveRenderer.WORLD_TOPOLOGY_TRIANGLES,
            RustGalWorldPrimitiveRenderer.WORLD_WINDING_CCW, -1,
            0, 128, -0.01F, 128, 128, -0.01F, 128, 0, -0.01F, 0, 0, -0.01F,
            0, 1, 1, 1, 1, 0, 0, 0,
            128, 128, RustGalWorldPrimitiveRenderer.MATERIAL_SOURCE_TEXTURED,
            RustGalWorldPrimitiveRenderer.MATERIAL_SOURCE_UV_LOCAL_TEXTURE, -1, 15728640
        );
    }
}

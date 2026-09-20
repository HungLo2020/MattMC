package net.vulkanic;

import net.vulkanic.world.RustGalWorldPrimitiveRenderer;
import net.vulkanic.bridge.VulkanicGalBridge;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistantHorizonsGenericBoxContractTest {
    @Test
    void copiedBoxRecordRetainsOnlyPrimitiveSemanticInputs() {
        var box = new VulkanicGalBridge.WorldDistantHorizonsGenericBoxRecord(
            -1.0f, -2.0f, -3.0f, 4.0f, 5.0f, 6.0f,
            0x80A0B0C0, 0x00F000F0,
            0.8f, 0.9f, 0.6f, 0.7f, 1.0f, 0.5f, true);

        assertEquals(-1.0f, box.minX());
        assertEquals(6.0f, box.maxZ());
        assertEquals(0x80A0B0C0, box.colorArgb());
        assertEquals(0x00F000F0, box.packedLight());
        assertTrue(box.ssaoEnabled());
    }

    @Test
    void genericBoxTransportKeepsTopologyAndExpansionInRust() throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        String renderer = Files.readString(root.resolve(
            "src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
        String lod = Files.readString(root.resolve(
            "src/main/java/com/seibel/distanthorizons/core/render/renderer/LodRenderer.java"));
        String generic = Files.readString(root.resolve(
            "src/main/java/com/seibel/distanthorizons/core/render/renderer/generic/GenericObjectRenderer.java"));

        String rustFfi = Files.readString(root.resolve(
            "src/main/rust/render/vulkanic/ffi/world.rs"));
        assertEquals(0, count(renderer, "appendDistantHorizonsGenericBoxFaceLocked(box,"),
            "Java must not rebuild six face records for each copied box");
        assertTrue(renderer.contains("PENDING_DH_GENERIC_BOXES.addAll(boxes)"));
        assertTrue(renderer.contains("consumedDistantHorizonsRouteSelected"));
        assertTrue(renderer.contains("admittedDistantHorizonsGenericBoxes"),
            "generic boxes must be paired with the consumed private-DH route decision");
        assertTrue(rustFfi.contains("const DH_GENERIC_BOX_FACE_COUNT: usize = 6"));
        assertTrue(rustFfi.contains("decode_dh_generic_boxes"));
        assertTrue(rustFfi.contains("WORLD_MATERIAL_TEXTURE_GENERATED_WHITE"));
        assertTrue(generic.contains("LightTexture.pack"));
        assertTrue(generic.contains("collectRustSemantic"));
        assertTrue(generic.contains("rustSemanticGroupIds"));
        assertTrue(generic.contains("rustSemanticBoxes"));
        assertTrue(generic.contains("rustSemanticGroupsToPostRender"));
        assertTrue(generic.contains("ids.clear()"),
            "generic preflight scratch storage must be reused between frames");
        assertTrue(generic.contains("distant-horizons-generic-semantics"));
        assertTrue(generic.contains(":cloudDelegated=0"));
        assertTrue(generic.contains(":cloudPrivate="));
        assertTrue(generic.contains("markDistantHorizonsPrivateClouds"));
        assertTrue(renderer.contains("STRATUM_DH_GENERIC"));
        assertTrue(generic.contains(":boxes="));
        assertTrue(generic.contains(":ssaoBoxes="));
        assertTrue(generic.contains(":nonSsaoBoxes="));
        assertTrue(generic.contains("mattmc.dev.dhGenericBoxFixture"));
        assertTrue(generic.contains("RustGenericBoxFixture"));
        assertTrue(generic.contains("MC_RENDER.getLookAtVector()"));
        assertTrue(generic.contains("direction.normalize()"));
        assertTrue(generic.contains("fixture.setSsaoEnabled(true)"));
        assertTrue(generic.contains("boxGroup.ssaoEnabled"));
        assertFalse(generic.contains("boxGroup.ssaoEnabled && boxGroup.size() > 0"),
            "SSAO-enabled generic groups must cross the semantic boundary for Rust phase ordering");
        assertTrue(generic.contains("finally\n\t\t{\n\t\t\t// A later group can reject the Rust route"),
            "accepted generic groups must receive postRender cleanup on fail-closed collection");
        String capture = Files.readString(root.resolve(
            "src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java"));
        assertTrue(capture.contains("rustGalSubmittedWorkIdentities"));
        assertTrue(capture.contains("boundedIdentity"));
        int collect = lod.indexOf("collectRustGenericSemantics(renderParams)");
        int select = lod.indexOf("markRustNonWaterRouteSelected()", collect);
        assertTrue(collect >= 0 && select > collect,
            "generic DH semantics must be copied before route admission");
    }

    private static int count(String text, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }
}

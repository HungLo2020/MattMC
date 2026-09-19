package net.vulkanic.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RustGalTerrainAtlasPublicationTest {
    @Test
    void animatedFrameChangesReuseTheImmutableAtlasDeclaration() {
        assertTrue(RustGalTerrainRenderer.canReuseSemanticAtlasPayload(
            7L, 7L, 11L, 12L, true));
    }

    @Test
    void staticFrameChangesStillRequireAReplacementPayload() {
        assertFalse(RustGalTerrainRenderer.canReuseSemanticAtlasPayload(
            7L, 7L, 11L, 12L, false));
        assertTrue(RustGalTerrainRenderer.canReuseSemanticAtlasPayload(
            7L, 7L, 11L, 11L, false));
    }

    @Test
    void aSemanticReloadAlwaysReplacesThePayload() {
        assertFalse(RustGalTerrainRenderer.canReuseSemanticAtlasPayload(
            7L, 8L, 11L, 11L, true));
    }
}

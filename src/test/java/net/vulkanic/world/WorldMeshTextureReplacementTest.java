package net.vulkanic.world;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import net.vulkanic.bridge.VulkanicGalBridge.WorldMeshTextureAssetRecord;
import net.vulkanic.bridge.VulkanicGalBridge.WorldMeshAnimationFrameRecord;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldMeshTextureReplacementTest {
    @org.junit.jupiter.api.BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    private static WorldMeshTextureAssetRecord texture(int[] fields, byte[] base,
        List<byte[]> mips, List<WorldMeshAnimationFrameRecord> frames) {
        return new WorldMeshTextureAssetRecord(fields[0], base, fields[1], fields[2], fields[3],
            fields[4], fields[5], fields[6], fields[7], frames, fields[8], mips);
    }

    @Test
    void allSemanticFieldsAndMipBytesParticipateInIdentity() {
        int[] fields = {17, 2, 2, 2, 3, 0, 2, 0, 0};
        var frames = List.of(new WorldMeshAnimationFrameRecord(0, 3), new WorldMeshAnimationFrameRecord(1, 7));
        var original = texture(fields, new byte[]{1, 2}, List.of(new byte[]{3, 4}), frames);
        original.mipPngBytes().getFirst()[0] = 99;
        assertArrayEquals(new byte[]{3, 4}, original.mipPngBytes().getFirst(), "caller must not mutate retained identity");
        assertTrue(original.sameContent(texture(fields.clone(), new byte[]{1, 2}, List.of(new byte[]{3, 4}), frames)));
        assertFalse(original.sameContent(null));
        for (int field = 0; field < fields.length; field++) {
            int[] changed = fields.clone();
            changed[field]++;
            assertFalse(original.sameContent(texture(changed, new byte[]{1, 2}, List.of(new byte[]{3, 4}), frames)),
                "field " + field + " must invalidate texture identity");
        }
        assertFalse(original.sameContent(texture(fields, new byte[]{2, 1}, List.of(new byte[]{3, 4}), frames)));
        assertFalse(original.sameContent(texture(fields, new byte[]{1, 2}, List.of(new byte[]{4, 3}), frames)));
        assertFalse(original.sameContent(texture(fields, new byte[]{1, 2}, List.of(), frames)));
        assertFalse(original.sameContent(texture(fields, new byte[]{1, 2}, List.of(new byte[]{3, 4}),
            List.of(new WorldMeshAnimationFrameRecord(1, 7), new WorldMeshAnimationFrameRecord(0, 3)))));
    }

    @Test
    void publisherRedirtiesMipOnlyReplacementButNotEqualIndependentCopies() {
        var textures = new LinkedHashMap<Integer, WorldMeshTextureAssetRecord>();
        var dirty = new LinkedHashSet<Integer>();
        var first = new WorldMeshTextureAssetRecord(17, new byte[]{1, 2}, List.of(new byte[]{3, 4}));
        assertTrue(RustGalWorldPrimitiveRenderer.registerChangedTexture(textures, dirty, first));
        assertEquals(java.util.Set.of(17), dirty);
        dirty.clear(); // Previous upload was accepted.
        var same = new WorldMeshTextureAssetRecord(17, new byte[]{1, 2}, List.of(new byte[]{3, 4}));
        assertFalse(RustGalWorldPrimitiveRenderer.registerChangedTexture(textures, dirty, same));
        assertTrue(dirty.isEmpty());
        var changed = new WorldMeshTextureAssetRecord(17, new byte[]{1, 2}, List.of(new byte[]{4, 3}));
        assertTrue(RustGalWorldPrimitiveRenderer.registerChangedTexture(textures, dirty, changed));
        assertEquals(java.util.Set.of(17), dirty);
        assertSame(changed, textures.get(17));
    }
}

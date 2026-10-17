package net.vulkanic.world;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.MapTextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.vulkanic.bridge.VulkanicGalBridge;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NativeMapWorldImageTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void generatedMapPublishesWithoutMinecraftTextureOrResourceManagers() throws Exception {
        assertNull(Minecraft.getInstance(), "this regression exercises CPU publication without a client");
        var semantic = RustGalWorldPrimitiveRenderer.class.getDeclaredMethod(
            "registerSemanticTextureAsset", ResourceLocation.class, int.class, String.class);
        var dynamic = RustGalWorldPrimitiveRenderer.class.getDeclaredMethod(
            "registerDynamicTextureAsset", ResourceLocation.class, int.class);
        semantic.setAccessible(true);
        dynamic.setAccessible(true);
        Map<Integer, VulkanicGalBridge.WorldMeshTextureAssetRecord> textures = registry("WORLD_MESH_TEXTURES");
        Map<ResourceLocation, Long> fingerprints = registry("DYNAMIC_WORLD_ASSET_FINGERPRINTS");
        Map<ResourceLocation, Integer> bytes = registry("DYNAMIC_WORLD_ASSET_BYTES");
        Set<Integer> dirty = registry("DIRTY_WORLD_MESH_TEXTURES");
        int textureId = 0xf123c768;
        var data = MapItemSavedData.createForClient((byte) 0, false, Level.OVERWORLD);
        for (int i = 0; i < data.colors.length; i++) data.colors[i] = (byte) i;
        var manager = new MapTextureManager(null);
        var mapId = new MapId(1234569);
        var identity = manager.prepareMapTexture(mapId, data);
        assertFalse(textures.containsKey(textureId));
        assertFalse(fingerprints.containsKey(identity));
        try {
            assertEquals(true, semantic.invoke(null, identity, textureId, "map-regression"));
            var first = textures.get(textureId);
            assertNotNull(first);
            assertEquals(VulkanicGalBridge.WORLD_MESH_TEXTURE_COORDINATE_ORIGIN_MINECRAFT_TOP_LEFT,
                first.coordinateOrigin());
            var image = ImageIO.read(new ByteArrayInputStream(first.pngBytes()));
            for (int i = 0; i < data.colors.length; i++) {
                assertEquals(MapColor.getColorFromPackedId(data.colors[i]), image.getRGB(i % 128, i / 128));
            }
            dirty.remove(textureId); // The first publication has been consumed.
            assertEquals(true, dynamic.invoke(null, identity, textureId));
            assertSame(first, textures.get(textureId), "an unchanged map reuses its immutable payload");
            assertFalse(dirty.contains(textureId), "an unchanged map does not enqueue another upload");

            data.colors[0] = (byte) MapColor.FIRE.getPackedId(MapColor.Brightness.HIGH);
            manager.update(mapId, data);
            manager.prepareMapTexture(mapId, data);
            assertEquals(true, semantic.invoke(null, identity, textureId, "map-regression"));
            assertNotSame(first, textures.get(textureId));
            assertTrue(dirty.contains(textureId));
            image = ImageIO.read(new ByteArrayInputStream(textures.get(textureId).pngBytes()));
            assertEquals(MapColor.getColorFromPackedId(data.colors[0]), image.getRGB(0, 0));
        } finally {
            manager.close();
            textures.remove(textureId);
            dirty.remove(textureId);
            fingerprints.remove(identity);
            bytes.remove(identity);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T registry(String name) throws ReflectiveOperationException {
        var field = RustGalWorldPrimitiveRenderer.class.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(null);
    }
}

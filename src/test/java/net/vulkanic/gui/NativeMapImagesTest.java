package net.vulkanic.gui;

import net.minecraft.client.resources.MapTextureManager;
import net.minecraft.client.resources.NativeMapImages;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.vulkanic.gui.RustGalGuiRawImageAssets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NativeMapImagesTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void mapStagingCopiesCompactColorsAndRetiresThemOnReset() throws Exception {
        var data=MapItemSavedData.createForClient((byte)0,false,Level.OVERWORLD);
        for (int i=0;i<data.colors.length;i++) data.colors[i]=(byte)i;
        var manager=new MapTextureManager(null);
        try {
            var id=new MapId(1234568);
            var location=manager.prepareMapTexture(id,data);
            var snapshot=RustGalGuiRawImageAssets.semanticSnapshotUnstaged(location);
            assertNotNull(snapshot);
            assertEquals(RustGalGuiRawImageAssets.RAW_MAP_COLOR8,snapshot.format());
            assertEquals(16384,snapshot.pixels().length);
            byte[] expected=data.colors.clone();
            RustGalGuiRawImageAssets.invalidate();
            manager.prepareMapTexture(id,data);
            assertArrayEquals(expected,RustGalGuiRawImageAssets.semanticSnapshotUnstaged(location).pixels(),
                "existing clean map must republish after CPU image invalidation");
            data.colors[0]=63;
            assertArrayEquals(expected,snapshot.pixels());
            assertArrayEquals(expected,RustGalGuiRawImageAssets.semanticSnapshotUnstaged(location).pixels());

            var encodeWorld=net.vulkanic.world.RustGalWorldPrimitiveRenderer.class.getDeclaredMethod("encodeSemanticImageSnapshot", RustGalGuiRawImageAssets.SemanticRawImageSnapshot.class);
            encodeWorld.setAccessible(true);
            byte[] png=(byte[])encodeWorld.invoke(null,snapshot);
            assertArrayEquals(NativeMapImages.encodePng(snapshot.pixels()),png);
            var image=ImageIO.read(new ByteArrayInputStream(png));
            for (int i=0;i<expected.length;i++)
                assertEquals(MapColor.getColorFromPackedId(expected[i]),image.getRGB(i%128,i/128));
            manager.update(id,data);
            manager.prepareMapTexture(id,data);
            assertEquals(63,RustGalGuiRawImageAssets.semanticSnapshotUnstaged(location).pixels()[0]);
            manager.resetData();
            // Inspect the authoritative staging table; resolve() may otherwise
            // try Minecraft's resource manager after the CPU asset is removed.
            var field=net.vulkanic.gui.RustGalFrameCoordinator.class.getDeclaredField("pendingRawImages");
            field.setAccessible(true);
            var pending=(java.util.Map<?,?>)field.get(null);
            assertFalse(pending.containsKey(RustGalGuiRawImageAssets.assetId(location.toString())));
        } finally { manager.close(); }
    }

    @Test void indexedPngRejectsMalformedDataBeforeNativeAdmission() {
        assertThrows(IllegalArgumentException.class,()->NativeMapImages.encodePng(null));
        assertThrows(IllegalArgumentException.class,()->NativeMapImages.encodePng(new byte[16383]));
        assertThrows(IllegalArgumentException.class,()->NativeMapImages.encodePng(new byte[16385]));
    }
}

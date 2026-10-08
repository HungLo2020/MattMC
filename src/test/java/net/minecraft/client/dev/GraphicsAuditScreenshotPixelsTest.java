package net.minecraft.client.dev;

import net.blaze3d.platform.NativeImage;
import net.blaze3d.textures.TextureFormat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GraphicsAuditScreenshotPixelsTest {
    @Test void convertsDeclaredBgraReadbackToRgbaWithoutMovingPixels() {
        try (var image = new NativeImage(2, 2, false)) {
            int[] argb = {0xff123456, 0x801122ee, 0xff0000ff, 0xffff0000};
            for (int i = 0; i < argb.length; i++) image.setPixelABGR(i % 2, i / 2, argb[i]);
            GraphicsAuditScreenshotPixels.normalize(image, TextureFormat.BGRA8);
            for (int i = 0; i < argb.length; i++) assertEquals(argb[i], image.getPixel(i % 2, i / 2));
        }
    }

    @Test void rgbaRemainsUntouchedAndUnknownFormatsAreRejected() {
        try (var image = new NativeImage(1, 1, false)) {
            image.setPixel(0, 0, 0x80123456);
            GraphicsAuditScreenshotPixels.normalize(image, TextureFormat.RGBA8);
            assertEquals(0x80123456, image.getPixel(0, 0));
            assertThrows(IllegalArgumentException.class,
                () -> GraphicsAuditScreenshotPixels.normalize(image, TextureFormat.DEPTH32));
            assertEquals(0x80123456, image.getPixel(0, 0));
        }
    }
}

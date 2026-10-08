package net.minecraft.client.dev;

import net.blaze3d.platform.NativeImage;
import net.blaze3d.textures.TextureFormat;
import net.minecraft.util.ARGB;

/** Diagnostic-only normalization of Screenshot's raw readback byte layout. */
final class GraphicsAuditScreenshotPixels {
    private GraphicsAuditScreenshotPixels() {}

    static void normalize(NativeImage image, TextureFormat sourceFormat) {
        if (sourceFormat == TextureFormat.RGBA8) return;
        if (sourceFormat != TextureFormat.BGRA8) {
            throw new IllegalArgumentException("Unsupported diagnostic color format: " + sourceFormat);
        }
        // Screenshot copies raw BGRA readback bytes into an RGBA NativeImage.
        // Convert the declared layout, never infer colors from image content.
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setPixel(x, y, ARGB.fromABGR(image.getPixel(x, y)));
            }
        }
    }
}

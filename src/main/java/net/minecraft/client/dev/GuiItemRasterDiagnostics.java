package net.minecraft.client.dev;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import net.blaze3d.textures.GpuTexture;
import net.logging.LogUtils;

/** Opt-in observation only: no binding, pixel-store, texture or renderer changes. */
public final class GuiItemRasterDiagnostics {
    static long backgroundObservationFrame(long completedRenderFrames, boolean afterBlit) {
        // Minecraft.afterRender increments the counter before blitToScreen.
        return afterBlit ? completedRenderFrames : completedRenderFrames + 1;
    }
    static boolean observesBackgroundStage(String stage) {
        return "main-after-post".equals(stage) || "main-after-gui".equals(stage) || "window-after-blit".equals(stage);
    }
    private GuiItemRasterDiagnostics() {}

    static boolean shouldObserveBackground(boolean initialized, boolean terrainReady, int settledFrames, int requiredFrames) {
        return initialized && terrainReady && requiredFrames >= 2 && settledFrames >= requiredFrames - 1;
    }

    /** Fixed 3x3 interiors matching the shared 1280x720 alpha-step fixture. */
    static int[][] backgroundProbePoints() {
        int[][] points = new int[9 * 5 * 9][2];
        int index = 0;
        for (int item = 0; item < 9; item++) {
            for (int center : new int[] {6,12,18,30,39}) {
                for (int y = 674; y <= 676; y++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        points[index++] = new int[] {376 + 60 * item + center + dx, y};
                    }
                }
            }
        }
        return points;
    }

    public static boolean observe(GpuTexture texture, int cell, int count) {
        if (!Boolean.getBoolean("mattmc.dev.guiItemRasterTrace") || count == 0) return false;
        int width = texture.getWidth(0), height = texture.getHeight(0);
        if (!validLayout(width, height, cell, count)) return true;
        net.vulkanic.diagnostics.GuiItemTextureReadback.observe(texture, pixels -> {
            for (String line : describe(pixels, width, height, cell, count)) {
                LogUtils.getLogger().info("gui.item.raster-pixels {}", line);
            }
        });
        return true;
    }

    static boolean validLayout(int width, int height, int cell, int count) {
        return width > 0 && height > 0 && width <= 2048 && height <= 2048
            && cell > 0 && cell <= Math.min(width,height) && count > 0 && count <= 32
            && count <= (width / cell) * (height / cell);
    }

    static List<String> describe(ByteBuffer pixels, int width, int height, int cell, int count) {
        if (!validLayout(width,height,cell,count) || pixels.limit() < width*height*4) {
            throw new IllegalArgumentException("invalid bounded item raster readback");
        }
        List<String> lines = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            int left = index % (width/cell) * cell, top = index / (width/cell) * cell;
            StringBuilder line = new StringBuilder("slot=" + left + "," + top + "," + cell
                + " atlas=" + width + "x" + height + " centerColumn=");
            String previous = null;
            for (int row = 0; row < cell; row++) {
                int offset = ((height-1-top-row)*width + left + cell/2)*4;
                String value = Byte.toUnsignedInt(pixels.get(offset)) + ","
                    + Byte.toUnsignedInt(pixels.get(offset+1)) + ","
                    + Byte.toUnsignedInt(pixels.get(offset+2)) + ","
                    + Byte.toUnsignedInt(pixels.get(offset+3));
                if (!value.equals(previous)) line.append(row).append(':').append(value).append(';');
                previous = value;
            }
            line.append(" centerRow=");
            previous = null;
            for (int column = 0; column < cell; column++) {
                int offset = ((height-1-top-cell/2)*width + left + column)*4;
                String value = Byte.toUnsignedInt(pixels.get(offset)) + ","
                    + Byte.toUnsignedInt(pixels.get(offset+1)) + ","
                    + Byte.toUnsignedInt(pixels.get(offset+2)) + ","
                    + Byte.toUnsignedInt(pixels.get(offset+3));
                if (!value.equals(previous)) line.append(column).append(':').append(value).append(';');
                previous = value;
            }
            lines.add(line.toString());
        }
        return List.copyOf(lines);
    }
}

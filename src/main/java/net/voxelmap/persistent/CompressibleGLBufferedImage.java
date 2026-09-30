package net.voxelmap.persistent;

import net.voxelmap.VoxelConstants;
import net.voxelmap.util.CompressionUtils;
import java.util.UUID;
import java.util.zip.DataFormatException;
import net.minecraft.resources.ResourceLocation;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.gui.RustGalGuiRawImageAssets;
import org.apache.logging.log4j.Level;

public class CompressibleGLBufferedImage {

    private byte[] bytes;
    private final int width;
    private final int height;
    private final Object bufferLock = new Object();
    private boolean isCompressed;
    private final boolean compressNotDelete;
    private final ResourceLocation location = ResourceLocation.fromNamespaceAndPath("voxelmap", "mapimage/" + UUID.randomUUID());
    private boolean staged;

    public CompressibleGLBufferedImage(int width, int height, int imageType) {
        this.width = width;
        this.height = height;
        this.bytes = new byte[width * height * 4];
        this.compressNotDelete = VoxelConstants.getVoxelMapInstance().getPersistentMapOptions().outputImages;
    }

    public byte[] getData() {
        if (this.isCompressed) {
            this.decompress();
        }

        return this.bytes;
    }

    public ResourceLocation getTextureLocation() {
        return this.staged ? this.location : null;
    }

    /** Publishes the current CPU pixels as a Rust semantic GUI image. */
    public void stageToRust() {
        if (!VulkanicAPI.isOnRenderThread()) {
            VoxelConstants.getLogger().log(Level.WARN, "Texture upload call from wrong thread", new Exception());
            return;
        }

        if (this.isCompressed) {
            this.decompress();
        }

        byte[] pixels;
        synchronized (this.bufferLock) {
            pixels = this.bytes.clone();
        }
        this.staged = RustGalGuiRawImageAssets.stageCpuRgba8(this.location, this.width, this.height, pixels, true);
        this.compress();
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public void deleteTexture() {
        if (!VulkanicAPI.isOnRenderThread()) {
            VoxelConstants.getLogger().log(Level.WARN, "Texture unload call from wrong thread", new Exception());
            return;
        }
        if (this.staged) {
            RustGalGuiRawImageAssets.releaseCpuRgba8(this.location);
            this.staged = false;
        }
    }

    public void setRGB(int x, int y, int color) {
        if (this.isCompressed) {
            this.decompress();
        }

        int index = (x + y * this.getWidth()) * 4;
        synchronized (this.bufferLock) {
            int alpha = color >> 24 & 0xFF;
            this.bytes[index + 0] = (byte) ((color >> 16 & 0xFF) * alpha / 255);
            this.bytes[index + 1] = (byte) ((color >> 8 & 0xFF) * alpha / 255);
            this.bytes[index + 2] = (byte) ((color & 0xFF) * alpha / 255);
            this.bytes[index + 3] = -1;
        }
    }

    private synchronized void compress() {
        if (!this.isCompressed) {
            if (this.compressNotDelete) {
                this.bytes = CompressionUtils.compress(this.bytes);
            } else {
                this.bytes = null;
            }

            this.isCompressed = true;
        }
    }

    private synchronized void decompress() {
        if (this.isCompressed) {
            if (this.compressNotDelete) {
                try {
                    this.bytes = CompressionUtils.decompress(this.bytes);
                } catch (DataFormatException ignored) {
                }
            } else {
                this.bytes = new byte[this.width * this.height * 4];
                this.isCompressed = false;
            }

        }
    }
}

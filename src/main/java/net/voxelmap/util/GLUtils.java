package net.voxelmap.util;

import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.systems.CommandEncoder;
import net.blaze3d.systems.RenderSystem;
import net.blaze3d.textures.GpuTexture;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import net.minecraft.util.ARGB;

public class GLUtils {
    public static void readTextureContentsToBufferedImage(GpuTexture gpuTexture, Consumer<BufferedImage> resultConsumer) {
        RenderSystem.assertOnRenderThread();
        if (net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
                || net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
            throw new IllegalStateException("Java VoxelMap texture readback is unavailable on the Rust Vulkan route");
        }
        int bytePerPixel = gpuTexture.getFormat().pixelSize();
        int width = gpuTexture.getWidth(0);
        int height = gpuTexture.getHeight(0);
        int bufferSize = bytePerPixel * width * height;
        GpuBuffer gpuBuffer = net.vulkanic.VulkanicAPI.createBuffer(() -> "Texture read buffer", GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, bufferSize);
        CommandEncoder commandEncoder = net.vulkanic.VulkanicAPI.createCommandEncoder();
        commandEncoder.copyTextureToBuffer(gpuTexture, gpuBuffer, 0, () -> {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
            try (GpuBuffer.MappedView readView = commandEncoder.mapBuffer(gpuBuffer, true, false)) {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int pixel = readView.data().getInt((x + y * width) * bytePerPixel);
                        image.setRGB(x, y, ARGB.fromABGR(pixel));
                    }
                }
            }
            gpuBuffer.close();
            resultConsumer.accept(image);
        }, 0);
    }
}

package com.github.alexthe666.citadel.client.texture;

import com.mojang.blaze3d.platform.NativeImage;
// Citadel: TextureUtil.prepareImage might not exist in 1.21
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.FastColor;

import java.awt.image.BufferedImage;

// Citadel: Video frame texture for video playback feature
// Simplified for 1.21 - TextureUtil methods may have changed
public class VideoFrameTexture extends DynamicTexture {

    public VideoFrameTexture(NativeImage image) {
        super(image);
    }


    @Override
    public void setPixels(NativeImage nativeImage) {
        super.setPixels(nativeImage);
        // Citadel: TextureUtil.prepareImage may not exist in 1.21
        // Image preparation is handled by DynamicTexture
        if (this.getPixels() != null) {
            this.upload();
        }
    }

    public void setPixelsFromBufferedImage(BufferedImage bufferedImage) {
        for(int i = 0; i < Math.min(this.getPixels().getWidth(), bufferedImage.getWidth()); i++){
            for(int j = 0; j < Math.min(this.getPixels().getHeight(), bufferedImage.getHeight()); j++){
                int color = bufferedImage.getRGB(i, j);
                int r = color >> 16 & 255;
                int g = color >> 8 & 255;
                int b = color & 255;
                this.getPixels().setPixelRGBA(i, j, FastColor.ABGR32.color(0XFF, b, g, r));
            }
        }
        this.upload();
    }
}

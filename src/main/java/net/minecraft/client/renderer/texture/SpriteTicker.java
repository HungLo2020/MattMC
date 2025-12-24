package net.minecraft.client.renderer.texture;

import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public interface SpriteTicker extends AutoCloseable {
	void tickAndUpload(int i, int j, GpuTexture gpuTexture);

	void close();
}

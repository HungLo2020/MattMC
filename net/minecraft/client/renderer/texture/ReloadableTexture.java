package net.minecraft.client.renderer.texture;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.TextureFormat;
import java.io.IOException;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

@Environment(EnvType.CLIENT)
public abstract class ReloadableTexture extends AbstractTexture {
	private final ResourceLocation resourceId;

	public ReloadableTexture(ResourceLocation resourceLocation) {
		this.resourceId = resourceLocation;
	}

	public ResourceLocation resourceId() {
		return this.resourceId;
	}

	public void apply(TextureContents textureContents) {
		boolean bl = textureContents.clamp();
		boolean bl2 = textureContents.blur();

		try (NativeImage nativeImage = textureContents.image()) {
			this.doLoad(nativeImage, bl2, bl);
		}
	}

	protected void doLoad(NativeImage nativeImage, boolean bl, boolean bl2) {
		GpuDevice gpuDevice = RenderSystem.getDevice();
		this.close();
		this.texture = gpuDevice.createTexture(this.resourceId::toString, 5, TextureFormat.RGBA8, nativeImage.getWidth(), nativeImage.getHeight(), 1, 1);
		this.textureView = gpuDevice.createTextureView(this.texture);
		this.setFilter(bl, false);
		this.setClamp(bl2);
		gpuDevice.createCommandEncoder().writeToTexture(this.texture, nativeImage);
	}

	public abstract TextureContents loadContents(ResourceManager resourceManager) throws IOException;
}

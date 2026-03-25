package net.minecraft.client.renderer.texture;

import net.blaze3d.platform.NativeImage;
import net.blaze3d.systems.RenderSystem;
import net.blaze3d.textures.TextureFormat;
import java.io.IOException;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
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
		this.close();
		this.texture = net.vulkanic.VulkanicAPI.createTexture(this.resourceId::toString, 5, TextureFormat.RGBA8, nativeImage.getWidth(), nativeImage.getHeight(), 1, 1);
		this.textureView = net.vulkanic.VulkanicAPI.createTextureView(this.texture);
		this.setFilter(bl, false);
		this.setClamp(bl2);
		net.vulkanic.VulkanicAPI.createCommandEncoder().writeToTexture(this.texture, nativeImage);
		
		// Iris: Track texture for PBR system
		net.irisshaders.iris.pbr.TextureTracker.INSTANCE.trackTexture(net.vulkanic.VulkanicCoreAPI.textureId(this.texture), this);
	}

	public abstract TextureContents loadContents(ResourceManager resourceManager) throws IOException;
}

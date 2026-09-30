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
		// Rust owns resource-pack texture admission and receives copied CPU
		// pixels through semantic asset transport; never materialize an
		// otherwise-unowned Java Vulkan texture during the handoff.
		this.texture = null;
		this.textureView = null;
		if (!net.vulkanic.gui.RustGalGuiRawImageAssets.stageNativeImage(this.resourceId, nativeImage)) {
			throw new IllegalStateException("Rust semantic texture staging rejected bounded image for " + this.resourceId);
		}
		return;
		
	}

	public abstract TextureContents loadContents(ResourceManager resourceManager) throws IOException;
}

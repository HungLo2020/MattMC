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
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
			|| net.vulkanic.VulkanicAPI.isVulkanBackendSelected()) {
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
		this.texture = net.vulkanic.VulkanicAPI.createTexture(this.resourceId::toString, 5, TextureFormat.RGBA8, nativeImage.getWidth(), nativeImage.getHeight(), 1, 1);
		this.textureView = net.vulkanic.VulkanicAPI.createTextureView(this.texture);
		this.setFilter(bl, false);
		this.setClamp(bl2);
		if (!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
			&& !net.vulkanic.VulkanicAPI.isVulkanBackendSelected()) {
			net.vulkanic.VulkanicAPI.createCommandEncoder().writeToTexture(this.texture, nativeImage);
		}
		
		// Iris PBR tracking is compatibility-only; semantic Rust Vulkan assets
		// must not publish Java GPU state into the live tracker.
		if (!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
			&& !net.vulkanic.VulkanicAPI.isVulkanBackendSelected()) {
			net.irisshaders.iris.pbr.TextureTracker.INSTANCE.trackTexture(net.vulkanic.VulkanicCoreAPI.textureId(this.texture), this);
		}
	}

	public abstract TextureContents loadContents(ResourceManager resourceManager) throws IOException;
}

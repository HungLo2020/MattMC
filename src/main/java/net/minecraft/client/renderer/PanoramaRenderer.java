package net.minecraft.client.renderer;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;

@Environment(EnvType.CLIENT)
public class PanoramaRenderer {
	public static final ResourceLocation PANORAMA_OVERLAY = ResourceLocation.withDefaultNamespace("textures/gui/title/background/panorama_overlay.png");
	private final Minecraft minecraft;
	private final CubeMap cubeMap;
	private float spin;

	public PanoramaRenderer(CubeMap cubeMap) {
		this.cubeMap = cubeMap;
		this.minecraft = Minecraft.getInstance();
	}

	public void render(GuiGraphics guiGraphics, int i, int j, boolean bl) {
		if (bl) {
			float f = this.minecraft.getDeltaTracker().getRealtimeDeltaTicks();
			float g = (float)(f * this.minecraft.options.panoramaSpeed().get());
			this.spin = wrap(this.spin + g * 0.1F, 360.0F);
		}

		if (!net.vulkanic.gui.RustGalPanoramaRenderer.enqueue(this.cubeMap, 10.0F, -this.spin, i, j)) {
			if (net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
				|| net.vulkanic.gui.RustGalGuiRenderer.isWholeFrameVulkanEnabled()) {
				throw new IllegalStateException("Rust Vulkan whole-frame panorama asset is unavailable; Java panorama rendering is not a fallback");
			}
			this.cubeMap.render(this.minecraft, 10.0F, -this.spin);
		}
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, PANORAMA_OVERLAY, 0, 0, 0.0F, 0.0F, i, j, 16, 128, 16, 128);
	}

	private static float wrap(float f, float g) {
		return f > g ? f - g : f;
	}

	public void registerTextures(TextureManager textureManager) {
		// The Rust whole-frame route copies the cubemap through its semantic asset
		// collector.  Do not even enter the Java texture manager on selected Vulkan;
		// CubeMap also guards its lower-level registration methods, but keeping this
		// callsite fenced prevents future panorama variants from reintroducing a
		// Java GPU allocation before semantic submission.
		if (net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
			|| net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			return;
		}
		this.cubeMap.registerTextures(textureManager);
	}
}

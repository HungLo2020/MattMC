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

		if (!net.vulkanic.gui.RustGalPanoramaRenderer.enqueue(
			this.cubeMap, 10.0F, -this.spin, i, j,
			new net.vulkanic.bridge.VulkanicGalBridge.GuiProjectionRecord(
				(float)this.minecraft.getWindow().getWidth() / this.minecraft.getWindow().getGuiScale(),
				(float)this.minecraft.getWindow().getHeight() / this.minecraft.getWindow().getGuiScale()),
			guiGraphics.guiRenderState
		)) {
			throw new IllegalStateException("Rust Vulkan whole-frame panorama asset is unavailable; Java panorama rendering is not a fallback");
		}
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, PANORAMA_OVERLAY, 0, 0, 0.0F, 0.0F, i, j, 16, 128, 16, 128);
	}

	/** Bounded audit receipt only; never used to control panorama rendering. */
	public float graphicsAuditSpin() {
		return this.spin;
	}

	private static float wrap(float f, float g) {
		return f > g ? f - g : f;
	}

	public void registerTextures(TextureManager textureManager) {
		return;
	}
}
